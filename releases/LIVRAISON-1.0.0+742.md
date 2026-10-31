# Livraison — réflexion discrète (lot `reflexion-discrete`)

- **Version** : `1.0.0+742` (versionCode `742` = `ONYX_VERSION_CODE`, > 741)
- **Commit** : (voir `git log -1`) — branche `lot/reflexion-discrete`, base `cbec47ea` (`fusion/reconciliation`)
- **Recette** : `:core:testDebugUnitTest :app:testDebugUnitTest :app:assembleRelease`
  → **BUILD SUCCESSFUL**, **3177 tests, 0 échec** (core 1485 + app 1692),
  `LANG=fr_FR.UTF-8 LC_ALL=fr_FR.UTF-8`, keystore `le magasin de clés local
- **sha256** : voir `SHA256SUMS-1.0.0+742` — **relu sur l'appareil** (`emulator-5572`) : identique
- **Ce que le lot change** : le bloc de réflexion remplace le pavé de verre par un **filet vertical
  de 2 dp** et du texte en retrait sur la page ; il s'annonce en **petites capitales**
  (« RAISONNEMENT DU MODÈLE ») avec la mention « **brut, non traduit** » ; il se **tronque à six
  lignes** (Voir tout / Réduire) ; il est **à la marge du contenu** ; et la pastille « aller au
  dernier message » ne le recouvre plus (**67 397 px² → 0 px²**).
- **Preuves** : `qa/20261029-reflexion-discrete/RAPPORT.md` et `captures/`.
