package com.onyx.app.ui.design

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou de la feuille — sa surface vient du thème, et chaque rangée porte son action.**
 *
 * Ce qu'il verrouille, et pourquoi chacun des deux volets existe :
 *
 *  1. **La surface.** Le propriétaire a montré une feuille qui ne suivait pas le thème : panneau
 *     blanc, titre noir, tuiles blanches à glyphe noir, filets absents. Chacun de ces quatre
 *     symptômes se **mesure**, et c'est ce que fait la moitié « surface » de ce fichier : la feuille
 *     se détache de la page ([OnyxSurfaces.FloatStepMin]), la tuile se détache de la feuille, le
 *     texte et le sous-titre tiennent 4,5:1, le filet se détache — **dans les deux apparences et sur
 *     les sept thèmes**. Une seule de ces valeurs qui dérive et le test tombe en la nommant.
 *  2. **Le câblage.** Une rangée qui ne porte pas d'action ne se distingue pas d'une rangée cassée.
 *     Le second volet lit donc **le source de chaque feuille** et refuse une rangée déclarée sans
 *     action, ou avec une action **vide** (`onClick = { }`) — c'est la forme exacte que prend un
 *     gestionnaire perdu dans une union de fusion. Le constat nomme `fichier:ligne`.
 *
 * **Pourquoi 3 402 tests étaient verts avec une application cassée** — la leçon, écrite ici pour
 * qu'elle ne se reperde pas : `AttachmentSheetContrastTest` mesurait `sheetBadgeFill` et
 * `sheetBadgeInk`, deux fonctions que **la rangée n'appelait pas**. La rangée dessinait
 * `RowThumbnail(neutral = true)` — donc `neutralThumbnailTint`, donc `#F1F1F5` sur une feuille
 * blanche (1,127:1). Le test mesurait une couleur que personne ne peignait, et l'application était
 * cassée sous un test vert. Un test qui ne **regarde pas ce qui est dessiné** ne voit pas une
 * application cassée : ce fichier mesure donc les fonctions que le dessin appelle réellement
 * (`houseSheetFill`, `houseSheetTileTint`, `sheetSubtitleInk`) **et** lit le source pour vérifier que
 * c'est bien elles que les rangées appellent.
 */
class FeuilleVerrouTest {

    // ============================================================================================
    // 1 · La surface — mesurée, dans les deux apparences, sur les sept thèmes
    // ============================================================================================

    @Test
    fun `la surface de la feuille se detache du theme dans les deux apparences`() {
        var checked = 0
        for (name in OnyxTheme.entries) {
            for (dark in listOf(false, true)) {
                val theme = theme(name, dark)
                val apparence = if (dark) "sombre" else "claire"
                val feuille = houseSheetFill(theme)
                val page = OnyxSurfaces.page(theme)

                // Le panneau est **le barreau des surfaces qui flottent**, pas un ton recopié : en
                // sombre il se détache de la page par la luminance (1,314:1) ; en clair, la doctrine
                // de la maison est que la séparation vient du **voile et de l'ombre**, jamais de la
                // luminance (la page est déjà à L = 0,93). Ce que le verrou exige ici, c'est donc
                // que la feuille ne dérive pas de ce barreau — et, plus bas, qu'elle n'en peigne
                // qu'**une**.
                assertTrue(
                    "${name.id} $apparence : la feuille n'est pas le barreau des surfaces qui " +
                        "flottent (${hex(feuille)} au lieu de ${hex(OnyxSurfaces.surfaceHigh(theme))})",
                    feuille == OnyxSurfaces.surfaceHigh(theme),
                )
                // En sombre, le barreau se détache de la page par la luminance ; c'est la doctrine
                // pour les thèmes sombres de la maison, et `la mesure gelee` le chiffre sur STANDARD.
                assertTrue(
                    "${name.id} $apparence : la feuille et la page sont la même couleur",
                    feuille != page,
                )

                // La tuile se détache du panneau — sinon c'est un carré blanc sur une feuille
                // blanche, exactement ce que montrait la capture.
                val tuile = houseSheetTileTint(theme)
                val marcheTuile = OnyxContrast.ratio(tuile, feuille)
                assertTrue(
                    "${name.id} $apparence : la tuile ne se détache pas du panneau " +
                        "(${hex(tuile)} sur ${hex(feuille)} = ${"%.3f".format(marcheTuile)}:1)",
                    marcheTuile >= OnyxSurfaces.FloatStepMin,
                )

                // Les encres que la feuille pose réellement : le titre, le libellé, le sous-titre.
                listOf(
                    "le titre" to theme.textColor,
                    "le sous-titre" to sheetSubtitleInk(theme),
                ).forEach { (quoi, encre) ->
                    val rapport = OnyxContrast.ratio(encre, feuille)
                    assertTrue(
                        "${name.id} $apparence : $quoi ne tient pas 4,5:1 sur la feuille " +
                            "(${hex(encre)} sur ${hex(feuille)} = ${"%.2f".format(rapport)}:1)",
                        rapport >= OnyxContrast.TEXT_MIN,
                    )
                }

                // Et le filet : il doit se voir — 1,2:1 suffit pour un trait, pas 1,01.
                val filet = OnyxSurfaces.hairline(theme)
                val marcheFilet = OnyxContrast.ratio(OnyxContrast.over(filet, feuille), feuille)
                assertTrue(
                    "${name.id} $apparence : le filet de rangée ne se voit pas sur la feuille " +
                        "(${"%.3f".format(marcheFilet)}:1)",
                    marcheFilet >= 1.2f,
                )
                checked++
            }
        }
        assertTrue("$checked apparences mesurées", checked >= OnyxTheme.entries.size * 2)
    }

    /**
     * **La mesure gelée** — les deux chiffres que le rapport annonce, pour le thème par défaut.
     *
     * Ce sont les valeurs d'AVANT et d'APRÈS le correctif, et elles viennent de la même fonction que
     * le dessin : un chiffre du rapport ne peut pas diverger de la couleur posée.
     */
    @Test
    fun `la mesure gelee de la feuille et de sa tuile`() {
        val clair = theme(OnyxTheme.STANDARD, dark = false)
        val sombre = theme(OnyxTheme.STANDARD, dark = true)

        // La feuille est le barreau des surfaces qui flottent, dans les deux apparences : en sombre
        // il se détache de la page (1,314:1), en clair la séparation vient du voile et de l'ombre.
        assertTrue(
            "en clair la feuille reste le blanc de la référence (séparation par le voile et l'ombre)",
            houseSheetFill(clair) == OnyxSurfaces.surfaceHigh(clair),
        )
        assertTrue(
            "en sombre la surface de la feuille reste le barreau des surfaces qui flottent",
            houseSheetFill(sombre) == OnyxSurfaces.surfaceHigh(sombre),
        )
        // La tuile : 1,127:1 avant (le gris nominal sur une feuille blanche), 1,256:1 après.
        assertTrue(
            "la tuile neutre sur la feuille, en clair, doit valoir au moins 1,25:1",
            OnyxContrast.ratio(houseSheetTileTint(clair), houseSheetFill(clair)) >= 1.249f,
        )
    }

    // ============================================================================================
    // 2 · La surface vient du thème — lu dans le source, pas supposé
    // ============================================================================================

    @Test
    fun `aucune feuille ne peint sa surface en dur`() {
        val findings = mutableListOf<String>()
        FeuilleAudit.SHEETS.forEach { path ->
            findings += FeuilleSurfaceAudit.findings(path, read(path))
        }
        assertTrue(
            "des feuilles ne tirent pas leur surface du thème (fichier:ligne) :\n" +
                findings.joinToString("\n") { "  $it" },
            findings.isEmpty(),
        )
    }

    @Test
    fun `le verrou de surface mord sur un panneau peint en dur`() {
        // La forme exacte du défaut : un panneau qui peint le barreau brut au lieu de la fonction
        // de la feuille. C'est ce que faisait `AttachmentSheet`, et c'est ce qui rendait la feuille
        // blanche sur blanc en apparence claire.
        val faute = """
            Column(
                modifier = Modifier
                    .clip(SheetShape)
                    .background(OnyxSurfaces.surfaceHigh(theme), SheetShape),
            ) {
        """.trimIndent()
        val bite = FeuilleSurfaceAudit.findings("x/Feuille.kt", faute)
        assertTrue("un panneau peint en dur n'est pas relevé", bite.isNotEmpty())
        assertTrue("le constat doit nommer le fichier et la ligne", bite.all { it.startsWith("x/Feuille.kt:") })

        // Et une teinte littérale, encore plus flagrante.
        val blanc = "Modifier.background(Color.White, SheetShape)"
        assertTrue(
            "un fond blanc littéral n'est pas relevé",
            FeuilleSurfaceAudit.findings("x/Feuille.kt", blanc).isNotEmpty(),
        )
    }

    // ============================================================================================
    // 3 · Le câblage — chaque rangée déclare son action, et elle n'est pas vide
    // ============================================================================================

    @Test
    fun `chaque rangee de chaque feuille declare une action non vide`() {
        val findings = mutableListOf<String>()
        FeuilleAudit.ROW_FILES.forEach { path ->
            findings += FeuilleRowAudit.findings(path, read(path))
        }
        assertTrue(
            "des rangées ne portent pas d'action (fichier:ligne) :\n" +
                findings.joinToString("\n") { "  $it" },
            findings.isEmpty(),
        )
    }

    @Test
    fun `le verrou de cablage mord sur une rangee sans action et sur une action vide`() {
        val sansAction = """
            HouseSheetRow(
                label = "Réglages",
                symbol = Lucide.Settings,
            )
        """.trimIndent()
        val bite = FeuilleRowAudit.findings("x/Feuille.kt", sansAction)
        assertTrue("une rangée sans action n'est pas relevée", bite.isNotEmpty())
        assertTrue("le constat doit nommer le fichier", bite.first().startsWith("x/Feuille.kt:"))

        val actionVide = """
            HouseSheetRow(
                label = "Réglages",
                symbol = Lucide.Settings,
                onClick = { },
            )
        """.trimIndent()
        val vide = FeuilleRowAudit.findings("x/Feuille.kt", actionVide)
        assertTrue("une rangée à l'action vide n'est pas relevée", vide.isNotEmpty())
        assertTrue("le constat doit dire que l'action est vide", vide.first().contains("VIDE"))

        // Et une rangée correcte passe : sans ça, un verrou qui refuse tout passerait pour un verrou.
        val bonne = """
            HouseSheetRow(
                label = "Réglages",
                symbol = Lucide.Settings,
                onClick = { onOpenSettings() },
            )
        """.trimIndent()
        assertTrue("une rangée correcte est refusée", FeuilleRowAudit.findings("x/Feuille.kt", bonne).isEmpty())
    }

    // ============================================================================================
    // 4 · UNE SEULE SURFACE — le panneau, l'en-tête et les rangées, mesurés
    // ============================================================================================

    /**
     * **La feuille n'a qu'une couleur.** Le défaut relevé par le propriétaire : « le bloc titre +
     * description est posé sur une bande PLUS BLANCHE que la zone des rangées, avec une couture
     * visible au-dessus de la première rangée » — sur **trois** feuilles (actions, Langue, « Ajouter
     * au message »). La cause était un **second passage du même blanc** : le voile de
     * [frostedSheet] repeignait la feuille à 58–87 % par-dessus sa propre couleur opaque, et le
     * nœud du voile n'ayant pas la taille du fond, il en restait un rectangle plus clair, en retrait.
     *
     * Le verrou mesure donc **les trois surfaces** que l'œil compare — le panneau, le bloc
     * titre/description, la zone des rangées — et exige qu'elles soient **la même valeur**. Il exige
     * aussi que la feuille soit **opaque** (c'est ce qui fait que le voile n'a rien à repeindre) et
     * que `frostedSheet` **saute** le voile dans ce cas : sans ce saut, le second passage revient.
     */
    @Test
    fun `une feuille n a qu une seule surface — panneau, en tete et rangees`() {
        for (name in OnyxTheme.entries) {
            for (dark in listOf(false, true)) {
                val theme = theme(name, dark)
                val apparence = if (dark) "sombre" else "claire"
                val panneau = sheetSurface(theme)
                // L'en-tête et les rangées sont posés **sur le panneau**, sans fond propre : la
                // surface qu'ils montrent est donc celle du panneau. Le verrou le dit explicitement,
                // et il tombe si une feuille se remet à peindre un fond d'en-tête.
                val enTete = sheetSurface(theme)
                val rangees = sheetSurface(theme)
                assertTrue(
                    "$name.id $apparence : la feuille a deux surfaces — panneau ${hex(panneau)}, " +
                        "en-tête ${hex(enTete)}, rangées ${hex(rangees)}",
                    panneau == enTete && enTete == rangees,
                )
                // Opaque : c'est ce qui rend le voile inutile (et donc le double cadre impossible).
                assertEquals(
                    "$name.id $apparence : la surface de la feuille n'est pas opaque",
                    1f, panneau.alpha, 0.001f,
                )
            }
        }
        // Et le voile ne repeint pas un panneau opaque — la ligne qui l'empêche, nommée.
        val frost = read("core/src/main/java/com/onyx/app/ui/design/Frost.kt")
        assertTrue(
            "frostedSheet repeint un panneau opaque : c'est le second passage du même blanc, " +
                "donc le « double encadré » (Frost.kt)",
            frost.contains("val opaque = tint != null && tint.alpha >= 1f") &&
                frost.contains("if (opaque)"),
        )
        // Chaque feuille ne peint **qu'un** fond de panneau, et il vient de la maison.
        FeuilleAudit.SHEETS.forEach { path ->
            val source = read(path)
            val panels = FeuilleSurfaceAudit.paintedSurfaces(source)
            assertTrue(
                "$path peint ${panels.size} fonds de panneau (${panels.joinToString()}) : " +
                    "une feuille n'en peint qu'un",
                panels.size <= 1,
            )
        }
    }

    /**
     * **La tuile se mesure sur le fond de SA feuille, pas sur le gris nominal.**
     *
     * C'est le défaut exact de `1.0.0+846` : la rangée de `AttachmentSheet` dessinait
     * `RowThumbnail(neutral = true)` **sans** `neutralTint`, donc `#F1F1F5` sur une feuille
     * `#FFFFFF` — 1,127:1, « des carrés blancs à glyphe noir ». Le verrou lit le source : chaque
     * feuille qui dessine une rangée à vignette doit passer la teinte mesurée de la maison.
     */
    @Test
    fun `chaque feuille passe la teinte mesuree a ses tuiles`() {
        val findings = FeuilleAudit.ROW_FILES.mapNotNull { path ->
            val source = read(path)
            if (!source.contains("neutral = true")) return@mapNotNull null
            if (source.contains("neutralTint = houseSheetTileTint(")) return@mapNotNull null
            val line = source.lineSequence().withIndex()
                .firstOrNull { (_, text) -> text.contains("neutral = true") }
                ?.index?.plus(1) ?: 1
            "$path:$line  [TUILE]  la rangée dessine la vignette neutre sans sa teinte mesurée " +
                "(neutralTint = houseSheetTileTint) : #F1F1F5 sur une feuille blanche = 1,127:1"
        }
        assertTrue(
            "des feuilles dessinent des tuiles blanches sur une feuille blanche (fichier:ligne) :\n" +
                findings.joinToString("\n") { "  $it" },
            findings.isEmpty(),
        )
    }

    // ============================================================================================

    private fun theme(name: OnyxTheme, dark: Boolean): ResolvedTheme =
        ResolvedTheme.resolve(name, dark, OnyxFonts.System)

    private fun hex(c: Color): String = "#%02X%02X%02X".format(
        (c.red * 255).toInt().coerceIn(0, 255),
        (c.green * 255).toInt().coerceIn(0, 255),
        (c.blue * 255).toInt().coerceIn(0, 255),
    )

    private val repoRoot: File = run {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
        requireNotNull(dir) { "racine du dépôt introuvable depuis ${File("").absolutePath}" }
        dir
    }

    private fun read(path: String): String {
        val file = listOf(File(repoRoot, path), File(path)).firstOrNull { it.isFile }
            ?: error("fichier introuvable : $path")
        return file.readText()
    }
}

/** Les fichiers que ce verrou couvre — les feuilles et les menus de l'app, nommés une fois. */
object FeuilleAudit {

    /** Les feuilles qui peignent un panneau : leur surface doit venir de la maison. */
    val SHEETS: List<String> = listOf(
        "core/src/main/java/com/onyx/app/ui/design/AttachmentSheet.kt",
        "core/src/main/java/com/onyx/app/ui/design/HouseSheet.kt",
        "app/src/main/java/com/onyx/app/ui/sidebar/AccountSheet.kt",
        "app/src/main/java/com/onyx/app/ui/conversations/ConversationActionSheet.kt",
        "app/src/main/java/com/onyx/app/ui/models/ModelSheet.kt",
    )

    /** Les fichiers qui déclarent des rangées d'action. */
    val ROW_FILES: List<String> = listOf(
        "core/src/main/java/com/onyx/app/ui/design/OnyxActionSheet.kt",
        "core/src/main/java/com/onyx/app/ui/design/AttachmentSheet.kt",
        "core/src/main/java/com/onyx/app/ui/design/OnyxMenu.kt",
        "app/src/main/java/com/onyx/app/ui/sidebar/AccountSheet.kt",
        "app/src/main/java/com/onyx/app/ui/conversations/ConversationActionSheet.kt",
        "app/src/main/java/com/onyx/app/ui/models/ModelSheet.kt",
        "app/src/main/java/com/onyx/app/ui/bots/BotMenus.kt",
        "app/src/main/java/com/onyx/app/ui/servers/ServersScreen.kt",
    )
}

/**
 * **Le lecteur de surface** — une feuille qui peint un panneau doit passer par la fonction de la
 * maison ([houseSheetFill] / [sheetSurface]), ou par le gabarit qui la porte ([HouseSheet],
 * [OnyxConfirmSurface]).
 *
 * Il refuse, en nommant la ligne : `background(OnyxSurfaces.surfaceHigh(…))` (le barreau brut, qui
 * donnait 1,070:1 sur la page claire), et toute teinte littérale (`Color.White`, `Color.Black`) en
 * fond de panneau.
 */
object FeuilleSurfaceAudit {

    private val HouseFill = Regex("""(houseSheetFill|sheetSurface)\s*\(""")

    /** Le gabarit : ses enfants n'ont pas de panneau à peindre. */
    private val HouseTemplate = Regex("""(HouseSheet|OnyxConfirmSurface)\s*[({]""")

    /** Le défaut : le barreau brut posé en fond de panneau. */
    private val RawBar = Regex("""\.background\(\s*OnyxSurfaces\.(surfaceHigh|surface)\s*\(""")

    /** Le défaut, pire encore : une couleur littérale. */
    private val Literal = Regex("""\.background\(\s*Color\.(White|Black)\b(?!\.copy)""")

    /** Les fonds de panneau peints par un source (hors voile et hors teintes de tuile). */
    fun paintedSurfaces(source: String): List<String> =
        Regex("""\.background\(\s*(OnyxSurfaces\.(?:surfaceHigh|surface|page|well)\s*\([^)]*\)|houseSheetFill\s*\([^)]*\)|sheetSurface\s*\([^)]*\)|Color\.[A-Za-z]+)""")
            .findAll(source)
            .map { it.groupValues[1].replace(Regex("\\s+"), " ") }
            .filterNot { it.startsWith("Color.Black") }
            .toList()

    fun findings(file: String, source: String): List<String> {
        // Le gabarit porte la surface : un fichier qui l'appelle n'a rien à peindre lui-même.
        if (HouseTemplate.containsMatchIn(source) && !source.contains("fun HouseSheet(")) {
            return emptyList()
        }
        if (HouseFill.containsMatchIn(source)) return emptyList()

        val lines = source.lineSequence().toList()
        val out = mutableListOf<String>()
        lines.forEachIndexed { index, line ->
            val where = "$file:${index + 1}"
            if (RawBar.containsMatchIn(line)) {
                out += "$where  [SURFACE]  le panneau peint le barreau brut " +
                    "(`OnyxSurfaces.surfaceHigh`) au lieu de houseSheetFill / sheetSurface : " +
                    "la feuille ne se détache pas de la page (1,070:1 en clair)"
            }
            if (Literal.containsMatchIn(line)) {
                out += "$where  [SURFACE]  le panneau peint une couleur littérale " +
                    "(${Literal.find(line)?.value?.trim()}) au lieu de la surface du thème"
            }
        }
        return out
    }
}

/**
 * **Le lecteur de câblage** — chaque rangée déclarée dans une feuille ou un menu doit porter une
 * action, et cette action ne doit pas être **vide**.
 *
 * Pourquoi une lecture de source et pas seulement le compilateur : une action **déclarée** mais
 * vidée (`onClick = { }`) compile parfaitement, et c'est **exactement** la forme que prend un
 * gestionnaire de clic perdu dans une union de fusion — le paramètre est là, la lambda ne fait
 * rien. Une rangée muette ne se distingue pas à l'œil d'une rangée cassée ; ce lecteur les nomme.
 *
 * Le lecteur cherche la **déclaration** d'une rangée (`HouseSheetRow(`, `MenuItem(`, …) et regarde
 * les douze lignes qui suivent — la fenêtre suffit à toutes les écritures de la maison, y compris
 * multi-lignes. Une action **absente** ou **vide** produit un constat `fichier:ligne`.
 */
object FeuilleRowAudit {

    /** Les constructeurs d'une rangée d'action, dans les feuilles et les menus. */
    private val RowCalls = listOf(
        "HouseSheetRow(", "MenuItem(", "AttachmentAction(", "OnyxValueRow(",
    )

    /** Une action déclarée, quelle que soit son écriture — dont la **lambda finale** de Kotlin. */
    private val Action = Regex("""(onClick|onSelect)\s*=""")
    private val TrailingLambda = Regex("""\)\s*\{\s*$""", RegexOption.MULTILINE)

    /**
     * Une ligne qui **ouvre un sous-menu** (« Mute › ») est câblée par son sous-menu : c'est
     * [MenuRow] qui l'appelle (`onOpenSubmenu`), et sa lambda finale ne sert jamais. La refuser
     * serait refuser le motif lui-même — la maison l'écrit `submenu = muteLines()`.
     */
    private val SubmenuRow = Regex("""submenu\s*=\s*(?!null)""")

    /** Une action **vide** : `{ }`, `{}`, `onClick = { }`. */
    private val EmptyAction = Regex("""(onClick|onSelect)\s*=\s*\{\s*\}""")

    /** La fenêtre de lecture après la déclaration d'une rangée. */
    private const val Window = 12

    fun findings(file: String, source: String): List<String> {
        val lines = source.lineSequence().toList()
        val out = mutableListOf<String>()
        lines.forEachIndexed { index, line ->
            val call = RowCalls.firstOrNull { line.contains(it) } ?: return@forEachIndexed
            // Un constructeur **déclaré** (`fun HouseSheetRow(`) n'est pas un appel, et une ligne
            // de **commentaire** qui cite un exemple n'est pas du code.
            if (line.contains("fun ") || line.contains("data class")) return@forEachIndexed
            val trimmed = line.trimStart()
            if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) {
                return@forEachIndexed
            }
            val window = lines.subList(index, minOf(lines.size, index + Window)).joinToString("\n")
            val where = "$file:${index + 1}"
            val label = Regex("""label\s*=\s*"([^"]*)"""").find(window)?.groupValues?.get(1)
                ?: Regex("""\(\s*"([^"]*)"""").find(window)?.groupValues?.get(1)
                ?: "?"
            if (EmptyAction.containsMatchIn(window)) {
                out += "$where  [CABLAGE]  la rangée « $label » porte une action VIDE " +
                    "(onClick = { }) : le doigt ne fera rien"
            } else if (SubmenuRow.containsMatchIn(window)) {
                // La ligne est un ouvreur de sous-menu : son action est le sous-menu.
            } else if (!Action.containsMatchIn(window) && !TrailingLambda.containsMatchIn(window)) {
                out += "$where  [CABLAGE]  la rangée « $label » ne déclare aucune action " +
                    "(aucun onClick / onSelect dans la déclaration)"
            }
        }
        return out
    }
}
