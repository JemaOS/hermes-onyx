package com.onyx.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * « /model » : la commande slash qui ouvre le sélecteur de modèle.
 *
 * Comme `ComposerView.send` côté référence : un `/model` nu change de modèle (même écran que par
 * l'en-tête) ; avec un argument, la ligne reste une commande du serveur.
 */
class ModelCommandTest {

    @Test
    fun `un model nu est la commande locale`() {
        assertTrue(isModelCommand("/model"))
        assertTrue(isModelCommand("  /model  "))
        assertTrue(isModelCommand("/MODEL"))
    }

    @Test
    fun `un model avec argument reste une commande du serveur`() {
        assertFalse(isModelCommand("/model gpt-5"))
        assertFalse(isModelCommand("/models"))
        assertFalse(isModelCommand("/reasoning high"))
        assertFalse(isModelCommand("bonjour"))
    }

    @Test
    fun `le menu ajoute model quand il correspond et n'est pas deja la`() {
        val front = withModelCommand(emptyList(), "/")
        assertEquals(1, front.size)
        assertEquals("/model", front.first().text)

        val typed = withModelCommand(emptyList(), "/mo")
        assertEquals("/model", typed.first().text)

        val matched = withModelCommand(emptyList(), "/model")
        assertEquals("/model", matched.first().text)
    }

    @Test
    fun `le menu ne propose pas model pour un autre prefixe`() {
        assertTrue(withModelCommand(emptyList(), "/reasoning").isEmpty())
        assertTrue(withModelCommand(emptyList(), "/x").isEmpty())
    }

    @Test
    fun `le menu ne duplique pas un model deja venu du gateway`() {
        val fromGateway = listOf(SlashItem(text = "/model", display = "/model", detail = "server copy"))
        val merged = withModelCommand(fromGateway, "/model")

        assertEquals(1, merged.size)
        assertEquals("server copy", merged.first().detail)
    }

    @Test
    fun `l'ecran de chat ouvre le selecteur sur la demande de model`() {
        val chat = java.io.File("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt").readText()
        assertTrue(chat.contains("LaunchedEffect(state.modelPickerRequested)"))
        assertTrue(chat.contains("onModelCommand"))

        val vm = java.io.File("src/main/java/com/onyx/app/ui/chat/ChatViewModel.kt").readText()
        assertTrue(vm.contains("isModelCommand(text)"))
        assertTrue(vm.contains("modelPickerRequested = true"))
        assertTrue(vm.contains("fun consumeModelPickerRequest()"))
        assertTrue(vm.contains("withModelCommand(items, value)"))
    }
}
