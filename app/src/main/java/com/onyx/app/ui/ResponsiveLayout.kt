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
 * Géométrie du `NavigationSplitView` de la référence sur un écran large (tablette, pliable déplié).
 *
 * La capture du Honor Magic V3 déplié montrait le défaut d'origine : un partage 58/42 figé, un
 * volet de ~540 dp qu'aucun contrôle ne repliait, et un fil écrasé à droite. Les règles ici sont
 * donc **bornées**, pas proportionnelles :
 *
 * - le volet vise [SidebarIdealWidth] (360 dp, la colonne idéale de la référence), jamais moins de
 *   [SidebarMinWidth] (320 dp) ni plus de [SidebarMaxWidth] (400 dp) ;
 * - il ne prend jamais plus de [SidebarMaxFraction] de la fenêtre ;
 * - le détail garde au moins [DetailMinWidth] (360 dp) ; quand les deux minima ne tiennent pas
 *   ensemble, l'app repasse à **un seul panneau** — le comportement de `NavigationSplitView` ;
 * - le volet peut être **replié** ([ListDetailLayout.sidebarVisible]) : le détail prend alors
 *   toute la largeur, et le contrôle reste dans sa barre.
 */
internal val SidebarMinWidth: Dp = 320.dp
internal val SidebarMaxWidth: Dp = 400.dp
internal val SidebarIdealWidth: Dp = 360.dp

/** Le détail garde une largeur exploitable : en dessous, un seul panneau. */
internal val DetailMinWidth: Dp = 360.dp

/** Le volet ne dépasse jamais cette part de la fenêtre (la capture montrait 58 %). */
internal const val SidebarMaxFraction = 0.40f

/**
 * La largeur du volet **quand il porte un écran** — une tuile ouverte dans le volet, la flèche de
 * retour en tête (voir `OnyxSidebarState.destination`).
 *
 * Pourquoi une seconde géométrie : la colonne de la liste (320…400 dp) est faite pour des lignes de
 * conversation, pas pour un écran. Y dessiner « Compétences », « Aperçus » ou « Réglages » les
 * écraserait — c'est exactement le « sans écraser le contenu dans 320 dp » de la demande. Le volet
 * prend donc la largeur d'un écran de la maison : jamais moins de [SidebarContentMinWidth] (400 dp,
 * la largeur d'un téléphone, où tous ces écrans se lisent déjà), jamais plus de
 * [SidebarContentMaxWidth], et jamais plus de [SidebarContentFraction] de la fenêtre.
 *
 * Le fil, à côté, **n'est jamais retiré** : tant que la fenêtre peut lui garder [DetailMinWidth] on
 * le lui garde ; sur une fenêtre plus juste, le volet se contente de sa part et le fil se rétrécit.
 */
internal val SidebarContentMinWidth: Dp = 400.dp
internal val SidebarContentMaxWidth: Dp = 560.dp
internal const val SidebarContentFraction = 0.55f

/** La largeur du volet **portant un écran**, pour cette fenêtre (voir [SidebarContentMinWidth]). */
internal fun sidebarContentWidthFor(availableWidth: Dp): Dp {
    val share = availableWidth * SidebarContentFraction
    val capped = minOf(share, SidebarContentMaxWidth)
    val roomForDetail = availableWidth - DetailMinWidth
    val bounded = if (roomForDetail >= SidebarContentMinWidth) minOf(capped, roomForDetail) else capped
    val floor = minOf(SidebarContentMinWidth, availableWidth)
    return bounded.coerceAtLeast(floor).coerceAtMost(availableWidth)
}

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
     * Deux panneaux **dessinés** : ce que les écrans appelaient « étendu ». Replié, la fenêtre a
     * toujours la place de deux panneaux (`twoPanes`) mais un seul est peint.
     */
    val expanded: Boolean get() = twoPanes && sidebarVisible
}

/**
 * **Le seuil** entre les deux dispositions : la plus petite largeur qui laisse tenir, côte à côte,
 * le volet (au moins [SidebarMinWidth]) et le détail (au moins [DetailMinWidth]) — ni l'un ni
 * l'autre n'étant plus étroit que sa part maximale ([SidebarMaxFraction]).
 *
 * Il est écrit **une fois** et nommé, parce que tout dépend de lui : le mode du volet
 * (`sidebarModeFor`), donc la largeur de la surimpression, et le fait que la largeur **élargie**
 * d'un écran ([sidebarContentWidthFor], 400…560 dp) ne s'applique jamais sur un téléphone ni sur un
 * pliable replié. Relevé à l'appareil : **411 dp** (Honor replié) est sous ce seuil — un seul
 * panneau ; **862 dp** (déplié) est au-dessus — deux panneaux.
 */
internal val TwoPaneMinWidth: Dp =
    maxOf(SidebarMinWidth / SidebarMaxFraction, SidebarMinWidth + DetailMinWidth)

/** La fenêtre peut-elle porter deux panneaux avec leurs minima respectifs ? */
internal fun canShowTwoPanes(availableWidth: Dp): Boolean =
    availableWidth >= TwoPaneMinWidth && availableWidth * SidebarMaxFraction >= SidebarMinWidth

/** La largeur du volet pour cette fenêtre : 320…400 dp, jamais plus de 40 % de la largeur. */
internal fun sidebarWidthFor(availableWidth: Dp): Dp {
    val cap = minOf(SidebarMaxWidth, availableWidth * SidebarMaxFraction).coerceAtLeast(SidebarMinWidth)
    return SidebarIdealWidth.coerceIn(SidebarMinWidth, cap)
}

internal fun listDetailLayout(availableWidth: Dp, collapsed: Boolean = false, content: Boolean = false): ListDetailLayout {
    if (!canShowTwoPanes(availableWidth)) {
        return ListDetailLayout(
            twoPanes = false,
            sidebarVisible = false,
            sidebarWidth = 0.dp,
            detailWidth = availableWidth,
        )
    }
    // Le volet porte un **écran** : il prend la largeur d'un écran, pas la colonne de la liste —
    // sinon « Compétences » ou « Aperçus » se dessineraient dans 320…400 dp et seraient écrasés.
    val sidebarWidth = if (content) sidebarContentWidthFor(availableWidth) else sidebarWidthFor(availableWidth)
    val visible = !collapsed
    return ListDetailLayout(
        twoPanes = true,
        sidebarVisible = visible,
        sidebarWidth = sidebarWidth,
        // Replié, le détail prend toute la largeur : il n'y a plus de colonne à côté de lui.
        detailWidth = if (visible) availableWidth - sidebarWidth else availableWidth,
    )
}

/**
 * Le tableau Kanban et sa conversation suivent **exactement** la même géométrie bornée que les
 * autres volets : `KanbanView` n'a pas de raison de partager la fenêtre en deux moitiés quand la
 * liste des conversations, elle, tient dans 360 dp.
 */
internal fun kanbanPairLayout(availableWidth: Dp, collapsed: Boolean = false): ListDetailLayout =
    listDetailLayout(availableWidth, collapsed)

/**
 * Le contrôle du volet, celui de la barre de la référence : la **même** icône (`sidebar.left`) replie et
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
 * Les onglets de section de la liste (`ListSection` : Cron, Kanban, Code). Ce sont des **pairs** :
 * on passe de l'un à l'autre par l'onglet, et le retour retombe sur le fil.
 *
 * La **liste des conversations** n'en fait plus partie : l'écran qui la portait comme route a été
 * retiré (elle vit dans le **volet**, qui est un panneau et non une destination). Le rail de
 * section n'a donc plus de racine propre — un onglet de section à pile vide retombe sur le fil,
 * comme tout le reste ([rootRouteBehind]).
 */
internal val SectionTabRoutes: Set<String> = linkedSetOf("CRON", "KANBAN", "CODE")

internal fun navigateWithBackStack(
    currentRoute: String,
    targetRoute: String,
    backStack: MutableList<String>,
): String {
    if (targetRoute == currentRoute) return currentRoute
    // La pile ne se vide que pour rester **dans le rail** : on va d'un onglet de section à un autre
    // (de Cron à Kanban, par exemple). Un onglet de section atteint **depuis l'extérieur** du rail
    // (les Réglages, une tuile, un menu) est une page **poussée** comme une autre : sa route entre
    // dans la pile, si bien que le retour du bord — comme le bouton retour maison, ils appellent
    // tous deux `navBack()` — rend la page d'où l'on vient (les Réglages) et non la liste. Avant,
    // la pile était vidée pour **tout** onglet de section : Réglages → Cron → retour du bord
    // rendait la liste au lieu des Réglages, la page d'où l'on venait pourtant d'entrer
    // (défaut signalé).
    val intoRail = targetRoute in SectionTabRoutes && currentRoute in SectionTabRoutes
    if (intoRail) {
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
 * Le fil est la racine, **dans les deux largeurs** : la liste des conversations n'est plus une
 * route (elle vit dans le volet, qui est un panneau), donc un onglet de section à pile vide — Cron,
 * Kanban, Code — retombe sur le fil comme tout le reste. [currentRoute] reste le paramètre de la
 * règle : c'est lui qui décidera, si un écran racine distinct reparaît, où le retour doit tomber.
 */
internal fun rootRouteBehind(
    currentRoute: String,
    chatRoute: String,
): String = chatRoute

/**
 * Une entrée à restaurer par le retour du bord, ou `null` pour laisser quitter l'app. */
internal fun popRoute(backStack: MutableList<String>): String? = backStack.removeLastOrNull()

/**
 * Le retour est-il **traité par l'app** sur cet écran ?
 *
 * Au pied de la pile on est à la **racine**, et la racine doit se comporter pareil dans les deux
 * formats : le retour rend la main au système (l'app se ferme) — jamais un écran intermédiaire qui
 * ne change rien.
 *
 * La racine est le fil, **dans les deux largeurs**. La liste des conversations n'est **plus une
 * route** : l'écran qui la portait a été retiré, elle vit dans le **volet**, qui est un panneau et
 * non une destination. `twoPanes` ne déplace donc plus la racine — le volet du pliable déplié peint
 * le même fil — mais la règle le dit explicitement : la [RetourMatriceTest], elle, mesure la réponse
 * sur les deux formats, formats qui ne doivent plus jamais demander deux gestes au lieu d'un.
 */
internal fun routeBackHandled(
    currentRoute: String,
    backStack: List<String>,
    twoPanes: Boolean,
    chatRoute: String,
): Boolean = when {
    backStack.isNotEmpty() -> true
    twoPanes -> currentRoute != chatRoute
    else -> currentRoute != chatRoute
}

/**
 * Quitte un écran dont **l'état est vide**, vers [targetRoute] : la route vide n'entre pas dans la
 * pile, et on efface la trace de celles qui y seraient déjà.
 *
 * C'est ce qui manquait au repli d'état vide. Les charges utiles de ces routes (`selectedText`,
 * `patchText`, `pdfSource`, `photoSet`, `subagentAgent`…) sont de simples `remember` : elles ne
 * survivent pas à une recréation d'activité, alors que `route` est `rememberSaveable`. L'app
 * revient donc sur `SELECT_TEXT` sans texte — et le repli faisait `navTo(CHAT)`, qui **empilait** la
 * route vide. Le retour retombait dessus, elle rebondissait aussitôt : l'écran ne changeait pas et
 * l'app ne se fermait jamais (mesuré à l'appareil : trois appuis sur retour, aucun effet, l'app
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
    // La destination ne doit pas non plus rester dans la pile : on y va, on n'y revient pas.
    backStack.removeAll { it == targetRoute }
    return targetRoute
}

/**
 * Les deux panneaux, avec un volet qui **se replie au lieu de disparaître** : la largeur est
 * animée ([OnyxMotion.Sites.SidePanelWidth], la course du panneau latéral de la référence) et le détail
 * s'élargit au même rythme — une transition, jamais un saut. Replié tout à fait, le volet n'est
 * même plus composé.
 *
 * Deux panneaux réels : barres, défilement, composer et insets ne sont jamais partagés.
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
