package com.onyx.app.localization

/**
 * Les **commandes « / » du composeur** : les six commandes fixes de l'app, et le texte de `/help`.
 *
 * Le lot « top5 » ouvre le menu aux compétences du profil. Leur libellé de droite est la
 * **description écrite par l'auteur de la compétence** : c'est une donnée du serveur, elle
 * s'affiche telle quelle et ne figure donc pas ici (seul le repli d'une compétence sans
 * description, « Skill », est déjà traduit par le catalogue partagé).
 *
 * Les clés sont les chaînes anglaises écrites dans `SlashCommands` (`app/…/chat`), jamais
 * renommées.
 */
internal val onyxSlashFrench: Map<String, String> = mapOf(
    // Les six commandes fixes — seules celles qui manquaient au dictionnaire.
    "Choose the model" to "Choisir le modèle",
    "Voice mode" to "Mode vocal",
    "Commands" to "Commandes",
    // Le texte que `/help` écrit dans le fil.
    "Slash commands" to "Commandes « / »",
    "Anything else that starts with `/` goes to the gateway." to
        "Tout ce qui commence par « / » et ne figure pas ici part vers la passerelle.",
)
