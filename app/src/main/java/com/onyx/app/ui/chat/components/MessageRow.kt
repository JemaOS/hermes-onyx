package com.onyx.app.ui.chat.components

import com.onyx.app.localization.OnyxLanguage
import androidx.annotation.DrawableRes
import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
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
import com.onyx.app.ui.design.LocalReduceMotion
import com.onyx.app.ui.design.MotionFamily
import com.onyx.app.ui.design.OnyxSpace
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
 * Une ligne du fil de conversation : port de `MessageRow`.
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
    /** L'état honnête du tour en cours : l'étape connue et les horaires (`ChatUiState.live`). */
    live: TurnPhase? = null,
    modifier: Modifier = Modifier,
    showActions: Boolean = true,
    /** La fiche du premier lien du message (`Réglages → Apparence → Aperçus des liens`). */
    linkPreviews: Boolean = false,
    onRegenerate: (() -> Unit)? = null,
    onReadAloud: (() -> Unit)? = null,
    isReading: Boolean = false,
    /**
     * « Brancher à partir d'ici » : copier ce fil **jusqu'à cette réponse** dans une nouvelle
     * conversation, et l'ouvrir. Le ViewModel demande confirmation avant de copier quoi que ce
     * soit ; `null` là où le geste n'a pas de sens (une question, un tour en cours).
     */
    onBranch: (() -> Unit)? = null,
    /**
     * Restaurer la conversation **à ce message** : tout ce qui suit est défait. Le ViewModel
     * demande confirmation avant de retirer quoi que ce soit.
     */
    onRestore: (() -> Unit)? = null,
    onApproval: (approvalId: String, choice: String) -> Unit = { _, _ -> },
    onOpenDelivered: ((com.onyx.app.data.model.serve.DeliveredFile) -> Unit)? = null,
    highlighted: Boolean = false,
    onToast: ((String) -> Unit)? = null,
    onOpenPatch: ((String) -> Unit)? = null,
    onSelectText: ((String) -> Unit)? = null,
    onOpenPhotos: ((List<com.onyx.app.data.model.Attachment>, Int) -> Unit)? = null,
    onOpenFile: ((com.onyx.app.data.model.Attachment) -> Unit)? = null,
    onOpenSubagent: ((com.onyx.app.data.model.SubagentActivity) -> Unit)? = null,
    /**
     * LOT organisation-fil-competences : arrête un sous-agent encore vivant
     * (`subagent.interrupt`). `null` quand le transport ne porte pas le pilotage — les cartes ne
     * dessinent alors aucun bouton d'arrêt mort.
     */
    onStopSubagent: ((com.onyx.app.data.model.SubagentActivity) -> Unit)? = null,
    onClarify: ((Map<String, String>) -> Unit)? = null,
    onSudo: ((String) -> Unit)? = null,
    onSecret: ((String) -> Unit)? = null,
    /**
     * LOT fil-vivant : vrai quand la personne **remonte lire** le fil (le suivi du bas est
     * détaché). Les puces d'état des cartes **ralentissent** alors — l'état ne continue pas à
     * battre à pleine cadence sous les yeux de qui lit.
     */
    readingUp: Boolean = false,
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
                        .background(theme.accent.copy(alpha = 0.10f), RoundedCornerShape(OnyxSpace.ControlCorner))
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                } else {
                    Modifier
                },
            ),
        horizontalAlignment = if (message.isUser && !promptStyled) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Le nom du bot ne se pose que s'il a quelque chose à coiffer : un message vide coiffé
        // de son nom fait lire le message de l'utilisateur qui suit comme sa réponse (défaut
        // signalé par le propriétaire).
        if (isAssistant && promptPrefix == null && (isLive || hasSomethingToShow(message))) {
            SpeakerLine(message = message, name = replyName, isLive = isLive)
        }

        // Une note de pilotage s'affiche en pastille, jamais en bulle : « Your steer »
        // (`MessageRow:126` — Label orange à 12 %, pied de page, texte secondaire).
        if (message.isSteer) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(theme.warning.copy(alpha = 0.12f))
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
                if (message.tools.isNotEmpty()) StepsFold(tools = message.tools, isLive = isLive, textEmpty = message.text.isEmpty(), readingUp = readingUp)
            }
        }

        // Un diff dans la réponse ou dans une sortie d'outil se lit à part.
        val patch = (message.tools.mapNotNull { it.output } + message.text)
            .lastOrNull { com.onyx.app.ui.rich.looksLikePatch(it) }
        if (patch != null && onOpenPatch != null) {
            com.onyx.app.ui.chat.components.ContextCard(
                // LOT fil-vivant : un diff est une **carte de contexte** comme les autres — même
                // rayon, même filet, même en-tête, état à gauche, action à droite. Son état est
                // celui d'un travail livré : il est là, complet, dès qu'il s'affiche.
                state = patchCardState(remember(patch) { com.onyx.app.ui.rich.patchFiles(patch) }.size),
                title = "View change",
                detail = remember(patch) { com.onyx.app.ui.rich.patchSummary(com.onyx.app.ui.rich.patchFiles(patch)) },
                titleMonospace = false,
                readingUp = readingUp,
                onClick = { onOpenPatch(patch) },
            )
        }

        // Les sous-agents que cette réponse a lancés : une rangée par tâche, ouverte au tap sur sa
        // transcription. Ils ne se confondent pas avec les outils (`message.subagents`), c'est le
        // gateway qui les a nommés.
        if (onOpenSubagent != null && message.subagents.isNotEmpty()) {
            SubagentRows(
                subagents = message.subagents,
                onOpenTranscript = onOpenSubagent,
                onStop = onStopSubagent,
                readingUp = readingUp,
            )
        }

        todosFrom(message.tools).takeIf { it.isNotEmpty() }?.let { todos ->
            TodoChecklist(items = todos, isLive = isLive)
        }

        // Les fichiers que la réponse a livrés, comme les pastilles de la référence.
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
                        OnyxIcon(Symbols.Doc, tint = theme.textColor, size = 13.dp)
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
                readingUp = readingUp,
            )
        }

        when {
            // Tant que rien n'est écrit, le fil dit tout de même ce qui se passe : l'onde seule
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
        // réponse finie — jamais une note de pilotage ni une erreur (`MessageRow:176`).
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
                onRestore = onRestore,
                onBranch = onBranch,
                tokens = message.usage,
            )
        }
    }
}

/** Vrai quand ce message a un corps, une erreur ou une carte : de quoi justifier un nom. */
private fun hasSomethingToShow(message: ChatMessage): Boolean =
    message.text.isNotEmpty() || message.reasoning.isNotEmpty() || message.tools.isNotEmpty() ||
        message.error != null || !message.cards.isNullOrEmpty() || message.approvals.isNotEmpty() ||
        message.clarify != null || message.secret != null || message.sudoRequestId != null

/**
 * En attente du premier token : la forme d'onde de l'app, qui respire — et sous elle, l'état
 * **honnête** du tour : le temps écoulé et la dernière étape connue.
 *
 * Sans cette ligne, un tour dont le serveur n'envoie rien (ou n'envoie que plus tard) ressemble à
 * du travail à vide : une onde, et rien à lire. Un tour qui n'a rien dit depuis un délai anormal
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
 * La ligne d'état du tour, recalculée chaque seconde : le temps écoulé avance même quand le
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

/** Préfixe seul en gras, comme `Text("\(Text(prompt).bold())\(message.text)")`. */
internal fun promptLineText(prefix: String, text: String): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.AnnotatedString.Builder().apply {
        append(prefix)
        addStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold), 0, prefix.length)
        append(text)
    }.toAnnotatedString()

/** Vos messages dans un thème CRT : `❯ texte`, à plat, comme une invite de terminal. */
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

/** Votre bulle : ses coins sont ceux du thème, le coin bas-droit rentré. */
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
                // lecture par-dessus : c'est `VideoTile` de la référence. L'appui ouvre le lecteur.
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
                // Un fichier joint n'est pas qu'une image : un PDF, un texte ou une archive se
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

/** La pastille d'un fichier joint dans une bulle : son symbole, puis son nom. */
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
            maxLines = 2,
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
    val theme = LocalOnyxTheme.current
    // Une vignette de 64 dp : elle se décode **réduite** (`BoundedImages`), jamais en pleine
    // résolution. Décoder l'image entière puis la dessiner à 64 dp faisait lever
    // « Canvas: trying to draw too large(192000000bytes) bitmap » sur le fil principal — le
    // processus mourait, l'app se relançait, et la même bulle le tuait de nouveau.
    var attempted by androidx.compose.runtime.remember(attachment.uri) {
        androidx.compose.runtime.mutableStateOf(false)
    }
    val bitmap by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        null,
        attachment.uri,
    ) {
        value = com.onyx.app.ui.chat.BoundedImages
            .decodeUriOffMain(context, android.net.Uri.parse(attachment.uri), com.onyx.app.ui.chat.BoundedImages.THUMB_PX)
            ?.asImageBitmap()
        attempted = true
    }

    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(OnyxSpace.ControlCorner))
            .background(theme.userBubbleText.copy(alpha = 0.14f))
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
        // Une image qui n'a pas pu être lue garde sa place et **le dit** : une case vide fait
        // croire à une image noire, et le propriétaire ne sait pas quoi faire.
        if (attempted && bitmap == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                OnyxIcon(symbol = Symbols.Photo, tint = theme.userBubbleText.copy(alpha = 0.55f), size = 18.dp)
                BasicText(
                    text = com.onyx.app.localization.OnyxLocalization["Unreadable"],
                    style = theme.caption2.copy(color = theme.userBubbleText.copy(alpha = 0.7f)),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * La vignette d'une vidéo jointe : sa première image sous un rond de lecture.
 *
 * C'est `VideoTile` de la référence, à la lettre : l'image se lit une fois et reste là (aucune
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
    // La vignette se fait à 3× la taille affichée, comme la référence : nette à la densité de
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
            .clip(RoundedCornerShape(OnyxSpace.ControlCorner))
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

/**
 * Le raisonnement du modèle, replié sous une ligne à icône : le cerveau, **son annonce en petites
 * capitales** (« RAISONNEMENT DU MODÈLE »), la durée discrète, puis un chevron. Le détail se lit en
 * retrait derrière un **filet vertical de 2 dp** — le filet des replis de la maison —, posé sur la
 * **surface de la page** : plus de pavé plein qui se détache du fond papier.
 *
 * La ligne dit **ce que c'est**, et si le texte est dans une autre langue que l'app, elle le dit
 * aussi (« brut, non traduit ») : sans cela, un raisonnement en anglais posé dans une interface
 * française se lit comme de l'interface mal traduite.
 *
 * Le repli est **fermé par défaut** (même une réponse courte : il ne s'ouvre pas tout seul) et ne
 * s'ouvre que sur appui. Sous Reduce Motion, l'ouverture est **figée**.
 */
@Composable
private fun ThinkingFold(message: ChatMessage, isLive: Boolean) {
    val theme = LocalOnyxTheme.current
    val reduceMotion = LocalReduceMotion.current
    var chosen by remember(message.id) { mutableStateOf<Boolean?>(null) }
    val open = chosen ?: false
    val thinking = isLive && message.text.isEmpty()
    // **Ce que le bloc doit dire de lui-même** : le raisonnement est dans une autre langue que
    // l'app. Rien à annoncer s'il est déjà dans notre langue.
    val foreign = reasoningLooksForeign(message.reasoning, OnyxLocalization.isFrench())
    // **L'annonce en petites capitales** : 11 sp SemiBold, interlettrage +0,08 em — la métrique du
    // libellé de section de la maison —, à l'encre mesurée ([reasoningInk]).
    val eyebrow = theme.caption2.copy(
        color = reasoningInk(theme),
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.08.em,
    )

    val chevronAngle by animateFloatAsState(
        targetValue = if (open) 90f else 0f,
        animationSpec = OnyxMotion.Sites.MessageFold,
        label = "reasoningChevron",
    )
    // **L'arrivée du raisonnement** est une famille nommée ([MotionFamily.Reasoning]) : la durée
    // paraît en fondu de 200 ms, et le passage de « en train de réfléchir » à « réflexion terminée »
    // est **le même** mouvement. Ce qui vient d'la référence (le repli lui-même, `MessageFold`) garde
    // sa courbe : c'est une autre intention, et la parité des courbes portées ne se négocie pas.
    // Sous « Réduire les animations », la spec vaut `snap()` : la ligne change d'un coup.
    val reasoningSpec = MotionFamily.Reasoning.spec<Float>(reduceMotion)

    Column {
        Row(
            modifier = Modifier
                .onyxClickable(pressedScale = 0.98f, pressedAlpha = 0.7f) { chosen = !open }
                // **Aligné sur la marge du contenu** : plus de retrait horizontal — le cerveau
                // commence au bord du texte du message dont le bloc dépend.
                .padding(vertical = OnyxSpace.Hair),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OnyxSpace.IconLabelGap),
        ) {
            OnyxIcon(symbol = Symbols.Brain, tint = theme.secondaryText, size = 12.dp)
            AnimatedContent(
                targetState = thinking,
                transitionSpec = { fadeIn(animationSpec = reasoningSpec) togetherWith fadeOut(animationSpec = reasoningSpec) },
                label = "reasoningArrival",
            ) { live ->
                if (live) {
                    ShimmerText(
                        text = OnyxLocalization["Model reasoning"].uppercase(),
                        style = eyebrow,
                        baseColor = reasoningInk(theme),
                        highlightColor = theme.textColor,
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(OnyxSpace.IconLabelGap),
                    ) {
                        // **L'annonce** : ce n'est pas un libellé d'interface, c'est le
                        // raisonnement brut du modèle — la ligne le dit en petites capitales.
                        BasicText(
                            text = OnyxLocalization["Model reasoning"].uppercase(),
                            localize = false,
                            style = eyebrow,
                        )
                        // La durée reste (elle sert au propriétaire) mais discrète, et lisible : sa
                        // taille suffit à la mettre en retrait, pas une encre sous le seuil.
                        reasoningSeconds(message)?.let { seconds ->
                            BasicText(
                                text = "$seconds s",
                                localize = false,
                                style = theme.caption.copy(color = reasoningInk(theme)),
                            )
                        }
                    }
                }
            }
            // **La mention qui évite le malentendu.** Un raisonnement dans une autre langue que
            // l'app est **brut** — pas de l'interface mal traduite. Elle paraît même replié, pour
            // que personne ne l'attribue à l'app.
            if (foreign) {
                BasicText(
                    text = "· " + OnyxLocalization["raw, untranslated"].uppercase(),
                    localize = false,
                    style = eyebrow,
                )
            }
            OnyxIcon(
                symbol = Symbols.ChevronRight,
                tint = theme.secondaryText,
                size = 11.dp,
                modifier = Modifier.rotate(chevronAngle),
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = if (reduceMotion) {
                EnterTransition.None
            } else {
                expandVertically(
                    animationSpec = OnyxMotion.Sites.MessageFoldSize,
                    expandFrom = Alignment.Top,
                ) +
                    slideInVertically(animationSpec = OnyxMotion.Sites.MessageFoldOffset) { -it } +
                    fadeIn(animationSpec = OnyxMotion.Sites.MessageFold)
            },
            exit = if (reduceMotion) {
                ExitTransition.None
            } else {
                shrinkVertically(animationSpec = OnyxMotion.Sites.MessageFoldSize, shrinkTowards = Alignment.Top) +
                    slideOutVertically(animationSpec = OnyxMotion.Sites.MessageFoldOffset) { -it } +
                    fadeOut(animationSpec = OnyxMotion.Sites.MessageFold)
            },
        ) {
            ReasoningDetail(message = message, isLive = isLive)
        }
    }
}

/**
 * Le détail d'un raisonnement : **un filet vertical de 2 dp à gauche** — le filet des replis de la
 * maison (`FoldBody`) — et le texte **en retrait**, posé sur la **surface de la page**. Pas d'aplat
 * plein, pas de bordure lourde, pas de monospace (il est réservé aux identifiants) : la réflexion
 * est un contenu **secondaire**, elle se lit, elle ne crie pas.
 *
 * **La longueur est tenue** : au-delà de six lignes le bloc se tronque, et son **pied** porte
 * « Voir tout » ; déplié, il porte « Réduire ». Il y a donc toujours un moyen de refermer **depuis
 * le bas du bloc** — jamais un mur de dix écrans, jamais un texte sans fin.
 *
 * Le bloc est **à la marge du contenu** : il n'ajoute aucun retrait de carte qui le détacherait du
 * message dont il dépend.
 */
@Composable
private fun ReasoningDetail(message: ChatMessage, isLive: Boolean) {
    val theme = LocalOnyxTheme.current
    var expanded by remember(message.id) { mutableStateOf(false) }
    val full = if (isLive) tail(message.reasoning, 360) else message.reasoning
    val long = reasoningIsLong(full)
    // **Le corps secondaire** : la méta de l'échelle, à l'interligne confortable — c'est une page
    // à lire, pas un compteur.
    val body = theme.footnote.copy(
        color = reasoningInk(theme),
        lineHeight = theme.footnote.fontSize * ReasoningBlock.LineHeightRatio,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = OnyxSpace.Hair),
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // **Le filet** : un repère de 2 dp au ton de filet de la maison, pas une bordure de
            // carte — il rattache le bloc au message, il ne l'encadre pas.
            Box(
                modifier = Modifier
                    .width(ReasoningBlock.RailWidth)
                    .fillMaxHeight()
                    .background(theme.separator),
            )
            Column(modifier = Modifier.padding(start = ReasoningBlock.TextIndent)) {
                SelectableText(
                    text = full,
                    localize = false,
                    style = body,
                    maxLines =
                        if (long && !expanded) ReasoningBlock.CollapsedLines else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                )
                if (long) {
                    ReasoningLengthControl(expanded = expanded, onToggle = { expanded = !expanded })
                }
            }
        }
    }
}

/**
 * Le **pied du bloc** : « Voir tout » quand il est tronqué, « Réduire » quand il est déplié. La
 * cible est celle du pouce ([OnyxSpace.TouchTarget], 48 dp), l'encre est **mesurée** contre la page
 * ([reasoningInk]), et le chevron dit le sens du geste — jamais un mot sans contrôle.
 */
@Composable
private fun ReasoningLengthControl(expanded: Boolean, onToggle: () -> Unit) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = OnyxSpace.TouchTarget)
            .onyxClickable(pressedAlpha = 0.6f, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OnyxSpace.IconLabelGap),
    ) {
        BasicText(
            text = OnyxLocalization[if (expanded) "Show less" else "See all"],
            style = theme.footnote.copy(color = reasoningInk(theme)),
        )
        OnyxIcon(
            symbol = if (expanded) Symbols.ChevronUp else Symbols.ChevronDown,
            tint = reasoningInk(theme),
            size = 11.dp,
        )
    }
}

/**
 * L'encre lisible du raisonnement et de son libellé : **mesurée** contre le fond réel (la page).
 * La teinte secondaire du thème donne 3,45:1 en clair — sous le seuil de 4,5 d'un texte. 0,70 de
 * l'encre principale donne **8,5:1** en clair et **10:1** en sombre. La mise en retrait se fait par
 * la **taille**, pas par une encre illisible ; les icônes, elles, gardent la teinte secondaire
 * (3,45:1 clair / 6,33:1 sombre, au-dessus du seuil de 3 d'une icône).
 */
internal fun reasoningInk(theme: com.onyx.app.ui.theme.ResolvedTheme): Color =
    theme.textColor.copy(alpha = 0.70f)

/** Les secondes de réflexion, ou `null` pour une réponse enregistrée avant qu'on garde les temps. */
internal fun reasoningSeconds(message: ChatMessage): Long? {
    val started = message.reasoningStartedAt ?: return null
    val ended = message.reasoningEndedAt ?: return null
    // Une réflexion sous la seconde a quand même pris un moment ; « 0 s » se lirait comme rien.
    return ((maxOf(ended, started + 1000) - started) / 1000).coerceAtLeast(1)
}

/** Les outils que la réponse a utilisés, repliés de la même façon. */
@Composable
private fun StepsFold(tools: List<ToolActivity>, isLive: Boolean, textEmpty: Boolean, readingUp: Boolean = false) {
    val theme = LocalOnyxTheme.current
    var chosen by remember { mutableStateOf<Boolean?>(null) }
    // LOT outils-detail : l'appel dont la **feuille de détail** est ouverte, gardé par son
    // identifiant — jamais une position dans la liste (voir `toolCallSelection`).
    var detailOf by remember { mutableStateOf<String?>(null) }
    val working = isLive && tools.any { it.isRunning }
    val open = chosen ?: (isLive && textEmpty)

    Column {
        // LOT organisation-fil-competences : la puce repliée de la référence — deux icônes de
        // genre d'outil, puis « Ran 3 tools · 1 s » (et « Working · 3 tools » tant que ça tourne).
        FoldHeader(
            open = open,
            onClick = { chosen = !open },
        ) {
            RanToolsChipLabel(tools = tools, working = working)
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
                tools.forEach { tool ->
                    val canOpen = tool.args != null || tool.output != null
                    val rowLabel = toolRowLabel(tool)
                    // **LOT fil-vivant : chaque appel d'outil est une carte de contexte**
                    // ([ContextCard]) — même rayon, même filet, même en-tête, état à gauche, action
                    // à droite, exactement comme un sous-agent ou une approbation. Sa puce dit son
                    // **vrai** état ([toolCardState] : l'anneau qui tourne, la coche tracée,
                    // la croix), et le repli est celui de la maison.
                    //
                    // Le repli garde son geste d'origine : la **rangée** ouvre la feuille du détail
                    // (48 dp de haut, le même contrat que le lot « outils-detail »), le **chevron**
                    // déplie l'appel sur place.
                    ContextCard(
                        state = toolCardState(tool.status),
                        title = rowLabel.text,
                        titleMonospace = rowLabel.monospace,
                        identifier = rowLabel.id,
                        detail = tool.preview?.takeIf { it.isNotBlank() },
                        trailing = toolElapsedSeconds(tool)?.let { ranToolsDuration(it) },
                        minHeight = ToolCallRowMinTouch,
                        leading = { StepMark(status = tool.status) },
                        readingUp = readingUp,
                        onClick = { detailOf = tool.id },
                        foldBody = if (canOpen) {
                            { StepDetail(tool) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
        // LOT outils-detail : la feuille du gabarit maison, ouverte sur **l'appel touché**.
        // `toolCallSelection` le choisit par son identifiant : deux `terminal` de la même réponse
        // ne se confondent pas, et la feuille ne montre jamais « le premier » ni « le dernier ».
        toolCallSelection(tools, detailOf)?.let { picked ->
            ToolCallDetailSheet(tool = picked, onDismiss = { detailOf = null })
        }
    }
}

/** Un outil, ouvert : ce avec quoi il a été appelé et ce qu'il a rendu. */
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
                .background(theme.surfaceColor, RoundedCornerShape(OnyxSpace.ControlCorner))
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

/** L'état d'un outil : un anneau qui tourne, une coche, ou une croix. */
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
 * `.easeOut(duration: 0.4).delay(0.05)`). Le chemin est celui de la référence, dessiné au trait.
 */
@Composable
private fun DrawnCheckmark(color: androidx.compose.ui.graphics.Color) {
    var drawn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { drawn = true }
    // `StepMark` : `if animated, !reduceMotion { withAnimation(…0,4 + 0,05) } else { drawn = 1 }`
    // (`MessageRow:620-624`) — la coche est posée d'un coup, sans tracé.
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

/**
 * Le retrait de la rangée : il aligne le **premier glyphe** sur le bord du texte de la réponse.
 * La case fait 44 dp et le glyphe 20 dp, donc 12 dp d'encart de chaque côté — d'où un retrait de
 * −12 (mesuré, pas à l'œil).
 */
internal val ReplyActionRowLeading = (-12).dp

/** La taille du glyphe, **identique pour les quatre**. */
internal val ReplyActionGlyphSize = 20.dp

/** La cible tactile de chaque action : 44 dp (guide d'accessibilité). */
internal val ReplyActionTouchTarget = 44.dp

/** L'espacement, **égal**, entre deux cases (et donc entre deux glyphes). */
internal val ReplyActionSpacing = 2.dp

/**
 * Les actions de la rangée, dans l'ordre de la référence : **brancher d'ici**, restaurer,
 * régénérer, lire (ou arrêter), copier.
 *
 * La référence n'a pas de « restaurer » (c'est le geste de la maison) ; elle ouvre la rangée par la
 * *bifurcation*. Celle-ci est donc la première à gauche, et les quatre actions d'avant gardent
 * leur ordre et leurs cases.
 */
internal enum class ReplyAction { Branch, Restore, Regenerate, Read, Copy }

/** Une case de la rangée : son glyphe, son libellé, et si elle est active. */
internal data class ReplyActionSlot(
    val action: ReplyAction,
    @DrawableRes val symbol: Int,
    val label: String,
    val enabled: Boolean,
    /** La copie réussie teinte sa coche en vert, comme la référence. */
    val tintIsSuccess: Boolean = false,
)

/**
 * Les cases de la rangée, dans l'ordre : **brancher d'ici**, restaurer, régénérer, lire (ou
 * arrêter), copier.
 *
 * Fonction **pure** (donc testable) : les cinq cases portent la **même** taille de glyphe, et une
 * action indisponible garde **sa** case plutôt que de disparaître — la rangée ne bouge donc jamais.
 * L'état de la lecture (enceinte → carré) et celui de la copie (feuilles → coche) changent le glyphe
 * **dans** la même case.
 */
internal fun replyActionSlots(
    copied: Boolean,
    isReading: Boolean,
    canBranch: Boolean,
    canRestore: Boolean,
    canRegenerate: Boolean,
    canRead: Boolean,
): List<ReplyActionSlot> = listOf(
    // La **première à gauche**, comme la référence : la bifurcation ouvre la rangée.
    ReplyActionSlot(ReplyAction.Branch, Symbols.Branch, "Branch from here", canBranch),
    ReplyActionSlot(ReplyAction.Restore, Symbols.ArrowCounterclockwise, "Restore to here", canRestore),
    ReplyActionSlot(ReplyAction.Regenerate, Symbols.ArrowClockwise, "Regenerate", canRegenerate),
    ReplyActionSlot(
        ReplyAction.Read,
        if (isReading) Symbols.Stop else Symbols.SpeakerWave,
        if (isReading) "Stop reading" else "Read aloud",
        canRead,
    ),
    ReplyActionSlot(
        ReplyAction.Copy,
        if (copied) Symbols.Checkmark else Symbols.DocOnDoc,
        if (copied) "Copied" else "Copy reply",
        enabled = true,
        tintIsSuccess = copied,
    ),
)

/** `MessageRow.actionRow` : caption2 avec chiffres tabulaires, pas tout le texte en monospace. */
internal fun replyMetricsTextStyle(theme: com.onyx.app.ui.theme.ResolvedTheme): androidx.compose.ui.text.TextStyle =
    theme.caption2.copy(color = theme.tertiaryText, fontFeatureSettings = "tnum")

/**
 * Restaurer, régénérer, lire à voix haute, copier — puis la vitesse de la réponse, à la fin de la
 * rangée et **discrète** (elle ne déplace pas les quatre glyphes).
 */
@Composable
private fun ActionRow(
    text: String,
    /** Le nom de qui parle : l'étiquette du presse-papiers suit le fil (le bot, sinon Onyx). */
    speaker: String,
    onToast: ((String) -> Unit)?,
    onSelectText: ((String) -> Unit)?,
    metrics: TurnMetrics?,
    isReading: Boolean,
    onReadAloud: (() -> Unit)?,
    onRegenerate: (() -> Unit)?,
    onRestore: (() -> Unit)?,
    /**
     * « Brancher à partir d'ici » : la copie du fil jusqu'à cette réponse, dans une nouvelle
     * conversation. `null` quand l'écran ne le propose pas — la case reste, grisée, et n'ouvre
     * alors la confirmation de personne.
     */
    onBranch: (() -> Unit)? = null,
    /**
     * LOT organisation-fil-competences : les jetons du tour, « 18,4k ↓ · 612 ↑ », à droite du pied
     * — la même paire que la référence. `null` quand le serveur n'a rien compté.
     */
    tokens: com.onyx.app.data.model.TokenUsage? = null,
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

    val slots = replyActionSlots(
        copied = copied,
        isReading = isReading,
        canBranch = onBranch != null,
        canRestore = onRestore != null,
        canRegenerate = onRegenerate != null,
        canRead = onReadAloud != null,
    )

    Row(
        modifier = Modifier.offset(x = ReplyActionRowLeading),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReplyActionSpacing),
    ) {
        slots.forEach { slot ->
            ActionIcon(
                symbol = slot.symbol,
                label = slot.label,
                tint = when {
                    !slot.enabled -> theme.tertiaryText
                    slot.tintIsSuccess -> theme.success
                    else -> theme.secondaryText
                },
                enabled = slot.enabled,
                // « Select text » reste joignable (il a perdu son icône « … » au profit des quatre
                // actions de la référence) : l'appui long sur la copie ouvre le texte sélectionnable.
                onLongPress = if (slot.action == ReplyAction.Copy && onSelectText != null) {
                    { onSelectText.invoke(text) }
                } else {
                    null
                },
            ) {
                when (slot.action) {
                    ReplyAction.Branch -> onBranch?.invoke()
                    ReplyAction.Restore -> onRestore?.invoke()
                    ReplyAction.Regenerate -> onRegenerate?.invoke()
                    ReplyAction.Read -> onReadAloud?.invoke()
                    ReplyAction.Copy -> {
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(speaker, text)))
                        }
                        copied = true
                        onToast?.invoke("Copied")
                    }
                }
            }
        }

        // LOT organisation-fil-competences : les jetons du pied de réponse, à droite — « 18,4k ↓ ·
        // 612 ↑ », entrée puis sortie. Rien quand le serveur n'a rien compté.
        replyTokenPair(tokens)?.let { pair ->
            BasicText(
                text = pair,
                localize = false,
                style = replyMetricsTextStyle(theme),
                maxLines = 1,
                modifier = Modifier.padding(start = 6.dp),
            )
        }

        metrics?.takeIf { it.completedAt != null }?.let { value ->
            // `withAnimation(.easeOut(duration: 0.15)) { fullMetrics.toggle() }` : les chiffres
            // détaillés remplacent le résumé en fondu. Discret, à la fin de la rangée.
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

/**
 * Une case de la rangée : même côté (44 dp) pour les quatre, glyphe centré, et le changement d'état
 * (copie → coche, lecture → arrêt) échange le dessin **dans** la case sans en bouger le cadre.
 */
@Composable
private fun ActionIcon(
    @DrawableRes symbol: Int,
    label: String,
    tint: Color,
    enabled: Boolean = true,
    onLongPress: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(ReplyActionTouchTarget)
            // L'encre des cases est choisie (secondaire au repos, tertiaire quand l'action manque) :
            // `disabledAlpha = 1f` pour qu'une couche d'opacité ne l'écrase pas.
            .then(
                if (onLongPress == null) {
                    Modifier.onyxClickable(
                        enabled = enabled,
                        pressedScale = 0.9f,
                        disabledAlpha = 1f,
                        onClick = onClick,
                    )
                } else {
                    Modifier.onyxTappable(enabled = enabled, onLongPress = onLongPress, onClick = onClick)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Le glyphe s'échange en se redimensionnant : `withAnimation(.snappy(duration: 0.2))`
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

/** Le corps d'un repli : un filet de 2 pt sur son flanc gauche (`FoldBody`). */
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
    return if (seconds < 10) "$count · ${String.format(OnyxLanguage.preferred, "%.1f", seconds)} s" else "$count · ${seconds.toInt()} s"
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
