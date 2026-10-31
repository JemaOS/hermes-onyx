package com.onyx.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le **câblage** du volet du Chat : ce qui ne peut pas revenir sans qu'on s'en aperçoive.
 *
 * Le volet est là **dans les deux largeurs** — posé à côté du fil sur une tablette, en
 * surimpression sur un téléphone —, le contrôle de la barre l'ouvre et le referme **en un seul
 * appui**, le retour matériel le referme, et les **bots ne sont pas dans la liste de
 * conversations** : ils vivent dans la position *Bots*, avec les salons.
 *
 * Le second point de ce fichier tient la **règle de nommage** du lot : aucun de ces fichiers ne
 * nomme l'autre application ni la firme, ses systèmes ou ses appareils. Les chaînes interdites sont
 * **assemblées** pour que le balayage puisse échouer sur n'importe quelle ligne, y compris la
 * sienne (même méthode que `PackageNameIndependenceTest`).
 */
class SidebarWiringTest {

    private companion object {
        val SIDEBAR_DIR = "src/main/java/com/onyx/app/ui/sidebar"

        val SIDEBAR_FILES = listOf(
            "$SIDEBAR_DIR/ChatSidebar.kt",
            "$SIDEBAR_DIR/ChatSidebarBots.kt",
            "$SIDEBAR_DIR/ChatSidebarHost.kt",
            "$SIDEBAR_DIR/ChatSidebarModel.kt",
            "$SIDEBAR_DIR/OnyxSidebarState.kt",
            "$SIDEBAR_DIR/SidebarScaffold.kt",
        )

        /** Les mots que le lot ne doit jamais écrire : la firme, ses systèmes, ses appareils. */
        val FORBIDDEN = listOf(
            "ap" + "ple",
            "iph" + "one",
            "ip" + "ad",
            "mac" + "os",
            "sir" + "i",
        )

        /** Le nom de l'autre application, assemblée comme les précédents. */
        val OTHER_APP = "her" + "ald"
    }

    private val app = read("src/main/java/com/onyx/app/ui/OnyxApp.kt")
    private val sidebar = read("$SIDEBAR_DIR/ChatSidebar.kt")
    private val botsTab = read("$SIDEBAR_DIR/ChatSidebarBots.kt")
    private val host = read("$SIDEBAR_DIR/ChatSidebarHost.kt")
    private val model = read("$SIDEBAR_DIR/ChatSidebarModel.kt")
    private val state = read("$SIDEBAR_DIR/OnyxSidebarState.kt")
    private val scaffold = read("$SIDEBAR_DIR/SidebarScaffold.kt")

    private fun read(path: String) = File(path).readText()

    @Test
    fun `le volet est dessine dans les deux largeurs, depuis le meme etat`() {
        // Le volet **posé** : la colonne des deux panneaux, c'est lui.
        assertTrue(
            "le volet posé n'est plus la liste des conversations mais le volet",
            app.contains("sidebar = { chatSidebar() },"),
        )
        // La **surimpression** : le même volet, la même enveloppe pour toutes les routes.
        assertTrue(
            app.contains("SidebarScaffold(state = sidebarState, sidebar = { chatSidebar() }, content = { inner() })"),
        )
        assertTrue("les routes compactes passent par l'enveloppe", app.contains("Route.CHAT -> withSidebar { chatPane(true, sidebarToggleControl) }"))
        assertTrue(app.contains("Route.BOTS -> withSidebar { botsPane(false, sidebarToggleControl) }"))
        assertTrue(app.contains("Route.CRON -> withSidebar { cronPane(false, sidebarToggleControl) }"))
    }

    @Test
    fun `le mode du volet suit la largeur, et l etat est ecrit dans l etat du volet`() {
        assertTrue(app.contains("val sidebarMode = sidebarModeFor(maxWidth)"))
        assertTrue(app.contains("LaunchedEffect(sidebarMode) { sidebarState.updateMode(sidebarMode) }"))
        assertTrue(
            "la géométrie des deux panneaux suit l'ouverture du volet",
            app.contains("collapsed = sidebarMode == SidebarMode.DOCKED && !sidebarState.isOpen,"),
        )
        // …et **ce que le volet porte** : porteur d'un écran, il prend la largeur d'un écran.
        assertTrue(app.contains("content = sidebarState.isNavigating,"))
        // Le mode est décidé par la largeur, jamais par un réglage.
        assertTrue(state.contains("fun sidebarModeFor(availableWidth: Dp): SidebarMode"))
        assertTrue(state.contains("if (canShowTwoPanes(availableWidth)) SidebarMode.DOCKED else SidebarMode.COMPACT"))
    }

    @Test
    fun `le volet s ouvre et se referme en un seul geste, et le retour materiel le referme`() {
        // Un appui : le même contrôle partout, et il n'a qu'une action.
        assertTrue(app.contains("SidebarToggleControl("))
        assertTrue(app.contains("onClick = { sidebarState.toggle() }"))
        // Le retour matériel **dépile le volet avant de le refermer** : sur un écran il revient à
        // la liste, sur la liste il referme la surimpression — un niveau à la fois, jamais une
        // sortie brutale. La règle vit dans le modèle du volet, pour que le bouton système, le
        // geste de bord et l'appui sur le voile fassent **le même** geste.
        assertTrue(
            "le retour du volet dépile avant de fermer",
            scaffold.contains("BackHandler(enabled = state.isOpen) { sidebarBackStep(state) }"),
        )
        assertTrue(
            "la règle des deux marches vit dans le modèle",
            state.contains("if (!state.popDestination()) state.close()"),
        )
        // Le geste du bord ouvre : un glissement horizontal sur le contenu, qui se referme en
        // glissant vers la gauche, ou en appuyant sur le voile — par la même descente.
        assertTrue(scaffold.contains(".draggable("))
        assertTrue(scaffold.contains("onDragStopped"))
        assertTrue(scaffold.contains(".onyxClickable(onClick = { sidebarBackStep(state) })"))
    }

    @Test
    fun `une tuile ouvre son ecran DANS le volet, et ne le referme plus`() {
        // Le choix inverse est **retiré** : une tuile faisait `navTo(id)` puis `sidebarState.close()`.
        val tileHandler = app.substringAfter("onOpenTile = { id ->").substringBefore("},\n")
        assertFalse(
            "une tuile ne doit plus fermer le volet (choix précédent, corrigé)",
            tileHandler.contains("sidebarState.close()"),
        )
        assertFalse(
            "une tuile ne doit plus changer la route de l'app : elle empile dans le volet",
            tileHandler.contains("navTo("),
        )
        assertTrue(
            "une tuile empile son écran dans le volet",
            tileHandler.contains("sidebarState.pushDestination(id)"),
        )
        // Le volet reçoit l'écran qu'il porte, et le dessine **à la place** de sa liste.
        assertTrue(app.contains("destination = sidebarState.destination.takeIf { sidebarState.isOpen }"))
        assertTrue(app.contains("destinationContent = paneScreenFor.takeIf { sidebarState.isOpen }"))
        assertTrue(sidebar.contains("if (showingScreen) {"))
        assertTrue("le pied du volet reste sous l'écran", sidebar.contains("SidebarFooter("))
        // Et le retour **dans** le volet dépile : c'est le même geste que la flèche de l'écran.
        assertTrue(app.contains("val paneBack: () -> Unit = { sidebarState.popDestination() }"))
        assertTrue(host.contains("BackHandler(enabled = destination != null) { onPaneBack() }"))
    }

    @Test
    fun `chaque tuile du volet a son ecran dans le volet`() {
        com.onyx.app.ui.sidebar.SidebarDestinations.tiles.forEach { tile ->
            assertTrue(
                "la tuile ${tile.id} n'a pas d'écran porté par le volet",
                com.onyx.app.ui.sidebar.SidebarDestinations.paneScreens.contains(tile.id),
            )
            assertTrue(
                "l'écran de ${tile.id} n'est pas construit par le volet",
                app.contains("Route.${tile.id}.name ->"),
            )
        }
    }

    @Test
    fun `le volet s elargit quand il porte un ecran, et pas quand il montre sa liste`() {
        // La largeur suit ce que le volet porte : c'est le « sans écraser le contenu dans 320 dp ».
        assertTrue(scaffold.contains("sidebarWidthForMode(maxWidth, mode, content = state.isNavigating)"))
        assertTrue(state.contains("content: Boolean = false"))
        // En surimpression (téléphone), le volet garde **sa** largeur : rien ne bouge.
        assertTrue(state.contains("SidebarMode.COMPACT -> sidebarOverlayWidthFor(availableWidth)"))
    }

    @Test
    fun `une destination que le volet ne porte pas reprend le plein ecran`() {
        assertTrue(app.contains("val paneLeave: (String) -> Unit = { id ->"))
        assertTrue(app.contains("sidebarState.clearDestinations()"))
        // Une page profonde des Réglages prend l'app, comme avant…
        assertTrue(app.contains("onOpenAppearance = { paneLeave(Route.APPEARANCE.name) }"))
        assertTrue(app.contains("onOpenServers = { paneLeave(Route.SERVERS.name) }"))
        // …et celles que le volet porte **restent** dans le volet, un niveau plus loin.
        assertTrue(app.contains("onOpenModels = { panePush(Route.MODELS.name) }"))
        assertTrue(app.contains("onOpenInsights = { panePush(Route.INSIGHTS.name) }"))
        // La ligne « Compétences » des Réglages a été retirée : elle doublait « Capacités ».
        assertFalse(app.contains("onOpenSkills"))
        // Le pied du volet reste accessible : « Nouvelle conversation » sort du volet proprement.
        assertTrue(app.contains("paneLeave(Route.CHAT.name)"))
    }

    @Test
    fun `les bots sont absents de la liste de conversations et presents dans leur onglet`() {
        // La liste ne prend **que** des conversations : un bot n'y entre pas, par construction.
        assertTrue(model.contains("conversations: List<Conversation>,"))
        assertFalse(
            "la fonction des lignes ne doit pas prendre de bots",
            model.substringAfter("internal fun sidebarRows(").substringBefore("): List<SidebarRow>")
                .contains("BotProfile"),
        )
        // La position *Chats* ne reçoit pas les bots ; la position *Bots* les dessine.
        val chatsBranch = sidebar.substringAfter("SidebarTab.CHATS -> ConversationList(").substringBefore("SidebarTab.BOTS -> BotsTab(")
        assertFalse("la branche Chats ne parle pas des bots", chatsBranch.contains("bots"))
        val botsBranch = sidebar.substringAfter("SidebarTab.BOTS -> BotsTab(")
        assertTrue("la branche Bots les porte", botsBranch.contains("bots = bots"))
        // Et les bots ont bien leur grille de cartes et la section des salons.
        assertTrue(botsTab.contains("SectionHeader(title = \"Bots\""))
        assertTrue(botsTab.contains("SectionHeader(title = \"Rooms\""))
        assertTrue(botsTab.contains("InterlacedFaces("))
    }

    @Test
    fun `le volet est une seule copie, plein bord, et ne pousse pas le fil`() {
        // **Une seule copie** : un seul conteneur de volet, et il borne ce qu'il peint — le fondu
        // et le glissement ne peuvent donc pas laisser une image rémanente à côté de lui.
        assertEquals(
            "un conteneur de volet par disposition (surimpression, posé) — jamais deux copies",
            2,
            Regex("requiredWidth\\(width\\)").findAll(scaffold).count(),
        )
        assertTrue(scaffold.contains(".clipToBounds()"))
        // **Plein bord** : le volet qui prend la fenêtre ne s'arrondit plus dans le coin de l'écran,
        // et son filet de bord n'a plus de bord à marquer.
        assertTrue(scaffold.contains("val fullBleed = width >= windowWidth"))
        assertTrue(scaffold.contains("if (fullBleed) RectangleShape else DrawerShape"))
        assertTrue(scaffold.contains("if (fullBleed) return@drawBehind"))
        // Le fil ne glisse que s'il reste quelque chose à découvrir **à côté** du volet : la
        // surimpression d'un téléphone prenant la fenêtre entière, il ne glisse plus du tout.
        assertTrue(scaffold.contains("val pushesContent = width < windowWidth"))
        assertTrue(scaffold.contains("if (pushesContent) (widthPx * p).roundToInt() else 0"))
    }

    @Test
    fun `le halo des Reglages ne consomme plus de hauteur`() {
        // Le halo était un bloc de 300 dp dans la page : un trou au milieu des Réglages, où le
        // contenu défilé se coupait net. Il est désormais **peint** par la page (modificateur).
        val settings = read("src/main/java/com/onyx/app/ui/settings/SettingsScreen.kt")
        assertFalse(
            "le halo ne doit plus être un bloc dans la page",
            settings.contains("HaloBackdrop(height = 300.dp)"),
        )
        assertTrue(settings.contains(".haloBackdrop("))
    }

    @Test
    fun `la tete porte la passerelle, son adresse et son voyant, et le pied la nouvelle conversation`() {
        assertTrue(sidebar.contains("address = gatewayAddress"))
        assertTrue(sidebar.contains("reachable = reachable"))
        assertTrue(sidebar.contains("BasicText(text = \"Search chats\""))
        assertTrue(sidebar.contains("OnyxSegmented("))
        assertTrue(sidebar.contains("New conversation"))
        assertTrue(sidebar.contains("SidebarTileGrid("))
        assertTrue(sidebar.contains("SidebarProjects("))
        assertTrue(sidebar.contains("SidebarFilters("))
        // Le compteur de bots qui attendent se lit depuis la position *Chats*.
        assertTrue(host.contains("botsWaiting = waitingBots"))
    }

    @Test
    fun `aucun fichier du lot ne nomme l autre application, la firme, ses systemes ou ses appareils`() {
        val offenders = mutableListOf<String>()
        for (path in SIDEBAR_FILES) {
            val text = read(path)
            for (word in FORBIDDEN + OTHER_APP) {
                text.lineSequence().forEachIndexed { index, line ->
                    if (line.contains(word, ignoreCase = true)) {
                        offenders += "$path:${index + 1}: $word"
                    }
                }
            }
        }
        assertEquals("des mots interdits sont écrits dans le lot : $offenders", emptyList<String>(), offenders)
    }

    // -----------------------------------------------------------------------------------------
    // Le mode recherche : il masque, il rétablit, il se quitte, et il ne ment pas sur sa portée.
    // -----------------------------------------------------------------------------------------

    /** La branche de la recherche : de la garde du sélecteur à la branche des onglets. */
    private val searchBranch: String
        get() = sidebar.substringAfter("if (!searching) {").substringBefore("} else when (tab) {")

    /** La branche de la liste : ce qui n'est dessiné **que** hors recherche. */
    private val listBranch: String
        get() = sidebar.substringAfter("} else when (tab) {")

    @Test
    fun `entrer en recherche masque les tuiles, les projets, les filtres et le selecteur`() {
        // Hors recherche, la liste porte bien ses quatre éléments.
        assertTrue(listBranch.contains("SidebarTileGrid("))
        assertTrue(listBranch.contains("SidebarProjects("))
        assertTrue(listBranch.contains("SidebarFilters("))
        assertTrue("le sélecteur Chats / Bots reste dans le volet hors recherche", sidebar.contains("SidebarSegment(tab = tab, botsWaiting = botsWaiting, onTab = onTab)"))
        // Le sélecteur est **dans la garde**, pas seulement ailleurs dans le fichier.
        assertTrue(sidebar.contains("if (!searching) {"))
        assertTrue(
            "le sélecteur doit être la première chose sous la garde",
            sidebar.substringAfter("if (!searching) {").substringBefore("SidebarSegment(").isBlank(),
        )
        // En recherche, **aucun** des éléments de la liste n'est composé : la recherche est le seul
        // contenu.
        assertTrue(searchBranch.contains("SearchResults("))
        for (hidden in listOf("SidebarTileGrid(", "SidebarProjects(", "SidebarFilters(")) {
            assertFalse("« $hidden » est encore dessiné en recherche", searchBranch.contains(hidden))
        }
        // Et le pied, lui, **reste** : « Nouvelle conversation » et le rond d'initiale ne partent pas.
        assertTrue(sidebar.contains("        SidebarFooter("))
        assertFalse("le pied ne doit pas être dans la branche de recherche", searchBranch.contains("SidebarFooter("))
    }

    @Test
    fun `les resultats remplacent la liste, et la position de la liste est gardee`() {
        assertTrue(searchBranch.contains("SearchResults("))
        assertTrue(searchBranch.contains("state = resultsState,"))
        // La liste des conversations est **hors** de la recherche, donc jamais filtrée par la requête.
        assertTrue(listBranch.contains("query = \"\","))
        // Deux positions de défilement, tenues **par le volet** : la recherche rend la sienne neuve,
        // et la liste retrouve la sienne au retour.
        assertTrue(sidebar.contains("val listState = rememberLazyListState()"))
        assertTrue(sidebar.contains("val resultsState = rememberLazyListState()"))
        assertTrue(listBranch.contains("state = listState,"))
        assertTrue(sidebar.contains("state: LazyListState,"))
    }

    @Test
    fun `effacer le texte et sortir de la recherche sont deux actions distinctes`() {
        // Effacer : vide la requête, et **reste** en recherche.
        assertTrue(sidebar.contains(".onyxTappable(onClick = { onQuery(\"\") })"))
        assertTrue(sidebar.contains(".semanticsLabel(\"Clear search\")"))
        // Sortir : referme la recherche, et c'est **l'autre** bouton, à l'extérieur du champ.
        assertTrue(sidebar.contains(".onyxTappable(onClick = onExitSearch)"))
        assertTrue(sidebar.contains(".semanticsLabel(\"End search\")"))
        // Le libellé du bouton de sortie est traduit : une commande sans mot n'existe pas pour un
        // lecteur d'écran.
        assertTrue(host.contains("onExitSearch = exitSearch,"))
    }

    @Test
    fun `le champ porte l anneau de focus en recherche, et entre au premier appui`() {
        assertTrue(
            "l'anneau de focus a disparu",
            sidebar.contains("if (searching) Modifier.border(SearchFocusRing, theme.accent, shape) else Modifier"),
        )
        // Les deux chemins entrent : l'appui sur la capsule, et le focus du champ.
        assertTrue(sidebar.contains("onEnterSearch = onEnterSearch,"))
        assertTrue(sidebar.contains(".focusRequester(focus)"))
        assertTrue(sidebar.contains(".onFocusChanged { if (it.isFocused) onEnterSearch() }"))
        // Et la **sortie rend le champ au repos** : sans ça le champ gardait le focus, un second
        // appui ne changeait plus rien et le champ devenait inerte (défaut vu à l'appareil).
        assertTrue(sidebar.contains("val focusManager = LocalFocusManager.current"))
        assertTrue(sidebar.contains("if (!searching) focusManager.clearFocus()"))
    }

    @Test
    fun `l etat vide est centre et dit ce que la recherche interroge`() {
        assertTrue(sidebar.contains("text = SearchScopeSentence,"))
        assertTrue(sidebar.contains("text = if (asked) SearchEmptyTitle else \"No conversations yet.\","))
        assertTrue(sidebar.contains("Modifier.fillParentMaxSize()"))
        assertTrue(sidebar.contains("textAlign = TextAlign.Center,"))
        // La vignette : la loupe **barrée**, sinon rien ne distingue « rien trouvé » d'une recherche
        // en cours.
        assertTrue(sidebar.contains("private fun SearchEmptyGlyph()"))
        assertTrue(sidebar.contains("cap = StrokeCap.Round,"))
        // Et la phrase est la **même vérité** que la fonction qui filtre : elle vit à côté d'elle.
        assertTrue(model.contains("internal const val SearchScopeSentence: String ="))
        assertTrue(model.contains("internal const val SearchEmptyTitle: String = \"No results\""))
        assertTrue(model.contains("internal fun sidebarMatches("))
    }

    @Test
    fun `la frappe est mise en attente, jamais une requete par caractere`() {
        assertTrue(host.contains("var settledQuery by remember { mutableStateOf(\"\") }"))
        assertTrue(host.contains("debouncedSearchQuery(query) { settledQuery = it }"))
        assertTrue(host.contains("val activeQuery = if (searching) settledQuery else \"\""))
        // C'est la requête **retenue** qui filtre, jamais la frappe.
        assertTrue(host.contains("query = activeQuery.trim(),"))
        assertFalse(
            "la liste est encore construite sur la frappe brute : une requête par caractère",
            host.contains("query = query.trim(),"),
        )
    }

    @Test
    fun `le retour quitte la recherche avant de refermer le volet, et la sortie efface la requete`() {
        // Le retour de la recherche est composé **dans** le volet, donc après celui de l'enveloppe :
        // tant que la recherche est ouverte, c'est lui qui prend le geste.
        assertTrue(
            "le retour ne quitte plus la recherche",
            host.contains("BackHandler(enabled = searching && sidebarState.isOpen) { exitSearch() }"),
        )
        // Le geste **suivant** referme le volet : le gestionnaire de l'enveloppe est toujours là,
        // intact — et c'est le **même** retour gradué (n° 3) que le bouton système et le bord.
        assertTrue(scaffold.contains("BackHandler(enabled = state.isOpen) { sidebarBackStep(state) }"))
        // Le volet porte le mode : c'est ce qui permet à l'enveloppe et au volet de s'ordonner.
        assertTrue(host.contains("sidebarState: OnyxSidebarState,"))
        assertTrue(app.contains("sidebarState = sidebarState,"))
        // Sortir de la recherche **efface la requête** — décision du lot, tenue ici : sans ça la
        // liste reviendrait filtrée par un texte que plus rien n'affiche.
        assertTrue(host.contains("sidebarState.exitSearch()"))
        assertTrue(host.contains("query = \"\"\n        settledQuery = \"\""))
    }

    @Test
    fun `la rangee des filtres se replie, sinon la troisieme pastille est inatteignable`() {
        val filters = sidebar.substringAfter("private fun SidebarFilters(").substringBefore("// ---")
        // n° 1 : la rangée est un `FlowRow` — ce qui ne tient pas **descend d'une rangée** au lieu de
        // sortir par le bord. « Vous attend » reste atteignable, et chaque puce reste entière.
        assertTrue("la rangée des filtres doit se replier : « Vous attend » ne doit jamais sortir de l'écran", filters.contains("FlowRow("))
        assertFalse("le défilement horizontal est retiré : la rangée se replie", filters.contains(".horizontalScroll(rememberScrollState())"))
        // Et elle est bornée : sans ça, la puce du « + » des projets passait dessous.
        assertTrue(sidebar.substringAfter("private fun SidebarProjects(").contains(".clipToBounds()"))
    }

    @Test
    fun `le libelle d une tuile ne se coupe pas au milieu d un mot`() {
        val tile = sidebar.substringAfter("private fun SidebarTileCell(").substringBefore("// ---")
        // Le libellé est mesuré par la grille (`TileLabelFit`, une seule taille) et rangé **aux
        // espaces** par `tileLabelPlan` : la taille cède avant le mot, un mot ne se coupe jamais.
        assertTrue(
            "le libellé de la tuile n'est plus rangé par `tileLabelPlan`",
            tile.contains("tileLabelPlan("),
        )
        assertTrue(
            "le libellé de la tuile n'est plus mesuré par la grille",
            tile.contains("val base = labelStyle"),
        )
        assertTrue(model.contains("internal const val TileLabelWordLimit: Int = 11"))
    }
}
