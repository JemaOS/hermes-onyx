package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import com.onyx.app.data.skill.SkillJournal
import java.util.Locale
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les trois routes du compagnon `onyx-record`, **contre un vrai serveur HTTP** (MockWebServer, la
 * convention du dépôt).
 *
 * Ce que ce fichier tient :
 *
 * 1. **les chemins, les méthodes et les corps sont ceux du greffon** — un bail porte son `bridge`,
 *    un `stop` porte `session_id` + `journal`, un brouillon porte le journal en clair ;
 * 2. **les refus sont lus par leur code** — `already_recording` donne de quoi **adopter** le bail,
 *    `410` se distingue de `404`, et la phrase affichée est celle de l'app, jamais le `message`
 *    anglais du serveur ;
 * 3. **un refus inconnu est cité tel quel** — il ne devient pas un « Réessayez » anonyme.
 */
class SkillRecordApiTest {

    private lateinit var server: MockWebServer
    private var previousLocale: Locale = Locale.getDefault()

    @Test
    fun `start ouvre le bail avec l adresse du pont, sans profil`() = runBlocking {
        answering(
            """
            {"session_id":"rec_01J9Z3K4Q7V8","started_at":1730650000.123,
             "deadline_at":1730650600.123,"max_seconds":600,"grace_seconds":120}
            """.trimIndent(),
        )

        val lease = SkillRecordApi(client()).start("192.168.1.24:8765", "pc-7f21")

        assertEquals("rec_01J9Z3K4Q7V8", lease.sessionId)
        assertEquals(600, lease.maxSeconds)
        assertEquals(120, lease.graceSeconds)
        assertFalse(lease.adopted)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/${SkillRecordApi.START}", request.requestUrl?.encodedPath)
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("192.168.1.24:8765", body["bridge"]!!.jsonObject["endpoint"]!!.jsonPrimitive.content)
        assertEquals("pc-7f21", body["bridge"]!!.jsonObject["session"]!!.jsonPrimitive.content)
        assertNull("un bail ne prend pas de profil", body["profile"])
        assertNull("rien à jeter au premier départ", body["discard_pending"])
    }

    @Test
    fun `already_recording donne de quoi rejoindre le bail ouvert`() = runBlocking {
        answering(
            """
            {"detail":{"code":"already_recording","message":"A recording is already open.",
             "session_id":"rec_DEJAOUVERT","deadline_at":1730650600.0}}
            """.trimIndent(),
            code = 409,
        )

        val failure = try {
            SkillRecordApi(client()).start("192.168.1.24:8765", "pc-7f21")
            error("un bail déjà ouvert doit être un refus")
        } catch (refused: Throwable) {
            RecordFailure.from(refused)
        }

        assertNotNull(failure)
        assertEquals("already_recording", failure!!.code)
        assertEquals(409, failure.status)
        assertEquals("rec_DEJAOUVERT", failure.sessionId)

        // Le client **adopte** le bail : même identifiant, même échéance, et il se sait adopté.
        val lease = failure.adoptLease(nowSeconds = 1_730_650_000.0)
        assertNotNull(lease)
        assertEquals("rec_DEJAOUVERT", lease!!.sessionId)
        assertEquals(600.0, lease.deadlineAt - 1_730_650_000.0, 0.001)
        assertTrue(lease.adopted)

        // La phrase est celle de l'app, pas le message anglais du serveur.
        val phrase = recordFailureMessage(failure, RecordRoute.START)
        assertEquals("Un enregistrement est déjà ouvert sur ce serveur.", phrase)
    }

    @Test
    fun `stop envoie le journal et relit la forme canonique`() = runBlocking {
        answering(
            """
            {"session_id":"rec_01J9Z3K4Q7V8","stopped_at":1730650012.0,"duration_ms":12000,
             "actions":2,"bytes":512,"truncated":false,"dropped":0,"end_reason":"stopped",
             "journal":{"version":1,"session_id":"rec_01J9Z3K4Q7V8","started_at":1730650000.0,
               "duration_ms":12000,"end_reason":"stopped",
               "screen":{"width_px":1440,"height_px":900,"scale":1.0},
               "bridge":{"endpoint":"192.168.1.24:8765","session":"pc-7f21"},
               "events":[
                 {"seq":1,"at_ms":0,"kind":"tap","target":{"x_pct":0.421,"y_pct":0.183}},
                 {"seq":2,"at_ms":1240,"kind":"type","target":{},"text":"Rapport 2026","chars":12}
               ]}}
            """.trimIndent(),
        )

        // Un journal minimal, écrit à la main : ce que le téléphone enverrait.
        val payload = SkillJournal.toJson(
            entries = emptyList(),
            sessionId = "rec_01J9Z3K4Q7V8",
            startedAtSeconds = 1_730_650_000.0,
            durationMs = 0,
            screenWidth = 1440,
            screenHeight = 900,
            bridgeEndpoint = "192.168.1.24:8765",
            bridgeSession = "pc-7f21",
        )
        val stopped = SkillRecordApi(client()).stop("rec_01J9Z3K4Q7V8", payload)

        assertEquals("stopped", stopped.endReason)
        assertEquals(2, stopped.actions)
        assertEquals(12_000L, stopped.durationMs)
        assertFalse(stopped.truncated)
        assertEquals(2, stopped.journal?.actions?.size)
        assertEquals(1440, stopped.journal?.windowWidth)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/${SkillRecordApi.STOP}", request.requestUrl?.encodedPath)
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("rec_01J9Z3K4Q7V8", body["session_id"]!!.jsonPrimitive.content)
        assertEquals("1", body["journal"]!!.jsonObject["version"]!!.jsonPrimitive.content)
    }

    @Test
    fun `un stop sans journal est une annulation`() = runBlocking {
        answering(
            """
            {"session_id":"rec_01J9Z3K4Q7V8","stopped_at":1730650001.0,"duration_ms":1000,
             "actions":0,"bytes":0,"truncated":false,"dropped":0,"end_reason":"discarded"}
            """.trimIndent(),
        )

        val stopped = SkillRecordApi(client()).stop("rec_01J9Z3K4Q7V8")

        assertEquals("discarded", stopped.endReason)
        assertNull(stopped.journal)
        assertNull(stopped.journalJson)
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertNull("aucun journal ne part pour une annulation", body["journal"])
    }

    @Test
    fun `draft rend les etapes, celles a relire et les avertissements`() = runBlocking {
        answering(
            """
            {"name":"creer-un-dossier","title":"Créer un dossier","steps":2,
             "markdown":"# Créer un dossier\n\n1. Tap \"Nouveau dossier\"\n2. Tap at 51% / 8% of the window — à relire : libellé absent\n",
             "review":[{"seq":9,"step":2,"reason":"no_label","line":4}],
             "warnings":["label_truncated"],"journal_bytes":2196}
            """.trimIndent(),
        )

        val draft = SkillRecordApi(client()).draft(
            journal = Json.parseToJsonElement("""{"version":1}"""),
            nameHint = "creer-un-dossier",
        )

        assertEquals("creer-un-dossier", draft.name)
        assertEquals("Créer un dossier", draft.title)
        assertEquals(2, draft.steps)
        assertEquals(1, draft.review.size)
        assertEquals(2, draft.review.single().step)
        assertEquals("no_label", draft.review.single().reason)
        assertEquals(listOf("label_truncated"), draft.warnings)
        assertEquals(2196, draft.journalBytes)

        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("creer-un-dossier", body["name_hint"]!!.jsonPrimitive.content)
        assertNotNull(body["journal"])
    }

    @Test
    fun `une route absente a sa phrase, et n est pas une panne de transport`() = runBlocking {
        answering("""{"detail":"Not Found"}""", code = 404)

        val failure = try {
            SkillRecordApi(client()).start("192.168.1.24:8765", "pc-7f21")
            error("un 404 doit être un refus")
        } catch (refused: Throwable) {
            RecordFailure.from(refused)
        }

        assertNotNull(failure)
        assertTrue(failure!!.routeMissing)
        assertEquals(404, failure.status)
        assertEquals(
            "Ce serveur ne sait pas enregistrer une démonstration (route absente).",
            recordFailureMessage(failure, RecordRoute.START),
        )
    }

    @Test
    fun `un bail expire se distingue d un identifiant inconnu`() = runBlocking {
        answering("""{"detail":{"code":"session_expired","message":"The window is over."}}""", code = 410)
        val expired = try {
            SkillRecordApi(client()).stop("rec_01J9Z3K4Q7V8")
            error("410 attendu")
        } catch (refused: Throwable) {
            RecordFailure.from(refused)
        }

        answering("""{"detail":{"code":"unknown_session"}}""", code = 409)
        val unknown = try {
            SkillRecordApi(client()).stop("rec_INCONNU")
            error("409 attendu")
        } catch (refused: Throwable) {
            RecordFailure.from(refused)
        }

        assertTrue(expired!!.routeMissing.not())
        assertEquals("session_expired", expired.code)
        assertEquals(410, expired.status)
        assertEquals(
            "La fenêtre d’enregistrement est expirée. Votre démonstration est restée sur cet appareil.",
            recordFailureMessage(expired, RecordRoute.STOP),
        )

        assertEquals("unknown_session", unknown!!.code)
        assertEquals(
            "Ce serveur ne connaît pas cet enregistrement.",
            recordFailureMessage(unknown, RecordRoute.STOP),
        )
    }

    @Test
    fun `un refus inconnu est cite tel quel`() = runBlocking {
        answering("""{"detail":{"code":"quelque_chose_de_nouveau"}}""", code = 422)

        val failure = try {
            SkillRecordApi(client()).draft(Json.parseToJsonElement("""{"version":1}"""))
            error("422 attendu")
        } catch (refused: Throwable) {
            RecordFailure.from(refused)
        }

        assertEquals("quelque_chose_de_nouveau", failure!!.code)
        assertEquals(
            "Le serveur a refusé : quelque_chose_de_nouveau.",
            recordFailureMessage(failure, RecordRoute.DRAFT),
        )
    }

    @Test
    fun `un serveur injoignable n est pas un refus du greffon`() {
        // Rien n'est en file : OkHttp échoue sur une connexion refusée, pas sur un code du greffon.
        val failure = try {
            runBlocking { SkillRecordApi(client()).start("192.168.1.24:8765", "pc-7f21") }
            null
        } catch (error: Throwable) {
            RecordFailure.from(error)
        }
        assertNull("une panne de transport n'est pas un refus d'état", failure)
    }

    // MARK: - Outillage

    private fun config() = ServerConfig(
        id = "enregistrement",
        serveBaseUrl = server.url("/").toString().trimEnd('/'),
        apiKey = "cle-1",
        transport = Transport.HERMES_SERVE,
    )

    private fun client() = ServeClient(config())

    private fun answering(body: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    @org.junit.Before
    fun setUp() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.FRANCE)
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        Locale.setDefault(previousLocale)
        server.shutdown()
    }
}
