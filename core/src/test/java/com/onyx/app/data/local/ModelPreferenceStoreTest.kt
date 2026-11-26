package com.onyx.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les favoris et les récents d'un modèle, **gardés par serveur**.
 *
 * C'est ce qui fait qu'une étoile posée sur le téléphone **se retrouve après une relance** : le
 * magasin relu rend exactement ce qui a été écrit. Le test le vérifie sans appareil, sur une copie
 * en mémoire de `SharedPreferences`.
 */
class ModelPreferenceStoreTest {

    /** Une copie en mémoire de `SharedPreferences` : mêmes clés, même comportement. */
    private class MemoryStore : RenameStore {
        private val values = mutableMapOf<String, Any>()
        private val sets = mutableMapOf<String, Set<String>>()

        override fun contains(key: String): Boolean = values.containsKey(key) || sets.containsKey(key)
        override fun string(key: String): String? = values[key] as? String
        override fun boolean(key: String, fallback: Boolean): Boolean = values[key] as? Boolean ?: fallback
        override fun putString(key: String, value: String) { values[key] = value }
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key); sets.remove(key) }
        override fun stringSet(key: String): Set<String>? = sets[key]
        override fun putStringSet(key: String, value: Set<String>) { sets[key] = value.toSet() }
    }

    @Test
    fun `un favori pose se retrouve apres une relance`() {
        val store = MemoryStore()
        ModelPreferenceStore(store).setFavourite("server-a", "openai/gpt-5", true)

        // La « relance » : le magasin est relu **sur la même copie** — c'est ce que fait l'app au
        // démarrage suivant, et l'étoile est toujours là.
        val afterRestart = ModelPreferenceStore(store)
        assertEquals(setOf("openai/gpt-5"), afterRestart.favourites("server-a"))
        assertTrue(afterRestart.isFavourite("server-a", "openai/gpt-5"))

        // Un magasin neuf, sur une autre copie, n'a rien : les favoris sont bien ceux de l'appareil.
        assertTrue(ModelPreferenceStore(MemoryStore()).favourites("server-a").isEmpty())
    }

    @Test
    fun `un favori se retire et le fichier reste propre`() {
        val store = MemoryStore()
        val prefs = ModelPreferenceStore(store)
        prefs.setFavourite("server-a", "openai/gpt-5", true)
        prefs.setFavourite("server-a", "openai/gpt-5", false)
        assertTrue(prefs.favourites("server-a").isEmpty())
        assertFalse("la clé vide n'est pas gardée", store.contains(ModelKeys.favourites("server-a")))
    }

    @Test
    fun `les favoris sont propres a un serveur`() {
        val store = MemoryStore()
        val prefs = ModelPreferenceStore(store)
        prefs.setFavourite("server-a", "a/one", true)
        prefs.setFavourite("server-b", "b/two", true)
        assertEquals(setOf("a/one"), prefs.favourites("server-a"))
        assertEquals(setOf("b/two"), prefs.favourites("server-b"))
    }

    @Test
    fun `les recents gardent l ordre, sans doublon, et bornes`() {
        val store = MemoryStore()
        val prefs = ModelPreferenceStore(store)
        prefs.noteRecent("server-a", "one")
        prefs.noteRecent("server-a", "two")
        prefs.noteRecent("server-a", "one")
        assertEquals(listOf("one", "two"), prefs.recents("server-a"))

        for (index in 0 until 20) prefs.noteRecent("server-a", "model-$index")
        assertEquals(ModelKeys.RECENT_LIMIT, prefs.recents("server-a").size)
        assertEquals("model-19", prefs.recents("server-a").first())
    }

    @Test
    fun `un identifiant vide n est jamais garde`() {
        val store = MemoryStore()
        val prefs = ModelPreferenceStore(store)
        prefs.setFavourite("server-a", "   ", true)
        prefs.noteRecent("server-a", "")
        assertTrue(prefs.favourites("server-a").isEmpty())
        assertTrue(prefs.recents("server-a").isEmpty())
    }
}
