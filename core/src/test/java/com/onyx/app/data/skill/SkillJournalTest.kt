package com.onyx.app.data.skill

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le journal sur le fil**, confronté à la spec §2 — sans greffon Python.
 *
 * Ce que ce fichier tient :
 *
 * 1. **la forme** — `version`, `session_id`, `started_at`, `duration_ms`, `end_reason`, `screen`,
 *    `bridge`, `events` ; `seq` qui part de 1 et ne saute pas ;
 * 2. **le secret** — un `type` secret ne porte **aucun** `text`, et `chars` vaut zéro : la longueur
 *    d'un mot de passe est déjà une information (voir l'en-tête de [SkillJournal]) ;
 * 3. **les refus de la spec** — un défilement horizontal, une adresse `file:` ne partent pas ;
 * 4. **l'aller-retour** — le journal canonique relu rend les mêmes pourcentages, donc le même
 *    brouillon.
 */
class SkillJournalTest {

    private fun entry(atMs: Long, action: TracedAction) = SkillRecorder.Entry(atMs, action)

    private fun json(
        entries: List<SkillRecorder.Entry>,
        width: Int = 1_000,
        height: Int = 800,
        expired: Boolean = false,
    ): JsonObject = SkillJournal.toJson(
        entries = entries,
        sessionId = "rec_01J9Z3K4Q7V8",
        startedAtSeconds = 1_730_650_000.123,
        durationMs = 12_000,
        screenWidth = width,
        screenHeight = height,
        bridgeEndpoint = "192.168.1.24:8765",
        bridgeSession = "pc-7f21",
        endReason = if (expired) "expired" else "stopped",
        title = "Créer un dossier",
    )

    private fun JsonObject.event(index: Int): JsonObject = (this["events"] as JsonArray)[index].jsonObject

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.content

    @Test
    fun `l objet racine porte les champs de la spec`() {
        val body = json(listOf(entry(0, TracedAction.Tap(421f, 183f))))
        assertEquals("1", body.string("version"))
        assertEquals("rec_01J9Z3K4Q7V8", body.string("session_id"))
        assertEquals("12000", body.string("duration_ms"))
        assertEquals("stopped", body.string("end_reason"))
        assertEquals("Créer un dossier", body.string("title"))

        val screen = body["screen"]!!.jsonObject
        assertEquals("1000", screen.string("width_px"))
        assertEquals("800", screen.string("height_px"))
        assertEquals("1.0", screen.string("scale"))

        val bridge = body["bridge"]!!.jsonObject
        assertEquals("192.168.1.24:8765", bridge.string("endpoint"))
        assertEquals("pc-7f21", bridge.string("session"))
    }

    @Test
    fun `les evenements sont numerotes de un, dans l ordre`() {
        val body = json(
            listOf(
                entry(0, TracedAction.Tap(10f, 10f)),
                entry(400, TracedAction.Type("Rapport")),
                entry(900, TracedAction.Key(NamedKey.ESCAPE)),
            ),
        )
        val events = body["events"] as JsonArray
        assertEquals(listOf("1", "2", "3"), events.map { it.jsonObject.string("seq") })
        assertEquals(listOf("0", "400", "900"), events.map { it.jsonObject.string("at_ms") })
        assertEquals(listOf("tap", "type", "key"), events.map { it.jsonObject.string("kind") })
        assertEquals("escape", events[2].jsonObject.string("key"))
    }

    @Test
    fun `un appui porte ses pourcentages en fractions bornees`() {
        val body = json(listOf(entry(0, TracedAction.Tap(421f, 183f))), width = 1_000, height = 1_000)
        val target = body.event(0)["target"]!!.jsonObject
        assertEquals("0.421", target.string("x_pct"))
        assertEquals("0.183", target.string("y_pct"))
        // Hors de la fenêtre : borné, jamais « 1.03 ».
        val far = json(listOf(entry(0, TracedAction.Tap(5_000f, -80f))), width = 1_000, height = 1_000)
        assertEquals("1.0", far.event(0)["target"]!!.jsonObject.string("x_pct"))
        assertEquals("0.0", far.event(0)["target"]!!.jsonObject.string("y_pct"))
    }

    @Test
    fun `une fenetre inconnue n ecrit aucun pourcentage`() {
        val body = json(listOf(entry(0, TracedAction.Tap(12f, 34f, label = "Nouveau"))), width = 0, height = 0)
        val target = body.event(0)["target"]!!.jsonObject
        assertNull(target["x_pct"])
        assertNull(target["y_pct"])
        assertEquals("Nouveau", target.string("label"))
        // La fenêtre envoyée reste positive : le serveur refuse une taille nulle.
        assertEquals("1", body["screen"]!!.jsonObject.string("width_px"))
    }

    @Test
    fun `un texte secret ne porte ni texte ni longueur`() {
        val body = json(listOf(entry(0, TracedAction.Type("", isSecret = true))))
        val event = body.event(0)
        assertNull(event["text"])
        assertEquals("true", event.string("secret"))
        assertEquals("0", event.string("chars"))
        assertFalse(body.toString().contains("mot-de-passe"))
    }

    @Test
    fun `un defilement horizontal et une adresse locale ne partent pas`() {
        val body = json(
            listOf(
                entry(0, TracedAction.Scroll(ScrollDirection.LEFT, 3)),
                entry(100, TracedAction.Navigate("file:///tmp/secret.txt")),
                entry(200, TracedAction.Navigate("https://exemple.fr/rapport")),
                entry(300, TracedAction.Scroll(ScrollDirection.DOWN, 760)),
            ),
        )
        val events = body["events"] as JsonArray
        assertEquals(2, events.size)
        // La numérotation reste contiguë : les étapes abandonnées ne laissent pas de trou.
        assertEquals(listOf("1", "2"), events.map { it.jsonObject.string("seq") })
        assertEquals("navigate", events[0].jsonObject.string("kind"))
        assertEquals("https://exemple.fr/rapport", events[0].jsonObject.string("url"))
        assertEquals("scroll", events[1].jsonObject.string("kind"))
        assertEquals("760", events[1].jsonObject.string("amount"))
    }

    @Test
    fun `le journal canonique relu rend les memes gestes`() {
        val actions = listOf(
            TracedAction.Tap(421f, 183f, label = null),
            TracedAction.Type("Rapport 2026"),
            TracedAction.Type("", isSecret = true),
            TracedAction.Scroll(ScrollDirection.DOWN, 760),
            TracedAction.Navigate("https://exemple.fr/rapport"),
            TracedAction.Wait(1_200),
            TracedAction.Key(NamedKey.BACK),
        )
        val body = json(actions.indices.map { entry(it * 100L, actions[it]) })
        val read = SkillJournal.fromJson(body)

        assertEquals(1_000, read.windowWidth)
        assertEquals(800, read.windowHeight)
        assertEquals("Créer un dossier", read.title)
        assertEquals(actions.size, read.actions.size)
        assertEquals(actions[1], read.actions[1])
        assertEquals(actions[2], read.actions[2])
        assertEquals(actions[3], read.actions[3])
        assertEquals(actions[5], read.actions[5])
        assertEquals(actions[6], read.actions[6])

        // Un appui muet relu retombe sur le **même** pourcentage que celui écrit.
        val tap = read.actions[0] as TracedAction.Tap
        assertEquals(421f, tap.x, 0.5f)
        assertEquals(183f, tap.y, 0.5f)
        assertNull(tap.label)
    }

    @Test
    fun `le brouillon du journal relu est le meme octet`() {
        val actions = listOf(
            TracedAction.Tap(421f, 183f),
            TracedAction.Type("Rapport 2026"),
            TracedAction.Scroll(ScrollDirection.DOWN, 500),
            TracedAction.Scroll(ScrollDirection.DOWN, 260),
        )
        val original = ActionJournal(actions, windowWidth = 1_000, windowHeight = 800, title = "Facture")
        val read = SkillJournal.fromJson(json(actions.indices.map { entry(it * 100L, actions[it]) }))
            .copy(title = original.title)
        assertEquals(skillDraftMarkdown(original), skillDraftMarkdown(read))
    }

    @Test
    fun `un evenement inconnu est ignore, jamais devine`() {
        val body = json(
            listOf(
                entry(0, TracedAction.Tap(10f, 10f)),
                entry(100, TracedAction.Key(NamedKey.HOME)),
            ),
        )
        // Le serveur peut ajouter `focus`, que le modèle du lot ne convertit pas.
        val withFocus = JsonObject(
            body + mapOf(
                "events" to JsonArray(
                    listOf(
                        kotlinx.serialization.json.buildJsonObject {
                            put("seq", 1)
                            put("at_ms", 0)
                            put("kind", "focus")
                        },
                    ) + (body["events"] as JsonArray),
                ),
            ),
        )
        val read = SkillJournal.fromJson(withFocus)
        assertEquals(2, read.actions.size)
    }
}
