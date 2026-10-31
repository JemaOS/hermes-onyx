package com.onyx.app.ui.chat

import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.design.PlaqueGeometry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Les deux moitiés de la règle de suivi du fil, verrouillées par des tests qui mordent.**
 *
 * La règle est **double et les deux moitiés comptent** :
 *
 *  1. le contenu neuf — l'attente, le début de réflexion, le début de réponse, le premier jeton —
 *     ramène la vue **en bas** quand la personne n'a pas remonté, et le message envoyé y est
 *     **visible** ; c'est [premiere_moitie] ;
 *  2. si la personne **remonte lire** pendant la réponse, le suivi se **détache** et ne la ramène
 *     plus de force (`when I scroll up slowly, it forces me to the last message`) ; c'est
 *     [deuxieme_moitie].
 *
 * Le défaut mesuré du lot appartient à la première moitié et n'était pas dans le suivi lui-même :
 * le fil **était** collé au bas, mais le rembourrage posé sous sa dernière ligne comptait le
 * **clavier deux fois** (la fenêtre est déjà réduite au-dessus de lui). Clavier ouvert, il restait
 * `1 517 − 1 449 = 68 px` (26 dp) pour le contenu : moins d'une rangée, écrasé sous la barre du
 * haut — l'écran du propriétaire, **vide**, sans son message ni la réponse.
 *
 * Les chiffres de ce fichier sont ceux **relevés à l'appareil** (densité 2,625 — 420 dpi), pas des
 * estimations : `fil = 1517 px`, rembourrage `1449 px` avant / `630 px` après.
 */
class SuiviEtatsMorsureTest {

    /** L'appareil de mesure : 1080×2400, densité 420 = 2,625. */
    private val densityPxPerDp = 420f / 160f

    private fun px(dp: Int): Int = kotlin.math.round(dp * densityPxPerDp).toInt()

    /** La hauteur du fil, mesurée au clavier ouvert (`OnyxScrollFollow … fil=1517px`). */
    private val filPx = 1517

    /** La marge d'ancrage (une rangée), en pixels. */
    private val rangeePx = px(ScrollFollow.AnchorMarginDp)

    /** Le rembourrage du bas, mesuré de part et d'autre du correctif (réserve + voie + écart). */
    private val rembourrageAvantPx = px(484 + PlaqueGeometry.FloatLane.value.toInt() + OnyxSpace.BlockGap.value.toInt())
    private val rembourrageApresPx = px(172 + PlaqueGeometry.FloatLane.value.toInt() + OnyxSpace.BlockGap.value.toInt())

    private val chatScreen = File("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt").readText()
    private val mainActivity = File("src/main/java/com/onyx/app/MainActivity.kt").readText()

    private val composerStacking: String = run {
        val candidats = listOf(
            File("../core/src/main/java/com/onyx/app/ui/design/ComposerStacking.kt"),
            File("core/src/main/java/com/onyx/app/ui/design/ComposerStacking.kt"),
        )
        candidats.firstOrNull { it.isFile }?.readText()
            ?: error("ComposerStacking.kt introuvable depuis ${File(".").absolutePath}")
    }

    // -------------------------------------------------------------------------------------------
    // 1. LE CONTENU NEUF EST VISIBLE — et une transition d'état ne le laisse pas hors de la vue.
    // -------------------------------------------------------------------------------------------

    @Test
    fun `premiere_moitie`() {
        // (a) La geometrie du defaut : la reserve du bas doit laisser au moins une rangee.
        assertFalse(
            "avant le correctif, la reserve ne laissait que $rembourrageAvantPx px " +
                "pour ${filPx}px de fil : moins d'une rangee — le contenu neuf etait invisible. " +
                "Si ce test passe, c'est que le clavier est de nouveau compte deux fois.",
            ScrollFollow.bottomPaddingLeavesRoomForNewContent(
                rembourrageAvantPx, viewportPx = filPx, marginPx = rangeePx,
            ),
        )
        assertTrue(
            "apres le correctif, la reserve laisse place au contenu neuf",
            ScrollFollow.bottomPaddingLeavesRoomForNewContent(
                rembourrageApresPx, viewportPx = filPx, marginPx = rangeePx,
            ),
        )
        // La marge exigee est bien une rangee de la maison (56 dp), pas un chiffre invente.
        assertEquals(56, ScrollFollow.AnchorMarginDp)
        assertEquals(147, rangeePx)

        // (b) Le clavier ne se compte qu'une fois : la reserve lit le drapeau de la racine.
        assertTrue(
            "la reserve du fil doit demander si la racine a deja paye le clavier",
            composerStacking.contains("LocalOnyxImeTakenByRoot.current"),
        )
        assertTrue(
            "et elle ne doit compter le clavier QUE si la racine ne l'a pas paye",
            composerStacking.contains("val imePx = if (imeTakenByRoot) 0 else imeRawPx"),
        )
        assertTrue(
            "la racine doit poser le drapeau au meme endroit que `imePadding`",
            mainActivity.contains("LocalOnyxImeTakenByRoot provides true"),
        )

        // (c) Chaque transition d'etat du tour ramene la vue : l'etape est une cle de l'effet.
        assertTrue(
            "l'etape du tour doit rejouer le collage au bas (attente, reflexion, reponse, 1er jeton)",
            chatScreen.contains("lastIndex, lastLength, state.isStreaming, state.queued.size, state.live?.step, following,"),
        )
        // Le collage au bas se borne a la fin du rembourrage : la derniere ligne reste au-dessus.
        assertTrue(
            "le collage au bas passe par le decalage de fin de liste",
            chatScreen.contains("listState.scrollToItem(latestItemIndex, BottomScrollOffset)"),
        )
    }

    // -------------------------------------------------------------------------------------------
    // 2. REMONTER LIRE N'EST JAMAIS DEFAIT — meme a une transition d'etat.
    // -------------------------------------------------------------------------------------------

    @Test
    fun `deuxieme_moitie`() {
        // Un doigt pose detache le suivi tout de suite, et le geste ne le re-attache pas.
        assertFalse("un geste detache le suivi", ScrollFollow.afterTouch())
        assertFalse(
            "pendant le geste, le suivi ne se re-attache pas meme si le bas est a l'ecran",
            ScrollFollow.atRest(scrolling = true, atAnchor = true, following = false),
        )
        assertTrue(
            "revenir au bas et lacher re-attache",
            ScrollFollow.atRest(scrolling = false, atAnchor = true, following = false),
        )
        // Une transition d'etat ne re-attache JAMAIS : c'est le premier jeton recu apres avoir
        // remonte lire qui ramenait la vue au bas, et c'est ce que cette regle interdit.
        assertFalse(
            "une transition d'etat ne ramene pas quelqu'un qui a remonte",
            ScrollFollow.followsNewContent(following = false),
        )
        assertTrue(
            "mais elle suit le contenu neuf quand on est reste au bas",
            ScrollFollow.followsNewContent(following = true),
        )

        // Et l'ecran ne colle au bas que sous cette condition — nommee, pas implicite.
        assertTrue(
            "le collage au bas est conditionne a la decision de suivi",
            chatScreen.contains("if (ScrollFollow.followsNewContent(following) && state.messages.isNotEmpty())"),
        )
        assertTrue(
            "le suivi se detache au premier doigt, sans attendre la position",
            chatScreen.contains("is DragInteraction.Start") &&
                chatScreen.contains("following = ScrollFollow.afterTouch()"),
        )
        assertTrue(
            "un geste en cours reprend la main sur la marge",
            chatScreen.contains("snapshotFlow { listState.isScrollInProgress to withinAnchor }"),
        )
    }
}
