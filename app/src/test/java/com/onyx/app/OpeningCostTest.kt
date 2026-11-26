package com.onyx.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Ce qu'ouvrir un fil et changer de bot ne font plus.**
 *
 * Deux passes coûteuses vivaient sur le chemin d'une ouverture de conversation — donc d'un
 * changement de bot, qui en ouvre une :
 *
 *  1. `cardsFor` recopiait **tous** les messages de la liste pour retirer le drapeau « en train
 *     d'écrire », même quand aucune bulle ne l'avait. Un `map` rend toujours une liste neuve : sur
 *     un fil de cinq cents messages, la copie était payée à chaque première ouverture, et la liste
 *     neuve n'était plus reconnue « inchangée » par le dépôt ;
 *  2. `sidebarRows` recevait `includeMessages = true` en dur, donc lisait le texte de **tous** les
 *     messages de **toutes** les conversations pour un filtre qui, sans requête, s'arrête à la
 *     première ligne (`sidebarMatches` rend `true` avant même de regarder un message).
 *
 * Les deux règles sont des **formes de code**, pas des durées : c'est pourquoi elles sont tenues
 * ici, à côté de la source qu'elles décrivent.
 */
class OpeningCostTest {

    private fun read(path: String) = File(path).readText()

    private val viewModel = read("src/main/java/com/onyx/app/ui/chat/ChatViewModel.kt")
    private val host = read("src/main/java/com/onyx/app/ui/sidebar/ChatSidebarHost.kt")

    @Test
    fun `la copie des messages d un fil ne se fait que si une bulle ecrit encore`() {
        assertTrue(
            "la seconde passe est conditionnée à la présence d'une bulle en cours",
            viewModel.contains("if (resolved.none { it.isStreaming }) resolved"),
        )
        assertFalse(
            "la copie inconditionnelle de toute la liste ne doit pas revenir",
            viewModel.contains(
                "val resolved = ReplyCards.resolve(source)\n" +
                    "            .map { message -> if (message.isStreaming) message.copy(isStreaming = false) else message }",
            ),
        )
        assertTrue(
            "la résolution reste gardée par la liste source : rouvrir un fil intact ne relit rien",
            viewModel.contains("if (cachedSource === source) return resolved"),
        )
    }

    @Test
    fun `le volet ne scanne les messages que sous une recherche posee`() {
        assertTrue(
            "la portée de la recherche suit la requête",
            host.contains("includeMessages = activeQuery.isNotBlank()"),
        )
        assertFalse(
            "la portée en dur relisait tous les messages à chaque ouverture du volet",
            host.contains("includeMessages = true,"),
        )
    }
}