package com.onyx.app.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.onyx.app.data.local.SettingsKeys
import com.onyx.app.calls.QuietHours
import com.onyx.app.data.model.serve.InsightsPeriod
import com.onyx.app.localization.OnyxLanguage
import com.onyx.app.notifications.WorkTracking
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
 * The app's preferences: theme, appearance, voice and writing.
 *
 * Mirror of the reference-side `Settings.shared`, reduced to what the steps carried: Appearance, Voice,
 * Writing. The shape does not change — a `StateFlow` read by the UI, written to disk on change.
 */
class SettingsRepository(private val store: DataStore<Preferences>) {

    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val _theme = MutableStateFlow(OnyxTheme.STANDARD)
    val theme: StateFlow<OnyxTheme> = _theme.asStateFlow()

    /**
     * True once [load] has run: the values carried by the flows are then **the disk's values**,
     * not the defaults.
     *
     * This is what background work ([com.onyx.app.notifications.ReplyWatcher]) reads: started by
     * Android, it can query the repository before the startup load is finished — and a
     * disabled setting would then read as its default.
     */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _appearance = MutableStateFlow(AppearanceMode.SYSTEM)
    val appearance: StateFlow<AppearanceMode> = _appearance.asStateFlow()

    /**
     * "Pure black" (`Settings.pureBlack`): in **dark** appearance only, page backgrounds
     * switch to absolute black — gentler on an OLED screen. **False by default.**
     */
    private val _pureBlack = MutableStateFlow(false)
    val pureBlack: StateFlow<Boolean> = _pureBlack.asStateFlow()

    /** "Halo" (`Settings.halo`): the soft coloured glow behind the top of each screen. */
    private val _halo = MutableStateFlow(true)
    val halo: StateFlow<Boolean> = _halo.asStateFlow()

    /**
     * "Fluid": a conversation card's cover is the animated fluid (variant B), instead
     * of the deterministic image. **True by default.** When off, the card falls back to exactly
     * the old cover — nothing else changes.
     */
    private val _fluideCover = MutableStateFlow(true)
    val fluideCover: StateFlow<Boolean> = _fluideCover.asStateFlow()

    /**
     * **The fluid's material** — the choice the owner settled on the variants board:
     * **filaments, seed `3417634283`, blue hue**. Three **stored** values,
     * re-read at startup and set as-is on the card
     * ([com.onyx.app.ui.design.LocalFluideChoix]). Before them, only the variant was assumed:
     * the seed came from the conversation, so the hue landed on one of the house's six —
     * **green** for its conversations — and the owner's choice was nowhere.
     */
    private val _fluideChoix = MutableStateFlow(com.onyx.app.ui.design.ChoixCouverture.DEFAUT)
    val fluideChoix: StateFlow<com.onyx.app.ui.design.ChoixCouverture> = _fluideChoix.asStateFlow()

    /** Looks read by the Android Auto companion via AutoSettingsProvider. */
    private val _autoCardLook = MutableStateFlow(AutoCardLook.COLOR)
    val autoCardLook: StateFlow<AutoCardLook> = _autoCardLook.asStateFlow()

    private val _autoVoiceLook = MutableStateFlow(AutoVoiceLook.BARS)
    val autoVoiceLook: StateFlow<AutoVoiceLook> = _autoVoiceLook.asStateFlow()

    /** Mic and playback language; empty = the interface's. */
    private val _speechLocale = MutableStateFlow(DEFAULT_SPEECH_LOCALE)
    val speechLocale: StateFlow<String> = _speechLocale.asStateFlow()

    /**
     * The language **actually given to the engine**: the person's choice, otherwise the
     * interface's.
     *
     * The app was pinned to "fr-FR": an English app listened in French, and English phrases
     * were badly transcribed — so never recognised. The mic language therefore follows the
     * interface's, as long as the person has not chosen another.
     */
    private val _speechLocaleResolved = MutableStateFlow(resolveSpeechLocale(DEFAULT_SPEECH_LOCALE))
    val speechLocaleResolved: StateFlow<String> = _speechLocaleResolved.asStateFlow()

    /**
     * "Recognise offline" (`Settings.offlineRecognition`): **false by default**.
     *
     * Recognition is **online**, using the device engine. When enforced, offline was the
     * cause of `code=13 language-unavailable`: on a phone without an offline pack for the
     * language, the engine started then stopped by itself. This setting is the **only**
     * path to offline, and an offline engine that fails falls back online on its own.
     */
    private val _offlineRecognition = MutableStateFlow(false)
    val offlineRecognition: StateFlow<Boolean> = _offlineRecognition.asStateFlow()

    /** Does voice mode start listening again after each reply? */
    private val _handsFreeByDefault = MutableStateFlow(true)
    val handsFreeByDefault: StateFlow<Boolean> = _handsFreeByDefault.asStateFlow()

    /** Does the keyboard's Enter key send the message? */
    private val _returnSends = MutableStateFlow(true)
    val returnSends: StateFlow<Boolean> = _returnSends.asStateFlow()

    /** The spoken voice's speed (0.5 to 2.0, like the reference slider). */
    private val _voiceSpeed = MutableStateFlow(1.0f)
    val voiceSpeed: StateFlow<Float> = _voiceSpeed.asStateFlow()

    /** The chosen synthesis: Android, a Kokoro server, or the Dashboard's Hermes. */
    private val _replyVoice = MutableStateFlow(ReplyVoice.ON_DEVICE)
    val replyVoice: StateFlow<ReplyVoice> = _replyVoice.asStateFlow()

    /** Address of the Kokoro-compatible server, empty until it is configured. */
    private val _kokoroUrl = MutableStateFlow(DEFAULT_KOKORO_URL)
    val kokoroUrl: StateFlow<String> = _kokoroUrl.asStateFlow()

    /**
     * The voice service's token, when it is protected (token proxy). **Empty by default**:
     * without a token, the app sends no header and talks to an open local server as before. It
     * stays **out of the address** — it ends up in screenshots and logs — and travels
     * only in the `Authorization` header of `KokoroSpeechClient`.
     */
    private val _kokoroToken = MutableStateFlow(DEFAULT_KOKORO_TOKEN)
    val kokoroToken: StateFlow<String> = _kokoroToken.asStateFlow()

    /** Voice or blend (`am_onyx(2)+bm_george(1)`) requested from Kokoro. */
    private val _kokoroVoice = MutableStateFlow(DEFAULT_KOKORO_VOICE)
    val kokoroVoice: StateFlow<String> = _kokoroVoice.asStateFlow()

    /** The reply language requested from the model; empty = automatic (the question's). */
    private val _replyLanguage = MutableStateFlow("")
    val replyLanguage: StateFlow<String> = _replyLanguage.asStateFlow()

    /**
     * The interface language: `system` (Android's, the default), `fr` or `en`
     * (`OnyxLanguage`). The choice never touches `Locale.getDefault()`.
     */
    private val _language = MutableStateFlow(OnyxLanguage.SYSTEM)
    val language: StateFlow<String> = _language.asStateFlow()

    /** Request three follow-up questions after each reply ("Suggest follow-up questions"). */
    private val _suggestFollowUps = MutableStateFlow(false)
    val suggestFollowUps: StateFlow<Boolean> = _suggestFollowUps.asStateFlow()

    /** The spoken prefix rules, in the order they are tried. */
    private val _spokenPrefixes = MutableStateFlow<List<com.onyx.app.ui.settings.voice.SpokenPrefix>>(emptyList())
    val spokenPrefixes: StateFlow<List<com.onyx.app.ui.settings.voice.SpokenPrefix>> = _spokenPrefixes.asStateFlow()

    /** Personal ways to end a hands-free conversation. */
    private val _stopPhrases = MutableStateFlow<List<String>>(emptyList())
    val stopPhrases: StateFlow<List<String>> = _stopPhrases.asStateFlow()

    /** The reasoning effort requested (`Settings.reasoningEfforts`); empty = left to the server. */
    private val _reasoningEffort = MutableStateFlow("")
    val reasoningEffort: StateFlow<String> = _reasoningEffort.asStateFlow()

    /**
     * "Talk during replies" (`Settings.TalkOver`): never, with headphones only
     * (the reference default), or everywhere. It is this setting, confronted with the current route,
     * that decides whether the mic opens behind a reply — on a phone's loudspeaker, it
     * does not open, and the synthesis voice therefore cannot come back through the mic.
     */
    private val _talkOver = MutableStateFlow(TalkOver.DEFAULT)
    val talkOver: StateFlow<TalkOver> = _talkOver.asStateFlow()

    /**
     * "Earpiece when raised to your ear" (`Settings.earpieceAtEar`): phone raised to the
     * ear, the reply plays in the earpiece, like a call.
     */
    private val _earpieceAtEar = MutableStateFlow(true)
    val earpieceAtEar: StateFlow<Boolean> = _earpieceAtEar.asStateFlow()

    /** Approval alerts received while Onyx is in the background. */
    private val _notifyInBackground = MutableStateFlow(true)
    val notifyInBackground: StateFlow<Boolean> = _notifyInBackground.asStateFlow()

    /** Alert sound; Android uses two channels, with and without sound. */
    private val _notificationSound = MutableStateFlow(true)
    val notificationSound: StateFlow<Boolean> = _notificationSound.asStateFlow()

    /**
     * "Notify me when a reply arrives, app closed": the reply watcher
     * ([com.onyx.app.notifications.ReplyWatcher]) re-reads the tracked sessions' log every
     * fifteen minutes and publishes the reply that arrived in the meantime. **True by default**: without it, a
     * bot that finishes its turn while the phone is in a pocket never announces itself.
     */
    private val _replyWatch = MutableStateFlow(true)
    val replyWatch: StateFlow<Boolean> = _replyWatch.asStateFlow()

    /**
     * "Calls for a reply" (`phone_call_me` / `phoneRings` of the reference): an approval
     * or a question **rings like a call** — full screen on the lock screen — instead of a
     * plain notification. **False by default**: the user turns it on (Settings → Phone
     * → Calls). Without it, everything behaves as before.
     */
    private val _phoneRings = MutableStateFlow(false)
    val phoneRings: StateFlow<Boolean> = _phoneRings.asStateFlow()

    /**
     * Answering puts Hermes **online** (voice mode listens) instead of opening the conversation:
     * this is the reference's `phoneRingsTalk`, **true by default** — a call you answer is a
     * call where you talk.
     */
    private val _phoneRingsTalk = MutableStateFlow(true)
    val phoneRingsTalk: StateFlow<Boolean> = _phoneRingsTalk.asStateFlow()

    /** Quiet hours: nothing rings during the window ([quietFrom] → [quietUntil]). */
    private val _quietHours = MutableStateFlow(false)
    val quietHours: StateFlow<Boolean> = _quietHours.asStateFlow()

    /** The start of the quiet window, in minutes since midnight (22:00 by default). */
    private val _quietFrom = MutableStateFlow(QuietHours.DEFAULT_FROM)
    val quietFrom: StateFlow<Int> = _quietFrom.asStateFlow()

    /** The end of the quiet window, in minutes since midnight (07:00 by default). */
    private val _quietUntil = MutableStateFlow(QuietHours.DEFAULT_UNTIL)
    val quietUntil: StateFlow<Int> = _quietUntil.asStateFlow()

    /**
     * "Work tracking": what a bot announces about its work — never, only at the end
     * (the default), or at each step with the progress notification in the status bar.
     */
    private val _workTracking = MutableStateFlow(WorkTracking.DEFAULT)
    val workTracking: StateFlow<WorkTracking> = _workTracking.asStateFlow()

    /** What the header and the input area call the assistant; empty = "Onyx". */
    private val _displayName = MutableStateFlow("")
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    /**
     * The first-launch setup screen has been passed ("Skip" or "Done").
     *
     * This is the reference's `Settings.setupDone`. Without it, the screen reappeared on each
     * composition as long as no address was entered: the two buttons seemed dead and
     * the user stayed trapped in the form.
     */
    private val _setupDone = MutableStateFlow(false)
    val setupDone: StateFlow<Boolean> = _setupDone.asStateFlow()

    /** Colours chosen per theme, keys = theme identifiers (`Settings.themeColors`). */
    private val _themeColors = MutableStateFlow<Map<String, ThemeColors>>(emptyMap())
    val themeColors: StateFlow<Map<String, ThemeColors>> = _themeColors.asStateFlow()

    /** "Bot faces": when off, each bot is its initial on a disc of its colour. */
    private val _botFaces = MutableStateFlow(true)
    val botFaces: StateFlow<Boolean> = _botFaces.asStateFlow()

    /** The voice set for each bot, per server (`Settings.botVoices`). */
    private val _botVoices = MutableStateFlow<Map<String, Map<String, com.onyx.app.voice.BotVoice>>>(emptyMap())
    val botVoices: StateFlow<Map<String, Map<String, com.onyx.app.voice.BotVoice>>> = _botVoices.asStateFlow()

    /**
     * The voices set for bots **of this server** (the reference's `Settings.botVoicesHere`): this is what
     * playback consults to give each one its own.
     */
    fun botVoicesHere(serverId: String): Map<String, com.onyx.app.voice.BotVoice> =
        _botVoices.value[serverId].orEmpty()

    /**
     * Sets a bot's voice (the reference's `BotVoicePicker`); `null` clears its setting — the bot then
     * takes back a voice of its own (`BotVoices`). An empty entry is not stored.
     */
    suspend fun setBotVoice(serverId: String, profile: String, voice: com.onyx.app.voice.BotVoice?) {
        val here = _botVoices.value[serverId].orEmpty().toMutableMap()
        if (voice == null || voice.isEmpty) here.remove(profile) else here[profile] = voice
        val all = _botVoices.value.toMutableMap()
        if (here.isEmpty()) all.remove(serverId) else all[serverId] = here
        _botVoices.value = all
        store.edit { it[Keys.BOT_VOICES] = json.encodeToString(BOT_VOICES_SERIALIZER, all) }
    }

    /** "Pictures from Hermes": Hermes' profile picture replaces the face when it has one. */
    private val _botPictures = MutableStateFlow(true)
    val botPictures: StateFlow<Boolean> = _botPictures.asStateFlow()

    /** "Bot colors in their chats": a chat's bubbles and accent take the bot's colour. */
    private val _botColors = MutableStateFlow(true)
    val botColors: StateFlow<Boolean> = _botColors.asStateFlow()

    /**
     * "Link previews": a card with the page's title and image under a message that
     * contains a link (`LinkPreviews`). **Off by default**: the phone opens the page
     * itself, so the site sees the visit, whereas Onyx otherwise only talks to the servers it
     * was given.
     */
    private val _linkPreviews = MutableStateFlow(false)
    val linkPreviews: StateFlow<Boolean> = _linkPreviews.asStateFlow()

    /** "Chat text size": steps -2…2 around the system size (`Settings.chatTextSizes`). */
    private val _chatTextSize = MutableStateFlow(0)
    val chatTextSize: StateFlow<Int> = _chatTextSize.asStateFlow()

    /** The voice mode orb (`Settings.voiceOrb`). */
    private val _voiceOrb = MutableStateFlow(VoiceOrbChoice.ONYX)
    val voiceOrb: StateFlow<VoiceOrbChoice> = _voiceOrb.asStateFlow()

    /** What moves inside the orb face (`Settings.orbLight`). */
    private val _orbLight = MutableStateFlow(OrbLight.NEBULA)
    val orbLight: StateFlow<OrbLight> = _orbLight.asStateFlow()

    /**
     * Is the left pane of the two-panel layout collapsed? **Device** setting (like the
     * other interface settings: it lives in the local `DataStore`, not on the server): the
     * collapsible, once expanded, keeps the shape its owner gave it from one session to the next.
     */
    private val _sidebarCollapsed = MutableStateFlow(false)
    val sidebarCollapsed: StateFlow<Boolean> = _sidebarCollapsed.asStateFlow()

    /**
     * **"Halo"**: does the glass react to the finger? Specular light, press-in on tap,
     * illuminated outline — all three from a single switch.
     *
     * It is **the only** setting the "tactile glass" batch adds; the screen that displays it is carried
     * by another batch, which adds precisely "Halo". Here we therefore only set **the key and its
     * read**: when off, the application provides `LocalGlassHalo = false` and *everything* is flat, without
     * a single site having to worry about it. Default value **true**: the glass is alive as
     * in the reference reference.
     */
    private val _glassHalo = MutableStateFlow(true)
    val glassHalo: StateFlow<Boolean> = _glassHalo.asStateFlow()

    /**
     * The insights period (7, 30 or 90 days), **remembered**: reopening the screen finds the
     * period being viewed. Like [sidebarCollapsed], it is a device setting, written in
     * the local `DataStore` — it never goes to the server, and so does not follow the profile.
     */
    private val _insightsPeriod = MutableStateFlow(InsightsPeriod.DEFAULT)
    val insightsPeriod: StateFlow<InsightsPeriod> = _insightsPeriod.asStateFlow()

    /**
     * **The pane's shortcuts grid** — the identifiers of the displayed tiles, in the
     * person's order. It is a setting of **this device** (like the insights period and the pane's
     * collapse): it is written in the local `DataStore`, it never goes to the server.
     *
     * The default value is **the original grid** (`SidebarTileLayout.defaultIds`, the six tiles
     * of the reference): an absent, unreadable key, or one written by an earlier version, yields that
     * grid — never an empty grid, and **never** the invalidation of an already stored setting.
     */
    private val _sidebarTiles = MutableStateFlow(com.onyx.app.ui.sidebar.SidebarTileLayout.defaultIds)
    val sidebarTiles: StateFlow<List<String>> = _sidebarTiles.asStateFlow()

    /**
     * The app language requested by a setup link **or** by the Language setting: it is
     * the **same** setting (`Keys.LANGUAGE`, key `app_language`), carried by [language]/[setLanguage].
     * A link does not create a second setting, it writes it — hence the absence of a second flow here.
     */

    /** Reads what was stored; the default values are the reference app's. */
    suspend fun load() {
        val preferences = store.data.first()
        _theme.value = OnyxTheme.fromId(preferences[SettingsKeys.THEME])
        _appearance.value = AppearanceMode.fromId(preferences[SettingsKeys.APPEARANCE])
        _pureBlack.value = preferences[Keys.PURE_BLACK] ?: false
        _halo.value = preferences[Keys.HALO] ?: true
        _fluideCover.value = preferences[Keys.FLUIDE_COVER] ?: true
        // **What the owner stored, and nothing more.** The read carries the choice as
        // it was **written on gesture** ([setFluideChoix], called on the variants board);
        // **no key is seeded here** — startup never writes. If there is **no** choice
        // stored, the card takes the **visible** default ([ChoixCouverture.DEFAUT] — filaments,
        // seed `3417634283`, blue hue) **without writing it**; as soon as a choice exists, it is **IT**
        // that wins. Seeding at startup would mean silently overwriting the user's choice,
        // which the shipped lock (`AppearancePersistenceLockTest`) forbids.
        _fluideChoix.value = com.onyx.app.ui.design.ChoixCouverture.enregistre(
            variante = preferences[Keys.FLUIDE_VARIANTE],
            graine = preferences[Keys.FLUIDE_GRAINE],
            teinte = preferences[Keys.FLUIDE_TEINTE],
        )
        _autoCardLook.value = AutoCardLook.fromId(preferences[Keys.AUTO_CARDS])
        _autoVoiceLook.value = AutoVoiceLook.fromId(preferences[Keys.AUTO_VOICE])
        // The mic language: **an explicit choice** first, otherwise the interface's.
        //
        // The inheritance: before, "fr-FR" was the **default** value — and an entry of the picker
        // ("French"). A "fr-FR" from the old key is therefore ambiguous: treating it as a
        // choice would pin the mic to French on an English app; treating it as "nothing"
        // would delete a real choice of the person. The **new** key (`speech_locale_chosen`) lifts
        // the ambiguity: it is only written by a choice, and it is the one that counts.
        _speechLocale.value = preferences[Keys.SPEECH_LOCALE_CHOSEN]
            ?: preferences[Keys.SPEECH_LOCALE]?.takeUnless { it == LEGACY_FIXED_SPEECH_LOCALE }
            ?: DEFAULT_SPEECH_LOCALE
        _handsFreeByDefault.value = preferences[Keys.HANDS_FREE] ?: true
        // "Recognise offline": **false by default** — the only path to offline.
        _offlineRecognition.value = preferences[Keys.OFFLINE_RECOGNITION] ?: false
        _returnSends.value = preferences[Keys.RETURN_SENDS] ?: true
        _voiceSpeed.value = preferences[Keys.VOICE_SPEED] ?: 1.0f
        _replyVoice.value = ReplyVoice.fromId(preferences[Keys.REPLY_VOICE])
        _kokoroUrl.value = preferences[Keys.KOKORO_URL] ?: DEFAULT_KOKORO_URL
        // The voice service's token: the default value comes from the proxy file (0600),
        // injected at build time for the test build; otherwise empty, so no header.
        _kokoroToken.value = preferences[Keys.KOKORO_TOKEN] ?: DEFAULT_KOKORO_TOKEN
        _kokoroVoice.value = preferences[Keys.KOKORO_VOICE] ?: DEFAULT_KOKORO_VOICE
        _replyLanguage.value = preferences[Keys.REPLY_LANGUAGE] ?: ""
        // The interface language: re-read and **re-applied** here, so that a reset of the
        // settings also brings the app back to Android's language.
        _language.value = OnyxLanguage.normalize(preferences[Keys.LANGUAGE])
        OnyxLanguage.apply(_language.value)
        // The mic language resolves **after** the interface's: when nothing is chosen,
        // it follows the language.
        _speechLocaleResolved.value = resolveSpeechLocale(_speechLocale.value)
        // The stored mode; failing that the old boolean (true meant "everywhere"), failing that the
        // reference default.
        _talkOver.value = preferences[Keys.TALK_OVER_MODE]?.let(TalkOver::fromId)
            ?: preferences[Keys.TALK_OVER]?.let(TalkOver::fromLegacy)
            ?: TalkOver.DEFAULT
        _earpieceAtEar.value = preferences[Keys.EARPIECE_AT_EAR] ?: true
        _suggestFollowUps.value = preferences[Keys.SUGGEST_FOLLOW_UPS] ?: false
        _reasoningEffort.value = preferences[Keys.REASONING_EFFORT] ?: ""
        // **True by default**: a conversation app must speak in the background. The setting
        // was false, which left a fresh install mute — the work continued at
        // the host, but nobody was notified.
        _notifyInBackground.value = preferences[Keys.NOTIFY_IN_BACKGROUND] ?: true
        _notificationSound.value = preferences[Keys.NOTIFICATION_SOUND] ?: true
        // "Notify me when a reply arrives, app closed": true by default, cf. `_replyWatch`.
        _replyWatch.value = preferences[Keys.REPLY_WATCH] ?: true
        // "Calls for a reply" and quiet hours: the four keys of the call batch.
        _phoneRings.value = preferences[Keys.PHONE_RINGS] ?: false
        _phoneRingsTalk.value = preferences[Keys.PHONE_RINGS_TALK] ?: true
        _quietHours.value = preferences[Keys.QUIET_HOURS] ?: false
        _quietFrom.value = (preferences[Keys.QUIET_FROM] ?: QuietHours.DEFAULT_FROM)
            .coerceIn(0, QuietHours.MINUTES_A_DAY - 1)
        _quietUntil.value = (preferences[Keys.QUIET_UNTIL] ?: QuietHours.DEFAULT_UNTIL)
            .coerceIn(0, QuietHours.MINUTES_A_DAY - 1)
        // The three-choice setting; failing that the old boolean (true meant "follow the
        // turn", false "follow nothing"), failing that the default.
        _workTracking.value = preferences[Keys.WORK_TRACKING]?.let(WorkTracking::fromId)
            ?: preferences[Keys.SHOW_LIVE_ACTIVITY]?.let { live -> if (live) WorkTracking.DEFAULT else WorkTracking.NEVER }
            ?: WorkTracking.DEFAULT
        // The default name changed with the rename: an "Onyx" still stored is
        // the old default value, not a choice — it becomes empty again, hence "Onyx".
        _displayName.value = (preferences[Keys.DISPLAY_NAME] ?: "")
            .takeUnless { it.trim().equals("Onyx", ignoreCase = true) }
            .orEmpty()
        _botFaces.value = preferences[Keys.BOT_FACES] ?: true
        _botPictures.value = preferences[Keys.BOT_PICTURES] ?: true
        _botColors.value = preferences[Keys.BOT_COLORS] ?: true
        _linkPreviews.value = preferences[Keys.LINK_PREVIEWS] ?: false
        // Has the first-launch setup screen already been passed?
        _setupDone.value = preferences[Keys.SETUP_DONE] ?: false
        _chatTextSize.value = (preferences[Keys.CHAT_TEXT_SIZE] ?: 0)
            .coerceIn(ResolvedTheme.chatTextSizes.first(), ResolvedTheme.chatTextSizes.last())
        _voiceOrb.value = VoiceOrbChoice.fromId(preferences[Keys.VOICE_ORB])
        _orbLight.value = OrbLight.fromId(preferences[Keys.ORB_LIGHT])
        _sidebarCollapsed.value = preferences[Keys.SIDEBAR_COLLAPSED] ?: false
        // "Halo": does the glass react to the finger? True by default, as in the reference.
        _glassHalo.value = preferences[Keys.GLASS_HALO] ?: true
        _insightsPeriod.value = InsightsPeriod.fromDays(preferences[SettingsKeys.INSIGHTS_PERIOD])
        // The shortcuts grid: re-read **as it was stored** (order included). What the
        // file did not yet carry — or an earlier version did not know — yields the
        // original grid, never an amputated grid (see `SidebarTileLayout.parse`).
        _sidebarTiles.value = com.onyx.app.ui.sidebar.SidebarTileLayout
            .parse(preferences[SettingsKeys.SIDEBAR_TILES])
        _themeColors.value = runCatching {
            json.decodeFromString(THEME_COLORS_SERIALIZER, preferences[Keys.THEME_COLORS].orEmpty())
        }.getOrDefault(emptyMap())
        // The voices set for bots, per server then per profile (`Settings.botVoices`).
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
        // The disk has been fully read: the flows now carry the truth, not the defaults.
        _loaded.value = true
    }

    suspend fun setTheme(theme: OnyxTheme) {
        _theme.value = theme
        store.edit { it[SettingsKeys.THEME] = theme.id }
    }

    suspend fun setAppearance(appearance: AppearanceMode) {
        _appearance.value = appearance
        store.edit { it[SettingsKeys.APPEARANCE] = appearance.id }
    }

    /** "Pure black": page backgrounds at absolute black in dark appearance (OLED). */
    suspend fun setPureBlack(value: Boolean) {
        _pureBlack.value = value
        store.edit { it[Keys.PURE_BLACK] = value }
    }

    /** "Halo": the soft coloured glow behind the top of each screen. */
    suspend fun setHalo(value: Boolean) {
        _halo.value = value
        store.edit { it[Keys.HALO] = value }
    }

    /**
     * "Fluid": the animated conversation cover (variant B), or the old image.
     *
     * **This is where, and only here, the CHOICE is written**: enabling the fluid cover means
     * **touching the filaments variant on the board** — at that instant, the user's gesture
     * stores the choice (variant, seed, hue) via [setFluideChoix]. **Startup seeds
     * nothing** (`load()` only re-reads): otherwise it would overwrite, on each launch, what
     * the user had chosen — which the shipped lock (`AppearancePersistenceLockTest`) refuses.
     */
    suspend fun setFluideCover(value: Boolean) {
        _fluideCover.value = value
        store.edit { it[Keys.FLUIDE_COVER] = value }
        if (value) setFluideChoix(_fluideChoix.value)
    }

    /**
     * **Stores the fluid's material** — the three values of the choice (variant, seed, hue).
     * They are written together: a half-written choice would draw a hue that the seed
     * does not give. This is what the conformance lock refuses.
     */
    suspend fun setFluideChoix(value: com.onyx.app.ui.design.ChoixCouverture) {
        _fluideChoix.value = value
        store.edit {
            it[Keys.FLUIDE_VARIANTE] = value.variante
            it[Keys.FLUIDE_GRAINE] = value.graine
            it[Keys.FLUIDE_TEINTE] = value.teinte
        }
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
        _speechLocale.value = clean
        _speechLocaleResolved.value = resolveSpeechLocale(clean)
        // The choice goes into the **new** key: it is it, and only it, that distinguishes an explicit
        // choice from an inherited value. The old key stays as is — writing it too
        // would make the "fr-FR" chosen by the person indistinguishable from the default "fr-FR".
        store.edit { it[Keys.SPEECH_LOCALE_CHOSEN] = clean }
    }

    /**
     * The language given to the engine: the person's choice, otherwise the interface's.
     *
     * An English app listens in English — otherwise English phrases are badly transcribed,
     * so never recognised.
     */
    private fun resolveSpeechLocale(raw: String): String =
        raw.trim().ifEmpty { defaultSpeechLocale() }

    suspend fun setHandsFreeByDefault(value: Boolean) {
        _handsFreeByDefault.value = value
        store.edit { it[Keys.HANDS_FREE] = value }
    }

    /**
     * "Recognise offline": the **only** path to offline. Off (the default),
     * recognition is online; on, the engine tries the device alone and falls back online
     * on its own if it lacks the language.
     */
    suspend fun setOfflineRecognition(value: Boolean) {
        _offlineRecognition.value = value
        store.edit { it[Keys.OFFLINE_RECOGNITION] = value }
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

    /** The protected voice service's token; empty clears the header and returns to the open server. */
    suspend fun setKokoroToken(value: String) {
        _kokoroToken.value = value
        store.edit { it[Keys.KOKORO_TOKEN] = value }
    }

    suspend fun setKokoroVoice(value: String) {
        _kokoroVoice.value = value
        store.edit { it[Keys.KOKORO_VOICE] = value }
    }

    suspend fun setReplyLanguage(code: String) {
        _replyLanguage.value = code.trim()
        store.edit { it[Keys.REPLY_LANGUAGE] = _replyLanguage.value }
    }

    /**
     * The chosen language, set **before** the first composition (`OnyxApp` re-reads it at
     * startup): the first screen is already in the right language, with no flicker in Android's.
     * The rest of the settings, however, may arrive a little later.
     */
    suspend fun applyStoredLanguage() {
        val stored = OnyxLanguage.normalize(store.data.first()[Keys.LANGUAGE])
        _language.value = stored
        OnyxLanguage.apply(stored)
    }

    /**
     * The stored theme and appearance, set **before** the first composition — same contract
     * as [applyStoredLanguage], and for the same reason.
     *
     * Without this call, `_theme` and `_appearance` stay on their defaults ([OnyxTheme.STANDARD] and
     * [AppearanceMode.SYSTEM]) until [load] has finished reading the disk. Yet [load] is
     * started **after** `Application.onCreate` (`AppContainer.install`), so in competition with
     * the composition of `OnyxApp`: the first frame (and every frame until the end of the read)
     * is rendered in **SYSTEM**. On a light phone, that means **light** — even though
     * the user chose "Dark" and the store carries `appearance=dark`.
     *
     * It is the only read that must precede the first frame; **no write** takes place here,
     * the user's choice is never touched at startup.
     */
    suspend fun applyStoredTheme() {
        val stored = store.data.first()
        _theme.value = OnyxTheme.fromId(stored[SettingsKeys.THEME])
        _appearance.value = AppearanceMode.fromId(stored[SettingsKeys.APPEARANCE])
    }

    /**
     * Changes the interface language.
     *
     * The effect is **immediate in memory** (`OnyxLanguage.apply`, before the first possible
     * suspension): the caller can recreate the activity right away, the screen is already redrawn
     * in the new language. The write to disk follows.
     */
    suspend fun setLanguage(value: String) {
        val clean = OnyxLanguage.normalize(value)
        _language.value = clean
        OnyxLanguage.apply(clean)
        // The mic language follows the interface's — unless the person has chosen one.
        _speechLocaleResolved.value = resolveSpeechLocale(_speechLocale.value)
        store.edit { it[Keys.LANGUAGE] = clean }
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
            // The old boolean stays written: an earlier version of the app re-reads the same file.
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

    /** "Notify me when a reply arrives, app closed": the reply watcher. */
    suspend fun setReplyWatch(value: Boolean) {
        _replyWatch.value = value
        store.edit { it[Keys.REPLY_WATCH] = value }
    }

    /** "Calls for a reply": make the phone ring for an approval or a question. */
    suspend fun setPhoneRings(value: Boolean) {
        _phoneRings.value = value
        store.edit { it[Keys.PHONE_RINGS] = value }
    }

    /** Answering puts Hermes online (live voice) rather than opening the conversation. */
    suspend fun setPhoneRingsTalk(value: Boolean) {
        _phoneRingsTalk.value = value
        store.edit { it[Keys.PHONE_RINGS_TALK] = value }
    }

    /** Quiet hours: nothing rings during the window. */
    suspend fun setQuietHours(value: Boolean) {
        _quietHours.value = value
        store.edit { it[Keys.QUIET_HOURS] = value }
    }

    /** The start of the quiet window, in minutes since midnight. */
    suspend fun setQuietFrom(minute: Int) {
        val clean = minute.coerceIn(0, QuietHours.MINUTES_A_DAY - 1)
        _quietFrom.value = clean
        store.edit { it[Keys.QUIET_FROM] = clean }
    }

    /** The end of the quiet window, in minutes since midnight. */
    suspend fun setQuietUntil(minute: Int) {
        val clean = minute.coerceIn(0, QuietHours.MINUTES_A_DAY - 1)
        _quietUntil.value = clean
        store.edit { it[Keys.QUIET_UNTIL] = clean }
    }

    /**
     * Is nothing ringing now? Quiet hours on **and** the instant inside them —
     * it is the only question the approval call and the agent's call ask.
     */
    fun quietNow(millis: Long = System.currentTimeMillis()): Boolean =
        QuietHours.now(QuietHours.minuteOfDay(millis), _quietHours.value, _quietFrom.value, _quietUntil.value)

    suspend fun setShowLiveActivity(value: Boolean) {
        _workTracking.value = if (value) WorkTracking.DEFAULT else WorkTracking.NEVER
        store.edit { it[Keys.WORK_TRACKING] = _workTracking.value.id }
    }

    /** Stores work tracking — three choices, only one kept. */
    suspend fun setWorkTracking(value: WorkTracking) {
        _workTracking.value = value
        store.edit { it[Keys.WORK_TRACKING] = value.id }
    }

    suspend fun setDisplayName(value: String) {
        _displayName.value = value
        store.edit { it[Keys.DISPLAY_NAME] = value }
    }

    suspend fun setSetupDone(value: Boolean) {
        _setupDone.value = value
        store.edit { it[Keys.SETUP_DONE] = value }
    }

    /** Stores the colours per theme; a removed entry falls back to the theme's colours. */
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

    /** Collapses or expands the left pane of the two-panel layout, on this device. */
    suspend fun setSidebarCollapsed(value: Boolean) {
        _sidebarCollapsed.value = value
        store.edit { it[Keys.SIDEBAR_COLLAPSED] = value }
    }

    /** "Halo": does the glass react to the finger? Off, everything is flat (see [glassHalo]). */
    suspend fun setGlassHalo(value: Boolean) {
        _glassHalo.value = value
        store.edit { it[Keys.GLASS_HALO] = value }
    }

    /**
     * Remembers the insights period. Written **immediately** (not when the screen closes):
     * a device killed while viewing the period finds it again on the next launch.
     */
    suspend fun setInsightsPeriod(period: InsightsPeriod) {
        _insightsPeriod.value = period
        store.edit { it[SettingsKeys.INSIGHTS_PERIOD] = period.days }
    }

    /**
     * **Remembers the pane's shortcuts grid**: the displayed tiles, in their order.
     *
     * Written **immediately** (a device killed in the middle of composition finds the grid it
     * was showing), and always **brought back within its bounds** before being written — at most twelve, at least
     * four — so that the file cannot carry a grid that the screen would refuse to draw.
     */
    suspend fun setSidebarTiles(ids: List<String>) {
        val clean = com.onyx.app.ui.sidebar.SidebarTileLayout.normalize(ids)
        _sidebarTiles.value = clean
        store.edit {
            it[SettingsKeys.SIDEBAR_TILES] = com.onyx.app.ui.sidebar.SidebarTileLayout.encode(clean)
        }
    }

    /** Resets the shortcuts grid to **the original grid** (the reference's six tiles). */
    suspend fun resetSidebarTiles() {
        setSidebarTiles(com.onyx.app.ui.sidebar.SidebarTileLayout.defaultIds)
    }

    /**
     * Resets **all** settings to their original values, like the reference's `Settings.shared.reset()`:
     * the preferences are cleared, then re-read — what comes back are the defaults of `load()`.
     */
    suspend fun reset() {
        store.edit { it.clear() }
        load()
    }

    private object Keys {
        val AUTO_CARDS = stringPreferencesKey("carPlayCards")
        val AUTO_VOICE = stringPreferencesKey("carPlayVoice")
        val SPEECH_LOCALE = stringPreferencesKey("speech_locale")
        /**
         * The key **after**: the one that carries an **explicit** choice of the person.
         *
         * The inheritance is ambiguous: before, "fr-FR" was the **default** value *and* an entry
         * of the picker ("French"). A stored "fr-FR" could therefore be either, and
         * overwriting it at random would have deleted a real choice. A new key, written only when
         * the person chooses, lifts the ambiguity: the "fr-FR" of the old key is an inherited
         * value, the "fr-FR" of the new one is a choice.
         */
        val SPEECH_LOCALE_CHOSEN = stringPreferencesKey("speech_locale_chosen")
        val HANDS_FREE = booleanPreferencesKey("hands_free_by_default")
        /**
         * "Recognise offline". New key, **false by default**: a device that has never
         * set anything recognises online (before, offline was enforced on everyone).
         */
        val OFFLINE_RECOGNITION = booleanPreferencesKey("offline_recognition")
        val RETURN_SENDS = booleanPreferencesKey("return_sends")
        val VOICE_SPEED = floatPreferencesKey("voice_speed")
        val REPLY_VOICE = stringPreferencesKey("replyVoice")
        val KOKORO_URL = stringPreferencesKey("kokoroURL")
        val KOKORO_VOICE = stringPreferencesKey("kokoroVoice")
        /** The protected voice service's token; it never goes into `kokoroURL`. */
        val KOKORO_TOKEN = stringPreferencesKey("kokoroToken")
        val REPLY_LANGUAGE = stringPreferencesKey("reply_language")
        /** The interface language (`OnyxLanguage`): a setting specific to this app. */
        val LANGUAGE = stringPreferencesKey("app_language")
        val TALK_OVER = booleanPreferencesKey("talk_over")
        /** The reference's `Settings.talkOver` is a three-state choice, not a boolean. */
        val TALK_OVER_MODE = stringPreferencesKey("talk_over_mode")
        /** The reference key, word for word: `Settings.Keys.earpieceAtEar`. */
        val EARPIECE_AT_EAR = booleanPreferencesKey("earpieceAtEar")
        val SUGGEST_FOLLOW_UPS = booleanPreferencesKey("suggest_follow_ups")
        val REASONING_EFFORT = stringPreferencesKey("reasoning_effort")
        val NOTIFY_IN_BACKGROUND = booleanPreferencesKey("notify_in_background")
        val NOTIFICATION_SOUND = booleanPreferencesKey("notification_sound")
        /**
         * "Notify me when a reply arrives, app closed": the reply watcher's key.
         * New and **true by default** — an app that has never set anything watches its sessions.
         */
        val REPLY_WATCH = booleanPreferencesKey("reply_watch")
        /** "Calls for a reply" and its suite: the keys of the call batch. */
        val PHONE_RINGS = booleanPreferencesKey("phoneRings")
        val PHONE_RINGS_TALK = booleanPreferencesKey("phoneRingsTalk")
        val QUIET_HOURS = booleanPreferencesKey("quietHours")
        val QUIET_FROM = intPreferencesKey("quietFrom")
        val QUIET_UNTIL = intPreferencesKey("quietUntil")
        /** The old boolean, kept for recovery: a true meant "follow the turn". */
        val SHOW_LIVE_ACTIVITY = booleanPreferencesKey("show_live_activity")
        /** "Work tracking": `never`, `finish` or `step`. */
        val WORK_TRACKING = stringPreferencesKey("work_tracking")
        val STOP_PHRASES = stringPreferencesKey("stop_phrases")
        val SPOKEN_PREFIXES = stringPreferencesKey("spoken_prefixes")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        /** The reference key, word for word: `Settings.Keys.setupDone`. */
        val SETUP_DONE = booleanPreferencesKey("setupDone")
        val THEME_COLORS = stringPreferencesKey("themeColors")
        // The keys carry the same names as the reference's `Settings.Keys`.
        val BOT_FACES = booleanPreferencesKey("botFaces")
        val BOT_PICTURES = booleanPreferencesKey("botPictures")
        val BOT_COLORS = booleanPreferencesKey("botColors")
        /** The reference key, word for word: `Settings.Keys.botVoices`. */
        val BOT_VOICES = stringPreferencesKey("botVoices")
        // The reference key, word for word: `Settings.Keys.linkPreviews`.
        val LINK_PREVIEWS = booleanPreferencesKey("linkPreviews")
        val CHAT_TEXT_SIZE = intPreferencesKey("chatTextSize")
        val VOICE_ORB = stringPreferencesKey("voiceOrb")
        val ORB_LIGHT = stringPreferencesKey("orbLight")
        /** The left pane of the two-panel layout, collapsed or not (device setting). */
        val SIDEBAR_COLLAPSED = booleanPreferencesKey("sidebarCollapsed")
        /** "Pure black": backgrounds at absolute black in dark mode (OLED). Same name as `Settings.Keys`. */
        val PURE_BLACK = booleanPreferencesKey("pureBlack")
        /** "Halo": the soft glow behind the top of each screen. Same name as `Settings.Keys`. */
        val HALO = booleanPreferencesKey("halo")
        /** "Fluid": the animated conversation cover. True by default. */
        val FLUIDE_COVER = booleanPreferencesKey("fluideCover")
        /**
         * **The fluid's material**: the chosen variant, the stroke's seed and the named hue.
         * Written together by `setFluideChoix`; re-read by `load()`. It is these three keys that
         * carry the **owner's choice** (filaments, seed `3417634283`, blue hue) from one
         * startup to the next — without them, the card reinvented the hue on each draw.
         */
        val FLUIDE_VARIANTE = stringPreferencesKey("fluideVariante")
        val FLUIDE_GRAINE = longPreferencesKey("fluideGraine")
        val FLUIDE_TEINTE = stringPreferencesKey("fluideTeinte")
        /**
         * "Halo": does the glass react to the finger? Key specific to the app (there is no matching
         * entry in the reference's `Settings.Keys` — liquid glass there is always alive),
         * **true by default**.
         */
        val GLASS_HALO = booleanPreferencesKey("glassHalo")
    }

    private val THEME_COLORS_SERIALIZER = MapSerializer(String.serializer(), ThemeColors.serializer())

    /** `Settings.botVoices`: per server, then per bot profile. */
    private val BOT_VOICES_SERIALIZER: kotlinx.serialization.KSerializer<Map<String, Map<String, com.onyx.app.voice.BotVoice>>> =
        MapSerializer(String.serializer(), MapSerializer(String.serializer(), com.onyx.app.voice.BotVoice.serializer()))

    companion object {
        const val DEFAULT_KOKORO_URL = ""
        const val DEFAULT_KOKORO_VOICE = "am_onyx(2)+bm_george(1)"

        /**
         * The voice service's token for the **test build**: it comes from the proxy's `0600`
         * file, read at build time (see `app/build.gradle.kts`) — never from the repository, never
         * displayed. Absent, it stays empty: the app then talks to an open server, as before.
         */
        val DEFAULT_KOKORO_TOKEN: String = com.onyx.app.BuildConfig.VOICE_TOKEN

        /** The app's languages: the first empty entry means "System". */
        val APP_LANGUAGES = listOf(
            "" to "System",
            "fr" to "Français",
            "en" to "English",
        )

        /**
         * The locale to set for a language code; `null` means **System** (the phone's is not
         * touched). Pure, so testable without Android.
         */
        fun localeFor(code: String): java.util.Locale? {
            val clean = code.trim().lowercase().substringBefore('-').take(5)
            if (clean.isEmpty()) return null
            return runCatching { java.util.Locale.forLanguageTag(clean) }
                .getOrNull()?.takeIf { it.language.isNotEmpty() }
        }

        /** The seven effort levels, in the reference's order; `""` = "Default". */
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

        /**
         * The recognition language when the person has not chosen one: **the interface's**.
         * Empty means "automatic" — the app was pinned to "fr-FR", and an
         * English app listened in French.
         */
        const val DEFAULT_SPEECH_LOCALE = ""

        /**
         * The **pinned** value from before: installs from before stored "fr-FR" without it
         * being a choice. It is brought back to "automatic" on load (see `load()`).
         */
        const val LEGACY_FIXED_SPEECH_LOCALE = "fr-FR"

        /** The default recognition language: the interface's. */
        fun defaultSpeechLocale(): String =
            if (com.onyx.app.localization.OnyxLocalization.isFrench()) "fr-FR" else "en-US"

        /** The offered reply languages; the first empty entry means "automatic". */
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

        /** The sentence added to the instructions, word for word as on the reference side. */
        fun replyInstruction(code: String): String? {
            val name = REPLY_LANGUAGES.firstOrNull { it.first == code }?.second ?: return null
            return "Always reply in $name, whatever language the user writes or speaks in, " +
                "unless they ask for another language."
        }

        /**
         * The **playback** language of a chosen reply language: the short code ("fr", "en")
         * becomes the tag the reader expects ("fr-FR", "en-US").
         *
         * `null` for "automatic": the voice then follows the mic language, hence the
         * interface's. This is what makes playback consistent with what is said.
         */
        fun replyLanguageTag(code: String): String? = when (code) {
            "fr" -> "fr-FR"
            "en" -> "en-US"
            "de" -> "de-DE"
            "es" -> "es-ES"
            "it" -> "it-IT"
            "pt" -> "pt-BR"
            "nl" -> "nl-NL"
            else -> null
        }

        /** The offered languages, the ones the embedded engines know best. */
        val SPEECH_LOCALES = listOf(
            "" to "Automatic",
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
