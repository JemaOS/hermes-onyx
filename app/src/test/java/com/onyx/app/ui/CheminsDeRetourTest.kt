package com.onyx.app.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Les six chemins de retour**, tenus ensemble pour toutes les routes.
 *
 * 1. le **bouton retour du système** ;
 * 2. le **geste de bord** — gauche **et** droit — et surtout : pas avalé par un contenu horizontal ;
 * 3. le **retour prédictif** (aperçu sous le doigt) — et, quand il n'est pas disponible, le test le
 *    dit au lieu de le supposer ;
 * 4. le retour depuis une **feuille ou un dialogue** : il ferme la feuille d'abord ;
 * 5. le retour **clavier ouvert** : le premier retour ferme le clavier ;
 * 6. le retour après **rotation** et après **mort du processus** : même destination, même position.
 *
 * Les chemins 1, 2 et 6 passent tous par la **même** descente (`navBack()` puis le repli) : c'est ce
 * que mesure [RetourMatriceTest] et ce que verrouille [TableDesRoutesVerrouTest]. Ce fichier mesure
 * ce qui tient **autour** de cette descente : la surface du geste, la feuille, le clavier et la
 * restauration.
 */
class CheminsDeRetourTest {

    private val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()
    private val lignes = app.lines()
    private val manifest = File("src/main/AndroidManifest.xml").readText()
    private val scaffold = File("src/main/java/com/onyx/app/ui/sidebar/SidebarScaffold.kt").readText()

    /**
     * La branche (ou les branches) `Route.X ->` du routeur, prises à l'**indentation** du graphe.
     * Chercher `"Route.X ->"` tout court est le piège : `FileOpenRoute.AUDIO ->` porte la même
     * chaîne, et le test lirait le bloc d'ouverture de fichier au lieu de l'écran.
     */
    private fun branchesDe(route: String): String {
        val entete = Regex("^(\\s*)Route\\.([A-Z_0-9]+)((?:\\s*,\\s*Route\\.[A-Z_0-9]+)*)\\s*->")
        val sortie = StringBuilder()
        lignes.forEachIndexed { index, ligne ->
            val m = entete.find(ligne) ?: return@forEachIndexed
            val noms = listOf(m.groupValues[2]) + Regex("Route\\.([A-Z_0-9]+)").findAll(m.groupValues[3]).map { it.groupValues[1] }
            if (route !in noms) return@forEachIndexed
            val indentation = m.groupValues[1].length
            var fin = index + 1
            while (fin < lignes.size) {
                val suivante = lignes[fin]
                val ms = entete.find(suivante)
                if (ms != null && ms.groupValues[1].length <= indentation) break
                if (suivante.isNotBlank() && !suivante.startsWith(" ".repeat(indentation))) break
                fin++
            }
            sortie.append(lignes.subList(index, fin).joinToString("\n")).append('\n')
        }
        return sortie.toString()
    }

    /** #1 — le bouton système : un seul point de passage, et il n'est sauté sur aucune route. */
    @Test
    fun `1 le bouton systeme passe par la meme descente sur toutes les routes`() {
        // Le `BackHandler` de la mise en page est le point d'entrée du bouton **et** du geste : la
        // même condition (`routeBackHandled`) décide s'il est consommé, pour les deux formats.
        assertTrue(app.contains("BackHandler(enabled = backHandled) {"))
        assertTrue(app.contains("val backHandled = routeBackHandled("))
        assertTrue(app.contains("if (!navBack()) {"))
        assertTrue(app.contains("rootRouteBehind("))
        // La règle est partagée, donc la même pour le pliable déplié et le téléphone.
        val regle = File("src/main/java/com/onyx/app/ui/ResponsiveLayout.kt").readText()
        assertTrue(regle.contains("internal fun routeBackHandled("))
        assertTrue(regle.contains("internal fun popRoute("))
        assertTrue(regle.contains("internal fun rootRouteBehind("))
        // Et **aucune** route ne quitte l'app depuis une sous-page : la condition est vraie pour
        // toute route autre que le fil, même à pile vide.
        assertTrue(regle.contains("backStack.isNotEmpty() -> true"))
        assertTrue(regle.contains("else -> currentRoute != chatRoute"))
    }

    /** #2 — le geste de bord : les deux bords, et pas avalé par un contenu horizontal. */
    @Test
    fun `2 le geste de bord est pris au bord, au-dessus du contenu horizontal`() {
        // Le geste **est** le retour du système, sur les deux bords (Android réserve une bande de
        // chaque côté) : l'app n'exclut aucune bande et ne réclame aucun bord pour elle. C'est ce
        // qui garantit que le geste passe **même au-dessus d'un carrousel** : le fil porte un
        // `HorizontalPager` (les onglets du bas) et, **mesuré à l'appareil**, un glissement depuis
        // le bord gauche alors qu'on est sur l'onglet « Flux » (la page 1 du carrousel) **n'est pas
        // avalé** : le retour système aboutit (aux abscisses 40 px et 80 px, cf.
        // `qa/20261030-retours-exhaustifs/bande.jsonl`). Aucune bande d'app n'est donc posée : un
        // contrôle qui doublerait le geste du système n'aurait rien à protéger, et il volerait des
        // appuis au carrousel.
        assertTrue("le retour geste/prédictif est activé", manifest.contains("android:enableOnBackInvokedCallback=\"true\""))
        assertFalse("aucun bord n'est exclu du geste système", manifest.contains("SYSTEM_GESTURE_EXCLUSION"))
        assertFalse("aucune action « aller à l'accueil » détournée", manifest.contains("GLOBAL_ACTION_BACK"))

        // Le contenu horizontal qui posait la question est bien là, nommé, pour que ce test se
        // relise : le fil, et les deux visionneuses.
        listOf(
            "ui/chat/ChatScreen.kt",
            "ui/chat/PhotoViewerScreen.kt",
            "ui/chat/PdfViewerScreen.kt",
        ).forEach { fichier ->
            val source = File("src/main/java/com/onyx/app/$fichier").readText()
            assertTrue("$fichier : son carrousel", source.contains("HorizontalPager("))
            // Et il **ne pose aucune exclusion de bord** : rien ne réclame le bord pour lui.
            assertFalse("$fichier : aucune exclusion de bord", source.contains("systemGestureExclusion"))
        }
        // Le volet, lui, se glisse depuis partout (plein écran) **et** dépile d'abord : le geste du
        // bord et le bouton système passent par la même descente.
        val scaffold = File("src/main/java/com/onyx/app/ui/sidebar/SidebarScaffold.kt").readText()
        assertTrue(scaffold.contains("onDragStopped = { velocity ->"))
        assertTrue(scaffold.contains("if (open) state.open() else sidebarBackStep(state)"))
        assertTrue(scaffold.contains("BackHandler(enabled = state.isOpen) { sidebarBackStep(state) }"))
    }

    /** #3 — le retour prédictif : présent, ou dit franchement. */
    @Test
    fun `3 le retour predictif est disponible et tenu par la meme pile`() {
        assertTrue(manifest.contains("android:enableOnBackInvokedCallback=\"true\""))
        // La bibliothèque qui porte `BackHandler` honorant le retour prédictif : androidx.activity ≥ 1.8.
        val toml = File("../gradle/libs.versions.toml").readText()
        val version = Regex("activityCompose = \"([0-9.]+)\"").find(toml)!!.groupValues[1]
        val (majeur, mineur) = version.split(".").let { it[0].toInt() to it[1].toInt() }
        assertTrue(
            "androidx.activity $version porte le retour prédictif (≥ 1.8)",
            majeur > 1 || (majeur == 1 && mineur >= 8),
        )
        // L'aperçu (la destination sous le doigt) est fourni par le système pour un retour **non
        // consommé**. Toutes les surfaces de retour de cet écran passent par `BackHandler`, donc le
        // système sait les décrire ; les routes qui sortent l'app sont la racine, où il n'y a rien à
        // décrire — c'est le lanceur qui apparaît. Aucune animation `EnterTransition` n'est posée sur
        // le retour prédictif lui-même : rien à « deviner ».
        assertTrue(app.contains("onyxRouteTransition("))
    }

    /** #4 — le retour depuis une feuille ou un dialogue ferme la feuille d'abord. */
    @Test
    fun `4 chaque feuille prend le retour avant l ecran`() {
        // Chaque feuille a **son** `BackHandler`, qui la referme : elle ne laisse donc jamais
        // passer le retour vers l'écran tant qu'elle est ouverte.
        val feuilles = mapOf(
            "ui/chat/components/ChatActionSheet.kt" to "BackHandler(enabled = panel != null) { open = null }",
            "ui/conversations/ConversationActionSheet.kt" to "BackHandler(onBack = onDismiss)",
            "ui/setup/SetupCodeScanner.kt" to "BackHandler(onBack = onCancel)",
            "ui/setup/SetupCodeSheet.kt" to "BackHandler {",
            "ui/context/ContextFileEditorScreen.kt" to "BackHandler(enabled = state.dirty) { confirmDiscard = true }",
        )
        feuilles.forEach { (fichier, attendu) ->
            val source = File("src/main/java/com/onyx/app/$fichier").readText()
            assertTrue("$fichier : la feuille prend le retour", source.contains(attendu))
        }
        // Et la recherche du fil (un bandeau, pas une feuille) la prend aussi la première, avant le
        // retour de la mise en page — c'est l'ordre de composition qui le décide.
        val chat = File("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt").readText()
        assertTrue(chat.contains("BackHandler(enabled = state.searchOpen) {"))
        // Le volet en surimpression, lui aussi, prend le retour avant l'écran qu'il porte.
        assertTrue(scaffold.contains("BackHandler(enabled = state.isOpen) { sidebarBackStep(state) }"))
    }

    /** #5 — le retour clavier ouvert : le premier retour ferme le clavier. */
    @Test
    fun `5 le premier retour ferme le clavier, le second remonte`() {
        // Ce chemin est celui de la **plateforme** : le clavier est une fenêtre à part, et Android
        // lui route le retour avant l'app tant qu'il est affiché — l'app n'enregistre donc **aucun**
        // `BackHandler` qui aurait la prétention de passer avant lui. Le vérifier, c'est vérifier
        // qu'aucun écran ne s'interpose : aucun `BackHandler` n'est conditionné à un champ de saisie.
        val sources = File("src/main/java/com/onyx/app").walkTopDown().filter { it.extension == "kt" }
        val suspects = mutableListOf<String>()
        sources.forEach { fichier ->
            val texte = fichier.readText()
            Regex("BackHandler\\(([^)]*)\\)").findAll(texte).forEach { m ->
                val condition = m.groupValues[1]
                if (Regex("focus|keyboard|ime|isComposing|typing|draft|input", RegexOption.IGNORE_CASE).containsMatchIn(condition)) {
                    suspects += "${fichier.name}: ${m.value}"
                }
            }
        }
        assertTrue("un retour conditionné au clavier s'interposerait avant l'IME : $suspects", suspects.isEmpty())
        // La fenêtre se **redimensionne** sous le clavier (`adjustResize`) : le clavier ne recouvre
        // pas le compositeur, donc le retour qui le ferme rend la page exactement telle qu'elle était,
        // sans défilement à rattraper.
        assertTrue(manifest.contains("android:windowSoftInputMode=\"adjustResize\""))
        // Et l'app garde ses propres retours **au-dessus** du clavier, pas dans son chemin : le
        // terminal ferme sa session d'un niveau puis remonte (`close()` → `onBack()`), la feuille de
        // saisie se referme sur son propre état — c'est l'ordre que le retour de la plateforme suit.
        val terminal = File("src/main/java/com/onyx/app/ui/terminal/TerminalScreen.kt").readText()
        assertTrue(terminal.contains("BackHandler { close() }"))
        // Le second retour remonte : la descente est celle de la mise en page.
        assertTrue(app.contains("if (!navBack()) {"))
    }

    /** #6 — rotation et mort du processus : même destination, même position. */
    @Test
    fun `6 la rotation et la mort du processus rendent la meme destination`() {
        // La rotation **et** le pliage ne relancent pas l'activité : la position et la page restent.
        val configChanges = manifest.lines().first { it.contains("android:configChanges=") }
        listOf("orientation", "screenSize", "smallestScreenSize", "screenLayout", "keyboardHidden", "uiMode")
            .forEach { assertTrue("le manifeste déclare $it : $configChanges", configChanges.contains(it)) }
        // La route survit à la mort du processus…
        assertTrue(app.contains("var route by rememberSaveable { mutableStateOf(Route.CHAT.name) }"))
        // …et la **position de chaque page** est enregistrée par route : le retour la retrouve.
        assertTrue(app.contains("val routeStates = rememberSaveableStateHolder()"))
        assertTrue(app.contains("routeStates.SaveableStateProvider(presentedRoute.name)"))
        // Le garde-fou de démarrage : un lancement de secours retombe sur le fil **sans l'empiler**.
        assertTrue(app.contains("com.onyx.app.ui.StartupGuard.with(context.applicationContext)"))
        assertTrue(app.contains("route = navigateLeavingEmpty(route, Route.CHAT.name, backStack)"))
        // Chaque écran dont la charge utile n'est pas sauvegardable se replie sur **la page quittée**,
        // jamais sur une page choisie par défaut — c'est le défaut corrigé dans cette passe.
        listOf("SELECT_TEXT", "PATCH", "PHOTO", "PDF", "VIDEO", "AUDIO", "DOCUMENT", "ARCHIVE", "SUBAGENT", "SESSION_USAGE", "PROJECT_SESSIONS")
            .forEach { route ->
                assertTrue(
                    "Route.$route se replie hors composition",
                    branchesDe(route).contains("navFromEmpty("),
                )
            }
    }

    /** La table des routes couvre **toutes** les destinations : le lien avec le verrou. */
    @Test
    fun `la table des retours couvre toutes les destinations`() {
        val bloc = app.substringAfter("private enum class Route {").substringBefore("\n}")
        val noms = Regex("\\b[A-Z][A-Z_0-9]{2,}\\b")
            .findAll(bloc.lines().joinToString("\n") { it.substringBefore("//") })
            .map { it.value }.distinct().toList()
        assertEquals(
            "une destination du graphe n'est pas dans la table",
            noms.sorted(),
            TableDesRetours.routes.sorted(),
        )
    }
}
