# Présence unique — ce qui est corrigé ici

Suite de `docs/AUDIT-WEAR-AUTO.md`. Chaque ligne de l'audit est classée : **corrigé dans `:app`**,
**hors périmètre** (raison structurelle), ou **nécessite un pont**.

| Écart de l'audit | État | Où / pourquoi |
|---|---|---|
| 1. Wear OS ne connaît aucun bot | **hors périmètre — pont nécessaire** | `wear/` est *standalone* (`wear/src/main/AndroidManifest.xml:22`), ne dépend que de `:core` (`wear/build.gradle.kts:89`) et n'a **aucun** pont téléphone → montre : 0 occurrence de `DataClient`/`MessageClient`/`DataLayer` dans `app/` et `core/`. Rien à lire côté `:app` : il faut un Data Layer (ou un contrat équivalent à celui d'Auto) **plus** une résolution de visage déplacée dans `:core`, puisque `BotFaceResolver` vit dans `:app`. |
| 2. Android Auto ne connaît aucun bot | **hors périmètre — pont nécessaire** | `auto/` ne dépend que de `:core` (`auto/build.gradle.kts:84`) : `BotFaceResolver`, `BotRosterFaces` et `BotFaceStore` lui sont inaccessibles. Le contrat du pont ne transporte que l'habillage et le serveur actif (`core/src/main/java/com/onyx/app/car/AutoLooks.kt:5-13`, rempli en `app/src/main/java/com/onyx/app/data/local/AutoSettingsProvider.kt:32-40`) : ajouter un bot demande d'**étendre ce contrat** (module `:core` **et** `auto/`), pas de corriger `:app`. |
| 3. Le widget efface l'identité du bot | **corrigé ici** | `app/src/main/java/com/onyx/app/data/local/BotPresenceIndex.kt` publie, à chaque lecture du roster (depuis `RosterShortcuts.publish`), le nom, le libellé et le `canonical_session` de chaque profil. `OnyxWidget.kt` y résout le bot de la conversation par les **deux liens de l'app** (chat serveur du bot, puis titre posé) et écrit `snapshot.speaker` à la place de la constante. « Onyx » reste le repli quand aucun bot ne revendique la conversation. Aucune modification des modules compagnons : le widget ne change pas de forme de données, il gagne un champ interne. |
| 4. La voix réglée n'atteint ni la montre ni la voiture | **hors périmètre** | Aucun `configure(...)` dans `wear/` ni `auto/` : c'est leur code (et leur état) qui manque, pas un défaut de `:app`. Côté `:app`, le fil, la salle et la scène passent déjà par `SpeechOutput` + `BotVoices` (`OnyxApp.kt:1086-1095`, `:1220`). Corriger demande d'appeler `configure` **dans les deux modules** avec un état de bot qu'ils ne reçoivent pas (voir 1 et 2). |
| 5. Wear ne garde aucun tour, Auto garde le sien | **hors périmètre** | `WearAskViewModel.kt:121` envoie un tour unique et `OnyxCarSession` tient un historique de session ; lier l'un ou l'autre au `chatId` du bot demande le pont (1, 2) et une décision produit sur la mémoire de la montre. Rien dans `:app` ne peut le faire à leur place. |
| 6. La tuile de raccourci relit `BotFaceStore` | **corrigé ici** | `RosterShortcuts.publish` reçoit désormais `faceResolver: BotFaceResolver` (défaut `none`) et écrit `shortcut.faceCode ?: faceResolver.codeOf(shortcut.bot)` ; la fonction privée qui lisait `AppContainer.botFaces` est supprimée. `BotsViewModel.republishShortcuts` passe `BotFaceResolver.of(state.faces)` — la table du roster. |
| 7. La salle a le même repli | **corrigé ici, avec une nuance assumée** | `GroupRoomViewModel` ne garde plus un `BotFaceStore` comme source de dessin : il le pose en crochet `local` de `BotFaceResolver` (`BotFaceResolver.of(emptyMap(), BotFaceStore(application)::faceOf)`), et `face` lit le résolveur. Le repli survit donc **à l'intérieur** de la résolution unique au lieu d'être un second chemin. Il n'a pas été supprimé parce qu'un test existant le verrouille (`app/src/test/java/com/onyx/app/ui/voice/GroupVoiceBigFaceTest.kt:232-235`) et que les tests existants ne sont pas modifiés dans ce lot ; le rendre obligatoire (`BotFaceResolver.none` sans crochet local) est un changement d'une ligne à faire avec ce test. |
| 8. Deux variantes du même calcul | **corrigé ici** | Le volet lit `faceResolver::codeOf` (`OnyxApp.kt`, `faceCodeOf`), et `BotsViewModel.faceFor` ne réécrit plus la règle : `BotFaceResolver.of(_state.value.faces).codeOf(bot.name)`, sans le second repli `?: bot.faceCode`. |

## Ce qui n'est pas corrigeable dans `:app`, et pourquoi

- **Wear** : module *standalone*, dépend de `:core` seul, aucun pont de données. Le bot n'y est pas
  représenté du tout (ni roster, ni visage, ni son `chatId`) : il n'y a rien à « lire correctement »,
  il y a un canal à créer.
- **Auto** : le pont existe mais son contrat (`AutoLooks`) ne porte que huit extras d'habillage et de
  serveur ; `auto/` ne peut pas atteindre les types de `:app`. Étendre le contrat touche `:core` et
  `auto/`, hors du périmètre de ce lot.
- **Voix des deux compagnons** : elle découle des deux points ci-dessus — sans état de bot reçu, il
  n'y a pas de `speaker` à passer à `SpeechOutput.speak`.
- **Tour** : même dépendance ; en plus, « quel historique » est une décision produit.

## Preuve

`app/src/test/java/com/onyx/app/ui/bots/PresenceUniqueTest.kt` lit les sources et verrouille les
chemins : tuile sans magasin, salle servie par le résolveur, volet sur `faceResolver::codeOf`,
éditeur sans second repli, widget sur `snapshot.speaker`, et les deux liens conversation → bot
(chat serveur, puis titre). Les deux suites restent vertes.