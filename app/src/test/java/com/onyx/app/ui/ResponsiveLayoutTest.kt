package com.onyx.app.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onyx.app.localization.OnyxLocalization
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La géométrie des deux panneaux, telle que la demande de la capture du Honor Magic V3 déplié
 * (2344×2156 px ≈ 933×859 dp) l'impose: un volet borné (320…400 dp, jamais plus de 40 % de la
 * fenêtre), un détail qui garde 360 dp, un repli possible et persistant, et — sous ce que les
 * deux minima demandent — un seul panneau.
 */
class ResponsiveLayoutTest {

    /** Les largeurs qui portent réellement deux panneaux (paliers du pliable et de la tablette). */
    private val wideWidths = listOf(800.dp, 840.dp, 900.dp, 933.dp, 1024.dp, 1280.dp, 1920.dp)

    private fun assertDp(expected: Float, actual: Dp, tolerance: Float = 0.05f) {
        assertEquals("dp attendus", expected, actual.value, tolerance)
    }

    // — Le seuil: deux panneaux seulement quand les deux minima tiennent —

    @Test
    fun `le telephone compact conserve une seule colonne`() {
        val phone = listDetailLayout(360.dp)
        val smallFoldable = listDetailLayout(599.dp)

        assertFalse(phone.twoPanes)
        assertFalse(phone.expanded)
        assertDp(360f, phone.detailWidth)
        assertFalse(smallFoldable.twoPanes)
        assertDp(599f, smallFoldable.detailWidth)
    }

    /**
     * Entre 600 et 800 dp, l'ancien code ouvrait deux panneaux avec un volet rogné et un détail
     * sous ses minima. La règle d'téléphone est plus stricte: pas de deux panneaux tant que le
     * volet (320 dp) et le détail (360 dp) ne tiennent pas ensemble, volet ≤ 40 %.
     */
    @Test
    fun `sous ce que les deux minima demandent l app repasse a un seul panneau`() {
        listOf(600.dp, 720.dp, 799.dp).forEach { width ->
            assertFalse("$width devrait rester à un panneau", canShowTwoPanes(width))
            val layout = listDetailLayout(width)
            assertFalse(layout.twoPanes)
            assertDp(0f, layout.sidebarWidth)
            assertDp(width.value, layout.detailWidth)
        }
        assertTrue(canShowTwoPanes(800.dp))
    }

    // — Les bornes du volet: 320…400 dp, jamais plus de 40 % —

    @Test
    fun `le volet ne sort jamais de ses bornes`() {
        wideWidths.forEach { width ->
            val layout = listDetailLayout(width)

            assertTrue("$width doit être en deux panneaux", layout.twoPanes)
            assertTrue("$width : volet visible", layout.sidebarVisible)
            assertTrue("$width : volet sous le minimum", layout.sidebarWidth >= SidebarMinWidth)
            assertTrue("$width : volet au-dessus du maximum", layout.sidebarWidth <= SidebarMaxWidth)
        }
    }

    @Test
    fun `la largeur ideale est celle d iPadOS des que la fenetre le permet`() {
        listOf(900.dp, 933.dp, 1024.dp, 1280.dp, 1920.dp).forEach { width ->
            assertDp(360f, listDetailLayout(width).sidebarWidth)
        }
        // Au tout premier palier, le plafond de 40 % rogne le volet sous son idéal.
        assertDp(320f, listDetailLayout(800.dp).sidebarWidth)
        assertDp(330f, listDetailLayout(825.dp).sidebarWidth)
    }

    @Test
    fun `le volet ne prend jamais plus de 40 pour cent de la fenetre`() {
        wideWidths.forEach { width ->
            val layout = listDetailLayout(width)
            val share = layout.sidebarWidth.value / width.value
            assertTrue("$width : volet à ${share * 100}%", share <= SidebarMaxFraction + 0.0001f)
        }
    }

    @Test
    fun `le detail garde 360 dp et les deux panneaux occupent toute la largeur`() {
        wideWidths.forEach { width ->
            val layout = listDetailLayout(width)

            assertTrue("$width : détail trop étroit", layout.detailWidth >= DetailMinWidth)
            assertDp(width.value, layout.sidebarWidth + layout.detailWidth)
        }
    }

    // — Le repli: le détail prend tout, le volet garde sa largeur de référence —

    @Test
    fun `replie le detail prend toute la largeur`() {
        wideWidths.forEach { width ->
            val collapsed = listDetailLayout(width, collapsed = true)

            assertTrue("$width : la fenêtre a toujours la place", collapsed.twoPanes)
            assertFalse("$width : plus de volet dessiné", collapsed.sidebarVisible)
            assertFalse("$width : pas étendu", collapsed.expanded)
            assertDp(width.value, collapsed.detailWidth)
            // La largeur géométrique du volet reste celle qui servira au dépliage.
            assertDp(listDetailLayout(width).sidebarWidth.value, collapsed.sidebarWidth)
        }
    }

    @Test
    fun `le repli ne replie rien sur une fenetre etroite`() {
        val collapsed = listDetailLayout(411.dp, collapsed = true)

        assertFalse(collapsed.twoPanes)
        assertFalse(collapsed.sidebarVisible)
        assertDp(0f, collapsed.sidebarWidth)
        assertDp(411f, collapsed.detailWidth)
    }

    /**
     * La géométrie exacte du Magic V3 déplié: 933 dp de large. L'ancien partage donnait ~541 dp
     * au volet (58 %) et 392 dp au fil; ici le volet tient dans 360 dp et le fil prend 573 dp.
     */
    @Test
    fun `le volet du pliable deplie tient dans 360 dp`() {
        val unfolded = listDetailLayout(933.dp)

        assertDp(360f, unfolded.sidebarWidth)
        assertDp(573f, unfolded.detailWidth)
        assertTrue(unfolded.detailWidth > unfolded.sidebarWidth)
    }

    // — Le tableau Kanban suit la même géométrie que les autres volets —

    @Test
    fun `le tableau et la conversation se partagent la largeur etendue`() {
        wideWidths.forEach { width ->
            val pair = kanbanPairLayout(width)

            assertTrue("$width devrait être en deux panneaux", pair.twoPanes)
            assertTrue("$width : conversation trop étroite", pair.detailWidth >= DetailMinWidth)
            assertTrue(
                "$width : tableau au-dessus de 40 %",
                pair.sidebarWidth.value / width.value <= SidebarMaxFraction + 0.0001f,
            )
            assertDp(width.value, pair.sidebarWidth + pair.detailWidth)
        }
    }

    @Test
    fun `le tableau Kanban suit exactement la geometrie des autres volets`() {
        wideWidths.forEach { width ->
            val list = listDetailLayout(width)
            val pair = kanbanPairLayout(width)

            assertEquals("$width : même deux-panneaux", list.twoPanes, pair.twoPanes)
            assertDp(list.sidebarWidth.value, pair.sidebarWidth)
            assertDp(list.detailWidth.value, pair.detailWidth)
        }
        // L'ancien partage 58/42 n'existe plus: le tableau n'est plus le plus large.
        assertTrue(kanbanPairLayout(933.dp).detailWidth > kanbanPairLayout(933.dp).sidebarWidth)
    }

    @Test
    fun `sous le seuil la conversation s ouvre par dessus le tableau`() {
        val phone = kanbanPairLayout(411.dp)

        assertFalse(phone.expanded)
        assertDp(411f, phone.detailWidth)
        assertDp(0f, phone.sidebarWidth)
    }

    @Test
    fun `le tableau Kanban se replie comme les autres volets`() {
        val collapsed = kanbanPairLayout(933.dp, collapsed = true)

        assertTrue(collapsed.twoPanes)
        assertFalse(collapsed.sidebarVisible)
        assertDp(933f, collapsed.detailWidth)
    }

    // — Le libellé du contrôle: localisé, et il dit ce que le prochain appui fera —

    @Test
    fun `le controle du volet est localise dans les deux sens`() {
        val locale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            assertEquals("Replier la liste", OnyxLocalization["Collapse list"])
            assertEquals("Déplier la liste", OnyxLocalization["Expand list"])
        } finally {
            Locale.setDefault(locale)
        }
    }

    // — La navigation autour des panneaux (inchangée) —

    @Test
    fun `la selection compacte rouvre le chat sans empiler un faux retour`() {
        val backStack = mutableListOf("CHAT")

        val route = navigateWithBackStack("CONVERSATIONS", "CHAT", backStack)

        assertEquals("CHAT", route)
        assertTrue(backStack.isEmpty())
    }

    @Test
    fun `une nouvelle conversation compacte rouvre le meme chat sans doublon`() {
        val backStack = mutableListOf("CHAT")

        val route = navigateWithBackStack("CONVERSATIONS", "CHAT", backStack)
        val repeated = navigateWithBackStack(route, "CHAT", backStack)

        assertEquals("CHAT", repeated)
        assertTrue(backStack.isEmpty())
    }

    @Test
    fun `le retour du bord restaure le detail precedent en mode etendu`() {
        val backStack = mutableListOf<String>()
        val chat = navigateWithBackStack("CRON", "CHAT", backStack)

        assertEquals("CHAT", chat)
        assertEquals(listOf("CRON"), backStack)
        assertEquals("CRON", popRoute(backStack))
        assertTrue(backStack.isEmpty())
        assertNull(popRoute(backStack))
    }

    @Test
    fun `la liste compacte devient le detail etendu sans faux retour`() {
        val backStack = mutableListOf("SETTINGS", "CHAT")

        val route = routeWhenExpanded(
            currentRoute = "CONVERSATIONS",
            backStack = backStack,
            conversationsRoute = "CONVERSATIONS",
            chatRoute = "CHAT",
        )

        assertEquals("CHAT", route)
        assertEquals(listOf("SETTINGS"), backStack)
    }

    @Test
    fun `un detail deja etendu ne change pas de route au redimensionnement`() {
        val backStack = mutableListOf("CHAT")

        val route = routeWhenExpanded(
            currentRoute = "CRON",
            backStack = backStack,
            conversationsRoute = "CONVERSATIONS",
            chatRoute = "CHAT",
        )

        assertEquals("CRON", route)
        assertEquals(listOf("CHAT"), backStack)
    }

    // — Les retours (audit des routes) —

    @Test
    fun `un onglet de section ouvert depuis les Reglages ne renvoie pas aux Reglages`() {
        // Réglages ouverts par-dessus la liste: la pile porte le fil et la liste.
        val backStack = mutableListOf("CHAT", "CONVERSATIONS")

        val route = navigateWithBackStack("SETTINGS", "KANBAN", backStack)

        assertEquals("KANBAN", route)
        // La feuille ne reste pas dessous: il n'y a plus rien à dépiler…
        assertTrue(backStack.isEmpty())
        assertNull(popRoute(backStack))
        // …donc le retour retombe sur la liste des conversations, jamais sur les Réglages.
        assertEquals(
            "CONVERSATIONS",
            rootRouteBehind("KANBAN", twoPanes = false, chatRoute = "CHAT", conversationsRoute = "CONVERSATIONS"),
        )
    }

    @Test
    fun `le meme chemin vaut pour l onglet Cron`() {
        val backStack = mutableListOf("CHAT", "CONVERSATIONS")

        assertEquals("CRON", navigateWithBackStack("SETTINGS", "CRON", backStack))
        assertTrue(backStack.isEmpty())
        assertEquals(
            "CONVERSATIONS",
            rootRouteBehind("CRON", twoPanes = false, chatRoute = "CHAT", conversationsRoute = "CONVERSATIONS"),
        )
    }

    @Test
    fun `un onglet de section reste la racine en deux panneaux`() {
        val backStack = mutableListOf("CHAT", "CONVERSATIONS")

        assertEquals("KANBAN", navigateWithBackStack("SETTINGS", "KANBAN", backStack))
        assertTrue(backStack.isEmpty())
        // Deux panneaux: la liste est déjà le fond de l'écran.
        assertEquals(
            "CONVERSATIONS",
            rootRouteBehind("KANBAN", twoPanes = true, chatRoute = "CHAT", conversationsRoute = "CONVERSATIONS"),
        )
    }

    @Test
    fun `le fil reste la racine compacte partout ailleurs`() {
        assertEquals("CHAT", rootRouteBehind("CHAT", false, "CHAT", "CONVERSATIONS"))
        assertEquals("CHAT", rootRouteBehind("SETTINGS", false, "CHAT", "CONVERSATIONS"))
        // Depuis la liste elle-même le retour va au fil: le rail mène de l'un à l'autre.
        assertEquals("CHAT", rootRouteBehind("CONVERSATIONS", false, "CHAT", "CONVERSATIONS"))
    }

    @Test
    fun `les Reglages referment la feuille et reviennent a l ecran quitte`() {
        // Ouverts depuis la liste: « Terminé » rend la liste, pas le fil.
        assertEquals("CONVERSATIONS", popRoute(mutableListOf("CHAT", "CONVERSATIONS")))
        // Ouverts depuis le fil: « Terminé » rend le fil.
        assertEquals("CHAT", popRoute(mutableListOf("CHAT")))
        // Pile vide (premier lancement): le repli est le fil.
        assertNull(popRoute(mutableListOf()))
    }

    @Test
    fun `une conversation ouverte depuis la liste ramene a la liste au retour`() {
        // Le rail mène du fil à la liste; ouvrir une conversation depuis la liste empile la
        // liste, et le retour y revient. Avant, la pile gardait le fil: elle était dépilée à
        // l'ouverture de la conversation, si bien que le retour laissait une pile **vide** sur le
        // fil — l'app se fermait et l'appareil retombait sur le lanceur (mesuré sur l'appareil).
        val backStack = mutableListOf<String>()

        assertEquals("CONVERSATIONS", navigateWithBackStack("CHAT", "CONVERSATIONS", backStack))
        assertTrue(backStack.isEmpty())
        assertEquals("CHAT", navigateWithBackStack("CONVERSATIONS", "CHAT", backStack))
        assertEquals(listOf("CONVERSATIONS"), backStack)
        assertEquals("CONVERSATIONS", popRoute(backStack))
    }

    // — Ce qui tient la demande dans le code (le volet, les rangées, le composeur) —

    private val appSource = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()
    private val layoutSource = File("src/main/java/com/onyx/app/ui/ResponsiveLayout.kt").readText()
    private val kanbanSource = File("src/main/java/com/onyx/app/ui/kanban/KanbanScreen.kt").readText()
    private val composerSource = File("src/main/java/com/onyx/app/ui/chat/components/OnyxComposer.kt").readText()
    private val chatSource = File("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt").readText()
    private val settingsRepo = File("src/main/java/com/onyx/app/data/repo/SettingsRepository.kt").readText()

    @Test
    fun `le volet s anime au lieu de sauter`() {
        assertTrue(layoutSource.contains("animateDpAsState("))
        assertTrue(layoutSource.contains("OnyxMotion.Sites.SidePanelWidth"))
        assertTrue(layoutSource.contains("if (animatedWidth > 0.dp)"))
    }

    @Test
    fun `le repli est un reglage de l appareil, relu par la racine`() {
        assertTrue(settingsRepo.contains("val sidebarCollapsed: StateFlow<Boolean>"))
        assertTrue(settingsRepo.contains("suspend fun setSidebarCollapsed(value: Boolean)"))
        assertTrue(settingsRepo.contains("booleanPreferencesKey(\"sidebarCollapsed\")"))
        assertTrue(appSource.contains("val sidebarCollapsed by settingsRepo.sidebarCollapsed.collectAsStateWithLifecycle()"))
        assertTrue(appSource.contains("settingsRepo.setSidebarCollapsed(true)"))
        assertTrue(appSource.contains("settingsRepo.setSidebarCollapsed(false)"))
        assertTrue(appSource.contains("val layout = listDetailLayout(maxWidth, sidebarCollapsed)"))
    }

    @Test
    fun `le controle est porte par toutes les routes en deux panneaux`() {
        // Le volet des routes de liste, et le tableau Kanban.
        assertTrue(appSource.contains("conversationsPane(true, presentedRoute, collapseControl)"))
        assertTrue(appSource.contains("kanbanPane(true, kanbanTopRow, collapseControl)"))
        // Replié, c'est la barre du détail qui le porte: fil, horaires, bots, Kanban, Réglages.
        assertTrue(appSource.contains("chatPane(false, expandControl)"))
        assertTrue(appSource.contains("Route.CRON -> cronPane(true, expandControl)"))
        assertTrue(appSource.contains("Route.BOTS -> botsPane(true, expandControl)"))
        assertTrue(appSource.contains("kanbanPane(true, null, expandControl)"))
        assertTrue(appSource.contains("sidebarControl = expandControl ?: collapseControl"))
        // Les deux panneaux sont décidés par la géométrie, pas par l'ancien 600 dp.
        assertTrue(appSource.contains("layout.twoPanes && presentedRoute in TwoPaneRoutes"))
    }

    @Test
    fun `le controle est dessine par les barres des cinq ecrans`() {
        assertTrue(kanbanSource.contains("leading = sidebarControl"))
        assertTrue(File("src/main/java/com/onyx/app/ui/cron/CronScreen.kt").readText().contains("leading = sidebarControl"))
        assertTrue(File("src/main/java/com/onyx/app/ui/bots/BotsScreen.kt").readText().contains("leading = sidebarControl"))
        assertTrue(File("src/main/java/com/onyx/app/ui/settings/SettingsScreen.kt").readText().contains("leading = sidebarControl"))
        assertTrue(File("src/main/java/com/onyx/app/ui/conversations/ConversationsScreen.kt").readText().contains("sidebarControl?.let {"))
        assertTrue(chatSource.contains("sidebarToggle = sidebarToggle"))
    }

    @Test
    fun `une carte Kanban reserve la largeur du bouton de menu`() {
        assertTrue(kanbanSource.contains("internal val KanbanRowMenuSize: Dp = 44.dp"))
        assertTrue(kanbanSource.contains("size(KanbanRowMenuSize)"))
        assertTrue(kanbanSource.contains("heightIn(min = KanbanRowMinHeight)"))
        assertTrue(kanbanSource.contains("maxLines = 2"))
        assertTrue(kanbanSource.contains("overflow = TextOverflow.Ellipsis"))
    }

    @Test
    fun `le placeholder du composeur tient sur une ligne`() {
        assertTrue(composerSource.contains("text = placeholder"))
        assertTrue(composerSource.contains("maxLines = 1"))
        assertTrue(composerSource.contains("overflow = TextOverflow.Ellipsis"))
    }

    @Test
    fun `le bouton du dernier message a une marge du bord`() {
        assertTrue(chatSource.contains(".padding(start = 16.dp, end = 16.dp, bottom = composerHeight + 8.dp)"))
    }

    @Test
    fun `les sous-pages depilent avant de se rabattre sur leur parent logique`() {
        // Réglages: « Terminé » revient à l'écran quitté, pas systématiquement au fil.
        assertTrue(appSource.contains("onDone = { if (!navBack()) navTo(Route.CHAT.name) }"))
        // Les pages Modèle, Voix, salle de groupe: même contrat.
        assertTrue(appSource.contains("onBack = { if (!navBack()) navTo(Route.CHAT.name) }"))
        assertTrue(appSource.contains("onBack = { if (!navBack()) navTo(Route.SETTINGS.name) }"))
        assertTrue(appSource.contains("onBack = { if (!navBack()) navTo(Route.BOTS.name) }"))
        // Le retour à pile vide passe par la règle partagée, plus par une route écrite en dur.
        assertTrue(appSource.contains("rootRouteBehind("))
        assertTrue(layoutSource.contains("internal val SectionTabRoutes: Set<String>"))
        assertTrue(layoutSource.contains("if (targetRoute in SectionTabRoutes) {"))
        // Les **deux** onglets de section retombent sur la liste des conversations.
        val listFallback = "if (!navBack()) navTo(Route.CONVERSATIONS.name) })"
        assertEquals(2, appSource.split(listFallback).size - 1)
        // Plus aucun onglet ne se rabat sur les Réglages quand la pile est vide.
        assertFalse(appSource.contains("onBack = { navTo(Route.SETTINGS.name) },\n                sidebarControl = sidebarControl,"))
        // Toute entrée de route passe par la pile: une affectation directe de `route` laissait
        // la pile intacte et le retour dépilait l'entrée d'avant.
        val direct = Regex(
            "^\\s*route = (?!previous|navigateWithBackStack|routeWhenExpanded|navigateLeavingEmpty)",
            RegexOption.MULTILINE,
        ).findAll(appSource).toList()
        assertTrue("aucune route écrite en direct : $direct", direct.isEmpty())
    }
}
