package com.onyx.app.localization

/**
 * Le français de la barre d'onglets d'une conversation et des vues qu'elle ouvre
 * (**Flux**, **Fichiers**).
 *
 * Ces clés sont **neuves** : avant chacune, la clé a été cherchée dans **tous** les dictionnaires
 * du projet, parce que cette table est lue en dernier et qu'une clé en double **écraserait** la
 * traduction existante. « Files » est donc volontairement absent : il est déjà au catalogue partagé
 * (« Fichiers »).
 *
 * **Les clés d'« Idées » et d'« Objectifs » ont été retirées** avec leurs écrans (demande explicite
 * du propriétaire) : plus rien ici ne nomme des listes locales qui n'existent plus.
 */
val onyxTabsFrench: Map<String, String> = mapOf(
    // Les onglets de contenu.
    "Chat" to "Chat",
    "Feed" to "Flux",

    // Le retour d'une carte du flux, et ses actions.
    "Thumb up" to "Pouce en haut",
    "Thumb down" to "Pouce en bas",
    "Discuss" to "Discuter",
    "Source and detail" to "Source et détail",
    "Source: %1\$s" to "Source : %1\$s",
    "Your thumbs are kept on this phone, per bot and per card." to
        "Vos pouces sont gardés sur ce téléphone, par bot et par carte.",
    "Compose" to "Composer",
    // Ce que « Discuter » écrit dans le champ, prêt à envoyer.
    "About %1\$s: " to "À propos de %1\$s : ",

    // Le flux, groupé par jour.
    "This week" to "Cette semaine",
    "Earlier" to "Plus tôt",

    // Les états vides du flux et des fichiers : ils disent toujours ce qu'il en est, et quoi faire.
    "No cards from %1\$s yet. Its answers appear here." to
        "Aucune carte de %1\$s pour l’instant. Ses réponses apparaîtront ici.",
    "Write the first question to fill this feed." to
        "Écrivez la première question pour remplir ce flux.",
    "Nothing has been exchanged in this conversation yet." to
        "Rien n’a encore été échangé dans cette conversation.",
    "Files the bot delivers and files you attach land here." to
        "Les fichiers livrés par le bot et ceux que vous joignez arrivent ici.",
    "attachment" to "pièce jointe",
    "This list is empty." to "Cette liste est vide.",

    // L'onglet **Fichiers** : les filtres par famille, l'ordre de la liste, l'état de chaque
    // fichier sur le téléphone, et les gestes de l'appui long.
    "Images" to "Images",
    "Videos" to "Vidéos",
    "Audio" to "Audio",
    "Documents" to "Documents",
    "Archives" to "Archives",
    "Sort" to "Tri",
    "Recent first" to "Récents d’abord",
    "Oldest first" to "Anciens d’abord",
    "Size" to "Taille",
    "Type" to "Type",
    "To download" to "À télécharger",
    "Downloading…" to "Téléchargement…",
    "Available" to "Disponible",
    "Save to the phone" to "Enregistrer sur le téléphone",
    "Saved to the phone" to "Enregistré sur le téléphone",
    "Couldn't save it." to "Impossible de l’enregistrer.",
    "Copy path" to "Copier le chemin",
    "Path copied" to "Chemin copié",
    "Open with" to "Ouvrir avec",
    "Sharing isn't possible for this file." to "Le partage n’est pas possible pour ce fichier.",
    "This sound can't be played." to "Ce son ne peut pas être lu.",
    "This archive can't be read here." to "Cette archive ne peut pas être lue ici.",
    "This archive is empty." to "Cette archive est vide.",
    "Only the first %1\$s of this file is shown." to "Seuls les premiers %1\$s de ce fichier sont affichés.",
    "No picture in this conversation." to "Aucune image dans cette conversation.",
    "No video in this conversation." to "Aucune vidéo dans cette conversation.",
    "No sound in this conversation." to "Aucun son dans cette conversation.",
    "No document in this conversation." to "Aucun document dans cette conversation.",
    "No archive in this conversation." to "Aucune archive dans cette conversation.",
)
