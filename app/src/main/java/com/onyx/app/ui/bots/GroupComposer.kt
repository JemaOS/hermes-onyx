package com.onyx.app.ui.bots

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import com.onyx.app.ui.localization.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onyx.app.data.model.AttachedFileNote
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.data.model.serve.DeliveredFile
import com.onyx.app.ui.chat.DeliveredFiles
import com.onyx.app.ui.chat.PendingAttachment
import com.onyx.app.ui.chat.pendingAttachmentsFromUris
import com.onyx.app.ui.chat.components.clipboardImages
import com.onyx.app.ui.design.BotLook
import com.onyx.app.ui.design.ComposerCircleButton
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxMenu
import com.onyx.app.ui.design.LiveMark
import com.onyx.app.ui.design.MenuItem
import com.onyx.app.ui.design.OnyxMotion
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.glassSurface
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.voice.SpeechEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Le texte entendu rejoint le brouillon initial, après une espace seulement si nécessaire. */
internal fun dictationJoined(draft: String, heard: String): String {
    val clean = heard.trim()
    if (clean.isEmpty()) return draft
    if (draft.isEmpty()) return clean
    return if (draft.last().isWhitespace()) draft + clean else "$draft $clean"
}

private val GroupTagPattern = Regex("""(?:^|\s)@([\p{L}\p{N}_-]*)$""")

internal fun groupTagToken(draft: String): String? =
    GroupTagPattern.find(draft)?.groupValues?.get(1)

/** Tous les bots dont la poignée ou le nom commence par la mention en cours. */
internal fun groupTagSuggestions(draft: String, bots: List<BotProfile>): List<BotProfile> {
    val typed = groupTagToken(draft)?.lowercase() ?: return emptyList()
    return bots.filter {
        typed.isEmpty() || it.handle.lowercase().startsWith(typed) || it.label().lowercase().startsWith(typed)
    }
}

/** Remplace seulement la mention en cours et conserve exactement le blanc qui la précédait. */
internal fun replaceTagToken(draft: String, handle: String): String {
    val match = GroupTagPattern.find(draft) ?: return draft
    val leading = match.value.first().takeIf { it.isWhitespace() }?.toString().orEmpty()
    return draft.substring(0, match.range.first) + leading + "@" + handle + " "
}

/** Vrai si le champ porte quelque chose à envoyer (`GroupComposer.hasDraft`). */
internal fun groupComposerHasDraft(draft: String, attachmentCount: Int): Boolean =
    draft.isNotBlank() || attachmentCount > 0

/** Ce que le bouton rond du groupe propose: arrêter, parler, ou envoyer. */
internal enum class GroupComposerAction { STOP, TALK, SEND }

/**
 * Le rôle du bouton rond, exactement la cascade: « Stop » pendant un tour quand le
 * champ est vide, « Talk » quand le champ est vide et qu'une scène vocale est offerte, « Send »
 * sinon (désactivé tant que rien n'est écrit).
 */
internal fun groupComposerAction(hasDraft: Boolean, isStreaming: Boolean, canTalk: Boolean): GroupComposerAction = when {
    isStreaming && !hasDraft -> GroupComposerAction.STOP
    !hasDraft && canTalk -> GroupComposerAction.TALK
    else -> GroupComposerAction.SEND
}

/** Le texte d'invite du champ: « Listening… » pendant la dictée, sinon l'invite de la salle. */
internal fun groupComposerPlaceholder(dictating: Boolean): String =
    if (dictating) "Listening…" else "Message the group"

/** Un fichier nommé par une note: récupération du serveur puis ouverture, avec échec rejouable. */
@Composable
fun GroupFileChip(path: String, server: ServerConfig, modifier: Modifier = Modifier) {
    val theme = LocalOnyxTheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fetching by remember(path) { mutableStateOf(false) }
    var failed by remember(path) { mutableStateOf(false) }
    val name = AttachedFileNote.sentName(path)
    val picture = DeliveredFiles.isImage(name)

    Row(
        modifier = modifier
            .background(theme.fill, RoundedCornerShape(50))
            .onyxClickable(enabled = !fetching, pressedScale = 0.95f) {
                fetching = true
                failed = false
                scope.launch {
                    val result = DeliveredFiles.open(
                        context = context,
                        config = server,
                        file = DeliveredFile(name = name, path = path),
                    )
                    failed = result.isFailure
                    fetching = false
                }
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            fetching -> LiveMark(color = theme.secondaryText)
            failed -> OnyxIcon(Symbols.ExclamationTriangle, tint = theme.warning, size = 13.dp)
            else -> OnyxIcon(if (picture) Symbols.Photo else Symbols.Doc, tint = theme.accent, size = 13.dp)
        }
        BasicText(
            text = name,
            localize = false,
            style = theme.footnote.copy(color = theme.textColor),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Le nom d'un fichier dont l'upload est encore en cours (`feed.sending`). */
@Composable
fun PendingGroupFileChip(name: String, modifier: Modifier = Modifier) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = modifier
            .background(theme.fill, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OnyxIcon(Symbols.ArrowUpCircle, tint = theme.secondaryText, size = 13.dp)
        BasicText(
            text = name,
            localize = false,
            style = theme.footnote.copy(color = theme.secondaryText),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Le champ sous un chat de groupe (comme `GroupComposer`): les pièces jointes du message (Add,
 * avec Files, la photothèque et l'appareil photo; les images et fichiers en attente; la dictée;
 * Send et Stop), et les bots offerts pendant qu'une mention `@` est tapée. Une salle ne prend que
 * du texte: ce qui est joint monte au serveur et le message le nomme.
 *
 * L'état (brouillon, pièces jointes, dictée, pièces jointes) vit ici, comme les `@State`:
 * l'écran ne fournit que les bots, l'état du tour et les actions d'envoi, d'arrêt et de voix.
 */
@Composable
fun GroupComposer(
    bots: List<BotProfile>,
    isStreaming: Boolean,
    onSend: (String, List<PendingAttachment>) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    /** Le bouton Talk ouvre la scène vocale de la salle (`GroupVoice`), depuis le champ vide. */
    onTalk: (() -> Unit)? = null,
    /** L'erreur du fil (`feed.problem`), montrée quand le composer n'a pas la sienne. */
    serverError: String? = null,
    /** La langue de la dictée du champ, distincte de la scène mains-libres. */
    speechLocale: String = SpeechEngine.DEFAULT_LOCALE,
    /** Donné par l'écran qui doit rendre le focus au champ (`Switch to typing`). */
    focusRequester: FocusRequester? = null,
) {
    val theme = LocalOnyxTheme.current
    val context = LocalContext.current
    var draft by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf<List<PendingAttachment>>(emptyList()) }
    var problem by remember { mutableStateOf<String?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val hasDraft = groupComposerHasDraft(draft, attachments.size)
    val suggestions = remember(draft, bots) { groupTagSuggestions(draft, bots) }

    fun addAttachments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        attachments = attachments + pendingAttachmentsFromUris(context.contentResolver, uris)
        problem = null
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        addAttachments(uris)
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { uris ->
        addAttachments(uris)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) addAttachments(listOf(uri))
        cameraUri = null
    }

    // Le micro du champ est une dictée indépendante: le texte rejoint le brouillon, rien n'est envoyé.
    val speechEngine = remember(speechLocale) { SpeechEngine(context, speechLocale) }
    val speechState by speechEngine.state.collectAsStateWithLifecycle()
    var dictating by remember { mutableStateOf(false) }
    var dictationOpened by remember { mutableStateOf(false) }
    var dictationBase by remember { mutableStateOf<String?>(null) }
    DisposableEffect(speechEngine) { onDispose { speechEngine.release() } }

    fun beginDictation() {
        problem = null
        dictationBase = draft
        dictationOpened = false
        dictating = true
        speechEngine.start()
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            beginDictation()
        } else {
            dictating = false
            dictationBase = null
            problem = "Onyx needs the microphone to listen. Allow it in Android's Settings → Apps → Onyx."
        }
    }

    fun toggleDictation() {
        if (dictating) {
            speechEngine.stop()
        } else {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) beginDictation() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    /** Finit la dictée où elle en est; ce qu'elle a entendu est déjà dans le brouillon. */
    fun stopDictating() {
        dictating = false
        dictationOpened = false
        dictationBase = null
        speechEngine.cancel()
    }

    LaunchedEffect(speechState, dictating) {
        if (!dictating) return@LaunchedEffect
        if (!dictationOpened) {
            if (!speechState.isListening) return@LaunchedEffect
            dictationOpened = true
        }
        dictationBase?.let { start ->
            if (speechState.transcript.isNotEmpty()) draft = dictationJoined(start, speechState.transcript)
        }
        speechState.problem?.let { problem = it }
        if (speechState.isFinal) {
            dictating = false
            dictationOpened = false
            dictationBase = null
        }
    }

    /** Envoie le brouillon et ses pièces jointes, puis vide le champ (`GroupComposer.send`). */
    fun send() {
        stopDictating()
        if (!hasDraft) return
        problem = null
        onSend(draft, attachments)
        draft = ""
        attachments = emptyList()
    }

    val action = groupComposerAction(hasDraft, isStreaming, onTalk != null)
    val shownProblem = problem ?: serverError

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.pageColor.copy(alpha = 0.94f))
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Les bots proposés pendant qu'on tape une mention `@`.
        if (suggestions.isNotEmpty()) {
            GroupTagSuggestions(
                bots = suggestions,
                onPick = { bot ->
                    draft = replaceTagToken(draft, bot.handle)
                    focusRequester?.requestFocus()
                },
            )
        }

        // Les images et fichiers en attente de partir.
        if (attachments.isNotEmpty()) {
            GroupAttachmentStrip(
                attachments = attachments,
                onRemove = { id -> attachments = attachments.filterNot { it.id == id } },
            )
        }

        if (shownProblem != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OnyxIcon(Symbols.ExclamationTriangle, tint = theme.warning, size = 13.dp)
                BasicText(
                    text = shownProblem,
                    localize = problem != null,
                    style = theme.footnote.copy(color = theme.warning),
                )
            }
        }

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            GroupComposerField(
                draft = draft,
                onDraftChange = { draft = it },
                dictating = dictating,
                onToggleDictation = { toggleDictation() },
                onAdd = { showAddMenu = true },
                focusRequester = focusRequester,
                modifier = Modifier.weight(1f),
            )
            AnimatedContent(
                targetState = action,
                transitionSpec = {
                    (
                        scaleIn(initialScale = 0.4f, animationSpec = OnyxMotion.Sites.MessageActions) +
                            fadeIn(animationSpec = OnyxMotion.Sites.MessageActions)
                        ).togetherWith(
                        scaleOut(targetScale = 0.4f, animationSpec = OnyxMotion.Sites.MessageActions) +
                            fadeOut(animationSpec = OnyxMotion.Sites.MessageActions),
                    )
                },
                label = "groupComposerTrailingButton",
            ) { current ->
                when (current) {
                    GroupComposerAction.STOP -> ComposerCircleButton(
                        symbol = Symbols.Stop,
                        label = "Stop",
                        tint = theme.error,
                        size = 44.dp,
                    ) {
                        stopDictating()
                        onStop()
                    }

                    GroupComposerAction.TALK -> ComposerCircleButton(
                        symbol = null,
                        label = "Talk",
                        tint = theme.accent,
                        size = 44.dp,
                        waveform = true,
                    ) {
                        stopDictating()
                        onTalk?.invoke()
                    }

                    GroupComposerAction.SEND -> ComposerCircleButton(
                        symbol = Symbols.ArrowUp,
                        label = "Send",
                        tint = theme.accent,
                        size = 44.dp,
                        enabled = hasDraft,
                        onClick = { send() },
                    )
                }
            }
        }
    }

    // Le menu « + »: Files, la photothèque, l'appareil photo, et le collage Android.
    if (showAddMenu) {
        OnyxMenu(
            items = buildList {
                add(MenuItem("Files", Symbols.Folder) {
                    showAddMenu = false
                    filePicker.launch(arrayOf("*/*"))
                })
                add(MenuItem("Photo Library", Symbols.PhotoOnRectangle) {
                    showAddMenu = false
                    photoPicker.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                        ),
                    )
                })
                if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
                    add(MenuItem("Camera", Symbols.Camera) {
                        showAddMenu = false
                        val uri = groupCameraUri(context)
                        if (uri == null) {
                            problem = "Camera isn't available."
                        } else {
                            cameraUri = uri
                            camera.launch(uri)
                        }
                    })
                }
                add(MenuItem("Paste", Symbols.DocOnDoc) {
                    showAddMenu = false
                    addAttachments(clipboardImages(context))
                })
            },
            onDismiss = { showAddMenu = false },
        )
    }
}

/**
 * Le champ de la salle: Add, le texte, et le micro, sur le verre ou la surface du thème
 * (`GroupComposer.field`). Le rayon est fixe (22), comme le champ.
 */
@Composable
private fun GroupComposerField(
    draft: String,
    onDraftChange: (String) -> Unit,
    dictating: Boolean,
    onToggleDictation: () -> Unit,
    onAdd: () -> Unit,
    focusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .then(
                if (theme.usesGlass) {
                    Modifier.glassSurface(shape)
                } else {
                    Modifier.background(theme.surfaceColor, shape)
                },
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .onyxClickable(pressedScale = 0.9f, onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            OnyxIcon(
                symbol = Symbols.Plus,
                tint = theme.secondaryText,
                size = 20.dp,
                contentDescription = "Add",
            )
        }

        Box(modifier = Modifier.weight(1f).defaultMinSize(minHeight = 38.dp)) {
            if (draft.isEmpty()) {
                BasicText(
                    text = groupComposerPlaceholder(dictating),
                    style = theme.body.copy(color = theme.tertiaryText),
                    modifier = Modifier.padding(vertical = 9.dp),
                )
            }
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                textStyle = theme.userFont.copy(color = theme.textColor),
                cursorBrush = SolidColor(theme.accent),
                maxLines = 6,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Default,
                ),
                modifier = Modifier
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .fillMaxWidth()
                    .padding(vertical = 9.dp),
            )
        }

        // Le micro: la dictée écrit le message, elle ne l'envoie pas (`DictateButton`).
        Box(
            modifier = Modifier
                .size(width = 34.dp, height = 38.dp)
                .onyxClickable(pressedScale = 0.9f, onClick = onToggleDictation),
            contentAlignment = Alignment.Center,
        ) {
            OnyxIcon(
                symbol = if (dictating) Symbols.Waveform else Symbols.MicOutline,
                tint = if (dictating) theme.error else theme.secondaryText,
                size = 17.dp,
                contentDescription = if (dictating) "Stop dictating" else "Dictate",
            )
        }
    }
}

/** Les bots offerts pendant une mention `@` (`GroupComposer.tags`): visage, poignée et nom. */
@Composable
private fun GroupTagSuggestions(
    bots: List<BotProfile>,
    onPick: (BotProfile) -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.surfaceColor, RoundedCornerShape(14.dp))
            .border(1.dp, theme.separator, RoundedCornerShape(14.dp)),
    ) {
        bots.forEach { bot ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onyxClickable(pressedScale = 0.98f) { onPick(bot) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BotLook(name = bot.name, size = 22.dp, faceCode = bot.faceCode)
                BasicText(
                    text = "@" + bot.handle,
                    localize = false,
                    style = theme.body.copy(color = theme.textColor, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
                BasicText(
                    text = bot.label(),
                    localize = false,
                    style = theme.caption.copy(color = theme.secondaryText),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * La bande des pièces jointes du message (`AttachmentStrip`): une vignette par image, une
 * pastille par fichier, et la croix de retrait sur le coin haut-droit de chacune.
 */
@Composable
private fun GroupAttachmentStrip(
    attachments: List<PendingAttachment>,
    onRemove: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        attachments.forEach { attachment ->
            Box {
                if (attachment.isImage) {
                    GroupImageThumbnail(attachment)
                } else {
                    GroupFileTile(attachment)
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-6).dp)
                        .size(22.dp)
                        .onyxClickable(pressedScale = 0.9f) { onRemove(attachment.id) },
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
    }
}

/** La vignette d'une image jointe, décodée à la taille où elle se montre (`AttachmentTile`). */
@Composable
private fun GroupImageThumbnail(attachment: PendingAttachment) {
    val theme = LocalOnyxTheme.current
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = attachment.uri) {
        value = withContext(Dispatchers.IO) { groupThumbnail(context, attachment.uri, GroupThumbnailPixels) }
    }

    Box(
        modifier = Modifier
            .size(GroupThumbnailSize)
            .clip(RoundedCornerShape(12.dp))
            .background(theme.fill),
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
                modifier = Modifier.size(GroupThumbnailSize),
            )
        }
    }
}

/** La pastille d'un fichier joint: son symbole, son nom, sur la surface du thème. */
@Composable
private fun GroupFileTile(attachment: PendingAttachment) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .height(45.dp)
            .background(theme.surfaceColor, RoundedCornerShape(12.dp))
            .padding(start = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OnyxIcon(
            symbol = if (attachment.mimeType.startsWith("audio/")) Symbols.Waveform else Symbols.Doc,
            tint = theme.accent,
            size = 16.dp,
        )
        BasicText(
            text = attachment.name,
            localize = false,
            style = theme.caption.copy(color = theme.textColor),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Le côté d'une vignette de pièce jointe, et le repère de décodage qui va avec. */
private val GroupThumbnailSize = 64.dp
private const val GroupThumbnailPixels = 256

/** La vignette d'une image jointe, décodée à la taille où elle se montre. */
private fun groupThumbnail(context: Context, uri: Uri, maxPixels: Int): ImageBitmap? = runCatching {
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

/** Le fichier temporaire partagé où l'appareil photo du groupe écrit sa prise. */
private fun groupCameraUri(context: Context): Uri? = runCatching {
    val folder = java.io.File(context.cacheDir, "photos").apply { mkdirs() }
    val file = java.io.File(folder, "group-${System.currentTimeMillis()}.jpg")
    androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}.getOrNull()
