package com.onyx.app.ui.theme

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le fond-matière est posé, et les icônes prennent l'encre du contexte.**
 *
 * Le lot réhabille : chaque écran porte une **matière** ([onyxPage]) au lieu d'un aplat de page —
 * sans quoi une vitre posée dessus n'a rien à échantillonner — et les icônes **décoratives** cessent
 * de porter la couleur d'accent pour prendre l'encre du contexte (claire sur verre sombre, foncée sur
 * verre clair).
 *
 * Ce qui reste coloré le reste **parce que la couleur porte une information** (état sélectionné,
 * alerte, direct, épingle) : ce test verrouille les deux côtés — les conversions faites, et les
 * exceptions gardées — pour qu'un lot suivant ne puisse ni remettre du bleu décoratif, ni retirer un
 * accent qui dit quelque chose.
 */
class MatiereEtMonochromeTest {

    private fun appSource(path: String): String = File("src/main/java/com/onyx/app", path).readText()

    @Test
    fun `les ecrans portent la matiere au lieu d un aplat de page`() {
        val screens = listOf(
            "ui/terminal/TerminalScreen.kt",
            "ui/cron/CronScreen.kt",
            "ui/cron/BotSchedulesScreen.kt",
            "ui/files/FilesScreen.kt",
            "ui/servers/ServersScreen.kt",
            "ui/servers/ProfilePickerScreen.kt",
            "ui/models/ModelsScreen.kt",
            "ui/code/CodeScreen.kt",
            "ui/capabilities/SkillEditorScreen.kt",
            "ui/tools/ToolsScreen.kt",
            "ui/sidebar/ArchivedScreen.kt",
            "ui/kanban/KanbanScreen.kt",
            "ui/bots/BotEditorScreen.kt",
            "ui/bots/GroupChatScreen.kt",
            "ui/bots/GroupRoomScreen.kt",
            "ui/context/ContextScreen.kt",
            "ui/chat/DocumentScreens.kt",
            "ui/chat/SubagentListScreen.kt",
            "ui/conversations/ProjectSessionsScreen.kt",
        )
        screens.forEach { path ->
            val source = appSource(path)
            assertTrue("$path doit poser la matière (onyxPage)", source.contains(".onyxPage(theme)"))
            assertTrue(
                "$path doit importer onyxPage",
                source.contains("import com.onyx.app.ui.design.onyxPage"),
            )
        }
    }

    @Test
    fun `les icones decoratives prennent l encre du contexte`() {
        // Chaque ligne : le fichier, et le pictogramme qui ne dit rien par sa couleur.
        val converted = listOf(
            "ui/terminal/TerminalScreen.kt" to "OnyxIcon(Symbols.Terminal, tint = theme.textColor",
            "ui/files/FilesScreen.kt" to "OnyxIcon(Symbols.Brain, tint = theme.textColor",
            "ui/servers/ServersScreen.kt" to "OnyxIcon(Symbols.Globe, tint = theme.textColor",
            "ui/common/ConnectionProblemCard.kt" to "OnyxIcon(symbol = Symbols.ServerRack, tint = theme.textColor",
            "ui/common/TipJarScreen.kt" to "OnyxIcon(Symbols.HeartFill, tint = theme.textColor",
            "ui/common/WhatsNewScreen.kt" to "OnyxIcon(symbol = item.symbol, tint = theme.textColor",
            "ui/chat/components/StartScreen.kt" to "OnyxIcon(symbol = card.symbol, tint = theme.textColor",
            "ui/chat/components/LinkPreviewCard.kt" to "OnyxIcon(Symbols.Link, tint = theme.textColor",
            "ui/kanban/KanbanScreen.kt" to "OnyxIcon(Symbols.SquareAndPencil, tint = theme.textColor",
            "ui/bots/GroupComposer.kt" to "Symbols.Doc, tint = theme.textColor",
            "ui/lock/LockScreen.kt" to "OnyxIcon(Symbols.LockOutline, tint = theme.secondaryText",
        )
        converted.forEach { (path, needle) ->
            assertTrue("$path : pictogramme décoratif encore en accent ($needle)", appSource(path).contains(needle))
        }
    }

    @Test
    fun `les accents qui portent une information restent colores`() {
        // Les exceptions, avec leur raison : un état, une alerte, un direct, une épingle.
        val kept = listOf(
            // État sélectionné : la coche d'une liste de choix.
            "ui/models/ModelsScreen.kt" to "OnyxIcon(Symbols.Checkmark, tint = theme.accent",
            "ui/servers/ServersScreen.kt" to "OnyxIcon(Symbols.Checkmark, tint = theme.accent",
            // Direct : l'onde du bot qui parle.
            "ui/bots/GroupRoomScreen.kt" to "OnyxIcon(symbol = Symbols.SpeakerWave, tint = theme.accent",
            // Nouveau message : la pastille « aller au dernier ».
            "ui/bots/GroupRoomScreen.kt" to "OnyxIcon(Symbols.ArrowUpCircle, tint = theme.accent",
            // Épingle : l'état « épinglé » d'un bot.
            "ui/bots/BotEditorScreen.kt" to "OnyxIcon(Symbols.Pin, tint = theme.accent",
        )
        kept.forEach { (path, needle) ->
            assertTrue("$path : l'accent qui dit un état a été retiré ($needle)", appSource(path).contains(needle))
        }
    }
}
