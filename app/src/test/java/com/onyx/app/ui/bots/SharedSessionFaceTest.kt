package com.onyx.app.ui.bots

import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.ui.sidebar.sidebarRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * **Deux bots, deux visages — même quand la session du serveur est la même.**
 *
 * Relevé sur le téléphone : un bot créé **à partir de** « coder », renommé « coder_logs », et les
 * deux rangées portaient le **même** visage ; changer le visage de l'un ne changeait que l'autre.
 *
 * La cause n'est pas le magasin de visages (une clé par nom de profil) mais **l'identité** : un bot
 * cloné hérite du `canonical_session` de son original (`clone_from` côté serveur). Deux bots
 * revendiquaient donc la même session, et `botOfConversation` prenait le **premier du roster** —
 * les deux conversations recevaient la même identité, donc le même visage.
 *
 * Ce fichier tient la règle : une session partagée **ne désigne personne**, c'est le nom de la
 * conversation qui tranche, et à défaut le profil que la conversation a retenu.
 */
class SharedSessionFaceTest {

    private val server = ServerConfig(id = "srv", name = "Onyx", baseUrl = "https://exemple.test")

    /** Les deux bots réclament la **même** session — c'est ce que `clone_from` produit. */
    private fun bots(coderFace: String, logsFace: String) = listOf(
        BotProfile(name = "coder", chatId = "session-1", faceCode = coderFace),
        BotProfile(name = "coder_logs", chatId = "session-1", faceCode = logsFace),
    )

    private fun conversation(name: String, botProfile: String? = null) = Conversation(
        id = "conv-$name",
        title = name,
        createdAt = 0L,
        updatedAt = 0L,
        serverSessionId = "session-1",
        botProfile = botProfile,
    )

    private val coder = conversation("coder")
    private val logs = conversation("coder_logs")

    // MARK: - L'identité

    @Test
    fun `une session partagee ne donne pas le premier bot aux deux conversations`() {
        val roster = bots("1", "2")
        assertEquals("coder", conversationProfile(coder, roster, server))
        assertEquals("coder_logs", conversationProfile(logs, roster, server))
        assertNotEquals(
            "les deux conversations ne peuvent pas parler au même bot",
            conversationProfile(coder, roster, server),
            conversationProfile(logs, roster, server),
        )
    }

    @Test
    fun `sans nom qui tranche, la conversation garde le profil qu elle a retenu`() {
        // La conversation ne se nomme plus d'après un bot : c'est `botProfile` qui répond.
        val renamed = conversation("ma session de logs", botProfile = "coder_logs")
        assertEquals("coder_logs", conversationProfile(renamed, bots("1", "2"), server))
    }

    @Test
    fun `une session exclusive reste le lien fort`() {
        // Un seul bot revendique la session : rien ne change, c'est lui — même si la conversation
        // porte un autre nom.
        val solo = listOf(BotProfile(name = "coder", chatId = "session-1", faceCode = "1"))
        assertEquals("coder", conversationProfile(conversation("notes perso"), solo, server))
    }

    // MARK: - Le visage, jusqu'à la rangée

    @Test
    fun `changer le visage d un bot ne change que sa rangee`() {
        fun rows(faces: Map<String, String?>) = sidebarRows(
            conversations = listOf(coder, logs),
            liveKinds = emptyMap(),
            seenMarks = emptyMap(),
            now = 1_000.0,
            identityOf = { c -> conversationProfile(c, bots("1", "2"), server) },
            faceCodeOf = { de -> faces[de] },
        )

        val before = rows(mapOf("coder" to "1", "coder_logs" to "2"))
        assertEquals("1", before.first { it.title == "coder" }.identityFace)
        assertEquals("2", before.first { it.title == "coder_logs" }.identityFace)

        // On change **seulement** le visage de coder.
        val after = rows(mapOf("coder" to "7", "coder_logs" to "2"))
        assertEquals("7", after.first { it.title == "coder" }.identityFace)
        assertEquals(
            "la rangée de coder_logs ne bouge pas",
            "2",
            after.first { it.title == "coder_logs" }.identityFace,
        )
    }
}
