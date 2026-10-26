package com.onyx.app.ui.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.SubagentActivity
import com.onyx.app.data.model.SubagentStatus
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.LiveMark
import com.onyx.app.ui.design.SelectableText
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * Les sous-agents d'une réponse (`SubagentRows`, ).
 *
 * Une rangée par tâche déléguée, décalée par sa profondeur: son but, son état, sa place dans une
 * série, une ligne de détail vivante (outil en cours, ou durée et résumé une fois fini) — et un
 * tap qui ouvre sa transcription quand le gateway a dit où elle est.
 *
 * **Pas d'horloge ici**: le fil ne doit rien relancer tout seul. Un compteur qui s'incrémente
 * changerait la hauteur du message et ré-attacherait le défilement sous le doigt de l'utilisateur
 * (défaut déjà signalé: « ça force à remonter »). Le temps écoulé se lit sur la liste des tâches,
 * qui est un écran à part et n'a pas de défilement suivi à casser.
 */
@Composable
fun SubagentRows(
    subagents: List<SubagentActivity>,
    onOpenTranscript: (SubagentActivity) -> Unit,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
) {
    if (subagents.isEmpty()) return
    val theme = LocalOnyxTheme.current

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        subagents.forEach { agent ->
            SubagentRow(
                agent = agent,
                now = now,
                onOpenTranscript = onOpenTranscript,
                indent = agent.depth.coerceIn(0, 3) * 14,
                accent = theme.accent,
            )
        }
    }
}

@Composable
private fun SubagentRow(
    agent: SubagentActivity,
    now: Long,
    onOpenTranscript: (SubagentActivity) -> Unit,
    indent: Int,
    accent: Color,
) {
    val theme = LocalOnyxTheme.current
    val tint = subagentTint(agent.status, theme)
    var expanded by remember(agent.id) { mutableStateOf(false) }
    val openable = agent.hasTranscript
    val summary = agent.summary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent.dp)
            .background(color = tint.copy(alpha = 0.08f), shape = RoundedCornerShape(10.dp))
            .onyxClickable { if (openable) onOpenTranscript(agent) else expanded = !expanded }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (agent.status == SubagentStatus.RUNNING) {
                LiveMark(color = accent)
            } else {
                OnyxIcon(symbol = subagentIcon(agent.status), tint = tint, size = 12.dp)
            }
            BasicText(
                text = subagentGoal(agent),
                localize = agent.goal.isBlank(),
                style = theme.footnote.copy(color = theme.textColor, fontWeight = FontWeight.Medium),
                maxLines = if (expanded) Int.MAX_VALUE else 2,
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
            text = subagentDetail(agent, now),
            style = theme.caption.copy(color = theme.secondaryText),
            maxLines = if (expanded) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Ce que l'enfant a rendu: le résumé que le gateway donne à la fin.
        AnimatedVisibility(visible = expanded && !summary.isNullOrBlank()) {
            SelectableText(
                text = summary.orEmpty().take(600),
                localize = false,
                style = theme.caption.copy(color = theme.textColor),
            )
        }
        // Sans session d'enfant, il n'y a rien à ouvrir: la rangée dépliée le dit, plutôt que de
        // laisser croire qu'un chevron manquant est un oubli.
        AnimatedVisibility(visible = expanded && !openable) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OnyxIcon(symbol = Symbols.WifiExclamation, tint = theme.tertiaryText, size = 10.dp)
                BasicText(
                    text = "This transport doesn't give the subagent's transcript",
                    style = theme.caption.copy(color = theme.tertiaryText),
                )
            }
        }
    }
}
