package com.onyx.app.ui.voice

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le mode vocal solo 2.0: la transcription en haut à droite, la pastille mains libres, les
 * trois commandes, le réglage de l'écouteur, et l'interruption qui passe par le niveau du micro.
 *
 * Vérifications de source: elles portent sur le câblage, pas sur le rendu — c'est la capture
 * d'émulateur qui juge le rendu.
 */
class SoloVoice2Test {
    private val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()
    private val screen = File("src/main/java/com/onyx/app/ui/voice/VoiceScreen.kt").readText()
    private val settings = File("src/main/java/com/onyx/app/ui/settings/VoiceSettingsScreen.kt").readText()
    private val repo = File("src/main/java/com/onyx/app/data/repo/SettingsRepository.kt").readText()
    private val session = File("../core/src/main/java/com/onyx/app/voice/VoiceSession.kt").readText()

    @Test
    fun `l en tete montre le titre de la conversation, jamais le nom du serveur`() {
        // `soloHeader`: `Text(conversation.title)`, tronqué à 170 points.
        assertTrue(app.contains("(state.conversationName?.takeIf { it.isNotBlank() }"))
        assertTrue(app.contains("conversationTitle = if (voiceDemo)"))
        assertFalse(
            "l'écran vocal ne doit pas titrer directement avec le nom du serveur",
            app.contains("conversationTitle = state.server.displayName,"),
        )
        assertTrue(screen.contains("Modifier.weight(1f).widthIn(max = 170.dp)"))
        assertTrue(screen.contains("Symbols.Waveform"))
        assertTrue(screen.contains("text = \"Hands-free\""))
    }

    @Test
    fun `le réglage de l écouteur fait le tour écran, dépôt et enregistrement`() {
        assertTrue(repo.contains("booleanPreferencesKey(\"earpieceAtEar\")"))
        assertTrue(repo.contains("_earpieceAtEar.value = preferences[Keys.EARPIECE_AT_EAR] ?: true"))
        assertTrue(repo.contains("suspend fun setEarpieceAtEar(value: Boolean)"))
        assertTrue(settings.contains("label = \"Earpiece when raised to your ear\""))
        assertTrue(settings.contains("onCheckedChange = viewModel::setEarpieceAtEar"))
        assertTrue(app.contains("voice.earRoutingEnabled = earpieceAtEar"))
    }

    @Test
    fun `le barge-in passe par le niveau du micro puis par les mots`() {
        // Le niveau d'abord: le moteur ne doit pas s'interrompre en s'entendant lui-même.
        assertTrue(session.contains("bargeIn = if (audioRoute.hasExternalRoute())"))
        assertTrue(session.contains("BargeInDetector.headset(hold = holdSeconds)"))
        assertTrue(session.contains("BargeInDetector.echoCancelled(hold = holdSeconds)"))
        assertTrue(session.contains("bargeIn.feed(speech.rmsDb, LEVEL_STEP_SECONDS)"))
        assertTrue(session.contains("output.pause()"))
        assertTrue(session.contains("output.resume()"))
        // Et la décision: bruits d'écoute, ordre d'arrêt, vrais mots.
        assertTrue(session.contains("BargeInOutcome.BACKCHANNEL"))
        assertTrue(session.contains("BargeInOutcome.STOP"))
        assertTrue(session.contains("BargeInOutcome.WORDS"))
        assertTrue(session.contains("stopPhrases"))
        // « Stop » par-dessus la réponse coupe aussi le tour qui s'écrit côté serveur.
        assertTrue(app.contains("voice.onStoppedByWord = { chatViewModel.stop() }"))
    }

    @Test
    fun `les barres de l orbe ne sont plus decoupees au BlendMode Clear`() {
        val motion = File("src/main/java/com/onyx/app/ui/voice/VoiceLiveMotion.kt").readText()
        val orb = motion.substringAfter("internal fun DarkGlassWaveOrb(")
            .substringBefore("/** `LiveWaveform.loudness`")
        // Le `Clear` hors écran est ce qui trouait le fond sous l'orbe: les carrés et les points
        // colorés autour des barres venaient de là.
        assertFalse("plus de découpe au Clear", orb.contains("BlendMode.Clear"))
        assertFalse("plus de composition hors écran", orb.contains("CompositingStrategy"))
        // Des barres arrondies, qui montent et descendent, avec un dégradé cohérent avec le disque.
        assertTrue(orb.contains("cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)"))
        assertTrue(orb.contains("Brush.verticalGradient"))
        assertTrue(orb.contains("LiveWaveRest"))
        // Les barres figées du PNG sont effacées une fois pour toutes, dans le bitmap — et elles
        // sont **trouvées dans l'image**: une géométrie supposée laissait 2 px de blanc aux deux
        // extrémités (« deux mini barres verticales » aux bouts de la rangée de points).
        assertTrue(motion.contains("internal fun stripFrozenBars("))
        assertTrue(motion.contains("frozenBarColumns(pixels, out.width, out.height)"))
        assertTrue(motion.contains("internal fun frozenBarColumns("))
    }

    @Test
    fun `le demi-duplex et l endpointing sont branches sur la session`() {
        // Le micro derrière la réponse: le réglage « Parler pendant les réponses » **et** la route
        // du moment (jamais sur le haut-parleur quand le réglage ne vaut que pour un casque).
        assertTrue(session.contains("shouldReopenMicForSpeech(_phase.value, engine.state.value.isListening, listenAfterSpeech, micBehindReply)"))
        assertTrue(session.contains("micBehindReply = talkOverAllowsMic(talkOver, audioRoute.hasExternalRoute())"))
        assertTrue(session.contains("if (!micBehindReply) return"))
        // La garde anti-écho passe par le même point de décision sur les deux chemins: la
        // transcription finale, et la parole par-dessus la lecture.
        assertTrue(session.contains("shouldSendHeardTranscript(speech.transcript, lastReplyText, alreadySent = false)"))
        assertTrue(session.contains("shouldSendHeardTranscript(text, _spoken.value, alreadySent = false)"))
        assertTrue(session.contains("endpointSilenceMs"))
        // L'endpointing: la transcription stable **et** la pièce calme (1,2 s / 0,72 s).
        assertTrue(session.contains("endpointWatcher.level(engine.state.value.level"))
        assertTrue(session.contains("endpointWatcher.shouldEnd(_heard.value"))
        assertTrue(session.contains("var autoRelisten: Boolean = false"))
        assertTrue(session.contains("BARGE_IN_HOLD_MS / 1000.0"))
    }

    @Test
    fun `le micro est ferme avant que la voix ne demarre`() {
        // Le texte lu ne doit pas être capté avant la fermeture du micro: la session d'écoute est
        // jetée au début de la lecture, et la relance après la fin attend un délai.
        val speak = session.substringAfter("fun speak(text: String").substringBefore("fun pause()")
        assertTrue(speak.contains("engine.cancel()"))
        assertTrue(session.contains("delay(MIC_AFTER_REPLY_DELAY_MS)"))
    }

    @Test
    fun `le telephone a l oreille sort par l ecouteur`() {
        assertTrue(session.contains("ear.setWanted(true)"))
        assertTrue(session.contains("ear.setWanted(false)"))
        assertTrue(session.contains("ear.onAtEarChanged = { atEar -> reroute(atEar) }"))
        assertTrue(session.contains("output.setEarRouting(atEar)"))
        val output = File("../core/src/main/java/com/onyx/app/voice/SpeechOutput.kt").readText()
        assertTrue(output.contains("AudioAttributes.USAGE_VOICE_COMMUNICATION"))
        assertTrue(output.contains("engine?.setAudioAttributes"))
        val player = File("../core/src/main/java/com/onyx/app/voice/SpeechAudioPlayer.kt").readText()
        assertTrue(player.contains(".setUsage(usage)"))
        // Le capteur de proximité est la seule source de « à l'oreille ».
        val android = File("../core/src/main/java/com/onyx/app/voice/AndroidEarRouting.kt").readText()
        assertTrue(android.contains("Sensor.TYPE_PROXIMITY"))
        assertTrue(android.contains("AudioManager.MODE_IN_COMMUNICATION"))
        assertTrue(android.contains("isSpeakerphoneOn = false"))
        // Et l'accéléromètre dit « posé face contre la table », qui n'est pas une oreille.
        assertTrue(android.contains("Sensor.TYPE_ACCELEROMETER"))
    }
}
