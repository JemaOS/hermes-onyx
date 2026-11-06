# Hermes Onyx 1.3.84

La carte de prise en main ne se refusait pas, elle suivait dans tous les fils, et l'écran
qu'elle ouvrait était flou. Trois défauts, une seule carte.

## Refuser est une réponse

Le plugin ne connaissait que deux gestes : `acquire` et `release`. Ne rien faire était donc la
seule façon de dire non — et ce silence coûtait cher des deux côtés : l'agent attendait son tour
jusqu'à **quinze minutes**, et la carte restait à l'écran pendant ce temps.

`lease` accepte maintenant `decline`. Le refus est dit au serveur, qui :

- retire la demande en attente (`pending` passe à vide, la carte tombe) ;
- réveille l'appel d'outil avec `status: declined` et la consigne de ne pas insister ;
- laisse la page intacte — rien n'a été saisi, l'agent reprend son tour en sachant à quoi s'en
  tenir.

L'app envoie ce refus depuis un bouton **Refuser** posé à côté de « Prendre la main », et retire
la carte dans la seconde sans attendre le prochain sondage.

## Une demande, une conversation

`onyx-browser/health` publie la demande pour **le serveur**, pas pour un fil. L'app la lisait comme
un état global : chaque conversation ouverte dessinait la même carte. On quittait le fil où l'agent
demandait de l'aide, sans avoir rien fait, et l'invitation suivait dans les autres.

La demande est maintenant rattachée à **une** conversation, une seule fois :

| Qui se présente | Qui prend la demande |
|---|---|
| une conversation à l'arrêt, la première | elle la garde |
| ensuite la conversation dont le tour tourne | elle la prend — c'est elle qui a demandé |
| une troisième, plus tard | rien : la demande est déjà à quelqu'un |

Une conversation vivante ne se fait plus dépouiller : sinon la carte sauterait d'un fil à l'autre à
chaque recomposition, ce qui est exactement le défaut qu'on répare.

## La netteté vient de la capture, pas du zoom

Le viewport était figé à `390×844` en DPR 2, soit des trames de **780×1688 px**, étirées par l'app
à la largeur réelle de l'écran (~1080 px) : un agrandissement de 1,4×, et c'est là que le texte
fin devenait mou.

- **L'écran annonce sa taille** (`viewport`), la page prend cette forme : capture **1080×2400** sur
  un écran de 1080, donc plus d'agrandissement.
- `clip.scale` reste à `1.0` — au-delà, Chrome rend une mosaïque ; c'est la taille CSS qui porte la
  finesse.
- L'affichage passe de `FilterQuality.Low` (bilinéaire simple, qui étale les glyphes) à `Medium`.

## Vérifié

- `:core:testDebugUnitTest` + `:app:testDebugUnitTest` : **4301 tests, 0 échec**.
- Nouveau test `TakeoverClaimTest` : la demande ne suit pas dans un autre fil, la conversation
  vivante prend la main une fois, le refus oublie la demande, et le registre ne retient que ce qui
  attend encore.
- `_phone_css({1080, 2400})` exécuté : `(540, 1200, 2.0)` → capture 1080×2400 (avant : 780×1688).
- `py_compile` sur les quatre fichiers du plugin, `apksigner verify` sur les trois APK.

**Non vérifié** : rien de tout cela n'a été exercé sur un appareil — pas de prise en main réelle, pas
de refus réel depuis l'écran. C'est prouvé par les tests JVM et par l'exécution du code du plugin.
