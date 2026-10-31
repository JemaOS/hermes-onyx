package com.onyx.app.ui.chat

import com.onyx.app.ui.design.ComposerStacking
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Rien de la conversation ne passe sous la pile du bas.**
 *
 * Le défaut signalé sur capture : les dernières lignes du fil (« Réflexion · 12,4 s », « …
 * caractères mis en cache ») étaient **recouvertes** par la carte du compositeur. La cause n'était
 * pas la pile — elle était juste — mais la **réserve** que le fil s'accordait : `compositeur +
 * onglets + 8`, alors que la pile occupe `compositeur + onglets + encart du système + 8`. L'écart
 * vaut la zone de navigation : **24 dp** en navigation par gestes, **48 dp** à trois boutons, et la
 * zone du clavier quand il est ouvert. Exactement la hauteur dont le fil manquait.
 *
 * Ce test compare donc **deux valeurs** — la réserve annoncée et la hauteur de la pile — pour chaque
 * cas que la consigne demande de couvrir : réponse courte, réponse longue, raisonnement ouvert,
 * outils affichés, bandeau d'erreur, pièce jointe dans le compositeur, police 1,3×, pliable déplié,
 * et clavier ouvert. Il ne se contente pas de vérifier « qu'il y a un rembourrage » : c'est
 * précisément ce qu'on pouvait faire avant, et c'est ce qui a laissé passer le défaut.
 *
 * Il verrouille aussi les deux autres règles de la consigne :
 *  - **la barre masquée réduit la réserve** (la poignée reste, la barre non) ;
 *  - **rien ne force la vue en bas quand la personne a remonté le fil** — le défilement automatique
 *    ne se déclenche que si le suivi est attaché ([ScrollFollow.afterTouch] rend `false`, donc un
 *    doigt sur la liste détache, et `ChatScreen` ne colle au bas que sous cette condition).
 */
class ChatThreadReserveTest {

    /**
     * Les hauteurs **mesurées** des cas de la consigne, en dp (densité 2,625 — relevées sur les
     * captures de ce lot : hauteur de la carte du compositeur et de la rangée du bas, en pixels,
     * divisée par la densité).
     */
    private data class Case(
        val label: String,
        val composerDp: Int,
        val bottomRowDp: Int,
        val imeDp: Int,
        val navigationDp: Int,
    )

    private val cases = listOf(
        // Pile « normale » : carte du compositeur seule, barre d'onglets complète, navigation par
        // gestes (24 dp) — c'est le cas de la capture du propriétaire.
        Case("réponse courte", 132, 86, 0, 24),
        Case("réponse longue", 132, 86, 0, 24),
        Case("raisonnement ouvert", 132, 86, 0, 24),
        Case("outils affichés", 132, 86, 0, 24),
        // Le bandeau d'erreur vit dans la carte : elle grandit de sa hauteur.
        Case("bandeau d'erreur", 176, 86, 0, 24),
        // Deux pièces jointes : la carte grandit encore.
        Case("pièce jointe", 198, 86, 0, 24),
        // Police système 1,3× : la carte et la rangée grandissent ensemble.
        Case("police 1,3×", 172, 104, 0, 24),
        // Pliable déplié : navigation à trois boutons (48 dp) sur les grands écrans de ce modèle.
        Case("pliable déplié", 132, 86, 0, 48),
        // Clavier ouvert : l'encart d'IME est le plus grand des deux, il est compté une fois.
        Case("clavier ouvert", 132, 86, 288, 24),
        // Barre masquée à la main : la poignée reste (44 dp), la barre s'en va.
        Case("barre masquée", 132, 44, 0, 24),
    )

    private val chatScreen =
        File("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt").readText()

    @Test
    fun `la reserve couvre toute la pile, dans les dix cas mesures`() {
        val report = StringBuilder(
            "\nTABLE DE LA RESERVE DU BAS — dp\n" +
                "cas                 compositeur  rangée  ime  nav  pile  réserve  marge  verdict\n",
        )
        cases.forEach { case ->
            val stack = ComposerStacking.stackHeightDp(
                composerDp = case.composerDp,
                bottomRowDp = case.bottomRowDp,
                imeDp = case.imeDp,
                navigationBarsDp = case.navigationDp,
            )
            val reserve = ComposerStacking.threadBottomReserveDp(
                composerDp = case.composerDp,
                bottomRowDp = case.bottomRowDp,
                imeDp = case.imeDp,
                navigationBarsDp = case.navigationDp,
            )
            val covers = ComposerStacking.reserveCoversStack(
                reserveDp = reserve,
                composerDp = case.composerDp,
                bottomRowDp = case.bottomRowDp,
                imeDp = case.imeDp,
                navigationBarsDp = case.navigationDp,
            )
            report.append(
                "%-19s %-11d %-7d %-4d %-4d %-5d %-8d %-6d %s\n".format(
                    case.label,
                    case.composerDp,
                    case.bottomRowDp,
                    case.imeDp,
                    case.navigationDp,
                    stack,
                    reserve,
                    reserve - stack,
                    if (covers) "ok" else "ÉCHEC",
                ),
            )
            assertTrue(
                "${case.label} : la réserve ($reserve) ne couvre pas la pile ($stack) — " +
                    "c'est ce qui laissait le bas du fil sous la carte",
                covers,
            )
            // Et la réserve reste l'écart de la maison au-dessus de la pile : ni collée, ni
            // démesurée. Une réserve trop grande pousserait le dernier message trop haut, ce qui se
            // voit tout autant.
            assertEquals(
                "${case.label} : la réserve doit valoir la pile plus l'écart de la maison",
                stack + ComposerStacking.StackGapDp,
                reserve,
            )
        }
        print(report)
    }

    @Test
    fun `l encart du systeme est compte, et une seule fois`() {
        // Le cœur du défaut : la réserve **doit** contenir l'encart du système. Vérifié en le
        // retirant — la réserve cesse alors de couvrir la pile, exactement comme dans le build
        // signalé.
        val composer = 132
        val row = 86
        val withoutSystem = composer + row + ComposerStacking.StackGapDp
        assertFalse(
            "sans l'encart du système, la réserve ne doit pas couvrir la pile",
            ComposerStacking.reserveCoversStack(
                reserveDp = withoutSystem,
                composerDp = composer,
                bottomRowDp = row,
                imeDp = 0,
                navigationBarsDp = 24,
            ),
        )
        // Et l'encart n'est jamais la somme du clavier et de la navigation.
        assertEquals(
            "au plus grand des deux, jamais la somme",
            ComposerStacking.threadBottomReserveDp(composer, row, imeDp = 288, navigationBarsDp = 24),
            composer + row + 288 + ComposerStacking.StackGapDp,
        )
        assertTrue(
            ComposerStacking.marginsDoNotCumulate(imeDp = 288, navigationBarsDp = 24),
        )
    }

    @Test
    fun `masquer la barre reduit la reserve, sans la supprimer`() {
        val full = ComposerStacking.threadBottomReserveDp(132, bottomRowDp = 86, imeDp = 0, navigationBarsDp = 24)
        val hidden = ComposerStacking.threadBottomReserveDp(132, bottomRowDp = 44, imeDp = 0, navigationBarsDp = 24)
        assertTrue("la réserve est plus petite quand la barre est masquée", hidden < full)
        assertTrue("mais elle reste : la poignée occupe le bas", hidden > 132 + 24)
        assertEquals("l'écart vaut la barre, moins la poignée", full - 86 + 44, hidden)
    }

    @Test
    fun `le fil reserve la pile, il ne l estime plus`() {
        // Le verrou de source : le fil passe par la fonction partagée, et l'ancienne formule
        // (`compositeur + onglets`) n'existe plus nulle part dans l'écran.
        assertTrue(
            "le fil doit réserver par `threadBottomReserve`",
            chatScreen.contains("threadBottomReserve("),
        )
        assertFalse(
            "l'ancienne réserve, sans l'encart du système, ne doit pas revenir",
            chatScreen.contains("(composerHeightPx + tabBarHeightPx).toDp()"),
        )
        // Le rembourrage du fil, le spacer des onglets vides et le rembourrage des autres onglets
        // passent tous par la **même** valeur : une seule source, donc pas de surface oubliée.
        val uses = Regex("glassBottomInset").findAll(chatScreen).count()
        assertTrue("la réserve doit servir à tous les rembourrages du bas ($uses occurrences)", uses >= 4)
    }

    @Test
    fun `remonter le fil a la main n est jamais defait par le defilement automatique`() {
        // Un doigt posé sur la liste détache le suivi, et rien ne le ré-attache pendant le geste.
        assertFalse("un geste détache le suivi", ScrollFollow.afterTouch())
        assertFalse(
            "pendant le geste, le suivi ne se ré-attache pas même si le bas est à l'écran",
            ScrollFollow.atRest(scrolling = true, atAnchor = true, following = false),
        )
        // Et l'écran ne colle au bas que sous cette condition. Le verrou nomme la décision
        // (`ScrollFollow.followsNewContent`) — la même garantie, portée par la fonction qui dit la
        // règle, et qui vaut désormais aussi pour les transitions d'état du tour.
        assertTrue(
            "le défilement automatique est conditionné au suivi",
            chatScreen.contains("if (ScrollFollow.followsNewContent(following) && state.messages.isNotEmpty())"),
        )
        // Le défilement automatique laisse le bas **au-dessus** de la réserve : il colle à la
        // dernière ligne avec un grand décalage, qui se borne à la fin du rembourrage.
        assertTrue(
            "le collage au bas passe par le décalage de fin de liste",
            chatScreen.contains("listState.scrollToItem(latestItemIndex, BottomScrollOffset)"),
        )
    }
}
