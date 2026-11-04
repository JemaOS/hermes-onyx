package com.onyx.app.ui.chat

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.ToolActivity
import com.onyx.app.data.model.ToolStatus

/**
 * **Un fil long, pour mesurer le défilement** (`am start --ez onyx.threadDemo 40`).
 *
 * Le propriétaire signale un défilement qui n'est pas fluide « quand il y a beaucoup de choses à
 * l'écran ». Une impression ne se corrige pas : il faut un fil chargé **reproductible**, et des
 * temps d'image lus sur l'appareil (`dumpsys gfxinfo`). Cette doublure fabrique ce fil — des
 * réponses avec de la prose en Markdown, des listes, du code, et **beaucoup d'étapes d'outils**,
 * qui sont ce que le fil dessine de plus lourd.
 *
 * Rien n'est inventé sur le serveur : ces messages ne sont **jamais** envoyés. Le drapeau ouvre le
 * fil, on le défile, on lit les images. Sans le drapeau, rien de tout ceci n'est appelé.
 */
object ThreadStandIn {

    /** Le nombre de messages du fil de mesure (par défaut : de quoi remplir plusieurs écrans). */
    const val DefaultCount: Int = 40

    private val PROSE = listOf(
        "Voilà ce que j'ai trouvé. Le dépôt est propre, les tests passent, et rien n'a été " +
            "modifié en dehors du fichier visé.",
        "Il y a **trois** points à regarder :\n\n" +
            "1. la fonction de rendu est appelée deux fois ;\n" +
            "2. la liste n'a pas de clé stable ;\n" +
            "3. le calcul de la date se refait à chaque image.\n\n" +
            "Le troisième est celui qui se sent au doigt.",
        "Le résultat est stable. J'ai relancé la suite complète deux fois pour écarter une " +
            "instabilité de minutage, et les deux passages sont identiques.",
        "```kotlin\nfun scroll() {\n    val key = remember(id) { build() }\n    LazyColumn { items(list, key = { it.id }) { row -> Row(it) } }\n}\n```\n" +
            "C'est la forme qui tient : une clé stable, un calcul mémorisé.",
        "> Une mesure vaut mieux qu'une intuition — surtout sur du rendu.\n\n" +
            "C'est pour ça que j'ai posé la doublure : elle donne le même fil à chaque essai.",
    )

    /** Le fil de mesure : [count] messages, chaque réponse portant ses étapes d'outils. */
    fun messages(
        count: Int = DefaultCount,
        withTools: Boolean = true,
        now: Long = System.currentTimeMillis(),
    ): List<ChatMessage> =
        (0 until count).map { index ->
            val user = index % 2 == 0
            val time = now - (count - index) * 60_000L
            if (user) {
                ChatMessage(
                    id = "demo-u-$index",
                    role = Role.USER,
                    text = "Peux-tu regarder le point $index ? Il me semble que ça traîne.",
                    createdAt = time,
                )
            } else {
                ChatMessage(
                    id = "demo-a-$index",
                    role = Role.ASSISTANT,
                    text = PROSE[(index / 2) % PROSE.size],
                    createdAt = time,
                    tools = if (withTools) toolsFor(index, time) else emptyList(),
                )
            }
        }

    /** Les étapes d'une réponse : c'est leur nombre qui pèse à l'écran. */
    private fun toolsFor(index: Int, time: Long): List<ToolActivity> =
        (0 until 6).map { step ->
            ToolActivity(
                id = "demo-t-$index-$step",
                name = toolName(step),
                preview = toolLabel(step),
                status = ToolStatus.COMPLETED,
                startedAt = time + step * 1_000L,
                endedAt = time + step * 1_000L + 400L,
            )
        }

    private fun toolName(step: Int): String = when (step % 4) {
        0 -> "terminal"
        1 -> "read_file"
        2 -> "search_files"
        else -> "patch"
    }

    private fun toolLabel(step: Int): String = when (step % 4) {
        0 -> "git status --short"
        1 -> "app/src/main/java/com/onyx/app/ui/chat/ChatScreen.kt"
        2 -> "LocalGlassScrollFreeze"
        else -> "Changement appliqué"
    }
}
