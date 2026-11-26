package com.onyx.app.ui.common

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La page des remerciements ne crédite que ce que l'app embarque ou ce que le poste fait tourner.
 *
 * Deux garanties, et pas une de plus : aucun composant cité n'appartient à une autre plateforme, et
 * chaque composant cité se retrouve dans le dépôt — une dépendance déclarée, une ressource livrée,
 * un fichier source, ou un service du poste documenté par l'URL où sa licence a été lue. Un
 * composant fantôme fait échouer ce test.
 */
class AcknowledgementsPageTest {

    /** La racine du dépôt, depuis le dossier de travail du test. */
    private val root: File = generateSequence(File(".").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile }
        ?: error("racine du dépôt introuvable depuis ${File(".").absolutePath}")

    private val versions: String = File(root, "gradle/libs.versions.toml").readText()

    private val buildFiles: String = listOf("app", "core", "wear", "auto")
        .map { File(root, "$it/build.gradle.kts") }
        .filter { it.isFile }
        .joinToString("\n") { it.readText() }

    private val screenSource: String =
        File(root, "app/src/main/java/com/onyx/app/ui/common/AcknowledgementsScreen.kt").readText()

    /**
     * Les mots qui trahissent la plateforme d'origine : interdits dans toute l'interface, page des
     * remerciements comprise. Le motif exige des bornes de mot pour ne pas confondre un mot court avec
     * une syllabe d'un autre mot.
     */
    private val forbidden = listOf(
        "sw" + "ift", "sw" + "iftui", "sw" + "iftnio", "sw" + "iftterm", "i" + "os", "i" + "phone", "i" + "pad", "ap" + "ple", "x" + "code", "s" + "iri",
    )

    /** La phrase qui déclare un composant comme écrit dans ce dépôt, donc sans tiers à créditer. */
    private val HOMEGROWN = "not a third-party component"

    private fun matchesWord(text: String, word: String): Boolean =
        Regex("(?<![a-z])$word(?![a-z])").containsMatchIn(text.lowercase())

    @Test
    fun `aucune ligne ne cite une autre plateforme`() {
        val texts = ONYX_ACKNOWLEDGEMENTS.flatMap { listOf(it.name, it.credit) } + ACKNOWLEDGEMENTS_INTRO
        texts.forEach { text ->
            forbidden.forEach { word ->
                assertFalse("« $word » dans « $text »", matchesWord(text, word))
            }
        }
    }

    @Test
    fun `le fichier de l'ecran ne contient aucun mot interdit`() {
        forbidden.forEach { word ->
            assertFalse(
                "« $word » dans AcknowledgementsScreen.kt",
                matchesWord(screenSource, word),
            )
        }
    }

    @Test
    fun `chaque composant cite se retrouve dans le depot`() {
        ONYX_ACKNOWLEDGEMENTS.forEach { entry ->
            when (val provenance = entry.provenance) {
                is Provenance.Dependency -> {
                    assertTrue(
                        "alias Gradle inconnu pour ${entry.name} : ${provenance.alias}",
                        versions.contains("${provenance.alias} ="),
                    )
                    val artifact = provenance.coordinate.substringAfter(':')
                    assertTrue(
                        "coordonnée absente de libs.versions.toml : ${provenance.coordinate}",
                        versions.contains("name = \"$artifact\""),
                    )
                    val dotted = provenance.alias.replace('-', '.')
                    assertTrue(
                        "alias non référencé par un module : ${provenance.alias}",
                        buildFiles.contains("libs.$dotted"),
                    )
                }

                is Provenance.Asset -> assertTrue(
                    "ressource absente : ${provenance.path}",
                    File(root, "core/src/main/assets/${provenance.path}").isFile,
                )

                is Provenance.Source -> assertTrue(
                    "fichier source absent : ${provenance.path}",
                    File(root, provenance.path).isFile,
                )

                is Provenance.Desktop -> assertTrue(
                    "aucune source de licence pour ${entry.name} : ${provenance.licenceSource}",
                    provenance.licenceSource.startsWith("https://"),
                )
            }
        }
    }

    @Test
    fun `chaque composant porte une licence nommee`() {
        val named = listOf(
            "Apache License", "MIT License", "SIL Open Font License",
            "Open Data Commons", "CC0", "General Public License", "LGPL",
        )
        ONYX_ACKNOWLEDGEMENTS.forEach { entry ->
            // Le code maison ne crédite personne : la ligne le dit, et c'est la seule exception.
            if (entry.credit.contains(HOMEGROWN)) {
                assertTrue(
                    "un composant maison doit être du code du dépôt : ${entry.name}",
                    entry.provenance is Provenance.Source,
                )
            } else {
                assertTrue(
                    "licence non nommée pour ${entry.name} : ${entry.credit}",
                    named.any { entry.credit.contains(it) },
                )
            }
        }
    }

    @Test
    fun `les composants de la page sont uniques`() {
        val names = ONYX_ACKNOWLEDGEMENTS.map { it.name }
        assertEquals("doublon dans les remerciements", names.size, names.distinct().size)
    }

    @Test
    fun `la phrase d'introduction nomme les trois moteurs`() {
        assertTrue(ACKNOWLEDGEMENTS_INTRO.contains("KaTeX"))
        assertTrue(ACKNOWLEDGEMENTS_INTRO.contains("mermaid"))
        assertTrue(ACKNOWLEDGEMENTS_INTRO.contains("sshj"))
    }

    @Test
    fun `le terminal est declare maison, pas credite a un tiers`() {
        val terminal = ONYX_ACKNOWLEDGEMENTS.single { it.name == "Terminal" }
        assertTrue("le terminal doit être du code du dépôt", terminal.provenance is Provenance.Source)
        assertTrue(
            "le terminal doit dire qu'il n'est pas un composant tiers",
            terminal.credit.contains(HOMEGROWN),
        )
    }

    @Test
    fun `les fichiers de licence d'une autre plateforme ne sont plus livres`() {
        val licences = File(root, "core/src/main/assets/rich/LICENSES")
        assertTrue("dossier des licences absent", licences.isDirectory)
        listOf("Sw" + "iftTerm-MIT.txt", "Sw" + "ift-packages-Apache.txt").forEach { gone ->
            assertFalse("fichier encore livré : $gone", File(licences, gone).exists())
        }
        assertEquals(
            "seules les licences des ressources livrées restent",
            listOf("KaTeX-MIT.txt", "KaTeX-fonts-OFL.txt", "mermaid-MIT.txt").sorted(),
            licences.listFiles()!!.map { it.name }.sorted(),
        )
    }

    @Test
    fun `la page separe le telephone du poste, sans melange`() {
        val phone = ONYX_ACKNOWLEDGEMENTS.filter { it.where == Where.PHONE }
        val computer = ONYX_ACKNOWLEDGEMENTS.filter { it.where == Where.COMPUTER }
        assertTrue("aucun composant embarqué", phone.isNotEmpty())
        assertTrue("aucun service du poste", computer.isNotEmpty())
        assertTrue(
            "les ressources et dépendances sont du côté téléphone",
            phone.all { it.provenance !is Provenance.Desktop },
        )
        assertTrue(
            "les services du poste sont du côté poste",
            computer.all { it.provenance is Provenance.Desktop },
        )
    }
}
