# Défilement libre pendant que l'agent réfléchit

Défaut signalé deux fois par le propriétaire : « quand il travaille en réflexion je ne peux même
pas descendre » ; « quand l'IA réfléchit je peux pas remonter le fil de la discussion, ça me
bloque ». La vue se recollait en bas à chaque jeton de raisonnement, à chaque ligne d'outil, à
chaque battement du compteur de durée.

## Ce qui a changé

`ScrollFollow` (pur, sans Compose) porte la décision, comme le modèle :

1. un doigt posé sur la liste **détache** le suivi **tout de suite** — y compris quand la vue est
   encore collée en bas (c'est la règle qui manquait : le détachement attendait que la *position*
   quitte le bas, or le suivi venait justement d'y ramener la vue avant qu'on ait pu s'éloigner) ;
2. au repos, le suivi se **ré-attache** si le bas est à l'écran, à **120 points** près
   (le seuil du modèle) — un geste en cours ne ré-attache jamais ;
3. le contenu qui grandit ne va en bas que **si l'on suit**.

« Aller au dernier message » re-attache le suivi ; le bouton s'offre sur `!following && !atBottom`,
comme le modèle ; une question de la personne ramène en bas. Même règle pour le fil d'une salle
de bots, dont l'ancien effet *animait* vers le bas à chaque morceau de texte.

## Mesure (appareil, APK 1.0.0+288)

Question posée par un simple partage de texte (chemin normal, aucun drapeau), flux lent servi par
`serve_reflexion.py` : la réflexion s'allonge ~53 s, la réponse s'écrit ensuite. Après un
glissement **lent** vers le haut (1,2 s — un geste rapide lance la liste et porte bien plus loin
que voulu), on relève l'arbre d'accessibilité trois fois, à 9 s et 20 s d'intervalle, alors que le
contenu continue de grandir :

| relevé | 1er rang de la liste | bulle de la question | « Jump to latest » | tour en cours à l'écran |
|---|---|---|---|---|
| T0 | 413 px | 1858 px | oui | oui |
| T+9 s | 413 px | 1858 px | oui | oui |
| T+20 s | 413 px | 1858 px | oui | oui |

- **26 lignes** présentes aux deux relevés, **pire écart 0 px** (T0/T+9 s *et* T0/T+20 s).
- Pendant ces 20 s, le compteur de durée est passé de **26,0 s à 38,0 s** et le texte du panneau
  « Thinking » a continué d'avancer : le contenu grandissait bien sous la position de lecture, qui
  n'a pas bougé d'un pixel.
- Le bouton « Jump to latest » était visible aux trois relevés (`t0-remonte.png`,
  `t9-croissance.png`, `t20-croissance.png`).
- L'appui sur le bouton ramène au bas **et ré-attache** le suivi : le bouton disparaît, le tour en
  cours se lit jusqu'à ses dix lignes d'outil (`tap-dernier-message.png`).

Chiffres bruts : `mesure.json`.

## Rejouer

```bash
systemd-run --user --unit=onyx-scroll-mock python3 qa/20261026-scroll-libre/serve_reflexion.py 8791
python3 qa/20261026-scroll-libre/inject.py http://10.0.2.2:8791   # pose le serveur (appareil root)
python3 qa/20261026-scroll-libre/capture.py                       # mesure ; sort 0 si la position tient
```

`emulator-5572` (AVD `onyx-gv`, propre au lot) — à tuer en fin de lot.

## Restes

- Les lignes d'outil ne sont visibles dans la capture qu'**après** l'appui sur « Jump to latest » :
  au moment où l'on remonte lire, le pli des outils est juste sous le bord bas de l'écran. Le
  mécanisme est le même (rien ne bouge tant qu'on est détaché), mais la capture des lignes qui
  s'ajoutent pendant qu'on lit n'est pas dans ces images.
- La capture avant correctif (APK 1.0.0+287) n'a pas été refaite : le défaut n'est prouvé que par
  le test qui échouait avant (`ScrollFollowTest`, 7 échecs sur 12 avant le correctif) et par la
  règle elle-même.

## Le test qui échouait avant

`qa/20261026-scroll-libre/tests-avant-correctif.log` : `ScrollFollowTest` et
`ChatScrollFollowTest` joués **avant** le correctif (règle retirée : le suivi se ré-attachait dès
que la vue lisait « au bas », et rien ne branchait le premier doigt) — **12 tests, 7 échecs**. Le
cœur : `le raisonnement qui grandit pendant le geste ne ramene pas la vue en bas` échoue, parce que
`atRest(scrolling = true, atBottom = true, following = false)` rendait `true`.
