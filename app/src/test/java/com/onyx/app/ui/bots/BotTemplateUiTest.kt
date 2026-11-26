package com.onyx.app.ui.bots

import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.localization.OnyxLocalization
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
 * L'**interface** du partage de modèle : l'entrée « Partager comme modèle » dans le menu d'un bot,
 * la feuille qui donne le lien (et son QR), et l'arrivée d'un lien chez quelqu'un d'autre — aperçu
 * obligatoire, puis ajout réel par les routes de l'éditeur de bot.
 *
 * Le codec et l'aperçu ont déjà leurs tests (`BotTemplateShareTest`) ; ceux-ci tiennent ce qui se
 * voit et ce qui s'écrit : l'entrée est dans le menu **à sa place**, la feuille montre le lien, un
 * lien invalide est **refusé avec sa phrase**, et l'aperçu **nomme les absences** avant le bouton
 * « Ajouter ».
 */
class BotTemplateUiTest {
    private val root = File("src/main/java/com/onyx/app/ui")
    private val menus = File(root, "bots/BotMenus.kt").readText()
    private val sheets = File(root, "bots/BotTemplateSheets.kt").readText()
    private val importLogic = File(root, "bots/BotTemplateImport.kt").readText()
    private val viewModel = File(root, "bots/BotsViewModel.kt").readText()
    private val app = File(root, "OnyxApp.kt").readText()
    private val mainActivity = File("src/main/java/com/onyx/app/MainActivity.kt").readText()
    private val inbox = File("src/main/java/com/onyx/app/intake/ShareInbox.kt").readText()
    private val manifest = File("src/main/AndroidManifest.xml").readText()
    private val appGradle = File("build.gradle.kts").readText()

    private val kilo = BotProfile(name = "kilo", title = "Kilo", chatId = "bot-chat")

    // MARK: - L'entrée du menu, à sa place

    @Test
    fun `l entree Partager comme modele ferme le menu d un bot`() {
        val menu = botMenuActions(kilo, isOpen = false, unread = false, hasOrder = true, hasRecent = true)

        assertEquals("elle est la dernière", BotMenuAction.ShareTemplate, menu.last())
        assertEquals(1, menu.count { it == BotMenuAction.ShareTemplate })
        // Après **toutes** celles de la maison : le tri par récent, quand il est là, passe avant.
        assertTrue(
            "elle vient après « Sort Bots by Recent »",
            menu.indexOf(BotMenuAction.SortByRecent) < menu.indexOf(BotMenuAction.ShareTemplate),
        )
    }

    @Test
    fun `l entree porte son mot et son symbole`() {
        assertEquals("Share as Template…", botMenuLabel(BotMenuAction.ShareTemplate))
        assertEquals("Partager comme modèle…", OnyxLocalization.translate("Share as Template…", Locale.FRANCE))
        val symbol = botMenuSymbol(BotMenuAction.ShareTemplate)
        assertNotNull("l'entrée a son symbole", symbol)
        assertEquals(com.onyx.app.ui.design.Symbols.Share, symbol)
        // La ligne existe : le `when` du menu est exhaustif, une entrée sans geste ne s'affiche pas.
        assertTrue(menus.contains("BotMenuAction.ShareTemplate -> onShareTemplate"))
    }

    @Test
    fun `un bot sans profil sur le Dashboard ne se partage pas`() {
        // Un bot ajouté à la main n'a ni `SOUL.md` ni jeux d'outils côté serveur, et le profil
        // principal est l'agent de la personne : ni l'un ni l'autre n'est un modèle à donner.
        assertFalse(
            botMenuActions(BotProfile(name = "api-bot", viaAPI = true), isOpen = false, unread = false, hasOrder = false)
                .contains(BotMenuAction.ShareTemplate),
        )
        assertFalse(
            botMenuActions(BotProfile(name = "default", isDefault = true), isOpen = false, unread = false, hasOrder = false)
                .contains(BotMenuAction.ShareTemplate),
        )
        assertTrue(
            "le geste est porté par la table du menu, donc par les deux écrans",
            menus.contains("onShareTemplate = handlers.onShareTemplate?.let { act -> { act(bot) } }"),
        )
        assertTrue("l'app le branche", app.contains("onShareTemplate = { bot ->"))
    }

    // MARK: - La feuille du partage : le lien, la copie, le QR

    @Test
    fun `la feuille montre le lien en clair et le copie`() {
        assertTrue("le lien est montré tel quel", sheets.contains("text = link"))
        assertTrue("un bouton le copie", sheets.contains("\"Copied\" else \"Copy link\""))
        assertTrue("la copie passe par le presse-papiers", sheets.contains("nativeClipboard.setPrimaryClip("))
        assertTrue("elle n'invente pas un lien partiel", sheets.contains("else -> BasicText("))
        assertTrue("la feuille dit d'où vient le lien", sheets.contains("title = \"Share as Template…\""))
        assertTrue("et l'écran l'ouvre sur le bot touché", app.contains("BotTemplateShareSheet("))
    }

    @Test
    fun `le QR n est dessine que par une bibliotheque deja presente`() {
        // Le dépôt embarque ZXing (`zxing-android-embedded`), qui **lit** les codes — il sait aussi
        // en écrire, et `QRCodeWriter` est déjà utilisé par le code de connexion. Aucune dépendance
        // n'est ajoutée : la feuille s'en sert, et se rabat sur le lien seul si le dessin échoue.
        assertTrue(
            "zxing est déjà une dépendance de l'app",
            appGradle.contains("implementation(libs.zxing.embedded)"),
        )
        assertTrue("aucune bibliothèque nouvelle", !appGradle.contains("zxing-core") && !appGradle.contains("qrcode-generator"))
        assertTrue("la feuille encode le lien avec ZXing", sheets.contains("QRCodeWriter().encode(link, BarcodeFormat.QR_CODE"))
        assertTrue("un échec de dessin laisse le lien", sheets.contains(".getOrNull()"))
        assertTrue("et la feuille le dit", sheets.contains("\"The code couldn't be drawn.\""))
    }

    @Test
    fun `le QR de la feuille encode vraiment le lien`() {
        // Pas une promesse : le même `QRCodeWriter` que la feuille, relu par le lecteur ZXing que le
        // dépôt utilise déjà pour scanner. Le code rend **exactement** le lien partagé.
        val link = BotTemplateShare.of(BotTemplateSpec.all.first { it.id == "coder" }).link
        val size = 512
        val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(link, com.google.zxing.BarcodeFormat.QR_CODE, size, size)
        val pixels = IntArray(size * size) { index ->
            val x = index % size
            val y = index / size
            // Noir opaque ou blanc opaque, en ARGB : rien d'Android dans un test JVM.
            if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val decoded = com.google.zxing.qrcode.QRCodeReader().decode(
            com.google.zxing.BinaryBitmap(
                com.google.zxing.common.HybridBinarizer(
                    com.google.zxing.RGBLuminanceSource(size, size, pixels),
                ),
            ),
        ).text

        assertEquals(link, decoded)
        assertTrue("le code reste un des liens d'Onyx", decoded.startsWith("onyx://bot?v=1&t="))
    }

    @Test
    fun `la feuille dit si le prompt part avec le lien`() {
        assertTrue(sheets.contains("\"This bot has no SOUL.md: the link carries no prompt.\""))
        assertTrue(sheets.contains("\"The prompt (SOUL.md) goes with the link.\""))
        assertTrue("le ViewModel lit le SOUL du profil", viewModel.contains("ServeFilesApi(ServeClient(config)).soul(bot.name)"))
        assertTrue("et les outils allumés du profil", viewModel.contains("describeToolsets(bot.name).enabled"))
        assertTrue("et ses compétences allumées", viewModel.contains("ServeSkillsApi(ServeClient(config)).skills(bot.name)"))
    }

    // MARK: - Ce que le partage porte, et ce qu'il ne porte pas

    @Test
    fun `le modele partage reprend ce que le serveur sait du bot`() {
        // Le visage d'un modèle du catalogue, tel que le serveur l'écrit (`1:forme:…`).
        val face = BotTemplateSpec.all.first { it.id == "researcher" }.face
        assertNotNull(face)
        val bot = BotProfile(
            name = "kilo",
            title = "Kilo",
            description = "Tient le catalogue.",
            model = "hermes-4",
            faceCode = face!!.code(),
        )

        val share = sharedTemplateOf(
            bot = bot,
            label = "Kilo",
            soul = "# Kilo\n\nTu tiens le catalogue.",
            tools = listOf("web", "files"),
            skills = listOf("arxiv"),
        )

        assertEquals("kilo", share.spec.id)
        assertEquals("Kilo", share.spec.title)
        assertEquals("Tient le catalogue.", share.spec.about)
        assertEquals("# Kilo\n\nTu tiens le catalogue.", share.spec.soul)
        assertEquals(face, share.spec.face)
        assertEquals(listOf("web", "files"), share.tools)
        assertEquals(listOf("arxiv"), share.skills)
        assertEquals("hermes-4", share.model)

        // Le lien fait l'aller-retour avec **tout** cela : c'est ce que le destinataire recevra.
        val decoded = BotTemplateShareCodec.decode(share.link)
        assertEquals(share, decoded)
        assertEquals(face, decoded.spec.face)
    }

    @Test
    fun `un bot sans SOUL part sans prompt, et le sait`() {
        val share = sharedTemplateOf(bot = BotProfile(name = "muet", title = "Muet"), label = "Muet", soul = null)

        assertTrue(share.spec.soul.isEmpty())
        assertTrue("la feuille le dira", share.spec.soul.isBlank())
    }

    @Test
    fun `le profil du bot importe prend un nom libre`() {
        assertEquals("researcher", templateHandle("Researcher", emptySet()))
        assertEquals("researcher_2", templateHandle("Researcher 2", emptySet()))
        // Deux imports du même modèle ne s'écrasent pas : le second prend un suffixe.
        assertEquals("researcher-2", templateHandle("Researcher", setOf("researcher")))
        assertEquals("researcher-3", templateHandle("Researcher", setOf("researcher", "researcher-2")))
        // Un titre vide ou qui ne garde aucune lettre ne fait pas un profil sans nom.
        assertEquals("bot", templateHandle("   ", emptySet()))
        assertEquals("bot", templateHandle("—", emptySet()))
        assertTrue("le nom reste dans les bornes du serveur", templateHandle("a".repeat(200), emptySet()).length <= 64)
        assertNotEquals(
            "deux titres différents ne donnent pas le même profil",
            templateHandle("Scout", emptySet()),
            templateHandle("Scout Two", emptySet()),
        )
    }

    // MARK: - L'arrivée d'un lien : l'aperçu d'abord, l'ajout ensuite

    @Test
    fun `l apercu nomme les absences avant d ajouter`() {
        val link = BotTemplateShare(
            spec = BotTemplateSpec.all.first { it.id == "researcher" },
            tools = listOf("web"),
            skills = listOf("arxiv"),
            model = "hermes-4",
        ).link

        val share = BotTemplateShareCodec.decode(link)
        val preview = share.preview()

        // Ce qui est là.
        assertEquals("Researcher", preview.name)
        assertEquals(listOf("web"), preview.tools)
        assertEquals(listOf("arxiv"), preview.skills)
        assertEquals("hermes-4", preview.model)

        // Ce qui n'y est pas : les six absences, nommées, et la feuille les peint toutes.
        assertEquals(6, preview.omissions.size)
        val lines = preview.omissionLabels(Locale.FRANCE)
        assertEquals(6, lines.size)
        lines.forEach { line -> assertTrue("chaque absence s'annonce : $line", line.startsWith("Pas dans le lien")) }
        assertTrue("la clé et le serveur sont dits", lines[0].contains("clé API") && lines[0].contains("adresse du serveur"))
        assertTrue("les conversations aussi", lines[1].contains("conversations"))
        assertTrue("les fichiers aussi", lines[5].contains("fichiers"))

        assertTrue("la feuille les rend", sheets.contains("preview.omissionLabels()"))
        assertTrue("sous leur titre", sheets.contains("What the link doesn't carry"))
        assertTrue("elle montre le nom et le rôle", sheets.contains("text = preview.name") && sheets.contains("text = preview.role"))
        assertTrue("et les outils, les compétences et le modèle", importLogic.contains("previewLines("))
        // Rien ne part au serveur depuis l'aperçu : le geste est le bouton.
        assertTrue(sheets.contains("onClick = { if (!busy) onAdd(share) }"))
    }

    @Test
    fun `un lien invalide est refuse avec sa phrase, sans rien ajouter`() {
        val refused = BotTemplateShareCodec.decodeOrNull("bonjour").exceptionOrNull()

        assertTrue(refused is BotTemplateShareException)
        assertEquals(BotTemplateShareError.NOT_A_TEMPLATE, (refused as BotTemplateShareException).reason)
        assertEquals(
            "Ce n’est pas un lien de modèle de bot Onyx.",
            OnyxLocalization.translate(refused.reason.key, Locale.FRANCE),
        )

        // Un lien qui porte une clé est refusé de la même façon, et l'écran dit **pourquoi**.
        val withKey = BotTemplateShareCodec
            .decodeOrNull("onyx://bot?v=1&t=" + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("""{"v":1,"n":"Scout","apiKey":"abc"}""".toByteArray(Charsets.UTF_8)))
            .exceptionOrNull() as? BotTemplateShareException
        assertNotNull("une clé est refusée", withKey)
        assertEquals(BotTemplateShareError.SECRET, withKey!!.reason)
        assertTrue("le refus nomme la clé", withKey.reason.message(withKey.detail).contains("apiKey"))

        // Et la feuille ne montre pas de bouton « Add » sur un lien refusé.
        assertTrue(
            "la feuille s'arrête sur la phrase du refus",
            sheets.contains("refused.reason.message(refused.detail)"),
        )
    }

    @Test
    fun `l ajout ecrit un vrai profil par les routes de l editeur`() {
        // Le profil naît par `profiles.create` (le même appel que « New Bot »), puis ses outils, ses
        // compétences et son modèle — jamais une promesse d'écran.
        assertTrue(viewModel.contains("bots.createBot("))
        assertTrue("le lien est copié de l'agent principal", viewModel.contains("cloneFrom = sourceBots.firstOrNull { it.isDefault }?.name"))
        assertTrue("les jeux d'outils partent", viewModel.contains("bots.setToolsets(profile, share.tools)"))
        assertTrue("les compétences aussi", viewModel.contains("skills.toggle(name, enabled = true, profile = profile)"))
        assertTrue("le modèle par défaut aussi", viewModel.contains("bots.setModel(profile, share.model)"))
        assertTrue("le visage est gardé sur l'appareil", viewModel.contains("faces.setFace(profile, faceCode)"))
        assertTrue("et la liste est relue", viewModel.contains("refresh()"))
        // Ce qui n'a pas pu être écrit se dit, au lieu de passer pour un ajout complet.
        assertTrue(viewModel.contains("notApplied += OnyxLocalization[\"the tool sets\"]"))
        assertTrue(viewModel.contains("reportPartialImport("))
        assertTrue(app.contains("botsViewModel.reportPartialImport(outcome)"))
        assertTrue(OnyxLocalization.translate("the tool sets", Locale.FRANCE).contains("outils"))
    }

    // MARK: - L'arrivée du lien, depuis l'extérieur

    @Test
    fun `un lien ouvert depuis une autre app arrive dans l apercu, jamais dans le composeur`() {
        assertTrue("le schéma est déclaré", manifest.contains("<data android:scheme=\"onyx\" android:host=\"bot\" />"))
        assertTrue("la boîte reçoit le lien", inbox.contains("fun offerTemplate(link: String?)"))
        assertTrue("et le passe à l'UI", inbox.contains("val pendingTemplate: StateFlow<String?>"))
        assertTrue("un lien ouvert est reconnu", mainActivity.contains("uri.host.equals(\"bot\", ignoreCase = true)"))
        assertTrue("un lien partagé aussi", mainActivity.contains("if (isTemplateLink(text))"))
        assertTrue("l'aperçu s'ouvre", app.contains("importLink = pendingTemplate"))
        assertTrue("la feuille d'arrivée est montrée", app.contains("BotTemplateImportSheet("))
        // Une fois consommé, le lien ne revient pas : l'aperçu ne se rouvre pas à chaque recomposition.
        assertTrue("le lien est consommé", inbox.contains("_pendingTemplate.value = null"))
    }
}
