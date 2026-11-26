package com.onyx.app.data.net

/**
 * L'aperçu : ajusté, zoomé, déplacé — et **toujours borné**.
 *
 * Le zoom et le déplacement ne sont pas deux réglages de plus : ce sont la même transformation que
 * [ComputerScale.fit] applique déjà. Le zoom multiplie le rectangle ajusté, le déplacement le
 * décale, et le résultat reste un [ComputerFit] — donc la même arithmétique de doigt→écran, et un
 * doigt posé hors de l'image qui n'envoie toujours rien.
 *
 * Trois règles tiennent l'ensemble, et aucune ne dépend de l'écran :
 *
 * 1. **on ne dézoome pas sous l'ajustement** : l'image ne rétrécit jamais dans du vide ;
 * 2. **on ne fait pas sortir l'image de son cadre** : le déplacement est borné à ce qui dépasse, si
 *    bien qu'un glissement trop long ramène au bord au lieu de perdre le bureau ;
 * 3. **un double tap inverse** : ajusté → rapproché, rapproché → ajusté, jamais entre deux.
 */
object ComputerViewport {

    /** Le zoom de l'ajustement : l'image entière, au plus grand. */
    const val FIT = 1f

    /** Le plafond de rapprochement. Au-delà, on n'agrandit que des pixels d'image. */
    const val MAX_ZOOM = 6f

    /** Ce qu'un double tap demande quand on est ajusté. */
    const val DOUBLE_TAP_ZOOM = 2.5f

    /** En dessous, on se croit encore à l'ajustement : la marge évite un va-et-vient au double tap. */
    private const val FIT_EPSILON = 0.05f

    fun zoomOf(current: Float): Float = current.coerceIn(FIT, MAX_ZOOM)

    /** Le zoom après un double tap : ajusté devient rapproché, tout le reste redevient ajusté. */
    fun doubleTapZoom(current: Float): Float =
        if (current <= FIT + FIT_EPSILON) DOUBLE_TAP_ZOOM else FIT

    /** Vrai quand l'image est exactement ajustée dans son cadre (donc rien à recentrer). */
    fun isFitted(zoom: Float, panX: Float, panY: Float): Boolean =
        zoom <= FIT + FIT_EPSILON && panX == 0f && panY == 0f

    /**
     * Le zoom demandé par un geste à deux doigts : le rapport des écartements, appliqué au zoom du
     * début du geste. Un écartement nul (les deux doigts au même point) ne change rien.
     */
    fun pinchZoom(startZoom: Float, spanStart: Float, spanNow: Float): Float =
        if (spanStart <= 0f) zoomOf(startZoom) else zoomOf(startZoom * (spanNow / spanStart))

    /**
     * Le déplacement borné.
     *
     * Le cadre laisse une marge `(image − boîte) / 2` de chaque côté quand l'image est plus grande
     * que lui, et **aucune** quand elle est plus petite (elle reste centrée) : c'est ce qui fait
     * qu'à l'ajustement le déplacement ne fait rien du tout.
     */
    fun clampPan(
        boxWidth: Float,
        boxHeight: Float,
        fitWidth: Float,
        fitHeight: Float,
        panX: Float,
        panY: Float,
    ): Pair<Float, Float> {
        val slackX = ((fitWidth - boxWidth) / 2f).coerceAtLeast(0f)
        val slackY = ((fitHeight - boxHeight) / 2f).coerceAtLeast(0f)
        return panX.coerceIn(-slackX, slackX) to panY.coerceIn(-slackY, slackY)
    }

    /** Le rectangle où l'image se dessine, zoom et déplacement compris, borné. */
    fun fitOf(
        boxWidth: Float,
        boxHeight: Float,
        imageWidth: Int,
        imageHeight: Int,
        zoom: Float = FIT,
        panX: Float = 0f,
        panY: Float = 0f,
    ): ComputerFit {
        val base = ComputerScale.fit(boxWidth, boxHeight, imageWidth, imageHeight)
        val z = zoomOf(zoom)
        if (z == FIT && panX == 0f && panY == 0f) return base
        val width = base.width * z
        val height = base.height * z
        val (clampedX, clampedY) = clampPan(boxWidth, boxHeight, width, height, panX, panY)
        return ComputerFit(
            left = (boxWidth - width) / 2f + clampedX,
            top = (boxHeight - height) / 2f + clampedY,
            width = width,
            height = height,
        )
    }

    /**
     * Le déplacement qui rapproche **sous le doigt** : le point de l'image touché au double tap
     * reste sous ce doigt après le zoom, au lieu de filer au centre.
     *
     * Rend le déplacement à appliquer au nouveau zoom (déjà borné).
     */
    fun panToFocus(
        boxWidth: Float,
        boxHeight: Float,
        imageWidth: Int,
        imageHeight: Int,
        zoom: Float,
        focusX: Float,
        focusY: Float,
    ): Pair<Float, Float> {
        val base = ComputerScale.fit(boxWidth, boxHeight, imageWidth, imageHeight)
        if (base.width <= 0f || base.height <= 0f) return 0f to 0f
        val z = zoomOf(zoom)
        val width = base.width * z
        val height = base.height * z
        // La fraction de l'image sous le doigt, à l'ajustement.
        val u = ((focusX - base.left) / base.width).coerceIn(0f, 1f)
        val v = ((focusY - base.top) / base.height).coerceIn(0f, 1f)
        // Où le coin haut-gauche doit tomber pour la garder là, à ce zoom.
        val wantedLeft = focusX - u * width
        val wantedTop = focusY - v * height
        val panX = wantedLeft - (boxWidth - width) / 2f
        val panY = wantedTop - (boxHeight - height) / 2f
        return clampPan(boxWidth, boxHeight, width, height, panX, panY)
    }

    /**
     * Le geste à deux doigts, ramené à un seul cran.
     *
     * Trop de mouvement pour un rapport d'écartement : un pincement de 200 px qui donne ×3 d'un
     * coup, c'est un aperçu qui saute. Chaque geste rend donc au plus [MAX_PINCH_STEP] par rapport
     * d'image, ce qui garde la main maîtresse.
     */
    const val MAX_PINCH_STEP = 2.5f

    fun pinchZoomBounded(startZoom: Float, spanStart: Float, spanNow: Float): Float {
        val wanted = pinchZoom(startZoom, spanStart, spanNow)
        val ceiling = zoomOf(startZoom) * MAX_PINCH_STEP
        val floor = zoomOf(startZoom) / MAX_PINCH_STEP
        return wanted.coerceIn(maxOf(floor, FIT), minOf(ceiling, MAX_ZOOM))
    }
}
