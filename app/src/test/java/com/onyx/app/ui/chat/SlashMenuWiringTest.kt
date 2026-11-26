package com.onyx.app.ui.chat

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le menu « / » à l'écran**, tenu par contrat de source.
 *
 * Le défaut relevé sur l'appareil tenait en deux mots : « ça passe derrière », et « pas bien
 * aligné ». Deux causes distinctes, donc deux verrous distincts :
 *
 *  1. **La place.** La liste était le dernier enfant du pane du fil : son bas tombait sous la
 *     plaque du compositeur, et ses dernières lignes n'étaient ni lisibles ni cliquables. Elle
 *     remonte maintenant de la réserve du fil, et sa hauteur est **mesurée** — jamais en dur.
 *  2. **L'alignement.** Le nom et le détail vivaient dans une seule rangée : le détail du serveur
 *     fait deux lignes, la rangée grandissait de travers et les lignes n'avaient plus la même
 *     hauteur. Le dessin est une colonne, chaque texte coupé à une ligne.
 *
 * Et un troisième, qui rendait le tout inutilisable : le menu restait ouvert sur la commande
 * choisie. Il se referme maintenant au choix ([ChatViewModel.pickSlash]).
 */
class SlashMenuWiringTest {

    private val chatScreen = File("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt").readText()
    private val menu = File("src/main/java/com/onyx/app/ui/chat/components/SlashMenu.kt").readText()
    private val viewModel = File("src/main/java/com/onyx/app/ui/chat/ChatViewModel.kt").readText()
    private val commands = File("src/main/java/com/onyx/app/ui/chat/ChatCommands.kt").readText()
    private val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()

    @Test
    fun `le menu se pose au-dessus de la plaque, jamais derriere`() {
        // L'ancienne feuille était ancrée au bas du pane : c'est exactement ce que le
        // propriétaire voyait (« ça passe derrière »). Sa forme ne doit pas revenir.
        assertFalse(
            "la liste ne doit plus être un enfant du pane collé au bas de l'écran",
            chatScreen.contains("Spacer(Modifier.height(glassBottomInset + floatLane))"),
        )
        assertTrue("le menu est un composant à part", chatScreen.contains("SlashMenu("))
        // **Il se pose SUR la plaque.** Il réservait `glassBottomInset + floatLane + BlockGap` :
        // la voie des commandes flottantes s'ajoutait à une réserve qui la contient déjà, et le
        // menu flottait 68 dp au-dessus de sa plaque — détaché, dans une bande vide (relevé sur la
        // capture). La réserve du compositeur plus un demi-pas : la garantie « jamais derrière »
        // tient (la réserve couvre toute la pile), et le menu touche presque sa plaque.
        assertTrue(
            "il se pose sur la réserve du compositeur, à un demi-pas de la plaque",
            chatScreen.contains("bottom = glassBottomInset + OnyxSpace.Half"),
        )
        assertTrue(
            "et sa hauteur au plus lui est donnée, mesurée",
            chatScreen.contains("maxHeight = slashMenuMaxHeight"),
        )
        // La hauteur vient de l'écran, pas d'une constante : une police à 1,3× ou un clavier
        // ouvert doivent faire défiler la liste, pas la faire passer sous la plaque.
        assertTrue(
            "la hauteur au plus est calculée depuis la configuration",
            chatScreen.contains("LocalConfiguration.current.screenHeightDp.dp") &&
                chatScreen.contains(".coerceAtLeast(OnyxSpace.Base)"),
        )
    }

    @Test
    fun `chaque ligne tient sur deux lignes alignees, et defile au besoin`() {
        assertTrue("la liste défile", menu.contains(".verticalScroll(rememberScrollState())"))
        assertTrue("la hauteur est bornée", menu.contains(".heightIn(max = maxHeight)"))
        assertEquals(
            "un seul site de coupure — la description ; un nom de commande n'est pas coupé",
            1,
            Regex("TextOverflow.Ellipsis").findAll(menu).count(),
        )
        assertTrue(
            "la description se replie sur deux lignes (politique des libellés)",
            menu.contains("maxLines = 2,"),
        )
        assertTrue(
            "le détail passe par le catalogue (une description de compétence ressort inchangée)",
            menu.contains("OnyxLocalization[item.detail]"),
        )
    }

    @Test
    fun `la liste fournie passe devant celle du serveur`() {
        // Le fond du défaut « les compétences sont inutilisables » : ajoutées après, coupées à
        // huit. L'ordre est maintenant : l'app d'abord, le serveur ensuite.
        assertTrue(commands.contains("(local + items).take(SLASH_MENU_LINES)"))
    }

    @Test
    fun `choisir une ligne ferme le menu`() {
        assertTrue("le choix a son entrée", viewModel.contains("fun pickSlash(text: String)"))
        val body = viewModel.substringAfter("fun pickSlash(text: String)")
            .substringBefore("\n    }")
        assertTrue(
            "le choix vide le menu dans le même geste",
            body.contains("slashItems = emptyList()"),
        )
        // Et l'écran l'utilise : le menu ne se rouvre pas sur la commande qu'on vient de prendre.
        assertTrue(chatScreen.contains("onPick = onPickSlash"))
        assertTrue(app.contains("onPickSlash = chatViewModel::pickSlash"))
    }
}
