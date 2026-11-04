package com.onyx.app.ui

import androidx.compose.runtime.Immutable
import com.onyx.app.data.model.ApprovalRequest
import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.SubagentActivity
import com.onyx.app.ui.chat.ChatUiState
import com.onyx.app.ui.design.FallbackDeclaration
import com.onyx.app.ui.voice.VoiceTurn
import com.onyx.app.notifications.PendingQuestion
import com.onyx.app.notifications.pendingQuestionsOf
import com.onyx.app.data.model.ServerConfig

/**
 * **Ce que la coquille de l'app lit de l'état du chat — et qui ne change pas d'un jeton à l'autre.**
 *
 * `OnyxApp()` est le composable racine : 5 000 lignes qui tiennent la navigation, le volet, les
 * feuilles, les notifications et les cinq routes. Il lisait `ChatUiState` **entier** (`val state by
 * …collectAsStateWithLifecycle()`), et l'état du chat est republié à chaque publication de texte —
 * trente-trois fois par seconde pendant une réponse. Chaque jeton rejouait donc la racine entière,
 * et surtout les trois balayages qu'elle fait de **tout** le fil :
 *
 *  - `state.subagents`, un `flatMap` sur chaque message, un `distinctBy`, une fusion avec le roster ;
 *  - `state.messages.flatMap { it.approvals }`, un second balayage complet ;
 *  - `pendingQuestionsOf(state.messages)`, un troisième.
 *
 * Ces trois-là ne dépendent **pas** du texte qui s'écrit : une approbation, une question d'agent ou
 * une tâche de sous-agent ne bougent pas parce qu'un mot vient d'arriver. Cette projection les
 * calcule donc une fois par **forme** du fil, et son égalité structurelle fait que la racine ne
 * recompose plus au jeton : seuls les champs qu'elle porte vraiment (le serveur, la conversation,
 * l'état du tour) la font changer.
 *
 * **Ce qui reste vivant.** Le texte des messages n'est **pas** ici : il change à chaque jeton, et
 * une valeur qui change à chaque jeton ne doit être lue que par la ligne qui l'affiche. La coquille
 * n'en garde que la **taille** (`messagesSize`), qui suffit à ses effets (démarrer les cartes de
 * départ, publier au widget, relire le roster) ; là où elle a besoin du texte lui-même, elle lit
 * l'état au moment de l'effet (`chatState.value`), pas pendant la composition.
 */
@Immutable
data class ShellState(
    val server: ServerConfig,
    val conversationId: String?,
    val serverSessionId: String?,
    val conversationName: String?,
    val input: String,
    val isStreaming: Boolean,
    val modelInForce: String?,
    val fallback: FallbackDeclaration?,
    /** Une ligne par sous-agent de la conversation, roster compris (calculé une fois). */
    val subagents: List<SubagentActivity>,
    /** La forme du fil : sa taille, pas son contenu. */
    val messagesSize: Int,
    /** Les approbations qui attendent une décision, toutes conversations du fil confondues. */
    val pendingApprovals: List<ApprovalRequest>,
    /** Les questions d'agent (question, sudo, secret) qui attendent une réponse. */
    val pendingQuestions: List<PendingQuestion>,
    /** La première question de la conversation — le titre de repli de la voix. */
    val firstUserQuestion: String?,
    /**
     * L'historique de l'écran vocal : les tours **finis**.
     *
     * La ligne qui s'écrit n'y entre qu'à la fin de son tour ([ChatMessage.isStreaming] faux) :
     * sinon la liste se recalculerait à chaque jeton pour un écran qui ne montre que des tours
     * révolus.
     */
    val voiceTurns: List<VoiceTurn>,
) {
    companion object {

        /** Projette l'état du chat en ce que la coquille en lit. Pur, donc mesurable sans appareil. */
        fun of(ui: ChatUiState): ShellState {
            // Le fil **posé** : les messages finis, dans l'ordre. Pendant un tour, la réponse en
            // cours s'écrit — elle n'entre pas ici avant sa fin.
            val settled = ui.messages.filterNot { it.isStreaming }
            return ShellState(
                server = ui.server,
                conversationId = ui.conversationId,
                serverSessionId = ui.serverSessionId,
                conversationName = ui.conversationName,
                input = ui.input,
                isStreaming = ui.isStreaming,
                modelInForce = ui.modelInForce,
                fallback = ui.fallback,
                subagents = ui.subagents,
                messagesSize = ui.messages.size,
                pendingApprovals = ui.messages.flatMap { it.approvals }.filter { it.isPending },
                pendingQuestions = pendingQuestionsOf(ui.messages),
                firstUserQuestion = ui.messages.firstOrNull { it.role == Role.USER }?.text,
                voiceTurns = voiceTurnsOf(settled),
            )
        }

        /** Chaque question du fil, suivie de la première réponse finie qui vient après elle. */
        private fun voiceTurnsOf(messages: List<ChatMessage>): List<VoiceTurn> = buildList {
            messages.forEachIndexed { index, question ->
                if (question.role != Role.USER) return@forEachIndexed
                val answer = messages.subList(index + 1, messages.size)
                    .firstOrNull { it.role == Role.ASSISTANT }
                    ?.text
                    ?.takeIf { it.isNotBlank() }
                    ?: return@forEachIndexed
                add(VoiceTurn(question = question.text, answer = answer))
            }
        }
    }
}
