package com.onyx.app.ui.chat

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * La touche nommée que Compose rapporte, ou [ChatKey.OTHER].
 *
 * Compose est la seule partie de la table des raccourcis qui ne peut pas être testée sur la JVM:
 * elle est isolée ici, et tout ce qui décide vit dans `chatShortcutFor`.
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
 * Le raccourci que cet événement déclenche, ou `null`.
 *
 * Comme le `onPreviewKeyEvent` de `OnyxComposer` pour Ctrl+V: la touche de commande couvre ⌘
 * et Ctrl sur un clavier Android/externe, et Échap agit sans modificateur.
 */
fun chatShortcutOf(event: KeyEvent): ChatShortcut? {
    if (event.type != KeyEventType.KeyDown) return null
    val ctrl = event.isCtrlPressed || event.isMetaPressed
    return chatShortcutFor(chatKeyOf(event.key), ctrl)
}
