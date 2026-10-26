# Lots — C1 des retours, vérification des routes non vues, et les deux premiers manques

**App**: Hermes Onyx (`com.onyx.app`). **Branche**: `onyx-media-capture`, worktree neuf détaché
de `d364381` (rien n'a été écrit dans `onyx-integration2`).

| | |
|---|---|
| Commit livré | **`27bf395`** — « Ouvre les videos dans l app et ajoute la capture rapide » |
| APK | **`1.0.0+319`** (`versionCode 319`) |
| SHA‑256 | **`61ad5c80dea2690b86d50c6552301bcfcb3d6bd4958fa0fc4ed4d230b3547280`** |
| SHA sur l'appareil | **identique** (`pm path` + `exec-out cat \| sha256sum`), revérifié après la passe |
| Tests | **1124 dans les XML, 0 échec, 0 erreur** (plancher 1090) |
| Recette | `:core:testDebugUnitTest :app:testDebugUnitTest :app:assembleRelease` — **BUILD SUCCESSFUL** |
| Appareil | `emulator-5600` (AVD `onyx-mq`, copie de `onyx-nav`, API 36, x86_64, 1080×2400 à 420 dpi = **411 dp**) — `emulator-5558` **jamais touché** |
| Serveur | double du Dashboard déjà en marche sur `http://10.0.2.2:8787` (**pas lancé par ce lot**, laissé en marche) |

**Un test instable, connu et nommé:** `KanbanLiveBoardTest` (délai `MockWebServer` sous charge) est
tombé sur une passe complète, puis **repasse vert relancé seul**
(`--tests 'com.onyx.app.data.net.KanbanLiveBoardTest' --rerun-tasks` → BUILD SUCCESSFUL) ; la passe
complète qui suit est verte. Ce n'est pas une régression du lot.

---

## A1 — C1: `navTo` pendant la composition sur les états vides — **REPRODUIT**

L'audit ne l'avait pas reproduit. Il l'est, à l'appareil, et il est **corrigé** (la preuve justifie
la correction ; l'audit laissait « à faire sans consigne contraire »).

### Le chemin, dans l'ordre où Android le produit

1. Les charges utiles de ces routes sont de simples `remember` — elles **ne survivent pas** à une
   recréation d'activité —, alors que `route` est `rememberSaveable` :
   `OnyxApp.kt:448` (`route`) contre `:484` (`patchText`), `:485` (`selectedText`), `:488`
   (`subagentAgent`), `:509` (`photoSet`), `:512` (`pdfSource`), `:465` (`contextEditor`).
2. L'utilisateur revient donc **sur `SELECT_TEXT` sans texte**, sur `PHOTO` sans photo, etc.
3. Les branches appelaient `navTo(CHAT)` **dans le corps composable** (`SUBAGENT`, `PHOTO`, `PDF`,
   `SELECT_TEXT`, `PATCH`, `CONTEXT_EDITOR`) — une écriture d'état pendant la composition.
4. Ce `navTo` **empilait** la route vide. Le retour retombait dessus, elle rebondissait aussitôt :
   l'écran ne changeait pas et **l'app ne se fermait jamais**.

### Mesure avant / après (mêmes gestes, même appareil)

`qa/20261026-media-capture/c1.py`, relevés dans `results-c1.jsonl`, captures `shots/AVANT-c1-*` et
`shots/APRES-c1-*` :

| Pas | **Avant** (APK `1.0.0+318`, `9b80725b…`) | **Après** (APK `1.0.0+319`, `61ad5c80…`) |
|---|---|---|
| Feuille « Sélectionner du texte » ouverte | oui | oui |
| `am kill` du processus | PID 5143 → **mort** | PID 5762 → **mort** |
| Retour par restauration de la tâche | nouveau PID, `feuille_encore_la: false` → **le fil** | nouveau PID, la feuille disparaît → **le fil** |
| **Retour n°1** (bouton système) | **l'app reste au premier plan, écran inchangé** | **l'app se ferme** (lanceur) |
| **Retour n°2** | **écran inchangé**, app toujours là | — |
| **Retour n°3** | **écran inchangé**, app toujours là | — |
| Témoin : le fil atteint sans restauration, retour | l'app se ferme | l'app se ferme |

**C1 est donc réel, reproductible, et il produisait un geste de retour mort** (trois appuis, aucun
effet, app jamais fermée) — pas seulement « une recomposition perdue ». Captures : avant
`AVANT-c1-3-retour-1.png` (l'app au premier plan, fil inchangé) / après `APRES-c1-3-retour-1.png`
(le lanceur).

### La correction (`27bf395`)

- `ResponsiveLayout.kt` : **`navigateLeavingEmpty`** — un écran dont l'état est vide **n'entre pas
  dans la pile**, et sa trace (ainsi que celle de la destination) est effacée.
- `OnyxApp.kt` : les neuf replis (`SUBAGENT`, `PHOTO`, `PDF`, `VIDEO`, `SELECT_TEXT`, `PATCH`,
  `CONTEXT_EDITOR`, `SESSION_USAGE`, `PROJECT_SESSIONS`) passent par
  `LaunchedEffect(Unit) { navFromEmpty(…) }` — plus aucune écriture d'état pendant la composition.
- Tests : `EmptyStateReturnTest` (8 tests) tient les deux moitiés — le repli hors composition, et
  **l'ancien repli reproduit** (`l ancien repli avalait le retour`, l'appui avalé en 4 étapes) contre
  **la règle nouvelle** (`la nouvelle regle laisse le retour aboutir`). Un test d'avant a été
  réaligné, pas supprimé : `ResponsiveLayoutTest` interdit les affectations directes de `route`, il
  accepte maintenant `navigateLeavingEmpty` comme les autres fonctions de navigation.

**Le choix de conception reste en attente du propriétaire** (§ « Ce qui reste » de l'audit): quand
la pile d'un onglet de section est vide, le retour retombe sur la **liste des conversations**. Rien
n'a été changé là-dessus.

---

## A2 — Vérification à l'écran des routes non vues par l'audit

Méthode de l'audit reprise telle quelle : on ouvre par le **point d'entrée réel**, on relève la
signature et les **bornes d'un repère**, on fait retour, on relève de nouveau (destination,
position, écran quitté). Aucune coordonnée écrite à la main.

| Route | Entrée réelle | Résultat | Preuve |
|---|---|---|---|
| **CAMERA** | composeur « + » › Appareil photo | **✅ ouvert** — écran caméra (« Fermer », « Allumer la lampe »), preview dessinée ; retour système → **le fil quitté** | `R2-CAMERA-avant.png`, `R2-CAMERA-apres.png` |
| **SELECT_TEXT** | réponse d'agent › action « … » | **✅ ouvert** — « Sélectionner du texte » + « Tout copier » ; retour → **le fil** (destination juste ; position non comparable : le repère « Tout copier » n'existe que sur la feuille) | `ROUTE-SELECT_TEXT-ouvert.png`, `AVANT-c1-3-retour-1.png` |
| **QUICK_CAPTURE** (nouvelle) | intent/raccourci `com.onyx.app.action.CAPTURE` | **✅ ouvert, retour bouton ✅, retour geste du bord ✅** (les deux rendent le fil quitté) | `NOTE-1-ouverte.png`, `NOTE-4-retour.png`, `NOTE-5-retour-bord.png` |
| **VIDEO** (nouvelle) | vignette d'une vidéo jointe (bande **et** fil) | **✅ ouvert** des deux points d'entrée ; retour → l'écran quitté | `VIDEO-3-lecteur.png`, `VIDEO-7-lecteur-depuis-le-fil.png` |
| **SUBAGENT** | ligne d'un sous-agent dans le fil | **❌ non atteint** : l'appui sur la ligne ne pousse pas l'écran (la ligne reste dans le fil) ; la seule ouverture vue reste `onOpenSubagent` (`OnyxApp.kt:1227`), jamais déclenchée par un geste | — |
| **PATCH** | diff dans un tour réel | **❌ non atteint** : le double du Dashboard ne sert pas de réponse contenant un diff (`patch != null` est indispensable, `MessageRow.kt:182`) | — |
| **PDF** / **PHOTO** | pièce jointe du composeur, ou fichier d'un tour | **❌ non atteint** : le **sélecteur du système** ne présentait ni `photo-test.png` ni `doc-test.pdf` (il garde le filtre « Videos » d'un essai précédent) — échec de la **boîte à outils**, comme les `ÉCHEC-ENTRÉE` de l'audit ; rien ne dit que l'app est en cause | `R2-PHOTO-bande` absent |
| **BOT_EDITOR · BOT_FACE · BOT_SCHEDULES · BOT_VOICE · HIDDEN_BOTS** | appui long sur une tuile de bot | **❌ non atteint cette passe** : la passe a atterri sur le fil, pas sur la section Bots ; l'ancrage du menu avait été vérifié par l'audit, pas ici | — |

**Deux volets**: la passe de l'audit les avait mesurés sur l'APK corrigé (`Z1`–`Z6`, tous ✅, dont
`Z5` le geste du bord en deux volets) ; **ce lot n'a pas rejoué la géométrie deux volets** (aucune
route nouvelle n'y touche : `QUICK_CAPTURE` et `VIDEO` sont des feuilles, comme `PDF`/`PHOTO`, et
suivent `presentedRoutes`). À rejouer si le propriétaire veut le chiffre sur cette géométrie.

---

## B1 — Pièce jointe vidéo dans le fil (lecteur + vignette) — **fait**

Comme le modèle : la vidéo ne part plus à l'app du système, elle se lit **dans Onyx**, et sa
**première image** sert de vignette sous un rond de lecture (son `VideoTile`).

| Fichier | Ce qu'il fait |
|---|---|
| `app/.../ui/chat/FileViewers.kt` | `FileViewerKind.VIDEO` ; `isVideo(name, mime)` (type d'abord, nom ensuite, comme `Attachment.isVideo`) ; extensions et types MIME vidéo |
| `app/.../ui/chat/VideoPoster.kt` | **`VideoPoster`** (première image par `MediaMetadataRetriever`, à 3× la taille affichée ; durée ; poids) et **`VideoMeta`** (durée `0:07`/`1:02:03`, poids `820 o`/`12,4 Mo` selon la langue, libellé « Vidéo : nom, poids ») |
| `app/.../ui/chat/VideoPlayerScreen.kt` | lecteur plein écran noir : chevron de fermeture, nom du fichier, **barre de transport de la maison** (pas de `MediaController` système), rond lecture/pause, piste qu'on fait glisser, ±15 s, temps écoulé / durée, refus écrit si la vidéo ne se lit pas |
| `app/.../ui/chat/components/MessageRow.kt` | **`VideoThumb`** dans la bulle : première image + rond de lecture, l'appui ouvre le lecteur ; la vidéo ne tombe plus dans la pastille de fichier |
| `app/.../ui/chat/components/OnyxComposer.kt` | **`VideoStripThumbnail`** dans la bande du composeur + `Symbols.Film` |
| `core/.../ui/design/Symbols.kt`, `core/src/main/res/drawable/ic_sym_film.xml` | le symbole « film » |
| `app/.../ui/OnyxApp.kt` | route **`VIDEO`**, état `videoSource`, branche du routeur (repli **hors composition**), `presentedRoutes` |
| `app/src/main/AndroidManifest.xml` | partage et ouverture de `video/*` (SEND + VIEW) |

### Captures (APK `1.0.0+319`) et relevés — `results-video.jsonl`

| Ce qui est mesuré | Résultat |
|---|---|
| Le mp4 choisi dans le sélecteur | `clip-test.mp4` présent |
| La vignette dans la bande | **oui**, bornes `[47, 1988, 215, 2156]` (168×168 px) — `VIDEO-2-bande.png` |
| Le lecteur ouvert par la vignette | `Fermer \| clip-test.mp4 \| Play \| 0:00 \| 0:04 \| Reculer de 15 secondes \| Avancer de 15 secondes` — `VIDEO-3-lecteur.png` |
| **La lecture** (appui sur le rond) | passe à `Pause \| 0:02`, puis `Play \| 0:04` → **la position avance jusqu'à la fin** : la vidéo est réellement décodée — `VIDEO-4-lecture.png` |
| Le retour | le fil quitté, à sa place — `VIDEO-5-retour.png` |
| La vignette dans le **fil** après envoi | **oui**, bornes `[833, 1786, 1001, 1954]`, annonce lue : `clip-test.mp4` + **« Vidéo : clip-test.mp4 »** (le gabarit français) — `VIDEO-6-fil.png` |
| Le lecteur **rouvert depuis le fil** | oui, même écran — `VIDEO-7-lecteur-depuis-le-fil.png` |
| Le refus, si la vidéo ne se lit pas | branche `setOnErrorListener` → « Cette vidéo ne peut pas être lue. » (non rejouée : le mp4 de recette se lit) |

Tests : `VideoAttachmentTest` (9 tests) et `FileViewersTest` **réaligné** — la ligne « le reste part
à l'app du système » prenait `clip.mov` en exemple : elle prend maintenant `data.bin`, et la vidéo a
son test à part (le test n'a pas été supprimé).

---

## B2 — Capture rapide (« quick capture ») — **fait, avec deux restes écrits**

Comme le modèle : une note prise **en passant**, **gardée au moment où elle est prise**, classée
par l'agent, **rien ne se dit en retour**.

| Fichier | Ce qu'il fait |
|---|---|
| `core/.../data/capture/QuickCapture.kt` | `CaptureNote`, `CaptureCopy` (le message de classement, la conversation `Notes`), **`CaptureInbox`** : notes prises/attente/remise, lot remis en entier (une note qui a atteint le serveur ne repart jamais), une remise à la fois — testable sans appareil |
| `app/.../data/capture/CaptureSession.kt` | **`QuickCapture`** (boîte sur le disque — `onyx_capture`, notes écrites dès la prise, relues au démarrage, nouvelle tentative espacée 15 s/60 s/180 s/600 s, état d'attente exposé) et **`CaptureFiler`** (les notes partent **en un seul message**, dans un tour que personne ne regarde : `HermesServeChat` ou `ChatCompletionsClient` selon le transport) |
| `app/.../ui/capture/QuickCaptureScreen.kt` | l'écran : `OnyxPageHeader`, section « LA NOTE », champ, bouton « Prendre la note », l'**issue** de l'appui (jamais muet), section « EN ATTENTE » avec l'heure et la suppression |
| `app/.../intake/ShareInbox.kt` + `MainActivity.kt` + manifeste | `shareInbox.requestCapture()` / `captureRequested`, action **`com.onyx.app.action.CAPTURE`** |
| `app/src/main/res/xml/shortcuts.xml` + `strings.xml` | le raccourci « Prendre une note » de l'illustration — **deux gestes depuis n'importe où** |
| `app/.../ui/OnyxApp.kt` | route **`QUICK_CAPTURE`**, consommation de la demande (sans toucher à la conversation ouverte), entrée « Prendre une note » dans le menu « … » du fil, et **remise retentée quand l'app reparaît** |
| `core/.../localization/OnyxLocalization.kt`, `OnyxFrenchRemaining.kt` | gabarit « Vidéo : … » et les libellés français des deux écrans (clés cherchées dans **tous** les dictionnaires avant ajout : aucun doublon) |

### État exact, mesuré (APK `1.0.0+319`) — `results-captures.jsonl`

- **Ouverture par le raccourci** (`am start -a com.onyx.app.action.CAPTURE`) → l'écran, en français :
  « Retour | Prendre une note | LA NOTE | Prendre la note | … | EN ATTENTE | Rien en attente. »
  (`NOTE-1-ouverte.png`).
- **Deux gestes** : le champ, le texte (« Le plombier passe jeudi a 10h »), le bouton
  (`NOTE-2-saisie.png`).
- **Prise de la note** : la note **reste** dans « EN ATTENTE » avec « lundi 26 octobre à 10:51 », et
  l'appui **dit son issue** : « En attente du serveur. Elle repartira toute seule. »
  (`NOTE-3-prise.png`). *Elle n'a pas pu être classée ici : la clé d'API du serveur vit dans le
  magasin chiffré de l'app, le double du Dashboard posé par `inject.py` ne la porte pas — l'app
  répond exactement ce qu'elle doit (« Aucune clé API enregistrée »). La note, elle, **survit aux
  redémarrages et aux réinstallations** (vue après trois installations successives).*
- **Retour** : par le contrôle de la page **et** par le **geste du bord** — les deux rendent le fil
  quitté (`NOTE-4-retour.png`, `NOTE-5-retour-bord.png`).

Tests : `QuickCaptureTest` (9 tests, dans `core`) et `QuickCaptureWiringTest` (8 tests) — route
déclarée/dessinée/retour, les deux gestes, l'intent, la persistance, la remise d'un seul tenant,
« rien ne se dit en retour » (aucun `voice.speak`, aucun envoi dans la conversation ouverte), et les
libellés français.

### Ce qui reste sur cette fonction (écrit, pas caché)

1. **La dictée** : le modèle prend la note **à la voix** (raccourci → écoute directe) ; ici la
   note est **écrite**. Le raccourci ouvre l'écran, le champ et le bouton font les deux gestes.
   (~1,5–2 h : brancher l'écran vocal en mode capture.)
2. **La section des notes en attente dans la liste des conversations** (le modèle les y montre) :
   ici elles sont sur l'écran de la capture. (~1 h.)
3. **Le nom de la conversation côté serveur** : les notes partent en un message dans un tour à part,
   mais l'app ne crée pas encore la conversation « Notes » dans sa liste locale — donc la réponse de
   l'agent n'est pas relisible dans l'app. (~2 h.)

## Ce qui reste du lot, en une ligne chacun

- **A2** : `SUBAGENT`, `PATCH`, `PDF`, `PHOTO` et les cinq écrans de bot **ne sont pas vérifiés à
  l'écran** (motifs ci-dessus ; deux sont des ratés du **sélecteur du système**, pas de l'app).
- **Deux volets** non rejoués sur cet APK (la passe de l'audit les avait tous ✅).
- **C1** : le **choix de conception** du retour d'un onglet de section à pile vide est toujours en
  attente du propriétaire ; sa correction de comportement (§A1) n'y touche pas.
- Mis à part : **`:auto` (Android Auto)** n'a pas suivi le renommage — chantier de cohérence à part.

## Fichiers et preuves

- Code : `27bf395` (11 fichiers touchés + 5 créés), dans le worktree `/home/john/onyx-media-capture`.
- Relevés : `qa/20261026-media-capture/` — `results-c1.jsonl` (avant/après), `results-video.jsonl`,
  `results-captures.jsonl`, `results-routes.jsonl` ; `shots/` (~30 captures, préfixes `AVANT-c1-`,
  `APRES-c1-`, `VIDEO-`, `NOTE-`, `R2-`, `ROUTE-`).
- Boîte à outils : `driver.py`, `harness.py`, `inject.py` repris de `qa/route-audit/` ;
  `c1.py`, `video.py`, `capture.py`, `routes2.py` écrits ici.
- **Aucun message de commit ni texte d'interface ne nomme la plateforme d'origine** : un test refuse
  les mots interdits dans les fichiers neufs et les valeurs françaises ajoutées.
