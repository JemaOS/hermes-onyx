package com.onyx.app.ui.design

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ReferencePalette
import com.onyx.app.ui.theme.ResolvedTheme
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurDefaults
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.RefractionProfile
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource

/**
 * Le **verre liquide premium** : une vitre qui laisse voir, **flouté pour de vrai**, le contenu qui
 * défile dessous.
 *
 * Pourquoi un fichier à côté de [Frost] : `Frost` portait la première version — un calque du contenu
 * flouté par `BlurEffect` posé par l'app elle-même, avec un voile de page à 86–88 % d'opacité. Le
 * voile était si épais que le flou ne se voyait presque pas, et son liseré ne se lisait que sur un
 * bord net. Le verre qu'on veut ici est plus mince (le contenu derrière se voit), plus flou (36 à
 * 50 dp au lieu de 12 à 24) et granuleux (un bruit qui casse les bandes de dégradé du flou). Le
 * flou d'arrière-plan **réel** est donc confié à `dev.chrisbanes.haze` : c'est lui qui échantillonne
 * la source, la floute, la teinte et la garnit de bruit, dans un seul passage GPU. `Frost` reste en
 * place, inchangé, pour les surfaces qui ne recouvrent rien (voir « ce qui est écarté » plus bas).
 *
 * **La règle de pose.** Une vitre n'a de sens que si **du contenu passe dessous**. Haze est donc
 * branchée de la seule façon qui marche : `HazeState` sur le **parent qui défile**
 * ([onyxHazeSource], posé par l'écran, qui compte en plus la source pour la vitre), et le verre sur
 * le **composant** ([hazeBlur] ou [hazeGlass], posés ici). Deux surfaces plates l'une sur l'autre ne
 * produisent rien de visible : c'est pour ça que l'écran de conversation fait passer le fil **sous**
 * la barre du haut et **sous** la pile du bas, et que les surfaces posées sur un aplat gardent
 * `frostedBar` (la liste des surfaces écartées est dans le rapport du lot).
 *
 * **Ce que le verre garantit.**
 * - La **réfraction** : à partir d'Android 13 le contenu échantillonné est **déplacé** selon le
 *   profil d'arête ([GlassTier.REFRACTION]) — mais d'un déplacement **sub-pixel et uniforme**
 *   ([GlassRefractionDisplacementDp] : quelques dixièmes de pixel). C'est la cible de la référence : le fond
 *   est **flouté** (illisible dans le verre), **teinté**, **légèrement saturé**, et l'arête se marque
 *   d'un **liseré clair** — jamais d'une lentille. Le déplacement qui « courbait ce qui passe
 *   dessous » (30 dp de flou → 27 dp de déplacement) est ce que le propriétaire a refusé : il
 *   gauchissait les arêtes et dupliquait l'étiquette voisine dans la tuile.
 * - Le flou est **paramétrable** et borné : 36 à 50 dp ([GlassBlurMinDp], [GlassBlurMaxDp]), 36 par
 *   défaut.
 * - La teinte est celle du thème : blanc 20 % en clair, anthracite 30 % en sombre ([GlassTintLight],
 *   [GlassTintDark]) — mais **remontée** si la mesure l'exige : l'encre de la surface ne descend
 *   jamais sous son seuil, même avec un aplat blanc ou noir derrière ([glassTintAlpha]).
 * - Le bruit ([GlassNoiseFactor], 0,1) casse le **banding** du flou, qui sinon se lit en anneaux sur
 *   un dégradé sombre.
 * - Le **reflet de tranche** : un liseré de 0,75 dp blanc à 40 %, tracé **suivant la forme** de la
 *   surface (jamais un rectangle à coins carrés sous un clip arrondi — la faute corrigée par le lot
 *   précédent, gardée ici).
 * - Le **repli** : si Haze ne peut pas échantillonner (pas de source, appareil qui refuse le flou),
 *   la vitre est remplacée par un voile opaque **plus opaque** que le verre, jamais par un trou.
 * - Les **paliers** ([GlassTier]) : le verre complet là où la plateforme sait déplacer le contenu,
 *   le flou seul là où elle refuse le déplacement, le voile translucide partout ailleurs. Aucun des
 *   trois ne plante, et aucun n'est décidé au hasard de la version : [glassTier] est une fonction
 *   pure, éprouvée par les tests.
 * - Le **mode allégé** se déclenche sur une **mesure de fluidité** ([GlassFluidity]), pas au hasard.
 *
 * **« Réduire les animations » : le verre reste, la lumière se fige.** C'est la différence assumée
 * avec [FrostLevel] : le verre n'est ni un mouvement ni une décoration animée, il n'y a donc rien à
 * figer — la surface garde sa réfraction et son flou, et le changement de qualité est **instantané**
 * au lieu d'être fondu. Ce qui se fige, c'est le reflet piloté par le doigt
 * ([GlassReducedMotionPolicy.Reduced] quand le réglage est posé) : le verre cesse de réagir.
 */

// ---------------------------------------------------------------------------------------------
// Les bornes — la spec, à un seul endroit, donc vérifiable seule
// ---------------------------------------------------------------------------------------------

/**
 * Le flou le plus faible du verre pleine qualité.
 *
 * **Relevé de 30 à 36 dp.** Sur une tuile de 44 dp, 30 dp de flou ne suffisaient pas à rendre le
 * fond illisible : la réfraction réinjectait le détail (voir [GlassRefractionDetailIntensity]).
 * La réfraction remise à sa place, le flou devient la **seule** image du fond — et 36 dp le
 * noient franchement, même quand l'étiquette voisine est du texte noir sur blanc.
 */
const val GlassBlurMinDp: Float = 36f

/** Le flou le plus fort du verre pleine qualité. */
const val GlassBlurMaxDp: Float = 50f

/** Le flou par défaut d'une surface qui ne dit rien d'autre. */
const val GlassBlurDefaultDp: Float = 36f

/**
 * Le facteur de bruit, 0 à 1. 0,1 est le réglage retenu : Haze en pose 0,15 par défaut, ce qui se
 * voit comme un grain sur une grande surface ; 0,1 casse les anneaux du flou sans salir le texte
 * posé dessus (le grain est dans la **vitre**, sous l'encre, jamais dans l'encre).
 */
const val GlassNoiseFactor: Float = 0.1f

/** La teinte du verre en apparence claire : blanc 20 %. */
const val GlassTintLight: Float = 0.20f

/** La teinte du verre en apparence sombre : anthracite 30 %. */
const val GlassTintDark: Float = 0.30f

/**
 * L'anthracite de la vitre sombre : le **troisième barreau** de la référence (`Palette.Surface3`),
 * un graphite neutre. La maison portait `#23262B`, qui tirait au bleu (8 points entre le rouge et
 * le bleu) : une vitre ne doit pas teinter ce qu'elle laisse voir.
 */
val GlassAnthracite: Color = ReferencePalette.DarkSurface3

// ---------------------------------------------------------------------------------------------
// La profondeur — ce qui fait qu'une vitre se voit
// ---------------------------------------------------------------------------------------------

/**
 * **Le défaut que ces quatre constantes réparent.** Mesurée sur la capture du propriétaire, la
 * vitre sombre valait 61, 63, 66 — soit 1,99:1 avec la page, donc elle se **détachait** déjà. Ce
 * qui manquait n'était pas l'écart avec la page, c'était l'écart **à l'intérieur** de la vitre :
 * une seule valeur d'un bord à l'autre, un liseré de 0,75 dp à 40 %, et rien d'autre. Un aplat
 * uniforme, même détaché, se lit comme du plastique ; une vitre se lit comme une vitre parce
 * qu'elle est **éclairée en haut, ombrée en bas, et bordée d'un filet clair des deux côtés**.
 *
 * La profondeur est donc faite de quatre pièces, toutes peintes **derrière** le contenu (l'encre
 * reste nette) :
 *  1. [GlassSheenStop] — la **lumière de tête** : un dégradé qui part du haut et s'éteint vite ;
 *  2. [GlassShadeStop] — l'**ombre de pied** : le pendant exact, en bas ;
 *  3. [GlassHairlineDp] — le **liseré interne**, à une marche et demie du bord : c'est lui qui
 *     dessine l'épaisseur du verre, et il se voit même quand la vitre et la page ont la même
 *     couleur (le cas du thème clair) ;
 *  4. le **reflet de tranche** ([GlassEdgeDp], [GlassEdgeAlpha]), déjà en place.
 *
 * Les quatre sont mesurées par `GlassLuminanceTest`, qui publie l'écart vitre/fond **et** l'écart
 * haut/bas de chaque surface : c'est ce second chiffre qui prouve la profondeur, et il ne peut pas
 * être inventé.
 */

/** Où la lumière de tête s'éteint, en fraction de la hauteur de la surface. */
const val GlassSheenStop: Float = 0.34f

/** Son opacité au bord haut, en sombre — assez pour se lire sur du noir, pas assez pour blanchir. */
const val GlassSheenDark: Float = 0.16f

/** Idem en clair : sur une page blanche un blanc de plus ne se voit pas, on garde donc peu. */
const val GlassSheenLight: Float = 0.26f

/** Où l'ombre de pied commence, en fraction de la hauteur. */
const val GlassShadeStart: Float = 0.66f

/** Son opacité au bord bas, en sombre : c'est elle qui creuse la vitre. */
const val GlassShadeDark: Float = 0.22f

/**
 * Idem en clair : **peu, mais pas rien** — une vitre claire ne se creuse pas, elle se pose.
 *
 * Elle a valu 9 % ; le liseré interne du verre est passé au plafond du trait (12 %, [OnyxEdge]),
 * et c'est cette **ombre de pied** qui porte désormais le détachement de la vitre sur une page
 * claire gris-bleu — mesuré : 1,28:1 sur l'ardoise (`#DADFE7`), au-dessus du seuil de 1,20:1
 * ([GlassDetachmentMin]). La planche fait de même : son arête basse est un filet sombre
 * (`rgba(22,26,34,.12)`, `v4css.py:142`), c'est la profondeur de la vitre, pas un encadrement.
 */
const val GlassShadeLight: Float = 0.16f

/** L'épaisseur du liseré interne. */
val GlassHairlineDp: Dp = 1.dp

/** Le retrait du liseré interne par rapport au bord : l'épaisseur du verre. */
val GlassHairlineInset: Dp = 1.5.dp

/** Son opacité en sombre : le filet clair qui dessine l'arête intérieure. */
const val GlassHairlineDark: Float = 0.10f

/**
 * Son opacité en clair — et ici le filet est **sombre**, pas clair.
 *
 * La règle de la maison en clair : la vitre ne dépasse jamais la page vers le blanc (un voile plus
 * clair que la page peint un bandeau gris sale, c'est mesuré et documenté dans `Frost.kt`). Sur une
 * page blanche, la face de la vitre **est** donc la page, à 1,00:1 — et une vitre qui ne se détache
 * pas, ce n'est pas du verre. Ce qui la détache, c'est le couple **lumière de tête + liseré
 * sombre** : deux directions opposées, donc au moins une des deux se voit quelle que soit la page.
 *
 * **12 %, et pas 16 :** le trait de la planche est un plafond ([OnyxEdge.MaxDarkAlpha]) et la vitre
 * le suit — le liseré interne reste un filet, jamais un cadre. Le seuil de perceptibilité du lot
 * (1,20:1, [GlassDetachmentMin]) n'est pas abandonné pour autant : la lumière de tête et l'ombre de
 * pied portent la détachement, et c'est leur maximum qui est mesuré ([glassDetachment]).
 */
const val GlassHairlineLight: Float = 0.12f


/** Le liseré : la borne basse de la spec. */
const val GlassEdgeMinDp: Float = 0.5f

/** Le liseré : la borne haute de la spec. */
const val GlassEdgeMaxDp: Float = 1f

/**
 * L'épaisseur retenue du reflet de tranche, 0,75 dp — **justifié** : 0,5 dp disparaît sous
 * l'antialiasing dès 2× de densité (1 px écrasé sur deux lignes), 1 dp se lit comme le trait d'une
 * carte Material, cette app n'en veut pas. 0,75 dp donne 2 px pleins à 2,75× — une arête, pas un
 * cadre.
 */
const val GlassEdgeDp: Float = 0.75f

/** L'opacité du liseré : le **haut** de la fourchette demandée (30–40 %). */
const val GlassEdgeAlpha: Float = 0.40f

/**
 * **Le liseré des grandes barres familiales** (compositeur, barre d'onglets, barre du haut).
 *
 * Le défaut relevé sur la capture du propriétaire : « le compositeur porte un liseré blanc épais en
 * double — une bague autour de la carte, et la carte ». Il n'y en avait pas deux par erreur : la
 * **finition** du verre en dessine deux, et c'est voulu pour une petite pastille (le reflet de
 * tranche *et* le filet interne, qui donnent l'épaisseur du verre). Sur une **grande** surface, les
 * deux filets se lisent à la même distance de l'arête et font un **cerceau** de 2 à 3 dp. Les grandes
 * barres gardent donc le filet interne et **rétrécissent le reflet** : 0,5 dp à 26 %, à la place de
 * 0,75 dp à 40 %. Une arête, pas une bague — et la profondeur (lumière de tête, ombre de pied, filet
 * interne) reste entière, donc [glassDepth] ne bouge pas.
 */
const val GlassEdgeThinDp: Float = 0.5f

/** L'opacité du reflet rétréci des grandes barres. */
const val GlassEdgeThinAlpha: Float = 0.26f

/** Au-dessus de ce flou, une surface est une **grande barre** : son reflet de tranche est rétréci. */
const val GlassBarBlurMinDp: Float = 36f

/**
 * **Le flou de la plaque du bas** (compositeur + rangée d'onglets), relevé de 36 à **44 dp**.
 *
 * Le propriétaire, sur capture : « il manque du liquid glass, c'est pas assez prononcé ». Une partie
 * du verre perdu venait du voile (voir [glassMaskingFloorBlurred] : la plaque masquait le fil *comme
 * si elle n'était qu'un voile*, donc son opacité était montée au plancher de masquage, et rien ne se
 * voyait plus au travers). L'autre partie vient du **flou lui-même** : plus il est large, plus ce qui
 * passe derrière est noyé — c'est-à-dire plus la plaque se lit comme une vitre plutôt que comme un
 * aplat. 44 dp reste dans la fourchette de la maison (36–50, [GlassBlurMinDp]/[GlassBlurMaxDp]) et
 * laisse 6 dp de marge sous le plafond.
 *
 * La barre du haut, elle, **ne bouge pas** : 36 dp ([GlassBlurDefaultDp]) — c'est le défaut déclaré
 * de la famille, et il est verrouillé (`LiquidGlassTest`).
 */
const val GlassBarBlurPlateDp: Float = 44f

// ---------------------------------------------------------------------------------------------
// L'arête de verre des plaques — le jeton de la planche, peint pour la première fois
// ---------------------------------------------------------------------------------------------

/**
 * **L'arête claire du haut d'une plaque de verre** — `--gedge` / `--gloss` de la planche :
 * blanc **98 %** en clair, **62 %** en sombre (`v4css.py:142` / `:154`), **1,5 px** d'épaisseur
 * (`hcss.py:98-102`, la valeur que [PlaqueGeometry.TopEdge] porte déjà).
 *
 * **Pourquoi elle arrivait nulle part.** Le jeton existait deux fois dans la maison
 * ([OnyxEdge.TopEdgeLightAlpha], [PlaqueGeometry.TopEdgeAlphaLight]) et une fonction le rendait
 * ([OnyxEdge.topEdge]) — **aucun écran ne l'appelait**. Les trois surfaces flottantes (l'en-tête, la
 * plaque du compositeur et des onglets, la carte du compositeur) étaient donc peintes **sans leur
 * arête** : c'est exactement le reproche du propriétaire, « il manque du liquid glass, c'est pas
 * assez prononcé ». Une vitre sans arête se lit comme un aplat découpé ; c'est l'arête qui la pose.
 *
 * **Pourquoi une ligne droite et pas un contour.** La profondeur complète du verre
 * ([drawGlassFinish] : lumière de tête, ombre de pied, liseré interne, reflet de tranche) a été
 * **retirée** de ces trois surfaces par le lot de la matière, sur la mesure de la planche
 * (`MatiereAplatVerrouTest` : la planche ne dessine ni halo ni liseré autour du corps de la plaque).
 * Ce que la planche dessine, en revanche, c'est **son arête haute** — et c'est la seule pièce que
 * ces plaques reçoivent ici. Elle se peint donc **sur le bord haut du plat**, rentrée du rayon pour
 * ne pas dépasser sur les coins, et rien d'autre n'est ajouté : le corps reste un aplat, comme la
 * planche le veut.
 */
val GlassPlateEdgeInset: Dp = 20.dp

/** L'arête d'une plaque est-elle peinte ? (la porte que le verrou interroge) */
fun glassPlateEdgeUsed(surface: GlassSurface): Boolean = surface.plateEdge

/** L'épaisseur réellement peinte de l'arête d'une plaque, en dp (0 si la surface n'en porte pas). */
fun glassPlateEdgeDp(surface: GlassSurface): Float =
    if (surface.plateEdge) PlaqueGeometry.TopEdge.value else 0f

/** L'opacité réellement peinte de l'arête d'une plaque (0 si la surface n'en porte pas). */
fun glassPlateEdgeAlpha(surface: GlassSurface, dark: Boolean): Float = when {
    !surface.plateEdge -> 0f
    dark -> OnyxEdge.TopEdgeDarkAlpha
    else -> OnyxEdge.TopEdgeLightAlpha
}

/**
 * **Le masquage du contenu derrière une plaque, mesuré APRÈS le flou.**
 *
 * Le plancher de masquage ([glassMaskingFloor]) compare l'encre du fil et la page **tels quels** —
 * comme si la vitre n'était qu'un voile. C'est le pire cas d'un voile nu, mais ce n'est **pas** le
 * cas d'une plaque : derrière elle, le fil passe dans un flou de 36 à 50 dp, qui **dilue un fût de
 * lettre dans un disque de 95 à 130 px**. Ce qui traverse la vitre n'est donc pas le texte, c'est
 * **la moyenne locale de son voisinage** — et c'est ce que ce plancher mesure.
 *
 * Le modèle est volontairement **conservateur** : un fût de [GlassMaskedStrokePx] px qui traverse un
 * disque de rayon [blurPx] n'occupe qu'une part `f = strokePx / (2 · blurPx)` de ce disque ; l'encre
 * vue au travers est donc `lerp(page, ink, f)`. Un fût de 2 px dans un flou de 36 dp à 2,625×
 * (94,5 px) vaut `f = 0,0106` : le contraste résiduel tombe à **1,01:1**, très en dessous du seuil
 * de masquage (2,5:1, [MaskedContentMaxRatio]). Le plancher rend donc **0**, et le voile de la
 * plaque est celui de son encre ([glassTintAlpha]) — c'est-à-dire une **vitre**, pas un aplat.
 *
 * Ce que la maison ne perd pas : la règle « on ne lit pas le fil au travers » reste tenue, mais elle
 * est tenue **par le flou**, et elle est **mesurée sur l'appareil au pixel** (voir le rapport du lot
 * « verre-plaques » : `mesure-plaques.py`, contraste dans la bande intérieure de la plaque).
 */
const val GlassMaskedStrokePx: Float = 2f

/** La part d'un disque de flou qu'occupe un fût d'encre : 0 à 1. Pure. */
fun glassBlurStrokeCoverage(strokePx: Float, blurPx: Float): Float {
    if (blurPx <= 0f) return 1f
    return (strokePx / (2f * blurPx)).coerceIn(0f, 1f)
}

/**
 * Le plancher de masquage d'une plaque **floutée** : la plus petite opacité de voile qui amène le
 * contraste du contenu **déjà flouté** sous [maxRatio].
 */
fun glassMaskingFloorBlurred(
    tint: Color,
    ink: Color,
    page: Color,
    blurPx: Float,
    strokePx: Float = GlassMaskedStrokePx,
    maxRatio: Float = MaskedContentMaxRatio,
): Float {
    val coverage = glassBlurStrokeCoverage(strokePx, blurPx)
    val blurred = lerp(page, ink, coverage)
    var alpha = 0f
    while (alpha <= 1f) {
        val veil = tint.copy(alpha = alpha)
        val seenInk = compositedBackdrop(veil, blurred)
        val seenPage = compositedBackdrop(veil, page)
        if (OnyxContrast.ratio(seenInk, seenPage) <= maxRatio) return alpha
        alpha += LegibilityStep
    }
    return 1f
}

/**
 * Le voile **peint** d'une plaque qui masque, le flou derrière compris : le plus haut de la teinte
 * de son encre et du plancher flouté ci-dessus. Fonction nommée pour que l'écran et le verrou
 * lisent le même chiffre.
 */
fun glassMaskingTintAlphaBlurred(
    tint: Color,
    nominal: Float,
    ink: Color,
    page: Color,
    blurPx: Float,
): Float = maxOf(nominal, glassMaskingFloorBlurred(tint, ink, page, blurPx))

/**
 * Le **rayon commun de la famille des barres flottantes** : la carte du compositeur, la barre des
 * cinq onglets et la barre du haut. C'est la valeur du lot « composition » (`ComposerCardRadius`) —
 * elle est reprise ici **une seule fois** pour que les trois surfaces ne puissent plus diverger.
 *
 * Une vitre qui n'a pas de forme arrondie se lit comme un rectangle franc : c'est le défaut « les
 * bords ne sont pas arrondis », et il ne se voit pas au même endroit selon la surface. La table
 * [GlassSurface] porte donc le rayon de chacune, et `liquidGlass` s'en sert **par défaut**.
 */
val GlassBarCorner: Dp = 22.dp

/** Le voile de repli ne descend jamais sous ce plancher : une surface légèrement opaque, pas un trou. */
const val GlassScrimMinLight: Float = 0.82f

/** Idem en sombre : la page y est déjà presque noire, la vitre doit s'en détacher. */
const val GlassScrimMinDark: Float = 0.86f

// ---------------------------------------------------------------------------------------------
// La qualité — pleine, allégée, repli
// ---------------------------------------------------------------------------------------------

/**
 * Les trois qualités du verre, de la meilleure à la plus pauvre.
 *
 * L'ordre est celui de la dégradation : [FULL] puis [LITE] puis [SCRIM]. On ne remonte jamais
 * au-dessus du **plafond** de l'appareil, et on ne descend jamais en dessous du mesuré.
 */
enum class GlassLevel(val blurScale: Float, val tintBonus: Float) {
    /** Flou au rayon de la surface (36–50 dp), teinte nominative. */
    FULL(blurScale = 1f, tintBonus = 0f),

    /**
     * **Mode allégé** : la moitié du flou, et la teinte remontée de 12 points. Le verre reste un
     * verre (on le voit à travers), il coûte deux fois moins cher, et la lisibilité **monte** au
     * lieu de descendre — c'est le sens du compromis.
     */
    LITE(blurScale = 0.5f, tintBonus = 0.12f),

    /**
     * **Repli** : pas de flou du tout (Haze dessine alors son voile, celui des plateformes qui
     * refusent le flou). Le voile est **plus opaque** que le verre, et c'est voulu : sans flou, le
     * contraste local du contenu passe entier sous l'encre, alors qu'un flou de 36 dp le lisse.
     */
    SCRIM(blurScale = 0f, tintBonus = 0f),
    ;

    /** Le flou est-il réellement appliqué ? */
    val blurs: Boolean get() = this != SCRIM
}

/**
 * Le **plafond** du verre sur cet appareil, avant toute mesure de fluidité : la version d'Android et
 * la mémoire décident, comme pour [frostLevel].
 *
 * Sous Android 12, Haze ne floute pas : son défaut ne l'active qu'à partir de cette version
 * (`isBlurEnabledByDefault`), et le verre y démarre donc en repli au lieu d'afficher une vitre qui ne
 * se rafraîchit pas — c'est **le même verdict que la plateforme**, lu au même endroit qu'elle plutôt
 * qu'écrit une seconde fois ici. Les appareils à faible mémoire démarrent en mode allégé.
 */
fun glassCeiling(sdkInt: Int, lowRam: Boolean): GlassLevel = when {
    sdkInt < GlassBlurMinSdk -> GlassLevel.SCRIM
    lowRam -> GlassLevel.LITE
    else -> GlassLevel.FULL
}

// ---------------------------------------------------------------------------------------------
// Les paliers du verre — ce que la plateforme sait réellement faire
// ---------------------------------------------------------------------------------------------

/** La version d'Android à partir de laquelle Haze floute réellement. */
const val GlassBlurMinSdk: Int = 31

/** La version d'Android à partir de laquelle la plateforme sait **déplacer** le contenu (AGSL). */
const val GlassRefractionMinSdk: Int = 33

/**
 * **Ce que la vitre peut faire sur cette plateforme.** Un seul chiffre décide, et les trois paliers
 * sont ceux de la demande, dans cet ordre :
 *
 *  - [REFRACTION] — la plateforme expose un processeur d'ombrage à l'exécution (API 33+), et le flou
 *    fonctionne : Haze **déplace** le contenu échantillonné selon le profil d'arête, dessine le
 *    liseré d'arête et disperse les canaux. C'est le verre liquide complet.
 *  - [BLUR] — pas de processeur d'ombrage, mais le flou de la plateforme est accepté : le contenu
 *    est flouté, teinté et grainé, le liseré d'arête est peint **par nous** ([drawGlassFinish]).
 *    Pas de réfraction — le contenu n'est pas déplacé — mais une vitre honnête et bon marché.
 *  - [VEIL] — ni l'un ni l'autre : la vitre est un **voile translucide**, peint par nous, plus
 *    opaque pour que l'encre garde son contraste. Aucun plantage, aucun flou refusé.
 *
 * Le second verdict vient de **Haze lui-même** ([HazeBlurDefaults.isBlurEnabledByDefault]) : c'est
 * lui qui sait où son flou se rafraîchit, et le lire évite d'écrire ici une table de versions qui
 * vieillirait sans nous. La fonction reste **pure** pour être éprouvée seule.
 */
enum class GlassTier {
    /** Réfraction du contenu, liseré d'arête, dispersion : le verre liquide complet. */
    REFRACTION,

    /** Le contenu est flouté et teinté, mais **pas déplacé**. */
    BLUR,

    /** Un voile translucide, et rien d'autre : le repli ultime, jamais un trou. */
    VEIL,
}

/** Le palier d'une plateforme donnée. Pure : deux entiers (ou un verdict) entrent, un palier sort. */
fun glassTier(
    sdkInt: Int,
    blurEnabledByDefault: Boolean = HazeBlurDefaults.isBlurEnabledByDefault(),
): GlassTier = when {
    !blurEnabledByDefault -> GlassTier.VEIL
    sdkInt >= GlassRefractionMinSdk -> GlassTier.REFRACTION
    sdkInt >= GlassBlurMinSdk -> GlassTier.BLUR
    else -> GlassTier.VEIL
}

/**
 * **Le palier forcé — la constante de la preuve, et rien d'autre.**
 *
 * Elle vaut `null` en production : le palier est celui de la plateforme, lu par [glassTier]. Elle
 * n'existe que pour produire la **paire de captures avec et sans réfraction** que le brief exige —
 * la seule façon de montrer, noir sur blanc, ce que la réfraction change sur nos surfaces. On la
 * passe à [GlassTier.VEIL] le temps d'un build de preuve, on capture, on la remet à `null`.
 *
 * Elle est ici, à côté de [glassTier], et pas dans un écran : forcer un palier depuis un appelant
 * ferait diverger ce que l'app fait de ce que la table déclare.
 */
val GlassTierForcedForEvidence: GlassTier? = null

/** Le palier de l'appareil qui exécute l'app — ou celui de la preuve, quand il est posé. */
val glassTierNow: GlassTier get() = GlassTierForcedForEvidence ?: glassTier(Build.VERSION.SDK_INT)

/**
 * La force de la réfraction, 0 à 1.
 *
 * **C'est la constante qui a produit le défaut du propriétaire.** À 0,8, le verre n'était pas une
 * vitre : c'était une **lentille**. la référence ne déplace pas le fond — il le floute et le teinte, et
 * l'arête se lit par un **liseré**, pas par une courbure. La force reste donc un souffle : elle
 * allume l'arête sans courber ce qui passe dessous.
 */
const val GlassRefractionStrength: Float = 0.06f

/** La largeur de la bande où la réfraction s'applique, en dp, depuis le bord. */
const val GlassRefractionEdgeDp: Float = 22f

/**
 * Le déplacement de réfraction, en **dp absolus** — et c'est le cœur de la correction.
 *
 * La règle d'avant le dérivait du flou (`blurDp * 0,9`) : une tuile de 30 dp de flou déplaçait donc
 * le fond de **27 dp** (≈74 px à 2,75×), sur une tuile qui en fait 44 de haut. Un déplacement
 * pareil **gauchit** les arêtes du fond et **duplique** ce qui passe à côté (l'étiquette
 * « RACCOURCIS », juste au-dessus de la grille, se retrouvait lue **dans** la tuile). la référence
 * déplace de **quelques dixièmes de pixel**, uniformément : 0,2 dp vaut 0,55 px à 2,75× — à la
 * limite du perceptible, jamais une lentille, jamais une seconde copie.
 */
const val GlassRefractionDisplacementDp: Float = 0.2f

/**
 * **Conservée pour ne pas casser un appelant voisin** : le déplacement ne se dérive **plus** du
 * flou. Un appelant qui s'en servirait encore demanderait une lentille ; la table
 * ([glassRefractionDisplacementPx]) ne la lui donne plus. La valeur est celle d'avant, à titre de
 * mémoire de la faute — elle n'est plus lue pour peindre.
 */
const val GlassRefractionDisplacementRatio: Float = 0.9f

/** La part de la plus petite dimension consacrée au profil optique (l'éclairage, lui, l'utilise). */
const val GlassRefractionHeightFraction: Float = 0.18f

/**
 * L'intensité du deuxième passage de détail sur l'arête.
 *
 * **Zéro, et c'est la seconde cause du défaut.** Ce passage réinjecte les **hautes fréquences** du
 * fond après le flou : à 0,8, 80 % des arêtes et du texte traversaient le verre **nets**, malgré un
 * flou de 30 dp — c'est exactement « l'étiquette reste lisible dans la tuile ». Un verre de la référence ne
 * réinjecte aucun détail : le flou est la seule image du fond, et rien de reconnaissable n'y survit.
 */
const val GlassRefractionDetailIntensity: Float = 0f

/**
 * La dispersion chromatique de l'arête, 0 à 1.
 *
 * **Zéro chez la référence.** À 0,06 elle teintait l'arête d'une frange arc-en-ciel — la « goutte d'eau »
 * que le propriétaire a vue. Le liseré reste, mais il est **blanc** : une arête, pas un prisme.
 */
const val GlassChromaticAberration: Float = 0f

/**
 * Le gain de saturation du fond **dans** le verre : +10 %.
 *
 * C'est la seule des quatre « saveurs » du verre de la référence que l'app n'avait pas : le fond est
 * **légèrement** plus saturé dans la vitre que derrière elle. 1,10 est le pas retenu — assez pour
 * que la matière se sente, trop peu pour repeindre une photo.
 */
const val GlassSaturationGain: Float = 1.10f

/** L'intensité du reflet spéculaire ambiant du verre. */
const val GlassSpecularIntensity: Float = 0.5f

/** L'ombre d'arête que le verre pose sous son liseré, en alpha. */
const val GlassEdgeShadowAlpha: Float = 0.14f

/** L'adoucissement du bord du verre, en dp : c'est là que la réfraction s'éteint. */
const val GlassEdgeSoftnessDp: Float = 1f

// ---------------------------------------------------------------------------------------------
// Qui a posé une source — le compte que la vitre sait lire
// ---------------------------------------------------------------------------------------------

/**
 * **Le compte des sources posées.**
 *
 * Haze 2 a refermé la liste de ses zones (`HazeState.areas`, désormais `internal`) : la vitre ne peut
 * plus demander à l'état s'il y a **quelque chose à échantillonner**. Or c'est exactement ce qu'elle
 * doit savoir — sans source, Haze ne dessine rien, et la vitre doit retomber sur son voile plutôt que
 * de rester transparente sur du texte.
 *
 * On tient donc le compte nous-mêmes, à côté de l'état Haze : un écran qui pose [onyxHazeSource]
 * incrémente, et le retire en quittant. Le compte est un **état de snapshot**, donc la vitre se
 * rallume d'elle-même dès que la source arrive — sans que l'écran ait à le lui dire.
 */
@Stable
class GlassSourcePresence {
    /** Le nombre de sources posées dans l'arbre. */
    var count: Int by mutableIntStateOf(0)
        internal set

    internal fun enter() {
        count += 1
    }

    internal fun leave() {
        if (count > 0) count -= 1
    }
}

/** Le compte des sources de l'arbre ; `null` hors de l'arbre qui l'a posé (aperçus, tests). */
val LocalGlassSourcePresence = staticCompositionLocalOf<GlassSourcePresence?> { null }

/**
 * **L'état du verre, tel qu'il tourne sur cet appareil.**
 *
 * Le propriétaire ne pouvait pas répondre à sa propre question — « je ne sais pas où est ton verre
 * liquide » — parce que l'app ne disait **nulle part** quel palier elle avait retenu ni combien de
 * surfaces échantillonnaient réellement. Un diagnostic qui ne se lit qu'à la capture n'est pas un
 * diagnostic : les Réglages affichent donc cet état (une ligne, pas un tableau de bord permanent).
 *
 * Le compte est tenu par [liquidGlass] lui-même : il incrémente **quand la vitre échantillonne**, donc
 * le chiffre affiché est le nombre de vitres qui lisent vraiment l'arrière-plan — pas le nombre
 * d'appels à la fonction, qui vaudrait aussi pour les voiles posés sur un aplat.
 */
@Stable
object GlassDiagnostics {

    /** Le nombre de vitres qui échantillonnent l'arrière-plan, à cet instant. */
    var sampling: Int by mutableIntStateOf(0)
        private set

    /** Le palier du **dernier** verre qui échantillonne : c'est celui de l'écran qu'on regarde. */
    var tier: GlassTier by mutableStateOf(GlassTier.VEIL)
        private set

    internal fun enter(palier: GlassTier) {
        sampling += 1
        tier = palier
    }

    internal fun leave() {
        if (sampling > 0) sampling -= 1
    }

    /** Le nom d'un palier, tel qu'il se lit dans les Réglages. Jamais un nom de marque. */
    fun tierLabel(palier: GlassTier): String = when (palier) {
        GlassTier.REFRACTION -> "réfraction"
        GlassTier.BLUR -> "flou"
        GlassTier.VEIL -> "voile"
    }

    /** Le nom d'une qualité : pleine, allégée, ou repli. */
    fun levelLabel(qualite: GlassLevel): String = when (qualite) {
        GlassLevel.FULL -> "pleine"
        GlassLevel.LITE -> "allégée"
        GlassLevel.SCRIM -> "repli"
    }

    /**
     * **La ligne d'état du verre**, telle qu'elle s'affiche. Pure : elle reçoit ce qu'elle raconte, donc
     * le test peut la figer mot pour mot — et le propriétaire peut la lire dans l'app.
     *
     * Elle dit les trois choses qui décident de ce qu'il voit : le palier de la plateforme, la qualité
     * retenue par la mesure de fluidité, et le **nombre de surfaces qui échantillonnent** (zéro veut
     * dire « rien à voir ici », et c'est une réponse, pas une panne).
     */
    fun line(palier: GlassTier, qualite: GlassLevel, surfaces: Int, sdkInt: Int): String {
        val compte = when (surfaces) {
            0 -> "aucune surface en verre ici"
            1 -> "1 surface en verre"
            else -> "$surfaces surfaces en verre"
        }
        return "Verre liquide : ${tierLabel(palier)} (Android ${sdkInt}) · qualité ${levelLabel(qualite)} · $compte"
    }
}

/**
 * **La source Haze, comptée.** Même modificateur que [hazeSource][dev.chrisbanes.haze.hazeSource] —
 * il échantillonne le contenu qui défile — plus une ligne : il dit à [LocalGlassSourcePresence] qu'il
 * est là. C'est ce qui permet à [liquidGlass] de distinguer « il y a du contenu à voir » de
 * « il n'y a rien », sans deviner.
 */
fun Modifier.onyxHazeSource(state: HazeState): Modifier = composed {
    val presence = LocalGlassSourcePresence.current
    DisposableEffect(presence) {
        presence?.enter()
        onDispose { presence?.leave() }
    }
    this.hazeSource(state)
}

// ---------------------------------------------------------------------------------------------
// La fluidité — le mode allégé se déclenche sur une mesure
// ---------------------------------------------------------------------------------------------

/**
 * Le suivi de fluidité, et la règle (pure) qui en tire une qualité.
 *
 * Pourquoi une mesure plutôt qu'un seuil d'appareil : le flou d'arrière-plan coûte cher, mais son
 * coût dépend de ce qui passe dessous — un fil de texte coûte moins qu'un fil d'images. Un appareil
 * rapide qui rame sur un fil lourd doit donc **descendre**, et un appareil modeste qui suit son fil
 * doit pouvoir **remonter**. On échantillonne donc les trames réellement produites, et la décision
 * se fait en **charge** — la durée moyenne des trames divisée par la **période d'affichage** — avec
 * une **bande morte** (1,35 pour descendre, 1,15 pour remonter) pour ne pas osciller.
 *
 * L'échantillonneur ne tourne **que** quand au moins une vitre est à l'écran (`demand`), comme le
 * calque de [Frost] n'est enregistré que si une vitre le demande : un écran sans verre ne paie rien.
 */
@Stable
class GlassFluidity internal constructor(
    ceiling: GlassLevel,
) {
    /** Le plafond de l'appareil — on ne remonte jamais au-dessus. */
    var ceiling: GlassLevel by mutableStateOf(ceiling)
        internal set

    /**
     * La période d'affichage, en millisecondes (16,67 ms à 60 images/s, 8,33 ms à 120).
     *
     * Tout le suivi s'y rapporte : une trame de 16,67 ms est **parfaite** sur un écran à 60 images/s
     * et deux fois trop lente sur un écran à 120. Un seuil écrit en dur sur 60 images/s aurait
     * déclaré « saccadé » un appareil à 120 images/s parfaitement fluide — et fait tomber le verre
     * en mode allégé sur les meilleurs appareils. C'est la faute que cette constante évite.
     */
    var frameBudgetMs: Double = DefaultFrameBudgetMs
        internal set

    /** Combien de vitres veulent être mesurées. À zéro, aucune trame n'est écoutée. */
    var demand by mutableIntStateOf(0)
        private set

    /** La qualité retenue, telle que les vitres la lisent. */
    var level: GlassLevel by mutableStateOf(ceiling)
        private set

    /**
     * **L'appui tactile a-t-il encore le droit de se peindre ?**
     *
     * L'appui tourne **pendant** le geste, c'est-à-dire au pire moment pour le budget. Il est donc
     * coupé par la mesure, pas par une intuition : dès que la charge atteint le seuil d'allégement
     * ([DowngradeLoad], la même valeur qui fait descendre la qualité du flou), l'appui s'éteint — et
     * il ne se rallume que sous le seuil de retour ([RestoreLoad]), donc sans oscillation.
     *
     * C'est la règle du lot : « un verre fluide qui fait tomber à trente images par seconde n'est
     * pas un verre réussi ». La lumière au doigt a été retirée (voir `GlassTouch.kt`) ; ce qui reste
     * — un enfoncement, une ombre — reste la pièce la plus dispensable, et c'est elle qui tombe en
     * premier, pas le flou.
     */
    var pressAllowed: Boolean by mutableStateOf(true)
        private set

    private val window = ArrayDeque<Double>()
    private var settled: GlassLevel = ceiling
    private var lastFrame = 0L

    /**
     * Combien de fenêtres se sont écoulées depuis le dernier changement de qualité.
     *
     * Sans ce compteur, la qualité **clignote** : mesuré sur l'émulateur, `FULL -> LITE` puis
     * `LITE -> FULL` à 400 ms d'intervalle pendant un défilement qui respire — deux niveaux de flou
     * en alternance rapide, ce qui se voit plus que le flou lui-même. Une qualité tient donc au
     * moins [DwellWindows] fenêtres (environ une seconde) avant d'en changer.
     */
    private var windowsSinceChange: Int = DwellWindows

    /** Combien d'échantillons sont entrés depuis la dernière **évaluation** (une par fenêtre). */
    private var samplesSinceWindow: Int = 0

    internal fun acquire() {
        demand += 1
    }

    internal fun release() {
        demand = (demand - 1).coerceAtLeast(0)
        if (demand == 0) {
            // Plus rien à mesurer : la fenêtre repart à zéro, pour qu'un retour sur l'écran ne soit
            // pas jugé sur les trames d'un autre écran.
            window.clear()
            lastFrame = 0L
            samplesSinceWindow = 0
            settled = ceiling
            windowsSinceChange = DwellWindows
            level = ceiling
            pressAllowed = true
        }
    }

    /** Le plafond ou la période d'affichage changent : la mesure repart de zéro. */
    internal fun retune(ceiling: GlassLevel, frameBudgetMs: Double) {
        this.ceiling = ceiling
        if (frameBudgetMs > 0.0) this.frameBudgetMs = frameBudgetMs
        window.clear()
        lastFrame = 0L
        samplesSinceWindow = 0
        settled = ceiling
        windowsSinceChange = DwellWindows
        if (level != settled) level = settled
    }

    /**
     * Une trame de plus. La décision ne bouge que si la fenêtre est **pleine** — quatre trames ne
     * disent rien — et une trame anormalement longue (mise en veille, changement d'écran, retour
     * du système) est écartée : ce n'est pas le verre qui l'a coûtée.
     */
    internal fun sample(frameNanos: Long) {
        val previous = lastFrame
        lastFrame = frameNanos
        if (previous == 0L) return
        val ms = (frameNanos - previous) / 1_000_000.0
        if (ms <= 0.0 || ms > GlassFluidity.IgnoredFrameMs) return
        window.addLast(ms)
        while (window.size > GlassFluidity.WindowFrames) window.removeFirst()
        if (window.size < GlassFluidity.WindowFrames) return
        // **Une évaluation par fenêtre pleine**, pas une par trame : la décision porte sur une
        // demi-seconde de rendu, pas sur la dernière trame — et la durée de garde se compte alors
        // en fenêtres, ce qui la rend vérifiable sans appareil (une évaluation par trame rendait le
        // garde-fou dépendant du nombre de trames, donc inopérant : mesuré).
        if (++samplesSinceWindow < GlassFluidity.WindowFrames) return
        samplesSinceWindow = 0
        val average = window.sum() / window.size
        val jank = window.count { it > frameBudgetMs * GlassFluidity.JankLoad }.toDouble() / window.size
        val next = GlassFluidity.levelFor(average / frameBudgetMs, jank, settled)
        // **Le budget de l'appui**, décidé sur la même mesure et la même fenêtre : au-dessus du seuil
        // d'allégement, l'appui s'éteint ; il ne se rallume que sous le seuil de retour. Deux états,
        // deux seuils, aucune oscillation — et une seule ligne de journal, comme la qualité.
        val pressNext = when {
            !pressAllowed -> average / frameBudgetMs < GlassFluidity.RestoreLoad &&
                jank < GlassFluidity.RestoreJankRatio
            average / frameBudgetMs > GlassFluidity.DowngradeLoad ||
                jank > GlassFluidity.DowngradeJankRatio -> false
            else -> true
        }
        if (pressNext != pressAllowed) {
            println(
                "$GlassLogTag appui ${if (pressNext) "rétabli" else "coupé par le budget"} : charge " +
                    "${"%.2f".format(Locale.ROOT, average / frameBudgetMs)}× la période " +
                    "(${"%.1f".format(Locale.ROOT, average)} ms), saccades " +
                    "${"%.0f".format(Locale.ROOT, jank * 100)} %",
            )
            pressAllowed = pressNext
        }
        val decided = GlassFluidity.dwelled(next, settled, windowsSinceChange)
        if (decided != settled) {
            settled = decided
            // La fenêtre qui change la qualité la **relance** : elle ne compte pas dans la durée de
            // garde, sans quoi une seule fenêtre suffirait à repartir dans l'autre sens.
            windowsSinceChange = 0
        } else {
            windowsSinceChange = (windowsSinceChange + 1).coerceAtMost(Int.MAX_VALUE / 2)
        }
        // Le plafond est la **meilleure** qualité permise : les ordinaux descendent avec la qualité,
        // donc on ne garde jamais un ordinal plus bas que le sien.
        val capped = if (settled.ordinal < ceiling.ordinal) ceiling else settled
        if (capped != level) {
            // Le changement est **journalisé** : c'est la trace qui prouve que le mode allégé se
            // déclenche sur la mesure, et à quelle charge. Sans cette ligne, la décision ne serait
            // vérifiable que par ses effets.
            // `println` plutôt que le journal du système : la règle de qualité se vérifie **sans
            // appareil** (un test unitaire ne peut pas appeler le journal d'Android, dont les
            // méthodes ne sont pas simulées), et la trace reste lisible dans `logcat` sous
            // l'étiquette ci-dessous.
            println(
                "$GlassLogTag qualité $level -> $capped : charge " +
                    "${"%.2f".format(Locale.ROOT, average / frameBudgetMs)}× la période " +
                    "(${"%.1f".format(Locale.ROOT, average)} ms), saccades " +
                    "${"%.0f".format(Locale.ROOT, jank * 100)} %",
            )
            level = capped
        }
    }

    companion object {
        /** La période d'affichage par défaut : 60 images/s, quand l'écran ne se laisse pas lire. */
        const val DefaultFrameBudgetMs: Double = 16.67

        /** La fenêtre de mesure, en trames — une demi-seconde à 60 images/s. */
        const val WindowFrames: Int = 32

        /**
         * Le nombre de fenêtres qu'une qualité tient avant qu'une autre décision puisse s'appliquer :
         * deux fenêtres, soit environ une seconde à 60 images/s. C'est ce qui empêche le clignotement
         * entre pleine qualité et mode allégé sur une charge qui respire.
         */
        const val DwellWindows: Int = 2

        /** Une trame plus longue que ça n'est pas imputée au verre (veille, changement d'écran). */
        const val IgnoredFrameMs: Double = 64.0

        /** Au-delà, la trame a manqué un vsync : c'est une saccade, pas une trame pleine. */
        const val JankLoad: Double = 1.5

        /** Au-dessus de cette charge moyenne, le verre s'allège. */
        const val DowngradeLoad: Double = 1.35

        /** Ou quand plus d'une trame sur cinq manque son vsync. */
        const val DowngradeJankRatio: Double = 0.20

        /** En dessous et sans saccade, il remonte — la bande morte est de 20 %. */
        const val RestoreLoad: Double = 1.15

        /** Et moins d'une trame sur vingt perdue. */
        const val RestoreJankRatio: Double = 0.05

        /** Allégé ne suffit pas : au-delà, le verre passe en repli. */
        const val ScrimLoad: Double = 2.0

        /** Idem quand près d'une trame sur deux est perdue malgré le mode allégé. */
        const val ScrimJankRatio: Double = 0.45

        /**
         * La règle, **pure** : la qualité suivante, sachant la qualité courante et la **charge** —
         * la moyenne des trames divisée par la période d'affichage.
         *
         * Les deux sens ne partagent pas les mêmes seuils : c'est ce qui empêche l'oscillation. Et
         * une charge de 1,0 (le cas d'une app au repos, dont les trames tombent juste sur le vsync)
         * reste en pleine qualité.
         */
        /**
         * La décision **retenue** : la qualité ne change qu'une fois [DwellWindows] fenêtres
         * écoulées depuis le dernier changement. Pure, donc vérifiable seule — c'est elle qui
         * empêche le clignotement `FULL -> LITE -> FULL` observé à 400 ms d'intervalle avant ce
         * garde-fou.
         */
        fun dwelled(
            next: GlassLevel,
            current: GlassLevel,
            windowsSinceChange: Int,
        ): GlassLevel = if (next != current && windowsSinceChange >= DwellWindows) next else current

        /**
         * La règle de charge, **pure** : la qualité suivante, sachant la qualité courante.
         */
        fun levelFor(load: Double, jankRatio: Double, current: GlassLevel): GlassLevel = when (current) {
            GlassLevel.FULL ->
                if (load > DowngradeLoad || jankRatio > DowngradeJankRatio) GlassLevel.LITE else current

            GlassLevel.LITE -> when {
                load > ScrimLoad || jankRatio > ScrimJankRatio -> GlassLevel.SCRIM
                load < RestoreLoad && jankRatio < RestoreJankRatio -> GlassLevel.FULL
                else -> current
            }

            GlassLevel.SCRIM ->
                if (load < RestoreLoad && jankRatio < RestoreJankRatio) GlassLevel.LITE else current
        }
    }
}

/**
 * L'échantillonneur de l'arbre, posé une fois à la racine (voir `Theme.kt`) avec le thème : la
 * décision (pleine, allégée, repli) est ainsi **une** pour tous les écrans, et un changement
 * d'apparence ou de réglage se voit sans redémarrer l'app.
 */
@Composable
fun rememberGlassFluidity(): GlassFluidity {
    val context = LocalContext.current
    val view = LocalView.current
    val lowRam = remember(context) {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
    }
    val ceiling = remember(lowRam) { glassCeiling(Build.VERSION.SDK_INT, lowRam) }
    // La période d'affichage **réelle** : un écran à 120 images/s ne se juge pas avec le budget d'un
    // écran à 60.
    val refreshHz = view.display?.refreshRate?.takeIf { it > 1f } ?: 60f
    val budget = 1000.0 / refreshHz
    val holder = remember { GlassFluidity(ceiling) }
    LaunchedEffect(ceiling, budget) { holder.retune(ceiling, budget) }

    // L'écoute des trames ne démarre que si une vitre est composée. `demand` est lu ici : la
    // première vitre déclenche la mesure, la dernière l'arrête.
    val wanted = holder.demand > 0
    LaunchedEffect(wanted) {
        if (!wanted) return@LaunchedEffect
        while (true) {
            withFrameNanos { holder.sample(it) }
        }
    }
    return holder
}

// ---------------------------------------------------------------------------------------------
// La lisibilité — mesurée, jamais supposée
// ---------------------------------------------------------------------------------------------

/** Ce qu'une surface porte : le seuil de son encre vient de là. */
enum class GlassInk(val minRatio: Float) {
    /** Un libellé, un titre : 4,5:1. */
    TEXT(OnyxContrast.TEXT_MIN),

    /** Une icône, un chiffre : 3:1. */
    ICON(OnyxContrast.ICON_MIN),

    /**
     * Une encre **dérivée** : la surface ne pose pas la teinte du thème telle quelle, elle **choisit**
     * son encre contre le fond réellement composé ([readableInk], lue par la barre d'onglets et par la
     * puce du modèle).
     *
     * Pourquoi une famille à part, et pas [TEXT] : exiger *la* teinte du thème sur la vitre oblige le
     * voile à monter jusqu'à ce qu'un gris secondaire translucide tienne 4,5:1 **sur du noir pur** —
     * relevé sur la barre d'onglets en clair : le plancher montait à **67 %** de blanc, c'est-à-dire
     * une vitre opaque. Ce qui doit tenir, c'est qu'une encre **lisible existe** sur le voile retenu :
     * le noir ou le blanc, celui des deux qui passe. Le voile peut donc rester mince (**48 %** dans le
     * même cas, mesuré par `LiquidGlassTest`) et le contenu qui défile derrière reste visible — c'est
     * la définition du verre liquide, et c'est ce que le propriétaire demandait.
     */
    DERIVED(OnyxContrast.TEXT_MIN),
}

/**
 * **Ce que la vitre peut échantillonner** — et c'est ce qui décide de son chemin de peinture, pas le
 * goût de l'appelant.
 *
 * Haze ne sait lire que la fenêtre où il vit : la vitre et sa source doivent être dans la **même**
 * fenêtre. C'est la seule contrainte technique de tout le verre, et elle range les surfaces de
 * l'app en trois familles. Les nommer ici, plutôt que de les redécouvrir appel par appel, évite la
 * faute qui consiste à poser un flou qu'aucune source ne peut alimenter (le « verre perdu » : un
 * voile presque opaque qui se lit à 0,07:1 de la page, mesuré).
 */
enum class GlassSampling {
    /** Du contenu passe dessous, dans la **même** fenêtre : Haze floute pour de vrai. */
    IN_SCREEN,

    /**
     * Le panneau vit dans **sa propre fenêtre** (feuille, menu ancré, alerte) : une `Dialog` ouvre
     * une fenêtre que le calque de la fenêtre principale n'atteint pas, Haze ne peut donc rien
     * échantillonner. Le flou vient du **système** (`DialogWindowBlur`, `FLAG_BLUR_BEHIND`), et la
     * finition — teinte, reflet de tranche, liseré internal, lumière de tête — reste exactement
     * celle des autres vitres, parce qu'elle est peinte par le **même** code ([drawGlassFinish]).
     */
    OWN_WINDOW,

    /** Rien ne passe dessous (une barre posée sur un aplat, un contrôle segmenté) : pas de calque. */
    FLAT,
}

/**
 * Une surface du verre : son flou, son encre, son ombre, sa forme — et ce qu'elle peut
 * échantillonner.
 *
 * Les rayons sont **par surface** parce que leur hauteur et ce qu'elles portent diffèrent : une
 * capsule de 22 dp n'a pas besoin du flou d'un compositeur de 100 dp, et un flou plus large qu'une
 * surface produirait un aplat. Tous tiennent dans 36–50 dp, la fourchette demandée.
 *
 * **La table est le registre.** Une surface qui n'y est pas ne peut pas recevoir de vitre correcte :
 * elle n'aurait ni forme, ni rayon, ni niveau de flou, ni seuil d'encre. C'est pour ça que les
 * surfaces qui *ne peuvent pas* porter de flou y figurent quand même, avec leur [sampling] — la
 * liste des écarts fait partie du livrable, et elle se lit ici, pas dans un commentaire.
 */
enum class GlassSurface(
    /** Le rayon de flou, en dp, en pleine qualité. Contraint à 36–50 par le test. */
    val blurDp: Float,
    /** Le seuil que l'encre posée dessus doit tenir. */
    val ink: GlassInk,
    /** L'ombre douce qui détache la vitre du contenu — jamais « carte flottante ». */
    val shadowDp: Float,
    /**
     * La **forme** de la vitre, avec son rayon.
     *
     * Elle est portée par la surface, pas par l'appelant : une vitre qui n'a pas de forme arrondie
     * se lit comme un rectangle franc (« les bords ne sont pas arrondis »), et c'est un défaut qui
     * se glisse à chaque nouvel appel. `liquidGlass` s'en sert **par défaut** — un appelant peut la
     * remplacer, il ne peut pas l'oublier.
     */
    val shape: Shape,
    /** Ce que la vitre peut échantillonner — donc son chemin de peinture. */
    val sampling: GlassSampling = GlassSampling.IN_SCREEN,
    /**
     * **La vitre reçoit-elle sa « profondeur » (lumière de tête, ombre de pied, liseré interne,
     * reflet de tranche) ?**
     *
     * `true` (défaut) : la finition est peinte — c'est ce qui fait lire une **vitre**.
     * `false` : la surface est un **aplat** — la teinte et le flou restent (elle se sépare donc de
     * la page par la seule luminance), mais **aucun filet, aucun halo** n'est ajouté par-dessus.
     *
     * Pourquoi ce cran existe : la planche d'acceptation (« Galerie ») dessine la plaque du bas et
     * les cartes/tuiles en **aplats** — mesuré sur `fil-clair.png`/`fil-sombre.png` : le corps de la
     * plaque est plat (248–250 en clair, ~22 en sombre) et une carte ne porte **ni ombre ni arête**.
     * L'app, elle, ajoutait à ces surfaces un **filet clair** sur l'arête haute (mesuré L≈254 en
     * clair, L≈107 en sombre) et un **halo dégradé** (mesuré 86 → 64 en sombre). Ce sont deux
     * surfaces empilées là où la planche n'en veut qu'une.
     */
    val lit: Boolean = true,
    /**
     * **La plaque porte-t-elle son arête de verre** (l'arête claire du haut, le jeton `--gedge` de
     * la planche) ? `false` par défaut : une carte, une tuile, une rangée n'en portent pas.
     *
     * `true` sur les **trois plaques flottantes** — l'en-tête, la plaque du compositeur et des
     * onglets, la carte du compositeur — et sur la **plaque d'onglet** ([PANE]) : c'est le reproche
     * du propriétaire (« il manque du liquid glass, c'est pas assez prononcé »), mesuré avant :
     * les deux plaques étaient peintes **sans arête** (le jeton existait, [OnyxEdge.topEdge], et
     * n'était appelé par personne).
     */
    val plateEdge: Boolean = false,
) {

    /** La barre du haut du fil, par-dessus le fil qui défile. Une barre **flottante**, comme le
     * compositeur et la barre d'onglets : elle est détachée des bords, sans quoi ses coins arrondis
     * se poseraient sur le bord de l'écran et se liraient comme un rectangle rogné. */
    THREAD_TOP_BAR(GlassBlurDefaultDp, GlassInk.TEXT, 4f, RoundedCornerShape(GlassBarCorner), lit = false, plateEdge = true),

    /**
     * **La barre des cinq onglets, en pilule flottante.**
     *
     * Deux décisions de forme, mesurées sur la capture du propriétaire :
     *
     * - sa forme est une **capsule** ([RoundedCornerShape] à 50 %) et non un rectangle arrondi :
     *   sur une pilule détachée des quatre côtés, un rayon de 22 dp se lit encore comme un rectangle
     *   rogné, alors que la capsule épouse la hauteur et se lit comme un objet posé ;
     * - son encre est **dérivée** ([GlassInk.DERIVED]) : ses libellés sont choisis contre le voile
     *   composé par `readableInk`, si bien que le voile peut rester mince et laisser voir le fil qui
     *   défile dessous — c'est le « liquid glass ».
     */
    TAB_BAR(GlassBarBlurPlateDp, GlassInk.DERIVED, 5f, RoundedCornerShape(50), lit = false, plateEdge = true),

    /** Le compositeur quand le fil défile dessous : la plus grande surface, donc le plus de flou. */
    COMPOSER(48f, GlassInk.TEXT, 6f, RoundedCornerShape(GlassBarCorner), lit = false, plateEdge = true),

    /**
     * **La plaque d'un onglet de contenu** — celle qui glisse sous le doigt (Chat / Flux / Fichiers).
     *
     * Le propriétaire : « quand je clique par exemple Flux ou Fichiers je devrais avoir comme une
     * plaque en verre que je peux faire glisser de gauche a droite ». Avant ce lot, le pageur ne
     * portait que du **contenu nu** : rien à déplacer sinon le texte, aucune arête pendant le geste
     * (mesuré sur les quatre images prises à mi-course). Cette surface est la plaque qui se déplace :
     * elle vit **dans** la page du pageur, donc elle suit le doigt à l'identique — c'est le pageur
     * qui la translate, elle n'a pas de geste à elle.
     *
     * Son encre est **dérivée** ([GlassInk.DERIVED]) pour la même raison que la barre d'onglets : elle
     * porte du texte du thème, et une encre fixe ferait remonter le voile jusqu'à l'aplat.
     */
    PANE(GlassBlurDefaultDp, GlassInk.DERIVED, 8f, RoundedCornerShape(PlaqueGeometry.Radius), lit = false, plateEdge = true),

    /**
     * La capsule d'en-tête (sous-agents) : elle ne porte qu'un chiffre et une icône, donc le seuil
     * est celui des icônes (3:1) — et comme elle est petite, son flou est le plus serré de la
     * famille.
     */
    HEADER_CAPSULE(GlassBlurMinDp, GlassInk.ICON, 2f, RoundedCornerShape(50)),

    /**
     * **Les deux feuilles du bas** (actions d'une conversation, ajout de pièce jointe) et l'alerte.
     *
     * Elles vivent dans leur propre fenêtre : Haze ne peut pas les échantillonner
     * ([GlassSampling.OWN_WINDOW]), donc le flou réel vient du système et la finition du même code.
     * Leur flou nominal est **le plus large de la table** parce qu'elles couvrent presque tout
     * l'écran : c'est la seule surface où un rayon de 40 dp ne mange pas le panneau.
     */
    SHEET(40f, GlassInk.TEXT, 14f, RoundedCornerShape(GlassPanelCorner), GlassSampling.OWN_WINDOW),

    /** L'alerte : le même panneau qu'une feuille, en plus petit. */
    ALERT(40f, GlassInk.TEXT, 14f, RoundedCornerShape(GlassPanelCorner), GlassSampling.OWN_WINDOW),

    /**
     * Le menu ancré, posé près de la rangée touchée : sa propre fenêtre lui aussi, avec un voile
     * léger devant le reste de l'écran — donc un panneau qui laisse voir.
     */
    MENU(40f, GlassInk.TEXT, 12f, RoundedCornerShape(GlassPanelCorner), GlassSampling.OWN_WINDOW),

    /**
     * Le volet latéral. En surimpression (téléphone, paysage, pliable replié) il **recouvre le
     * fil** : du contenu passe dessous, donc la vitre peut vraiment échantillonner. Posé à côté du
     * détail (pliable déplié), rien ne passe dessous et le voile prend le relais — la même table
     * sert les deux, seul le chemin de peinture change, et il change **tout seul** parce que
     * `liquidGlass` mesure s'il y a une source.
     *
     * Sa forme n'arrondit que le bord extérieur (droit) : c'est celui qui se voit, l'autre étant
     * contre le bord de l'écran en surimpression et contre le détail une fois posé.
     */
    SIDEBAR_PANEL(
        36f,
        GlassInk.TEXT,
        8f,
        RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp),
    ),

    /** Une tuile du volet : un carré de verre dans un panneau de verre. */
    SIDEBAR_TILE(GlassBlurMinDp, GlassInk.TEXT, 0f, RoundedCornerShape(OnyxSpace.TileCorner), lit = false),

    /** Une carte de bot : même famille que la tuile, un cran de rayon plus large. */
    BOT_CARD(GlassBlurMinDp, GlassInk.TEXT, 0f, RoundedCornerShape(OnyxSpace.CardCorner), lit = false),

    /**
     * Une rangée de liste (conversation, salon, bot de l'onglet *Bots*). C'est la surface la plus
     * répétée de l'app : son flou est le plus serré et son ombre la plus faible, sans quoi une
     * liste de trente rangées ferait trente ombres.
     */
    LIST_ROW(GlassBlurMinDp, GlassInk.TEXT, 0f, RoundedCornerShape(OnyxSpace.CardCorner), lit = false),

    /**
     * Le bloc de réponse : la carte de l'assistant dans le fil.
     *
     * Union de deux corrections, sans rien perdre :
     * — son flou était **34 dp**, un cran **sous le plancher** de la table ; il prend donc le
     *   plancher **`GlassBlurMinDp`**, comme les autres cartes. Une surface plus floue que les autres
     *   laissait lire la réponse précédente à travers la suivante, et cette carte défile **au-dessus
     *   du fil** — c'est le cas qui a le plus besoin du flou, pas le moins.
     * — et comme les autres cartes, elle reste **sans ombre ni finition** (`shadowDp = 0f`,
     *   `lit = false`) : la matière de l'app est un seul aplat, pas une carte flottante.
     */
    RESPONSE_BLOCK(GlassBlurMinDp, GlassInk.TEXT, 0f, RoundedCornerShape(OnyxSpace.CardCorner), lit = false),

    /**
     * Le contrôle segmenté (*Chats / Bots*, les filtres) : il porte des icônes et des chiffres, donc
     * le seuil des icônes. Il est posé dans une barre, sur un aplat — mais il est **une pilule**,
     * donc sa forme reste arrondie et son liseré fait tout le travail de détachement.
     */
    SEGMENTED(GlassBlurMinDp, GlassInk.ICON, 0f, RoundedCornerShape(50), GlassSampling.FLAT, lit = false),

    /**
     * **La grande carte de matière** : le verre sur une surface de contenu — le fil vide qui
     * s'ouvre, le panneau du volet quand il recouvre le fil.
     *
     * C'est la forme de la référence du propriétaire (« une grande carte de verre, très arrondie,
     * qui ne touche pas les bords, teintée par ce qu'il y a derrière ») : un rayon de [GlassCardCorner]
     * (30 dp, la famille des panneaux), une ombre large ([GlassCardShadow]) et une encre **dérivée** —
     * la carte choisit son encre contre le voile composé, donc le voile peut rester mince et la
     * matière se voir au travers. Sans encre dérivée, le plancher de lisibilité d'une encre fixe
     * remonte jusqu'à l'opacité : c'est ainsi qu'une vitre devient une capsule blanche.
     */
    MATTER_CARD(40f, GlassInk.DERIVED, GlassCardShadow, RoundedCornerShape(GlassCardCorner)),
    ;

    /**
     * **La vitre s'enfonce-t-elle à l'appui ?**
     *
     * Une échelle de 1,5 % sur une pastille de 40 dp change 0,6 dp de son dessin : c'est un
     * enfoncement, on le sent. Sur la carte d'un compositeur (96 dp de haut, du texte dedans), la
     * même échelle déplacerait l'encre d'un pixel et demi — on le verrait **bouger**, ce qui est un
     * défaut. Les grandes surfaces prennent donc l'appui par le **gain de luminance** seul ; les
     * petites prennent les deux. La limite est la hauteur : au-dessus de 40 dp de flou (les barres et
     * les panneaux), on n'enfonce pas.
     */
    val scalesOnPress: Boolean get() = blurDp < 40f
}

/** Le rayon de flou de la famille des **panneaux** : feuille, alerte, menu. */
val GlassPanelCorner: Dp = OnyxSpace.PanelCorner

/**
 * **Le palier avec lequel le verre est PEINT.**
 *
 * Il vaut [GlassLevel.FULL], et il est **constant** — c'est la règle du lot « opacité constante ».
 *
 * Pourquoi une constante, et pas la mesure ([GlassFluidity.level]) : la mesure fait varier ce qui est
 * **peint** (le voile du mode allégé remonte de 12 points, le repli **éteint le flou**), et une
 * surface dont l'opacité ou le flou dépend d'une grandeur qu'on ne commande pas — la charge des
 * trames, qui monte toute seule quand la cadence baisse ou quand l'écran ne se rafraîchit plus au
 * rythme lu à la composition ([GlassFluidity.frameBudgetMs]) — se met à changer **sans qu'on ait
 * rien touché**. Le pire cas était le repli : flou **éteint**, le contenu passait net sous l'encre et
 * se lisait **à travers** la plaque et le compositeur — le défaut rapporté par le propriétaire
 * (« si je ne touche pas l'écran, ça devient trop transparent »).
 *
 * Le verre garde donc **une seule** apparence, celle du repos : sa teinte nominative remontée au
 * plancher de lisibilité, et son flou nominal. La mesure reste en place — elle sert au budget du halo
 * ([GlassFluidity.haloAllowed]) et continue de se journaliser sous `OnyxGlass` — mais elle **ne peint
 * plus rien**. Le repli de *plateforme* (SDK sans flou, appareil qui refuse) reste, lui : c'est une
 * capacité, pas une charge.
 */
val glassPaintLevel: GlassLevel = GlassLevel.FULL

/**
 * L'opacité **peinte** de la teinte d'une surface : [glassTintAlpha] au palier constant
 * [glassPaintLevel]. Fonction **sans paramètre de niveau**, à dessein — c'est ce qui empêche la
 * mesure de rentrer à nouveau dans le chemin de peinture, et c'est elle que le verrou interroge.
 */
fun glassPaintTintAlpha(
    surface: GlassSurface,
    dark: Boolean,
    inks: List<Color>,
): Float = glassTintAlpha(surface, glassPaintLevel, dark, inks)

/** Le flou **peint** d'une surface : [glassBlurDp] au palier constant [glassPaintLevel]. */
fun glassPaintBlurDp(surface: GlassSurface): Float = glassBlurDp(surface, glassPaintLevel)

/** La profondeur d'ombre **peinte** d'une surface, au palier constant ([GlassLevel.SCRIM] amincit). */
fun glassPaintShadowDp(surface: GlassSurface): Float =
    surface.shadowDp * if (glassPaintLevel == GlassLevel.SCRIM) 0.6f else 1f

/**
 * Le rayon de la **grande carte de verre** : 30 dp, la fourchette de la référence du propriétaire
 * (28–32 dp), et un cran au-dessus du rayon des barres (22 dp) — une carte se reconnaît à son coin,
 * une barre à sa forme.
 */
val GlassCardCorner: Dp = 30.dp

/**
 * L'ombre de la grande carte : **large et douce**, une seule pour toute la famille ([MATTER_CARD]),
 * parce qu'une carte posée sur la matière doit s'en détacher sans se lire comme un panneau flottant.
 * C'est le haut de la fourchette des ombres du verre (2 à 14 dp).
 */
const val GlassCardShadow: Float = 14f

/** Le rayon réellement appliqué, en dp (0 = pas de flou, la surface est en repli). */
fun glassBlurDp(surface: GlassSurface, level: GlassLevel): Float = surface.blurDp * level.blurScale

/**
 * L'opacité de la teinte **nominative** — celle de la spec : blanc 20 % en clair, anthracite 30 %
 * en sombre. C'est l'identité du verre ; [glassTintAlpha] peut la remonter.
 */
fun glassNominalTint(dark: Boolean): Float = if (dark) GlassTintDark else GlassTintLight

/** La couleur de la teinte : blanc en clair, anthracite en sombre. */
fun glassTintColour(dark: Boolean): Color = if (dark) GlassAnthracite else Color.White

/**
 * Le **plancher de lisibilité** de la teinte : la plus petite opacité (à partir de [from]) qui tient
 * le seuil pour **toutes** les encres de la surface, avec **les deux aplats extrêmes** derrière —
 * blanc pur et noir pur. C'est le pire cas honnête de [Frost] : le flou ne change pas la couleur
 * moyenne d'un aplat, il ne fait que rapprocher le fond de son voisinage.
 *
 * Le balayage est déterministe (pas de dichotomie : le rapport n'est pas monotone en α, une teinte
 * anthracite sur du blanc passe par un minimum) et c'est **la même fonction** qui décide à l'écran
 * et qui remplit le tableau du rapport — le chiffre annoncé et le chiffre vérifié ne peuvent pas
 * diverger.
 */
fun glassLegibilityFloor(
    tint: Color,
    inks: List<Color>,
    minRatio: Float,
    from: Float = 0f,
): Float {
    if (inks.isEmpty()) return from.coerceIn(0f, 1f)
    val target = minRatio + LegibilityMargin
    var alpha = from.coerceIn(0f, 1f)
    while (alpha <= 1f) {
        val veil = tint.copy(alpha = alpha)
        val ok = inks.all { ink ->
            val onLight = compositedBackdrop(veil, Color.White)
            val onDark = compositedBackdrop(veil, Color.Black)
            OnyxContrast.ratio(ink, onLight) >= target && OnyxContrast.ratio(ink, onDark) >= target
        }
        if (ok) return alpha
        alpha += LegibilityStep
    }
    return 1f
}

/**
 * Le meilleur rapport qu'une encre **dérivée** peut atteindre sur ce qui passe sous la vitre : le noir
 * ou le blanc, celui des deux qui se détache le mieux du fond composé.
 *
 * C'est la seule mesure honnête pour une surface dont l'encre est choisie
 * ([GlassInk.DERIVED]) : exiger une teinte **fixe** obligerait le voile à devenir opaque.
 */
fun bestDerivedInkRatio(veil: Color, under: Color): Float {
    val backdrop = compositedBackdrop(veil, under)
    return maxOf(OnyxContrast.ratio(Color.Black, backdrop), OnyxContrast.ratio(Color.White, backdrop))
}

/**
 * Le **plancher de lisibilité d'une surface à encre dérivée** : la plus petite opacité (à partir de
 * [from]) qui laisse **une encre lisible possible** — le noir ou le blanc — devant les **deux aplats
 * extrêmes** (blanc pur et noir pur).
 *
 * Le chiffre qui en sort est le cœur de la correction : sur la barre d'onglets en clair, le plancher
 * d'une encre **fixe** montait à **0,67** (la vitre devenait un aplat de blanc), celui-ci s'arrête à
 * **0,48** — la vitre laisse passer 52 % du contenu, et les libellés restent lisibles parce que la
 * barre **choisit** leur encre contre ce voile-là (`readableInk`).
 */
fun glassDerivedFloor(
    tint: Color,
    minRatio: Float,
    from: Float = 0f,
): Float {
    val target = minRatio + LegibilityMargin
    var alpha = from.coerceIn(0f, 1f)
    while (alpha <= 1f) {
        val veil = tint.copy(alpha = alpha)
        val ok = listOf(Color.White, Color.Black).all { under ->
            bestDerivedInkRatio(veil, under) >= target
        }
        if (ok) return alpha
        alpha += LegibilityStep
    }
    return 1f
}

/** Le pas du balayage de lisibilité : 1 % — plus fin que ce que l'œil distingue sur un voile. */
const val LegibilityStep: Float = 0.01f

/**
 * La marge au-dessus du seuil, dans le balayage du plancher.
 *
 * Même raison que `BarInk.IDLE_MARGIN` : une encre est **antialiasée**, donc le pixel de cœur
 * atteint la couleur et son voisin non. Un plancher calculé pour tomber *exactement* sur 3,00:1
 * livrerait une icône mesurée juste au ras du seuil — on vise un peu au-dessus. 0,15 est le plus
 * petit pas qui ne coûte presque rien en opacité (un point de voile) et qui sort du ras.
 */
const val LegibilityMargin: Float = 0.15f

/**
 * L'opacité **réellement appliquée** à la teinte de la vitre : la nominative, remontée au plancher de
 * lisibilité si celui-ci est plus haut, puis majorée du bonus du mode allégé.
 *
 * C'est ici que se joue « le verre ne détruit pas la lisibilité » : quand la mesure dit que 20 % de
 * blanc ne suffisent pas à porter un titre au-dessus d'une photo sombre, c'est le plancher qui
 * s'applique — pas l'inverse.
 */
fun glassTintAlpha(
    surface: GlassSurface,
    level: GlassLevel,
    dark: Boolean,
    inks: List<Color>,
): Float {
    val tint = glassTintColour(dark)
    val nominal = glassNominalTint(dark)
    val floor = when (surface.ink) {
        // Une encre **dérivée** : le voile n'a qu'à laisser *une* encre lisible possible.
        GlassInk.DERIVED -> glassDerivedFloor(tint, surface.ink.minRatio, nominal)
        else -> glassLegibilityFloor(tint, inks, surface.ink.minRatio, nominal)
    }
    return (floor + level.tintBonus).coerceIn(0f, 1f)
}

/**
 * **Le pire rapport d'encre que la surface puisse présenter**, encre dérivée comprise.
 *
 * Publique pour une raison précise : c'est **la même** mesure que `LiquidGlassTest` imprime et
 * exige. Un chiffre annoncé dans le rapport et un chiffre vérifié ailleurs divergeraient au premier
 * changement de voile — ici, il n'y a qu'un code.
 */
fun glassWorstInkRatio(surface: GlassSurface, level: GlassLevel, theme: ResolvedTheme): Float {
    val tint = glassTintColour(theme.dark)
    val alpha = glassTintAlpha(surface, level, theme.dark, glassInks(surface, theme))
    val veil = tint.copy(alpha = alpha)
    return listOf(Color.White, Color.Black).minOf { under ->
        val backdrop = compositedBackdrop(veil, under)
        when (surface.ink) {
            GlassInk.DERIVED -> maxOf(
                OnyxContrast.ratio(Color.Black, backdrop),
                OnyxContrast.ratio(Color.White, backdrop),
            )
            else -> glassInks(surface, theme).minOf { OnyxContrast.ratio(it, backdrop) }
        }
    }
}

/**
 * Le voile du **repli**, celui que Haze dessine quand le flou est refusé, et celui que la vitre
 * peint elle-même quand il n'y a rien à échantillonner.
 *
 * Il est **plus opaque** que le verre, et pas par prudence : sans flou, une ligne de texte noir passe
 * entière sous l'encre de la barre, alors qu'un flou de 36 dp la noie dans la moyenne de son
 * voisinage. Le plancher de lisibilité est donc relevé de [ScrimBonus], et jamais sous
 * [GlassScrimMinLight] / [GlassScrimMinDark].
 */
fun glassScrimAlpha(
    surface: GlassSurface,
    dark: Boolean,
    inks: List<Color>,
): Float {
    val tint = glassTintColour(dark)
    val nominal = glassNominalTint(dark)
    val floor = glassLegibilityFloor(tint, inks, surface.ink.minRatio, nominal)
    val minimum = if (dark) GlassScrimMinDark else GlassScrimMinLight
    return (floor + ScrimBonus).coerceIn(minimum, 1f)
}

/** Ce que le repli ajoute au plancher de lisibilité. */
const val ScrimBonus: Float = 0.20f

/**
 * **Le rapport de contraste au-dessus duquel le contenu qui passe sous une vitre est encore
 * lisible.** C'est le seuil du **masquage** (et non celui de l'encre posée *sur* la vitre) : 2,5:1
 * est bien en dessous du seuil du texte (4,5:1, [OnyxContrast.TEXT_MIN]) comme de celui d'un grand
 * libellé ou d'une icône (3:1, [OnyxContrast.ICON_MIN]) — un fil à ce niveau ne se lit plus.
 */
const val MaskedContentMaxRatio: Float = 2.5f

/**
 * **Le plancher de masquage** : la plus petite opacité de voile à partir de laquelle le contenu qui
 * passe **derrière** la vitre n'est plus lisible à travers elle.
 *
 * Le défaut relevé par le propriétaire — « le texte de la conversation passe À TRAVERS et AUTOUR de
 * la pastille et de la plaque » — n'était pas un manque de flou : c'était un **voile trop mince**.
 * Une vitre mince est l'identité du verre liquide (le contenu reste visible), mais une surface
 * **flottante au-dessus du fil** doit le **masquer** — du texte lisible dans la plaque n'est plus du
 * verre, c'est une fuite.
 *
 * La mesure porte sur ce qui se compose **réellement** : l'encre du fil ([ink]) et son fond ([page]),
 * chacun recouverts du voile ([tint] à l'opacité balayée). L'opacité rendue amène leur contraste à
 * [maxRatio] ou en dessous — le fil cesse d'être lisible. Le balayage est déterministe (le rapport
 * n'est pas monotone en α sur une teinte colorée), et c'est **la même fonction** qui décide à l'écran
 * et qui remplit le tableau du rapport.
 */
fun glassMaskingFloor(
    tint: Color,
    ink: Color,
    page: Color,
    maxRatio: Float = MaskedContentMaxRatio,
): Float {
    var alpha = 0f
    while (alpha <= 1f) {
        val veil = tint.copy(alpha = alpha)
        val text = compositedBackdrop(veil, ink)
        val back = compositedBackdrop(veil, page)
        if (OnyxContrast.ratio(text, back) <= maxRatio) return alpha
        alpha += LegibilityStep
    }
    return 1f
}

/**
 * Le **voile peint d'une surface qui masque** : le plus haut de la teinte nominative et du plancher
 * de masquage ([glassMaskingFloor]). Fonction nommée pour que le rapport et l'écran lisent le même
 * chiffre.
 */
fun glassMaskingTintAlpha(
    tint: Color,
    nominal: Float,
    ink: Color,
    page: Color,
): Float = maxOf(nominal, glassMaskingFloor(tint, ink, page))

/** Le voile du repli : la vitre teintée, posée **sur la page** — jamais du blanc pur. */
fun glassScrimColour(theme: ResolvedTheme): Color = (theme.pageColor)

/** Les encres d'une surface, telles qu'elles sont posées dessus. */
fun glassInks(surface: GlassSurface, theme: ResolvedTheme): List<Color> = when (surface.ink) {
    // Ce qui se lit sur la barre : le titre, le sous-titre — les deux au seuil du texte. Le plus
    // faible des deux décide, donc on les passe tous les deux.
    GlassInk.TEXT -> listOf(theme.textColor, theme.secondaryText)

    // Une icône ou un chiffre : le seuil des icônes, sur l'encre pleine comme sur l'accent.
    GlassInk.ICON -> listOf(theme.textColor)

    // Une encre dérivée : la surface choisit elle-même la sienne contre le voile composé. Cette liste
    // ne sert donc qu'au **repli** ([glassScrimAlpha]), qui garde la règle conservatrice du texte.
    GlassInk.DERIVED -> listOf(theme.textColor, theme.secondaryText)
}

// ---------------------------------------------------------------------------------------------
// Le verre
// ---------------------------------------------------------------------------------------------

/** L'étiquette du journal du verre : c'est là que se lit le passage en mode allégé. */
const val GlassLogTag: String = "OnyxGlass"

/** L'état Haze de l'écran, posé par la racine ; `null` hors de l'arbre qui l'a posé (aperçus, tests). */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/** L'échantillonneur de fluidité de l'arbre ; `null` hors de l'arbre qui l'a posé. */
val LocalGlassFluidity = staticCompositionLocalOf<GlassFluidity?> { null }

/**
 * Une vitre, posée **derrière** le contenu de la surface qu'elle habille.
 *
 * À utiliser en bout de chaîne de modificateurs, sur la racine de la surface :
 *
 * ```
 * Row(modifier = Modifier.fillMaxWidth().liquidGlass(GlassSurface.THREAD_TOP_BAR)) { … }
 * ```
 *
 * Le contenu de la surface est dessiné **au-dessus** de la vitre : l'encre reste nette alors que le
 * fond bouge. Le liseré suit la forme ([shape]) et ne se dessine jamais comme un rectangle à coins
 * carrés sous un clip arrondi.
 *
 * [shape] **vaut la forme de la surface par défaut** ([GlassSurface.shape]) : la vitre reçoit donc
 * toujours une forme arrondie, et un appelant qui l'omet ne peut pas fabriquer un rectangle franc —
 * c'est exactement le défaut « les bords ne sont pas arrondis » que la table a fermé. On ne la
 * remplace que pour un cas particulier, jamais par un `RectangleShape` de commodité.
 */
fun Modifier.liquidGlass(
    surface: GlassSurface,
    shape: Shape = surface.shape,
    state: HazeState? = null,
    /**
     * Le verre réagit-il au doigt ? `true` par défaut. Un appelant peut l'éteindre là où l'appui
     * n'aurait pas de sens (une vitre purement décorative, un aperçu), et le réglage global
     * ([LocalGlassHalo]) l'emporte sur tout le reste.
     */
    halo: Boolean = true,
    /**
     * **La vitre doit-elle MASQUER ce qu'elle recouvre ?** `false` par défaut — le verre mince est
     * l'identité de l'app, et la plupart des surfaces ne recouvrent rien de lisible.
     *
     * `true` pour les surfaces **flottantes au-dessus du fil** (la plaque du bas, qui porte le
     * compositeur et les onglets sur du texte qui défile) : le voile peint est alors remonté au
     * **plancher de masquage** ([glassMaskingFloor]) — l'opacité à partir de laquelle le contenu
     * dessous n'est **plus lisible** à travers. C'est le défaut relevé (« le texte passe à travers
     * et autour de la pastille et de la plaque ») : la vitre mince laissait lire le fil **dans** la
     * plaque. Le **flou** et la **finition** (lumière de tête, liseré, reflet de tranche) ne
     * changent pas : le verre reste du verre.
     */
    masking: Boolean = false,
): Modifier = composed {
    val theme = LocalOnyxTheme.current
    // L'état Haze vient de l'écran qui a posé la source ; explicitement quand l'appelant le donne
    // (une surface posée hors de l'arbre de l'écran), sinon celui de la racine la plus proche.
    val source = state ?: LocalHazeState.current
    val fluidity = LocalGlassFluidity.current
    // **L'opacité et le flou peints ne dépendent plus d'aucune mesure** : le palier de peinture est
    // constant ([glassPaintLevel]). La mesure ne sert plus qu'au budget du halo ([GlassFluidity]).
    // C'est ce qui rend l'apparence **indépendante du temps** : ni le flou ni la teinte ne peuvent
    // plus changer parce que la cadence a bougé — et le repli ne peut plus éteindre le flou.
    val level = glassPaintLevel
    val dark = theme.dark
    val inks = glassInks(surface, theme)
    val tint = glassTintColour(dark)
    val nominalTintAlpha = glassPaintTintAlpha(surface, dark, inks)
    val scrim = tint.copy(alpha = glassScrimAlpha(surface, dark, inks))
    val blurDp = glassPaintBlurDp(surface)
    val shadowDp = glassPaintShadowDp(surface)

    // ---- Le verre répond au doigt, **sans lumière** (voir `GlassTouch.kt`) ---------------------
    //
    // La lumière au contact — tache spéculaire et contour illuminé — a été **retirée partout** :
    // aucune vitre ne s'éclaire plus sous le doigt. Ce qui reste est un appui **non lumineux**, et un
    // seul chiffre le porte : les petites surfaces s'enfoncent (`GlassPressScale`), les grandes
    // s'assombrissent (`GlassPressShade`). Le réglage « Halo », « Réduire les animations », le budget
    // mesuré et le défilement l'éteignent tous les quatre.
    val pressEnabled = halo && LocalGlassHalo.current
    // **L'appui se fige pendant un défilement** : un doigt qui fait défiler n'est pas un appui, donc
    // aucune vitre ne réagit sous lui — l'effet ne suit pas le contenu. **Ce signal ne touche plus à
    // la peinture du verre** : il ne coupe que la *réponse au doigt* (enfoncement, assombrissement),
    // qui ne peint ni lumière ni voile. Le gel du flou qu'il portait a été retiré (voir plus bas).
    val scrolling = LocalGlassScrollFreeze.current
    val reduceMotion = LocalReduceMotion.current
    val pressState = rememberGlassPressState()
    val pressFade by animateFloatAsState(
        targetValue = if (pressState.pressed) 1f else 0f,
        animationSpec = MotionFamily.Press.animated(),
        label = "glassPressFade",
    )
    val pressStrength = glassPressStrength(
        enabled = pressEnabled,
        reduceMotion = reduceMotion,
        fluidityAllows = fluidity?.pressAllowed ?: true,
        pressed = pressFade,
        scrolling = scrolling,
    )
    // **L'appui, jamais une lumière.** Les petites surfaces (tuile, rangée, capsule) s'enfoncent —
    // un pour cent et demi d'échelle sur une pastille de 40 dp ne se voit pas dans son texte ; sur la
    // carte d'un compositeur, si, et elles ne s'enfoncent donc pas. Les grandes (barres, panneaux)
    // prennent à la place une **ombre** très légère dans la vitre (`GlassPressShade`), sous l'encre.
    val pressScale = if (surface.scalesOnPress) {
        glassPressScale(state = pressState, enabled = pressEnabled, reduceMotion = reduceMotion)
    } else {
        1f
    }
    val pressShade = if (surface.scalesOnPress) 0f else GlassPressShade * pressStrength
    // **Le voile peint** : nominal, ou remonté au **plancher de masquage** quand la vitre recouvre
    // du texte qui ne doit pas se lire au travers ([masking]). Le flou ([blurDp]) et la finition ne
    // changent pas : le verre reste du verre, il cesse simplement de laisser lire le fil.
    //
    // **Le plancher de masquage est celui du contenu FLUTÉ** ([glassMaskingFloorBlurred]) : derrière
    // une plaque, le fil traverse un flou de 36 à 50 dp, qui dilue un fût de lettre dans un disque de
    // 95 à 130 px. Mesurer le masquage sur le texte **net** — c'est ce que faisait la première
    // version — revient à ignorer le flou, donc à monter l'opacité jusqu'à l'aplat : la plaque
    // cessait d'être une vitre (défaut relevé par le propriétaire : « il manque du liquid glass »).
    // La règle « on ne lit pas le fil au travers » reste tenue, mais **par le flou**, et elle est
    // vérifiée au pixel sur l'appareil (voir le rapport du lot).
    val density = LocalDensity.current.density
    val tintAlpha = if (masking) {
        glassMaskingTintAlphaBlurred(
            tint = tint,
            nominal = nominalTintAlpha,
            ink = theme.textColor,
            page = theme.pageColor,
            blurPx = blurDp * density,
        )
    } else {
        nominalTintAlpha
    }

    // **La parallaxe** du reflet de tête : la lumière d'une vitre qui se déplace glisse un peu moins
    // vite qu'elle. La position est arrondie au pas de 8 dp — un reflet qui redemanderait une trame
    // pour un huitième de pixel coûterait plus cher que l'effet qu'il rend.
    var hereY by remember { mutableFloatStateOf(0f) }
    var parallaxDp by remember { mutableFloatStateOf(0f) }

    // **Y a-t-il quelque chose à échantillonner ?** Haze ne dessine son effet que s'il connaît une
    // source : sans elle, il laisse la vitre telle quelle. Le compte des sources ([GlassSourcePresence],
    // tenu par [onyxHazeSource]) est un **état de snapshot** : la vitre se rallume d'elle-même dès que
    // la source est posée, et on peint le voile nous-mêmes tant qu'il n'y a rien à voir.
    // **Le geste ne touche plus à la peinture du verre.** Le gel de défilement plafonnait le flou
    // ici : tant qu'une liste bougeait, la vitre prenait le **voile de repli** ([GlassTier.VEIL],
    // flou nul, voile de 0,86) au lieu de son verre, puis retrouvait son flou au repos. Son motif —
    // « échantillonner du contenu en mouvement repeint un flou neuf à chaque image » — avait été
    // écrit quand la vitre portait une **lumière de toucher** qui glissait avec le doigt ; cette
    // lumière a été retirée partout (`1.0.0+706`), et le gel n'a plus d'objet : il n'abaissait plus
    // la qualité du verre sans rien corriger. Le palier peint est donc **le même au repos et en
    // mouvement** — seule compte la présence d'une source à échantillonner ([sampling]), qui ne
    // dépend pas du geste.
    //
    // **Et ce que la surface peut échantillonner compte aussi** ([GlassSampling]) : une source posée
    // dans la fenêtre de l'app n'alimente pas un panneau qui vit dans **sa propre** fenêtre (une
    // feuille, un menu ancré, une alerte), ni une surface posée sur un aplat (le contrôle segmenté).
    // Ces deux familles prenaient pourtant le chemin de la réfraction : Haze y dessinait un effet
    // **sans rien à lire** — un calque payé pour rien, et une vitre qui se croit alimentée. Elles
    // prennent maintenant le chemin du voile, et c'est la table qui le dit, pas l'appelant.
    val sampling = source != null &&
        surface.sampling == GlassSampling.IN_SCREEN &&
        (LocalGlassSourcePresence.current?.count ?: 0) > 0
    val tier = glassTierNow
    val page = theme.pageColor

    // **Ce que la vitre fait, elle le dit.** Deux surfaces ne suffisent pas à raconter un écran, mais
    // elles répondent à la seule question que le propriétaire ne pouvait pas trancher seul : « quel
    // palier tourne sur MON appareil, et combien de surfaces échantillonnent vraiment ? » — les
    // Réglages les affichent ([GlassDiagnostics]).
    DisposableEffect(fluidity, sampling) {
        if (sampling) {
            fluidity?.acquire()
            GlassDiagnostics.enter(tier)
        }
        onDispose {
            if (sampling) {
                fluidity?.release()
                GlassDiagnostics.leave()
            }
        }
    }

    this
        .graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
        }
        .onGloballyPositioned { coords ->
            val stepPx = 8f * density
            if (stepPx <= 0f) return@onGloballyPositioned
            val quantized = (coords.positionInRoot().y / stepPx).toInt() * stepPx
            // Arrondi au pas : le reflet ne bouge que par paliers, donc il ne redemande pas une trame
            // pour un pixel de déplacement.
            if (quantized != hereY) {
                hereY = quantized
                parallaxDp = glassParallaxDp(quantized, density)
            }
        }
        .glassPress(pressState)
        .shadow(shadowDp.dp, shape, clip = false)
        .clip(shape)
        .then(
            if (sampling) {
                Modifier.glassEffect(
                    state = source,
                    tier = tier,
                    shape = shape,
                    blurDp = blurDp,
                    tint = tint,
                    tintAlpha = tintAlpha,
                    scrim = scrim,
                    page = page,
                    level = level,
                    reduceMotion = reduceMotion,
                )
            } else {
                // Le repli : une surface légèrement opaque, jamais un trou.
                Modifier.drawWithContent {
                    drawRect(color = scrim)
                    drawContent()
                }
            },
        )
        .drawWithContent {
            // La profondeur est peinte **avant** le contenu : la lumière de tête, l'ombre de pied,
            // le liseré interne et le reflet de tranche sont dans la vitre, sous l'encre.
            // **La lumière de tête ne dépend plus du geste** : le reflet reprend sa parallaxe, celle
            // qui suit la **position de la surface** dans l'écran — jamais un doigt. Le gel de
            // défilement l'éteignait pour qu'elle ne « suive » pas le contenu ; ce motif est mort
            // avec la lumière de toucher, et l'arête redevient constante.
            //
            // **Les grandes barres rétrécissent leur reflet de tranche** ([GlassEdgeThinDp]) : sur une
            // surface de 100 dp, les deux filets du verre se lisaient comme une bague en double — le
            // défaut relevé sur la capture du propriétaire. Une petite pastille garde le reflet plein :
            // c'est la distance entre les deux filets, pas le filet, qui fait la bague.
            if (surface.plateEdge) {
                // **L'arête de la plaque** : le jeton `--gedge` de la planche, peint sur le bord haut.
                // C'est la seule pièce de profondeur que les trois plaques flottantes portent (le lot
                // de la matière leur a retiré halo, liseré et reflet, sur la mesure de la planche) —
                // et c'est précisément ce qui manquait au propriétaire : « il manque du liquid glass,
                // c'est pas assez prononcé ».
                drawGlassPlateEdge(shape, dark)
            }
            if (surface.lit) {
                if (surface.blurDp >= GlassBarBlurMinDp) {
                    drawGlassFinish(
                        shape,
                        dark,
                        edgeDp = GlassEdgeThinDp,
                        edgeAlpha = GlassEdgeThinAlpha,
                        sheenShift = parallaxDp,
                    )
                } else {
                    drawGlassFinish(shape, dark, sheenShift = parallaxDp)
                }
            }
            // **L'appui non lumineux**, au-dessus de la profondeur et toujours sous l'encre : une
            // ombre très légère, jamais un éclat. Le halo de doigt n'est peint nulle part.
            if (pressShade > 0f) {
                drawRect(color = Color.Black.copy(alpha = pressShade), size = size)
            }
            drawContent()
        }
}

/**
 * **La plaque de verre d'un onglet** — la surface qui porte le contenu d'un onglet de conversation et
 * **glisse avec lui** sous le doigt.
 *
 * Elle est posée dans la page du pageur ([ChatScreen]), donc c'est le pageur qui la translate pendant
 * le glissement : son arête reste visible **à mi-course**, ce qui est la demande du propriétaire
 * (« une plaque en verre que je peux faire glisser de gauche a droite »). Elle reprend la matière de
 * la plaque du bas : le rayon des panneaux ([PlaqueGeometry.Radius], 28 dp), la marge d'écran de la
 * maison ([PlaqueGeometry.SideMargin], 16 dp), le filet de la maison ([OnyxSurfaces.hairline]) —
 * donc elle se lit comme la même famille, et non comme une carte de contenu de plus.
 */
fun Modifier.panePlate(theme: ResolvedTheme): Modifier {
    val shape = GlassSurface.PANE.shape
    return this
        .padding(horizontal = PlaqueGeometry.SideMargin)
        .clip(shape)
        .border(PlaqueGeometry.Outline, OnyxSurfaces.hairline(theme), shape)
        .liquidGlass(GlassSurface.PANE, shape)
}

/**
 * L'effet Haze lui-même, **au palier de la plateforme**.
 *
 * Trois chemins, et un seul est pris :
 *
 *  - [GlassTier.REFRACTION] — [hazeGlass] : le contenu échantillonné est **déplacé** selon le profil
 *    d'arête, le liseré d'arête et le reflet spéculaire sont dessinés par le même passage, et la
 *    dispersion chromatique est réglée au minimum lisible.
 *  - [GlassTier.BLUR] — [hazeBlur] : le flou, la teinte, le bruit, et le voile de repli que Haze
 *    dessine quand la plateforme refuse le flou. Le contenu n'est **pas** déplacé ; le liseré
 *    d'arête, lui, reste — il est peint par [drawGlassFinish], sous l'encre.
 *  - [GlassTier.VEIL] — aucun des deux : on n'arrive pas ici (voir [liquidGlass], qui peint le voile
 *    lui-même), mais le `when` est **exhaustif** : ajouter un palier sans lui donner de chemin ne
 *    compile pas.
 *
 * La teinte est posée **par-dessus** le contenu échantillonné — c'est elle qui fait la vitre, et son
 * opacité vient de la mesure ([glassTintAlpha]), pas d'une valeur écrite ici.
 *
 * **« Réduire les animations »** : le verre reste, mais sa lumière se fige. Haze accepte une
 * politique explicite ([GlassReducedMotionPolicy.Reduced]) ; on la lui donne plutôt que d'espérer
 * qu'il lise la même échelle de durée que nous.
 */
@OptIn(ExperimentalHazeApi::class)
private fun Modifier.glassEffect(
    state: HazeState,
    tier: GlassTier,
    shape: Shape,
    blurDp: Float,
    tint: Color,
    tintAlpha: Float,
    scrim: Color,
    page: Color,
    level: GlassLevel,
    reduceMotion: Boolean,
): Modifier = composed {
    // La source de l'écran : le contenu qui défile sous la vitre.
    val input = HazeInput.Sources(state)
    when (tier) {
        GlassTier.REFRACTION -> this.hazeGlass(
            input = input,
            style = glassRefractionStyle(shape, blurDp, tint, tintAlpha, page, level),
            interactionReducedMotionPolicy = if (reduceMotion) {
                GlassReducedMotionPolicy.Reduced
            } else {
                GlassReducedMotionPolicy.System
            },
        )

        GlassTier.BLUR -> this.hazeBlur(
            input = input,
            style = glassBlurStyle(blurDp, tint, tintAlpha, scrim, page, level),
        )

        GlassTier.VEIL -> this
    }
}

/**
 * Le **style du verre complet** : réfraction, arête, dispersion, reflet.
 *
 * Il part du style `regular` de Haze — le matériau de référence, déjà calibré pour l'éclairage — et
 * n'en change que ce que la surface décide : la forme, le flou (le nôtre, 36 à 50 dp), la teinte
 * mesurée de la maison, et la **bande de réfraction**, exprimée depuis le bord de la forme.
 */
@OptIn(ExperimentalHazeApi::class)
private fun glassRefractionStyle(
    surfaceShape: Shape,
    blurDp: Float,
    tint: Color,
    tintAlpha: Float,
    page: Color,
    level: GlassLevel,
): GlassStyle = GlassStyle.regular.then {
    // Le verre prend la **forme de la surface** : sans ça, sa réfraction suivrait un rectangle à
    // coins arrondis de 16 dp, et le liseré ne suivrait pas la pilule qu'il habille.
    if (surfaceShape is RoundedCornerShape) shape(surfaceShape)
    optics(
        refractionStrength = GlassRefractionStrength,
        refractionHeightFraction = GlassRefractionHeightFraction,
        // Le déplacement **absolu**, sous le pixel : la lentille est retirée (voir la constante).
        refractionDisplacement = GlassRefractionDisplacementDp.dp,
        depth = 1f,
        blurRadius = blurDp.dp,
        refractionDetailIntensity = GlassRefractionDetailIntensity,
        // Le profil reste `Edge` — c'est lui qui pose la lumière sur l'arête — mais le déplacement
        // qu'il conduit est désormais sub-pixel : l'arête **s'allume**, elle ne courbe plus rien.
        refractionProfile = if (level.blurs) {
            RefractionProfile.Edge(GlassRefractionEdgeDp.dp)
        } else {
            RefractionProfile.Surface
        },
    )
    specularIntensity(GlassSpecularIntensity)
    edgeShadow(Color.Black.copy(alpha = GlassEdgeShadowAlpha))
    edgeSoftness(GlassEdgeSoftnessDp.dp)
    chromaticAberrationStrength(GlassChromaticAberration)
    // Le fond gagne un souffle de saturation **dans** le verre : la quatrième saveur de la référence, celle
    // qui manquait. Elle ne touche pas l'encre — le texte de la surface est peint par-dessus.
    chromaMultiplier(GlassSaturationGain)
    this.tint(tint.copy(alpha = tintAlpha))
    backgroundColor(page)
}

/**
 * **Le déplacement de réfraction réellement appliqué, en px** — la fonction que la table et le
 * verrou interrogent, pour que le chiffre annoncé et le chiffre vérifié soient le même.
 *
 * Elle est **absolue** : elle ne lit plus le flou de la surface. C'est ce qui tue la lentille —
 * un flou de 50 dp déplace exactement autant qu'un flou de 30, et jamais plus que
 * [GlassRefractionDisplacementDp].
 */
fun glassRefractionDisplacementPx(density: Float): Float = GlassRefractionDisplacementDp * density

/**
 * **Le déplacement que l'ancienne règle aurait produit, en px** — gardée pour que le rapport puisse
 * chiffrer la faute (« 27 dp, soit 74 px ») avec la **même** formule que le code qui la commettait.
 */
fun glassRefractionDisplacementRatioPx(blurDp: Float, density: Float): Float =
    blurDp * GlassRefractionDisplacementRatio * density

/**
 * Le **style du flou seul** : le flou, la teinte, le bruit, et le voile de repli.
 *
 * C'est exactement ce que la vitre faisait avant l'arrivée du verre complet, à la virgule près —
 * même flou, même bruit, même voile — donc le palier intermédiaire n'invente rien : il **conserve**.
 */
private fun glassBlurStyle(
    blurDp: Float,
    tint: Color,
    tintAlpha: Float,
    scrim: Color,
    page: Color,
    level: GlassLevel,
): HazeBlurStyle = HazeBlurStyle {
    blurEnabled(level.blurs)
    blurRadius(blurDp.dp)
    noiseFactor(GlassNoiseFactor)
    colorEffects(listOf(HazeColorEffect.tint(tint.copy(alpha = tintAlpha))))
    // Le voile que Haze dessine quand la plateforme refuse le flou : plus opaque que la vitre.
    fallbackColorEffect(HazeColorEffect.tint(scrim))
    // La page derrière la vitre : là où le contenu échantillonné est transparent, on voit la
    // page — jamais un trou.
    backgroundColor(page)
    blurredEdgeTreatment(BlurredEdgeTreatment.Rectangle)
}

/**
 * Le **composant** : la même vitre, pour les endroits où c'est un `Spacer` dans un `Box` qui la
 * porte (une feuille, un panneau, un en-tête) plutôt qu'une chaîne de modificateurs sur la racine
 * d'une barre existante. C'est le **même** dessin : [LiquidGlass] et [liquidGlass] n'ont qu'un
 * chemin de peinture, ils ne peuvent pas diverger.
 *
 * À poser en premier enfant d'un `Box`, en `matchParentSize()` — comme `Frost` :
 *
 * ```
 * Box(modifier) {
 *     LiquidGlass(GlassSurface.HEADER_CAPSULE, shape, Modifier.matchParentSize())
 *     content()
 * }
 * ```
 */
@Composable
fun LiquidGlass(
    surface: GlassSurface,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    Spacer(modifier = modifier.then(Modifier.liquidGlass(surface, shape)))
}

/**
 * **Le reflet de tranche** : le contour de la forme, rentré d'une demi-épaisseur pour que le trait
 * tienne **dans** la surface, en blanc — c'est lui qui marque l'arête de la vitre et qui la détache
 * du contenu flouté, où tout est justement devenu mou.
 */
private fun DrawScope.drawGlassEdge(
    shape: Shape,
    strokePx: Float,
    alpha: Float,
) {
    val inset = strokePx / 2f
    val inner = Size(
        width = (size.width - strokePx).coerceAtLeast(0f),
        height = (size.height - strokePx).coerceAtLeast(0f),
    )
    if (inner.width <= 0f || inner.height <= 0f) return
    val outline = shape.createOutline(inner, layoutDirection, this)
    withTransform({ translate(inset, inset) }) {
        drawOutline(
            outline = outline,
            color = Color.White.copy(alpha = alpha),
            style = Stroke(width = strokePx),
        )
    }
}

/**
 * **Le liseré interne** : le même contour que le reflet, rentré de [GlassHairlineInset] — c'est lui
 * qui donne au verre son **épaisseur**.
 *
 * Pourquoi un second filet plutôt que le seul reflet de tranche : sur une page claire, la face de la
 * vitre est exactement la page (la règle de `Frost.kt` interdit de la tirer vers le blanc), et un
 * reflet blanc sur du blanc ne se voit pas. Un filet **sombre**, lui, se voit sur une page claire ; et
 * sur une page sombre c'est l'inverse — c'est donc le contraste des deux qui garantit qu'une vitre se
 * détache dans **les deux apparences**, sans jamais dépendre d'une seule direction de lumière.
 */
private fun DrawScope.drawGlassHairline(shape: Shape, dark: Boolean) {
    val strokePx = GlassHairlineDp.toPx()
    val inset = GlassHairlineInset.toPx() + strokePx / 2f
    val inner = Size(
        width = (size.width - 2f * inset).coerceAtLeast(0f),
        height = (size.height - 2f * inset).coerceAtLeast(0f),
    )
    if (inner.width <= 0f || inner.height <= 0f) return
    val outline = shape.createOutline(inner, layoutDirection, this)
    withTransform({ translate(inset, inset) }) {
        drawOutline(
            outline = outline,
            color = if (dark) {
                Color.White.copy(alpha = GlassHairlineDark)
            } else {
                Color.Black.copy(alpha = GlassHairlineLight)
            },
            style = Stroke(width = strokePx),
        )
    }
}

/**
 * **La finition d'une vitre** : la lumière de tête, l'ombre de pied, le liseré interne et le reflet
 * de tranche — les quatre pièces de la profondeur, peintes dans cet ordre, **derrière** le contenu
 * (l'encre reste nette).
 *
 * Publique à dessein : le verre a **deux** chemins de peinture — Haze pour les surfaces qui
 * recouvrent du contenu de la même fenêtre, la fenêtre du système pour les panneaux (feuille, menu,
 * alerte) — et les deux doivent donner **la même** vitre. Deux copies de ce dessin auraient divergé
 * au premier changement, et c'est exactement ainsi qu'un verre se met à ne plus ressembler à un
 * verre sous certains panneaux.
 *
 * [edgeDp] et [edgeAlpha] sont paramétrables pour une seule raison : le liseré des panneaux à
 * fenêtre propre garde ses valeurs historiques (0,5 dp à 16 % en sombre), parce qu'un filet plus
 * épais s'y doublerait avec le contour que la fenêtre du système dessine déjà.
 */
fun DrawScope.drawGlassFinish(
    shape: Shape,
    dark: Boolean,
    edgeDp: Float = GlassEdgeDp,
    edgeAlpha: Float = GlassEdgeAlpha,
    /**
     * Le décalage de la **lumière de tête**, en dp : c'est la parallaxe d'une vitre qui se déplace
     * (voir `glassParallaxDp`). Il décale le dégradé sans déplacer la surface : la lumière glisse sur
     * le verre, elle ne le suit pas à l'identique — c'est ce qui fait qu'on la lit comme posée
     * **sur** lui.
     */
    sheenShift: Float = 0f,
) {
    if (size.width <= 0f || size.height <= 0f) return

    // 1. La lumière de tête : elle part du bord haut et s'éteint à GlassSheenStop.
    val sheen = if (dark) GlassSheenDark else GlassSheenLight
    if (sheen > 0f) {
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = sheen),
                GlassSheenStop to Color.Transparent,
                startY = sheenShift * density,
                endY = size.height + sheenShift * density,
            ),
            size = size,
        )
    }

    // 2. L'ombre de pied : le pendant exact, en sens inverse.
    val shade = if (dark) GlassShadeDark else GlassShadeLight
    if (shade > 0f) {
        drawRect(
            brush = Brush.verticalGradient(
                GlassShadeStart to Color.Transparent,
                1f to Color.Black.copy(alpha = shade),
                startY = 0f,
                endY = size.height,
            ),
            size = size,
        )
    }

    // 3. Le liseré interne, puis 4. le reflet de tranche : les deux arêtes du verre.
    drawGlassHairline(shape, dark)
    drawGlassEdge(shape, edgeDp.dp.toPx(), edgeAlpha)
}

// ---------------------------------------------------------------------------------------------
// La mesure — les chiffres du rapport sont ceux du code
// ---------------------------------------------------------------------------------------------

/** L'opacité de la lumière de tête **au bord** de la vitre : le maximum du dégradé. */
fun glassSheenAlpha(dark: Boolean): Float = if (dark) GlassSheenDark else GlassSheenLight

/**
 * **La profondeur est-elle peinte pour cette surface ?** — la même porte que `liquidGlass`.
 *
 * Publique pour que le verrou l'interroge directement : une surface `lit = false` (la plaque du bas,
 * une carte, une tuile) ne reçoit **ni lumière de tête, ni ombre de pied, ni liseré, ni filet** —
 * elle se sépare de la page par la **seule luminance** de sa teinte. Remettre `lit = true` sur une de
 * ces surfaces fait échouer le verrou en la nommant.
 */
fun glassFinishLit(surface: GlassSurface): Boolean = surface.lit

/** L'opacité de la lumière de tête **réellement peinte** pour cette surface (0 si c'est un aplat). */
fun glassSheenUsed(surface: GlassSurface, dark: Boolean): Float =
    if (surface.lit) glassSheenAlpha(dark) else 0f

/** L'opacité de l'ombre de pied **réellement peinte** pour cette surface (0 si c'est un aplat). */
fun glassShadeUsed(surface: GlassSurface, dark: Boolean): Float =
    if (surface.lit) glassShadeAlpha(dark) else 0f

/** L'ombre droite **réellement posée** sous cette surface (en dp) — 0 pour une carte/un aplat. */
fun glassShadowUsed(surface: GlassSurface): Float = glassPaintShadowDp(surface)

/** L'opacité de l'ombre de pied **au bord** de la vitre. */
fun glassShadeAlpha(dark: Boolean): Float = if (dark) GlassShadeDark else GlassShadeLight

/**
 * La **face** de la vitre telle qu'elle se mesure : la teinte que la lisibilité a retenue, composée
 * sur ce qui passe derrière ([backdrop]).
 *
 * C'est la même composition que celle du plancher de lisibilité ([compositedBackdrop]) et la même
 * teinte que celle peinte à l'écran ([glassTintAlpha]) : le chiffre du rapport ne peut donc pas
 * être un chiffre à part.
 */
fun glassFace(surface: GlassSurface, level: GlassLevel, theme: ResolvedTheme, backdrop: Color): Color =
    compositedBackdrop(
        glassTintColour(theme.dark).copy(
            alpha = glassTintAlpha(surface, level, theme.dark, glassInks(surface, theme)),
        ),
        backdrop,
    )

/** Le **haut** de la vitre : la face, plus la lumière de tête — le point le plus clair. */
fun glassTopRim(surface: GlassSurface, level: GlassLevel, theme: ResolvedTheme, backdrop: Color): Color =
    compositedBackdrop(
        Color.White.copy(alpha = glassSheenAlpha(theme.dark)),
        glassFace(surface, level, theme, backdrop),
    )

/** Le **bas** de la vitre : la face, plus l'ombre de pied — le point le plus sombre. */
fun glassBottomRim(surface: GlassSurface, level: GlassLevel, theme: ResolvedTheme, backdrop: Color): Color =
    compositedBackdrop(
        Color.Black.copy(alpha = glassShadeAlpha(theme.dark)),
        glassFace(surface, level, theme, backdrop),
    )

/**
 * **L'arête de verre d'une plaque** — l'arête claire du haut, le jeton `--gedge` de la planche
 * ([OnyxEdge.topEdge] : blanc 98 % en clair, 62 % en sombre), peinte sur [PlaqueGeometry.TopEdge]
 * (1,5 dp) et **rentrée de [GlassPlateEdgeInset]** pour ne pas déborder sur les coins arrondis.
 *
 * Elle se peint **au-dessus** de la vitre et **sous** le contenu ([drawWithContent] l'appelle avant
 * `drawContent`), donc le texte de la plaque reste net. Une seule ligne, sur le bord haut : c'est la
 * seule pièce de la profondeur que ces plaques portent (voir [GlassSurface.plateEdge]).
 */
private fun DrawScope.drawGlassPlateEdge(shape: Shape, dark: Boolean) {
    val thickness = PlaqueGeometry.TopEdge.toPx()
    if (thickness <= 0f) return
    val inset = GlassPlateEdgeInset.toPx()
    val left = inset.coerceAtMost(size.width / 2f)
    val right = size.width - left
    if (right <= left) return
    drawLine(
        color = OnyxEdge.topEdge(dark),
        start = Offset(left, thickness / 2f),
        end = Offset(right, thickness / 2f),
        strokeWidth = thickness,
        cap = StrokeCap.Round,
    )
    // `shape` est gardé dans la signature pour la lisibilité du contrat : l'arête suit le bord haut
    // de la forme, et un jour un bord non droit la fera suivre un chemin plutôt qu'une ligne.
    @Suppress("UNUSED_EXPRESSION") shape
}

/** L'encre du liseré interne : claire en sombre, sombre en clair. */
fun glassHairlineColour(dark: Boolean): Color =
    if (dark) Color.White.copy(alpha = GlassHairlineDark) else Color.Black.copy(alpha = GlassHairlineLight)

/**
 * La **détache** d'une surface : le plus grand écart qu'elle présente avec son fond, parmi la face,
 * le bord haut, le bord bas et le liseré interne.
 *
 * Un seul chiffre ne suffirait pas : en clair, la face **est** la page (par la règle du voile), donc
 * c'est le liseré qui détache ; en sombre, c'est la face. Prendre le maximum des quatre est la seule
 * mesure qui reste vraie dans les deux apparences — et c'est elle que le test exige au-dessus du
 * seuil de perceptibilité.
 */
fun glassDetachment(
    surface: GlassSurface,
    level: GlassLevel,
    theme: ResolvedTheme,
    backdrop: Color,
): Float {
    val face = glassFace(surface, level, theme, backdrop)
    val hairline = compositedBackdrop(glassHairlineColour(theme.dark), face)
    return listOf(face, glassTopRim(surface, level, theme, backdrop), glassBottomRim(surface, level, theme, backdrop), hairline)
        .maxOf { OnyxContrast.ratio(it, backdrop) }
}

/**
 * La **profondeur** d'une surface : l'écart entre son bord haut et son bord bas.
 *
 * C'est le chiffre qui manquait à la capture du propriétaire : une vitre qui se détache de la page
 * mais qui est **plate** d'un bord à l'autre (1,00:1) se lit comme du plastique. Ce que le verre
 * liquide ajoute, c'est cette pente — claire en haut, sombre en bas.
 */
fun glassDepth(surface: GlassSurface, level: GlassLevel, theme: ResolvedTheme, backdrop: Color): Float =
    OnyxContrast.ratio(
        glassTopRim(surface, level, theme, backdrop),
        glassBottomRim(surface, level, theme, backdrop),
    )

/** Sous cet écart, une vitre ne se distingue pas assez de son fond pour se lire comme une vitre. */
const val GlassDetachmentMin: Float = 1.20f

/** Sous cette pente, la vitre est un aplat : elle n'a pas de profondeur. */
const val GlassDepthMin: Float = 1.18f
