package com.onyx.app.ui.design

/**
 * **Les états du champ d'écriture — une seule couche de texte à la fois.**
 *
 * Le défaut que ce fichier ferme, relevé sur la capture du propriétaire : pendant l'attente du
 * serveur, **deux textes étaient écrits au même endroit** dans le champ — l'invite (« Écrire à
 * Onyx… ») et l'état du tour (« En attente du serveur ») —, l'indicateur d'activité **mordait** le
 * début du texte, et le bouton d'envoi avait changé de forme. Aucun de ces états n'avait jamais été
 * photographié : tous les lots avaient relevé le champ **au repos**.
 *
 * La règle tient en une phrase, et c'est une **fonction pure** (donc vérifiable sans appareil) :
 *
 * > le champ porte **un seul** texte — soit le brouillon, soit **une** invite, soit **rien** ;
 * > un état qui prend la place du champ **fait céder** l'invite, il ne s'y ajoute jamais.
 *
 * **Ce qui cède la place à quoi** — l'ordre de [composerFieldInvite] est l'ordre de priorité :
 *
 * 1. le **brouillon** non vide : c'est lui, la couche ; aucune invite n'est dessinée (c'est
 *    exactement le cas « la réponse arrive pendant que je saisis » — on n'efface pas ce qu'on
 *    écrit) ;
 * 2. la **dictée** : « Listening… » prend la place de l'invite ;
 * 3. une **réponse qui s'écrit et qu'on peut piloter** : « Steer the reply… » ;
 * 4. une **réponse qui s'écrit sans pilotage** : l'invite **cède** ([YIELDED]) — l'état vit dans la
 *    carte d'activité **au-dessus** du champ, jamais dedans ;
 * 5. **hors ligne** : l'invite **cède** ([OFFLINE]) — la file (« N messages waiting ») porte l'état ;
 * 6. **au repos** : l'invite d'écriture, la seule qui reste.
 *
 * **Rien n'est supprimé** : les six états du champ qui existaient (l'invite de la carte, celle de la
 * plaque, celle de la salle de groupe, « Listening… », « Steer the reply… », la file) sont tous
 * rendus par cette table ; deux d'entre eux cessent simplement d'écrire un texte dans le champ.
 */

/**
 * Ce que le champ d'écriture peut **écrire** — une valeur, jamais deux.
 *
 * [NONE] et les deux états « cédés » ([YIELDED], [OFFLINE]) ne dessinent **aucun** texte : la
 * couche du champ est alors vide, et c'est ce qui garantit qu'un seul libellé soit jamais peint à
 * l'endroit du champ.
 */
enum class ComposerFieldInvite {
    /** Au repos : l'invite d'écriture (la plaque mesure son libellé et peut l'abréger). */
    ASK,

    /** Une salle de groupe : « Message the group ». */
    GROUP,

    /** La dictée tourne : « Listening… ». */
    DICTATION,

    /** Une réponse s'écrit et le brouillon peut la piloter : « Steer the reply… ». */
    STEER,

    /** Une réponse s'écrit sans pilotage : l'invite **cède** — l'état vit au-dessus du champ. */
    YIELDED,

    /** Serveur injoignable : l'invite **cède** — la file d'attente porte l'état. */
    OFFLINE,

    /** Le brouillon est écrit : **il est la couche**. Aucune invite n'est dessinée avec lui. */
    NONE,
    ;

    /** Vrai quand cette invite **écrit** un texte dans le champ (donc occupe la couche). */
    val writes: Boolean get() = this == ASK || this == GROUP || this == DICTATION || this == STEER
}

/**
 * **La seule décision de ce qui s'écrit dans le champ.**
 *
 * Fonction **pure** : le même état donne toujours la même invite, donc le tableau du rapport, le
 * verrou et le rendu lisent la même règle.
 *
 * @param draftBlank vrai quand le brouillon et les pièces jointes sont vides ;
 * @param dictating vrai pendant une dictée ;
 * @param streaming vrai pendant une réponse du serveur ;
 * @param canSteer vrai quand le brouillon peut piloter la réponse en cours ;
 * @param offline vrai quand le serveur est injoignable (la file porte alors l'état) ;
 * @param group vrai pour une salle de groupe (son invite dit « Message the group »).
 */
fun composerFieldInvite(
    draftBlank: Boolean,
    dictating: Boolean,
    streaming: Boolean,
    canSteer: Boolean,
    offline: Boolean,
    group: Boolean = false,
): ComposerFieldInvite = when {
    // 1. Le brouillon est la couche : rien d'autre ne s'écrit dans le champ. C'est le cas de la
    //    réponse qui arrive pendant la saisie — on ne recouvre pas ce que la personne écrit.
    !draftBlank -> ComposerFieldInvite.NONE
    // 2. La dictée écrit son propre mot à la place de l'invite.
    dictating -> ComposerFieldInvite.DICTATION
    // 3. Une réponse pilotable propose le pilotage.
    streaming && canSteer -> ComposerFieldInvite.STEER
    // 4. Une réponse non pilotable : l'état vit au-dessus, l'invite cède.
    streaming -> ComposerFieldInvite.YIELDED
    // 5. Hors ligne : la file porte l'état, l'invite cède.
    offline -> ComposerFieldInvite.OFFLINE
    // 6. Une salle de groupe : son invite propre.
    group -> ComposerFieldInvite.GROUP
    // 7. Au repos : la seule invite qui reste.
    else -> ComposerFieldInvite.ASK
}

/**
 * **Les textes réellement dessinés dans le champ** — au plus **un**, par construction.
 *
 * C'est ce que le verrou interroge : si une implémentation dessinait l'invite *et* le brouillon (le
 * défaut de la capture), la liste en porterait deux et le test casserait. La fonction est pure et
 * prend le rendu de l'invite en paramètre, pour que le test n'ait pas besoin d'un thème.
 */
fun composerFieldTexts(
    draft: String,
    invite: ComposerFieldInvite,
    inviteText: String?,
): List<String> = buildList {
    if (draft.isNotEmpty()) add(draft)
    if (invite.writes && inviteText != null) add(inviteText)
}
