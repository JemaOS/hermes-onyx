package com.onyx.app.ui.conversations

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.Role
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **L'ordre des rangées de la feuille d'une conversation**, aligné sur la référence.
 *
 * Chez la référence, la feuille d'une session range ses lignes dans cet ordre : *Rename · Make
 * thread · Pin · Usage and cost · … · Copy session ID · **Archive** · **Delete***. L'archivage est
 * une **sortie de liste** : elle se lit en avant-dernière ligne, juste avant le destructeur, et non
 * au milieu des gestes du fil. Onyx la rangeait troisième — ce test tient l'ordre désormais.
 *
 * Rien n'a été retiré : les compléments de la maison (« Marquer comme non lu », « Usage & coût »,
 * « Copier le titre », « Ouvrir ailleurs ») restent, **avant** l'archivage.
 */
class ConversationSheetOrderTest {

    private fun conversation(empty: Boolean = false) = Conversation(
        id = "c1",
        title = "Une conversation",
        createdAt = 1_000,
        updatedAt = 2_000,
        messages = if (empty) emptyList() else listOf(
            ChatMessage(id = "m1", role = Role.USER, text = "Bonjour", createdAt = 1_000),
        ),
    )

    private fun rows(withExtras: Boolean) = conversationSheetRows(
        conversation = conversation(),
        canMarkUnread = true,
        muted = false,
        unread = true,
        onRename = {},
        onCreateThread = {},
        onTogglePin = {},
        onToggleArchive = {},
        onDelete = {},
        onToggleUnread = if (withExtras) ({}) else null,
        onUsage = if (withExtras) ({}) else null,
        onCopyTitle = if (withExtras) ({}) else null,
        onOpenElsewhere = if (withExtras) ({}) else null,
    )

    @Test
    fun `l archiver se lit en avant-derniere ligne, la suppression en derniere`() {
        val labels = rows(withExtras = false).map { it.label }
        assertEquals(listOf("Rename", "Create thread", "Pin", "Archive", "Delete"), labels)
    }

    @Test
    fun `les complements passent avant l archiver, qui reste devant le destructeur`() {
        val labels = rows(withExtras = true).map { it.label }
        assertEquals(listOf("Rename", "Create thread", "Pin", "Mark as Read", "Usage & cost", "Copy title", "Open in New Window", "Archive", "Delete"), labels)
        // Le destructeur ne remonte jamais : il est la **dernière** ligne.
        assertEquals("Delete", labels.last())
        // Et l'archivage le précède immédiatement.
        assertEquals("Archive", labels[labels.size - 2])
    }

    @Test
    fun `rien n a ete retire de la feuille`() {
        // Les cinq gestes d'origine et les quatre compléments sont tous là.
        val labels = rows(withExtras = true).map { it.label }.toSet()
        listOf(
            "Rename", "Create thread", "Pin", "Archive", "Delete",
            "Mark as Read", "Usage & cost", "Copy title", "Open in New Window",
        ).forEach { label ->
            assertEquals("la ligne « $label » ne peut pas disparaître", true, labels.contains(label))
        }
    }

    @Test
    fun `une conversation vide garde son fil desactive, pas retire`() {
        // « Créer un fil » reste **présent** mais désactivé sur une conversation vide (rien à
        // recopier) : la ligne existe, elle n'agit pas.
        val row = conversationSheetRows(
            conversation = conversation(empty = true),
            canMarkUnread = false,
            muted = false,
            onRename = {},
            onCreateThread = {},
            onTogglePin = {},
            onToggleArchive = {},
            onDelete = {},
        ).first { it.label == "Create thread" }
        assertEquals(false, row.enabled)
    }
}
