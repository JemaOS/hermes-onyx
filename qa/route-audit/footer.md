
# Incohérences trouvées

Trois **bloquantes** (l'app se ferme, ou l'écran quitté n'est pas retrouvé), quatre **gênantes**
(le retour aboutit ailleurs qu'où l'on était), une **cosmétique**. Chaque fois : route, point
d'entrée, geste, attendu, obtenu, captures avant/après et coordonnées relevées.

## Bloquantes

### B1 — Réglages → **Kanban** puis retour → **les Réglages** *(signalé par le propriétaire)*

- **Route** `KANBAN` · **point d'entrée** Réglages › Kanban · **geste** retour système.
- **Attendu** la liste des conversations · **obtenu** l'écran « Réglages », à la même position.
- **Relevé** (`results-avant.jsonl`, cas `17-SETTINGS-Kanban`) : ligne repère « Kanban » à
  `(163, 329, 932, 381)` **avant** et **après** — on est revenu aux Réglages, pas ailleurs :
  écran après = « Réglages | Terminé | Cron | Kanban | Le contexte, la mémoire… ».
- **Captures** `shots/17-SETTINGS-Kanban-3-avant.png` → `…-5-apres.png`. Même chose pour **Cron**
  (cas `16-SETTINGS-Cron`, repère `(163, 246, 932, 264)`).
- **Cause** : les Réglages s'ouvrent **par-dessus** l'écran courant et s'empilaient sous le
  tableau ; le retour dépilait d'abord la feuille.

### B2 — Liste → **rangée d'un bot** puis retour → **l'app se ferme**

- **Route** `CHAT` · **point d'entrée** liste › rangée « Quill » · **geste** retour système.
- **Attendu** la liste · **obtenu** **le lanceur** : écran après = « At a glance | Mon, Oct 26 |
  Analog | … » (capture `shots/L3-LISTE-RangeeBotVersFil-5-apres.png`).
- **Cause** : la pile contenait le fil (`CHAT`) sous la liste ; l'ouverture de la conversation a
  dépilé cette entrée (déduplication), la pile est restée **vide sur le fil** — et à la racine,
  le retour système ferme l'activité.

### B3 — Liste → **Nouveau bot** puis retour → **le fil**

- **Route** `BOTS` · **point d'entrée** liste › Nouveau bot · **geste** retour système.
- **Attendu** la liste · **obtenu** le fil : écran après = « Conversations | Nouvelle
  conversation | Plus | Bonjour. » (capture `shots/L5-LISTE-NouveauBot-5-apres.png`).
- **Cause** : `onNewBot` écrivait `route` **en direct** (`route = …`) au lieu de passer par la
  pile : la liste n'était pas empilée, et le retour dépilait l'entrée d'avant.

## Gênantes

### G1 — Réglages ouvertes depuis la liste: « Terminé » → **le fil**

`SETTINGS`, entrée liste › engrenage, contrôle « Terminé ». Attendu : la liste quittée. Obtenu : le
fil (cas `F5-FEUILLE-Reglages-ClicSurTermine`, écran après = « Conversations | … | Bonjour. »).
Le bouton système, lui, revenait bien à la liste — les deux retours ne disaient pas la même chose.

### G2 — Réglages → **Modèle** puis retour → **le fil**

`MODELS`, entrée Réglages › Modèle. Attendu : les Réglages quittées. Obtenu : le fil (la table
`MODELS` de la source porte `onBack = { navTo(Route.CHAT.name) }`, sans dépiler).

### G3 — Voix ouverte depuis la scène vocale: retour → **les Réglages**

`VOICE_SETTINGS` a deux entrées (Réglages › Voix, et scène vocale › Réglages). Son contrôle de
retour renvoyait toujours aux Réglages. **Non reproduit à l'appareil** (la scène vocale demande un
micro) : constaté dans la source (`onBack = { navTo(Route.SETTINGS.name) }`).

### G4 — Salle de groupe ouverte depuis la liste: retour → **Bots**

`GROUP_CHAT` s'ouvre depuis la liste des conversations **et** depuis l'onglet Bots ; son retour
renvoyait toujours sur Bots. Constaté dans la source (deux `onBack = { navTo(Route.BOTS.name) }`).

## Cosmétique

### C1 — `navTo` pendant la composition sur les états vides

Les branches `SUBAGENT`, `PHOTO`, `PDF`, `CONTEXT_EDITOR` (et les redirections
`SESSION_USAGE` / `PROJECT_SESSIONS`) appellent `navTo(...)` **dans le corps composable** quand
leur état est vide, au lieu de le faire dans un `LaunchedEffect`. C'est une écriture d'état pendant
la composition : une recomposition perdue, et un retour de plus quand l'état vide se reproduit.
Non reproduit (il faut l'état vide exact) ; signalé, non corrigé — voir « Ce qui reste ».

# Corrections faites

| # | Correction | Fichier | Test qui la tient |
|---|---|---|---|
| 1 | **Un onglet de section est une racine** : l'ouvrir **vide la pile**, donc la feuille qui l'a ouvert se referme (B1). | `app/.../ui/ResponsiveLayout.kt` — `navigateWithBackStack`, `SectionTabRoutes` | ``un onglet de section ouvert depuis les Reglages ne renvoie pas aux Reglages``, ``le meme chemin vaut pour l onglet Cron``, ``un onglet de section reste la racine en deux panneaux`` |
| 2 | **Le repli du retour à pile vide est une règle partagée** (`rootRouteBehind`) : un onglet de section retombe sur la **liste des conversations**, jamais sur les Réglages. | `ResponsiveLayout.kt` + `OnyxApp.kt` (`BackHandler`) | ``le fil reste la racine compacte partout ailleurs``, `AbsentRoutesReachabilityTest > le retour du systeme n est jamais avale` (réaligné) |
| 3 | **L'onglet Cron** retombe sur la liste (il retombait sur les Réglages). | `OnyxApp.kt` — `cronPane` | ``les sous-pages depilent avant de se rabattre sur leur parent logique`` |
| 4 | **« Terminé » des Réglages** revient à l'écran quitté (G1). | `OnyxApp.kt` — branche `SETTINGS` | ``les Reglages referment la feuille et reviennent a l ecran quitte`` |
| 5 | **« Modèle »** revient à l'écran quitté (G2). | `OnyxApp.kt` — branche `MODELS` | idem 4 |
| 6 | **Voix** et **salle de groupe** dépilent d'abord, et ne se rabattent sur Bots / Réglages qu'à pile vide (G3, G4). | `OnyxApp.kt` | ``les sous-pages depilent…`` |
| 7 | **« Nouveau bot »** passe par la pile au lieu d'écrire `route` en direct (B3). | `OnyxApp.kt` — `conversationsPane` | ``les sous-pages depilent…`` (contrôle : aucune route écrite en direct) |
| 8 | **Ouvrir une conversation depuis la liste** n'y laisse plus une pile vide (B2). | `ResponsiveLayout.kt` | ``une conversation ouverte depuis la liste ramene a la liste au retour`` |

**Un test par défaut corrigé**: les tests 1, 2, 8 portent exactement le parcours décrit
(« depuis X, retour → Y, avec la position conservée »), les tests 3 à 7 tiennent les appels de la
source, et le contrôle « aucune route écrite en direct » interdit la réapparition de la cause B3.

# Après correction — les trois défauts rejoués

| Cas | Entrée | Avant (1.0.0+287) | Après (1.0.0+288) |
|---|---|---|---|
| `D1` | Réglages › Kanban → retour | **Réglages** (même position) | **liste des conversations** |
| `D2` | Réglages › Cron → retour | **Réglages** | **liste des conversations** |
| `D3` | liste › rangée « Quill » → retour | **lanceur** (app fermée) | **liste des conversations** |
| `D4` | liste › Nouveau bot → retour | **le fil** | **liste des conversations** |
| `D5` | Réglages (feuille) › « Terminé » | **le fil** | **liste des conversations** |
| `D6` | Réglages › Modèle → retour | **le fil** | **Réglages**, à la même position |

Les six cas passent au vert dans la table des essais ci-dessus (campagne `results.jsonl`,
relevés de 03:11–03:2x sur l'APK corrigé). `D1`/`D2` portent la mention « position non
comparable » : la ligne « Kanban » est une **ligne des Réglages** avant et l'**onglet de la liste**
après ; la destination est vérifiée par le texte, la position ne peut pas l'être.

## Deux volets — les mêmes chemins, la liste à gauche

Géométrie 800×1200 à 160 dpi = **800 dp** de large : les deux panneaux sont dessinés
(`captures-cles/Z0-deux-volets.png`). Six cas rejoués sur l'APK corrigé (`cases-deux.json`) :

| Cas | Entrée | Ce qui doit arriver | Verdict |
|---|---|---|---|
| `Z1` | Réglages › Kanban → retour système | la **liste** reste, le tableau se referme | ✅ |
| `Z2` | liste › rangée « Quill » → retour système | la liste est toujours là, la conversation s'ouvre à droite | ✅ (la liste **ne disparaît pas** : c'est le cas deux volets, la signature du parent se retrouve dans celle de l'enfant) |
| `Z3` | Réglages › Modèle → retour système | les **Réglages**, à la même position `(62, 303, 570, 323)` avant et après | ✅ |
| `Z4` | onglet **Kanban** de la colonne → retour système | la liste revient dans son cadre | ✅ |
| `Z5` | onglet **Kanban** → **geste du bord** | la liste revient — le geste du bord marche aussi en deux volets | ✅ |
| `Z6` | Réglages › Cron → retour système | la liste | ✅ |

Les cas `E2`–`E7` de la campagne « bords » (une colonne) couvrent le geste depuis Réglages, Kanban,
Cron, Voix, un enfant de Voix, le bord **droit**, et depuis la racine : tous ✅.

# Ce qui reste, et pourquoi ce n'est pas deviné

1. **Où le retour d'un onglet de section doit-il retomber, à pile vide ?** L'audit applique **la
   liste des conversations** — c'est ce que le propriétaire a écrit (« aux Réglages au lieu de la
   liste des conversations »), et c'est ce que le contrôle propre de Kanban faisait déjà. L'autre
   choix possible est de retomber directement sur **le fil** (l'accueil réel de l'app en une
   colonne) : dans ce cas, `rootRouteBehind` et le repli de `cronPane`/`kanbanPane` changent d'une
   ligne, et les tests 1/2/3 changent de valeur attendue. **Non deviné, laissé au choix.**
2. **Le bouton « Terminé » des Réglages** est le **seul** contrôle de retour de l'app qui agit sur
   la feuille : partout ailleurs, c'est le chevron « Retour ». Le corriger comme ici (dépiler) est
   le minimum ; en faire un chevron « Retour » serait plus cohérent, mais c'est un choix de
   libellé, pas de comportement.
3. **C1 (état vide)** : corriger demande de déplacer ces `navTo` dans un `LaunchedEffect` pour
   chaque branche. Aucune capture ne le justifie aujourd'hui ; à faire sans consigne contraire.
4. **Routes non ouvertes par un geste dans cette passe** (leur contrat de retour est lu dans la
   source et listé dans l'inventaire) :
   - `CAMERA` — l'émulateur n'a pas de caméra ;
   - `SUBAGENT` — demande qu'un tour d'agent produise un appel d'outil vivant ;
   - `PATCH`, `SELECT_TEXT`, `PDF`, `PHOTO` — demandent un tour réel contenant un diff, une
     sélection, un PDF ou une image ;
   - `BOT_FACE`, `BOT_SCHEDULES`, `BOT_VOICE`, `SKILL_EDITOR`, `FILE_EDITOR`, `CONTEXT_EDITOR`,
     `LOGS`, `SETUP` — atteignables par un **appui long** sur une tuile, ou par la sélection d'un
     fichier / d'une compétence ; l'ancrage du menu a été **vérifié** (appui long sur la tuile
     « Onyx » → « Modifier le bot… », « Changer de visage… », « Horaires… ») mais chacun de ces
     écrans n'a pas été poussé puis dépilé un par un dans le temps de cette passe.
   Le tableau des routes dit, pour chacune, si elle a été **essayée à l'appareil** ou seulement
   **relevée dans la source**.

# Captures

Elles sont dans `qa/route-audit/shots/` (235 fichiers), nommées `<cas>-1-parent`, `-3-avant`,
`-4-enfant`, `-5-apres` : la page parente, l'écran quitté juste avant l'appui, la sous-page
ouverte, et l'écran **après le retour**. Les captures des défauts se lisent dans cet ordre :

| Fichier | Ce qu'il montre |
|---|---|
| `17-SETTINGS-Kanban-4-enfant.png` / `-5-apres.png` | B1 : le tableau Kanban, puis **les Réglages** au retour |
| `16-SETTINGS-Cron-4-enfant.png` / `-5-apres.png` | B1 : idem pour Cron |
| `L3-LISTE-RangeeBotVersFil-4-enfant.png` / `-5-apres.png` | B2 : le fil de Quill, puis **le lanceur** |
| `L5-LISTE-NouveauBot-4-enfant.png` / `-5-apres.png` | B3 : la liste des bots, puis **le fil** |
| `D1-SETTINGS-Kanban-Retour-5-apres.png` | après correction : **la liste des conversations** |
| `D3-LISTE-RangeeBotVersFil-Retour-5-apres.png` | après correction : la liste, app toujours ouverte |
| `D4-LISTE-NouveauBot-Retour-5-apres.png` | après correction : la liste |
| `F1-MENU-TUILE-RetourSysteme-4-enfant.png` / `-5-apres.png` | le menu ancré d'une tuile, et sa fermeture par le retour |
| `E2-BORD-Kanban-5-apres.png` | le geste **du bord** depuis le tableau : la liste, pas le lanceur |

# Restes de l'audit

- Les relevés bruts sont dans `results-avant.jsonl` (47 essais sur l'APK 1.0.0+287) et
  `results.jsonl` (essais sur l'APK 1.0.0+288, une colonne puis deux volets) ; les journaux
  d'exécution dans `run-*.log`.
- Un essai qui n'a pas trouvé son libellé **au bout de ses balayages** est marqué
  `ÉCHEC-ENTRÉE` — c'est un **défaut de la boîte à outils**, pas de l'app : les quatre lignes de
  Réglages perdues ainsi ont été rejouées et passent (`R1`–`R4`).
- Toute route que la table ne marque pas « essayée » n'est **pas** annoncée comme vérifiée.
