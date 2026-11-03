package com.onyx.app.ui.record

import com.onyx.app.data.net.RecordLease
import com.onyx.app.data.net.RecordReview
import com.onyx.app.localization.OnyxLocalization
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **« Montrer une fois »** sans Compose et sans réseau : les règles que l'écran ne doit pas perdre.
 *
 * Ce que ce fichier tient :
 *
 * 1. **l'entrée n'existe que si elle peut mener quelque part** — capacité prouvée **et** pont
 *    configuré ; sinon rien, pas de bouton mort ;
 * 2. **le chronomètre se lit** — « 9 min 12 s », jamais un nombre nu ; et les dix minutes sont le
 *    plafond du lot ;
 * 3. **les étapes du brouillon**, avec celles qui sont **à relire**, que la raison vienne du
 *    serveur (code) ou du Markdown local ;
 * 4. **l'adresse du pont** est réduite à `hôte[:port]`, la seule forme que le greffon accepte ;
 * 5. **rien ne promet une capture d'images** : le source de l'écran ne parle ni de pixels ni
 *    d'audio, et la feuille dit que les gestes seuls sont gardés.
 */
class RecordScreenTest {

    private val screen = File("src/main/java/com/onyx/app/ui/record/RecordScreen.kt").readText()
    private val controller = File("src/main/java/com/onyx/app/ui/record/RecordController.kt").readText()
    private val computer = File("src/main/java/com/onyx/app/ui/computer/ComputerScreen.kt").readText()

    // ---- 1. L'entrée conditionnelle ---------------------------------------------------------

    @Test
    fun `l entree n est dessinee que si la capacite et le pont sont la`() {
        assertTrue(recordEntryVisible(serverAllowsRecording = true, bridgeConfigured = true))
        assertFalse("sans capacité, rien", recordEntryVisible(false, true))
        assertFalse("sans pont, rien", recordEntryVisible(true, false))
        assertFalse(recordEntryVisible(false, false))

        // Dans l'écran Ordinateur : la ligne est sous `if (recordAllowed)`, et `recordAllowed` lit
        // la capacité `RECORD` **et** la phase du pont. Aucun autre chemin ne l'ajoute.
        assertTrue(computer.contains("if (recordAllowed) {"))
        assertTrue(computer.contains("MenuItem(\"Show me once\""))
        assertTrue(computer.contains("ServerCapability.RECORD"))
        assertTrue(computer.contains("phase == ComputerBridgeClient.Phase.LIVE"))
        assertTrue(computer.contains("recordEntryVisible("))
        // Pas de ligne grisée : l'entrée est ajoutée ou elle ne l'est pas.
        assertFalse(computer.contains("Show me once\", Symbols.HandRaised, enabled = false"))
    }

    // ---- 2. Le chronomètre -----------------------------------------------------------------

    @Test
    fun `le chronometre se lit en minutes et secondes`() {
        assertEquals("10 min 0 s", recordClockText(600_000L))
        assertEquals("9 min 12 s", recordClockText(552_000L))
        assertEquals("45 s", recordClockText(45_400L))
        assertEquals("0 s", recordClockText(0L))
        // Une échéance dépassée ne s'écrit pas en négatif.
        assertEquals("0 s", recordClockText(-5_000L))
    }

    @Test
    fun `le plafond est de dix minutes`() {
        assertEquals(600_000L, com.onyx.app.data.skill.SkillRecorder.MAX_DURATION_MS)
        assertTrue(screen.contains("stops on its own after ten minutes"))
    }

    // ---- 3. L'aperçu du brouillon ----------------------------------------------------------

    @Test
    fun `les etapes se lisent, et celles a relire portent leur raison`() {
        val markdown = """
            # Créer un dossier

            Reproduire les 3 étapes enregistrées, dans l'ordre où elles ont été faites.
            1. Tap "Nouveau dossier"
            2. Type "Rapport 2026"
            3. Tap at 51% / 8% of the window — à relire : libellé absent

            > À relire : l'étape 3.
        """.trimIndent()
        val steps = recordStepLines(markdown)

        assertEquals(3, steps.size)
        assertEquals(1, steps[0].number)
        assertEquals("Tap \"Nouveau dossier\"", steps[0].text)
        assertNull(steps[0].review)
        assertEquals("Tap at 51% / 8% of the window", steps[2].text)
        assertEquals("libellé absent", steps[2].review)
        // La note finale « > À relire » n'est pas une étape.
        assertFalse(steps.any { it.text.startsWith(">") })
    }

    @Test
    fun `une raison venue du serveur est traduite par le catalogue`() {
        val steps = recordStepLines(
            "1. Tap \"Valider\"\n2. Type •••• — secret, non enregistré",
            review = listOf(RecordReview(step = 2, reason = "secret_field", line = 2)),
        )
        // Le Markdown du convertisseur ne cite pas le secret dans « À relire » : c'est le tableau
        // `review[]` qui le dit, et l'écran le traduit.
        assertEquals("champ masqué", steps[1].review)
        assertEquals("À relire · champ masqué", OnyxLocalization.format("To review · %1\$s", steps[1].review))
    }

    @Test
    fun `un brouillon vide le dit, il ne fabrique pas d etapes`() {
        assertTrue(recordStepLines("# Démonstration vide\n\n_Aucune étape._\n").isEmpty())
    }

    // ---- 4. L'adresse du pont ---------------------------------------------------------------

    @Test
    fun `l adresse du pont est reduite a hote et port`() {
        assertEquals("192.168.1.24:8765", RecordController.recordBridgeEndpoint("192.168.1.24:8765"))
        assertEquals("192.168.1.24:8765", RecordController.recordBridgeEndpoint("ws://192.168.1.24:8765/ws"))
        assertEquals("pont.exemple.fr", RecordController.recordBridgeEndpoint("https://pont.exemple.fr/ordinateur"))
        // Ce que le greffon refuserait ne part pas : ni identifiant, ni espace, ni requête.
        assertNull(RecordController.recordBridgeEndpoint(""))
        assertNull(RecordController.recordBridgeEndpoint("hote:8765?jeton=1"))
        assertNull(RecordController.recordBridgeEndpoint("utilisateur@hote:8765"))
    }

    @Test
    fun `le nom d un skill est un dossier en minuscules a traits d union`() {
        assertEquals("creer-un-dossier", RecordController.recordSkillName("Créer un dossier"))
        assertEquals("facture-2026", RecordController.recordSkillName("  Facture — 2026  "))
        assertEquals("demonstration", RecordController.recordSkillName("!!!"))
    }

    // ---- 5. Ce que l'écran ne fait pas -------------------------------------------------------

    @Test
    fun `aucune capture d image ni de son n est promesse`() {
        assertFalse("aucun pixel capturé", screen.contains("Bitmap"))
        assertFalse("aucune capture d'écran", screen.contains("screenshot", ignoreCase = true))
        assertFalse("aucun micro", screen.contains("AudioRecord"))
        assertTrue(
            "l'écran dit ce qu'il garde",
            screen.contains("no image, no sound, no password"),
        )
        // Et le contrôleur ne tient aucun champ pour une image.
        assertFalse(controller.contains("Bitmap"))
        assertFalse(controller.contains("ByteArray"))
    }

    @Test
    fun `le secret passe par le drapeau, jamais par un texte masque`() {
        // Le drapeau est celui de l'utilisateur (le pont ne décrit pas les champs) : c'est la
        // seule façon honnête de ne pas enregistrer un mot de passe tapé dans une session du PC.
        assertTrue(computer.contains("secret = typedIsSecret"))
        assertTrue(computer.contains("noteType(text, secret)"))
        assertTrue(controller.contains("recorder.type(text, secret)"))
    }

    @Test
    fun `les phrases des etats existent en francais`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.FRANCE)
        try {
            assertEquals(
                "Ce serveur ne sait pas enregistrer une démonstration (route absente).",
                OnyxLocalization["This server can't record a demonstration (route missing)."],
            )
            assertEquals(
                "Ce serveur ne sait pas arrêter un enregistrement (route absente).",
                OnyxLocalization["This server can't close a recording (route missing)."],
            )
            assertEquals(
                "Ce serveur ne sait pas transformer une démonstration en skill (route absente). " +
                    "Le journal reste sur cet appareil.",
                OnyxLocalization[
                    "This server can't turn a demonstration into a skill (route missing). " +
                        "The journal stays on this device.",
                ],
            )
            assertEquals(
                "Le pont n’est pas configuré : il n’y a rien à montrer.",
                OnyxLocalization["The bridge is not configured: there is nothing to demonstrate."],
            )
            assertEquals(
                "L’écran du PC n’est pas encore ouvert.",
                OnyxLocalization["The computer screen is not open yet."],
            )
            assertEquals("Montrer une fois", OnyxLocalization["Show me once"])
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `un bail est un état, pas une exception`() {
        val lease = RecordLease(
            sessionId = "rec_01",
            startedAt = 1_730_650_000.0,
            deadlineAt = 1_730_650_600.0,
            maxSeconds = 600,
            graceSeconds = 120,
        )
        assertFalse(lease.adopted)
        assertEquals(600, lease.maxSeconds)
    }
}
