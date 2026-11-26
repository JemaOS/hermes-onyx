package com.onyx.app.localization

/**
 * Les libellés repris de l'organisation de la référence : le fil (rangées d'outils, carte
 * d'activité, cartes de sous-agents, pied de réponse) et l'écran Compétences (badges de
 * provenance).
 *
 * Les clés sont les chaînes anglaises écrites dans ces composants, comme partout dans l'app. Les
 * **identifiants** (un nom d'outil, un nom de compétence, un modèle) ne passent jamais par ici :
 * l'écran les rend verbatim (`localize = false`).
 */
internal val onyxOrganisationFrench: Map<String, String> = linkedMapOf(
    // La puce repliée des outils : le verbe de la référence, et sa durée.
    "Ran %d tools" to "Lancé %d outils",
    "Ran %d tool" to "Lancé %d outil",
    "< 1 s" to "< 1 s",
    "%d s" to "%d s",
    "%d min %d s" to "%d min %d s",

    // La carte d'activité : son étape, et le compte de sous-agents d'une barre d'état.
    "Étape %1\$d sur %2\$d" to "Étape %1\$d sur %2\$d",
    "Working with subagents" to "Travail avec sous-agents",
    "1 subagent" to "1 sous-agent",
    "%d subagents" to "%d sous-agents",
    // La carte d'un enfant lancé mais suivi à l'aveugle (l'API de conversation ne dit rien de plus).
    "In the background" to "En arrière-plan",

    // Les badges de provenance d'une compétence.
    "hub" to "hub",
    "learned" to "appris",
    "external" to "externe",

    // Le verbe au passé d'une rangée d'outil (les clés « (tool) » évitent la collision avec
    // « Read » → « Lire », qui est un bouton, et non un temps du passé).
    "Ran (tool)" to "Lancé",
    "Read (tool)" to "Lu",
    "Edited (tool)" to "Modifié",
    "Searched (tool)" to "Recherché",
    "Delegated (tool)" to "Délégué",
    // Le verbe d'un outil que le tableau ne connaît pas : son identifiant suit, **en monospace** —
    // la clé sans argument existe pour que le nom ne soit pas dit deux fois.
    "Used (tool)" to "Utilisé",
    "Used %1\$s" to "Utilisé %1\$s",

    // La feuille du détail d'un appel d'outil : ses rangées, ses blocs, ses notes.
    "Duration" to "Durée",
    "Arguments" to "Arguments",
    "Result" to "Résultat",
    "Show details" to "Afficher les détails",
    // Cinq blocs dans la même feuille ne peuvent pas porter cinq fois le même mot : le libellé de
    // chacun dit lequel il copie.
    "Copy %1\$s" to "Copier %1\$s",
    "Copied (%1\$d characters)" to "Copié (%1\$d caractères)",
    // Ce que la feuille dit quand un champ n'est pas dans nos données : une rangée vide se lirait
    // comme un appel sans arguments, ce qui serait faux.
    "The arguments of this call were not kept by this transport." to
        "Les arguments de cet appel n’ont pas été conservés par ce transport.",
)
