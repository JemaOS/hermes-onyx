# Barre du haut: le visage du bot, l'en-tête du fil retiré

Preuves de la demande du propriétaire (capture `img_2d4d02372500.jpg`) : l'avatar doit être dans la
**barre du haut** — « si après je défile la conversation je peux plus le voir, soit cohérent » — et
la pastille de nom sous l'avatar dans le fil doit disparaître.

## Ce que montrent les captures

| Fichier | Ce qu'on y voit |
|---|---|
| `after-scout-light.png` | **Thème clair**, le fil **scout** (les mêmes messages et la même réponse que la capture du propriétaire) : le visage du bot est à gauche du titre **dans la barre**, et le fil n'a **ni en-tête de 64 dp ni pastille « Onyx »** avant la première bulle. C'est la comparaison directe avec `img_2d4d02372500.jpg`. |
| `after-queue-light.png` | **Thème clair**, file d'attente **non vide** (« 3 messages waiting », trois rangées « Waiting ») : le bouton « Jump to latest » **n'est visible nulle part** — il ne recouvre donc ni le bandeau, ni les rangées en attente, ni le composeur. |
| `after-scout-chat.png`, `after-queue.png` | Les mêmes deux vues en **thème sombre**. |
| `orb-light-idle-1..3.png` | L'orbe au repos (« READY ») en thème clair, à trois instants de l'animation : l'aura est un halo **circulaire** qui s'éteint avant le bord — plus de pavé à bords droits. |
| `orb-listening-1..3.png`, `orb-idle-1..3.png` | L'orbe à l'écoute (« LISTENING ») en thème sombre, à plusieurs instants. |
| `before-launch.png` | Une conversation simple (« Kitchen remodel ») : la barre porte le visage de Onyx (le repli quand aucun bot n'est lié) et le fil reste sans en-tête. |
| `conversations.before.json` | L'état de l'appareil avant l'injection (restauré après). |

Comparaison avec la capture du propriétaire (`img_2d4d02372500.jpg` et `img_ee86607826b5.jpg`) :

- **Avant** : un visage de 64 dp et une pastille « Onyx » en **tête du fil**, qui défilaient avec
  les messages ; le bouton « Aller au dernier message » à 84 dp fixes **par-dessus** le bandeau
  « 4 messages en attente » et la dernière bulle.
- **Après** : le visage est dans la barre (fixe), le fil commence directement par la première bulle,
  et le bouton « Jump to latest » (quand il est offert) est ancré à la hauteur **mesurée** du
  composeur — donc au-dessus du bandeau — ou retiré quand la file est non vide.

## Ce que ces captures **ne** montrent pas

Le visage de **scout** dans la barre : sur cet émulateur l'app n'a pas d'identifiants Dashboard
(`serveBaseUrl` / `serveUsername`), donc le roster des bots n'est pas chargé et
`botOfConversation` retombe sur `null` → Onyx (le repli normal d'une conversation sans bot lié).
Le rattachement « scout » lui-même est tenu par les tests unitaires (`ThreadSpeakerTest`) :
- le chat serveur du bot (`BotProfile.chatId`) lie la conversation ;
- le titre posé par `botChatToOpen` lie **encore après le premier tour** (c'était le défaut : le
  titre ne comptait que tant que `serverSessionId == null`) ;
- un titre ambigu (deux profils du même libellé) ne rattache personne.

La boucle Larsen n'est pas capturable ici : l'émulateur n'a pas de micro. Elle est tenue par
`HalfDuplexTest` (la réponse lue qui revient est jetée, la garde vaut aussi par-dessus la lecture)
et par le branchement vérifié dans `SoloVoice2Test`.

## Comment rejouer

- `drive.py` — démarre l'app, ouvre la liste, ouvre la conversation **scout**, capture.
- `queue2.py` — coupe le réseau, injecte une file de trois messages en attente dans
  `files/conversations.json`, capture.
- `light.py` — les deux vues en thème clair (`cmd uimode night no`).
- `orb.py` — l'écran vocal et ses captures de l'orbe.

Le bandeau rouge « Invalid URL port » sur les captures de file vient de la configuration de cet
émulateur (une `baseUrl` enregistrée par un autre lot : `http://10.0.2.2:8650 hermes-agent`, un
espace dans l'URL) — sans rapport avec ces changements.
