package com.onyx.app.ui.cron

import com.onyx.app.localization.OnyxLocalization
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **L'écran des horaires d'un bot parle français.**
 *
 * Relevé sur la capture du propriétaire : « Nouvel horaire » était bien traduit — NOM, QUAND,
 * Répéter, En semaine, À — mais l'en-tête de section disait **« WHAT CODER DOES »**, et deux
 * explications de la même famille portaient le nom du bot en anglais.
 *
 * Ces quatre textes **composent le nom du bot** : ils ne peuvent donc pas être des clés fixes du
 * dictionnaire. Ils sont tenus par des motifs, et ce test les éprouve avec un nom réel — c'est la
 * seule façon de vérifier qu'un motif attrape bien la phrase entière, ponctuation comprise.
 */
class ScheduleCopyLocalizationTest {

    private val screen = File("src/main/java/com/onyx/app/ui/cron/BotSchedulesScreen.kt").readText()

    private fun fr(text: String) = OnyxLocalization.translate(text, Locale.FRANCE)

    @Test
    fun `ce que fait le bot est traduit`() {
        assertEquals("Ce que fait coder", fr("What coder does"))
    }

    @Test
    fun `le titre de la liste des horaires est traduit`() {
        assertEquals("Horaires de coder", fr("coder's Schedules"))
    }

    @Test
    fun `les deux explications qui portent le nom du bot sont traduites`() {
        val empty =
            "Give coder something to do at a time: every weekday at 7, check the weather and your " +
                "calendar and say what to wear. Its answer comes into its chat."
        val footer =
            "Each run is coder's, with its own personality and tools, and its answer comes into " +
                "its chat, with a notification, while your phone is off. Times are your server's. " +
                "Hermes 0.21.5 and later run a bot's schedules by themselves; earlier ones only " +
                "with gateway.multiplex_profiles on."

        listOf(empty, footer).forEach { text ->
            val shown = fr(text)
            assertNotEquals("« $text » reste en anglais", text, shown)
            assertTrue("le nom du bot est gardé : $shown", shown.contains("coder"))
        }
    }

    @Test
    fun `l ecran compose bien ces quatre textes`() {
        assertTrue(screen.contains("header = \"What \$botName does\","))
        assertTrue(screen.contains("OnyxPageHeader(title = \"\$name's Schedules\""))
        assertTrue(screen.contains("description = \"Give \$name something to do at a time:"))
        assertTrue(screen.contains("footer = \"Each run is \$name's,"))
    }
}
