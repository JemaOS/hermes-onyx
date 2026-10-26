# Recette — écran « Ordinateur » (l'écran du PC en direct, piloté au doigt)

26 octobre 2026. Émulateur dédié `emulator-5594` (AVD `onyx-274`, 1080×2400, `-no-window`),
**APK release** installé — `1.0.0+288`, `sha256 6d7452b7c8625aea3f68a7c858df2893f32060676d265c128537e5bd6110f352`
(build du commit `d084f41`, la recette qui suit).
Le pont tourne sur le PC, à `127.0.0.1:8795` ; l'app l'atteint par `http://10.0.2.2:8795`
(l'hôte vu depuis l'émulateur).

Harnais : `drive.py` (pilotage par ancres d'accessibilité — aucune coordonnée écrite à la main
pour l'app) et `recette.py` (la séquence complète). Les points touchés **dans l'aperçu** sont
donnés en fraction de l'écran du PC, et le harnais refait la même mise à l'échelle que l'écran.

## Ce que la recette montre, exigence par exigence

| Exigence | Preuve | Ce qu'on y lit |
|---|---|---|
| (a) l'écran réel du PC, en direct | `shots/10-apercu.png` | le bureau du PC (VS Code, calculateur, dock) **dans** l'app, en direct ; sous l'image : `The agent has the hand. · click 186,1101 · 3840×2160` |
| (b) un appui sur le téléphone = un clic sur le PC | `shots/pc-1-avant.jpg` → `shots/pc-2-clic.jpg` | le calculateur du PC passe de vide à **7** : l'appui sur « C » puis « 7 » a été livré ; journal : `click 184,962` puis `click 184,1097` |
| (c) la frappe du téléphone arrive sur le PC | `shots/pc-3-frappe.jpg` | le calculateur affiche **742** — le « 42 » tapé au clavier de l'app est arrivé après le 7 ; journal : `type « 42 »` |
| (d) prendre / rendre la main | `shots/11-main.png`, `shots/20-main-rendue.png` | « You have the hand. » puis « The agent has the hand. » ; journal : `takeover` (epoch 5) puis `handback` (epoch 6) |
| (d) approbation **refusée** | `shots/22-approbation.png`, `shots/23-refus.png` | la question « Run this command? / uname -a » puis le refus ; l'app affiche « Refusé : rien n'est parti. (uname -a) » et le journal du pont **n'a aucune ligne `run`** |
| (d) approbation **acceptée** | `shots/24-approbation2.png`, `shots/25-approuve.png`, `shots/pc-4-commande.jpg` | après « Approve », journal : `run_started` / `run_finished` ; la sortie de la commande s'affiche dans l'app et une horloge s'ouvre sur le PC |
| sécurité : jeton obligatoire, écoute locale | journal du pont + `ss` | `token_rejected` pour un jeton faux ; la socket n'écoute que `127.0.0.1:8795` ; `GET /health` ne rend que `{"ok": true}` |
| sécurité : le jeton jamais en clair | `shots/02-rempli.png` | le champ du jeton est masqué en entier ; « Copy the saved token » ne fait que copier ; l'app ne l'affiche nulle part |

## Les deux défauts trouvés par la recette, et corrigés

1. **Coordonnées mises à l'échelle deux fois.** L'app envoyait des pixels de l'écran du PC et le
   pont les agrandissait encore : le clic partait ailleurs (journal : `click 552,2159` pour un
   appui visant `186,963`). Le protocole nomme désormais l'espace (`space: "screen"` pour l'app,
   `view` par défaut pour un client qui envoie l'image telle quelle), et
   `check-bridge.py --space screen` prouve que les deux espaces tombent sur le même pixel.
2. **Menu qui restait ouvert sous la question qu'il venait d'ouvrir.** Chaque entrée du menu
   referme le menu (`showMenu = false`), et un test de câblage le vérifie.

## Le journal du pont

`~/.onyx-computer-bridge/bridge.log`, une ligne JSON par événement. Extraits de la passe :

```
client_connected viewer=… peer=('127.0.0.1', 43888)
takeover        viewer=… epoch=5
action          kind=click x=184 y=962        ← « C » du calculateur
action          kind=click x=184 y=1097       ← « 7 »
action          kind=type  detail=42          ← la frappe du téléphone
handback        viewer=… reason=app epoch=6
```

## Ce qui n'a **pas** pu être montré

- **Le glisser (drag)** : le code et les tests le couvrent (`drag` du protocole, `ComputerGestures`
  classe un geste qui bouge), et le pont l'exécute, mais aucun test de bout en bout au doigt n'a
  été joué — `adb shell input` n'a pas de geste glissé simple d'un seul tenant.
- **La molette** (`Up` / `Down`) : envoyée et acceptée (`scroll`), son effet n'a pas été prouvé sur
  une surface qui défile.
- **Le trousseau de l'émulateur** : il a gardé le jeton une première fois, l'a perdu après une mise
  à jour de l'APK (le magasin efface alors l'entrée devenue illisible). L'app le dit désormais
  (« This phone's keystore wouldn't keep the token: it lasts for this session only ») et garde le
  jeton en mémoire pour la session — mais le cas « Keystore fiable » n'a été vu que sur cet
  émulateur, pas sur un téléphone réel.
- **La prise de main pendant que l'agent pilote** : le pont refuse alors les entrées de l'app
  (`denied not_holder`, vu au journal), mais il n'informe pas le `computer_use` de Hermes — ce
  dernier regarde un bureau de bot, pas cet écran. À dire plutôt qu'à supposer.
