# Audit de performance — le fil de conversation

> **Arbre audité** : `/home/john/Bureau/onyx`, `main` @ `6379c8f` (+ les corrections de ce lot).
> **Plainte** : « quand il y a beaucoup d'informations dans un chat, ça lag au défilement ».
> **Appareil visé** : Snapdragon 8 Elite (3ᵉ génération).

## 1. Ce qui a été mesuré, et ce qui ne l'a pas été

Il n'y a **aucun appareil branché** sur cette machine (`adb devices` vide) et le paquet `emulator`
n'est pas installé dans `/home/john/android-toolchain/sdk` (seuls des profils `.avd` existent, sans
binaire). Un émulateur en `swiftshader` mesurerait du GL logiciel : ses temps de trame ne disent rien
d'un Snapdragon, et un chiffre tiré de là serait pire que pas de chiffre.

Donc : cet audit est **statique**, appuyé sur le code lu (chaque constat cite `fichier:ligne`), et
les corrections sont vérifiées par la compilation et les suites de tests — **pas** par une mesure de
trame. La section 6 donne le protocole exact pour trancher sur ton téléphone, et ce que chaque
chiffre doit valoir après correction.

## 2. Le modèle de coût : trois horloges, pas une

Une saccade n'a pas une cause, elle a une horloge. Les trois qui comptent ici :

| Horloge | Cadence | Ce qui la paie |
|---|---|---|
| **Par trame** | 60–120 Hz | une animation infinie par rangée, un flou, un calque réenregistré |
| **Par jeton** | ~33 Hz (`TEXT_PUBLISH_MS = 30`, `TurnSession.kt:563`) | tout `remember(...)` dont la clé change quand le fil change, tout calcul refait sur **tout** le fil |
| **Par entrée de rangée** | à chaque défilement | composition, mesure, mise en page de texte d'une rangée qui (re)entre dans la fenêtre |

Une correction appartient à celle des trois qu'elle supprime. Les trois sont représentées ci-dessous.

## 3. Les défauts trouvés

Gravité : **P0** = explique à lui seul la saccade ; **P1** = coût par trame ou par jeton notable ;
**P2** = gaspillage régulier, faible risque ; **P3** = à surveiller.

| # | Défaut | Où | Horloge | Gravité | État |
|---|---|---|---|---|---|
| 1 | `state.live` passé à **toutes** les rangées : aucun saut de recomposition possible | `ChatScreen.kt:1128` (avant : `live = state.live`) | par jeton | **P0** | **corrigé** |
| 2 | `LocalGlassScrollFreeze provides listState.isScrollInProgress` lu dans la portée de l'écran — bascule 2×/geste **et 2×/`scrollToItem`** | `ChatScreen.kt:1042`, `ChatSidebar.kt:2201` | par jeton **et** par geste | **P0** | **corrigé** |
| 3 | `MessageDerived.of` re-scannait **toutes** les sorties d'outils à chaque jeton (regex ~12 k car./outil) | `MessageRow.kt:226-236` | par jeton | **P0** | **corrigé** |
| 4 | `DrawnCheckmark` : un tracé animé + un `Path` par image pour **chaque** étape finie qui (re)entre à l'écran | `MessageRow.kt:1170` | par trame, pendant le défilement | **P0** | **corrigé** |
| 5 | `pendingApprovalCount(state.messages)` : `sumOf` + deux itérateurs sur **tout** le fil, à chaque recomposition | `TurnFlow.kt:53` | par jeton | P1 | **corrigé** |
| 6 | `linkPreviewUrl` : regex sur tout le texte de la rangée à chaque composition (aperçus allumés) | `LinkPreviewCard.kt:100` | par jeton | P1 | **corrigé** |
| 7 | `BotMentions.menu(...)` recalculé à chaque recomposition de l'écran | `ChatScreen.kt:386` | par jeton | P1 | **corrigé** |
| 8 | `RanToolsChipLabel` : deux `mapNotNull` + parcours d'horodatages par composition | `ThreadOrganisation.kt:309` | par jeton | P2 | **corrigé** |
| 9 | Pas de `contentType` sur les `items` : la liste peut réutiliser une sous-composition d'une autre forme | `ChatScreen.kt:1084`, `1171` | par entrée | P2 | **corrigé** |
| 10 | `OnyxApp()` lit l'état du chat **à sa racine** : ~4 700 lignes ré-exécutées 33×/s | `OnyxApp.kt:573` | par jeton | P1 | **documenté** (§5.1) |
| 11 | Toute la page est une **source Haze** : le fond (`drawMatiere` : dégradé + 3 taches + grain shader + vignette) est réenregistré et rastérisé à chaque image de défilement | `ChatScreen.kt:918-928`, `Material.kt:199` | par trame | P1 | **documenté** (§5.2) |
| 12 | Une prose balisée = une `TextView` Markwon créée **à chaque entrée** de rangée (parse + mise en page) | `MarkdownText.kt:301-360` | par entrée | P1 | **documenté** (§5.3) |
| 13 | Aucun *baseline profile* : rien n'est compilé AOT pour le défilement Compose | `app/build.gradle.kts` | toutes | P1 | **documenté** (§5.4) |
| 14 | `debug` n'est ni minifié ni optimisé | `app/build.gradle.kts:113` | toutes | **P0 si testé en debug** | **à vérifier** (§5.5) |

## 4. Le détail des corrections

### 4.1 `state.live` n'est lu que par la ligne vivante — `ChatScreen.kt:1128`

`state.live` est un `TurnPhase` qui porte `lastEventAt` : **il change à chaque jeton**. Passé à
toutes les rangées, il rendait les paramètres de chacune différents à chaque publication ; sous
« strong skipping » (Kotlin 2.4.20), une rangée dont un paramètre diffère **ne peut pas être sautée**.
Une réponse qui s'écrit recomposait donc toutes les lignes visibles : prose, replis, cartes, puces
d'outils — 33 fois par seconde.

```kotlin
val isLive = state.isStreaming && index == state.messages.lastIndex
MessageRow(
    isLive = isLive,
    live = if (isLive) state.live else null,   // ← seule la ligne vivante lit l'état du tour
```

Une rangée finie ne lit pas `live` : `WaitingPulse` n'existe que tant que le texte est vide, et
seulement sur la ligne vivante (`MessageRow.kt:317`). L'invariant est donc intact, l'égalité tient,
et Compose saute les autres rangées.

### 4.2 Le gel du verre sort de la portée de l'écran — `GlassTouch.kt:161`, posé en `ChatScreen.kt:1042`

`LocalGlassScrollFreeze provides listState.isScrollInProgress` était évalué **dans la portée de
`ChatScreen`**. Ce booléen ne bascule pas seulement au début et à la fin d'un geste : chaque
`listState.scrollToItem(...)` — c'est-à-dire **chaque jeton** pendant qu'une réponse s'écrit
(`ChatScreen.kt:603`) — le fait passer à `true` puis à `false`. Résultat : **deux recompositions
complètes du fil par jeton**, pour un booléen qui ne sert qu'à éteindre l'appui des vitres.

```kotlin
@Composable
fun GlassScrollFreeze(listState: LazyListState, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalGlassScrollFreeze provides listState.isScrollInProgress,  // lue ICI, pas chez l'appelant
        content = content,
    )
}
```

Appliqué au fil (`ChatScreen.kt:1042`) **et** au volet (`ChatSidebar.kt:2201`), qui avait la même
forme. Le verrou `SidebarConversationGesturesTest` a été mis à jour : il exigeait la chaîne exacte
`CompositionLocalProvider(LocalGlassScrollFreeze provides state.isScrollInProgress)` ; il exige
désormais `GlassScrollFreeze(state)`, plus l'invariant réel — que le helper lise
`listState.isScrollInProgress` de la liste qu'on lui passe. L'invariant n'est pas affaibli, il est
déplacé au bon endroit.

### 4.3 La réponse en cours n'est plus analysée à chaque jeton — `MessageDerived.kt:50`

`MessageDerived.of` cherchait un diff (`looksLikePatch` sur chaque sortie d'outil **et** sur le
texte), découpait le diff, décodait le JSON des tâches et passait une expression régulière sur
~12 000 caractères par outil pour les fichiers livrés. Les clés du `remember` étaient
`(id, text, tools)` : le texte changeant à chaque jeton, **tout** était relu trente fois par seconde
pendant qu'une réponse s'écrit — dont les sorties d'outils, qui n'avaient pas bougé.

```kotlin
val derived = remember(message.id, message.tools, if (isLive) null else message.text) {
    MessageDerived.of(message, includeText = !isLive)
}
```

Les clés redeviennent `(id, tools)` : stables tant qu'un outil ne change pas. La carte de diff d'une
**réponse** ne peut de toute façon apparaître qu'une fois la réponse finie ; les cartes des outils
(diff, tâches, fichiers livrés) restent, elles, calculées pendant le travail.

### 4.4 La coche d'un outil fini ne se retrace plus à chaque visite — `MessageRow.kt:1170`

`DrawnCheckmark` montait une animation pour **toutes** les étapes finies, à leur première
composition : remonter un fil de quarante outils faisait rejouer quarante tracés à la fois, chacun
construisant son `Path` à chaque image. C'est exactement là que la liste compose les rangées qu'on
fait défiler.

```kotlin
private fun DrawnCheckmark(color: Color, animate: Boolean = false)   // animate = isLive
```

Une coche obtenue sous les yeux se trace ; une coche qu'on remonte lire est déjà tracée, pleine, sans
boucle ni allocation.

### 4.5 Les petits gaspillages réguliers

- **`pendingApprovalCount`** (`TurnFlow.kt:53`) : `sumOf { approvals.count { … } }` ouvrait deux
  itérateurs et une lambda par message sur **tout** le fil. Boucle indexée, zéro allocation, même
  résultat, mêmes messages. (Non mémorisé volontairement : `isPending` peut basculer sans que la
  taille d'aucune liste ne change, une clé bon marché rendrait l'en-tête faux.)
- **`linkPreviewUrl`** (`LinkPreviewCard.kt:100`) : mémorisé sur `(text, isLive, linkPreviews)` —
  ses seules entrées.
- **`BotMentions.menu`** (`ChatScreen.kt:386`) : mémorisé sur les cinq arguments qui le composent.
- **`RanToolsChipLabel`** (`ThreadOrganisation.kt:309`) : badges et résumé mémorisés sur `tools` /
  `working`.
- **`contentType`** sur les deux `items` du fil (`ChatScreen.kt:1084`, `1171`) : `user` / `reply` /
  `gone` / `queued`, pour que la liste ne réutilise pas une sous-composition d'une autre forme.

## 5. Ce qui a été regardé et **volontairement** laissé

### 5.1 `OnyxApp()` lit l'état du chat à sa racine — `OnyxApp.kt:573`

`val state by chatViewModel.state.collectAsStateWithLifecycle()` est lu dans `OnyxApp()`, qui fait
**4 691 lignes**. Chaque jeton ré-exécute donc le corps de cette fonction : chaînes de `when`,
allocation de toutes les lambdas passées aux écrans, constructions de valeurs. Les composables
enfants sont sautés (c'est le rôle de Compose), mais le corps, lui, tourne 33 fois par seconde.

**Pourquoi pas corrigé ici** : la sortie propre est d'extraire un composable `ChatRoute` qui lit le
*flow* et ne rend que le fil — mais `state` est consommé à une trentaine d'endroits dans le fichier
(volet, nav, en-têtes, feuilles). C'est un lot à part, testable, pas un ajustement de fin d'audit.
C'est le prochain gain structurel, et il est plus gros que tout ce qui précède une fois les P0
tombés.

### 5.2 Le fond de page est réenregistré à chaque image — `Material.kt:199`, `ChatScreen.kt:918`

La page entière est la source Haze (`onyxHazeSource`) : à chaque image de défilement, Haze
réenregistre et rastérise la région pour l'échantillonner, ce qui ré-exécute `drawMatiere` —
dégradé linéaire + trois taches radiales + un `ImageShader` de grain + vignette, plein écran.

Deux pistes, refusées **en l'état** faute de mesure :

- pré-rastériser la matière dans un `ImageBitmap` (une seule image à blitter). À 1:1, c'est ~10 Mo
  pour un seul thème, et le grain est justement dessiné « à 3 pixels pour un point » : le réduire
  change le rendu voulu ;
- sortir la matière de la source Haze. C'est un défaut **mesuré et corrigé** dans un lot précédent
  (la plaque montrait un aplat qui ne portait pas la page) : on ne défait pas une décision mesurée.

À trancher sur l'appareil, avec la section 6 : si le temps de trame pendant un défilement **à fil
vide** est mauvais alors que la composition est saine, c'est là qu'il faut aller.

### 5.3 Une prose balisée crée une `TextView` à chaque entrée de rangée — `MarkdownText.kt:301`

Le texte **sans** balisage est déjà rendu par Compose (`hasMarkup`, `MarkdownText.kt:148`) : c'est le
gros du contenu. Une prose avec du balisage passe par Markwon dans un `AndroidView`, et la vue est
recréée à chaque fois que la rangée rentre dans la fenêtre (parse + mise en page). Un cache LRU de
`Spanned` (clé = markdown + thème) supprimerait le parse au retour, pas la mise en page.

Non fait : c'est une optimisation à risque visuel (spans partagés, point de flux) qui demande une
mesure avant/après pour valoir sa complexité. Candidat n° 2 après le §5.1.

### 5.4 Aucun baseline profile — `app/build.gradle.kts`

Il n'y a pas de module `baselineprofile` et aucune dépendance `androidx.profileinstaller` : rien de
l'app n'est compilé AOT pour le défilement, tout passe par le JIT. C'est le gain au meilleur
rapport effort/risque d'une app Compose — mais un profil **utile** se génère avec un macrobenchmark
**sur l'appareil**. Sans téléphone ici, on ne peut pas le produire honnêtement.

### 5.5 Vérifie que tu testes la bonne version

`debug` n'a ni R8 ni minification (`app/build.gradle.kts:113-131`) ; `release` a les deux. Une
saccade constatée sur un APK `debug` peut être, à elle seule, la moitié du problème. Le chemin
release est déjà en place (clé de debug en repli, commentaire du fichier).

### 5.6 Les branches de perf non fusionnées

`git branch -a --no-merged main` liste `origin/lot/perf-defilement` et `origin/lot/perf-row`. Elles
ne sont **pas** récupérables : leur diff avec `main` supprime des centaines de fichiers
(`tools/onyx-setup.py`, `scripts/release-notes.py`…), c'est-à-dire qu'elles datent d'une ère
antérieure à la restructuration du dépôt. Rien à en tirer ici ; les leçons étaient déjà dans le
code (`BlocConversationFace`, `MarkwonCache`, `MessageDerived`).

## 6. Le protocole de mesure, sur ton téléphone

Trois mesures, dans cet ordre. `com.onyx.app` est l'identifiant installé.

**a) Trames saccadées (le chiffre qui compte)**

```bash
adb shell dumpsys gfxinfo com.onyx.app reset
# puis : défiler 10 s dans un fil chargé, à la main, à vitesse constante
adb shell dumpsys gfxinfo com.onyx.app | sed -n '1,40p'
```

On lit `Janky frames: N (x%)` et la ligne `90th/95th/99th percentile`. Sur un 8 Elite, un
défilement sain dans un fil de 300 messages doit rester **sous ~2 %** de trames saccadées, avec une
99ᵉ sous 30 ms. Comparer **avant/après**, même geste, même fil.

**b) Le même geste pendant qu'une réponse s'écrit** — c'est là que les défauts 1, 2 et 3 se
voyaient : envoyer une question longue (« liste les 40 fichiers de ce dépôt et explique chacun »),
puis défiler pendant la réponse. Le `%` de trames saccadées doit chuter franchement ; si ce n'est
pas le cas, le problème restant est le §5.1 ou le §5.3.

**c) Trace fine, si un chiffre ne suffit pas**

```bash
adb shell perfetto -o /data/misc/perfetto-traces/onyx.perfetto-trace -t 10s \
  sched freq idle am wm gfx view binder_driver
adb pull /data/misc/perfetto-traces/onyx.perfetto-trace
# ouvrir sur ui.perfetto.dev
```

Dans la trace, la piste `RenderThread` + `main` dite « Compose » : ce qu'on cherche après ces
corrections, c'est **une** `recomposition` par jeton (la ligne vivante) et non une par rangée, et
plus de `Path` alloué pendant le défilement.

**d) Compte de recompositions (sans trace)**

Android Studio → *Layout Inspector* → cocher « Show recomposition counts » → défiler : les rangées
finies doivent rester à un compte **stable** pendant qu'une réponse s'écrit.

## 7. Ce que les suites prouvent, et ce qu'elles ne prouvent pas

- **Prouvé** : l'arbre compile (`:app:compileDebugKotlin`), et les deux suites passent
  (`:core:testDebugUnitTest`, `:app:testDebugUnitTest`) — compteurs lus dans les XML, ci-dessous.
- **Prouvé par verrou de source** : le fil et le volet passent bien par `GlassScrollFreeze`, et le
  helper lit l'état de défilement de la liste qu'on lui donne.
- **Non prouvable sur la JVM** : qu'une rangée soit effectivement sautée, et qu'un tracé de coche ne
  se monte plus pour une étape finie. Ce sont des faits de composition Compose ; la preuve est le
  code plus le compte de recompositions de la section 6d.

## 8. Résultat des suites (cette session)

Exécutées sur une **copie isolée de l'arbre** (`onyx-audit`, `main` + les modifications de ce lot,
y compris les fichiers non suivis), parce qu'un autre chantier compilait dans le même dossier : deux
Gradle sur un même *checkout* se détruisent leurs résultats (`NoSuchFileException:
in-progress-results-generic.bin`, `EOFException`), et un premier essai a échoué exactement comme ça.

```
$ ./gradlew :core:testDebugUnitTest :app:testDebugUnitTest --rerun --no-build-cache
BUILD SUCCESSFUL in 4m 27s
```

Compteurs lus **dans les XML** (`build/test-results/testDebugUnitTest/*.xml`), pas dans la sortie de
Gradle, et horodatages vérifiés postérieurs au lancement :

| Module | Fichiers XML | Tests | Échecs | Erreurs | Ignorés |
|---|---|---|---|---|---|
| `:core` | 282 | **1 948** | 0 | 0 | 1 |
| `:app` | 293 | **2 169** | 0 | 0 | 0 |

Les deux nouveaux tests de `MessageDerivedTest` sont dans le total `:app` :

- `une reponse en cours n est pas analysee pour un diff` — `includeText = false` ne rend aucun diff,
  `true` le rend (la carte arrive à la fin) ;
- `les sorties d outils restent analysees pendant l ecriture` — le diff d'une **sortie d'outil** est
  toujours trouvé pendant le tour.

Et le verrou du volet, réécrit (`SidebarConversationGesturesTest`) : `GlassScrollFreeze(state)` +
« le helper lit `listState.isScrollInProgress` de la liste qu'il reçoit ».

## 9. Ordre des prochains travaux

1. **§5.1** — extraire la lecture de l'état du chat d'`OnyxApp()` (`ChatRoute`) : le plus gros gain
   restant, et il profite à tous les écrans, pas seulement au fil.
2. **§6** — mesurer `gfxinfo` **avant/après** sur ton téléphone, en release, les deux gestes
   (défilement à vide, défilement pendant une réponse). Sans ça, les §5.2 à §5.4 restent des
   hypothèses, pas des chantiers.
3. **§5.4** — baseline profile, dès qu'un appareil est disponible pour le générer.
4. **§5.3** — cache des `Spanned` de Markwon, si la mesure désigne les rangées balisées.

