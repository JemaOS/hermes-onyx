# Hermes Onyx 1.3.79

The two costs left over from 1.3.78: the whole conversation file rewritten on every save, and a
terminal repainting its 40 lines for every fragment of shell output.

## 1. Saving stops rewriting the file on every step

`store.updateData` rewrites the **entire** file: every local conversation with its full history.
`persistCurrent()` is called from sixteen places, several times per turn — so a single agentic turn
recopied all of that text once per tool step. On a twenty-page thread that is what shows up as
stutter while the app grows.

- Write requests are now **conflated** (`Channel.CONFLATED`) and answered by a single writer after a
  250 ms settle window: a burst costs **one or two** writes instead of one per step, and the file
  always ends on the freshest state — nothing is dropped, only the repetition.
- `ConversationRepository.flush()` exists for any path that must know the file is up to date, and it
  writes only when something is actually pending.

### A data-loss bug found on the way

`saves()` updated the in-memory list with a read-then-write (`_conversations.value = …`). Two
concurrent saves — the view model publishing while a turn callback publishes — each started from the
same list, and the second **erased the other's conversation**. Twelve concurrent saves left exactly
one conversation on disk. That is now an atomic `update {}`; the coalescing test asserts all twelve
survive.

## 2. A talkative terminal no longer repaints per fragment

`onOutput` did `revision++` for **every** SSH fragment, and the render rebuilds forty
`AnnotatedString`s (style per cell) each time. `npm install`, a build, a `tail -f`: hundreds of full
repaints a second, which reads as freezing that has nothing to do with the network — worse with many
terminals open.

`OutputPacer` limits publication to one per frame (16 ms) and covers the rest with a single trailing
publish. The buffer keeps being written immediately (a bounded 40×120 grid), so **nothing is lost**:
the screen shows the complete state once per frame instead of once per fragment.

Both are proven without a clock: a counter of writes, a counter of publications
(`ConversationWriteCoalescingTest`, `OutputPacerTest`).

## What this version does not do

- No hardware measurement; the emulator's frame numbers say nothing about your device. `tools/perf/`
  produces the real number on the phone.
- Conversation files are still one JSON document: the write is now rare, not smaller. Splitting a
  conversation's history out of the index would be the next step for very large threads.

---

## Files

| Package | Module | sha256 |
|---|---|---|
| `onyx-1.3.79-release.apk` | app | *see `SHA256SUMS-1.3.79.txt`* |

```bash
curl -LO https://github.com/JemaOS/hermes-onyx/releases/latest/download/SHA256SUMS-1.3.79.txt
sha256sum -c SHA256SUMS-1.3.79.txt
```