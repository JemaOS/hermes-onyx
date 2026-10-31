package com.onyx.app.voice

import kotlinx.serialization.Serializable

/**
 * La voix qu'un bot lit ses répliques, telle qu'elle a été réglée pour lui (`Settings.botVoices`).
 *
 * Rien de réglé, c'est « Automatique » : une voix de son cru parmi celles dont Onyx lit, comme un
 * bot a un visage de son cru (`BotVoices`).
 */
@Serializable
data class BotVoice(
    /** Une voix du synthétiseur, par son identifiant (`Voice.name` d'Android). */
    val onDevice: String? = null,
    /** Une voix du serveur Kokoro, par son identifiant (« am_adam »). */
    val kokoro: String? = null,
    /** Lue avec la voix d'Onyx, comme avant que les bots aient une voix. */
    val sameAsMain: Boolean? = null,
    /**
     * Sa vitesse (1 = celle de la voix), quelle que soit sa voix ; rien lit à la vitesse d'Onyx
     * (Réglages → Voix → Voice speed).
     */
    val speed: Float? = null,
) {
    /** Aucune voix réglée : il en reçoit une des siennes ([BotVoices]). */
    val isAutomatic: Boolean get() = onDevice == null && kokoro == null && sameAsMain != true

    /** Rien de réglé du tout, voix ni vitesse : autant dire aucune fiche. */
    val isEmpty: Boolean get() = isAutomatic && speed == null
}

/**
 * Une voix installée, réduite à ce qui décide (`SpokenLanguage.AppleVoice` de la référence, transposé au
 * `android.speech.tts.Voice` d'Android).
 */
data class SpokenVoice(
    /** L'identifiant de la voix (`Voice.name`) : « fr-fr-x-frd-local ». */
    val id: String,
    /** Sa langue, « fr-FR ». */
    val language: String,
    /** 2 : une voix de meilleure qualité (enhanced/premium de la référence), 1 : les autres. */
    val quality: Int,
    /** Une voix de démonstration : jamais donnée à un bot. */
    val novelty: Boolean = false,
)

/**
 * Quelle voix lit les mots d'un bot : celle qui lui a été réglée, sinon une des siennes, dans la
 * langue de la voix d'Onyx et jamais celle d'Onyx, **deux bots jamais pareils** tant qu'il y a des
 * voix à répartir. Le principal lit de la voix d'Onyx.
 *
 * Aucune hauteur n'est inventée nulle part : ce qui décide, c'est la voix du bot — son réglage, ou
 * celle que cette répartition lui donne.
 */
object BotVoices {

    /**
     * La voix Kokoro des mots de `profile`, ou `null` pour celle d'Onyx (`main`).
     *
     * `available` vide (le serveur n'a pas donné sa liste) : rien n'est promis, la voix réglée est
     * prise telle quelle et la répartition se tait — c'est ce que fait la référence.
     */
    fun kokoro(
        forProfile: String?,
        picked: Map<String, BotVoice>,
        main: String,
        available: List<String>,
        /** Les noms de la salle : deux bots n'ont pas la même voix tant qu'il y en a assez. */
        names: List<String> = emptyList(),
    ): String? {
        val profile = forProfile?.takeIf { it.isNotEmpty() && it != "default" } ?: return null
        if (picked[profile]?.sameAsMain == true) return null
        val chosen = picked[profile]?.kokoro
        if (chosen != null && (available.isEmpty() || available.contains(chosen))) return chosen
        val language = kokoroLanguage(main) ?: return null
        val pool = available.filter { it != main && kokoroLanguage(it) == language }
        val unpicked = unpicked(names, picked) { it.kokoro }
        return assign(profile, names = unpicked, tiers = listOf(pool), taken = picked.values.mapNotNull { it.kokoro })
    }

    /**
     * La voix Kokoro qu'une réplique envoie au service : **celle de son bot** quand il en a une
     * (`kokoro`), sinon **celle d'Onyx** (`main`) — c'est ce que « Automatique » donne, et c'est le
     * comportement d'avant les voix par bot. La lecture s'en sert telle quelle
     * (`SpeechOutput.startRemote`), donc c'est **cette** valeur qui part dans la requête.
     */
    fun kokoroFor(
        forProfile: String?,
        picked: Map<String, BotVoice>,
        main: String,
        available: List<String> = emptyList(),
        names: List<String> = emptyList(),
    ): String = kokoro(forProfile, picked, main, available, names) ?: main

    /**
     * La voix du synthétiseur des mots de `profile`, dans `language` (« fr »), ou `null` pour celle
     * d'Onyx (`main`, l'identifiant de la voix avec laquelle Onyx lit cette langue).
     *
     * Les meilleures voix installées passent d'abord (qualité 2), les autres ensuite : un bot qu'on
     * entend à part des autres vaut mieux qu'une voix plus fine.
     */
    fun onDevice(
        forProfile: String?,
        picked: Map<String, BotVoice>,
        language: String,
        main: String?,
        installed: List<SpokenVoice>,
        names: List<String>,
    ): String? {
        val profile = forProfile?.takeIf { it.isNotEmpty() && it != "default" } ?: return null
        if (picked[profile]?.sameAsMain == true) return null
        val speaking = installed.filter { base(it.language) == language && !it.novelty }
        val chosen = picked[profile]?.onDevice
        if (chosen != null && speaking.any { it.id == chosen }) return chosen
        val others = speaking.filter { it.id != main }
        val tiers = listOf(
            others.filter { it.quality >= 2 }.map { it.id },
            others.filter { it.quality < 2 }.map { it.id },
        )
        val unpicked = unpicked(names, picked) { it.onDevice }
        return assign(profile, names = unpicked, tiers = tiers, taken = picked.values.mapNotNull { it.onDevice })
    }

    /**
     * La voix de `name` : celle sur laquelle tombe son nom dans la première des `tiers` qui en a
     * une de libre, sinon la suivante de libre là — les noms passant dans l'ordre, pour que deux
     * bots n'en partagent pas tant qu'il y en a assez ; au-delà, celle sur laquelle son nom tombe
     * parmi toutes. `taken` sont les voix réglées, qu'aucun ne reçoit.
     */
    fun assign(name: String, names: List<String>, tiers: List<List<String>>, taken: List<String>): String? {
        val live = tiers.map { it.sorted() }.filter { it.isNotEmpty() }
        val all = live.flatten()
        if (all.isEmpty()) return null
        val used = taken.filter { all.contains(it) }.toMutableSet()
        val roster = (names + name).filter { it != "default" }.toSortedSet()
        for (other in roster) {
            val given: String
            val tier = live.firstOrNull { pool -> pool.any { !used.contains(it) } }
            if (tier != null) {
                var index = (hash(other) % tier.size.toULong()).toInt()
                while (used.contains(tier[index])) index = (index + 1) % tier.size
                given = tier[index]
            } else {
                given = all[(hash(other) % all.size.toULong()).toInt()]
            }
            if (other == name) return given
            used.add(given)
        }
        return null
    }

    /** La vitesse à laquelle `profile` est lu : la sienne si elle est réglée, sinon celle d'Onyx. */
    fun speed(forProfile: String?, picked: Map<String, BotVoice>, main: Float): Float {
        val profile = forProfile?.takeIf { it.isNotEmpty() && it != "default" } ?: return main
        val own = picked[profile]?.speed ?: return main
        return if (own > 0f) own else main
    }

    /**
     * La langue de base d'un code (« fr » de « fr-FR », « en » de « en_US ») : `SpokenLanguage.base`
     * de la référence.
     */
    fun base(code: String): String =
        code.split('-', '_').firstOrNull()?.lowercase().orEmpty()

    /** Les lettres de langue des voix Kokoro, `<langue><f|m>_<nom>` (`SpokenLanguage.kokoroLetters`). */
    private val kokoroLetters = mapOf(
        "a" to "en", "b" to "en", "e" to "es", "f" to "fr", "h" to "hi",
        "i" to "it", "j" to "ja", "p" to "pt", "z" to "zh",
    )

    /**
     * La langue de base d'une voix Kokoro — « fr » de « ff_siwis » —, ou `null` pour tout ce qui
     * n'est pas un identifiant Kokoro (les voix d'autres serveurs, « echo », ne disent rien).
     */
    fun kokoroLanguage(of: String): String? {
        val code = of.trim().take(3)
        if (code.length != 3) return null
        if (!code[0].isLetter() || !code[0].isLowerCase()) return null
        if (code[1] != 'f' && code[1] != 'm') return null
        if (code[2] != '_') return null
        return kokoroLetters[code[0].toString()]
    }

    /** Les noms de la salle auxquels aucune voix n'a encore été réglée. */
    private fun unpicked(
        names: List<String>,
        picked: Map<String, BotVoice>,
        of: (BotVoice) -> String?,
    ): List<String> = names.filter { name ->
        picked[name]?.let { voice -> of(voice) != null || voice.sameAsMain == true } != true
    }

    /** FNV-1a : la même voix pour le même nom sur tout appareil et à chaque lancement. */
    fun hash(text: String): ULong {
        var hash = 14_695_981_039_346_656_037uL
        for (byte in text.toByteArray(Charsets.UTF_8)) {
            hash = (hash xor (byte.toInt() and 0xFF).toULong()) * 1_099_511_628_211uL
        }
        return hash
    }
}
