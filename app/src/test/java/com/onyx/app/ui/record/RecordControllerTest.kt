package com.onyx.app.ui.record

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.SkillRecordApi
import java.util.Locale
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * **Le parcours complet d'une démonstration**, contre un vrai serveur HTTP : départ, gestes,
 * arrêt, brouillon — et les refus.
 *
 * Ce que ce fichier tient :
 *
 * 1. **les gestes ne comptent que sous bail** — un appui avant `start` n'entre pas, un appui
 *    pendant la capture entre, un appui après `stop` n'entre plus ;
 * 2. **le brouillon vient du serveur** quand `skills.draft` répond ; il est converti **sur
 *    l'appareil** quand la route manque — et le journal n'est jamais perdu (D2, D4) ;
 * 3. **un bail déjà ouvert est rejoint**, pas refusé (spec §7.2) ;
 * 4. **un refus est affiché tel quel**, avec la phrase de son code.
 */
class RecordControllerTest {

    private lateinit var server: MockWebServer
    private var previousLocale: Locale = Locale.getDefault()

    /** Une horloge qu'on avance à la main. */
    private class FakeClock(var now: Long = 1_000_000L) {
        fun advance(ms: Long) {
            now += ms
        }
    }

    @Before
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

    @Test
    fun `depart, gestes, arret, brouillon du serveur`() = runBlocking {
        answering(LEASE)
        answering(STOPPED)
        answering(DRAFT)

        val clock = FakeClock()
        val controller = controller(clock)

        controller.start("192.168.1.24:8765", "pc-7f21", width = 1_440, height = 900)
        assertEquals(RecordController.Phase.RECORDING, controller.state.value.phase)
        assertTrue(controller.recording)

        clock.advance(900)
        controller.noteTap(600f, 200f)
        clock.advance(400)
        controller.noteType("Rapport 2026", secret = false)
        clock.advance(100)
        controller.noteScroll(3)
        assertEquals(3, controller.state.value.actions)

        controller.finish()
        val state = controller.state.value
        assertEquals(RecordController.Phase.REVIEW, state.phase)
        assertNull(state.problem)
        assertNotNull(state.draft)
        assertEquals("creer-un-dossier", state.draft!!.name)
        assertEquals(2, state.draft!!.steps)
        assertTrue(state.draft!!.markdown.contains("Tap at 51%"))

        // Le journal envoyé porte bien les gestes, et l'adresse du pont réduite.
        server.takeRequest() // start
        val stopBody = server.takeRequest().body.readUtf8()
        assertTrue(stopBody.contains("\"kind\":\"tap\""))
        assertTrue(stopBody.contains("\"kind\":\"type\""))
        assertTrue(stopBody.contains("\"kind\":\"scroll\""))
        assertTrue(stopBody.contains("192.168.1.24:8765"))
        assertFalse(stopBody.contains("\"profile\""))
    }

    @Test
    fun `un bail deja ouvert est rejoint, pas refuse`() = runBlocking {
        answering(
            """{"detail":{"code":"already_recording","session_id":"rec_DEJAOUVERT","deadline_at":1000600.0}}""",
            code = 409,
        )

        val controller = controller(FakeClock())
        controller.start("192.168.1.24:8765", "pc-7f21", 1_440, 900)

        val state = controller.state.value
        assertEquals(RecordController.Phase.RECORDING, state.phase)
        assertEquals("rec_DEJAOUVERT", state.lease?.sessionId)
        assertTrue(state.lease!!.adopted)
        assertEquals("Un enregistrement est déjà ouvert sur ce serveur.", state.notice)
        assertNull(state.problem)
    }

    @Test
    fun `une route absente au depart est une phrase, pas un ecran muet`() = runBlocking {
        answering("""{"detail":"Not Found"}""", code = 404)

        val controller = controller(FakeClock())
        controller.start("192.168.1.24:8765", "pc-7f21", 1_440, 900)

        val state = controller.state.value
        assertEquals(RecordController.Phase.IDLE, state.phase)
        assertFalse(controller.recording)
        assertEquals(
            "Ce serveur ne sait pas enregistrer une démonstration (route absente).",
            state.problem,
        )
        // Aucun geste n'a été retenu : sans bail, rien ne s'écrit.
        assertEquals(0, controller.state.value.actions)
    }

    @Test
    fun `un bail expire garde le journal et le brouillon local`() = runBlocking {
        answering(LEASE)
        answering("""{"detail":{"code":"session_expired","message":"The window is over."}}""", code = 410)
        // `skills.draft` manque aussi : c'est le pire cas, et il doit rester lisible.
        answering("""{"detail":"Not Found"}""", code = 404)

        val clock = FakeClock()
        val controller = controller(clock)
        controller.start("192.168.1.24:8765", "pc-7f21", 800, 600)
        clock.advance(500)
        controller.noteTap(400f, 300f)
        controller.finish()

        val state = controller.state.value
        assertEquals(RecordController.Phase.REVIEW, state.phase)
        assertEquals(
            "La fenêtre d’enregistrement est expirée. Votre démonstration est restée sur cet appareil.",
            state.problem,
        )
        assertNull(state.draft)
        assertNotNull("le journal est converti sur l'appareil", state.localMarkdown)
        assertTrue(state.localMarkdown!!.contains("Tap at 50% / 50% of the window"))
        assertTrue(state.localReview.single().reason.contains("libellé absent"))

        // « En faire un skill » rend un nom et le Markdown, même sans le serveur.
        val kept = controller.keep()
        assertNotNull(kept)
        assertEquals("demonstration-enregistree", kept!!.first)
        assertTrue(kept.second.startsWith("# Démonstration enregistrée"))
    }

    @Test
    fun `jeter remet a zero et jette le journal en attente au prochain depart`() = runBlocking {
        answering(LEASE)
        answering(STOPPED)
        answering(DRAFT)

        val controller = controller(FakeClock())
        controller.start("192.168.1.24:8765", "pc-7f21", 1_440, 900)
        controller.noteTap(10f, 10f)
        controller.finish()
        controller.discard()

        assertEquals(RecordController.Phase.IDLE, controller.state.value.phase)
        assertNull(controller.state.value.draft)

        answering(LEASE)
        controller.start("192.168.1.24:8765", "pc-7f21", 1_440, 900)
        // start, stop, draft, puis le nouveau start : c'est ce dernier qui porte le drapeau.
        server.takeRequest()
        server.takeRequest()
        server.takeRequest()
        val body = server.takeRequest().body.readUtf8()
        assertTrue("le journal en attente est jeté : $body", body.contains("\"discard_pending\":true"))
    }

    @Test
    fun `un pont sans identifiant ne part pas`() = runBlocking {
        val controller = controller(FakeClock())
        controller.start("192.168.1.24:8765", "", 1_440, 900)

        assertEquals(RecordController.Phase.IDLE, controller.state.value.phase)
        assertNotNull(controller.state.value.problem)
        // Rien n'a été envoyé : le bail serait anonyme et le serveur le refuserait.
        assertNull(server.takeRequest(200, java.util.concurrent.TimeUnit.MILLISECONDS))
    }

    // MARK: - Outillage

    private fun controller(clock: FakeClock) = RecordController(
        api = SkillRecordApi(
            ServeClient(
                ServerConfig(
                    id = "enregistrement",
                    serveBaseUrl = server.url("/").toString().trimEnd('/'),
                    apiKey = "cle-1",
                    transport = Transport.HERMES_SERVE,
                ),
            ),
        ),
        clock = { clock.now },
    )

    private fun answering(body: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    private companion object {
        const val LEASE =
            """{"session_id":"rec_01","started_at":1000.0,"deadline_at":1000600.0,
               "max_seconds":600,"grace_seconds":120}"""

        const val STOPPED =
            """{"session_id":"rec_01","duration_ms":1400,"actions":3,"bytes":700,
               "truncated":false,"dropped":0,"end_reason":"stopped",
               "journal":{"version":1,"session_id":"rec_01","started_at":1000.0,
                 "duration_ms":1400,"end_reason":"stopped",
                 "screen":{"width_px":1440,"height_px":900,"scale":1.0},
                 "bridge":{"endpoint":"192.168.1.24:8765","session":"pc-7f21"},
                 "events":[
                   {"seq":1,"at_ms":900,"kind":"tap","target":{"x_pct":0.42,"y_pct":0.22}},
                   {"seq":2,"at_ms":1300,"kind":"type","target":{},"text":"Rapport 2026","chars":12},
                   {"seq":3,"at_ms":1400,"kind":"scroll","target":{},"direction":"down","amount":3}
                 ]}}"""

        const val DRAFT =
            """{"name":"creer-un-dossier","title":"Créer un dossier","steps":2,
               "markdown":"# Créer un dossier\n\n1. Tap \"Nouveau dossier\"\n2. Tap at 51% / 8% of the window — à relire : libellé absent\n",
               "review":[{"seq":9,"step":2,"reason":"no_label","line":4}],
               "warnings":[],"journal_bytes":700}"""
    }
}
