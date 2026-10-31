package com.onyx.app.ui.voice

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou des chemins de lecture : la voix du bot, ou rien.**
 *
 * Le défaut : « la voix du bot est réglée, il parle toujours de la voix par défaut ». La cause
 * n'était pas le stockage — la valeur était gardée et affichée — mais la **lecture** : quatre
 * chemins demandaient une réplique **sans dire de qui** elle était, donc `SpeechOutput` retombait sur
 * la voix d'Onyx. Le modèle (la référence) est pourtant net : `readAloud(text, speaker: conversation.bot?.profile)`
 * partout.
 *
 * Ce test **mord** : tout appel de lecture d'une réponse (`voice.speak`, `voice.finishTurn`,
 * `speech.speak`, `session.speak`) doit porter un `speaker`. La liste des chemins est celle du
 * relevé, et elle est vérifiée **génériquement** — un chemin de lecture ajouté plus tard, sans
 * locuteur, fait échouer le test au lieu de passer inaperçu.
 */
class BotVoiceReadPathsTest {

    private val root = File("src/main/java/com/onyx/app/ui")
    private val app = File(root, "OnyxApp.kt").readText()
    private val groupChat = File(root, "bots/GroupChatViewModel.kt").readText()
    private val groupVoice = File(root, "bots/GroupVoice.kt").readText()

    /** Les arguments de chaque appel `call` du fichier, parenthèses équilibrées. */
    private fun callArguments(source: String, call: String): List<String> {
        val out = mutableListOf<String>()
        var from = 0
        while (true) {
            val at = source.indexOf(call, from)
            if (at < 0) break
            from = at + call.length
            if (source.substring(maxOf(0, at - 4), at).endsWith("fun ")) continue
            var depth = 1
            var i = from
            while (i < source.length && depth > 0) {
                when (source[i]) {
                    '(' -> depth++
                    ')' -> depth--
                }
                i++
            }
            out += source.substring(from, i - 1)
        }
        return out
    }

    // MARK: - Chaque chemin de lecture dit de qui sont les mots

    @Test
    fun `aucun chemin de lecture ne demande une reponse sans dire de quel bot elle est`() {
        // Le fil : « Lire à voix haute » sur une réponse.
        val fil = callArguments(app, "voice.speak(")
        assertTrue("le fil lit des réponses", fil.isNotEmpty())
        fil.forEach { args ->
            assertTrue("voix du fil : speaker manquant dans « $args »", args.contains("speaker ="))
        }

        // Le mode vocal : la fin du tour, et la relance.
        val turns = callArguments(app, "voice.finishTurn(")
        assertTrue("le mode vocal lit la réponse du tour", turns.isNotEmpty())
        turns.forEach { args ->
            assertTrue("mode vocal : speaker manquant dans « $args »", args.contains("speaker ="))
        }

        // La salle neuve : la lecture à voix haute de ce qui s'écrit (`GroupChatViewModel`).
        val salle = callArguments(groupChat, "speech.speak(")
        assertTrue("la salle lit ses réponses", salle.isNotEmpty())
        salle.forEach { args ->
            assertTrue("salle : speaker manquant dans « $args »", args.contains("speaker ="))
        }

        // La salle du serveur (la scène vocale) : déjà câblée, et le verrou la tient.
        val scene = callArguments(groupVoice, "session.speak(")
        assertTrue("la scène vocale lit les répliques", scene.isNotEmpty())
        scene.forEach { args ->
            assertTrue("scène vocale : speaker manquant dans « $args »", args.contains("speaker ="))
        }
    }

    @Test
    fun `le bot de la conversation ouverte est celui que la lecture nomme`() {
        val bot = File(root, "bots/BotChat.kt")
        assertTrue("le bot de la conversation", bot.exists())
        // Le fil, le mode vocal (tour et relance) et la maquette de QA nomment le bot de la
        // conversation **ouverte**, comme `conversation.bot?.profile` du modèle.
        assertEquals(4, Regex("speaker = open(ed)?ConversationBot\\?\\.name").findAll(app).count())
        assertFalse(
            "plus de lecture du fil sans locuteur",
            app.contains("voice.speak(message.text, listenAfterSpeech = false)"),
        )
        assertFalse("plus de relance sans locuteur", app.contains("voice.speak(it.text) }"))
    }

    // MARK: - Le locuteur traverse la session, et décide la voix

    @Test
    fun `la session porte le locuteur jusqu au lecteur, et le lecteur le resout par le bot`() {
        val session = File("../core/src/main/java/com/onyx/app/voice/VoiceSession.kt").readText()
        val reply = File("../core/src/main/java/com/onyx/app/voice/SpeechReply.kt").readText()
        val output = File("../core/src/main/java/com/onyx/app/voice/SpeechOutput.kt").readText()

        assertTrue(
            "la session accepte le locuteur",
            session.contains("fun speak(text: String, listenAfterSpeech: Boolean = true, speaker: String? = null)"),
        )
        assertTrue(
            "la fin du tour aussi",
            session.contains("fun finishTurn(replyText: String?, speaker: String? = null)"),
        )
        assertTrue("et le passe au lecteur", session.contains("reply.speak(clean, replyLanguageTag, speaker)"))
        assertTrue("le lecteur le porte", reply.contains("fun speak(text: String, languageTag: String?, speaker: String?)"))
        assertTrue(
            "et la voix se résout par le profil du bot",
            output.contains("BotVoices.kokoroFor(speakingAs, botVoices, kokoroVoice, kokoroVoices, botNames)"),
        )
        // Le synthétiseur de l'appareil : la même règle, par `BotVoices.onDevice`.
        assertTrue(output.contains("forProfile = speaker,"))
    }

    @Test
    fun `la salle neuve porte les memes reglages de voix que le fil`() {
        // Sans ses réglages, le lecteur de la salle lirait de la voix d'Onyx quel que soit le profil
        // qu'on lui passe : le `speaker` n'aurait rien à quoi s'appliquer.
        assertTrue(groupChat.contains("speech.configure("))
        assertTrue(groupChat.contains("voice = setup.replyVoice,"))
        assertTrue(groupChat.contains("botVoices = setup.botVoices,"))
        assertTrue(
            "et la réplique sort de la voix de son bot",
            groupChat.contains("speech.speak(said, speaker = bot.name)"),
        )
    }
}
