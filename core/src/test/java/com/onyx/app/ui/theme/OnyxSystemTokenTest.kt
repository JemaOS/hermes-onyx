package com.onyx.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou de la table des rôles neutres.**
 *
 * Ce test verrouillait les valeurs de la référence (`#F2F2F7`, `#3C3C43` à 60 %, `#545458` à 60 %…). Il
 * verrouille maintenant, rôle par rôle, **celles de la référence** — celles qui sont écrites dans
 * `ReferencePalette`. C'est le seul endroit où une valeur de la table de palette peut se casser sans
 * qu'une capture le montre : le changement serait alors « invisible par accident », exactement le
 * défaut du lot précédent.
 *
 * La raison de chaque rôle est dans le KDoc de [ReferencePalette], avec son emplacement dans la
 * source de la référence.
 */
class OnyxSystemTokenTest {
    @Test
    fun `les roles neutres portent exactement les valeurs de la reference`() {
        val expected = mapOf(
            "page" to DynColor(ReferencePalette.LightPage, ReferencePalette.DarkPage),
            "surface" to DynColor(ReferencePalette.LightSurface, ReferencePalette.DarkSurface),
            "creux" to DynColor(ReferencePalette.LightSurface2, ReferencePalette.DarkSurface2),
            "encre" to DynColor(ReferencePalette.LightText, ReferencePalette.DarkText),
            "encre secondaire" to DynColor(ReferencePalette.LightText2, ReferencePalette.DarkText2),
            "encre tertiaire" to DynColor(ReferencePalette.LightText4, ReferencePalette.DarkText4),
            "encre muette" to DynColor(ReferencePalette.LightText5, ReferencePalette.DarkText5),
            "separateur" to DynColor(ReferencePalette.LightLine, ReferencePalette.DarkLine),
            "danger" to ReferencePalette.Danger,
            "avertissement" to ReferencePalette.Warning,
            "succes" to ReferencePalette.Success,
        )
        val actual = mapOf(
            "page" to OnyxSystem.background,
            "surface" to OnyxSystem.secondaryBackground,
            "creux" to OnyxSystem.tertiaryBackground,
            "encre" to OnyxSystem.label,
            "encre secondaire" to OnyxSystem.secondaryLabel,
            "encre tertiaire" to OnyxSystem.tertiaryLabel,
            "encre muette" to OnyxSystem.quaternaryLabel,
            "separateur" to OnyxSystem.separator,
            "danger" to OnyxSystem.red,
            "avertissement" to OnyxSystem.orange,
            "succes" to OnyxSystem.green,
        )
        assertEquals(expected.keys, actual.keys)
        actual.forEach { (role, value) ->
            assertEquals(role, expected.getValue(role), value)
        }
    }

    @Test
    fun `les valeurs de la table sont bien celles lues dans la source de la reference`() {
        // Les hexadécimaux, écrits à la main : une faute de frappe dans `ReferencePalette` doit
        // casser ici, pas à la capture.
        assertEquals("#F7F7F9", hex(ReferencePalette.LightPage))
        assertEquals("#FFFFFF", hex(ReferencePalette.LightSurface))
        assertEquals("#F1F1F5", hex(ReferencePalette.LightSurface2))
        assertEquals("#E9E9EF", hex(ReferencePalette.LightSurface3))
        assertEquals("#F3F3F7", hex(ReferencePalette.LightWell))
        assertEquals("#0A0A0C", hex(ReferencePalette.DarkPage))
        assertEquals("#16161A", hex(ReferencePalette.DarkSurface))
        assertEquals("#1C1C22", hex(ReferencePalette.DarkSurface2))
        assertEquals("#232329", hex(ReferencePalette.DarkSurface3))
        assertEquals("#121215", hex(ReferencePalette.DarkWell))
        assertEquals("#18181D", hex(ReferencePalette.DarkSheet))
        assertEquals("#2C2C33", hex(ReferencePalette.DarkThumb))
        assertEquals("#0A0A0C", hex(ReferencePalette.LightText))
        assertEquals("#27272A", hex(ReferencePalette.LightText2))
        assertEquals("#6B6B75", hex(ReferencePalette.LightText4))
        assertEquals("#8E8E98", hex(ReferencePalette.LightText5))
        assertEquals("#F4F4F6", hex(ReferencePalette.DarkText))
        assertEquals("#D4D4D8", hex(ReferencePalette.DarkText2))
        assertEquals("#8A8A94", hex(ReferencePalette.DarkText4))
        assertEquals("#6B6B75", hex(ReferencePalette.DarkText5))
        assertEquals("#2F6BF5", hex(ReferencePalette.Accent))
        assertEquals("#CF222E", hex(ReferencePalette.Danger.light))
        assertEquals("#F85149", hex(ReferencePalette.Danger.dark))
        assertEquals("#1A7F37", hex(ReferencePalette.Success.light))
        assertEquals("#3FB950", hex(ReferencePalette.Success.dark))
        assertEquals("#B76E00", hex(ReferencePalette.Warning.light))
        assertEquals("#F5A524", hex(ReferencePalette.Warning.dark))
        assertEquals("#6B6B75", hex(ReferencePalette.NeutralAction))
    }

    @Test
    fun `les cheveux de la reference sont bien des cheveux`() {
        // 8 % et 7 % : un séparateur ne doit pas se lire comme une bordure (la maison portait 29 %
        // et 60 %, ce qui dessinait des cadres). Compose range l'alpha sur 8 bits : la valeur lue
        // est 20/255, donc on mesure le **crantage**, pas une égalité au centième.
        assertTrue("le séparateur clair est un cheveu", ReferencePalette.LightLine.alpha in 0.05f..0.10f)
        assertTrue("le séparateur sombre est un cheveu", ReferencePalette.DarkLine.alpha in 0.04f..0.10f)
        assertTrue("le cheveu fort clair reste léger", ReferencePalette.LightLineStrong.alpha in 0.10f..0.20f)
        assertTrue("le cheveu fort sombre reste léger", ReferencePalette.DarkLineStrong.alpha in 0.08f..0.20f)
    }

    @Test
    fun `les sheets groupees prennent la page et la surface de la reference`() {
        listOf(false, true).forEach { dark ->
            val theme = ResolvedTheme.resolve(OnyxTheme.AMBER, dark, OnyxFonts.System)
            val appearance = if (dark) "dark" else "light"

            assertEquals(
                appearance,
                if (dark) ReferencePalette.DarkPage else ReferencePalette.LightPage,
                theme.formPageColor,
            )
            assertEquals(
                appearance,
                if (dark) ReferencePalette.DarkSurface else ReferencePalette.LightSurface,
                theme.formSurfaceColor,
            )
            assertEquals(
                appearance,
                if (dark) ReferencePalette.DarkText else ReferencePalette.LightText,
                theme.primaryText,
            )
            assertEquals(
                appearance,
                if (dark) ReferencePalette.DarkSurface2 else ReferencePalette.LightSurface2,
                theme.tertiarySurfaceColor,
            )
        }
    }

    @Test
    fun `les gris de la table sont neutres, et les seuls satures sont les signaux`() {
        val report = StringBuilder("rôle · couleur · écart rouge-bleu (points de 0 à 255)\n")

        // Les rôles **neutres** : un gris qui tire au bleu ou au violet est un gris faux. L'écart
        // maximal admis est celui que la **référence** se donne elle-même à elle-même (10 points,
        // l'encre muette `#6B6B75` / `#8A8A94`). La maison portait `#23262B` (8), `#DCE3EE` (18),
        // `#1A2230` (22) — trois gris qui tiraient, et c'est ce que le propriétaire a vu.
        val neutrals = mapOf(
            "page claire" to ReferencePalette.LightPage, "surface claire" to ReferencePalette.LightSurface,
            "creux clair" to ReferencePalette.LightSurface2, "troisième barreau clair" to ReferencePalette.LightSurface3,
            "cuvette claire" to ReferencePalette.LightWell,
            "page sombre" to ReferencePalette.DarkPage, "surface sombre" to ReferencePalette.DarkSurface,
            "creux sombre" to ReferencePalette.DarkSurface2, "troisième barreau sombre" to ReferencePalette.DarkSurface3,
            "cuvette sombre" to ReferencePalette.DarkWell, "feuille sombre" to ReferencePalette.DarkSheet,
            "pouce sombre" to ReferencePalette.DarkThumb,
            "encre claire" to ReferencePalette.LightText, "encre claire 2" to ReferencePalette.LightText2,
            "encre claire 3" to ReferencePalette.LightText4, "encre claire 4" to ReferencePalette.LightText5,
            "encre sombre" to ReferencePalette.DarkText, "encre sombre 2" to ReferencePalette.DarkText2,
            "encre sombre 3" to ReferencePalette.DarkText4, "encre sombre 4" to ReferencePalette.DarkText5,
            "action neutre" to ReferencePalette.NeutralAction,
        )
        var failures = 0
        neutrals.forEach { (role, colour) ->
            val spread = spread(colour)
            val ok = spread <= NeutralSpreadMax
            if (!ok) failures++
            report.append("$role · ${hex(colour)} · $spread ${if (ok) "ok" else "TEINTÉ"}\n")
        }
        // Et l'inverse : les signaux, eux, **doivent** être saturés — sinon ce ne sont plus des
        // signaux mais des gris, et l'écran ne dit plus rien.
        listOf(
            "danger clair" to ReferencePalette.Danger.light, "danger sombre" to ReferencePalette.Danger.dark,
            "succès clair" to ReferencePalette.Success.light, "succès sombre" to ReferencePalette.Success.dark,
            "avertissement clair" to ReferencePalette.Warning.light, "avertissement sombre" to ReferencePalette.Warning.dark,
        ).forEach { (role, colour) ->
            val value = spread(colour)
            val ok = value > NeutralSpreadMax
            if (!ok) failures++
            report.append("$role · ${hex(colour)} · $value ${if (ok) "ok" else "TROP PÂLE"}\n")
        }
        println(report)
        assertTrue(
            "des rôles neutres ne sont pas neutres :\n" +
                report.lineSequence().filter { it.contains("TEINTÉ") || it.contains("TROP PÂLE") }.joinToString("\n"),
            failures == 0,
        )
    }

    /** L'écart entre le canal le plus fort et le plus faible d'une couleur, en points de 0 à 255. */
    private fun spread(colour: Color): Int = listOf(colour.red, colour.green, colour.blue)
        .let { channels -> ((channels.max() - channels.min()) * 255f + 0.5f).toInt() }

    private fun hex(colour: Color): String = String.format(
        java.util.Locale.ROOT,
        "#%02X%02X%02X",
        (colour.red * 255f + 0.5f).toInt(),
        (colour.green * 255f + 0.5f).toInt(),
        (colour.blue * 255f + 0.5f).toInt(),
    )

    companion object {
        /**
         * L'écart maximal d'un gris de la maison, en points : **10**, l'écart que la référence
         * s'accorde à elle-même sur son encre muette. Un gris plus bleu que ça est un gris qui
         * tire, et cela se mesure.
         */
        const val NeutralSpreadMax = 10
    }
}

