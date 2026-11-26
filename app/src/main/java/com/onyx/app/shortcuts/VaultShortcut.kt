package com.onyx.app.shortcuts

import android.content.Context
import android.content.pm.ShortcutManager

/**
 * The **"Secret vault"** shortcut of the long-press menu on the icon.
 *
 * The tile is declared in `res/xml/shortcuts.xml` (it does not depend on any server data),
 * but it must only appear on a server that **serves** the `onyx-vault` plugin: a tile that opens
 * an empty page is a dead tap, and the house prefers an absent entry. The launcher knows exactly
 * how to do that — the tile stays declared and it is its **activation** that follows the proof.
 *
 * [sync] is therefore called **once per proof** (the Settings screen carries it, since it is the
 * one that probes the capabilities): `true` enables the tile, `false` removes it from the menu. A
 * device that has never probed keeps the declared state — the tile is there, and the Vault page
 * itself says what is missing if the server does not serve it.
 */
object VaultShortcut {

    /** The tile's identifier, the one from `res/xml/shortcuts.xml`. */
    const val ID = "vault"

    /** What the launcher writes under the removed tile, when it accepts it. */
    private const val WHY = "Ce serveur ne sert pas le coffre de secrets."

    fun sync(context: Context, ready: Boolean) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        // The launcher may refuse (work profile, restricted mode): that is not an error of
        // the app, and nothing must break because of it.
        runCatching {
            if (ready) manager.enableShortcuts(listOf(ID))
            else manager.disableShortcuts(listOf(ID), WHY)
        }
    }
}
