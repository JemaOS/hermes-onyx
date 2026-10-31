package com.onyx.app.data.net

import com.onyx.app.localization.OnyxLocalization
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportErrorLocalizationTest {
    @Test
    fun `les erreurs locales suivent la langue demandee`() {
        assertEquals(
            "Aucune clé API enregistrée. Ajoutez-en une dans les réglages.",
            TransportError.MissingApiKey.userMessage(Locale.FRANCE),
        )
        assertEquals(
            "No API key is saved. Add one in Settings.",
            TransportError.MissingApiKey.userMessage(Locale.ENGLISH),
        )
        assertEquals(
            "No API key is saved. Add one in Settings.",
            TransportError.MissingApiKey.userMessage(Locale.JAPAN),
        )
    }

    @Test
    fun `les details transport produits localement sont traduits sans toucher aux donnees`() {
        val cases = linkedMapOf(
            "Local server" to "Serveur local",
            "empty base URL" to "URL de base vide",
            "unreadable chunk: %1\$s" to "fragment illisible : %1\$s",
            "empty Dashboard address" to "adresse du Dashboard vide",
            "empty body" to "corps vide",
            "api/status returned nothing" to "api/status n’a rien renvoyé",
        )
        cases.forEach { (english, french) ->
            assertEquals(french, OnyxLocalization.translate(english, Locale.FRANCE))
            assertEquals(english, OnyxLocalization.translate(english, Locale.ENGLISH))
        }
        assertEquals(
            "fragment illisible : Settings",
            OnyxLocalization.format("unreadable chunk: %1\$s", "Settings", locale = Locale.FRANCE),
        )
    }

    @Test
    fun `le detail du serveur reste verbatim dans les deux langues`() {
        val error = TransportError.Http(500, "Settings")
        assertEquals(
            "Le serveur a renvoyé une erreur HTTP 500. Settings",
            error.userMessage(Locale.FRANCE),
        )
        assertEquals(
            "The server returned HTTP error 500. Settings",
            error.userMessage(Locale.ENGLISH),
        )
        val malformed = TransportError.Malformed("Settings").userMessage(Locale.FRANCE)
        assertTrue(malformed.endsWith("Settings"))
    }

    @Test
    fun `un refus de connexion du Dashboard dit pourquoi, en francais`() {
        // Ce que renvoie `/auth/password-login` quand le mot de passe est faux.
        val refused = TransportError.Http(401, """{"detail":"Invalid credentials"}""")
        assertTrue(refused.userMessage(Locale.FRANCE).contains("refusé la connexion"))

        // Ce que renvoie `/api/*` sans session du tout.
        val noCookie = TransportError.Http(
            401,
            """{"error":"unauthenticated","detail":"Unauthorized","reason":"no_cookie","login_url":"/login"}""",
        )
        assertTrue(noCookie.userMessage(Locale.FRANCE).contains("refusé la connexion"))

        // Et quand la session a expiré.
        val expired = TransportError.Http(
            401,
            """{"error":"session_expired","detail":"Unauthorized","reason":"invalid_or_expired_session"}""",
        )
        assertEquals(
            "Votre session a expiré. Reconnectez-vous dans les réglages.",
            expired.userMessage(Locale.FRANCE),
        )
    }

    @Test
    fun `adresse introuvable, serveur injoignable et delai se disent sans jargon`() {
        assertEquals(
            "Adresse introuvable. Vérifiez l’adresse dans les réglages.",
            TransportError.AddressNotFound("no such host").userMessage(Locale.FRANCE),
        )
        assertEquals(
            "Serveur injoignable. Vérifiez l’adresse dans les réglages.",
            TransportError.Unreachable("no route to host").userMessage(Locale.FRANCE),
        )
        // Le mot technique (« timeout ») ne doit plus sortir dans ce que lit l'utilisateur.
        val timedOut = TransportError.Timeout("timeout").userMessage(Locale.FRANCE)
        assertFalse(timedOut.contains("timeout"))
        assertTrue(timedOut.contains("temps"))

        // Et le cas Dashboard sans identifiants renseignés.
        assertTrue(
            TransportError.SignInRequired.userMessage(Locale.FRANCE).contains("mot de passe"),
        )
    }
}
