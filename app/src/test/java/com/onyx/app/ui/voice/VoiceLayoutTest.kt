package com.onyx.app.ui.voice

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceLayoutTest {
    private val source = File("src/main/java/com/onyx/app/ui/voice/VoiceScreen.kt").readText()
    private val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()

    @Test
    fun `les trois commandes du bas reprennent symboles tailles et insets `() {
        assertTrue(source.contains("Symbols.Keyboard"))
        assertTrue(source.contains("Symbols.ArrowCounterclockwise"))
        assertTrue(source.contains("if (phase == VoicePhase.SPEAKING)"))
        assertTrue(source.contains("VoiceRoundButton("))
        assertTrue(source.contains("contentTint = Color.White"))
        // Les deux autres commandes gardent leur glyphe de 20 points; le carré d'arrêt, lui, prend
        // la boîte qui lui donne la taille du modèle (`VoiceStopGlyph`, test du bouton rouge).
        assertTrue(source.contains("OnyxIcon(symbol = symbol, tint = tint, size = 20.dp)"))
        assertTrue(source.contains("iconSize = if (working) VoiceStopGlyph.dp else 20.dp"))
        assertTrue(source.contains("navigationBarsPadding()"))
        assertTrue(source.contains("top = 14.dp, bottom = 12.dp"))
        assertTrue(File("../core/src/main/res/drawable/ic_sym_keyboard.xml").isFile)
        assertTrue(File("../core/src/main/res/drawable/ic_sym_arrow_counterclockwise.xml").isFile)
    }

    @Test
    fun `le bouton rouge est un carre pendant l activite, une croix au repos`() {
        // `solIsWorking`: à l'écoute, en réflexion, pendant la lecture, ou tant que la réponse
        // s'écrit — le carré ■ arrête; dès qu'il n'y a plus rien à arrêter, la croix ✕ quitte le
        // mode vocal. C'est l'état du modèle, prise en écoute: carré.
        assertTrue(source.contains("val working = if (group != null)"))
        assertTrue(
            source.contains(
                "phase == VoicePhase.LISTENING || phase == VoicePhase.THINKING || " +
                    "phase == VoicePhase.SPEAKING || streaming",
            ),
        )
        assertTrue(source.contains("symbol = if (working) Symbols.Stop else Symbols.Xmark"))
        assertTrue(source.contains("label = if (working) \"Stop\" else \"End voice mode\""))
        // Et la TAILLE du carré: le modèle le dessine à 0,27 du bouton rouge (mesuré sur ses
        // captures: 21 à 22 px de carré dans un disque de 80). Le glyphe ne remplit que 10/24 de
        // sa boîte, qui doit donc faire 39 points — à 20, le carré en faisait 8, la moitié.
        assertTrue(source.contains("iconSize = if (working) VoiceStopGlyph.dp else 20.dp"))
        assertTrue(source.contains("private const val VoiceStopGlyph = 39f"))
        // Et les glyphes eux-mêmes: `stop.fill` est un carré plein, `xmark` deux traits croisés.
        assertTrue(
            File("../core/src/main/res/drawable/ic_sym_stop_fill.xml").readText()
                .contains("M7,7 h10 v10 H7 Z"),
        )
        assertTrue(File("../core/src/main/res/drawable/ic_sym_xmark.xml").isFile)
    }

    @Test
    fun `la pause est aussi branchee en solo et replay ne relance pas le micro`() {
        assertTrue(app.contains("isPaused = voice.isPaused.collectAsStateWithLifecycle().value"))
        assertTrue(app.contains("onPauseResume = { if (voice.isPaused.value) voice.resumeSpeaking() else voice.pauseSpeaking() }"))
        assertFalse(app.contains("onRestart = {\n                    voiceProblem = null\n                    voice.startListening()"))
        assertTrue(app.contains("allowEnd = !state.isStreaming"))
        assertTrue(app.contains("streaming = state.isStreaming"))
        assertTrue(source.contains("|| streaming"))
    }

    @Test
    fun `hands free garde waveform et le titre en footnote`() {
        val pill = source.substringAfter("private fun HandsFreePill").substringBefore("// MARK:")
        assertTrue(pill.contains("symbol = Symbols.Waveform"))
        assertFalse(pill.contains("Symbols.Bolt"))
        assertFalse(pill.contains("\"On\" else \"Off\""))
        assertTrue(source.contains("style = theme.footnote.copy(color = theme.secondaryText, textAlign = TextAlign.End)"))
    }

    @Test
    fun `les orbes image conservent la meme empreinte que l orbe dessine`() {
        assertFalse(source.contains("Box(modifier = Modifier.size(320.dp)"))
        assertTrue(source.contains("Box(modifier = Modifier.size(233.dp)"))
    }

    @Test
    fun `les anneaux de l orbe entourent toutes les faces comme le Halos `() {
        // `VoiceView` pose `Halos` hors du `if`/`else` des orbes: le dessiné **et** les images.
        val drawn = source.substringAfter("private fun VoiceOrb(").substringBefore("private fun phaseTitle(")
        val picture = source.substringAfter("private fun OrbPictureFace(").substringBefore("private fun OrbChooserRow(")
        assertTrue(drawn.contains("VoiceHalos("))
        assertTrue(drawn.contains("subtle = phase == VoicePhase.IDLE"))
        assertTrue(picture.contains("VoiceHalos("))
        assertTrue(picture.contains("subtle = phase == VoicePhase.IDLE"))
    }

    @Test
    fun `le bouton rouge est un carre des que quelque chose tourne et une croix sinon`() {
        // `solIsWorking ? "stop.fill": "xmark"`: le carré arrête, la croix quitte.
        assertTrue(source.contains("symbol = if (working) Symbols.Stop else Symbols.Xmark"))
        assertTrue(source.contains("label = if (working) \"Stop\" else \"End voice mode\""))
        // Le carré fait la taille de celui du modèle (0,25 du bouton), pas la moitié.
        assertTrue(source.contains("VoiceStopGlyph"))
        // `solIsWorking` couvre l'écoute, la réflexion, la lecture et la réponse qui s'écrit.
        assertTrue(source.contains("phase == VoicePhase.LISTENING || phase == VoicePhase.THINKING"))
        assertTrue(source.contains("|| streaming"))
        assertTrue(File("../core/src/main/res/drawable/ic_sym_stop_fill.xml").isFile)
    }
}
