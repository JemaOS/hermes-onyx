package com.onyx.app.debug

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import java.io.File
import java.io.FileOutputStream

/**
 * Capture l'écran de l'app dans un fichier PNG, pour comparer l'UI à celle de l'app de référence.
 *
 * Uniquement dans la variante debug : la capture système (`screencap`) passe par le compositeur,
 * que l'émulateur de cette machine ne sait pas servir (assertion DMA). Ici on dessine
 * directement la vue de la fenêtre dans un Bitmap — même pixels, autre chemin.
 *
 * Usage : adb shell content call --uri content://com.onyx.app.capture --method capture
 */
class CaptureProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val answer = Bundle()
        if (method != "capture") return answer

        val root = topRootView()
        if (root == null) {
            answer.putString("result", "no window")
            return answer
        }

        val width = root.width.takeIf { it > 0 } ?: root.measuredWidth
        val height = root.height.takeIf { it > 0 } ?: root.measuredHeight
        if (width <= 0 || height <= 0) {
            answer.putString("result", "empty window")
            return answer
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))

        val folder = File(context!!.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "captures")
        folder.mkdirs()
        val name = arg?.takeIf { it.isNotBlank() } ?: "screen"
        val file = File(folder, "$name.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()

        answer.putString("result", "ok")
        answer.putString("path", file.absolutePath)
        answer.putInt("width", width)
        answer.putInt("height", height)
        return answer
    }

    /** La vue de la première fenêtre, celle de l'activité au premier plan. */
    private fun topRootView(): View? = runCatching {
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        val getRootViews = global.getDeclaredMethod("getRootViews").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val views = getRootViews.invoke(instance) as? List<View>
        views?.lastOrNull()
    }.getOrNull()

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int = 0
}
