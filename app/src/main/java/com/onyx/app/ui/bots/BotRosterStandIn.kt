package com.onyx.app.ui.bots

import com.onyx.app.data.model.serve.BotProfile

/**
 * La doublure de QA du roster : les bots d'exemple, pour un appareil qui n'a aucun Dashboard où
 * les lire.
 *
 * Le la référence a exactement la même chose — `ConversationsView.demoBots`
 * (Echo/Views/ConversationsView:2268), posé dans le `BotDirectory` quand
 * `-echo.demoBots` est passé au lancement. Ici c'est l'extra `onyx.demoBots` (`am start -e
 * onyx.demoBots true`) qui la pose. Rien ne la lit autrement : sans l'extra, le roster est
 * celui du serveur, ou vide.
 *
 * Les quatre bots sont ceux de la doublure de la référence, avec leurs mêmes noms et libellés :
 * le profil principal (`@hermes`, sans titre), `scout` (« Scout »), `writer` (« Quill ») et
 * `fixit` (« Fix-it ») — de quoi voir la liste des mentions `@` avec plusieurs bots, l'ordre du
 * roster (principal, puis le plus récemment parlé) et le filtrage par ce qui suit le « @ ».
 */
object BotRosterStandIn {

    /** Les profils de la doublure, avec leurs dates d'activité relatives à [now]. */
    fun bots(now: Long = System.currentTimeMillis()): List<BotProfile> = listOf(
        BotProfile(
            name = "default",
            isDefault = true,
            lastActive = now - 3_600_000,
            preview = "Done: the reminder is set for 4 pm.",
        ),
        BotProfile(
            name = "scout",
            title = "Scout",
            description = "Finds things out",
            lastActive = now - 600_000,
            preview = "Three trails under two hours from you, all open.",
        ),
        BotProfile(
            name = "writer",
            displayName = "Quill",
            description = "Edits and drafts",
            lastActive = now - 120_000,
            preview = "Want me to carry that through the next paragraph?",
        ),
        BotProfile(
            name = "fixit",
            title = "Fix-it",
            description = "Looks after the home server",
        ),
    )
}
