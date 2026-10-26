package com.onyx.app

import com.onyx.app.ui.settings.voice.SpokenPrefix
import com.onyx.app.ui.settings.voice.addable
import com.onyx.app.voice.normalizeStopPhrase
import com.onyx.app.ui.settings.voice.routeVoiceText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Le routage des préfixes parlés décide ce qui part au modèle: un mot mal reconnu ne doit pas
 * avaler la question, et une phrase de politesse ne doit pas changer le sens.
 */
class SpokenPrefixTest {

    @Test
    fun `le mot en tete devient le prefixe`() {
        val rules = listOf(SpokenPrefix(word = "Claude", prefix = "@claude "))
        val route = routeVoiceText("Claude, quelle heure est-il ?", rules)

        assertEquals("@claude quelle heure est-il ?", route.text)
    }

    @Test
    fun `un alias est reconnu`() {
        val rules = listOf(SpokenPrefix(word = "Claude", aliases = listOf("Cloud"), prefix = "« "))
        assertEquals("« ouvre la porte", routeVoiceText("Cloud ouvre la porte", rules).text)
    }

    @Test
    fun `le mot doit etre entier`() {
        val rules = listOf(SpokenPrefix(word = "Claude", prefix = "x"))
        assertEquals("Claudette arrive", routeVoiceText("Claudette arrive", rules).text)
    }

    @Test
    fun `une phrase sans regle reste telle quelle`() {
        val route = routeVoiceText("Bonjour", listOf(SpokenPrefix(word = "Claude", prefix = "@c ")))
        assertEquals("Bonjour", route.text)
        assertNull(route.bot)
    }

    @Test
    fun `les politesses sont retirees avant comparaison`() {
        assertEquals("stop", normalizeStopPhrase("Okay Onyx, stop please."))
    }

    @Test
    fun `une phrase deja connue n'est pas ajoutee`() {
        assertNull(addable("stop", emptyList()))
        assertNull(addable("", emptyList()))
        assertEquals("coupe le son", addable("coupe le son", emptyList()))
    }
}
