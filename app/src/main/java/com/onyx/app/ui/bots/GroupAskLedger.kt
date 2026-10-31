package com.onyx.app.ui.bots

import com.onyx.app.voice.normalizeForEcho
import com.onyx.app.voice.transcriptSimilarity
import java.util.UUID

/**
 * Ce qu'il faut faire d'une question reconnue par la voix.
 *
 * Une question n'a **qu'un** identifiant : c'est lui qui part dans la salle (`event_id` de
 * `groups.send`), qui la nomme dans le journal, et qui empêche qu'elle y entre deux fois. Une
 * transcription qui grandit — « vous êtes là » puis « vous êtes là ? » puis « vous êtes là, les
 * gars ? » — est **la même question** : elle garde son identifiant et ne repart pas.
 */
internal sealed class GroupAskDecision {
    /** La question part, une fois, sous cet identifiant neuf. */
    data class Send(val id: String, val text: String) : GroupAskDecision()

    /** C'est la question qui vient d'être posée (ou sa transcription qui a grandi) : rien ne part. */
    data class Again(val id: String, val why: String) : GroupAskDecision()

    /** Rien à envoyer (silence, bruit). */
    data object Blank : GroupAskDecision()
}

/**
 * Le guichet des questions d'une salle : une seule entrée par question, quel que soit le nombre de
 * fois où le moteur la rend.
 *
 * Le défaut rapporté par les bots eux-mêmes : « Tes messages “vous êtes là” reviennent en boucle ».
 * Le moteur de reconnaissance rend une **finale** à chaque fois qu'il croit la phrase finie, et
 * cette finale **grandit** quand il reprend le fil (« vous êtes là » → « vous êtes là ? »). Chaque
 * variante passait la garde d'égalité de la session (`lastAsked`) parce que le texte différait
 * d'un caractère : la salle recevait donc trois ou quatre fois la même question.
 *
 * Trois règles, dans cet ordre, et une seule sortie possible — [GroupAskDecision.Send] :
 *  1. **la même phrase** (à la casse, aux accents et à la ponctuation près) revient dans
 *     [repeatWindowMs] : c'est un rejeu du moteur ;
 *  2. **la transcription a grandi** (l'une contient l'autre) dans [growthWindowMs] : c'est la même
 *     question qui s'écrit, pas une nouvelle ;
 *  3. **la phrase est quasi identique** ([similarity] de mots communs) dans [repeatWindowMs] : le
 *     moteur a reconnu la même phrase autrement.
 *
 * Le temps est passé en paramètre : la décision est pure, donc éprouvable sans horloge.
 */
internal class GroupAskLedger(
    private val repeatWindowMs: Long = ASK_REPEAT_WINDOW_MS,
    private val growthWindowMs: Long = ASK_GROWTH_WINDOW_MS,
    private val similarity: Float = ASK_SIMILARITY,
    /** L'identifiant neuf d'une question admise — remplaçable pour une preuve déterministe. */
    private val newId: () -> String = { "ask-" + UUID.randomUUID().toString() },
) {
    private var lastText: String = ""
    private var lastId: String = ""
    private var lastAt: Long = Long.MIN_VALUE

    /** Les phrases déjà posées, avec leur identifiant : une phrase ne revient jamais sous un autre. */
    private val asked = mutableListOf<Asked>()

    private data class Asked(val text: String, val id: String, val at: Long)

    fun admit(question: String, now: Long = System.currentTimeMillis()): GroupAskDecision {
        val text = question.trim()
        val normalised = normalizeForEcho(text)
        if (normalised.isEmpty()) return GroupAskDecision.Blank

        // 1. La même phrase, telle quelle.
        if (normalised == lastText && now - lastAt <= repeatWindowMs) {
            return GroupAskDecision.Again(lastId, "la même phrase revient")
        }

        // 2. La transcription qui grandit (ou qui se rogne) : c'est la même question.
        if (lastText.isNotEmpty() && now - lastAt <= growthWindowMs && grows(lastText, normalised)) {
            lastText = normalised
            lastAt = now
            return GroupAskDecision.Again(lastId, "la transcription a grandi")
        }

        // 3. La même phrase reconnue avec un mot de moins (ou de plus) : le moteur a avalé ou
        //    rajouté un mot, mais c'est la même question. Une **substitution** (« demain » →
        //    « mardi », « 1 » → « 2 ») n'en est pas une : c'est une autre question.
        val near = asked.lastOrNull { it.at >= now - repeatWindowMs && sameWords(it.text, normalised) }
        if (near != null) {
            lastText = normalised
            lastId = near.id
            lastAt = now
            return GroupAskDecision.Again(near.id, "une phrase quasi identique vient d'être posée")
        }

        val id = newId()
        lastText = normalised
        lastId = id
        lastAt = now
        asked += Asked(normalised, id, now)
        // Ce qui est trop vieux pour servir de garde ne sert plus à rien : la mémoire ne grandit pas.
        asked.removeAll { it.at < now - repeatWindowMs }
        return GroupAskDecision.Send(id, text)
    }

    /** L'identifiant de la dernière question admise (`null` avant la première). */
    val lastAskedId: String? get() = lastId.takeIf { it.isNotEmpty() }

    private fun grows(known: String, fresh: String): Boolean {
        if (known == fresh) return true
        // Un mot isolé n'est pas une transcription qui grandit (« oui » puis « oui » est traité
        // par l'égalité) : il faut au moins deux mots pour parler de la même phrase.
        val words = known.split(' ').count { it.isNotEmpty() }
        if (words < 2) return false
        return fresh.startsWith("$known ") || known.startsWith("$fresh ")
    }

    private fun sameWords(known: String, fresh: String): Boolean {
        if (known == fresh) return true
        // D'abord la ressemblance de mots : en dessous du seuil, ce n'est pas la même phrase.
        if (transcriptSimilarity(known, fresh) < similarity) return false
        val a = known.split(' ').filter { it.isNotEmpty() }
        val b = fresh.split(' ').filter { it.isNotEmpty() }
        if (minOf(a.size, b.size) < MIN_SAME_WORDS) return false
        // Deux mots d'écart au plus : au-delà, ce n'est plus la même phrase reconnue autrement.
        if (kotlin.math.abs(a.size - b.size) > MAX_SAME_WORD_GAP) return false
        return containsInOrder(a, b) || containsInOrder(b, a)
    }

    /** Les mots de [short] apparaissent-ils dans [long], dans l'ordre ? (un mot avalé, rien de plus) */
    private fun containsInOrder(short: List<String>, long: List<String>): Boolean {
        if (short.size > long.size) return false
        var at = 0
        for (word in long) {
            if (at < short.size && word == short[at]) at += 1
        }
        return at == short.size
    }

    internal companion object {
        /** Combien de temps une phrase déjà posée ne repart pas. */
        const val ASK_REPEAT_WINDOW_MS = 20_000L

        /**
         * Combien de temps une transcription peut encore grandir. Court : c'est le temps que met le
         * moteur à finir **sa** phrase, pas celui d'une nouvelle question.
         */
        const val ASK_GROWTH_WINDOW_MS = 8_000L

        /** La part de mots communs au-delà de laquelle deux phrases sont la même question. */
        const val ASK_SIMILARITY = 0.8f

        /** En dessous, deux phrases courtes se ressemblent trop facilement pour être comparées. */
        private const val MIN_SAME_WORDS = 3

        /** Combien de mots le moteur peut avaler ou rajouter sans changer la question. */
        private const val MAX_SAME_WORD_GAP = 2
    }
}
