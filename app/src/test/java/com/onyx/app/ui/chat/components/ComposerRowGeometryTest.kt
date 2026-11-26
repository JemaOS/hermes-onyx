package com.onyx.app.ui.chat.components

import com.onyx.app.ui.design.OnyxSpace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Deux commandes du compositeur ne se chevauchent jamais** — ni à l'œil, ni au doigt.
 *
 * Le défaut signalé sur la capture du propriétaire : « le bouton rond bleu chevauche la puce du
 * modèle ». Sa cause est arithmétique, pas esthétique, et c'est ce qui la rend vérifiable :
 *
 * - la valeur **visuelle** d'une commande de la rangée est de **38 dp** (`ComposerControlSlotHeight`) ;
 * - Android **élargit lui-même** toute cible tactile sous son minimum de **48 dp** — soit **5 dp de
 *   chaque côté** pour une commande de 38 ;
 * - l'écart posé entre deux commandes valait **4 dp**.
 *
 * 4 < 2 × 5 : les deux cibles élargies se recouvraient de **6 dp**. Deux éléments sur les mêmes
 * pixels, exactement ce que l'œil et le doigt lisent comme une superposition — et le relevé de
 * l'arbre d'accessibilité le confirme : sur `1.0.0+547`, le nœud du bouton rond mesurait
 * **42 × 48 dp** pour une allocation de 38.
 *
 * L'écart de la rangée est donc [ComposerRowGeometry.Gap] — la marche `Snug` de l'échelle (12 dp),
 * 12 ≥ 10. Ce test **échoue** si l'écart redescend sous le seuil, si la valeur visuelle change sans
 * que l'écart suive, ou si la marge latérale de la carte quitte l'échelle.
 */
class ComposerRowGeometryTest {

    /** Le relevé de l'appareil sur le build `1.0.0+547` : ce que la rangée posait réellement. */
    private val measuredVisualDp = 38f
    private val measuredGapDpBefore = 4f
    private val systemTouchMinimumDp = 48f

    @Test
    fun `l ancien ecart faisait bien se recouvrir deux cibles — le defaut est reproduit`() {
        assertFalse(
            "avec 4 dp, deux commandes de 38 dp se recouvraient : c'est le défaut de la capture",
            ComposerRowGeometry.targetsClear(
                visualDp = measuredVisualDp,
                gapDp = measuredGapDpBefore,
                minimumDp = systemTouchMinimumDp,
            ),
        )
        // Le recouvrement, chiffré : 2 × (48 − 38)/2 − 4 = 6 dp sur le même pixel.
        val overflow = (ComposerRowGeometry.touchWidthDp(measuredVisualDp, systemTouchMinimumDp) - measuredVisualDp) / 2f
        assertEquals("le débordement d'une cible de 38 dp est de 5 dp par côté", 5f, overflow, 0f)
        assertEquals("donc 6 dp de recouvrement avec l'écart d'avant", 6f, 2f * overflow - measuredGapDpBefore, 0f)
    }

    @Test
    fun `l ecart retenu interdit tout recouvrement, a toutes les tailles de la consigne`() {
        val gap = ComposerRowGeometry.Gap.value
        assertEquals("l'écart est la marche `Snug` de l'échelle", OnyxSpace.Snug, ComposerRowGeometry.Gap)
        assertTrue("et elle est sur l'échelle", OnyxSpace.isOnScale(ComposerRowGeometry.Gap))
        // La taille que l'app pose **réellement** : la cible de la maison (44 dp) pour chaque
        // commande de la rangée — le « + », le micro, le bouton rond, l'envoi, l'agrandissement.
        assertTrue(
            "une commande de 44 dp séparée de $gap dp ne doit pas se recouvrir",
            ComposerRowGeometry.targetsClear(visualDp = ComposerControlSlotHeight.value, gapDp = gap),
        )
        // Et la taille d'hier n'est pas non plus en recouvrement : 12 ≥ 2 × 5 — c'est l'écart seul
        // qui réparait déjà le cas des 38 dp ; c'est lui aussi qui couvre le reste.
        assertTrue(ComposerRowGeometry.targetsClear(38f, gap))
        // **Ce que la règle refuse**, et c'est son intérêt : une commande allouée sous 44 dp. À 30 dp
        // le système élargit la cible de 9 dp par côté, il faudrait 18 dp d'écart. C'est exactement
        // le cas de l'ancien bouton d'agrandissement — la règle l'attrape, la valeur est passée à 44.
        assertFalse(
            "une commande de 30 dp doit être refusée par la règle, pas tolérée",
            ComposerRowGeometry.targetsClear(30f, gap),
        )
        // Et la règle ne dépend pas de la largeur de l'écran : elle est arithmétique.
        assertFalse(ComposerRowGeometry.targetsClear(38f, 9.5f))
        assertTrue(ComposerRowGeometry.targetsClear(38f, 10f))
    }

    @Test
    fun `la puce du modele ne prend jamais la place d une commande`() {
        // La rangée réelle d'un téléphone à 411 dp : la carte, moins ses deux marges, moins les
        // commandes fixes et leurs écarts — le reste va à la puce. Elle est **positive** à toutes les
        // largeurs de la consigne, donc aucune commande n'est poussée hors de sa boîte.
        val gap = ComposerRowGeometry.Gap.value
        val screens = listOf(320f, 360f, 411f, 838f)
        val report = StringBuilder("\nTABLE DE LA RANGÉE DU COMPOSITEUR — dp\n")
        report.append("écran  marge  rangée  fixes  écarts  puce  verdict\n")
        screens.forEach { screenDp ->
            val side = ComposerCardSideMargin.value
            val rowDp = screenDp - 2f * side
            // Cinq éléments : le « + », la puce, le micro, le bouton rond, l'envoi. Quatre sont des
            // commandes fixes (la cinquième, la puce, prend **ce qui reste**), et quatre écarts les
            // séparent.
            val fixed = 4f * ComposerControlSlotHeight.value
            val chip = ComposerRowGeometry.chipWidthDp(rowDp, fixedDp = fixed, count = 5, gapDp = gap)
            val ok = chip > 0f
            report.append(
                "%-6.0f %-6.1f %-7.1f %-6.0f %-7.1f %-5.1f %s\n".format(
                    screenDp, side, rowDp, fixed, gap, chip, if (ok) "ok" else "ÉCHEC",
                ),
            )
            assertTrue("écran $screenDp dp : la puce doit avoir de la place ($chip dp)", ok)
        }
        print(report)
        // La marge de la carte et celle de la pilule sont **la même** : une seule colonne de départ.
        assertEquals(
            "la carte du compositeur et la pilule partagent la marge d'écran",
            OnyxSpace.ScreenMargin,
            ComposerCardSideMargin,
        )
        assertTrue(OnyxSpace.isOnScale(ComposerCardSideMargin))
    }

    @Test
    fun `le seuil de recouvrement est celui que la regle nomme`() {
        // Le verrou du chiffre : la constante, la valeur de la commande et l'écart disent la même chose.
        assertEquals(48f, ComposerRowGeometry.TouchTargetDp, 0f)
        assertEquals(44f, ComposerControlSlotHeight.value, 0f)
        assertTrue(
            "l'écart doit couvrir deux fois le débordement",
            ComposerRowGeometry.Gap.value >= 2f * (ComposerRowGeometry.TouchTargetDp - ComposerControlSlotHeight.value) / 2f,
        )
    }
}
