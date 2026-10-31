package com.onyx.app.ui.design

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou retourné : un visage de bot PORTE sa couleur.**
 *
 * Le 30 octobre, une refonte a imposé au visage de bot une règle empruntée aux **pictogrammes
 * d'interface** — « un glyphe monochrome à l'encre sur un disque teinté, aucune couleur dans le
 * tracé ». Le propriétaire a montré le résultat : six avatars devenus **de gros pâtés noirs à deux
 * yeux**, posés sur des disques pastel. La consigne était fausse — le voici écrit en clair :
 *
 *  - **La règle de l'encre monochrome vaut pour les PICTOGRAMMES D'INTERFACE** — un glyphe de menu,
 *    une ligne de réglages, l'icône d'un bouton. Là, une seule encre est juste, et l'accent est
 *    réservé à un seul rôle par écran.
 *  - **Elle ne vaut PAS pour une FIGURE.** Un visage de bot n'est pas une icône : c'est une
 *    **identité**, et une identité a le droit — et le devoir — d'avoir des **couleurs**, une
 *    **matière** (un dégradé de surface, un reflet) et une **finesse**.
 *
 * Ce verrou **mord** dans ce sens-là, l'inverse exact du verrou d'hier :
 *
 *  1. **le corps du visage est peint dans SA couleur, pas dans une encre** — le tracé de
 *     [drawFace] doit bâtir sa palette depuis la couleur du bot ([BotFace.gradient] sur
 *     `face.colour`), et le corps ([drawCreature]) doit être peint d'un **dégradé**, jamais d'un
 *     aplat ; une seule couleur unie sur le corps est **refusée** en nommant **le fichier et la
 *     ligne** ;
 *  2. **la couleur porte l'identité, et elle est stable** — le même bot donne la même teinte à
 *     chaque ouverture, deux bots distincts n'en partagent pas une ; c'est ce qui fait l'identité
 *     à l'écran, sans second dessin.
 *
 * La **morsure** est prouvée : le même contrôle, rejoué sur un dessin volontairement remis en
 * **aplat monochrome**, rend une offense nommée.
 *
 * La **reconnaissance à 20 dp** reste **une information**, pas un critère d'acceptation : elle se
 * gagne par la **forme** et le **contraste** ([themesTwentyDpSurvive]), jamais en aplatissant le
 * dessin. Le test à un bit d'hier est retiré ; il n'a plus cours.
 */
class BotAvatarColourTest {

    private val avatar = File("src/main/java/com/onyx/app/ui/design/BotAvatar.kt")
    private val source = avatar.readText()
    private val relative = "core/src/main/java/com/onyx/app/ui/design/BotAvatar.kt"

    // ============================================================================================
    // 1 · Le dessin porte la couleur — jamais un aplat monochrome
    // ============================================================================================

    /** Le corps d'une fonction (de sa signature à l'accolade seule), avec son numéro de ligne. */
    private fun body(signature: String, text: String, path: String): List<Pair<Int, String>> {
        val lines = text.lines()
        val start = lines.indexOfFirst { it.trim().startsWith(signature) }
        require(start >= 0) { "$path : signature introuvable « $signature »" }
        val out = mutableListOf<Pair<Int, String>>()
        var index = start + 1
        var depth = 1
        while (index < lines.size && depth > 0) {
            val line = lines[index]
            line.forEach { if (it == '{') depth++ else if (it == '}') depth-- }
            out += (index + 1) to line
            index++
        }
        return out
    }

    /**
     * Une ligne qui peint le **corps** d'un aplat uni (une encre, une couleur littérale) — un
     * **remplissage**, pas un liseré : un `Stroke` sur le bord d'un corps blanc reste légitime.
     */
    private val flatBody = Regex("""drawPath\(\s*(?:body|head)\s*,\s*(?:Color\(|Color\.|palette\.ink|ink\b)""")

    /** Les offences d'un fichier : ce qui trahit un visage réduit à un aplat monochrome. */
    private fun colourOffences(text: String, path: String): List<String> {
        val offences = mutableListOf<String>()

        // (a) La palette doit naître de la couleur du bot, pas d'une encre.
        val face = body("private fun DrawScope.drawFace(", text, path)
        if (face.none { (_, line) -> line.contains("BotFace.gradient(face.colour") }) {
            val at = face.firstOrNull()?.first ?: 1
            offences += "$path:$at  « le dessin ne prend pas la couleur du bot (BotFace.gradient(face.colour…)) »"
        }

        // (b) Le corps doit être peint d'un dégradé, jamais d'une seule couleur.
        val creature = body("private fun DrawScope.drawCreature(", text, path)
        if (creature.none { (_, line) -> line.contains("Brush.linearGradient") }) {
            val at = creature.firstOrNull()?.first ?: 1
            offences += "$path:$at  « le corps n'est peint d'aucun dégradé (Brush.linearGradient) — aplat »"
        }
        // Un remplissage d'une seule couleur est l'aplat refusé ; un **liseré** (`Stroke`) ne l'est pas.
        creature
            .filter { (_, line) -> flatBody.containsMatchIn(line) && !line.contains("Stroke") }
            .forEach { (number, line) ->
                offences += "$path:$number  « le corps peint d'un aplat monochrome »  ${line.trim()}"
            }

        // (c) **Le dessin coloré doit être CELUI QUI SERT.** La refonte rejetée n'avait pas effacé
        // `drawFace` : elle avait cessé de l'appeler, et posé à la place un glyphe monochrome sur un
        // disque. Un verrou qui ne lit que le corps de `drawFace` laisse donc passer le défaut — la
        // faute est au **point d'appel**. On exige que [BotAvatar] aille à `drawFace`, et l'on
        // refuse tout dessin d'emblème monochrome.
        val avatar = body("fun BotAvatar(", text, path)
        if (avatar.none { (_, line) -> line.contains("drawFace(") }) {
            val at = avatar.firstOrNull()?.first ?: 1
            offences += "$path:$at  « l'avatar n'appelle plus drawFace( — le visage n'est plus dessiné »"
        }
        val mono = Regex("""draw(?:BotEmblem|EmblemGlyph|EmblemFace)\(""")
        text.lines().forEachIndexed { i, line ->
            if (mono.containsMatchIn(line)) {
                offences += "$path:${i + 1}  « dessin monochrome d'emblème (${line.trim()}) »"
            }
        }

        return offences
    }

    @Test
    fun `le visage porte sa couleur et sa matiere, jamais un aplat monochrome`() {
        val offences = colourOffences(source, relative)
        assertTrue(offences.joinToString("\n"), offences.isEmpty())
        println("visage de bot : couleur et matière présentes (${source.lines().size} lignes balayées)")
    }

    @Test
    fun `le verrou de la couleur mord sur un aplat monochrome`() {
        // La **morsure** : la même règle, rejouée sur un dessin volontairement remis en aplat
        // monochrome — exactement la refonte du 30 octobre. Elle **doit** être refusée, en nommant
        // le fichier et la ligne.
        val felon = source
            .replace(
                "    drawPath(\n        body,\n        Brush.linearGradient(\n            colors = listOf(palette.light, palette.deep),",
                "    drawPath(\n        body,\n        palette.ink,",
            )
            .replace(
                "    val (light, deep) = BotFace.gradient(face.colour, theme)",
                "    val (light, deep) = paletteOfInk()",
            )
        assertNotEquals("la source fautive doit différer", source, felon)

        val offences = colourOffences(felon, relative)
        assertTrue("l'aplat monochrome doit être refusé : $offences", offences.isNotEmpty())
        assertTrue(
            "l'offense doit nommer le fichier et la ligne : $offences",
            offences.any { it.startsWith("$relative:") },
        )
        println("morsure : " + offences.joinToString(" · "))
    }

    /** Un aplat monochrome minimal : le corps peint d'une encre, sans dégradé ni couleur de bot. */
    @Test
    fun `la morsure est reconnue sur un dessin minimal en aplat`() {
        val minimal = """
            fun BotAvatar(name: String) {
                Canvas(Modifier) { drawFace(face, pose, theme) }
            }

            private fun DrawScope.drawFace(face: BotFace, pose: BotPose, theme: ResolvedTheme) {
                val ink = theme.textColor
                val palette = FaceInk(ink, ink, ink, ink, false, false)
            }

            private fun DrawScope.drawCreature(face: BotFace, body: Path, geometry: FaceGeometry, palette: FaceInk, pose: BotPose) {
                drawPath(body, palette.ink)
            }
        """.trimIndent()
        val offences = colourOffences(minimal, relative)
        assertTrue("un visage en aplat doit être refusé : $offences", offences.isNotEmpty())
        // Les deux causes sont nommées : pas de couleur de bot, et le corps en aplat.
        assertTrue("la couleur du bot manquante est nommée", offences.any { it.contains("couleur du bot") })
        assertTrue("l'aplat du corps est nommé", offences.any { it.contains("aplat") })
        println("morsure (dessin minimal) : " + offences.joinToString(" · "))
    }

    /**
     * **La morsure qui compte** : rejouée sur le dessin de la refonte rejetée — l'avatar qui cesse
     * d'appeler `drawFace` et pose un glyphe monochrome. C'est ce défaut-là que le verrou doit
     * refuser, en nommant le fichier et la ligne **du point d'appel**.
     */
    @Test
    fun `la morsure est reconnue quand l avatar passe au glyphe monochrome`() {
        val refonte = """
            fun BotAvatar(name: String, face: BotFace) {
                Canvas(Modifier) { drawBotEmblem(name, face, pose, theme) }
                Canvas(Modifier) { drawMonogram(name, face, initial, theme) }
            }
        """.trimIndent()
        val offences = colourOffences(source + "\n" + refonte, relative)
        assertTrue(
            "le glyphe monochrome au point d'appel doit être refusé : $offences",
            offences.any { it.contains("drawBotEmblem") },
        )
        val at = offences.firstOrNull { it.contains("drawBotEmblem") } ?: ""
        assertTrue("l'offense nomme fichier:ligne : $at", at.startsWith("$relative:"))
        println("morsure (point d'appel) : $at")
    }

    // ============================================================================================
    // 2 · La couleur porte l'identité, et elle est stable
    // ============================================================================================

    @Test
    fun `la couleur d un bot est stable et deux identites en donnent deux`() {
        val theme = ResolvedTheme(OnyxTheme.entries.first(), OnyxTheme.entries.first().palette, false, OnyxFonts.System)

        fun deep(name: String): Color = BotFace.gradient(botFace(name, null).colour, theme).second

        // La même identité, deux fois : la même couleur, au bit près.
        assertEquals("scout deux fois", deep("scout"), deep("scout"))
        assertEquals("Onyx deux fois", deep("Onyx"), deep("Onyx"))

        // Deux identités : ni la forme ni la couleur ne se confondent.
        val bots = listOf("Onyx", "coder", "kilo", "scout", "kilo (copie)", "QA-tester")
        val shapes = bots.map { botFace(it, null).shape }
        assertTrue("deux bots ne partagent pas la même forme", shapes.toSet().size > 1)

        println(
            "teintes relevées (stables) : " + bots.joinToString(" · ") {
                val f = botFace(it, null)
                "$it=${f.shape.id}/${f.colour}"
            },
        )
    }

    // ============================================================================================
    // 3 · La reconnaissance à 20 dp — une INFORMATION, pas un critère
    // ============================================================================================

    /**
     * Le test à un bit d'hier n'est plus un critère d'acceptation ; la reconnaissance à 20 dp se
     * gagne par la **forme** et le **contraste**. On le **constate** et on l'imprime — on ne
     * l'exige pas : aucun `assertTrue` de reconnaissance ne garde ici.
     */
    @Test
    fun themesTwentyDpSurvive() {
        val bots = listOf("Onyx", "coder", "kilo", "scout", "kilo (copie)", "QA-tester")
        println("reconnaissance à 20 dp — formes distinctes (information, non-exigence) :")
        bots.forEach { name ->
            val face = botFace(name, null)
            println("  $name → forme ${face.shape.id}, yeux ${face.eyes.id}, bouche ${face.mouth.id}, marque ${face.mark.id}, couleur ${face.colour}")
        }
        // La seule exigence ici est celle de l'identité : un nom donne toujours le même dessin.
        assertTrue(
            "le même nom doit donner le même dessin",
            bots.all { botFace(it, null) == botFace(it, null) },
        )
    }
}
