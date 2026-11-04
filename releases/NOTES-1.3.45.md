# Release notes — Hermes Onyx `1.3.45`

**Version:** `1.3.45` (`versionCode` = `1177`, written in `gradle.properties`).
**Packages:** phone `onyx-1.3.45-release.apk`; `onyx-auto-1.3.45-release.apk`;
`onyx-wear-1.3.45-release.apk`; checksums in `SHA256SUMS-1.3.45.txt`.
**Test suite:** counted in the XML of the build that produced these packages — 0 failure.

---

## 1. The face fix, in a build you can install

The bot-face fix shipped in `1.3.44` is here unchanged, with the two tests that hold it:

- **two bots sharing a server session** (a clone inherits `canonical_session`) no longer collapse
  into one identity: each conversation's own name renders its bot, so each row keeps its own face
  (`botOfConversation`, `app/src/main/java/com/onyx/app/ui/bots/BotChat.kt`; same rule in
  `BotPresenceIndex.labelOf` for the widget's presence);
- **a session claimed by a single bot still wins over the name** — the contract `ThreadSpeakerTest`
  holds (« atlas » over a conversation titled « Scout »). The rule was not flipped to name-first:
  the name only decides when the session is shared, i.e. when the session says nothing.

Why a new number rather than another `1.3.44`: the phone that reported the defect still announced
« La version 1.3.41 est sortie », so the fixed build was never installed on it. `1.3.45` is the same
code, published so the update banner points at a build that contains the fix.

Locks: `SharedSessionFaceTest`, 6 tests — including « changing one bot's face moves only its row »
and the two exceptions above.

## 2. Still true from `1.3.44` → `1.3.36`

- The vault has its own Settings section (« Secrets ») and its own launcher shortcut; « Ce que fait
  coder » / « Horaires de coder » are French; the NOM field opens on the bot's name; « Enregistrer »
  saves and stays; deleting asks in a centred dialog; **Modèle** opens the real model list; the
  conversation list reads « En cours » (and beats) and « à l'instant ».

## 3. What is not claimed

- **No frame number** and no on-device verification: no device on the build machine, no emulator
  binary.
- The inherited `canonical_session` is a server-side artefact of `clone_from`; the app now reads it
  correctly, it is not fixed at the source.
