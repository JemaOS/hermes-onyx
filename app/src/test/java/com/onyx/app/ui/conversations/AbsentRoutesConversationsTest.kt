package com.onyx.app.ui.conversations

import com.onyx.app.localization.OnyxLocalization
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les deux destinations de la liste des conversations qui manquaient: « Usage & cost »
 * (`SessionUsageView`) et les sessions d'un projet (`ProjectSessionsView`), plus la feuille
 * « New Group Chat » (`NewGroupChatView`), dont le choix des bots doit vraiment compter.
 */
class AbsentRoutesConversationsTest {

    private val screen = File("src/main/java/com/onyx/app/ui/conversations/ConversationsScreen.kt").readText()
    private val usage = File("src/main/java/com/onyx/app/ui/conversations/SessionUsageScreen.kt").readText()
    private val projects = File("src/main/java/com/onyx/app/ui/conversations/ProjectSessionsScreen.kt").readText()

    // MARK: - SessionUsageView

    @Test
    fun `la fiche d usage reprend les trois sections `() {
        assertTrue(usage.contains("OnyxFormSection(header = \"Tokens\")"))
        assertTrue("les compteurs du registre", usage.contains("UsageRow(\"Reasoning\""))
        assertTrue(usage.contains("UsageRow(\"Cache read\""))
        assertTrue(usage.contains("UsageRow(\"Cache write\""))
        assertTrue(usage.contains("UsageRow(\"Total\", summary.totalTokens.toString())"))
        assertTrue(usage.contains("OnyxFormSection(header = \"Cost\")"))
        assertTrue("le coût n'apparaît qu'avec un coût", usage.contains("if (cost > 0) {"))
        assertTrue(usage.contains("UsageRow(\"Estimated\""))
        assertTrue(usage.contains("UsageRow(\"Actual\""))
        assertTrue(usage.contains("OnyxFormSection(header = \"Activity\")"))
        assertTrue("la phrase du pied de page ", usage.contains("Costs are the gateway's own estimate from provider pricing."))
        assertTrue("son bouton Done", usage.contains("action = \"Done\""))
        assertTrue("qui ferme l'écran", usage.contains("onAction = onBack"))
    }

    @Test
    fun `les chiffres du serveur priment sur ceux du fil local`() {
        assertTrue(usage.contains("ServeSessionsApi(ServeClient(reachable)).summary(stored)"))
        assertTrue(usage.contains("if (!reachable.hasServeCredentials) return@LaunchedEffect"))
    }

    @Test
    fun `le menu d une rangee porte Usage et cout, avant Rename`() {
        val menu = screen.substringAfter("// Le menu d'une rangée").substringBefore("OnyxMenu(items = items")
        val usageItem = menu.indexOf("\"Usage & cost\"")
        val renameItem = menu.indexOf("\"Rename\"")
        assertTrue("l'entrée existe", usageItem > 0)
        assertTrue("elle précède Rename, comme le contextMenu ", usageItem < renameItem)
        assertTrue(menu.contains("openUsage(conversation)"))
    }

    // MARK: - ProjectSessionsView

    @Test
    fun `les sessions d un projet suivent `() {
        assertTrue(projects.contains("class ProjectSessionsViewModel"))
        assertTrue("la route ", projects.contains(".projectSessions(projectId)"))
        assertTrue("un projet, ses voies, leurs sessions", projects.contains("lane.sessions.forEachIndexed"))
        assertTrue("sa recherche", projects.contains("SearchField(placeholder = \"Search in \${project.label}\""))
        assertTrue("son état vide", projects.contains("text = \"No conversations yet\""))
        assertTrue("le dossier du projet", projects.contains("Conversations started here work in its folder on your server: \$path"))
        assertTrue("son titre", projects.contains("OnyxPageHeader(title = project.label, onBack = onBack, localizeTitle = false)"))
    }

    @Test
    fun `une rangee ouvre la fiche d usage, faute de pouvoir rouvrir la session`() {
        assertTrue(projects.contains("onOpenSession: (SessionSummary) -> Unit"))
        assertTrue(projects.contains("ProjectSessionRow(session = session, onClick = { onOpenSession(session) })"))
    }

    @Test
    fun `la section Projects de la liste montre les projets du serveur`() {
        assertTrue(screen.contains("private fun ServerProjectsSection("))
        assertTrue("le dossier du projet", screen.contains("symbol = if (project.isHome) Symbols.HouseFill else Symbols.FolderFill"))
        assertTrue("le fourre-tout et le dossier personnel sont écartés du compte", screen.contains("\"\${project.sessionCount} conversations\""))
        assertTrue("une rangée ouvre le projet", screen.contains("OnyxRow(onClick = { onOpen(project) })"))
        // Sans Dashboard, le regroupement local reste: rien ne disparaît.
        assertTrue(screen.contains("} else if (projects.isNotEmpty()) {"))
    }

    // MARK: - NewGroupChatView

    @Test
    fun `la feuille choisit deux a six bots, et son choix compte`() {
        val sheet = screen.substringAfter("private fun NewGroupSheet(").substringBefore("/** Une ligne « libellé → valeur »")
        assertTrue("son titre", sheet.contains("text = \"New Group Chat\""))
        assertTrue("la phrase ", sheet.contains("Two to six. A message goes to all of them"))
        assertTrue("six au plus", sheet.contains("picked.size < 6"))
        assertTrue("deux au moins pour créer", sheet.contains("enabled = picked.size >= 2"))
        assertTrue("le champ du nom", sheet.contains("text = \"Name\""))
        assertTrue("les visages des bots", sheet.contains("BotLook(name = bot.name, faceCode = bot.faceCode, size = 30.dp)"))
        // Le choix remonte jusqu'à la salle: ni perdu, ni ignoré.
        assertTrue(sheet.contains("onCreate(picked, name.trim())"))
        assertTrue(screen.contains("onNewGroupChat(picked, name)"))
    }

    // MARK: - La langue

    @Test
    fun `les libelles neufs sont dits en francais`() {
        assertEquals("Usage et coût", OnyxLocalization.translate("Usage & cost", Locale.FRENCH))
        assertEquals("Raisonnement", OnyxLocalization.translate("Reasoning", Locale.FRENCH))
        assertEquals("Cache écrit", OnyxLocalization.translate("Cache write", Locale.FRENCH))
        assertEquals("Coût", OnyxLocalization.translate("Cost", Locale.FRENCH))
        assertEquals("Réel", OnyxLocalization.translate("Actual", Locale.FRENCH))
        assertEquals("Origine", OnyxLocalization.translate("Source", Locale.FRENCH))
        assertEquals("Impossible de charger le projet", OnyxLocalization.translate("Couldn't load the project", Locale.FRENCH))
        assertEquals("Rechercher dans Echo", OnyxLocalization.translate("Search in Echo", Locale.FRENCH))
        assertEquals("12 messages", OnyxLocalization.translate("12 messages", Locale.FRENCH))
    }
}
