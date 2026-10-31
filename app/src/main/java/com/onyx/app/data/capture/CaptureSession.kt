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
 * La boîte des notes de « capture rapide », posée sur ce téléphone.
 *
 * Les notes sont écrites **au moment où elles sont prises** (elles ne dépendent ni du réseau ni
 * d'un écran ouvert) et remises dès que le serveur répond. La remise elle-même vit dans
 * [CaptureFiler] ; ici, seulement la boîte et son disque.
 */
object QuickCapture {

    /** La plus vieille tentative : une note qui n'arrive pas reste et repart plus tard. */
    private val retryDelaysMillis = listOf(15_000L, 60_000L, 180_000L, 600_000L)

    private const val PreferencesName = "onyx_capture"
    private const val WaitingKey = "capture.waiting"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var store: android.content.SharedPreferences? = null
    private var inbox: CaptureInbox? = null
    private var attempts = 0
    private var retrying = false

    private val _waiting = MutableStateFlow<List<CaptureNote>>(emptyList())

    /** Les notes pas encore classées, la plus ancienne d'abord. */
    val waiting: StateFlow<List<CaptureNote>> = _waiting.asStateFlow()

    /** Vrai quand une remise est en cours : l'écran le dit au lieu de rester muet. */
    private val _filing = MutableStateFlow(false)
    val filing: StateFlow<Boolean> = _filing.asStateFlow()

    /** La dernière issue connue, pour que l'écran montre quelque chose après un appui. */
    private val _outcome = MutableStateFlow<CaptureOutcome?>(null)
    val outcome: StateFlow<CaptureOutcome?> = _outcome.asStateFlow()

    /** À appeler une fois, depuis `OnyxApplication`, avec le contexte de l'application. */
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
     * Prend une note. Faux pour un texte vide : rien n'est gardé pour rien.
     *
     * La remise part tout de suite ; si le serveur ne répond pas, la note reste dans la boîte et
     * une nouvelle tentative est programmée.
     */
    fun add(text: String): Boolean {
        val box = inbox ?: return false
        if (!box.add(text, activeServerId(), activeProfile())) return false
        _waiting.value = box.waiting
        attempts = 0
        fileNow()
        return true
    }

    /** Efface une note à la main : elle n'est jamais remise. */
    fun remove(id: String) {
        val box = inbox ?: return
        box.remove(id)
        _waiting.value = box.waiting
    }

    /** Une tentative de remise, tout de suite (à l'ouverture de l'app, ou au retour du réseau). */
    fun fileNow() {
        val box = inbox ?: return
        scope.launch { file(box, activeServer(), activeProfile()) }
    }

    /** Ce qui peut partir pour le serveur et le profil en service. */
    fun ready(): List<CaptureNote> {
        val box = inbox ?: return emptyList()
        return box.ready(activeServerId(), activeProfile())
    }

    private suspend fun file(box: CaptureInbox, server: ServerConfig?, profile: String?) {
        if (server == null) {
            // Rien n'est configuré : la note attend un serveur, elle n'est pas perdue.
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

    /** La date d'une note, comme la référence l'écrit dans le message : « lundi 26 octobre à 09:12 ». */
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
 * La remise : les notes partent **en un seul message** dans un tour que personne ne regarde.
 *
 * C'est le `CaptureFiler` de la référence : l'agent range la note avec ce qu'il a (sa mémoire, une
 * liste, un rappel, le calendrier), répond en quelques mots, et **rien n'est lu à voix haute** —
 * la réponse n'est simplement pas affichée dans la conversation ouverte.
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
            // Le Dashboard : un canal à nous, refermé en sortant, comme le tour d'un intent.
            Transport.HERMES_SERVE -> HermesServeChat(server).stream(request).collect { }
            else -> ChatCompletionsClient(server).stream(request).collect { }
        }
    }
}
