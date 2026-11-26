package com.onyx.app

import com.onyx.app.ui.settings.AppIconChoice
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Le sélecteur ne doit exposer que des icônes réellement embarquées et réellement lançables. */
class AppIconCatalogTest {
    @Test
    fun `les quatorze choix suivent lordre et le primaire de la reference`() {
        assertEquals(
            listOf(
                "AppIconFaceMist",
                "AppIcon",
                "AppIconGraphiteBlock",
                "AppIconSteelBlock",
                "AppIconMist",
                "AppIconGraphiteFlat",
                "AppIconGraphiteBlockFlat",
                "AppIconSteelFlat",
                "AppIconMistFlat",
                "AppIconNavyBlock",
                "AppIconInk",
                "AppIconPaperNavy",
                "AppIconGold",
                "AppIconClassic",
            ),
            AppIconChoice.entries.map { it.rawValue },
        )
        assertEquals(AppIconChoice.GRAPHITE_BLOCK, AppIconChoice.primary)
        assertEquals(14, AppIconChoice.entries.map { it.aliasName }.distinct().size)
        assertEquals(14, AppIconChoice.entries.map { it.resourceName }.distinct().size)
    }

    @Test
    fun `chaque choix a ses images carrees et rondes aux cinq densites`() {
        val densities = listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")
        AppIconChoice.entries.forEach { choice ->
            densities.forEach { density ->
                assertTrue(
                    "missing $density/${choice.resourceName}",
                    File("src/main/res/mipmap-$density/${choice.resourceName}.png").isFile,
                )
                assertTrue(
                    "missing round $density/${choice.resourceName}",
                    File("src/main/res/mipmap-$density/${choice.resourceName}_round.png").isFile,
                )
            }
        }
    }

    @Test
    fun `chaque choix a un alias launcher et MainActivity na pas son propre lanceur`() {
        val androidNs = "http://schemas.android.com/apk/res/android"
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val aliases = document.getElementsByTagName("activity-alias")
        assertEquals(AppIconChoice.entries.size, aliases.length)

        AppIconChoice.entries.forEach { choice ->
            val alias = (0 until aliases.length).map { aliases.item(it) as Element }.firstOrNull {
                // L'alias est nommé d'après le paquet **installé** (`${applicationId}`) : c'est ce
                // que le code reconstruit à l'exécution avec `context.packageName`.
                it.getAttributeNS(androidNs, "name") == "\${applicationId}" + choice.aliasName
            }
            assertNotNull("missing alias ${choice.aliasName}", alias)
            alias!!
            assertFalse(
                "un alias nommé sans le paquet installé ne bascule jamais : ${choice.aliasName}",
                (alias.getAttributeNS(androidNs, "name")).startsWith("."),
            )
            assertEquals(".MainActivity", alias.getAttributeNS(androidNs, "targetActivity"))
            assertEquals("@mipmap/${choice.resourceName}", alias.getAttributeNS(androidNs, "icon"))
            assertEquals("@mipmap/${choice.resourceName}_round", alias.getAttributeNS(androidNs, "roundIcon"))
            assertEquals(
                (choice == AppIconChoice.primary).toString(),
                alias.getAttributeNS(androidNs, "enabled"),
            )
            val actions = alias.getElementsByTagName("action")
            assertTrue((0 until actions.length).any {
                (actions.item(it) as Element).getAttributeNS(androidNs, "name") == "android.intent.action.MAIN"
            })
            val categories = alias.getElementsByTagName("category")
            assertTrue((0 until categories.length).any {
                (categories.item(it) as Element).getAttributeNS(androidNs, "name") == "android.intent.category.LAUNCHER"
            })
        }

        val activities = document.getElementsByTagName("activity")
        val main = (0 until activities.length).map { activities.item(it) as Element }.single {
            it.getAttributeNS(androidNs, "name") == ".MainActivity"
        }
        val mainActions = main.getElementsByTagName("action")
        assertFalse((0 until mainActions.length).any {
            (mainActions.item(it) as Element).getAttributeNS(androidNs, "name") == "android.intent.action.MAIN"
        })
    }
}
