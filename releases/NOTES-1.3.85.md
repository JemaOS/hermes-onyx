# Hermes Onyx 1.3.85

Renommer un bot ne doit renommer que lui. C'est le contraire qui se passait.

## Le défaut

« J'ai changé le nom de ce bot, ça change tous les bots coders le nom aussi. »

Le titre d'une conversation de bot **est** une copie du nom de ce bot — c'est ce qui la rattache à
lui quand le serveur ne donne pas de session canonique. La règle de renommage s'appuyait sur cette
copie : *toute* conversation dont le titre valait l'ancien nom suivait. Deux bots au même libellé,
ou les fils d'un bot écrits sous le nom d'un autre, partaient donc ensemble.

## Ce qui change

**Le bot se reconnaît par son identité, plus par son libellé.**

| Ce qui désigne le bot | Avant | Maintenant |
|---|---|---|
| son profil (`botProfile`) | oui | oui |
| la session que le serveur lui donne | non | oui — c'est la seule preuve pour les fils écrits avant `botProfile` |
| son ancien titre | **oui** | non |

Un fil qui ne peut pas être prouvé comme appartenant à ce bot n'est pas touché.

**Et un fil que vous avez nommé vous-même garde son nom.** « Refonte du parser » reste « Refonte du
parser » : le renommage d'un bot ne réécrit que les titres qui n'étaient rien d'autre que son nom.

## Ce qui était déjà écrit

Corriger la règle ne nettoie pas les dégâts : sur le téléphone, les fils salis portaient déjà le
nouveau nom. La liste les remet d'aplomb à l'affichage, sans rien réécrire sur le disque :

- le titre est le libellé **vivant** du bot de la conversation, lu sur le roster ;
- un titre qui est le libellé d'un **autre** bot est un dégât collatéral → il reprend le nom de son
  propre bot ;
- tout autre titre est un choix du propriétaire → il n'est pas touché.

Rien n'est deviné quand le roster est absent : la ligne garde alors son titre enregistré.

## Au passage

Le bouton **Refuser** de la carte de prise en main dessinait son contour à 30 % d'encre —
au-dessus du plafond de 12 % que la maison tient pour tous les traits (`OnyxEdgeLockTest` le
refusait). Il porte maintenant le jeton.

## Vérifié

- `BotRenamePropagationTest` : **6 tests**, dont 4 nouveaux — deux bots au même libellé, un fil d'un
  autre profil, un fil nommé par le propriétaire.
- `SidebarTitleTest` : **6 tests** sur le calcul de titre (dégât réparé, titre choisi intact, profil
  inconnu, roster absent).
- `:app:testDebugUnitTest` : **2317 tests, 0 échec** ; `:core:testDebugUnitTest`, `OnyxEdgeLockTest`
  verts.
- `:app:assembleRelease`, `:wear:assembleRelease`, `:auto:assembleRelease` : trois APK signés
  (`apksigner verify` OK, même clé debug que les versions précédentes).

**Non vérifié** : rien de tout cela n'a été exercé sur un téléphone.