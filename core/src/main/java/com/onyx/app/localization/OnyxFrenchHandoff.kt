package com.onyx.app.localization

/**
 * Les mots du **passage de main client** — le meneur d'une salle et sa ligne de journal.
 *
 * Le vocabulaire est celui de la référence : « room » se dit **salon**, et le meneur n'est **pas**
 * un chef — c'est un **destinataire par défaut**. Les trois phrases le disent sans hiérarchie :
 * le réglage (« Meneur de ce salon »), son retrait (« Aucun meneur »), et la ligne que la salle
 * ajoute quand un membre nomme le suivant (« X passe la main à Y »).
 *
 * La phrase de la ligne porte **deux noms** : ce sont des arguments (`%1$s`, `%2$s`), jamais des
 * mots traduits — `OnyxLocalization.format` ne traduit pas ce qu'elle reçoit.
 */
internal val onyxHandoffFrench: Map<String, String> = linkedMapOf(
    // Le réglage, dans le menu « … » de la salle, et le choix qui l'enlève.
    "Lead of this room" to "Meneur de ce salon",
    "No lead" to "Aucun meneur",
    // La ligne du journal : qui passe la main, et à qui.
    "%1\$s hands the room to %2\$s" to "%1\$s passe la main à %2\$s",
)
