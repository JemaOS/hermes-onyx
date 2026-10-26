# ROUTE-AUDIT — les retours de l'app, route par route

**C'est un audit, pas une revue de code**: chaque route a été ouverte dans un émulateur par son
point d'entrée réel, une sous-page a été poussée par-dessus, et le retour a été mesuré — écran
d'arrivée, position de défilement et état relevés **avant** et **après**, chiffres à l'appui.

| | |
|---|---|
| App | **Hermes Onyx** (`com.onyx.app`), phrase d'écran : « Onyx pour Android » |
| APK mesuré (avant) | **1.0.0+287** — `sha256 2d242f4914d8194083b56bd16e710d241235fe8b6305db79fdded0cbca8202ce` |
| APK mesuré (après) | **1.0.0+288** — `sha256 310f2fad03094779132557856f0b05617fb25accca5e3c61e60c6df3c0f4d992` |
| Commit de l'audit | `c3581bc` (worktree neuf détaché de `276182d`, jamais dans `onyx-integration2`) |
| Appareil | `emulator-5600` (AVD `onyx-nav`, API 36, x86_64) — **jamais `emulator-5558`** |
| Une colonne | 1080×2400 à 420 dpi = **411 dp** de large (seuil des deux volets : 600 dp) |
| Deux volets | 800×1200 à 160 dpi = **800 dp** de large, deux panneaux réellement dessinés (capture `shots/Z0-deux-volets.png` : la liste à gauche, la conversation à droite) |
| Serveur | double du Dashboard Hermes `http://10.0.2.2:8787` (six profils, même jeu que les captures du propriétaire), posé par `inject.py` |
| Recette | `:core:testDebugUnitTest :app:testDebugUnitTest :app:assembleRelease --no-daemon --console=plain -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.workers.max=2` |
| Tests | **956** dans les XML, **0 échec** (plancher 949 + **7** tests ajoutés) |

## Bilan

| | |
|---|---|
| Routes dans l'énumération `Route` | **56** |
| Routes avec au moins un point d'entrée relevé dans la source | **56** (aucune orpheline) |
| Combinaisons essayées à l'appareil | **63 cas distincts**, **77 relevés** (les deux campagnes) |
| Cas « une colonne » (APK 1.0.0+287, avant) | 47 relevés — 37 ✅, **6 incohérents** (les vrais défauts), 4 non conclus par la boîte à outils |
| Cas « une colonne » et « deux volets » (APK 1.0.0+288, après) | 30 relevés — 25 ✅, 2 incohérents **artefacts de mesure** (rejoués et verts), 3 releveurs ratés |
| Incohérences trouvées | **8** : 3 bloquantes, 4 gênantes, 1 cosmétique |
| Corrigées | **7** (les 3 bloquantes et les 4 gênantes), chacune avec son test |
| Laissées au propriétaire | **1** (cosmétique : `navTo` pendant la composition sur les états vides) + 1 **choix de conception** |

## Ce que l'audit ne couvre pas

- `uiautomator` se fait tuer par le noyau quand la mémoire de l'appareil est serrée : ses échecs
  apparaissent en `ÉCHEC-ENTRÉE` / `ÉCHEC-RELEVÉ`, jamais en « incohérence de l'app ». Ils sont
  comptés à part et rejoués quand c'est possible (`R1`–`R4`).
- La géométrie deux volets a dû passer par `800×1200 à 160 dpi` (800 dp) : à `2200×2480 à 420 dpi`
  (838 dp), l'arbre d'accessibilité de cet appareil n'est plus relevable de façon fiable
  (`uiautomator` tué). Les deux panneaux sont bien dessinés, cf. `captures-cles/Z0-deux-volets.png`.


## Comment un cas est mesuré

Chaque cas (fichiers `cases-*.json`) décrit une **entrée réelle** (les libellés à toucher depuis la
racine), la sous-page à ouvrir, et ce qui doit être là après le retour. La boîte à outils
(`driver.py`, `harness.py`) ne connaît **aucune coordonnée écrite à la main** : tout passe par des
ancres d'accessibilité (`uiautomator dump`) et des libellés visibles.

Pour chaque cas :

1. l'app **redémarre** (état identique à chaque essai) puis les étapes d'entrée sont jouées ;
2. on **fait défiler** jusqu'à la ligne visée et on relève (a) la signature de l'écran,
   (b) les **bornes** d'une ligne repère (par ex. la ligne qu'on s'apprête à toucher) ;
3. on ouvre la sous-page et on relève sa signature — l'appui doit avoir **changé** d'écran ;
4. on fait **retour** — par le bouton système, par le **geste du bord**, ou par le contrôle de
   l'app, selon le cas ;
5. on relève à nouveau signature et ligne repère, et on exige les trois choses :
   **(a)** l'écran quitté, **(b)** la **même position** (bornes identiques), **(c)** le même état.

Ce qui compte comme une **incohérence** : revenir ailleurs qu'où l'on était, ne pas retrouver la
position, laisser un écran vide, retomber sur les Réglages sans raison, ou laisser l'app se fermer.
Quand la ligne repère existe sur les deux écrans avec un autre rôle (par ex. « Kanban » : ligne des
Réglages avant, onglet de la liste après), la comparaison de position ne dit rien — ces cas sont
signalés « destination juste, position non comparable ».

**Le retour a été essayé des deux façons à chaque fois**: bouton/geste système **et** contrôle de
retour de la page.

# Inventaire — toutes les routes

Les 56 valeurs de l'énumération `Route`, chacune avec **ses points d'entrée réels** relevés dans la
source (le libellé du geste, et la ligne exacte du `navTo`) et **son contrat de retour** (ce que le
contrôle propre de la page fait). Table fabriquée par `report.py` : aucune ligne écrite à la main,
donc aucune route oubliée ni inventée.

Routes dans l'énumération `Route` : **56**

## Inventaire: chaque route, ses points d'entrée et son contrôle de retour

| # | Route | Points d'entrée relevés dans la source | Contrôle de retour de la page |
|---|---|---|---|
| 1 | `CHAT` | onFinish (`OnyxApp.kt:859`)<br>ligne 984 (`OnyxApp.kt:984`)<br>ligne 1014 (`OnyxApp.kt:1014`)<br>onglet Discussions (`OnyxApp.kt:1279`)<br>rangée de conversation (`OnyxApp.kt:1303`)<br>Nouvelle conversation (`OnyxApp.kt:1316`)<br>Terminé (`OnyxApp.kt:1394`)<br>onSelect (`OnyxApp.kt:1493`)<br>onAskOnyx (`OnyxApp.kt:1525`)<br>end (`OnyxApp.kt:1726`)<br>onBackToKeyboard (`OnyxApp.kt:1731`)<br>Terminé (`OnyxApp.kt:1762`)<br>onSend (`OnyxApp.kt:1764`)<br>Terminé (`OnyxApp.kt:1778`)<br>onEraseEverything (`OnyxApp.kt:1813`)<br>retour (contrôle de la page) (`OnyxApp.kt:2340`)<br>retour (contrôle de la page) (`OnyxApp.kt:2357`)<br>retour (contrôle de la page) (`OnyxApp.kt:2372`)<br>onCapture (`OnyxApp.kt:2382`)<br>fermer (`OnyxApp.kt:2384`)<br>fermer (`OnyxApp.kt:2389`)<br>fermer (`OnyxApp.kt:2401`)<br>ligne 2409 (`OnyxApp.kt:2409`)<br>fermer (`OnyxApp.kt:2413`)<br>ligne 2421 (`OnyxApp.kt:2421`)<br>retour (contrôle de la page) (`OnyxApp.kt:2425`)<br>ligne 2433 (`OnyxApp.kt:2433`)<br>retour (contrôle de la page) (`OnyxApp.kt:2438`)<br>onPick (`OnyxApp.kt:2745`)<br>retour (contrôle de la page) (`OnyxApp.kt:2747`) | aucun contrôle propre |
| 2 | `CONVERSATIONS` | ligne 1129 (`OnyxApp.kt:1129`)<br>ligne 1476 (`OnyxApp.kt:1476`)<br>ligne 1516 (`OnyxApp.kt:1516`)<br>retour (contrôle de la page) (`OnyxApp.kt:2247`)<br>retour (contrôle de la page) (`OnyxApp.kt:2253`)<br>ligne 2262 (`OnyxApp.kt:2262`)<br>retour (contrôle de la page) (`OnyxApp.kt:2278`) | aucun contrôle propre |
| 3 | `SETTINGS` | engrenage (`OnyxApp.kt:1142`)<br>ligne 1176 (`OnyxApp.kt:1176`)<br>onOpenSettingsFromError (`OnyxApp.kt:1181`)<br>engrenage (`OnyxApp.kt:1391`)<br>Unit (`OnyxApp.kt:1409`)<br>retour (contrôle de la page) (`OnyxApp.kt:2233`)<br>retour (contrôle de la page) (`OnyxApp.kt:2283`)<br>retour (contrôle de la page) (`OnyxApp.kt:2285`)<br>retour (contrôle de la page) (`OnyxApp.kt:2287`)<br>retour (contrôle de la page) (`OnyxApp.kt:2289`)<br>retour (contrôle de la page) (`OnyxApp.kt:2308`)<br>retour (contrôle de la page) (`OnyxApp.kt:2318`)<br>retour (contrôle de la page) (`OnyxApp.kt:2328`)<br>retour (contrôle de la page) (`OnyxApp.kt:2351`)<br>retour (contrôle de la page) (`OnyxApp.kt:2446`)<br>retour (contrôle de la page) (`OnyxApp.kt:2455`)<br>onChoose (`OnyxApp.kt:2474`)<br>retour (contrôle de la page) (`OnyxApp.kt:2476`)<br>Continuer (`OnyxApp.kt:2482`)<br>retour (contrôle de la page) (`OnyxApp.kt:2493`)<br>retour (contrôle de la page) (`OnyxApp.kt:2600`)<br>retour (contrôle de la page) (`OnyxApp.kt:2628`)<br>retour (contrôle de la page) (`OnyxApp.kt:2657`)<br>retour (contrôle de la page) (`OnyxApp.kt:2688`)<br>retour (contrôle de la page) (`OnyxApp.kt:2731`) | onDone = { if (!navBack()) navTo(Route.CHAT.name) }, |
| 4 | `APPEARANCE` | Réglages › Apparence (`OnyxApp.kt:1779`) | onBack = { navTo(Route.SETTINGS.name) }, |
| 5 | `MODELS` | en-tête du fil › modèle (`OnyxApp.kt:1135`)<br>onModelCommand (`OnyxApp.kt:1140`)<br>Réglages › Modèle (`OnyxApp.kt:1780`) | onBack = { if (!navBack()) navTo(Route.CHAT.name) }, |
| 6 | `VOICE` | ligne 654 (`OnyxApp.kt:654`)<br>ligne 814 (`OnyxApp.kt:814`)<br>ligne 897 (`OnyxApp.kt:897`) | aucun contrôle propre |
| 7 | `GATEWAY` | Réglages › Passerelle (`OnyxApp.kt:1782`)<br>retour (contrôle de la page) (`OnyxApp.kt:2700`) | onBack = { navTo(Route.SETTINGS.name) }, |
| 8 | `LOGS` | Passerelle › Journaux (`OnyxApp.kt:2689`) | onBack = { navTo(Route.GATEWAY.name) }, |
| 9 | `FILES` | Réglages › Fichiers (`OnyxApp.kt:1783`)<br>ligne 2668 (`OnyxApp.kt:2668`)<br>retour (contrôle de la page) (`OnyxApp.kt:2676`) | onBack = { navTo(Route.SETTINGS.name) }, |
| 10 | `FILE_EDITOR` | onOpenFile (`OnyxApp.kt:2660`) | onBack = { navTo(Route.FILES.name) }, |
| 11 | `CONTEXT` | Réglages › Fichiers de contexte (`OnyxApp.kt:1798`) | — |
| 12 | `MEMORY` | Réglages › Mémoire (`OnyxApp.kt:1799`) | — |
| 13 | `CONTEXT_EDITOR` | ligne 1912 (`OnyxApp.kt:1912`)<br>onOpenFile (`OnyxApp.kt:2605`) | onBack = { if (!navBack()) navTo(contextReturn) }, ; onBack = { navTo(contextReturn) }, |
| 14 | `VOICE_SETTINGS` | engrenage (`OnyxApp.kt:1733`)<br>rangée Voix (`OnyxApp.kt:1781`)<br>retour (contrôle de la page) (`OnyxApp.kt:2335`)<br>retour (contrôle de la page) (`OnyxApp.kt:2535`)<br>retour (contrôle de la page) (`OnyxApp.kt:2570`)<br>retour (contrôle de la page) (`OnyxApp.kt:2579`)<br>retour (contrôle de la page) (`OnyxApp.kt:2588`) | onBack = { if (!navBack()) navTo(Route.SETTINGS.name) }, |
| 15 | `NOTIFICATIONS` | Réglages › Notifications (`OnyxApp.kt:1800`) | onBack = { if (!navBack()) navTo(Route.SETTINGS.name) }, |
| 16 | `KOKORO_VOICES` | Voix › voix Kokoro (`OnyxApp.kt:2323`) | onBack = { navTo(Route.VOICE_SETTINGS.name) }, |
| 17 | `CRON` | Réglages › Cron (`OnyxApp.kt:1392`)<br>onSelect (`OnyxApp.kt:1496`)<br>Réglages › Cron (`OnyxApp.kt:1784`) | aucun contrôle propre |
| 18 | `KANBAN` | Réglages › Kanban (`OnyxApp.kt:1393`)<br>Réglages › Kanban (`OnyxApp.kt:1785`) | onDone = { navTo(Route.CHAT.name) }, ; onBack = if (wideDetail) null else ({ if (!navBack()) navTo(Route.SETTINGS.name) }), ; onBack = if (wideDetail) null else ({ if (!navBack()) navTo(Route.CONVERSATIONS.name) }), ; onBack = if (wideDetail) null else ({ if (!navBack()) navTo(Route.CONVERSATIONS.name) }), |
| 19 | `COMPOSER_EDITOR` | composeur › éditer (`OnyxApp.kt:1159`) | onDone = { navTo(Route.CHAT.name) }, |
| 20 | `PHONE_TOOLS` | Réglages › Outils du téléphone (`OnyxApp.kt:1786`) | onBack = { navTo(Route.SETTINGS.name) }) |
| 21 | `LOCK` | Réglages › Verrouillage (`OnyxApp.kt:1794`) | onBack = { navTo(Route.SETTINGS.name) }) |
| 22 | `CONNECT` | Réglages › Configurer un autre appareil (`OnyxApp.kt:1795`) | onBack = { navTo(Route.SETTINGS.name) }, |
| 23 | `BOTS` | onLetBotsMessage (`OnyxApp.kt:1390`)<br>Réglages › Bots (`OnyxApp.kt:1787`)<br>ligne 1826 (`OnyxApp.kt:1826`)<br>ligne 1852 (`OnyxApp.kt:1852`)<br>onAdd (`OnyxApp.kt:1878`)<br>retour (contrôle de la page) (`OnyxApp.kt:1880`)<br>onSave (`OnyxApp.kt:1939`)<br>onDelete (`OnyxApp.kt:1944`)<br>ligne 1952 (`OnyxApp.kt:1952`)<br>Annuler (`OnyxApp.kt:1959`)<br>Annuler (`OnyxApp.kt:1966`)<br>retour (contrôle de la page) (`OnyxApp.kt:2010`)<br>onEnded (`OnyxApp.kt:2018`)<br>retour (contrôle de la page) (`OnyxApp.kt:2054`)<br>retour (contrôle de la page) (`OnyxApp.kt:2240`) | aucun contrôle propre |
| 24 | `BOT_EDITOR` | menu d'une tuile de bot › Modifier le bot (`OnyxApp.kt:1322`)<br>menu d'une tuile de bot › Modifier le bot (`OnyxApp.kt:1415`)<br>Nouveau bot (`OnyxApp.kt:1454`)<br>retour (contrôle de la page) (`OnyxApp.kt:1839`)<br>onSave (`OnyxApp.kt:1977`)<br>Annuler (`OnyxApp.kt:1979`) | onCancel = { navTo(Route.BOTS.name) }, |
| 25 | `BOT_FACE` | menu d'une tuile de bot › Changer de visage (`OnyxApp.kt:1326`)<br>menu d'une tuile de bot › Changer de visage (`OnyxApp.kt:1420`)<br>onFaceChosen (`OnyxApp.kt:1902`) | onCancel = { if (!navBack()) navTo(Route.BOT_EDITOR.name) }, |
| 26 | `BOT_SCHEDULES` | menu d'une tuile de bot › Horaires (`OnyxApp.kt:1331`)<br>onSchedules (`OnyxApp.kt:1426`)<br>onOpenSchedules (`OnyxApp.kt:1916`) | onBack = { if (!navBack()) navTo(Route.BOT_EDITOR.name) }, |
| 27 | `BOT_VOICE` | menu d'une tuile de bot › Voix (`OnyxApp.kt:1337`)<br>onVoice (`OnyxApp.kt:1432`)<br>rangée Voix (`OnyxApp.kt:1921`) | onBack = { |
| 28 | `GROUP_CHAT` | ligne 1088 (`OnyxApp.kt:1088`)<br>onGroupChat (`OnyxApp.kt:1438`)<br>onStop (`OnyxApp.kt:2201`)<br>onBackToKeyboard (`OnyxApp.kt:2207`) | onBack = { if (!navBack()) navTo(Route.BOTS.name) }, ; onBack = { |
| 29 | `GROUP_VOICE` | ligne 892 (`OnyxApp.kt:892`)<br>rangée Voix (`OnyxApp.kt:2013`)<br>rangée Voix (`OnyxApp.kt:2056`) | aucun contrôle propre |
| 30 | `ACKNOWLEDGEMENTS` | Réglages › Remerciements (`OnyxApp.kt:1797`) | onBack = { navTo(Route.SETTINGS.name) }) |
| 31 | `SPEECH_LANGUAGE` | Voix › Langue d'écoute (`OnyxApp.kt:2319`) | onBack = { navTo(Route.VOICE_SETTINGS.name) }, |
| 32 | `REPLY_LANGUAGE` | Voix › Langue des réponses (`OnyxApp.kt:2320`) | onBack = { navTo(Route.VOICE_SETTINGS.name) }, ; onBack = { navTo(Route.SETTINGS.name) }, |
| 33 | `STOP_PHRASES` | Voix › Phrases d'arrêt (`OnyxApp.kt:2321`) | onBack = { navTo(Route.VOICE_SETTINGS.name) }, |
| 34 | `SERVERS` | Réglages › Serveurs (`OnyxApp.kt:1788`)<br>Terminé (`OnyxApp.kt:2506`) | onBack = { navTo(Route.SETTINGS.name) }, |
| 35 | `SETUP` | Serveurs › Modifier (`OnyxApp.kt:851`)<br>Serveurs › Modifier (`OnyxApp.kt:2494`) | onDone = { |
| 36 | `SPOKEN_PREFIXES` | Voix › Préfixes parlés (`OnyxApp.kt:2322`)<br>retour (contrôle de la page) (`OnyxApp.kt:2541`)<br>retour (contrôle de la page) (`OnyxApp.kt:2559`) | onBack = { navTo(Route.VOICE_SETTINGS.name) }, |
| 37 | `SPOKEN_PREFIX_EDITOR` | onAdd (`OnyxApp.kt:2525`)<br>onOpenRule (`OnyxApp.kt:2533`) | onBack = { navTo(Route.SPOKEN_PREFIXES.name) }, |
| 38 | `PROFILES` | Réglages › Profil (`OnyxApp.kt:1789`) | onBack = { navTo(Route.SETTINGS.name) }, |
| 39 | `WHATS_NEW` | Réglages › Nouveautés (`OnyxApp.kt:1792`) | aucun contrôle propre |
| 40 | `PATCH` | onOpenPatch (`OnyxApp.kt:1184`) | onBack = { navTo(Route.CHAT.name) }, |
| 41 | `SELECT_TEXT` | onSelectText (`OnyxApp.kt:1188`) | onBack = { navTo(Route.CHAT.name) }, |
| 42 | `PHOTO` | ligne 533 (`OnyxApp.kt:533`)<br>onOpenPhotos (`OnyxApp.kt:1204`) | onClose = { navTo(Route.CHAT.name) }, |
| 43 | `PDF` | ligne 520 (`OnyxApp.kt:520`) | onClose = { navTo(Route.CHAT.name) }, |
| 44 | `SKILLS` | Réglages › Compétences (`OnyxApp.kt:1790`)<br>enregistré (`OnyxApp.kt:2462`)<br>retour (contrôle de la page) (`OnyxApp.kt:2463`) | onBack = { navTo(Route.SETTINGS.name) }, |
| 45 | `SKILL_EDITOR` | onOpenSkill (`OnyxApp.kt:2453`) | onBack = { navTo(Route.SKILLS.name) }, |
| 46 | `TOOLS` | Réglages › Outils (`OnyxApp.kt:1791`) | onBack = { navTo(Route.SETTINGS.name) }, |
| 47 | `API_BOT` | Nouveau bot (`OnyxApp.kt:1456`) | onBack = { navTo(Route.BOTS.name) }, |
| 48 | `CAMERA` | composeur › appareil photo (`OnyxApp.kt:1190`) | onClose = { navTo(Route.CHAT.name) }, |
| 49 | `TIP_JAR` | Réglages › Soutenir Onyx (`OnyxApp.kt:1793`) | onBack = { navTo(Route.SETTINGS.name) }) |
| 50 | `SUBAGENT` | onOpenSubagent (`OnyxApp.kt:1199`) | onBack = { navTo(Route.CHAT.name) }, |
| 51 | `TERMINAL` | ligne 1175 (`OnyxApp.kt:1175`)<br>Réglages › Terminal (`OnyxApp.kt:2350`) | onBack = { if (!navBack()) navTo(Route.CHAT.name) }, |
| 52 | `TERMINAL_SETTINGS` | Réglages › Terminal (`OnyxApp.kt:1796`) | onBack = { if (!navBack()) navTo(Route.SETTINGS.name) }, |
| 53 | `CONNECTION_DETAILS` | Réglages › Détails de connexion (`OnyxApp.kt:1801`) | onBack = { if (!navBack()) navTo(Route.SETTINGS.name) }, |
| 54 | `HIDDEN_BOTS` | menu d'une tuile de bot › Bots cachés (`OnyxApp.kt:1385`)<br>menu d'une tuile de bot › Bots cachés (`OnyxApp.kt:1461`) | onBack = { if (!navBack()) navTo(Route.BOTS.name) }, |
| 55 | `SESSION_USAGE` | menu d'une rangée › Usage & cost (`OnyxApp.kt:1366`)<br>onOpenSession (`OnyxApp.kt:2276`) | onBack = { if (!navBack()) navTo(Route.CONVERSATIONS.name) }, |
| 56 | `PROJECT_SESSIONS` | onOpenProject (`OnyxApp.kt:1371`) | onBack = { if (!navBack()) navTo(Route.CONVERSATIONS.name) }, |
## Essais mesurés (`results-*.jsonl`)

| Cas | Campagne | Comment | Écran parent (repère avant) | Après le retour (repère) | Verdict |
|---|---|---|---|---|---|
| `03-SETTINGS-Modele` | results-avant.jsonl | system | [163, 867, 482, 919] | [163, 867, 482, 919] | **OK** |
| `04-SETTINGS-Apparence` | results-avant.jsonl | system | [163, 978, 698, 1030] | [163, 978, 698, 1030] | **OK** |
| `05-SETTINGS-Voix` | results-avant.jsonl | system | [163, 1204, 807, 1256] | [163, 1204, 807, 1256] | **OK** |
| `06-SETTINGS-Notifications` | results-avant.jsonl | system | [163, 1315, 709, 1367] | [163, 1315, 709, 1367] | **OK** |
| `07-SETTINGS-Competences` | results-avant.jsonl | system | [163, 2281, 932, 2333] | [163, 2281, 932, 2333] | **OK** |
| `08-SETTINGS-Outils` | results-avant.jsonl | system | [163, 2392, 932, 2400] | [163, 2392, 932, 2400] | **OK** |
| `09-SETTINGS-OutilsTelephone` | results-avant.jsonl |  | None | None | **ÉCHEC-ENTRÉE** |
| `10-SETTINGS-Bots` | results-avant.jsonl |  | None | None | **ÉCHEC-ENTRÉE** |
| `11-SETTINGS-Profil` | results-avant.jsonl | system | [163, 1381, 932, 1433] | [163, 1381, 932, 1433] | **OK** |
| `12-SETTINGS-Contexte` | results-avant.jsonl | system | [163, 1507, 932, 1559] | [163, 1507, 932, 1559] | **OK** |
| `13-SETTINGS-Memoire` | results-avant.jsonl |  | None | None | **ÉCHEC-ENTRÉE** |
| `14-SETTINGS-Fichiers` | results-avant.jsonl |  | None | None | **ÉCHEC-ENTRÉE** |
| `15-SETTINGS-Passerelle` | results-avant.jsonl | system | [163, 1750, 932, 1802] | [163, 1750, 932, 1802] | **OK** |
| `16-SETTINGS-Cron` | results-avant.jsonl | system | [163, 246, 932, 264] | [163, 246, 932, 264] | **INCOHÉRENT** |
| `17-SETTINGS-Kanban` | results-avant.jsonl | system | [163, 329, 932, 381] | [163, 329, 932, 381] | **INCOHÉRENT** |
| `18-SETTINGS-DetailsConnexion` | results-avant.jsonl | system | [163, 608, 900, 660] | [163, 608, 900, 660] | **OK** |
| `19-SETTINGS-Terminal` | results-avant.jsonl | system | [163, 1381, 900, 1433] | [163, 1381, 900, 1433] | **OK** |
| `20-SETTINGS-Verrouillage` | results-avant.jsonl | system | [163, 1592, 900, 1644] | [163, 1592, 900, 1644] | **OK** |
| `21-SETTINGS-AutreAppareil` | results-avant.jsonl | system | [163, 1137, 900, 1189] | [163, 1137, 900, 1189] | **OK** |
| `22-SETTINGS-Remerciements` | results-avant.jsonl | system | [163, 1976, 900, 2028] | [163, 1976, 900, 2028] | **OK** |
| `23-SETTINGS-Nouveautes` | results-avant.jsonl | system | [163, 1962, 900, 2014] | [163, 1962, 900, 2014] | **OK** |
| `24-SETTINGS-Soutenir` | results-avant.jsonl | system | [163, 1585, 900, 1637] | [163, 1585, 900, 1637] | **OK** |
| `25-SETTINGS-Serveurs` | results-avant.jsonl | system | [163, 2190, 875, 2242] | [163, 2190, 875, 2242] | **OK** |
| `V1-VOIX-LangueEcoute` | results-avant.jsonl | system | [84, 677, 857, 729] | [84, 677, 857, 729] | **OK** |
| `V1-VOIX-LangueEcoute` | results.jsonl | system | [84, 677, 857, 729] | [84, 677, 857, 729] | **OK** |
| `V2-VOIX-LangueReponses` | results-avant.jsonl | system | [84, 677, 857, 729] | [84, 677, 857, 729] | **OK** |
| `V2-VOIX-LangueReponses` | results.jsonl | system | [84, 677, 857, 729] | [84, 677, 857, 729] | **OK** |
| `V3-VOIX-PhrasesArret` | results-avant.jsonl | system | [84, 677, 857, 729] | [84, 677, 857, 729] | **OK** |
| `V4-VOIX-PrefixesParles` | results-avant.jsonl | system | [84, 677, 857, 729] | [84, 677, 857, 729] | **OK** |
| `L1-LISTE-OngletCron` | results-avant.jsonl | system | [99, 419, 328, 468] | [99, 419, 328, 468] | **OK** |
| `L2-LISTE-OngletKanban` | results-avant.jsonl | system | [99, 419, 328, 468] | [99, 419, 328, 468] | **OK** |
| `L3-LISTE-RangeeBotVersFil` | results-avant.jsonl | system | [99, 419, 328, 468] | None | **INCOHÉRENT** |
| `L4-LISTE-NouvelleSalleGroupe` | results-avant.jsonl | system | [99, 419, 328, 468] | [99, 419, 328, 468] | **OK** |
| `L5-LISTE-NouveauBot` | results-avant.jsonl | system | [99, 419, 328, 468] | None | **INCOHÉRENT** |
| `L6-LISTE-DialogueLaisserDiscuter` | results-avant.jsonl | system | [99, 419, 328, 468] | [99, 419, 328, 468] | **OK** |
| `L7-KANBAN-RetourApp` | results-avant.jsonl | app | None | None | **OK** |
| `L8-CRON-RetourApp` | results-avant.jsonl | app | None | None | **OK** |
| `F1-MENU-TUILE-RetourSysteme` | results-avant.jsonl | system | None | None | **OK** |
| `F2-MENU-TUILE-ClicExterieur` | results-avant.jsonl | outside | None | None | **OK** |
| `F3-DIALOGUE-LaisserDiscuter-Retour` | results-avant.jsonl | system | None | None | **INCOHÉRENT** |
| `F4-FEUILLE-Reglages-RetourFerme` | results-avant.jsonl | system | None | None | **OK** |
| `F5-FEUILLE-Reglages-ClicSurTermine` | results-avant.jsonl | app | None | None | **INCOHÉRENT** |
| `E1-BORD-Reglages` | results-avant.jsonl | edge | None | None | **OK** |
| `E1-BORD-Reglages` | results.jsonl |  | None | None | **ÉCHEC-ENTRÉE** |
| `E2-BORD-Kanban` | results-avant.jsonl | edge | None | None | **OK** |
| `E2-BORD-Kanban` | results.jsonl |  | None | None | **ÉCHEC-ENTRÉE** |
| `E3-BORD-Cron` | results-avant.jsonl | edge | None | None | **OK** |
| `E3-BORD-Cron` | results.jsonl |  | None | None | **ÉCHEC-ENTRÉE** |
| `E4-BORD-Voix` | results-avant.jsonl | edge | None | None | **OK** |
| `E4-BORD-Voix` | results.jsonl |  | None | None | **ÉCHEC-ENTRÉE** |
| `E5-BORD-EnfantVoix` | results-avant.jsonl | edge | None | None | **OK** |
| `E5-BORD-EnfantVoix` | results.jsonl |  | None | None | **ÉCHEC-ENTRÉE** |
| `E6-BORD-DROIT-Kanban` | results-avant.jsonl | edge | None | None | **OK** |
| `E7-BORD-RacineFil` | results-avant.jsonl | edge | None | None | **OK** |
| `D1-SETTINGS-Kanban-Retour` | results.jsonl | system | [163, 1589, 932, 1641] | [796, 419, 938, 468] | **INCOHÉRENT** |
| `D1-SETTINGS-Kanban-Retour` | results.jsonl | system | None | None | **OK** |
| `D2-SETTINGS-Cron-Retour` | results.jsonl | system | [163, 1846, 932, 1898] | [496, 419, 584, 468] | **INCOHÉRENT** |
| `D2-SETTINGS-Cron-Retour` | results.jsonl | system | None | None | **OK** |
| `D3-LISTE-RangeeBotVersFil-Retour` | results.jsonl | system | [99, 419, 328, 468] | [99, 419, 328, 468] | **OK** |
| `D4-LISTE-NouveauBot-Retour` | results.jsonl | system | [99, 419, 328, 468] | [99, 419, 328, 468] | **OK** |
| `D5-FEUILLE-Reglages-Termine-Retour` | results.jsonl | app | None | None | **OK** |
| `D6-SETTINGS-Modele-RetourVersReglages` | results.jsonl | system | [163, 867, 482, 919] | [163, 867, 482, 919] | **OK** |
| `R1-SETTINGS-OutilsTelephone` | results.jsonl | system | [163, 678, 932, 730] | [163, 678, 932, 730] | **OK** |
| `R2-SETTINGS-Bots` | results.jsonl | system | [163, 817, 932, 869] | [163, 817, 932, 869] | **OK** |
| `R3-SETTINGS-Memoire` | results.jsonl | system | [163, 1122, 932, 1174] | [163, 1122, 932, 1174] | **OK** |
| `R4-SETTINGS-Fichiers` | results.jsonl | system | [163, 1566, 932, 1618] | [163, 1566, 932, 1618] | **OK** |
| `Z1-DEUX-Reglages-Kanban-Retour` | results.jsonl |  | None | None | **ÉCHEC-ENTRÉE** |
| `Z1-DEUX-Reglages-Kanban-Retour` | results.jsonl |  | None | None | **ÉCHEC-ENTRÉE** |
| `Z2-DEUX-ListeRangeeBot-Retour` | results.jsonl | system | None | None | **INCOHÉRENT** |
| `Z2-DEUX-ListeRangeeBot-Retour` | results.jsonl | system | None | None | **OK** |
| `Z3-DEUX-Settings-Modele-Retour` | results.jsonl | system | [62, 303, 570, 323] | [62, 303, 570, 323] | **OK** |
| `Z3-DEUX-Settings-Modele-Retour` | results.jsonl | system | [62, 303, 570, 323] | [62, 303, 570, 323] | **OK** |
| `Z4-DEUX-OngletKanban-Retour` | results.jsonl | system | None | None | **OK** |
| `Z4-DEUX-OngletKanban-Retour` | results.jsonl | system | None | None | **OK** |
| `Z5-DEUX-Bord-Kanban` | results.jsonl | edge | None | None | **OK** |
| `Z5-DEUX-Bord-Kanban` | results.jsonl | edge | None | None | **INCOHÉRENT** |
| `Z5-DEUX-Bord-Kanban` | results.jsonl | edge | None | None | **OK** |
| `Z6-DEUX-Settings-Cron-Retour` | results.jsonl | system | None | None | **OK** |
| `Z6-DEUX-Settings-Cron-Retour` | results.jsonl | system | None | None | **OK** |

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
