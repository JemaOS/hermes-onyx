# AUDIT — la présence d'un bot dans Wear, Auto, le widget et les tuiles

Audit de l'affirmation d'architecture : « un bot doit avoir une **présence unique** — même visage,
même voix, même modèle, même tour — dans le fil, la salle, la scène vocale, le widget, Wear et
Android Auto ».

| | |
|---|---|
| Worktree | `onyx-audit`, branche `lot/top5-audit` |
| Méthode | lecture du code seul ; aucune compilation, aucun test lancé, aucun fichier de code touché |
| Règle de preuve | chaque case du tableau porte le `fichier:ligne` qui **décide** ; ce qui n'existe pas est écrit « aucun » |

---

## 1. La référence : ce que fait le fil

Trois décisions, trois endroits, et c'est cette chaîne que les autres surfaces doivent rejoindre.

**Le visage.** Le roster est résolu **une fois** par `withSwiftFaces` (le choix local prime sur
`ui_meta.onyx.face`, puis `BotFace.faces` répartit les créatures sans doublon) :
`app/src/main/java/com/onyx/app/ui/bots/BotRosterFaces.kt:13-25`, appelé en
`app/src/main/java/com/onyx/app/ui/bots/BotsViewModel.kt:523`. La table qui en sort est exposée aux
écrans par `BotFaceResolver`, dont le contrat est `codeOf(profile)` —
`app/src/main/java/com/onyx/app/ui/bots/BotFaceResolver.kt:30-33` et `:45-48` ; le résolveur de
l'app est construit en `app/src/main/java/com/onyx/app/ui/OnyxApp.kt:590-595`. Le fil le lit par
`ThreadSpeaker.faceCode = bot.faceCode` (`app/src/main/java/com/onyx/app/ui/chat/components/ThreadSpeaker.kt:57`),
puis `ChatScreen` le passe au dessin (`app/src/main/java/com/onyx/app/ui/chat/ChatScreen.kt:1113-1114`).

**La voix.** Un seul lecteur applique le choix : `SpeechOutput.speak(text, languageTag, speaker)`
(`core/src/main/java/com/onyx/app/voice/SpeechOutput.kt:267-285`). `speaker` est le **nom de profil**
du bot ; il décide de la voix de l'appareil (`BotVoices.onDevice`,
`core/src/main/java/com/onyx/app/voice/BotVoices.kt:102-122`, appelé en `SpeechOutput.kt:234`), de la
voix Kokoro (`BotVoices.kokoroFor`, `core/src/main/java/com/onyx/app/voice/BotVoices.kt:87-93`,
appelé en `SpeechOutput.kt:364-365`) et de la vitesse (`BotVoices.speed`,
`core/src/main/java/com/onyx/app/voice/BotVoices.kt:153-157`, appelé en `SpeechOutput.kt:278`). Les
réglages entrent par `SpeechOutput.configure(..., botVoices = ...)`
(`core/src/main/java/com/onyx/app/voice/SpeechOutput.kt:132-150`), posés pour le fil en
`app/src/main/java/com/onyx/app/ui/OnyxApp.kt:1086-1095`. Le fil passe `speaker = openConversationBot?.name`
en `app/src/main/java/com/onyx/app/ui/OnyxApp.kt:1220` et la relecture d'une bulle en
`app/src/main/java/com/onyx/app/ui/chat/components/MessageRow.kt:339`.

**Le modèle.** Une seule valeur sert à la fois à **nommer** et à **envoyer** : `ChatUiState.modelInForce`
(`app/src/main/java/com/onyx/app/ui/chat/ChatUiState.kt:169-173`, ordre conversation → bot → serveur),
alimentée par `botsViewModel::modelOf` (`app/src/main/java/com/onyx/app/ui/OnyxApp.kt:1855-1859`),
c'est-à-dire `faces.modelOf(name) ?: bot.model` (`app/src/main/java/com/onyx/app/ui/bots/BotsViewModel.kt:601`).
L'envoi porte exactement ça : `model = modelOverride ?: state.modelInForce`
(`app/src/main/java/com/onyx/app/ui/chat/ChatViewModel.kt:1488`), et le profil du bot est appliqué au
tour en `app/src/main/java/com/onyx/app/ui/chat/ChatViewModel.kt:1440`.

---

## 2. Tableau des surfaces

| Surface | Visage — où il est résolu | Voix — où elle est choisie | Modèle — où il est lu | Même chemin que le fil ? |
|---|---|---|---|---|
| **Fil** | `BotProfile.faceCode` du roster résolu par `withSwiftFaces` (`BotsViewModel.kt:523`), table exposée par `BotFaceResolver` (`OnyxApp.kt:590-595`), lue en `ThreadSpeaker.kt:57` | `SpeechOutput.speak(..., speaker = openConversationBot?.name)` (`OnyxApp.kt:1220`) → `BotVoices.onDevice` / `kokoroFor` (`SpeechOutput.kt:234`, `:365`) | `ChatUiState.modelInForce` (`ChatUiState.kt:169-173`) ← `BotsViewModel.modelOf` (`BotsViewModel.kt:601`), envoyé en `ChatViewModel.kt:1488` | **référence** |
| **Salle** | `faceResolver::codeOf` (`GroupRoomScreen.kt:197`) posé au ViewModel (`GroupRoomScreen.kt:200`) → `useFaces` (`GroupRoomViewModel.kt:66-68`) → `voiceLines(face)` (`GroupRoomFeed.kt:110`) | `session.speak(reply.text, speaker = reply.profile)` (`GroupVoice.kt:718`) ; salle locale : `speech.speak(said, speaker = bot.name)` (`GroupChatViewModel.kt:452`) | salle serveur : **aucun modèle envoyé**, `api.send(room.id, said, id)` (`GroupRoomFeed.kt:436`) — le serveur décide ; salle locale : `groupBotConfig` (`GroupChatViewModel.kt:90-96`) → `model = bot.model ?: connection.model`, envoyé en `:422` | **oui**, sauf repli `GroupRoomViewModel.kt:70` |
| **Vocal (scène)** | `GroupVoiceLine.faceCode` (`GroupVoice.kt:36`), produit par `voiceLines(face)` (`GroupRoomFeed.kt:110`) ou `message.bot?.faceCode` (`GroupChatViewModel.kt:185`) ; `withFaces(faceResolver::codeOf)` en `OnyxApp.kt:3773` | `session.speak(..., speaker = last.profile)` (`GroupVoice.kt:683`, `:718`) | **aucune lecture de modèle dans la scène** (0 occurrence de `model` dans `GroupVoice.kt`) : le tour vient du fil (`OnyxApp.kt:1214-1220`) ou de la salle | **oui** |
| **Widget** | **aucun** — le widget n'a aucune notion de bot ; libellé fixe « Onyx » (`OnyxWidget.kt:131`), note d'agent (`OnyxWidget.kt:76`) | **aucune** — le widget ne parle pas | **aucun** — il lit la dernière conversation (`OnyxWidget.kt:73-95`), pas un modèle | **non** : ni le chemin du fil, ni un second chemin — rien |
| **Wear OS** | **aucun** — 0 occurrence de `BotFace`/`BotProfile`/`faceCode` dans `wear/` ; l'écran ne dessine que l'anneau et l'icône micro (`WearActivity.kt:106-118`) ; la tuile Wear dit « Ask Onyx » (`WearAskTileService.kt:86`) | **voix d'Onyx, jamais celle d'un bot** : `output.speak(reply)` sans `speaker` (`WearAskViewModel.kt:135`) → `BotVoices.onDevice(null, …)` rend `null` (`BotVoices.kt:110`) → voix par défaut (`SpeechOutput.kt:231-249`) | `WearSettings.server().model` (`WearSettings.kt:24`) ← SharedPreferences locales `onyx_wear` (`WearSettings.kt:16`, `:33-39`), envoyé en `WearAskViewModel.kt:121` | **non** : second chemin complet (préfs locales + modèle du serveur) |
| **Android Auto** | **aucun** — 0 occurrence dans `auto/` ; les images sont `AutoArtwork.startCard` (`AutoHomeScreen.kt:76`) et `AutoArtwork.voiceState` (`AskScreen.kt:147`), palette fixe (`core/src/main/java/com/onyx/app/car/AutoArtwork.kt:20-26`) ; l'en-tête nomme le **serveur** (`AskScreen.kt:135`) | **voix d'Onyx, jamais celle d'un bot** : `output.speak(reply)` sans `speaker` (`AskScreen.kt:118`) ; aucun `configure(...)` dans `auto/` → `replyVoice` reste `ON_DEVICE` (`SpeechOutput.kt:70`) et `botVoices` vide (`SpeechOutput.kt:81`) | `AutoSettingsBridge.server()` (`AskScreen.kt:87`) ← `EXTRA_SERVER_MODEL` = `server.model` du **serveur actif** du téléphone (`AutoSettingsProvider.kt:31`, `:39`), envoyé en `AskScreen.kt:94` | **non** : état partiel reçu (habillage + serveur), aucun état de bot |
| **Tuile de raccourci** | `shortcut.faceCode` = `bot.faceCode` du roster résolu (`BotShortcutPlan.kt:138`, `RosterShortcuts.kt:88`) → `BotTileArtwork.botTile` (`RosterShortcuts.kt:96`, `BotTileArtwork.kt:112-131`) ; **repli** `AppContainer.botFaces.faceOf` (`RosterShortcuts.kt:163-164`) | **aucune** — la tuile n'émet aucun son | **aucun** — la tuile ne porte qu'une intention (`RosterShortcuts.kt:124-137`) | **oui** pour le visage (via `BotProfile.faceCode`), **avec un repli distinct** qui lit `BotFaceStore` directement |

---

## 3. Ce que chaque surface hors téléphone reçoit réellement

**Wear OS ne reçoit rien.** Le module est *standalone* (`wear/src/main/AndroidManifest.xml:22`) et ne
dépend que de `:core` (`wear/build.gradle.kts:89`) ; aucun pont téléphone → montre n'existe :
`grep` de `DataClient|MessageClient|DataLayer|wearable` dans `app/src/main` et `core/src/main` ne rend
**aucune** occurrence. La montre a donc son propre serveur, sa propre clé et son propre champ
« Model » saisi au poignet (`WearActivity.kt:164-195`, enregistré par `WearSettings.kt:33-39`). Un bot
n'y est pas représenté : il n'y a ni roster, ni visage, ni voix de bot, ni conversation de bot.

**Android Auto reçoit un état partiel.** Le pont existe — un `ContentProvider` dans l'app
(`app/src/main/java/com/onyx/app/data/local/AutoSettingsProvider.kt:17`) joint par
`AutoSettingsBridge` (`auto/src/main/java/com/onyx/app/auto/AutoSettingsBridge.kt:47-59`) — mais le
contrat ne transporte que huit extras (`core/src/main/java/com/onyx/app/car/AutoLooks.kt:5-13`) :
l'habillage des cartes, celui de la voix, et la connexion du **serveur actif**
(`AutoSettingsProvider.kt:31-40`). Aucun extra ne nomme un bot, son visage, sa voix ou son modèle.
Côté voiture, `auto/` ne dépend que de `:core` (`auto/build.gradle.kts:84`), donc `BotFaceResolver`,
`BotRosterFaces` et `BotFaceStore` — qui vivent dans `:app` — lui sont structurellement inaccessibles ;
`BotVoices` et `SpeechOutput`, eux, sont dans `:core` et accessibles, mais ne sont jamais configurés
avec les voix des bots.

**Le widget ne reçoit que du texte.** `widgetSnapshot` lit la dernière conversation
(`OnyxWidget.kt:73-95`) et `PhoneToolsStore.agentWidgetText` (`OnyxWidget.kt:76`) ; le rendu écrit
« Onyx » en dur (`OnyxWidget.kt:131`). Aucun `BotProfile` n'est importé ni lu. Une réponse de bot y
apparaît donc sous l'identité d'Onyx.

**La tuile de raccourci, elle, porte bien le bot** — mais par un second chemin de repli. La couleur
vient de `BotTileArtwork.botColour(name, faceCode, theme)` (`BotTileArtwork.kt:99-100`) et le code
vient du roster résolu (`BotShortcutPlan.kt:138`) ; si ce code est nul, `RosterShortcuts` lit
directement `BotFaceStore` (`RosterShortcuts.kt:163-164`) au lieu de `BotFaceResolver`. En pratique le
repli est presque inatteignable — `withSwiftFaces` donne un code à tout nom qu'il a vu
(`BotRosterFaces.kt:24`, et `BotFace.faces` remplit une entrée par nom,
`core/src/main/java/com/onyx/app/ui/design/BotAvatar.kt:415-459`) —, mais le chemin existe et lit une
autre source que la règle unique.

---

## 4. Le tour, puisqu'il fait partie de l'affirmation

| Surface | D'où vient l'historique du tour | `fichier:ligne` |
|---|---|---|
| Fil | l'historique de la conversation ouverte, avec le profil du bot appliqué au tour | `ChatViewModel.kt:1440`, `:1486` |
| Salle serveur | le journal de la salle ; l'app n'envoie que le texte | `GroupRoomFeed.kt:436` |
| Salle locale | le fil local du compositeur | `GroupChatViewModel.kt:421` |
| Wear | **un seul tour** : `history = listOf(ChatTurn(role = USER, text = question))` — aucune mémoire d'une question à l'autre | `WearAskViewModel.kt:121` |
| Android Auto | l'historique de la **session voiture** (`OnyxCarSession.history`), remis à zéro par « New Chat » | `OnyxCarAppService.kt:22`, `AskScreen.kt:89`, `AutoHomeScreen.kt:79` |
| Widget / tuile | aucun tour : ils n'émettent pas de requête | `OnyxWidget.kt:73-95`, `RosterShortcuts.kt:124-137` |

Ni Wear ni Auto ne reprennent le `chatId` du bot (`BotProfile.chatId`,
`core/src/main/java/com/onyx/app/data/model/serve/BotProfile.kt:39`) : ce sont des conversations
parallèles, pas la conversation du bot.

---

## 5. Verdict

**Présence unique : partielle.**

- **Oui** à l'intérieur de l'app : le fil, la salle et la scène vocale passent tous par
  `BotFaceResolver` (via `BotProfile.faceCode`), par `SpeechOutput` + `BotVoices`, et par
  `BotsViewModel.modelOf` ; aucun de ces trois écrans ne réinvente la règle.
- **Non** hors de l'app : sur trois des sept surfaces de l'affirmation — widget, Wear, Android Auto —
  le bot n'a **aucune** présence. Ce n'est pas un second chemin de résolution, c'est une absence de
  résolution : le visage n'existe pas, la voix est celle d'Onyx par défaut, et le modèle est celui du
  serveur (Wear : le sien, saisi sur la montre ; Auto : celui du serveur actif du téléphone). Un bot
  n'y est donc ni reconnaissable, ni audible, ni serviable comme lui-même.
- **Partielle aussi sur la tuile de raccourci** : le visage suit bien le roster, mais un repli lit
  `BotFaceStore` en direct, hors `BotFaceResolver`.

---

## 6. Écarts à corriger, par gravité

1. **Wear OS ne connaît aucun bot.** La montre est *standalone* (`wear/src/main/AndroidManifest.xml:22`),
   n'a aucun pont avec le téléphone (0 occurrence de `DataClient`/`MessageClient`/`DataLayer` dans `app/` et
   `core/`) et ne dépend que de `:core` (`wear/build.gradle.kts:89`). Un bot n'y a ni visage, ni voix,
   ni conversation.
   *À corriger :* `wear/src/main/java/com/onyx/app/wear/WearAskViewModel.kt:121` (le tour) et
   `:135` (la lecture) ; l'état à recevoir manque entièrement — il faut un pont téléphone → montre
   (Data Layer) ou un contrat équivalent à celui d'Auto, et une résolution de visage dans `:core`
   (aujourd'hui `BotFaceResolver` est dans `:app`, `BotFaceResolver.kt:1`).

2. **Android Auto ne connaît aucun bot.** Le contrat du pont n'expose que l'habillage et le serveur
   (`core/src/main/java/com/onyx/app/car/AutoLooks.kt:5-13`), et le provider ne remplit que ces
   champs (`app/src/main/java/com/onyx/app/data/local/AutoSettingsProvider.kt:32-40`).
   *À corriger :* `AutoSettingsContract` (`AutoLooks.kt:4-14`) — ajouter le bot de la conversation
   courante (profil, `faceCode`, voix) ; `AutoSettingsBridge.looks/server`
   (`AutoSettingsBridge.kt:21-44`) pour les lire ; `AskScreen.kt:135-150` pour l'afficher.

3. **Le widget efface l'identité du bot.** Une réponse de bot s'y lit sous « Onyx »
   (`app/src/main/java/com/onyx/app/widget/OnyxWidget.kt:131`), alors que la conversation porte un bot.
   *À corriger :* `OnyxWidget.kt:73-95` (porter le libellé et le `faceCode` du bot de la conversation)
   et `:131` (écrire ce libellé à la place de la constante).

4. **La voix réglée dans l'app n'atteint ni la montre ni la voiture.** Aucun `configure(...)` n'existe
   dans `wear/` ni dans `auto/` (`grep` : seule occurrence, `WearAskViewModel.kt:65`, qui est
   l'enregistrement du serveur). `SpeechOutput` y garde donc ses défauts : `replyVoice = ON_DEVICE`
   (`SpeechOutput.kt:70`), `botVoices` vide (`:81`), `botNames` vide (`:82`). Conséquence : même la
   voix d'Onyx choisie dans les Réglages (Kokoro, Hermes) n'y est pas appliquée, et un bot ne pourrait
   pas y avoir la sienne même nommé.
   *À corriger :* `WearAskViewModel.kt:45-46` et `AskScreen.kt:42-43` — appeler `SpeechOutput.configure`
   avec `replyVoice`, `kokoro*` et `botVoices`, et passer `speaker` à `speak`.

5. **Wear ne garde aucun tour, Auto garde le sien.** La montre envoie une requête à un seul tour
   (`WearAskViewModel.kt:121`) ; la voiture tient un historique de session (`OnyxCarAppService.kt:22`,
   `AskScreen.kt:89`) qui n'est pas celui du bot (`BotProfile.chatId`,
   `core/src/main/java/com/onyx/app/data/model/serve/BotProfile.kt:39`). « Même tour » est donc faux
   sur les deux surfaces, indépendamment du visage et de la voix.
   *À corriger :* `WearAskViewModel.kt:114-121` (historique) et `OnyxCarSession` (lier la session au
   `chatId` du bot plutôt qu'à une liste locale).

6. **La tuile de raccourci a un repli qui lit `BotFaceStore` hors `BotFaceResolver`.**
   `RosterShortcuts.kt:88` → `:163-164` (`AppContainer.botFaces.faceOf`). Le contrat du résolveur dit
   pourtant qu'« un écran ne lit jamais `BotFaceStore` pour dessiner » (`BotFaceResolver.kt:27-28`).
   *À corriger :* `app/src/main/java/com/onyx/app/shortcuts/RosterShortcuts.kt:88` — passer le
   résolveur (ou la table du roster) au lieu de relire le magasin.

7. **La salle a le même repli.** `GroupRoomViewModel.face` retombe sur `faceStore.faceOf(name)` quand
   l'écran n'a pas encore posé sa table (`GroupRoomViewModel.kt:70`) — c'est exactement le défaut que
   le commentaire de `GroupRoomScreen.kt:195-196` dit avoir fermé.
   *À corriger :* `GroupRoomViewModel.kt:63-70` — rendre la table obligatoire (ou `BotFaceResolver.none`)
   plutôt qu'un retour au magasin local.

8. **Deux variantes du même calcul, hors du résolveur.** Le volet résout par
   `knownBots.firstOrNull { it.name == name }?.faceCode ?: botsState.faces[name]`
   (`OnyxApp.kt:2761-2763`) et l'éditeur par `BotFaceResolver.of(state.faces).codeOf(...) ?: bot.faceCode`
   (`BotsViewModel.kt:658-659`). Les deux donnent aujourd'hui le même résultat que le résolveur, mais
   ce sont deux écritures de la règle en plus de `BotFaceResolver.of` (`OnyxApp.kt:590-595`).
   *À corriger :* `OnyxApp.kt:2761` et `BotsViewModel.kt:659` — lire `faceResolver.codeOf`.

---

## 7. Non trouvé

- Aucun test ne vérifie la présence d'un bot dans `wear/` ou `auto/` : `app/src/test` et
  `core/src/test` ne citent ces modules que dans `NomEtIconeDuLanceurTest.kt`,
  `VersionSchemeTest.kt` et `PackageNameIndependenceTest.kt`, pour les noms de paquets.
- Aucun réglage « bot de la voiture » ou « bot de la montre » : `grep` de `BotProfile` dans `wear/` et
  `auto/` rend 0 occurrence.
- Aucun pont de données Wear (Data Layer, `MessageClient`, `DataClient`) dans `app/` ni `core/`.
