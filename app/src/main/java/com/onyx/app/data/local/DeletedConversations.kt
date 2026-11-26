package com.onyx.app.data.local

import android.content.Context

/**
 * **The mark of deleted conversations**: the identifiers this phone has removed.
 *
 * This is what makes a deletion **durable**. The local store merely stops writing the
 * conversation to its file; on its own, that is not enough: the same conversation can be
 * rewritten later by a path that did not know it had been deleted — the end of a turn
 * that records the feed (`ChatViewModel.persistCurrent`), a bot attachment read back from the server, a
 * profile migration. With the mark, these paths find the door closed: `save` refuses a
 * marked identifier, and no resync can bring it back.
 *
 * The mark lives **outside the conversations file** (the phone's preferences): it therefore survives
 * restarts, and the "Erase everything" that empties the file.
 *
 * Only the **banner's undo** undoes a mark ([unmark]): it is the only thing that
 * truly brings back a conversation the person had just deleted.
 */
interface DeletedConversations {

    /** The identifiers marked as deleted. */
    fun marked(): Set<String>

    /** Marks a conversation as deleted. */
    fun mark(id: String)

    /** Undoes the mark — the banner's "Undo", and it alone. */
    fun unmark(id: String)

    companion object {
        /** A store without disk: the tests, and windows without a repository. */
        val InMemory: DeletedConversations get() = InMemoryDeletedConversations()
    }
}

/** [DeletedConversations] in memory. */
class InMemoryDeletedConversations(
    private val ids: MutableSet<String> = mutableSetOf(),
) : DeletedConversations {

    override fun marked(): Set<String> = ids.toSet()

    override fun mark(id: String) {
        ids += id
    }

    override fun unmark(id: String) {
        ids -= id
    }
}

/**
 * [DeletedConversations] on the **phone's preferences**: the mark survives the app's restart,
 * like the other states the server does not hold (the seen activity, the bots' order).
 */
class SharedPreferencesDeletedConversations(context: Context) : DeletedConversations {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // `getStringSet` returns the file's **live** set: it is copied before being used, otherwise
    // a subsequent write would modify it under our feet.
    override fun marked(): Set<String> = prefs.getStringSet(KEY, emptySet()).orEmpty().toSet()

    override fun mark(id: String) {
        prefs.edit().putStringSet(KEY, marked() + id).apply()
    }

    override fun unmark(id: String) {
        prefs.edit().putStringSet(KEY, marked() - id).apply()
    }

    private companion object {
        const val FILE = "onyx_deleted_conversations"
        const val KEY = "deleted"
    }
}
