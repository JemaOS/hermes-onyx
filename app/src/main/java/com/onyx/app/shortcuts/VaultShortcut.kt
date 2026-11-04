package com.onyx.app.shortcuts

import android.content.Context
import android.content.pm.ShortcutManager

/**
 * Le raccourci **« Coffre de secrets »** du menu d'appui long sur l'icône.
 *
 * La tuile est déclarée dans `res/xml/shortcuts.xml` (elle ne dépend d'aucune donnée du serveur),
 * mais elle ne doit apparaître que sur un serveur qui **sert** le greffon `onyx-vault` : une tuile
 * qui ouvre une page vide est un tap mort, et la maison préfère une entrée absente. Le lanceur sait
 * exactement faire ça — la tuile reste déclarée et c'est son **activation** qui suit la preuve.
 *
 * [sync] est donc appelée **une fois par preuve** (l'écran des Réglages la porte, puisque c'est lui
 * qui sonde les capacités) : `true` active la tuile, `false` la retire du menu. Un appareil qui n'a
 * jamais sondé garde l'état déclaré — la tuile est là, et la page du Coffre dit elle-même ce qui
 * manque si le serveur ne la sert pas.
 */
object VaultShortcut {

    /** L'identifiant de la tuile, celui de `res/xml/shortcuts.xml`. */
    const val ID = "vault"

    /** Ce que le lanceur écrit sous la tuile retirée, quand il l'accepte. */
    private const val WHY = "Ce serveur ne sert pas le coffre de secrets."

    fun sync(context: Context, ready: Boolean) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        // Le lanceur peut refuser (profil de travail, mode restreint) : ce n'est pas une erreur de
        // l'app, et rien ne doit casser à cause de ça.
        runCatching {
            if (ready) manager.enableShortcuts(listOf(ID))
            else manager.disableShortcuts(listOf(ID), WHY)
        }
    }
}
