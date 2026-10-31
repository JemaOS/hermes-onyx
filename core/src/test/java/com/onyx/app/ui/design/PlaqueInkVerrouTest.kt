package com.onyx.app.ui.design

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou d'appariement surface / encre de la plaque du bas** — et il **mord**.
 *
 * Le défaut qu'il ferme : en apparence sombre, les icônes et les libellés de la rangée d'onglets de
 * la plaque se lisaient en quasi noir sur quasi noir (« on ne voit rien, les icônes sont
 * illisibles »), alors qu'en apparence claire la même barre était juste. La cause n'était pas une
 * couleur écrite en dur : c'était le **fond** contre lequel l'encre était dérivée. La barre
 * interrogeait le pire des deux aplats qui peuvent passer derrière la vitre (blanc pur et noir pur)
 * et gardait le plus **clair** — un gris à 189 —, puis « assombrissait » l'encre claire du thème
 * jusqu'à `#2F2F30`, soit **1,40:1** sur la plaque *réellement peinte* (18, 18, 21).
 *
 * Ce que le test exige, et c'est ce qui le fait mordre :
 *
 * 1. **chaque encre de la plaque tient son seuil dans les DEUX apparences**, contre le fond
 *    réellement composé ([plaqueGround]) — libellé d'onglet 4,5:1, icône 3:1 ;
 * 2. **l'encre de la plaque est celle du thème** (l'encre secondaire au repos, l'accent en encre
 *    pour l'onglet actif, l'encre pleine pour le repère-visage) : une valeur **dérivée** dans
 *    l'apparence de référence fait échouer le test — c'est l'assertion qui attrape « l'encre du
 *    thème clair restée en sombre » ;
 * 3. **le sens du rattrapage suit le fond** : en sombre l'encre est du côté clair de sa surface, en
 *    clair du côté sombre.
 *
 * Le test lit **les mêmes fonctions que l'écran** ([plaqueVeil], [plaqueGround], [plaqueInk]) : le
 * chiffre imprimé et le chiffre vérifié sont un seul calcul. Le tableau est imprimé — il est le
 * relevé du rapport.
 */
class PlaqueInkVerrouTest {

    private fun name(color: Color): String = "#%02X%02X%02X".format(
        (color.red * 255).roundToInt(),
        (color.green * 255).roundToInt(),
        (color.blue * 255).roundToInt(),
    )

    private fun theme(theme: OnyxTheme, dark: Boolean): ResolvedTheme =
        ResolvedTheme(theme = theme, palette = theme.palette, dark = dark, fonts = OnyxFonts.System)

    /** Les trois onglets de contenu, puis le visage — le même ordre que la barre. */
    private val marks: List<Pair<String, Boolean>> = listOf(
        "Chat (onglet)" to false,
        "Flux (onglet)" to false,
        "Fichiers (onglet)" to false,
        "Onyx (repère-visage)" to true,
    )

    /** Le fond et l'encre de chaque repère, dans une apparence : au repos, puis chacun à tour de rôle actif. */
    private fun rows(theme: ResolvedTheme): List<Triple<String, Color, Color>> = buildList {
        marks.forEach { (mark, isFace) ->
            add(Triple(mark, plaqueGround(theme, selected = false), plaqueInk(theme, selected = false, isFace = isFace)))
        }
        marks.forEach { (mark, isFace) ->
            add(Triple("$mark — ACTIF", plaqueGround(theme, selected = true), plaqueInk(theme, selected = true, isFace = isFace)))
        }
    }

    /**
     * **Le relevé complet : `écran → élément → contraste sombre → contraste clair → verdict`.**
     *
     * C'est le tableau du rapport, imprimé par le test — donc recopié, jamais ressaisi.
     */
    @Test
    fun `le releve de la plaque est imprime et tient les seuils dans les deux apparences`() {
        val dark = theme(OnyxTheme.STANDARD, true)
        val light = theme(OnyxTheme.STANDARD, false)
        val darkRows = rows(dark).associateBy { it.first }
        val lightRows = rows(light).associateBy { it.first }

        println("== PLAQUE DU BAS — écran → élément → rapport sombre → rapport clair → verdict ==")
        println("   seuil : libellé 4,50:1 · icône 3,00:1")
        println("   plaque (au repos) sombre = ${name(plaqueGround(dark, false))} · claire = ${name(plaqueGround(light, false))}")
        println("   pastille de l'onglet actif : sombre = ${name(plaqueGround(dark, true))} · claire = ${name(plaqueGround(light, true))}")

        var worstDark = Float.MAX_VALUE
        var worstLight = Float.MAX_VALUE
        darkRows.keys.forEach { mark ->
            val (_, darkGround, darkInk) = darkRows.getValue(mark)
            val (_, lightGround, lightInk) = lightRows.getValue(mark)
            val ratioDark = OnyxContrast.ratio(darkInk, darkGround)
            val ratioLight = OnyxContrast.ratio(lightInk, lightGround)
            worstDark = minOf(worstDark, ratioDark)
            worstLight = minOf(worstLight, ratioLight)
            println(
                "   $mark → ${name(darkInk)} sur ${name(darkGround)} = ${"%.2f".format(ratioDark)}:1" +
                    " · ${name(lightInk)} sur ${name(lightGround)} = ${"%.2f".format(ratioLight)}:1",
            )
            assertTrue(
                "PLAQUE / $mark : en apparence SOMBRE l'encre ${name(darkInk)} ne tient que " +
                    "${"%.2f".format(ratioDark)}:1 sur sa plaque ${name(darkGround)} (seuil 4,50:1) — " +
                    "core/src/main/java/com/onyx/app/ui/design/ChatTabBar.kt:493 (plaqueInk) appelle " +
                    "readableInk sur plaqueGround",
                ratioDark >= OnyxContrast.TEXT_MIN,
            )
            assertTrue(
                "PLAQUE / $mark : en apparence CLAIRE l'encre ${name(lightInk)} ne tient que " +
                    "${"%.2f".format(ratioLight)}:1 sur sa plaque ${name(lightGround)} (seuil 4,50:1) — " +
                    "core/src/main/java/com/onyx/app/ui/design/ChatTabBar.kt:493 (plaqueInk) appelle " +
                    "readableInk sur plaqueGround",
                ratioLight >= OnyxContrast.TEXT_MIN,
            )
        }
        println("   pire encre sombre = ${"%.2f".format(worstDark)}:1 · pire encre claire = ${"%.2f".format(worstLight)}:1")
    }

    /** **Tous les thèmes, les deux apparences** : aucune encre de la plaque ne descend sous son seuil. */
    @Test
    fun `aucun theme ne fait passer une encre de la plaque sous son seuil`() {
        OnyxTheme.entries.forEach { nom ->
            listOf(false, true).forEach { d ->
                val t = theme(nom, d)
                val appearance = if (d) "sombre" else "clair"
                rows(t).forEach { (mark, ground, ink) ->
                    val ratio = OnyxContrast.ratio(ink, ground)
                    assertTrue(
                        "PLAQUE / $mark ($nom, $appearance) : ${name(ink)} sur ${name(ground)} = " +
                            "${"%.2f".format(ratio)}:1, sous le seuil de 4,50:1 — " +
                            "core/src/main/java/com/onyx/app/ui/design/ChatTabBar.kt:493 (plaqueInk)",
                        ratio >= OnyxContrast.TEXT_MIN,
                    )
                }
            }
        }
    }

    /**
     * **L'encre de la plaque est celle du thème** — la mutation « remets l'encre du thème clair en
     * sombre ».
     *
     * L'assertion est **exacte** : au repos l'encre est *l'encre secondaire du thème*, à l'état actif
     * *son accent en encre*, pour le visage *son encre pleine*. Aucune valeur dérivée n'est admise
     * quand celle du thème passe (le `readableInk` de la plaque ne doit se déclencher que si elle
     * échoue — et elle ne doit pas échouer sur la plaque telle qu'elle est peinte).
     */
    @Test
    fun `l'encre de la plaque est celle du theme dans son apparence`() {
        listOf(false, true).forEach { d ->
            val t = theme(OnyxTheme.STANDARD, d)
            val appearance = if (d) "sombre" else "clair"
            marks.forEach { (mark, isFace) ->
                val ink = plaqueInk(t, selected = false, isFace = isFace)
                val expected = if (isFace) t.textColor else t.secondaryText
                assertEquals(
                    "PLAQUE / $mark ($appearance) : l'encre posée ${name(ink)} n'est pas celle du " +
                        "thème ${name(expected)} — une encre dérivée s'est substituée à elle. " +
                        "core/src/main/java/com/onyx/app/ui/design/ChatTabBar.kt:493 (plaqueInk)",
                    expected, ink,
                )
                val activeInk = plaqueInk(t, selected = true, isFace = isFace)
                val activeExpected = if (isFace) t.textColor else t.accentInk
                assertEquals(
                    "PLAQUE / $mark ACTIF ($appearance) : l'encre posée ${name(activeInk)} n'est pas " +
                        "celle du thème ${name(activeExpected)}. " +
                        "core/src/main/java/com/onyx/app/ui/design/ChatTabBar.kt:493 (plaqueInk)",
                    activeExpected, activeInk,
                )
            }
        }
    }

    /**
     * **Le sens du rattrapage suit le fond.** En sombre l'encre est du côté **clair** de sa surface,
     * en clair du côté **sombre** — jamais l'inverse.
     */
    @Test
    fun `l'encre de la plaque est du bon cote de sa surface`() {
        OnyxTheme.entries.forEach { nom ->
            listOf(false, true).forEach { d ->
                val t = theme(nom, d)
                marks.forEach { (mark, isFace) ->
                    listOf(false, true).forEach { selected ->
                        val ground = plaqueGround(t, selected)
                        val ink = plaqueInk(t, selected, isFace)
                        if (d) {
                            assertTrue(
                                "PLAQUE / $mark ($nom, sombre) : l'encre ${name(ink)} n'est pas plus " +
                                    "claire que sa surface ${name(ground)}. " +
                                    "core/src/main/java/com/onyx/app/ui/design/ChatTabBar.kt:493 (plaqueInk)",
                                OnyxContrast.luminance(ink) > OnyxContrast.luminance(ground),
                            )
                        } else {
                            assertTrue(
                                "PLAQUE / $mark ($nom, clair) : l'encre ${name(ink)} n'est pas plus " +
                                    "sombre que sa surface ${name(ground)}. " +
                                    "core/src/main/java/com/onyx/app/ui/design/ChatTabBar.kt:493 (plaqueInk)",
                                OnyxContrast.luminance(ink) < OnyxContrast.luminance(ground),
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * **La pastille de l'onglet actif se détache de la plaque** — l'onglet actif se voit **avant** de
     * porter une couleur.
     *
     * En apparence **sombre**, la marche est exigée telle quelle ([OnyxSurfaces.CapsuleStepMin],
     * 1,15:1) : la plaque y est du graphite, la pastille a de la place devant elle.
     *
     * En apparence **claire**, cette marche n'est **pas atteignable** : la plaque claire est à
     * `#F9F9FA` (luminance 0,966) et le blanc pur n'en donne que **1,058:1**. C'est la contrainte que
     * la maison connaît déjà (« en clair, la séparation vient du filet et de l'ombre, pas de la
     * luminance ») : le test exige donc que la pastille claire **atteigne son plafond physique**
     * (à 2 % près), et il l'**imprime**. Un test qui exigerait 1,15:1 en clair serait un test rouge
     * quoi qu'on fasse.
     */
    @Test
    fun `la pastille active se detache de la plaque dans les deux apparences`() {
        val dark = theme(OnyxTheme.STANDARD, true)
        val plateDark = plaqueGround(dark, selected = false)
        val pillDark = plaqueGround(dark, selected = true)
        val stepDark = OnyxContrast.ratio(pillDark, plateDark)
        println("   pastille sombre : ${name(pillDark)} sur ${name(plateDark)} = ${"%.3f".format(stepDark)}:1")
        assertTrue(
            "PLAQUE / pastille (sombre) : ${"%.3f".format(stepDark)}:1 contre la plaque, sous " +
                "${OnyxSurfaces.CapsuleStepMin} — " +
                "core/src/main/java/com/onyx/app/ui/design/PlaqueGeometry.kt:169 (activePill)",
            stepDark >= OnyxSurfaces.CapsuleStepMin,
        )

        val light = theme(OnyxTheme.STANDARD, false)
        val plateLight = plaqueGround(light, selected = false)
        val pillLight = plaqueGround(light, selected = true)
        val stepLight = OnyxContrast.ratio(pillLight, plateLight)
        val ceiling = OnyxContrast.ratio(Color.White, plateLight)
        println(
            "   pastille claire : ${name(pillLight)} sur ${name(plateLight)} = ${"%.3f".format(stepLight)}:1" +
                " (plafond physique, plaque à L=${"%.3f".format(OnyxContrast.luminance(plateLight))} : " +
                "${"%.3f".format(ceiling)}:1)",
        )
        assertTrue(
            "PLAQUE / pastille (claire) : ${"%.3f".format(stepLight)}:1 contre la plaque, quand le " +
                "plafond physique du blanc pur y vaut ${"%.3f".format(ceiling)}:1 — la pastille claire " +
                "n'atteint même pas son plafond : core/src/main/java/com/onyx/app/ui/design/" +
                "PlaqueGeometry.kt:154 (ActivePillLightAlpha)",
            stepLight >= ceiling * 0.98f,
        )
    }

    /**
     * **Ce que la plaque fait sur la page** — imprimé, et **non** gardé : c'est la matière de la
     * plaque (l'épaisseur de son voile de verre), et deux autres lots la tiennent. Le chiffre est
     * publié ici pour qu'il soit nommé plutôt que découvert sur une capture.
     */
    @Test
    fun `le releve imprime la marche de la plaque sur la page`() {
        listOf(true, false).forEach { d ->
            val t = theme(OnyxTheme.STANDARD, d)
            val plate = plaqueGround(t, selected = false)
            val step = OnyxContrast.ratio(plate, OnyxSurfaces.page(t))
            println(
                "   plaque sur la page (${if (d) "sombre" else "clair"}) : ${name(plate)} sur " +
                    "${name(OnyxSurfaces.page(t))} = ${"%.3f".format(step)}:1 " +
                    "(barreau des surfaces flottantes : ${OnyxSurfaces.FloatStepMin})",
            )
        }
    }

    /**
     * **Le sens du rattrapage d'encre suit le fond** — la règle pure, éprouvée sur les deux pôles.
     * C'est la règle dont l'absence produisait le défaut.
     */
    @Test
    fun `readableInk s'eloigne du fond au lieu de l'y enfoncer`() {
        val dark = Color(0xFF121215)
        val light = Color(0xFFF7F7F9)
        assertTrue(
            "readableInk assombrit une encre sur un fond sombre — c'est le défaut d'origine",
            OnyxContrast.luminance(readableInk(Color(0xFF27272A), dark)) >
                OnyxContrast.luminance(Color(0xFF27272A)),
        )
        assertTrue(
            "readableInk éclaircit une encre sur un fond clair",
            OnyxContrast.luminance(readableInk(Color(0xFFD4D4D8), light)) <
                OnyxContrast.luminance(Color(0xFFD4D4D8)),
        )
        assertEquals(Color(0xFFD4D4D8), readableInk(Color(0xFFD4D4D8), dark))
        assertEquals(Color(0xFF27272A), readableInk(Color(0xFF27272A), light))
    }
}
