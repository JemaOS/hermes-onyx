package com.onyx.app.data.local

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.ToolActivity
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * **Ce que coûte une sauvegarde, quand le fichier grossit.**
 *
 * Le magasin local tient **toutes** les conversations dans un seul JSON. Chaque `save()`
 * resérialise donc la liste entière puis la réécrit en entier : le coût d'une sauvegarde ne dépend
 * pas de ce qui a changé, mais de la taille de tout ce que l'app a jamais écrit.
 *
 * C'est exactement la plainte du propriétaire (« ça rame quand j'ai plusieurs conversations, ou une
 * conversation qui a lancé 60 outils ») : les deux termes multiplient le même coût.
 *
 * Ce test **mesure** au lieu d'affirmer : il imprime le temps et la taille, et ne garde qu'un
 * plafond très large, pour attraper une régression d'ordre de grandeur sans être instable en CI.
 */
class ConversationWriteCostTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun tool(index: Int, at: Long) = ToolActivity(
        id = "t$index",
        name = "read_file",
        preview = "read_file · ${index % 900} ms",
        status = com.onyx.app.data.model.ToolStatus.COMPLETED,
        startedAt = at,
        endedAt = at + 400,
        args = """{"path":"/home/john/Bureau/onyx/app/src/main/java/com/onyx/app/ui/chat/ChatScreen.kt"}""",
        output = """{"ok": true}""" + " Le serveur a répondu correctement, mais la latence reste élevée. ".repeat(6),
    )

    private fun conversation(index: Int, toolsPerTurn: Int, turns: Int): Conversation {
        val at = 1_700_000_000_000L
        val messages = ArrayList<ChatMessage>(turns * 2)
        for (turn in 0 until turns) {
            messages += ChatMessage(
                id = "c$index-u$turn",
                role = Role.USER,
                text = "Question ${turn + 1} de la conversation $index.",
                createdAt = at + turn * 120_000,
            )
            messages += ChatMessage(
                id = "c$index-a$turn",
                role = Role.ASSISTANT,
                text = buildString {
                    repeat(6) { step ->
                        append("## Étape $step\n\n")
                        append("Le serveur a répondu correctement.\n\n")
                        repeat(9) { append("- Point ${it + 1} : le cache garde des entrées.\n") }
                        append('\n')
                    }
                },
                createdAt = at + turn * 120_000 + 1_000,
                tools = (0 until toolsPerTurn).map { tool(index * 1_000 + turn * 100 + it, at + it * 400L) },
            )
        }
        return Conversation(
            id = "seed-$index",
            title = "Conversation $index — $toolsPerTurn outils",
            createdAt = at,
            updatedAt = at + index * 3_600_000L,
            messages = messages,
        )
    }

    private fun sizeOf(conversations: List<Conversation>): Pair<Int, Long> {
        val serializer = ListSerializer(Conversation.serializer())
        // Une passe de chauffe : la JVM compile les sérialiseurs à la première utilisation.
        json.encodeToString(serializer, conversations)
        val started = System.nanoTime()
        val text = json.encodeToString(serializer, conversations)
        val elapsed = (System.nanoTime() - started) / 1_000_000
        return elapsed.toInt() to text.length.toLong()
    }

    @Test
    fun `une sauvegarde reserialise tout le fichier - la mesure, et son plafond`() {
        val one = listOf(conversation(0, toolsPerTurn = 60, turns = 6))
        val many = (0 until 25).map { conversation(it, toolsPerTurn = 60, turns = 6) }

        val (oneMs, oneChars) = sizeOf(one)
        val (manyMs, manyChars) = sizeOf(many)

        println(
            "écriture : 1 conversation de 60 outils = $oneMs ms / ${oneChars / 1024} Kio ; " +
                "25 conversations = $manyMs ms / ${manyChars / 1024} Kio",
        )

        // Le plafond large (2 s) n'est pas une cible : c'est un garde-fou contre un retour à une
        // sérialisation quadratique. La valeur qui compte est celle imprimée ci-dessus.
        assert(manyMs in 0..2_000) { "resérialiser 25 conversations prend $manyMs ms" }
        assert(oneMs in 0..2_000) { "resérialiser 1 conversation prend $oneMs ms" }
    }
}