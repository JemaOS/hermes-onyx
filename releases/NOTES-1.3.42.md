# Release notes — Hermes Onyx `1.3.42`

**Version:** `1.3.42` (`versionCode` = `1174`, written in `gradle.properties`).
**Packages:** phone `onyx-1.3.42-release.apk`; `onyx-auto-1.3.42-release.apk`;
`onyx-wear-1.3.42-release.apk`; checksums in `SHA256SUMS-1.3.42.txt`.
**Test suite:** counted in the XML of the build that produced these packages — 0 failure.

---

## 1. « WHAT CODER DOES » speaks French

Reported from the phone, on **Nouvel horaire**: the labels were French (NOM, QUAND, Répéter,
En semaine, À, « Comme vous le demanderiez dans sa conversation ») but the section header read
**WHAT CODER DOES**.

- **Cause**: the header is « What *coder* does » — it **composes the bot's name**. A composed
  sentence is not a dictionary key, and this one had no pattern, so it passed through in English
  while its neighbours were translated. The list's own title, « *coder*'s Schedules », had the same
  hole.
- **Fix**: two patterns in `OnyxLocalization`, anchored on the whole sentence and re-injecting the
  name as it was: « Ce que fait coder », « Horaires de coder ».
- **The two explanations** of that screen (« Give *coder* something to do… », « Each run is
  *coder*'s… ») were already covered — their patterns exist further down the same list
  (`FunctionalQaLocalizationTest` holds them). No duplicate was added: a second pattern for the same
  sentence would take the words away from the first, which is exactly what the suite caught before
  this shipped.
- Lock: `ScheduleCopyLocalizationTest` — the two new sentences are exercised **with a real name**
  (the only way to prove a pattern catches the whole string), the two existing ones are checked to
  keep the name and to change language, and the screen is checked to compose all four.

## 2. Still true from `1.3.41` → `1.3.36`

- The NOM field opens on the bot's name; « Enregistrer » saves and stays; deleting asks in a centred
  dialog; **Modèle** opens the real model list; the conversation list reads « En cours » (and beats)
  and « à l'instant »; Settings → Calls is French.

## 3. What is not claimed

- **No frame number** — no device on the build machine, no emulator binary; the protocol is in
  `AUDIT-PERFORMANCE.md`.
- The French catalogue is not claimed complete for the whole app: what is locked is the settings
  screens, the conversation list, the Calls page and the schedules screen.
