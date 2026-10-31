package com.onyx.app.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou des retours.** Il mord sur quatre défauts, et **nomme toujours la destination** :
 *
 * 1. une valeur de l'énum `Route` **sans ligne** dans [TableDesRetours] ;
 * 2. une ligne dont la [RetourRoute.declaration] est **vide** (« aucun retour déclaré ») ;
 * 3. une ligne dont la [RetourRoute.declaration] **ne se retrouve plus** dans la source, là où elle
 *    doit vivre (la branche du routeur, le panneau qui porte l'écran, ou la mise en page) : c'est le
 *    cas « la route a perdu son retour » ;
 * 4. une ligne **périmée** (une destination que l'énum ne porte plus), ou un [RetourRoute.repli] qui
 *    ne nomme pas une destination existante.
 *
 * La source reste le **graphe de navigation** : le test lit `OnyxApp.kt`, en extrait l'énum et les
 * branches du `when (presentedRoute)`, et confronte. La table ne peut donc pas dériver en silence.
 */
class TableDesRoutesVerrouTest {

    private val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()
    private val lignes = app.lines()

    /** Les valeurs de l'énum `Route`, dans l'ordre — la source de vérité du graphe. */
    private val enumRoutes: List<String> by lazy {
        val bloc = app.substringAfter("private enum class Route {").substringBefore("\n}")
        val sansCommentaires = bloc.lines().joinToString("\n") { it.substringBefore("//") }
        Regex("\\b[A-Z][A-Z_0-9]{2,}\\b").findAll(sansCommentaires).map { it.value }.distinct().toList()
    }

    private val entete = Regex("^(\\s*)Route\\.([A-Z_0-9]+)((?:\\s*,\\s*Route\\.[A-Z_0-9]+)*)\\s*->")

    /** Toutes les branches `Route.X ->` (un même écran peut en avoir plusieurs : volet et pleine page). */
    private fun branches(route: String): String {
        val morceaux = StringBuilder()
        lignes.forEachIndexed { index, ligne ->
            val m = entete.find(ligne) ?: return@forEachIndexed
            val noms = listOf(m.groupValues[2]) + Regex("Route\\.([A-Z_0-9]+)").findAll(m.groupValues[3]).map { it.groupValues[1] }
            if (route !in noms) return@forEachIndexed
            val indentation = m.groupValues[1].length
            var fin = index + 1
            while (fin < lignes.size) {
                val suivante = lignes[fin]
                val mSuivante = entete.find(suivante)
                if (mSuivante != null && mSuivante.groupValues[1].length <= indentation) break
                if (suivante.isNotBlank() && !suivante.startsWith(" ".repeat(indentation))) break
                fin++
            }
            morceaux.append(lignes.subList(index, fin).joinToString("\n")).append('\n')
        }
        return morceaux.toString()
    }

    /** Le bloc d'une lambda de panneau (`val chatPane = …`), où vit le retour d'un écran du volet. */
    private fun panneau(nom: String): String {
        lignes.forEachIndexed { index, ligne ->
            if (Regex("val $nom\\b").containsMatchIn(ligne)) {
                val indentation = ligne.length - ligne.trimStart().length
                var fin = index + 1
                while (fin < lignes.size) {
                    val suivante = lignes[fin]
                    if (suivante.isNotBlank() && (suivante.length - suivante.trimStart().length) <= indentation) break
                    fin++
                }
                return lignes.subList(index, fin).joinToString("\n")
            }
        }
        return ""
    }

    /** Là où le verrou cherche la déclaration d'une ligne. */
    private fun support(declaration: String, support: String): String = when {
        support == "layout" -> app
        support.startsWith("panneau:") -> panneau(support.substringAfter("panneau:"))
        else -> declaration // la branche, résolue par l'appelant
    }

    @Test
    fun `chaque destination du graphe a sa ligne dans la table`() {
        val sansLigne = enumRoutes.filter { TableDesRetours.de(it) == null }
        assertTrue(
            "des destinations du graphe n'ont pas de retour déclaré dans la table : $sansLigne",
            sansLigne.isEmpty(),
        )
        // Et l'autre moitié : le graphe est bien lu (un énum vide rendrait le test creux).
        assertTrue("l'énum porte ses destinations (${enumRoutes.size})", enumRoutes.size >= 60)
    }

    @Test
    fun `aucune ligne n est vide ni perimee`() {
        val perimees = TableDesRetours.routes.filter { it !in enumRoutes }
        assertTrue("des lignes nomment une destination que l'énum ne porte plus : $perimees", perimees.isEmpty())

        val vides = TableDesRetours.toutes.filter { it.declaration.isBlank() }.map { it.route }
        assertTrue("des destinations sans retour déclaré : $vides", vides.isEmpty())
    }

    @Test
    fun `chaque retour declare se retrouve dans la source, la ou il vit`() {
        val perdues = mutableListOf<String>()
        TableDesRetours.toutes.forEach { ligne ->
            val ou = if (ligne.support == SUPPORT_BRANCHE) branches(ligne.route) else support(ligne.declaration, ligne.support)
            if (!ou.contains(ligne.declaration)) perdues += "${ligne.route} (« ${ligne.declaration} » introuvable dans ${ligne.support})"
        }
        assertTrue("des destinations ont perdu le retour qu'elles déclarent : $perdues", perdues.isEmpty())
    }

    @Test
    fun `chaque destination du graphe est dessinee par le routeur`() {
        // Un écran sans branche serait un écran blanc, et une route sans destination serait injoignable.
        val sansBranche = enumRoutes.filter { branches(it).isBlank() }
        assertTrue("des destinations n'ont aucune branche de routeur : $sansBranche", sansBranche.isEmpty())
    }

    @Test
    fun `chaque repli nomme une destination existante ou un etat de la source`() {
        TableDesRetours.toutes.forEach { ligne ->
            val repli = ligne.repli ?: return@forEach
            val nommeUneDestination = repli in enumRoutes
            val estDynamique = Regex("\\b$repli\\b").containsMatchIn(app)
            assertTrue(
                "${ligne.route} : le repli « $repli » n'est ni une destination de l'énum ni un état lu dans la source",
                nommeUneDestination || estDynamique,
            )
        }
    }

    @Test
    fun `l inventaire des retours ecrits en dur est vide`() {
        // Le trou de l'audit précédent : ces retours-ci étaient **certifiés** « à une seule entrée »
        // — `navTo(X)` sans `navBack()`. Le défaut du propriétaire a montré le contraire : l'édition
        // d'un bot s'ouvre **aussi** d'une conversation, et la flèche ramenait alors sur l'onglet
        // Bots au lieu du fil. La flèche descend désormais la **même** descente que le retour système
        // (`if (!navBack()) navTo(<repli>)`), et l'inventaire des retours écrits en dur est donc
        // **fermé** : vide. La morsure vit dans `FlecheInterneVerrouTest` (chaque `navBack()` manquant
        // est nommé avec sa ligne).
        assertEquals(
            "plus aucun retour de page ne peut écrire sa cible en dur",
            emptySet<String>(),
            TableDesRetours.toutes.filter { it.nature == NatureRetour.FIXE }.map { it.route }.toSet(),
        )
    }

    @Test
    fun `la racine est le fil, et lui seul`() {
        val racines = TableDesRetours.toutes.filter { it.nature == NatureRetour.RACINE_SYSTEME }.map { it.route }
        assertEquals(listOf("CHAT"), racines)
    }
}
