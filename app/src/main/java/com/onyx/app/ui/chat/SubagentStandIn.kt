package com.onyx.app.ui.chat

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.SubagentActivity
import com.onyx.app.data.model.SubagentStatus
import com.onyx.app.data.model.ToolActivity
import com.onyx.app.data.model.ToolStatus

/**
 * La doublure de QA des sous-agents (`am start --ez onyx.subagentDemo true`).
 *
 * Un émulateur n'a ni Dashboard ni agent : aucun enfant ne peut donc naître pour de vrai. Le
 * drapeau pose un fil et un roster **injectés** — exactement le même genre de doublure que
 * `onyx.demoBots` pour le roster des bots, ou `onyx.voiceDemo` pour l'écran vocal.
 *
 * Les **états** couverts sont ceux que l'app sait dessiner :
 *  - une tâche qui travaille, avec un outil en cours et un compteur d'outils ;
 *  - une tâche qui démarre (aucun outil encore) ;
 *  - une tâche **lancée à l'aveugle** ([SubagentStatus.DISPATCHED]) — celle que l'API de
 *    conversation produit, sans session d'enfant : rien à ouvrir, rien à arrêter ;
 *  - une tâche finie, et une en échec.
 *
 * Le drapeau ne change **rien** quand il est absent : [conversation] et [agents] ne sont appelés
 * que sur demande explicite.
 */
internal object SubagentStandIn {

    /** Le roster injecté : quatre tâches, dont deux vivantes. */
    fun agents(now: Long): List<SubagentActivity> = listOf(
        SubagentActivity(
            id = "sa-pricing",
            goal = "Check the three hosting plans and their limits",
            taskIndex = 0,
            taskCount = 3,
            status = SubagentStatus.RUNNING,
            toolCount = 7,
            lastTool = "read_file",
            childSessionId = "child-pricing",
            model = "gpt-4.1",
            startedAt = now - 42_000,
        ),
        SubagentActivity(
            id = "sa-calendar",
            goal = "Look at the calendar for a free hour on Thursday",
            taskIndex = 1,
            taskCount = 3,
            status = SubagentStatus.RUNNING,
            toolCount = 0,
            childSessionId = "child-calendar",
            startedAt = now - 6_000,
        ),
        SubagentActivity(
            id = "sa-vet",
            goal = "Check the vet's opening hours for tomorrow",
            taskIndex = 2,
            taskCount = 3,
            status = SubagentStatus.COMPLETED,
            toolCount = 4,
            lastTool = "web_search",
            summary = "Opens at 8:30, closes at 18:00; Saturdays until noon.",
            durationSeconds = 23.0,
            childSessionId = "child-vet",
            model = "gpt-4.1",
            startedAt = now - 95_000,
        ),
        // Une ligne « lancée » sans session d'enfant : celle que seule l'API de conversation
        // permet de voir, et sur laquelle ni transcription ni arrêt ne sont proposés.
        SubagentActivity(
            id = "delegate:run:0",
            goal = "Do a sweep of the six competitors' pricing pages",
            status = SubagentStatus.DISPATCHED,
            toolCount = 0,
            startedAt = now - 12_000,
        ),
    )

    /** Le fil injecté : la question, puis la réponse qui a délégué. */
    fun conversation(now: Long): List<ChatMessage> = listOf(
        ChatMessage(
            id = "demo-user",
            role = Role.USER,
            text = "Compare the hosting plans, find me an hour on Thursday and check the vet's hours.",
            createdAt = now - 100_000,
        ),
        ChatMessage(
            id = "demo-reply",
            role = Role.ASSISTANT,
            text = "I'll run those three in parallel and come back with one answer.",
            createdAt = now - 98_000,
            tools = listOf(
                ToolActivity(
                    id = "demo-tool",
                    name = "delegate_task",
                    preview = "Delegating 3 tasks",
                    status = ToolStatus.RUNNING,
                    startedAt = now - 96_000,
                ),
            ),
            subagents = agents(now),
        ),
    )

    /**
     * La transcription injectée d'un enfant : ce que `subagent.tail` rendrait pour lui — les
     * derniers tours de sa propre conversation, tels qu'ils sortent du fichier vivant du gateway.
     */
    fun transcript(agent: SubagentActivity): String = when (agent.id) {
        "sa-pricing" -> """
            [user] Check the three hosting plans and their limits.

            [thinking] Three plans on the page: Starter, Pro, Team. I need the row that carries
            the storage and the seat count for each, and the overage price.

            [tool] web_extract {"urls": ["https://example.com/pricing"]}
            [result] Starter 10 GB / 1 seat / $0.10 per GB over; Pro 100 GB / 5 seats; Team
            1 TB / 25 seats, SSO on Team only.

            [assistant] Starter 10 GB, 1 seat. Pro 100 GB, 5 seats, $9/month. Team 1 TB,
            25 seats, SSO.
        """.trimIndent()

        "sa-calendar" -> """
            [user] Look at the calendar for a free hour on Thursday.

            [tool] web_search {"query": "thursday availability"}
        """.trimIndent()

        "sa-vet" -> """
            [user] Check the vet's opening hours for tomorrow.

            [tool] web_search {"query": "vet opening hours"}
            [result] Mon–Fri 08:30–18:00; Sat 09:00–12:00; closed Sunday.

            [assistant] Opens at 8:30, closes at 18:00; Saturdays until noon.
        """.trimIndent()

        else -> ""
    }
}
