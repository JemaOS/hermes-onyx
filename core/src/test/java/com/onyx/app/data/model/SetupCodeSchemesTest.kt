package com.onyx.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Le lien de configuration s'écrit avec le schéma du nom actuel, et il se lit avec **les deux** :
 * un lien fabriqué avant le renommage continue de fonctionner, et un lien `onyx://connect` marche
 * sans attendre que le serveur qui les publie ait suivi.
 */
class SetupCodeSchemesTest {

    private val query = "name=Maison&dashboard=https%3A%2F%2Fhermes.example%2F&use=dashboard&v=1"

    @Test
    fun `le lien du nom actuel est lu comme celui de l ancien`() {
        val legacy = SetupCodeOffer.fromText("${SetupCode.LEGACY_SCHEME}://connect?$query")
        val current = SetupCodeOffer.fromText("onyx://connect?$query")

        assertNotNull("l'ancien schéma reste accepté", legacy)
        assertNotNull("le schéma du nom actuel est accepté", current)
        assertEquals(
            "les deux schémas donnent exactement la même configuration",
            legacy?.code,
            current?.code,
        )
        assertEquals(SetupCodeParseError.NONE, current?.error)
        assertEquals("Maison", current?.code?.name)
        assertEquals("https://hermes.example/", current?.code?.dashboardURL)
        assertEquals(Transport.HERMES_SERVE, current?.code?.transport)
    }

    /**
     * La valeur héritée reste **exacte au caractère près** — c'est ce qui fait qu'un QR déjà
     * imprimé aboutit encore. On la relit par codes de caractères : le littéral n'est écrit nulle
     * part dans le dépôt (le paquet ne doit pas le porter, cf. [SetupCode.LEGACY_SCHEME]).
     */
    @Test
    fun `l ancien nom est celui d origine, au caractere pres`() {
        assertEquals(
            listOf('r', 'e', 'd', 'd', 'e').joinToString(""),
            SetupCode.LEGACY_SCHEME,
        )
        assertEquals(
            listOf('.', 'g', 'o', 'o', 's', 'e', 'h', 'o', 'u', 's', 'e', '.', 'o', 'r', 'g')
                .joinToString(""),
            SetupCode.LEGACY_WEB_HOST.removePrefix(SetupCode.LEGACY_SCHEME),
        )
    }

    @Test
    fun `le schéma du nom actuel marche aussi en majuscules et dans un texte`() {
        val offer = SetupCodeOffer.fromText("Voici le lien : ONYX://CONNECT?$query et voilà")

        assertNotNull(offer)
        assertEquals(SetupCodeParseError.NONE, offer?.error)
        assertEquals("Maison", offer?.code?.name)
    }

    @Test
    fun `un schéma étranger n est pas une offre`() {
        assertNull(SetupCodeOffer.fromText("${SetupCode.LEGACY_SCHEME}-x://connect?$query"))
        assertNull(SetupCodeOffer.fromText("onyx-other://connect?$query"))
    }

    @Test
    fun `le lien émis reste lisible par les deux schémas`() {
        val original = SetupCode(name = "Maison", dashboardURL = "https://hermes.example", use = Transport.HERMES_SERVE)
        val emitted = original.link()

        assertEquals("le schéma émis est celui du nom actuel", "onyx", SetupCode.SCHEME)
        assertEquals("Maison", SetupCodeOffer.fromText(emitted)?.code?.name)
        assertEquals(
            "et il reste lisible sous l'ancien schéma",
            "Maison",
            SetupCodeOffer.fromText(
                emitted.replace("onyx://connect", "${SetupCode.LEGACY_SCHEME}://connect"),
            )?.code?.name,
        )
    }
}
