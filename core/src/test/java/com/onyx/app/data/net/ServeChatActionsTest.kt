package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La lecture des trois routes de la feuille des actions — `session.usage` (+ le découpage du
 * contexte), `process.list`, `rollback.list` — et les deux gestes qui les suivent
 * (`process.kill`, `rollback.restore`).
 *
 * Ce qui compte ici : **aucun chiffre n'est inventé**. Un champ absent reste `null` et s'affiche
 * « — » ; une route qui n'existe pas rend un **échec**, jamais une liste vide — sans quoi l'écran
 * annoncerait « rien ne tourne » là où il n'a en fait rien pu lire.
 */
class ServeChatActionsTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Le relevé réel d'un `session.usage` (serveur Hermes 0.22.3), tel qu'il arrive. */
    private val liveUsage = json.parseToJsonElement(
        """
        {"model":"deepseek/deepseek-v4.1-flash","input":13381,"output":117,"reasoning":113,
         "prompt":15429,"completion":117,"total":15546,"calls":1,"context_used":15546,
         "context_max":1048576,"context_percent":1,"context_source":"provider_usage",
         "context_estimated":false,"compressions":0,"cache_hit_pct":13,"avg_latency_s":2.6,
         "avg_tps":44.8,"active_subagents":0}
        """.trimIndent(),
    )

    /** Le relevé réel d'un `session.context_breakdown`. */
    private val liveBreakdown = json.parseToJsonElement(
        """
        {"categories":[
           {"color":"var(--context-usage-system)","id":"system_prompt","label":"System prompt","tokens":4471},
           {"color":"var(--context-usage-tools)","id":"tool_definitions","label":"Tool definitions","tokens":9174},
           {"color":"var(--context-usage-subagents)","id":"subagent_definitions","label":"Subagent definitions","tokens":1166},
           {"color":"var(--context-usage-memory)","id":"memory","label":"Memory","tokens":1086},
           {"color":"var(--context-usage-conversation)","id":"conversation","label":"Conversation","tokens":302}],
         "context_max":1048576,"context_percent":1,"context_used":15546,"estimated_total":16199,
         "context_source":"provider_usage","context_estimated":false,"model":"deepseek/deepseek-v4.1-flash",
         "context_files":[
           {"label":"SOUL.md","path":"/home/john/.hermes/profiles/coder/SOUL.md","chars":663,
            "est_tokens":167,"loaded":true,"status":"loaded"}]}
        """.trimIndent(),
    )

    /** Le relevé réel d'une `session.resume` : la session vivante sous son identifiant d'exécution. */
    private val resumed = json.parseToJsonElement(
        """{"session_id":"e9a7d51b","resumed":"sess-1","messages_omitted":true,"message_count":4,"status":"idle"}""",
    )

    private fun actions(channel: RpcChannel) = ServeChatActions(ServerConfig(id = "qa"), channel)

    @Test
    fun `la consommation se lit champ par champ`() {
        val usage = ServeChatActions.usageOf(liveUsage, liveBreakdown)
        assertEquals("deepseek/deepseek-v4.1-flash", usage.model)
        assertEquals(13381, usage.inputTokens)
        assertEquals(117, usage.outputTokens)
        assertEquals(113, usage.reasoningTokens)
        assertEquals(15546, usage.totalTokens)
        assertEquals(1, usage.apiCalls)
        assertEquals(15546, usage.contextUsed)
        assertEquals(1048576, usage.contextMax)
        assertEquals(1, usage.contextPercent)
        assertEquals(13, usage.cacheShare()!!)
        assertFalse(usage.isEmpty)
        // Le prix n'est pas dans `session.usage` : il reste absent, il ne vaut pas zéro.
        assertNull(usage.estimatedCostUsd)
        assertNull(usage.actualCostUsd)
        assertNull(usage.costUsd)
    }

    @Test
    fun `ce qui remplit la fenetre vient du decoupage du contexte`() {
        val usage = ServeChatActions.usageOf(liveUsage, liveBreakdown)
        assertEquals(
            listOf("System prompt", "Tool definitions", "Subagent definitions", "Memory", "Conversation"),
            usage.contextSlices.map { it.label },
        )
        assertEquals(4471, usage.contextSlices.first().tokens)
        assertEquals(1, usage.contextFiles.size)
        assertEquals("SOUL.md", usage.contextFiles.single().label)
        assertEquals(167, usage.contextFiles.single().tokens)
    }

    @Test
    fun `la part du cache se calcule quand le serveur ne la donne pas`() {
        val withPercent = ServeChatActions.usageOf(
            json.parseToJsonElement("""{"cache_hit_pct":42}"""),
            null,
        )
        assertEquals(42, withPercent.cacheShare()!!)

        // Sans `cache_hit_pct`, la part se déduit des jetons lus dans le cache.
        val deduced = ServeChatActions.usageOf(
            json.parseToJsonElement("""{"input":75,"cache_read_tokens":25}"""),
            null,
        )
        assertEquals(25, deduced.cacheShare()!!)

        // Rien : la part reste inconnue, elle ne devient pas 0 %.
        assertNull(ServeChatActions.usageOf(JsonNull, null).cacheShare())
    }

    @Test
    fun `un champ absent s affiche en tiret, jamais en zero invente`() {
        assertEquals("\u2014", ServeChatActions.compact(null))
        assertEquals("\u2014", ServeChatActions.money(null))
        assertEquals("\u2014", ServeChatActions.count(null))
        assertEquals("\u2014", ServeChatActions.percent(null))
        assertEquals("8.2k", ServeChatActions.compact(8200))
        assertEquals("\$0", ServeChatActions.money(0.0))
        assertEquals("$0.0142", ServeChatActions.money(0.0142))
    }

    @Test
    fun `les processus se lisent et ceux qui tournent passent devant`() {
        val answer = json.parseToJsonElement(
            """
            {"processes":[
              {"session_id":"proc_done","command":"make test","status":"exited","exit_code":0,
               "completion_reason":"exited","uptime_seconds":41},
              {"session_id":"proc_live","command":"python3 serve.py","status":"running","uptime_seconds":12}]}
            """.trimIndent(),
        )
        val processes = ServeChatActions.processesOf(answer)
        assertEquals(listOf("proc_live", "proc_done"), processes.map { it.id })
        assertTrue(processes.first().running)
        assertFalse(processes.last().running)
        assertEquals(0, processes.last().exitCode)
    }

    @Test
    fun `une reponse sans processus est une liste vide, pas un echec`() {
        val processes = ServeChatActions.processesOf(json.parseToJsonElement("""{"processes":[]}"""))
        assertTrue(processes.isEmpty())
    }

    @Test
    fun `les points de restauration disent s ils sont ouverts`() {
        val disabled = ServeChatActions.restorePointsOf(json.parseToJsonElement("""{"enabled":false,"checkpoints":[]}"""))
        assertFalse(disabled.enabled)
        assertTrue(disabled.items.isEmpty())

        val live = ServeChatActions.restorePointsOf(
            json.parseToJsonElement(
                """{"enabled":true,"checkpoints":[{"hash":"a1b2c3d4","timestamp":"2026-10-27T09:12","message":"avant la refonte"}]}""",
            ),
        )
        assertTrue(live.enabled)
        assertEquals(1, live.items.size)
        assertEquals("a1b2c3d4", live.items.single().hash)
        assertEquals("avant la refonte", live.items.single().message)
    }

    @Test
    fun `une duree se dit en francais simple`() {
        assertNull(ServeChatActions.duration(null))
        assertNull(ServeChatActions.duration(-3))
        assertEquals("9 s", ServeChatActions.duration(9))
        assertEquals("3 min", ServeChatActions.duration(181))
        assertEquals("1 h 12 min", ServeChatActions.duration(4320))
    }

    @Test
    fun `un sujet de processus tient sur une ligne`() {
        assertEquals("make test", ServeChatActions.processSubject("make test\nsecond line"))
        val long = "x".repeat(400)
        assertEquals(120, ServeChatActions.processSubject(long).length)
        assertTrue(ServeChatActions.processSubject(long).endsWith("\u2026"))
    }

    // -- Les routes, par le canal ------------------------------------------------------------

    @Test
    fun `la consommation demande session_usage et le decoupage du contexte`() = runBlocking {
        val asked = mutableListOf<String>()
        val channel = RpcChannel { method, _, _ ->
            asked += method
            when (method) {
                "session.resume" -> resumed
                "session.usage" -> liveUsage
                else -> liveBreakdown
            }
        }
        val usage = actions(channel).usage("sess-1").getOrThrow()
        assertEquals(
            listOf("session.resume", "session.usage", "session.context_breakdown"),
            asked,
        )
        assertEquals(15546, usage.totalTokens)
    }

    // -- L'identifiant d'exécution ----------------------------------------------------------

    /**
     * Le défaut signalé : la feuille envoyait l'identifiant du **registre** (`api-…`, la clé de la
     * conversation) à `session.usage`, qui n'accepte que l'identifiant d'**exécution** — et le
     * gateway répondait `4001 session not found` sur une conversation parfaitement ordinaire.
     */
    @Test
    fun `la lecture demande d abord l identifiant d execution, et lit avec lui`() = runBlocking {
        val stored = "api-6796f7daf00648a4"
        val sent = mutableMapOf<String, String?>()
        val channel = RpcChannel { method, params, _ ->
            sent[method] = params["session_id"]?.toString()?.trim('"')
            when (method) {
                "session.resume" -> resumed
                "session.usage" -> liveUsage
                else -> liveBreakdown
            }
        }
        actions(channel).usage(stored).getOrThrow()
        // La reprise part avec la clé du **registre**…
        assertEquals(stored, sent["session.resume"])
        // …et la lecture, elle, part avec l'identifiant d'**exécution** que la reprise a rendu.
        assertEquals("e9a7d51b", sent["session.usage"])
        assertEquals("e9a7d51b", sent["session.context_breakdown"])
    }

    @Test
    fun `les processus et les points de restauration passent par le meme identifiant`() = runBlocking {
        val stored = "api-6796f7daf00648a4"
        val sent = mutableMapOf<String, String?>()
        val channel = RpcChannel { method, params, _ ->
            sent[method] = params["session_id"]?.toString()?.trim('"')
            when (method) {
                "session.resume" -> resumed
                "process.list" -> json.parseToJsonElement("""{"processes":[]}""")
                else -> json.parseToJsonElement("""{"enabled":true,"checkpoints":[]}""")
            }
        }
        actions(channel).processes(stored).getOrThrow()
        actions(channel).restorePoints(stored).getOrThrow()
        assertEquals("e9a7d51b", sent["process.list"])
        assertEquals("e9a7d51b", sent["rollback.list"])
    }

    @Test
    fun `une reprise refusee laisse l identifiant qu on avait`() = runBlocking {
        // Le serveur refuse la reprise : on rend la clé de reprise, et la lecture échouera
        // franchement — c'est la feuille qui le dira, en français.
        val channel = RpcChannel { method, _, _ ->
            when (method) {
                "session.resume" -> throw IllegalStateException("session not found")
                else -> liveUsage
            }
        }
        assertEquals("api-1", actions(channel).liveSessionId("api-1"))
        // Une réponse sans `session_id` ne fait pas mieux.
        assertEquals(
            "api-1",
            ServeChatActions.liveSessionIdOf(json.parseToJsonElement("""{"resumed":"api-1"}"""), "api-1"),
        )
    }

    @Test
    fun `l identifiant d execution se lit dans la reponse de la reprise`() {
        assertEquals(
            "e9a7d51b",
            ServeChatActions.liveSessionIdOf(
                json.parseToJsonElement("""{"session_id":"e9a7d51b","resumed":"api-6796f7daf00648a4"}"""),
                "api-6796f7daf00648a4",
            ),
        )
        // Vide ou absent : on ne rend pas une chaîne vide, on rend ce qu'on avait.
        assertEquals("api-1", ServeChatActions.liveSessionIdOf(JsonNull, "api-1"))
        assertEquals("api-1", ServeChatActions.liveSessionIdOf(json.parseToJsonElement("""{"session_id":"  "}"""), "api-1"))
    }

    @Test
    fun `un refus de session se dit en francais, jamais par son code`() {
        // « session not found » — le code brut du gateway — ne s'affiche plus tel quel.
        assertEquals(
            "Le serveur ne tient plus la session vivante de cette conversation.",
            ServeChatActions.failureReason("session not found"),
        )
        // « method not found » contient « not found » : il ne doit **pas** être lu comme une session
        // morte, sinon on enverrait la personne rouvrir une conversation bien vivante.
        assertEquals(
            "Ce serveur n'offre pas cette lecture.",
            ServeChatActions.failureReason("Method not found: process.list"),
        )
        // Un refus inconnu est encadré en français, la raison du serveur restant à côté.
        assertEquals(
            "Le serveur a refusé la lecture : boom",
            ServeChatActions.failureReason("boom"),
        )
        // Un motif muet est dit, pas laissé vide.
        assertEquals("le serveur n'a pas donné de raison", ServeChatActions.failureReason("   "))
    }

    @Test
    fun `un refus du serveur n est pas une liste vide`() = runBlocking {
        // `process.list` absent du serveur (ou session inconnue) : la lecture échoue et le dit.
        val channel = RpcChannel { _, _, _ -> throw IllegalStateException("unknown method: process.list") }
        val result = actions(channel).processes("sess-1")
        assertTrue("une route absente doit échouer, pas rendre une liste vide", result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("process.list"))
    }

    @Test
    fun `arreter un processus passe par process_kill et dit son motif`() = runBlocking {
        var params: JsonObject? = null
        val channel = RpcChannel { method, p, _ ->
            when (method) {
                "session.resume" -> resumed
                "process.kill" -> {
                    params = p
                    json.parseToJsonElement("""{"status":"killed"}""")
                }
                else -> error("méthode inattendue : $method")
            }
        }
        assertNull(actions(channel).stopProcess("sess-1", "proc_live"))
        // Le refus s'arrête à l'identifiant d'exécution : c'est lui que `process.kill` attend aussi.
        assertEquals("e9a7d51b", params!!["session_id"]!!.toString().trim('"'))
        assertEquals("proc_live", params!!["process_id"]!!.toString().trim('"'))

        val missing = RpcChannel { _, _, _ -> json.parseToJsonElement("""{"status":"not_found"}""") }
        assertEquals(
            "ce processus ne tourne plus",
            actions(missing).stopProcess("sess-1", "proc_live"),
        )
    }

    @Test
    fun `restaurer passe par rollback_restore et dit son motif`() = runBlocking {
        var params: JsonObject? = null
        val ok = RpcChannel { method, p, _ ->
            when (method) {
                "session.resume" -> resumed
                "rollback.restore" -> {
                    params = p
                    json.parseToJsonElement("""{"success":true,"restored_to":"a1b2c3d4"}""")
                }
                else -> error("méthode inattendue : $method")
            }
        }
        assertNull(actions(ok).restore("sess-1", "a1b2c3d4"))
        assertEquals("a1b2c3d4", params!!["hash"]!!.toString().trim('"'))
        assertEquals("e9a7d51b", params!!["session_id"]!!.toString().trim('"'))

        val refused = RpcChannel { _, _, _ ->
            json.parseToJsonElement("""{"success":false,"reason":"busy"}""")
        }
        assertEquals("busy", actions(refused).restore("sess-1", "a1b2c3d4"))
    }

    @Test
    fun `une session sans identifiant ne pose pas de session_id dans les parametres`() {
        assertFalse(ServeChatActions.sessionParams("").containsKey("session_id"))
        assertEquals("\"abc\"", ServeChatActions.sessionParams("abc")["session_id"].toString())
    }
}
