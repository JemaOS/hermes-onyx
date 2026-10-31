package com.onyx.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * **Le compteur de l'app — un nombre dans une pastille pleine.**
 *
 * Une seule fonction pour tous les compteurs : la barre d'onglets (« Flux »), le compteur d'une
 * tuile du volet, l'étiquette d'un en-tête de section. Elle tire **toute** sa géométrie de
 * [BadgeGeometry], et rien d'autre :
 *
 * - la boîte du chiffre est **mesurée** sous la police réellement rendue (`caption2` du thème, donc
 *   l'échelle de police du système comprise) — c'est elle qui décide ;
 * - la largeur peinte vaut le texte plus [BadgeGeometry.PadHorizontal] de chaque côté, **jamais
 *   moins que la hauteur peinte** (le carré optique d'un seul chiffre) : aucune largeur fixe ;
 * - la hauteur peinte vaut le texte plus [BadgeGeometry.PadVertical] au-dessus et au-dessous : elle
 *   est **dérivée du corps du texte**, donc une police à 1,3× donne une pastille plus haute ;
 * - le nombre s'écrit **entier** jusqu'à [BadgeGeometry.MaxShown], sur une ligne, sans ellipse.
 *
 * La forme est le **stade** de la maison (`RoundedCornerShape(50)`, celui de la barre et du champ) :
 * avec le rembourrage vertical, la courbe ne peut pas passer dans l'encre du chiffre.
 */
@Composable
fun CounterBadge(
    count: Int,
    fill: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val text = BadgeGeometry.label(count)
    val style = theme.caption2.copy(color = contentColor, fontWeight = FontWeight.SemiBold)
    // **La mesure, sous la police réellement rendue.** Le corps du thème porte déjà l'échelle du
    // système (`fontScale`) : la boîte mesurée grandit donc avec la police, et la pastille avec elle.
    val box = with(density) { measurer.measure(text, style, maxLines = 1).size }
    val textWidthDp = with(density) { box.width.toDp().value }
    val textHeightDp = with(density) { box.height.toDp().value }
    val paintedWidthDp = BadgeGeometry.paintedWidthDp(textWidthDp, textHeightDp)
    val paintedHeightDp = BadgeGeometry.paintedHeightDp(textHeightDp)

    Box(
        modifier = modifier
            // **La taille vient de la mesure, pas d'une constante** : les deux minima sont calculés,
            // et le rembourrage intérieur garantit qu'ils sont tenus au pixel près.
            .defaultMinSize(minWidth = paintedWidthDp.dp, minHeight = paintedHeightDp.dp)
            .clip(RoundedCornerShape(50))
            .background(fill)
            .padding(
                horizontal = BadgeGeometry.PadHorizontal,
                vertical = BadgeGeometry.PadVertical,
            ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            localize = false,
            style = style,
            maxLines = 1,
            // **Aucune ellipse : un nombre ne se coupe pas.** Si l'ellipse s'armait, c'est que la
            // boîte peinte serait plus étroite que le texte — exactement ce que le verrou refuse.
            overflow = TextOverflow.Clip,
        )
    }
}
