package com.onyx.app.ui.chat

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.chat.PendingAttachment
import com.onyx.app.data.model.OutboxItem
import com.onyx.app.data.model.PrefillProgress
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.SubagentActivity

/**
 * Tout ce que l'écran de chat doit rendre.
 *
 * Immuable : le ViewModel publie une nouvelle valeur à chaque événement du flux.
 */
data class ChatUiState(
    val server: ServerConfig,
    /** Conversation locale affichée : la liste large surligne exactement cette rangée. */
    val conversationId: String? = null,
    /** Identifiant de session serveur utilisé par les projets et réglages de conversation. */
    val serverSessionId: String? = null,
    /** Le nom donné à la conversation par « Renommer » (`Conversation.name` de la référence) ; `null` tant
     *  qu'aucun nom n'a été donné, auquel cas l'en-tête montre la première question. */
    val conversationName: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isStreaming: Boolean = false,
    /**
     * L'état honnête du tour en cours de cette conversation : l'étape connue et les horaires.
     * Le fil s'en sert pour dire ce qui se passe (le serveur n'envoie pas toujours de quoi le
     * montrer), et il est le même après un retour dans la conversation.
     */
    val live: TurnPhase? = null,
    /** Progression du préremplissage, quand le serveur la remonte (llama.cpp). */
    val prefill: PrefillProgress? = null,
    /** Statut du gateway/provider pendant le tour (ex. « provider indisponible, réessai »). */
    val providerStatus: String? = null,
    /**
     * Ce qu'a répondu le serveur à une note de pilotage (« steer queued », « steer rejected… »).
     * La ligne de statut de la référence (`Conversation.statusLine`) ; elle s'efface à la fin du tour.
     */
    val steerStatus: String? = null,
    /** Dernière erreur à afficher en bandeau, ou `null`. */
    val error: String? = null,
    /** Vrai si l'erreur peut être rejouée avec le même message. */
    val canRetry: Boolean = false,
    /** Serveur injoignable : utile pour proposer les réglages. */
    val isConnectionProblem: Boolean = false,
    /** Messages écrits pendant une réponse ou retenus par une coupure, avec leurs pièces jointes. */
    val queued: List<com.onyx.app.data.model.OutboxItem> = emptyList(),
    /** Ce qui est joint au message en cours d'écriture. */
    val attachments: List<PendingAttachment> = emptyList(),
    /**
     * Ce qu'on a refusé à l'ajout d'une pièce jointe, et pourquoi (le poids et le plafond, en
     * chiffres) — affiché sous le champ, à la place du problème de dictée. `null` quand rien n'a
     * été refusé.
     */
    val attachmentProblem: String? = null,
    /** La recherche dans le fil est ouverte, et ce qu'elle a trouvé. */
    val searchOpen: Boolean = false,
    val searchQuery: String = "",
    val followUps: List<String> = emptyList(),
    /** Le menu « / » du composeur, quand le brouillon commence par une barre oblique. */
    val slashItems: List<SlashItem> = emptyList(),
    /** Un `/model` nu a été envoyé : l'écran ouvre le sélecteur de modèle (une seule fois). */
    val modelPickerRequested: Boolean = false,
    /**
     * Le modèle réellement employé par le dernier envoi, une fois résolu contre le serveur : c'est
     * lui que la seconde ligne de la barre du haut nomme, à côté du « @handle » du bot
     * (`ContentView` : `modelChipLabel`).
     */
    val modelInUse: String? = null,
    /**
     * Le modèle posé pour le **bot** dont c'est la conversation, ou `null` pour une conversation
     * simple. C'est **le même souvenir** que celui qu'écrit la feuille de modèle
     * ([com.onyx.app.ui.bots.BotsViewModel.setModel] → `FaceKeys.model`) : la pilule du haut nomme
     * donc immédiatement ce que la personne vient de choisir, et l'envoi porte ce même modèle.
     */
    val botModel: String? = null,
    /**
     * Le **profil du bot** dont c'est la conversation, ou `null` pour une conversation simple.
     *
     * C'est lui qui décide du profil réellement **en force** ([profileInForce]) et du `profile`
     * porté au tour : la liaison bot → profil. Sans lui, la conversation d'un bot tournait dans le
     * profil du serveur (souvent `default`) alors que le bot était annoncé sur son propre profil.
     * `""` (le profil principal) et `null` (conversation simple) ne sont pas la même chose.
     */
    val botProfile: String? = null,
    /**
     * Le **repli déclaré** du tour : la passerelle a basculé du modèle configuré vers un autre
     * (souvent le modèle **local**) parce que le premier a échoué, et elle l'a dit sur le flux.
     * C'est lui qui fait que [modelInForce] nomme le modèle **en force** et que l'en-tête, le
     * sélecteur et la consommation **disent** le repli au lieu d'annoncer le configuré.
     */
    val fallback: com.onyx.app.ui.design.FallbackDeclaration? = null,
    /**
     * La commande « / » que l'app doit encore exécuter (ouvrir le sélecteur de modèle, une
     * nouvelle conversation…). L'écran la consomme puis appelle [ChatViewModel.consumeSlash] :
     * une commande ne doit pas se rejouer à la recomposition suivante.
     */
    val pendingSlash: SlashAction? = null,
    val searchHits: List<String> = emptyList(),
    val searchCurrent: Int = 0,
    /**
     * Le roster des sous-agents vivants, tel que `subagent.list` le rend (Dashboard seulement).
     *
     * Il comble ce que le fil ne sait pas : la **date de départ** et le **dernier outil** d'un
     * enfant lancé avant que l'app regarde, ou pendant une reconnexion. Les lignes du fil restent
     * la source vive ; le roster les complète.
     */
    val subagentRoster: List<SubagentActivity> = emptyList(),
    /**
     * La restauration qui attend **confirmation** : le message visé et ce qui serait défait.
     * Rien n'est retiré tant que ce champ est posé — c'est la confirmation demandée.
     */
    val restorePrompt: RestorePrompt? = null,
    /** Ce qu'une restauration vient de défaire, à annoncer une fois puis à oublier. */
    val restoreNotice: String? = null,
    /**
     * Le **branchement** qui attend confirmation : le message visé et ce qui serait copié. Rien
     * n'existe tant que ce champ est posé — c'est la confirmation que la référence demande aussi.
     */
    val branchPrompt: BranchPrompt? = null,
) {
    val isEmpty: Boolean get() = messages.isEmpty() && queued.isEmpty()
    val canSend: Boolean get() = (input.isNotBlank() || attachments.isNotEmpty()) && !isStreaming

    /**
     * Tous les sous-agents de la conversation : ceux des réponses, complétés par le roster.
     *
     * Une seule ligne par `subagent_id` — un enfant annoncé par `subagent.start` puis relu par
     * `subagent.list` ne doit pas apparaître deux fois.
     */
    val subagents: List<SubagentActivity>
        get() {
            val fromMessages = messages.flatMap { it.subagents }
            if (subagentRoster.isEmpty()) return fromMessages
            val known = fromMessages.map { it.id }.toSet()
            val merged = fromMessages.map { row ->
                val server = subagentRoster.firstOrNull { it.id == row.id } ?: return@map row
                row.copy(
                    startedAt = row.startedAt.takeIf { it > 0 } ?: server.startedAt,
                    lastTool = row.lastTool ?: server.lastTool,
                    toolCount = row.toolCount.takeIf { it > 0 } ?: server.toolCount,
                    childSessionId = row.childSessionId ?: server.childSessionId,
                    model = row.model ?: server.model,
                    acceptingSteer = row.acceptingSteer && server.acceptingSteer,
                )
            }
            return merged + subagentRoster.filter { it.id !in known }
        }

    /**
     * Vrai quand un message écrit maintenant peut piloter la réponse en cours plutôt que
     * d'attendre. Miroir de `Conversation.canSteer` : il faut un tour en cours et un transport
     * qui garde un registre côté serveur (`hasLedger` : sessions Hermes ou serve).
     */
    val canSteer: Boolean get() = isStreaming && server.transport.isStateful

    /**
     * **Le modèle en vigueur** — la seule source de vérité du « modèle qui va servir », **nom à peindre
     * et modèle à envoyer, la même valeur**.
     *
     * La pilule du haut, la puce, la feuille de modèle et l'envoi la lisent tous : **ce qui est nommé
     * est ce qui part**. L'ordre et la règle des alias sont dans
     * [com.onyx.app.ui.design.ModelInForce.inForce] (conversation → bot → serveur → défaut, l'alias de
     * passerelle écarté).
     */
    val modelInForce: String?
        get() = com.onyx.app.ui.design.ModelFallback.displayName(
            fallback,
            com.onyx.app.ui.design.ModelInForce.inForce(modelInUse, botModel, server.effectiveModel),
        )

    /**
     * **Le profil en vigueur** — la même valeur que celle que l'envoi porte `profile` au tour.
     *
     * Le profil du bot de la conversation décide, sinon celui du serveur, sinon `default`
     * ([com.onyx.app.ui.design.ProfileInForce.inForce]). L'en-tête écrit cette valeur **telle
     * quelle** et l'envoi porte **exactement** [botProfile] : ce qui est montré est ce qui sert.
     */
    val profileInForce: String
        get() = com.onyx.app.ui.design.ProfileInForce.inForce(botProfile, server.effectiveProfile)
}

/** Change le fil présenté sans laisser fuiter les contrôles éphémères du fil précédent. */
internal fun ChatUiState.switchToConversation(
    conversationId: String,
    serverSessionId: String?,
    messages: List<ChatMessage>,
    input: String,
    queued: List<OutboxItem>,
    conversationName: String? = null,
): ChatUiState = copy(
    conversationId = conversationId,
    serverSessionId = serverSessionId,
    conversationName = conversationName,
    messages = messages,
    input = input,
    isStreaming = false,
    live = null,
    // Le modèle servi appartient à la conversation qu'on quitte : celui de la nouvelle arrive
    // avec son premier tour (`follow`). Sans ça, le sous-titre de l'en-tête annonçait encore le
    // modèle de l'ancienne conversation après une création.
    modelInUse = null,
    // Et le modèle du **bot** de l'ancienne conversation : celui de la nouvelle arrive avec sa
    // propre conversation (le bot qui la revendique), jamais hérité de la précédente.
    botModel = null,
    // Et son **profil** : une nouvelle conversation n'hérite pas du profil du bot qu'on quitte —
    // l'écran le repose pour la conversation qu'il ouvre (`setBotProfile`), ou `null` (conversation
    // simple). Sans cette remise à zéro, la conversation suivante garderait le profil de la
    // précédente, et la liaison bot → profil serait fausse.
    botProfile = null,
    // Le repli déclaré appartient à la conversation qu'on quitte : il ne suit pas.
    fallback = null,
    prefill = null,
    providerStatus = null,
    steerStatus = null,
    error = null,
    canRetry = false,
    isConnectionProblem = false,
    queued = queued,
    attachments = emptyList(),
    searchOpen = false,
    searchQuery = "",
    followUps = emptyList(),
    slashItems = emptyList(),
    modelPickerRequested = false,
    pendingSlash = null,
    searchHits = emptyList(),
    searchCurrent = 0,
    restorePrompt = null,
    restoreNotice = null,
    // Une confirmation qui visait l'autre fil n'a plus d'objet ici (le branchement surtout : il
    // ouvrirait la copie d'une conversation qu'on vient de quitter).
    branchPrompt = null,
)

/**
 * Une restauration qui attend confirmation.
 *
 * [removedMessages] : le nombre de messages du fil qui seront retirés (le message visé reste) ;
 * [removedTurns] : le nombre de **tours utilisateur** qu'il faut rembobiner côté gateway
 * (`session.undo` compte les tours, pas les messages).
 */
data class RestorePrompt(
    val messageId: String,
    val removedMessages: Int,
    val removedTurns: Int,
)

/**
 * « 1 message » / « 3 messages » : le compte, dans la langue de l'app.
 *
 * **Le zéro ne se dit pas.** Une phrase qui annonce « retire 0 messages » est absurde : elle décrit
 * une action qui n'en fait aucune. Le cas a donc **sa propre phrase** ([restoreZeroPhrase]), et
 * [restorePromptMessage] bascule dessus dès que le compte vaut zéro.
 */
internal fun restoreCountPhrase(count: Int): String =
    if (count == 1) OnyxLocalization["1 message"] else OnyxLocalization["$count messages"]

/**
 * La phrase quand il n'y a **rien** à retirer — le message visé est le dernier du fil.
 *
 * On ne dit pas « 0 message » : on dit ce qui est vrai, et qui s'explique — le fil s'arrête sur ce
 * message, donc la restauration ne retirerait rien.
 */
internal fun restoreZeroPhrase(): String =
    OnyxLocalization["Nothing follows this message: restoring would remove nothing."]

/**
 * Ce qu'une restauration **à** [messageId] retirerait — `null` si le message n'est pas dans le fil.
 *
 * Fonction **pure** (donc testable) : c'est la règle qui décide du compte des messages et des tours
 * utilisateur à rembobiner. Le message visé **reste** (c'est le point où l'on revient) ; tout ce qui
 * le suit est retiré. Rien n'est coupé ici : [RestorePrompt] sert à **demander confirmation**.
 */
internal fun restorePlan(messages: List<ChatMessage>, messageId: String): RestorePrompt? {
    val index = messages.indexOfFirst { it.id == messageId }
    if (index < 0) return null
    val removed = messages.drop(index + 1)
    return RestorePrompt(
        messageId = messageId,
        removedMessages = removed.size,
        removedTurns = removed.count { it.isQuestion },
    )
}

/** Le fil ramené **à** [messageId] : le message visé, et tout ce qui le précède. */
internal fun messagesThroughRestore(messages: List<ChatMessage>, messageId: String): List<ChatMessage> {
    val index = messages.indexOfFirst { it.id == messageId }
    return if (index < 0) messages else messages.take(index + 1)
}

/**
 * Un branchement qui attend confirmation.
 *
 * [copiedMessages] : le nombre de messages que la nouvelle conversation recevra — le message visé
 * **et** tout ce qui le précède. C'est ce compte que la phrase de confirmation donne, et c'est lui
 * qu'un test peut opposer au fil réellement créé.
 */
data class BranchPrompt(
    val messageId: String,
    val copiedMessages: Int,
)

/**
 * Ce qu'un branchement **à partir de** [messageId] copierait — `null` si le message n'est pas dans
 * le fil.
 *
 * Fonction **pure** (donc testable) : le message visé est **inclus** (c'est le point de départ de
 * la nouvelle conversation), et tout ce qui suit est laissé à l'originale. Rien n'est créé ici :
 * [BranchPrompt] sert à **demander confirmation**.
 */
internal fun branchPlan(messages: List<ChatMessage>, messageId: String): BranchPrompt? {
    val index = messages.indexOfFirst { it.id == messageId }
    if (index < 0) return null
    return BranchPrompt(messageId = messageId, copiedMessages = index + 1)
}

/**
 * Les **quatre lignes** de la confirmation d'un branchement, dans l'ordre de la référence : ce que
 * le branchement fait, ce qu'il ne fait pas, quand rien ne démarre — puis, en plus, ce que la copie
 * emporte vraiment chez nous.
 *
 * La dernière ligne est la nôtre et elle est **vraie** : la copie vit sur ce téléphone et les
 * pièces jointes suivent, parce que les messages sont copiés avec leurs pièces jointes. Le compte
 * est celui du fil visé, dans la langue de l'app.
 */
internal fun branchPromptLines(prompt: BranchPrompt): List<String> = listOf(
    OnyxLocalization["Copies this conversation up to this message into a new conversation, and opens it."],
    OnyxLocalization["This conversation stays as it is."],
    OnyxLocalization["No task starts until you send something in the new conversation."],
    String.format(
        OnyxLocalization["The copy lives on this phone: %1\$s, attachments included."],
        restoreCountPhrase(prompt.copiedMessages),
    ),
)

/**
 * La phrase de confirmation d'une restauration : ce qui sera défait, en clair.
 *
 * **Le vrai nombre de messages retirés** — celui de [restorePlan], calculé sur le fil réel — au
 * singulier quand il vaut 1, au pluriel au-delà. Et **jamais « 0 »** : quand rien ne suit le message
 * visé, la phrase change ([restoreZeroPhrase]) au lieu d'annoncer un compte nul.
 */
internal fun restorePromptMessage(prompt: RestorePrompt): String =
    if (prompt.removedMessages == 0) {
        restoreZeroPhrase()
    } else {
        String.format(
            OnyxLocalization["This removes %1\$s and everything after it, and rewinds the server to the same point. It can't be undone."],
            restoreCountPhrase(prompt.removedMessages),
        )
    }

/** Une entrée du menu `/` : ce qui s'écrit dans le brouillon, et ce qui s'affiche. */
data class SlashItem(val text: String, val display: String, val detail: String = "")
