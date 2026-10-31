package com.onyx.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.onyx.app.data.repo.SettingsRepository
import com.onyx.app.localization.OnyxLanguage
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La langue de l'interface : le défaut reste « Système », le choix est persisté et relu, et il est
 * appliqué **sans** que la langue d'Android change.
 */
class LanguageSettingPersistenceTest {

    @After
    fun remettreLeChoix() {
        OnyxLanguage.apply(OnyxLanguage.SYSTEM)
    }

    @Test
    fun `le defaut est Systeme, la langue d Android telle quelle`() = runBlocking {
        val settings = SettingsRepository(FakePreferencesStore())
        settings.load()
        assertEquals(OnyxLanguage.SYSTEM, settings.language.value)
        assertEquals(Locale.getDefault(), OnyxLanguage.preferred)
    }

    @Test
    fun `le choix s applique aussitot et survit au redemarrage`() = runBlocking {
        val androidAUjourdHui = Locale.getDefault()
        val store = FakePreferencesStore()
        val settings = SettingsRepository(store)
        settings.load()

        settings.setLanguage("en")
        assertEquals("en", settings.language.value)
        assertEquals(OnyxLanguage.ENGLISH, OnyxLanguage.choice)
        assertEquals(Locale.ENGLISH, OnyxLanguage.preferred)
        // Android n'est pas touché : le choix ne se propage pas aux autres éléments du système.
        assertEquals(androidAUjourdHui, Locale.getDefault())

        // « Redémarrage » : un dépôt neuf sur le même magasin, comme au lancement du processus.
        OnyxLanguage.apply(OnyxLanguage.SYSTEM)
        val relu = SettingsRepository(store)
        relu.load()
        assertEquals("en", relu.language.value)
        assertEquals(Locale.ENGLISH, OnyxLanguage.preferred)

        // La langue d'un lancement à froid, posée avant la première composition.
        OnyxLanguage.apply(OnyxLanguage.SYSTEM)
        val demarrage = SettingsRepository(store)
        demarrage.applyStoredLanguage()
        assertEquals(OnyxLanguage.ENGLISH, OnyxLanguage.choice)

        // Retour à « Système » : exactement le comportement d'avant.
        relu.setLanguage("system")
        assertEquals(OnyxLanguage.SYSTEM, relu.language.value)
        assertEquals(androidAUjourdHui, Locale.getDefault())
        assertEquals(Locale.getDefault(), OnyxLanguage.preferred)

        // Un choix inconnu (une valeur qu'une version aurait écrite autrement) retombe sur
        // « Système » plutôt que de laisser l'app sans langue.
        relu.setLanguage("klingon")
        assertEquals(OnyxLanguage.SYSTEM, relu.language.value)
        assertEquals(Locale.getDefault(), OnyxLanguage.preferred)

        assertEquals(androidAUjourdHui, Locale.getDefault())
    }

    @Test
    fun `une remise a zero des reglages ramene aussi la langue du systeme`() = runBlocking {
        val store = FakePreferencesStore()
        val settings = SettingsRepository(store)
        settings.load()
        settings.setLanguage("fr")
        assertEquals(Locale.FRENCH, OnyxLanguage.preferred)

        settings.reset()
        assertEquals(OnyxLanguage.SYSTEM, settings.language.value)
        assertEquals(Locale.getDefault(), OnyxLanguage.preferred)
    }

    private class FakePreferencesStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
