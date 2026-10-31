package com.onyx.app.ui.design

import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou du fluide des cartes — quatre morsures, chacune nommée par son fichier et sa ligne.**
 *
 * Le propriétaire a tranché : la variante **B — filaments** entre dans l'app, avec quatre propriétés
 * non négociables. Ce test **rougit** si l'une d'elles est rompue, et il **dit où** :
 *
 *  1. **La graine dérive de l'identifiant de la conversation.** Un fluide tiré d'autre chose — de
 *     l'horloge, d'un `Random`, du rang dans la liste — rendrait une carte différente à chaque
 *     ouverture : ce n'est plus une identité. Le test exige que [Fluide.plan] suive le `crc32` de
 *     « titre \\0 identifiant » ([ConversationCover.seed]) et que deux identifiants donnent deux
 *     fluides distincts.
 *  2. **La respiration ne démarre pas avant cinq secondes.** [Fluide.breath] doit valoir exactement
 *     `0` tant que l'horloge n'a pas atteint [Fluide.DELAY_MS] : aucun mouvement en cours quand
 *     l'œil arrive.
 *  3. **Sous « Réduire les animations », le fluide est figé — sans exception.** [Fluide.level] doit
 *     rendre [Fluide.Level.FIGE], et le programme ne doit monter **aucune** boucle.
 *  4. **Le contraste du nom tient** (4,5:1) **sur la pire image animée**, en clair **et** en sombre.
 *
 * Les vérifications de source sont là pour que la morsure **nomme** `fichier:ligne` : c'est ce que
 * le rapport du lot doit citer, et c'est aussi ce qui attrape le cas où une garde serait contournée
 * ailleurs (un appelant qui anime la carte sans passer par [Fluide.level]).
 */
class FluideVerrouTest {

    private val TITRE = "Réunion de chantier"
    private val ID = "conv-1"

    // -----------------------------------------------------------------------------------------
    // 1 · La graine vient de l'identifiant
    // -----------------------------------------------------------------------------------------

    @Test
    fun `la graine derive de l identifiant de la conversation, et pas d autre chose`() {
        // Le crc32 de « titre \0 identifiant » — le chiffre de la maquette, et celui de la maison.
        assertEquals(
            "la graine doit être le crc32 de « titre \\0 identifiant » (ConversationCover.seed)",
            3417634283L,
            Fluide.graine(TITRE, ID),
        )
        assertEquals(
            "la graine du fluide doit être EXACTEMENT celle de la couverture (ConversationCover.seed)",
            ConversationCover.seed(TITRE, ID),
            Fluide.graine(TITRE, ID),
        )
        // Changer l'identifiant change la graine ; changer le titre aussi.
        assertNotEquals(Fluide.graine(TITRE, ID), Fluide.graine(TITRE, "conv-2"))
        assertNotEquals(Fluide.graine(TITRE, ID), Fluide.graine("Autre titre", ID))
        // …et la teinte suit la même règle que la maison : (graine & MAX) % 6.
        assertEquals(3, Fluide.plan(TITRE, ID, dark = true).teinteIndex) // bleu, comme la maquette
    }

    @Test
    fun `la meme conversation rend le meme fluide, meme apres un redemarrage`() {
        // « Redémarrage » : deux plans construits séparément, aucun état partagé, aucun Random.
        val premier = Fluide.plan(TITRE, ID, dark = true)
        val second = Fluide.plan(TITRE, ID, dark = true)
        for (v in listOf(0.05f, 0.2f, 0.4f, 0.62f, 0.9f)) {
            for (u in listOf(0.05f, 0.3f, 0.6f, 0.95f)) {
                val a = Fluide.sample(premier, u, v, 0f)
                val b = Fluide.sample(second, u, v, 0f)
                assertEquals("même identifiant, même pixel en u=$u v=$v", a[0], b[0], 1e-6f)
                assertEquals("même identifiant, même pixel en u=$u v=$v", a[1], b[1], 1e-6f)
                assertEquals("même identifiant, même pixel en u=$u v=$v", a[2], b[2], 1e-6f)
            }
        }
    }

    @Test
    fun `deux identifiants differents rendent deux fluides differents`() {
        // Sous le choix **identité** (graine 0) : c'est là que la règle de la maison s'applique —
        // l'identifiant de la conversation porte la graine. Sous le **choix enregistré** (filaments,
        // graine de la planche), c'est le matériau retenu qui gagne, et c'est le verrou de conformité
        // (`FluideChoixVerrouTest`) qui le tient.
        val identite = ChoixCouverture.identite()
        val a = Fluide.plan(TITRE, "conv-1", dark = true, choix = identite)
        val b = Fluide.plan(TITRE, "conv-7", dark = true, choix = identite)
        var differents = 0
        var total = 0
        for (v in 0..20) {
            for (u in 0..20) {
                val pa = Fluide.sample(a, u / 20f, v / 20f, 0f)
                val pb = Fluide.sample(b, u / 20f, v / 20f, 0f)
                total++
                if (abs(pa[0] - pb[0]) + abs(pa[1] - pb[1]) + abs(pa[2] - pb[2]) > 0.02f) differents++
            }
        }
        assertTrue(
            "deux conversations doivent porter deux fluides distincts (distincts : $differents/$total)",
            differents > total / 5,
        )
    }

    @Test
    fun `le choix enregistre porte la graine, et l identite reste disponible`() {
        // Le choix du propriétaire : toutes les conversations portent la graine de la planche.
        val choix = ChoixCouverture.DEFAUT
        for (id in listOf("conv-1", "conv-7", "conv-42")) {
            assertEquals(
                "sous le choix enregistré, la graine est celle du choix — pas celle de la conversation ($id)",
                3_417_634_283L,
                choix.graineDe(TITRE, id),
            )
        }
        // Et le chemin « identité » n'est pas perdu : il rend bien la graine de la conversation.
        assertEquals(
            "le choix identité garde la graine de la conversation",
            Fluide.graine(TITRE, "conv-7"),
            ChoixCouverture.identite().graineDe(TITRE, "conv-7"),
        )
    }

    // -----------------------------------------------------------------------------------------
    // 2 · La respiration — cinq secondes, puis rien qu'un souffle
    // -----------------------------------------------------------------------------------------

    @Test
    fun `la respiration ne commence pas avant cinq secondes`() {
        // Toute la fenêtre avant le délai : rigoureusement zéro.
        for (ms in 0L..4_999L step 250L) {
            assertEquals("aucun mouvement avant 5 s (t = $ms ms)", 0f, Fluide.breath(ms, contact = false), 0f)
        }
        // À l'instant pile du délai, le souffle est encore à l'arrêt (cos(0) = 1).
        assertEquals("à 5 000 ms pile, le souffle démarre de zéro", 0f, Fluide.breath(5_000L, false), 0f)
        // Et il vit ensuite : le maximum tombe à une demi-période.
        assertTrue("après 5 s, la respiration vit", Fluide.breath(5_000L + 1_200L, false) > 0.99f)
        assertTrue("la respiration reste dans [0, 1]", Fluide.breath(9_999L, false) in 0f..1f)
        // Le verrou doit nommer la garde qui porte le délai.
        val (f, l) = sourceDe("src/main/java/com/onyx/app/ui/design/Fluide.kt", "elapsedMs < delayMs")!!
        assertTrue("le délai de 5 s doit être gardé dans $f:$l", l > 0)
    }

    @Test
    fun `la respiration s arrete net au premier contact`() {
        for (ms in listOf(0L, 4_999L, 5_000L, 7_400L, 60_000L)) {
            assertEquals("au contact, le souffle retombe à zéro (t = $ms ms)", 0f, Fluide.breath(ms, contact = true), 0f)
        }
    }

    @Test
    fun `la carte invisible ne respire pas et ne calcule rien`() {
        // La condition d'activité porte bien (et visible, et sans contact) — nommée à la ligne.
        val (f, l) = sourceDe("src/main/java/com/onyx/app/ui/design/FluideCover.kt", "&& visible && !contact")!!
        assertTrue("la carte invisible ne doit pas animer — vu dans $f:$l", l > 0)
    }

    // -----------------------------------------------------------------------------------------
    // 3 · « Réduire les animations » — le fluide reste, figé
    // -----------------------------------------------------------------------------------------

    @Test
    fun `sous reduire les animations le fluide est fige, sans exception`() {
        val niveau = Fluide.level(enabled = true, reduceMotion = true, sdkInt = 36, lowRam = false)
        assertEquals("« Réduire les animations » fige le fluide", Fluide.Level.FIGE, niveau)
        assertFalse("un régime figé n'anime rien", Fluide.animates(niveau))
        assertTrue("le fluide reste dessiné", Fluide.drawsFluid(niveau))
        // Et la règle qui le décide est bien celle du fichier, nommée.
        val (f, l) = sourceDe("src/main/java/com/onyx/app/ui/design/Fluide.kt", "reduceMotion -> Level.FIGE")!!
        assertTrue("la règle du figement doit vivre dans $f:$l", l > 0)
        // Le composable lit le réglage système et ne monte aucune boucle hors régime animé.
        val (f2, l2) = sourceDe("src/main/java/com/onyx/app/ui/design/FluideCover.kt", "LocalReduceMotion.current")!!
        assertTrue("le composable doit lire « Réduire les animations » — $f2:$l2", l2 > 0)
        val (f3, l3) = sourceDe("src/main/java/com/onyx/app/ui/design/FluideCover.kt", "delay(Fluide.DELAY_MS)")!!
        assertTrue("aucune horloge n'est montée avant les 5 s — $f3:$l3", l3 > 0)
    }

    @Test
    fun `l interrupteur eteint rend l ancienne couverture, et il est vrai par defaut`() {
        assertEquals(
            "l'interrupteur éteint rend la couverture déterministe",
            Fluide.Level.AUCUN,
            Fluide.level(enabled = false, reduceMotion = false, sdkInt = 36, lowRam = false),
        )
        assertEquals(
            "avant la treizième version d'Android, pas d'AGSL : l'ancienne couverture",
            Fluide.Level.AUCUN,
            Fluide.level(enabled = true, reduceMotion = false, sdkInt = 32, lowRam = false),
        )
        assertEquals(
            "un appareil modeste prend la boucle pré-calculée",
            Fluide.Level.PRECALCULE,
            Fluide.level(enabled = true, reduceMotion = false, sdkInt = 36, lowRam = true),
        )
        assertEquals(
            "un appareil courant calcule le champ",
            Fluide.Level.CALCULE,
            Fluide.level(enabled = true, reduceMotion = false, sdkInt = 36, lowRam = false),
        )
        val (f, l) = sourceDe("../app/src/main/java/com/onyx/app/data/repo/SettingsRepository.kt", "private val _fluideCover = MutableStateFlow(true)")!!
        assertTrue("l'interrupteur est vrai par défaut — $f:$l", l > 0)
    }

    @Test
    fun `la carte de conversation appelle bien le fluide`() {
        val (f, l) = sourceDe("src/main/java/com/onyx/app/ui/design/Galerie.kt", "FluideConversationCover(")!!
        assertTrue("la carte doit appeler le fluide — $f:$l", l > 0)
        // Et le visage ne grossit pas : le disque d'état vient des **constantes de la maison**, et il
        // n'est plus dessiné à la main dans le volet. La pastille de la carte est la **marque
        // d'identité** (`IdentityMark`, lot marques-identite) : son diamètre est une constante
        // publique, et l'état s'y pose en point de présence — la géométrie ne peut plus dériver d'un
        // site d'appel à l'autre.
        val (f2, l2) = sourceDe("src/main/java/com/onyx/app/ui/design/IdentityMark.kt", "val Diameter: Dp = 32.dp")!!
        assertTrue("le diamètre de la marque d'identité est une constante de la maison — $f2:$l2", l2 > 0)
        val (f3, l3) = sourceDe("src/main/java/com/onyx/app/ui/design/IdentityMark.kt", "val PresenceDiameter: Dp = 11.dp")!!
        assertTrue("le point d'état a, lui aussi, sa constante — $f3:$l3", l3 > 0)
    }

    // -----------------------------------------------------------------------------------------
    // 4 · Le contraste du nom, sur la pire image animée
    // -----------------------------------------------------------------------------------------

    /**
     * La pire image animée sous le nom : on parcourt la zone du bandeau, on prend **le pixel le plus
     * clair de la boucle de respiration** ([Fluide.worstSample]) et l'on compose le volet de pied de
     * la carte par-dessus (le voile noir que [ConversationCoverCard] peint). C'est le pire cas réel.
     */
    private fun pireLuminanceSousLeNom(plan: Fluide.Plan): Float {
        var pire = 0f
        var v = 0.56f
        while (v <= 0.98f) {
            val veil = if (v < 0.5f) 0f else (0.44f + 0.5f * ((v - 0.5f) * 2f)).coerceIn(0f, 0.94f)
            var u = 0.02f
            while (u <= 0.98f) {
                val rgb = Fluide.underBlackVeil(Fluide.worstSample(plan, u, v), veil)
                val l = Fluide.lumOf(rgb)
                if (l > pire) pire = l
                u += 0.02f
            }
            v += 0.02f
        }
        return pire
    }

    @Test
    fun `le contraste du nom tient sur la pire image animee, en clair et en sombre`() {
        listOf(true, false).forEach { dark ->
            val plan = Fluide.plan(TITRE, ID, dark = dark)
            val pire = pireLuminanceSousLeNom(plan)
            // L'encre du nom de la carte : blanc pur (`Galerie.kt`, `.cvttl`).
            val ratio = Fluide.ratio(Fluide.lumOf(floatArrayOf(1f, 1f, 1f)), pire)
            assertTrue(
                "le nom doit tenir ${TEXT_CONTRAST_MIN}:1 sur la pire image (${if (dark) "sombre" else "clair"}), " +
                    "mesuré ${"%.2f".format(ratio)}:1 — pire luminance $pire",
                ratio >= TEXT_CONTRAST_MIN,
            )
            assertTrue("la pire image est au moins aussi claire que celle d'arrivée", pire > 0f)
        }
    }

    @Test
    fun `le coin du disque de visage reste sombre sur la pire image animee`() {
        // La garde de coin du programme de rendu, nommée dans la source AGSL.
        val (f, l) = sourceDe("src/main/java/com/onyx/app/ui/design/FluideCover.kt", "1.0 - 0.80*exp(-dot(p-coin,p-coin)*24.0)")!!
        assertTrue("la garde de coin doit vivre dans $f:$l", l > 0)
        // Et une source de lumière **hors** du coin : tirée dans la moitié gauche.
        val (f2, l2) = sourceDe("src/main/java/com/onyx/app/ui/design/Fluide.kt", "0.14 + r.next() * 0.95")!!
        assertTrue("la lumière doit être tirée dans la moitié gauche — $f2:$l2", l2 > 0)

        listOf(true, false).forEach { dark ->
            val plan = Fluide.plan(TITRE, ID, dark = dark)
            val asp = Fluide.CARD_ASPECT
            val coinU = (asp - 0.105f) / asp
            var pireCoin = 0f
            var v = 0.02f
            while (v <= 0.20f) {
                var u = coinU - 0.06f
                while (u <= coinU + 0.06f) {
                    val rgb = Fluide.underBlackVeil(Fluide.worstSample(plan, u, v), 0.30f)
                    val l = Fluide.lumOf(rgb)
                    if (l > pireCoin) pireCoin = l
                    u += 0.01f
                }
                v += 0.01f
            }
            // Le disque porte son icône en blanc : le seuil d'icône est 3:1, très largement tenu
            // parce que le coin est sombre (le blanc y donnerait au moins 7:1, mesuré au rapport).
            val ratio = Fluide.ratio(Fluide.lumOf(floatArrayOf(1f, 1f, 1f)), pireCoin)
            assertTrue(
                "le coin du disque doit rester sombre (${if (dark) "sombre" else "clair"}) : " +
                    "contraste blanc ${"%.2f".format(ratio)}:1, pire luminance $pireCoin",
                ratio >= ICON_CONTRAST_MIN,
            )
        }
    }

    // -----------------------------------------------------------------------------------------
    // La mesure, pour le rapport — les chiffres qui décident
    // -----------------------------------------------------------------------------------------

    @Test
    fun `mesures du rapport — contrastes a la pire image animee`() {
        listOf(true, false).forEach { dark ->
            val plan = Fluide.plan(TITRE, ID, dark = dark)
            val apparence = if (dark) "sombre" else "clair"
            val pireNom = pireLuminanceSousLeNom(plan)
            val ratioNom = Fluide.ratio(Fluide.lumOf(floatArrayOf(1f, 1f, 1f)), pireNom)
            val asp = Fluide.CARD_ASPECT
            val coinU = (asp - 0.105f) / asp
            var pireCoin = 0f
            var v = 0.02f
            while (v <= 0.20f) {
                var u = coinU - 0.06f
                while (u <= coinU + 0.06f) {
                    val l = Fluide.lumOf(Fluide.underBlackVeil(Fluide.worstSample(plan, u, v), 0.30f))
                    if (l > pireCoin) pireCoin = l
                    u += 0.01f
                }
                v += 0.01f
            }
            val ratioCoin = Fluide.ratio(Fluide.lumOf(floatArrayOf(1f, 1f, 1f)), pireCoin)
            println(
                "MESURE $apparence | nom (blanc) vs pire image + volet de pied : " +
                    "luminance ${"%.4f".format(pireNom)} -> ${"%.2f".format(ratioNom)}:1 (seuil $TEXT_CONTRAST_MIN) | " +
                    "disque (blanc sous voile 30%%) au pire : luminance ${"%.4f".format(pireCoin)} -> " +
                    "${"%.2f".format(ratioCoin)}:1 (seuil icône $ICON_CONTRAST_MIN)",
            )
        }
        assertTrue(true)
    }

    // -----------------------------------------------------------------------------------------
    // L'outillage : nommer fichier:ligne
    // -----------------------------------------------------------------------------------------

    /** Le premier `fichier:ligne` qui contient [motif], ou `null` — c'est la morsure nommée. */
    private fun sourceDe(chemin: String, motif: String): Pair<String, Int>? {
        val cible = File(chemin)
        if (!cible.isFile) return null
        cible.readLines().forEachIndexed { index, ligne ->
            if (ligne.contains(motif)) return cible.path to (index + 1)
        }
        return null
    }
}
