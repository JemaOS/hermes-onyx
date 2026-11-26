package com.onyx.app.ui.bots

import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.ui.design.BotFace

/**
 * Resolves the roster's faces once for every screen (list, group, voice and stacks).
 *
 * Local choices take precedence over `ui_meta.onyx.face`, as in `BotFaceStore`. Old purely
 * numeric orbs stay unchanged; the other codes are read back by [BotFace.parse], then
 * rewritten in the server format of the reference after assignment without duplicates.
 */
fun List<BotProfile>.withSwiftFaces(localCodes: Map<String, String?> = emptyMap()): List<BotProfile> {
    val raw = associate { bot -> bot.name to (localCodes[bot.name] ?: bot.faceCode) }
    val picked = raw.mapNotNull { (name, code) ->
        BotFace.parse(code)?.let { face -> name to face }
    }.toMap()
    val resolved = BotFace.faces(map { it.name }, picked)

    return map { bot ->
        val old = raw[bot.name]
        when {
            old?.toIntOrNull() != null -> bot.copy(faceCode = old)
            else -> bot.copy(faceCode = resolved[bot.name]?.code())
        }
    }
}
