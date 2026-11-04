# Revue de performance — état réel, ce qui est corrigé, ce qui reste

**Date** : 4 novembre 2026 · **Version au moment de la revue** : 1.3.15 → 1.3.16

**Méthode.** Quatre audits parallèles en lecture seule (recomposition/défilement, démarrage/navigation,
temps réel/réseau, persistance/mémoire), puis **vérification de chaque constat dans le code** avant de
toucher quoi que ce soit. Chaque constat retenu ici a été relu à la ligne citée.

**Ce que cette revue n'est pas.** Il n'y a **aucune mesure au profileur** dans ce document : les coûts
sont déduits du code (nombre d'allocations, fréquence d'appel, taille des objets), pas mesurés avec
Perfetto ou Macrobenchmark. Les hiérarchies annoncées sont donc des raisonnements, pas des chiffres.
La section « Ce qu'il reste à mesurer » dit comment obtenir les vrais.

---

## 1. Faux positif à connaître (vérifié, corrigé ailleurs)

L'audit a affirmé que `TurnSession.shouldPersist()` comparait `message.text.length` et réécrivait donc
`conversations.json` à chaque jeton. **C'est faux dans ce dépôt** : la fonction compare
`textBucket(message.text.length)` (`TurnSession.kt:189-207`) depuis le commit `b7f1892`, avec un palier
de 256 caractères. Deux nouveaux tests le figent désormais (`TurnSessionTest`).

Le dire ici compte : un audit qui se trompe sur un point de cette taille invite à vérifier les autres
avant d'agir. Tous les constats ci-dessous ont été relus dans le code.

---

## 2. Corrigé dans cette passe

| # | Défaut | Où | Ce qui change |
|---|---|---|---|
| 1 | Texte de la réponse recopié en entier à chaque fragment (`message.text + incoming`) : O(n²), deux publications d'état par fragment | `TurnSession.kt:365` | Tampon `StringBuilder` + publication au plus une fois par trame (`TEXT_PUBLISH_MS = 30`), vidé sur tout événement structurel (`flushText`) |
| 2 | Markdown re-parsé à chaque recomposition de la rangée, même quand le texte n'a pas bougé | `MarkdownText.kt:231` | Garde `view.tag` (texte + indicateur de flux) : plus de `setMarkdown` inutile |
| 3 | Chaque `close()` d'un `ServeRpc` jetable relançait jusqu'à 3 handshakes (login, ticket, upgrade) | `ServeRpc.kt:136, 328, 270` | Drapeau `closed` : une fermeture volontaire ne déclenche plus de reprise |
| 4 | Journal de salle recopié intégralement toutes les 500 ms, sans borne | `GroupRoomFeed.kt:281` | `takeLast(MAX_EVENTS = 500)` |
| 5 | Brouillons : décodage + encodage JSON de **tous** les brouillons à chaque frappe | `DraftStore.kt:24` | Carte gardée en mémoire, seule l'écriture reste |
| 6 | Photos de bots décodées en pleine résolution (4 Mo/avatar), deux clés par bot | `BotsViewModel.kt:417` | `BoundedImages.decodeBytes(maxPx = 256)` |

Déjà livrés avant cette revue, rappelés pour ne pas les recompter : animations infinies par étape
d'outil supprimées (`MessageRow.StepMark`), gel du verre au défilement, cartes du Flux et fichiers non
recalculés par jeton, libellés de jour mémorisés, compte de tours non rescanné.

---

## 3. Ce qui reste, classé par gain (non appliqué)

### Gain fort

1. **Toutes les lignes visibles rejouent à chaque jeton** — `ChatScreen.kt:1035-1088` lit `state`
   (instable) dans le scope des `items` et fabrique ~12 lambdas par ligne ; `ChatMessage`
   (`ChatMessage.kt:237`) n'est pas `@Immutable`. Correctif : annoter `ChatMessage`/`ToolActivity`,
   hoister les valeurs lues (`liveId`, `isStreaming`) hors des items, mémoriser les lambdas par
   `message.id`. **C'est le correctif qui débloque les gains 2 et 3.**
2. **`MessageRow` re-scanne les sorties d'outils à chaque composition** — `MessageRow.kt:215-248` :
   `lastOrNull { looksLikePatch }` sur tout le texte, `patchFiles` appelé deux fois (O(n²) sur le
   diff), `todosFrom` (parse JSON), `DeliveredFiles.of` (regex sur ~12 000 caractères par outil).
   Correctif : un seul `remember` par contenu, comme déjà fait pour `feedCards`.
3. **`reasoningLooksForeign` re-tokenise tout le raisonnement** — `MessageRow.kt:729` +
   `ReasoningBlock.kt:144-147` : `lowercase()` + `Regex(...).findAll(...).toList()` à chaque
   composition. Correctif : `remember(message.reasoning, langue)` et regex de fichier.

### Gain moyen

4. **Markwon reconstruit quand une rangée re-entre dans la fenêtre** — `MarkdownText.kt:154-197` : le
   moteur (4 plugins) est dans un `remember` **de la ligne** ; chaque entrée dans la fenêtre refait un
   registre de plugins + un parse complet. Correctif : cache LRU de moteurs, clefé sur le style.
5. **`OnyxApp` lit `ChatUiState` dans son propre scope** — `OnyxApp.kt:557` : le shell entier recompose
   à chaque jeton et à chaque frappe. Correctif : passer `State<ChatUiState>` et lire `.value` dans
   `ChatScreen`, dériver les rares valeurs dont la racine a besoin.
6. **`contentPadding` de la LazyColumn piloté image par image** par `animateContentSize` du
   compositeur (`OnyxComposer.kt:401` → `ChatScreen.kt:1398` → `:1019`). Correctif : publier une
   réserve stable (valeur au repos, `maxOf` avec la hauteur vive).
7. **`Modifier.composed` sur chaque élément cliquable** (`Interactions.kt:38, 91`) : une
   sous-composition + un état d'animation par contrôle, ~12 par ligne de fil. Correctif : `clickable`
   avec `MutableInteractionSource` mémorisée pour les rangées ; `Modifier.Node` à terme.
8. **Une surface de verre par rangée de conversation dans le volet** (`Galerie.kt:404`) : 8-10
   échantillonnages de fond par image au défilement. Correctif : une plaque derrière la liste.
9. **Appels RPC annulés : entrée `pending` jamais retirée** (`ServeRpc.kt:321`) — fuite lente, et un
   `complete.slash` parti par frappe (`ChatViewModel.kt:705`). Correctif : `finally { pending.remove }`
   + debounce du menu.
10. **Import des sessions serveur à chaque émission de configuration** (`ChatViewModel.kt:234`) :
    1 requête + N écritures complètes par réglage modifié. Correctif : un import par serveur.
11. **Attente active `while (isStreaming) delay(200)`** (`ChatViewModel.kt:1111`) : 5 réveils/s sans
    limite. Correctif : `first { !it.isStreaming }` sous `withTimeout`.

### Gain faible (à faire quand le reste est fait)

12. `rowTime` construit `Calendar` + `SimpleDateFormat` par rangée (`ConversationShared.kt:513`).
13. Flux : compte par jour en O(jours × cartes) (`ChatPanels.kt:510`).
14. Deux `TextMeasurer.measure` non mémorisés dans le compositeur (`OnyxComposer.kt:629, 676`).
15. `pendingApprovalCount` / `contextUsage` / pastille du Flux rescannés à chaque composition
    (`ChatScreen.kt:609, 599, 715`).

---

## 4. Persistance et mémoire — le vrai chantier de fond

**`conversations.json` est un fichier unique réécrit en entier à chaque écriture**
(`ConversationRepository.kt:206-212` + `OnyxDataStore.kt:112`). Trois conséquences mesurables :

- `pinAll` / `archiveAll` / l'import serveur font **N écritures complètes** au lieu d'une
  (`ConversationRepository.kt:102, 106`, `ChatViewModel.kt:270`) ;
- le sérialiseur charge le fichier **trois fois** en mémoire (octets, `String` UTF-16, arbre) et en
  recopie deux à l'écriture (`OnyxDataStore.kt:104-115`) ;
- le décodage au démarrage d'un historique de 15 Mo occupe ~45 Mo en pic.

Correctif de fond : **un fichier par conversation + un index**, comme la référence. En attendant :
`saveAll(list)` pour les actions de masse, décodage/encodage en flux.

Mémoire : les images-clés de vidéos sont gardées en résolution native dans une map sans éviction
(`ConversationFilesController.kt:468`) — une frame 4K pèse 33 Mo pour une vignette de 56 dp.

---

## 5. Démarrage

Trois blocages du thread principal avant la première image, tous vérifiés :

- `runBlocking { settings.applyStoredLanguage(); applyStoredTheme() }` lit DataStore
  (`AppContainer.kt:121`) ;
- `StartupGuard.begin()` fait un `commit()` (fsync) dans la composition (`OnyxApp.kt:623` →
  `StartupGuard.kt:91`) ;
- `SetupSuggestions.localAddresses()` énumère les interfaces réseau à chaque démarrage
  (`OnyxApp.kt:565`), pour un écran que la plupart des utilisateurs n'ouvrent jamais.

Plus : les trois chargements d'`AppContainer` sont **séquentiels** alors qu'ils sont indépendants
(`AppContainer.kt:193`), et `key(appLanguage)` enveloppe l'état de navigation — changer la langue vide
la pile et renvoie sur Chat (`OnyxApp.kt:526`).

---

## 6. Ce qu'il reste à mesurer

Aucun chiffre dans ce document ne vient d'un profileur. Pour passer des raisonnements aux mesures :

1. `androidx.benchmark:benchmark-macro-junit4` + `BaselineProfile` sur trois scénarios :
   démarrage à froid, défilement d'un fil de 300 messages, réponse en streaming de 2 000 jetons.
2. `adb shell dumpsys gfxinfo com.onyx.app framestats` avant/après chaque lot, sur un **appareil
   réel** — l'émulateur disponible ici tourne en `swiftshader`, ses chiffres ne valent rien.
3. Perfetto (`trace_config: sched, freq, atrace gfx view`) pour voir si la saccade est CPU, GC ou GPU.

Tant que ces mesures n'existent pas, toute affirmation du type « c'est plus fluide de X % » serait
inventée.
