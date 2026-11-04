package com.onyx.app

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * **Le dernier plantage, écrit sur le téléphone.**
 *
 * Un plantage qu'on ne peut pas lire ne se corrige pas : les rapports du propriétaire décrivent des
 * symptômes (« ça crash souvent avec beaucoup de conversations ») et, sans la pile, chaque
 * correction n'est qu'une hypothèse. Cette classe installe un gestionnaire d'exceptions non
 * rattrapées qui écrit la trace **complète** (cause incluse) dans un fichier, puis laisse Android
 * faire son travail : l'app se ferme comme avant, le dialogue système s'affiche comme avant.
 *
 * Les fichiers vivent dans `Android/data/com.onyx.app/files/crashes/` — lisibles depuis un
 * gestionnaire de fichiers, donc envoyables sans câble ni ordinateur. Cinq au maximum : le dernier
 * écrase le plus ancien, un plantage en boucle ne remplit pas le téléphone.
 *
 * Rien n'y est inventé : la version de l'app, le modèle et la version d'Android viennent du
 * système, la pile vient de l'exception elle-même.
 */
object CrashLog {

    /** Ce que le gestionnaire précédent faisait — on lui rend la main après avoir écrit. */
    private var previous: Thread.UncaughtExceptionHandler? = null

    /** Le dossier des traces, ou `null` quand le stockage externe n'est pas monté. */
    fun directory(context: Context): File? {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "crashes").apply { if (!exists()) mkdirs() }
    }

    /** Les traces enregistrées, la plus récente d'abord. */
    fun reports(context: Context): List<File> =
        directory(context)?.listFiles { file -> file.extension == "txt" }
            ?.sortedByDescending { it.name }
            .orEmpty()

    /**
     * Installe le gestionnaire. **Idempotent** : un second appel ne pose pas deux fois le même
     * crochet (et ne perd pas le gestionnaire d'origine).
     */
    fun install(context: Context) {
        if (previous != null) return
        val app = context.applicationContext
        val existing = Thread.getDefaultUncaughtExceptionHandler()
        previous = existing
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
            // La main revient au système : le dialogue « Onyx s'est arrêté » reste le sien.
            if (existing != null) existing.uncaughtException(thread, error)
        }
    }

    /** Écrit la trace pour [error], et rend le fichier écrit (pour un test, ou un partage). */
    fun write(context: Context, thread: Thread, error: Throwable): File? {
        val dir = directory(context) ?: return null
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "crash-$stamp.txt")

        val trace = StringWriter().also { buffer ->
            PrintWriter(buffer).use { writer ->
                writer.println("Onyx — rapport de plantage")
                writer.println("version   : ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                writer.println("appareil  : ${Build.MANUFACTURER} ${Build.MODEL}")
                writer.println("android   : ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                writer.println("fil       : ${thread.name}")
                writer.println("quand     : ${Date()}")
                writer.println()
                error.printStackTrace(writer)
            }
        }.toString()

        return runCatching {
            file.writeText(trace)
            // Cinq traces au plus : le plus ancien part.
            dir.listFiles { f -> f.extension == "txt" }
                ?.sortedByDescending { it.name }
                ?.drop(MAX_REPORTS)
                ?.forEach { it.delete() }
            file
        }.getOrNull()
    }

    /** Le nombre de traces gardées. */
    private const val MAX_REPORTS = 5
}
