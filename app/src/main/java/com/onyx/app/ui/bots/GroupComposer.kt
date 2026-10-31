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

/**
 * Le **titre** que le compositeur de la maison reçoit pour une salle : c'est lui qui fait dire
 * « Envoyer un message au salon » à l'invite du champ (`ComposerFieldInvite.GROUP`,
 * `ComposerFieldLayers.kt`), et **rien d'autre** ne réécrit ce libellé. Une salle n'a pas de nom
 * de bot à écrire dans le champ : ce titre est le repère du groupe, pas un texte de texte.
 */
internal const val GroupComposerTitle = "the group"

/** Un fichier nommé par une note : récupération du serveur puis ouverture, avec échec rejouable. */
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
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Le champ sous un chat de groupe.
 *
 * **Il n'a plus de dessin à lui.** Tout ce qui se voit — le « + », le champ, le micro, le bouton
 * vocal, l'arrêt, l'envoi (cercle à l'accent, flèche à l'encre **mesurée**) — vient du compositeur
 * de la maison ([OnyxComposer]). Ce qui reste ici est ce qui est **légitimement** propre au groupe :
 *
 * - l'**invite** de la salle (le titre `the group` → « Envoyer un message au salon », par la source
 *   unique `ComposerFieldInvite.GROUP`) ;
 * - les **pièces jointes** (le menu Add, la photothèque, l'appareil photo, la lame) et leur examen
 *   par `admitAttachments` — la salle ne prend que du texte, ce qui est joint monte au serveur ;
 * - la **dictée** du champ (le texte rejoint le brouillon, rien n'est envoyé) ;
 * - les **mentions** `@` : la liste des bots offerte pendant la frappe, dessinée au-dessus du champ.
 *
 * L'état (brouillon, pièces jointes, dictée) vit ici, comme les `@State` de la référence : l'écran ne
 * fournit que les bots, l'état du tour et les actions d'envoi, d'arrêt et de voix.
 */
@Composable
fun GroupComposer(
    bots: List<BotProfile>,
    isStreaming: Boolean,
    onSend: (String, List<PendingAttachment>) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    /** Le bouton vocal ouvre la scène vocale de la salle (`GroupVoice`), depuis le champ vide. */
    onTalk: (() -> Unit)? = null,
    /** L'erreur du fil (`feed.problem`), montrée quand le compositeur n'a pas la sienne. */
    serverError: String? = null,
    /** La langue de la dictée du champ, distincte de la scène mains-libres. */
    speechLocale: String = SpeechEngine.DEFAULT_LOCALE,
    /** Donné par l'écran qui doit rendre le focus au champ (`Switch to typing`). */
    focusRequester: FocusRequester? = null,
    /**
     * **Le compositeur est posé sur la plaque unique** d'un écran (la pile du bas) : il ne peint
     * plus sa propre carte, la plaque s'en charge. Faux : la carte d'origine.
     */
    plate: Boolean = false,
    /**
     * La marge système du bas est-elle prise par le compositeur ? `false` quand l'écran la prend
     * **lui-même, une seule fois**, pour toute la pile du bas (compositeur **et** barre d'onglets) —
     * c'est ce qui empêche les deux marges (clavier et navigation) de s'additionner.
     */
    applyBottomInsets: Boolean = true,
    /**
     * Le sujet qu'un **« Discuter »** du flux a écrit : il rejoint le brouillon **une fois**, puis
     * la salle dit qu'elle l'a pris (`onPrefillConsumed`) — ainsi un aller-retour d'onglet ne le
     * réécrit pas par-dessus ce que la personne tape.
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
        // Le même examen que le chat : un fichier au-dessus du plafond est refusé ici aussi, avec
        // son poids et la limite, au lieu de partir entier vers le serveur.
        scope.launch {
            val admission = withContext(Dispatchers.IO) {
                // L'autorisation persistable quand elle existe, **puis** la copie durable : seule
                // la copie garantit qu'une pièce reste lisible après fermeture de l'app.
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

    // Le micro du champ est une dictée indépendante : le texte rejoint le brouillon, rien n'est envoyé.
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

    /** Finit la dictée où elle en est ; ce qu'elle a entendu est déjà dans le brouillon. */
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

    // Le sujet d'un « Discuter » du flux : il rejoint le brouillon **une seule fois**.
    LaunchedEffect(prefill) {
        val subject = prefill ?: return@LaunchedEffect
        draft = subject
        onPrefillConsumed()
        focusRequester?.requestFocus()
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

    Column(modifier = modifier.fillMaxWidth()) {
        // Les bots proposés pendant qu'on tape une mention `@` — au-dessus du champ, comme la liste
        // des mentions d'une conversation.
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

        // **Le compositeur de la maison — le seul dessin de la commande d'envoi.**
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
            // Le vocal n'est dessiné que si une scène vocale est offerte : jamais une commande muette.
            onTalk = { onTalk?.invoke() },
            showTalk = onTalk != null,
            dictating = dictating,
            onDictate = { toggleDictation() },
            problem = problem ?: serverError,
            localizeProblem = problem == null,
            onAddAttachment = { showAddMenu = true },
            // L'éditeur de brouillon long demande un écran que la salle n'a pas : la commande
            // n'apparaît donc pas, plutôt que d'ouvrir le vide (voir le rapport du lot).
            allowExpand = false,
            focusRequester = focusRequester,
            plate = plate,
            applyBottomInsets = applyBottomInsets,
        )
    }

    // La feuille qui monte du bas, la même que celle du chat : le titre, les **vraies** limites
    // d'Onyx en sous-titre, et une ligne par entrée avec sa pastille d'icône. L'ordre est celui de
    // la référence ; « Coller » s'y ajoute, la référence ne l'a pas.
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

/** Les bots offerts pendant une mention `@` (`GroupComposer.tags`) : visage, poignée et nom. */
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
 * Où l'appareil photo du groupe écrit sa prise : le **stockage privé durable**, jamais le cache.
 *
 * Même défaut que dans le chat simple : une prise dans `cacheDir` disparaît au nettoyage, et la
 * bulle devient « Illisible ».
 */
private fun groupCameraUri(context: Context): Uri? = runCatching {
    val folder = java.io.File(context.filesDir, "photos").apply { mkdirs() }
    val file = java.io.File(folder, "group-${System.currentTimeMillis()}.jpg")
    androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}.getOrNull()
