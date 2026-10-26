package com.onyx.app.ui.conversations

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'aspect de « tous vos serveurs dans une seule liste »: le menu du serveur au-dessus de la
 * liste, chaque rangée d'un autre serveur étiquetée du sien et **inerte** (ni glissement ni
 * sélection), la ligne qui nomme un serveur absent, et la clé composite qui distingue deux
 * conversations de même identifiant.
 */
class AllServersLayoutTest {

    private val source = File("src/main/java/com/onyx/app/ui/conversations/ConversationsScreen.kt").readText()
    private val remote = source.substringAfter("private fun RemoteConversationRow(")
        .substringBefore("/** L'étiquette d'un serveur sur une rangée")
    private val menuBar = source.substringAfter("private fun ServerMenuBar(")
        .substringBefore("/**\n * La rangée d'un autre serveur")

    @Test
    fun `l ecran prend les serveurs, le serveur actif et la bascule`() {
        assertTrue(source.contains("servers: List<ServerTag> = emptyList()"))
        assertTrue(source.contains("activeServerId: String = \"\""))
        assertTrue(source.contains("onSwitchServer: ((String) -> Unit)? = null"))
        assertTrue(source.contains("onOpenRemote: ((AllServerRow) -> Unit)? = null"))
        assertTrue(source.contains("unreachable: List<ServerTag> = emptyList()"))
    }

    @Test
    fun `le menu du serveur apparait avec deux serveurs et porte All Servers`() {
        assertTrue("montré dès deux serveurs", source.contains("if (servers.size >= 2) {"))
        assertTrue(source.contains("ServerMenuBar("))
        assertTrue("le titre est All Servers ou le nom du serveur", menuBar.contains("if (allServers) \"All Servers\" else activeName"))
        assertTrue("l'icône de la pile de serveurs", menuBar.contains("Symbols.SquareStack3dUp"))
        assertTrue("le rappel de où les conversations neuves commencent", menuBar.contains("\"· new chats on %1\\\$s\""))
        // Le menu lui-même: All Servers coché, puis chaque serveur.
        assertTrue(source.contains("label = \"All Servers\""))
        assertTrue(source.contains("checked = allServers"))
        assertTrue(source.contains("checked = !allServers && server.id == activeServerId"))
        assertTrue(source.contains("symbol = Symbols.SquareStack3dUp"))
    }

    @Test
    fun `un serveur absent est nomme au dessus de la liste`() {
        assertTrue(source.contains("AllServers.unreachableLine(unreachable.map { it.name })"))
        assertTrue("la ligne porte l'icône du réseau", source.contains("OnyxIcon(Symbols.WifiExclamation"))
        assertTrue("la phrase est déjà localisée, pas retraduite", source.contains("localize = false"))
    }

    @Test
    fun `la rangee d un autre serveur est etiquetee et inerte`() {
        assertTrue(source.contains("if (row.remote) {"))
        assertTrue(source.contains("RemoteConversationRow("))
        assertTrue("l'étiquette du serveur est dessinée", remote.contains("ServerTagCapsule(name = row.serverName.orEmpty())"))
        // Inerte: pas de volet de glissement, pas de case de sélection, la rangée s'ouvre ailleurs.
        assertFalse("aucun glissement sur la rangée d'un autre serveur", remote.contains("SwipeableRowActions"))
        assertFalse("aucune sélection sur la rangée d'un autre serveur", remote.contains("selecting"))
        assertTrue("elle s'ouvre sur son serveur", source.contains("onClick = { onOpenRemote?.invoke(row) }"))
    }

    @Test
    fun `la rangee ouverte se reconnait par sa cle composite, jamais par le seul identifiant`() {
        assertTrue(source.contains("val openKey = currentId?.let { AllServers.key(activeServerId, it) }"))
        assertTrue(source.contains("isOpen = row.key == openKey"))
        assertTrue("les rangées épinglées aussi", source.contains("isOpen = AllServers.key(serverOf(conversation), conversation.id) == openKey"))
        assertFalse("plus de comparaison sur le seul identifiant", source.contains("isOpen = conversation.id == currentId"))
    }

    @Test
    fun `seul le serveur ou l app est liste ses conversations sans All Servers`() {
        assertTrue(source.contains("conversations.filter { it.serverId.isBlank() || it.serverId == activeServerId }"))
        assertTrue(source.contains("AllServers.others(conversations, activeServerId, servers)"))
        assertTrue(source.contains("AllServers.merge("))
    }

    @Test
    fun `l icone All Servers existe`() {
        val symbols = File("../core/src/main/java/com/onyx/app/ui/design/Symbols.kt").readText()
        assertTrue(symbols.contains("val SquareStack3dUp = R.drawable.ic_sym_square_stack_3d_up"))
        assertTrue(File("../core/src/main/res/drawable/ic_sym_square_stack_3d_up.xml").exists())
    }
}
