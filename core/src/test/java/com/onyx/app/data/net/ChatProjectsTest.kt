package com.onyx.app.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatProjectsTest {
    @Test
    fun `le nom de dossier est nettoye comme sur la référence`() {
        assertEquals("Mon-projet- suite", projectFolderName("  Mon/projet: suite  "))
        assertEquals("visible", projectFolderName("...visible"))
        assertEquals("ligne 1 ligne 2", projectFolderName("ligne 1\nligne 2"))
        assertNull(projectFolderName("... \n "))
        assertEquals(80, projectFolderName("x".repeat(120))?.length)
    }

    @Test
    fun `seuls les vrais dossiers sont proposes dans l ordre alphabetique`() {
        val projects = listOf(
            ServerProject(id = "home", label = "No project", path = "/home", isHome = true),
            ServerProject(id = "z", label = "zeta", path = "/z"),
            ServerProject(id = "empty", label = "Empty", path = null),
            ServerProject(id = "a", label = "Alpha", path = "/a"),
        )

        assertEquals(listOf("Alpha", "zeta"), projectFolders(projects).map { it.label })
        assertEquals(listOf("zeta"), projectMoveTargets(projects, "Alpha").map { it.label })
    }

    @Test
    fun `les chemins du serveur gardent une seule barre oblique`() {
        assertEquals("/home/Projects/Onyx", serverPathJoin("/home/", "Projects", "Onyx"))
        assertEquals("~/Projects", serverPathJoin("~", "Projects"))
    }

    @Test
    fun `le groupe par defaut porte le nom du serveur, un vrai projet garde le sien`() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.FRANCE)
            val bucket = serverProjectFromRow(
                kotlinx.serialization.json.Json.parseToJsonElement(
                    """{"id":"__no_project__","isNoProject":true,"sessionCount":76}""",
                ) as kotlinx.serialization.json.JsonObject,
            )!!
            // Le serveur dit « Home » pour ce fourre-tout ; l'app le nommait « No project » puis le
            // traduisait « Sans projet ». Le groupe porte donc le nom du serveur, en toute langue —
            // c'est le nom que montre la référence.
            assertEquals("Home", bucket.displayLabel)
            assertEquals(76, bucket.sessionCount)
            // Le mot d'une version antérieure du serveur est ramené au nom d'aujourd'hui.
            val legacy = serverProjectFromRow(
                kotlinx.serialization.json.Json.parseToJsonElement(
                    """{"id":"__no_project__","label":"No project","isNoProject":true,"sessionCount":4}""",
                ) as kotlinx.serialization.json.JsonObject,
            )!!
            assertEquals("Home", legacy.displayLabel)
            val named = serverProjectFromRow(
                kotlinx.serialization.json.Json.parseToJsonElement(
                    """{"id":"/home/dev/onyx","label":"Onyx","path":"/home/dev/onyx","isAuto":false}""",
                ) as kotlinx.serialization.json.JsonObject,
            )!!
            // Un projet est un dossier : son nom ne se traduit jamais.
            assertEquals("Onyx", named.displayLabel)
            java.util.Locale.setDefault(java.util.Locale.US)
            assertEquals("Home", bucket.displayLabel)
            assertEquals("Onyx", named.displayLabel)
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test
    fun `seul un projet fait par quelqu un se renomme et se retire`() {
        val made = serverProjectFromRow(
            kotlinx.serialization.json.Json.parseToJsonElement(
                """{"id":"/home/dev/onyx","label":"Onyx","path":"/home/dev/onyx","isAuto":false}""",
            ) as kotlinx.serialization.json.JsonObject,
        )!!
        val found = serverProjectFromRow(
            kotlinx.serialization.json.Json.parseToJsonElement(
                """{"id":"/home/dev/elsewhere","label":"elsewhere","path":"/home/dev/elsewhere","isAuto":true}""",
            ) as kotlinx.serialization.json.JsonObject,
        )!!
        // Sans la clé, le projet compte comme trouvé — le repli de la référence.
        val silent = serverProjectFromRow(
            kotlinx.serialization.json.Json.parseToJsonElement(
                """{"id":"/repo","label":"repo","path":"/repo"}""",
            ) as kotlinx.serialization.json.JsonObject,
        )!!
        val bucket = serverProjectFromRow(
            kotlinx.serialization.json.Json.parseToJsonElement(
                """{"id":"__no_project__","isNoProject":true}""",
            ) as kotlinx.serialization.json.JsonObject,
        )!!
        assertTrue(made.isOwn)
        assertTrue(!found.isOwn)
        assertTrue(!silent.isOwn)
        assertTrue(!bucket.isOwn)
    }

    @Test
    fun `la liste montre le fourre-tout d abord puis le plus recent`() {
        val now = 1_773_000_000_000L
        val projects = listOf(
            ServerProject(id = "a", label = "Alpha", path = "/a", isAuto = false, sessionCount = 2, lastActive = now - 3000),
            ServerProject(id = "b", label = "Beta", path = "/b", isAuto = false, sessionCount = 1, lastActive = now - 1000),
            ServerProject(id = "__no_project__", label = "Home", path = null, isNoProject = true, sessionCount = 76, lastActive = now - 5000),
            // Trouvé par Hermes et sans conversation : il reste hors de la liste.
            ServerProject(id = "c", label = "Gamma", path = "/c", isAuto = true, sessionCount = 0, lastActive = now),
        )
        val listed = listedProjects(projects)
        assertEquals(listOf("Home", "Beta", "Alpha"), listed.map { it.label })
        // Un projet fait par quelqu'un s'y montre avant sa première conversation.
        val fresh = listedProjects(
            listOf(ServerProject(id = "d", label = "Delta", path = "/d", isAuto = false, sessionCount = 0)),
        )
        assertEquals(listOf("Delta"), fresh.map { it.label })
        // Le groupe par défaut, lui, s'y montre **toujours** — même vide : c'est la porte de
        // secours du volet, et la retirer ferait disparaître d'un coup tout ce qu'il porte.
        val emptyHome = listedProjects(
            listOf(ServerProject(id = "__no_project__", label = "Home", path = null, isNoProject = true, sessionCount = 0)),
        )
        assertEquals(listOf("Home"), emptyHome.map { it.label })
    }

    @Test
    fun `la derniere activite de projects tree est lue en camelCase`() {
        val row = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"id":"/repo","label":"repo","path":"/repo","sessionCount":3,"lastActive":1773000000}""",
        ) as kotlinx.serialization.json.JsonObject
        val project = serverProjectFromRow(row)!!
        assertEquals(3, project.sessionCount)
        assertEquals(1_773_000_000_000L, project.lastActive)
        // Zéro veut dire « rien à dire » : un projet sans conversation ne date pas de 1970.
        val empty = serverProjectFromRow(
            kotlinx.serialization.json.Json.parseToJsonElement(
                """{"id":"/fresh","label":"fresh","path":"/fresh","sessionCount":0,"lastActive":0}""",
            ) as kotlinx.serialization.json.JsonObject,
        )!!
        assertNull("aucune date à montrer", empty.lastActive)
    }

    @Test
    fun `un projet se renomme et se retire par les routes du Dashboard`() {
        // La surface publique des deux gestes, telle que la référence les appelle.
        val source = java.io.File("src/main/java/com/onyx/app/data/net/ChatProjects.kt").readText()
        assertTrue(source.contains("\"projects.update\""))
        assertTrue(source.contains("\"projects.delete\""))
        assertTrue(source.contains("\"session.workspace.move\""))
        assertTrue(source.contains("\"session.delete\""))
        assertTrue(source.contains("api/sessions/\$stored"))
    }
}
