# Hermes Onyx 1.3.77

The browser takeover request was drawn **on top of the conversation**. It now lives in the prompt
well, where it belongs.

## What was wrong

Two defects, one visible symptom (screenshot of 2026-11-05: the card covered the last messages
while `Écrire à Onyx…` showed underneath it):

1. `MessageRow` drew a `BrowserTakeoverCard` inside the thread's tool row. At the bottom of a
   conversation that card sat *over* the last bubbles.
2. The prompt band (`BrowserInvite`) did exist, but `composerHeightPx` measured **only the text
   field**. When the band appeared it grew the glass plate while the thread's bottom reserve stayed
   put — so the plate covered the end of the conversation.

## What changed

- The in-thread card is gone (`MessageRow`), along with the tool-name detection that fed it. The
  request is carried by `BrowserInvite` in the composer well — which also covers the case the row
  never could: an agent working in **another** session, because it reads `onyx-browser/health`
  instead of this session's tool calls.
- The well is measured as a whole (band + field, one `onSizeChanged`). The thread reserve
  (`threadBottomReserve`) and the "jump to latest" clearance now include the band, so nothing is
  hidden behind it.

`BrowserInviteInPromptTest` pins both halves.

## What this version does not do

- The band still appears only when the plugin answers `onyx-browser/health`; on a server without the
  plugin there is no request to show (nothing pretends otherwise).
- The takeover still opens the browser panel; the card itself is unchanged apart from its place.

---

## Files

| Package | Module | sha256 |
|---|---|---|
| `onyx-1.3.77-release.apk` | app | *see `SHA256SUMS-1.3.77.txt`* |

```bash
curl -LO https://github.com/JemaOS/hermes-onyx/releases/latest/download/SHA256SUMS-1.3.77.txt
sha256sum -c SHA256SUMS-1.3.77.txt
```