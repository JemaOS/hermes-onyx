package com.onyx.app.ui.design

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'expressivité des bots : les cinq états de conversation, leurs micro-expressions (sourcils,
 * paupières, regard), le fondu d'un état à l'autre, et la vie minuscule des listes.
 *
 * Ces vérifications portent sur la **règle** (l'humeur pose quelles micro-expressions) et sur le
 * **câblage du dessin** ; c'est la capture d'émulateur qui juge le rendu.
 */
class BotExpressionTest {

    private val source = File("src/main/java/com/onyx/app/ui/design/BotAvatar.kt").readText()

    @Test
    fun `les cinq etats de conversation, et l attente reste neutre`() {
        // Écoute : sourcils hauts, regard vers vous.
        assertTrue("écoute : sourcils hauts", BotMood.LISTENING.expression.brow > 0.2f)
        // Réflexion : sourcils rapprochés.
        assertTrue("réflexion : sourcils rapprochés", BotMood.THINKING.expression.brow < -0.2f)
        // Succès : sourcils levés.
        assertEquals(0.30f, BotMood.PLEASED.expression.brow, 0.0001f)
        // Erreur : sourcils froncés.
        assertTrue("erreur : sourcils froncés", BotMood.ERROR.expression.brow < -0.5f)
        // Attente : le repos, un rien de doute — jamais une expression marquée.
        assertEquals(0f, BotMood.IDLE.expression.brow, 0f)
        assertEquals(0f, BotMood.IDLE.expression.squint, 0f)
        assertNull(BotMood.IDLE.expression.look)
        assertTrue(Math.abs(BotMood.WAITING.expression.brow) <= 0.2f)
    }

    @Test
    fun `chaque etat a son regard, la reflexion voit de cote`() {
        assertNull("au repos, le regard reste celui du visage", BotMood.IDLE.expression.look)
        val thinking = BotMood.THINKING.expression.look!!
        assertTrue("réflexion : le regard part de côté", thinking.x > 0.6f)
        val listening = BotMood.LISTENING.expression.look!!
        assertTrue("écoute : le regard vient vers vous", listening.y > 0f)
    }

    @Test
    fun `un visage au repos ne porte aucun sourcil`() {
        // Près de zéro, rien n'est tracé : la créature ordinaire reste exactement celle d'avant.
        assertTrue(source.contains("if (Math.abs(b) < 0.02f) return"))
    }

    @Test
    fun `la transition d humeur est fondue, et nette sous Reduce Motion`() {
        assertTrue(source.contains("val progress = remember { Animatable(1f) }"))
        assertTrue(source.contains("progress.animateTo(1f, OnyxMotion.easeInOut(OnyxMotion.MS_350))"))
        assertTrue("sous Reduce Motion, la bascule est nette", source.contains("progress.snapTo(1f)"))
        assertTrue(source.contains("progress.snapTo(0f)"))
        assertTrue("l'ancien visage reste dessous", source.contains("previous.value = stage"))
        assertTrue(source.contains("Modifier.matchParentSize().alpha(progress.value)"))
    }

    @Test
    fun `les micro-expressions se dessinent`() {
        assertTrue("les sourcils sont tracés", source.contains("private fun DrawScope.drawBrows("))
        assertTrue("la respiration déplace le corps", source.contains("translate(0f, -breath * 1.4f)"))
        assertTrue(
            "les paupières rétrécissent l'œil sans le fermer",
            source.contains("1f - pose.squint.coerceIn(0f, 1f) * 0.35f"),
        )
        // Les trois temps de la pose sont portés par `BotPose`.
        assertTrue(source.contains("val brow: Float = 0f"))
        assertTrue(source.contains("val squint: Float = 0f"))
        assertTrue(source.contains("val breath: Float = 0f"))
    }

    @Test
    fun `les listes ont une vie minuscule a elles`() {
        // Le clignement seul, tiré au sort à chaque fois : plusieurs visages ne clignent pas
        // ensemble, et une liste de vingt bots ne re-dessine qu'un visage de temps en temps.
        assertTrue(source.contains("delay(Random.nextLong(4_000L, 7_001L))"))
        assertTrue(source.contains("val blinking = rememberBlink(pose == null && blinks && mood != BotMood.WORKING)"))
    }
}
