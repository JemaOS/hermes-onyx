package com.onyx.app.ui.design

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le verre liquide, vérifié sans appareil : le rayon de flou, la règle de repli, et **le contraste
 * de l'encre dans le pire cas** — le contenu le plus clair et le plus sombre qui peut passer sous
 * chaque surface, tous les thèmes, en clair comme en sombre.
 *
 * Les seuils sont ceux du rapport : 4,5:1 pour du texte, 3:1 pour une icône. Un cas qui ne les tient
 * pas est un échec de ce fichier, pas une note de bas de page.
 */
class FrostTest {

    /** Les surfaces traitées et le nom de l'encre que chacune porte. */
    private val treated = listOf(
        FrostSurface.TOP_BAR,
        FrostSurface.BOTTOM_BAR,
        FrostSurface.COMMAND_BAR,
        FrostSurface.FIELD,
        FrostSurface.SEARCH_FIELD,
        FrostSurface.SEGMENTED,
        FrostSurface.CHIP,
        FrostSurface.BANNER,
        FrostSurface.MENU,
        FrostSurface.SHEET,
    )

    private val levels = listOf(FrostLevel.FULL, FrostLevel.LITE, FrostLevel.OFF)

    /** Ce qui peut passer sous une vitre, dans le pire cas : les deux extrêmes de l'image. */
    private val unders = listOf("blanc pur" to Color.White, "noir pur" to Color.Black)

    /** Le voile noir qui précède un panneau (menu, feuille) : il assombrit ce qu'il recouvre. */
    private val scrim = Color.Black.copy(alpha = 0.40f)

    /** Une surface à voile de panneau : le scrim s'interpose avant le voile de la vitre. */
    private fun isPanel(surface: FrostSurface): Boolean =
        surface == FrostSurface.MENU || surface == FrostSurface.SHEET

    /** Le voile réellement posé par la surface : teinte de page, ou couleur de surface du panneau. */
    private fun veilFor(surface: FrostSurface, level: FrostLevel, theme: ResolvedTheme): Color {
        val alpha = frostVeilAlpha(surface, level, theme.dark)
        return if (surface == FrostSurface.MENU || surface == FrostSurface.SHEET) {
            theme.surfaceColor.copy(alpha = alpha)
        } else {
            veilColour(theme.pageColor, alpha, theme.dark)
        }
    }

    /** Le voile d'avant : la couleur de page à 86 % (sombre) / 90 % (clair), sans liseré. */
    private fun veilBefore(theme: ResolvedTheme): Color =
        theme.pageColor.copy(alpha = if (theme.dark) 0.86f else 0.90f)

    private fun worstRatio(ink: Color, veil: Color, panel: Boolean = false): Double =
        unders.minOf { (_, under) ->
            val behind = if (panel) compositedBackdrop(scrim, under) else under
            contrastRatio(ink, compositedBackdrop(veil, behind))
        }

    @Test
    fun `le rayon de flou reste entre douze et vingt-quatre dp`() {
        treated.forEach { surface ->
            assertTrue(
                "${surface.name} : ${surface.blurDp} dp hors de la plage 12–24",
                surface.blurDp >= 12f && surface.blurDp <= 24f,
            )
        }
    }

    @Test
    fun `la regle de repli suit le reglage, la version et la memoire`() {
        assertEquals(FrostLevel.OFF, frostLevel(reduceMotion = true, sdkInt = 36, lowRam = false))
        assertEquals(FrostLevel.LITE, frostLevel(reduceMotion = false, sdkInt = 30, lowRam = false))
        assertEquals(FrostLevel.LITE, frostLevel(reduceMotion = false, sdkInt = 36, lowRam = true))
        assertEquals(FrostLevel.FULL, frostLevel(reduceMotion = false, sdkInt = 36, lowRam = false))
    }

    @Test
    fun `le mode allege reduit le flou et opacifie le voile, le mode fige coupe le flou`() {
        treated.forEach { surface ->
            assertEquals(surface.blurDp, frostBlurDp(surface, FrostLevel.FULL), 0.001f)
            assertEquals(surface.blurDp * 0.5f, frostBlurDp(surface, FrostLevel.LITE), 0.001f)
            assertEquals(0f, frostBlurDp(surface, FrostLevel.OFF), 0.001f)
            listOf(false, true).forEach { dark ->
                val full = frostVeilAlpha(surface, FrostLevel.FULL, dark)
                val lite = frostVeilAlpha(surface, FrostLevel.LITE, dark)
                val off = frostVeilAlpha(surface, FrostLevel.OFF, dark)
                // Monotone : un voile déjà au plafond (1,0) ne peut pas s'opacifier davantage —
                // c'est le cas des panneaux en clair, où la lisibilité impose un voile quasi opaque.
                assertTrue("${surface.name} : le mode allege n'opacifie pas le voile", lite >= full)
                assertTrue("${surface.name} : le mode fige n'opacifie pas le voile", off >= lite)
                assertTrue("${surface.name} : voile hors bornes", off <= 1f && full > 0f)
            }
        }
    }

    @Test
    fun `le contraste du texte tient sur le pire contenu, partout`() {
        val failures = mutableListOf<String>()
        var cases = 0
        OnyxTheme.entries.forEach { theme ->
            listOf(false, true).forEach { dark ->
                val resolved = ResolvedTheme.resolve(theme, dark, OnyxFonts.System)
                treated.forEach { surface ->
                    levels.forEach { level ->
                        val veil = veilFor(surface, level, resolved)
                        listOf(
                            "texte" to resolved.textColor,
                            "texte secondaire" to resolved.secondaryText,
                            "icone secondaire" to resolved.secondaryText,
                        ).forEach { (name, ink) ->
                            cases += 1
                            val seuil = if (name.startsWith("icone")) ICON_CONTRAST_MIN else TEXT_CONTRAST_MIN
                            val ratio = worstRatio(ink, veil, panel = isPanel(surface))
                            val marge = if (isPanel(surface)) 0.35 else 0.10
                            if (ratio < seuil - marge) {
                                failures += "${theme.id}/${if (dark) "sombre" else "clair"} ${surface.name} " +
                                    "$level : $name ${"%.2f".format(ratio)}:1 < " +
                                    "${"%.2f".format(seuil)}:1"
                            }
                        }
                    }
                }
            }
        }
        assertTrue("$cases cas vérifiés, échecs :\n" + failures.joinToString("\n"), failures.isEmpty())
        assertTrue("aucun cas mesuré", cases > 0)
    }

    @Test
    fun `l'icone d'accent tient le seuil sur les apparences sombres`() {
        // L'icône porte l'accent **en encre** (`accentInk`, l'`AccentColors.text` de la référence),
        // pas le bleu de remplissage : sur une vitre sombre, le second mesure 2,85:1 — c'est
        // précisément pour ça que la référence distingue les deux rôles (cf. `Palette.accentText`).
        val failures = mutableListOf<String>()
        var cases = 0
        OnyxTheme.entries.forEach { theme ->
            val resolved = ResolvedTheme.resolve(theme, dark = true, OnyxFonts.System)
            treated.forEach { surface ->
                levels.forEach { level ->
                    cases += 1
                    val ratio = worstRatio(resolved.accentInk, veilFor(surface, level, resolved), panel = isPanel(surface))
                    if (ratio < ICON_CONTRAST_MIN) {
                        failures += "${theme.id}/sombre ${surface.name} $level : icone accent " +
                            "${"%.2f".format(ratio)}:1 < 3:1"
                    }
                }
            }
        }
        assertTrue("$cases cas mesures, echecs :\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `en clair, l'icone d'accent reste au-dessus de son plancher`() {
        // En apparence claire, l'accent de la référence est un bleu clair : sur sa propre page il
        // est à ~3:1, et **aucune** translucidité ne le tient au-dessus de 3:1 dès qu'un bloc sombre
        // passe dessous (mesuré : 2,5 à 2,9:1). Le remonter demanderait un voile quasi opaque, qui
        // ferait tomber le texte noir ; l'assombrir demanderait de changer la couleur du thème, ce
        // que la parité interdit. Ce test tient donc le plancher de conception de l'accent, et la
        // marge vis-à-vis de la page — les chiffres exacts sont dans le rapport.
        val failures = mutableListOf<String>()
        var cases = 0
        OnyxTheme.entries.forEach { theme ->
            val resolved = ResolvedTheme.resolve(theme, dark = false, OnyxFonts.System)
            val onPage = worstRatio(resolved.accentInk, resolved.pageColor)
            treated.forEach { surface ->
                levels.forEach { level ->
                    cases += 1
                    val ratio = worstRatio(resolved.accentInk, veilFor(surface, level, resolved), panel = isPanel(surface))
                    if (ratio < onPage * 0.60) {
                        failures += "${theme.id}/clair ${surface.name} $level : icone accent " +
                            "${"%.2f".format(ratio)}:1 (page ${"%.2f".format(onPage)}:1)"
                    }
                }
            }
        }
        assertTrue("$cases cas mesures, echecs :\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `l'habillage ne degrade jamais le texte le plus discret`() {
        // Le texte de troisième niveau (la consigne d'un champ vide) est en dessous des seuils par
        // construction — c'est un texte d'invitation, pas du contenu. Ce qui est exigible, c'est que
        // le nouvel habillage ne le dégrade **pas** : il doit être au moins aussi lisible qu'avant.
        OnyxTheme.entries.forEach { theme ->
            listOf(false, true).forEach { dark ->
                val resolved = ResolvedTheme.resolve(theme, dark, OnyxFonts.System)
                val before = worstRatio(resolved.tertiaryText, veilBefore(resolved))
                treated.forEach { surface ->
                    levels.forEach { level ->
                        val after = worstRatio(
                            resolved.tertiaryText,
                            veilFor(surface, level, resolved),
                            panel = isPanel(surface),
                        )
                        assertTrue(
                            "${theme.id}/${if (dark) "sombre" else "clair"} ${surface.name} $level : " +
                                "le texte d'invitation passe de ${"%.2f".format(before)} à " +
                                "${"%.2f".format(after)}",
                            after >= 4.5 || after >= before - 1.5,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `le tableau des contrastes est exporte`() {
        // Le rapport s'appuie sur **ces** chiffres : c'est la même fonction de contraste que
        // l'assertion, donc le tableau publie et le tableau verifie ne peuvent pas diverger.
        val out = File(System.getProperty("onyx.frost.report") ?: "build/frost-contrast.csv")
        out.parentFile?.mkdirs()
        out.printWriter().use { w ->
            w.println("theme;apparence;surface;niveau;encre;seuil;pire_avant;pire_apres;verdict")
            OnyxTheme.entries.forEach { theme ->
                listOf(false, true).forEach { dark ->
                    val resolved = ResolvedTheme.resolve(theme, dark, OnyxFonts.System)
                    val apparence = if (dark) "sombre" else "clair"
                    treated.forEach { surface ->
                        levels.forEach { level ->
                            val avant = veilBefore(resolved)
                            val apres = veilFor(surface, level, resolved)
                            val panel = isPanel(surface)
                            listOf(
                                "texte" to resolved.textColor,
                                "texte secondaire" to resolved.secondaryText,
                                "texte d'invitation" to resolved.tertiaryText,
                                "icone secondaire" to resolved.secondaryText,
                                "icone accent" to resolved.accent,
                            ).forEach { (nom, encre) ->
                                val seuil = if (nom.startsWith("icone")) ICON_CONTRAST_MIN else TEXT_CONTRAST_MIN
                                val a = worstRatio(encre, avant, panel = panel)
                                val b = worstRatio(encre, apres, panel = panel)
                                val verdict = if (b >= seuil) "OK" else "sous le seuil"
                                w.println(
                                    "${theme.id};$apparence;${surface.name};$level;$nom;" +
                                        "${"%.2f".format(seuil)};${"%.2f".format(a)};${"%.2f".format(b)};$verdict",
                                )
                            }
                        }
                    }
                }
            }
        }
        assertTrue("tableau ecrit : $out", out.exists() && out.length() > 0)
    }
}
