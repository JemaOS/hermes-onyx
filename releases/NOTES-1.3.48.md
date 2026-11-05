# Release notes — Hermes Onyx `1.3.48`

**Version:** `1.3.48` (`versionCode` = `1180`, written in `gradle.properties`).
**Packages:** phone `onyx-1.3.48-release.apk`; `onyx-auto-1.3.48-release.apk`;
`onyx-wear-1.3.48-release.apk`; checksums in `SHA256SUMS-1.3.48.txt`.
**Test suite:** counted in the XML of the build that produced these packages — 0 failure.

---

## 1. The reply that is being written no longer goes through a system view

Reported from the phone: « quand il y a beaucoup d'informations dans un chat, ça lag au
défilement ». The audit that followed (`AUDIT-PERFORMANCE.md`) found three clocks, and this
release removes the heaviest one: the cost **per token**, while a reply is being written.

- **Measured** (emulator, host GPU, a 12 000-character reply): the Markwon `TextView` whose
  content changes at every token cost **17.5 ms of UI thread per frame** — 15.7 ms of it in
  record-display — while the same text rendered by Compose cost **1.4 ms**. At 33 tokens per
  second the main thread was over its 16.7 ms budget before anything else was drawn.
- **A finished view costs nothing**: its display list is kept, which is why scrolling an
  archive of replies never paid this price. The defect existed only during writing.
- The live block now goes through **`StreamingProse`** — bold, italic, inline code, strikethrough,
  links, headings, bullets and checkboxes, rendered as Compose text. The moment the reply ends,
  the row takes the Markwon path back, cached.

**What this does not claim.** The numbers above are from the emulator, and they are the
**thread-UI** cost per frame — the part that does not depend on the GPU. They are not a
frame rate on a Snapdragon 8 Elite: no device was attached to the build machine and no emulator
binary is installed. The measurement protocol for the phone is in `AUDIT-PERFORMANCE.md` §6.

## 2. Three more posts on the same clock

- **A reply body is not a UI label.** The plain-text path handed every response to the
  translation dictionary: a hundred anchored regular expressions swept over the whole string, at
  every composition of the row. Past 512 characters — or from the first line break — a string is
  no longer a label and is rendered as it stands; a response body no longer goes through the
  dictionary at all.
- **The reasoning rail is painted, not measured.** It used to sit in an intrinsically-measured
  `Row`, which traverses the text once more on every layout of that block — and that block is
  exactly the one that re-enters the window when you scroll a thread of tools. It is drawn now,
  like the rail of a folded block already was.
- **The voice turns of a thread are paired in one pass.** The previous version re-scanned
  everything that followed each question: a **quadratic** sweep, redone at every publication of
  text. Same result, one pass.

The Markwon render cache holds **48** entries instead of 16: sixteen only covered the screen,
so scrolling back up — the gesture of the complaint — found an empty cache and re-parsed every
row.

## 3. The screen keeps the phase of the turn, not the whole state

The screen's projection now carries `TurnPhase.isStreaming` rather than the whole chat state:
the phase is what drives the thread's state, and it says so **before** the state does. Without
it, the screen painted « travail en cours » on a turn the server had just closed.

## 4. Still true from `1.3.47` → `1.3.36`

- A thread's face does not leak to its siblings; two bots sharing a `canonical_session` keep two
  identities; the vault has its own Settings section and its tile in the shortcuts; « Ce que fait
  coder » / « Horaires de coder » are French; the NOM field opens on the bot's name;
  « Enregistrer » saves and stays; deleting asks in a centred dialog; **Modèle** opens the real
  model list; the list reads « En cours » (and beats) and « à l'instant ».
- The scroll of a dense thread no longer rebuilds its background at every frame, and the row that
  writes itself no longer replays the whole thread at every token.

## 5. What is not claimed

- **No frame rate on the phone, and no on-device verification**: no device was attached to the
  build machine, and no emulator binary is installed in the SDK. The figures in §1 are the
  emulator's thread-UI cost per frame, and they are stated as such.
- This build was made from a clean archive of its commit.
