package com.onyx.app.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou contre le défaut d'origine : « À L'ÉCOUTE » avec sept points plats et rien qui rentre.**
 *
 * Le propriétaire voyait exactement cela : l'écran disait qu'il écoutait, les points ne bougeaient
 * pas d'un pixel, et rien — ni à l'écran ni au journal — ne disait que le son n'arrivait pas. Ces
 * vérifications cassent si ce cas redevient muet :
 *
 *  1. une écoute **sans une seule trame** ne peut plus se terminer en [ListenTrouble.NONE] ;
 *  2. un moteur qui tourne **sans un mot** le dit aussi ;
 *  3. le niveau affiché prend **le plus fort** du moteur et du micro de l'app — jamais l'écrasement ;
 *  4. [LevelFusion] exige le micro de l'app quand le moteur ne livre rien d'exploitable ;
 *  5. la session **branche** tout cela (source vérifiée), et la ligne de journal est courte,
 *     cherchable et colorée en avertissement.
 */
class SilentListeningGuardTest {

    // MARK: - 1 & 2. L'honnêteté ne peut plus se taire

    @Test
    fun `le moteur qui tourne sans une trame ne peut plus dire que tout va bien`() {
        val trouble = listenTrouble(
            phase = VoicePhase.LISTENING,
            heard = "",
            frames = 0,
            engineStarted = true,
            listeningForMs = NO_FRAMES_MS,
        )
        assertTrue("le silence total doit être dit", trouble != ListenTrouble.NONE)
        assertTrue("c'est bien « aucune trame »", trouble == ListenTrouble.NO_FRAMES)
    }

    @Test
    fun `un moteur jamais démarré est dit avant tout le reste`() {
        val trouble = listenTrouble(
            phase = VoicePhase.LISTENING,
            heard = "",
            frames = 0,
            engineStarted = false,
            listeningForMs = ENGINE_START_GRACE_MS,
        )
        assertTrue(trouble == ListenTrouble.ENGINE_NEVER_STARTED)
    }

    @Test
    fun `des trames sans un mot le disent aussi`() {
        val trouble = listenTrouble(
            phase = VoicePhase.LISTENING,
            heard = "",
            frames = 42,
            engineStarted = true,
            listeningForMs = NO_WORDS_MS,
        )
        assertTrue(trouble == ListenTrouble.NO_WORDS)
    }

    @Test
    fun `hors de l ecoute, aucune panne n est annoncee`() {
        assertTrue(
            listenTrouble(VoicePhase.THINKING, "", frames = 0, engineStarted = true, listeningForMs = 99_999) ==
                ListenTrouble.NONE,
        )
        assertTrue(
            listenTrouble(VoicePhase.IDLE, "", frames = 0, engineStarted = true, listeningForMs = 99_999) ==
                ListenTrouble.NONE,
        )
    }

    // MARK: - 3. Le niveau affiché

    @Test
    fun `le niveau affiche prend le plus fort des deux signaux`() {
        assertTrue("un moteur muet n'efface plus le micro de l'app", displayedLevel(0f, 0.6f) == 0.6f)
        assertTrue("et l'inverse est vrai aussi", displayedLevel(0.6f, 0f) == 0.6f)
        assertTrue("une mesure plate du moteur ne cache pas un vrai son", displayedLevel(0.02f, 0.44f) == 0.44f)
        assertTrue("les deux signaux muets laissent les points à zéro", displayedLevel(0f, 0f) == 0f)
        assertTrue("rien ne dépasse 1", displayedLevel(3f, 2f) == 1f)
    }

    // MARK: - 4. La décision de prendre le micro de l'app

    @Test
    fun `aucune trame du moteur fait prendre le micro de l app`() {
        val fusion = LevelFusion()
        repeat(20) { fusion.observe(0f, engineFrames = 0) }
        assertTrue("aucune trame : le moteur ne donne rien", fusion.wantsMeter)
    }

    @Test
    fun `des mesures plates font prendre le micro de l app`() {
        val fusion = LevelFusion()
        repeat(20) { fusion.observe(0.01f, engineFrames = 20) }
        assertTrue("un niveau qui ne bouge pas n'est pas exploitable", fusion.wantsMeter)
    }

    @Test
    fun `un vrai niveau du moteur n ouvre pas un second micro`() {
        val fusion = LevelFusion()
        val levels = listOf(0.05f, 0.4f, 0.12f, 0.7f, 0.3f, 0.2f)
        repeat(20) { fusion.observe(levels[it % levels.size], engineFrames = it + 1) }
        assertFalse("le moteur travaille : on ne lui vole pas le micro", fusion.wantsMeter)
        assertTrue(fusion.decided)
    }

    @Test
    fun `une nouvelle ecoute repose la question`() {
        val fusion = LevelFusion()
        repeat(20) { fusion.observe(0f, engineFrames = 0) }
        assertTrue(fusion.wantsMeter)
        fusion.reset()
        assertFalse(fusion.wantsMeter)
        assertFalse(fusion.decided)
    }

    // MARK: - 5. La ligne à transmettre

    @Test
    fun `la ligne d ecoute muette est courte et cherchable`() {
        val line = silentListeningLine(frames = 0, engineStarted = true)
        assertTrue("cherchable telle quelle : $line", line.contains("on n'entend rien"))
        assertTrue("courte : $line", line.length <= 64)
        assertTrue("elle dit que le moteur a répondu", line.contains("moteur=oui"))
        assertTrue(
            "elle dit combien de trames sont arrivées, moteur et micro confondus",
            silentListeningLine(frames = 0, engineStarted = false).contains("trames=0"),
        )
    }

    // MARK: - Le câblage, en source (le vrai rendu est jugé par la capture d'émulateur)

    @Test
    fun `la session branche l honnetete sur ce cas`() {
        val session = java.io.File("src/main/java/com/onyx/app/voice/VoiceSession.kt").readText()
        assertTrue("le niveau affiché est le plus fort des deux", session.contains("displayedLevel(engineSpeech.level, meter.level.value)"))
        assertTrue("la décision du moteur muet est prise", session.contains("levelFusion.observe(engineSpeech.level, engine.frameCount)"))
        assertTrue(
            "et le micro de l'app ne s'ouvre que **sur décision**, et **une seule fois** par écoute",
            session.contains("if (levelFusion.wantsMeter && !meterAttempted && !meter.isAvailable) {") &&
                session.contains("meterAttempted = true"),
        )
        assertTrue(
            "les trames jugées sont celles du moteur **plus** celles du micro de l'app",
            session.contains("val frames = engine.frameCount + meter.frames"),
        )
        assertTrue(
            "l'alerte « on n'entend rien » est écrite avec la ligne courte",
            session.contains("VoiceDiagnostics.note(silentListeningLine(frames, engine.answered))"),
        )
        assertTrue(
            "les points retombent au repos dès qu'on quitte l'écoute",
            session.contains("_listenLevel.value = 0f"),
        )
    }
}
