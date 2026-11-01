# Hermes Onyx — 1.0.0+1049

`versionCode 1049`, `versionName 1.0.0+1049` (releves au `aapt2 dump badging` sur les trois paquets ;
`versionCode 1049` egalement relu **sur l'appareil**, `dumpsys package com.onyx.app`).

Les trois paquets sont batis sur l'**arbre d'integration rev 1049** (tete `a4d85aae`, 1049 commits,
arbre propre : `git status --porcelain` = 0 ligne). Les sources publiees sur `main` sont **la copie
nettoyee de cette meme rev 1049** (`onyx.versionCode=1049`, commit `89e63a4`) : un build fait **depuis
les sources publiees** rend `versionCode='1049' versionName='1.0.0+1049'` sur les trois modules, et la
suite y est verte (**3674 tests, 0 echec**). Ce qui change ici, ce sont la **galerie d'ecrans**,
refaite sur le build final, et la **release**, au numero `1.0.0+1049`.

## Ce qui change depuis 1.0.0+1047

Un lot fusionne (`--no-ff`, zero conflit), mesure sur la tete d'integration :

- **`lot/liquide-unifie`** (`567feca1`, 2 fichiers, +266 / -44) : la carte n'a plus de rectangle
  interieur — la bande d'effacement ne se compose plus sur elle-meme, et la bande basse est son
  miroir (`app/src/main/java/com/onyx/app/ui/design/Galerie.kt`, verrou
  `core/src/test/java/com/onyx/app/ui/design/BlocsVerreVerrouTest.kt`).

Commits **1047 -> 1049**, donc version `1047 -> 1049` ; tests **3673 -> 3674**
(`:core` 1702 -> 1703, `:app` 1971 inchange).

## Galerie d'ecrans refaite sur le build final

Les 40 fichiers de `docs/screenshots/` (20 sombres + 20 jumelles `-light`) ont ete repris sur le
paquet **`+1049`** : `versionCode 1049` et `versionName 1.0.0+1049` relus **sur l'appareil** avant
la serie (`dumpsys package com.onyx.app`), et le `sha256` du `base.apk` relu sur l'appareil
`bd8fe440c56dc2e33eb6d95e3bad1a6d48633a801724842acff489bca757d72e` (identique au fichier local).
Rendu **materiel** : `Selecting Vulkan device: NVIDIA GeForce RTX 4070 SUPER` au journal de
l'emulateur, et `dumpsys SurfaceFlinger` -> `GLES: ... NVIDIA GeForce RTX 4070 SUPER/PCIe/SSE2 ...
OpenGL ES 3.1 (4.5.0 NVIDIA 595.99.02)`. Memes ecrans, memes noms de fichiers que la galerie
precedente ; barre d'etat retiree et adresse du serveur de demo redigee (floutee), comme avant.

## Paquets

| paquet | octets | sha256 |
|---|---|---|
| `Hermes-Onyx-1.0.0+1049-telephone.apk` | 12 497 884 | `bd8fe440c56dc2e33eb6d95e3bad1a6d48633a801724842acff489bca757d72e` |
| `Hermes-Onyx-1.0.0+1049-auto.apk` | 12 678 664 | `2a82156b36ff121bd084179d59f7f999a4b3074668bf56988f7363e94ad6bc61` |
| `Hermes-Onyx-1.0.0+1049-wear.apk` | 35 541 717 | `3551eb556da66893812018b6adda72316fa7b3af03952876790ae4c7ece7dca6` |

`com.onyx.app` / `com.onyx.app.auto` / `com.onyx.app.wear`. `sha256sum -c SHA256SUMS-1049.txt` :
**3/3 reussis**. Signature : certificat `CN=Hermes Onyx, OU=Android, O=JemaOS, L=Paris,
ST=Ile-de-France, C=FR`, SHA-256 `bf43641666c36da0e7058547f85b0922c5ed14b5da86a812b08e0e67d1ed4049`
(`apksigner verify --print-certs`) — **le meme que le paquet `1.0.0+1047`** : la mise a jour en
place depuis `1.0.0+1047` (ou `+988`) est normale, sans desinstallation.

## Tests

`:core` **1703 tests** (1 ignore) + `:app` **1971 tests** = **3674 tests, 0 echec**, sur les sources
de l'arbre d'integration rev 1049 (`./gradlew :core:testDebugUnitTest :app:testDebugUnitTest`).
