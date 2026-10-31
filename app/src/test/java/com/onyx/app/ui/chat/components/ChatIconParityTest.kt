package com.onyx.app.ui.chat.components

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatIconParityTest {
    @Test
    fun `doc on doc garde la feuille arriere entiere et son coin`() {
        val paths = vectorPaths("../core/src/main/res/drawable/ic_sym_doc_on_doc.xml")

        // La feuille de derrière se dessine d'un seul trait : son coin coupé, son dessus, son bord
        // gauche, et son bas qui s'arrête sous la feuille de devant.
        assertTrue(paths.any { it == "M16,8 L12,4 H5 V17 H8" })
        assertTrue(paths.any { it == "M15,7 V11 H19" })
        assertTrue(paths.any { it == "M8,7 H15 L19,11 V20 H8 Z" })
    }

    @Test
    fun `le micro du composer a une variante outline et non pleine`() {
        val file = File("../core/src/main/res/drawable/ic_sym_mic.xml")
        assertTrue("ic_sym_mic.xml manquant", file.isFile)
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val firstPath = document.getElementsByTagName("path").item(0).attributes
        assertEquals("@android:color/transparent", firstPath.getNamedItem("android:fillColor").nodeValue)
    }

    @Test
    fun `les variantes fill decoupent leur glyphe au lieu de le peindre sur le disque`() {
        listOf(
            "ic_sym_checkmark_circle_fill.xml",
            "ic_sym_xmark_circle_fill.xml",
            "ic_sym_exclamationmark_circle_fill.xml",
            "ic_sym_plus_circle_fill.xml",
            "ic_sym_minus_circle_fill.xml",
            "ic_sym_xmark_octagon_fill.xml",
            "ic_sym_exclamationmark_triangle_fill.xml",
        ).forEach { name ->
            val source = File("../core/src/main/res/drawable/$name").readText()
            assertTrue("$name doit utiliser un trou evenOdd", source.contains("android:fillType=\"evenOdd\""))
        }
    }

    @Test
    fun `le visage de la barre est la, conversation simple comprise`() {
        // La règle a changé, sur un constat : le visage **manquait** dans l'écran que la personne
        // voit en premier — une nouvelle conversation. Il n'était posé que pour le fil d'un bot
        // (`botProfile != null`), donc une conversation simple n'en portait aucun. Or le visage
        // d'Onyx existe pour les deux cas (`threadHeaderSpeaker` le donne) : la barre le montre
        // toujours, et le profil du bot ne sert plus qu'à choisir **quel** visage.
        val chat = File("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt").readText()
        assertTrue(chat.contains("avatarName = threadSpeaker.faceName,"))
        assertTrue(chat.contains("avatarFaceCode = threadSpeaker.faceCode,"))
        assertFalse(
            "le visage ne doit plus être conditionné au fil d'un bot",
            chat.contains("avatarName = threadSpeaker.faceName.takeIf"),
        )
        val speaker = File("src/main/java/com/onyx/app/ui/chat/components/ThreadSpeaker.kt").readText()
        assertTrue(
            speaker.contains(
                "botProfile = if (bot.isDefault || bot.name == OnyxProfile) OnyxProfile else bot.name",
            ),
        )
        // Et le fil simple retombe sur le visage de Onyx, pas sur rien.
        assertTrue(speaker.contains("faceName = OnyxProfile"))
    }

    private fun vectorPaths(path: String): List<String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        val nodes = document.getElementsByTagName("path")
        return (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("android:pathData").nodeValue }
    }
}
