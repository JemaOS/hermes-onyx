package com.onyx.app.data.capture

/**
 * Une note prise en passant : ce que « capture rapide » recueille.
 *
 * « Le plombier passe jeudi à dix » n'est pas une question, et la conversation qui se trouve
 * ouverte n'est pas l'endroit où elle se range. La note est gardée ici **au moment où elle est
 * prise**, et remise dès que le serveur répond — tout de suite en règle générale, plus tard si le
 * téléphone est hors ligne.
 *
 * Une note appartient au serveur et au profil qui servaient quand elle a été prise, et les attend :
 * une note prise pour « Travail » ne part pas chez « Maison » parce que c'est Maison qui est
 * ouvert une heure plus tard.
 */
data class CaptureNote(
    val id: String,
    val text: String,
    val at: Long,
    /** Le serveur pour lequel elle a été prise (son identifiant de configuration). */
    val server: String,
    val profile: String? = null,
)

/** Les mots du classement, écrits une fois. */
object CaptureCopy {

    /** La conversation dans laquelle les notes sont déposées, sur le serveur comme ici. */
    const val Title = "Notes"

    /**
     * Ce que l'agent reçoit : les notes, l'heure de chacune, et le fait que personne n'attend.
     *
     * Même intention que le message de la référence, mot pour mot sur le fond : ranger là où ça va,
     * répondre en quelques mots, ne rien demander en retour.
     */
    fun message(notes: List<CaptureNote>, whenLabel: (CaptureNote) -> String): String {
        if (notes.isEmpty()) return ""
        val several = notes.size > 1
        val lead = "${if (several) "Notes" else "A note"} I took in passing, with Onyx's quick capture. " +
            "File ${if (several) "each" else "it"} where it belongs: your memory, a list, a reminder, " +
            "the calendar, whatever fits. I'm not waiting for an answer, so reply in a few words with " +
            "what you did, and don't ask me anything back."
        val body = notes.joinToString("\n") { note -> "- ${whenLabel(note)}: ${note.text}" }
        return "$lead\n\n$body"
    }
}

/** Ce qu'une remise a donné. */
enum class CaptureOutcome {
    /** La ou les notes sont arrivées : elles ne sont plus à nous. */
    FILED,

    /** Elles n'ont pas pu partir : elles restent, et repartiront. */
    WAITING,

    /** Rien à remettre (aucune note, ou aucune pour ce serveur-ci). */
    NOTHING,
}

/**
 * La boîte des notes : ce qui attend, et ce qui peut partir.
 *
 * La logique est ici, sans Android, pour être éprouvée sans appareil : la remise elle-même
 * (`deliver`) et l'écriture sur le disque (`persist`) sont données de l'extérieur.
 */
class CaptureInbox(
    initial: List<CaptureNote> = emptyList(),
    private val now: () -> Long = { System.currentTimeMillis() },
    private val newId: () -> String = { java.util.UUID.randomUUID().toString() },
    private val persist: (List<CaptureNote>) -> Unit = {},
    private val deliver: suspend (List<CaptureNote>) -> Unit = {},
) {

    private val values = initial.toMutableList()

    /** Les notes pas encore remises, la plus ancienne d'abord. */
    val waiting: List<CaptureNote> get() = values.toList()

    /** Vrai pendant qu'une remise est en cours : deux remises ne se chevauchent pas. */
    var isFiling: Boolean = false

    /** Vrai quand au moins une note est prise pour ce serveur et ce profil-ci. */
    fun hasReady(server: String, profile: String?): Boolean =
        ready(server, profile).isNotEmpty()

    /** Les notes qui peuvent partir maintenant : celles du serveur et du profil en service. */
    fun ready(server: String, profile: String?): List<CaptureNote> =
        values.filter { it.server == server && it.profile == profile }

    /**
     * Prend une note. Faux pour une note vide : rien n'est gardé pour un texte blanc, comme le
     * refus de la référence.
     */
    fun add(text: String, server: String, profile: String?): Boolean {
        val clean = text.trim()
        if (clean.isEmpty()) return false
        values += CaptureNote(
            id = newId(),
            text = clean,
            at = now(),
            server = server,
            profile = profile,
        )
        persist(values.toList())
        return true
    }

    /** Retire une note à la main (la note est effacée, jamais remise). */
    fun remove(id: String): Boolean {
        val removed = values.removeAll { it.id == id }
        if (removed) persist(values.toList())
        return removed
    }

    /**
     * Remet ce qui peut partir, en un seul lot. Ce qui n'arrive pas reste et repartira.
     *
     * Un lot déjà remis part **en entier** : une note qui a atteint le serveur ne repart jamais
     * une seconde fois, sinon elle serait classée deux fois.
     */
    suspend fun file(server: String, profile: String?): CaptureOutcome {
        val batch = ready(server, profile)
        if (isFiling || batch.isEmpty()) return CaptureOutcome.NOTHING
        isFiling = true
        return try {
            deliver(batch)
            val sent = batch.map { it.id }.toSet()
            values.removeAll { it.id in sent }
            persist(values.toList())
            CaptureOutcome.FILED
        } catch (_: Throwable) {
            CaptureOutcome.WAITING
        } finally {
            isFiling = false
        }
    }
}
