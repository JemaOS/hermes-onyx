package com.onyx.app.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le pilotage au doigt, calculé et vérifié **sans téléphone ni doigt**.
 *
 * C'est ici que se prouvent les chiffres de la refonte : la proportion du pointeur relatif, la
 * justesse de la visée (au pixel), le sens et la taille d'un cran de molette, l'élan, et le plafond
 * de **paquets par geste** qui empêche un doigt rapide de noyer le pont. Les nombres donnés dans le
 * rapport viennent de ces assertions.
 */
class ComputerTouchTest {

    // Une image 1280 large pour un bureau 3840 : le rapport habituel de l'écran « Ordinateur ».
    private val boxWidth = 1000f
    private val boxHeight = 600f
    private val imageWidth = 1280
    private val imageHeight = 720
    private val screenWidth = 3840
    private val screenHeight = 2160

    private val fit = ComputerScale.fit(boxWidth, boxHeight, imageWidth, imageHeight)

    // ---- le pointeur relatif ---------------------------------------------------------------

    @Test
    fun `le pointeur demarre au centre du bureau, jamais sous le doigt`() {
        assertEquals(ComputerPoint(1920, 1080), ComputerPointer.centered(3840, 2160))
        assertEquals(ComputerPoint(0, 0), ComputerPointer.centered(0, 0))
    }

    @Test
    fun `un mouvement de doigt deplace le pointeur proportionnellement, depuis sa position`() {
        val from = ComputerPoint(1920, 1080)
        // 100 px de vue sur une image large de 1000 px pour un bureau de 3840 : ×3,84.
        val moved = ComputerPointer.advance(
            from = from, dxView = 100f, dyView = 0f,
            imageWidthPx = fit.width, imageHeightPx = fit.height,
            screenWidth = screenWidth, screenHeight = screenHeight,
            gain = ComputerGain.NORMAL,
        )
        assertEquals(1920 + 384, moved.x)
        assertEquals(1080, moved.y)
    }

    @Test
    fun `aucun saut au debut du geste, un doigt immobile ne bouge pas le pointeur`() {
        val from = ComputerPoint(1920, 1080)
        val still = ComputerPointer.advance(
            from = from, dxView = 0f, dyView = 0f,
            imageWidthPx = fit.width, imageHeightPx = fit.height,
            screenWidth = screenWidth, screenHeight = screenHeight,
            gain = ComputerGain.NORMAL,
        )
        assertEquals(from, still)
    }

    @Test
    fun `les trois crans de vitesse s'ordonnent, et le cycle est ferme`() {
        assertTrue(ComputerGain.PRECISION.factor < ComputerGain.NORMAL.factor)
        assertTrue(ComputerGain.NORMAL.factor < ComputerGain.FAST.factor)
        assertEquals(ComputerGain.NORMAL, ComputerGain.PRECISION.next())
        assertEquals(ComputerGain.FAST, ComputerGain.NORMAL.next())
        assertEquals(ComputerGain.PRECISION, ComputerGain.FAST.next())
    }

    @Test
    fun `la precision est chiffrable, un petit geste avance d'autant moins qu'on est fin`() {
        val from = ComputerPoint(1000, 1000)
        fun step(gain: ComputerGain) = ComputerPointer.advance(
            from = from, dxView = 10f, dyView = 0f,
            imageWidthPx = fit.width, imageHeightPx = fit.height,
            screenWidth = screenWidth, screenHeight = screenHeight,
            gain = gain,
        ).x - from.x
        // 10 px de doigt : ~13 px de bureau en précision, ~38 en normal, ~84 en rapide.
        assertEquals(13, step(ComputerGain.PRECISION))
        assertEquals(38, step(ComputerGain.NORMAL))
        assertEquals(84, step(ComputerGain.FAST))
    }

    @Test
    fun `le pointeur ne sort jamais du bureau`() {
        val atEdge = ComputerPoint(3839, 2159)
        val pushed = ComputerPointer.advance(
            from = atEdge, dxView = 9999f, dyView = 9999f,
            imageWidthPx = fit.width, imageHeightPx = fit.height,
            screenWidth = screenWidth, screenHeight = screenHeight,
            gain = ComputerGain.FAST,
        )
        assertEquals(3839, pushed.x)
        assertEquals(2159, pushed.y)
    }

    @Test
    fun `le curseur dessine tombe sur le pixel qui partira au PC, au pixel pres`() {
        // Aller-retour dessin → écran → dessin : erreur nulle, sinon un curseur ment.
        val point = ComputerPoint(1920, 1080)
        val (viewX, viewY) = ComputerPointer.toView(point, fit, screenWidth, screenHeight)
        assertEquals(500f, viewX, 0.001f)
        assertEquals(300f, viewY, 0.001f)
        val back = ComputerScale.toScreen(
            touchX = viewX, touchY = viewY, fit = fit,
            imageWidth = imageWidth, imageHeight = imageHeight,
            screenWidth = screenWidth, screenHeight = screenHeight,
        )
        assertEquals(point, back)
    }

    // ---- la molette -----------------------------------------------------------------------

    @Test
    fun `le defilement est naturel, le doigt vers le haut emmene la page vers le haut`() {
        // Le doigt monte (dyView négatif) : le contenu suit le doigt, donc un cran positif.
        assertTrue(ComputerWheel.notches(-46f) > 0)
        assertTrue(ComputerWheel.notches(46f) < 0)
        assertEquals(0, ComputerWheel.notches(0f))
    }

    @Test
    fun `un doigt qui tremble ne defile pas, un long glissement vaut plusieurs crans`() {
        assertEquals(0, ComputerWheel.notches(10f))
        assertEquals(0, ComputerWheel.notches(-18f))
        assertEquals(3, ComputerWheel.notches(-138f))
        assertEquals(-3, ComputerWheel.notches(138f))
    }

    // ---- l'elan ---------------------------------------------------------------------------

    @Test
    fun `l'elan continue le defilement puis s'arrete tout seul`() {
        // Un lancer vers le haut : la vitesse est négative, l'élan émet des crans positifs.
        val fling = ComputerFling(velocityViewPerMs = -1.2f)
        var total = 0
        var steps = 0
        while (!fling.finished && steps < 200) {
            total += fling.step(ComputerFling.STEP_MS)
            steps += 1
        }
        assertTrue("l'élan doit finir par s'arrêter", fling.finished)
        assertTrue("le lancer vers le haut doit faire défiler vers le bas", total > 0)
        assertTrue("un élan ne doit pas courir indéfiniment", steps in 5..60)
    }

    @Test
    fun `un elan trop lent ne part pas`() {
        val fling = ComputerFling(velocityViewPerMs = 0.001f)
        var total = 0
        var steps = 0
        while (!fling.finished && steps < 100) {
            total += fling.step(ComputerFling.STEP_MS)
            steps += 1
        }
        assertEquals(0, total)
    }

    // ---- le budget de paquets -------------------------------------------------------------

    @Test
    fun `le mouvement est cumule et n'est emis qu'a la cadence du budget`() {
        val budget = ComputerPacketBudget(50L)
        budget.add(10f, 0f)
        budget.add(10f, 0f)
        // Premier créneau : tout ce qui est dû part d'un coup.
        assertEquals(20f to 0f, budget.takeIfDue(1_000L))
        budget.add(5f, 5f)
        assertTrue("trop tôt, rien ne part", budget.takeIfDue(1_010L) == null)
        assertEquals(5f to 5f, budget.takeIfDue(1_050L))
    }

    @Test
    fun `un geste d'une seconde tient dans le plafond de cadence, doigt rapide compris`() {
        // 1000 ms d'un geste échantillonné à ~60 Hz (le doigt bouge vite) :
        val budget = ComputerPacketBudget(ComputerPacketBudget.MOTION_MS)
        var sent = 0
        var t = 0L
        while (t <= 1_000L) {
            budget.add(16f, 8f)
            if (budget.takeIfDue(t) != null) sent += 1
            t += 16L
        }
        // Mesure : 16 messages pour une seconde de glissement échantillonné à 60 Hz (63 mouvements
        // de doigt). Le plafond théorique est de 1000/50 + 1 = 21.
        assertTrue("le pont ne doit pas être noyé : $sent messages", sent <= 21)
        assertTrue("et le pointeur doit tout de même suivre : $sent", sent >= 14)
    }

    @Test
    fun `ce qui reste du est rendu a la fin du geste`() {
        val budget = ComputerPacketBudget(50L)
        budget.add(3f, 4f)
        assertEquals(3f to 4f, budget.takeRemaining())
        assertTrue(budget.takeRemaining() == null)
    }

    // ---- taper, appui long ----------------------------------------------------------------

    @Test
    fun `un appui court et immobile est un clic, un appui long un clic droit`() {
        val slop = 30f
        assertEquals(ComputerFinger.CLICK, ComputerTaps.onRelease(4f, 120L, slop))
        assertEquals(ComputerFinger.RIGHT_CLICK, ComputerTaps.onRelease(4f, 700L, slop))
        assertEquals(ComputerFinger.MOVE, ComputerTaps.onRelease(120f, 120L, slop))
        // Un doigt qui a bougé n'est jamais un clic droit, même tenu longtemps.
        assertEquals(ComputerFinger.MOVE, ComputerTaps.onRelease(120f, 700L, slop))
    }

    @Test
    fun `la tolerance d'un appui est celle du doigt, pas d'un pixel`() {
        assertFalse(ComputerTaps.moving(20f, 30f))
        assertTrue(ComputerTaps.moving(31f, 30f))
    }

    // ---- deux doigts ----------------------------------------------------------------------

    @Test
    fun `l'ecartement des doigts fait un pincement, le glissement commun un defilement`() {
        // Écartement de 200 → 260 : +30 %, c'est un pincement.
        assertEquals(
            ComputerTwoFinger.PINCH,
            ComputerTwoFingerPolicy.decide(200f, 260f, 5f, 30f),
        )
        // Écartement stable, les deux doigts voyagent ensemble : un défilement.
        assertEquals(
            ComputerTwoFinger.SCROLL,
            ComputerTwoFingerPolicy.decide(200f, 205f, 90f, 30f),
        )
        // Rien n'a encore bougé : pas décidé.
        assertEquals(
            ComputerTwoFinger.UNDECIDED,
            ComputerTwoFingerPolicy.decide(200f, 202f, 4f, 30f),
        )
    }

    @Test
    fun `la vitesse du lancer se lit sur la derniere fenetre du doigt`() {
        val velocity = ComputerVelocity(90L)
        velocity.add(0L, 0f, 100f)
        velocity.add(30L, 0f, 60f)
        velocity.add(60L, 0f, 20f)
        val (vx, vy) = velocity.velocity()
        assertEquals(0f, vx, 0.001f)
        // 80 px de vue en 60 ms : le doigt monte vite.
        assertEquals(-80f / 60f, vy, 0.001f)
    }
}
