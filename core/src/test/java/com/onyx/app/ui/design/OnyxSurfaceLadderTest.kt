package com.onyx.app.ui.design

import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ReferencePalette
import com.onyx.app.ui.theme.ResolvedTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **L'échelle des surfaces, mesurée — et la table du rapport, calculée ici.**
 *
 * Le reproche principal du propriétaire sur `1.0.0+580` : « le compositeur est un bloc quasi noir
 * sur une page quasi noire, aucune séparation perceptible », « la barre d'onglets est une capsule
 * encore plus sombre que la page », « la carte Continuer est noire sur noire ». Ce test **chiffre**
 * ces trois phrases et refuse qu'elles redeviennent vraies.
 *
 * Il imprime la table complète (rôle, couleur claire, couleur sombre, luminance, marche sur la page
 * dans les deux apparences) : les chiffres du rapport et ceux du verrou sont donc **le même calcul**.
 *
 * Les seuils sont nommés dans [OnyxSurfaces], avec leur source :
 *  - [OnyxSurfaces.StepMin] (1,15:1) vient du premier barreau d'un sombre natif de référence
 *    (Linear : `#08090a` → `#0f1011`) ;
 *  - [OnyxSurfaces.FloatStepMin] (1,25:1) est la marche d'une surface qui **flotte** ;
 *  - [OnyxSurfaces.CapsuleStepMin] (1,15:1) est la marche de la pastille **contre sa barre** ;
 *  - [OnyxSurfaces.ActiveInkMin] (3:1) est l'exigence du brief pour l'onglet actif.
 */
class OnyxSurfaceLadderTest {

    private val dark = ResolvedTheme.resolve(OnyxTheme.STANDARD, dark = true, fonts = OnyxFonts.System)

    private fun hex(color: androidx.compose.ui.graphics.Color): String = String.format(
        "#%02X%02X%02X",
        (color.red * 255f).toInt(),
        (color.green * 255f).toInt(),
        (color.blue * 255f).toInt(),
    )

    @Test
    fun `la table de l echelle des surfaces est imprimee`() {
        println("=== ÉCHELLE DES SURFACES — clair / sombre, luminance et marche sur la page ===")
        println(
            String.format(
                "%-46s %-9s %-9s %-10s %-9s %-9s",
                "rôle", "clair", "sombre", "L(sombre)", "sombre", "clair",
            ),
        )
        OnyxSurfaces.steps.forEach { step ->
            println(
                String.format(
                    "%-46s %-9s %-9s %-10.6f %-9.3f %-9.3f",
                    step.role, step.light, step.dark, step.luminanceDark, step.overPageDark, step.overPageLight,
                ),
            )
        }
        println(
            "seuils : marche ${OnyxSurfaces.StepMin} · creux ${OnyxSurfaces.WellStepMin} · " +
                "flottant ${OnyxSurfaces.FloatStepMin} · clair ${OnyxSurfaces.LightStepMin} · " +
                "capsule ${OnyxSurfaces.CapsuleStepMin} · encre active ${OnyxSurfaces.ActiveInkMin}",
        )
        println(
            "filet clair ${OnyxSurfaces.hairline(ResolvedTheme.fallback())} · " +
                "filet sombre ${hex(OnyxSurfaces.hairline(dark))} · ombre unique ${OnyxShadow.Float}",
        )
        assertEquals("l'échelle porte cinq barreaux", 5, OnyxSurfaces.steps.size)
    }

    @Test
    fun `le compositeur se detache de la page d une marche franche`() {
        val page = OnyxSurfaces.page(dark)
        val composer = OnyxSurfaces.surfaceHigh(dark)
        val step = OnyxContrast.ratio(composer, page)
        assertTrue(
            "le compositeur ne mesure que $step:1 sur la page sombre — c'était 1,058:1 sur la capture " +
                "(#121215 sur #0A0A0C), le défaut à ne pas reproduire",
            step >= OnyxSurfaces.FloatStepMin,
        )
        // La valeur annoncée dans le KDoc d'OnyxSurfaces.
        assertEquals(1.314f, step, 0.005f)
    }

    @Test
    fun `chaque barreau ordinaire se detache de la page`() {
        val page = OnyxSurfaces.page(dark)
        // Le **creux** a son propre seuil : il ne porte pas d'encre et se **sent** plutôt qu'il ne
        // se détache (la valeur de la référence mesure 1,058:1 — c'est un fait de la référence).
        val well = OnyxContrast.ratio(OnyxSurfaces.well(dark), page)
        assertTrue(
            "le creux mesure $well:1 sur la page — sous ${OnyxSurfaces.WellStepMin}, il n'y a plus de creux",
            well >= OnyxSurfaces.WellStepMin,
        )
        assertTrue(
            "le creux doit rester **sous** la carte : un creux plus clair que la surface qui l'entoure se lit comme un relief",
            OnyxContrast.luminance(OnyxSurfaces.well(dark)) < OnyxContrast.luminance(OnyxSurfaces.surface(dark)),
        )
        listOf(
            "carte" to OnyxSurfaces.surface(dark),
            "barreau haut" to OnyxSurfaces.surfaceHigh(dark),
            "capsule" to OnyxSurfaces.capsule(dark),
            "tuile" to OnyxSurfaces.tile(dark),
        ).forEach { (name, colour) ->
            val step = OnyxContrast.ratio(colour, page)
            assertTrue(
                "$name : $step:1 sur la page — sous la marche minimale ${OnyxSurfaces.StepMin}, " +
                    "deux surfaces de même teinte se lisent comme une seule",
                step >= OnyxSurfaces.StepMin,
            )
        }
    }

    @Test
    fun `la capsule active se voit dans sa barre et porte une encre lisible`() {
        val bar = OnyxSurfaces.surfaceHigh(dark)
        val capsule = OnyxSurfaces.capsule(dark)
        val step = OnyxContrast.ratio(capsule, bar)
        assertTrue(
            "la pastille de l'onglet actif ne mesure que $step:1 contre la barre — la « tache bleu " +
                "sombre illisible » de la capture",
            step >= OnyxSurfaces.CapsuleStepMin,
        )
        // **L'encre de l'onglet actif.** L'accent en encre de la référence sur la pastille.
        val icon = OnyxContrast.ratio(dark.accentInk, capsule)
        assertTrue("l'icône de l'onglet actif mesure $icon:1 — le brief exige 3:1", icon >= OnyxSurfaces.ActiveInkMin)
        val label = OnyxContrast.ratio(dark.textColor, capsule)
        assertTrue("le libellé d'un onglet est du texte : il exige 4,5:1 (mesuré $label)", label >= OnyxContrast.TEXT_MIN)
    }

    @Test
    fun `la capsule active se voit aussi en apparence claire`() {
        val light = ResolvedTheme.resolve(OnyxTheme.STANDARD, dark = false, fonts = OnyxFonts.System)
        val page = OnyxSurfaces.page(light)
        val capsule = OnyxSurfaces.capsule(light)
        val step = OnyxContrast.ratio(capsule, page)
        assertTrue(
            "en clair, la pastille de l'onglet actif mesure $step:1 sur la page — sous " +
                "${OnyxSurfaces.LightStepMin}, elle ne se distingue plus (les états ne se définissent " +
                "jamais pour une seule apparence)",
            step >= OnyxSurfaces.LightStepMin,
        )
        assertTrue(
            "l'accent en encre sur la pastille claire mesure " +
                "${OnyxContrast.ratio(light.accentInk, capsule)}:1",
            OnyxContrast.ratio(light.accentInk, capsule) >= OnyxSurfaces.ActiveInkMin,
        )
    }

    @Test
    fun `le filet se distingue de la page dans les deux apparences`() {
        val light = ResolvedTheme.resolve(OnyxTheme.STANDARD, dark = false, fonts = OnyxFonts.System)
        val darkStep = OnyxContrast.ratio(
            OnyxContrast.over(OnyxSurfaces.hairline(dark), ReferencePalette.DarkPage),
            ReferencePalette.DarkPage,
        )
        val lightStep = OnyxContrast.ratio(
            OnyxContrast.over(OnyxSurfaces.hairline(light), ReferencePalette.LightPage),
            ReferencePalette.LightPage,
        )
        assertTrue("le filet sombre ne mesure que $darkStep:1 sur la page", darkStep >= OnyxSurfaces.HairlineMin)
        assertTrue("le filet clair ne mesure que $lightStep:1 sur la page", lightStep >= OnyxSurfaces.HairlineMin)
    }

    @Test
    fun `chaque role de surface prend son rayon dans la famille des formes`() {
        // Les marches de la famille, **plus** les rayons emboîtés : un segment qui vit dans une
        // capsule prend `interior(outer, inset)` — deux courbes de même centre, ce qui est la règle
        // de l'emboîtement et non un rayon de plus.
        val family = OnyxSpace.radii.map { it.second }.toSet()
        val nested = buildSet {
            addAll(family)
            listOf(OnyxSpace.PanelCorner, OnyxSpace.CardCorner, OnyxSpace.TileCorner).forEach { outer ->
                OnyxSpace.Scale.forEach { inset -> add(OnyxSpace.interior(outer, inset)) }
            }
        }
        SurfaceRole.entries.forEach { role ->
            assertTrue(
                "$role : son rayon (${role.radius}) n'est ni une marche de la famille des formes ni " +
                    "un emboîtement de deux marches — un rayon écrit sur place est le défaut d'origine",
                role.radius in nested,
            )
        }
        // Les deux capsules et la barre sont les seules formes **entièrement** arrondies.
        assertEquals(
            setOf(SurfaceRole.Capsule, SurfaceRole.ActiveCapsule, SurfaceRole.Bar),
            SurfaceRole.entries.filter { it.shape == androidx.compose.foundation.shape.RoundedCornerShape(50) }.toSet(),
        )
    }

    @Test
    fun `il n y a qu une ombre et un filet pour toute l app`() {
        // Une seule ombre : les seuls rôles qui se détachent du contenu la partagent.
        assertEquals(
            "seuls la surface flottante, la barre et la tuile portent l'ombre",
            setOf(SurfaceRole.Float, SurfaceRole.Bar, SurfaceRole.Tile),
            SurfaceRole.entries.filter { it.hasShadow }.toSet(),
        )
        assertEquals("l'ombre unique fait bien 6 dp", 6f, OnyxShadow.Float.value, 0f)
        assertEquals(
            "la vignette de rangée porte l'ombre unique",
            OnyxShadow.Float,
            ThumbnailShadow,
        )
        // La pastille d'un sélecteur plat n'a **aucune** ombre : elle vit dans une piste, elle est
        // posée. C'est ce qui garde à l'app **une** portée d'ombre au lieu de deux.
        assertEquals(
            "la pastille du sélecteur plat ne porte aucune ombre",
            0f,
            FlatSegmentPillElevation.value,
            0f,
        )
        // Un seul filet : le champ, la surface flottante, la carte et la barre.
        assertEquals(
            setOf(SurfaceRole.Field, SurfaceRole.Float, SurfaceRole.Card, SurfaceRole.Bar),
            SurfaceRole.entries.filter { it.hasHairline }.toSet(),
        )
        assertEquals("le filet fait 1 dp", 1f, OnyxSpace.Stroke.value, 0f)
        assertEquals("le filet du bouton discret fait 1 dp aussi", OnyxSpace.Stroke, FlatHairlineDp)
    }

    @Test
    fun `les deux barreaux ajoutes restent dans la fourchette d un sombre natif`() {
        // Linear (une référence de sombre natif) : #08090a → #0f1011 (1,154:1) → #191a1b (1,197:1)
        // → #28282c (1,331:1). Nos deux valeurs ajoutées doivent y rester : au-dessus du premier
        // barreau, sous le plus clair — sans quoi on quitte le sombre pour du gris moyen.
        val page = ReferencePalette.DarkPage
        val high = OnyxContrast.ratio(OnyxSurfaces.DarkSurfaceHigh, page)
        val capsule = OnyxContrast.ratio(OnyxSurfaces.DarkCapsule, page)
        assertTrue("le barreau haut ($high:1) doit dépasser 1,15:1", high > 1.15f)
        assertTrue("le barreau haut ($high:1) doit rester sous 1,45:1", high < 1.45f)
        assertTrue("la capsule ($capsule:1) doit dépasser le barreau haut", capsule > high)
        assertTrue("la capsule ($capsule:1) doit rester sous 1,95:1", capsule < 1.95f)
    }
}
