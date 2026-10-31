package com.onyx.app.ui.sidebar

import com.onyx.app.data.model.Conversation

/**
 * **La règle de l'écran « Archives »**, et rien d'autre : les conversations que cet appareil a
 * **archivées**, de la plus récente à la plus ancienne.
 *
 * Elle vit ici, **hors** du modèle de la liste du volet (`ChatSidebarModel`), pour une raison
 * précise : la liste du volet ne filtre **jamais** sur l'état d'archive — une conversation rangée y
 * reste une rangée, atteignable, et deux tests le tiennent. La vue des archives est une **autre**
 * destination, avec sa propre règle : elle, et elle seule, ne garde que les rangées archivées.
 *
 * L'état lui-même n'est pas nouveau : `Conversation.archived` existe depuis le début (la feuille
 * d'une rangée et le lot de la sélection savent archiver). Ce qui manquait, c'était la destination
 * qui les montre — et cette fonction est ce qu'elle affiche.
 *
 * Pure, donc vérifiable sans appareil (`ArchivedConversationsTest`).
 */
internal fun archivedConversations(conversations: List<Conversation>): List<Conversation> =
    conversations.filter { it.archived }.sortedByDescending { it.updatedAt }
