package com.onyx.app.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La règle « une réponse est-elle nouvelle ? », testée sans Android.
 *
 * Ce sont les cas qui font la différence entre une alerte utile et une alerte qui ment : le
 * premier passage (rien à annoncer), le message de l'utilisateur sans réponse, deux passages sur
 * la même réponse, et l'aller-retour sur le disque de la mesure.
 */
class ReplyWatchTest {

    private fun user(text: String) = ServeSessionMessages.Row(role = "user", text = text)
    private fun assistant(text: String) = ServeSessionMessages.Row(role = "assistant", text = text)

    @Test
    fun `le premier passage prend la mesure sans rien annoncer`() {
        val current = ReplyWatch.snapshotOf(listOf(user("salut"), assistant("bonjour")))
        assertFalse(ReplyWatch.isNewReply(previous = null, current = current))
    }

    @Test
    fun `une reponse qui arrive apres un premier passage est nouvelle`() {
        val previous = ReplyWatch.snapshotOf(listOf(user("q1"), assistant("r1")))
        val current = ReplyWatch.snapshotOf(listOf(user("q1"), assistant("r1"), user("q2"), assistant("r2")))
        assertTrue(ReplyWatch.isNewReply(previous, current))
    }

    @Test
    fun `une question sans reponse n'annonce rien`() {
        val before = ReplyWatch.snapshotOf(listOf(user("q1"), assistant("r1")))
        // L'utilisateur a écrit : le journal grandit, la dernière réponse ne change pas.
        val after = ReplyWatch.snapshotOf(listOf(user("q1"), assistant("r1"), user("q2")))
        assertTrue(after.count > before.count)
        assertFalse(ReplyWatch.isNewReply(before, after))
    }

    @Test
    fun `deux passages sur la meme reponse ne sonnent qu'une fois`() {
        val first = ReplyWatch.snapshotOf(listOf(user("q1"), assistant("r1")))
        val second = ReplyWatch.snapshotOf(listOf(user("q1"), assistant("r1"), user("q2"), assistant("r2")))
        assertTrue(ReplyWatch.isNewReply(first, second))
        // Le passage suivant voit exactement la même chose : plus rien à annoncer.
        assertFalse(ReplyWatch.isNewReply(second, second))
    }

    @Test
    fun `une reponse deja dans le fil du telephone n'est pas annoncee`() {
        val previous = ReplyWatch.snapshotOf(listOf(user("q1"), assistant("r1")))
        val current = ReplyWatch.snapshotOf(listOf(user("q1"), assistant("r1"), user("q2"), assistant("r2")))
        // Le fil local a déjà reçu `r2` : le suivi du travail l'a annoncée, ou l'écran la montre.
        assertEquals("r2", ReplyWatch.lastAnswer(listOf(user("q2"), assistant("r2"))))
        assertFalse(ReplyWatch.isNewReply(previous, current, alreadyHere = ReplyWatch.fingerprintOf("r2")))
        // Une réponse que le téléphone ne tient pas encore, elle, s'annonce.
        assertTrue(ReplyWatch.isNewReply(previous, current, alreadyHere = ReplyWatch.fingerprintOf("r1")))
    }

    @Test
    fun `le journal qui retrecit n'annonce rien`() {
        // Une session reprise ou nettoyée côté hôte : ce n'est pas une nouvelle réponse.
        val previous = ReplyWatch.Snapshot(count = 8, fingerprint = "5:1a2b")
        val current = ReplyWatch.Snapshot(count = 2, fingerprint = "9:ffff")
        assertFalse(ReplyWatch.isNewReply(previous, current))
    }

    @Test
    fun `une reponse vide n'est pas une reponse`() {
        val previous = ReplyWatch.Snapshot(count = 2, fingerprint = "5:1a2b")
        assertFalse(ReplyWatch.isNewReply(previous, ReplyWatch.Snapshot(count = 4, fingerprint = "")))
        assertEquals("", ReplyWatch.fingerprintOf("   \n  "))
    }

    @Test
    fun `la derniere reponse est prise meme suivie de messages sans texte`() {
        val rows = listOf(
            user("q1"),
            assistant("r1"),
            user("q2"),
            assistant("r2"),
            // Un tour qui écrit un message d'outil, ou une réponse vide : la mesure reste `r2`.
            ServeSessionMessages.Row(role = "tool", text = "{}"),
            assistant(""),
        )
        assertEquals("r2", ReplyWatch.lastAnswer(rows))
        assertEquals(rows.size, ReplyWatch.snapshotOf(rows).count)
    }

    @Test
    fun `l'empreinte ne garde pas le texte et change avec lui`() {
        val short = ReplyWatch.fingerprintOf("Bonjour")
        val longer = ReplyWatch.fingerprintOf("Bonjour, avec une suite")
        assertEquals(short, ReplyWatch.fingerprintOf("  Bonjour  "))
        assertTrue(short.isNotEmpty())
        assertTrue("empreinte en clair : $short", !short.contains("Bonjour"))
        assertTrue(short != longer)
    }

    @Test
    fun `la mesure fait l'aller-retour sur le disque`() {
        val snapshot = ReplyWatch.Snapshot(count = 12, fingerprint = "160:deadbeef")
        assertEquals(snapshot, ReplyWatch.decode(ReplyWatch.encode(snapshot)))
        assertEquals(ReplyWatch.encode(snapshot), "12|160:deadbeef")
        // Une ligne absente ou abîmée vaut « jamais mesuré » : le prochain passage reprend la mesure.
        assertNull(ReplyWatch.decode(null))
        assertNull(ReplyWatch.decode(""))
        assertNull(ReplyWatch.decode("pas une mesure"))
        assertNull(ReplyWatch.decode("x|3"))
        assertNull(ReplyWatch.decode("-1|3"))
    }

    @Test
    fun `l'apercu tient sur une notification`() {
        assertEquals("Bonjour", ReplyWatch.preview("  Bonjour \n "))
        val long = "mot ".repeat(200)
        val preview = ReplyWatch.preview(long, limit = 40)
        assertTrue(preview.length <= 40)
        assertTrue(preview.endsWith("…"))
        // Aucune coupure au milieu d'un mot.
        assertTrue(preview, !preview.removeSuffix("…").endsWith("mo"))
    }
}
