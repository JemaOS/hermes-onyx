package com.onyx.app.data.net

/**
 * Le message d'un échec de sonde : **l'adresse réellement tentée**, **la raison**, et **le corps de
 * la réponse du serveur** quand il y en a un.
 *
 * Le défaut observé : un serveur répondait `HTTP 401` avec un corps on ne peut plus explicite
 * (`"clé d'installation absente ou invalide"`), et l'application disait « Adresse introuvable.
 * Vérifiez l'adresse dans les réglages. » — elle envoyait corriger une adresse qui était **juste**,
 * au lieu de montrer ce que le serveur avait dit. Le corps est donc repris ici, **verbatim** (rogné
 * et mis sur une ligne), jamais remplacé par une phrase générique.
 *
 * La logique vit hors du dessin, comme les autres calculs de la maison : elle se mesure sans monter
 * un écran, et c'est un test qui la tient.
 */
object ProbeFailure {

    /** Longueur maximale du corps repris : de quoi lire un message JSON d'erreur, jamais un roman. */
    const val BODY_LIMIT = 300

    /** Le corps d'une réponse, rogné et ramené sur une ligne — vide quand il n'y a rien à dire. */
    fun body(raw: String, limit: Int = BODY_LIMIT): String =
        raw.trim().replace(WHITESPACE, " ").take(limit)

    /**
     * « `<adresse>` — `<raison> <corps>` ».
     *
     * L'adresse vide est **omise** plutôt que rendue en tiret orphelin ; le corps vide ne laisse pas
     * de séparateur traînant.
     */
    fun message(address: String, reason: String, rawBody: String = ""): String {
        val head = if (address.isBlank()) reason.trim() else "${address.trim()} — ${reason.trim()}"
        val detail = body(rawBody)
        return if (detail.isEmpty()) head else "$head — $detail"
    }

    private val WHITESPACE = Regex("\\s+")
}
