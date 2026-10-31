package com.onyx.app.ui.bots

import com.onyx.app.localization.OnyxLocalization
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les 14 contrôles et rappels morts trouvés par l'audit statique (`ROUTES-PARITY-AUDIT.md` §7) :
 * chacun doit soit agir, soit être refusé avec un message — jamais rester muet.
 *
 * L'écran est vérifié sur ses sources, comme les autres passes de parité : ce qui compte est que
 * le rappel soit **fourni** au site d'appel, relié à une écriture, et que rien d'inerte ne
 * subsiste.
 */
class DeadHandlersWiringTest {
    private val root = File("src/main/java/com/onyx/app/ui")
    private val onyxApp = File(root, "OnyxApp.kt").readText()
    private val botEditor = File(root, "bots/BotEditorScreen.kt").readText()
    private val botEditorViewModel = File(root, "bots/BotEditorViewModel.kt").readText()
    private val botsViewModel = File(root, "bots/BotsViewModel.kt").readText()
    private val groupChat = File(root, "bots/GroupChatScreen.kt").readText()
    private val chatScreen = File(root, "chat/ChatScreen.kt").readText()
    private val voiceScreen = File(root, "voice/VoiceScreen.kt").readText()
    private val terminal = File(root, "terminal/TerminalScreen.kt").readText()
    private val scanner = File(root, "setup/SetupCodeScanner.kt").readText()
    private val coreDesign = File("../core/src/main/java/com/onyx/app/ui/design")
    private val onyxAlert = File(coreDesign, "OnyxAlert.kt").readText()
    private val openingCover = File(coreDesign, "OpeningCover.kt").readText()

    // MARK: - 1. Supprimer un bot le supprime vraiment

    @Test
    fun `le bouton Supprimer passe par la route serveur et ne navigue qu'ensuite`() {
        val branch = onyxApp.substringAfter("Route.BOT_EDITOR -> {").substringBefore("Route.BOT_FACE -> {")
        assertTrue("la suppression est écrite au serveur", branch.contains("botsViewModel.deleteBot(target)"))
        assertTrue("l'écran ne se ferme qu'après une suppression qui a pris", branch.contains("if (failure == null)"))
        assertTrue("un refus se lit dans l'éditeur", branch.contains("Couldn't delete it: %1\\\$s"))
        assertFalse(
            "effacer le visage ne remplace pas une suppression",
            branch.contains("setFace(it, null)"),
        )
        // Le modèle de l'éditeur remonte l'échec, comme le `Label(problem, …)` de la référence.
        assertTrue(botEditor.contains("problem: String? = null"))
        assertTrue(botEditor.contains("problem?.let { message ->"))
        // Et l'écriture est bien un DELETE du profil.
        assertTrue(botsViewModel.contains("profiles.deleteProfile(bot.name)"))
        assertTrue(botsViewModel.contains("The main profile can't be deleted."))
        assertTrue("le visage et les mots locaux partent avec lui", botsViewModel.contains("faces.forget(bot.name)"))
        assertTrue("le roster perd le bot sans perdre les autres", botsViewModel.contains("sourceBots = sourceBots.filterNot { it.name == bot.name }"))
    }

    // MARK: - 2 à 4. Compétences et jeux d'outils

    @Test
    fun `les interrupteurs de competence, l'epingle et les jeux d'outils sont fournis`() {
        val branch = onyxApp.substringAfter("Route.BOT_EDITOR -> {").substringBefore("Route.BOT_FACE -> {")
        assertTrue(branch.contains("onToggleSkill = editorViewModel::toggleSkill"))
        assertTrue(branch.contains("onPinSkill = editorViewModel::pinSkill"))
        assertTrue(branch.contains("onToolsSelectionChange = editorViewModel::setToolsSelection"))
        assertTrue(branch.contains("skills = editorState.skills"))
        assertTrue(branch.contains("alwaysLoadedSkills = editorState.alwaysLoaded"))
        assertTrue(branch.contains("toolsets = editorState.toolsets"))
        assertTrue(branch.contains("toolsSelection = editorState.toolsSelection"))
        assertTrue(branch.contains("mainToolsets = editorState.mainToolsets"))
    }

    @Test
    fun `chaque geste part au serveur, et un refus remet l'etat d'avant`() {
        // Allumer/éteindre : `PUT api/skills/toggle` (référence `toggleSkill`).
        assertTrue(botEditorViewModel.contains("skills.toggle(skill.name, enabled, bot)"))
        // Épingler : toute la liste `auto_load` est écrite (référence `setAlwaysLoadedSkills`).
        assertTrue(botEditorViewModel.contains("skills.setAlwaysLoaded(names.sorted(), bot)"))
        // Jeux d'outils : `profiles.configure { enabled_toolsets }` (référence `BotChange.toolsets`).
        assertTrue(botEditorViewModel.contains("ServeBotsRpc(rpc).setToolsets(bot, names)"))
        // Un refus est montré, et l'état revient comme il était : rien n'est dit fait sans l'être.
        assertTrue(botEditorViewModel.contains("alwaysLoaded = before.alwaysLoaded"))
        assertTrue(botEditorViewModel.contains("toolsSelection = before"))
        assertTrue(botEditorViewModel.contains("Couldn't switch %1\\\$s on: %2\\\$s"))
        assertTrue(botEditorViewModel.contains("Couldn't pin %1\\\$s: %2\\\$s"))
    }

    // MARK: - 5. « Try again » de la liste

    @Test
    fun `le bouton Try again relit vraiment la liste`() {
        // La carte de problème de l'écran retiré vit maintenant dans la **ligne d'état** du volet :
        // c'est elle qui relit les deux listes, et elle n'apparaît que si la passerelle n'a pas
        // répondu. Le rond de réessai de la tête a été retiré — deux chemins pour le même geste
        // auraient été un doublon —, donc c'est la ligne qui doit porter le rappel.
        val volet = File(root, "sidebar/ChatSidebar.kt").readText()
        assertTrue("la ligne d'état n'existe plus", volet.contains("SidebarStatusLine("))
        assertTrue("elle n'apparaît que si la passerelle n'a pas répondu", volet.contains("if (reachable == false) {"))
        assertTrue("le rappel est passé à la ligne d'état", volet.contains("onRetry = onRetry,"))
        assertTrue("le libellé du bouton n'est plus celui de la ligne d'état", volet.contains("text = SAVED_COPY_RETRY_LABEL,"))
        assertTrue("il est branché", onyxApp.contains("onRetry = {"))
        assertTrue("la liste des bots est relue", onyxApp.contains("botsViewModel.refresh()"))
        assertTrue("et celle des conversations", onyxApp.contains("scope.launch { conversationRepo.load() }"))
    }

    // MARK: - 6. « Let bots message each other » installe le réglage

    @Test
    fun `la ligne qui fait parler les bots n'ouvre plus Reglages`() {
        val bots = onyxApp.substringAfter("val botsPane").substringBefore("val cronPane")
        assertTrue(bots.contains("onLetBotsMessage = { askingLetBotsMessage = true }"))
        assertFalse("plus de détour vers Réglages", bots.contains("onLetBotsMessage = { navTo(Route.SETTINGS.name) }"))
        // La question et l'écriture, comme le confirmationDialog de la référence.
        assertTrue(onyxApp.contains("title = \"Let bots message each other?\""))
        assertTrue(onyxApp.contains("AlertAction(\"Let Them\")"))
        // Le résultat n'est plus jeté : il est montré (succès ou échec) — l'appui restait muet.
        assertTrue(onyxApp.contains("val failure = botsViewModel.letBotsMessage()"))
        assertTrue(botsViewModel.contains("ServeBotsRpc(rpc).letBotsMessageEachOther(main.name)"))
    }

    // MARK: - 7 et 8. La recherche du chat suit référence

    @Test
    fun `la recherche du chat n'annonce plus une liste de resultats qui n'existe pas`() {
        assertFalse("TranscriptView n'a pas de liste de résultats", chatScreen.contains("searchMatches:"))
        assertFalse("ni d'ouverture d'un résultat", chatScreen.contains("onOpenSearchResult:"))
        // Le pas-à-pas de la référence, lui, reste : le champ, le compte et les flèches.
        assertTrue(chatScreen.contains("matches = state.searchHits.size"))
        assertTrue(chatScreen.contains("onStep = onStepSearch"))
    }

    // MARK: - 9. L'orbe solo est un bouton

    @Test
    fun `l'orbe solo est cliquable et suit la phase`() {
        val voice = onyxApp.substringAfter("Route.VOICE -> VoiceScreen(").substringBefore("Route.COMPOSER_EDITOR")
        assertTrue(voice.contains("onOrbClick = {"))
        assertTrue("en veille, l'orbe se met à écouter", voice.contains("voice.startListening()"))
        assertTrue(voiceScreen.contains("onClick = onOrbClick"))
        assertTrue(voiceScreen.contains("private fun VoiceOrbDisplay("))
        assertTrue("l'orbe porte une action", voiceScreen.contains(".onyxClickable(onClick = onClick)"))
    }

    // MARK: - 10 et 11. Salle : mains-libres, et fin d'ouverture

    @Test
    fun `la salle ne passe plus un rappel mains-libres qui ne fait rien`() {
        val group = onyxApp.substringAfter("Route.GROUP_VOICE -> {").substringBefore("Route.PHONE_TOOLS")
        assertFalse("plus de lambda vide", group.contains("onToggleHandsFree = {}"))
        assertTrue("la pastille n'est dessinée que si le rappel existe", voiceScreen.contains("onToggleHandsFree: (() -> Unit)? = null"))
        assertTrue(voiceScreen.contains("onToggleHandsFree?.let { toggle ->"))
    }

    @Test
    fun `la fin de l'ouverture est traitee et le fondu va jusqu'au bout`() {
        val cover = onyxApp.substringAfter("if (openingRect != null) {").substringBefore("if (askingLetBotsMessage)")
        assertTrue("la couverture se retire à la fin", cover.contains("onFinished = { openingRect = null }"))
        assertFalse("et pas dès l'échange", cover.contains("onSwapped = { openingRect = null }"))
        assertTrue("l'échange est facultatif", openingCover.contains("onSwapped: (() -> Unit)? = null"))
    }

    // MARK: - 12 à 14. Les rappels vides restants

    @Test
    fun `l'alerte de confiance du terminal n'a pas de sortie muette`() {
        val trust = terminal.substringAfter("model.askingTrust?.let { fingerprint ->").substringBefore("private fun TerminalStatusCard")
        assertTrue("on ne ferme pas une question de confiance en touchant à côté", trust.contains("onDismiss = null"))
        assertFalse(trust.contains("onDismiss = {}"))
        assertTrue(onyxAlert.contains("onDismiss: (() -> Unit)? = null"))
        assertTrue("sans sortie, le dialogue n'est pas fermable", onyxAlert.contains("dismissOnClickOutside = dismissable"))
    }

    @Test
    fun `le scanner ne passe plus un rappel pour un bouton absent`() {
        assertFalse(scanner.contains("onAction = {}"))
        assertTrue(scanner.contains("onAction: (() -> Unit)? = null"))
        assertTrue("le bouton n'est dessiné qu'avec son libellé et son rappel", scanner.contains("if (label != null && onAction != null)"))
    }

    @Test
    fun `la salle n'a plus de menu de bot qui ne pouvait pas s'ouvrir`() {
        assertFalse("plus de rappel mort", groupChat.contains("onOpenBotChat"))
        assertFalse("plus d'état jamais posé", groupChat.contains("menuBot"))
        assertFalse(groupChat.contains("OnyxMenu"))
        // Un bot de la salle se choisit d'un tap, sans appui long (référence `GroupChatView`).
        assertTrue(groupChat.contains(".onyxClickable { onToggle(bot) }"))
    }

    // MARK: - La langue

    @Test
    fun `les nouveaux messages sont dits en francais`() {
        assertTrue(OnyxLocalization.translate("Let bots message each other?", Locale.FRENCH).endsWith("?"))
        assertFalse(
            OnyxLocalization.translate("Couldn't delete it: %1\$s", Locale.ENGLISH) ==
                OnyxLocalization.translate("Couldn't delete it: %1\$s", Locale.FRENCH),
        )
        val french = OnyxLocalization.translate("Couldn't delete it: %1\$s", Locale.FRENCH)
        assertEquals("Impossible de le supprimer : %1\$s", french)
        assertEquals("Le profil principal ne peut pas être supprimé.", OnyxLocalization.translate("The main profile can't be deleted.", Locale.FRENCH))
        assertEquals("Les laisser", OnyxLocalization.translate("Let Them", Locale.FRENCH))
    }
}
