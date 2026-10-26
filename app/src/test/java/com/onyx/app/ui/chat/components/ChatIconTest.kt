package com.onyx.app.ui.chat.components

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatIconTest {
    @Test
    fun `doc on doc contient bien le contour complet du document arriere`() {
        val paths = vectorPaths("../core/src/main/res/drawable/ic_sym_doc_on_doc.xml")

        assertTrue(paths.any { it == "M9,17 H4 V2 H11 L15,6 V7" })
        assertTrue(paths.any { it == "M11,2 V6 H15" })
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
    fun `le visage de la barre ne va qu au fil d un bot`() {
        //: `if let botFace`, et `botFace` n'existe que quand la conversation
        // a un bot (`conversation.bot.map { … }`). Une conversation simple ne porte donc **aucun**
        // visage dans la barre du haut: le titre seul.
        val chat = File("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt").readText()
        assertTrue(chat.contains("avatarName = threadSpeaker.faceName.takeIf { threadSpeaker.botProfile != null }"))
        assertTrue(chat.contains("avatarFaceCode = threadSpeaker.faceCode.takeIf { threadSpeaker.botProfile != null }"))
        // Et c'est bien le profil du bot qui décide: une conversation sans bot n'en a pas.
        val speaker = File("src/main/java/com/onyx/app/ui/chat/components/ThreadSpeaker.kt").readText()
        assertTrue(
            speaker.contains(
                "botProfile = if (bot.isDefault || bot.name == OnyxProfile) OnyxProfile else bot.name",
            ),
        )
    }

    private fun vectorPaths(path: String): List<String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        val nodes = document.getElementsByTagName("path")
        return (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("android:pathData").nodeValue }
    }
}
