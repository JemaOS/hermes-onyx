package com.onyx.app.ui.design

/**
 * Le collage dans un champ, hors du clavier.
 *
 * Le défaut d'origine : coller un jeton dans un champ passait par le clavier et la valeur en
 * ressortait plus courte qu'elle n'était entrée (un jeton de vingt caractères est arrivé à six au
 * journal du pont Ordinateur). Deux gestes le corrigent, et **aucun des deux ne coupe** :
 *
 *  1. [OnyxFieldPaste.text] — poser le presse-papier **d'un coup**, sans passer par le clavier ;
 *  2. [OnyxFieldSync.shouldAdopt] — n'adopter la valeur du parent que quand elle vient d'ailleurs,
 *     jamais l'écho de sa propre frappe, qu'un effet asynchrone pouvait rejouer en arrière.
 *
 * La logique vit ici, hors du dessin : elle se mesure sans monter un écran.
 */
object OnyxFieldPaste {

    /**
     * Le texte à poser pour un presse-papier donné.
     *
     * Seuls les blancs qui entourent disparaissent (un jeton copié depuis un terminal traîne un
     * retour à la ligne) ; **rien au milieu n'est retiré**. Longueur d'entrée = longueur de sortie
     * dès qu'il n'y a qu'à rogner les bords.
     */
    fun text(clipboard: String): String = clipboard.trim()
}

/**
 * Quand un champ doit-il reprendre la valeur que le parent lui donne ?
 *
 * Le champ tient sa valeur localement (il la frappe) et la reçoit du parent. Les deux ne doivent
 * pas se battre : reprendre l'écho de sa propre frappe pendant qu'on tape reculait le texte et le
 * coupait. On n'adopte donc que ce qui vient réellement d'ailleurs.
 */
object OnyxFieldSync {

    /**
     * Vrai quand la valeur du parent doit remplacer celle du champ.
     *
     * - `false` si c'est l'écho de ce que le champ vient d'émettre (`value == emitted`) : le champ
     *   est déjà à jour, le rejouer ne ferait que reculer ;
     * - `false` si le champ porte déjà cette valeur ;
     * - `true` sinon : un lien appliqué, un bouton « Coller », une valeur posée d'ailleurs.
     */
    fun shouldAdopt(value: String, emitted: String, field: String): Boolean =
        value != emitted && field != value
}
