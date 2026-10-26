package com.onyx.app.ui.bots

import com.onyx.app.localization.OnyxLocalization
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * « Hidden Bots », comme `HiddenBotsView`: la tuile « Hidden » de la grille des bots ouvre un
 * écran qui montre les bots cachés et les rend visibles un à un. Une action qui ne fait que
 * fermer l'écran ne compte pas: « Show » écrit sur le serveur, et à défaut ici.
 */
class HiddenBotsTest {

    private val screen = File("src/main/java/com/onyx/app/ui/bots/HiddenBotsScreen.kt").readText()
    private val bots = File("src/main/java/com/onyx/app/ui/bots/BotsScreen.kt").readText()
    private val viewModel = File("src/main/java/com/onyx/app/ui/bots/BotsViewModel.kt").readText()
    private val rpc = File("../core/src/main/java/com/onyx/app/data/net/ServeBotsRpc.kt").readText()

    // MARK: - L'entrée

    @Test
    fun `la grille porte la tuile Hidden, apres New Bot`() {
        assertEquals(
            listOf(BotGridCell.Face, BotGridCell.Face, BotGridCell.NewBot, BotGridCell.Hidden),
            botGridCells(2, expanded = false, hasDashboard = true, hidden = 3),
        )
        // Sans bot caché, aucune tuile: la grille reste celle d'avant.
        assertEquals(
            listOf(BotGridCell.Face, BotGridCell.Face, BotGridCell.NewBot),
            botGridCells(2, expanded = false, hasDashboard = true, hidden = 0),
        )
        assertTrue(bots.contains("title = \"Hidden\""))
        assertTrue(bots.contains("symbol = Symbols.EyeSlash"))
        assertTrue(bots.contains("onOpenHiddenBots = { onOpenHiddenBots?.invoke() }"))
        assertTrue(bots.contains("hidden = if (onOpenHiddenBots != null) hiddenCount else 0"))
    }

    // MARK: - L'écran

    @Test
    fun `l ecran est celui , son titre, ses lignes et son pied`() {
        assertTrue(screen.contains("OnyxPageHeader(title = \"Hidden Bots\", onBack = null, action = \"Done\", onAction = onBack)"))
        assertTrue(screen.contains("text = bot.label(mainName)"))
        assertTrue("la phrase du bot sous son nom", screen.contains("text = bot.description"))
        assertTrue("son visage de 30 points", screen.contains("size = 30.dp"))
        assertTrue("le bouton ", screen.contains("text = \"Show\""))
        assertTrue("le pied de page ", screen.contains("Hidden here and in Hermes Desktop alike."))
        assertTrue("l'avertissement au-dessus", screen.contains("Symbols.ExclamationTriangle"))
    }

    @Test
    fun `l ecran se ferme quand le dernier bot est montre`() {
        assertTrue(screen.contains("viewModel.showBot(bot, onAllShown = onBack)"))
        assertTrue(viewModel.contains("if (_state.value.hidden.isEmpty()) onAllShown()"))
    }

    // MARK: - L'action

    @Test
    fun `Show ecrit la visibilite sur le serveur, et ici sans Dashboard`() {
        assertTrue(viewModel.contains("ServeBotsRpc(rpc).setHidden(bot.name, hidden = false)"))
        assertTrue(viewModel.contains("faces.setHideOverride(bot.name, false)"))
        assertTrue(rpc.contains("suspend fun setHidden(profile: String, hidden: Boolean, revision: Int = 0)"))
        assertTrue(rpc.contains("put(\"hidden\", hidden)"))
        // Le choix local prime sur le `ui_meta` du serveur, sans quoi un bot caché ne
        // disparaîtrait jamais de la liste.
        assertTrue(viewModel.contains("isHidden = hideOverride(bot.name) ?: (!isShown(bot.name) || bot.isHidden)"))
    }

    @Test
    fun `les bots caches sont lus parmi tous les profils, pas les seuls visibles`() {
        assertTrue(viewModel.contains("val fromRest = if (config.hasServeCredentials) profiles.profiles() else emptyList()"))
        assertTrue(viewModel.contains("hidden = resolved.filter { bot -> bot.isHidden && !bot.isDefault }"))
    }

    // MARK: - La langue

    @Test
    fun `les libelles sont dits en francais`() {
        assertEquals("Bots masqués", OnyxLocalization.translate("Hidden Bots", Locale.FRENCH))
        assertEquals("Masqué", OnyxLocalization.translate("Hidden", Locale.FRENCH))
        assertEquals("Aucun bot caché", OnyxLocalization.translate("No hidden bots", Locale.FRENCH))
    }
}
