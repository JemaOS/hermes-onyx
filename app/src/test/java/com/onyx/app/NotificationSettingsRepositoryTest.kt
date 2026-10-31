package com.onyx.app

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.onyx.app.data.repo.SettingsRepository
import com.onyx.app.notifications.WorkTracking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Les réglages de notifications doivent être réels, persistants et conservés au rechargement. */
class NotificationSettingsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `les valeurs initiales suivent les reglages référence et android`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) {
                folder.newFile("defaults.preferences_pb")
            }
            val repository = SettingsRepository(store)

            repository.load()

            assertFalse(repository.notifyInBackground.value)
            assertTrue(repository.notificationSound.value)
            assertEquals(
                "le défaut proposé est « à la fin seulement »",
                WorkTracking.ON_FINISH,
                repository.workTracking.value,
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `le suivi du travail s'ecrit et se relit`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) {
                folder.newFile("saved.preferences_pb")
            }
            val repository = SettingsRepository(store)

            repository.setNotifyInBackground(false)
            repository.setNotificationSound(false)
            repository.setWorkTracking(WorkTracking.EVERY_STEP)

            assertFalse(repository.notifyInBackground.value)
            assertFalse(repository.notificationSound.value)
            assertEquals(WorkTracking.EVERY_STEP, repository.workTracking.value)

            val saved = store.data.first()
            assertEquals(false, saved[booleanPreferencesKey("notify_in_background")])
            assertEquals(false, saved[booleanPreferencesKey("notification_sound")])
            assertEquals("step", saved[stringPreferencesKey("work_tracking")])

            // Relu depuis le disque, le choix survit à un redémarrage de l'app.
            repository.load()
            assertEquals(WorkTracking.EVERY_STEP, repository.workTracking.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `l'ancien booleen de notification persistante est repris`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) {
                folder.newFile("legacy.preferences_pb")
            }
            // Une version antérieure n'écrivait qu'un vrai/faux « notification persistante ».
            store.edit { it[booleanPreferencesKey("show_live_activity")] = false }
            val repository = SettingsRepository(store)
            repository.load()
            assertEquals(
                "un ancien « éteint » devient « jamais », pas un retour du bandeau",
                WorkTracking.NEVER,
                repository.workTracking.value,
            )

            store.edit { it[booleanPreferencesKey("show_live_activity")] = true }
            repository.load()
            assertEquals(WorkTracking.ON_FINISH, repository.workTracking.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `les trois choix portent des identifiants stables et un libelle traduisible`() {
        assertEquals(listOf("never", "finish", "step"), WorkTracking.choices.map { it.id })
        assertEquals(
            listOf("Never", "When the turn ends", "At every step"),
            WorkTracking.choices.map { it.label },
        )
        assertEquals(WorkTracking.ON_FINISH, WorkTracking.fromId(null))
        assertEquals(WorkTracking.NEVER, WorkTracking.fromId("never"))
        assertEquals(
            "un identifiant inconnu retombe sur le défaut",
            WorkTracking.ON_FINISH,
            WorkTracking.fromId("n'importe quoi"),
        )
    }
}
