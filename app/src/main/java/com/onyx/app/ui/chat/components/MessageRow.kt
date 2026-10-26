package com.onyx.app.ui.chat.components

import androidx.annotation.DrawableRes
import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.ApprovalRequest
import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.ToolActivity
import com.onyx.app.data.model.ToolStatus
import com.onyx.app.data.model.TurnMetrics
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.chat.TurnPhase
import com.onyx.app.ui.chat.TurnStatusCopy
import com.onyx.app.ui.design.FoldHeader
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.LiveMark
import com.onyx.app.ui.design.OnyxMotion
import com.onyx.app.ui.design.ambientLoop
import com.onyx.app.ui.design.SelectableText
import com.onyx.app.ui.design.ShimmerText
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.WaveformPulse
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Une ligne du fil de conversation.
 *
 * L'agent parle sous son nom, ses réponses sont posées à plat sur la page, son raisonnement et
 * ses appels d'outils se replient sous une même ligne à chevron, et vos messages forment une
 * bulle à droite — ou une ligne de shell `❯` dans les thèmes CRT.
 */
@Composable
fun MessageRow(
    message: ChatMessage,
    replyName: String,
    isLive: Boolean,
    /** L'état honnête du tour en cours: l'étape connue et les horaires (`ChatUiState.live`). */
    live: TurnPhase? = null,
    modifier: Modifier = Modifier,
    showActions: Boolean = true,
    /** La fiche du premier lien du message (`Réglages → Apparence → Aperçus des liens`). */
    linkPreviews: Boolean = false,
    onRegenerate: (() -> Unit)? = null,
    onReadAloud: (() -> Unit)? = null,
    isReading: Boolean = false,
    onApproval: (approvalId: String, choice: String) -> Unit = { _, _ -> },
    onOpenDelivered: ((com.onyx.app.data.model.serve.DeliveredFile) -> Unit)? = null,
    highlighted: Boolean = false,
    onToast: ((String) -> Unit)? = null,
    onOpenPatch: ((String) -> Unit)? = null,
    onSelectText: ((String) -> Unit)? = null,
    onOpenPhotos: ((List<com.onyx.app.data.model.Attachment>, Int) -> Unit)? = null,
    onOpenFile: ((com.onyx.app.data.model.Attachment) -> Unit)? = null,
    onOpenSubagent: ((com.onyx.app.data.model.SubagentActivity) -> Unit)? = null,
    onClarify: ((Map<String, String>) -> Unit)? = null,
    onSudo: ((String) -> Unit)? = null,
    onSecret: ((String) -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current
    val promptPrefix = theme.promptPrefix
    val promptStyled = promptPrefix != null && message.isUser
    val isAssistant = !message.isUser
    val flatReply = isAssistant && theme.palette.flatReplies && message.error == null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (highlighted) {
                    Modifier
                        .background(theme.accent.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                } else {
                    Modifier
                },
            ),
        horizontalAlignment = if (message.isUser && !promptStyled) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Le nom du bot ne se pose que s'il a quelque chose à coiffer: un message vide coiffé
        // de son nom fait lire le message de l'utilisateur qui suit comme sa réponse (défaut
        // signalé par le propriétaire).
        if (isAssistant && promptPrefix == null && (isLive || hasSomethingToShow(message))) {
            SpeakerLine(message = message, name = replyName, isLive = isLive)
        }

        // Une note de pilotage s'affiche en pastille, jamais en bulle: « Your steer »
        // (Label orange à 12 %, pied de page, texte secondaire).
        if (message.isSteer) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFFF9500).copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OnyxIcon(Symbols.ArrowTurnDownRight, tint = theme.secondaryText, size = 12.dp)
                SelectableText(
                    text = message.text,
                    localize = false,
                    style = theme.footnote.copy(color = theme.secondaryText),
                )
            }
        }

        if (isAssistant && (message.reasoning.isNotEmpty() || message.tools.isNotEmpty())) {
            Column {
                if (message.reasoning.isNotEmpty()) ThinkingFold(message = message, isLive = isLive)
                if (message.tools.isNotEmpty()) StepsFold(tools = message.tools, isLive = isLive, textEmpty = message.text.isEmpty())
            }
        }

        // Un diff dans la réponse ou dans une sortie d'outil se lit à part.
        val patch = (message.tools.mapNotNull { it.output } + message.text)
            .lastOrNull { com.onyx.app.ui.rich.looksLikePatch(it) }
        if (patch != null && onOpenPatch != null) {
            val files = remember(patch) { com.onyx.app.ui.rich.patchFiles(patch) }
            Row {
                Row(
                    modifier = Modifier
                        .background(theme.surfaceColor, RoundedCornerShape(50))
                        .onyxClickable(pressedScale = 0.95f) { onOpenPatch(patch) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OnyxIcon(Symbols.Doc, tint = theme.accent, size = 13.dp)
                    BasicText(
                        text = "View change · " + com.onyx.app.ui.rich.patchSummary(files),
                        style = theme.caption.copy(color = theme.textColor),
                        maxLines = 1,
                    )
                }
            }
        }

        // Les sous-agents que cette réponse a lancés: une rangée par tâche, ouverte au tap sur sa
        // transcription. Ils ne se confondent pas avec les outils (`message.subagents`), c'est le
        // gateway qui les a nommés.
        if (onOpenSubagent != null && message.subagents.isNotEmpty()) {
            SubagentRows(subagents = message.subagents, onOpenTranscript = onOpenSubagent)
        }

        todosFrom(message.tools).takeIf { it.isNotEmpty() }?.let { todos ->
            TodoChecklist(items = todos, isLive = isLive)
        }

        // Les fichiers que la réponse a livrés, comme les pastilles.
        val delivered = com.onyx.app.ui.chat.DeliveredFiles.of(
            message.tools.map { it.output } + message.text,
        )
        if (delivered.isNotEmpty() && onOpenDelivered != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                delivered.forEach { file ->
                    Row(
                        modifier = Modifier
                            .background(theme.surfaceColor, RoundedCornerShape(50))
                            .onyxClickable(pressedScale = 0.95f) { onOpenDelivered(file) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OnyxIcon(Symbols.Doc, tint = theme.accent, size = 13.dp)
                        BasicText(
                            text = file.name,
                            localize = false,
                            style = theme.caption.copy(color = theme.textColor),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        message.clarify?.let { request ->
            ClarifyCard(request = request, onRespond = { answers -> onClarify?.invoke(answers) }, speaker = replyName)
        }
        message.secret?.let { request ->
            SecretCard(request = request, onRespond = { value -> onSecret?.invoke(value) }, speaker = replyName)
        }
        if (message.sudoRequestId != null) {
            SudoCard(onRespond = { password -> onSudo?.invoke(password) })
        }

        message.approvals.forEach { approval ->
            ApprovalCard(
                request = approval,
                onChoose = { choice -> onApproval(approval.id, choice) },
            )
        }

        when {
            // Tant que rien n'est écrit, le fil dit tout de même ce qui se passe: l'onde seule
            // laissait l'agent « travailler dans le vide » sans que rien ne le dise.
            isLive && message.text.isEmpty() -> WaitingPulse(flat = flatReply, phase = live)

            isAssistant && message.error == null && message.text.isNotEmpty() -> MarkdownText(
                markdown = message.text,
                isStreaming = isLive,
                modifier = Modifier.padding(
                    horizontal = if (flatReply) 2.dp else 14.dp,
                    vertical = if (flatReply) 0.dp else theme.bubbleVerticalPadding,
                ),
            )

            promptStyled -> PromptLine(
                prefix = promptPrefix.orEmpty(),
                text = message.text,
                color = theme.promptColor,
            )

            message.text.isNotEmpty() || message.attachments.isNotEmpty() -> UserBubble(
                text = message.text,
                attachments = message.attachments,
                onOpenPhotos = onOpenPhotos,
                onOpenFile = onOpenFile,
            )
        }

        // La page du premier lien web, l'interrupteur allumé (`LinkPreviewSlot`), une fois la
        // réponse finie — jamais une note de pilotage ni une erreur.
        LinkPreviewSlot(message = message, isLive = isLive, linkPreviews = linkPreviews)

        message.error?.let { error ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OnyxIcon(Symbols.ExclamationCircle, tint = theme.error, size = 14.dp)
                SelectableText(text = error, localize = false, style = theme.footnote.copy(color = theme.error))
            }
        }

        AnimatedVisibility(
            visible = showActions && isAssistant && !isLive && message.error == null && message.text.isNotEmpty(),
            enter = fadeIn(animationSpec = OnyxMotion.Sites.MessageActions),
            exit = fadeOut(animationSpec = OnyxMotion.Sites.MessageActions),
        ) {
            ActionRow(
                text = message.text,
                speaker = replyName,
                onToast = onToast,
                onSelectText = onSelectText,
                metrics = message.metrics,
                isReading = isReading,
                onReadAloud = onReadAloud,
                onRegenerate = onRegenerate,
            )
        }
    }
}

/** Vrai quand ce message a un corps, une erreur ou une carte: de quoi justifier un nom. */
private fun hasSomethingToShow(message: ChatMessage): Boolean =
    message.text.isNotEmpty() || message.reasoning.isNotEmpty() || message.tools.isNotEmpty() ||
        message.error != null || !message.cards.isNullOrEmpty() || message.approvals.isNotEmpty() ||
        message.clarify != null || message.secret != null || message.sudoRequestId != null

/**
 * En attente du premier token: la forme d'onde de l'app, qui respire — et sous elle, l'état
 * **honnête** du tour: le temps écoulé et la dernière étape connue.
 *
 * Sans cette ligne, un tour dont le serveur n'envoie rien (ou n'envoie que plus tard) ressemble à
 * du travail à vide: une onde, et rien à lire. Un tour qui n'a rien dit depuis un délai anormal
 * le dit ici, plutôt que de tourner indéfiniment en silence.
 */
@Composable
private fun WaitingPulse(flat: Boolean, phase: TurnPhase? = null) {
    val theme = LocalOnyxTheme.current
    val status = phase?.let { turnStatusLine(it) }
    Column(
        modifier = Modifier
            .then(
                if (flat) {
                    Modifier.padding(horizontal = 2.dp, vertical = 4.dp)
                } else {
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .background(theme.assistantBubble ?: theme.surfaceColor, RoundedCornerShape(theme.bubbleRadius))
                },
            ),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        WaveformPulse(color = theme.accent, height = 20f)
        status?.let { line ->
            BasicText(
                text = line.text,
                localize = false,
                style = theme.caption.copy(color = if (line.warning) theme.error else theme.secondaryText),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** La ligne d'état d'un tour, et si elle est un avertissement. */
private data class TurnStatusLine(val text: String, val warning: Boolean)

/**
 * La ligne d'état du tour, recalculée chaque seconde: le temps écoulé avance même quand le
 * serveur se tait, et l'avertissement arrive de lui-même après le délai anormal.
 */
@Composable
private fun turnStatusLine(phase: TurnPhase): TurnStatusLine? {
    val now by produceState(initialValue = System.currentTimeMillis(), key1 = phase) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val text = TurnStatusCopy.line(phase, now) ?: return null
    return TurnStatusLine(text, TurnStatusCopy.isWarning(phase, now))
}

/** Qui parle, et — pendant que la réponse s'écrit — depuis combien de temps. */
@Composable
private fun SpeakerLine(message: ChatMessage, name: String, isLive: Boolean) {
    val theme = LocalOnyxTheme.current
    val elapsed = tickingSeconds(since = message.createdAt, active = isLive)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isLive) LiveMark(color = theme.accent)
        BasicText(text = name, localize = false, style = theme.subheadline.copy(color = theme.textColor, fontWeight = FontWeight.SemiBold))
        elapsed?.let { seconds ->
            ShimmerText(
                text = "is working · ${TurnMetrics.format(seconds.toDouble())}",
                style = theme.subheadline,
                baseColor = theme.secondaryText,
                highlightColor = theme.textColor,
            )
        }
    }
}

/** Préfixe seul en gras, comme `Text("\(Text(prompt).bold)\(message.text)")`. */
internal fun promptLineText(prefix: String, text: String): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.AnnotatedString.Builder().apply {
        append(prefix)
        addStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold), 0, prefix.length)
        append(text)
    }.toAnnotatedString()

/** Vos messages dans un thème CRT: `❯ texte`, à plat, comme une invite de terminal. */
@Composable
private fun PromptLine(prefix: String, text: String, color: Color) {
    val theme = LocalOnyxTheme.current
    Row(modifier = Modifier.fillMaxWidth()) {
        SelectableText(
            text = promptLineText(prefix, text),
            style = theme.messageFont.copy(color = color),
        )
    }
}

/** Votre bulle: ses coins sont ceux du thème, le coin bas-droit rentré. */
@Composable
private fun UserBubble(
    text: String,
    attachments: List<com.onyx.app.data.model.Attachment> = emptyList(),
    onOpenPhotos: ((List<com.onyx.app.data.model.Attachment>, Int) -> Unit)? = null,
    onOpenFile: ((com.onyx.app.data.model.Attachment) -> Unit)? = null,
    onClarify: ((Map<String, String>) -> Unit)? = null,
    onSudo: ((String) -> Unit)? = null,
    onSecret: ((String) -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .background(
                    color = theme.userBubbleColor,
                    shape = RoundedCornerShape(
                        topStart = theme.bubbleRadius,
                        topEnd = theme.bubbleRadius,
                        bottomEnd = minOf(5.dp, theme.bubbleRadius),
                        bottomStart = theme.bubbleRadius,
                    ),
                )
                .padding(horizontal = 14.dp, vertical = theme.bubbleVerticalPadding),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val pictures = attachments.filter { it.mimeType.startsWith("image/") }
                if (pictures.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pictures.take(3).forEachIndexed { index, attachment ->
                            AttachmentThumb(
                                attachment = attachment,
                                onClick = { onOpenPhotos?.invoke(pictures, index) },
                            )
                        }
                        if (pictures.size > 3) {
                            BasicText(
                                text = "+${pictures.size - 3}",
                                style = theme.caption.copy(color = theme.userBubbleText),
                            )
                        }
                    }
                }
                // Une vidéo jointe se montre comme l'image, avec sa première image et un rond de
                // lecture par-dessus: c'est `VideoTile` du modèle. L'appui ouvre le lecteur.
                val videos = attachments.filter {
                    com.onyx.app.ui.chat.FileViewers.isVideo(
                        it.name.orEmpty().ifEmpty { it.uri.substringAfterLast('/') },
                        it.mimeType,
                    )
                }
                if (videos.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        videos.take(2).forEach { attachment ->
                            VideoThumb(
                                attachment = attachment,
                                onClick = { onOpenFile?.invoke(attachment) },
                            )
                        }
                        if (videos.size > 2) {
                            BasicText(
                                text = "+${videos.size - 2}",
                                style = theme.caption.copy(color = theme.userBubbleText),
                            )
                        }
                    }
                }
                if (text.isNotEmpty()) {
                    SelectableText(text = text, localize = false, style = theme.userFont.copy(color = theme.userBubbleText))
                }
                // Un fichier joint n'est pas qu'une image: un PDF, un texte ou une archive se
                // montrent en pastille, et s'ouvrent au tap (le PDF dans le lecteur de l'app).
                val files = attachments.filterNot {
                    it.mimeType.startsWith("image/") || com.onyx.app.ui.chat.FileViewers.isVideo(
                        it.name.orEmpty().ifEmpty { it.uri.substringAfterLast('/') },
                        it.mimeType,
                    )
                }
                if (files.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        files.forEach { attachment ->
                            AttachmentFileChip(
                                attachment = attachment,
                                onClick = { onOpenFile?.invoke(attachment) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** La pastille d'un fichier joint dans une bulle: son symbole, puis son nom. */
@Composable
private fun AttachmentFileChip(
    attachment: com.onyx.app.data.model.Attachment,
    onClick: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val name = attachment.name.orEmpty().ifEmpty { attachment.uri.substringAfterLast('/') }
    Row(
        modifier = Modifier
            .background(theme.userBubbleText.copy(alpha = 0.14f), RoundedCornerShape(50))
            .onyxClickable(pressedScale = 0.95f, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OnyxIcon(
            symbol = when (com.onyx.app.ui.chat.FileViewers.kind(name, attachment.mimeType)) {
                com.onyx.app.ui.chat.FileViewerKind.PDF -> Symbols.DocOnDoc
                com.onyx.app.ui.chat.FileViewerKind.VIDEO -> Symbols.Film
                com.onyx.app.ui.chat.FileViewerKind.PHOTO -> Symbols.Photo
                com.onyx.app.ui.chat.FileViewerKind.EXTERNAL -> Symbols.Doc
            },
            tint = theme.userBubbleText,
            size = 13.dp,
        )
        BasicText(
            text = name,
            localize = false,
            style = theme.caption.copy(color = theme.userBubbleText),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Une vignette d'image jointe, dans la bulle. */
@Composable
private fun AttachmentThumb(
    attachment: com.onyx.app.data.model.Attachment,
    onClick: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        null,
        attachment.uri,
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(android.net.Uri.parse(attachment.uri))?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .onyxClickable(onClick = onClick),
    ) {
        bitmap?.let {
            androidx.compose.foundation.Image(
                bitmap = it,
                contentDescription = attachment.name,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(64.dp),
            )
        }
    }
}

/**
 * La vignette d'une vidéo jointe: sa première image sous un rond de lecture.
 *
 * C'est `VideoTile` du modèle, à la lettre: l'image se lit une fois et reste là (aucune
 * lecture pendant que le fil défile), et quand aucune image ne sort du film, c'est le rond seul
 * sur un fond neutre. L'appui ouvre le lecteur intégré.
 */
@Composable
private fun VideoThumb(
    attachment: com.onyx.app.data.model.Attachment,
    onClick: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val name = attachment.name.orEmpty().ifEmpty { attachment.uri.substringAfterLast('/') }
    // La vignette se fait à 3× la taille affichée, comme le modèle: nette à la densité de
    // l'écran sans garder en mémoire l'image entière du film.
    val poster by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        null,
        attachment.uri,
    ) {
        value = com.onyx.app.ui.chat.VideoPoster.frame(context, android.net.Uri.parse(attachment.uri), 192)
    }

    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .onyxClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        poster?.let {
            androidx.compose.foundation.Image(
                bitmap = it,
                contentDescription = name,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(64.dp),
            )
        } ?: Box(
            modifier = Modifier
                .size(64.dp)
                .background(LocalOnyxTheme.current.userBubbleText.copy(alpha = 0.14f)),
        )
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(Color.Black.copy(alpha = 0.45f), androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            OnyxIcon(
                symbol = Symbols.Play,
                tint = Color.White,
                size = 12.dp,
                contentDescription = OnyxLocalization[com.onyx.app.ui.chat.VideoMeta.accessibilityLabel(name, 0L)],
            )
        }
    }
}

/** Le raisonnement du modèle, replié sous « Thinking » / « Thought for 6 s ». */
@Composable
private fun ThinkingFold(message: ChatMessage, isLive: Boolean) {
    val theme = LocalOnyxTheme.current
    var chosen by remember(message.id) { mutableStateOf<Boolean?>(null) }
    val open = chosen ?: isLive
    val thinking = isLive && message.text.isEmpty()

    Column {
        FoldHeader(open = open, onClick = { chosen = !open }) {
            if (thinking) {
                ShimmerText(
                    text = "Thinking",
                    style = theme.subheadline,
                    baseColor = theme.secondaryText,
                    highlightColor = theme.textColor,
                )
            } else {
                BasicText(text = thoughtLabel(message), style = theme.subheadline.copy(color = theme.secondaryText))
            }
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(
                animationSpec = OnyxMotion.Sites.MessageFoldSize,
                expandFrom = Alignment.Top,
            ) +
                slideInVertically(animationSpec = OnyxMotion.Sites.MessageFoldOffset) { -it } +
                fadeIn(animationSpec = OnyxMotion.Sites.MessageFold),
            exit = shrinkVertically(animationSpec = OnyxMotion.Sites.MessageFoldSize, shrinkTowards = Alignment.Top) +
                slideOutVertically(animationSpec = OnyxMotion.Sites.MessageFoldOffset) { -it } +
                fadeOut(animationSpec = OnyxMotion.Sites.MessageFold),
        ) {
            FoldBody {
                SelectableText(
                    text = if (isLive) tail(message.reasoning, 360) else message.reasoning,
                    localize = false,
                    style = theme.footnote.copy(color = theme.secondaryText),
                )
            }
        }
    }
}

/** « Thought for 6 s »; « Thought » pour les réponses enregistrées avant qu'on garde les temps. */
private fun thoughtLabel(message: ChatMessage): String {
    val started = message.reasoningStartedAt
    val ended = message.reasoningEndedAt
    if (started != null && ended != null) {
        // Une réflexion sous la seconde a quand même pris un moment; « 0 s » se lirait comme rien.
        val seconds = ((maxOf(ended, started + 1000) - started) / 1000).coerceAtLeast(1)
        return "Thought for $seconds s"
    }
    return "Thought"
}

/** Les outils que la réponse a utilisés, repliés de la même façon. */
@Composable
private fun StepsFold(tools: List<ToolActivity>, isLive: Boolean, textEmpty: Boolean) {
    val theme = LocalOnyxTheme.current
    var chosen by remember { mutableStateOf<Boolean?>(null) }
    var openStep by remember { mutableStateOf<String?>(null) }
    val working = isLive && tools.any { it.isRunning }
    val open = chosen ?: (isLive && textEmpty)

    Column {
        FoldHeader(
            open = open,
            label = if (working) "Working · ${toolsSummary(tools, working = true)}"
            else "Used ${toolsSummary(tools, working = false)}",
            onClick = { chosen = !open },
        )
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(
                animationSpec = OnyxMotion.Sites.MessageFoldSize,
                expandFrom = Alignment.Top,
            ) +
                slideInVertically(animationSpec = OnyxMotion.Sites.MessageFoldOffset) { -it } +
                fadeIn(animationSpec = OnyxMotion.Sites.MessageFold),
            exit = shrinkVertically(animationSpec = OnyxMotion.Sites.MessageFoldSize, shrinkTowards = Alignment.Top) +
                slideOutVertically(animationSpec = OnyxMotion.Sites.MessageFoldOffset) { -it } +
                fadeOut(animationSpec = OnyxMotion.Sites.MessageFold),
        ) {
            FoldBody {
                tools.forEach { tool ->
                    val canOpen = tool.args != null || tool.output != null
                    val isOpen = openStep == tool.id
                    val chevronAngle by animateFloatAsState(
                        targetValue = if (isOpen) 90f else 0f,
                        animationSpec = OnyxMotion.Sites.MessageFold,
                        label = "stepChevron",
                    )
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onyxTappable(enabled = canOpen) { openStep = if (isOpen) null else tool.id }
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            StepMark(status = tool.status)
                            Column(modifier = Modifier.weight(1f)) {
                                SelectableText(
                                    text = tool.name.ifBlank { "outil" },
                                    localize = false,
                                    style = theme.subheadline.copy(
                                        color = if (tool.isRunning) theme.textColor else theme.secondaryText,
                                    ),
                                )
                                tool.preview?.takeIf { it.isNotBlank() }?.let { preview ->
                                    SelectableText(
                                        text = preview,
                                        localize = false,
                                        style = theme.caption.copy(color = theme.secondaryText),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (canOpen) {
                                OnyxIcon(
                                    Symbols.ChevronRight,
                                    tint = theme.tertiaryText,
                                    size = 11.dp,
                                    modifier = Modifier.rotate(chevronAngle),
                                )
                            }
                        }
                        AnimatedVisibility(
                            visible = isOpen,
                            enter = expandVertically(
                                animationSpec = OnyxMotion.Sites.MessageFoldSize,
                                expandFrom = Alignment.Top,
                            ) +
                                slideInHorizontally(animationSpec = OnyxMotion.Sites.MessageFoldOffset) { -it } +
                                fadeIn(animationSpec = OnyxMotion.Sites.MessageFold),
                            exit = shrinkVertically(animationSpec = OnyxMotion.Sites.MessageFoldSize, shrinkTowards = Alignment.Top) +
                                slideOutHorizontally(animationSpec = OnyxMotion.Sites.MessageFoldOffset) { -it } +
                                fadeOut(animationSpec = OnyxMotion.Sites.MessageFold),
                        ) {
                            StepDetail(tool)
                        }
                    }
                }
            }
        }
    }
}

/** Un outil, ouvert: ce avec quoi il a été appelé et ce qu'il a rendu. */
@Composable
private fun StepDetail(tool: ToolActivity) {
    val theme = LocalOnyxTheme.current
    Column(
        modifier = Modifier
            .padding(start = 32.dp, bottom = 8.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tool.args?.let { DetailBlock(label = "Input", text = it, lines = 6) }
        when (val output = tool.output) {
            null -> if (tool.isRunning) {
                SelectableText("Still running.", style = theme.caption.copy(color = theme.secondaryText))
            } else {
                SelectableText(
                    "No output was kept for this step.",
                    style = theme.caption.copy(color = theme.secondaryText),
                )
            }

            else -> DetailBlock(label = "Output", text = output, lines = 12)
        }
    }
}

@Composable
private fun DetailBlock(label: String, text: String, lines: Int) {
    val theme = LocalOnyxTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        BasicText(text = OnyxLocalization[label].uppercase(), localize = false, style = theme.caption2.copy(color = theme.secondaryText))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.surfaceColor, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            SelectableText(
                text = head(text, lines),
                localize = false,
                style = theme.caption.copy(color = theme.textColor, fontFamily = FontFamily.Monospace),
                maxLines = lines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** L'état d'un outil: un anneau qui tourne, une coche, ou une croix. */
@Composable
private fun StepMark(status: ToolStatus) {
    val theme = LocalOnyxTheme.current
    val transition = rememberInfiniteTransition(label = "step")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 900, easing = LinearEasing)),
        label = "stepSpin",
    )

    Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
        when (status) {
            ToolStatus.RUNNING -> Canvas(modifier = Modifier.size(16.dp)) {
                val stroke = Stroke(width = 2f, cap = StrokeCap.Round)
                drawArc(
                    color = theme.secondaryText.copy(alpha = 0.3f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset.Zero,
                    size = Size(this.size.width, this.size.height),
                    style = Stroke(width = 2f),
                )
                drawArc(
                    color = theme.secondaryText,
                    startAngle = angle,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset.Zero,
                    size = Size(this.size.width, this.size.height),
                    style = stroke,
                )
            }

            ToolStatus.COMPLETED -> Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(theme.success.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                DrawnCheckmark(color = theme.success)
            }

            ToolStatus.FAILED -> Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(theme.error.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                OnyxIcon(Symbols.Xmark, tint = theme.error, size = 11.dp)
            }
        }
    }
}

/**
 * La coche d'un outil fini, qui se trace (`StepMark`, `CheckShape` + `.trim(from:to:)`,
 * `.easeOut(duration: 0.4).delay(0.05)`). Le chemin est celui , dessiné au trait.
 */
@Composable
private fun DrawnCheckmark(color: androidx.compose.ui.graphics.Color) {
    var drawn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { drawn = true }
    // `StepMark`: `if animated, !reduceMotion { withAnimation(…0,4 + 0,05) } else { drawn = 1 }`
    // — la coche est posée d'un coup, sans tracé.
    val progress = ambientLoop(1f) {
        androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (drawn) 1f else 0f,
            animationSpec = OnyxMotion.Sites.StepCheck,
            label = "stepCheckDraw",
        ).value
    }

    androidx.compose.foundation.Canvas(modifier = Modifier.size(20.dp)) {
        val pad = 5.dp.toPx()
        val w = size.width - 2f * pad
        val h = size.height - 2f * pad
        val ox = pad
        val oy = pad
        val p0 = Offset(ox + w * 0.08f, oy + h * 0.55f)
        val p1 = Offset(ox + w * 0.40f, oy + h * 0.88f)
        val p2 = Offset(ox + w * 0.95f, oy + h * 0.18f)
        val l1 = (p1 - p0).getDistance()
        val l2 = (p2 - p1).getDistance()
        val covered = (l1 + l2) * progress
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(p0.x, p0.y)
            if (covered <= l1) {
                val t = if (l1 <= 0f) 1f else (covered / l1).coerceIn(0f, 1f)
                lineTo(p0.x + (p1.x - p0.x) * t, p0.y + (p1.y - p0.y) * t)
            } else {
                lineTo(p1.x, p1.y)
                val t = if (l2 <= 0f) 1f else ((covered - l1) / l2).coerceIn(0f, 1f)
                lineTo(p1.x + (p2.x - p1.x) * t, p1.y + (p2.y - p1.y) * t)
            }
        }
        drawPath(
            path = path,
            color = color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
    }
}

internal val ReplyActionRowLeading = (-7).dp
internal val ReplyActionGlyphSize = 15.dp

/** `MessageRow.actionRow`: caption2 avec chiffres tabulaires, pas tout le texte en monospace. */
internal fun replyMetricsTextStyle(theme: com.onyx.app.ui.theme.ResolvedTheme): androidx.compose.ui.text.TextStyle =
    theme.caption2.copy(color = theme.tertiaryText, fontFeatureSettings = "tnum")

/** Copier, lire à voix haute, relancer, puis la vitesse de la réponse. */
@Composable
private fun ActionRow(
    text: String,
    /** Le nom de qui parle: l'étiquette du presse-papiers suit le fil (le bot, sinon Onyx). */
    speaker: String,
    onToast: ((String) -> Unit)?,
    onSelectText: ((String) -> Unit)?,
    metrics: TurnMetrics?,
    isReading: Boolean,
    onReadAloud: (() -> Unit)?,
    onRegenerate: (() -> Unit)?,
) {
    val theme = LocalOnyxTheme.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    var fullMetrics by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }

    Row(
        modifier = Modifier.offset(x = ReplyActionRowLeading),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ActionIcon(
            symbol = if (copied) Symbols.Checkmark else Symbols.DocOnDoc,
            label = if (copied) "Copied" else "Copy reply",
            tint = if (copied) theme.success else theme.secondaryText,
        ) {
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(speaker, text)))
            }
            copied = true
            onToast?.invoke("Copied")
        }

        onReadAloud?.let { action ->
            ActionIcon(
                symbol = if (isReading) Symbols.Stop else Symbols.SpeakerWave,
                label = if (isReading) "Stop reading" else "Read aloud",
                tint = theme.secondaryText,
                onClick = action,
            )
        }

        onRegenerate?.let { action ->
            ActionIcon(
                symbol = Symbols.ArrowClockwise,
                label = "Regenerate",
                tint = theme.secondaryText,
                onClick = action,
            )
        }

        ActionIcon(symbol = Symbols.Ellipsis, label = "More", tint = theme.secondaryText) {
            onSelectText?.invoke(text)
        }

        metrics?.takeIf { it.completedAt != null }?.let { value ->
            // `withAnimation(.easeOut(duration: 0.15)) { fullMetrics.toggle }`: les chiffres
            // détaillés remplacent le résumé en fondu.
            AnimatedContent(
                targetState = fullMetrics,
                transitionSpec = {
                    fadeIn(animationSpec = OnyxMotion.Sites.MetricsToggle)
                        .togetherWith(fadeOut(animationSpec = OnyxMotion.Sites.MetricsToggle))
                },
                label = "replyMetrics",
                modifier = Modifier
                    .padding(start = 6.dp)
                    .onyxTappable { fullMetrics = !fullMetrics },
            ) { expanded ->
                BasicText(
                    text = if (expanded) value.summary() else value.compact(),
                    localize = false,
                    style = replyMetricsTextStyle(theme),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ActionIcon(
    @DrawableRes symbol: Int,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .onyxClickable(pressedScale = 0.9f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Le glyphe s'échange en se redimensionnant: `withAnimation(.snappy(duration: 0.2))`
        // autour du changement de `copied` (copie → coche, lecture → arrêt).
        AnimatedContent(
            targetState = symbol,
            transitionSpec = {
                (
                    fadeIn(animationSpec = OnyxMotion.Sites.Snappy200) +
                        scaleIn(animationSpec = OnyxMotion.Sites.Snappy200, initialScale = 0.7f)
                    ).togetherWith(
                    fadeOut(animationSpec = OnyxMotion.Sites.Snappy200) +
                        scaleOut(animationSpec = OnyxMotion.Sites.Snappy200, targetScale = 0.7f),
                )
            },
            label = "actionIcon",
        ) { res ->
            OnyxIcon(symbol = res, tint = tint, size = ReplyActionGlyphSize, contentDescription = label)
        }
    }
}

/** Le corps d'un repli: un filet de 2 pt sur son flanc gauche (`FoldBody`). */
@Composable
private fun FoldBody(content: @Composable () -> Unit) {
    val theme = LocalOnyxTheme.current
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(theme.separator),
        )
        Column(
            modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 4.dp),
        ) {
            content()
        }
    }
}

/** « 3 tools », puis « 3 tools · 4.2 s » une fois tous finis et chronométrés. */
private fun toolsSummary(tools: List<ToolActivity>, working: Boolean): String {
    val count = if (tools.size == 1) "1 tool" else "${tools.size} tools"
    if (working) return count
    val start = tools.mapNotNull { it.startedAt }.minOrNull() ?: return count
    val end = tools.mapNotNull { it.endedAt }.maxOrNull() ?: return count
    if (end <= start) return count
    val seconds = (end - start).toDouble() / 1000.0
    return if (seconds < 10) "$count · ${"%.1f".format(seconds)} s" else "$count · ${seconds.toInt()} s"
}

/** Les premières lignes d'un texte, comme `MessageRow.head`. */
internal fun head(text: String, lines: Int): String =
    text.split("\n").take(lines).joinToString("\n").take(lines * 160)

/** Les derniers caractères d'un texte, en démarrant sur un mot, comme `MessageRow.tail`. */
internal fun tail(text: String, maxCharacters: Int): String {
    if (text.length <= maxCharacters) return text
    val cut = text.takeLast(maxCharacters)
    val space = cut.indexOfFirst { it.isWhitespace() }
    return if (space < 0) "…$cut" else "…" + cut.substring(space + 1)
}

/** Secondes écoulées depuis [since], rafraîchies chaque seconde tant que [active]. */
@Composable
private fun tickingSeconds(since: Long, active: Boolean): Long? = produceState<Long?>(
    initialValue = if (active) 0L else null,
    key1 = since,
    key2 = active,
) {
    if (!active) {
        value = null
        return@produceState
    }
    while (true) {
        value = ((System.currentTimeMillis() - since) / 1000).coerceAtLeast(0)
        delay(1000)
    }
}.value
