package com.onyx.app.ui.bots

import com.onyx.app.data.model.serve.BotProfile

/**
 * A profile's face, resolved in **a single place** for whatever draws it.
 *
 * The bot list, a room's row, the header and the large faces of a room, the
 * bots' replies, the `@` mentions and the member stack all go through [codeOf]: that is what
 * guarantees that the same bot shows **the same face everywhere**. Two resolution paths
 * gave two faces for a single bot — the list showed the one from its profile, the room
 * another, inferred from the name alone —, and the person saw one creature in the list and another on
 * entering the room.
 *
 * [codeOf] returns the profile's code, or `null` when nothing fixes it. A `null` is not a draw:
 * without a code, it is the **name** that decides, by the same computation every time — a bot's
 * face without an assigned face is therefore the same on every opening.
 *
 * The two sources, in this order:
 * 1. what the phone keeps for this profile ([BotFaceStore], the choice made in the face
 *    picker), already merged into the table by [withSwiftFaces];
 * 2. what the server writes for it (`ui_meta.onyx.face`), likewise merged into the table: it
 *    assigns the creatures **once for the whole roster**, without the same one twice.
 *
 * A profile the table does not know falls back to the local choice, then to `null`.
 *
 * **A screen therefore never reads `BotFaceStore` to draw**: it reads this function, on the
 * roster table as [withSwiftFaces] produced it.
 */
fun interface BotFaceResolver {

    /** The face code of this profile; `null` lets the name decide, as everywhere else. */
    fun codeOf(profile: String): String?

    companion object {

        /** No resolved face: the names decide alone. */
        val none: BotFaceResolver = BotFaceResolver { null }

        /**
         * The resolver of a roster: [resolved] is the table of faces by profile name (as
         * [withSwiftFaces] renders it), [local] the choice kept by the phone for a name that the
         * table does not carry.
         */
        fun of(
            resolved: Map<String, String>,
            local: (String) -> String? = { null },
        ): BotFaceResolver = BotFaceResolver { profile -> resolved[profile] ?: local(profile) }

        /**
         * The resolver of the bots as a screen has them: each already carries its resolved face
         * (`BotProfile.faceCode`, the output of [withSwiftFaces]), including a demo
         * stand-in. A bot without a code does not enter the table: its name will decide alone.
         */
        fun of(bots: List<BotProfile>): BotFaceResolver =
            of(bots.mapNotNull { bot -> bot.faceCode?.let { code -> bot.name to code } }.toMap())
    }
}
