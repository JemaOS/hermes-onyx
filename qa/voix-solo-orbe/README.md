# Voix solo: le carré d'arrêt à la taille du modèle, et l'orbe à halo unique

Dernier écran « partiel » du relevé visuel 2.1. Deux écarts, corrigés et mesurés sur l'APK de
release comme sur celui de debug (pose injectée).

## Ce qui a été corrigé

**1. Le carré du bouton d'arrêt faisait la moitié de celui du modèle.**

Le carré blanc dans le bouton rouge, mesuré au même seuil sur les deux images :

| source | disque rouge | carré blanc | carré / disque |
|---|---|---|---|
| modèle, voix solo à l'écoute | 80 px | 22 px | **0,275** |
| modèle, scène de groupe qui parle | 80 px | 21 px | **0,263** |
| modèle, scène de groupe | 69 px | 18 px | **0,261** |
| Onyx 274, voix solo à l'écoute (avant) | 158 px | 21 px | **0,133** |
| Onyx 276, voix solo à l'écoute (après) | 158 px | 42 px | **0,266** |
| Onyx 276, thème clair (après) | 158 px | 42 px | **0,266** |

La règle du glyphe ne bouge pas — la source du modèle la donne telle quelle
(`solIsWorking ? "stop.fill" : "xmark"`) : le carré tant que quelque chose tourne (écoute,
réflexion, lecture, réponse qui s'écrit), la croix au repos, avec le même libellé. Seule la
**taille** changeait: le glyphe embarqué ne remplit que 10/24 de sa boîte, et la boîte faisait 20
points pour un bouton de 60. Elle en fait 39 (`VoiceStopGlyph`), soit un carré de 16 points —
0,27 du bouton, la part du modèle. Même centrage (dx +0,5 px), même blanc pur. Les deux
captures de modèle qui montrent le bouton rouge sont toutes en activité (carré) : aucune ne
montre l'état au repos.

**2. L'orbe de la voix solo n'avait pas d'aura: trois cercles nets sur le noir.**

`OrbFace` pose l'aura **avant** le choix de la face — un seul halo diffus dans la couleur de la
phase, 297 pt + la voix à l'écoute, 216 pt au repos, `startRadius: 66`, `endRadius: 143`/`110`.
La scène d'une salle le portait ; la voix solo, non. L'aura est maintenant posée sur les deux
faces de la voix solo (`VoiceOrb` et `OrbPictureFace`), avec ses nombres d'origine et le plateau
de 66 points (sans lui, le dégradé s'éteignait avant le bord du disque et ne laissait qu'un
liseré). Elle est découpée au cercle : jamais de bord carré.

## Captures

`avant/` — Onyx 1.0.0+274 (`base.apk` sha256 `4caf5d25…`), l'APK installé avant ce lot :

- `avant-voix-solo-sombre.png` : voix solo, à l'écoute, thème sombre.

`apres/` — Onyx 1.0.0+276 (`base.apk` sha256 `a618e387…`) :

- `apres-release-sombre.png` : écran vocal solo, repose (« PRÊT »), thème sombre, APK **release**.
- `apres-release-installee.png` : la release installée, l'app revenue sur son fil.
- `apres-debug-sombre.png`, `apres-debug-clair.png` : la pose « à l'écoute », **injectée**
  (`am start … --ez onyx.voiceDemo true`, crochet de debug seulement — l'émulateur n'a pas de
  micro, comme le simulateur du modèle) ; sombre et clair.
- `apres-debug-sombre-police13.png` : la même pose, police 1,3×.
- `apres-debug-sombre-reduce-motion.png` : la même pose, « Supprimer les animations » (les trois
  échelles à 0).

Comparaisons à la **même échelle** (l'app ramenée au px/point du modèle, l'échelle lue sur le
bouton rouge — 60 points des deux côtés) :

- `comparaison-bouton-avant.png` / `comparaison-bouton-apres-sombre.png` /
  `comparaison-bouton-apres-clair.png` : le bouton d'arrêt.
- `comparaison-orbe-avant.png` / `comparaison-orbe-apres-sombre.png` /
  `comparaison-orbe-apres-clair.png` : l'orbe.
- `comparaison-ecran-avant.png` / `comparaison-ecran-apres-sombre.png` : les deux écrans entiers.

**Le modèle n'a pas de capture de cet écran en thème clair**: ses captures de l'écran vocal
sont toutes en sombre. La pose claire se contrôle donc sur la géométrie (le carré fait 0,266 du
bouton, comme en sombre) et sur l'aura, pas sur une image de modèle.

## Écarts qui restent

- L'aura de la voix solo est **bleue et plus serrée** que celle du modèle, qui tire sur le
  gris et porte plus loin : luminance moyenne le long d'un rayon, au-delà du disque (R = rayon du
  disque) —

  | | 0,90 R | 1,05 R | 1,20 R | 1,40 R | 1,60 R |
  |---|---|---|---|---|---|
  | modèle | 69 | 58 | 54 | 47 | 29 |
  | Onyx après | 71 | 93 | 35 | 0 | 0 |

  Le fond du modèle est lui-même à ~23 (pas du noir pur), donc sa lueur utile à 1,40 R est
  ~24 — la nôtre y est à 0. Même famille de halo, portée plus courte. La salle (déjà conforme)
  garde exactement l'aura qu'elle avait : rien n'y a été touché.
- Au repos, le bouton rouge porte la croix du modèle, pas le carré : c'est la règle de sa
  source, et aucune de ses captures ne montre cet état.

## Comment rejouer

```
# témoin de reference et mesure: le bouton rouge large de 60 points des deux cotes
python3 compare2.py            # fabrique les comparaisons a la meme echelle
python3 capture.py avant release        # APK release, au repos, sombre/clair/police 1,3x
python3 capture_injecte.py              # APK debug, pose « a l'ecoute » injectee (ONYX_DEV)
python3 capture_release.py              # APK release, sombre puis clair (par la preference)
```

L'état « à l'écoute » est **injecté** : l'émulateur n'a pas de micro, et la pose est demandée au
lancement. Le drapeau est `--ez onyx.voiceDemo true` (un extra **booléen** : avec `-e`, l'app le
lit comme une chaîne et l'ignore).

Recette : `:core:testDebugUnitTest :app:testDebugUnitTest :app:assembleRelease`, 895 tests verts
(plancher 894), BUILD SUCCESSFUL ; `:app:assembleDebug` pour l'APK des captures injectées.
