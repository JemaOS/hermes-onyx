package com.onyx.app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * **Le verrou du nom et de l'icône au lanceur.**
 *
 * Le propriétaire a tranché deux choses, et elles remplacent la convention antérieure :
 *
 * 1. **Le nom est « Onyx »** — un seul mot — partout où Android lit un nom d'application :
 *    `app_name` de l'application, de `MainActivity`, des quatorze alias de lanceur, du récepteur
 *    du widget ; les libellés des raccourcis (appui long sur l'icône) ; et, dans leurs modules,
 *    `wear_app_name` (montre) et `auto_app_name` (voiture). L'ancien libellé (« Hermes Onyx ») ne
 *    doit plus apparaître **dans aucun dossier de langue**.
 * 2. **L'icône est celle de la capture** — le carré arrondi sombre, le cercle et la rune —
 *    c'est-à-dire `ic_launcher_graphite_block`. Elle ne doit plus changer, ni par une variante de
 *    build, ni par un thème, ni par une refonte : l'alias actif du manifeste et le choix primaire
 *    du sélecteur pointent sur cette même ressource, et l'icône déclarée de l'APK en est une copie
 *    **octet pour octet**.
 *
 * Les chemins sont relatifs au module `:app` (le répertoire de travail des tests unitaires), comme
 * `AppIconCatalogTest`. Chaque message d'échec **nomme le fichier** fautif.
 */
class NomEtIconeDuLanceurTest {

    private val androidNs = "http://schemas.android.com/apk/res/android"

    /** La couche de référence : le carré arrondi sombre, le cercle et la rune. */
    private val referenceIcon = "ic_launcher_graphite_block"

    /** L'alias de lanceur qui la porte. */
    private val referenceAlias = "GraphiteBlock"

    /** L'ancien libellé, celui que le propriétaire a retiré. */
    private val retiredLabel = "Hermes Onyx"

    private fun document(path: String): Document =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File(path))

    private fun elementsOf(document: Document, tag: String): List<Element> {
        val nodes = document.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    /** Les `string` d'un dossier de ressources, par nom. */
    private fun strings(dir: File): Map<String, String> =
        elementsOf(document(File(dir, "strings.xml").path), "string")
            .associate { it.getAttribute("name") to it.textContent }

    /** Tous les `values*` : `values`, `values-fr`, `values-en-rGB`… (les langues présentes). */
    private fun valueDirs(res: File): List<File> =
        res.listFiles()?.filter { it.isDirectory && it.name.startsWith("values") }
            .orEmpty().sortedBy { it.name }

    // --- 1. Le nom ----------------------------------------------------------------------------

    @Test
    fun `le nom au lanceur est exactement Onyx, et l'ancien libelle n'est plus nulle part`() {
        val res = File("src/main/res")
        val dirs = valueDirs(res)
        assertTrue("aucun dossier values/ dans ${res.path}", dirs.isNotEmpty())

        dirs.forEach { dir ->
            val file = File(dir, "strings.xml").path
            val all = strings(dir)

            // Aucun libellé de l'ancien nom, quelle que soit la clé (nom, raccourcis, widget).
            all.forEach { (name, value) ->
                assertFalse(
                    "$file porte encore l'ancien libellé du lanceur dans « $name » : $value",
                    value.contains(retiredLabel),
                )
            }

            // La valeur de repli (« values ») doit être exactement « Onyx ».
            if (dir.name == "values") {
                assertEquals("$file : app_name", "Onyx", all["app_name"])
            } else if (all.containsKey("app_name")) {
                // Une traduction ne renomme pas l'app : si elle définit app_name, c'est « Onyx ».
                assertEquals("$file : app_name", "Onyx", all["app_name"])
            }
        }
    }

    @Test
    fun `le manifeste ne nomme l'application, ses activites et ses alias que par app_name`() {
        val path = "src/main/AndroidManifest.xml"
        val document = document(path)

        val app = elementsOf(document, "application").single()
        assertEquals("$path : libellé de l'application", "@string/app_name", app.getAttributeNS(androidNs, "label"))

        val main = elementsOf(document, "activity").single {
            it.getAttributeNS(androidNs, "name") == ".MainActivity"
        }
        assertEquals("$path : libellé de MainActivity", "@string/app_name", main.getAttributeNS(androidNs, "label"))

        val aliases = elementsOf(document, "activity-alias")
        assertTrue("$path : aucun alias de lanceur", aliases.isNotEmpty())
        aliases.forEach { alias ->
            assertEquals(
                "$path : libellé de l'alias ${alias.getAttributeNS(androidNs, "name")}",
                "@string/app_name",
                alias.getAttributeNS(androidNs, "label"),
            )
        }

        val widget = elementsOf(document, "receiver").single {
            it.getAttributeNS(androidNs, "name") == ".widget.OnyxWidgetReceiver"
        }
        assertEquals("$path : libellé du widget", "@string/app_name", widget.getAttributeNS(androidNs, "label"))

        // Aucun libellé écrit en dur, et surtout pas l'ancien nom.
        assertFalse("$path contient encore « $retiredLabel »", File(path).readText().contains(retiredLabel))
    }

    // --- 2. L'icône ---------------------------------------------------------------------------

    @Test
    fun `un seul alias de lanceur est actif, et c'est celui de la reference`() {
        val path = "src/main/AndroidManifest.xml"
        val document = document(path)
        val aliases = elementsOf(document, "activity-alias")

        val enabled = aliases.filter { it.getAttributeNS(androidNs, "enabled") == "true" }
        assertEquals(
            "$path : un seul alias doit être actif, or ${enabled.map { it.getAttributeNS(androidNs, "name") }}",
            1,
            enabled.size,
        )

        val active = enabled.single()
        assertTrue(
            "$path : l'alias actif ${active.getAttributeNS(androidNs, "name")} n'est pas celui de la " +
                "référence (.launcher.$referenceAlias)",
            active.getAttributeNS(androidNs, "name").endsWith("${'$'}{applicationId}.launcher.$referenceAlias") ||
                active.getAttributeNS(androidNs, "name").endsWith(".launcher.$referenceAlias"),
        )
        assertEquals(
            "$path : icône de l'alias actif",
            "@mipmap/$referenceIcon",
            active.getAttributeNS(androidNs, "icon"),
        )
        assertEquals(
            "$path : icône ronde de l'alias actif",
            "@mipmap/${referenceIcon}_round",
            active.getAttributeNS(androidNs, "roundIcon"),
        )
    }

    @Test
    fun `l'icone declaree de l'APK est, octet pour octet, la couche de reference`() {
        val path = "src/main/AndroidManifest.xml"
        val app = elementsOf(document(path), "application").single()
        assertEquals("$path : icône de l'application", "@mipmap/ic_launcher", app.getAttributeNS(androidNs, "icon"))
        assertEquals(
            "$path : icône ronde de l'application",
            "@mipmap/ic_launcher_round",
            app.getAttributeNS(androidNs, "roundIcon"),
        )

        listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi").forEach { density ->
            val launcher = File("src/main/res/mipmap-$density/ic_launcher.png")
            val round = File("src/main/res/mipmap-$density/ic_launcher_round.png")
            val reference = File("src/main/res/mipmap-$density/$referenceIcon.png")
            val referenceRound = File("src/main/res/mipmap-$density/${referenceIcon}_round.png")

            assertTrue("${launcher.path} absent", launcher.isFile)
            assertTrue("${round.path} absent", round.isFile)
            assertTrue("${reference.path} absent", reference.isFile)
            assertTrue("${referenceRound.path} absent", referenceRound.isFile)

            assertTrue(
                "${launcher.path} n'est plus la couche de référence (${reference.path}) : " +
                    "l'icône de l'APK a changé",
                launcher.readBytes().contentEquals(reference.readBytes()),
            )
            assertTrue(
                "${round.path} n'est plus la couche ronde de référence (${referenceRound.path})",
                round.readBytes().contentEquals(referenceRound.readBytes()),
            )
        }
    }

    @Test
    fun `le choix primaire du selecteur suit le manifeste, ressource et alias nommes`() {
        val choice = com.onyx.app.ui.settings.AppIconChoice.GRAPHITE_BLOCK
        assertEquals(
            "AppIconSettings.kt : le choix primaire n'est plus l'icône de référence",
            choice,
            com.onyx.app.ui.settings.AppIconChoice.primary,
        )
        assertEquals("AppIconSettings.kt : ressource du choix primaire", referenceIcon, choice.resourceName)
        assertEquals("AppIconSettings.kt : alias du choix primaire", ".launcher.$referenceAlias", choice.aliasName)
    }

    // --- 3. Montre et voiture -----------------------------------------------------------------

    @Test
    fun `la montre et la voiture lisent aussi Onyx`() {
        val wear = File("../wear/src/main/res/values")
        val wearFile = File(wear, "strings.xml").path
        assertEquals("$wearFile : wear_app_name", "Onyx", strings(wear)["wear_app_name"])
        strings(wear).forEach { (name, value) ->
            assertFalse("$wearFile porte encore l'ancien libellé dans « $name »", value.contains(retiredLabel))
        }

        val auto = File("../auto/src/main/res/values")
        val autoFile = File(auto, "strings.xml").path
        assertEquals("$autoFile : auto_app_name", "Onyx", strings(auto)["auto_app_name"])
        strings(auto).forEach { (name, value) ->
            assertFalse("$autoFile porte encore l'ancien libellé dans « $name »", value.contains(retiredLabel))
        }

        val wearPath = "../wear/src/main/AndroidManifest.xml"
        val wearApp = elementsOf(document(wearPath), "application").single()
        assertEquals("$wearPath : libellé de la montre", "@string/wear_app_name", wearApp.getAttributeNS(androidNs, "label"))

        val autoPath = "../auto/src/main/AndroidManifest.xml"
        val autoApp = elementsOf(document(autoPath), "application").single()
        assertEquals("$autoPath : libellé de la voiture", "@string/auto_app_name", autoApp.getAttributeNS(androidNs, "label"))
    }
}
