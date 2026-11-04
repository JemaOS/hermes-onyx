# Release notes — Hermes Onyx `1.3.40`

**Version:** `1.3.40` (`versionCode` = `1172`, written in `gradle.properties`).
**Packages:** phone `onyx-1.3.40-release.apk`; `onyx-auto-1.3.40-release.apk`;
`onyx-wear-1.3.40-release.apk`; checksums in `SHA256SUMS-1.3.40.txt`.
**Test suite:** counted in the XML of the build that produced these packages — 0 failure.

---

## 1. « Enregistrer » saves, and stays

Reported from the phone: the top-right **Enregistrer** on a bot's identity page threw the view onto
the bots list — read as « la page de création de bot » — so saving looked like leaving the screen
mid-edit.

- **Cause**: the screen itself called `onSave(title, about, model, shown)`; it was the **parent**
  that appended `navTo(Route.BOTS.name)` after writing the profile, with nothing asking for it
  (`OnyxApp.kt`).
- **Fix**: `onSave` writes and returns — `saveEdit` already stores the profile (server
  `profiles.configure` when the name or description changed, plus the phone's copy) and re-reads the
  roster, so the page and the list both show the new values. **No navigation at all.**
- **And a word of feedback**: « Enregistré » appears next to the action for 1.6 s, then fades —
  otherwise a silent save reads like a dead button. It is translated (`Saved` → « Enregistré »).
- **Creation is unchanged**: `onCreate` still returns to the bots list, because there the screen has
  done its job and the new bot has to appear.
- Lock: `BotSaveStaysTest` — the `onSave` block contains no real `navTo(` call (comments that
  mention it are ignored, the check reads trimmed lines), the creation block still does, the screen
  flashes the confirmation, and the word translates.

## 2. Still true from `1.3.39`, `1.3.38`, `1.3.37`

- Deleting a bot asks its question in a centred dialog, not at the bottom of the scrolling page.
- **Modèle** opens the real model list (`ModelScope.BOT`).
- The conversation list's working line reads « En cours » and beats; « now » reads « à l'instant ».
- Settings → Calls and four other settings explanations are in French.

## 3. What is not claimed

- **No frame number** — no device on the build machine, no emulator binary; the protocol is in
  `AUDIT-PERFORMANCE.md`.
- Saving does **not** close the page, and that is the whole point: leaving is the back arrow's job,
  or the list's.
