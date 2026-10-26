package com.onyx.app

import com.onyx.app.ui.conversations.SelectionBarRows
import com.onyx.app.ui.conversations.selectionBarRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La barre de sélection multiple: le compte ne se casse **jamais** mot par mot.
 *
 * Le défaut signalé — le compte tombé en quatre lignes (« 4 / éléments / sélection / nés ») — venait
 * d'un compte laissé seul élément souple d'une ligne serrée entre deux actions de largeur fixe. La
 * barre mesure donc ses textes (en pixels, police du système comprise) et choisit sa forme: le
 * compte en entier à côté des actions, sinon le compte sur **sa** ligne, les actions dessous, et le
 * nombre seul en dernier recours.
 *
 * Le contrôle central n'est pas un cas particulier mais l'**invariant**: chaque fois que le compte
 * partage sa ligne avec les actions, le libellé retenu tient dans la place qui lui reste; et s'il a
 * sa ligne, il y tient aussi. C'est exactement ce qui manquait, et c'est ce qui se vérifie sur
 * toutes les largeurs, aux trois polices.
 */
class SelectionBarRowsTest {
    // Les largeurs mesurées d'un téléphone (pixels, densité ~2,75): le compte en entier
    // (« 4 éléments sélectionnés »), le nombre seul, « Tout désélectionner » et « Annuler ».
    private val full = 412f
    private val short = 28f
    private val selectAll = 418f
    private val cancel = 151f
    private val spacing = 44f

    /** Ce que la ligne doit réserver aux deux actions, écarts compris. */
    private val beside = 2f * spacing + selectAll + cancel

    /** Ce que les deux actions occupent sur une ligne à elles. */
    private val actionsAlone = selectAll + cancel + spacing

    private fun rows(available: Float): SelectionBarRows =
        selectionBarRows(available, full, short, selectAll, cancel, spacing)

    @Test
    fun `un ecran large garde le compte entier sur sa ligne`() {
        val shape = rows(1200f)
        assertFalse("le libellé entier tient à côté des actions : rien à bouger", shape.actionsBelow)
        assertFalse("donc rien à abréger", shape.shortCount)
        assertFalse("rien à empiler non plus", shape.actionsStacked)
    }

    @Test
    fun `sur telephone le compte prend sa ligne, en entier`() {
        // 411 dp à la police normale: la place restante ne porte pas « 4 éléments sélectionnés » à
        // côté de « Tout désélectionner » et « Annuler ». Le compte passe donc **à la ligne**, entier
        // — jamais un mot coupé, et jamais un « 4 » seul alors que la phrase peut s'écrire.
        val shape = rows(1042f)
        assertTrue("le compte ne partage plus sa ligne", shape.actionsBelow)
        assertFalse("il s'écrit en entier, la ligne est à lui", shape.shortCount)
        assertTrue("il y tient", full <= 1042f)
        assertFalse("et les actions tiennent côte à côte dessous", shape.actionsStacked)
    }

    @Test
    fun `le compte ne s'abrege que s il ne tient pas meme seul`() {
        // Un volet de pliable très étroit: 412 px de libellé ne tiennent plus sur une ligne.
        val shape = rows(300f)
        assertTrue(shape.shortCount)
        assertTrue(shape.actionsBelow)
        assertTrue("le nombre, lui, tient toujours", short <= 300f)
    }

    @Test
    fun `au plus etroit les actions prennent chacune leur ligne`() {
        val shape = rows(500f)
        assertTrue(shape.actionsBelow)
        assertTrue("les deux actions ne tiennent pas ensemble dessous", shape.actionsStacked)
        assertTrue("chaque action tient sur la sienne", selectAll <= 500f && cancel <= 500f)
        assertFalse("et le compte garde son libellé entier", shape.shortCount)
    }

    @Test
    fun `le compte partage sa ligne seulement si son libelle y tient`() {
        // L'invariant du défaut signalé, balayé sur toutes les largeurs utiles — téléphone étroit,
        // volet de dépliable, écran large — aux trois polices d'accessibilité (les largeurs
        // mesurées suivent la police et sont donc mises à l'échelle).
        for (scale in listOf(1f, 1.3f, 2f)) {
            val f = full * scale
            val s = short * scale
            val a = selectAll * scale
            val c = cancel * scale
            val besideScaled = 2f * spacing + a + c
            for (available in 80..2400 step 5) {
                val width = available.toFloat()
                val shape = selectionBarRows(width, f, s, a, c, spacing)
                val label = if (shape.shortCount) s else f
                if (shape.actionsBelow) {
                    assertTrue(
                        "police ${scale}×, $width px : le compte a sa ligne, il doit y tenir",
                        label <= width,
                    )
                } else {
                    assertTrue(
                        "police ${scale}×, $width px : le compte tient à côté des actions",
                        label + besideScaled <= width,
                    )
                }
            }
        }
    }

    @Test
    fun `le libelle entier est prefere des qu il tient a cote des actions`() {
        // Le repli n'est pas gratuit: la ligne courte n'apparaît que quand le libellé entier
        // déborde réellement.
        assertFalse("il tient au pixel près : libellé entier", rows(full + beside).actionsBelow)
        assertTrue("un pixel de moins : il descend", rows(full + beside - 1f).actionsBelow)
        assertFalse("et descendre n'abrège pas", rows(full + beside - 1f).shortCount)
    }

    @Test
    fun `abreger est le dernier recours, apres la descente`() {
        // Ordre des replis: le compte descend avant de s'abréger.
        assertTrue("il ne tient plus à côté : il descend", rows(full).actionsBelow)
        assertFalse("et il s'écrit encore en entier sur sa ligne", rows(full).shortCount)
        assertTrue(
            "sous la largeur du libellé, même seul il ne tient plus : le nombre",
            rows(full - 1f).shortCount,
        )
        assertTrue("il est toujours descendu", rows(full - 1f).actionsBelow)
    }

    @Test
    fun `les deux actions partagent leur ligne tant qu elles y tiennent`() {
        val partagee = rows(660f)
        assertFalse("elles y tiennent ensemble", partagee.actionsStacked)
        assertTrue("de 613 px à 660 px, pas d'empilement", actionsAlone <= 660f)
        assertTrue("sous 613 px, chacune prend sa ligne", rows(actionsAlone - 1f).actionsStacked)
        assertEquals("l'empilement ne change pas le compte", partagee.shortCount, rows(actionsAlone - 1f).shortCount)
    }
}
