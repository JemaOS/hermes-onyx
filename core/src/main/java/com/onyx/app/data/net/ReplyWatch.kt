package com.onyx.app.data.net

/**
 * « Cette réponse est-elle nouvelle ? » — la seule décision du surveillant de réponses.
 *
 * Le surveillant (`ReplyWatcher`, côté app) tourne à l'aveugle : il relit le journal d'une session
 * à intervalles réguliers, sans savoir ce qui s'est passé entre deux passages. Tout ce qu'il peut
 * comparer, c'est **ce qu'il a mesuré la dernière fois** et **ce qu'il mesure maintenant** — ce
 * couple est un [Snapshot], gardé sur le disque entre deux passages.
 *
 * Rien ici ne touche à Android : c'est volontaire, pour que la règle se teste sur la JVM. Un
 * surveillant qui annoncerait la réponse d'hier, ou qui annoncerait deux fois la même, serait une
 * panne silencieuse — et c'est exactement ce que ces fonctions décident.
 *
 * La règle retenue :
 *  - **premier passage** ([isNewReply] avec `previous == null`) : on prend la mesure, on
 *    n'annonce **rien**. Une conversation jamais surveillée ne doit pas faire sonner le téléphone
 *    avec une réponse déjà lue.
 *  - le journal doit avoir **grandi** : un message de l'utilisateur qui part sans réponse ne
 *    change pas le décompte de la réponse, et n'annonce donc rien.
 *  - la **dernière réponse** doit avoir changé : deux passages sur la même réponse ne sonnent
 *    qu'une fois, même si l'utilisateur a posé une question entre-temps.
 *  - une réponse **vide** n'est pas une réponse : le tour tourne encore, ou il n'a rien produit.
 */
object ReplyWatch {

    /**
     * Ce que le surveillant retient d'une session entre deux passages.
     *
     * [count] est le nombre de messages du journal, [fingerprint] l'empreinte de la dernière
     * réponse de l'hôte (voir [fingerprintOf]) — vide quand la session n'en a pas encore.
     */
    data class Snapshot(val count: Int, val fingerprint: String) {
        /** Aucune réponse à annoncer : le tour n'a rien écrit, ou rien de visible. */
        val hasAnswer: Boolean get() = fingerprint.isNotEmpty()
    }

    /** La mesure d'un journal : sa taille et l'empreinte de sa dernière réponse. */
    fun snapshotOf(rows: List<ServeSessionMessages.Row>): Snapshot =
        Snapshot(count = rows.size, fingerprint = fingerprintOf(lastAnswer(rows)))

    /**
     * Le texte de la **dernière** réponse de l'hôte, ou `""`.
     *
     * On ne prend pas la dernière ligne du journal : un tour d'agent écrit aussi des messages
     * d'outil, et une réponse vide ne doit pas effacer celle d'avant.
     */
    fun lastAnswer(rows: List<ServeSessionMessages.Row>): String {
        for (row in rows.asReversed()) {
            if (row.role == ROLE_ASSISTANT && row.text.isNotBlank()) return row.text
        }
        return ""
    }

    /**
     * L'empreinte d'une réponse : sa longueur et son hachage, jamais son texte.
     *
     * Le disque n'a pas à garder le contenu des réponses — seulement de quoi reconnaître qu'elles
     * ont changé. Le hachage de `String` est stable d'un processus à l'autre, ce qui suffit ici :
     * ces valeurs ne quittent jamais le téléphone.
     */
    fun fingerprintOf(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""
        return trimmed.length.toString() + ":" + trimmed.hashCode().toUInt().toString(16)
    }

    /**
     * Une réponse est-elle apparue **depuis** [previous] ?
     *
     * [alreadyHere] est l'empreinte de la dernière réponse que **ce téléphone** tient déjà dans le
     * fil de la conversation ([fingerprintOf] de son texte). Quand elle est la même, la réponse est
     * sous les yeux de l'utilisateur (ou l'a été) et le suivi du travail l'a déjà annoncée : le
     * surveillant se taît, plutôt que de sonner une seconde fois pour un tour que l'app a suivi.
     *
     * `false` pour un premier passage : c'est [previous] qui manque, pas la réponse.
     */
    fun isNewReply(previous: Snapshot?, current: Snapshot, alreadyHere: String? = null): Boolean {
        if (previous == null) return false
        if (!current.hasAnswer) return false
        if (current.count <= previous.count) return false
        if (current.fingerprint == previous.fingerprint) return false
        return alreadyHere != current.fingerprint
    }

    /** La mesure telle qu'elle est gardée sur le disque : `« 12|<longueur>:<hachage> »`. */
    fun encode(snapshot: Snapshot): String = snapshot.count.toString() + SEPARATOR + snapshot.fingerprint

    /** Relit une mesure écrite par [encode] ; `null` quand la ligne est illisible (ou absente). */
    fun decode(raw: String?): Snapshot? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val cut = text.indexOf(SEPARATOR)
        if (cut <= 0) return null
        val count = text.substring(0, cut).toIntOrNull() ?: return null
        if (count < 0) return null
        return Snapshot(count = count, fingerprint = text.substring(cut + 1))
    }

    /**
     * L'aperçu d'une réponse dans la notification : les espaces ramenés à un seul, coupé court.
     *
     * Une réponse d'agent est du texte long, souvent en plusieurs paragraphes : la notification
     * n'en montre que le début, et jamais une coupure au milieu d'un mot quand c'est évitable.
     */
    fun preview(text: String, limit: Int = PREVIEW_LENGTH): String {
        val flat = text.trim().replace(WHITESPACE, " ")
        if (limit <= 0 || flat.length <= limit) return flat
        val cut = flat.take(limit - 1)
        val wordEnd = cut.lastIndexOf(' ')
        val kept = if (wordEnd > limit / 2) cut.substring(0, wordEnd) else cut
        return kept.trimEnd() + "…"
    }

    /** Le séparateur de [encode] : absent des empreintes (`chiffres:hexa`). */
    private const val SEPARATOR = "|"

    /** La longueur d'un aperçu : ce que tient une notification dépliée sur deux lignes. */
    const val PREVIEW_LENGTH = 160

    /** Le rôle du journal qui porte la réponse de l'hôte (voir `ServeSessionMessages.Row`). */
    private const val ROLE_ASSISTANT = "assistant"

    private val WHITESPACE = Regex("\\s+")
}
