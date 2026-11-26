package com.onyx.app.data.model

import com.onyx.app.localization.OnyxLanguage
import com.onyx.app.localization.OnyxLocalization
import java.text.Normalizer
import java.util.Locale

/**
 * L'état d'une session de code, **tel qu'il a été observé sur un tour réel**.
 *
 * Aucune de ces valeurs n'est posée d'avance : une session naît [RUNNING] parce qu'un tour vient
 * d'être lancé pour de bon, et ne passe à [READY] ou [FAILED] que sur l'issue **réellement**
 * rapportée par le tour (fin normale, erreur du transport). Le serveur Hermes n'expose **aucune**
 * ressource « session de code » avec un statut : ce que l'écran montre est donc l'état du tour
 * Hermes que la session a démarré, jamais un statut inventé.
 */
enum class CodeStatus(val key: String) {
    /** Un tour travaille en ce moment. */
    RUNNING("running"),

    /** Le tour s'est terminé normalement. */
    READY("ready"),

    /** Le tour a échoué : l'erreur du transport est affichée telle quelle. */
    FAILED("failed"),

    /** Le tour attend une réponse de la personne (question, approbation). */
    NEEDS_YOU("needs_you"),
    ;

    companion object {
        fun fromKey(raw: String?): CodeStatus? = entries.firstOrNull { it.key == raw }
    }
}

/**
 * Les quatre filtres de l'écran Code, dans l'ordre de la rangée :
 * Tout · En cours · À toi · Prêt.
 */
enum class CodeFilter(val key: String) {
    ALL("all"),
    RUNNING("running"),
    NEEDS_YOU("needs_you"),
    READY("ready"),
    ;

    /**
     * Le filtre retient-il cet état ?
     *
     * Un échec ([CodeStatus.FAILED]) n'a pas de filtre à lui : il reste visible dans « Tout »
     * (le compter ailleurs supposerait un cinquième bouton que la référence n'a pas).
     */
    fun accepts(status: CodeStatus): Boolean = when (this) {
        ALL -> true
        RUNNING -> status == CodeStatus.RUNNING
        NEEDS_YOU -> status == CodeStatus.NEEDS_YOU
        READY -> status == CodeStatus.READY
    }
}

/**
 * Une session de code telle que l'app la tient.
 *
 * Le serveur a le **dépôt** (`/api/git/worktrees`), ses **branches** (`/api/git/branches`) et ses
 * **sessions** ; il n'a pas de notion de « session de code ». La fiche est donc **locale** — le
 * titre, la branche dérivée du titre, le dossier choisi — et son **statut vient du tour réel**
 * qu'elle a lancé. [local] est vrai par construction : la carte le dit à l'écran.
 */
data class CodeSession(
    /** L'identifiant de la conversation locale qui porte le tour. */
    val id: String,
    val title: String,
    /** `code/<slug>` dérivé du titre, ou celle choisie à la main à la modification. */
    val branch: String,
    /** Le dépôt choisi au compositeur. */
    val repoPath: String? = null,
    /** La copie de travail (worktree) choisie, ou créée par le serveur. */
    val worktreePath: String? = null,
    val createdAtMillis: Long,
    val status: CodeStatus = CodeStatus.RUNNING,
    val statusAtMillis: Long = createdAtMillis,
    /** La session Hermes réelle, quand le transport l'a créée. */
    val sessionKey: String? = null,
    /**
     * La **demande** telle qu'elle a été envoyée. C'est ce que « Relancer » rejoue ; les fiches
     * écrites avant que la demande ne soit gardée n'en ont pas, et la relance retombe alors sur le
     * titre — jamais sur rien.
     */
    val prompt: String? = null,
    /** Vrai quand la fiche n'existe que sur ce téléphone. */
    val local: Boolean = true,
) {
    /** Ce que la carte montre sous le titre : `code/<slug>`. */
    val branchLabel: String get() = branch
}

/**
 * La fiche **renommée** : le **titre seul** change.
 *
 * La branche n'est pas retouchée : c'est celle d'une **vraie** branche du dépôt (`code/<slug>`),
 * la renommer ici ne la renommerait pas chez git et l'écran mentirait. Un titre vide ne remplace
 * rien — l'ancien reste.
 */
fun CodeSession.renamed(title: String): CodeSession =
    copy(title = title.trim().ifEmpty { this.title })

/**
 * La fiche **modifiée à la main** : titre, branche, demande, dépôt et copie de travail.
 *
 * Un champ laissé vide garde sa valeur précédente (un titre ou une branche vides ne veulent rien
 * dire) ; la demande, elle, peut être effacée — elle n'est alors simplement plus gardée.
 */
fun CodeSession.edited(
    title: String = this.title,
    branch: String = this.branch,
    prompt: String? = this.prompt,
    repoPath: String? = this.repoPath,
    worktreePath: String? = this.worktreePath,
): CodeSession = copy(
    title = title.trim().ifEmpty { this.title },
    branch = branch.trim().ifEmpty { this.branch },
    prompt = prompt?.trim()?.takeIf { it.isNotEmpty() },
    repoPath = repoPath?.takeIf { it.isNotBlank() },
    worktreePath = worktreePath?.takeIf { it.isNotBlank() },
)

/**
 * La fiche d'une **relance** : une session **neuve** — identifiant neuf, même dépôt, même copie de
 * travail, même branche, même demande —, en cours parce qu'un tour vient d'être lancé pour elle.
 *
 * L'identifiant est celui de la **nouvelle** conversation Hermes : la précédente n'est ni reprise
 * ni touchée, elle reste dans l'historique.
 */
fun CodeSession.relaunched(id: String, atMillis: Long): CodeSession = copy(
    id = id,
    createdAtMillis = atMillis,
    status = CodeStatus.RUNNING,
    statusAtMillis = atMillis,
    sessionKey = null,
)

/**
 * Le libellé d'un filtre, suivi de son compteur : « Tout 2 », « En cours 0 ».
 *
 * Le **nom** est traduit seul, puis le compteur s'ajoute : la chaîne entière n'est pas une clé du
 * dictionnaire, et l'assembler d'abord laissait l'anglais à l'écran (pire : une tournure d'un autre
 * écran reconnaît « Running … » et rend « Exécution de 0 »).
 *
 * [showCounts] les laisse tomber quand la rangée n'a plus la place de les porter sans qu'une puce
 * soit coupée : le nom du filtre, lui, reste **entier**.
 */
fun codeFilterLabel(
    filter: CodeFilter,
    count: Int,
    locale: Locale = OnyxLanguage.preferred,
    showCounts: Boolean = true,
): String {
    val name = when (filter) {
        CodeFilter.ALL -> "Everything"
        CodeFilter.RUNNING -> "Running"
        CodeFilter.NEEDS_YOU -> "Yours"
        CodeFilter.READY -> "Ready"
    }
    val label = OnyxLocalization.translate(name, locale)
    return if (showCounts) label + " " + count else label
}

/** Le slug d'un titre : minuscules, sans accents, mots joints par des tirets. */
fun codeSlug(title: String, max: Int = 40): String {
    val flat = Normalizer.normalize(title, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase(Locale.ROOT)
    val words = flat.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
    val slug = words.joinToString("-").take(max).trim('-')
    return slug.ifEmpty { "session" }
}

/** La branche d'une session de code : `code/<slug du titre>`. */
fun codeBranch(title: String): String = "code/" + codeSlug(title)

/** Combien de sessions chaque filtre retient — le compteur affiché sur la pastille. */
fun codeFilterCounts(sessions: List<CodeSession>): Map<CodeFilter, Int> =
    CodeFilter.entries.associateWith { filter -> sessions.count { filter.accepts(it.status) } }

/** Les sessions que le filtre montre, la plus récente d'abord. */
fun filteredCodeSessions(sessions: List<CodeSession>, filter: CodeFilter): List<CodeSession> =
    sessions.filter { filter.accepts(it.status) }.sortedByDescending { it.createdAtMillis }

/**
 * Le temps écoulé, en une unité courte : « 3 s », « 2 min », « 1 h », « 4 j ».
 *
 * La quantité seule — la tournure (« il y a … » / « … ago ») est posée par la traduction, jamais
 * par cette fonction : un nombre et une date se formatent **avec** leur locale.
 */
fun codeElapsed(sinceMillis: Long, nowMillis: Long): String {
    val seconds = ((nowMillis - sinceMillis).coerceAtLeast(0L) + 500L) / 1000L
    return when {
        seconds < 60L -> "$seconds s"
        seconds < 3600L -> "${(seconds + 30L) / 60L} min"
        seconds < 86_400L -> "${(seconds + 1800L) / 3600L} h"
        else -> "${(seconds + 43_200L) / 86_400L} j"
    }
}
