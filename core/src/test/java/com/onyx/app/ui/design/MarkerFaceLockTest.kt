package com.onyx.app.ui.design

import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou du visage du repère de la barre d'onglets — ENTIER, sans anneau qui le rogne, et
 * séparé du verre par un levier qui ne rogne pas.**
 *
 * La demande du propriétaire, capture à l'appui (le repère « Onyx » encadré en rouge) :
 *
 * > « pas besoin de mettre le cercle blanc, ça coupe le visage, supprime le cercle »
 *
 * Le repère portait un **anneau blanc** de 2,5 dp (`PlateGeometry.FaceRing`, la planche `.capface`)
 * **et** découpait le visage en rond (`clip(RoundedCornerShape(50))`) : l'anneau débordait sur le
 * bord du visage, et le rond le tranchait. Les deux sont **retirés** ; le visage est peint entier.
 *
 * Le verrou tient deux dents :
 *
 *  1. **la source** — le repère ne peint plus l'anneau, ne découpe plus le visage ; l'anneau reste
 *     **déclaré et documenté** dans `PlateGeometry` (pas effacé en silence), et le levier de
 *     remplacement (une **ombre douce**, `clip = false`) est nommé ;
 *  2. **la mesure** — la séparation du visage contre le **fond réel** de la plaque, dans les **deux
 *     apparences**, sur **toute** la palette des visages. Le témoin nommé est le visage **nu** (qui
 *     se confond avec le verre) et l'**anneau blanc refusé** : l'ombre douce doit faire **strictement
 *     mieux** que l'anneau qu'elle remplace.
 */
class MarkerFaceLockTest {

    private fun read(path: String): String =
        listOf(File(path), File("../$path")).firstOrNull { it.isFile }?.readText() ?: ""

    private val bar = read("core/src/main/java/com/onyx/app/ui/design/ChatTabBar.kt")
    private val geo = read("core/src/main/java/com/onyx/app/ui/design/PlateGeometry.kt")

    private fun theme(dark: Boolean): ResolvedTheme =
        ResolvedTheme.resolve(OnyxTheme.STANDARD, dark = dark, fonts = OnyxFonts.System)

    // =============================================================================================
    // 1 · La source — le visage entier, sans anneau, et l'anneau non effacé en silence
    // =============================================================================================

    @Test
    fun `le repere-visage ne peint PLUS l anneau blanc qui rognait le visage`() {
        assertFalse(
            "le repère-visage peint encore l'anneau blanc (`PlateGeometry.FaceRing`) : le " +
                "propriétaire l'a refusé — il rognait le visage. Fautif : ChatTabBar.kt",
            bar.contains("PlateGeometry.FaceRing,"),
        )
        assertFalse(
            "le visage du repère est encore découpé en rond (`clip(RoundedCornerShape(50))`) : le " +
                "contenant le rogne. Fautif : ChatTabBar.kt",
            bar.contains("clip(RoundedCornerShape(50))"),
        )
        assertTrue(
            "l'anneau a disparu du code : il doit rester DÉCLARÉ et documenté comme non peint (le " +
                "propriétaire l'a refusé), pas effacé en silence — PlateGeometry.kt",
            geo.contains("val FaceRing: Dp = 2.5.dp") && geo.contains("PLUS PEINT"),
        )
        assertTrue(
            "aucun levier ne remplace l'anneau : le visage se confondrait avec le verre — ChatTabBar.kt",
            bar.contains("PlateGeometry.FaceShadow"),
        )
        assertTrue(
            "le levier de remplacement doit NE PAS rogner (`clip = false`) — ChatTabBar.kt",
            bar.contains("clip = false"),
        )
    }

    // =============================================================================================
    // 2 · La mesure — le visage nu se confond, l'ombre douce fait mieux que l'anneau refusé
    // =============================================================================================

    @Test
    fun `le visage se detache du verre SANS l anneau, et mieux que l anneau ne le faisait`() {
        val sep = LinkedHashMap<Boolean, Triple<Float, Float, Float>>() // (sans, anneau, ombre)
        for (dark in listOf(false, true)) {
            val theme = theme(dark)
            sep[dark] = Triple(
                PlateGeometry.faceSeparationWithoutLever(theme),
                PlateGeometry.faceSeparationWithRing(theme),
                PlateGeometry.faceSeparation(theme),
            )
        }
        val rapport = StringBuilder()
        for ((dark, t) in sep) {
            rapport.append(
                "  %-7s visage ↔ verre : SANS levier %.2f:1 · anneau blanc %.2f:1 · OMBRE douce %.2f:1%n"
                    .format(if (dark) "sombre" else "clair", t.first, t.second, t.third),
            )
        }
        println("séparation du visage du repère (pire couleur de la palette) :\n$rapport")

        val (nuClair, anneauClair, ombreClair) = sep[false]!!
        val (nuSombre, anneauSombre, ombreSombre) = sep[true]!!
        // **Le témoin nommé** : le visage NU se confond avec le verre, dans les deux apparences.
        assertTrue(
            "un visage sans levier devrait se confondre avec le verre : clair %.2f:1, sombre %.2f:1 — " +
                "la dent ne mord pas".format(nuClair, nuSombre),
            nuClair < 2f && nuSombre < 2f,
        )
        // **VERT APRÈS, en apparence CLAIRE** — c'est là que l'anneau blanc ne servait à rien (un
        // anneau blanc sur un verre clair ne détache rien) et que le propriétaire voit ses cartes :
        // l'ombre douce, elle, sépare.
        assertTrue(
            ("en apparence claire, l'ombre douce ne fait pas mieux que l'anneau blanc refusé : " +
                "ombre %.2f:1 ≤ anneau %.2f:1 + 0,25\n%s").format(ombreClair, anneauClair, rapport),
            ombreClair > anneauClair + 0.25f,
        )
        // Et dans les **deux** apparences, l'ombre fait strictement mieux que **sans levier**.
        assertTrue(
            "l'ombre douce n'améliore pas la séparation contre le verre (clair %.2f > %.2f · sombre " +
                "%.2f > %.2f)".format(ombreClair, nuClair, ombreSombre, nuSombre),
            ombreClair > nuClair + 0.25f && ombreSombre > nuSombre + 0.25f,
        )
        // **LA RÉSERVE, écrite plutôt que maquillée.** L'ombre douce ne fait pas 3:1 sur **toute** la
        // palette : un visage de teinte moyenne sur un verre de teinte moyenne ne se sépare pas sans
        // un anneau quasi opaque — que le propriétaire a refusé. En apparence sombre, l'ancien anneau
        // blanc atteignait 3,04:1 (mesuré), c'est-à-dire qu'il **tenait** là ; il rognait le visage,
        // et le propriétaire l'a refusé en bloc. On le dit franchement.
        println(
            "réserve mesurée : l'ombre douce donne %.2f:1 au pire (clair) et %.2f:1 (sombre) — sous " +
                "3:1 pour les teintes moyennes. L'ancien anneau blanc tenait %.2f:1 en sombre (mais " +
                "il rognait le visage et le propriétaire l'a refusé) et ne servait à rien en clair " +
                "(%.2f:1).".format(ombreClair, ombreSombre, anneauSombre, anneauClair),
        )
    }
}
