package com.onyx.app.calls

/**
 * La logique d'appel, **pure** (sans Android), donc éprouvable sur JVM.
 *
 * Reprise de `core/phone/PhoneTool.kt` et `core/phone/AgentCalls.kt` de la référence : les
 * **heures de tranquillité** (rien ne sonne la nuit), le **résultat** d'un appel, et la **raison**
 * d'un appel placé par l'agent. Cette partie ne touche ni notification, ni écran : c'est le
 * « quand » et le « quoi », pas le « comment ».
 */
enum class CallOutcome(val id: String) {
    /** Décroché : Hermes est en ligne. */
    ANSWERED("answered"),

    /** Refusé : le tour attend dans la conversation, comme d'habitude. */
    DECLINED("declined"),

    /** Personne n'a décroché avant la fin de la sonnerie. */
    NO_ANSWER("no_answer"),

    /** Déjà en appel ou en voix avec Hermes : rien n'a sonné. */
    BUSY("busy"),

    /** Heures de tranquillité : rien n'a sonné. */
    QUIET("quiet"),
}

/**
 * Pourquoi l'agent appelle (l'outil « appelle-moi ») : la voix ouvre l'appel avec ça.
 *
 * [details] sert seulement à la voix pour répondre aux questions, jamais à lire tout haut.
 */
data class CallReason(val reason: String, val details: String? = null)

/**
 * Les heures de tranquillité : une plage horaire, en minutes depuis minuit, pendant laquelle rien
 * ne sonne — ni un appel pour une approbation, ni un appel placé par l'agent.
 *
 * Port de `AppSettings.quietNow` / `inQuietWindow` / `clockTime` de la référence
 * (`core/phone/PhoneTool.kt:47-66`).
 */
object QuietHours {

    const val MINUTES_A_DAY = 24 * 60

    /** 22:00, le début par défaut. */
    const val DEFAULT_FROM = 22 * 60

    /** 07:00, la fin par défaut. */
    const val DEFAULT_UNTIL = 7 * 60

    /**
     * [minute] tombe-t-elle de [from] jusqu'à, **sans l'inclure**, [until], tout en minutes du jour ?
     *
     * Une plage dont la fin est plus tôt que le début traverse minuit (22:00 → 07:00) ; une plage
     * qui commence où elle finit est **vide** — un silence de zéro minute, pas vingt-quatre heures.
     */
    fun inWindow(from: Int, until: Int, minute: Int): Boolean = when {
        from == until -> false
        from < until -> minute in from until until
        else -> minute >= from || minute < until
    }

    /** Rien ne sonne maintenant : les heures de tranquillité sont allumées et [minuteOfDay] est dedans. */
    fun now(minuteOfDay: Int, enabled: Boolean, from: Int, until: Int): Boolean =
        enabled && inWindow(from, until, minuteOfDay % MINUTES_A_DAY)

    /** L'heure en minutes depuis minuit maintenant, sur l'horloge du téléphone. */
    fun minuteOfDay(millis: Long, timeZone: java.util.TimeZone = java.util.TimeZone.getDefault()): Int {
        val calendar = java.util.Calendar.getInstance(timeZone).apply { timeInMillis = millis }
        return calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
    }

    /** « 22:00 », pour [minuteOfDay]. */
    fun clock(minuteOfDay: Int): String {
        val m = ((minuteOfDay % MINUTES_A_DAY) + MINUTES_A_DAY) % MINUTES_A_DAY
        return "${(m / 60).toString().padStart(2, '0')}:${(m % 60).toString().padStart(2, '0')}"
    }

    /** L'heure « HH:MM » écrite en minutes, ou `null` si elle n'est pas lisible. */
    fun parse(text: String): Int? {
        val parts = text.trim().split(":")
        if (parts.size != 2) return null
        val hour = parts[0].trim().toIntOrNull() ?: return null
        val minute = parts[1].trim().toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }
}
