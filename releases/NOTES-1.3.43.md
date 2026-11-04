# Release notes — Hermes Onyx `1.3.43`

**Version:** `1.3.43` (`versionCode` = `1175`, written in `gradle.properties`).
**Packages:** phone `onyx-1.3.43-release.apk`; `onyx-auto-1.3.43-release.apk`;
`onyx-wear-1.3.43-release.apk`; checksums in `SHA256SUMS-1.3.43.txt`.
**Test suite:** counted in the XML of the build that produced these packages — 0 failure.

---

## 1. The vault leaves « Capacités » and gets its own place

Reported from the phone: « Coffre de secrets › » sat **above the skills list**, between the tabs and
the skill search — a setting that did not read as a setting, in a screen that lists what the agent
may *use*.

**Three changes, one proof.**

1. **Out of « Capacités »** — the row, its `onOpenVault` parameter and the vault probe are gone from
   `CapabilitiesScreen`. That screen now lists only skills, toolsets and MCP servers.
2. **Into Settings, in its own section** — « Secrets », with the row « Coffre de secrets » and its
   explanation, just above « About » (`SettingsScreen`). Its line only exists if the probe proves this
   server serves the `onyx-vault` plugin: no greyed row, no dead tap.
3. **Onto the icon's long-press menu** — a fourth shortcut next to *Demander*, *Parler* and
   *Prendre une note*, with its own tile (`ic_shortcut_vault`, same rounded square and the same
   accent family), French labels (`values-fr`) and `com.onyx.app.action.VAULT` →
   `ShareInbox.requestVault()`, consumed **once** by the UI, which opens `Route.VAULT` without
   touching the conversation.

**They share a single probe.** `SettingsScreen` asks `ServerProbe` for `ServerCapability.VAULT` and
hands the answer to both: the row, and `VaultShortcut.sync(context, ready)` — which lets the launcher
remove the tile when the capability is not proven, and put it back when it is. One proof, two entries:
the shortcut can never lead to a page its server does not serve.

**The way back** renders Settings (`TableDesRetours`: `VAULT → SETTINGS`), since the vault no longer
opens from « Capacités ».

Locks: `VaultEntryTest` (7 tests — gone from Capabilities, its own section, the shared proof, the
tile, the French labels, the single consumption) and the updated `VaultScreenTest`.

## 2. Still true from `1.3.42` → `1.3.36`

- « Ce que fait coder » / « Horaires de coder »; the NOM field opens on the bot's name;
  « Enregistrer » saves and stays; deleting asks in a centred dialog; **Modèle** opens the real model
  list; the conversation list reads « En cours » (and beats) and « à l'instant ».

## 3. What is not claimed

- **No frame number** and **no on-device check of the launcher menu**: no device on the build
  machine, no emulator binary. The menu itself (`dumpsys shortcut`) is to be read on the phone; what
  is proven here is the contract in the code and in the suites.
- The French catalogue is not claimed complete for the whole app.
