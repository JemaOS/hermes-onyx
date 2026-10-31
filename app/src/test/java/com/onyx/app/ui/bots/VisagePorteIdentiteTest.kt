package com.onyx.app.ui.bots

import com.onyx.app.ui.design.ChatTab
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le visage du quatrième repère ouvre l'identité du bot.**
 *
 * Le défaut : le lot de la plaque avait changé le **dessin** du quatrième repère de la barre du bas
 * (le visage au lieu de l'ampoule) **sans changer son routage**. On appuyait donc sur un visage, et
 * on arrivait sur la page « Idées » qui occupait cette place — l'apparence avait changé, pas ce que
 * ça déclenche.
 *
 * Ce verrou tient les trois bouts, parce qu'il suffit d'un seul pour perdre la correction :
 *
 * 1. la **position** — le repère-visage est bien le quatrième de la barre, et c'est la place
 *    qu'occupait « Idées » (on ne l'a pas déplacé) ;
 * 2. le **câblage** — ce repère est routé vers `onOpenBotIdentity`, et **plus** vers le pageur ; le
 *    parent le branche sur `Route.BOT_EDITOR`, sur le bot de la conversation ;
 * 3. la **morsure** — la règle est une fonction pure : rejouée sur l'ancien routage, elle
 *    **échoue**. Il n'y a donc pas de version de ce test qui passerait avec le défaut.
 *
 * **L'arbitrage de la réconciliation, nommé ici.** Un lot avait ajouté une **rangée « Idées »** sur
 * cette page, pour garder le mot atteignable. La suppression d'Idées/Objectifs (demande explicite du
 * propriétaire) la **refuse** : la page d'identité du bot **ne porte pas** de rangée « Idées », et
 * l'onglet « Idées » n'existe plus (le pageur compte trois pages). Ce verrou **nomme** cet arbitrage
 * — il n'y a plus de porte de secours pour un écran supprimé — au lieu de contredire
 * `SuppressionIdeesObjectifsTest`, qui arbitre.
 */
class VisagePorteIdentiteTest {

    private fun read(path: String): String {
        val file = listOf(path, "../$path").map(::File).firstOrNull { it.isFile }
            ?: error("fichier introuvable : $path")
        return file.readText()
    }

    private val chatScreen = read("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt")
    private val topBar = read("src/main/java/com/onyx/app/ui/chat/components/OnyxTopBar.kt")
    private val app = read("src/main/java/com/onyx/app/ui/OnyxApp.kt")
    private val editor = read("src/main/java/com/onyx/app/ui/bots/BotEditorScreen.kt")

    /**
     * **La règle, réduite à une fonction pure.** C'est elle le verrou : elle est vraie sur le code
     * corrigé, fausse sur le défaut — et c'est la **même** fonction qui est interrogée dans les
     * deux cas, donc le test ne peut pas être « vrai par construction ».
     * Elle accepte les deux écritures équivalentes du branchement : le `if (…) { … }` du premier
     * jet et le `when { … -> … }` de la réconciliation — dans les deux cas, l'appui sur le repère
     * **doit** appeler `onOpenBotIdentity()` sous la garde du branchement.
     */
    private fun routesFaceToIdentity(source: String): Boolean =
        Regex("""index == faceIndex && onOpenBotIdentity != null\s*(?:->|\{)\s*onOpenBotIdentity\(\)""")
            .containsMatchIn(source)

    // ---- 1. La position du repère n'a pas bougé -------------------------------------------------

    @Test
    fun `le quatrieme repere de la barre est le visage, la ou etait Ideas`() {
        assertEquals("la barre dessine quatre repères", 4, ChatTab.drawn.size)
        assertEquals("le repère-visage est le quatrième", 3, ChatTab.drawn.lastIndex)
        assertEquals(
            "et c'est bien le visage, à la position qu'occupait « Idées » — on n'a pas déplacé le repère",
            ChatTab.BOT,
            ChatTab.drawn.last(),
        )
        assertFalse("le repère-visage n'est pas une page du pageur", ChatTab.BOT in ChatTab.paged)
    }

    // ---- 2. Le câblage : le geste, pas le dessin ------------------------------------------------

    @Test
    fun `l appui sur le repere-visage ouvre l identite, pas une page du pageur`() {
        assertTrue(
            "ChatScreen doit router le repère-visage vers `onOpenBotIdentity`",
            routesFaceToIdentity(chatScreen),
        )
        assertFalse(
            "l'ancien routage — le pageur sans condition — ne doit plus se lire dans `onSelect`",
            Regex("""onSelect\s*=\s*\{\s*index\s*->\s*scope\.launch""").containsMatchIn(chatScreen),
        )
        assertTrue(
            "et le repère-visage garde son index de position, calculé une seule fois",
            chatScreen.contains("val faceIndex = ChatTab.drawn.lastIndex"),
        )
    }

    @Test
    fun `le parent branche le visage sur l identite du bot de la conversation`() {
        assertTrue(app.contains("onOpenBotIdentity = openBotIdentity"))
        assertTrue(
            "l'identité ouverte est celle du bot de la conversation, sinon celle de l'agent principal",
            app.contains("(openedConversationBot ?: bots.firstOrNull { it.isDefault })"),
        )
        assertTrue(
            "et elle ouvre la page d'identité (`Route.BOT_EDITOR`)",
            app.contains("navTo(Route.BOT_EDITOR.name)"),
        )
    }

    @Test
    fun `le visage de l en-tete du fil ouvre la meme page`() {
        assertTrue(
            "ChatScreen passe le même branchement au visage de l'en-tête",
            chatScreen.contains("onOpenFace = onOpenBotIdentity"),
        )
        assertTrue(topBar.contains("onOpenFace: (() -> Unit)? = null"))
        assertTrue(
            "et le visage y devient un contrôle dès qu'il sait où il mène",
            topBar.contains("if (openFace != null)"),
        )
    }

    // ---- 3. « Idées » n'a plus de porte : l'arbitrage, nommé ------------------------------------

    @Test
    fun `Idees n a plus de porte, ni sur la page d identite ni ailleurs`() {
        // L'arbitrage : la page d'identité du bot **ne porte pas** de rangée « Idées ». Le lot du
        // visage l'avait ajoutée pour garder le mot atteignable ; la suppression d'Idées/Objectifs
        // (demande du propriétaire) la refuse — l'écran, sa route, sa destination, sa clé de
        // dictionnaire et toute porte de secours sont partis ensemble.
        assertFalse(
            "la page d'identité ne porte AUCUNE rangée « Idées »",
            editor.contains("onOpenIdeas"),
        )
        assertFalse(
            "et aucun libellé « Ideas » n'y est dessiné",
            editor.contains("label = \"Ideas\""),
        )
        assertFalse("le parent ne la branche plus", app.contains("onOpenIdeas"))
    }

    // ---- 4. La page d'identité est dans le langage du socle --------------------------------------

    @Test
    fun `la page d identite porte le socle et plus les valeurs de l ancien`() {
        assertFalse(
            "plus une seule section de l'ancien formulaire (cartes plates, marges d'à-côté)",
            editor.contains("OnyxFormSection("),
        )
        assertTrue(
            "les cartes portent l'ombre en trois profondeurs, appelée pour de vrai",
            editor.contains("socleShadow(OnyxSocle.ShadowMid, OnyxSocle.RadiusCard"),
        )
        assertTrue("le trait est celui de la maison", editor.contains("OnyxSocle.Stroke,"))
        assertTrue("la marge d'écran est celle de la maison", editor.contains("OnyxSocle.ScreenMargin"))
        assertTrue("et les corps sont ceux du socle", editor.contains("socleTitleStyle(theme)"))
        assertTrue("les identifiants et les nombres passent en monospace", editor.contains("socleMonoStyle(theme)"))
    }

    // ---- 5. La morsure : l'ancien routage fait échouer la règle ----------------------------------

    @Test
    fun `remettre l ancien routage fait echouer la regle`() {
        // On rejoue **exactement** le défaut : le repère-visage repart dans le pageur, sans
        // condition. La règle doit alors être fausse — c'est la preuve que le verrou mord.
        val old = chatScreen.replace(
            Regex("""index == faceIndex && onOpenBotIdentity != null\s*(?:->|\{)\s*onOpenBotIdentity\(\)"""),
            "scope.launch { pagerState.animateScrollToPage(index) }",
        )
        assertFalse(
            "avec l'ancien routage, la règle doit ÉCHOUER (le verrou mord)",
            routesFaceToIdentity(old),
        )
        assertFalse(
            "elle est au moins aussi exigeante que le garde : un repère sans branchement ne passe pas",
            routesFaceToIdentity("if (index == faceIndex) { onOpenBotIdentity() }"),
        )
    }
}
