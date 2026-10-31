package com.onyx.app.ui.common

import com.onyx.app.data.net.TransportError
import com.onyx.app.localization.OnyxLocalization
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les **états de page** de ce lot : la panne de liaison, la route absente, et les phrases des états
 * vides. Tout est **pur** (aucun Compose) : la décision se vérifie sans appareil, et les phrases se
 * vérifient dans les deux langues.
 *
 * Le point qui compte ici est que les deux règles lisent **les phrases que le code produit
 * réellement** (`TransportError.userMessage`) : chaque assertion part d'un `TransportError` vrai, pas
 * d'une chaîne inventée pour l'occasion. Une phrase reformulée dans `TransportError` fait donc
 * échouer ce fichier — c'est exactement ce qu'on veut.
 */
class PageStatesTest {

    // MARK: - « le serveur n'a pas répondu » — les cinq familles de TransportError

    @Test
    fun `une panne de liaison est reconnue dans les deux langues`() {
        val connectivity = listOf(
            TransportError.BadUrl("bad url"),
            TransportError.Unreachable("connection refused"),
            TransportError.AddressNotFound("Unable to resolve host"),
            TransportError.Timeout("read timed out"),
            TransportError.StreamLost("unexpected end of stream"),
        )
        for (error in connectivity) {
            for (locale in listOf(Locale.FRANCE, Locale.ENGLISH)) {
                val message = error.userMessage(locale)
                assertTrue(
                    "« $message » (${locale.language}) doit se lire comme une panne de liaison",
                    looksLikeServerTrouble(message),
                )
            }
        }
    }

    @Test
    fun `un refus n'est pas une panne de liaison`() {
        val refusals = listOf(
            TransportError.MissingApiKey,
            TransportError.SignInRequired,
            TransportError.Http(401, "gateway_auth_failed"),
            TransportError.Http(403, ""),
            TransportError.Http(404, ""),
            TransportError.Malformed("bad json"),
            TransportError.Rpc(-32601, "Method not found"),
        )
        for (error in refusals) {
            for (locale in listOf(Locale.FRANCE, Locale.ENGLISH)) {
                val message = error.userMessage(locale)
                assertFalse(
                    "« $message » (${locale.language}) n'est pas une panne de liaison",
                    looksLikeServerTrouble(message),
                )
            }
        }
    }

    @Test
    fun `la phrase du fil suit la meme regle que le panneau des ecrans`() {
        // Le fil décide déjà de la carte qu'il montre (`ConnectionProblemCard.kindOf`) ; ce qu'on
        // verrouille ici, c'est que la règle des écrans de données **ne contredit pas** celle du fil
        // sur les deux familles qui comptent.
        assertTrue(looksLikeServerTrouble("Serveur injoignable. Vérifiez l’adresse dans les réglages."))
        assertTrue(looksLikeServerTrouble("Impossible de contacter votre gateway"))
        assertFalse(looksLikeServerTrouble("Ce serveur ne connaît pas le profil Hermes sélectionné."))
        assertFalse(looksLikeServerTrouble(null))
        assertFalse(looksLikeServerTrouble(""))
    }

    // MARK: - « la route du serveur n'existe pas » (audit §5)

    @Test
    fun `une route absente n'est pas confondue avec une panne`() {
        assertTrue(
            "une méthode JSON-RPC inconnue",
            looksLikeMissingRoute(TransportError.Rpc(-32601, "Method not found")),
        )
        assertTrue(
            "un endpoint REST qui n'est pas là",
            looksLikeMissingRoute(TransportError.Http(404, "not found")),
        )
        assertFalse(
            "un serveur injoignable n'est pas une route absente",
            looksLikeMissingRoute(TransportError.Unreachable("refused")),
        )
        assertFalse(
            "un 500 n'est pas une route absente",
            looksLikeMissingRoute(TransportError.Http(500, "")),
        )
        assertFalse(looksLikeMissingRoute(null))
        // Et dans l'autre sens : une route absente n'est pas une panne de liaison.
        assertFalse(looksLikeServerTrouble(TransportError.Rpc(-32601, "Method not found").userMessage(Locale.FRANCE)))
    }

    // MARK: - Les phrases des états, en français

    @Test
    fun `les etats ajoutes disent leur phrase en francais`() {
        val expected = mapOf(
            "Loading from Onyx…" to "Chargement depuis Onyx…",
            "Try Again" to "Réessayer",
            "No gateway configured" to "Aucun gateway configuré",
            "No hidden bots" to "Aucun bot caché",
            "No conversations in this project yet" to "Aucune conversation dans ce projet pour le moment",
            "Not available on this gateway yet" to "Pas encore disponible sur cette passerelle",
        )
        for ((english, french) in expected) {
            assertEquals("« $english »", french, OnyxLocalization.translate(english, Locale.FRANCE))
        }
    }

    @Test
    fun `les phrases longues des etats vides sont traduites et non vides`() {
        val long = listOf(
            "This folder has nothing Onyx can show. Files whose names start with a dot stay hidden unless you ask for them.",
            "This project's sessions come from the gateway route projects.project_sessions, which this gateway does not serve. Onyx shows nothing here rather than an empty project.",
            "Hidden bots are the gateway roster's own, shared with Hermes Desktop. Nothing is hidden right now, so there is nothing to bring back here.",
            "The gateway's usage route didn't answer for this session, so these are the counts this phone kept. Reasoning tokens and cost come from the gateway alone and stay blank.",
            // La route qui a **répondu** sans la ligne de cette session sous ce profil : le profil
            // est nommé, et le trou `%1$s` reçoit son nom.
            "The gateway's registry answered, but it holds no row for this session under the profile “%1\$s”, so these are the counts this phone kept. Reasoning tokens and cost come from the gateway alone and stay blank.",
            "Code sessions run against a repository on the Hermes gateway. Set up the Hermes Dashboard in Settings, then pick a repository here.",
        )
        for (english in long) {
            val french = OnyxLocalization.translate(english, Locale.FRANCE)
            assertTrue("« $english » doit avoir sa traduction", french != english)
            assertTrue("la traduction ne peut pas être vide", french.isNotBlank())
            // La règle de l'audit : jamais « aucun élément », jamais un état muet.
            assertFalse("« $french » n'est pas une phrase vide", french.equals("Aucun élément", ignoreCase = true))
        }
    }
}
