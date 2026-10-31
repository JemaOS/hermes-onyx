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
 * **Le verrou des commandes du compositeur et de la barre d'onglets.**
 *
 * Il ne juge pas une intention : il **recalcule ce que l'écran peint**, par les mêmes appels que
 * l'écran (`ComposerCommands`, `plaqueGround`, `plaqueInk`, `ModelContrast`), pour les **sept thèmes
 * dans les deux apparences**, et il exige les seuils de la maison — **3:1** pour un contrôle (un
 * disque, un trait d'icône), **4,5:1** pour un glyphe porteur de sens.
 *
 * La mesure qui a motivé ce fichier est celle de l'envoi **inerte** (champ vide) : le bouton rond
 * passait `enabled = hasDraft || trailingState != "send"`, donc `false` sur un champ vide, et
 * `onyxClickable` applique alors `Modifier.alpha(0.35)` à **tout** l'arbre — le disque **et** la
 * flèche. Composé sur la plaque, le disque d'envoi mesurait alors **1,49:1** en apparence sombre
 * (« Standard »), **1,40:1** au pire des sept thèmes et **2,60:1** au mieux : sous le plancher
 * d'icône dans **les quatorze couples (thème, apparence)**. La zone du bouton était vide — la
 * capture du propriétaire. Le relevé du témoin est imprimé par le premier test.
 */
class CompositeurCommandsVerrouTest {

    private fun theme(nom: OnyxTheme, dark: Boolean): ResolvedTheme =
        ResolvedTheme(theme = nom, palette = nom.palette, dark = dark, fonts = OnyxFonts.System)

    private fun name(c: Color): String = "#%02X%02X%02X".format(
        (c.red * 255).roundToInt(), (c.green * 255).roundToInt(), (c.blue * 255).roundToInt(),
    )

    private fun r(a: Color, b: Color): Double = contrastRatio(a, b)

    private fun f(v: Double): String = "%.2f".format(java.util.Locale.ROOT, v)

    /** Une commande telle que **l'écran la peint** : son fond (le disque, ou la plaque) et son encre. */
    private data class Cmd(
        val nom: String,
        val fond: Color,
        val glyphe: Color,
        /** Le fond contre lequel le **contrôle** doit se détacher (la plaque). `null` = sans objet. */
        val dessous: Color?,
        val seuilGlyphe: Double,
    )

    /**
     * **Le relevé, dans l'ordre où l'écran dessine** — « + », dictée, agrandissement, mode vocal,
     * envoi (champ vide **et** champ rempli), arrêt, steer, file, puce du modèle, puis les trois
     * états d'onglet.
     *
     * Chaque ligne appelle la **même** fonction que `OnyxComposer` / `ChatTabBar` : le chiffre
     * annoncé et le chiffre vérifié sont le même appel.
     */
    private fun releve(t: ResolvedTheme): List<Cmd> {
        val sol = ComposerCommands.ground(t)
        val add = ComposerCommands.glyphOnPlaque(t, t.secondaryText)
        val micro = ComposerCommands.glyphOnPlaque(t, t.secondaryText)
        val agrandir = ComposerCommands.glyphOnPlaque(t, t.secondaryText)
        val talkDisc = ComposerCommands.disc(t, t.fill)
        val talkInk = OnyxContrast.dimmed(
            OnyxContrast.over(t.secondaryText, talkDisc), talkDisc,
            OnyxContrast.ICON_MIN + ComposerCommands.IDLE_MARGIN,
        )
        val sendDisc = ComposerCommands.disc(t, t.accent)
        val sendInk = ComposerCommands.inkOnDisc(t, sendDisc)
        val stopDisc = ComposerCommands.disc(t, t.fill)
        val stopInk = ComposerCommands.inkOnDisc(t, stopDisc, t.textColor)
        val steerDisc = ComposerCommands.disc(t, t.warning)
        val steerInk = ComposerCommands.inkOnDisc(t, steerDisc)
        val afterDisc = ComposerCommands.disc(t, t.accent)
        val afterInk = ComposerCommands.inkOnDisc(t, afterDisc)
        val puce = ModelContrast.chip(t)
        val puceInk = readableInk(t.textColor, puce)
        val ongletActif = plaqueGround(t, selected = true)
        val ongletRepos = plaqueGround(t, selected = false)
        return listOf(
            // Les commandes au trait : leur fond **est** la plaque.
            Cmd("pièce jointe (+)", sol, add, null, TEXT_MIN_OF_HOUSE),
            Cmd("dictée (micro)", sol, micro, null, TEXT_MIN_OF_HOUSE),
            Cmd("agrandissement", sol, agrandir, null, TEXT_MIN_OF_HOUSE),
            // Les disques : le contrôle se détache de la plaque, le glyphe de son disque.
            Cmd("mode vocal (disque)", talkDisc, talkInk, sol, ICON_MIN_OF_HOUSE),
            Cmd("mode vocal (onde)", talkDisc, talkInk, null, ICON_MIN_OF_HOUSE),
            // **L'envoi inerte** (champ vide) se peint comme l'envoi armé : c'est la propriété
            // que le défaut violait. `INERT_ALPHA` est vérifié séparément.
            Cmd("envoi (champ vide)", sendDisc, sendInk, sol, TEXT_MIN_OF_HOUSE),
            Cmd("envoi (champ rempli)", sendDisc, sendInk, sol, TEXT_MIN_OF_HOUSE),
            Cmd("arrêt (pendant l'envoi)", stopDisc, stopInk, sol, TEXT_MIN_OF_HOUSE),
            Cmd("steer", steerDisc, steerInk, sol, TEXT_MIN_OF_HOUSE),
            Cmd("file (after)", afterDisc, afterInk, sol, TEXT_MIN_OF_HOUSE),
            // Le sélecteur de modèle : sa puce est une vitre sur la carte de composition.
            Cmd("sélecteur de modèle", puce, puceInk, null, TEXT_MIN_OF_HOUSE),
            // La barre d'onglets : le fond est une pastille, donc le seul seuil qui s'applique au
            // glyphe est celui d'une icône.
            Cmd("onglet actif", ongletActif, plaqueInk(t, true, false), null, ICON_MIN_OF_HOUSE),
            Cmd("onglet au repos", ongletRepos, plaqueInk(t, false, false), null, ICON_MIN_OF_HOUSE),
            Cmd("onglet visage", ongletRepos, plaqueInk(t, false, true), null, ICON_MIN_OF_HOUSE),
        )
    }

    /** Le **témoin du défaut** : ce que la tentative précédente peignait, et son rapport mesuré. */
    private fun temoin(t: ResolvedTheme): List<Triple<String, Double, String>> {
        val sol = ComposerCommands.ground(t)
        val envoi = t.accent
        val attenue = OnyxContrast.over(envoi.copy(alpha = 0.35f), sol)
        val vocal = OnyxContrast.over(t.fill.copy(alpha = 0.35f), sol)
        return listOf(
            Triple("envoi atténué à 35 %", r(attenue, sol), "${name(envoi)} @0,35 sur $sol = ${name(attenue)}"),
            Triple("mode vocal atténué à 35 %", r(vocal, sol), "${name(t.fill)} @0,35 sur $sol = ${name(vocal)}"),
            Triple("mode vocal au plein", r(t.fill, sol), "${name(t.fill)} sur $sol"),
            Triple("envoi au plein", r(envoi, sol), "${name(envoi)} sur $sol"),
        )
    }

    @Test
    fun `releve des commandes — les deux apparences, les sept themes`() {
        println("== COMMANDES DU COMPOSITEUR ET DE LA BARRE — APRÈS correction ==")
        OnyxTheme.entries.forEach { nom ->
            listOf(false, true).forEach { dark ->
                val t = theme(nom, dark)
                val sol = ComposerCommands.ground(t)
                println("-- %-12s %-6s | sol de plaque %s".format(nom.id, if (dark) "sombre" else "clair", name(sol)))
                releve(t).forEach { c ->
                    println(
                        "   %-24s fond %s  encre %s  glyphe/fond %s  fond/sol %s".format(
                            c.nom, name(c.fond), name(c.glyphe), f(r(c.glyphe, c.fond)),
                            c.dessous?.let { f(r(c.fond, it)) } ?: "—",
                        ),
                    )
                }
                println("   TÉMOIN (défaut fermé) :")
                temoin(t).forEach { (quoi, ratio, detail) ->
                    println("     %-28s %s:1   (%s)".format(quoi, f(ratio), detail))
                }
            }
        }
    }

    /**
     * **LE VERROU.** Il nomme la commande qui tombe, son thème, son apparence et le rapport mesuré.
     */
    @Test
    fun `aucune commande sous le seuil, dans l une des deux apparences`() {
        val fautes = mutableListOf<String>()
        OnyxTheme.entries.forEach { nom ->
            listOf(false, true).forEach { dark ->
                val t = theme(nom, dark)
                releve(t).forEach { c ->
                    if (c.dessous != null) {
                        val d = r(c.fond, c.dessous)
                        if (d < ICON_MIN_OF_HOUSE) {
                            fautes += "%s / %s : le FOND de « %s » mesure %s:1 contre son sol %s (seuil %s)".format(
                                nom.id, if (dark) "sombre" else "clair", c.nom, f(d), name(c.dessous), f(ICON_MIN_OF_HOUSE),
                            )
                        }
                    }
                    val g = r(c.glyphe, c.fond)
                    if (g < c.seuilGlyphe) {
                        fautes += "%s / %s : l'ENCRE de « %s » mesure %s:1 sur son fond %s (seuil %s)".format(
                            nom.id, if (dark) "sombre" else "clair", c.nom, f(g), name(c.fond), f(c.seuilGlyphe),
                        )
                    }
                    if (c.glyphe == Color.Unspecified) {
                        fautes += "%s / %s : « %s » n'a pas d'encre (Color.Unspecified = noir par défaut)".format(
                            nom.id, if (dark) "sombre" else "clair", c.nom,
                        )
                    }
                }
            }
        }
        assertTrue("commandes sous le seuil :\n" + fautes.joinToString("\n"), fautes.isEmpty())
    }

    /**
     * **L'envoi inerte ne change pas de peinture, et le témoin du défaut reste sous le seuil.**
     *
     * Un contrôle inerte dont l'encre est mesurée passe `INERT_ALPHA` : `enabled` gouverne le clic,
     * jamais la peinture. Le témoin mesure ce que l'atténuation de la maison (0,35) aurait donné —
     * s'il remontait au-dessus du plancher, c'est que le relevé du défaut a vieilli et qu'il faut le
     * refaire, pas le garder.
     */
    @Test
    fun `l envoi inerte se peint comme l envoi arme`() {
        assertEquals(
            "une commande mesurée ne s'atténue pas quand elle est inerte",
            1f,
            ComposerCommands.INERT_ALPHA,
            0f,
        )
        OnyxTheme.entries.forEach { nom ->
            listOf(false, true).forEach { dark ->
                val t = theme(nom, dark)
                val sol = ComposerCommands.ground(t)
                val disc = ComposerCommands.disc(t, t.accent)
                val idem = OnyxContrast.over(disc.copy(alpha = ComposerCommands.INERT_ALPHA), sol)
                assertTrue(
                    "$nom / ${if (dark) "sombre" else "clair"} : l'envoi inerte doit peindre $disc " +
                        "sur $sol, or il peint $idem",
                    r(disc, sol) == r(idem, sol),
                )
                val attenue = OnyxContrast.over(disc.copy(alpha = 0.35f), sol)
                assertTrue(
                    "$nom / ${if (dark) "sombre" else "clair"} : le témoin du défaut devait rester " +
                        "sous le plancher d'icône — atténué à 35 %, l'envoi mesurerait ${f(r(attenue, sol))}:1",
                    r(attenue, sol) < ICON_MIN_OF_HOUSE,
                )
            }
        }
    }

    private companion object {
        /** Le plancher de la maison pour un glyphe porteur de sens (WCAG AA). */
        const val TEXT_MIN_OF_HOUSE = 4.5

        /** Le plancher de la maison pour un contrôle : un disque, un trait d'icône. */
        const val ICON_MIN_OF_HOUSE = 3.0
    }
}
