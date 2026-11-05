package com.onyx.app.perf

import com.onyx.app.data.model.ApprovalRequest
import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.ToolActivity
import com.onyx.app.data.model.ToolStatus
import com.onyx.app.ui.ShellState
import com.onyx.app.ui.chat.ChatScreenState
import com.onyx.app.ui.chat.ChatUiState
import com.onyx.app.ui.rich.splitMarkdown
import com.onyx.app.ui.chat.components.MessageDerived
import com.onyx.app.ui.chat.components.hasMarkup
import org.junit.Test

/**
 * **Ce que coûte le travail d'une rangée — mesuré, pas supposé.**
 *
 * Une `LazyColumn` jette ses rangées hors écran : celle qui revient est recomposée de zéro, et tous
 * ses `remember` repartent à vide. Ce que ce test mesure, c'est exactement ce qui se paie à chaque
 * **entrée de rangée** dans la fenêtre — donc en faisant défiler un fil chargé :
 *
 *  - le découpage Markdown du texte (`splitMarkdown`) ;
 *  - la détection de balisage (`hasMarkup`, ligne par ligne puis une expression régulière) ;
 *  - ce que la rangée déduit de son contenu (`MessageDerived.of` : diffs, tâches, fichiers livrés) ;
 *  - et, pour comparaison, la projection d'état de l'écran et celle de la coquille.
 *
 * **Ce que ces chiffres valent.** Ils sont pris sur la JVM de la machine de développement, pas sur le
 * téléphone : un Snapdragon est plus lent sur du code Kotlin que ce JVM de bureau, et ART n'a pas le
 * même JIT. Ils disent donc l'**ordre de grandeur** et le **rapport** entre les postes (« ce poste
 * coûte cent fois celui-là »), jamais la durée d'une trame à l'appareil. Le protocole de mesure sur
 * l'appareil est dans `docs/PERF-MESURE.md` (ou `AUDIT-PERFORMANCE.md` §6).
 *
 * Il n'y a **aucune assertion de temps** : un seuil écrit sur une JVM de CI échouerait au premier
 * build à froid, pour une raison qui n'a rien à voir avec l'app. La sortie se lit dans le rapport de
 * test (`build/test-results/...`), ou avec `--info`.
 */
class RowWorkCostTest {

    // ---- Le contenu d'une rangée « dense », celle d'un agent qui a travaillé ------------------

    /** Une réponse d'agent réaliste : titres, listes, gras, code en ligne, ~3 400 caractères. */
    private val prose = buildString {
        appendLine("Voici ce que j'ai trouvé dans le dépôt, point par point.")
        appendLine()
        appendLine("## Les trois postes qui coûtent")
        appendLine()
        repeat(12) { index ->
            appendLine(
                "$index. **Le poste $index** — la rangée relit `MessageDerived.of` à chaque " +
                    "composition, et la liste jette ses rangées : le retour à l'écran refait tout. " +
                    "C'est mesuré, `fichier.kt:${100 + index}`.",
            )
        }
        appendLine()
        appendLine("Une phrase avec du `code en ligne`, un [lien](https://exemple.fr/page) et de " +
            "l'*italique* qui n'ouvre pas de bloc.")
        appendLine()
        appendLine("```kotlin")
        repeat(10) { appendLine("val cache$it = Lru<String, Spanned>(16)") }
        appendLine("```")
        appendLine()
        appendLine("> Une citation, qui passe par le rendu récursif.")
        appendLine()
        repeat(6) {
            appendLine("Un paragraphe de conclusion, avec du texte ordinaire qui ne porte aucun " +
                "balisage du tout, pour que la mesure ne soit pas seulement du Markdown.")
        }
    }

    /** Une sortie d'outil de ~12 000 caractères, comme un `cat` de fichier. */
    private val toolOutput = buildString {
        repeat(200) { appendLine("    ${"ligne de sortie $it".padEnd(56, '.')} ok") }
    }

    private fun message(text: String, tools: List<ToolActivity> = emptyList()) = ChatMessage(
        id = "m1",
        role = Role.ASSISTANT,
        createdAt = 0L,
        text = text,
        tools = tools,
    )

    private fun tool(index: Int, output: String) = ToolActivity(
        id = "t$index",
        name = "terminal",
        output = output,
        status = ToolStatus.COMPLETED,
        startedAt = 1_000L,
        endedAt = 4_200L,
    )

    /** Une réponse de huit outils, comme celle du relevé du propriétaire. */
    private val dense = message(prose, (0 until 8).map { tool(it, toolOutput) })

    // ---- La mesure ---------------------------------------------------------------------------

    private inline fun costMicros(label: String, warmup: Int = 20, runs: Int = 50, block: () -> Unit) {
        repeat(warmup) { block() }
        val start = System.nanoTime()
        repeat(runs) { block() }
        val micros = (System.nanoTime() - start) / 1000.0 / runs
        println(String.format("%-46s %10.1f µs", label, micros))
    }

    @Test
    fun `le cout du travail d une rangee, mesure`() {
        println()
        println("== Travail d'une rangée (JVM de développement — ordre de grandeur, pas une trame) ==")
        println("prose=${prose.length} car., sorties=${dense.tools.size} × ${toolOutput.length} car.")
        println()

        costMicros("splitMarkdown (découpage du texte)") { splitMarkdown(prose) }
        costMicros("hasMarkup (balisage ? puis regex)") { hasMarkup(prose) }
        costMicros("MessageDerived.of (8 outils × 12 k)") { MessageDerived.of(dense) }
        // Le même, mais pendant un flux : c'est le chemin que prend la ligne vivante.
        costMicros("MessageDerived.of (includeText = false)") {
            MessageDerived.of(dense, includeText = false)
        }
        // Et ce que coûte vraiment une **entrée de rangée** une fois le travail gardé : l'empreinte
        // du contenu, plus une lecture de LRU. C'est le chemin que prend une rangée qui revient à
        // l'écran — celui du défilement.
        com.onyx.app.ui.chat.components.RowCaches.derived(dense, includeText = true)
        costMicros("RowCaches.derived — entrée déjà gardée (8 outils)") {
            com.onyx.app.ui.chat.components.RowCaches.derived(dense, includeText = true)
        }

        println()
        println("== Les projections d'état (une fois par forme du fil) ==")
        val thread = buildList {
            repeat(150) { index ->
                add(
                    ChatMessage(
                        id = "q$index",
                        role = if (index % 2 == 0) Role.USER else Role.ASSISTANT,
                        createdAt = index * 1_000L,
                        text = "Message $index, avec un peu de texte ordinaire.",
                        tools = if (index % 2 == 1) (0 until 4).map { tool(it, toolOutput) } else emptyList(),
                        approvals = if (index % 37 == 0) {
                            listOf(ApprovalRequest(id = "a$index", command = "ls", description = "Lister"))
                        } else {
                            emptyList()
                        },
                    ),
                )
            }
        }
        val server = ServerConfig(id = "srv", name = "Onyx", baseUrl = "http://host:8080", model = "m")
        val state = ChatUiState(server = server, conversationId = "c1", messages = thread, isStreaming = true)
        costMicros("ChatScreenState.of (300 messages)") { ChatScreenState.of(state) }
        costMicros("ShellState.of (300 messages)") { ShellState.of(state) }
        println()
    }
}
