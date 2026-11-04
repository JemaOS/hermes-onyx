# Release notes — Hermes Onyx `1.3.37`

**Version:** `1.3.37` (`versionCode` = `1169`, written in `gradle.properties`).
**Packages:** phone `onyx-1.3.37-release.apk`; `onyx-auto-1.3.37-release.apk`;
`onyx-wear-1.3.37-release.apk`; checksums in `SHA256SUMS-1.3.37.txt`.
**Test suite:** counted in the XML of the build that produced these packages — `:core` +
`:app`, 0 failure.

---

## 1. « Working » and « now » speak French, and the working line moves

Reported from the list of conversations: under the title « coder » the second line said
**« Working »** and the time **« now »**, in English, in a French list.

- **The dictionary already had the words** (« Working » → « En cours », « now » → « à l'instant »,
  both in `OnyxLocalization`). The defect was in the **rendering**: `BlocConversation` drew its
  preview and its time `localize = false` — correct for the preview of a message, which is user
  text, and therefore wrong for the *state* of an agent, which is an app string.
- **The row now carries its activity** (`SidebarRow.activity`, filled from the server's
  `session.active_list`), so the screen can tell a state from a message:
  `localizePreview = row.activity != null`, `localizeMeta = true`
  (`app/src/main/java/com/onyx/app/ui/sidebar/ChatSidebar.kt`,
  `core/src/main/java/com/onyx/app/ui/design/Gallery.kt`).
- **And the working line beats**: « En cours. », « En cours.. », « En cours... », one step every
  420 ms, then round again — the state is written **and** it moves, so it reads as work in progress
  rather than as a label. The step is read inside the line's own scope
  (`WorkingPreviewLine`), so a frame invalidates that text and nothing else: not the card, not the
  list.
- **Under Reduce Motion** the three dots are laid down at once (`WorkingDots.Static`), like every
  other loop in the app.

Locks: `SidebarWorkingLabelTest` (the row carries its activity; the three strings translate; the
screen really branches on them) and `WorkingDotsTest` (one, two, three, then again — including a
negative step, which must not leave the cycle).

## 2. Still true from `1.3.36`

Settings → Calls and four other settings explanations are in French, and `SettingsFooterLocalizationTest`
fails the suite when a settings explanation has no French rendering.

## 3. What is not claimed

- **No frame number.** No device on the build machine, no emulator binary; the performance
  protocol is in `AUDIT-PERFORMANCE.md`.
- The French catalogue is not claimed complete in general: what is locked is the settings screens
  (titles and explanations) and the conversation list's state line.
