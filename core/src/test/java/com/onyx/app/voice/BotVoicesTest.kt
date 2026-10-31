package com.onyx.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La voix de chaque bot : celle qui lui a été réglée, sinon une des siennes, **jamais deux fois la
 * même** tant qu'il y a des voix à répartir, et jamais une hauteur inventée — c'est la voix du bot
 * qui décide (`BotVoices` de la référence, `Echo/Services/Voice/BotVoices`).
 */
class BotVoicesTest {

    private val french = listOf(
        // La voix d'Onyx, puis de quoi donner un voix à chacun.
        SpokenVoice("fr-fr-x-frd-local", "fr-FR", quality = 1),
        SpokenVoice("fr-fr-x-fre-enhanced", "fr-FR", quality = 2),
        SpokenVoice("fr-fr-x-frf-local", "fr-FR", quality = 1),
        SpokenVoice("fr-fr-x-fra-network", "fr-FR", quality = 2),
        SpokenVoice("en-us-x-enf-local", "en-US", quality = 2),
    )

    @Test
    fun `un bot n a jamais la voix d Onyx`() {
        val voice = BotVoices.onDevice("quill", emptyMap(), "fr", "fr-fr-x-frd-local", french, listOf("quill"))
        assertNotEquals("fr-fr-x-frd-local", voice)
        assertTrue("la voix vient bien de la langue lue", voice!!.startsWith("fr-fr"))
    }

    @Test
    fun `deux bots ne lisent pas de la meme voix`() {
        val names = listOf("onyx", "quill", "scout")
        val voices = names.map { name ->
            BotVoices.onDevice(name, emptyMap(), "fr", "fr-fr-x-frd-local", french, names)
        }
        assertTrue(voices.all { it != null })
        assertEquals("aucune voix partagée", voices.size, voices.toSet().size)
    }

    @Test
    fun `la meme salle donne toujours la meme voix`() {
        val first = BotVoices.onDevice("quill", emptyMap(), "fr", "fr-fr-x-frd-local", french, listOf("quill", "scout"))
        val second = BotVoices.onDevice("quill", emptyMap(), "fr", "fr-fr-x-frd-local", french, listOf("quill", "scout"))
        assertEquals(first, second)
    }

    @Test
    fun `les meilleures voix installees passent avant les autres`() {
        val names = listOf("quill", "scout")
        val chosen = names.map { BotVoices.onDevice(it, emptyMap(), "fr", null, french, names) }
        val best = french.filter { it.quality >= 2 }.map { it.id }
        assertTrue("les voix « enhanced » partent les premières", chosen.all { it in best })
    }

    @Test
    fun `une voix reglee pour un bot l emporte`() {
        val picked = mapOf("quill" to BotVoice(onDevice = "fr-fr-x-frf-local"))
        assertEquals(
            "fr-fr-x-frf-local",
            BotVoices.onDevice("quill", picked, "fr", "fr-fr-x-frd-local", french, listOf("quill")),
        )
    }

    @Test
    fun `une voix reglee qui n est plus installee laisse le bot reprendre la sienne`() {
        val picked = mapOf("quill" to BotVoice(onDevice = "voix-disparue"))
        val voice = BotVoices.onDevice("quill", picked, "fr", null, french, listOf("quill"))
        assertTrue(voice!!.startsWith("fr-fr"))
        assertNotEquals("voix-disparue", voice)
    }

    @Test
    fun `un bot regle sur la voix d Onyx lit de la voix d Onyx`() {
        val picked = mapOf("quill" to BotVoice(sameAsMain = true))
        assertNull(BotVoices.onDevice("quill", picked, "fr", null, french, listOf("quill")))
        assertNull(BotVoices.kokoro("quill", picked, "am_onyx", listOf("am_onyx", "am_adam")))
    }

    @Test
    fun `le principal et un profil sans nom gardent la voix d Onyx`() {
        assertNull(BotVoices.onDevice("default", emptyMap(), "fr", null, french, listOf("default")))
        assertNull(BotVoices.onDevice(null, emptyMap(), "fr", null, french, emptyList()))
        assertNull(BotVoices.onDevice("", emptyMap(), "fr", null, french, emptyList()))
    }

    @Test
    fun `une voix de demonstration n est jamais donnee a un bot`() {
        val installed = listOf(
            SpokenVoice("fr-fr-x-frd-local", "fr-FR", quality = 1),
            SpokenVoice("zarvox", "fr-FR", quality = 2, novelty = true),
        )
        assertNull(BotVoices.onDevice("quill", emptyMap(), "fr", "fr-fr-x-frd-local", installed, listOf("quill")))
    }

    // MARK: - Kokoro

    private val kokoro = listOf("am_onyx", "am_adam", "am_michael", "bm_george", "ff_siwis")

    @Test
    fun `les voix Kokoro d un bot suivent la langue de la voix principale`() {
        val picked = emptyMap<String, BotVoice>()
        val names = listOf("quill", "scout")
        val chosen = names.map { BotVoices.kokoro(it, picked, "am_onyx", kokoro, names) }
        assertTrue("jamais celle d'Onyx", chosen.none { it == "am_onyx" })
        assertEquals("deux voix différentes", chosen.size, chosen.toSet().size)
        assertTrue("dans la langue d'Onyx, l'américain", chosen.all { BotVoices.kokoroLanguage(it!!) == "en" })
    }

    @Test
    fun `sans liste du serveur, aucune voix n est promise`() {
        assertNull(BotVoices.kokoro("quill", emptyMap(), "am_onyx", emptyList()))
    }

    @Test
    fun `une voix Kokoro reglee l emporte, et une voix disparue retombe sur la repartition`() {
        val picked = mapOf("quill" to BotVoice(kokoro = "bm_george"))
        assertEquals("bm_george", BotVoices.kokoro("quill", picked, "am_onyx", kokoro))
        val gone = mapOf("quill" to BotVoice(kokoro = "voix-inconnue"))
        val voice = BotVoices.kokoro("quill", gone, "am_onyx", kokoro)
        assertTrue(voice != null && voice != "voix_inconnue")
    }

    @Test
    fun `une voix Kokoro non identifiee ne dit pas sa langue`() {
        assertNull(BotVoices.kokoroLanguage("echo"))
        // Un mélange : c'est la **première** voix qui donne la langue, comme la référence.
        assertEquals("en", BotVoices.kokoroLanguage("am_onyx(2)+bm_george(1)"))
        assertEquals("fr", BotVoices.kokoroLanguage("ff_siwis"))
        assertEquals("en", BotVoices.kokoroLanguage("bm_george"))
    }

    @Test
    fun `la langue de base se lit d un code comme d un identifiant`() {
        assertEquals("fr", BotVoices.base("fr-FR"))
        assertEquals("en", BotVoices.base("en_US"))
        assertEquals("", BotVoices.base(""))
    }

    // MARK: - Vitesse et empreinte

    @Test
    fun `la vitesse d un bot est la sienne, sinon celle d Onyx`() {
        assertEquals(1.0f, BotVoices.speed("quill", emptyMap(), 1.0f), 0.0001f)
        assertEquals(1.4f, BotVoices.speed("quill", mapOf("quill" to BotVoice(speed = 1.4f)), 1.0f), 0.0001f)
        assertEquals(
            "une vitesse nulle ou négative ne compte pas",
            1.0f,
            BotVoices.speed("quill", mapOf("quill" to BotVoice(speed = 0f)), 1.0f),
            0.0001f,
        )
        assertEquals("le principal garde la vitesse d'Onyx", 1.0f, BotVoices.speed("default", emptyMap(), 1.0f), 0.0001f)
    }

    @Test
    fun `l empreinte d un nom est la FNV-1a de la référence`() {
        assertEquals(0xcbf29ce484222325uL, BotVoices.hash(""))
        // Le même algorithme que `BotFace.forName` : les octets UTF-8, un par un.
        assertEquals(BotVoices.hash("quill"), BotVoices.hash("quill"))
        assertNotEquals(BotVoices.hash("quill"), BotVoices.hash("scout"))
    }

    @Test
    fun `une fiche vide n est pas un reglage`() {
        assertTrue(BotVoice().isAutomatic)
        assertTrue(BotVoice().isEmpty)
        assertTrue(BotVoice(speed = 1.2f).isAutomatic)
        assertFalse(BotVoice(speed = 1.2f).isEmpty)
        assertFalse(BotVoice(kokoro = "am_adam").isAutomatic)
        assertFalse(BotVoice(sameAsMain = true).isAutomatic)
    }
}
