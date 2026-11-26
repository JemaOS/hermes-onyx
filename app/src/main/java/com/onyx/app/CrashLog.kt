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
 * **The last crash, written on the phone.**
 *
 * A crash you cannot read cannot be fixed: the owner's reports describe
 * symptoms ("it crashes often with a lot of conversations" — French UI string « ça crash souvent avec beaucoup de conversations ») and, without the stack, every
 * fix is only a hypothesis. This class installs an uncaught-exception handler
 * that writes the **full** trace (cause included) to a file, then lets Android
 * do its job: the app closes as before, the system dialog shows as before.
 *
 * The files live in `Android/data/com.onyx.app/files/crashes/` — readable from a
 * file manager, so they can be sent without a cable or a computer. Five at most: the latest
 * overwrites the oldest, a crash loop does not fill up the phone.
 *
 * Nothing in them is invented: the app version, the model and the Android version come from the
 * system, the stack comes from the exception itself.
 */
object CrashLog {

    /** What the previous handler did — we hand control back to it after writing. */
    private var previous: Thread.UncaughtExceptionHandler? = null

    /** The traces folder, or `null` when external storage is not mounted. */
    fun directory(context: Context): File? {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "crashes").apply { if (!exists()) mkdirs() }
    }

    /** The recorded traces, most recent first. */
    fun reports(context: Context): List<File> =
        directory(context)?.listFiles { file -> file.extension == "txt" }
            ?.sortedByDescending { it.name }
            .orEmpty()

    /**
     * Installs the handler. **Idempotent**: a second call does not install the same
     * hook twice (and does not lose the original handler).
     */
    fun install(context: Context) {
        if (previous != null) return
        val app = context.applicationContext
        val existing = Thread.getDefaultUncaughtExceptionHandler()
        previous = existing
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
            // Control returns to the system: the "Onyx s'est arrêté" (Onyx has stopped) dialog stays its own.
            if (existing != null) existing.uncaughtException(thread, error)
        }
    }

    /** Writes the trace for [error], and returns the written file (for a test, or a share). */
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
            // Five traces at most: the oldest goes.
            dir.listFiles { f -> f.extension == "txt" }
                ?.sortedByDescending { it.name }
                ?.drop(MAX_REPORTS)
                ?.forEach { it.delete() }
            file
        }.getOrNull()
    }

    /** The number of traces kept. */
    private const val MAX_REPORTS = 5
}
