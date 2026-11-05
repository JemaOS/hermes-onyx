# Hermes Onyx 1.3.73

Two defects from one gateway log (2026-11-05): a chat that showed *nothing from the server*
for minutes while the agent was in fact working, and a reply that never came back after the
app was closed.

## 1. The profile now travels with the request

The phone polls `GET /api/sessions/<id>/messages` to recover a reply it did not watch live. The
request named no profile, so a multiplexed gateway served it from its **default** profile while
the session lived in `profiles/coder` — `404 session_not_found` every 10 s, swallowed by the
client, so the bubble stayed empty.

The profile cannot ride in the path: `/p/<profile>/` is answered by the dashboard (405), which
is why the app never used it. It now travels in `X-Hermes-Profile` (`OnyxHttp.kt`,
`OnyxHttp.PROFILE_HEADER`). A server that has no key for that profile answers 401; the client
retries once without the header and remembers the refusal, so an installation that worked before
keeps working (`OnyxHttpProfileHeaderTest`, 4 tests).

Server side (Hermes 1.x, `gateway.platforms.api_server`): the header is honoured only for a
profile the listener actually serves, and the token still has to authenticate — the profile's own
`API_SERVER_KEY`, or the listener's key when the operator sets
`gateway.api_server.share_key_across_profiles: true`. An unknown name is a 404, exactly like an
unknown prefix.

## 2. A tool that runs is not a silent server

Nothing changed here — this version ships the copy already in the tree: a long tool call says
*Exécution de <outil>*, never *Aucune nouvelle du serveur*. If a build shows the older wording,
it predates this release.

## What this version does not do

- It does not create per-profile keys for you. On a single-key gateway, set
  `share_key_across_profiles: true`; otherwise give each profile its own `API_SERVER_KEY` and
  enter it in the app's profile field.
- The gateway must run a build that knows `X-Hermes-Profile`; an older one ignores the header and
  the app falls back to the default profile (no error, same behaviour as before).

---

## Files

| Package | Module | sha256 |
|---|---|---|
| `onyx-1.3.73-release.apk` | app | *see `SHA256SUMS-1.3.73.txt`* |

Verify a download:

```bash
curl -LO https://github.com/JemaOS/hermes-onyx/releases/latest/download/SHA256SUMS-1.3.73.txt
sha256sum -c SHA256SUMS-1.3.73.txt
```
