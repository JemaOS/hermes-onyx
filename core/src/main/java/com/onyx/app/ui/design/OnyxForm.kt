package com.onyx.app.ui.design

import android.content.ClipData
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.localization.resolveOnyxText
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Les pièces d'un formulaire la référence : une liste groupée sur une surface arrondie, avec ses rangées
 * et ses filets — le `Form` de la référence, sans rien de Material.
 */
@Composable
fun OnyxFormSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    localizeHeader: Boolean = true,
    localizeFooter: Boolean = true,
    content: @Composable () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Column(modifier = modifier.fillMaxWidth().padding(top = OnyxSpace.SectionGap)) {
        header?.let {
            BasicText(
                text = if (localizeHeader) OnyxLocalization[it].uppercase() else it.uppercase(),
                localize = false,
                // Les petites capitales grises tiennent 4,5:1 sur la page : le gris secondaire du
                // système tombait à 3,3:1 sur la page claire, sous le seuil des textes.
                style = theme.footnote.copy(
                    color = OnyxInkOn(theme.secondaryText, theme.formPageColor, OnyxContrast.TEXT_MIN),
                ),
                modifier = Modifier.padding(start = OnyxSpace.BarInset, bottom = OnyxSpace.Half),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OnyxSpace.ScreenMargin)
                .background(theme.formSurfaceColor, RoundedCornerShape(OnyxSpace.CardCorner)),
        ) {
            content()
        }
        footer?.let {
            BasicText(
                text = it,
                localize = localizeFooter,
                style = theme.footnote.copy(
                    color = OnyxInkOn(theme.secondaryText, theme.formPageColor, OnyxContrast.TEXT_MIN),
                ),
                modifier = Modifier.padding(start = OnyxSpace.BarInset, end = OnyxSpace.BarInset, top = OnyxSpace.Half),
            )
        }
    }
}

/** Le filet entre deux rangées, **en retrait des deux bords**.
 *
 * [inset] écarte le filet du bord **gauche** ; [endInset] l'écarte du bord **droit**. Par défaut les
 * deux valent le rembourrage de carte, si bien qu'un filet ne touche plus aucun bord — c'est la
 * forme de la référence, et c'est ce qui distingue une liste **dessinée** d'une liste bord à bord.
 *
 * Une rangée qui porte une **vignette** de tête utilise [OnyxSpace.separatorInset] : le filet part
 * alors du libellé, après la vignette, et non du bord du panneau.
 */
@Composable
fun OnyxRowDivider(
    inset: androidx.compose.ui.unit.Dp = OnyxSpace.CardPadding,
    endInset: androidx.compose.ui.unit.Dp = inset,
) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = inset, end = endInset)
            .height(0.5.dp)
            .background(theme.separator),
    )
}

/** Une rangée de formulaire : libellé à gauche, contenu à droite. */
@Composable
fun OnyxRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    /** L'appui long de la rangée, quand elle porte un menu de contexte (`contextMenu` d'la référence). */
    onLongPress: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null || onLongPress != null) {
                    Modifier.onyxTappable(onLongPress = onLongPress, onClick = onClick ?: {})
                } else {
                    Modifier
                },
            )
            .padding(horizontal = OnyxSpace.CardPadding, vertical = OnyxSpace.Snug),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OnyxSpace.BlockGap),
        content = content,
    )
}

/**
 * Un champ : le libellé à gauche, la valeur éditable alignée à droite.
 *
 * Au repos la valeur se cale à droite, son début coupé comme dans la référence ; pendant l'édition
 * elle défile pour que le curseur reste visible (voir [OnyxEditableValue]).
 */
@Composable
fun OnyxFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    secret: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    /** Un geste « Coller » à côté du champ : le presse-papier posé d'un coup, sans le clavier. */
    paste: Boolean = false,
) {
    val theme = LocalOnyxTheme.current
    val clipboard = LocalClipboard.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    OnyxRow(modifier = modifier) {
        BasicText(
            text = label,
            style = theme.body.copy(color = theme.primaryText),
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        OnyxEditableValue(
            value = value,
            onValueChange = onValueChange,
            textStyle = theme.body.copy(color = theme.secondaryText),
            placeholder = placeholder,
            placeholderStyle = theme.body.copy(color = theme.tertiaryText),
            secret = secret,
            keyboardType = keyboardType,
            imeAction = imeAction,
            modifier = Modifier.weight(1.3f),
        )
        if (paste) {
            // « Coller » d'un coup : le presse-papier posé tel quel, **sans passer par le clavier** —
            // c'est ce chemin qui n'a jamais rien coupé (voir OnyxFieldPaste).
            OnyxIcon(
                symbol = Symbols.DocOnClipboard,
                tint = theme.tertiaryText,
                size = 15.dp,
                modifier = Modifier.onyxTappable {
                    scope.launch {
                        val text = OnyxFieldPaste.text(clipboardText(clipboard))
                        if (text.isNotEmpty()) onValueChange(text)
                    }
                },
            )
        }
        if (secret) {
            // Un champ secret ne montre jamais sa fin : on l'offre par la copie, jamais par l'œil.
            OnyxIcon(
                symbol = Symbols.DocOnDoc,
                tint = theme.tertiaryText,
                size = 15.dp,
                modifier = Modifier.onyxTappable {
                    clipboard.nativeClipboard.setPrimaryClip(ClipData.newPlainText(label, value))
                },
            )
        }
    }
}

/** Le texte du presse-papier, ou vide — jamais une exception. */
suspend fun clipboardText(clipboard: androidx.compose.ui.platform.Clipboard): String {
    val clip = runCatching { clipboard.getClipEntry() }.getOrNull()?.clipData ?: return ""
    if (clip.itemCount <= 0) return ""
    return clip.getItemAt(0).text?.toString().orEmpty()
}

/**
 * Une rangée qui ouvre autre chose : la vignette d'icône, le libellé, sa valeur en description grise,
 * puis le chevron — l'anatomie d'une rangée de la référence.
 *
 * Le `symbol` se pose sur la **vignette grise** ([OnyxIconTile]) : la valeur prend alors la ligne de
 * description, sur deux lignes au plus, comme la `NavRow` de la référence. Rien n'est perdu — le
 * libellé et sa valeur restent l'un sous l'autre.
 */
@Composable
fun OnyxValueRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    symbol: Int? = null,
    tint: Color? = null,
    localizeValue: Boolean = true,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Anatomy.NavRowMinHeight)
            .onyxTappable(onClick = onClick)
            .padding(horizontal = Anatomy.RowPaddingH, vertical = Anatomy.RowPaddingV),
        horizontalArrangement = Arrangement.spacedBy(Anatomy.RowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        symbol?.let { OnyxIconTile(symbol = it, tint = tint) }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Anatomy.TitleDetailGap),
        ) {
            BasicText(
                text = label,
                style = theme.body.copy(color = theme.primaryText),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            value.takeIf { it.isNotBlank() }?.let {
                BasicText(
                    text = it,
                    localize = localizeValue,
                    style = theme.footnote.copy(
                        color = OnyxInkOn(theme.secondaryText, theme.formSurfaceColor, OnyxContrast.TEXT_MIN),
                    ),
                    maxLines = Anatomy.DetailLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        OnyxIcon(
            Symbols.ChevronRight,
            tint = OnyxInkOn(theme.tertiaryText, theme.formSurfaceColor, OnyxContrast.ICON_MIN),
            size = Anatomy.Chevron,
        )
    }
}

/**
 * Le résumé d'un `DisclosureGroup` : le titre, la valeur à droite, et le chevron qui pivote.
 *
 * la référence pose ce chevron tout seul, à la fin de la rangée ; ici il se tourne d'un quart de tour
 * quand le groupe s'ouvre, comme dans les Réglages d'la référence.
 */
@Composable
fun OnyxDisclosureSummary(
    title: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: @Composable () -> Unit = {},
) {
    val theme = LocalOnyxTheme.current
    OnyxRow(modifier = modifier, onClick = onClick) {
        BasicText(
            text = title,
            style = theme.body.copy(color = theme.primaryText),
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            value()
            OnyxIcon(
                symbol = Symbols.ChevronRight,
                tint = theme.tertiaryText,
                size = 11.dp,
                modifier = Modifier.rotate(if (expanded) 90f else 0f),
            )
        }
    }
}

/**
 * Une rangée avec un interrupteur, comme `Toggle` dans un `Form`.
 *
 * La forme est celle de la référence : la **vignette d'icône grise** en tête quand un [symbol] est
 * donné, le libellé, l'interrupteur à droite — et toute la rangée qui bascule.
 */
@Composable
fun OnyxToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    symbol: Int? = null,
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
        BasicText(
            text = label,
            style = theme.body.copy(color = if (enabled) theme.primaryText else theme.secondaryText),
            modifier = Modifier.weight(1f),
        )
        OnyxSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/**
 * L'interrupteur d'la référence : une piste arrondie, le pouce à droite quand il est allumé.
 *
 * La piste **allumée** est celle de la référence — **l'accent du thème** (le bleu de la maison), et
 * non le vert du système : c'est le contrôle que la référence montre dans ses rangées. Elle est
 * écartée de la page juste assez pour tenir 3:1 ([OnyxContrast.distinctOn]) : sur le thème sombre en
 * apparence claire, l'accent tombait sous le seuil et l'allumé ne se distinguait plus de l'éteint au
 * premier regard. La piste **éteinte** reste le gris des creux ([fill]). Le pouce reste blanc : il
 * se pose **sur** la piste, dont le sépare son ombre, pas la page.
 *
 * Les dimensions sont celles de la référence : une piste de 46×28, un pouce de 22.
 */
@Composable
fun OnyxSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val theme = LocalOnyxTheme.current
    val track = if (checked) OnyxContrast.distinctOn(theme.accent, theme.formPageColor) else theme.fill
    Box(
        modifier = modifier
            .size(width = 46.dp, height = 28.dp)
            .background(track, RoundedCornerShape(50))
            .onyxTappable(enabled = enabled) { onCheckedChange(!checked) },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 3.dp)
                .size(22.dp)
                .background(Color.White, RoundedCornerShape(50)),
        )
    }
}

/** Le contrôle segmenté d'la référence (`AdaptiveSegmented`). */
@Composable
fun OnyxSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Un symbole par option, avant son libellé (le `Picker` du thème de la référence y met un
     * téléphone, un soleil et une lune). Vide pour un contrôle sans icônes.
     */
    icons: List<Int?> = emptyList(),
    /**
     * **La piste plate du volet** (apparence claire seulement) : une piste gris clair, la pastille
     * de l'option retenue **blanche et opaque**, son libellé noir, l'autre gris.
     *
     * Pourquoi un drapeau plutôt qu'un second composant : le contrôle segmenté est **un** contrôle,
     * avec sa mesure de libellé ([SegmentedFit]) et son animation de pastille ([MotionFamily.Tab]).
     * En écrire un deuxième pour changer trois couleurs aurait dupliqué les deux, et c'est ainsi
     * qu'un contrôle se met à ne plus glisser comme son voisin. Le drapeau est **ignoré en sombre** :
     * là, la piste garde son verre, exactement comme avant.
     */
    flat: Boolean = false,
    /**
     * **La pilule pleine** : l'option retenue est un **aplat d'accent**, son libellé en **blanc** —
     * c'est la « pilule pleine » de la planche validée
     * (`.segi.on{background:var(--accfill);color:#fff}`, `hcss.py:86`).
     *
     * Pourquoi un drapeau et non un second composant : la même raison que [flat] — le contrôle
     * segmenté est **un** contrôle, avec sa mesure de libellé ([SegmentedFit]) et son animation de
     * pastille ([MotionFamily.Tab]). Seule la **couleur de l'option retenue** change, et elle change
     * dans les deux apparences : c'est le seul accent du volet Discussions.
     */
    filled: Boolean = false,
) {
    val theme = LocalOnyxTheme.current
    // Le contrôle segmenté d'la référence : une capsule sombre, et le choix retenu dans une pastille plus
    // claire, aux extrémités rondes.
    //
    // Au **clair plat** (le volet), la capsule cède la place à la piste du système : le gris des
    // creux de la famille plate ([flatTrackColor]), la pastille blanche, et la pastille prend la
    // petite ombre qui la fait lire comme posée **sur** la piste — c'est le seul trait de la famille
    // plate, et il remplace le liseré que le verre portait.
    val flatTrack = flat && !theme.dark
    // **La pastille du contrôle segmenté** : le barreau [OnyxSurfaces.capsule] en sombre — la même
    // marche que la capsule de l'onglet actif, donc les deux capsules de l'app se ressemblent — et
    // la carte blanche en clair, où la séparation vient du filet et de **l'ombre unique**
    // ([OnyxShadow.Float]).
    //
    // C'était `Color.White.copy(alpha = 0.22f)` sur le verre sombre : un voile blanc sur un voile
    // anthracite, deux translucides empilés, donc une pastille dont la valeur dépendait de ce qui
    // passait dessous — l'inverse d'une échelle de surfaces.
    val selectedPill = if (filled) {
        // **La pilule pleine de la planche** : l'aplat d'accent, mesuré pour porter son mot.
        OnyxContrast.filled(theme.accent)
    } else if (flatTrack) {
        flatTrackPillColor(theme)
    } else if (theme.dark) {
        OnyxSurfaces.capsule(theme)
    } else {
        Color.White.copy(alpha = 0.95f)
    }

    if (options.isEmpty()) return
    val safeSelection = selectedIndex.coerceIn(0, options.lastIndex)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .then(
                if (flatTrack) {
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(flatTrackColor(theme))
                } else {
                    Modifier.frostedBar(FrostSurface.SEGMENTED, RoundedCornerShape(50))
                },
            )
            .padding(OnyxSpace.Hair),
    ) {
        val segmentWidth = maxWidth / options.size
        // Le libellé le plus large décide de la taille : à police 1,3× sur 360 dp, quatre cases
        // ne tiennent plus à 16 pt et la dernière était **coupée** (il n'en restait que la
        // bordure, lue comme un trait parasite). On **mesure** chaque libellé sous la densité
        // courante et on descend d'un demi-point jusqu'à ce que le plus large tienne.
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        // Ce que le texte peut réellement occuper dans sa case : sa largeur moins le dégagement.
        val availableWidth = SegmentedFit.availableWidthDp(segmentWidth.value)
        // Une case qui porte une icône lui réserve sa place, mesurée une fois pour toutes.
        val iconCount = icons.count { it != null }
        val iconAllowance = if (iconCount == 0) 0f else SegmentedFit.availableWidthDp(segmentWidth.value) / options.size * 0.18f
        val fontSizeSp = SegmentedFit.fontSize(availableWidth) { size ->
            val style = TextStyle(fontSize = size.sp, fontWeight = FontWeight.SemiBold)
            options.maxOf { label ->
                measurer.measure(text = label, style = style, maxLines = 1).size.width
                    .let { px -> with(density) { px.toDp().value } }
            }
        }
        val labelSize = fontSizeSp.sp
        // Les deux encres de la piste plate : l'option retenue en **noir** — le texte primaire du
        // **système** (c'est la règle d'un formulaire : ses encres ne viennent jamais de la palette
        // des réponses) —, l'autre en **gris mesuré** sur la piste, jamais `secondaryLabel` posé au
        // hasard, qui tombe sous 4,5:1 sur un gris clair.
        val activeInk = if (filled) OnyxContrast.inkOn(selectedPill) else theme.primaryText
        val idleInk = if (flatTrack) {
            OnyxContrast.dimmed(theme.primaryText, flatTrackColor(theme), OnyxContrast.TEXT_MIN)
        } else {
            theme.secondaryText
        }
        val pillOffset by animateDpAsState(
            targetValue = segmentWidth * safeSelection,
            // La pastille du contrôle segmenté glisse **avec la famille de la sélection**
            // ([MotionFamily.Tab]) : c'est le même geste que le choix d'un onglet — la même courbe,
            // la même durée, 240 ms — et non une deuxième animation qui lui ressemblerait. Sous
            // « Réduire les animations », la pastille est à sa place d'emblée (`snap()`).
            animationSpec = MotionFamily.Tab.animated(),
            label = "segmentedSelection",
        )
        Box(
            modifier = Modifier
                .offset(x = pillOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                // Au clair plat, la pastille blanche se lit par son **filet** et non par une ombre :
                // l'app n'a qu'une ombre ([OnyxShadow.Float]) et elle est réservée à ce qui **flotte
                // au-dessus du contenu**. Une pastille dans une piste est posée, pas flottante — une
                // piste n'a pas de profondeur à donner.
                .then(
                    if (flatTrack && !filled) {
                        Modifier.border(OnyxSpace.Stroke, OnyxSurfaces.hairline(theme), RoundedCornerShape(50))
                    } else {
                        Modifier
                    },
                )
                .background(selectedPill, RoundedCornerShape(50)),
        )
        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { index, label ->
                val active = index == safeSelection
                val icon = icons.getOrNull(index)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onyxTappable { onSelect(index) }
                        // **La case porte son nom.** Relevé d'arbre : le nœud cliquable d'une case
                        // était muet, son libellé vivant dans le `BasicText` **enfant** — le volet
                        // comptait pour cela « Chats » et « Bots » parmi les zones sans libellé.
                        // Le libellé est celui de la case, traduit une fois (clé connue → français).
                        .tapLabel(resolveOnyxText(label))
                        .padding(vertical = OnyxSpace.Half, horizontal = OnyxSpace.GlyphGap),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (icon != null) {
                            OnyxIcon(
                                symbol = icon,
                                tint = if (active) activeInk else idleInk,
                                size = 14.dp,
                            )
                        }
                        BasicText(
                            text = label,
                            style = androidx.compose.ui.text.TextStyle(
                                color = if (active) activeInk else idleInk,
                                fontSize = if (icon != null) (labelSize.value - iconAllowance * 0.4f).coerceAtLeast(10f).sp else labelSize,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                            // Jamais de repli sur deux lignes : un libellé entier sur une ligne, la
                            // taille se réduisant pour qu'il tienne. `available` documente la place
                            // offerte au texte de chaque case.
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

/** Le titre d'une page de Réglages : « ← titre » et l'action à droite. */
@Composable
fun OnyxPageHeader(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    actionEnabled: Boolean = true,
    backLabel: String = "Back",
    localizeTitle: Boolean = true,
    /**
     * Un contrôle à gauche, avant le titre. Le volet des deux panneaux y met son `sidebar.left`
     * quand la barre n'a pas de retour à porter : replié, le détail garde le contrôle dans sa
     * barre. Il tient la place du retour (50 dp) pour que le titre reste centré.
     */
    leading: (@Composable () -> Unit)? = null,
    /**
     * Affiche le chevron du bouton de gauche. Faux pour un mot seul comme « Skip » de l'écran de
     * configuration (il n'y a pas de retour en arrière, seulement une sortie).
     */
    showBackChevron: Boolean = true,
    /**
     * Un symbole à droite, avant l'action : l'état de la liaison d'un écran (`KanbanView` y met
     * la diffusion du tableau). `trailingLabel` est son libellé d'accessibilité, localisé.
     */
    trailingSymbol: Int? = null,
    trailingTint: Color? = null,
    trailingLabel: String? = null,
    /**
     * Un contrôle à droite, **avant** l'action : l'écran d'un projet y met son menu « … » (créer,
     * renommer, retirer), à côté du « Sélectionner » de la sélection multiple.
     */
    trailing: (@Composable () -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = OnyxSpace.CardPadding, vertical = OnyxSpace.BlockGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            // Le retour garde la priorité : un écran qui a un retour à porter ne le perd jamais
            // au profit du contrôle du volet (celui-ci ne se dessine que sans retour).
            onBack != null -> Row(
                modifier = Modifier.onyxClickable(onClick = onBack),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Hair),
            ) {
                if (showBackChevron) {
                    OnyxIcon(
                        Symbols.ChevronRight,
                        tint = theme.accent,
                        size = 15.dp,
                        modifier = Modifier.rotate(180f),
                    )
                }
                BasicText(text = backLabel, style = theme.body.copy(color = theme.accent))
            }

            leading != null -> Box(
                modifier = Modifier.width(50.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                leading()
            }

            else -> Box(Modifier.size(50.dp))
        }
        BasicText(
            text = title,
            localize = localizeTitle,
            style = theme.headline.copy(color = theme.primaryText, textAlign = TextAlign.Center),
            modifier = Modifier.weight(1f),
            // Le titre **ne se replie jamais** : sur un volet étroit (Honor replié, 340 dp), il se
            // coupait au milieu du mot (« Kanba / n ») et se posait par-dessus le retour, qui est à
            // sa gauche. Une seule ligne, abrégée par des points : le mot reste lisible, la barre
            // reste une barre, et le retour garde sa place.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailingSymbol != null) {
            OnyxIcon(
                symbol = trailingSymbol,
                tint = trailingTint ?: theme.secondaryText,
                size = 16.dp,
                contentDescription = trailingLabel,
                modifier = Modifier.padding(
                    end = if (action != null && onAction != null) 10.dp else 0.dp,
                ),
            )
        }
        trailing?.let {
            it()
            if (action != null && onAction != null) Spacer(Modifier.size(10.dp))
        }
        if (action != null && onAction != null) {
            BasicText(
                text = action,
                style = theme.body.copy(color = theme.accent, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.onyxClickable(enabled = actionEnabled, onClick = onAction),
            )
        } else if (trailingSymbol == null && trailing == null) {
            Box(Modifier.size(50.dp))
        }
    }
}

/**
 * `ContentUnavailableView` : un symbole gris, un titre, une phrase — au centre, pour un état vide.
 *
 * Les écrans qui portent un `ContentUnavailableView` la référence (Kanban, Cron, les horaires d'un bot)
 * s'en servent avec le symbole de la référence : c'est le même encombrement partout (38 pt de glyphe,
 * `title3` semi-gras, `footnote` secondaire), quel que soit l'écran qui le pose.
 */
@Composable
fun OnyxEmptyState(
    @androidx.annotation.DrawableRes symbol: Int,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    /** La phrase de l'état vide est un libellé de Onyx : elle suit la langue, comme le titre. */
    localizeDescription: Boolean = true,
) {
    val theme = LocalOnyxTheme.current
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OnyxIcon(symbol = symbol, tint = theme.secondaryText, size = 38.dp)
        BasicText(
            text = title,
            style = theme.title3.copy(
                color = theme.primaryText,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            ),
        )
        description?.let { text ->
            BasicText(
                text = text,
                localize = localizeDescription,
                style = theme.footnote.copy(color = theme.secondaryText, textAlign = TextAlign.Center),
            )
        }
    }
}

/**
 * Le champ de texte d'la référence, sans décoration : le fond et le cadre viennent du conteneur.
 * Compose n'expose que `BasicTextField`, qui ne ressemble à rien — c'est celui-ci qu'on habille.
 *
 * Sur une seule ligne, la valeur suit [OnyxEditableValue] : on lit toute la valeur en déplaçant le
 * curseur, même quand elle dépasse le champ.
 */
@Composable
fun OnyxTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle? = null,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 6,
    alignment: TextAlign = TextAlign.Start,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    onSend: (() -> Unit)? = null,
    /** Mot de passe ou secret : le texte reste masqué, sans changer les autres champs. */
    secret: Boolean = false,
    /**
     * Le nombre de lignes sur lesquelles la valeur peut **se replier**.
     *
     * À 1 (défaut) le champ est une ligne qui défile. Au-delà, la valeur s'écrit sur plusieurs
     * lignes et **son début reste sous les yeux** : c'est ce qu'exige une adresse longue — un champ
     * d'une ligne cale la fin (le curseur y est), donc l'adresse ne se lit que par son bout.
     */
    wrapLines: Int = 1,
) {
    val theme = LocalOnyxTheme.current
    val textStyle = (style ?: theme.body).copy(color = theme.primaryText)
    val placeholderStyle = textStyle.copy(color = theme.tertiaryText)

    if (singleLine || wrapLines > 1) {
        OnyxEditableValue(
            value = value,
            onValueChange = onValueChange,
            textStyle = textStyle,
            placeholder = placeholder,
            placeholderStyle = placeholderStyle,
            secret = secret,
            alignment = alignment,
            keyboardType = keyboardType,
            imeAction = imeAction,
            onSend = onSend,
            wrapLines = wrapLines,
            modifier = modifier,
        )
        return
    }

    Box(modifier = modifier) {
        if (value.isEmpty() && placeholder.isNotEmpty()) {
            BasicText(
                text = placeholder,
                style = placeholderStyle.copy(textAlign = alignment),
                maxLines = maxLines,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = textStyle.copy(textAlign = alignment),
            singleLine = false,
            maxLines = maxLines,
            cursorBrush = SolidColor(theme.accent),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (secret) KeyboardType.Password else keyboardType,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(onSend = onSend?.let { action -> { action() } }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Une valeur éditable sur une seule rangée.
 *
 * Au repos (champ non focalisé) elle garde le regard de la référence : alignée à droite, sa fin
 * visible, son début coupé à gauche. Dès qu'on l'édite, le texte défile horizontalement pour que le
 * curseur reste dans la fenêtre visible, quelle que soit la longueur de la valeur — un collage au
 * milieu, une sélection, une police agrandie ne cachent plus la lettre sous le curseur.
 *
 * Le champ est mesuré sans borne horizontale : la valeur tient sur une ligne, et c'est la position
 * calculée par [OnyxFieldScroll] qui décide de la partie montrée.
 */
@Composable
fun OnyxEditableValue(
    value: String,
    onValueChange: (String) -> Unit,
    textStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    placeholderStyle: androidx.compose.ui.text.TextStyle = textStyle,
    secret: Boolean = false,
    alignment: TextAlign = TextAlign.End,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    /** Montrer la fin de la valeur au repos (le regard de la référence) plutôt que son début. */
    alignEnd: Boolean = alignment == TextAlign.End || alignment == TextAlign.Right,
    onSend: (() -> Unit)? = null,
    /** Au-delà de 1, la valeur **se replie**: son début reste lisible (voir [OnyxWrappedEditableValue]). */
    wrapLines: Int = 1,
) {
    if (wrapLines > 1) {
        OnyxWrappedEditableValue(
            value = value,
            onValueChange = onValueChange,
            textStyle = textStyle,
            placeholder = placeholder,
            placeholderStyle = placeholderStyle,
            secret = secret,
            keyboardType = keyboardType,
            maxLines = wrapLines,
            modifier = modifier,
        )
        return
    }
    val theme = LocalOnyxTheme.current
    val density = LocalDensity.current
    val focusRequester = remember { FocusRequester() }
    val visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None

    var field by remember {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }
    // La dernière valeur émise **par ce champ** : elle distingue l'écho de sa propre frappe (à
    // ignorer — le rejouer reculait le texte collé) d'un changement venu d'ailleurs (à adopter).
    var emitted by remember { mutableStateOf(value) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var focused by remember { mutableStateOf(false) }
    // L'origine du texte en pixels ; Int.MIN_VALUE tant que la première mise en page n'a pas couru.
    var origin by remember { mutableIntStateOf(Int.MIN_VALUE) }
    val margin = with(density) { 4.dp.roundToPx() }
    val caret = field.selection.end.coerceIn(0, field.text.length)

    // La valeur vient du parent : on la reprend quand elle vient d'ailleurs, jamais l'écho de ce que
    // le champ vient d'émettre (voir OnyxFieldSync) — c'est ce qui a corrigé la troncature au collage.
    LaunchedEffect(value) {
        if (OnyxFieldSync.shouldAdopt(value, emitted, field.text)) {
            field = TextFieldValue(
                text = value,
                selection = TextRange(field.selection.end.coerceIn(0, value.length)),
            )
            emitted = value
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .onyxTappable { focusRequester.requestFocus() },
    ) {
        val viewport = constraints.maxWidth

        // Le curseur suit le texte : au repos on cale la fin, en édition on le garde visible.
        LaunchedEffect(focused, caret, layout, viewport) {
            val result = layout
            val content = result?.size?.width ?: 0
            val rest = OnyxFieldScroll.rest(viewport = viewport, content = content, alignEnd = alignEnd)
            origin = if (!focused || result == null) {
                rest
            } else {
                val cursor = result.getCursorRect(caret.coerceAtMost(result.layoutInput.text.length))
                OnyxFieldScroll.origin(
                    viewport = viewport,
                    content = content,
                    caretLeft = cursor.left.roundToInt(),
                    caretRight = cursor.right.roundToInt(),
                    previous = if (origin == Int.MIN_VALUE) rest else origin,
                    alignEnd = alignEnd,
                    margin = margin,
                )
            }
        }

        if (value.isEmpty() && placeholder.isNotEmpty()) {
            BasicText(
                text = placeholder,
                style = placeholderStyle.copy(textAlign = alignment),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Layout(
            content = {
                BasicTextField(
                    value = field,
                    onValueChange = { next ->
                        // La frappe part **avant** tout effet : elle est notée comme la dernière
                        // valeur émise, pour qu'un retour du parent ne la rejoue pas en arrière.
                        emitted = next.text
                        field = next
                        if (next.text != value) onValueChange(next.text)
                    },
                    textStyle = textStyle,
                    singleLine = true,
                    cursorBrush = SolidColor(theme.accent),
                    visualTransformation = visualTransformation,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (secret) KeyboardType.Password else keyboardType,
                        imeAction = imeAction,
                    ),
                    // `onSend` seulement quand l'appelant en fournit un : sinon Compose applique son
                    // geste par défaut — « Suivant » passe au champ suivant, « Terminé » ferme le
                    // clavier. Une action vide, explicitement posée, annulait ce geste.
                    keyboardActions = KeyboardActions(onSend = onSend?.let { action -> { action() } }),
                    onTextLayout = { layout = it },
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged { focused = it.isFocused },
                )
            },
            modifier = Modifier.clipToBounds(),
        ) { measurables, constraints ->
            // Sans borne horizontale : la valeur reste sur une ligne, plus large que le champ s'il faut.
            val placeable = measurables.first().measure(
                constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity),
            )
            val resolved = if (origin == Int.MIN_VALUE) {
                OnyxFieldScroll.rest(constraints.maxWidth, placeable.width, alignEnd)
            } else {
                origin
            }
            layout(constraints.maxWidth, placeable.height) {
                placeable.placeRelative(resolved, 0)
            }
        }
    }
}

/**
 * **Une valeur éditable repliée sur plusieurs lignes.**
 *
 * Le défaut : un champ d'**une** ligne cale sa valeur à droite au repos et suit le curseur pendant
 * l'édition — donc une adresse longue ne se lit que par son **bout**. Le propriétaire a lu
 * `an.trycloudflare.com` sur une adresse de 49 caractères et a cru à une coupure ; c'était le
 * défilement du curseur. Ici la valeur s'écrit sur **plusieurs lignes** : son **début** reste sous
 * les yeux, sans rien déplacer à la main.
 *
 * La garde d'écho est la **même** que la ligne simple ([OnyxFieldSync.shouldAdopt]) : un retour du
 * parent ne rejoue pas le texte collé en arrière — c'est le correctif de la maison, il ne se
 * réécrit pas deux fois.
 */
@Composable
private fun OnyxWrappedEditableValue(
    value: String,
    onValueChange: (String) -> Unit,
    textStyle: androidx.compose.ui.text.TextStyle,
    placeholder: String,
    placeholderStyle: androidx.compose.ui.text.TextStyle,
    secret: Boolean,
    keyboardType: KeyboardType,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val focusRequester = remember { FocusRequester() }
    var field by remember {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }
    var emitted by remember { mutableStateOf(value) }

    LaunchedEffect(value) {
        if (OnyxFieldSync.shouldAdopt(value, emitted, field.text)) {
            field = TextFieldValue(
                text = value,
                selection = TextRange(field.selection.end.coerceIn(0, value.length)),
            )
            emitted = value
        }
    }

    Box(modifier = modifier.onyxTappable { focusRequester.requestFocus() }) {
        if (value.isEmpty() && placeholder.isNotEmpty()) {
            BasicText(
                text = placeholder,
                style = placeholderStyle,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        BasicTextField(
            value = field,
            onValueChange = { next ->
                // La frappe part avant tout effet, comme sur la ligne simple.
                emitted = next.text
                field = next
                if (next.text != value) onValueChange(next.text)
            },
            textStyle = textStyle,
            singleLine = false,
            maxLines = maxLines,
            cursorBrush = SolidColor(theme.accent),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (secret) KeyboardType.Password else keyboardType,
                imeAction = ImeAction.Default,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )
    }
}
