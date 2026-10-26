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
            // Mode vocal solo: le réglage de l'appel à l'oreille et le nom de l'orbe.
            "Earpiece when raised to your ear" to "Écouteur quand on le lève à l’oreille",
            "Talk to %1\$s" to "Parler à %1\$s",
        )
        cases.forEach { (english, french) ->
            assertEquals(french, OnyxLocalization.translate(english, Locale.FRANCE))
            assertEquals(english, OnyxLocalization.translate(english, Locale.ENGLISH))
        }
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
