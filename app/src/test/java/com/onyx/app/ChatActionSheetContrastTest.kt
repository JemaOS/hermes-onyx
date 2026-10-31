package com.onyx.app

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.chat.components.ChatActionBadge
import com.onyx.app.ui.chat.components.ChatActionTint
import com.onyx.app.ui.chat.components.SheetInk
import com.onyx.app.ui.chat.components.tintHue
import com.onyx.app.ui.design.OnyxContrast
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La **lisibilité** de la feuille des actions, **mesurée** : les seuils WCAG 2.1 AA sur tous les
 * thèmes et les deux apparences — 4,5:1 pour un mot, 3:1 pour une icône.
 *
 * Ce que ce test ferme, et que la capture du propriétaire montrait déjà :
 *
 *  1. le libellé secondaire du système (`secondaryText`) posé **tel quel** sur la surface claire la
 *     plus claire est à **3,10:1** — sous le seuil d'un mot. C'est lui qui rendait les notes, les
 *     titres de section et les légendes « délavés » ;
 *  2. deux familles de pastille (identifiant, écrans) tiennent leur teinte de ce même libellé, qui
 *     est **translucide** (60 %). Mesurée sans être composée, la teinte « tenait » 7:1 ; **peinte**
 *     ainsi, elle tombait à 2,96:1 sur sa propre pastille. [ChatActionBadge.ink] rend donc une encre
 *     opaque : ce qui est mesuré est ce qui est peint.
 *
 * Les couples sont calculés comme l'écran les calcule (`SheetInk`, `ChatActionBadge`, `tintHue`), et
 * le pire cas est éprouvé **en plus** du thème : la surface la plus claire possible (blanc pur) et la
 * plus sombre. Le tableau complet part dans la sortie du test — c'est la preuve du lot.
 */
class ChatActionSheetContrastTest {

    private fun theme(theme: OnyxTheme, dark: Boolean) =
        ResolvedTheme.resolve(theme, dark = dark, fonts = OnyxFonts.System)

    private data class Pair(val what: String, val ink: Color, val ground: Color, val min: Float)

    /** Les couples texte de la feuille : le libellé, le rouge du danger, et la note secondaire. */
    private fun textPairs(t: ResolvedTheme): List<Pair> {
        val surface = t.surfaceColor
        return listOf(
            Pair("libellé · vitre", SheetInk.label(t, surface, dangerous = false), surface, OnyxContrast.TEXT_MIN),
            Pair("libellé de danger · vitre", SheetInk.label(t, surface, dangerous = true), surface, OnyxContrast.TEXT_MIN),
            Pair("note secondaire · vitre", SheetInk.muted(t, surface), surface, OnyxContrast.TEXT_MIN),
        )
    }

    /** Les pastilles d'icône : une par famille de teinte, chacune sur son propre fond. */
    private fun badgePairs(t: ResolvedTheme): List<Pair> {
        val surface = t.surfaceColor
        return ChatActionTint.entries.map { tint ->
            val hue = tintHue(tint, t)
            Pair(
                "pastille $tint",
                ChatActionBadge.ink(hue, surface),
                ChatActionBadge.fill(hue, surface),
                OnyxContrast.ICON_MIN,
            )
        }
    }

    private fun report(pairs: List<Pair>) {
        pairs.forEach { pair ->
            val ratio = OnyxContrast.ratio(pair.ink, pair.ground)
            println(
                "  %-34s %6.2f:1  (seuil %.1f)  encre alpha %.2f".format(
                    pair.what, ratio, pair.min, pair.ink.alpha,
                ),
            )
        }
    }

    @Test
    fun `le texte et les vignettes tiennent le seuil sur tous les themes`() {
        var checked = 0
        listOf(false, true).forEach { dark ->
            OnyxTheme.entries.forEach { name ->
                val t = theme(name, dark)
                val pairs = textPairs(t) + badgePairs(t)
                println("— thème $name (${if (dark) "sombre" else "clair"}) —")
                report(pairs)
                pairs.forEach { pair ->
                    val ratio = OnyxContrast.ratio(pair.ink, pair.ground)
                    assertTrue(
                        "${pair.what} sous le seuil : $ratio < ${pair.min}",
                        ratio >= pair.min,
                    )
                    checked += 1
                }
            }
        }
        assertTrue("le balayage ne mesure rien", checked > 40)
    }

    @Test
    fun `dans le pire cas, la vitre la plus claire et la plus sombre tiennent aussi`() {
        // « Le pire cas » demandé : le fond de vitre le plus clair possible — la surface d'un thème
        // clair, poussée au blanc pur — et, à l'autre bout, un presque-noir. Les encres de la feuille
        // sont dérivées de la surface, elles doivent donc y tenir **par construction**.
        val white = Color(0xFFFFFFFF)
        val nearBlack = Color(0xFF101014)
        val t = theme(OnyxTheme.SLATE, dark = false)
        listOf("blanc pur" to white, "presque-noir" to nearBlack).forEach { (name, surface) ->
            val pairs = listOf(
                Pair("libellé · $name", SheetInk.label(t, surface, false), surface, OnyxContrast.TEXT_MIN),
                Pair("danger · $name", SheetInk.label(t, surface, true), surface, OnyxContrast.TEXT_MIN),
                Pair("note · $name", SheetInk.muted(t, surface), surface, OnyxContrast.TEXT_MIN),
            ) + ChatActionTint.entries.map { tint ->
                val hue = tintHue(tint, t)
                Pair(
                    "pastille $tint · $name",
                    ChatActionBadge.ink(hue, surface),
                    ChatActionBadge.fill(hue, surface),
                    OnyxContrast.ICON_MIN,
                )
            }
            println("— pire cas : $name —")
            report(pairs)
            pairs.forEach { pair ->
                assertTrue(
                    "${pair.what} sous le seuil : ${OnyxContrast.ratio(pair.ink, pair.ground)}",
                    OnyxContrast.ratio(pair.ink, pair.ground) >= pair.min,
                )
            }
        }
    }

    @Test
    fun `une teinte translucide est composee avant d etre mesuree`() {
        // Le cas mesuré du défaut : le libellé secondaire du système, à 60 %, sur la surface claire
        // la plus claire. Mesuré tel quel il « tient » ; peint tel quel, il ne tient plus.
        val surface = Color(0xFFFFFFFF)
        val translucent = Color(0x993C3C43)
        val raw = OnyxContrast.ratio(translucent, OnyxContrast.over(translucent, surface))
        assertTrue("la teinte brute devait frôler le seuil : $raw", raw >= OnyxContrast.ICON_MIN)

        val ink = ChatActionBadge.ink(translucent, surface)
        assertEquals("l'encre d'une vignette doit être opaque", 1f, ink.alpha, 0.001f)
        val drawn = OnyxContrast.ratio(ink, ChatActionBadge.fill(translucent, surface))
        assertTrue("la vignette peinte doit tenir 3:1, mesuré $drawn", drawn >= OnyxContrast.ICON_MIN)
    }

    @Test
    fun `la note secondaire ne descend jamais sous le seuil d un mot`() {
        // Ce que le défaut mesurait : 3,10:1 sur du blanc pur, sous 4,5:1. La teinte d'avant est
        // **gelée** (`#3C3C43` à 60 %) : `secondaryText` porte maintenant le secondaire de la
        // référence (`#27272A`), qui mesure 14,9:1 — le défaut n'est donc plus reproductible avec
        // le jeton vivant, et un relevé d'avant ne se lit pas dans le jeton d'après.
        val white = Color(0xFFFFFFFF)
        val legacy = Color(0x993C3C43)
        val t = theme(OnyxTheme.SLATE, dark = false)
        val before = OnyxContrast.over(legacy, white)
        assertTrue(
            "le libellé secondaire brut devait être sous le seuil : ${OnyxContrast.ratio(before, white)}",
            OnyxContrast.ratio(before, white) < OnyxContrast.TEXT_MIN,
        )
        assertTrue(
            "le secondaire de la reference doit, lui, tenir le seuil : ${OnyxContrast.ratio(t.secondaryText, white)}",
            OnyxContrast.ratio(t.secondaryText, white) >= OnyxContrast.TEXT_MIN,
        )
        assertTrue(
            "SheetInk.muted doit le ramener au seuil",
            OnyxContrast.ratio(SheetInk.muted(t, white), white) >= OnyxContrast.TEXT_MIN,
        )
    }
}
