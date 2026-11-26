package com.onyx.app.ui.capabilities

import com.onyx.app.ui.design.BadgeGeometry
import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.design.OnyxSurfaces
import com.onyx.app.ui.design.SurfaceRole

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.ContentSectionLabel
import com.onyx.app.ui.design.Lucide
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxSwitch
import com.onyx.app.ui.design.OnyxContrast
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.bodyInk
import com.onyx.app.ui.design.iconInk
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.design.signalInk
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * The pieces shared by the three "Capabilities" families.
 *
 * They are here **only once** because the three tabs ask the same questions:
 * a row with a switch, a tappable word, a search field, an empty state, a failure
 * banner. Three copies would have diverged on contrasts and on touch heights.
 *
 * No animation: a disclosure, a switch and a banner appear at once, which makes
 * the "reduce motion" preference true without any exception to watch.
 */

/** A word under the title, discreet, that says what the family commits to. */
@Composable
internal fun Note(text: String) {
    val theme = LocalOnyxTheme.current
    BasicText(
        text = text,
        style = theme.footnote.copy(color = bodyInk(theme, theme.formPageColor)),
        modifier = Modifier.padding(start = 4.dp),
    )
}

/** The family's count: "24 skills · 12 on", or "3 of 24 …" during a search. */
@Composable
internal fun CountLine(text: String) {
    val theme = LocalOnyxTheme.current
    BasicText(
        text = text,
        localize = false,
        style = theme.caption.copy(color = bodyInk(theme, theme.formPageColor, theme.tertiaryText)),
        modifier = Modifier.padding(start = 4.dp),
    )
}

/**
 * The banner for a load failure.
 *
 * A family that could not be read **says so** and keeps "Try again" within reach: the screen stays
 * usable, the two other families stay readable, and nothing is replaced by an empty page
 * without explanation. The server's word is rendered as is ([localize] set to false for a raw message).
 */
@Composable
internal fun FailureBanner(text: String, retry: String = "Try again", onRetry: (() -> Unit)? = null) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .background(theme.formSurfaceColor, RoundedCornerShape(OnyxSpace.ControlCorner))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OnyxIcon(
            Symbols.ExclamationTriangle,
            tint = OnyxContrast.distinctOn(theme.warning, theme.formSurfaceColor, OnyxContrast.ICON_MIN),
            size = 16.dp,
        )
        BasicText(
            text = text,
            localize = false,
            style = theme.footnote.copy(color = bodyInk(theme, theme.formSurfaceColor)),
            modifier = Modifier.weight(1f),
        )
        if (onRetry != null) {
            ActionLine(text = retry, enabled = true, onClick = onRetry, surface = theme.formSurfaceColor)
        }
    }
}

/** A waiting line, as long as the list has not arrived. */
@Composable
internal fun LoadingLine(what: String) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicText(text = what, style = theme.footnote.copy(color = bodyInk(theme, theme.formPageColor)))
    }
}

/** An empty state of a family: one sentence, not an illustration page. */
@Composable
internal fun EmptyLine(text: String) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicText(text = text, style = theme.footnote.copy(color = bodyInk(theme, theme.formPageColor)))
    }
}

/**
 * A group's title (a skill family) — **the reference's section label**:
 * 11 sp SemiBold, +0.08 em letter spacing, uppercase, in grey (see `ContentSectionLabel`).
 */
@Composable
internal fun GroupHeading(text: String) {
    ContentSectionLabel(
        text = text,
        modifier = Modifier.padding(start = OnyxSpace.CardPadding + OnyxSpace.Hair, top = OnyxSpace.Snug, bottom = OnyxSpace.Half),
    )
}

/**
 * The small provenance badge of a skill, next to its name — "learned", "hub",
 * "external".
 *
 * LOT skill-thread-organization: taken from the reference. "learned" (a skill the agent writes
 * itself) takes the accent tone; the others stay discreet — it is a **source**,
 * not a state.
 */
@Composable
internal fun ProvenanceBadge(text: String, accent: Boolean) {
    val theme = LocalOnyxTheme.current
    BasicText(
        text = text,
        localize = false,
        style = theme.caption2.copy(
            color = if (accent) theme.accent else bodyInk(theme, theme.formPageColor),
            fontWeight = FontWeight.SemiBold,
        ),
        maxLines = 1,
        modifier = Modifier
            .background(if (accent) theme.accent.copy(alpha = 0.14f) else theme.fill, CircleShape)
            // **The house counter's vertical padding**: 2 dp, never 1. At 1 dp, the
            // text box came into contact with the painted box and the stage ran into the
            // descenders ("learned", the "p"). The value comes from [BadgeGeometry], like the tab bar
            // and the panel — a single source for a single reading gesture.
            .padding(horizontal = 7.dp, vertical = BadgeGeometry.PadVertical),
    )
}

/**
 * A capability row: its name, what it commits to on **two lines** at most, and its switch.
 *
 * The whole row is the touch target (≥ 44 dp high, the switch itself being only 31 dp); it
 * carries the state and role of a switch for screen readers, and a row that is not set
 * here says so ([lockedNote]).
 */
@Composable
internal fun CapabilityRow(
    title: String,
    detail: String?,
    localizeTitle: Boolean = false,
    checked: Boolean,
    busy: Boolean,
    canToggle: Boolean,
    onToggle: (Boolean) -> Unit,
    lockedNote: String = "Set by the plugin",
    trail: (@Composable () -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .then(
                if (canToggle) {
                    Modifier.semantics {
                        role = Role.Switch
                        toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                    }.onyxTappable(enabled = !busy) { onToggle(!checked) }
                } else {
                    Modifier.semantics(mergeDescendants = true) {
                        val state = OnyxLocalization[if (checked) "Turned on" else "Turned off"]
                        contentDescription = "$state, ${OnyxLocalization[lockedNote]}"
                    }
                },
            )
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(
                text = title,
                localize = localizeTitle,
                style = theme.body.copy(
                    color = if (checked) {
                        theme.textColor
                    } else {
                        bodyInk(theme, theme.formPageColor)
                    },
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            detail?.let {
                BasicText(
                    text = it,
                    localize = false,
                    style = theme.caption.copy(color = bodyInk(theme, theme.formPageColor)),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trail != null) {
            trail()
        } else if (canToggle) {
            OnyxSwitch(checked = checked, onCheckedChange = onToggle, enabled = !busy)
        }
    }
}

/**
 * A tappable word ("Test", "Install", "Open"), tinted with the accent.
 *
 * The ink is computed against **the surface the word rests on** ([surface]): the same word placed on a
 * form section does not have the same background as on the page, and an accent readable on one can
 * fall below 4.5:1 on the other.
 */
@Composable
internal fun ActionLine(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    localize: Boolean = true,
    surface: Color = LocalOnyxTheme.current.formPageColor,
) {
    val theme = LocalOnyxTheme.current
    BasicText(
        text = text,
        localize = localize,
        style = theme.footnote.copy(
            color = if (enabled) {
                signalInk(theme, surface)
            } else {
                bodyInk(theme, surface, theme.tertiaryText)
            },
            fontWeight = FontWeight.SemiBold,
        ),
        modifier = Modifier
            .heightIn(min = 44.dp)
            .onyxTappable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

/**
 * "Browse the catalogue": a full-width button under the search field.
 *
 * A tinted word is not distinct enough from an action row to be the only path to the
 * catalogue: it is a surface, with a drawing and a chevron.
 */
@Composable
internal fun CatalogueButton(
    onClick: () -> Unit,
    label: String = "Browse the catalogue",
    symbol: Int = Symbols.SquareStack3dUp,
) {
    val theme = LocalOnyxTheme.current
    val shape = RoundedCornerShape(OnyxSpace.ControlCorner)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .background(theme.surfaceColor, shape)
            .onyxTappable(onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OnyxIcon(symbol, tint = signalInk(theme, theme.surfaceColor), size = 16.dp)
        BasicText(
            text = label,
            style = theme.footnote.copy(color = signalInk(theme, theme.surfaceColor), fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f),
        )
        OnyxIcon(Symbols.ChevronRight, tint = iconInk(theme, theme.surfaceColor), size = 11.dp)
    }
}

/** The search field: the magnifier, the value, and the cross that clears it. */
@Composable
internal fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    val theme = LocalOnyxTheme.current
    // **The "field" role** ([SurfaceRole.Field]): the family's well **and the hairline**, with the field's
    // corner (`FieldCorner`, 14 dp). It used to be `RoundedCornerShape(OnyxSpace.ControlCorner)`
    // — the corner of a **small control** (a key, a badge) — on a card background, without a hairline:
    // a 44 dp high field that took the shape of a button, and that, on the white card of a
    // light screen, read as a pill with a thick outline. A single role for all the fields of
    // the app: this one and the composer's are now the same shape.
    val shape = SurfaceRole.Field.shape
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // **The 48 dp touch target**: a 44 dp field was below the brief's floor AND
            // below the source's (`TextField.kt:145`: 52 dp).
            .heightIn(min = OnyxSpace.TouchTarget)
            .clip(shape)
            .background(OnyxSurfaces.well(theme), shape)
            .border(OnyxSpace.Stroke, OnyxSurfaces.hairline(theme), shape)
            .padding(horizontal = OnyxSpace.Half + OnyxSpace.GlyphGap),
        horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OnyxIcon(Lucide.Search, tint = iconInk(theme, OnyxSurfaces.well(theme)), size = 16.dp)
        Box(modifier = Modifier.weight(1f).padding(vertical = 12.dp)) {
            if (value.isEmpty()) {
                BasicText(
                    text = placeholder,
                    style = theme.footnote.copy(color = bodyInk(theme, OnyxSurfaces.well(theme), theme.tertiaryText)),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = theme.footnote.copy(color = theme.textColor),
                cursorBrush = SolidColor(theme.accent),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
            )
        }
        if (value.isNotEmpty()) {
            OnyxIcon(
                symbol = Lucide.X,
                tint = iconInk(theme, OnyxSurfaces.well(theme)),
                size = 14.dp,
                modifier = Modifier.onyxTappable { onValueChange("") },
            )
        }
    }
}

/**
 * A disclosure: the title and its count, the chevron that turns a quarter turn.
 *
 * The chevron points down when open — the state reads from the drawing, not only from the word,
 * and the row carries "expanded" / "collapsed" for screen readers.
 */
@Composable
internal fun DisclosureRow(
    title: String,
    subtitle: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    trail: (@Composable () -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "${OnyxLocalization[title]}, ${
                    if (expanded) OnyxLocalization["Expanded"] else OnyxLocalization["Collapsed"]
                }"
            }
            .onyxTappable(onClick = onToggle)
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OnyxIcon(
            symbol = if (expanded) Symbols.ChevronDown else Symbols.ChevronRight,
            tint = iconInk(theme, theme.formPageColor),
            size = 12.dp,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(
                text = title,
                style = theme.body.copy(color = theme.textColor, fontWeight = FontWeight.Medium),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                BasicText(
                    text = it,
                    localize = false,
                    style = theme.caption.copy(color = bodyInk(theme, theme.formPageColor)),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trail?.let { it() }
    }
}
