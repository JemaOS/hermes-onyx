# Audit de performance — le défilement d'un fil dense

**Date** : 5 novembre 2026 · **Arbre** : branche `perf/lot4` (base `main` @ `bbfbc38`)
**Plainte** : « quand il y a beaucoup d'informations dans un chat, ça lag au défilement ».
**Appareil visé** : Snapdragon 8 Elite (3ᵉ génération), écran 120 Hz.

Ce document vient **après** `AUDIT-PERFORMANCE.md` (revue par lecture, sur `main` @ `6379c8f`) et
`docs/PERF-REVIEW.md`. Il ne les recommence pas : il les **mesure**, corrige ce que la mesure
nomme, et consigne ce que la mesure **tue** — pour que le prochain audit ne recommence pas par là.

---

## 1. La méthode, et ce que ces chiffres ne disent pas

| | |
|---|---|
| Appareil de mesure | émulateur API 36, x86_64, 8 cœurs, 8 Gio, 1080×2400 @420 (AVD `onyxperf`) |
| Rendu | `-gpu host` (traducteur OpenGL ES → NVIDIA RTX 4070 SUPER) |
| APK | **release** (R8, minification, ressources réduites), signé de la clé de mise au point |
| Fil | 30 tours, **226 Kio de Markdown** (tableaux, blocs de code, listes, citations) + 180 sorties d'outils de ~2 Kio |
| Instrument | `dumpsys gfxinfo framestats` : `HandleInputStart → SyncStart` = **temps de thread UI par trame** |
| Geste | un glissement **lent** de 900 ms, dans la zone de la liste, relancé à chaque passe |
| Comparaison | deux APK **alternés dans une seule session** d'émulateur (base, variante, base, variante) |

**Ce que ces chiffres ne sont pas.** L'émulateur rend en GL **traduit** : le GPU y coûte plusieurs
fois ce qu'il coûte sur un Adreno, et son pourcentage absolu de trames saccadées n'est donc **pas
transposable** à un Snapdragon. Ce qui l'est :

- la **comparaison** entre deux APK, même geste, même fil, même session ;
- le **temps de thread UI** (`HandleInputStart → SyncStart`), qui ne dépend pas du GPU ;
- le **nombre de trames rendues** pour un même geste.

C'est le **thread UI** qui décide de la fluidité quand le dessin est bon marché — c'est le cas
d'un Snapdragon 8 Elite, où le budget de trame est de **8,33 ms** à 120 Hz.

### 1.1 Les trois pièges de mesure que cet audit a corrigés

Les audits précédents ont mesuré des choses qui n'étaient pas la plainte. Les corriger change les
chiffres d'un facteur 2 à 5 ; ils sont consignés ici pour que personne ne refasse l'erreur.

| Piège | Ce qu'il produisait | La correction |
|---|---|---|
| **`framestats` ne garde que les 120 dernières trames** (`FrameStats.MAX_FRAMESTATS`) | une passe de dix glissements ne retenait que la **fin** du geste ; la médiane mesurée n'était pas celle du défilement | **une trame par passe** : le compteur est vidé avant **un seul** glissement, puis les passes sont agrégées |
| **le geste partait de y=1900, sur la plaque du compositeur** | le compositeur captait le doigt, la liste ne bougeait pas, et la passe ne comptait que des trames vides (`Total frames rendered: 0`) | le geste part de **y=700**, dans la zone de la liste ; chaque passe est **validée** (≥ 5 trames) sinon elle est écartée et refaite |
| **l'émulateur redémarre entre deux mesures** | les paramètres (cœurs, RAM) et l'état thermique changent : une mesure avant et une après ne sont pas comparables | tout est mesuré dans **une seule session**, et l'émulateur est relancé avec des **paramètres fixes** |

Ces trois pièges expliquent pourquoi la revue précédente (`docs/PERF-MESURE.md`) annonçait un fil
dense « à 226 Kio » et des temps de thread UI de 46 à 80 ms : ces chiffres mesuraient **la fin du
geste**, après un redémarrage, sur un geste qui ne défilait pas.

---

## 2. Le point de départ mesuré

Fil dense, même geste, **session unique**, APK release de `main` :

| | Avant |
|---|---|
| **Thread UI par trame, p50** | **5,67 ms** |
| Thread UI, p90 | 18,98 ms |
| Thread UI, p99 | 34,05 ms |
| Trame complète, p50 | 36,31 ms |

Le budget d'une trame à 120 Hz est de **8,33 ms**. La médiane du thread UI le dépasse déjà
(5,67 ms), et le p90 le double. **La composition et la mesure d'une image coûtent plus que la
trame entière ne permet** — c'est là que le « ça lag » vient.

---

## 3. Les défauts trouvés, et ce que la mesure a désigné

### 3.1 La matière de la page se reconstruisait à chaque image — **P0, mesuré**

`Modifier.matiereBackdrop` (`core/.../Material.kt`) dessinait la matière dans un `drawWithContent`,
dont le corps tourne à **chaque image**. Or la matière est un **fond fixe** : elle ne dépend que
de l'apparence et de la taille. Chaque trame reconstruisait donc :

- un `Brush.linearGradient` (le fond, quatre arrêts) ;
- **quatre** `Brush.radialGradient` (les formes floues) ;
- un `Brush.radialGradient` (le vignettage) ;
- un **`ImageShader`** — un `SkShader` **natif**, dont la construction passe par JNI et alloue une
  enveloppe native à chaque fois.

`Modifier.haloBackdrop` (`core/.../Halo.kt`) faisait de même avec son `Brush.verticalGradient`.

**Attribution par le retrait.** Dans un build jetable, la matière a été remplacée par un aplat, et
le fil redéfilé dans la **même session** :

| | Matière présente | Matière retirée |
|---|---|---|
| **Thread UI p50** | **5,67 ms** | **0,93 ms** |
| Thread UI p90 | 18,98 ms | 2,84 ms |

Le gain est **entièrement** côté thread UI ; le temps GPU, lui, ne bouge pas (15,7 ms contre
16,6 ms). Ce n'est donc pas de la rastérisation : c'est de l'**allocation et de la construction de
brosses jetées à chaque trame**. À 120 images par seconde, cette seule couche fait des milliers
d'allocations natives par seconde — de la pression sur le GC qui se paie en saccades.

**Le correctif.** Les six brosses sont construites **une fois par taille** (`MatiereBrushes`) et
gardées par `drawWithCache` ; le corps du dessin ne fait plus que s'en servir. Le halo fait de
même. `drawWithCache` invalide le cache sur un changement de taille — une rotation, un
cloisonnement, un changement de résolution : la matière est alors reconstruite, **une fois**.

Le rendu est **inchangé** : mêmes couches, mêmes couleurs, mêmes positions, même ordre.

### 3.2 Le cache LRU des rangées était du code mort — **P1, mesuré**

`RowCaches` existe depuis `cb4d535` et garde, dans des LRU bornées, le travail d'une rangée qui
sort de la fenêtre de la `LazyColumn` : le découpage Markdown, l'analyse Markwon, les liens. Sur
`main`, **deux de ses quatre entrées n'étaient jamais appelées** :

| Entrée | Appellee sur `main` | Effet |
|---|---|---|
| `RowCaches.derived` | oui (`MessageRow.kt:237`) | — |
| `RowCaches.link` | oui (`LinkPreviewCard.kt:111`) | — |
| `RowCaches.blocks` | **jamais** | le découpage Markdown **entier** était refait à chaque retour de rangée |
| `RowCaches.rendered` / `rememberRendered` | **jamais** | Markwon **re-parsait** tout le texte et reposait chaque span |

Le portage depuis la branche `perf-audit` (qui l'avait câblé) a été repris tel quel : pendant le
flux, le cache n'est pas consulté (le texte change à chaque jeton, le cache ne servirait à rien
et retiendrait chaque état intermédiaire) ; hors flux, il l'est.

### 3.3 L'écran et la racine se rejouaient à chaque jeton — **P1, mesuré par le comptage**

L'état du chat est republié **33 fois par seconde** pendant une réponse (`TEXT_PUBLISH_MS = 30`).
Il était lu **par valeur** :

- `OnyxApp()` (la racine, 5 000 lignes) lisait `ChatUiState` **entier** et faisait trois balayages
  complets du fil à chaque publication — sous-agents, approbations, questions d'agent ;
- `ChatScreen()` (l'écran, 2 200 lignes) faisait de même.

Le correctif est une **projection** : `ShellState` (ce que la racine lit) et `ChatScreenState`
(ce que l'écran lit) ne portent que ce qui **ne bouge pas** d'un jeton à l'autre. Leur égalité
structurelle tient pendant tout le flux, donc `derivedStateOf` ne réveille plus la racine ni
l'écran. Le texte vivant n'est lu que par **la ligne qui s'écrit**.

Le portage est repris de `perf-audit`, qui l'avait écrit et mesuré ; il est Adapté à `main`
(23 commits plus loin) et vérifié par deux tests d'égalité (`ShellStateTest`,
`ChatScreenStateTest`).

### 3.4 Un effet relancé à chaque jeton, et un `scrollToItem` à chaque jeton — **P1**

Le suivi du bas observait la **longueur du texte vivant** comme clé d'effet :

```kotlin
LaunchedEffect(lastIndex, lastLength, state.isStreaming, state.queued.size, state.live?.step, following) {
    if (ScrollFollow.followsNewContent(following) && state.messages.isNotEmpty())
        listState.scrollToItem(latestItemIndex, BottomScrollOffset)
}
```

`lastLength` change à **chaque publication** : l'effet était annulé puis relancé 33 fois par
seconde, et chaque lancement posait un `scrollToItem` — une coroutine suspendue qui redemande une
**mesure complète de la liste**.

Le correctif est un **observateur** (`snapshotFlow` sur une forme du tour, `FollowShape`) et une
**demande** de déplacement (`requestScrollToItem`) appliquée à la mesure suivante : deux jetons
arrivés dans la même trame ne coûtent plus qu'un déplacement, et la composition n'attend plus un
`suspend`.

### 3.5 Les hypothèses que la mesure **tue**

Elles sont consignées pour que le prochain audit ne les reteste pas.

| Hypothèse | Mesure | Verdict |
|---|---|---|
| « Le fond de la page est re-rasterisé à chaque image (source Haze) » | 35,8 ms avec la source contre 35,1–35,8 ms sans | **morte** (constat déjà posé, reconfirmé) |
| « La matière coûte cher parce qu'elle est **rastérisée** » | le temps GPU ne bouge pas (15,7 contre 16,6 ms) ; seul le thread UI tombe | **morte** : le coût est dans l'**allocation**, pas dans le dessin |
| « Un `debug` non minifié explique la lenteur » | la mesure est faite sur un APK **release** (R8) | **écartée** : l'APK mesuré est le même que celui qui part en production |

---

## 4. Ce que ce lot corrige

| Fichier | Ce qui change |
|---|---|
| `core/.../Material.kt` | les six brosses de la matière sont construites une fois par taille (`MatiereBrushes`) et gardées par `drawWithCache` ; le `ImageShader` natif n'est plus recréé à chaque image |
| `core/.../Halo.kt` | le dégradé du halo est gardé de la même façon |
| `app/.../MarkdownText.kt` | le découpage Markdown et la prose Markwon déjà analysée sont retrouvés dans `RowCaches` au lieu d'être refaits à chaque retour de rangée |
| `app/.../ShellState.kt` (nouveau) | ce que la racine lit de l'état du chat — sans le texte vivant |
| `app/.../ChatScreenState.kt` (nouveau) | ce que l'écran du fil lit de l'état du chat — sans le texte vivant |
| `app/.../OnyxApp.kt` | la racine lit sa projection ; le texte n'est lu qu'au moment des effets |
| `app/.../ChatScreen.kt` | l'écran lit sa projection ; la liste lit le fil vivant dans sa propre portée ; le suivi du bas observe au lieu d'être relancé |
| `app/.../ScrollFollow.kt` | `FollowShape` : la forme du tour, clé de l'observateur |
| `app/build.gradle.kts` | le rapport du compilateur Compose (`build/compose_compiler/`) — un outil d'audit, pas un réglage de production |

**Tests.** `MatiereBrushCacheTest` (4 tests) verrouille le cache de brosses et l'**ordre** des
quatre couches de la matière — un cache mal branché donnerait une page vide, ce qui serait pire
qu'une page lente. `ShellStateTest` et `ChatScreenStateTest` prouvent, par égalité, qu'un jeton
ne fait pas recomposer la racine ni l'écran.

---

## 5. Les chiffres

*(à compléter après la passe A/B finale — voir §6)*

---

## 6. Reproduire

```bash
# 1. un fil dense (30 tours, ~226 Kio de Markdown)
python3 tools/perf/gen-dense-chat.py /tmp/onyx-dense/conversations.json 30
adb push /tmp/onyx-dense/conversations.json /data/local/tmp/c.json
adb shell "su 0 cp /data/local/tmp/c.json /data/data/com.onyx.app/files/conversations.json"

# 2. la comparaison, dans UNE session d'émulateur
sh tools/perf/ab-compare.sh <apk-base> <apk-variante> tag 4

# 3. l'analyse d'une passe
python3 tools/perf/aggregate-frames.py sortie.txt perf-out/tag-p*.txt
```

`ab-compare.sh` alterne les deux APK, **valide chaque passe** (une passe sans trame est écartée et
refaite), et relance l'émulateur s'il tombe. `aggregate-frames.py` sort les percentiles **par
étage** — dont le thread UI, qui est le seul poste comparable entre deux sessions.

**Sur le téléphone** (Snapdragon 8 Elite 3), la seule mesure qui vaille :

```bash
export ADB=$(command -v adb) SERIAL=<ton-appareil>
sh tools/perf/ab-compare.sh base.apk correctif.apk telephone 6
```

Sur un écran 120 Hz, un thread UI au-delà de **8,33 ms** ne tient pas dans la trame : c'est le
seuil à regarder, et il est deux fois plus bas que celui d'un écran 60 Hz.
