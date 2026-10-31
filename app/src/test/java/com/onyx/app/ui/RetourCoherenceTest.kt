package com.onyx.app.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Un test par **incohérence corrigée** dans la passe « retour & geste de bord ».
 *
 * Chaque test tient les deux moitiés : ce que le code fait **maintenant**, et le défaut d'avant,
 * tel qu'il se produisait — pour que la correction ne puisse pas être défaite sans que le test
 * tombe.
 */
class RetourCoherenceTest {

    private val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()
    private val chat = File("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt").readText()
    private val ordinateur = File("src/main/java/com/onyx/app/ui/computer/ComputerScreen.kt").readText()
    private val regle = File("src/main/java/com/onyx/app/ui/ResponsiveLayout.kt").readText()

    private val lignesApp = app.lines()

    /**
     * La branche (ou les branches) `Route.X ->` du routeur, prises à l'**indentation** du graphe.
     * Chercher `"Route.X ->"` tout court est le piège : `FileOpenRoute.PHOTO ->` contient la même
     * chaîne, et le test lirait alors le bloc d'ouverture de fichier au lieu de l'écran.
     */
    private fun branchesDe(route: String): String {
        val entete = Regex("^(\\s*)Route\\.([A-Z_0-9]+)((?:\\s*,\\s*Route\\.[A-Z_0-9]+)*)\\s*->")
        val sortie = StringBuilder()
        lignesApp.forEachIndexed { index, ligne ->
            val m = entete.find(ligne) ?: return@forEachIndexed
            val noms = listOf(m.groupValues[2]) + Regex("Route\\.([A-Z_0-9]+)").findAll(m.groupValues[3]).map { it.groupValues[1] }
            if (route !in noms) return@forEachIndexed
            val indentation = m.groupValues[1].length
            var fin = index + 1
            while (fin < lignesApp.size) {
                val suivante = lignesApp[fin]
                val ms = entete.find(suivante)
                if (ms != null && ms.groupValues[1].length <= indentation) break
                if (suivante.isNotBlank() && !suivante.startsWith(" ".repeat(indentation))) break
                fin++
            }
            sortie.append(lignesApp.subList(index, fin).joinToString("\n")).append('\n')
        }
        return sortie.toString()
    }

    /** La branche d'une route dans le `when (presentedRoute)`. */
    private fun branche(route: String): String = branchesDe(route)

    /** Le bloc du volet : ses tuiles, ses listes et ses choix. */
    private val volet: String = app
        .substringAfter("val chatSidebar: @Composable () -> Unit = {")
        .substringBefore("// Le clavier est traité")

    /**
     * Chaque `onBack = …` de la mise en page, avec la route qui le porte. Les expressions qui ne
     * remontent **ni** la pile de routes (`navBack()`) **ni** celle du volet (`paneBack`) sont les
     * **retours fixes** : ils ne conviennent qu'aux écrans qu'une seule entrée ouvre (la page
     * quittée y est toujours la même).
     */
    private fun retoursFixes(): Map<String, String> {
        val lignes = app.lines()
        var route = ""
        val fixes = linkedMapOf<String, String>()
        lignes.forEachIndexed { index, ligne ->
            Regex("^\\s*Route\\.([A-Z_0-9]+)").find(ligne)?.let { route = it.groupValues[1] }
            if (ligne.contains("onBack = ")) {
                // L'expression entière : `onBack = {` peut s'étaler sur trois lignes.
                val expression = StringBuilder(ligne.substringAfter("onBack ="))
                var profondeur = expression.count { it == '{' } - expression.count { it == '}' }
                var suivant = index + 1
                while (profondeur > 0 && suivant < lignes.size) {
                    val ligneSuivante = lignes[suivant]
                    expression.append(' ').append(ligneSuivante)
                    profondeur += ligneSuivante.count { it == '{' } - ligneSuivante.count { it == '}' }
                    suivant++
                }
                // `navBack()` remonte la pile de routes ; `paneBack` dépile l'écran du **volet**,
                // et `paneLeave` sort du volet en empilant la page quittée. Les trois rendent la
                // page quittée : aucun n'est un « retour fixe ».
                if (!expression.contains("navBack()") && !expression.contains("paneBack") &&
                    !expression.contains("paneLeave")
                ) {
                    fixes[route] = expression.toString().trim()
                }
            }
        }
        return fixes
    }

    @Test
    fun `la recherche du fil prend le retour`() {
        // Le défaut : sur l'accueil (le fil), le geste du bord fermait l'app alors que le bandeau
        // « Rechercher dans la conversation » était ouvert — un niveau sauté.
        assertTrue(chat.contains("import androidx.activity.compose.BackHandler"))
        assertTrue(chat.contains("BackHandler(enabled = state.searchOpen) {"))
        // Il fait exactement ce que fait la croix du bandeau.
        val gestionnaire = chat.substringAfter("BackHandler(enabled = state.searchOpen) {").substringBefore("}")
        assertTrue("le retour vide la recherche", gestionnaire.contains("onSearch(\"\")"))
        assertTrue("et la referme", gestionnaire.contains("onFindRequested()"))
        // Le bandeau, lui, est bien un état du fil (c'est lui qui est refermé, pas un écran).
        assertTrue(chat.contains("if (state.searchOpen) {"))
    }

    @Test
    fun `le plein ecran de l ordinateur sort avant l ecran`() {
        // Le défaut : la garde était **inversée** (`enabled = !fullscreen`) — en plein écran le
        // retour laissait l'écran entier, et hors plein écran elle ne faisait que ce que le retour
        // système faisait déjà. Le commentaire disait pourtant l'inverse.
        assertFalse(
            "plus de garde inversée",
            ordinateur.contains("BackHandler(enabled = !fullscreen)"),
        )
        assertTrue(ordinateur.contains("BackHandler {"))
        val gestionnaire = ordinateur.substringAfter("BackHandler {").substringBefore("\n    }")
        assertTrue("le plein écran se quitte d'abord", gestionnaire.contains("if (fullscreen) {"))
        assertTrue(gestionnaire.contains("fullscreen = false"))
        assertTrue("puis l'écran se quitte", gestionnaire.contains("onBack()"))
    }

    @Test
    fun `chaque choix du volet referme le volet`() {
        // « Le retour ferme l'élément ouvert d'abord » : le volet en surimpression doit être refermé
        // par le choix qui l'a pris, sinon il recouvre l'écran où l'on vient d'arriver — et le
        // « + » des salles comme le projet choisi ne le faisaient pas (seuls cas des huit).
        val sansFermeture = mutableListOf<String>()
        val lignes = volet.lines()
        lignes.forEachIndexed { index, ligne ->
            if (ligne.contains("navTo(")) {
                val autour = ligne + " " + lignes.getOrElse(index + 1) { "" }
                if (!autour.contains("sidebarState.close()")) sansFermeture += ligne.trim()
            }
        }
        assertTrue("des ouvertures laissent le volet ouvert : $sansFermeture", sansFermeture.isEmpty())
        assertTrue(app.contains("onNewRoom = { navTo(Route.BOTS.name); sidebarState.close() }"))
    }

    @Test
    fun `les tuiles du volet ne se rabattent pas sur les Reglages`() {
        // Le défaut : Outils et Fichiers du serveur sont des **tuiles du volet**, et
        // leur retour était écrit `navTo(Route.SETTINGS.name)`. Ouverts depuis le fil, ce retour
        // n'y rendait **rien** : il empilait les Réglages, et le retour suivant rouvrait la page
        // qu'on venait de quitter. (La tuile « Compétences » a depuis été retirée : « Capacités »,
        // qui la remplace, a le **même** retour et n'est pas concernée par ce défaut.)
        listOf("TOOLS", "FILES").forEach { route ->
            assertTrue(
                "$route : le retour remonte la pile avant de se rabattre",
                branche(route).contains("onBack = { if (!navBack()) navTo(Route.SETTINGS.name) }"),
            )
        }
    }

    @Test
    fun `Nouveau bot revient au fil quand le volet l a ouvert`() {
        // Même défaut sur `API_BOT` : le volet y mène quand le serveur n'a pas d'identifiants
        // Dashboard, et la page quittée est alors le fil, pas la liste des bots.
        assertTrue(branche("API_BOT").contains("onBack = { if (!navBack()) navTo(Route.BOTS.name) }"))
    }

    @Test
    fun `ordinateur selection et correctif remontent la page quittee`() {
        // Ouverts depuis la feuille d'une réponse : en deux panneaux, la page quittée est la liste
        // des conversations (qui peint le fil), donc un `navTo(CHAT)` fixe y laissait une entrée.
        listOf("COMPUTER", "SELECT_TEXT", "PATCH").forEach { route ->
            assertTrue(
                "$route : le retour remonte la page quittée",
                branche(route).contains("onBack = { if (!navBack()) navTo(Route.CHAT.name) }"),
            )
        }
    }

    @Test
    fun `les serveurs reviennent a la configuration quand on en vient`() {
        // `SERVERS` s'ouvre aussi à la **fin de la configuration** (`SETUP`) : le retour fixe aux
        // Réglages y empilait une entrée et rejouait l'écran quitté au retour suivant.
        assertTrue(branche("SERVERS").contains("onBack = { if (!navBack()) navTo(Route.SETTINGS.name) }"))
    }

    @Test
    fun `un retour ecrit en navTo laissait une entree qui se rejouait`() {
        // Le défaut, tel qu'il se produisait — Fichiers du serveur ouvert du volet, page quittée :
        // le fil.
        val ancien = mutableListOf<String>()
        var route = navigateWithBackStack("CHAT", "FILES", ancien)
        assertEquals(listOf("CHAT"), ancien)
        // L'ancien `onBack = { navTo(Route.SETTINGS.name) }` : la cible n'est pas le sommet de la
        // pile, donc il **empile** l'écran quitté au lieu de le dépiler.
        route = navigateWithBackStack(route, "SETTINGS", ancien)
        assertEquals("SETTINGS", route)
        assertEquals(listOf("CHAT", "FILES"), ancien)
        // Et le retour suivant ne rend pas le fil : il rouvre Fichiers — l'aller-retour sans fin.
        assertEquals("FILES", popRoute(ancien))

        // Le retour de maintenant, `if (!navBack()) navTo(Route.SETTINGS.name)` : la page quittée.
        val maintenant = mutableListOf<String>()
        navigateWithBackStack("CHAT", "FILES", maintenant)
        assertEquals("CHAT", popRoute(maintenant))
        assertTrue("la pile est vide : le geste rend la main au système", maintenant.isEmpty())
    }

    @Test
    fun `le geste du bord et le voile font le meme retour que le bouton systeme`() {
        // Le doute du propriétaire : « le geste de bord fait la même chose que le bouton système ».
        // Avant, le glissement fermait le volet **entier** d'un coup — en sautant l'écran que le
        // volet portait —, alors que le bouton système dépilait d'abord. Les trois chemins passent
        // désormais par la **même** descente, un niveau à la fois.
        val scaffold = File("src/main/java/com/onyx/app/ui/sidebar/SidebarScaffold.kt").readText()
        // Le bouton système.
        assertTrue(scaffold.contains("BackHandler(enabled = state.isOpen) { sidebarBackStep(state) }"))
        // La fin d'un glissement du bord.
        assertTrue(scaffold.contains("if (open) state.open() else sidebarBackStep(state)"))
        // L'appui sur le voile.
        assertTrue(scaffold.contains("onyxClickable(onClick = { sidebarBackStep(state) })"))
        // Plus aucune fermeture sèche qui sauterait l'écran porté.
        assertFalse("plus de fermeture sèche au glissement", scaffold.contains("else state.close()"))
        assertFalse("plus de fermeture sèche au voile", scaffold.contains("onClick = { state.close() }"))
        // La règle vit dans le modèle du volet, avec ses deux marches.
        val regle = File("src/main/java/com/onyx/app/ui/sidebar/OnyxSidebarState.kt").readText()
        assertTrue(regle.contains("fun sidebarBackStep(state: OnyxSidebarState) {"))
        assertTrue(regle.contains("if (!state.popDestination()) state.close()"))
    }

    @Test
    fun `la surimpression est a ras bord, sans coin arrondi qui laisserait un liseré sombre`() {
        // Le défaut du thème clair : la feuille du volet arrondissait ses coins du bord extérieur
        // **par-dessus le voile noir** — deux liserés sombres dans les coins. À ras bord (pleine
        // largeur), le panneau est carré : plus rien à révéler derrière lui.
        val scaffold = File("src/main/java/com/onyx/app/ui/sidebar/SidebarScaffold.kt").readText()
        assertTrue(scaffold.contains("val fullBleed = width >= windowWidth"))
        assertTrue(scaffold.contains("val drawerShape = if (fullBleed) RectangleShape else DrawerShape"))
        assertTrue(scaffold.contains(".clip(drawerShape)"))
        assertTrue(scaffold.contains(".background(sidebarPaneColor(theme), drawerShape)"))
        // À ras bord, aucun filet au bord de l'écran (il se lirait comme un liseré de plus).
        assertTrue(scaffold.contains("if (fullBleed) return@drawBehind"))
    }

    @Test
    fun `le volet n est jamais dessine deux fois`() {
        // Le « bouton fantôme » du relevé était le contrôle du fil, resté visible dans la bande de
        // 71 dp à droite du volet : la bande est supprimée (#1), donc le fantôme aussi. Reste la
        // garantie de fond : **une seule** instance du volet est composée par chemin.
        val scaffold = File("src/main/java/com/onyx/app/ui/sidebar/SidebarScaffold.kt").readText()
        // Une `sidebar()` dans la surimpression, une dans le volet posé — deux branches exclusives.
        assertEquals(2, scaffold.split("sidebar()").size - 1)
        assertTrue(scaffold.contains("if (mode == SidebarMode.DOCKED) {"))
        assertTrue(scaffold.contains("return@BoxWithConstraints"))
        // Dans le volet : l'écran porté **ou** la liste, jamais les deux.
        val sidebar = File("src/main/java/com/onyx/app/ui/sidebar/ChatSidebar.kt").readText()
        assertTrue(sidebar.contains("val screen = destinationContent?.takeIf { destination != null }"))
        assertTrue(sidebar.contains("if (showingScreen) {"))
        // `OnyxApp` branche le volet une seule fois par format : l'enveloppe à un panneau, ou
        // `OnyxListDetail` à deux panneaux — jamais les deux.
        assertEquals(2, app.split("chatSidebar()").size - 1)
        assertTrue(app.contains("if (layout.twoPanes && presentedRoute in TwoPaneRoutes)"))
    }

    @Test
    fun `aucune entree du volet ne perd la page quittee`() {
        // **Le trou du verrou, refermé.** Le défaut : `paneLeave` effaçait la pile du volet puis
        // empilait la seule destination choisie. La page quittée (le panneau Réglages du volet)
        // n'entrait donc dans **aucune** des deux piles, et le retour rendait le fil — jamais les
        // Réglages. Rien ne le mesurait : la boucle de `chaque choix du volet referme le volet` ne
        // regarde que les lignes qui contiennent littéralement `navTo(`, or `paneLeave` encapsule
        // le sien dans sa définition. On mesure donc la **règle**, plus la forme des lignes.
        val corps = app.substringAfter("val paneLeave: (String) -> Unit = { id ->")
            .substringBefore("val paneScreen:")
        val quittee = corps.indexOf("sidebarState.destination")
        val empilee = corps.indexOf("navTo(quittee)")
        val videe = corps.indexOf("clearDestinations()")
        val ferme = corps.indexOf("sidebarState.close()")
        val cible = corps.indexOf("navTo(id)")
        mapOf(
            "lire la page quittée" to quittee,
            "l'empiler dans la pile de routes" to empilee,
            "repartir de la liste du volet" to videe,
            "refermer le volet" to ferme,
            "ouvrir la destination" to cible,
        ).forEach { (quoi, position) ->
            assertTrue("`paneLeave` doit savoir $quoi (correctif P1)", position >= 0)
        }
        assertTrue(
            "l'ordre doit être : lire → empiler → vider → refermer → destination",
            quittee < empilee && empilee < videe && videe < ferme && ferme < cible,
        )
        // La page quittée **est** celle que le volet porte, et elle ne se ré-empile pas sur
        // elle-même quand la destination est déjà celle-là.
        assertTrue(corps.contains("sidebarState.destination?.takeIf { it != id }"))

        // Et l'autre moitié : aucune rangée ne peut contourner cette règle. Les vingt rangées des
        // Réglages du volet passent **toutes** par `paneLeave` (les six autres par `panePush`) —
        // aucune ne va `navTo` directement.
        val rangees = app.substringAfter("Route.SETTINGS.name -> SettingsScreen(")
            .substringBefore("// Une tuile sans écran ici")
        assertEquals(
            "les vingt rangées qui quittent le volet passent par `paneLeave`",
            20,
            rangees.lines().count { it.contains("paneLeave(Route.") },
        )
        assertEquals(
            "les six rangées portées par le volet passent par `panePush`",
            6,
            rangees.lines().count { it.contains("panePush(Route.") },
        )
        assertFalse(
            "aucune rangée ne va `navTo` directement : la page quittée s'y perdrait",
            rangees.substringBefore("onEraseEverything").contains("navTo("),
        )
        // Une page **plein écran** ne sort jamais par le volet : `paneLeave` n'existe que dans le
        // volet, sinon la « page quittée » serait celle du fil et non l'écran qu'on regardait.
        val pleinEcran = app.substringAfter("routeStates.SaveableStateProvider(presentedRoute.name)")
        assertFalse("aucune page de route n'utilise `paneLeave`", pleinEcran.contains("paneLeave("))
    }

    @Test
    fun `la fleche d une section depile la meme pile que le geste`() {
        // Le défaut : `if (sidebarState.isNavigating) paneBack else inApp`. `isNavigating` est vrai
        // dès qu'un écran est posé **dans** le volet, même fermé, même invisible : la flèche
        // dépilait une pile que personne ne voyait, donc le premier appui n'avait aucun effet
        // visible — et le geste, lui, ne faisait pas la même chose. Les deux dépilent maintenant la
        // **même** pile : celle du volet seulement quand il porte **cet** écran, celle des routes
        // sinon.
        assertFalse(
            "plus aucune flèche ne se fie à `isNavigating`",
            app.contains("sidebarState.isNavigating) paneBack"),
        )
        val fleches = Regex("if \\(sidebarState\\.destination == Route\\.([A-Z_0-9]+)\\.name\\) paneBack else inApp")
        assertEquals(
            "les trois onglets de section partagent la même décision",
            setOf("CRON", "KANBAN", "CODE"),
            fleches.findAll(app).map { it.groupValues[1] }.toSet(),
        )
        // Le geste ne peut pas dépiler un volet fermé : l'écran porté n'est transmis que volet
        // ouvert (`takeIf { sidebarState.isOpen }`), et `SidebarScaffold` ne prend le retour que
        // volet ouvert. La flèche et le geste lisent donc la même visibilité.
        assertTrue(app.contains("destination = sidebarState.destination.takeIf { sidebarState.isOpen }"))
        val scaffold = File("src/main/java/com/onyx/app/ui/sidebar/SidebarScaffold.kt").readText()
        assertTrue(scaffold.contains("BackHandler(enabled = state.isOpen) { sidebarBackStep(state) }"))
        // Et la flèche du volet lui-même (`paneBack`) dépile la même pile que le geste.
        assertTrue(app.contains("val paneBack: () -> Unit = { sidebarState.popDestination() }"))
        assertTrue(
            "le geste et la flèche du volet passent par la même descente",
            File("src/main/java/com/onyx/app/ui/sidebar/OnyxSidebarState.kt").readText()
                .contains("if (!state.popDestination()) state.close()"),
        )
    }

    @Test
    fun `tant que le volet porte un ecran, c est lui qui prend le retour`() {
        // L'ordre de composition décide qui répond : le `BackHandler` du volet (dans
        // `SidebarScaffold`) est posé **avant** le contenu, celui du volet porteur d'écran
        // (`ChatSidebarHost`) **après** — c'est donc le second qui prend le retour tant qu'un écran
        // est ouvert : un niveau, jamais la fermeture. La feuille et la recherche du fil, composées
        // plus loin encore, passent avant les deux.
        val host = File("src/main/java/com/onyx/app/ui/sidebar/ChatSidebarHost.kt").readText()
        assertTrue(host.contains("BackHandler(enabled = destination != null) { onPaneBack() }"))
        assertTrue(app.contains("val paneBack: () -> Unit = { sidebarState.popDestination() }"))
        val chat = File("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt").readText()
        assertTrue("la recherche du fil prend le retour en premier", chat.contains("BackHandler(enabled = state.searchOpen) {"))
    }

    @Test
    fun `les retours fixes ne restent que sur les ecrans a une seule entree`() {
        // La règle, resserrée par la correction de la flèche interne : **aucun** retour de page
        // n'est plus écrit en `navTo` sans remonter la pile. Les dix-huit « retours fixes » que ce
        // test certifiait comme légitimes (une seule entrée, croyait-on) sont tous passés à la
        // descente commune — l'édition d'un bot, en particulier, s'ouvre aussi d'une conversation.
        // L'ensemble attendu est donc **vide**, et le rester est la règle.
        assertEquals(
            "plus aucun retour de page écrit en dur (la flèche remonte la pile)",
            emptySet<String>(),
            retoursFixes().keys,
        )
        // Et la règle partagée porte bien les deux formats.
        assertTrue(regle.contains("internal fun routeBackHandled("))
        assertTrue(app.contains("val backHandled = routeBackHandled("))
    }

    @Test
    fun `les ecrans a etat vide ne sont pas des destinations`() {
        // Rappel de la règle qui tient tout le reste : un écran qu'on ne peut pas montrer n'entre
        // pas dans la pile — sinon le retour rebondit dessus et le geste est avalé.
        assertTrue(regle.contains("internal fun navigateLeavingEmpty("))
        assertTrue(app.contains("LaunchedEffect(Unit) { navFromEmpty(Route.CHAT.name) }"))
        // La liste des conversations vit dans le volet : le seul écran à état vide qui rejoue une
        // route est le fil — l'ancienne route « CONVERSATIONS » n'existe plus.
        assertTrue("plus aucun repli vers la liste retirée", !app.contains("Route.CONVERSATIONS"))
    }
}
