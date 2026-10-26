package com.onyx.app.ui.rich

import android.content.Context

/**
 * La hauteur qu'un bloc riche a prise la dernière fois.
 *
 * Comme `WebBlockHeights`: un diagramme ou une page mesuré une fois garde sa
 * hauteur, si bien qu'il ne saute pas quand le fil se redessine ou qu'on remonte le lire.
 */
object WebBlockHeights {

    private val memory = mutableMapOf<String, Int>()
    private var prefs: android.content.SharedPreferences? = null

    /** À appeler une fois au démarrage, avec le contexte de l'application. */
    fun install(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("onyx_web_heights", Context.MODE_PRIVATE)
    }

    fun key(kind: String, source: String): String = "$kind:${source.hashCode()}"

    fun height(key: String): Int? = memory[key] ?: prefs?.getInt(key, 0)?.takeIf { it > 0 }

    fun remember(key: String, height: Int) {
        if (height <= 0) return
        memory[key] = height
        prefs?.edit()?.putInt(key, height)?.apply()
    }
}
