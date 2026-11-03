package com.onyx.app.ui.bots

import com.onyx.app.data.local.BotPresence
import com.onyx.app.data.local.BotPresenceIndex
import com.onyx.app.data.model.Conversation
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La présence unique ne se réécrit pas à côté.** Le contrat de `BotFaceResolver` est qu'un écran
 * ne lit jamais `BotFaceStore` pour dessiner : il lit la table du roster. Quatre endroits de `:app`
 * refaisaient pourtant le calcul — la tuile de raccourci (deux fois), la salle, le volet et
 * l'éditeur — et le widget écrivait « Onyx » en dur, effaçant l'identité du bot.
 *
 * Ces essais lisent la source, comme `BotFaceResolverTest` : ce qui se voit à l'écran est prouvé
 * à l'émulateur, ici on verrouille le **chemin**.
 */
class PresenceUniqueTest {

    private fun read(path: String): String =
        listOf(File(path), File("../$path")).firstOrNull { it.isFile }?.readText() ?: ""

    private val shortcuts = read("src/main/java/com/onyx/app/shortcuts/RosterShortcuts.kt")
    private val room = read("src/main/java/com/onyx/app/ui/bots/GroupRoomViewModel.kt")
    private val app = read("src/main/java/com/onyx/app/ui/OnyxApp.kt")
    private val bots = read("src/main/java/com/onyx/app/ui/bots/BotsViewModel.kt")
    private val widget = read("src/main/java/com/onyx/app/widget/OnyxWidget.kt")

    // — 1. La tuile de raccourci —

    @Test
    fun laTuileNeLitPlusLeMagasinPourDessiner() {
        assertFalse(
            "la tuile ne relit plus BotFaceStore pour dessiner",
            shortcuts.contains("BotFaceStore"),
        )
        assertFalse(
            "ni le magasin du conteneur",
            shortcuts.contains("botFaces.faceOf"),
        )
        assertTrue(
            "son repli est la résolution unique du roster",
            shortcuts.contains("val faceCode = shortcut.faceCode ?: faceResolver.codeOf(shortcut.bot)"),
        )
        assertTrue(
            "la publication reçoit cette résolution",
            shortcuts.contains("faceResolver: BotFaceResolver = BotFaceResolver.none"),
        )
        assertTrue(
            "et la section lui passe la table du roster",
            bots.contains("faceResolver = BotFaceResolver.of(state.faces)"),
        )
    }

    // — 2. La salle —

    @Test
    fun laSalleNeLitPlusLeMagasinDirectement() {
        assertFalse(
            "la salle ne garde plus le magasin comme source de visage",
            room.contains("private val faceStore = BotFaceStore("),
        )
        assertTrue(
            "elle le pose en repli **du résolveur**, son crochet local",
            room.contains("BotFaceResolver.of(emptyMap(), BotFaceStore(application)::faceOf)"),
        )
        assertTrue(
            "et le visage qu'elle rend est celui de la résolution unique",
            room.contains("faces.value?.invoke(name) ?: faceStore.faceOf(name)"),
        )
        assertTrue(
            "le champ est un résolveur, pas un magasin",
            room.contains("private val faceStore: BotFaceResolver ="),
        )
    }

    // — 3. Le volet et l'éditeur —

    @Test
    fun leVoletLitLaMemeInstanceQueLeFil() {
        assertTrue(
            "le volet passe par le résolveur du fil",
            app.contains("faceResolver::codeOf"),
        )
        assertFalse(
            "il ne refait plus le calcul du visage",
            app.contains("knownBots.firstOrNull { it.name == name }?.faceCode"),
        )
    }

    @Test
    fun lEditeurNEcritPlusUneSecondeRegle() {
        assertTrue(
            "le visage d'un bot vient de la résolution unique",
            bots.contains("BotFaceResolver.of(_state.value.faces).codeOf(bot.name)"),
        )
        assertFalse(
            "sans second repli sur le profil",
            bots.contains("BotFaceResolver.of(_state.value.faces).codeOf(bot.name) ?: bot.faceCode"),
        )
    }

    // — 4. Le widget —

    @Test
    fun leWidgetNommeLeBotQuandIlLeConnait() {
        assertTrue(
            "l'en-tête écrit le locuteur résolu",
            widget.contains("text = snapshot.speaker,"),
        )
        assertFalse(
            "et plus « Onyx » en dur dans l'en-tête",
            widget.contains("text = \"Onyx\","),
        )
        assertTrue(
            "le locuteur vient de la table publiée par le roster",
            widget.contains("BotPresenceIndex.labelOf(conversation, BotPresenceIndex(context).entries())"),
        )
        assertTrue(
            "« Onyx » reste le repli",
            widget.contains("?: NotificationCopy.ONYX"),
        )
    }

    // — 5. Le lien conversation → bot, celui du widget —

    private fun conversation(id: String = "c1", session: String? = null) =
        Conversation(
            id = id,
            createdAt = 0L,
            updatedAt = 0L,
            serverSessionId = session,
        )

    private val roster = listOf(
        BotPresence(name = "scout", label = "Scout", chatId = "session-scout"),
        BotPresence(name = "quill", label = "Quill", chatId = null),
    )

    @Test
    fun leWidgetSuitLeChatServeurDuBot() {
        val linked = conversation(session = "session-scout")
        assertEquals("Scout", BotPresenceIndex.labelOf(linked, roster))
    }

    @Test
    fun leWidgetSuitLeTitrePoseParLOuvertureDuChat() {
        val titled = Conversation(
            id = "c2",
            title = "Quill",
            createdAt = 0L,
            updatedAt = 0L,
            messages = listOf(
                com.onyx.app.data.model.ChatMessage(
                    id = "m1",
                    role = com.onyx.app.data.model.Role.USER,
                    text = "tu es là ?",
                    createdAt = 1L,
                ),
            ),
        )
        assertEquals("Quill", BotPresenceIndex.labelOf(titled, roster))
    }

    @Test
    fun leWidgetGardeOnyxQuandAucunBotNeRevendiqueLaConversation() {
        assertNull(BotPresenceIndex.labelOf(conversation(), roster))
        // Un titre qui ne désigne aucun bot ne rattache rien non plus.
        val other = Conversation(id = "c3", title = "Réunion", createdAt = 0L, updatedAt = 0L)
        assertNull(BotPresenceIndex.labelOf(other, roster))
        // Roster jamais lu : rien à nommer.
        assertNull(BotPresenceIndex.labelOf(conversation(session = "session-scout"), emptyList()))
    }
}