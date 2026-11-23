package com.onyx.app.ui.bots

import com.onyx.app.voice.normalizeForEcho
import com.onyx.app.voice.transcriptSimilarity
import java.util.UUID

/**
 * What to do with a question recognised by voice.
 *
 * A question has **only one** identifier: it is the one sent to the room (`event_id` of
 * `groups.send`), that names it in the journal, and that keeps it from entering twice. A
 * transcription that grows — "are you there" then "are you there?" then "are you there,
 * guys?" — is **the same question**: it keeps its identifier and is not sent again.
 */
internal sealed class GroupAskDecision {
    /** The question goes out, once, under this fresh identifier. */
    data class Send(val id: String, val text: String) : GroupAskDecision()

    /** This is the question just asked (or its grown transcription): nothing goes out. */
    data class Again(val id: String, val why: String) : GroupAskDecision()

    /** Nothing to send (silence, noise). */
    data object Blank : GroupAskDecision()
}

/**
 * The question desk of a room: a single entry per question, however many times the engine
 * returns it.
 *
 * The defect reported by the bots themselves: "Your 'are you there' messages keep coming back".
 * The recognition engine yields a **final** every time it believes the sentence is finished, and
 * this final **grows** when it picks the thread back up ("are you there" -> "are you there?").
 * Each variant passed the session's equality guard (`lastAsked`) because the text differed by a
 * single character: so the room received the same question three or four times.
 *
 * Three rules, in this order, and a single possible outcome — [GroupAskDecision.Send]:
 *  1. **the same sentence** (ignoring case, accents and punctuation) comes back within
 *     [repeatWindowMs]: it is an engine replay;
 *  2. **the transcription has grown** (one contains the other) within [growthWindowMs]: it is the
 *     same question being written, not a new one;
 *  3. **the sentence is nearly identical** ([similarity] of common words) within [repeatWindowMs]:
 *     the engine recognised the same sentence differently.
 *
 * Time is passed as a parameter: the decision is pure, hence testable without a clock.
 */
internal class GroupAskLedger(
    private val repeatWindowMs: Long = ASK_REPEAT_WINDOW_MS,
    private val growthWindowMs: Long = ASK_GROWTH_WINDOW_MS,
    private val similarity: Float = ASK_SIMILARITY,
    /** The fresh identifier of an admitted question — replaceable for a deterministic proof. */
    private val newId: () -> String = { "ask-" + UUID.randomUUID().toString() },
) {
    private var lastText: String = ""
    private var lastId: String = ""
    private var lastAt: Long = Long.MIN_VALUE

    /** The sentences already asked, with their identifier: a sentence never comes back under another. */
    private val asked = mutableListOf<Asked>()

    private data class Asked(val text: String, val id: String, val at: Long)

    fun admit(question: String, now: Long = System.currentTimeMillis()): GroupAskDecision {
        val text = question.trim()
        val normalised = normalizeForEcho(text)
        if (normalised.isEmpty()) return GroupAskDecision.Blank

        // 1. The same sentence, as is.
        if (normalised == lastText && now - lastAt <= repeatWindowMs) {
            return GroupAskDecision.Again(lastId, "la même phrase revient")
        }

        // 2. A transcription that grows (or is trimmed): it is the same question.
        if (lastText.isNotEmpty() && now - lastAt <= growthWindowMs && grows(lastText, normalised)) {
            lastText = normalised
            lastAt = now
            return GroupAskDecision.Again(lastId, "the transcription has grown")
        }

        // 3. The same sentence recognised with one fewer (or one more) word: the engine swallowed
        //    or added a word, but it is the same question. A **substitution** ("tomorrow" ->
        //    "Tuesday", "1" -> "2") is not one: it is another question.
        val near = asked.lastOrNull { it.at >= now - repeatWindowMs && sameWords(it.text, normalised) }
        if (near != null) {
            lastText = normalised
            lastId = near.id
            lastAt = now
            return GroupAskDecision.Again(near.id, "a nearly identical sentence was just asked")
        }

        val id = newId()
        lastText = normalised
        lastId = id
        lastAt = now
        asked += Asked(normalised, id, now)
        // What is too old to serve as a guard is useless: memory does not grow.
        asked.removeAll { it.at < now - repeatWindowMs }
        return GroupAskDecision.Send(id, text)
    }

    /** The identifier of the last admitted question (`null` before the first). */
    val lastAskedId: String? get() = lastId.takeIf { it.isNotEmpty() }

    private fun grows(known: String, fresh: String): Boolean {
        if (known == fresh) return true
        // A single word is not a growing transcription ("yes" then "yes" is handled
        // by equality): at least two words are needed to speak of the same sentence.
        val words = known.split(' ').count { it.isNotEmpty() }
        if (words < 2) return false
        return fresh.startsWith("$known ") || known.startsWith("$fresh ")
    }

    private fun sameWords(known: String, fresh: String): Boolean {
        if (known == fresh) return true
        // Word similarity first: below the threshold, it is not the same sentence.
        if (transcriptSimilarity(known, fresh) < similarity) return false
        val a = known.split(' ').filter { it.isNotEmpty() }
        val b = fresh.split(' ').filter { it.isNotEmpty() }
        if (minOf(a.size, b.size) < MIN_SAME_WORDS) return false
        // Two words apart at most: beyond that, it is no longer the same sentence recognised differently.
        if (kotlin.math.abs(a.size - b.size) > MAX_SAME_WORD_GAP) return false
        return containsInOrder(a, b) || containsInOrder(b, a)
    }

    /** Do the words of [short] appear in [long], in order? (one word swallowed, nothing more) */
    private fun containsInOrder(short: List<String>, long: List<String>): Boolean {
        if (short.size > long.size) return false
        var at = 0
        for (word in long) {
            if (at < short.size && word == short[at]) at += 1
        }
        return at == short.size
    }

    internal companion object {
        /** How long an already asked sentence is not sent again. */
        const val ASK_REPEAT_WINDOW_MS = 20_000L

        /**
         * How long a transcription may still grow. Short: it is the time the engine takes to finish
         * **its** sentence, not that of a new question.
         */
        const val ASK_GROWTH_WINDOW_MS = 8_000L

        /** The share of common words beyond which two sentences are the same question. */
        const val ASK_SIMILARITY = 0.8f

        /** Below this, two short sentences look too much alike to be compared. */
        private const val MIN_SAME_WORDS = 3

        /** How many words the engine may swallow or add without changing the question. */
        private const val MAX_SAME_WORD_GAP = 2
    }
}
