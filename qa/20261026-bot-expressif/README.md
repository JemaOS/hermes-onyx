# L'expressivité des bots — recette

Ce qu'un état ajoute au visage, les preuves, et ce qui a été mesuré.

## Ce que chaque état porte

| État (humeur) | Yeux | Bouche | Micro-expressions |
|---|---|---|---|
| Attente (`IDLE`) | ceux du bot | celle du bot | **aucune** (la créature du modèle) |
| Travail (`WORKING`) | grands | trait | sourcils un peu bas, paupières basses |
| Écoute (`LISTENING`) | ceux du bot | celle du bot | sourcils **hauts**, regard vers vous |
| Réflexion (`THINKING`) | mi-clos | trait | sourcils **rapprochés**, regard de côté et en haut |
| Succès (`PLEASED`) | joyeux | sourire | sourcils levés |
| Erreur (`ERROR`) | ceux du bot | moue | sourcils **froncés** |

La scène vocale ajoute, par phase : sourcils (hauts à l'écoute, rapprochés en réflexion, animés
pendant la parole), paupières (mi-closes en réflexion), et une **respiration** lente (un cycle
d'environ 4 s) qui est la seule chose qui bouge au repos.

Tous ces nombres sont bornés et de faible amplitude ; le visage ordinaire (repos) ne change pas et
la barre du haut reste immobile.

## Les preuves

| Fichier | Ce qu'il montre |
|---|---|
| `planche-bot-expressif.png` | la planche : les cinq états, la séquence de parole, l'écoute, Reduce Motion, le clignement de la liste |
| `planche-etats.png` | les cinq états côte à côte (planche debug, police 1,3×) |
| `voix-parle-{1,2,3}.png` | la séquence : la bouche qui s'ouvre et se ferme (3 images à ~0,3 s) |
| `voix-ecoute.png` | l'écoute : les visages attentifs de l'orbe |
| `voix-fige-{a,b}.png` | Reduce Motion : deux images à 3 s d'écart, **octet pour octet identiques** (`ImageChops.difference` → `bbox None`) |
| `burst-*.png` + `liste-clignement.png` | la liste des bots : le clignement est **pris sur le fait** (images 06 et 11) |
| `gfxinfo-*.txt`, mesures ci-dessous | le coût |

Les états sont **injectés** pour la capture : la planche vient d'une activité debug
(`BotExpressionsActivity`), la scène vocale de la maquette `onyx.groupVoiceDemo`. Le câblage réel
(quels écrans passent quel état) est vérifié par les tests de source ; le parcours lui-même
(serveur, micro) reste à voir sur une vraie machine configurée.

## Le coût mesuré

Émulateur `emulator-5602`, GPU logiciel (swiftshader), hôte partagé — les valeurs absolues sont donc
gonflées ; ce qui compte est l'**écart**.

| Écran | Coût (processus, % d'un cœur) |
|---|---|
| Liste des bots (6 visages, clignements seuls) | **9,4 %** |
| Écran sans visage animé (accueil) | ~0 % |
| Scène vocale, animation normale (grand visage + 3 petits + orbe/halos) | **90,4 %** |
| Scène vocale, Reduce Motion (figée) | **70,4 %** |

Lectures : la liste de visages **ne coûte presque rien** (elle ne se re-dessine qu'au clignement,
une fois par visage toutes les 4 à 7 s, jamais en continu) ; le coût de la scène vocale est celui de
son propre dessin (orbe, halos, blobs) — figer le visage ne le baisse que de ~2 points, donc les
micro-expressions n'en sont pas la charge. `dumpsys gfxinfo` donne des trames à 150-300 ms sur cet
émulateur logiciel : ce n'est pas une image par seconde de téléphone et cela n'est pas annoncé comme
tel.
