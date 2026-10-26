package com.onyx.app

import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.data.model.serve.BotListItem
import com.onyx.app.data.net.ActivityKind
import com.onyx.app.ui.bots.BotBadges
import com.onyx.app.ui.bots.CLOCK_SLACK
import com.onyx.app.ui.bots.WORKER_LIVE_MILLIS
import com.onyx.app.ui.bots.botActivity
import com.onyx.app.ui.bots.botKind
import com.onyx.app.ui.bots.isUnread
import com.onyx.app.ui.bots.roomActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les pastilles d'activité d'un bot et d'une salle, là où les prend: « au travail » et
 * « en attente de vous » viennent du serveur (`session.active_list`, en direct), le « pas encore
 * lu » de ce que ce téléphone a vu contre la date que le serveur donne — jamais d'une
 * conversation locale qui n'a rien à voir (`ChatActivity` ).
 */
class BotBadgesTest {

    private val now = 1_761_324_000_000L

    // MARK: - Le non-lu (`ChatActivity.unread`)

    @Test
    fun `une ligne ouverte ou en travail ne s allume pas`() {
        assertFalse(isUnread(markSeconds = 100.0, seen = 50.0, isOpen = true, busy = false))
        assertFalse(isUnread(markSeconds = 100.0, seen = 50.0, isOpen = false, busy = true))
    }

    @Test
    fun `sans marque de lecture ou sans activite, rien n est nouveau`() {
        assertFalse(isUnread(markSeconds = null, seen = 0.0, isOpen = false, busy = false))
        assertFalse(isUnread(markSeconds = 100.0, seen = null, isOpen = false, busy = false))
    }

    @Test
    fun `ce qui a bouge apres la derniere lecture est nouveau, la petite difference d horloge mise a part`() {
        assertTrue(isUnread(markSeconds = 100.0, seen = 50.0, isOpen = false, busy = false))
        assertFalse("l'écart d'horloge ne compte pas", isUnread(markSeconds = 52.0, seen = 50.0, isOpen = false, busy = false))
        assertTrue(
            "au-delà de l'écart, si",
            isUnread(markSeconds = 50.0 + CLOCK_SLACK + 1, seen = 50.0, isOpen = false, busy = false),
        )
        assertTrue("marquée non lue à la main", isUnread(markSeconds = 10.0, seen = 0.0, isOpen = false, busy = false))
    }

    // MARK: - Ce qu'un bot fait (`ChatActivity.kind(of:)`)

    @Test
    fun `un bot travaille quand le serveur le dit dans son propre chat`() {
        val bot = BotProfile(name = "kilo", chatId = "bot-chat")
        assertTrue(botKind(bot, mapOf("bot-chat" to ActivityKind.WORKING), openStreaming = false, nowMillis = now) == ActivityKind.WORKING)
        assertNull("une autre session ne dit rien de lui", botKind(bot, mapOf("other" to ActivityKind.WORKING), false, now))
        assertNull("sans chat connu, le serveur ne dit rien", botKind(BotProfile(name = "kilo"), mapOf("bot-chat" to ActivityKind.WORKING), false, now))
        assertTrue(
            "une approbation en attente est « Needs you »",
            botKind(bot, mapOf("bot-chat" to ActivityKind.NEEDS_YOU), false, now) == ActivityKind.NEEDS_YOU,
        )
    }

    @Test
    fun `son chat ouvert qui ecrit et son worker entendu le disent aussi`() {
        val bot = BotProfile(name = "kilo", chatId = "bot-chat")
        assertTrue(botKind(bot, emptyMap(), openStreaming = true, nowMillis = now) == ActivityKind.WORKING)

        val worker = BotProfile(name = "kilo", workerActiveMillis = now - WORKER_LIVE_MILLIS + 1_000)
        assertTrue("un worker entendu il y a moins de 150 s", botKind(worker, emptyMap(), false, now) == ActivityKind.WORKING)
        val old = BotProfile(name = "kilo", workerActiveMillis = now - WORKER_LIVE_MILLIS - 1_000)
        assertNull("un worker tu depuis plus de 150 s ne dit rien", botKind(old, emptyMap(), false, now))
    }

    // MARK: - La pastille complète

    @Test
    fun `la pastille d un bot suit le serveur pour le travail et le telephone pour le non-lu`() {
        val bot = BotProfile(name = "kilo", chatId = "bot-chat", lastActive = 100_000L)

        val working = botActivity(bot, isOpen = false, seen = 50.0, kind = ActivityKind.WORKING, muted = false)
        assertTrue(working.working)
        assertFalse("pendant qu'il travaille, rien n'est encore une réponse", working.unread)

        val unseen = botActivity(bot, isOpen = false, seen = 50.0, kind = null, muted = false)
        assertFalse(unseen.working)
        assertTrue("du nouveau depuis la dernière fois", unseen.unread)

        val seen = botActivity(bot, isOpen = false, seen = 100.0, kind = null, muted = false)
        assertFalse(seen.unread)

        val muted = botActivity(bot, isOpen = false, seen = 50.0, kind = null, muted = true)
        assertFalse("tu : pas de point", muted.unread)

        val waiting = botActivity(bot, isOpen = false, seen = 50.0, kind = ActivityKind.NEEDS_YOU, muted = false)
        assertTrue(waiting.needsYou)
    }

    @Test
    fun `la pastille d une salle suit la meme regle`() {
        val waiting = roomActivity(markSeconds = 100.0, isOpen = false, seen = 50.0, kind = ActivityKind.NEEDS_YOU, muted = false)
        assertTrue(waiting.needsYou)
        assertFalse("elle attend quelqu'un : ce n'est pas encore du non-lu", waiting.unread)

        val fresh = roomActivity(markSeconds = 100.0, isOpen = false, seen = 50.0, kind = null, muted = false)
        assertTrue(fresh.unread)
        assertTrue("sans marque d'activité, une salle ne s'allume pas", !roomActivity(null, false, 0.0, null, false).unread)
    }

    // MARK: - Les sources, par identifiant de ligne (`BotListItem`)

    @Test
    fun `les pastilles se prennent par l identifiant de la ligne`() {
        val bot = BotProfile(name = "kilo", chatId = "bot-chat", lastActive = 100_000L)
        val badges = BotBadges(
            liveKinds = mapOf("bot-chat" to ActivityKind.WORKING),
            roomKinds = mapOf("onyx-1" to ActivityKind.NEEDS_YOU),
            seenMarks = mapOf(
                BotListItem.Bot(bot).id to 50.0,
                "room:onyx-1" to 0.0,
            ),
        )

        val botBadge = badges.forBot(bot, isOpen = false, openStreaming = false, muted = false, nowMillis = now)
        assertTrue(botBadge.working)
        assertFalse(botBadge.unread)

        val roomBadge = badges.forRoom(id = "onyx-1", activityMark = 12.0, isOpen = false, muted = false)
        assertTrue(roomBadge.needsYou)

        // Une salle qu'on ne connaît pas ne montre rien.
        val unknown = badges.forRoom(id = "onyx-9", activityMark = 12.0, isOpen = false, muted = false)
        assertFalse(unknown.working)
        assertFalse(unknown.needsYou)
        assertFalse(unknown.unread)
    }
}
