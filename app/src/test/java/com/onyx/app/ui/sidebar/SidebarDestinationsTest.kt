package com.onyx.app.ui.sidebar

import com.onyx.app.ui.design.ChatTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La grille du volet, ses deux groupes — et aucune destination morte.**
 *
 * La personne a vu « Fichiers » des deux côtés (la tuile du volet et l'onglet du bas) et l'a lu
 * comme un second menu ajouté par-dessus le sien. Le doublon était **de nom**, pas de destination :
 * la tuile ouvre le navigateur de fichiers du serveur, l'onglet les pièces de la conversation.
 *
 * Ce test tient donc les bouts :
 * - **aucune tuile ne recouvre un onglet** par sa destination ;
 * - **aucun libellé de tuile ne reprend celui d'un onglet** — le doublon visible se règle par le nom ;
 * - **aucune tuile ne double la roue dentée** de la tête (les Réglages) ;
 * - la grille est en **deux groupes** : la référence d'abord, dans son ordre, puis Onyx ;
 * - **toutes** les tuiles sont là, chacune avec son écran — rien n'est retiré, rien n'est orphelin.
 */
class SidebarDestinationsTest {

    /** Le groupe de la référence, tel qu'il doit rester : l'ordre de la référence, Inbox excepté. */
    private val ordreReference = listOf("CRON", "INSIGHTS", "CAPABILITIES", "TOOLS", "GATEWAY", "ARCHIVED")

    /** Le groupe d'Onyx, tel qu'il doit rester — quatre tuiles, deux rangées pleines. */
    private val ordreOnyx = listOf("FILES", "MODELS", "KANBAN", "CODE")

    @Test
    fun `aucune tuile du volet ne recouvre un onglet de la barre du bas`() {
        val conflicts = SidebarDestinations.conflicts(SidebarDestinations.tiles)
        assertEquals("tuiles en double : $conflicts", emptyList<String>(), conflicts)
    }

    @Test
    fun `aucun libelle de tuile ne reprend celui d un onglet`() {
        // Le cas exact de la capture : « Fichiers » d'un côté, « Fichiers » de l'autre.
        val clashes = SidebarDestinations.labelClashes(SidebarDestinations.tiles)
        assertEquals("libellés en double : $clashes", emptyList<String>(), clashes)
        assertFalse(
            "la tuile des fichiers ne peut plus s'appeler comme l'onglet",
            SidebarDestinations.tiles.any { it.label.equals("Files", ignoreCase = true) },
        )
    }

    @Test
    fun `la regle attrape une tuile qui reprendrait le nom d un onglet`() {
        // La contre-épreuve : remettre l'ancien libellé doit être vu par la règle.
        val withClash = SidebarDestinations.tiles.map {
            if (it.id == "FILES") it.copy(label = "Files") else it
        }
        assertEquals(listOf("Files"), SidebarDestinations.labelClashes(withClash))
    }

    @Test
    fun `aucune tuile ne double la roue dentee de la tete`() {
        assertEquals(
            "aucune tuile ne doit ouvrir les Réglages : la roue dentée de la tête est leur seul accès",
            emptyList<String>(),
            SidebarDestinations.headConflicts(SidebarDestinations.tiles),
        )
    }

    @Test
    fun `la tuile des Reglages a ete retiree, les autres tuiles restent`() {
        // On retire **le** doublon — la tuile « Réglages », qui ouvrait le même écran que la roue
        // dentée de la tête — et rien d'autre : les tuiles de la grille sont là, chacune avec son
        // écran et son icône. « Capacités » n'y figure **qu'une fois** : la référence la porte dans
        // sa grille, à côté d'Aperçus, et Onyx ne l'offrait que depuis les Réglages. La tuile
        // « Compétences » a été retirée (doublon de « Capacités ») : elle n'est plus dans la liste.
        listOf("CRON", "TOOLS", "FILES", "MODELS", "KANBAN", "CODE", "INSIGHTS", "CAPABILITIES")
            .forEach { id ->
                assertTrue(
                    "la tuile $id doit être présente",
                    SidebarDestinations.tiles.any { it.id == id },
                )
            }
        // Et la tuile retirée ne revient pas sans que le test tombe.
        assertFalse("la tuile « Compétences » est retirée", SidebarDestinations.tiles.any { it.id == "SKILLS" })
        // La tuile des Réglages, elle, a bien quitté la grille…
        assertFalse(SidebarDestinations.tiles.any { it.id == SidebarDestinations.HEAD_SETTINGS_TILE })
        // …et « Capacités » n'est portée que par **une** tuile, jamais deux.
        assertEquals(1, SidebarDestinations.tiles.count { it.id == "CAPABILITIES" })
    }

    @Test
    fun `la regle attrape une tuile qui doublerait la tete du volet`() {
        // La contre-épreuve : remettre la tuile « Réglages » doit être vu par la règle.
        val withHead = SidebarDestinations.tiles + SidebarTile(
            SidebarDestinations.HEAD_SETTINGS_TILE,
            "Settings",
            com.onyx.app.ui.design.Symbols.GearshapeSingle,
        )
        assertEquals(listOf("SETTINGS"), SidebarDestinations.headConflicts(withHead))
    }

    @Test
    fun `les Reglages gardent un chemin d acces dans le volet`() {
        // Aucune tuile n'ouvre les Réglages : la roue dentée de la tête du volet les porte seule.
        val sidebar = java.io.File("src/main/java/com/onyx/app/ui/sidebar/ChatSidebar.kt").readText()
        assertTrue(
            "la roue dentée de la tête doit rester le point d'entrée des Réglages",
            sidebar.contains("onClick = onOpenSettings"),
        )
        assertTrue(
            "la tête doit demander l'ouverture des Réglages",
            sidebar.substringAfter("private fun SidebarHeader(").contains("onOpenSettings: () -> Unit"),
        )
    }

    @Test
    fun `la grille porte les deux groupes, dans l ordre de la reference puis Onyx`() {
        // Le groupe de la référence, dans son ordre — Inbox excepté (documenté dans l'objet).
        assertEquals(ordreReference, SidebarDestinations.referenceOrder)
        assertEquals(ordreOnyx, SidebarDestinations.onyxOrder)
        assertEquals(
            "la grille montre la référence d'abord, puis Onyx",
            ordreReference + ordreOnyx,
            SidebarDestinations.tiles.map { it.id },
        )
        // Et les deux groupes sont bien distincts, jamais mêlés.
        assertEquals(ordreReference, SidebarDestinations.group(SidebarTileGroup.REFERENCE).map { it.id })
        assertEquals(ordreOnyx, SidebarDestinations.group(SidebarTileGroup.ONYX).map { it.id })
        assertEquals(SidebarDestinations.tiles.size, SidebarDestinations.group(SidebarTileGroup.REFERENCE).size + SidebarDestinations.group(SidebarTileGroup.ONYX).size)
    }

    @Test
    fun `Inbox est la seule section de la reference ecartee, et elle est dite`() {
        // On ne crée pas une tuile qui n'ouvre rien : « Inbox » n'a aucune destination chez Onyx.
        assertEquals(setOf("Inbox"), SidebarDestinations.absentFromReference.keys)
        assertFalse(
            "aucune tuile ne doit promettre une boîte de réception qui n'existe pas",
            SidebarDestinations.tiles.any { it.label.equals("Inbox", ignoreCase = true) },
        )
        // Et la raison est écrite, pas laissée à l'oral.
        assertTrue(SidebarDestinations.absentFromReference.getValue("Inbox").contains("boîte de réception"))
    }

    @Test
    fun `les tuiles de la reference portent leurs mots, traduits un pour un`() {
        // La clé est le mot de la référence ; la traduction est celle du dictionnaire, et elle est
        // relue par `ReferenceNavigationAlignmentTest` (les quatre mots nouveaux : Plugins, Config).
        val labels = SidebarDestinations.referenceTiles.associate { it.id to it.label }
        assertEquals("Scheduled", labels["CRON"])
        assertEquals("Insights", labels["INSIGHTS"])
        assertEquals("Capabilities", labels["CAPABILITIES"])
        assertEquals("Plugins", labels["TOOLS"])
        assertEquals("Config", labels["GATEWAY"])
        assertEquals("Archives", labels["ARCHIVED"])
    }

    @Test
    fun `la tuile Plugins et la tuile Outils ont ete fusionnees, et c est dit`() {
        // La fusion : deux mots pour **un seul écran**. L'écran reste (ToolsScreen), le doublon non.
        assertTrue(
            "la tuile de la référence existe",
            SidebarDestinations.referenceTiles.any { it.id == "TOOLS" && it.label == "Plugins" },
        )
        assertTrue(
            "la fusion est écrite",
            SidebarDestinations.fusionLog.any { it.first == "Plugins" && it.second == "Tools" },
        )
        // Et il ne reste qu'**une** tuile pour cet écran, pas deux.
        assertEquals(1, SidebarDestinations.tiles.count { it.id == "TOOLS" })
    }

    @Test
    fun `les onglets de contenu du bas sont tous declares`() {
        // « Idées » et « Objectifs » ont été supprimés : la barre du bas n'offre plus que les trois
        // onglets de contenu. Le repère-visage (ChatTab.BOT) n'est pas une vue du fil — il ouvre
        // l'identité du bot —, il n'est donc pas listé ici.
        assertEquals(
            "les onglets de contenu de la barre doivent être déclarés",
            ChatTab.paged.size,
            SidebarDestinations.bottomTabDestinations.size,
        )
        ChatTab.paged.forEach { tab ->
            assertTrue(
                "l'onglet ${tab.key} doit figurer dans les destinations du bas",
                SidebarDestinations.bottomTabDestinations.containsKey(tab.key),
            )
        }
    }

    @Test
    fun `chaque tuile a de quoi s afficher`() {
        SidebarDestinations.tiles.forEach { tile ->
            assertTrue("la tuile ${tile.id} sans libellé", tile.label.isNotBlank())
            assertTrue("la tuile ${tile.id} sans icône", tile.symbol != 0)
        }
        assertEquals(
            "deux tuiles ne peuvent pas ouvrir le même écran",
            SidebarDestinations.tiles.size,
            SidebarDestinations.tiles.map { it.id }.distinct().size,
        )
    }

    @Test
    fun `chaque tuile est portee par le volet, avec les feuilles de ses ecrans`() {
        // Une tuile ouvre son écran **dans** le volet : chaque tuile doit donc avoir son écran dans
        // le volet, sinon elle n'ouvrirait rien.
        SidebarDestinations.tiles.forEach { tile ->
            assertTrue(
                "la tuile ${tile.id} n'est pas portée par le volet",
                SidebarDestinations.paneScreens.contains(tile.id),
            )
        }
        // Et les deux feuilles qu'un écran du volet ouvre à son tour en font partie.
        assertTrue(SidebarDestinations.paneScreens.contains("SKILL_EDITOR"))
        assertTrue(SidebarDestinations.paneScreens.contains("FILE_EDITOR"))
        assertEquals(
            "le volet porte exactement ses tuiles et les deux feuilles",
            SidebarDestinations.tiles.size + 2,
            SidebarDestinations.paneScreens.size,
        )
        // Et rien d'autre : une destination qui n'est pas une tuile ni une de ces deux feuilles
        // n'est **pas** portée par le volet (elle garde sa présentation pleine page).
        assertEquals(
            setOf("SKILL_EDITOR", "FILE_EDITOR"),
            SidebarDestinations.paneScreens - SidebarDestinations.tiles.map { it.id }.toSet(),
        )
        assertFalse(SidebarDestinations.paneScreens.contains("APPEARANCE"))
        assertFalse(SidebarDestinations.paneScreens.contains("SERVERS"))
        assertFalse(SidebarDestinations.paneScreens.contains("CHAT"))
    }

    @Test
    fun `la destination des archives est nouvelle et elle a son ecran`() {
        // La tuile *Archived* de la référence : elle n'existait pas, elle est créée ici — et elle
        // ouvre un écran, pas le vide.
        assertTrue(SidebarDestinations.paneScreens.contains("ARCHIVED"))
        val screen = java.io.File("src/main/java/com/onyx/app/ui/sidebar/ArchivedScreen.kt")
        assertTrue("l'écran des Archives doit exister", screen.exists())
        val text = screen.readText()
        assertTrue("l'écran garde l'état vide de la référence", text.contains("Nothing archived"))
        assertTrue("l'écran lit l'état archivé", text.contains("archivedConversations("))
    }
}
