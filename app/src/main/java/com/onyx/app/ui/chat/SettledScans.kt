package com.onyx.app.ui.chat

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Role
import com.onyx.app.ui.voice.VoiceTurn

/**
 * **Ce qui ne dépend que des messages FINIS, calculé une fois par forme du fil.**
 *
 * Le défaut que ce cache supprime : `ShellState.of` (la projection lue par la coquille, donc par
 * la racine de l'app) recalculait à **chaque jeton** trois choses qui ne dépendent pourtant pas du
 * texte qui s'écrit —
 *
 *  - une copie du fil entier sans le message en cours (`filterNot { isStreaming }`, une liste de N
 *    éléments allouée trente fois par seconde) ;
 *  - `voiceTurnsOf`, qui fabrique **un `VoiceTurn` par tour** de la conversation ;
 *  - la première question, par un balayage complet.
 *
 * Avec vingt pages d'historique, c'est un travail qui **grandit avec ce que l'app contient** — et
 * c'est exactement la plainte : « plus j'ai d'infos, plus c'est lent ».
 *
 * La clé est la **frontière** des messages finis (leur nombre, leurs deux extrémités) plus une
 * empreinte de longueur qui attrape une édition de texte sans allocation. Pendant qu'une réponse
 * s'écrit, cette frontière ne bouge pas : le calcul est donc fait **une fois** par tour, et non
 * par jeton.
 */
internal object SettledScans {

    private data class Key(val count: Int, val firstId: String?, val lastId: String?, val textLength: Long)

    /** Le résultat d'un calcul, gardé avec la clé qui l'a produit. */
    class Result(
        val firstUserQuestion: String?,
        val voiceTurns: List<VoiceTurn>,
        /** Le nombre de messages **utilisateur** du fil fini — la même passe, pas un second balayage. */
        val userTurns: Int,
    )

    private var key: Key? = null
    private var result: Result = Result(null, emptyList(), 0)

    /**
     * Le nombre de calculs réellement effectués — la mesure que les tests lisent.
     *
     * Exposé pour prouver la propriété (« trente jetons, un calcul ») sans horloge : un temps en
     * millisecondes dépend de la machine, un compteur non.
     */
    internal var computations: Int = 0
        private set

    /** Remet le cache à zéro (tests, ou changement de conversation). */
    internal fun clear() {
        key = null
        result = Result(null, emptyList(), 0)
        computations = 0
    }

    fun of(messages: List<ChatMessage>, settledCount: Int): Result {
        val settled = settledCount.coerceIn(0, messages.size)
        var textLength = 0L
        for (index in 0 until settled) textLength += messages[index].text.length
        val next = Key(
            count = settled,
            firstId = messages.firstOrNull()?.id,
            lastId = messages.getOrNull(settled - 1)?.id,
            textLength = textLength,
        )
        val cached = key
        if (cached != null && cached == next) return result
        computations++
        var firstUser: String? = null
        var users = 0
        for (index in 0 until settled) {
            val message = messages[index]
            if (message.role != Role.USER) continue
            users++
            if (firstUser == null) firstUser = message.text
        }
        val turns = voiceTurnsOf(messages, settled)
        result = Result(firstUser, turns, users)
        key = next
        return result
    }

    /**
     * Chaque question du fil, suivie de la première réponse **finie** qui vient après elle.
     *
     * **Un seul passage, à rebours**, borné aux messages finis : la version d'avant refaisait un
     * `subList` + `firstOrNull` par question (balayage quadratique), et lisait la liste entière —
     * y compris la réponse en cours d'écriture, qui n'a pas encore sa place dans un historique.
     */
    private fun voiceTurnsOf(messages: List<ChatMessage>, settled: Int): List<VoiceTurn> {
        if (settled <= 0) return emptyList()
        val turns = ArrayList<VoiceTurn>()
        var next: ChatMessage? = null
        for (index in settled - 1 downTo 0) {
            val message = messages[index]
            if (message.role == Role.ASSISTANT) {
                next = message
                continue
            }
            if (message.role != Role.USER) continue
            val answer = next?.text?.takeIf { it.isNotBlank() } ?: continue
            turns.add(VoiceTurn(question = message.text, answer = answer))
        }
        // Le parcours est à rebours : la liste se remet dans l'ordre du fil.
        turns.reverse()
        return turns
    }
}