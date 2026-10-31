# RETOUR-AUDIT — toutes les routes, et le retour de partout

Audit du **retour** de l'app : chaque destination ouverte par son entrée réelle, un retour joué
(touche système **et** geste du bord), et — pour chaque geste — l'**arbre d'accessibilité** avant
et après, avec le titre de l'écran et l'élément en haut de la liste.

| | |
|---|---|
| App | **Hermes Onyx** (paquet `com.onyx.app`), worktree neuf `onyx-routes-retour` (base `0bebdd61` = `1.0.0+467`) |
| Commits du lot | `8059febd` (le retour), `a7fa372b` (le pliage) ; les relevés suivent (l'APK mesuré est celui d'`a7fa372b`, mêmes sources) |
| APK mesuré | **1.0.0+469** (`versionCode 469`) — `sha256 d52debee9c07436a64fb06f8f37b63e5998633385cbcd975cdfa00759c14eb37` |
| Appareil | `emulator-5580` (AVD `onyx36`, API 36, x86_64) — **jamais `emulator-5558`**, tué à la fin |
| Une colonne | `1080x2400 @420` = **411 dp** — le téléphone, le pliable **replié** |
| Deux panneaux | `2156x2344 @400` = **862 dp** — le pliable **déplié** (la géométrie du propriétaire) |
| Serveur | passerelle Hermes réelle `http://10.0.2.2:8650` (profil actif), posée par `qa/20261027-retour-routes/inject.py` |
| Recette | `:core:testDebugUnitTest :app:testDebugUnitTest :app:assembleRelease --no-daemon --console=plain -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.workers.max=2` |
| Tests | **2168** dans les XML, **0 échec** — plancher **2146** + **22** ajoutés (cf. §5) |

## Comment un retour est mesuré

1. l'app **redémarre** (chaque cas part du même état) et l'écran parent est atteint par son
   entrée réelle — le volet, son contrôle de barre, la ligne des Réglages, le menu « … » du fil ;
2. on **relève** l'arbre d'accessibilité du parent (titre + premiers éléments + bornes de la ligne
   repère) ;
3. on ouvre la destination et on relève son arbre (preuve qu'on a bien changé d'écran) ;
4. on **retourne** : `system` = touche retour, `edge` = **geste du bord gauche** (balayage de
   l'edge, la largeur calculée sur la taille courante de l'écran) ;
5. on relève l'arbre d'arrivée et on exige : **(a)** l'écran quitté, **(b)** la **même position**
   de la ligne repère (bornes identiques), **(c)** le même état — volet refermé, feuille refermée,
   recherche refermée.

Ce qui compte comme une **incohérence** : revenir ailleurs qu'où l'on était, laisser un niveau
sauté (deux écrans d'un coup), laisser l'élément ouvert par-dessus l'écran d'arrivée, perdre la
position de la liste, ou fermer l'app sans raison.

# 1. Le tableau complet des routes

Les **66** valeurs de l'énumération `Route`, chacune avec l'écran qu'elle affiche réellement (la
branche du routeur), ses points d'entrée relevés dans la source, et le contrat de retour de son
propre contrôle. **Aucune route n'est déclarée « vue » sans une entrée qui l'ouvre** : les 66
apparaissent au moins une fois comme cible de `navTo`/`navFromEmpty` (`RetourMatriceTest`), et
chacune a sa branche dans le `when` — **aucune route ne mène à un écran blanc**.

| | |
|---|---|
| Valeurs de l'énum `Route` | **66** |
| Routes avec une branche du routeur | **66** (aucune chute dans un `else` vide) |
| Routes injoignables | **0** |
| Routes menant à un écran vide ou d'erreur | **0** (les écrans à état vide **sortent** de la pile : `navigateLeavingEmpty`) |

| Route | Écran réellement affiché | Comment on y arrive | Retour (contrôle de la page) |
|---|---|---|---|
| `CHAT` | le fil (bavardage, onglets du bas) | racine ; menu du fil ; volet | navBack → CHAT |
| `CONVERSATIONS` | la liste des conversations (rail) | volet (control), menu du fil « Toutes les conversations » ; onglet de section | navBack → CHAT |
| `SETTINGS` | les Réglages | volet (tuile), menu du fil, réglages enfants | navBack → CHAT |
| `APPEARANCE` | l'Apparence | Réglages (ligne) | **repli fixe → Réglages** (une seule entrée) |
| `MODELS` | les Modèles | volet (tuile), en-tête du fil, Réglages | navBack → CHAT |
| `VOICE` | la voix seule (orbe) | bouton « Parler » du fil, partage vocal | CHAT |
| `GATEWAY` | la Passerelle | Réglages (ligne) | **repli fixe → Réglages** (une seule entrée) |
| `LOGS` | les Journaux (Agent/Erreurs/Passerelle) | Réglages (ligne) | navBack → Réglages |
| `FILES` | le navigateur de fichiers du serveur | volet (tuile), Réglages | navBack → Réglages |
| `FILE_EDITOR` | l'éditeur de fichier | Réglages (ligne) | **repli fixe → Fichiers** (une seule entrée) |
| `CONTEXT` | les fichiers de contexte (SOUL, etc.) | Réglages (ligne) | **repli fixe → Réglages** (une seule entrée) |
| `MEMORY` | la Mémoire | Réglages (ligne) | **repli fixe → Réglages** (une seule entrée) |
| `CONTEXT_EDITOR` | l'éditeur de contexte (branche fichier ou bot) | Réglages (ligne) | **repli fixe → la page de contexte (`contextReturn`)** (une seule entrée) |
| `VOICE_SETTINGS` | les réglages de Voix | Réglages (ligne) | navBack → Réglages |
| `NOTIFICATIONS` | les Notifications | Réglages (ligne) | navBack → Réglages |
| `KOKORO_VOICES` | les voix Kokoro | Réglages (ligne) | **repli fixe → Voix** (une seule entrée) |
| `CRON` | les Planifiés | volet (tuile), onglet de section, Réglages ; onglet de section | navBack → CONVERSATIONS |
| `KANBAN` | le tableau Kanban | volet (tuile), onglet de section, Réglages ; onglet de section | navBack → CONVERSATIONS |
| `CODE` | l'écran Code (sessions) | volet (tuile), onglet de section ; onglet de section | navBack → CONVERSATIONS |
| `COMPOSER_EDITOR` | l'éditeur du brouillon | fil → déplier le brouillon | CHAT (Terminé) |
| `PHONE_TOOLS` | les outils du téléphone | Réglages (ligne) | **repli fixe → Réglages** (une seule entrée) |
| `LOCK` | le Verrouillage | Réglages (ligne) | **repli fixe → Réglages** (une seule entrée) |
| `CONNECT` | Configurer un autre appareil (QR) | Réglages (ligne) | **repli fixe → Réglages** (une seule entrée) |
| `BOTS` | la grille des bots | Réglages (ligne) | navBack → Réglages |
| `BOT_EDITOR` | l'éditeur d'un bot | Bots → menu d'une tuile → Modifier | navBack → Bots |
| `BOT_FACE` | le choix du visage | Bots → menu → Changer de visage | navBack → éditeur de bot |
| `BOT_SCHEDULES` | les horaires d'un bot | Bots → menu → Horaires | navBack → éditeur de bot |
| `BOT_VOICE` | la voix d'un bot | Bots → menu → Voix | navBack → éditeur de bot ou Réglages |
| `GROUP_CHAT` | le fil d'une salle | Bots → Discussion de groupe, volet → une salle | navBack → Bots |
| `GROUP_VOICE` | la voix d'une salle | salle → « Parler » | navBack → salle |
| `ACKNOWLEDGEMENTS` | les Remerciements | Réglages (ligne) | **repli fixe → Réglages** (une seule entrée) |
| `SPEECH_LANGUAGE` | la langue d'écoute | Réglages (ligne) | **repli fixe → Voix** (une seule entrée) |
| `REPLY_LANGUAGE` | la langue de réponse | Réglages (ligne) | **repli fixe → Voix** (une seule entrée) |
| `COMPUTER` | l'Ordinateur (pont) | menu du fil | navBack → CHAT |
| `STOP_PHRASES` | les phrases d'arrêt | Réglages (ligne) | **repli fixe → Voix** (une seule entrée) |
| `SERVERS` | les Serveurs | Réglages, et la fin de la configuration | navBack → Réglages |
| `SETUP` | la configuration | première ouverture, Réglages → Serveurs → Modifier | (config) — sortie par Terminé/Ignorer → Serveurs |
| `SPOKEN_PREFIXES` | les préfixes parlés | Réglages (ligne) | **repli fixe → Voix** (une seule entrée) |
| `SPOKEN_PREFIX_EDITOR` | l'éditeur d'un préfixe | Réglages (ligne) | **repli fixe → Préfixes parlés** (une seule entrée) |
| `PROFILES` | le choix du profil | Réglages (ligne) | **repli fixe → Réglages** (une seule entrée) |
| `WHATS_NEW` | les Nouveautés | Réglages (ligne) | Réglages |
| `PATCH` | le diff (`Modifier`) | carte « Voir la modification » | navBack → CHAT |
| `SELECT_TEXT` | Sélectionner du texte | feuille d'une réponse → « … » | navBack → CHAT |
| `PHOTO` | la visionneuse photo | vignette d'une bulle / Photothèque | CHAT |
| `PDF` | le lecteur PDF | pièce jointe PDF | CHAT |
| `VIDEO` | le lecteur vidéo | pièce jointe vidéo | CHAT |
| `SKILLS` | les Compétences | volet (tuile), Réglages | navBack → Réglages |
| `SKILL_EDITOR` | l'éditeur de compétence | Réglages (ligne) | **repli fixe → Compétences** (une seule entrée) |
| `TOOLS` | les Outils | volet (tuile), Réglages | navBack → Réglages |
| `API_BOT` | un bot par clé d'API | Bots → Nouveau bot sans Dashboard ; volet → « Nouveau bot » | navBack → Bots |
| `CAMERA` | l'appareil photo | composeur → Appareil photo | CHAT |
| `TIP_JAR` | Soutenir Onyx | Réglages (ligne) | **repli fixe → Réglages** (une seule entrée) |
| `SUBAGENT` | le fil d'un sous-agent | carte d'un sous-agent | navBack → CHAT |
| `SUBAGENTS` | les tâches lancées (sous-agents) | menu du fil (« Sous-agents ») | navBack → CHAT |
| `TERMINAL` | le Terminal | menu du fil, Réglages | navBack → CHAT |
| `TERMINAL_SETTINGS` | les réglages du Terminal | Réglages (ligne) | navBack → Réglages |
| `AUDIO` | le lecteur de son | onglet Fichiers → un son | Fichiers du fil |
| `DOCUMENT` | le lecteur de document | onglet Fichiers → un document | Fichiers du fil |
| `ARCHIVE` | le contenu d'une archive | onglet Fichiers → une archive | Fichiers du fil |
| `QUICK_CAPTURE` | la capture rapide | menu du fil, raccourci de partage | navBack → CHAT |
| `CONNECTION_DETAILS` | les détails de connexion | Réglages (ligne) | navBack → Réglages |
| `HIDDEN_BOTS` | les bots masqués | Réglages (ligne) | navBack → Bots |
| `SESSION_USAGE` | l'usage et le coût d'une session | Réglages (ligne) | navBack → CONVERSATIONS |
| `PROJECT_SESSIONS` | les sessions d'un projet | Réglages (ligne) | navBack → CONVERSATIONS |
| `CAPABILITIES` | les Capacités | Réglages (ligne) | navBack → Réglages |
| `INSIGHTS` | les Aperçus | volet (tuile), Réglages | navBack → Réglages |

Deux écrans n'ont pas de branche **à leur nom** parce qu'ils la partagent, et c'est délibéré :
`CONTEXT` et `MEMORY` sont dessinés par la branche `Route.CONTEXT, Route.MEMORY ->`.

# 2. La matrice du retour

**Écran de départ → ce que fait le retour → écran d'arrivée → état conservé ? → verdict.**
Les lignes marquées **⌨** sont mesurées à l'appareil (§7) ; les autres sont tenues par les tests
de la matrice (§5), qui rejouent exactement les mêmes règles (`navigateWithBackStack`, `popRoute`,
`routeBackBehind`, `routeBackHandled`) que le `BackHandler` de la mise en page.

| Écran de départ | Ce que fait le retour | Écran d'arrivée | État conservé ? | Verdict |
|---|---|---|---|---|
| Le fil (racine, une colonne) | le retour n'est pas consommé : le système ferme l'app | le lanceur | — | ✅ cohérent avec le téléphone |
| La liste des conversations (rail, pile vide) | `rootRouteBehind` → le fil | le fil | défilement du fil | ✅ |
| Une conversation ouverte de la liste | `navBack()` → dépile | la liste, **au même endroit** | position + filtres + recherche du volet | ✅ |
| Le fil → **volet** → une tuile (⌨ Compétences, Outils, Fichiers du serveur, Planifiés, Kanban, Aperçus, Modèles) | `navBack()` → dépile la page quittée | **le fil**, volet **refermé** | onglet du bas + recherche du volet | ✅ **corrigé** (3 écrans) |
| Le fil → **volet** → « Nouveau bot » (serveur sans Dashboard) | `navBack()` → dépile | **le fil** | — | ✅ **corrigé** |
| Les Réglages → une ligne (⌨ Compétences, Planifiés, Outils, Serveurs) | `navBack()` → dépile | **les Réglages** | position de la liste des Réglages | ✅ (contrat d'origine, tenu) |
| Les Réglages → **volet** → Compétences (deux panneaux) | `navBack()` → dépile | **les Réglages** | panneau posé à sa place | ✅ |
| Le fil → **onglet du bas** (⌨ Flux, Objectifs) | ce n'est **pas** une route : le retour n'est pas consommé | **le lanceur** (l'app se ferme, comme au pied du fil) | l'onglet de la conversation est gardé (`ChatPanelStore`), avec le filtre/tri de l'onglet Fichiers et son défilement | ✅ — aucun historique d'onglets rejoué |
| Le fil → **volet ouvert** (⌨) | le volet se referme **d'abord** (`BackHandler(enabled = state.isOpen)`) | le fil, volet refermé | rien d'autre ne bouge | ✅ |
| Le fil → **recherche du fil** (⌨ « … » → Chercher) | la recherche se referme **d'abord** | le fil | fil et brouillon intacts | ✅ **corrigé** |
| Le fil → feuille d'actions / feuille du modèle | `Dialog`/`ModalBottomSheet` : la feuille se referme | le fil | — | ✅ |
| Le fil → menu ancré « … » | `DropdownMenu` : le menu se referme | le fil | — | ✅ |
| Une réponse → « … » → Sélectionner du texte | `navBack()` → dépile | **le fil** (ou la liste, en deux panneaux) | — | ✅ **corrigé** |
| Une réponse → carte « Voir la modification » | `navBack()` → dépile | **le fil** | — | ✅ **corrigé** |
| Le fil → menu « … » → Ordinateur (⌨) | `navBack()` → dépile | **le fil** | session du pont non fermée | ✅ **corrigé** |
| Ordinateur → **plein écran** | le plein écran se quitte **d'abord** | l'Ordinateur, plein écran quitté | flux et session intacts | ✅ **corrigé** (garde inversée), *non produisible à l'appareil* (§4) |
| Le fil → menu « … » → Terminal | `BackHandler` du terminal → clavier | le terminal, clavier | session SSH intacte | ✅ |
| Le fil → sous-agents → un sous-agent | `navBack()` → dépile | les sous-agents | — | ✅ |
| Une conversation → un fichier joint (PDF, vidéo, photo, son, document, archive) | `navBack()` → dépile | le fil, **onglet Fichiers intact** | filtre + tri de l'onglet | ✅ |
| Compétences → une compétence | `navBack()` → dépile | Compétences | position de la liste | ✅ |
| Fichiers → un fichier (éditeur) | `navBack()` → dépile | Fichiers | position | ✅ |
| Voix → préfixes parlés → un préfixe | `navBack()` → dépile | Préfixes parlés | — | ✅ |
| Bots → un bot (éditeur) → Horaires / Visage / Voix | `navBack()` → dépile | l'éditeur du bot | — | ✅ |
| Une salle → sa voix | `navBack()` → dépile | la salle | — | ✅ |
| Les Réglages → **Serveurs** (ou la fin de la configuration) | `navBack()` → dépile | **la page quittée** (Réglages ou Configuration) | — | ✅ **corrigé** |
| **Pliable déplié** : la racine (le fil, ou la liste qui peint le même panneau) | le retour n'est **pas** consommé — un seul geste pour rentrer | le lanceur | — | ✅ **corrigé** |
| **Pliable déplié** : le fil → volet → Kanban | `navBack()` → dépile | **le fil** | panneau à sa place | ✅ |
| **Replier / déplier** pendant qu'on est sur un écran (⌨ P1–P3) | **aucune navigation rejouée** : le format change **sur place** (plus de relance d'activité) | **le même écran** | position, onglet, volet | ✅ **corrigé** |
| L'écran à état vide (sélection, diff, photo, PDF, sous-agent, usage, sessions) atteint par restauration | le repli se pose **hors composition** et la route vide **n'entre pas** dans la pile | la page de repli | — | ✅ (hors de cette passe) |
| Écrans à **une seule entrée** (17 routes, §3) | repli fixe : la page quittée est toujours la même | cette page-là | — | ✅ assumé et **mesuré** (`RetourCoherenceTest`) |

**Aucune boucle** : aucune suite de retours ne revient sur un état déjà traversé (route + pile) et
toute suite finit par rendre la main au système — 16 parcours × 8 gestes mesurés par
`RetourMatriceTest`.

# 3. Les incohérences trouvées, corrigées

Chacune est **corrigée** avec, en face, le test qui la tient (§5) et le relevé d'avant/après (§7).
`git diff` : 10 hunks, tous bornés.

### 3.1 Un écran ouvert du **volet** ne rendait pas le fil (3 routes)

`app/src/main/java/com/onyx/app/ui/OnyxApp.kt` — **Compétences, Outils, Fichiers du serveur** sont
des **tuiles du volet**, et leur retour était écrit en dur :

```kotlin
// avant — lignes 3474 (SKILLS), 3465 (TOOLS), 3713 (FILES)
onBack = { navTo(Route.SETTINGS.name) },
// après — lignes 3498, 3487, 3741
onBack = { if (!navBack()) navTo(Route.SETTINGS.name) },
```

**Ce qui se passait** (mesuré par le test) : ouvert du volet, la page quittée est le **fil** ;
`navTo(Route.SETTINGS.name)` n'a pas la cible au sommet de la pile, donc il **empile** l'écran
quitté au lieu de le dépiler — le retour sautait aux Réglages, d'où la personne ne venait pas, et
le retour suivant **rouvrait l'écran quitté** (aller-retour sans fin). C'est le défaut signalé mot
pour mot : « le retour depuis … ne me ramenait pas sur la page précédente ».

### 3.2 Deux autres entrées du volet laissaient une entrée derrière elle

- **`API_BOT`** (ligne 2668 avant → **2684**) : ouvert du volet (« Nouveau bot » d'un serveur sans
  identifiants Dashboard), `onBack = { navTo(Route.BOTS.name) }` sautait à la liste des bots.
- **`SELECT_TEXT`** (3444 → **3463**) et **`PATCH`** (3457 → **3477**) : ouverts de la feuille d'une
  réponse, `onBack = { navTo(Route.CHAT.name) }` ; en deux panneaux la page quittée est la **liste**
  (qui peint le fil), et le `navTo` y laissait une entrée qui se rejouait.
- **`COMPUTER`** (3180 → **3198**) : ouvert du menu du fil, même correction.
- **`SERVERS`** (3531 → **3557**) : ouvert des Réglages **et** de la fin de la configuration
  (`SETUP`), et le repli fixe y empilait une entrée.

Toutes prennent la forme de la maison : `if (!navBack()) navTo(<repli>)` — **un niveau à la fois**,
puis le repli sémantique.

### 3.3 La **recherche du fil** ne prenait pas le retour

`app/src/main/java/com/onyx/app/ui/chat/ChatScreen.kt` — **633** (bloc ajouté, ligne **629**) :

```kotlin
// avant : aucun BackHandler pour `state.searchOpen`
// après
BackHandler(enabled = state.searchOpen) {
    onSearch("")
    onFindRequested()
}
```

Sur l'accueil (le fil), `backHandled` est **faux** : le retour n'était pas consommé, donc avec le
bandeau « Chercher dans la conversation » ouvert, le geste du bord **fermait l'app** — un niveau
sauté. Il fait maintenant exactement ce que fait la croix du bandeau.

### 3.4 Le **plein écran de l'Ordinateur** : garde inversée

`app/src/main/java/com/onyx/app/ui/computer/ComputerScreen.kt` — ligne **192** avant :

```kotlin
// avant — la garde ne se posait QUE hors plein écran, où elle doublait le retour système
BackHandler(enabled = !fullscreen) { onBack() }
// après — ligne 193
BackHandler { if (fullscreen) { fullscreen = false } else { onBack() } }
```

Le commentaire du code annonçait déjà le bon comportement (« le retour sort d'abord du plein
écran ») et le test `ComputerScreenUxTest` vérifiait la ligne **inversée** : la correction rend le
commentaire vrai et le test le tient.

### 3.5 Le **pliable déplié** demandait deux gestes pour rentrer

`app/src/main/java/com/onyx/app/ui/ResponsiveLayout.kt` — nouvelle règle partagée `routeBackHandled`
(ligne **205**), appelée par `OnyxApp.kt` ligne **2357** :

```kotlin
// avant (OnyxApp, 2347) : en deux panneaux la racine était CONVERSATIONS seulement
backStack.isNotEmpty() || route != Route.CONVERSATIONS.name
// après : la racine large est le FIL — le détail peint le fil pour CHAT comme pour CONVERSATIONS
backStack.isNotEmpty() ||
    (route != chatRoute && route != conversationsRoute)
```

**Ce qui se passait** (relevé sur pliable déplié) : au pied de la pile, le retour consommait la
route et rejouait `CONVERSATIONS`, qui peint le **même** panneau que `CHAT` — le premier geste du
bord ne faisait **rien de visible**, et le second fermait l'app. Deux gestes pour rentrer au lieu
d'un, et un retour qui paraît avalé. Les deux formats ont maintenant la même racine.

### 3.6 Deux entrées du volet ne le refermaient pas (incohérence interne)

`app/src/main/java/com/onyx/app/ui/OnyxApp.kt` — lignes **2275** et **2277** avant :

```kotlin
// avant — huit entrées du volet refermaient le volet, deux non
onNewRoom = { navTo(Route.BOTS.name) },
onOpenProject = { project -> selectedProject = project; navTo(Route.PROJECT_SESSIONS.name) },
// après — lignes 2278 et 2281-2286
onNewRoom = { navTo(Route.BOTS.name); sidebarState.close() },
onOpenProject = { project -> selectedProject = project
                  navTo(Route.PROJECT_SESSIONS.name)
                  sidebarState.close() },
```

Sans ça, le « + » des salles et le choix d'un projet laissaient le volet **ouvert par-dessus
l'écran où l'on venait d'arriver** : l'inverse de la règle « le retour ferme l'élément ouvert
d'abord », et deux gestes imposés. `RetourCoherenceTest` mesure l'invariant sur **toutes** les
entrées du volet, pas seulement les deux corrigées.

### 3.7 Le **pliage** relançait l'activité (la page et sa position perdues)

`app/src/main/AndroidManifest.xml` — ligne **58** avant :

```xml
<!-- avant : `smallestScreenSize` manquait -->
android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|uiMode"
<!-- après — ligne 64 -->
android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden|uiMode"
```

Le pliage change la **plus petite largeur** (411 dp replié, 862 dp déplié) : c'est
`smallestScreenSize`. Non déclarée, Android **relance** l'activité — relevé pendant le changement
de format à l'appareil : `handleRelaunchActivity` dans le journal du système, puis
`OnyxRoute: route=CHAT` **sans qu'aucun `navTo` n'ait été appelé**. Autrement dit : on regardait une
page, on dépliait (ou on repliait) l'écran, et l'app revenait au fil — la page ouverte, son volet et
sa position de défilement perdus. C'est le « replier/déplier ne doit pas rejouer une navigation ni
perdre la position » de la consigne, et c'est le geste quotidien du propriétaire (Honor Magic V3).
Déclarée, le format change **sur place** : même route, même volet, même position (relevés P1–P3).
Le test lit la ligne du manifeste.

# 4. Ce qui n'est pas produisible, dit franchement

- **Le plein écran de l'Ordinateur** (§3.4) : l'état « plein écran » n'existe qu'avec une session
  de pont **configurée** (sinon l'écran montre son formulaire). L'appareil de mesure n'a pas de pont ;
  la correction est donc tenue par le test de source (`ComputerScreenUxTest`) et par la lecture,
  pas par un relevé. **C'est la seule correction sans relevé à l'appareil.**
- **« Nouveau bot » sans identifiants Dashboard** (§3.2, `API_BOT`) : la passerelle de mesure porte
  des identifiants Dashboard, donc le volet ouvre `BOTS` et non `API_BOT`. La correction est tenue
  par la matrice et le test de source, comme dans `ROUTES-AUDIT.md` pour la même route.
- **Le volet *posé* se referme quand on ouvre une tuile** : c'est la décision documentée du lot
  précédent (« le volet est un chemin, pas un état à traîner »). Elle est **identique dans les deux
  formats** et le retour, lui, rend la page quittée. Changer ce point (garder le panneau ouvert sur
  le pliable) rendrait visible un second défaut : le volet garde son état **par route** (recherche,
  filtre, position Chats/Bots sont `rememberSaveable` **dans la page**), si bien qu'un panneau laissé
  ouvert afficherait un volet **réinitialisé** sur la page d'arrivée. Le hoister demanderait de
  toucher `SidebarHost`/`ChatSidebar` — l'autre lot y travaille. **Signalé, pas touché.**
- **`uiautomator` en deux panneaux** : à 862 dp l'arbre est parfois refusé par le noyau de
  l'appareil (relevé vide). Les cas deux panneaux sont rejoués et, quand le relevé manque, c'est
  écrit « ÉCHEC-RELEVÉ » — jamais compté comme une incohérence de l'app.

# 5. Les tests

| Fichier | Ce qu'il tient |
|---|---|
| `app/src/test/java/com/onyx/app/ui/RetourMatriceTest.kt` | **la matrice du retour** : 16 parcours × (destination attendue, aucune boucle, l'app finit par se fermer) + les racines (une colonne / deux panneaux) + le pliable (**dont le pliage qui ne relance plus l'activité**) + **les 66 routes dessinées et atteignables** + la position conservée (défilements sauvegardables, `SaveableStateProvider` par route) + les onglets du bas + le volet |
| `app/src/test/java/com/onyx/app/ui/RetourCoherenceTest.kt` | **un test par incohérence corrigée** : recherche du fil, plein écran de l'Ordinateur, chaque entrée du volet referme le volet, les tuiles du volet ne se rabattent pas sur les Réglages, `API_BOT`, Ordinateur/sélection/correctif, Serveurs, l'aller-retour que produisait un `onBack` en `navTo`, **et l'inventaire des retours fixes** (18 écrans à une seule entrée — tout autre retour fixe fait échouer) |
| `app/src/test/java/com/onyx/app/ui/SettingsReturnTest.kt` (mis à jour) | la règle partagée `routeBackHandled` (les trois branches) |
| `app/src/test/java/com/onyx/app/ComputerScreenUxTest.kt` (mis à jour) | le plein écran se quitte **avant** l'écran |
| `app/src/test/java/com/onyx/app/ui/AbsentRoutesReachabilityTest.kt` (mis à jour) | la condition de retour vient de la règle partagée |

**22 tests ajoutés** (2 classes), plancher **2146** → **2168**, **0 échec**.

# 6. Preuves

- **Arbres d'accessibilité** : `qa/20261027-retour-routes/trees/<cas>-{1-avant,2-enfant,3-apres}.xml`
  (l'arbre entier), et le titre + les premiers éléments de chaque arbre dans les relevés.
- **Relevés** : `results.jsonl` (une ligne par cas et par geste), `run-compact.log`,
  `run-deux.log`, `run-plier.log` (les passes complètes) et `frag-releves.md` (le §7, fabriqué
  par `rapport.py`).
- **Captures** : `qa/20261027-retour-routes/shots/<cas>-{1-avant,2-enfant,3-apres}.png`.
- **Boîte à outils** : `driver.py` (ancres d'accessibilité, `edge_back` calculé sur la taille
  courante, `snap()` qui écrit l'arbre entier), `run.py` (les parcours, `geom compact|deux`),
  `cases.py`, `fold.py` (replier/déplier), `rapport.py` (le §7), `inject.py` (serveur posé dans le
  `DataStore`).
- **APK** : `…/kilo/cache/scratch/out/onyx-retour-1.0.0+469.apk` — `versionCode 469`,
  `sha256 d52debee9c07436a64fb06f8f37b63e5998633385cbcd975cdfa00759c14eb37`.

# 7. Les relevés à l'appareil

**Une colonne** (411 dp — le téléphone, le pliable replié).

### Les 16 parcours — 16/16 ✅

| Cas | Retour joué | Arbre **avant** (titre — premiers éléments) | Arbre **après** | Verdict |
|---|---|---|---|---|
| `01-VOLET-Competences-systeme` | touche retour | **Settings** — Chats | Bots | **Expand list** — Hello. | What should Onyx look into? | ✅ OK |
| `02-VOLET-Outils-bord` | geste du bord | **Settings** — Chats | Bots | **Expand list** — Hello. | What should Onyx look into? | ✅ OK |
| `03-VOLET-FichiersServeur-systeme` | touche retour | **Settings** — Chats | Bots | **Expand list** — Hello. | What should Onyx look into? | ✅ OK |
| `04-VOLET-Planifies-systeme` | touche retour | **Settings** — Chats | Bots | **Expand list** — Hello. | What should Onyx look into? | ✅ OK |
| `05-VOLET-Kanban-bord` | geste du bord | **Settings** — Chats | Bots | **Expand list** — Hello. | What should Onyx look into? | ✅ OK |
| `06-VOLET-Apercus-systeme` | touche retour | **Settings** — Chats | Bots | **Expand list** — Hello. | What should Onyx look into? | ✅ OK |
| `07-VOLET-Modeles-systeme` | touche retour | **Settings** — Chats | Bots | **Expand list** — Hello. | What should Onyx look into? | ✅ OK |
| `08-REGLAGES-Apparence-systeme` | touche retour | **Done** — Edit | Onyx | **Done** — Edit | Onyx | ✅ OK |
| `09-REGLAGES-Voix-systeme` | touche retour (rejoué) | **Done** — Hermes API · http://10.0.2.2:8650 | Add a server | **Done** — Hermes API · http://10.0.2.2:8650 | Add a server | ✅ OK |
| `10-REGLAGES-Notifications-bord` | geste du bord | **Done** — Hermes API · http://10.0.2.2:8650 | Add a server | **Done** — Hermes API · http://10.0.2.2:8650 | Add a server | ✅ OK |
| `11-REGLAGES-Journaux-systeme` | touche retour | **Done** — Hermes API · http://10.0.2.2:8650 | Add a server | **Done** — Hermes API · http://10.0.2.2:8650 | Add a server | ✅ OK |
| `12-ONGLET-Flux-systeme` | touche retour (rejoué) | **Expand list** — Hello. | What should Onyx look into? | **Home** — Gmail | Photos | ✅ OK · l'app a rendu la main |
| `13-ONGLET-Objectifs-systeme` | touche retour (rejoué) | **Expand list** — Hello. | What should Onyx look into? | **Home** — Gmail | Photos | ✅ OK · l'app a rendu la main |
| `14-FIL-Volet-se-referme` | touche retour (rejoué) | **Expand list** — Hello. | What should Onyx look into? | **Expand list** — Hello. | What should Onyx look into? | ✅ OK |
| `15-FIL-Recherche-systeme` | touche retour | **?** — Onyx | Find in conversation | **Expand list** — Hello. | What should Onyx look into? | ✅ OK |
| `16-REGLAGES-Position-conservee` | touche retour | **Done** — VOICE | Voice | **Done** — VOICE | Voice | ✅ OK · position de la ligne repère **identique** |

**Deux panneaux** (862 dp — le pliable déplié, la géométrie du propriétaire).

### Les 4 parcours — 4/4 ✅

| Cas | Retour joué | Arbre **avant** (titre — premiers éléments) | Arbre **après** | Verdict |
|---|---|---|---|---|
| `Z1-DEUX-racine-une-seule-fois` | touche retour | **Collapse list** — Chats | Bots | **Home** — Google search | Voice search | ✅ OK · l'app a rendu la main |
| `Z2-DEUX-Kanban-systeme` | touche retour | **Collapse list** — Chats | Bots | **Expand list** — Hello. | What should Onyx look into? | ✅ OK |
| `Z3-DEUX-Competences-bord` | geste du bord | **Collapse list** — Chats | Bots | **Expand list** — Hello. | What should Onyx look into? | ✅ OK |
| `Z4-DEUX-REGLAGES-Apparence` | touche retour | **Expand list** — Hermes API · http://10.0.2.2:8650 | Add a server | **Expand list** — Hermes API · http://10.0.2.2:8650 | Add a server | ✅ OK |

**Replier / déplier l'écran** pendant qu'on est sur un écran.

### Les 3 changements de format — 3/3 ✅

| Cas | Retour joué | Arbre **avant** (titre — premiers éléments) | Arbre **après** | Verdict |
|---|---|---|---|---|
| `P1-PLIER-Fil` | changement de format | **Expand list** — Hello. | What should Onyx look into? | **Collapse list** — Chats | Bots | ✅ OK · écran conservé : oui |
| `P2-PLIER-Competences` | changement de format | **Back** — No skills yet | A skill is a SKILL.md the agent loads on demand when a request matches. | **Back** — No skills yet | A skill is a SKILL.md the agent loads on demand when a request matches. | ✅ OK · écran conservé : oui |
| `P3-DEPLIER-Competences` | changement de format | **Back** — No skills yet | A skill is a SKILL.md the agent loads on demand when a request matches. | **Back** — No skills yet | A skill is a SKILL.md the agent loads on demand when a request matches. | ✅ OK · écran conservé : oui |

