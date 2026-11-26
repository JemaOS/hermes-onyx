package com.onyx.app.ui.design

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La mise en page du champ : au repos le regard de la référence, en édition le curseur visible.
 *
 * Le défaut d'origine — un champ aligné à droite dont le début reste coupé pendant l'édition — venait
 * du champ lui-même : la valeur était posée dans une boîte de la largeur du champ, sans défilement,
 * et le curseur se promenait dans un texte tronqué. Ces gardes tiennent la correction en place.
 */
class OnyxFieldEditingLayoutTest {

    private fun file(path: String): File =
        listOf(File(path), File("core/$path")).firstOrNull { it.exists() }
            ?: error("introuvable : $path")

    private val form = file("src/main/java/com/onyx/app/ui/design/OnyxForm.kt").readText()

    @Test
    fun `le champ defile pour garder le curseur visible pendant l edition`() {
        assertTrue("le champ défile : il suit le curseur", form.contains("OnyxFieldScroll.origin("))
        assertTrue("le curseur est lu dans la mise en page", form.contains("getCursorRect("))
        assertTrue("la position du texte suit chaque mise en page", form.contains("onTextLayout = { layout = it }"))
        assertTrue("le défilement repart quand la sélection bouge", form.contains("LaunchedEffect(focused, caret, layout, viewport)"))
        assertTrue("rien ne dépasse du champ", form.contains(".clipToBounds()"))
        assertTrue("le texte est mesuré sur une seule ligne, plus large que le champ", form.contains("Constraints.Infinity"))
    }

    @Test
    fun `au repos la valeur garde le regard de la reference`() {
        assertTrue("l'alignement à droite est calculé, pas dessiné", form.contains("OnyxFieldScroll.rest("))
        assertTrue("les champs alignés à gauche ne basculent pas", form.contains("alignEnd: Boolean = alignment == TextAlign.End"))
        assertTrue("le placeholder garde son alignement", form.contains("placeholderStyle.copy(textAlign = alignment)"))
    }

    @Test
    fun `les deux champs de formulaire passent par la meme valeur editable`() {
        assertTrue("la rangée de formulaire", form.contains("OnyxEditableValue("))
        // Le champ de texte passe par la **même** valeur éditable — désormais aussi quand la
        // valeur se replie sur plusieurs lignes (l'adresse longue, qu'on doit pouvoir relire).
        assertTrue("le champ de texte", form.contains("if (singleLine || wrapLines > 1) {"))
        assertTrue("le champ multiple garde son ancien dessin", form.contains("singleLine = false"))
    }

    @Test
    fun `un champ secret ne montre jamais sa valeur`() {
        assertTrue("le secret reste masqué", form.contains("if (secret) PasswordVisualTransformation() else VisualTransformation.None"))
        assertTrue("le secret se donne par la copie", form.contains("Symbols.DocOnDoc"))
        assertFalse("aucun œil ne dévoile la valeur", form.contains("revealed"))
    }
}
