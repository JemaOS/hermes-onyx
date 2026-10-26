package com.onyx.app.ui.chat.components

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.chat.PendingAttachment
import com.onyx.app.ui.design.ComposerCircleButton
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.OnyxMotion
import com.onyx.app.ui.design.glassSurface
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.theme.LocalOnyxTheme
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Nombre de pièces jointes au plus dans un message. */
private const val MaxAttachmentCount = 10

/** Poids total au plus, toutes pièces jointes ensemble. */
private const val MaxAttachmentBytes = 25L * 1024L * 1024L

/** Ce qui se dit quand on dépasse le nombre. */
private const val TooManyAttachmentsMessage = "You can attach up to 10 files."

/** Ce qui se dit quand on dépasse le poids. */
private const val AttachmentsTooLargeMessage = "That's more than 25 MB."

/** Le côté d'une vignette d'image dans la bande, et le repère de décodage qui va avec. */
private val StripThumbnailSize = 64.dp
private const val StripThumbnailPixels = 256

/** `ComposerView` mesure la ligne en points, puis ajoute les deux paddings de 5. */
internal fun measuredComposerButtonSize(rowHeightPx: Int, density: Float): androidx.compose.ui.unit.Dp =
    (rowHeightPx / density).dp + 10.dp

internal val ComposerPlusGlyphSize = 20.dp
internal val ComposerDictateGlyphSize = 17.dp
internal val ComposerExpandGlyphSize = 13.dp
internal val ComposerControlSlotHeight = 38.dp

/**
 * Le composer d'un chat, porté de `ComposerView.composerRow`.
 *
 * Un seul champ arrondi (bouton « + », le texte, l'agrandissement, le micro) et, à côté, un
 * bouton rond dont le rôle suit ce que vous faites: mains-libres, envoi, ou arrêt.
 *
 * Au-dessus, la bande des pièces jointes: une vignette pour chaque image, une pastille pour
 * chaque fichier (son type et son poids en sous-titre), et la croix de retrait sur le coin.
 */
@Composable
fun OnyxComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    isStreaming: Boolean,
    title: String,
    modifier: Modifier = Modifier,
    /** Donné par un écran qui doit rendre le focus au champ, sans imposer ce besoin aux autres. */
    focusRequester: FocusRequester? = null,
    canSteer: Boolean = false,
    returnSends: Boolean = true,
    queued: Int = 0,
    /** Erreur propre à l'écran (dictée, upload), montrée après la bande de pièces jointes. */
    problem: String? = null,
    localizeProblem: Boolean = false,
    attachments: List<com.onyx.app.ui.chat.PendingAttachment> = emptyList(),
    /** Les images collées au clavier (Ctrl+V) arrivent ici, sans les aperçus de liens. */
    onPasteImages: ((List<android.net.Uri>) -> Unit)? = null,
    onRemoveAttachment: (String) -> Unit = {},
    /** Une pièce jointe ouverte au tap depuis la bande (le PDF dans le lecteur de l'app). */
    onOpenAttachment: (com.onyx.app.ui.chat.PendingAttachment) -> Unit = {},
    onSend: () -> Unit,
    onStop: () -> Unit,
    onTalk: () -> Unit,
    /** Action spécifique au steer; à défaut, le même envoi que la file est conservé. */
    onSteer: (() -> Unit)? = null,
    /** Le groupe envoie avec arrow.up même pendant un tour; le chat offre steer/file. */
    queueWhileStreaming: Boolean = true,
    /** Le micro dicte dans le champ; à défaut, il conserve l'ancienne action vocale. */
    dictating: Boolean = false,
    onDictate: (() -> Unit)? = null,
    onAddAttachment: () -> Unit = {},
    onExpand: () -> Unit = {},
    allowExpand: Boolean = true,
    maxLines: Int = 5,
    trailingButtonSize: androidx.compose.ui.unit.Dp? = null,
    verticalInsets: androidx.compose.ui.unit.Dp = 6.dp,
    auxiliaryInsets: androidx.compose.ui.unit.Dp = 18.dp,
    fieldContentPadding: androidx.compose.ui.unit.Dp = 5.dp,
    /** Les pièces jointes déposées sur le composeur; `null` laisse le fil les recevoir. */
    onDropAttachments: ((List<android.net.Uri>) -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val hasDraft = draft.isNotBlank() || attachments.isNotEmpty()
    val draftIsLong = draft.length > 160 || draft.count { it == '\n' } >= 3
    var buttonSize by remember { mutableStateOf(trailingButtonSize ?: 48.dp) }
    var focused by remember { mutableStateOf(false) }

    // Le poids de chaque pièce jointe, résolu une fois auprès du fournisseur de contenu.
    val sizes = remember { mutableStateMapOf<String, Long>() }
    LaunchedEffect(attachments) {
        attachments.forEach { attachment ->
            if (sizes[attachment.id] == null) {
                val bytes = withContext(Dispatchers.IO) { resolveAttachmentSize(context, attachment.uri) }
                if (bytes != null) sizes[attachment.id] = bytes
            }
        }
    }
    val knownBytes = attachments.sumOf { sizes[it.id] ?: 0L }

    // Ce qu'un dépôt a fait refuser; le message s'efface dès que la liste change.
    var refused by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(attachments.size) { refused = null }
    val attachmentError = refused ?: when {
        attachments.size > MaxAttachmentCount -> TooManyAttachmentsMessage
        knownBytes > MaxAttachmentBytes -> AttachmentsTooLargeMessage
        else -> null
    }

    // Le dépôt du composeur: il ajoute l'image ou le texte, après avoir vérifié les limites.
    val dropTarget = rememberComposerDropTarget(
        attachmentCount = attachments.size,
        knownBytes = knownBytes,
        onAccept = onDropAttachments ?: {},
        onRefuse = { message -> refused = message },
    )
    // Sans dépôt branché, le composeur ne capte rien: c'est le fil qui reçoit, comme avant.
    val capturesDrop = onDropAttachments != null

    val placeholder = when {
        dictating -> "Listening…"
        isStreaming && canSteer -> "Steer the reply…"
        title == "the group" -> "Message the group"
        else -> "Ask $title"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // `ComposerView`: `.animation(.snappy(duration: 0.3), value: pendingAttachments.count)`
            // — la bande de pièces jointes ouvre et referme la hauteur du
            // champ en ressort, au lieu de la sauter d'un coup. Même chose pour le brouillon en
            // cours d'édition (`:138`) et les suggestions (`:139`).
            .animateContentSize(animationSpec = OnyxMotion.Sites.ComposerRowSize)
            .then(
                if (capturesDrop) {
                    Modifier.dragAndDropTarget(
                        shouldStartDragAndDrop = { containsAttachableDrop(it) },
                        target = dropTarget,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        if (attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = auxiliaryInsets, end = auxiliaryInsets, top = 6.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                attachments.forEach { attachment ->
                    AttachmentStripTile(
                        attachment = attachment,
                        bytes = sizes[attachment.id],
                        onRemove = { onRemoveAttachment(attachment.id) },
                        onOpen = { onOpenAttachment(attachment) },
                    )
                }
            }
        }
        val shownProblem = problem ?: attachmentError
        if (shownProblem != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(start = auxiliaryInsets, bottom = 2.dp),
            ) {
                OnyxIcon(
                    Symbols.ExclamationTriangle,
                    tint = if (problem != null) theme.warning else theme.error,
                    size = 13.dp,
                )
                BasicText(
                    text = shownProblem,
                    localize = if (problem != null) localizeProblem else true,
                    style = theme.caption.copy(color = if (problem != null) theme.warning else theme.error),
                )
            }
        }
        if (queued > 0) {
            BasicText(
                text = if (queued == 1) "1 message waiting" else "$queued messages waiting",
                style = theme.caption.copy(color = theme.secondaryText),
                modifier = Modifier.padding(start = 22.dp, bottom = 2.dp),
            )
        }
        Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.pageColor.copy(alpha = 0.94f))
            .imePadding()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = verticalInsets, bottom = verticalInsets),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(buttonSize / 2))
                .then(
                    if (theme.usesGlass) {
                        Modifier.glassSurface(RoundedCornerShape(buttonSize / 2))
                    } else {
                        Modifier.background(theme.surfaceColor)
                    },
                )
                .border(
                    width = 1.5.dp,
                    color = if (focused && hasDraft) theme.accent.copy(alpha = 0.45f) else Color.Transparent,
                    shape = RoundedCornerShape(buttonSize / 2),
                )
                .padding(fieldContentPadding),
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.onSizeChanged { size ->
                    val measured = measuredComposerButtonSize(size.height, density.density)
                    if (trailingButtonSize == null && !draft.contains("\n") && measured < 72.dp) buttonSize = measured
                },
            ) {
                IconSlot(
                    symbol = Symbols.Plus,
                    label = "Add",
                    size = 38.dp,
                    glyphSize = ComposerPlusGlyphSize,
                    onClick = onAddAttachment,
                )

                Box(modifier = Modifier.weight(1f).defaultMinSize(minHeight = 38.dp)) {
                    if (draft.isEmpty()) {
                        BasicText(
                            text = placeholder,
                            style = theme.body.copy(color = theme.tertiaryText),
                            // Une seule ligne: le panneau de détail du pliable déplié n'est pas
                            // si large, et « Demander à Onyx » passait sur deux lignes dans le
                            // champ. Le texte s'élide, le champ garde sa hauteur.
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 9.dp),
                        )
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        textStyle = theme.body.copy(color = theme.textColor),
                        cursorBrush = SolidColor(theme.accent),
                        maxLines = maxLines,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = if (returnSends) ImeAction.Send else ImeAction.Default,
                        ),
                        keyboardActions = KeyboardActions(onSend = { if (hasDraft) onSend() }),
                        modifier = Modifier
                            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                            .onFocusChanged { focused = it.isFocused }
                            .fillMaxWidth()
                            .padding(vertical = 9.dp)
                            // Ctrl+V / Cmd+V sur une image: le champ de texte ne prend que du
                            // texte, on intercepte donc avant lui.
                            .onPreviewKeyEvent { event ->
                                val chord = (event.isCtrlPressed || event.isMetaPressed) &&
                                    event.key == androidx.compose.ui.input.key.Key.V
                                if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && chord) {
                                    val uris = clipboardImages(context)
                                    if (uris.isNotEmpty() && onPasteImages != null) {
                                        onPasteImages.invoke(uris)
                                        true
                                    } else {
                                        false
                                    }
                                } else {
                                    false
                                }
                            },
                    )
                }

                if (draftIsLong && allowExpand) {
                    IconSlot(
                        symbol = Symbols.Expand,
                        label = "Open the editor",
                        size = 30.dp,
                        height = ComposerControlSlotHeight,
                        glyphSize = ComposerExpandGlyphSize,
                        onClick = onExpand,
                    )
                }

                IconSlot(
                    symbol = if (dictating) Symbols.Waveform else Symbols.MicOutline,
                    label = if (dictating) "Stop dictating" else "Dictate",
                    size = 34.dp,
                    height = ComposerControlSlotHeight,
                    glyphSize = ComposerDictateGlyphSize,
                    tint = if (dictating) theme.error else theme.secondaryText,
                    onClick = onDictate ?: onTalk,
                )
            }
        }

        val trailingStates = when {
            isStreaming && hasDraft -> when {
                !queueWhileStreaming -> listOf("send")
                canSteer && draft.isNotBlank() -> listOf("steer", "after")
                else -> listOf("after")
            }
            isStreaming -> listOf("stop")
            hasDraft -> listOf("send")
            else -> listOf("talk")
        }
        AnimatedContent(
            targetState = trailingStates,
            transitionSpec = {
                (
                    scaleIn(initialScale = 0.4f, animationSpec = OnyxMotion.Sites.ComposerTrailing) +
                        fadeIn(animationSpec = OnyxMotion.Sites.ComposerTrailing)
                    ).togetherWith(
                    scaleOut(targetScale = 0.4f, animationSpec = OnyxMotion.Sites.ComposerTrailing) +
                        fadeOut(animationSpec = OnyxMotion.Sites.ComposerTrailing),
                )
            },
            label = "composerTrailingButton",
        ) { currentStates ->
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                currentStates.forEach { trailingState ->
                    TrailingComposerButton(
                        state = trailingState,
                        size = buttonSize,
                        onSend = onSend,
                        onSteer = onSteer ?: onSend,
                        onStop = onStop,
                        onTalk = onTalk,
                    )
                }
            }
        }
        }
    }
}

/**
 * Une pièce jointe de la bande, avec sa croix de retrait posée sur le coin haut-droit
 * (`AttachmentStrip` ).
 *
 * Une image se montre en vignette; un fichier se montre en pastille, nom sur une ligne et
 * sous-titre « type · poids » sur la suivante.
 */
@Composable
private fun AttachmentStripTile(
    attachment: PendingAttachment,
    bytes: Long?,
    onRemove: () -> Unit,
    onOpen: () -> Unit = {},
) {
    Box {
        if (attachment.isImage) {
            ImageStripThumbnail(attachment = attachment, onClick = onOpen)
        } else if (com.onyx.app.ui.chat.FileViewers.isVideo(attachment.name, attachment.mimeType)) {
            VideoStripThumbnail(attachment = attachment, onClick = onOpen)
        } else {
            FileStripChip(attachment = attachment, bytes = bytes, onClick = onOpen)
        }
        // La croix est un disque sombre sous un « × » blanc, comme `xmark.circle.fill` teinté.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-6).dp)
                .size(22.dp)
                .onyxClickable(pressedScale = 0.9f, onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape),
            )
            OnyxIcon(
                symbol = Symbols.Xmark,
                tint = Color.White,
                size = 10.dp,
                contentDescription = "Remove ${attachment.name}",
            )
        }
    }
}

/** La vignette d'une image jointe: son image, décodée à la taille où elle se montre. */
@Composable
private fun ImageStripThumbnail(attachment: PendingAttachment, onClick: () -> Unit = {}) {
    val theme = LocalOnyxTheme.current
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = attachment.uri) {
        value = withContext(Dispatchers.IO) {
            decodeAttachmentThumbnail(context, attachment.uri, StripThumbnailPixels)
        }
    }

    Box(
        modifier = Modifier
            .size(StripThumbnailSize)
            .clip(RoundedCornerShape(12.dp))
            .background(theme.fill)
            .onyxClickable(pressedScale = 0.97f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val shown = bitmap
        if (shown == null) {
            OnyxIcon(symbol = Symbols.Photo, tint = theme.tertiaryText, size = 22.dp)
        } else {
            Image(
                bitmap = shown,
                contentDescription = attachment.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** La pastille d'un fichier joint: son symbole, son nom, et « type · poids ». */
@Composable
private fun FileStripChip(attachment: PendingAttachment, bytes: Long?, onClick: () -> Unit = {}) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .height(45.dp)
            .background(theme.surfaceColor, RoundedCornerShape(12.dp))
            .onyxClickable(pressedScale = 0.97f, onClick = onClick)
            .padding(start = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OnyxIcon(symbol = fileSymbol(attachment), tint = theme.accent, size = 16.dp)
        Column {
            BasicText(
                text = attachment.name,
                localize = false,
                style = theme.caption.copy(color = theme.textColor),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                text = attachmentSubtitle(attachment.mimeType, attachment.name, bytes),
                style = theme.caption2.copy(color = theme.secondaryText),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Le symbole d'un fichier, comme `AttachmentTile`: l'onde pour le son, le document sinon. */
private fun fileSymbol(attachment: PendingAttachment): Int = when {
    attachment.mimeType.startsWith("audio/") -> Symbols.Waveform
    com.onyx.app.ui.chat.FileViewers.isVideo(attachment.name, attachment.mimeType) -> Symbols.Film
    else -> Symbols.Doc
}

/**
 * La vignette d'une vidéo jointe, dans la bande du composeur: sa première image sous un rond de
 * lecture. Une vidéo dont aucune image ne sort garde la pastille de fichier (jamais un carré vide).
 */
@Composable
private fun VideoStripThumbnail(attachment: PendingAttachment, onClick: () -> Unit = {}) {
    val theme = LocalOnyxTheme.current
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = attachment.uri) {
        value = com.onyx.app.ui.chat.VideoPoster.frame(context, attachment.uri, StripThumbnailPixels)
    }

    Box(
        modifier = Modifier
            .size(StripThumbnailSize)
            .clip(RoundedCornerShape(12.dp))
            .background(theme.fill)
            .onyxClickable(pressedScale = 0.97f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val shown = bitmap
        if (shown == null) {
            OnyxIcon(symbol = Symbols.Film, tint = theme.tertiaryText, size = 22.dp)
        } else {
            Image(
                bitmap = shown,
                contentDescription = attachment.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Le rond de lecture se pose sur l'image, comme `play.fill` du modèle.
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            OnyxIcon(symbol = Symbols.Play, tint = Color.White, size = 11.dp)
        }
    }
}

/** Le rôle du bouton rond suit l'état, comme `ComposerView.trailingButtons`. */
@Composable
private fun TrailingComposerButton(
    state: String,
    size: androidx.compose.ui.unit.Dp,
    onSend: () -> Unit,
    onSteer: () -> Unit,
    onStop: () -> Unit,
    onTalk: () -> Unit,
) {
    val theme = LocalOnyxTheme.current

    when (state) {
        "steer" -> ComposerCircleButton(
            symbol = Symbols.ArrowTurnDownRight,
            label = "Steer this reply",
            tint = theme.warning,
            size = size,
            onClick = onSteer,
        )

        "after" -> ComposerCircleButton(
            symbol = Symbols.SendAfter,
            label = "Send after this reply",
            tint = theme.accent,
            size = size,
            onClick = onSend,
        )

        "stop" -> ComposerCircleButton(
            symbol = Symbols.Stop,
            label = "Stop",
            tint = theme.error,
            size = size,
            onClick = onStop,
        )

        "send" -> ComposerCircleButton(
            symbol = Symbols.ArrowUp,
            label = "Send",
            tint = theme.accent,
            size = size,
            onClick = onSend,
        )

        else -> ComposerCircleButton(
            symbol = null,
            label = "Talk",
            tint = theme.accent,
            size = size,
            waveform = true,
            onClick = onTalk,
        )
    }
}

@Composable
private fun IconSlot(
    symbol: Int,
    label: String,
    size: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp? = null,
    glyphSize: androidx.compose.ui.unit.Dp? = null,
    tint: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = Modifier
            .size(width = size, height = height ?: size)
            .onyxClickable(pressedScale = 0.9f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        OnyxIcon(
            symbol = symbol,
            tint = tint ?: theme.secondaryText,
            size = glyphSize ?: size * 0.45f,
            contentDescription = label,
        )
    }
}

// MARK: - Le dépôt dans le composeur

/**
 * Le dépôt que le composeur accepte: les images et le texte, comme le fil (`ChatDropTarget`).
 *
 * Le nombre et le poids total sont vérifiés avant d'accepter; au-delà, le dépôt est refusé et le
 * message le dit.
 */
@Composable
private fun rememberComposerDropTarget(
    attachmentCount: Int,
    knownBytes: Long,
    onAccept: (List<Uri>) -> Unit,
    onRefuse: (String) -> Unit,
): DragAndDropTarget {
    val context = LocalContext.current
    val count = rememberUpdatedState(attachmentCount)
    val bytes = rememberUpdatedState(knownBytes)
    val accept = rememberUpdatedState(onAccept)
    val refuse = rememberUpdatedState(onRefuse)
    return remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val items = event.toAndroidDragEvent().clipData ?: return false
                val uris = buildList {
                    for (index in 0 until items.itemCount) {
                        val uri = items.getItemAt(index).uri ?: continue
                        val mime = context.contentResolver.getType(uri).orEmpty()
                        if (mime.startsWith("image/") || mime.startsWith("text/")) add(uri)
                    }
                }
                if (uris.isEmpty()) return false
                if (count.value + uris.size > MaxAttachmentCount) {
                    refuse.value(TooManyAttachmentsMessage)
                    return true
                }
                val incoming = uris.sumOf { resolveAttachmentSize(context, it) ?: 0L }
                if (bytes.value + incoming > MaxAttachmentBytes) {
                    refuse.value(AttachmentsTooLargeMessage)
                    return true
                }
                accept.value(uris)
                return true
            }
        }
    }
}

/** Vrai si le dépôt porte une image ou du texte: alors le composeur peut le recevoir. */
private fun containsAttachableDrop(event: DragAndDropEvent): Boolean {
    val description = event.toAndroidDragEvent().clipDescription ?: return false
    return (0 until description.mimeTypeCount).any { index ->
        val mime = description.getMimeType(index).orEmpty()
        mime.startsWith("image/") || mime.startsWith("text/")
    }
}

/** Le poids d'une pièce jointe, quand l'appareil le connaît (`OpenableColumns.SIZE`). */
private fun resolveAttachmentSize(context: Context, uri: Uri): Long? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        val column = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst() && column >= 0) cursor.getLong(column).takeIf { it >= 0 } else null
    }
}.getOrNull()

/** La vignette d'une image jointe, décodée à la taille où elle se montre. */
private fun decodeAttachmentThumbnail(context: Context, uri: Uri, maxPixels: Int): ImageBitmap? =
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (largest / sample > maxPixels * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)?.asImageBitmap()
        }
    }.getOrNull()

/** Le type lisible d'un fichier: « PDF », sinon son extension en majuscules. */
private fun attachmentKindLabel(mimeType: String, name: String): String {
    val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return when {
        mimeType == "application/pdf" || extension == "pdf" -> "PDF"
        extension.isNotEmpty() -> extension.uppercase(Locale.ROOT)
        else -> "File"
    }
}

/** « 2,4 Mo »: un poids lisible, à la française, comme `ByteCountFormatter`. */
internal fun attachmentSizeLabel(bytes: Long): String {
    if (bytes < 1000) return "$bytes o"
    val units = arrayOf("Ko", "Mo", "Go", "To")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1000 && unit < units.lastIndex) {
        value /= 1000.0
        unit++
    }
    val number = String.format(Locale.FRENCH, if (value < 10) "%.1f" else "%.0f", value)
    return "$number ${units[unit]}"
}

/** « PDF · 2,4 Mo »: le sous-titre d'un fichier joint, le poids quand il est connu. */
private fun attachmentSubtitle(mimeType: String, name: String, bytes: Long?): String {
    val kind = attachmentKindLabel(mimeType, name)
    return if (bytes != null) "$kind · ${attachmentSizeLabel(bytes)}" else kind
}

/** `ComposerPaste.isPicture`: une image n'est pas retenue si l'élément porte aussi du texte. */
internal fun isPictureClipboardItem(mimeType: String?, hasText: Boolean): Boolean =
    mimeType.orEmpty().startsWith("image/") && !hasText

/** Toutes les images du presse-papiers, jamais la miniature associée à un lien copié. */
internal fun clipboardImages(context: Context): List<Uri> {
    val manager = context.getSystemService(android.content.ClipboardManager::class.java) ?: return emptyList()
    val clip = manager.primaryClip ?: return emptyList()
    return buildList {
        for (index in 0 until clip.itemCount) {
            val item = clip.getItemAt(index)
            val uri = item.uri ?: continue
            val mime = context.contentResolver.getType(uri)
            if (isPictureClipboardItem(mime, hasText = item.text != null || item.htmlText != null)) add(uri)
        }
    }
}
