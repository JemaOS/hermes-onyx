package com.onyx.app.ui.chat

import com.onyx.app.ui.design.ChatTab
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou de source du lot « onglets de la plaque » — les quatre colonnes, la pastille
 * contenue, et le visage qui ne se teinte plus.**
 *
 * Les chiffres (60 dp de cellule, 8 dp de dégagement, 140 dp de plaque) sont éprouvés sans appareil
 * par `PlaqueOngletsVerrouTest`, dans `core`. Ici on vérifie que **les écrans s'en servent
 * vraiment** — la seule chose qu'un test de source puisse dire, et elle est nécessaire : une table
 * juste que personne ne lit ne change aucun pixel.
 *
 * Ce verrou **mord** : c'est lui qui attrape « les colonnes ne sont plus égales » (une poignée
 * remise dans la barre, une largeur écrite pour l'un des quatre) et « la pastille franchit son
 * bord » (le centrage de la rangée retiré, le plancher de cellule ôté).
 */
class OngletsPlaqueWiringTest {

    private fun read(path: String): String {
        val file = listOf(path, "../$path").map(::File).firstOrNull { it.isFile }
            ?: error("fichier introuvable : $path")
        return file.readText()
    }

    private val screen = read("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt")
    private val bar = read("../core/src/main/java/com/onyx/app/ui/design/ChatTabBar.kt")

    // — 1 · quatre repères, quatre colonnes ÉGALES —

    @Test
    fun `la barre ne recoit aucune poignee, les quatre colonnes restent egales`() {
        assertTrue(
            "l'écran dessine les quatre repères de la planche",
            screen.contains("tabs = ChatTab.drawn,"),
        )
        assertEquals("la barre porte quatre repères, pas cinq", 4, ChatTab.drawn.size)
        val call = screen.substringAfter("tabs = ChatTab.drawn,").substringBefore("onSelect = {")
        assertFalse(
            "une poignée DANS la barre prend la cible d'une colonne avant les repères : « Chat " +
                "collé à gauche, les autres serrés au centre » — c'est le défaut mesuré",
            call.contains("handle ="),
        )
    }

    @Test
    fun `chaque repere prend une part egale, et la cellule a son plancher`() {
        val tab = bar
            .substringAfter("tabs.forEachIndexed { index, tab ->")
            .substringBefore("BasicText(")
        assertTrue(
            "les quatre repères se partagent la rangée à poids égal — jamais une largeur écrite",
            tab.contains(".weight(1f)"),
        )
        assertTrue(
            "sur la plaque, la cellule a le plancher de la planche (`.navi{height:60px}`)",
            tab.contains("Modifier.heightIn(min = PlaqueGeometry.TabCellHeight)"),
        )
        assertTrue(
            "et l'écart icône-libellé reste le jeton de la maison, jamais un chiffre local",
            tab.contains("Arrangement.spacedBy(OnyxSpace.IconLabelGap)"),
        )
    }

    @Test
    fun `la rangee d onglets centre sa barre dans ses 76 dp`() {
        val row = screen
            .substringAfter(".height(PlaqueGeometry.TabRowHeight)")
            .substringBefore("ChatTabBar(")
        assertTrue(
            "sans centrage, la pastille monte jusqu'au filet et laisse tout le blanc sous elle",
            row.contains("verticalArrangement = Arrangement.Center,"),
        )
    }

    // — 2 · le visage désigne le bot, il ne teinte pas la commande —

    @Test
    fun `le disque du visage ne prend plus la couleur d'identite du bot`() {
        val face = bar
            .substringAfter("if (faceContent != null && faceIndex == index)")
            .substringBefore("faceContent()")
        assertFalse(
            "la couleur d'identité du bot DÉSIGNE le bot, elle ne colore pas la commande : peinte " +
                "ici, elle donnait au quatrième repère le « cercle violet » relevé",
            face.contains("background(accent)"),
        )
        assertTrue(
            "l'anneau clair de la planche reste : c'est lui qui détache le visage du verre",
            face.contains("PlaqueGeometry.FaceRing"),
        )
        // Le visage lui-même porte sa couleur : c'est son identité, et elle reste.
        assertTrue("le visage de l'écran reste celui de la conversation", screen.contains("BotLook("))
    }
}
