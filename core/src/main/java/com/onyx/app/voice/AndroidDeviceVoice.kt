package com.onyx.app.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Le `TextToSpeech` d'Android : la voix de l'appareil, prête à relayer un serveur muet.
 *
 * Le moteur se construit sans bloquer (son callback arrive ensuite). Tant que son état n'est pas
 * connu, [isAvailable] reste optimiste et une phrase demandée est mise en attente : elle part dès
 * l'initialisation. Un moteur réellement absent ou hostile le dit alors par `onDone(false)`, et
 * l'écran l'annonce au lieu de se taire.
 */
class AndroidDeviceVoice(context: Context) : DeviceVoice {

    private class Active(val id: String, val onDone: (Boolean) -> Unit)

    private var engine: TextToSpeech? = null
    private var status = UNKNOWN
    private var sequence = 0L

    @Volatile
    private var active: Active? = null
    private var queued: Queued? = null

    private class Queued(val text: String, val languageTag: String, val onDone: (Boolean) -> Unit)

    /** Les callbacks du moteur ne reviennent pas forcément sur le looper principal. */
    private val main = Handler(Looper.getMainLooper())

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) = finish(utteranceId, true)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = finish(utteranceId, false)

        override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId, false)
    }

    init {
        engine = TextToSpeech(context.applicationContext) { result ->
            status = if (result == TextToSpeech.SUCCESS) READY else FAILED
            val request = queued
            queued = null
            if (status == READY) {
                engine?.setOnUtteranceProgressListener(progress)
                request?.let { speak(it.text, it.languageTag, it.onDone) }
            } else {
                request?.onDone?.invoke(false)
            }
        }
    }

    override val isAvailable: Boolean get() = status != FAILED

    override fun speak(text: String, languageTag: String, onDone: (Boolean) -> Unit) {
        val clean = text.trim()
        if (clean.isEmpty()) {
            onDone(false)
            return
        }
        when (status) {
            UNKNOWN -> {
                queued = Queued(clean, languageTag, onDone)
                return
            }
            FAILED -> {
                onDone(false)
                return
            }
        }
        applyLanguage(languageTag)
        sequence += 1
        val id = "onyx.device.$sequence"
        active = Active(id, onDone)
        val started = runCatching { engine?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, id) }.getOrNull()
        if (started != TextToSpeech.SUCCESS) {
            active = null
            onDone(false)
        }
    }

    override fun stop() {
        // On invalide d'abord : la phrase coupée ne doit pas rappeler `onDone` après un Stop.
        active = null
        queued = null
        engine?.stop()
    }

    override fun release() {
        active = null
        queued = null
        engine?.shutdown()
        engine = null
        status = FAILED
    }

    private fun finish(utteranceId: String?, spoken: Boolean) {
        val current = active ?: return
        if (utteranceId == null || utteranceId != current.id) return
        active = null
        main.post { current.onDone(spoken) }
    }

    private fun applyLanguage(tag: String) {
        val locale = runCatching { Locale.forLanguageTag(tag) }.getOrNull() ?: return
        val result = engine?.setLanguage(locale) ?: return
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine?.setLanguage(Locale.getDefault())
        }
    }

    private companion object {
        const val UNKNOWN = 0
        const val READY = 1
        const val FAILED = 2
    }
}
