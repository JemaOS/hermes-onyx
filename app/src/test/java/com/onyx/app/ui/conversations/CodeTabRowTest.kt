package com.onyx.app.ui.conversations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La rangée d'onglets de section : **Chat · Cron · Kanban · Code**.
 *
 * Elle était portée par l'écran des conversations ; celui-ci a été retiré, et c'est le tableau
 * **Kanban** qui la porte encore (son `topRow`). Les libellés, eux, ne changent pas : ils viennent
 * toujours de [ListSection], la même source pour tous les porteurs.
 *
 * « Discussions » (le titre de section de la liste) était trop long pour la rangée, où quatre
 * libellés doivent tenir **ensemble** à police 1,3× sur 360 dp : l'onglet dit « Chat », plus court.
 */
class CodeTabRowTest {

    private val root = File("src/main/java/com/onyx/app/ui")
    private val app = File(root, "OnyxApp.kt").readText()

    @Test
    fun `la rangee porte quatre onglets, Chat en tete et Code a la fin`() {
        assertEquals(
            listOf("Chat", "Cron", "Kanban", "Code"),
            ListSection.entries.map { it.title },
        )
    }

    @Test
    fun `l onglet Code ouvre son ecran depuis la rangee`() {
        assertTrue("la rangée du tableau Kanban est la même liste", app.contains("ListSection.entries.map { it.title }"))
        assertTrue(app.contains("ListSection.CODE -> navTo(Route.CODE.name)"))
        assertTrue(app.contains("Route.CODE -> codePane"))
    }
}
