package com.onyx.app.ui.chat

import com.onyx.app.data.model.Attachment
import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.OutboxItem
import com.onyx.app.data.model.OutboxState
import com.onyx.app.data.model.Role
import com.onyx.app.data.repo.branchedConversation
import com.onyx.app.data.repo.forkTitle
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * « Brancher à partir d'ici » : ce que la branche copie **exactement**, et ce qu'elle ne touche pas.
 *
 * Le point du propriétaire : après l'action, la nouvelle conversation contient les messages
 * **jusqu'à celui-là inclus**, l'originale est **inchangée**, et **aucun tour** n'est déclenché. Les
 * trois règles vivent dans des fonctions **pures** ([branchPlan], [branchedConversation],
 * [branchPromptLines]) et dans le chemin du ViewModel — vérifiables ici sans appareil.
 *
 * Le branchement n'a **aucune route serveur** : le gateway n'expose pas de quoi bifurquer une
 * session. La copie vit sur le téléphone, la confirmation le dit, et c'est l'absence de tout appel
 * (`confirmBranch` ne touche ni au magasin des tours, ni au flux, ni à l'outbox) qui garantit
 * qu'un branchement ne démarre rien.
 */
class BranchFromHereTest {

    private fun user(id: String, text: String = "q", attachments: List<Attachment> = emptyList()) =
        ChatMessage(id = id, role = Role.USER, text = text, createdAt = 0L, attachments = attachments)

    private fun reply(id: String) = ChatMessage(id = id, role = Role.ASSISTANT, text = "a", createdAt = 0L)

    /** Six messages, trois tours : la cible « a2 » tombe au milieu. */
    private val thread = listOf(user("u1"), reply("a1"), user("u2"), reply("a2"), user("u3"), reply("a3"))

    private val draft = OutboxItem(
        id = "u9",
        text = "pas encore envoyé",
        queuedAt = 5L,
        state = OutboxState.WAITING_FOR_CONNECTION,
    )

    private val original = Conversation(
        id = "conv-1",
        title = "Où est la clé ?",
        createdAt = 0L,
        updatedAt = 1_000L,
        serverId = "default",
        serverSessionId = "sess-42",
        messages = thread,
        outbox = listOf(draft),
    )

    private fun branch(messageId: String = "a2") =
        branchedConversation(original, id = "conv-2", messageId = messageId, now = 9_999L)

    // ---- 1. ce qui est copié ---------------------------------------------------------------

    @Test
    fun `la branche copie le message vise et tout ce qui le precede`() {
        val copy = branch()!!
        assertEquals(listOf("u1", "a1", "u2", "a2"), copy.messages.map { it.id })
        assertEquals(4, copy.messages.size)
        // Le premier et le dernier : le point de départ du fil et le message visé, exactement.
        assertEquals("u1", copy.messages.first().id)
        assertEquals("a2", copy.messages.last().id)
        // Ce qui suit la cible reste à l'originale.
        assertFalse(copy.messages.any { it.id == "u3" || it.id == "a3" })
    }

    @Test
    fun `le compte annonce par la confirmation est celui du fil copie`() {
        val plan = branchPlan(thread, "a2")!!
        // Le message visé **est** inclus : du premier message au dernier, c'est tout le fil.
        assertEquals(4, plan.copiedMessages)
        assertEquals(6, branchPlan(thread, "a3")!!.copiedMessages)
        assertEquals(1, branchPlan(thread, "u1")!!.copiedMessages)
        assertEquals(plan.copiedMessages, branch()!!.messages.size)
    }

    @Test
    fun `la branche garde le texte, le raisonnement et les etapes d'outils`() {
        val rich = ChatMessage(
            id = "a1",
            role = Role.ASSISTANT,
            text = "fait",
            createdAt = 0L,
            reasoning = "je réfléchis",
        )
        val source = original.copy(messages = listOf(user("u1", text = "fais-le"), rich))
        val copy = branchedConversation(source, "conv-3", "a1", 7L)!!
        assertEquals("je réfléchis", copy.messages[1].reasoning)
        assertEquals("fait", copy.messages[1].text)
        assertEquals("fais-le", copy.messages[0].text)
    }

    @Test
    fun `les pieces jointes suivent la copie`() {
        val picture = Attachment(id = "att-1", mimeType = "image/png", uri = "content://media/1", name = "photo.png")
        val source = original.copy(messages = listOf(user("u1"), user("u2", attachments = listOf(picture))))
        val copy = branchedConversation(source, "conv-3", "u2", 7L)!!
        assertEquals(listOf(picture), copy.messages.last().attachments)
        // Le même fichier du téléphone, pas une copie de son contenu : la référence garde la même.
        assertEquals("content://media/1", copy.messages.last().attachments.single().uri)
    }

    // ---- 2. ce qui n'est pas copié, et pourquoi --------------------------------------------

    @Test
    fun `la branche n emporte ni file d attente ni session serveur`() {
        val copy = branch()!!
        // Rien n'est en attente d'envoi : c'est ce qui interdit qu'un branchement déclenche un tour.
        assertTrue(copy.outbox.isEmpty())
        // Pas de session reprise : le premier envoi dans la branche ouvrira la sienne, et rien de
        // ce qu'on y écrira ne tombera dans l'originale.
        assertNull(copy.serverSessionId)
        // Le serveur d'origine, lui, reste noté (la branche vit sur le même).
        assertEquals("default", copy.serverId)
    }

    // ---- 3. l'originale ne bouge pas -------------------------------------------------------

    @Test
    fun `l originale garde ses messages, son titre et sa session`() {
        branch()
        assertEquals(6, original.messages.size)
        assertEquals("Où est la clé ?", original.title)
        assertEquals("sess-42", original.serverSessionId)
        assertEquals(listOf(draft), original.outbox)
    }

    @Test
    fun `la branche est une autre conversation`() {
        val copy = branch()!!
        assertNotEquals(original.id, copy.id)
        assertEquals("conv-2", copy.id)
        // Elle arrive dans les Chats, en tête : ni épinglée, ni archivée, datée de maintenant.
        assertFalse(copy.pinned)
        assertFalse(copy.archived)
        assertEquals(9_999L, copy.updatedAt)
        // Son nombre de tours est celui des messages copiés (deux questions dans « jusqu'à a2 »).
        assertEquals(2, copy.turnCount)
    }

    @Test
    fun `un message absent du fil ne cree rien`() {
        assertNull(branchPlan(thread, "inconnu"))
        assertNull(branchedConversation(original, "conv-9", "inconnu", 1L))
    }

    @Test
    fun `le titre de la branche suit la convention du fil`() {
        assertEquals(forkTitle("Où est la clé ?"), branch()!!.title)
    }

    @Test
    fun `un titre encore au defaut prend la premiere question`() {
        val fresh = original.copy(title = Conversation.DefaultTitle, messages = thread)
        assertEquals(forkTitle("q"), branchedConversation(fresh, "conv-3", "a2", 1L)!!.title)
    }

    // ---- 4. la confirmation, en quatre lignes, et en français ------------------------------

    @Test
    fun `la confirmation dit les quatre lignes, dans l'ordre de la reference`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            val lines = branchPromptLines(BranchPrompt("a2", copiedMessages = 4))
            assertEquals(4, lines.size)
            // 1. ce que le branchement fait — 2. ce qu'il ne fait pas — 3. quand rien ne démarre.
            assertTrue(lines[0], lines[0].contains("Copie cette conversation jusqu’à ce message"))
            assertTrue(lines[0], lines[0].contains("nouvelle conversation"))
            assertTrue(lines[1], lines[1].contains("reste telle quelle"))
            assertTrue(lines[2], lines[2].contains("Aucune tâche ne démarre"))
            // 4. ce que la copie emporte **vraiment** chez nous : le compte, et les pièces jointes.
            assertTrue(lines[3], lines[3].contains("4 messages"))
            assertTrue(lines[3], lines[3].contains("pièces jointes comprises"))
            assertTrue(lines[3], lines[3].contains("ce téléphone"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `un seul message copie se dit au singulier`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            assertTrue(branchPromptLines(BranchPrompt("u1", 1)).last().contains("1 message,"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    // ---- 5. le chemin réel : le bouton, la boîte, et l'absence d'envoi ----------------------

    private val chatViewModel = File("src/main/java/com/onyx/app/ui/chat/ChatViewModel.kt").readText()
    private val chatScreen = File("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt").readText()
    private val onyxApp = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()
    private val messageRow = File("src/main/java/com/onyx/app/ui/chat/components/MessageRow.kt").readText()
    private val alert = File("src/main/java/com/onyx/app/ui/chat/components/BranchFromHereAlert.kt").readText()
    private val symbols = File("../core/src/main/java/com/onyx/app/ui/design/Symbols.kt").readText()

    /** Le corps d'une fonction, de sa déclaration à l'accolade fermante de premier niveau. */
    private fun body(source: String, signature: String): String {
        val start = source.indexOf(signature)
        assertTrue("$signature introuvable", start >= 0)
        var depth = 0
        var index = source.indexOf('{', start)
        val from = index
        while (index < source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(from, index + 1)
                }
            }
            index++
        }
        return source.substring(from)
    }

    @Test
    fun `le geste est branche a l'ecran, et la boite s'affiche`() {
        // Le déclencheur : la rangée d'actions d'une **réponse** finie, jamais d'un tour en cours.
        assertTrue(
            chatScreen.contains("onBranch = if (!message.isUser && !state.isStreaming)"),
        )
        assertTrue(chatScreen.contains("BranchConfirmAlert("))
        // Le geste suit le chemin de la restauration : un message visé, une confirmation, un effet.
        assertTrue(onyxApp.contains("onBranch = chatViewModel::requestBranch"))
        assertTrue(onyxApp.contains("onConfirmBranch = chatViewModel::confirmBranch"))
        assertTrue(onyxApp.contains("onCancelBranch = chatViewModel::cancelBranch"))
        // Et la boîte : les quatre lignes, et **la surface de la maison** ([OnyxConfirm]) — plus une
        // troisième surface dessinée ici (panneau de verre propre, titre centré, actions alignées à
        // droite, « Annuler » en texte nu). Le geste n'efface rien : l'action principale prend donc
        // la pilule d'accent, pas l'encre de danger.
        assertTrue(alert.contains("branchPromptLines(prompt)"))
        assertTrue(alert.contains("OnyxLocalization[\"Branch from here?\"]"))
        assertTrue(alert.contains("OnyxLocalization[\"Cancel\"]"))
        assertTrue(alert.contains("OnyxLocalization[\"Branch this conversation\"]"))
        assertTrue("la confirmation passe par le composant de la maison", alert.contains("OnyxConfirm("))
        assertTrue("le geste n'est pas destructeur : pilule d'accent", alert.contains("destructive = false"))
        assertFalse("la confirmation ne dessine plus son propre panneau", alert.contains("frostedSheet"))
    }

    @Test
    fun `la confirmation ne declenche aucun tour et n envoie rien`() {
        val confirm = body(chatViewModel, "fun confirmBranch()")
        // Ce qui crée la branche : une copie locale, puis l'ouverture de la copie.
        assertTrue(confirm.contains("conversations.branchFrom(source, prompt.messageId)"))
        assertTrue(confirm.contains("open(branch)"))
        // Rien d'autre : aucun envoi, aucun tour lancé, aucune file relancée, aucun réessai.
        listOf("appendTurn", "startStream", "turns.start", "sendText", "retryOutbox", "rpc", "Rewind", "session.").forEach { forbidden ->
            assertFalse("« $forbidden » n'a rien à faire dans un branchement", confirm.contains(forbidden))
        }
        // La demande, elle non plus, ne touche à rien : elle pose la confirmation et s'arrête.
        val request = body(chatViewModel, "fun requestBranch(messageId: String)")
        assertTrue(request.contains("branchPlan(current.messages, messageId)"))
        assertTrue(request.contains("branchPrompt = plan"))
        assertFalse(request.contains("conversations."))
        // Et refuser ne crée rien : la confirmation s'efface.
        assertTrue(body(chatViewModel, "fun cancelBranch()").contains("branchPrompt = null"))
    }

    @Test
    fun `l icone du bouton est le dessin de la reference`() {
        // Le glyphe vient du jeu d'icônes de la référence (`icons-lucide` 1.1.0, `GitBranch`) : la
        // maison n'a aucune version de ce dessin dans ses symboles.
        assertTrue(symbols.contains("val Branch = R.drawable.ic_lucide_git_branch"))
        assertNotNull(File("../core/src/main/res/drawable/ic_lucide_git_branch.xml").takeIf { it.isFile })
        // Et c'est bien cette case qui ouvre la rangée.
        assertTrue(messageRow.contains("ReplyActionSlot(ReplyAction.Branch, Symbols.Branch, \"Branch from here\", canBranch)"))
    }
}
