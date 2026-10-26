package com.onyx.app.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.onyx.app.car.AutoArtwork
import com.onyx.app.car.AutoVoiceState
import com.onyx.app.data.model.ChatRequest
import com.onyx.app.data.model.ChatTurn
import com.onyx.app.data.model.Role
import com.onyx.app.data.net.ChatCompletionsClient
import com.onyx.app.data.net.TransportError
import com.onyx.app.data.net.TurnEvent
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.voice.SpeechEngine
import com.onyx.app.voice.SpeechOutput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Une question dictée par l'assistant, la réponse affichée et lue, dans l'historique Auto courant. */
class AskScreen(
    carContext: CarContext,
    private val initialQuery: String?,
    private val history: MutableList<ChatTurn> = mutableListOf(),
    private val showBack: Boolean = false,
) : Screen(carContext) {

    private val settings = CarSettings(carContext)
    private val looks = AutoSettingsBridge(carContext)
    private val speech = SpeechEngine(carContext)
    private val output = SpeechOutput(carContext)

    private var job: Job? = null
    private var question: String? = null
    private var answer: String? = null
    private var problem: String? = null
    private var asking = false
    private var speaking = false

    init {
        output.onFinished = {
            speaking = false
            invalidate()
        }
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                job?.cancel()
                speech.release()
                output.shutdown()
            }
        })
        initialQuery?.let(::ask)
    }

    /** Pose la question au serveur et lit la réponse. Le micro reste celui de l'assistant Android. */
    fun ask(text: String?) {
        val clean = text?.trim().orEmpty()
        if (clean.isEmpty() || job?.isActive == true) return

        output.stop()
        speaking = false
        question = clean
        answer = null
        problem = null
        asking = true
        invalidate()

        job = lifecycleScope.launch {
            if (!settings.isConfigured) {
                asking = false
                problem = "Set the server address in Onyx on the phone first."
                invalidate()
                return@launch
            }
            val server = settings.server()
            val builder = StringBuilder()
            history += ChatTurn(role = Role.USER, text = clean)
            try {
                ChatCompletionsClient(server).stream(
                    ChatRequest(
                        history = history.toList(),
                        model = server.effectiveModel,
                    ),
                ).collect { event ->
                    when (event) {
                        is TurnEvent.TextDelta -> {
                            builder.append(event.text)
                            answer = builder.toString()
                            invalidate()
                        }

                        is TurnEvent.TextFinal -> {
                            answer = event.text
                            invalidate()
                        }

                        else -> Unit
                    }
                }
                asking = false
                val reply = answer ?: "The server sent nothing back."
                answer = reply
                answer?.takeIf { it.isNotBlank() }?.let { history += ChatTurn(role = Role.ASSISTANT, text = it) }
                speaking = output.isReady
                invalidate()
                output.speak(reply)
            } catch (error: CancellationException) {
                throw error
            } catch (error: TransportError) {
                asking = false
                problem = error.userMessage()
                invalidate()
            } catch (error: Exception) {
                asking = false
                problem = error.message ?: "Something went wrong."
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val header = Header.Builder()
            .setTitle(settings.server().displayName)
            .setStartHeaderAction(if (showBack) Action.BACK else Action.APP_ICON)
            .build()

        if (asking || speaking) {
            val state = if (asking) AutoVoiceState.THINKING else AutoVoiceState.SPEAKING
            val status = if (asking) carContext.getString(R.string.auto_thinking) else OnyxLocalization["Speaking"]
            val row = Row.Builder()
                .setTitle(status)
                .addText(question.orEmpty())
                .setImage(
                    CarIcon.Builder(
                        IconCompat.createWithBitmap(AutoArtwork.voiceState(state, looks.looks().voice)),
                    ).build(),
                    Row.IMAGE_TYPE_LARGE,
                )
                .build()
            return ListTemplate.Builder()
                .setHeader(header)
                .setSingleList(ItemList.Builder().addItem(row).build())
                .build()
        }

        val message = when {
            problem != null -> problem.orEmpty()
            answer != null -> "${question.orEmpty()}\n\n$answer"
            question != null -> question.orEmpty()
            else -> carContext.getString(R.string.auto_ready)
        }

        return MessageTemplate.Builder(message.take(MAX_MESSAGE))
            .setHeader(header)
            .build()
    }

    private companion object {
        const val MAX_MESSAGE = 1_500
    }
}
