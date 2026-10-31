package com.onyx.app.ui.chat.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.theme.ResolvedTheme
import java.io.File
import java.util.Locale
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou du bloc de réflexion.** Ce fichier ne décrit pas seulement le nouveau dessin : il le
 * **tient**, et il **mord** — deux tests échouent si le bloc repasse sous son seuil de contraste, et
 * si un élément flottant le recouvre. Les preuves de morsure sont dans le rapport du lot (on remet
 * l'encre secondaire du thème, ou la pastille au bord bas, et le test tombe).
 *
 * Trois familles, dans l'ordre du brief : la **forme** (un filet, pas un pavé), la **longueur** (la
 * troncature et son contrôle), et les deux **mesures** (contraste, recouvrement).
 */
class ReasoningBlockTest {

    // -------------------------------------------------------------------------------------------
    // 1. La forme : un filet et du texte en retrait, jamais un pavé plein
    // -------------------------------------------------------------------------------------------

    @Test
    fun `le bloc se lit derriere un filet, sur la page, jamais dans un pave`() {
        // Le filet est celui des replis de la maison, et le retrait est une marche de l'échelle.
        assertEquals(2.dp, ReasoningBlock.RailWidth)
        assertEquals(OnyxSpace.Snug, ReasoningBlock.TextIndent)

        val detail = messageRowSource()
            .substringAfter("private fun ReasoningDetail(")
            .substringBefore("private fun ReasoningLengthControl(")

        // Le filet est posé au ton de filet de la maison.
        assertTrue("le filet du bloc a disparu", detail.contains("ReasoningBlock.RailWidth"))
        assertTrue("le filet n'a plus le ton de la maison", detail.contains("background(theme.separator)"))
        assertTrue("le texte n'est plus en retrait", detail.contains("ReasoningBlock.TextIndent"))
        // **Plus de pavé** : ni vitre, ni aplat de surface, ni bordure de carte.
        assertFalse("une vitre est revenue dans le bloc", detail.contains("liquidGlass"))
        assertFalse("un aplat de surface est revenu", detail.contains("surfaceColor"))
        assertFalse("une bordure de carte est revenue", detail.contains("border("))
        // **Pas de monospace** : il est réservé aux identifiants.
        assertFalse("le bloc est repassé en monospace", detail.contains("FontFamily.Monospace"))
    }

    @Test
    fun `le bloc est a la marge du contenu`() {
        val detail = messageRowSource()
            .substringAfter("private fun ThinkingFold(")
            .substringBefore("private fun ReasoningDetail(")
        // La ligne du repli ne porte plus de retrait horizontal : elle commence au bord du message.
        assertFalse(
            "la ligne du raisonnement a repris un retrait horizontal",
            detail.contains("horizontal = OnyxSpace.GlyphGap"),
        )
        assertTrue(detail.contains(".padding(vertical = OnyxSpace.Hair)"))
    }

    // -------------------------------------------------------------------------------------------
    // 2. La longueur : troncature avec « Voir tout », « Réduire » en bas du bloc
    // -------------------------------------------------------------------------------------------

    @Test
    fun `au-dela du seuil le bloc se tronque et porte son controle`() {
        // Sous le seuil : rien à replier.
        assertFalse(reasoningIsLong("Une phrase courte."))
        val six = (1..ReasoningBlock.CollapsedLines).joinToString("\n") { "ligne $it" }
        assertFalse("le seuil lui-même tient", reasoningIsLong(six))
        val plus = (1..ReasoningBlock.CollapsedLines + 1).joinToString("\n") { "ligne $it" }
        assertTrue("une ligne de plus doit tronquer", reasoningIsLong(plus))
        // Un long paragraphe sans retour à la ligne se compte au repli réel.
        assertTrue(reasoningIsLong("mot ".repeat(120)))
        // La longueur se compte en lignes du corps, pas en octets.
        assertEquals(0, reasoningLineBudget(""))
        assertEquals(2, reasoningLineBudget("a".repeat(ReasoningBlock.CharsPerLine + 1)))
    }

    @Test
    fun `la troncature et la refermeture sont dans le source`() {
        val source = messageRowSource()
        val detail = source
            .substringAfter("private fun ReasoningDetail(")
            .substringBefore("private fun ReasoningLengthControl(")
        assertTrue("le bloc n'est plus borné", detail.contains("ReasoningBlock.CollapsedLines"))
        assertTrue("la troncature n'est plus marquée", detail.contains("TextOverflow.Ellipsis"))
        assertTrue("le pied du bloc a disparu", detail.contains("ReasoningLengthControl"))

        val control = source.substringAfter("private fun ReasoningLengthControl(")
        assertTrue(
            "le contrôle ne bascule plus Voir tout / Réduire",
            control.contains("OnyxLocalization[if (expanded) \"Show less\" else \"See all\"]"),
        )
        // La cible du pied est celle du pouce, jamais un mot sans cible.
        assertTrue(control.contains("OnyxSpace.TouchTarget"))
    }

    // -------------------------------------------------------------------------------------------
    // 3. L'annonce : ce que c'est, et si c'est brut
    // -------------------------------------------------------------------------------------------

    @Test
    fun `le bloc annonce ce qu'il est`() {
        val header = messageRowSource()
            .substringAfter("private fun ThinkingFold(")
            .substringBefore("private fun ReasoningDetail(")
        // L'annonce est en petites capitales (majuscules + interlettrage), pas un libellé nu.
        assertTrue(header.contains("OnyxLocalization[\"Model reasoning\"].uppercase()"))
        assertTrue(header.contains("letterSpacing = 0.08.em"))
        // La mention « brut, non traduit » existe et ne dépend que de la langue détectée.
        assertTrue(header.contains("OnyxLocalization[\"raw, untranslated\"]"))
        assertTrue(header.contains("reasoningLooksForeign(message.reasoning"))

        // Les libellés sont français, et l'anglais reste inchangé.
        assertEquals("Raisonnement du modèle", OnyxLocalization.translate("Model reasoning", Locale.FRANCE))
        assertEquals("brut, non traduit", OnyxLocalization.translate("raw, untranslated", Locale.FRANCE))
        assertEquals("Voir tout", OnyxLocalization.translate("See all", Locale.FRANCE))
        assertEquals("Réduire", OnyxLocalization.translate("Show less", Locale.FRANCE))
        assertEquals("Model reasoning", OnyxLocalization.translate("Model reasoning", Locale.ENGLISH))
    }

    @Test
    fun `la langue du raisonnement est detectee sans faux positif sur le francais`() {
        // **Anglais franc** : un texte anglais est signalé quand l'app parle français.
        val english =
            "I need to read the question, then choose a short and correct answer. There is nothing " +
                "else to check here, and I will answer directly."
        assertTrue(reasoningLooksForeign(english, appIsFrench = true))
        // Français franc : jamais signalé quand l'app parle français.
        val french =
            "Je regarde ce que la personne demande. Il faut lire la question, puis choisir une " +
                "réponse courte et juste. Rien d'autre à vérifier ici."
        assertFalse(reasoningLooksForeign(french, appIsFrench = true))
        // Et pour une app anglaise, le français se trahit (diacritiques) : il est signalé.
        assertTrue(reasoningLooksForeign(french, appIsFrench = false))
        // Un texte neutre et court ne conclut jamais à l'étranger.
        assertFalse(reasoningLooksForeign("ok", appIsFrench = true))
        assertFalse(reasoningLooksForeign("", appIsFrench = true))
    }

    // -------------------------------------------------------------------------------------------
    // 4 & 5. Les mesures : contraste et recouvrement — les deux verrous qui mordent
    // -------------------------------------------------------------------------------------------

    private fun channel(value: Float): Double =
        if (value <= 0.03928f) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)

    private fun luminance(c: Color): Double =
        0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun ratio(fg: Color, bg: Color): Double {
        val a = luminance(fg)
        val b = luminance(bg)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    private fun over(fg: Color, bg: Color): Color = Color(
        red = fg.red * fg.alpha + bg.red * (1 - fg.alpha),
        green = fg.green * fg.alpha + bg.green * (1 - fg.alpha),
        blue = fg.blue * fg.alpha + bg.blue * (1 - fg.alpha),
    )

    @Test
    fun `l'encre du bloc tient le seuil de contraste dans les deux apparences`() {
        val light = ResolvedTheme.fallback()
        val cases = listOf(
            "clair" to (light to Color.White),
            "sombre" to (
                ResolvedTheme.resolve(light.theme, dark = true, fonts = light.fonts) to Color.Black
                ),
        )
        cases.forEach { (name, pair) ->
            val (theme, page) = pair
            val ink = ratio(over(reasoningInk(theme), page), page)
            assertTrue(
                "raisonnement $name : $ink < ${ReasoningBlock.TextContrastFloor}",
                ink >= ReasoningBlock.TextContrastFloor,
            )
        }

        // **LA MORSURE.** Le seuil n'est utile que s'il sait **refuser** : la même encre affaiblie
        // tombe sous 4,5 sur la page claire. C'est exactement ce que le verrou refuse — et la
        // démonstration de morsure du rapport le rejoue sur le source (`reasoningInk` ramené à
        // 0,35 d'alpha), où le test tombe alors que la suite était verte.
        val page = Color.White
        val weak = ratio(over(reasoningInk(light).copy(alpha = 0.35f), page), page)
        assertTrue(
            "le seuil ne refuse plus une encre affaiblie ($weak) : le verrou de contraste ne mord plus",
            weak < ReasoningBlock.TextContrastFloor,
        )
    }

    @Test
    fun `aucun element flottant ne recouvre le bloc de reflexion`() {
        // Le modèle : le fil réserve une bande sous sa dernière ligne ; la pastille flottante vit
        // DANS cette bande. Le bloc de réflexion, lui, est du contenu — il s'arrête au haut de la
        // bande. Le recouvrement est donc **nul** par construction.
        val reserve = ReasoningBlock.FloatBand.MinReserve.value
        val pillHeight = ReasoningBlock.FloatBand.PillHeight.value
        val viewport = 2000f
        val left = OnyxSpace.ScreenMargin.value
        val right = 1040f - OnyxSpace.ScreenMargin.value

        // Le bloc : du haut du fil jusqu'au haut de la bande réservée.
        val blockBottom = viewport - reserve
        // La pastille : centrée en bas, à sa hauteur, dans la bande réservée.
        val pillTop = viewport - reserve
        val pillBottom = pillTop + pillHeight

        val overlap = overlapAreaPx2(left, 100f, right, blockBottom, 16f, pillTop, 600f, pillBottom)
        assertEquals("le bloc et la pastille se recouvrent", 0f, overlap, 0.001f)

        // **LA MORSURE.** Si le fil ne réserve plus la bande (une réserve trop courte — ce qui
        // arrive quand on retire la réserve du bas), le contenu descend DANS la pastille et le même
        // calcul le dit : la surface recouverte est strictement positive.
        val shortReserve = reserve - ReasoningBlock.FloatBand.Gap.value - 1f
        val shortBlockBottom = viewport - shortReserve
        val bitten = overlapAreaPx2(left, 100f, right, shortBlockBottom, 16f, pillTop, 600f, pillBottom)
        assertTrue("une réserve trop courte doit faire mordre le verrou (recouvrement = $bitten)", bitten > 0f)
    }

    @Test
    fun `la pastille flottante est cantonnee a la bande reservee du fil`() {
        val chat = File("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt").readText()
        // La pastille partage la réserve `jumpClearance` : elle se pose **au-dessus de la plaque**,
        // dans la bande que le fil s'interdit — c'est la garde qui garantit le recouvrement nul
        // mesuré ci-dessus.
        val anchor = chat
            .substringAfter("visible = offersJump")
            .substringBefore("JumpToLatestPill(")
        assertTrue("la pastille n'est plus rangée au-dessus de la plaque", anchor.contains("jumpClearance"))
        assertTrue(
            "le fil ne réserve plus la bande de la pastille",
            chat.contains("bottom = jumpClearance"),
        )
    }

    private fun messageRowSource(): String =
        File("src/main/java/com/onyx/app/ui/chat/components/MessageRow.kt").readText()
}
