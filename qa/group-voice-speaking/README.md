# La scène vocale d'une salle — le grand visage qui parle, son expression, la légende et la voix de chaque bot

Le propriétaire compare sa capture au modèle 2.1 et dit trois choses : *le design ne ressemble
pas au modèle*, *il n'y a pas d'expression quand le visage parle*, et *ils ne parlent pas entre
eux avec une voix différente*. Ce lot porte les trois.

- Worktree : `/home/john/onyx-voice-group` (branche détachée), base `461add9` — le HEAD de
  `onyx-integration2` au moment du implémentation (261 commits, `versionName 1.0.0+261`).
- Commit du implémentation : `6f875f1` — *La scene vocale d une salle : le grand visage qui parle, son
  expression, la legende et la voix de chaque bot* ; puis `11e0eb0` (les voix lues depuis les
  réglages du serveur, le niveau de la maquette posé dès la première image).
- APK release : `releases/app-v1.0.0_265-20261025-2215.apk`,
  `sha256 335a06200341649f43b3ed04c5d10c7555bbe8d965dedc381f4374163a73934c`, `versionName 1.0.0+265`
  (265 = le nombre de commits du dépôt au moment du build, HEAD `11e0eb0`) — **installé et
  vérifié sur `emulator-5588`** : `pm path` puis `sha256sum` sur l'appareil donnent la même
  empreinte, et l'app s'ouvre normalement.
- APK debug des captures (`emulator-5554`) : `sha256 278ec5f52c5017d069f4da309601cb6e6966cd0c509776dac17e80539c144395`,
  vérifié sur l'appareil avant et après la passe.
- Le  de `/home/john/r132/onyx` est la spécification : 
  (`OrbFace`, `GroupVoiceFaces`, `OnyxVoiceFace`, `phaseTitle`, `phaseColor`),
  ,  (`BotPose`).

## Ce qui est porté

1. **Un seul grand visage, celui qui parle.** `GroupVoiceOrbDisplay` dessine les visages de toute la
   salle au repos (`GroupVoiceFaces`) et, pendant la lecture, **le seul bot dont la réponse est lue**
   (`group.speaker`), à 196 pt comme `OnyxVoiceFace.size` ; les autres s'effacent par le
   `handoff`  (glissement + rétrécissement + fondu, fondu seul sous Reduce Motion).
2. **L'aura prend la couleur du visage qui parle** (`botFaceColour` : le profond de son dégradé),
   la légende et les anneaux gardant la couleur de la phase — vert pendant la parole. C'est ce qui
   fait l'aura verte autour de Quill sur le modèle.
3. **L'expression** : `VoiceFace` (`OnyxVoiceFace.pose`) — clignement toutes les 4,6 s et un double
   toutes les 13,8 s, regard qui suit la phase (vers vous à l'écoute, en haut et de côté en
   réflexion, devant pendant la lecture, au repos un regard tenu puis un passage vif),
   **bouche qui s'ouvre avec la voix** (`niveau × 1,8`), tête qui penche avec le regard. Une image
   sur trente. `BotAvatar`/`BotLook` reçoivent une `BotPose` (regard, clignement, bouche,
   inclinaison) ; la bouche qui parle est celle  (large de 7 + 3·o et haute de 1,6 + 8·o).
4. **Reduce Motion fige le visage** : aucun instant ne passe, donc pas de clignement, pas de dérive,
   et la bouche reste celle du visage (`TimelineView(paused:)` ).
5. **La voix propre à chaque bot** (`BotVoices`, comme ) : chaque réplique est lue
   avec la voix de son bot — celle qui lui a été réglée, sinon une des siennes, **deux bots jamais
   pareils** tant qu'il y a des voix à répartir, et le principal lit de la voix d'Onyx. La répartition
   est l'empreinte FNV-1a du nom, donc la même sur tout appareil et à chaque lancement. Aucune
   hauteur n'est inventée : c'est la voix qui change, jamais un réglage sur la voix d'un autre.
   `BotVoices` donne la voix du synthétiseur (`SpeechOutput.installedVoices()`, qualité ≥ *high*
   d'abord) et la voix Kokoro (même langue que celle d'Onyx) ; la vitesse du bot s'ajoute
   (`BotVoices.speed`).
6. **Le fil garde un visage et un nom par réponse** (`RoomMemberRow`), et **le tour en cours se
   distingue** : un haut-parleur et le nom dans la couleur de l'accent sur la réponse dont la voix
   sort.
7. **L'ordre de parole** est celui du implémentation précédent (`GroupVoiceReplies`, `after`, les passages,
   un bot à la fois) — inchangé, et toujours éprouvé par `GroupVoiceTest`.

## Le réglage par bot: ce qui existe, ce qui reste

`Settings.botVoices` est porté côté données (`SettingsRepository.botVoices`, `botVoicesHere`,
`setBotVoice`, la clé `botVoices` ) et **lu** par la lecture (`OnyxApp` → `SpeechOutput.configure`
→ `BotVoices`). En revanche, **l'écran qui règle la voix d'un bot () n'est pas
dessiné** : les deux menus de bot gardent `onVoice = null` (`BotsScreen`,
`ConversationsScreen`). Un bot sans réglage reçoit donc une voix de son cru — c'est ce qui fait
qu'ils ne lisent pas tous de la même voix —, et un réglage posé par un autre chemin est honoré.

## Vérification

```
JAVA_HOME=/home/john/android-toolchain/jdk-21 ANDROID_HOME=/home/john/android-toolchain/sdk \
/home/john/android-toolchain/gradle/bin/gradle :core:testDebugUnitTest :app:testDebugUnitTest \
  --no-daemon --console=plain -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.workers.max=2
```

`BUILD SUCCESSFUL` — **840 épreuves, 0 échec, 0 erreur** (`*/build/test-results/testDebugUnitTest/*.xml`),
dont **31 nouvelles** : 17 pour les voix par bot (`BotVoicesTest`), 5 pour l'instant du visage
(`VoiceFacePoseTest`), 9 pour le câblage de la scène (`GroupVoiceScene2Test`). Le plancher de
809 est tenu (809 + 31).

Une passe a rendu **3 échecs** dans `GroupRoomFeedTest` (épreuves de brouillons qui dépendent du
temps) sous charge ; elles sont repassées vertes deux fois de suite, sans changement de code.

## Les captures

| Capture | Ce qu'elle montre |
|:--|:--|
| `01-scene-quill-parle.png` | la scène de parole : un seul grand visage (Quill), aura verte, « QUILL IS SPEAKING » en capitales espacées sous l'orbe, le fil avec visage + nom par réponse, Quill marqué du haut-parleur, les trois boutons |
| `05-quill-parle.png` | la même scène **en français** : « **QUILL PARLE** » — ce que voit le propriétaire |
| `03-bouche-1/2/3.png` + `mouth-openness.txt` | la bouche du grand visage à trois instants : 51×49, 44×31, 51×49 px — **elle s'ouvre et se referme** (18 px d'amplitude) ; le visage entier change de 5 612 pixels entre les deux extrêmes |
| `04-reduce-motion-1/2.png` + `reduce-motion.txt` | sous « Supprimer les animations » : deux images à 1,2 s d'écart, **mêmes octets** (sha256 identique), 0 pixel de différence — le visage est figé et sa bouche est celle du visage (64×26, large et plate) |
| `02-ecoute-visages.png` | l'orbe au repos à l'écoute : les trois visages, « LISTENING », « Go ahead. » |
| `08-release-installee.png` | l'APK release installé qui s'ouvre (l'écran de conversation d'Onyx) |
| `06-comparaison-reference.png` | le modèle 2.1 à gauche, la scène Android à droite |
| `07-comparaison-ecoute.png` | la capture du propriétaire à gauche, la même scène Android à droite |
| `01-…xml`, `02-…xml`, `05-…xml` | l'arbre d'accessibilité : « QUILL IS SPEAKING » (et « QUILL PARLE »), les quatre réponses, les descriptions des visages |

## La réplique est simulée, et c'est dit

L'émulateur **n'a ni microphone ni moteur de reconnaissance** : la scène de parole ne peut pas être
provoquée par la voix. Elle est donc posée par un **état injecté**, le crochet de debug
`-e onyx.groupVoiceDemo speaking` (debug seulement, comme `-echo.groupVoice` ) : une salle
scriptée, la question et les trois réponses du modèle, Quill comme bot qui parle, et un niveau
de voix **simulé** qui bat (`GroupVoiceDemo.levelAt`, la formule de `OrbFace.demoLevel` ). Le
niveau réel d'une vraie lecture vient de `SpeechOutput.replyLevel` : la **vraie** amplitude des
échantillons quand la voix est de l'audio (Kokoro, Hermes), un niveau tenu — et dit comme tel —
quand c'est le synthétiseur, qui n'expose aucune mesure.

`02-ecoute-visages.png` et `05-quill-parle.png` ont été prises pendant cette passe ; le texte de la
légende est vérifié dans l'arbre (`QUILL IS SPEAKING`, `QUILL PARLE`) et par la localisation
(« <NOM> is speaking » → « <NOM> parle », éprouvé par `OnyxLocalizationTest`).

## Écarts assumés

- Les visages de la maquette sont des codes choisis pour la capture (Quill en `blob` sarcelle à
  fard), pas les visages du propriétaire : ce sont les siens que l'app dessine sur son appareil.
- L'anneau de l'orbe est plus ténu que sur le modèle : les deux halos  sont là, mais un
  instantané les prend à leur ouverture la plus large, où leur opacité est au plus bas.
- La bulle de la question est le bleu du thème, celui d'Onyx, et non le lavande du modèle.
- Le haut-parleur devant le nom de Quill n'est pas sur le modèle : c'est la marque du tour en
  cours demandée pour ce lot.
- Sur un visage aux yeux **joyeux** (les arcs de ^ ^, ceux de Quill sur le modèle), le
  clignement ne se voit pas : c'est le dessin , où `case .happy` trace les arcs sans
  regarder l'ouverture des yeux. Ce qui bouge alors sur ce visage, c'est la bouche et l'inclinaison
  de la tête — c'est ce que mesurent les trois images de `03-bouche-*`. Un visage aux yeux ronds
  (ou étincelants) montre aussi le clignement.
