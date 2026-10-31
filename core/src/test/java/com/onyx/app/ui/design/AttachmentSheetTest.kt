package com.onyx.app.ui.design

import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La feuille qui monte du bas : sa forme (ancrée en bas, coins arrondis, poignée), ses trois
 * fermetures (appui à côté, glissement, retour matériel), sa ligne type (pastille carrée arrondie
 * teintée, icône, libellé, cible de 48 dp) et son comportement sous « Réduire les animations ».
 *
 * Le défaut corrigé : le « + » ouvrait une **carte flottante au milieu de l'écran** (`OnyxMenu`),
 * quatre lignes à plat, sans titre. Ces assertions portent sur la source parce que c'est la source
 * qui décide de la forme ; la preuve en pixels est la capture de la feuille ouverte.
 */
class AttachmentSheetTest {

    private val sheet = File("src/main/java/com/onyx/app/ui/design/AttachmentSheet.kt").readText()

    // ---- 1. la forme ----------------------------------------------------------------------------

    @Test
    fun `le panneau est ancre en bas, arrondi, avec sa poignee`() {
        assertTrue("ancrée en bas", sheet.contains("Alignment.BottomCenter"))
        assertTrue("des coins arrondis, sur le haut seulement (le panneau descend au bord)", sheet.contains("val SheetShape = HouseSheetShape"))
        assertTrue("la poignée", sheet.contains("SheetHandleWidth = 36.dp"))
        assertTrue("la poignée est plate et arrondie", sheet.contains("SheetHandleHeight = 4.dp"))
        assertTrue("la poignée se dessine", sheet.contains("private fun SheetHandle()"))
        // La feuille est une boîte de dialogue plein écran (pas de carte au milieu) : la marge du
        // bord, et le panneau sous le doigt.
        assertTrue(sheet.contains("DialogProperties(usePlatformDefaultWidth = false)"))
        assertFalse("plus de carte flottante du système", sheet.contains("ModalBottomSheet"))
        assertFalse("aucun habillage Material", sheet.contains("MaterialTheme"))
    }

    @Test
    fun `le titre et le sous titre sont dans la feuille`() {
        assertTrue("un titre", sheet.contains("private fun SheetHeader(title: String, subtitle: String?"))
        // LOT anatomie-des-contenus : le titre n'est plus centré — il suit **le gabarit unique**,
        // qui est celui de la source (`BottomSheet.kt:133-155` : grand titre aligné à gauche).
        assertTrue("le titre suit le gabarit unique", sheet.contains("HouseSheetHeader(title = title, subtitle = subtitle"))
        // Le sous-titre est composé ailleurs (les vrais chiffres du code) : il s'affiche tel quel.
        assertTrue("le sous-titre n'est pas re-traduit", sheet.contains("localize = false"))
    }

    // ---- 2. les trois fermetures ----------------------------------------------------------------

    @Test
    fun `on ferme par appui a cote, par glissement et par le retour`() {
        // Le retour matériel : la Dialog de Compose reçoit le geste du système et rappelle onDismiss.
        assertTrue("la boîte de dialogue écoute le retour", sheet.contains("onDismissRequest = onDismiss"))
        // L'appui hors du panneau.
        assertTrue("le voile referme", sheet.contains(".onyxTappable(onClick = onDismiss)"))
        // Le glissement vers le bas, au-delà de la distance qui vaut un « non ».
        assertTrue("le glissement est suivi", sheet.contains("detectVerticalDragGestures("))
        assertTrue("il décide à la fin du geste", sheet.contains("if (drag.value > dismissDistance) {"))
        assertTrue("et seulement au-delà de la distance", sheet.contains("drag.value + amount"))
        // Le panneau avale l'appui : toucher entre deux lignes ne referme rien.
        assertTrue("le panneau ne transmet pas l'appui au voile", sheet.contains(".onyxTappable(onClick = {})"))
    }

    // ---- 3. la ligne type -----------------------------------------------------------------------

    @Test
    fun `chaque ligne a sa vignette carree arrondie teintee et une cible de 48 dp au moins`() {
        assertEquals("la hauteur d'une ligne", 56.dp, SheetRowHeight)
        assertTrue("la cible dépasse les 48 dp de la norme", SheetRowHeight >= 48.dp)
        assertTrue("la ligne prend sa hauteur", sheet.contains("defaultMinSize(minHeight = SheetRowHeight)"))
        assertTrue("la ligne prend toute la largeur", sheet.contains(".fillMaxWidth()"))
        assertTrue("toute la ligne est touchable", sheet.contains(".onyxTappable(onClick = action.onSelect)"))
        // La vignette est celle de **toutes** les rangées : sa taille et son coin viennent de la
        // table des formes, plus d'un nombre recopié dans la feuille.
        assertEquals("la vignette", OnyxSpace.ThumbnailSize, SheetBadgeSize)
        assertEquals("et c'est la taille de la famille", 44.dp, SheetBadgeSize)
        assertTrue(
            "la vignette est celle de la maison (une seule implémentation)",
            sheet.contains("RowThumbnail("),
        )
        // **La vignette est neutre.** Elle portait `tint = theme.accent` — donc une tuile bleue sur
        // chaque ligne, ce que le propriétaire a dénoncé (« chaque rangée a sa tuile colorée »). Une
        // couleur ne sert plus qu'à un **état** : seule la ligne destructive garde la sienne.
        assertFalse(
            "la vignette ne porte plus une couleur de famille",
            sheet.contains("tint = theme.accent"),
        )
        // LOT anatomie-des-contenus : la vignette est neutre **partout**, y compris sur la ligne
        // qui ne se rattrape pas — le propriétaire a demandé que le danger soit porté par **l'encre
        // du libellé**, jamais par un aplat de couleur (la tuile rouge de « Se déconnecter »).
        assertTrue("la vignette est neutre partout", sheet.contains("neutral = true,"))
        assertTrue("et le danger n'est plus un aplat", !sheet.contains("tint = theme.error else null"))
        assertTrue(
            // Le danger est une **encre mesurée sur le fond de la feuille**, pas le rouge de thème
            // posé tel quel : la même règle que la rangée de `HouseSheet`.
            "le libellé suit — et prend l'alerte sur une ligne qui ne se rattrape pas",
            sheet.contains("contentDangerInk(theme, houseSheetFill(theme))"),
        )
    }

    @Test
    fun `les lignes sont separees par un filet en retrait des deux bords`() {
        // Le filet part du libellé — donc après la vignette — et s'arrête avant le bord droit :
        // une seule source, `OnyxSpace.separatorInset`.
        assertTrue(
            "le filet de la feuille vient de la source unique",
            sheet.contains("private val SheetDividerInset = OnyxSpace.separatorInset(SheetBadgeSize)"),
        )
        assertTrue(
            // Le filet est en retrait des **deux** bords : `endInset` vaut `inset` par défaut, et
            // sans lui le trait s'arrêtait à 68 dp du bord droit (mesuré sur la capture du
            // propriétaire : il finissait 80 px avant le bord du panneau).
            "un filet entre deux lignes, en retrait des deux bords",
            sheet.contains(
                "if (index > 0) OnyxRowDivider(inset = SheetDividerInset, endInset = OnyxSpace.CardPadding)",
            ),
        )
    }

    @Test
    fun `la distance de fermeture se mesure en doigts, pas en pixels d un ecran`() {
        assertEquals(96.dp, SheetDismissDistance)
        assertTrue(sheet.contains("val dismissDistance = with(density) { SheetDismissDistance.toPx() }"))
    }

    // ---- 4. Réduire les animations --------------------------------------------------------------

    @Test
    fun `sous reduire les animations, la feuille est figee`() {
        assertTrue("la feuille lit le drapeau", sheet.contains("val reduceMotion = LocalReduceMotion.current"))
        assertTrue("elle est là d'emblée", sheet.contains("Animatable(if (reduceMotion) 0f else riseDistance)"))
        assertTrue("la montée est instantanée", sheet.contains("if (reduceMotion) rise.snapTo(0f)"))
        // **Deux temps justes, pas un seul** : la montée est la famille [MotionFamily.Open], le
        // retour la famille [MotionFamily.Dismiss]. Une sortie n'est pas une entrée inversée —
        // elle est plus courte et elle accélère — et c'est ce qui fait qu'elle ne retarde rien.
        // Les deux restent des familles de la maison : une courbe et une durée chacune, nommées
        // une seule fois. Avant ce lot, le retour avait sa propre durée écrite à la main (200 ms).
        assertTrue(
            "la montée est la famille des ouvertures",
            sheet.contains("MotionFamily.Open.spec(reduceMotion)"),
        )
        assertTrue(
            "le retour après un glissement avorté est la famille des sorties",
            sheet.contains("MotionFamily.Dismiss.spec(reduceMotion)"),
        )
        assertTrue(
            "et les deux familles sont distinctes (une sortie n'est pas une entrée inversée)",
            MotionFamily.Open != MotionFamily.Dismiss,
        )
    }

    // ---- 5. l'encre est mesurée -----------------------------------------------------------------

    @Test
    fun `les encres de la feuille sont mesurees, jamais choisies a l'oeil`() {
        // L'icône de la rangée prend l'encre **mesurée** de la maison : c'est la vignette
        // `RowThumbnail` qui la porte (une seule implémentation), et l'encre de la pastille de la
        // palette (n° 8) est mesurée de la même façon — jamais choisie à l'œil.
        assertTrue(
            "la vignette de la rangée est celle de la maison, dont l'encre est mesurée",
            sheet.contains("RowThumbnail("),
        )
        assertTrue(
            "l'encre de la pastille est mesurée, jamais choisie à l'œil",
            sheet.contains("if (OnyxContrast.ratio(accent, pastille) >= OnyxContrast.ICON_MIN) return accent"),
        )
        assertTrue(
            // Le sous-titre se mesure sur le fond **réellement peint** par la feuille — pas sur le
            // barreau brut, qui divergeait de la surface une fois celle-ci écartée de la page.
            "le sous-titre",
            sheet.contains("OnyxContrast.dimmed(theme.textColor, houseSheetFill(theme), OnyxContrast.TEXT_MIN)"),
        )
        assertTrue("la teinte reste assez soutenue pour se voir", SheetBadgeTint >= 0.10f)
        assertTrue(
            "l'encre de la vignette est mesurée dans la vignette de la maison",
            File("src/main/java/com/onyx/app/ui/design/RowThumbnail.kt").readText()
                .contains("if (OnyxContrast.ratio(colour, tint) >= OnyxContrast.ICON_MIN) return colour"),
        )
    }
}
