package com.onyx.app.ui.bots

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.serve.GroupEvent
import com.onyx.app.data.model.serve.GroupMember

/**
 * **A room's journal, read like a conversation's messages.**
 *
 * This is what gives the room's **Files** tab and **Feed** the same substance as a bot's: the
 * exchanged files (by `ChatFiles.conversationFiles`, which reads the **raw** text where
 * `DeliveredFiles` spots the attached paths), and the bots' replies as dated cards.
 *
 * **Each message's author travels with it** (`ChatMessage.authorProfile`): it is the member's
 * **profile name**, read by the **same** key as the thread — the event's `actorID`, resolved by
 * `GroupMember.profile` (see `RoomEventRow`, which sets the name and face of a reply from it).
 * Without this, the Feed did not know who had replied and painted the room's face on all its
 * cards — the "all cards have the same face" defect.
 *
 * Each room therefore only has **its** files: the substance comes from its own journal
 * (`state.rows`), never from a shared store. The function is **pure** — the parity lock
 * queries it without a device. [members] is the list of the room's members; if absent, no author
 * is named (the Feed then falls back on the room's face).
 */
internal fun roomThreadMessages(
    rows: List<GroupEvent>,
    members: List<GroupMember> = emptyList(),
): List<ChatMessage> =
    rows.filter { it.isMessage }.map { event ->
        ChatMessage(
            id = "room-seq-" + event.seq,
            role = if (event.kind == "message.user") Role.USER else Role.ASSISTANT,
            text = event.text,
            createdAt = event.createdAtMillis,
            // The member who spoke, by the same key as the thread: `room.member(actorID).profile`.
            authorProfile = members.firstOrNull { it.id == event.actorID }?.profile.orEmpty(),
        )
    }
