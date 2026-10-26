# Le grand visage qui parle dans une salle de groupe — reproduit, corrigé, recapturé

Le propriétaire dit : *« quand je crée un groupe avec des bots j'ai l'ancienne UI, c'est pas comme
le modèle, le  que je veux ; regarde les autres images : chacun des bots parle chacun son tour
avec ses propres expressions et animations »*. Sa capture montre l'orbe avec **tous les visages côte
à côte**, la légende **« À L'ÉCOUTE »**, et un bouton du milieu **gris terne** (une relance
désactivée) là où le modèle met le ⏸ de la pause.

- Worktree : `/home/john/onyx-group-voice-3` (branche `onyx-group-voice-3`), base `d364381`, le
  HEAD de `onyx-integration2` (318 commits, `versionName 1.0.0+318`). `onyx-integration2` n'a pas
  été touché.
- Spécification : `/home/john/r132/onyx` —  (`groupSpeaker`,
  `OrbFace`, `GroupVoiceFaces`, `phaseTitle`, `controls`), .
- Émulateur : `emulator-5570` (AVD `onyx-e44`, 1080×2400, français, sombre). **`emulator-5558` n'a
  pas été utilisé.** L'émulateur a été éteint à la fin.

## 1. La cause exacte, reproduite à l'écran

Une salle de groupe est tenue par un **serveur de rejeu** (`serve.py` : les mêmes routes REST que le
Dashboard, son canal JSON-RPC `groups.*`, et `POST /push` qui fait arriver la réplique du bot
suivant). Le vocabulaire de l'acteur est celui de la passerelle, mot pour mot
(`gateway/hosted_room_discussion.py` : `actor = {"kind": "member", "id": member_id, "profile": profile}`) :
**l'identifiant du membre et son profil sont deux choses distinctes**.

Le journal de la machine, pendant la lecture (`android.util.Log` posé dans le coordinateur, à HEAD) :

```
collect lines=2 seqs=[1, 100] pending=1 phase=IDLE held=false
next()   isOn=true held=false current=false phase=IDLE pending=1
SPEAK m-2 :: The trail's open, but it's mud      <- l'identifiant du membre, pas le profil
SPEAK m-3 :: And here's your invite: Falls
```

Sur l'écran, à ce moment : **tous les visages dans l'orbe** et la légende **« A BOT PARLE »** — la
capture du propriétaire, exactement (`05-avant-tous-les-visages.png`, son dump dit `A BOT PARLE`).

**Pourquoi.** `GroupRoomFeed.voiceLines` remplissait `GroupVoiceLine.profile` avec
`event.actorID` — **l'identifiant du membre** — alors que tous ses lecteurs y lisent un **profil** de
bot : `GroupVoiceScene.speaker = bots.firstOrNull { it.name == speakerId }`, et `composerBots` nomme
chaque bot par `member.profile`. Les deux ne se rencontrent que si la salle les confond. Sinon :

- `speaker == null` → `GroupVoiceOrbDisplay` retombe sur `GroupVoiceFaces` (**tous les visages**) et
  `groupVoicePhaseTitle` sur « A bot is speaking » → **« A BOT PARLE »** ;
- `session.speak(speaker = m-2)` → `BotVoices` ne trouve pas ce bot : **il n'a pas sa propre voix**.

Le modèle fait exactement l'inverse : `groupSpeaker = group.feed.room.member(group.voice.speaker)`
— elle résout **par l'identifiant du membre**, puis prend son profil.

Et pour la salle **créée sur le téléphone** (le cas littéral du propriétaire, « quand je crée un
groupe »), sa deuxième cause, éprouvée par les épreuves : `GroupChatViewModel.lines` ne portait ni
`seq` ni `draftId`, or `GroupVoiceReplies.update` ne retient que ceux-là (les messages du journal et
les brouillons vivants). **Rien n'était donc jamais lu** : ni voix, ni visage, ni légende.

Troisième chose, celle du bouton : le milieu de la barre était la **relance**, `enabled` seulement
quand la phase est au repos — d'où le **gris terne** de sa capture.

## 2. Ce qui est corrigé

| Fichier | Changement |
|:--|:--|
| `ui/bots/GroupVoice.kt` | `GroupVoiceLine.memberID` : l'identifiant du membre est gardé **à part**, et `profile` dit le profil du bot — le vocabulaire de la passerelle. |
| `ui/bots/GroupRoomFeed.kt` | `voiceLines` porte `profile = room.member(id)?.profile` et `memberID = actorID`, comme `room.member(id)?.profile` du modèle. |
| `ui/bots/GroupChatViewModel.kt` | `GroupMessage.seq` posé à la naissance ; le fil local porte `seq` (une fois la réponse finie), `draftId` (tant qu'elle s'écrit), `after` et `memberID` — **le vocabulaire d'une salle du serveur**. |
| `ui/voice/VoiceScreen.kt` | Dans une salle, le bouton du milieu est **Pause / Reprendre**, le ⏸ du modèle ; la relance reste au solo. |

## 3. L'épreuve qui échouait

`app/src/test/java/com/onyx/app/ui/voice/GroupVoiceBigFaceTest.kt` — **3 des 5 épreuves échouent à
HEAD**, mesuré en remettant les sources de `app/src/main` à HEAD (`git stash`), épreuve relancée :

```
FAIL une salle du serveur pose le visage du bot qui parle, et la legende le nomme
     java.lang.ComparisonFailure: le fil porte le profil du bot expected:<[scout]> but was:<[m-7]>
FAIL le fil d une salle neuve porte de quoi etre lu par la voix
     java.lang.AssertionError: un numéro par question
FAIL les trois boutons de la reference, et la pause qui met vraiment en pause
     java.lang.AssertionError: une salle met le ⏸ du modèle au milieu
```

Après le correctif : **5/5 vertes**, dont « pendant la lecture d'un bot dans une salle, le grand
visage affiché est celui du bot qui parle, et la légende le nomme ».

## 4. Recette

```
JAVA_HOME=/home/john/android-toolchain/jdk-21 ANDROID_HOME=/home/john/android-toolchain/sdk \
/home/john/android-toolchain/gradle/bin/gradle :core:testDebugUnitTest :app:testDebugUnitTest \
  :app:assembleRelease --no-daemon --console=plain \
  -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.workers.max=2
```

`BUILD SUCCESSFUL in 4m 56s`. Compté dans les XML : **1095 épreuves, 0 échec, 0 erreur** —
`core` 491, `app` 604. Le plancher de **1090** est tenu (1090 + 5 nouvelles). Un premier passage
avait vu le démon Gradle se faire tuer (« disappeared unexpectedly ») sous la charge de la machine,
où un autre lot lançait la même recette ; le passage retenu va au bout.

- APK release : `app/build/outputs/apk/release/app-release.apk`, `versionName 1.0.0+318`,
  `versionCode 318`.
- `sha256 d5dc26648dd00b431f41d9b54f15f49f0809ced830454ee4bc7f94b7c1592459`.
- **Installé et vérifié sur `emulator-5570`** : `adb exec-out cat $(pm path)` donne la **même**
  empreinte, `dumpsys package` relit `1.0.0+318`.

## 5. Les captures

| Capture | Ce qu'elle montre |
|:--|:--|
| `20-release-scout-parle.png` | **APK release** : un seul grand visage (Scout), son aura, trois anneaux fins, la légende **« SCOUT PARLE »** en capitales espacées vertes, le fil avec visage + nom + haut-parleur, l'aide du bas, et les trois boutons **clavier / ⏸ / ■** |
| `21-release-quill-parle.png` | la même scène, **le bot suivant, Quill** : « **QUILL PARLE** » — deux bots différents à tour de rôle |
| `01-apres-scout-parle.png`, `02-apres-quill-parle.png`, `03-apres-onyx-parle-pause.png` | les trois bots à tour de rôle (« SCOUT PARLE », « QUILL PARLE », « ONYX PARLE »), dumps compris |
| `03-apres-onyx-parle-pause.png` + `.xml` | **la pause vérifiée** : avant l'appui « ONYX PARLE » + bouton « Pause » ; après l'appui la légende **tient** (« ONYX PARLE ») et le bouton devient « **Reprendre** » — la lecture est tenue, pas terminée |
| `04-apres-a-l-ecoute.png` | l'orbe au repos : tous les visages, « PRÊT », le ⏸ au milieu (plus de gris terne) |
| `05-avant-tous-les-visages.png` | **avant** (APK construit à HEAD intact) : tous les visages côte à côte, « **A BOT PARLE** », relance grise au milieu |
| `10-comparaison-quill.png`, `11-comparaison-scout.png` | le modèle 2.1 (l'écran du téléphone cadré) à gauche, la scène à droite, **même largeur** |
| `12-comparaison-avant-apres.png` | la capture du propriétaire, la scène d'avant, la scène d'après |

`*.xml` sont les arbres d'accessibilité : la légende y est lue telle quelle (`SCOUT PARLE`,
`QUILL PARLE`, `ONYX PARLE`, `A BOT PARLE`), ce qui est la preuve que **c'est le bot qui parle** qui
est nommé, et pas seulement l'image.

## 6. Écarts assumés

- Les visages du serveur de rejeu sont ceux que l'app dessine **par défaut** (le faux Dashboard ne
  donne aucun code de visage) : ce ne sont pas ceux du propriétaire, et ce n'est pas un écart de
  implémentation — le précédent lot l'avait déjà relevé.
- Les anneaux : le implémentation a les **deux halos**  (ils voyagent vers l'extérieur en s'effaçant) ;
  un instantané en prend un net et l'autre presque éteint, là où le cadrage du modèle en
  montre deux. La mécanique est la même.
- La ligne d'aide et les trois boutons sont ceux du modèle ; la phrase d'invocation est
  « stop listening » / « that's all », traduite à l'identique.
- Le bandeau orange « Onyx a besoin du micro pour écouter » vient de l'émulateur, qui n'a ni
  microphone ni moteur de reconnaissance : il n'est pas dans le modèle.
- Le solo garde la relance ; dans une salle, elle laisse la place au ⏸ (ce que le lot demandait).

## 7. Ce qui n'est pas capturé

La salle **créée sur le téléphone** (le cas littéral du propriétaire) n'est prouvée **que par les
épreuves**, pas par une capture : sans microphone ni moteur de reconnaissance, l'émulateur ne peut ni
poser la question qui ouvre le round, ni faire répondre les bots, et la scène d'une salle ne lit que
ce qui arrive **après** son ouverture. Sa cause (aucun `seq` sur le fil) est mesurée par
`GroupVoiceBigFaceTest`, et le correctif est le même code que celui capturé ici.
