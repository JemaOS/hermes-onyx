package com.onyx.app.ui.rich

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Le libellé d'un bloc de code: le langage de la clôture, en capitales, ou « code » — comme la
 * capture de référence montre « BASH » au-dessus de la commande.
 */
class CodeBlockLabelTest {

    @Test
    fun `le langage de la cloture est mis en capitales`() {
        assertEquals("BASH", codeBlockLabel("bash"))
        assertEquals("SWIFT", codeBlockLabel("Swift"))
        assertEquals("JSON", codeBlockLabel("json"))
    }

    @Test
    fun `sans langage le bloc s'appelle code`() {
        assertEquals("CODE", codeBlockLabel(null))
        assertEquals("CODE", codeBlockLabel(""))
        assertEquals("CODE", codeBlockLabel("   "))
    }
}
