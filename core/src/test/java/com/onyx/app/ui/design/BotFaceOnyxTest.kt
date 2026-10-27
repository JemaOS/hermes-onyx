package com.onyx.app.ui.design

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vecteurs repris de: aucun vecteur n'est calculé avec l'implémentation Kotlin. */
class BotFaceOnyxTest {
    @Test
    fun `le profil default est la bulle principale du theme`() {
        val expected = "1:bubble:round:none:none:13:1:-3"

        assertEquals(expected, BotFace.main.code())
        assertEquals(BotFace.main, BotFace.forName("default"))
        assertEquals(BotFace.main, BotFace.parse(expected))
    }

    @Test
    fun `les noms utilisent les vecteurs FNV-1a 64 bits `() {
        val vectors = mapOf(
            "kilo" to "1:diamond:happy:smile:freckles:5:2:-3",
            "scout" to "1:burst:sleepy:oh:freckles:3:2:2",
            "writer" to "1:round:happy:oh:blush:3:2:2",
            "fixit" to "1:egg:sparkle:none:antenna:7:3:-6",
            "équipe" to "1:egg:mismatched:grin:spark:3:1:1",
            "🤖" to "1:hex:round:smile:antenna:7:3:2",
        )

        vectors.forEach { (name, expected) ->
            assertEquals("vecteur  pour $name", expected, BotFace.forName(name).code())
        }
    }

    @Test
    fun `le code serveur relit toutes les parties et normalise leurs bornes`() {
        val picked = BotFace(
            shape = FaceShape.EGG,
            eyes = FaceEyes.SPARKLE,
            mouth = FaceMouth.GRIN,
            mark = FaceMark.SPARK,
            colour = 5,
            glance = 2,
            tilt = -4,
        )
        assertEquals("1:egg:sparkle:grin:spark:5:2:-4", picked.code())
        assertEquals(picked, BotFace.parse(picked.code()))

        assertEquals(
            "1:hex:round:none:none:15:3:-8",
            BotFace.parse("1:hex:round:none:none:-1:-5:-20")?.code(),
        )
        assertEquals(
            "1:hex:round:none:none:0:0:8",
            BotFace.parse("1:hex:round:none:none:16:8:20")?.code(),
        )
        assertNull(BotFace.parse("2:egg:round:none:none:0:1:0"))
        assertNull(BotFace.parse("1:cloud:round:none:none:0:1:0"))
        assertNull(BotFace.parse("1:circle:round:none:none:0:1:0"))
        assertNull(BotFace.parse("nonsense"))
    }

    @Test
    fun `les anciens codes restent lisibles et se reecrivent au format serveur`() {
        val old = BotFace.parse("circle-wide-open-2")
        assertEquals(FaceShape.CIRCLE, old?.shape)
        assertEquals(FaceEyes.WIDE, old?.eyes)
        assertEquals(FaceMouth.OPEN, old?.mouth)
        assertEquals("1:round:round:oh:none:2:0:0", old?.code())

        val oldWithMark = BotFace.parse("squircle-line-line-blush-3")
        assertEquals(FaceMark.BLUSH, oldWithMark?.mark)
        assertEquals("1:square:sleepy:none:blush:3:0:0", oldWithMark?.code())
    }

    @Test
    fun `le roster attribue les creatures  sans doublon ni ordre parasite`() {
        val names = listOf("default", "scout", "writer", "fixit", "coder", "research", "work", "personal", "home", "garden")
        val faces = BotFace.faces(names)
        val features = faces.values.map { listOf(it.shape, it.eyes, it.mouth, it.mark) }

        assertEquals(names.size, faces.size)
        assertEquals(names.size, features.toSet().size)
        assertEquals(names.size, faces.values.map { it.colour }.toSet().size)
        assertEquals(faces, BotFace.faces(names.reversed()))
        assertEquals(BotFace.main, faces["default"])
        assertEquals(BotFace.forName("scout"), BotFace.faces(listOf("scout"))["scout"])
        assertEquals(8, names.sorted().take(8).map { faces.getValue(it).shape }.toSet().size)
        assertTrue(faces.filterKeys { it != "default" }.values.none {
            it.shape == FaceShape.BUBBLE || it.colour == 0 || it.colour >= BotFace.seededColors
        })

        val fourteen = BotFace.faces(names + listOf("eleven", "twelve", "thirteen", "fourteen"))
        assertEquals(14, fourteen.values.map { listOf(it.shape, it.eyes, it.mouth, it.mark) }.toSet().size)
    }

    @Test
    fun `les visages choisis sont reserves et la bulle reste exclusive a default`() {
        val names = listOf("default", "scout", "writer", "fixit", "coder")
        val given = BotFace.faces(names)
        val writers = given.getValue("writer")
        val faces = BotFace.faces(names, picked = mapOf("scout" to writers))

        assertEquals(writers, faces["scout"])
        assertFalse(faces.getValue("writer").sameFeaturesForTest(writers))
        assertEquals(BotFace.main, faces["default"])

        val bubble = BotFace.parse("1:bubble:round:none:none:0:1:-3")!!
        assertFalse(BotFace.faces(listOf("default", "scout"), mapOf("scout" to bubble))["scout"]?.shape == FaceShape.BUBBLE)

        val ghost = BotFace(FaceShape.GHOST, FaceEyes.HAPPY, FaceMouth.SMILE, 3, FaceMark.BLUSH, 0, 2)
        assertEquals(ghost, BotFace.faces(listOf("default", "scout"), mapOf("default" to ghost))["default"])

        val blue = BotFace.main.copy(colour = BotFace.codeBlue)
        val aroundBlue = BotFace.faces(listOf("default", "scout", "writer", "fixit"), mapOf("default" to blue))
        assertEquals(blue, aroundBlue["default"])
        assertTrue(aroundBlue.filterKeys { it != "default" }.values.none { it.colour == 0 || it.colour >= 11 })
    }

    private fun BotFace.sameFeaturesForTest(other: BotFace): Boolean =
        shape == other.shape && eyes == other.eyes && mouth == other.mouth && mark == other.mark

    @Test
    fun `la palette et les degrades reprennent chaque valeur `() {
        val expected = listOf(
            Color(0.66f, 0.83f, 1.00f) to Color(0.30f, 0.55f, 0.96f),
            Color(1.00f, 0.89f, 0.54f) to Color(0.96f, 0.68f, 0.22f),
            Color(1.00f, 0.70f, 0.85f) to Color(0.91f, 0.34f, 0.62f),
            Color(0.68f, 0.73f, 1.00f) to Color(0.36f, 0.42f, 0.94f),
            Color(0.58f, 0.94f, 0.87f) to Color(0.13f, 0.69f, 0.60f),
            Color(0.82f, 0.74f, 1.00f) to Color(0.54f, 0.40f, 0.94f),
            Color(0.78f, 0.96f, 0.66f) to Color(0.34f, 0.76f, 0.40f),
            Color(1.00f, 0.78f, 0.57f) to Color(0.94f, 0.50f, 0.22f),
            Color(0.66f, 0.91f, 1.00f) to Color(0.18f, 0.68f, 0.90f),
            Color(1.00f, 0.73f, 0.66f) to Color(0.93f, 0.42f, 0.37f),
            Color(0.92f, 0.97f, 0.63f) to Color(0.70f, 0.80f, 0.17f),
            Color(1.00f, 1.00f, 1.00f) to Color(0.86f, 0.86f, 0.86f),
            Color(0.34f, 0.34f, 0.34f) to Color(0.07f, 0.07f, 0.07f),
        )

        expected.forEachIndexed { index, pair -> assertEquals("couleur $index", pair, BotFace.pair(index)) }
        assertEquals(Color(1.00f, 0.87f, 0.93f) to Color(0.96f, 0.64f, 0.80f), BotFace.pair(15))
        assertEquals(listOf(0, 1, 2, 15, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12), BotFace.pickable)
    }

    @Test
    fun `les couleurs theme et Code suivent les regles `() {
        val theme = ResolvedTheme.fallback()
        val themed = BotFace.gradient(BotFace.themed, theme)
        assertEquals(theme.userBubbleColor, themed.second)
        assertColor(theme.userBubbleColor.lighten(0.14f), themed.first)

        val code = BotFace.gradient(BotFace.codeBlue, theme)
        assertEquals(Color(0.796f, 0.941f, 1.0f), code.second)
        assertColor(code.second.lighten(0.14f), code.first)
    }

    @Test
    fun `le picker propose exactement les parties et couleurs `() {
        assertEquals(listOf(FaceShape.BUBBLE) + FaceShape.given, FaceShape.offered(isDefault = true))
        assertEquals(FaceShape.given, FaceShape.offered(isDefault = false))
        assertFalse(FaceShape.offered(isDefault = false).contains(FaceShape.BUBBLE))
        assertEquals(listOf(BotFace.themed, BotFace.codeBlue) + BotFace.pickable, BotFace.offeredColors(isDefault = true))
        assertEquals(BotFace.pickable, BotFace.offeredColors(isDefault = false))
        assertFalse(BotFace.offeredColors(isDefault = false).contains(BotFace.themed))
        assertFalse(BotFace.offeredColors(isDefault = false).contains(BotFace.codeBlue))
    }

    @Test
    fun `surprise me garde regard et inclinaison sans prendre la bulle de Onyx`() {
        val current = BotFace.main.copy(glance = 3, tilt = -5)
        val changed = BotFace.random(keeping = current, random = kotlin.random.Random(20261024))

        assertTrue(changed.shape in FaceShape.given)
        assertTrue(changed.eyes in FaceEyes.ordered)
        assertTrue(changed.mouth in FaceMouth.ordered)
        assertTrue(changed.mark in FaceMark.ordered)
        assertTrue(changed.colour in BotFace.pickable)
        assertEquals(current.glance, changed.glance)
        assertEquals(current.tilt, changed.tilt)
    }

    // MARK: - Réglages → Apparence: « Bot faces », « Bot colors », « Chat text size »

    @Test
    fun `bot faces eteint donne l initiale en capitale du premier caractere utile`() {
        // `BotAvatar.initial(of:)`: une lettre ou un chiffre, sinon le premier
        // caractère non blanc et non ponctuation (« @scout » donne S).
        assertEquals("S", BotFace.initialFor("@scout"))
        assertEquals("É", BotFace.initialFor("équipe"))
        assertEquals("1", BotFace.initialFor("1st bot"))
        assertEquals("O", BotFace.initialFor("Onyx"))
        assertEquals("", BotFace.initialFor(""))
        assertEquals("", BotFace.initialFor("  ..."))
    }

    @Test
    fun `les couleurs d un chat suivent le visage du bot`() {
        // `BotFace.chatColors`: rien pour la couleur du thème, le bleu Code tel quel, le blanc et
        // le noir pour les bulles seules, sinon le profond de la couleur en accent et en bulle.
        assertNull(BotFace(shape = FaceShape.BUBBLE, eyes = FaceEyes.ROUND, mouth = FaceMouth.NONE,
            mark = FaceMark.NONE, colour = BotFace.themed, glance = 1, tilt = 0).chatColors())

        val codeBlue = BotFace(shape = FaceShape.ROUND, eyes = FaceEyes.ROUND, mouth = FaceMouth.SMILE,
            mark = FaceMark.NONE, colour = BotFace.codeBlue, glance = 0, tilt = 0).chatColors()
        assertEquals(0.796, codeBlue!!.accent!!.red, 0.0001)
        assertEquals(codeBlue.accent, codeBlue.bubble)

        val white = BotFace(shape = FaceShape.ROUND, eyes = FaceEyes.ROUND, mouth = FaceMouth.SMILE,
            mark = FaceMark.NONE, colour = BotFace.white, glance = 0, tilt = 0).chatColors()
        assertNull("le blanc ne colore que les bulles", white!!.accent)
        assertTrue(white.bubble != null)

        val black = BotFace(shape = FaceShape.ROUND, eyes = FaceEyes.ROUND, mouth = FaceMouth.SMILE,
            mark = FaceMark.NONE, colour = BotFace.white + 1, glance = 0, tilt = 0).chatColors()
        assertNull(black!!.accent)

        val blue = BotFace(shape = FaceShape.ROUND, eyes = FaceEyes.ROUND, mouth = FaceMouth.SMILE,
            mark = FaceMark.NONE, colour = 0, glance = 0, tilt = 0).chatColors()
        assertEquals(blue!!.accent, blue.bubble)
        // Le profond de la palette bleue (les couleurs Compose sont quantifiées sur 8 bits).
        assertEquals("le profond de la couleur bleue", 0.30, blue.accent!!.red, 0.01)
        assertEquals(0.55, blue.accent.green, 0.01)
        assertEquals(0.96, blue.accent.blue, 0.01)
    }

    @Test
    fun `le texte du chat a les cinq pas et les libelles `() {
        assertEquals(listOf(-2, -1, 0, 1, 2), ResolvedTheme.chatTextSizes)
        assertEquals(
            listOf("Smallest", "Smaller", "Same as the phone", "Larger", "Largest"),
            ResolvedTheme.chatTextSizes.map(ResolvedTheme::chatTextLabel),
        )
        assertEquals(1f, ResolvedTheme.chatTextScale(0), 0.0001f)
        assertTrue(ResolvedTheme.chatTextScale(-2) < 1f)
        assertTrue(ResolvedTheme.chatTextScale(2) > 1f)
    }

    @Test
    fun `l echelle du texte change les tailles du fil sans toucher aux couleurs`() {
        val base = ResolvedTheme.resolve(OnyxTheme.STANDARD, dark = false, fonts = OnyxFonts.System)
        val larger = base.withFontScale(ResolvedTheme.chatTextScale(1))
        assertTrue(larger.messageFont.fontSize.value > base.messageFont.fontSize.value)
        assertTrue(larger.userFont.fontSize.value > base.userFont.fontSize.value)
        assertTrue(larger.body.fontSize.value > base.body.fontSize.value)
        assertEquals(base.accent, larger.accent)
        assertEquals(base.userBubbleColor, larger.userBubbleColor)
        // Les couleurs d'un bot remplacent l'accent et la bulle, sans toucher à la taille.
        val botBlue = BotFace(shape = FaceShape.ROUND, eyes = FaceEyes.ROUND, mouth = FaceMouth.SMILE,
            mark = FaceMark.NONE, colour = 0, glance = 0, tilt = 0).chatColors()
        val tinted = base.withColors(botBlue)
        assertEquals(botBlue!!.accent!!.color, tinted.accent)
        assertEquals(botBlue.bubble!!.color, tinted.userBubbleColor)
        assertEquals(base.messageFont.fontSize.value, tinted.messageFont.fontSize.value, 0.0001f)
    }

    private fun assertColor(expected: Color, actual: Color) {
        assertEquals(expected.red, actual.red, 0.00001f)
        assertEquals(expected.green, actual.green, 0.00001f)
        assertEquals(expected.blue, actual.blue, 0.00001f)
        assertEquals(expected.alpha, actual.alpha, 0.00001f)
    }
}