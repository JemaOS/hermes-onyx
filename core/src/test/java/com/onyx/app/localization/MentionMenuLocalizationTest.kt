package com.onyx.app.localization

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Le libellé que lit un lecteur d'écran devant la liste des mentions `@` du composeur
 * (`MentionSuggestions`, `ComposerView.mentionMenu` : « Bots to mention »).
 */
class MentionMenuLocalizationTest {

    @Test
    fun `la liste des mentions est nommee dans la langue du systeme`() {
        assertEquals("Bots à mentionner", OnyxLocalization.translate("Bots to mention", Locale.FRANCE))
        assertEquals("Bots à mentionner", OnyxLocalization.translate("Bots to mention", Locale.CANADA_FRENCH))
        // Anglais, et toute autre langue : le libellé d'origine, sans changement.
        assertEquals("Bots to mention", OnyxLocalization.translate("Bots to mention", Locale.ENGLISH))
        assertEquals("Bots to mention", OnyxLocalization.translate("Bots to mention", Locale.JAPAN))
    }

    @Test
    fun `une poignee de bot reste verbatim`() {
        // Le libellé d'une ligne est un identifiant : seul le nom du bot s'affiche, jamais une
        // traduction qui le rendrait méconnaissable.
        assertEquals("@scout", OnyxLocalization.translate("@scout", Locale.FRANCE))
        assertEquals("@fix-it", OnyxLocalization.translate("@fix-it", Locale.FRANCE))
    }
}
