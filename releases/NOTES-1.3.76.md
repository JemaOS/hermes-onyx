# Hermes Onyx 1.3.76

The thread now opens on its **last page** and walks back page by page.

## Opening a conversation no longer downloads it whole

`GET /api/sessions/<id>/messages` answers 500 messages by default — **1,166,980 bytes** measured on
a real conversation — and the screen only ever showed the end of it. The first read now asks for
`limit=60&order=latest`: **101 to 135 KB** per page on the same conversation.

## "Earlier messages" actually works now

The row existed but the tap did nothing: the page start was computed as
`maxOf(visibleFrom, messagesSize - PAGE)`, so `visibleFrom` — always smaller — was overwritten on
every recomposition. Two fixes:

- a `-1` sentinel ("no page unfolded yet") with `visibleFrom.coerceIn(0, tail)`;
- the page is remembered per **conversation** instead of per first message id, so prepending an
  older page no longer snaps the reader back to the tail.

Two labels, one row: `Show earlier messages (30)` while a page is already in memory,
`Load earlier messages` when the page above has to be fetched (`offset`). An empty page turns the
row off instead of inviting a tap that returns nothing. The list keeps its scroll anchor while
older messages are prepended.

## What this version does not do

- No jump-by-date or search-driven paging; it is a step-by-step walk back through the thread.
- Media inside older pages arrives as links, exactly as before.

---

## Files

| Package | Module | sha256 |
|---|---|---|
| `onyx-1.3.76-release.apk` | app | *see `SHA256SUMS-1.3.76.txt`* |

```bash
curl -LO https://github.com/JemaOS/hermes-onyx/releases/latest/download/SHA256SUMS-1.3.76.txt
sha256sum -c SHA256SUMS-1.3.76.txt
```