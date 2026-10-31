package com.onyx.app.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * La langue de l'app du lien « tout en un ».
 *
 * Un code de lien peut dire `lang=fr` : l'app pose alors la locale. Le champ est le **même
 * stockage** que le réglage Langue (Système / Français / English) — un lien ne crée pas un second
 * réglage, il écrit celui-là. `""` veut dire Système : on ne touche pas à la locale du téléphone.
 */
class AppLanguageTest {

    @Test
    fun `systeme ne touche pas a la locale du telephone`() {
        assertNull(SettingsRepository.localeFor(""))
        assertNull(SettingsRepository.localeFor("   "))
        assertNull(SettingsRepository.localeFor("-"))
    }

    @Test
    fun `un code court donne sa langue`() {
        assertEquals("fr", SettingsRepository.localeFor("fr")?.language)
        assertEquals("en", SettingsRepository.localeFor("en-US")?.language)
        assertEquals("fr", SettingsRepository.localeFor(" FR ")?.language)
    }

    @Test
    fun `les trois choix du reglage existent`() {
        assertEquals(listOf("", "fr", "en"), SettingsRepository.APP_LANGUAGES.map { it.first })
    }
}
