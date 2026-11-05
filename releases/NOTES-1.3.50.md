# Release notes — Hermes Onyx `1.3.50`

**Version:** `1.3.50` (`versionCode` = `1182`, written in `gradle.properties`).
**Packages:** phone `onyx-1.3.50-release.apk`; `onyx-auto-1.3.50-release.apk`;
`onyx-wear-1.3.50-release.apk`; checksums in `SHA256SUMS-1.3.50.txt`.
**Test suite:** counted in the XML of the build that produced these packages — 0 failure.

---

## 1. « WHAT CODER DOES » speaks French, whatever the bot is called

Reported from the phone, on **Nouvel horaire**: every label of the screen was French (NOM, QUAND,
Répéter, En semaine, À, the explanation under the field) but the section header still read
**WHAT CODER DOES**.

- **What the first fix (1.3.42) did, and why it was not enough.** The header is the sentence
  « What *coder* does » — the bot's name is *inside* it. 1.3.42 taught the dictionary a pattern
  (`^What (.+) does$`) that recognises that shape. A pattern can only work if it guesses right
  about a name it did not write: an upper-case name, a doubled space, an apostrophe, and the
  sentence is not recognised — and the header falls back to English **above French labels**, which
  is exactly the screenshot.
- **This release removes the guessing.** The header is now a French *template* filled with the
  name: `OnyxLocalization.format("What %s does", botName)` → **« Ce que fait coder »**, and the
  component is told not to translate it a second time (`localizeHeader = false`). Nothing has to be
  recognised any more, so no name can escape.
- **The pattern stays, and is now case- and space-tolerant** (`RegexOption.IGNORE_CASE`,
  `^What\s+(.+?)\s+does$`): any other caller that still composes the sentence — and a label that
  reaches the screen already capitalised — is translated too.
- **Why the phone still showed English.** The pattern only exists from **1.3.42** on
  (`git show v1.3.41:…OnyxLocalization.kt` has no such line; `v1.3.42` does), so an install of
  `1.3.40` or `1.3.41` draws the English header whatever the source says today. Réglages →
  « App version » tells which build is on the phone; anything below `1.3.42` predates the fix.
- **Locks.** `ScheduleCopyLocalizationTest` now proves three things: a name goes through the
  template unchanged (« Le Bot de John », « l'agent d'Olympe », a name with padding), the pattern
  still catches « WHAT CODER DOES » and « What  coder  does », and the screen really composes the
  header through the template.

## 2. What this version does not do

- Nothing could be checked **on a device** here: no phone is attached to the build machine.
  The APKs were verified statically — signer, `versionCode`, asset name, and the translated
  string read back out of the published `classes.dex`.
- The glass's background blur still cannot be judged on an emulator.
