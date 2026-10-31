package com.onyx.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme

/**
 * **« Halo » : la matière douce derrière le verre.**
 *
 * Le défaut que ce fichier répare n'est pas un défaut de verre, c'est un défaut de **fond** : posée
 * sur un aplat gris, une vitre ne se voit pas. Le verre se lit parce qu'il y a **quelque chose à
 * voir à travers** — une lumière colorée douce, qui bouge d'un bord à l'autre de la page. Le verre
 * n'opacifie jamais le halo et le halo ne remplace jamais le verre : les deux **cohabitent**, dans
 * cet ordre (le halo au fond, la surface au-dessus).
 *
 * La lumière vient de l'**accent du thème de la personne** — jamais d'une couleur étrangère : sur
 * « Paper » le halo est chaud, sur « Slate » il est froid, sur « Hermes » il est doré. C'est la
 * palette d'Onyx, éclaircie, qui fait la matière ; changer de thème change la matière.
 *
 * Les arrêts sont **calculés** ([topStops], [underStops]) : purement, donc éprouvables sans appareil.
 * « Éteint », la liste est **vide** — le halo ne peint pas un « halo discret » résiduel, il ne peint
 * rien du tout.
 */
object Halo {

    /** L'alpha du halo au sommet de la page, selon l'apparence : plus franc sur une page noire. */
    fun topAlpha(theme: ResolvedTheme): Float = when {
        !theme.halo -> 0f
        theme.dark -> 0.18f
        else -> 0.11f
    }

    /**
     * Les arrêts du dégradé du haut, ou **vide** quand le halo est éteint. Pur, donc éprouvable sans
     * Android : la liste vide est la preuve que « éteint » n'aplatit bien qu'à zéro.
     */
    fun topStops(theme: ResolvedTheme): List<Color> {
        val alpha = topAlpha(theme)
        if (alpha <= 0f) return emptyList()
        return listOf(theme.accent.copy(alpha = alpha), Color.Transparent)
    }

    /** L'alpha derrière le contenu (sous les messages, derrière une rangée de boutons). */
    fun underAlpha(theme: ResolvedTheme): Float = when {
        !theme.halo -> 0f
        theme.dark -> 0.06f
        else -> 0.04f
    }

    /** Les arrêts du halo de contenu, ou vide quand il est éteint. */
    fun underStops(theme: ResolvedTheme): List<Color> {
        val alpha = underAlpha(theme)
        if (alpha <= 0f) return emptyList()
        return listOf(Color.Transparent, theme.accent.copy(alpha = alpha), Color.Transparent)
    }
}

/**
 * Le halo du haut d'un écran : un dégradé vertical de l'accent vers le transparent, au fond de la
 * page. Ne peint **rien** quand le halo est éteint ([Halo.topStops] vide).
 */
@Composable
fun HaloBackdrop(
    modifier: Modifier = Modifier,
    height: Dp = 260.dp,
    theme: ResolvedTheme = LocalOnyxTheme.current,
) {
    val stops = Halo.topStops(theme)
    if (stops.isEmpty()) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(Brush.verticalGradient(stops)),
    )
}

/**
 * Le halo derrière le contenu : une lueur d'accent très ténue, posée au fond d'une zone (sous les
 * messages, derrière une rangée de boutons). Rien quand le halo est éteint.
 */
@Composable
fun HaloUnderlay(modifier: Modifier = Modifier, theme: ResolvedTheme = LocalOnyxTheme.current) {
    val stops = Halo.underStops(theme)
    if (stops.isEmpty()) return
    Box(modifier = modifier.background(Brush.verticalGradient(stops)))
}

/**
 * Le halo **peint derrière le contenu** d'une surface : le dégradé du haut y est tracé avant ce que
 * la surface dessine, si bien que le verre posé par-dessus a **quelque chose à voir**.
 *
 * À poser **après** `background(couleur de page)` : la page reste l'aplat du thème, le halo en est
 * la lumière. Ne peint rien quand le halo est éteint ([Halo.topStops] vide).
 */
fun Modifier.haloBackdrop(theme: ResolvedTheme, height: Dp = 260.dp): Modifier = drawWithContent {
    val stops = Halo.topStops(theme)
    if (stops.isNotEmpty()) {
        val h = height.toPx().coerceAtMost(size.height)
        if (h > 0f) {
            drawRect(
                brush = Brush.verticalGradient(stops, startY = 0f, endY = h),
                size = androidx.compose.ui.geometry.Size(size.width, h),
            )
        }
    }
    drawContent()
}

/**
 * **Le fond d'un écran de la maison** : la couleur de page du thème, la **matière** ([matterBackdrop]
 * — les zones froides et chaudes que la vitre échantillonne), puis le halo.
 *
 * Un écran qui pose `background(theme.pageColor)` remplace cet appel par `onyxPage(theme)` et gagne
 * la matière — la vitre qui le recouvre se met alors à se lire, au lieu de se poser sur un aplat.
 *
 * **L'ordre compte** : la page est l'aplat, la matière est sa lumière (elle se pose sur la page), et
 * le halo est la lumière d'accent du haut (il se pose sur la matière). Le halo garde son réglage —
 * éteint, il ne peint rien —, mais la matière **reste** : c'est ce que la vitre a à manger, et un
 * écran plat rendrait le verre invisible même réglage allumé.
 */
fun Modifier.onyxPage(theme: ResolvedTheme, haloHeight: Dp = 260.dp): Modifier =
    this.background(theme.pageColor)
        .matterBackdrop(theme)
        .haloBackdrop(theme, haloHeight)
