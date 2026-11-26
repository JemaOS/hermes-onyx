package com.onyx.app.ui.chat.components

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le fil garde ses rangées d'outils quand seul le texte change.**
 *
 * Relevé du propriétaire, capture à l'appui : « ça lag quand il y a beaucoup de trucs comme ça dans
 * le chat » — une réponse qui a lancé huit outils, donc huit cartes empilées pendant que la réponse
 * s'écrit encore. Le corps de chaque carte était peint **depuis la boucle**, avec ses lambdas
 * recréées à chaque passage : rien n'était sautable, donc rien n'était sauté, et les huit cartes se
 * redessinaient à la cadence du texte.
 *
 * Ce verrou tient les trois choses qui font qu'une rangée peut être sautée — et elles se perdent
 * facilement, parce qu'aucune ne se voit à l'écran :
 *
 * 1. la boucle **identifie** chaque carte par son appel (`key(tool.id)`) ;
 * 2. la carte est une composable **à part**, qui ne reçoit que ce qui la concerne ;
 * 3. la lambda d'ouverture est **une seule**, mémorisée — recréée par passage, elle rendait toutes
 *    les cartes non sautables à elle seule.
 */
class ToolRowsStayCheapTest {

    private val source = File("src/main/java/com/onyx/app/ui/chat/components/MessageRow.kt").readText()

    /** Le source **sans ses commentaires** : une règle porte sur le code, pas sur ce qui l'explique. */
    private val code = source.lineSequence()
        .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") || it.trimStart().startsWith("/*") }
        .joinToString("\n")

    @Test
    fun `chaque rangee d outil est identifiee par son appel`() {
        val boucle = source.substringAfter("tools.forEach { tool ->").substringBefore("FoldBody")
        assertTrue(
            "la boucle doit poser une clé : sans elle, Compose ne reconnaît pas la carte qui n'a pas bougé",
            source.contains("key(tool.id) {"),
        )
        assertTrue("et peindre la rangée dédiée", boucle.isNotEmpty() && source.contains("ToolStepRow("))
    }

    @Test
    fun `la rangee ne recoit que ce qui la concerne`() {
        val rangee = source.substringAfter("private fun ToolStepRow(")
        val tete = rangee.substringBefore(") {")
        assertTrue("l'appel", tete.contains("tool: ToolActivity,"))
        assertTrue("l'état vivant", tete.contains("isLive: Boolean,"))
        assertTrue("la lecture vers le haut", tete.contains("readingUp: Boolean,"))
        assertTrue("et l'ouverture, une seule fois pour toute la liste", tete.contains("onOpen: (String) -> Unit,"))
        // Rien de ce qui change à chaque image ne doit entrer dans la rangée.
        assertFalse("la rangée ne prend pas le texte de la réponse", tete.contains("content: String"))
        assertFalse("ni l'index de la boucle", tete.contains("index"))
    }

    @Test
    fun `la lambda d ouverture est memorisee et partagee`() {
        assertTrue(
            "une seule lambda pour toutes les rangées",
            source.contains("val onOpenStep: (String) -> Unit = remember { { id -> detailOf = id } }"),
        )
        assertTrue(
            "et c'est elle que la rangée reçoit",
            source.contains("onOpen = onOpenStep,"),
        )
        assertFalse(
            "plus de lambda recréée par carte",
            source.contains("onOpen = { detailOf = it },"),
        )
    }

    @Test
    fun `le libelle et la duree se lisent une fois par appel`() {
        assertTrue(
            "le libellé est mémorisé sur l'appel",
            source.contains("val rowLabel = remember(tool) { toolRowLabel(tool) }"),
        )
        assertTrue(
            "la durée aussi",
            source.contains("val elapsed = remember(tool) { toolElapsedSeconds(tool) }"),
        )
    }

    @Test
    fun `le filet du repli ne coute plus de mesure intrinsèque`() {
        val fold = code.substringAfter("private fun FoldBody(").substringBefore("private fun toolsSummary")
        assertFalse(
            "`IntrinsicSize.Min` traverse tous les enfants du repli — une passe de plus sur chaque carte",
            fold.contains("IntrinsicSize.Min"),
        )
        assertTrue("le filet est peint derrière la colonne", fold.contains("drawBehind {"))
        assertTrue("et garde ses 2 dp", fold.contains("Size(2.dp.toPx(), size.height)"))
    }
}
