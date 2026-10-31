package com.onyx.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.onyx.app.data.repo.SettingsRepository
import com.onyx.app.localization.OnyxLanguage
import com.onyx.app.ui.theme.AppearanceMode
import com.onyx.app.ui.theme.OnyxTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **Le verrou du thème : le choix de l'utilisateur survit au démarrage, et rien ne l'écrase.**
 *
 * Ce test mord sur deux propriétés à la fois :
 *
 * 1. **Aucune écriture n'est faite au démarrage sans action de l'utilisateur.** Le magasin compte
 *    ses `updateData` ; `load()`, `applyStoredLanguage()` et `applyStoredTheme()` doivent en faire
 *    **zéro**. Réintroduire une écriture de thème à l'initialisation (le défaut écrit après la
 *    lecture, la « migration » qui réinitialise, le `setAppearance` de démarrage) rend ce test
 *    **rouge** : le magasin porterait alors `appearance=light` là où l'utilisateur avait choisi
 *    `dark` — exactement le défaut qui faisait rouvrir l'app en clair.
 *
 * 2. **Le thème enregistré est posé avant la première image.** `applyStoredTheme()` — appelé par
 *    `AppContainer.install` dans le même `runBlocking` que `applyStoredLanguage()` — doit rendre le
 *    bon mode **sans** que `load()` ait tourné. Sans lui, `OnyxAppTheme` composerait avec le défaut
 *    `AppearanceMode.SYSTEM`, donc le **clair** sur un téléphone en clair.
 */
class AppearancePersistenceVerrouTest {

    @After
    fun remettreLaLangue() {
        OnyxLanguage.apply(OnyxLanguage.SYSTEM)
    }

    private fun key(name: String) = stringPreferencesKey(name)

    @Test
    fun `le choix du theme survit a la mort du processus et au rechargement`() = runBlocking {
        AppearanceMode.entries.forEach { mode ->
            val store = FakePreferencesStore()
            val settings = SettingsRepository(store)
            // L'utilisateur choisit, comme depuis les Réglages.
            settings.applyStoredTheme()
            settings.setAppearance(mode)

            // « Mort du processus » : un dépôt tout neuf sur le même magasin, puis la lecture
            // synchrone d'avant la première image, exactement comme `AppContainer.install`.
            val froid = SettingsRepository(store)
            froid.applyStoredTheme()
            assertEquals("le mode « ${mode.id} » doit être posé avant la première image", mode, froid.appearance.value)

            // Et la lecture complète (celle de `load()`, asynchrone) dit la même chose.
            froid.load()
            assertEquals("le mode « ${mode.id} » doit survivre au rechargement", mode, froid.appearance.value)
        }
    }

    @Test
    fun `l'apparence et le theme enregistres survivent a une fermeture complete`() = runBlocking {
        val store = FakePreferencesStore()
        val settings = SettingsRepository(store)
        settings.applyStoredTheme()
        settings.setAppearance(AppearanceMode.DARK)
        settings.setTheme(OnyxTheme.GITHUB_DARK)

        // Le magasin porte bien le choix, et rien d'autre que le choix.
        assertEquals("dark", store.state.value[key("appearance")])
        assertEquals("githubDark", store.state.value[key("theme")])

        // Nouveau processus : le dépôt relit le disque et retrouve les deux choix.
        val relu = SettingsRepository(store)
        relu.load()
        assertEquals(AppearanceMode.DARK, relu.appearance.value)
        assertEquals(OnyxTheme.GITHUB_DARK, relu.theme.value)
    }

    @Test
    fun `aucune ecriture de theme au demarrage sans action de l'utilisateur`() = runBlocking {
        val store = FakePreferencesStore()
        // L'utilisateur a laissé le sombre dans le magasin, l'app est arrêtée.
        store.updateData { it.toMutablePreferences().apply { set(key("appearance"), "dark") } }
        val avant = store.writes

        val settings = SettingsRepository(store)
        // Le démarrage tel quel : lecture synchrone d'avant la première image, puis `load()`.
        settings.applyStoredTheme()
        settings.applyStoredLanguage()
        settings.load()

        assertEquals(
            "le démarrage ne doit RIEN écrire dans les réglages",
            0,
            store.writes - avant,
        )
        assertEquals("le choix de l'utilisateur est intact", "dark", store.state.value[key("appearance")])
        assertEquals(AppearanceMode.DARK, settings.appearance.value)
    }

    @Test
    fun `le theme enregistre est pose avant la premiere image, sans attendre load`() = runBlocking {
        val store = FakePreferencesStore()
        store.updateData {
            it.toMutablePreferences().apply {
                set(key("appearance"), "dark")
                set(key("theme"), "paper")
            }
        }

        val settings = SettingsRepository(store)
        // **Seulement** la lecture d'avant composition : `load()` n'a pas tourné.
        settings.applyStoredTheme()

        assertEquals(AppearanceMode.DARK, settings.appearance.value)
        assertEquals(OnyxTheme.PAPER, settings.theme.value)
    }

    @Test
    fun `un magasin sans choix retombe sur le defaut, sans ecrire`() = runBlocking {
        val store = FakePreferencesStore()
        val settings = SettingsRepository(store)
        settings.applyStoredTheme()
        settings.load()

        assertEquals(AppearanceMode.SYSTEM, settings.appearance.value)
        assertEquals(OnyxTheme.STANDARD, settings.theme.value)
        assertEquals("un magasin vide n'est pas rempli pour autant", 0, store.writes)
        assertEquals(null, store.state.value[key("appearance")])
    }

    /** Un `DataStore` en mémoire qui **compte ses écritures** : c'est lui qui donne sa morsure au verrou. */
    private class FakePreferencesStore : DataStore<Preferences> {
        val state = MutableStateFlow(emptyPreferences())

        /** Nombre d'appels à [updateData] : toute écriture des réglages passe par là. */
        var writes: Int = 0
            private set

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            writes += 1
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
