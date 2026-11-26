package com.onyx.app.ui.design

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le collage dans un champ ne coupe plus.
 *
 * Le défaut observé : un jeton de vingt caractères collé dans un champ est arrivé à six au journal
 * du pont Ordinateur. Le champ reprenait l'écho de sa propre frappe — un effet asynchrone rejouait
 * la valeur du parent **en arrière** pendant le collage. Deux gardes le tiennent :
 *  - [OnyxFieldPaste.text] pose le presse-papier d'un coup, sans le clavier ;
 *  - [OnyxFieldSync.shouldAdopt] n'adopte que ce qui vient d'ailleurs, jamais son propre écho ;
 *  - et le champ de formulaire offre le geste « Coller » à côté de la copie.
 */
class OnyxFieldPasteTest {

    private fun file(path: String): File =
        listOf(File(path), File("core/$path")).firstOrNull { it.exists() }
            ?: error("introuvable : $path")

    private val form = file("src/main/java/com/onyx/app/ui/design/OnyxForm.kt").readText()

    @Test
    fun `un jeton de vingt caracteres colle reste vingt caracteres`() {
        val token = "0123456789abcdefghij"

        assertEquals(20, token.length)
        val pasted = OnyxFieldPaste.text("  $token\n")

        assertEquals("rien n'est mangé au collage", 20, pasted.length)
        assertEquals(token, pasted)
    }

    /**
     * **Garde-fou de longueur** — le collage ne coupe rien, quelle que soit la longueur.
     *
     * Ce n'est **pas** le sujet de la panne d'origine (le champ d'adresse n'était pas tronqué : il
     * montrait la fin de la valeur, le curseur y étant). C'est le verrou qui interdit qu'une
     * longueur se remette à rogner un jour sans qu'un test le dise — une adresse peut porter un
     * jeton, un chemin et un port.
     */
    @Test
    fun `garde-fou une adresse de quarante-neuf caracteres reste intacte`() {
        val address = "https://amp-withdrawal-mary-van.trycloudflare.com"

        assertEquals(49, address.length)
        assertEquals("rien n'est mangé au collage", address, OnyxFieldPaste.text("  $address\n"))
    }

    @Test
    fun `garde-fou une adresse de 256 caracteres ressort entiere`() {
        val address = "https://" + "a".repeat(236) + ".example.com"

        assertEquals(256, address.length)
        assertEquals(address, OnyxFieldPaste.text(address))
        assertEquals("une valeur longue ne se replie pas sur sa fin", address, OnyxFieldPaste.text("$address "))
    }

    @Test
    fun `rien au milieu nest retire`() {
        assertEquals("a b+c/d=e", OnyxFieldPaste.text("a b+c/d=e"))
        assertEquals("colle-au-milieu", OnyxFieldPaste.text("colle-au-milieu"))
    }

    @Test
    fun `lecho de sa propre frappe ne recule pas le champ`() {
        val typed = "0123456789abcdefghij"

        assertFalse(
            "l'écho de la frappe ne doit pas être rejoué",
            OnyxFieldSync.shouldAdopt(value = typed, emitted = typed, field = typed),
        )
    }

    @Test
    fun `une valeur venue dailleurs est adoptee`() {
        assertTrue(
            "un lien appliqué, un bouton Coller : on adopte",
            OnyxFieldSync.shouldAdopt(value = "colle", emitted = "ancien", field = "ancien"),
        )
        assertFalse(
            "le champ porte déjà cette valeur",
            OnyxFieldSync.shouldAdopt(value = "identique", emitted = "x", field = "identique"),
        )
    }

    @Test
    fun `le champ porte le geste Coller et la garde decho`() {
        assertTrue("la garde de l'écho est utilisée", form.contains("OnyxFieldSync.shouldAdopt("))
        assertTrue("la frappe est notée avant l'effet", form.contains("emitted = next.text"))
        assertTrue("le geste Coller existe", form.contains("Symbols.DocOnClipboard"))
        assertTrue("le presse-papier est posé d'un coup", form.contains("OnyxFieldPaste.text(clipboardText("))
    }
}
