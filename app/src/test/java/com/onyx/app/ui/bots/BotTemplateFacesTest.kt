package com.onyx.app.ui.bots

import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.BotFace
import com.onyx.app.ui.design.FaceMouth
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les vignettes de « Start from » de l'écran « New Bot ».
 *
 * Le propriétaire y voyait six pastilles **sombres** portant toutes le même petit groupe de barres
 * verticales, alors que l'aperçu du bot juste au-dessus montrait le vrai visage dessiné : la
 * vignette dessinait un **orbe générique** (`OrbFaces.all[orb % size]`, une icône de la famille
 * des orbes) au lieu de la créature de son modèle, et le catalogue ne portait le visage d'aucun
 * modèle. Ces tests tiennent les deux bouts : chaque modèle a **son** visage, la vignette passe par
 * le **même moteur** que l'aperçu, et ce visage part avec la création du bot.
 */
class BotTemplateFacesTest {

    private val editor = File("src/main/java/com/onyx/app/ui/bots/BotEditorScreen.kt").readText()
    private val viewModel = File("src/main/java/com/onyx/app/ui/bots/BotsViewModel.kt").readText()
    private val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()
    private val rpc = File("../core/src/main/java/com/onyx/app/data/net/ServeBotsRpc.kt").readText()

    private val tile = editor.substringAfter("private fun TemplateTile(").substringBefore("/**\n * Les jeux d'outils")

    // MARK: - Le catalogue : un visage par modèle

    @Test
    fun `chaque modele porte son propre visage, et Blank n'en a pas`() {
        val models = BotTemplateSpec.all
        assertEquals("les cinq modèles de la référence", 5, models.size)
        models.forEach { spec ->
            val face = spec.face
            assertNotNull("${spec.title} n'a pas de visage", face)
            assertTrue("${spec.title} : sa forme, ses yeux, sa bouche et sa marque", face!!.code().startsWith("1:"))
        }
        assertNull("« Blank » est le modèle sans visage", BotTemplateSpec.blank.face)
    }

    @Test
    fun `deux modeles ne montrent pas la meme creature`() {
        val faces: List<BotFace> = BotTemplateSpec.all.mapNotNull { it.face }
        assertEquals("aucun visage répété", faces.size, faces.toSet().size)
        // La forme et la couleur disent le modèle d'un coup d'œil : aucune n'est partagée.
        assertEquals(faces.size, faces.map { it.shape }.toSet().size)
        assertEquals(faces.size, faces.map { it.colour }.toSet().size)
    }

    @Test
    fun `chaque modele dit ce qu'il fait par son expression`() {
        val byId = BotTemplateSpec.all.associateBy { it.id }
        // La chercheuse a l'œil étincelant et l'antenne qui capte.
        assertEquals("sparkle", byId.getValue("researcher").face!!.code().split(':')[2])
        assertTrue(byId.getValue("researcher").face!!.code().contains(":antenna:"))
        // L'écrivain sourit ; le codeur garde la bouche fermée ; la maison a la bouche ronde.
        assertEquals(FaceMouth.SMILE, byId.getValue("writer").face!!.mouth)
        assertEquals(FaceMouth.NONE, byId.getValue("coder").face!!.mouth)
        assertEquals(FaceMouth.OH, byId.getValue("home").face!!.mouth)
    }

    // MARK: - La vignette : le même moteur que l'aperçu, animé

    @Test
    fun `la vignette dessine le visage du modele, jamais un orbe`() {
        assertTrue("le visage passe par le moteur du dessin", tile.contains("BotAvatar("))
        assertTrue("avec le code de son modèle", tile.contains("faceCode = face.code()"))
        assertFalse("plus d'orbe générique", tile.contains("OrbAvatar("))
        assertFalse("plus de catalogue d'orbes", tile.contains("OrbFaces"))
        assertFalse("plus d'index d'orbe dans le catalogue", editor.contains("orb = "))
    }

    @Test
    fun `le modele sans visage garde la tuile vide`() {
        assertTrue("« Blank » est le « + »", tile.contains("if (face != null)"))
        assertTrue(tile.contains("OnyxIcon(symbol = Symbols.Plus"))
        assertTrue("et le catalogue le dit", editor.contains("label = \"Blank\","))
    }

    @Test
    fun `le visage respire au repos et reagit a l'appui`() {
        assertTrue(
            "au repos il respire, à l'appui il se tourne vers vous",
            tile.contains("phase = if (held) VoicePhase.LISTENING else VoicePhase.IDLE"),
        )
        assertTrue("l'animation est celle qui existe déjà pour ces visages", tile.contains("voicePose("))
        assertTrue("et sa bouche reste la sienne", tile.contains(".copy(mouth = null)"))
        assertTrue("l'appui relève les sourcils, lisibles à la taille d'une vignette", tile.contains("brow = 0.95f"))
        assertTrue("l'appui vient de la source d'appui de la tuile", tile.contains("val press = remember { MutableInteractionSource() }"))
        assertTrue(tile.contains("val held by press.collectIsPressedAsState()"))
        assertTrue("et la tuile la partage avec son geste", tile.contains("interaction = press"))
        // L'animation n'est pas réécrite ici : c'est celle de la scène vocale, et le même visage
        // respire au repos (`voicePose` : « la respiration, un cycle d'environ quatre secondes »).
        val voiceFace = File("src/main/java/com/onyx/app/ui/voice/VoiceFace.kt").readText()
        assertTrue("la respiration est celle qui existe déjà", voiceFace.contains("val breath = 0.5f * sin(seconds * 1.5f)"))
        assertTrue("et sous Reduce Motion, aucun instant ne passe", voiceFace.contains("val pose = if (reduceMotion)"))
        assertTrue(voiceFace.contains("BotPose()"))
    }

    @Test
    fun `le visage est fige quand les animations sont reduites`() {
        assertTrue("l'horloge du visage", tile.contains("val seconds = rememberBotSeconds()"))
        assertTrue("sans instant, la pose de repos", tile.contains("} ?: BotPose()"))
        val motion = File("../core/src/main/java/com/onyx/app/ui/design/BotAvatar.kt").readText()
        assertTrue(
            "et cette horloge ne tourne pas sous « Réduire les animations »",
            motion.contains("if (LocalReduceMotion.current) return null"),
        )
    }

    // MARK: - Le visage choisi part avec la création

    @Test
    fun `le visage du modele est celui de l'apercu et de la creation`() {
        assertTrue(
            "l'aperçu du haut montre le visage du modèle choisi",
            editor.contains("faceCode = pickedFace?.code() ?: faceCode"),
        )
        assertTrue("le choix du modèle retient son visage", editor.contains("pickedFace = spec.face"))
        assertTrue("et « Blank » le remet à rien", editor.contains("pickedFace = null"))
        assertTrue(
            "« Create » l'envoie au serveur",
            editor.contains("onCreate(title, about, spec?.soul.orEmpty(), shown, pickedFace?.code())"),
        )
        assertTrue("la création est branchée", app.contains("botsViewModel.createBot("))
        assertTrue("avec le visage du modèle", app.contains("faceCode = faceCode,"))
        assertTrue("le profil naît sur le serveur", rpc.contains("rpc.call(\n            \"profiles.create\","))
        assertTrue("et son visage se pose avec lui", rpc.contains("put(\"onyx\", faceCode?.let { code -> buildJsonObject { put(\"face\", code) } } ?: JsonNull)"))
        assertTrue("le visage reste aussi sur ce téléphone", viewModel.contains("faces.setFace(profile, faceCode)"))
    }

    @Test
    fun `le refus du serveur est dit en francais`() {
        assertEquals(
            "Hermes n'a pas créé le bot.",
            OnyxLocalization.translate("Hermes did not make the bot.", Locale.FRENCH),
        )
    }

    @Test
    fun `aucun texte visible ne nomme la plateforme d'origine`() {
        // La règle du lot : ce que la personne lit ne dit jamais d'où vient l'app — les écrans
        // parlent de « la référence ». Ce contrôle-là ne regarde que les textes affichables.
        val literal = Regex("\"([^\"\\\\]|\\\\.)*\"")
        listOf(
            "BotEditorScreen.kt" to editor,
            "BotsViewModel.kt" to viewModel,
            "ServeBotsRpc.kt" to rpc,
        ).forEach { (name, source) ->
            val texts = literal.findAll(source).map { it.value }
            listOf("i" + "OS", "i" + "Phone", "i" + "Pad", "Ap" + "ple", "mac" + "OS", "S" + "iri").forEach { word ->
                assertTrue(
                    "« $word » ne doit apparaître dans aucun texte visible de $name",
                    texts.none { it.contains(word) },
                )
            }
        }
    }

    // MARK: - La poignée du bot neuf

    @Test
    fun `la poignee du bot neuf suit le nom tape`() {
        assertEquals("researcher", botHandle("  Researcher "))
        assertEquals("my_helper", botHandle("My Helper"))
        assertEquals("", botHandle("   "))
    }
}
