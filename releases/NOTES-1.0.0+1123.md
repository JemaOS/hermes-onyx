# Onyx 1.0.0+1123 — le menu ancré devient du verre

Le menu ancré se lisait comme un **rectangle gris translucide** posé sur l'écran. Ce n'était pas une teinte trop forte : c'était **quatre fautes empilées**, dont aucune ne se voyait à l'écran.

## 1. Le menu vivait dans une `Dialog` — une autre fenêtre

Elle ne voit ni la source de flou Haze (posée par l'écran, dans la fenêtre principale), ni la page de la lentille, ni le calque de fond. Un panneau qui y vit ne peut donc **rien** échantillonner : il ne peut que se peindre d'un voile.

Il passe désormais dans un **overlay de la fenêtre principale** (`OverlayHost`) — ce qui corrige les **29 appelants** d'un coup. Le retour matériel ferme le menu, la `Dialog` ne le faisait plus.

## 2. Le chemin de la lentille peignait une teinte mais n'échantillonnait rien

La texture enregistrée rendait vide : le panneau laissait passer le texte dessous **net**. Le menu passe sur le **chemin de Haze**, seul chemin mesuré comme échantillonnant — le volet le prouve (`count=1`).

## 3. La matière de la page de la salle était posée hors de la source Haze

La vitre n'avait qu'un aplat à manger. Elle est désormais posée **dans** la source, comme dans le fil de bot. C'était la même faute que le fil avait, et la même correction.

## 4. `masking = true` remontait le corps à 0,92

Le panneau redevenait l'aplat qu'on voulait supprimer. Retiré — la lisibilité, ici, c'est le **flou** qui la porte, pas l'opacité.

## Mesuré sur l'appareil

La salle, le menu au-dessus du fil :

| | avant | après |
|---|---|---|
| corrélation avec l'image nette (1,0 = texte net) | 0,9927 | **0,2804** |
| écart-type du fond sous le panneau | 1,3 (aplat) | contenu réel, 0,6 → 94,7 |

Le texte sous le panneau n'est plus net : il est **flouté**. Le panneau porte l'arête lumineuse et le texte du menu reste lisible.

## Ce qui n'a pas bougé

Les surfaces qui ne sont pas des menus gardent leur chemin : la feuille du volet reste volontairement opaque (verrou `DialogueSurfaceVerrouTest`), et `SHEET`/`ALERT` restent déclarées à fenêtre propre.

## Tests

**3755 tests, 0 échec.** Le verrou `GlassLuminanceTest` dit maintenant ce que dit la table (le menu est dans la fenêtre), et `VerreFondVerrouTest` a un verrou de plus pour la matière de la salle.
