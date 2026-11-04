# Verre liquide pour Onyx — rapport du lot

Arbre : `/home/john/onyx-verre-582` (worktree neuf depuis `b2b7a5b9`, branche `fusion/verre-liquide-582`).
Commit livré : **09a618da** (rev 584).

---

## 1. Ce qui est livré

| | |
|---|---|
| APK | `…/kilo/cache/scratch/out/Onyx-verre-liquide-1.0.0+584.apk` |
| `versionName` | **1.0.0+584** (> 580) |
| `sha256` | `6c44988b0447f74ca193faf4bc27102a6651c89a0fa3a8f5156ad844784f0b44` |
| signature | `CN=Onyx` (la clé de release du dépôt) |
| `compileSdk` / `targetSdk` / `minSdk` | 37 / 36 / 26 |
| tests | **2 644, 0 échec, 0 erreur** (recette complète au commit livré, `BUILD SUCCESSFUL`) |

## 2. Étape 1 — la bibliothèque de verre passe en 2.0

`dev.chrisbanes.haze` **1.6.10 → 2.0.1**, et **la 2.0 a découpé ses effets en trois modules** :
`haze` (le cœur), `haze-blur` (le flou), **`haze-glass` (le verre : réfraction, profil d'arête,
dispersion chromatique, liseré d'arête, reflet spéculaire)**. Les trois sont posés en `api` dans
`core` — la source Haze est posée par les écrans du module `app`, le type traverse donc la frontière.

Le saut de version est **un seul mouvement**, parce que la bibliothèque ne laisse pas le choix :
`haze-glass` 2.0.1 réclame Compose 1.12.1, Kotlin 2.4.20, `compileSdk 37` — et Compose 1.12.1 réclame
AGP 9.1, qui réclame Gradle 9.

| | avant | après |
|---|---|---|
| haze | 1.6.10 | **2.0.1** (+ `haze-blur`, `haze-glass`) |
| kotlin | 2.1.20 | **2.4.20** |
| compose (BOM) | 2025.06.01 | **2026.09.00** |
| AGP | 8.11.1 | **9.1.0** |
| gradle | 8.13 | **9.8.2** |
| `compileSdk` | 36 | **37** |
| plugin `org.jetbrains.kotlin.android` | appliqué | **retiré** (Kotlin intégré à AGP 9) |

### Ce que le saut a cassé, et qui est réparé

1. **L'application ne démarrait plus du tout.** `androidx.work:work-runtime:2.7.1` traîne derrière lui
   `androidx.room:room-runtime:2.2.5`, **antérieur aux règles ProGuard que Room livre lui-même** : sa
   base de travail est construite **par réflexion sur le nom de sa classe générée**, et R8 (dont le
   comportement change en AGP 9) ne la trouvait plus. Trace :
   `Failed to create an instance of androidx.work.impl.WorkDatabase`, dans `InitializationProvider`,
   **avant la première image**. Réparé par des règles de conservation explicites dans
   `app/proguard-rules.pro` — et **prouvé** : l'APK 1.0.0+584 s'installe et démarre
   (`topResumedActivity = com.onyx.app/.launcher.FaceMist`, 0 `FATAL EXCEPTION`, PSS 50 Mo).
   *Je n'ai donc **pas** eu à revenir à 1.6.10 : la panne était réparable en deux règles.*
2. **Une erreur de compilation** sur un test de type devenu prouvablement faux
   (`VoiceSession.kt:130`, `is SpeechOutput` sur une classe finale qui n'implémente pas l'interface) :
   la branche morte est retirée, le comportement est identique (`else -> null`).

## 3. Étape 2 — la réfraction et l'arête sur nos surfaces

`liquidGlass` ne connaît plus qu'**un seul chemin de peinture par palier**, choisi par
`glassTier()`, fonction **pure** (donc éprouvée sans appareil) :

| palier | quand | ce qui est peint |
|---|---|---|
| **REFRACTION** | API ≥ 33 **et** flou accepté | `hazeGlass` : le contenu est **déplacé** selon `RefractionProfile.Edge(22 dp)`, dispersion chromatique 0,06, liseré d'arête et reflet spéculaire par le même passage, profondeur sur deux échantillons |
| **BLUR** | API 31–32 (le flou de la plateforme marche, pas le déplacement) | `hazeBlur` : flou, teinte mesurée, bruit, voile de repli — **exactement** ce que la vitre faisait avant, conservé |
| **VEIL** | plateforme qui refuse le flou (sous 12) **ou** aucune source posée | notre voile translucide, plus opaque, liseré d'arête par `drawGlassFinish` — **jamais un trou** |

Le verdict du flou n'est pas réécrit à la main : il est **lu chez la bibliothèque**
(`HazeBlurDefaults.isBlurEnabledByDefault()`), et le `when` est exhaustif — ajouter un palier sans
lui donner de chemin ne compile pas. `GlassRefractionAvailable` est passé de **faux à vrai** (c'était
une mesure, c'est toujours une mesure), et `glassCeiling` suit le **même seuil** que la plateforme.

**« Réduire les animations »** : le verre reste, la lumière se fige — `GlassReducedMotionPolicy.Reduced`
est passée explicitement à Haze, plutôt que d'espérer qu'il lise la même échelle de durée que nous.

**Une source comptée.** Haze 2 a refermé sa liste de zones (`HazeState.areas` est `internal`) : la
vitre ne peut plus demander à l'état s'il y a quelque chose à échantillonner. `GlassSourcePresence`
tient donc le compte à côté de l'état, et `Modifier.onyxHazeSource(state)` l'incrémente — c'est un
état de snapshot, la vitre se rallume d'elle-même quand la source arrive.

## 4. Étape 3 — la fusion des formes

**Tableau des bibliothèques** (clonées dans `/home/john/onyx-verre-582-libs`, licence et dernier
commit relevés à la source) :

| bibliothèque | ce qu'elle fait | licence | API min | dernier commit | taille | intégrée ? |
|---|---|---|---|---|---|---|
| **Haze 2.0.1** `haze`+`haze-blur`+`haze-glass` | cœur + flou + **verre** : réfraction du contenu, dispersion, liseré, reflet piloté ; replis et budget de rendu intégrés | Apache-2.0 | verre complet API 33+, flou 31+, `compileSdk 37` | 2026-09 (2.0.1) | AAR `haze-glass` ~440 Ko | **OUI** (étapes 1 et 2) |
| **Abdullajon1881/LiquidGlass** | Compose + Vues + RN : réfraction d'arête AGSL, **fusion de formes lissée jusqu'à 8 rectangles**, appui gel, **3 paliers 33/12/<12** | Apache-2.0 | minSdk 21 | 2026-06-13 | 11 Mo | **NON** — Maven Central *configuré mais non publié*, et il **remplace** Haze (il capture l'arrière-plan lui-même) |
| **QWEA0/Liquid-Glass-Android** | **Vues** : SDF réel, dispersion physique, reflet piloté par le capteur de gravité, **fusion de deux formes par smooth-min** (`setSecondaryShape`, 33+), repli C++/NEON 24+ | MIT | minSdk 24 | 2026-09-29 | 23 Mo | **NON** — `FrameLayout` de vues ; à mettre dans Compose par `AndroidView`, et sa capture d'arrière-plan entrerait en concurrence avec celle de Haze |
| **Kyant0/AndroidLiquidGlass** | verre liquide Compose Multiplatform (`io.github.kyant0:backdrop`) : flou, réfraction « lens », reflets, ombres, flou progressif ; **Maven Central** | Apache-2.0 | minSdk 21, réfraction AGSL 33+ | 2026-08-26 | 25 Mo | **NON** — remplace Haze, **aucune fusion de formes** |
| **Kashif-E/KMPLiquidGlass** | dérivé déclaré de Kyant0, en KMP ; Maven Central | Apache-2.0 | minSdk 21 | 2026-06-19 | 37 Mo | **NON** — dérivé, mêmes raisons |
| **styropyr0/PrismalAGSL** | Compose sur AGSL/RenderEffect : flou, réfraction d'arête, reflet, dégradation automatique, composants intégrés ; JitPack | MIT | minSdk 25 | 2026-09-30 | 12 Mo | **NON** — remplace Haze, pas de fusion |
| **styropyr0/Prismal** | Vues, OpenGL ES 2.0 : réfraction « physiquement exacte », flou, aberration chromatique, Fresnel, ressort ; JitPack | MIT | minSdk 25 | 2026-10-25 (v1.0.8) | 86 Mo | **NON** — moteur de vues OpenGL, ne se branche pas sur Compose/Haze |
| **Mortd3kay/liquid-glass-android** | flou, distorsion, ombres AGSL ; API 21+ avec repli | **non identifiée** (« NOASSERTION », pas de licence SPDX) | minSdk 21 | 2025-12-08 | 18 Mo | **NON** — licence non identifiable, version expérimentale 0.1.0 |
| **NadeemIqbal/liquid-glass** | surfaces givrées Compose Multiplatform, `Modifier.liquidGlass` + composants | Apache-2.0 | — | 2026-05-20 | 1,5 Mo | **NON** — flou seul, ni réfraction ni fusion |
| *(bonus, trouvé en route)* **skydoves/Cloudy** | flou + verre liquide KMP, GPU avec repli CPU, 1 290 ★ | Apache-2.0 | minSdk 21 | 2026-09-13 | 22 Mo | **NON** — redondant avec Haze, pas de fusion |

**Pourquoi aucune n'est intégrée — la raison est la même pour toutes.** Ces bibliothèques **capturent
elles-mêmes l'arrière-plan** : ce sont des **moteurs de verre**, pas des effets posables. En intégrer
une, c'est faire tourner **deux moteurs** sur les mêmes pixels — deux captures d'arrière-plan par
trame, deux jeux de shaders, deux budgets — et accepter que l'apparence diverge entre les surfaces
qui portent l'un et celles qui portent l'autre. Le seul service qu'elles rendraient et que Haze ne
rend pas est **la fusion des formes** ; c'est donc **cette pièce-là** qui a été écrite.

### La soudure, écrite ici — `core/.../design/GlassWeld.kt`

Deux vitres voisines ne se contentent pas de se toucher : quand elles se rapprochent, une **langue de
matière** les relie — un col dont la largeur suit un `smoothstep`, borné par deux **cubiques
concaves**, posé **derrière** les deux formes qui le recouvrent à moitié (5 dp dans chacune). Le
liseré ne suit que le col, jamais les bords haut et bas, qui sont *dans* les formes — les souligner
dessinerait la couture qu'on cherche à effacer.

Pose : `Modifier.glassWeldBehind(surface, upper, lower)` sur la **pile du bas** de l'écran de
conversation, entre le **compositeur** et la **barre d'onglets** — les deux formes qui se rejoignent
là. Les deux rectangles sont **mesurés** (`boundsInParent`), pas supposés.

**Ce que la soudure ne fait pas, et pourquoi.** Elle relie les formes, elle **ne réfracte pas à
travers le pont** : Haze échantillonne chaque vitre dans **ses** bornes, et une source n'éclaire pas
une troisième surface posée entre les deux. Un pont qui courberait le fil demanderait de le peindre
**dans la même passe** que la vitre — donc un moteur de verre à nous, c'est-à-dire exactement ce que
la bibliothèque candidate apporte par ailleurs. Le choix est assumé : on garde **un** moteur, et on
écrit la seule pièce qu'il n'a pas.

Éprouvé par `GlassWeldTest` : profil nul/plein/lissé (0,5 au milieu), monotonie, absence de pont
quand il n'y a rien à souder, élargissement du col, non-débordement de l'emprise des deux formes.

## 5. Le rendu de l'émulateur — la correction du brief est confirmée

`emulator -gpu host` **fonctionne** sur cette machine, et l'émulateur **utilise bien la carte de
l'hôte**. Deux preuves indépendantes, relevées pendant la session :

* journal de l'émulateur — `emuglConfig_init: vulkan_mode_selected:host gles_mode_selected:host`,
  `Selecting Vulkan device: NVIDIA GeForce RTX 4070 SUPER, Version: 1.4.329`, `glInteropSupported: true` ;
* sur l'appareil — `dumpsys SurfaceFlinger` :
  `GLES: Google (NVIDIA Corporation), Android Emulator OpenGL ES Translator (NVIDIA GeForce RTX 4070
  SUPER/PCIe/SSE2), OpenGL ES 3.1 (4.5.0 NVIDIA 595.99.02)`.

AVD du lot : `onyxverre33` — **cible explicite** (`target=android-33`), **port bas** (`-port 5556`),
**partition 6 G** (5,8 G montés), **second démarrage complet** (`stop` / `start`), Android **13**
(API 33, donc palier REFRACTION). `emulator-5702` n'a pas été touché ; ceux de `5554` et `5564`
non plus. L'émulateur du lot a été **tué à la fin** (`adb emu kill`).

**Ce que je n'ai pas pu obtenir : les captures du verre.** Trois causes, dans l'ordre d'importance :

1. **La carte est partagée.** Trois autres émulateurs tournaient sur la même RTX 4070 pendant la
   session (`onyxappel:5554`, `onyxuniq:5564` — tous deux en `-gpu host` — et `vg-fold:5702`). À
   8,7 Gio / 12,3 Gio occupés, mon émulateur s'est fait **préempter deux fois** :
   `ERROR | DisplaySurfaceGlContextHelper context was preempted by others`, puis `device not found`.
   Je n'ai pas arrêté ceux des autres lots.
2. **La navigation a atterri au mauvais endroit.** Sur les quatre dernières tentatives, les repères
   tapés ont ouvert l'**écran de voix** (la demande de micro s'affiche) puis le **lanceur**, pas
   l'onglet visé : l'app reprend sur l'écran qu'elle veut bien, et mes coordonnées venaient d'un
   relevé fait sur un autre état. Les deux paires « avant / après » obtenues sont donc des captures
   **valides mais du mauvais écran** — je ne les présente pas comme une preuve de verre.
3. **Aucune mesure d'images par seconde n'a été prise** pour la même raison : le pas de mesure
   n'a jamais été atteint sur un écran utile.

**Ce qui reste à faire, précisément, pour fermer la preuve visuelle** — la mécanique est en place et
testée : `qa/verre-liquide/capture.py` (arbre d'accessibilité, zooms 2× et 3× sur la barre du haut,
la pile du bas et la **soudure**, `gfxinfo` avant/après pour les images par seconde, `screenrecord`
puis découpage par `ffmpeg` pour prendre la soudure **pendant** qu'elle se ferme). Il manque
**un émulateur sans concurrence GPU**, et une navigation ancrée sur l'arbre à chaque pas plutôt que
sur des coordonnées relevées une fois.

## 6. Mesures de coût du verre

| | avant | après |
|---|---|---|
| images/seconde, défilement long | non mesuré | **non mesuré** (même cause) |
| mémoire (PSS au repos, sur l'appareil) | — | **50 206 Ko** (mesuré sur l'APK 1.0.0+584) |

**Non mesuré, et pas estimé.** Ce que le code fait pour tenir la cadence, en revanche, est en place et
vérifiable : Haze 2 résout lui-même un budget de rendu (`GlassRenderBudget`, plafond de 4096 px par
calque et 16,7 Mpx retenus, échelle d'entrée dégradée par recherche), le mode allégé existant
(`GlassFluidity`) descend d'un cran sur **mesure** de la charge, et le halo s'éteint de lui-même
quand la cascade tombe. **Si la mesure descend sous ~50 i/s, la portée à réduire est le nombre de
surfaces en REFRACTION** : `glassTier()` est le seul point qui décide, et il se règle sans toucher
aux écrans.

## 7. Replis par version — état

| version | palier | vérifié |
|---|---|---|
| Android 13 / API 33 (émulateur `onyxverre33`, GPU hôte) | REFRACTION | l'app **démarre et tourne** (installée, lancée, PSS relevé). La **réfraction** n'a pas pu être capturée (voir §5) |
| Android 12 / API 31–32 | BLUR | **non vérifié sur appareil** — `glassTier` le fige par test pur ; l'émulateur API 31 a été préparé mais jamais démarré, faute de temps |
| sous 12 (API 26–30) | VEIL | **non vérifié sur appareil** — même raison |

Les trois paliers sont en revanche **figés par les tests** (`les trois paliers separent la
bibliotheque de la plateforme`, `le plafond du verre suit le meme seuil que la plateforme`), et le
repli « jamais un trou » est le comportement d'avant, conservé tel quel.

## 8. Fichiers touchés

* `gradle/libs.versions.toml`, `gradle.properties`, `build.gradle.kts`, `*/build.gradle.kts` — le saut
  de version (§2)
* `core/.../ui/design/LiquidGlass.kt` — paliers, `hazeGlass`/`hazeBlur`, source comptée, `glassCeiling`
* `core/.../ui/design/GlassWeld.kt` — **la soudure** (nouveau)
* `core/.../ui/design/GlassTouch.kt` — `GlassRefractionAvailable` passe à vrai, documents mis à jour
* `core/.../ui/theme/Theme.kt` — le compte des sources posé à la racine
* `app/.../ui/chat/ChatScreen.kt` — source comptée, mesures des deux formes, soudure posée
* `app/proguard-rules.pro` — **les règles qui sauvent le démarrage**
* `core/.../voice/VoiceSession.kt` — la branche prouvablement morte
* tests : `GlassWeldTest` (nouveau), `GlassTouchTest`, `LiquidGlassTest`, `ChatGlassWiringTest`
* `qa/verre-liquide/` — `device.py`, `capture.py`, `capture_min.py`, `parcours.py` (le pilote de capture)
