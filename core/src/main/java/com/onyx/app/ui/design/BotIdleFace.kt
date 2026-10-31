package com.onyx.app.ui.design

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * La vie de repos d'un visage de bot : ce qu'il fait quand personne ne lui parle.
 *
 * Le propriétaire a demandé « des animations aléatoires en boucle pour égayer », et « quand je le
 * touche, il est content ». Deux règles tiennent tout ce fichier :
 *
 * - **Des pulsions courtes, jamais un mouvement continu.** Un visage qui bouge en permanence
 *   détourne l'œil du titre ; ce qui fait « vivant », c'est une pulsion brève (un clignement, un
 *   regard, un souffle) suivie d'une **pause**. Les pulsions sont donc séparées par une attente de
 *   4 à 9 secondes, et le visage est **strictement immobile** entre deux.
 * - **Rien de nouveau dans la créature.** Aucune couleur, aucune forme, aucun trait : tout passe
 *   par les nombres que [BotPose] porte déjà (regard, clignement, inclinaison, sourcils,
 *   paupières, respiration) et, pour le micro-sourire, par la pose « content » du moteur
 *   ([BotMood.PLEASED]).
 *
 * Le hasard est **une fonction pure à graine** : deux indices donnent toujours le même pouls, donc
 * la séquence se vérifie en test (intervalles, poids, reproductibilité) au lieu de dépendre d'un
 * `Random` caché dans la composition.
 */

/**
 * Une animation de repos : ce qu'elle s'appelle, **son poids** dans le tirage et sa durée.
 *
 * Le poids fait la fréquence : le clignement (40) est cinq fois plus fréquent que le hochement (8),
 * parce que c'est lui qui donne l'impression de vie, alors qu'un hochement répété deviendrait un
 * tic.
 */
enum class BotIdleCue(val frLabel: String, val weight: Int, val durationMillis: Long) {
    /** Les yeux se ferment et se rouvrent : le plus fréquent, et le plus bref. */
    BLINK("clignement", 40, 170L),

    /** Le regard part de côté et revient, avec un rien d'inclinaison. */
    GLANCE("regard", 22, 760L),

    /** Un souffle : le corps monte, redescend et se repose. */
    BREATH("respiration", 18, 2_600L),

    /** Un sourire minuscule, par la pose « content » du moteur. */
    SMILE("micro-sourire", 12, 1_200L),

    /** Un petit hochement de la tête : le plus rare. */
    NOD("hochement", 8, 900L),
    ;

    companion object {
        /** La somme des poids — **100**, pour qu'un poids se lise comme un pourcentage. */
        val totalWeight: Int = entries.sumOf { it.weight }

        /**
         * L'animation qu'un tirage de `0` (inclus) à [totalWeight] (exclu) donne : on balaie les
         * poids déclarés, du plus lourd au plus léger, et l'animation dont la tranche contient le
         * tirage l'emporte. C'est ce qui rend le poids **exactement** respecté, et non approché.
         */
        fun forDraw(draw: Int): BotIdleCue {
            var rest = Math.floorMod(draw, totalWeight)
            for (cue in entries) {
                if (rest < cue.weight) return cue
                rest -= cue.weight
            }
            return entries.last()
        }
    }
}

/**
 * Un pouls du visage au repos : son animation, l'attente qui l'a précédé, et quand il commence.
 *
 * [startMillis] est la somme des attentes depuis l'entrée à l'écran ; il n'est porté que par
 * [BotIdle.schedule], un pouls pris isolément partant de zéro.
 */
data class BotIdlePulse(
    val index: Int,
    val cue: BotIdleCue,
    val gapMillis: Long,
    val durationMillis: Long,
    val startMillis: Long = 0L,
) {
    /** L'instant où le pouls se termine. */
    val endMillis: Long get() = startMillis + durationMillis
}

/**
 * Les pouls d'un visage au repos, tirés au sort **à partir d'une graine**.
 *
 * Tout est fonction pure : `pulse(graine, 3)` rend toujours le même pouls, sur n'importe quel
 * appareil, et [schedule] en donne la suite avec l'instant de chacun. La composition, elle, ne
 * fait qu'appeler [pulse] dans l'ordre — elle ne tire rien au sort elle-même.
 */
object BotIdle {

    /** Le plus court intervalle entre deux pouls : quatre secondes. */
    const val MIN_GAP_MS = 4_000L

    /** Le plus long : neuf secondes. */
    const val MAX_GAP_MS = 9_000L

    /** L'amplitude de la respiration : 0,9 sur l'échelle −1 … +1 de [BotPose.breath]. */
    const val BREATH_AMPLITUDE = 0.9f

    /** Le degré d'inclinaison d'un hochement, de part et d'autre du repos. */
    const val NOD_DEGREES = 4f

    /** De combien le regard part de côté, en unités de [BotPose.look]. */
    const val GLANCE_REACH = 0.85f

    /** Ce qu'un micro-sourire ajoute de sourcil — un rien, jamais un masque. */
    const val NOD_BROW = 0.18f

    /** Le pouls [index] : son animation, sa durée et l'attente qui le précède. */
    fun pulse(seed: Long, index: Int): BotIdlePulse {
        val cue = cue(seed, index)
        return BotIdlePulse(
            index = index,
            cue = cue,
            gapMillis = gapMillis(seed, index),
            durationMillis = cue.durationMillis,
        )
    }

    /** L'attente avant le pouls [index] : de [MIN_GAP_MS] à [MAX_GAP_MS], **bornes incluses**. */
    fun gapMillis(seed: Long, index: Int): Long {
        val span = MAX_GAP_MS - MIN_GAP_MS + 1L
        return MIN_GAP_MS + draw(seed, index, GAP_SALT).mod(span.toULong()).toLong()
    }

    /** L'animation du pouls [index], tirée **selon son poids**. */
    fun cue(seed: Long, index: Int): BotIdleCue =
        BotIdleCue.forDraw(draw(seed, index, CUE_SALT).mod(BotIdleCue.totalWeight.toULong()).toInt())

    /** Les [count] premiers pouls, avec l'instant du début de chacun (la somme des attentes). */
    fun schedule(seed: Long, count: Int): List<BotIdlePulse> {
        var at = 0L
        return List(count) { index ->
            val pulse = pulse(seed, index)
            at += pulse.gapMillis
            pulse.copy(startMillis = at)
        }
    }

    /**
     * Le pouls d'un instant, ou `null` si le visage est au repos : [schedule] avance jusqu'au
     * dernier pouls commencé avant `millis`, et le rend s'il n'est pas encore terminé.
     */
    fun pulseAt(seed: Long, millis: Long, horizon: Int = 4_096): BotIdlePulse? {
        var at = 0L
        for (index in 0 until horizon) {
            val pulse = pulse(seed, index)
            at += pulse.gapMillis
            // Ce pouls et tous les suivants commencent après l'instant demandé.
            if (at > millis) return null
            val current = pulse.copy(startMillis = at)
            if (millis < current.endMillis) return current
        }
        return null
    }

    /**
     * La graine d'un visage : son nom et son code, hachés comme le tirage d'un nom de bot.
     *
     * Deux bots ne tirent donc pas la même séquence — un clignement n'est pas un rythme partagé —
     * et un même bot rejoue exactement la même vie d'une session à l'autre, ce qui rend la capture
     * et le test reproductibles.
     */
    fun seedFor(name: String, faceCode: String? = null): Long =
        fnv1a("$name\u0000${faceCode.orEmpty()}")

    /** Le même FNV-1a 64 bits que le tirage des visages, sur les octets UTF-8. */
    private fun fnv1a(seed: String): Long {
        var hash = 14_695_981_039_346_656_037uL
        for (byte in seed.toByteArray(Charsets.UTF_8)) {
            hash = (hash xor (byte.toInt() and 0xFF).toULong()) * 1_099_511_628_211uL
        }
        return hash.toLong()
    }

    /** Le sel du tirage de l'attente, distinct de celui de l'animation. */
    private const val GAP_SALT = 0x51ED27uL

    /** Le sel du tirage de l'animation. */
    private const val CUE_SALT = 0xA17B3uL

    /** Le tirage du couple (graine, index, sel) : un mélange d'usage général, reproductible. */
    private fun draw(seed: Long, index: Int, salt: ULong): ULong =
        splitmix(seed.toULong() xor splitmix(index.toULong() * 0x9E3779B97F4A7C15UL + salt))

    /** `splitmix64` : chaque bit du résultat dépend de tous ceux de l'entrée. */
    private fun splitmix(value: ULong): ULong {
        var z = value + 0x9E3779B97F4A7C15UL
        z = (z xor (z shr 30)) * 0xBF58476D1CE4E5B9UL
        z = (z xor (z shr 27)) * 0x94D049BB133111EBUL
        return z xor (z shr 31)
    }
}

/** L'état du visage pendant un pouls : ce qu'il **dessine** ([pose]) et ce qu'il **est** ([mood]). */
data class BotIdleFrame(val pose: BotPose, val mood: BotMood)

/**
 * L'état d'un visage à un instant d'un pouls : `progress` va de 0 (le début) à 1 (la fin).
 *
 * [tilt] est l'inclinaison **propre** du visage : une pulsion qui penche la tête doit partir de
 * celle du bot, jamais de zéro, sinon le visage se redresserait d'un coup au début de chaque
 * hochement.
 *
 * Aux deux bouts, la pose rendue est **exactement** celle du repos (tout à zéro, l'inclinaison du
 * visage inchangée) : un pouls ne laisse donc jamais le visage décalé, et deux pouls qui se
 * suivent ne sautent pas.
 */
fun botIdleFrame(
    pulse: BotIdlePulse,
    progress: Float,
    tilt: Float = 0f,
    base: BotMood = BotMood.IDLE,
): BotIdleFrame {
    val at = progress.coerceIn(0f, 1f)
    // Aux deux bouts, la pose **est** celle du repos, au bit près : le calcul laisserait sinon
    // traîner un `1e-16` (ou un regard à zéro mais non nul), et le visage sauterait d'un rien au
    // premier et au dernier dessin du pouls.
    if (at <= 0f || at >= 1f) return BotIdleFrame(BotPose(tilt = tilt), base)
    // Une vague : nulle aux deux bouts, au sommet au milieu. C'est la forme d'un clignement et
    // d'un hochement — rien ne subsiste après.
    val wave = sin(PI * at).toFloat()
    // Un aller-retour complet : positif (l'inspiration), puis négatif (le corps retombe).
    val round = sin(2f * PI * at).toFloat()
    return when (pulse.cue) {
        // Les yeux se ferment à demi-course et se rouvrent : 0,9 de fermeture, comme le moteur.
        BotIdleCue.BLINK -> BotIdleFrame(BotPose(blink = wave, tilt = tilt), base)

        BotIdleCue.GLANCE -> {
            // Le regard part tôt, tient un instant, puis revient tôt : un aller franc, un retour
            // franc. Le côté alterne d'un pouls à l'autre, sinon le visage lorgnerait toujours du
            // même œil.
            val side = if (pulse.index % 2 == 0) 1f else -1f
            val out = when {
                at < 0.22f -> easeOut(at / 0.22f)
                at < 0.62f -> 1f
                else -> easeOut((1f - at) / 0.38f)
            }
            BotIdleFrame(
                BotPose(
                    look = Offset(BotIdle.GLANCE_REACH * side * out, 0.25f * out),
                    tilt = tilt + 1.2f * side * out,
                ),
                base,
            )
        }

        // Le souffle : le corps monte, redescend sous son repos, puis revient.
        BotIdleCue.BREATH -> BotIdleFrame(
            BotPose(breath = BotIdle.BREATH_AMPLITUDE * round, tilt = tilt),
            base,
        )

        // Le micro-sourire : c'est la pose « content » du moteur qui s'en charge (les yeux
        // sourient, la bouche sourit, le sourcil se lève) — rien n'est redessiné ici.
        BotIdleCue.SMILE -> BotIdleFrame(
            BotPose(tilt = tilt),
            if (base == BotMood.IDLE) BotMood.PLEASED else base,
        )

        // Le petit hochement : la tête salue d'un côté et revient, avec un rien de sourcil.
        BotIdleCue.NOD -> BotIdleFrame(
            BotPose(tilt = tilt + BotIdle.NOD_DEGREES * wave, brow = BotIdle.NOD_BROW * wave),
            base,
        )
    }
}

/** Un départ franc : la courbe monte vite puis s'aplatit (`easeOutCubic` du moteur). */
private fun easeOut(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    val rest = 1f - x
    return 1f - rest * rest * rest
}

/**
 * Ce qu'un appui sur le visage déclenche : la pose tenue, combien de temps, et si le corps rebondit.
 *
 * Sous « Réduire les animations », la pose s'échange **d'un coup** (`bounce = false`) : le visage
 * change d'expression, mais rien ne se déplace.
 */
data class BotPressReaction(val mood: BotMood, val durationMillis: Long, val bounce: Boolean)

/** Les deux réactions d'un appui sur le visage : le contentement, et la surprise. */
object BotPress {

    /** L'appui court tient une pose de contentement un peu plus d'une demi-seconde. */
    const val TAP_MS = 620L

    /** L'appui long tient la seconde pose plus longtemps. */
    const val LONG_PRESS_MS = 1_150L

    /** L'amplitude du rebond, en unités de [BotPose.breath]. */
    const val BOUNCE_AMPLITUDE = 1f

    /**
     * La seconde pose : la **surprise** — sourcils hauts, paupières ouvertes, regard vers vous.
     *
     * C'est la pose d'écoute du moteur, prise telle quelle : le clignement d'un seul œil n'existe
     * pas dans [BotPose] (le clignement ferme les deux), et en inventer un demanderait un trait
     * nouveau, ce que la scène s'interdit.
     */
    val SURPRISE = BotMood.LISTENING

    /** La réaction d'un appui : le contentement, ou la surprise d'un appui long. */
    fun reaction(long: Boolean, reduceMotion: Boolean = false): BotPressReaction =
        if (long) {
            BotPressReaction(SURPRISE, LONG_PRESS_MS, bounce = !reduceMotion)
        } else {
            BotPressReaction(BotMood.PLEASED, TAP_MS, bounce = !reduceMotion)
        }
}

/**
 * Le petit rebond d'une réaction : le corps monte d'un coup (le premier quart), puis retombe au
 * repos. `progress` va de 0 à 1 ; aux deux bouts la pose est celle du repos, donc le retour au
 * repos ne saute pas.
 */
fun botPressPose(progress: Float, tilt: Float = 0f): BotPose {
    val at = progress.coerceIn(0f, 1f)
    // Comme un pouls de repos : aux deux bouts, la pose est **exactement** celle du repos.
    if (at <= 0f || at >= 1f) return BotPose(tilt = tilt)
    return BotPose(
        breath = BotPress.BOUNCE_AMPLITUDE * sin(PI * sqrt(at)).toFloat(),
        tilt = tilt,
    )
}
