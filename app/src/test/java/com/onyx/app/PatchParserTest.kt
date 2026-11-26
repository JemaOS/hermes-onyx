package com.onyx.app

import com.onyx.app.ui.rich.PatchLine
import com.onyx.app.ui.rich.looksLikePatch
import com.onyx.app.ui.rich.patchFiles
import com.onyx.app.ui.rich.patchSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vérifie la lecture d'un diff unifié : c'est ce qui décide ce qu'on montre d'un changement
 * proposé par l'agent, et une erreur y ferait lire du code faux.
 */
class PatchParserTest {

    private val diff = """
        diff --git a/app/Main.kt b/app/Main.kt
        index 1111..2222 100644
        --- a/app/Main.kt
        +++ b/app/Main.kt
        @@ -1,3 +1,4 @@
         class Main
        -val old = 1
        +val new = 2
        +val more = 3
    """.trimIndent()

    @Test
    fun `un fichier est lu avec son chemin et ses compteurs`() {
        val files = patchFiles(diff)

        assertEquals(1, files.size)
        assertEquals("app/Main.kt", files[0].path)
        assertEquals(2, files[0].added)
        assertEquals(1, files[0].removed)
    }

    @Test
    fun `les lignes gardent leur nature`() {
        val lines = patchFiles(diff).first().lines

        assertTrue(lines.any { it.kind == PatchLine.Kind.HUNK })
        assertTrue(lines.any { it.kind == PatchLine.Kind.ADDED })
        assertTrue(lines.any { it.kind == PatchLine.Kind.REMOVED })
        assertTrue(lines.any { it.kind == PatchLine.Kind.CONTEXT })
    }

    @Test
    fun `les en-tetes de difflib sont coupes a la tabulation`() {
        val difflib = """
            --- old.py	2026-01-02 10:00:00
            +++ new.py	2026-01-02 10:00:01
            @@ -1 +1 @@
            -a
            +b
        """.trimIndent()

        assertEquals("new.py", patchFiles(difflib).first().path)
    }

    @Test
    fun `le resume compte les fichiers et les lignes`() {
        assertEquals("1 file · +2 −1", patchSummary(patchFiles(diff)))
    }

    @Test
    fun `un texte ordinaire n'est pas un diff`() {
        assertFalse(looksLikePatch("Bonjour, voici la réponse."))
        assertTrue(looksLikePatch(diff))
    }
}
