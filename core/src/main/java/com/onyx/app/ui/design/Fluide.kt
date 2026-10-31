package com.onyx.app.ui.design

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * **Le fluide d'une carte de conversation — la variante B retenue, portée dans l'app.**
 *
 * La maquette de choix vit hors du dépôt (`/home/john/qa/20261030-fluide-cartes/`) : le programme de
 * rendu y est écrit en GLSL (`maquettes/carte.html`), la matière a été arbitrée par le propriétaire
 * (variante **B — filaments**, avec le grain de **C**), et ce fichier en est le **port fidèle**,
 * valeurs et ordre de tirage compris, pour que la même conversation rende **le même fluide** dans la
 * maquette et dans l'app.
 *
 * Trois choses y vivent, toutes **pures** donc vérifiables sans appareil :
 *
 * 1. [plan] — le plan d'une conversation, tiré **une fois** de son identifiant (le `crc32` de
 *    « titre \\0 identifiant », la même fonction que [ConversationCover.seed]) par un `splitmix32`.
 *    Aucune horloge, aucun `Random` : la même conversation a **toujours** le même fluide, y compris
 *    après la mort du processus. C'est une **identité**, pas un tirage.
 * 2. [level] — la décision de rendu : programme de rendu sur la carte graphique, boucle
 *    pré-calculée, image figée, ou repli sur l'ancienne couverture. C'est cette fonction qui décide
 *    du coût, donc c'est elle que le test interroge.
 * 3. [breath] — la chronologie de la respiration : **immobile** tant que l'horloge n'a pas atteint
 *    [DELAY_MS] (5 s), **respiration lente** ensuite, **arrêt net** au premier contact.
 *
 * Et une quatrième, pour la mesure : [sample] — le **même** calcul qu'à l'écran, en Kotlin, pour que
 * le contraste de la pire image se **calcule** au lieu de s'estimer.
 */
object Fluide {

    // ---------------------------------------------------------------------------------------
    // 1 · La graine et le tirage — reproductibles, jamais l'horloge
    // ---------------------------------------------------------------------------------------

    /** Le nombre de teintes de la maison (les six de [ConversationCover.palettes]). */
    const val TEINTES = 6

    /** Les six teintes, dans l'ordre de [ConversationCover.palettes] — la teinte porte l'identité. */
    private val teintes: List<Pair<Float, Float>> = listOf(
        24f to 0.58f,   // ambre
        176f to 0.55f,  // jade
        33f to 0.50f,   // bois
        222f to 0.52f,  // bleu
        347f to 0.48f,  // rose
        89f to 0.34f,   // mousse
    )

    /** La graine : `crc32` de « titre \\0 identifiant », exactement [ConversationCover.seed]. */
    fun graine(title: String, id: String): Long = ConversationCover.seed(title, id)

    /**
     * Un `splitmix32` : la suite reproductible tirée de la graine. Mêmes appels → mêmes valeurs,
     * sur toutes les machines, parce que tout est fait sur des entiers 32 bits.
     */
    class Tirage(graine: Long) {
        private var a: Int = graine.toInt()
        fun next(): Double {
            a += 0x9E3779B9.toInt()
            var t = a xor (a ushr 16)
            t *= 0x21F0AAAD
            t = t xor (t ushr 15)
            t *= 0x735A2D97
            t = t xor (t ushr 15)
            return (t.toLong() and 0xFFFFFFFFL) / 4294967296.0
        }
    }

    // ---------------------------------------------------------------------------------------
    // 2 · Le plan — valeur pure, testable sans rendu
    // ---------------------------------------------------------------------------------------

    /**
     * Le plan complet d'un fluide. Tous les champs sont **déjà** les valeurs d'entrée du programme
     * de rendu : le composable n'a plus rien à décider, il ne fait que les poser en uniformes.
     */
    data class Plan(
        /** La graine de **famille** — celle du choix (la planche). Elle porte la teinte. */
        val graine: Long,
        /**
         * La graine du **champ** — celle de la **conversation**. C'est elle qui donne à chaque
         * conversation **sa** nappe : même famille visuelle (même teinte, même matière), **champ
         * différent**. Deux conversations ne rendent jamais la même image.
         */
        val graineChamp: Long,
        val teinteIndex: Int,
        val teinteH: Float,
        val dark: Boolean,
        /** Le **brouillard** : la matière lumineuse du fond de la carte (variante B). */
        val brumeProfonde: Color,
        val brumeClaire: Color,
        /** Le gain du ruban lumineux : plein en sombre, contenu en clair (comme la planche). */
        val ruban: Float,
        val profond: Color,
        val median: Color,
        val pale: Color,
        val lumiere: Color,
        val sol: Color,
        val voile: Color,
        /** Les trois vortex — position (x, y). */
        val v0: FloatArray,
        val v1: FloatArray,
        val v2: FloatArray,
        /** La source de lumière — **toujours dans la moitié gauche** (corrigé de la maquette). */
        val lum: FloatArray,
        /** La force des trois vortex. */
        val force: FloatArray,
        /** Leur rayon d'influence. */
        val rayon: FloatArray,
        /** Le nombre de filaments en travers de la carte (9 à 14). */
        val raies: Float,
        /** L'inclinaison de la nappe. */
        val incli: Float,
        val sat: Float,
        val haut: Float,
        val bas: Float,
    ) {
        /** La teinte, telle qu'un humain la nomme — pour le rapport. */
        val teinteNom: String
            get() = listOf("ambre", "jade", "bois", "bleu", "rose", "mousse")[teinteIndex]
    }

    /** Une couleur HSL → [Color] opaque, h 0-360, s/l 0-1 (`hsl()` de la maquette). */
    private fun hsl(h: Float, s: Float, l: Float): Color {
        val hh = ((h % 360f) + 360f) % 360f
        val ss = s.coerceIn(0f, 1f)
        val ll = l.coerceIn(0f, 1f)
        val c = (1f - abs(2f * ll - 1f)) * ss
        val x = c * (1f - abs((hh / 60f) % 2f - 1f))
        val m = ll - c / 2f
        val (r, g, b) = when {
            hh < 60f -> Triple(c, x, 0f)
            hh < 120f -> Triple(x, c, 0f)
            hh < 180f -> Triple(0f, c, x)
            hh < 240f -> Triple(0f, x, c)
            hh < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color(r + m, g + m, b + m, 1f)
    }

    /**
     * **Le plan déterministe de `(titre, identifiant)`, sous le choix enregistré.**
     *
     * La graine vient du **choix** ([ChoixCouverture.graine]) et **jamais** d'un défaut du code :
     * avec le choix du propriétaire (graine `3417634283`), toutes les cartes portent le fluide de la
     * planche — filaments, teinte bleu, 11 raies — **au pixel**. La valeur [ChoixCouverture.GRAINE_IDENTITE]
     * rend l'ancienne règle (la graine est le `crc32` de « titre \\0 identifiant »), qui reste
     * disponible et vérifiée. L'ordre des tirages est celui de la maquette (`planDe`) : vortex,
     * forces, rayons, lumière, raies, inclinaison.
     */
    fun plan(
        title: String,
        id: String,
        dark: Boolean,
        choix: ChoixCouverture = ChoixCouverture.DEFAUT,
    ): Plan {
        // **LA FAMILLE** : la graine du choix (la planche) — c'est elle qui porte la **teinte**.
        val fam = choix.graineDe(title, id)
        // **LE CHAMP** : la graine de la **conversation**. Chaque conversation a **sa** nappe, dans la
        // **même famille visuelle** (même teinte, même matière) : c'est ce que fait une liste où
        // chaque couverture est une image générative propre. Sous le choix « identité », la famille
        // EST la conversation (l'ancienne règle, conservée).
        val champ = if (choix.estIdentite) fam else graine(title, id)
        val r = Tirage(champ)
        val idx = if (choix.estIdentite) (((champ.toInt()) and Int.MAX_VALUE) % TEINTES) else choix.teinteIndex
        val (h, s) = teintes[idx.coerceIn(0, TEINTES - 1)]
                // **La SATURATION de la nappe** : relevee (0,78 -> 1,00 en sombre, 0,62 -> 0,80 en clair).
        // Assombrir le fond reduit mecaniquement la chroma ; sans ce relevement, le bleu-rouge
        // tombait sous le seuil de securite de +0,070. Il vaut +0,100 en sombre (planche +0,108)
        // et +0,075 en clair (planche +0,093).
        val sBase = s * (if (dark) 1.00f else 0.80f)

        val profond: Color
        val median: Color
        val pale: Color
        val lumiere: Color
        val sol: Color
        val voile: Color
        val sat: Float
        val brumeProfonde: Color
        val brumeClaire: Color
        val ruban: Float
        if (dark) {
            profond = hsl(h, sBase, 0.080f)
            median = hsl(h, sBase, 0.38f)
            pale = hsl(h, sBase * 0.72f, 0.72f)
            lumiere = hsl(h - 10f, sBase * 0.24f, 0.88f)
            sol = Color(11 / 255f, 13 / 255f, 18 / 255f, 1f)
            voile = Color(0f, 0f, 0f, 1f)
            sat = 0.86f
            // **Le brouillard bleu de la planche sombre, à son niveau LUMINEUX mesuré** : la planche
            // ne pose pas un aplat sombre, elle pose une **nappe claire gris-bleu** — le champ de
            // brume remplit tout le cadre, et les volutes y sont des **modulations douces**, non des
            // tubes lumineux. Le niveau de base est donc monté à celui de la planche (bande mesurée,
            // voile de pied compris) : la nappe passe d'un gris très sombre à un gris-bleu moyen.
            brumeProfonde = hsl(h, sBase * 1.00f, 0.070f)
            brumeClaire = hsl(h, sBase * 0.90f, 0.500f)
            ruban = 1.00f
        } else {
            profond = hsl(h, sBase, 0.740f)
            median = hsl(h, sBase, 0.60f)
            pale = hsl(h, sBase * 0.52f, 0.88f)
            lumiere = hsl(h, 0.22f, 0.86f)
            sol = Color(236 / 255f, 238 / 255f, 243 / 255f, 1f)
            voile = Color(247 / 255f, 247 / 255f, 249 / 255f, 1f)
            sat = 0.78f
            // **Le brouillard bleu clair de la planche claire** : une **nappe** claire, presque
            // uniforme, où les volutes se lisent comme des modulations. Le niveau est monté à celui
            // que la planche mesure sur sa bande (voile de pied compris).
            brumeProfonde = hsl(h, sBase * 1.00f, 0.740f)
            brumeClaire = hsl(h, sBase * 0.85f, 0.940f)
            ruban = 0.38f
        }

        // Trois vortex et une source de lumière — tirés de la graine, jamais de l'horloge.
        // L'ORDRE est celui de la maquette : v0, v1, v2, force, rayon, lumière, raies, inclinaison.
        val v0 = floatArrayOf((r.next() * 1.6 + 0.10).toFloat(), (r.next() * 0.85 + 0.05).toFloat())
        val v1 = floatArrayOf((r.next() * 1.6 + 0.10).toFloat(), (r.next() * 0.85 + 0.05).toFloat())
        val v2 = floatArrayOf((r.next() * 1.6 + 0.10).toFloat(), (r.next() * 0.85 + 0.05).toFloat())
        val force = floatArrayOf(
            ((r.next() * 2 - 1) * 0.55).toFloat(),
            ((r.next() * 2 - 1) * 0.45).toFloat(),
            ((r.next() * 2 - 1) * 0.35).toFloat(),
        )
        val rayon = floatArrayOf(
            (0.34 + r.next() * 0.26).toFloat(),
            (0.28 + r.next() * 0.24).toFloat(),
            (0.36 + r.next() * 0.28).toFloat(),
        )
        val lum = floatArrayOf((0.14 + r.next() * 0.95).toFloat(), (0.09 + r.next() * 0.28).toFloat())
        val raies = (9f + floor(r.next() * 6).toFloat())
        val incli = ((r.next() * 2 - 1) * 0.55).toFloat()

        return Plan(
            graine = fam, graineChamp = champ, teinteIndex = idx, teinteH = h, dark = dark,
            brumeProfonde = brumeProfonde, brumeClaire = brumeClaire, ruban = ruban,
            profond = profond, median = median, pale = pale, lumiere = lumiere,
            sol = sol, voile = voile,
            v0 = v0, v1 = v1, v2 = v2, lum = lum, force = force, rayon = rayon,
            raies = raies, incli = incli, sat = sat, haut = 0.38f, bas = 0.56f,
        )
    }

    // ---------------------------------------------------------------------------------------
    // 3 · La décision de rendu — la règle qui décide du coût
    // ---------------------------------------------------------------------------------------

    /** Les quatre régimes de rendu du fluide. */
    enum class Level {
        /** Le champ d'écoulement est **calculé sur la carte graphique** (AGSL), respirant. */
        CALCULE,

        /** Une **boucle pré-calculée** : les images de la respiration, cuites une fois, défilées. */
        PRECALCULE,

        /** Le fluide est **figé** à sa pose d'arrivée — « Réduire les animations », sans exception. */
        FIGE,

        /** Aucun fluide : l'ancienne couverture déterministe, ou l'interrupteur éteint. */
        AUCUN,
    }

    /** Vrai pour les régimes qui font bouger quelque chose — jamais sous « Réduire les animations ». */
    fun animates(level: Level): Boolean = level == Level.CALCULE || level == Level.PRECALCULE

    /** Vrai si le régime dessine le fluide (peu importe s'il bouge). */
    fun drawsFluid(level: Level): Boolean = level != Level.AUCUN

    /**
     * La règle, **pure** : le programme de rendu sur la carte graphique quand tout est là ; la boucle
     * pré-calculée sur un appareil modeste ; l'image figée sous « Réduire les animations » ; et
     * l'ancienne couverture quand le fluide est éteint ou que le système ne sait pas l'exécuter
     * (AGSL demande la **treizième version d'Android**).
     */
    fun level(enabled: Boolean, reduceMotion: Boolean, sdkInt: Int, lowRam: Boolean): Level = when {
        !enabled -> Level.AUCUN
        sdkInt < 33 -> Level.AUCUN
        reduceMotion -> Level.FIGE
        lowRam -> Level.PRECALCULE
        else -> Level.CALCULE
    }

    // ---------------------------------------------------------------------------------------
    // 4 · La respiration — les trois temps
    // ---------------------------------------------------------------------------------------

    /** **Cinq secondes** sans contact avant que la respiration ne commence. */
    const val DELAY_MS = 5_000L

    /** La période de la respiration — lente : 2,4 s pour un aller-retour (`souffle` de la maquette). */
    const val PERIOD_MS = 2_400.0

    /**
     * Le souffle, **pur** : `0` strictement tant que l'horloge n'a pas atteint [DELAY_MS], puis une
     * respiration lente en cosinus ; `0` **net** dès le premier contact. Aucun mouvement n'est en
     * cours à l'arrivée : la valeur de départ est exactement l'arrêt.
     */
    fun breath(elapsedMs: Long, contact: Boolean): Float = breath(elapsedMs, contact, DELAY_MS)

    /** La même règle, avec un délai explicite — c'est celle que le verrou interroge. */
    fun breath(elapsedMs: Long, contact: Boolean, delayMs: Long): Float {
        if (contact) return 0f
        if (elapsedMs < delayMs) return 0f
        val t = (elapsedMs - delayMs).toDouble()
        return (0.5 - 0.5 * cos(2.0 * Math.PI * t / PERIOD_MS)).toFloat()
    }

    // ---------------------------------------------------------------------------------------
    // 4bis · Le cycle de dérive — la forme bouge, et la boucle se referme
    // ---------------------------------------------------------------------------------------

    /**
     * **La période du cycle de dérive : sept secondes et deux dixièmes.** C'est le **seul** rythme
     * de la carte : [PERIOD_MS] (2,4 s) le divise **exactement trois fois**, si bien que la lumière
     * et la forme se referment ensemble sur la **même** période. Un mouvement plus court se lit
     * comme un tic ; plus long, on ne le voit pas sur une carte de 192 dp.
     */
    const val CYCLE_MS = 7_200.0

    /** Combien de respirations tiennent dans un cycle — l'entier qui rend la boucle refermable. */
    const val SOUFFLES_PAR_CYCLE = 3

    /**
     * **La phase du cycle, dans `[0,1[`** — pure, et **périodique** : `phase(0)` et `phase(1)` valent
     * la même chose, donc le dessin du début et celui de la fin du cycle sont **identiques au
     * pixel**. C'est ce qui rend le raccord **invisible** : il n'y a pas de raccord. Aucun mouvement
     * avant [DELAY_MS], comme la respiration.
     */
    fun phase(elapsedMs: Long, contact: Boolean = false, delayMs: Long = DELAY_MS): Float {
        if (contact) return 0f
        if (elapsedMs < delayMs) return 0f
        val t = (elapsedMs - delayMs).toDouble() % CYCLE_MS
        return (t / CYCLE_MS).toFloat()
    }

    /** L'amplitude de la dérive des vortex, en unités de carte : **douce** — la forme se déforme. */
    const val DERIVE = 0.055f

    /** L'amplitude du glissement des filaments, en raies : moins d'une raie, donc jamais de saut. */
    const val DECALAGE = 0.85f

    /**
     * **Un centre qui dérive sur un cercle**, parcouru **une fois par cycle** : la fin du cycle et
     * son départ se rejoignent au pixel près.
     */
    fun derive(centre: FloatArray, amp: Float, ph0: Float, phase: Float): FloatArray {
        val a = (2.0 * Math.PI * (phase + ph0)).toFloat()
        return floatArrayOf(centre[0] + amp * cos(a), centre[1] + amp * sin(a))
    }

    /**
     * **Le glissement de la nappe** : la phase du potentiel, en raies, sur un aller-retour en
     * sinus — donc une vitesse nulle aux extrémités, et une valeur **identique** en `phase = 0` et
     * `phase = 1`.
     */
    fun decalage(phase: Float): Float =
        (DECALAGE * sin(2.0 * Math.PI * phase)).toFloat()

    /** La respiration rangée dans le cycle : trois respirations par cycle, donc une boucle unique. */
    fun souffleDuCycle(phase: Float): Float =
        (0.5 - 0.5 * cos(2.0 * Math.PI * phase * SOUFFLES_PAR_CYCLE)).toFloat()

    // ---------------------------------------------------------------------------------------
    // 5 · Le calcul du pixel — le même que le programme de rendu, en Kotlin
    // ---------------------------------------------------------------------------------------

    private fun h11(n: Float): Float {
        val s = sin(n * 127.1f + 311.7f) * 43758.5453123f
        return s - floor(s)
    }

    private fun vn(px: Float, py: Float): Float {
        val ix = floor(px); val iy = floor(py)
        val fx = px - ix; val fy = py - iy
        // Interpolation QUINTIQUE (C2) : les plis du cube faisaient un contour polygonal.
        val ux = fx * fx * fx * (fx * (fx * 6f - 15f) + 10f)
        val uy = fy * fy * fy * (fy * (fy * 6f - 15f) + 10f)
        val a = h11(ix + iy * 57f)
        val b = h11(ix + 1f + iy * 57f)
        val c = h11(ix + (iy + 1f) * 57f)
        val d = h11(ix + 1f + (iy + 1f) * 57f)
        return mix(mix(a, b, ux), mix(c, d, ux), uy)
    }

    private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun fbm(px: Float, py: Float): Float {
        var s = 0f
        var a = 0.55f
        var x = px; var y = py
        repeat(4) {
            s += a * vn(x, y)
            x = x * 2.02f + 11.3f; y = y * 2.02f + 7.7f
            a *= 0.44f
        }
        return s / 1.03f
    }

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /**
     * **Le champ d'écoulement — sa FORME est FIGÉE.**
     *
     * Le propriétaire a corrigé le mouvement : *« la forme doit être stable, c'est la lumière qui
     * circule le long des rubans »*. Les trois vortex ne dérivent donc **plus** : la `phase` est
     * reçue mais **n'entre pas** dans le champ. Le dessin garde exactement la même silhouette à
     * chaque instant, et c'est [courant] qui porte tout le mouvement.
     */
    private fun flux(plan: Plan, px: Float, py: Float, phase: Float): FloatArray {
        var sx = 0f; var sy = 0f
        fun add(cx: Float, cy: Float, f: Float, r: Float) {
            val dx = px - cx; val dy = py - cy
            val e = exp(-(dx * dx + dy * dy) / (r * r))
            sx += f * e * (-dy)
            sy += f * e * (dx)
        }
        add(plan.v0[0], plan.v0[1], plan.force[0], plan.rayon[0])
        add(plan.v1[0], plan.v1[1], plan.force[1], plan.rayon[1])
        add(plan.v2[0], plan.v2[1], plan.force[2], plan.rayon[2])
        return floatArrayOf(sx, sy)
    }

    private fun dens(plan: Plan, px: Float, py: Float, phase: Float): Float {
        val f = flux(plan, px * 1.15f, py * 1.15f, phase)
        val wx = px + 0.68f * f[0]; val wy = py + 0.68f * f[1]
        return smoothstep(0.14f, 0.90f, fbm(wx * 1.95f, wy * 1.95f))
    }

    /**
     * **LE CHAMP VOLUMÉTRIQUE — la lumière naît de la crête de ce champ.**
     *
     * C'est le remplaçant du `potentiel` de raies : au lieu des **lignes de niveau d'une rampe**
     * (`(1,25·y + inclinaison·x)·raies`), qui donnaient un champ de **lignes fines, parallèles et
     * régulièrement espacées**, on lit les crêtes d'un **bruit lisse de basse fréquence** (quelques
     * octaves de `fbm`), **entraîné par les vortex figés** ([flux]). L'iso-valeur médiane de ce
     * champ décrit **deux à quatre masses larges et irrégulières** — des volutes de courbure
     * variable, dont une qui se recourbe en crochet — et **aucune n'est parallèle aux autres**.
     *
     * C'est la même matière que la planche de référence, dont le dessin était précisément la crête
     * d'un tel champ (`pow(1−|2d−1|, 30)` sur `d = dens(p)`), et non un potentiel de raies.
     */
    private fun champ(plan: Plan, px: Float, py: Float): Float {
        // Le champ LISSÉ : le centre et ses quatre voisins — une passe de flou qui aplatit les plis
        // de l'iso-valeur qui restaient (le contour polygonal).
        val e = E_CHAMP
        var s = 0.25f * champBrut(plan, px, py)
        s += 0.125f * (
            champBrut(plan, px + e, py) + champBrut(plan, px - e, py) +
                champBrut(plan, px, py + e) + champBrut(plan, px, py - e)
            )
        s += 0.0625f * (
            champBrut(plan, px + e, py + e) + champBrut(plan, px + e, py - e) +
                champBrut(plan, px - e, py + e) + champBrut(plan, px - e, py - e)
            )
        return s
    }

    /** Le champ **brut** — la crête d'un seul bruit, entraîné par les vortex figés. */
    private fun champBrut(plan: Plan, px: Float, py: Float): Float {
        val f = flux(plan, px * 1.02f, py * 1.02f, 0f)
        val wx = px + CHAMP_WARP * f[0]
        val wy = py + CHAMP_WARP * f[1]
        return fbm(wx * CHAMP_FREQ, wy * CHAMP_FREQ)
    }

    /**
     * **LE COURANT — la lumière qui CIRCULE dans la forme.**
     *
     * La forme est **figée** ([champ] ne reçoit pas la phase) ; le mouvement est **tout entier** dans
     * cette tête brillante suivie d'une **longue queue**, et dans le **halo qui la suit** — le halo
     * doit avancer avec la tête, sinon l'œil lit un scintillement au lieu d'un courant.
     *
     * `s = 1,25·px − incli·py` balaie la carte ; la tête se décale d'une volute à l'autre
     * (`fract(⌊c·12⌋·0,37)`, où `c` est **le champ** — donc le long des volutes). Tout est
     * **périodique** en `phase`, donc le dessin de fin de cycle est **identique au pixel** à celui
     * du début : la boucle n'a pas de raccord.
     *
     * Rend `[cour, halo]`.
     */
    fun courant(plan: Plan, px: Float, py: Float, c: Float, phase: Float): FloatArray {
        val s = 1.25f * px - plan.incli * py
        val tete = frac(phase + frac(floor(c * 12f) * 0.37f))
        val dd = frac(s * 0.62f - tete + 0.5f) - 0.5f
        val head = exp(-dd * dd / (2f * COURANT_HEAD * COURANT_HEAD))
        val tail = if (dd < 0f) exp(dd / COURANT_TAIL) else 0f
        val halo = exp(-dd * dd / (2f * COURANT_HALO * COURANT_HALO))
        return floatArrayOf(head + 0.5f * tail, halo)
    }

    /** La partie fractionnaire, **toujours dans `[0,1[`** — même pour un argument négatif. */
    private fun frac(x: Float): Float = x - floor(x)

    // ---------------------------------------------------------------------------------------
    // 5bis · Les constantes du dessin — le champ volumétrique et ses crêtes
    // ---------------------------------------------------------------------------------------

    /** L'écart du courant entre sa tête et sa queue. La tête est **large** : une lueur, pas un trait. */
    private const val COURANT_HEAD = 0.20f
    private const val COURANT_HALO = 0.26f
    private const val COURANT_TAIL = 0.30f

    /** La fréquence du **champ volumétrique** — basse : deux à quatre masses sur la carte. */
    const val CHAMP_FREQ = 2.60f

    /** L'entraînement du champ par les vortex figés. */
    const val CHAMP_WARP = 0.35f

    /**
     * **LA PORTÉE DU FLOU DU CHAMP** — 0,034 → **0,10**. C'est LE levier qui **amincit** la
     * veine : la largeur mesurée passe de 18,2 px à **9,2** (planche 5,9) **sans** ajouter de
     * grain (verrou `grain du fond` : 3,99, seuil 4,50 ; la planche 3,10) ni de courbure
     * (0,207, seuil 0,26). Monter la fréquence du bruit obtenait la même largeur mais portait le
     * grain à 12,4 : c'est ce flou-là qui est juste.
     */
    const val E_CHAMP = 0.10f

    /**
     * **LA FRÉQUENCE DU PLI FIN** — la modulation de détail (inchangée : 8,0). C'est
     * [E_CHAMP] (la portée du flou du champ) qui **amincit** la veine, et non cette fréquence :
     * monter la fréquence amenait le grain du fond à 12,4 (verrou : 4,50).
     */
    const val D2_FREQ = 8.0f

    /** La demi-largeur du **pli** de la veine : de l'effilé ([BAND_MIN]) à l'épais ([BAND_MAX]). */
    const val BAND_MIN = 0.014f
    const val BAND_MAX = 0.038f

    /** Le dosage de l'épaisseur variable du pli le long de la nappe (sous la grande nappe). */
    const val EPAISSEUR = 1.40f

    /** La portée du **halo** de la nappe : une lueur très large sur les masses les plus denses. */
    const val HALO_W = 0.60f

    /** Conservée : la transition du pli reste large — c'est un dégradé, pas un seuil. */
    const val CRETE_ARETE = 1.30f

    /** Les gains : le halo (large, doux) et le relief de la grande nappe. */
    const val HALO_GAIN = 0.07f
    const val CORPS_GAIN = 0.42f

    /**
     * **LA NAPPE.** La densité du champ pilote le **TON** sur une transition **large** : de
     * [NAPPE_LO] à [NAPPE_HI], presque toute la plage de la densité. C'est ce qui fait une **brume**
     * (un ton continu) et non un **isocontour** (une courbe, donc un bord — un néon).
     */
    const val NAPPE_LO = 0.32f
    const val NAPPE_HI = 0.68f
    const val NAPPE_GAIN = 0.55f
    const val RELIEF_GAIN = 0.10f

    /**
     * **L'ÉCHELLE DU PLI.** La demi-largeur de la modulation, en multiples de la maille du bruit :
     * une veine est un **creux doux** dans la nappe (des dizaines de pixels de transition), jamais
     * l'iso-valeur d'un champ (2 à 3 px — un trait).
     */
    const val VEINE_ECHELLE = 20.0f

    /** **LA LUEUR** — large et diffuse : la source, son noyau, et le courant qui parcourt la nappe. */
    const val LUEUR_GAIN = 0.06f
    const val NOYAU_GAIN = 0.80f

    /** **LA PORTÉE DU FOYER** — la source éclaire, elle ne blanchit plus : la portée passe de
     *  4,0 à **10,0** et le gain de la lueur de 0,50 à **0,06**. La bande sombre retombe de 105,0
     *  à **84,9** (planche 84,4) et le bleu−rouge revient à celui de la planche (+0,108). */
    const val FOYER_K = 10.0f
    const val COURANT_GAIN = 0.26f

    /** Le genou de la **compression douce** : au-delà, la retombée devient graduelle (plus de clip). */
    const val DOUX_K = 0.92f

    /** Le grain de la planche : un pas de trois pixels, ramené à son niveau mesuré. */
    const val GRAIN_BASE = 0.978f
    const val GRAIN_AMP = 0.008f

    /** Le poids de la **nappe de brume** lumineuse (`brumeClaire`) et l'éclaircissement du corps. */
    const val HAZE_FACTOR = 0.42f
    const val MIX_MEDIAN = 0.22f

    /**
     * **Le pixel du fluide**, en (u, v) ∈ [0,1]² — `u` de gauche à droite, `v` **de haut en bas**
     * (comme `uv` à l'écran). [souffle] est le souffle de la respiration. C'est la copie fidèle du
     * `main()` du programme de rendu : c'est ce qui permet de **mesurer** le contraste au lieu de
     * l'estimer, et c'est aussi ce qui donne la pire image animée.
     */
    fun sample(
        plan: Plan,
        u: Float,
        v: Float,
        souffle: Float,
        voileA: Float = 0f,
        phase: Float = 0f,
    ): FloatArray {
        val asp = CARD_ASPECT
        val px = u * asp
        val py = v
        // La source de lumière est **figée**, comme la forme : seule la lueur du courant circule.
        val src = plan.lum
        val dlx = px - src[0]; val dly = py - src[1]
        val foyer = exp(-(dlx * dlx + dly * dly) * FOYER_K)
        val noyau = exp(-(dlx * dlx + dly * dly) * 11.0f)
        val fg = flux(plan, px * 0.9f, py * 0.9f, phase)
        val fg2 = flux(plan, px * 0.8f, py * 0.8f, phase)
        val grande = 0.62f * fbm((px + 0.34f * fg[0]) * 0.44f, (py + 0.34f * fg[1]) * 0.44f) +
            0.38f * fbm((px + 0.20f * fg2[0]) * 0.22f, (py + 0.20f * fg2[1]) * 0.22f)
        val g = smoothstep(0.16f, 0.90f, grande)

        // ---- LE TON DE LA NAPPE : un CHAMP CONTINU, jamais un isocontour ----------------------
        // Le défaut nommé : la crête était EXTRAITE comme l'iso-valeur du champ (`abs(dcre-0.5)`) —
        // une COURBE, donc un **trait brillant à bords nets** : un néon (des îlots, des anneaux, des
        // points isolés). Ici le **TON** est une fonction **MONOTONE** des densités : aucune valeur
        // de gris n'est privilégiée, donc il n'y a **aucun anneau**, **aucun contour fermé**.
        val c = champ(plan, px, py)
        val d2 = fbm(px * D2_FREQ + 3.1f, py * D2_FREQ + 7.7f)
        // L'échelle du pli : le détail fin vit dans la grande nappe (g) ; la portée est LARGE.
        val poids = (BAND_MIN + (BAND_MAX - BAND_MIN) * (g * EPAISSEUR).coerceIn(0f, 1f)) * VEINE_ECHELLE
        val dn = mix(c, d2, poids.coerceIn(0f, 0.70f))
        // LA MISE EN TON est LINÉAIRE (et non une courbe en S) : aucune valeur de gris n'est
        // privilégiée, donc aucune courbe de niveau raide — aucune arête, aucun anneau visible.
        val ton = ((dn - NAPPE_LO) / (NAPPE_HI - NAPPE_LO)).coerceIn(0f, 1f)
        // LE TON : deux gris-bleus PROCHES — la veine et le fond de la nappe, à faible contraste.
        var col = mix3(plan.profond, plan.brumeClaire, ton)
        col = mix3(col, plan.pale, RELIEF_GAIN * g)

        // ---- LA LUEUR : large et diffuse (la source, le halo, puis le courant) -----------------
        val rub = plan.ruban
        val srcw = 0.45f + 0.55f * foyer
        col = mix3(col, plan.lumiere, LUEUR_GAIN * foyer * rub * srcw)
        col = mix3(col, plan.lumiere, NOYAU_GAIN * noyau * rub * srcw)
        // LE HALO : une lueur très large sur les masses les plus denses — monotone en densité, donc
        // sans anneau (aucun niveau de gris n'est privilégié).
        val uc = 1f - c
        val hal = smoothstep(HALO_W, 0.0f, uc)
        val halo = hal * hal
        col = mix3(col, plan.pale, HALO_GAIN * halo * rub)
        val co = courant(plan, px, py, c, phase)
        col = mix3(col, plan.lumiere, COURANT_GAIN * co[0] * rub)
        col = mix3(col, plan.lumiere, COURANT_GAIN * 0.5f * co[1] * rub)

        // La matière de la planche — le grain de pas de trois pixels — dont le **contraste respire**.
        val grainGain = 0.85f + 0.15f * cos((2.0 * Math.PI * phase).toFloat())
        val fin = h11(floor(u * CARD_W_PX / 3f) + floor(v * CARD_H_PX / 3f) * 57f)
        col = mul3(col, GRAIN_BASE + GRAIN_AMP * fin * grainGain)

        // la garde de coin : la lumière sortie du coin du disque
        val coinX = asp - 0.105f; val coinY = 0.105f
        val dx = px - coinX; val dy = py - coinY
        col = mul3(col, 1f - 0.80f * exp(-(dx * dx + dy * dy) * 24f))

        val lum = 0.299f * col[0] + 0.587f * col[1] + 0.114f * col[2]
        col = mix3(floatArrayOf(lum, lum, lum), col, plan.sat)
        val solF = mix(1.10f, 0.70f, v)
        val sol = mul3(plan.sol, solF)
        val masque = 1f - smoothstep(plan.haut, plan.bas, v)
        col = mix3(sol, col, masque)
        val rx = (u - 0.5f) * 1f; val ry = (v - 0.42f) * 0.85f
        val vig = 1f - 0.26f * smoothstep(0.45f, 1.25f, kotlin.math.sqrt(rx * rx + ry * ry) * 1.6f)
        col = mul3(col, vig)
        val a = if (v < 0.5f) mix(0f, 0.44f, v * 2f) else mix(0.44f, 0.94f, (v - 0.5f) * 2f)
        // Dans l'app, le voile de pied est **peint par la carte** ([ConversationCoverCard]) : le
        // programme de rendu sort donc sans voile (`voileA = 0`), exactement le régime « brute » de
        // la maquette. Le voile de la carte se compose par-dessus, et c'est lui que la mesure pose.
        col = mix3(col, plan.voile, a * voileA)
        // LA COMPRESSION DOUCE avant le clip : le cœur reste blanc, la retombée est graduelle.
        col = doux(col)
        return floatArrayOf(col[0].coerceIn(0f, 1f), col[1].coerceIn(0f, 1f), col[2].coerceIn(0f, 1f))
    }

    /** La **compression douce** : au-dessus du genou [DOUX_K], la retombée devient graduelle — le
     *  cœur reste blanc sans jamais être écrêté à plat. */
    private fun doux(c: FloatArray): FloatArray {
        val k = DOUX_K
        return floatArrayOf(
            douxCh(c[0], k), douxCh(c[1], k), douxCh(c[2], k),
        )
    }

    private fun douxCh(x: Float, k: Float): Float {
        val lo = min(x, k)
        val hi = max(x - k, 0f)
        return lo + (1f - k) * (1f - exp(-hi / (1f - k)))
    }

    private fun mix3(a: Color, b: Color, t: Float) =
        floatArrayOf(mix(a.red, b.red, t), mix(a.green, b.green, t), mix(a.blue, b.blue, t))

    private fun mix3(a: FloatArray, b: Color, t: Float) =
        floatArrayOf(mix(a[0], b.red, t), mix(a[1], b.green, t), mix(a[2], b.blue, t))

    private fun mix3(a: FloatArray, b: FloatArray, t: Float) =
        floatArrayOf(mix(a[0], b[0], t), mix(a[1], b[1], t), mix(a[2], b[2], t))

    private fun mul3(c: Color, s: Float) = floatArrayOf(c.red * s, c.green * s, c.blue * s)
    private fun mul3(c: FloatArray, s: Float) = floatArrayOf(c[0] * s, c[1] * s, c[2] * s)

    private fun add3(a: FloatArray, b: FloatArray) =
        floatArrayOf(a[0] + b[0], a[1] + b[1], a[2] + b[2])

    /**
     * **Le gain du ruban — retiré, et remplacé par la lumière des crêtes.**
     *
     * La passe d'avant posait un **ruban** dont la lumière était réglée par ce gain `0,50` : il
     * fallait compenser un corps `pow 4` et une jupe `exp` que la planche n'a pas. Le dessin ne pose
     * **plus de ruban** : il pose la **crête d'un champ volumétrique** ([champ]), et ses deux gains
     * sont désormais [HALO_GAIN] (le halo large) et [CORPS_GAIN] (le corps de la volute). Ces deux
     * gains ont été réglés **sur la planche** : sur la bande v 0,05→0,45, la luminance de la zone
     * lumineuse vaut **158,8** contre **144,6** sur la planche (l'app d'avant, 95,5), et la largeur
     * de crête **21,4 px** contre **20,8** (l'app d'avant, 5,2 — un trait).
     *
     * La constante reste déclarée à `1,0` pour que le nom ne mente pas s'il est cité ailleurs.
     */
    const val RIBBON_GAIN = 1.0f

    /** Le rapport largeur/hauteur de la carte de conversation (379 × 192, celui de la maquette). */
    const val CARD_W_PX = 379f
    const val CARD_H_PX = 192f
    const val CARD_ASPECT = CARD_W_PX / CARD_H_PX

    /**
     * **La pire image animée** : le maximum pixel à pixel sur **le cycle entier** — la lumière
     * (trois respirations) et la forme (une dérive) confondues. C'est l'image la plus claire que le
     * fluide puisse produire ; c'est elle qui décide du contraste, pas une moyenne, et c'est aussi
     * elle qui sert de témoin de mouvement : deux phases distinctes doivent donner deux images
     * distinctes. Douze phases, comme la maquette (`pireImage`).
     */
    fun worstSample(plan: Plan, u: Float, v: Float): FloatArray {
        var best = sample(plan, u, v, 0f)
        var bestLum = lumOf(best)
        val n = 12
        for (k in 1 until n) {
            val phase = k.toFloat() / n
            val px = sample(plan, u, v, souffleDuCycle(phase), phase = phase)
            val l = lumOf(px)
            if (l > bestLum) { best = px; bestLum = l }
        }
        return best
    }

    /** La luminance relative WCAG d'un triplet linéaire 0-1. */
    fun lumOf(rgb: FloatArray): Float {
        fun ch(c: Float): Float {
            val v = c.coerceIn(0f, 1f)
            return if (v <= 0.04045f) v / 12.92f else Math.pow(((v + 0.055f) / 1.055).toDouble(), 2.4).toFloat()
        }
        return 0.2126f * ch(rgb[0]) + 0.7152f * ch(rgb[1]) + 0.0722f * ch(rgb[2])
    }

    /** Le rapport de contraste WCAG entre deux luminances relatives. */
    fun ratio(l1: Float, l2: Float): Double {
        val hi = max(l1, l2).toDouble(); val lo = min(l1, l2).toDouble()
        return (hi + 0.05) / (lo + 0.05)
    }

    /** Compose une couleur (0-1) sous un voile noir d'opacité [alpha] (`mix(col, noir, a)`). */
    fun underBlackVeil(rgb: FloatArray, alpha: Float): FloatArray =
        floatArrayOf(rgb[0] * (1f - alpha), rgb[1] * (1f - alpha), rgb[2] * (1f - alpha))
}
