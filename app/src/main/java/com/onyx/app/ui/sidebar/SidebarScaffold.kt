package com.onyx.app.ui.sidebar

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.design.MotionFamily
import com.onyx.app.ui.design.OnyxMotion
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.reducedSpec
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.design.semanticsLabel
import com.onyx.app.ui.design.glassSurface
import com.onyx.app.ui.design.sidebarPaneColor
import com.onyx.app.ui.design.sidebarPaneElevation
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import com.onyx.app.ui.design.tapLabel
import com.onyx.app.ui.localization.resolveOnyxText

/**
 * Les deux dispositions du volet, en **une seule** : le volet **posé** (large) qui rétrécit le
 * détail, et le volet en **surimpression** (étroit) qui glisse par-dessus, avec son voile.
 *
 * Rien ne demande deux gestes :
 * - un **appui** sur le contrôle de la barre du contenu ouvre et referme ([SidebarToggleControl]) ;
 * - le **retour matériel dépile le volet avant de le refermer** : sur un écran, il revient à la
 *   liste ; sur la liste, il referme la surimpression — et fermé, il ne touche à rien, le bord de
 *   l'écran reste donc au système pour remonter la pile ;
 * - le **geste du bord** ouvre la surimpression : un glissement vers la droite dans le contenu, un
 *   glissement vers la gauche pour la refermer ; un appui sur le voile fait de même.
 *
 * Le volet est un **conteneur de navigation** : une tuile y ouvre son écran (flèche de retour, titre,
 * contenu), et le pied reste en place. Sa largeur suit ce qu'il porte — la colonne de la liste
 * quand il montre sa liste, une largeur d'écran quand il porte un écran ([sidebarWidthForMode]).
 *
 * Deux panneaux **réels** : le volet porte sa tête, sa recherche, ses tuiles et ses listes, le
 * détail son propre défilement. Replié, le volet n'est même plus composé.
 */
@Composable
internal fun SidebarScaffold(
    state: OnyxSidebarState,
    sidebar: @Composable () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val mode = sidebarModeFor(maxWidth)
        // La largeur décide du mode, et le mode est écrit dans l'état : c'est ce qui donne à chaque
        // largeur **sa** mémoire.
        LaunchedEffect(mode) { state.updateMode(mode) }

        val width = sidebarWidthForMode(maxWidth, mode, content = state.isNavigating)
        val widthPx = with(LocalDensity.current) { width.toPx() }
        // La largeur de la fenêtre, lue **une fois** : les lambdas imbriquées du dessin (Box de
        // l'ombre, Box du panneau) n'ont plus le récepteur du `BoxWithConstraints` sous la main.
        val windowWidth = maxWidth

        if (mode == SidebarMode.DOCKED) {
            DockedSidebar(state = state, width = width, sidebar = sidebar, content = content)
            return@BoxWithConstraints
        }

        // ---- La surimpression ----
        val scope = rememberCoroutineScope()
        // Le volet **remplace** le fil : la fenêtre étroite n'a pas la place d'en montrer un
        // morceau, et le morceau qu'elle montrait était lu comme un second panneau (voir
        // [sidebarOverlayWidthFor]). Le fil ne glisse donc pas : seul le volet glisse, sur un voile
        // qui s'éclaircit avec lui. Glisser le fil en plus laissait ses commandes à l'arrivée au
        // bord droit — une seconde copie apparente du volet.
        val pushesContent = width < windowWidth
        val progress = remember { Animatable(if (state.isOpen) 1f else 0f) }
        // La course du volet : **la famille [MotionFamily.Panel]** — une courbe, une durée, quel
        // que soit le chemin (ouverture, fermeture, retour après un glissement avorté). C'était
        // `SidePanel` (0,32 s `easeInOut`), écrit à la main ; c'est désormais le même nom que celui
        // du fondu de son contenu, juste en dessous : un volet qui glisse **et** dont le contenu
        // apparaît est un seul mouvement. Sous « Réduire les animations », `snap()`.
        val slideSpec = MotionFamily.Panel.animated<Float>()
        val contentFade by animateFloatAsState(
            targetValue = if (state.isOpen) 1f else 0f,
            animationSpec = MotionFamily.Panel.animated<Float>(),
            label = "drawerContentFade",
        )
        LaunchedEffect(state.isOpen, widthPx) {
            progress.animateTo(
                targetValue = if (state.isOpen) 1f else 0f,
                animationSpec = slideSpec,
            )
        }
        val drag = rememberDraggableState { delta ->
            scope.launch { progress.snapTo((progress.value + delta / widthPx).coerceIn(0f, 1f)) }
        }
        // Le retour matériel **dépile d'abord le volet**, un niveau à la fois : l'écran rend la
        // place à la liste, et c'est seulement quand le volet est sur sa liste qu'un retour le
        // referme. Aucune sortie brutale — « écran → liste → fermeture », dans cet ordre.
        // Le **geste du bord** et l'**appui sur le voile** passent par la **même** [sidebarBackStep] :
        // ils font exactement ce que fait le bouton système, au lieu de fermer le volet entier d'un
        // coup en sautant l'écran qu'il porte.
        BackHandler(enabled = state.isOpen) { sidebarBackStep(state) }

        val p = progress.value
        val hidden = if (!state.isOpen && p == 0f) Modifier.clearAndSetSemantics { } else Modifier
        Box(
            modifier = Modifier
                .fillMaxSize()
                // **Le fond révélé est la page.** Le contenu est poussé de la largeur du volet ; sous
                // les coins arrondis du bord extérieur, plus rien n'est peint — et ce « rien »
                // laissait voir le **fond de fenêtre** (`onyx_background`, `#FF0E0E11`), donc deux
                // coins quasi noirs collés à la surface claire du volet (mesuré (8,8,10) contre
                // (255,255,255) sur la capture du propriétaire, en clair). Le fond de page les
                // remplit : ce qui se voit autour du volet est la page, jamais la fenêtre.
                .background(drawerGround(theme))
                .draggable(
                    state = drag,
                    orientation = Orientation.Horizontal,
                    onDragStopped = { velocity ->
                        val open = when {
                            velocity > FLING -> true
                            velocity < -FLING -> false
                            else -> progress.value > 0.5f
                        }
                        if (open) state.open() else sidebarBackStep(state)
                    },
                ),
        ) {
            // Le contenu ne glisse que s'il reste quelque chose à découvrir à côté du volet.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(if (pushesContent) (widthPx * p).roundToInt() else 0, 0) },
            ) {
                content()
            }
            if (p > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // **Le voile suit l'apparence.** Il était écrit en dur — `Color.Black` à 0,4,
                        // la même valeur dans les deux apparences : en clair, la bande révélée à
                        // droite du volet valait (153,153,153) contre une surface à (255,255,255),
                        // c'est-à-dire « du noir » pour l'œil. En clair, le voile est désormais la
                        // page tirée d'un cran vers le noir ; en sombre, c'est le voile de la maison.
                        .background(drawerVeil(theme, p))
                        // **n° 3 — l'appui sur le voile est le même retour** que le bouton système
                        // et le geste du bord : il dépile l'écran porté avant de fermer le volet.
                        .onyxClickable(onClick = { sidebarBackStep(state) })
                        // **Le voile porte son nom.** Relevé d'arbre : ce nœud cliquable plein écran
                        // n'avait ni texte ni description — un lecteur d'écran annonçait une zone
                        // muette. Son libellé dit ce que l'appui fait, comme le contrôle du volet.
                        .tapLabel(resolveOnyxText("Close list")),
                )
            }
            // Le volet prend la fenêtre entière : il touche le bord de l'écran, et ses coins du bord
            // extérieur ne s'arrondissent plus — un coin arrondi dans un coin d'écran se lit comme un
            // défaut, et le filet du bord n'a plus de bord à marquer.
            val fullBleed = width >= windowWidth
            val drawerShape = if (fullBleed) RectangleShape else DrawerShape
            Box(
                modifier = Modifier
                    .requiredWidth(width)
                    .fillMaxHeight()
                    .align(Alignment.CenterStart)
                    .offset { IntOffset((-widthPx * (1f - p)).roundToInt(), 0) }
                    // Le **fondu du contenu**, dans la même famille que le glissement : les deux
                    // portent la même durée, donc le contenu ne se pose jamais avant ni après le
                    // panneau. Le fondu est porté par le panneau entier — donc par sa vitre aussi.
                    .graphicsLayer { alpha = contentFade }
                    // **L'ombre douce, en clair (n° 4).** Elle est posée **avant tout `clip`** : un
                    // `graphicsLayer` d'ombre dessine hors de ses bornes, donc c'est cet ordre qui
                    // la laisse dépasser du panneau. Et l'ombre se **tait en sombre** — le verre y
                    // porte déjà la sienne, une seconde l'épaissirait au lieu de l'adoucir.
                    .shadow(sidebarPaneElevation(theme), drawerShape, clip = false)
                    // Le volet est **une seule copie** : sa forme borne ce qu'il peint, si bien que
                    // ni le glissement ni le fondu ne laissent une image rémanente à côté de lui.
                    .clipToBounds()
                    .clip(drawerShape)
                    // **La surface du panneau (n° 4)** : quasi blanche en clair, la vitre en sombre.
                    // Même source que le fond du volet ([sidebarPaneColor]) — jamais un cran d'écart
                    // entre le panneau et la coquille qui le porte.
                    .background(sidebarPaneColor(theme), drawerShape)
                    .drawBehind {
                        // À ras bord (n° 3, pleine largeur), il n'y a **pas de panneau voisin** à
                        // séparer : aucun filet — un filet au bord de l'écran se lirait comme un
                        // liseré sombre de plus. Sur une feuille arrondie (fenêtre plus large qu'un
                        // panneau), le filet suit le rayon et s'arrête aux coins.
                        if (fullBleed) return@drawBehind
                        // Le **liseré de séparation** : en sombre seulement (n° 4). Au clair, le
                        // panneau se détache par son ombre — un trait en plus serait exactement le
                        // contour que la référence n'a pas.
                        if (sidebarPaneElevation(theme) > 0.dp) return@drawBehind
                        val r = DRAWER_RADIUS.toPx()
                        val s = 1.dp.toPx()
                        drawLine(
                            color = theme.separator,
                            start = Offset(size.width - s / 2, r),
                            end = Offset(size.width - s / 2, size.height - r),
                            strokeWidth = s,
                        )
                    }
                    .then(hidden),
            ) {
                sidebar()
            }
        }
    }
}

/**
 * Le volet **posé** : sa largeur s'anime (jamais un saut), le détail prend le reste, et un filet
 * marque la séparation. Replié tout à fait, le volet n'est plus composé du tout.
 */
@Composable
private fun DockedSidebar(
    state: OnyxSidebarState,
    width: Dp,
    sidebar: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val target = if (state.isOpen) width else 0.dp
    val animated by androidx.compose.animation.core.animateDpAsState(
        targetValue = target,
        animationSpec = MotionFamily.Panel.animated<Dp>(),
        label = "sidebarWidth",
    )
    Row(modifier = Modifier.fillMaxSize()) {
        if (animated > 0.dp) {
            Box(
                modifier = Modifier
                    .width(animated)
                    .fillMaxHeight()
                    // **L'ombre du panneau posé**, avant le `clipToBounds` : c'est l'ordre qui lui
                    // permet de déborder à droite, sur le détail — dans l'ordre inverse, le rognage
                    // du contenu (qui existe pour l'animation de largeur) la mangerait, et le
                    // panneau se retrouverait sans aucun détachement.
                    .shadow(sidebarPaneElevation(theme), RectangleShape, clip = false)
                    .clipToBounds()
                    .drawBehind {
                        // Le filet de séparation : en sombre seulement, pour la même raison que dans
                        // la surimpression — au clair, c'est l'ombre qui sépare les deux panneaux.
                        if (sidebarPaneElevation(theme) > 0.dp) return@drawBehind
                        drawLine(
                            color = theme.separator,
                            start = Offset(size.width, 0f),
                            end = Offset(size.width, size.height),
                            strokeWidth = 0.5.dp.toPx(),
                        )
                    },
            ) {
                Box(Modifier.requiredWidth(width).fillMaxHeight().align(Alignment.CenterEnd)) { sidebar() }
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) { content() }
    }
}

/**
 * Le contrôle du volet, celui de la barre : **la même** pastille dans les deux largeurs, et **un
 * seul** appui suffit. Le libellé d'accessibilité dit ce que le prochain appui fera (« Replier la
 * liste » / « Déplier la liste ») ; l'icône ne change pas.
 *
 * L'icône est **au jeu de la barre** (`ic_sym_sidebar_left` : grille 24, trait 2, encre 2 à 22,
 * centrée) et prend l'**encre du texte**, pas l'accent : peinte en bleu sur la vitre, elle se lisait
 * comme un rectangle avec une barre de couleur — le propriétaire l'a décrite ainsi. Une encre neutre
 * à 20 dp, dans un rond de verre de 38 dp, se lit comme un contrôle de barre et rien d'autre.
 */
@Composable
internal fun SidebarToggleControl(
    open: Boolean,
    onClick: () -> Unit,
    size: Dp = 38.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .glassSurface(androidx.compose.foundation.shape.CircleShape)
            .onyxClickable(onClick = onClick)
            .semanticsLabel(if (open) "Collapse list" else "Expand list"),
        contentAlignment = Alignment.Center,
    ) {
        val theme = LocalOnyxTheme.current
        OnyxIcon(symbol = Symbols.SidebarLeft, tint = theme.textColor, size = 20.dp)
    }
}

private const val FLING = 800f

private val DRAWER_RADIUS = 28.dp
private val DrawerShape = RoundedCornerShape(topEnd = DRAWER_RADIUS, bottomEnd = DRAWER_RADIUS)
