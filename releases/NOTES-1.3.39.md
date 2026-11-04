# Release notes — Hermes Onyx `1.3.39`

**Version:** `1.3.39` (`versionCode` = `1171`, written in `gradle.properties`).
**Packages:** phone `onyx-1.3.39-release.apk`; `onyx-auto-1.3.39-release.apk`;
`onyx-wear-1.3.39-release.apk`; checksums in `SHA256SUMS-1.3.39.txt`.
**Test suite:** counted in the XML of the build that produced these packages — 0 failure.

---

## 1. « Supprimer le bot » asks the question where you can see it

Reported from the phone: tapping **Supprimer le bot** threw the page to the bottom and the question
landed under the fold — « je ne vois pas les options, je suis obligé de le faire manuellement ».

- **Cause**: the confirmation was a **block appended at the end of the scrolling column**
  (`BotEditorScreen.kt`). It grew the layout below the last section, so the question and its two
  buttons sat outside the screen, and opening it moved the view instead of showing it.
- **Fix**: the confirmation is now **the app's own dialog** (`OnyxConfirm`, the same panel the
  sidebar and the transcript use): centred, above the screen, fully visible whatever the length of
  the page — and the page no longer moves at all. Destructive action on the left in the danger ink,
  « Annuler » beside it, both translated.
- Locks: `BotDeleteConfirmTest` — the dialog is used with the bot's name and both outcomes, the old
  inline rows are gone (that is the defect), and « Delete » / « Cancel » read « Supprimer » /
  « Annuler ».

## 2. Still true from `1.3.38`

- **Modèle**, in a bot's identity page, opens the real model list (`ModelScope.BOT`), and
  « Identique à l'agent principal » keeps its own row.

## 3. Still true from `1.3.37` and `1.3.36`

- The conversation list's working line reads « En cours » and beats; « now » reads « à l'instant ».
- Settings → Calls and four other settings explanations are in French.

## 4. What is not claimed

- **No frame number** — no device on the build machine, no emulator binary; the protocol is in
  `AUDIT-PERFORMANCE.md`.
- The dialog changes **where** the question is asked, not what deleting a bot does: the call still
  goes through the same `onDelete` (server first, the screen only leaves once it has taken).
