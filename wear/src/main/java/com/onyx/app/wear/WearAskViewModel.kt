package com.onyx.app.wear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onyx.app.data.model.ChatRequest
import com.onyx.app.data.model.ChatTurn
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.net.ChatCompletionsClient
import com.onyx.app.data.net.TransportError
import com.onyx.app.data.net.TurnEvent
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.voice.SpeechEngine
import com.onyx.app.voice.SpeechOutput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the watch is: idle, listening, waiting, or speaking. */
enum class WearPhase { IDLE, LISTENING, WAITING, SPEAKING, PROBLEM }

data class WearState(
    val phase: WearPhase = WearPhase.IDLE,
    val heard: String = "",
    val question: String = "",
    val reply: String = "",
    val problem: String? = null,
)

/**
 * The watch: you speak, the question goes to the gateway, the answer appears and is read aloud.
 *
 * A reduced mirror of the reference Watch app: the same dictated → question → spoken answer loop, with
 * the same building blocks as the phone since they live in `:core`.
 */
class WearAskViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = WearSettings(application)

    val speech = SpeechEngine(application)
    private val output = SpeechOutput(application)

    private val _state = MutableStateFlow(WearState())
    val state: StateFlow<WearState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        output.onFinished = {
            if (_state.value.phase == WearPhase.SPEAKING) _state.update { it.copy(phase = WearPhase.IDLE) }
        }
        speech.onFinished = {
            val heard = speech.state.value.transcript.trim()
            if (heard.isNotEmpty()) ask(heard)
        }
    }

    val isConfigured: Boolean get() = settings.isConfigured

    fun configure(baseUrl: String, apiKey: String, model: String) {
        settings.save(baseUrl, apiKey, model)
        _state.update { it.copy(phase = WearPhase.IDLE, problem = null) }
    }

    /** The watch button: one tap to talk, one tap to stop. */
    fun toggleListening() {
        if (_state.value.phase == WearPhase.LISTENING) {
            speech.stop()
            _state.update { it.copy(phase = WearPhase.IDLE) }
            return
        }
        output.stop()
        _state.update { it.copy(phase = WearPhase.LISTENING, heard = "", problem = null) }
        speech.start(true)
    }

    fun ask(text: String) {
        val question = text.trim()
        if (question.isEmpty() || job?.isActive == true) return
        speech.stop()
        _state.update {
            it.copy(phase = WearPhase.WAITING, question = question, reply = "", problem = null)
        }
        speakQuestion(question)
    }

    fun stop() {
        job?.cancel()
        job = null
        speech.stop()
        output.stop()
        _state.update { it.copy(phase = WearPhase.IDLE) }
    }

    /** Reads the last answer aloud again. */
    fun speakAgain() {
        val reply = _state.value.reply
        if (reply.isBlank()) return
        _state.update { it.copy(phase = WearPhase.SPEAKING) }
        output.speak(reply)
    }

    override fun onCleared() {
        speech.release()
        output.shutdown()
        super.onCleared()
    }

    private fun speakQuestion(question: String) {
        val server: ServerConfig = settings.server()
        job = viewModelScope.launch {
            val client = ChatCompletionsClient(server)
            val builder = StringBuilder()
            try {
                client.stream(
                    ChatRequest(history = listOf(ChatTurn(role = Role.USER, text = question)), model = server.effectiveModel),
                ).collect { event ->
                    when (event) {
                        is TurnEvent.TextDelta -> {
                            builder.append(event.text)
                            _state.update { it.copy(reply = builder.toString()) }
                        }

                        is TurnEvent.TextFinal -> _state.update { it.copy(reply = event.text) }
                        else -> Unit
                    }
                }
                val reply = builder.toString().ifBlank { OnyxLocalization["The server sent nothing back."] }
                _state.update { it.copy(phase = WearPhase.SPEAKING, reply = reply) }
                output.speak(reply)
            } catch (error: CancellationException) {
                throw error
            } catch (error: TransportError) {
                _state.update { it.copy(phase = WearPhase.PROBLEM, problem = error.userMessage()) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(phase = WearPhase.PROBLEM, problem = error.message ?: OnyxLocalization["Something went wrong."])
                }
            }
        }
    }
}
