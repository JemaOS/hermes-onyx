package com.onyx.app.ui.chat

/**
 * The chat keyboard shortcuts, ported from the `keyboardShortcut`s of `ContentView:540`.
 *
 * the reference attaches them to invisible buttons in the reference's tree; Android has no such
 * mechanism, so the screen decides by itself from the key and the modifiers. The whole decision
 * lives here, without depending on Compose, to stay testable on the JVM.
 *
 * Reference → Android mapping:
 *  - ⌘N  "New conversation"      → Ctrl+N
 *  - ⌘F  "Find in conversation"  → Ctrl+F
 *  - ⌘K  (conversation list)     → Ctrl+K: the thread search bar
 *  - ⌘↩  "Send"                  → Ctrl+Enter
 *  - ⌘.  "Stop reply"            → Ctrl+.
 *  - ⌘L  "Focus composer"        → Ctrl+L
 *  - ⌘E  "Export as Markdown"    → Ctrl+E
 *  - Escape closes whatever is open (search, menus, slash-command draft).
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

/** A named key, independent of Compose so the table stays a pure function. */
enum class ChatKey { N, K, F, L, E, PERIOD, ENTER, ESCAPE, OTHER }

/**
 * The shortcut this key triggers, or `null` if it does nothing.
 *
 * `ctrl` covers ⌘ on the reference and Ctrl on Android; without it, only Escape acts — as on the
 * reference, where the other shortcuts require the command key.
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
 * Does the action make sense in this state?
 *
 * the reference disables its buttons (`Send` during a turn, `Find` and `Export` without messages):
 * the shortcut must not do better than the button it doubles. `Send` in particular is reserved
 * outside streaming, where the composer offers steer/file instead.
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
