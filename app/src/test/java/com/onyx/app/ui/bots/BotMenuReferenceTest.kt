package com.onyx.app.ui.bots

import com.onyx.app.data.local.FaceKeys
import com.onyx.app.data.model.serve.BotListItem
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.localization.OnyxLanguage
import com.onyx.app.ui.design.LucideMenuIcons
import com.onyx.app.ui.sidebar.botGridColumns
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le menu d'une carte de bot** : les neuf entrées de la référence, dans son ordre
 * (`shared/ui/.../bots/BotsRoster.kt:444-454`), puis celles de la maison — et chacune son geste.
 *
 * Ce que ce fichier tient, et qu'aucun autre ne tient :
 *
 *  - l'**ordre** des neuf, mot pour mot, l'entrée « Nouvelle conversation avec … » portant **le nom
 *    du bot** ;
 *  - **aucune entrée morte** : avec la table de gestes complète, le panneau montre une ligne par
 *    entrée ; retire un geste, et son entrée disparaît ;
 *  - les **icônes de la source**, et la **famille de trait** de leurs fichiers (24, trait 2, bouts
 *    et jointures arrondis) ;
 *  - l'**épinglage** : le bot passe en tête, et le détacher le rend à sa place.
 */
class BotMenuReferenceTest {

    private val ui = File("src/main/java/com/onyx/app/ui")
    private val menus = File(ui, "bots/BotMenus.kt").readText()
    private val chatsidebarBots = File(ui, "sidebar/ChatSidebarBots.kt").readText()
    private val host = File(ui, "sidebar/ChatSidebarHost.kt").readText()
    private val viewModel = File(ui, "bots/BotsViewModel.kt").readText()
    private val app = File(ui, "OnyxApp.kt").readText()
    private val rpc = File("../core/src/main/java/com/onyx/app/data/net/ServeBotsRpc.kt").readText()
    private val icons = File("../core/src/main/java/com/onyx/app/ui/design/LucideMenuIcons.kt").readText()
    private val drawables = File("../core/src/main/res/drawable")

    /**
     * Les **neuf entrées de la référence**, dans son ordre — `BotsRoster.kt:444-454` : « Pin to
     * top », « Hide », « Open recent session », « New chat with … », « Routines », « Start fresh »,
     * « Edit », « Duplicate », « Delete ».
     */
    private val nine = listOf(
        BotMenuAction.Pin,
        BotMenuAction.Hide,
        BotMenuAction.OpenRecent,
        BotMenuAction.NewChat,
        BotMenuAction.Routines,
        BotMenuAction.StartFresh,
        BotMenuAction.EditBot,
        BotMenuAction.Duplicate,
        BotMenuAction.Delete,
    )

    /** Un bot que le Dashboard tient, avec son chat : tout ce que les neuf entrées demandent. */
    private val bot = BotProfile(name = "kilo", chatId = "bot-chat", setUp = true)

    private fun frame(label: String = "Kilo", pinned: Boolean = false, hidden: Boolean = false) =
        BotMenuFrame(label = label, pinned = pinned, hidden = hidden)

    // MARK: - Les neuf, dans l'ordre

    @Test
    fun `les neuf entrees de la reference ouvrent le menu, dans son ordre`() {
        val actions = botMenuActions(bot, isOpen = false, unread = false, hasOrder = false, hasRecent = true)
        assertEquals("les neuf premières sont celles de la référence", nine, actions.take(9))
        // Et celles de la maison suivent, sans qu'aucune n'ait bougé de place relative.
        assertEquals(
            listOf(
                BotMenuAction.MarkUnread,
                BotMenuAction.Voice,
                BotMenuAction.Mute,
                BotMenuAction.ChangeFace,
                BotMenuAction.Reorder,
            ),
            actions.drop(9),
        )
    }

    @Test
    fun `le quatrieme mot porte le nom du bot`() {
        // En anglais d'abord : c'est le mot de la source, et le nom du bot y entre.
        OnyxLanguage.apply(OnyxLanguage.ENGLISH)
        try {
            val labels = nine.map { botMenuLabel(it, frame(label = "Kilo")) }
            assertEquals(
                listOf(
                    "Pin to top",
                    "Hide",
                    "Open recent session",
                    // Le nom du bot est dans le libellé, comme à la source (`BotsRoster.kt:448`).
                    "New chat with Kilo",
                    "Routines",
                    "Start fresh",
                    "Edit",
                    "Duplicate",
                    "Delete",
                ),
                labels,
            )
            assertTrue("le libellé suit le nom du bot", labels[3].contains("Kilo"))
            assertFalse("aucun « X » générique", labels[3].contains("<"))
            // Les quatre mots qui suivent l'état, comme les `if` de la source (`:445-446`).
            assertEquals("Unpin", botMenuLabel(BotMenuAction.Pin, frame(pinned = true)))
            assertEquals("Unhide", botMenuLabel(BotMenuAction.Hide, frame(hidden = true)))
        } finally {
            OnyxLanguage.apply(OnyxLanguage.SYSTEM)
        }
    }

    // MARK: - Aucune entrée morte

    @Test
    fun `avec tous les gestes, chaque entree du menu s affiche`() {
        val actions = botMenuActions(bot, isOpen = false, unread = false, hasOrder = true, hasRecent = true)
        val items = menuItems(
            actions = actions,
            frame = frame(),
            muteLines = { listOf(com.onyx.app.ui.design.MenuItem("1 h") { }) },
            onDismiss = {},
            onMarkRead = {},
            onMarkUnread = {},
            onVoice = {},
            onRoutines = {},
            onRemoveFromOnyx = {},
            onEdit = {},
            onChangeFace = {},
            onHide = {},
            onSetPinned = {},
            onOpenRecent = {},
            onNewChat = {},
            onStartFresh = {},
            onDuplicate = {},
            onDelete = {},
            onReorder = {},
            onSortByRecent = {},
        )
        assertEquals("une ligne par entrée : aucune ne manque, aucune ne s'ajoute", actions.size, items.size)
        assertEquals(nine.map { botMenuLabel(it, frame()) }, items.take(9).map { it.label })
        // Chaque ligne des neuf porte son icône, et la ligne « Supprimer » est destructrice.
        assertTrue("les neuf ont leur icône", items.take(9).all { it.symbol != null })
        assertTrue("« Supprimer » est en rouge, comme le destructeur du modèle", items[8].destructive)
        assertFalse("« Modifier » ne l'est pas", items[6].destructive)
    }

    @Test
    fun `un geste absent retire son entree, jamais une entree morte`() {
        val actions = botMenuActions(bot, isOpen = false, unread = false, hasOrder = false, hasRecent = true)
        val withoutGestures = menuItems(
            actions = actions,
            frame = frame(),
            muteLines = { emptyList() },
            onDismiss = {},
            onMarkUnread = {},
        )
        assertEquals(
            "seule l'entrée dont l'écran fournit le geste s'affiche (le silence est un sous-menu, il est toujours là)",
            listOf("Mark as Unread", "Mute"),
            withoutGestures.map { it.label },
        )
        // Et les entrées que le serveur ne peut pas servir ne sont même pas dans la liste d'actions.
        val api = botMenuActions(BotProfile(name = "api-bot", viaAPI = true), isOpen = false, unread = false, hasOrder = false)
        assertFalse(api.contains(BotMenuAction.Routines))
        assertFalse(api.contains(BotMenuAction.Duplicate))
        assertFalse(api.contains(BotMenuAction.Delete))
        assertFalse(api.contains(BotMenuAction.EditBot))
        val withoutChat = botMenuActions(BotProfile(name = "neuf", setUp = true), isOpen = false, unread = false, hasOrder = false)
        assertFalse("rien à archiver : pas de « Repartir à zéro »", withoutChat.contains(BotMenuAction.StartFresh))
        assertFalse(
            "aucune autre session : pas d'« Ouvrir la dernière session »",
            withoutChat.contains(BotMenuAction.OpenRecent),
        )
    }

    // MARK: - Les icônes de la source

    @Test
    fun `les icones des neuf sont celles de la reference`() {
        assertEquals(LucideMenuIcons.Pin, botMenuSymbol(BotMenuAction.Pin))
        assertEquals(LucideMenuIcons.PinOff, botMenuSymbol(BotMenuAction.Pin, frame(pinned = true)))
        assertEquals(LucideMenuIcons.EyeOff, botMenuSymbol(BotMenuAction.Hide))
        assertEquals(LucideMenuIcons.Eye, botMenuSymbol(BotMenuAction.Hide, frame(hidden = true)))
        assertEquals(LucideMenuIcons.History, botMenuSymbol(BotMenuAction.OpenRecent))
        assertEquals(LucideMenuIcons.MessageSquarePlus, botMenuSymbol(BotMenuAction.NewChat))
        assertEquals(LucideMenuIcons.CalendarClock, botMenuSymbol(BotMenuAction.Routines))
        assertEquals(LucideMenuIcons.RotateCcw, botMenuSymbol(BotMenuAction.StartFresh))
        assertEquals(LucideMenuIcons.Pencil, botMenuSymbol(BotMenuAction.EditBot))
        assertEquals(LucideMenuIcons.Copy, botMenuSymbol(BotMenuAction.Duplicate))
        assertEquals(LucideMenuIcons.Trash2, botMenuSymbol(BotMenuAction.Delete))
        // Les entrées de la maison gardent les symboles de la maison.
        assertEquals(com.onyx.app.ui.design.Symbols.Clock, botMenuSymbol(BotMenuAction.SortByRecent))
        assertEquals(com.onyx.app.ui.design.Symbols.EnvelopeOpen, botMenuSymbol(BotMenuAction.MarkRead))
        // Le menu ne nomme **jamais** un dessin de visage ou un glyphe écrit à la main.
        assertFalse("aucune icône en dur dans le menu", menus.contains("R.drawable."))
    }

    @Test
    fun `les icones sont d une seule famille de trait`() {
        // Le jeu de la source : grille **24**, **un trait de 2**, bouts et jointures arrondis, et
        // rien de rempli. Onze fichiers : les neuf entrées, plus l'œil et le crayon à l'état posé.
        val wanted = listOf(
            "ic_lucide_pin", "ic_lucide_pin_off", "ic_lucide_history", "ic_lucide_message_square_plus",
            "ic_lucide_calendar_clock", "ic_lucide_rotate_ccw", "ic_lucide_copy", "ic_lucide_trash_2",
            "ic_lucide_eye", "ic_lucide_eye_off", "ic_lucide_pencil",
        )
        for (name in wanted) {
            val file = File(drawables, "$name.xml")
            assertTrue("$name.xml est là", file.isFile)
            val text = file.readText()
            assertTrue("$name : la grille de 24", text.contains("android:viewportWidth=\"24\""))
            assertTrue("$name : le même carré en dp", text.contains("android:width=\"24dp\""))
            assertTrue("$name : un trait de 2", text.contains("android:strokeWidth=\"2\""))
            assertTrue("$name : des bouts ronds", text.contains("android:strokeLineCap=\"round\""))
            assertTrue("$name : des jointures rondes", text.contains("android:strokeLineJoin=\"round\""))
            assertTrue("$name : au trait, jamais plein", text.contains("android:fillColor=\"#00000000\""))
            // Chaque fichier dit **où** la référence s'en sert.
            assertTrue("$name : la ligne de la source", text.contains("BotsRoster.kt:"))
        }
        // L'objet qui les expose les nomme comme la source, et dit d'où vient la géométrie.
        for (name in listOf("Pin", "PinOff", "Eye", "EyeOff", "History", "MessageSquarePlus", "CalendarClock", "RotateCcw", "Pencil", "Copy", "Trash2")) {
            assertTrue("LucideMenuIcons.$name", icons.contains("val $name = R.drawable."))
        }
        assertTrue("la géométrie est générée, pas redessinée", icons.contains("lucide-react"))
    }

    // MARK: - Épingler : l'ordre, et sa persistance

    @Test
    fun `un bot epingle passe en tete, et le detacher le rend a sa place`() {
        val a = BotProfile(name = "a")
        val b = BotProfile(name = "b", pinned = true)
        val c = BotProfile(name = "c")
        val rooms = emptyList<com.onyx.app.data.model.serve.GroupRoom>()

        // Sans ordre mis à la main : l'épinglé passe devant, les autres gardent leur ordre reçu.
        assertEquals(
            listOf("b", "a", "c"),
            BotListItem.arranged(listOf(a, b, c), rooms, order = emptyList())
                .map { (it as BotListItem.Bot).profile.name },
        )
        // Avec un ordre mis à la main : l'épingle passe **devant lui** — c'est « Pin to top ».
        assertEquals(
            listOf("b", "c", "a"),
            BotListItem.arranged(listOf(a, b, c), rooms, order = listOf("bot:c", "bot:a", "bot:b"))
                .map { (it as BotListItem.Bot).profile.name },
        )
        // Détaché — le même bot, sans son épingle — il retrouve sa place dans l'ordre enregistré,
        // intact : les autres n'ont pas bougé.
        val detached = listOf(a, b.copy(pinned = false), c)
        assertEquals(
            listOf("c", "a", "b"),
            BotListItem.arranged(detached, rooms, order = listOf("bot:c", "bot:a", "bot:b"))
                .map { (it as BotListItem.Bot).profile.name },
        )
    }

    @Test
    fun `l epinglage est garde sur ce telephone, et suivi d une ecriture serveur`() {
        // Le choix du téléphone prime sur le `ui_meta`, comme la visibilité : même plan, même clé.
        assertTrue("la clé est celle du plan des visages", File("../core/src/main/java/com/onyx/app/data/local/RenameStore.kt").readText().contains("fun pinOverride(bot: String): String = \"force_pinned_\$bot\""))
        assertEquals("force_pinned_kilo", FaceKeys.pinOverride("kilo"))
        assertTrue("le magasin l'écrit", viewModel.contains("faces.setPinOverride(bot.name, pinned)"))
        assertTrue("et il la relit en éditant le bot", viewModel.contains("pinned = pinOverride(bot.name) ?: bot.pinned"))
        assertTrue("un renommage la suit", File("../core/src/main/java/com/onyx/app/data/local/ProfileStateMigration.kt").readText().contains("moveBoolean(faces, FaceKeys.pinOverride(from), FaceKeys.pinOverride(to))"))
        // La même écriture part sur le serveur, avec la révision lue — `ui_meta.hermes-bots.pinned`.
        assertTrue(viewModel.contains("ServeBotsRpc(rpc).setPinned(bot.name, pinned = pinned, revision = bot.setUpRevision)"))
        assertTrue(rpc.contains("put(\"pinned\", pinned)"))
        assertTrue("le tableau de bord le lit", File("../core/src/main/java/com/onyx/app/data/net/ServeProfilesApi.kt").readText().contains("pinned = meta?.get(\"pinned\").asBool() ?: false"))
    }

    // MARK: - La carte de bot, alignée sur celle de la référence

    @Test
    fun `la carte est celle de la planche, sans perdre les visages animes`() {
        // Le visage : 48 dp, la taille de la planche (`.botface{width:48px;height:48px}`,
        // `hcss.py:161`).
        assertTrue("le visage de la planche", chatsidebarBots.contains("const val FaceDp = 48"))
        // Le point d'état : 8 dp, en haut à droite (`.pdot{width:8px;height:8px}`, `hcss.py:164`).
        assertTrue(chatsidebarBots.contains("const val DotDp = 8"))
        assertTrue(chatsidebarBots.contains("BotStatusDot(tone = bot.tone, unread = bot.unread, size = BotsGrid.DotDp.dp)"))
        // Deux colonnes, cartes de même hauteur (la hauteur est calculée, jamais celle du contenu).
        assertEquals(2, botGridColumns(411f, 1f))
        assertEquals("une seule colonne à 1,3× de police", 1, botGridColumns(411f, 1.3f))
        assertTrue(chatsidebarBots.contains(".height(cardHeight)"))
        // L'épingle marque la carte, comme la source (`:489`).
        assertTrue("la marque d'épingle sur la carte", chatsidebarBots.contains("if (bot.pinned)"))
        assertTrue(chatsidebarBots.contains("LucideMenuIcons.Pin"))
        // Le sous-titre : l'aperçu, la phrase, « Say hello » — jamais un vide sous le nom.
        assertTrue(host.contains("?: OnyxLocalization[\"Say hello\"]"))
        // **Les visages animés restent** : la carte dessine le visage vivant, pas une image fixe.
        assertTrue(chatsidebarBots.contains("BotAvatar(name = bot.name, faceCode = faceCode"))
    }

    // MARK: - Ce que chaque action fait vraiment

    @Test
    fun `chaque action serveur passe par sa route`() {
        // « Dupliquer » : `profiles.create` avec `clone_from`, puis le même geste de `ui_meta`.
        assertTrue(viewModel.contains("cloneFrom = bot.name"))
        assertTrue(viewModel.contains("createBot("))
        assertTrue(rpc.contains("put(\"clone_from\", cloneFrom)"))
        // « Supprimer » : la route REST du profil, jamais un appel socket inventé.
        assertTrue(viewModel.contains("profiles.deleteProfile(bot.name)"))
        // « Repartir à zéro » : le chat du bot est archivé (`PATCH api/sessions/{id}`).
        assertTrue(viewModel.contains("ChatSessionActions(rpc, ServeClient(config)).archive(stored)"))
        assertTrue(File("../core/src/main/java/com/onyx/app/data/net/ChatProjects.kt").readText().contains("path = \"api/sessions/\$stored\""))
        // « Routines » : l'écran des tâches planifiées du bot, `api/cron/jobs`.
        assertTrue(app.contains("botCronProfile = bot.name"))
        assertTrue(app.contains("navTo(Route.BOT_SCHEDULES.name)"))
        // « Supprimer » demande confirmation avant d'agir, dans **les deux** écrans.
        assertTrue("le volet demande", host.contains("title = OnyxLocalization.format(\"Delete %1\\\$s?\", bot.label(mainName))"))
        assertTrue("et l'écran des bots aussi", File(ui, "bots/BotsScreen.kt").readText().contains("\"Delete %1\\\$s?\""))
        // Les deux endroits montrent le **même** menu : une seule table de gestes.
        assertTrue("le volet", app.contains("botMenu = botMenuTable"))
        assertTrue("la page Bots", app.contains("botMenu = botMenuTable"))
        assertEquals("la table est bâtie une fois", 1, app.split("val botMenuTable = BotMenuHandlers(").size - 1)
    }
}
