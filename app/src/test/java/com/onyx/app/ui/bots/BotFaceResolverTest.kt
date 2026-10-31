package com.onyx.app.ui.bots

import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.data.model.serve.GroupMember
import com.onyx.app.ui.design.BotFace
import com.onyx.app.ui.design.FaceEyes
import com.onyx.app.ui.design.FaceMark
import com.onyx.app.ui.design.FaceMouth
import com.onyx.app.ui.design.FaceShape
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le visage d'un profil, résolu par **une seule** fonction ([BotFaceResolver]) : la salle montre
 * exactement ce que la liste montre, et un bot sans visage attribué garde le même à chaque
 * ouverture.
 *
 * Le défaut d'origine : la salle lisait le choix local (`BotFaceStore`) toute seule. Quand le
 * visage d'un bot venait du serveur (`ui_meta.onyx.face`), ce choix local était vide, la salle
 * retombait sur le seul **nom**, et les deux grands visages n'étaient plus ceux du profil — la
 * liste, elle, lisait bien la table du roster. Deux chemins, deux visages pour un même bot.
 */
class BotFaceResolverTest {

    private fun read(path: String) = File(path).readText()

    // — Les couleurs de la palette, par leur rang (`BotFace.colourNames`) —
    private val blue = 0
    private val violet = 5
    private val green = 6
    private val orange = 7

    /** Un bot du serveur, avec le visage que son profil porte (`ui_meta.onyx.face`). */
    private fun bot(name: String, face: BotFace): BotProfile =
        BotProfile(name = name, displayName = name, faceCode = face.code())

    private fun face(shape: FaceShape, colour: Int, mark: FaceMark = FaceMark.NONE): BotFace =
        BotFace(
            shape = shape,
            eyes = FaceEyes.HAPPY,
            mouth = FaceMouth.GRIN,
            mark = mark,
            colour = colour,
            glance = 1,
            tilt = 3,
        )

    /** Trois groupes de couleurs différentes, comme ceux que la personne ouvre. */
    private val threeGroups: List<List<BotProfile>> = listOf(
        // Le groupe signalé : orange et vert.
        listOf(
            bot("QA-tester", face(FaceShape.ROUND, orange)),
            bot("writer", face(FaceShape.EGG, green, FaceMark.FRECKLES)),
        ),
        // Violet et vert.
        listOf(
            bot("scout", face(FaceShape.PILL, violet)),
            bot("writer", face(FaceShape.HEX, green)),
        ),
        // Violet et bleu.
        listOf(
            bot("scout", face(FaceShape.STAR, violet)),
            bot("quill", face(FaceShape.BLOB, blue, FaceMark.SPARK)),
        ),
    )

    // — 1. La salle et la liste montrent le MÊME visage, sur trois groupes —

    @Test
    fun laSalleEtLaListeMontrentLeMemeVisageSurTroisGroupes() {
        threeGroups.forEachIndexed { index, roster ->
            val listed = roster.withSwiftFaces().associate { it.name to it.faceCode }
            // Ce que la salle résout : `OnyxApp` construit le résolveur sur les bots tels qu'ils
            // portent leur visage — exactement la liste passée à la section.
            val room = BotFaceResolver.of(roster.withSwiftFaces())

            roster.forEach { member ->
                val inList = listed[member.name]
                val inRoom = room.codeOf(member.name)
                assertEquals(
                    "groupe ${index + 1} : ${member.name} ne peut pas montrer deux visages",
                    inList,
                    inRoom,
                )
                assertEquals(
                    "groupe ${index + 1} : ${member.name} montre le visage de son profil",
                    member.faceCode,
                    inRoom,
                )
                assertNotNull("groupe ${index + 1} : ${member.name} a un visage", inRoom)
            }

            // Les couleurs réellement dessinées sont celles choisies pour ce groupe.
            assertEquals(
                "groupe ${index + 1} : les couleurs de la salle",
                roster.map { BotFace.parse(it.faceCode)!!.colour },
                roster.map { BotFace.parse(room.codeOf(it.name))!!.colour },
            )
        }
    }

    @Test
    fun lOrdreDesVisagesDeLaSalleSuitLOrdreDesMembres() {
        // L'ordre est celui que le serveur renvoie, et les deux visages ne se confondent pas.
        val members = listOf(
            GroupMember(id = "qa-tester", profile = "qa-tester", handle = "hermes"),
            GroupMember(id = "writer", profile = "writer", handle = "writer"),
        )
        val resolved = listOf(
            bot("qa-tester", face(FaceShape.ROUND, orange)),
            bot("writer", face(FaceShape.EGG, green)),
        ).withSwiftFaces()
        val room = BotFaceResolver.of(resolved)

        val shown = members.map { member -> room.codeOf(member.profile) }
        assertEquals(listOf(orange, green), shown.map { BotFace.parse(it)!!.colour })
        // L'ordre compte : la pile suit l'ordre des membres, elle ne les réordonne pas.
        val reversed = members.reversed().map { member -> room.codeOf(member.profile) }
        assertEquals(listOf(green, orange), reversed.map { BotFace.parse(it)!!.colour })
    }

    @Test
    fun leNomSeulNeRedonnePasLeVisageDuProfil() {
        // Ce que la salle montrait avant le correctif : elle ne lisait que le choix gardé par le
        // téléphone, vide quand le visage vient du serveur (`ui_meta.onyx.face`), et retombait
        // alors sur le **seul nom**. Relevé sur ce groupe : « QA-tester » y donnait un visage bleu
        // et « writer » un indigo — deux bleus/violets là où les profils portent orange et vert.
        val roster = threeGroups[0]
        val resolved = roster.withSwiftFaces()
        val room = BotFaceResolver.of(resolved)
        val listed = resolved.associate { it.name to it.faceCode }
        val byNameOnly = resolved.associate { it.name to BotFace.forName(it.name).code() }

        assertEquals(orange, BotFace.parse(room.codeOf("QA-tester"))!!.colour)
        assertEquals(green, BotFace.parse(room.codeOf("writer"))!!.colour)
        assertEquals(listed["QA-tester"], room.codeOf("QA-tester"))
        assertEquals(listed["writer"], room.codeOf("writer"))

        // Le nom seul ne redonne ni l'un ni l'autre : c'est là que la salle se séparait de la liste.
        assertNotEquals(listed["QA-tester"], byNameOnly["QA-tester"])
        assertNotEquals(listed["writer"], byNameOnly["writer"])
    }

    // — 2. Un bot sans visage attribué retombe sur un visage STABLE —

    @Test
    fun unBotSansVisageAttribueGardeLeMemeVisageAChaqueOuverture() {
        val roster = listOf(BotProfile(name = "scout"), BotProfile(name = "quill"))
        val listed = roster.withSwiftFaces().associate { it.name to it.faceCode }

        // Deux ouvertures de la même salle : la même table, donc les mêmes visages.
        val first = BotFaceResolver.of(roster.withSwiftFaces())
        val second = BotFaceResolver.of(roster.withSwiftFaces())

        roster.forEach { member ->
            val once = first.codeOf(member.name)
            val again = second.codeOf(member.name)
            assertNotNull("$member a un visage", once)
            assertEquals("le visage ne change pas d'une ouverture à l'autre", once, again)
            assertEquals("la salle lit le visage du profil", listed[member.name], once)
        }

        // Sans aucune table, un nom ne tire jamais au sort : le même visage à chaque fois.
        assertEquals(BotFace.forName("scout"), BotFace.forName("scout"))
        assertEquals(BotFace.forName("scout").code(), BotFace.forName("scout").code())
        assertNull(BotFaceResolver.none.codeOf("scout"))
    }

    @Test
    fun leChoixDuTelephonePrimelEtUnNomInconnuDeLaTableRetombeSurLui() {
        val table = mapOf("writer" to face(FaceShape.EGG, green).code())
        val local = mapOf(
            "writer" to face(FaceShape.HEX, orange).code(),
            "scout" to face(FaceShape.PILL, violet).code(),
        )
        val faces = BotFaceResolver.of(table, local::get)

        assertEquals("la table du roster d'abord", table["writer"], faces.codeOf("writer"))
        assertEquals("un nom que la table ne porte pas", local["scout"], faces.codeOf("scout"))
        assertNull("un profil inconnu ne tire rien au sort", faces.codeOf("inconnu"))
    }

    // — 3. Une seule fonction de résolution, et aucun écran qui résout à part —

    @Test
    fun laSalleNeResoutPlusLesVisagesTouteSeule() {
        val room = read("src/main/java/com/onyx/app/ui/bots/GroupRoomScreen.kt")
        assertFalse(
            "la salle ne lit plus le choix local pour dessiner",
            room.contains("BotFaceStore"),
        )
        assertTrue(
            "la salle lit la résolution unique",
            room.contains("val face: (String) -> String? = faceResolver::codeOf"),
        )
        // L'en-tête (22 dp), les grands visages (44 dp) et les réponses passent tous par `face`.
        assertTrue(
            "l'en-tête et les grands visages des membres",
            room.contains("RoomFaceStack(members = room.members, face = face, size = 22.dp)"),
        )
        assertTrue(
            "les grands visages de la salle",
            room.contains("RoomFaceStack(members = room.members, face = face, size = 44.dp)"),
        )
        assertTrue(
            "les mentions `@` du composeur",
            room.contains("room.composerBots(face)"),
        )
        assertTrue(
            "l'ordre suit l'ordre des membres du serveur",
            room.contains("members.take(3).forEach { member ->"),
        )
    }

    @Test
    fun leVoletResoutLesVisagesParLaMemeFonction() {
        val volet = read("src/main/java/com/onyx/app/ui/sidebar/ChatSidebarBots.kt")
        assertTrue("la tuile d'un bot", volet.contains("faceCode = faceCodeOf(bot.name)"))
        assertTrue(
            "les visages empilés d'une salle",
            volet.contains("InterlacedFaces(names = row.memberProfiles, faceCodeOf = faceCodeOf)"),
        )
        assertFalse(
            "aucun écran ne lit le choix local pour dessiner",
            volet.contains("BotFaceStore"),
        )
        assertTrue(
            "l'ordre suit l'ordre des profils des membres du serveur",
            volet.contains("names = row.memberProfiles"),
        )
    }

    @Test
    fun leRosterResoutLesVisagesParLaMemeFonction() {
        val viewModel = read("src/main/java/com/onyx/app/ui/bots/BotsViewModel.kt")
        assertTrue(
            "le visage d'un bot passe par la résolution unique",
            viewModel.contains("BotFaceResolver.of(_state.value.faces).codeOf(bot.name)"),
        )
    }
}
