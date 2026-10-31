package com.onyx.app.voice

import java.util.Locale

/**
 * Le choix de la voix d'un bot (`BotVoicePicker` de la référence), hors de l'écran : les entrées offertes,
 * ce que dit « Automatique », quelles voix de l'appareil sont offertes, le résumé d'un choix, et
 * la fiche qu'un choix écrit.
 *
 * La feuille s'en sert telle quelle — la mise en page, elle, vit dans `BotVoicePickerScreen` — et
 * rien ici ne touche à un écran ni à un moteur : ce qui se vérifie se vérifie sans appareil.
 */
object BotVoicePicker {

    /** Une entrée de la liste : ce qu'elle montre, et la fiche [BotVoice] qu'elle règle. */
    data class Choice(
        /** L'identifiant de la voix : `Voice.name` d'Android, ou l'identifiant Kokoro. */
        val id: String,
        /** Son nom, tel qu'il se lit dans la rangée. */
        val name: String,
        /** Sa ligne de détail (qualité · langue, identifiant Kokoro, ou rien). */
        val detail: String?,
        /** La fiche qu'un appui règle pour le bot. */
        val voice: BotVoice,
    )

    /**
     * Les voix offertes, dans l'ordre du modèle : celles de la langue d'Onyx d'abord — c'est avec
     * elles qu'une réplique dans cette langue se lit —, puis les autres par nom. Sur l'appareil,
     * les meilleures installées d'abord, et jamais une voix de démonstration.
     */
    fun choices(
        engine: ReplyVoice,
        kokoroVoices: List<String>,
        kokoroMain: String,
        mainName: String,
        installed: List<SpokenVoice>,
        listening: String,
    ): List<Choice> = when (engine) {
        ReplyVoice.KOKORO -> {
            val main = BotVoices.kokoroLanguage(kokoroMain)
            kokoroVoices.distinct()
                .sortedWith(compareBy({ BotVoices.kokoroLanguage(it) != main }, { it }))
                .map { id ->
                    Choice(
                        id = id,
                        name = kokoroName(id),
                        // « La voix d'Onyx » : celle sur laquelle il lit déjà, dite en clair.
                        detail = if (id == kokoroMain) "$mainName's voice" else id,
                        voice = BotVoice(kokoro = id),
                    )
                }
        }

        ReplyVoice.ON_DEVICE -> installed
            .filter { BotVoices.base(it.language) == listening && !it.novelty }
            // 2 : une voix « enhanced » d'Android, qui passe avant les autres, comme sur la référence.
            .sortedWith(compareByDescending<SpokenVoice> { it.quality }.thenBy { it.id })
            .map { voice ->
                Choice(
                    id = voice.id,
                    // Android ne nomme pas ses voix : `Voice.name` est leur nom, et il se montre.
                    name = voice.id,
                    detail = qualityLabel(voice.quality) + " · " + voice.language,
                    voice = BotVoice(onDevice = voice.id),
                )
            }

        ReplyVoice.HERMES -> emptyList()
    }

    /**
     * La voix que « Automatique » donne au bot maintenant : celle de son cru, ou `null` quand il
     * n'y a rien à répartir — le serveur n'a pas d'autre voix dans sa langue, ou l'appareil n'en a
     * pas d'autre installée. C'est la voix d'Onyx qui lit alors.
     *
     * [others] est ce qui est réglé pour les autres bots (celui-ci retiré) ; [names] les noms de la
     * salle, qui décident de qui reçoit quoi (`BotVoices`).
     */
    fun automaticVoice(
        engine: ReplyVoice,
        profile: String,
        others: Map<String, BotVoice>,
        kokoroMain: String,
        kokoroVoices: List<String>,
        installed: List<SpokenVoice>,
        listening: String,
        deviceMain: String?,
        names: List<String>,
    ): String? = when (engine) {
        ReplyVoice.KOKORO -> BotVoices.kokoro(
            forProfile = profile,
            picked = others,
            main = kokoroMain,
            available = kokoroVoices,
            names = names,
        )

        ReplyVoice.ON_DEVICE -> BotVoices.onDevice(
            forProfile = profile,
            picked = others,
            language = listening,
            main = deviceMain,
            installed = installed,
            names = names,
        )

        ReplyVoice.HERMES -> null
    }

    /**
     * « Automatique », « La voix de l'agent principal », une voix Kokoro nommée, ou la voix de
     * l'appareil — puis sa vitesse, quand elle en a une. Le résumé de la rangée « Voix » de
     * l'éditeur d'un bot (`BotEditor.voiceSummary`).
     */
    fun summary(voice: BotVoice?): String {
        val speed = voice?.speed?.let { " · " + String.format(Locale.ROOT, "%.2f×", it) } ?: ""
        val kokoro = voice?.kokoro.orEmpty()
        val onDevice = voice?.onDevice.orEmpty()
        return when {
            voice?.sameAsMain == true -> "Same as the main agent$speed"
            kokoro.isNotEmpty() -> kokoroName(kokoro) + speed
            onDevice.isNotEmpty() -> onDevice + speed
            else -> "Automatic$speed"
        }
    }

    /**
     * La fiche qu'un choix écrit : la voix choisie, sa vitesse gardée telle quelle (`set` du
     * modèle). Rien du tout — pas de voix, pas de vitesse — efface le réglage.
     */
    fun withVoice(voice: BotVoice?, current: BotVoice?): BotVoice? {
        val record = voice?.copy(speed = current?.speed) ?: BotVoice(speed = current?.speed)
        return record.takeUnless { it.isEmpty }
    }

    /** La fiche qu'un changement de vitesse écrit : sa voix gardée, sa vitesse changée. */
    fun withSpeed(speed: Float?, current: BotVoice?): BotVoice? {
        val record = current?.copy(speed = speed) ?: BotVoice(speed = speed)
        return record.takeUnless { it.isEmpty }
    }

    /**
     * Un identifiant Kokoro nommé : « am_adam » devient « Adam (American, male) » — `kokoroName`
     * du modèle, mot pour mot. Ce qui n'a pas la forme `<langue><f|m>_<nom>` est rendu tel quel.
     */
    fun kokoroName(id: String): String {
        val underscore = id.indexOf('_')
        // `<langue><f|m>_<nom>` : la lettre de langue, la lettre de genre, puis le nom.
        if (underscore != 2) return id
        val who = when (id[1]) {
            'f' -> "female"
            'm' -> "male"
            else -> null
        }
        val place = accent[id[0]]
        val about = listOfNotNull(place, who).joinToString(", ")
        return id.substring(3).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() } +
            if (about.isEmpty()) "" else " ($about)"
    }

    /** La qualité d'une voix de l'appareil, dite comme référence : « Enhanced » ou « Default ». */
    fun qualityLabel(quality: Int): String = if (quality >= 2) "Enhanced" else "Default"

    /** Les lettres de langue des voix Kokoro, comme `BotVoicePicker.kokoroName` du modèle. */
    private val accent = mapOf(
        'a' to "American",
        'b' to "British",
        'e' to "Spanish",
        'f' to "French",
        'h' to "Hindi",
        'i' to "Italian",
        'j' to "Japanese",
        'p' to "Portuguese",
        'z' to "Chinese",
    )
}
