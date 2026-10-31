package com.onyx.app.ui.chat

import com.onyx.app.ui.design.OnyxSpace

import com.onyx.app.ui.design.onyxPage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.SubagentActivity
import com.onyx.app.data.model.SubagentStatus
import com.onyx.app.ui.chat.components.subagentGoal
import com.onyx.app.ui.chat.components.subagentIcon
import com.onyx.app.ui.chat.components.subagentListDetail
import com.onyx.app.ui.chat.components.subagentTint
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.LiveMark
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.delay

/**
 * Les tâches lancées par l'agent, ouvertes depuis la conversation.
 *
 * C'est l'écran qui répond à « je veux voir travailler les sous-agents » : une ligne par tâche
 * déléguée, son état, **le temps écoulé qui avance**, et sa dernière action ; un tap ouvre sa
 * transcription.
 *
 * D'où viennent les lignes — et ce que chaque transport donne :
 *  - **Dashboard (`hermes serve`)** : les trames `subagent.*` (but, profondeur, dernier outil,
 *    tour d'outils, état) et le roster `subagent.list` (date de départ, `last_tool`) ; le temps
 *    écoulé est donc celui du serveur, et la transcription existe ;
 *  - **API de conversation (`/v1`)** : rien de tout cela n'est transporté — seul l'appel
 *    `delegate_task` est visible, avec le but dans son libellé. La tâche apparaît en
 *    [SubagentStatus.DISPATCHED] (« Lancé ») : le but est connu, l'état réel ne l'est pas, et
 *    l'écran ne propose ni transcription ni arrêt, parce que le transport ne les porte pas.
 */
@Composable
fun SubagentListScreen(
    subagents: List<SubagentActivity>,
    onOpen: (SubagentActivity) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val rows = remember(subagents) { subagents.sortedWith(subagentOrder) }
    val anyLive = rows.any { it.isLive }

    // Le temps écoulé avance tout seul, tant qu'il y a quelque chose à mesurer. Un seul
    // incrément par seconde, et rien qui bouge quand tout est fini.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(anyLive) {
        while (anyLive) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .onyxPage(theme),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(
                title = "Tasks",
                onBack = onBack,
            )
        }

        if (rows.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = "No tasks yet. Ask the agent to delegate, or open a conversation where it did.",
                    style = theme.body.copy(color = theme.secondaryText),
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp,
                vertical = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(items = rows, key = { it.id }) { agent ->
                TaskRow(agent = agent, now = now, onOpen = onOpen)
            }
        }
    }
}

@Composable
private fun TaskRow(
    agent: SubagentActivity,
    now: Long,
    onOpen: (SubagentActivity) -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val tint = subagentTint(agent.status, theme)
    val openable = agent.hasTranscript

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = 0.08f), RoundedCornerShape(OnyxSpace.ControlCorner))
            .onyxClickable { if (openable) onOpen(agent) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (agent.status == SubagentStatus.RUNNING) {
                LiveMark(color = theme.accent)
            } else {
                OnyxIcon(symbol = subagentIcon(agent.status), tint = tint, size = 13.dp)
            }
            BasicText(
                text = subagentGoal(agent),
                localize = agent.goal.isBlank(),
                style = theme.footnote.copy(color = theme.textColor, fontWeight = FontWeight.Medium),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (agent.taskCount > 1) {
                BasicText(
                    text = "${agent.taskIndex + 1}/${agent.taskCount}",
                    localize = false,
                    style = theme.caption2.copy(color = theme.secondaryText),
                )
            }
            if (openable) {
                OnyxIcon(symbol = Symbols.ChevronRight, tint = theme.tertiaryText, size = 10.dp)
            }
        }
        BasicText(
            text = subagentListDetail(agent, now),
            style = theme.caption.copy(color = theme.secondaryText),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * L'ordre de la liste : ce qui travaille d'abord (les plus récents en tête), puis ce qui est fini,
 * du plus récent au plus ancien. Une tâche lancée et sans nouvelle reste **en haut** : c'est
 * elle qu'on venait voir.
 */
internal val subagentOrder: Comparator<SubagentActivity> =
    compareByDescending<SubagentActivity> { it.isLive }
        .thenByDescending { it.startedAt }
        .thenByDescending { it.status == SubagentStatus.DISPATCHED }
