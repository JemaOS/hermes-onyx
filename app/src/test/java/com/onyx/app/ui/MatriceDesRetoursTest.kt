package com.onyx.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La matrice du retour, engendrée depuis la table** — une ligne par destination, et **par nature
 * de retour**. Là où [RetourMatriceTest] liste les parcours relevés à la main (ceux de l'app réelle),
 * ce fichier prend **les 67 destinations de [TableDesRetours]** et rejoue, pour chacune, le modèle
 * exact de la mise en page :
 *
 * - `PILE` : `if (!navBack()) navTo(<repli>)` — l'entrée empile, le retour dépile → **la page
 *   quittée**, quel que soit le point d'entrée ;
 * - `FIXE` : `navTo(<repli>)` — la destination n'a qu'**une** entrée, donc la cible est au sommet de
 *   la pile et le `navTo` la dépile au lieu de l'empiler ;
 * - `RACINE_SYSTEME` : au pied de la pile, le retour n'est pas consommé — le système ferme l'app.
 *
 * Trois invariants sont mesurés sur **toutes** les lignes : le retour rend la page quittée ; aucune
 * suite de retours ne revient sur un état déjà traversé ; et **aucune sous-page ne quitte l'app**.
 */
class MatriceDesRetoursTest {

    private val chat = "CHAT"

    private fun retour(route: String, pile: MutableList<String>): String? {
        // Le `BackHandler` de la mise en page : la même descente pour le bouton, le geste et le
        // clavier (une fois le clavier fermé).
        if (!routeBackHandled(route, pile, twoPanes = false, chatRoute = chat)) return null
        return popRoute(pile) ?: rootRouteBehind(route, chat)
    }

    /** Ce que le retour d'une ligne de la table rend, en partant de [entree]. */
    private fun retourDe(ligne: RetourRoute, entree: String): String? {
        val pile = mutableListOf<String>()
        val route = navigateWithBackStack(entree, ligne.route, pile)
        return when (ligne.nature) {
            NatureRetour.RACINE_SYSTEME, NatureRetour.PILE -> retour(route, pile)
            // Un repli fixe est **le code** : `onBack = { navTo(<repli>) }` — on rejoue `navTo`.
            NatureRetour.FIXE -> navigateWithBackStack(route, ligne.repli!!, pile)
        }
    }

    /** Les points d'entrée rejoués pour une ligne : ceux que son repli nomme, et le fil. */
    private fun entreesDe(ligne: RetourRoute): List<String> = when (ligne.nature) {
        NatureRetour.RACINE_SYSTEME -> listOf(chat)
        NatureRetour.FIXE -> listOf(ligne.repli!!)
        // Un repli **dynamique** (`contextReturn`) n'est pas une destination : il est porté par
        // l'état, et l'entrée rejouée est alors le fil. Les deux entrées réelles de ces écrans sont
        // couvertes par `RetourMatriceTest` (le parcours relevé, page quittée comprise).
        NatureRetour.PILE -> listOfNotNull(
            ligne.repli?.takeIf { it != chat && Regex("[A-Z][A-Z_0-9]{2,}").matches(it) },
            chat,
        ).distinct()
    }

    @Test
    fun `chaque destination rend la page d ou l on vient`() {
        val ecarts = mutableListOf<String>()
        TableDesRetours.toutes.forEach { ligne ->
            entreesDe(ligne).forEach { entree ->
                val arrivee = retourDe(ligne, entree)
                val attendu = if (ligne.nature == NatureRetour.RACINE_SYSTEME) null else entree
                if (arrivee != attendu) {
                    ecarts += "${ligne.route} (${ligne.ecran}) depuis $entree → $arrivee, attendu $attendu"
                }
            }
        }
        assertEquals("des retours ne rendent pas la page quittée", emptyList<String>(), ecarts)
    }

    @Test
    fun `aucune suite de retours ne revient sur un etat deja traverse`() {
        val boucles = mutableListOf<String>()
        TableDesRetours.toutes.forEach { ligne ->
            entreesDe(ligne).forEach { entree ->
                val pile = mutableListOf<String>()
                var route = navigateWithBackStack(entree, ligne.route, pile)
                val vus = mutableSetOf<String>()
                var gestes = 0
                var sorti = false
                while (gestes < 10) {
                    if (!vus.add(route + " | " + pile.joinToString(","))) {
                        boucles += "${ligne.route} depuis $entree : état rejoué ($route | $pile)"
                        break
                    }
                    val suivant = retour(route, pile)
                    gestes++
                    if (suivant == null) { sorti = true; break }
                    route = suivant
                }
                if (!sorti && gestes >= 10) boucles += "${ligne.route} depuis $entree : le retour ne sort jamais"
            }
        }
        assertEquals("des suites de retours bouclent", emptyList<String>(), boucles)
    }

    @Test
    fun `aucune sous-page ne quitte l app`() {
        // À pile vide, toute destination **autre que le fil** consomme le retour : le geste du bord
        // rend la page d'avant, il ne ferme pas l'app. C'est la demande du propriétaire, mesurée ici
        // sur les 66 sous-pages.
        TableDesRetours.toutes.filter { it.nature != NatureRetour.RACINE_SYSTEME }.forEach { ligne ->
            assertTrue(
                "${ligne.route} (${ligne.ecran}) laisserait fermer l'app depuis une sous-page",
                routeBackHandled(ligne.route, emptyList(), twoPanes = false, chatRoute = chat),
            )
            assertNotNull("${ligne.route} : le retour à pile vide ne rend rien", retour(ligne.route, mutableListOf()))
        }
        // Et la racine, elle, rend bien la main au système — dans les deux formats.
        assertFalse(routeBackHandled(chat, emptyList(), twoPanes = false, chatRoute = chat))
        assertFalse(routeBackHandled(chat, emptyList(), twoPanes = true, chatRoute = chat))
        assertNull(retour(chat, mutableListOf()))
    }

    @Test
    fun `la matrice couvre les trois natures, et toutes les destinations`() {
        val natures = TableDesRetours.toutes.groupingBy { it.nature }.eachCount()
        assertTrue("aucune racine", natures[NatureRetour.RACINE_SYSTEME] == 1)
        assertTrue("des destinations qui remontent la pile", natures[NatureRetour.PILE]!! >= 60)
        // Plus aucune destination à « repli fixe » : la flèche de page remonte la pile **avant** de
        // se rabattre, sur les 66 sous-pages (voir `FlecheInterneVerrouTest`, et le défaut signalé
        // de l'édition d'un bot). Le repli reste déclaré — il n'est simplement plus la seule marche.
        assertEquals("plus aucune destination à repli écrit en dur", 0, natures[NatureRetour.FIXE] ?: 0)
        assertEquals(
            "la somme des natures est le graphe",
            TableDesRetours.routes.size,
            natures.values.sum(),
        )
    }
}
