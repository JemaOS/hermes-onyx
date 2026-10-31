package com.onyx.app.shortcuts

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le menu qui s'ouvre sur un appui long sur l'icône, côté branchement : les trois tuiles d'action
 * restent, la section des bots s'ajoute, et elle suit la liste réelle des bots.
 *
 * Ces essais lisent les fichiers — c'est le contrat entre l'activité, le dépôt de la demande, la
 * section des bots et le manifeste. Ce qui se voit à l'écran est prouvé à l'émulateur
 * (`dumpsys shortcut` et la capture du menu).
 */
class LongPressMenuTest {

    private val shortcuts = File("src/main/res/xml/shortcuts.xml").readText()
    private val manifest = File("src/main/AndroidManifest.xml").readText()
    private val activity = File("src/main/java/com/onyx/app/MainActivity.kt").readText()
    private val publisher = File("src/main/java/com/onyx/app/shortcuts/RosterShortcuts.kt").readText()
    private val inbox = File("src/main/java/com/onyx/app/shortcuts/BotShortcutInbox.kt").readText()
    private val bots = File("src/main/java/com/onyx/app/ui/bots/BotsViewModel.kt").readText()
    private val chat = File("src/main/java/com/onyx/app/ui/chat/ChatViewModel.kt").readText()

    private fun tile(name: String): String =
        File("../core/src/main/res/drawable/ic_shortcut_$name.xml").readText()

    // MARK: - Les trois tuiles d'action restent

    @Test
    fun `les trois raccourcis d action sont toujours la, rien n a ete retire`() {
        listOf("ask", "talk", "note").forEach { id ->
            assertTrue("le raccourci $id existe", shortcuts.contains("android:shortcutId=\"$id\""))
            assertTrue("le raccourci $id est actif", shortcuts.contains("android:enabled=\"true\""))
        }
        assertTrue(shortcuts.contains("android:action=\"com.onyx.app.action.ASK\""))
        assertTrue(shortcuts.contains("android:action=\"com.onyx.app.action.VOICE\""))
        assertTrue(shortcuts.contains("android:action=\"com.onyx.app.action.CAPTURE\""))
        assertTrue(shortcuts.contains("@string/shortcut_ask_short"))
        assertTrue(shortcuts.contains("@string/shortcut_talk_short"))
        assertTrue(shortcuts.contains("@string/shortcut_note_short"))
    }

    @Test
    fun `chaque raccourci d action porte sa tuile et non un glyphe nu`() {
        assertTrue(shortcuts.contains("android:icon=\"@drawable/ic_shortcut_ask\""))
        assertTrue(shortcuts.contains("android:icon=\"@drawable/ic_shortcut_talk\""))
        assertTrue(shortcuts.contains("android:icon=\"@drawable/ic_shortcut_note\""))
        assertFalse("aucun glyphe monochrome ne reste", shortcuts.contains("@drawable/ic_sym_"))
    }

    @Test
    fun `chaque tuile est un carre a coins arrondis de la famille de l accent`() {
        val colours = listOf("ask", "talk", "note").map { name ->
            val xml = tile(name)
            assertTrue(
                "la tuile $name est un carré à coins arrondis",
                xml.contains("M24,0 H84 A24,24 0 0 1 108,24 V84 A24,24 0 0 1 84,108 H24 A24,24 0 0 1 0,84 V24 A24,24 0 0 1 24,0 Z"),
            )
            assertTrue("le glyphe de $name est blanc", xml.contains("#FFFFFFFF"))
            val fill = Regex("#FF[0-9A-F]{6}").findAll(xml).map { it.value }.first { it != "#FFFFFFFF" }
            fill
        }
        // Une seule famille : le bleu de l'accent et ses deux pas, un rôle chacun.
        assertTrue(colours.contains("#FF2F6BF5"))
        assertTrue(colours.contains("#FF1F4FC4"))
        assertTrue(colours.contains("#FF5B8CFF"))
        assertTrue("trois pas distincts", colours.distinct().size == 3)
    }

    @Test
    fun `les tuiles disent ce qu elles font d un masque circulaire`() {
        listOf("ask", "talk", "note").forEach { name ->
            assertTrue(
                "la tuile $name dit son disque de sûreté",
                tile(name).contains("masque circulaire") && tile(name).contains("disque de sûreté"),
            )
        }
    }

    @Test
    fun `les trois raccourcis d action sont declares, donc toujours presents`() {
        // Déclarés dans le manifeste : ils ne dépendent d'aucune lecture du serveur.
        assertTrue(shortcuts.contains("android:shortcutId=\"ask\""))
        assertTrue(
            "le meta-data est sur les alias du lanceur, là où le menu le lit",
            manifest.contains("android:name=\"android.app.shortcuts\""),
        )
        assertTrue(manifest.contains("android:resource=\"@xml/shortcuts\""))
    }

    // MARK: - La section des bots

    @Test
    fun `un raccourci de bot ouvre une conversation avec ce bot`() {
        assertTrue("la demande est déposée par l'activité", activity.contains("BotShortcutInbox.request("))
        assertTrue("elle porte le bot visé", activity.contains("bot = bot"))
        assertTrue("elle porte son chat nommé", activity.contains("chatId = intent.getStringExtra(RosterShortcuts.EXTRA_BOT_CHAT)"))
        assertTrue("l'action est celle de l'activité", activity.contains("ACTION_BOT -> {"))
        assertTrue("le manifeste accepte l'action", manifest.contains("<action android:name=\"\${onyxNamespace}.action.BOT\" />"))
        assertTrue("le chat du bot s'ouvre par le même chemin que sa tuile", chat.contains("botChatToOpen("))
        assertTrue("et la conversation trouvée s'ouvre", chat.contains("open(chat)"))
        assertTrue("une demande est consommée une fois", chat.contains("BotShortcutInbox.consume()"))
        assertTrue("le dépôt garde une seule demande", inbox.contains("MutableStateFlow<BotShortcutRequest?>(null)"))
    }

    @Test
    fun `la tuile d un bot porte sa couleur et ouvre son intention`() {
        assertTrue("le raccourci est bâti sur le bot", publisher.contains("BotTileArtwork.botTile("))
        assertTrue(
            "sa couleur vient du visage résolu du roster",
            publisher.contains("val faceCode = shortcut.faceCode ?:"),
        )
        assertTrue("et sa photo quand elle est là", publisher.contains("BotTileArtwork.photoTile("))
        assertTrue("l'initiale quand elle n'y est pas", publisher.contains("label = shortcut.label"))
        assertTrue("l'intention porte l'action", publisher.contains("action = ACTION_BOT"))
        assertTrue("et la conversation demandée", publisher.contains("putExtra(EXTRA_BOT_LABEL, shortcut.label)"))
    }

    @Test
    fun `la section suit la liste reelle des bots`() {
        assertTrue("la section est republiée à chaque lecture du roster", bots.contains("republishShortcuts()"))
        assertTrue("depuis la liste réelle", bots.contains("bots = state.bots"))
        assertTrue("dans l'ordre enregistré", bots.contains("order = state.order"))
        assertTrue("et avec les photos du roster", bots.contains("pictures = _pictures.value"))
        assertTrue("la republier est sans effet si le dépôt n'est pas installé", bots.contains("AppContainer.isInstalled"))
    }

    @Test
    fun `l ensemble publie est remplace, donc un bot supprime perd son raccourci`() {
        assertTrue(
            "setDynamicShortcuts remplace tout l'ensemble",
            publisher.contains("manager.setDynamicShortcuts(infos)"),
        )
        assertFalse(
            "addDynamicShortcuts laisserait vivre le raccourci d'un bot disparu",
            publisher.contains("addDynamicShortcuts"),
        )
        assertFalse(
            "aucun raccourci n'est épinglé de force",
            publisher.contains("requestPinShortcut"),
        )
    }

    @Test
    fun `le raccourci appartient au composant lanceur, pas a l activite`() {
        assertTrue("le composant est l'alias d'icône actif", publisher.contains("AppIconController("))
        assertTrue("et il est posé sur le raccourci", publisher.contains(".setActivity(component)"))
    }

    @Test
    fun `la place de la plateforme est respectee`() {
        assertTrue("le nombre stocké est lu", publisher.contains("manager.maxShortcutCountPerActivity"))
        assertTrue("la place des trois actions est déduite", publisher.contains("BotShortcutPlan.ACTION_IDS.size"))
        assertTrue("celle des raccourcis épinglés aussi", publisher.contains("manager.pinnedShortcuts.size"))
        assertTrue("rien n'est publié au-delà", publisher.contains("coerceAtLeast(0)"))
        assertTrue("la taille d'icône demandée est respectée", publisher.contains("manager.iconMaxWidth"))
    }

    @Test
    fun `l application ne nomme ni la plateforme ni l autre application`() {
        val mine = listOf(
            shortcuts,
            publisher,
            inbox,
            File("src/main/java/com/onyx/app/shortcuts/BotShortcutPlan.kt").readText(),
            File("../core/src/main/java/com/onyx/app/shortcuts/BotTileArtwork.kt").readText(),
            tile("ask"),
            tile("talk"),
            tile("note"),
        )
        val forbidden = listOf("Gemini", "S" + "iri", "i" + "OS", "i" + "Phone", "red" + "de", "Red" + "de")
        mine.forEach { text ->
            forbidden.forEach { word ->
                assertFalse("« $word » ne s'écrit pas ici", text.contains(word))
            }
            assertTrue("la référence s'écrit « la référence »", text.contains("référence") || text.contains("appui long"))
        }
    }
}
