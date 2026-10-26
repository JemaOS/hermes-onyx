package com.onyx.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import com.onyx.app.data.net.ChatCompletionsClient
import com.onyx.app.data.net.ConnectionProbe
import com.onyx.app.data.net.TransportError
import com.onyx.app.data.repo.ServerRepository
import com.onyx.app.data.repo.SettingsRepository
import com.onyx.app.car.AutoCardLook
import com.onyx.app.car.AutoVoiceLook
import com.onyx.app.ui.theme.AppearanceMode
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ThemeColors
import com.onyx.app.voice.ReplyVoice
import com.onyx.app.voice.TalkOver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Les Réglages: la connexion, l'apparence, et la liste des serveurs.
 *
 * Chaque champ écrit dans le brouillon, et
 * « Enregistrer » pousse le tout dans le dépôt, qui le persiste et met à jour le Keystore.
 */
class SettingsViewModel(
    private val serversRepo: ServerRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    val servers: StateFlow<List<ServerConfig>> = serversRepo.servers
    val activeId: StateFlow<String> = serversRepo.activeId
    val theme: StateFlow<OnyxTheme> = settingsRepo.theme
    val appearance: StateFlow<AppearanceMode> = settingsRepo.appearance
    val autoCardLook: StateFlow<AutoCardLook> = settingsRepo.autoCardLook
    val autoVoiceLook: StateFlow<AutoVoiceLook> = settingsRepo.autoVoiceLook
    val speechLocale: StateFlow<String> = settingsRepo.speechLocale
    val replyLanguage: StateFlow<String> = settingsRepo.replyLanguage
    val voiceSpeed: StateFlow<Float> = settingsRepo.voiceSpeed
    val replyVoice: StateFlow<ReplyVoice> = settingsRepo.replyVoice
    val kokoroUrl: StateFlow<String> = settingsRepo.kokoroUrl
    val kokoroVoice: StateFlow<String> = settingsRepo.kokoroVoice
    val talkOver: StateFlow<TalkOver> = settingsRepo.talkOver
    val earpieceAtEar: StateFlow<Boolean> = settingsRepo.earpieceAtEar
    val suggestFollowUps: StateFlow<Boolean> = settingsRepo.suggestFollowUps
    val reasoningEffort: StateFlow<String> = settingsRepo.reasoningEffort
    val handsFreeByDefault: StateFlow<Boolean> = settingsRepo.handsFreeByDefault
    val returnSends: StateFlow<Boolean> = settingsRepo.returnSends
    val notifyInBackground: StateFlow<Boolean> = settingsRepo.notifyInBackground
    val notificationSound: StateFlow<Boolean> = settingsRepo.notificationSound
    val showLiveActivity: StateFlow<Boolean> = settingsRepo.showLiveActivity
    val displayName: StateFlow<String> = settingsRepo.displayName
    val themeColors: StateFlow<Map<String, ThemeColors>> = settingsRepo.themeColors
    val botFaces: StateFlow<Boolean> = settingsRepo.botFaces
    val botPictures: StateFlow<Boolean> = settingsRepo.botPictures
    val botColors: StateFlow<Boolean> = settingsRepo.botColors
    val linkPreviews: StateFlow<Boolean> = settingsRepo.linkPreviews
    val chatTextSize: StateFlow<Int> = settingsRepo.chatTextSize
    val voiceOrb: StateFlow<com.onyx.app.ui.voice.VoiceOrbChoice> = settingsRepo.voiceOrb
    val orbLight: StateFlow<com.onyx.app.ui.voice.OrbLight> = settingsRepo.orbLight

    /** Le serveur en cours d'édition; `null` quand rien n'est ouvert. */
    private val _draft = MutableStateFlow<ServerConfig?>(null)
    val draft: StateFlow<ServerConfig?> = _draft.asStateFlow()

    private val _probe = MutableStateFlow<ConnectionProbe?>(null)
    val probe: StateFlow<ConnectionProbe?> = _probe.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    /** Ouvre un serveur pour l'éditer. */
    fun open(config: ServerConfig) {
        _draft.value = config
        _probe.value = null
    }

    fun closeEditor() {
        _draft.value = null
        _probe.value = null
    }

    /** Met à jour le brouillon; sans effet si rien n'est ouvert. */
    fun update(transform: (ServerConfig) -> ServerConfig) {
        _draft.value = _draft.value?.let(transform)
    }

    fun save() {
        val draft = _draft.value ?: return
        viewModelScope.launch { serversRepo.upsert(draft) }
    }

    /** Enregistre puis ferme — le bouton « Done » d'une modification. */
    fun saveAndClose() {
        save()
        closeEditor()
    }

    /** Ajoute un serveur vide et l'ouvre. */
    fun addServer() {
        val fresh = ServerConfig(
            id = serversRepo.newId(),
            name = "Onyx",
            baseUrl = "",
            transport = Transport.CHAT_COMPLETIONS,
        )
        viewModelScope.launch {
            serversRepo.upsert(fresh)
            _draft.value = fresh
        }
    }

    fun delete(config: ServerConfig) {
        viewModelScope.launch {
            serversRepo.remove(config.id)
            if (_draft.value?.id == config.id) closeEditor()
        }
    }

    fun setActive(id: String) {
        viewModelScope.launch { serversRepo.setActive(id) }
    }

    /** Essaie la connexion au serveur du brouillon et garde le résultat pour l'écran. */
    fun testConnection() {
        val draft = _draft.value ?: return
        viewModelScope.launch {
            _isTesting.value = true
            _probe.value = try {
                ChatCompletionsClient(draft).testConnection()
            } catch (error: CancellationException) {
                throw error
            } catch (error: TransportError) {
                ConnectionProbe(false, error.userMessage(), 0L, emptyList())
            } finally {
                _isTesting.value = false
            }
        }
    }

    fun setTheme(theme: OnyxTheme) {
        viewModelScope.launch { settingsRepo.setTheme(theme) }
    }

    fun setAppearance(appearance: AppearanceMode) {
        viewModelScope.launch { settingsRepo.setAppearance(appearance) }
    }

    fun setAutoCardLook(value: AutoCardLook) {
        viewModelScope.launch { settingsRepo.setAutoCardLook(value) }
    }

    fun setAutoVoiceLook(value: AutoVoiceLook) {
        viewModelScope.launch { settingsRepo.setAutoVoiceLook(value) }
    }

    fun setSpeechLocale(tag: String) {
        viewModelScope.launch { settingsRepo.setSpeechLocale(tag) }
    }

    fun setHandsFreeByDefault(value: Boolean) {
        viewModelScope.launch { settingsRepo.setHandsFreeByDefault(value) }
    }

    fun setReturnSends(value: Boolean) {
        viewModelScope.launch { settingsRepo.setReturnSends(value) }
    }

    fun setVoiceSpeed(value: Float) {
        viewModelScope.launch { settingsRepo.setVoiceSpeed(value) }
    }

    fun setReplyVoice(value: ReplyVoice) {
        viewModelScope.launch { settingsRepo.setReplyVoice(value) }
    }

    fun setKokoroUrl(value: String) {
        viewModelScope.launch { settingsRepo.setKokoroUrl(value) }
    }

    fun setKokoroVoice(value: String) {
        viewModelScope.launch { settingsRepo.setKokoroVoice(value) }
    }

    fun setReplyLanguage(code: String) {
        viewModelScope.launch { settingsRepo.setReplyLanguage(code) }
    }

    fun setReasoningEffort(value: String) {
        viewModelScope.launch { settingsRepo.setReasoningEffort(value) }
    }

    fun setSuggestFollowUps(value: Boolean) {
        viewModelScope.launch { settingsRepo.setSuggestFollowUps(value) }
    }

    /** La rangée des Réglages est un interrupteur: allumé = « partout », éteint = « jamais ». */
    fun setTalkOver(value: Boolean) {
        viewModelScope.launch {
            settingsRepo.setTalkOver(if (value) TalkOver.EVERYWHERE else TalkOver.OFF)
        }
    }

    /** Le choix exact (jamais / avec un casque / partout), depuis le panneau vocal. */
    fun setTalkOverMode(value: TalkOver) {
        viewModelScope.launch { settingsRepo.setTalkOver(value) }
    }

    fun setEarpieceAtEar(value: Boolean) {
        viewModelScope.launch { settingsRepo.setEarpieceAtEar(value) }
    }

    fun setNotifyInBackground(value: Boolean) {
        viewModelScope.launch { settingsRepo.setNotifyInBackground(value) }
    }

    fun setNotificationSound(value: Boolean) {
        viewModelScope.launch { settingsRepo.setNotificationSound(value) }
    }

    fun setShowLiveActivity(value: Boolean) {
        viewModelScope.launch { settingsRepo.setShowLiveActivity(value) }
    }

    fun setDisplayName(value: String) {
        viewModelScope.launch { settingsRepo.setDisplayName(value) }
    }

    fun setThemeColors(value: Map<String, ThemeColors>) {
        viewModelScope.launch { settingsRepo.setThemeColors(value) }
    }

    fun setBotFaces(value: Boolean) {
        viewModelScope.launch { settingsRepo.setBotFaces(value) }
    }

    fun setBotPictures(value: Boolean) {
        viewModelScope.launch { settingsRepo.setBotPictures(value) }
    }

    fun setBotColors(value: Boolean) {
        viewModelScope.launch { settingsRepo.setBotColors(value) }
    }

    fun setLinkPreviews(value: Boolean) {
        viewModelScope.launch { settingsRepo.setLinkPreviews(value) }
    }

    fun setChatTextSize(steps: Int) {
        viewModelScope.launch { settingsRepo.setChatTextSize(steps) }
    }

    fun setVoiceOrb(orb: com.onyx.app.ui.voice.VoiceOrbChoice) {
        viewModelScope.launch { settingsRepo.setVoiceOrb(orb) }
    }

    fun setOrbLight(light: com.onyx.app.ui.voice.OrbLight) {
        viewModelScope.launch { settingsRepo.setOrbLight(light) }
    }

    /**
     * La fenêtre de contexte d'un serveur, le champ « Context window » de `MetricsSettings`:
     * la même valeur que l'anneau de contexte du chat.
     */
    fun setContextWindow(id: String, value: Int) {
        val server = serversRepo.servers.value.firstOrNull { it.id == id } ?: return
        if (server.contextWindow == value) return
        viewModelScope.launch { serversRepo.upsert(server.copy(contextWindow = value)) }
    }

    /** Enregistre les adresses, clés et identifiants de la page « Connection details ». */
    fun saveConnection(server: ServerConfig) {
        viewModelScope.launch { serversRepo.upsert(server) }
    }
}
