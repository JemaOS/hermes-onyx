package com.onyx.app.data.model

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les cartes d'une réponse (`ReplyCards`, `onyx_show`): lues avec indulgence depuis les arguments
 * de l'appel, gardées sur l'étape, retrouvées quand une conversation est relue, et remplies par la
 * réponse du plugin quand elle s'y lit. Même jeu d'épreuves que.
 */
class ReplyCardsTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    private fun parse(text: String) = ReplyCards.parse(Json.parseToJsonElement(text.trimIndent()))

    private fun card(text: String): List<ReplyCard> = parse("""{"cards": [$text]}""")

    @Test
    fun `une carte un chemin et ses lieux`() {
        val cards = parse(
            """
            {"cards": [{"type": "map", "title": "To the café", "route": {"mode": "Walking", "from": "here",
              "to": {"name": "Rossio Roasters", "address": "Rua das Portas 136", "lat": "38.7176", "lng": -9.1404}}},
              {"type": "map", "places": ["Tram 28 Café", {"name": "Nowhere", "latitude": 120, "longitude": 10}]}]}
            """,
        )
        assertEquals(2, cards.size)
        val walk = cards[0] as ReplyCard.Map
        val pins = cards[1] as ReplyCard.Map
        val route = walk.route!!
        // « here » est là où la personne est; les nombres passent en texte; lat et lng sont lus.
        assertEquals(ReplyCard.Route.Mode.WALKING, route.mode)
        assertNull(route.from)
        assertEquals("Rossio Roasters", route.to.name)
        assertEquals(38.7176, route.to.latitude!!, 0.0)
        assertEquals(-9.1404, route.to.longitude!!, 0.0)
        assertEquals(listOf("Tram 28 Café", "Nowhere"), pins.places.map { it.name })
        // Une coordonnée hors du globe est laissée de côté.
        assertTrue(!pins.places[1].hasCoordinate)
        assertNull(pins.places[1].latitude)
    }

    @Test
    fun `les lieux, lus avec indulgence`() {
        val cards = parse(
            """
            {"cards": [{"type": "places", "places": [{"name": "A", "rating": "4.6", "url": "javascript:alert(1)", "website": "x"},
              {"name": "B", "rating": 9, "url": "https://b.example"}, {"address": "no name"}]}]}
            """,
        )
        val list = cards.first() as ReplyCard.Places
        // Un lieu sans nom est écarté; une note est ramenée sur cinq; un lien qui n'est pas du web aussi.
        assertEquals(listOf("A", "B"), list.places.map { it.name })
        assertEquals(4.6, list.places[0].rating!!, 0.0)
        assertNull(list.places[0].url)
        assertEquals(5.0, list.places[1].rating!!, 0.0)
        assertEquals("https://b.example", list.places[1].url)
    }

    @Test
    fun `la meteo dans les mots qu elle veut`() {
        val cards = parse(
            """
            {"cards": [{"type": "weather", "location": "Boston", "units": "fahrenheit",
              "current": {"temp": "61°F", "condition": "Partly Cloudy", "humidity": "55%"},
              "hours": [{"time": "14:00", "temperature": 63, "condition": "sunny"}, {"time": "15:00", "condition": "rain"}],
              "days": [{"day": "2026-10-23", "high": 64, "low": 50, "condition": "Thunderstorms", "precipitation": 80}]},
              {"type": "weather", "place": "Nowhere"}]}
            """,
        )
        // Une prévision avec son seul lieu est gardée, pour être cherchée.
        assertEquals(2, cards.size)
        val weather = cards[0] as ReplyCard.Weather
        val bare = cards[1] as ReplyCard.Weather
        assertTrue(weather.hasReading)
        assertTrue(!bare.hasReading)
        assertEquals("Boston", weather.place)
        assertEquals("F", weather.units)
        assertEquals(61.0, weather.now!!.temperature, 0.0)
        assertEquals(ReplyCard.Condition.PARTLY_CLOUDY, weather.now!!.condition)
        assertEquals(55, weather.now!!.humidity)
        // Une heure sans température est écartée.
        assertEquals(1, weather.hours.size)
        assertEquals(ReplyCard.Condition.CLEAR, weather.hours[0].condition)
        assertEquals(ReplyCard.Condition.THUNDERSTORM, weather.days.first().condition)
        assertEquals(80, weather.days.first().precipitation)
        assertEquals(ReplyCard.Condition.UNKNOWN, ReplyCard.Condition.fromLenient("something odd"))
    }

    /**
     * Vu le 2026-10-23: la réponse disait 85 °F et ciel clair, et l'appel portait 0 et pas de
     * condition. Un relevé nomme sa condition: celui-là n'est pas dessiné, la carte n'a plus que
     * son lieu, à chercher.
     */
    @Test
    fun `un releve de remplissage n est pas dessine`() {
        val placeholder = """{"type":"weather","units":"F","now":{"temperature":0,"condition":""},"place":"Orlando FL"}"""
        assertTrue(!hasReading(card(placeholder)))
        val withDays = card(
            """{"type":"weather","place":"Orlando FL","now":{"temperature":0,"condition":""},""" +
                """"days":[{"date":"2026-10-24","high":88,"low":72,"condition":"clear"},{"date":"2026-10-25","high":0,"low":0}]}""",
        )
        val weather = withDays.first() as ReplyCard.Weather
        assertNull(weather.now)
        assertEquals(1, weather.days.size)
        assertEquals(88.0, weather.days[0].high, 0.0)
        val real = card("""{"type":"weather","place":"Orlando FL","units":"F","now":{"temperature":85,"condition":"clear"}}""")
        val reading = real.first() as ReplyCard.Weather
        assertEquals(85.0, reading.now!!.temperature, 0.0)
        assertEquals(ReplyCard.Condition.CLEAR, reading.now!!.condition)
        // Un vrai 0 vient avec ses autres nombres.
        assertEquals(1, card("""{"type":"weather","place":"Fairbanks","now":{"temperature":0,"condition":"snow","feels_like":-12}}""").size)
        // Plus tard le même jour: 0 et « clear », la météo jamais cherchée, rien d'autre lu avec: pas de relevé.
        assertTrue(
            !hasReading(
                card("""{"place":"Orlando FL","now":{"temperature":0,"condition":"clear"},"type":"weather","units":"F"}"""),
            ),
        )
    }

    private fun hasReading(cards: List<ReplyCard>): Boolean = (cards.first() as ReplyCard.Weather).hasReading

    @Test
    fun `l heure quelque part ou ici`() {
        val cards = parse(
            """
            {"cards": [{"type": "time", "place": "Lisbon", "timezone": "Europe/Lisbon"}, {"type": "time"},
                       {"type": "clock", "place": "Tokyo", "time_zone": "Mars/Base"}]}
            """,
        )
        assertEquals(
            listOf(
                ReplyCard.Clock(place = "Lisbon", timeZone = "Europe/Lisbon"),
                ReplyCard.Clock(place = null, timeZone = null),
                // Un fuseau qui n'en est pas un est laissé à chercher.
                ReplyCard.Clock(place = "Tokyo", timeZone = null),
            ),
            cards,
        )
        assertEquals("Same time", ReplyCard.Clock.difference(0))
        assertEquals("4 hr behind", ReplyCard.Clock.difference(-14_400))
        assertEquals("13 hr ahead", ReplyCard.Clock.difference(46_800))
        assertEquals("5½ hr ahead", ReplyCard.Clock.difference(19_800))
        assertEquals("5¾ hr ahead", ReplyCard.Clock.difference(20_700))
        assertEquals("½ hr ahead", ReplyCard.Clock.difference(1_800))
    }

    /** Le plugin 1.7.0 répond avec les cartes remplies: ce qu'il a cherché remplace l'appel. */
    @Test
    fun `la reponse du plugin prime sur l appel`() {
        var step = ToolActivity(
            id = "1",
            name = ReplyCards.tool,
            preview = null,
            status = ToolStatus.COMPLETED,
            args = ToolActivity.args(
                ReplyCards.tool,
                Json.parseToJsonElement(
                    """{"cards": [{"type": "weather", "place": "Orlando FL", "now": {"temperature": 66, "condition": "clear"}}]}""",
                ),
            ),
        )
        step = step.copy(
            output = """
            {"shown": ["weather: Orlando FL (now 75.5°F clear, 1 hours)"], "said": ["weather: Orlando FL: now 76°F clear"],
             "cards": [{"type": "weather", "place": "Orlando FL", "units": "F", "source": "Open-Meteo.com",
                        "now": {"temperature": 75.5, "condition": "clear", "night": true, "feels_like": 71.9, "wind": "12 mph NE"},
                        "hours": [{"time": "2026-10-23T19:00", "temperature": 70.1, "condition": "clear", "night": true, "precipitation": 0}], "days": []}]}
            """.trimIndent(),
        )
        val fromAnswer = ReplyCards.cards(from = step)!!.first() as ReplyCard.Weather
        assertEquals("F", fromAnswer.units)
        assertEquals(75.5, fromAnswer.now!!.temperature, 0.0)
        assertEquals(true, fromAnswer.now!!.night)
        assertEquals(true, fromAnswer.hours[0].night)
        // Un plugin plus ancien, ou une réponse illisible: l'appel, à chercher.
        val refused = step.copy(output = """{"error": "Nothing could be shown: card 1: the weather needs 'now'."}""")
        val fromCall = ReplyCards.cards(from = refused)!!.first() as ReplyCard.Weather
        assertEquals(66.0, fromCall.now!!.temperature, 0.0)
    }

    @Test
    fun `quatre au plus, et des genres connus`() {
        val one = """{"type": "places", "places": ["A"]}"""
        val cards = parse("""{"cards": [{"type": "chart"}, """ + List(6) { one }.joinToString(", ") + "]}")
        assertEquals(4, cards.size)
        // Une carte seule, sans la liste.
        assertEquals(1, parse("""{"type": "places", "places": ["A"]}""").size)
        // L'appel d'un autre outil ne donne rien.
        assertNull(ReplyCards.cards(fromCall = "web_search", args = Json.parseToJsonElement(one)))
    }

    @Test
    fun `les arguments de l appel restent du json sur son etape`() {
        val args = Json.parseToJsonElement("""{"cards": [{"type": "places", "places": [{"name": "A", "note": "line one\nline two"}]}]}""")
        val kept = ToolActivity.args(ReplyCards.tool, args)!!
        assertEquals(args, Json.parseToJsonElement(kept))
        // Tout autre outil se lit comme avant, en toutes lettres.
        val listed = ToolActivity.args("terminal", Json.parseToJsonElement("""{"command": "ls"}"""))
        assertEquals("command: ls", listed)
        val step = ToolActivity(id = "1", name = ReplyCards.tool, status = ToolStatus.COMPLETED, args = kept)
        assertEquals(1, ReplyCards.cards(from = step)!!.size)
        // Un appel que le plugin a refusé ne montre rien.
        assertNull(ReplyCards.cards(from = step.copy(status = ToolStatus.FAILED)))
    }

    @Test
    fun `une reponse relue retrouve ses cartes`() {
        val messages = listOf(
            ChatMessage(id = "u1", role = Role.USER, text = "Coffee?", createdAt = 0, tools = emptyList()),
            ChatMessage(
                id = "a1",
                role = Role.ASSISTANT,
                text = "Rossio Roasters is closest.",
                createdAt = 0,
                tools = listOf(
                    ToolActivity(
                        id = "t1",
                        name = ReplyCards.tool,
                        status = ToolStatus.COMPLETED,
                        args = """{"cards": [{"type": "places", "places": [{"name": "Rossio Roasters"}]}]}""",
                    ),
                ),
            ),
        )
        val resolved = ReplyCards.resolve(messages)
        val list = resolved[1].cards!!.first() as ReplyCard.Places
        assertEquals("Rossio Roasters", list.places.first().name)
        // Une réponse qui a déjà ses cartes n'est pas refaite.
        val kept = ReplyCards.resolve(resolved.map { it.copy(cards = emptyList()) })
        assertEquals(emptyList<ReplyCard>(), kept[1].cards)
    }

    @Test
    fun `les cartes survivent a l enregistrement de la conversation`() {
        val message = ChatMessage(
            id = "a1",
            role = Role.ASSISTANT,
            text = "Here you are.",
            createdAt = 1_700_000_000_000,
            cards = parse(
                """
                {"cards": [{"type": "map", "title": "To Rossio", "places": [{"name": "Rossio Roasters", "latitude": 38.7, "longitude": -9.14}]},
                  {"type": "weather", "place": "Lisbon", "units": "C", "now": {"temperature": 19, "condition": "partly_cloudy", "humidity": 62}},
                  {"type": "time", "place": "Tokyo", "timezone": "Asia/Tokyo"}]}
                """,
            ),
        )
        val text = json.encodeToString(ListSerializer(ChatMessage.serializer()), listOf(message))
        val read = json.decodeFromString(ListSerializer(ChatMessage.serializer()), text)
        assertEquals(message.cards, read.first().cards)
    }
}
