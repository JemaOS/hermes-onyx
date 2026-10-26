package com.onyx.app.car

/** Contrat en lecture seule entre l'app téléphone et son compagnon Auto, signés avec la même clé. */
object AutoSettingsContract {
    const val AUTHORITY = "com.onyx.app.auto_settings"
    const val PERMISSION = "com.onyx.app.permission.AUTO_SETTINGS"
    const val METHOD_LOOKS = "looks"
    const val EXTRA_CARDS = "cards"
    const val EXTRA_VOICE = "voice"
    const val EXTRA_SERVER_NAME = "server_name"
    const val EXTRA_SERVER_URL = "server_url"
    const val EXTRA_SERVER_KEY = "server_key"
    const val EXTRA_SERVER_MODEL = "server_model"
}

/** Les six habillages de CarPlayCardSettings, conservés pour les cartes Android Auto disponibles. */
enum class AutoCardLook(val id: String, val label: String, val blurb: String) {
    COLOR("color", "Color", "Blue for Ask, green for a new chat"),
    GLOW("glow", "Glow", "Nearly black, with a light behind each symbol"),
    ORB("orb", "Orb", "The voice orb, ringed in each card's color"),
    GOLD("gold", "Gold", "Graphite, with the symbols in gold"),
    GRAPHITE("graphite", "Graphite", "Graphite and plain light symbols"),
    MARKS("marks", "Marks", "Onyx's own marks: voice bars and the gold spark"),
    ;

    companion object {
        fun fromId(raw: String?): AutoCardLook = entries.firstOrNull { it.id == raw } ?: COLOR
    }
}

/** Les cinq habillages de CarPlayVoiceSettings, rendus en images statiques par Android Auto. */
enum class AutoVoiceLook(val id: String, val label: String, val blurb: String) {
    BARS("bars", "Bars", "Three solid bars of light, and one that glides"),
    LAMPS("lamps", "Lamps", "The bars as stacks of small lamps, and a scanner"),
    KITT("kitt", "KITT", "The lamps in red, on black"),
    ORB("orb", "Orb", "A ring of light around Onyx's spark"),
    ONYX("onyx", "Onyx", "Onyx's face: it looks up to think, and talks"),
    ;

    companion object {
        fun fromId(raw: String?): AutoVoiceLook = entries.firstOrNull { it.id == raw } ?: BARS
    }
}

/** Seules ces deux cartes ont une action réelle dans le Car App actuel. */
enum class AutoStart { ASK, NEW_CHAT }

/** Android Auto ne prête pas de micro au Car App: seuls ces deux états sont donc affichés. */
enum class AutoVoiceState { THINKING, SPEAKING }
