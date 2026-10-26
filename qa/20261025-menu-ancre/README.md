# Le menu d'une rangée de bot: ancré à la rangée, icônes, sous-menu du silence

Design de la **modèle 2.1** (capture `img_ee37b66a0021.jpg`) appliqué au menu d'une rangée de bot
d'Onyx : le panneau n'est plus posé au centre de l'écran, il s'ouvre **contre la rangée touchée**,
avec l'icône à gauche, le chevron des sous-menus à droite, un filet **entre les groupes seulement**,
et le silence en **sous-menu** (« Mute › ») au lieu de quatre lignes en clair. **Aucune option n'est
perdue** : les quatre durées sont dans le sous-menu, plus « Unmute » si le bot est déjà tu.

## Ce que montrent les captures

| Fichier | Contenu |
|---|---|
| `10-menu-ancre-sombre.png` | **Thème sombre** : le menu est ancré sous la rangée **Scout** (celle qu'on a touchée), largeur compacte, coins arrondis, fond translucide, voile léger sur tout l'écran. Lignes dans l'ordre du modèle : Marquer comme non lu, **Mettre en silence ›**, Horaires…, Modifier le bot…, Changer de visage…, Masquer, filet, Réordonner les bots. |
| `11-sous-menu-silence-sombre.png` | **Le sous-menu du silence** : les **quatre durées** (Pendant 1 heure, Pendant 8 heures, Jusqu'à demain, Jusqu'à réactivation), chacune sa cloche barrée. |
| `20-menu-ancre-clair.png`, `21-sous-menu-silence-clair.png` | Les mêmes, **thème clair** : le voile couvre tout l'écran (aucune bande claire), le panneau reste lisible. |
| `30-menu-ancre-police-1,3.png`, `31-sous-menu-silence-police-1,3.png` | **Police système 1,3×** : les lignes grandissent, rien n'est coupé, le panneau reste ancré à la rangée. |
| `40-menu-ancre-deux-panneaux.png`, `41-sous-menu-silence-deux-panneaux.png` | **Deux panneaux** (2156×2344 @402, comme la capture du propriétaire) : le menu s'ancre dans le volet de gauche, et faute de place **le sous-menu s'ouvre à côté du panneau**, à la hauteur de la ligne « Mettre en silence ». |
| `12-menu-avant-silence.png` → `16-reactive.png` | **La chaîne du silence, à l'écran** : le menu, le choix de « Pendant 1 heure », la rangée **tu** (cloche barrée), le sous-menu **déjà tu** — « **Réactiver** », « Silencieux jusqu'à 23:29 », **puis les quatre durées encore** — et le bot réactivé. |
| `50-comparaison-reference.png` | **Côte à côte avec le modèle 2.1**, à la **même échelle** (px par point/dp), chaque panneau encadré. |
| `inject.py`, `capture.py`, `comparaison.py` | De quoi rejouer : la configuration du serveur, le pilotage par ancres d'accessibilité, la comparaison. |

## Appareil, serveur, APK

- AVD **`onyx-pane`**, port **5564** (`emulator-5564`), 1080×2400 @420 → **411 × 914 dp**.
  L'écran large des deux panneaux est posé par `wm size 2156x2344` + `wm density 402` (restaurés
  après).
- Serveur : le **double de Hermes** (`/tmp/kilo/mock-hermes/serve.py`, port 8787) — les six profils
  de la capture du propriétaire (Onyx, Quill, Scout, Fix-it, Weekend hike, Garage project) — posé
  sans passer par les formulaires par `inject.py` (`adb root`, écriture du `DataStore`).
  Langue de l'app forcée en français : `cmd locale set-app-locales com.onyx.app --locales fr-FR`.
- APK **release** `versionName 1.0.0+263`, `versionCode 263` :
  - SHA-256 du fichier construit **et** de l'APK installé sur l'appareil (identiques) :
    `85783fc946acc42a4ebd72edc1d66fb3955fd3e0b50716b905bd89951d06a98a`
    (`releases/app-v1.0.0_263-20261025-2233.apk.sha256`).
  - Les captures viennent de ce build : `capture.py` relit le sha256 de l'APK installé sur
    l'appareil avant chaque passe (il a rendu la même empreinte).

## Mesures (§ demandées)

| Mesure | Modèle 2.1 | Onyx (ce lot) |
|---|---|---|
| Largeur du panneau | 257 px = **250 pt** (profil des pixels) | **280 dp** (`MenuWidth`) |
| Hauteur d'une ligne | ~48 pt (pans entiers) | **44 dp** (`defaultMinSize`, + filet et marge du panneau) |
| Coins | arrondis, ~14 pt | **14 dp** (`MenuCorner`) |
| Icône d'une ligne | ~17 pt | **17 dp** (`MenuIconSize`), teinte d'accent, rouge si destructeur |
| Chevron d'un sous-menu | oui, à droite | **oui** (`Symbols.ChevronRight`, 13 dp) |
| Filets | un seul, entre le groupe des actions et « Reorder Bots » | **un seul** (`groupBreak`, devant Réordonner les bots) |
| Écart entre la rangée et le panneau | ~6 pt | **6 dp** (`MenuGap`), sous la rangée, au-dessus si la place manque |
| Marge au bord de l'écran | ~20 pt à gauche | **10 dp** (`MenuMargin`) |
| Voile | léger, tout l'écran | **`Color.Black.copy(alpha = 0.18f)`**, tout l'écran, se ferme au clic extérieur et à Échap |

## Ce qui reste différent du modèle

1. **La couleur des icônes** : celles du modèle sont monochromes (blanc cassé) ; ici elles
   gardent la **teinte d'accent du thème** (bleu en « Messages »), comme le menu actuel du
   propriétaire — le rouge reste réservé au destructeur.
2. **La ligne de plus** : « Marquer comme non lu » n'apparaît que si le bot a un chat non lu ; la
   capture du modèle ne l'a pas (son bot était lu). C'est le comportement du modèle, pas un
   écart de design.
3. **Pas de flou** derrière le panneau : le fond est une surface translucide (94 %), pas un
   `blur` — Compose n'a pas l'effet de verre du modèle sans surcoût.
4. **Le menu d'une salle de groupe** et celui d'une **tuile** de l'écran Bots sont ancrés de la même
   façon (mêmes fonctions, même sous-menu) ; ils sont couverts par les tests et le source, mais pas
   capturés : le double de serveur ne sert pas de salle de groupe comme telle, et l'écran Bots a
   besoin d'un défilement des Réglages pour être atteint.
5. **`Voice…`** reste absent : aucun écran du port ne choisit la voix d'un bot (`BotVoicePicker`
   n'est pas porté) — inchangé par ce lot.
