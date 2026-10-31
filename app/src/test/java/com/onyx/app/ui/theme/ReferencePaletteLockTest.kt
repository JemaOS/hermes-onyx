package com.onyx.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.design.OnyxContrast
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou de la palette.**
 *
 * Une charte ne tient pas parce qu'elle est écrite, mais parce que **rien ne peut la contourner**.
 * Ce test tombe donc dans trois sens :
 *
 * 1. **Aucune couleur hors palette dans l'interface** — tout `Color(0x…)` ou `Color(<flottant>,
 *    …)` écrit dans `core/src/main` ou `app/src/main` fait **échouer** le test, sauf dans les
 *    fichiers de l'[AllowList], chacun avec sa raison. Une couleur ajoutée « juste pour voir » ne
 *    passe pas la construction : c'est le seul moyen qu'un « je changerai ça plus tard » ne devienne
 *    pas un accent de plus six mois après.
 * 2. **La palette elle-même reste neutre** — tout littéral de [ReferencePalette] est soit un gris
 *    (écart rouge-bleu ≤ 10 points), soit l'un des **cinq** rôles saturés admis : l'accent, le
 *    danger, le succès, l'avertissement — et rien d'autre. Un violet ajouté à la palette casse ici.
 * 3. **L'accent est unique** — le thème par défaut, résolu, ne porte **qu'une** couleur saturée :
 *    son accent. Toutes les autres — page, surfaces, encres, séparateur — sont des gris. C'est la
 *    règle du propriétaire, mesurée au lieu d'être promise.
 */
class ReferencePaletteLockTest {

    /** La racine du dépôt : le dossier qui contient `settings.gradle.kts`. */
    private val root: File = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    /**
     * Les fichiers autorisés à peindre une couleur, et **pourquoi**.
     *
     * La liste est courte par construction : c'est le point du verrou. Y ajouter une entrée est une
     * décision, et elle doit s'écrire ici.
     */
    private val allowList: Map<String, String> = mapOf(
        "core/src/main/java/com/onyx/app/ui/theme/ReferencePalette.kt" to
            "la palette elle-même — la table de la référence, relue par le test 2",
        "core/src/main/java/com/onyx/app/ui/theme/OnyxTheme.kt" to
            "l'identité des sept thèmes (Paper, Terminal, Amber…) et les palettes de coloration " +
                "syntaxique : ce sont des choix du lecteur et une information, pas une décoration",
        "core/src/main/java/com/onyx/app/ui/design/OnyxContrast.kt" to
            "`DarkInk` `#1B1B1D`, le presque-noir maison — neutre (2 points), il porte l'encre des " +
                "boutons pleins de toute l'app",
        "core/src/main/java/com/onyx/app/ui/design/OnyxSocle.kt" to
            "les **encres d'ombre** du socle : trois crans de la même famille (proche, moyen, " +
                "large), recopiés du générateur (hcss.py:16-20). Ce ne sont pas des couleurs de " +
                "décoration — ce sont les trois opacités d'une ombre, et le socle les tient en un " +
                "seul endroit ([OnyxSocle.shadowInk])",
        "core/src/main/java/com/onyx/app/ui/design/BotAvatar.kt" to
            "le visage d'un bot et sa palette : c'est l'identité d'un bot, elle doit rester " +
                "distincte d'un bot à l'autre (§4 « ne perds rien »)",
        "core/src/main/java/com/onyx/app/ui/design/Fluide.kt" to
            "le fluide d'une carte de conversation : ses couleurs sont **tirées de la graine de " +
                "la conversation** (six teintes de la maison dérivées en HSL, comme la couverture " +
                "générative) — c'est l'identité d'une conversation, pas une décoration. Les seules " +
                "valeurs fixes sont le sol et le voile de page de la matière, recopiés de la " +
                "maquette (`carte.html`) et posés en un seul endroit ([Fluide.plan]). Le champ " +
                "d'écoulement lui-même est un programme de rendu, pas une couleur.",
        "core/src/main/java/com/onyx/app/ui/design/Matter.kt" to
            "les deux **ancres de température** de la matière — un ambre (`WarmAnchor`) et un bleu " +
                "(`CoolAnchor`). Ce ne sont pas des teintes d'identité : ce sont les points vers " +
                "lesquels l'accent d'un thème est tiré pour donner du **chaud** et du **froid** au " +
                "fond, comme la référence du propriétaire. Une couleur de thème ne peut pas les " +
                "porter : un thème à accent doré n'aurait aucune zone froide. La mesure est dans " +
                "`MatterLegibilityTest` (températures opposées, encre tenue sur le pire cas)",
        "core/src/main/java/com/onyx/app/ui/design/OnyxBrand.kt" to
            "**la marque de l'application — le logo du lanceur, à l'identique.** Ce ne sont pas " +
                "des couleurs de thème, et c'est exactement le point : une marque est l'**identité** " +
                "de l'app, elle ne se dérive pas du thème. Les cinq teintes sont *mot pour mot* " +
                "celles de l'icône de lanceur (`@color/ic_launcher_background` `#1A202A`, le cercle " +
                "`#36404C`→`#222937`, le contour `#A9B3C2`, la rune `#DADFE8`) et le reflet " +
                "`#26FFFFFF`. Elles ne peuvent pas passer par `ReferencePalette` : les y mettre " +
                "serait les faire **varier avec le thème**, ce que le propriétaire refuse. " +
                "`RuneSourceUniqueTest` les compare aux deux ressources de l'icône, fichier:ligne.",
    )

    @Test
    fun `aucune couleur hors palette n est peinte dans l interface`() {
        val hex = Regex("""Color\(\s*0x([0-9A-Fa-f]{6,8})\s*\)""")
        val floats = Regex("""Color\(\s*[0-9]*\.?[0-9]+f\s*,""")
        val offences = mutableListOf<String>()
        var scanned = 0
        var skipped = 0

        listOf("core/src/main", "app/src/main").forEach { sourceSet ->
            File(root, sourceSet).walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val relative = file.relativeTo(root).invariantSeparatorsPath
                    if (relative in allowList) {
                        skipped++
                        return@forEach
                    }
                    scanned++
                    file.readLines().forEachIndexed { index, line ->
                        val hit = hex.find(line) ?: floats.find(line)
                        if (hit != null) {
                            offences += "$relative:${index + 1}  '${hit.value.trim()}'  $line".trim()
                        }
                    }
                }
        }

        val report = buildString {
            append("sources principales controlees : ").append(scanned)
                .append(" · dispensees par la liste blanche : ").append(skipped)
                .append(" · couleurs hors palette : ").append(offences.size).append('\n')
            append("autorisés par le verrou (").append(allowList.size).append(") :\n")
            allowList.forEach { (path, why) -> append("  · ").append(path.substringAfterLast('/')).append(" — ").append(why).append('\n') }
            offences.forEach { append("  HORS PALETTE  ").append(it).append('\n') }
        }
        println(report)

        assertTrue(
            "une couleur écrite en dur doit passer par `ReferencePalette` (ou être justifiée dans " +
                "l'`allowList` de ce test) :\n" + offences.joinToString("\n"),
            offences.isEmpty(),
        )
    }

    @Test
    fun `la palette elle meme ne contient que des neutres, l accent et les signaux`() {
        val file = File(root, "core/src/main/java/com/onyx/app/ui/theme/ReferencePalette.kt")
        assertTrue("ReferencePalette.kt est introuvable sous $root", file.isFile)

        val literals = Regex("""0x([0-9A-Fa-f]{8})""")
            .findAll(file.readText())
            .map { it.groupValues[1].uppercase(Locale.ROOT) }
            .filter { it.substring(0, 2) == "FF" }
            .toSortedSet()

        // Les cinq rôles saturés admis, et eux seuls. Toute autre couleur doit être un gris.
        val allowedSaturated = setOf(
            "FF2F6BF5", // l'accent, `AccentPalette.Blue`
            "FF1F56D6", // l'accent en texte sur clair
            "FF7AA5FF", // l'accent en texte sur sombre
            "FFCF222E", "FFF85149", // danger
            "FF1A7F37", "FF3FB950", // succès
            "FFB76E00", "FFF5A524", // avertissement
        )

        // Une encre **translucide** ne se juge pas sur son littéral : personne ne la voit telle
        // quelle. `#0A0A1E` est celle des cheveux de la référence, posée à 8 % — composée, elle est
        // neutre à 4 points. On l'admet ici, et on le **vérifie** juste après.
        val translucentInks = setOf("FF0A0A1E")

        val report = StringBuilder("littéral · écart rouge-bleu · verdict\n")
        val wrong = mutableListOf<String>()
        literals.forEach { literal ->
            val rgb = literal.substring(2)
            val r = rgb.substring(0, 2).toInt(16)
            val g = rgb.substring(2, 4).toInt(16)
            val b = rgb.substring(4, 6).toInt(16)
            val spread = maxOf(r, g, b) - minOf(r, g, b)
            val neutral = spread <= 10
            val approved = literal in allowedSaturated
            val hairline = literal in translucentInks
            val ok = neutral || approved || hairline
            if (!ok) wrong += "#$rgb ($spread)"
            report.append("#").append(rgb).append(" · ").append(spread)
                .append(
                    when {
                        neutral -> " neutre"
                        approved -> " signal admis"
                        hairline -> " cheveu translucide (mesuré composé ci-dessous)"
                        else -> " REFUSÉ"
                    },
                )
                .append('\n')
        }
        println(report)

        // La preuve que le cheveu translucide est bien neutre : **composé** sur sa page.
        listOf(
            "cheveu clair sur sa page" to OnyxContrast.over(ReferencePalette.LightLine, ReferencePalette.LightPage),
            "cheveu sombre sur sa page" to OnyxContrast.over(ReferencePalette.DarkLine, ReferencePalette.DarkPage),
        ).forEach { (what, composed) ->
            val spread = listOf(composed.red, composed.green, composed.blue)
                .let { channels -> ((channels.max() - channels.min()) * 255f + 0.5f).toInt() }
            assertTrue("$what n'est pas neutre : $spread points", spread <= 10)
        }

        // La table **exacte** : ajouter une couleur à la palette se voit ici avant de se voir à
        // l'écran, et en retirer une aussi.
        assertEquals(
            "les littéraux de la palette ont changé",
            listOf(
                "FF0A0A0C", "FF0A0A1E", "FF121215", "FF16161A", "FF18181D", "FF1A7F37", "FF1C1C22",
                "FF1F56D6", "FF232329",
                // **Les deux barreaux ajoutés par la maison** — la marche des surfaces qui flottent
                // (`#26262B`, 1,314:1 sur la page sombre) et la pastille de l'onglet actif
                // (`#33333A`, 1,201:1 contre sa barre). Ce sont les **seules** valeurs que la
                // maison ajoute à la référence, elles portent l'échelle des surfaces, et elles
                // sont neutres (écart rouge-bleu de 5 et 7 points).
                "FF26262B", "FF27272A", "FF2C2C33", "FF2F6BF5", "FF33333A", "FF3FB950", "FF6B6B75",
                "FF7AA5FF", "FF8A8A94", "FF8E8E98", "FFB76E00", "FFCF222E", "FFD4D4D8", "FFE9E9EF",
                "FFF1F1F5", "FFF3F3F7", "FFF4F4F6", "FFF5A524", "FFF7F7F9", "FFF85149", "FFFFFFFF",
            ),
            literals.toList(),
        )
        assertTrue(
            "une couleur saturée hors des cinq rôles admis est entrée dans la palette : $wrong",
            wrong.isEmpty(),
        )
    }

    @Test
    fun `le theme par defaut ne porte qu une seule couleur saturee, son accent`() {
        listOf(false, true).forEach { dark ->
            val theme = ResolvedTheme.resolve(OnyxTheme.STANDARD, dark, OnyxFonts.System)
            val appearance = if (dark) "sombre" else "clair"

            assertEquals("accent $appearance", ReferencePalette.Accent, theme.accent)

            // Tous les rôles de surface et d'encre : des gris, mesurés.
            val roles = mapOf(
                "page" to theme.pageColor,
                "surface" to theme.surfaceColor,
                "creux" to theme.tertiarySurfaceColor,
                "encre" to theme.textColor,
                "encre secondaire" to theme.secondaryText,
                "encre tertiaire" to theme.tertiaryText,
                "encre muette" to theme.quaternaryText,
            )
            val report = StringBuilder("rôle ($appearance) · couleur · écart\n")
            roles.forEach { (role, colour) ->
                val spread = spread(colour)
                report.append(role).append(" · ").append(hex(colour)).append(" · ").append(spread).append('\n')
                assertTrue(
                    "$role ($appearance) tire vers une couleur : $spread points d'écart",
                    spread <= 10,
                )
            }
            // Le séparateur est un cheveu **translucide** : il ne compte que par ce qu'il laisse
            // voir, et sa composition sur sa page doit rester un gris.
            val composed = OnyxContrast.over(theme.separator, theme.pageColor)
            assertTrue(
                "le séparateur ($appearance) se lit comme une couleur",
                spread(composed) <= 10,
            )
            assertTrue(
                "le séparateur ($appearance) n'est pas un cheveu",
                OnyxContrast.ratio(composed, theme.pageColor) < 1.5f,
            )
            println(report)
        }
    }

    private fun spread(colour: Color): Int = listOf(colour.red, colour.green, colour.blue)
        .let { channels -> ((channels.max() - channels.min()) * 255f + 0.5f).toInt() }

    private fun hex(colour: Color): String = String.format(
        Locale.ROOT,
        "#%02X%02X%02X",
        (colour.red * 255f + 0.5f).toInt(),
        (colour.green * 255f + 0.5f).toInt(),
        (colour.blue * 255f + 0.5f).toInt(),
    )
}
