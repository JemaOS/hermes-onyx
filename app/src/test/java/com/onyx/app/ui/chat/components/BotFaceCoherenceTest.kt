package com.onyx.app.ui.chat.components

import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.ui.bots.BotFaceResolver
import com.onyx.app.ui.bots.withSwiftFaces
import com.onyx.app.ui.design.BotFace
import com.onyx.app.ui.design.FaceEyes
import com.onyx.app.ui.design.FaceMark
import com.onyx.app.ui.design.FaceMouth
import com.onyx.app.ui.design.FaceShape
import com.onyx.app.ui.design.botFace
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou de cohérence du visage** : un même bot porte **le même visage** sur toutes les
 * surfaces.
 *
 * Le défaut signalé : la barre du haut montrait la **bulle bleue** d'Onyx, et le Flux (avec l'en-tête
 * « Onyx » de la section) un **soleil ambre**. Ce n'était pas deux bots : c'était **un seul bot, deux
 * clés**. Pour Onyx, le profil s'appelle `default` et le libellé `Onyx` — la barre du haut résolvait
 * le visage sur le **profil**, le Flux sur le **libellé**, et
 * `BotFace.forName("default")` (la bulle bleue) n'est pas `BotFace.forName("Onyx")` (un soleil ambre
 * de forme `burst`). Le visage se résout donc désormais **partout** par `botFace(nom de profil,
 * code)` — une seule fonction —, et aucune surface ne passe plus un libellé.
 *
 * Les tests de source tiennent le **câblage** (la clé que chaque surface donne) ; les tests purs
 * tiennent la **résolution** elle-même et le visage **neutre** d'un auteur inconnu.
 */
class BotFaceCoherenceTest {

    private val chatScreen = File("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt").readText()
    private val panels = File("src/main/java/com/onyx/app/ui/chat/ChatPanels.kt").readText()
    private val sidebarHost = File("src/main/java/com/onyx/app/ui/sidebar/ChatSidebarHost.kt").readText()
    private val sidebarBots = File("src/main/java/com/onyx/app/ui/sidebar/ChatSidebarBots.kt").readText()

    // — 1. Le même bot rend le même visage, quelle que soit la surface —

    @Test
    fun `le meme bot rend le meme visage sur la barre et dans le flux`() {
        // Ce que chaque surface passe à la résolution unique. Le bot principal a DEUX clés : son
        // profil (« default ») — la clé de la barre du haut — et son libellé (« Onyx ») — la clé
        // qu'employait le Flux. Deux clés, deux créatures.
        val topBar = botFace("default", null)
        val feed = botFace("default", null)

        assertEquals("le même bot, le même visage", topBar, feed)
        assertEquals("Onyx est sa bulle bleue", BotFace.main, topBar)
        // La preuve du défaut, gardée : le libellé n'est **pas** une identité de visage — s'il
        // redevenait la clé d'une surface, la bulle bleue deviendrait un soleil ambre.
        val byLabel = botFace("Onyx", null)
        assertNotEquals("le libellé n'est pas le profil", BotFace.main, byLabel)
        // Le soleil jaune de la capture, nommé : la forme `burst` (le soleil) en teinte `Amber`.
        assertEquals("le libellé « Onyx » donne bien un soleil", FaceShape.BURST, byLabel.shape)
        assertEquals("et sa teinte est l'ambre (le jaune)", 1, byLabel.colour)
        assertEquals("la bulle bleue, elle, n'est pas un soleil", FaceShape.BUBBLE, BotFace.main.shape)
    }

    @Test
    fun `un bot du roster montre le meme visage par toutes les cles de ses surfaces`() {
        val roster = listOf(
            BotProfile(name = "default", isDefault = true, title = "Onyx"),
            BotProfile(name = "scout", title = "Scout"),
            BotProfile(name = "quill", title = "Quille"),
        ).withSwiftFaces()
        val resolver = BotFaceResolver.of(roster)

        roster.forEach { bot ->
            // Ce que la barre du haut et le Flux reçoivent maintenant : `ThreadSpeaker.faceName` /
            // `.faceCode`. Ce que la liste, les cartes et les salons reçoivent :
            // `BotFaceResolver.codeOf(bot.name)`. Les deux doivent rendre le même visage.
            val speaker = threadHeaderSpeaker(bot, assistantName = "Onyx")
            assertEquals(
                "la barre du haut et la liste s'accordent sur « ${bot.name} »",
                botFace(speaker.faceName, speaker.faceCode),
                botFace(bot.name, resolver.codeOf(bot.name)),
            )
            // Et le libellé, s'il différait du profil, ne doit rien changer.
            assertEquals(
                "le libellé de « ${bot.name} » ne déplace pas le visage",
                botFace(bot.name, resolver.codeOf(bot.name)),
                botFace(speaker.label, speaker.faceCode),
            )
        }
    }

    // — 2. Le visage se résout sur le PROFIL, jamais sur le libellé —

    @Test
    fun `le flux demande le visage au profil du fil, pas a son libelle`() {
        val faceName = Regex("""faceName\s*=\s*threadSpeaker\.faceName\s*,""").findAll(chatScreen).count()
        // Seul le panneau du **Flux** reste (Idées et Objectifs supprimés) : il passe le **profil**,
        // jamais le libellé — le défaut « deux visages pour un bot » ne peut pas revenir par là.
        assertEquals("le Flux passe le profil", 1, faceName)
        assertFalse(
            "le libellé ne sert plus d'identité de visage",
            Regex("""botName\s*=\s*threadSpeaker\.label\s*,\s*\n\s*faceCode""").containsMatchIn(chatScreen),
        )
    }

    @Test
    fun `les panneaux dessinent le visage sur son nom de profil`() {
        assertTrue("la tête d'onglet", panels.contains("BotLook(name = faceName, faceCode = faceCode"))
        assertFalse("plus de visage résolu sur le libellé", panels.contains("BotLook(name = botName"))
        assertTrue("la source d'une carte", panels.contains("SourceMark(faceName = faceName"))
    }

    @Test
    fun `la liste et les salles resolvent sur le nom de profil`() {
        // L'écran des conversations a été retiré : sa liste **et ses salles** vivent dans le volet.
        // Les salons y portent les noms de **profil** de leurs membres, et la pile se dessine par
        // eux — jamais par les libellés (sinon un membre montrait le soleil de son libellé).
        assertTrue(
            "la pile de membres d'une salle, sur les profils",
            sidebarBots.contains("InterlacedFaces(names = row.memberProfiles"),
        )
        assertFalse("plus les libellés d'une salle comme clés de visage",
            sidebarBots.contains("InterlacedFaces(names = row.members"))
    }

    @Test
    fun `le volet entrelace les visages sur les noms de profil`() {
        assertTrue(
            "les salons du volet portent aussi les profils",
            sidebarHost.contains("memberProfiles = room.members.map { it.name }"),
        )
        assertTrue(
            "et les dessinent par eux, jamais par les libellés",
            sidebarBots.contains("InterlacedFaces(names = row.memberProfiles"),
        )
    }

    // — 3. Aucun visage de repli pour un bot connu ; un auteur inconnu est neutre —

    @Test
    fun `un auteur inconnu recoit un visage neutre, jamais une creature`() {
        val unknown = botFace("", null)
        assertEquals("le neutre de la résolution", BotFace.unknown, unknown)
        assertEquals("un rond", FaceShape.CIRCLE, unknown.shape)
        assertEquals("deux yeux ronds", FaceEyes.ROUND, unknown.eyes)
        assertEquals("aucune bouche", FaceMouth.NONE, unknown.mouth)
        assertEquals("aucune marque", FaceMark.NONE, unknown.mark)
        // Une créature donnée par un nom est **expressive** ; le neutre ne l'est pas — deux formes
        // différentes, donc l'un ne se lit jamais comme l'autre.
        assertNotEquals("un nom ne donne pas le neutre", BotFace.forName(""), unknown)
        assertNotEquals("le neutre n'est pas la bulle de Onyx", BotFace.main.shape, unknown.shape)
    }

    @Test
    fun `un code enregistre l emporte, quel que soit le nom`() {
        val code = "1:egg:sparkle:smile:antenna:8:1:-3"
        assertEquals(BotFace.parse(code), botFace("default", code))
        assertEquals(BotFace.parse(code), botFace("un-nom-quelconque", code))
    }
}
