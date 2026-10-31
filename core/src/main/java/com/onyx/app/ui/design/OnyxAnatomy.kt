package com.onyx.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlin.math.roundToInt

/**
 * **L'anatomie d'une rangée de Réglages**, relevée sur la référence — les mesures exactes, au même
 * endroit, plutôt que des nombres dispersés dans chaque écran.
 *
 * Ce que la référence pose, et que ces constantes nomment :
 *  - une **vignette d'icône grise** carrée-arrondie en tête de rangée (facultative), l'icône grise
 *    dessus — jamais une pastille vive ;
 *  - le **libellé**, puis sa **description grise** sur deux lignes au plus ;
 *  - **le contrôle à droite**, dans une seule colonne ;
 *  - un **filet en retrait** entre deux rangées : il commence **après la vignette** et s'arrête
 *    **avant** le bord droit — il ne touche donc aucun bord ;
 *  - une carte à coins **très** arrondis ([OnyxSpace.CardCorner]), sans bordure.
 *
 * Ces nombres viennent de la référence telle qu'elle est écrite (14 dp de rembourrage de rangée,
 * 32 dp de vignette, 10 dp de rayon de vignette, 15 dp de glyphe, 56 dp de rangée qui ouvre une
 * page, 1 dp de filet). Ils vivent ici pour qu'une seule source décide de la forme d'une rangée.
 */
/**
 * **L'encre d'un texte sur son fond** : la couleur **composée** sur [background] (un gris de
 * système est translucide), puis écartée juste assez pour tenir [minRatio]. Sans la composition, la
 * mesure lit la couleur nue d'un gris à 60 % d'opacité et la croit très contrastée — alors qu'elle
 * se pose en gris moyen sur la carte.
 */
fun OnyxInkOn(ink: Color, background: Color, minRatio: Float = TextInkTarget): Color =
    OnyxContrast.distinctOn(OnyxContrast.over(ink, background), background, minRatio)

/**
 * La cible d'encre d'un **texte** : le seuil, plus une marge. Le fond réel d'une page s'écarte de
 * quelques unités du fond nominal (le halo pose un voile coloré) et le seuil se mesurait alors juste
 * en dessous — 4,46:1 pour une cible de 4,5:1, ce qui est un défaut de mesure autant qu'un défaut.
 */
const val TextInkTarget: Float = 4.75f

object Anatomy {
    /** Le rembourrage horizontal d'une rangée (la référence : 14 dp). */
    val RowPaddingH: Dp = 14.dp

    /** Le rembourrage vertical d'une rangée (la référence : 13 dp). */
    val RowPaddingV: Dp = 13.dp

    /** L'écart entre la vignette, le bloc de texte et le contrôle (la référence : 14 dp). */
    val RowGap: Dp = 14.dp

    /** L'écart entre le libellé et sa description (la référence : 1 dp). */
    val TitleDetailGap: Dp = 1.dp

    /** La hauteur minimale d'une rangée qui ouvre une page (la référence : 56 dp). */
    val NavRowMinHeight: Dp = 56.dp

    /** Le côté de la vignette d'icône grise (la référence : 32 dp). */
    val TileSize: Dp = 32.dp

    /** Le rayon de la vignette d'icône grise (la référence : 10 dp). */
    val TileCorner: Dp = 10.dp

    /** Le côté du glyphe **dans** la vignette grise (la référence : 15 dp). */
    val TileIcon: Dp = 15.dp

    /** Le côté du glyphe d'un chevron de fin de rangée (la référence : 16 dp). */
    val Chevron: Dp = 16.dp

    /** La hauteur minimale d'une pilule d'action (la référence : 32 dp). */
    val PillMinHeight: Dp = 32.dp

    /** Le côté du glyphe d'une pilule d'action (la référence : 15 dp). */
    val PillIcon: Dp = 15.dp

    /** Deux lignes de description au plus, comme la référence. */
    const val DetailLines: Int = 2

    /**
     * Le retrait du filet **quand la rangée porte une vignette** : il part du libellé, donc après la
     * vignette ([RowPaddingH] + [TileSize] + [RowGap]), et non du bord du panneau.
     */
    val DividerInsetWithTile: Dp = RowPaddingH + TileSize + RowGap

    /** Le filet ne touche pas le bord droit non plus : il s'arrête au rembourrage de rangée. */
    val DividerEndInset: Dp = RowPaddingH
}

/**
 * Le **filet d'une rangée de l'anatomie** : il commence **après la vignette** et s'arrête **avant**
 * le bord droit — il ne touche donc aucun bord, ce qui est ce qui distingue une liste dessinée d'une
 * liste bord à bord.
 */
@Composable
fun OnyxAnatomyDivider() {
    OnyxRowDivider(
        inset = Anatomy.DividerInsetWithTile,
        endInset = Anatomy.DividerEndInset,
    )
}

/**
 * La **vignette d'icône grise** d'une rangée : un carré arrondi de 32 dp, un fond gris de la famille
 * des creux, et le glyphe dedans en gris — c'est la vignette de la référence, et c'est ce qui
 * distingue une rangée de Réglages d'une pastille de couleur.
 *
 * Elle ne prend sa couleur vive que si l'appelant la lui donne ([tint]) : le gris est le défaut.
 */
@Composable
fun OnyxIconTile(
    symbol: Int,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = modifier
            .size(Anatomy.TileSize)
            .background(theme.fill, RoundedCornerShape(Anatomy.TileCorner)),
        contentAlignment = Alignment.Center,
    ) {
        OnyxIcon(
            symbol = symbol,
            // L'encre de la vignette est **mesurée** sur son fond : un gris posé au hasard tombait
            // sous 3:1 sur quelques thèmes (l'icône se devinait plus qu'elle ne se lisait).
            tint = tint ?: OnyxInkOn(theme.secondaryText, theme.fill, OnyxContrast.ICON_MIN),
            size = Anatomy.TileIcon,
        )
    }
}

/**
 * Une rangée qui **ouvre une page** : la vignette grise (facultative), le libellé, sa description
 * grise sur deux lignes au plus, puis le chevron — le tout sur une seule colonne à droite.
 *
 * C'est la `NavRow` de la référence : hauteur minimale 56 dp pour que la rangée reste une cible,
 * libellé et description empilés, chevron au bout.
 */
@Composable
fun OnyxDetailRow(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    detailLocalize: Boolean = true,
    symbol: Int? = null,
    localizeTitle: Boolean = true,
    onClick: (() -> Unit)? = null,
    description: String? = null,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Anatomy.NavRowMinHeight)
            .then(if (onClick != null) Modifier.onyxTappable(onClick = onClick) else Modifier)
            .padding(horizontal = Anatomy.RowPaddingH, vertical = Anatomy.RowPaddingV),
        horizontalArrangement = Arrangement.spacedBy(Anatomy.RowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        symbol?.let { OnyxIconTile(it) }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Anatomy.TitleDetailGap),
        ) {
            BasicText(
                text = title,
                localize = localizeTitle,
                style = theme.body.copy(color = theme.primaryText),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            detail?.takeIf { it.isNotBlank() }?.let {
                BasicText(
                    text = it,
                    localize = detailLocalize,
                    // La description grise tient 4,5:1 sur la carte : le gris secondaire du système
                    // tombait à 3,4:1 sur une carte blanche.
                    style = theme.footnote.copy(
                        color = OnyxInkOn(theme.secondaryText, theme.formSurfaceColor, OnyxContrast.TEXT_MIN),
                    ),
                    maxLines = Anatomy.DetailLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        OnyxIcon(
            symbol = Symbols.ChevronRight,
            tint = OnyxInkOn(theme.tertiaryText, theme.formSurfaceColor, OnyxContrast.ICON_MIN),
            size = Anatomy.Chevron,
            contentDescription = description,
        )
    }
}

/**
 * Une rangée **interrupteur** : la vignette grise (facultative), le libellé, sa description, et
 * l'interrupteur à droite. Toute la rangée bascule, comme la référence.
 */
@Composable
fun OnyxSwitchDetailRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    symbol: Int? = null,
    enabled: Boolean = true,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Anatomy.NavRowMinHeight)
            .then(if (enabled) Modifier.onyxTappable { onCheckedChange(!checked) } else Modifier)
            .padding(horizontal = Anatomy.RowPaddingH, vertical = Anatomy.RowPaddingV),
        horizontalArrangement = Arrangement.spacedBy(Anatomy.RowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        symbol?.let { OnyxIconTile(it) }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Anatomy.TitleDetailGap),
        ) {
            BasicText(
                text = title,
                style = theme.body.copy(color = if (enabled) theme.primaryText else theme.secondaryText),
            )
            BasicText(
                text = detail,
                style = theme.footnote.copy(color = OnyxInkOn(theme.secondaryText, theme.formSurfaceColor, OnyxContrast.TEXT_MIN)),
                maxLines = Anatomy.DetailLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OnyxSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/**
 * Une **pilule d'action à bordure** : le libellé, son petit glyphe avant, un filet de contour et
 * aucun fond. C'est la forme que la référence donne à ses boutons secondaires de rangée (« Vérifier »,
 * « Choisir une photo ») — un bouton qui se lit comme une action, pas comme un aplat.
 *
 * La cible tactile fait 44 dp de haut pour rester conforme ; la pilule dessinée en fait 32.
 */
@Composable
fun OnyxPillButton(
    text: String,
    symbol: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    /** L'annonce lue par les lecteurs d'écran, quand le libellé seul ne dit pas ce qu'il fait. */
    description: String? = null,
) {
    val theme = LocalOnyxTheme.current
    // **Le contour est le filet de la maquette, et rien d'autre.** Il portait une encre de la référence
    // mesurée à 3:1 (`OnyxInkOn`) — donc **opaque** — doublée d'un `theme.quaternaryText` tout aussi
    // opaque quand la pilule travaille : deux encadrements pleins autour d'un bouton de rangée. La
    // planche, elle, ne pose qu'un filet à 12 % ([OnyxEdge.fil]) ; c'est lui qui dessine la pilule,
    // au repos comme en travail.
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .then(if (!busy) Modifier.onyxTappable(onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp)
            .semantics { description?.let { contentDescription = it } },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = Anatomy.PillMinHeight)
                .clip(CircleShape)
                .border(OnyxSpace.Stroke, OnyxEdge.fil(theme), CircleShape)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OnyxIcon(
                symbol = symbol,
                tint = if (busy) theme.secondaryText else theme.accent,
                size = Anatomy.PillIcon,
            )
            BasicText(
                text = text,
                style = theme.footnote.copy(
                    color = if (busy) theme.secondaryText else theme.primaryText,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
        }
    }
}

/**
 * Une rangée **d'information** : le titre, puis sa valeur en dessous en gris — la forme de la
 * référence pour la version et l'état de la passerelle en bas de page.
 *
 * C'est une information, pas une action : aucun chevron, aucun contour, et la valeur peut se replier
 * sur deux lignes sans jamais couper un mot au milieu (le repli est sur les mots, l'asthme est
 * borné à deux lignes puis abrégé).
 */
@Composable
fun OnyxInfoRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueLocalize: Boolean = true,
) {
    val theme = LocalOnyxTheme.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Anatomy.RowPaddingH, vertical = Anatomy.RowPaddingV),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicText(text = title, style = theme.body.copy(color = theme.primaryText))
        BasicText(
            text = value,
            localize = valueLocalize,
            style = theme.footnote.copy(color = OnyxInkOn(theme.secondaryText, theme.formSurfaceColor, OnyxContrast.TEXT_MIN)),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * L'**en-tête d'une page de Réglages** : une grande flèche de retour, puis le titre en très grand
 * sous elle, aligné à gauche — la forme de la référence, sans aucune autre chrome au-dessus.
 *
 * Les [actions] gardent leur place à droite de la flèche : c'est là que le contrôle du volet se
 * pose quand les deux panneaux sont ouverts, pour que replier la liste depuis les Réglages continue
 * de fonctionner.
 */
@Composable
fun OnyxLargePageHeader(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    localizeTitle: Boolean = true,
    actions: (@Composable () -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .onyxClickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    // La « grande flèche » : le chevron du retour maison, à 20 dp et retourné — la
                    // même flèche que les autres pages, simplement plus grande.
                    OnyxIcon(
                        symbol = Symbols.ChevronRight,
                        tint = theme.secondaryText,
                        size = 24.dp,
                        contentDescription = "Back",
                        modifier = Modifier.rotate(180f),
                    )
                }
            } else {
                Box(Modifier.size(44.dp))
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions?.invoke()
            }
        }
        BasicText(
            text = title,
            localize = localizeTitle,
            style = theme.largeTitle.copy(
                color = theme.primaryText,
                fontWeight = FontWeight.SemiBold,
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .semantics { heading() },
        )
    }
}

/** Le côté du pouce du curseur de taille de texte (la référence : 22 dp). */
private val TextSizeThumb: Dp = 22.dp

/**
 * Le **curseur de taille de texte** de la référence : un « A » à chaque bout, des crans
 * intermédiaires, le pouce blanc qui glisse par crans, et **le nom du cran courant sous la piste**
 * (celui du milieu se dit « Défaut »).
 *
 * Il se règle au toucher comme au glissement ; les lecteurs d'écran l'ajustent comme un curseur
 * (`progressBarRangeInfo`).
 *
 * @param labels un libellé par cran, dans l'ordre — c'est lui qui s'affiche sous la piste.
 */
@Composable
fun OnyxTextSizeSlider(
    selectedIndex: Int,
    labels: List<String>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    if (labels.isEmpty()) return
    val last = labels.lastIndex
    val index = selectedIndex.coerceIn(0, last)
    val onPick by rememberUpdatedState(onSelect)
    val current by rememberUpdatedState(index)
    // Seul un nouveau cran est enregistré : un glissement rapporte chaque mouvement.
    val pick = { to: Int -> if (to != current) onPick(to) }
    // Les « A » gardent leur taille quels que soient les réglages : la piste ne bouge pas sous le doigt.
    val fontScale = LocalDensity.current.fontScale

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
                BasicText(
                    text = "A",
                    localize = false,
                    style = theme.footnote.copy(
                        // Le « A » du petit bout : **11 sp déclarés**, pas 12 divisé par l'échelle.
                        // Divisé, il tombait à 9,2 sp à police 1,3× et à 6 sp à police 2× — un
                        // glyphe illisible, et une taille qui n'existe pas dans l'app.
                        fontSize = OnyxSocle.Micro,
                        color = OnyxInkOn(theme.secondaryText, theme.formSurfaceColor, OnyxContrast.TEXT_MIN),
                    ),
                )
            }
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .semantics {
                        contentDescription = OnyxLocalization["Text size"]
                        stateDescription = labels[index]
                        progressBarRangeInfo = ProgressBarRangeInfo(index.toFloat(), 0f..last.toFloat(), steps = (last - 1).coerceAtLeast(0))
                        if (last > 0) {
                            setProgress { target ->
                                pick(target.roundToInt().coerceIn(0, last))
                                true
                            }
                        }
                    }
                    .pointerInput(last) {
                        if (last == 0) return@pointerInput
                        // Les crans sont à un demi-pouce des bords, là où le centre du pouce peut aller.
                        val inset = (TextSizeThumb / 2).toPx()
                        fun at(x: Float) = ((x - inset) / (size.width - inset * 2) * last).roundToInt().coerceIn(0, last)
                        detectTapGestures { pick(at(it.x)) }
                    }
                    .pointerInput(last) {
                        if (last == 0) return@pointerInput
                        val inset = (TextSizeThumb / 2).toPx()
                        fun at(x: Float) = ((x - inset) / (size.width - inset * 2) * last).roundToInt().coerceIn(0, last)
                        detectHorizontalDragGestures { change, _ -> pick(at(change.position.x)) }
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                val fraction = if (last == 0) 0f else index.toFloat() / last
                val inset = TextSizeThumb / 2
                val track = Modifier.padding(horizontal = inset - 3.dp)
                Box(track.fillMaxWidth().height(4.dp).background(theme.tertiarySurfaceColor, CircleShape))
                Box(
                    track
                        .width(6.dp + (maxWidth - TextSizeThumb) * fraction)
                        .height(4.dp)
                        .background(theme.accent, CircleShape),
                )
                // Les crans, chacun un point centré là où tombe le centre du pouce ; les passés à l'accent.
                Row(track.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    labels.forEachIndexed { i, _ ->
                        Box(
                            Modifier
                                .size(6.dp)
                                .background(
                                    if (i <= index) theme.accent
                                    else OnyxInkOn(theme.quaternaryText, theme.tertiarySurfaceColor, OnyxContrast.ICON_MIN),
                                    CircleShape,
                                ),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .offset { IntOffset(((maxWidth - TextSizeThumb) * fraction).roundToPx(), 0) }
                        .size(TextSizeThumb)
                        // **L'ombre unique et le filet unique.** C'était `shadow(4.dp, …)` avec une
                        // bordure à 0,5 dp du séparateur de la référence : un troisième réglage d'ombre et une
                        // deuxième épaisseur de trait pour le même objet — le curseur d'un réglage,
                        // qui n'a aucune raison de différer d'une pastille ou d'une carte.
                        .shadow(OnyxShadow.Float, CircleShape, clip = false)
                        .background(Color.White, CircleShape)
                        .border(OnyxSpace.Stroke, OnyxSurfaces.hairline(theme), CircleShape),
                )
            }
            Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
                BasicText(
                    text = "A",
                    localize = false,
                    style = theme.footnote.copy(fontSize = OnyxSocle.Title, color = theme.secondaryText),
                )
            }
        }
        BasicText(
            text = labels[index],
            style = theme.footnote.copy(
                // La mention sous la piste est un **texte** : elle tient 4,5:1, pas 3:1.
                color = OnyxInkOn(theme.accent, theme.formSurfaceColor, OnyxContrast.TEXT_MIN),
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    // Centré sous la piste, pas sous toute la carte.
                    val placeable = measurable.measure(constraints)
                    layout(constraints.maxWidth, placeable.height) {
                        placeable.placeRelative((constraints.maxWidth - placeable.width) / 2, 0)
                    }
                },
        )
    }
}
