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
 * Les pièces communes aux trois familles de « Capacités ».
 *
 * Elles sont ici **une seule fois** parce que les trois onglets posent les mêmes questions :
 * une ligne à interrupteur, un mot qui se touche, un champ de recherche, un état vide, un bandeau
 * d'échec. Trois copies auraient divergé sur les contrastes et sur les hauteurs tactiles.
 *
 * Aucune animation : un dépli, un interrupteur et un bandeau apparaissent d'un coup, ce qui rend
 * la préférence « réduire les animations » vraie sans exception à surveiller.
 */

/** Un mot sous le titre, discret, qui dit ce que la famille engage. */
@Composable
internal fun Note(text: String) {
    val theme = LocalOnyxTheme.current
    BasicText(
        text = text,
        style = theme.footnote.copy(color = bodyInk(theme, theme.formPageColor)),
        modifier = Modifier.padding(start = 4.dp),
    )
}

/** Le compte de la famille : « 24 compétences · 12 allumées », ou « 3 sur 24 … » pendant une recherche. */
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
 * Le bandeau d'un échec de chargement.
 *
 * Une famille qui n'a pas pu être lue **le dit** et laisse « Réessayer » à portée : l'écran reste
 * utilisable, les deux autres familles restent lisibles, et rien n'est remplacé par une page vide
 * sans explication. Le mot du serveur est rendu tel quel ([localize] à faux pour un message brut).
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

/** Une ligne d'attente, tant que la liste n'est pas arrivée. */
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

/** Un état vide d'une famille : une phrase, pas une page d'illustration. */
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
 * Le titre d'un groupe (une famille de compétences) — **le libellé de section de la référence** :
 * 11 sp SemiBold, interlettrage +0,08 em, en majuscules, en gris (voir `ContentSectionLabel`).
 */
@Composable
internal fun GroupHeading(text: String) {
    ContentSectionLabel(
        text = text,
        modifier = Modifier.padding(start = OnyxSpace.CardPadding + OnyxSpace.Hair, top = OnyxSpace.Snug, bottom = OnyxSpace.Half),
    )
}

/**
 * Le petit badge de provenance d'une compétence, à côté de son nom — « appris », « hub »,
 * « externe ».
 *
 * LOT organisation-fil-competences : repris de la référence. « appris » (une compétence qu'écrit
 * l'agent lui-même) prend le ton de l'accent ; les autres restent discrets — c'est une **source**,
 * pas un état.
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
            // **Le rembourrage vertical du compteur de la maison** : 2 dp, jamais 1. À 1 dp, la
            // boîte du texte venait au contact de la boîte peinte et le stade passait dans les
            // jambages (« appris », le « p »). La valeur vient de [BadgeGeometry], comme la barre
            // d'onglets et le volet — une seule source pour un seul geste de lecture.
            .padding(horizontal = 7.dp, vertical = BadgeGeometry.PadVertical),
    )
}

/**
 * Une ligne de capacité : son nom, ce qu'elle engage sur **deux lignes** au plus, et son interrupteur.
 *
 * Toute la ligne est la cible tactile (≥ 44 dp de haut, l'interrupteur n'étant lui que 31 dp) ; elle
 * porte l'état et le rôle d'un interrupteur pour les lecteurs d'écran, et une ligne qui ne se règle
 * pas ici le dit ([lockedNote]).
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
 * Un mot qui se touche (« Tester », « Installer », « Ouvrir »), teinté de l'accent.
 *
 * L'encre est calculée contre **la surface où le mot repose** ([surface]) : le même mot posé sur une
 * section de formulaire n'a pas le même fond que sur la page, et un accent lisible sur l'un peut
 * tomber sous 4,5:1 sur l'autre.
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
 * « Parcourir le catalogue » : un bouton pleine largeur sous le champ de recherche.
 *
 * Un mot teinté ne se distingue pas assez d'une ligne d'action pour être le seul chemin vers le
 * catalogue : c'est une surface, avec un dessin et un chevron.
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

/** Le champ de recherche : la loupe, la valeur, et la croix qui l'efface. */
@Composable
internal fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    val theme = LocalOnyxTheme.current
    // **Le rôle « champ »** ([SurfaceRole.Field]) : le creux de la famille **et le filet**, avec le
    // coin de champ (`FieldCorner`, 14 dp). C'était `RoundedCornerShape(OnyxSpace.ControlCorner)`
    // — le coin d'un **petit contrôle** (une touche, un badge) — sur un fond de carte, sans filet :
    // un champ de 44 dp de haut qui prenait la forme d'un bouton, et qui, sur la carte blanche d'un
    // écran clair, se lisait comme une pilule au liseré épais. Un seul rôle pour tous les champs de
    // l'app : celui-ci et celui du compositeur sont maintenant la même forme.
    val shape = SurfaceRole.Field.shape
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // **La cible tactile de 48 dp** : un champ de 44 dp était sous le plancher du brief ET
            // sous celui de la source (`TextField.kt:145` : 52 dp).
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
 * Un dépli : le titre et son compte, le chevron qui tourne d'un quart de tour.
 *
 * Le chevron pointe vers le bas quand c'est ouvert — l'état se lit au dessin, pas seulement au mot,
 * et la ligne porte « déplié » / « plié » pour les lecteurs d'écran.
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
