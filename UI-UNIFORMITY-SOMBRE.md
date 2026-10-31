# Lot « uniformité + sombre » — rapport

Branche `fusion/complete`, worktree `/home/john/onyx-fusion-551`.

Le propriétaire a testé `1.0.0+580` (onze lots fusionnés) et a écrit : « il y a plein d'endroits qui
ne sont pas uniformisés au thème, la version sombre est très moche, pas digne d'un grand designer ».
Ce rapport liste **chaque endroit**, avant → après, avec le chiffre.

---

## 1. LA LISTE — tous les endroits qui ne suivaient pas le thème

Chaque ligne donne : l'objet, **avant** (ce qui était écrit), **après** (ce que la source porte
maintenant), et **le chiffre** qui justifie la correction.

### 1.1 Le sombre qui s'écrase sur lui-même (le reproche principal)

| # | objet | avant | après | chiffre |
|---|---|---|---|---|
| 1 | **compositeur** | vitre seule : voile anthracite du verre, `#121215` sur la page `#0A0A0C` | barreau `OnyxSurfaces.surfaceHigh` peint **sous** la vitre : `#26262B`, filet 1 dp, ombre unique | **1,058:1 → 1,314:1** (seuil « flottant » = 1,25:1) |
| 2 | **barre d'onglets** | même voile : `#121215` | même barreau `#26262B` + filet + ombre, forme **capsule** | **1,058:1 → 1,314:1** |
| 3 | **onglet actif** | `accent.copy(alpha = 0.16f)` sur le voile → bleu sombre | barreau `OnyxSurfaces.capsule` `#33333A` | capsule : **1,201:1 contre sa barre** (seuil 1,15) ; encre d'accent `#7AA5FF` : **5,165:1** (plancher du brief : 3:1) ; libellé : **11,411:1** |
| 4 | **feuille des actions** (menu d'une conversation) | fond opaque `surfaceColor` `#16161A` | `OnyxSurfaces.surfaceHigh` `#26262B` + filet | **1,096:1 → 1,314:1** |
| 5 | **feuille des pièces jointes** | `frostedSheet(tint = theme.surfaceColor)` | `tint = OnyxSurfaces.surfaceHigh(theme)` | **1,096:1 → 1,314:1** |
| 6 | **menu ancré** (`OnyxMenu`) | `frostedBar(tint = theme.surfaceColor)` | `tint = OnyxSurfaces.surfaceHigh` + filet qui suit la forme | **1,096:1 → 1,314:1** |
| 7 | **alerte** (`OnyxAlert`) | `background(theme.surfaceColor, PanelCorner)` | `surfaceHigh` + filet | **1,096:1 → 1,314:1** |
| 8 | **carte « Continuer »** | `.background(theme.surfaceColor, TileCorner)` — **aucun filet** | `Modifier.surfaceRole(SurfaceRole.Card)` : carte `#16161A` **+ le filet** `#FFFFFF` à 12 % | fond 1,096:1, **filet mesuré 1,121:1 sur la page** (avant : 0 → la carte n'avait aucun bord) |
| 9 | **accueil vide** | colonne centrée, `spacedBy(24.dp)`, marges 28 dp, **rien** entre le titre et les cartes | bloc posé en haut (décalage `Loose × 4`), **un filet** sépare la tête de la liste, marges = marge d'écran (16 dp), cartes en cascade | marge **28 → 16 dp** (une seule colonne de départ avec le compositeur) ; filet intercalé : **1,121:1** : la page a une arête |
| 10 | **barre du haut du fil** | verre seul, forme 22 dp, aucun barreau | rôle `SurfaceRole.Bar` : `surfaceHigh` + filet + ombre, **capsule** | **1,058:1 → 1,314:1** ; forme 22 dp → capsule (la même que la barre d'onglets) |

### 1.2 Le « trop de couleur » (feuilles et menus)

| # | objet | avant | après | chiffre |
|---|---|---|---|---|
| 11 | menu d'une conversation : teinte de chaque ligne | 4 familles colorées : accent (Chercher/Renommer/Nouvelle conversation), **succès** (Prendre une note), **avertissement** (Processus), accent (Export/Points de restauration), secondaire (id./écrans) | **encre neutre du thème** pour toutes les familles sauf le danger | vignettes colorées **7 → 1** (l'accent disparaît des lignes : 1 couleur saturée en moins par feuille) |
| 12 | feuille des pièces jointes : vignette de ligne | `RowThumbnail(tint = theme.accent)` → **bleu pour les 4 lignes** | `RowThumbnail(neutral = true)` : gris de la famille des tuiles + encre mesurée | teinte saturée : **4 lignes → 0** ; encre de la vignette mesurée ≥ **3:1** sur le gris |
| 13 | ligne destructive | vignette + libellé rouges | **la seule couleur conservée** : le rouge reste sur la vignette **et** le libellé | c'est un **état**, pas une famille (voir §5) |

### 1.3 Les formes incohérentes d'un écran à l'autre

| # | objet | avant (rayon / bord) | après | chiffre |
|---|---|---|---|---|
| 14 | feuille des actions | coin **22 dp** écrit sur place | `OnyxSpace.PanelCorner` | **22 → 26 dp** |
| 15 | carte du compositeur | `ComposerCardRadius = 22.dp` écrit sur place | `OnyxSpace.PanelCorner` | **22 → 26 dp** |
| 16 | champ de recherche (Capacités, Projets, Compétences) | coin `ControlCorner` (9 dp, coin d'un **petit contrôle**), fond `surfaceColor`, **aucun filet** | `SurfaceRole.Field` : `FieldCorner` + creux + filet | **9 → 14 dp**, filet **0 → 1 dp** |
| 17 | champ du compositeur | coin 22 dp, filet à 50 % d'un séparateur à 7 % | même rôle de champ (filet `hairline`) | 22 → 26 dp (carte) ; deux champs, **une** forme |
| 18 | pastille du contrôle segmenté (sombre) | `Color.White.copy(alpha = 0.22f)` sur un voile anthracite — **deux translucides empilés** | `OnyxSurfaces.capsule` (opaque) | la valeur ne dépend plus du contenu derrière |
| 19 | ombre de la pastille segmentée | `2.dp` | `OnyxShadow.Float` = 6 dp | **4 portées d'ombre → 1** |
| 20 | ombre du curseur de taille de texte | `4.dp` | `OnyxShadow.Float` | idem |
| 21 | ombre de la vignette de ligne | `4.dp` | `OnyxShadow.Float` | idem |
| 22 | bordure du bouton discret | `0,5.dp` | `OnyxSpace.Stroke` = 1 dp | **2 épaisseurs de trait → 1** |
| 23 | bordure du curseur de réglage | `0,5.dp` + séparateur de la référence | `OnyxSpace.Stroke` + `hairline` | idem |
| 24 | onglet actif | `RoundedCornerShape(TileCorner)` = 16 dp dans une barre **capsule** | `SurfaceRole.ActiveCapsule.shape` = capsule | un rectangle arrondi dans une capsule → **la même forme** |

### 1.4 Le compositeur : deux cercles concurrents

| # | objet | avant | après | chiffre |
|---|---|---|---|---|
| 25 | cercle vocal | **accent plein** (`theme.accent`) | fond de contrôle `theme.fill` + **filet**, onde en encre **secondaire atténuée** (seuil 3:1 + marge) | éléments accentués dans le compositeur : **2 → 1** |
| 26 | cercle d'envoi au repos | `theme.fill` + `tertiaryText`, **sans filet** → « cassé » | `theme.fill` + **filet** + encre **mesurée** (`dimmed`, ≥ 3,5:1) : la flèche se voit, elle ne crie pas | la flèche avait la couleur la plus pâle du compositeur ; elle est maintenant au-dessus du seuil des icônes |
| 27 | hiérarchie | deux cercles de tailles proches, tous deux accentués, et **le plus lumineux était la dictée** | **une** action accentuée (l'envoi, dès qu'un brouillon existe) ; la dictée est une commande **au repos**, plus sourde que l'envoi | même taille (44 dp) : la différence est l'**accent**, pas la taille |
| 27 bis | champ du compositeur | rayon 22 dp (= celui de la carte) → coins **non emboîtés**, le champ paraissait posé de travers | `interior(PanelCorner, 12 dp)` = 14 dp : les deux courbes partagent leur centre | 26 − 12 = **14 dp**, la règle de l'emboîtement de `OnyxSpace` |

### 1.5 En-tête (F)

| # | écran | avant | après |
|---|---|---|---|
| 28 | fil (conversation) | `liquidGlass(THREAD_TOP_BAR)` : verre seul, forme 22 dp | `SurfaceRole.Bar` : barreau `surfaceHigh`, filet, ombre, **capsule** |
| 29 | salles (GroupRoom), voix, lecteurs, mise en route | **barres écrites à part** (voir §6 « ce qui reste ») | non traitées dans ce lot — listées |

### 1.6 Animation (règles, pas d'impression)

| # | objet | avant | après |
|---|---|---|---|
| 30 | feuille des actions | montée en `tween(220)` **écrit en dur** | `MotionFamily.Open` (ressort 380 / 0,88) — la même que toutes les feuilles |
| 31 | onglet actif | fondu de teinte, aucune image de déplacement | capsule qui **se déplace** (offset animé) et **se déforme** (`Feedback.TabSquash`) |
| 32 | rangées de liste | posées **d'un bloc** | cascade `MotionFamily.Row` (180 ms, pas 40 ms, plafond 160 ms) |
| 33 | orbe vocale au repos | immobile | respiration `MotionFamily.Orb` (2,6 s), figée sous Reduce Motion |
| 34 | retour au toucher | échelle 0,94, aucune vibration | `Feedback.PressScale` = **0,96** + vibration courte (12 ms) |

---

## 2. LA TABLE DES SURFACES (luminances et écarts)

Imprimée par `OnyxSurfaceLadderTest` — les chiffres du rapport et ceux du verrou sont **le même
calcul**.

| rôle | clair | sombre | luminance (sombre) | marche / page, sombre | marche / page, clair |
|---|---|---|---|---|---|
| **page** | `#F7F7F9` | `#0A0A0C` | 0,003082 | 1,000 | 1,000 |
| creux (champ, piste) | `#F3F3F7` | `#121215` | 0,006154 | **1,058** | 1,034 |
| surface (carte, rangée) | `#FFFFFF` | `#16161A` | 0,008190 | **1,096** | 1,070 |
| **surface haute** (compositeur, barres, feuille, menu) | `#FFFFFF` | `#26262B` | 0,019727 | **1,314** | 1,070 |
| **capsule active** (onglet) | `#E9E9EF` | `#33333A` | 0,033769 | **1,578** | 1,130 |
| tuile (vignette d'icône) | `#F1F1F5` | `#2C2C33` | 0,025759 | **1,427** | 1,053 |

**Filet** : encre `#0A0A1E` à 8 % (clair) / blanc à 12 % (sombre), **1 dp**, une seule épaisseur.
**Ombre** : une seule, **6 dp**, noire à 12 % (clair) / 30 % (sombre).

### 2 bis. Les mêmes chiffres, relevés **sur les captures**

Un jeton peut mentir sur ce qui est peint (la vitre passe par-dessus). `qa/20261028-uniformite-sombre/compare.py`
relit donc la table **au pixel**, sur `shots/` :

| point | sombre | /page | clair | /page |
|---|---|---|---|---|
| page | `#0A0A0C` | 1,000 | `#F7F7F9` | 1,000 |
| carte « Continuer » | `#16161A` | **1,096** | `#FFFFFF` | 1,070 |
| **compositeur** (barreau + vitre) | `#2C2C30` | **1,422** | `#F8F8F9` | 1,008 |
| **capsule active** de l'onglet | `#33333A` | **1,578** | `#E9E9EF` | 1,130 |

Le compositeur mesuré **1,422:1** (et non 1,314:1) : la vitre, posée sur le barreau, ajoute son reflet
de tranche — la marche est donc encore plus franche que la table des jetons ne le promet. En clair il
mesure **1,008:1**, et c'est le **filet** qui fait la séparation : c'est le régime clair, prévu comme
tel.


**Seuils et leur source**

| seuil | valeur | source |
|---|---|---|
| marche ordinaire | **1,09:1** | la référence : sa carte sombre `#16161A` sur sa page `#0A0A0C` = 1,096:1 |
| creux | 1,05:1 | la référence : son creux = 1,058:1 (un creux se sent, il ne se détache pas) |
| surface **flottante** | **1,25:1** | Linear (`popular-web-designs`) : son plus grand barreau `#28282c` = 1,331:1 |
| apparence claire | 1,10:1 | contrainte physique : la page claire est à `L = 0,931` ; le 3ᵉ barreau de la référence = 1,130:1 |
| capsule contre sa barre | 1,15:1 | premier barreau d'un sombre natif (Linear `#08090a` → `#0f1011` = 1,154:1) |
| encre de l'onglet actif | ≥ 3:1 (brief) | mesuré : **5,165:1** pour l'accent-en-encre |
| filet | 1,06:1 | `pro-rules.md` : « border and divider visibility … in both themes » |

---

## 3. LA TABLE DES DURÉES ET DES COURBES

Toutes les valeurs vivent dans `MotionFamily` (une courbe + une durée par famille) ou dans
`Feedback` (les valeurs d'effet). **Aucune valeur en dur dans un écran.** `MotionFamilyTest` refuse
une durée hors bornes, refuse deux familles de même signature, refuse qu'un geste dépasse 250 ms, et
refuse qu'une famille survive à Reduce Motion.

| famille | ce qu'elle anime | courbe | durée | classe | Reduce Motion |
|---|---|---|---|---|---|
| `Open` | feuille du bas (montée) | ressort 380 / **0,88** | 280 ms | ouverture | `snap()` |
| `Dismiss` | sortie de panneau | `EaseIn` | 260 ms | ouverture | `snap()` |
| `Panel` | volet latéral | `EaseInOut` | 320 ms | ouverture | `snap()` |
| `Screen` | transition d'écran | `Standard` | 280 ms | ouverture | `snap()` |
| `Message` | arrivée d'un message | ressort 380 / 0,80 | 440 ms | ouverture | `snap()` |
| **`Row`** | **arrivée d'une rangée de liste** | `EaseOut` | **180 ms** | cascade | `snap()` + délai **0** |
| `Press` | appui (échelle) | ressort 700 / 0,75 | 200 ms | geste | `snap()` |
| **`Tab`** | **sélection d'onglet + déplacement de la capsule** | ressort 500 / **0,82** | 240 ms | geste | `snap()` + déformation nulle |
| `Reasoning` | arrivée du raisonnement | `EaseOut` | 200 ms | geste | `snap()` |
| `Send` | état du bouton d'envoi | ressort 620 / 0,70 | 240 ms | geste | `snap()` |
| **`Orb`** | **respiration de l'orbe au repos** | `EaseInOut` | **2 600 ms** | ambiance | `snap()` |

**Les valeurs d'effet** (`Feedback`)

| constante | valeur | où elle s'applique |
|---|---|---|
| `PressScale` | **0,96** | tout `onyxClickable` de l'app (défaut du modificateur) |
| `PressHapticsMs` | 12 ms | vibration au **posé** du doigt |
| `StaggerMs` | **40 ms** | pas de la cascade d'une liste |
| `StaggerMaxMs` | **160 ms** | plafond : au-delà de 4 rangées, tout part ensemble |
| `TabSquash` | **0,94** | compression de la capsule pendant son déplacement |
| `TabOvershoot` | 0,18 | dépassement du ressort de la capsule |
| `OrbBreath` | **1,04** | amplitude de la respiration |

---

## 4. CE QUE DISENT LES COMPÉTENCES (et où je m'en écarte)

| décision | recommandation | source | retenue ? |
|---|---|---|---|
| **marche de surface en sombre** | « Keep cards/surfaces clearly separated from background with sufficient opacity/elevation » ; ne pas empiler des surfaces trop transparentes qui brouillent la hiérarchie | `ui-ux-pro-max` → `references/pro-rules.md`, *Light/Dark Mode Contrast* | **oui** — seuil 1,09:1 (référence) pour une carte, 1,25:1 pour une surface flottante |
| **deux régimes clair/sombre** | Vercel porte ses cartes claires par `box-shadow: 0 0 0 1px` (« shadow-as-border », « cards feel built, not floating ») ; Linear porte ses surfaces sombres par des barreaux opaques | `popular-web-designs` → `templates/vercel.md`, `templates/linear.app.md` | **oui** — en clair : filet + ombre ; en sombre : luminance + filet |
| **largeur du barreau sombre** | Linear : `#08090a` → `#0f1011` (1,154:1) → `#191a1b` (1,197:1) → `#28282c` (1,331:1) | idem | **oui** — la maison reste dans la fourchette 1,06–1,58 |
| **contraste des composants** | icônes et limites de contrôle ≥ **3:1** ; texte ≥ **4,5:1** ; en sombre, 3:1 réservé au grand texte et aux éléments non textuels | `ui-ux-pro-max` → `references/pro-rules.md`, *Light/Dark Mode Contrast* + `SKILL.md` priorité 1 | **oui** — encre de l'onglet actif 5,165:1 ; libellé 11,411:1 ; encre des cercles mesurée ≥3:1 |
| **cibles tactiles** | ≥ 44 pt référence / **48 dp Android** ; agrandir la cible quand l'icône est plus petite | `ui-ux-pro-max` → `references/pro-rules.md`, *Touch Target Minimum* | **oui** — le compositeur gardait déjà `ComposerRowGeometry` (écart 12 dp ≥ 2 × débordement) ; les rangées de feuille font 52 dp |
| **échelle d'espacement** | « 4/8dp spacing rhythm : consistent 4/8dp spacing system » ; « consistent modular scale » | `ui-ux-pro-max` → `references/pro-rules.md`, *Layout & Spacing* + `ux-guidelines` *Font Size Scale* | **oui** — l'accueil vide passe de 28/10/6 dp à la marge d'écran 16 et aux marches 24/8/2 |
| **une ombre** | « State contrast parity » et « Consistent Icon Sizing » : des tokens, jamais des valeurs par composant | `ui-ux-pro-max` → `references/pro-rules.md` | **oui** — 4 portées → **1** (6 dp) |
| **durées d'animation** | « Micro-interaction timing uses shared tokens » ; « One duration for every transition » = anti-pattern ; cascade web 60 ms/élément, `back.out(1.4)` | `ui-ux-pro-max` → `references/pro-rules.md` *Interaction* + domaine `gsap`, preset *Stagger List* | **oui, avec un écart déclaré** : je prends **40 ms** et non 60 ms — une liste de téléphone est lue plus vite et ses rangées sont plus hautes ; le plafond de 160 ms n'existe pas côté web |
| **retour au toucher** | « Tap feedback : clear pressed feedback within 80-150 ms » ; « pressed-state visuals do not shift layout bounds or cause jitter » | `ui-ux-pro-max` → `references/pro-rules.md`, *Interaction* | **oui** — 0,96 sur un transform (jamais une taille), 12 ms de vibration |
| **Reduce Motion** | « contexte : `matchMedia('(prefers-reduced-motion: reduce)')` … skip non-essential motion and render the final state immediately » ; « Reduced motion supported » | `ui-ux-pro-max` → domaine `gsap` + `pro-rules.md` *Accessibility* | **oui** — `snap()` pour **toutes** les familles, délai de cascade **nul**, déformation **nulle** |
| **pas d'emoji comme icône** | « No Emoji as Structural Icons » | `ui-ux-pro-max` → `references/pro-rules.md` | déjà tenu (jeu d'icônes vectorielles) |
| **une seule couleur saturée** | « no ad-hoc per-screen hardcoded colors » ; accent « used sparingly and intentionally, appearing only on CTAs, active states, and brand elements » | `pro-rules.md` *Visual Quality* + `popular-web-designs` → `linear.app.md` | **oui** — les tuiles de feuille passent en encre neutre |
| **preuve du changement** | « un lot qui remplace des valeurs en dur par des jetons est neutre par construction » ; captures scellées par `sha256` relu **sur l'appareil** ; un diff ~0 est une capture ratée | `visual-refresh-evidence` | **oui** — voir §7 |

**Un écart que j'assume, écrit noir sur blanc.** `visual-refresh-evidence` demande de figer un
« avant » en **littéral** (un « avant » ne se lit pas dans le « après »). Ici, les valeurs d'avant
sont écrites en clair dans les commentaires (`#121215`, `1,058:1`, `0,5 dp`, `22 dp`…) et dans les
messages des assertions, mais **pas** dans un test qui les lirait depuis les jetons vivants. Le
risque est donc limité à la prose : aucune assertion ne peut se mettre à « passer » après coup.

---

## 5. LES EXCEPTIONS DE COULEUR CONSERVÉES (avec la raison)

La règle : **la couleur ne sert qu'à un ÉTAT.** Cinq exceptions, et rien d'autre :

| # | ce qui reste coloré | où | pourquoi c'est un **état**, pas une décoration |
|---|---|---|---|
| 1 | **l'accent** (`#2F6BF5` / encre `#1F56D6` – `#7AA5FF`) | l'unique action principale du compositeur (l'envoi, quand un brouillon existe), l'onglet actif (en encre), le chevron d'une ligne cochée, le curseur | c'est « voici la chose à faire » / « voici où tu es » |
| 2 | **danger** (`#CF222E` – `#F85149`) | la ligne **destructive** d'une feuille (vignette + libellé), un arrêt d'agent en cours | « ça ne se rattrape pas » |
| 3 | **succès** (`#1A7F37` – `#3FB950`) | le **statut** d'un processus qui tourne, la coche d'une étape terminée | « c'est fait » |
| 4 | **avertissement** (`#B76E00` – `#F5A524`) | le voyant d'une passerelle absente, un agent qui parle sans être actif | « attention » |
| 5 | **direct** (la teinte de phase de l'orbe) | l'orbe vocale et la pastille « nouveaux messages » | « ça tourne **maintenant** » |

**Retiré de cette liste par ce lot** : l'accent posé sur les **tuiles** de toutes les rangées d'une
feuille ou d'un menu (il indicait une *famille de libellé*, pas un état), et les teintes succès /
avertissement / secondaire qui servaient à distinguer des **familles d'action** dans le menu d'une
conversation.

**Intouchés, parce qu'ils portent une information et non une décoration** : les palettes de visage
de bot (l'identité d'un bot), la coloration syntaxique (un langage), les couleurs d'un thème choisi
par le lecteur (Paper, Slate, Terminal, Amber, Hermes, Code).

---

## 6. CE QUI RESTE

Non audité dans ce lot, et listé pour qu'il ne diverge pas en silence :

1. **En-têtes d'écran écrits à part** — `GroupChatHeader` (salles), `RoomHeader`, `VoiceHeader` /
   `GroupVoiceHeader` (voix), l'en-tête du compositeur plein écran (`ComposerHeader`), les lecteurs
   (`VideoPlayerScreen`, `AudioPlayerScreen`, `PdfViewerScreen`), la mise en route. Le rôle
   `SurfaceRole.Bar` existe maintenant pour eux : il reste à les y brancher.
2. **`OnyxAnatomy.TileCorner` (10 dp)** — la vignette grise des Réglages reste à 10 dp alors que la
   famille dit `ThumbnailCorner` (13) / `TileCorner` (16). C'est un verrou de parité des formulaires
   (« le même depuis toujours ») : à trancher explicitement.
3. **`GlassBarCorner` (22 dp)** dans `LiquidGlass.kt` n'est plus utilisé par la barre du haut, mais
   la constante reste : à retirer quand le lot « verre liquide » aura atterri.
4. **`sheetBadgeFill` / `sheetBadgeInk` / `SheetBadgeTint` / `TabSelectionTint`** et les constantes de
   teinte de famille (`TabSelectionTint`) : les fonctions restent (elles sont testées) mais la
   feuille ne les appelle plus. À retirer au prochain lot, avec leurs tests.
5. **Rangées de listes hors feuilles** (volet des conversations, Liste des tâches, Cron, Fichiers) :
   la cascade `Modifier.rowArrival(index)` leur est ouverte, elle n'est branchée que sur les deux
   feuilles, le menu, l'alerte et l'accueil vide.
6. **Le rappel de position des pastilles de non-lu** et le badge d'un onglet gardent l'accent : c'est
   un état (du neuf), donc conforme.

---

## 7. HUNKS SUR LES FICHIERS DE VERRE — à signaler au lot « verre liquide / Haze 2.0 »

> **AUCUN.** C'était l'objectif de conception de ce lot.

`LiquidGlass.kt`, `Frost.kt` et `Glass.kt` sont **intacts** : la séparation des surfaces flottantes
est portée par le **barreau peint sous la vitre** (`OnyxSurfaces.surfaceHigh`), et non par le voile
du verre (`GlassTintDark`, `glassTintAlpha`). Deux conséquences pour la fusion :

- **zéro conflit** avec le lot « verre liquide » sur ces trois fichiers ;
- **et une raison de ne pas y toucher** : la marche de luminance devient **déterministe** (elle ne
  dépend ni du contenu qui défile sous la vitre ni de l'opacité que la mesure de lisibilité retient).
  Faire porter la même marche par `GlassTintDark` aurait rendu la séparation dépendante du contenu —
  c'est le défaut même qu'on corrige.

Les seuls fichiers de verre **nommés** dans ce lot le sont en **lecture** (`GlassSurface.shape`,
`glassTintColour`, `frostedBar`, `frostedSheet`).

---

## 8. L'ÉMULATEUR, ET LA DÉGRADATION DÉCLARÉE

Le brief demandait de lancer l'émulateur **avec `-gpu host`** et de le **vérifier dans le journal**.
C'est ce qui a été fait, et c'est écrit là :

```
emuglConfig_init: vulkan_mode_selected:host gles_mode_selected:host
emuglConfig_get_vulkan_hardware_gpu_support_info: Found physical GPU
    'NVIDIA GeForce RTX 4070 SUPER', type: VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU, driverVersion: 595.99
Selecting Vulkan device: NVIDIA GeForce RTX 4070 SUPER, Version: 1.4.329
Graphics Adapter Android Emulator OpenGL ES Translator (NVIDIA GeForce RTX 4070 SUPER/PCIe/SSE2)
```

**`-gpu host` a fonctionné, puis a planté — deux fois.** Le traducteur OpenGL de l'hôte
(`gfxstream/host/gl/glestranslator/gles_v2/gles_v2_imp.cpp`) part en `SIGSEGV` :

* 1ʳᵉ tentative : `glTexParameteri … error null ctx` puis `Main process exited, code=dumped, status=11/SEGV`
  après **35 minutes** (l'app installée, les réglages posés, trois captures prises) ;
* 2ᵉ tentative : même `SIGSEGV` après **2 minutes**.

**Repli, déclaré** : `-gpu swiftshader_indirect` (rendu logiciel) pour la passe définitive. Ce que
cela coûte, et pourquoi ce n'est pas grave **pour ce lot** :

* perdu : le **flou d'arrière-plan réel** et la réfraction de la vitre (le « verre liquide » du lot
  voisin). Une ombre de petite forme peut y être tessellée en polygone ;
* **conservé, et c'est le sujet de ce lot** : toutes les corrections de cette passe sont des
  **barreaux de couleur opaque**, des **filets** et des **rayons** — des aplats que le rendu logiciel
  peint à l'identique. La séparation des surfaces ne dépend **pas** de la vitre, puisqu'elle est
  peinte **sous** elle (c'était le choix de conception de §7). Les six couleurs dominantes relevées
  sur les captures tombent exactement sur les jetons de la table (`#0A0A0C`, `#16161A`, `#33333A`,
  `#F7F7F9`, `#FFFFFF`, `#E9E9EF`), ce qui serait impossible si la vitre décidait de la valeur.

Les deux passes de captures (sombre puis clair) ont été prises **après** le repli, sur le même AVD
(`onyxuniq`, 1080×2400, 420 dpi, partition 6 G), avec `adb root` et l'appareil scellé :

| | |
|---|---|
| `versionName` | **1.0.0+582** (relu sur l'appareil) |
| `sha256` du `base.apk` **sur l'appareil** | `7ac315d6050ce927fa44da661d00b983c1d1fad6be2670b8a18007c92b2915c7` |
| `sha256` du fichier livré | `7ac315d6050ce927fa44da661d00b983c1d1fad6be2670b8a18007c92b2915c7` (identique) |

---

## 9. AVANT → APRÈS, AU PIXEL

`1.0.0+580` (les captures de la fusion finale, `qa/20261028-fusion-finale/captures/`) contre
`1.0.0+582` (celles de ce lot), **même écran, même format** :

| paire | pixels bougés (seuil 3/255) | écart moyen | couleurs dominantes avant → après |
|---|---|---|---|
| accueil vide · **sombre** | **35,76 %** | 17,91 | `#0A0A0C` 43,7 % `#222227` 3,6 % → `#0A0A0C` 43,6 % **`#16161A` 4,1 %** `#202026` 2,1 % |
| accueil vide · clair | 41,96 % | 16,16 | `#F7F7F9` 43,4 % `#FFFFFF` 5,2 % → `#F7F7F9` 43,3 % `#FFFFFF` 4,9 % |
| feuille d'ajout · **sombre** | **98,40 %** | 56,06 | `#0A0A0C` 45,4 % **`#2F6BF5` 8,5 %** → **plus aucun `#2F6BF5`** |
| feuille d'ajout · clair | 99,81 % | 143,59 | `#F7F7F9` 45,1 % **`#2F6BF5` 8,5 %** → **plus aucun `#2F6BF5`** |

La ligne qui compte est la **troisième** : l'accent `#2F6BF5` occupait **8,5 %** des pixels de la
feuille d'ajout — c'étaient les quatre vignettes bleues. Il n'en reste **aucun**. Un diff proche de
zéro aurait été une capture ratée ; ici il est de 36 à 99 %.

