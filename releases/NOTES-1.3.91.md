# Hermes Onyx 1.3.91

Le volet redevient un panneau : une seule couleur, aucune bordure, et un changement de section qui
ne fait plus glisser la page.

## Un fond uni, sans matière ni halo

Le volet clair portait la **matière** de la page (dégradé multi-arrêts, formes floues, grain,
vignettage) sous une vitre qui la ré-échantillonnait. L'ensemble se lisait comme un fond taché.

- Le volet ne pose plus ni `matiereBackdrop`, ni `haloBackdrop` : sa couleur pleine, et rien d'autre.
- `sidebarPaneSurface` ne garde le verre qu'en **sombre**. Au clair, le panneau est sa couleur, sans
  voile, sans liseré interne, sans arête.
- `sidebarTileSurface` conserve sa plaque et son seuil de luminance, mais perd son filet.

## Plus de bordures autour des surfaces

Le propriétaire a refusé les cadres autour des éléments du volet. Ils sont retirés au clair :

- le champ de recherche devient un **creux uni** (le verre de Frost ne peint plus son liseré ; en
  sombre il reste tel quel) ;
- les tuiles, le carré d'icône, les pastilles de projet et de filtre, la ligne « Réessayer », le
  bouton « Réinitialiser » et les actions de section (crayon, « + ») n'ont plus de filet ;
- le seul contour qui reste est l'**anneau d'accent du champ pendant la saisie**, et la case de
  sélection (un contrôle, pas une décoration).

## Un changement de section ne bouge plus la page

Un appui sur une tuile ou un raccourci faisait glisser horizontalement toute la page. Le changement
de section se fait désormais **sur place**, avec un fondu court. L'animation de largeur du volet
elle-même est conservée.

## Vérifié

- `:core:testDebugUnitTest` + `:app:testDebugUnitTest` : **4 337 tests, 0 échec**, 1 ignoré.
- `:app:assembleRelease`, `:wear:assembleRelease`, `:auto:assembleRelease` : trois APK signés.
- `apksigner verify` : même certificat que les versions précédentes
  (`f58852a2…`), `versionCode 1223`, `versionName 1.3.91`.

| Paquet | Module | Taille |
|---|---|---|
| `onyx-1.3.91-release.apk` | app | 12,1 Mo |
| `onyx-auto-1.3.91-release.apk` | auto | 12,3 Mo |
| `onyx-wear-1.3.91-release.apk` | wear | 34,3 Mo |

**Non vérifié** : le rendu n'a pas été recontrôlé sur le Honor Magic V3 dans cette version. Les
changements sont visuels et structurels ; les verrous de test couvrent l'absence de matière, de
filet et de glissement de section.
