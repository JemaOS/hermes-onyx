package com.onyx.app.ui.design

import androidx.compose.animation.core.SnapSpec
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **« Le fil vivant » — la grammaire des quatre états, tenue par un test.**
 *
 * Ce que ce fichier ferme : l'état de l'agent se lisait à un **spinner** et à une carte isolée.
 * Quatre états existent maintenant, chacun avec une **signature** — un mouvement, une période, une
 * courbe, une amplitude — et la table ci-dessous est la seule source de ces valeurs.
 *
 * Les contrats vérifiés :
 *
 *  1. **quatre états, et quatre signatures distinctes** — deux états qui partagent leur mouvement
 *     *et* leur période ne se distinguent plus ;
 *  2. **chaque état fait ce qu'il annonce** — la réflexion respire (2,6 s `EaseInOut`), le travail
 *     pulse (1,6 s `EaseOut`), l'attente s'immobilise **avec une lueur** (figée, halo > 0), le
 *     repos **s'éteint** (aucun mouvement, aucune lueur) ;
 *  3. **aucune couleur d'état** — la signature ne porte pas de `Color` : les quatre états se
 *     dessinent avec un seul accent, et c'est le mouvement qui les distingue ;
 *  4. **« Réduire les animations » fige tout** — `snap()`, pose de repos, aucune boucle ;
 *  5. **la lecture passe avant l'état** — quand on remonte lire, la période est **multipliée**, et
 *     elle n'est **jamais** plus courte qu'au repos.
 */
class FlowStateTest {

    private val states = FlowState.entries.toList()

    @Test
    fun `quatre etats, une signature distincte chacun`() {
        assertEquals(
            "la grammaire du fil a exactement quatre états",
            listOf(FlowState.REST, FlowState.THINKING, FlowState.WORKING, FlowState.DECIDING),
            states,
        )
        val signatures = states.map { state ->
            val signature = FlowGrammar.signature(state)
            listOf(
                signature.motion.name,
                signature.periodMs.toString(),
                signature.scaleAmplitude.toString(),
                signature.alphaFrom.toString(),
                signature.alphaTo.toString(),
                signature.glowAlpha.toString(),
            ).joinToString("|")
        }
        assertEquals(
            "deux états partagent la même signature : ils ne se distinguent plus à l'écran",
            signatures.size,
            signatures.toSet().size,
        )
        states.forEach { state ->
            val signature = FlowGrammar.signature(state)
            assertTrue("${state.name} : sa période ne peut pas être négative", signature.periodMs >= 0)
            assertTrue("${state.name} : son amplitude ne peut pas être négative", signature.scaleAmplitude >= 0f)
            assertTrue(
                "${state.name} : une lueur au-delà de ${Feedback.FlowGlowMax} devient un aplat coloré",
                signature.glowAlpha <= Feedback.FlowGlowMax,
            )
        }
    }

    @Test
    fun `chaque etat fait ce qu il annonce — respirer, pulser, luire, s eteindre`() {
        val rest = FlowGrammar.signature(FlowState.REST)
        assertEquals("au repos, l'état ne bouge pas", FlowMotion.STILL, rest.motion)
        assertEquals("au repos, l'état s'éteint : aucune lueur", 0f, rest.glowAlpha, 0f)
        assertEquals("au repos, la puce est éteinte", 0f, rest.alphaTo, 0f)

        val thinking = FlowGrammar.signature(FlowState.THINKING)
        assertEquals("la réflexion respire", FlowMotion.BREATH, thinking.motion)
        assertEquals("la respiration est celle de l'orbe", MotionFamily.Orb, thinking.family)
        assertEquals("la respiration dure 2,6 s", 2600, thinking.periodMs)
        assertEquals("sa courbe est EaseInOut", "EaseInOut", thinking.curveName)
        assertEquals(
            "son amplitude est le souffle de l'orbe",
            Feedback.OrbBreath - 1f,
            thinking.scaleAmplitude,
            1e-5f,
        )
        assertEquals("et le souffle de l'état est nommé", Feedback.FlowBreath, thinking.scaleAmplitude, 1e-5f)

        val working = FlowGrammar.signature(FlowState.WORKING)
        assertEquals("le travail pulse", FlowMotion.PULSE, working.motion)
        assertEquals("la pulsation dure 1,6 s", 1600, working.periodMs)
        assertEquals("sa courbe est EaseOut", "EaseOut", working.curveName)
        assertTrue(
            "le travail bat plus vite que la réflexion ne respire",
            working.periodMs < thinking.periodMs,
        )
        assertTrue(
            "le travail bat plus fort que la réflexion ne respire",
            working.scaleAmplitude > thinking.scaleAmplitude,
        )

        val deciding = FlowGrammar.signature(FlowState.DECIDING)
        assertEquals("l'attente s'immobilise", FlowMotion.STILL, deciding.motion)
        assertTrue(
            "l'attente garde une lueur tenue, pas un éclat : ${deciding.glowAlpha}",
            deciding.glowAlpha >= 0.25f && deciding.glowAlpha <= Feedback.FlowGlowMax,
        )
        assertTrue(
            "la lueur de l'attente est plus forte que celle du travail (le travail, lui, bouge)",
            deciding.glowAlpha >= FlowGrammar.signature(FlowState.WORKING).glowAlpha * 0.9f,
        )
        assertTrue(
            "l'attente figée ne se confond pas avec le repos éteint",
            deciding.glowAlpha != rest.glowAlpha,
        )
    }

    @Test
    fun `le repos et l attente sont figes par nature, la reflexion et le travail bouclent`() {
        assertEquals(
            "seuls les états qui bougent montent une boucle",
            listOf(FlowState.THINKING, FlowState.WORKING),
            FlowGrammar.loopingStates,
        )
        assertTrue("le repos ne boucle pas", FlowGrammar.frozen(FlowState.REST, reduceMotion = false))
        assertTrue("l'attente ne boucle pas", FlowGrammar.frozen(FlowState.DECIDING, reduceMotion = false))
        assertFalse("la réflexion boucle", FlowGrammar.frozen(FlowState.THINKING, reduceMotion = false))
        assertFalse("le travail boucle", FlowGrammar.frozen(FlowState.WORKING, reduceMotion = false))
    }

    @Test
    fun `Reduce Motion fige les quatre etats, sans une image intermediaire`() {
        states.forEach { state ->
            assertTrue(
                "${state.name} : sous « Réduire les animations », l'état doit être figé",
                FlowGrammar.frozen(state, reduceMotion = true),
            )
            assertTrue(
                "${state.name} : la spec doit alors être `snap()`",
                FlowGrammar.animationSpec(state, readingUp = false, reduceMotion = true) is SnapSpec,
            )
            val pose = FlowGrammar.pose(state, readingUp = false, reduceMotion = true, progress = 0.7f)
            val signature = FlowGrammar.signature(state)
            assertEquals("${state.name} : pose de repos, sans échelle", 1f, pose.scale, 0f)
            assertEquals("${state.name} : pose de repos, opacité pleine", signature.alphaTo, pose.alpha, 0f)
            assertEquals("${state.name} : pose de repos, lueur tenue", signature.glowAlpha, pose.glow, 0f)
        }
    }

    @Test
    fun `la lecture passe avant l etat, la periode est multipliee et jamais divisee`() {
        assertEquals("le facteur du ralentissement est annoncé", 3, FlowGrammar.ReadingUpFactor)
        FlowGrammar.loopingStates.forEach { state ->
            val calm = FlowGrammar.periodMs(state, readingUp = false)
            val reading = FlowGrammar.periodMs(state, readingUp = true)
            assertTrue(
                "${state.name} : remonter lire doit **ralentir** l'état ($calm → $reading)",
                reading > calm,
            )
            assertEquals(
                "${state.name} : et jamais l'accélérer",
                calm * FlowGrammar.ReadingUpFactor,
                reading,
            )
        }
        // Un état figé reste figé : la lecture ne lui invente pas un mouvement.
        listOf(FlowState.REST, FlowState.DECIDING).forEach { state ->
            assertEquals("${state.name} reste figé", 0, FlowGrammar.periodMs(state, readingUp = true))
            assertEquals("${state.name} reste figé", 0, FlowGrammar.periodMs(state, readingUp = false))
        }
    }

    @Test
    fun `la pose va du repos a la pointe, sans jamais depasser l amplitude`() {
        FlowGrammar.loopingStates.forEach { state ->
            val signature = FlowGrammar.signature(state)
            val rest = FlowGrammar.pose(state, readingUp = false, reduceMotion = false, progress = 0f)
            val peak = FlowGrammar.pose(state, readingUp = false, reduceMotion = false, progress = 1f)
            assertEquals("${state.name} : au repos, l'échelle est 1", 1f, rest.scale, 0f)
            assertEquals(
                "${state.name} : à la pointe, l'échelle porte l'amplitude",
                1f + signature.scaleAmplitude,
                peak.scale,
                1e-4f,
            )
            assertEquals("${state.name} : au repos, l'opacité basse", signature.alphaFrom, rest.alpha, 1e-4f)
            assertEquals("${state.name} : à la pointe, l'opacité haute", signature.alphaTo, peak.alpha, 1e-4f)
            assertTrue(
                "${state.name} : la lueur ne recule pas quand la pointe monte",
                peak.glow >= rest.glow,
            )
            assertTrue(
                "${state.name} : aucune lueur au-delà du plafond de la maison",
                peak.glow <= Feedback.FlowGlowMax,
            )
        }
    }

    @Test
    fun `une seule image d animation par periode, et une image d aller-retour pour la respiration`() {
        val breath = FlowGrammar.signature(FlowState.THINKING)
        val pulse = FlowGrammar.signature(FlowState.WORKING)
        assertEquals(
            "la respiration est un aller-retour : deux demi-périodes",
            1300,
            breath.legMs(readingUp = false),
        )
        assertEquals(
            "la pulsation est une poussée : une période entière",
            1600,
            pulse.legMs(readingUp = false),
        )
        assertEquals(
            "la lecture allonge l'image, elle ne la raccourcit pas",
            breath.legMs(readingUp = true),
            breath.legMs(readingUp = false) * FlowGrammar.ReadingUpFactor,
        )
        assertEquals("la respiration fait l'aller-retour", androidx.compose.animation.core.RepeatMode.Reverse, FlowGrammar.repeatMode(FlowState.THINKING))
        assertEquals("la pulsation repart", androidx.compose.animation.core.RepeatMode.Restart, FlowGrammar.repeatMode(FlowState.WORKING))
    }

    /**
     * **Aucune couleur d'état.** Les quatre états se distinguent par le **mouvement** : le
     * propriétaire a refusé « une couleur saturée par état », et la maison n'a qu'un accent. Ce
     * verrou lit la source : la signature ne déclare aucun `Color`, et la fonction qui dessine
     * prend sa teinte **en paramètre** au lieu de la choisir.
     */
    @Test
    fun `la signature d un etat ne porte aucune couleur`() {
        val source = File("src/main/java/com/onyx/app/ui/design/FlowState.kt").readText()
        val declaration = source.substringAfter("data class FlowSignature(").substringBefore(") {")
        assertFalse(
            "une signature d'état ne déclare aucune couleur : le mouvement seul distingue les états",
            declaration.contains("Color"),
        )
        assertTrue(
            "la grammaire annonce le halo maximal comme une opacité d'accent, pas une teinte",
            source.contains("Feedback.FlowGlowMax"),
        )
    }

    /**
     * **« Réduire les animations » est lu par la seule fonction qu'un écran appelle**, et la boucle
     * n'est même pas *composée* sous le réglage : une boucle infinie à l'échelle zéro dégénère en
     * une itération par trame au lieu de ne pas tourner.
     */
    @Test
    fun `sous Reduce Motion la boucle n est pas composee`() {
        val source = File("src/main/java/com/onyx/app/ui/design/FlowState.kt").readText()
        assertTrue(
            "`rememberFlowPose` doit lire le réglage système",
            source.contains("val reduceMotion = LocalReduceMotion.current"),
        )
        assertTrue(
            "et sortir **avant** de composer la transition",
            source.contains("if (FlowGrammar.frozen(state, reduceMotion)) {"),
        )
    }
}
