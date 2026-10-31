package com.onyx.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.onyx.app.data.repo.SettingsRepository
import com.onyx.app.localization.OnyxLanguage
import com.onyx.app.ui.design.ChoixCouverture
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **Le verrou du CHOIX de couverture : écrit AU GESTE, jamais semé au démarrage — et il survit.**
 *
 * Ce test tourne contre le vrai [SettingsRepository] (et compte ses écritures). Il **rougit** si
 *
 *  1. **le démarrage écrit quoi que ce soit** — en particulier s'il **sème** les trois clés du choix
 *     (`fluideVariante` / `fluideGraine` / `fluideTeinte`) au premier lancement. C'est la
 *     contradiction que le lot a tranchée : l'app ne décide pas à la place de l'utilisateur. Le
 *     verrou livré `AppearancePersistenceVerrouTest` dit la même chose pour le thème ; celui-ci le
 *     dit pour la **couverture** ;
 *  2. **le tap sur la variante (le geste) n'écrit pas le choix** — activer la couverture de fluide,
 *     c'est toucher la variante des filaments sur la planche : à cet instant, le choix
 *     (variante, graine, teinte) doit être enregistré ;
 *  3. **le choix ne survit pas** à la mort du processus ;
 *  4. **aucun choix enregistré ne rend pas le sien** : la carte doit prendre le défaut **visible**
 *     (filaments, graine `3417634283`, teinte bleu), **sans l'écrire**.
 */
class FluidePersistanceVerrouTest {

    @After
    fun remettreLaLangue() {
        OnyxLanguage.apply(OnyxLanguage.SYSTEM)
    }

    private fun cleVar(name: String) = stringPreferencesKey(name)
    private fun cleGraine(name: String) = longPreferencesKey(name)

    @Test
    fun `le demarrage n'ecrit RIEN et ne seme aucun choix, mais rend le choix visible`() = runBlocking {
        val store = FakePreferencesStore()
        // L'utilisateur a laissé un thème enregistré, l'app est arrêtée. Aucun choix de couverture.
        store.updateData { it.toMutablePreferences().apply { set(cleVar("appearance"), "dark") } }
        val avant = store.writes

        val settings = SettingsRepository(store)
        // Le démarrage tel quel : lecture d'avant la première image, puis `load()`.
        settings.applyStoredTheme()
        settings.applyStoredLanguage()
        settings.load()

        assertEquals("le démarrage ne doit RIEN écrire dans les réglages", 0, store.writes - avant)
        // Aucune clé de choix n'a été semée.
        assertEquals(null, store.state.value[cleGraine("fluideGraine")])
        assertEquals(null, store.state.value[cleVar("fluideVariante")])
        assertEquals(null, store.state.value[cleVar("fluideTeinte")])
        // …et pourtant la carte rend SON choix comme défaut visible : filaments, graine, teinte bleu.
        assertEquals(ChoixCouverture.DEFAUT, settings.fluideChoix.value)
        assertEquals("filaments", settings.fluideChoix.value.variante)
        assertEquals(3_417_634_283L, settings.fluideChoix.value.graine)
        assertEquals("bleu", settings.fluideChoix.value.teinte)
        assertEquals(3, settings.fluideChoix.value.teinteIndex)
    }

    @Test
    fun `le TAP sur la variante ecrit le choix, qui survit a la mort du processus`() = runBlocking {
        val store = FakePreferencesStore()
        val settings = SettingsRepository(store)
        settings.load()
        assertEquals("rien n'est écrit avant le geste", 0, store.writes)

        // **LE GESTE** : le tap sur la variante — activer la couverture de fluide, c'est choisir les
        // filaments. À cet instant, et à lui seul, le choix s'enregistre.
        settings.setFluideCover(true)
        assertEquals("le geste écrit le choix", "filaments", store.state.value[cleVar("fluideVariante")])
        assertEquals("le geste écrit la graine", 3_417_634_283L, store.state.value[cleGraine("fluideGraine")])
        assertEquals("le geste écrit la teinte", "bleu", store.state.value[cleVar("fluideTeinte")])

        // « Mort du processus » : un dépôt tout neuf sur le même magasin, puis le rechargement.
        val froid = SettingsRepository(store)
        froid.load()
        assertEquals("le choix du geste survit au rechargement", ChoixCouverture.DEFAUT, froid.fluideChoix.value)
        assertEquals(3_417_634_283L, froid.fluideChoix.value.graine)
        assertEquals("bleu", froid.fluideChoix.value.teinte)
    }

    @Test
    fun `un choix enregistre differe gagne sur le defaut, et n'est pas ecrase au demarrage`() = runBlocking {
        val store = FakePreferencesStore()
        // L'utilisateur avait choisi l'ambre, graine 123456780, une autre fois.
        store.updateData {
            it.toMutablePreferences().apply {
                set(cleVar("fluideVariante"), "filaments")
                set(cleGraine("fluideGraine"), 123_456_780L)
                set(cleVar("fluideTeinte"), "ambre")
            }
        }
        val avant = store.writes

        val settings = SettingsRepository(store)
        settings.applyStoredTheme()
        settings.load()

        assertEquals("le démarrage ne réécrit pas le choix de l'utilisateur", 0, store.writes - avant)
        assertEquals("le choix enregistré GAGNE", 123_456_780L, settings.fluideChoix.value.graine)
        assertEquals("ambre", settings.fluideChoix.value.teinte)
    }

    /** Un `DataStore` en mémoire qui **compte ses écritures** : c'est lui qui donne sa morsure. */
    private class FakePreferencesStore : DataStore<Preferences> {
        val state = MutableStateFlow(emptyPreferences())

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
