# Release notes — Hermes Onyx `1.3.36`

What this version brings, and **where it is provable**. Each point points back to a commit or to a
file in the sources — nothing here is claimed that cannot be shown.

**Version:** `1.3.36` (`versionCode` = `1168`, written in `gradle.properties`).
**Packages:** phone `onyx-1.3.36-release.apk`; `onyx-auto-1.3.36-release.apk`;
`onyx-wear-1.3.36-release.apk`; checksums in `SHA256SUMS-1.3.36.txt`.
**Test suite:** `4 127 tests, 0 failures` — `:core:testDebugUnitTest` + `:app:testDebugUnitTest`,
counted in the XML of the build that produced these packages.

---

## 1. French, where it was missing

The screen reported from the phone: **Settings → Calls** showed French switches (« Sonner pour une
décision », « Répondre en voix live », « Heures de tranquillité ») under English section titles
(« CALLS FOR AN ANSWER », « TRY IT ») and English explanations.

- **Cause, and it is not a wording problem.** `OnyxFormSection` localizes its `header` and `footer`
  through `OnyxLocalization` (`core/src/main/java/com/onyx/app/ui/design/OnyxForm.kt`); the labels
  had entries, the titles and the four explanations did not — so they passed through unchanged.
- **Calls screen**: the two missing titles, the three explanations, and the quiet-hours sentence,
  which the screen **composes** (it names two times) and which is therefore held by two patterns,
  not by a dictionary key — `OnyxLocalization.kt`, `OnyxFrenchSettings.kt`.
- **And the same defect elsewhere**: four more settings explanations were untranslated — three on
  Connection details (the Dashboard login, the Cloudflare Access token, the custom header) and one
  on Notifications (background alerts). Fixed in the same place.
- **Two locks, because the bug was a silence**: `CallsLocalizationTest` holds the Calls screen —
  its fixed texts, the quiet-hours patterns, and the fact that the screen really writes those keys.
  `SettingsFooterLocalizationTest` reads **every settings screen**, re-joins concatenated literals
  and requires a French rendering for each explanation of three words or more. A new screen with an
  untranslated footer now fails the suite instead of shipping.

## 2. The tool cards of a live reply

- **A row is identified by its call, not by its place** (`d9e9392`): `ToolStepRow` is composed under
  `key(tool.id)`, receives a **single** `onOpen` lambda shared by all rows, and memoises its label
  and its elapsed time. The cards of a reply therefore stay skippable while its text is being
  written — the same complaint as `1.3.35`, one level deeper
  (`app/src/main/java/com/onyx/app/ui/chat/components/MessageRow.kt`).

## 3. The guards followed the shape, they were not loosened

Two source guards named the exact line that `d9e9392` moved (`ThreadOrganisationTest`,
`ToolCallDetailTest`). They now assert the **same invariants** on the new shape: the tool card still
carries the command in monospace and the duration on the right, and a tap still opens the detail of
**its own** call (`onClick = { onOpen(tool.id) }` fed by `onOpenStep`).

## 4. What is not claimed

- **No frame number.** There is no device on the build machine and no emulator binary; the
  performance figures and the `gfxinfo` protocol live in `AUDIT-PERFORMANCE.md`, to be measured on
  the phone.
- The French catalogue is **not** claimed complete in general: what is locked is the settings
  screens' titles and explanations, through `SettingsFooterLocalizationTest`.
