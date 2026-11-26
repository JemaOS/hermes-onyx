package com.onyx.app.data.capture

import com.onyx.app.localization.OnyxLanguage
import android.content.Context
import com.onyx.app.data.model.ChatRequest
import com.onyx.app.data.model.ChatTurn
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import com.onyx.app.data.net.ChatCompletionsClient
import com.onyx.app.data.net.HermesServeChat
import com.onyx.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The "quick capture" note box, kept on this phone (French UI string « capture rapide »).
 *
 * Notes are written **at the moment they are taken** (they depend neither on the network nor
 * on an open screen) and handed over as soon as the server answers. The delivery itself lives in
 * [CaptureFiler]; here, only the box and its disk.
 */
object QuickCapture {

    /** Oldest attempt first: a note that does not arrive stays and leaves again later. */
    private val retryDelaysMillis = listOf(15_000L, 60_000L, 180_000L, 600_000L)

    private const val PreferencesName = "onyx_capture"
    private const val WaitingKey = "capture.waiting"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var store: android.content.SharedPreferences? = null
    private var inbox: CaptureInbox? = null
    private var attempts = 0
    private var retrying = false

    private val _waiting = MutableStateFlow<List<CaptureNote>>(emptyList())

    /** The notes not yet filed, oldest first. */
    val waiting: StateFlow<List<CaptureNote>> = _waiting.asStateFlow()

    /** True when a delivery is in progress: the screen says so instead of staying silent. */
    private val _filing = MutableStateFlow(false)
    val filing: StateFlow<Boolean> = _filing.asStateFlow()

    /** The last known outcome, so that the screen shows something after a tap. */
    private val _outcome = MutableStateFlow<CaptureOutcome?>(null)
    val outcome: StateFlow<CaptureOutcome?> = _outcome.asStateFlow()

    /** To be called once, from `OnyxApplication`, with the application context. */
    fun install(context: Context) {
        if (inbox != null) return
        val preferences = context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        store = preferences
        val box = CaptureInbox(
            initial = read(preferences),
            persist = { notes -> write(preferences, notes) },
            deliver = { notes -> CaptureFiler.deliver(notes) },
        )
        inbox = box
        _waiting.value = box.waiting
    }

    /**
     * Takes a note. False for an empty text: nothing is kept for nothing.
     *
     * The delivery leaves right away; if the server does not answer, the note stays in the box and
     * a new attempt is scheduled.
     */
    fun add(text: String): Boolean {
        val box = inbox ?: return false
        if (!box.add(text, activeServerId(), activeProfile())) return false
        _waiting.value = box.waiting
        attempts = 0
        fileNow()
        return true
    }

    /** Erases a note by hand: it is never handed over. */
    fun remove(id: String) {
        val box = inbox ?: return
        box.remove(id)
        _waiting.value = box.waiting
    }

    /** A delivery attempt, right away (when the app opens, or when the network returns). */
    fun fileNow() {
        val box = inbox ?: return
        scope.launch { file(box, activeServer(), activeProfile()) }
    }

    /** What can leave for the server and profile in service. */
    fun ready(): List<CaptureNote> {
        val box = inbox ?: return emptyList()
        return box.ready(activeServerId(), activeProfile())
    }

    private suspend fun file(box: CaptureInbox, server: ServerConfig?, profile: String?) {
        if (server == null) {
            // Nothing is configured: the note waits for a server, it is not lost.
            _outcome.value = CaptureOutcome.WAITING
            return
        }
        _filing.value = true
        val result = try {
            box.file(server.id, profile)
        } finally {
            _filing.value = false
        }
        _outcome.value = result
        _waiting.value = box.waiting
        when (result) {
            CaptureOutcome.WAITING -> scheduleRetry()
            CaptureOutcome.FILED, CaptureOutcome.NOTHING -> {
                attempts = 0
                retrying = false
            }
        }
    }

    private fun scheduleRetry() {
        if (retrying) return
        retrying = true
        val delay = retryDelaysMillis[minOf(attempts, retryDelaysMillis.lastIndex)]
        attempts += 1
        scope.launch {
            kotlinx.coroutines.delay(delay)
            retrying = false
            fileNow()
        }
    }

    private fun activeServer(): ServerConfig? {
        if (!AppContainer.isInstalled) return null
        val server = AppContainer.servers.activeServer.value
        return server.takeIf { it.baseUrl.isNotBlank() || it.serveBaseUrl.isNotBlank() }
    }

    private fun activeServerId(): String =
        if (AppContainer.isInstalled) AppContainer.servers.activeServer.value.id else ""

    private fun activeProfile(): String? =
        if (AppContainer.isInstalled) AppContainer.servers.activeServer.value.hermesProfile.ifEmpty { null } else null

    /** A note's date, as the reference writes it in the message: « lundi 26 octobre à 09:12 » (Monday 26 October at 09:12). */
    fun whenLabel(at: Long): String =
        SimpleDateFormat("EEEE d MMMM 'à' HH:mm", OnyxLanguage.preferred).format(Date(at))

    private fun read(preferences: android.content.SharedPreferences): List<CaptureNote> {
        val raw = preferences.getString(WaitingKey, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                CaptureNote(
                    id = item.optString("id"),
                    text = item.optString("text"),
                    at = item.optLong("at"),
                    server = item.optString("server"),
                    profile = item.optString("profile").ifEmpty { null },
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun write(preferences: android.content.SharedPreferences, notes: List<CaptureNote>) {
        val array = JSONArray()
        notes.forEach { note ->
            array.put(
                JSONObject().apply {
                    put("id", note.id)
                    put("text", note.text)
                    put("at", note.at)
                    put("server", note.server)
                    put("profile", note.profile ?: "")
                },
            )
        }
        preferences.edit().putString(WaitingKey, array.toString()).apply()
    }
}

/**
 * The delivery: notes leave **in a single message** in a turn no one watches.
 *
 * It is the reference's `CaptureFiler`: the agent files the note with what it has (its memory, a
 * list, a reminder, the calendar), answers in a few words, and **nothing is read aloud** —
 * the answer is simply not shown in the open conversation.
 */
object CaptureFiler {

    suspend fun deliver(notes: List<CaptureNote>) {
        if (notes.isEmpty()) return
        val server = AppContainer.servers.activeServer.value
        val request = ChatRequest(
            history = listOf(
                ChatTurn(
                    role = Role.USER,
                    text = CaptureCopy.message(notes) { QuickCapture.whenLabel(it.at) },
                ),
            ),
            model = server.effectiveModel,
            provider = server.effectiveProvider,
        )
        when (server.transport) {
            // The Dashboard: a channel of our own, closed on the way out, like an intent's turn.
            Transport.HERMES_SERVE -> HermesServeChat(server).stream(request).collect { }
            else -> ChatCompletionsClient(server).stream(request).collect { }
        }
    }
}
