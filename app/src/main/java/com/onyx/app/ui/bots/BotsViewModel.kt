package com.onyx.app.ui.bots

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onyx.app.data.local.BotRosterCache
import com.onyx.app.data.local.CachedRoster
import com.onyx.app.data.local.FaceKeys
import com.onyx.app.data.local.GroupRoomStore
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.BotListItem
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.data.model.serve.GroupRoom
import com.onyx.app.data.net.ActivityKind
import com.onyx.app.data.net.ActivityKinds
import com.onyx.app.data.net.ChatSessionActions
import com.onyx.app.data.net.ServeActivityRpc
import com.onyx.app.data.net.ServeBotsRpc
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.ServeFilesApi
import com.onyx.app.data.net.ServeGroupsRpc
import com.onyx.app.data.net.ServeProfilesApi
import com.onyx.app.data.net.ServeRpc
import com.onyx.app.data.net.ServeSkillsApi
import com.onyx.app.data.net.TransportError
import com.onyx.app.data.net.humanFailure
import com.onyx.app.data.net.visibleBotProfiles
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.chat.BoundedImages
import com.onyx.app.ui.conversations.GroupChat
import com.onyx.app.ui.conversations.GroupCreation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The face chosen for each bot, kept on the phone.
 *
 * the reference writes the face to the server (`profiles.configure`, over WebSocket). This port does
 * not have that channel: the choice therefore stays on the device — the face is the same anywhere Onyx runs on
 * this phone, but not on another one.
 */
class BotFaceStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FaceKeys.FILE, Context.MODE_PRIVATE)

    /**
     * This store's keys live in [FaceKeys]: it is the same map that a profile rename migration reads
     * ([com.onyx.app.data.local.ProfileStateMigration]), a single source of truth for what the phone keeps
     * **per profile name**.
     */
    fun faceOf(bot: String): String? = prefs.getString(FaceKeys.face(bot), null)

    fun setFace(bot: String, code: String?) {
        prefs.edit().apply {
            if (code == null) remove(FaceKeys.face(bot)) else putString(FaceKeys.face(bot), code)
        }.apply()
    }

    /*
     * What we write about a bot from the editor. The reference writes it to the server (`profiles.configure`,
     * WebSocket); this port does not have that channel, so these values stay on this phone — like the
     * face.
     */
    fun titleOf(bot: String): String? = prefs.getString(FaceKeys.title(bot), null)

    fun aboutOf(bot: String): String? = prefs.getString(FaceKeys.about(bot), null)

    fun modelOf(bot: String): String? = prefs.getString(FaceKeys.model(bot), null)

    fun isShown(bot: String): Boolean = prefs.getBoolean(FaceKeys.shown(bot), true)

    /**
     * A visibility choice made **here**, which takes precedence over what the server says about the bot: `true`
     * hidden, `false` shown, `null` when the server decides. This is the path of the "Hidden
     * Bots" screen without a Dashboard, where nothing else can make a bot disappear.
     */
    fun hideOverride(bot: String): Boolean? =
        if (!prefs.contains(FaceKeys.hideOverride(bot))) null else prefs.getBoolean(FaceKeys.hideOverride(bot), false)

    fun setHideOverride(bot: String, hidden: Boolean?) {
        val key = FaceKeys.hideOverride(bot)
        prefs.edit()
            .apply { if (hidden == null) remove(key) else putBoolean(key, hidden) }
            .apply()
    }

    /**
     * "Pin to top" ("Épingler en haut") done **here**, which takes precedence over the server's `ui_meta`: `true` pinned,
     * `false` unpinned, `null` when the server decides. Same map as [hideOverride] — this is what
     * keeps the choice **offline**, and what the list rereads on each launch.
     */
    fun pinOverride(bot: String): Boolean? =
        if (!prefs.contains(FaceKeys.pinOverride(bot))) null else prefs.getBoolean(FaceKeys.pinOverride(bot), false)

    fun setPinOverride(bot: String, pinned: Boolean?) {
        val key = FaceKeys.pinOverride(bot)
        prefs.edit()
            .apply { if (pinned == null) remove(key) else putBoolean(key, pinned) }
            .apply()
    }

    /** Erases everything this phone keeps about bots: faces, titles, added profiles. */
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    fun saveEdit(bot: String, title: String, about: String, model: String?, shown: Boolean) {
        prefs.edit()
            .putString(FaceKeys.title(bot), title)
            .putString(FaceKeys.about(bot), about)
            .putBoolean(FaceKeys.shown(bot), shown)
            .apply {
                if (model == null) remove(FaceKeys.model(bot)) else putString(FaceKeys.model(bot), model)
            }
            .apply()
    }

    /** The profiles added by hand (without a Dashboard to list the server). */
    fun localBots(): List<String> =
        prefs.getString(FaceKeys.API_BOTS, "").orEmpty().split(FaceKeys.SEPARATOR).filter { it.isNotBlank() }

    /**
     * Forgets everything we had written about a bot: its face, its title, its sentence, its model and
     * its visibility.
     *
     * Without this, a bot deleted then remade under the same name would take back the old words —
     * the reference editor does not have this problem, everything living on the server deleted with it.
     */
    fun forget(bot: String) {
        val bots = localBots().toMutableList()
        bots.remove(bot)
        prefs.edit()
            .remove(FaceKeys.face(bot))
            .remove(FaceKeys.title(bot))
            .remove(FaceKeys.about(bot))
            .remove(FaceKeys.model(bot))
            .remove(FaceKeys.shown(bot))
            .remove(FaceKeys.pinOverride(bot))
            .putString(FaceKeys.API_BOTS, bots.joinToString(FaceKeys.SEPARATOR))
            .apply()
    }

    fun addLocalBot(profile: String, title: String) {
        val bots = localBots().toMutableList()
        if (profile !in bots) bots += profile
        prefs.edit().putString(FaceKeys.API_BOTS, bots.joinToString(FaceKeys.SEPARATOR)).apply()
        if (title.isNotBlank()) prefs.edit().putString(FaceKeys.title(profile), title).apply()
    }

    /** The bot as the editor left it: its title, its sentence, its model, its visibility. */
    fun edited(bot: BotProfile): BotProfile = bot.copy(
        title = titleOf(bot.name) ?: bot.title,
        description = aboutOf(bot.name) ?: bot.description,
        model = modelOf(bot.name) ?: bot.model,
        // A choice made in "Hidden Bots" takes precedence over the server's `ui_meta`.
        isHidden = hideOverride(bot.name) ?: (!isShown(bot.name) || bot.isHidden),
        // "Pin to top": same rule, same map — the phone's choice takes precedence over `ui_meta`.
        pinned = pinOverride(bot.name) ?: bot.pinned,
    )
}

data class BotsUiState(
    val bots: List<BotProfile> = emptyList(),
    /** The hidden bots, excluding the main profile: the "Hidden Bots" screen shows them. */
    val hidden: List<BotProfile> = emptyList(),
    val faces: Map<String, String> = emptyMap(),
    val loading: Boolean = true,
    val problem: String? = null,
    /**
     * The server's rooms, below the grid: those Hermes hosts (`groups.list`) and those
     * Hermes Desktop keeps on its side (its copy), in the order of the section.
     */
    val groups: List<GroupChat> = emptyList(),
    /** This Dashboard knows how to host a room, and can open one now. */
    val groupsSupported: Boolean = false,
    val groupsWritable: Boolean = false,
    /** The hidden bots — here or in Hermes Desktop —, to be shown again (the "Hidden" tile). */
    val hiddenBots: List<BotProfile> = emptyList(),
    /** The order set by hand ("Reorder Bots"), empty when the section follows the most recent. */
    val order: List<String> = emptyList(),
    /** The section's rows, in that order (bots and rooms mixed: `BotListItem`). */
    val arranged: List<BotListItem> = emptyList(),
    /** What the server reports as live, per conversation id (`session.active_list`). */
    val liveKinds: Map<String, ActivityKind> = emptyMap(),
    /** What the server reports as live in a room, per room id. */
    val roomKinds: Map<String, ActivityKind> = emptyMap(),
    /** What this phone has seen of each row (`bot:<profile>` / `room:<room>`). */
    val seenMarks: Map<String, Double?> = emptyMap(),
)

/**
 * The server's bots.
 *
 * Mirror of the "Bots" section ("Bots") of `ConversationsView` and of `BotEditor`: the Hermes profiles,
 * their face, their prompt (their `SOUL.md`) and their schedules — plus, like the reference:
 * - the server's **rooms** (`GroupDirectory.refresh`);
 * - the **activity** the Dashboard reports (`ChatActivity.kind(of:)` on `session.active_list`);
 * - the section's **order** ("Reorder Bots") and the **hidden** bots.
 */
class BotsViewModel(
    config: ServerConfig,
    private val faces: BotFaceStore,
    private val activity: BotActivityStore,
    /** The rooms kept on this phone: a "New Group Chat" made without a Dashboard. */
    private val groupRooms: GroupRoomStore,
    /**
     * The last successful roster, kept on this phone. It **precedes** the network by one frame: without
     * it, on restart, the list is empty when the screen draws, and the open conversation
     * showed up as Onyx (blue face, "@default") before jumping to its real bot.
     */
    private val roster: BotRosterCache,
) : ViewModel() {

    private var config: ServerConfig = config
    private var profiles = ServeProfilesApi(ServeClient(config))

    /**
     * Rebinds the model to the current configuration when the server address changes: without that,
     * "Retry" ("Réessayer") replayed the old client (and therefore the old "Unable to resolve
     * host" error) while the new address was already responding.
     */
    fun rebind(newConfig: ServerConfig) {
        if (newConfig.configSignature == config.configSignature) return
        config = newConfig
        profiles = ServeProfilesApi(ServeClient(newConfig))
        _state.update { it.copy(problem = null) }
    }

    /**
     * The scope of the kept roster: **the server**, not the whole configuration.
     *
     * The roster says who a server's bots are — the same ones, whatever model serves
     * them. The key is therefore [ServerConfig.rosterScope], and not the whole signature: with that one,
     * choosing a model made the roster lost, and the screen fell back to Onyx (blue face)
     * while the network answered.
     */
    private val serverKey: String get() = config.rosterScope

    private val _state = MutableStateFlow(BotsUiState())
    val state: StateFlow<BotsUiState> = _state.asStateFlow()

    /**
     * The pictures of bots with "Pictures from Hermes" turned on, by profile name **and** by
     * label (screens pass one or the other to `BotAvatar`, just as the reference's `BotFace.picture`
     * follows the face and not the displayed name).
     */
    private val _pictures = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    val pictures: StateFlow<Map<String, ImageBitmap>> = _pictures.asStateFlow()

    /** The profiles whose picture has already been requested this session. */
    private val askedPictures = mutableSetOf<String>()

    /** The server's raw roster: resolved codes are never reinjected as choices. */
    private var sourceBots: List<BotProfile> = emptyList()

    /** The bot whose display we just requested in "Hidden Bots". */
    private val _showing = MutableStateFlow<String?>(null)
    val showing: StateFlow<String?> = _showing.asStateFlow()

    /** What prevented the server from showing a bot. */
    private val _showProblem = MutableStateFlow<String?>(null)
    val showProblem: StateFlow<String?> = _showProblem.asStateFlow()

    /** The server's hidden bots, as it gives them (`ui_meta.hidden`). */
    private var hiddenSource: List<BotProfile> = emptyList()

    /** The rooms read on the last pass, before ordering. */
    private var sourceRooms: List<GroupRoom> = emptyList()

    init {
        // **The last known roster holds the first frame.** Without it, `bots` is empty when
        // the screen draws (the network has not answered), the open conversation cannot find its
        // bot and the header shows Onyx — blue face, "@default" — before jumping. The read is
        // local and synchronous: it is there before any composition.
        //
        // What is reread is not the server's **raw** roster, but the one that was **displayed**:
        // the faces there are the *effective* faces — those the roster's allocation
        // assigned. The name alone does not give them back (`forName("coder")` draws a different palette than
        // the `coder` of a six-bot roster), and neither does the server's raw code: without the rendered table,
        // the bot would find another creature on restart than the one from yesterday.
        val remembered = roster.load(serverKey)
        if (remembered != null) {
            _state.update { it.copy(bots = remembered.bots, faces = remembered.faces, loading = false) }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { reload() }
    }

    /**
     * Erases a failure's words, once read.
     *
     * This is the one the *Bots* slot of the drawer shows ("No free name for the copy" / "Aucun nom libre pour la copie."), and
     * that a tap erases — the same words the "Bots" page shows, in a single place, so that
     * a failure is not said twice in two ways.
     */
    fun dismissProblem() = _state.update { it.copy(problem = null) }

    private suspend fun reload() {
        _state.update { it.copy(loading = true, problem = null) }
        try {
            // A single `ServeRpc` WebSocket to read the profiles, the rooms and the
            // activity at once; the REST route `profiles.profiles()` is only a fallback if the profiles
            // RPC fails or comes back empty.
            val rpc = if (config.hasServeCredentials) ServeRpc(config) else null
            val rpcBots: List<BotProfile>?
            try {
                rpcBots = readBotsByRpc(rpc)
                val fromServer = if (!rpcBots.isNullOrEmpty()) {
                    rpcBots
                } else {
                    val fromRest = if (config.hasServeCredentials) profiles.profiles() else emptyList()
                    if (rpcBots == null || fromRest.isNotEmpty()) fromRest else rpcBots
                }
                val known = fromServer.map { it.name }.toSet()
                // Added by hand (`Add Bot`): without a Dashboard to list the server, this is where
                // they live — and this is where they are removed from (`Remove from Onyx` in the menu).
                val extra = faces.localBots()
                    .filterNot { it in known }
                    .map { BotProfile(name = it, title = faces.titleOf(it), isDefault = false, viaAPI = true) }
                sourceBots = fromServer + extra
                // The rooms and the activity are an extra: if the Dashboard does not answer, the
                // roster already read must stay displayed. Without this guard, a failure here erased the
                // list of bots — the app looked empty while the server was answering.
                runCatching { readRoomsAndActivity(rpc) }
            } finally {
                rpc?.close()
            }
            publish()
        } catch (error: CancellationException) {
            throw error
        } catch (error: TransportError) {
            // The address actually tried: without it, "Unable to resolve host" does not say
            // which server failed (the old or the new one).
            val where = config.serveBase.ifBlank { config.baseUrl }.trim()
            val suffix = if (where.isEmpty()) "" else " — $where"
            _state.update { it.copy(loading = false, problem = error.userMessage() + suffix) }
        } catch (error: Exception) {
            val where = config.serveBase.ifBlank { config.baseUrl }.trim()
            val suffix = if (where.isEmpty()) "" else " — $where"
            _state.update {
                it.copy(
                    loading = false,
                    problem = (humanFailure(error, OnyxLocalization["Couldn't load the bots"])) + suffix,
                )
            }
        }
    }

    /**
     * Shows a hidden bot, like `HiddenBotsView.show` on the reference side: first on the server (the
     * profile's `ui_meta`, with the revision read), then, when there is no Dashboard to
     * write it, right here.
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
     * The same gesture, with what the screen does when the last hidden bot is shown: it closes,
     * like the reference's `dismiss()`.
     */
    fun showBot(bot: BotProfile, onAllShown: () -> Unit) {
        _showing.value = bot.name
        _showProblem.value = null
        viewModelScope.launch {
            try {
                if (config.hasServeCredentials) {
                    val rpc = ServeRpc(config)
                    try {
                        // The revision read accompanies the write — Hermes refuses a `ui_meta`
                        // changed in the meantime; without a revision read, the reference's signature as is.
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
                _showProblem.value = humanFailure(error, OnyxLocalization["Couldn't show the bot"])
            } finally {
                _showing.value = null
            }
        }
    }

    /** Republishes what is displayed when only the faces changed (choice of a face). */
    private fun publishFaces() = publish()

    /**
     * Fetches the pictures of bots that have one in Hermes Desktop (`profiles.get_asset`),
     * like `BotPictures.load` on the reference side: once per launch and per profile. Without a reachable
     * Dashboard, there is nothing to do: the faces stay drawn.
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
                    // **Decoded bounded.** A profile avatar arrives at 1024×1024 (4 MB in
                    // ARGB_8888) while the only thing we do with it is a 108 px tile:
                    // decoded at full resolution and kept for the life of the process, twenty bots
                    // weighed ~80 MB of heap. `BoundedImages` reads the header, chooses the factor and
                    // never builds the whole image.
                    val bitmap = BoundedImages.decodeBytes(bytes, maxPx = AVATAR_PX)
                        ?.asImageBitmap() ?: continue
                    // The profile name and the displayed label lead to the same picture (two keys, one
                    // bitmap: a map only pays for the reference).
                    found[bot.name] = bitmap
                    found[bot.label(config.displayName)] = bitmap
                }
                if (found.isNotEmpty()) {
                    _pictures.update { it + found }
                    // The long-press menu tiles carry the picture as soon as it is there: the
                    // section is republished with it, otherwise the tile would stay at the bot's initial
                    // while its picture has arrived.
                    republishShortcuts()
                }
            } finally {
                rpc.close()
            }
        }
    }

    /** The roster through RPC (`profiles.list`), or `null` if the Dashboard does not answer. */
    private suspend fun readBotsByRpc(sharedRpc: ServeRpc? = null): List<BotProfile>? {
        if (!config.hasServeCredentials) return null
        val rpc = sharedRpc ?: ServeRpc(config)
        return try {
            val all = ServeBotsRpc(rpc).profiles()
            // Hermes Desktop's hidden bots: `BotRoster` leaves them out of its list, but the
            // "Hidden" tile counts them and shows them.
            hiddenSource = all.filter { it.isHidden && !it.isDefault }
            all.visibleBotProfiles(config.displayName)
        } catch (error: TransportError) {
            null
        } finally {
            if (sharedRpc == null) rpc.close()
        }
    }

    /**
     * The rooms and the activity, read on the same Dashboard: `groups.capabilities` says whether this
     * server knows how to host a room, `groups.list` and the Hermes Desktop copy give them, and
     * `session.active_list` says what is working right now.
     *
     * The rooms **kept on this phone** (a "New Group Chat" made without a Dashboard) are always
     * read, with or without a server: they therefore survive a relaunch, like the server's ones.
     */
    private suspend fun readRoomsAndActivity(sharedRpc: ServeRpc? = null) {
        val kept = keptRooms()
        if (!config.hasServeCredentials) {
            hiddenSource = emptyList()
            sourceRooms = kept
            _state.update { it.copy(groupsSupported = false, groupsWritable = false, liveKinds = emptyMap(), roomKinds = emptyMap()) }
            return
        }
        val rpc = sharedRpc ?: ServeRpc(config)
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
            // Without rooms or activity, the grid still holds; so do the local dots.
            sourceRooms = kept
            _state.update { it.copy(groupsSupported = false, groupsWritable = false, liveKinds = emptyMap(), roomKinds = emptyMap()) }
        } finally {
            if (sharedRpc == null) rpc.close()
        }
    }

    /** The rooms this phone keeps for this connection (`GroupRoomStore`). */
    private fun keptRooms(): List<GroupRoom> = groupRooms.rooms(serverKey)

    /**
     * "New Group Chat": the room of these bots, named or not, made **now**.
     *
     * It is opened on the server when the connection carries Dashboard credentials
     * (`groups.create`, the reference's call body); without a Dashboard, it is kept on this
     * phone, with its members, and is found again after a relaunch.
     *
     * The same name never makes two rooms: the one already there comes back. A failure returns what
     * caused it and **nothing** is kept — never a half-made room, never a silent success.
     */
    suspend fun createGroup(
        name: String,
        members: Set<String>,
        /** The name Onyx gives the main profile: a room's members inherit it. */
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
        // The list is reread: the made room takes its place there, as if the server had served it.
        if (made is GroupCreation.Made) reload()
        return made
    }

    /**
     * The section as it must be displayed: the bots and the rooms in the recorded order
     * (`BotListItem.arranged`), the resolved faces, the hidden bots separately, and what this
     * phone has seen of each row.
     */
    private fun publish() {
        val order = activity.order(serverKey)
        // The whole roster, including the server's hidden ones, as the editor left it: the faces
        // resolved, and the visibility choice made here which takes precedence over `ui_meta`.
        val rosterBots = (sourceBots + hiddenSource).distinctBy { it.name }
        val local = rosterBots.associate { it.name to faces.faceOf(it.name) }
        val resolved = rosterBots.withSwiftFaces(local).map { faces.edited(it) }
        val hidden = resolved.filter { bot -> bot.isHidden && !bot.isDefault }
        // The section shows the others, in the recorded order, with the rooms mixed in with the bots.
        val arranged = BotListItem.arranged(bots = resolved.filterNot { it.isHidden }, rooms = sourceRooms, order = order)

        val visible = arranged.filterIsInstance<BotListItem.Bot>().map { it.profile }
        val codes = resolved.mapNotNull { bot -> bot.faceCode?.let { bot.name to it } }.toMap()
        val groups = arranged.filterIsInstance<BotListItem.Room>().map { roomChat(it.room, visible) }

        // **The displayed roster is kept, with the effective faces.** It is the one that will answer the
        // first frame of the next launch, before the network has said anything.
        //
        // What is kept is not the server's *raw* roster, but the one that was **displayed**:
        // the faces there are those the roster's allocation assigned (two bots cannot
        // have the same creature). The name alone does not give them back — `forName("coder")` draws
        // palette 4 while the `coder` of a six-bot roster carries palette 6 — and neither does the
        // server's raw code: without the rendered table, the bot would find another creature
        // on restart than the one from yesterday.
        //
        // The scope is that of the connection (like the kept rooms): two servers never
        // share their bots.
        roster.save(serverKey, CachedRoster(bots = visible, faces = codes))

        // Rows seen for the first time count as read: the first look at the
        // list does not light it up entirely (`ChatActivity.listed`). A room without an activity mark
        // (never served) is not recorded.
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
        republishShortcuts()
    }

    /**
     * The **bots section** of the menu that opens on a long press of the icon.
     *
     * Republishes one shortcut per bot from the **real** list: a bot that appears receives its
     * tile, a bot that disappears loses its own, a renamed or recolored bot changes shortcut.
     * The selection rule (favorites, then the most recent, then the server order) and the
     * platform's bounds are held in [RosterShortcuts]; here, there is only the
     * wiring to the section.
     */
    private var lastShortcutsSignature: Int = 0

    private fun republishShortcuts() {
        if (!com.onyx.app.di.AppContainer.isInstalled) return
        val state = _state.value
        val pictures = _pictures.value
        val mainName = config.displayName
        val signature = java.util.Objects.hash(
            state.bots.map { listOf(it.name, it.title, it.faceCode, it.chatId, it.pinned) },
            state.order,
            state.faces,
            mainName,
            pictures.keys,
        )
        if (signature == lastShortcutsSignature) return
        lastShortcutsSignature = signature
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            runCatching {
                com.onyx.app.shortcuts.RosterShortcuts.publish(
                    context = com.onyx.app.di.AppContainer.appContext,
                    bots = state.bots,
                    order = state.order,
                    mainName = mainName,
                    pictures = pictures,
                    // The roster's map: the tile does not reread the local store to draw.
                    faceResolver = BotFaceResolver.of(state.faces),
                )
            }
        }
    }

    /** The section's dots: the server for the activity, this phone for unread. */
    fun badges(): BotBadges = BotBadges(
        liveKinds = _state.value.liveKinds,
        roomKinds = _state.value.roomKinds,
        seenMarks = _state.value.seenMarks,
    )

    /** Adds a bot named by hand, then rereads the list. */
    fun addLocalBot(profile: String, title: String) {
        faces.addLocalBot(profile, title)
        refresh()
    }

    /**
     * The model a bot will use: the choice made **here** (the model sheet writes
     * `FaceKeys.model` per bot), otherwise the one the roster carries from the server, otherwise `null`.
     *
     * This is the resolution already used by the other reads of a bot's model ([BotFaceStore]:
     * "the local choice takes precedence over what the server says"). Public because the chat screen needs it:
     * this is the name that feeds the single source of the model in force on the conversation side.
     */
    fun modelOf(bot: BotProfile): String? = faces.modelOf(bot.name) ?: bot.model

    /** Changes a bot's model: on the server when possible, here otherwise. */
    fun setModel(bot: BotProfile, model: String?, provider: String? = null) {
        if (config.hasServeCredentials) {
            viewModelScope.launch {
                runCatching {
                    val rpc = ServeRpc(config)
                    ServeBotsRpc(rpc).setModel(bot.name, model, provider)
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
        // The face goes to the server when the Dashboard is reachable; otherwise it stays here.
        if (config.hasServeCredentials) {
            viewModelScope.launch {
                try {
                    val rpc = ServeRpc(config)
                    try {
                        // The revision **of the face** (`ui_meta.onyx`), not that of the bot settings
                        // (`ui_meta.hermes-bots`): the server counts them separately. Sending `0`
                        // made it answer "this bot was just changed elsewhere" — the write
                        // was refused, and its failure being swallowed, the server kept the old
                        // face: the chosen mouth never appeared in the voice scene.
                        ServeBotsRpc(rpc).setFace(bot.name, code, revision = bot.faceRevision)
                    } finally {
                        rpc.close()
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    // A write failure is **said**: without that, the gesture seemed to go through and the
                    // face never changed.
                    val message = OnyxLocalization.format("Couldn't set that up: %1\$s", error.said())
                    _state.update { it.copy(problem = message) }
                }
            }
        }
        faces.setFace(bot.name, code)
        sourceBots = sourceBots.map { current ->
            if (current.name == bot.name) current.copy(faceCode = code) else current
        }
        publishFaces()
    }

    /**
     * A bot's face, through **the single resolution**: the roster's map (`state.faces`, output
     * of [withSwiftFaces], local choice and `ui_meta.onyx.face` together). A name the map does not
     * carry leaves the name to decide, as everywhere — there is no second computation here.
     */
    fun faceFor(bot: BotProfile): String? =
        BotFaceResolver.of(_state.value.faces).codeOf(bot.name)

    /** True only for a recorded choice, not for the roster's computed creature. */
    fun hasPickedFace(bot: BotProfile): Boolean =
        faces.faceOf(bot.name) != null || sourceBots.firstOrNull { it.name == bot.name }?.faceCode != null

    /**
     * "Hide" from a tile's menu: the bot leaves the list, here **and** on the server when the
     * Dashboard is reachable.
     *
     * Mirror of `hide(_:)` (ConversationsView:878, `changeBot(hidden: true)`), with the same
     * write as the editor's "Hidden" button (`writeHidden`) and the local choice taking precedence
     * when the server does not answer.
     */
    fun hideBot(bot: BotProfile) {
        faces.setHideOverride(bot.name, true)
        writeHidden(bot, hidden = true)
        refresh()
    }

    /**
     * "Remove from Onyx" from the menu, on a bot added by hand (`viaAPI`): nothing of it is left
     * on this phone — its profile, its face, its words, its visibility.
     *
     * Mirror of `removeAPIBot(_:)` (ConversationsView:871): the reference removes the profile from
     * `settings.apiBotsHere` and its key from the keychain; here everything Onyx kept about it lives in
     * `BotFaceStore` (`forget`), and the list is reread so that it disappears.
     */
    fun removeAPIBot(bot: BotProfile) {
        faces.forget(bot.name)
        sourceBots = sourceBots.filterNot { it.name == bot.name }
        refresh()
    }

    /**
     * Saves what the editor wrote, then rereads the list as it must be displayed.
     *
     * **The name goes to the server.** It was only written on this phone, in `BotFaceStore`:
     * the thread header therefore read it, but everything that comes from the roster — the list of
     * conversations, the tile, the Dashboard, the other devices — kept displaying the old
     * name. That is the "not in sync everywhere" that was reported: a bot is renamed, and only one screen follows.
     *
     * Hermes keeps a bot's title in `ui_meta["hermes-bots"].title`, the same key as its
     * visibility — so the revision to announce is [BotProfile.setUpRevision], not the face one.
     * The local copy is written first: offline, the name changes on this device anyway.
     */
    fun saveEdit(bot: BotProfile, title: String, about: String, model: String?, shown: Boolean) {
        faces.saveEdit(bot.name, title, about, model, shown)
        // "Hidden" is part of Hermes Desktop's settings: hiding a bot here hides it
        // for all devices, like `changeBot(BotChange(hidden:))` in the reference.
        writeHidden(bot, hidden = !shown)
        // The server, next: a refusal is said, otherwise the name would seem taken everywhere.
        val renomme = title.trim().isNotEmpty() && title.trim() != bot.label()
        val decrit = about.trim().isNotEmpty() && about.trim() != bot.description.trim()
        if ((renomme || decrit) && config.hasServeCredentials) {
            viewModelScope.launch {
                try {
                    val rpc = ServeRpc(config)
                    try {
                        ServeBotsRpc(rpc).setAbout(
                            profile = bot.name,
                            title = title.trim(),
                            about = about.trim(),
                            revision = bot.setUpRevision,
                        )
                    } finally {
                        rpc.close()
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    val message = OnyxLocalization.format("Couldn't set that up: %1\$s", error.said())
                    _state.update { it.copy(problem = message) }
                }
            }
        }
        refresh()
    }

    /**
     * "Create" of a new bot: the profile is born on the server, with the chosen model's face.
     *
     * The face is part of what the model fills in: it is written on the profile
     * (`ui_meta.onyx.face`) **and** kept on this phone, so that the row shows it from the
     * first reread, even if the server did not keep the `ui_meta`.
     *
     * `null`: it is done. Otherwise: what prevented it, ready to be shown.
     */
    suspend fun createBot(
        title: String,
        about: String,
        soul: String,
        faceCode: String?,
        hidden: Boolean = false,
    ): String? {
        if (!config.hasServeCredentials) return OnyxLocalization["Needs the Hermes Dashboard"]
        val profile = botHandle(title)
        if (profile.isEmpty()) return OnyxLocalization["Give it a name with a letter or number in it."]
        return try {
            val rpc = ServeRpc(config)
            try {
                ServeBotsRpc(rpc).createBot(
                    profile = profile,
                    title = title,
                    about = about,
                    soul = soul,
                    faceCode = faceCode,
                    // "It starts as a copy of {main}'s setup": the main profile is the starting
                    // copy, and its bots do not share its connections (`clone_from` leaves
                    // them aside, like the reference).
                    cloneFrom = sourceBots.firstOrNull { it.isDefault }?.name
                        ?: config.hermesProfile.trim().takeIf { it.isNotEmpty() },
                    hidden = hidden,
                )
            } finally {
                rpc.close()
            }
            faces.setFace(profile, faceCode)
            refresh()
            null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            error.said()
        }
    }

    /** A bot's prompt is its `SOUL.md`: the context editor already knows how to open it. */
    fun soulPath(bot: BotProfile): String = ".hermes/SOUL.md"

    /**
     * Deletes a bot on the server — and with it its chats, its memories and its settings.
     *
     * Mirror of `BotEditor.delete()` (BotEditor:467) and of `HermesServeClient.deleteBot`
     * (BotManagement:244): the profile goes away through the Dashboard's REST route. Nothing is
     * removed from the list, and the screen does not navigate, until the server has answered: a
     * failure leaves the bot as it is.
     *
     * `null`: it is done. Otherwise: what prevented it, ready to be shown.
     */
    suspend fun deleteBot(bot: BotProfile): String? {
        // The main profile is the person's agent: the reference does not delete it either.
        if (bot.isDefault) return OnyxLocalization["The main profile can't be deleted."]
        if (!config.hasServeCredentials) return OnyxLocalization["Needs the Hermes Dashboard"]
        return try {
            profiles.deleteProfile(bot.name)
            // What we had written about it on this phone must not survive its deletion.
            faces.forget(bot.name)
            sourceBots = sourceBots.filterNot { it.name == bot.name }
            // A hidden bot lives in `hiddenSource`: deleting it must also remove it from the
            // hidden list, without waiting for the server reread.
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
     * "Let bots message each other": the main profile is installed as a server bot,
     * which gives each bot chat the tool to write to the others.
     *
     * Mirror of `ConversationsView.letBotsMessageEachOther()` (ConversationsView:889):
     * the error is surfaced instead of being swallowed, and the list is reread so that the line disappears
     * once the setting is in place.
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
            // The list carries the failure, like the reference's `ledgerError`: the "Try Again" row is already
            // there, and nothing suggests that the setting was applied.
            _state.update { it.copy(problem = message) }
            message
        }
    }

    /** An error's text, as the app shows it elsewhere. */
    private fun Exception.said(): String = when (this) {
        is TransportError -> userMessage()
        else -> message ?: OnyxLocalization["Couldn't load the bots"]
    }

    // MARK: - Reorder Bots

    /**
     * A row dragged to another place: the section keeps this order from now on
     * (`Settings.botOrderHere` on the reference side), the rows that are not shown staying at the end.
     */
    fun move(from: Int, to: Int) {
        val shown = _state.value.arranged
        val order = BotListItem.orderAfter(shown = shown, from = from, to = to, kept = _state.value.order)
        activity.setOrder(serverKey, order)
        publish()
    }

    /** "Sort Bots by Recent": the section goes back to most recent first. */
    fun sortByRecent() {
        activity.setOrder(serverKey, emptyList())
        publish()
    }

    /**
     * A bot's visibility goes to the server (`profiles.configure`) when the Dashboard is
     * reachable — with the revision read, Hermes refusing a `ui_meta` changed in the meantime. The local
     * list, for its part, follows without waiting.
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

    // MARK: - Pinning, duplicating, starting over

    /**
     * "Pin to top" ("Épingler en haut") / "Unpin" ("Désépingler"): the bot moves to the head of the section, and stays there.
     *
     * Mirror of the reference's `setPinned` (`BotsViewModel.setPinned` → `changeMeta(bot, "pinned", …)`):
     * the choice is written **here** first — that is what keeps it offline and from one launch to
     * the next —, then on the server (`ui_meta["hermes-bots"].pinned`, the same block as
     * visibility) when the Dashboard is reachable, so that Hermes Desktop sees it too.
     */
    fun setPinned(bot: BotProfile, pinned: Boolean) {
        faces.setPinOverride(bot.name, pinned)
        writePinned(bot, pinned)
        refresh()
    }

    /** The pinning goes to the server, with the revision read, like visibility. */
    private fun writePinned(bot: BotProfile, pinned: Boolean) {
        if (!config.hasServeCredentials) return
        viewModelScope.launch {
            runCatching {
                val rpc = ServeRpc(config)
                try {
                    ServeBotsRpc(rpc).setPinned(bot.name, pinned = pinned, revision = bot.setUpRevision)
                } finally {
                    rpc.close()
                }
            }
        }
    }

    /**
     * "Duplicate" ("Dupliquer"): a second bot, a **copy** of this one.
     *
     * What is copied — exactly what `clone_from` copies on the server side, plus what this
     * phone writes about the bot:
     *  - **the profile configuration**: model, provider, keys, connections, tool sets;
     *  - **its skills, its `SOUL.md` and its memory** (the profile is cloned, not recreated);
     *  - **its face** (`ui_meta.onyx.face`, rewritten on the copy, and kept here too);
     *  - **its title** — "<title> (copy)" —, its **sentence** and its **visibility**.
     * What is **not** copied: its chat and the history of its conversations, and the pinning
     * (the copy arrives without it).
     *
     * The copy's name is `<profile>-2`, `-3`… the first free one, like the reference. Returns `null`
     * when it is done, otherwise what prevented it.
     */
    suspend fun duplicate(bot: BotProfile): String? {
        if (!config.hasServeCredentials) return OnyxLocalization["Needs the Hermes Dashboard"]
        val taken = (_state.value.bots + _state.value.hidden).map { it.name }.toSet()
        val name = (2..99).map { n -> bot.name.take(64 - "-$n".length) + "-$n" }.firstOrNull { it !in taken }
        val refuse = if (name == null) OnyxLocalization["No free name for the copy."] else null
        if (refuse != null) {
            _state.update { it.copy(problem = refuse) }
            return refuse
        }
        return try {
            val rpc = ServeRpc(config)
            try {
                ServeBotsRpc(rpc).createBot(
                    profile = name!!,
                    title = OnyxLocalization.format("%1\$s (copy)", bot.label(config.displayName)),
                    about = bot.description,
                    soul = "",
                    faceCode = faces.faceOf(bot.name) ?: bot.faceCode,
                    cloneFrom = bot.name,
                    hidden = bot.isHidden,
                )
            } finally {
                rpc.close()
            }
            // The copy carries the **same face** as its original: kept here too, so that the
            // card shows it from the first reread (the `ui_meta` may not be kept).
            faces.setFace(name!!, faces.faceOf(bot.name) ?: bot.faceCode)
            refresh()
            null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val said = error.said()
            _state.update { it.copy(problem = OnyxLocalization.format("Couldn't copy %1\$s. %2\$s", bot.label(config.displayName), said)) }
            said
        }
    }

    // MARK: - Sharing a model, and adding it from a link

    /**
     * The "Share as model" ("Partager comme modèle") link: what the server knows about this bot, encoded by
     * [BotTemplateShareCodec].
     *
     * The prompt is its `SOUL.md` (`api/profiles/{name}/soul`), the tools and the skills are
     * those the server declares **enabled** for this profile — never a guessed list. A read
     * that fails does not break the share: the link goes out with what we have, and [BotTemplateOffer]
     * says whether the prompt is in it.
     *
     * The codec's refusals (a prompt that contains an address or a key) come back as
     * [BotTemplateOfferOutcome.Refused]: the share sheet says them, it does not show an empty
     * link.
     */
    internal suspend fun templateOffer(bot: BotProfile): BotTemplateOfferOutcome {
        if (!config.hasServeCredentials) {
            return BotTemplateOfferOutcome.Refused(OnyxLocalization["Needs the Hermes Dashboard"])
        }
        return try {
            val soul: String?
            val tools: List<String>
            val skills: List<String>
            val rpc = ServeRpc(config)
            try {
                soul = runCatching { ServeFilesApi(ServeClient(config)).soul(bot.name) }.getOrNull()
                tools = runCatching { ServeBotsRpc(rpc).describeToolsets(bot.name).enabled.toList() }
                    .getOrDefault(emptyList())
            } finally {
                rpc.close()
            }
            skills = runCatching {
                ServeSkillsApi(ServeClient(config)).skills(bot.name)
                    .filter { skill -> skill.enabled }
                    .map { skill -> skill.name }
            }.getOrDefault(emptyList())
            val share = sharedTemplateOf(
                bot = bot,
                label = bot.label(config.displayName).ifBlank { bot.name },
                soul = soul,
                tools = tools,
                skills = skills,
            )
            BotTemplateOfferOutcome.Ready(
                BotTemplateOffer(link = share.link, withoutPrompt = share.spec.soul.isBlank()),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            BotTemplateOfferOutcome.Refused(error.said())
        }
    }

    /**
     * The return of a partial add, put on the bots list: what was not written is read,
     * rather than disappearing with the sheet.
     */
    internal fun reportPartialImport(outcome: BotTemplateImportOutcome.Added) {
        if (outcome.notApplied.isEmpty()) return
        _state.update {
            it.copy(
                problem = OnyxLocalization.format(
                    "Added %1\$s, but not everything was written: %2\$s.",
                    outcome.profile,
                    outcome.notApplied.joinToString(", "),
                ),
            )
        }
    }

    /**
     * "Add" ("Ajouter") of a model link: the profile is born on the server through **the bot
     * editor's routes** — `profiles.create` (`ServeBotsRpc.createBot`, the same `clone_from` as
     * "New Bot"), then its tool sets (`profiles.configure`), its skills
     * (`api/skills/toggle`) and its default model (`profiles.configure`).
     *
     * What could not be written is **not** hidden: [BotTemplateImportOutcome.Added.notApplied]
     * names it, and the screen shows it. A complete profile is therefore not half promised: it is
     * written, or it is said.
     */
    internal suspend fun importTemplate(share: BotTemplateShare): BotTemplateImportOutcome {
        if (!config.hasServeCredentials) {
            return BotTemplateImportOutcome.Refused(OnyxLocalization["Needs the Hermes Dashboard"])
        }
        val taken = (_state.value.bots + _state.value.hidden).map { it.name }.toSet()
        val profile = templateHandle(share.spec.title, taken)
        val faceCode = share.spec.face?.code()
        val notApplied = mutableListOf<String>()
        return try {
            val rpc = ServeRpc(config)
            try {
                val bots = ServeBotsRpc(rpc)
                bots.createBot(
                    profile = profile,
                    title = share.spec.title,
                    about = share.spec.about,
                    soul = share.spec.soul,
                    faceCode = faceCode,
                    // Like "New Bot": the profile is born as a copy of the main agent, without inheriting
                    // its connections.
                    cloneFrom = sourceBots.firstOrNull { it.isDefault }?.name
                        ?: config.hermesProfile.trim().takeIf { it.isNotEmpty() },
                    hidden = false,
                )
                if (share.tools.isNotEmpty()) {
                    try {
                        bots.setToolsets(profile, share.tools)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        notApplied += OnyxLocalization["the tool sets"]
                    }
                }
                if (!share.model.isNullOrBlank()) {
                    try {
                        bots.setModel(profile, share.model)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        notApplied += OnyxLocalization["the default model"]
                    }
                }
            } finally {
                rpc.close()
            }
            if (share.skills.isNotEmpty()) {
                val skills = ServeSkillsApi(ServeClient(config))
                var failed = false
                for (name in share.skills) {
                    try {
                        skills.toggle(name, enabled = true, profile = profile)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        failed = true
                    }
                }
                if (failed) notApplied += OnyxLocalization["the skills"]
            }
            // The face is kept here too: the card shows it from the first reread, even
            // if the server did not keep the `ui_meta`.
            faces.setFace(profile, faceCode)
            refresh()
            BotTemplateImportOutcome.Added(profile, notApplied)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            BotTemplateImportOutcome.Refused(error.said())
        }
    }

    /**
     * "Start fresh" ("Repartir à zéro"): **archives** the bot's chat, which keeps all of its history, then lets
     * a fresh conversation open.
     *
     * Mirror of the reference's `startFresh` (`BotsViewModel.startFresh` → `sessions.setArchived`):
     * the same route (`PATCH api/sessions/{id}`), the same effect — the old thread stays in
     * the history, the bot keeps its memory, its skills and its settings. Returns `null` when
     * it is done, otherwise what prevented it.
     */
    suspend fun archiveChat(bot: BotProfile): String? {
        val stored = bot.chatId?.takeIf { it.isNotBlank() } ?: return null
        if (!config.hasServeCredentials) return OnyxLocalization["Needs the Hermes Dashboard"]
        return try {
            val rpc = ServeRpc(config)
            try {
                ChatSessionActions(rpc, ServeClient(config)).archive(stored)
            } finally {
                rpc.close()
            }
            refresh()
            null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val said = error.said()
            _state.update { it.copy(problem = OnyxLocalization.format("Couldn't start %1\$s over. %2\$s", bot.label(config.displayName), said)) }
            said
        }
    }

    // MARK: - What has been seen

    /** The bot's chat was opened: everything it said is seen. */
    fun markBotSeen(bot: BotProfile) {
        val mark = bot.lastActive?.let { it / 1000.0 } ?: (System.currentTimeMillis() / 1000.0)
        activity.markSeen(serverKey, BotListItem.Bot(bot).id, mark)
        publishMarks()
    }

    /** "Mark as Unread" on a bot's tile. */
    fun markBotUnread(bot: BotProfile) {
        activity.markUnread(serverKey, BotListItem.Bot(bot).id)
        publishMarks()
    }

    /** A room was looked at up to its last event. */
    fun markRoomSeen(room: GroupChat) {
        val mark = room.activityMark ?: return
        activity.markSeen(serverKey, "room:" + room.id, mark)
        publishMarks()
    }

    /** "Mark as Unread" on a room's row. */
    fun markRoomUnread(room: GroupChat) {
        activity.markUnread(serverKey, "room:" + room.id)
        publishMarks()
    }

    private fun publishMarks() {
        val ids = _state.value.bots.map { BotListItem.Bot(it).id } + _state.value.groups.map { "room:" + it.id }
        val marks = ids.associateWith { activity.seen(serverKey, it) }
        _state.update { it.copy(seenMarks = marks) }
    }

    private companion object {
        /**
         * The largest edge kept for a bot's picture.
         *
         * The long-press menu tile draws it at 192 px (`BotTileArtwork.SIZE`): 256 keeps the
         * density margin of 3× screens, and divides the memory of a 1024×1024 avatar by sixteen.
         */
        const val AVATAR_PX = 256
    }
}
