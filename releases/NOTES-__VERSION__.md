# Release notes — Hermes Onyx `__VERSION__`

What this version brings, and **where it is provable**. Each point points back to a commit of the
merge tree, to a file in the sources, or to a measured number. Nothing is claimed here that cannot
be shown.

**Version:** `__VERSION__` (`versionCode` = `__CODE__`, written in `gradle.properties` — the public
tree is the flattened copy of the merge tree, so its history does not carry the number).
**Packages:** phone `Hermes-Onyx-__VERSION__-telephone.apk`; the auto / wear companions carry
`__SIDECAR__` if they were not rebuilt for this version.
**Test suite:** `__TESTS__ tests, 0 failures` — `:core:testDebugUnitTest` + `:app:testDebugUnitTest`.

---

## 1. Material and colours

- **The palette and the surfaces are read from the source, not redrawn.** Six palettes of the
  reference compared field by field: **54 values compared, 54 identical, 0 difference**
  (`PARITY-COLOURS.md`). The palette lock reads **412 sources read, 4 skipped,
  0 colour outside the palette** (commit `3cd0f588`).
- **The dark theme is measured, bar by bar.** The surface scale is laid down in tokens
  (`core/src/main/java/com/onyx/app/ui/design/OnyxSurfaces.kt`, `OnyxSurfaceStyle.kt`) and each
  difference is given a figure (commit `a8c50987`, report `UI-UNIFORMITY-DARK.md`):

  | object | before | after | figure |
  |---|---|---|---|
  | composer | `#121215` on the page `#0A0A0C` | `surfaceHigh` bar `#26262B` + hairline + shadow | **1.058:1 → 1.314:1** ("floating" threshold 1.25:1) |
  | tab bar | same veil | same bar, capsule shape | **1.058:1 → 1.314:1** |
  | active tab | accent at 16 % over the veil | opaque capsule `#33333A` | **1.201:1** against its bar (threshold 1.15); accent ink **5.165:1** (floor 3:1) |
  | actions / attachments sheet | opaque background `#16161A` | `surfaceHigh` + hairline | **1.096:1 → 1.314:1** |
  | thread top bar | glass alone, 22 dp corner | `surfaceHigh` + hairline + shadow, **capsule** | **1.058:1 → 1.314:1** |

- **A single family of shapes and shadows:** corners written in place → `OnyxSpace.PanelCorner`
  (**22 → 26 dp**), four shadow depths → one (**`OnyxShadow.Float`**, 6 dp), zero decorative
  colour in the sheets and menus (commit `a8c50987`).

## 2. Liquid glass — Haze 2.0.1, refraction and welding

- **The glass library moves to Haze 2.0.1** (`dev.chrisbanes.haze` `1.6.10 → 2.0.1`), with its
  **`haze`**, **`haze-blur`** and **`haze-glass`** modules. The glass module brings the
  **refraction**: the sampled content is **displaced** according to the edge profile
  (`RefractionProfile.Edge`), with chromatic dispersion, an edge fringe and a specular highlight
  (`gradle/libs.versions.toml`, `core/build.gradle.kts`, `REPORT-LIQUID-GLASS.md`).
- **The refraction is a measurement, not a promise.** `GlassRefractionAvailable = true` follows the
  actual availability; it requires a runtime shader processor (**API 33+**,
  `GlassRefractionMinSdk`) and a **fallback stays wired** below that — the two do not replace each
  other (`core/src/main/java/com/onyx/app/ui/design/GlassTouch.kt`, `LiquidGlass.kt`).
- **The depth of the pane is composed of four measured pieces**: head light, foot shadow, internal
  thickness fringe and edge reflection, each given a figure and checked by `GlassLuminanceTest`
  (`core/.../design/LiquidGlass.kt`).
- **The welding of the shapes.** The bridge geometry leaves no traces (commit `13033ee5`) and the
  weld profile **shrinks with the distance** (commit `4f414fde`).
- **The glass is visible**: the material behind the pane and the state of the step (commit
  `113ccd21`), R8 keep rules laid down for the work planner (commit `09a618da`,
  report `0e9075ab`).

## 3. What moved in the interface

- **A floating bottom bar** and the reference palette (commit `52d0a4cf`), on a token-based
  rework of the liquid glass (commit `3baf4ca3`).
- **Round buttons, without facets**: the octagons become circles, the shadow is drawn
  (commit `794472c7`).
- **The scroll tracking**: the thread follows if you are at the bottom, with an anchoring margin
  carried by the state (commit `af6f86e6`; `ScrollFollow`, `ChatScrollFollowTest.kt`).
- **Two-way conversation swiping** (`SidebarSwipe`, commit `da32ba08`).
- **The thread organisation** and the Capabilities screen (commit `87bb09fa`).
- **Sub-agents** — an agent's children appear as a task list, with their own transcription, their
  duration and a stop button (commits `5ddc604e`, `170ed926`, `a3912744`, `d831f5f9`, `3df3edc1` ;
  `SubagentListTest.kt`, `ThreadOrganisationTest.kt`).
- **The duplicate "Skills" removed:** the sidebar tile and the Settings row have left the
  interface, the route with them; the "Onyx" group drops to **four** tiles in a 2 × 2 grid,
  and everything the skills family did is **inside** "Capabilities", editor included
  (commit `64d8e756`; `CapabilitiesDoublonRetireTest.kt`).
- **Settings — anatomy:** large-title header, tiles, indented separators, text size slider,
  repaired contrast (commit `99979fb4`).

## 4. Calls, files, widget

- **The incoming call and the quiet hours.** When a decision lands, the phone **rings like a
  call**, full screen above the lock screen (`CallRinger.kt`,
  `IncomingCallActivity.kt`, `CallActionReceiver.kt`); answering puts the conversation on the
  line. The **quiet hours** cut the ringer over the chosen window (`QuietHours`,
  `CallsScreen.kt`). Commit `4cc98475`.
- **TIFF, HEIC and AVIF** on top of the usual formats: in-house TIFF decoding and dedicated
  readers (`FileViewers.kt`, `BoundedImages.kt`, `TiffImageTest.kt`). Commits `363a1cb0`,
  `02f7a517`.
- **The HTML widget repaired:** the animated fallback was dropping the rendering engine — now an
  **immediate** fallback with a held width (commit `b7afc4dd`); the page bridge and its
  measurement are **scripts** again (commit `b523abef`).

## 5. The back navigation

- **The back paths are fixed:** the page you left is no longer lost on the way out of the sidebar
  (commit `f48b2fcd`), and a bench measures the three back paths, before/after (commit `bdb8d2e0`,
  `AUDIT-FEEDBACK.md`, `RetourMatriceTest.kt`).

## 6. What this version does not do

- The glass's **background blur** cannot be judged on an emulator (it renders in software): it has
  to be checked on a real device.
- The **auto** and **wear** companions are only rebuilt if they ship with the release; otherwise
  they stay on their previous version (`__SIDECAR__`), and the install README says so.

---

## Files

| Package | Module | sha256 |
|---|---|---|
| `Hermes-Onyx-__VERSION__-telephone.apk` | app | *see `SHA256SUMS`* |
| `Hermes-Onyx-__SIDECAR__-auto.apk` | auto | *see `SHA256SUMS`* if provided |
| `Hermes-Onyx-__SIDECAR__-wear.apk` | wear | *see `SHA256SUMS`* if provided |

Verify a download:

```bash
curl -LO https://github.com/JemaOS/hermes-onyx/releases/latest/download/SHA256SUMS
sha256sum -c SHA256SUMS
```
