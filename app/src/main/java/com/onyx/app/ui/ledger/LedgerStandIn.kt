package com.onyx.app.ui.ledger

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.SubagentActivity
import com.onyx.app.data.model.SubagentStatus
import com.onyx.app.data.model.TokenUsage
import com.onyx.app.data.model.ToolActivity
import com.onyx.app.data.model.ToolStatus
import com.onyx.app.data.model.TurnMetrics
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.data.model.serve.LedgerScope
import com.onyx.app.data.net.SessionSummary
import com.onyx.app.ui.design.ServerPrice

/**
 * La **doublure de QA** du relevé d'agent.
 *
 * Un émulateur n'a ni Dashboard ni agent : le registre des sessions et la table de prix du serveur
 * n'y répondent pas, donc les quatre cas que le lot doit prouver — une conversation neuve, une
 * conversation chargée, un bot dont le fournisseur ne publie **aucun** prix, un appel dont la
 * donnée n'a pas été conservée — ne peuvent pas y être produits pour de vrai.
 *
 * La doublure les **pose** : un fil, un registre et une table de prix dont les valeurs sont celles
 * **relevées sur le serveur réel** (le prix `$0.30 / $1.20` publié pour `minimax/minimax-m2.7` par
 * `kilocode`, l'absence de ligne de prix pour le fournisseur local), jamais des chiffres inventés
 * pour la capture. Elle ne change **rien** quand elle n'est pas demandée, et le drapeau qui la
 * demande n'existe qu'en debug.
 */
data class LedgerStandIn(
    val conversations: List<Conversation>,
    val bots: List<BotProfile>,
    val openConversation: Conversation?,
    val initialScope: LedgerScope,
    val registry: List<SessionSummary>,
    val prices: Map<String, ServerPrice>,
    val priceSource: String,
    val priceReadAt: Long,
) {

    companion object {

        /** Les quatre cas posés. Le drapeau les nomme ; un nom inconnu ne pose rien. */
        const val FRESH = "fraiche"
        const val LOADED = "chargee"
        const val NO_PRICE = "sans-prix"
        const val MISSING = "manquante"

        /**
         * L'instant du relevé de prix, écrit dans la provenance : le **28 octobre 2026 à 17 h 31**,
         * l'heure du relevé fait sur le serveur du propriétaire (voir le tableau de prix du lot de
         * la feuille de modèle). Il est construit à partir de l'heure locale pour que la capture
         * dise cette heure-là, quel que soit le fuseau de l'émulateur.
         */
        private val READ_AT: Long = java.time.LocalDateTime.of(2026, 10, 28, 17, 31)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

        private const val SOURCE = "http://10.0.2.2:8890 · api/model/options"

        /** Le prix publié par le serveur pour `minimax/minimax-m2.7`, mot pour mot. */
        private val KILO_PRICE = ServerPrice(input = "\$0.30", output = "\$1.20", cache = "\$0.006")

        private const val TOOL = 1_000L

        fun of(demo: String): LedgerStandIn? = when (demo) {
            FRESH -> fresh()
            LOADED -> loaded()
            NO_PRICE -> noPrice()
            MISSING -> missing()
            else -> null
        }

        // ——— Les bots : le principal et deux autres, le lien étant celui de l'app (`chatId`) ———

        private fun bots(): List<BotProfile> = listOf(
            BotProfile(
                name = "hermes",
                title = null,
                displayName = "Onyx",
                isDefault = true,
                model = "minimax/minimax-m2.7",
                chatId = "sess-chargee",
            ),
            BotProfile(
                name = "scout",
                title = "Scout",
                description = "Veille sur les prix des concurrents.",
                model = "sage-a1.5",
                chatId = "sess-scout-1",
            ),
            BotProfile(
                name = "atlas",
                title = "Atlas",
                description = "Rangement du NAS.",
                model = "qwen/qwen3.8-27b",
                chatId = "sess-atlas-1",
            ),
        )

        private fun answer(
            id: String,
            at: Long,
            text: String,
            tools: List<ToolActivity> = emptyList(),
            subagents: List<SubagentActivity> = emptyList(),
            usage: TokenUsage? = null,
            seconds: Double? = null,
        ): ChatMessage = ChatMessage(
            id = id,
            role = Role.ASSISTANT,
            text = text,
            createdAt = at,
            tools = tools,
            subagents = subagents,
            usage = usage,
            metrics = seconds?.let { TurnMetrics(startedAt = at, completedAt = at + (it * 1000).toLong()) },
        )

        private fun user(id: String, at: Long, text: String): ChatMessage =
            ChatMessage(id = id, role = Role.USER, text = text, createdAt = at)

        private fun tool(id: String, name: String, status: ToolStatus, args: String? = null, output: String? = null) =
            ToolActivity(
                id = id,
                name = name,
                preview = args?.lineSequence()?.firstOrNull(),
                status = status,
                startedAt = TOOL,
                endedAt = if (status == ToolStatus.RUNNING) null else TOOL + 1_400L,
                args = args,
                output = output,
            )

        private fun subagent(id: String, goal: String, tracked: Boolean) = SubagentActivity(
            id = id,
            goal = goal,
            taskIndex = 0,
            taskCount = 1,
            status = if (tracked) SubagentStatus.COMPLETED else SubagentStatus.DISPATCHED,
            toolCount = 4,
            childSessionId = if (tracked) "child-$id" else null,
            durationSeconds = 42.0,
        )

        // ——— Cas 1 : la conversation neuve — deux messages, rien d'exécuté ———

        private fun fresh(): LedgerStandIn {
            val conversation = Conversation(
                id = "conv-fraiche",
                title = "Onyx",
                createdAt = 1_793_200_000_000L,
                updatedAt = 1_793_200_060_000L,
                serverSessionId = null,
                messages = listOf(
                    user("u1", 1_793_200_000_000L, "Bonjour"),
                    answer("a1", 1_793_200_030_000L, "Bonjour. Que puis-je faire ?"),
                ),
            )
            return LedgerStandIn(
                conversations = listOf(conversation),
                bots = bots(),
                openConversation = conversation,
                initialScope = LedgerScope.CONVERSATION,
                registry = emptyList(),
                prices = mapOf(PROBE_MODEL to KILO_PRICE),
                priceSource = SOURCE,
                priceReadAt = READ_AT,
            )
        }

        /** Le modèle servi par les deux fournisseurs du serveur, pour que la table soit non vide. */
        private const val PROBE_MODEL = "minimax/minimax-m2.7"

        // ——— Cas 2 : la conversation chargée — outils, sous-agents, jetons, prix du serveur ———

        private fun loaded(): LedgerStandIn {
            val tools = listOf(
                tool("t1", "search_files", ToolStatus.COMPLETED, "pattern: pricing", "14 files"),
                tool("t2", "read_file", ToolStatus.COMPLETED, "path: docs/pricing.md", "…"),
                tool("t3", "terminal", ToolStatus.COMPLETED, "command: df -h /mnt/nas", "/dev/sdb1 93%"),
                tool("t4", "terminal", ToolStatus.FAILED, "command: /opt/backup/backup.sh", "Permission denied"),
                tool("t5", "write_file", ToolStatus.COMPLETED, "path: notes/prix.md", "written"),
                tool("t6", "delegate_task", ToolStatus.COMPLETED, """{"tasks":[{"goal":"…"}]}""", "3 lancées"),
                tool("t7", "web_extract", ToolStatus.FAILED, "urls: [https://exemple.test/prix]", "timed out"),
                tool("t8", "read_file", ToolStatus.COMPLETED, "path: README.md", "…"),
                tool("t9", "terminal", ToolStatus.RUNNING, "command: rsync -a /mnt/nas /mnt/backup"),
            )
            val subagents = listOf(
                subagent("sa-1", "Relever les prix des six concurrents", tracked = true),
                subagent("sa-2", "Comparer les deux grilles tarifaires", tracked = true),
                subagent("sa-3", "Vérifier les conditions de licence", tracked = false),
            )
            val messages = listOf(
                user("u1", 1_793_000_000_000L, "Compare nos prix avec ceux des concurrents et range le résultat."),
                answer(
                    id = "a1",
                    at = 1_793_000_010_000L,
                    text = "Je commence par chercher les grilles.",
                    tools = tools.take(3),
                    seconds = 40.0,
                ),
                answer(
                    id = "a2",
                    at = 1_793_000_060_000L,
                    text = "Trois tâches sont lancées en parallèle.",
                    tools = tools.drop(3).take(4),
                    subagents = subagents,
                    seconds = 52.4,
                ),
                answer(
                    id = "a3",
                    at = 1_793_000_130_000L,
                    text = "La sauvegarde tourne encore ; le reste est écrit dans `notes/prix.md`.",
                    tools = tools.drop(7),
                    usage = TokenUsage(
                        input = 1_048_576,
                        output = 87_808,
                        cached = 18_400,
                        contextUsed = 96_000,
                        contextMax = 200_000,
                    ),
                    seconds = 40.0,
                ),
            )
            val conversation = Conversation(
                id = "conv-chargee",
                title = "Onyx",
                createdAt = 1_793_000_000_000L,
                updatedAt = 1_793_000_170_000L,
                serverSessionId = "sess-chargee",
                messages = messages,
            )
            return LedgerStandIn(
                conversations = listOf(conversation),
                bots = bots(),
                openConversation = conversation,
                initialScope = LedgerScope.CONVERSATION,
                registry = listOf(
                    SessionSummary(
                        id = "sess-chargee",
                        title = "Compare nos prix",
                        model = PROBE_MODEL,
                        source = "api_server",
                        messageCount = 4,
                        toolCallCount = 9,
                        apiCallCount = 7,
                        inputTokens = 1_048_576,
                        outputTokens = 87_808,
                        reasoningTokens = 4_120,
                        cacheReadTokens = 18_400,
                        estimatedCostUsd = 0.0,
                        actualCostUsd = 0.0,
                        startedAt = 1_793_000_000.0,
                        lastActive = 1_793_000_170.0,
                    ),
                ),
                prices = mapOf(PROBE_MODEL to KILO_PRICE),
                priceSource = SOURCE,
                priceReadAt = READ_AT,
            )
        }

        // ——— Cas 3 : un bot dont le fournisseur ne publie aucun prix ———

        private fun noPrice(): LedgerStandIn {
            fun conversation(id: String, session: String, title: String, input: Int, output: Int): Conversation {
                val messages = listOf(
                    user("u-$id", 1_793_100_000_000L, "Fais le tour des prix."),
                    answer(
                        id = "a-$id",
                        at = 1_793_100_010_000L,
                        text = "C'est relevé.",
                        tools = listOf(
                            tool("t-$id-1", "web_extract", ToolStatus.COMPLETED, "urls: [https://exemple.test]", "…"),
                            tool("t-$id-2", "write_file", ToolStatus.COMPLETED, "path: veille.md", "written"),
                        ),
                        usage = TokenUsage(input = input, output = output),
                        seconds = 18.0,
                    ),
                )
                return Conversation(
                    id = id,
                    title = title,
                    createdAt = 1_793_100_000_000L,
                    updatedAt = 1_793_100_060_000L,
                    serverSessionId = session,
                    messages = messages,
                )
            }
            val first = conversation("conv-scout-1", "sess-scout-1", "Scout", 8_400, 900)
            // La seconde n'a **pas** de session serveur : son rattachement tient au titre posé.
            val second = conversation("conv-scout-2", "", "Scout", 3_100, 420)
            return LedgerStandIn(
                conversations = listOf(first, second),
                bots = bots(),
                openConversation = first,
                initialScope = LedgerScope.BOT,
                registry = listOf(
                    SessionSummary(
                        id = "sess-scout-1",
                        title = "Veille",
                        model = "sage-a1.5",
                        source = "api_server",
                        toolCallCount = 2,
                        inputTokens = 8_400,
                        outputTokens = 900,
                        estimatedCostUsd = 0.0,
                        actualCostUsd = 0.0,
                    ),
                ),
                // Le fournisseur **local** ne publie aucune ligne de prix : la table est vide, donc
                // aucun montant ne peut être estimé pour `sage-a1.5`.
                prices = mapOf(PROBE_MODEL to KILO_PRICE),
                priceSource = SOURCE,
                priceReadAt = READ_AT,
            )
        }

        // ——— Cas 4 : ce que le transport n'a pas conservé ———

        private fun missing(): LedgerStandIn {
            val messages = listOf(
                user("u1", 1_793_150_000_000L, "Sauvegarde le NAS."),
                // Le transport `chat.completions` annonce l'appel **sans** arguments ni résultat, et
                // ne remonte ni jetons ni durée : les trois trous du relevé, ensemble.
                answer(
                    id = "a1",
                    at = 1_793_150_010_000L,
                    text = "C'est lancé.",
                    tools = listOf(
                        tool("t1", "terminal", ToolStatus.COMPLETED),
                        tool("t2", "write_file", ToolStatus.FAILED),
                    ),
                ),
                answer(
                    id = "a2",
                    at = 1_793_150_090_000L,
                    text = "Voilà.",
                    tools = listOf(tool("t3", "terminal", ToolStatus.COMPLETED)),
                ),
            )
            val conversation = Conversation(
                id = "conv-atlas-1",
                title = "Atlas",
                createdAt = 1_793_150_000_000L,
                updatedAt = 1_793_150_100_000L,
                serverSessionId = "sess-atlas-1",
                messages = messages,
            )
            return LedgerStandIn(
                conversations = listOf(conversation),
                bots = bots(),
                openConversation = conversation,
                initialScope = LedgerScope.CONVERSATION,
                // Le registre a répondu, mais **sans** cette session : le relevé dit que le fil est
                // la seule source, au lieu de peindre le vide comme un zéro.
                registry = emptyList(),
                prices = mapOf(PROBE_MODEL to KILO_PRICE),
                priceSource = SOURCE,
                priceReadAt = READ_AT,
            )
        }
    }
}
