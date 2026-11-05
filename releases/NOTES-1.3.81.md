# Hermes Onyx 1.3.81

Measured on the gateway log, not guessed: in **2 hours** the app pulled **99.9 MB**, of which
**54 MB** on two sessions alone — 3.4 MB and 4.4 MB per request, every ~16 minutes, in the
background. That is the notification watcher, and it was reading the full 500-message page.

## The watcher reads a window now

`ReplyWatcher` asks for the whole thread because its "is this a new reply?" rule compared **message
counts** — a bounded window would have frozen the count and silenced notifications. That is why the
previous pass deliberately left it unbounded.

The rule is now an **fingerprint of the end of the thread** (`ReplyWatch.trailingOf`: role + text of
the last three rows, nothing stored), so:

- the watcher requests `WATCH_TAIL = 20` rows: **~10 KB instead of 3.4–4.4 MB** per poll;
- a reply whose text is *identical* to the previous one is now **detected** (the window shifted),
  which the count never could;
- the stored snapshot is versioned (`v2:`). A snapshot written by the previous version decodes to
  "never measured", so the first pass after the update records silently instead of ringing for a
  reply already read.

`ReplyWatchTest` covers the window, the identical-text case, and the old-format refusal. The guard in
`ServeSessionMessagesTailTest` that used to require the full read now pins the new contract on both
sides (watcher asks for a window; the rule no longer compares counts).

## Expected effect

Two sessions × ~7.8 MB every 16 minutes ≈ **29 MB/hour** of mobile data removed, and the battery cost
of parsing and storing it with it. This is the single largest remaining cost in your usage pattern,
and unlike the rendering work it is **measurable from the server log** — the numbers above are.

## What this version does not do

- Conversation storage is still one JSON document.
- No on-device frame measurement; `tools/perf/` produces it.

---

## Files

| Package | Module | sha256 |
|---|---|---|
| `onyx-1.3.81-release.apk` | app | *see `SHA256SUMS-1.3.81.txt`* |

```bash
curl -LO https://github.com/JemaOS/hermes-onyx/releases/latest/download/SHA256SUMS-1.3.81.txt
sha256sum -c SHA256SUMS-1.3.81.txt
```