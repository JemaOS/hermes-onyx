package com.onyx.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.SubagentActivity
import com.onyx.app.data.model.SubagentStatus
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.chat.components.subagentDetail
import com.onyx.app.ui.chat.components.subagentGoal
import com.onyx.app.ui.chat.components.subagentIcon
import com.onyx.app.ui.chat.components.subagentStateLabel
import com.onyx.app.ui.chat.components.subagentTint
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.LiveMark
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * La transcription d'un sous-agent
 * (`SubagentTranscriptView`, ).
 *
 * Son but en tête, son état, son modèle, et sa conversation — ou ce qui en est lu.
 *
 * **D'où vient la transcription**: le Dashboard seul sait la donner, par `subagent.tail`
 * (`subagent_id`) — la tranche vivante de ce que l'enfant a écrit. L'API de conversation ne la
 * transporte pas: quand le transport ne peut pas la donner, l'écran le dit au lieu de laisser
 * un chargement tourner pour toujours. Le `child_session_id` n'est pas transporté non plus par
 * l'API, et c'est lui qu'il faudrait pour `session.resume`: les deux chemins manquent pour la
 * même raison.
 */
@Composable
fun SubagentTranscriptScreen(
    agent: SubagentActivity,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Lit la transcription vivante (Dashboard); `null` quand le transport ne la porte pas. */
    loadTranscript: (suspend (SubagentActivity) -> SubagentTailView?)? = null,
    /** Oriente le sous-agent; la transcription dit si la consigne a été prise. */
    onSteer: (suspend (String) -> Boolean)? = null,
    /** Arrête le sous-agent; `false` s'il avait déjà fini. */
    onStop: (suspend () -> Boolean)? = null,
    now: Long = System.currentTimeMillis(),
) {
    val theme = LocalOnyxTheme.current
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf<String?>(null) }
    var transcript by remember(agent.id) { mutableStateOf<String?>(null) }
    var loading by remember(agent.id) { mutableStateOf(loadTranscript != null) }
    var unavailable by remember(agent.id) { mutableStateOf(false) }
    val running = agent.status == SubagentStatus.RUNNING

    // La tranche vivante se relit tant que l'enfant travaille: sa conversation grandit pendant
    // qu'on la regarde. Une fois la tâche finie, une seule lecture suffit.
    LaunchedEffect(agent.id, agent.status, loadTranscript) {
        val load = loadTranscript
        if (load == null) {
            loading = false
            unavailable = true
            return@LaunchedEffect
        }
        while (true) {
            val read = runCatching { load(agent) }.getOrNull()
            when {
                read == null -> {
                    unavailable = true
                    loading = false
                    return@LaunchedEffect
                }
                read.available -> {
                    transcript = read.text
                    unavailable = false
                }
                // `available: false` est normal: l'enfant démarre, ou il n'a rien écrit encore.
                else -> unavailable = true
            }
            loading = false
            if (agent.status != SubagentStatus.RUNNING) return@LaunchedEffect
            delay(2_000)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.pageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(
                title = "Subagent",
                onBack = onBack,
                // « Stop » n'apparaît que sur un sous-agent qui tourne **et** que le gateway laisse
                // piloter: sans le canal du Dashboard, l'écran reste en lecture seule, comme quand
                // le tour tourne dans le gateway.
                action = if (onStop != null && running) "Stop" else null,
                onAction = if (onStop != null && running) {
                    {
                        scope.launch {
                            note = if (onStop.invoke()) "Stopping…" else "Subagent already finished"
                        }
                    }
                } else {
                    null
                },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText(
                text = subagentGoal(agent),
                localize = agent.goal.isBlank(),
                style = theme.headline.copy(color = theme.textColor),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (running) {
                    LiveMark(color = theme.accent)
                } else {
                    OnyxIcon(
                        symbol = subagentIcon(agent.status),
                        tint = subagentTint(agent.status, theme),
                        size = 13.dp,
                    )
                }
                BasicText(
                    text = buildString {
                        append(subagentStateLabel(agent.status))
                        // Le détail se traduit **à part**: dans la ligne complète, le motif
                        // « N tools · … » ne s'accroche plus et le compte restait en anglais
                        // (« Terminé · 7 tools · … », vu en capture).
                        if (!running) append(" · ").append(OnyxLocalization[subagentDetail(agent, now)])
                        agent.model?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                        if (agent.taskCount > 1) append(" · task ${agent.taskIndex + 1} of ${agent.taskCount}")
                    },
                    style = theme.caption.copy(color = theme.secondaryText),
                )
            }
        }

        // La zone de contenu prend ce qui reste, et seulement ce qui reste: un `fillMaxSize`
        // ici pousserait la barre de consigne hors de l'écran (vu en capture), et le geste de
        // pilotage n'aurait plus nulle part où se poser.
        Box(modifier = Modifier.weight(1f)) {
            when {
                loading -> CenteredMessage("The subagent is starting.")
                unavailable && transcript.isNullOrBlank() ->
                    CenteredMessage("The transcript isn't carried by this server")
                transcript.isNullOrBlank() -> CenteredMessage("This subagent left no transcript.")
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    SelectionContainer {
                        BasicText(
                            text = transcript.orEmpty(),
                            localize = false,
                            style = theme.footnote.copy(color = theme.textColor, fontFamily = FontFamily.Monospace),
                        )
                    }
                }
            }
        }

        // On peut orienter le sous-agent en cours de route, comme.
        if (onSteer != null && running) {
            var steer by remember { mutableStateOf("") }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.surfaceColor)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(theme.fill, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = steer,
                        onValueChange = { steer = it },
                        textStyle = theme.footnote.copy(color = theme.textColor),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(theme.accent),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (steer.isEmpty()) {
                                BasicText(
                                    text = "Steer the subagent…",
                                    style = theme.footnote.copy(color = theme.tertiaryText),
                                )
                            }
                            inner()
                        },
                    )
                }
                OnyxIcon(
                    symbol = Symbols.SendAfter,
                    tint = if (steer.isBlank()) theme.tertiaryText else theme.accent,
                    size = 18.dp,
                    modifier = Modifier.onyxClickable(enabled = steer.isNotBlank()) {
                        val text = steer
                        steer = ""
                        scope.launch {
                            // La consigne peut être refusée: le gateway le dit, la transcription
                            // le répète — jamais un appui sans issue.
                            note = if (onSteer.invoke(text)) {
                                "Sent to the subagent"
                            } else {
                                "The subagent isn't accepting steering right now"
                            }
                        }
                    },
                )
            }
        }

        note?.let { text ->
            BasicText(
                text = text,
                style = theme.caption.copy(color = theme.secondaryText),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun CenteredMessage(text: String) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = text, style = theme.body.copy(color = theme.secondaryText))
    }
}

/** Ce que le lecteur de transcription a trouvé: la tranche vivante, ou rien. */
data class SubagentTailView(
    val text: String,
    val available: Boolean,
    val truncated: Boolean = false,
)
