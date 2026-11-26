package com.onyx.app.data.net

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **La conversation se nomme, sinon le gateway en crée une au hasard.**
 *
 * Sans `X-Hermes-Session-Key`, l'hôte ne sait pas quelle conversation il sert : il fabrique une
 * session à chaque requête et se heurte à sa propre route déjà enregistrée — `admission_conflict`,
 * le bandeau « Réponse inattendue » qui bloquait chaque envoi, avec trois fois le même message dans
 * le fil et un bot qui répondait « Oui John, je suis là » au lieu de la question posée.
 */
class SessionKeyHeaderTest {

    private val client = File("src/main/java/com/onyx/app/data/net/ChatCompletionsClient.kt").readText()
    private val requete = File("src/main/java/com/onyx/app/data/model/ChatRequest.kt").readText()
    private val ecran = File("../app/src/main/java/com/onyx/app/ui/chat/ChatViewModel.kt").readText()

    @Test
    fun `la requete porte la cle de conversation`() {
        assertTrue("le champ existe", requete.contains("val conversationKey: String? = null"))
        assertTrue(
            "il est expliqué (le refus qu'il évite)",
            requete.contains("admission_conflict"),
        )
    }

    @Test
    fun `le client envoie l en-tete quand il y a une cle`() {
        assertTrue("le nom de l'en-tête est déclaré", client.contains("const val SESSION_KEY_HEADER = \"X-Hermes-Session-Key\""))
        assertTrue("il est posé sur la requête", client.contains("header(SESSION_KEY_HEADER, cle.take(MAX_SESSION_KEY))"))
        assertTrue(
            "et seulement avec une clé d'API (le serveur refuse l'en-tête sans identité)",
            client.contains("if (key.isNotEmpty()) header(SESSION_KEY_HEADER"),
        )
    }

    @Test
    fun `le fil l annonce sous son identifiant de conversation`() {
        assertTrue(
            "chaque envoi porte la clé du fil",
            ecran.contains("conversationKey = conversationId,"),
        )
    }
}
