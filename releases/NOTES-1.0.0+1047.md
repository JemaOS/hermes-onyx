# Hermes Onyx — 1.0.0+1047

`versionCode 1047`, `versionName 1.0.0+1047` (relevés au `aapt2 dump badging` sur les trois paquets).

Les sources publiées sur `main` sont la **copie nettoyée de l'arbre d'intégration rev 1047**
(`b1da0ced`, 1047 commits) : même transformation que la passe précédente, mots interdits retirés.
Le `versionCode` publié est écrit une fois dans `gradle.properties` (`onyx.versionCode=1047`), lu par
les trois modules ; un build de vérification depuis ces sources rend
`versionCode='1047' versionName='1.0.0+1047'` sur `app`, `auto` et `wear`.

## Ce qui change depuis 1.0.0+988

Mesuré sur le dépôt public : **106 fichiers modifiés, 12 ajoutés** (118 entrées,
+6132 / −827 lignes). Lots fusionnés depuis l'arbre de la 988 :

- **encadré dans le verre de la carte** — le bord retombe à 0, l'encre sur la plaque de contenu tient
  **4,53:1** ; la feuille « Modèle » n'empile plus deux surfaces ;
- **fiche d'usage sur une source unique** — le modèle en vigueur (`ModelInForce`) alimente le relevé
  d'agent **et** la fiche par bot ; les compteurs à zéro sont expliqués
  (`FicheUsageModeleEnVigueurVerrouTest`, `ModeleEnVigueurFicheCasTest`, `ChatUsageCoherenceTest`) ;
- **lecture de voix qui ne hache plus** — pré-tampon, priorité de fil audio, piste remplie avant
  `play()` (`PcmPlaybackTest`) ;
- **repère-visage entier** (`RepereVisageVerrouTest`) et **synchro des visages par conversation**
  (`CarteVisageSynchroTest`).

## Paquets

| paquet | octets | sha256 |
|---|---|---|
| `Hermes-Onyx-1.0.0+1047-telephone.apk` | 12 497 884 | `abdf43eadcfce3ebc4b91438b7675cbd318578dd6b0dfd97a48bef6c9dbfd8a7` |
| `Hermes-Onyx-1.0.0+1047-auto.apk` | 12 678 660 | `779fa3c0e3ef5654de499caa8cea3e569e8703cd7c3c0e09986b987b058e437e` |
| `Hermes-Onyx-1.0.0+1047-wear.apk` | 35 541 717 | `ee63cfd2ff2d9e48a0c3a3f484896d4887e19cb7c4972b40784c8a27498ad8d0` |

`com.onyx.app` / `com.onyx.app.auto` / `com.onyx.app.wear`. `sha256sum -c SHA256SUMS-1047.txt` :
**3/3 réussis**. Signature : certificat `CN=Hermes Onyx, OU=Android, O=JemaOS, L=Paris,
ST=Ile-de-France, C=FR`, SHA-256 `bf43641666c36da0e7058547f85b0922c5ed14b5da86a812b08e0e67d1ed4049`
sur les trois — **le même que le paquet `1.0.0+988` déjà publié** (`apksigner verify --print-certs`) :
la mise à jour en place depuis `1.0.0+988` est donc normale, sans désinstallation.

## Tests

`:core` **1702 tests** (1 ignoré) + `:app` **1971 tests** = **3673 tests, 0 échec**, sur les sources
publiées (`./gradlew :core:testDebugUnitTest :app:testDebugUnitTest`).
