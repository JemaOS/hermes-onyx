package com.onyx.app.ui.chat.components

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.ToolActivity
import com.onyx.app.ui.chat.DeliveredFiles

/**
 * **Ce qu'une rangée déduit de son contenu — calculé une fois par contenu.**
 *
 * Quatre analyses parcouraient le texte de la réponse à **chaque** composition de la ligne :
 *
 *  - la recherche d'un diff : `looksLikePatch` sur chaque sortie d'outil **et** sur le texte ;
 *  - le découpage du diff : `patchFiles`, **appelé deux fois** pour la même valeur, puis
 *    `patchSummary` ;
 *  - les tâches à cocher : `todosFrom`, un décodage JSON par composition ;
 *  - les fichiers livrés : `DeliveredFiles.of`, une expression régulière sur ~12 000 caractères
 *    par outil.
 *
 * Sur une réponse de quarante outils, c'est un demi-mégaoctet relu à chaque image pendant qu'on
 * fait défiler le fil. Ces quatre résultats ne dépendent que du contenu : ils sont donc calculés
 * **une fois**, et la rangée les reçoit tout faits.
 *
 * Pur — aucune dépendance à Compose, donc mesurable sans appareil.
 */
internal data class MessageDerived(
    /** Le diff que la réponse montre, s'il y en a un. */
    val patch: String?,
    /** Le nombre de fichiers du diff (l'état de la carte). */
    val patchFileCount: Int,
    /** La ligne de résumé du diff (« 3 fichiers, +42 −7 »). */
    val patchSummary: String?,
    /** Les tâches de la dernière liste `todo` vue dans les sorties d'outils. */
    val todos: List<TodoItem>,
    /** Les fichiers que la réponse a livrés. */
    val delivered: List<com.onyx.app.data.model.serve.DeliveredFile>,
) {
    companion object {

        /**
         * Calcule une fois ce que la rangée déduit de [message].
         *
         * [includeText] à `false` écarte la **réponse** de la recherche de diff. Une réponse qui
         * s'écrit change de texte à chaque jeton ; la relire pour y chercher un diff à chaque
         * publication, c'est un balayage du texte entier répété sur une chaîne qui grandit —
         * quadratique sur la durée du tour, pour une carte qui ne peut de toute façon apparaître
         * qu'une fois la réponse finie. Les **sorties d'outils**, elles, restent lues : elles ne
         * changent qu'à la fin d'un outil, et c'est d'elles que viennent le diff, les tâches et les
         * fichiers livrés pendant le travail.
         */
        fun of(message: ChatMessage, includeText: Boolean = true): MessageDerived {
            // Un diff dans la réponse ou dans une sortie d'outil se lit à part : on garde le
            // **dernier**, celui qui décrit l'état final.
            val patch = (message.tools.mapNotNull { it.output } + if (includeText) message.text else "")
                .lastOrNull { com.onyx.app.ui.rich.looksLikePatch(it) }
            // `patchFiles` une seule fois : il découpe tout le diff, et la carte en lit deux
            // choses (le compte et le résumé).
            val files = patch?.let { runCatching { com.onyx.app.ui.rich.patchFiles(it) }.getOrDefault(emptyList()) }
                ?: emptyList()
            return MessageDerived(
                patch = patch,
                patchFileCount = files.size,
                patchSummary = patch?.let {
                    runCatching { com.onyx.app.ui.rich.patchSummary(files) }.getOrNull()
                },
                todos = todosFrom(message.tools),
                delivered = DeliveredFiles.of(
                    if (includeText) message.scannedText() else message.tools.mapNotNull { it.output },
                ),
            )
        }
    }
}

/**
 * Le texte brut que la rangée parcourt pour ses analyses : les sorties d'outils puis la réponse.
 *
 * Exposé pour que les tests puissent mesurer **ce qui est relu** sans passer par Compose.
 */
internal fun ChatMessage.scannedText(): List<String> = tools.mapNotNull { it.output } + text

/** Un accès typé aux sorties, pour les tests : la liste ne doit pas être parcourue deux fois. */
internal fun List<ToolActivity>.outputs(): List<String> = mapNotNull { it.output }
