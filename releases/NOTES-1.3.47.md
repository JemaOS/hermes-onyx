# Release notes — Hermes Onyx `1.3.47`

**Version:** `1.3.47` (`versionCode` = `1179`, written in `gradle.properties`).
**Packages:** phone `onyx-1.3.47-release.apk`; `onyx-auto-1.3.47-release.apk`;
`onyx-wear-1.3.47-release.apk`; checksums in `SHA256SUMS-1.3.47.txt`.
**Test suite:** counted in the XML of the build that produced these packages — 0 failure.

---

## 1. The vault appears among the shortcuts

Reported from the phone, on the sidebar's **RACCOURCIS** grid: the vault had its Settings line and
its launcher shortcut, but no tile here.

- `SidebarDestinations.onyxTiles` gains **`VAULT`** — « Secrets vault » (→ « Coffre de secrets »), the
  lock, in the Onyx group.
- It lands in the **library** (« BIBLIOTHÈQUE »), next to *Fichiers du serveur* and *Modèles*, not in
  the default grid: a ninth tile forced on everyone is not a default, and the library is exactly the
  place for a destination you may or may not want. One « + » and it sits beside *Planifiés*,
  *Aperçus*, *Capacités*…
- `paneScreens` is derived from the catalogue, so the tile opens the vault **inside the sidebar**,
  like its neighbours, with `onBack = paneBack`.

**Locks updated with their reason**: the Onyx group is five tiles, the catalogue eleven, the library
lists five destinations, and `SidebarFlatLightTest` counts 11 tiles. The row-geometry test no longer
asserts an even count (five tiles = two full rows and one alone on the left) — it asserts the rule
that keeps that last tile left-aligned instead of stretching it.

## 2. Still true from `1.3.46` → `1.3.36`

- A thread's face does not leak to its siblings (the face lives on the conversation now); two bots
  sharing a `canonical_session` keep two identities; the vault has its own Settings section
  (« Secrets »); « Ce que fait coder » / « Horaires de coder » are French; the NOM field opens on the
  bot's name; « Enregistrer » saves and stays; deleting asks in a centred dialog; **Modèle** opens the
  real model list; the list reads « En cours » (and beats) and « à l'instant ».

## 3. What is not claimed

- **No frame number** and no on-device verification: no device on the build machine, no emulator
  binary.
- This build was made from a clean archive of its commit: the working tree also carries another
  task's in-flight chat refactor, which is **not** part of this release.
