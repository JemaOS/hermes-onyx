package com.onyx.app

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.onyx.app.data.repo.SettingsRepository
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
    fun `les valeurs initiales suivent les reglages ios et android`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) {
                folder.newFile("defaults.preferences_pb")
            }
            val repository = SettingsRepository(store)

            repository.load()

            assertFalse(repository.notifyInBackground.value)
            assertTrue(repository.notificationSound.value)
            assertTrue(repository.showLiveActivity.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `les trois choix sont ecrits dans le magasin de preferences`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) {
                folder.newFile("saved.preferences_pb")
            }
            val repository = SettingsRepository(store)

            repository.setNotifyInBackground(false)
            repository.setNotificationSound(false)
            repository.setShowLiveActivity(false)

            assertFalse(repository.notifyInBackground.value)
            assertFalse(repository.notificationSound.value)
            assertFalse(repository.showLiveActivity.value)
            val saved = store.data.first()
            assertEquals(false, saved[booleanPreferencesKey("notify_in_background")])
            assertEquals(false, saved[booleanPreferencesKey("notification_sound")])
            assertEquals(false, saved[booleanPreferencesKey("show_live_activity")])
        } finally {
            scope.cancel()
        }
    }
}
