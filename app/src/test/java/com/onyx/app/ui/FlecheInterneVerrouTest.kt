package com.onyx.app.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La flèche de retour DANS la page — le verrou du trou de l'audit précédent.**
 *
 * Le lot des retours exhaustifs avait relevé **le retour du système** et **le geste du bord**, et
 * laissé de côté **la flèche de l'en-tête** (le contrôle qui vit *dans* la page : `onBack`,
 * `onCancel`, `onClose`, `onDone`, `onContinue`, `onBackToKeyboard`). Or c'est elle que le doigt
 * touche le plus souvent — et sur 32 des 67 destinations elle portait une **cible écrite en dur**
 * (`navTo(<cible>)`, sans remonter la pile). Défaut rapporté par le propriétaire : depuis l'édition
 * d'un bot, la flèche ramenait sur l'onglet **Bots** — une destination fixe — alors qu'il venait
 * d'une **conversation**.
 *
 * Trois invariants sont mesurés, sur **les 67 destinations** :
 *
 * 1. **Un seul mécanisme.** Toute flèche de page descend la même descente que le retour système :
 *    elle remonte la pile (`navBack()`) et ne se rabat que si elle est vide. Un site qui écrit
 *    `navTo(<cible>)` sans `navBack()` fait échouer ce verrou, **nommé avec son fichier et sa ligne**
 *    (c'est la morsure : sur le code d'avant, cette liste n'est pas vide).
 * 2. **La même destination.** Le repli de la flèche (littéral du site d'appel) est **égal** à la
 *    cible du retour système pour le même écran — cible lue dans le `BackHandler`, qui la tire de la
 *    **table des retours** (`TableDesRetours.de(route)?.repli`). Deux lectures indépendantes qui
 *    doivent tomber juste.
 * 3. **Le repli est déclaré.** La cible de la flèche est le `repli` de la ligne de table de la
 *    route : aucune flèche n'invente sa destination.
 */
class FlecheInterneVerrouTest {

    private val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()
    private val lignes = app.lines()

    /** Les contrôles qui portent la flèche de la page. */
    private val motsDeFleche = listOf(
        "onBack", "onCancel", "onClose", "onDone", "onContinue", "onBackToKeyboard",
    )

    private val entete = Regex("^(\\s*)Route\\.([A-Z_0-9]+)((?:\\s*,\\s*Route\\.[A-Z_0-9]+)*)\\s*->")

    /** Toutes les branches `Route.X ->` (un même écran peut en avoir plusieurs : volet et pleine page). */
    private fun branches(route: String): String {
        val morceaux = StringBuilder()
        lignes.forEachIndexed { index, ligne ->
            val m = entete.find(ligne) ?: return@forEachIndexed
            val noms = listOf(m.groupValues[2]) +
                Regex("Route\\.([A-Z_0-9]+)").findAll(m.groupValues[3]).map { it.groupValues[1] }
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

    /** Le bloc d'une lambda de panneau (`val cronPane = …`), où vit la flèche d'un écran du volet. */
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

    /** La région où vit la flèche d'une route : sa branche, plus son panneau s'il la porte. */
    private fun region(route: String): String {
        val ligne = TableDesRetours.de(route) ?: return branches(route)
        val support = ligne.support
        return branches(route) + if (support.startsWith("panneau:")) panneau(support.substringAfter("panneau:")) else ""
    }

    /** Le repli écrit dans une déclaration de flèche (`navTo(Route.X.name)` ou l'état `contextReturn`). */
    private val repliEcrit = Regex("navTo\\(\\s*(?:Route\\.)?([A-Z_0-9]+)\\.name\\s*\\)")
    private val repliEtat = Regex("navTo\\(\\s*([a-z][A-Za-z0-9_]*)\\s*\\)")

    /**
     * Toutes les cibles écrites dans une déclaration de flèche. La plupart n'en portent **qu'une** ;
     * la voix d'un bot en porte deux (`if (bot.isDefault) SETTINGS else BOT_EDITOR`) — la cible est
     * alors un choix, et le verrou exige que le repli de la table soit **l'une** des deux.
     */
    private fun replisDe(declaration: String): List<String> {
        val dansNavTo = declaration.substringAfter("navTo(", "")
        return (
            Regex("(?:Route\\.)?([A-Z_0-9]+)\\.name").findAll(dansNavTo).map { it.groupValues[1] } +
                Regex("navTo\\(\\s*([a-z][A-Za-z0-9_]*)\\s*\\)").findAll(declaration).map { it.groupValues[1] }
            ).toList()
    }

    /**
     * La cible du **retour système** pour un écran : celle du `BackHandler`, qui lit le repli de la
     * table. Si le code revenait à une cible écrite en dur, ce verrou le verrait (elle ne serait plus
     * « le repli de la table »).
     */
    private val repliSystemeVientDeLaTable: Boolean by lazy {
        val bloc = app.substringAfter("fun repliDeRetour(): String =")
            .substringBefore("BackHandler(enabled = backHandled)")
        bloc.contains("TableDesRetours.de(route)?.repli")
    }

    private fun cibleSysteme(route: String): String? = when {
        !repliSystemeVientDeLaTable -> "CHAT"
        route == "CONTEXT_EDITOR" -> "contextReturn"
        else -> TableDesRetours.de(route)?.repli
    }

    /**
     * **La morsure.** Le balayage brut, indépendant de la table : tout site de flèche qui écrit sa
     * cible en `navTo(` **sans** remonter la pile est relevé, avec son fichier et sa ligne.
     */
    private fun flechesSansDescente(): List<String> {
        val fautifs = mutableListOf<String>()
        var index = 0
        while (index < lignes.size) {
            val ligne = lignes[index]
            val mot = motsDeFleche.firstOrNull { Regex("\\b$it\\s*=").containsMatchIn(ligne) }
            if (mot == null) { index++; continue }
            val expression = StringBuilder(ligne.substringAfter("$mot ="))
            var profondeur = expression.count { it == '{' } - expression.count { it == '}' }
            var suivant = index
            while (profondeur > 0 && suivant + 1 < lignes.size) {
                suivant++
                expression.append(' ').append(lignes[suivant])
                profondeur += lignes[suivant].count { it == '{' } - lignes[suivant].count { it == '}' }
            }
            val texte = expression.toString()
            val navigue = texte.contains("navTo(") || texte.contains("navFromEmpty(")
            val remonte = texte.contains("navBack()") || texte.contains("paneBack") ||
                texte.contains("sidebarState")
            if (navigue && !remonte) {
                fautifs += "OnyxApp.kt:${index + 1} — $mot : ${texte.trim()}"
            }
            index = suivant + 1
        }
        return fautifs
    }

    @Test
    fun `aucune fleche de page n ecrit sa cible en dur, sans remonter la pile`() {
        val fautifs = flechesSansDescente()
        assertEquals(
            "des flèches de page sautent la pile (cible écrite en dur) :\n" + fautifs.joinToString("\n"),
            emptyList<String>(),
            fautifs,
        )
    }

    @Test
    fun `les 66 fleches de page remontent la pile`() {
        val sansDescente = mutableListOf<String>()
        var avecFleche = 0
        TableDesRetours.toutes.forEach { ligne ->
            if (ligne.nature == NatureRetour.RACINE_SYSTEME) return@forEach
            val ou = region(ligne.route)
            if (ou.isBlank()) return@forEach
            avecFleche++
            if (!ou.contains(ligne.declaration)) {
                sansDescente += "${ligne.route} : « ${ligne.declaration} » introuvable dans sa région"
            } else if (!ligne.declaration.contains("navBack()")) {
                sansDescente += "${ligne.route} : « ${ligne.declaration} » ne remonte pas la pile"
            }
        }
        assertTrue("le relevé porte ses 66 flèches (trouvé $avecFleche)", avecFleche >= 66)
        assertEquals(
            "des flèches de page ne descendent pas la pile :\n" + sansDescente.joinToString("\n"),
            emptyList<String>(),
            sansDescente,
        )
    }

    @Test
    fun `la fleche et le retour systeme visent la meme destination, pour les 66 ecrans`() {
        assertTrue(
            "le retour système tire son repli de la table des retours",
            repliSystemeVientDeLaTable,
        )
        val ecarts = mutableListOf<String>()
        TableDesRetours.toutes.forEach { ligne ->
            if (ligne.nature == NatureRetour.RACINE_SYSTEME) return@forEach
            val fleches = replisDe(ligne.declaration)
            val systeme = cibleSysteme(ligne.route)
            if (systeme == null || systeme !in fleches) {
                ecarts += "${ligne.route} : la flèche vise $fleches, le retour système « $systeme »"
            }
        }
        assertEquals(
            "la flèche de page et le retour système divergent :\n" + ecarts.joinToString("\n"),
            emptyList<String>(),
            ecarts,
        )
    }
}
