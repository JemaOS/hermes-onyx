# Onyx 1.3.17 — a fork keeps the face of its original

One defect, one release. It was about the app losing a bot's identity the moment you branched a
conversation.

## The fork lost its bot

You opened a conversation with `coder`, then created a thread from it. The new conversation was
titled `coder (fork)` — and it showed **Onyx's blue face** while the original kept `coder`'s own
face, in the sidebar and in the header alike.

The title was the culprit. A conversation is tied to its bot by two links, in this order:

1. the **server chat** the bot names (`BotProfile.chatId`, its `canonical_session`) — the strong
   link;
2. the **title** that opening a bot's chat gives the conversation — the durable one, and the only
   one left when the roster names no `canonical_session` for that bot.

A branch is the *copy* of a conversation: it carries its original's name, `coder`. The suffix
`(fork)` is **display** — it says "this is a branch", nothing more. But the title comparison read
the suffixed title, so `coder (fork)` matched no bot, and the branch fell back to the default:
Onyx's own face.

## The rule now

The name compared against the roster is the title **without** the branch suffix
(`Conversation.botName()`). One place, one rule — the same way the app already resolves a face in
one place.

- a branch of a bot conversation keeps that bot — its face, its profile, its widget label;
- a branch of an ordinary conversation stays unattached: once the suffix is removed, the base name
  is the first question again, and a title that is only a question links to nobody;
- the suffix itself is defined **once** (`Conversation.ForkSuffix`), so the title shown and the
  title compared can no longer drift apart;
- an ambiguous name still links to nobody: it must designate a **single** bot.

Both surfaces now agree, because both read the same resolver: the sidebar card
(`conversationProfile`), the chat header (`botOfConversationOrRemembered`), the home-screen widget
(`BotPresenceIndex.labelOf`) and the ledger (`AgentLedgerScreen`).

## Nothing else changed

No new capability, no new network transport, no new state: the branch keeps the identity its
original already had. Every existing function — chat, rooms, hands-free, Computer, Wear, Auto — is
untouched.

**Test suite:** `4064 tests, 0 failures` — `:core:testDebugUnitTest` + `:app:testDebugUnitTest`.
