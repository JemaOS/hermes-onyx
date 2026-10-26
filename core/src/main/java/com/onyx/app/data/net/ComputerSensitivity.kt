package com.onyx.app.data.net

/**
 * Ce qui, sur cet écran, ne part pas sans un « oui » de l'utilisateur.
 *
 * Deux familles, et rien d'autre:
 *
 * - **une commande**: elle agit sur le PC hors de ce que l'aperçu montre (elle peut écrire des
 * fichiers, installer, supprimer). Elle est toujours soumise à approbation;
 * - **les raccourcis de session**: verrouiller, se déconnecter, changer de console. On ne les
 * déclenche pas d'un doigt qui glissait.
 *
 * Un clic, une frappe ordinaire, un glisser ou la molette, non: ce sont les gestes mêmes de
 * l'aperçu, et demander une confirmation pour chacun viderait l'approbation de son sens.
 *
 * Le pont applique la même règle de son côté: l'app demande, le pont refuse ce qui n'est pas
 * approuvé. Une app modifiée ne peut donc pas contourner l'approbation.
 */
enum class ComputerInput { TAP, DRAG, SCROLL, TEXT, KEY, RUN }

object ComputerSensitivity {

    /** Les raccourcis qui touchent à la session elle-même. */
    val SENSITIVE_KEYS: Set<String> = setOf(
        "ctrl+alt+l", "super+l", "ctrl+alt+delete", "ctrl+alt+backspace", "logoff",
        "ctrl+alt+f1", "ctrl+alt+f2", "ctrl+alt+f3", "ctrl+alt+f4", "ctrl+alt+f5", "ctrl+alt+f6",
    )

    /**
     * Ce que le pont refuse même approuvé: ce n'est pas un aperçu, c'est le disque de quelqu'un.
     * Les mêmes motifs sont gravés des deux côtés, exprès.
     */
    val BLOCKED_COMMANDS: List<Regex> = listOf(
        Regex("""\bmkfs(\.|\s)"""),
        Regex("""\bdd\b[^\n]*\bof=/dev/"""),
        Regex("""\brm\s+-rf\s+/(\s|$)"""),
        Regex("""\bshutdown\b|\breboot\b|\bpoweroff\b"""),
        Regex(""":\(\)\s*\{"""),
    )

    /** Faut-il un « oui » explicite avant d'envoyer cette action ? */
    fun requiresApproval(action: ComputerInput, detail: String): Boolean = when (action) {
        ComputerInput.RUN -> true
        ComputerInput.KEY -> detail.trim().lowercase() in SENSITIVE_KEYS
        else -> false
    }

    /** Une commande que l'app elle-même refuse de proposer — dite avant l'approbation, pas après. */
    fun blocked(command: String): Boolean = BLOCKED_COMMANDS.any { it.containsMatchIn(command) }
}
