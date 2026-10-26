# Écran déplié: volet repliable, largeurs bornées, cartes non écrasées

Preuves de la demande du propriétaire (capture `img_aebc231d7d5a.jpg`) : « c'est pas du tout
optimisé pour un écran Honor Magic V3 déplié, je peux même pas réduire le volet de gauche si je
veux ; soit cohérent et optimisé ».

## Ce que montrent les captures

| Fichier | Contenu |
|---|---|
| `10-volet-deplie.png` | **Volet déplié**, même écran que la capture du propriétaire : tableau Kanban à gauche (343 dp, 40 %), fil scout à droite (515 dp). Le contrôle `sidebar.left` est à gauche de la barre « Kanban / Nouvelle carte ». Les cinq cartes portent leur « … » dans une réserve de 44 dp, séparée du titre et du résumé. |
| `20-volet-replie-pendant.png` | **La transition** : prise 120 ms après l'appui, le volet est encore à mi-course — le repli s'anime, il ne saute pas. |
| `21-volet-replie.png` | **Volet replié** : le fil prend toute la largeur, et le contrôle reste dans sa barre (libellé d'accessibilité « Déplier la liste »). |
| `30-volet-deplie-retour.png` | Le volet rouvert depuis la barre du détail : l'état revient, le libellé redevient « Replier la liste ». |
| `00-explore.png` | La route Conversations en deux panneaux (volet = liste, détail = fil), même contrôle dans la barre du volet. |
| `comparaison-avant-apres.png` | Côte à côte avec la capture du propriétaire. |
| `comparaison-cartes.png` | Zoom sur les rangées : avant, le « … » dans le texte ; après, la réserve fixe. |
| `measurements.json`, `geometry.json` | Les mesures lues dans l'arbre d'accessibilité (voir plus bas). |
| `prefs-persistees.json` | Les clés réellement écrites par l'app dans son `DataStore` (dont `sidebarCollapsed`). |

## Appareil et géométrie

- AVD **`onyx-fold`**, créé pour ce lot depuis le profil d'appareil **« 8" Fold-out »
  (id 63)** — un vrai pliable, avec sa charnière (`Goldfish hinge sensor0`, états `OPENED` /
  `CLOSED` dans `androidboot.qemu.device_state`) — image `android-36.1 google_apis x86_64`.
- Géométrie posée à celle de la capture : `wm size 2156x2344`, `wm density 402` →
  **858,1 × 932,9 dp**, soit les 2344×2156 px du Magic V3 déplié (la capture du propriétaire est
  ce même 2156×2344 réduit à 1177×1280, au facteur 0,5459 près).
- Origine de l'AVD (restaurée à la fin) : `wm size 2200x2480`, `wm density 420` —
  `ORIGINAL-VALUES.txt`.
- Langue de l'app forcée en français par locale **par application**
  (`cmd locale set-app-locales com.onyx.app --locales fr-FR`) pour comparer à la capture.

## APK

- **release**, `versionName 1.0.0+226`, `versionCode 226`.
- SHA-256 du fichier construit : `649ed0978e0880d118ba9bafbe3fb70c450349974f7a1c35e4a051de27e1285e`
- SHA-256 de l'APK **installé** (`adb exec-out cat $(pm path com.onyx.app) | sha256sum`) :
  **identique** — les captures viennent bien de ce build.

## Mesures (arbre d'accessibilité, `measurements.json` / `geometry.json`)

| Mesure | Valeur |
|---|---|
| Fenêtre | 855,1 × 932,9 dp (2156×2344 px @402) |
| Volet | **343,1 dp = 40,0 %** de la fenêtre (862 px) |
| Détail | **515,0 dp** (1294 px) |
| Contrôle, volet déplié | `content-desc = "Replier la liste"`, boîte 27,72 → 148,193 |
| Contrôle, volet replié | `content-desc = "Déplier la liste"`, boîte 17,67 → 138,188 |
| Rangées de cartes avec « … » | 5 |
| Rangées où le « … » chevauche du texte | **0** |
| Texte le plus à droite d'une rangée | 711 px ; la réserve commence à 731 px → **20 px (8 dp) libres** |
| Réserve du « … » | 111 px = **44 dp** (zone touchable) |
| Réglage persistant | `sidebarCollapsed` écrit par l'app dans `onyx_settings.preferences_pb` (bool protobuf, `08 00` = déplié) — l'état survit à l'arrêt de l'app |

Avant, sur la même géométrie : `kanbanPairLayout(858 dp)` donnait 42 % au fil et **497,6 dp
(58 %)** au volet — c'est le partage de la capture du propriétaire (683 px sur 1177).

## Comment rejouer

```bash
bash qa/20261025-foldable-panes/launch.sh            # détache l'AVD pliable (port 5582)
adb -s emulator-5582 shell wm size 2156x2344
adb -s emulator-5582 shell wm density 402
adb -s emulator-5582 root
adb -s emulator-5582 install -r -d app/build/outputs/apk/release/app-release.apk
bash qa/20261025-foldable-panes/start-dashboard.sh   # le double du Dashboard, port 8670
python3 qa/20261025-foldable-panes/inject.py         # serveur + conversations dans le DataStore
python3 qa/20261025-foldable-panes/capture.py all    # les cinq captures + les mesures
```

- `fake-dashboard.py` — double du Dashboard : `api/plugins/kanban/board` sert **les mêmes cartes
  que la capture du propriétaire** (mêmes titres, résumés, priorités P2, âges « il y a 3/4
  heures »), plus `auth/password-login`, la sonde `api/sessions`, le socket d'événements.
- `inject.py` — écrit `files/datastore/onyx_settings.preferences_pb` (protobuf `Preferences`
  manipulé à la main) et `files/conversations.json`. L'APK est une **release**, donc `run-as` est
  refusé : on passe par `adb root`, puis on rend les fichiers à l'uid du paquet.
- `capture.py` — pilote par ancres d'accessibilité (jamais de coordonnées en dur), et mesure le
  chevauchement du « … » en comparant les bornes du bouton et celles des textes de la rangée.

## Ce que ces captures ne montrent pas

1. **Le vrai Dashboard** : aucun identifiant du Dashboard de l'utilisateur n'était disponible (et
   les secrets Hermes sont hors périmètre). Le tableau vient donc d'un **double local**
   (`fake-dashboard.py`) qui sert exactement les cartes de la capture. La géométrie, le repli, le
   défilement et le rendu des rangées sont réels ; les **données** sont une maquette fidèle.
2. **La charnière** : l'AVD est un vrai pliable, mais le lot n'a pas rejoué la transition
   plié→déplié du système (redimensionnement à chaud, `onConfigurationChanged`, insets de
   pliure) : la géométrie est posée par `wm size` / `wm density`.
3. **Les autres routes** : le repli a été vérifié à l'écran sur Kanban (volet = tableau) et
   Conversations (volet = liste) ; Cron, Bots et Réglages portent le même contrôle dans leur
   barre (tests unitaires + lecture du source), mais n'ont pas été capturés ici.
4. **La police système 1,3×** : mesurée par les tests (`listDetailLayout` ne dépend pas de la
   police ; la largeur suit la densité), pas capturée sur l'appareil dans ce lot.
5. La rangée de capsules est **défilée** dans `10-volet-deplie.png` (« Planifié · Prêt · En cours ·
   Bloqué… ») : à 343 dp, « En cours » n'est atteignable qu'en la faisant glisser — c'est le
   défaut D8 déjà relevé sur `RESPONSIVE-QA.md`, pas une régression de ce lot.
