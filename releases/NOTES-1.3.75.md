# Hermes Onyx 1.3.75

One measurement, from a real conversation on this server: `GET /api/sessions/<id>/messages`
answers its **default page of 500 messages — 1,166,980 bytes**. The app asked for it every five
seconds while a turn was silent, then again on every recovery attempt. That is tens of megabytes
per open conversation on a mobile connection, for a reply that lives in the last few lines.

## The poll reads the tail, not the thread

- `ServeSessionMessages.rows(sessionId, profile, limit)` now accepts a window and sends
  `limit=N&order=latest` — the order the server already returns chronologically.
- `reply()` uses `REPLY_TAIL = 40`. Measured on the same conversation: **97,000 bytes instead of
  1,166,980** (12× less), and it stops as soon as the reply lands.
- Opening a conversation still reads the whole thread: that is one read per open, and truncating
  it would silently hide history.

A window that is too small cannot show a wrong answer: `trailingReply` returns `null` and the
bubble stays as it is — the rescue is missed, nothing is invented (`ServeSessionMessagesTailTest`).

### What was deliberately left unbounded

`ReplyWatcher` (background notification checks) still reads the full thread. Its snapshot compares
**message counts** (`ReplyWatch.isNewReply` requires `current.count > previous.count`), so a bounded
window would saturate the count and silently stop announcing replies. A source guard pins that
reason so a later "optimisation" cannot undo it.

## What this version does not do

- The thread opened on screen is still up to 500 messages; a paged history view is a separate
  change.
- Compression lineage reads (`include_ancestors`) are unchanged.

---

## Files

| Package | Module | sha256 |
|---|---|---|
| `onyx-1.3.75-release.apk` | app | *see `SHA256SUMS-1.3.75.txt`* |

Verify a download:

```bash
curl -LO https://github.com/JemaOS/hermes-onyx/releases/latest/download/SHA256SUMS-1.3.75.txt
sha256sum -c SHA256SUMS-1.3.75.txt
```