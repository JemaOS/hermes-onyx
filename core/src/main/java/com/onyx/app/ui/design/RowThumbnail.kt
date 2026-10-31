package com.onyx.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ReferencePalette
import com.onyx.app.ui.theme.ResolvedTheme

/**
 * **La vignette d'icône d'une rangée** : un carré arrondi de 44 dp, une icône du jeu normalisé
 * dedans, une teinte vive et une **ombre douce**.
 *
 * Pourquoi un composant à part, alors que chaque écran dessinait déjà « un carré avec une icône » :
 * le relevé de ce lot a trouvé, pour ce seul élément, **quatre** tailles (34, 38, 40, 44), **trois**
 * rayons (10, 11, 12) et tantôt une ombre, tantôt aucune. Deux lignes voisines d'une même feuille ne
 * portaient donc pas la même vignette — et c'est exactement ce que l'œil lit comme « pas soigné ».
 * Il n'y a plus qu'**une** taille ([OnyxSpace.ThumbnailSize]) et qu'**un** rayon
 * ([OnyxSpace.ThumbnailCorner]) : ceux de la famille des formes.
 *
 * **La forme se lit par la lumière et l'ombre, jamais par un contour.** La teinte est un dégradé
 * vertical (claire en haut, plus dense en bas) et une **ombre portée douce** détache la vignette de
 * sa rangée ; aucune bordure d'un pixel n'est tracée. C'est la règle de la maison pour toutes les
 * surfaces, appliquée ici au plus petit élément.
 *
 * **La teinte vive, mais la maison.** La couleur vient de l'appelant (l'accent du thème, ou la
 * couleur d'un bot) ; à défaut, c'est l'accent du thème. Rien n'est repris d'ailleurs : la vignette
 * prend la palette d'Onyx, jamais une couleur étrangère. L'encre de l'icône est **mesurée**
 * ([thumbnailInk]) sur la teinte composée, jamais choisie à l'œil.
 */

/** Le côté de l'icône dans la vignette. Une icône du jeu normalisé (grille 24, trait 2). */
val ThumbnailIconSize: Dp = 22.dp

/** L'ombre portée de la vignette : **l'ombre unique de l'app** ([OnyxShadow.Float]). */
val ThumbnailShadow: Dp = OnyxShadow.Float

/** Sous ce rapport, la teinte ne se distingue plus du panneau : ce n'est plus une vignette. */
const val ThumbnailVisible: Float = 1.06f

/** L'opacité de la teinte de repli (l'encre du texte) quand l'accent lui-même est trop pâle. */
const val ThumbnailTintStep: Float = 0.16f

/**
 * La teinte de la vignette, composée sur sa rangée.
 *
 * **La couleur vive d'abord** : `colour` (l'accent du thème, ou la couleur d'un bot) telle quelle,
 * composée sur le panneau. C'est ce qui donne à chaque vignette sa couleur propre, au lieu d'un
 * pastel délavé.
 *
 * **Le repli** : sur deux thèmes clairs l'accent est presque aussi clair que le panneau — un bleu
 * d'accent pâle sur une page ivoire donne 1,03:1, une vignette invisible. La vignette prend alors la
 * teinte de l'**encre du texte**, qui se voit toujours quelle que soit la palette. C'est la mesure
 * qui décide, pas l'œil.
 */
fun thumbnailTint(colour: Color, theme: ResolvedTheme): Color {
    val accentTint = OnyxContrast.over(colour, theme.surfaceColor)
    if (OnyxContrast.ratio(accentTint, theme.surfaceColor) >= ThumbnailVisible) return accentTint
    return OnyxContrast.over(theme.textColor.copy(alpha = ThumbnailTintStep), theme.surfaceColor)
}

/**
 * Le dégradé de la vignette : la teinte éclaircie en tête, assombrie en pied.
 *
 * Deux directions opposées plutôt qu'un aplat : c'est ce qui fait lire un **volume** et non une
 * pastille de couleur — la même règle que la lumière de tête et l'ombre de pied des vitres.
 */
fun thumbnailGradient(colour: Color, theme: ResolvedTheme): Brush {
    val base = thumbnailTint(colour, theme)
    val light = lerp(base, Color.White, if (theme.dark) 0.22f else 0.30f)
    val deep = lerp(base, Color.Black, if (theme.dark) 0.10f else 0.16f)
    return Brush.verticalGradient(listOf(light, deep))
}

/**
 * L'encre de l'icône sur sa vignette : la **couleur de la maison**, écartée du fond juste assez pour
 * tenir le seuil des icônes (3:1) — et, si même l'accent décalé au maximum n'y suffit pas, l'encre
 * qui se lit le mieux sur la teinte (blanc ou presque-noir).
 *
 * L'idée : une vignette garde son identité (la couleur du thème ou du bot) le plus longtemps
 * possible ; c'est l'**encre** qui s'écarte, jamais la teinte. Une icône illisible dans une vignette
 * vive est le défaut le plus facile à ne pas voir — il se mesure ici, pas à l'œil.
 */
fun thumbnailInk(colour: Color, theme: ResolvedTheme): Color {
    val tint = thumbnailTint(colour, theme)
    if (OnyxContrast.ratio(colour, tint) >= OnyxContrast.ICON_MIN) return colour
    // La direction qui offre le plus de place — le noir sur une teinte claire, le blanc sur une
    // teinte sombre — et, en dernier recours, cette encre-là : l'une des deux tient toujours 3:1.
    val towards =
        if (OnyxContrast.ratio(Color.Black, tint) >= OnyxContrast.ratio(Color.White, tint)) Color.Black
        else Color.White
    var far = 0f
    var near = 1f
    repeat(20) {
        val middle = (far + near) / 2f
        if (OnyxContrast.ratio(lerp(colour, towards, middle), tint) >= OnyxContrast.ICON_MIN) {
            near = middle
        } else {
            far = middle
        }
    }
    val shifted = lerp(colour, towards, near)
    return if (OnyxContrast.ratio(shifted, tint) >= OnyxContrast.ICON_MIN) shifted else towards
}

/**
 * **La teinte neutre d'une vignette** — la règle de la maison depuis le lot « zéro couleur
 * décorative ».
 *
 * Le propriétaire a dénoncé, sur la feuille des pièces jointes et le menu d'une conversation,
 * « chaque rangée a sa tuile colorée » : bleu pour Photothèque/Appareil photo/Fichiers/Coller,
 * puis bleu, vert, orange, rouge pour Chercher/Renommer/Prendre une note/Processus/Points de
 * restauration. C'était exactement le « trop de couleur » qu'il venait de faire retirer ailleurs.
 *
 * La règle est donc : **une vignette de rangée porte l'encre neutre du thème**, jamais une couleur
 * de famille. C'est la vignette grise de l'anatomie ([OnyxIconTile], `theme.fill`) — la seule que la
 * référence dessine dans une liste. La couleur reste admise sur la **ligne** d'un état réel
 * (destructive), jamais sur la vignette d'un libellé.
 */
fun neutralThumbnailTint(theme: ResolvedTheme): Color =
    if (theme.dark) ReferencePalette.DarkThumb else ReferencePalette.LightSurface2

/** L'encre de la vignette **neutre** : le secondaire du thème, écarté juste assez pour tenir 3:1. */
fun neutralThumbnailInk(theme: ResolvedTheme): Color =
    OnyxContrast.distinctOn(theme.secondaryText, neutralThumbnailTint(theme), OnyxContrast.ICON_MIN)

/**
 * Une vignette de rangée.
 *
 * @param symbol l'icône du jeu normalisé de la maison.
 * @param tint la couleur vive de l'entrée ; `null` prend l'accent du thème.
 * @param neutral la vignette **neutre** : le gris de la famille des tuiles, et l'encre secondaire du
 *   thème. C'est le mode de **toutes** les rangées de feuille et de menu (voir
 *   [neutralThumbnailTint]) ; `tint` est alors ignoré.
 * @param neutralTint la teinte de la vignette **neutre**, quand l'appelant doit l'écarter de son
 *   propre fond (`null` : le gris de la maison tel quel). Une feuille dont le panneau est blanc passe
 *   ici sa teinte mesurée ([HouseSheet.houseSheetTileTint]) — sans quoi la vignette serait blanche
 *   sur blanc. L'encre de l'icône est **mesurée sur cette teinte**, jamais supposée.
 */
@Composable
fun RowThumbnail(
    symbol: Int,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    neutral: Boolean = false,
    contentDescription: String? = null,
    neutralTint: Color? = null,
) {
    val theme = LocalOnyxTheme.current
    if (neutral) {
        // La teinte du mode neutre : celle de la famille, ou celle que l'appelant a mesurée pour son
        // fond. L'encre suit **la teinte réellement posée** — une vignette passée à une autre teinte
        // ne peut pas garder l'encre calculée pour le gris nominal.
        val ground = neutralTint ?: neutralThumbnailTint(theme)
        Box(
            modifier = modifier
                .size(OnyxSpace.ThumbnailSize)
                .shadow(ThumbnailShadow, RoundedCornerShape(OnyxSpace.ThumbnailCorner), clip = false)
                .clip(RoundedCornerShape(OnyxSpace.ThumbnailCorner))
                .background(ground),
            contentAlignment = Alignment.Center,
        ) {
            OnyxIcon(
                symbol = symbol,
                tint = OnyxContrast.distinctOn(theme.secondaryText, ground, OnyxContrast.ICON_MIN),
                size = ThumbnailIconSize,
                contentDescription = contentDescription,
            )
        }
        return
    }
    val colour = tint ?: theme.accent
    Box(
        modifier = modifier
            .size(OnyxSpace.ThumbnailSize)
            .shadow(ThumbnailShadow, RoundedCornerShape(OnyxSpace.ThumbnailCorner), clip = false)
            .clip(RoundedCornerShape(OnyxSpace.ThumbnailCorner))
            .background(thumbnailGradient(colour, theme)),
        contentAlignment = Alignment.Center,
    ) {
        OnyxIcon(
            symbol = symbol,
            tint = thumbnailInk(colour, theme),
            size = ThumbnailIconSize,
            contentDescription = contentDescription,
        )
    }
}
