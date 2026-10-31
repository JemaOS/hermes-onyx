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
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.car.AutoCardLook
import com.onyx.app.car.AutoVoiceLook
import com.onyx.app.ui.theme.AppearanceMode
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ThemeColors
import com.onyx.app.notifications.WorkTracking
import com.onyx.app.voice.ReplyVoice
import com.onyx.app.voice.TalkOver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Les Réglages : la connexion, l'apparence, et la liste des serveurs.
 *
 * Miroir de `SettingsView` + `Settings.shared` : chaque champ écrit dans le brouillon, et
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
    /** « Noir pur » : les fonds de page au noir absolu en apparence sombre (OLED). */
    val pureBlack: StateFlow<Boolean> = settingsRepo.pureBlack
    /** « Halo » : la lumière colorée douce derrière le haut de chaque écran. */
    val halo: StateFlow<Boolean> = settingsRepo.halo
    /** « Fluide » : la couverture animée des cartes de conversation. Vrai par défaut. */
    val fluideCover: StateFlow<Boolean> = settingsRepo.fluideCover
    val autoCardLook: StateFlow<AutoCardLook> = settingsRepo.autoCardLook
    val autoVoiceLook: StateFlow<AutoVoiceLook> = settingsRepo.autoVoiceLook
    val speechLocale: StateFlow<String> = settingsRepo.speechLocale
    val replyLanguage: StateFlow<String> = settingsRepo.replyLanguage
    val language: StateFlow<String> = settingsRepo.language
    val voiceSpeed: StateFlow<Float> = settingsRepo.voiceSpeed
    val replyVoice: StateFlow<ReplyVoice> = settingsRepo.replyVoice
    val kokoroUrl: StateFlow<String> = settingsRepo.kokoroUrl
    /** Le jeton du service vocal protégé (masqué à l'écran ; vide : aucun en-tête). */
    val kokoroToken: StateFlow<String> = settingsRepo.kokoroToken
    val kokoroVoice: StateFlow<String> = settingsRepo.kokoroVoice
    val talkOver: StateFlow<TalkOver> = settingsRepo.talkOver
    val earpieceAtEar: StateFlow<Boolean> = settingsRepo.earpieceAtEar
    val suggestFollowUps: StateFlow<Boolean> = settingsRepo.suggestFollowUps
    val reasoningEffort: StateFlow<String> = settingsRepo.reasoningEffort
    val handsFreeByDefault: StateFlow<Boolean> = settingsRepo.handsFreeByDefault
    /** « Reconnaître hors ligne » : faux par défaut — le seul chemin vers le hors ligne. */
    val offlineRecognition: StateFlow<Boolean> = settingsRepo.offlineRecognition
    val returnSends: StateFlow<Boolean> = settingsRepo.returnSends
    val notifyInBackground: StateFlow<Boolean> = settingsRepo.notifyInBackground
    val notificationSound: StateFlow<Boolean> = settingsRepo.notificationSound

    /** « Suivi du travail » : jamais, à la fin seulement, ou à chaque étape. */
    val workTracking: StateFlow<WorkTracking> = settingsRepo.workTracking
    val displayName: StateFlow<String> = settingsRepo.displayName
    /** L'écran de configuration du premier lancement a été traversé (« Skip » ou « Done »). */
    val setupDone: StateFlow<Boolean> = settingsRepo.setupDone
    val themeColors: StateFlow<Map<String, ThemeColors>> = settingsRepo.themeColors
    val botFaces: StateFlow<Boolean> = settingsRepo.botFaces
    val botPictures: StateFlow<Boolean> = settingsRepo.botPictures
    val botColors: StateFlow<Boolean> = settingsRepo.botColors
    val linkPreviews: StateFlow<Boolean> = settingsRepo.linkPreviews
    val chatTextSize: StateFlow<Int> = settingsRepo.chatTextSize
    val voiceOrb: StateFlow<com.onyx.app.ui.voice.VoiceOrbChoice> = settingsRepo.voiceOrb
    val orbLight: StateFlow<com.onyx.app.ui.voice.OrbLight> = settingsRepo.orbLight

    /** Le serveur en cours d'édition ; `null` quand rien n'est ouvert. */
    private val _draft = MutableStateFlow<ServerConfig?>(null)
    val draft: StateFlow<ServerConfig?> = _draft.asStateFlow()

    private val _probe = MutableStateFlow<ConnectionProbe?>(null)
    val probe: StateFlow<ConnectionProbe?> = _probe.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    /**
     * Le résultat de « Vérifier » sur la **carte de passerelle** : le serveur **actif**, pas le
     * brouillon de l'éditeur. C'est le même contrôle que « Tester la connexion », mais posé en haut
     * des Réglages et rendu sur la carte — succès, ou le motif réel de l'échec.
     */
    private val _gatewayProbe = MutableStateFlow<ConnectionProbe?>(null)
    val gatewayProbe: StateFlow<ConnectionProbe?> = _gatewayProbe.asStateFlow()

    /** Vrai pendant que « Vérifier » interroge la passerelle. */
    private val _gatewayVerifying = MutableStateFlow(false)
    val gatewayVerifying: StateFlow<Boolean> = _gatewayVerifying.asStateFlow()

    /**
     * « Vérifier » : teste **pour de vrai** la connexion au serveur actif — adresse + clé — et garde
     * le résultat réel. L'échec est classé par le transport (adresse introuvable, identifiants
     * refusés, serveur muet, délai dépassé), donc la carte dit toujours *ce qui* a échoué.
     */
    fun verifyActiveGateway() {
        val server = serversRepo.servers.value.firstOrNull { it.id == serversRepo.activeId.value } ?: return
        viewModelScope.launch {
            _gatewayVerifying.value = true
            _gatewayProbe.value = try {
                ChatCompletionsClient(server).testConnection()
            } catch (error: CancellationException) {
                throw error
            } catch (error: TransportError) {
                ConnectionProbe(false, error.userMessage(), 0L, emptyList())
            } catch (error: Exception) {
                ConnectionProbe(false, error.message ?: OnyxLocalization["Server unreachable. Check the address in Settings."], 0L, emptyList())
            } finally {
                _gatewayVerifying.value = false
            }
        }
    }

    /** Ouvre un serveur pour l'éditer. */
    fun open(config: ServerConfig) {
        _draft.value = config
        _probe.value = null
    }

    fun closeEditor() {
        _draft.value = null
        _probe.value = null
    }

    /** Met à jour le brouillon ; sans effet si rien n'est ouvert. */
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

    /** « Noir pur » : les fonds de page au noir absolu en apparence sombre (OLED). */
    fun setPureBlack(value: Boolean) {
        viewModelScope.launch { settingsRepo.setPureBlack(value) }
    }

    /** « Halo » : la lumière colorée douce derrière le haut de chaque écran. */
    fun setHalo(value: Boolean) {
        viewModelScope.launch { settingsRepo.setHalo(value) }
    }

    /** « Fluide » : la couverture animée des cartes de conversation (variante B). */
    fun setFluideCover(value: Boolean) {
        viewModelScope.launch { settingsRepo.setFluideCover(value) }
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

    /** « Reconnaître hors ligne » : le seul chemin vers le hors ligne, éteint par défaut. */
    fun setOfflineRecognition(value: Boolean) {
        viewModelScope.launch { settingsRepo.setOfflineRecognition(value) }
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

    /** Le jeton du service vocal protégé ; vide revient au serveur local ouvert. */
    fun setKokoroToken(value: String) {
        viewModelScope.launch { settingsRepo.setKokoroToken(value) }
    }

    fun setKokoroVoice(value: String) {
        viewModelScope.launch { settingsRepo.setKokoroVoice(value) }
    }

    fun setReplyLanguage(code: String) {
        viewModelScope.launch { settingsRepo.setReplyLanguage(code) }
    }

    /**
     * La langue de l'interface. Le changement prend effet **tout de suite** en mémoire
     * (`SettingsRepository` l'applique avant sa première suspension) ; l'écran qui l'appelle
     * recrée ensuite l'activité pour que tout l'arbre se redessine dans la langue choisie —
     * c'est la seule façon d'atteindre les libellés déjà calculés hors du wrapper de texte.
     */
    fun setLanguage(value: String) {
        viewModelScope.launch { settingsRepo.setLanguage(value) }
    }

    fun setReasoningEffort(value: String) {
        viewModelScope.launch { settingsRepo.setReasoningEffort(value) }
    }

    fun setSuggestFollowUps(value: Boolean) {
        viewModelScope.launch { settingsRepo.setSuggestFollowUps(value) }
    }

    /** La rangée des Réglages est un interrupteur : allumé = « partout », éteint = « jamais ». */
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

    fun setWorkTracking(value: WorkTracking) {
        viewModelScope.launch { settingsRepo.setWorkTracking(value) }
    }

    fun setDisplayName(value: String) {
        viewModelScope.launch { settingsRepo.setDisplayName(value) }
    }

    /** L'écran de configuration du premier lancement a été traversé : il ne se réaffiche plus seul. */
    fun setSetupDone(value: Boolean) {
        viewModelScope.launch { settingsRepo.setSetupDone(value) }
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
     * La fenêtre de contexte d'un serveur, le champ « Context window » de `MetricsSettings` :
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
