# Measured performance audit — feed scrolling

**Date**: November 4, 2026 · **Tree**: `perf-audit` branch (base `d12000d` + the scroll batch)
**Complaint**: "when there's a lot of information in a chat, it lags when scrolling".

This document **complements** `docs/PERF-REVIEW.md`: it replaces none of its findings, it
**measures** them, and it corrects one that code reading could not settle — the cost that recurs
every time a row **re-enters** the list window.

---

## 1. What was measured, with what, and what the numbers are worth

| | |
|---|---|
| Device | API 36 emulator, x86_64, 6 cores, 4 GiB, 1080×2400 @420 (AVD `onyxperf`) |
| APK | **release** (R8, minification, shrunk resources), signed with the update key |
| Measurement feed | 30 turns, **226 KiB of Markdown** (tables, code blocks, lists, quotes) + 180 tool outputs of ~2 KiB |
| Instrument | `dumpsys gfxinfo` (`Janky frames`, percentiles) and `dumpsys gfxinfo framestats` |
| Gesture | 18 swipes of 120 ms, 9 up then 9 down, same area, same starting position |
| Rendering | `swiftshader` (**software** GL) for the comparisons; one `-gpu host` pass as a control |

**What these numbers are not.** The emulator renders in software GL: drawing there costs several
times what it costs on an Adreno, so the **absolute percentage of janky frames is not transferable**
to a Snapdragon 8 Elite. What is transferable:

- the **comparison** between two APKs, same gesture, same feed, same emulator;
- the **UI thread time** (`HandleInputStart → SyncStart`), which does not depend on the GPU;
- the **comparison** at rest / during a response.

Control under hardware rendering (`-gpu host`, NVIDIA) on this same feed, at rest: **0.3% janky
frames**, UI thread `p50 = 0.8 ms`. In other words: when drawing is not the bottleneck, the
composition of the feed at rest is already healthy — it is the **work per frame** that decides, and
that is what the numbers below track.

---

## 2. The two results, and the one that does not deliver

### 2.1 Scrolling at rest: the cache divides UI thread time by 1.6

Same dense feed, same APK except for one fix (`RowCaches`), same gesture.

| | Without cache | With cache | Delta |
|---|---|---|---|
| Frames rendered | 142 | 173 | — |
| **Janky frames** | **97.2%** | **91.9%** | **−5.3 pts** |
| **UI thread, p50** | **59.0 ms** | **36.1 ms** | **−39%** |
| UI thread, p90 | 102.9 ms | 71.7 ms | −30% |
| "Slow UI thread" frames | 76 / 142 (54%) | 94 / 173 (54%) | — |
| Frame p50 | 181.6 ms | 116.8 ms | −36% |

Reading: the time the UI thread spends per input frame drops from **59 to 36 ms**. Under software
GL, drawing remains the ceiling (hence a still-high jank percentage); on a device where drawing is
free, it is that column that decides smoothness.

### 2.2 Scrolling **while** a response is being written: the cache is not enough

Same feed, same gesture, but started two seconds after sending a question, served by a local fake
gateway (`tools/perf/mock-gateway.py`, 24 characters every 35 ms, ~33 publications/s — the cadence
of `TEXT_PUBLISH_MS = 30`).

| | Without cache | With cache |
|---|---|---|
| Frames rendered | 113 | 109 |
| Janky frames | 100% | 100% |
| **UI thread, p50** | **80.3 ms** | **86.6 ms** |
| UI thread, p90 | 102.2 ms | 97.5 ms |
| "slow UI thread" | 97 / 113 (86%) | 100 / 109 (92%) |
| Frame p50 | 236.7 ms | 266.6 ms |

**The cache changes nothing here, and that is the most useful result of this audit.** The difference
is within measurement noise (one pass each, software GL); the conclusion itself is clear: while a
response is being written, the cost is **not** in archive rows coming back — it is in the
**per-token recomposition** of the entire screen. A live row is never "re-entered": it is recomposed
thirty-three times per second, with the text growing.

In other words: the complaint has **two distinct causes**, and this batch fixes only one.

---

## 3. What this batch fixes

### 3.1 A row's work survives its exit from the window — `RowCaches.kt`

A `LazyColumn` **throws away** its off-screen rows. All of the row's `remember`s therefore restarted
empty, and each return to the screen redid:

- the Markdown splitting of the entire text (`splitMarkdown`);
- the Markwon parse of each styled paragraph, spans included (`setMarkdown`);
- the diff search, the diff splitting, the task JSON and the delivered-files regex on **every**
  tool output (`MessageDerived.of`);
- the first-link regex (`linkPreviewUrl`).

These four results depend only on content. They are now kept in **bounded LRUs** (64 derived rows,
24 splittings, 16 rendered parses, 64 links), keyed by **content** — identifier + fingerprint of the
text and the tools — never by position.

The fingerprint is deliberately **cheap to compute**: lengths and states, never the text. A tool
output that arrives changes length and status; a text being written changes length. Both invalidate
the entry without re-reading half a megabyte to find out.

Measured effect: **§2.1**, −39% UI thread time per input frame.

### 3.2 Already-parsed Markwon prose is reused — `MarkdownText.kt`

`AndroidView` recreates a `TextView` on every row entry; `setMarkdown` then re-parses all the text
and re-applies every span. The resulting `Spanned` (task checkboxes included) is now kept by
(text, style) and rendered with one `setText` — **as a copy** (`SpannableString`), because two rows
must not share a `Spannable` that selection can write to. The streaming dot (`•`) stays out of the
cache: it only exists during the response.

### 3.3 The arrival animation is no longer mounted on archive rows — `ChatScreen.kt`

`Modifier.messageArrival` is a `Modifier.composed`: mounted on **every** row, it opened a
subcomposition and three `animateFloatAsState` for an animation that, in practice, only concerns
the message just sent. Archive rows receive a bare `Modifier`.

### 3.4 What this batch does not redo

The scroll batch already present in the tree is kept as is: `state.live` reserved for the live
line, `GlassScrollFreeze` out of the screen's scope, `MessageDerived.of(includeText = false)`
during streaming, non-animated tool checkbox for an archive, `contentType` on the `items`,
`pendingApprovalCount` without allocation, `BotMentions.menu` memoized.

---

## 4. What remains — and what measurement points to, not reasoning

1. **Per-token recomposition of the entire screen.** `OnyxApp()` reads `ChatUiState` at its root
   (`OnyxApp.kt:573`) and `ChatScreen` reads `state` in its body: on each token, ~4,700 lines of
   root and ~2,200 lines of screen are re-executed, and the `LazyColumn` scope is rebuilt with a
   dozen lambdas per visible row. **This is the cause of §2.2.** Clean way out: a `ChatRoute`
   composable that reads the stream and renders only the feed, and stable row lambdas
   (`remember(message.id)`), so that only the live line and the list recompose.
2. **The live line's prose** is re-split and re-parsed on every token (the text changes, that is
   unavoidable); what is not unavoidable is re-parsing **the already-finished blocks** of that same
   response. Incremental splitting (keep closed blocks, re-parse only the last one) is the natural
   complement to #1.
3. **The entire page is the Haze source** (`ChatScreen.kt:918`): each scroll frame re-records and
   rasterizes the background. **Drawing** cost, not composition — the GPU pass at rest (0.3%) says
   it is not limiting at 60 Hz, it says nothing about a 120 Hz screen.
4. **Baseline profile**: still absent. To be generated with a macrobenchmark **on the phone** — the
   emulator would produce an x86 profile that says nothing about ART on ARM.

---

## 5. Reproducing

```bash
# 1. a dense feed, and push it into the app (rooted device or emulator)
python3 tools/perf/gen-dense-chat.py /tmp/conversations.json 30
adb push /tmp/conversations.json /data/local/tmp/c.json
adb shell "su 0 cp /data/local/tmp/c.json /data/data/com.onyx.app/files/conversations.json"

# 2. a gateway that writes slowly, to measure "during a response"
python3 tools/perf/mock-gateway.py &   # http://<host>:19200, model "mock-1"

# 3. the gesture, and the numbers
sh tools/perf/measure-scroll.sh avant 14
sh tools/perf/measure-scroll.sh apres 14
```

`measure-scroll.sh` prints `Janky frames`, the percentiles, the "slow UI thread" counters and the
UI thread time computed from `framestats` (`HandleInputStart → SyncStart`).

**Honest protocol.** One pass per state is not enough to settle a 5% gap: do three passes, take the
median, and always compare **the same feed** and **the same gesture**. On the phone, `dumpsys
gfxinfo` on the **release** APK is the only measurement that counts; a `debug` APK is neither
minified nor optimized and can by itself double the cost.
