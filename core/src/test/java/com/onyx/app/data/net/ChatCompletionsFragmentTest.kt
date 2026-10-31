package com.onyx.app.data.net

import com.onyx.app.data.model.ChatRequest
import com.onyx.app.data.model.ChatTurn
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * **LE VERROU DU FLUX D'APPEL D'OUTIL.** Le fil d'un serveur OpenAI-compatible peut livrer une
 * seule trame d'appel d'outil **coupée entre deux morceaux** : l'app doit **assembler** les
 * fragments jusqu'à obtenir un JSON entier, puis l'analyser — un fragment partiel n'est **jamais**
 * une erreur, ne montre jamais le JSON brut, et ne laisse jamais fuir un chemin interne
 * (`/home/`, `.hermes`, `native-inputs`) ni une commande dans le texte visible.
 *
 * Le premier verrou est celui du propriétaire : un appel d'outil **découpé à CHAQUE position
 * d'octet** doit s'assembler **exactement**. Il échoue sur la tête avant correction (le fragment
 * partiel était jeté comme « fragment illisible ») et passe après.
 */
class ChatCompletionsFragmentTest {

    private fun enqueue(sse: String): Pair<MockWebServer, ServerConfig> {
        val server = MockWebServer()
        server.start()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sse),
        )
        val config = ServerConfig(
            id = "fragment-test",
            name = "Fragment test",
            baseUrl = server.url("/").toString().trimEnd('/'),
            transport = Transport.CHAT_COMPLETIONS,
        )
        return server to config
    }

    private fun events(sse: String): List<TurnEvent> {
        val (server, config) = enqueue(sse)
        return try {
            runBlocking {
                ChatCompletionsClient(config, OkHttpClient())
                    .stream(ChatRequest(history = listOf(ChatTurn(role = Role.USER, text = "salut"))))
                    .toList()
            }
        } finally {
            server.shutdown()
        }
    }

    /** La raison montrée par l'app pour un flux qui n'a pas produit de réponse. */
    private fun failureMessage(sse: String): String {
        val (server, config) = enqueue(sse)
        val error = try {
            runBlocking {
                ChatCompletionsClient(config, OkHttpClient())
                    .stream(ChatRequest(history = listOf(ChatTurn(role = Role.USER, text = "salut"))))
                    .toList()
            }
            null
        } catch (thrown: Throwable) {
            thrown
        } finally {
            server.shutdown()
        }
        assertTrue("le flux aurait dû échouer, pas rendre une réponse", error is TransportError)
        return (error as TransportError).userMessage(Locale.FRANCE)
    }

    /** Une trame de chunk contenant un appel d'outil, construite avec l'échappement réel du fil. */
    private fun toolChunk(arguments: String, callId: String? = "call_1"): String =
        buildJsonObject {
            putJsonArray("choices") {
                addJsonObject {
                    put("index", 0)
                    putJsonObject("delta") {
                        putJsonArray("tool_calls") {
                            addJsonObject {
                                put("index", 0)
                                if (callId != null) put("id", callId)
                                put("type", "function")
                                putJsonObject("function") {
                                    put("name", "terminal")
                                    put("arguments", arguments)
                                }
                            }
                        }
                    }
                }
            }
        }.toString()

    /** L'arguments de l'outil : contient une chaîne coupable, un échappement `\"`, un chemin interne. */
    private val toolArguments =
        "{\"execution_generation\":1,\"command\":\"sleep 3; cd /home/john/.hermes/cache/documents/" +
            "native-inputs/50829f630e53a20d850ebe358d573017e616cf1d4ff6a6b09261ae1b915c8c67/ && " +
            "python3 -c \\\"from PIL import Image\\\"\"}"

    /**
     * **Ce qui ne doit jamais se lire dans le texte visible** : le JSON brut, un chemin interne
     * (`/home/`, `.hermes`, `native-inputs`, `/tmp/`) ou la commande. C'est le balayage qu'opposent
     * les deux verrous de fuite.
     */
    private val leakMarkers =
        listOf("/home/", ".hermes", "native-inputs", "/tmp/", "{", "execution_generation", "sleep 3", "&&", "python3")

    /**
     * Le **texte visible** d'une suite d'événements : ce que le fil montre — le texte, le
     * raisonnement, le nom et le **libellé** d'un appel, le résultat d'un outil, un statut. Les
     * `args` d'un appel n'y sont pas : ils sont le contenu de la fiche de l'appel, pas le fil.
     */
    private fun visible(events: List<TurnEvent>): String = events.joinToString("\n") { event ->
        when (event) {
            is TurnEvent.TextDelta -> event.text
            is TurnEvent.TextFinal -> event.text
            is TurnEvent.ReasoningDelta -> event.text
            is TurnEvent.ToolStarted -> event.name + " " + event.preview.orEmpty()
            is TurnEvent.ToolFinished -> event.name + " " + event.output.orEmpty()
            is TurnEvent.Status -> event.text
            else -> ""
        }
    }

    // ---- VERROU 1 : découpe à chaque position d'octet --------------------------------

    @Test
    fun `a tool call chunk split at every byte position still assembles exactly`() {
        val chunk = toolChunk(toolArguments)
        for (cut in 0..chunk.length) {
            val sse = "data: " + chunk.substring(0, cut) + "\n\n" +
                "data: " + chunk.substring(cut) + "\n\n" +
                "data: [DONE]\n\n"
            val events = events(sse)
            val started = events.filterIsInstance<TurnEvent.ToolStarted>()
            assertEquals("coupure à l'octet $cut : un seul appel d'outil attendu", 1, started.size)
            assertEquals("coupure à l'octet $cut : le nom", "terminal", started.single().name)
            assertEquals("coupure à l'octet $cut : les arguments assemblés", toolArguments, started.single().args)
            // et rien de brut dans le texte visible : un découpage ne doit jamais *montrer* un morceau.
            val leaked = leakMarkers.filter { visible(events).contains(it) }
            assertTrue("coupure à l'octet $cut : fuite $leaked dans le texte visible", leaked.isEmpty())
        }
    }

    @Test
    fun `a tool call split across many then single byte pieces still assembles exactly`() {
        val chunk = toolChunk(toolArguments)
        // une coupure en morceaux d'un octet, puis deux morceaux : l'assemblage doit tenir.
        val pieces = chunk.map { it.toString() }
        val sse = pieces.joinToString("") { "data: $it\n\n" } + "data: [DONE]\n\n"
        val started = events(sse).filterIsInstance<TurnEvent.ToolStarted>()
        assertEquals(1, started.size)
        assertEquals("terminal", started.single().name)
        assertEquals(toolArguments, started.single().args)
    }

    // ---- cas : identifiant absent puis présent ---------------------------------------

    @Test
    fun `a tool call whose id only arrives with the second fragment still assembles`() {
        val head = buildJsonObject {
            putJsonArray("choices") {
                addJsonObject {
                    put("index", 0)
                    putJsonObject("delta") {
                        putJsonArray("tool_calls") {
                            addJsonObject {
                                put("index", 0)
                                putJsonObject("function") { put("arguments", toolArguments.substring(0, 40)) }
                            }
                        }
                    }
                }
            }
        }.toString()
        val tailArgs = toolArguments.substring(40)
        val tail = buildJsonObject {
            putJsonArray("choices") {
                addJsonObject {
                    put("index", 0)
                    putJsonObject("delta") {
                        putJsonArray("tool_calls") {
                            addJsonObject {
                                put("index", 0)
                                put("id", "call_late")
                                putJsonObject("function") {
                                    put("name", "terminal")
                                    put("arguments", tailArgs)
                                }
                            }
                        }
                    }
                }
            }
        }.toString()
        val sse = "data: $head\n\ndata: $tail\n\ndata: [DONE]\n\n"
        val started = events(sse).filterIsInstance<TurnEvent.ToolStarted>()
        assertEquals(1, started.size)
        assertEquals("terminal", started.single().name)
        assertEquals(toolArguments, started.single().args)
    }

    // ---- cas : plusieurs appels simultanés -------------------------------------------

    @Test
    fun `two parallel tool calls are assembled separately`() {
        val first = toolChunk("{\"command\":\"echo un\"}", callId = "call_a")
        val second = toolChunk("{\"command\":\"echo deux\"}", callId = "call_b")
        // le second appel est à l'index 1 : on le reconstruit ici.
        val secondIndexed = second.replace("\"index\":0", "\"index\":1")
        val sse = "data: $first\n\ndata: $secondIndexed\n\ndata: [DONE]\n\n"
        val started = events(sse).filterIsInstance<TurnEvent.ToolStarted>()
        assertEquals(2, started.size)
        assertEquals(setOf("{\"command\":\"echo un\"}", "{\"command\":\"echo deux\"}"), started.map { it.args }.toSet())
    }

    // ---- VERROU 1b : troncature à CHAQUE longueur — jamais une fuite ----------------

    /**
     * Le balayage **exhaustif** de la troncature : le flux s'arrête après `cut` caractères de la
     * trame d'appel d'outil, **sans** `[DONE]`. Pour **chaque** longueur possible, le texte visible
     * — les événements, ou la raison de l'échec — ne doit porter **ni** le JSON brut, **ni** un
     * chemin interne, **ni** la commande. C'est le balayage du propriétaire : pas trois essais
     * choisis, **toutes** les coupes, à l'octet près.
     */
    @Test
    fun `every cut length of a tool call frame leaks neither json nor a path nor a command`() {
        val chunk = toolChunk(toolArguments)
        for (cut in 0..chunk.length) {
            val sse = "data: " + chunk.substring(0, cut) + "\n\n"
            val shown = try {
                visible(events(sse))
            } catch (thrown: Throwable) {
                assertTrue(
                    "coupe à $cut : l'échec doit être un TransportError, pas $thrown",
                    thrown is TransportError,
                )
                (thrown as TransportError).userMessage(Locale.FRANCE)
            }
            val leaked = leakMarkers.filter { shown.contains(it) }
            assertTrue("coupe à $cut : fuite $leaked dans « $shown »", leaked.isEmpty())
            // quand le flux s'arrête avant la fin, la raison reste une phrase à nous, relançable.
            if (shown.isNotEmpty()) {
                assertTrue("coupe à $cut : la raison doit être lisible (« $shown »)", shown.any { it.isLetter() })
            }
        }
    }

    // ---- VERROU 2 : aucune fuite de JSON brut, de chemin ou de commande --------------

    @Test
    fun `a truncated fragment never shows raw json or an internal path`() {
        // le fragment du propriétaire, sans trame complète ni [DONE] : le flux s'est interrompu.
        val fragment = "{\"execution_generation\": 1, \"command\": \"sleep 3; cd " +
            "/home/john/.hermes/cache/documents/native-inputs/50829f630e53a20d850ebe358d573017e616cf1d4ff6a6b09261ae1b915c8c67/ " +
            "&& && python3 -c \\\"from PIL import"
        val shown = failureMessage("data: $fragment\n\n")
        assertFalse("fuite d'un chemin interne : « $shown »", shown.contains("/home/"))
        assertFalse("fuite de .hermes : « $shown »", shown.contains(".hermes"))
        assertFalse("fuite de native-inputs : « $shown »", shown.contains("native-inputs"))
        assertFalse("fuite du JSON brut : « $shown »", shown.contains("{"))
        assertFalse("fuite de la commande : « $shown »", shown.contains("sleep 3"))
        assertFalse("fuite du shell : « $shown »", shown.contains("&&"))
        assertFalse("fuite de python : « $shown »", shown.contains("python3"))
        // et elle dit ce qui s'est passé, en français lisible.
        assertTrue("la raison doit être lisible : « $shown »", shown.any { it.isLetter() })
    }

    /**
     * La **rangée** de l'appel porte le libellé du gateway — et le gateway y glisse le dossier de
     * travail de l'agent (le `f="/home/john/.hermes/…" + 3 commands` de la capture du
     * propriétaire). Le chemin interne ne doit pas atteindre la ligne ; le libellé, lui, reste.
     */
    @Test
    fun `a tool row preview never carries an internal path`() {
        val label = "f=\"/home/john/.hermes/cache/documents/native-inputs/" +
            "78d94c2b10cf940d8d380ad07ebb3e3579906b6e2d5c4be12d57353ad7825ef8/" +
            "api_78d94c2b10cf940d8d380ad07ebb3e35.png\" + 3 commands"
        val progress = buildJsonObject {
            put("tool", "terminal")
            put("toolCallId", "call_1")
            put("status", "running")
            put("label", label)
        }.toString()
        val sse = "event: hermes.tool.progress\n" + "data: " + progress + "\n\n" + "data: [DONE]\n\n"
        val started = events(sse).filterIsInstance<TurnEvent.ToolStarted>()
        assertEquals(1, started.size)
        val preview = started.single().preview.orEmpty()
        assertFalse("chemin interne dans la ligne d'appel : « $preview »", preview.contains("/home/"))
        assertFalse(".hermes dans la ligne d'appel : « $preview »", preview.contains(".hermes"))
        assertFalse("native-inputs dans la ligne d'appel : « $preview »", preview.contains("native-inputs"))
        // et le libellé reste **lisible** : il dit ce qui a été fait, et garde le nom du fichier.
        assertTrue("le libellé doit rester lisible : « $preview »", preview.contains("3 commands"))
        assertTrue("le nom du fichier reste : « $preview »", preview.contains("api_78d94c2b10cf940d8d380ad07ebb3e35.png"))
    }

    @Test
    fun `an unknown whole piece is ignored and never shown`() {
        val sse = "data: {\"surprise\":\"valeur\"}\n\ndata: [DONE]\n\n"
        val events = events(sse)
        // rien de montré, aucune erreur : un entier qu'on ne comprend pas n'est pas une fuite.
        assertTrue(events.none { (it as? TurnEvent.Status)?.text?.contains("{") == true })
    }
}
