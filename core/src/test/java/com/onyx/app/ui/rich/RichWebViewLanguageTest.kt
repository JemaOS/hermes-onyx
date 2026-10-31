package com.onyx.app.ui.rich

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La seule chose qui, dans la page embarquée (KaTeX, Mermaid), dépend de la langue : la langue
 * déclarée du document. Elle était écrite en dur ; elle suit maintenant le choix de l'app, comme
 * le reste du rendu. Le contenu lui-même (formules, diagrammes) ne dépend pas de la langue.
 */
class RichWebViewLanguageTest {

    private val template = File("src/main/assets/rich/render.html").readText()
    private val web = File("src/main/java/com/onyx/app/ui/rich/RichWebView.kt").readText()

    @Test
    fun `la page embarquee declare la langue de l app, pas une langue en dur`() {
        assertTrue(template.contains("<html lang=\"__LANG__\">"))
        assertFalse("la langue ne doit plus être écrite en dur", template.contains("lang=\"fr\""))
        assertTrue(
            "le gabarit doit être rempli avec la langue choisie",
            web.contains(".replace(\"__LANG__\", com.onyx.app.localization.OnyxLanguage.preferred.language)"),
        )
    }
}
