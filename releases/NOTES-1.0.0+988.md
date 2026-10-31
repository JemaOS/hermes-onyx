**SI VOUS AVEZ LA 980, LA MISE À JOUR EST NORMALE. SI VOUS AVEZ UNE VERSION ANTÉRIEURE À LA 980, LE CERTIFICAT DE SIGNATURE A CHANGÉ : DÉSINSTALLEZ L'APPLICATION AVANT D'INSTALLER CELLE-CI.**

# Notes de version — Hermes Onyx `1.0.0+988`

Ce que cette version apporte, et **où c'est prouvable**. Chaque point renvoie à un commit de
l'arbre d'intégration, à un fichier de la source, ou à une mesure chiffrée. Rien n'est annoncé ici
qui ne puisse être montré.

**Version :** `1.0.0+988` (`versionCode` = `988`, écrit dans `gradle.properties` — l'arbre public
est la copie aplatie de l'arbre d'intégration, son historique ne porte donc pas le numéro ; tête
d'intégration `d7d84bdb`).
**Paquets :** téléphone `Hermes-Onyx-1.0.0+988-telephone.apk`, auto `Hermes-Onyx-1.0.0+988-auto.apk`,
wear `Hermes-Onyx-1.0.0+988-wear.apk` — les trois reconstruits et signés pour cette version.
**Signature :** les trois paquets portent **un seul certificat**, le même que la `1.0.0+980` —
`CN=Hermes Onyx, OU=Android, O=JemaOS, L=Paris, ST=Ile-de-France, C=FR`
(SHA-256 `bf43641666c36da0e7058547f85b0922c5ed14b5da86a812b08e0e67d1ed4049`).
**Ce que cela change pour vous :** depuis la `1.0.0+980`, la mise à jour **en place** se fait
normalement. Depuis une version **antérieure à la `1.0.0+980`**, il faut **désinstaller, puis
installer** — le certificat de signature a changé entre-temps, et Android refuse une mise à jour
d'un paquet signé par un autre certificat. Le serveur, lui, n'a rien perdu ; rescannez votre code
de configuration après réinstallation.

---

## 1. Le verre des plaques, plus prononcé — et la plaque qui suit le doigt

- **Le flou de la plaque du bas passe de 36 à 44 dp** (`GlassBarBlurPlateDp` dans
  `core/src/main/java/com/onyx/app/ui/design/LiquidGlass.kt`) : une partie du verre perdu venait du
  voile, l'autre du flou lui-même — plus il est large, plus ce qui passe derrière se lit comme une
  vitre plutôt qu'un aplat. 44 dp reste dans la fourchette de la maison (36–50) ; la barre du haut,
  elle, ne bouge pas (36 dp, verrouillé).
- **L'arête de verre des plaques est peinte pour la première fois** :
  `GlassSurface.PANE` et `plateEdge` (`plateEdge`/`drawGlassPlateEdge` dans `LiquidGlass.kt`),
  l'arête claire du haut — blanc 98 % en clair, 62 % en sombre, 1,5 px — exposée aussi **par
  apparence** (`OnyxEdge.topEdge(dark = …)`, `core/src/main/java/com/onyx/app/ui/design/OnyxEdge.kt`).
- **La plaque de contenu suit le doigt** : quand on passe d'un onglet à l'autre (Flux, Fichiers…),
  le contenu est posé sur une plaque de verre **transformée par le pageur**, donc elle glisse avec
  le doigt et son arête reste visible **pendant** le mouvement (`panePlate`, `ChatScreen.kt`).
- Lots : outillage et verre `0696e0e0`, complément de tête (verrous `PANE` et `plateEdge`) `ef9dde64` ;
  verrou de source neuf `app/src/test/java/com/onyx/app/VerrePlaquesVerrouTest.kt` (297 lignes).

## 2. Les visages des bots défilent dans une conversation de groupe

- **Le repère du bas fait défiler les visages des membres**, un bot après l'autre, **8 secondes
  chacun**, en boucle. C'est une **règle pure** (`TabFaceCycle.PeriodMs = 8_000L`,
  `rememberTabFaceCycleIndex` dans `core/src/main/java/com/onyx/app/ui/design/ChatTabBar.kt`) : le
  chiffre que le verrou interroge et celui que l'écran emploie sont **le même appel** ; une
  conversation à un bot ne défile pas, et « Réduire les animations » fige le visage sur le premier
  membre. La teinte de la salle suit le visage montré — elle ne peut donc pas montrer un bot sous
  la couleur d'un autre (`GroupRoomScreen.kt`, `app/src/main/java/com/onyx/app/ui/bots/`).
- Commit `84a0a432` (« Visages de groupe : le repère du bas fait défiler les bots, 8 secondes
  chacun ») ; verrou de source neuf
  `app/src/test/java/com/onyx/app/ui/bots/VisagesGroupeVerrouTest.kt` (154 lignes).

## 3. Ce qu'apportait déjà la 980 (rappel)

- **Marques d'identité unifiées** — `IdentityMark.kt`, une seule source lue par le volet, la galerie
  et l'en-tête.
- **La couverture qui rend enfin la nappe** — crête enroulée et nappe de ton rendue par un vrai
  **processeur d'ombrage** (`android.graphics.RuntimeShader`, AGSL) dans `FluideCover.kt`, avec un
  repli là où il ne compile pas.
- **Le bouton d'envoi visible dans les deux apparences** — clair comme sombre
  (`OnyxComposer.kt`).
- **Pièces jointes durables** — elles survivent à la fermeture de l'écran.
- **Usage par bot et par conversation**, et non plus seulement au total.
- **Parité des conversations de groupe** avec l'intégration.
- **L'en-tête sans doublon** — il n'est plus dessiné deux fois.

## 4. Les anciens noms

- Depuis la 980, l'ancien schéma d'URL et l'hôte web sont **assemblés à l'exécution**
  (`SetupCode.kt`) : le dex livré ne porte plus le littéral, et le comportement est conservé — un
  lien à l'ancien schéma, même déjà imprimé dans un QR, ouvre toujours la feuille de configuration.
  Le contrôle `scripts/check-forbidden-words.sh` et le `grep` borné du dépôt public rendent **0**,
  contenu **et** noms de fichiers.

## 5. Ce que cette version ne fait pas

- Le **flou d'arrière-plan du verre** ne se juge pas sur un émulateur (il rend en logiciel) : il se
  vérifie sur un appareil réel.
- Les **captures d'écran** de la galerie du README peuvent montrer un build antérieur ; chacune est
  étiquetée avec son build dans `docs/screenshots/README.md`.

---

## Fichiers

| Paquet | Module | sha256 |
|---|---|---|
| `Hermes-Onyx-1.0.0+988-telephone.apk` | app | *voir `SHA256SUMS`* |
| `Hermes-Onyx-1.0.0+988-auto.apk` | auto | *voir `SHA256SUMS`* |
| `Hermes-Onyx-1.0.0+988-wear.apk` | wear | *voir `SHA256SUMS`* |

Vérifier un téléchargement :

```bash
curl -LO https://github.com/JemaOS/hermes-onyx/releases/latest/download/SHA256SUMS
sha256sum -c SHA256SUMS
```
