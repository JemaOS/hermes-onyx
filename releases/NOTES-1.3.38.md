# Release notes — Hermes Onyx `1.3.38`

**Version:** `1.3.38` (`versionCode` = `1170`, written in `gradle.properties`).
**Packages:** phone `onyx-1.3.38-release.apk`; `onyx-auto-1.3.38-release.apk`;
`onyx-wear-1.3.38-release.apk`; checksums in `SHA256SUMS-1.3.38.txt`.
**Test suite:** counted in the XML of the build that produced these packages — 0 failure.

---

## 1. « Modèle », dans l'identité d'un bot, ouvre la vraie liste des modèles

Reported from the phone, two screenshots: the bot's identity page showed a **Modèle** row with a raw
identifier (`deepseek/…-flash-1017`) and, underneath, only two lines — « Identique à Onyx » and
`hermes-agent`. Nothing told the person **which models exist**, while the sheet used in the
conversation shows all of them: search, provider filters, prices, favourites.

- **Tapping « Modèle » now opens that same sheet**, above the identity page
  (`app/src/main/java/com/onyx/app/ui/bots/BotEditorScreen.kt`,
  `app/src/main/java/com/onyx/app/ui/OnyxApp.kt`). It is the same composable as the thread and the
  room (`ui/models/ModelSheet.kt`), so there is one model list in the app, not a second one to keep
  in step.
- **The scope is the bot, and it says so**: `ModelScope.BOT` prints « Pour toutes les conversations
  de ce bot » under the title, and the choice is written on the bot's profile — server
  (`profiles.configure`) and local copy — then the roster is re-read so the row shows the model just
  picked. The model also enters the server's recents.
- **The catalogue is only read when the sheet opens**, like the other two entry points: the editor's
  flag joined the effect that fetches it, so nothing is requested while the page is merely open.
- **« Identique à l'agent principal » keeps its own row.** It is not a model but the absence of a
  choice, and the sheet only picks models: without that row it would have become unreachable. It
  stays under the Modèle row, with its tick.
- Without a parent-provided sheet (previews, tests), the editor still unfolds its local list — the
  behaviour is unchanged where the sheet does not exist.

Locks: `BotEditorModelSheetTest` — the row opens the sheet when it is provided and no longer unfolds
the raw list, the parent wires it with `ModelScope.BOT` on the bot's model, and the
« identique à l'agent principal » row is still drawn.

## 2. Still true from `1.3.37` and `1.3.36`

- The conversation list's working line says **« En cours »** in French and **beats** (« En cours. »,
  « En cours.. », « En cours... »), and « now » reads « à l'instant ».
- Settings → Calls, and four other settings explanations, are in French, locked by
  `SettingsFooterLocalizationTest`.

## 3. What is not claimed

- **No frame number** — no device on the build machine, no emulator binary; the protocol is in
  `AUDIT-PERFORMANCE.md`.
- The model sheet is **not** claimed to list every provider a server could ever have: it shows what
  the server's catalogue returns, which is what the thread shows too.
