package com.onyx.app.ui.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou des glyphes des Réglages.**
 *
 * Le défaut relevé par le propriétaire : la rangée « Appels » de Réglages portait `phone.fill`,
 * dont le dessin était un **rectangle arrondi plein** — un carré noir — au milieu d'une colonne de
 * glyphes **au trait** (document, cloche, téléphone-appareil, barres, clé, silhouettes, buste…).
 *
 * Ce test balaie **toutes les sources de `ui/settings`** (Réglages, Apparence et icône d'app,
 * Téléphone et Appels, Verrouillage, Connexion, Notifications, Voix et ses sous-écrans, Android
 * Auto), résout chaque `Symbols.x` / `Lucide.x` / `LucideMenuIcons.x` vers son vectoriel, lit le
 * vectoriel — et **échoue en nommant le fichier et la ligne** dès qu'un glyphe **plein** (une
 * surface peinte, aucun trait) est employé hors du **jeu autorisé** ci-dessous.
 *
 * Le jeu autorisé n'est pas une commodité : chaque entrée est un **état**, un **voyant**, une
 * **commande** ou un **dessin plein de la famille**, et sa raison est écrite ici. Une icône de
 * rangée qui n'est pas dans cette liste doit être **au trait** — c'est la règle de la colonne.
 */
class SettingsGlyphFamilyTest {

    // ---- le jeu autorisé : un plein, et sa raison ------------------------------------------------

    private val allowed: Map<String, String> = mapOf(
        "ic_sym_checkmark_circle_fill" to
            "l'ÉTAT d'une rangée — le contrôle de droite (serveur actif, délai de verrouillage, " +
            "politique choisie, préfixe prononcé) —, jamais la vignette de gauche.",
        "ic_sym_xmark_octagon_fill" to
            "le VOYANT d'erreur d'un en-tête refusé, en rouge ; la forme rouge est le message.",
        "ic_sym_minus_circle_fill" to
            "une COMMANDE — le pas « une demi-heure plus tôt », « un préfixe avant » : deux cibles " +
            "tactiles de 18 dp, pas une tuile de liste.",
        "ic_sym_plus_circle_fill" to
            "la COMMANDE jumelle du pas (« une demi-heure plus tard »).",
        "ic_sym_moon_fill" to
            "l'ÉTAT d'apparence du contrôle segmenté (téléphone / soleil / lune), à côté de " +
            "`SunMax` : ce n'est pas une vignette de rangée mais l'icône d'un choix.",
        "ic_sym_person_2_fill" to
            "les SILHOUETTES de la famille (rangée « Bots », liste des préfixes) : deux formes " +
            "fermées, pas une surface — c'est le dessin que le propriétaire lit comme des " +
            "« silhouettes », et le jeu plein accepté par `MenuIconFamilyGeometryTest`.",
    )

    // ---- lecture des sources ---------------------------------------------------------------------

    /** Le préfixe des messages, pour que le relevé se lise depuis la racine du dépôt. */
    private val prefix = "app/src/main/java/com/onyx/app/"

    private fun design(path: String): File =
        listOf(File("../core/src/main/java/com/onyx/app/$path"), File("core/src/main/java/com/onyx/app/$path"))
            .first { it.isFile }

    private val drawables: File =
        listOf(File("../core/src/main/res/drawable"), File("core/src/main/res/drawable"))
            .first { it.isDirectory }

    /** `Symbols.x = R.drawable.y` → `x` vers `y`, pour les trois tables de symboles. */
    private fun table(path: String): Map<String, String> =
        Regex("val (\\w+) = R\\.drawable\\.(\\w+)")
            .findAll(design(path).readText())
            .associate { it.groupValues[1] to it.groupValues[2] }

    private val tables: Map<String, Map<String, String>> = mapOf(
        "Symbols" to table("ui/design/Symbols.kt"),
        "Lucide" to table("ui/design/LucideSymbols.kt"),
        "LucideMenuIcons" to table("ui/design/LucideMenuIcons.kt"),
    )

    /** Toutes les sources de `ui/settings`, sous-écrans compris. */
    private fun settingsSources(): List<File> {
        val root = File("src/main/java/com/onyx/app/ui/settings")
        assertTrue("les sources des Réglages sont là", root.isDirectory)
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.sorted().toList()
    }

    private fun relative(file: File): String =
        prefix + file.path.removePrefix("src/main/java/com/onyx/app/").replace(File.separatorChar, '/')

    /**
     * Un glyphe **plein** : le vectoriel déclare une **surface peinte** (un `fillColor` opaque) et
     * **aucun trait** (`strokeWidth`). C'est la forme du carré noir ; un dessin « mixte » (un aplat
     * de détail dans un tracé) n'en fait pas partie.
     */
    private fun isSolid(drawable: String): Boolean {
        val file = File(drawables, "$drawable.xml")
        if (!file.isFile) return false
        val xml = file.readText()
        val painted = Regex("android:fillColor=\"([^\"]*)\"")
            .findAll(xml)
            .map { it.groupValues[1] }
            .any { it.startsWith("#") && !it.startsWith("#00") && !it.startsWith("#00000000") }
        val stroked = Regex("android:strokeWidth=\"([\\d.]+)\"")
            .findAll(xml)
            .any { it.groupValues[1].toDouble() > 0.0 }
        return painted && !stroked
    }

    /** Le verdict d'une ligne de source : la liste des glyphes pleins hors du jeu autorisé. */
    private fun scanLine(file: String, line: Int, text: String): List<String> =
        Regex("\\b(Symbols|Lucide|LucideMenuIcons)\\.(\\w+)")
            .findAll(text)
            .mapNotNull { match ->
                val drawable = tables[match.groupValues[1]]?.get(match.groupValues[2]) ?: return@mapNotNull null
                if (!isSolid(drawable) || drawable in allowed) return@mapNotNull null
                "$file:$line — ${match.groupValues[1]}.${match.groupValues[2]} → $drawable.xml"
            }
            .toList()

    private fun sweep(): List<String> = settingsSources().flatMap { file ->
        file.readLines().withIndex().flatMap { (index, line) ->
            scanLine(relative(file), index + 1, line)
        }
    }

    // ---- 1. le verrou ----------------------------------------------------------------------------

    @Test
    fun `une rangee de reglages ne porte pas de glyphe plein hors du jeu autorise`() {
        val intruders = sweep()
        assertTrue(
            "des glyphes pleins hors du jeu autorisé :\n" +
                intruders.joinToString("\n") { "  $it" },
            intruders.isEmpty(),
        )
    }

    @Test
    fun `le detecteur mord — il nomme le plein d avant, son fichier et sa ligne`() {
        // La ligne **d'avant le correctif**, telle quelle (rangée « Appels » de Réglages) : le
        // détecteur doit la dénoncer avec son fichier et sa ligne — c'est la preuve de morsure.
        val before = "                        AgentRow(\"Calls\", Symbols.PhoneFill, true, openCalls)"
        assertEquals(
            listOf(
                "app/src/main/java/com/onyx/app/ui/settings/SettingsScreen.kt:381 — " +
                    "Symbols.PhoneFill → ic_sym_phone_fill.xml",
            ),
            scanLine("app/src/main/java/com/onyx/app/ui/settings/SettingsScreen.kt", 381, before),
        )
    }

    @Test
    fun `le detecteur laisse passer ce qui est au trait et ce qui est autorise`() {
        assertTrue(
            "un glyphe au trait est hors de cause",
            scanLine("x.kt", 1, "AgentRow(\"Phone tools\", Symbols.Phone, true, openPhoneTools)").isEmpty(),
        )
        assertTrue(
            "un état du jeu autorisé est hors de cause",
            scanLine("x.kt", 1, "symbol = Symbols.CheckmarkCircle,").isEmpty(),
        )
        assertTrue(
            "un indirection (`symbolFor`) ne fait pas tomber le relevé",
            scanLine("x.kt", 1, "PhoneArea.PHOTOS -> Symbols.PhotoOnRectangle").isEmpty(),
        )
    }

    // ---- 2. le jeu autorisé ne rouille pas -------------------------------------------------------

    @Test
    fun `chaque plein autorise dit sa raison et sert encore`() {
        val used = settingsSources().flatMap { file ->
            Regex("\\b(Symbols|Lucide|LucideMenuIcons)\\.(\\w+)").findAll(file.readText()).map { match ->
                tables[match.groupValues[1]]?.get(match.groupValues[2])
            }.toList()
        }
        allowed.forEach { (drawable, reason) ->
            assertTrue("$drawable : le vectoriel manque", File(drawables, "$drawable.xml").isFile)
            assertTrue("$drawable : plus utilisé dans les Réglages — la liste doit maigrir", drawable in used)
            assertTrue("$drawable : la raison est vide", reason.trim().length > 20)
        }
    }

    // ---- 3. la colonne : une seule boîte, un seul trait, les mêmes mesures ------------------------

    @Test
    fun `tous les glyphes des Reglages sont sur la boite de vingt-quatre`() {
        val glyphs = settingsSources().flatMap { file ->
            Regex("\\b(Symbols|Lucide|LucideMenuIcons)\\.(\\w+)").findAll(file.readText()).mapNotNull { match ->
                tables[match.groupValues[1]]?.get(match.groupValues[2])
            }
        }.distinct()
        assertTrue("des glyphes sont lus", glyphs.size >= 30)
        glyphs.forEach { drawable ->
            val xml = File(drawables, "$drawable.xml").readText()
            listOf("width", "height", "viewportWidth", "viewportHeight").forEach { name ->
                val value = Regex("android:$name=\"([^\"]*)\"")
                    .find(xml)!!.groupValues[1].removeSuffix("dp").toDouble()
                assertEquals("$drawable : la boîte ($name)", 24.0, value, 0.001)
            }
        }
    }

    @Test
    fun `la vignette de rangee tient ses mesures, pour toutes les rangees`() {
        val anatomy = design("ui/design/OnyxAnatomy.kt").readText()
        // La tuile : 32 dp de côté, rayon 10, glyphe 15 — les trois nombres de la référence.
        assertTrue("le côté de la tuile", anatomy.contains("val TileSize: Dp = 32.dp"))
        assertTrue("le rayon de la tuile", anatomy.contains("val TileCorner: Dp = 10.dp"))
        assertTrue("le côté du glyphe", anatomy.contains("val TileIcon: Dp = 15.dp"))
        // La rangée : rembourrage 14/13, écart 14, hauteur 56 — les mêmes pour toutes les rangées.
        assertTrue("le rembourrage horizontal", anatomy.contains("val RowPaddingH: Dp = 14.dp"))
        assertTrue("le rembourrage vertical", anatomy.contains("val RowPaddingV: Dp = 13.dp"))
        assertTrue("l'écart", anatomy.contains("val RowGap: Dp = 14.dp"))
        assertTrue("la hauteur minimale", anatomy.contains("val NavRowMinHeight: Dp = 56.dp"))
        // Le filet part du libellé : rembourrage + tuile + écart.
        assertTrue(
            "le retrait du filet",
            anatomy.contains("val DividerInsetWithTile: Dp = RowPaddingH + TileSize + RowGap"),
        )
        // Et la tuile **applique** ces trois nombres — c'est ce qui rend la colonne uniforme.
        assertTrue("la tuile prend le côté", anatomy.contains(".size(Anatomy.TileSize)"))
        assertTrue("la tuile prend le rayon", anatomy.contains("RoundedCornerShape(Anatomy.TileCorner)"))
        assertTrue("la tuile prend le côté du glyphe", anatomy.contains("size = Anatomy.TileIcon"))
    }

    // ---- 4. la rangée « Appels » et ses voisines -------------------------------------------------

    @Test
    fun `la rangee Appels porte le combine au trait, et plus aucun plein de telephone`() {
        val screen = File("src/main/java/com/onyx/app/ui/settings/SettingsScreen.kt").readText()
        assertTrue(
            "la rangée « Appels » porte le combiné au trait",
            screen.contains("AgentRow(\"Calls\", Symbols.PhoneOutline, true, openCalls)"),
        )
        assertTrue(
            "le combiné au trait existe et est déclaré",
            table("ui/design/Symbols.kt")["PhoneOutline"] == "ic_sym_phone",
        )
        // Le carré noir a quitté les Réglages **et** l'écran Appels.
        settingsSources().forEach { file ->
            assertFalse(
                "${relative(file)} porte encore Symbols.PhoneFill",
                file.readText().contains("Symbols.PhoneFill"),
            )
        }
    }

    @Test
    fun `la colonne des rangees de reglages ne porte plus que du trait`() {
        // Le reste de la colonne : les couleurs des rangées de Réglages, une à une, au trait.
        val screen = File("src/main/java/com/onyx/app/ui/settings/SettingsScreen.kt").readText()
        listOf(
            "Appearance" to "Symbols.PaintbrushOutline",
            "Language" to "Symbols.Globe",
            "Model" to "Symbols.BoltOutline",
            "Voice" to "Symbols.Waveform",
            "Logs" to "Symbols.Doc",
            "Notifications" to "Symbols.Bell",
            "Phone tools" to "Symbols.Phone",
            "Calls" to "Symbols.PhoneOutline",
            "Capabilities" to "Symbols.SquareStack3dUp",
            "Insights" to "Symbols.ChartBar",
            "Tools" to "Symbols.WrenchAndScrewdriver",
            "Profile" to "Symbols.PersonCircle",
            "Memory" to "Symbols.Brain",
            "Files" to "Symbols.Folder",
            "Gateway" to "Symbols.Gearshape",
            "Cron" to "Symbols.Clock",
            "Kanban" to "Symbols.RectangleSplit",
            "Terminal" to "Symbols.Terminal",
            "Lock" to "Symbols.LockOutline",
            "Set up another device" to "Symbols.QrCode",
            "Acknowledgements" to "Symbols.Doc",
            "What's New" to "Lucide.Sparkles",
            "Support Onyx" to "Symbols.Heart",
            "Servers" to "Symbols.ServerRack",
        ).forEach { (label, symbol) ->
            assertTrue("$label : $symbol", screen.contains(symbol))
        }
        // Le modèle et les deux « sparkles » ont quitté le plein.
        assertFalse("le modèle ne porte plus l'éclair plein", screen.contains("symbol = Symbols.Bolt,"))
        assertFalse("la rangée « suggestions » ne porte plus l'étoile pleine", screen.contains("symbol = Symbols.Sparkles,"))
        // Le micro des deux rangées de la voix, au trait comme dans le compositeur.
        val voice = File("src/main/java/com/onyx/app/ui/settings/VoiceSettingsScreen.kt").readText()
        assertFalse("le micro plein a quitté les rangées de la voix", voice.contains("symbol = Symbols.Mic,"))
        assertTrue("les deux rangées portent le micro au trait", voice.contains("symbol = Symbols.MicOutline,"))
    }
}
