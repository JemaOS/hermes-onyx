# Audit de performance — le fil de conversation (3ᵉ passe, mesurée à l'appareil)

> **Arbre audité** : worktree `onyx-audit3`, branche `perf/lot3`, `main` @ `95b1b7f` (1.3.40).
> **Plainte** : « quand il y a beaucoup d'informations dans un chat, ça lag au défilement ».
> **Appareil visé** : Snapdragon 8 Elite (3ᵉ génération). **Aucun téléphone branché** sur cette
> machine : la mesure a été faite sur un **émulateur** (AVD `onyxperf`, Android 16, 1080 × 2400,
> 420 dpi, `-gpu host` sur une RTX 4070 SUPER), et la §6 dit exactement ce que l'émulateur prouve et
> ce qu'il ne prouve pas.
> **Passes précédentes** : `AUDIT-PERFORMANCE.md` (postes « par jeton » et « par trame ») et
> `AUDIT-PERFORMANCE-2.md` (corps d'écran et racine rejoués au jeton) — leurs corrections sont dans
> `main` et **ne sont pas re-corrigées ici**.

## 1. Ce que cette passe a de nouveau : des trames, pas des millisecondes de JVM

Les deux passes précédentes n'avaient **aucune mesure de trame** : elles prouvaient le travail d'une
rangée sur la JVM, et renvoyaient le reste à « un jour, sur ton téléphone ». Cette passe mesure des
**trames réelles** : `dumpsys gfxinfo <pkg> framestats`, dépouillé par
`tools/perf/framestats.py` (ajouté ici), sur un fil de **40 tours / 80 messages / 302 Kio de texte**
(produit par `tools/perf/gen-dense-chat.py`, copié ici), et sur une **réponse qui s'écrit**
(`tools/perf/mock-gateway.py`, un jeton toutes les 33 ms).

Protocole, rejouable :

```bash
# 1. l'arbre, la release minifiée (jamais la debug : cf. §6)
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk

# 2. un fil dense : on remplace le fichier de conversations de l'app (adb root sur l'émulateur)
python3 tools/perf/gen-dense-chat.py /tmp/conversations.json 40
adb push /tmp/conversations.json /data/local/tmp/c.json
adb shell "cp /data/local/tmp/c.json /data/data/com.onyx.app/files/conversations.json && \
           chown $(adb shell stat -c %u /data/data/com.onyx.app):$(adb shell stat -c %g /data/data/com.onyx.app) \
           /data/data/com.onyx.app/files/conversations.json"

# 3. le défilement : glissements LENTS (1,2 s), trois passes
SERIAL=emulator-5554 OUT=perf-out tools/perf/measure-drag.sh base 6 3
```

## 2. Les chiffres — avant / après, même appareil, même geste

### 2.1 Pendant qu'une réponse s'écrit : **le défaut principal, trouvé et corrigé**

Une réponse de 12 000 caractères (titres, listes, tableaux, blocs de code — la forme exacte d'un
rapport d'agent), un jeton toutes les 33 ms. Durées par trame, `framestats`, 120 trames :

| | thread UI (p50) | dont enregistrement du dessin | trame totale (p50) |
|---|---|---|---|
| **1.3.40 (release)** | **17,5 ms** | **15,7 ms** | 36,1 ms |
| expérience : Markwon retiré du flux | 1,4 ms | 0,85 ms | 20,5 ms |
| **correction retenue** (`StreamingProse`) | **1,8 ms** | 2,4 ms | **21,0 ms** |

Lecture : à 33 jetons par seconde, le thread principal passait **17,5 ms sur un budget de 16,7** —
il ne pouvait pas tenir la cadence, donc chaque trame était en retard et une réponse qui s'écrit
saccadait **par construction**, quel que soit le téléphone. Ce n'était pas le découpage Markdown
(mesuré à 0,5–1,3 ms par jeton, journal instrumenté) : c'était **le `TextView` de Markwon dont le
contenu change à chaque trame**. Une vue du système réenregistre sa *display list* à chaque
`setText`, et Compose doit réenregistrer son calque par-dessus.

**La correction** : le bloc de prose **en cours d'écriture** ne passe plus par Markwon, mais par
`StreamingProse` (texte de Compose : gras, italique, code en ligne, barré, liens, titres, puces,
cases). Dès que la réponse est finie, la rangée reprend le chemin Markwon — rendu complet, une seule
fois, mis en cache. Le texte du flux ne perd donc rien : il gagne un rendu qui ne coûte pas une vue.

Vérifié à l'écran (capture `perf-out-streamprose/end.png`) : puces, gras, code en ligne et titres
sont rendus pendant le flux, sans astérisques visibles.

### 2.2 Au défilement d'un fil dense (au repos, pas de flux)

| | thread UI (p50 / p90 / p99) | trame totale |
|---|---|---|
| 1.3.40 | 0,8 / 2,1 / 11,6 ms | 18,9 ms |
| après ce lot | 0,8 / 1,4 / 12,6 ms | 18,9 ms |

**Le défilement d'archives n'était déjà plus un problème de thread principal** : 0,8 ms de travail
Compose par trame. Ce que la mesure désigne, c'est le **dessin** (`IssueDrawCommands → SyncStart`,
~16 ms) et le **GPU** (~17 ms), c'est-à-dire le compositeur de l'émulateur (rendu translaté vers un
pilote hôte, 1080 × 2400). C'est la limite de l'instrument, pas un verdict sur ton téléphone — §6.

### 2.3 Le coût du dictionnaire — un défaut réel, trouvé en chemin

`BasicText` (le texte de Compose de la maison) **traduit chaque chaîne** qu'on lui donne :
`OnyxLocalization.translate` interroge le dictionnaire, puis essaie une centaine d'expressions
régulières ancrées `^…$` **sur la chaîne entière**. Une réponse de 27 674 caractères y passait donc
en entier — à chaque composition de la rangée. Mesuré (`TextPathCostTest`, JVM) :

| appel | avant | après |
|---|---|---|
| `translate(réponse de 27 674 car.)` | **30,5 µs** | **0,2 µs** |
| `resolveOnyxText(réponse, localize = true)` | 17,4 µs | 0,3 µs |
| `translate("Réglages")` — un vrai libellé | 20,7 µs | 20,7 µs (inchangé) |

La correction est dans `OnyxLocalization.translate` : au-delà de 512 caractères, ou dès qu'il y a un
saut de ligne, la chaîne est rendue telle quelle. Le plus long libellé réel de l'app fait 392
caractères et n'a pas de saut de ligne (les textes multi-paragraphes passent par
`translateParagraphs`, qui découpe ligne par ligne) : **aucun libellé ne perd sa traduction**.

## 3. Les autres corrections de ce lot

| # | Défaut | Où | Horloge | État |
|---|---|---|---|---|
| 28 | Le bloc de prose vivant était un `TextView` Markwon réenregistré à chaque jeton | `MarkdownText.kt` → `StreamingProse.kt` | par jeton | **corrigé + mesuré (§2.1)** |
| 29 | Le corps d'une réponse passait par le dictionnaire de traduction (100 regex ancrées) | `OnyxLocalization.kt:546`, `MarkdownText.kt` (`MarkdownPlain`) | par composition | **corrigé + mesuré (§2.3)** |
| 30 | Les pinceaux de la matière (dont un `ImageShader` = un `SkShader` **natif**) étaient reconstruits **à chaque image** de défilement | `Material.kt` (`matiereBackdrop`) | par trame | **corrigé** (`drawWithCache`) |
| 31 | Idem pour le halo d'accent (un dégradé vertical par image) | `Halo.kt:106` (`haloBackdrop`) | par trame | **corrigé** (`drawWithCache`) |
| 32 | Le cache de rendu Markwon ne tenait que 16 entrées : une remontée de fil le vidait et re-parsait chaque rangée | `RowCaches.kt:34` | par entrée | **corrigé** (48) |

Les deux corrections de dessin (§30, §31) ne changent **aucun pixel** : mêmes pinceaux, mêmes
couleurs, même ordre de peinture. Elles suppriment seulement leur reconstruction par image. Ce
qu'elles valent sur un 8 Elite ne peut pas être mesuré ici (le GPU de l'émulateur est le facteur
limitant, §2.2) ; sur l'émulateur, la trame totale ne bouge pas d'un pouce, ce qui est cohérent avec
un poste dominé par le compositeur.

## 4. Ce que l'audit a **écarté**, avec la mesure qui l'écarte

- **La source Haze n'est pas le coût du défilement.** Mesure : source retirée
  (`perf-out-nohaze/`), trame totale 35,8 ms contre 35,1–35,8 ms — **aucun gain**. L'hypothèse
  « le fond est réenregistré et rastérisé à chaque image » (§6.2 de la passe 2) **ne tient pas** sur
  cette scène : ce n'est pas elle qui fait saccader un fil chargé.
- **La prose sans balisage n'est plus une vue Android** (passe 2, §6.1) : confirmé, `MarkdownPlain`
  passe par Compose. Ce qui restait à corriger, c'était le **bloc vivant** — n'importe quelle prose,
  balisée ou non, y passait par Markwon dès qu'un marqueur apparaissait.
- **Le profil de démarrage n'est pas absent** : l'APK contient `assets/dexopt/baseline.prof`
  (13 319 octets, règles des bibliothèques Compose/Haze fusionnées par AGP). Il ne contient **pas**
  de règles pour le code de l'app (`com/onyx/app` absent) : en produire demande un
  macrobenchmark **sur un appareil physique** (§7 d).
- **Toutes les composables de l'app sont sautables.** Rapport du compilateur Compose
  (`app/build/compose_compiler/app-composables.txt`, activé par ce lot dans `app/build.gradle.kts`) :
  **573 `restartable skippable`, 0 `restartable but not skippable`**. Aucune rangée n'est forcée de
  recomposer par un paramètre instable — l'hypothèse la plus fréquente d'un « ça lag » est donc
  écartée, nommément.

## 5. Ce qui reste ouvert

1. **Le coût par entrée de rangée sur un vrai appareil.** `RowCaches.derived` mesuré à 97–179 µs
   pour une réponse de 12 000 caractères (émulateur), `splitMarkdown` à 7 µs en cache et 68–75 µs à
   froid (JVM) : ce n'est pas un poste de saccade. La mesure manquante est le **nombre de rangées
   recomposées par seconde** pendant un geste, lisible au Layout Inspector (« recomposition counts »).
2. **Le rendu Markdown en Compose natif** pour une réponse **finie** (passe 2, §6.1) : c'est le
   dernier poste « par entrée » qui reste une vue du système. Il ne se justifie que si la mesure à
   l'appareil désigne encore les rangées balisées **au défilement** (pas au flux : c'est réglé).
3. **Le `PostFrameCallback` du verre et la lentille** (Android 13+) : trois surfaces échantillonnent
   la page. Sur un appareil à 120 Hz, c'est le poste GPU le plus probable — à trancher par la trace
   Perfetto (§6 c).
4. **`conversations.json` réécrit en entier** à chaque écriture (documenté dans `docs/PERF-FLUX.md`) :
   ce n'est pas une saccade de défilement, c'est un coût d'écriture et de mémoire. Hors périmètre de
   cette passe, et toujours ouvert.

## 6. Ce que l'émulateur prouve, et ce qu'il ne prouve pas

**Prouvé** (les deux sens de la comparaison sont sur le même instrument, même geste, même fil) :

- un thread principal qui passait **17,5 ms par trame** pendant un flux en passe **1,8** : le défaut
  existait, il est corrigé, et le rapport de 10× est mesuré ;
- le défilement d'archives ne coûte **déjà plus** de thread principal (0,8 ms) ;
- la source Haze n'explique pas la saccade du défilement (source retirée = aucun gain) ;
- toutes les composables sont sautables, et le dictionnaire de traduction coûtait 30 µs par
  composition sur une réponse — les deux mesurés sur la JVM, pas supposés.

**Non prouvable ici** :

- **les millisecondes absolues**. Le GPU de l'émulateur (`-gpu host`, GLES translaté vers un pilote
  de bureau) est le facteur limitant du dessin : 16 ms d'enregistrement et 17 ms de GPU par trame au
  défilement, sur une RTX 4070 SUPER. Un Adreno 830 ne se comporte pas comme un pilote translaté. Ce
  sont donc les **rapports** et les **régressions** qui se lisent ici, jamais « ta trame fera X ms » ;
- **le comportement à 120 Hz** : l'AVD est en 60 Hz, et le budget de trame double sur ton écran ;
- **le coût réel du verre** (Haze + lentille) : l'émulateur ne rastérise pas ces flous comme un
  Adreno.

## 7. Le protocole sur ton téléphone (10 minutes)

```bash
# a) la version qui compte : la release, installée par-dessus (même clé)
./gradlew :app:assembleRelease && adb install -r app/build/outputs/apk/release/app-release.apk

# b) le geste qui fait mal : défiler 10 s dans un fil chargé, à la main, à vitesse constante
adb shell dumpsys gfxinfo com.onyx.app reset
# …défiler…
adb shell dumpsys gfxinfo com.onyx.app | sed -n '1,12p'
```

Ce qu'on veut lire sur un 8 Elite : **`Janky frames` sous ~2 %**, 99ᵉ percentile **sous 30 ms**
(un écran à 120 Hz divise le budget : 8,3 ms). Puis le même geste **pendant qu'une réponse s'écrit**
(poser « liste les 40 fichiers de ce dépôt et explique chacun », défiler pendant la réponse) : c'est
la scène du §2.1, et le chiffre doit maintenant être **le même** que hors flux.

Si le défilement reste saccadé alors que le thread UI est sous 2 ms, la trace fine désigne le
coupable :

```bash
adb shell perfetto -o /data/misc/perfetto-traces/onyx.perfetto-trace -t 10s \
  sched freq idle gfx view binder_driver
adb pull /data/misc/perfetto-traces/onyx.perfetto-trace   # ui.perfetto.dev
```

Pistes à lire : `main` (Compose), `RenderThread` (dessin), `GPU completion`. Un `RenderThread` plein
avec un `main` vide = le verre et la lentille (§5.3) ; un `main` plein = une rangée qui recompose
encore.

**(d) Le profil de démarrage du code de l'app** (une fois, avec le téléphone branché) : module
`baselineprofile` + test Macrobenchmark sur la `LazyColumn` du fil, puis
`./gradlew :baselineprofile:pullAndGenerateBaselineProfile`. L'APK ne contient aujourd'hui que les
règles des bibliothèques (§4).

## 8. Ce que les suites prouvent

- `:core:testDebugUnitTest` et `:app:testDebugUnitTest` : compteurs lus **dans les XML**, §9.
- `StreamingProseTest` (10 tests) : le rendu du flux **ne perd aucun caractère** (hors marqueurs
  remplacés), un marqueur non fermé **reste littéral** (comme Markwon), le code ne se ré-analyse pas,
  un `snake_case` n'est pas de l'italique, un lien garde son libellé, les puces et cases deviennent
  des glyphes, un titre est une marque de ligne entière.
- `TextPathCostTest` : **l'instrument** de la §2.3 (aucune assertion de temps : un seuil sur une JVM
  de CI échouerait au premier build à froid).
- `RowCachesTest` / `MarkdownCacheTest` : la LRU de rendu, portée à 48 entrées, reste bornée et sa
  clé est le contenu.

## 9. Résultat des suites

```
$ ./gradlew :core:testDebugUnitTest :app:testDebugUnitTest --rerun-tasks
BUILD SUCCESSFUL in 3m 36s
```

Compteurs lus **dans les XML** (`build/test-results/testDebugUnitTest/*.xml`), horodatages vérifiés
postérieurs au lancement :

| Module | Fichiers XML | Tests | Échecs | Erreurs | Ignorés |
|---|---|---|---|---|---|
| `:core` | 283 | **1 951** | 0 | 0 | 1 |
| `:app` | 306 | **2 216** | 0 | 0 | 0 |

Les deux verrous touchés par ce lot ont été **déplacés, pas relâchés** : `OnyxBaseLockTest` exige
toujours que tout fichier d'interface appartienne à une route — `StreamingProse.kt` y entre, donc il
est audité comme les autres ; `MarkdownCacheTest` exige toujours un cache **borné**, avec sa nouvelle
borne (48). Les scripts `tools/perf/measure-drag.sh` et `tools/perf/framestats.py` sont ajoutés pour
que la mesure soit rejouable en une commande.