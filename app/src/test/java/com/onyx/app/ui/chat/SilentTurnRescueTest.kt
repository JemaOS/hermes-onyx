package com.onyx.app.ui.chat

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **Une réponse qui n'arrive pas par le flux est relue dans la session.**
 *
 * Telegram affiche « en train d'écrire » pendant qu'un outil travaille, sans rien perdre : il
 * relit la session. L'app, elle, attendait le flux — et quand un relais, un changement de réseau
 * ou une veille le coupait, la réponse restait chez l'hôte, invisible, avec « aucune nouvelle du
 * serveur » à l'écran pour toujours.
 */
class SilentTurnRescueTest {

    private val source: String =
        File("src/main/java/com/onyx/app/ui/chat/ChatViewModel.kt").readText()

    @Test
    fun `le veilleur relit la session quand le flux se tait`() {
        assertTrue("le veilleur existe", source.contains("private fun watchForRescue("))
        assertTrue(
            "il relit les messages de la session",
            source.contains("ServeSessionMessages(server).reply(id)"),
        )
        assertTrue(
            "il ne touche à rien tant que le flux parle",
            source.contains("phase.quietSeconds(System.currentTimeMillis()) < RESCUE_AFTER_SECONDS"),
        )
    }

    @Test
    fun `la reponse relue clot le tour`() {
        assertTrue(
            "la réponse relue est posée dans le message du tour",
            source.contains("session.edit { message -> message.copy(text = fusion, isStreaming = false, error = null) }"),
        )
        assertTrue("et le tour se clôt", source.contains("session.succeed()"))
    }

    @Test
    fun `le veilleur demarre avec le tour et s arrete avec lui`() {
        assertTrue(
            "il est lancé au démarrage du tour",
            source.contains("watchForRescue(conversation, assistantId, server)"),
        )
        assertTrue(
            "il s'arrête quand le tour n'est plus là",
            source.contains("val session = turns.session(conversation) ?: return@launch"),
        )
        // LOT décrochage : le garde-fou ne sort plus sur tout tour fini, seulement sur un tour
        // fini **proprement** — une troncature doit encore être rattrapée chez l'hôte.
        assertTrue(
            "et quand le tour a fini, sauf s'il a été coupé",
            source.contains("if (!phase.isStreaming && !cut) return@launch"),
        )
        assertTrue(
            "il est annulé par un nouveau tour",
            source.contains("rescueJob?.cancel()"),
        )
    }

    @Test
    fun `une troncature est rattrapee chez l hote`() {
        // Le tour est déjà marqué en échec par `TurnSession.fail` : la réponse existe pourtant
        // chez l'hôte, et c'est exactement ce que le veilleur doit aller chercher.
        assertTrue(
            "la coupure est reconnue à sa cause, pas à un silence",
            source.contains("val cut = !phase.isStreaming && session.failure is TransportError.Truncated"),
        )
        assertTrue(
            "l'échec est effacé par la réponse complète",
            source.contains("copy(text = fusion, isStreaming = false, error = null)"),
        )
    }
}
