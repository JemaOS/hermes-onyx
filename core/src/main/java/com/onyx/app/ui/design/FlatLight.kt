package com.onyx.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ReferencePalette
import com.onyx.app.ui.theme.ResolvedTheme

/**
 * **Les surfaces plates du volet en apparence claire.**
 *
 * Le verre n'est pas une fin : c'est une réponse à la question « qu'est-ce qui passe derrière ? ».
 * Quand rien ne passe derrière — une liste posée sur son propre panneau, des tuiles dans ce
 * panneau —, la vitre n'apporte plus de profondeur, elle n'apporte que du **bruit** : une teinte
 * qui voile, un liseré de 0,75 dp à 40 %, un filet interne à 16 %, une lumière de tête et une ombre
 * de pied. Sur une page blanche, ces quatre pièces se lisent comme une surface **sale**, pas comme
 * une surface propre.
 *
 * D'où cette famille, et elle est **réservée au clair** : en sombre, la vitre reste exactement ce
 * qu'elle était (la page est presque noire, la vitre s'en détache vers l'ombre, et le grain du verre
 * y fait le travail). Le mode clair du volet prend donc les surfaces **plates** :
 *
 * 1. le **panneau** — quasi blanc, **sans bordure**, détaché par une **ombre douce** et rien d'autre ;
 * 2. les **creux** ([flatWellColor]) — la tuile, le disque d'une rangée, la piste du sélecteur, la
 *    puce au repos : **un seul gris**, obtenu du `fill` du système **remonté juste assez** pour se
 *    détacher du panneau ([FlatDetachmentMin]) ;
 * 3. la **pastille pleine** ([flatActivePillColor]) — la puce de filtre choisie, pleine et foncée,
 *    son libellé en blanc : c'est l'encre du texte qui porte, mesurée comme partout ailleurs.
 *
 * **Ce que ces valeurs ne font pas.** Elles ne sont ni choisies à l'œil ni recopiées d'une capture :
 * chaque teinte sort d'une **mesure** — la luminance relative WCAG de [OnyxContrast] — et le seuil
 * est celui de la maison, 1,20:1 pour un détachement de surface (le même que [GlassDetachmentMin]).
 * Un test (`FlatLightTest`) vérifie les trois familles sur les sept thèmes et les deux apparences.
 *
 * **La règle de la maison qui reste vraie.** « Le voile d'une surface ne dépasse jamais la page vers
 * le blanc » ([veilIsNeverLighterThanPage]) est la règle du **verre** : un voile plus clair que la
 * page peint un bandeau gris sale sous un liseré blanc. Un panneau **plat** ne peint pas de voile —
 * il est **opaque**, et c'est lui qui porte l'encre : il peut donc monter au-dessus de la page,
 * puisque c'est précisément ce qui le détache quand la page est déjà claire (le cas de « Messages »
 * et de « Paper » en clair, où la page est blanche). Sa détache de la page par la seule **ombre**
 * quand les deux valent blanc — mesuré, et c'est pour ça que l'ombre est un jeton, pas un réglage.
 */

// ---------------------------------------------------------------------------------------------
// Les bornes — la spec, à un seul endroit, donc vérifiable seule
// ---------------------------------------------------------------------------------------------

/**
 * Le **détachement minimal** d'une surface plate de son fond : **1,10:1**.
 *
 * La maison tenait 1,20:1 (la valeur de [GlassDetachmentMin]). Ce seuil était **au-dessus de la
 * référence** : elle sépare ses surfaces d'un seul cran, `#F7F7F9` → `#FFFFFF` en clair, soit
 * **1,03:1**, et son creux (`#F1F1F5`) n'en est qu'à **1,13:1** du blanc. À 1,20, la fonction de
 * détachement **remontait** ce creux d'un cran de plus, et la tuile se lisait comme un **bloc gris**
 * sur un panneau blanc — exactement le défaut relevé à l'œil. Le seuil suit donc la référence :
 * une tuile qui tient déjà son cran est rendue **telle quelle** (cf. [flatContrastingTint]).
 *
 * Les deux familles ne mesurent d'ailleurs pas la même chose : une **vitre** voile un fond, donc
 * elle doit s'en détacher de 1,20 pour se lire ; une **plaque opaque** suit la marche de la
 * référence, qui est plus fine. Le couplage avec [GlassDetachmentMin] n'était qu'une commodité.
 */
const val FlatDetachmentMin: Float = 1.10f

/**
 * De combien le panneau monte vers le blanc, depuis la page.
 *
 * 0,55 est le réglage retenu : sur « Ardoise » clair (page `#DADFE7`) le panneau sort à `#F1F3F7`,
 * soit 1,16:1 de la page — assez pour se lire comme une surface, pas assez pour virer au blanc pur
 * qui ferait un trou clair au milieu de l'écran. Sur les thèmes dont la page est **déjà** blanche
 * (« Messages », « Paper », « Hermes » clair), la valeur ne change rien : la détachement vient alors
 * de l'ombre seule, et c'est exactement ce que la référence fait.
 */
const val FlatPanelLift: Float = 0.55f

/**
 * L'ombre du panneau, en clair : **la seule** chose qui le détache quand la page est blanche.
 *
 * Elle est plus large que les ombres du verre (2 à 14 dp) parce qu'elle porte **seule** : il n'y a
 * ni liseré, ni filet interne, ni lumière de tête pour aider. 12 dp est l'élévation à laquelle un
 * panneau blanc sur une page blanche montre encore un bord lisible sans se lire comme une carte qui
 * flotte.
 */
val FlatPanelElevation: Dp = 12.dp

/**
 * La part d'accent dans l'encre d'une icône de tuile.
 *
 * **0,12, et pas 0,55 :** la tuile portait une icône « neutre-bleutée », teinte plus qu'à moitié de
 * l'accent. Cinquante tuiles d'un volet, cela fait **cinquante accents** à l'écran — et c'est
 * exactement le « trop de couleur » relevé. L'icône d'une tuile est maintenant une **encre neutre**
 * avec un souffle d'accent (12 %), juste assez pour qu'elle reste de la maison sans se lire comme
 * une marque. L'accent plein est réservé à ce qui **agit** (la puce choisie, l'onglet actif).
 */
const val FlatIconBlue: Float = 0.12f

/** La hauteur d'une puce (filtre, projet) : **constante**, la même pour toutes. */
val FlatChipHeight: Dp = 32.dp

/**
 * L'épaisseur de la bordure fine d'un bouton discret (« Try again » de la ligne d'état).
 *
 * **Le filet de la maison** ([OnyxSpace.Stroke], 1 dp) et non plus 0,5 dp : l'app n'a qu'une
 * épaisseur de filet. Un demi-point se lisait au ras de l'antialiasing sur les écrans denses, et
 * il donnait au bouton discret une bordure d'une autre famille que celle des autres surfaces.
 */
val FlatHairlineDp: Dp = OnyxSpace.Stroke

/**
 * L'ombre de la pastille d'un sélecteur plat : **aucune**, et c'est la règle de l'ombre unique.
 *
 * La pastille d'un contrôle segmenté vit dans une **piste** (un creux) : elle est *posée*, pas
 * *flottante*. Une piste n'a pas de profondeur à donner — la pastille se lit à sa couleur et à son
 * filet. Lui laisser une ombre de 2 dp créait une **deuxième** portée d'ombre dans l'app, pour un
 * objet qui n'a aucune raison de se détacher du contenu : l'unique ombre ([OnyxShadow.Float]) est
 * réservée à ce qui **flotte au-dessus du contenu** (compositeur, barres, feuille, menu, vignette).
 */
val FlatSegmentPillElevation: Dp = 0.dp

// ---------------------------------------------------------------------------------------------
// Les teintes — trois fonctions, et elles portent tout
// ---------------------------------------------------------------------------------------------

/**
 * **Le panneau du volet** : quasi blanc au clair, la page en sombre (le verre s'en charge).
 *
 * Une seule fonction pour les deux appelants — la coquille du volet et le contenu du volet —, sans
 * quoi les deux surfaces se mettraient à diverger d'un cran, et c'est exactement ce qui se voit.
 */
fun sidebarPaneColor(theme: ResolvedTheme): Color =
    if (theme.dark) theme.pageColor else lerp(theme.pageColor, Color.White, FlatPanelLift)

/**
 * L'élévation du panneau : **nulle en sombre**, douce en clair.
 *
 * En sombre le verre porte déjà son ombre ([GlassSurface.shadowDp]) ; en ajouter une seconde
 * l'épaissirait au lieu de l'adoucir.
 */
fun sidebarPaneElevation(theme: ResolvedTheme): Dp = if (theme.dark) 0.dp else FlatPanelElevation

/**
 * Une teinte **détachée de son fond** juste assez pour tenir [minRatio] : le gris du système
 * remonté vers le noir (ou le blanc, si le fond est sombre) du plus petit pas qui suffit.
 *
 * Le balayage est déterministe et jamais plus loin que nécessaire : une teinte qui tient déjà le
 * seuil est rendue **telle quelle**, et le résultat ne dépend pas du chemin — donc il se teste.
 */
fun flatContrastingTint(
    tint: Color,
    ground: Color,
    minRatio: Float = FlatDetachmentMin,
): Color {
    if (OnyxContrast.ratio(tint, ground) >= minRatio) return tint
    val towards = if (OnyxContrast.luminance(ground) > 0.5f) Color.Black else Color.White
    var step = FlatTintStep
    while (step <= 1f) {
        val candidate = lerp(tint, towards, step)
        if (OnyxContrast.ratio(candidate, ground) >= minRatio) return candidate
        step += FlatTintStep
    }
    return lerp(tint, towards, 1f)
}

/** Le pas du balayage de [flatContrastingTint] : 1 % — plus fin que ce que l'œil distingue. */
const val FlatTintStep: Float = 0.01f

/**
 * **Le gris des creux** : la tuile, le disque d'une rangée, la piste du sélecteur et la puce au
 * repos prennent **cette** teinte, et pas une par composant.
 *
 * Elle part du `fill` du système — le gris des contrôles, celui que la personne voit partout sur son
 * téléphone —, composé sur le panneau puis remonté au seuil de détachement. Un `fill` seul donne
 * 1,17:1 sur un panneau blanc (mesuré) : sous le seuil, donc la fonction le pousse du cran qui
 * manque. C'est la différence entre « les surfaces se ressemblent » et « les surfaces sont une ».
 */
fun flatWellColor(theme: ResolvedTheme, ground: Color = sidebarPaneColor(theme)): Color =
    flatContrastingTint(OnyxContrast.over(theme.fill, ground), ground, FlatDetachmentMin)

/**
 * Le **détachement d'une tuile** de son panneau : **1,18:1**, plus haut que celui d'un creux
 * ordinaire ([FlatDetachmentMin], 1,10:1).
 *
 * **Défaut relevé sur la capture du propriétaire** : « les tuiles du volet sont des rectangles gris
 * sans bordure sur une page du même ordre de teinte — la séparation est trop faible ». Une tuile
 * n'est pas un creux : elle porte un pictogramme et un mot, elle est répétée sur plusieurs colonnes,
 * et c'est la **grille** qui doit se lire comme une grille. Un cran plus haut que le seuil des
 * creux, plus une **bordure de 0,5 dp** ([FlatHairlineDp]) sur son pourtour : le détachement par la
 * teinte seule demandait 1,30:1 pour être vu à l'œil nu (mesuré), ce qui aurait fait des tuiles
 * grises sur un panneau blanc — le défaut d'origine.
 */
const val FlatTileMin: Float = 1.18f

/**
 * La teinte d'une **tuile** : le gris des creux, remonté au seuil des tuiles ([FlatTileMin]).
 */
fun flatTileColor(theme: ResolvedTheme, ground: Color = sidebarPaneColor(theme)): Color =
    flatContrastingTint(OnyxContrast.over(theme.fill, ground), ground, FlatTileMin)

/**
 * Le liseré d'une tuile : le **séparateur renforcé** du thème, pas une bordure de carte. Une tuile
 * qui n'a que sa teinte se perd sur un panneau du même ordre ; un filet d'un demi-point lui donne
 * son arête sans dessiner de cadre.
 */
fun flatTileHairline(theme: ResolvedTheme): Color =
    // **Au jeton des 12 %.** La référence porte une ligne « forte » à 14 % (`LightLineStrong`) ; le lot
    // des contours a plafonné TOUT contour sombre au jeton de la planche (12 %, [OnyxEdge.MaxDarkAlpha]).
    // Le liseré de tuile garde son intention — un filet d'un demi-point qui détache la plaque — mais à
    // la valeur de la maison : au-dessus, c'était le défaut « des bords gris très prononcés ».
    OnyxEdge.fil(theme)

/**
 * **La pastille pleine** : la puce de filtre choisie, en **accent** — le même bleu que le bouton
 * principal de l'écran, son mot dans l'encre mesurée.
 *
 * **Correction d'un défaut relevé sur les captures du propriétaire** : la puce choisie (« Tout » du
 * volet) était une **pastille noire** ([OnyxContrast.filled] sur l'**encre du texte**), alors que le
 * bouton principal de l'écran est bleu. Deux couleurs d'action pour un seul écran, et la plus forte
 * des deux pour le contrôle le moins important. La puce choisie prend donc l'**accent** — elle dit
 * un **état de sélection**, et c'est la seule famille d'accent que la maison s'autorise en plus de
 * l'action principale (même règle pour l'onglet actif et la coche d'une liste). La couleur passe par
 * [OnyxContrast.filled] : si l'accent ne portait pas son mot à 4,5:1, c'est le **fond** qui bouge.
 */
fun flatActivePillColor(theme: ResolvedTheme): Color = OnyxContrast.filled(theme.accent)

/** L'encre d'une puce pleine : celle qui se lit le mieux dessus — blanc sur un fond foncé. */
fun flatActivePillInk(theme: ResolvedTheme): Color = OnyxContrast.inkOn(flatActivePillColor(theme))

/**
 * **L'encre d'une icône de tuile** : la teinte du texte tirée vers l'accent ([FlatIconBlue]), puis
 * **ramenée au seuil des icônes** sur [ground] — par le bas comme par le haut.
 *
 * Les deux sens comptent, et le second a coûté une mesure : sur le thème « Code » clair, l'accent est
 * un bleu de ciel pâle (`#CBF0FF`) ; mélangé à l'encre du texte, il donne une icône à **2,89:1** sur
 * le gris de la tuile — sous le seuil, donc invisible pour qui a besoin de ce seuil. Une fonction
 * qui ne saurait qu'**atténuer** ne peut rien pour elle (`dimmed` rend l'encre telle quelle quand
 * elle est déjà trop faible : il n'y a rien à retirer). Celle-ci **éloigne** d'abord l'encre de son
 * fond du plus petit pas qui suffit, puis l'atténue jusqu'au seuil : le bleu reste bleu, et il se
 * lit.
 *
 * La marge ([FlatInkMargin]) est celle de la maison : un trait d'icône est antialiasé, donc le pixel
 * de cœur atteint la couleur et son voisin non — une encre calculée pour tomber *exactement* sur
 * 3,00:1 livrerait une icône mesurée au ras du seuil.
 */
fun flatIconInk(theme: ResolvedTheme, ground: Color): Color {
    val base = OnyxContrast.over(lerp(theme.textColor, theme.accent, FlatIconBlue), ground)
    val target = OnyxContrast.ICON_MIN + FlatInkMargin
    return if (OnyxContrast.ratio(base, ground) >= target) {
        OnyxContrast.dimmed(base, ground, target)
    } else {
        OnyxContrast.distinctOn(base, ground, target)
    }
}

/** La marge d'une encre d'icône au-dessus du seuil : le trait est antialiasé (cf. `BarInk`). */
const val FlatInkMargin: Float = 0.5f

/** La piste d'un sélecteur : le gris des creux, sur la page. */
fun flatTrackColor(theme: ResolvedTheme): Color =
    flatContrastingTint(OnyxContrast.over(theme.fill, theme.pageColor), theme.pageColor, FlatDetachmentMin)

/** La pastille de l'option retenue : blanche, et c'est tout — c'est la piste qui la fait lire. */
fun flatTrackPillColor(theme: ResolvedTheme): Color = if (theme.dark) Color.White.copy(alpha = 0.22f) else Color.White

// ---------------------------------------------------------------------------------------------
// La pose — un modificateur, une forme, une ombre
// ---------------------------------------------------------------------------------------------

/**
 * Une **plaque plate** : une couleur, une forme, une ombre douce, et **aucune bordure**.
 *
 * C'est tout ce qu'une surface plate est au clair. Le `clip` vient **après** l'ombre pour que
 * l'ombre d'un enfant ne soit pas rognée par la forme (la faute classique, et celle qui donne un
 * panneau plat sans détachement sur une page blanche).
 */
fun Modifier.flatPlate(
    colour: Color,
    shape: Shape,
    elevation: Dp = 0.dp,
): Modifier = this
    .then(if (elevation > 0.dp) Modifier.shadow(elevation, shape, clip = false) else Modifier)
    .clip(shape)
    .background(colour)

/**
 * **La surface du volet** : le **grand verre de matière**, dans **les deux apparences**.
 *
 * Le lot « volet clair propre » avait retiré la vitre au clair — et c'était mesuré : sur un panneau
 * neuf, quatre pièces de verre (teinte, liseré, filet interne, lumière et ombre) se lisaient comme
 * une surface **sale**. Ce lot-ci rouvre la question pour une raison précise, et la réponse change
 * parce que **le fond a changé** : la page porte maintenant une **matière** (les zones froides et
 * chaudes de [Matter]), le volet est un **grand panneau** qui la recouvre, et une grande vitre a
 * enfin quelque chose à montrer — la matière se teinte au travers. C'est la demande explicite du
 * propriétaire (« une grande carte de verre teintée par ce qu'il y a derrière, l'œil les voit tous
 * les jours »), et `SIDEBAR_PANEL` est déclarée [GlassSampling.IN_SCREEN] : en surimpression, du
 * contenu passe dessous, donc la vitre échantillonne **vraiment**.
 *
 * Un seul point de décision, donc le mode sombre du volet ne peut pas se dégrader en ré-habillant le
 * mode clair — le verre est appelé **exactement** comme avant, avec la même table ([GlassSurface]).
 */
@Composable
fun Modifier.sidebarPaneSurface(): Modifier = this.liquidGlass(GlassSurface.SIDEBAR_PANEL)

/**
 * La surface d'une **tuile** (volet) ou d'une **carte de bot** : la vitre de la table en sombre, le
 * **creux détaché et bordé** au clair.
 *
 * Au clair, la tuile garde sa plaque — la décision du lot « volet clair propre » est conservée, une
 * tuile n'a rien à échantillonner dans un volet posé à côté du détail. Mais elle prend le **seuil des
 * tuiles** ([flatTileColor], 1,18:1 au lieu de 1,10) et un **liseré d'un demi-point** : c'est la
 * correction du défaut relevé sur la capture du propriétaire, « des rectangles gris sans bordure sur
 * une page du même ordre de teinte ».
 */
@Composable
fun Modifier.sidebarTileSurface(surface: GlassSurface, shape: Shape): Modifier {
    val theme = LocalOnyxTheme.current
    return if (theme.dark) {
        this.liquidGlass(surface, shape)
    } else {
        this
            .flatPlate(flatTileColor(theme), shape)
            .border(FlatHairlineDp, flatTileHairline(theme), shape)
    }
}

/**
 * La surface d'une **rangée de liste** : la vitre de la table en sombre, **rien** au clair.
 *
 * Au clair, une rangée de liste n'est pas une surface du tout : elle est du texte et un disque sur le
 * panneau. C'est la référence — et c'est ce qui laisse la liste respirer sans séparateur ni cadre.
 */
@Composable
fun Modifier.sidebarRowSurface(shape: Shape): Modifier {
    val theme = LocalOnyxTheme.current
    return if (theme.dark) this.liquidGlass(GlassSurface.LIST_ROW, shape) else this
}
