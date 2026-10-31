package com.onyx.app.data.capture

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La boîte des notes de « capture rapide ».
 *
 * Ce qui compte, et que ces tests tiennent : une note est gardée **au moment où elle est prise**,
 * elle n'est remise que pour le serveur et le profil pour lesquels elle a été prise, et une note
 * qui n'arrive pas **reste** au lieu de disparaître.
 */
class QuickCaptureTest {

    private var clock = 1_000L
    private var nextId = 0

    private fun inbox(
        initial: List<CaptureNote> = emptyList(),
        deliver: suspend (List<CaptureNote>) -> Unit = {},
        persisted: MutableList<List<CaptureNote>> = mutableListOf(),
    ) = CaptureInbox(
        initial = initial,
        now = { clock },
        newId = { "note-${nextId++}" },
        persist = { persisted += it },
        deliver = deliver,
    )

    @Test
    fun `une note vide n est pas gardee`() {
        val box = inbox()
        assertFalse(box.add("   ", "serveur", null))
        assertTrue(box.waiting.isEmpty())
    }

    @Test
    fun `une note est gardee des qu elle est prise`() {
        val persisted = mutableListOf<List<CaptureNote>>()
        val box = inbox(persisted = persisted)
        assertTrue(box.add("Le plombier passe jeudi à dix", "serveur", "Travail"))
        assertEquals(1, box.waiting.size)
        assertEquals("Le plombier passe jeudi à dix", box.waiting.single().text)
        assertEquals(1_000L, box.waiting.single().at)
        assertEquals("la note est écrite tout de suite", 1, persisted.size)
    }

    @Test
    fun `une note part pour le serveur et le profil qui l ont prise`() {
        val box = inbox()
        box.add("pour le travail", "travail", "Travail")
        box.add("pour la maison", "maison", "Maison")
        assertEquals(listOf("pour le travail"), box.ready("travail", "Travail").map { it.text })
        assertEquals(listOf("pour la maison"), box.ready("maison", "Maison").map { it.text })
        assertTrue("un autre profil ne la prend pas", box.ready("travail", "Maison").isEmpty())
    }

    @Test
    fun `une remise qui reussit vide la boite du lot`() = runBlocking {
        val sent = mutableListOf<List<CaptureNote>>()
        val box = inbox(deliver = { sent += it })
        box.add("une", "serveur", null)
        box.add("deux", "serveur", null)
        assertEquals(CaptureOutcome.FILED, box.file("serveur", null))
        assertEquals("les deux partent en un seul lot", 1, sent.size)
        assertEquals(2, sent.single().size)
        assertTrue(box.waiting.isEmpty())
    }

    @Test
    fun `une remise qui echoue garde la note`() = runBlocking {
        val box = inbox(deliver = { throw IllegalStateException("hors ligne") })
        box.add("à classer plus tard", "serveur", null)
        assertEquals(CaptureOutcome.WAITING, box.file("serveur", null))
        assertEquals(1, box.waiting.size)
    }

    @Test
    fun `deux remises ne se chevauchent pas`() = runBlocking {
        var calls = 0
        var sawFiling = false
        lateinit var box: CaptureInbox
        box = CaptureInbox(
            now = { clock },
            newId = { "note-${nextId++}" },
            deliver = {
                calls += 1
                sawFiling = box.isFiling
            },
        )
        box.add("une", "serveur", null)
        assertEquals(CaptureOutcome.FILED, box.file("serveur", null))
        assertEquals(1, calls)
        assertTrue("la remise en cours est signalée", sawFiling)
        assertFalse(box.isFiling)
    }

    @Test
    fun `une note peut etre effacee a la main`() {
        val box = inbox()
        box.add("à jeter", "serveur", null)
        val id = box.waiting.single().id
        assertTrue(box.remove(id))
        assertTrue(box.waiting.isEmpty())
        assertFalse("une note déjà partie ne s'efface pas deux fois", box.remove(id))
    }

    @Test
    fun `le message dit de classer et de ne rien demander`() {
        val notes = listOf(
            CaptureNote(id = "1", text = "Le plombier passe jeudi à dix", at = 0L, server = "s"),
            CaptureNote(id = "2", text = "Penser aux billets", at = 0L, server = "s"),
        )
        val one = CaptureCopy.message(listOf(notes[0])) { "lundi" }
        val many = CaptureCopy.message(notes) { "lundi" }
        assertTrue(one.startsWith("A note I took in passing"))
        assertTrue(many.startsWith("Notes I took in passing"))
        listOf(one, many).forEach { message ->
            assertTrue("le classement est demandé", message.contains("File"))
            assertTrue("l'agent ne doit rien demander en retour", message.contains("don't ask me anything back"))
        }
        assertTrue(many.contains("- lundi: Le plombier passe jeudi à dix"))
        assertTrue(many.contains("- lundi: Penser aux billets"))
        assertEquals("", CaptureCopy.message(emptyList()) { "lundi" })
        assertEquals("Notes", CaptureCopy.Title)
    }

    @Test
    fun `aucun mot de la plateforme d origine dans le message`() {
        val message = CaptureCopy.message(
            listOf(CaptureNote(id = "1", text = "x", at = 0L, server = "s")),
        ) { "lundi" }
        listOf("i" + "OS", "i" + "Phone", "Ap" + "ple", "S" + "iri").forEach { word ->
            assertFalse("« $word » ne doit pas apparaître", message.contains(word))
        }
    }
}
