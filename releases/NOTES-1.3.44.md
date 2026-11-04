# Release notes — Hermes Onyx `1.3.44`

**Version:** `1.3.44` (`versionCode` = `1176`, written in `gradle.properties`).
**Packages:** phone `onyx-1.3.44-release.apk`; `onyx-auto-1.3.44-release.apk`;
`onyx-wear-1.3.44-release.apk`; checksums in `SHA256SUMS-1.3.44.txt`.
**Test suite:** counted in the XML of the build that produced these packages — 0 failure.

---

## 1. A bot made from another keeps its own face

Reported from the phone: a bot created **from coder** and renamed « coder_logs » showed the **same**
face as « coder » in the conversation list, and changing one face changed both.

**The face store was not at fault** — it is keyed by profile name, one key each. The fault was in
**identity**.

A bot created « from » another inherits its source's `canonical_session` (`clone_from`, server side).
Both profiles then claim the same conversation, and `botOfConversation` took **the first one in roster
order**:

```kotlin
bots.firstOrNull { bot -> bot.chatId != null && (… == conversation.serverSessionId …) }
```

Two conversations → same bot → same face, and the face you had just given to `coder_logs` was drawn
nowhere. That is the « tous les coder ont syncro le même visage » in the report.

**Now a shared session identifies nobody.** The rule, in `botOfConversation`
(`app/src/main/java/com/onyx/app/ui/bots/BotChat.kt`):

1. a session claimed by **exactly one** bot is still the strong link — unchanged;
2. a session claimed by **several** bots no longer decides: the conversation's **own name** picks
   between the claimants;
3. if the name picks nobody either, the conversation falls back to the profile it **remembered**
   (`Conversation.botProfile`) — never to a bot drawn at random from a list.

`BotPresenceIndex.labelOf` (the widget's presence and label) had the same `firstOrNull` and gets the
same rule, so the widget cannot attribute one conversation's activity to the other bot either.

**What does not change:** a duplicate still starts with its original's face (that is what cloning
means), and one bot still shows the same face everywhere it appears — the fix separates *bots*, not
copies of a bot.

Locks: `SharedSessionFaceTest` (4 tests) — two bots on one session get two identities; a renamed
conversation keeps its remembered profile; a session with a single claimant still wins; and changing
one bot's face moves **only** its row.

## 2. Still true from `1.3.43` → `1.3.36`

- The vault has its own Settings section (« Secrets ») and its own launcher shortcut; « Ce que fait
  coder » and « Horaires de coder » are French; the NOM field opens on the bot's name;
  « Enregistrer » saves and stays; deleting asks in a centred dialog; **Modèle** opens the real model
  list; the conversation list reads « En cours » (and beats) and « à l'instant ».

## 3. What is not claimed

- **No frame number** and no on-device verification: no device on the build machine, no emulator
  binary. What is proven is the resolution rule and the suites.
- The inherited `canonical_session` is a **server-side** artefact of `clone_from`; the app now reads
  it correctly, but it is not fixed at the source.
