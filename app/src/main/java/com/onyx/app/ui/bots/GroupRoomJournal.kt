package com.onyx.app.ui.bots

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.serve.GroupEvent

/**
 * **Le journal d'une salle, lu comme les messages d'une conversation.**
 *
 * C'est ce qui donne à l'onglet **Fichiers** et au **Flux** de la salle la même matière qu'à ceux
 * d'un bot : les fichiers échangés (par `ChatFiles.conversationFiles`, qui lit le texte **brut** où
 * `DeliveredFiles` repère les chemins joints), et les réponses des bots en cartes datées.
 *
 * Chaque salon n'a donc que **ses** fichiers : la matière vient de son propre journal
 * (`state.rows`), jamais d'un magasin partagé. La fonction est **pure** — le verrou de parité
 * l'interroge sans appareil.
 */
internal fun roomThreadMessages(rows: List<GroupEvent>): List<ChatMessage> =
    rows.filter { it.isMessage }.map { event ->
        ChatMessage(
            id = "room-seq-" + event.seq,
            role = if (event.kind == "message.user") Role.USER else Role.ASSISTANT,
            text = event.text,
            createdAt = event.createdAtMillis,
        )
    }
