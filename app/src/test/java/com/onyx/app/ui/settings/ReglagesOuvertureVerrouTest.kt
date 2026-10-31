package com.onyx.app.ui.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou d'ouverture des Réglages.**
 *
 * Les verrous existants (`AppearanceParityTest`) certifiaient les libellés de la page Apparence
 * **sans jamais l'ouvrir** : ils lisaient le texte de la source. C'est précisément ce qui a laissé
 * passer le plantage rapporté — taper la rangée **Apparence** **fermait l'application**, alors que
 * toute la suite était verte.
 *
 * Ce verrou-ci **parcourt les destinations de Réglages et ouvre leur écran** dans toute la mesure
 * où un test JVM le permet :
 *
 * 1. il résout la destination de chaque rangée (`navTo(Route.X)`) jusqu'à sa **branche du routeur**
 *    — une rangée sans branche n'atteint pas son écran, et le test la nomme ;
 * 2. il parcourt **tous les `painterResource` de l'application**, résout la ressource réellement
 *    reçue (y compris à travers un champ d'énum comme `AppIconChoice.imageRes`) et **refuse les
 *    icônes adaptatives** (les fichiers `mipmap-anydpi-v26`, balise `<adaptive-icon>`), le type que
 *    `painterResource` ne sait pas décoder et qui **tuait le processus** à l'ouverture de la page.
 *
 * Morsure mesurée : sur la source d'avant, le second test échoue **en nommant `Apparence`**, le
 * fichier `AppIconSettings.kt` et la ressource `ic_launcher_graphite_block`.
 */
class ReglagesOuvertureVerrouTest {

    private val sourcesApp = File("src/main/java/com/onyx/app")
    private val resDir = File("src/main/res")
    private val onyxApp = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()

    private fun fichiersKotlin(): List<File> =
        sourcesApp.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /** Les lignes de la branche `Route.<route> -> …` du routeur, jusqu'à la branche suivante. */
    private fun blocDeBranche(route: String): String {
        val lignes = onyxApp.lines()
        val entete = Regex("^(\\s*)Route\\.$route\\s*->")
        val debut = lignes.indexOfFirst { entete.containsMatchIn(it) }
        if (debut < 0) return ""
        val indentation = entete.find(lignes[debut])!!.groupValues[1].length
        val suivantes = Regex("^\\s*Route\\.[A-Z_0-9]+\\s*->")
        var fin = debut + 1
        while (fin < lignes.size) {
            val l = lignes[fin]
            if (suivantes.containsMatchIn(l) && l.takeWhile { it == ' ' }.length <= indentation) break
            fin++
        }
        return lignes.subList(debut, fin).joinToString("\n")
    }

    /** Les icônes adaptatives embarquées : `nom` → chemin du XML `<adaptive-icon>`. */
    private val iconesAdaptatives: Map<String, String> by lazy {
        val trouvees = mutableMapOf<String, String>()
        File(resDir, "mipmap-anydpi-v26").listFiles()?.forEach { f ->
            if (f.isFile && f.extension == "xml" && f.readText().contains("<adaptive-icon")) {
                trouvees[f.nameWithoutExtension] = f.path
            }
        }
        trouvees
    }

    /**
     * Les ressources qu'un champ `@DrawableRes val <ident>` peut porter : on lit, dans le fichier
     * qui déclare le champ, **toutes** les ressources qu'il référence (`R.mipmap.x` /
     * `R.drawable.x`). C'est ainsi qu'un `painterResource(selected.imageRes)` se résout jusqu'à
     * l'icône de lanceur réellement dessinée.
     */
    private val valeursDesChampsDrawable: Map<String, Set<String>> by lazy {
        val table = mutableMapOf<String, MutableSet<String>>()
        fichiersKotlin().forEach { f ->
            val src = f.readText()
            Regex("@DrawableRes\\s+val\\s+(\\w+)").findAll(src).forEach { m ->
                val ident = m.groupValues[1]
                val refs = Regex("R\\.(?:mipmap|drawable)\\.(\\w+)").findAll(src).map { it.groupValues[1] }
                table.getOrPut(ident) { mutableSetOf() }.addAll(refs)
            }
        }
        table
    }

    // MARK: - 1. Chaque rangée des Réglages atteint son écran

    /** Toutes les destinations que le routeur traite (`Route.X ->`, en-têtes groupés compris). */
    private fun branchesDuRouteur(): Set<String> {
        val trouves = mutableSetOf<String>()
        onyxApp.lines().forEach { ligne ->
            val entete = Regex("^\\s*Route\\.(?:[A-Z_0-9]+)(?:\\s*,\\s*Route\\.[A-Z_0-9]+)*\\s*->")
                .find(ligne) ?: return@forEach
            Regex("Route\\.([A-Z_0-9]+)").findAll(entete.value).forEach { trouves += it.groupValues[1] }
        }
        return trouves
    }

    @Test
    fun `chaque destination ouverte depuis les Reglages a sa branche dans le routeur`() {
        val bloc = blocDeBranche("SETTINGS")
        assertTrue("la branche Route.SETTINGS est introuvable : le test ne mesure rien", bloc.isNotEmpty())

        val destinations = Regex("navTo\\(Route\\.([A-Z_0-9]+)\\.name\\)")
            .findAll(bloc).map { it.groupValues[1] }.toSet()
        assertTrue(
            "aucune destination trouvée dans Route.SETTINGS : le test ne mesure rien",
            destinations.isNotEmpty(),
        )

        val orphelines = destinations - branchesDuRouteur()
        assertTrue(
            "ces rangées de Réglages n'ont pas de branche : elles n'atteignent pas leur écran → $orphelines",
            orphelines.isEmpty(),
        )
    }

    // MARK: - 2. Aucun écran n'ouvre sur une ressource que `painterResource` refuse

    @Test
    fun `aucune page n ouvre sur une icone adaptative remise a painterResource`() {
        val fautes = mutableListOf<String>()
        fichiersKotlin().forEach { f ->
            val src = f.readText()
            Regex("painterResource\\(([^()]*)\\)").findAll(src).forEach { m ->
                val argument = m.groupValues[1].trim().removeSuffix(",").trim()
                val candidats = mutableSetOf<String>()
                Regex("^R\\.(?:mipmap|drawable)\\.(\\w+)$")
                    .matchEntire(argument)
                    ?.let { candidats += it.groupValues[1] }
                if (candidats.isEmpty()) {
                    candidats += valeursDesChampsDrawable[argument.substringAfterLast('.').trim()].orEmpty()
                }
                candidats.filter { it in iconesAdaptatives }.forEach { nom ->
                    val ligne = src.substring(0, m.range.first).count { it == '\n' } + 1
                    fautes += "${f.path}:$ligne — painterResource($argument) reçoit « $nom », " +
                        "une icône adaptative (${iconesAdaptatives[nom]}) : `painterResource` la refuse " +
                        "et l'app se ferme en ouvrant la page (rangée Apparence)."
                }
            }
        }
        assertTrue(
            "des écrans remettent une icône adaptative à Compose — ils plantent à l'ouverture :\n" +
                fautes.joinToString("\n"),
            fautes.isEmpty(),
        )
    }
}
