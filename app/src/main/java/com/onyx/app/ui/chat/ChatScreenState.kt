package com.onyx.app.ui.chat

import androidx.compose.runtime.Immutable
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.SubagentActivity
import com.onyx.app.data.model.TokenUsage

/**
 * **Ce que l'écran du fil lit de l'état du chat — sans le texte qui s'écrit.**
 *
 * `ChatScreen` lisait `ChatUiState` **entier** : le corps de l'écran fait 2 200 lignes (barre du
 * haut, pageur d'onglets, compositeur, menus, feuilles), et chaque publication de texte — 33 par
 * seconde pendant une réponse — le rejouait d'un bout à l'autre. Le texte vivant n'est pourtant lu
 * que par **une** ligne : celle qui s'écrit.
 *
 * Cette projection est la serrure : elle ne porte que ce qui ne bouge **pas** d'un jeton à
 * l'autre, son égalité structurelle tient pendant tout le flux, et `derivedStateOf` fait donc que
 * le corps ne recompose plus quand un mot arrive. Le fil, lui, lit la liste vivante à part
 * (`stateFlow.value.messages`) dans sa propre portée : c'est la seule qui doit suivre le texte.
 *
 * Ce qui reste vivant et qui est **lu par le corps sans le faire recomposer** :
 *  - `messagesSize` (la forme du fil, pas son contenu) ;
 *  - `liveStep` (l'étape du tour : elle ne change qu'aux transitions) ;
 *  - `settledCount` / `settledLastId` (la frontière des messages finis : le Flux et les Fichiers ne
 *    lisent qu'eux) ;
 *  - `pendingApprovals` / `contextUsage` (deux balayages qui ne dépendent pas du texte).
 */
@Immutable
data class ChatScreenState(
    val server: ServerConfig,
    val conversationId: String?,
    val serverSessionId: String?,
    val conversationName: String?,
    val input: String,
    val isStreaming: Boolean,
    val canSteer: Boolean,
    val canRetry: Boolean,
    val isEmpty: Boolean,
    val modelInForce: String?,
    val profileInForce: String,
    val botProfile: String?,
    val fallback: com.onyx.app.ui.design.FallbackDeclaration?,
    val error: String?,
    val isConnectionProblem: Boolean,
    val providerStatus: String?,
    val steerStatus: String?,
    val prefill: com.onyx.app.data.model.PrefillProgress?,
    val attachments: List<PendingAttachment>,
    val attachmentProblem: String?,
    val searchOpen: Boolean,
    val searchQuery: String,
    val searchHits: List<String>,
    val searchCurrent: Int,
    val followUps: List<String>,
    val slashItems: List<SlashItem>,
    val modelPickerRequested: Boolean,
    val pendingSlash: SlashAction?,
    val restorePrompt: RestorePrompt?,
    val restoreNotice: String?,
    val branchPrompt: BranchPrompt?,
    val subagents: List<SubagentActivity>,

    // ---- La forme du fil, jamais son contenu vivant -------------------------------------------
    /** Le nombre de messages — la clé de la liste, stable pendant qu'une réponse s'écrit. */
    val messagesSize: Int,
    /** Le nombre de messages en attente d'envoi. */
    val queuedSize: Int,
    /** L'identifiant du premier message : la page de reprise se réinitialise quand il change. */
    val firstMessageId: String?,
    /** Le tour a-t-il annoncé une phase ? (`ChatUiState.live` non nul) */
    val hasLivePhase: Boolean,
    /** L'étape du tour en cours — un changement d'étape est un vrai changement d'écran. */
    val liveStep: TurnStep?,
    /** La première question du fil (le titre de repli, l'état vide). */
    val firstUserQuestion: String?,
    /** Y a-t-il eu au moins une question ? (l'accueil ne se montre que sans tour) */
    val hasUserTurn: Boolean,
    /** Le nombre de tours de la personne : c'est lui qui ramène la vue au bas. */
    val userTurnCount: Int,
    /** Le nombre de messages **finis** : le Flux et les Fichiers s'arrêtent là. */
    val settledCount: Int,
    /** L'identifiant du dernier message fini — la clé de leurs caches. */
    val settledLastId: String?,
    /** Les approbations qui attendent une décision (un balayage, pas un texte). */
    val pendingApprovals: Int,
    /** Le dernier remplissage de contexte connu, pour la puce du haut. */
    val contextUsage: TokenUsage?,
) {
    companion object {

        fun of(ui: ChatUiState): ChatScreenState {
            val messages = ui.messages
            val settled = if (ui.isStreaming) (messages.size - 1).coerceAtLeast(0) else messages.size
            var approvals = 0
            var users = 0
            for (message in messages) {
                if (message.approvals.isNotEmpty()) {
                    for (approval in message.approvals) if (approval.isPending) approvals++
                }
                if (message.role == com.onyx.app.data.model.Role.USER) users++
            }
            return ChatScreenState(
                server = ui.server,
                conversationId = ui.conversationId,
                serverSessionId = ui.serverSessionId,
                conversationName = ui.conversationName,
                input = ui.input,
                isStreaming = ui.isStreaming,
                canSteer = ui.canSteer,
                canRetry = ui.canRetry,
                isEmpty = ui.isEmpty,
                modelInForce = ui.modelInForce,
                profileInForce = ui.profileInForce,
                botProfile = ui.botProfile,
                fallback = ui.fallback,
                error = ui.error,
                isConnectionProblem = ui.isConnectionProblem,
                providerStatus = ui.providerStatus,
                steerStatus = ui.steerStatus,
                prefill = ui.prefill,
                attachments = ui.attachments,
                attachmentProblem = ui.attachmentProblem,
                searchOpen = ui.searchOpen,
                searchQuery = ui.searchQuery,
                searchHits = ui.searchHits,
                searchCurrent = ui.searchCurrent,
                followUps = ui.followUps,
                slashItems = ui.slashItems,
                modelPickerRequested = ui.modelPickerRequested,
                pendingSlash = ui.pendingSlash,
                restorePrompt = ui.restorePrompt,
                restoreNotice = ui.restoreNotice,
                branchPrompt = ui.branchPrompt,
                subagents = ui.subagents,
                messagesSize = messages.size,
                queuedSize = ui.queued.size,
                firstMessageId = messages.firstOrNull()?.id,
                hasLivePhase = ui.live != null,
                liveStep = ui.live?.step,
                firstUserQuestion = messages.firstOrNull { it.role == com.onyx.app.data.model.Role.USER }?.text,
                hasUserTurn = users > 0,
                userTurnCount = users,
                settledCount = settled,
                settledLastId = messages.getOrNull(settled - 1)?.id,
                pendingApprovals = approvals,
                contextUsage = messages.lastOrNull { it.usage?.contextUsed != null }?.usage,
            )
        }
    }
}
