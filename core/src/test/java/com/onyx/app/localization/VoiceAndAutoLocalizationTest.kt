package com.onyx.app.localization

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceAndAutoLocalizationTest {
    @Test
    fun `les nouvelles vues ont leurs libelles francais et leur repli anglais`() {
        val cases = linkedMapOf(
            "Kokoro voices" to "Voix Kokoro",
            "Current voice" to "Voix actuelle",
            "Loading voices from Kokoro…" to "Chargement des voix depuis Kokoro…",
            "Stop preview" to "Arrêter l’extrait",
            "American English" to "Anglais américain",
            "female" to "féminin",
            "Say a test sentence" to "Dire une phrase de test",
            "Android Auto cards" to "Cartes Android Auto",
            "Android Auto voice screen" to "Écran vocal Android Auto",
            "New Chat" to "Nouveau chat",
            "One question" to "Une question",
            "Start fresh" to "Repartir de zéro",
            "Bars" to "Barres",
            "Lamps" to "Voyants",
            // Mode vocal solo : le réglage de l'appel à l'oreille et le nom de l'orbe.
            "Earpiece when raised to your ear" to "Écouteur quand on le lève à l’oreille",
            "Talk to %1\$s" to "Parler à %1\$s",
            // Le diagnostic d'écoute : « on n'entend rien », et l'action qui ouvre les réglages.
            "I can't hear anything — check your microphone." to "Je n'entends rien — vérifie le micro.",
            // Les deux autres causes d'un silence : le moteur qui n'a jamais répondu, et celui qui
            // tourne sans rendre un mot (le cas rapporté, où l'écran restait muet).
            "The device's speech recogniser never answered. Tap the orb to listen again." to
                "Le moteur de reconnaissance de l’appareil n’a pas répondu. Touchez l’orbe pour réécouter.",
            "The recogniser is running but finding no words. Tap the orb and try again." to
                "Le moteur de reconnaissance tourne mais ne trouve aucun mot. Touchez l’orbe et réessayez.",
            "Open Android settings" to "Ouvrir les réglages Android",
            // La phrase d'aide du mains libres se compose : le gabarit est traduit, et le mot de
            // liaison entre les phrases aussi.
            "Say %1\$s to end hands-free." to "Dites %1\$s pour quitter le mode mains libres.",
            " or " to " ou ",
        )
        cases.forEach { (english, french) ->
            assertEquals(french, OnyxLocalization.translate(english, Locale.FRANCE))
            assertEquals(english, OnyxLocalization.translate(english, Locale.ENGLISH))
        }
    }

    @Test
    fun `la phrase d aide du mains libres nomme les phrases vraiment ecoutees`() {
        // Les phrases réglées par la personne prennent la place des mots anglais de base, et le
        // gabarit se traduit sans traduire la liste (le nom des phrases reste tel quel).
        assertEquals(
            "Dites « arrête » ou « c'est tout » pour quitter le mode mains libres.",
            OnyxLocalization.format(
                "Say %1\$s to end hands-free.",
                listOf("arrête", "c'est tout").joinToString(OnyxLocalization.translate(" or ", Locale.FRANCE)) { "« $it »" },
                locale = Locale.FRANCE,
            ),
        )
        // Sans phrase réglée, ce sont celles de base qui sont nommées, et la phrase reste française.
        assertEquals(
            "Dites « stop listening » ou « that's all » pour quitter le mode mains libres.",
            OnyxLocalization.format(
                "Say %1\$s to end hands-free.",
                listOf("stop listening", "that's all").joinToString(OnyxLocalization.translate(" or ", Locale.FRANCE)) { "« $it »" },
                locale = Locale.FRANCE,
            ),
        )
        assertEquals(
            "Say « stop listening » or « that's all » to end hands-free.",
            OnyxLocalization.format(
                "Say %1\$s to end hands-free.",
                listOf("stop listening", "that's all").joinToString(OnyxLocalization.translate(" or ", Locale.ENGLISH)) { "« $it »" },
                locale = Locale.ENGLISH,
            ),
        )
    }

    @Test
    fun `les messages dynamiques ne traduisent pas les noms propres`() {
        assertEquals(
            "Écouter Heart",
            OnyxLocalization.translate("Preview Heart", Locale.FRANCE),
        )
        assertEquals(
            "Lu par Edge, la voix en ligne de Microsoft",
            OnyxLocalization.format(
                "Spoken by %1\$s",
                OnyxLocalization.translate("Edge, Microsoft's online voice", Locale.FRANCE),
                locale = Locale.FRANCE,
            ),
        )
        assertEquals(
            "Kokoro est injoignable : détail du serveur",
            OnyxLocalization.format("Couldn't reach Kokoro: %1\$s", "détail du serveur", locale = Locale.FRANCE),
        )
    }
}
