# Notes de version — Hermes Onyx `1.0.0+972`

Ce que cette version apporte, et **où c'est prouvable**. Chaque point renvoie à un commit de
l'arbre de fusion, à un fichier de la source, ou à une mesure chiffrée. Rien n'est annoncé ici
qui ne puisse être montré.

**Version :** `1.0.0+972` (`versionCode` = `972`, écrit dans `gradle.properties` — l'arbre public
est la copie aplatie de l'arbre de fusion, son historique ne porte donc pas le numéro).
**Paquets :** téléphone `Hermes-Onyx-1.0.0+972-telephone.apk`, auto `Hermes-Onyx-1.0.0+972-auto.apk`,
wear `Hermes-Onyx-1.0.0+972-wear.apk` — les trois reconstruits et signés pour cette version.
**Recette :** `3596 tests, 0 échec` — `:core:testDebugUnitTest` + `:app:testDebugUnitTest`.

---

## 1. Matière et couleurs

- **La palette et les surfaces sont lues dans la source, pas redessinées.** Six palettes de la
  référence comparées champ par champ : **54 valeurs comparées, 54 identiques, 0 écart**
  (`PARITY-COLOURS.md`). Le verrou de palette relève **412 sources lues, 4 dispensées,
  0 couleur hors palette** (commit `3cd0f588`).
- **Le thème sombre est mesuré, barreau par barreau.** L'échelle de surfaces est posée en jetons
  (`core/src/main/java/com/onyx/app/ui/design/OnyxSurfaces.kt`, `OnyxSurfaceStyle.kt`) et chaque
  écart est chiffré (commit `a8c50987`, rapport `UI-UNIFORMITY-SOMBRE.md`) :

  | objet | avant | après | chiffre |
  |---|---|---|---|
  | compositeur | `#121215` sur la page `#0A0A0C` | barreau `surfaceHigh` `#26262B` + filet + ombre | **1,058:1 → 1,314:1** (seuil « flottant » 1,25:1) |
  | barre d'onglets | même voile | même barreau, forme capsule | **1,058:1 → 1,314:1** |
  | onglet actif | accent à 16 % sur le voile | capsule `#33333A` opaque | **1,201:1** contre sa barre (seuil 1,15) ; encre d'accent **5,165:1** (plancher 3:1) |
  | feuille des actions / pièces jointes | fond opaque `#16161A` | `surfaceHigh` + filet | **1,096:1 → 1,314:1** |
  | barre du haut du fil | verre seul, coin 22 dp | `surfaceHigh` + filet + ombre, **capsule** | **1,058:1 → 1,314:1** |

- **Une seule famille de formes et d'ombres :** coins écrits sur place → `OnyxSpace.PanelCorner`
  (**22 → 26 dp**), quatre portées d'ombre → une (**`OnyxShadow.Float`**, 6 dp), zéro couleur
  décorative dans les feuilles et menus (commit `a8c50987`).

## 2. Verre liquide — Haze 2.0.1, réfraction et soudure

- **La bibliothèque de verre passe en Haze 2.0.1** (`dev.chrisbanes.haze` `1.6.10 → 2.0.1`), avec
  ses modules **`haze`**, **`haze-blur`** et **`haze-glass`**. Le module de verre apporte la
  **réfraction** : le contenu échantillonné est **déplacé** selon le profil d'arête
  (`RefractionProfile.Edge`), avec dispersion chromatique, liseré d'arête et reflet spéculaire
  (`gradle/libs.versions.toml`, `core/build.gradle.kts`, `RAPPORT-VERRE-LIQUIDE.md`).
- **La réfraction est une mesure, pas une promesse.** `GlassRefractionAvailable = true` suit la
  disponibilité réelle ; elle demande un processeur d'ombrage à l'exécution (**API 33+**,
  `GlassRefractionMinSdk`) et un **repli reste branché** en dessous — les deux ne se remplacent
  pas (`core/src/main/java/com/onyx/app/ui/design/GlassTouch.kt`, `LiquidGlass.kt`).
- **La profondeur de la vitre est composée de quatre pièces mesurées** : lumière de tête, ombre de
  pied, liseré interne d'épaisseur et reflet de tranche, chacune chiffrée et vérifiée par
  `GlassLuminanceTest` (`core/.../design/LiquidGlass.kt`).
- **La soudure des formes.** La géométrie du pont sort des traces (commit `13033ee5`) et le profil
  de soudure **décroît avec l'écart** (commit `4f414fde`).
- **Le verre est visible** : la matière derrière la vitre et l'état du palier (commit `113ccd21`),
  règles de conservation R8 posées pour le planificateur de travail (commit `09a618da`,
  rapport `0e9075ab`).

## 3. Ce qui a bougé dans l'interface

- **Une barre du bas flottante** et la palette de référence (commit `52d0a4cf`), sur une refonte du
  verre liquide par jetons (commit `3baf4ca3`).
- **Des boutons ronds, sans facettes** : les octogones deviennent des cercles, l'ombre est dessinée
  (commit `794472c7`).
- **Le suivi du défilement** : le fil suit si l'on est en bas, avec une marge d'ancrage portée par
  l'état (commit `af6f86e6` ; `ScrollFollow`, `ChatScrollFollowTest.kt`).
- **Le glissement des conversations, à deux sens** (`SidebarSwipe`, commit `da32ba08`).
- **L'organisation du fil** et l'écran Capacités (commit `87bb09fa`).
- **Les sous-agents** — les enfants d'un agent apparaissent en liste de tâches, avec leur propre
  transcription, leur durée et un bouton d'arrêt (commits `5ddc604e`, `170ed926`, `a3912744`,
  `d831f5f9`, `3df3edc1` ; `SubagentListTest.kt`, `ThreadOrganisationTest.kt`).
- **Le doublon « Compétences » retiré :** la tuile du volet et la ligne des Réglages ont quitté
  l'interface, la route avec elles ; le groupe « Onyx » passe à **quatre** tuiles en grille 2 × 2,
  et tout ce que faisait la famille des compétences est **dans** « Capacités », éditeur compris
  (commit `64d8e756` ; `CapabilitiesDoublonRetireTest.kt`).
- **Réglages — anatomie :** en-tête à grand titre, tuiles, séparateurs en retrait, curseur de taille
  de texte, contraste réparé (commit `99979fb4`).

## 4. Appels, fichiers, widget

- **L'appel entrant et les heures de tranquillité.** Quand une décision tombe, le téléphone **sonne
  comme un appel**, plein écran par-dessus l'écran verrouillé (`CallRinger.kt`,
  `IncomingCallActivity.kt`, `CallActionReceiver.kt`) ; répondre met la conversation sur la ligne.
  Les **heures de tranquillité** coupent la sonnerie sur la plage choisie (`QuietHours`,
  `CallsScreen.kt`). Commit `4cc98475`.
- **TIFF, HEIC et AVIF** en plus des formats courants : décodage TIFF de la maison et lecteurs
  dédiés (`FileViewers.kt`, `BoundedImages.kt`, `TiffImageTest.kt`). Commits `363a1cb0`,
  `02f7a517`.
- **Le widget HTML réparé :** le repli animé faisait tomber le moteur de rendu — repli **immédiat** et
  largeur tenue (commit `b7afc4dd`) ; le pont de la page et sa mesure redeviennent des **scripts**
  (commit `b523abef`).

## 5. Le retour

- **Les retours sont corrigés :** la page quittée n'est plus perdue au sortir du volet (commit
  `f48b2fcd`), et un banc mesure les trois chemins de retour, avant/après (commit `bdb8d2e0`,
  `RETOUR-AUDIT.md`, `RetourMatriceTest.kt`).

## 6. Ce que cette version ne fait pas

- Le **flou d'arrière-plan du verre** ne se juge pas sur un émulateur (il rend en logiciel) : il se
  vérifie sur un appareil réel.
- Les compagnons **auto** et **wear** ne sont reconstruits que s'ils sont fournis avec la release ;
  sinon ils restent sur leur version précédente (`1.0.0+972`), et le README d'installation le
  dit.

---

## Fichiers

| Paquet | Module | sha256 |
|---|---|---|
| `Hermes-Onyx-1.0.0+972-telephone.apk` | app | *voir `SHA256SUMS`* |
| `Hermes-Onyx-1.0.0+972-auto.apk` | auto | *voir `SHA256SUMS`* |
| `Hermes-Onyx-1.0.0+972-wear.apk` | wear | *voir `SHA256SUMS`* |

Vérifier un téléchargement :

```bash
curl -LO https://github.com/JemaOS/hermes-onyx/releases/latest/download/SHA256SUMS
sha256sum -c SHA256SUMS
```
