# Release notes — Hermes Onyx `1.3.46`

**Version:** `1.3.46` (`versionCode` = `1178`, written in `gradle.properties`).
**Packages:** phone `onyx-1.3.46-release.apk`; `onyx-auto-1.3.46-release.apk`;
`onyx-wear-1.3.46-release.apk`; checksums in `SHA256SUMS-1.3.46.txt`.
**Test suite:** counted in the XML of the build that produced these packages — 0 failure.

---

## 1. The face belongs to the conversation you changed it in

Reported from the phone, after `1.3.44`: « j'ai changé juste le visage de celui que j'avais en
conversation, ça a changé tous les coder ». Three rows in the list, all titled `coder`, all moved.

**Cause — this one is a design gap, not a resolution bug.** A face only existed on the **profile**:
`BotFaceStore` is keyed by profile name, and every row, header and bubble of that bot read the same
key. A bot with three conversations therefore had one face for three rows, and the fix in `1.3.44`
(correctly identifying *which bot* each row belongs to) could not separate them — they are the same
bot.

**Fix — the conversation carries its own face.** `Conversation` gains `faceCode` (nullable, so old
files read fine):

- opening a bot's identity **from a conversation** and picking a face writes `faceCode` on **that
  conversation** (`ConversationRepository.save`), and only that row changes;
- the list uses `conversation.faceCode ?: faceCodeOf(identity)` — the thread's face wins for its own
  row, and its siblings keep the bot's;
- the thread header, the bubbles (« Bot colors in their chats ») and the bottom mark show the
  thread's face, so what the row shows and what the conversation shows cannot disagree;
- the **Bots** tab still edits the **bot's** face, and that face applies to every conversation of that
  bot that has no face of its own. Picking the default face on a thread clears its override.

Nothing is written to the server for a thread face: it is a local property of that conversation, and
the bot's own face is untouched.

Locks: `ThreadFaceTest` (4 tests) — one thread's face does not leak to its siblings, a thread without
a face follows its bot, the list reads the thread first, and the picker writes to the conversation
when it was opened from one.

## 2. Still true from `1.3.45` → `1.3.36`

- Two bots sharing a `canonical_session` are no longer collapsed into one identity; the vault has its
  own Settings section and launcher shortcut; « Ce que fait coder » / « Horaires de coder » are
  French; the NOM field opens on the bot's name; « Enregistrer » saves and stays; deleting asks in a
  centred dialog; **Modèle** opens the real model list; the list reads « En cours » and « à
  l'instant ».

## 3. What is not claimed

- **No frame number** and no on-device verification: no device on the build machine, no emulator
  binary.
- A thread face is not shared with another device — like every other face in this client.
