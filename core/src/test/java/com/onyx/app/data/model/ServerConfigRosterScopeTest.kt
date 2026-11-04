package com.onyx.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * **La portée du roster : le serveur, pas la configuration entière.**
 *
 * Le roster gardé répond à « qui sont les bots de ce serveur ». Il ne dépend pas du modèle qui
 * les sert : le même serveur, changé de modèle, a les mêmes bots — et doit donc garder les mêmes
 * visages.
 *
 * Avec la signature entière, choisir un modèle faisait perdre le roster : la clé changeait, la
 * garde était introuvable, et l'écran retombait sur Onyx — visage bleu, « @default » — le temps
 * que le réseau réponde. C'est le défaut que ce correctif cherche, et il se reproduisait par ce
 * chemin-là.
 */
class ServerConfigRosterScopeTest {

    private val serveur = ServerConfig(
        id = "srv-1",
        name = "Onyx",
        baseUrl = "https://exemple.test/v1",
        apiKey = "cle",
        model = "deepseek/deepseek-v4.1-flash",
        provider = "kilocode",
        serveBaseUrl = "https://tableau-de-bord.test",
        serveUsername = "john",
        servePassword = "motdepasse",
    )

    @Test
    fun `le modele ne change pas la portee du roster`() {
        // Changer de modèle ne change pas les bots du serveur : la portée reste la même, et le
        // roster garde sa place.
        val autreModele = serveur.copy(model = "minimax/minimax-m3.1-flash", provider = "kilocode")

        assertEquals(serveur.rosterScope, autreModele.rosterScope)
    }

    @Test
    fun `le provider ne change pas la portee du roster`() {
        assertEquals(serveur.rosterScope, serveur.copy(provider = "autre").rosterScope)
    }

    @Test
    fun `deux serveurs differents n'ont pas la meme portee`() {
        // La contamination d'identité : le roster de l'un afficherait les bots de l'autre.
        val autre = serveur.copy(id = "srv-2", baseUrl = "https://autre.test/v1")

        assertNotEquals(serveur.rosterScope, autre.rosterScope)
    }

    @Test
    fun `une adresse differente change la portee`() {
        assertNotEquals(serveur.rosterScope, serveur.copy(baseUrl = "https://ailleurs.test/v1").rosterScope)
    }

    @Test
    fun `un tableau de bord different change la portee`() {
        // Le Dashboard est le serveur qui porte le roster : deux tableaux de bord sont deux
        // serveurs, même si la passerelle est la même.
        assertNotEquals(
            serveur.rosterScope,
            serveur.copy(serveBaseUrl = "https://autre-tableau.test").rosterScope,
        )
    }

    @Test
    fun `des identifiants differents changent la portee`() {
        assertNotEquals(serveur.rosterScope, serveur.copy(apiKey = "autre-cle").rosterScope)
        assertNotEquals(serveur.rosterScope, serveur.copy(servePassword = "autre").rosterScope)
        assertNotEquals(serveur.rosterScope, serveur.copy(accessID = "autre").rosterScope)
    }

    @Test
    fun `un profil Hermes different change la portee`() {
        assertNotEquals(serveur.rosterScope, serveur.copy(hermesProfile = "coder").rosterScope)
    }

    @Test
    fun `la portee reste bien une cle de configuration`() {
        // La signature entière change, elle, avec le modèle : c'est wanted pour les écrans, qui
        // doivent se reconstruire quand le modèle change.
        assertNotEquals(serveur.configSignature, serveur.copy(model = "autre").configSignature)
    }
}
