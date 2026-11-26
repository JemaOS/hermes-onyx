package com.onyx.app.ui.design

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Une courbe et une durée par famille — et rien ne dépasse.**
 *
 * Le défaut que ce test ferme est celui du propriétaire : « plat, sans vie ». Il ne manquait pas
 * d'animations, il y en avait onze durées et cinq courbes pour huit intentions — une feuille montait
 * en 280 ms ici et en 300 ms là, une sélection d'onglet ne bougeait pas du tout, et le volet glissait
 * en 320 ms pendant que son contenu apparaissait en 200. Rien ne répondait à rien.
 *
 * Ce test tient donc trois choses, et ce sont les seules qui empêchent la dérive :
 *  1. **une signature unique par famille** — deux familles ne peuvent pas partager la même
 *     `(courbe, durée)`, sinon la huitième intention serait un doublon de la troisième ;
 *  2. **les bornes du geste** — toute famille « micro » (appui, sélection, raisonnement, envoi)
 *     tient sous [MotionFamily.MicroMaxMs] (250 ms). C'est le « rien de plus de ~250 ms » de la
 *     consigne, tenu par un test et pas par une intention ;
 *  3. **Reduce Motion** — `spec(true)` est un [SnapSpec] pour **toutes** les familles, sans
 *     exception : aucune animation ne survit au réglage système, et aucune ne retarde une action.
 */
class MotionFamilyTest {

    private val families = MotionFamily.entries.toList()

    @Test
    fun `une courbe et une duree par famille, et deux familles ne se ressemblent pas`() {
        val signatures = families.map { it.curveName to it.ms }
        assertEquals(
            "deux familles partagent la même (courbe, durée) : ce n'est plus une famille, c'est un doublon",
            signatures.size,
            signatures.toSet().size,
        )
        families.forEach { family ->
            assertTrue("${family.name} : sa durée doit être positive", family.ms > 0)
            assertTrue(
                "${family.name} : une famille déclarée ressort doit porter amortissement ET raideur",
                (family.dampingRatio == null) == (family.stiffness == null),
            )
        }
    }

    @Test
    fun `les familles couvrent les intentions du lot, gestes, ouvertures et ambiances`() {
        // Les gestes nommés par la consigne : appui d'une tuile, sélection d'un onglet, arrivée du
        // raisonnement, changement d'état du bouton d'envoi.
        assertEquals(
            setOf(
                MotionFamily.Press,
                MotionFamily.Tab,
                MotionFamily.Reasoning,
                MotionFamily.Send,
            ),
            MotionFamily.microFamilies.toSet(),
        )
        // Les ouvertures, arrivées et sorties — plus longues, jamais des gestes.
        assertEquals(
            setOf(
                MotionFamily.Open,
                MotionFamily.Dismiss,
                MotionFamily.Panel,
                MotionFamily.Screen,
                MotionFamily.Message,
                // LOT fil-vivant : le changement d'état du fil — une **arrivée** (le nouvel état se
                // pose au lieu de remplacer le précédent), pas un geste : aucun doigt ne la
                // déclenche.
                MotionFamily.FlowShift,
            ),
            MotionFamily.openingFamilies.toSet(),
        )
        // La cascade : courte comme un geste, mais déclenchée par l'apparition d'une liste.
        assertEquals(setOf(MotionFamily.Row), MotionFamily.cascadeFamilies.toSet())
        // Les ambiances : un mouvement qui dure tant que l'écran est là, sans doigt ni destination.
        // LOT fil-vivant : la pulsation d'un état qui travaille rejoint la respiration de l'orbe —
        // deux ambiances, une par état qui bouge (réflexion, travail).
        assertEquals(
            setOf(MotionFamily.Orb, MotionFamily.WorkPulse),
            MotionFamily.ambientFamilies.toSet(),
        )
        // Chaque famille est classée une fois, et une seule.
        assertEquals(
            "chaque famille est soit un geste, soit une arrivée, soit une cascade, soit une ambiance",
            families.toSet(),
            (
                MotionFamily.microFamilies + MotionFamily.openingFamilies +
                    MotionFamily.cascadeFamilies + MotionFamily.ambientFamilies
                ).toSet(),
        )
    }

    /**
     * **Les valeurs d'effet demandées par le propriétaire, vérifiées comme des nombres.**
     *
     * « retour au toucher : échelle 0,96 + vibration courte » ; « capsule qui se déforme légèrement » ;
     * « cascade très courte » ; « respiration lente au repos ». Quatre exigences, quatre chiffres —
     * et pas une seule de ces valeurs écrite dans un écran : elles vivent dans [Feedback].
     */
    @Test
    fun `les valeurs d effet sont celles de la consigne, nommees et bornees`() {
        assertEquals("l'échelle d'appui de la consigne", 0.96f, Feedback.PressScale, 0f)
        assertTrue("la vibration de l'appui est courte", Feedback.PressHapticsMs in 1..30)
        assertTrue("la déformation de la capsule est légère", Feedback.TabSquash in 0.90f..0.98f)
        assertTrue("la cascade est très courte", Feedback.StaggerMs in 20..60)
        assertTrue(
            "le plafond de la cascade borne quatre rangées et pas plus",
            Feedback.StaggerMaxMs <= Feedback.StaggerMs * 5,
        )
        assertTrue("la respiration de l'orbe est un souffle", Feedback.OrbBreath in 1.01f..1.08f)
        assertEquals("la respiration est lente", 2600, MotionFamily.Orb.ms)
    }

    /**
     * **La cascade, la règle pure.** Le délai du n-ième élément est `n × 40 ms`, saturé à 160 ms, et
     * **zéro** sous « Réduire les animations ».
     */
    @Test
    fun `la cascade d une liste est courte, plafonnee et nulle sous Reduce Motion`() {
        assertEquals(0, staggerDelay(0, reduceMotion = false))
        assertEquals(Feedback.StaggerMs, staggerDelay(1, reduceMotion = false))
        assertEquals(Feedback.StaggerMaxMs, staggerDelay(4, reduceMotion = false))
        assertEquals(
            "au-delà du plafond, toutes les rangées partent ensemble",
            staggerDelay(9, reduceMotion = false),
            staggerDelay(20, reduceMotion = false),
        )
        assertEquals("sous Reduce Motion, la cascade n'existe pas", 0, staggerDelay(20, reduceMotion = true))
        assertEquals("un indice négatif ne décale rien", 0, staggerDelay(-3, reduceMotion = false))
    }

    @Test
    fun `la famille de l onglet porte le mouvement de la capsule`() {
        // La sélection d'un onglet n'est plus un fondu de couleur : elle porte un déplacement, donc
        // un ressort — avec un dépassement (amortissement < 1) assez doux pour se poser.
        val damping = MotionFamily.Tab.dampingRatio
        assertTrue("la capsule doit ressortir ($damping)", damping != null && damping < 1f)
        assertTrue("le dépassement doit rester discret ($damping)", damping!! >= 0.75f)
        assertTrue("la sélection reste un geste de la main", MotionFamily.Tab.isMicro)
    }

    @Test
    fun `un geste de la main ne depasse pas 250 ms`() {
        assertEquals("le plafond du geste est 250 ms", 250, MotionFamily.MicroMaxMs)
        MotionFamily.microFamilies.forEach { family ->
            assertTrue(
                "${family.name} : ${family.ms} ms pour un geste de la main, c'est une animation qui " +
                    "retarde l'action (plafond ${MotionFamily.MicroMaxMs})",
                family.ms <= MotionFamily.MicroMaxMs,
            )
            assertTrue("${family.name} doit être reconnue comme micro", family.isMicro)
        }
        // Les ouvertures, elles, ont le droit d'être plus longues — mais elles restent bornées, et
        // aucune n'est un geste.
        MotionFamily.openingFamilies.forEach { family ->
            assertTrue("${family.name} : une ouverture doit durer plus qu'un geste", family.ms > MotionFamily.MicroMaxMs)
            assertTrue("${family.name} ne doit pas être classée micro", !family.isMicro)
        }
        // La cascade est l'inverse : une **arrivée** qui doit rester courte, sans être un geste.
        MotionFamily.cascadeFamilies.forEach { family ->
            assertTrue(
                "${family.name} : une cascade plus longue qu'un geste se lit comme un chargement",
                family.ms <= MotionFamily.MicroMaxMs,
            )
        }
        // L'ambiance est la seule qui ait le droit de durer : c'est une respiration, pas une action.
        MotionFamily.ambientFamilies.forEach { family ->
            assertTrue("${family.name} : une ambiance doit être lente", family.ms > MotionFamily.MicroMaxMs)
        }
    }

    @Test
    fun `Reduce Motion fige toutes les familles`() {
        families.forEach { family ->
            // Aucune exception : c'est ce qui rend le réglage vérifiable par capture.
            assertTrue(
                "${family.name} : sous « Réduire les animations », la spec doit être `snap()`",
                family.spec<Float>(reduceMotion = true) is SnapSpec,
            )
            assertTrue(
                "${family.name} : idem pour une position (IntOffset)",
                family.spec<IntOffset>(reduceMotion = true) is SnapSpec,
            )
            assertTrue(
                "${family.name} : idem pour une dimension (Dp)",
                family.spec<Dp>(reduceMotion = true) is SnapSpec,
            )
            assertTrue(
                "${family.name} : idem pour une couleur",
                family.spec<Color>(reduceMotion = true) is SnapSpec,
            )
        }
    }

    @Test
    fun `hors Reduce Motion, la spec est exactement celle de la famille`() {
        families.forEach { family ->
            val spec = family.spec<Float>(reduceMotion = false)
            if (family.dampingRatio != null) {
                val spring = spec as? SpringSpec<Float>
                assertEquals("${family.name} : ce doit être un ressort", true, spring != null)
                assertEquals("${family.name} : amortissement", family.dampingRatio!!, spring!!.dampingRatio, 0f)
                assertEquals("${family.name} : raideur", family.stiffness!!, spring.stiffness, 0f)
            } else {
                val tween = spec as? TweenSpec<Float>
                assertEquals("${family.name} : ce doit être un `tween`", true, tween != null)
                assertEquals("${family.name} : durée", family.ms, tween!!.durationMillis)
                assertEquals("${family.name} : courbe", family.curve, tween.easing)
            }
        }
    }

    @Test
    fun `les courbes des familles sont celles du vocabulaire de la maison`() {
        val vocabulary = setOf(
            OnyxMotion.EaseOut,
            OnyxMotion.EaseIn,
            OnyxMotion.EaseInOut,
            OnyxMotion.Linear,
            OnyxMotion.Standard,
        )
        families.forEach { family ->
            assertTrue(
                "${family.name} : sa courbe n'est pas du vocabulaire relevé dans la référence",
                vocabulary.contains(family.curve),
            )
            assertTrue("${family.name} : sa courbe doit être nommée", family.curveName.isNotBlank())
            assertTrue("${family.name} : son libellé doit être non vide", family.label.isNotBlank())
        }
    }

    @Test
    fun `la famille des feuilles porte un rebond discret, pas un rebond de jouet`() {
        // « montée + rebond discret » : un amortissement sous 1 (donc un dépassement), mais pas
        // sous 0,7 — au-delà, la feuille oscille et se lit comme un défaut.
        val damping = MotionFamily.Open.dampingRatio
        assertTrue("la famille des feuilles doit porter un amortissement", damping != null)
        assertTrue("la feuille doit ressortir ($damping)", damping!! < 1f)
        assertTrue("le rebond doit rester discret ($damping)", damping >= 0.8f)
    }
}
