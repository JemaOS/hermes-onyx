# Architecture — les cinq fonctions du chantier TOP5

Ce document décrit **comment** les cinq fonctions retenues (voir `TOP5.md` de conversation) entrent
dans Onyx **sans toucher** à ce qui existe déjà. Il ne contient aucune décision de goût : chaque
point d'accroche cite un fichier réel du dépôt, lu sur `main` (`e8c195a`).

---

## 0. Le contrat de non-régression

Cinq règles. Un lot qui en viole une est refusé, même s'il fonctionne.

1. **Additif seulement.** Aucune signature existante ne change sans paramètre par défaut. Le modèle
   est déjà dans le dépôt : `BotEditorScreen` reçoit `onOpenTools: (() -> Unit)? = null` et
   `onOpenSkills: (() -> Unit)? = null` (`BotEditorScreen.kt:326-327`) — des capacités ajoutées
   sans casser un seul appelant.
2. **Aucun nouvel écran sans porte.** Un écran nouveau n'est atteignable que par une entrée qui
   **s'efface** quand le serveur ne sait pas la servir — le motif de ces mêmes lambdas nulles :
   fournir `null` ne dessine rien plutôt que de laisser un tap mort.
3. **Aucun nouveau client réseau.** Tout passe par `ServeClient.request(path, method, query, body,
   timeoutSeconds)` (`core/.../data/net/ServeClient.kt:128`). Une famille de routes nouvelle =
   un fichier `*Api.kt` miroir de `ServeSkillsApi.kt`, jamais un second transport.
4. **Rien qui casse hors ligne ni sous un serveur ancien.** Une route absente rend
   `LoadState.Failed` (`chat/components/ChatActionSheet.kt:808`) ou un objet « désactivé » —
   précédent : `RestorePointList(enabled = false)` (`ServeChatActions.kt:516-526`).
5. **Interdit de toucher** dans ce chantier : les cinq onglets du fil, la scène vocale, les rooms,
   `Computer`, le flux de chat SSE, la navigation existante. Nous **ajoutons** des routes à
   `Route`, nous n'en renommons aucune (`OnyxApp.kt:234`).

## 1. Les quatre briques du socle

Tout le reste en dépend ; elles se livrent **avant** les fonctions, dans cet ordre.

### B1 — `ServerProbe` : ce que ce serveur sait faire

**Problème réel** : aucune route de capacités n'existe (`grep "capab\|supports"` sur
`ServeClient.kt` / `ServeRpc.kt` : zéro). Chaque écran découvre l'absence **en échouant**.

**Brique** : `core/.../data/net/ServerProbe.kt`
- une sonde par couple `(serveur, configSignature)`, résultat en cache mémoire + disque ;
- chaque capacité est un `enum class Support { YES, NO, UNKNOWN }` ;
- une sonde = un `GET` léger sur une route existante et idempotente, avec un timeout court ;
- jamais dans le chemin critique : `UNKNOWN` se comporte comme `NO`.

**Conséquence** : les cinq fonctions deviennent des **drapeaux de dessin**, pas des exceptions.

### B2 — `VaultClient` : écrire un secret sans jamais le relire

**Brique** : `core/.../data/net/VaultClient.kt`, miroir de `ServeSkillsApi.kt`.
- `put(profile, name, value)` : la valeur part, l'app **ne la garde pas** ;
- `list(profile)` : rend `name`, `fingerprint` (4 derniers), `usedBy` ;
- `revoke(profile, name)`.

**Garde-fou** : aucun log, aucun `toString()`, aucune sauvegarde d'état Compose ne porte la valeur
— le modèle est `SecretStore` (`data/local/SecretStore.kt`) pour le stockage local, mais ici **rien
n'est stocké côté téléphone**.

### B3 — `RecordingSession` : la démonstration

**Brique** : `core/.../data/net/RecordingSession.kt`, **au-dessus** de `ComputerBridgeClient.kt`
(existant, 752 lignes : il sait déjà l'écran et le toucher).
- démarre/arrête une capture **des actions de l'utilisateur** (tap, saisie, navigation),
  avec horodatage et cible ;
- mémoire seulement, plafond dur (10 minutes, comme la référence) puis `buf` compressé ;
- produit un **journal neutre** (pas de pixels par défaut), converti en brouillon de skill.

**Décision (tranchée, voir §7)** : le journal d'**actions** seulement — tap, saisie, défilement,
navigation, attente —, jamais les pixels, jamais l'audio, jamais un champ marqué secret.

### B4 — `SlashRegistry` : les `/` extensibles

**État** : `SlashCommands.kt:48-53` — six commandes fixes (`/model`, `/new`, `/find`, `/settings`,
`/voice`, `/help`) et un `when` sur l'entrée.

**Brique** : une **liste fournie**, pas une liste codée : `defaultCommands + skillCommands(profile)`
où `skillCommands` vient de `ServeSkillsApi.skills(profile)`. Le picker et l'exécution lisent la
même liste, donc un `/nom-de-skill` s'insère comme un `/model` — et disparaît si le profil n'a pas
ce skill.

---

## 2. Les cinq fonctions, une par lot

Chaque lot : une branche `lot/top5-<nom>` depuis `main`, un rapport `RAPPORT-TOP5-<NOM>.md`, les
captures sous `qa/<date>-top5-<nom>/`. Aucune migration de préférences, aucun changement de base.

### Lot 1 — Skills par `/` (1 jour)

| | |
|---|---|
| Surface | le sélecteur `/` du composeur, existant |
| Données | `ServeSkillsApi.skills(profile)` — route **déjà utilisée** |
| Routes serveur | aucune |
| Accroche | `SlashCommands.kt` (liste → fournisseur), `ChatComposer` (aucune signature changée, la liste est passée) |
| Repli | profil sans skill → la liste est celle d'aujourd'hui, à l'identique |
| Preuve | test pur : `SkillSlashCommandsTest` — les six commandes restent, l'ordre ne bouge pas, un skill produit la même `SlashAction` qu'une commande |

**Pourquoi en premier** : zéro dépendance serveur, zéro surface nouvelle, effet immédiat.

### Lot 2 — Coffre (4 jours, dont 2 côté serveur)

| | |
|---|---|
| Surface | **nouvel écran** `VAULT` + entrée dans « Capacités », sous « Secrets » |
| Données | `VaultClient` (B2) |
| Routes serveur | `vault.list`, `vault.put`, `vault.revoke` — **à créer côté Hermes** |
| Accroche | `OnyxApp.kt:234` (+1 constante), un `when` de route, `RouteRetours.kt` (libellé de retour), `CapabilitiesScreen` (entrée) |
| Repli | `ServerProbe` dit `NO` → l'entrée n'est pas dessinée ; l'écran n'existe pas dans l'historique |
| Preuve | test de dessin : sans la capacité, aucune ligne « Secrets » ; avec, une ligne par secret, jamais de valeur |

### Lot 3 — `Teach a task` (1,5 semaine, dont la moitié serveur)

| | |
|---|---|
| Surface | bouton « Show me once » dans `ComputerScreen`, puis brouillon dans `SkillEditorScreen`, puis « en faire une routine » vers `CronScreen` |
| Données | `RecordingSession` (B3) → texte de skill |
| Routes serveur | `skills.record.start/stop` (ou 100 % local si tu préfères), `skills.draft` |
| Accroche | `ComputerScreen.kt` (bouton, aucun comportement existant modifié), `SkillEditorScreen` (pré-remplissage), `CronScreen` (lien existant) |
| Repli | pas de pont Computer → bouton absent ; pont sans route de capture → bouton grisé avec la phrase du serveur |
| Preuve | test pur du **convertisseur** journal→skill (entrée : 40 événements, sortie : un markdown stable) + test du bouton absent sans capacité |
| Risque | le seul lot qui touche une surface sensible (l'écran). Il est derrière `ServerProbe` + un réglage `top5.recording = off` par défaut |

### Lot 4 — Templates de bot partageables (3 jours)

| | |
|---|---|
| Surface | dans les détails d'un bot : « Share as template » → lien + QR ; à l'arrivée : aperçu puis « Add » |
| Données | **`BotTemplateSpec` existe déjà** (`BotEditorScreen.kt:85`, modèles `blank`/`Researcher`/`Writer`/`Coder`/`Home`/`Planner`) — on ne crée pas un modèle de données, on le **sérialise** |
| Routes serveur | aucune : le partage est un encodage local (JSON → lien, `onyx://bot/…`) |
| Accroche | `BotEditorScreen` (action de menu), `SetupCodeScanner`/`QrCodeDecoder` (déjà là pour l'appairage), `OnyxApp` (route d'arrivée) |
| Repli | lien illisible → phrase, rien n'est créé ; lien lisible → **aperçu obligatoire** avant écriture |
| Interdit | aucun secret, aucune conversation, aucune identité de serveur dans le lien — l'aperçu le dit ligne par ligne |
| Preuve | test d'aller-retour : `spec → lien → spec` identique ; test de refus : un lien contenant `apiKey` est rejeté, pas nettoyé en silence |

### Lot 5 — Bot principal et passage de main (1 semaine, surtout serveur)

| | |
|---|---|
| Surface | dans une room : désigner un « main bot » ; dans le fil, l'événement de passage (« Talk hands to Coder ») |
| Données | le modèle de room existant (`GroupRoomFeed`, `GroupRoomJournal`) |
| Routes serveur | `groups.handoff`, `groups.lead` — **à créer côté Hermes** |
| Accroche | `GroupRoomScreen` — une ligne de menu ajoutée à côté de l'entrée existante `onPickBotModel` (`GroupRoomScreen.kt:168`, dessinée en `:723`), `GroupRoomFeed` (un type d'événement de plus, rendu par la même liste) |
| Repli | serveur sans handoff → l'entrée de menu n'existe pas ; les rooms actuelles se comportent exactement comme aujourd'hui |
| Preuve | test pur : un journal avec un handoff produit les mêmes rangées + une, sans réordonner les existantes |

---

## 3. Ce qui reste côté Hermes (à spécifier, pas à coder ici)

**Décision (voir §7)** : les routes du coffre ne sont **pas** un patch de Hermes. Elles vivent dans
un **plugin**, `~/.hermes/plugins/onyx-vault/` — c'est un mécanisme du produit, pas un contournement :
les plugins montent leurs routes avec `APIRouter` sous `/api/plugins/<nom>/`
(`plugins/kanban/dashboard/plugin_api.py:1-43` en est l'exemple vivant dans l'arbre).

| Route | Pour | Où | Lot |
|---|---|---|---|
| `vault.list` / `vault.put` / `vault.revoke` | coffre par profil | **plugin `onyx-vault`** (`plugin.yaml` + `dashboard/plugin_api.py`) | 2 |
| `skills.record.start` / `stop` / `skills.draft` | démonstration → skill | plugin `onyx-vault` ou second plugin ; rien dans le core | 3 |
| `groups.lead` / `groups.handoff` | bot principal | core Hermes (c'est une notion de room) | 5 |

**Deux capacités, pas une** : `lead` et `handoff` portent chacune leur sonde — un serveur peut connaître l'un sans l'autre, et un seul drapeau commun dessinerait une entrée de menu qui échoue. Voir §8.

Aucune n'est bloquante pour les lots 1 et 4 : **ces deux-là se livrent sans toucher au serveur.**

## 3 bis. Le plugin `onyx-vault`, en clair

```
~/.hermes/plugins/onyx-vault/
├── plugin.yaml            # name, description, version
├── __init__.py            # register(ctx) — éventuellement un SecretSource
└── dashboard/plugin_api.py # APIRouter → vault.list / vault.put / vault.revoke
```

- **Ce qu'il n'est pas** : un fork de Hermes, ni un patch de `~/.hermes/hermes-agent` (un
  `hermes update` l'écraserait). Le dossier se retire = la fonction disparaît proprement.
- **Ce qu'il est** : un plugin ordinaire, monté sur `/api/plugins/onyx-vault/`, avec l'auth du
  Dashboard, donc Onyx le consomme comme n'importe quelle route — par `ServeClient`.
- **Option en plus, pas obligation** : s'enregistrer comme `SecretSource`
  (`agent.secret_sources.base.SecretSource`, doc `secret-source-plugin.md`) pour que les clés du
  coffre arrivent aussi dans l'environnement du bot au démarrage. Le cadre possède l'ordre et la
  précédence ; on n'écrit que `fetch()`.
- **Limite à écrire dans l'écran** : un plugin tourne avec les privilèges de l'agent. Le coffre est
  donc un composant sensible, pas un bac à sable.

## 3 ter. Preuve du montage — vérifiée, pas supposée

Essai mené dans un `HERMES_HOME` **jetable** (la config réelle et le serveur en cours ne sont pas
touchés) : un plugin minimal `onyx-probe` posé dans `<home>/plugins/onyx-probe/dashboard/`, un
`plugins.enabled` dans `config.yaml`, puis le même chemin que les tests du dépôt —
découverte → `_get_dashboard_plugins(force_rescan=True)` → `_mount_plugin_api_routes()`.

**Cas 1 — plugin activé** (`plugins.enabled: [onyx-probe]`) :

```
plugins découverts : ['onyx-probe', 'hermes-achievements', 'kanban']
routes montées pour onyx-probe : ['/api/plugins/onyx-probe/list', '/api/plugins/onyx-probe/put']
GET /api/plugins/onyx-probe/list -> 401 {"detail":"Unauthorized"}
```

→ les routes **montent** sous `/api/plugins/<nom>/`, et le `401` prouve que **l'authentification du
Dashboard est devant** (donc Onyx passe la même clé que pour le reste).

**Cas 2 — plugin présent mais non activé** (`plugins.enabled: []`) :

```
plugins découverts : ['onyx-probe', 'hermes-achievements', 'kanban']
routes montées pour onyx-probe : []
```

→ le plugin est **vu**, mais son code Python n'est **jamais importé** : la porte
`plugins.enabled` est une garde de sécurité (GHSA-mcfc-hp25-cjv7), pas une formalité. **Il faut donc
activer le plugin**, sinon le coffre n'existe pas — c'est une ligne de documentation à ne pas
oublier dans le lot 2.

**Bonus vérifié** : le montage à chaud existe (`_PluginApiHotMount`,
`hermes_cli/web_server_dashboard.py:1084-1105`) — la **première requête** sur
`/api/plugins/<nom>/…` monte les routes dans le processus, **sans redémarrer** le serveur. Le
développement du coffre ne demande donc pas de couper ton Hermes.

**Conclusion** : ADR-2 tient. Le coffre est un plugin activé, pas un patch du serveur.

## 4. Ordre, et pourquoi

```
Lot 1  (1 j,  0 dépendance)      → prouve le socle B4, livre tout de suite
Lot 2  (4 j,  3 routes)          → débloque le reste : sans coffre, un bot ne fait rien de réel
Lot 4  (3 j,  0 dépendance)      → croissance ; réutilise un modèle existant
Lot 3  (1,5 sem., 3 routes + B3) → la démonstration, le vrai différenciateur
Lot 5  (1 sem., 2 routes)        → l'équipe ; ne vaut rien avant que les bots puissent agir
```

`B1 (ServerProbe)` est écrit **dans le lot 1** (une journée), parce que le lot 1 n'en a pas besoin
mais le lot 2 si — et il vaut mieux l'écrire quand il ne bloque rien.

## 5. Recette — ce qu'on vérifie à chaque lot

1. `:core:testDebugUnitTest` et `:app:testDebugUnitTest` avec `--rerun --no-build-cache`, comptes
   lus **dans les XML** (jamais dans la sortie Gradle), zéro échec.
2. Le compte de tests **monte** ; aucun test existant n'est modifié pour passer.
3. Sur un serveur **sans** les routes : l'entrée est absente, aucun écran vide, aucun tap mort.
4. Sur un serveur **avec** les routes : la fonction marche, et le retour arrière ramène à l'écran
   d'où l'on vient (entrée ajoutée à `RouteRetours.kt`).
5. Toute chaîne visible est dans le catalogue : un fichier `OnyxFrenchTop5.kt` ajouté à la
   concaténation de `OnyxLocalization.kt:221` — sinon `MenuAndSheetLocalizationTest` doit échouer.
6. `git status --short` ne montre que les fichiers du lot ; les artefacts de build ne sont pas
   indexés.

## 6. Les trois risques, et leur parade

| Risque | Parade |
|---|---|
| La démonstration capture des données sensibles | journal d'**actions** par défaut (pas d'images), plafond 10 min, réglage `off`, et l'aperçu avant conversion montre la séquence ligne par ligne |
| Le coffre devient une fuite | valeur jamais relue, jamais journalisée, jamais sauvegardée dans l'état Compose ; l'écran n'affiche qu'une empreinte à 4 caractères ; test qui refuse un `toString()` de l'objet |
| Les 235 tests qui lisent le source bloquent le refactor | tout ce qui est logique (convertisseur, codec, fournisseur de commandes) est un **objet pur** testé par comportement ; les tests de source restent pour la garde visuelle, on n'en ajoute pas |

---

## 7. Décisions tranchées

### ADR-1 — La démonstration enregistre des **actions**, pas des images

**Décision** : la capture produit un **journal typé** — `tap(position, libellé si accessible)`,
`type(texte, masqué si champ secret)`, `scroll`, `navigate(url)`, `wait(ms)`, `key(back/home)` — et
rien d'autre. Pas de pixels, pas d'audio, pas de presse-papiers.

**Ce que ça donne** : une démonstration de 10 minutes pèse quelques kilo-octets, la conversion en
skill est **déterministe** (donc testable sans écran), et aucun contenu visuel ne quitte le
téléphone tant qu'une action ne le nomme pas.

**Ce qu'on accepte de perdre** : les gestes purement visuels (glisser sur un canevas, dessiner,
choisir une couleur). Le brouillon le **dit** sur l'étape concernée (« cette étape aura peut-être
besoin d'une capture ») au lieu de faire semblant.

**Repli honnête** : une cible sans libellé d'accessibilité devient `tap 42 % / 18 % de la fenêtre`,
marquée « à relire ». Jamais une étape silencieuse.

**Effet sur le plan** : B3 tombe à ~150 lignes + un test pur du convertisseur ; le lot 3 passe de
2 semaines à **1,5 semaine**, et le risque « fuite de données sensibles » tombe au niveau d'un
réglage (`top5.recording`, `off` par défaut).

### ADR-2 — Le coffre est un **plugin Hermes**, pas un patch du serveur

**Décision** : `vault.*` est servi par `~/.hermes/plugins/onyx-vault/` (un `APIRouter` monté sous
`/api/plugins/onyx-vault/`, exactement comme `plugins/kanban/dashboard/plugin_api.py`), avec en
option l'enregistrement d'un `SecretSource` pour exposer les clés au démarrage de l'agent.

**Pourquoi pas autrement** : patcher `~/.hermes/hermes-agent` serait écrasé au premier
`hermes update` ; forker Hermes coûte une vie de maintenance ; mettre les secrets dans le téléphone
contredit l'objectif (le bot tourne sur le serveur, pas sur le téléphone).

**Ce que ça donne** : la fonction s'installe et se retire par un dossier, elle survit aux mises à
jour, elle utilise l'authentification du Dashboard (donc Onyx la consomme par `ServeClient`, sans
transport nouveau), et le dépôt Hermes reste intact.

**Ce qu'on accepte** : un plugin tourne avec les privilèges de l'agent — ce n'est pas un bac à
sable. L'écran du coffre le dit, et le test refuse qu'une valeur parte dans un log ou dans un
`toString()`.

**Effet sur le plan** : le lot 2 reste à 4 jours, dont **2 j de plugin** et 2 j d'écran ; et le lot
3 n'a plus besoin du core pour `skills.record.*`.

---

## 8. Amendements après la première vague (vérifiés, pas supposés)

Ces cinq points sortent du travail réellement exécuté : ils corrigent le plan, ils ne l'habillent pas.

### 8.1 Le journal de démonstration n'entre pas dans le coffre

Le lot 3 vivra dans un **second plugin**, `onyx-record`, pas dans `onyx-vault`. Raison : la garde
centrale du coffre est « une valeur n'est **jamais** relue » ; un journal de démonstration, lui, est
relu par construction (on l'affiche avant d'en faire un skill). Les mettre dans le même composant
obligerait à écrire une exception dans la règle la plus utile du coffre.

### 8.2 `lead` et `handoff` sont deux capacités distinctes

Un serveur peut savoir passer la main sans savoir désigner un meneur, ou l'inverse. Deux sondes,
deux entrées de menu, deux replis — jamais un drapeau commun : il dessinerait une entrée qui échoue.

### 8.3 Deux pièges du code existant, écrits comme tests obligatoires du lot 5

- `GroupEvent.from` lit `payload.member_id` **avant** l'identité de l'acteur (`GroupThread.kt:82-84`,
  vérifié). Un événement `bot.handoff` qui porterait cette clé volerait l'auteur de la ligne : le
  test doit figer que l'acteur reste le bon.
- `turn.cancelled` de raison `handed_over` (le type existe, `GroupThread.kt:56`) doit rendre `null`
  dans la note d'événement, sinon le journal affiche « X was stopped » **et** le passage de main pour
  un seul geste.

### 8.4 Une référence du plan était fausse

`onOpenBotModels` **n'existe pas** sur `main` : il appartient à un travail non commité d'une autre
branche. Le vrai motif d'entrée conditionnelle d'une room est `onPickBotModel` (`GroupRoomScreen.kt:168`,
dessiné en `:723`). Corrigé au §2, lot 5. C'est la deuxième fois que ce plan cite un symbole non
vérifié ; la règle « citer ou ne pas affirmer » vaut aussi pour ce document.

### 8.5 Ce que le lot 1 a appris sur l'invocation des skills

Hermes n'invoque pas un skill en envoyant une ligne `/nom` : `slash.exec` **refuse** exprès une
commande de skill (`SkillSlashRefused`, `tui_gateway/slash_worker.py:89-112`). La voie réelle est
`command.dispatch` (`tui_gateway/contracts/tools_commands.py:298`), qui rend un `message` à soumettre
comme tour. Onyx empruntait déjà cette voie pour les commandes utilisateur (`ChatViewModel.kt:1154`,
`:1195-1204`) : le lot 1 la réutilise, il n'invente aucune route.
