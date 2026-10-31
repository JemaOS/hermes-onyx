package com.onyx.app.ui.design

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Box

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme

/**
 * Le **verre liquide** des surfaces : une vitre posée au-dessus du contenu, qui laisse voir ce qui
 * passe derrière — flouté pour de vrai quand la version du système le permet, et un voile
 * translucent opaque sinon.
 *
 * Pourquoi un fichier à part : l'habillage existant (dégradé translucent + liseré) décrit la
 * **forme** d'une pastille, pas une **surface traversée par du contenu**. Une barre fixe se juge sur
 * ce qui la traverse, donc elle a besoin de trois choses que l'ancien habillage n'avait pas : ce
 * qu'il y a **derrière** (un calque enregistré du contenu), un **flou** appliqué à ce calque, et une
 * **règle de repli** quand le flou coûte trop cher ou n'existe pas.
 *
 * Le tout est piloté par le thème (clair/sombre) et **figé** sous « Réduire les animations ».
 */

// ---------------------------------------------------------------------------------------------
// Les surfaces traitées
// ---------------------------------------------------------------------------------------------

/**
 * Une surface du verre : son rayon de flou (12 à 24 dp), son voile, son liseré et son ombre. Les
 * valeurs sont **par surface** parce qu'une barre fine et un panneau de menu ne se lisent pas de la
 * même distance : le panneau se permet un flou plus large, la barre non (elle porte du texte de
 * 12 à 17 points).
 */
enum class FrostSurface(
    /** Rayon de flou, en dp, à pleine qualité. Contraint à 12–24 par le test. */
    val blurDp: Float,
    /** Opacité du voile par-dessus le contenu flouté, en sombre. */
    val veilDark: Float,
    /** Idem en clair. */
    val veilLight: Float,
    /** L'ombre douce : elle détache la vitre du contenu sans faire « carte flottante ». */
    val shadowDp: Float,
) {
    /** La barre du haut, par-dessus le fil ou la liste. */
    TOP_BAR(16f, 0.86f, 0.88f, 4f),

    /** La barre d'onglets / la barre de sélection basse. */
    BOTTOM_BAR(16f, 0.87f, 0.89f, 5f),

    /** La barre de commandes du bas : le champ d'écriture et ses boutons. */
    COMMAND_BAR(20f, 0.87f, 0.89f, 6f),

    /** Le champ d'écriture lui-même (dans la barre de commandes). */
    FIELD(14f, 0.85f, 0.87f, 2f),

    /** La barre de recherche. */
    SEARCH_FIELD(12f, 0.85f, 0.87f, 2f),

    /** Les onglets segmentés. */
    SEGMENTED(12f, 0.85f, 0.87f, 2f),

    /** Une puce de section (mention `@`, sélection, relance). */
    CHIP(12f, 0.84f, 0.86f, 3f),

    /** Un bandeau d'état (erreur, pré-remplissage, attente). */
    BANNER(14f, 0.85f, 0.87f, 3f),

    /**
     * Le panneau d'un menu ancré, posé sur la liste. Celui-là **floute pour de vrai** : un voile
     * noir le précède (le voile du menu), donc le contenu sous le panneau est déjà assombri et le
     * panneau peut rester plus transparent que les barres.
     */
    MENU(20f, 0.80f, 0.94f, 14f),

    /** Le panneau d'une feuille ou d'une alerte, même régime que le menu. */
    SHEET(24f, 0.82f, 0.95f, 18f),
}

// ---------------------------------------------------------------------------------------------
// La règle de repli — pure, donc testable
// ---------------------------------------------------------------------------------------------

/** Les trois qualités possibles du verre. */
enum class FrostLevel {
    /** Flou réel, au rayon de la surface. */
    FULL,

    /** Appareil modeste ou système sans flou d'arrière-plan : flou réduit, voile plus opaque. */
    LITE,

    /** « Réduire les animations » : pas de flou, voile opaque translucent, fondu figé. */
    OFF,
    ;

    val blurScale: Float
        get() = when (this) {
            FULL -> 1f
            LITE -> 0.5f
            OFF -> 0f
        }

    /** Ce que le voile gagne en opacité pour compenser un flou absent ou réduit. */
    val veilBonus: Float
        get() = when (this) {
            FULL -> 0f
            LITE -> 0.10f
            OFF -> 0.16f
        }
}

/**
 * La règle, **pure** : « Réduire les animations » fige le verre ; un système sans flou d'arrière-plan
 * (avant la douzième version d'Android) ou un appareil à faible mémoire passe en mode allégé ;
 * sinon pleine qualité.
 *
 * Elle est pure pour être vérifiée seule, sans appareil : c'est la décision qui décide du coût.
 */
fun frostLevel(
    reduceMotion: Boolean,
    sdkInt: Int,
    lowRam: Boolean,
): FrostLevel = when {
    reduceMotion -> FrostLevel.OFF
    sdkInt < 31 || lowRam -> FrostLevel.LITE
    else -> FrostLevel.FULL
}

/**
 * La qualité du verre sur **cet** appareil : le réglage système, la version d'Android et la mémoire.
 * Lue une fois à la racine, elle décide du coût de tout le reste.
 */
@Composable
fun rememberFrostLevel(): FrostLevel {
    val reduceMotion = LocalReduceMotion.current
    val context = LocalContext.current
    val lowRam = remember(context) {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
    }
    return remember(reduceMotion, lowRam) { frostLevel(reduceMotion, Build.VERSION.SDK_INT, lowRam) }
}

/** Le rayon réellement appliqué, en dp (0 = pas de flou). */
fun frostBlurDp(surface: FrostSurface, level: FrostLevel): Float = surface.blurDp * level.blurScale

/** L'opacité du voile réellement appliquée. */
fun frostVeilAlpha(surface: FrostSurface, level: FrostLevel, dark: Boolean): Float {
    val base = if (dark) surface.veilDark else surface.veilLight
    return (base + level.veilBonus).coerceIn(0f, 1f)
}

// ---------------------------------------------------------------------------------------------
// Le calque de fond
// ---------------------------------------------------------------------------------------------

/**
 * Le contenu tel qu'il est dessiné, gardé pour être redessiné **sous** les vitres.
 *
 * Un seul calque pour l'écran : les surfaces qui se recouvrent le partagent, et l'appareil ne
 * dessine qu'une fois ce qu'il faut flouter.
 */
class Backdrop internal constructor(
    internal val layer: GraphicsLayer,
) {

    /** L'origine du contenu enregistré, dans les coordonnées de la racine. */
    internal var origin: Offset by mutableStateOf(Offset.Zero)

    /** Vrai dès qu'une vitre a réellement dessiné le calque une fois. */
    internal var recorded: Boolean = false

    /**
     * Vrai pendant l'enregistrement du calque. Une vitre **à l'intérieur** du contenu enregistré ne
     * se redessine pas elle-même : dessiner un calque pendant qu'on l'enregistre est indéfini, et
     * c'est le genre de faute qui donne un écran noir une fois sur dix.
     */
    internal var recording: Boolean = false

    /**
     * Combien de vitres veulent voir ce qui passe derrière. À zéro, le contenu n'est **pas**
     * enregistré du tout : une app qui n'affiche aucune vitre ne paie pas le calque.
     */
    internal var wanted by mutableStateOf(0)

    internal fun acquire() {
        wanted += 1
    }

    internal fun release() {
        wanted = (wanted - 1).coerceAtLeast(0)
    }

    /** Ce qu'un appelant doit lire pour savoir s'il y a une raison d'enregistrer. */
    val isWanted: Boolean get() = wanted > 0
}

/**
 * Enregistre le contenu de la surface qui porte ce modificateur dans [backdrop], **seulement
 * quand une vitre le demande**. C'est l'optimisation qui rend le verre acceptable : sans vitre
 * visible, aucun calque n'est créé et le défilement coûte exactement ce qu'il coûtait avant.
 */
fun Modifier.recordBackdrop(backdrop: Backdrop): Modifier = composed {
    val wanted = backdrop.isWanted
    this
        .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
        .then(
            if (!wanted) {
                Modifier
            } else {
                Modifier.drawWithContent {
                    backdrop.recording = true
                    backdrop.layer.record {
                        this@drawWithContent.drawContent()
                    }
                    backdrop.recording = false
                    backdrop.recorded = true
                    drawLayer(backdrop.layer)
                }
            },
        )
}

/**
 * Le calque d'un écran. Un seul par racine : les vitres qui se recouvrent le partagent.
 *
 * Le calque lui-même vient de `rememberGraphicsLayer()` — c'est la seule fabrique publique, le
 * constructeur direct étant réservé au moteur.
 */
@Composable
fun rememberBackdrop(): Backdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { Backdrop(layer) }
}

/** Le calque de l'écran ; `null` hors de l'arbre qui l'a posé (aperçus, tests). */
val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** La qualité du verre sur cet appareil, posée à la racine. */
val LocalFrostLevel = staticCompositionLocalOf { FrostLevel.FULL }

// ---------------------------------------------------------------------------------------------
// Le dessin
// ---------------------------------------------------------------------------------------------

/**
 * Une vitre : ce qu'il faut **poser derrière** le contenu d'une surface.
 *
 * À utiliser comme premier enfant d'un `Box`, en `matchParentSize()` :
 *
 * ```
 * Box(modifier) {
 *     Frost(FrostSurface.SEARCH_FIELD, shape, Modifier.matchParentSize())
 *     content()
 * }
 * ```
 *
 * Le flou est appliqué à un calque **ne contenant que la vitre** — jamais au texte de la surface :
 * c'est ce qui garde l'encre nette alors que le fond bouge.
 */
@Composable
fun Frost(
    surface: FrostSurface,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val level = LocalFrostLevel.current
    val backdrop = LocalBackdrop.current
    val blurPx = with(LocalDensity.current) { frostBlurDp(surface, level).dp.toPx() }
    val veil = veilColour(theme.pageColor, frostVeilAlpha(surface, level, theme.dark), theme.dark)
    val edge = edgeColour(theme.dark)
    val shadowDp = surface.shadowDp * if (level == FrostLevel.OFF) 0.6f else 1f
    val live = blurPx > 0f && backdrop != null

    var here by remember { mutableStateOf(Offset.Zero) }

    // Une vitre qui floute pour de vrai demande le calque ; en sortant, elle le relâche — le
    // contenu cesse d'être enregistré dès qu'il n'y a plus rien à voir à travers.
    DisposableEffect(backdrop, live) {
        if (live) backdrop?.acquire()
        onDispose { if (live) backdrop?.release() }
    }

    Box(
        modifier
            .onGloballyPositioned { here = it.positionInRoot() }
            .shadow(shadowDp.dp, shape, clip = false)
            .clip(shape)
            .frostPaint(backdrop, live, here, blurPx, veil, edge, shape, theme.dark),
    )
}

/**
 * Le rendu complet, en une seule chaîne de modificateurs : le calque flouté, puis le voile, puis la
 * finition de vitre (lumière de tête, ombre de pied, liseré interne, reflet de tranche) — dessinés
 * derrière le contenu de la surface.
 */
private fun Modifier.frostPaint(
    backdrop: Backdrop?,
    live: Boolean,
    here: Offset,
    blurPx: Float,
    veil: Color,
    edge: Color,
    shape: Shape,
    dark: Boolean,
): Modifier {
    val blurred = if (live) {
        this.graphicsLayer { renderEffect = BlurEffect(blurPx, blurPx, TileMode.Clamp) }
    } else {
        this
    }
    return blurred.drawWithContent {
        drawFrostPane(backdrop, live, here, veil, edge, shape, dark)
        drawContent()
    }
}

/**
 * La vitre elle-même : le contenu flouté, le voile teinté, et **la même finition que les vitres de
 * la fenêtre principale** ([drawGlassFinish]).
 *
 * C'est le point de passage du lot : une feuille, un menu ou une alerte vivent dans leur propre
 * fenêtre, donc Haze ne peut pas les échantillonner — leur flou vient du système
 * (`DialogWindowBlur`). Mais une vitre qui se voit *différemment* selon son mode de flou n'est pas
 * une vitre, c'est deux styles. La profondeur est donc peinte par le **même** code, avec les valeurs
 * de liseré du panneau (0,5 dp / [edgeColour]) parce qu'un filet plus épais s'y doublerait avec le
 * contour de la fenêtre du système.
 *
 * Le liseré **suit la forme de la surface** ([shape]) et ne se dessine jamais comme un rectangle à
 * coins carrés : un rectangle tracé sous un `clip` arrondi voit ses quatre coins coupés, et quand
 * le rayon dépasse la demi-hauteur (une capsule, un champ de 45 dp à 26 dp d'arrondi) il n'en
 * survit qu'un **moignon vertical au milieu des bords gauche et droit** — le « filet parasite »
 * qui se lit à l'intérieur de la vitre. On trace donc le contour de la forme, rentré d'une
 * demi-épaisseur pour que le trait tienne **dans** la surface.
 */
private fun DrawScope.drawFrostPane(
    backdrop: Backdrop?,
    live: Boolean,
    here: Offset,
    veil: Color,
    edge: Color,
    shape: Shape,
    dark: Boolean,
) {
    val b = backdrop
    if (live && b != null && b.recorded && !b.recording) {
        withTransform({ translate(b.origin.x - here.x, b.origin.y - here.y) }) {
            drawLayer(b.layer)
        }
    }
    drawRect(color = veil, size = size)
    drawGlassFinish(shape = shape, dark = dark, edgeDp = 0.5f, edgeAlpha = edge.alpha)
}

/** Le liseré : 0,5 dp, plus clair en sombre, plus clair que la page en clair. */
internal fun edgeColour(dark: Boolean): Color =
    if (dark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.62f)

/**
 * Le voile : la couleur de page, **jamais du blanc pur** — une vitre teintée de la page garde la
 * teinte du thème (une vitre blanche sur un thème ambré fait « gris sale »).
 *
 * Deux règles, et la seconde est celle qui a coûté un aller-retour :
 *
 * 1. **En sombre** la vitre se pose d'un cran **sous** la page (elle se détache vers le noir).
 * 2. **En clair elle garde exactement la page** — elle n'est *jamais* tirée vers le blanc. Un voile
 *    plus clair que la page fait baisser le contraste de l'encre sur la vitre sous celui de la page
 *    elle-même, et surtout il **peint un bandeau plus blanc que la page** : c'est ce que le
 *    propriétaire voyait derrière le champ du compositeur en thème clair, la zone du champ étant
 *    couverte par cette vitre de bout en bout (mesuré sur « Ardoise » clair, page `#DADFE7`, où le
 *    fond du compositeur sortait à `#DCE0E8` — deux crans plus clair, sous un liseré blanc).
 *    La barre se détache par son **liseré** et son **ombre**, jamais par un aplat plus clair.
 *
 * [veilIsNeverLighterThanPage] est la règle mise en fonction : le test l'interroge pour les sept
 * thèmes, donc le chiffre annoncé et le chiffre vérifié sont le même.
 */
fun veilColour(pageColour: Color, alpha: Float, dark: Boolean): Color {
    val base = if (dark) lerp(pageColour, Color.Black, 0.25f) else pageColour
    return base.copy(alpha = alpha.coerceIn(0f, 1f))
}

/**
 * Vrai si le voile ne peut pas se lire comme un **bandeau plus blanc que la page** : il n'est pas
 * plus clair que la page, dans aucune des deux apparences.
 *
 * En sombre c'est déjà vrai de deux crans (le voile se creuse vers le noir) ; en clair c'est la
 * règle qui remplace l'ancienne teinte tirée vers le blanc.
 */
fun veilIsNeverLighterThanPage(theme: ResolvedTheme, surface: FrostSurface, level: FrostLevel): Boolean {
    val veil = veilColour(theme.pageColor, frostVeilAlpha(surface, level, theme.dark), theme.dark)
    return veil.luminance() <= theme.pageColor.luminance() + 0.0005f
}

// ---------------------------------------------------------------------------------------------
// Contraste — la partie non négociable
// ---------------------------------------------------------------------------------------------

/**
 * Le rapport de contraste WCAG 2.1 d'une encre sur un fond, calculé pour de vrai (luminance
 * relative, seuils 4,5:1 texte / 3:1 icône).
 *
 * Il sert au test **et** au rapport : c'est la même fonction qui décide si une vitre est acceptable,
 * donc le chiffre annoncé et le chiffre vérifié ne peuvent pas diverger.
 */
fun contrastRatio(ink: Color, background: Color): Double {
    val a = ink.luminance().toDouble()
    val b = background.luminance().toDouble()
    val hi = maxOf(a, b)
    val lo = minOf(a, b)
    return (hi + 0.05) / (lo + 0.05)
}

/**
 * Ce que l'on voit à travers la vitre, **dans le pire cas** : le contenu le plus clair qui peut
 * passer dessous (ou le plus sombre), flouté puis recouvert du voile.
 *
 * Le flou ne change pas la couleur moyenne ; il rapproche le fond du voisinage — donc le pire cas
 * honnête est un aplat de la couleur extrême : blanc pur sous un voile sombre, noir pur sous un
 * voile clair. On teste les deux et on garde le pire.
 */
fun compositedBackdrop(veil: Color, under: Color): Color {
    val a = veil.alpha
    return Color(
        red = veil.red * a + under.red * (1f - a),
        green = veil.green * a + under.green * (1f - a),
        blue = veil.blue * a + under.blue * (1f - a),
        alpha = 1f,
    )
}

/** Les seuils : le texte se lit, l'icône se reconnaît. */
const val TEXT_CONTRAST_MIN = 4.5
const val ICON_CONTRAST_MIN = 3.0

// ---------------------------------------------------------------------------------------------
// Le verre des surfaces qui ne recouvrent rien
// ---------------------------------------------------------------------------------------------

/**
 * Le verre d'une **surface qui ne recouvre pas de contenu défilant** : sa teinte de page, son liseré
 * de 0,5 dp et son ombre douce — sans calque, donc sans coût.
 *
 * C'est le cas des barres de cette app : elles sont **posées au-dessus** de leur page dans une
 * disposition en colonne, si bien que rien ne passe dessous. Y poser un flou coûterait un calque
 * pour flouter un aplat — exactement le « gris sale » qu'il faut éviter. Le jour où du contenu
 * passe sous une barre, elle prend `Frost` (flou réel) au lieu de ceci.
 */
fun Modifier.frostedBar(
    surface: FrostSurface,
    shape: Shape = RectangleShape,
    /**
     * La teinte de la vitre. `null` : la couleur de page, légèrement tirée vers le blanc (clair) ou
     * le noir (sombre) — le cas des barres. Un panneau de menu, lui, donne sa couleur de surface :
     * c'est elle qui porte le texte, et elle reste teintée, jamais blanche.
     */
    tint: Color? = null,
): Modifier = composed {
    val theme = LocalOnyxTheme.current
    val level = LocalFrostLevel.current
    val alpha = frostVeilAlpha(surface, level, theme.dark)
    val veil = tint?.copy(alpha = alpha) ?: veilColour(theme.pageColor, alpha, theme.dark)
    val edge = edgeColour(theme.dark)
    val shadowDp = surface.shadowDp * if (level == FrostLevel.OFF) 0.6f else 1f

    this
        .shadow(shadowDp.dp, shape, clip = false)
        .clip(shape)
        .drawWithContent {
            drawFrostPane(
                backdrop = null,
                live = false,
                here = Offset.Zero,
                veil = veil,
                edge = edge,
                shape = shape,
                dark = theme.dark,
            )
            drawContent()
        }
}

// ---------------------------------------------------------------------------------------------
// Le verre des feuilles — translucide, mais jamais illisible
// ---------------------------------------------------------------------------------------------

/**
 * L'alpha **nominal** du voile d'une feuille : translucide, pour que le contenu derrière se devine.
 *
 * Pourquoi une valeur distincte de celle des barres : une barre porte du texte de 12 à 17 points
 * **posé sur elle**, et rien ne passe dessous ; une feuille, elle, couvre presque tout l'écran et
 * c'est justement **ce qu'elle couvre** qui doit rester devinable. Un voile de barre (86 à 95 %)
 * rend la feuille opaque — c'était le défaut : une feuille fermée sur son aplat, comme une image.
 *
 * Le nominal est le point de départ, jamais la décision : [frostedSheetAlpha] le remonte jusqu'au
 * plancher de lisibilité mesuré, et c'est ce plancher qui garantit le 4,5:1 sur le pire contenu.
 */
const val SheetVeilLight: Float = 0.58f

/** Idem en sombre : la page y est presque noire, la feuille doit s'en détacher en laissant voir. */
const val SheetVeilDark: Float = 0.62f

/**
 * L'opacité du voile d'une **feuille translucide** : le nominal, **remonté** au plancher de
 * lisibilité de la couleur de surface de la feuille — la plus petite opacité qui tient 4,5:1 pour
 * l'encre du texte **sur un contenu blanc pur et sur un contenu noir pur**.
 *
 * C'est la même fonction que le verre premium ([glassLegibilityFloor]) : le chiffre annoncé dans le
 * rapport et le chiffre appliqué à l'écran sortent du même code, donc ils ne peuvent pas diverger.
 * Le voile noir qui précède la feuille ([SheetScrim]) est une marge **en plus**, jamais comptée.
 */
fun frostedSheetAlpha(surface: FrostSurface, level: FrostLevel, theme: ResolvedTheme): Float {
    val nominal = if (theme.dark) SheetVeilDark else SheetVeilLight
    val inks = listOf(theme.textColor, theme.secondaryText)
    val floor = glassLegibilityFloor(theme.surfaceColor, inks, OnyxContrast.TEXT_MIN, nominal)
    return (floor + level.veilBonus).coerceIn(0f, 1f)
}

/**
 * Le verre d'une **feuille du bas** : translucide sur ce qu'elle recouvre, avec la même finition que
 * toutes les vitres de la maison ([drawGlassFinish]) — lumière de tête, ombre de pied, liseré
 * interne, reflet de tranche.
 *
 * Le flou réel vient de la **fenêtre du système** ([DialogWindowBlur], posé par l'appelant) : une
 * feuille vit dans sa propre fenêtre, donc le calque de la fenêtre principale ne l'atteint pas.
 * C'est pour ça que la feuille n'est pas *plus* transparente qu'un verre de barre de près : ce qui
 * passe dessous est flouté par le système, et le voile — mesuré — ne fait que garantir l'encre.
 */
fun Modifier.frostedSheet(
    surface: FrostSurface,
    shape: Shape,
    tint: Color? = null,
): Modifier = composed {
    val theme = LocalOnyxTheme.current
    val level = LocalFrostLevel.current
    val alpha = frostedSheetAlpha(surface, level, theme)
    // **Une surface opaque n'est pas une vitre — et le verrou est ici, pas chez l'appelant.**
    //
    // Défaut mesuré (dialogue « Nouveau salon », apparence claire, 1,0×) : le panneau portait
    // `clip` + `background(teinte opaque)` **puis** ce modificateur. Le voile tombait bien à
    // [Color.Transparent] — la règle dite juste en dessous s'appliquait — mais la **finition de
    // vitre** ([drawGlassFinish] : lumière de tête, ombre de pied, liseré, reflet de tranche) se
    // peignait quand même **par-dessus** un fond opaque. Sur 108 points d'échantillonnage
    // répartis sur le panneau, la face allait de **201 à 255** : bande plus claire en haut, corps
    // plus gris, halo aux quatre bords — la **seconde surface**, le rectangle qui ne suit pas la
    // forme, exactement ce que le propriétaire a montré. La même chaîne **sans** `frostedSheet`
    // rend **255 partout** : l'écart tombe à **0**.
    //
    // Donc : quand la teinte est **opaque**, le modificateur **ne peint rien**. L'appelant garde
    // sa seule surface (`clip` + `background`), du bord au bord ; la profondeur vient de l'ombre
    // du socle, posée **sous** la face. Les vitres translucides (les barres, les verres réels)
    // ne sont pas touchées : elles, elles ont bien quelque chose à voiler.
    val opaque = tint != null && tint.alpha >= 1f
    if (opaque) return@composed this
    val veil = (tint ?: theme.surfaceColor).copy(alpha = alpha)
    val edge = edgeColour(theme.dark)
    val shadowDp = surface.shadowDp * if (level == FrostLevel.OFF) 0.6f else 1f

    this
        .shadow(shadowDp.dp, shape, clip = false)
        .clip(shape)
        .drawWithContent {
            drawFrostPane(
                backdrop = null,
                live = false,
                here = Offset.Zero,
                veil = veil,
                edge = edge,
                shape = shape,
                dark = theme.dark,
            )
            drawContent()
        }
}

// ---------------------------------------------------------------------------------------------
// Le flou des fenêtres de dialogue
// ---------------------------------------------------------------------------------------------

/**
 * Le flou d'arrière-plan d'une **fenêtre de dialogue** — le panneau d'un menu ancré, d'une feuille
 * ou d'une alerte.
 *
 * Ces panneaux vivent dans leur propre fenêtre : ils ne peuvent pas réutiliser le calque de la
 * fenêtre principale. La vitre demande donc au système de flouter ce qui passe **derrière la
 * fenêtre**, et retombe proprement sur le voile opaque quand le système ne le permet pas — c'est le
 * repli, il ne se voit pas comme une panne.
 */
@Composable
fun DialogWindowBlur(
    surface: FrostSurface,
    enabled: Boolean = true,
) {
    val level = LocalFrostLevel.current
    val view = LocalView.current
    val density = LocalDensity.current
    DisposableEffect(view, level, surface) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val radius = with(density) { frostBlurDp(surface, level).dp.roundToPx() }
        val supported = android.os.Build.VERSION.SDK_INT >= 31

        // **Le voile de la fenêtre est retiré ICI, et SANS condition.**
        //
        // Mesuré (dialogue « Nouveau salon », apparence claire) : la page derrière tombait de
        // L≈230 (matière « Galerie claire ») à **L=41** — soit 82 % d'assombrissement. Or
        // [HouseSheetScrim] (noir 55 %) n'en explique que la moitié (≈L 104) : le reste venait de
        // la **fenêtre de dialogue** (`dimAmount` ≈ 0,6, posé **en plus** du voile de la maison).
        // Le même empilement en sombre ne se voit pas (page déjà à L≈10) — c'est pourquoi le
        // défaut n'était visible qu'en clair.
        //
        // **Pourquoi le premier correctif n'a pas pris.** `setDimAmount(0f)` était appelé **dans**
        // le `runCatching` du flou, derrière un `return` posé quand `level == FrostLevel.OFF` :
        // sur un appareil (ou une passe) où le flou est éteint, la fonction **sortait avant de
        // retirer le voile** — le `dimAmount` restait donc, et la mesure rendait L=41 **avant comme
        // après**. La règle est donc inversée : le voile de plateforme est retiré **toujours** (il
        // n'appartient pas à la maison), et **seul le flou** reste conditionné au palier.
        // `clearFlags(FLAG_DIM_BEHIND)` en plus de `setDimAmount(0f)` : certaines versions
        // redessinent le voile dès que le drapeau est posé, quelle que soit l'amplitude.
        if (window != null) {
            runCatching {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window.setDimAmount(0f)
            }
        }

        // Le **flou** de la fenêtre, lui, suit le palier : absent quand la plateforme ne sait pas
        // flouter, ou quand le repli est demandé.
        val blur = enabled && supported && level != FrostLevel.OFF
        if (window != null && blur) {
            runCatching {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.setBackgroundBlurRadius(radius)
            }
        }
        onDispose {
            if (window != null && supported) {
                runCatching { window.setBackgroundBlurRadius(0) }
            }
        }
    }
}
