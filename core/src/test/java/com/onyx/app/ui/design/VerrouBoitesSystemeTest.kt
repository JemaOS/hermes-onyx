package com.onyx.app.ui.design

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou des boîtes et des bandeaux du système.**
 *
 * Le propriétaire a dit : « aucune boîte de dialogue ni bandeau du système dans toute l'appli ». Ce
 * n'est pas une intention à tenir une fois : c'est une **règle qui doit mordre** quand quelqu'un
 * réintroduit le défaut six mois plus tard, sans savoir qu'il existait.
 *
 * Le relevé exhaustif des usages du composant de dialogue et du bandeau du système
 * ([RapportSysteme] plus bas) est **gelé ici, dans le code** : la liste des fichiers où un `Dialog(`
 * du système est **assumé** (parce que c'est **notre** panneau dans une fenêtre du système — le
 * `Dialog` de Compose n'est qu'une fenêtre, pas une surface), et **aucune** occurrence d'un motif
 * interdit (`AlertDialog(`, `BasicAlertDialog(`, `Snackbar(`, `SnackbarHost(`).
 *
 * Le test échoue en **nommant le fichier et la ligne** de chaque nouvel usage — c'est la seule
 * manière de rendre le défaut réparable en un coup d'œil.
 *
 * Ce qui n'est **pas** interdit : le `Dialog(` de Compose lui-même, nos surfaces de la maison s'en
 * servent comme **fenêtre** (elles dessinent leur propre panneau : verre, rayon, marges, actions).
 * Ce que le test interdit, c'est un usage **nouveau** hors de la liste gelée, et toute boîte
 * réellement **du système** (une `AlertDialog` de Material, un `Snackbar` de Material).
 */
class VerrouBoitesSystemeTest {

    @Test
    fun `aucune boite ni bandeau du systeme neuf dans l appli`() {
        val findings = mutableListOf<SystemDialogAudit.Finding>()

        sourceRoots().forEach { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val rel = file.relativeTo(repoRoot).invariantSeparatorsPath
                    val source = file.readText()
                    // Les motifs interdits partout, sans exception.
                    findings += SystemDialogAudit.forbiddenFindings(rel, source)
                    // Les `Dialog(` : tolérés dans les fichiers gelés, à hauteur du compte gelé.
                    findings += SystemDialogAudit.dialogFindings(rel, source, BoitesAutorisees[rel] ?: 0)
                    // Les sélecteurs du système d'exploitation : tolérés nommément, jamais ailleurs.
                    findings += SystemDialogAudit.systemPickerFindings(rel, source, SelecteursDuSysteme[rel] ?: 0)
                }
        }

        assertTrue(
            "des surfaces du système sont apparues (fichier:ligne) :\n" +
                findings.joinToString("\n") { "  ${it.file}:${it.line}  [${it.kind}]  ${it.text.trim()}" },
            findings.isEmpty(),
        )
    }

    /**
     * **La preuve que le verrou mord** — sur une source fabriquée, pas sur le dépôt : un fichier neuf
     * qui ouvre une `AlertDialog` ou un `Snackbar` doit produire des constats **nommés**. Sans ce
     * second test, un verrou qui ne détecte rien passerait pour un verrou qui n'a rien à dire.
     */
    @Test
    fun `le verrou mord sur une source qui reintroduit le systeme`() {
        val neuve = """
            package x
            import androidx.compose.material3.AlertDialog
            import androidx.compose.material3.Snackbar
            import androidx.compose.ui.window.Dialog
            fun f() {
                Dialog(onDismissRequest = {}) {}
                AlertDialog(onDismissRequest = {})
                Snackbar(snackbarData)
            }
        """.trimIndent()

        val interdits = SystemDialogAudit.forbiddenFindings("x/Neuf.kt", neuve)
        assertTrue("AlertDialog n'est pas relevé", interdits.any { it.kind == SystemDialogAudit.Kind.ALERT_DIALOG })
        assertTrue("Snackbar n'est pas relevé", interdits.any { it.kind == SystemDialogAudit.Kind.SNACKBAR })
        assertTrue("la ligne n'est pas nommée", interdits.all { it.line > 0 && it.file == "x/Neuf.kt" })

        // Et un `Dialog(` dans un fichier **hors de la liste gelée** doit être relevé, nommé.
        val boites = SystemDialogAudit.dialogFindings("x/Neuf.kt", neuve, allowedCount = 0)
        assertTrue("le Dialog( neuf n'est pas relevé", boites.any { it.kind == SystemDialogAudit.Kind.WINDOW })
        assertTrue(
            "le constat doit nommer le fichier et la ligne",
            boites.any { it.file == "x/Neuf.kt" && it.line == 6 },
        )
    }

    // ============================================================================================
    //  Le relevé, gelé
    // ============================================================================================

    private val repoRoot: File = run {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
        requireNotNull(dir) { "racine du dépôt (settings.gradle.kts) introuvable depuis ${File("").absolutePath}" }
        dir
    }

    private fun sourceRoots(): List<File> =
        listOf(File(repoRoot, "core/src/main"), File(repoRoot, "app/src/main")).filter { it.isDirectory }

    companion object {

        /**
         * **Le relevé exhaustif des `Dialog(` — gelé.** Chaque entrée est un **fichier** où le
         * `Dialog` de Compose sert de **fenêtre** à une surface de la maison (le panneau est dessiné
         * par nous : verre, rayon, marges, actions). La valeur est le nombre d'occurrences attendu.
         *
         * Ce ne sont **pas** des boîtes du système : c'est notre panneau dans une fenêtre. Le
         * propriétaire ne les voit pas comme des boîtes du système ; la confirmation de suppression
         * (`OnyxConfirm`) est passée de la boîte qui *ressemblait* au système à cette famille-là.
         *
         * Un usage **neuf** (fichier absent, ou compte dépassé) fait mordre le verrou.
         */
        val BoitesAutorisees: Map<String, Int> = mapOf(
            // — La fenêtre d'une surface de la maison —
            "core/src/main/java/com/onyx/app/ui/design/OnyxAlert.kt" to 1,
            "core/src/main/java/com/onyx/app/ui/design/OnyxConfirm.kt" to 1,
            "core/src/main/java/com/onyx/app/ui/design/HouseSheet.kt" to 1,
            "core/src/main/java/com/onyx/app/ui/design/AttachmentSheet.kt" to 1,
            "core/src/main/java/com/onyx/app/ui/design/OnyxMenu.kt" to 2,
            "core/src/main/java/com/onyx/app/ui/rich/RichBlocks.kt" to 1,
            "app/src/main/java/com/onyx/app/ui/models/ModelSheet.kt" to 1,
            "app/src/main/java/com/onyx/app/ui/chat/components/ToolCallDetail.kt" to 1,
            "app/src/main/java/com/onyx/app/ui/chat/components/OnyxTopBar.kt" to 1,
            "app/src/main/java/com/onyx/app/ui/chat/components/ChatActionSheet.kt" to 1,
            "app/src/main/java/com/onyx/app/ui/conversations/ConversationActionSheet.kt" to 1,
            "app/src/main/java/com/onyx/app/ui/bots/RoomCreationDialog.kt" to 1,
            "app/src/main/java/com/onyx/app/ui/bots/BotsScreen.kt" to 1,
            "app/src/main/java/com/onyx/app/ui/settings/SettingsScreen.kt" to 1,
            // `BranchFromHereAlert.kt` a **quitté** cette liste : la confirmation de la branche ne
            // dessine plus sa propre fenêtre, elle passe par [OnyxConfirm] (une seule surface de
            // confirmation dans l'app). Un `Dialog(` neuf y serait donc relevé — c'est voulu.
        )

        /**
         * **Les exceptions nommées : les sélecteurs du système d'exploitation.**
         *
         * `DatePickerDialog` / `TimePickerDialog` **ne nous appartiennent pas** : ce sont les boîtes
         * de sélection de date et d'heure d'Android, avec leur propre apparence (matérielle) et leur
         * propre localisation par le système. On les garde — et on les nomme, fichier et compte — pour
         * qu'un usage **neuf** ailleurs soit relevé. Aucune autre exception n'est admise.
         */
        val SelecteursDuSysteme: Map<String, Int> = mapOf(
            "app/src/main/java/com/onyx/app/ui/cron/BotSchedulesScreen.kt" to 3,
        )
    }
}

/**
 * **Le relevé — le lecteur du source.** Il ne prétend pas lire l'arbre syntaxique : il lit les
 * **formes d'écriture** qui ouvrent une boîte du système, ce qui suffit à fermer la dérive (ces
 * formes sont la seule manière d'appeler un composant Compose). Le relevé imprimé dans le rapport
 * sort de ce même objet, donc le chiffre annoncé et le chiffre vérifié ne peuvent pas diverger.
 */
object SystemDialogAudit {

    enum class Kind { WINDOW, ALERT_DIALOG, SNACKBAR, SYSTEM_PICKER }

    data class Finding(val kind: Kind, val file: String, val line: Int, val text: String)

    /** Les motifs **interdits partout** — une vraie boîte ou un vrai bandeau du système, jamais la maison. */
    private val Interdits: List<Pair<Kind, String>> = listOf(
        Kind.ALERT_DIALOG to "AlertDialog(",
        Kind.ALERT_DIALOG to "BasicAlertDialog(",
        Kind.SNACKBAR to "SnackbarHost(",
        Kind.SNACKBAR to "Snackbar(",
        // Le bandeau du système d'Android — jamais le nôtre : la maison a son propre `ToastHost`
        // (une capsule dessinée), pas le `Toast` gris de la plateforme.
        Kind.SNACKBAR to "Toast.makeText(",
        Kind.SNACKBAR to "import android.widget.Toast",
    )

    /** Le `Dialog(` de Compose — une **fenêtre**, tolérée seulement dans les fichiers gelés. */
    private val Fenetre = Regex("""\bDialog\(""")

    /** Les sélecteurs du système d'exploitation — tolérés nommément. */
    private val Selecteur = Regex("""\b(DatePickerDialog|TimePickerDialog)\(""")

    fun forbiddenFindings(file: String, source: String): List<Finding> =
        Interdits.flatMap { (kind, token) -> occurrences(file, source, token, kind) }

    fun dialogFindings(file: String, source: String, allowedCount: Int): List<Finding> {
        val hits = source.lineSequence().withIndex().flatMap { (index, line) ->
            Fenetre.findAll(line).map { index + 1 to line }
        }.toList()
        // Toléré **à hauteur du compte gelé** : les premiers sont les nôtres, le suivant est un neuf.
        return hits.drop(allowedCount).map { (line, text) -> Finding(Kind.WINDOW, file, line, text) }
    }

    fun systemPickerFindings(file: String, source: String, allowedCount: Int): List<Finding> {
        val hits = source.lineSequence().withIndex().flatMap { (index, line) ->
            Selecteur.findAll(line).map { index + 1 to line }
        }.toList()
        return hits.drop(allowedCount).map { (line, text) -> Finding(Kind.SYSTEM_PICKER, file, line, text) }
    }

    private fun occurrences(file: String, source: String, token: String, kind: Kind): List<Finding> =
        source.lineSequence().withIndex()
            .filter { it.value.contains(token) }
            .map { (index, line) -> Finding(kind, file, index + 1, line) }
            .toList()
}
