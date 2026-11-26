package com.onyx.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.onyx.app.car.AutoCardLook
import com.onyx.app.car.AutoVoiceLook
import com.onyx.app.data.repo.SettingsRepository
import com.onyx.app.voice.ReplyVoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceAndAutoSettingsTest {
    @Test
    fun `les choix voix et Android Auto sont restaures depuis le meme magasin`() = runBlocking {
        val store = FakePreferencesStore()
        val settings = SettingsRepository(store)
        settings.load()
        assertEquals(ReplyVoice.ON_DEVICE, settings.replyVoice.value)
        assertEquals(SettingsRepository.DEFAULT_KOKORO_VOICE, settings.kokoroVoice.value)
        assertEquals(SettingsRepository.DEFAULT_KOKORO_TOKEN, settings.kokoroToken.value)
        assertEquals(AutoCardLook.COLOR, settings.autoCardLook.value)
        assertEquals(AutoVoiceLook.BARS, settings.autoVoiceLook.value)

        settings.setReplyVoice(ReplyVoice.KOKORO)
        settings.setKokoroUrl("http://tts.test:8880/")
        settings.setKokoroVoice("am_onyx(2)+bm_george(1)")
        settings.setKokoroToken("jeton-de-test")
        settings.setAutoCardLook(AutoCardLook.GLOW)
        settings.setAutoVoiceLook(AutoVoiceLook.KITT)

        val restored = SettingsRepository(store)
        restored.load()
        assertEquals(ReplyVoice.KOKORO, restored.replyVoice.value)
        assertEquals("http://tts.test:8880/", restored.kokoroUrl.value)
        assertEquals("am_onyx(2)+bm_george(1)", restored.kokoroVoice.value)
        assertEquals("jeton-de-test", restored.kokoroToken.value)
        assertEquals(AutoCardLook.GLOW, restored.autoCardLook.value)
        assertEquals(AutoVoiceLook.KITT, restored.autoVoiceLook.value)
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
