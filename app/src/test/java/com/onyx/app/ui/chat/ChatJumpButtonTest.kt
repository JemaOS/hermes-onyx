package com.onyx.app.ui.chat

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le bouton « aller au dernier message », défaut signalé : *« il est dessiné SOUS la carte du
 * compositeur, à moitié recouvert, illisible et intappable »*.
 *
 * Ce qui est vérifié ici est le **câblage** : où l'écran pose le bouton, quelle cible il lui donne,
 * ce qui le fait paraître et disparaître, et ce que son appui déclenche. La géométrie mesurée
 * (position en pixels, cible à l'arbre d'accessibilité) est dans le rapport du lot — un test de
 * source ne juge pas un rendu, la capture d'émulateur le fait.
 */
class ChatJumpButtonTest {
    private val screen = File("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt").readText()
    private val stacking = File("../core/src/main/java/com/onyx/app/ui/design/ComposerStacking.kt").readText()

    @Test
    fun `le bouton se pose au dessus de la reserve du fil, jamais dedans`() {
        // La marge est **la réserve du fil** (la pile mesurée) plus un dégagement nommé : une seule
        // source de vérité, la même valeur que le rembourrage du bas de la liste.
        assertTrue(
            screen.contains("val jumpClearance = glassBottomInset + ComposerStacking.PillClearanceDp.dp"),
        )
        assertTrue(screen.contains(".padding(start = 16.dp, end = 16.dp, bottom = jumpClearance)"))
        // Et le dégagement est un jeton de l'échelle, pas un nombre écrit dans l'écran.
        assertTrue(
            "le dégagement doit venir de `ComposerStacking`",
            stacking.contains("val PillClearanceDp: Int = OnyxSpace.BlockGap.value.toInt()"),
        )
    }

    @Test
    fun `l encart du systeme n est plus compte deux fois sous le bouton`() {
        // La réserve porte déjà `max(clavier, navigation)`. Un `navigationBarsPadding()` posé en
        // plus sur le bouton le faisait descendre de toute la hauteur de l'encart — mesuré 7,6 dp
        // du bord de la carte sur un appareil en navigation par gestes, et jusqu'à 48 dp **dedans**
        // là où la racine ne consomme pas cet encart.
        val bouton = screen.substringAfter("val jumpClearance").substringBefore("ToastHost")
        assertTrue("la fenêtre du bouton doit être trouvée", bouton.contains("AnimatedVisibility"))
        assertFalse(
            "le bouton ne reprend pas une seconde marge système",
            bouton.contains("navigationBarsPadding()"),
        )
        assertTrue("il est bien au-dessus de la pile, pour le doigt comme pour l'œil", bouton.contains(".zIndex(2f)"))
        assertTrue(bouton.contains(".padding(start = 16.dp, end = 16.dp, bottom = jumpClearance)"))
    }

    @Test
    fun `la cible du bouton fait au moins 44 dp`() {
        // Mesuré à l'arbre d'accessibilité : la pastille faisait 47 px de haut à la densité 2,625,
        // soit **18 dp** — la moitié de la cible du pouce. C'est de là que vient « intappable ».
        assertTrue(
            screen.contains(".sizeIn(minWidth = OnyxSpace.ThumbnailSize, minHeight = OnyxSpace.ThumbnailSize)"),
        )
        val thumbnail = Regex("val ThumbnailSize: Dp = (\\d+)\\.dp")
            .find(File("../core/src/main/java/com/onyx/app/ui/design/Spacing.kt").readText())
        assertTrue("la cible doit être déclarée dans l'échelle", thumbnail != null)
        assertTrue(
            "44 dp est la cible de la maison",
            thumbnail!!.groupValues[1].toInt() >= 44,
        )
    }

    @Test
    fun `le bouton parait d un coup et disparait d un coup`() {
        // Plus de glissement ni de mise à l'échelle : un fondu court, **figé** sous « Réduire les
        // animations » (`ComposerStacking.fadeSpec` : une seule décision de mouvement).
        assertTrue(
            screen.contains("enter = fadeIn(animationSpec = ComposerStacking.fadeSpec(reduceMotion))"),
        )
        assertTrue(
            screen.contains("exit = fadeOut(animationSpec = ComposerStacking.fadeSpec(reduceMotion))"),
        )
        val bouton = screen.substringAfter("private fun JumpToLatestPill")
        assertFalse("plus de glissement d'entrée", bouton.contains("slideInVertically"))
        assertFalse("plus de mise à l'échelle d'entrée", bouton.contains("scaleIn("))
    }

    @Test
    fun `le bouton disparait quand la fin est a l ecran`() {
        assertTrue(
            screen.contains("val offersJump = state.messages.isNotEmpty() && !following && !withinAnchor"),
        )
        assertTrue(
            "et il s'efface devant les messages en attente et la liste des mentions",
            screen.contains("state.queued.isEmpty() && mentionSuggestions.isEmpty()"),
        )
    }

    @Test
    fun `son appui ramene en bas et re attache le suivi`() {
        assertTrue(screen.contains("following = ScrollFollow.jumpToLatest()"))
        assertTrue(screen.contains("listState.scrollToItem(latestItemIndex, BottomScrollOffset)"))
    }

    @Test
    fun `le fil ouvre sur le dernier message, sans saut`() {
        // La liste naît posée sur la fin du fil : plus d'écran qui s'ouvre en haut puis saute en
        // bas au premier passage de l'effet de suivi.
        assertTrue(screen.contains("rememberLazyListState(initialFirstVisibleItemIndex = latestItemIndex)"))
    }

    @Test
    fun `la marge d ancrage est un etat, pas une valeur figee`() {
        // **Le piège qui a coûté une passe entière.** `snapshotFlow` ne suit que les lectures
        // d'état : une valeur calculée dans la composition y restait **figée à la première passe**,
        // le suivi se ré-attachait à chaque relâchement, et la vue retombait au bas — la correction
        // semblait alors sans effet. La trace de `logcat` l'a montré noir sur blanc
        // (« suivi=attaché distance au bas=920px dans la marge=false »).
        assertTrue(screen.contains("val withinAnchor by remember(anchorMarginPx) {"))
        assertTrue(
            screen.contains(
                "derivedStateOf { ScrollFollow.withinAnchor(distanceToBottomPx, anchorMarginPx.toInt()) }",
            ),
        )
        assertTrue(screen.contains("snapshotFlow { listState.isScrollInProgress to withinAnchor }"))
    }
}
