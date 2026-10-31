package com.onyx.app.ui.bots

import androidx.compose.ui.graphics.Color
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.serve.GroupEvent
import com.onyx.app.ui.design.OnyxContrast
import com.onyx.app.ui.design.readableInk
import com.onyx.app.ui.theme.ReferencePalette
import java.io.File
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou de la parité groupe ↔ conversation à un bot.**
 *
 * Trois choses, une par clause du lot, toutes vérifiables sans appareil :
 *
 *  1. **aucun écran ne redessine sa propre commande d'envoi** — le groupe passe par le compositeur
 *     de la maison (`OnyxComposer`), et la flèche d'envoi ne se peint **qu'à un seul endroit** de
 *     tout le module `app` ;
 *  2. **l'encre d'une commande est MESURÉE**, jamais écrite à la main : c'est ce qui la tient au
 *     seuil de la maison dans les deux apparences ;
 *  3. **la route de groupe compose le panneau de fichiers et la barre d'onglets** — les mêmes que
 *     ceux d'un bot.
 */
class GroupePariteVerrouTest {

    private fun read(path: String): String {
        val file = listOf(path, "../$path").map(::File).firstOrNull { it.isFile }
            ?: error("fichier introuvable : $path")
        return file.readText()
    }

    private val group = read("src/main/java/com/onyx/app/ui/bots/GroupComposer.kt")
    private val room = read("src/main/java/com/onyx/app/ui/bots/GroupRoomScreen.kt")
    private val composer = read("src/main/java/com/onyx/app/ui/chat/components/OnyxComposer.kt")

    // — 1 · un seul dessin de la commande d'envoi —

    @Test
    fun `la salle ne redessine plus sa propre commande d envoi`() {
        assertTrue(
            "GroupComposer.kt doit appeler le compositeur de la maison",
            group.contains("OnyxComposer("),
        )
        assertTrue(
            "GroupComposer.kt ne doit plus peindre un bouton rond à lui (une seconde commande d'envoi)",
            !group.contains("ComposerCircleButton("),
        )
        assertTrue(
            "la cascade de bouton propre au groupe (une seconde décision d'envoi) ne doit plus exister",
            !group.contains("GroupComposerAction"),
        )
    }

    @Test
    fun `aucun ecran du module app ne peint la fleche d envoi hors de la maison`() {
        val sendButton = Regex("""ComposerCircleButton\(\s*symbol = Symbols\.ArrowUp""")
        val offenders = File("src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.name != "OnyxComposer.kt" }
            .filter { sendButton.containsMatchIn(it.readText()) }
            .map { it.path }
            .toList()
        assertTrue(
            "seule la maison dessine la flèche d'envoi ; ces écrans en peignent une seconde : $offenders",
            offenders.isEmpty(),
        )
    }

    // — 2 · l'encre d'une commande est mesurée, et elle tient le seuil —

    @Test
    fun `le compositeur de la maison mesure l encre de ses commandes`() {
        // **Arbitrage de la fusion avec `lot/bouton-envoi`.** Ce verrou exigeait d'abord que le
        // compositeur de la maison peigne par `readableInk(theme.userText, theme.accent)` et
        // `tint = theme.fill` : c'était le fichier **avant** `lot/bouton-envoi`. Ce lot a mesuré ce
        // fichier (`CompositeurCommandsVerrouTest`, 3 tests) et l'a corrigé : `theme.fill` seul
        // contre le sol de la plaque = **1,10:1**, `theme.accent` seul = 1,14:1 sur la page du thème
        // « Code » en clair — tous deux **sous** le plancher d'icône de 3:1. L'intention de ce
        // verrou ne change pas : la maison ne peint **jamais** une couleur du thème crue, elle peint
        // une encre **MESURÉE**. La mesure porte maintenant contre la **plaque** (`ComposerCommands`),
        // ce qui est plus fort que `readableInk` sur la teinte seule.
        assertTrue(
            "l'envoi de la maison peint son encre MESURÉE sur le disque réellement peint " +
                "(`ComposerCommands.inkOnDisc`), pas une couleur du thème crue",
            composer.contains("ComposerCommands.inkOnDisc("),
        )
        assertTrue(
            "le fond d'une commande de la maison se mesure contre le sol de la plaque " +
                "(`ComposerCommands.disc`)",
            composer.contains("ComposerCommands.disc("),
        )
        assertTrue(
            "le vocal de la maison est le fond de contrôle NEUTRE (jamais l'accent) : " +
                "`ComposerCommands.disc(theme, theme.fill)` — `theme.fill` seul valait 1,10:1 " +
                "contre le sol sombre de la plaque",
            composer.contains("ComposerCommands.disc(theme, theme.fill)"),
        )
        assertTrue(
            "la salle ne peint plus AUCUN glyphe de commande à elle (l'envoi, le micro, l'onde)",
            !group.contains("ComposerCircleButton(") &&
                !group.contains("waveform = true") &&
                !group.contains("Symbols.ArrowUp,") &&
                !group.contains("Symbols.MicOutline,"),
        )
    }

    /**
     * **La mesure qui fait mordre le verrou.** Un envoi dont l'encre serait laissée à `theme.userText`
     * (blanc, comme le faisait le dessin du groupe) tombe **sous le seuil de la maison** dès que
     * l'accent est celui d'un bot : mesuré 1,92:1 sur l'ocre, 2,25:1 sur le vert, 2,72:1 sur le
     * turquoise, 3,29:1 sur le bleu clair. `readableInk` ramène chacune au seuil.
     */
    @Test
    fun `l encre mesuree tient le seuil sur tous les accents, l encre crue non`() {
        // Les accents profonds des visages de bot (`BotFace` : la seconde couleur de chaque paire).
        val botAccents = listOf(
            Color(0.30f, 0.55f, 0.96f), // 3,29:1 en blanc — sous le seuil
            Color(0.96f, 0.68f, 0.22f), // 1,92:1
            Color(0.91f, 0.34f, 0.62f), // 3,34:1
            Color(0.36f, 0.42f, 0.94f), // 4,36:1
            Color(0.13f, 0.69f, 0.60f), // 2,72:1
            Color(0.54f, 0.40f, 0.94f), // 4,01:1
            Color(0.34f, 0.76f, 0.40f), // 2,25:1
        )
        // Les accents que le thème emploie réellement, dans les deux apparences.
        val themeAccents = listOf(
            ReferencePalette.Accent,
            ReferencePalette.Warning.light, ReferencePalette.Warning.dark,
            ReferencePalette.Success.light, ReferencePalette.Success.dark,
            ReferencePalette.Danger.light, ReferencePalette.Danger.dark,
        )
        val rawFailures = mutableListOf<String>()
        (botAccents + themeAccents).forEach { accent ->
            // L'encre crue — ce que la salle peignait avant ce lot.
            if (OnyxContrast.ratio(Color.White, accent) < OnyxContrast.TEXT_MIN) {
                rawFailures += "${OnyxContrast.ratio(Color.White, accent)}:1"
            }
            // L'encre MESURÉE — ce que la maison peint : elle tient, par construction.
            val ink = readableInk(themeUserText(), accent, OnyxContrast.TEXT_MIN.toDouble())
            val held = OnyxContrast.ratio(ink, accent)
            assertTrue(
                "l'encre mesurée doit tenir ${OnyxContrast.TEXT_MIN}:1 sur l'accent, mesuré $held:1",
                held >= OnyxContrast.TEXT_MIN,
            )
        }
        assertTrue(
            "l'encre crue doit ÉCHOUER quelque part, sinon ce verrou ne prouverait rien (relevé : $rawFailures)",
            rawFailures.isNotEmpty(),
        )
    }

    /** L'encre crue des commandes : le blanc de `userText` du thème. */
    private fun themeUserText(): Color = Color.White

    // — 3 · la route de groupe compose le panneau et la barre —

    @Test
    fun `la route de groupe compose le panneau de fichiers et la barre d onglets`() {
        assertTrue(
            "le panneau de fichiers de la maison (ses six filtres, son compte, son tri) doit être composé",
            room.contains("ChatFilesPane("),
        )
        assertTrue(
            "la barre d'onglets de la maison doit être composée",
            room.contains("ChatTabBar("),
        )
        assertTrue(
            "la barre doit être celle de la maison, à quatre repères (Chat, Flux, Fichiers, visage)",
            room.contains("tabs = ChatTab.drawn"),
        )
        assertTrue(
            "les fichiers de la salle viennent de SON journal (`ChatFiles.conversationFiles`)",
            room.contains("ChatFiles.conversationFiles("),
        )
    }

    // — 4 · chaque salon garde SES fichiers —

    @Test
    fun `le journal d une salle donne ses fichiers, et deux salons different`() {
        val note = "\n\n[The user attached a file, saved on your machine at "
        val other = "\n\n[The user attached a picture, saved on your machine at "
        val roomA = listOf(
            GroupEvent(seq = 1, kind = "message.user", actorKind = "user", actorID = null,
                payload = JsonObject(mapOf("text" to kotlinx.serialization.json.JsonPrimitive(
                    "voici$note/a/alpha.png — read it with your tools.]"))), createdAtMillis = 10L),
        )
        val roomB = listOf(
            GroupEvent(seq = 1, kind = "message.member", actorKind = "member", actorID = "bot-1",
                payload = JsonObject(mapOf("text" to kotlinx.serialization.json.JsonPrimitive(
                    "et voila$other/b/beta.pdf — look at it with your tools.]"))), createdAtMillis = 20L),
        )
        val filesA = com.onyx.app.ui.chat.ChatFiles
            .conversationFiles(roomThreadMessages(roomA)).map { it.path }
        val filesB = com.onyx.app.ui.chat.ChatFiles
            .conversationFiles(roomThreadMessages(roomB)).map { it.path }
        assertEquals("le salon A ne montre que SON fichier", listOf("/a/alpha.png"), filesA)
        assertEquals("le salon B ne montre que le sien", listOf("/b/beta.pdf"), filesB)
        assertTrue("deux salons ne partagent donc pas la même liste", filesA != filesB)
    }

    @Test
    fun `le journal se lit comme des messages, la personne et les bots`() {
        val rows = listOf(
            GroupEvent(seq = 1, kind = "message.user", actorKind = "user", actorID = null,
                payload = JsonObject(mapOf("text" to kotlinx.serialization.json.JsonPrimitive("bonjour"))),
                createdAtMillis = 1L),
            GroupEvent(seq = 2, kind = "message.member", actorKind = "member", actorID = "bot-1",
                payload = JsonObject(mapOf("text" to kotlinx.serialization.json.JsonPrimitive("salut"))),
                createdAtMillis = 2L),
            GroupEvent(seq = 3, kind = "turn.settled", actorKind = "gateway", actorID = null,
                payload = JsonObject(emptyMap()), createdAtMillis = 3L),
        )
        val messages = roomThreadMessages(rows)
        assertEquals("seuls les messages font un message", 2, messages.size)
        assertEquals(Role.USER, messages[0].role)
        assertEquals(Role.ASSISTANT, messages[1].role)
        assertEquals("salut", messages[1].text)
    }
}
