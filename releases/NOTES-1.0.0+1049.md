# Hermes Onyx — 1.0.0+1049

`versionCode 1049`, `versionName 1.0.0+1049` (read with `aapt2 dump badging` on the three packages;
`versionCode 1049` re-read **on the device**, `dumpsys package com.onyx.app`).

The three packages are built from the **integration tree rev 1049** (head `a4d85aae`, 1049 commits,
clean tree: `git status --porcelain` = 0 lines). The sources published on `main` are the **cleaned
copy of that same rev 1049** (`onyx.versionCode=1049`, commit `89e63a4`): a build made **from the
published sources** yields `versionCode='1049' versionName='1.0.0+1049'` on the three modules, and
the suite is green there (**3674 tests, 0 failures**). What changes here is the **screenshot
gallery**, redone on the final build, and the **release**, at number `1.0.0+1049`.

## What changes since 1.0.0+1047

One batch merges (`--no-ff`, zero conflicts), measured on the integration head:

- **`lot/liquide-unifie`** (`567feca1`, 2 files, +266 / -44): the card no longer has an inner
  rectangle — the erase band no longer composes over itself, and the lower band is its mirror
  (`app/src/main/java/com/onyx/app/ui/design/Galerie.kt`, locked by
  `core/src/test/java/com/onyx/app/ui/design/BlocksGlassLockTest.kt`).

Commits **1047 -> 1049**, hence version `1047 -> 1049`; tests **3673 -> 3674**
(`:core` 1702 -> 1703, `:app` 1971 unchanged).

## Screenshot gallery redone on the final build

The 40 files in `docs/screenshots/` (20 dark + 20 `-light` twins) were retaken on the **`+1049`**
package: `versionCode 1049` and `versionName 1.0.0+1049` re-read **on the device** before the
series (`dumpsys package com.onyx.app`), and the `sha256` of the `base.apk` re-read on the device
(`bd8fe440c56dc2e33eb6d95e3bad1a6d48633a801724842acff489bca757d72e`, identical to the local file).
**Hardware rendering**: `Selecting Vulkan device: NVIDIA GeForce RTX 4070 SUPER` in the emulator log,
and `dumpsys SurfaceFlinger` -> `GLES: ... NVIDIA GeForce RTX 4070 SUPER/PCIe/SSE2 ...
OpenGL ES 3.1 (4.5.0 NVIDIA 595.99.02)`. Same screens, same file names as the previous gallery;
status bar removed and the demo server address rewritten (blurred), as before.

## Packages

| package | bytes | sha256 |
|---|---|---|
| `Hermes-Onyx-1.0.0+1049-telephone.apk` | 12 497 884 | `bd8fe440c56dc2e33eb6d95e3bad1a6d48633a801724842acff489bca757d72e` |
| `Hermes-Onyx-1.0.0+1049-auto.apk` | 12 678 664 | `2a82156b36ff121bd084179d59f7f999a4b3074668bf56988f7363e94ad6bc61` |
| `Hermes-Onyx-1.0.0+1049-wear.apk` | 35 541 717 | `3551eb556da66893812018b6adda72316fa7b3af03952876790ae4c7ece7dca6` |

`com.onyx.app` / `com.onyx.app.auto` / `com.onyx.app.wear`. `sha256sum -c SHA256SUMS-1049.txt`:
**3/3 succeeded**. Signature: certificate `CN=Hermes Onyx, OU=Android, O=JemaOS, L=Paris,
ST=Ile-de-France, C=FR`, SHA-256 `bf43641666c36da0e7058547f85b0922c5ed14b5da86a812b08e0e67d1ed4049`
(`apksigner verify --print-certs`) — **the same as the `1.0.0+1047` package**: an in-place update
from `1.0.0+1047` (or `+988`) is normal, with no uninstall.

## Tests

`:core` **1703 tests** (1 skipped) + `:app` **1971 tests** = **3674 tests, 0 failures**, on the
sources of the integration tree rev 1049 (`./gradlew :core:testDebugUnitTest :app:testDebugUnitTest`).
