# Sélectionner et copier le texte d'un fil — recette du 26/10/2026

Le propriétaire ne pouvait pas copier un passage d'un message : l'appui long sur le texte ne
sélectionnait rien. Le texte du fil se prend maintenant au doigt comme n'importe quel texte du
système : appui long → un mot, deux poignées pour étendre, et la **barre du système** (Couper /
Copier / Coller / Tout sélectionner), jamais une barre dessinée par l'app.

## Ce que la recette couvre

| capture | ce qu'elle montre |
| --- | --- |
| `20-appui-long-message-agent-<thème>.png` | appui long sur la prose d'un message de l'agent : un mot surligné, deux poignées, la barre du système avec « Copier » |
| `21-selection-etendue-<thème>.png` | la poignée droite tirée : la sélection s'étend sur la fin de la ligne, la barre suit |
| `22-appui-long-message-utilisateur-<thème>.png` | appui long sur un message de l'utilisateur, dans sa bulle |
| `23-appui-long-bloc-de-code-<thème>.png` | appui long dans un bloc de code (le cas d'usage le plus évident) |
| `24-copier-depuis-la-barre-<thème>.png` | « Copier » dans la barre du système |
| `25-barre-du-champ-<thème>.png` | le champ de saisie vide, sa barre avec « Coller » |
| `26-collage-dans-le-champ-<thème>.png` | le mot collé dans le champ : ce qui a été copié arrive bien |
| `30-menu-de-liste-<thème>.png` | l'appui long sur une ligne de la liste ouvre toujours son menu ancré |

Deux thèmes : `clair` et `sombre`. Les captures sont en français (`cmd locale set-app-locales
com.onyx.app --locales fr-FR`), comme le modèle du propriétaire.

## Comment rejouer

Appareil : `emulator-5570` (AVD `onyx-lp`), APK release `1.0.0+288` installée en `install -r`
(même clé que les builds précédents, les données et le serveur configuré survivent).

```bash
export PATH=$PATH:/home/john/android-toolchain/sdk/platform-tools/adb
python3 qa/20261026-selection-texte/inject.py http://10.0.2.2:8787   # le double du Dashboard
python3 qa/20261026-selection-texte/appearance.py dark              # ou light
python3 qa/20261026-selection-texte/capture.py sombre               # captures 20 à 23 du thème
python3 qa/20261026-selection-texte/paste_check.py sombre           # 24, 25, 26 : copier/coller réel
python3 qa/20261026-selection-texte/menu_check.py sombre            # 30 : le menu de la ligne de liste
python3 qa/20261026-selection-texte/scroll_check.py                 # le fil défile toujours sous le doigt
```

Le double (`/tmp/kilo/mock-hermes/serve.py`, port 8787) sert la réponse qui porte les deux cas :
de la prose et un bloc de code ```bash.

## Deux choses que la recette dit honnêtement

- **La barre du système ne propose pas « Couper » ni « Coller » sur un texte qu'on ne peut pas
  modifier.** C'est la règle du système : `Copier` et `Tout sélectionner` (et `Partager`, parce que
  l'app déclare un partage) sur le texte du fil ; `Couper`/`Coller` n'existent que dans un champ de
  saisie — c'est exactement là que la capture `25` les montre, et `26` prouve que le collage a lieu.
  La capture de modèle montre les trois parce que sa barre habille un champ.
- **La barre flottante n'est pas dans l'arbre d'accessibilité** : elle vit dans une autre fenêtre.
  Les seules coordonnées écrites sont donc ses entrées, calculées depuis la géométrie lue dans
  l'arbre (« Copier » à 210 px à gauche et 125 px au-dessus du point d'appui long ; « Coller » sur
  l'axe du champ, 53 px au-dessus de son bord haut), et vérifiées par la capture qui suit.

## Un cas tranché: les lignes de liste

Une ligne dont l'appui long ouvre déjà un menu — la liste des conversations, les brouillons en
file — **ne prend pas la sélection** : le geste sert la ligne. C'est ce que fait le modèle, où
l'appui long d'un message est son menu et où le texte se sélectionne dans une copie à part ;
l'app garde d'ailleurs son entrée « Plus → sélectionner le texte ».
