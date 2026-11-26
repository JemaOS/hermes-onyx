package com.onyx.app.ui.chat

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le fil s'ouvre sur sa dernière page, et « messages précédents » remonte vraiment.**
 *
 * Deux défauts que ce garde-fou empêche de revenir :
 *
 * 1. Le calcul de page était `maxOf(visibleFrom, messagesSize - PAGE)`. `visibleFrom` étant
 *    toujours plus petit que la fin du fil, il était écrasé : la ligne « N earlier messages »
 *    paraissait, et **chaque appui ne dépliait rien**. La sentinelle `-1` (« aucune page
 *    dépliée ») et le `coerceIn` sont ce qui rend le geste utile.
 * 2. La page était mémorisée sur `state.firstMessageId`. Poser une page plus ancienne en tête
 *    change cet identifiant : la pagination se refermait sous le doigt. Elle est désormais clée
 *    sur la **conversation**.
 *
 * Le fil s'ouvre aussi borné (`THREAD_PAGE`) : 500 messages pesaient 1,17 Mo à chaque ouverture.
 */
class ThreadPagingTest {

    private val screen = File("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt").readText()
    private val viewModel = File("src/main/java/com/onyx/app/ui/chat/ChatViewModel.kt").readText()

    @Test
    fun `la page se deplie vraiment`() {
        assertFalse(
            "la page ne peut plus être écrasée par la fin du fil (maxOf)",
            screen.contains("maxOf(visibleFrom, state.messagesSize - PAGE)"),
        )
        assertTrue(
            "la page part d'une sentinelle : -1 = aucune page dépliée",
            screen.contains("mutableIntStateOf(-1)"),
        )
        assertTrue(
            "et elle est bornée à la taille du fil",
            screen.contains("visibleFrom.coerceIn(0, tail)"),
        )
    }

    @Test
    fun `la pagination survit a une page posee en tete`() {
        assertTrue(
            "la page est mémorisée par conversation, pas par premier message",
            screen.contains("remember(state.conversationId) { mutableIntStateOf(-1) }"),
        )
        assertFalse(
            "sinon une page plus ancienne referme le fil",
            screen.contains("remember(state.firstMessageId) { mutableIntStateOf("),
        )
    }

    @Test
    fun `le fil s ouvre borne et le reste se demande au serveur`() {
        assertTrue(
            "l'ouverture demande une page, pas la route nue",
            viewModel.contains("rows(sessionId, conversation.botProfile, limit = THREAD_PAGE)"),
        )
        assertTrue(
            "et la page d'avant se demande par offset",
            viewModel.contains("limit = THREAD_PAGE, offset = offset"),
        )
        assertTrue(
            "la ligne du haut dit quand il reste plus ancien",
            screen.contains("hiddenCount > 0 || state.earlierAvailable"),
        )
    }
}