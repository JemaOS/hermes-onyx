package com.onyx.app.ui.bots

import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.ui.design.ComposerFieldInvite
import com.onyx.app.ui.design.composerFieldInvite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupComposerLogicTest {
    private val bots = listOf(
        BotProfile(name = "scout", title = "Scout"),
        BotProfile(name = "sage", title = "Sage"),
        BotProfile(name = "kilo", title = "Research"),
    )

    @Test
    fun `la dictee rejoint le brouillon sans ajouter d espace en trop`() {
        assertEquals("Bonjour", dictationJoined("", "  Bonjour \n"))
        assertEquals("Bonjour tout le monde", dictationJoined("Bonjour", "tout le monde"))
        assertEquals("Bonjour \ntout le monde", dictationJoined("Bonjour \n", "tout le monde"))
        assertEquals("Brouillon", dictationJoined("Brouillon", "  "))
    }

    @Test
    fun `les suggestions cherchent dans la poignee et le nom`() {
        assertEquals(listOf("kilo"), groupTagSuggestions("Dis @ki", bots).map { it.name })
        assertEquals(listOf("kilo"), groupTagSuggestions("Dis @res", bots).map { it.name })
        assertEquals(3, groupTagSuggestions("@", bots).size)
        assertEquals(0, groupTagSuggestions("sans mention", bots).size)
    }

    @Test
    fun `la completion utilise la poignee et preserve le blanc precedent`() {
        assertEquals("Dis à @kilo ", replaceTagToken("Dis à @ki", "kilo"))
        assertEquals("Bonjour\n@scout ", replaceTagToken("Bonjour\n@sc", "scout"))
        assertEquals("texte", replaceTagToken("texte", "kilo"))
    }

    // hasDraft de la référence : texte qui n'est pas que du blanc, ou au moins une pièce jointe.
    @Test
    fun `le champ a un brouillon avec du texte ou une piece jointe`() {
        assertFalse(groupComposerHasDraft("", 0))
        assertFalse(groupComposerHasDraft("   \n ", 0))
        assertTrue(groupComposerHasDraft("salut", 0))
        assertTrue(groupComposerHasDraft("", 1))
        assertTrue(groupComposerHasDraft("salut", 2))
    }

    // Le bouton rond n'est plus dessiné par la salle : la cascade d'états (send / steer / stop)
    // vit dans le compositeur de la maison (`OnyxComposer`). Ce qui reste propre au groupe est son
    // **invite**, et elle vient de la source unique (`composerFieldInvite`).
    @Test
    fun `l invite de la salle vient de la table de la maison`() {
        assertEquals(
            ComposerFieldInvite.GROUP,
            composerFieldInvite(draftBlank = true, dictating = false, streaming = false, canSteer = false, offline = false, group = true),
        )
        // Un brouillon est la couche : aucune invite ne s'écrit avec lui.
        assertEquals(
            ComposerFieldInvite.NONE,
            composerFieldInvite(draftBlank = false, dictating = false, streaming = false, canSteer = false, offline = false, group = true),
        )
    }

    @Test
    fun `l invite du champ dit la dictee quand elle ecoute`() {
        assertEquals(
            ComposerFieldInvite.DICTATION,
            composerFieldInvite(draftBlank = true, dictating = true, streaming = false, canSteer = false, offline = false, group = true),
        )
        assertEquals(
            ComposerFieldInvite.GROUP,
            composerFieldInvite(draftBlank = true, dictating = false, streaming = false, canSteer = false, offline = false, group = true),
        )
    }
}
