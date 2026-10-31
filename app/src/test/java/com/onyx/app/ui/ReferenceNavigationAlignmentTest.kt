package com.onyx.app.ui

import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.sidebar.SidebarDestinations
import com.onyx.app.ui.sidebar.SidebarTileGroup
import com.onyx.app.ui.sidebar.SidebarTileLayout
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La grille du volet d'Onyx contre celle de la référence — la table de correspondance, verrouillée.**
 *
 * La référence n'a que **quatre routes** (`Loading`, `Connect`, `SignIn`, `Chat`) : sa navigation
 * ne vit pas dans une énumération, elle vit **dans le volet**, dont la grille porte ses sections,
 * deux par rangée, dans cet ordre :
 *
 * > Inbox · Scheduled · Insights · Capabilities · Plugins · Config · Archived
 *
 * Onyx est un sur-ensemble : sa grille suit **le même ordre** (Inbox excepté, faute de fonction
 * réelle) et porte **les mêmes mots**, traduits un pour un — puis, sous un intitulé « Onyx », ses
 * fonctions propres, intactes.
 *
 * | Section de la référence | Tuile d'Onyx | Mot (FR) | Écran réel |
 * |---|---|---|---|
 * | Scheduled   | `CRON`         | Planifiés      | `CronScreen` |
 * | Insights    | `INSIGHTS`     | Aperçus        | `InsightsScreen` |
 * | Capabilities| `CAPABILITIES` | Capacités      | `CapabilitiesScreen` |
 * | Plugins     | `TOOLS`        | Greffons       | `ToolsScreen` (le plus proche réel) |
 * | Config      | `GATEWAY`      | Configuration  | `GatewayScreen` |
 * | Archived    | `ARCHIVED`     | Archives       | `ArchivedScreen` (**créé ici**) |
 * | Inbox       | **aucune**     | —              | **écartée** — pas de boîte de réception chez Onyx |
 *
 * Et ce qui est **propre à Onyx**, sous l'intitulé « Onyx » : `FILES`, `MODELS`, `KANBAN`, `CODE`
 * — **quatre** tuiles depuis le retrait de `SKILLS` (doublon de `CAPABILITIES`). Rien d'autre n'est
 * retiré, rien n'est orphelin.
 */
class ReferenceNavigationAlignmentTest {

    private val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()
    private val tiles = SidebarDestinations.tiles

    /** Les sections de la référence qu'Onyx sert **au même endroit** (la grille), et leur mot. */
    private val sectionsPartagees = listOf(
        Triple("Scheduled", "CRON", "Scheduled"),
        Triple("Insights", "INSIGHTS", "Insights"),
        Triple("Capabilities", "CAPABILITIES", "Capabilities"),
        Triple("Plugins", "TOOLS", "Plugins"),
        Triple("Config", "GATEWAY", "Config"),
        // La seule exception de mot : la maison nomme l'ensemble « Archives » (« Afficher les
        // archives »). « Archived » reste l'**état** — la colonne d'un tableau, le bandeau d'une
        // action —, deux autres emplois.
        Triple("Archived", "ARCHIVED", "Archives"),
    )

    /** L'ordre de la référence, Inbox excepté : c'est celui qu'Onyx doit tenir. */
    private val ordreDeLaReference = listOf("CRON", "INSIGHTS", "CAPABILITIES", "TOOLS", "GATEWAY", "ARCHIVED")

    /** Les tuiles qu'Onyx a **en plus** — la référence ne les porte pas. */
    private val sectionsPropresaOnyx = listOf("FILES", "MODELS", "KANBAN", "CODE")

    @Test
    fun `les sections partagees sont portees par la grille du volet, avec leur mot`() {
        sectionsPartagees.forEach { (referenceWord, id, tileLabel) ->
            assertTrue(
                "« $referenceWord » de la référence doit être une tuile du volet ($id)",
                tiles.any { it.id == id },
            )
            assertEquals(
                "la tuile $id doit porter le mot prévu pour « $referenceWord »",
                tileLabel,
                tiles.first { it.id == id }.label,
            )
        }
        // « Même mot » est tenu pour toutes — sauf l'ensemble des archives, la seule exception,
        // nommée et justifiée.
        assertEquals(
            "une seule section change de mot, et c'est dit",
            listOf("Archived"),
            sectionsPartagees.filter { (word, _, label) -> word != label }.map { it.first },
        )
    }

    @Test
    fun `l ordre de la reference est tenu, Inbox excepte`() {
        // C'est l'exigence « même ordre » : le groupe de la référence suit celui de la référence,
        // section par section, et un test le compare à la liste figée.
        assertEquals(ordreDeLaReference, SidebarDestinations.referenceOrder)
        // Et il vient **avant** le groupe d'Onyx, jamais mêlé.
        assertEquals(SidebarDestinations.referenceOrder, tiles.take(ordreDeLaReference.size).map { it.id })
        assertTrue(
            "le groupe d'Onyx vient après le groupe de la référence",
            tiles.drop(ordreDeLaReference.size).all { it.group == SidebarTileGroup.ONYX },
        )
    }

    @Test
    fun `les tuiles propres a Onyx restent toutes la, dans leur propre groupe`() {
        sectionsPropresaOnyx.forEach { id ->
            assertTrue("la tuile $id, propre à Onyx, doit rester", tiles.any { it.id == id })
            assertEquals(
                "la tuile $id appartient au groupe Onyx",
                SidebarTileGroup.ONYX,
                tiles.first { it.id == id }.group,
            )
        }
        assertEquals(sectionsPropresaOnyx, SidebarDestinations.onyxOrder)
    }

    @Test
    fun `Inbox n a pas de destination et n est pas une coquille`() {
        // La référence montre *ce que les tâches suivies ont rapporté*. Onyx n'a **aucune** boîte de
        // réception inter-conversations : on le dit, et on ne dessine pas une tuile qui promet dans
        // le vide.
        assertTrue("l'écart est documenté", SidebarDestinations.absentFromReference.containsKey("Inbox"))
        assertFalse(
            "« Inbox » n'a pas de route : aucune tuile ne doit le promettre",
            tiles.any { it.label.equals("Inbox", ignoreCase = true) || it.id == "INBOX" },
        )
        // Le reste des sections de la référence, lui, a bien sa tuile (aucune autre absente).
        assertEquals(
            "aucune section de la référence n'est absente d'Onyx hors Inbox",
            setOf("Inbox"),
            SidebarDestinations.absentFromReference.keys,
        )
    }

    @Test
    fun `chaque tuile a son ecran dans le volet, donc rien n est orphelin`() {
        // Une section portée par le volet est un écran **réel**, construit par la branche `when`.
        tiles.forEach { tile ->
            assertTrue(
                "l'écran de ${tile.id} n'est pas porté par le volet",
                SidebarDestinations.paneScreens.contains(tile.id),
            )
            assertTrue(
                "l'écran de ${tile.id} n'est pas construit par le routeur du volet",
                app.contains("Route.${tile.id}.name ->"),
            )
        }
    }

    @Test
    fun `les libelles de la grille sont dits en francais`() {
        // L'app est en français, la référence en anglais : la clé est le mot de la référence, la
        // traduction est française — un pour un.
        assertEquals("Planifiés", OnyxLocalization.translate("Scheduled", Locale.FRENCH))
        assertEquals("Aperçus", OnyxLocalization.translate("Insights", Locale.FRENCH))
        assertEquals("Capacités", OnyxLocalization.translate("Capabilities", Locale.FRENCH))
        assertEquals("Greffons", OnyxLocalization.translate("Plugins", Locale.FRENCH))
        assertEquals("Configuration", OnyxLocalization.translate("Config", Locale.FRENCH))
        assertEquals("Archives", OnyxLocalization.translate("Archives", Locale.FRENCH))
        assertEquals("Rien d’archivé", OnyxLocalization.translate("Nothing archived", Locale.FRENCH))
        assertEquals("Compétences", OnyxLocalization.translate("Skills", Locale.FRENCH))
        assertEquals("Fichiers du serveur", OnyxLocalization.translate("Files browser", Locale.FRENCH))
        // Les mots de la référence restent en anglais comme clé (aucun mot anglais codé en dur).
        assertEquals("Plugins", OnyxLocalization.translate("Plugins", Locale.ENGLISH))
        assertEquals("Archived", OnyxLocalization.translate("Archived", Locale.ENGLISH))
        // « Archived » reste l'état (colonne d'un tableau, bandeau d'une action) : le mot du volet
        // est « Archives », et les deux ne se confondent pas.
        assertEquals("Archivé", OnyxLocalization.translate("Archived", Locale.FRENCH))
    }

    @Test
    fun `la grille dessine six tuiles dans un seul groupe, sans intitule`() {
        val sidebar = File("src/main/java/com/onyx/app/ui/sidebar/ChatSidebar.kt").readText()
        // **Six tuiles, un seul groupe** — la grille de la planche (`hscene.py:45`,
        // `tiles_row(M, 268, CW, 56, [...])`) : Planifiés, Aperçus, Capacités, Greffons,
        // Configuration, Archives, **sans** intitulé de groupe (brief « cinq divergences », n° 2).
        //
        // Ce lot-ci rend la grille **personnalisable** : elle dessine la grille du réglage, en **une
        // seule liste** (`tiles.chunked(columns)`), toujours sans intitulé de groupe. La règle de
        // fond ne change pas — six tuiles par défaut, un seul groupe, aucun titre.
        assertTrue(
            "la grille n'itère qu'une liste, jamais deux groupes",
            sidebar.contains("tiles.chunked(columns).forEach { row ->"),
        )
        assertFalse(
            "la grille ne porte plus d'intitulé de groupe",
            sidebar.contains("SidebarDestinations.ONYX_GROUP_LABEL"),
        )
        assertEquals("les six tuiles de la référence", 6, SidebarDestinations.referenceTiles.size)
        // La grille par défaut, ce sont **ces six tuiles-là**, dans cet ordre.
        assertEquals(
            "la grille par défaut est celle de la référence",
            SidebarDestinations.referenceOrder,
            SidebarTileLayout.defaultIds,
        )
        // Rien n'est supprimé du code : les quatre tuiles de l'app restent **déclarées**, même si la
        // grille ne les dessine plus — la bibliothèque les rend, et la liste est remise au
        // propriétaire.
        assertEquals("les quatre tuiles de l'app restent déclarées", 4, SidebarDestinations.onyxTiles.size)
        assertEquals("Onyx", SidebarDestinations.ONYX_GROUP_LABEL)
    }

    @Test
    fun `Plugins a fusionne avec la tuile Outils, et la fusion est dite`() {
        // Le doublon possible : « Plugins » (référence) et « Outils » (Onyx) menaient au même écran.
        // Le mot de la référence l'emporte, l'écran reste, et une seule tuile le porte.
        assertEquals(1, tiles.count { it.id == "TOOLS" })
        assertEquals("Plugins", tiles.first { it.id == "TOOLS" }.label)
        assertTrue(
            "la fusion est écrite dans la table",
            SidebarDestinations.fusionLog.any { it.first == "Plugins" && it.second == "Tools" },
        )
    }

    @Test
    fun `la destination des archives est reelle, pas une coquille`() {
        // Les données existaient (l'état `archived` d'une conversation) : la destination est créée
        // avec l'écran réel, et la table le dit.
        assertTrue(SidebarDestinations.referenceOrder.contains("ARCHIVED"))
        assertTrue(app.contains("Route.ARCHIVED.name -> ArchivedScreen("))
        assertTrue(app.contains("SidebarDestinations.referenceTiles")
            || app.contains("SidebarDestinations.tiles"))
    }

    @Test
    fun `la coquille racine est la meme que celle de la reference`() {
        // La référence : Connect → SignIn → un unique « Home » (le fil, volet à côté). Onyx garde
        // la même racine, et la liste des conversations vit **dans le volet**, pas comme route.
        assertTrue("le fil est la racine", app.contains("Route.CHAT -> withSidebar { chatPane("))
        assertFalse(
            "la liste des conversations n'est plus une route",
            app.contains("Route.CONVERSATIONS"),
        )
        assertTrue(
            "le volet est une coquille dans les deux largeurs",
            app.contains("SidebarScaffold(state = sidebarState, sidebar = { chatSidebar() }, content = { inner() })"),
        )
    }
}
