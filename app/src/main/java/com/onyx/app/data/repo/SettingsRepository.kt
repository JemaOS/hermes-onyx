package com.onyx.app.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.onyx.app.data.local.SettingsKeys
import com.onyx.app.car.AutoCardLook
import com.onyx.app.car.AutoVoiceLook
import com.onyx.app.ui.theme.AppearanceMode
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import com.onyx.app.ui.theme.ThemeColors
import com.onyx.app.ui.voice.OrbLight
import com.onyx.app.ui.voice.VoiceOrbChoice
import com.onyx.app.voice.ReplyVoice
import com.onyx.app.voice.TalkOver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Les préférences de l'app: thème, apparence, voix et écriture.
 *
 * Comme `Settings.shared` , réduit à ce que les étapes ont porté: Apparence, Voix,
 * Writing. La forme ne change pas — un `StateFlow` lu par l'UI, écrit sur disque au changement.
 */
class SettingsRepository(private val store: DataStore<Preferences>) {

    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val _theme = MutableStateFlow(OnyxTheme.STANDARD)
    val theme: StateFlow<OnyxTheme> = _theme.asStateFlow()

    private val _appearance = MutableStateFlow(AppearanceMode.SYSTEM)
    val appearance: StateFlow<AppearanceMode> = _appearance.asStateFlow()

    /** Habillages lus par le compagnon Android Auto via AutoSettingsProvider. */
    private val _autoCardLook = MutableStateFlow(AutoCardLook.COLOR)
    val autoCardLook: StateFlow<AutoCardLook> = _autoCardLook.asStateFlow()

    private val _autoVoiceLook = MutableStateFlow(AutoVoiceLook.BARS)
    val autoVoiceLook: StateFlow<AutoVoiceLook> = _autoVoiceLook.asStateFlow()

    /** La langue du micro et de la lecture; vide = celle du téléphone. */
    private val _speechLocale = MutableStateFlow(DEFAULT_SPEECH_LOCALE)
    val speechLocale: StateFlow<String> = _speechLocale.asStateFlow()

    /** Le mode vocal se remet-il à écouter après chaque réponse ? */
    private val _handsFreeByDefault = MutableStateFlow(true)
    val handsFreeByDefault: StateFlow<Boolean> = _handsFreeByDefault.asStateFlow()

    /** La touche Entrée du clavier envoie-t-elle le message ? */
    private val _returnSends = MutableStateFlow(true)
    val returnSends: StateFlow<Boolean> = _returnSends.asStateFlow()

    /** La vitesse de la voix lue (0.5 à 2.0, comme le curseur ). */
    private val _voiceSpeed = MutableStateFlow(1.0f)
    val voiceSpeed: StateFlow<Float> = _voiceSpeed.asStateFlow()

    /** La synthèse choisie: Android, un serveur Kokoro, ou le Hermes du Dashboard. */
    private val _replyVoice = MutableStateFlow(ReplyVoice.ON_DEVICE)
    val replyVoice: StateFlow<ReplyVoice> = _replyVoice.asStateFlow()

    /** Adresse du serveur Kokoro-compatible, vide tant qu'il n'est pas configuré. */
    private val _kokoroUrl = MutableStateFlow(DEFAULT_KOKORO_URL)
    val kokoroUrl: StateFlow<String> = _kokoroUrl.asStateFlow()

    /** Voix ou mélange (`am_onyx(2)+bm_george(1)`) demandé à Kokoro. */
    private val _kokoroVoice = MutableStateFlow(DEFAULT_KOKORO_VOICE)
    val kokoroVoice: StateFlow<String> = _kokoroVoice.asStateFlow()

    /** La langue des réponses demandée au modèle; vide = automatique (celle de la question). */
    private val _replyLanguage = MutableStateFlow("")
    val replyLanguage: StateFlow<String> = _replyLanguage.asStateFlow()

    /** Demande trois questions de relance après chaque réponse (« Suggest follow-up questions »). */
    private val _suggestFollowUps = MutableStateFlow(false)
    val suggestFollowUps: StateFlow<Boolean> = _suggestFollowUps.asStateFlow()

    /** Les règles de préfixe parlé, dans l'ordre où elles s'essaient. */
    private val _spokenPrefixes = MutableStateFlow<List<com.onyx.app.ui.settings.voice.SpokenPrefix>>(emptyList())
    val spokenPrefixes: StateFlow<List<com.onyx.app.ui.settings.voice.SpokenPrefix>> = _spokenPrefixes.asStateFlow()

    /** Les façons personnelles de terminer une conversation en mains-libres. */
    private val _stopPhrases = MutableStateFlow<List<String>>(emptyList())
    val stopPhrases: StateFlow<List<String>> = _stopPhrases.asStateFlow()

    /** L'effort de raisonnement demandé (`Settings.reasoningEfforts`); vide = laissé au serveur. */
    private val _reasoningEffort = MutableStateFlow("")
    val reasoningEffort: StateFlow<String> = _reasoningEffort.asStateFlow()

    /**
     * « Parler pendant les réponses » (`Settings.TalkOver`): jamais, avec un casque seulement
     * (le défaut du modèle), ou partout. C'est ce réglage, confronté à la route du moment,
     * qui décide si le micro s'ouvre derrière une réponse — sur le haut-parleur d'un téléphone, il
     * ne s'ouvre pas, et la voix de synthèse ne peut donc pas revenir par le micro.
     */
    private val _talkOver = MutableStateFlow(TalkOver.DEFAULT)
    val talkOver: StateFlow<TalkOver> = _talkOver.asStateFlow()

    /**
     * « Earpiece when raised to your ear » (`Settings.earpieceAtEar`): téléphone levé à
     * l'oreille, la réponse se joue dans l'écouteur, comme un appel.
     */
    private val _earpieceAtEar = MutableStateFlow(true)
    val earpieceAtEar: StateFlow<Boolean> = _earpieceAtEar.asStateFlow()

    /** Alertes d'approbation reçues pendant que Onyx est en arrière-plan. */
    private val _notifyInBackground = MutableStateFlow(false)
    val notifyInBackground: StateFlow<Boolean> = _notifyInBackground.asStateFlow()

    /** Son des alertes; Android utilise deux canaux, avec et sans son. */
    private val _notificationSound = MutableStateFlow(true)
    val notificationSound: StateFlow<Boolean> = _notificationSound.asStateFlow()

    /** Notification persistante de progression pendant une réponse. */
    private val _showLiveActivity = MutableStateFlow(true)
    val showLiveActivity: StateFlow<Boolean> = _showLiveActivity.asStateFlow()

    /** Ce que l'en-tête et la zone de saisie appellent l'assistant; vide = « Onyx ». */
    private val _displayName = MutableStateFlow("")
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    /** Les couleurs choisies par thème, clés = identifiants de thème (`Settings.themeColors`). */
    private val _themeColors = MutableStateFlow<Map<String, ThemeColors>>(emptyMap())
    val themeColors: StateFlow<Map<String, ThemeColors>> = _themeColors.asStateFlow()

    /** « Bot faces »: éteint, chaque bot est son initiale sur un disque de sa couleur. */
    private val _botFaces = MutableStateFlow(true)
    val botFaces: StateFlow<Boolean> = _botFaces.asStateFlow()

    /** La voix réglée pour chaque bot, par serveur (`Settings.botVoices`). */
    private val _botVoices = MutableStateFlow<Map<String, Map<String, com.onyx.app.voice.BotVoice>>>(emptyMap())
    val botVoices: StateFlow<Map<String, Map<String, com.onyx.app.voice.BotVoice>>> = _botVoices.asStateFlow()

    /**
     * Les voix réglées pour les bots **de ce serveur** (`Settings.botVoicesHere` ): c'est ce
     * que la lecture consulte pour donner à chacun la sienne.
     */
    fun botVoicesHere(serverId: String): Map<String, com.onyx.app.voice.BotVoice> =
        _botVoices.value[serverId].orEmpty()

    /**
     * Règle la voix d'un bot (`BotVoicePicker` ); `null` efface son réglage — le bot reprend
     * alors une voix de son cru (`BotVoices`). Une fiche vide n'est pas enregistrée.
     */
    suspend fun setBotVoice(serverId: String, profile: String, voice: com.onyx.app.voice.BotVoice?) {
        val here = _botVoices.value[serverId].orEmpty().toMutableMap()
        if (voice == null || voice.isEmpty) here.remove(profile) else here[profile] = voice
        val all = _botVoices.value.toMutableMap()
        if (here.isEmpty()) all.remove(serverId) else all[serverId] = here
        _botVoices.value = all
        store.edit { it[Keys.BOT_VOICES] = json.encodeToString(BOT_VOICES_SERIALIZER, all) }
    }

    /** « Pictures from Hermes »: la photo du profil Hermes remplace le visage quand il en a une. */
    private val _botPictures = MutableStateFlow(true)
    val botPictures: StateFlow<Boolean> = _botPictures.asStateFlow()

    /** « Bot colors in their chats »: bulles et accent d'un chat prennent la couleur du bot. */
    private val _botColors = MutableStateFlow(true)
    val botColors: StateFlow<Boolean> = _botColors.asStateFlow()

    /**
     * « Link previews »: une fiche avec le titre et l'image de la page sous un message qui
     * contient un lien (`LinkPreviews`). **Éteint par défaut**: le téléphone ouvre la page
     * lui-même, donc le site voit la visite, là où Onyx ne parle sinon qu'aux serveurs qu'on lui
     * a donnés.
     */
    private val _linkPreviews = MutableStateFlow(false)
    val linkPreviews: StateFlow<Boolean> = _linkPreviews.asStateFlow()

    /** « Chat text size »: les pas -2…2 autour de la taille système (`Settings.chatTextSizes`). */
    private val _chatTextSize = MutableStateFlow(0)
    val chatTextSize: StateFlow<Int> = _chatTextSize.asStateFlow()

    /** L'orbe du mode vocal (`Settings.voiceOrb`). */
    private val _voiceOrb = MutableStateFlow(VoiceOrbChoice.ONYX)
    val voiceOrb: StateFlow<VoiceOrbChoice> = _voiceOrb.asStateFlow()

    /** Ce qui bouge dans la face de l'orbe (`Settings.orbLight`). */
    private val _orbLight = MutableStateFlow(OrbLight.NEBULA)
    val orbLight: StateFlow<OrbLight> = _orbLight.asStateFlow()

    /**
     * Le volet de gauche des deux panneaux est-il replié ? Réglage **de l'appareil** (comme les
     * autres réglages d'interface: il vit dans le `DataStore` local, pas sur le serveur): le
     * pliable déplié garde la forme que son porteur lui a donnée d'une session à l'autre.
     */
    private val _sidebarCollapsed = MutableStateFlow(false)
    val sidebarCollapsed: StateFlow<Boolean> = _sidebarCollapsed.asStateFlow()

    /** Lit ce qui a été enregistré; les valeurs par défaut sont celles de l'app. */
    suspend fun load() {
        val preferences = store.data.first()
        _theme.value = OnyxTheme.fromId(preferences[SettingsKeys.THEME])
        _appearance.value = AppearanceMode.fromId(preferences[SettingsKeys.APPEARANCE])
        _autoCardLook.value = AutoCardLook.fromId(preferences[Keys.AUTO_CARDS])
        _autoVoiceLook.value = AutoVoiceLook.fromId(preferences[Keys.AUTO_VOICE])
        _speechLocale.value = preferences[Keys.SPEECH_LOCALE] ?: DEFAULT_SPEECH_LOCALE
        _handsFreeByDefault.value = preferences[Keys.HANDS_FREE] ?: true
        _returnSends.value = preferences[Keys.RETURN_SENDS] ?: true
        _voiceSpeed.value = preferences[Keys.VOICE_SPEED] ?: 1.0f
        _replyVoice.value = ReplyVoice.fromId(preferences[Keys.REPLY_VOICE])
        _kokoroUrl.value = preferences[Keys.KOKORO_URL] ?: DEFAULT_KOKORO_URL
        _kokoroVoice.value = preferences[Keys.KOKORO_VOICE] ?: DEFAULT_KOKORO_VOICE
        _replyLanguage.value = preferences[Keys.REPLY_LANGUAGE] ?: ""
        // Le mode enregistré; à défaut l'ancien booléen (vrai valait « partout »), à défaut le
        // défaut du modèle.
        _talkOver.value = preferences[Keys.TALK_OVER_MODE]?.let(TalkOver::fromId)
            ?: preferences[Keys.TALK_OVER]?.let(TalkOver::fromLegacy)
            ?: TalkOver.DEFAULT
        _earpieceAtEar.value = preferences[Keys.EARPIECE_AT_EAR] ?: true
        _suggestFollowUps.value = preferences[Keys.SUGGEST_FOLLOW_UPS] ?: false
        _reasoningEffort.value = preferences[Keys.REASONING_EFFORT] ?: ""
        _notifyInBackground.value = preferences[Keys.NOTIFY_IN_BACKGROUND] ?: false
        _notificationSound.value = preferences[Keys.NOTIFICATION_SOUND] ?: true
        _showLiveActivity.value = preferences[Keys.SHOW_LIVE_ACTIVITY] ?: true
        _displayName.value = (preferences[Keys.DISPLAY_NAME] ?: "").trim()
        _botFaces.value = preferences[Keys.BOT_FACES] ?: true
        _botPictures.value = preferences[Keys.BOT_PICTURES] ?: true
        _botColors.value = preferences[Keys.BOT_COLORS] ?: true
        _linkPreviews.value = preferences[Keys.LINK_PREVIEWS] ?: false
        _chatTextSize.value = (preferences[Keys.CHAT_TEXT_SIZE] ?: 0)
            .coerceIn(ResolvedTheme.chatTextSizes.first(), ResolvedTheme.chatTextSizes.last())
        _voiceOrb.value = VoiceOrbChoice.fromId(preferences[Keys.VOICE_ORB])
        _orbLight.value = OrbLight.fromId(preferences[Keys.ORB_LIGHT])
        _sidebarCollapsed.value = preferences[Keys.SIDEBAR_COLLAPSED] ?: false
        _themeColors.value = runCatching {
            json.decodeFromString(THEME_COLORS_SERIALIZER, preferences[Keys.THEME_COLORS].orEmpty())
        }.getOrDefault(emptyMap())
        // Les voix réglées pour les bots, par serveur puis par profil (`Settings.botVoices`).
        _botVoices.value = runCatching {
            json.decodeFromString(BOT_VOICES_SERIALIZER, preferences[Keys.BOT_VOICES].orEmpty())
        }.getOrDefault(emptyMap())
        _spokenPrefixes.value = runCatching {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(com.onyx.app.ui.settings.voice.SpokenPrefix.serializer()),
                preferences[Keys.SPOKEN_PREFIXES].orEmpty(),
            )
        }.getOrDefault(emptyList())
        _stopPhrases.value = preferences[Keys.STOP_PHRASES]?.split("\u001F")?.filter { it.isNotEmpty() }.orEmpty()
    }

    suspend fun setTheme(theme: OnyxTheme) {
        _theme.value = theme
        store.edit { it[SettingsKeys.THEME] = theme.id }
    }

    suspend fun setAppearance(appearance: AppearanceMode) {
        _appearance.value = appearance
        store.edit { it[SettingsKeys.APPEARANCE] = appearance.id }
    }

    suspend fun setAutoCardLook(value: AutoCardLook) {
        _autoCardLook.value = value
        store.edit { it[Keys.AUTO_CARDS] = value.id }
    }

    suspend fun setAutoVoiceLook(value: AutoVoiceLook) {
        _autoVoiceLook.value = value
        store.edit { it[Keys.AUTO_VOICE] = value.id }
    }

    suspend fun setSpeechLocale(tag: String) {
        val clean = tag.trim()
        _speechLocale.value = clean.ifEmpty { DEFAULT_SPEECH_LOCALE }
        store.edit { it[Keys.SPEECH_LOCALE] = _speechLocale.value }
    }

    suspend fun setHandsFreeByDefault(value: Boolean) {
        _handsFreeByDefault.value = value
        store.edit { it[Keys.HANDS_FREE] = value }
    }

    suspend fun setReturnSends(value: Boolean) {
        _returnSends.value = value
        store.edit { it[Keys.RETURN_SENDS] = value }
    }

    suspend fun setVoiceSpeed(value: Float) {
        val clamped = value.coerceIn(0.5f, 2.0f)
        _voiceSpeed.value = clamped
        store.edit { it[Keys.VOICE_SPEED] = clamped }
    }

    suspend fun setReplyVoice(value: ReplyVoice) {
        _replyVoice.value = value
        store.edit { it[Keys.REPLY_VOICE] = value.id }
    }

    suspend fun setKokoroUrl(value: String) {
        _kokoroUrl.value = value
        store.edit { it[Keys.KOKORO_URL] = value }
    }

    suspend fun setKokoroVoice(value: String) {
        _kokoroVoice.value = value
        store.edit { it[Keys.KOKORO_VOICE] = value }
    }

    suspend fun setReplyLanguage(code: String) {
        _replyLanguage.value = code.trim()
        store.edit { it[Keys.REPLY_LANGUAGE] = _replyLanguage.value }
    }

    suspend fun setSpokenPrefixes(value: List<com.onyx.app.ui.settings.voice.SpokenPrefix>) {
        _spokenPrefixes.value = value
        store.edit {
            it[Keys.SPOKEN_PREFIXES] = json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(com.onyx.app.ui.settings.voice.SpokenPrefix.serializer()),
                value,
            )
        }
    }

    suspend fun setStopPhrases(value: List<String>) {
        _stopPhrases.value = value
        store.edit { it[Keys.STOP_PHRASES] = value.joinToString("\u001F") }
    }

    suspend fun setReasoningEffort(value: String) {
        _reasoningEffort.value = value
        store.edit { it[Keys.REASONING_EFFORT] = value }
    }

    suspend fun setSuggestFollowUps(value: Boolean) {
        _suggestFollowUps.value = value
        store.edit { it[Keys.SUGGEST_FOLLOW_UPS] = value }
    }

    suspend fun setTalkOver(value: TalkOver) {
        _talkOver.value = value
        store.edit {
            it[Keys.TALK_OVER_MODE] = value.id
            // L'ancien booléen reste écrit: une version antérieure de l'app relit le même fichier.
            it[Keys.TALK_OVER] = value != TalkOver.OFF
        }
    }

    suspend fun setEarpieceAtEar(value: Boolean) {
        _earpieceAtEar.value = value
        store.edit { it[Keys.EARPIECE_AT_EAR] = value }
    }

    suspend fun setNotifyInBackground(value: Boolean) {
        _notifyInBackground.value = value
        store.edit { it[Keys.NOTIFY_IN_BACKGROUND] = value }
    }

    suspend fun setNotificationSound(value: Boolean) {
        _notificationSound.value = value
        store.edit { it[Keys.NOTIFICATION_SOUND] = value }
    }

    suspend fun setShowLiveActivity(value: Boolean) {
        _showLiveActivity.value = value
        store.edit { it[Keys.SHOW_LIVE_ACTIVITY] = value }
    }

    suspend fun setDisplayName(value: String) {
        _displayName.value = value
        store.edit { it[Keys.DISPLAY_NAME] = value }
    }

    /** Enregistre les couleurs par thème; une entrée retirée repart sur les couleurs du thème. */
    suspend fun setThemeColors(value: Map<String, ThemeColors>) {
        _themeColors.value = value
        store.edit { it[Keys.THEME_COLORS] = json.encodeToString(THEME_COLORS_SERIALIZER, value) }
    }

    suspend fun setBotFaces(value: Boolean) {
        _botFaces.value = value
        store.edit { it[Keys.BOT_FACES] = value }
    }

    suspend fun setBotPictures(value: Boolean) {
        _botPictures.value = value
        store.edit { it[Keys.BOT_PICTURES] = value }
    }

    suspend fun setBotColors(value: Boolean) {
        _botColors.value = value
        store.edit { it[Keys.BOT_COLORS] = value }
    }

    suspend fun setLinkPreviews(value: Boolean) {
        _linkPreviews.value = value
        store.edit { it[Keys.LINK_PREVIEWS] = value }
    }

    suspend fun setChatTextSize(steps: Int) {
        val clamped = steps.coerceIn(ResolvedTheme.chatTextSizes.first(), ResolvedTheme.chatTextSizes.last())
        _chatTextSize.value = clamped
        store.edit { it[Keys.CHAT_TEXT_SIZE] = clamped }
    }

    suspend fun setVoiceOrb(orb: VoiceOrbChoice) {
        _voiceOrb.value = orb
        store.edit { it[Keys.VOICE_ORB] = orb.id }
    }

    suspend fun setOrbLight(light: OrbLight) {
        _orbLight.value = light
        store.edit { it[Keys.ORB_LIGHT] = light.id }
    }

    /** Replie ou déplie le volet de gauche des deux panneaux, sur cet appareil. */
    suspend fun setSidebarCollapsed(value: Boolean) {
        _sidebarCollapsed.value = value
        store.edit { it[Keys.SIDEBAR_COLLAPSED] = value }
    }

    /**
     * Remet **tous** les réglages à leurs valeurs d'origine, comme `Settings.shared.reset`:
     * les préférences sont effacées, puis relues — ce sont les défauts de `load` qui reviennent.
     */
    suspend fun reset() {
        store.edit { it.clear() }
        load()
    }

    private object Keys {
        val AUTO_CARDS = stringPreferencesKey("carPlayCards")
        val AUTO_VOICE = stringPreferencesKey("carPlayVoice")
        val SPEECH_LOCALE = stringPreferencesKey("speech_locale")
        val HANDS_FREE = booleanPreferencesKey("hands_free_by_default")
        val RETURN_SENDS = booleanPreferencesKey("return_sends")
        val VOICE_SPEED = floatPreferencesKey("voice_speed")
        val REPLY_VOICE = stringPreferencesKey("replyVoice")
        val KOKORO_URL = stringPreferencesKey("kokoroURL")
        val KOKORO_VOICE = stringPreferencesKey("kokoroVoice")
        val REPLY_LANGUAGE = stringPreferencesKey("reply_language")
        val TALK_OVER = booleanPreferencesKey("talk_over")
        /** `Settings.talkOver` du modèle est un choix à trois états, pas un booléen. */
        val TALK_OVER_MODE = stringPreferencesKey("talk_over_mode")
        /** La clé , mot pour mot: `Settings.Keys.earpieceAtEar`. */
        val EARPIECE_AT_EAR = booleanPreferencesKey("earpieceAtEar")
        val SUGGEST_FOLLOW_UPS = booleanPreferencesKey("suggest_follow_ups")
        val REASONING_EFFORT = stringPreferencesKey("reasoning_effort")
        val NOTIFY_IN_BACKGROUND = booleanPreferencesKey("notify_in_background")
        val NOTIFICATION_SOUND = booleanPreferencesKey("notification_sound")
        val SHOW_LIVE_ACTIVITY = booleanPreferencesKey("show_live_activity")
        val STOP_PHRASES = stringPreferencesKey("stop_phrases")
        val SPOKEN_PREFIXES = stringPreferencesKey("spoken_prefixes")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val THEME_COLORS = stringPreferencesKey("themeColors")
        // Les clés portent les mêmes noms que `Settings.Keys`.
        val BOT_FACES = booleanPreferencesKey("botFaces")
        val BOT_PICTURES = booleanPreferencesKey("botPictures")
        val BOT_COLORS = booleanPreferencesKey("botColors")
        /** La clé , mot pour mot: `Settings.Keys.botVoices`. */
        val BOT_VOICES = stringPreferencesKey("botVoices")
        // La clé , mot pour mot: `Settings.Keys.linkPreviews`.
        val LINK_PREVIEWS = booleanPreferencesKey("linkPreviews")
        val CHAT_TEXT_SIZE = intPreferencesKey("chatTextSize")
        val VOICE_ORB = stringPreferencesKey("voiceOrb")
        val ORB_LIGHT = stringPreferencesKey("orbLight")
        /** Le volet de gauche des deux panneaux, replié ou non (réglage de l'appareil). */
        val SIDEBAR_COLLAPSED = booleanPreferencesKey("sidebarCollapsed")
    }

    private val THEME_COLORS_SERIALIZER = MapSerializer(String.serializer(), ThemeColors.serializer())

    /** `Settings.botVoices`: par serveur, puis par profil de bot. */
    private val BOT_VOICES_SERIALIZER: kotlinx.serialization.KSerializer<Map<String, Map<String, com.onyx.app.voice.BotVoice>>> =
        MapSerializer(String.serializer(), MapSerializer(String.serializer(), com.onyx.app.voice.BotVoice.serializer()))

    companion object {
        const val DEFAULT_KOKORO_URL = ""
        const val DEFAULT_KOKORO_VOICE = "am_onyx(2)+bm_george(1)"

        /** Les sept niveaux d'effort, dans l'ordre; `""` = « Default ». */
        val REASONING_EFFORTS = listOf(
            "" to "Default",
            "none" to "None",
            "minimal" to "Minimal",
            "low" to "Low",
            "medium" to "Medium",
            "high" to "High",
            "xhigh" to "X-High",
            "max" to "Max",
        )

        /** La langue par défaut de la voix, celle que le brief demande. */
        const val DEFAULT_SPEECH_LOCALE = "fr-FR"

        /** Les langues de réponse proposées; la première entrée vide veut dire « automatique ». */
        val REPLY_LANGUAGES = listOf(
            "" to "Automatic",
            "fr" to "Français",
            "en" to "English",
            "de" to "Deutsch",
            "es" to "Español",
            "it" to "Italiano",
            "pt" to "Português",
            "nl" to "Nederlands",
        )

        /** La phrase ajoutée aux consignes, mot pour mot comme. */
        fun replyInstruction(code: String): String? {
            val name = REPLY_LANGUAGES.firstOrNull { it.first == code }?.second ?: return null
            return "Always reply in $name, whatever language the user writes or speaks in, " +
                "unless they ask for another language."
        }

        /** Les langues proposées, celles que les moteurs embarqués connaissent le mieux. */
        val SPEECH_LOCALES = listOf(
            "fr-FR" to "Français",
            "en-US" to "English (US)",
            "en-GB" to "English (UK)",
            "de-DE" to "Deutsch",
            "es-ES" to "Español",
            "it-IT" to "Italiano",
            "pt-BR" to "Português (BR)",
            "nl-NL" to "Nederlands",
        )
    }
}
