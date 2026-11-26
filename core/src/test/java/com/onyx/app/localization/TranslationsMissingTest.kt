package com.onyx.app.localization

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Les libellés qui manquaient en français.**
 *
 * L'app écrit ses chaînes **en anglais** dans le code et les traduit par le dictionnaire. Une chaîne
 * absente du dictionnaire s'affiche **en anglais**, même dans une interface française — c'est ce
 * qui est arrivé sur l'écran d'édition d'un bot, où « WHAT IT DOES » et « NAME » s'affichaient sous
 * des titres français.
 *
 * Ces libellés-là ont une **valeur qui dépend d'un paramètre** (« Version 1.0.0+1123 »), donc ils
 * ne peuvent pas être des clés : à l'exécution l'interpolation est déjà faite. Ils sont traduits par
 * des **motifs**, qui voient la forme finale.
 *
 * Ce test verrouille que :
 *
 *  1. **chaque forme est bien traduite** ;
 *  2. **la partie variable est reprise à l'identique** — le motif ne réécrit jamais le paramètre ;
 *  3. **en anglais, rien ne change** — la traduction est française, l'anglais reste l'anglais ;
 *  4. **un motif ne mange pas une chaîne qui a sa propre entrée** (« Delete this server? » garde sa
 *     traduction, parce que le dictionnaire est consulté **avant** les motifs).
 */
class TranslationsMissingTest {

    @Test
    fun `chaque forme est traduite en francais`() {
        assertEquals(
            "Version 1.0.0+1123",
            OnyxLocalization.translate("Version 1.0.0+1123", Locale.FRANCE),
        )
        assertEquals(
            "Priorité Haute",
            OnyxLocalization.translate("Priorité Haute", Locale.FRANCE),
        )
        assertEquals(
            "Modèle → hermes-agent",
            OnyxLocalization.translate("Modèle → hermes-agent", Locale.FRANCE),
        )
        assertEquals(
            "Supprimer coder ?",
            OnyxLocalization.translate("Supprimer coder ?", Locale.FRANCE),
        )
        assertEquals(
            "Toutes les 2 heures",
            OnyxLocalization.translate("Toutes les 2 heures", Locale.FRANCE),
        )
    }

    @Test
    fun `la partie variable est reprise a l identique`() {
        // Le paramètre ne doit pas être réécrit : « Version 1.0.0+1123 » garde son numéro.
        assertEquals(
            "Version 9.9.9-test",
            OnyxLocalization.translate("Version 9.9.9-test", Locale.FRANCE),
        )
        assertEquals(
            "Priorité Basse",
            OnyxLocalization.translate("Priorité Basse", Locale.FRANCE),
        )
    }

    @Test
    fun `en anglais rien ne change`() {
        listOf(
            "Version 1.0.0+1123",
            "Priority High",
            "Model → hermes-agent",
            "Delete coder?",
            "Every 2 hours",
        ).forEach { cle ->
            assertEquals(
                "« $cle » ne devrait pas être traduit en anglais",
                cle,
                OnyxLocalization.translate(cle, Locale.ENGLISH),
            )
        }
    }

    @Test
    fun `un motif ne mange pas une chaine qui a sa propre entree`() {
        // Le dictionnaire est consulté **avant** les motifs : ces trois chaînes gardent leur
        // traduction, et le motif « Delete … ? » ne les attrape pas.
        assertEquals(
            "Supprimer ce serveur ?",
            OnyxLocalization.translate("Supprimer ce serveur ?", Locale.FRANCE),
        )
        assertEquals(
            "Supprimer cette session ?",
            OnyxLocalization.translate("Supprimer cette session ?", Locale.FRANCE),
        )
    }

    @Test
    fun `une cle absente du dictionnaire reste en anglais, et c est visible`() {
        // Le rappel du mécanisme : sans entrée dans le dictionnaire, l'anglais passe tel quel. C'est
        // ce qui rend le trou visible — et c'est pour cela que les motifs doivent le couvrir.
        val absente = "Une chaîne qui n'existe pas dans le dictionnaire"
        assertEquals(absente, OnyxLocalization.translate(absente, Locale.FRANCE))
        assertFalse(
            onyxMissingFrenchPatterns.any { (motif, _) -> motif.matches(absente) },
        )
    }
}
