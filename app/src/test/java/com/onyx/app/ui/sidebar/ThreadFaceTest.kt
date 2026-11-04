package com.onyx.app.ui.sidebar

import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.ui.bots.conversationProfile
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le visage d'un fil ne déborde pas sur les autres fils du même bot.**
 *
 * Relevé par le propriétaire : « j'ai changé juste le visage de celui que j'avais en conversation,
 * ça a changé tous les coder ». Trois rangées portaient le nom du même bot, et le visage n'existait
 * qu'au niveau du **profil** : le changer changeait les trois.
 *
 * Le visage vit maintenant aussi sur la **conversation** ([Conversation.faceCode]) : ce fil-là le
 * porte, ses voisins gardent celui du bot, et la tuile du bot dans l'onglet « Bots » ne bouge pas.
 */
class ThreadFaceTest {

    private val server = ServerConfig(id = "srv", name = "Onyx", baseUrl = "https://exemple.test")
    private val bot = BotProfile(name = "coder", faceCode = "1")

    private fun thread(id: String, face: String? = null) = Conversation(
        id = id,
        title = "coder",
        createdAt = 0L,
        updatedAt = 0L,
        botProfile = "coder",
        faceCode = face,
    )

    private fun rows(vararg conversations: Conversation): List<SidebarRow> = sidebarRows(
        conversations = conversations.toList(),
        liveKinds = emptyMap(),
        seenMarks = emptyMap(),
        now = 1_000.0,
        identityOf = { c -> conversationProfile(c, listOf(bot), server) },
        faceCodeOf = { profile -> if (profile == "coder") "1" else null },
    )

    @Test
    fun `le visage du fil gagne sur celui du bot, pour sa seule rangee`() {
        val changed = thread("c1", face = "9")
        val other = thread("c2")
        val all = rows(changed, other)

        assertEquals("9", all.first { it.title == "coder" && it.identityFace == "9" }.identityFace)
        assertEquals(
            "l'autre fil du même bot garde le visage du bot",
            1,
            all.count { it.identityFace == "1" },
        )
        assertEquals("deux rangées, deux visages", 2, all.map { it.identityFace }.distinct().size)
    }

    @Test
    fun `sans visage de fil, la rangee suit le bot`() {
        assertEquals("1", rows(thread("c1")).single().identityFace)
    }

    // MARK: - Le branchement

    private val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()
    private val screen = File("src/main/java/com/onyx/app/ui/sidebar/ChatSidebarModel.kt").readText()

    @Test
    fun `la liste lit le visage du fil avant celui du bot`() {
        assertTrue(screen.contains("identityFace = conversation.faceCode ?: faceCodeOf(identity)"))
    }

    @Test
    fun `le choix fait dans un fil ecrit sur ce fil, pas sur le bot`() {
        assertTrue("le contexte du fil existe", app.contains("var faceConversation by remember"))
        assertTrue("il est posé à l'ouverture depuis l'en-tête", app.contains("faceConversation = openedConversation"))
        assertTrue("l'onglet Bots le remet à zéro", app.contains("faceConversation = null"))
        assertTrue(
            "l'écriture va sur la conversation",
            app.contains("conversationRepo.save(thread.copy(faceCode = code))"),
        )
        assertTrue(
            "et le visage du bot ne reçoit le choix que hors d'un fil",
            app.contains("} else {\n                                botsViewModel.setFace(bot, code)"),
        )
        assertTrue("l'en-tête du fil suit ce visage", app.contains("openedConversation?.faceCode?.let { code -> profile.copy(faceCode = code) }"))
    }
}
