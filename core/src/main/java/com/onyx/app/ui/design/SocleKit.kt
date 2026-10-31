package com.onyx.app.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme

/**
 * **Le vocabulaire du socle, en composables.** C'est ici que les jetons de [OnyxSocle] deviennent
 * des formes : six corps, deux espacements, une famille de rayons, une épaisseur de trait.
 *
 * Ces composables sont **ceux des trois écrans sans imagerie** — Réglages, Capacités, la feuille de
 * modèle. Ils n'ont, par construction, **aucune couverture** : ils tiennent le langage par la
 * typographie, le rythme et les pastilles, et rien d'autre. C'est **cohérent**, pas un manque.
 *
 * Un seul accent par écran : le cran actif d'un segmenté, l'action principale, la bascule ouverte,
 * le point d'une pastille d'état. Tout le reste est neutre.
 */

// =================================================================================================
//  Les styles — les six corps du socle, résolus pour le thème courant
// =================================================================================================

/** `.dsp` — le grand titre d'écran : 30 sp, −0,032 em, ExtraBold. */
@Composable
fun socleDisplayStyle(theme: ResolvedTheme): TextStyle =
    theme.largeTitle.copy(
        fontSize = OnyxSocle.Display,
        fontWeight = OnyxSocle.WeightDisplay,
        letterSpacing = OnyxSocle.TrackingDisplay,
    )

/** `.sht` — le titre de feuille : 26 sp, −0,030 em. */
@Composable
fun socleHeadingStyle(theme: ResolvedTheme): TextStyle =
    theme.title.copy(
        fontSize = OnyxSocle.Heading,
        fontWeight = OnyxSocle.WeightTitle,
        letterSpacing = OnyxSocle.TrackingHeading,
    )

/** `.ttl` — le titre de carte, le nom d'un bot : 16 sp, −0,018 em, Bold. */
@Composable
fun socleTitleStyle(theme: ResolvedTheme): TextStyle =
    theme.body.copy(
        fontSize = OnyxSocle.Title,
        fontWeight = OnyxSocle.WeightTitle,
        letterSpacing = OnyxSocle.TrackingTitle,
    )

/** `.mtx2` — le corps : 15 sp, −0,004 em. */
@Composable
fun socleBodyStyle(theme: ResolvedTheme): TextStyle =
    theme.body.copy(fontSize = OnyxSocle.Body, letterSpacing = OnyxSocle.TrackingBody)

/** `.mt` — les méta : 12 sp. */
@Composable
fun socleMetaStyle(theme: ResolvedTheme): TextStyle = theme.caption.copy(fontSize = OnyxSocle.Meta)

/** `.sec` — l'intitulé de section : 11 sp, **+0,16 em**, capitales. */
@Composable
fun socleSectionStyle(theme: ResolvedTheme): TextStyle =
    theme.caption2.copy(
        fontSize = OnyxSocle.Micro,
        letterSpacing = OnyxSocle.TrackingMicro,
        fontWeight = FontWeight.SemiBold,
    )

/** L'identifiant et le nombre : monospace, au plancher (13 sp, l'exception déclarée du socle). */
@Composable
fun socleMonoStyle(theme: ResolvedTheme): TextStyle =
    theme.caption.copy(fontSize = OnyxSocle.Meta, fontFamily = FontFamily.Monospace)

/**
 * **L'encre de tout titre ou en-tête de la maison** : l'encre pleine du thème ([ResolvedTheme.textColor],
 * le `label` de la référence). C'est celle que [SocleScreenTitle] et [SocleLargeHeader] posent déjà.
 *
 * **Pourquoi elle est nommée.** Un style du socle ne porte pas de couleur : il décrit la forme
 * (corps, poids, interlettrage). Un `BasicText` qui reçoit ce style **sans** `.copy(color = …)`
 * n'hérite de rien — la Fondation écrit alors du **noir par défaut**. Le titre se lit donc en noir
 * dans les deux apparences : correct en clair (par accident), **invisible en sombre** (noir sur la
 * page de nuit). Un titre n'a pas de « couleur à lui » : son encre **est** celle de la maison, et
 * elle se **lit** sur la surface où il se pose — d'où cette fonction, la seule source de l'encre
 * d'un titre.
 */
fun socleTitleInk(theme: ResolvedTheme): Color = theme.textColor

// =================================================================================================
//  L'en-tête d'écran
// =================================================================================================

/** Le grand titre d'un écran, sa ligne de contexte, et son action — une seule, à droite. */
@Composable
fun SocleScreenTitle(
    title: String,
    subtitle: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OnyxSocle.ScreenMargin)
            .padding(top = OnyxSocle.Loose, bottom = OnyxSocle.Tight),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(text = title, style = socleDisplayStyle(theme).copy(color = theme.textColor), maxLines = 1)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                BasicText(
                    text = subtitle,
                    style = socleMetaStyle(theme).copy(color = theme.secondaryText),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (action != null && onAction != null) {
            BasicText(
                text = action,
                style = theme.body.copy(color = theme.accent, fontSize = OnyxSocle.Body),
                maxLines = 1,
                modifier = Modifier
                    .defaultMinSize(minHeight = OnyxSocle.TouchTarget)
                    .onyxClickable(onClick = onAction)
                    .padding(horizontal = OnyxSocle.Tight, vertical = OnyxSocle.Tight),
            )
        }
    }
}

/**
 * L'en-tête **à grand titre** d'un écran : le retour (la flèche de la maison), le grand titre du
 * socle (**30 sp, −0,032 em, ExtraBold**), sa ligne de contexte, et le contrôle du volet à droite.
 *
 * C'est l'en-tête des écrans **sans imagerie** : il n'y a rien à couvrir, le titre porte l'écran.
 */
@Composable
fun SocleLargeHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    localizeTitle: Boolean = true,
    actions: (@Composable () -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = OnyxSocle.Tight)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = OnyxSocle.Tight, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                Box(
                    modifier = Modifier.size(OnyxSocle.TouchTarget - 4.dp).onyxClickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    OnyxIcon(
                        symbol = Symbols.ChevronRight,
                        tint = theme.secondaryText,
                        size = 24.dp,
                        contentDescription = "Back",
                        modifier = Modifier.rotate(180f),
                    )
                }
            } else {
                Box(Modifier.size(OnyxSocle.TouchTarget - 4.dp))
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) { actions?.invoke() }
        }
        BasicText(
            text = title,
            localize = localizeTitle,
            style = socleDisplayStyle(theme).copy(color = theme.textColor),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = OnyxSocle.ScreenMargin)
                .semantics { heading() },
        )
        if (!subtitle.isNullOrBlank()) {
            BasicText(
                text = subtitle,
                // **Le sous-titre est un libellé de l'interface**, pas une donnée : il passe par le
                // dictionnaire central comme le titre. `false` le laissait en anglais au milieu d'un
                // écran français (« What the agent can use in new chats » sous « Capacités »).
                // Une donnée passée ici (un nom, une adresse) ressort **verbatim** : une clé inconnue
                // du dictionnaire est rendue telle quelle.
                localize = true,
                style = socleMetaStyle(theme).copy(color = theme.secondaryText),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(
                    start = OnyxSocle.ScreenMargin,
                    end = OnyxSocle.ScreenMargin,
                    top = 2.dp,
                ),
            )
        }
    }
}

/** Une section : son intitulé en petites capitales, puis son contenu. */
@Composable
fun SocleSectionLabel(text: String) {
    val theme = LocalOnyxTheme.current
    BasicText(
        text = text.uppercase(),
        style = socleSectionStyle(theme).copy(color = theme.secondaryText),
        maxLines = 1,
        localize = false,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = OnyxSocle.ScreenMargin, end = OnyxSocle.ScreenMargin, top = OnyxSocle.Loose, bottom = OnyxSocle.Tight),
    )
}

// =================================================================================================
//  La rangée : tuile grise + libellé + description + contrôle
// =================================================================================================

/** La tuile grise d'une rangée : un carré de 44, coin 14, portant l'icône de la maison. */
@Composable
fun SocleTile(symbol: Int, tint: Color? = null, modifier: Modifier = Modifier) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = modifier
            .size(OnyxSocle.ThumbnailSize)
            .clip(RoundedCornerShape(OnyxSocle.RadiusField))
            .background(theme.fill),
        contentAlignment = Alignment.Center,
    ) {
        OnyxIcon(symbol = symbol, tint = tint ?: theme.textColor, size = 20.dp, contentDescription = null)
    }
}

/**
 * Une rangée de réglage : la tuile, le libellé, la description, et le contrôle à droite.
 *
 * Le séparateur est **en retrait** : il part de la marge (jamais sous la tuile ni devant elle),
 * comme la feuille de la maison. La hauteur est au moins [OnyxSocle.RowHeight] — une cible de 56.
 */
@Composable
fun SocleRow(
    label: String,
    description: String? = null,
    symbol: Int? = null,
    value: String? = null,
    chevron: Boolean = true,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.onyxTappable(onClick = onClick) else Modifier)
                .defaultMinSize(minHeight = OnyxSocle.RowHeight)
                .padding(horizontal = OnyxSocle.ScreenMargin, vertical = OnyxSocle.Tight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OnyxSocle.IconLabelGap),
        ) {
            if (symbol != null) SocleTile(symbol = symbol)
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = label,
                    style = socleTitleStyle(theme).copy(color = theme.textColor),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (description != null) {
                    Spacer(Modifier.height(2.dp))
                    BasicText(
                        text = description,
                        style = socleMetaStyle(theme).copy(color = theme.secondaryText),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when {
                trailing != null -> trailing()
                value != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(OnyxSocle.Tight)) {
                        BasicText(
                            text = value,
                            style = socleMetaStyle(theme).copy(color = theme.secondaryText),
                            maxLines = 1,
                        )
                        if (chevron) OnyxIcon(Symbols.ChevronRight, tint = theme.tertiaryText, size = 12.dp, contentDescription = null)
                    }
                }
                chevron -> OnyxIcon(Symbols.ChevronRight, tint = theme.tertiaryText, size = 12.dp, contentDescription = null)
            }
        }
        if (showDivider) {
            // Le séparateur en retrait : de la marge au bord droit.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = OnyxSocle.ScreenMargin, end = OnyxSocle.ScreenMargin)
                    .height(1.dp)
                    .background(theme.separator),
            )
        }
    }
}

/** La carte d'une section : la surface, un coin de carte, l'ombre de la famille. */
@Composable
fun SocleCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = OnyxSocle.ScreenMargin)
            // L'ombre de la famille, **appelée** : sans cet appel la carte annoncée « avec l'ombre
            // de la famille » reste plate — et un modificateur sans appelant sort du binaire.
            .socleShadow(OnyxSocle.ShadowMid, OnyxSocle.RadiusCard, theme.dark)
            .clip(RoundedCornerShape(OnyxSocle.RadiusCard))
            .background(theme.formSurfaceColor),
    ) { content() }
}

// =================================================================================================
//  Les pastilles et les compteurs
// =================================================================================================

/**
 * Une pastille de comptage : un disque gris, un nombre — jamais un accent.
 *
 * Le disque garde sa **taille optique** de 26 dp comme *plancher* (`defaultMinSize` : un minimum,
 * pas une largeur fixe — deux et trois chiffres l'élargissent), et le chiffre y est posé avec le
 * rembourrage vertical de la maison ([BadgeGeometry.PadVertical], 2 dp) au lieu de venir au contact
 * de la boîte peinte.
 */
@Composable
fun SocleCountPill(count: Int, modifier: Modifier = Modifier) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 26.dp, minHeight = 26.dp)
            .clip(CircleShape)
            .background(theme.fill),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = count.toString(),
            style = socleMetaStyle(theme).copy(color = theme.textColor, fontWeight = FontWeight.Medium),
            maxLines = 1,
            localize = false,
            modifier = Modifier.padding(
                horizontal = OnyxSocle.Tight,
                vertical = BadgeGeometry.PadVertical,
            ),
        )
    }
}

/** La pastille d'état (la joignabilité) : un point coloré et un mot, sur la gorge. */
@Composable
fun SocleStatusPill(text: String, dot: Color, modifier: Modifier = Modifier) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(OnyxSocle.RadiusCapsule))
            .background(theme.fill)
            .padding(horizontal = OnyxSocle.Tight, vertical = OnyxSocle.Tight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OnyxSocle.Tight),
    ) {
        Canvas(modifier = Modifier.size(8.dp)) { drawCircle(dot, radius = size.minDimension / 2f) }
        BasicText(text = text, style = socleMetaStyle(theme).copy(color = theme.textColor), maxLines = 1)
    }
}

/** Le monogramme gris d'un fournisseur ou d'un modèle : l'initiale, en capitales. */
@Composable
fun SocleMonogram(letter: String, modifier: Modifier = Modifier) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = modifier
            .size(OnyxSocle.ThumbnailSize)
            .clip(RoundedCornerShape(OnyxSocle.RadiusField))
            .background(theme.fill),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = letter.take(1).uppercase(),
            style = theme.body.copy(fontSize = OnyxSocle.Title, fontWeight = FontWeight.SemiBold, color = theme.secondaryText),
            maxLines = 1,
            localize = false,
        )
    }
}

// =================================================================================================
//  Les contrôles : segmenté, bascule, capsule de choix
// =================================================================================================

/** Un item d'un segmenté ou d'une capsule : son libellé, et son compteur s'il en a un. */
data class SocleItem(val label: String, val count: Int? = null)

/**
 * Le **segmenté** d'un sélecteur : une capsule de gorge, l'item actif **plein** à l'accent — le
 * **compteur dans le libellé** (« Compétences 5 »). Un seul cran actif.
 */
@Composable
fun SocleSegmented(
    items: List<SocleItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = OnyxSocle.ScreenMargin)
            .clip(RoundedCornerShape(OnyxSocle.RadiusCapsule))
            .background(theme.fill)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(OnyxSocle.ControlGap / 2),
    ) {
        items.forEachIndexed { index, item ->
            val active = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = OnyxSocle.TouchTarget - 8.dp)
                    .clip(RoundedCornerShape(OnyxSocle.RadiusCapsule))
                    .then(if (active) Modifier.background(theme.accent) else Modifier)
                    .onyxTappable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    BasicText(
                        text = item.label,
                        style = socleBodyStyle(theme).copy(
                            color = if (active) Color.White else theme.textColor,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        maxLines = 1,
                        softWrap = false,
                    )
                    if (item.count != null) {
                        BasicText(
                            text = item.count.toString(),
                            style = socleMetaStyle(theme).copy(color = if (active) Color.White else theme.secondaryText),
                            maxLines = 1,
                            localize = false,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Les **pastilles de réflexion** d'une feuille : une capsule, le cran choisi **plein** à l'accent.
 * C'est le seul accent de la feuille avec la coche de la ligne retenue.
 */
@Composable
fun SocleChoicePills(
    items: List<SocleItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = OnyxSocle.ScreenMargin),
        horizontalArrangement = Arrangement.spacedBy(OnyxSocle.ControlGap),
    ) {
        items.forEachIndexed { index, item ->
            val active = index == selectedIndex
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = 40.dp)
                    .clip(RoundedCornerShape(OnyxSocle.RadiusCapsule))
                    .background(if (active) theme.accent else theme.fill)
                    .onyxTappable { onSelect(index) }
                    .padding(horizontal = OnyxSocle.Loose, vertical = OnyxSocle.Tight),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = item.label,
                    style = socleBodyStyle(theme).copy(
                        color = if (active) Color.White else theme.textColor,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** La bascule de la maison : une gorge en capsule, le pouce qui glisse, l'accent quand c'est ouvert. */
@Composable
fun SocleSwitch(checked: Boolean, enabled: Boolean = true, onCheckedChange: ((Boolean) -> Unit)? = null) {
    val theme = LocalOnyxTheme.current
    val trackW = 51.dp
    val trackH = 31.dp
    val thumb = 27.dp
    Box(
        modifier = Modifier
            .width(trackW)
            .height(trackH)
            .clip(RoundedCornerShape(OnyxSocle.RadiusCapsule))
            .background(if (checked) theme.accent else theme.fill)
            .then(if (enabled && onCheckedChange != null) Modifier.onyxTappable { onCheckedChange(!checked) } else Modifier),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .size(thumb)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

// =================================================================================================
//  Le champ de recherche (la loupe + le placeholder), sans imagerie
// =================================================================================================

/** Le champ de recherche d'un écran sans imagerie : une capsule de gorge, la loupe, le texte. */
@Composable
fun SocleSearchField(
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = OnyxSocle.ScreenMargin)
            .defaultMinSize(minHeight = OnyxSocle.TouchTarget)
            .clip(RoundedCornerShape(OnyxSocle.RadiusCapsule))
            .background(theme.fill)
            .padding(horizontal = OnyxSocle.Loose),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OnyxSocle.IconLabelGap),
    ) {
        OnyxIcon(symbol = Symbols.MagnifyingGlass, tint = theme.secondaryText, size = 18.dp, contentDescription = null)
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                BasicText(text = placeholder, style = socleBodyStyle(theme).copy(color = theme.secondaryText), maxLines = 1)
            }
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = socleBodyStyle(theme).copy(color = theme.textColor),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(theme.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
