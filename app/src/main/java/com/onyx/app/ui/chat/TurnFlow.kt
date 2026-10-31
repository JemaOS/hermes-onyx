package com.onyx.app.ui.chat

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.ui.design.FlowState

/**
 * **L'état réel d'un tour, lu dans le fil — le déclencheur de chaque état.**
 *
 * Le concept « fil vivant » n'a le droit de peindre qu'un état **vrai** : ces deux fonctions sont
 * donc le seul endroit où un état est décidé, et elles ne lisent que ce que le tour sait vraiment
 * ([TurnPhase], les approbations en attente). Aucun état n'est décoratif : chacun correspond à une
 * étape que le serveur a fait connaître, ou à une question qui attend une réponse.
 *
 * | état | déclencheur |
 * |---|---|
 * | [FlowState.REST] | aucun tour, ou tour fini ([TurnStep.DONE], [TurnStep.STOPPED], [TurnStep.FAILED]) |
 * | [FlowState.THINKING] | le tour est ouvert, rien n'est écrit : [TurnStep.STARTING] (le serveur n'a rien dit) ou [TurnStep.THINKING] (la réflexion est arrivée) |
 * | [FlowState.WORKING] | [TurnStep.TOOL] (un outil tourne) ou [TurnStep.WRITING] (la réponse s'écrit) |
 * | [FlowState.DECIDING] | au moins une approbation **en attente** (`ApprovalRequest.isPending`) |
 *
 * **Pourquoi l'attente l'emporte sur le travail.** Un tour qui attend une approbation est un tour
 * **arrêté** : rien n'avance tant que la personne n'a pas répondu. Montrer « travail » là serait
 * faux — et c'est exactement le défaut que le lot corrige (on ne voyait qu'une carte isolée, sans
 * rapport avec l'en-tête). L'attente est donc testée **en premier**, avant l'étape.
 *
 * **Pourquoi [TurnStep.STARTING] est une réflexion.** Le tour est ouvert, le serveur n'a encore
 * rien envoyé : l'agent travaille là-bas. Le dire « au repos » serait faux (il y a un tour), le
 * dire « travail » serait faux aussi (rien n'est encore produit). C'est le même souffle que la
 * réflexion, et la ligne d'état du fil dit, elle, « En attente du serveur · 0:02 ».
 */
fun turnFlowState(live: TurnPhase?, pendingApprovals: Int): FlowState = when {
    live == null || !live.isStreaming -> FlowState.REST

    // Une question posée à la personne arrête le tour : c'est le seul état qui ne bouge pas.
    pendingApprovals > 0 -> FlowState.DECIDING

    live.step == TurnStep.TOOL || live.step == TurnStep.WRITING -> FlowState.WORKING

    // STARTING et THINKING : le tour est ouvert, la réponse n'a pas commencé.
    live.step == TurnStep.STARTING || live.step == TurnStep.THINKING -> FlowState.THINKING

    // DONE / STOPPED / FAILED : le tour n'est plus en cours, `isStreaming` est faux — on ne passe
    // ici que si l'hôte a menti sur son propre état, et le repos est alors la seule vérité.
    else -> FlowState.REST
}

/**
 * **Les approbations qui attendent vraiment une réponse**, dans tout le fil.
 *
 * Le compte ne regarde que les questions **ouvertes** (`decision == null`) : une approbation déjà
 * répondue est une carte d'archive, pas un tour arrêté. C'est ce compte que [turnFlowState] reçoit.
 */
fun pendingApprovalCount(messages: List<ChatMessage>): Int =
    messages.sumOf { message -> message.approvals.count { it.isPending } }

/** Le même compte, pour un fil déjà réduit à ses approbations. */
fun pendingApprovalCountOf(vararg approvals: com.onyx.app.data.model.ApprovalRequest): Int =
    approvals.count { it.isPending }
