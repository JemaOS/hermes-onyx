package com.onyx.app.ui.chat.components

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.ui.bots.botOfConversation
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Qui la conversation montre, et où.
 *
 * L'ancien en-tête du fil (le visage posé en tête des messages, avec le nom en pastille juste
 * dessous) a été retiré : le visage vit maintenant dans la barre du haut, où il ne défile pas, et le
 * nom n'est plus écrit sous l'avatar. Ces tests tiennent la règle du visage (le bot de la
 * conversation, sinon Onyx), le rattachement du bot par le titre posé, et les propriétés de la
 * barre : avatar découpé au cercle, Reduce Motion respecté, plus rien dans le fil.
 */
class ThreadSpeakerTest {

    private val topBarSource = File("src/main/java/com/onyx/app/ui/chat/components/OnyxTopBar.kt").readText()
    private val screenSource = File("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt").readText()
    private val botsDir = File("src/main/java/com/onyx/app/ui/chat/components")

    private fun conversation(
        id: String,
        title: String,
        session: String? = null,
        firstMessage: String? = null,
    ) = Conversation(
        id = id,
        title = title,
        createdAt = 0L,
        updatedAt = 0L,
        serverSessionId = session,
        messages = firstMessage?.let {
            listOf(ChatMessage(id = "m", role = Role.USER, text = it, createdAt = 0L))
        } ?: emptyList(),
    )

    @Test
    fun `un bot lie montre son nom et son profil`() {
        val bot = BotProfile(name = "scout", title = "Scout", faceCode = "1:round:round:smile:none:0:0:0")

        val speaker = threadHeaderSpeaker(bot, assistantName = "Onyx")

        assertEquals("Scout", speaker.label)
        assertEquals("scout", speaker.faceName)
        assertEquals("1:round:round:smile:none:0:0:0", speaker.faceCode)
    }

    @Test
    fun `le chat serveur d'un bot lie la conversation`() {
        val bot = BotProfile(name = "scout", title = "Scout", chatId = "sess-1")

        assertEquals("scout", botOfConversation(conversation("c1", "Scout", session = "sess-1"), listOf(bot))?.name)
        assertEquals("scout", botOfConversation(conversation("sess-1", "Scout"), listOf(bot))?.name)
    }

    @Test
    fun `un roster sans chat serveur retrouve le bot par le titre pose`() {
        val bot = BotProfile(name = "scout", title = "Scout") // `canonical_session` absent du roster

        // Le chat d'un bot est ouvert titré de son nom (`botChatToOpen`) : c'est ce titre qui le lie.
        assertEquals(
            "scout",
            botOfConversation(conversation("c1", "Scout", firstMessage = "Bonjour Scout"), listOf(bot))?.name,
        )
        // Un titre qui n'est que la première question ne lie à personne.
        assertNull(botOfConversation(conversation("c2", "Scout", firstMessage = "Scout"), listOf(bot)))
    }

    @Test
    fun `le titre pose lie encore quand la session serveur s'est ecrite`() {
        // Le défaut « scout » : après le premier tour, la session serveur s'écrit dans la
        // conversation, et l'ancien test (`serverSessionId == null`) perdait le bot.
        val bot = BotProfile(name = "scout", title = "Scout")

        assertEquals(
            "scout",
            botOfConversation(
                conversation("c1", "Scout", session = "sess-ecrite", firstMessage = "Bonjour"),
                listOf(bot),
            )?.name,
        )
        // Mais un bot dont le `canonical_session` est celui de la conversation garde la priorité.
        val named = BotProfile(name = "atlas", title = "Atlas", chatId = "sess-ecrite")
        assertEquals(
            "atlas",
            botOfConversation(conversation("c1", "Scout", session = "sess-ecrite"), listOf(bot, named))?.name,
        )
    }

    @Test
    fun `le lien tient a l ouverture par la liste comme par la barre`() {
        // Ouvrir un fil de bot par la liste (nom de rangée) ou par la barre (visage + nom) revient
        // au même lien : le chat serveur du bot (`canonical_session`), sinon le titre posé. Le
        // premier tour écrit la session serveur et un changement de modèle ne touchent ni l'un ni
        // l'autre : le bot reste lié.
        val named = BotProfile(name = "writer", title = "writer", chatId = "sess-w")
        val byTitle = BotProfile(name = "scout", title = "scout")
        assertEquals("writer", botOfConversation(conversation("c1", "writer", session = "sess-w"), listOf(named))?.name)
        assertEquals(
            "scout",
            botOfConversation(conversation("c2", "scout", session = "sess-ecrite", firstMessage = "Bonjour"), listOf(byTitle))?.name,
        )
    }

    @Test
    fun `un titre ambigu ne rattache personne`() {
        // Deux profils du même libellé : le lien serait un faux positif — mieux vaut Onyx.
        val first = BotProfile(name = "scout", title = "Scout")
        val second = BotProfile(name = "scout-2", title = "Scout")

        assertNull(botOfConversation(conversation("c1", "Scout"), listOf(first, second)))
    }

    @Test
    fun `une conversation simple n'est liee a aucun bot`() {
        val bot = BotProfile(name = "scout", title = "Scout")

        assertNull(botOfConversation(conversation("c3", "Test", firstMessage = "Test"), listOf(bot)))
        assertNull(botOfConversation(conversation("c4", "New Conversation"), listOf(bot)))
        assertNull(botOfConversation(conversation("c5", "Test", session = "sess-9", firstMessage = "Test"), listOf(bot)))
    }

    @Test
    fun `un bot sans titre retombe sur le nom de son profil`() {
        val bot = BotProfile(name = "atlas")

        assertEquals("atlas", threadHeaderSpeaker(bot, assistantName = "Onyx").label)
    }

    @Test
    fun `le bot principal montre le nom d'agent et le visage de Onyx`() {
        val bot = BotProfile(name = "default", isDefault = true)

        val speaker = threadHeaderSpeaker(bot, assistantName = "Hermes")

        // « son nom d'agent » : le nom des Réglages → Apparence, et le visage de la bulle de Onyx.
        assertEquals("Hermes", speaker.label)
        assertEquals("default", speaker.faceName)
    }

    @Test
    fun `une conversation simple montre Onyx, jamais un visage vide`() {
        val speaker = threadHeaderSpeaker(bot = null, assistantName = "Hermes")

        assertEquals("Hermes", speaker.label)
        assertEquals("default", speaker.faceName)
        assertNull(speaker.faceCode)
        assertTrue(speaker.label.isNotBlank())
    }

    // MARK: - L'avatar est dans la barre, plus dans le fil

    @Test
    fun `le fil n'a plus d'en-tete ni de pastille de nom`() {
        assertFalse("plus d'élément d'en-tête dans la liste", screenSource.contains("thread-header"))
        assertFalse("plus de composant d'en-tête", screenSource.contains("ThreadHeader("))
        assertFalse("plus de constant d'éléments d'en-tête", screenSource.contains("ThreadHeaderItems"))
        assertFalse(
            "le composant d'en-tête et sa pastille ont été supprimés du module",
            botsDir.listFiles().orEmpty().any { it.name == "ThreadHeader.kt" },
        )
    }

    @Test
    fun `le fil donne le visage de la conversation a la barre du haut`() {
        assertTrue(
            "la barre reçoit le nom du visage de la conversation",
            screenSource.contains("avatarName = threadSpeaker.faceName"),
        )
        assertTrue(
            "la barre reçoit le visage enregistré quand il y en a un",
            screenSource.contains("avatarFaceCode = threadSpeaker.faceCode"),
        )
    }

    @Test
    fun `le visage de la barre n a ni aura ni pulsation`() {
        // Le visage de la barre, c'est la scène vivante `LiveBotFace`, qui dessine par le
        // `BotLook` partagé : la barre ne réinvente pas un avatar.
        assertTrue("la barre montre le visage vivant", topBarSource.contains("LiveBotFace("))
        val liveSource = File("src/main/java/com/onyx/app/ui/chat/components/LiveBotFace.kt").readText()
        assertTrue("l'avatar est dessiné par BotLook", liveSource.contains("BotLook("))
        // L'aura (le disque de couleur derrière la face) n'existe pas : la référence n'en met aucune.
        assertFalse("plus d'aura d'accent", topBarSource.contains("radialGradient"))
        assertFalse("plus de dégradé", topBarSource.contains("Brush."))
        assertFalse("plus de pulsation", topBarSource.contains("rememberInfiniteTransition"))
        assertFalse("plus d'aura non plus dans la scène", liveSource.contains("radialGradient"))
        assertFalse("ni de dégradé", liveSource.contains("Brush."))
        assertFalse("ni de pulsation", liveSource.contains("rememberInfiniteTransition"))
        // Le clignement **du moteur** reste éteint : la scène donne l'instant du visage, sinon deux
        // clignotements se superposeraient. C'est elle qui fait vivre la face, par pouls espacés.
        assertFalse("pas de clignement du moteur dans la barre", topBarSource.contains("rememberBlink"))
        assertTrue("la scène donne l'instant du visage", liveSource.contains("blinks = false"))
        // Rien de Material : la barre reste faite main, comme le reste de l'écran.
        assertFalse(topBarSource.contains("androidx.compose.material"))
    }

    @Test
    fun `la barre nomme le bot de la conversation`() {
        val speakerSource = File("src/main/java/com/onyx/app/ui/chat/components/ThreadSpeaker.kt").readText()
        // La première ligne de la barre porte le nom ; la seconde porte le « @handle » du bot et
        // le modèle (`ContentView:448`) — la répéter à l'identique était le doublon
        // « writer / writer » vu par l'utilisateur.
        // La seconde ligne nomme **le modèle qui répond** (le nom du bot est déjà au-dessus) :
        // le « @handle » ne servait à rien et a été retiré à la demande du propriétaire.
        assertFalse("plus de « @handle » dans la barre", screenSource.contains("\"@\$it\""))
        // La seconde ligne nomme le modèle — **son nom**, jamais l'alias de passerelle que l'app
        // envoie (`hermes-agent`) : c'est la **valeur unique** `state.modelInForce` qui s'écrit, et la
        // requête d'envoi porte exactement la même.
        assertTrue(
            "le sous-titre nomme le modèle en vigueur — la même valeur que l'envoi",
            screenSource.contains("subtitle = state.modelInForce"),
        )
        assertTrue("le profil principal a le handle « hermes »", speakerSource.contains("if (it == OnyxProfile) \"hermes\" else it"))
        assertTrue("l'étiquette au-dessus de la bulle aussi", screenSource.contains("replyName = threadSpeaker.label"))
        assertTrue("le placeholder du composeur aussi", screenSource.contains("title = threadSpeaker.label"))
        assertTrue(topBarSource.contains("onPickModel") && topBarSource.contains("onMenu"))
        assertTrue(topBarSource.contains("onNewConversation"))
    }

    @Test
    fun `les cartes d'interruption nomment le bot de la conversation`() {
        val interrupt = File("src/main/java/com/onyx/app/ui/chat/components/InterruptCards.kt").readText()
        assertTrue(interrupt.contains("speaker: String = \"Onyx\""))
        assertTrue(interrupt.contains("\$speaker has a question"))
        assertTrue(interrupt.contains("\$speaker needs a secret"))
    }
}
