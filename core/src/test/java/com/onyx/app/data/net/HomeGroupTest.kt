package com.onyx.app.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le **verrou** du lot « toutes les conversations dans le volet ».
 *
 * Il mesure l'invariant, pas une intention : *aucune conversation du registre n'apparaît dans aucun
 * groupe*. Une conversation, ici, c'est un identifiant du registre du serveur (`GET api/sessions`) ;
 * un groupe, c'est un projet de la rangée ou le groupe par défaut « Home ».
 *
 * Le jour où quelqu'un retire une conversation du groupe par défaut, ou oublie d'y ranger ce que le
 * serveur ne range pas (une session `oneshot`, `cron`, `kanban`, ou une conversation sans message),
 * le premier test échoue et dit lesquelles.
 */
class HomeGroupTest {

    /** Un projet nommé du serveur, avec les conversations qu'il porte. */
    private fun named(id: String, label: String, vararg sessions: String) = ServerProject(
        id = id,
        label = label,
        path = "/home/$label",
        isAuto = false,
        sessionIds = sessions.toSet(),
        sessionCount = sessions.size,
    )

    /** Le fourre-tout du serveur, tel que `projects.tree` le rend. */
    private fun serverHome(vararg sessions: String) = ServerProject(
        id = NO_PROJECT_ID,
        label = ServerProject.NO_PROJECT_LABEL,
        path = null,
        isNoProject = true,
        sessionIds = sessions.toSet(),
        sessionCount = sessions.size,
    )

    private fun summary(id: String) = SessionSummary(id = id)

    /**
     * **Le verrou** : le registre entier est couvert par les groupes.
     *
     * « test » et « tesy2 » sont les projets de la référence (vides, faits à la main), « depot » un
     * projet qui porte deux conversations, et la base contient en plus ce que le serveur ne range
     * jamais : une session de ligne de commande sans message (`cli`), une exécution unique
     * (`oneshot`) et une session de tableau de bord (`kanban`).
     */
    @Test
    fun `aucune conversation du registre n apparait dans aucun groupe`() {
        val tree = listOf(
            serverHome("h1", "h2"),
            named("p_1", "test"),
            named("p_2", "tesy2"),
            named("/repo", "depot", "d1", "d2"),
        )
        val base = setOf("h1", "h2", "d1", "d2", "cli1", "oneshot1", "kanban1")

        val groups = withHomeGroup(tree, base)

        // Aucune orpheline : c'est l'invariant du lot.
        assertEquals(emptySet<String>(), unclaimedSessions(groups, base))
        // Rien n'est perdu non plus : la réunion des groupes est exactement la base…
        assertEquals(base, groups.flatMapTo(LinkedHashSet()) { it.sessionIds })
        // …et chaque conversation n'est comptée qu'une fois.
        assertEquals(base.size, groups.sumOf { it.sessionIds.size })
    }

    /** Le groupe par défaut porte **tout** ce qu'aucun projet ne réclame — il est le défaut, pas un reste. */
    @Test
    fun `le groupe par defaut porte tout ce qu aucun projet ne reclame`() {
        val tree = listOf(serverHome("h1"), named("/repo", "depot", "d1"))
        val base = setOf("h1", "d1", "cli1", "oneshot1", "kanban1")

        val home = withHomeGroup(tree, base).first { it.isNoProject }

        assertEquals(setOf("h1", "cli1", "oneshot1", "kanban1"), home.sessionIds)
        assertEquals("Home", home.displayLabel)
        // Le projet nommé n'a pas perdu la sienne, et rien n'est passé deux fois.
        assertEquals(setOf("d1"), withHomeGroup(tree, base).first { !it.isNoProject }.sessionIds)
    }

    /**
     * Quand **aucun** projet ne réclame de conversation — le cas de la référence, où « Home » porte
     * tout —, le groupe par défaut les porte toutes, et son compte le dit.
     */
    @Test
    fun `le groupe par defaut porte toutes les conversations quand rien n est reclame`() {
        val rows = (1..87).map { "s$it" }
        val tree = listOf(serverHome(*rows.toTypedArray()), named("p_1", "test"), named("p_2", "tesy2"))
        val base = rows.toSet()

        val groups = withHomeGroup(tree, base)
        val home = groups.first { it.isNoProject }

        assertEquals(87, home.sessionCount)
        assertEquals(87, home.sessionIds.size)
        assertEquals(emptySet<String>(), unclaimedSessions(groups, base))
    }

    /** Le compte annoncé est **le nombre de rangées listées**, et le groupe par défaut existe toujours. */
    @Test
    fun `le compte annonce est le nombre de rangees listees`() {
        val tree = listOf(serverHome("h1"), named("/repo", "depot", "d1", "d2"))
        val base = setOf("h1", "d1", "d2", "cli1")

        val groups = withHomeGroup(tree, base)
        groups.forEach { group ->
            assertEquals("« ${group.label} » annonce ce qu'il liste", group.sessionIds.size, group.sessionCount)
        }
        // Le défaut existe même quand tout le reste est réclamé ailleurs.
        val allClaimed = withHomeGroup(listOf(named("/repo", "depot", "d1")), setOf("d1"))
        assertEquals(listOf("Home", "depot"), allClaimed.map { it.label })
        assertEquals(0, allClaimed.first().sessionCount)
    }

    /**
     * La descente d'un projet **complétée** : ce que l'écran liste est ce que la puce annonce.
     *
     * Le serveur ne rend, pour le groupe par défaut, que les conversations qu'il a su placer ; les
     * autres descendent dans la première voie. Le compte est recalculé sur les rangées — annoncé =
     * listé.
     */
    @Test
    fun `les conversations sans groupe descendent dans la premiere voie du groupe par defaut`() {
        val home = serverHome("h1").copy(
            lanes = listOf(ProjectLane(id = NO_PROJECT_ID, label = "Home", sessions = listOf(summary("h1")))),
        )

        val filled = home.filledWith(listOf(summary("cli1"), summary("oneshot1")))

        assertEquals(listOf("h1", "cli1", "oneshot1"), filled.lanes.single().sessions.map { it.id })
        assertEquals(3, filled.sessionCount)
        assertEquals(lanesSessionIds(filled.lanes).size, filled.sessionCount)
        // Rien de ce que le serveur avait rangé n'a bougé, et rien n'est descendu deux fois.
        assertEquals(listOf("h1"), filled.lanes.single().sessions.take(1).map { it.id })
        assertEquals(filled, filled.filledWith(emptyList()))
    }

    /** Un registre muet ne fait rien dire de plus : on montre ce que le serveur dit, sans inventer. */
    @Test
    fun `un registre muet laisse l arbre tel quel`() {
        val tree = listOf(serverHome("h1"), named("/repo", "depot", "d1"))

        assertEquals(tree, withHomeGroup(tree, null))
    }

    /**
     * Le groupe du serveur **n'est jamais amoindri** : ce qu'il portait reste chez lui, et seul ce
     * qu'aucun projet ne réclame s'y ajoute. C'est la règle « on ajoute et on range, on ne retire
     * rien », appliquée au groupe par défaut lui-même.
     */
    @Test
    fun `le groupe du serveur n est jamais amoindri`() {
        val tree = listOf(serverHome("h1", "h2"), named("/repo", "depot", "d1"))
        val base = setOf("h1", "h2", "d1", "cli1")

        val home = withHomeGroup(tree, base).first { it.isNoProject }

        assertTrue("ce que le serveur portait est encore là", home.sessionIds.containsAll(setOf("h1", "h2")))
        assertEquals(setOf("h1", "h2", "cli1"), home.sessionIds)
    }

    /** Une conversation ne se trouve jamais **dans deux groupes** : elle est réclamée, ou au défaut. */
    @Test
    fun `une conversation reclamee par un projet n est pas dans le groupe par defaut`() {
        val tree = listOf(serverHome("h1"), named("/repo", "depot", "d1", "d2"))
        val base = setOf("h1", "d1", "d2")

        val groups = withHomeGroup(tree, base)
        val home = groups.first { it.isNoProject }.sessionIds
        val claimed = groups.filterNot { it.isNoProject }.flatMap { it.sessionIds }

        assertEquals(setOf("h1"), home)
        assertEquals(emptySet<String>(), (home intersect claimed.toSet()))
    }

    /** Une conversation arrivée depuis la dernière lecture entre au défaut, sans rien déranger. */
    @Test
    fun `une conversation nouvelle entre dans le groupe par defaut sans rien deranger`() {
        val tree = listOf(serverHome("h1"), named("/repo", "depot", "d1"))

        val before = withHomeGroup(tree, setOf("h1", "d1"))
        val after = withHomeGroup(tree, setOf("h1", "d1", "cli1"))

        assertEquals(setOf("h1"), before.first { it.isNoProject }.sessionIds)
        assertEquals(setOf("h1", "cli1"), after.first { it.isNoProject }.sessionIds)
        // Le projet nommé, lui, n'a pas bougé d'un identifiant.
        assertEquals(
            before.first { !it.isNoProject }.sessionIds,
            after.first { !it.isNoProject }.sessionIds,
        )
    }

    /** Un registre vide ne fabrique rien : le défaut est là, à zéro, et rien n'est inventé. */
    @Test
    fun `un registre vide ne fabrique aucune conversation`() {
        val groups = withHomeGroup(listOf(named("/repo", "depot")), emptySet())

        assertEquals(listOf("Home", "depot"), groups.map { it.label })
        assertEquals(0, groups.first { it.isNoProject }.sessionCount)
        assertEquals(emptySet<String>(), unclaimedSessions(groups, emptySet()))
    }

    /**
     * Un arbre **vide** (un serveur neuf, aucun projet) garde son groupe par défaut : c'est alors
     * lui qui porte tout, et le volet n'est jamais vide alors que le registre ne l'est pas.
     */
    @Test
    fun `un arbre sans aucun projet garde son groupe par defaut`() {
        val base = setOf("s1", "s2", "s3")

        val groups = withHomeGroup(emptyList(), base)

        assertEquals(listOf("Home"), groups.map { it.label })
        assertEquals(base, groups.single().sessionIds)
        assertEquals(3, groups.single().sessionCount)
        assertEquals(emptySet<String>(), unclaimedSessions(groups, base))
    }

    /** Une conversation réclamée mais **absente du registre** ne laisse pas d'orpheline derrière elle. */
    @Test
    fun `une conversation reclamee hors registre ne laisse pas d orpheline`() {
        val tree = listOf(serverHome("h1"), named("/repo", "depot", "absente"))

        val groups = withHomeGroup(tree, setOf("h1"))

        assertEquals(setOf("h1"), groups.first { it.isNoProject }.sessionIds)
        assertEquals(emptySet<String>(), unclaimedSessions(groups, setOf("h1")))
    }

    /**
     * Le groupe par défaut **n'est pas un dossier** : il ne se propose ni comme destination d'un
     * déplacement ni comme projet où démarrer une conversation — c'est un groupe, pas un lieu.
     */
    @Test
    fun `le groupe par defaut n est pas un dossier ou demarrer`() {
        val groups = withHomeGroup(listOf(serverHome("h1"), named("/repo", "depot", "d1")), setOf("h1", "d1"))

        val home = groups.first { it.isNoProject }

        assertEquals(listOf("depot"), projectFolders(groups).map { it.label })
        assertEquals(emptyList<ServerProject>(), projectMoveTargets(groups, "depot").filter { it.isNoProject })
        assertTrue("on ne démarre pas une conversation dans le groupe par défaut", !home.canStart)
    }

    /** Le défaut **ouvre** la rangée, et les projets du serveur gardent leur ordre : rien n'est trié de travers. */
    @Test
    fun `le groupe par defaut ouvre la rangee et les projets gardent leur ordre`() {
        val tree = listOf(
            named("p_1", "test"),
            named("p_2", "tesy2"),
            named("/repo", "depot", "d1"),
        )

        val groups = withHomeGroup(tree, setOf("d1"))

        assertEquals(listOf("Home", "test", "tesy2", "depot"), groups.map { it.label })
    }

    /**
     * Un projet **trouvé** dont le fil annonce des années d'historique mais qui ne liste rien n'est
     * plus montré : sa puce annoncerait 417 conversations et en ouvrirait zéro. Le compte se lit sur
     * `sessionIds`, donc il tombe à zéro, donc la liste l'écarte — comme la référence.
     */
    @Test
    fun `un projet trouve qui ne liste rien n est plus annonce`() {
        val lying = serverProjectFromRow(
            kotlinx.serialization.json.Json.parseToJsonElement(
                """{"id":"/srv/historique","label":"historique","path":"/srv/historique","isAuto":true,"sessionCount":417,"sessionIds":[]}""",
            ) as kotlinx.serialization.json.JsonObject,
        )!!

        assertEquals(0, lying.sessionCount)
        assertEquals(emptyList<ServerProject>(), listedProjects(listOf(lying)))
        // Un projet fait à la main, lui, se montre avant sa première conversation.
        assertEquals(1, listedProjects(listOf(named("/repo", "depot"))).size)
    }

    /** Les voies **suivantes** d'un projet ne bougent pas : la descente complète la première, et rien d'autre. */
    @Test
    fun `les voies suivantes d un projet ne bougent pas`() {
        val lane1 = ProjectLane(id = "l1", label = "main", sessions = listOf(summary("a")))
        val lane2 = ProjectLane(id = "l2", label = "topic", sessions = listOf(summary("b")))
        val project = named("/repo", "depot", "a", "b").copy(lanes = listOf(lane1, lane2))

        val filled = project.filledWith(listOf(summary("cli1")))

        assertEquals(listOf("a", "cli1"), filled.lanes[0].sessions.map { it.id })
        assertEquals(lane2, filled.lanes[1])
        assertEquals(3, filled.sessionCount)
    }

    /**
     * **Le verrou, mesuré sur la forme réelle du fil** : une réponse de `projects.tree` sans le
     * groupe par défaut (le serveur l'omet quand rien n'est sans projet) doit quand même produire
     * « Home », avec ce qu'aucun projet ne réclame — sinon ces conversations-là n'ont aucun groupe.
     */
    @Test
    fun `un arbre sans groupe par defaut en gagne un, avec ce que personne ne reclame`() {
        val projects = listOf(
            serverProjectFromRow(
                kotlinx.serialization.json.Json.parseToJsonElement(
                    """{"id":"/repo","label":"depot","path":"/repo","isAuto":false,"sessionCount":1,"sessionIds":["d1"]}""",
                ) as kotlinx.serialization.json.JsonObject,
            )!!,
        )
        val base = setOf("d1", "cli1")

        val groups = withHomeGroup(projects, base)

        assertTrue("le groupe par défaut est là", groups.any { it.isNoProject })
        assertEquals(setOf("cli1"), groups.first { it.isNoProject }.sessionIds)
        assertEquals(emptySet<String>(), unclaimedSessions(groups, base))
    }

    /**
     * Une réponse de la route avec `sessionIds` : le compte se lit **dessus**, jamais sur le
     * `sessionCount` du fil — un dépôt trouvé y annonce des années d'historique (cron, aides) alors
     * que sa puce n'ouvre sur rien.
     */
    @Test
    fun `le compte se lit sur les conversations que le projet liste`() {
        val row = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"id":"/repo","label":"depot","path":"/repo","sessionCount":417,"sessionIds":["d1","d2"]}""",
        ) as kotlinx.serialization.json.JsonObject

        val project = serverProjectFromRow(row)!!

        assertEquals(2, project.sessionCount)
        assertEquals(setOf("d1", "d2"), project.sessionIds)

        // Sans la clé (une réponse d'avant la refonte), on garde le compte du fil.
        val older = serverProjectFromRow(
            kotlinx.serialization.json.Json.parseToJsonElement(
                """{"id":"/repo","label":"depot","path":"/repo","sessionCount":417}""",
            ) as kotlinx.serialization.json.JsonObject,
        )!!
        assertEquals(417, older.sessionCount)
        assertEquals(emptySet<String>(), older.sessionIds)
    }

    /** Les projets du serveur sont rendus **tels quels** (mêmes objets) : la règle n'en retouche aucun. */
    @Test
    fun `les projets du serveur ne sont pas retouches`() {
        val depot = named("/repo", "depot", "d1")
        val test = named("p_1", "test")

        val groups = withHomeGroup(listOf(test, serverHome("h1"), depot), setOf("h1", "d1"))

        // Le défaut est un objet neuf ; les projets du serveur, non.
        assertTrue(groups.first() !== depot)
        assertTrue(groups[1] === test)
        assertTrue(groups[2] === depot)
        // Et le défaut n'apparaît qu'une fois, même si le fil en portait deux.
        assertEquals(1, groups.count { it.isNoProject })
    }

    /** La dernière date que le serveur donnait au défaut reste la sienne : rien n'est réinventé. */
    @Test
    fun `le groupe par defaut garde la date du serveur`() {
        val dated = serverHome("h1").copy(lastActive = 1_700_000_000_000L)

        val home = withHomeGroup(listOf(dated), setOf("h1", "cli1")).first { it.isNoProject }

        assertEquals(1_700_000_000_000L, home.lastActive)
    }

    /**
     * **Le verrou du branchement.** La règle n'agit que si le volet passe par elle : ces trois
     * lignes sont le chemin que l'app suit vraiment — l'arbre complété, le registre lu en entier,
     * et la descente complétée. Retirer l'un des trois rendrait des conversations invisibles, et ce
     * test-ci échoue.
     */
    @Test
    fun `le volet passe par la regle du groupe par defaut`() {
        val source = java.io.File("src/main/java/com/onyx/app/data/net/ChatProjects.kt").readText()

        assertTrue(
            "l'arbre se complète",
            source.contains("return withHomeGroup(projects, registeredSessionIds())"),
        )
        assertTrue(
            "le registre se lit en entier",
            source.contains("files.sessions().allSummaries()"),
        )
        assertTrue(
            "la descente du groupe par défaut se complète",
            source.contains("return filledWith(registered.filter"),
        )
        // La fenêtre demandée est la même pour l'arbre et pour la descente, sinon un compte peut mentir.
        assertEquals(2, Regex("put\\(\"session_limit\", PROJECT_SESSION_WINDOW\\)").findAll(source).count())
    }
}
