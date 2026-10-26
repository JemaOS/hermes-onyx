package com.onyx.app.data.net

/**
 * Le jeton du pont, tel qu'il est saisi — ramené à sa valeur avant l'envoi.
 *
 * Un jeton ne se tape pas: il se copie. Et un copier-coller ramène des restes — un blanc ou un
 * retour à la ligne ramassé dans un terminal, des guillemets ajoutés par un message, le préfixe
 * qu'on venait de lire dans la documentation (`Bearer `, `token=`) collé **devant** la valeur alors
 * que l'app ajoute déjà le sien. Refuser la connexion pour un espace serait une panne inventée par
 * l'app: le pont tolère ces restes, l'app fait de même.
 *
 * Ce qui reste après nettoyage est **le** jeton: il part dans un en-tête `Authorization`, il n'est
 * jamais journalisé ni affiché.
 */
object ComputerToken {

    private val PREFIXES = listOf("bearer ", "token=", "token ", "jeton=", "jeton ")

    /**
     * Le jeton débarrassé des blancs autour et d'un éventuel `Bearer ` collé devant.
     *
     * C'est la forme minimale du nettoyage, celle qui s'applique avant tout envoi: un retour à la
     * ligne ramassé dans un terminal, et le préfixe recopié d'un exemple de documentation. Le
     * nettoyage plus complet ([clean]) s'applique ensuite, quand on veut aussi les guillemets et les
     * préfixes `token=`/`jeton=`.
     */
    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        val prefix = "Bearer "
        val hasPrefix = trimmed.length > prefix.length &&
            trimmed.startsWith("Bearer", ignoreCase = true) &&
            trimmed[prefix.length - 1].isWhitespace()
        return if (hasPrefix) trimmed.substring(prefix.length).trim() else trimmed
    }

    /**
     * Le jeton nettoyé: sans blancs autour, sans guillemets, sans préfixe recollé.
     *
     * Un blanc **à l'intérieur** n'est jamais retiré en douce: il ne peut pas venir d'un collage
     * propre, et le corriger masquerait une valeur fausse. [looksMalformed] le dit à l'utilisateur.
     */
    fun clean(raw: String): String {
        var value = raw.replace("\u200b", "").replace("\ufeff", "").trim().trim('"').trim('\'').trim()
        // Le préfixe peut arriver deux fois: l'app en ajoute un, l'utilisateur en avait déjà tapé un.
        repeat(3) {
            val prefix = PREFIXES.firstOrNull { value.length > it.length && value.startsWith(it, ignoreCase = true) }
            if (prefix == null) return@repeat
            value = value.substring(prefix.length).trim().trim('"').trim('\'').trim()
        }
        return value
    }

    /** Un blanc à l'intérieur: la valeur ne peut pas être un jeton, on le dit au lieu d'essayer. */
    fun looksMalformed(cleaned: String): Boolean =
        cleaned.any { it.isWhitespace() } || cleaned.isEmpty()

    /**
     * Vrai quand la valeur ne peut pas être un jeton du tout.
     *
     * L'app ne connaît pas la longueur attendue: c'est le pont qui la donne, dans le motif de son
     * refus (`bad-token:len:18:20`). Ce contrôle avant l'envoi ne rejette donc que l'impossible —
     * moins de quatre caractères, ce qu'un jeton du pont (vingt) ne fait jamais après un collage.
     * Au-delà, c'est le pont qui juge: il refuse en nommant les deux longueurs, ce qui est plus
     * utile qu'un refus local deviné. Rejeter plus large ici couperait des jetons légitimes d'un
     * autre pont, et l'écran afficherait « jeton coupé » pour une valeur qui, elle, allait très bien.
     */
    fun obviouslyTooShort(cleaned: String): Boolean = cleaned.length < 4
}
