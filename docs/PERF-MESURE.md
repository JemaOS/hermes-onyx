# Audit de performance mesuré — le défilement du fil

**Date** : 4 novembre 2026 · **Arbre** : branche `perf-audit` (base `d12000d` + le lot de défilement)
**Plainte** : « quand il y a beaucoup d'informations dans un chat, ça lag au défilement ».

Ce document **complète** `docs/PERF-REVIEW.md` : il ne remplace aucun de ses constats, il les
**mesure**, et il en corrige un que la lecture de code ne pouvait pas trancher — le coût qui revient
à chaque fois qu'une rangée **re-entre** dans la fenêtre de la liste.

---

## 1. Ce qui a été mesuré, avec quoi, et ce que les chiffres valent

| | |
|---|---|
| Appareil | émulateur API 36, x86_64, 6 cœurs, 4 Gio, 1080×2400 @420 (AVD `onyxperf`) |
| APK | **release** (R8, minification, ressources réduites), signé avec la clé de mise à jour |
| Fil de mesure | 30 tours, **226 Kio de Markdown** (tableaux, blocs de code, listes, citations) + 180 sorties d'outils de ~2 Kio |
| Instrument | `dumpsys gfxinfo` (`Janky frames`, percentiles) et `dumpsys gfxinfo framestats` |
| Geste | 18 glissements de 120 ms, 9 vers le haut puis 9 vers le bas, même zone, même position de départ |
| Rendu | `swiftshader` (GL **logiciel**) pour les comparaisons ; une passe `-gpu host` en contrôle |

**Ce que ces chiffres ne sont pas.** L'émulateur rend en GL logiciel : le dessin y coûte plusieurs
fois ce qu'il coûte sur un Adreno, donc le **pourcentage absolu de trames saccadées n'est pas
transposable** à un Snapdragon 8 Elite. Ce qui est transposable :

- la **comparaison** entre deux APK, même geste, même fil, même émulateur ;
- le **temps de thread UI** (`HandleInputStart → SyncStart`), qui ne dépend pas du GPU ;
- la **comparaison** à l'arrêt / pendant une réponse.

Contrôle au rendu matériel (`-gpu host`, NVIDIA) sur ce même fil, à l'arrêt : **0,3 % de trames
saccadées**, thread UI `p50 = 0,8 ms`. Autrement dit : quand le dessin n'est pas le goulet, la
composition du fil à l'arrêt est déjà saine — c'est le **travail par trame** qui décide, et c'est
celui que les chiffres ci-dessous suivent.

---

## 2. Les deux résultats, et celui qui n'est pas au rendez-vous

### 2.1 Défilement à l'arrêt : le cache divise le temps de thread UI par 1,6

Même fil dense, même APK à une correction près (`RowCaches`), même geste.

| | Sans cache | Avec cache | Écart |
|---|---|---|---|
| Trames rendues | 142 | 173 | — |
| **Trames saccadées** | **97,2 %** | **91,9 %** | **−5,3 pts** |
| **Thread UI, p50** | **59,0 ms** | **36,1 ms** | **−39 %** |
| Thread UI, p90 | 102,9 ms | 71,7 ms | −30 % |
| Trames « slow UI thread » | 76 / 142 (54 %) | 94 / 173 (54 %) | — |
| p50 de trame | 181,6 ms | 116,8 ms | −36 % |

Lecture : le temps que le thread UI passe par trame d'entrée tombe de **59 à 36 ms**. Sous GL
logiciel, le dessin reste le plafond (d'où un pourcentage de saccades encore haut) ; sur un appareil
où le dessin est gratuit, c'est cette colonne-là qui décide de la fluidité.

### 2.2 Défilement **pendant** qu'une réponse s'écrit : le cache ne suffit pas

Même fil, même geste, mais lancé deux secondes après l'envoi d'une question, servie par un faux
gateway local (`tools/perf/mock-gateway.py`, 24 caractères toutes les 35 ms, ~33 publications/s — la
cadence de `TEXT_PUBLISH_MS = 30`).

| | Sans cache | Avec cache |
|---|---|---|
| Trames rendues | 113 | 109 |
| Trames saccadées | 100 % | 100 % |
| **Thread UI, p50** | **80,3 ms** | **86,6 ms** |
| Thread UI, p90 | 102,2 ms | 97,5 ms |
| « slow UI thread » | 97 / 113 (86 %) | 100 / 109 (92 %) |
| p50 de trame | 236,7 ms | 266,6 ms |

**Le cache ne change rien ici, et c'est le résultat le plus utile de cet audit.** La différence est
dans le bruit de mesure (une passe chacune, GL logiciel) ; la conclusion, elle, est nette : pendant
qu'une réponse s'écrit, le coût n'est **pas** dans le retour des rangées d'archive — il est dans la
**recomposition par jeton** de l'écran entier. Une rangée vivante n'est jamais « re-entrée » : elle
est recomposée trente-trois fois par seconde, avec le texte qui grandit.

Autrement dit : la plainte a **deux causes distinctes**, et ce lot n'en corrige qu'une.

---

## 3. Ce que ce lot corrige

### 3.1 Le travail d'une rangée survit à sa sortie de la fenêtre — `RowCaches.kt`

Une `LazyColumn` **jette** ses rangées hors écran. Tous les `remember` de la rangée repartaient donc
à vide, et chaque retour à l'écran refaisait :

- le découpage Markdown du texte entier (`splitMarkdown`) ;
- l'analyse Markwon de chaque paragraphe balisé, spans compris (`setMarkdown`) ;
- la recherche de diff, le découpage du diff, le JSON des tâches et la regex des fichiers livrés sur
  **chaque** sortie d'outil (`MessageDerived.of`) ;
- la regex du premier lien (`linkPreviewUrl`).

Ces quatre résultats ne dépendent que du contenu. Ils sont désormais gardés dans des **LRU bornées**
(64 rangées déduites, 24 découpages, 16 proses rendues, 64 liens), clefées par **contenu** —
identifiant + empreinte du texte et des outils — jamais par position.

L'empreinte est volontairement **courte à calculer** : longueurs et états, jamais le texte. Une
sortie d'outil qui arrive change de longueur et de statut ; un texte qui s'écrit change de longueur.
Les deux invalident l'entrée sans relire un demi-mégaoctet pour le savoir.

Effet mesuré : **§2.1**, −39 % de temps de thread UI par trame d'entrée.

### 3.2 La prose Markwon déjà analysée se retrouve — `MarkdownText.kt`

`AndroidView` recrée une `TextView` à chaque entrée de rangée ; `setMarkdown` re-parse alors tout le
texte et repose chaque span. Le `Spanned` produit (cases de tâche comprises) est maintenant gardé par
(texte, style) et rendu d'un `setText` — **en copie** (`SpannableString`), parce que deux rangées ne
doivent pas partager un `Spannable` que la sélection peut écrire. Le point de flux (`•`) reste hors
du cache : il n'existe que pendant la réponse.

### 3.3 L'animation d'arrivée ne se monte plus sur les rangées d'archive — `ChatScreen.kt`

`Modifier.messageArrival` est un `Modifier.composed` : monté sur **chaque** rangée, il ouvrait une
sous-composition et trois `animateFloatAsState` pour une animation qui ne concerne, en pratique, que
le message qu'on vient d'envoyer. Les rangées d'archive reçoivent `Modifier` nu.

### 3.4 Ce que ce lot ne refait pas

Le lot de défilement déjà présent dans l'arbre est conservé tel quel : `state.live` réservé à la
ligne vivante, `GlassScrollFreeze` hors de la portée de l'écran, `MessageDerived.of(includeText =
false)` pendant le flux, coche d'outil non animée pour une archive, `contentType` sur les `items`,
`pendingApprovalCount` sans allocation, `BotMentions.menu` mémorisé.

---

## 4. Ce qui reste — et que la mesure désigne, pas le raisonnement

1. **La recomposition par jeton de l'écran entier.** `OnyxApp()` lit `ChatUiState` à sa racine
   (`OnyxApp.kt:573`) et `ChatScreen` lit `state` dans son corps : à chaque jeton, ~4 700 lignes de
   racine et ~2 200 lignes d'écran sont ré-exécutées, et la portée de la `LazyColumn` est
   reconstruite avec une douzaine de lambdas par rangée visible. **C'est la cause du §2.2.** Sortie
   propre : un composable `ChatRoute` qui lit le flux et ne rend que le fil, et des lambdas de rangée
   stables (`remember(message.id)`), pour que seules la ligne vivante et la liste se recomposent.
2. **La prose de la ligne vivante** est re-découpée et re-parsée à chaque jeton (le texte change,
   c'est inévitable) ; ce qui ne l'est pas, c'est de re-parser **les blocs déjà terminés** de cette
   même réponse. Un découpage incrémental (garder les blocs clos, ne re-parser que le dernier) est le
   complément naturel du n°1.
3. **La page entière est la source Haze** (`ChatScreen.kt:918`) : chaque image de défilement
   réenregistre et rastérise le fond. Coût **de dessin**, pas de composition — la passe GPU à
   l'arrêt (0,3 %) dit qu'il n'est pas limitant à 60 Hz, elle ne dit rien d'un écran 120 Hz.
4. **Baseline profile** : toujours absent. À générer avec un macrobenchmark **sur le téléphone** —
   l'émulateur produirait un profil x86 qui ne dit rien d'ART sur ARM.

---

## 5. Reproduire

```bash
# 1. un fil dense, et le pousser dans l'app (appareil rooté ou émulateur)
python3 tools/perf/gen-dense-chat.py /tmp/conversations.json 30
adb push /tmp/conversations.json /data/local/tmp/c.json
adb shell "su 0 cp /data/local/tmp/c.json /data/data/com.onyx.app/files/conversations.json"

# 2. un gateway qui écrit lentement, pour mesurer « pendant une réponse »
python3 tools/perf/mock-gateway.py &   # http://<hôte>:19200, modèle « mock-1 »

# 3. le geste, et les chiffres
sh tools/perf/measure-scroll.sh avant 14
sh tools/perf/measure-scroll.sh apres 14
```

`measure-scroll.sh` imprime `Janky frames`, les percentiles, les compteurs « slow UI thread » et le
temps de thread UI calculé depuis `framestats` (`HandleInputStart → SyncStart`).

**Protocole honnête.** Une passe par état ne suffit pas à trancher un écart de 5 % : refaire trois
passes, prendre la médiane, et toujours comparer **le même fil** et **le même geste**. Sur le
téléphone, `dumpsys gfxinfo` sur l'APK **release** est la seule mesure qui vaille ; un APK `debug`
n'est ni minifié ni optimisé et peut à lui seul doubler le coût.
