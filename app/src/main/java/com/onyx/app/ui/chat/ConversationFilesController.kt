package com.onyx.app.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.compose.runtime.mutableStateMapOf
import com.onyx.app.data.model.ConversationFile
import com.onyx.app.data.model.FileFamily
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.DeliveredFile
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.ServeFileDownload
import com.onyx.app.data.net.ServeFilesApi
import com.onyx.app.localization.OnyxLocalization
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * What a conversation file has become **on the phone**.
 *
 * Four states, those the tab displays: [Remote] ("to download"), [Fetching] (the received
 * weight and the announced weight, for the bar), [Available] (the file is there, it opens **without network**)
 * and [Failed] (with the reason in words, and something to retry — **only when replaying can
 * succeed**). An attachment the person put in the thread is [Available] from the outset: it
 * never needed the server.
 */
sealed interface FileAvailability {

    /** On the server, not here yet. */
    data object Remote : FileAvailability

    /** In progress: [received] bytes received, [total] announced by the server (or `null`). */
    data class Fetching(val received: Long, val total: Long?) : FileAvailability {
        /** The received share, between 0 and 1; `null` when the server announced nothing. */
        val fraction: Float? get() = total?.takeIf { it > 0L }?.let { (received.toFloat() / it).coerceIn(0f, 1f) }
    }

    /** There, on the phone. */
    data class Available(val local: File) : FileAvailability

    /**
     * The failure, **named**, and the gesture that can still work.
     *
     * [retryable] is false when replaying the same request cannot succeed: an expired gallery
     * permission, a file removed from the device, or a target that **no one serves**
     * (definitive 404) do not come back by pressing "Réessayer" ("Retry"). The tab then shows
     * "Indisponible" ("Unavailable") and the reason, instead of a red button that would fail every time — that is
     * what the owner saw.
     */
    data class Failed(val reason: String, val retryable: Boolean = true) : FileAvailability
}

/** What the server answered about a target: it serves it, it does not serve it, or it stays silent. */
enum class ServedTarget { PRESENT, ABSENT, UNKNOWN }

/**
 * **The guarantee of the Files tab: no card without a resolvable target.**
 *
 * A card's state is decided here, from what the app really knows about its target: a **present
 * local** copy (it opens), a target **served** by the server (it downloads), or
 * a target that **no one serves** — in which case the card says so, with the name and date that are
 * already its own, and **offers no gesture**: nothing can bring it back.
 *
 * The rule is pure, therefore testable: it is what `FileCardTargetResolvedTest` measures.
 */
object FileCardTargets {

    /**
     * A card's state.
     *
     * [local] is the copy on the phone (null if the app has none). [served] is the server's
     * verdict on the remote target, [why] the sentence that says **what changed** when no one
     * serves it any more.
     */
    fun state(local: File?, served: ServedTarget, why: String): FileAvailability = when {
        local != null && local.exists() -> FileAvailability.Available(local)
        served == ServedTarget.ABSENT -> FileAvailability.Failed(reason = why, retryable = false)
        else -> FileAvailability.Remote
    }

    /** True when the card offers a gesture: a local target, or a target that can be requested. */
    fun offersAnAction(state: FileAvailability): Boolean = when (state) {
        is FileAvailability.Available -> true
        is FileAvailability.Remote -> true
        is FileAvailability.Fetching -> false
        is FileAvailability.Failed -> state.retryable
    }

    /**
     * True when the card **would lie**: it promises a gesture while the target does not exist and
     * is served by no one. That is what the lock forbids.
     */
    fun promisesWithoutTarget(state: FileAvailability, local: File?, served: ServedTarget): Boolean =
        (state is FileAvailability.Remote || state is FileAvailability.Fetching) &&
            (local == null || !local.exists()) && served == ServedTarget.ABSENT
}

/**
 * The download state and thumbnails of a conversation's files.
 *
 * It lives for the duration of the screen (`remember` above the tab): nothing is kept between two
 * openings of the app, and an already fetched file is read again in the **app's files**
 * ([FileAvailability.Available]) — that is what makes a local file open without network, **and
 * keeps it there**: the phone's `cache` folder, on the other hand, can be purged at any time, and a
 * file that had been seen once could no longer be seen again.
 *
 * Thumbnails come out of the device: an image is decoded bounded ([decodeBounded]), a video's first
 * image comes from [MediaMetadataRetriever]. Nothing is taken from the network for anything
 * other than the file itself.
 */
class ConversationFilesController(
    private val context: Context,
    private val config: ServerConfig,
) {

    private val states = mutableStateMapOf<String, FileAvailability>()
    private val sizes = mutableStateMapOf<String, Long?>()
    private val posters = mutableStateMapOf<String, Bitmap?>()
    private val postersRequested = mutableSetOf<String>()

    /**
     * The folder where fetched files land.
     *
     * **`filesDir`, not `cacheDir`**: the phone's cache is purged whenever the system wants, and a
     * file that had been seen once disappeared from the tab — "I can never see them again". Nothing
     * is kept forever, though: deleting a file or the conversation does not leave it behind
     * (see [forget]).
     */
    private val folder: File get() = File(context.filesDir, "conversation-files").apply { mkdirs() }

    /** A file's state; "to download" as long as nothing has been decided. */
    fun state(path: String): FileAvailability = states[path] ?: FileAvailability.Remote

    /** The weight, when known (phone or server). */
    fun size(path: String): Long? = sizes[path]

    /** The already decoded thumbnail, or `null` (the family icon then takes its place). */
    fun poster(path: String): Bitmap? = posters[path]

    /**
     * Classifies a file: what is **already** on the phone goes to [FileAvailability.Available]
     * right away — including the **copy** of an already fetched file, whether the pointer is a
     * server path or an address ("see again" must not depend on the pointer's shape).
     * A **server path** still absent is then **checked against the folder that holds it**
     * — is the target served, yes or no. What no one serves says so right away
     * ([DeliveredFiles.goneText]), instead of a "to download" card that could only fail.
     */
    fun prepare(file: ConversationFile, scope: CoroutineScope) {
        val path = file.path
        if (states[path] == null && sizes[path] == null) {
            when {
                path.startsWith("content://") -> scope.launch { adoptContent(file) }
                path.startsWith("file://") -> {
                    val local = Uri.parse(path).path?.let { File(it) }
                    if (local != null && local.exists()) states[path] = FileAvailability.Available(local)
                }
                !DeliveredFiles.isRemoteUrl(path) && path.startsWith("/") && File(path).exists() ->
                    states[path] = FileAvailability.Available(File(path))

                else -> {
                    // Already fetched during a previous opening: it is **there**, and it
                    // opens without network — that is the whole point of having kept it. Applies to a
                    // **server path** as well as an **address**: it is "seeing again" a
                    // file already seen, and it must not depend on the pointer's shape.
                    val cached = target(path, file.name)
                    if (cached.exists() && cached.length() > 0L) {
                        states[path] = FileAvailability.Available(cached)
                        sizes[path] = cached.length()
                    }
                }
            }
        }
        if (sizes[path] == null && states[path] == null) scope.launch { resolve(file) }
    }

    /**
     * Fetches the file if it is not there. The tap calls this before opening.
     *
     * A failure **does not propagate**: the tab's row already carries the reason, and an exception
     * thrown from this `launch` killed the process (the main thread caught it as an unhandled
     * error). It is the second face of the defect: a file that does not download made the app
     * close, next to a "Réessayer" ("Retry") that had nothing left to retry.
     */
    fun fetch(file: ConversationFile, scope: CoroutineScope) {
        // Nothing to do when it is already there or arriving: two close taps do not
        // start two downloads of the same file.
        val current = state(file.path)
        if (current is FileAvailability.Fetching || current is FileAvailability.Available) return
        scope.launch {
            runCatching { runFetch(file, current) }
        }
    }

    /** The local file, downloading it first if needed. Raises a clear error otherwise. */
    suspend fun local(file: ConversationFile): File = when (val current = state(file.path)) {
        is FileAvailability.Available -> current.local
        else -> runFetch(file, current)
    }

    /**
     * The file's thumbnail, decoded **off the main thread**: bounded for an image, a
     * key frame for a video.
     *
     * The main thread never waits for a decode. That is what matters for a TIFF, the only
     * format the in-house decoder must read in full before shrinking: 24 million pixels
     * on a UI thread is a visible freeze, and the device ends up saying so.
     */
    suspend fun loadPoster(file: ConversationFile) {
        val local = (state(file.path) as? FileAvailability.Available)?.local ?: return
        if (posters.containsKey(file.path) || file.path in postersRequested) return
        postersRequested += file.path
        val decoded = when (file.family) {
            FileFamily.IMAGE -> withContext(Dispatchers.IO) { decodeBounded(local) }
            FileFamily.VIDEO -> withContext(Dispatchers.IO) { frameOf(local) }
            else -> null
        } ?: return
        posters[file.path] = decoded
    }

    /**
     * Replays **for real**.
     *
     * The old gesture cleared the state then relaunched the **same** request: when the target no
     * longer exists, the answer was identical, and "Réessayer" ("Retry") retried nothing. Here the failure is forgotten
     * **along with** the folder read that was used to judge (it is what said "it is no longer there"),
     * the target is **re-checked**, and it is only fetched if it is served.
     */
    fun retry(file: ConversationFile, scope: CoroutineScope) {
        if (state(file.path) !is FileAvailability.Failed) return
        forget(file.path)
        scope.launch {
            resolve(file)
            if (state(file.path) is FileAvailability.Remote) runCatching { runFetch(file, state(file.path)) }
        }
    }

    /** Forgets everything used to judge a path: the state, the weight, and the folder read. */
    private fun forget(path: String) {
        states.remove(path)
        sizes.remove(path)
        val directory = path.substringBeforeLast('/', "")
        folders.keys.removeAll { it == directory || directory.startsWith(it.trimEnd('/') + "/") }
    }

    // -----------------------------------------------------------------------------------------
    // The real work
    // -----------------------------------------------------------------------------------------

    private suspend fun runFetch(file: ConversationFile, from: FileAvailability): File {
        // **A device path is NEVER requested from the server.** The server answers
        // `400 Path must be absolute` to a `content://` — and the card then showed
        // "Indisponible" ("Unavailable") with "the server refused the request (400)" for a file that had
        // never been on the server. The refusal came from the client, not the server. We say so
        // right here, with the gesture that can succeed (send it again from the device).
        if (DeliveredFiles.isLocalPath(file.path)) {
            val reason = DeliveredFiles.localProblemText(LocalSourceGone(
                OnyxLocalization["This file is no longer on this device. Send it again from the device."],
            ))
            states[file.path] = FileAvailability.Failed(retryable = false, reason = reason)
            throw IllegalStateException(reason)
        }
        states[file.path] = FileAvailability.Fetching(0L, sizes[file.path])
        val destination = target(file.path, file.name)
        return try {
            val download = ServeFileDownload(config)
            val report: (Long, Long?) -> Unit = { received, total ->
                states[file.path] = FileAvailability.Fetching(received, total)
            }
            // An **address** named by the response is requested **from itself**; a server
            // path goes through the Dashboard. That is what finally gets an address served:
            // requesting it from `api/files/download` could never succeed.
            val local = if (DeliveredFiles.isRemoteUrl(file.path)) {
                download.toUrl(file.path, destination, report)
            } else {
                download.toFile(file.path, destination, report)
            }
            // The server may answer 200 with an acknowledgement instead of the file
            // (`{"ok": true}`): the row then announced "Available, 12 B" and the reader
            // opened black. It is named, instead of guessed — and replaying the same request
            // would return the same acknowledgement, so no gesture is offered.
            if (DeliveredFiles.looksLikeAck(local, file.name)) {
                local.delete()
                val reason = OnyxLocalization["The server answered with a receipt instead of the file. Open it again from the reply."]
                states[file.path] = FileAvailability.Failed(retryable = false, reason = reason)
                throw IllegalStateException(reason)
            }
            sizes[file.path] = local.length()
            states[file.path] = FileAvailability.Available(local)
            local
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            // **The cause, classified.** `isRetryable` says whether replaying can succeed: a definitive 404
            // (the target is served by no one) therefore no longer offers a "Réessayer" ("Retry") that replayed
            // the same dead request on every press — the gesture the owner saw.
            val problem = DeliveredFiles.problem(error, DeliveredFile(name = file.name, path = file.path))
            states[file.path] = FileAvailability.Failed(reason = problem.text, retryable = problem.retryable)
            throw error
        }
    }

    /** A phone attachment: copied into the cache so that every reader can read it. */
    private suspend fun adoptContent(file: ConversationFile) {
        val uri = Uri.parse(file.path)
        val destination = target(file.path, file.name)
        try {
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                } ?: throw LocalSourceGone(OnyxLocalization["This file is no longer on this device."])
            }
            // An empty copy is not a file: saying so here avoids a black reader later.
            if (!destination.exists() || destination.length() <= 0L) {
                throw LocalSourceGone(OnyxLocalization["This file is no longer on this device."])
            }
            sizes[file.path] = destination.length()
            states[file.path] = FileAvailability.Available(destination)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            // **The cause, named.** A gallery permission does not survive the process dying:
            // the file is still in the thread, but its `content://` no longer answers.
            // "Couldn't open it" said nothing; "Réessayer" ("Retry") could not
            // succeed. We name the expiry, and do not offer a pointless gesture.
            val expired = error is SecurityException || error is LocalSourceGone
            val reason = when {
                error is SecurityException ->
                    OnyxLocalization["Access to this file has expired: it comes from the gallery. Send it again from the device."]
                error is LocalSourceGone ->
                    OnyxLocalization["This file is no longer on this device."]
                error is java.io.FileNotFoundException ->
                    OnyxLocalization["This file is no longer on this device."]
                else -> DeliveredFiles.failureText(error, null)
            }
            // A partial copy must not pass for an available file on the next screen.
            runCatching { if (destination.length() <= 0L || expired) destination.delete() }
            states[file.path] = FileAvailability.Failed(reason = reason, retryable = !expired)
        }
    }

    /** A local source that is no longer there: the named case, distinct from a network failure. */
    private class LocalSourceGone(message: String) : IllegalStateException(message)

    /**
     * **Is the target served?** — the question the card must have answered.
     *
     * Three answers: on the phone the size is read at once; on the server it is asked
     * from the folder that holds it (`api/files`, the same route as the Settings → Files screen, kept in
     * memory for all files of the same folder) — and **this read also says whether the name is
     * there**. If it is not, no one serves it: the card says so ([DeliveredFiles.goneText]),
     * without a gesture, instead of promising a download that could only fail. An absolute
     * address, however, cannot be checked without requesting it: it stays "to download", and its
     * eventual failure will be named and classified.
     */
    private suspend fun resolve(file: ConversationFile) {
        val path = file.path
        try {
            when {
                DeliveredFiles.isRemoteUrl(path) -> Unit
                path.startsWith("content://") -> {
                    val size = contentSize(Uri.parse(path))
                    sizes[path] = size
                    // A device URI that no longer answers (expired permission) is **not**
                    // requested from the server: it would answer 400, and the card would blame the
                    // server for a refusal that comes from here. We say so right here.
                    if (size == null && states[path] == null) {
                        states[path] = FileAvailability.Failed(
                            retryable = false,
                            reason = DeliveredFiles.localProblemText(LocalSourceGone(
                                OnyxLocalization["This file is no longer on this device. Send it again from the device."],
                            )),
                        )
                    }
                }
                path.startsWith("file://") -> {
                    val local = Uri.parse(path).path?.let { File(it) }
                    sizes[path] = local?.takeIf { it.exists() }?.length()
                    // Same guard: a durable copy that is no longer there (deleted outside the app)
                    // is not requested from the server, which never had it.
                    if (sizes[path] == null && states[path] == null) {
                        states[path] = FileAvailability.Failed(
                            retryable = false,
                            reason = DeliveredFiles.localProblemText(LocalSourceGone(
                                OnyxLocalization["This file is no longer on this device. Send it again from the device."],
                            )),
                        )
                    }
                }

                path.startsWith("/") && File(path).exists() -> sizes[path] = File(path).length()
                else -> {
                    val served = servedFolder(path) ?: return
                    val size = served[file.name]
                    if (size != null) {
                        sizes[path] = size
                    } else if (states[path] == null) {
                        val why = DeliveredFiles.goneText(DeliveredFile(name = file.name, path = path))
                        states[path] = FileCardTargets.state(local = null, served = ServedTarget.ABSENT, why = why)
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            sizes[path] = null
        }
    }

    /**
     * The entries of the folder that holds [path], or `null` when the server did not answer — in which
     * case we conclude **nothing**: not knowing is not the same as knowing it is absent.
     */
    private suspend fun servedFolder(path: String): Map<String, Long>? {
        val directory = path.substringBeforeLast('/', "")
        if (directory.isEmpty()) return null
        return runCatching { cachedFolder(directory) }.getOrNull()
    }

    private val folders = mutableMapOf<String, Map<String, Long>>()

    private suspend fun cachedFolder(directory: String): Map<String, Long> {
        folders[directory]?.let { return it }
        val entries = withContext(Dispatchers.IO) {
            ServeFilesApi(ServeClient(config)).list(directory)
        }
        val map = entries.associate { it.name to it.size }
        folders[directory] = map
        return map
    }

    private fun contentSize(uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) cursor.getLong(index) else null
        }
    }.getOrNull()

    /** The file name, without anything that would name another folder. */
    private fun safeName(name: String): String =
        name.replace('/', '_').replace('\\', '_').takeIf { it.isNotBlank() } ?: "file"

    /**
     * The file in the cache, for a given path.
     *
     * The name **alone** is not enough: two conversations can deliver "rapport.pdf" ("report.pdf"), and the
     * first fetched would then be served for the second. The path's fingerprint separates them, and the
     * original name stays at the end (it is what carries the extension, hence the family).
     */
    private fun target(path: String, name: String): File =
        File(folder, "${path.hashCode().toUInt().toString(16)}-${safeName(name)}")

    /** A **bounded** image: never the whole image for a 56 dp thumbnail. */
    private fun decodeBounded(local: File, maxPx: Int = BoundedImages.ROW_PX): Bitmap? =
        BoundedImages.decodeFile(local, maxPx)

    /**
     * A video's first image (`getFrameAtTime` at the start of the stream), **shrunk to the size
     * of a row**.
     *
     * It used to come out at native resolution and be kept as is: a 4K key frame weighs
     * 3840×2160×4 = 33 MB in ARGB_8888 for a 56 dp chip, and five videos in a thread
     * held 165 MB of heap — enough to make the Files tab crash. Since Android 8.1,
     * `getScaledFrameAtTime` decodes directly at the requested size (the original never exists);
     * before, the frame is shrunk and recycled once.
     */
    private fun frameOf(local: File): Bitmap? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(local.absolutePath)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    0L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    BoundedImages.ROW_PX,
                    BoundedImages.ROW_PX,
                )
            } else {
                retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?.let(::shrinkFrame)
            }
        } finally {
            runCatching { retriever.release() }
        }
    }.getOrNull()

    /**
     * Shrinks an already decoded image to [BoundedImages.ROW_PX] on its longest edge, and
     * **recycles the original**: without that, the full-resolution frame would stay in memory until the
     * next GC pass, for a thumbnail no one draws any bigger.
     */
    private fun shrinkFrame(frame: Bitmap): Bitmap {
        val longest = maxOf(frame.width, frame.height)
        if (longest <= BoundedImages.ROW_PX) return frame
        val scale = BoundedImages.ROW_PX.toFloat() / longest
        val scaled = runCatching {
            Bitmap.createScaledBitmap(
                frame,
                (frame.width * scale).toInt().coerceAtLeast(1),
                (frame.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        }.getOrNull() ?: return frame
        if (scaled !== frame) frame.recycle()
        return scaled
    }
}
