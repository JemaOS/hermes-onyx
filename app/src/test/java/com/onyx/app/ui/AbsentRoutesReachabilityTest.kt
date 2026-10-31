package com.onyx.app.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aucun écran orphelin : chacune des destinations portées est déclarée dans l'énum `Route`,
 * dessinée par la branche `when` du routeur, **et** ouverte par une entrée réelle de l'interface —
 * un réglage, une tuile de la grille, une ligne de menu, une rangée de projet.
 */
class AbsentRoutesReachabilityTest {

    private val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()
    private val settings = File("src/main/java/com/onyx/app/ui/settings/SettingsScreen.kt").readText()
    private val bots = File("src/main/java/com/onyx/app/ui/bots/BotsScreen.kt").readText()
    private val volet = File("src/main/java/com/onyx/app/ui/sidebar/ChatSidebar.kt").readText()
    private val sheet = File("src/main/java/com/onyx/app/ui/conversations/ConversationActionSheet.kt").readText()

    /** La branche complète d'une route dans le `when (presentedRoute)`. */
    private fun branch(name: String): String =
        app.substringAfter("Route.$name ->").substringBefore("\n            Route.")

    @Test
    fun `les quatre routes sont declarees`() {
        val routes = app.substringAfter("private enum class Route {").substringBefore("\n}")
        listOf("CONNECTION_DETAILS", "HIDDEN_BOTS", "SESSION_USAGE", "PROJECT_SESSIONS").forEach { name ->
            assertTrue("Route.$name est déclarée", routes.contains(name))
        }
    }

    @Test
    fun `chaque route est dessinee par le routeur`() {
        assertTrue(branch("CONNECTION_DETAILS").contains("ConnectionDetailsScreen("))
        assertTrue(branch("HIDDEN_BOTS").contains("HiddenBotsScreen("))
        assertTrue(branch("SESSION_USAGE").contains("SessionUsageScreen("))
        assertTrue(branch("PROJECT_SESSIONS").contains("ProjectSessionsScreen("))
    }

    @Test
    fun `chaque route a un chemin de retour`() {
        assertTrue(branch("CONNECTION_DETAILS").contains("navTo(Route.SETTINGS.name)"))
        assertTrue(branch("HIDDEN_BOTS").contains("navTo(Route.BOTS.name)"))
        assertTrue(branch("SESSION_USAGE").contains("navTo(Route.CHAT.name)"))
        assertTrue(branch("PROJECT_SESSIONS").contains("navTo(Route.CHAT.name)"))
    }

    @Test
    fun `Connection details s ouvre depuis les Reglages`() {
        assertTrue(settings.contains("label = \"Connection details\""))
        assertTrue(app.contains("onOpenConnectionDetails = { navTo(Route.CONNECTION_DETAILS.name) }"))
    }

    @Test
    fun `Hidden Bots s ouvre depuis la grille des bots`() {
        assertTrue(bots.contains("title = \"Hidden\""))
        assertTrue(app.contains("hiddenCount = botsState.hidden.size"))
        assertTrue(app.contains("onOpenHiddenBots = { navTo(Route.HIDDEN_BOTS.name) }"))
    }

    @Test
    fun `Usage et cout s ouvre depuis le menu d une conversation`() {
        // « Usage & cost » vit maintenant dans **la feuille du bas** de la conversation (le menu de
        // l'appui long) : il reste atteignable depuis une rangée du volet, et ouvre l'écran.
        assertTrue(sheet.contains("\"Usage & cost\""))
        assertTrue("la rangée du volet ouvre la feuille", volet.contains("ConversationActionSheet("))
        assertTrue(app.contains("onOpenUsageConversation = { conversation ->"))
        assertTrue(app.contains("navTo(Route.SESSION_USAGE.name)"))
    }

    @Test
    fun `le menu d une rangee s ouvre vraiment, par un appui long`() {
        // Sans ce geste, le menu — et « Usage & cost » avec lui — serait un écran orphelin.
        assertTrue(volet.contains("onLongPress = onLongPress?.let { press -> { bounds?.let(press) } }"))
        val interactions = File("../core/src/main/java/com/onyx/app/ui/design/Interactions.kt").readText()
        assertTrue(interactions.contains("onLongClick = onLongPress"))
        assertTrue(interactions.contains("combinedClickable("))
    }

    @Test
    fun `les sessions d un projet s ouvrent depuis la section Projects`() {
        assertTrue(volet.contains("SidebarProjects("))
        assertTrue(app.contains("onOpenProject = { project ->"))
        assertTrue(app.contains("navTo(Route.PROJECT_SESSIONS.name)"))
    }

    @Test
    fun `New Group Chat ouvre la salle avec les bots choisis`() {
        // La création d'une salle vit dans l'écran des bots ; la salle choisie s'ouvre par la
        // pile, et c'est `groupPicked` que la route de la salle relit.
        assertTrue(app.contains("onGroupChat = {"))
        assertTrue(app.contains("groupPicked = picked"))
        assertTrue(app.contains("groupViewModel.setSelected(picked)"))
        assertTrue(app.contains("title = groupTitle"))
    }

    @Test
    fun `Erase everything part des Reglages et efface avant de revenir`() {
        assertTrue(settings.contains("text = \"Erase everything…\""))
        assertTrue(app.contains("onEraseEverything = {"))
        assertTrue(app.contains("AppContainer.eraseEverything()"))
        assertTrue(app.contains("chatViewModel.newConversation()"))
    }

    @Test
    fun `le retour du systeme n est jamais avale`() {
        // Sur Réglages en deux panneaux, la pile vide faisait rendre faux à `navBack()` et le geste
        // du bord ne faisait rien. Le retour remonte désormais toujours quelque part.
        val source = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()
        assertTrue("une condition de retour explicite", source.contains("val backHandled = routeBackHandled("))
        // Le repli à pile vide passe par **une seule** règle (`repliDeRetour`) : le repli déclaré
        // de la route dans la table des retours, et `rootRouteBehind` en dernier recours — la même
        // cible que la flèche de la page (voir `FlecheInterneVerrouTest`).
        assertTrue(
            "le retour remonte au lieu de ne rien faire",
            source.contains("if (!navBack()) {") && source.contains("navTo(repliDeRetour())"),
        )
        assertTrue(
            "l'écran de repli vient de la règle partagée",
            source.contains("TableDesRetours.de(route)?.repli") &&
                source.contains("?: rootRouteBehind(currentRoute = route, chatRoute = Route.CHAT.name)"),
        )
    }
}
