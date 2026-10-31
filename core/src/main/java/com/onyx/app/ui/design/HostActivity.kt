package com.onyx.app.ui.design

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * L'activité qui héberge ce contexte, en remontant la chaîne des `ContextWrapper`.
 *
 * Sert aux gestes qui ont besoin de l'activité elle-même — recréer l'écran quand la langue de
 * l'app change, par exemple. `null` quand le contexte n'appartient à aucune activité (un
 * `applicationContext`), auquel cas l'appelant ne fait rien plutôt que de planter.
 */
fun Context.hostActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
