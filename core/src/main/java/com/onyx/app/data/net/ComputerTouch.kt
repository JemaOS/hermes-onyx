package com.onyx.app.data.net

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Le pilotage au doigt de l'écran du PC : un **pointeur relatif**, décidé et calculé ici.
 *
 * Le défaut d'origine tenait à une seule chose : le doigt **était** le pointeur. Poser le doigt sur
 * l'aperçu cliquait **là où le doigt se trouvait** (coordonnées absolues), donc il fallait viser la
 * cible avec le bout du doigt — sur un bureau de 3840×2160, cela veut dire couvrir la cible de sa
 * main, et se contorsionner pour un menu ou une case à cocher. Trois conséquences, toutes vécues :
 * on ne sait pas où est le pointeur, on ne peut pas cliquer finement, et il n'existe aucun geste
 * pour défiler ni pour le clic droit.
 *
 * Ici, le doigt et le pointeur sont **découplés** : glisser n'importe où déplace le pointeur
 * **proportionnellement au mouvement du doigt**, depuis sa position courante. Le pointeur se dessine
 * à l'écran (voir l'écran « Ordinateur »), donc on le voit ; on l'affine d'un doigt à côté, sans le
 * couvrir. C'est la logique d'un pavé tactile, et c'est ce que ce fichier rend calculable — donc
 * testable — sans écran ni doigt.
 *
 * Tout y est **pur** : aucun état d'Android, aucune horloge implicite. Les décisions de cadence et
 * d'inertie prennent le temps en paramètre, ce qui permet de rejouer un geste image par image dans
 * un test et de compter exactement ce qui part sur le pont.
 */

/** La vitesse du pointeur pour un même mouvement de doigt : trois crans, jamais un réglage caché. */
enum class ComputerGain(val factor: Float) {
    /** Viser une petite case : le pointeur avance moins vite que le doigt. */
    PRECISION(0.35f),

    /** Le régime habituel : le pointeur suit le doigt dans la même proportion que l'image. */
    NORMAL(1f),

    /** Traverser un grand bureau 4K d'un seul geste. */
    FAST(2.2f);

    /** Le cran suivant, en boucle — un seul bouton fait le tour des trois modes. */
    fun next(): ComputerGain = entries[(ordinal + 1) % entries.size]

    /** La clé traduisible du cran, pour le libellé accessible du bouton de visée. */
    val labelKey: String
        get() = when (this) {
            PRECISION -> "Pointer: precision"
            NORMAL -> "Pointer: normal"
            FAST -> "Pointer: fast"
        }
}

/** Un geste à deux doigts : deux gestes différents sur la même pose, tranchés une fois pour toutes. */
enum class ComputerTwoFinger { UNDECIDED, PINCH, SCROLL }

/**
 * Le pointeur relatif : sa position, et comment un mouvement de doigt la fait avancer.
 *
 * La conversion doigt→pointeur passe par l'échelle de l'image affichée : un pixel de vue vaut
 * `largeurÉcranPC / largeurImageAffichée` pixels du PC. C'est ce qui fait qu'un geste « traverse »
 * le bureau dans les mêmes proportions que l'image le montre, et qu'un aperçu zoomé (où l'on voit
 * moins de bureau) déplace le pointeur plus finement sans rien régler.
 */
object ComputerPointer {

    /** Où le pointeur apparaît la première fois : au centre du bureau, jamais sous le doigt. */
    fun centered(screenWidth: Int, screenHeight: Int): ComputerPoint =
        ComputerPoint(maxOf(0, screenWidth / 2), maxOf(0, screenHeight / 2))

    /** Combien de pixels du PC vaut un pixel de vue, à cette échelle d'image. */
    fun screenPerView(imageSpanPx: Float, screenSpanPx: Int): Float =
        if (imageSpanPx <= 0f || screenSpanPx <= 0) 1f else screenSpanPx / imageSpanPx

    /**
     * Le pointeur après un glissement du doigt de `(dxView, dyView)` pixels de vue.
     *
     * Le résultat est **borné aux bords du bureau** : on ne peut pas viser un pixel qui n'existe
     * pas, et tirer plus loin ne fait que s'arrêter au bord — jamais perdre le pointeur.
     */
    fun advance(
        from: ComputerPoint,
        dxView: Float,
        dyView: Float,
        imageWidthPx: Float,
        imageHeightPx: Float,
        screenWidth: Int,
        screenHeight: Int,
        gain: ComputerGain,
    ): ComputerPoint {
        if (screenWidth <= 0 || screenHeight <= 0) return from
        val perViewX = screenPerView(imageWidthPx, screenWidth)
        val perViewY = screenPerView(imageHeightPx, screenHeight)
        val x = from.x + dxView * perViewX * gain.factor
        val y = from.y + dyView * perViewY * gain.factor
        return ComputerPoint(
            x.roundToInt().coerceIn(0, screenWidth - 1),
            y.roundToInt().coerceIn(0, screenHeight - 1),
        )
    }

    /**
     * Où poser le curseur dans l'aperçu, en pixels de vue.
     *
     * Exactement l'inverse de [ComputerScale.toScreen] : le curseur dessiné tombe **sur le pixel
     * cliqué**, au pixel près. C'est la condition d'une visée honnête — un curseur qui ment de
     * quelques pixels fait cliquer à côté.
     */
    fun toView(
        point: ComputerPoint,
        fit: ComputerFit,
        screenWidth: Int,
        screenHeight: Int,
    ): Pair<Float, Float> {
        if (screenWidth <= 0 || screenHeight <= 0) return fit.left to fit.top
        val x = fit.left + point.x.toFloat() / screenWidth * fit.width
        val y = fit.top + point.y.toFloat() / screenHeight * fit.height
        return x to y
    }
}

/**
 * La molette d'un glissement à deux doigts.
 *
 * Un cran de molette vaut une petite longueur de vue ; un glissement plus court que ça ne fait
 * rien (le doigt qui tremble ne défile pas), et un long glissement vaut plusieurs crans d'un coup.
 */
object ComputerWheel {

    /** Ce qu'un cran de molette vaut en pixels de vue : environ une ligne de texte. */
    const val NOTCH_VIEW_PX = 46f

    /**
     * Le nombre de crans pour un glissement de doigt de `dyView` pixels de vue.
     *
     * **Défilement naturel** : le doigt vers le haut emmène le contenu vers le haut, donc la vue
     * descend — un cran **positif**. Négatif = vers le haut de la page. C'est le signe de la
     * molette du PC, celui qu'attend le pont (négatif = bouton 4, positif = bouton 5).
     */
    fun notches(dyView: Float): Int {
        if (dyView == 0f) return 0
        val raw = -dyView / NOTCH_VIEW_PX
        return if (raw >= 0f) (raw + 0.5f).toInt() else -((-raw + 0.5f).toInt())
    }
}

/**
 * L'élan d'un défilement : la molette continue un peu après que le doigt a lâché, puis s'arrête.
 *
 * Rejouable image par image : on lui donne le pas de temps, elle rend le nombre de crans à émettre
 * **maintenant** (souvent 0) et se déclare finie quand la vitesse retombe. Un élan ne part jamais
 * en boucle : le frottement le tue, et un pas plancher l'arrête même si le calcul traînait.
 */
class ComputerFling(
    velocityViewPerMs: Float,
    private val gain: ComputerGain = ComputerGain.NORMAL,
) {
    private var velocity = velocityViewPerMs
    private var carried = 0f

    /** Vrai quand l'élan a rendu tout son mouvement : l'appelant cesse de l'interroger. */
    var finished: Boolean = false
        private set

    /** Les crans à émettre pour un pas de [stepMs] millisecondes. */
    fun step(stepMs: Float): Int {
        if (finished) return 0
        carried += -velocity * stepMs / ComputerWheel.NOTCH_VIEW_PX * gain.factor
        velocity *= FRICTION
        val whole = if (carried >= 0f) (carried + 0.5f).toInt() else -((-carried + 0.5f).toInt())
        carried -= whole
        if (abs(velocity) < MIN_VELOCITY && abs(carried) < 0.5f) finished = true
        return whole
    }

    companion object {
        /** Le frottement par pas : la vitesse retombe aux deux tiers en une douzaine de pas. */
        const val FRICTION = 0.90f

        /** En dessous de cette vitesse (pixels de vue par milliseconde), l'élan est fini. */
        const val MIN_VELOCITY = 0.02f

        /** Le pas d'un élan : un peu plus lent que l'image, pour ne pas noyer le pont. */
        const val STEP_MS = 33f
    }
}

/**
 * Le plafond de cadence d'un geste : ce qui empêche un doigt qui bouge vite de **noyer le pont**.
 *
 * Un geste produit un mouvement par image d'écran (60 par seconde, ou plus) ; l'envoyer tel quel
 * ferait des centaines de messages par seconde pour un pointeur qui, à l'écran, bouge de toute
 * façon au rythme du flux d'images du PC. On **cumule** donc le mouvement et on n'émet qu'à
 * intervalle fixe — le pointeur local, lui, suit le doigt sans attendre.
 *
 * Le budget est mesuré : un geste d'une seconde tient dans `1000 / minIntervalMs` messages.
 */
class ComputerPacketBudget(val minIntervalMs: Long) {

    private var lastAt = NEVER
    private var dx = 0f
    private var dy = 0f

    /** Cumule un mouvement de doigt, à émettre plus tard. */
    fun add(dxView: Float, dyView: Float) {
        dx += dxView
        dy += dyView
    }

    /**
     * Le mouvement cumulé, s'il est temps de l'émettre — sinon `null`, et il reste cumulé.
     *
     * Un cumul nul ne consomme pas de créneau : on ne paie pas un message pour rien.
     */
    fun takeIfDue(nowMs: Long): Pair<Float, Float>? {
        if (dx == 0f && dy == 0f) return null
        // Le premier créneau est toujours dû : le repère initial est « jamais » et non une borne
        // qui ferait déborder la soustraction.
        if (lastAt != NEVER && nowMs - lastAt < minIntervalMs) return null
        lastAt = nowMs
        val out = dx to dy
        dx = 0f
        dy = 0f
        return out
    }

    /** Ce qui reste dû, à émettre à la fin du geste quoi qu'il arrive. */
    fun takeRemaining(): Pair<Float, Float>? {
        if (dx == 0f && dy == 0f) return null
        val out = dx to dy
        dx = 0f
        dy = 0f
        lastAt = NEVER
        return out
    }

    companion object {
        /** Le repère d'un budget qui n'a encore rien émis : jamais. */
        private const val NEVER = Long.MIN_VALUE

        /** Le pointeur : au plus une vingtaine de messages par seconde, largement assez fluide. */
        const val MOTION_MS = 50L

        /** La molette : un peu plus rare encore, un cran se voyant à l'œil. */
        const val WHEEL_MS = 60L
    }
}

/**
 * Ce qu'un doigt seul a fait, décidé par une règle **pure** — donc vérifiable sans téléphone.
 *
 * Un appui court et immobile est un clic ; un appui long et immobile est un clic droit ; un doigt
 * qui dépasse la tolérance est un déplacement du pointeur. La règle est la même au relâchement et
 * pendant le geste : il n'y a pas deux comportements à tenir cohérents.
 */
enum class ComputerFinger { CLICK, RIGHT_CLICK, MOVE }

object ComputerTaps {

    /** Au-delà de cette immobilité, un appui devient un clic droit (ms). */
    const val LONG_PRESS_MS = 480L

    /** Ce qu'est un doigt relâché : sa distance parcourue, sa durée, la tolérance du doigt. */
    fun onRelease(
        distancePx: Float,
        durationMs: Long,
        slopPx: Float,
        longPressMs: Long = LONG_PRESS_MS,
    ): ComputerFinger = when {
        distancePx > slopPx -> ComputerFinger.MOVE
        durationMs >= longPressMs -> ComputerFinger.RIGHT_CLICK
        else -> ComputerFinger.CLICK
    }

    /** Vrai dès que le doigt a assez bougé pour n'être plus un appui mais un déplacement. */
    fun moving(distancePx: Float, slopPx: Float): Boolean = distancePx > slopPx
}

/**
 * Le tranchage d'une pose à deux doigts : **pincer** pour zoomer, ou **glisser** pour défiler.
 *
 * Les deux gestes commencent pareil (deux doigts posés). On regarde ce qui bouge en premier :
 * l'**écartement** des doigts (pincement) ou leur **déplacement** commun (défilement). Le choix est
 * fait une fois et ne change plus jusqu'au lever — sans quoi un pincement maladroit défilerait, et
 * inversement, au milieu du geste.
 */
object ComputerTwoFingerPolicy {

    /** Un écartement qui a varié de plus de ce rapport est un pincement. */
    const val PINCH_RATIO = 0.12f

    fun decide(
        startSpanPx: Float,
        currentSpanPx: Float,
        driftPx: Float,
        slopPx: Float,
    ): ComputerTwoFinger {
        if (startSpanPx <= 0f) return ComputerTwoFinger.SCROLL
        val ratio = abs(currentSpanPx / startSpanPx - 1f)
        if (ratio >= PINCH_RATIO) return ComputerTwoFinger.PINCH
        if (driftPx > slopPx) return ComputerTwoFinger.SCROLL
        return ComputerTwoFinger.UNDECIDED
    }
}

/**
 * La vitesse d'un glissement, à partir des derniers points : ce qui alimente l'élan.
 *
 * On ne garde qu'une courte fenêtre (celle du doigt qui vient de partir) : une vitesse calculée sur
 * tout le geste ignore le lancer final, celle calculée sur le dernier pixel est bruitée.
 */
class ComputerVelocity(spanMs: Long) {

    private val span = spanMs.coerceAtLeast(1L)
    private data class Sample(val at: Long, val x: Float, val y: Float)

    private val samples = ArrayDeque<Sample>()

    fun add(atMs: Long, x: Float, y: Float) {
        samples.addLast(Sample(atMs, x, y))
        while (samples.size > 2 && samples.first().at < atMs - span) samples.removeFirst()
    }

    /** La vitesse en pixels de vue par milliseconde, `(vx, vy)`, sur la fenêtre gardée. */
    fun velocity(): Pair<Float, Float> {
        if (samples.size < 2) return 0f to 0f
        val first = samples.first()
        val last = samples.last()
        val dt = (last.at - first.at).coerceAtLeast(1L)
        return (last.x - first.x) / dt to (last.y - first.y) / dt
    }
}
