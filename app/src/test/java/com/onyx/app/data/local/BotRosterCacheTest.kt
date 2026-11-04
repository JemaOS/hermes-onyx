package com.onyx.app.data.local

import com.onyx.app.data.model.serve.BotProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le dernier roster connu, gardé sur le téléphone.**
 *
 * C'est ce qui empêche l'en-tête de montrer Onyx (visage bleu, « @default ») le temps que le réseau
 * réponde : au redémarrage, la liste des bots est relue ici, sans attendre.
 */
class BotRosterCacheTest {

    private class Memory : TextStore {
        val values = mutableMapOf<String, String>()
        override fun get(key: String): String? = values[key]
        override fun put(key: String, value: String) {
            values[key] = value
        }
    }

    private val coder = BotProfile(
        name = "coder",
        title = "Coder",
        chatId = "sess-coder",
        faceCode = "2:round:round:smile:none:0:0:0",
    )
    private val main = BotProfile(name = "default", title = "Onyx", isDefault = true, viaAPI = true)

    @Test
    fun `le roster enregistre se relit tel quel`() {
        val memory = Memory()
        BotRosterCache(memory).save(listOf(coder, main))

        val read = BotRosterCache(memory).load()

        assertEquals(listOf("coder", "default"), read.map { it.name })
        assertEquals("Coder", read[0].title)
        assertEquals("sess-coder", read[0].chatId)
        assertEquals("2:round:round:smile:none:0:0:0", read[0].faceCode)
        // Le profil principal doit rester principal : c'est lui qui porte le visage d'Onyx.
        assertTrue(read[1].isDefault)
        assertTrue(read[1].viaAPI)
        assertFalse(read[0].isDefault)
    }

    @Test
    fun `un roster vide n efface pas le dernier connu`() {
        // Sans serveur (hors ligne, ou Dashboard muet), la lecture rend une liste vide — et cette
        // liste vide ne doit pas effacer ce que le téléphone savait déjà.
        val memory = Memory()
        BotRosterCache(memory).save(listOf(coder))
        BotRosterCache(memory).save(emptyList())

        assertEquals(listOf("coder"), BotRosterCache(memory).load().map { it.name })
    }

    @Test
    fun `un contenu illisible ne fait pas tomber l app`() {
        // Fichier tronqué, version d'avant, écriture à moitié : on rend une liste vide, jamais une
        // exception au démarrage.
        val memory = Memory()
        memory.put("roster", "{ceci n'est pas du json")
        assertTrue(BotRosterCache(memory).load().isEmpty())

        memory.put("roster", """[{"title":"sans nom"},{"name":""},{"name":"coder"}]""")
        assertEquals(listOf("coder"), BotRosterCache(memory).load().map { it.name })
    }
}
