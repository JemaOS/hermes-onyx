# Hermes Onyx 1.3.74

One defect, seen in the gateway log of 2026-11-05 21:06: a conversation opened from the list
polled `GET /api/sessions/<id>/messages` every 5 s and got `404 session_not_found`, while the
session lived in `profiles/coder`.

## The profile now travels with the read, not with the screen

The four places that re-read a session (`loadRemoteThread`, the empty-reply recovery, the silent
turn rescue, and the notification watcher) took the profile from the **screen's** current bot
(`ChatUiState.botProfile`), which is `null` for a conversation that belongs to no bot object — so
no profile was announced at all and the server answered from its default profile.

- `ServeSessionMessages.rows(sessionId, profile)` / `reply(sessionId, profile)` now take the
  profile explicitly and put it in the URL (`…/messages?profile=coder`), in addition to the
  `X-Hermes-Profile` header: a relay can drop a header, not a query parameter
  (`ServeSessionMessagesProfileTest`, 3 tests).
- `ChatViewModel.loadRemoteThread` and the recovery path pass the **conversation's** profile
  (`Conversation.botProfile`), falling back to the server's own profile; `ReplyWatcher` does the
  same for background notifications.

Server side (Hermes 1.x, `gateway.platforms.api_server`): `?profile=<name>` is resolved exactly
like the header — a profile the listener serves, unknown names refused with 404, and the token
still has to authenticate. So this button only lights up against a gateway that knows the
parameter; on an older one it is ignored and the app behaves as before.

## What this version does not do

- It does not create per-profile keys. On a single-key gateway set
  `gateway.api_server.share_key_across_profiles: true`; otherwise give the profile its own
  `API_SERVER_KEY` and enter it in the app.
- The conversation must be tagged with its profile. A conversation the app has never seen in a
  per-profile listing has nothing to announce — open the list once after installing.

---

## Files

| Package | Module | sha256 |
|---|---|---|
| `onyx-1.3.74-release.apk` | app | *see `SHA256SUMS-1.3.74.txt`* |

Verify a download:

```bash
curl -LO https://github.com/JemaOS/hermes-onyx/releases/latest/download/SHA256SUMS-1.3.74.txt
sha256sum -c SHA256SUMS-1.3.74.txt
```