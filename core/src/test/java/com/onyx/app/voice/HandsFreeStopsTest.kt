package com.onyx.app.voice

import com.onyx.app.localization.OnyxLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test

/**
 * Les phrases d'arrêt citées à l'écran suivent la **langue de l'app**.
 *
 * L'erreur a été faite deux fois, et dans les deux sens : la paire anglaise s'affichait sous une
 * phrase d'aide française, puis la paire française s'est affichée sous une phrase d'aide anglaise.
 * Ce que l'app cite et ce que le moteur entend doivent être **la même chose**, dans la langue de
 * l'interface.
 */
class HandsFreeStopsTest {

    private val before = OnyxLanguage.choice

    @After
    fun restore() {
        OnyxLanguage.apply(before)
    }

    @Test
    fun `l app anglaise cite les phrases anglaises`() {
        OnyxLanguage.apply(OnyxLanguage.ENGLISH)

        assertEquals(listOf("stop listening", "that's all"), HandsFreeStops.cited)
        assertEquals("“stop listening” or “that's all”", HandsFreeStops.quoted())
    }

    @Test
    fun `l app francaise cite les phrases francaises`() {
        OnyxLanguage.apply(OnyxLanguage.FRENCH)

        assertEquals(listOf("arrête d'écouter", "c'est tout"), HandsFreeStops.cited)
        assertEquals("« arrête d'écouter » ou « c'est tout »", HandsFreeStops.quoted())
    }

    @Test
    fun `les deux paires sont entendues dans les deux langues`() {
        // Ce que l'app cite n'est qu'un échantillon : la liste complète est multilingue, et les
        // phrases de l'autre langue restent comprises.
        val heard = BUILT_IN_STOP_PHRASES.map { comparableStopPhrase(it) }.toSet()
        listOf(
            "arrête d'écouter", "c'est tout",
            "stop listening", "that's all",
            "stop", "that's enough", "goodbye", "never mind", "cancel", "thank you",
        ).forEach { phrase ->
            assertTrue("« $phrase » n'est pas entendue", comparableStopPhrase(phrase) in heard)
        }
    }

    @Test
    fun `les phrases de la personne sont citees dans la langue de l app`() {
        OnyxLanguage.apply(OnyxLanguage.ENGLISH)
        assertEquals("“stop” or “enough”", HandsFreeStops.quoted(listOf("stop", "enough")))

        OnyxLanguage.apply(OnyxLanguage.FRENCH)
        assertEquals("« stop » ou « enough »", HandsFreeStops.quoted(listOf("stop", "enough")))
    }
}
