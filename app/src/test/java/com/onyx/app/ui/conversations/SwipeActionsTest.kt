package com.onyx.app.ui.conversations

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les actions de glissement, dignes.
 *
 * Le défaut relevé sur la capture: trois pavés à coins carrés, collés entre eux et au bord droit,
 * sans marge, du texte seul sans icône. Ces tests tiennent la règle 17/18/26 — un pavé
 * arrondi par action, l'icône **au-dessus** du libellé, du blanc sur une teinte **pleine**, 8 dp
 * entre les pavés et 12 dp de marge autour du groupe — et la rangée qui suit le doigt découpée au
 * même arrondi.
 */
class SwipeActionsTest {

    private val screen = File("src/main/java/com/onyx/app/ui/conversations/ConversationsScreen.kt").readText()
    private val actions = screen.substringAfter("private fun SwipeableRowActions(")
        .substringBefore("/** Le grand cercle de verre de la barre")

    @Test
    fun `chaque action est un pave arrondi, pas un carre colle au bord`() {
        assertTrue("la largeur d'un pavé est définie", screen.contains("private val SwipeActionWidth = 84.dp"))
        assertTrue("un rayon d'arrondi est défini", screen.contains("private val SwipeActionCorner = 16.dp"))
        assertTrue("le pavé est découpé au même arrondi", actions.contains(".clip(RoundedCornerShape(SwipeActionCorner))"))
        assertTrue("le fond est plein, pas teinté à 16 %", actions.contains(".background(tint)"))
        assertFalse("plus de fond délavé", actions.contains("tint.copy(alpha = 0.16f)"))
    }

    @Test
    fun `les pavees ont une marge et un espacement, jamais colles entre eux`() {
        assertTrue("8 dp entre les pavés", screen.contains("Arrangement.spacedBy(SwipeActionGap)"))
        assertTrue("marge de 12 dp autour du groupe", actions.contains("padding(horizontal = SwipeActionMargin, vertical = 6.dp)"))
        assertTrue("aucun pavé ne touche le bord", screen.contains("private val SwipeActionMargin = 12.dp"))
    }

    @Test
    fun `chaque action porte son icone au-dessus de son libelle, en blanc`() {
        assertTrue("une icône au-dessus du libellé", actions.contains("Column(") && actions.contains("horizontalAlignment = Alignment.CenterHorizontally"))
        assertTrue("l'icône de l'épingle", actions.contains("symbol = Symbols.Pin"))
        assertTrue("l'icône de l'archivage", actions.contains("symbol = Symbols.ArchiveBox"))
        assertTrue("l'icône de la corbeille", actions.contains("symbol = Symbols.Trash"))
        assertTrue("le visage suit le doigt", actions.contains("OnyxIcon(symbol = symbol, tint = Color.White"))
        assertTrue("le libellé est blanc", actions.contains("color = Color.White"))
    }

    @Test
    fun `les teintes sont celles , pleines`() {
        assertTrue("épingler prend l'accent du thème", actions.contains("tint = theme.accent"))
        assertTrue("archiver prend un gris système", actions.contains("tint = SwipeActionGray"))
        assertTrue("le rouge destructif pour supprimer", actions.contains("tint = theme.error"))
    }

    @Test
    fun `la rangee suit le doigt et se decoupe au meme arrondi`() {
        assertTrue("le contenu s'écarte de la largeur du volet", actions.contains(".offset { IntOffset(offset.value.roundToInt(), 0) }"))
        assertTrue("la découpe suit l'arrondi des pavés", actions.contains("clip = offset.value < -0.5f"))
        assertTrue("le glissement suit le doigt", actions.contains("offset.snapTo(next)"))
    }

    @Test
    fun `un glissement franc declenche la premiere action`() {
        assertTrue("le volet plus un cran déclenche", actions.contains("actionsWidth.toFloat() + SwipeFullGesture.toPx()"))
        assertTrue("la première action est épingler", actions.contains("onPin(conversation, !conversation.pinned)"))
    }
}
