package com.onyx.app.ui.setup

import com.onyx.app.data.model.SetupCode
import com.onyx.app.data.model.SetupCodeOffer
import com.onyx.app.data.model.Transport
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Les deux rangées qui collent prennent deux chemins distincts.**
 *
 * Le défaut observé : coller **une adresse seule** dans la rangée « Coller un lien de
 * configuration » répondait « Ce n'est pas un lien de configuration Onyx » — vrai, mais muet sur ce
 * qu'il fallait faire, alors que la rangée du dessus attendait exactement ce qui venait d'être
 * collé. Ici le portail d'acceptation est **celui de l'application** ([SetupCodeOffer.fromText]) :
 * un lien de configuration est reconnu, une adresse ne l'est pas — et le message dit, pour chaque
 * cas, où va ce qui est collé.
 */
class SetupPasteTest {

    /** Le portail réel : c'est ce que `onPasteCode` appelle dans l'arbre. */
    private fun accepted(text: String): Boolean = SetupCodeOffer.fromText(text) != null

    private val gatewayAddress = "https://amp-withdrawal-mary-van.trycloudflare.com"

    private val setupLink = SetupCode(
        name = "Maison",
        use = Transport.HERMES_SESSIONS,
        apiURL = "https://gw.example.com",
    ).link()

    @Test
    fun `un lien de configuration est reconnu, et ne dit rien`() {
        assertTrue("le lien doit être reconnu par le portail de l'app", accepted(setupLink))
        assertNull("un lien accepté ne laisse aucun message", SetupPaste.problemFor(setupLink, accepted(setupLink)))
    }

    @Test
    fun `une adresse seule prend l AUTRE chemin, et l ecran le dit`() {
        assertFalse("une adresse n'est pas un lien de configuration", accepted(gatewayAddress))

        val problem = SetupPaste.problemFor(gatewayAddress, accepted(gatewayAddress))

        assertNotNull("une adresse collée là doit être expliquée", problem)
        assertTrue("il dit que c'est une adresse", problem!!.contains("address"))
        assertTrue("il dit où la mettre", problem.contains("Gateway address field above"))
        assertTrue("il dit ce qu'est un lien", problem.contains("onyx://"))
    }

    @Test
    fun `un presse-papiers vide est nomme comme tel`() {
        assertEqualsText("Nothing to paste — the clipboard is empty.", SetupPaste.problemFor("   ", false))
    }

    @Test
    fun `un texte quelconque dit ce qu est un lien de configuration`() {
        val problem = SetupPaste.problemFor("bonjour", false)

        assertNotNull(problem)
        assertTrue("il nomme le cas", problem!!.startsWith("That isn't an Onyx setup link."))
        assertTrue("il dit d'où vient un lien", problem.contains("Settings › Connection › Set up another device"))
    }

    @Test
    fun `les deux rangees annoncent chacune ce qu elle accepte`() {
        assertNotEquals("les deux rangées ne disent pas la même chose", SetupPaste.LINK_ACCEPTS, SetupPaste.ADDRESS_ACCEPTS)
        assertTrue("le lien est un lien complet", SetupPaste.LINK_ACCEPTS.contains("onyx://"))
        assertTrue("l'adresse est une adresse seule", SetupPaste.ADDRESS_ACCEPTS.contains("One address only"))
    }

    private fun assertEqualsText(expected: String, actual: String?) =
        org.junit.Assert.assertEquals(expected, actual)
}
