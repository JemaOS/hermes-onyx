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

        /**
         * Chaque question du fil, suivie de la première réponse **finie** qui vient après elle.
         *
         * **Un seul passage, à rebours.** La version d'avant, pour chaque question, refaisait un
         * `subList` + `firstOrNull` sur tout ce qui la suivait : c'était un balayage **quadratique**
         * (deux mille messages = quatre millions de comparaisons) refait à chaque publication de
         * texte, puisque la projection est recalculée à chaque jeton. À rebours, on garde la
         * première réponse rencontrée et chaque question la lit : même résultat, un seul passage.
         *
         * La règle est celle de la référence : la réponse d'une question est la **première** ligne
         * d'assistant qui la suit ; si cette ligne est vide, la question n'a pas de tour (on ne
         * saute pas à la suivante).
         */
        private fun voiceTurnsOf(messages: List<ChatMessage>): List<VoiceTurn> {
            val turns = ArrayList<VoiceTurn>()
            var next: ChatMessage? = null
            for (index in messages.indices.reversed()) {
                val message = messages[index]
                if (message.role == Role.ASSISTANT) {
                    next = message
                    continue
                }
                if (message.role != Role.USER) continue
                val answer = next?.text?.takeIf { it.isNotBlank() } ?: continue
                turns.add(VoiceTurn(question = message.text, answer = answer))
            }
            // Le parcours est à rebours : la liste se remet dans l'ordre du fil.
            return turns.asReversed()
        }
    }
}
