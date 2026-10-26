package com.onyx.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

/**
 * Comme `ThemeColors` de: les couleurs choisies par le lecteur pour un thème.
 *
 * Un champ absent garde la couleur propre au thème, si bien que changer de thème n'emporte jamais
 * les choix faits sur un autre.
 */
@Immutable
@Serializable
data class ThemeColors(
    val accent: RGB? = null,
    val bubble: RGB? = null,
) {
    val isEmpty: Boolean get() = accent == null && bubble == null

    /**
     * Une couleur sRGB qui survit à un aller-retour JSON.
     *
     * Un choix vaut dans les deux apparences; les couleurs propres au thème, elles, gardent leurs
     * variantes claire et sombre.
     */
    @Immutable
    @Serializable
    data class RGB(val red: Double, val green: Double, val blue: Double) {
        /** Le sélecteur peut rendre des valeurs un peu hors de 0…1: on les ramène au sRGB. */
        constructor(color: Color) : this(
            red = color.red.toDouble().coerceIn(0.0, 1.0),
            green = color.green.toDouble().coerceIn(0.0, 1.0),
            blue = color.blue.toDouble().coerceIn(0.0, 1.0),
        )

        val color: Color get() = Color(red.toFloat(), green.toFloat(), blue.toFloat())

        /** Quasi-noir ou blanc, selon ce qui se lit le mieux dessus (luminance relative WCAG). */
        val contrastingText: Color
            get() {
                fun linear(c: Double): Double = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
                val luminance = 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)
                val againstWhite = 1.05 / (luminance + 0.05)
                val againstDark = (luminance + 0.05) / 0.0569   // la luminance de #1B1B1D, plus 0.05
                return if (againstWhite >= againstDark) Color.White else Color(0.106f, 0.106f, 0.114f)
            }
    }
}

private fun Double.pow(exponent: Double): Double = Math.pow(this, exponent)
