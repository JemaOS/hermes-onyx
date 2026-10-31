package com.onyx.app.ui

import com.onyx.app.ui.sidebar.SidebarDestinations
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'**ancien écran des conversations** a été retiré : c'était un doublon de la liste du volet, et
 * le propriétaire l'a demandé (« elle devrait plus exister, elle sert à rien »).
 *
 * Ce fichier tient les deux moitiés de la règle.
 *
 * 1. **La route ne reparaît pas.** Ni dans l'énum `Route`, ni dans la mise en page, ni dans le
 *    routeur : un écran qu'on ne peut plus montrer ne doit pas être une destination. Les fichiers
 *    qui le portaient sont partis avec lui.
 * 2. **Aucune fonction ne s'est perdue.** Ce que l'écran faisait vit ailleurs, et c'est vérifiable :
 *    la liste et sa recherche dans le volet, Cron / Kanban / Code dans ses tuiles, les bots et les
 *    salons dans sa position *Bots*, la nouvelle conversation et la multi-sélection dans son pied
 *    et sa tête, le menu d'une rangée à l'appui long (marquer lu, épingler, usage, archiver,
 *    supprimer). Le tableau du rapport de lot suit cette énumération.
 */
class AncienEcranConversationsTest {

    private val root = "src/main/java/com/onyx/app"
    private val app = read("$root/ui/OnyxApp.kt")
    private val layout = read("$root/ui/ResponsiveLayout.kt")
    private val sidebar = read("$root/ui/sidebar/ChatSidebar.kt")
    private val host = read("$root/ui/sidebar/ChatSidebarHost.kt")
    private val botsTab = read("$root/ui/sidebar/ChatSidebarBots.kt")

    private fun read(path: String) = File(path).readText()

    // MARK: - 1. La route ne reparaît pas

    @Test
    fun `la route des conversations n existe plus`() {
        val routes = app.substringAfter("private enum class Route {").substringBefore("\n}")
        assertFalse("`CONVERSATIONS` est revenu dans l'énum", routes.contains("CONVERSATIONS"))
        assertFalse("plus de route écrite dans la mise en page", app.contains("Route.CONVERSATIONS"))
        assertFalse("plus de route écrite dans la navigation", layout.contains("CONVERSATIONS"))
        assertFalse("plus de rail nommé", layout.contains("ConversationsRail"))
    }

    @Test
    fun `l ecran, ses fichiers et son entree de menu sont partis`() {
        assertFalse("le fichier de l'écran", File("$root/ui/conversations/ConversationsScreen.kt").exists())
        assertFalse("le fichier « All Servers », qui n'était que sa liste unifiée", File("$root/ui/conversations/AllServers.kt").exists())
        assertFalse("le nom du composable n'est plus appelé", app.contains("ConversationsScreen"))
        assertFalse("le panneau non plus", app.contains("conversationsPane"))
        assertFalse("« Toutes les conversations » du menu du fil est retirée", app.contains("All conversations"))
        assertFalse("la clé de langue aussi", read("../core/src/main/java/com/onyx/app/localization/OnyxLocalization.kt").contains("\"All conversations\""))
    }

    @Test
    fun `aucune entree de menu ne pointe dans le vide`() {
        // Tout `Route.X` écrit dans la mise en page existe encore dans l'énum : une route retirée
        // mais encore visée par une entrée, un retour ou un repli ferait échouer ce test.
        val routes = app.substringAfter("private enum class Route {").substringBefore("\n}")
        val declared = Regex("[A-Z][A-Z0-9_]{2,}").findAll(routes).map { it.value }.toSet()
        val used = Regex("(?<![A-Za-z_])Route\\.([A-Z][A-Z0-9_]{2,})").findAll(app).map { it.groupValues[1] }.toSet()
        assertEquals("des routes visées n'existent plus : ${used - declared}", emptySet<String>(), used - declared)
    }

    @Test
    fun `le repli des onglets de section est le fil`() {
        // La liste des conversations n'est plus une destination : Cron, Kanban et Code (les trois
        // sous-pages à repli propre) retombent sur le fil, jamais sur un écran absent.
        // Le repli est le fil : `inApp` se rabat sur `CHAT`, et porté par le volet le même retour
        // **dépile** (`paneBack`) au lieu de sauter une marche.
        assertTrue(
            "le repli hors du volet est le fil",
            app.contains("val inApp: () -> Unit = ({ if (!navBack()) navTo(Route.CHAT.name) })"),
        )
        assertEquals(
            "les trois onglets de section passent par le même retour",
            3,
            app.split(") paneBack else inApp").size - 1,
        )
        // Et ce retour ne dépile le volet que s'il porte **cet** écran (`isNavigating` était vrai
        // pour un volet fermé : la flèche dépilait alors une pile invisible).
        assertFalse(app.contains("if (sidebarState.isNavigating) paneBack else inApp"))
        assertFalse("plus aucun repli vers la liste", app.contains("navTo(Route.CONVERSATIONS.name)"))
    }

    // MARK: - 2. Aucune fonction perdue

    @Test
    fun `la liste des conversations et sa recherche vivent dans le volet`() {
        assertTrue("la recherche du volet", sidebar.contains("BasicText(text = \"Search chats\""))
        assertTrue("la liste des rangées", sidebar.contains("private fun ConversationList("))
        assertTrue("les rangées viennent du modèle de volet", host.contains("sidebarRows("))
    }

    @Test
    fun `Cron, Kanban et Code ouvrent leurs tuiles du volet`() {
        val tiles = SidebarDestinations.tiles.map { it.id }
        listOf("CRON", "KANBAN", "CODE").forEach { route ->
            assertTrue("la tuile $route du volet", route in tiles)
        }
        assertTrue("la grille de tuiles est dessinée", sidebar.contains("SidebarTileGrid("))
    }

    @Test
    fun `les bots et les salons vivent dans la position Bots du volet`() {
        assertTrue("la section des bots", botsTab.contains("SectionHeader(title = \"Bots\""))
        assertTrue("la section des salons", botsTab.contains("SectionHeader(title = \"Rooms\""))
        assertTrue("un salon nomme ses participants", botsTab.contains("row.members"))
        assertTrue("leurs visages sont entrelacés", botsTab.contains("InterlacedFaces("))
        // Le volet reçoit bien les deux sources : les bots du roster et les salles du serveur.
        assertTrue(host.contains("bots: List<BotProfile>,"))
        assertTrue(host.contains("groups: List<GroupChat>,"))
    }

    @Test
    fun `le crayon de la barre est le pied du volet`() {
        // « New conversation » : le bouton de la barre de l'écran retiré.
        assertTrue("le pied du volet", sidebar.contains("private fun SidebarFooter("))
        assertTrue("son libellé", sidebar.contains("New conversation"))
        assertTrue("son action", sidebar.contains("onNewConversation = onNewConversation"))
    }

    @Test
    fun `la coche de la barre est la selection du volet`() {
        // « Select » : la multi-sélection, puis archiver ou supprimer le lot.
        //
        // **Le contrôle de la tête n'est plus dessiné** : la planche montre **un seul** rond dans la
        // tête du volet, à l'encre neutre (brief « cinq divergences », n° 4). La multi-sélection
        // n'est **pas** retirée pour autant — elle s'ouvre par l'**appui long** d'une rangée, et
        // tout ce qu'elle portait reste en place : la case, la barre de sélection, le lot qui
        // remonte à l'écran, l'archivage et la suppression groupés, leur confirmation.
        assertTrue("le volet porte encore l'état de multi-sélection", sidebar.contains("SidebarSelectionState"))
        assertTrue("l'appui long d'une rangée l'ouvre", sidebar.contains("onLongPress = onLongPress?.let"))
        assertTrue("son câblage est encore déclaré", sidebar.contains("onSelect: (() -> Unit)? = null"))
        assertTrue("une rangée prend sa case", sidebar.contains("private fun SelectionMark("))
        assertTrue("la barre de sélection est portée", sidebar.contains("SelectionBar("))
        assertTrue("et son lot remonte à l'écran", sidebar.contains("onArchiveSelected(") && sidebar.contains("onDeleteSelected("))
        assertTrue("l'archivage groupé est câblé", app.contains("onArchiveConversations = { ids ->"))
        assertTrue("la suppression groupée aussi", app.contains("onDeleteConversations = { ids ->"))
        assertTrue("et elle demande confirmation", host.contains("Delete \$count conversation"))
    }

    @Test
    fun `le menu d une rangee garde les gestes de l ecran retire`() {
        // Les gestes de l'écran retiré vivent désormais dans le volet : le glissement vers la
        // gauche et l'appui long (la feuille du bas). Rien de ce qu'ils portaient ne se perd.
        val sheet = read("$root/ui/conversations/ConversationActionSheet.kt")
        assertTrue("l'appui long", sidebar.contains("onLongPress = onLongPress?.let"))
        assertTrue("le glissement vers la gauche", sidebar.contains("SwipeableConversationRow("))
        assertTrue("la feuille maison, pas une troisième", sidebar.contains("ConversationActionSheet("))
        assertTrue("marquer lu", sheet.contains("\"Mark as Read\" else \"Mark as Unread\""))
        assertTrue("épingler", sheet.contains("\"Unpin\" else \"Pin\""))
        assertTrue("usage et coût", sheet.contains("\"Usage & cost\""))
        assertTrue("archiver", sheet.contains("\"Unarchive\" else \"Archive\""))
        assertTrue("supprimer", sheet.contains("label = \"Delete\""))
        assertTrue("renommer", sheet.contains("label = \"Rename\""))
        assertTrue("créer un fil", sheet.contains("label = \"Create thread\""))
        assertTrue("les quatre gestes sont câblés", app.contains("onPinConversation = { conversation, pinned ->"))
        assertTrue("le renommage reste dans le menu du fil", app.contains("MenuItem(\"Rename\", Symbols.Pencil)"))
        assertTrue("« Usage & cost » ouvre la fiche d'usage", app.contains("navTo(Route.SESSION_USAGE.name)"))
    }

    @Test
    fun `les marques de lecture sont relues par la rangee`() {
        assertTrue("l'écran garde les marques", app.contains("var unreadMarks by remember"))
        assertTrue("et les passe au volet", app.contains("seenMarks = unreadMarks"))
        assertTrue("le volet s'en sert pour le ton de la rangée", host.contains("seenMarks[conversation.id]"))
    }
}
