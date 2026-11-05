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
        assertTrue("la fenêtre a bougé", after.trailing != before.trailing)
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
        // Une session reprise ou nettoyée côté hôte : la fenêtre a bougé, mais aucune réponse
        // nouvelle n'est apparue. C'est l'empreinte de la réponse qui tranche, pas la taille.
        val previous = ReplyWatch.Snapshot(trailing = "8:1a2b", fingerprint = "5:1a2b")
        val current = ReplyWatch.Snapshot(trailing = "2:ffff", fingerprint = "5:1a2b")
        assertFalse(ReplyWatch.isNewReply(previous, current))
    }

    @Test
    fun `une reponse vide n'est pas une reponse`() {
        val previous = ReplyWatch.Snapshot(trailing = "2:1a2b", fingerprint = "5:1a2b")
        assertFalse(
            ReplyWatch.isNewReply(previous, ReplyWatch.Snapshot(trailing = "4:ffff", fingerprint = "")),
        )
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
        assertTrue("la fin est mesurée", ReplyWatch.snapshotOf(rows).trailing.isNotEmpty())
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
        val snapshot = ReplyWatch.Snapshot(trailing = "12:1a2b", fingerprint = "160:deadbeef")
        assertEquals(snapshot, ReplyWatch.decode(ReplyWatch.encode(snapshot)))
        assertEquals(ReplyWatch.encode(snapshot), "v2:12:1a2b|160:deadbeef")
        // Une ligne absente ou abîmée vaut « jamais mesuré » : le prochain passage reprend la mesure.
        assertNull(ReplyWatch.decode(null))
        assertNull(ReplyWatch.decode(""))
        assertNull(ReplyWatch.decode("pas une mesure"))
        assertNull(ReplyWatch.decode("v2:sans separateur"))
    }

    @Test
    fun `une mesure de l ancienne version vaut jamais mesure, donc rien n est annonce`() {
        // `« 12|160:deadbeef »` : l'ancien format (un compte, pas une empreinte de fin). Le relire
        // comme un nouveau serait lu comme « la fenêtre a bougé » et ferait sonner le téléphone pour
        // une réponse déjà lue, au premier passage après la mise à jour.
        assertNull(ReplyWatch.decode("12|160:deadbeef"))
    }

    @Test
    fun `une reponse de texte identique est vue quand la fenetre glisse`() {
        // Le compte ne savait pas le dire : même texte, même empreinte de réponse, mais une ligne de
        // plus — la fenêtre s'est décalée, une nouvelle réponse est bien arrivée.
        val before = ReplyWatch.snapshotOf(listOf(user("q1"), assistant("OK")))
        val after = ReplyWatch.snapshotOf(listOf(user("q1"), assistant("OK"), user("q2"), assistant("OK")))
        assertEquals("la réponse est identique", before.fingerprint, after.fingerprint)
        assertTrue("et pourtant quelque chose est arrivé", after.trailing != before.trailing)
    }

    @Test
    fun `la mesure ne lit que la fin du journal`() {
        // Le surveillant demande `WATCH_TAIL` lignes : la règle ne doit donc rien attendre du début.
        val long = (0 until 400).map { index -> if (index % 2 == 0) user("q$index") else assistant("r$index") }
        val tail = long.takeLast(ReplyWatch.WATCH_TAIL)
        val fromTail = ReplyWatch.snapshotOf(tail)
        val fromWhole = ReplyWatch.snapshotOf(long)
        assertEquals("la dernière réponse est la même", fromWhole.fingerprint, fromTail.fingerprint)
        assertTrue("et la fin est mesurée", fromTail.trailing.isNotEmpty())
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
