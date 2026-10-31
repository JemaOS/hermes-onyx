package com.onyx.app.ui.chat

/**
 * Les raccourcis clavier du chat, portés depuis les `keyboardShortcut` de `ContentView:540`.
 *
 * référence les pose sur des boutons invisibles dans l'arbre la référence ; Android n'a pas ce mécanisme,
 * donc l'écran décide lui-même à partir de la touche et des modificateurs. Toute la décision
 * vit ici, sans dépendre de Compose, pour rester testable sur la JVM.
 *
 * Correspondance référence → Android :
 *  - ⌘N  « New conversation »      → Ctrl+N
 *  - ⌘F  « Find in conversation »  → Ctrl+F
 *  - ⌘K  (liste des conversations) → Ctrl+K : la barre de recherche du fil
 *  - ⌘↩  « Send »                  → Ctrl+Entrée
 *  - ⌘.  « Stop reply »            → Ctrl+.
 *  - ⌘L  « Focus composer »        → Ctrl+L
 *  - ⌘E  « Export as Markdown »    → Ctrl+E
 *  - Échap ferme ce qui est ouvert (recherche, menus, brouillon de commande).
 */
enum class ChatShortcut {
    NEW_CONVERSATION,
    FIND,
    SEND,
    STOP,
    FOCUS_COMPOSER,
    EXPORT,
    CLOSE,
}

/** Une touche nommée, indépendante de Compose pour que la table reste une fonction pure. */
enum class ChatKey { N, K, F, L, E, PERIOD, ENTER, ESCAPE, OTHER }

/**
 * Le raccourci que déclenche cette touche, ou `null` si elle ne fait rien.
 *
 * `ctrl` couvre ⌘ sur la référence et Ctrl sur Android ; sans lui, seule Échap agit — comme référence, où
 * les autres raccourcis exigent la touche de commande.
 */
fun chatShortcutFor(key: ChatKey, ctrl: Boolean): ChatShortcut? = when {
    key == ChatKey.ESCAPE -> ChatShortcut.CLOSE
    !ctrl -> null
    key == ChatKey.N -> ChatShortcut.NEW_CONVERSATION
    key == ChatKey.K -> ChatShortcut.FIND
    key == ChatKey.F -> ChatShortcut.FIND
    key == ChatKey.L -> ChatShortcut.FOCUS_COMPOSER
    key == ChatKey.E -> ChatShortcut.EXPORT
    key == ChatKey.PERIOD -> ChatShortcut.STOP
    key == ChatKey.ENTER -> ChatShortcut.SEND
    else -> null
}

/**
 * L'action a-t-elle un sens dans cet état ?
 *
 * référence désactive ses boutons (`Send` pendant un tour, `Find` et `Export` sans message) : le
 * raccourci ne doit pas mieux faire que le bouton qu'il double. `Send` en particulier est
 * réservé hors streaming, où le composeur propose steer/file à la place.
 */
fun allowsChatShortcut(
    shortcut: ChatShortcut,
    isStreaming: Boolean,
    hasDraft: Boolean,
    hasMessages: Boolean,
    searchOpen: Boolean,
): Boolean = when (shortcut) {
    ChatShortcut.NEW_CONVERSATION -> true
    ChatShortcut.FIND -> hasMessages
    ChatShortcut.SEND -> hasDraft && !isStreaming
    ChatShortcut.STOP -> isStreaming
    ChatShortcut.FOCUS_COMPOSER -> !isStreaming || hasDraft
    ChatShortcut.EXPORT -> hasMessages
    ChatShortcut.CLOSE -> searchOpen
}
