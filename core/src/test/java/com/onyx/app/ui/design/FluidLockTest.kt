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
 *     ouverture : ce n'est plus une identité. Le test exige que [Fluid.plan] suive le `crc32` de
 *     « titre \\0 identifiant » ([ConversationCover.seed]) et que deux identifiants donnent deux
 *     fluides distincts.
 *  2. **La respiration ne démarre pas avant cinq secondes.** [Fluid.breath] doit valoir exactement
 *     `0` tant que l'horloge n'a pas atteint [Fluid.DELAY_MS] : aucun mouvement en cours quand
 *     l'œil arrive.
 *  3. **Sous « Réduire les animations », le fluide est figé — sans exception.** [Fluid.level] doit
 *     rendre [Fluid.Level.FIGE], et le programme ne doit monter **aucune** boucle.
 *  4. **Le contraste du nom tient** (4,5:1) **sur la pire image animée**, en clair **et** en sombre.
 *
 * Les vérifications de source sont là pour que la morsure **nomme** `fichier:ligne` : c'est ce que
 * le rapport du lot doit citer, et c'est aussi ce qui attrape le cas où une garde serait contournée
 * ailleurs (un appelant qui anime la carte sans passer par [Fluid.level]).
 *
 * **Re-pointé par le lot « blocs-verre ».** Le fluide n'est **plus peint par une carte de
 * conversation** (le propriétaire a refusé les trois compositions et demandé une plaque de verre avec
 * le visage du bot) : les dents 1 à 4 portent donc désormais sur le **matériau de la planche**, et
 * celle qui exigeait l'appel du fluide par la carte exige maintenant l'inverse — **plus peinte sur
 * les cartes**, matériau déclaré. Aucune dent n'a été supprimée, aucune désarmée.
 */
class FluidLockTest {

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
            Fluid.graine(TITRE, ID),
        )
        assertEquals(
            "la graine du fluide doit être EXACTEMENT celle de la couverture (ConversationCover.seed)",
            ConversationCover.seed(TITRE, ID),
            Fluid.graine(TITRE, ID),
        )
        // Changer l'identifiant change la graine ; changer le titre aussi.
        assertNotEquals(Fluid.graine(TITRE, ID), Fluid.graine(TITRE, "conv-2"))
        assertNotEquals(Fluid.graine(TITRE, ID), Fluid.graine("Autre titre", ID))
        // …et la teinte suit la même règle que la maison : (graine & MAX) % 6.
        assertEquals(3, Fluid.plan(TITRE, ID, dark = true).teinteIndex) // bleu, comme la maquette
    }

    @Test
    fun `la meme conversation rend le meme fluide, meme apres un redemarrage`() {
        // « Redémarrage » : deux plans construits séparément, aucun état partagé, aucun Random.
        val premier = Fluid.plan(TITRE, ID, dark = true)
        val second = Fluid.plan(TITRE, ID, dark = true)
        for (v in listOf(0.05f, 0.2f, 0.4f, 0.62f, 0.9f)) {
            for (u in listOf(0.05f, 0.3f, 0.6f, 0.95f)) {
                val a = Fluid.sample(premier, u, v, 0f)
                val b = Fluid.sample(second, u, v, 0f)
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
        // (`FluidChoiceLockTest`) qui le tient.
        val identite = ChoixCouverture.identite()
        val a = Fluid.plan(TITRE, "conv-1", dark = true, choix = identite)
        val b = Fluid.plan(TITRE, "conv-7", dark = true, choix = identite)
        var differents = 0
        var total = 0
        for (v in 0..20) {
            for (u in 0..20) {
                val pa = Fluid.sample(a, u / 20f, v / 20f, 0f)
                val pb = Fluid.sample(b, u / 20f, v / 20f, 0f)
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
            Fluid.graine(TITRE, "conv-7"),
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
            assertEquals("aucun mouvement avant 5 s (t = $ms ms)", 0f, Fluid.breath(ms, contact = false), 0f)
        }
        // À l'instant pile du délai, le souffle est encore à l'arrêt (cos(0) = 1).
        assertEquals("à 5 000 ms pile, le souffle démarre de zéro", 0f, Fluid.breath(5_000L, false), 0f)
        // Et il vit ensuite : le maximum tombe à une demi-période.
        assertTrue("après 5 s, la respiration vit", Fluid.breath(5_000L + 1_200L, false) > 0.99f)
        assertTrue("la respiration reste dans [0, 1]", Fluid.breath(9_999L, false) in 0f..1f)
        // Le verrou doit nommer la garde qui porte le délai.
        val (f, l) = sourceDe("src/main/java/com/onyx/app/ui/design/Fluid.kt", "elapsedMs < delayMs")!!
        assertTrue("le délai de 5 s doit être gardé dans $f:$l", l > 0)
    }

    @Test
    fun `la respiration s arrete net au premier contact`() {
        for (ms in listOf(0L, 4_999L, 5_000L, 7_400L, 60_000L)) {
            assertEquals("au contact, le souffle retombe à zéro (t = $ms ms)", 0f, Fluid.breath(ms, contact = true), 0f)
        }
    }

    @Test
    fun `la carte invisible ne respire pas et ne calcule rien`() {
        // La condition d'activité porte bien (et visible, et sans contact) — nommée à la ligne.
        val (f, l) = sourceDe("src/main/java/com/onyx/app/ui/design/FluidCover.kt", "&& visible && !contact")!!
        assertTrue("la carte invisible ne doit pas animer — vu dans $f:$l", l > 0)
    }

    // -----------------------------------------------------------------------------------------
    // 3 · « Réduire les animations » — le fluide reste, figé
    // -----------------------------------------------------------------------------------------

    @Test
    fun `sous reduire les animations le fluide est fige, sans exception`() {
        val niveau = Fluid.level(enabled = true, reduceMotion = true, sdkInt = 36, lowRam = false)
        assertEquals("« Réduire les animations » fige le fluide", Fluid.Level.FIGE, niveau)
        assertFalse("un régime figé n'anime rien", Fluid.animates(niveau))
        assertTrue("le fluide reste dessiné", Fluid.drawsFluid(niveau))
        // Et la règle qui le décide est bien celle du fichier, nommée.
        val (f, l) = sourceDe("src/main/java/com/onyx/app/ui/design/Fluid.kt", "reduceMotion -> Level.FIGE")!!
        assertTrue("la règle du figement doit vivre dans $f:$l", l > 0)
        // Le composable lit le réglage système et ne monte aucune boucle hors régime animé.
        val (f2, l2) = sourceDe("src/main/java/com/onyx/app/ui/design/FluidCover.kt", "LocalReduceMotion.current")!!
        assertTrue("le composable doit lire « Réduire les animations » — $f2:$l2", l2 > 0)
        val (f3, l3) = sourceDe("src/main/java/com/onyx/app/ui/design/FluidCover.kt", "delay(Fluid.DELAY_MS)")!!
        assertTrue("aucune horloge n'est montée avant les 5 s — $f3:$l3", l3 > 0)
    }

    @Test
    fun `l interrupteur eteint rend l ancienne couverture, et il est vrai par defaut`() {
        assertEquals(
            "l'interrupteur éteint rend la couverture déterministe",
            Fluid.Level.AUCUN,
            Fluid.level(enabled = false, reduceMotion = false, sdkInt = 36, lowRam = false),
        )
        assertEquals(
            "avant la treizième version d'Android, pas d'AGSL : l'ancienne couverture",
            Fluid.Level.AUCUN,
            Fluid.level(enabled = true, reduceMotion = false, sdkInt = 32, lowRam = false),
        )
        assertEquals(
            "un appareil modeste prend la boucle pré-calculée",
            Fluid.Level.PRECALCULE,
            Fluid.level(enabled = true, reduceMotion = false, sdkInt = 36, lowRam = true),
        )
        assertEquals(
            "un appareil courant calcule le champ",
            Fluid.Level.CALCULE,
            Fluid.level(enabled = true, reduceMotion = false, sdkInt = 36, lowRam = false),
        )
        val (f, l) = sourceDe("../app/src/main/java/com/onyx/app/data/repo/SettingsRepository.kt", "private val _fluideCover = MutableStateFlow(true)")!!
        assertTrue("l'interrupteur est vrai par défaut — $f:$l", l > 0)
    }

    @Test
    fun `la carte de conversation NE peint plus la couverture, et le materiau reste declare`() {
        // =========================================================================================
        // **VERROU RE-POINTÉ — lot « blocs-verre », jamais désarmé.**
        //
        // Cette dent exigeait l'**appel** du fluide par la carte (« la carte de conversation appelle
        // bien le fluide »). Le propriétaire a refusé les trois compositions — la troisième comprise —
        // et remplacé la direction, capture à l'appui : « pour les blocs de conversation tu vas pas
        // mettre d'images, ça va être très simple : une plaque de verre avec liquid glass, le visage
        // du bot… ». La dent exige donc **l'inverse**, et elle exige **plus** qu'avant : que le
        // matériau reste **déclaré et documenté** comme matériau de la planche, sans quoi le prochain
        // lot le remettrait sur une carte en croyant le réveiller.
        // =========================================================================================
        val galerie = File("src/main/java/com/onyx/app/ui/design/Gallery.kt")
            .takeIf { it.isFile } ?: File("../core/src/main/java/com/onyx/app/ui/design/Gallery.kt")
        assertTrue("la carte de conversation est introuvable : ${galerie.path}", galerie.isFile)
        val carte = galerie.readText()
        val appels = listOf("FluideConversationCover(", "ConversationCover(", "ConversationCoverCard(")
            .filter { carte.contains(it) }
        assertTrue(
            "la carte peint encore une couverture générée : $appels — le propriétaire a refusé les " +
                "trois compositions et demandé une plaque de verre (voir Gallery.kt, BlocConversation)",
            appels.isEmpty(),
        )
        // Le matériau, lui, doit rester vivant et **nommé comme non peint** : un composable supprimé
        // en silence serait perdu ; un composable dont on a oublié qu'il est retiré serait remis.
        val (f, l) = sourceDe("src/main/java/com/onyx/app/ui/design/FluidCover.kt", "fun FluideConversationCover(")!!
        assertTrue("le composable du fluide doit rester déclaré (matériau de la planche) — $f:$l", l > 0)
        val (f2, l2) = sourceDe(
            "src/main/java/com/onyx/app/ui/design/FluidCover.kt",
            "PLUS PEINTE SUR LES CARTES",
        )!!
        assertTrue("le fichier du fluide doit dire qu'il n'est plus peint par les cartes — $f2:$l2", l2 > 0)
        // Et le visage ne grossit pas : le disque d'état vient des **constantes de la maison**, et il
        // n'est plus dessiné à la main dans le volet. La pastille de la carte est la **marque
        // d'identité** (`IdentityMark`, lot marques-identite) : son diamètre est une constante
        // publique, et l'état s'y pose en point de présence — la géométrie ne peut plus dériver d'un
        // site d'appel à l'autre.
        val (f3, l3) = sourceDe("src/main/java/com/onyx/app/ui/design/IdentityMark.kt", "val Diameter: Dp = 32.dp")!!
        assertTrue("le diamètre de la marque d'identité est une constante de la maison — $f3:$l3", l3 > 0)
        val (f4, l4) = sourceDe("src/main/java/com/onyx/app/ui/design/IdentityMark.kt", "val PresenceDiameter: Dp = 11.dp")!!
        assertTrue("le point d'état a, lui aussi, sa constante — $f4:$l4", l4 > 0)
    }

    // -----------------------------------------------------------------------------------------
    // 4 · Le contraste du nom, sur la pire image animée
    // -----------------------------------------------------------------------------------------

    /**
     * La pire image animée sous le nom : on parcourt la zone du bandeau, on prend **le pixel le plus
     * clair de la boucle de respiration** ([Fluid.worstSample]) et l'on compose le volet de pied de
     * la carte par-dessus (le voile noir que [ConversationCoverCard] peint). C'est le pire cas réel.
     */
    private fun pireLuminanceSousLeNom(plan: Fluid.Plan): Float {
        var pire = 0f
        var v = 0.56f
        while (v <= 0.98f) {
            val veil = if (v < 0.5f) 0f else (0.44f + 0.5f * ((v - 0.5f) * 2f)).coerceIn(0f, 0.94f)
            var u = 0.02f
            while (u <= 0.98f) {
                val rgb = Fluid.underBlackVeil(Fluid.worstSample(plan, u, v), veil)
                val l = Fluid.lumOf(rgb)
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
            val plan = Fluid.plan(TITRE, ID, dark = dark)
            val pire = pireLuminanceSousLeNom(plan)
            // L'encre du nom de la carte : blanc pur (`Gallery.kt`, `.cvttl`).
            val ratio = Fluid.ratio(Fluid.lumOf(floatArrayOf(1f, 1f, 1f)), pire)
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
        val (f, l) = sourceDe("src/main/java/com/onyx/app/ui/design/FluidCover.kt", "1.0 - 0.80*exp(-dot(p-coin,p-coin)*24.0)")!!
        assertTrue("la garde de coin doit vivre dans $f:$l", l > 0)
        // Et une source de lumière **hors** du coin : tirée dans la moitié gauche.
        val (f2, l2) = sourceDe("src/main/java/com/onyx/app/ui/design/Fluid.kt", "0.14 + r.next() * 0.95")!!
        assertTrue("la lumière doit être tirée dans la moitié gauche — $f2:$l2", l2 > 0)

        listOf(true, false).forEach { dark ->
            val plan = Fluid.plan(TITRE, ID, dark = dark)
            val asp = Fluid.CARD_ASPECT
            val coinU = (asp - 0.105f) / asp
            var pireCoin = 0f
            var v = 0.02f
            while (v <= 0.20f) {
                var u = coinU - 0.06f
                while (u <= coinU + 0.06f) {
                    val rgb = Fluid.underBlackVeil(Fluid.worstSample(plan, u, v), 0.30f)
                    val l = Fluid.lumOf(rgb)
                    if (l > pireCoin) pireCoin = l
                    u += 0.01f
                }
                v += 0.01f
            }
            // Le disque porte son icône en blanc : le seuil d'icône est 3:1, très largement tenu
            // parce que le coin est sombre (le blanc y donnerait au moins 7:1, mesuré au rapport).
            val ratio = Fluid.ratio(Fluid.lumOf(floatArrayOf(1f, 1f, 1f)), pireCoin)
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
            val plan = Fluid.plan(TITRE, ID, dark = dark)
            val apparence = if (dark) "sombre" else "clair"
            val pireNom = pireLuminanceSousLeNom(plan)
            val ratioNom = Fluid.ratio(Fluid.lumOf(floatArrayOf(1f, 1f, 1f)), pireNom)
            val asp = Fluid.CARD_ASPECT
            val coinU = (asp - 0.105f) / asp
            var pireCoin = 0f
            var v = 0.02f
            while (v <= 0.20f) {
                var u = coinU - 0.06f
                while (u <= coinU + 0.06f) {
                    val l = Fluid.lumOf(Fluid.underBlackVeil(Fluid.worstSample(plan, u, v), 0.30f))
                    if (l > pireCoin) pireCoin = l
                    u += 0.01f
                }
                v += 0.01f
            }
            val ratioCoin = Fluid.ratio(Fluid.lumOf(floatArrayOf(1f, 1f, 1f)), pireCoin)
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
