package com.onyx.app.ui.sidebar

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le crayon de « Conversations » : l'entrée de la multi-sélection.**
 *
 * Le mode existait déjà (`SidebarSelectionState`, la barre du bas avec « Annuler », « Tout
 * sélectionner », « Archiver », « Supprimer »), mais il ne s'ouvrait que par le rond de la tête du
 * volet — un geste qui ne se lit pas comme « cette liste ». Le crayon vit désormais à côté du titre
 * de la section, à la même place et de la même forme que celui de « Raccourcis ».
 *
 * Ce que ce verrou empêche : que le crayon soit posé dans un en-tête qui ne connaît pas l'état de
 * sélection (il ne ferait rien), ou qu'il reste affiché pendant la sélection (deux contrôles pour
 * le même geste, alors que la barre du bas porte déjà tout).
 */
class SidebarSelectionPencilTest {

    private val sidebar = File("src/main/java/com/onyx/app/ui/sidebar/ChatSidebar.kt").readText()

    @Test
    fun `le crayon vit dans l en tete de la section des conversations`() {
        val section = sidebar.substringAfter("item(key = \"section\")")
            .substringBefore("SidebarTab.BOTS")

        assertTrue("la section porte le crayon", section.contains("SectionActionButton("))
        assertTrue("avec le symbole du crayon", section.contains("symbol = Symbols.Pencil"))
        assertTrue("et un nom pour le lecteur d'écran", section.contains("label = \"Select conversations\""))
    }

    @Test
    fun `le crayon ouvre la selection qui existe deja`() {
        val section = sidebar.substringAfter("item(key = \"section\")")
            .substringBefore("SidebarTab.BOTS")

        // Il appelle le **même** état que le rond de la tête : une seule machine à états.
        assertTrue("il démarre la sélection", section.contains("selectEntry.start()"))
        assertTrue(
            "sur l'état du volet, pas une copie",
            section.contains("val selectEntry = selection?.takeIf { !it.isSelecting }"),
        )
    }

    @Test
    fun `le crayon s efface pendant la selection`() {
        val section = sidebar.substringAfter("item(key = \"section\")")
            .substringBefore("SidebarTab.BOTS")

        // Pendant la sélection, la barre du bas dit déjà tout (compte, tout sélectionner, annuler,
        // supprimer) : un second contrôle ici répéterait la même chose.
        assertTrue(
            "il n'est dessiné que hors sélection",
            section.contains("takeIf { !it.isSelecting }"),
        )
        assertTrue(
            "et la barre du bas reste la seule pendant la sélection",
            sidebar.contains("if (selection != null && selection.isSelecting && tab == SidebarTab.CHATS)"),
        )
    }
}