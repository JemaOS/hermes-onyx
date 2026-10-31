package com.onyx.app.data.net

/**
 * Le doigt, l'image, l'écran du PC : la seule arithmétique qui compte.
 *
 * L'image reçue ne fait pas la taille de l'écran du PC (1280 pixels de large pour un bureau de
 * 3840, ici) et la vue, elle, ne fait ni l'une ni l'autre. Trois échelles se superposent donc, et
 * une erreur d'un seul cran envoie le clic à côté — sur le bureau de quelqu'un. D'où deux règles
 * tenues ici, une fois pour toutes :
 *
 * 1. l'image est **ajustée** dans la vue, jamais déformée (`fit`) : les bandes laissées libres ne
 *    sont pas de l'image, et un doigt posé dessus n'envoie rien (`null`) ;
 * 2. le point de l'image est ramené aux pixels de l'écran du PC par proportion, arrondi, et borné
 *    aux bords — un doigt au bord droit ne peut pas viser un pixel qui n'existe pas.
 */
data class ComputerFit(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height

    fun contains(x: Float, y: Float): Boolean = x >= left && x <= right && y >= top && y <= bottom
}

/** Un point de l'écran du PC, en pixels réels. */
data class ComputerPoint(val x: Int, val y: Int)

object ComputerScale {

    /** L'image entière, aussi grande que possible, centrée dans la boîte : jamais étirée. */
    fun fit(boxWidth: Float, boxHeight: Float, imageWidth: Int, imageHeight: Int): ComputerFit {
        if (boxWidth <= 0f || boxHeight <= 0f || imageWidth <= 0 || imageHeight <= 0) {
            return ComputerFit(0f, 0f, 0f, 0f)
        }
        val scale = minOf(boxWidth / imageWidth, boxHeight / imageHeight)
        val width = imageWidth * scale
        val height = imageHeight * scale
        return ComputerFit((boxWidth - width) / 2f, (boxHeight - height) / 2f, width, height)
    }

    /**
     * Le point de l'écran du PC sous le doigt.
     *
     * `null` quand le doigt est hors de l'image (une bande noire, la barre d'état) : mieux vaut ne
     * rien envoyer que cliquer au hasard.
     */
    fun toScreen(
        touchX: Float,
        touchY: Float,
        fit: ComputerFit,
        imageWidth: Int,
        imageHeight: Int,
        screenWidth: Int,
        screenHeight: Int,
    ): ComputerPoint? {
        if (!fit.contains(touchX, touchY)) return null
        if (fit.width <= 0f || fit.height <= 0f || screenWidth <= 0 || screenHeight <= 0) return null
        val onImageX = (touchX - fit.left) / fit.width * imageWidth
        val onImageY = (touchY - fit.top) / fit.height * imageHeight
        val x = (onImageX * screenWidth / imageWidth).toInt().coerceIn(0, screenWidth - 1)
        val y = (onImageY * screenHeight / imageHeight).toInt().coerceIn(0, screenHeight - 1)
        return ComputerPoint(x, y)
    }
}

/** Ce qu'un geste est devenu. */
enum class ComputerGesture { TAP, DRAG, IGNORED }

object ComputerGestures {

    /**
     * Un geste du doigt, classé.
     *
     * Un **appui** est court et immobile — c'est un clic. Un **glisser** a bougé au-delà de la
     * tolérance — c'est un glisser. Un appui **long et immobile** ne devient rien : l'utilisateur
     * tient son doigt posé pour lire l'écran, et cliquer là serait un clic qu'il n'a pas demandé.
     */
    fun classify(dx: Float, dy: Float, durationMs: Long, slopPx: Float, longPressMs: Long = 600L): ComputerGesture {
        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
        if (distance > slopPx) return ComputerGesture.DRAG
        if (durationMs >= longPressMs) return ComputerGesture.IGNORED
        return ComputerGesture.TAP
    }

    /** La tolérance d'un appui, en pixels, pour un écran donné (env. 12 points). */
    fun slopPx(density: Float): Float = 12f * density
}
