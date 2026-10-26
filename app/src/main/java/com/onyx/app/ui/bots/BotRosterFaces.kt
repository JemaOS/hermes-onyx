package com.onyx.app.ui.bots

import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.ui.design.BotFace

/**
 * Résout une fois les visages du roster pour tous les écrans (liste, groupe, voix et piles).
 *
 * Les choix locaux priment sur `ui_meta.onyx.face`, comme dans `BotFaceStore`. Les anciennes orbes
 * purement numériques restent inchangées; les autres codes sont relus par [BotFace.parse], puis
 * réécrits dans le format serveur après attribution sans doublons.
 */
fun List<BotProfile>.withOnyxFaces(localCodes: Map<String, String?> = emptyMap()): List<BotProfile> {
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
