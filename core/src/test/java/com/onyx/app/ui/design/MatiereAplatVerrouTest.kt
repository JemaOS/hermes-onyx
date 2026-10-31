package com.onyx.app.ui.design

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **Le verrou de la matière alignée sur la planche (« Galerie ») — un aplat, pas deux surfaces.**
 *
 * La planche d'acceptation est `out/maquettes-hermes-v2/{bots,fil}-{clair,sombre}.png`. Mesurée au
 * pixel, elle dessine trois choses que l'app ajoutait **en trop** :
 *
 *  1. **la plaque du bas** (compositeur + barre d'onglets) est un **seul aplat** — son corps vaut
 *     248–250 en clair et ~22 en sombre, séparé de la page par la **luminance seule** (fil-clair.png
 *     y=2000 : 254 → fil-sombre.png y=2000 : 64 côté app). L'app posait par-dessus une **lumière de
 *     tête** et un **liseré** (mesuré en sombre : halo 86 → 64, arête claire à L≈107) ;
 *  2. **une carte, une tuile** n'ont **ni ombre ni arête** ; l'app leur donnait une ombre
 *     ([GlassSurface.shadowDp]) et la même bande tête/pied que la plaque ;
 *  3. **le voile d'un dialogue** est **une** épaisseur (la planche : un seul `scrim`) ; l'app en
 *     empilait **deux** — le `dimAmount` de la fenêtre (≈ 0,6) **plus** [HouseSheetScrim] (55 %),
 *     soit 82 % mesurés.
 *
 * Ce test interroge les **mêmes** fonctions que l'écran (`glassFinishLit`, `glassSheenUsed`,
 * `glassShadeUsed`, `glassShadowUsed`) et lit la **porte du voile** dans `Frost.kt`. Il mord :
 * remettre `lit = true` sur la plaque, `3f` d'ombre sur une carte, ou un `return` devant
 * `setDimAmount(0f)` le fait échouer en nommant la surface et l'endroit.
 */
class MatiereAplatVerrouTest {

    // ---------------------------------------------------------------------------------------------
    // 1 · La plaque du bas — un seul aplat
    // ---------------------------------------------------------------------------------------------

    /** Les surfaces que la planche dessine **en aplat** : la plaque du bas, l'en-tête, les cartes. */
    private val flatBars = listOf(
        GlassSurface.THREAD_TOP_BAR,
        GlassSurface.TAB_BAR,
        GlassSurface.COMPOSER,
    )

    /** Les cartes et les tuiles : ni ombre, ni arête, ni bande. */
    private val flatCards = listOf(
        GlassSurface.SIDEBAR_TILE,
        GlassSurface.BOT_CARD,
        GlassSurface.LIST_ROW,
        GlassSurface.RESPONSE_BLOCK,
        GlassSurface.SEGMENTED,
    )

    @Test
    fun `la plaque du bas ne recoit ni filet ni halo`() {
        flatBars.forEach { s ->
            assertFalse(
                "$s doit être un aplat (lit=false) : la planche ne lui dessine ni filet ni halo",
                glassFinishLit(s),
            )
            for (dark in listOf(false, true)) {
                assertEquals(
                    "la lumière de tête de $s (sombre=$dark) doit valoir ZÉRO — c'est le halo " +
                        "que la planche n'a pas (app mesurée : 86 → 64 en sombre)",
                    0f,
                    glassSheenUsed(s, dark),
                    1e-6f,
                )
                assertEquals(
                    "l'ombre de pied de $s (sombre=$dark) doit valoir ZÉRO — c'est la seconde " +
                        "surface que la planche n'a pas",
                    0f,
                    glassShadeUsed(s, dark),
                    1e-6f,
                )
            }
        }
    }

    @Test
    fun `les cartes et les tuiles n'ont ni ombre ni arete`() {
        flatCards.forEach { s ->
            assertFalse(
                "$s doit être un aplat (lit=false) : la planche ne lui dessine ni ombre ni arête",
                glassFinishLit(s),
            )
            assertEquals(
                "l'ombre posée sous $s doit valoir ZÉRO — « chaque tuile montre sa bande » est " +
                    "le défaut relevé",
                0f,
                glassShadowUsed(s),
                1e-6f,
            )
            assertEquals(
                "l'ombre déclarée de $s doit être à 0 dp",
                0f,
                s.shadowDp,
                1e-6f,
            )
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 2 · Le contrôle : une surface de VERRE garde sa profondeur
    // ---------------------------------------------------------------------------------------------

    /**
     * Le verrou ne doit **pas** tout aplatir : le volet, les panneaux et la grande carte de matière
     * restent des **vitres** (ils recouvrent du contenu, et c'est là que le flou vit). Sans ce
     * contrôle, « tout mettre à zéro » passerait le test et tuerait le verre de l'app.
     */
    @Test
    fun `une surface de verre garde sa profondeur`() {
        val lit = listOf(
            GlassSurface.SIDEBAR_PANEL,
            GlassSurface.SHEET,
            GlassSurface.ALERT,
            GlassSurface.MENU,
            GlassSurface.HEADER_CAPSULE,
            GlassSurface.MATTER_CARD,
        )
        lit.forEach { s ->
            assertTrue("$s est une vitre : elle doit garder sa finition (lit=true)", glassFinishLit(s))
            assertTrue(
                "la lumière de tête de $s doit être strictement positive — sinon ce n'est plus une vitre",
                glassSheenUsed(s, dark = true) > 0f,
            )
        }
        assertTrue(
            "la feuille du bas doit garder son ombre (le panneau se pose sur la page)",
            glassShadowUsed(GlassSurface.SHEET) > 0f,
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 3 · Le voile du dialogue — une seule épaisseur
    // ---------------------------------------------------------------------------------------------

    /** Le voile de la maison : la **seule** épaisseur admise dans une feuille ou un dialogue. */
    @Test
    fun `le voile du dialogue est une seule epaisseur`() {
        // Compose quantifie l'alpha au pas de 8 bits : 55 % s'écrit 140/255 = 0,54902.
        assertEquals(
            "le voile de la maison vaut 55 % — c'est LA seule épaisseur peinte par nous",
            140f,
            HouseSheetScrim.alpha * 255f,
            0.5f,
        )
    }

    /**
     * **La porte du voile de plateforme.** `DialogWindowBlur` retire le `dimAmount` de la fenêtre ;
     * le premier correctif l'appelait derrière un `return` posé quand le flou est éteint, et ne
     * prenait donc jamais sur les passes sans flou (mesuré : L=41 **avant comme après**). Le test
     * lit le corps de la fonction et exige que l'appel soit **inconditionnel**.
     */
    @Test
    fun `le voile de plateforme est retire sans condition`() {
        val source = listOf(
            File("src/main/java/com/onyx/app/ui/design/Frost.kt"),
            File("core/src/main/java/com/onyx/app/ui/design/Frost.kt"),
        ).firstOrNull { it.exists() }?.readText()
            ?: error("Frost.kt introuvable depuis ${File(".").absolutePath}")

        val start = source.indexOf("fun DialogWindowBlur(")
        assertTrue("DialogWindowBlur doit exister dans Frost.kt", start >= 0)
        // Le corps de la fonction : jusqu'au prochain `@Composable`.
        val end = source.indexOf("@Composable", start).let { if (it < 0) source.length else it }
        // **Les commentaires sont retirés avant l'examen** : ils peuvent nommer la faute (le mot
        // `return` y figure volontairement), et ne sont pas du code.
        val body = source.substring(start, end)
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("//[^\\n]*"), " ")

        assertTrue(
            "DialogWindowBlur doit appeler setDimAmount(0f) : c'est ce qui retire le second voile",
            body.contains("setDimAmount(0f)"),
        )
        assertTrue(
            "DialogWindowBlur doit aussi clearFlags(FLAG_DIM_BEHIND) — un dimAmount à 0 avec le " +
                "drapeau posé redessine le voile sur certaines versions",
            body.contains("FLAG_DIM_BEHIND"),
        )
        assertFalse(
            "DialogWindowBlur ne doit plus SORTIR avant de retirer le voile : un `return` derrière " +
                "le niveau de flou laisse le dimAmount en place quand le flou est éteint — c'est " +
                "exactement pourquoi le premier correctif ne prenait pas (L=41 avant comme après)",
            body.contains("return"),
        )
    }
}
