package com.onyx.app.data.net

/** Ce qu'une action se lit sous l'aperçu: un verbe, puis son détail. */
data class ComputerActionText(val verb: String, val detail: String)

/**
 * Comment la dernière action se raconte à l'écran.
 *
 * Le verbe reste une clé courte (« click », « typing »…) que l'écran traduit; le détail garde ce
 * qui vient du pont tel quel — des coordonnées, un texte, une commande — parce qu'un détail
 * traduit ne serait plus la vérité de ce qui s'est passé.
 */
fun ComputerAction?.text(): ComputerActionText? {
    if (this == null) return null
    return when (kind) {
        "click" -> ComputerActionText("click", listOfNotNull(x, y).joinToString(","))
        "type" -> ComputerActionText("typing", "« $detail »")
        "drag" -> ComputerActionText("drag", detail)
        "scroll" -> ComputerActionText("scroll", detail)
        "key" -> ComputerActionText("key", detail)
        "run" -> ComputerActionText("command", detail)
        else -> ComputerActionText("action", detail)
    }
}
