# Release notes — Hermes Onyx `1.3.41`

**Version:** `1.3.41` (`versionCode` = `1173`, written in `gradle.properties`).
**Packages:** phone `onyx-1.3.41-release.apk`; `onyx-auto-1.3.41-release.apk`;
`onyx-wear-1.3.41-release.apk`; checksums in `SHA256SUMS-1.3.41.txt`.
**Test suite:** counted in the XML of the build that produced these packages — 0 failure.

---

## 1. The NOM field shows the bot's name, instead of an empty box

Reported from the phone: on a bot's identity page the **NOM** field was empty — only the grey
placeholder « Scout » showed — while the page itself was titled « coder ». You could not see what you
were editing.

- **Cause**: the field started from `title ?: displayName`. A bot that has neither (its name lives in
  the profile identifier, `coder`) therefore left the box empty, and the page title fell back to
  `BotProfile.label` — the two disagreed.
- **Fix**: the field now starts from **the name the page already shows**,
  `bot?.label(mainName)` (`app/src/main/java/com/onyx/app/ui/bots/BotEditorScreen.kt`), so both
  agree. Typing a new name still works the same, and saving without touching it writes **nothing**
  on the server: `saveEdit` only renames when the title differs from the label.
- A new bot still opens empty, with its placeholder — there is nothing to pre-fill yet.
- Lock: `BotSaveStaysTest` now also holds the pre-filled name (`BotProfile(name = "coder").label()`
  is `coder`, and the field's initial value is the label).

## 2. Still true from `1.3.40`

- « Enregistrer » writes the profile and **stays** on the page, with « Enregistré » flashing beside
  it; « Créer » still returns to the bots list.

## 3. Still true from `1.3.39` → `1.3.36`

- Deleting a bot asks its question in a centred dialog, not at the bottom of the page.
- **Modèle** opens the real model list (`ModelScope.BOT`).
- The conversation list reads « En cours » (and it beats) and « à l'instant ».
- Settings → Calls and four other settings explanations are French.

## 4. What is not claimed

- **No frame number** — no device on the build machine, no emulator binary; the protocol is in
  `AUDIT-PERFORMANCE.md`.
- Pre-filling the name does not rename anything by itself: the write still happens on **Enregistrer**,
  and only if the name actually changed.
