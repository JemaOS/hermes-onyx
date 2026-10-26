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
