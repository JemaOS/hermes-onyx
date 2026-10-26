package com.onyx.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le choix de la voix d'un bot (`BotVoicePicker`): ce que la feuille montre, ce que dit
 * « Automatique », le résumé d'un choix, et la fiche qu'un appui écrit — la même résolution que la
 * lecture, donc rien qui ne se vérifie sans appareil.
 */
class BotVoicePickerTest {

    private val french = listOf(
        // La voix d'Onyx, puis de quoi donner une voix à chacun.
        SpokenVoice("fr-fr-x-frd-local", "fr-FR", quality = 1),
        SpokenVoice("fr-fr-x-fre-enhanced", "fr-FR", quality = 2),
        SpokenVoice("fr-fr-x-frf-local", "fr-FR", quality = 1),
        SpokenVoice("en-us-x-enf-local", "en-US", quality = 2),
        SpokenVoice("zarvox", "fr-FR", quality = 2, novelty = true),
    )

    private val kokoro = listOf("am_onyx", "am_adam", "bm_george", "ff_siwis")

    // MARK: - Les entrées offertes

    @Test
    fun `les voix de l appareil sont celles de la langue lue, les meilleures d abord`() {
        val choices = BotVoicePicker.choices(
            engine = ReplyVoice.ON_DEVICE,
            kokoroVoices = emptyList(),
            kokoroMain = "",
            mainName = "Hermes",
            installed = french,
            listening = "fr",
        )
        // Aucune autre langue, aucune voix de démonstration, et la qualité d'abord.
        assertEquals(
            listOf("fr-fr-x-fre-enhanced", "fr-fr-x-frd-local", "fr-fr-x-frf-local"),
            choices.map { it.id },
        )
        // Le détail est la qualité puis la langue, comme `Self.quality + " · " + language`.
        assertEquals("Enhanced · fr-FR", choices[0].detail)
        assertEquals("Default · fr-FR", choices[1].detail)
        // Le nom d'une voix Android est son identifiant: c'est lui qui se montre.
        assertEquals("fr-fr-x-fre-enhanced", choices[0].name)
        assertEquals(BotVoice(onDevice = "fr-fr-x-fre-enhanced"), choices[0].voice)
    }

    @Test
    fun `les voix Kokoro montrent la langue d Onyx d abord, la sienne dite en clair`() {
        val choices = BotVoicePicker.choices(
            engine = ReplyVoice.KOKORO,
            kokoroVoices = kokoro,
            kokoroMain = "am_onyx",
            mainName = "Hermes",
            installed = emptyList(),
            listening = "fr",
        )
        // L'américain (la langue d'Onyx) d'abord, par identifiant; le français ensuite.
        assertEquals(listOf("am_adam", "am_onyx", "bm_george", "ff_siwis"), choices.map { it.id })
        // La voix d'Onyx s'annonce comme la sienne; les autres, par leur identifiant.
        assertEquals("Hermes's voice", choices.first { it.id == "am_onyx" }.detail)
        assertEquals("am_adam", choices.first { it.id == "am_adam" }.detail)
        // Et son nom se lit: « Adam (American, male) ».
        assertEquals("Adam (American, male)", choices.first { it.id == "am_adam" }.name)
        assertEquals(BotVoice(kokoro = "bm_george"), choices.first { it.id == "bm_george" }.voice)
        // Rien de deux fois, même si le serveur se répète.
        assertEquals(kokoro.size, BotVoicePicker.choices(ReplyVoice.KOKORO, kokoro + kokoro, "am_onyx", "Hermes", emptyList(), "fr").size)
    }

    @Test
    fun `la voix d Hermes ne se regle pas par bot`() {
        assertTrue(
            BotVoicePicker.choices(ReplyVoice.HERMES, kokoro, "am_onyx", "Hermes", french, "fr").isEmpty(),
        )
    }

    // MARK: - Ce que « Automatique » donne

    @Test
    fun `automatique dit la voix que le bot recoit vraiment`() {
        val names = listOf("quill", "scout")
        val given = BotVoicePicker.automaticVoice(
            engine = ReplyVoice.ON_DEVICE,
            profile = "quill",
            others = emptyMap(),
            kokoroMain = "",
            kokoroVoices = emptyList(),
            installed = french,
            listening = "fr",
            deviceMain = "fr-fr-x-frd-local",
            names = names,
        )
        // Exactement ce que la lecture donnerait (`BotVoices`), pas une phrase à part.
        assertEquals(
            BotVoices.onDevice("quill", emptyMap(), "fr", "fr-fr-x-frd-local", french, names),
            given,
        )
        assertTrue("et une voix de la langue lue", given!!.startsWith("fr-fr"))
    }

    @Test
    fun `automatique ne promet rien quand il n y a rien a repartir`() {
        // Une seule voix installée, et c'est celle d'Onyx: le bot lit de la sienne.
        val only = listOf(SpokenVoice("fr-fr-x-frd-local", "fr-FR", quality = 1))
        assertNull(
            BotVoicePicker.automaticVoice(ReplyVoice.ON_DEVICE, "quill", emptyMap(), "", emptyList(), only, "fr", "fr-fr-x-frd-local", listOf("quill")),
        )
        // Sans liste du serveur, Kokoro ne promet rien non plus.
        assertNull(
            BotVoicePicker.automaticVoice(ReplyVoice.KOKORO, "quill", emptyMap(), "am_onyx", emptyList(), emptyList(), "fr", null, listOf("quill")),
        )
        // Et la voix d'Hermes n'est pas un choix.
        assertNull(
            BotVoicePicker.automaticVoice(ReplyVoice.HERMES, "quill", emptyMap(), "am_onyx", kokoro, french, "fr", null, listOf("quill")),
        )
    }

    // MARK: - Le résumé de la rangée « Voix »

    @Test
    fun `le resume d un choix dit la voix, puis sa vitesse`() {
        assertEquals("Automatic", BotVoicePicker.summary(null))
        assertEquals("Automatic · 1.40×", BotVoicePicker.summary(BotVoice(speed = 1.4f)))
        assertEquals("Same as the main agent", BotVoicePicker.summary(BotVoice(sameAsMain = true)))
        assertEquals("Adam (American, male)", BotVoicePicker.summary(BotVoice(kokoro = "am_adam")))
        assertEquals(
            "Adam (American, male) · 1.20×",
            BotVoicePicker.summary(BotVoice(kokoro = "am_adam", speed = 1.2f)),
        )
        assertEquals(
            "fr-fr-x-frf-local",
            BotVoicePicker.summary(BotVoice(onDevice = "fr-fr-x-frf-local")),
        )
    }

    // MARK: - Ce qu'un appui écrit

    @Test
    fun `un choix garde la vitesse reglee`() {
        val current = BotVoice(onDevice = "fr-fr-x-frf-local", speed = 1.4f)
        assertEquals(
            BotVoice(kokoro = "am_adam", speed = 1.4f),
            BotVoicePicker.withVoice(BotVoice(kokoro = "am_adam"), current),
        )
        // « Automatique » efface la voix et garde la vitesse.
        assertEquals(BotVoice(speed = 1.4f), BotVoicePicker.withVoice(null, current))
        // Sans vitesse, la fiche est vide: rien n'est gardé du tout.
        assertNull(BotVoicePicker.withVoice(null, BotVoice(onDevice = "x")))
    }

    @Test
    fun `la vitesse se regle sans perdre la voix`() {
        assertEquals(
            BotVoice(kokoro = "am_adam", speed = 1.05f),
            BotVoicePicker.withSpeed(1.05f, BotVoice(kokoro = "am_adam")),
        )
        // Elle seule compte: elle se garde même sans voix réglée.
        assertEquals(BotVoice(speed = 1.05f), BotVoicePicker.withSpeed(1.05f, null))
        // Et s'éteindre efface la fiche quand il n'y a plus rien à dire.
        assertNull(BotVoicePicker.withSpeed(null, null))
    }

    // MARK: - Les noms

    @Test
    fun `un identifiant Kokoro se nomme comme dans la reference`() {
        assertEquals("Adam (American, male)", BotVoicePicker.kokoroName("am_adam"))
        assertEquals("Siwis (French, female)", BotVoicePicker.kokoroName("ff_siwis"))
        assertEquals("George (British, male)", BotVoicePicker.kokoroName("bm_george"))
        // Tout ce qui n'a pas la forme `<langue><f|m>_<nom>` reste tel quel.
        assertEquals("echo", BotVoicePicker.kokoroName("echo"))
        assertEquals("zarvox", BotVoicePicker.kokoroName("zarvox"))
        assertEquals("", BotVoicePicker.kokoroName(""))
    }
}
