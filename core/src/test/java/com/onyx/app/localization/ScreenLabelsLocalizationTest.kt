package com.onyx.app.localization

import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Libellés des écrans relevés par l'audit de localisation (voir LOCALIZATION-AUDIT.md) :
 * états vides sans Dashboard, visage d'un bot, exemples des horaires, terminal et voix.
 *
 * Les caractères accentués et les gabarits `%1$s` sont vérifiés séparément : c'est ce qui casse
 * en premier quand une traduction est recopiée à la main.
 */
class ScreenLabelsLocalizationTest {
    private val fr = Locale.FRANCE

    @Test
    fun `les etats vides sans Dashboard parlent francais`() {
        assertEquals("Aucun gateway configuré", OnyxLocalization.translate("No gateway configured", fr))
        assertEquals(
            "Les tâches cron vivent sur le gateway Hermes. Configurez le Dashboard Hermes ou l’API Hermes dans Réglages.",
            OnyxLocalization.translate(
                "Cron jobs live on the Hermes gateway. Set up the Hermes Dashboard or the Hermes API in Settings.",
                fr,
            ),
        )
        assertEquals(
            "Le tableau kanban fait partie du Dashboard Hermes. Ajoutez l’adresse et les identifiants de votre Dashboard Hermes dans Réglages.",
            OnyxLocalization.translate(
                "The kanban board is part of the Hermes Dashboard. Add your Hermes Dashboard address and login in Settings.",
                fr,
            ),
        )
    }

    @Test
    fun `le visage d'un bot dit ou il est conserve`() {
        assertEquals(
            "Conservé sur votre serveur Hermes, pour que Onyx l’affiche aussi sur vos autres appareils.",
            OnyxLocalization.translate(
                "Kept on your Hermes server, so Onyx on your other devices shows it too.",
                fr,
            ),
        )
        assertEquals(
            "Conservé sur ce téléphone ; cette connexion n’a pas d’identifiants de serveur Hermes.",
            OnyxLocalization.translate("Kept on this phone; this connection has no Hermes server login.", fr),
        )
    }

    @Test
    fun `les exemples des horaires d'un bot suivent la langue`() {
        assertEquals("Quoi porter", OnyxLocalization.translate("What to wear", fr))
        assertEquals(
            "Consulte la météo et mon agenda, et dis-moi quoi porter",
            OnyxLocalization.translate("Check the weather and my calendar, and tell me what to wear", fr),
        )
    }

    @Test
    fun `le terminal et la voix ne laissent pas de phrase anglaise`() {
        assertEquals("la connexion s’est fermée.", OnyxLocalization.translate("the connection closed.", fr))
        assertEquals(
            "Voici votre Hermes, qui lit les réponses avec sa propre voix.",
            OnyxLocalization.translate("This is your Hermes, reading replies out with its own voice.", fr),
        )
        assertEquals(
            ", qu’est-ce qui utilise le port 8880 ?",
            OnyxLocalization.translate(", what's using port 8880?", fr),
        )
    }

    @Test
    fun `la phrase du gabarit d'horaire garde son mot et suit la langue`() {
        // L'exemple affiché sous un préfixe parlé : le mot est à l'utilisateur, la phrase à Onyx.
        val english = "Claude" + OnyxLocalization.translate(", what's using port 8880?", Locale.ENGLISH)
        val french = "Claude" + OnyxLocalization.translate(", what's using port 8880?", fr)
        assertEquals("Claude, what's using port 8880?", english)
        assertEquals("Claude, qu’est-ce qui utilise le port 8880 ?", french)
    }

    @Test
    fun `en anglais les nouveaux libelles restent inchanges`() {
        val keys = listOf(
            "No gateway configured",
            "Cron jobs live on the Hermes gateway. Set up the Hermes Dashboard or the Hermes API in Settings.",
            "The kanban board is part of the Hermes Dashboard. Add your Hermes Dashboard address and login in Settings.",
            "Kept on your Hermes server, so Onyx on your other devices shows it too.",
            "Kept on this phone; this connection has no Hermes server login.",
            "What to wear",
            "Check the weather and my calendar, and tell me what to wear",
            "the connection closed.",
            "This is your Hermes, reading replies out with its own voice.",
            ", what's using port 8880?",
        )
        keys.forEach { key ->
            assertEquals("Repli anglais de « $key »", key, OnyxLocalization.translate(key, Locale.ENGLISH))
        }
    }

    @Test
    fun `les en tetes de section des Reglages parlent francais`() {
        // Deux en-têtes de `OnyxFormSection` restaient en anglais au milieu des autres (« PHONE »,
        // « ACCOUNT »), relevés sur 1.0.0+546, 411 dp.
        assertEquals("TÉLÉPHONE", OnyxLocalization.translate("Phone", fr).uppercase())
        assertEquals("COMPTE", OnyxLocalization.translate("Account", fr).uppercase())
        // Et les autres en-têtes, déjà français, ne bougent pas.
        assertEquals("PASSERELLE", OnyxLocalization.translate("Gateway", fr).uppercase())
        assertEquals("VOIX", OnyxLocalization.translate("Voice", fr).uppercase())
        // En anglais, rien ne change.
        assertEquals("Phone", OnyxLocalization.translate("Phone", Locale.ENGLISH))
        assertEquals("Account", OnyxLocalization.translate("Account", Locale.ENGLISH))
    }

    @Test
    fun `les accents arrivent intacts dans le texte rendu`() {
        // Chaque traduction porte ses accents écrits tels quels, pas une translittération ASCII.
        val fragments = linkedMapOf(
            "No gateway configured" to "configuré",
            "Cron jobs live on the Hermes gateway. Set up the Hermes Dashboard or the Hermes API in Settings." to
                "tâches cron",
            "The kanban board is part of the Hermes Dashboard. Add your Hermes Dashboard address and login in Settings." to
                "l’adresse",
            "Kept on your Hermes server, so Onyx on your other devices shows it too." to "Conservé",
            "Kept on this phone; this connection has no Hermes server login." to "téléphone",
            "Check the weather and my calendar, and tell me what to wear" to "météo",
            "the connection closed." to "fermée",
            "This is your Hermes, reading replies out with its own voice." to "réponses",
            ", what's using port 8880?" to "qu’est-ce",
        )
        fragments.forEach { (key, fragment) ->
            val rendered = OnyxLocalization.translate(key, fr)
            assertTrue("« $fragment » attendu dans : $rendered", rendered.contains(fragment))
        }

        // Aucune séquence d'octets mal décodée : les caractères sont rendus, pas aplatis.
        val all = fragments.keys.joinToString(" ") { OnyxLocalization.translate(it, fr) }
        listOf("Ã", "Â", "�").forEach { mojibake ->
            assertFalse("texte mal décodé ($mojibake) : $all", all.contains(mojibake))
        }
    }

    @Test
    fun `les gabarits de format gardent leurs emplacements`() {
        // Un libellé traduit doit garder le nombre et l'ordre de ses %n$s, sinon String.format lève.
        assertEquals(
            "Rien dans À faire",
            OnyxLocalization.format("Nothing in %1\$s", OnyxLocalization["To do"], locale = fr),
        )
        assertEquals(
            "Enregistré sur le gateway sous my_secret",
            OnyxLocalization.format("Stored on the gateway as %1\$s", "my_secret", locale = fr),
        )
        assertEquals(
            "Les autres l’appellent @ada. Son identifiant reste @ada.",
            OnyxLocalization.format(
                "Others call it @%1\$s. Its handle stays @%1\$s.",
                "ada",
                locale = fr,
            ),
        )
        assertEquals(
            "4k sur 8k jetons (42 %)",
            OnyxLocalization.format("%1\$s of %2\$s tokens (%3\$d%%)", "4k", "8k", 42, locale = fr),
        )
    }

    @Test
    fun `l'etat vide localise sa phrase comme son titre`() {
        val form = listOf(File("src/main/java/com/onyx/app/ui/design/OnyxForm.kt"), File("core/src/main/java/com/onyx/app/ui/design/OnyxForm.kt"))
            .firstOrNull { it.exists() }
            ?: error("OnyxForm.kt introuvable")
        val source = form.readText()
        assertTrue(source.contains("localizeDescription: Boolean = true"))
        assertTrue(source.contains("localize = localizeDescription"))
        // La description d'un état vide est un libellé de Onyx : elle ne doit plus être figée.
        assertFalse(
            "la description de OnyxEmptyState est repassée en localize = false",
            source.contains("description?.let { text ->\n            BasicText(\n                text = text,\n                localize = false"),
        )
    }
}
