package com.onyx.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les commandes « / » du chat.
 *
 * n'en garde qu'une locale — `/model` nu ouvre le sélecteur — et
 * envoie le reste au gateway. On vérifie ici que chaque commande de l'app agit, que le reste part
 * bien au gateway, et qu'un argument change de camp comme.
 */
class SlashCommandsTest {
    @Test
    fun `model nu ouvre le selecteur comme `() {
        assertEquals(SlashAction.OPEN_MODEL_PICKER, SlashCommands.action("/model"))
        assertEquals(SlashAction.OPEN_MODEL_PICKER, SlashCommands.action("  /MODEL  "))
    }

    @Test
    fun `model avec un argument reste une commande du gateway`() {
        // « Bare /model opens the picker; with an argument it stays a serve slash command. »
        assertEquals(SlashAction.GATEWAY, SlashCommands.action("/model gpt-4o"))
        assertEquals(SlashAction.GATEWAY, SlashCommands.action("/reasoning high"))
    }

    @Test
    fun `chaque commande locale a une action`() {
        val expected = mapOf(
            "/model" to SlashAction.OPEN_MODEL_PICKER,
            "/new" to SlashAction.NEW_CONVERSATION,
            "/find" to SlashAction.FIND,
            "/settings" to SlashAction.SETTINGS,
            "/voice" to SlashAction.VOICE,
            "/help" to SlashAction.HELP,
        )
        assertEquals(expected.keys, SlashCommands.builtIn.map { it.name }.toSet())
        expected.forEach { (line, action) ->
            assertEquals(line, action, SlashCommands.action(line))
        }
    }

    @Test
    fun `tout le reste part au gateway`() {
        assertEquals(SlashAction.GATEWAY, SlashCommands.action("/clear"))
        assertEquals(SlashAction.GATEWAY, SlashCommands.action("/fast"))
        assertEquals(SlashAction.GATEWAY, SlashCommands.action("/some.custom.command"))
    }

    @Test
    fun `sans barre oblique ce nest pas une commande`() {
        assertNull(SlashCommands.commandFor("bonjour"))
        assertEquals(SlashAction.GATEWAY, SlashCommands.action("bonjour"))
    }

    @Test
    fun `le menu local filtre sur ce qui est tape`() {
        assertEquals(listOf("/model"), SlashCommands.matches("/mo").map { it.name })
        assertTrue(SlashCommands.matches("/").isNotEmpty())
        // Rien ne correspond: on montre tout plutôt qu'un menu vide.
        assertEquals(SlashCommands.builtIn.size, SlashCommands.matches("/zzz").size)
        assertTrue(SlashCommands.matches("bonjour").isEmpty())
    }

    @Test
    fun `help enumere les commandes connues`() {
        val help = SlashCommands.helpText()
        SlashCommands.builtIn.forEach { command ->
            assertTrue("help doit lister ${command.name}", help.contains(command.name))
        }
    }
}
