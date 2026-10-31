package com.onyx.app.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * **Les cas de la fiche d'usage, tapés sur les vrais modèles du propriétaire — et dans tous, le nom
 * affiché est le modèle EN VIGUEUR.**
 *
 * Le défaut signalé, et mesuré : l'en-tête nommait `deepseek/deepseek-v4.1-flash` (le modèle que le
 * propriétaire venait de choisir pour `scout`) pendant que la fiche de consommation nommait
 * `sage-a1.5` (le modèle **local** du profil `default`, celui du premier tour). La cause : la fiche
 * lisait le `model` que la session avait **consigné** à son ouverture, et ce champ ne bouge pas quand
 * on change de modèle.
 *
 * Ici, la résolution unique ([ModelInForce.usageModel] / [ModelInForce.preferInForce]) est tenue par
 * les trois cas de conversation, et **le changement de modèle suit** : on relit le nom après avoir
 * changé le modèle en vigueur, et il a bougé — sans recréer la conversation.
 */
class ModeleEnVigueurFicheCasTest {

    /** Le modèle distant du fournisseur Kilo, celui que le propriétaire a choisi pour `scout`. */
    private val distant = "deepseek/deepseek-v4.1-flash"

    /** Le modèle **local** du profil `default` (LM Studio sur la machine). */
    private val local = "ornith1.5-9b-3mper0rr-obliterated"

    /** Le modèle que la session de `scout` avait consigné **avant** le changement. */
    private val consigne = "sage-a1.5"

    // — (a) le nom affiché est celui de l'en-tête, pas celui du registre -------------------------

    @Test
    fun `conversation d un bot — le nom en vigueur prime sur le nom consigne`() {
        // L'en-tête : le bot porte `distant` (sa feuille de modèle l'a écrit). La session, elle,
        // avait consigné `local`. AVANT, la fiche nommait `consigne` ; APRÈS, `distant`.
        val nom = ModelInForce.usageModel(
            conversation = null,
            bot = distant,
            server = "hermes-agent",
            recorded = consigne,
        )
        assertEquals("la fiche nomme le modèle EN VIGUEUR", distant, nom)
    }

    @Test
    fun `conversation simple — le nom vif l emporte sur le consigne`() {
        val nom = ModelInForce.usageModel(
            conversation = distant,
            bot = null,
            server = null,
            recorded = consigne,
        )
        assertEquals(distant, nom)
    }

    @Test
    fun `rien en vigueur — le nom consigne sert de repli`() {
        val nom = ModelInForce.usageModel(
            conversation = null,
            bot = null,
            server = null,
            recorded = local,
        )
        assertEquals("le seul nom connu est celui du registre", local, nom)
    }

    @Test
    fun `un alias de passerelle ne s ecrit jamais, ni d un cote ni de l autre`() {
        // Le serveur ne configure qu'un alias, et la session a consigné un alias : la fiche n'écrit
        // aucun des deux (règle de `ModelName`) — elle laisse le titre sans modèle.
        assertNull(
            ModelInForce.usageModel(null, null, "hermes-agent", "default"),
        )
        // L'alias en repli est écarté aussi.
        assertEquals(distant, ModelInForce.usageModel(null, distant, "hermes-agent", "hermes-agent"))
    }

    // — (b) le changement de modèle SUIT, sans recréer la conversation ---------------------------

    @Test
    fun `changer le modele en vigueur change le nom de la fiche, sans recreer la conversation`() {
        val sessionConsignee = consigne
        val avant = ModelInForce.usageModel(null, distant, null, sessionConsignee)
        val apres = ModelInForce.usageModel(null, local, null, sessionConsignee)
        assertEquals("avant le changement", distant, avant)
        assertEquals("après le changement", local, apres)
        // Le nom de la session **n'a pas bougé** : c'est lui qui était figé, et c'est la raison du
        // défaut. La fiche suit le modèle en vigueur, pas ce nom-là.
        assertEquals("la session consigne toujours la même chose", consigne, sessionConsignee)
        assertNotEquals("la fiche doit suivre le changement de modèle", avant, apres)
    }

    // — (c) la même valeur alimente le nom et le prix -------------------------------------------

    @Test
    fun `le prix se cherche sous le nom affiche — les deux ne divergent pas`() {
        val table = mapOf(
            distant to ServerPrice(input = "$0.30", output = "$1.20"),
            // Le modèle local ne publie aucun tarif : c'en est le cas normal.
        )
        val affiche = ModelInForce.preferInForce(distant, consigne)
        // Le prix se cherche **sous le nom affiché** (`table[affiche]`), pas sous le nom consigné.
        assertEquals(distant, affiche)
        assertEquals("$0.30", table.getValue(affiche!!).input)
        assertNull("le nom consigné n'a pas de tarif ici", table[consigne])
        // Et quand le modèle en vigueur est le local, la recherche tombe à vide — l'écran le dira.
        val afficheLocal = ModelInForce.preferInForce(local, consigne)
        assertNull("aucun tarif pour le modèle local", table[afficheLocal!!])
    }
}
