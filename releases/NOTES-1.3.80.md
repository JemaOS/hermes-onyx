# Hermes Onyx 1.3.80

Third pass on the two things your usage stresses: many tool steps in a thread, and many terminals.
No app behaviour changes — only the cost of drawing and saving.

## 1. A terminal no longer builds 4,800 objects per frame

`paintLine` created a `SpanStyle` **per character**: 120 spans per line × 40 lines, on every
publication — close to **300,000 objects per second** during a build. No device survives that, and
neither does the collector; it is the freezing that has nothing to do with the network.

- Consecutive cells with the same style are now **one span** (`styleRuns`): a single-colour line goes
  from 120 spans to **one**. Colour changes still cost one span per change, never per character.
- The painted row is **kept** while its text and style fingerprint do not change
  (`TerminalLineCache`): a progress bar rewriting one line repaints one row, not forty. Twenty
  repaints replace eight hundred.

## 2. Saving stops comparing (and copying) the whole thread

- `persistCurrent` copied every message (`map { it.copy(isStreaming = false) }`) on each of its
  sixteen call sites — 500 allocations for a thread of 500 messages, and the fresh list made the
  repository's "nothing changed" check impossible.
- The copy now happens only when a message is actually streaming, and `save()` recognises an
  unchanged conversation **by identity** (`previous === conversation`, or the same message list plus
  equal surface fields) instead of deep-comparing every message, all their text and all their tool
  output — megabytes per save.
- The projection reused by `ShellState` now hands back **the same list instance**, so the equality
  that decides whether to recompose stops at the identity check instead of rereading twenty pages.

## What this version does not do

- No hardware measurement. `tools/perf/` produces the real frame numbers on your phone; the claims
  above are proven by counters and instance identity, not by a stopwatch.
- Conversation storage is still one JSON document: writes are now rare, the file is not smaller.

---

## Files

| Package | Module | sha256 |
|---|---|---|
| `onyx-1.3.80-release.apk` | app | *see `SHA256SUMS-1.3.80.txt`* |

```bash
curl -LO https://github.com/JemaOS/hermes-onyx/releases/latest/download/SHA256SUMS-1.3.80.txt
sha256sum -c SHA256SUMS-1.3.80.txt
```