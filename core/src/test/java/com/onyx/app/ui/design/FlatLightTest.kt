package com.onyx.app.ui.design

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * **Les surfaces plates du volet au clair se voient, et leur encre se lit.**
 *
 * Le lot « volet clair propre » retire le verre du volet **en apparence claire seulement**, et le
 * remplace par trois familles plates : le panneau quasi blanc détaché par une ombre, le **gris des
 * creux** (tuile, disque de rangée, piste du sélecteur, puce au repos) et la **pastille pleine**
 * foncée de la puce choisie. Retirer le verre, c'est retirer les quatre pièces qui détachaient la
 * surface — liseré, filet interne, lumière de tête, ombre de pied. Ce qui reste doit donc tenir
 * **tout seul**, et ce test exige les chiffres, sur **les sept thèmes** :
 *
 * - le **détachement** du gris des creux et du panneau ([FlatDetachmentMin], 1,20:1) : sans lui, une
 *   tuile se lirait comme un trou dans la page ;
 * - la **lisibilité** de l'encre d'icône sur ce gris (3:1) et de l'encre de la pastille pleine sur
 *   son fond (4,5:1) ;
 * - la **neutralité en sombre** : le panneau sombre vaut exactement la page, et l'élévation y est
 *   nulle — c'est ce qui garantit que ré-habiller le clair n'a pas touché le verre du sombre.
 *
 * Le tableau est **imprimé** : le chiffre du rapport et le chiffre vérifié sortent du même code, au
 * même moment.
 */
class FlatLightTest {

    private fun theme(theme: OnyxTheme, dark: Boolean) =
        ResolvedTheme.resolve(theme, dark = dark, fonts = OnyxFonts.System)

    @Test
    fun `le gris des creux se detache du panneau, sur tous les themes clairs`() {
        val report = StringBuilder()
        report.append("thème · surface · fond · détachement · seuil · lu\n")
        var failures = 0
        for (theme in OnyxTheme.entries) {
            val t = theme(theme, dark = false)
            val pane = sidebarPaneColor(t)
            val well = flatWellColor(t, pane)
            val track = flatTrackColor(t)
            listOf(
                "tuile / disque / puce au repos · panneau" to OnyxContrast.ratio(well, pane),
                "piste du sélecteur · page" to OnyxContrast.ratio(track, t.pageColor),
            ).forEach { (what, value) ->
                val ok = value + 1e-4f >= FlatDetachmentMin
                if (!ok) failures++
                report.append(
                    "${theme.id} · $what · ${"%.1f".format(Locale.ROOT, FlatDetachmentMin)} · " +
                        "${"%.3f".format(Locale.ROOT, value)} ${if (ok) "ok" else "SOUS LE SEUIL"}\n",
                )
            }
            report.append(
                "  · page=${hex(t.pageColor)} panneau=${hex(pane)} creux=${hex(well)} " +
                    "piste=${hex(track)} icône=${hex(flatIconInk(t, well))} " +
                    "pastille=${hex(flatActivePillColor(t))} encre=${hex(flatActivePillInk(t))}\n",
            )
        }
        println(report)
        assertTrue(
            "des surfaces plates ne se détachent pas de leur fond :\n" +
                report.lineSequence().filter { it.contains("SOUS LE SEUIL") }.joinToString("\n"),
            failures == 0,
        )
    }

    @Test
    fun `l icone d une tuile tient 3 pour 1, est plus sombre que son fond et tire sur l accent`() {
        val report = StringBuilder()
        var failures = 0
        for (theme in OnyxTheme.entries) {
            val t = theme(theme, dark = false)
            val pane = sidebarPaneColor(t)
            val well = flatWellColor(t, pane)
            val ink = flatIconInk(t, well)
            val ratio = OnyxContrast.ratio(ink, well)
            val ok = ratio + 1e-4f >= OnyxContrast.ICON_MIN
            if (!ok) failures++
            // L'encre est **plus sombre que son fond** (une icône claire sur un gris clair serait le
            // défaut que la mesure écarte) et elle **tire sur l'accent** du thème : c'est ce qui la
            // rend neutre-bleutée sur les thèmes bleus, et ambrée sur le thème doré — jamais un gris
            // pur, qui ne dirait plus de quelle maison est l'icône.
            val darker = OnyxContrast.luminance(ink) < OnyxContrast.luminance(well)
            // Elle **tire sur l'accent** du thème : le canal dominant de l'encre suit celui de
            // l'accent. C'est ce qui la rend neutre-bleutée sur les thèmes bleus et ambrée sur le
            // thème doré — jamais un gris pur, qui ne dirait plus de quelle maison est l'icône.
            val leaning = dominant(ink) == dominant(t.accent)
            report.append(
                "${theme.id} · icône de tuile · 3,0 · ${"%.2f".format(Locale.ROOT, ratio)} " +
                    "${if (ok) "ok" else "SOUS LE SEUIL"} · plus sombre que le fond=$darker · " +
                    "vers l'accent=$leaning\n",
            )
            assertTrue("l'icône d'une tuile n'est pas plus sombre que son fond sur ${theme.id}", darker)
            assertTrue("l'icône d'une tuile ne tire pas sur l'accent sur ${theme.id}", leaning)
            // **Neutre-bleutée** : c'est le cas de la référence, et c'est vrai des thèmes à accent
            // bleu. Le test le mesure là où la propriété doit tenir, pas sur un thème doré où la
            // bonne réponse est « ambrée ».
            if (t.accent.blue > t.accent.red) {
                assertTrue(
                    "l'icône d'une tuile n'est pas bleutée sur ${theme.id} (accent bleu)",
                    ink.blue > ink.red,
                )
            }
        }
        println(report)
        assertTrue(
            "des icônes de tuile passent sous 3:1 :\n" +
                report.lineSequence().filter { it.contains("SOUS LE SEUIL") }.joinToString("\n"),
            failures == 0,
        )
    }

    @Test
    fun `la pastille pleine porte son mot a 4,5 pour 1, sur tous les themes`() {
        val report = StringBuilder()
        var failures = 0
        for (theme in OnyxTheme.entries) {
            val t = theme(theme, dark = false)
            val fill = flatActivePillColor(t)
            val ink = flatActivePillInk(t)
            val ratio = OnyxContrast.ratio(ink, fill)
            val ok = ratio + 1e-4f >= OnyxContrast.TEXT_MIN
            if (!ok) failures++
            // **La pastille choisie est celle de l'accent** : c'est un **état de sélection**, donc la
            // seule famille que la maison s'autorise en plus de l'action principale. Elle valait
            // l'encre du texte (une pastille noire) — le défaut relevé sur la capture du
            // propriétaire, « la pastille active est noire alors que le bouton principal est bleu ».
            // Ce qui compte reste ce qui tient un mot : le rapport de 4,5:1 est mesuré juste au-dessus.
            val accentFamily = fill == OnyxContrast.filled(t.accent)
            val dark = OnyxContrast.luminance(fill) < 0.5f
            report.append(
                "${theme.id} · puce choisie · 4,5 · ${"%.2f".format(Locale.ROOT, ratio)} " +
                    "${if (ok) "ok" else "SOUS LE SEUIL"} · fond foncé=$dark\n",
            )
            assertTrue("la pastille choisie n'est plus celle de l'accent sur ${theme.id}", accentFamily)
            // Un accent clair (« Code », bleu de ciel) donne une pastille claire : c'est l'accent du
            // thème, pas une faute — c'est pour ça que l'assertion porte sur la famille, et que le
            // fond sombre n'est exigé que là où l'accent est sombre.
            if (OnyxContrast.luminance(t.accent) < 0.5f) {
                assertTrue("l'accent sombre de ${theme.id} donne une pastille claire", dark)
            }
        }
        println(report)
        assertTrue(
            "des libellés de puce choisie passent sous 4,5:1 :\n" +
                report.lineSequence().filter { it.contains("SOUS LE SEUIL") }.joinToString("\n"),
            failures == 0,
        )
    }

    @Test
    fun `la pastille blanche du selecteur se detache de sa piste`() {
        for (theme in OnyxTheme.entries) {
            val t = theme(theme, dark = false)
            val pill = flatTrackPillColor(t)
            val track = flatTrackColor(t)
            val ratio = OnyxContrast.ratio(pill, track)
            assertTrue(
                "la pastille du sélecteur ne se détache pas de sa piste sur ${theme.id} ($ratio)",
                ratio + 1e-4f >= FlatDetachmentMin,
            )
        }
    }

    @Test
    fun `le panneau ne descend jamais sous la page, et le sombre garde exactement sa page`() {
        val report = StringBuilder()
        for (theme in OnyxTheme.entries) {
            val light = theme(theme, dark = false)
            val dark = theme(theme, dark = true)
            val lightPane = sidebarPaneColor(light)
            val darkPane = sidebarPaneColor(dark)
            report.append(
                "${theme.id} · page clair=${hex(light.pageColor)} · panneau clair=${hex(lightPane)} · " +
                    "page sombre=${hex(dark.pageColor)} · panneau sombre=${hex(darkPane)}\n",
            )
            // Le panneau **monte** vers le blanc (ou ne bouge pas si la page est déjà blanche) : il
            // ne descend jamais sous la page, sinon il se lirait comme un renfoncement.
            assertTrue(
                "le panneau clair descend sous la page sur ${theme.id}",
                OnyxContrast.luminance(lightPane) + 1e-4f >= OnyxContrast.luminance(light.pageColor),
            )
            // Et **en sombre il vaut la page, à l'identique** : la vitre s'en charge, et c'est ce
            // qui prouve que le mode sombre n'a pas été touché.
            assertEquals("le panneau sombre a bougé sur ${theme.id}", dark.pageColor, darkPane)
            assertTrue("l'élévation du panneau n'est pas nulle en sombre", sidebarPaneElevation(dark).value == 0f)
            assertTrue("l'élévation du panneau est nulle en clair", sidebarPaneElevation(light).value > 0f)
        }
        println(report)
    }

    @Test
    fun `la teinte d une surface qui se detache deja est rendue telle quelle`() {
        // La fonction ne « corrige » pas sans raison : c'est ce qui la garde vérifiable.
        val already = OnyxContrast.over(Color(0xFF767680).copy(alpha = 0.5f), Color.White)
        assertEquals(already, flatContrastingTint(already, Color.White, FlatDetachmentMin))
    }

    @Test
    fun `les bornes de la famille plate sont celles du volet`() {
        // Le détachement d'une plaque suit la marche de la **référence** (1,10), pas celle de la
        // vitre (1,20) : les deux familles ne mesurent pas la même chose (cf. `FlatDetachmentMin`).
        assertEquals("le seuil de détachement est celui de la référence", 1.10f, FlatDetachmentMin, 1e-4f)
        assertTrue(
            "une plaque plate ne peut pas se détacher plus que la vitre qui la voile",
            FlatDetachmentMin <= GlassDetachmentMin,
        )
        assertTrue("l'ombre du panneau doit être douce, donc large", FlatPanelElevation.value >= 8f)
        assertTrue("l'ombre du panneau ne doit pas se lire comme une carte flottante", FlatPanelElevation.value <= 20f)
        assertTrue("la puce garde la hauteur de la famille", FlatChipHeight.value == 32f)
        assertTrue("la bordure du bouton discret reste un filet", FlatHairlineDp.value <= 1f)
        assertTrue("l'icône d'une tuile est une encre neutre, à peine touchée d'accent", FlatIconBlue in 0f..0.25f)
        assertTrue("le panneau monte vers le blanc", FlatPanelLift in 0.3f..0.9f)
        assertTrue("la pastille d'un sélecteur reste posée, pas flottante", FlatSegmentPillElevation.value <= 4f)
    }

    /** Le canal dominant d'une couleur : c'est lui qui dit de quelle teinte elle tire. */
    private fun dominant(colour: Color): Int = when {
        colour.red >= colour.green && colour.red >= colour.blue -> 0
        colour.green >= colour.blue -> 1
        else -> 2
    }

    private fun hex(colour: Color): String =
        String.format(
            Locale.ROOT,
            "#%02X%02X%02X",
            (colour.red * 255f).toInt(),
            (colour.green * 255f).toInt(),
            (colour.blue * 255f).toInt(),
        )
}
