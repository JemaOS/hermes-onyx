package com.onyx.app.ui.bots

import com.onyx.app.ui.design.OnyxSpace

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.localization.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.onyx.app.ui.chat.AttachmentLimits
import com.onyx.app.ui.chat.admitAttachments
import com.onyx.app.ui.chat.takeDurablePermissions
import com.onyx.app.ui.chat.components.OnyxComposer
import com.onyx.app.ui.chat.components.clipboardImages
import com.onyx.app.ui.design.AttachmentAction
import com.onyx.app.ui.design.AttachmentSheet
import com.onyx.app.ui.design.BotLook
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.LiveMark
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.voice.SpeechEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The heard text joins the initial draft, after a space only when necessary. */
internal fun dictationJoined(draft: String, heard: String): String {
    val clean = heard.trim()
    if (clean.isEmpty()) return draft
    if (draft.isEmpty()) return clean
    return if (draft.last().isWhitespace()) draft + clean else "$draft $clean"
}

private val GroupTagPattern = Regex("""(?:^|\s)@([\p{L}\p{N}_-]*)$""")

internal fun groupTagToken(draft: String): String? =
    GroupTagPattern.find(draft)?.groupValues?.get(1)

/** All bots whose handle or name starts with the mention in progress. */
internal fun groupTagSuggestions(draft: String, bots: List<BotProfile>): List<BotProfile> {
    val typed = groupTagToken(draft)?.lowercase() ?: return emptyList()
    return bots.filter {
        typed.isEmpty() || it.handle.lowercase().startsWith(typed) || it.label().lowercase().startsWith(typed)
    }
}

/** Replaces only the mention in progress and keeps exactly the whitespace that preceded it. */
internal fun replaceTagToken(draft: String, handle: String): String {
    val match = GroupTagPattern.find(draft) ?: return draft
    val leading = match.value.first().takeIf { it.isWhitespace() }?.toString().orEmpty()
    return draft.substring(0, match.range.first) + leading + "@" + handle + " "
}

/** True if the field carries something to send (`GroupComposer.hasDraft`). */
internal fun groupComposerHasDraft(draft: String, attachmentCount: Int): Boolean =
    draft.isNotBlank() || attachmentCount > 0

/**
 * The **title** the house composer receives for a room: it is what makes the field prompt say
 * "Envoyer un message au salon" ("Send a message to the room") (`ComposerFieldInvite.GROUP`,
 * `ComposerFieldLayers.kt`), and **nothing else** rewrites this label. A room has no bot name to
 * write in the field: this title is the group's landmark, not a body text.
 */
internal const val GroupComposerTitle = "the group"

/** A file named by a note: fetched from the server then opened, with a retryable failure. */
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
            else -> OnyxIcon(if (picture) Symbols.Photo else Symbols.Doc, tint = theme.textColor, size = 13.dp)
        }
        BasicText(
            text = name,
            localize = false,
            style = theme.footnote.copy(color = theme.textColor),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The name of a file whose upload is still in progress (`feed.sending`). */
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
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The field under a group chat.
 *
 * **It no longer has its own drawing.** Everything visible — the "+", the field, the mic, the voice
 * button, the stop button, the send button (accent circle, ink arrow **measured**) — comes from the
 * house composer ([OnyxComposer]). What remains here is what is **legitimately** specific to the group:
 *
 * - the **prompt** of the room (the title `the group` → "Send a message to the room", through the
 *   single source `ComposerFieldInvite.GROUP`);
 * - the **attachments** (the Add menu, the photo library, the camera, the sheet) and their review
 *   by `admitAttachments` — the room only takes text, whatever is attached goes up to the server;
 * - the field's **dictation** (the text joins the draft, nothing is sent);
 * - the `@` **mentions**: the list of bots offered while typing, drawn above the field.
 *
 * The state (draft, attachments, dictation) lives here, like the `@State` of the reference: the
 * screen only provides the bots, the turn state and the send, stop and voice actions.
 */
@Composable
fun GroupComposer(
    bots: List<BotProfile>,
    isStreaming: Boolean,
    onSend: (String, List<PendingAttachment>) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    /** The voice button opens the room's voice scene (`GroupVoice`), from the empty field. */
    onTalk: (() -> Unit)? = null,
    /** The feed's error (`feed.problem`), shown when the composer has none of its own. */
    serverError: String? = null,
    /**
     * **The room's lead, as written** (its room name, otherwise its handle): the line above
     * reminds of it as long as it is chosen, because an invisible default recipient cannot be
     * told apart from a room without a lead. `null` — no line, and the composer is exactly the
     * one from before.
     */
    leadLabel: String? = null,
    /** The field's dictation language, distinct from the hands-free scene. */
    speechLocale: String = SpeechEngine.DEFAULT_LOCALE,
    /** Given by the screen that must return focus to the field (`Switch to typing`). */
    focusRequester: FocusRequester? = null,
    /**
     * **The composer sits on a screen's single plate** (the bottom stack): it no longer paints
     * its own card, the plate handles it. False: the original card.
     */
    plate: Boolean = false,
    /**
     * Is the bottom system inset handled by the composer? `false` when the screen handles it
     * **itself, once only**, for the whole bottom stack (composer **and** tab bar) — this is what
     * prevents the two insets (keyboard and navigation) from adding up.
     */
    applyBottomInsets: Boolean = true,
    /**
     * The subject a feed's **"Chat"** button wrote: it joins the draft **once**, then the room
     * says it took it (`onPrefillConsumed`) — so a tab round-trip does not rewrite it over what
     * the person is typing.
     */
    prefill: String? = null,
    onPrefillConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf<List<PendingAttachment>>(emptyList()) }
    var problem by remember { mutableStateOf<String?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val hasDraft = groupComposerHasDraft(draft, attachments.size)
    val suggestions = remember(draft, bots) { groupTagSuggestions(draft, bots) }

    fun addAttachments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        // The same review as the chat: a file above the ceiling is refused here too, with its
        // size and the limit, instead of going up whole to the server.
        scope.launch {
            val admission = withContext(Dispatchers.IO) {
                // The persistable permission when it exists, **then** the durable copy: only the
                // copy guarantees an attachment stays readable after the app is closed.
                takeDurablePermissions(context, uris)
                admitAttachments(context, attachments, uris)
            }
            attachments = attachments + admission.accepted
            problem = admission.problem
        }
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

    // The field's mic is an independent dictation: the text joins the draft, nothing is sent.
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
        speechEngine.start(true)
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

    /** Ends the dictation where it stands; what it heard is already in the draft. */
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

    // The subject of a feed's "Chat" button: it joins the draft **once**.
    LaunchedEffect(prefill) {
        val subject = prefill ?: return@LaunchedEffect
        draft = subject
        onPrefillConsumed()
        focusRequester?.requestFocus()
    }

    /** Sends the draft and its attachments, then clears the field (`GroupComposer.send`). */
    fun send() {
        stopDictating()
        if (!hasDraft) return
        problem = null
        onSend(draft, attachments)
        draft = ""
        attachments = emptyList()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // The bots proposed while typing an `@` mention — above the field, like a conversation's
        // mention list.
        if (suggestions.isNotEmpty()) {
            GroupTagSuggestions(
                bots = suggestions,
                onPick = { bot ->
                    draft = replaceTagToken(draft, bot.handle)
                    focusRequester?.requestFocus()
                },
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            )
        }

        // **The lead, recalled above the field.** Without this line, a local setting
        // (a default recipient) is not visible anywhere: the room would write to someone without
        // the person having it in front of them. Nothing when there is no lead.
        leadLabel?.takeIf { it.isNotBlank() }?.let { label ->
            val theme = LocalOnyxTheme.current
            BasicText(
                text = OnyxLocalization.format("Lead: %1\$s", label),
                localize = false,
                style = theme.caption.copy(color = theme.secondaryText, fontWeight = FontWeight.Medium),
                // A label wraps, it is not cut off: "Lead: …" on one line became
                // "Lead: pla…" at 1.3× font size (truncation policy, `OnyxTextPolicy`).
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 2.dp),
            )
        }

        // **The house composer — the only drawing of the send command.**
        OnyxComposer(
            draft = draft,
            onDraftChange = { draft = it },
            isStreaming = isStreaming,
            title = GroupComposerTitle,
            attachments = attachments,
            onRemoveAttachment = { id -> attachments = attachments.filterNot { it.id == id } },
            onPasteImages = { uris -> addAttachments(uris) },
            onSend = { send() },
            onStop = {
                stopDictating()
                onStop()
            },
            // Voice is only drawn if a voice scene is offered: never a mute command.
            onTalk = { onTalk?.invoke() },
            showTalk = onTalk != null,
            dictating = dictating,
            onDictate = { toggleDictation() },
            problem = problem ?: serverError,
            localizeProblem = problem == null,
            onAddAttachment = { showAddMenu = true },
            // The long-draft editor needs a screen the room does not have: the command
            // therefore does not appear, rather than opening emptiness (see the batch report).
            allowExpand = false,
            focusRequester = focusRequester,
            plate = plate,
            applyBottomInsets = applyBottomInsets,
        )
    }

    // The sheet that rises from the bottom, the same as the chat's: the title, Onyx's **real**
    // limits as a subtitle, and one row per entry with its icon badge. The order is that of the
    // reference; "Paste" ("Coller") is added, the reference does not have it.
    if (showAddMenu) {
        AttachmentSheet(
            title = "Add to message",
            subtitle = AttachmentLimits.subtitle(),
            actions = buildList {
                add(AttachmentAction("Photo Library", Symbols.PhotoOnRectangle) {
                    showAddMenu = false
                    photoPicker.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                        ),
                    )
                })
                if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
                    add(AttachmentAction("Camera", Symbols.Camera) {
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
                add(AttachmentAction("Files", Symbols.Paperclip) {
                    showAddMenu = false
                    filePicker.launch(arrayOf("*/*"))
                })
                add(AttachmentAction("Paste", Symbols.DocOnClipboard) {
                    showAddMenu = false
                    addAttachments(clipboardImages(context))
                })
            },
            onDismiss = { showAddMenu = false },
        )
    }
}

/** The bots offered during an `@` mention (`GroupComposer.tags`): face, handle and name. */
@Composable
private fun GroupTagSuggestions(
    bots: List<BotProfile>,
    onPick: (BotProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.surfaceColor, RoundedCornerShape(OnyxSpace.FieldCorner))
            .border(1.dp, theme.separator, RoundedCornerShape(OnyxSpace.FieldCorner)),
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * Where the group's camera writes its shot: the **durable private storage**, never the cache.
 *
 * Same default as in the simple chat: a shot in `cacheDir` disappears on cleanup, and the
 * bubble becomes "Unreadable" ("Illisible").
 */
private fun groupCameraUri(context: Context): Uri? = runCatching {
    val folder = java.io.File(context.filesDir, "photos").apply { mkdirs() }
    val file = java.io.File(folder, "group-${System.currentTimeMillis()}.jpg")
    androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}.getOrNull()
