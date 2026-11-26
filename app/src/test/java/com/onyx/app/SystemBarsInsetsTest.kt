package com.onyx.app

import java.io.File
import com.onyx.app.ui.design.SafeBarSpacing
import com.onyx.app.ui.design.TabBarGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le verrou des **marges du système** sur les barres de l'app.
 *
 * Le défaut mesuré : la barre à cinq onglets était le dernier enfant d'une colonne bord à bord —
 * donc à 0 dp du bas de l'écran — et la zone du geste d'accueil (ou la barre de navigation) la
 * recouvrait ; le geste passait par-dessus « Flux / Fichiers ». Rien dans le code ne
 * l'empêchait, et rien ne pouvait le signaler au prochain écran recopié.
 *
 * Ce test lit donc **chaque racine d'écran** et exige qu'elle prenne l'encart du système. Le calcul
 * de la marge, lui, est éprouvé sans appareil par `SafeBarSpacingTest` (core) — ici on vérifie que
 * les écrans s'en servent vraiment, et que l'en-tête se pose **sous** la barre d'état au lieu de
 * glisser dessous.
 */
class SystemBarsInsetsTest {

    private fun read(path: String) = File(path).readText()

    private val chat = read("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt")
    private val topBar = read("src/main/java/com/onyx/app/ui/chat/components/OnyxTopBar.kt")

    // — La barre à cinq onglets d'une conversation (le défaut signalé) —

    /** La fin de `ChatScreen` : la barre d'écriture et la barre d'onglets, hors du pageur. */
    private fun bottomStack(): String =
        chat.substringAfter("// **La pile du bas**").substringBefore("val jumpClearance")


    @Test
    fun `la hauteur mesuree reste celle de la barre, sans l encart`() {
        // Le bouton « dernier message » se pose d'après la **réserve du fil** — qui a déjà pris
        // l'encart du système **une seule fois** (`threadBottomReserve`) — plus un dégagement nommé.
        // Il ne reprend donc plus la zone système de son côté : elle était comptée deux fois, et
        // le bouton descendait dans la carte du compositeur au lieu de s'appuyer dessus.
        val stack = bottomStack()
        assertTrue("l'encart est pris avant la mesure", stack.indexOf("composerBottomInsets") < stack.indexOf("onSizeChanged"))
        assertTrue("la mesure enveloppe la barre seule", stack.indexOf("onSizeChanged") < stack.indexOf("OnyxComposer("))
        assertFalse(
            "le bouton de saut ne reprend pas la zone système : la réserve la porte déjà",
            chat.substringAfter("val jumpClearance").substringBefore("ToastHost")
                .contains("navigationBarsPadding()"),
        )
        assertTrue(
            "il se pose sur la réserve du fil, plus un dégagement",
            chat.contains("val jumpClearance = glassBottomInset + ComposerStacking.PillClearanceDp.dp"),
        )
    }

    // — L'en-tête du fil —

    @Test
    fun `l en-tete se pose sous la barre d etat, jamais dessous`() {
        // L'ordre des modificateurs est la règle : `statusBarsPadding` **avant** la vitre, donc la
        // barre commence sous la barre d'état et garde son arête haute. Dans l'autre ordre le verre
        // commençait à y=0, hors de l'écran, et la barre se lisait « coupée en haut ».
        //
        // La vitre s'appelle désormais `liquidGlass` (le verre premium à flou d'arrière-plan réel,
        // `dev.chrisbanes.haze`) ; `frostedBar` reste le nom de la vitre des surfaces qui ne
        // recouvrent rien. La règle testée est la même : il y a **une** vitre, et l'encart passe
        // avant elle.
        val inset = topBar.indexOf("statusBarsPadding()")
        val glass = listOf("liquidGlass(", "frostedBar(")
            .map { topBar.indexOf(it) }
            .filter { it >= 0 }
            .minOrNull() ?: -1
        assertTrue("l'en-tête doit porter la barre d'état", inset >= 0)
        assertTrue("la vitre de l'en-tête doit exister", glass >= 0)
        assertTrue("la barre d'état passe avant la vitre", inset < glass)
    }

    // — Le balayage : aucune racine ne colle au bord bas —

    @Test
    fun `chaque ecran balaye degage la zone systeme en bas`() {
        val screens = listOf(
            "ui/chat/ChatScreen.kt",
            "ui/bots/GroupChatScreen.kt",
            "ui/bots/GroupRoomScreen.kt",
            "ui/voice/VoiceScreen.kt",
            "ui/computer/ComputerScreen.kt",
            "ui/computer/ComputerStreamPanel.kt",
            "ui/settings/SettingsScreen.kt",
            "ui/settings/ConnectionDetailsScreen.kt",
            "ui/cron/CronScreen.kt",
            "ui/cron/BotSchedulesScreen.kt",
            "ui/kanban/KanbanScreen.kt",
            "ui/code/CodeScreen.kt",
            "ui/files/FilesScreen.kt",
            "ui/terminal/TerminalScreen.kt",
            "ui/conversations/SessionUsageScreen.kt",
            "ui/setup/SetupScreen.kt",
            "ui/setup/SetupCodeSheet.kt",
            "ui/bots/BotVoicePickerScreen.kt",
            "ui/bots/HiddenBotsScreen.kt",
            "ui/settings/PushSettingsScreen.kt",
            "ui/settings/VoiceSettingsScreen.kt",
        )
        screens.forEach { path ->
            val source = read("src/main/java/com/onyx/app/$path")
            assertTrue(
                "$path doit prendre la zone système (navigationBarsPadding) : une racine bord à bord " +
                    "sans cet encart enterre sa dernière rangée sous la barre de navigation.",
                source.contains("navigationBarsPadding()"),
            )
        }
    }

}
