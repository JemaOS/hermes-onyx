# Hermes Onyx 1.3.78

Two things: the takeover card goes back where it belongs, and the app stops re-scanning the whole
thread thirty times a second.

## 1. The takeover card is a line of the thread — one place, not two

1.3.77 moved it into the prompt well. It then covered the composer's first line
(`Guider la réponse…`), and the thread item that already existed made it appear **twice**.

- The prompt well no longer carries it (`ChatScreen`); the composer measures the field alone again.
- It stays a **line of the thread** (`item(key = "browser-invite")`), where an invitation to take
  the hand belongs: it scrolls with the conversation.
- `BrowserInviteSinglePlacementTest` asserts the count of placements is **exactly one** — the
  duplicate cannot come back silently.

## 2. The projections no longer grow with the thread

`ShellState.of` and `ChatScreenState.of` are read by the app root and the chat screen, and they are
recomputed on **every** published token — thirty times a second while a reply streams. Both walked
the entire message list each time:

- `ShellState.of` allocated a full copy of the thread (`filterNot { isStreaming }`, N elements),
  a `flatMap` list for approvals, and **one `VoiceTurn` per turn of the conversation**;
- `ChatScreenState.of` walked every message again to count user turns and find the first question.

With twenty pages of history that is work proportional to what the app holds — the shape of « plus
j'ai d'infos, plus c'est lent ». `SettledScans` computes what depends only on the **finished**
messages once per thread shape (count, first/last id, plus a length fingerprint that catches an
edited text), and both projections reuse it. Approvals and questions are deliberately **not**
cached: one can appear mid-turn, on the line being written, and a card awaiting a decision must
never wait for a token.

`SettledScansPerTokenTest` proves it with a counter rather than a clock: thirty publications of
text, **one** projection; a message added, one more; an edited question, seen.

## Measurement tools are now in the tree

`tools/perf/` (dense-chat generator, mock gateway, scroll and stream harnesses) and
`docs/PERF-MESURE.md` came from the unmerged `perf-audit` branch. Scroll/stream numbers below are
**not** claimed for this build: they were taken on the emulator, not on your phone.

## What this version does not do

- No hardware measurement. Frame times from the emulator say nothing about your device; the
  harness in `tools/perf/` produces the number on the phone.
- Conversation persistence still rewrites the whole local file on each save (on a background
  thread). That is the next scaling cost when a conversation gets very large.

---

## Files

| Package | Module | sha256 |
|---|---|---|
| `onyx-1.3.78-release.apk` | app | *see `SHA256SUMS-1.3.78.txt`* |

```bash
curl -LO https://github.com/JemaOS/hermes-onyx/releases/latest/download/SHA256SUMS-1.3.78.txt
sha256sum -c SHA256SUMS-1.3.78.txt
```