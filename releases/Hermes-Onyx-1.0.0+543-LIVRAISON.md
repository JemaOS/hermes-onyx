# Livraison — troisième fusion (dix lots), version `1.0.0+543`

- **Version** : `versionName = 1.0.0+543`, `versionCode = 543` — **la même sur les trois** APK.
  (543 > 502 comme demandé ; dérivée de `git rev-list --count HEAD` = 543 ; **aucun** autre APK de
  `releases/` ne porte le versionCode 543 — vérifié par `aapt2 dump badging` sur chaque APK présent.)
- **Branche / HEAD** : `fusion/finale-464` @ le commit du rapport de fusion (rev 543).
- **Commit propre laissé pour le QA final** : l'arbre de livraison est committé, sans marqueur de
  conflit. Le seul ajout post-build est **ce relevé** (un `.md`), replié dans le commit du rapport par
  `--amend` : le compte de commits — donc la version — **ne change pas** (543).

## Les trois APK

| Fichier | `applicationId` | sha256 |
|---|---|---|
| `Hermes-Onyx-1.0.0+543-telephone.apk` | `com.onyx.app` | `b89007a3f24618dc8cf47f57d64644b4ede7c7ce17ea9361cdf7a4f1c5435132` |
| `Hermes-Onyx-1.0.0+543-auto.apk` | `com.onyx.app.auto` | `15659acaa6c48dbd42c7997072ad228968d3c77687364e77638a38025e8c1ea2` |
| `Hermes-Onyx-1.0.0+543-wear.apk` | `com.onyx.app.wear` | `1aa84c43052dc1ed2bb57a298d86e3426ea6a17fa3022c9713f848cc431da143` |

- `Hermes-Onyx-1.0.0+543-SHA256SUMS` — **`sha256sum -c` passe** (les trois : « Réussi »).
- **Même clé** sur les trois : `apksigner verify --print-certs` →
  SHA-256 `fa3364fdb2ca25dd91ce3f7f4731c7b472d46f9af5b11152388a595bf01112cb` (identique sur les trois).
  Le DN de la clé (le nom de la firme d'origine) est une propriété **préexistante** du magasin
  `le magasin de clés local — non modifiée ici (la changer changerait la clé).
- Aucun nom partagé écrasé : les trois fichiers sont nommés `Hermes-Onyx-<version>-{telephone,auto,wear}.apk`
  et l'absence de collision a été vérifiée **avant** écriture.

## Recette

- Environnement : `systemd-run --user --scope -p MemoryMax=32G` (hors cgroup restreint),
  `ONYX_RELEASE_KEYSTORE=le magasin de clés local
- Commande :
  `:core:testDebugUnitTest :app:testDebugUnitTest :app:assembleRelease --no-daemon --console=plain
  -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.workers.max=2`
  puis `:auto:assembleRelease :wear:assembleRelease`.
- Tests : **2451, 0 échec** (core **1166**, app **1285**) — au-delà des ≥ 2309 attendus. Flakes connus
  (`KanbanLiveBoardTest`, `GroupVoiceLoopTest`, `ResponsiveLayoutTest`) verts à ce passage.
- `:app:assembleRelease` → **BUILD SUCCESSFUL** ; `:auto:assembleRelease :wear:assembleRelease` →
  **BUILD SUCCESSFUL**.

## Reste pour le QA final

Voir `qa/20261028-fusion-finale/RAPPORT.md` (§ « Reste pour le QA final ») : passe visuelle sur
appareil (sombre puis clair, côte à côte avec le build accepté), montée des états réels (compte,
consommation), confirmation du **compte de tuiles** (11 = 6 + 5, contre 9 annoncés au brief), et le
libellé « Configuration » à la taille plancher sur un 411 dp à police 1,3×.
