package com.onyx.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.design.GlassCircleButton
import com.onyx.app.ui.design.OnyxMotion
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * Géométrie du `NavigationSplitView` sur un écran large (tablette, pliable déplié).
 *
 * La capture du Honor Magic V3 déplié montrait le défaut d'origine: un partage 58/42 figé, un
 * volet de ~540 dp qu'aucun contrôle ne repliait, et un fil écrasé à droite. Les règles ici sont
 * donc **bornées**, pas proportionnelles:
 *
 * - le volet vise [SidebarIdealWidth] (360 dp, la colonne idéale d'téléphone), jamais moins de
 * [SidebarMinWidth] (320 dp) ni plus de [SidebarMaxWidth] (400 dp);
 * - il ne prend jamais plus de [SidebarMaxFraction] de la fenêtre;
 * - le détail garde au moins [DetailMinWidth] (360 dp); quand les deux minima ne tiennent pas
 * ensemble, l'app repasse à **un seul panneau** — le comportement de `NavigationSplitView`;
 * - le volet peut être **replié** ([ListDetailLayout.sidebarVisible]): le détail prend alors
 * toute la largeur, et le contrôle reste dans sa barre.
 */
internal val SidebarMinWidth: Dp = 320.dp
internal val SidebarMaxWidth: Dp = 400.dp
internal val SidebarIdealWidth: Dp = 360.dp

/** Le détail garde une largeur exploitable: en dessous, un seul panneau. */
internal val DetailMinWidth: Dp = 360.dp

/** Le volet ne dépasse jamais cette part de la fenêtre (la capture montrait 58 %). */
internal const val SidebarMaxFraction = 0.40f

internal data class ListDetailLayout(
    /** La fenêtre a-t-elle la place de deux panneaux, volet replié ou non ? */
    val twoPanes: Boolean,
    /** Le volet est-il dessiné ? Faux quand il est replié, ou quand la fenêtre est trop étroite. */
    val sidebarVisible: Boolean,
    /** La largeur géométrique du volet (0 quand la fenêtre n'a pas la place de deux panneaux). */
    val sidebarWidth: Dp,
    /** La largeur que le détail occupe réellement dans cet état. */
    val detailWidth: Dp,
) {
    /**
     * Deux panneaux **dessinés**: ce que les écrans appelaient « étendu ». Replié, la fenêtre a
     * toujours la place de deux panneaux (`twoPanes`) mais un seul est peint.
     */
    val expanded: Boolean get() = twoPanes && sidebarVisible
}

/** La fenêtre peut-elle porter deux panneaux avec leurs minima respectifs ? */
internal fun canShowTwoPanes(availableWidth: Dp): Boolean =
    availableWidth * SidebarMaxFraction >= SidebarMinWidth &&
        availableWidth - SidebarMinWidth >= DetailMinWidth

/** La largeur du volet pour cette fenêtre: 320…400 dp, jamais plus de 40 % de la largeur. */
internal fun sidebarWidthFor(availableWidth: Dp): Dp {
    val cap = minOf(SidebarMaxWidth, availableWidth * SidebarMaxFraction).coerceAtLeast(SidebarMinWidth)
    return SidebarIdealWidth.coerceIn(SidebarMinWidth, cap)
}

internal fun listDetailLayout(availableWidth: Dp, collapsed: Boolean = false): ListDetailLayout {
    if (!canShowTwoPanes(availableWidth)) {
        return ListDetailLayout(
            twoPanes = false,
            sidebarVisible = false,
            sidebarWidth = 0.dp,
            detailWidth = availableWidth,
        )
    }
    val sidebarWidth = sidebarWidthFor(availableWidth)
    val visible = !collapsed
    return ListDetailLayout(
        twoPanes = true,
        sidebarVisible = visible,
        sidebarWidth = sidebarWidth,
        // Replié, le détail prend toute la largeur: il n'y a plus de colonne à côté de lui.
        detailWidth = if (visible) availableWidth - sidebarWidth else availableWidth,
    )
}

/**
 * Le tableau Kanban et sa conversation suivent **exactement** la même géométrie bornée que les
 * autres volets: `KanbanView` n'a pas de raison de partager la fenêtre en deux moitiés quand la
 * liste des conversations, elle, tient dans 360 dp.
 */
internal fun kanbanPairLayout(availableWidth: Dp, collapsed: Boolean = false): ListDetailLayout =
    listDetailLayout(availableWidth, collapsed)

/**
 * Le contrôle du volet, celui de la barre d'téléphone: la **même** icône (`sidebar.left`) replie et
 * déplie — c'est le libellé d'accessibilité qui dit ce que le prochain appui fera, localisé
 * (« Replier la liste » / « Déplier la liste »).
 */
@Composable
internal fun SidebarToggleButton(
    collapsed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCircleButton(
        symbol = Symbols.SidebarLeft,
        label = if (collapsed) "Expand list" else "Collapse list",
        size = 38.dp,
        modifier = modifier,
        onClick = onClick,
    )
}

/** Navigation partagée par les sélections compactes et les détails étendus. */

/**
 * Les onglets de section de la liste (`ListSection`: Discussions, Cron, Kanban). Ce sont des
 * **racines**: on les atteint comme un onglet, jamais comme un enfant de la page d'où l'on vient.
 */
internal val SectionTabRoutes: Set<String> = linkedSetOf("CONVERSATIONS", "CRON", "KANBAN")

internal fun navigateWithBackStack(
    currentRoute: String,
    targetRoute: String,
    backStack: MutableList<String>,
): String {
    if (targetRoute == currentRoute) return currentRoute
    // Ouvrir un onglet de section **vide la pile**: la feuille qui l'a ouvert (les Réglages, par
    // exemple) se referme au lieu de rester dessous. Sans ça, les Réglages → Kanban empilaient les
    // Réglages sous le tableau, et le retour du tableau ramenait aux Réglages au lieu de la liste
    // des conversations.
    if (targetRoute in SectionTabRoutes) {
        backStack.clear()
        return targetRoute
    }
    if (backStack.lastOrNull() == targetRoute) {
        backStack.removeAt(backStack.lastIndex)
    } else {
        backStack.add(currentRoute)
    }
    return targetRoute
}

/**
 * L'écran sur lequel le retour retombe quand la pile est vide — le retour ne doit **jamais**
 * être avalé, ni produire un écran qui n'a pas de sens.
 *
 * Deux panneaux: la liste des conversations est déjà le fond de l'écran. Compact: un onglet de
 * section (Cron, Kanban) retombe sur la **liste des conversations** — c'est là que son propre
 * contrôle de retour renvoie, et c'est ce qui a été demandé — et tout le reste retombe sur le fil.
 */
internal fun rootRouteBehind(
    currentRoute: String,
    twoPanes: Boolean,
    chatRoute: String,
    conversationsRoute: String,
): String = when {
    twoPanes -> conversationsRoute
    currentRoute in SectionTabRoutes && currentRoute != conversationsRoute -> conversationsRoute
    else -> chatRoute
}

/** Une entrée à restaurer par le retour système, ou `null` pour laisser quitter l'app. */
internal fun popRoute(backStack: MutableList<String>): String? = backStack.removeLastOrNull()

/**
 * Quitte un écran dont **l'état est vide**, vers [targetRoute]: la route vide n'entre pas dans la
 * pile, et on efface la trace de celles qui y seraient déjà.
 *
 * C'est ce qui manquait au repli d'état vide. Les charges utiles de ces routes (`selectedText`,
 * `patchText`, `pdfSource`, `photoSet`, `subagentAgent`…) sont de simples `remember`: elles ne
 * survivent pas à une recréation d'activité, alors que `route` est `rememberSaveable`. L'app
 * revient donc sur `SELECT_TEXT` sans texte — et le repli faisait `navTo(CHAT)`, qui **empilait** la
 * route vide. Le retour retombait dessus, elle rebondissait aussitôt: l'écran ne changeait pas et
 * l'app ne se fermait jamais (mesuré à l'appareil: trois appuis sur retour, aucun effet, l'app
 * toujours au premier plan). Un écran qu'on ne peut pas montrer ne doit pas être une destination.
 */
internal fun navigateLeavingEmpty(
    currentRoute: String,
    targetRoute: String,
    backStack: MutableList<String>,
): String {
    backStack.removeAll { it == currentRoute }
    if (targetRoute == currentRoute) return currentRoute
    if (targetRoute in SectionTabRoutes) {
        backStack.clear()
        return targetRoute
    }
    // La destination ne doit pas non plus rester dans la pile: on y va, on n'y revient pas.
    backStack.removeAll { it == targetRoute }
    return targetRoute
}

/** Au dépliage, la liste compacte laisse sa place au chat déjà visible dans le deuxième panneau. */
internal fun routeWhenExpanded(
    currentRoute: String,
    backStack: MutableList<String>,
    conversationsRoute: String,
    chatRoute: String,
): String {
    if (currentRoute != conversationsRoute) return currentRoute
    if (backStack.lastOrNull() == chatRoute) backStack.removeAt(backStack.lastIndex)
    return chatRoute
}

/**
 * Les deux panneaux, avec un volet qui **se replie au lieu de disparaître**: la largeur est
 * animée ([OnyxMotion.Sites.SidePanelWidth], la course du panneau latéral ) et le détail
 * s'élargit au même rythme — une transition, jamais un saut. Replié tout à fait, le volet n'est
 * même plus composé.
 *
 * Deux panneaux réels: barres, défilement, composer et insets ne sont jamais partagés.
 */
@Composable
internal fun OnyxListDetail(
    layout: ListDetailLayout,
    sidebar: @Composable () -> Unit,
    detail: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val separator = LocalOnyxTheme.current.separator
    val targetWidth = if (layout.sidebarVisible) layout.sidebarWidth else 0.dp
    val animatedWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = OnyxMotion.Sites.SidePanelWidth,
        label = "sidebarWidth",
    )
    Row(modifier = modifier.fillMaxSize()) {
        if (animatedWidth > 0.dp) {
            Box(
                modifier = Modifier
                    .width(animatedWidth)
                    .fillMaxHeight()
                    .drawBehind {
                        drawLine(
                            color = separator,
                            start = Offset(size.width, 0f),
                            end = Offset(size.width, size.height),
                            strokeWidth = 0.5.dp.toPx(),
                        )
                    },
            ) {
                sidebar()
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            detail()
        }
    }
}
