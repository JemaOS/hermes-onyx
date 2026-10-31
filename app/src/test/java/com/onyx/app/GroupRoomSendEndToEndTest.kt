package com.onyx.app

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import com.onyx.app.data.model.serve.GroupMember
import com.onyx.app.data.model.serve.GroupRoom
import com.onyx.app.data.net.ServeGroupsRpc
import com.onyx.app.data.net.ServeRpc
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.bots.GroupRoomFeed
import com.onyx.app.ui.bots.GroupVoiceReplies
import com.onyx.app.ui.bots.voiceLines
import java.util.Locale
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** La reponse du bot de la salle, mot pour mot. */
private const val ANSWER = "Samedi 8 h : je note la sortie et je previen(s) les autres."

/**
 * Le fil d'une salle jusqu'au bout du fil : `GroupRoomFeed` → `groups.send` → le canal du Dashboard
 * (ticket, WebSocket, trames JSON-RPC) → une salle qui garde ses événements → le journal relu.
 *
 * C'est la preuve que ce qui est écrit dans une salle **y est écrit** : le serveur de ce test tient
 * son propre journal, et c'est lui — pas l'application — qui le rend au fil.
 */
class GroupRoomSendEndToEndTest {

    private lateinit var dashboard: MockWebServer

    /** Le journal de la salle, tel qu'un Dashboard le tiendrait. */
    private val roomLog = mutableListOf<JsonElement>()
    private val sentEventIDs = mutableListOf<String>()

    /** La salle **repond** a la question : c'est ce que fait le pilote de la passerelle. */
    private var answers = false
    private val stopped = mutableListOf<String>()
    private var nextSeq = 1

    /**
     * Une **panne injectée** : le serveur refuse les N prochains `groups.send` (erreur interne
     * -32603), puis répond normalement. C'est le défaut que le bot a décrit — « mon message
     * précédent est parti en erreur » —, rejoué exprès.
     */
    private var failingSends = 0

    /** Chaque essai de `groups.send`, accepté ou non : la trace de la reprise. */
    private val sendAttempts = mutableListOf<String>()

    private val config = ServerConfig(
        id = "server",
        baseUrl = "",
        model = "hermes-agent",
        transport = Transport.HERMES_SERVE,
        serveBaseUrl = "http://placeholder",
        serveUsername = "onyx",
        servePassword = "port-only",
    )

    private val room = GroupRoom(
        id = "onyx-1",
        name = "Weekend hike",
        members = listOf(
            GroupMember(id = "default", profile = "default", handle = "hermes", displayName = "Onyx"),
            GroupMember(id = "scout", profile = "scout", handle = "scout", displayName = "Scout"),
        ),
    )

    @Before
    fun start() {
        dashboard = MockWebServer()
        dashboard.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path?.startsWith("/api/auth/ws-ticket") == true ->
                    MockResponse().setBody("""{"ticket":"ticket-1"}""")

                request.path?.startsWith("/api/ws") == true ->
                    MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                        override fun onMessage(webSocket: WebSocket, text: String) {
                            val frame = Json.parseToJsonElement(text).jsonObject
                            val id = frame["id"]?.jsonPrimitive?.content ?: return
                            val method = frame["method"]?.jsonPrimitive?.content.orEmpty()
                            val params = frame["params"]?.jsonObject ?: buildJsonObject { }
                            // La panne injectée : le serveur échoue **une fois** sur l'envoi, puis
                            // prend. Un client qui ne réessaie pas perd le message ; un client qui
                            // réessaie sans le même identifiant le fait entrer deux fois.
                            if (method == "groups.send") {
                                sendAttempts += params["event_id"]?.jsonPrimitive?.content.orEmpty()
                                if (failingSends > 0) {
                                    failingSends -= 1
                                    webSocket.send(
                                        buildJsonObject {
                                            put("jsonrpc", "2.0")
                                            put("id", id)
                                            put(
                                                "error",
                                                buildJsonObject {
                                                    put("code", -32603)
                                                    put("message", "internal error: room driver busy")
                                                },
                                            )
                                        }.toString(),
                                    )
                                    return
                                }
                            }
                            val result = answer(method, params)
                            webSocket.send(
                                buildJsonObject {
                                    put("jsonrpc", "2.0")
                                    put("id", id)
                                    put("result", result)
                                }.toString(),
                            )
                        }
                    })

                else -> MockResponse().setResponseCode(404)
            }
        }
        dashboard.start()
        val address = dashboard.url("/").toString().removeSuffix("/")
        configWithAddress(address)
    }

    private var live: ServerConfig = config

    private fun configWithAddress(address: String) {
        live = config.copy(serveBaseUrl = address)
    }

    @After
    fun stop() {
        dashboard.shutdown()
    }

    /** Ce que ce Dashboard répond, et ce qu'il retient. */
    private fun answer(method: String, params: JsonObject): JsonElement = when (method) {
        "groups.capabilities" -> buildJsonObject {
            put(
                "methods",
                buildJsonArray {
                    listOf("groups.list", "groups.create", "groups.state", "groups.send", "groups.log")
                        .forEach { add(JsonPrimitive(it)) }
                },
            )
            put("driver", true)
        }

        // Le journal de la salle, entier : c'est `groups.log` qui rogne après `since_seq`.
        "groups.log" -> buildJsonObject {
            put("events", buildJsonArray { roomLog.forEach { add(it) } })
            put("cursor", nextSeq - 1)
            put("has_more", false)
        }

        "groups.state" -> buildJsonObject {
            put("room", buildJsonObject {
                put("room_id", room.id)
                put("name", room.name)
                if (answers) {
                    // Une salle qui repond dit aussi **ses membres**, avec leur identifiant de salle
                    // (`member_id`), qui n'est pas leur profil : c'est ce que la passerelle renvoie,
                    // et c'est par lui que le fil retrouve le bot derriere chaque reponse.
                    put("members", buildJsonArray {
                        add(buildJsonObject {
                            put("member_id", "m-1"); put("profile", "default")
                            put("handle", "hermes"); put("display_name", "Onyx")
                        })
                        add(buildJsonObject {
                            put("member_id", "m-2"); put("profile", "scout")
                            put("handle", "scout"); put("display_name", "Scout")
                        })
                    })
                }
            })
            put("driver_status", buildJsonObject { put("working", false) })
        }

        "groups.send" -> {
            val eventID = params["event_id"]?.jsonPrimitive?.content.orEmpty()
            val said = params["payload"]?.jsonObject?.get("text")?.jsonPrimitive?.content.orEmpty()
            val thread = params["payload"]?.jsonObject?.get("thread_id")?.jsonPrimitive?.content
            sentEventIDs += eventID
            // Une salle reconnaît l'`event_id` : un message rejoué sous le même identifiant n'entre
            // qu'une fois. C'est ce qui rend la reprise sûre, même si la réponse s'est perdue.
            val known = roomLog.any { event ->
                event.jsonObject["payload"]?.jsonObject?.get("event_id")?.jsonPrimitive?.content == eventID
            }
            if (!known) {
                // Ce que la salle retient : le message, sous le fil « main », avec son identifiant.
                roomLog += buildJsonObject {
                    put("seq", nextSeq)
                    put("kind", "message.user")
                    put("actor", buildJsonObject { put("kind", "user"); put("id", "desktop") })
                    put("payload", buildJsonObject {
                        put("text", said)
                        put("thread_id", thread)
                        put("event_id", eventID)
                    })
                }
                nextSeq += 1
            }
            nextSeq += 1
            if (answers) {
                // Le bot de la salle ecrit sa reponse : le membre **de la salle** (son identifiant
                // differe de son profil, comme dans une salle ouverte depuis un autre appareil).
                roomLog += buildJsonObject {
                    put("seq", nextSeq)
                    put("kind", "message.member")
                    put("actor", buildJsonObject {
                        put("kind", "member"); put("id", "m-2"); put("profile", "scout")
                    })
                    put("payload", buildJsonObject {
                        put("text", ANSWER)
                        put("thread_id", thread)
                        put("member_id", "m-2")
                    })
                }
                nextSeq += 1
            }
            buildJsonObject { put("accepted", true) }
        }

        "groups.stop" -> {
            stopped += params["cancel_id"]?.jsonPrimitive?.content.orEmpty()
            buildJsonObject { put("stopped", true) }
        }

        else -> JsonObject(emptyMap())
    }

    @Test
    fun `un message ecrit dans une salle y est ecrit, et revient par son journal`() = runBlocking {
        val feed = GroupRoomFeed(room, ServeGroupsRpc(ServeRpc(live, OkHttpClient())))
        assertTrue("la salle part vide", feed.state.value.events.isEmpty())

        val id = feed.beginSend("Bonjour à tous")
        feed.deliver(id, "Bonjour à tous")

        // La salle l'a retenu : c'est ce que `groups.send` a écrit, avec l'identifiant du message.
        assertEquals(1, roomLog.size)
        val kept = roomLog.single().jsonObject
        assertEquals("message.user", kept["kind"]!!.jsonPrimitive.content)
        assertEquals("Bonjour à tous", kept["payload"]!!.jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals("main", kept["payload"]!!.jsonObject["thread_id"]!!.jsonPrimitive.content)
        assertEquals(id, sentEventIDs.single())
        assertEquals(id, kept["payload"]!!.jsonObject["event_id"]!!.jsonPrimitive.content)

        // Et il revient au fil par le journal de la salle, plus par l'attente locale.
        val state = feed.state.value
        assertEquals(listOf("Bonjour à tous"), state.events.filter { it.kind == "message.user" }.map { it.text })
        assertTrue("le message n'attend plus", state.sending.isEmpty())
        assertEquals(1, state.cursor)
    }

    /**
     * **Le defaut rapporte : la question part et personne ne repond.** Ce test casse si une question
     * envoyee ne produit **aucune reponse affichee** : il exige que ce que la salle a repondu
     * arrive dans les lignes de la scene vocale — avec le **profil** du bot (donc sa voix), son
     * **nom** (donc son avatar) et son **texte** — et que la file de lecture la prenne, une fois.
     *
     * Rejoue le vrai chemin : `groups.send` sur le canal du Dashboard, la salle qui tient son
     * journal, `groups.log` qui le rend, `voiceLines` qui en fait des lignes, `GroupVoiceReplies`
     * qui les met en file. Aucune doublure entre les deux bouts.
     */
    @Test
    fun `une question envoyee produit la reponse du bot, affichee et mise en file de lecture`() = runBlocking {
        answers = true
        val feed = GroupRoomFeed(room, ServeGroupsRpc(ServeRpc(live, OkHttpClient())))
        // Ce que la voix connaissait avant de poser la question : rien.
        val replies = GroupVoiceReplies(feed.state.value.voiceLines())

        val id = feed.beginSend("Qui vient samedi ?")
        feed.deliver(id, "Qui vient samedi ?")

        // 1. La salle a repondu, et c'est son journal qui le dit.
        val said = feed.state.value.events.filter { it.kind == "message.member" }
        assertEquals(1, said.size)
        // L'identifiant du membre dans la salle n'est **pas** son profil : c'est `m-2`, et c'est le
        // profil de la salle qui dit a quel bot la reponse appartient (`voiceLines`).
        assertEquals("m-2", said.single().actorID)

        // 2. Ce qui s'affiche : une seule ligne, celle du bot, avec de quoi la dessiner et la lire.
        val lines = feed.state.value.voiceLines()
        val reply = lines.filter { !it.isMine && it.seq != null }
        assertEquals("une reponse affichee", 1, reply.size)
        assertEquals("le profil du bot (sa voix)", "scout", reply.single().profile)
        assertEquals("son nom (son avatar)", "Scout", reply.single().name)
        assertEquals("son texte", ANSWER, reply.single().text)
        assertTrue("elle n'est pas en echec", !reply.single().failed)

        // 3. Et elle est **lue** : la file la prend, une seule fois, avec le profil de son bot.
        assertTrue("la file voit du nouveau", replies.update(lines, held = false))
        val reading = replies.takeNext()
        assertEquals("c'est la reponse du bot qui est lue", ANSWER, reading?.text)
        assertEquals("chaque bot garde sa voix", "scout", reading?.profile)
        assertNull("et une seule fois", replies.takeNext())
    }

    @Test
    fun `l arret d une salle part au Dashboard avec un identifiant neuf`() = runBlocking {
        val feed = GroupRoomFeed(room, ServeGroupsRpc(ServeRpc(live, OkHttpClient())))

        feed.stop()

        assertEquals(1, stopped.size)
        assertTrue(stopped.single().startsWith("stop-"))
    }

    // — Vingt tours, et un envoi qui échoue une fois ---------------------------------------------

    /** Le fil branché sur **ce** Dashboard, avec des poses d'envoi instantanées pour la preuve. */
    private fun feed(wait: Boolean = false): GroupRoomFeed = GroupRoomFeed(
        initialRoom = room,
        api = ServeGroupsRpc(ServeRpc(live, OkHttpClient())),
        sendBackoffMs = listOf(10L, 20L, 30L, 40L),
        waitSend = { ms -> if (wait) kotlinx.coroutines.delay(ms) },
    )

    @Test
    fun `vingt tours d affilee font vingt messages dans la salle, jamais vingt et un`() = runBlocking {
        val feed = feed()
        val questions = (1..20).map { turn -> "Tour $turn : quel temps fera-t-il demain ?" }

        for ((index, question) in questions.withIndex()) {
            feed.ask(question, "ask-${index + 1}")
            // Le même tour rejoué par l'écran (recomposition, phase qui repasse) : même identifiant.
            feed.ask(question, "ask-${index + 1}")
        }

        val said = roomLog.map { event ->
            event.jsonObject["payload"]!!.jsonObject["text"]!!.jsonPrimitive.content
        }
        assertEquals("vingt tours, vingt messages — jamais vingt et un", 20, said.size)
        assertEquals("ce sont les questions de la personne", questions, said)
        assertEquals(
            "vingt identifiants distincts, aucun rejoué",
            20,
            sentEventIDs.toSet().size,
        )
        assertEquals(
            "vingt essais d'envoi : le rejeu n'a même pas atteint le serveur",
            20,
            sendAttempts.size,
        )
        assertTrue("le fil n'attend plus rien", feed.state.value.sending.isEmpty())
        assertNull("et rien à signaler", feed.state.value.problem)
        // La trace du tour, telle qu'on la relit pour la preuve.
        for (turn in 0 until 20) {
            println(
                "TOUR ${turn + 1} | id=ask-${turn + 1} | envoyé=« ${said[turn]} » | 1 envoi | " +
                    "1 message dans la salle (seq=${turn + 1})",
            )
        }
    }

    @Test
    fun `un envoi qui echoue une fois est repris sans doublon`() = runBlocking {
        // La panne : le serveur refuse le premier `groups.send`, puis prend.
        failingSends = 1
        val feed = feed(wait = true)

        feed.ask("vous êtes là ?", "ask-panne")

        // Deux essais, **le même** identifiant : c'est lui qui empêche le doublon.
        assertEquals("deux essais d'envoi", 2, sendAttempts.size)
        assertEquals("le même `event_id` aux deux essais", 1, sendAttempts.toSet().size)
        assertEquals("un seul message dans la salle", 1, roomLog.size)
        val kept = roomLog.single().jsonObject
        assertEquals(
            "vous êtes là ?",
            kept["payload"]!!.jsonObject["text"]!!.jsonPrimitive.content,
        )
        assertEquals(
            "ask-panne",
            kept["payload"]!!.jsonObject["event_id"]!!.jsonPrimitive.content,
        )
        assertTrue("le message n'attend plus", feed.state.value.sending.isEmpty())
        assertTrue("et rien n'est signalé : il est arrivé", feed.state.value.problem == null)
        assertTrue(
            "le journal dit la reprise",
            com.onyx.app.voice.VoiceDiagnostics.lines().any { it.contains("envoi en échec (1/") },
        )
    }

    @Test
    fun `un envoi qui echoue pour de bon est dit en francais, et le message reste a l ecran`() = runBlocking {
        // Le serveur ne prend plus rien : les quatre poses s'épuisent.
        failingSends = 99
        val feed = feed(wait = true)

        feed.ask("vous êtes là ?", "ask-perdu")

        assertEquals("cinq essais, pas un de moins", 5, sendAttempts.size)
        assertTrue("rien n'est entré dans la salle", roomLog.isEmpty())
        // **Il ne disparaît pas** : la bulle reste, marquée, avec sa raison.
        val pending = feed.state.value.sending.single()
        assertEquals("ask-perdu", pending.id)
        assertTrue("le message est marqué en échec", pending.failed)
        // Et c'est **dit**, dans la langue de l'app — en français, exactement ceci.
        val shown = feed.state.value.problem.orEmpty()
        assertTrue("le problème s'affiche", shown.contains("internal error: room driver busy"))
        val template = "Your message did not reach the room: %1\$s"
        assertEquals(
            "Votre message n’a pas pu être envoyé dans la salle : %1\$s",
            OnyxLocalization.translate(template, Locale.FRENCH),
        )
        assertEquals(
            "le gabarit se remplit dans la langue demandée",
            "Votre message n’a pas pu être envoyé dans la salle : room driver busy",
            OnyxLocalization.format(template, "room driver busy", Locale.FRENCH),
        )
        // La bulle reste à l'écran : le message n'a pas disparu en silence.
        assertEquals("le message est toujours là", "vous êtes là ?", pending.text)
    }
}
