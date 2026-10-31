package com.onyx.app.ui.chat.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le visage de la barre du haut.**
 *
 * Il a disparu de l'écran que la personne voit en premier — une nouvelle conversation — et c'est ce
 * qu'elle a signalé. La cause n'était pas le dessin : c'était la **condition de pose**. La barre ne
 * recevait le visage que si le fil était celui d'un bot (`botProfile != null`) ; une conversation
 * simple n'en a pas, donc la barre n'avait plus rien à gauche du titre, et la pastille du volet
 * restait seule.
 *
 * Ce test tient les trois bouts, parce qu'il suffit d'un seul pour perdre le visage :
 * 1. la **source** — `threadHeaderSpeaker` donne un visage pour une conversation simple (celui
 *    d'Onyx), et son nom n'est jamais vide ;
 * 2. le **câblage** — `ChatScreen` passe ce visage à la barre, sans condition ;
 * 3. le **dessin** — `OnyxTopBar` pose `TopBarAvatar`, qui pose `LiveBotFace` (le visage animé).
 */
class TopBarFaceTest {

    private fun read(path: String): String {
        val file = listOf(path, "../$path").map(::File).firstOrNull { it.isFile }
            ?: error("fichier introuvable : $path")
        return file.readText()
    }

    private val chatScreen = read("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt")
    private val topBar = read("src/main/java/com/onyx/app/ui/chat/components/OnyxTopBar.kt")

    // ---- 1. la source -------------------------------------------------------------------------

    @Test
    fun `une conversation simple porte le visage de Onyx`() {
        val plain = threadHeaderSpeaker(bot = null, assistantName = "Onyx")
        assertEquals("le profil du fil simple", OnyxProfile, plain.faceName)
        assertTrue("le nom du visage n'est jamais vide", plain.faceName.isNotBlank())
    }

    // ---- 2. le câblage -----------------------------------------------------------------------

    @Test
    fun `la barre recoit le visage sans condition`() {
        val pass = Regex("""avatarName\s*=\s*threadSpeaker\.faceName\s*,""")
        assertTrue(
            "ChatScreen doit passer `avatarName = threadSpeaker.faceName`",
            pass.containsMatchIn(chatScreen),
        )
        // La condition retirée : plus aucun `takeIf` sur le visage de la barre.
        assertFalse(
            "le visage ne doit plus être conditionné à `botProfile`",
            chatScreen.contains("avatarName = threadSpeaker.faceName.takeIf"),
        )
        assertTrue(
            "le code du visage doit être passé lui aussi",
            Regex("""avatarFaceCode\s*=\s*threadSpeaker\.faceCode\s*,""").containsMatchIn(chatScreen),
        )
    }

    // ---- 3. le dessin ------------------------------------------------------------------------

    @Test
    fun `la barre dessine le visage vivant`() {
        assertTrue(
            "OnyxTopBar doit poser TopBarAvatar",
            topBar.contains("TopBarAvatar(name = avatarName, faceCode = avatarFaceCode)"),
        )
        assertTrue(
            "l'avatar doit être le visage vivant (LiveBotFace) et non un dessin à part",
            topBar.contains("LiveBotFace(name = name, faceCode = faceCode)"),
        )
    }

    @Test
    fun `le visage n est pas conditionne au bot dans la barre`() {
        // `if (avatarName != null)` est la seule garde admise : elle ne dépend pas du type de fil.
        assertTrue(
            "la garde de l'avatar doit être la seule nullité du nom",
            topBar.contains("if (avatarName != null) {"),
        )
    }
}
