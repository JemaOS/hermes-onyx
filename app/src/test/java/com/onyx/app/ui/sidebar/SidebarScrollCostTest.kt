package com.onyx.app.ui.sidebar

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le défilement du volet ne recompose plus rien.**
 *
 * Le défaut du propriétaire : « quand je défile c'est lent », sans rien à changer à l'aspect. La
 * cause n'était pas le dessin mais **l'écriture d'état pendant le défilement** : chaque rangée
 * publiait ses coordonnées à la fenêtre (`bounds = it.boundsInWindow()`) dans un `mutableStateOf`.
 * Pendant que la liste glisse, ces coordonnées changent toutes les 16 ms — donc chaque rangée
 * visible se recomposait ~60 fois par seconde, avec sa vitre, ses encres et ses textes.
 *
 * Ce verrou tient les trois règles du correctif, et rien de plus :
 *
 *  1. la mesure d'une rangée passe par un porteur **muet** ([RowBounds]) — lu au seul appui ;
 *  2. la bande d'encre de la carte ne compose plus ses couleurs dans la boucle de dessin ;
 *  3. les deux résolveurs d'identité du volet sont **mémoïsés** — ils sont les clés du `remember`
 *     qui construit les rangées, donc les recréer rebâtissait toute la liste.
 *
 * Aucune de ces règles ne touche à ce qui est peint : même verre, mêmes encres, mêmes cartes.
 */
class SidebarScrollCostTest {

    private val ui = File("src/main/java/com/onyx/app/ui")
    private val sidebar = File(ui, "sidebar/ChatSidebar.kt").readText()
    private val bots = File(ui, "sidebar/ChatSidebarBots.kt").readText()
    private val app = File(ui, "OnyxApp.kt").readText()
    private val galerie = File("../core/src/main/java/com/onyx/app/ui/design/Galerie.kt").readText()

    // — 1 · la mesure d'une rangée ne réveille personne —

    @Test
    fun `les limites d une rangee vivent dans un porteur muet`() {
        assertTrue(
            "la rangée se mesure dans un porteur muet",
            sidebar.contains("val bounds = remember { RowBounds() }"),
        )
        assertTrue(
            "et elle l'écrit sans publier d'état",
            sidebar.contains(".onGloballyPositioned { bounds.rect = it.boundsInWindow() }"),
        )
        assertFalse(
            "les limites ne sont plus un état : c'était une recomposition par image de défilement",
            sidebar.contains("var bounds by remember { mutableStateOf<Rect?>(null) }"),
        )
        // La carte de bot suit la même règle : elle vit dans la même liste qui défile.
        assertTrue(
            "la carte de bot se mesure aussi sans état",
            bots.contains("val bounds = remember { RowBounds() }") &&
                bots.contains(".onGloballyPositioned { bounds.rect = it.boundsInWindow() }"),
        )
        assertFalse(
            "et elle n'écrit plus d'état en se mesurant",
            bots.contains("var bounds by remember { mutableStateOf<Rect?>(null) }"),
        )
    }

    // — 2 · la bande d'encre ne compose plus de couleur pendant le dessin —

    @Test
    fun `la bande d encre cache ses couleurs au lieu de les composer a chaque image`() {
        assertTrue(
            "les couleurs de la bande sont calculées une fois",
            galerie.contains("val rowColours = remember(colour, rows)"),
        )
        assertTrue(
            "le plateau aussi",
            galerie.contains("val plateau = remember(colour, full)"),
        )
        // La boucle de dessin ne doit plus contenir de composition de couleur : c'était ~90
        // allocations par carte et par image.
        val zone = galerie.substringAfter("private fun Modifier.blocConversationInkZone")
            .substringBefore("fun BlocConversationFace")
        assertFalse(
            "plus aucune composition de couleur dans la boucle de dessin",
            zone.contains("color = colour.copy("),
        )
    }

    // — 3 · les clés des rangées ne changent plus à chaque recomposition —

    @Test
    fun `les resolveurs d identite du volet sont memoises`() {
        assertTrue(
            "le résolveur d'identité est une valeur retenue",
            app.contains("val identityOf: (com.onyx.app.data.model.Conversation) -> String = remember("),
        )
        assertTrue(
            "celui des visages aussi",
            app.contains("val faceCodeOf: (String) -> String? = remember("),
        )
        // Et la liste des bots connus est calculée une fois, plus une fois par rangée.
        assertTrue(
            "la table des bots est retenue",
            app.contains("val knownBots = remember(botsState.bots, botsState.hidden)"),
        )
    }
}
