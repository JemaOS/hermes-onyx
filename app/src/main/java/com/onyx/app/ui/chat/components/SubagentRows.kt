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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.SubagentActivity
import com.onyx.app.data.model.SubagentStatus
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.LiveMark
import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.design.SelectableText
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * Les sous-agents d'une réponse (`SubagentRows`, Echo/Views/SubagentRows:5).
 *
 * Une rangée par tâche déléguée, décalée par sa profondeur : son but, son état, sa place dans une
 * série, une ligne de détail vivante (outil en cours, ou durée et résumé une fois fini), son pied
 * « modèle · durée · N outils », et un tap qui ouvre sa transcription quand le gateway a dit où
 * elle est.
 *
 * Plusieurs tâches d'une même délégation sont **regroupées** sous une carte de groupe (« Travail
 * avec sous-agents · 1 sur 3 · 0:03 », les points colorés, l'arrêt du groupe, le chevron), comme la
 * référence : le fil dit d'abord combien travaillent, puis chaque tâche quand on ouvre.
 *
 * [onStop] arrête vraiment l'enfant (`subagent.interrupt`, un appel RPC par enfant) ; `null` quand
 * le transport ne porte pas le pilotage — la carte ne dessine alors **aucun** bouton mort.
 *
 * **Pas d'horloge ici** : le fil ne doit rien relancer tout seul. Un compteur qui s'incrémente
 * changerait la hauteur du message et ré-attacherait le défilement sous le doigt de l'utilisateur
 * (défaut déjà signalé : « ça force à remonter »). Le temps écoulé se lit sur la liste des tâches,
 * qui est un écran à part et n'a pas de défilement suivi à casser.
 */
@Composable
fun SubagentRows(
    subagents: List<SubagentActivity>,
    onOpenTranscript: (SubagentActivity) -> Unit,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
    /** Arrête un enfant encore vivant ; `null` quand le transport ne porte pas `subagent.interrupt`. */
    onStop: ((SubagentActivity) -> Unit)? = null,
    /** Vrai quand la personne remonte lire le fil : les puces d'état **ralentissent**. */
    readingUp: Boolean = false,
) {
    if (subagents.isEmpty()) return
    val theme = LocalOnyxTheme.current

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Une seule tâche n'a pas de groupe à annoncer : la rangée dit tout, et le chevron du
        // groupe n'ouvrirait rien.
        val grouped = subagents.size > 1
        var open by remember(subagents.map { it.id }) { mutableStateOf(subagents.any { it.isLive }) }
        if (grouped) {
            SubagentGroupCard(
                agents = subagents,
                expanded = open,
                onToggle = { open = !open },
                onStopAll = onStop?.let { stop -> { subagents.filter { it.isLive }.forEach(stop) } },
            )
        }
        if (!grouped || open) {
            subagents.forEach { agent ->
                SubagentRow(
                    agent = agent,
                    now = now,
                    onOpenTranscript = onOpenTranscript,
                    onStop = onStop,
                    indent = if (grouped) 0 else agent.depth.coerceIn(0, 3) * 14,
                    accent = theme.accent,
                    readingUp = readingUp,
                )
            }
        }
    }
}

@Composable
private fun SubagentRow(
    agent: SubagentActivity,
    now: Long,
    onOpenTranscript: (SubagentActivity) -> Unit,
    onStop: ((SubagentActivity) -> Unit)?,
    indent: Int,
    accent: Color,
    readingUp: Boolean = false,
) {
    val theme = LocalOnyxTheme.current
    val tint = subagentTint(agent.status, theme)
    val openable = agent.hasTranscript
    val summary = agent.summary
    // Le pied ne dit le compte d'outils que comparé à la ligne de détail : une tâche finie la porte
    // déjà là, une tâche vivante ne l'a nulle part ailleurs.
    val meta = subagentMetaLine(agent, now)

    // **LOT fil-vivant : la rangée d'un enfant est une carte de contexte** ([ContextCard]) — même
    // rayon, même filet, même en-tête, état à gauche, action à droite, comme un appel d'outil ou
    // une approbation. Sa puce dit son vrai état ([subagentCardState]) : l'enfant respire, vibre ou
    // s'arrête avec la même signature que le reste du fil.
    //
    // Le repli suit ce que la rangée a **vraiment** à ouvrir : une tâche qui a une transcription
    // s'ouvre au tap (le chevron reste absent — il n'y a rien à replier que le pied, déjà là) ;
    // une tâche sans transcription replie, elle, sa note et son résumé.
    ContextCard(
        state = subagentCardState(agent.status),
        title = subagentGoal(agent),
        detail = subagentDetail(agent, now),
        identifier = if (agent.taskCount > 1) "${agent.taskIndex + 1}/${agent.taskCount}" else null,
        modifier = Modifier.padding(start = indent.dp),
        readingUp = readingUp,
        onClick = if (openable) ({ onOpenTranscript(agent) }) else null,
        action = {
            // L'arrêt n'existe que pour un enfant **vivant** et un transport qui sait le piloter :
            // un bouton qui ne peut rien arrêter serait un bouton mort.
            if (agent.isLive) onStop?.let { stop -> SubagentStopButton(onClick = { stop(agent) }) }
            if (openable) {
                OnyxIcon(symbol = Symbols.ChevronRight, tint = theme.tertiaryText, size = 10.dp)
            }
        },
        body = {
            meta.takeIf { it.isNotBlank() }?.let { line ->
                BasicText(
                    text = line,
                    localize = false,
                    style = theme.caption2.copy(color = theme.tertiaryText, fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        foldBody = if (openable) null else {
            {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    summary?.takeIf { it.isNotBlank() }?.let { text ->
                        SelectableText(
                            text = text.take(600),
                            localize = false,
                            style = theme.caption.copy(color = theme.textColor),
                        )
                    }
                    // Sans session d'enfant, il n'y a rien à ouvrir : la rangée dépliée le dit,
                    // plutôt que de laisser croire qu'un chevron manquant est un oubli.
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
        },
    )
}
