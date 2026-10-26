# ROUTE-AUDIT — les retours de l'app, route par route

**C'est un audit, pas une revue de code**: chaque route a été ouverte dans un émulateur par son
point d'entrée réel, une sous-page a été poussée par-dessus, et le retour a été mesuré — écran
d'arrivée, position de défilement et état relevés **avant** et **après**, chiffres à l'appui.

| | |
|---|---|
| App | **Hermes Onyx** (`com.onyx.app`), phrase d'écran : « Onyx pour Android » |
| APK mesuré (avant) | **1.0.0+287** — `sha256 2d242f4914d8194083b56bd16e710d241235fe8b6305db79fdded0cbca8202ce` |
| APK mesuré (après) | **1.0.0+288** — `sha256 310f2fad03094779132557856f0b05617fb25accca5e3c61e60c6df3c0f4d992` |
| Commit de l'audit | `c3581bc` (worktree neuf détaché de `276182d`, jamais dans `onyx-integration2`) |
| Appareil | `emulator-5600` (AVD `onyx-nav`, API 36, x86_64) — **jamais `emulator-5558`** |
| Une colonne | 1080×2400 à 420 dpi = **411 dp** de large (seuil des deux volets : 600 dp) |
| Deux volets | 800×1200 à 160 dpi = **800 dp** de large, deux panneaux réellement dessinés (capture `shots/Z0-deux-volets.png` : la liste à gauche, la conversation à droite) |
| Serveur | double du Dashboard Hermes `http://10.0.2.2:8787` (six profils, même jeu que les captures du propriétaire), posé par `inject.py` |
| Recette | `:core:testDebugUnitTest :app:testDebugUnitTest :app:assembleRelease --no-daemon --console=plain -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.workers.max=2` |
| Tests | **956** dans les XML, **0 échec** (plancher 949 + **7** tests ajoutés) |

## Bilan

| | |
|---|---|
| Routes dans l'énumération `Route` | **56** |
| Routes avec au moins un point d'entrée relevé dans la source | **56** (aucune orpheline) |
| Combinaisons essayées à l'appareil | **63 cas distincts**, **77 relevés** (les deux campagnes) |
| Cas « une colonne » (APK 1.0.0+287, avant) | 47 relevés — 37 ✅, **6 incohérents** (les vrais défauts), 4 non conclus par la boîte à outils |
| Cas « une colonne » et « deux volets » (APK 1.0.0+288, après) | 30 relevés — 25 ✅, 2 incohérents **artefacts de mesure** (rejoués et verts), 3 releveurs ratés |
| Incohérences trouvées | **8** : 3 bloquantes, 4 gênantes, 1 cosmétique |
| Corrigées | **7** (les 3 bloquantes et les 4 gênantes), chacune avec son test |
| Laissées au propriétaire | **1** (cosmétique : `navTo` pendant la composition sur les états vides) + 1 **choix de conception** |

## Ce que l'audit ne couvre pas

- `uiautomator` se fait tuer par le noyau quand la mémoire de l'appareil est serrée : ses échecs
  apparaissent en `ÉCHEC-ENTRÉE` / `ÉCHEC-RELEVÉ`, jamais en « incohérence de l'app ». Ils sont
  comptés à part et rejoués quand c'est possible (`R1`–`R4`).
- La géométrie deux volets a dû passer par `800×1200 à 160 dpi` (800 dp) : à `2200×2480 à 420 dpi`
  (838 dp), l'arbre d'accessibilité de cet appareil n'est plus relevable de façon fiable
  (`uiautomator` tué). Les deux panneaux sont bien dessinés, cf. `captures-cles/Z0-deux-volets.png`.


## Comment un cas est mesuré

Chaque cas (fichiers `cases-*.json`) décrit une **entrée réelle** (les libellés à toucher depuis la
racine), la sous-page à ouvrir, et ce qui doit être là après le retour. La boîte à outils
(`driver.py`, `harness.py`) ne connaît **aucune coordonnée écrite à la main** : tout passe par des
ancres d'accessibilité (`uiautomator dump`) et des libellés visibles.

Pour chaque cas :

1. l'app **redémarre** (état identique à chaque essai) puis les étapes d'entrée sont jouées ;
2. on **fait défiler** jusqu'à la ligne visée et on relève (a) la signature de l'écran,
   (b) les **bornes** d'une ligne repère (par ex. la ligne qu'on s'apprête à toucher) ;
3. on ouvre la sous-page et on relève sa signature — l'appui doit avoir **changé** d'écran ;
4. on fait **retour** — par le bouton système, par le **geste du bord**, ou par le contrôle de
   l'app, selon le cas ;
5. on relève à nouveau signature et ligne repère, et on exige les trois choses :
   **(a)** l'écran quitté, **(b)** la **même position** (bornes identiques), **(c)** le même état.

Ce qui compte comme une **incohérence** : revenir ailleurs qu'où l'on était, ne pas retrouver la
position, laisser un écran vide, retomber sur les Réglages sans raison, ou laisser l'app se fermer.
Quand la ligne repère existe sur les deux écrans avec un autre rôle (par ex. « Kanban » : ligne des
Réglages avant, onglet de la liste après), la comparaison de position ne dit rien — ces cas sont
signalés « destination juste, position non comparable ».

**Le retour a été essayé des deux façons à chaque fois**: bouton/geste système **et** contrôle de
retour de la page.

# Inventaire — toutes les routes

Les 56 valeurs de l'énumération `Route`, chacune avec **ses points d'entrée réels** relevés dans la
source (le libellé du geste, et la ligne exacte du `navTo`) et **son contrat de retour** (ce que le
contrôle propre de la page fait). Table fabriquée par `report.py` : aucune ligne écrite à la main,
donc aucune route oubliée ni inventée.

