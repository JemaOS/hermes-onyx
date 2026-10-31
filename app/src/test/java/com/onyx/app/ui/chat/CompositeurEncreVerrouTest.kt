package com.onyx.app.ui.chat

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou de source des commandes du compositeur** — la seule chose qu'un test de source peut
 * dire, et elle est nécessaire : une règle juste que l'écran n'appelle pas ne change aucun pixel.
 *
 * Les **chiffres** (les rapports des sept thèmes dans les deux apparences) sont éprouvés sans
 * appareil par `CompositeurCommandsVerrouTest`, dans `core`. Ici on vérifie que le compositeur
 * **s'en sert** :
 *
 *  1. les cinq commandes prennent leur **fond** et leur **encre** de `ComposerCommands` (donc du
 *     thème, mesurés contre la plaque) — aucun appel direct à `theme.accent` / `theme.fill` /
 *     `theme.secondaryText` ne subsiste **comme teinte de commande** ;
 *  2. **aucune** commande du compositeur n'est atténuée quand elle est inerte : chaque appel de
 *     `ComposerCircleButton` porte `disabledAlpha = ComposerCommands.INERT_ALPHA` ;
 *  3. **aucune couleur écrite à la main** dans le fichier du compositeur (`Color(0x…`) ;
 *  4. la barre d'onglets garde sa propre source mesurée (`plaqueGround` / `plaqueInk`), et le sol du
 *     compositeur est **le même** (`ComposerCommands.ground`) : les deux rangées de la plaque
 *     lisent le même fond, donc ne peuvent pas diverger.
 *
 * **Le témoin du défaut**, mesuré et non supposé (il vit dans `core`, avec les rapports) : sur un
 * champ vide, `enabled = hasDraft || trailingState != "send"` vaut `false`, et la couche d'opacité
 * de 0,35 d'`onyxClickable` composait alors le disque d'envoi sur la plaque à **1,49:1** en apparence
 * sombre (« Standard ») — **1,40:1** au pire des sept thèmes, **2,60:1** au mieux : sous le plancher
 * d'icône de 3:1 dans **les quatorze couples (thème, apparence)**, c'est-à-dire nulle part visible.
 */
class CompositeurEncreVerrouTest {

    private fun read(path: String): String {
        val file = listOf(path, "../$path").map(::File).firstOrNull { it.isFile }
            ?: error("fichier introuvable : $path")
        return file.readText()
    }

    private val composer = read("src/main/java/com/onyx/app/ui/chat/components/OnyxComposer.kt")
    private val coreCommands = read("../core/src/main/java/com/onyx/app/ui/design/ComposerCommands.kt")
    private val coreBar = read("../core/src/main/java/com/onyx/app/ui/design/ChatTabBar.kt")
    private val groupe = read("src/main/java/com/onyx/app/ui/bots/GroupComposer.kt")

    @Test
    fun `les commandes du compositeur prennent fond et encre de la mesure`() {
        listOf(
            "ComposerCommands.disc(" to "le fond d'un disque (thème écarté du sol de la plaque)",
            "ComposerCommands.inkOnDisc(" to "l'encre du glyphe sur le disque réellement peint",
            "ComposerCommands.glyphOnPlaque(" to "l'encre d'une commande au trait, sur la plaque",
        ).forEach { (appel, quoi) ->
            assertTrue(
                "le compositeur doit appeler $appel — $quoi. Sans cet appel, le chiffre mesuré " +
                    "par le verrou de `core` n'est pas celui que l'écran peint.",
                composer.contains(appel),
            )
        }
        assertFalse(
            "une teinte de commande ne s'écrit plus à la main : `theme.fill` tel quel mesurait " +
                "1,10:1 contre le sol sombre de la plaque (le bouton vocal disparaissait).",
            composer.contains("tint = theme.fill,"),
        )
        assertFalse(
            "une teinte de commande ne s'écrit plus à la main : `theme.accent` tel quel mesurait " +
                "1,14:1 sur la page du thème « Code » en clair.",
            composer.contains("tint = theme.accent,"),
        )
    }

    @Test
    fun `aucune commande du compositeur n est attenuee quand elle est inerte`() {
        val appels = Regex("ComposerCircleButton\\(").findAll(composer).count()
        val inertes = Regex("disabledAlpha = ComposerCommands\\.INERT_ALPHA").findAll(composer).count()
        assertTrue("le compositeur doit peindre des boutons ronds (relevé : $appels)", appels > 0)
        assertTrue(
            "**chaque** bouton rond du compositeur porte `disabledAlpha = ComposerCommands." +
                "INERT_ALPHA` : `enabled` ne commande que le clic. Appels = $appels, inertes " +
                "déclarés = $inertes. Le bouton d'envoi sur un champ vide mesurait 1,49:1 en " +
                "sombre à 35 % d'opacité — la zone était vide sur la capture du propriétaire.",
            appels == inertes,
        )
        assertTrue(
            "l'état inerte existe bien (champ vide ⇒ rien à envoyer) : c'est ce que le clic refuse.",
            composer.contains("enabled = hasDraft || trailingState != \"send\""),
        )
    }

    @Test
    fun `aucune couleur ecrite a la main dans le compositeur`() {
        assertFalse(
            "un `Color(0x…)` dans le compositeur est une teinte qui ne vient pas du thème : elle " +
                "divergera au premier thème qui change. Relevé : il n'y en a aucun.",
            composer.contains("Color(0x"),
        )
    }

    @Test
    fun `le sol du compositeur est celui de la barre d onglets`() {
        assertTrue(
            "le sol d'une commande est le fond de la plaque **réellement peint**",
            coreCommands.contains("fun ground(theme: ResolvedTheme): Color = plaqueGround(theme, selected = false)"),
        )
        assertTrue(
            "et la barre d'onglets garde ses deux fonctions mesurées",
            coreBar.contains("fun plaqueGround(") && coreBar.contains("fun plaqueInk("),
        )
        // **Arbitrage de la fusion avec `lot/groupe-parite`.** Ce verrou exigeait du compositeur de
        // groupe qu'il porte `disabledAlpha = ComposerCommands.INERT_ALPHA` **à lui**. Or le lot
        // `lot/groupe-parite` lui retire **toute** peinture de commande (son propre verrou,
        // `GroupePariteVerrouTest`, exige que `GroupComposer.kt` ne contienne plus
        // `ComposerCircleButton(`) et le fait passer par `OnyxComposer`. Les deux assertions sont
        // donc **incompatibles** écrites ainsi. L'intention de ce verrou est conservée et renforcée :
        // l'envoi de la salle **est** celui de la maison, celui qui porte `INERT_ALPHA` — le défaut
        // est fermé **une fois**, là où on l'a mesuré (1,49:1 en sombre à 35 % d'opacité).
        assertTrue(
            "le compositeur de groupe remet son envoi à la maison (`OnyxComposer`) — donc au seul " +
                "bouton d'envoi, qui porte `disabledAlpha = ComposerCommands.INERT_ALPHA`",
            groupe.contains("OnyxComposer(") &&
                composer.contains("disabledAlpha = ComposerCommands.INERT_ALPHA"),
        )
    }
}
