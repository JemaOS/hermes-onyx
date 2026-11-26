package com.onyx.app.data.local

import com.onyx.app.data.model.serve.BotProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le dernier roster connu, gardé sur le téléphone — par connexion, et avec les visages
 * effectifs.**
 *
 * C'est ce qui empêche l'en-tête de montrer Onyx (visage bleu, « @default ») le temps que le
 * réseau réponde : au redémarrage, le roster est relu ici, sans attendre.
 *
 * Ce test tient les trois bouts, parce qu'il suffit d'un seul pour perdre le vrai bot :
 * 1. la **portée** — deux serveurs ne partagent jamais leurs bots ;
 * 2. le **visage effectif** — ce qui est gardé est le code *rendu*, pas le code brut ;
 * 3. la **tenue** — un roster vide n'efface rien, un contenu illisible ne fait pas tomber l'app.
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

    /** La connexion A et la connexion B : deux serveurs, deux portées. */
    private val serveurA = "HERMES_SERVE|http://10.0.2.2:8890|A"
    private val serveurB = "HERMES_SERVE|http://10.0.2.2:9119|B"

    @Test
    fun `le roster enregistre se relit tel quel`() {
        val memory = Memory()
        BotRosterCache(memory).save(serveurA, CachedRoster(listOf(coder, main), emptyMap()))

        val read = BotRosterCache(memory).load(serveurA)

        assertNotNull("le roster doit se relire", read)
        assertEquals(listOf("coder", "default"), read!!.bots.map { it.name })
        assertEquals("Coder", read.bots[0].title)
        assertEquals("sess-coder", read.bots[0].chatId)
        assertEquals("2:round:round:smile:none:0:0:0", read.bots[0].faceCode)
        // Le profil principal doit rester principal : c'est lui qui porte le visage d'Onyx.
        assertTrue(read.bots[1].isDefault)
        assertTrue(read.bots[1].viaAPI)
        assertTrue(!read.bots[0].isDefault)
    }

    @Test
    fun `deux serveurs ne se partagent jamais leurs bots`() {
        // La contamination d'identité : le roster de l'un affiché pour l'autre, c'est un bot qui
        // n'existe pas sur ce serveur — et un visage qui n'est pas à lui.
        val memory = Memory()
        val cache = BotRosterCache(memory)
        cache.save(serveurA, CachedRoster(listOf(coder), mapOf("coder" to "2:round:round:smile:none:0:0:0")))
        cache.save(serveurB, CachedRoster(listOf(main), mapOf("default" to "1:bubble:round:none:none:0:0:0")))

        val a = cache.load(serveurA)
        val b = cache.load(serveurB)

        assertEquals(listOf("coder"), a?.bots?.map { it.name })
        assertEquals(listOf("default"), b?.bots?.map { it.name })
        assertEquals("2:round:round:smile:none:0:0:0", a?.faces?.get("coder"))
        assertEquals("1:bubble:round:none:none:0:0:0", b?.faces?.get("default"))
        // Et l'un ne trouve rien chez l'autre.
        assertNull(cache.load("HERMES_SERVE|http://10.0.2.2:9999|C"))
    }

    @Test
    fun `le visage effectif garde est celui qui a ete rendu`() {
        // Ce qui est garde n'est pas le code brut du serveur : c'est le code *résolu*, celui que
        // la répartition du roster a attribué. Sans lui, le bot retrouverait au redémarrage une
        // autre créature que celle d'hier.
        val memory = Memory()
        val effectif = "1:round:round:smile:none:6:1:2"
        BotRosterCache(memory).save(serveurA, CachedRoster(listOf(coder), mapOf("coder" to effectif)))

        val read = BotRosterCache(memory).load(serveurA)

        assertEquals(effectif, read?.faces?.get("coder"))
        assertEquals(effectif, read?.bots?.firstOrNull { it.name == "coder" }?.faceCode)
    }

    @Test
    fun `un roster vide n efface pas le dernier connu`() {
        // Sans serveur (hors ligne, ou Dashboard muet), la lecture rend une liste vide — et cette
        // liste vide ne doit pas effacer ce que le téléphone savait déjà.
        val memory = Memory()
        val cache = BotRosterCache(memory)
        cache.save(serveurA, CachedRoster(listOf(coder), emptyMap()))
        cache.save(serveurA, CachedRoster(emptyList(), emptyMap()))

        assertEquals(listOf("coder"), cache.load(serveurA)?.bots?.map { it.name })
    }

    @Test
    fun `un contenu illisible ne fait pas tomber l app`() {
        // Fichier tronqué, version d'avant, écriture à moitié : on rend rien, jamais une
        // exception au démarrage.
        val memory = Memory()
        memory.put("rosters", "{ceci n'est pas du json")
        assertNull(BotRosterCache(memory).load(serveurA))

        memory.put("rosters", """{"A": {"bots": [{"title":"sans nom"},{"name":""},{"name":"coder"}]}}""")
        assertEquals(listOf("coder"), BotRosterCache(memory).load("A")?.bots?.map { it.name })
    }

    @Test
    fun `un roster jamais enregistre se relit comme absent`() {
        // `null` et une liste vide ne se confondent pas : le vide se distingue, parce qu'il ne
        // faut pas effacer ce qui est su.
        assertNull(BotRosterCache(Memory()).load(serveurA))
    }

    @Test
    fun `tout efface laisse plus de roster`() {
        // « Erase everything » : plus aucun roster gardé, pour aucune connexion.
        val memory = Memory()
        val cache = BotRosterCache(memory)
        cache.save(serveurA, CachedRoster(listOf(coder), emptyMap()))
        cache.save(serveurB, CachedRoster(listOf(main), emptyMap()))

        cache.clearAll()

        assertNull(cache.load(serveurA))
        assertNull(cache.load(serveurB))
    }
}
