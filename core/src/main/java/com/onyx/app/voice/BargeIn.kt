package com.onyx.app.voice

import java.text.Normalizer

/**
 * Repérer qu'on a commencé à parler pendant que Onyx parle, **au niveau du micro seul**.
 *
 * Comme. Il est nourri de chaque mesure du micro pendant qu'une réponse
 * se lit, et répond une fois: maintenant.
 *
 * Le niveau, pas les mots, exprès. Le moteur de reconnaissance ne doit pas écouter pendant que la
 * voix de Onyx est dans l'air: l'entendre écrire ce qu'il vient de dire le faisait s'interrompre
 * tout seul. Le niveau dit seulement « quelque chose se dit »; la réponse est alors **suspendue**
 * et le moteur écoute une pièce calme pour savoir si c'était des mots ([VoiceSession]).
 *
 * Les chiffres viennent d'un téléphone 15 Pro Max (2026-10-07): haut-parleur à mi- ou plein volume,
 * annulation d'écho système allumée, le micro est quasi muet pendant la lecture (≈ -95 dBFS);
 * une voix à bout de bras arrive à -14…-21 dBFS quelques dixièmes de seconde par mot. Sur un
 * casque, rien ne filtre le micro: la pièce s'entend (52…-68), la personne à -26…-36, et rien de
 * la réponse. Donc: assez fort pour l'endroit où est le micro, assez longtemps, et pas dans les
 * premiers instants d'une réponse.
 *
 * Classe pure: aucune dépendance Android, donc éprouvable sur JVM.
 */
class BargeInDetector(
    /** Le niveau qu'une voix doit atteindre, en dBFS, quel que soit le lieu: le plancher. */
    val minimum: Float,
    /** De combien une voix doit dépasser le bruit de la pièce, en dB. */
    val margin: Float = 14f,
    /** Combien de temps elle doit le tenir, en secondes; les creux entre syllabes sont pardonnés. */
    val hold: Double = 0.16,
    /**
     * Après le début (ou la reprise) d'une réponse, combien de temps rien ne compte: l'annulateur
     * d'écho réapprend la pièce et, pendant une demi-seconde environ, la réponse fuit aussi fort
     * qu'une voix.
     */
    val settle: Double = 1.2,
) {
    private var loudFor = 0.0
    private var waiting = settle

    /** Le bruit de la pièce, en dBFS: il tombe à tout ce qui est plus calme et remonte lentement. */
    private var floor: Float = 0f

    /** Repartir de zéro, comme au début ou à la reprise d'une réponse. */
    fun rearm() {
        loudFor = 0.0
        waiting = settle
    }

    /**
     * Le niveau d'une mesure et sa durée. Vrai une fois qu'une voix a été assez forte assez
     * longtemps; l'appelant cesse alors de nourrir le détecteur jusqu'au prochain [rearm].
     */
    fun feed(decibels: Float, seconds: Double): Boolean {
        floor = minOf(decibels, floor + (seconds * 3.0).toFloat()) // +3 dB par seconde au plus
        if (waiting > 0) {
            waiting -= seconds
            return false
        }
        if (decibels > maxOf(minimum, floor + margin)) {
            loudFor += seconds
        } else {
            // Un creux entre syllabes ne reprend que ce qu'il dure.
            loudFor = maxOf(0.0, loudFor - seconds)
        }
        return loudFor >= hold
    }

    companion object {
        /**
         * Le micro après l'annulation d'écho du téléphone: le haut-parleur, l'écouteur.
         *
         * `hold` est la tenue exigée: 400 ms quand c'est une interruption demandée (Réglages →
         * Voix), les 160 ms d'origine pour un simple examen.
         */
        fun echoCancelled(
            hold: Double = 0.16,
            settle: Double = 1.2,
        ) = BargeInDetector(minimum = -28f, hold = hold, settle = settle)

        /** Le micro d'un casque, qui entend la pièce à son vrai niveau et rien de la réponse. */
        fun headset(
            hold: Double = 0.16,
            settle: Double = 1.2,
        ) = BargeInDetector(minimum = -38f, hold = hold, settle = settle)
    }
}

/**
 * Les petits sons qu'un auditeur fait sans vouloir prendre la parole (« mm-hm », « okay »,
 * « d'accord »). Dits par-dessus une réponse, ce n'est pas une raison de la couper.
 *
 * Comme.
 */
object Backchannel {
    val words: Set<String> = setOf(
        "mm", "mhm", "mhmm", "mmhm", "mmhmm", "hmm", "hm", "uh", "um", "huh", "ah", "oh", "ooh",
        "aha", "uhhuh", "yeah", "yep", "yup", "yes",
        "ok", "okay", "right", "sure", "wow", "cool", "nice", "great", "good", "true", "exactly",
        "indeed", "really", "interesting",
        "i", "see", "got", "it", "gotcha", "alright", "all", "fine", "thanks", "thank", "you",
        // Le français, coupé comme l'anglais: « d'accord » donne « d » et « accord », « j'vois »
        // donne « j » et « vois » — les élisions d'une lettre comptent donc comme des mots vides.
        "d", "j", "l", "n", "s", "t", "qu", "accord", "oui", "ouais", "ben", "bah", "bon", "hein",
        "voila", "voilà", "vois", "merci", "bien", "super", "exact", "carrement", "carrément",
        "mouais",
    )

    /**
     * Vrai quand [text] n'est rien d'autre que ces sons: « Mm-hm. », « Okay, right », « Je vois ».
     * Les mots ne comptent qu'entiers: « okay, stop » n'en est pas un, pas plus qu'une longue
     * suite de ces mots.
     */
    fun isOnly(text: String): Boolean {
        val said = text.lowercase().replace("-", "")
            .split(Regex("[^\\p{L}]+"))
            .filter { it.isNotEmpty() }
        if (said.isEmpty() || said.size > 3) return false
        return said.all { it in words }
    }
}

/**
 * « Stop », pour une réponse que seul ce mot arrête (Réglages → Voix → Interrompre avec).
 *
 * Comme.
 */
object StopWord {
    /** Dits avant « stop », ceux-ci veulent le contraire. */
    private val negations = setOf(
        "don't", "dont", "not", "never", "won't", "wont", "can't", "cant", "cannot",
        "doesn't", "didn't",
        "ne", "pas", "jamais", "arrete pas", "arrête pas",
    )

    /**
     * Vrai quand [text] contient « stop » comme mot à part entière, et pas dans « don't stop ».
     * L'une des phrases propres de la personne ([own]) compte pareil, dite comme une suite de mots
     * n'importe où dans ce qui a été entendu; une phrase écrite sans espaces (chinois, japonais)
     * compte où qu'elle se trouve.
     */
    fun heardIn(text: String, own: List<String> = emptyList()): Boolean {
        val normalized = own(own)
        val words = words(text)
        val phrases = listOf(listOf("stop")) + normalized.map { words(it) }.filter { it.isNotEmpty() }
        for (phrase in phrases) {
            if (words.size < phrase.size) continue
            for (i in 0..(words.size - phrase.size)) {
                if (words.subList(i, i + phrase.size) == phrase) {
                    if (!(i > 0 && words[i - 1] in negations)) return true
                }
            }
        }
        val run = words.joinToString("")
        return normalized.any { phrase ->
            !phrase.contains(" ") && phrase.any { it.code >= 0x2E80 } && run.contains(phrase)
        }
    }

    /** Les phrases propres, telles qu'on les compare: minuscules, sans ponctuation, sans doublons. */
    fun own(phrases: List<String>): List<String> {
        val seen = LinkedHashSet<String>()
        return phrases.map { normalize(it) }.filter { it.isNotEmpty() && seen.add(it) }
    }

    private fun normalize(phrase: String) =
        phrase.lowercase().replace(Regex("[\\p{Punct}]"), " ").replace(Regex("\\s+"), " ").trim()

    /** Les mots de [text]: minuscules, sans accents, apostrophes droites, lettres et chiffres. */
    private fun words(text: String): List<String> {
        val flat = Normalizer.normalize(text.lowercase().replace('’', '\''), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return flat.split(Regex("[^\\p{L}\\p{N}']+")).filter { it.isNotEmpty() }
    }
}
