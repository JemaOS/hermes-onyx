package com.onyx.app.ui.chat

import androidx.compose.ui.unit.dp
import com.onyx.app.ui.design.ComposerStacking
import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.design.PlaqueGeometry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou du retrait de la poignée à chevron.**
 *
 * Le propriétaire a encadré le rond à chevron posé au-dessus de la plaque et l'a dit en une phrase :
 * *« supprime le bouton pour la possibilité de cacher »*. Le bouton commandait un **état** — la
 * barre d'onglets masquée, gardé par conversation — et occupait une **voie réservée** au-dessus de la
 * plaque.
 *
 * Ce fichier est le verrou des trois choses qui comptaient, et chacune est vérifiable :
 *
 *  1. **le bouton n'existe plus** — ni son dessin, ni son geste, ni son état, ni sa géométrie : les
 *     assertions portent sur l'**absence** des symboles, dans **chaque** fichier qui les portait ;
 *  2. **la plaque ne peut plus être masquée** : le seul effacement qui subsiste est celui du
 *     défilement, demandé par le propriétaire, et il ne mémorise rien (voir `ComposerStackingVeilTest`
 *     pour la règle, qui exige que la frappe la retienne toujours) ;
 *  3. **la place rendue au fil est mesurée**, et la pastille de défilement n'y perd pas sa garantie :
 *     la bande réservée ne peut pas être plus courte que la commande qu'elle loge, sinon la pastille
 *     se poserait **sur du texte** — le défaut relevé, et ce que ce verrou interdit.
 *
 * Les trois premiers points lisent les sources (c'est la seule chose qu'un test de source peut dire) ;
 * le quatrième compare les **mêmes** chiffres que l'écran emploie — ceux de `PlaqueGeometry`.
 */
class RetraitPoigneeChevronTest {

    private fun read(path: String): String {
        val file = listOf(path, "../$path").map(::File).firstOrNull { it.isFile }
            ?: error("fichier introuvable : $path")
        return file.readText()
    }

    private val screen = read("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt")
    private val bar = read("../core/src/main/java/com/onyx/app/ui/design/ChatTabBar.kt")
    private val stacking = read("../core/src/main/java/com/onyx/app/ui/design/ComposerStacking.kt")
    private val store = read("src/main/java/com/onyx/app/data/local/ChatPanelStore.kt")
    private val tabs = read("../core/src/main/java/com/onyx/app/localization/OnyxFrenchTabs.kt")

    // — 1 · le bouton, partout où il vivait —

    @Test
    fun `le rond a chevron n est plus dessine nulle part`() {
        assertFalse("ChatScreen pose encore la poignée", screen.contains("ChatTabBarHandle("))
        assertFalse("ChatTabBar dessine encore la poignée", bar.contains("fun ChatTabBarHandle("))
        assertFalse("la table de la poignée existe encore", bar.contains("object TabBarHandle"))
        assertFalse("la barre accepte encore une poignée", bar.contains("handle: (@Composable () -> Unit)?"))
        assertFalse("la barre réserve encore la place d'une poignée", bar.contains("handleDp = if (handle != null)"))
    }

    @Test
    fun `l etat d ouverture et de fermeture a disparu de bout en bout`() {
        assertFalse("l'écran garde encore l'état", screen.contains("tabBarHidden"))
        assertFalse("l'écran calcule encore la visibilité", screen.contains("tabBarVisible("))
        assertFalse("la règle de visibilité existe encore", stacking.contains("fun tabBarVisible("))
        assertFalse("l'opacité de masquage existe encore", stacking.contains("fun tabBarAlpha("))
        assertFalse("l'aller-retour existe encore", stacking.contains("fun toggled("))
        assertFalse("le libellé de la poignée existe encore", stacking.contains("fun handleLabelKey("))
        assertFalse("le magasin garde encore la préférence", store.contains("tabsHidden"))
        assertFalse("la barre est encore derrière une visibilité conditionnelle", screen.contains("label = \"tabBarVisibility\""))
        // Et ses deux libellés ont quitté le dictionnaire : une clé que personne ne porte est un
        // chemin qu'on rebrancherait par distraction.
        assertFalse("« Hide the tab bar » est encore une clé", tabs.contains("\"Hide the tab bar\" to "))
        assertFalse("« Show the tab bar » est encore une clé", tabs.contains("\"Show the tab bar\" to "))
    }

    // — 2 · la barre est peinte, sans condition —

    @Test
    fun `la barre d onglets est posee sans aucune condition de visibilite`() {
        assertTrue(
            "la barre doit être posée telle quelle dans la plaque",
            screen.contains("                ChatTabBar("),
        )
        assertTrue(
            "et ses quatre repères restent ceux de la planche",
            screen.contains("tabs = ChatTab.drawn,"),
        )
    }

    // — 3 · la place rendue, et la garantie tenue —

    @Test
    fun `la voie flottante a retreci de douze dp et le fil en profite`() {
        // La voie d'avant : le dégagement de la plaque, la cible de la poignée, et l'écart qu'elle
        // gardait au-dessus d'elle — 12 + 44 + 12 = 68 dp. La voie d'aujourd'hui : le dégagement et
        // la pastille, 12 + 44 = 56. Le fil récupère les 12 dp de la cible retirée.
        val voieAvant = PlaqueGeometry.FloatClearance + OnyxSpace.ThumbnailSize + OnyxSpace.Snug
        assertEquals(68.dp, voieAvant)
        assertEquals(56.dp, PlaqueGeometry.FloatLane)
        assertEquals("les douze dp rendus au fil", 12.dp, voieAvant - PlaqueGeometry.FloatLane)
        // L'écran ne compose plus aucune somme : il prend la voie de la table, donc les deux valeurs
        // ne peuvent pas diverger.
        assertTrue(screen.contains("val floatLane = PlaqueGeometry.FloatLane"))
        assertFalse(screen.contains("PlaqueGeometry.FloatClearance + PlaqueGeometry.FloatLane"))
    }

    @Test
    fun `la pastille de defilement ne peut pas se poser sur le texte, meme apres le retrait`() {
        // **Le calcul de non-chevauchement, avec les chiffres de l'écran.**
        //
        //  - la pastille est posée à `PillClearanceDp` au-dessus du haut de la plaque, et elle est
        //    haute de `PillHeight` : sa bande vaut donc `PillClearanceDp + PillHeight` ;
        //  - le fil s'arrête à `floatLane` au-dessus du haut de la plaque.
        // La condition de non-chevauchement est donc : `floatLane >= PillClearanceDp + PillHeight`.
        // C'est une **inégalité** que l'on mesure, pas une intention : si quelqu'un rabotait la voie
        // pour gagner encore quelques points, ce test rougirait avant que la pastille ne morde le
        // texte à l'écran.
        val bandeDeLaPastilleDp = ComposerStacking.PillClearanceDp + PlaqueGeometry.PillHeight.value
        assertEquals("dégagement 12 + pastille 44", 56f, bandeDeLaPastilleDp, 0f)
        assertTrue(
            "la voie réservée doit couvrir toute la bande de la pastille " +
                "(${PlaqueGeometry.FloatLane} contre $bandeDeLaPastilleDp dp)",
            PlaqueGeometry.FloatLane.value >= bandeDeLaPastilleDp,
        )
        // Et la pastille se pose bien dans cette bande, pas au-dessus d'elle.
        assertTrue(ComposerStacking.PillClearanceDp >= 0)
        assertTrue(PlaqueGeometry.PillHeight.value >= OnyxSpace.ThumbnailSize.value)
    }
}
