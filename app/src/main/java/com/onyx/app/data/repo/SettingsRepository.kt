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
 * Les préférences de l'app : thème, apparence, voix et écriture.
 *
 * Miroir de `Settings.shared` côté référence, réduit à ce que les étapes ont porté : Apparence, Voix,
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

    /**
     * « Noir pur » (`Settings.pureBlack`) : en apparence **sombre** seulement, les fonds de page
     * passent au noir absolu — plus doux sur un écran OLED. **Faux par défaut.**
     */
    private val _pureBlack = MutableStateFlow(false)
    val pureBlack: StateFlow<Boolean> = _pureBlack.asStateFlow()

    /** « Halo » (`Settings.halo`) : la lumière colorée douce derrière le haut de chaque écran. */
    private val _halo = MutableStateFlow(true)
    val halo: StateFlow<Boolean> = _halo.asStateFlow()

    /**
     * « Fluide » : la couverture d'une carte de conversation est le fluide animé (variante B), au
     * lieu de l'image déterministe. **Vrai par défaut.** Éteint, la carte reprend exactement
     * l'ancienne couverture — rien d'autre ne change.
     */
    private val _fluideCover = MutableStateFlow(true)
    val fluideCover: StateFlow<Boolean> = _fluideCover.asStateFlow()

    /**
     * **Le matériau du fluide** — le choix que le propriétaire a tranché sur la planche des
     * variantes : **filaments, graine `3417634283`, teinte bleu**. Trois valeurs **enregistrées**,
     * relues au démarrage et posées telles quelles dans la carte
     * ([com.onyx.app.ui.design.LocalFluideChoix]). Avant elles, seule la variante était supposée :
     * la graine venait de la conversation, donc la teinte tombait sur l'une des six de la maison —
     * **verte** pour ses conversations — et le choix du propriétaire n'était nulle part.
     */
    private val _fluideChoix = MutableStateFlow(com.onyx.app.ui.design.ChoixCouverture.DEFAUT)
    val fluideChoix: StateFlow<com.onyx.app.ui.design.ChoixCouverture> = _fluideChoix.asStateFlow()

    /** Habillages lus par le compagnon Android Auto via AutoSettingsProvider. */
    private val _autoCardLook = MutableStateFlow(AutoCardLook.COLOR)
    val autoCardLook: StateFlow<AutoCardLook> = _autoCardLook.asStateFlow()

    private val _autoVoiceLook = MutableStateFlow(AutoVoiceLook.BARS)
    val autoVoiceLook: StateFlow<AutoVoiceLook> = _autoVoiceLook.asStateFlow()

    /** La langue du micro et de la lecture ; vide = celle du téléphone. */
    private val _speechLocale = MutableStateFlow(DEFAULT_SPEECH_LOCALE)
    val speechLocale: StateFlow<String> = _speechLocale.asStateFlow()

    /**
     * « Reconnaître hors ligne » (`Settings.offlineRecognition`) : **faux par défaut**.
     *
     * La reconnaissance se fait **en ligne**, avec le moteur de l'appareil. Imposé, le hors ligne
     * était la cause du `code=13 language-unavailable` : sur un téléphone sans pack hors ligne pour
     * la langue, le moteur démarrait puis s'arrêtait tout seul. Ce réglage-ci est le **seul**
     * chemin vers le hors ligne, et un moteur hors ligne qui échoue bascule en ligne de lui-même.
     */
    private val _offlineRecognition = MutableStateFlow(false)
    val offlineRecognition: StateFlow<Boolean> = _offlineRecognition.asStateFlow()

    /** Le mode vocal se remet-il à écouter après chaque réponse ? */
    private val _handsFreeByDefault = MutableStateFlow(true)
    val handsFreeByDefault: StateFlow<Boolean> = _handsFreeByDefault.asStateFlow()

    /** La touche Entrée du clavier envoie-t-elle le message ? */
    private val _returnSends = MutableStateFlow(true)
    val returnSends: StateFlow<Boolean> = _returnSends.asStateFlow()

    /** La vitesse de la voix lue (0.5 à 2.0, comme le curseur de la référence). */
    private val _voiceSpeed = MutableStateFlow(1.0f)
    val voiceSpeed: StateFlow<Float> = _voiceSpeed.asStateFlow()

    /** La synthèse choisie : Android, un serveur Kokoro, ou le Hermes du Dashboard. */
    private val _replyVoice = MutableStateFlow(ReplyVoice.ON_DEVICE)
    val replyVoice: StateFlow<ReplyVoice> = _replyVoice.asStateFlow()

    /** Adresse du serveur Kokoro-compatible, vide tant qu'il n'est pas configuré. */
    private val _kokoroUrl = MutableStateFlow(DEFAULT_KOKORO_URL)
    val kokoroUrl: StateFlow<String> = _kokoroUrl.asStateFlow()

    /**
     * Le jeton du service vocal, quand il est protégé (mandataire à jeton). **Vide par défaut** :
     * sans jeton, l'app n'envoie aucun en-tête et parle à un serveur local ouvert comme avant. Il
     * reste **hors de l'adresse** — il finit dans les copies d'écran et les journaux — et ne voyage
     * que dans l'en-tête `Authorization` de `KokoroSpeechClient`.
     */
    private val _kokoroToken = MutableStateFlow(DEFAULT_KOKORO_TOKEN)
    val kokoroToken: StateFlow<String> = _kokoroToken.asStateFlow()

    /** Voix ou mélange (`am_onyx(2)+bm_george(1)`) demandé à Kokoro. */
    private val _kokoroVoice = MutableStateFlow(DEFAULT_KOKORO_VOICE)
    val kokoroVoice: StateFlow<String> = _kokoroVoice.asStateFlow()

    /** La langue des réponses demandée au modèle ; vide = automatique (celle de la question). */
    private val _replyLanguage = MutableStateFlow("")
    val replyLanguage: StateFlow<String> = _replyLanguage.asStateFlow()

    /**
     * La langue de l'interface : `system` (celle d'Android, le défaut), `fr` ou `en`
     * (`OnyxLanguage`). Le choix ne touche jamais `Locale.getDefault()`.
     */
    private val _language = MutableStateFlow(OnyxLanguage.SYSTEM)
    val language: StateFlow<String> = _language.asStateFlow()

    /** Demande trois questions de relance après chaque réponse (« Suggest follow-up questions »). */
    private val _suggestFollowUps = MutableStateFlow(false)
    val suggestFollowUps: StateFlow<Boolean> = _suggestFollowUps.asStateFlow()

    /** Les règles de préfixe parlé, dans l'ordre où elles s'essaient. */
    private val _spokenPrefixes = MutableStateFlow<List<com.onyx.app.ui.settings.voice.SpokenPrefix>>(emptyList())
    val spokenPrefixes: StateFlow<List<com.onyx.app.ui.settings.voice.SpokenPrefix>> = _spokenPrefixes.asStateFlow()

    /** Les façons personnelles de terminer une conversation en mains-libres. */
    private val _stopPhrases = MutableStateFlow<List<String>>(emptyList())
    val stopPhrases: StateFlow<List<String>> = _stopPhrases.asStateFlow()

    /** L'effort de raisonnement demandé (`Settings.reasoningEfforts`) ; vide = laissé au serveur. */
    private val _reasoningEffort = MutableStateFlow("")
    val reasoningEffort: StateFlow<String> = _reasoningEffort.asStateFlow()

    /**
     * « Parler pendant les réponses » (`Settings.TalkOver`) : jamais, avec un casque seulement
     * (le défaut de la référence), ou partout. C'est ce réglage, confronté à la route du moment,
     * qui décide si le micro s'ouvre derrière une réponse — sur le haut-parleur d'un téléphone, il
     * ne s'ouvre pas, et la voix de synthèse ne peut donc pas revenir par le micro.
     */
    private val _talkOver = MutableStateFlow(TalkOver.DEFAULT)
    val talkOver: StateFlow<TalkOver> = _talkOver.asStateFlow()

    /**
     * « Earpiece when raised to your ear » (`Settings.earpieceAtEar`) : téléphone levé à
     * l'oreille, la réponse se joue dans l'écouteur, comme un appel.
     */
    private val _earpieceAtEar = MutableStateFlow(true)
    val earpieceAtEar: StateFlow<Boolean> = _earpieceAtEar.asStateFlow()

    /** Alertes d'approbation reçues pendant que Onyx est en arrière-plan. */
    private val _notifyInBackground = MutableStateFlow(false)
    val notifyInBackground: StateFlow<Boolean> = _notifyInBackground.asStateFlow()

    /** Son des alertes ; Android utilise deux canaux, avec et sans son. */
    private val _notificationSound = MutableStateFlow(true)
    val notificationSound: StateFlow<Boolean> = _notificationSound.asStateFlow()

    /**
     * « Appels pour une réponse » (`phone_call_me` / `phoneRings` de la référence) : une approbation
     * ou une question **sonne comme un appel** — plein écran sur l'écran verrouillé — au lieu d'une
     * simple notification. **Faux par défaut** : c'est l'utilisateur qui l'allume (Réglages → Phone
     * → Calls). Sans lui, tout se comporte comme avant.
     */
    private val _phoneRings = MutableStateFlow(false)
    val phoneRings: StateFlow<Boolean> = _phoneRings.asStateFlow()

    /**
     * Décrocher met Hermes **en ligne** (le mode vocal écoute) au lieu d'ouvrir la conversation :
     * c'est le `phoneRingsTalk` de la référence, **vrai par défaut** — un appel qu'on décroche est un
     * appel où l'on parle.
     */
    private val _phoneRingsTalk = MutableStateFlow(true)
    val phoneRingsTalk: StateFlow<Boolean> = _phoneRingsTalk.asStateFlow()

    /** Les heures de tranquillité : rien ne sonne pendant la plage ([quietFrom] → [quietUntil]). */
    private val _quietHours = MutableStateFlow(false)
    val quietHours: StateFlow<Boolean> = _quietHours.asStateFlow()

    /** Le début de la plage de tranquillité, en minutes depuis minuit (22:00 par défaut). */
    private val _quietFrom = MutableStateFlow(QuietHours.DEFAULT_FROM)
    val quietFrom: StateFlow<Int> = _quietFrom.asStateFlow()

    /** La fin de la plage de tranquillité, en minutes depuis minuit (07:00 par défaut). */
    private val _quietUntil = MutableStateFlow(QuietHours.DEFAULT_UNTIL)
    val quietUntil: StateFlow<Int> = _quietUntil.asStateFlow()

    /**
     * « Suivi du travail » : ce qu'un bot annonce de son travail — jamais, seulement à la fin
     * (le défaut), ou à chaque étape avec la notification d'avancement dans la barre d'état.
     */
    private val _workTracking = MutableStateFlow(WorkTracking.DEFAULT)
    val workTracking: StateFlow<WorkTracking> = _workTracking.asStateFlow()

    /** Ce que l'en-tête et la zone de saisie appellent l'assistant ; vide = « Onyx ». */
    private val _displayName = MutableStateFlow("")
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    /**
     * L'écran de configuration du premier lancement a été traversé (« Skip » ou « Done »).
     *
     * C'est le `Settings.setupDone` de la référence. Sans lui, l'écran se réaffichait à chaque
     * composition tant qu'aucune adresse n'était saisie : les deux boutons paraissaient morts et
     * l'utilisateur restait enfermé dans le formulaire.
     */
    private val _setupDone = MutableStateFlow(false)
    val setupDone: StateFlow<Boolean> = _setupDone.asStateFlow()

    /** Les couleurs choisies par thème, clés = identifiants de thème (`Settings.themeColors`). */
    private val _themeColors = MutableStateFlow<Map<String, ThemeColors>>(emptyMap())
    val themeColors: StateFlow<Map<String, ThemeColors>> = _themeColors.asStateFlow()

    /** « Bot faces » : éteint, chaque bot est son initiale sur un disque de sa couleur. */
    private val _botFaces = MutableStateFlow(true)
    val botFaces: StateFlow<Boolean> = _botFaces.asStateFlow()

    /** La voix réglée pour chaque bot, par serveur (`Settings.botVoices`). */
    private val _botVoices = MutableStateFlow<Map<String, Map<String, com.onyx.app.voice.BotVoice>>>(emptyMap())
    val botVoices: StateFlow<Map<String, Map<String, com.onyx.app.voice.BotVoice>>> = _botVoices.asStateFlow()

    /**
     * Les voix réglées pour les bots **de ce serveur** (`Settings.botVoicesHere` de la référence) : c'est ce
     * que la lecture consulte pour donner à chacun la sienne.
     */
    fun botVoicesHere(serverId: String): Map<String, com.onyx.app.voice.BotVoice> =
        _botVoices.value[serverId].orEmpty()

    /**
     * Règle la voix d'un bot (`BotVoicePicker` de la référence) ; `null` efface son réglage — le bot reprend
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

    /** « Pictures from Hermes » : la photo du profil Hermes remplace le visage quand il en a une. */
    private val _botPictures = MutableStateFlow(true)
    val botPictures: StateFlow<Boolean> = _botPictures.asStateFlow()

    /** « Bot colors in their chats » : bulles et accent d'un chat prennent la couleur du bot. */
    private val _botColors = MutableStateFlow(true)
    val botColors: StateFlow<Boolean> = _botColors.asStateFlow()

    /**
     * « Link previews » : une fiche avec le titre et l'image de la page sous un message qui
     * contient un lien (`LinkPreviews`). **Éteint par défaut** : le téléphone ouvre la page
     * lui-même, donc le site voit la visite, là où Onyx ne parle sinon qu'aux serveurs qu'on lui
     * a donnés.
     */
    private val _linkPreviews = MutableStateFlow(false)
    val linkPreviews: StateFlow<Boolean> = _linkPreviews.asStateFlow()

    /** « Chat text size » : les pas -2…2 autour de la taille système (`Settings.chatTextSizes`). */
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
     * autres réglages d'interface : il vit dans le `DataStore` local, pas sur le serveur) : le
     * pliable déplié garde la forme que son porteur lui a donnée d'une session à l'autre.
     */
    private val _sidebarCollapsed = MutableStateFlow(false)
    val sidebarCollapsed: StateFlow<Boolean> = _sidebarCollapsed.asStateFlow()

    /**
     * **« Halo »** : le verre réagit-il au doigt ? Lumière spéculaire, enfoncement à l'appui,
     * contour illuminé — les trois d'un seul interrupteur.
     *
     * C'est **le seul** réglage que le lot « verre tactile » ajoute ; l'écran qui l'affiche est porté
     * par un autre lot, qui ajoute justement « Halo ». Ici on ne pose donc que **la clé et sa
     * lecture** : éteint, l'application fournit `LocalGlassHalo = false` et *tout* est plat, sans
     * qu'un seul site ait à s'en soucier. Valeur par défaut **vrai** : le verre est vivant comme
     * dans la référence référence.
     */
    private val _glassHalo = MutableStateFlow(true)
    val glassHalo: StateFlow<Boolean> = _glassHalo.asStateFlow()

    /**
     * La période des aperçus (7, 30 ou 90 jours), **mémorisée** : rouvrir l'écran retrouve la
     * période qu'on regardait. Comme [sidebarCollapsed], c'est un réglage de l'appareil, écrit dans
     * le `DataStore` local — il ne part jamais au serveur, et ne suit donc pas le profil.
     */
    private val _insightsPeriod = MutableStateFlow(InsightsPeriod.DEFAULT)
    val insightsPeriod: StateFlow<InsightsPeriod> = _insightsPeriod.asStateFlow()

    /**
     * **La grille de raccourcis du volet** — les identifiants des tuiles affichées, dans l'ordre de
     * la personne. C'est un réglage de **cet appareil** (comme la période des aperçus et le repli du
     * volet) : il s'écrit dans le `DataStore` local, il ne part jamais au serveur.
     *
     * La valeur par défaut est **la grille d'origine** (`SidebarTileLayout.defaultIds`, les six tuiles
     * de la référence) : une clé absente, illisible, ou écrite par une version antérieure rend cette
     * grille-là — jamais une grille vide, et **jamais** l'invalidation d'un réglage déjà stocké.
     */
    private val _sidebarTiles = MutableStateFlow(com.onyx.app.ui.sidebar.SidebarTileLayout.defaultIds)
    val sidebarTiles: StateFlow<List<String>> = _sidebarTiles.asStateFlow()

    /**
     * La langue de l'app demandée par un lien de configuration **ou** par le réglage Langue : c'est
     * le **même** réglage (`Keys.LANGUAGE`, clé `app_language`), porté par [language]/[setLanguage].
     * Un lien ne crée pas un second réglage, il l'écrit — d'où l'absence d'un second flux ici.
     */

    /** Lit ce qui a été enregistré ; les valeurs par défaut sont celles de l'app de référence. */
    suspend fun load() {
        val preferences = store.data.first()
        _theme.value = OnyxTheme.fromId(preferences[SettingsKeys.THEME])
        _appearance.value = AppearanceMode.fromId(preferences[SettingsKeys.APPEARANCE])
        _pureBlack.value = preferences[Keys.PURE_BLACK] ?: false
        _halo.value = preferences[Keys.HALO] ?: true
        _fluideCover.value = preferences[Keys.FLUIDE_COVER] ?: true
        // **Ce que le propriétaire a enregistré, et rien de plus.** La lecture porte le choix tel
        // qu'il a été **écrit au geste** ([setFluideChoix], appelé sur la planche des variantes) ;
        // **aucune clé n'est semée ici** — le démarrage n'écrit jamais. S'il n'y a **aucun** choix
        // enregistré, la carte prend le défaut **visible** ([ChoixCouverture.DEFAUT] — filaments,
        // graine `3417634283`, teinte bleu) **sans l'écrire** ; dès qu'un choix existe, c'est **LUI**
        // qui gagne. Semer au démarrage reviendrait à écraser en silence le choix de l'utilisateur,
        // ce que le verrou livré (`AppearancePersistenceVerrouTest`) interdit.
        _fluideChoix.value = com.onyx.app.ui.design.ChoixCouverture.enregistre(
            variante = preferences[Keys.FLUIDE_VARIANTE],
            graine = preferences[Keys.FLUIDE_GRAINE],
            teinte = preferences[Keys.FLUIDE_TEINTE],
        )
        _autoCardLook.value = AutoCardLook.fromId(preferences[Keys.AUTO_CARDS])
        _autoVoiceLook.value = AutoVoiceLook.fromId(preferences[Keys.AUTO_VOICE])
        _speechLocale.value = preferences[Keys.SPEECH_LOCALE] ?: DEFAULT_SPEECH_LOCALE
        _handsFreeByDefault.value = preferences[Keys.HANDS_FREE] ?: true
        // « Reconnaître hors ligne » : **faux par défaut** — le seul chemin vers le hors ligne.
        _offlineRecognition.value = preferences[Keys.OFFLINE_RECOGNITION] ?: false
        _returnSends.value = preferences[Keys.RETURN_SENDS] ?: true
        _voiceSpeed.value = preferences[Keys.VOICE_SPEED] ?: 1.0f
        _replyVoice.value = ReplyVoice.fromId(preferences[Keys.REPLY_VOICE])
        _kokoroUrl.value = preferences[Keys.KOKORO_URL] ?: DEFAULT_KOKORO_URL
        // Le jeton du service vocal : la valeur par défaut vient du fichier du mandataire (0600),
        // injecté à la compilation pour la version de test ; sinon vide, donc aucun en-tête.
        _kokoroToken.value = preferences[Keys.KOKORO_TOKEN] ?: DEFAULT_KOKORO_TOKEN
        _kokoroVoice.value = preferences[Keys.KOKORO_VOICE] ?: DEFAULT_KOKORO_VOICE
        _replyLanguage.value = preferences[Keys.REPLY_LANGUAGE] ?: ""
        // La langue de l'interface : relue et **réappliquée** ici, pour qu'une remise à zéro des
        // réglages ramène aussi l'app à la langue d'Android.
        _language.value = OnyxLanguage.normalize(preferences[Keys.LANGUAGE])
        OnyxLanguage.apply(_language.value)
        // Le mode enregistré ; à défaut l'ancien booléen (vrai valait « partout »), à défaut le
        // défaut de la référence.
        _talkOver.value = preferences[Keys.TALK_OVER_MODE]?.let(TalkOver::fromId)
            ?: preferences[Keys.TALK_OVER]?.let(TalkOver::fromLegacy)
            ?: TalkOver.DEFAULT
        _earpieceAtEar.value = preferences[Keys.EARPIECE_AT_EAR] ?: true
        _suggestFollowUps.value = preferences[Keys.SUGGEST_FOLLOW_UPS] ?: false
        _reasoningEffort.value = preferences[Keys.REASONING_EFFORT] ?: ""
        _notifyInBackground.value = preferences[Keys.NOTIFY_IN_BACKGROUND] ?: false
        _notificationSound.value = preferences[Keys.NOTIFICATION_SOUND] ?: true
        // « Appels pour une réponse » et les heures de tranquillité : les quatre clés du lot d'appel.
        _phoneRings.value = preferences[Keys.PHONE_RINGS] ?: false
        _phoneRingsTalk.value = preferences[Keys.PHONE_RINGS_TALK] ?: true
        _quietHours.value = preferences[Keys.QUIET_HOURS] ?: false
        _quietFrom.value = (preferences[Keys.QUIET_FROM] ?: QuietHours.DEFAULT_FROM)
            .coerceIn(0, QuietHours.MINUTES_A_DAY - 1)
        _quietUntil.value = (preferences[Keys.QUIET_UNTIL] ?: QuietHours.DEFAULT_UNTIL)
            .coerceIn(0, QuietHours.MINUTES_A_DAY - 1)
        // Le réglage en trois choix ; à défaut l'ancien booléen (vrai voulait dire « suivre le
        // tour », faux « ne rien suivre »), à défaut le défaut.
        _workTracking.value = preferences[Keys.WORK_TRACKING]?.let(WorkTracking::fromId)
            ?: preferences[Keys.SHOW_LIVE_ACTIVITY]?.let { live -> if (live) WorkTracking.DEFAULT else WorkTracking.NEVER }
            ?: WorkTracking.DEFAULT
        // Le nom par défaut a changé avec le renommage : un « Onyx » encore enregistré est
        // l'ancienne valeur par défaut, pas un choix — il redevient vide, donc « Onyx ».
        _displayName.value = (preferences[Keys.DISPLAY_NAME] ?: "")
            .takeUnless { it.trim().equals("Onyx", ignoreCase = true) }
            .orEmpty()
        _botFaces.value = preferences[Keys.BOT_FACES] ?: true
        _botPictures.value = preferences[Keys.BOT_PICTURES] ?: true
        _botColors.value = preferences[Keys.BOT_COLORS] ?: true
        _linkPreviews.value = preferences[Keys.LINK_PREVIEWS] ?: false
        // L'écran de configuration du premier lancement a-t-il déjà été traversé ?
        _setupDone.value = preferences[Keys.SETUP_DONE] ?: false
        _chatTextSize.value = (preferences[Keys.CHAT_TEXT_SIZE] ?: 0)
            .coerceIn(ResolvedTheme.chatTextSizes.first(), ResolvedTheme.chatTextSizes.last())
        _voiceOrb.value = VoiceOrbChoice.fromId(preferences[Keys.VOICE_ORB])
        _orbLight.value = OrbLight.fromId(preferences[Keys.ORB_LIGHT])
        _sidebarCollapsed.value = preferences[Keys.SIDEBAR_COLLAPSED] ?: false
        // « Halo » : le verre réagit-il au doigt ? Vrai par défaut, comme dans la référence.
        _glassHalo.value = preferences[Keys.GLASS_HALO] ?: true
        _insightsPeriod.value = InsightsPeriod.fromDays(preferences[SettingsKeys.INSIGHTS_PERIOD])
        // La grille de raccourcis : relue **telle qu'elle a été enregistrée** (ordre compris). Ce que
        // le fichier ne portait pas encore — ou qu'une version antérieure ne connaissait pas — rend la
        // grille d'origine, jamais une grille amputée (voir `SidebarTileLayout.parse`).
        _sidebarTiles.value = com.onyx.app.ui.sidebar.SidebarTileLayout
            .parse(preferences[SettingsKeys.SIDEBAR_TILES])
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

    /** « Noir pur » : les fonds de page au noir absolu en apparence sombre (OLED). */
    suspend fun setPureBlack(value: Boolean) {
        _pureBlack.value = value
        store.edit { it[Keys.PURE_BLACK] = value }
    }

    /** « Halo » : la lumière colorée douce derrière le haut de chaque écran. */
    suspend fun setHalo(value: Boolean) {
        _halo.value = value
        store.edit { it[Keys.HALO] = value }
    }

    /**
     * « Fluide » : la couverture de conversation animée (variante B), ou l'ancienne image.
     *
     * **C'est ici, et seulement ici, que le CHOIX s'écrit** : activer la couverture de fluide, c'est
     * **toucher la variante des filaments sur la planche** — à cet instant, le geste de l'utilisateur
     * enregistre le choix (variante, graine, teinte) par [setFluideChoix]. Le **démarrage ne sème
     * rien** (`load()` ne fait que relire) : sans quoi il écraserait, à chaque lancement, ce que
     * l'utilisateur avait choisi — ce que le verrou livré (`AppearancePersistenceVerrouTest`) refuse.
     */
    suspend fun setFluideCover(value: Boolean) {
        _fluideCover.value = value
        store.edit { it[Keys.FLUIDE_COVER] = value }
        if (value) setFluideChoix(_fluideChoix.value)
    }

    /**
     * **Enregistre le matériau du fluide** — les trois valeurs du choix (variante, graine, teinte).
     * Elles sont écrites ensemble : un choix à moitié écrit ferait dessiner une teinte que la graine
     * ne donne pas. C'est ce que le verrou de conformité refuse.
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
        _speechLocale.value = clean.ifEmpty { DEFAULT_SPEECH_LOCALE }
        store.edit { it[Keys.SPEECH_LOCALE] = _speechLocale.value }
    }

    suspend fun setHandsFreeByDefault(value: Boolean) {
        _handsFreeByDefault.value = value
        store.edit { it[Keys.HANDS_FREE] = value }
    }

    /**
     * « Reconnaître hors ligne » : le **seul** chemin vers le hors ligne. Éteint (le défaut), la
     * reconnaissance se fait en ligne ; allumé, le moteur tente l'appareil seul et bascule en ligne
     * de lui-même s'il n'a pas la langue.
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

    /** Le jeton du service vocal protégé ; vide efface l'en-tête et revient au serveur ouvert. */
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
     * La langue choisie, posée **avant** la première composition (`OnyxApp` la relit au
     * démarrage) : le premier écran est déjà dans la bonne langue, sans clignotement dans celle
     * d'Android. Le reste des réglages, lui, peut arriver un peu après.
     */
    suspend fun applyStoredLanguage() {
        val stored = OnyxLanguage.normalize(store.data.first()[Keys.LANGUAGE])
        _language.value = stored
        OnyxLanguage.apply(stored)
    }

    /**
     * Le thème et l'apparence enregistrés, posés **avant** la première composition — même contrat
     * que [applyStoredLanguage], et pour la même raison.
     *
     * Sans cet appel, `_theme` et `_appearance` restent sur leurs défauts ([OnyxTheme.STANDARD] et
     * [AppearanceMode.SYSTEM]) jusqu'à ce que [load] ait fini de lire le disque. Or [load] est
     * lancé **après** `Application.onCreate` (`AppContainer.install`), donc en concurrence avec la
     * composition de `OnyxApp` : la première image (et toute image jusqu'à la fin de la lecture)
     * est rendue en **SYSTEM**. Sur un téléphone en clair, cela veut dire **le clair** — alors même
     * que l'utilisateur a choisi « Sombre » et que le magasin porte `appearance=dark`.
     *
     * C'est la seule lecture qui doit précéder la première image ; **aucune écriture** n'a lieu ici,
     * le choix de l'utilisateur n'est jamais touché au démarrage.
     */
    suspend fun applyStoredTheme() {
        val stored = store.data.first()
        _theme.value = OnyxTheme.fromId(stored[SettingsKeys.THEME])
        _appearance.value = AppearanceMode.fromId(stored[SettingsKeys.APPEARANCE])
    }

    /**
     * Change la langue de l'interface.
     *
     * L'effet est **immédiat en mémoire** (`OnyxLanguage.apply`, avant la première suspension
     * possible) : l'appelant peut recréer l'activité dans la foulée, l'écran se redessine déjà
     * dans la nouvelle langue. L'écriture sur disque suit.
     */
    suspend fun setLanguage(value: String) {
        val clean = OnyxLanguage.normalize(value)
        _language.value = clean
        OnyxLanguage.apply(clean)
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
            // L'ancien booléen reste écrit : une version antérieure de l'app relit le même fichier.
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

    /** « Appels pour une réponse » : faire sonner le téléphone pour une approbation ou une question. */
    suspend fun setPhoneRings(value: Boolean) {
        _phoneRings.value = value
        store.edit { it[Keys.PHONE_RINGS] = value }
    }

    /** Décrocher met Hermes en ligne (voix live) plutôt que d'ouvrir la conversation. */
    suspend fun setPhoneRingsTalk(value: Boolean) {
        _phoneRingsTalk.value = value
        store.edit { it[Keys.PHONE_RINGS_TALK] = value }
    }

    /** Les heures de tranquillité : rien ne sonne pendant la plage. */
    suspend fun setQuietHours(value: Boolean) {
        _quietHours.value = value
        store.edit { it[Keys.QUIET_HOURS] = value }
    }

    /** Le début de la plage de tranquillité, en minutes depuis minuit. */
    suspend fun setQuietFrom(minute: Int) {
        val clean = minute.coerceIn(0, QuietHours.MINUTES_A_DAY - 1)
        _quietFrom.value = clean
        store.edit { it[Keys.QUIET_FROM] = clean }
    }

    /** La fin de la plage de tranquillité, en minutes depuis minuit. */
    suspend fun setQuietUntil(minute: Int) {
        val clean = minute.coerceIn(0, QuietHours.MINUTES_A_DAY - 1)
        _quietUntil.value = clean
        store.edit { it[Keys.QUIET_UNTIL] = clean }
    }

    /**
     * Rien ne sonne maintenant ? Les heures de tranquillité allumées **et** l'instant dedans —
     * c'est la seule question que se posent l'appel pour une approbation et l'appel de l'agent.
     */
    fun quietNow(millis: Long = System.currentTimeMillis()): Boolean =
        QuietHours.now(QuietHours.minuteOfDay(millis), _quietHours.value, _quietFrom.value, _quietUntil.value)

    suspend fun setShowLiveActivity(value: Boolean) {
        _workTracking.value = if (value) WorkTracking.DEFAULT else WorkTracking.NEVER
        store.edit { it[Keys.WORK_TRACKING] = _workTracking.value.id }
    }

    /** Enregistre le suivi du travail — trois choix, un seul retenu. */
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

    /** Enregistre les couleurs par thème ; une entrée retirée repart sur les couleurs du thème. */
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

    /** « Halo » : le verre réagit-il au doigt ? Éteint, tout est plat (voir [glassHalo]). */
    suspend fun setGlassHalo(value: Boolean) {
        _glassHalo.value = value
        store.edit { it[Keys.GLASS_HALO] = value }
    }

    /**
     * Mémorise la période des aperçus. Écrite **immédiatement** (pas à la fermeture de l'écran) :
     * un appareil tué pendant qu'on regardait la période la retrouve au lancement suivant.
     */
    suspend fun setInsightsPeriod(period: InsightsPeriod) {
        _insightsPeriod.value = period
        store.edit { it[SettingsKeys.INSIGHTS_PERIOD] = period.days }
    }

    /**
     * **Mémorise la grille de raccourcis du volet** : les tuiles affichées, dans leur ordre.
     *
     * Écrite **immédiatement** (un appareil tué au milieu de la composition retrouve la grille qu'il
     * montrait), et toujours **ramenée dans ses bornes** avant d'être écrite — au plus douze, au moins
     * quatre — si bien que le fichier ne peut pas porter une grille que l'écran refuserait de dessiner.
     */
    suspend fun setSidebarTiles(ids: List<String>) {
        val clean = com.onyx.app.ui.sidebar.SidebarTileLayout.normalize(ids)
        _sidebarTiles.value = clean
        store.edit {
            it[SettingsKeys.SIDEBAR_TILES] = com.onyx.app.ui.sidebar.SidebarTileLayout.encode(clean)
        }
    }

    /** Remet la grille de raccourcis à **la grille d'origine** (les six tuiles de la référence). */
    suspend fun resetSidebarTiles() {
        setSidebarTiles(com.onyx.app.ui.sidebar.SidebarTileLayout.defaultIds)
    }

    /**
     * Remet **tous** les réglages à leurs valeurs d'origine, comme `Settings.shared.reset()` de la référence :
     * les préférences sont effacées, puis relues — ce sont les défauts de `load()` qui reviennent.
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
        /**
         * « Reconnaître hors ligne ». Clé neuve, **fausse par défaut** : un appareil qui n'a jamais
         * rien réglé reconnaît en ligne (avant, le hors ligne était imposé à tout le monde).
         */
        val OFFLINE_RECOGNITION = booleanPreferencesKey("offline_recognition")
        val RETURN_SENDS = booleanPreferencesKey("return_sends")
        val VOICE_SPEED = floatPreferencesKey("voice_speed")
        val REPLY_VOICE = stringPreferencesKey("replyVoice")
        val KOKORO_URL = stringPreferencesKey("kokoroURL")
        val KOKORO_VOICE = stringPreferencesKey("kokoroVoice")
        /** Le jeton du service vocal protégé ; il ne va jamais dans `kokoroURL`. */
        val KOKORO_TOKEN = stringPreferencesKey("kokoroToken")
        val REPLY_LANGUAGE = stringPreferencesKey("reply_language")
        /** La langue de l'interface (`OnyxLanguage`) : un réglage propre à cette app. */
        val LANGUAGE = stringPreferencesKey("app_language")
        val TALK_OVER = booleanPreferencesKey("talk_over")
        /** `Settings.talkOver` de la référence est un choix à trois états, pas un booléen. */
        val TALK_OVER_MODE = stringPreferencesKey("talk_over_mode")
        /** La clé de la référence, mot pour mot : `Settings.Keys.earpieceAtEar`. */
        val EARPIECE_AT_EAR = booleanPreferencesKey("earpieceAtEar")
        val SUGGEST_FOLLOW_UPS = booleanPreferencesKey("suggest_follow_ups")
        val REASONING_EFFORT = stringPreferencesKey("reasoning_effort")
        val NOTIFY_IN_BACKGROUND = booleanPreferencesKey("notify_in_background")
        val NOTIFICATION_SOUND = booleanPreferencesKey("notification_sound")
        /** « Appels pour une réponse » et sa suite : les clés du lot d'appel. */
        val PHONE_RINGS = booleanPreferencesKey("phoneRings")
        val PHONE_RINGS_TALK = booleanPreferencesKey("phoneRingsTalk")
        val QUIET_HOURS = booleanPreferencesKey("quietHours")
        val QUIET_FROM = intPreferencesKey("quietFrom")
        val QUIET_UNTIL = intPreferencesKey("quietUntil")
        /** L'ancien booléen, gardé pour la reprise : un vrai valait « suivre le tour ». */
        val SHOW_LIVE_ACTIVITY = booleanPreferencesKey("show_live_activity")
        /** « Suivi du travail » : `never`, `finish` ou `step`. */
        val WORK_TRACKING = stringPreferencesKey("work_tracking")
        val STOP_PHRASES = stringPreferencesKey("stop_phrases")
        val SPOKEN_PREFIXES = stringPreferencesKey("spoken_prefixes")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        /** La clé de la référence, mot pour mot : `Settings.Keys.setupDone`. */
        val SETUP_DONE = booleanPreferencesKey("setupDone")
        val THEME_COLORS = stringPreferencesKey("themeColors")
        // Les clés portent les mêmes noms que `Settings.Keys` de la référence.
        val BOT_FACES = booleanPreferencesKey("botFaces")
        val BOT_PICTURES = booleanPreferencesKey("botPictures")
        val BOT_COLORS = booleanPreferencesKey("botColors")
        /** La clé de la référence, mot pour mot : `Settings.Keys.botVoices`. */
        val BOT_VOICES = stringPreferencesKey("botVoices")
        // La clé de la référence, mot pour mot : `Settings.Keys.linkPreviews`.
        val LINK_PREVIEWS = booleanPreferencesKey("linkPreviews")
        val CHAT_TEXT_SIZE = intPreferencesKey("chatTextSize")
        val VOICE_ORB = stringPreferencesKey("voiceOrb")
        val ORB_LIGHT = stringPreferencesKey("orbLight")
        /** Le volet de gauche des deux panneaux, replié ou non (réglage de l'appareil). */
        val SIDEBAR_COLLAPSED = booleanPreferencesKey("sidebarCollapsed")
        /** « Noir pur » : les fonds au noir absolu en sombre (OLED). Même nom que `Settings.Keys`. */
        val PURE_BLACK = booleanPreferencesKey("pureBlack")
        /** « Halo » : la lumière douce derrière le haut de chaque écran. Même nom que `Settings.Keys`. */
        val HALO = booleanPreferencesKey("halo")
        /** « Fluide » : la couverture de conversation animée. Vraie par défaut. */
        val FLUIDE_COVER = booleanPreferencesKey("fluideCover")
        /**
         * **Le matériau du fluide** : la variante retenue, la graine du tracé et la teinte nommée.
         * Écrites ensemble par `setFluideChoix` ; relues par `load()`. Ce sont ces trois clés qui
         * portent le **choix du propriétaire** (filaments, graine `3417634283`, teinte bleu) d'un
         * démarrage à l'autre — sans elles, la carte réinventait la teinte à chaque dessin.
         */
        val FLUIDE_VARIANTE = stringPreferencesKey("fluideVariante")
        val FLUIDE_GRAINE = longPreferencesKey("fluideGraine")
        val FLUIDE_TEINTE = stringPreferencesKey("fluideTeinte")
        /**
         * « Halo » : le verre réagit-il au doigt ? Clé propre à l'app (il n'y a pas d'entrée
         * correspondante dans `Settings.Keys` de la référence — le verre liquide y est toujours vivant),
         * **vraie par défaut**.
         */
        val GLASS_HALO = booleanPreferencesKey("glassHalo")
    }

    private val THEME_COLORS_SERIALIZER = MapSerializer(String.serializer(), ThemeColors.serializer())

    /** `Settings.botVoices` : par serveur, puis par profil de bot. */
    private val BOT_VOICES_SERIALIZER: kotlinx.serialization.KSerializer<Map<String, Map<String, com.onyx.app.voice.BotVoice>>> =
        MapSerializer(String.serializer(), MapSerializer(String.serializer(), com.onyx.app.voice.BotVoice.serializer()))

    companion object {
        const val DEFAULT_KOKORO_URL = ""
        const val DEFAULT_KOKORO_VOICE = "am_onyx(2)+bm_george(1)"

        /**
         * Le jeton du service vocal pour la **version de test** : il vient du fichier `0600` du
         * mandataire, lu à la compilation (voir `app/build.gradle.kts`) — jamais du dépôt, jamais
         * affiché. Absent, il reste vide : l'app parle alors à un serveur ouvert, comme avant.
         */
        val DEFAULT_KOKORO_TOKEN: String = com.onyx.app.BuildConfig.VOICE_TOKEN

        /** Les langues de l'app : la première entrée vide veut dire « Système ». */
        val APP_LANGUAGES = listOf(
            "" to "System",
            "fr" to "Français",
            "en" to "English",
        )

        /**
         * La locale à poser pour un code de langue ; `null` veut dire **Système** (on ne touche pas à
         * celle du téléphone). Pur, donc éprouvable sans Android.
         */
        fun localeFor(code: String): java.util.Locale? {
            val clean = code.trim().lowercase().substringBefore('-').take(5)
            if (clean.isEmpty()) return null
            return runCatching { java.util.Locale.forLanguageTag(clean) }
                .getOrNull()?.takeIf { it.language.isNotEmpty() }
        }

        /** Les sept niveaux d'effort, dans l'ordre de la référence ; `""` = « Default ». */
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

        /** Les langues de réponse proposées ; la première entrée vide veut dire « automatique ». */
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

        /** La phrase ajoutée aux consignes, mot pour mot comme côté référence. */
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
