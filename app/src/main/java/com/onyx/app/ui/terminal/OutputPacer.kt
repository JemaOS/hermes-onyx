package com.onyx.app.ui.terminal

/**
 * **Le débit des publications d'un terminal.**
 *
 * Un shell ne parle pas par mots : `npm install`, une compilation, un `tail -f` arrivent en
 * **fragments** — plusieurs centaines par seconde. Chaque fragment déclenchait jusqu'ici
 * `revision++`, et le rendu reconstruit **quarante `AnnotatedString`** (une par ligne, style par
 * cellule) à chaque publication : la boucle d'affichage passait son temps à repeindre l'écran
 * entier pour quelques caractères, et c'est le figement qu'on voit sur un terminal bavard.
 *
 * Cette classe ne décide **que** du moment de publication : le texte, lui, entre dans le tampon
 * immédiatement (c'est de l'écriture en grille, bornée, pas une copie). Règle :
 *
 *  - une publication au plus toutes les [frameMillis] — le reste attend la **publication de
 *    rattrapage**, qui vient une seule fois et montre l'état complet (rien n'est perdu : le tampon
 *    est déjà à jour) ;
 *  - la première publication d'une session part tout de suite, sans attendre une trame.
 *
 * Aucune horloge n'est lue ici : `now` vient de l'appelant, donc la classe est pure et se teste
 * sans appareil ni attente réelle.
 */
class OutputPacer(private val frameMillis: Long = DEFAULT_FRAME_MILLIS) {

    private var lastPublish: Long? = null

    /** Ce fragment doit-il être publié tout de suite ? Si non, le rattrapage s'en charge. */
    fun shouldPublishNow(now: Long): Boolean {
        val last = lastPublish
        if (last == null || now - last >= frameMillis) {
            lastPublish = now
            return true
        }
        return false
    }

    /** Le rattrapage vient de publier : la fenêtre repart de maintenant. */
    fun markPublished(now: Long) {
        lastPublish = now
    }

    /** Le délai avant lequel une publication est de nouveau permise (au moins une milliseconde). */
    fun trailingDelayMillis(now: Long): Long {
        val last = lastPublish ?: return 1L
        return (last + frameMillis - now).coerceIn(1L, frameMillis)
    }

    /** Nouvelle session : la prochaine sortie doit paraître immédiatement. */
    fun reset() {
        lastPublish = null
    }

    companion object {
        /**
         * Une trame à 60 Hz. Plus court ne sert à rien (l'écran ne peint pas plus vite) et plus long
         * se voit : le terminal paraît saccadé sur une sortie continue.
         */
        const val DEFAULT_FRAME_MILLIS = 16L
    }
}