package com.onyx.app.ui.design

import android.content.ClipData
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
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
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlin.math.roundToInt

/**
 * Les pièces d'un formulaire: une liste groupée sur une surface arrondie, avec ses rangées
 * et ses filets — le `Form` de , sans rien de Material.
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
    Column(modifier = modifier.fillMaxWidth().padding(top = 22.dp)) {
        header?.let {
            BasicText(
                text = if (localizeHeader) OnyxLocalization[it].uppercase() else it.uppercase(),
                localize = false,
                style = theme.footnote.copy(color = theme.secondaryText),
                modifier = Modifier.padding(start = 20.dp, bottom = 7.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(theme.formSurfaceColor, RoundedCornerShape(12.dp)),
        ) {
            content()
        }
        footer?.let {
            BasicText(
                text = it,
                localize = localizeFooter,
                style = theme.footnote.copy(color = theme.secondaryText),
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 7.dp),
            )
        }
    }
}

/** Le filet entre deux rangées, rentré sous le libellé. */
@Composable
fun OnyxRowDivider(inset: androidx.compose.ui.unit.Dp = 16.dp) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = inset)
            .height(0.5.dp)
            .background(theme.separator),
    )
}

/** Une rangée de formulaire: libellé à gauche, contenu à droite. */
@Composable
fun OnyxRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.onyxTappable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/**
 * Un champ: le libellé à gauche, la valeur éditable alignée à droite.
 *
 * Au repos la valeur se cale à droite, son début coupé comme dans le modèle; pendant l'édition
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
) {
    val theme = LocalOnyxTheme.current
    val clipboard = LocalClipboard.current

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
        if (secret) {
            // Un champ secret ne montre jamais sa fin: on l'offre par la copie, jamais par l'œil.
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

/** Une rangée qui ouvre autre chose: valeur puis chevron. */
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
    OnyxRow(modifier = modifier, onClick = onClick) {
        symbol?.let { OnyxIcon(symbol = it, tint = tint ?: theme.accent, size = 18.dp) }
        BasicText(text = label, style = theme.body.copy(color = theme.primaryText), maxLines = 1, modifier = Modifier.weight(1f))
        BasicText(
            text = value,
            localize = localizeValue,
            style = theme.body.copy(color = theme.secondaryText),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        OnyxIcon(Symbols.ChevronRight, tint = theme.tertiaryText, size = 12.dp)
    }
}

/**
 * Le résumé d'un `DisclosureGroup`: le titre, la valeur à droite, et le chevron qui pivote.
 *
 * pose ce chevron tout seul, à la fin de la rangée; ici il se tourne d'un quart de tour
 * quand le groupe s'ouvre, comme dans les Réglages.
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

/** Une rangée avec un interrupteur, comme `Toggle` dans un `Form`. */
@Composable
fun OnyxToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val theme = LocalOnyxTheme.current
    OnyxRow(modifier = modifier, onClick = if (enabled) ({ onCheckedChange(!checked) }) else null) {
        BasicText(
            text = label,
            style = theme.body.copy(color = if (enabled) theme.primaryText else theme.secondaryText),
            modifier = Modifier.weight(1f),
        )
        OnyxSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** L'interrupteur: une piste arrondie, le pouce à droite quand il est allumé. */
@Composable
fun OnyxSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = modifier
            .size(width = 51.dp, height = 31.dp)
            .background(if (checked) theme.success else theme.fill, RoundedCornerShape(50))
            .onyxTappable(enabled = enabled) { onCheckedChange(!checked) },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .size(27.dp)
                .background(Color.White, RoundedCornerShape(50)),
        )
    }
}

/** Le contrôle segmenté (`AdaptiveSegmented`). */
@Composable
fun OnyxSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    // Le contrôle segmenté: une capsule sombre, et le choix retenu dans une pastille plus
    // claire, aux extrémités rondes.
    val selectedPill = if (theme.dark) {
        Color.White.copy(alpha = 0.22f)
    } else {
        Color.White.copy(alpha = 0.95f)
    }

    if (options.isEmpty()) return
    val safeSelection = selectedIndex.coerceIn(0, options.lastIndex)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(theme.fill, RoundedCornerShape(50))
            .padding(3.dp),
    ) {
        val segmentWidth = maxWidth / options.size
        val pillOffset by animateDpAsState(
            targetValue = segmentWidth * safeSelection,
            animationSpec = OnyxMotion.Sites.TabSelectionDp,
            label = "segmentedSelection",
        )
        Box(
            modifier = Modifier
                .offset(x = pillOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .background(selectedPill, RoundedCornerShape(50)),
        )
        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { index, label ->
                val active = index == safeSelection
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onyxTappable { onSelect(index) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = label,
                        style = androidx.compose.ui.text.TextStyle(
                            color = if (active) theme.primaryText else theme.secondaryText,
                            fontSize = 16.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Le titre d'une page de Réglages: « ← titre » et l'action à droite. */
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
     * quand la barre n'a pas de retour à porter: replié, le détail garde le contrôle dans sa
     * barre. Il tient la place du retour (50 dp) pour que le titre reste centré.
     */
    leading: (@Composable () -> Unit)? = null,
    /**
     * Affiche le chevron du bouton de gauche. Faux pour un mot seul comme « Skip » de l'écran de
     * configuration (il n'y a pas de retour en arrière, seulement une sortie).
     */
    showBackChevron: Boolean = true,
    /**
     * Un symbole à droite, avant l'action: l'état de la liaison d'un écran (`KanbanView` y met
     * la diffusion du tableau). `trailingLabel` est son libellé d'accessibilité, localisé.
     */
    trailingSymbol: Int? = null,
    trailingTint: Color? = null,
    trailingLabel: String? = null,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            // Le retour garde la priorité: un écran qui a un retour à porter ne le perd jamais
            // au profit du contrôle du volet (celui-ci ne se dessine que sans retour).
            onBack != null -> Row(
                modifier = Modifier.onyxClickable(onClick = onBack),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
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
        if (action != null && onAction != null) {
            BasicText(
                text = action,
                style = theme.body.copy(color = theme.accent, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.onyxClickable(enabled = actionEnabled, onClick = onAction),
            )
        } else {
            Box(Modifier.size(50.dp))
        }
    }
}

/**
 * `ContentUnavailableView`: un symbole gris, un titre, une phrase — au centre, pour un état vide.
 *
 * Les écrans qui portent un `ContentUnavailableView` (Kanban, Cron, les horaires d'un bot)
 * s'en servent avec le symbole: c'est le même encombrement partout (38 pt de glyphe,
 * `title3` semi-gras, `footnote` secondaire), quel que soit l'écran qui le pose.
 */
@Composable
fun OnyxEmptyState(
    @androidx.annotation.DrawableRes symbol: Int,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    /** La phrase de l'état vide est un libellé de Onyx: elle suit la langue, comme le titre. */
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
 * Le champ de texte , sans décoration: le fond et le cadre viennent du conteneur.
 * Compose n'expose que `BasicTextField`, qui ne ressemble à rien — c'est celui-ci qu'on habille.
 *
 * Sur une seule ligne, la valeur suit [OnyxEditableValue]: on lit toute la valeur en déplaçant le
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
    /** Mot de passe ou secret: le texte reste masqué, sans changer les autres champs. */
    secret: Boolean = false,
) {
    val theme = LocalOnyxTheme.current
    val textStyle = (style ?: theme.body).copy(color = theme.primaryText)
    val placeholderStyle = textStyle.copy(color = theme.tertiaryText)

    if (singleLine) {
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
            keyboardActions = KeyboardActions(onSend = { onSend?.invoke() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Une valeur éditable sur une seule rangée.
 *
 * Au repos (champ non focalisé) elle garde le regard du modèle: alignée à droite, sa fin
 * visible, son début coupé à gauche. Dès qu'on l'édite, le texte défile horizontalement pour que le
 * curseur reste dans la fenêtre visible, quelle que soit la longueur de la valeur — un collage au
 * milieu, une sélection, une police agrandie ne cachent plus la lettre sous le curseur.
 *
 * Le champ est mesuré sans borne horizontale: la valeur tient sur une ligne, et c'est la position
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
    /** Montrer la fin de la valeur au repos (le regard du modèle) plutôt que son début. */
    alignEnd: Boolean = alignment == TextAlign.End || alignment == TextAlign.Right,
    onSend: (() -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current
    val density = LocalDensity.current
    val focusRequester = remember { FocusRequester() }
    val visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None

    var field by remember {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var focused by remember { mutableStateOf(false) }
    // L'origine du texte en pixels; Int.MIN_VALUE tant que la première mise en page n'a pas couru.
    var origin by remember { mutableIntStateOf(Int.MIN_VALUE) }
    val margin = with(density) { 4.dp.roundToPx() }
    val caret = field.selection.end.coerceIn(0, field.text.length)

    // La valeur vient du parent: on la reprend sans perdre le curseur.
    LaunchedEffect(value) {
        if (field.text != value) {
            field = TextFieldValue(
                text = value,
                selection = TextRange(field.selection.end.coerceIn(0, value.length)),
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .onyxTappable { focusRequester.requestFocus() },
    ) {
        val viewport = constraints.maxWidth

        // Le curseur suit le texte: au repos on cale la fin, en édition on le garde visible.
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
                    keyboardActions = KeyboardActions(onSend = { onSend?.invoke() }),
                    onTextLayout = { layout = it },
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged { focused = it.isFocused },
                )
            },
            modifier = Modifier.clipToBounds(),
        ) { measurables, constraints ->
            // Sans borne horizontale: la valeur reste sur une ligne, plus large que le champ s'il faut.
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
