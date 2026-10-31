package com.onyx.app.ui.chat

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le câblage du modèle du bot.** Le cas « conversation de bot » ne tient que si trois maillons
 * restent liés — et chacun est ici nommé dans sa source :

 * 1. le **souvenir local** du modèle d'un bot (`FaceKeys.model`, écrit par la feuille de modèle)
 *    prime, et le roster complète : `BotsViewModel.modelOf(BotProfile)` ;
 * 2. l'écran du chat **pousse** ce modèle dans l'état dès que la conversation (ou le roster) change :
 *    `chatViewModel.setBotModel(botsViewModel.modelOf(…))` ;
 * 3. l'état le porte (`ChatUiState.botModel`) et le fait entrer dans la valeur unique
 *    (`ModelInForce.inForce(modelInUse, botModel, server.effectiveModel)`), que la pilule **et**
 *    l'envoi lisent.
 *
 * Si l'un des trois casse, la pilule d'une conversation de bot nommerait autre chose que ce qui part.
 */
class BotModeleCablageTest {

    private fun read(path: String) = File(path).readText()

    private val app = read("src/main/java/com/onyx/app/ui/OnyxApp.kt")
    private val bots = read("src/main/java/com/onyx/app/ui/bots/BotsViewModel.kt")
    private val chatUiState = read("src/main/java/com/onyx/app/ui/chat/ChatUiState.kt")
    private val chatViewModel = read("src/main/java/com/onyx/app/ui/chat/ChatViewModel.kt")

    @Test
    fun `le modele d un bot se lit sur le choix local, sinon le roster`() {
        assertTrue(
            "le souvenir local du modèle du bot prime sur ce que le serveur dit",
            bots.contains("fun modelOf(bot: BotProfile): String? = faces.modelOf(bot.name) ?: bot.model"),
        )
    }

    @Test
    fun `l ecran du chat pousse le modele du bot dans l etat`() {
        assertTrue(
            "l'écran pousse le modèle du bot de la conversation ouverte",
            app.contains("chatViewModel.setBotModel(openedConversationBot?.let(botsViewModel::modelOf))"),
        )
    }

    @Test
    fun `l etat porte le modele du bot et le fait entrer dans la valeur unique`() {
        assertTrue("le champ existe", chatUiState.contains("val botModel: String? = null"))
        assertTrue(
            "il entre dans la résolution unique",
            chatUiState.contains("ModelInForce.inForce(modelInUse, botModel, server.effectiveModel)"),
        )
        assertTrue(
            "et il est remis à zéro au changement de conversation (rien ne fuit d'un fil à l'autre)",
            chatUiState.contains("botModel = null"),
        )
        // Le poussoir existe et baisse l'écho du serveur pour que le choix prenne.
        assertTrue(chatViewModel.contains("fun setBotModel(model: String?)"))
        assertTrue(
            "un nouveau choix invalide l'écho du serveur",
            chatViewModel.contains("it.copy(botModel = clean, modelInUse = null)"),
        )
    }

    @Test
    fun `un nouveau modele configure invalide aussi l echo du serveur`() {
        assertTrue(
            "un changement de ServerConfig.model remet modelInUse à zéro",
            chatViewModel.contains("val modelChanged = server.model != current.model"),
        )
        assertTrue(
            "et le pose dans l'état",
            chatViewModel.contains("modelInUse = if (modelChanged) null else it.modelInUse"),
        )
    }
}
