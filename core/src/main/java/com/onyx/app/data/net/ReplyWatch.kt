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
     * [trailing] est l'empreinte des **dernières lignes** du journal (voir [trailingOf]),
     * [fingerprint] celle de la dernière réponse de l'hôte — vide quand la session n'en a pas.
     *
     * **Pourquoi une empreinte et non un compte.** Le compte obligeait à lire le journal **entier**
     * pour savoir s'il avait grandi : mesuré sur cette machine, le veilleur tirait **7,8 Mo toutes
     * les seize minutes** en arrière-plan (deux sessions à 3,4 et 4,4 Mo), sur un téléphone en 4G.
     * Une empreinte des dernières lignes répond à la même question — « quelque chose est-il arrivé ?
     * » — sur une fenêtre bornée, et détecte même une réponse **de texte identique** à la précédente
     * (la fenêtre s'est décalée), ce que le compte ne faisait pas.
     */
    data class Snapshot(val trailing: String, val fingerprint: String) {
        /** Aucune réponse à annoncer : le tour n'a rien écrit, ou rien de visible. */
        val hasAnswer: Boolean get() = fingerprint.isNotEmpty()
    }

    /**
     * La fenêtre lue par le surveillant : les derniers messages seulement.
     *
     * Vingt lignes contiennent toujours la dernière réponse, le message d'utilisateur qui l'a
     * demandée et les messages d'outil du tour — ce que la règle consomme, et rien de plus.
     */
    const val WATCH_TAIL = 20

    /** La mesure d'un journal : l'empreinte de sa fin et celle de sa dernière réponse. */
    fun snapshotOf(rows: List<ServeSessionMessages.Row>): Snapshot =
        Snapshot(trailing = trailingOf(rows), fingerprint = fingerprintOf(lastAnswer(rows)))

    /**
     * L'empreinte des dernières lignes : rôle et texte, sans garder aucun contenu.
     *
     * Elle **change** dès qu'une ligne s'ajoute, qu'une réponse s'écrit ou que la fenêtre glisse —
     * c'est exactement « le journal a bougé », la seule chose que le compte savait dire, et sans
     * lire tout le journal.
     */
    fun trailingOf(rows: List<ServeSessionMessages.Row>, window: Int = TRAILING_WINDOW): String {
        if (rows.isEmpty()) return ""
        var hash = 7
        for (row in rows.takeLast(window)) {
            hash = hash * 31 + row.role.hashCode()
            hash = hash * 31 + row.text.trim().hashCode()
        }
        return rows.size.toString() + ":" + hash.toUInt().toString(16)
    }

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
        // Rien n'a bougé dans la fenêtre : ni nouvelle ligne, ni réponse réécrite.
        if (current.trailing == previous.trailing) return false
        if (current.fingerprint == previous.fingerprint) return false
        return alreadyHere != current.fingerprint
    }

    /**
     * La mesure telle qu'elle est gardée sur le disque : `« v2:<fin>|<réponse> »`.
     *
     * Le préfixe de version est ce qui protège la mise à jour : une mesure écrite par la version
     * précédente (`« 12|<empreinte> »`) n'est **pas** relue comme si elle venait de ce format — elle
     * vaut « jamais mesuré », donc le premier passage après l'installation prend la mesure sans rien
     * annoncer. Le contraire ferait sonner le téléphone pour une réponse déjà lue.
     */
    fun encode(snapshot: Snapshot): String =
        FORMAT + snapshot.trailing + SEPARATOR + snapshot.fingerprint

    /** Relit une mesure écrite par [encode] ; `null` quand la ligne est d'un autre format (ou absente). */
    fun decode(raw: String?): Snapshot? {
        val text = raw?.trim().orEmpty()
        if (!text.startsWith(FORMAT)) return null
        val body = text.removePrefix(FORMAT)
        val cut = body.indexOf(SEPARATOR)
        if (cut < 0) return null
        return Snapshot(trailing = body.substring(0, cut), fingerprint = body.substring(cut + 1))
    }

    /** Les lignes dont l'empreinte décrit « la fin du journal ». */
    private const val TRAILING_WINDOW = 3
    /** Le préfixe de format, versionné (voir [decode]). */
    private const val FORMAT = "v2:"

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
