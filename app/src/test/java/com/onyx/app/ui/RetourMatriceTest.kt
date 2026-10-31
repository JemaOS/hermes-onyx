package com.onyx.app.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La matrice du retour** : pour chaque écran, ce que fait le retour et **où l'utilisateur
 * atterrit**. Une ligne par parcours réel de l'app — le volet, la barre du bas, les menus, les
 * sous-écrans, les onglets de section, les deux formats d'écran (une colonne, deux panneaux).
 *
 * Le modèle est celui du code, pas une réécriture : l'entrée passe par [navigateWithBackStack]
 * (ce qu'`navTo` fait), et le retour par [routeBackHandled] puis [popRoute], avec le repli
 * [rootRouteBehind] — exactement le `BackHandler` de la mise en page, celui que le **geste du bord**
 * et le bouton maison empruntent tous les deux.
 *
 * Deux règles sont mesurées sur tous les parcours :
 * 1. le retour rend **l'écran quitté** ([Parcours.depuis]), jamais un autre ;
 * 2. le retour **suivant** ne rejoue pas l'écran d'où l'on venait — donc aucune boucle.
 */
class RetourMatriceTest {

    /**
     * Un parcours : la page d'où l'on part, la destination choisie, et la page que le retour doit
     * rendre.
     */
    private data class Parcours(
        val nom: String,
        val depuis: String,
        val vers: String,
        val retour: String,
        val deuxPanneaux: Boolean = false,
    )

    /** Ce qu'un retour rend, avec le repli du code quand la pile est vide. */
    private fun retour(depuis: String, vers: String, deuxPanneaux: Boolean = false): String? {
        val pile = mutableListOf<String>()
        val route = navigateWithBackStack(depuis, vers, pile)
        return retour(route, pile, deuxPanneaux)
    }

    private fun retour(route: String, pile: MutableList<String>, deuxPanneaux: Boolean): String? {
        // Le `BackHandler` : rien à dépiler et rien au-dessus de la racine, le geste rend la main
        // au système (l'app se ferme) — c'est `null`.
        if (!routeBackHandled(route, pile, deuxPanneaux, "CHAT")) return null
        return popRoute(pile) ?: rootRouteBehind(route, "CHAT")
    }

    /**
     * Les parcours relevés, un par destination : celles du **volet** (les neuf tuiles, plus le
     * « + » des salles), celles des **Réglages**, celles du **rail** des onglets de section, et les
     * **sous-écrans** d'une conversation ou d'une page (la liste des conversations n'est plus une
     * route : elle vit dans le volet, qui est un panneau).
     */
    private val parcours = listOf(
        // — Le volet : chaque tuile, ouverte depuis le fil —
        Parcours("volet · Planifiés", "CHAT", "CRON", "CHAT"),
        Parcours("volet · Kanban", "CHAT", "KANBAN", "CHAT"),
        Parcours("volet · Code", "CHAT", "CODE", "CHAT"),
        Parcours("volet · Outils", "CHAT", "TOOLS", "CHAT"),
        Parcours("volet · Fichiers du serveur", "CHAT", "FILES", "CHAT"),
        Parcours("volet · Modèles", "CHAT", "MODELS", "CHAT"),
        Parcours("volet · Aperçus", "CHAT", "INSIGHTS", "CHAT"),
        // Les tuiles du groupe de la référence : Capacités (comme chez elle), Configuration (la
        // page de la passerelle, distincte des Réglages) et Archives (la destination créée ici).
        Parcours("volet · Capacités", "CHAT", "CAPABILITIES", "CHAT"),
        Parcours("volet · Configuration", "CHAT", "GATEWAY", "CHAT"),
        Parcours("volet · Archives", "CHAT", "ARCHIVED", "CHAT"),
        Parcours("volet · Réglages", "CHAT", "SETTINGS", "CHAT"),
        Parcours("volet · Nouveau bot sans Dashboard", "CHAT", "API_BOT", "CHAT"),
        // — Les Réglages : chaque ligne enfant —
        Parcours("Réglages · Planifiés", "SETTINGS", "CRON", "SETTINGS"),
        Parcours("Réglages · Kanban", "SETTINGS", "KANBAN", "SETTINGS"),
        Parcours("Réglages · Outils", "SETTINGS", "TOOLS", "SETTINGS"),
        Parcours("Réglages · Fichiers", "SETTINGS", "FILES", "SETTINGS"),
        Parcours("Réglages · Serveurs", "SETTINGS", "SERVERS", "SETTINGS"),
        Parcours("Réglages · Apparence", "SETTINGS", "APPEARANCE", "SETTINGS"),
        Parcours("Réglages · Voix", "SETTINGS", "VOICE_SETTINGS", "SETTINGS"),
        Parcours("Réglages · Capacités", "SETTINGS", "CAPABILITIES", "SETTINGS"),
        // — Le rail des onglets de section, ouvert depuis le fil —
        Parcours("rail · Planifiés", "CHAT", "CRON", "CHAT"),
        Parcours("rail · Kanban", "CHAT", "KANBAN", "CHAT"),
        Parcours("rail · Code", "CHAT", "CODE", "CHAT"),
        // — Les sous-écrans d'une conversation —
        Parcours("fil · modèles", "CHAT", "MODELS", "CHAT"),
        Parcours("fil · ordinateur", "CHAT", "COMPUTER", "CHAT"),
        Parcours("fil · terminal", "CHAT", "TERMINAL", "CHAT"),
        Parcours("fil · voix", "CHAT", "VOICE", "CHAT"),
        Parcours("fil · sous-agents", "CHAT", "SUBAGENTS", "CHAT"),
        Parcours("fil · sélection de texte", "CHAT", "SELECT_TEXT", "CHAT"),
        Parcours("fil · correctif", "CHAT", "PATCH", "CHAT"),
        Parcours("fil · capture rapide", "CHAT", "QUICK_CAPTURE", "CHAT"),
        // — Les sous-écrans d'une page —
        // L'éditeur d'une compétence est ouvert par « Capacités » (la tuile « Compétences » a été
        // retirée) : le retour rend « Capacités », la page quittée.
        Parcours("Capacités · une compétence", "CAPABILITIES", "SKILL_EDITOR", "CAPABILITIES"),
        Parcours("Fichiers · un fichier", "FILES", "FILE_EDITOR", "FILES"),
        Parcours("Voix · préfixes parlés", "VOICE_SETTINGS", "SPOKEN_PREFIXES", "VOICE_SETTINGS"),
        Parcours("Bots · un bot", "BOTS", "BOT_EDITOR", "BOTS"),
        Parcours("Bots · salle de groupe", "BOTS", "GROUP_CHAT", "BOTS"),
        Parcours("Salle · voix de la salle", "GROUP_CHAT", "GROUP_VOICE", "GROUP_CHAT"),
        // — Le **panneau Réglages du volet** : la page quittée est le panneau lui-même ————
        // Ces entrées sortent du volet pour une page de l'app (`paneLeave`). Le défaut d'origine :
        // le panneau était effacé de la pile du volet **et** n'entrait pas dans celle des routes,
        // si bien que le retour rendait le fil — jamais les Réglages. Vingt rangées passaient par
        // là ; aucune n'était dans cette matrice (le trou par lequel le défaut est passé).
        Parcours("volet·Réglages · Apparence", "SETTINGS", "APPEARANCE", "SETTINGS"),
        Parcours("volet·Réglages · Voix", "SETTINGS", "VOICE_SETTINGS", "SETTINGS"),
        Parcours("volet·Réglages · Configuration", "SETTINGS", "GATEWAY", "SETTINGS"),
        Parcours("volet·Réglages · Serveurs", "SETTINGS", "SERVERS", "SETTINGS"),
        Parcours("volet·Réglages · Capacités", "SETTINGS", "CAPABILITIES", "SETTINGS"),
        Parcours("volet·Réglages · Terminal", "SETTINGS", "TERMINAL_SETTINGS", "SETTINGS"),
        Parcours("volet·Réglages · Détails de connexion", "SETTINGS", "CONNECTION_DETAILS", "SETTINGS"),
        Parcours("volet·Réglages · Notifications", "SETTINGS", "NOTIFICATIONS", "SETTINGS"),
        // Les six rangées que le volet **porte** (`panePush`) : le retour rend le panneau, qui est
        // resté dans le volet — le chemin conforme, mesuré ici pour que les deux se comparent.
        Parcours("volet·Réglages · Modèles", "SETTINGS", "MODELS", "SETTINGS"),
        // L'éditeur de compétence, ouvert par la tuile **Capacités** : le retour rend Capacités.
        Parcours("volet·Capacités · l'éditeur", "CAPABILITIES", "SKILL_EDITOR", "CAPABILITIES"),
    )

    @Test
    fun `chaque parcours revient sur la page quittee`() {
        parcours.forEach { parcours ->
            assertEquals(
                "${parcours.nom} : le retour doit rendre ${parcours.retour}",
                parcours.retour,
                retour(parcours.depuis, parcours.vers, parcours.deuxPanneaux),
            )
        }
    }

    @Test
    fun `aucun retour ne rejoue l ecran d ou l on vient`() {
        // La seconde moitié de la règle, formalisée : aucune suite de retours ne revient sur un
        // **état** déjà traversé (route + pile), et toute suite finit par quitter l'app. C'est ce
        // qu'un `onBack` écrit en `navTo` cassait : il laissait une entrée derrière lui, donc le
        // geste suivant rouvrait l'écran qu'on venait de quitter.
        parcours.forEach { parcours ->
            val pile = mutableListOf<String>()
            var route = navigateWithBackStack(parcours.depuis, parcours.vers, pile)
            val vus = mutableSetOf<String>()
            var gestes = 0
            var sorti = false
            while (gestes < 8) {
                val etat = route + " | " + pile.joinToString(",")
                assertTrue(
                    "${parcours.nom} : le retour revient sur un état déjà vu ($etat)",
                    vus.add(etat),
                )
                val suivant = retour(route, pile, parcours.deuxPanneaux)
                gestes++
                if (suivant == null) {
                    sorti = true
                    break
                }
                route = suivant
            }
            assertTrue("${parcours.nom} : le retour finit par quitter l'app", sorti)
        }
    }

    @Test
    fun `le rail des onglets de section ne s empile pas`() {
        // Le rail (Planifiés ⇄ Kanban ⇄ Code) est le seul couple qui va et vient sans empiler : les
        // appuis sur retour, eux, ne font **rien** deux fois — la pile reste vide, donc il n'y a
        // aucun niveau caché à rejouer, et l'app se ferme au pied du fil.
        val pile = mutableListOf<String>()
        var route = navigateWithBackStack("CRON", "KANBAN", pile)
        assertEquals("KANBAN", route)
        assertTrue("le rail ne s'empile pas", pile.isEmpty())
        route = navigateWithBackStack(route, "CODE", pile)
        assertEquals("CODE", route)
        assertTrue(pile.isEmpty())
        // Au pied de la pile, un onglet de section rend le fil : un vrai écran, donc le geste ne
        // rend pas la main au lanceur.
        assertEquals("CHAT", rootRouteBehind("CODE", "CHAT"))
        assertFalse(routeBackHandled("CHAT", pile, twoPanes = false, "CHAT"))
    }

    @Test
    fun `la racine ne consomme pas le retour`() {
        // Une colonne : le fil est la racine, et le retour y rend la main au système.
        assertFalse(routeBackHandled("CHAT", emptyList(), twoPanes = false, "CHAT"))
        assertNull(retour("CHAT", "CHAT"))
        // Un onglet de section n'est pas la racine : au pied de sa pile, le retour rend le fil.
        assertTrue(routeBackHandled("CRON", emptyList(), twoPanes = false, "CHAT"))
        assertEquals("CHAT", rootRouteBehind("CRON", "CHAT"))
    }

    @Test
    fun `le pliable deplie a la meme racine que le telephone`() {
        // Deux panneaux : le détail peint le fil, et la liste des conversations **n'est plus une
        // route** (elle vit dans le volet, à gauche) : la racine est la même qu'en une colonne, donc
        // l'app se ferme au premier geste, comme sur un téléphone, au lieu d'en demander deux.
        assertFalse(routeBackHandled("CHAT", emptyList(), twoPanes = true, "CHAT"))
        // Tout le reste se remonte : un onglet de section ouvert hors du rail rend la page quittée.
        assertTrue(routeBackHandled("CRON", emptyList(), twoPanes = true, "CHAT"))
        assertTrue(routeBackHandled("SETTINGS", emptyList(), twoPanes = true, "CHAT"))
        // Une pile non vide se dépile toujours, dans les deux formats.
        assertTrue(routeBackHandled("CHAT", listOf("SETTINGS"), twoPanes = true, "CHAT"))
        assertTrue(routeBackHandled("CHAT", listOf("SETTINGS"), twoPanes = false, "CHAT"))
    }

    @Test
    fun `le pliable deplie revient du tableau sur le fil`() {
        // Le cas du propriétaire : Kanban ouvert du volet, pliable déplié → le retour rend le fil
        // (la page quittée), pas la liste.
        assertEquals("CHAT", retour("CHAT", "KANBAN", deuxPanneaux = true))
        assertEquals("CHAT", retour("CHAT", "CRON", deuxPanneaux = true))
        // Replié, le même parcours rend la même chose : la réponse ne dépend pas du format.
        assertEquals("CHAT", retour("CHAT", "KANBAN", deuxPanneaux = false))
    }

    @Test
    fun `le pliage ne relance pas l activite`() {
        // Le pliage change la **plus petite largeur** (`smallestScreenSize`) : c'est la dimension
        // qui passe de 411 dp (replié) à 862 dp (déplié). Non déclarée au manifeste, Android
        // **relance** l'activité au pliage, et l'app revient au fil — la page ouverte et sa
        // position sont perdues (relevé : `handleRelaunchActivity` pendant le pliage, puis
        // `route=CHAT` sans qu'aucun `navTo` n'ait été appelé). Déclarée, le format change
        // **sur place** : même route, même volet, même position.
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val ligne = manifest.lines().first { it.contains("android:configChanges=") }
        assertTrue("le pliage est déclaré : $ligne", ligne.contains("smallestScreenSize"))
        assertTrue("la rotation aussi : $ligne", ligne.contains("orientation") && ligne.contains("screenSize"))
    }

    @Test
    fun `chaque route declaree est dessinee et atteignable`() {
        // Le tableau complet des routes : aucune valeur de l'énum sans branche de routeur (un écran
        // blanc), et aucune sans entrée qui l'ouvre (une destination injoignable).
        val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()
        val enum = app.substringAfter("private enum class Route {").substringBefore("\n}")
        val sansCommentaires = enum.lines().joinToString("\n") { it.substringBefore("//") }
        val routes = Regex("\\b[A-Z][A-Z_0-9]{2,}\\b")
            .findAll(sansCommentaires)
            .map { it.value }
            .distinct()
            .filter { app.contains("Route.$it") }
            .toList()
        assertTrue("l'énum porte ses routes (${routes.size})", routes.size >= 60)
        routes.forEach { route ->
            assertTrue(
                "Route.$route est dessinée par le routeur",
                app.contains("Route.$route ->") || app.contains("Route.$route,"),
            )
            assertTrue(
                "Route.$route est ouverte par une entrée (navTo/navFromEmpty, ou une tuile du volet)",
                app.contains("navTo(Route.$route.name)") || app.contains("navFromEmpty(Route.$route.name)") ||
                    app.contains("navTo($route)") || app.contains("Route.$route.name else") ||
                    // Depuis ce lot, une tuile du volet **est** une entrée : elle empile son écran
                    // dans le volet (`pushDestination`), l'écran est construit par la branche
                    // `Route.X.name ->`, et `SidebarWiringTest` mesure que la tuile a son écran.
                    com.onyx.app.ui.sidebar.SidebarDestinations.tiles.any { tile -> tile.id == route },
            )
        }
    }

    @Test
    fun `les listes gardent leur position de defilement`() {
        // « Le retour rend la liste au même endroit » : les défilements des écrans sont
        // **sauvegardables**, donc le retour les retrouve — pas un `remember` nu qui repart de zéro.
        val ecrans = listOf(
            "ui/cron/CronScreen.kt",
            "ui/kanban/KanbanScreen.kt",
            "ui/code/CodeScreen.kt",
            "ui/settings/SettingsScreen.kt",
        )
        ecrans.forEach { ecran ->
            val source = File("src/main/java/com/onyx/app/$ecran").readText()
            assertTrue(
                "$ecran : un défilement",
                source.contains("rememberScrollState()") || source.contains("rememberLazyListState()"),
            )
            assertFalse(
                "$ecran : pas de défilement non sauvegardable",
                source.contains("remember { ScrollState(") || source.contains("remember { LazyListState("),
            )
        }
        // Et la page entière est enregistrée par route : ce qui sort de la composition y laisse son
        // état (position, onglet, filtres), que le retour retrouve.
        val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()
        assertTrue(app.contains("val routeStates = rememberSaveableStateHolder()"))
        assertTrue(app.contains("routeStates.SaveableStateProvider(presentedRoute.name)"))
    }

    @Test
    fun `les onglets du bas retrouvent leur etat`() {
        // L'onglet choisi est gardé **par conversation** : revenir dans une conversation rouvre le
        // dernier onglet, et le pageur se pose dessus au lieu de repartir sur Chat.
        val chat = File("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt").readText()
        assertTrue(chat.contains("initialPage = chatTabIndexOf(panels.selectedTab(panelKey))"))
        assertTrue(chat.contains("panels.setSelectedTab(panelKey, ChatTab.entries[page.coerceIn(0, chatTabCount - 1)].key)"))
        val store = File("src/main/java/com/onyx/app/data/local/ChatPanelStore.kt").readText()
        assertTrue(store.contains("fun selectedTab(conversation: String): String?"))
        assertTrue(store.contains("fun setSelectedTab(conversation: String, tab: String)"))
        // Le filtre et le tri de l'onglet Fichiers survivent aussi (ce sont eux qu'on retrouve).
        val filesTab = File("src/main/java/com/onyx/app/ui/chat/FilesTab.kt").readText()
        assertTrue(filesTab.contains("var filterKey by rememberSaveable"))
        assertTrue(filesTab.contains("var sortKey by rememberSaveable"))
    }

    @Test
    fun `le volet garde sa recherche et son filtre`() {
        // Le volet est recomposé par page (deux panneaux) : sa recherche, son filtre, sa position
        // Chats/Bots et le projet choisi sont `rememberSaveable`, donc on les retrouve au retour.
        val host = File("src/main/java/com/onyx/app/ui/sidebar/ChatSidebarHost.kt").readText()
        listOf("tabName", "query", "filterName", "projectId").forEach { champ ->
            assertTrue("le volet garde « $champ »", host.contains("var $champ by rememberSaveable"))
        }
    }

    // ---- Les deux parcours du volet qui ne tiennent pas dans une seule pile ----------------

    @Test
    fun `la feuille des Reglages pousse Planifies sans perdre les Reglages`() {
        // Parcours n° 10 : **fil → Réglages (feuille) → Planifiés → retour**. Le cas que
        // `SettingsReturnTest` simulait seul, rejoué ici dans la matrice : le rail des onglets de
        // section ne vide plus la pile quand on l'atteint **depuis l'extérieur**, donc le retour
        // rend les Réglages — la page quittée — et non la liste.
        val pile = mutableListOf<String>()
        var route = navigateWithBackStack("CHAT", "SETTINGS", pile)
        assertEquals("SETTINGS", route)
        route = navigateWithBackStack(route, "CRON", pile)
        assertEquals("CRON", route)
        assertEquals("les Réglages sont restés dans la pile", listOf("CHAT", "SETTINGS"), pile)
        assertEquals("SETTINGS", retour(route, pile, false))
        assertEquals("CHAT", retour("SETTINGS", pile, false))
        assertNull(retour("CHAT", pile, false))
    }

    @Test
    fun `la fleche du volet depile le volet, celle de l ecran remonte les routes`() {
        // Parcours n° 11 : **volet · Planifiés → la flèche rend la liste du volet**. Le volet a sa
        // propre pile ([OnyxSidebarState.destinations]) et le geste comme la flèche la dépilent un
        // niveau à la fois : écran porté → liste → fermeture. La flèche d'un écran de **route**
        // (Planifiés ouvert en pleine page) ne doit, elle, dépiler le volet que s'il porte **cet**
        // écran — sinon elle dépilait un volet invisible (premier appui sans effet).
        val volet = com.onyx.app.ui.sidebar.OnyxSidebarState()
        volet.open()
        volet.pushDestination("SETTINGS")
        volet.pushDestination("CRON")
        assertEquals("CRON", volet.destination)
        // La flèche de l'écran que le volet porte : un niveau, et le volet reste ouvert.
        assertTrue(volet.popDestination())
        assertEquals("SETTINGS", volet.destination)
        assertTrue(volet.isOpen)
        // La flèche de la **route** Planifiés ne trouve plus son écran dans le volet : elle remonte
        // la pile de routes (`inApp`), pas une pile invisible.
        assertFalse(volet.destination == "CRON")
        // Le geste, lui, suit la même descente : la liste, puis la fermeture.
        com.onyx.app.ui.sidebar.sidebarBackStep(volet)
        assertNull("le volet revient à sa liste", volet.destination)
        assertTrue(volet.isOpen)
        com.onyx.app.ui.sidebar.sidebarBackStep(volet)
        assertFalse("puis il se referme", volet.isOpen)
    }
}
