package com.onyx.app.ui.chat

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou de source des trois retouches de la plaque.**
 *
 * Le lot « plaque finale » avait raidi sept écarts de la planche — mais sur trois points il avait
 * été au-delà d'elle, dont un **contre** elle. Les trois retouches sont donc verrouillées ici, écran
 * par écran, parce qu'un test de source est la seule chose qui empêche une décision de se reperdre :
 *
 *  1. **la pastille de l'onglet actif** revient — la planche la déclare
 *     (`.navi.on{background:var(--pill)}`) et **c'est elle qui gagne** ;
 *  2. **le bouton du mode vocal** revient **sur la plaque**, dans la rangée du champ entre le texte
 *     et l'envoi, et il revient **sans rien supprimer** : ce sont la marche de la rangée et le
 *     rembourrage du champ qui prennent les valeurs de la planche, et le libellé du champ qui est
 *     **choisi par la mesure** plutôt que coupé ;
 *  3. **le libellé du champ** est celui de la planche (« Écrire à Onyx… »), ajouté au dictionnaire
 *     central.
 *
 * Les chiffres (140 dp, 64 + 76, les alphas de `--pill`, la décision de la mesure) sont éprouvés
 * sans appareil par `PlaqueGeometryTest` et `PlaqueInviteTest`, dans `core`.
 */
class PlaqueRetouchesWiringTest {

    private fun read(path: String): String {
        val file = listOf(path, "../$path").map(::File).firstOrNull { it.isFile }
            ?: error("fichier introuvable : $path")
        return file.readText()
    }

    private val composer = read("src/main/java/com/onyx/app/ui/chat/components/OnyxComposer.kt")
    private val bar = read("../core/src/main/java/com/onyx/app/ui/design/ChatTabBar.kt")
    private val geometry = read("../core/src/main/java/com/onyx/app/ui/design/PlaqueGeometry.kt")
    private val dictionary = read("../core/src/main/java/com/onyx/app/localization/OnyxFrenchRemaining.kt")

    // — 1 · la pastille de l'onglet actif —

    @Test
    fun `la pastille de l onglet actif est peinte avec les valeurs de la planche`() {
        assertTrue(
            "la table porte `--pill` (blanc 74 % / 17 %)",
            geometry.contains("ActivePillLightAlpha: Float = 0.74f") &&
                geometry.contains("ActivePillDarkAlpha: Float = 0.17f"),
        )
        assertTrue(
            "et son filet (`inset 0 0 0 1px --pillline`, 92 % / 24 %)",
            geometry.contains("ActivePillLineLightAlpha: Float = 0.92f") &&
                geometry.contains("ActivePillLineDarkAlpha: Float = 0.24f"),
        )
        assertTrue(
            "la barre lit la table et peint le fond sous l'onglet choisi",
            bar.contains("val pill = PlaqueGeometry.activePill(theme)") &&
                bar.contains("val pillLine = PlaqueGeometry.activePillLine(theme)") &&
                bar.contains("background(pill, SurfaceRole.ActiveCapsule.shape)") &&
                bar.contains("border("),
        )
    }

    // — 2 · le bouton du mode vocal, sur la plaque, sans rien supprimer —

    @Test
    fun `le bouton du mode vocal est dessine sur la plaque, sans condition de mode`() {
        val row = composer.substringAfter("// **Le bouton du mode vocal, sur la plaque.**")
            .substringBefore("// **L'état du bouton d'envoi")
        assertTrue("la rangée doit porter le bouton vocal", row.contains("ComposerCircleButton("))
        assertTrue("et il ouvre le mode vocal", row.contains("onClick = onTalk,"))
        assertFalse(
            "il n'est plus réservé à la carte : la plaque le porte, comme la planche",
            row.contains("if (!plate)"),
        )
        assertTrue(
            "le micro de la dictée est toujours là : aucune commande n'a été supprimée",
            composer.contains("onClick = onDictate ?: onTalk,"),
        )
        assertTrue(
            "le « + » et le micro gardent la cible de 44 dp de la maison",
            composer.contains("internal val ComposerControlSlotHeight = OnyxSpace.ThumbnailSize"),
        )
        assertTrue(
            "et c'est l'envoi, seul, qui est un peu plus grand sur la plaque (48 dp, `.csend`)",
            composer.contains("if (plate) PlaqueGeometry.SendSlot else ComposerControlSlotHeight") &&
                geometry.contains("val SendSlot: Dp = 48.dp"),
        )
    }

    @Test
    fun `la place du bouton vocal se prend sur les valeurs de la planche, pas sur une commande`() {
        assertTrue(
            "la marche de la rangée prend `gap:10px` de la planche sur la plaque",
            geometry.contains("val FieldGap: Dp = 10.dp") &&
                composer.contains("if (plate) PlaqueGeometry.FieldGap else ComposerRowGeometry.Gap"),
        )
        assertTrue(
            "et le champ prend le rembourrage `.askf` (`0 4px`) de la planche",
            geometry.contains("val FieldInset: Dp = 4.dp") &&
                composer.contains("if (plate) PlaqueGeometry.FieldInset else 12.dp"),
        )
        assertTrue(
            "le champ reste élastique : il prend la place qui reste",
            composer.contains("content(Modifier.weight(1f))"),
        )
    }

    @Test
    fun `la plaque vaut 140 dp au repos et grandit avec le champ, sans jamais couper`() {
        // **Décision changée, et assumée** (demande du propriétaire : « le champ ne s'adapte pas »).
        // La rangée du champ était à hauteur **figée** (64 dp) : au-delà d'une ligne, le texte
        // débordait sous les commandes et la première ligne était coupée. Elle grandit désormais avec
        // son contenu — **au minimum 64 dp** (l'état au repos), puis ligne par ligne jusqu'au maximum
        // où le champ défile en lui-même (`ComposerGrowth`). Au repos, la plaque vaut toujours 140 dp.
        assertTrue(
            "la rangée du champ peut grandir au-delà des 64 dp du repos",
            composer.contains("Modifier.heightIn(min = PlaqueGeometry.FieldRowHeight)"),
        )
        assertFalse(
            "et elle n'est plus figée à 64 dp : c'est la hauteur figée qui coupait la première ligne",
            composer.contains("Modifier.height(PlaqueGeometry.FieldRowHeight)"),
        )
        assertTrue(
            "le plafond de lignes du champ est celui de la règle de croissance (8 lignes)",
            composer.contains("maxLines: Int = ComposerGrowth.MaxLines,"),
        )
    }

    // — 3 · le libellé du champ, celui de la planche —

    @Test
    fun `le libelle du champ est celui de la planche, choisi par la mesure`() {
        assertTrue(
            "la clé de la planche est au dictionnaire central, mot pour mot",
            dictionary.contains("\"Write to Onyx…\" to \"Écrire à Onyx…\""),
        )
        assertTrue(
            "et sa version courte est la même phrase sans l'ellipse",
            dictionary.contains("\"Write to Onyx\" to \"Écrire à Onyx\""),
        )
        assertTrue(
            "la plaque emploie le libellé choisi par la mesure de la place réellement offerte",
            composer.contains("OnyxLocalization[PlaqueInvite.key(maxWidth.value, measureInvite)]"),
        )
        assertTrue(
            "la mesure porte sur la police réellement rendue, pas sur une estimation",
            composer.contains("style = theme.body,") && composer.contains("maxLines = 1,"),
        )
    }
}
