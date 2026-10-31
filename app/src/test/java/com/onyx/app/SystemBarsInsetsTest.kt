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
    fun `la barre d onglets prend la zone systeme et l espace visuel de la maison`() {
        val stack = bottomStack()
        // **La plaque unique prend l'encart, une seule fois, pour le compositeur ET la barre**
        // (brief « cinq divergences », n° 5) : `composerBottomInsets` porte le clavier, puis la zone
        // de navigation **sans** le clavier — l'encart n'est donc jamais compté deux fois.
        assertTrue(
            "la plaque du bas doit prendre l'encart du système",
            stack.contains(".composerBottomInsets()"),
        )
        assertTrue(
            "elle doit s'écarter de la zone système",
            stack.contains("bottom = OnyxSpace.Half,"),
        )
        // La barre elle-même est **dans** la plaque encartée : c'est la plaque qui porte l'encart,
        // pas la barre, donc le verre ne descend jamais dans la zone.
        assertTrue("la barre doit être posée dans la plaque encartée", stack.contains("ChatTabBar("))
        assertTrue(
            "l'encart doit précéder la barre",
            stack.indexOf("composerBottomInsets") < stack.indexOf("ChatTabBar("),
        )
    }

    @Test
    fun `la hauteur mesuree reste celle de la barre, sans l encart`() {
        // Le bouton « dernier message » se pose d'après la **réserve du fil** — qui a déjà pris
        // l'encart du système **une seule fois** (`threadBottomReserve`) — plus un dégagement nommé.
        // Il ne reprend donc plus la zone système de son côté : elle était comptée deux fois, et
        // le bouton descendait dans la carte du compositeur au lieu de s'appuyer dessus.
        val stack = bottomStack()
        assertTrue("l'encart est pris avant la mesure", stack.indexOf("composerBottomInsets") < stack.indexOf("onSizeChanged"))
        assertTrue("la mesure enveloppe la barre seule", stack.indexOf("onSizeChanged") < stack.indexOf("ChatTabBar("))
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

    @Test
    fun `la marge n est pas une valeur ecrite en dur dans les barres basses`() {
        // Le piège nommé par le propriétaire : « une valeur codée en dur qui ne marche que sur un
        // écran ». La barre d'onglets et l'en-tête passent donc par l'encart du système, et la
        // constante de l'espace visuel vit à un seul endroit (`SafeBarSpacing`), pas dans l'écran.
        assertTrue(
            "la barre d'onglets ne fabrique pas sa marge à la main",
            !chat.contains("padding(bottom = 24.dp)") && !chat.contains("padding(bottom = 48.dp)"),
        )
        assertTrue(
            "l'espace visuel vient d'un jeton, jamais d'un nombre ecrit dans l'ecran",
            chat.contains("TabBarGeometry.BottomMargin") || chat.contains("SafeBarSpacing"),
        )
        // Et ce jeton **est** l'espace visuel de la maison : la valeur de `SafeBarSpacing.GapDp`,
        // portee par la famille de la barre flottante (`OnyxSpace.Half`). C'est l'egalite qui
        // compte, pas le nom du fichier ou le chiffre a ete ecrit — un jeton de famille peut
        // desormais le nommer, et il ne peut pas deriver sans faire echouer cette ligne.
        assertEquals(
            "le dégagement de la pilule doit être l'espace visuel de la maison",
            SafeBarSpacing.GapDp,
            TabBarGeometry.BottomMargin.value.toInt(),
        )
    }
}
