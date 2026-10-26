package com.onyx.app.notifications

/**
 * Le texte des notifications Onyx, en clairs, pour pouvoir le tester sans Android.
 *
 * Les chaînes rendues sont les clés anglaises que [com.onyx.app.localization.OnyxLocalization]
 * traduit (identité en anglais, gabarits pour les parties variables). C'est le même contrat que le
 * reste de l'app: aucun libellé écrit en dur dans un écran ou dans une notification.
 */
object NotificationCopy {

    // —— Approbations ————————————————————————————————————————————————

    /** Le titre d'une demande d'approbation (`ApprovalNotifier`). */
    const val APPROVAL_TITLE = "Approval needed"

    /** Les deux réponses, mot pour mot comme les boutons du gateway. */
    const val APPROVE = "Approve"
    const val DENY = "Deny"

    /** Ce que dit la notification une fois la réponse passée. */
    fun resolvedTitle(approved: Boolean): String = if (approved) "Approved" else "Denied"

    /** Une réponse qui n'a pas pu partir: la commande attend encore. */
    const val UNANSWERED_TITLE = "Couldn't answer"
    const val UNANSWERED_TEXT = "Open Onyx to answer from the conversation."

    /** Ce que dit la notification d'une question une fois la réponse partie. */
    const val ANSWERED_TITLE = "Answered"

    /** Le texte de la notification après réponse: la commande, ou l'échec et sa marche à suivre. */
    fun answeredText(command: String, failed: Boolean): String =
        if (failed) UNANSWERED_TEXT else command.take(120)

    // —— Questions de l'agent ————————————————————————————————————————

    /** Les trois choses qui arrêtent un agent: une question, sudo, un secret. */
    enum class QuestionKind { CLARIFY, SUDO, SECRET }

    /** L'action « Répondre », commune aux trois. */
    const val REPLY = "Reply"

    /** L'action qui ouvre l'app quand la saisie en ligne n'est pas possible. */
    const val OPEN = "Open"

    /**
     * Le titre d'une question, selon ce que l'agent demande.
     *
     * [name] est le nom de qui pose la question: le bot dans son fil, sinon « Onyx ». Le titre
     * nomme alors le bot — « writer has a question » — ce que le français rend par son motif.
     */
    fun questionTitle(kind: QuestionKind, name: String? = null): String = when (kind) {
        QuestionKind.CLARIFY -> name?.takeIf { it.isNotBlank() && it != "Onyx" }?.let { "$it has a question" }
            ?: "Onyx has a question"
        QuestionKind.SUDO -> "A password is needed"
        QuestionKind.SECRET -> "A secret is needed"
    }

    /** Le corps d'une question: la question elle-même, ou la consigne du secret. */
    fun questionText(prompt: String): String = prompt.take(160)

    /**
     * Une réponse en ligne est-elle possible ?
     *
     * `sudo` et `secret` s'adressent à la session ouverte: sans son identifiant, la réponse ne
     * peut pas partir depuis la notification, et l'action ouvre l'app. Une série de questions
     * (`batch` de plus d'une question) ne peut pas non plus tenir dans un seul champ de saisie.
     */
    fun canAnswerInline(kind: QuestionKind, hasSession: Boolean, questionCount: Int): Boolean = when (kind) {
        QuestionKind.CLARIFY -> questionCount <= 1
        QuestionKind.SUDO, QuestionKind.SECRET -> hasSession
    }

    // —— Tour en cours (Live Activity) ————————————————————————————————

    /** La phrase du tour, avec l'outil en cours et la durée écoulée. */
    fun turnPhase(prefillPercent: Int?, toolName: String?, elapsedSeconds: Long?): String {
        val base = when {
            !toolName.isNullOrBlank() -> "Running $toolName"
            prefillPercent != null -> "$prefillPercent% read"
            else -> "Answering…"
        }
        val seconds = elapsedSeconds?.takeIf { it > 0 } ?: return base
        return "$base · ${elapsed(seconds)}"
    }

    /** Une durée courte et lisible: « 12s », « 1m 05s », « 1h 02m ». */
    fun elapsed(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0)
        return when {
            safe < 60 -> "${safe}s"
            safe < 3600 -> "${safe / 60}m ${(safe % 60).toString().padStart(2, '0')}s"
            else -> "${safe / 3600}h ${((safe % 3600) / 60).toString().padStart(2, '0')}m"
        }
    }
}
