package com.onyx.app.ui.bots

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.serve.GroupEvent
import com.onyx.app.data.model.serve.GroupMember

/**
 * **Le journal d'une salle, lu comme les messages d'une conversation.**
 *
 * C'est ce qui donne à l'onglet **Fichiers** et au **Flux** de la salle la même matière qu'à ceux
 * d'un bot : les fichiers échangés (par `ChatFiles.conversationFiles`, qui lit le texte **brut** où
 * `DeliveredFiles` repère les chemins joints), et les réponses des bots en cartes datées.
 *
 * **L'auteur de chaque message voyage avec lui** (`ChatMessage.authorProfile`) : c'est le **nom de
 * profil** du membre, lu par la **même** clé que le fil — l'`actorID` de l'événement, résolu par
 * `GroupMember.profile` (voir `RoomEventRow`, qui pose par elle le nom et le visage d'une réponse).
 * Sans cela, le Flux ne savait pas qui avait répondu et peignait le visage de la salle sur toutes
 * ses cartes — le défaut « toutes les cartes ont le même visage ».
 *
 * Chaque salon n'a donc que **ses** fichiers : la matière vient de son propre journal
 * (`state.rows`), jamais d'un magasin partagé. La fonction est **pure** — le verrou de parité
 * l'interroge sans appareil. [members] est la liste des membres du salon ; absente, aucun auteur
 * n'est nommé (le Flux retombe alors sur le visage de la salle).
 */
internal fun roomThreadMessages(
    rows: List<GroupEvent>,
    members: List<GroupMember> = emptyList(),
): List<ChatMessage> =
    rows.filter { it.isMessage }.map { event ->
        ChatMessage(
            id = "room-seq-" + event.seq,
            role = if (event.kind == "message.user") Role.USER else Role.ASSISTANT,
            text = event.text,
            createdAt = event.createdAtMillis,
            // Le membre qui a parlé, par la même clé que le fil : `room.member(actorID).profile`.
            authorProfile = members.firstOrNull { it.id == event.actorID }?.profile.orEmpty(),
        )
    }
