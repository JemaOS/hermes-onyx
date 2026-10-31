package com.onyx.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.design.OnyxContrast
import kotlinx.serialization.Serializable

/**
 * Port de `ThemeColors` de `Theme` : les couleurs choisies par le lecteur pour un thème.
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
     * Un choix vaut dans les deux apparences ; les couleurs propres au thème, elles, gardent leurs
     * variantes claire et sombre.
     */
    @Immutable
    @Serializable
    data class RGB(val red: Double, val green: Double, val blue: Double) {
        /** Le sélecteur référence peut rendre des valeurs un peu hors de 0…1 : on les ramène au sRGB. */
        constructor(color: Color) : this(
            red = color.red.toDouble().coerceIn(0.0, 1.0),
            green = color.green.toDouble().coerceIn(0.0, 1.0),
            blue = color.blue.toDouble().coerceIn(0.0, 1.0),
        )

        val color: Color get() = Color(red.toFloat(), green.toFloat(), blue.toFloat())

        /**
         * Quasi-noir ou blanc, selon ce qui se lit le mieux dessus.
         *
         * Une seule règle dans l'app pour « quelle encre sur cette couleur » : celle de
         * [com.onyx.app.ui.design.OnyxContrast.inkOn] — c'est aussi elle qui décide de l'encre des
         * boutons pleins de la barre du bas de l'écran Ordinateur.
         */
        val contrastingText: Color get() = OnyxContrast.inkOn(color)
    }
}
