package com.onyx.app.ui.chat

import com.onyx.app.data.model.serve.DeliveredFile
import com.onyx.app.data.net.TransportError
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * **Aucune carte sans cible résolvable.**
 *
 * C'est le verrou de l'onglet Fichiers, et il vient d'un défaut mesuré : deux cartes (`me.json`,
 * `about.json`) portaient le nom du fichier, sa date, la mention « Il est introuvable… » et un
 * « Réessayer » — et « Réessayer » rejouait la **même** demande morte, parce que la cible n'était
 * servie par personne : la réponse avait nommé des **adresses**, et le motif de repérage n'en
 * gardait que la queue (`//hôte/api/me.json`), un chemin que le Dashboard n'a jamais servi.
 *
 * Le verrou tient en trois morceaux :
 *
 *  * **la règle**, pure : une carte ne propose un geste que si la cible existe sur le téléphone ou
 *    est servie ; sinon elle **dit ce qui a changé** et n'offre rien ;
 *  * **le classement des échecs** : `isRetryable` décide, et un 404 définitif ne propose pas de
 *    bouton — aucun site d'échec ne peut reprendre le défaut « vrai » ;
 *  * **le câblage** : l'adresse est demandée telle quelle, le chemin du serveur passe par le
 *    Dashboard, et l'absence est dite **avant** l'appui.
 */
class FileCardTargetResolvedTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val controller =
        File("src/main/java/com/onyx/app/ui/chat/ConversationFilesController.kt").readText().replace("\r\n", "\n")
    private val tab = File("src/main/java/com/onyx/app/ui/chat/FilesTab.kt").readText().replace("\r\n", "\n")
    private val delivered = File("src/main/java/com/onyx/app/ui/chat/DeliveredFiles.kt").readText().replace("\r\n", "\n")
    private val download =
        File("../core/src/main/java/com/onyx/app/data/net/ServeFileDownload.kt").readText().replace("\r\n", "\n")

    // -----------------------------------------------------------------------------------------
    // La règle, pure
    // -----------------------------------------------------------------------------------------

    @Test
    fun `une cible que personne ne sert dit ce qui a change, et n offre rien`() {
        val why = "Personne ne sert plus ce fichier à /home/onyx/Documents/me.json : il a été retiré."
        val state = FileCardTargets.state(local = null, served = ServedTarget.ABSENT, why = why)
        assertTrue("l'absence est un échec nommé", state is FileAvailability.Failed)
        assertEquals("la phrase dit ce qui a changé", why, (state as FileAvailability.Failed).reason)
        assertFalse(
            "« Réessayer » ne peut pas aboutir : la carte ne doit pas l'offrir",
            state.retryable,
        )
        assertFalse("aucun geste sur une cible que personne ne sert", FileCardTargets.offersAnAction(state))
    }

    @Test
    fun `une copie locale presente s ouvre`() {
        val local = folder.newFile("me.json").apply { writeBytes("{}".toByteArray()) }
        val state = FileCardTargets.state(local = local, served = ServedTarget.UNKNOWN, why = "peu importe")
        assertTrue(state is FileAvailability.Available)
        assertEquals(local, (state as FileAvailability.Available).local)
        assertTrue(FileCardTargets.offersAnAction(state))
    }

    @Test
    fun `une cible servie se telecharge`() {
        val state = FileCardTargets.state(local = null, served = ServedTarget.PRESENT, why = "peu importe")
        assertTrue("le serveur la sert : « à télécharger » est une promesse tenable", state is FileAvailability.Remote)
        assertTrue(FileCardTargets.offersAnAction(state))
    }

    @Test
    fun `une copie locale qui n existe plus ne compte pas`() {
        val absent = File(folder.root, "parti.json")
        assertTrue(
            "un fichier disparu du disque ne rend pas une carte ouvrable",
            FileCardTargets.state(absent, ServedTarget.ABSENT, "parti") is FileAvailability.Failed,
        )
    }

    @Test
    fun `le mensonge est nomme`() {
        assertTrue(
            "« à télécharger » sans cible servie est le mensonge d'origine",
            FileCardTargets.promisesWithoutTarget(FileAvailability.Remote, null, ServedTarget.ABSENT),
        )
        assertTrue(
            "en cours de récupération aussi",
            FileCardTargets.promisesWithoutTarget(FileAvailability.Fetching(1L, 2L), null, ServedTarget.ABSENT),
        )
        assertFalse(
            "servie : rien à interdire",
            FileCardTargets.promisesWithoutTarget(FileAvailability.Remote, null, ServedTarget.PRESENT),
        )
        assertFalse(
            "muette : on ne conclut pas une absence depuis un silence",
            FileCardTargets.promisesWithoutTarget(FileAvailability.Remote, null, ServedTarget.UNKNOWN),
        )
        assertFalse(
            "l'échec dit déjà la vérité",
            FileCardTargets.promisesWithoutTarget(FileAvailability.Failed("parti", retryable = false), null, ServedTarget.ABSENT),
        )
    }

    @Test
    fun `un refus definitif n offre pas de bouton, un essai perdu si`() {
        val gone = DeliveredFiles.problem(
            TransportError.Http(404, "{\"detail\":\"File not found\"}"),
            DeliveredFile(name = "me.json", path = "http://10.0.2.2:8899/url/me.json"),
        )
        assertFalse("un 404 est définitif : rejouer la même demande rendrait 404", gone.retryable)
        assertTrue("et la phrase nomme la cause", gone.text.contains("404"))

        val blip = DeliveredFiles.problem(TransportError.Timeout("timeout"), null)
        assertTrue("une panne passagère laisse réessayer", blip.retryable)
    }

    // -----------------------------------------------------------------------------------------
    // Le câblage — ce qui doit mordre sur la source d'avant
    // -----------------------------------------------------------------------------------------

    @Test
    fun `aucun echec n est ecrit sans son classement`() {
        val sites = Regex("""FileAvailability\.Failed\(([^)]*)\)""")
            .findAll(controller)
            .map { it.groupValues[1] }
            .toList()
        assertTrue("le contrôleur a bien des sites d'échec", sites.isNotEmpty())
        sites.forEach { arguments ->
            assertTrue(
                "un échec sans `retryable` reprend le défaut « vrai » — c'est le bouton mort d'origine : $arguments",
                arguments.contains("retryable"),
            )
        }
        assertTrue(
            "et il est classé par la règle de la maison",
            controller.contains("DeliveredFiles.problem(error, DeliveredFile(name = file.name, path = file.path))"),
        )
        assertTrue("la carte n'offre le geste que si rejouer peut aboutir", tab.contains("action = if (availability.retryable) onRetry else null"))
        assertTrue(
            "et l'étiquette le dit",
            tab.contains("label = if (availability.retryable) OnyxLocalization[\"Retry\"] else OnyxLocalization[\"Unavailable\"]"),
        )
    }

    @Test
    fun `une adresse nommee par la reponse est demandee telle quelle`() {
        assertTrue("l'adresse est reconnue", delivered.contains("fun isRemoteUrl(path: String): Boolean"))
        assertTrue(
            "le motif garde l'adresse entière (jamais la queue `//hôte/chemin`)",
            delivered.contains("""(?:https?://[^\s"'<>()\[\]]+|/[^\s"'<>()\[\]]+)\.("""),
        )
        assertTrue(
            "et une adresse n'est pas une pièce jointe de l'appareil",
            delivered.contains("!isRemoteUrl(path) &&"),
        )
        assertTrue("le téléchargement sait demander une adresse", download.contains("suspend fun toUrl("))
        assertTrue("le contrôleur la lui demande", controller.contains("download.toUrl(file.path, destination, report)"))
        assertTrue(
            "et un chemin du serveur passe toujours par le Dashboard",
            controller.contains("download.toFile(file.path, destination, report)"),
        )
    }

    @Test
    fun `une cible que personne ne sert est dite avant l appui`() {
        assertTrue("la lecture du dossier dit si le nom y est", controller.contains("val size = served[file.name]"))
        assertTrue("et son silence ne conclut rien", controller.contains("val served = servedFolder(path) ?: return"))
        assertTrue("l'absence devient une phrase", controller.contains("DeliveredFiles.goneText("))
        assertTrue("qui ne promet aucun geste", controller.contains("served = ServedTarget.ABSENT"))
        assertTrue("la règle est bien celle qui décide", controller.contains("FileCardTargets.state("))
    }

    @Test
    fun `revoir un fichier ne depend pas de la forme du pointeur`() {
        // Un fichier déjà récupéré doit se retrouver **dans les fichiers de l'app** que le
        // pointeur soit un chemin du serveur ou une adresse : sans cela, revenir sur l'onglet
        // après avoir ouvert un fichier le remettait à « à télécharger » — « jamais les revoir »,
        // par une seconde porte.
        val prepare = controller
            .substringAfter("fun prepare(file: ConversationFile, scope: CoroutineScope)")
            .substringBefore("fun fetch(")
        assertFalse(
            "une adresse ne doit pas court-circuiter la copie locale",
            prepare.contains("DeliveredFiles.isRemoteUrl(path) -> Unit"),
        )
        assertTrue("la copie est cherchée", prepare.contains("val cached = target(path, file.name)"))
        assertTrue(
            "et un chemin du disque n'est pas confondu avec une adresse",
            prepare.contains("!DeliveredFiles.isRemoteUrl(path) && path.startsWith(\"/\")"),
        )
    }

    @Test
    fun `la copie d un fichier vu une fois survit a l appareil`() {
        assertTrue(
            "les copies vivent dans les fichiers de l'app",
            controller.contains("File(context.filesDir, \"conversation-files\")"),
        )
        assertFalse(
            "jamais dans le cache, que le système purge",
            controller.contains("File(context.cacheDir, \"conversation-files\")"),
        )
        assertTrue(
            "et les fichiers livrés du fil aussi",
            delivered.contains("File(context.filesDir, \"delivered\")"),
        )
    }

    @Test
    fun `reessayer reverifie au lieu de rejouer la meme demande morte`() {
        val body = controller.substringAfter("fun retry(file: ConversationFile, scope: CoroutineScope)").substringBefore("// -----")
        assertTrue("le cache du dossier est oublié", body.contains("forget(file.path)"))
        assertTrue("la cible est revérifiée", body.contains("resolve(file)"))
        assertTrue("et elle n'est récupérée que si elle est servie", body.contains("if (state(file.path) is FileAvailability.Remote) runCatching { runFetch("))
        val forget = controller.substringAfter("private fun forget(path: String)").substringBefore("// -----")
        assertTrue("oublier veut dire oublier la lecture du dossier", forget.contains("folders.keys.removeAll"))
    }
}
