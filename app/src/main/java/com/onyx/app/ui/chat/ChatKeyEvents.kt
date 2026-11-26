package com.onyx.app.ui.chat

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * The named key that Compose reports, or [ChatKey.OTHER].
 *
 * Compose is the only part of the shortcut table that cannot be tested on the JVM:
 * it is isolated here, and everything that decides lives in `chatShortcutFor`.
 */
fun chatKeyOf(key: Key): ChatKey = when (key) {
    Key.N -> ChatKey.N
    Key.K -> ChatKey.K
    Key.F -> ChatKey.F
    Key.L -> ChatKey.L
    Key.E -> ChatKey.E
    Key.Period -> ChatKey.PERIOD
    Key.Enter -> ChatKey.ENTER
    Key.Escape -> ChatKey.ESCAPE
    else -> ChatKey.OTHER
}

/**
 * The shortcut this event triggers, or `null`.
 *
 * Mirrors `OnyxComposer`'s `onPreviewKeyEvent` for Ctrl+V: the command key covers ⌘
 * on the reference and Ctrl on an Android/external keyboard, and Escape acts with no modifier.
 */
fun chatShortcutOf(event: KeyEvent): ChatShortcut? {
    if (event.type != KeyEventType.KeyDown) return null
    val ctrl = event.isCtrlPressed || event.isMetaPressed
    return chatShortcutFor(chatKeyOf(event.key), ctrl)
}
