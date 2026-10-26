package com.onyx.app.voice

/** Qui lit les réponses, avec les mêmes identifiants persistés que `Settings.ReplyVoice`. */
enum class ReplyVoice(val id: String, val label: String) {
    ON_DEVICE("onDevice", "On-device"),
    KOKORO("kokoro", "Kokoro"),
    HERMES("hermes", "Hermes"),
    ;

    companion object {
        fun fromId(raw: String?): ReplyVoice = entries.firstOrNull { it.id == raw } ?: ON_DEVICE
    }
}

/** Les langues et genres dans lesquels KokoroVoicesView range les identifiants du serveur. */
data class KokoroVoiceGroup(
    val id: String,
    val language: String,
    val gender: String,
    val voices: List<String>,
)

/** Le nom lisible d'une voix: `af_heart` devient `Heart`, comme. */
fun kokoroVoiceDisplayName(voice: String): String {
    val underscore = voice.indexOf('_')
    if (underscore < 0 || underscore == voice.lastIndex) return voice
    val name = voice.substring(underscore + 1)
    return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

/** Regroupe les ids `<langue><genre>_<nom>`, triés par code comme le modèle. */
fun groupKokoroVoices(voices: List<String>): List<KokoroVoiceGroup> {
    val languages = mapOf(
        "a" to "American English",
        "b" to "British English",
        "e" to "Spanish",
        "f" to "French",
        "h" to "Hindi",
        "i" to "Italian",
        "j" to "Japanese",
        "p" to "Portuguese",
        "z" to "Chinese",
    )
    return voices.groupBy { it.take(2) }.toSortedMap().map { (code, grouped) ->
        KokoroVoiceGroup(
            id = code,
            language = languages[code.take(1)] ?: code.uppercase(),
            gender = when {
                code.endsWith("f") -> "female"
                code.endsWith("m") -> "male"
                else -> ""
            },
            voices = grouped,
        )
    }
}
