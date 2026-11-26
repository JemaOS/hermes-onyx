package com.onyx.app

import com.onyx.app.localization.OnyxLanguage
import com.onyx.app.ui.chat.components.attachmentSizeLabel
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Les nombres montrés à côté d'un fichier joint suivent la langue de l'app, pas celle d'Android :
 * le séparateur décimal était figé en français (« 12,4 Mo » sur un écran anglais).
 */
class NumberFormatLanguageTest {

    @After
    fun remettreLeChoix() {
        OnyxLanguage.apply(OnyxLanguage.SYSTEM)
    }

    @Test
    fun `le poids d une piece jointe suit la langue choisie`() {
        val androidAUjourdHui = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            OnyxLanguage.apply("fr")
            assertEquals("1,5 Mo", attachmentSizeLabel(1_480_000))
            OnyxLanguage.apply("en")
            assertEquals("1.5 Mo", attachmentSizeLabel(1_480_000))
            OnyxLanguage.apply("system")
            assertEquals("1.5 Mo", attachmentSizeLabel(1_480_000))
        } finally {
            OnyxLanguage.apply(OnyxLanguage.SYSTEM)
            Locale.setDefault(androidAUjourdHui)
        }
    }
}
