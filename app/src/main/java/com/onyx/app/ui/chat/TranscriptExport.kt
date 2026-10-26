package com.onyx.app.ui.chat

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider

/**
 * « Export as Markdown »: le fil dans un fichier, confié au partage d'Android.
 *
 * Comme `TranscriptExporter.file(title:messages:)`: le même Markdown, le même geste
 * — un fichier `.md` qu'on envoie où l'on veut.
 */
fun shareTranscript(context: Context, markdown: String, title: String) {
    val safe = title.ifBlank { "Conversation" }
        .replace(Regex("""[\\/:*?"<>|]"""), "-")
        .take(60)
    val folder = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
    val file = java.io.File(folder, "$safe.md")
    file.writeText(markdown)

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/markdown"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, safe)
        putExtra(Intent.EXTRA_TEXT, markdown)
        clipData = ClipData.newRawUri(safe, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Export as Markdown"))
}
