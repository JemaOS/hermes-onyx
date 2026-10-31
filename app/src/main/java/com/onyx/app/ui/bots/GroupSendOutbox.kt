package com.onyx.app.ui.bots

import com.onyx.app.data.model.OutboxItem
import com.onyx.app.data.net.TransportError
import com.onyx.app.ui.chat.OutboxQueue

/**
 * La file d'envoi d'une salle : ce qui n'est **pas encore** arrivé au serveur, et rien d'autre.
 *
 * Elle est la même que celle du chat ([OutboxQueue]) — même ordre, même tête, même retrait —, avec
 * ce qu'une reprise demande en plus : combien de fois un message a été tenté, et **quand** il a le
 * droit de repartir. Un message n'entre qu'une fois (son identifiant est son `event_id`), et il n'en
 * sort que parce que le serveur l'a pris, ou parce qu'on a renoncé **en le disant**.
 *
 * Le temps est passé en paramètre : la file se laisse éprouver sans horloge ni attente.
 */
internal class GroupSendOutbox(
    /** Les poses entre deux essais : croissantes, exprès. */
    val backoffMs: List<Long> = SEND_BACKOFF_MS,
) {
    private val queue = OutboxQueue()

    /**
     * Tout ce qui a été confié à cette file, même déjà parti. C'est ce qui garantit qu'une question
     * rejouée par l'écran (recomposition, phase qui repasse) ne repart pas : son identifiant est
     * déjà connu, et la file le refuse.
     */
    private val seen = mutableSetOf<String>()

    private val attempts = mutableMapOf<String, Int>()
    private val dueAt = mutableMapOf<String, Long>()

    /** Ce qui attend encore d'arriver au serveur, dans l'ordre où cela a été dit. */
    val items: List<OutboxItem> get() = queue.items

    val isNotEmpty: Boolean get() = queue.items.isNotEmpty()

    /** Vrai si ce message a déjà été confié à la file — parti, en attente, ou abandonné. */
    fun knows(id: String): Boolean = id in seen

    /** Met un message en file. Rend `false` s'il y était déjà : **jamais deux fois le même**. */
    fun offer(id: String, text: String, now: Long = System.currentTimeMillis()): Boolean {
        if (!seen.add(id)) return false
        queue.enqueue(OutboxItem(id = id, text = text, queuedAt = now), waiting = false)
        attempts[id] = 0
        dueAt[id] = now
        return true
    }

    /** La tête de la file, si son tour est venu (`null` : le tête attend encore sa pose). */
    fun due(now: Long = System.currentTimeMillis()): OutboxItem? {
        val head = queue.items.firstOrNull() ?: return null
        return head.takeIf { (dueAt[it.id] ?: now) <= now }
    }

    /** L'essai a échoué : le message **reste** en file, repoussé. Rend la pose appliquée. */
    fun retryLater(id: String, now: Long = System.currentTimeMillis()): Long {
        val count = (attempts[id] ?: 0) + 1
        attempts[id] = count
        val wait = backoffMs[(count - 1).coerceAtMost(backoffMs.lastIndex)]
        dueAt[id] = now + wait
        return wait
    }

    /** Combien d'essais ont déjà échoué pour ce message. */
    fun attemptsOf(id: String): Int = attempts[id] ?: 0

    /** Vrai quand les poses sont épuisées : le prochain échec est définitif. */
    fun exhausted(id: String): Boolean = attemptsOf(id) >= backoffMs.size

    /** Le serveur a pris le message : il quitte la file. */
    fun sent(id: String) {
        queue.remove(id)
        dueAt.remove(id)
        attempts.remove(id)
    }

    /** On renonce : le message quitte la file, et l'appelant **dit** pourquoi. */
    fun giveUp(id: String): OutboxItem? = queue.remove(id).also {
        dueAt.remove(id)
        attempts.remove(id)
    }

    /** La tête arrive à échéance : le délai à attendre, ou `0` s'il peut partir maintenant. */
    fun waitFor(now: Long = System.currentTimeMillis()): Long =
        (queue.items.firstOrNull()?.let { dueAt[it.id] } ?: now).let { (it - now).coerceAtLeast(0) }

    internal companion object {
        /**
         * Les poses entre deux essais : 1 s, 2 s, 3 s, 5 s. Croissantes, et assez courtes pour que
         * la personne n'attende pas une éternité avant d'apprendre que son message n'est pas passé.
         */
        val SEND_BACKOFF_MS = listOf(1_000L, 2_000L, 3_000L, 5_000L)

        /** Les codes d'un refus qui n'est **pas** une panne : réessayer n'y changerait rien. */
        val STALE_CODES = setOf(5118, 5119)
    }
}

/**
 * Un envoi qui a échoué vaut-il un nouvel essai ?
 *
 * On réessaie tout ce qui peut passer (réseau coupé, délai dépassé, 5xx, erreur interne du
 * serveur), et on ne réessaie **pas** ce qui ne passera jamais (adresse invalide, clé refusée,
 * 4xx métier, question qui n'attend plus). Un doute profite au réessai : un message perdu est pire
 * qu'un essai de trop — le serveur reconnaît l'`event_id` et ne garde le message qu'une fois.
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
