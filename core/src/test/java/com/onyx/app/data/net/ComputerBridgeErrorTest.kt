package com.onyx.app.data.net

import com.onyx.app.localization.OnyxLocalization
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ce que l'écran raconte quand la session n'est pas ouverte.
 *
 * Le défaut d'origine : une seule phrase, et le texte interne de la bibliothèque réseau à la
 * place de la cause — « Impossible de joindre le pont : sent ping but didn't receive pong within
 * 20000ms (after 0 successful ping/pongs) ». Ces tests tiennent les deux bouts : chaque cause
 * connue a **son** texte, et aucun texte ne contient de jargon ni le jeton. Ils couvrent les deux
 * entrées de la même cause — la réponse HTTP de la poignée de main (`401`) et le code de fermeture
 * du pont (`4401`, `4404`, `4429`).
 */
class ComputerBridgeErrorTest {

    @Test
    fun `le refus du pont est lu jusqu'au motif, longueur comprise`() {
        assertEquals(
            ComputerBridgeError.TokenCut(18, 20),
            ComputerBridgeError.fromClose(4401, "bad-token:len:18:20"),
        )
        assertEquals(
            ComputerBridgeError.TokenRefused,
            ComputerBridgeError.fromClose(4401, "bad-token:mismatch"),
        )
        assertEquals(
            ComputerBridgeError.TokenMissing,
            ComputerBridgeError.fromClose(4401, "bad-token:missing"),
        )
        // Motif de la version précédente du pont : le jeton ne correspondait pas.
        assertEquals(
            ComputerBridgeError.TokenRefused,
            ComputerBridgeError.fromClose(4401, "bad token"),
        )
    }

    @Test
    fun `les autres fermetures ont leur cause, et la fermeture polie n'est pas une panne`() {
        assertEquals(ComputerBridgeError.TooManyAttempts(300), ComputerBridgeError.fromClose(4429, "too many attempts"))
        assertEquals(ComputerBridgeError.WrongPath, ComputerBridgeError.fromClose(4404, "not found"))
        assertEquals(ComputerBridgeError.Closed(1011), ComputerBridgeError.fromClose(1011, "quelque chose"))
        assertNull(ComputerBridgeError.fromClose(1000, "app closed the session"))
        assertNull(ComputerBridgeError.fromClose(1001, ""))
    }

    @Test
    fun `une panne de transport ne se confond pas avec une autre`() {
        assertEquals(
            ComputerBridgeError.UnknownHost,
            ComputerBridgeClient.classify(UnknownHostException("no-such-host-onyx.invalid"), null),
        )
        assertEquals(
            ComputerBridgeError.TlsRefused,
            ComputerBridgeClient.classify(SSLHandshakeException("certificate verify failed"), null),
        )
        assertEquals(
            ComputerBridgeError.Timeout,
            ComputerBridgeClient.classify(SocketTimeoutException("timeout"), null),
        )
        assertEquals(
            ComputerBridgeError.Timeout,
            ComputerBridgeClient.classify(InterruptedIOException("timeout"), null),
        )
        assertEquals(
            ComputerBridgeError.Unreachable,
            ComputerBridgeClient.classify(ConnectException("Connection refused"), null),
        )
        // Le texte exact que l'app affichait : il doit être reconnu comme un silence du pont.
        assertEquals(
            ComputerBridgeError.Silent(0),
            ComputerBridgeClient.classify(
                java.io.IOException(
                    "sent ping but didn't receive pong within 20000ms (after 0 successful ping/pongs)",
                ),
                null,
            ),
        )
    }

    @Test
    fun `en francais, chaque cause dit ce qui s'est passe et ce qu'il y a a faire`() {
        val french = linkedMapOf(
            ComputerBridgeError.BadAddress to "Cette adresse de pont n’a pas l’air bonne.",
            ComputerBridgeError.UnknownHost to "Cette adresse n’existe pas. Vérifiez l’adresse du pont.",
            ComputerBridgeError.TlsRefused to "Le certificat du pont a été refusé (adresse ou relais en cause).",
            ComputerBridgeError.TokenRefused to "Le pont a refusé le jeton. Recopiez-le depuis l’ordinateur.",
            ComputerBridgeError.TokenMissing to "Le pont n’a reçu aucun jeton.",
            ComputerBridgeError.TokenMalformed to "Le jeton contient un espace : recopiez-le.",
            ComputerBridgeError.TokenCut(18, 20) to
                "Le jeton enregistré fait 18 caractères, le pont en attend 20 : il a été coupé au collage.",
            ComputerBridgeError.TokenCut(3, 0) to "Le jeton semble coupé. Recopiez-le depuis l’ordinateur.",
            ComputerBridgeError.Silent(45) to "Le pont n’a rien envoyé pendant 45 s : session coupée.",
            ComputerBridgeError.Timeout to "Le pont n’a pas répondu à temps.",
        )
        french.forEach { (error, expected) ->
            assertEquals(expected, error.message(Locale.FRANCE))
        }
    }

    @Test
    fun `aucun message ne cite le jargon de la bibliotheque ni un jeton`() {
        val errors = listOf(
            ComputerBridgeError.BadAddress,
            ComputerBridgeError.UnknownHost,
            ComputerBridgeError.TlsRefused,
            ComputerBridgeError.Unreachable,
            ComputerBridgeError.Timeout,
            ComputerBridgeError.TokenMissing,
            ComputerBridgeError.TokenRefused,
            ComputerBridgeError.TokenCut(18, 20),
            ComputerBridgeError.TokenMalformed,
            ComputerBridgeError.TooManyAttempts(300),
            ComputerBridgeError.WrongPath,
            ComputerBridgeError.Silent(45),
            ComputerBridgeError.Closed(1011),
            ComputerBridgeError.Lost("boom"),
        )
        errors.forEach { error ->
            listOf(Locale.FRANCE, Locale.ENGLISH).forEach { locale ->
                val text = error.message(locale)
                assertFalse("jargon resté: $text", text.contains("pong"))
                assertFalse("millisecondes restées: $text", text.contains("20000ms"))
                assertFalse("mot anglais dans le texte français: $text", text.contains("ping"))
                assertTrue("message vide", text.isNotBlank())
            }
        }
    }

    @Test
    fun `les causes qui demandent une action ne relancent pas la connexion toutes seules`() {
        assertTrue(ComputerBridgeError.TokenRefused.needsUser)
        assertTrue(ComputerBridgeError.TokenCut(18, 20).needsUser)
        assertTrue(ComputerBridgeError.TooManyAttempts(300).needsUser)
        assertTrue(ComputerBridgeError.BadAddress.needsUser)
        // Un silence ou un délai, au contraire, valent un nouvel essai.
        assertFalse(ComputerBridgeError.Silent(45).needsUser)
        assertFalse(ComputerBridgeError.Timeout.needsUser)
        assertFalse(ComputerBridgeError.Unreachable.needsUser)
    }

    @Test
    fun `les cles employees existent vraiment dans le dictionnaire francais`() {
        val keys = listOf(
            "That address doesn't exist. Check the bridge address.",
            "The bridge's certificate was refused.",
            "The bridge received no token.",
            "The bridge refused the token.",
            "The token looks cut. Copy it again from the computer.",
            "The token contains a space.",
            "The bridge doesn't serve that address.",
        )
        keys.forEach { key ->
            val translated = OnyxLocalization.translate(key, Locale.FRANCE)
            assertTrue("clé non traduite: $key", translated != key)
            assertEquals(key, OnyxLocalization.translate(key, Locale.ENGLISH))
        }
    }

    private val fr = Locale.FRANCE
    private val en = Locale.ENGLISH

    @Test
    fun `un nom d'hote introuvable se dit`() {
        assertEquals(
            "Ce nom n’existe pas : vérifiez l’adresse.",
            ComputerBridgeError.fromFailure(UnknownHostException("no-such-host.invalid"), null, fr),
        )
        assertEquals(
            "This name doesn't exist: check the address.",
            ComputerBridgeError.fromFailure(UnknownHostException("no-such-host.invalid"), null, en),
        )
    }

    @Test
    fun `une connexion refusee se dit`() {
        assertEquals(
            "Connexion refusée : rien n’écoute à cette adresse.",
            ComputerBridgeError.fromFailure(ConnectException("Connection refused"), null, fr),
        )
        assertEquals(
            "The connection was refused: nothing is listening there.",
            ComputerBridgeError.fromFailure(NoRouteToHostException("no route"), null, en),
        )
    }

    @Test
    fun `un delai depasse se dit`() {
        assertEquals(
            "Le pont n’a pas répondu à temps.",
            ComputerBridgeError.fromFailure(SocketTimeoutException("timeout"), null, fr),
        )
    }

    @Test
    fun `un TLS refuse se dit`() {
        assertEquals(
            "TLS refusé : un nom de tunnel demande wss://.",
            ComputerBridgeError.fromFailure(SSLHandshakeException("handshake failed"), null, fr),
        )
    }

    @Test
    fun `un jeton refuse se dit, quel que soit le chemin du refus`() {
        // Pendant la poignée de main : la réponse HTTP porte le 401.
        assertEquals(
            "Le pont a refusé le jeton. Recopiez-le depuis l’ordinateur.",
            ComputerBridgeError.fromFailure(ProtocolExceptionLike(), 401, fr),
        )
        // Après la poignée de main : le pont ferme avec son code.
        assertEquals("Le pont a refusé le jeton. Recopiez-le depuis l’ordinateur.", ComputerBridgeError.fromCloseCode(4401, fr))
        assertEquals("The bridge refused the token.", ComputerBridgeError.fromCloseCode(4401, en))
    }

    @Test
    fun `une reponse qui n'est pas le pont se dit`() {
        assertEquals(
            "Quelque chose répond, mais ce n’est pas le pont.",
            ComputerBridgeError.fromFailure(ProtocolExceptionLike(), 301, fr),
        )
        assertEquals(
            "Le pont ne répond pas à ce chemin.",
            ComputerBridgeError.fromCloseCode(4404, fr),
        )
        assertEquals(
            "Trop de tentatives : réessayez dans quelques minutes.",
            ComputerBridgeError.fromCloseCode(4429, fr),
        )
        assertNull(ComputerBridgeError.fromCloseCode(1000, fr))
        assertNull(ComputerBridgeError.fromCloseCode(1006, fr))
    }

    @Test
    fun `une panne inconnue garde le repli et n'invente rien`() {
        val text = ComputerBridgeError.fromFailure(IllegalStateException("boom"), null, fr)
        assertTrue(text, text.startsWith("Impossible de joindre le pont"))
        assertTrue(text, text.endsWith("boom"))
    }

    @Test
    fun `un refus ne se repare pas en reessayant, une panne de reseau si`() {
        assertTrue(ComputerBridgeError.permanent(4401))
        assertTrue(ComputerBridgeError.permanent(4404))
        assertTrue(ComputerBridgeError.permanent(4429))
        assertFalse(ComputerBridgeError.permanent(1006))
        assertFalse(ComputerBridgeError.permanent(1011))
    }

    /** Une panne du type que lève OkHttp quand la réponse n'est pas un `101`. */
    private class ProtocolExceptionLike : java.io.IOException("Expected HTTP 101 response")
}
