package com.onyx.app.ui.chat.components

import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onyx.app.data.model.ToolActivity
import com.onyx.app.data.model.ToolStatus
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.DialogWindowBlur
import com.onyx.app.ui.design.FrostSurface
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.LiveMark
import com.onyx.app.ui.design.LocalReduceMotion
import com.onyx.app.ui.design.MotionFamily
import com.onyx.app.ui.design.OnyxContrast
import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.design.OnyxSurfaces
import com.onyx.app.ui.design.SelectableText
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Le **détail d'un appel d'outil** : ce que la rangée repliée ne dit pas.
 *
 * La rangée d'outils du fil dit ce qui a été fait (« Lancé », « Lu »), l'identifiant technique en
 * monospace, la commande et la durée. Elle ne dit pas ce que l'appel a **reçu** ni ce qu'il a
 * **rendu** : c'est cette feuille, et elle s'ouvre sur **l'appel touché** — jamais « le premier » ni
 * « le dernier » de la liste.
 *
 * Ce qu'elle montre, dans l'ordre : le **nom de l'outil** en grand, en monospace (un identifiant
 * technique se rend verbatim, jamais traduit) ; son **état** et sa **durée** en rangées séparées par
 * un filet en retrait ; ses **arguments** et son **résultat** en blocs monospace défilants, chacun
 * avec son bouton **Copier** — le résultat d'un échec s'y lit sous le titre « Erreur », avec le
 * message réel du serveur, jamais le mot « erreur » tout seul.
 *
 * **Ce que nos données ne portent pas se dit.** Un transport qui n'a pas gardé les arguments, ou un
 * appel dont aucun résultat n'a été conservé, l'écrit dans la feuille ([toolCallNotes]) : une rangée
 * vide se lirait comme un appel sans contenu, ce qui serait faux.
 *
 * La forme est celle des feuilles de la maison : poignée, titre à gauche, rangées, filets en
 * retrait, fermeture par appui à côté, glissement vers le bas et retour matériel. Le texte long ne
 * casse pas la mise en page : chaque bloc a sa propre hauteur maximale et **défile** (verticalement
 * et horizontalement, sans retour à la ligne, pour que des colonnes de terminal restent alignées).
 */

// ------------------------------------------------------------------------------------------------
// La cible tactile et les gabarits — fonctions pures, testables
// ------------------------------------------------------------------------------------------------

/**
 * La hauteur minimale d'une rangée d'outil : la cible tactile de la maison.
 *
 * Elle vaut [OnyxSpace.RowHeight] par la règle du pouce, et la rangée la **porte** au lieu de
 * compter sur la seule taille de son texte (à police 1,3×, une rangée de deux mots tombait sous les
 * 48 dp).
 */
val ToolCallRowMinTouch = OnyxSpace.RowHeight

/** L'état d'un appel, en trois tons : ce qui tourne, ce qui a fini, ce qui a échoué. */
enum class ToolCallTone { RUNNING, DONE, FAILED }

/** Une rangée du gabarit : un libellé traduit, une valeur rendue verbatim. */
data class ToolCallRow(val label: String, val value: String, val monospace: Boolean = false)

/** Un bloc de texte technique : son titre traduit, son contenu verbatim, et s'il est un échec. */
data class ToolCallBlock(val title: String, val text: String, val failed: Boolean = false)

/**
 * Le détail complet d'un appel, prêt à dessiner.
 *
 * [name] est l'identifiant technique de l'outil : il se rend **verbatim** (jamais traduit, jamais
 * reformaté). [rows] porte ce qui est un état ou une mesure (l'état, la durée), [blocks] ce qui est
 * du texte technique (les arguments, le résultat), et [notes] ce qui **manque** dans nos données.
 */
data class ToolCallDetail(
    val name: String,
    val tone: ToolCallTone,
    val rows: List<ToolCallRow>,
    val blocks: List<ToolCallBlock>,
    val notes: List<String>,
) {
    val isFailed: Boolean get() = tone == ToolCallTone.FAILED
}

/** Le ton d'un appel, depuis son état de fil. */
fun toolCallTone(tool: ToolActivity): ToolCallTone = when (tool.status) {
    ToolStatus.RUNNING -> ToolCallTone.RUNNING
    ToolStatus.COMPLETED -> ToolCallTone.DONE
    ToolStatus.FAILED -> ToolCallTone.FAILED
}

/** L'état d'un appel en un mot, traduit par le dictionnaire central. */
fun toolCallStatusLabel(tool: ToolActivity): String = when (tool.status) {
    ToolStatus.RUNNING -> OnyxLocalization["Running"]
    ToolStatus.COMPLETED -> OnyxLocalization["Finished"]
    ToolStatus.FAILED -> OnyxLocalization["Failed"]
}

/** La durée d'un appel, quand elle est connue : « 1 s ». `null` tant qu'il tourne, ou sans horodate. */
fun toolCallDurationLabel(tool: ToolActivity): String? = ranToolsDuration(toolElapsedSeconds(tool))

/**
 * Ce que nos données ne portent pas pour cet appel — dit en toutes lettres, et **rien d'autre**.
 *
 * Un `args` absent ne veut pas dire « cet appel n'avait pas d'arguments » : cela veut dire que le
 * transport courant ne les a pas conservés. Un résultat absent sur un appel fini veut dire qu'aucun
 * n'a été gardé, pas que l'outil n'a rien rendu. Une rangée vide se lirait comme un appel sans
 * contenu : ces phrases l'empêchent.
 */
fun toolCallNotes(tool: ToolActivity): List<String> = buildList {
    if (tool.args == null) add(OnyxLocalization["The arguments of this call were not kept by this transport."])
    if (tool.output == null) {
        add(
            if (tool.isRunning) {
                OnyxLocalization["Still running."]
            } else {
                OnyxLocalization["No output was kept for this step."]
            },
        )
    }
}

/**
 * Le détail d'un appel, tel que la feuille le dessine.
 *
 * Les blocs vides n'existent pas : un champ absent n'est **pas** un bloc vide, c'est une note.
 */
fun toolCallDetail(tool: ToolActivity): ToolCallDetail {
    val rows = buildList {
        add(ToolCallRow(label = OnyxLocalization["State"], value = toolCallStatusLabel(tool)))
        toolCallDurationLabel(tool)?.let { add(ToolCallRow(label = OnyxLocalization["Duration"], value = it, monospace = true)) }
    }
    val blocks = buildList {
        tool.args?.takeIf { it.isNotBlank() }?.let {
            add(ToolCallBlock(title = OnyxLocalization["Arguments"], text = it))
        }
        tool.output?.takeIf { it.isNotBlank() }?.let {
            add(
                ToolCallBlock(
                    title = OnyxLocalization[if (tool.status == ToolStatus.FAILED) "Error" else "Result"],
                    text = it,
                    failed = tool.status == ToolStatus.FAILED,
                ),
            )
        }
    }
    return ToolCallDetail(
        name = tool.name.ifBlank { OnyxLocalization["a tool"] },
        tone = toolCallTone(tool),
        rows = rows,
        blocks = blocks,
        notes = toolCallNotes(tool),
    )
}

/**
 * L'appel que la feuille montre : **celui de cet identifiant**, jamais un autre.
 *
 * C'est le contrat de la rangée cliquable : deux appels du même outil (`terminal` deux fois) se
 * distinguent par leur identifiant, pas par leur nom ni par leur position dans la liste. Un
 * identifiant inconnu (la liste a changé sous la feuille) ne rend **rien** : une feuille sur un
 * autre appel serait pire que pas de feuille.
 */
fun toolCallSelection(tools: List<ToolActivity>, id: String?): ToolActivity? =
    tools.firstOrNull { id != null && it.id == id }

/**
 * Ce que la rangée d'outil écrit : le mot du travail (ou l'identifiant, tant que l'appel tourne), et
 * l'identifiant technique à côté quand le mot ne le porte pas déjà.
 *
 * Un identifiant technique est en monospace ([monospace]) : c'est un nom de code, pas une phrase.
 * Un outil **inconnu** du tableau des verbes garde lui aussi son identifiant à côté — écrit
 * **verbatim** (`vision_analyze`, souligné compris), et non plus replié dans le mot du passé
 * (« Utilisé vision analyze ») : c'est l'identifiant que le propriétaire cherche dans sa liste, et
 * le mot du dictionnaire ne le remplace pas.
 */
data class ToolRowLabel(val text: String, val monospace: Boolean, val id: String?)

/** Voir [ToolRowLabel]. */
fun toolRowLabel(tool: ToolActivity): ToolRowLabel = when {
    tool.isRunning -> ToolRowLabel(
        text = tool.name.ifBlank { OnyxLocalization["a tool"] },
        monospace = true,
        id = null,
    )

    else -> ToolRowLabel(
        text = knownToolVerb(tool.name) ?: OnyxLocalization["Used (tool)"],
        monospace = false,
        id = tool.name.takeIf { it.isNotBlank() },
    )
}

/** L'encre d'un état sur la surface de la feuille : la teinte de l'état, écartée du fond juste assez pour se lire. */
internal fun toolCallStateInk(theme: ResolvedTheme, surface: Color, tone: ToolCallTone): Color {
    val hue = when (tone) {
        ToolCallTone.RUNNING -> theme.accent
        ToolCallTone.DONE -> theme.success
        ToolCallTone.FAILED -> theme.error
    }
    val solid = OnyxContrast.over(hue, surface)
    if (OnyxContrast.ratio(solid, surface) >= OnyxContrast.TEXT_MIN) return solid
    return OnyxContrast.distinctOn(solid, surface, OnyxContrast.TEXT_MIN)
}

// ------------------------------------------------------------------------------------------------
// La feuille
// ------------------------------------------------------------------------------------------------

/** La hauteur maximale d'un bloc technique avant qu'il ne défile : au-delà, il noierait la feuille. */
private val ToolBlockMaxHeight = 220.dp

/** La hauteur maximale du corps de la feuille : elle défile au-delà, elle ne pousse jamais le bord. */
private val ToolSheetBodyMaxHeight = 560.dp

/** Le glissement au-delà duquel la feuille se referme. */
private val ToolSheetDismissDistance = 96.dp

private val ToolSheetHandleWidth = 40.dp
private val ToolSheetHandleHeight = 5.dp

/** Le voile : assez sombre pour détacher la feuille, jamais au point de tout cacher. */
private val ToolSheetScrim = Color.Black.copy(alpha = 0.42f)

/**
 * La feuille du détail d'un appel : poignée, titre en monospace, rangées, blocs monospace et notes.
 *
 * Elle se ferme de trois façons, comme les autres feuilles de la maison : **appui à côté** (le
 * voile), **glissement vers le bas** au-delà de [ToolSheetDismissDistance], et **retour matériel**
 * (la `Dialog` de Compose reçoit le geste du système et appelle [onDismiss]).
 */
@Composable
fun ToolCallDetailSheet(tool: ToolActivity, onDismiss: () -> Unit) {
    val theme = LocalOnyxTheme.current
    val detail = toolCallDetail(tool)
    val surface = OnyxSurfaces.surfaceHigh(theme)
    var notice by remember(tool.id) { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    // La copie est réelle (presse-papiers du système) et sa confirmation est **dans** la feuille :
    // le voile d'un dialogue couvrirait un message posé sous lui, et l'appui paraîtrait muet.
    fun copy(text: String, what: String) {
        scope.launch {
            runCatching {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(what, text)))
            }.onFailure { error ->
                notice = OnyxLocalization.format("Couldn't copy it: %1\$s", error.message ?: error.toString())
                return@launch
            }
            notice = OnyxLocalization.format("Copied (%1\$d characters)", text.length)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        DialogWindowBlur(FrostSurface.SHEET)
        ToolSheetOverlay(
            title = detail.name,
            tone = detail.tone,
            onDismiss = onDismiss,
            surface = surface,
        ) {
            notice?.let { line ->
                BasicText(
                    text = line,
                    localize = false,
                    style = theme.footnote.copy(color = SheetInk.muted(theme, surface)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = OnyxSpace.CardPadding, vertical = OnyxSpace.Hair),
                )
            }
            Spacer(Modifier.height(OnyxSpace.Hair))
            OnyxRowDivider(inset = OnyxSpace.separatorInset())
            detail.rows.forEachIndexed { index, row ->
                if (index > 0) OnyxRowDivider(inset = OnyxSpace.separatorInset())
                ToolCallRowLine(row = row, ink = theme.textColor, muted = SheetInk.muted(theme, surface), tone = detail.tone, surface = surface)
            }
            detail.blocks.forEach { block ->
                ToolCallBlockView(
                    block = block,
                    onCopy = { copy(it, block.title) },
                    errorInk = SheetInk.label(theme, surface, dangerous = true),
                )
            }
            detail.notes.forEach { note ->
                BasicText(
                    text = note,
                    style = theme.caption.copy(color = SheetInk.muted(theme, surface)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = OnyxSpace.CardPadding, vertical = OnyxSpace.Hair),
                )
            }
            Spacer(Modifier.height(OnyxSpace.BlockGap))
        }
    }
}

/** Une rangée du détail : son libellé à gauche, sa valeur à droite — la valeur d'un état en porte l'encre. */
@Composable
private fun ToolCallRowLine(row: ToolCallRow, ink: Color, muted: Color, tone: ToolCallTone, surface: Color) {
    val theme = LocalOnyxTheme.current
    // L'état prend l'encre de son ton (mesurée sur la surface) ; la durée, elle, est une mesure :
    // monospace, encre secondaire, comme sur la rangée du fil.
    val isState = row.label == OnyxLocalization["State"]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OnyxSpace.CardPadding, vertical = OnyxSpace.Half),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = row.label,
            style = theme.body.copy(color = ink),
            // Le libellé d'une ligne d'argument se **replie** (la politique de texte de la maison) :
            // une étiquette coupée sur une ligne est ce que le verrou des troncatures refuse.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.size(OnyxSpace.BlockGap))
        SelectableText(
            text = row.value,
            localize = false,
            style = theme.body.copy(
                color = if (isState) toolCallStateInk(theme, surface, tone) else muted,
                fontFamily = if (row.monospace) FontFamily.Monospace else null,
            ),
            maxLines = 1,
        )
    }
}

/**
 * Un bloc technique : son titre, son bouton **Copier**, et son texte en monospace qui **défile**.
 *
 * Le défilement est double — vertical **et** horizontal, sans retour à la ligne : une sortie de
 * terminal garde ses colonnes alignées, et une seule ligne de 12 000 caractères ne pousse pas la
 * feuille hors de l'écran.
 */
@Composable
private fun ToolCallBlockView(block: ToolCallBlock, onCopy: (String) -> Unit, errorInk: Color) {
    val theme = LocalOnyxTheme.current
    val ink = if (block.failed) errorInk else theme.textColor
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = OnyxSpace.CardPadding, vertical = OnyxSpace.Snug),
        verticalArrangement = Arrangement.spacedBy(OnyxSpace.Hair),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                text = block.title,
                style = theme.footnote.copy(color = if (block.failed) errorInk else SheetInk.muted(theme, OnyxSurfaces.surfaceHigh(theme)), fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
            )
            ToolCopyButton(label = block.title, onClick = { onCopy(block.text) })
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = ToolBlockMaxHeight)
                .background(OnyxSurfaces.well(theme), RoundedCornerShape(OnyxSpace.ControlCorner))
                .border(OnyxSpace.Stroke, OnyxSurfaces.hairline(theme), RoundedCornerShape(OnyxSpace.ControlCorner))
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = OnyxSpace.Snug, vertical = OnyxSpace.Half),
        ) {
            SelectableText(
                text = block.text,
                localize = false,
                style = theme.caption.copy(color = ink, fontFamily = FontFamily.Monospace),
                softWrap = false,
            )
        }
    }
}

/** Le bouton d'un bloc : il copie son texte, et son libellé dit lequel — cinq « Copier » ne se distinguent pas. */
@Composable
private fun ToolCopyButton(label: String, onClick: () -> Unit) {
    val theme = LocalOnyxTheme.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_500)
            copied = false
        }
    }
    Box(
        modifier = Modifier
            .size(ToolCallRowMinTouch)
            .semantics { contentDescription = OnyxLocalization.format("Copy %1\$s", label) }
            .onyxTappable {
                copied = true
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        OnyxIcon(
            symbol = if (copied) Symbols.Checkmark else Symbols.DocOnDoc,
            tint = if (copied) theme.success else theme.tertiaryText,
            size = 15.dp,
        )
    }
}

/** La marque d'état de la feuille : un anneau qui tourne, une coche, ou une croix — la même famille que la rangée du fil. */
@Composable
private fun ToolStateMark(tone: ToolCallTone) {
    val theme = LocalOnyxTheme.current
    val tint = when (tone) {
        ToolCallTone.RUNNING -> theme.accent
        ToolCallTone.DONE -> theme.success
        ToolCallTone.FAILED -> theme.error
    }
    Box(
        modifier = Modifier
            .size(20.dp)
            .background(tint.copy(alpha = 0.16f), CircleShape)
            .semantics { contentDescription = OnyxLocalization[when (tone) { ToolCallTone.RUNNING -> "Running"; ToolCallTone.DONE -> "Finished"; ToolCallTone.FAILED -> "Failed" }] },
        contentAlignment = Alignment.Center,
    ) {
        when (tone) {
            ToolCallTone.DONE -> OnyxIcon(Symbols.Checkmark, tint = tint, size = 11.dp)
            ToolCallTone.FAILED -> OnyxIcon(Symbols.Xmark, tint = tint, size = 11.dp)
            ToolCallTone.RUNNING -> LiveMark(color = tint, size = 11.dp)
        }
    }
}

/**
 * La surface de la feuille : poignée, titre à gauche, puis le corps qui défile.
 *
 * Le titre est le **nom de l'outil**, en monospace et en grand : c'est un identifiant technique, il
 * se rend verbatim (`localize = false`) — un nom d'outil traduit serait un nom qui n'existe pas.
 */
@Composable
private fun ToolSheetOverlay(
    title: String,
    tone: ToolCallTone,
    onDismiss: () -> Unit,
    surface: Color,
    content: @Composable () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val reduceMotion = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    var drag by remember { mutableFloatStateOf(0f) }
    val threshold = with(LocalDensity.current) { ToolSheetDismissDistance.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ToolSheetScrim)
            // Un appui hors de la feuille la referme : le voile prend le geste, la feuille non.
            .onyxTappable(onClick = onDismiss),
    ) {
        AnimatedVisibility(
            visible = shown || reduceMotion,
            enter = if (reduceMotion) {
                EnterTransition.None
            } else {
                slideInVertically(animationSpec = MotionFamily.Open.spec(reduceMotion), initialOffsetY = { it }) +
                    fadeIn(animationSpec = MotionFamily.Open.spec(reduceMotion))
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, drag.roundToInt()) }
                    .clip(RoundedCornerShape(topStart = OnyxSpace.PanelCorner, topEnd = OnyxSpace.PanelCorner))
                    .background(surface)
                    .border(
                        width = OnyxSpace.Stroke,
                        color = OnyxSurfaces.hairline(theme),
                        shape = RoundedCornerShape(topStart = OnyxSpace.PanelCorner, topEnd = OnyxSpace.PanelCorner),
                    )
                    // La feuille n'est pas une zone de fermeture : appuyer entre deux rangées ne fait rien.
                    .onyxTappable(onClick = {})
                    .navigationBarsPadding()
                    .pointerInput(threshold, onDismiss) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, amount -> drag = (drag + amount).coerceAtLeast(0f) },
                            onDragEnd = { if (drag > threshold) onDismiss() else drag = 0f },
                            onDragCancel = { drag = 0f },
                        )
                    },
            ) {
                // La poignée : elle dit qu'on peut tirer la feuille vers le bas.
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = OnyxSpace.Half, bottom = OnyxSpace.Hair),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = ToolSheetHandleWidth, height = ToolSheetHandleHeight)
                            .background(theme.tertiaryText.copy(alpha = 0.55f), RoundedCornerShape(50)),
                    )
                }
                // Le titre : le **nom de l'outil**, en monospace et en grand, précédé de la marque
                // de son état (une coche, une croix, l'anneau qui tourne). L'identifiant se rend
                // verbatim — un nom d'outil traduit serait un nom qui n'existe pas.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = OnyxSpace.CardPadding, end = OnyxSpace.CardPadding, top = OnyxSpace.Hair, bottom = OnyxSpace.Snug),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
                ) {
                    ToolStateMark(tone)
                    BasicText(
                        text = title,
                        localize = false,
                        style = theme.headline.copy(color = theme.textColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = ToolSheetBodyMaxHeight)
                        .verticalScroll(rememberScrollState()),
                ) {
                    content()
                }
            }
        }
    }
}

