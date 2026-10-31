package com.onyx.app.localization

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aucun texte affichable ne doit trahir la plateforme d'origine de l'app.
 *
 * La fuite se cache presque toujours dans le **dictionnaire français** : un libellé traduit peut
 * citer l'autre plateforme alors que tous les identifiants du code sont renommés. Le test balaye
 * donc **les clés et les valeurs** de tous les catalogues, dans les deux langues — c'est le
 * garde-fou qui manquait quand la page des remerciements créditait des bibliothèques de l'autre
 * plateforme.
 */
class VisibleTextHasNoForeignPlatformTest {

    /** Les mots de l'écosystème d'origine : jamais dans l'interface, ni en anglais ni en français. */
    private val forbidden = listOf(
        "sw" + "ift", "sw" + "iftui", "sw" + "iftnio", "sw" + "iftterm", "i" + "os", "i" + "phone", "i" + "pad", "ap" + "ple", "x" + "code", "s" + "iri",
    )

    /** Tout ce que l'app peut afficher : les catalogues français, et le retour français → anglais. */
    private val catalogs: Map<String, String> =
        onyxSharedFrench + onyxAdditionalFrench + onyxSettingsFrench + onyxCommonFrench +
            onyxRemainingFrench + onyxSetupTerminalFrench + onyxCardsFrench + onyxLockFrench +
            onyxToolsFrench + onyxToolsPageFrench + onyxEnglishFromFrench + onyxLanguageFrench +
            onyxCodeFrench + onyxCodeFrenchToEnglish + onyxLedgerFrench

    private fun mentions(text: String, word: String): Boolean =
        Regex("(?<![a-z])$word(?![a-z])").containsMatchIn(text.lowercase())

    @Test
    fun `les traductions affichees ne citent aucune autre plateforme`() {
        assertTrue("catalogues illisibles", catalogs.size > 100)
        catalogs.forEach { (key, value) ->
            forbidden.forEach { word ->
                assertFalse("clé : $key", mentions(key, word))
                assertFalse("« $key » → $value", mentions(value, word))
            }
        }
    }

    @Test
    fun `la page des remerciements est traduite dans les deux langues`() {
        val keys = listOf(
            "Acknowledgements",
            "Onyx draws math with KaTeX and diagrams with mermaid, both embedded in the app and used offline; its terminal is written here and speaks SSH with sshj. The rest comes from Android's libraries and from the services running on your own PC.",
            "On this phone",
            "On your PC",
            "KaTeX renders mathematics offline, from the copy shipped in the app — MIT License, © 2013-2020 Khan Academy and other contributors.",
            "Piper adds French voices behind the same address — GNU General Public License 3.0 or later, © the Piper authors (piper-tts 1.8.0).",
        )
        keys.forEach { key ->
            val value = catalogs[key]
            assertTrue("clé non traduite : $key", value != null)
            assertTrue("traduction vide pour $key", value!!.isNotBlank())
        }
    }

    @Test
    fun `le vocabulaire de l'autre plateforme ne revient pas par une clé voisine`() {
        // La page « Remerciements » a déjà cité les bibliothèques de l'autre plateforme : ce test
        // refuse aussi leurs noms propres, qui ne sont pas des mots du dictionnaire commun.
        val otherPlatformLibraries = listOf("sw" + "ift-term", "sw" + "ift-packages")
        otherPlatformLibraries.forEach { gone ->
            assertFalse("clé encore présente : $gone", catalogs.keys.any { it.lowercase().contains(gone) })
        }
    }
}
