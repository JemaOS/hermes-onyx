package com.onyx.app.ui.context

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le contrôleur est testé avec un vrai cycle lecture → édition → sauvegarde, sans réseau simulé dans l'UI. */
class ContextFileEditorControllerTest {
    @Test
    fun `la maison personnalisee du dashboard ne recoit pas un second dossier hermes`() {
        val state = ContextUiState(home = "/opt/data", loading = false)

        assertEquals("/opt/data/memories/MEMORY.md", state.pathFor(ContextFile.MEMORY))
        assertEquals("/opt/data/ENVIRONMENT.md", state.pathFor(ContextFile.ENVIRONMENT))
    }

    @Test
    fun `le chargement utilise la maison du profil actif`() = runBlocking {
        val store = FakeContextFileStore(home = "/opt/hermes/scout", text = "souvenirs")
        val editor = ContextFileEditorController(store, ContextFile.MEMORY, profile = "Scout")

        editor.load()

        assertEquals(listOf("Scout"), store.homeProfiles)
        assertEquals(listOf("/opt/hermes/scout/memories/MEMORY.md"), store.readPaths)
        assertEquals("souvenirs", editor.state.value.content)
        assertEquals("souvenirs", editor.state.value.original)
        assertEquals("/opt/hermes/scout/memories/MEMORY.md", editor.state.value.path)
        assertTrue(editor.state.value.homeKnown)
        assertFalse(editor.state.value.missing)
        assertFalse(editor.state.value.dirty)
        assertFalse(editor.state.value.loading)
    }

    @Test
    fun `un fichier absent est cree au chemin de secours`() = runBlocking {
        val store = FakeContextFileStore(home = null, text = null)
        val editor = ContextFileEditorController(store, ContextFile.ENVIRONMENT, profile = null)

        editor.load()
        assertTrue(editor.state.value.missing)
        assertFalse(editor.state.value.homeKnown)
        assertEquals("~/.hermes/ENVIRONMENT.md", editor.state.value.path)

        editor.onTextChange("règles")
        assertTrue(editor.state.value.dirty)
        editor.save()

        assertEquals(listOf("~/.hermes/ENVIRONMENT.md" to "règles"), store.writes)
        assertFalse(editor.state.value.missing)
        assertFalse(editor.state.value.dirty)
        assertFalse(editor.state.value.saving)
        assertNull(editor.state.value.error)
    }

    @Test
    fun `soul utilise la route du profil pour lire et ecrire`() = runBlocking {
        val store = FakeContextFileStore(home = null, soulText = "personnalité")
        val editor = ContextFileEditorController(store, ContextFile.SOUL, profile = "scout")

        editor.load()
        editor.onTextChange("nouvelle personnalité")
        editor.save()

        assertEquals(listOf("scout"), store.soulReads)
        assertEquals(listOf("scout" to "nouvelle personnalité"), store.soulWrites)
        assertEquals(emptyList<String>(), store.readPaths)
        assertEquals(emptyList<Pair<String, String>>(), store.writes)
        assertEquals("~/.hermes/profiles/scout/SOUL.md", editor.state.value.path)
        assertFalse(editor.state.value.dirty)
    }

    @Test
    fun `sauvegarder sans modification ne fait aucune requete`() = runBlocking {
        val store = FakeContextFileStore(home = "/hermes", text = "déjà là")
        val editor = ContextFileEditorController(store, ContextFile.MEMORY, profile = null)
        editor.load()

        editor.save()

        assertEquals(emptyList<Pair<String, String>>(), store.writes)
    }

    @Test
    fun `une erreur de sauvegarde garde le brouillon modifie`() = runBlocking {
        val store = FakeContextFileStore(home = "/hermes", text = "avant").apply {
            writeError = IOException("disque plein")
        }
        val editor = ContextFileEditorController(store, ContextFile.MEMORY, profile = null)
        editor.load()
        editor.onTextChange("à ne pas perdre")

        editor.save()

        assertEquals("à ne pas perdre", editor.state.value.content)
        assertEquals("avant", editor.state.value.original)
        assertEquals("disque plein", editor.state.value.error)
        assertTrue(editor.state.value.dirty)
        assertFalse(editor.state.value.saving)
    }
}

private class FakeContextFileStore(
    private val home: String?,
    private val text: String? = null,
    private val soulText: String? = null,
) : ContextFileStore {
    val homeProfiles = mutableListOf<String?>()
    val readPaths = mutableListOf<String>()
    val writes = mutableListOf<Pair<String, String>>()
    val soulReads = mutableListOf<String>()
    val soulWrites = mutableListOf<Pair<String, String>>()
    var writeError: Exception? = null

    override suspend fun home(profile: String?): String? {
        homeProfiles += profile
        return home
    }

    override suspend fun read(path: String): String? {
        readPaths += path
        return text
    }

    override suspend fun write(path: String, content: String) {
        writeError?.let { throw it }
        writes += path to content
    }

    override suspend fun readSoul(profile: String): String? {
        soulReads += profile
        return soulText
    }

    override suspend fun writeSoul(profile: String, content: String) {
        writeError?.let { throw it }
        soulWrites += profile to content
    }
}
