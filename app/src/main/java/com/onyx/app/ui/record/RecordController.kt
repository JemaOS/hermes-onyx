package com.onyx.app.ui.record

import com.onyx.app.data.net.ComputerAddress
import com.onyx.app.data.net.RecordDraft
import com.onyx.app.data.net.RecordFailure
import com.onyx.app.data.net.RecordLease
import com.onyx.app.data.net.RecordRoute
import com.onyx.app.data.net.SkillRecordApi
import com.onyx.app.data.net.humanFailure
import com.onyx.app.data.net.recordFailureMessage
import com.onyx.app.data.skill.NamedKey
import com.onyx.app.data.skill.ScrollDirection
import com.onyx.app.data.skill.SkillJournal
import com.onyx.app.data.skill.SkillRecorder
import com.onyx.app.data.skill.skillDraftMarkdown
import com.onyx.app.localization.OnyxLocalization
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject

/**
 * **Le déroulé d'une démonstration**, de « Montrer une fois » au brouillon — sans Compose.
 *
 * L'écran ne tient aucune règle : il appelle [start], [finish], [keep], [discard] et lit [state].
 * C'est ce qui permet de faire jouer le parcours complet contre un vrai serveur HTTP dans un test
 * (MockWebServer), refus compris, sans téléphone.
 *
 * ### Trois choses que ce fichier garantit
 *
 * 1. **Rien de la démonstration ne quitte l'appareil avant [finish].** Le journal vit dans
 *    [recorder] ; `start` n'envoie qu'une adresse de pont et un identifiant de session (D2).
 * 2. **Un refus ne perd jamais le journal.** Une expiration (`410`) ou une route absente laisse le
 *    brouillon se convertir **localement** avec le convertisseur du lot (`skillDraftMarkdown`) :
 *    c'est la même fonction que le serveur, au même octet.
 * 3. **Aucun état muet.** Chaque échec porte une phrase : le refus du greffon par son code, la
 *    panne de transport par [humanFailure], et la route absente par sa phrase à elle (spec §8.1).
 */
class RecordController(
    private val api: SkillRecordApi,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Les étapes du parcours, telles que la feuille les dessine. */
    enum class Phase {
        /** Avant tout : la feuille explique et propose « Montrer une fois ». */
        IDLE,

        /** Le bail part : un instant, pas un état stable. */
        STARTING,

        /** Le bail est ouvert, les gestes s'enregistrent. */
        RECORDING,

        /** Le journal monte, puis le brouillon se demande. */
        STOPPING,

        /** Le brouillon est là : « En faire un skill » ou « Jeter ». */
        REVIEW,
    }

    /**
     * Ce que l'écran affiche. [problem] et [notice] ne se mélangent pas : l'un est un refus ou une
     * panne (il reste jusqu'au geste suivant), l'autre une information qui ne demande rien.
     */
    data class UiState(
        val phase: Phase = Phase.IDLE,
        val problem: String? = null,
        val notice: String? = null,
        val lease: RecordLease? = null,
        val actions: Int = 0,
        /** L'échéance locale du bail, en millisecondes d'horloge murale (0 : pas de bail). */
        val deadlineAtMs: Long = 0L,
        val draft: RecordDraft? = null,
        /** Le brouillon converti **sur l'appareil**, quand `skills.draft` manque ou refuse. */
        val localMarkdown: String? = null,
        val localTitle: String? = null,
        val localReview: List<RecordReviewLine> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** L'enregistreur et son journal — l'écran y branche les gestes qu'il envoie au PC. */
    val recorder: SkillRecorder = SkillRecorder(clock)

    /** Prochain `start` : jette le journal que le serveur garde encore (`journal_pending`). */
    private var discardPending = false

    /** Les dimensions de l'écran du PC, lues du `hello` au moment du départ. */
    private var screenWidth = 0
    private var screenHeight = 0

    /** L'adresse du pont, réduite à `hôte[:port]` — la forme que `bridge.endpoint` exige. */
    private var bridgeEndpoint = ""
    private var bridgeSession = ""

    /** L'échéance locale, calculée à l'ouverture : elle ne dépend pas de l'horloge du serveur. */
    private var startedAtMs = 0L

    /** Les gestes de l'utilisateur, seulement quand un bail est ouvert. */
    fun noteTap(x: Float, y: Float) {
        if (!recording) return
        recorder.tap(x, y)
        publishCount()
    }

    fun noteType(text: String, secret: Boolean) {
        if (!recording) return
        recorder.type(text, secret)
        publishCount()
    }

    fun noteScroll(notches: Int) {
        if (!recording || notches == 0) return
        val direction = if (notches > 0) ScrollDirection.DOWN else ScrollDirection.UP
        recorder.scroll(direction, notches)
        publishCount()
    }

    fun noteKey(key: NamedKey) {
        if (!recording) return
        recorder.key(key)
        publishCount()
    }

    fun noteNavigate(url: String) {
        if (!recording) return
        recorder.navigate(url)
        publishCount()
    }

    /** Vrai pendant que les gestes comptent — la seule fenêtre où l'écran les note. */
    val recording: Boolean get() = _state.value.phase == Phase.RECORDING

    /**
     * Ouvre le bail puis démarre l'enregistreur.
     *
     * [endpoint] est l'adresse du pont **telle que l'utilisateur l'a écrite** : elle est réduite à
     * `hôte[:port]` ici, parce que le greffon refuse un schéma, un chemin ou un identifiant
     * (`bad_bridge`). Une adresse qui ne se réduit pas proprement ne part pas — l'écran le dit au
     * lieu d'envoyer un bail que le serveur refusera.
     */
    suspend fun start(endpoint: String, session: String, width: Int, height: Int) {
        val reduced = recordBridgeEndpoint(endpoint)
        if (reduced == null) {
            _state.value = _state.value.copy(
                phase = Phase.IDLE,
                problem = OnyxLocalization["The bridge address can't be used for a recording."],
            )
            return
        }
        if (session.isBlank()) {
            // Le `hello` du pont n'a pas encore donné son identifiant : sans lui, le bail serait
            // anonyme et le serveur le refuserait (`400 bad_bridge`).
            _state.value = _state.value.copy(
                phase = Phase.IDLE,
                problem = OnyxLocalization["The bridge is not configured: there is nothing to demonstrate."],
            )
            return
        }

        _state.value = _state.value.copy(phase = Phase.STARTING, problem = null, notice = null)
        bridgeEndpoint = reduced
        bridgeSession = session.take(200)
        screenWidth = width
        screenHeight = height

        val lease = try {
            api.start(bridgeEndpoint, bridgeSession, discardPending)
        } catch (error: Throwable) {
            val failure = RecordFailure.from(error)
            if (failure != null) {
                val adopted = failure.adoptLease(clock() / 1000.0)
                if (adopted != null) {
                    // Le serveur dit qu'un bail est déjà ouvert : on le **rejoint** au lieu
                    // d'échouer (spec §7.2), sinon l'écran resterait bloqué dix minutes.
                    open(adopted)
                    _state.value = _state.value.copy(
                        notice = recordFailureMessage(failure, RecordRoute.START),
                    )
                    return
                }
                _state.value = _state.value.copy(
                    phase = Phase.IDLE,
                    problem = recordFailureMessage(failure, RecordRoute.START),
                )
            } else {
                _state.value = _state.value.copy(phase = Phase.IDLE, problem = humanFailure(error))
            }
            return
        }

        discardPending = false
        open(lease)
    }

    private fun open(lease: RecordLease) {
        leaseRef = lease
        startedAtMs = clock()
        recorder.start()
        _state.value = _state.value.copy(
            phase = Phase.RECORDING,
            problem = null,
            notice = null,
            lease = lease,
            actions = 0,
            deadlineAtMs = startedAtMs + lease.maxSeconds * 1000L,
            draft = null,
            localMarkdown = null,
            localTitle = null,
            localReview = emptyList(),
        )
    }

    private var leaseRef: RecordLease? = null

    /**
     * Ferme la capture, envoie le journal, puis demande le brouillon.
     *
     * [expired] dit que le plafond de dix minutes a coupé la capture : le journal porte alors
     * `end_reason = expired`, et le serveur reste libre de rendre sa propre raison (D3).
     */
    suspend fun finish(expired: Boolean = false) {
        val lease = leaseRef ?: return
        if (!recording) return
        val duration = recorder.stop()
        val entries = recorder.entries()
        val journal = recorder.journal(screenWidth, screenHeight)
        val payload = SkillJournal.toJson(
            entries = entries,
            sessionId = lease.sessionId,
            startedAtSeconds = recorder.startedAt() / 1000.0,
            durationMs = duration,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            bridgeEndpoint = bridgeEndpoint,
            bridgeSession = bridgeSession,
            endReason = if (expired) "expired" else "stopped",
        )

        _state.value = _state.value.copy(phase = Phase.STOPPING, problem = null, notice = null)

        var canonical: JsonObject = payload
        var refusal: String? = null
        var notice: String? = null
        try {
            val stopped = api.stop(lease.sessionId, payload)
            canonical = stopped.journalJson ?: payload
            if (stopped.endReason == "expired") {
                notice = OnyxLocalization[
                    "The recording window has expired. Your demonstration is still on this device."
                ]
            }
        } catch (error: Throwable) {
            // Un refus de `stop` ne jette pas le journal : il est sur cet appareil (D2), et
            // `skills.draft` accepte un journal en clair (D4). On continue donc avec le nôtre.
            val failure = RecordFailure.from(error)
            refusal = if (failure != null) {
                recordFailureMessage(failure, RecordRoute.STOP)
            } else {
                humanFailure(error)
            }
        }

        val canonicalJournal = runCatching { SkillJournal.fromJson(canonical) }.getOrNull() ?: journal
        var draftError: Throwable? = null
        val draft = try {
            api.draft(canonical)
        } catch (error: Throwable) {
            draftError = error
            null
        }
        if (draft != null) {
            _state.value = _state.value.copy(
                phase = Phase.REVIEW,
                problem = refusal,
                notice = notice,
                draft = draft,
                actions = entries.size,
                localMarkdown = null,
                localReview = emptyList(),
            )
            return
        }

        // La conversion locale est **la même fonction** que celle du serveur : c'est le repli que
        // la spec prévoit (D4, §8.1), pas un brouillon approximatif.
        val markdown = skillDraftMarkdown(canonicalJournal)
        val draftFailure = draftError?.let { RecordFailure.from(it) }
        val localProblem = refusal ?: if (draftFailure != null) {
            recordFailureMessage(draftFailure, RecordRoute.DRAFT)
        } else {
            humanFailure(draftError)
        }
        _state.value = _state.value.copy(
            phase = Phase.REVIEW,
            // Le refus de `stop` reste la première phrase : c'est lui qui explique le repli.
            problem = localProblem,
            notice = notice,
            draft = null,
            actions = entries.size,
            localMarkdown = markdown,
            localTitle = canonicalJournal.title,
            localReview = recordLocalReview(markdown),
        )
    }

    /** « En faire un skill » : le nom et le Markdown que l'éditeur pré-remplit. */
    fun keep(): Pair<String, String>? {
        // Le journal est **converti** : le prochain `start` doit jeter celui que le serveur garde
        // encore, sinon `409 journal_pending` bloquerait la démonstration suivante (spec §6).
        if (_state.value.lease != null) discardPending = true
        val current = _state.value
        current.draft?.let { return it.name to it.markdown }
        val markdown = current.localMarkdown ?: return null
        val title = current.localTitle?.takeIf { it.isNotBlank() }
            ?: markdown.lineSequence().firstOrNull()?.removePrefix("# ")?.trim()
            ?: ""
        return recordSkillName(title) to markdown
    }

    /**
     * « Jeter » : rien n'est écrit, et le prochain `start` jettera le journal que le serveur garde
     * encore (spec §6).
     */
    fun discard() {
        if (_state.value.lease != null) discardPending = true
        reset()
    }

    /** Referme la feuille sans jeter le brouillon (l'état est gardé pour la prochaine ouverture). */
    fun reset() {
        recorder.stop()
        leaseRef = null
        _state.value = UiState()
    }

    /** Les millisecondes restantes avant le plafond ; 0 quand aucun bail ne court. */
    fun remainingMs(): Long {
        val deadline = _state.value.deadlineAtMs
        if (deadline == 0L) return 0L
        return (deadline - clock()).coerceAtLeast(0L)
    }

    private fun publishCount() {
        _state.value = _state.value.copy(actions = recorder.count)
    }

    companion object {

        /**
         * `hôte[:port]` d'une adresse de pont, ou `null` si elle n'est pas utilisable.
         *
         * Le greffon refuse un schéma, un chemin, un identifiant ou une espace (`bad_bridge`) :
         * on ne lui envoie donc que la partie qui a une chance d'être acceptée, et une adresse qui
         * n'en a aucune ne part pas du tout.
         */
        fun recordBridgeEndpoint(address: String): String? {
            val bare = ComputerAddress.display(address).substringBefore('/').trim()
            if (bare.isEmpty()) return null
            if (bare.contains("@") || bare.contains("?") || bare.contains("#")) return null
            if (bare.any { it.isWhitespace() }) return null
            return bare.take(255)
        }

        /** Le nom de dossier d'un skill, dérivé d'un titre — la même règle que le serveur. */
        fun recordSkillName(title: String): String {
            val folded = java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .lowercase()
            val slug = folded.replace(Regex("[^a-z0-9]+"), "-").trim('-')
            return slug.take(64).ifEmpty { "demonstration" }
        }
    }
}
