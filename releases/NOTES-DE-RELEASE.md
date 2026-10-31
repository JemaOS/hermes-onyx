# Hermes Onyx — Release notes

**Version `1.0.0+546`** (`versionCode` 546, derived from `git rev-list --count HEAD`).
Ships three artifacts, all built from the same commit of the fusion branch (`fusion/publie-545`, the
only commit that carries `versionCode` 546).

## Artifacts

| File | Module | `applicationId` | Size (bytes) | SHA-256 |
|---|---|---|---|---|
| `Hermes-Onyx-1.0.0+546-telephone.apk` | phone | `com.onyx.app` | 11 910 643 | `a813eea8c41fdf86dc40a168a1e014c51fc920d9bc2351a074ec21229f28eca7` |
| `Hermes-Onyx-1.0.0+546-auto.apk` | Android Auto | `com.onyx.app.auto` | 11 010 000 | `51ec4b9762d218999b5f1f29f8bab1f0ee16d1212244891aef2168048f541a71` |
| `Hermes-Onyx-1.0.0+546-wear.apk` | Wear OS | `com.onyx.app.wear` | 31 173 624 | `d624c19423efd942fb6d115caa3ba7e7be078559ce0dc0f977f2ddacc0de46c3` |

`Hermes-Onyx-1.0.0+546-SHA256SUMS` verifies with `sha256sum -c` (all three: OK).

**Signing** — the three APKs carry a single signer, the same certificate as every previous release
in this line (SHA-256 `fa3364fdb2ca25dd91ce3f7f4731c7b472d46f9af5b11152388a595bf01112cb`), so
`1.0.0+546` installs over `1.0.0+544`/`+545` without an uninstall.

## What is in this version

The build is the third fusion (ten lots, merged in two stages) plus two QA passes on device.

**Side panel (the drawer).**
- The panel is a **full-width overlay** on narrow windows: nothing is left beside it, so the strip
  of conversation that used to show under the veil — read as a second copy of the panel — is gone.
  Square corners at the window edge; the light-theme shadow is drawn *before* the clip.
- **Two tile groups**, in a fixed order: the reference group (Scheduled, Overviews, Capabilities,
  Plugins, Configuration, Archive — 6) then the **Onyx** group (Skills, Files, Models, Kanban,
  Code — 5). Eleven distinct destinations, no duplicates, every tile opens a real screen. The last
  row of the Onyx group holds one tile; it keeps its own column (it does not stretch into a banner).
- A **tile label never breaks inside a word**: labels wrap at spaces, the font size gives way before
  the word does.
- **Search mode** in the panel (focus ring, inner cross to clear, outer cross to leave), with the
  tiles/projects/filters hidden while searching.
- **Graded back**: sheet → search → carried screen → panel, one level at a time, identical for the
  system button and the edge gesture.

**Wide windows.** From 800 dp the panel is **docked** beside the conversation instead of covering
it; the panel starts open and each width keeps its own open/closed memory. A **carried screen**
(a tile opened inside the panel) takes the widened 400–560 dp width so it is not crushed.

**Account sheet.** Opened by the initial round in the footer *and* by the name/address in the
panel head. It shows the **measured** state of the gateway: reachable with the real latency, or
offline with a **real retry countdown** (30 s policy) and the exact reason reported by the probe —
never a raw English code.

**Conversation actions.** The `+` panel opens a bottom sheet (not a floating list) with the server
readings as extra rows.

**Capabilities / MCP.** Exhaustive: add, test, enable, remove, expandable tool lists.

**Appearance and language.** Light and dark themes, French throughout, no Material components.

## Fixes in `1.0.0+546`

1. **A sheet row label is cut at a word boundary, never inside a word.** The row labels of the
   family used the text engine's tail ellipsis, which cuts at the last character that fits: at
   font 1.3× the account sheet showed *"Réessayer la connexion maint…"* (out of *maintenant*) and
   *"Changer ou ajouter une passer…"* (out of *passerelle*), and the status reason ended on
   *"… du Dashboard dan…"*. A word-aware cutter (`fitWords`) now drops whole words before placing
   the ellipsis; a label that fits is left untouched (no gratuitous ellipsis). Measured on device:
   *"Réessayer la connexion …"*, *"Changer ou ajouter une …"*, *"… du Dashboard …"*.

Fixes carried from the QA pass that produced `1.0.0+544`:

2. **A wrapped tile label is no longer clipped.** `SidebarTileCell` set a fixed 44 dp height while
   the label plan allowed two lines; at 1.3× on a 411 dp phone the second line of
   *"Fichiers du serveur"* was cut to 21 px of its 53 px. The height is now a floor
   (`heightIn(min = …)`) and the row takes the height of its tallest tile, so the two tiles of a
   row grow together. Measured: the second line is 53 px, and the row grew from 126 px to 148 px.
3. **The Archive screen's action ink is measured**, not the raw accent (which sat at ≈3.4:1 on a
   near-white panel).
4. **The account sheet's notice ink is measured** the same way (13 themes × 2 appearances).

No screen, no tile, no function and no animation was removed in any of these passes.

## What depends on server routes

These readings are real routes on the Dashboard's JSON-RPC channel; the app renders **only what the
server returns** and says so when it returns nothing.

- **Consumption and cost.** `session.resume` is called first for the **run** identifier, then
  `session.usage` and `session.context_breakdown` are read with it. The panel shows input, output,
  reasoning, cache read/write, cache share, total, API calls, context used/max/percent, what fills
  the window (categories) and the context file manifest — plus the model name.
- **Restore points are disabled server-side.** `rollback.list` answers `enabled: false` for this
  folder, so the panel states *"This server does not keep restore points for this folder."*
  Nothing is offered and nothing is invented.
- **No model price is exposed.** The usage route returns no cost, so *Estimated* and *Actual* both
  show the em-dash and the panel states *"This server gives no price for this conversation; the
  counters above are all it has."* The app never estimates a price of its own.
- **Background processes are empty.** `process.list` answers with an empty list, so the panel
  states *"Nothing is running in the background for this conversation."* — as opposed to the
  "server refused" message, which is a different sentence.
- **Overviews (Insights)** need the dashboard's own readings; when the gateway is not reachable the
  screen says so in French (*"Impossible de charger les aperçus"*) with the reason and a **Try
  again** action, instead of an empty chart.
- A field the server does not send is shown as **“—”**, never as a zero.

## Tests

Recipe run out of the throttled cgroup, in the foreground, with
`ONYX_RELEASE_KEYSTORE=<the repository release keystore>`:

```
:core:testDebugUnitTest :app:testDebugUnitTest :app:assembleRelease
:auto:assembleRelease :wear:assembleRelease
--no-daemon --console=plain
-Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.workers.max=2
```

| Module | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| `:core` | 1173 | 0 | 0 | 0 |
| `:app` | 1286 | 0 | 0 | 0 |
| **Total** | **2459** | **0** | **0** | **0** |

`BUILD SUCCESSFUL`. The regression matrices are all green: `RetourMatriceTest`,
`RetourCoherenceTest`, `SidebarDestinationsTest`, `OnyxSpaceScaleTest`, `GlassShapeTest`,
`MenuIconFamilyGeometryTest`, `TopBarCornerTest`, `BotFaceCoherenceTest`,
`ReferenceNavigationAlignmentTest`, `ArchivedConversationsTest`, `SidebarTileLabelTest`,
`AccountSheetContrastTest`, `SidebarWiringTest`, `ResponsiveLayoutTest`, plus the new
`SheetLabelFitTest`.

## Verified on device

Phone 411 dp, foldable unfolded 862 dp and tablet 1280 dp, in **light and dark**, at font **1.3×**,
with Reduce Motion. Captures and measurements are in `qa/20261028-publication-546/`.

## Still not verifiable here

- **Background blur** of the glass: the emulator renders in software, so only a real phone shows it.
- **Android Auto and Wear**: built and signed, never executed.
- **Animations shorter than 100 ms**: not capturable.
