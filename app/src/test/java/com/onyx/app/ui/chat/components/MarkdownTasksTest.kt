package com.onyx.app.ui.chat.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTasksTest {
    @Test
    fun `reconnait les cases vides et cochees du Markdown`() {
        val markdown = "- [ ] ouvert\n- [x] terminé\n- [X] majuscule"

        assertEquals(false, taskMarkerAt(markdown, markdown.indexOf("[ ]")))
        assertEquals(true, taskMarkerAt(markdown, markdown.indexOf("[x]")))
        assertEquals(true, taskMarkerAt(markdown, markdown.indexOf("[X]")))
    }

    @Test
    fun `ignore le texte entre crochets qui nest pas une tache`() {
        assertNull(taskMarkerAt("un [lien] et [y]", 4))
        assertNull(taskMarkerAt("[y]", 0))
        assertNull(taskMarkerAt("[]", 0))
        assertNull(taskMarkerAt("[x]", -1))
        assertNull(taskMarkerAt("[x]", 2))
    }

    @Test
    fun `le libelle dune tache cochee s arrete a la fin de sa ligne`() {
        // « - [x] Permits approved\n- [ ] Countertop template »: le barré ne doit pas déborder.
        val markdown = "- [x] Permits approved\n- [ ] Countertop template"
        val marker = markdown.indexOf("[x]")

        assertEquals(markdown.indexOf('\n'), taskLineEnd(markdown, marker + 3))
    }

    @Test
    fun `la derniere ligne na pas de saut de ligne`() {
        val markdown = "- [x] Cabinets ordered (ETA 09-22)"

        assertEquals(markdown.length, taskLineEnd(markdown, markdown.indexOf("[x]") + 3))
    }

    @Test
    fun `la puce nest plus dessinee devant une case`() {
        // La case remplace le marqueur: ne dessine aucune puce devant elle.
        val markdown = "Intro\n- [x] Permits approved\n- [ ] Plumbing rough-in"

        val demoted = demoteTaskListMarkers(markdown)

        assertFalse(demoted.contains("- [x]"))
        assertFalse(demoted.contains("- [ ]"))
        // Le contenu de la tâche est intact, et chaque tâche garde sa ligne.
        assertTrue(demoted.contains("[x] Permits approved  "))
        assertTrue(demoted.contains("[ ] Plumbing rough-in  "))
        // La prose autour n'est pas touchée.
        assertTrue(demoted.startsWith("Intro\n"))
    }

    @Test
    fun `une liste ordinaire garde sa puce`() {
        val markdown = "Intro\n- Countertop template\n* Quartz\n+ Labor"

        assertEquals(markdown, demoteTaskListMarkers(markdown))
    }

    @Test
    fun `l imbrication devient des espaces insecables pour ne pas faire un bloc de code`() {
        val demoted = demoteTaskListMarkers("    - [x] Nested task")

        assertFalse("quatre espaces feraient un bloc de code", demoted.startsWith("    "))
        assertTrue(demoted.startsWith("\u00A0\u00A0\u00A0\u00A0[x] Nested task"))
    }
}
