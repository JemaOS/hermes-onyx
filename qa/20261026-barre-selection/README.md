# La barre de sélection multiple — recette du 26/10/2026

La capture du propriétaire montrait la barre du bas **cassée** : le compte coupé mot par mot sur
quatre lignes (« 4 / élément / s / sélectio / nnés ») dans une colonne étroite à gauche, les actions
groupées collées à la barre de gestes. Le compte n'est plus jamais coupé : il est **mesuré** sous la
police du système, garde une seule ligne, et la barre se replace proprement quand la place manque.

## Ce que fait la barre maintenant

| ligne | contenu |
| --- | --- |
| 1 | le compte des rangées cochées (`4 éléments sélectionnés`), sur **une** ligne |
| 2 | `Tout sélectionner` / `Tout désélectionner` et `Annuler`, alignés au bord droit |
| 3 | `[archiver] Archiver N` au bord **gauche**, `[corbeille] Supprimer N` au bord **droit** |

Un filet sépare la barre de la liste, et l'inset de la barre de navigation lui rend son air : les
actions ne touchent plus le geste du système, dans les deux thèmes.

Quand la place manque, la barre se replie **dans cet ordre** (aucun mot n'est jamais coupé) :

1. le compte en entier s'il tient à côté des deux actions — la barre la plus courte ;
2. sinon le compte prend **sa** ligne, actions dessous ;
3. si même dessus les deux actions ne tiennent pas ensemble, chacune prend sa ligne ;
4. et en dernier recours seulement, le compte se réduit au **nombre** (« 4 »).

## Les mesures du défaut (arbre d'accessibilité, mêmes écrans)

Deux builds, **le même appareil, les mêmes données, les mêmes écrans** : `1.0.0+288` (avant,
sha256 `0eec5d48…`) puis `1.0.0+319` (après, sha256 `2f1d0375…`), relevés dans
`shots/mesures-before.json` et `shots/mesures-after.json` capture par capture.

| état | avant (1.0.0+288) | après (1.0.0+319) |
| --- | --- | --- |
| fermé, 411 dp, police 1,0× | compte **355 × 95 px** (**2 lignes**) ; bas des actions à **2376 px**, soit le bas de la fenêtre — **sur le geste** | compte **996 × 47 px** (**1 ligne**) ; bas des actions à **2292 px**, 84 px au-dessus |
| fermé, 411 dp, police 1,3× | compte **228 × 245 px** (**4 lignes**, mots coupés) | compte **996 × 59 px** (**1 ligne**) |
| déplié, volet de 345 dp, 1,0× | compte **172 × 179 px** (**3 lignes**) | compte **782 × 44 px** (**1 ligne**), toujours à la largeur du volet |

Un compte d'une ligne fait au plus **une** hauteur de texte ; toute hauteur supérieure est une
coupure. C'est la mesure qui sert de preuve ici, pas l'œil. Dans le volet déplié, l'inset du bas
était déjà pris par la colonne — le défaut du geste est celui du **téléphone fermé**.

## Les captures

| fichier | ce qu'il montre |
| --- | --- |
| `shots/before-ferme-clair.png`, `-sombre` | le défaut **avant** : compte sur deux lignes, actions posées sur le geste |
| `shots/before-ferme-clair-police13.png` | le défaut avec la police du système à 1,3× : **quatre** lignes |
| `shots/before-ouvert-clair.png`, `-sombre` | le défaut dans le volet d'un pliable déplié : trois lignes |
| `shots/after-ferme-clair.png`, `-sombre`, `-police13` | la barre refaite, téléphone fermé, deux thèmes, deux polices |
| `shots/after-ferme-*-confirmation.png` | la confirmation `Supprimer 4 conversations ?`, en français |
| `shots/after-ouvert-clair.png`, `-sombre` | la barre dans le volet de gauche d'un pliable déplié : **à la largeur du volet**, jamais étirée sur la fenêtre |
| `shots/mesures-before.json`, `mesures-after.json` | la géométrie relevée dans l'arbre, capture par capture (texte, boîte, version, sha256 de l'APK installée) |
| `shots/comparaison-barre.png` | la planche à **la même échelle** : capture du propriétaire, avant, après (fermé, 1,3×, déplié) |

## Comment rejouer

Appareil : `emulator-5582` (AVD `onyx-fold`, le pliable 8 pouces). Les deux états sont posés par
`wm size` / `wm density` : **déplié** 2156×2344 à 400 dpi (862 dp), **fermé** 1080×2376 à 420 dpi
(411 dp, le téléphone de la capture du propriétaire). **`emulator-5558` n'est pas touché.**

```bash
export PATH=$PATH:/home/john/android-toolchain/sdk/platform-tools
export ONYX_SERIAL=emulator-5582
python3 qa/20261026-barre-selection/pose.py clair     # serveur (double du Dashboard) + 4 conversations
python3 qa/20261026-barre-selection/capture.py before # l'état d'avant (build déjà installé)
python3 qa/20261026-barre-selection/capture.py after  # 5 captures : deux états, deux thèmes, 1,3×
python3 qa/20261026-barre-selection/dialogue.py clair # la confirmation de suppression, en français
python3 qa/20261026-barre-selection/comparaison.py    # la planche à la même échelle
```

`pose.py` écrit le `DataStore` des réglages et `files/conversations.json` par `adb root` (l'APK est
une release, donc `run-as` est refusé) : quatre conversations, exactement l'état de la capture du
propriétaire. `capture.py` lit les ancres dans l'arbre d'accessibilité — aucune coordonnée écrite —
et **vérifie** le compte affiché avant de capturer : une passe ne part jamais sur un état à moitié
posé.

## Ce qui n'est pas prouvé ici

- Le geste **réel** de sélection rangée par rangée : la passe coche « Tout sélectionner » (un appui
  qui coche les quatre d'un coup), parce que quatre appuis successifs pendant que la barre apparaît
  en sélectionnaient deux. L'appui rangée à rangée est bien celui de l'app (`selected ± id`) — vérifié
  à l'œil sur une première passe — mais la recette s'appuie sur l'action groupée.
- `Archiver 4` n'a pas été déclenché jusqu'au bout (il vide la sélection) ; seule la confirmation de
  suppression a été ouverte puis refermée. Le câblage des deux actions est celui d'avant.
