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
 * Geometry of the reference `NavigationSplitView` on a wide screen (tablet, unfolded foldable).
 *
 * The screenshot of the unfolded Honor Magic V3 showed the original defect: a fixed 58/42 split, a
 * sidebar of ~540 dp that no control collapsed, and a feed crushed on the right. The rules here are
 * therefore **bounded**, not proportional:
 *
 * - the sidebar targets [SidebarIdealWidth] (360 dp, the reference's ideal column), never less than
 *   [SidebarMinWidth] (320 dp) nor more than [SidebarMaxWidth] (400 dp);
 * - it never takes more than [SidebarMaxFraction] of the window;
 * - the detail keeps at least [DetailMinWidth] (360 dp); when the two minima do not fit
 *   together, the app falls back to **a single pane** — the behaviour of `NavigationSplitView`;
 * - the sidebar can be **collapsed** ([ListDetailLayout.sidebarVisible]): the detail then takes
 *   the whole width, and the control stays in its bar.
 */
internal val SidebarMinWidth: Dp = 320.dp
internal val SidebarMaxWidth: Dp = 400.dp
internal val SidebarIdealWidth: Dp = 360.dp

/** The detail keeps a usable width: below this, a single pane. */
internal val DetailMinWidth: Dp = 360.dp

/** The sidebar never exceeds this share of the window (the screenshot showed 58 %). */
internal const val SidebarMaxFraction = 0.40f

/**
 * The width of the sidebar **when it carries a screen** — a tile opened in the sidebar, the back
 * arrow at its head (see `OnyxSidebarState.destination`).
 *
 * Why a second geometry: the list column (320…400 dp) is made for conversation rows, not for a
 * screen. Drawing "Compétences" ("Skills"), "Aperçus" ("Insights") or "Réglages" ("Settings") there
 * would crush them — exactly the "without crushing the content into 320 dp" of the request. The
 * sidebar therefore takes the width of an in-house screen: never less than [SidebarContentMinWidth] (400 dp,
 * the width of a phone, where all these screens already read well), never more than
 * [SidebarContentMaxWidth], and never more than [SidebarContentFraction] of the window.
 *
 * The feed, next to it, **is never removed**: as long as the window can keep [DetailMinWidth] for it,
 * we keep it; on a tighter window, the sidebar settles for its share and the feed shrinks.
 */
internal val SidebarContentMinWidth: Dp = 400.dp
internal val SidebarContentMaxWidth: Dp = 560.dp
internal const val SidebarContentFraction = 0.55f

/** The width of the sidebar **carrying a screen**, for this window (see [SidebarContentMinWidth]). */
internal fun sidebarContentWidthFor(availableWidth: Dp): Dp {
    val share = availableWidth * SidebarContentFraction
    val capped = minOf(share, SidebarContentMaxWidth)
    val roomForDetail = availableWidth - DetailMinWidth
    val bounded = if (roomForDetail >= SidebarContentMinWidth) minOf(capped, roomForDetail) else capped
    val floor = minOf(SidebarContentMinWidth, availableWidth)
    return bounded.coerceAtLeast(floor).coerceAtMost(availableWidth)
}

internal data class ListDetailLayout(
    /** Does the window have room for two panes, sidebar collapsed or not? */
    val twoPanes: Boolean,
    /** Is the sidebar drawn? False when it is collapsed, or when the window is too narrow. */
    val sidebarVisible: Boolean,
    /** The geometric width of the sidebar (0 when the window has no room for two panes). */
    val sidebarWidth: Dp,
    /** The width the detail actually occupies in this state. */
    val detailWidth: Dp,
) {
    /**
     * Two **drawn** panes: what the screens used to call "étendu" ("expanded"). When collapsed, the window
     * still has room for two panes (`twoPanes`) but only one is painted.
     */
    val expanded: Boolean get() = twoPanes && sidebarVisible
}

/**
 * **The threshold** between the two layouts: the smallest width that lets the sidebar (at least
 * [SidebarMinWidth]) and the detail (at least [DetailMinWidth]) fit side by side — neither of them
 * being narrower than its maximum share ([SidebarMaxFraction]).
 *
 * It is written **once** and named, because everything depends on it: the sidebar mode
 * (`sidebarModeFor`), hence the width of the overlay, and the fact that the **widened** width
 * of a screen ([sidebarContentWidthFor], 400…560 dp) never applies on a phone nor on an
 * unfolded foldable. Measured on the device: **411 dp** (Honor folded) is below this threshold — a single
 * pane; **862 dp** (unfolded) is above it — two panes.
 */
internal val TwoPaneMinWidth: Dp =
    maxOf(SidebarMinWidth / SidebarMaxFraction, SidebarMinWidth + DetailMinWidth)

/** Can the window carry two panes with their respective minima? */
internal fun canShowTwoPanes(availableWidth: Dp, fontScale: Float = 1f): Boolean =
    availableWidth >= TwoPaneMinWidth * fontScale.coerceAtLeast(1f) &&
        availableWidth * SidebarMaxFraction >= SidebarMinWidth

/** The width of the sidebar for this window: 320…400 dp, never more than 40 % of the width. */
internal fun sidebarWidthFor(availableWidth: Dp): Dp {
    val cap = minOf(SidebarMaxWidth, availableWidth * SidebarMaxFraction).coerceAtLeast(SidebarMinWidth)
    return SidebarIdealWidth.coerceIn(SidebarMinWidth, cap)
}

internal fun listDetailLayout(availableWidth: Dp, collapsed: Boolean = false, content: Boolean = false, fontScale: Float = 1f): ListDetailLayout {
    if (!canShowTwoPanes(availableWidth, fontScale)) {
        return ListDetailLayout(
            twoPanes = false,
            sidebarVisible = false,
            sidebarWidth = 0.dp,
            detailWidth = availableWidth,
        )
    }
    // The sidebar carries a **screen**: it takes the width of a screen, not the list column —
    // otherwise "Compétences" ("Skills") or "Aperçus" ("Insights") would be drawn within 320…400 dp and be crushed.
    val sidebarWidth = if (content) sidebarContentWidthFor(availableWidth) else sidebarWidthFor(availableWidth)
    val visible = !collapsed
    return ListDetailLayout(
        twoPanes = true,
        sidebarVisible = visible,
        sidebarWidth = sidebarWidth,
        // Collapsed, the detail takes the whole width: there is no column left beside it.
        detailWidth = if (visible) availableWidth - sidebarWidth else availableWidth,
    )
}

/**
 * The Kanban board and its conversation follow **exactly** the same bounded geometry as the
 * other sidebars: `KanbanView` has no reason to split the window in two halves when the
 * conversation list itself fits within 360 dp.
 */
internal fun kanbanPairLayout(availableWidth: Dp, collapsed: Boolean = false): ListDetailLayout =
    listDetailLayout(availableWidth, collapsed)

/**
 * The sidebar control, the reference bar's one: the **same** icon (`sidebar.left`) collapses and
 * expands — it is the accessibility label that says what the next tap will do, localized
 * ("Replier la liste" / "Déplier la liste" — "Collapse list" / "Expand list").
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

/** Navigation shared by compact selections and expanded details. */

/**
 * The list's section tabs (`ListSection`: Cron, Kanban, Code). They are **peers**:
 * one moves from one to the other through the tab, and back lands on the feed.
 *
 * The **conversation list** is no longer part of it: the screen that carried it as a route was
 * removed (it lives in the **sidebar**, which is a pane and not a destination). The section
 * rail therefore no longer has its own root — a section tab with an empty stack lands on the feed,
 * like everything else ([rootRouteBehind]).
 */
internal val SectionTabRoutes: Set<String> = linkedSetOf("CRON", "KANBAN", "CODE")

internal fun navigateWithBackStack(
    currentRoute: String,
    targetRoute: String,
    backStack: MutableList<String>,
): String {
    if (targetRoute == currentRoute) return currentRoute
    // The stack is only cleared to stay **within the rail**: one goes from a section tab to another
    // (from Cron to Kanban, for example). A section tab reached **from outside** the rail
    // (Settings, a tile, a menu) is a page **pushed** like any other: its route enters
    // the stack, so that the edge back gesture — like the home back button, both call
    // `navBack()` — returns the page one came from (Settings) and not the list. Before,
    // the stack was cleared for **every** section tab: Settings → Cron → edge back
    // returned the list instead of Settings, the page one had just entered from
    // (reported defect).
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
 * The screen the back returns to when the stack is empty — back must **never**
 * be swallowed, nor produce a screen that makes no sense.
 *
 * The feed is the root, **in both widths**: the conversation list is no longer a
 * route (it lives in the sidebar, which is a pane), so a section tab with an empty stack — Cron,
 * Kanban, Code — lands on the feed like everything else. [currentRoute] remains the parameter of the
 * rule: it is what will decide, if a distinct root screen reappears, where back must land.
 */
internal fun rootRouteBehind(
    currentRoute: String,
    chatRoute: String,
): String = chatRoute

/**
 * An entry to restore by the edge back gesture, or `null` to let the app quit. */
internal fun popRoute(backStack: MutableList<String>): String? = backStack.removeLastOrNull()

/**
 * Is back **handled by the app** on this screen?
 *
 * At the bottom of the stack one is at the **root**, and the root must behave the same in both
 * formats: back hands control back to the system (the app closes) — never an intermediate screen that
 * changes nothing.
 *
 * The root is the feed, **in both widths**. The conversation list is **no longer a
 * route**: the screen that carried it was removed, it lives in the **sidebar**, which is a pane and
 * not a destination. `twoPanes` therefore no longer moves the root — the unfolded foldable's sidebar paints
 * the same feed — but the rule says it explicitly: the [ReturnMatrixTest], itself, measures the response
 * on both formats, formats that must never again require two gestures instead of one.
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
 * Leaves a screen whose **state is empty**, towards [targetRoute]: the empty route does not enter the
 * stack, and the trace of those already there is erased.
 *
 * This is what was missing from the empty-state fallback. The payloads of these routes (`selectedText`,
 * `patchText`, `pdfSource`, `photoSet`, `subagentAgent`…) are plain `remember`s: they do not
 * survive an activity recreation, whereas `route` is `rememberSaveable`. The app
 * therefore returns to `SELECT_TEXT` without text — and the fallback did `navTo(CHAT)`, which **pushed** the
 * empty route. Back landed on it, it bounced right away: the screen did not change and
 * the app never closed (measured on the device: three back presses, no effect, the app
 * still in the foreground). A screen one cannot show must not be a destination.
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
    // The destination must not stay in the stack either: one goes there, one does not come back to it.
    backStack.removeAll { it == targetRoute }
    return targetRoute
}

/**
 * The two panes, with a sidebar that **collapses instead of disappearing**: the width is
 * animated ([OnyxMotion.Sites.SidePanelWidth], the travel of the reference's side panel) and the detail
 * widens at the same pace — a transition, never a jump. Fully collapsed, the sidebar is not
 * even composed anymore.
 *
 * Two real panes: bars, scrolling, composer and insets are never shared.
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
