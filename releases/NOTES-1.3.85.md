# Hermes Onyx 1.3.85

Trois défauts signalés sur la carte de prise en main, et un sur le renommage d'un bot. Les quatre
sont corrigés ici, et le refus est maintenant une décision qui **tient**.

## « Refuser » ne se voyait pas

Le bouton existait, peint avec la couleur par défaut du style : sur le fond de la carte, elle se
confondait avec le noir. On lisait « Refuser » comme une salissure à côté du bouton d'accent.

Il porte maintenant **le filet de la maison** (`OnyxEdge.fil`, 12 %) et l'encre du texte — un
contour, une couleur lisible, la même cible tactile que « Prendre la main ». Le verrou du trait
(`OnyxEdgeLockTest`) garde la règle : aucun contour au-dessus du jeton, aucune encre de texte
prise pour une bordure.

## Un refus, ça se retient

Refuser ne servait à rien. La carte tombait, puis :

- le sondage suivant la faisait revenir (l'app oubliait le refus dès que le serveur vidait
  `pending`) ;
- et au tour d'après — un prompt réécrit suffisait — l'agent **rappelait l'outil**, qui publiait
  une demande neuve avec un identifiant neuf. Rien ne reliait cette demande au refus.

Le refus est maintenant retenu **aux deux bouts** :

| Où | Ce qui est retenu | Portée |
|---|---|---|
| Plugin (`lease` → `decline`) | la conversation + la page où l'on a dit non | 30 min ; l'outil répond `declined` sans publier de carte |
| App (`TakeoverDeclines`) | la demande refusée | toute l'application, pas un fil |

Conséquence : refuser une fois suffit. La question ne revient ni dans ce fil, ni dans un autre, ni
au prompt suivant. Un autre site, ou une autre conversation, reste une demande légitime.

## La carte ne suit plus dans les autres conversations

L'app devinait à quel fil la demande appartenait (la conversation vivante passait devant). Le
serveur, lui, **savait** : le tour qui appelle l'outil a une session (`HERMES_SESSION_ID`).

`onyx-browser/health` publie désormais `pending.session`, la conversation qui a demandé la main.
L'app compare ce champ à la session du fil et n'affiche la carte que là. Le rattachement par
conversation vivante ne sert plus qu'aux serveurs qui ne publient pas ce champ.

## Renommer un bot ne renomme plus les autres

Le titre d'une conversation de bot **est** le nom du bot, et c'est par lui qu'elle se rattache à
lui. Mais la règle de report retombait sur le **titre** pour les conversations écrites avant
`botProfile` : deux bots portant le même libellé — ou les fils d'un bot créés sous le nom d'un
autre — partaient ensemble. Renommer un bot changeait le nom des autres.

Désormais :

- seules les conversations **de ce bot** suivent — par son profil, ou par la session que le
  serveur lui donne ;
- un fil que la personne a nommé elle-même garde son nom : le renommage ne réécrit pas ce qu'elle
  a écrit.

## Vérifié

- `:core:testDebugUnitTest` + `:app:testDebugUnitTest` : **4303 tests, 0 échec** (1 ignoré).
- `BotRenamePropagationTest` : 6 cas, dont « une conversation d'un autre bot qui portait le même
  titre ne bouge pas » et « un fil nommé par le propriétaire garde son nom ».
- `selftest.py` du plugin : **tout est vert**, y compris les quatre nouveaux cas du refus retenu
  (retenu, borné à sa page, borné à sa conversation, l'ancre ne crée pas une seconde page) — et
  les trois cas de trame/viewport qui ne suivaient plus le passage au viewport annoncé par l'app.
- `py_compile` sur `server.py`, `tools.py`, `session.py`.

**Non vérifié** : rien de tout cela n'a été exercé sur un appareil. Une prise en main réelle, un
refus réel, et un renommage réel depuis le téléphone restent à faire — c'est prouvé par les tests
JVM et par l'exécution du code du plugin, pas par un geste sur l'écran.