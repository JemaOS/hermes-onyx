package com.onyx.app.ui.sidebar

import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La géométrie des cartes du volet, position *Bots*.
 *
 * La capture du propriétaire montrait des cartes aux **hauteurs inégales** : `Onyx` n'a pas de
 * description et sa carte était courte, `Scout` en a une sur deux lignes et la sienne était haute —
 * la rangée se réglait donc sur sa carte la plus haute, et la grille se cassait. Le point d'état, le
 * visage et l'âge suivaient la même dérive, puisque tout était empilé sans réserve de place.
 *
 * Ce que le lot tient, et que ce fichier mesure :
 *
 * - **la hauteur d'une carte ne dépend pas de son contenu** : la description réserve **deux** lignes,
 *   avec ou sans texte, et le calcul ne prend que les métriques du thème ;
 * - le **visage** a une seule taille, le **point** une seule règle (coin haut-droit du visage), l'**âge**
 *   sa propre ligne, aligné à droite ;
 * - les écarts (colonnes, rangées) sortent de l'échelle nommée [BotsGrid], pas d'une valeur écrite
 *   sur place ;
 * - la description se coupe **au mot**, jamais au caractère.
 */
class BotsGridGeometryTest {

    private val source = File("src/main/java/com/onyx/app/ui/sidebar/ChatSidebarBots.kt").readText()
    private val card = source.substringAfter("private fun BotCard(").substringBefore("/**\n * La description d'un bot")
    private val desc = source.substringAfter("/**\n * La description d'un bot").substringBefore("private fun BotStatusDot")

    // MARK: - La hauteur, la même pour toutes

    @Test
    fun `la hauteur d une carte ne depend pas de ce qu elle contient`() {
        val pad = 12.dp
        val gap = 8.dp
        val face = 38.dp
        val name = 18.dp
        val caption = 14.4.dp
        val age = 13.2.dp
        // Deux cartes voisines, l'une avec description, l'autre sans : la fonction ne prend même pas
        // le texte — c'est ce qui rend les deux hauteurs rigoureusement identiques.
        val withText = botCardHeight(pad, gap, face, name, caption, age)
        val withoutText = botCardHeight(pad, gap, face, name, caption, age)
        assertEquals("une carte sans description garde la hauteur d'une carte qui en a une", withText, withoutText)
        // Et elle réserve bien **deux** lignes de légende : une ligne de plus = une ligne de hauteur.
        val two = botCardHeight(pad, gap, face, name, caption, age, lines = 2)
        val one = botCardHeight(pad, gap, face, name, caption, age, lines = 1)
        assertEquals(
            "deux lignes réservées valent une ligne de plus qu'une seule",
            caption.value,
            (two - one).value,
            0.001f,
        )
    }

    @Test
    fun `la carte reserve deux lignes de description avec ou sans texte`() {
        assertEquals(2, BotsGrid.DescriptionLines)
        // La description est **toujours** demandée à la carte : elle n'est plus sautée quand le bot
        // n'en a pas, sinon la carte raccourcit et la grille se casse.
        assertTrue(card.contains("BotDescription("))
        assertTrue(card.contains("text = bot.description,"))
        assertTrue(card.contains("lines = BotsGrid.DescriptionLines,"))
        // Et la place de la ligne d'âge, elle aussi, est toujours là.
        assertTrue(card.contains("horizontalArrangement = Arrangement.End,"))
        assertFalse(
            "la description ne doit plus être conditionnelle",
            card.contains("if (caption != null)"),
        )
    }

    @Test
    fun `la carte porte sa hauteur et ne s etire plus sur la rangee`() {
        assertTrue(card.contains(".height(cardHeight)"))
        // La rangée ne s'aligne plus sur sa carte la plus haute : la carte a sa hauteur propre.
        val row = source.substringAfter("items(bots.chunked(columns)").substringBefore("if (rooms.isNotEmpty()")
        assertFalse("plus de fillMaxHeight : la carte ne suit plus la rangée", row.contains("fillMaxHeight()"))
        assertTrue(row.contains("padding(bottom = BotsGrid.GapDp.dp)"))
        assertTrue(row.contains("Arrangement.spacedBy(BotsGrid.GapDp.dp)"))
    }

    // MARK: - Le visage, le point, l'âge : une règle chacun

    @Test
    fun `le visage et le point sont poses par des regles, pas par le hasard du texte`() {
        assertTrue("une seule taille de visage — celle de la planche, 48 dp", source.contains("const val FaceDp = 48"))
        assertTrue(card.contains("size = faceSize"))
        // Le point est au coin **haut-droit du visage** : même règle sur chaque carte.
        assertTrue(card.contains("verticalAlignment = Alignment.Top,"))
        assertTrue(card.contains("BotStatusDot(tone = bot.tone, unread = bot.unread, size = BotsGrid.DotDp.dp)"))
        assertTrue(source.contains("const val DotDp = 8"))
    }

    @Test
    fun `l age est a droite, sur sa propre ligne`() {
        val age = card.substringAfter("// L'âge : à droite").substringBefore("    }\n}")
        assertTrue(age.contains("Modifier.fillMaxWidth().height(ageLine)"))
        assertTrue(age.contains("horizontalArrangement = Arrangement.End,"))
        assertTrue(age.contains("BasicText(text = age"))
    }

    @Test
    fun `les ecarts sortent de l echelle d espacement`() {
        assertEquals("le pas de base", 4, BotsGrid.StepDp)
        assertEquals("deux pas entre deux colonnes et deux rangées", 8, BotsGrid.GapDp)
        assertEquals("trois pas à l'intérieur d'une carte", 12, BotsGrid.CardPadDp)
        assertTrue(card.contains("padding(bottom = BotsGrid.GapDp.dp)") || source.contains("BotsGrid.GapDp.dp"))
    }

    // MARK: - La description, coupée au mot

    @Test
    fun `la description se coupe a la frontiere d un mot, jamais au caractere`() {
        // Tient sur deux lignes de dix caractères : « un deux… », et le mot « trois » n'est pas
        // tranché au milieu.
        assertEquals("un deux…", ellipsizeAtWord("un deux trois quatre", 2) { it.length <= 10 })
        // Le texte qui tient est rendu tel quel, sans « … » ajouté.
        assertEquals("court", ellipsizeAtWord("court", 2) { it.length <= 10 })
        // Rien à dire, rien.
        assertEquals("", ellipsizeAtWord("   ", 2) { true })
        // Un seul mot trop long ne peut pas être coupé au mot : il passe tel quel, et l'ellipse du
        // texte prend le relais — un dernier recours, pas la règle.
        assertEquals("interminable", ellipsizeAtWord("interminable", 2) { it.length <= 4 })
        // Aucune ligne : rien du tout.
        assertEquals("", ellipsizeAtWord("un deux", 0) { true })
    }

    @Test
    fun `la coupure est branchee sur une mesure reelle du texte`() {
        // La variante mesurée existe et passe par le `TextMeasurer` de Compose, sur la largeur
        // réelle de la carte : la coupure au mot n'est pas une découpe à N caractères.
        assertTrue(source.contains("fun TextMeasurer.ellipsizeAtWord("))
        assertTrue(source.contains("constraints = Constraints(maxWidth = widthPx.coerceAtLeast(0))"))
        assertTrue(source.contains(".lineCount <= maxLines"))
        assertTrue(desc.contains("BoxWithConstraints(modifier = frame)"))
    }
}
