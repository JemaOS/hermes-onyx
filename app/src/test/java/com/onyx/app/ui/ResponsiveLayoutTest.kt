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
 * (2344×2156 px ≈ 933×859 dp) l'impose : un volet borné (320…400 dp, jamais plus de 40 % de la
 * fenêtre), un détail qui garde 360 dp, un repli possible et persistant, et — sous ce que les
 * deux minima demandent — un seul panneau.
 */
class ResponsiveLayoutTest {

    /** Les largeurs qui portent réellement deux panneaux (paliers du pliable et de la tablette). */
    private val wideWidths = listOf(800.dp, 840.dp, 900.dp, 933.dp, 1024.dp, 1280.dp, 1920.dp)

    private fun assertDp(expected: Float, actual: Dp, tolerance: Float = 0.05f) {
        assertEquals("dp attendus", expected, actual.value, tolerance)
    }

    // — Le seuil : deux panneaux seulement quand les deux minima tiennent —

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
     * sous ses minima. La règle de la référence est plus stricte : pas de deux panneaux tant que le
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

    // — Les bornes du volet : 320…400 dp, jamais plus de 40 % —

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
    fun `la largeur ideale est celle de la reference des que la fenetre le permet`() {
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

    // — Le repli : le détail prend tout, le volet garde sa largeur de référence —

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
     * La géométrie exacte du Magic V3 déplié : 933 dp de large. L'ancien partage donnait ~541 dp
     * au volet (58 %) et 392 dp au fil ; ici le volet tient dans 360 dp et le fil prend 573 dp.
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
        // L'ancien partage 58/42 n'existe plus : le tableau n'est plus le plus large.
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

    // — Le libellé du contrôle : localisé, et il dit ce que le prochain appui fera —

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

        val route = navigateWithBackStack("SKILLS", "CHAT", backStack)

        assertEquals("CHAT", route)
        // Le fil était déjà dans la pile : il est dépilé, jamais empilé deux fois.
        assertTrue(backStack.isEmpty())
    }

    @Test
    fun `une conversation ouverte depuis le volet revient au fil`() {
        // La liste n'est plus une route : ouvrir une conversation depuis le volet **va** au fil,
        // sans empiler la liste absente — le retour remonte donc la pile telle qu'elle était.
        val backStack = mutableListOf("SETTINGS")

        val route = navigateWithBackStack("SKILLS", "CHAT", backStack)

        assertEquals("CHAT", route)
        assertEquals(listOf("SETTINGS", "SKILLS"), backStack)
        assertEquals("SKILLS", popRoute(backStack))
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

    // — Les retours (audit des routes) —

    @Test
    fun `un onglet de section ouvert depuis les Reglages revient aux Reglages`() {
        // Réglages ouverts par-dessus une page : la pile porte le fil et cette page.
        val backStack = mutableListOf("CHAT", "SKILLS")

        val route = navigateWithBackStack("SETTINGS", "KANBAN", backStack)

        assertEquals("KANBAN", route)
        // La feuille reste dessous : la route quittée (les Réglages) est entrée dans la pile…
        assertEquals(listOf("CHAT", "SKILLS", "SETTINGS"), backStack)
        // …donc le retour du bord — comme le bouton retour maison, `navBack()` — rend les Réglages.
        assertEquals("SETTINGS", popRoute(backStack))
    }

    @Test
    fun `le meme chemin vaut pour l onglet Cron`() {
        val backStack = mutableListOf("CHAT", "SKILLS")

        assertEquals("CRON", navigateWithBackStack("SETTINGS", "CRON", backStack))
        assertEquals(listOf("CHAT", "SKILLS", "SETTINGS"), backStack)
        assertEquals("SETTINGS", popRoute(backStack))
    }

    @Test
    fun `un onglet de section atteint depuis un onglet reste dans le rail`() {
        val backStack = mutableListOf("CHAT", "SKILLS")

        // De Cron à Kanban : c'est un onglet, pas une page — la pile est remise au fond.
        assertEquals("KANBAN", navigateWithBackStack("CRON", "KANBAN", backStack))
        assertTrue(backStack.isEmpty())
        // La liste des conversations n'est plus une route : le repli est le fil, dans les deux
        // largeurs — un onglet de section à pile vide y retombe comme tout le reste.
        assertEquals("CHAT", rootRouteBehind("KANBAN", chatRoute = "CHAT"))
        assertEquals("CHAT", rootRouteBehind("CRON", chatRoute = "CHAT"))
    }

    @Test
    fun `le fil est la racine partout`() {
        assertEquals("CHAT", rootRouteBehind("CHAT", "CHAT"))
        assertEquals("CHAT", rootRouteBehind("SETTINGS", "CHAT"))
        assertEquals("CHAT", rootRouteBehind("KANBAN", "CHAT"))
    }

    @Test
    fun `les Reglages referment la feuille et reviennent a l ecran quitte`() {
        // Ouverts depuis une page : « Terminé » rend cette page, pas le fil.
        assertEquals("SKILLS", popRoute(mutableListOf("CHAT", "SKILLS")))
        // Ouverts depuis le fil : « Terminé » rend le fil.
        assertEquals("CHAT", popRoute(mutableListOf("CHAT")))
        // Pile vide (premier lancement) : le repli est le fil.
        assertNull(popRoute(mutableListOf()))
    }

    @Test
    fun `un onglet de section ouvert depuis une page y ramene au retour`() {
        // Les pages qui ne sont pas des onglets de section sont **poussées** : une page → Cron →
        // retour du bord rend cette page, et un onglet de section à un autre remet la pile au fond.
        val backStack = mutableListOf<String>()

        assertEquals("CRON", navigateWithBackStack("SKILLS", "CRON", backStack))
        assertEquals(listOf("SKILLS"), backStack)
        assertEquals("KANBAN", navigateWithBackStack("CRON", "KANBAN", backStack))
        assertTrue("d'un onglet de section à un autre, la pile est au fond", backStack.isEmpty())
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
        // Le repli est écrit depuis **l'état du volet** (une mémoire par largeur), plus depuis deux
        // boutons qui écrivaient chacun leur tour : un seul geste décide, et l'état le garde.
        assertTrue(appSource.contains("settingsRepo.setSidebarCollapsed(!sidebarState.isOpen)"))
        assertTrue(appSource.contains("val sidebarState = com.onyx.app.ui.sidebar.rememberOnyxSidebarState()"))
        assertTrue(appSource.contains("val sidebarMode = sidebarModeFor(maxWidth)"))
        assertTrue(appSource.contains("LaunchedEffect(sidebarMode) { sidebarState.updateMode(sidebarMode) }"))
        // La géométrie des deux panneaux suit l'ouverture du volet **et** ce qu'il porte : porteur
        // d'un écran, il prend la largeur d'un écran (la colonne de la liste l'écraserait).
        assertTrue(appSource.contains("collapsed = sidebarMode == SidebarMode.DOCKED && !sidebarState.isOpen,"))
        assertTrue(appSource.contains("content = sidebarState.isNavigating,"))
    }

    @Test
    fun `le volet pose portant un ecran prend la largeur d un ecran`() {
        listOf(800.dp, 838.dp, 900.dp, 933.dp, 1024.dp, 1280.dp, 1920.dp).forEach { width ->
            val list = listDetailLayout(width)
            val content = listDetailLayout(width, content = true)

            assertTrue("$width : le volet porteur d'un écran est plus large", content.sidebarWidth > list.sidebarWidth)
            assertTrue("$width : volet sous la largeur d'un écran", content.sidebarWidth >= SidebarContentMinWidth)
            assertTrue("$width : volet au-dessus du plafond", content.sidebarWidth <= SidebarContentMaxWidth)
            assertTrue("$width : volet plus large que la fenêtre", content.sidebarWidth <= width)
            assertTrue("$width : détail trop étroit", content.detailWidth >= 320.dp)
            assertDp(width.value, content.sidebarWidth + content.detailWidth)
            // Replié, c'est le détail qui prend tout, quelle que soit la largeur du volet.
            assertDp(width.value, listDetailLayout(width, collapsed = true, content = true).detailWidth)
        }
    }

    @Test
    fun `le controle est porte par toutes les routes en deux panneaux`() {
        // Le volet des routes de liste — c'est le volet du Chat qui le porte —, et le tableau Kanban.
        assertTrue(appSource.contains("sidebar = { chatSidebar() },"))
        assertTrue(appSource.contains("SidebarScaffold(state = sidebarState, sidebar = { chatSidebar() }, content = { inner() })"))
        assertTrue(appSource.contains("kanbanPane(true, kanbanTopRow, collapseControl)"))
        // Replié, c'est la barre du détail qui le porte : fil, horaires, bots, Kanban, Réglages.
        assertTrue(appSource.contains("chatPane(false, expandControl)"))
        assertTrue(appSource.contains("Route.CRON -> cronPane(true, expandControl)"))
        assertTrue(appSource.contains("Route.BOTS -> botsPane(true, expandControl)"))
        assertTrue(appSource.contains("kanbanPane(true, null, expandControl)"))
        assertTrue(appSource.contains("sidebarControl = expandControl ?: collapseControl"))
        // Les deux panneaux sont décidés par la géométrie, pas par l'ancien 600 dp.
        assertTrue(appSource.contains("layout.twoPanes && presentedRoute in TwoPaneRoutes"))
    }

    @Test
    fun `le controle est dessine par les barres des ecrans`() {
        assertTrue(kanbanSource.contains("leading = sidebarControl"))
        // Le rond du volet a été **retiré** de l'en-tête de Planifiés (ordre explicite du
        // propriétaire, 2026-10-30 : « supprime, elle ne sert à rien »). Ce rond portait
        // `sidebarState.toggle()` — replier/déplier la liste — et non une pause ; la pause d'une
        // tâche vit sur la tâche. Le verrou refuse donc son retour : le réintroduire rougit.
        assertFalse(
            "le rond du volet ne revient pas dans l'en-tête de Planifiés",
            File("src/main/java/com/onyx/app/ui/cron/CronScreen.kt").readText().contains("panel = sidebarControl"),
        )
        assertTrue(File("src/main/java/com/onyx/app/ui/bots/BotsScreen.kt").readText().contains("leading = sidebarControl"))
        assertTrue(File("src/main/java/com/onyx/app/ui/settings/SettingsScreen.kt").readText().contains("actions = sidebarControl"))
        assertTrue(File("src/main/java/com/onyx/app/ui/sidebar/ChatSidebar.kt").readText().contains("onOpenSettings = onOpenSettings"))
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
        // **Le libellé de la plaque est choisi entre deux clés** (celle de la planche, et la même
        // sans l'ellipse quand la place manque : voir `PlaqueInvite`) : la source ne porte donc plus
        // un seul `text = placeholder`, mais les deux cas — et les deux tiennent sur **une** ligne.
        assertTrue(
            composerSource.contains("OnyxLocalization[PlaqueInvite.key(maxWidth.value, measureInvite)]"),
        )
        assertTrue(composerSource.contains("placeholder"))
        assertTrue(composerSource.contains("maxLines = if (plate) 1 else 2"))
        assertTrue(composerSource.contains("maxLines = 1"))
        assertTrue(composerSource.contains("overflow = TextOverflow.Ellipsis"))
    }

    @Test
    fun `aucun libelle du volet ne se coupe au milieu d un mot`() {
        // La règle : tout libellé à lignes bornées porte une **ellipse**, jamais une coupure sèche
        // qui briserait un mot à 1,3× (« Nouvelle conver », « Vous atten ») ou sortirait de sa
        // pastille. Le bouton « Nouvelle conversation », les pastilles de filtre et leurs comptes,
        // l'âge d'une rangée et le pavé d'action sont les cas visés.
        val source = File("src/main/java/com/onyx/app/ui/sidebar/ChatSidebar.kt").readText()
        val lignes = source.lines()
        val nues = mutableListOf<String>()
        lignes.forEachIndexed { index, ligne ->
            if (ligne.trim().startsWith("maxLines =") || ligne.trim().startsWith("maxLines=")) {
                val fenetre = lignes.subList(index, minOf(index + 4, lignes.size)).joinToString(" ")
                if (!fenetre.contains("overflow = TextOverflow.Ellipsis")) nues += ligne.trim()
            }
        }
        assertTrue("des libellés se coupent sans ellipse : $nues", nues.isEmpty())
        // Et le libellé du pied — le bouton « Nouvelle conversation » — est bien celui-là.
        assertTrue(source.contains("text = \"New conversation\""))
    }

    @Test
    fun `le bouton du dernier message a une marge du bord`() {
        // Le bouton se pose au-dessus de la **réserve du fil** — la pile mesurée (compositeur,
        // barre d'onglets, encart du système, écart) plus un dégagement nommé — et non plus
        // au-dessus de deux estimations recopiées. La marge intérieure de 16 dp est conservée.
        assertTrue(
            chatSource.contains(
                ".padding(start = 16.dp, end = 16.dp, bottom = jumpClearance)",
            ),
        )
        assertTrue(
            chatSource.contains("val jumpClearance = glassBottomInset + ComposerStacking.PillClearanceDp.dp"),
        )
        // **L'encart du système n'est plus compté deux fois** : la réserve le porte déjà, et le
        // second `navigationBarsPadding()` faisait descendre le bouton dans la carte.
        assertFalse(
            "le bouton ne reprend pas une seconde marge système",
            chatSource.substringAfter("val jumpClearance").substringBefore("ToastHost")
                .contains("navigationBarsPadding()"),
        )
        // La hauteur de la barre d'onglets est mesurée, pas devinée.
        assertTrue(chatSource.contains("var tabBarHeightPx by remember { mutableIntStateOf(0) }"))
        assertTrue(chatSource.contains("tabBarHeightPx = it.height"))
    }

    @Test
    fun `les sous-pages depilent avant de se rabattre sur leur parent logique`() {
        // Réglages : « Terminé » revient à l'écran quitté, pas systématiquement au fil.
        assertTrue(appSource.contains("onDone = { if (!navBack()) navTo(Route.CHAT.name) }"))
        // Les pages Modèle, Voix, salle de groupe : même contrat.
        assertTrue(appSource.contains("onBack = { if (!navBack()) navTo(Route.CHAT.name) }"))
        assertTrue(appSource.contains("onBack = { if (!navBack()) navTo(Route.SETTINGS.name) }"))
        assertTrue(appSource.contains("onBack = { if (!navBack()) navTo(Route.BOTS.name) }"))
        // Le retour à pile vide passe par la règle partagée, plus par une route écrite en dur.
        assertTrue(appSource.contains("rootRouteBehind("))
        assertTrue(layoutSource.contains("internal val SectionTabRoutes: Set<String>"))
        // La pile ne se vide que **dans le rail** : onglet de section atteint depuis un onglet, ou
        // la liste elle-même. Depuis les Réglages, la route est poussée (le retour rend les Réglages).
        assertTrue(layoutSource.contains("val intoRail = targetRoute in SectionTabRoutes && currentRoute in SectionTabRoutes"))
        // La liste des conversations n'est plus une route : les onglets de section retombent sur le
        // **fil**, comme les autres sous-pages.
        val listFallback = "if (!navBack()) navTo(Route.CHAT.name) })"
        assertEquals(0, appSource.split("Route.CONVERSATIONS").size - 1)
        assertTrue("le repli des onglets de section est le fil", appSource.contains(listFallback))
        // Plus aucun onglet ne se rabat sur les Réglages quand la pile est vide.
        assertFalse(appSource.contains("onBack = { navTo(Route.SETTINGS.name) },\n                sidebarControl = sidebarControl,"))
        // Toute entrée de route passe par la pile : une affectation directe de `route` laissait
        // la pile intacte et le retour dépilait l'entrée d'avant.
        val direct = Regex(
            "^\\s*route = (?!previous|navigateWithBackStack|navigateLeavingEmpty)",
            RegexOption.MULTILINE,
        ).findAll(appSource).toList()
        assertTrue("aucune route écrite en direct : $direct", direct.isEmpty())
    }
}
