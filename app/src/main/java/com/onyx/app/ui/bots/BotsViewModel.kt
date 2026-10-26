package com.onyx.app.ui.bots

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onyx.app.data.local.GroupRoomStore
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.BotListItem
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.data.model.serve.GroupRoom
import com.onyx.app.data.net.ActivityKind
import com.onyx.app.data.net.ActivityKinds
import com.onyx.app.data.net.ServeActivityRpc
import com.onyx.app.data.net.ServeBotsRpc
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.ServeGroupsRpc
import com.onyx.app.data.net.ServeProfilesApi
import com.onyx.app.data.net.ServeRpc
import com.onyx.app.data.net.TransportError
import com.onyx.app.data.net.visibleBotProfiles
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.conversations.GroupChat
import com.onyx.app.ui.conversations.GroupCreation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Le visage choisi pour chaque bot, gardé sur le téléphone.
 *
 * écrit le visage sur le serveur (`profiles.configure`, en WebSocket). Ce port n'a pas ce
 * canal: le choix reste donc sur l'appareil — le visage est le même partout où Onyx tourne sur
 * ce téléphone, mais pas sur un autre.
 */
class BotFaceStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("onyx_bot_faces", Context.MODE_PRIVATE)

    fun faceOf(bot: String): String? = prefs.getString(bot, null)

    fun setFace(bot: String, code: String?) {
        prefs.edit().apply {
            if (code == null) remove(bot) else putString(bot, code)
        }.apply()
    }

    /*
     * Ce qu'on écrit d'un bot depuis l'éditeur. l'écrit sur le serveur (`profiles.configure`,
     * WebSocket); ce port n'a pas ce canal, donc ces valeurs restent sur ce téléphone — comme le
     * visage.
     */
    fun titleOf(bot: String): String? = prefs.getString("title_$bot", null)

    fun aboutOf(bot: String): String? = prefs.getString("about_$bot", null)

    fun modelOf(bot: String): String? = prefs.getString("model_$bot", null)

    fun isShown(bot: String): Boolean = prefs.getBoolean("shown_$bot", true)

    /**
     * Un choix de visibilité fait **ici**, qui prime sur ce que le serveur dit du bot: `true`
     * caché, `false` montré, `null` quand le serveur décide. C'est la voie de l'écran « Hidden
     * Bots » sans Dashboard, où rien d'autre ne peut faire disparaître un bot.
     */
    fun hideOverride(bot: String): Boolean? =
        if (!prefs.contains("force_hidden_$bot")) null else prefs.getBoolean("force_hidden_$bot", false)

    fun setHideOverride(bot: String, hidden: Boolean?) {
        prefs.edit()
            .apply { if (hidden == null) remove("force_hidden_$bot") else putBoolean("force_hidden_$bot", hidden) }
            .apply()
    }

    /** Efface tout ce que ce téléphone garde des bots: visages, titres, profils ajoutés. */
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    fun saveEdit(bot: String, title: String, about: String, model: String?, shown: Boolean) {
        prefs.edit()
            .putString("title_$bot", title)
            .putString("about_$bot", about)
            .putBoolean("shown_$bot", shown)
            .apply {
                if (model == null) remove("model_$bot") else putString("model_$bot", model)
            }
            .apply()
    }

    /** Les profils ajoutés à la main (sans Dashboard pour lister le serveur). */
    fun localBots(): List<String> =
        prefs.getString("api_bots", "").orEmpty().split("\u001F").filter { it.isNotBlank() }

    /**
     * Oublie tout ce qu'on avait écrit d'un bot: son visage, son titre, sa phrase, son modèle et
     * sa visibilité.
     *
     * Sans ça, un bot supprimé puis refait sous le même nom reprendrait les mots d'avant —
     * l'éditeur n'a pas ce problème, tout vivant sur le serveur supprimé avec lui.
     */
    fun forget(bot: String) {
        val bots = localBots().toMutableList()
        bots.remove(bot)
        prefs.edit()
            .remove(bot)
            .remove("title_$bot")
            .remove("about_$bot")
            .remove("model_$bot")
            .remove("shown_$bot")
            .putString("api_bots", bots.joinToString("\u001F"))
            .apply()
    }

    fun addLocalBot(profile: String, title: String) {
        val bots = localBots().toMutableList()
        if (profile !in bots) bots += profile
        prefs.edit().putString("api_bots", bots.joinToString("\u001F")).apply()
        if (title.isNotBlank()) prefs.edit().putString("title_$profile", title).apply()
    }

    /** Le bot tel que l'éditeur l'a laissé: son titre, sa phrase, son modèle, sa visibilité. */
    fun edited(bot: BotProfile): BotProfile = bot.copy(
        title = titleOf(bot.name) ?: bot.title,
        description = aboutOf(bot.name) ?: bot.description,
        model = modelOf(bot.name) ?: bot.model,
        // Un choix fait dans « Hidden Bots » prime sur le `ui_meta` du serveur.
        isHidden = hideOverride(bot.name) ?: (!isShown(bot.name) || bot.isHidden),
    )
}

data class BotsUiState(
    val bots: List<BotProfile> = emptyList(),
    /** Les bots cachés, hors profil principal: l'écran « Hidden Bots » les montre. */
    val hidden: List<BotProfile> = emptyList(),
    val faces: Map<String, String> = emptyMap(),
    val loading: Boolean = true,
    val problem: String? = null,
    /**
     * Les salles du serveur, sous la grille: celles qu'Hermes héberge (`groups.list`) et celles
     * qu'Hermes Desktop tient de son côté (sa copie), dans l'ordre de la section.
     */
    val groups: List<GroupChat> = emptyList(),
    /** Ce Dashboard sait tenir une salle, et peut en ouvrir une maintenant. */
    val groupsSupported: Boolean = false,
    val groupsWritable: Boolean = false,
    /** Les bots cachés — ici ou dans Hermes Desktop —, à montrer de nouveau (tuile « Hidden »). */
    val hiddenBots: List<BotProfile> = emptyList(),
    /** L'ordre mis à la main (« Reorder Bots »), vide quand la section suit le plus récent. */
    val order: List<String> = emptyList(),
    /** Les lignes de la section, dans cet ordre (bots et salles mêlés: `BotListItem`). */
    val arranged: List<BotListItem> = emptyList(),
    /** Ce que le serveur dit vivant, par id de conversation (`session.active_list`). */
    val liveKinds: Map<String, ActivityKind> = emptyMap(),
    /** Ce que le serveur dit vivant dans une salle, par id de salle. */
    val roomKinds: Map<String, ActivityKind> = emptyMap(),
    /** Ce que ce téléphone a vu de chaque ligne (`bot:<profil>` / `room:<salle>`). */
    val seenMarks: Map<String, Double?> = emptyMap(),
)

/**
 * Les bots du serveur.
 *
 * Comme la section « Bots »: les profils Hermes,
 * leur visage, leur prompt (leur `SOUL.md`) et leurs horaires — plus, comme:
 * - les **salles** du serveur (`GroupDirectory.refresh`);
 * - l'**activité** que le Dashboard signale (`ChatActivity.kind(of:)` sur `session.active_list`);
 * - l'**ordre** de la section (« Reorder Bots ») et les bots **cachés**.
 */
class BotsViewModel(
    config: ServerConfig,
    private val faces: BotFaceStore,
    private val activity: BotActivityStore,
    /** Les salles gardées sur ce téléphone: un « New Group Chat » fait sans Dashboard. */
    private val groupRooms: GroupRoomStore,
) : ViewModel() {

    private var config: ServerConfig = config
    private var profiles = ServeProfilesApi(ServeClient(config))

    /**
     * Relie le modèle à la configuration courante quand l'adresse du serveur change: sans ça,
     * « Réessayer » rejouait l'ancien client (et donc l'ancienne erreur « Unable to resolve
     * host ») alors que la nouvelle adresse répondait déjà.
     */
    fun rebind(newConfig: ServerConfig) {
        if (newConfig.configSignature == config.configSignature) return
        config = newConfig
        profiles = ServeProfilesApi(ServeClient(newConfig))
        _state.update { it.copy(problem = null) }
    }

    /** La connexion à laquelle ce que l'on garde se rapporte, comme `Settings.connectionKey`. */
    private val serverKey: String get() = config.configSignature

    private val _state = MutableStateFlow(BotsUiState())
    val state: StateFlow<BotsUiState> = _state.asStateFlow()

    /**
     * Les photos des bots avec « Pictures from Hermes » allumé, par nom de profil **et** par
     * libellé (les écrans passent l'un ou l'autre à `BotAvatar`, comme `BotFace.picture` 
     * suit la face et non le nom affiché).
     */
    private val _pictures = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    val pictures: StateFlow<Map<String, ImageBitmap>> = _pictures.asStateFlow()

    /** Les profils dont la photo a déjà été demandée cette session. */
    private val askedPictures = mutableSetOf<String>()

    /** Le roster brut du serveur: les codes résolus ne sont jamais réinjectés comme des choix. */
    private var sourceBots: List<BotProfile> = emptyList()

    /** Le bot dont on vient de demander l'affichage dans « Hidden Bots ». */
    private val _showing = MutableStateFlow<String?>(null)
    val showing: StateFlow<String?> = _showing.asStateFlow()

    /** Ce qui a empêché le serveur de montrer un bot. */
    private val _showProblem = MutableStateFlow<String?>(null)
    val showProblem: StateFlow<String?> = _showProblem.asStateFlow()

    /** Les bots cachés du serveur, tels qu'il les donne (`ui_meta.hidden`). */
    private var hiddenSource: List<BotProfile> = emptyList()

    /** Les salles lues au dernier passage, avant mise en ordre. */
    private var sourceRooms: List<GroupRoom> = emptyList()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        _state.update { it.copy(loading = true, problem = null) }
        try {
            // Les profils visibles du serveur; les cachés arrivent par `readBotsByRpc`, qui les
            // met de côté pour l'écran « Hidden Bots ».
            val fromRest = if (config.hasServeCredentials) profiles.profiles() else emptyList()
            // Le RPC de `BotDirectory` contient le `canonical_session` complet: aperçu et
            // dernière activité. La route REST reste le repli des Dashboards plus anciens.
            val rpcBots = readBotsByRpc()
            val fromServer = when {
                rpcBots == null -> fromRest
                rpcBots.isEmpty() && fromRest.isNotEmpty() -> fromRest
                else -> rpcBots
            }
            val known = fromServer.map { it.name }.toSet()
            // Ajoutés à la main (`Add Bot`): sans Dashboard pour lister le serveur, c'est ici
            // qu'ils vivent — et c'est d'ici qu'ils se retirent (`Remove from Onyx` du menu).
            val extra = faces.localBots()
                .filterNot { it in known }
                .map { BotProfile(name = it, title = faces.titleOf(it), isDefault = false, viaAPI = true) }
            sourceBots = fromServer + extra
            readRoomsAndActivity()
            publish()
        } catch (error: CancellationException) {
            throw error
        } catch (error: TransportError) {
            // L'adresse réellement tentée: sans elle, « Unable to resolve host » ne dit pas
            // quel serveur a échoué (l'ancien ou le nouveau).
            val where = config.serveBase.ifBlank { config.baseUrl }.trim()
            val suffix = if (where.isEmpty()) "" else " — $where"
            _state.update { it.copy(loading = false, problem = error.userMessage() + suffix) }
        } catch (error: Exception) {
            val where = config.serveBase.ifBlank { config.baseUrl }.trim()
            val suffix = if (where.isEmpty()) "" else " — $where"
            _state.update {
                it.copy(
                    loading = false,
                    problem = (error.message ?: OnyxLocalization["Couldn't load the bots"]) + suffix,
                )
            }
        }
    }

    /**
     * Montre un bot caché, comme `HiddenBotsView.show`: d'abord sur le serveur (le
     * `ui_meta` du profil, avec la révision lue), puis, quand il n'y a pas de Dashboard pour
     * l'écrire, ici même.
     */
    fun showBot(bot: BotProfile) {
        faces.saveEdit(
            bot = bot.name,
            title = faces.titleOf(bot.name) ?: bot.title.orEmpty(),
            about = faces.aboutOf(bot.name) ?: bot.description,
            model = faces.modelOf(bot.name) ?: bot.model,
            shown = true,
        )
        writeHidden(bot, hidden = false)
        refresh()
    }

    /**
     * Le même geste, avec ce que l'écran fait quand le dernier bot caché est montré: il se ferme,
     * comme le `dismiss`.
     */
    fun showBot(bot: BotProfile, onAllShown: () -> Unit) {
        _showing.value = bot.name
        _showProblem.value = null
        viewModelScope.launch {
            try {
                if (config.hasServeCredentials) {
                    val rpc = ServeRpc(config)
                    try {
                        // La révision lue accompagne l'écriture — Hermes refuse un `ui_meta`
                        // changé entre-temps; sans révision lue, la signature telle quelle.
                        val revision = bot.setUpRevision
                        if (revision > 0) {
                            ServeBotsRpc(rpc).setHidden(bot.name, hidden = false, revision = revision)
                        } else {
                            ServeBotsRpc(rpc).setHidden(bot.name, hidden = false)
                        }
                    } finally {
                        rpc.close()
                    }
                }
                faces.setHideOverride(bot.name, false)
                reload()
                if (_state.value.hidden.isEmpty()) onAllShown()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _showProblem.value = (error as? TransportError)?.userMessage()
                    ?: error.message
                    ?: OnyxLocalization["Couldn't show the bot"]
            } finally {
                _showing.value = null
            }
        }
    }

    /** Republie ce qui est affiché quand seuls les visages ont changé (choix d'un visage). */
    private fun publishFaces() = publish()

    /**
     * Récupère les photos des bots qui en ont une dans Hermes Desktop (`profiles.get_asset`),
     * comme `BotPictures.load`: une fois par lancement et par profil. Sans Dashboard
     * joignable, il n'y a rien à faire: les visages restent dessinés.
     */
    fun loadPictures() {
        if (!config.hasServeCredentials) return
        val targets = _state.value.bots.filter { it.hasPicture && askedPictures.add(it.name) }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            val rpc = ServeRpc(config)
            try {
                val bots = ServeBotsRpc(rpc)
                val found = mutableMapOf<String, ImageBitmap>()
                for (bot in targets) {
                    val bytes = runCatching { bots.botPicture(bot.name) }.getOrNull() ?: continue
                    val bitmap = runCatching {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }.getOrNull() ?: continue
                    // Le nom du profil et le libellé affiché mènent à la même photo.
                    found[bot.name] = bitmap
                    found[bot.label(config.displayName)] = bitmap
                }
                if (found.isNotEmpty()) _pictures.update { it + found }
            } finally {
                rpc.close()
            }
        }
    }

    /** Le roster par le RPC (`profiles.list`), ou `null` si le Dashboard ne répond pas. */
    private suspend fun readBotsByRpc(): List<BotProfile>? {
        if (!config.hasServeCredentials) return null
        val rpc = ServeRpc(config)
        return try {
            val all = ServeBotsRpc(rpc).profiles()
            // Les bots cachés d'Hermes Desktop: `BotRoster` les écarte de sa liste, mais la tuile
            // « Hidden » les compte et les montre.
            hiddenSource = all.filter { it.isHidden && !it.isDefault }
            all.visibleBotProfiles(config.displayName)
        } catch (error: TransportError) {
            null
        } finally {
            rpc.close()
        }
    }

    /**
     * Les salles et l'activité, lues sur le même Dashboard: `groups.capabilities` dit si ce
     * serveur sait tenir une salle, `groups.list` et la copie d'Hermes Desktop les donnent, et
     * `session.active_list` dit ce qui travaille en ce moment.
     *
     * Les salles **gardées sur ce téléphone** (un « New Group Chat » fait sans Dashboard) se lisent
     * toujours, avec ou sans serveur: elles survivent ainsi à une relance, comme celles du serveur.
     */
    private suspend fun readRoomsAndActivity() {
        val kept = keptRooms()
        if (!config.hasServeCredentials) {
            hiddenSource = emptyList()
            sourceRooms = kept
            _state.update { it.copy(groupsSupported = false, groupsWritable = false, liveKinds = emptyMap(), roomKinds = emptyMap()) }
            return
        }
        val rpc = ServeRpc(config)
        try {
            val groups = ServeGroupsRpc(rpc)
            val support = groups.support()
            val hosted = if (support.supported) groups.rooms() else emptyList()
            val desktop = groups.desktopRooms()
            sourceRooms = (hosted + desktop + kept).sortedByDescending { it.updatedAtMillis ?: Long.MIN_VALUE }
            val kinds = try {
                ServeActivityRpc(rpc).activeKinds()
            } catch (_: TransportError) {
                ActivityKinds()
            }
            _state.update {
                it.copy(
                    groupsSupported = support.supported,
                    groupsWritable = support.writable,
                    liveKinds = kinds.sessions,
                    roomKinds = kinds.rooms,
                )
            }
        } catch (_: TransportError) {
            // Sans salles ni activité, la grille tient encore; les pastilles locales aussi.
            sourceRooms = kept
            _state.update { it.copy(groupsSupported = false, groupsWritable = false, liveKinds = emptyMap(), roomKinds = emptyMap()) }
        } finally {
            rpc.close()
        }
    }

    /** Les salles que ce téléphone garde pour cette connexion (`GroupRoomStore`). */
    private fun keptRooms(): List<GroupRoom> = groupRooms.rooms(serverKey)

    /**
     * « New Group Chat »: la salle de ces bots, nommée ou non, faite **maintenant**.
     *
     * Elle est ouverte sur le serveur quand la connexion porte des identifiants de Dashboard
     * (`groups.create`, le corps d'appel du modèle); sans Dashboard, elle est gardée sur ce
     * téléphone, avec ses membres, et se retrouve après une relance.
     *
     * Le même nom ne fait jamais deux salles: celle qui est déjà là revient. Un échec rend ce qui
     * l'a causé et **rien** n'est gardé — jamais une salle à moitié faite, jamais un succès muet.
     */
    suspend fun createGroup(
        name: String,
        members: Set<String>,
        /** Le nom que Onyx donne au profil principal: les membres d'une salle en héritent. */
        mainName: String = config.displayName.ifBlank { "Onyx" },
    ): GroupCreation {
        val made = createGroupRoom(
            config = config,
            bots = _state.value.bots,
            known = sourceRooms,
            store = groupRooms,
            serverKey = serverKey,
            name = name,
            members = members,
            mainName = mainName,
        )
        // La liste se relit: la salle faite y prend sa place, comme si le serveur l'avait servie.
        if (made is GroupCreation.Made) reload()
        return made
    }

    /**
     * La section telle qu'elle doit s'afficher: les bots et les salles dans l'ordre enregistré
     * (`BotListItem.arranged`), les visages résolus, les bots cachés à part, et ce que ce
     * téléphone a vu de chaque ligne.
     */
    private fun publish() {
        val order = activity.order(serverKey)
        // Le roster entier, cachés du serveur compris, tel que l'éditeur l'a laissé: les visages
        // résolus, et le choix de visibilité fait ici qui prime sur le `ui_meta`.
        val roster = (sourceBots + hiddenSource).distinctBy { it.name }
        val local = roster.associate { it.name to faces.faceOf(it.name) }
        val resolved = roster.withOnyxFaces(local).map { faces.edited(it) }
        val hidden = resolved.filter { bot -> bot.isHidden && !bot.isDefault }
        // La section montre les autres, dans l'ordre enregistré, les salles mêlées aux bots.
        val arranged = BotListItem.arranged(bots = resolved.filterNot { it.isHidden }, rooms = sourceRooms, order = order)

        val visible = arranged.filterIsInstance<BotListItem.Bot>().map { it.profile }
        val codes = resolved.mapNotNull { bot -> bot.faceCode?.let { bot.name to it } }.toMap()
        val groups = arranged.filterIsInstance<BotListItem.Room>().map { roomChat(it.room, visible) }

        // Les lignes vues pour la première fois comptent comme lues: le premier regard sur la
        // liste ne l'allume pas entière (`ChatActivity.listed`). Une salle sans marque d'activité
        // (jamais servie) n'est pas enregistrée.
        activity.listed(serverKey, visible.map { BotListItem.Bot(it).id to it.lastActive?.toDouble() }, marksWithoutActivity = true)
        activity.listed(serverKey, groups.map { "room:" + it.id to it.activityMark }, marksWithoutActivity = false)

        val marks = (visible.map { BotListItem.Bot(it).id } + groups.map { "room:" + it.id })
            .associateWith { activity.seen(serverKey, it) }

        _state.update {
            it.copy(
                bots = visible,
                faces = codes,
                groups = groups,
                hidden = hidden,
                hiddenBots = hidden,
                order = order,
                arranged = arranged,
                seenMarks = marks,
                loading = false,
            )
        }
    }

    /** Les pastilles de la section: le serveur pour l'activité, ce téléphone pour le non-lu. */
    fun badges(): BotBadges = BotBadges(
        liveKinds = _state.value.liveKinds,
        roomKinds = _state.value.roomKinds,
        seenMarks = _state.value.seenMarks,
    )

    /** Ajoute un bot nommé à la main, puis relit la liste. */
    fun addLocalBot(profile: String, title: String) {
        faces.addLocalBot(profile, title)
        refresh()
    }

    /** Change le modèle d'un bot: sur le serveur quand c'est possible, ici sinon. */
    fun setModel(bot: BotProfile, model: String?) {
        if (config.hasServeCredentials) {
            viewModelScope.launch {
                runCatching {
                    val rpc = ServeRpc(config)
                    ServeBotsRpc(rpc).setModel(bot.name, model)
                    rpc.close()
                }
            }
        }
        faces.saveEdit(
            bot = bot.name,
            title = faces.titleOf(bot.name) ?: bot.title.orEmpty(),
            about = faces.aboutOf(bot.name) ?: bot.description,
            model = model,
            shown = faces.isShown(bot.name),
        )
        refresh()
    }


    fun setFace(bot: BotProfile, code: String?) {
        // Le visage part sur le serveur quand le Dashboard est joignable; sinon il reste ici.
        if (config.hasServeCredentials) {
            viewModelScope.launch {
                runCatching {
                    val rpc = ServeRpc(config)
                    ServeBotsRpc(rpc).setFace(bot.name, code, revision = 0)
                    rpc.close()
                }
            }
        }
        faces.setFace(bot.name, code)
        sourceBots = sourceBots.map { current ->
            if (current.name == bot.name) current.copy(faceCode = code) else current
        }
        publishFaces()
    }

    fun faceFor(bot: BotProfile): String? = _state.value.faces[bot.name] ?: bot.faceCode

    /** Vrai seulement pour un choix enregistré, pas pour la créature calculée du roster. */
    fun hasPickedFace(bot: BotProfile): Boolean =
        faces.faceOf(bot.name) != null || sourceBots.firstOrNull { it.name == bot.name }?.faceCode != null

    /**
     * « Hide » du menu d'une tuile: le bot quitte la liste, ici **et** sur le serveur quand le
     * Dashboard est joignable.
     *
     * Comme `hide(_:)changeBot(hidden: true)`), avec la même
     * écriture que le bouton « Hidden » de l'éditeur (`writeHidden`) et le choix local qui prime
     * quand le serveur ne répond pas.
     */
    fun hideBot(bot: BotProfile) {
        faces.setHideOverride(bot.name, true)
        writeHidden(bot, hidden = true)
        refresh()
    }

    /**
     * « Remove from Onyx » du menu, sur un bot ajouté à la main (`viaAPI`): il ne reste plus rien
     * de lui sur ce téléphone — son profil, son visage, ses mots, sa visibilité.
     *
     * Comme `removeAPIBot(_:)`: retire le profil de
     * `settings.apiBotsHere` et sa clé du trousseau; ici tout ce que Onyx gardait de lui vit dans
     * `BotFaceStore` (`forget`), et la liste est relue pour qu'il disparaisse.
     */
    fun removeAPIBot(bot: BotProfile) {
        faces.forget(bot.name)
        sourceBots = sourceBots.filterNot { it.name == bot.name }
        refresh()
    }

    /** Enregistre ce que l'éditeur a écrit, puis relit la liste telle qu'elle doit s'afficher. */
    fun saveEdit(bot: BotProfile, title: String, about: String, model: String?, shown: Boolean) {
        faces.saveEdit(bot.name, title, about, model, shown)
        // « Hidden » fait partie des réglages d'Hermes Desktop: masquer un bot ici le masque
        // pour tous les appareils, comme `changeBot(BotChange(hidden:))`.
        writeHidden(bot, hidden = !shown)
        refresh()
    }

    /** Le prompt d'un bot, c'est son `SOUL.md`: l'éditeur de contexte sait déjà l'ouvrir. */
    fun soulPath(bot: BotProfile): String = ".hermes/SOUL.md"

    /**
     * Supprime un bot sur le serveur — avec lui ses chats, ses mémoires et ses réglages.
     *
     * Le profil s'en va par la route REST du Dashboard. Rien n'est
     * retiré de la liste, et l'écran ne navigue pas, tant que le serveur n'a pas répondu: un
     * échec laisse le bot tel quel.
     *
     * `null`: c'est fait. Sinon: ce qui l'a empêché, prêt à être montré.
     */
    suspend fun deleteBot(bot: BotProfile): String? {
        // Le profil principal est l'agent de la personne: ne le supprime pas non plus.
        if (bot.isDefault) return OnyxLocalization["The main profile can't be deleted."]
        if (!config.hasServeCredentials) return OnyxLocalization["Needs the Hermes Dashboard"]
        return try {
            profiles.deleteProfile(bot.name)
            // Ce qu'on avait écrit de lui sur ce téléphone ne doit pas survivre à sa suppression.
            faces.forget(bot.name)
            sourceBots = sourceBots.filterNot { it.name == bot.name }
            // Un bot caché vit dans `hiddenSource`: le supprimer doit aussi le retirer de la
            // liste des cachés, sans attendre la relecture du serveur.
            hiddenSource = hiddenSource.filterNot { it.name == bot.name }
            publishFaces()
            null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            error.said()
        }
    }

    /**
     * « Let bots message each other »: le profil principal est installé comme un bot du serveur,
     * ce qui donne à chaque chat de bot l'outil d'écrire aux autres.
     *
     * Comme `ConversationsView.letBotsMessageEachOther`:
     * l'erreur remonte au lieu d'être avalée, et la liste est relue pour que la ligne disparaisse
     * une fois le réglage posé.
     */
    suspend fun letBotsMessage(): String? {
        if (!config.hasServeCredentials) return OnyxLocalization["Needs the Hermes Dashboard"]
        val main = sourceBots.firstOrNull { it.isDefault } ?: sourceBots.firstOrNull()
            ?: return OnyxLocalization["No bots yet"]
        return try {
            val rpc = ServeRpc(config)
            try {
                ServeBotsRpc(rpc).letBotsMessageEachOther(main.name)
            } finally {
                rpc.close()
            }
            refresh()
            null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val message = OnyxLocalization.format("Couldn't set that up: %1\$s", error.said())
            // La liste porte l'échec, comme le `ledgerError`: la ligne « Try Again » y
            // est déjà, et rien ne laisse croire que le réglage a été posé.
            _state.update { it.copy(problem = message) }
            message
        }
    }

    /** Le texte d'une erreur, tel que l'app le montre ailleurs. */
    private fun Exception.said(): String = when (this) {
        is TransportError -> userMessage()
        else -> message ?: OnyxLocalization["Couldn't load the bots"]
    }

    // MARK: - Reorder Bots

    /**
     * Une ligne glissée à une autre place: la section garde cet ordre désormais
     * (`Settings.botOrderHere` ), les lignes qui ne sont pas montrées restant à la fin.
     */
    fun move(from: Int, to: Int) {
        val shown = _state.value.arranged
        val order = BotListItem.orderAfter(shown = shown, from = from, to = to, kept = _state.value.order)
        activity.setOrder(serverKey, order)
        publish()
    }

    /** « Sort Bots by Recent »: la section revient au plus récent d'abord. */
    fun sortByRecent() {
        activity.setOrder(serverKey, emptyList())
        publish()
    }

    /**
     * La visibilité d'un bot part sur le serveur (`profiles.configure`) quand le Dashboard est
     * joignable — avec la révision lue, Hermes refusant un `ui_meta` changé entre-temps. La liste
     * locale, elle, suit sans attendre.
     */
    private fun writeHidden(bot: BotProfile, hidden: Boolean) {
        if (!config.hasServeCredentials) return
        viewModelScope.launch {
            runCatching {
                val rpc = ServeRpc(config)
                try {
                    ServeBotsRpc(rpc).setHidden(bot.name, hidden = hidden, revision = bot.setUpRevision)
                } finally {
                    rpc.close()
                }
            }
        }
    }

    // MARK: - Ce qui a été vu

    /** Le chat du bot a été ouvert: tout ce qu'il a dit est vu. */
    fun markBotSeen(bot: BotProfile) {
        val mark = bot.lastActive?.let { it / 1000.0 } ?: (System.currentTimeMillis() / 1000.0)
        activity.markSeen(serverKey, BotListItem.Bot(bot).id, mark)
        publishMarks()
    }

    /** « Mark as Unread » sur la tuile d'un bot. */
    fun markBotUnread(bot: BotProfile) {
        activity.markUnread(serverKey, BotListItem.Bot(bot).id)
        publishMarks()
    }

    /** Une salle a été regardée jusqu'à son dernier événement. */
    fun markRoomSeen(room: GroupChat) {
        val mark = room.activityMark ?: return
        activity.markSeen(serverKey, "room:" + room.id, mark)
        publishMarks()
    }

    /** « Mark as Unread » sur la rangée d'une salle. */
    fun markRoomUnread(room: GroupChat) {
        activity.markUnread(serverKey, "room:" + room.id)
        publishMarks()
    }

    private fun publishMarks() {
        val ids = _state.value.bots.map { BotListItem.Bot(it).id } + _state.value.groups.map { "room:" + it.id }
        val marks = ids.associateWith { activity.seen(serverKey, it) }
        _state.update { it.copy(seenMarks = marks) }
    }
}
