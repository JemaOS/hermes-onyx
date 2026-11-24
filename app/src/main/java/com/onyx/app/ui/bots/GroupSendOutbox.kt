package com.onyx.app.ui.bots

import com.onyx.app.data.model.OutboxItem
import com.onyx.app.data.net.TransportError
import com.onyx.app.ui.chat.OutboxQueue

/**
 * A room's send queue: what has **not yet** arrived at the server, and nothing else.
 *
 * It is the same as the chat's ([OutboxQueue]) — same order, same head, same removal —, plus what
 * a retry requires: how many times a message was attempted, and **when** it is allowed to leave
 * again. A message enters only once (its identifier is its `event_id`), and it only leaves because
 * the server took it, or because we gave up **while saying so**.
 *
 * Time is passed as a parameter: the queue can be exercised without a clock or waiting.
 */
internal class GroupSendOutbox(
    /** The pauses between two attempts: increasing, on purpose. */
    val backoffMs: List<Long> = SEND_BACKOFF_MS,
) {
    private val queue = OutboxQueue()

    /**
     * Everything entrusted to this queue, even already gone. This is what guarantees that a
     * question replayed by the screen (recomposition, phase coming back) does not leave again: its
     * identifier is already known, and the queue refuses it.
     */
    private val seen = mutableSetOf<String>()

    private val attempts = mutableMapOf<String, Int>()
    private val dueAt = mutableMapOf<String, Long>()

    /** What is still waiting to arrive at the server, in the order it was said. */
    val items: List<OutboxItem> get() = queue.items

    val isNotEmpty: Boolean get() = queue.items.isNotEmpty()

    /** True if this message was already entrusted to the queue — gone, waiting, or abandoned. */
    fun knows(id: String): Boolean = id in seen

    /** Queues a message. Returns `false` if it was already there: **never the same one twice**. */
    fun offer(id: String, text: String, now: Long = System.currentTimeMillis()): Boolean {
        if (!seen.add(id)) return false
        queue.enqueue(OutboxItem(id = id, text = text, queuedAt = now), waiting = false)
        attempts[id] = 0
        dueAt[id] = now
        return true
    }

    /** The queue's head, if its turn has come (`null`: the head is still waiting for its pause). */
    fun due(now: Long = System.currentTimeMillis()): OutboxItem? {
        val head = queue.items.firstOrNull() ?: return null
        return head.takeIf { (dueAt[it.id] ?: now) <= now }
    }

    /** The attempt failed: the message **stays** in the queue, postponed. Returns the pause applied. */
    fun retryLater(id: String, now: Long = System.currentTimeMillis()): Long {
        val count = (attempts[id] ?: 0) + 1
        attempts[id] = count
        val wait = backoffMs[(count - 1).coerceAtMost(backoffMs.lastIndex)]
        dueAt[id] = now + wait
        return wait
    }

    /** How many attempts have already failed for this message. */
    fun attemptsOf(id: String): Int = attempts[id] ?: 0

    /** True when the pauses are exhausted: the next failure is final. */
    fun exhausted(id: String): Boolean = attemptsOf(id) >= backoffMs.size

    /** The server took the message: it leaves the queue. */
    fun sent(id: String) {
        queue.remove(id)
        dueAt.remove(id)
        attempts.remove(id)
    }

    /** We give up: the message leaves the queue, and the caller **says** why. */
    fun giveUp(id: String): OutboxItem? = queue.remove(id).also {
        dueAt.remove(id)
        attempts.remove(id)
    }

    /** The head becomes due: the delay to wait, or `0` if it can leave now. */
    fun waitFor(now: Long = System.currentTimeMillis()): Long =
        (queue.items.firstOrNull()?.let { dueAt[it.id] } ?: now).let { (it - now).coerceAtLeast(0) }

    internal companion object {
        /**
         * The pauses between two attempts: 1 s, 2 s, 3 s, 5 s. Increasing, and short enough that
         * the person does not wait forever before learning that their message did not go through.
         */
        val SEND_BACKOFF_MS = listOf(1_000L, 2_000L, 3_000L, 5_000L)

        /** The codes of a refusal that is **not** an outage: retrying would change nothing. */
        val STALE_CODES = setOf(5118, 5119)
    }
}

/**
 * Does a failed send deserve another attempt?
 *
 * We retry everything that can go through (network down, timeout, 5xx, server internal error), and
 * we do **not** retry what will never go through (invalid address, refused key, business 4xx,
 * a question that no longer waits). Doubt favors the retry: a lost message is worse than one extra
 * attempt — the server recognizes the `event_id` and keeps the message only once.
 */
internal fun sendIsWorthRetrying(error: Throwable): Boolean = when (error) {
    is TransportError.BadUrl,
    TransportError.MissingApiKey,
    TransportError.SignInRequired,
    TransportError.Cancelled,
    is TransportError.Malformed,
    -> false
    is TransportError.Http -> error.isTransient
    is TransportError.Rpc -> error.code !in GroupSendOutbox.STALE_CODES
    else -> true
}
