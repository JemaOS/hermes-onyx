package com.onyx.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.onyx.app.data.model.serve.InsightsPeriod
import com.onyx.app.data.repo.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La période des « Aperçus » est **mémorisée**.
 *
 * Le propriétaire regarde les 90 jours ; rouvrir l'écran doit le retrouver sur les 90 jours, pas le
 * ramener au mois par défaut. C'est un réglage de l'appareil — écrit dans le magasin local, jamais
 * envoyé au serveur — et il survit à une relance du processus : le second dépôt est construit sur le
 * **même** magasin, comme au lancement suivant.
 */
class InsightsPeriodPersistenceTest {

    @Test
    fun `la periode par defaut est le mois`() = runBlocking {
        val settings = SettingsRepository(FakePreferencesStore())
        settings.load()

        assertEquals(InsightsPeriod.MONTH, settings.insightsPeriod.value)
    }

    @Test
    fun `le choix de periode survit a une relance`() = runBlocking {
        val store = FakePreferencesStore()
        val settings = SettingsRepository(store)
        settings.load()

        settings.setInsightsPeriod(InsightsPeriod.QUARTER)
        assertEquals(InsightsPeriod.QUARTER, settings.insightsPeriod.value)

        // « Relance » : un dépôt neuf sur le même magasin, comme au lancement du processus.
        val relu = SettingsRepository(store)
        relu.load()
        assertEquals(
            "les 90 jours sont retrouvés au lancement suivant",
            InsightsPeriod.QUARTER,
            relu.insightsPeriod.value,
        )

        // Et changer de période se mémorise aussi : le dernier choix l'emporte.
        relu.setInsightsPeriod(InsightsPeriod.WEEK)
        val relu2 = SettingsRepository(store)
        relu2.load()
        assertEquals(InsightsPeriod.WEEK, relu2.insightsPeriod.value)
    }

    @Test
    fun `une periode inconnue enregistree retombe sur le mois`() = runBlocking {
        val store = FakePreferencesStore()
        val settings = SettingsRepository(store)
        settings.load()

        // Une valeur qu'une version aurait pu écrire autrement : elle ne doit pas laisser l'écran
        // sans période.
        settings.setInsightsPeriod(InsightsPeriod.MONTH)
        store.forceDays(12)

        val relu = SettingsRepository(store)
        relu.load()
        assertEquals(InsightsPeriod.MONTH, relu.insightsPeriod.value)
    }

    private class FakePreferencesStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        /** Écrit une valeur de jours à la main, comme le ferait une version antérieure. */
        fun forceDays(days: Int) {
            state.value = emptyPreferences().toMutablePreferences().apply {
                this[com.onyx.app.data.local.SettingsKeys.INSIGHTS_PERIOD] = days
            }
        }

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
