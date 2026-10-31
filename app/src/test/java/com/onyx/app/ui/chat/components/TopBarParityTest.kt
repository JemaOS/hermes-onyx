package com.onyx.app.ui.chat.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TopBarParityTest {
    @Test
    fun `le titre garde les 60 caracteres du modele et une seule ligne`() {
        val long = "x".repeat(61)
        assertEquals(60, chatHeaderTitle(long).length)
        assertEquals("Première question", chatHeaderTitle("Première question\navec une suite"))
        assertEquals("Sans troncature préalable", chatHeaderTitle("Sans troncature préalable"))
    }

    @Test
    fun `la capsule et le titre reprennent les dimensions de la barre référence`() {
        assertEquals(86.dp, TopBarCapsuleWidth)
        assertEquals(30.dp, TopBarActionSlotHeight)
        assertEquals(240.dp, TopBarPrincipalMaxWidth)
    }

    // -- La coupe, jamais au milieu d'un mot ---------------------------------------------------

    /** Les caractères sur lesquels une coupe tombe : ceux qui séparent deux mots. */
    private val boundaries = listOf(' ', '/', '-', '·', ':')

    /**
     * La coupe est **propre** : elle tombe sur une frontière (avant ou après un séparateur), et le
     * texte gardé est un préfixe exact de l'original.
     */
    private fun assertCutAtBoundary(original: String, shown: String) {
        if (shown == original) return
        assertTrue("« $shown » doit finir par une ellipse", shown.endsWith("\u2026"))
        val kept = shown.removeSuffix("\u2026")
        assertTrue("« $kept » doit être un préfixe de « $original »", original.startsWith(kept))
        val before = kept.lastOrNull()
        val after = original.getOrNull(kept.length)
        assertTrue(
            "la coupe de « $shown » tombe dans un mot (avant=« $before », après=« $after »)",
            (before != null && before in boundaries) || (after != null && after in boundaries),
        )
    }

    @Test
    fun `un libelle qui tient est rendu tel quel`() {
        assertEquals("court", headerLine("court") { it.length <= 10 })
        assertEquals("", headerLine("   ") { true })
        // Un texte plus long que le budget mais **déjà** dans le budget de la mesure : rien à couper.
        assertEquals("Vérifier la", headerLine("Vérifier la") { true })
    }

    @Test
    fun `un titre long se coupe au mot, jamais au caractere`() {
        val title = "Vérifier la consommation et le coût d'une conversation ordinaire"
        val shown = headerLine(title) { it.length <= 12 }
        assertEquals("Vérifier la\u2026", shown)
        assertCutAtBoundary(title, shown)
        // Jamais « Vérifier la consom… » : le mot tranché n'apparaît pas.
        assertFalse(shown.contains("conso"))
    }

    @Test
    fun `un nom de modele se coupe a ses separateurs`() {
        val model = "deepseek/deepseek-v4.1-flash"
        val shown = headerLine(model) { it.length <= 22 }
        assertEquals("deepseek/deepseek-\u2026", shown)
        assertCutAtBoundary(model, shown)
        assertFalse("un demi-segment de version ne doit pas rester", shown.contains("v4."))
        // Le modèle entier est gardé quand il tient.
        assertEquals(model, headerLine(model) { it.length <= 40 })
    }

    @Test
    fun `un seul mot trop long n est pas coupe en deux`() {
        // Aucune frontière : on rend le mot entier, et c'est l'ellipse de la vue qui le rognera —
        // il reste alors **un** mot, jamais un demi-mot suivi d'un autre.
        assertEquals("interminable", headerLine("interminable") { it.length <= 4 })
    }

    @Test
    fun `le titre de la barre se coupe aussi au mot dans la limite des 60`() {
        val title = "Vérifier la consommation et le coût d'une conversation ordinaire"
        val shown = chatHeaderTitle(title)
        assertTrue("au plus 60 caractères", shown.length <= 60)
        assertCutAtBoundary(title, shown)
        // Le défaut d'origine, lui, coupait au 60e caractère : « …conversation ordi ».
        assertEquals(60, chatHeaderTitle("x".repeat(61)).length)
    }

    // -- L'espacement, sur l'échelle de la maison ---------------------------------------------

    @Test
    fun `la barre espace ses pieces sur l echelle du projet`() {
        val topBar = listOf(
            File("src/main/java/com/onyx/app/ui/chat/components/OnyxTopBar.kt"),
            File("../src/main/java/com/onyx/app/ui/chat/components/OnyxTopBar.kt"),
        ).first { it.isFile }.readText()

        // Le défaut mesuré : le contrôle de gauche et la boîte du visage se **touchaient** (écart
        // 0 dp), et l'écart du titre valait 6 dp — pas une marche de l'échelle. Les deux sont
        // maintenant un pas nommé, de chaque côté du visage.
        //
        // **Le visage peut désormais être un contrôle** (son appui ouvre l'identité du bot, voir
        // `onOpenFace`) : il est alors posé dans une cible tactile de la maison, et le pas
        // d'échelle l'encadre toujours — il est **autour du contrôle**, de chaque côté, et rien ne
        // le touche. La règle est donc la même, dite sur le contrôle plutôt que sur l'appel nu.
        assertTrue(
            "un pas d'échelle avant et après le visage (ou sa cible)",
            Regex(
                """Spacer\(Modifier\.width\(OnyxSpace\.Hair\)\)[\s\S]{0,1500}""" +
                    """TopBarAvatar\(name = avatarName, faceCode = avatarFaceCode\)[\s\S]{0,1500}""" +
                    """Spacer\(Modifier\.width\(OnyxSpace\.Hair\)\)""",
            ).containsMatchIn(topBar),
        )
        assertTrue(
            "et la cible tactile du visage est celle de la maison (48 dp)",
            topBar.contains(".size(OnyxSpace.TouchTarget)"),
        )
        assertTrue(
            "le visage reste dessiné par le même contrôle, dans les deux branches",
            topBar.contains("TopBarAvatar(name = avatarName, faceCode = avatarFaceCode)"),
        )
        assertFalse("plus de 6 dp hors échelle dans l'en-tête", topBar.contains("Spacer(Modifier.width(6.dp))"))
        // Et la coupe mesurée : un vrai mesureur sur la place réellement restante.
        assertTrue(topBar.contains("rememberTextMeasurer()"))
        // Le titre mesuré est le titre **dessiné** : `headerLine` recevait « New conversation » (qui
        // tient) puis la vue dessinait « Nouvelle conversation » (plus long), que l'ellipse du moteur
        // coupait au caractère — « Nouvelle convers… » à 1,3×, relevé sur 1.0.0+546. La mesure porte
        // donc sur `resolvedTitle`, la traduction résolue avant mesure.
        assertTrue(topBar.contains("if (titleIsPlaceholder) OnyxLocalization[title] else title"))
        assertTrue(topBar.contains("measurer.headerLine(resolvedTitle, titleStyle, budget)"))
        // Le **modèle** ne passe plus par la coupe de libellé : c'est un **identifiant** (une
        // adresse, un chemin, un nom de modèle ne se coupent pas à la fin — ils s'abrègent au
        // milieu). Il est donc dessiné par `IdentifierText`, pas par `headerLine`.
        assertTrue(topBar.contains("IdentifierText("))
        assertFalse(topBar.contains("measurer.headerLine(subtitle, subtitleStyle, budget)"))
        // La mesure ne doit **pas** être contrainte à la place disponible : la mise en page rogne
        // le tracé à la contrainte, `fits` dirait donc oui à tout et rien ne serait jamais coupé.
        assertFalse(
            "la mesure doit être libre (sans Constraints) pour que la largeur veuille dire quelque chose",
            topBar.contains("Constraints(maxWidth = widthPx"),
        )
    }
}
