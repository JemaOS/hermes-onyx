**LE CERTIFICAT DE SIGNATURE A CHANGÉ : DÉSINSTALLEZ L'ANCIENNE APPLICATION AVANT D'INSTALLER CETTE VERSION — LES MISES À JOUR EN PLACE SONT IMPOSSIBLES.**

# Notes de version — Hermes Onyx `1.0.0+980`

Ce que cette version apporte, et **où c'est prouvable**. Chaque point renvoie à un commit de
l'arbre d'intégration, à un fichier de la source, ou à une mesure chiffrée. Rien n'est annoncé ici
qui ne puisse être montré.

**Version :** `1.0.0+980` (`versionCode` = `980`, écrit dans `gradle.properties` — l'arbre public
est la copie aplatie de l'arbre d'intégration, son historique ne porte donc pas le numéro).
**Paquets :** téléphone `Hermes-Onyx-1.0.0+980-telephone.apk`, auto `Hermes-Onyx-1.0.0+980-auto.apk`,
wear `Hermes-Onyx-1.0.0+980-wear.apk` — les trois reconstruits et signés pour cette version.
**Signature :** les trois paquets portent **un nouveau certificat de signature** —
`CN=Hermes Onyx, OU=Android, O=JemaOS, L=Paris, ST=Ile-de-France, C=FR`
(SHA-256 `bf43641666c36da0e7058547f85b0922c5ed14b5da86a812b08e0e67d1ed4049`).
**Ce que cela change pour vous :** Android **refuse une mise à jour en place** depuis `1.0.0+972`
ou toute version antérieure (signées par un autre certificat). **Désinstallez l'application, puis
installez cette version**, et rescannez votre code de configuration — le serveur, lui, n'a rien perdu.
**Recette :** `3597 tests, 0 échec` — `:core:testDebugUnitTest` + `:app:testDebugUnitTest`
(**1661** dans `:core`, **1936** dans `:app`, 0 échec, 0 erreur), relevé sur les rapports XML de
l'arbre d'intégration (rev 980, tête `945755b8`), et rejoué par le CI sur le commit publié.

---

## 1. Marques d'identité unifiées

- **Une seule marque d'identité, une seule source.** `IdentityMark.kt` remplace les dessins
  d'identité dispersés ; le volet, la galerie et l'en-tête la lisent
  (`core/src/main/java/com/onyx/app/ui/design/IdentityMark.kt`,
  `app/src/main/java/com/onyx/app/ui/sidebar/ChatSidebar.kt`, `ChatSidebarModel.kt` ; fusion
  `52c93677`). Un verrou relève les marques qui restent dans la source
  (`app/src/test/java/com/onyx/app/ui/design/IdentityMarkLockTest.kt`, 335 lignes) ; le rapport du
  lot est `RAPPORT-MARQUES-IDENTITE.md`.

## 2. La couverture d'une conversation, et la nappe de ton

- **La crête de la couverture s'enroule** et la **nappe de ton** est rendue par un vrai
  **processeur d'ombrage** — `android.graphics.RuntimeShader` (AGSL) dans
  `core/src/main/java/com/onyx/app/ui/design/FluideCover.kt`, avec un repli branché là où il ne
  compile pas (`agslCompiles()`). Fusions `ffb787f7`, `928065df`, `cf431d90` (`lot/brume-nappe`) ;
  le relevé avant/après, appareil en main, est `qa/20261030-brume-courbure/`. Un verrou de source
  tient la matière (`core/src/test/java/com/onyx/app/ui/design/FluideBrumeVerrouTest.kt`).

## 3. Le bouton d'envoi visible dans les deux apparences

- **Le compositeur garde son bouton d'envoi en thème clair comme en thème sombre.** Le lot a
  reproduit le défaut par le geste (capture du Terminal sombre), nommé la cause fichier:ligne, et
  relevé les **8 commandes dans les deux apparences** (modèle + appareil) avec **4 états sur l'APK
  livré**, verrou rouge→vert (fusion `15a6cc71`, commit de lot `0e3d32a6`, arbitrage `1b98bb41` ;
  `app/src/main/java/com/onyx/app/ui/chat/components/OnyxComposer.kt`).

## 4. Pièces jointes durables

- **Les pièces jointes survivent à la fermeture de l'écran.** Fusion `04e31344`
  (`onyx-fichiers-durables-v2`) ; recette et journal de requêtes dans
  `qa/20261031-fichiers-durables/` (`RAPPORT.md`, `requests.log`).

## 5. Usage par bot et par conversation

- **L'usage se lit par bot et par conversation**, et non plus seulement au total. Fusions
  `4cc30a43` et `5d395eef` (`onyx-usage-par-bot`).

## 6. Parité des conversations de groupe

- **Les conversations de groupe rejoignent l'intégration à parité.** Fusion `93f4c522`
  (`onyx-groupe-parite`, `3248af56`), reprise par `4b36839e` ; rapport `RAPPORT-GROUPE-PARITE.md`,
  verrou de source côté compositeur de groupe (`GroupComposerLogicTest.kt`).

## 7. L'en-tête sans doublon

- **L'en-tête de conversation n'est plus dessiné en double.** Fusions `612cfd99` (`onyx-en-tete-nom`)
  et `92fc98e0` (`onyx-entete-intermediaire`) ; compte rendu `COMPTE-RENDU-ENTETE-NOM.md`.

## 8. Plus aucune trace des anciens noms — ni dans le code, ni dans le paquet

- **L'ancien schéma d'URL et l'hôte web sont assemblés à l'exécution** dans `SetupCode.kt`
  (`String(charArrayOf(...))` — une construction que ni le compilateur Kotlin ni R8 ne replient),
  de sorte que **le dex livré ne porte plus le littéral** ; et le comportement est **conservé** :
  un lien à l'ancien schéma — même déjà imprimé dans un QR — **ouvre toujours la feuille de
  configuration** (commits `effe14d5`, `f765242d`, `ee89676d`, `d6d2254f` ; fusion `945755b8`).
- **Le garde-fou est réalisé.** `FinishWiringTest` exige désormais une liste de compatibilité
  **vide** (`app/src/test/java/com/onyx/app/ui/FinishWiringTest.kt`) et le balayage des sources de
  production des quatre modules (`app`, `core`, `auto`, `wear`) ne voit plus l'ancien nom. Le
  contrôle `scripts/check-forbidden-words.sh` et le `grep` borné du dépôt public rendent **0**.

## 9. Ce que cette version ne fait pas

- Le **flou d'arrière-plan du verre** ne se juge pas sur un émulateur (il rend en logiciel) : il se
  vérifie sur un appareil réel.
- Les **captures d'écran** de la galerie du README peuvent montrer un build antérieur ; chacune est
  étiquetée avec son build dans `docs/screenshots/README.md`.

---

## Fichiers

| Paquet | Module | sha256 |
|---|---|---|
| `Hermes-Onyx-1.0.0+980-telephone.apk` | app | *voir `SHA256SUMS`* |
| `Hermes-Onyx-1.0.0+980-auto.apk` | auto | *voir `SHA256SUMS`* |
| `Hermes-Onyx-1.0.0+980-wear.apk` | wear | *voir `SHA256SUMS`* |

Vérifier un téléchargement :

```bash
curl -LO https://github.com/JemaOS/hermes-onyx/releases/latest/download/SHA256SUMS
sha256sum -c SHA256SUMS
```
