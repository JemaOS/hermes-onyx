package com.onyx.app.localization

/**
 * **La grille de raccourcis personnalisable** : le mode édition, la bibliothèque, les bornes.
 *
 * Un fichier à part, comme les autres blocs de la maison : les libellés de la personnalisation de la
 * grille n'ont rien à faire dans les tables des écrans. Les clés sont les phrases anglaises déjà
 * présentes dans le volet (l'écran les écrit **en anglais**) et la valeur est le français.
 *
 * Les deux refus portent un `%d` : le nombre vient des **bornes du code**
 * (`SidebarTileLayout.MIN_TILES` / `MAX_TILES`), jamais d'un nombre recopié ici — un plafond qui
 * changerait dans le code ne peut pas rester faux dans la phrase.
 */
internal val onyxSidebarTilesFrench: Map<String, String> = linkedMapOf(
    // L'intitulé de la section, au-dessus de la grille (petites capitales, avec son compteur).
    "Shortcuts" to "Raccourcis",
    // Le crayon du bout de la ligne de titre, et sa sortie au **même endroit**.
    "Edit shortcuts" to "Modifier les raccourcis",
    // Le retrait d'une tuile : la cible est le rond, et son nom le dit.
    "Remove %1\$s" to "Retirer %1\$s",
    // Ce qui vient d'être retiré, et le rattrapage discret.
    "Shortcut removed" to "Raccourci retiré",
    // Remettre la grille d'origine.
    "Reset" to "Réinitialiser",
    // La bibliothèque : les destinations disponibles mais pas sur la grille.
    "Library" to "Bibliothèque",
    "Add %1\$s" to "Ajouter %1\$s",
    // L'état vide de la bibliothèque : une phrase, jamais un blanc.
    "Everything is already on the grid." to "Tout est déjà sur la grille.",
    // Les deux bornes, dites à l'écran quand elles refusent.
    "A grid keeps at least %d tiles." to "Une grille garde au moins %d tuiles.",
    "A grid holds %d tiles at most." to "Une grille contient %d tuiles au maximum.",
    // Les deux refus d'un ajout qui ne vient pas des bornes.
    "This screen has no shortcut." to "Cet écran n'a pas de raccourci.",
    "This shortcut is already on the grid." to "Ce raccourci est déjà sur la grille.",
)
