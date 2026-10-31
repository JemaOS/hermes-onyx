package com.onyx.app.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou : l'interface nomme le modèle qui a servi le tour, ou elle le dit.**
 *
 * Un tour servi par un **repli** (la passerelle bascule en local quand le fournisseur configuré
 * échoue — `fallback_providers`) doit afficher le modèle **en force**, jamais le modèle configuré
 * présenté comme s'il avait répondu. Et quand un repli est à l'œuvre, l'interface doit le
 * **déclarer** (le modèle remplacé + le fournisseur réel), pas le masquer.
 *
 * Le texte de la déclaration est celui que la passerelle écrit sur le flux, tel quel :
 * `⚠️ Model fallback: deepseek/deepseek-v4.1-flash via kilocode unavailable (authentication
 * failed); using ornith1.5-9b-3mper0rr-obliterated via lmstudio.`
 *
 * **Morsure** : faire rendre au sélecteur de nom le modèle **configuré** (l'ancien comportement)
 * fait rougir les tests 3 et 4 ; faire disparaître la déclaration fait rougir le test 4.
 */
class VerrouModeleEnForceTest {

    /** La déclaration réelle du journal de la passerelle, mot pour mot. */
    private val notice =
        "⚠️ Model fallback: deepseek/deepseek-v4.1-flash via kilocode unavailable " +
            "(authentication failed); using ornith1.5-9b-3mper0rr-obliterated via lmstudio."

    private val declared: FallbackDeclaration get() = FallbackNotice.parse(notice)!!

    // ---- La déclaration se lit sur le flux ------------------------------------------------------

    @Test
    fun laDeclarationDeRepliEstLueSurLeFlux() {
        val repli = FallbackNotice.parse(notice)
        assertNotNull("le flux déclare le repli : il doit être lu", repli)
        repli!!
        assertEquals("deepseek/deepseek-v4.1-flash", repli.configuredModel)
        assertEquals("kilocode", repli.configuredProvider)
        assertEquals("ornith1.5-9b-3mper0rr-obliterated", repli.servedModel)
        assertEquals("lmstudio", repli.servedProvider)
        assertTrue("un modèle servi par lmstudio tourne en local", repli.servedIsLocal)
    }

    @Test
    fun unStatutOrdinaireNEstPasUneDeclarationDeRepli() {
        assertNull(FallbackNotice.parse("Thinking"))
        assertNull(FallbackNotice.parse(null))
    }

    // ---- Le modèle affiché est le modèle en force -----------------------------------------------

    @Test
    fun leModeleAfficheEstLeModeleEnForcePasLeConfigure() {
        // C'est le défaut signalé : le configuré (deepseek) annoncé alors que le tour est local.
        val affiche = ModelFallback.displayName(declared, "deepseek/deepseek-v4.1-flash")
        assertEquals(declared.servedModel, affiche)
        assertFalse(
            "l'en-tête ne doit pas annoncer le modèle configuré quand un autre a servi le tour",
            affiche == declared.configuredModel,
        )
    }

    @Test
    fun lEnTeteDeclareLeRepliEtNommeLeModeleEnForce() {
        // La réalité pendant un repli : `session.info` a annoncé le modèle configuré, et le repli
        // est déclaré sur le flux. La ligne déclarée nomme l'en-force ET le remplacé.
        val ligne = ModelFallback.declaredLine(declared)
        assertNotNull("un repli actif DOIT être déclaré", ligne)
        ligne!!
        assertTrue("la déclaration dit le modèle en force", ligne.contains(declared.servedLabel))
        assertTrue("la déclaration dit le fournisseur réel (local)", ligne.contains("lmstudio"))
        assertTrue("la déclaration dit le modèle remplacé", ligne.contains(declared.configuredLabel))
    }

    @Test
    fun unModeleEnVigueurSansRepliNEstPasDeclare() {
        assertNull("pas de repli : rien à déclarer", ModelFallback.declaredLine(null))
        assertEquals(
            "sans repli, le nom affiché est le modèle en vigueur",
            "deepseek/deepseek-v4.1-flash",
            ModelFallback.displayName(null, "deepseek/deepseek-v4.1-flash"),
        )
    }

    @Test
    fun leNomAfficheNEstJamaisUnAliasDePasserelle() {
        // Même déclaré, un alias ne s'affiche pas : le modèle en vigueur reprend la main.
        val alias = declared.copy(servedModel = "hermes-agent")
        assertEquals(
            "un alias de repli ne s'affiche pas",
            "deepseek/deepseek-v4.1-flash",
            ModelFallback.displayName(alias, "deepseek/deepseek-v4.1-flash"),
        )
    }

    // ---- Le coût d'un repli local n'est pas un tarif par million ---------------------------------

    @Test
    fun unRepliLocalNAffichePasDePrixParMillion() {
        assertNull(
            "servir en local : afficher un tarif par million serait faux",
            ModelFallback.costLine("\$0.30 in · \$1.20 out", declared),
        )
        assertEquals(
            "\$0.30 in · \$1.20 out",
            ModelFallback.costLine("\$0.30 in · \$1.20 out", null),
        )
    }

    // ---- Aucun cran de réflexion actif pour un repli local ---------------------------------------

    @Test
    fun unRepliLocalNAfficheAucunCranDeReflexionActif() {
        assertEquals("", ModelFallback.reflectionLabel("High", declared))
        assertEquals("High", ModelFallback.reflectionLabel("High", null))
    }
}
