package com.onyx.app.ui.setup

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ce que le mode « Simple » **propose**, mesuré sans monter un écran.
 *
 * Le défaut signalé : « quand je veux me connecter en simple ça propose rien ». Ces cas tiennent les
 * trois choses qui font qu'une proposition est utile — **d'où elle vient** (une source nommée),
 * **qu'elle ne se répète pas**, et **qu'une adresse qui porte un secret ne se relit jamais**.
 */
class SetupSuggestionsTest {

    @Test
    fun `les adresses connues sortent dans l'ordre de pertinence`() {
        val offered = SetupSuggestions.known(
            current = "http://127.0.0.1:9119",
            local = listOf("http://192.168.1.20:9119"),
            lastGateway = "https://maison.trycloudflare.com",
            saved = listOf("http://10.0.0.5:9119"),
        )
        assertEquals(
            listOf(
                SuggestionSource.CURRENT,
                SuggestionSource.LOCAL,
                SuggestionSource.LAST_GATEWAY,
                SuggestionSource.SAVED,
            ),
            offered.map { it.source },
        )
        assertEquals(
            listOf(
                "http://127.0.0.1:9119",
                "http://192.168.1.20:9119",
                "https://maison.trycloudflare.com",
                "http://10.0.0.5:9119",
            ),
            offered.map { it.address },
        )
    }

    @Test
    fun `la meme adresse n'est proposee qu'une fois`() {
        val offered = SetupSuggestions.known(
            current = "http://192.168.1.20:9119/",
            local = listOf("http://192.168.1.20:9119"),
            lastGateway = "http://192.168.1.20:9119",
            saved = listOf("http://192.168.1.20:9119"),
        )
        assertEquals(1, offered.size)
        // La barre oblique finale ne fait pas une seconde adresse.
        assertEquals("http://192.168.1.20:9119", offered.single().address)
    }

    @Test
    fun `une entree vide ou informe ne devient jamais une proposition`() {
        val offered = SetupSuggestions.known(
            current = "   ",
            local = listOf("", "pas-une-adresse"),
            lastGateway = "10.0.0.5:9119",
            saved = emptyList(),
        )
        assertTrue("rien de tout cela n'est une adresse", offered.isEmpty())
    }

    @Test
    fun `une adresse qui porte le jeton de voix est un secret`() {
        // Le serveur de voix annonce son adresse avec son jeton : le champ doit se masquer.
        assertTrue(SetupSuggestions.carriesSecret("http://10.0.2.2:8899/voices?token=cfgfr01"))
        assertTrue(SetupSuggestions.carriesSecret("https://voix.example/tts?key=abc"))
        assertTrue(SetupSuggestions.carriesSecret("https://voix.example/tts?access_token=abc"))
        // Une adresse nue n'en porte pas.
        assertFalse(SetupSuggestions.carriesSecret("http://192.168.1.20:9119"))
        assertFalse(SetupSuggestions.carriesSecret("https://maison.trycloudflare.com"))
        // Un paramètre qui n'est pas un secret ne masque rien.
        assertFalse(SetupSuggestions.carriesSecret("http://192.168.1.20:9119/?lang=fr"))
    }

    @Test
    fun `le presse-papiers rend l'adresse qu'il contient, jamais le bruit autour`() {
        assertEquals(
            "http://10.0.2.2:8899",
            SetupSuggestions.addressFromClipboard("  http://10.0.2.2:8899  "),
        )
        assertEquals(
            "https://maison.trycloudflare.com",
            SetupSuggestions.addressFromClipboard(
                "Tiens, essaie celle-ci : https://maison.trycloudflare.com et dis-moi",
            ),
        )
        assertEquals(
            "http://192.168.1.20:9119",
            SetupSuggestions.addressFromClipboard("http://192.168.1.20:9119\n"),
        )
        // Aucune adresse : rien à coller, et surtout pas un texte quelconque posé dans le champ.
        assertEquals("", SetupSuggestions.addressFromClipboard("bonjour"))
        assertEquals("", SetupSuggestions.addressFromClipboard(""))
    }

    @Test
    fun `une adresse utilisable porte un schema et un hote`() {
        assertTrue(SetupSuggestions.isAddress("http://192.168.1.20:9119"))
        assertTrue(SetupSuggestions.isAddress("https://maison.trycloudflare.com/"))
        assertFalse(SetupSuggestions.isAddress("192.168.1.20:9119"))
        assertFalse(SetupSuggestions.isAddress("http://"))
        assertFalse(SetupSuggestions.isAddress(""))
    }

    @Test
    fun `le lien de configuration porte les adresses, jamais les secrets`() {
        val config = ServerConfig(
            id = "s1",
            name = "Maison",
            baseUrl = "http://10.0.2.2:8899",
            apiKey = "clef-secrete-qui-ne-doit-pas-sortir",
            model = "hermes-agent",
            transport = Transport.CHAT_COMPLETIONS,
        )
        val link = setupLinkFor(config)
        assertTrue(link.startsWith("onyx://connect?"))
        assertTrue(link.contains("api="))
        assertFalse("un secret ne part pas dans le presse-papiers", link.contains(config.apiKey))
        assertFalse(link.contains("key="))
    }

    @Test
    fun `l'adresse d'un serveur est celle qu'on afficherait`() {
        val api = ServerConfig(id = "a", baseUrl = "http://10.0.2.2:8899/")
        assertEquals("http://10.0.2.2:8899", setupAddressOf(api))
        val dashboard = ServerConfig(id = "b", serveBaseUrl = "http://192.168.1.20:9119")
        assertEquals("http://192.168.1.20:9119", setupAddressOf(dashboard))
    }
}
