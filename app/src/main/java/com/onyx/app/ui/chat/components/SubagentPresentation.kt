package com.onyx.app.ui.chat.components

import androidx.compose.ui.graphics.Color
import com.onyx.app.data.model.SubagentActivity
import com.onyx.app.data.model.SubagentStatus
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.theme.ResolvedTheme
import java.util.Locale

/**
 * Ce qu'une ligne de sous-agent dit, mis en commun par le fil et la liste des tâches.
 *
 * Miroir de `SubagentRows.detail(_:)` / `label(_:)` / `icon(_:)` / `tint(_:)`
 * (Echo/Views/SubagentRows:77) : les deux écrans montrent la même chose, ils ne la
 * reformulent pas chacun à sa façon.
 */

/** Le but de la tâche, ou le mot de repli quand le gateway n'en a pas donné. */
fun subagentGoal(agent: SubagentActivity): String =
    agent.goal.takeIf { it.isNotBlank() } ?: "Delegated task"

/** L'état, en un mot : « En cours », « Lancé », « Terminé », « Échec ». */
fun subagentStateLabel(status: SubagentStatus): String = when (status) {
    SubagentStatus.RUNNING -> "Running"
    SubagentStatus.DISPATCHED -> "Dispatched"
    SubagentStatus.COMPLETED -> "Finished"
    SubagentStatus.FAILED -> "Failed"
}

/** L'icône de l'état (`person.2.wave.2` / `clock.arrow.2.circlepath` / les deux coches). */
fun subagentIcon(status: SubagentStatus): Int = when (status) {
    SubagentStatus.RUNNING -> Symbols.Person2
    SubagentStatus.DISPATCHED -> Symbols.Clock
    SubagentStatus.COMPLETED -> Symbols.CheckmarkCircle
    SubagentStatus.FAILED -> Symbols.XmarkCircle
}

/** La teinte de l'état. */
fun subagentTint(status: SubagentStatus, theme: ResolvedTheme): Color = when (status) {
    SubagentStatus.RUNNING -> theme.accent
    SubagentStatus.DISPATCHED -> theme.secondaryText
    SubagentStatus.COMPLETED -> theme.success
    SubagentStatus.FAILED -> theme.error
}

/**
 * Une durée en secondes, comme la référence : « 12 s » sous la minute, « 1,5 min » au-delà.
 *
 * Les chiffres sont séparés (`monospacedDigit` côté écran) pour que la ligne ne danse pas quand
 * le temps avance d'une seconde à l'autre.
 */
fun subagentDuration(seconds: Double): String =
    if (seconds < 60) String.format(Locale.US, "%.0f s", seconds)
    else String.format(Locale.US, "%.1f min", seconds / 60)

/**
 * La ligne de détail sous le but.
 *
 * Le la référence met le temps écoulé, l'outil en cours et le résumé de fin sur cette seule ligne ; on
 * fait pareil, et les motifs de localisation du dictionnaire reprennent « N tools · … ».
 */
fun subagentDetail(agent: SubagentActivity, now: Long): String {
    val tools = agent.toolCount
    return when (agent.status) {
        SubagentStatus.RUNNING -> buildList {
            if (tools > 0) add(if (tools == 1) "1 tool" else "$tools tools")
            agent.lastTool?.takeIf { it.isNotBlank() }?.let { add(it) }
        }.joinToString(" · ").ifEmpty { "Working…" }

        // On sait seulement que la tâche est partie : l'API de conversation ne dit rien de plus.
        SubagentStatus.DISPATCHED -> "Running in the background; results arrive in a later turn"

        SubagentStatus.COMPLETED, SubagentStatus.FAILED -> buildList {
            if (tools > 0) add(if (tools == 1) "1 tool" else "$tools tools")
            add(subagentDuration(agent.elapsedSeconds(now)))
            agent.summary?.takeIf { it.isNotBlank() }?.let { add(it.take(140)) }
        }.joinToString(" · ")
    }
}

/** Vrai si cette tâche a lancé au moins un outil : la liste s'en sert pour trier. */
fun SubagentActivity.hasWorked(): Boolean = toolCount > 0 || !lastTool.isNullOrBlank()

/**
 * La ligne de détail de la **liste des tâches** : la même chose que [subagentDetail], mais le
 * temps écoulé passe en tête et **avance**, vivant ou fini.
 *
 * C'est le seul endroit où il compte : dans le fil, un compteur qui s'incrémente changerait la
 * hauteur du message et ré-attacherait le défilement (défaut déjà signalé).
 */
fun subagentListDetail(agent: SubagentActivity, now: Long): String = when (agent.status) {
    // Vivante : la durée d'abord, et c'est celle qui avance (le serveur n'en donne pas encore).
    SubagentStatus.RUNNING, SubagentStatus.DISPATCHED ->
        "${subagentDuration(agent.elapsedSeconds(now))} · ${subagentDetail(agent, now)}"
    // Finie : [subagentDetail] porte déjà la durée retenue par le serveur — la préfixer la dirait
    // deux fois (« 23 s · 4 outils · 23 s · … », vu en capture).
    SubagentStatus.COMPLETED, SubagentStatus.FAILED -> subagentDetail(agent, now)
}
