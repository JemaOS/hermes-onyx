package com.onyx.app.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
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
 * Ce qu'un fichier de la conversation est devenu **sur le téléphone**.
 *
 * Quatre états, ceux que l'onglet affiche : [Remote] (« à télécharger »), [Fetching] (le poids reçu
 * et le poids annoncé, pour la barre), [Available] (le fichier est là, il s'ouvre **sans réseau**)
 * et [Failed] (avec la raison en mots, et de quoi réessayer — **seulement quand rejouer peut
 * aboutir**). Une pièce jointe que la personne a mise dans le fil est [Available] d'emblée : elle
 * n'a jamais eu besoin du serveur.
 */
sealed interface FileAvailability {

    /** Sur le serveur, pas encore ici. */
    data object Remote : FileAvailability

    /** En cours : [received] octets reçus, [total] annoncé par le serveur (ou `null`). */
    data class Fetching(val received: Long, val total: Long?) : FileAvailability {
        /** La part reçue, entre 0 et 1 ; `null` quand le serveur n'a rien annoncé. */
        val fraction: Float? get() = total?.takeIf { it > 0L }?.let { (received.toFloat() / it).coerceIn(0f, 1f) }
    }

    /** Là, sur le téléphone. */
    data class Available(val local: File) : FileAvailability

    /**
     * L'échec, **nommé**, et le geste qui peut encore marcher.
     *
     * [retryable] est faux quand rejouer la même demande ne peut pas aboutir : une autorisation
     * de galerie expirée, un fichier retiré de l'appareil, ou une cible que **personne ne sert**
     * (404 définitif) ne reviennent pas en appuyant sur « Réessayer ». L'onglet affiche alors
     * « Indisponible » et la raison, au lieu d'un bouton rouge qui échouera à chaque fois — c'est
     * ce que le propriétaire a vu.
     */
    data class Failed(val reason: String, val retryable: Boolean = true) : FileAvailability
}

/** Ce que le serveur a répondu au sujet d'une cible : il la sert, il ne la sert pas, ou il se tait. */
enum class ServedTarget { PRESENT, ABSENT, UNKNOWN }

/**
 * **La garantie de l'onglet Fichiers : aucune carte sans cible résolvable.**
 *
 * L'état d'une carte se décide ici, à partir de ce que l'app sait vraiment de sa cible : une copie
 * **locale présente** (elle s'ouvre), une cible **servie** par le serveur (elle se télécharge), ou
 * une cible que **personne ne sert** — auquel cas la carte le dit, avec le nom et la date qui sont
 * déjà les siens, et **ne propose aucun geste** : rien ne peut la rapporter.
 *
 * La règle est pure, donc éprouvable : c'est elle que `FileCardTargetResolvedTest` mesure.
 */
object FileCardTargets {

    /**
     * L'état d'une carte.
     *
     * [local] est la copie sur le téléphone (nulle si l'app n'en a pas). [served] est le verdict du
     * serveur sur la cible distante, [why] la phrase qui dit **ce qui a changé** quand personne ne
     * la sert plus.
     */
    fun state(local: File?, served: ServedTarget, why: String): FileAvailability = when {
        local != null && local.exists() -> FileAvailability.Available(local)
        served == ServedTarget.ABSENT -> FileAvailability.Failed(reason = why, retryable = false)
        else -> FileAvailability.Remote
    }

    /** Vrai quand la carte propose un geste : une cible locale, ou une cible que l'on peut demander. */
    fun offersAnAction(state: FileAvailability): Boolean = when (state) {
        is FileAvailability.Available -> true
        is FileAvailability.Remote -> true
        is FileAvailability.Fetching -> false
        is FileAvailability.Failed -> state.retryable
    }

    /**
     * Vrai quand la carte **mentirait** : elle promet un geste alors que la cible n'existe pas et
     * n'est servie par personne. C'est ce que le verrou interdit.
     */
    fun promisesWithoutTarget(state: FileAvailability, local: File?, served: ServedTarget): Boolean =
        (state is FileAvailability.Remote || state is FileAvailability.Fetching) &&
            (local == null || !local.exists()) && served == ServedTarget.ABSENT
}

/**
 * L'état de téléchargement et les vignettes des fichiers d'une conversation.
 *
 * Il vit le temps de l'écran (`remember` au-dessus de l'onglet) : rien n'est gardé entre deux
 * ouvertures de l'app, et un fichier déjà récupéré se relit dans les **fichiers de l'app**
 * ([FileAvailability.Available]) — c'est ce qui fait qu'un fichier local s'ouvre sans réseau, **et
 * qu'il reste là** : le dossier `cache` du téléphone, lui, peut être purgé à tout moment, et un
 * fichier qu'on a vu une fois ne pouvait plus être revu.
 *
 * Les vignettes sortent de l'appareil : une image se décode bornée ([decodeBounded]), la première
 * image d'une vidéo vient de [MediaMetadataRetriever]. Rien n'est pris sur le réseau pour autre
 * chose que le fichier lui-même.
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
     * Le dossier où atterrissent les fichiers récupérés.
     *
     * **`filesDir`, pas `cacheDir`** : le cache du téléphone se purge quand le système veut, et un
     * fichier qu'on avait vu une fois disparaissait de l'onglet — « je peux jamais les revoir ».
     * Rien n'est gardé éternellement pour autant : la suppression d'un fichier ou de la
     * conversation ne le laisse pas derrière (voir [forget]).
     */
    private val folder: File get() = File(context.filesDir, "conversation-files").apply { mkdirs() }

    /** L'état d'un fichier ; « à télécharger » tant que rien n'a été décidé. */
    fun state(path: String): FileAvailability = states[path] ?: FileAvailability.Remote

    /** Le poids, quand on le connaît (téléphone ou serveur). */
    fun size(path: String): Long? = sizes[path]

    /** La vignette déjà décodée, ou `null` (l'icône de famille prend alors la place). */
    fun poster(path: String): Bitmap? = posters[path]

    /**
     * Classe un fichier : ce qui est **déjà** sur le téléphone passe [FileAvailability.Available]
     * tout de suite — y compris la **copie** d'un fichier déjà récupéré, que le pointeur soit un
     * chemin du serveur ou une adresse (« revoir » ne doit pas dépendre de la forme du pointeur).
     * Un **chemin du serveur** encore absent est ensuite **vérifié auprès du dossier qui le
     * porte** — la cible est-elle servie, oui ou non. Ce que personne ne sert le dit tout de suite
     * ([DeliveredFiles.goneText]), au lieu d'une carte « à télécharger » qui ne pouvait qu'échouer.
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
                    // Déjà récupéré lors d'une ouverture précédente : il est **là**, et il
                    // s'ouvre sans réseau — c'est tout l'intérêt de l'avoir gardé. Vaut pour un
                    // **chemin du serveur** comme pour une **adresse** : c'est « revoir » un
                    // fichier qu'on a déjà vu, et ça ne doit pas dépendre de la forme du pointeur.
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
     * Récupère le fichier s'il n'est pas là. Le tap l'appelle avant d'ouvrir.
     *
     * Un échec **ne remonte pas** : la ligne de l'onglet porte déjà la raison, et une exception
     * lancée depuis ce `launch` tuait le processus (le fil principal l'attrapait comme une erreur
     * non gérée). C'est le second visage du défaut : un fichier qui ne se télécharge pas faisait
     * fermer l'app, à côté d'un « Réessayer » qui n'avait plus rien à réessayer.
     */
    fun fetch(file: ConversationFile, scope: CoroutineScope) {
        // Rien à faire quand il est déjà là ou en train d'arriver : deux appuis rapprochés ne
        // lancent pas deux téléchargements du même fichier.
        val current = state(file.path)
        if (current is FileAvailability.Fetching || current is FileAvailability.Available) return
        scope.launch {
            runCatching { runFetch(file, current) }
        }
    }

    /** Le fichier local, en le téléchargeant d'abord si besoin. Lève une erreur claire sinon. */
    suspend fun local(file: ConversationFile): File = when (val current = state(file.path)) {
        is FileAvailability.Available -> current.local
        else -> runFetch(file, current)
    }

    /**
     * La vignette du fichier, décodée **hors du fil principal** : bornée pour une image, une
     * image-clé pour une vidéo.
     *
     * Le fil principal n'attend jamais un décodage. C'est ce qui compte pour un TIFF, le seul
     * format que le décodeur de la maison doit lire en entier avant de réduire : 24 millions de
     * pixels sur un fil d'interface, c'est un gel visible, et l'appareil finit par le dire.
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
     * Rejoue **vraiment**.
     *
     * L'ancien geste effaçait l'état puis relançait la **même** demande : quand la cible n'existe
     * plus, la réponse était identique, et « Réessayer » ne réessayait rien. Ici l'échec est oublié
     * **avec** la lecture de dossier qui a servi à juger (c'est elle qui disait « il n'y est plus »),
     * la cible est **revérifiée**, et elle n'est récupérée que si elle est servie.
     */
    fun retry(file: ConversationFile, scope: CoroutineScope) {
        if (state(file.path) !is FileAvailability.Failed) return
        forget(file.path)
        scope.launch {
            resolve(file)
            if (state(file.path) is FileAvailability.Remote) runCatching { runFetch(file, state(file.path)) }
        }
    }

    /** Oublie tout ce qui a servi à juger un chemin : l'état, le poids, et le dossier lu. */
    private fun forget(path: String) {
        states.remove(path)
        sizes.remove(path)
        val directory = path.substringBeforeLast('/', "")
        folders.keys.removeAll { it == directory || directory.startsWith(it.trimEnd('/') + "/") }
    }

    // -----------------------------------------------------------------------------------------
    // Le travail réel
    // -----------------------------------------------------------------------------------------

    private suspend fun runFetch(file: ConversationFile, from: FileAvailability): File {
        // **Un chemin de l'appareil ne se demande JAMAIS au serveur.** Le serveur répond
        // `400 Path must be absolute` à un `content://` — et la carte affichait alors
        // « Indisponible » avec « le serveur a refusé la demande (400) » pour un fichier qui
        // n'avait jamais été sur le serveur. Le refus venait du client, pas du serveur. On le dit
        // sur place, avec le geste qui peut aboutir (le renvoyer depuis l'appareil).
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
            // Une **adresse** nommée par la réponse se demande **à elle-même** ; un chemin du
            // serveur passe par le Dashboard. C'est ce qui fait qu'une adresse est enfin servie :
            // la demander à `api/files/download` ne pouvait jamais aboutir.
            val local = if (DeliveredFiles.isRemoteUrl(file.path)) {
                download.toUrl(file.path, destination, report)
            } else {
                download.toFile(file.path, destination, report)
            }
            // Le serveur peut répondre 200 avec un accusé de réception au lieu du fichier
            // (`{"ok": true}`) : la ligne annonçait alors « Disponible, 12 o » et le lecteur
            // s'ouvrait noir. Ça se nomme, au lieu de se deviner — et rejouer la même demande
            // rendrait le même accusé, donc aucun geste n'est proposé.
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
            // **La cause, classée.** `isRetryable` dit si rejouer peut aboutir : un 404 définitif
            // (la cible n'est servie par personne) n'offre donc plus un « Réessayer » qui rejouait
            // la même demande morte à chaque appui — le geste que le propriétaire a vu.
            val problem = DeliveredFiles.problem(error, DeliveredFile(name = file.name, path = file.path))
            states[file.path] = FileAvailability.Failed(reason = problem.text, retryable = problem.retryable)
            throw error
        }
    }

    /** Une pièce jointe du téléphone : recopiée dans le cache pour que tous les lecteurs la lisent. */
    private suspend fun adoptContent(file: ConversationFile) {
        val uri = Uri.parse(file.path)
        val destination = target(file.path, file.name)
        try {
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                } ?: throw LocalSourceGone(OnyxLocalization["This file is no longer on this device."])
            }
            // Une copie vide n'est pas un fichier : le dire ici évite un lecteur noir plus tard.
            if (!destination.exists() || destination.length() <= 0L) {
                throw LocalSourceGone(OnyxLocalization["This file is no longer on this device."])
            }
            sizes[file.path] = destination.length()
            states[file.path] = FileAvailability.Available(destination)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            // **La cause, nommée.** Une autorisation de galerie ne survit pas à la mort du
            // processus : le fichier est toujours dans le fil, mais son `content://` ne répond
            // plus. « Impossible de l'ouvrir » ne disait rien ; « Réessayer » ne pouvait pas
            // aboutir. On nomme l'expiration, et on ne propose pas un geste inutile.
            val expired = error is SecurityException || error is LocalSourceGone
            val reason = when {
                error is SecurityException ->
                    OnyxLocalization["Access to this file has expired: it comes from the gallery. Send it again from the device."]
                error is LocalSourceGone -> error.message ?: OnyxLocalization["This file is no longer on this device."]
                error is java.io.FileNotFoundException ->
                    OnyxLocalization["This file is no longer on this device."]
                else -> DeliveredFiles.failureText(error, null)
            }
            // Une copie partielle ne doit pas passer pour un fichier disponible au prochain écran.
            runCatching { if (destination.length() <= 0L || expired) destination.delete() }
            states[file.path] = FileAvailability.Failed(reason = reason, retryable = !expired)
        }
    }

    /** Une source locale qui n'est plus là : le cas nommé, distinct d'une panne réseau. */
    private class LocalSourceGone(message: String) : IllegalStateException(message)

    /**
     * **La cible est-elle servie ?** — la question à laquelle la carte doit avoir répondu.
     *
     * Trois réponses : sur le téléphone la taille se lit d'un coup ; sur le serveur elle se demande
     * au dossier qui le porte (`api/files`, la même route que l'écran Réglages → Fichiers, gardée en
     * mémoire pour tous les fichiers du même dossier) — et **cette lecture dit aussi si le nom y
     * est**. S'il n'y est pas, personne ne le sert : la carte le dit ([DeliveredFiles.goneText]),
     * sans geste, au lieu de promettre un téléchargement qui ne pouvait qu'échouer. Une adresse
     * absolue, elle, ne se vérifie pas sans la demander : elle reste « à télécharger », et son
     * échec éventuel sera nommé et classé.
     */
    private suspend fun resolve(file: ConversationFile) {
        val path = file.path
        try {
            when {
                DeliveredFiles.isRemoteUrl(path) -> Unit
                path.startsWith("content://") -> {
                    val size = contentSize(Uri.parse(path))
                    sizes[path] = size
                    // Une URI de l'appareil qui ne répond plus (autorisation expirée) ne se
                    // demande **pas** au serveur : il répondrait 400, et la carte accuserait le
                    // serveur d'un refus qui vient d'ici. On le dit sur place.
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
                    // Même garde : une copie durable qui n'est plus là (effacée hors de l'app)
                    // ne se demande pas au serveur, qui ne l'a jamais eue.
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
     * Les entrées du dossier qui porte [path], ou `null` quand le serveur n'a pas répondu — auquel
     * cas on ne conclut **rien** : ne pas savoir n'est pas la même chose que savoir que c'est absent.
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

    /** Le nom du fichier, sans ce qui nommerait un autre dossier. */
    private fun safeName(name: String): String =
        name.replace('/', '_').replace('\\', '_').takeIf { it.isNotBlank() } ?: "file"

    /**
     * Le fichier dans le cache, pour un chemin donné.
     *
     * Le nom **seul** ne suffit pas : deux conversations peuvent livrer « rapport.pdf », et le
     * premier récupéré serait alors servi pour le second. L'empreinte du chemin le sépare, et le
     * nom d'origine reste à la fin (c'est lui qui porte l'extension, donc la famille).
     */
    private fun target(path: String, name: String): File =
        File(folder, "${path.hashCode().toUInt().toString(16)}-${safeName(name)}")

    /** Une image **bornée** : jamais l'image entière pour une vignette de 56 dp. */
    private fun decodeBounded(local: File, maxPx: Int = BoundedImages.ROW_PX): Bitmap? =
        BoundedImages.decodeFile(local, maxPx)

    /** La première image d'une vidéo (`getFrameAtTime` au début du flux). */
    private fun frameOf(local: File): Bitmap? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(local.absolutePath)
            retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } finally {
            runCatching { retriever.release() }
        }
    }.getOrNull()
}
