package com.onyx.app.data.model.serve

import com.onyx.app.localization.OnyxLanguage
import java.util.Locale

/**
 * Les formes rendues par le Dashboard Hermes.
 *
 * Chaque modèle ne garde que ce que les écrans affichent — les routes rendent davantage, et
 * changent d'une version du serveur à l'autre.
 */

/** L'état du gateway, tel que l'écran « Gateway » le montre. */
data class GatewayStatus(
    val version: String = "",
    val releaseDate: String = "",
    /** Le gateway répond-il ? */
    val gatewayOk: Boolean = false,
    val exitReason: String? = null,
    val turnsRunning: Int = 0,
    val host: String? = null,
    val system: String? = null,
    val upFor: String? = null,
    val memory: String? = null,
    val disk: String? = null,
    val troubles: List<GatewayTrouble> = emptyList(),
    val connections: List<GatewayConnection> = emptyList(),
) {
    val versionLine: String
        get() = listOf(version, releaseDate).filter { it.isNotEmpty() }.joinToString(" · ")

    val gatewayLabel: String get() = if (gatewayOk) "Connected" else "Not running"
}

/** Un problème signalé par le gateway (un sous-système en panne, par exemple). */
data class GatewayTrouble(val id: String, val status: String) {
    val label: String get() = "${id.replaceFirstChar { it.uppercase() }}: $status"
}

/** Ce à quoi le gateway est connecté (API, WhatsApp, iMessage, Home Assistant…). */
data class GatewayConnection(val title: String, val state: String, val error: String? = null) {
    val isConnected: Boolean get() = state.equals("connected", ignoreCase = true)
}

/** Un serveur MCP : sa forme, sa cible, et s'il est activé dans la configuration d'Hermes. */
data class McpServer(
    val name: String,
    /** La cible telle qu'on la montre : l'adresse, ou la commande locale. */
    val target: String = "",
    val enabled: Boolean = false,
    /** Renseigné quand c'est un plugin qui l'apporte : l'interrupteur suit le plugin. */
    val plugin: String? = null,
    /** `http` (une adresse) ou `stdio` (une commande locale) ; `unknown` si le serveur ne le dit pas. */
    val transport: String = "unknown",
    val url: String? = null,
    val command: String? = null,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val auth: String? = null,
    /** Les outils auxquels ce serveur est restreint, tels que le serveur les donne ; `null` = tous. */
    val toolSelection: List<String>? = null,
    /** `config` (écrit dans la configuration) ou `plugin`. */
    val source: String = "config",
) {
    /** Vrai pour un serveur qui se lance par une commande locale, faux pour une adresse. */
    val isLocalCommand: Boolean get() = transport == "stdio" || (transport == "unknown" && command != null)

    /** La ligne de commande complète (« python3 -m serveur --port 1 »), ou l'adresse. */
    val commandLine: String get() = listOfNotNull(command?.takeIf { it.isNotBlank() }, *args.toTypedArray())
        .joinToString(" ")
        .ifBlank { target }
}

/** Un outil qu'un serveur MCP a exposé, tel que la route de test le rend. */
data class McpTool(val name: String, val description: String = "")

/** Le résultat d'un « Test » sur un serveur MCP. */
data class McpProbe(
    val ok: Boolean,
    val summary: String,
    val error: String? = null,
    /** Les outils que le serveur a listés pendant le test. Vide quand le test a échoué. */
    val tools: List<McpTool> = emptyList(),
    val prompts: Int = 0,
    val resources: Int = 0,
    /** Le temps qu'a pris le test, mesuré sur l'appareil (le serveur ne le rend pas). */
    val latencyMillis: Long? = null,
)

/** Ce qu'un « Ajouter une source MCP » envoie, avant l'envoi. */
data class McpServerDraft(
    val name: String = "",
    val transport: String = "stdio",
    val command: String = "",
    val args: String = "",
    val env: String = "",
    val url: String = "",
    val auth: String = "none",
    val bearerToken: String = "",
) {
    /** Les arguments, découpés sur les espaces ; les guillemets ne sont pas interprétés. */
    val argumentList: List<String> get() = args.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    /**
     * Les variables d'environnement, une par ligne (« CLE=valeur »).
     *
     * Une ligne sans « = » n'est pas une variable : elle est refusée plutôt que posée telle quelle,
     * parce qu'un nom sans valeur ferait échouer le serveur sans dire pourquoi.
     */
    val environment: List<Pair<String, String>>
        get() = env.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
            val (key, value) = line.split("=", limit = 2).let { it.first() to it.getOrElse(1) { "" } }
            key.trim() to value.trim()
        }

    /** La ou les lignes d'environnement qui n'ont pas de « = ». */
    val malformedEnvironment: List<String>
        get() = env.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.contains('=') }
}

/** Une ligne de journal. */
data class ServeLogLine(val text: String, val level: LogLevel)

enum class LogLevel { PLAIN, WARNING, ERROR }

/** Une entrée de dossier, comme la liste « Fichiers » la montre. */
data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val modifiedAt: String? = null,
    /** Un fichier que l'éditeur de texte peut ouvrir. */
    val isText: Boolean = false,
) {
    val displaySize: String
        get() = when {
            isDirectory -> ""
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> "${String.format(OnyxLanguage.preferred, "%.1f", size / 1024.0 / 1024.0)} MB"
        }
}

/** Une tâche planifiée du gateway. */
data class CronJob(
    val id: String,
    val title: String = "",
    val prompt: String = "",
    val schedule: String = "",
    val botName: String? = null,
    val state: String = "",
    val paused: Boolean = false,
    val nextRun: String? = null,
    val lastRun: String? = null,
    val lastError: String? = null,
    val deliverTo: String? = null,
    val model: String? = null,
    val nextRunAt: Long? = null,
    val lastRunAt: Long? = null,
    val lastStatus: String? = null,
) {
    /** Le nom sans le préfixe `[bot:profil]` posé par Hermes Desktop. */
    val untaggedTitle: String
        get() {
            if (!title.startsWith("[bot:")) return title
            val end = title.indexOf(']')
            return if (end >= 0) title.substring(end + 1).trim() else title
        }

    val displayTitle: String get() = untaggedTitle.ifEmpty { prompt.lineSequence().firstOrNull().orEmpty().take(60) }

    /** Une routine dont la réponse revient dans la conversation d'un bot. */
    val isBotRoutine: Boolean get() = deliverTo?.startsWith("bot-chat") == true

    /** Le préfixe de Hermes Desktop la rend visible dans Onyx comme dans Desktop. */
    val sharedWithDesktop: Boolean
        get() = title.startsWith("[bot:") && title.indexOf(']') > 5

    /** L'heure est passée depuis plus de cinq minutes sans exécution : le serveur ne l'a pas tirée. */
    fun isMissed(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val next = nextRunAt ?: return false
        if (state != "scheduled" || nowMillis - next <= 300_000L) return false
        return (lastRunAt ?: Long.MIN_VALUE) < next
    }
}

/** « in 2 hours » / « 5 minutes ago », dans la langue demandée ou celle du système. */
fun relativeLabel(
    timestampMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
    locale: Locale = OnyxLanguage.preferred,
): String {
    val french = locale.language.equals("fr", ignoreCase = true)
    val delta = timestampMillis - nowMillis
    val distance = kotlin.math.abs(delta)
    if (distance < 45_000L) return if (french) "maintenant" else "now"
    val minutes = (distance + 30_000L) / 60_000L
    val (amount, unit) = when {
        minutes < 60 -> minutes to "minute"
        minutes < 24 * 60 -> (minutes + 30) / 60 to "hour"
        else -> (minutes + 12 * 60) / (24 * 60) to "day"
    }
    if (french) {
        val noun = when (unit) {
            "minute" -> if (amount == 1L) "minute" else "minutes"
            "hour" -> if (amount == 1L) "heure" else "heures"
            else -> if (amount == 1L) "jour" else "jours"
        }
        return if (delta >= 0) "dans $amount $noun" else "il y a $amount $noun"
    }
    val noun = if (amount == 1L) unit else unit + "s"
    return if (delta >= 0) "in $amount $noun" else "$amount $noun ago"
}

/** Une exécution passée d'une tâche. */
data class CronRun(val at: String, val state: String, val output: String? = null)

/** Où une tâche livre son résultat. */
data class DeliveryTarget(val id: String, val name: String, val available: Boolean = true)

/** Une carte du kanban. */
data class KanbanCard(
    val id: String,
    val title: String,
    val status: String = KanbanStatus.TRIAGE.id,
    val priority: Int = 0,
    val summary: String? = null,
    val body: String? = null,
    val assignee: String? = null,
    val createdAt: String? = null,
    /**
     * `created_at` du plugin, en millisecondes : la rangée en montre l'âge (« 1 hr. ago »), comme
     * le `relativeLabel` de la rangée de la référence. `createdAt` garde la valeur brute (le détail).
     */
    val createdAtMillis: Long? = null,
    val completedAt: String? = null,
    val commentCount: Int = 0,
    val failed: Boolean = false,
    val result: String? = null,
    val lastFailure: String? = null,
    val comments: List<KanbanComment> = emptyList(),
)

data class KanbanComment(val id: String, val author: String, val body: String, val at: String? = null)

/** Les colonnes du tableau, dans l'ordre, avec leur libellé et leur icône (comme référence). */
enum class KanbanStatus(val id: String, val label: String) {
    TRIAGE("triage", "Triage"),
    TODO("todo", "To do"),
    SCHEDULED("scheduled", "Scheduled"),
    READY("ready", "Ready"),
    RUNNING("running", "Running"),
    BLOCKED("blocked", "Blocked"),
    REVIEW("review", "Review"),
    DONE("done", "Done"),
    ARCHIVED("archived", "Archived"),
    ;

    /** Ce que le déplacement demande, comme le dialogue de la référence. */
    val movePrompt: String?
        get() = when (this) {
            SCHEDULED -> "Why is it deferred?"
            BLOCKED -> "What is it waiting on?"
            REVIEW -> "What should the reviewer look at?"
            DONE -> "What was the outcome?"
            else -> null
        }

    companion object {
        fun fromId(raw: String?): KanbanStatus =
            entries.firstOrNull { it.id == raw?.lowercase() } ?: TRIAGE
    }
}

/** Un fichier livré par l'agent, que la conversation propose d'ouvrir. */
data class DeliveredFile(val name: String, val path: String)
