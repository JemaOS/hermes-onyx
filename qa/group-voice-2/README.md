# Scène vocale d'une salle du serveur — port et branchement

Capture : `screenshots/group-voice-speaking.png` (état « X IS SPEAKING », comme le modèle ),
arbre : `ui/group-voice-speaking.xml`. `screenshots/room-talk.png` : la salle du serveur, son
composer avec **Talk** (il n'existait pas avant ce lot).

## Ce qui est porté

1. **Le fil de la salle nourrit la scène vocale.** `GroupVoiceThread` (GroupVoice.kt) est le
   `GroupVoiceScene { voice, feed }`  : ce qui se dessine (`lines`), si les bots travaillent
   (`isTalking`), ce qui n'attend que la personne (`waitsOnPerson`), et où va la question reconnue
   (`ask` → `groups.send`, `haltTalking` → `groups.stop`).
   - `GroupRoomFeedState.voiceLines(face)` (GroupRoomFeed.kt) fabrique ces lignes : les messages du
     journal, ce qui part (`sending`) et ce qui s'écrit (`drafts`), avec `seq`/`draftId`/`after`.
   - `GroupRoomViewModel` **est** ce fil : `ask` = `send` (`groups.send`), `haltTalking` = `stop`
     (`groups.stop`). Le compositeur local (`GroupChatViewModel`) l'est aussi, donc la salle neuve
     garde sa scène vocale.
   - `OnyxApp` : `Route.GROUP_VOICE` prend le ViewModel de la salle avec la **même clé** que
     `GROUP_CHAT` quand `openedRoom` est posé, et suit le fil pendant que la voix est ouverte
     (`repeatOnLifecycle`), comme `.task(id: scenePhase == .active || voice.isOn)`.
2. **Talk est offert sur une salle du serveur** : `GroupRoomScreen(onOpenVoice = …)` depuis
   `OnyxApp`. Le bouton n'est dessiné que si la lambda est fournie.
3. **File de parole portée** (`GroupReplies` ) : ordre de commencement (`after`), chaque
   réponse lue une fois, un brouillon dont le message arrive n'est pas relu, un bot qui passe
   (« pass ») n'est jamais lu, un brouillon lâché avant d'être entier ne l'est pas plus tard.
4. **La vue** : en-tête (visages empilés + nom, `FaceStack` size 22), orbe du bot qui parle avec
   l'aura, les deux anneaux (`Halos`) et la lueur du visage (`.shadow`), légende en capitales
   espacées (`.footnote.weight(.semibold).tracking(1.2)`), bulle de la personne, réponses **à
   plat** sous leur visage (`RoomMemberRow`, `palette.flatReplies`), la ligne orange « A bot needs
   you », l'aide mains-libres, et les trois boutons ronds (clavier, pause, carré rouge).

## Limite connue, dite plutôt qu'inventée

`VoiceSession` dit un texte **d'un bloc** (`speak`) ;  enchaîne les morceaux au fil de l'écriture
(`beginReading` / `continueReading` / `endReading` / `prefetch`). Une réponse n'est donc lue qu'une
fois **entière** — la salle l'a écrite, ou le bot a fini de l'écrire. La file qui décide *quoi* lire
et *dans quel ordre* est celle  ; seule la lecture incrémentale manque.

## Écarts de la capture

- La ligne rouge « Onyx needs the microphone to listen… » n'est pas dans le modèle : le micro a
  été refusé à l'émulateur (qui n'a ni microphone ni moteur de reconnaissance) pour ne pas ouvrir
  une écoute qui n'aurait pas de fin.
- Les visages des bots sortent du faux Dashboard (aucun code de visage) : leur dessin diffère de
  celui de la capture du propriétaire, ce n'est pas un écart de implémentation.
- La légende mesure 132 dp contre 125 pt sur le modèle (≈ +6 %), métriques de police.
