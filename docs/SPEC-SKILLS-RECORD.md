# Server specification — demonstration → skill

**Batch 3 in `ARCHITECTURE-TOP5.md`** (§2 "Batch 3 — Teach a task", §3 "What remains on the Hermes side",
ADR-1 §7). This document does not replace the plan: it fixes the contract for the three routes and the journal
format. It contains no implementation line.

The `ARCHITECTURE-TOP5.md:<line>` references point to that plan as it stands on `main` (`e8c195a`):
it is not in this document's branch, and its line numbers are those of that version.

What the plan decides and this document **does not revisit**: the journal consists of **actions**
only (ADR-1, `ARCHITECTURE-TOP5.md:258-277`), the cap is 10 minutes, the feature sits behind
`ServerProbe`, and the `top5.recording` setting is off by default (`ARCHITECTURE-TOP5.md:121`).

---

## 0. The five decisions this document settles

| # | Decision | Why |
|---|---|---|
| D1 | The three routes live in a **second plugin**, `onyx-record`, not in `onyx-vault`. | The vault has a single guard: "a value is never read back" (`ARCHITECTURE-TOP5.md:54-56`). A journal must be read back to be converted: putting it in the same component would weaken the vault's guard in the name of batch proximity. |
| D2 | The journal lives **on the phone** during recording; it is uploaded to the server **once**, in the `stop` call. | The Computer bridge is a socket between the phone and the PC (`ComputerBridgeClient.kt:61`): the server sees no action. Nothing can therefore be observed server-side, and nothing leaves the device before the user asks to finish (ADR-1). |
| D3 | `stop` returns the **canonical** journal: what the server validated and bounded, not what the phone sent. | The phone and the server may diverge on the bounds (version, size, secret). Only one version is authoritative, otherwise the "40 events → stable markdown" test (`ARCHITECTURE-TOP5.md:120`) depends on who is right. |
| D4 | `skills.draft` is **stateless**. An expired lease never costs the draft. | The journal stays on the phone. An expiration must not punish the user for tapping 30 seconds too late. |
| D5 | `skills.draft` **writes nothing**. Saving a skill remains `POST api/skills` (`ServeSkillsApi.kt:78-88`). | A single route writes a skill, and it already exists. A draft that has not been reviewed must not land in `~/.hermes/skills`. |

---

## 1. Where the routes live

Plugin `onyx-record`, mounted under `/api/plugins/onyx-record/` by the same mechanism as `onyx-vault`
(`ARCHITECTURE-TOP5.md:163-181`, mounting proven `:183-218`). The client calls them through
`ServeClient.request(path, method, query, body, timeoutSeconds)` (`ServeClient.kt:128`), with the path
relative to the Dashboard address (`ServeClient.kt:191`).

| Route name | Method | Path | Batch |
|---|---|---|---|
| `skills.record.start` | `POST` | `api/plugins/onyx-record/skills/record/start` | 3 |
| `skills.record.stop` | `POST` | `api/plugins/onyx-record/skills/record/stop` | 3 |
| `skills.draft` | `POST` | `api/plugins/onyx-record/skills/draft` | 3 |

Three consequences of the mounting, to be written into the batch:

- **The plugin must be enabled.** Present but absent from `plugins.enabled`, its code is never
  imported and no route is mounted (`ARCHITECTURE-TOP5.md:201-211`).
- **Dashboard authentication is in front.** Without a key or session: `401`
  (`ARCHITECTURE-TOP5.md:195`). Onyx passes the same identity as for everything else.
- **No new client family.** A `ServeRecordApi.kt` file, a mirror of
  `ServeSkillsApi.kt`, nothing else (§0 rule 3, `ARCHITECTURE-TOP5.md:20-22`).

Shape of a refusal, on all three routes:

```json
{"detail": {"code": "already_recording", "message": "A recording is already open.", "session_id": "rec_01J9Z3K4Q7V8"}}
```

`code` is **the first key** of the body: `ServeClient` keeps only the first 300 characters
of an error response (`ServeClient.kt:222`). A code placed after a long message would be cut off.
`message` is in English, for the server log; **the screen never displays `message`**, it
displays its own sentence (section 8).

---

## 2. The action journal — exact format

This is the contract between the three routes. A journal that does not look like it is rejected
(`422 bad_journal`), never repaired.

### 2.1 Root object

| Field | Type | Required | Rule |
|---|---|---|---|
| `version` | integer | yes | `1`. Any other value: `422 version_unsupported`. |
| `session_id` | string | yes | The lease returned by `start`. An unknown identifier: `409 unknown_session`. |
| `started_at` | decimal | yes | Unix seconds, three decimals. Set by the phone when the lease opens. |
| `duration_ms` | integer | yes | Duration measured by the phone, capped at `600000`. The server recomputes it and keeps **its own**. |
| `end_reason` | string | yes | `stopped` or `expired`. A client cannot declare anything else. |
| `screen` | object | yes | `width_px`, `height_px` (integers > 0), `scale` (decimal > 0). Used to write "42% / 18% of the window". |
| `bridge` | object | yes | `endpoint` (host:port, without scheme, without credentials) and `session` (the bridge session identifier, as `ComputerHello` gives it). |
| `events` | array | yes | 0 to 2000 entries, `seq` strictly increasing. |

### 2.2 An event

| Field | Type | Present for | Rule |
|---|---|---|---|
| `seq` | integer | all | ≥ 1, strictly increasing. Gaps are allowed (`dropped` explains them). |
| `at_ms` | integer | all | Milliseconds since `started_at`, increasing. An `at_ms` that goes backwards: `422 bad_journal`. |
| `kind` | string | all | `focus`, `tap`, `type`, `scroll`, `navigate`, `wait`, `key`. Nothing else. |
| `target` | object | all except `wait` | `label` (string ≤ 200 or absent), `role` (string ≤ 40 or absent), `window` (foreground window title, ≤ 120 or absent), `x_pct` / `y_pct` (decimals 0..1, only for `tap`). |
| `text` | string | `type` non-secret | ≤ 2000 characters. **Absent** as soon as `secret` is true. |
| `secret` | boolean | `type` | True: the field is masked on the PC side. |
| `chars` | integer | `type` | Length of the typed text, even when `secret` is true. |
| `direction` | string | `scroll` | `up` or `down`. |
| `amount` | integer | `scroll` | Notches, 1 to 100. |
| `url` | string | `navigate` | ≤ 2048, `http` or `https` scheme **only**. |
| `key` | string | `key` | `back`, `home`, `enter`, `tab`, `escape`. |
| `ms` | integer | `wait` | 100 to 30000. |

The seven `kind` values are those of ADR-1 (`ARCHITECTURE-TOP5.md:260-262`): `focus` is added because a
procedure without "which window was in front" is not replayable, and it carries no content.
**Dragging** is not a `kind`: ADR-1 accepts losing purely visual gestures
(`ARCHITECTURE-TOP5.md:268-270`), and the draft does not claim otherwise.

### 2.3 A complete journal

```json
{
  "version": 1,
  "session_id": "rec_01J9Z3K4Q7V8",
  "started_at": 1730650000.123,
  "duration_ms": 12000,
  "end_reason": "stopped",
  "screen": {"width_px": 1440, "height_px": 900, "scale": 1.0},
  "bridge": {"endpoint": "192.168.1.24:8765", "session": "pc-7f21"},
  "events": [
    {"seq": 1, "at_ms": 0, "kind": "focus",
     "target": {"label": "Finder", "role": "window", "window": "Finder"}},
    {"seq": 2, "at_ms": 1240, "kind": "tap",
     "target": {"label": "Nouveau dossier", "role": "button", "window": "Finder",
                "x_pct": 0.421, "y_pct": 0.183}},
    {"seq": 3, "at_ms": 2600, "kind": "type",
     "target": {"label": "Nom", "role": "text field", "window": "Finder"},
     "text": "Rapport 2026", "chars": 12},
    {"seq": 4, "at_ms": 3900, "kind": "type",
     "target": {"label": "Mot de passe", "role": "secure text field", "window": "Réglages"},
     "secret": true, "chars": 9},
    {"seq": 5, "at_ms": 5200, "kind": "key",
     "target": {"label": "Mot de passe", "role": "secure text field", "window": "Réglages"},
     "key": "enter"},
    {"seq": 6, "at_ms": 6100, "kind": "scroll",
     "target": {"label": "Liste des comptes", "role": "list", "window": "Réglages"},
     "direction": "down", "amount": 3},
    {"seq": 7, "at_ms": 7400, "kind": "navigate",
     "target": {"label": "Rapport 2026", "role": "link", "window": "Navigateur"},
     "url": "https://exemple.fr/rapport"},
    {"seq": 8, "at_ms": 9600, "kind": "wait", "ms": 1800},
    {"seq": 9, "at_ms": 12000, "kind": "tap",
     "target": {"window": "Aperçu", "x_pct": 0.512, "y_pct": 0.077}}
  ]
}
```

Event 9 has neither `label` nor `role`: this is exactly ADR-1's case — it becomes a step
"tap 51% / 8% of the window", **marked for review** (`ARCHITECTURE-TOP5.md:272-273`), never
a silent step.

### 2.4 The bounds

| Bound | Value | Consequence of exceeding it |
|---|---|---|
| Length of a recording | 600 s | The recorder stops on its own, `end_reason = expired`. |
| Events | 2000 | The oldest are dropped, `truncated = true`, `dropped` counts them. |
| Serialized size | 262144 bytes | Same rule as events. |
| `text` of a `type` | 2000 characters | `422 bad_journal`. |
| `label` of a target | 200 characters | Truncated to 200, `warnings` says so. |
| Collection delay after the end | 120 s | `410 session_expired` on `stop`. |

---

## 3. `skills.record.start`

Opens a recording **lease**. Observes nothing: the server sees neither screen nor action (D2).

**Who opens it.** Only `ComputerScreen`, through an explicit user gesture on the
« Montre-moi une fois » (Show me once) entry of the screen menu (`ComputerScreen.kt:418-479`). Never on load:
the screen opens the bridge session on its own (`ComputerScreen.kt:185`), and recording must not
follow that gesture. Neither from the thread, nor from Skills, nor from Wear or Android Auto:
without that screen, there is nothing to journal.

**Request**

```json
{
  "bridge": {"endpoint": "192.168.1.24:8765", "session": "pc-7f21"},
  "discard_pending": false
}
```

| Field | Rule |
|---|---|
| `bridge.endpoint` | Required, non-empty, without scheme or credentials. |
| `bridge.session` | Required, non-empty: the bridge session identifier. This is **the only possible meaning** of "no Computer bridge" on the server side — the route cannot observe the absence of a bridge, it can only refuse a lease that names no bridge. |
| `discard_pending` | Optional, `false` by default. `true`: the pending journal is discarded and the lease opens. |
| `profile` | **Forbidden.** Present → `400 profile_not_supported`. |

Why `profile` is rejected rather than ignored: `ServeSkillsApi.create` has no profile parameter
(`ServeSkillsApi.kt:78-88`). Accepting a profile at recording time would promise a filing that
skill recording cannot keep. The profile is chosen where it is still possible:
not in this batch.

Why `bridge.session` and not a boolean: a lease attached to a named bridge session makes
mixing two PCs impossible; a journal delivered under another session is rejected
(`409 bridge_mismatch`), instead of being accepted and attributed to the wrong machine.

**Response `200`**

```json
{
  "session_id": "rec_01J9Z3K4Q7V8",
  "started_at": 1730650000.123,
  "deadline_at": 1730650600.123,
  "max_seconds": 600,
  "grace_seconds": 120
}
```

`deadline_at` is the **server's** time. The phone aligns to it and does not keep its own
cap: two clocks that diverge produce two different ends.

**Refusals**

| Status | `code` | When | What the client does |
|---|---|---|---|
| 400 | `bad_bridge` | `bridge` absent, empty, or `endpoint` with a scheme | Displays « Le pont de l'écran n'est pas joignable. » ("The screen bridge is unreachable.") |
| 400 | `profile_not_supported` | `profile` present | Does not happen if the client respects the contract; to be fixed app-side. |
| 400 | `bad_request` | Unreadable body | Displays `humanFailure` (Onyx default, not this feature's). |
| 401 | — | Dashboard not authenticated | The normal reconnection path (`ServeClient.kt:135-145`). |
| 404 | — | Route absent | **Section 8**, exact sentence. |
| 409 | `already_recording` | A lease is open | Adopts the returned `session_id` and `deadline_at`, switches to `recording`. No second lease. |
| 409 | `journal_pending` | An unconverted journal is still there | Offers to convert or discard it; `discard_pending: true` if the user discards it. |
| 503 | `recorder_unavailable` | The plugin has no memory capacity | « L'enregistrement n'est pas disponible sur ce serveur. » ("Recording is not available on this server.") |

---

## 4. `skills.record.stop`

Closes the lease, **receives** the journal, validates it, bounds it, keeps it for 120 s and returns its canonical form.

**Request**

```json
{"session_id": "rec_01J9Z3K4Q7V8", "journal": { "version": 1, "…": "…" }}
```

`journal: null` (or absent) = **abandon**: the lease closes, nothing is kept, nothing is returned.
This is the only cancellation path; there is no fourth route.

**Response `200`**

```json
{
  "session_id": "rec_01J9Z3K4Q7V8",
  "stopped_at": 1730650012.5,
  "duration_ms": 12377,
  "actions": 9,
  "bytes": 2196,
  "truncated": false,
  "dropped": 0,
  "end_reason": "stopped",
  "journal": { "version": 1, "…": "…" }
}
```

| Field | Meaning |
|---|---|
| `actions` | Number of **retained** events. |
| `bytes` | UTF-8 size of the returned `journal`, as written. |
| `duration_ms` | `stopped_at − started_at`, capped at 600000. This is the server's duration, not the client's. |
| `truncated` / `dropped` | True and non-zero count when a bound from §2.4 was hit. |
| `end_reason` | `stopped`, `expired` or `discarded`. When the lease expired before the call, it is `expired` — **the client cannot declare it otherwise**. |
| `journal` | The canonical form: same events, same order, unknown fields removed, bounds applied. Absent if `end_reason = discarded`. |

**If the session has expired.** Three moments, three behaviors:

1. **The lease is expired, we are within the 120 s collection window.** `200`, `end_reason: "expired"`,
   `truncated: true` if events arrived after 600 s (the server drops them and counts them
   in `dropped`). The journal is accepted: a cap that throws away ten minutes because the user tapped
   late is not a cap, it is a punishment.
2. **The lease expired more than 120 s ago.** `410 session_expired`, without a journal body. The
   phone keeps its own: online `skills.draft` still works (D4).
3. **The lease never existed** (unknown `session_id`). `409 unknown_session`. The phone can
   still convert its journal online.

`stop` is **idempotent** within the collection window: a second call returns the same `journal` and the
same `bytes`, to the byte. This is what allows recovering a journal after the screen dies.

**Refusals**

| Status | `code` | When |
|---|---|---|
| 400 | `bad_request` | Unreadable body, or `journal` without `session_id`. |
| 404 | — | Route absent (section 8). |
| 409 | `not_recording` | No lease open: nothing to stop. |
| 409 | `bridge_mismatch` | `journal.bridge.session` is not the lease's. |
| 409 | `unknown_session` | `session_id` unknown to this server. |
| 410 | `session_expired` | Lease expired more than 120 s ago. |
| 413 | `journal_too_large` | Journal > 262144 bytes **before** bounding. |
| 422 | `bad_journal` | §2 not respected. |
| 422 | `version_unsupported` | `version` ≠ 1. |
| 422 | `secret_text_present` | A `secret: true` event carries a `text`. See section 6. |

---

## 5. `skills.draft`

Receives a journal, returns a Markdown draft and the list of steps to review. **Stateless**: two
calls with the same journal return the same byte. No writes, no files, no profile.

**Request**

```json
{
  "journal": { "version": 1, "…": "…" },
  "journal_id": null,
  "name_hint": "creer-un-dossier-dans-finder"
}
```

| Field | Rule |
|---|---|
| `journal` | The complete journal, same form as in §2. |
| `journal_id` | Optional, exclusive with `journal`. The `session_id` of a journal still in collection. Both together: `400 two_journals`. |
| `name_hint` | Optional. Otherwise, the name is derived from the first `focus` and the first named target, lowercased with hyphens. |

**Response `200`**

```json
{
  "name": "creer-un-dossier-dans-finder",
  "title": "Créer un dossier dans Finder",
  "steps": 7,
  "markdown": "---\nname: creer-un-dossier-dans-finder\ndescription: Créer un dossier dans Finder, puis ouvrir un lien.\n---\n\n## Quand l'utiliser\n\nQuand un dossier doit être créé dans Finder à partir d'un nom donné.\n\n## Procédure\n\n1. Ouvrir **Finder**.\n2. Toucher **Nouveau dossier**.\n3. Saisir `Rapport 2026` dans **Nom**.\n4. Saisir le mot de passe dans **Mot de passe** (champ masqué, non enregistré).\n5. Valider avec **Entrée**.\n6. Faire défiler **Liste des comptes** vers le bas (3 crans).\n7. Ouvrir le lien **Rapport 2026** (https://exemple.fr/rapport).\n8. Toucher 51 % / 8 % de la fenêtre **Aperçu** (à relire : aucun libellé).\n\n## Vérification\n\nLe dossier est visible dans Finder et le lien s'ouvre dans le navigateur.\n",
  "review": [
    {"seq": 9, "step": 8, "reason": "no_label", "line": 21},
    {"seq": 4, "step": 4, "reason": "secret_field", "line": 17}
  ],
  "warnings": ["label_truncated"],
  "journal_bytes": 2196
}
```

| Field | Meaning |
|---|---|
| `name` | The skill's folder name, lowercase with hyphens, ≤ 64 characters. It is `ServeSkillsApi.create(name, …)` that will write it. |
| `title` | The readable title, first line of the body. |
| `steps` | Number of numbered steps in `markdown`. |
| `review` | The steps to review, in draft order. Empty = nothing to review. |
| `review[].step` | The step number (1 = first line of « Procédure » (Procedure)). |
| `review[].line` | Line of the `markdown`, starting at 1. **Indicative**: the editor clamps it to the actual length and does not rely on it to place the cursor. |
| `review[].reason` | `no_label`, `secret_field`, `no_window`, `long_wait`, `unsupported_kind`. Closed list. |
| `warnings` | What was **changed** to fit: `label_truncated`, `text_truncated`, `url_dropped`, `wait_clamped`, `events_dropped`. |
| `journal_bytes` | The size of the received journal, so the screen can say what it sent. |

**The draft does not guess.** Each event becomes a step, in order. A `wait` of less than
500 ms does not become a step (it teaches nothing) but counts in `warnings` if it was
shortened. A `kind` unknown to this server version becomes a "to review" step with reason
`unsupported_kind`: it is the only way not to lose a step silently.

**No session data in the Markdown.** No date, no `session_id`, no hostname,
no bridge address. Otherwise the same journal would produce two different drafts, and the
converter test (`ARCHITECTURE-TOP5.md:120`) could not be stable.

**The Markdown shape follows what the editor already advertises.** The editing screen promises "Front matter
(name, description, version, metadata) then When to Use, Procedure, Pitfalls, Verification"
(`SkillEditorScreen.kt:146`). The draft writes exactly those sections: front matter, « Quand
l'utiliser » (When to use), « Procédure » (Procedure), « Vérification » (Verification). A draft of another shape would look foreign
in its own editor.

**Refusals**

| Status | `code` | When |
|---|---|---|
| 400 | `bad_request` | Unreadable body, neither `journal` nor `journal_id`. |
| 400 | `two_journals` | Both fields are present. |
| 404 | — | Route absent (section 8). |
| 409 | `still_recording` | `journal_id` designates a lease still open. |
| 409 | `no_journal` | Unknown `journal_id`, or abandoned lease. |
| 413 | `journal_too_large` | Journal > 262144 bytes. |
| 422 | `bad_journal` | §2 not respected. |
| 422 | `version_unsupported` | `version` ≠ 1. |
| 422 | `secret_text_present` | A `secret: true` carries a `text`. |

---

## 6. What is forbidden to record

Journal prohibitions, enforced by the server and not only by the phone.

| Prohibited | How it is upheld | Rejection if the rule is violated |
|---|---|---|
| **Pixels** — capture, thumbnail, cropping, screen OCR | No field carries an image. The `kind` list is closed (§2.2). | `422 bad_journal` (an `image`, `png`, `screenshot` field at any depth). |
| **Audio** — microphone, system sound, transcription | No audio `kind`. The voice scene is not on this path. | `422 bad_journal`. |
| **Clipboard** | The clipboard is never read. A **paste** is recorded as a `type` with `secret: true` and `chars` only: the sensitivity of clipboard content is unknown at the moment of the paste, so it is treated as secret by default. | A `type` event carrying `from_clipboard: true` and a `text`: `422 bad_journal`. |
| **Field marked secret** | `text` absent, `chars` alone. The draft writes « champ masqué, non enregistré » ("masked field, not recorded"). | `422 secret_text_present` **if** a `text` accompanies `secret: true`. The server rejects, it does not clean silently — same rule as the template link containing `apiKey` (`ARCHITECTURE-TOP5.md:133`). |
| **Local path of a document** | `url` accepts only `http` and `https`. An opened file becomes a `label` carrying its **name**, never its path. | `422 bad_journal` (`file:` or `onyx:` scheme). |
| **Full bridge credential** | `bridge.endpoint` is a host:port; a credential, token or password in the address: `400 bad_bridge`. | `400 bad_bridge`. |

The phone applies the same rules **before** sending: the server is the second lock, not the
first. A journal that did not pass the first must not exist.

---

## 7. State machine

Four states on the phone, two on the server. The server does not know `drafted`: `skills.draft`
is stateless (D4).

```
        start(200)              stop(journal)
 idle ────────────► recording ──────────────► stopped ─── draft(200) ───► drafted
   ▲                    │                        │                          │
   │   stop(null)       │ 600 s (server)         │ start(discard_pending)   │ start(200)
   └────────────────────┘                        └──────────────────────────┴──► recording
```

### 7.1 What the server holds at each step

| Phone state | Server state | Detail |
|---|---|---|
| `idle` | nothing | No lease. |
| `recording` | `recording` | Lease open, `deadline_at` known to both. |
| `stopped` | `journal_pending` | Canonical journal kept for 120 s. |
| `drafted` | `journal_pending` | The server has seen nothing of the draft; the journal remains until the end of the 120 s. |

### 7.2 Invalid transitions

| From | Gesture | Response | What the client displays |
|---|---|---|---|
| `recording` | `start` | `409 already_recording` | The button does not exist in this state. If the call is sent anyway (stale view), the client **adopts** the returned `session_id` and stays in `recording`. |
| `idle` | `stop` | `409 not_recording` | « Aucun enregistrement n'est ouvert. » ("No recording is open.") The button does not exist. |
| `recording` | `draft` | `409 still_recording` | The conversion button does not exist before stopping. |
| `idle` | `draft` | `409 no_journal` | « Rien à convertir : la démonstration n'a pas été enregistrée. » ("Nothing to convert: the demonstration was not recorded.") |
| `stopped` | `start` | `409 journal_pending` | « Une démonstration attend d'être convertie. » ("A demonstration is waiting to be converted.") Two outcomes offered: convert, or `discard_pending: true`. |
| `stopped` / `drafted` | `stop` | `200`, identical journal | Nothing to report: the call is idempotent. |
| `drafted` | `draft` | `200`, same byte | Nothing to report: converting the same journal twice is allowed. |

None of these transitions is a failure: the client must **never** display « Réessayez » ("Try again") for
any of them. This is the distinction that `looksLikeMissingRoute` already makes for absent routes
(`PageStates.kt:113-123`); here, the same rule applies to state conflicts.

### 7.3 What the screen shows, state by state

| State | Where | What is displayed |
|---|---|---|
| `idle` | `ComputerScreen`, menu | « Montre-moi une fois » (Show me once) entry, enabled. |
| `idle`, no bridge configured | `ComputerScreen`, menu | **No entry.** `ComputerBridgeStore.configured` is false (`ComputerBridgeStore.kt:63`): there is nothing to show. |
| `idle`, bridge configured, phase ≠ `LIVE` | `ComputerScreen`, menu | Entry **greyed out**, with « L'écran du PC n'est pas encore ouvert. » ("The PC screen is not open yet.") Transient state, it resolves on its own. |
| `recording` | banner at the top of the preview | « Enregistrement — arrêt automatique dans 9 min 12 s. » ("Recording — automatic stop in 9 min 12 s.") plus the « Arrêter » ("Stop") button. |
| `stopped` | result sheet | « Démonstration terminée : 9 actions, 12 s. » ("Demonstration finished: 9 actions, 12 s.") then « Convertir en skill » ("Convert to skill") / « Jeter » ("Discard"). |
| `drafted` | `SkillEditorScreen` | The pre-filled Markdown, and one line per step to review (`review`). |
| `drafted`, save refusal | `SkillEditorScreen` | `humanFailure` from `POST api/skills` — the existing refusal, not a new sentence. |

The `top5.recording` setting, off by default (`ARCHITECTURE-TOP5.md:121`), removes the menu entry,
exactly like the absence of a bridge. It does not grey out anything: a feature that has been turned off is not a
broken feature.

---

## 8. Fallbacks — and the exact sentence when the route does not exist

### 8.1 Server without the route

Detection via `looksLikeMissingRoute`: HTTP `404` or `-32601` (`PageStates.kt:113-123`). The plugin
absent, not enabled, or an older Dashboard all give the same response — this is
why the sentence does not talk about the server but about the feature.

| Route absent | Exact sentence displayed (FR) | EN key |
|---|---|---|
| `skills.record.start` | « Ce serveur ne sait pas enregistrer une démonstration (route absente). » | `This server can't record a demonstration (route missing).` |
| `skills.record.stop` | « Ce serveur ne sait pas arrêter un enregistrement (route absente). » | `This server can't close a recording (route missing).` |
| `skills.draft` | « Ce serveur ne sait pas transformer une démonstration en skill (route absente). Le journal reste sur cet appareil. » | `This server can't turn a demonstration into a skill (route missing). The journal stays on this device.` |

Three rules around these sentences:

- **`humanFailure` is not suitable.** On a `404`, it says « Endpoint introuvable (404). Vérifiez
  l'URL de base. » ("Endpoint not found (404). Check the base URL.") (`TransportError.kt:141`): this is wrong, the address is right and the route is missing.
  On a `-32601`, it says « Le serveur a refusé : … » ("The server refused: …") (`TransportError.kt:160`): this is true but
  useless. The three sentences above replace those two.
- **They live in the catalog**, not in a screen: an `OnyxFrenchTop5.kt` file added to the
  concatenation in `OnyxLocalization.kt:221` (`ARCHITECTURE-TOP5.md:241-242`). A label written in
  plain text in a `MenuItem(…)` without a French entry makes `MenuAndSheetLocalizationTest` fail
  (`MenuAndSheetLocalizationTest.kt:56-73`).
- **The sentence promises nothing.** It says neither "soon" nor "update the server": the
  phone does not know the Dashboard version.

### 8.2 Offline

`TransportError.Unreachable`: the sentence is Onyx's (« Serveur injoignable… », "Server unreachable…",
`TransportError.kt:216-227`), not one of the three above. An unreachable server is not a
server without the route; confusing the two would send the user looking for a useless update.

### 8.3 What remains true in all cases

- A recording in progress **survives** a network interruption: the journal is on the phone (D2).
  The lease, however, keeps running; `stop` may arrive 60 s later.
- `stop` lost (response never received): the client retries with the **same** `session_id`; the call is
  idempotent, it does not duplicate the journal.
- The draft depends on no already-mounted route except `POST api/skills` for the final
  save (`ServeSkillsApi.kt:78-88`), which has always existed.

---

## 9. Anchors in the existing code

| Statement in this document | Real file |
|---|---|
| Everything goes through a single transport, `request(path, method, query, body, timeoutSeconds)` | `core/.../data/net/ServeClient.kt:128` |
| The body of an error is cut at 300 characters | `core/.../data/net/ServeClient.kt:222` |
| The path is relative to the Dashboard address | `core/.../data/net/ServeClient.kt:191` |
| A dead cookie is caught once | `core/.../data/net/ServeClient.kt:135-145` |
| The mirror of a route family is an `*Api.kt` file | `core/.../data/net/ServeSkillsApi.kt:49-52` |
| Creating a skill does **not** take a profile | `core/.../data/net/ServeSkillsApi.kt:78-88` |
| An absent route returns a "disabled" object rather than an exception | `core/.../data/net/ServeChatActions.kt:516-526` |
| The "this server does not offer this read" refusal already exists | `core/.../data/net/ServeChatActions.kt:400-419` |
| The state of a read is `LoadState`: `Loading`, `Ready`, `Failed(reason)` | `app/.../ui/chat/components/ChatActionSheet.kt:808-812` |
| A `404` or a `-32601` is recognized as an absent route | `app/.../ui/common/PageStates.kt:113-123` |
| `humanFailure` says "check the URL" on a 404 | `core/.../data/net/TransportError.kt:141` |
| The Computer bridge already exposes tap, typing, scrolling, key, command | `core/.../data/net/ComputerBridgeClient.kt:571-685` |
| The bridge phase is `IDLE` / `CONNECTING` / `LIVE` / `RECONNECTING` / `FAILED` | `core/.../data/net/ComputerBridgeClient.kt:71-79` |
| "No bridge" is read from `configured` | `app/.../data/local/ComputerBridgeStore.kt:63` |
| The Computer screen opens its session on load — recording must not follow | `app/.../ui/computer/ComputerScreen.kt:185` |
| The Computer screen menu is a list of conditional items | `app/.../ui/computer/ComputerScreen.kt:418-478` |
| The skill editor advertises the Markdown sections | `app/.../ui/capabilities/SkillEditorScreen.kt:146` |
| Its signature accepts a default parameter without breaking a caller | `app/.../ui/capabilities/SkillEditorScreen.kt:54-61` |
| Settings are keys of `onyx_settings` | `app/.../data/local/OnyxDataStore.kt:53-56` |
| The French catalog is concatenated at a single point | `core/.../localization/OnyxLocalization.kt:221` |
| A menu label without translation makes a test fail | `core/.../test/.../MenuAndSheetLocalizationTest.kt:56-73` |

---

## 10. Batch anchor in the app

No new destination: `Route` (`OnyxApp.kt:234`) and `RouteRetours.kt` do not move.

- The menu entry: `ComputerScreen`, one more line in the conditional list
  (`ComputerScreen.kt:418-479`).
- The draft: `SkillEditorScreen` receives `draft` as a **parameter with a default value**, as
  `BotEditorScreen` received its null lambdas (`ARCHITECTURE-TOP5.md:13-16`). Without a draft,
  the screen is today's, identically.
- The routine: the existing link to `CronScreen` (`CronScreen.kt:98-107`). This batch adds no
  cron route.

---

## 11. What this batch does not do

- It does not record visual gestures (drag, draw, choose a color). ADR-1 accepts this
  (`ARCHITECTURE-TOP5.md:268-270`).
- It writes no skill. `skills.draft` returns text; `POST api/skills` writes it.
- It creates no routine. The button borrows `CronScreen`'s existing path.
- It journals nothing in the background: no recording outside `ComputerScreen`, no
  foreground service.
- It touches neither `Computer`, nor the voice scene, nor the rooms (§0 rule 5,
  `ARCHITECTURE-TOP5.md:26-28`).

---

## 12. Expected evidence

1. **Pure converter**: 40 events in, an identical Markdown out, two calls
   give the same byte (`ARCHITECTURE-TOP5.md:120`).
2. **Secret rejection**: a journal in which a `secret: true` carries a `text` returns `422
   secret_text_present`, and the response body does **not** contain that text.
3. **Bounds**: 2001 events → `truncated: true`, `dropped: 1`, `actions: 2000`.
4. **Expiration**: a lease exceeded by 100 s returns `200 end_reason: expired`; exceeded by 200 s returns
   `410`, and online `skills.draft` still returns a draft.
5. **Idempotence**: two `stop` calls within the window return the same `bytes`.
6. **Absence**: without the plugin, the menu entry is not drawn, and none of the three sentences of
   §8.1 leaves room for « Réessayez » ("Try again").
7. **Localization**: the three sentences of §8.1 and the labels of §7.3 are in the French
   catalog; `MenuAndSheetLocalizationTest` passes.

---

## 13. Open decisions

| # | Question | What I propose | What is needed to decide |
|---|---|---|---|
| O1 | Second `onyx-record` plugin, or routes added to `onyx-vault`? | Second plugin (D1). | The opinion of whoever will install it: two folders to enable, or a single component that does two things. |
| O2 | Is the journal uploaded at `stop` (D2), or does it continue to live only on the phone with an online `skills.draft`? | It is uploaded at `stop`, because the task requires that `stop` return the journal. | Nothing technical: it is a product choice about what leaves the device. |
| O3 | Should there be a `POST …/record/discard` rather than `stop` with `journal: null`? | No: three routes are enough, and `stop` without a journal is already the cancellation. | An API review. |
| O4 | Is the automatically derived skill name acceptable, or must it always be requested before conversion? | Derived, with `name_hint` to correct it. The editor allows changing it anyway (`SkillEditorScreen.kt:124-129`). | — |

## 14. Points in the plan that seemed inconsistent to me

1. **"No Computer bridge → button absent" (`ARCHITECTURE-TOP5.md:119`).** The server cannot
   observe the absence of a bridge: it does not see it (D2). The rule is therefore entirely client-side
   (`ComputerBridgeStore.kt:63`), and the route merely requires a non-empty `bridge.session`. The
   plan suggests the server refuses; it only refuses an anonymous lease.
2. **"Bridge without capture route → greyed-out button with the server's sentence"** (same line). There is
   no server sentence when the route is absent: there is no one to say it. And §0 rule
   2 (`:17-19`) requires that an entry **disappear** when the server does not know how to serve it. I followed
   §0: absent, not greyed out. Greyed out is reserved for a **transient** state of the bridge (phase ≠ `LIVE`), where
   a sentence of ours is enough.
3. **"B3: memory only… then compressed `buf`" (`:64`).** Whose "memory" is not stated. I
   settled it: the memory is the phone's, the server receives only a complete journal (D2).
4. **`skills.record.start/stop` "or 100% local if you prefer" (`:117`).** The two are not
   equivalent: a local version has no lease, therefore no shared `deadline_at`, therefore no
   `410`. This document describes the server version, the one with the three routes.
5. **Batch 3 is announced at "1.5 weeks, half of it server-side" (`:111`) for three routes.** This
   document adds a fourth mechanical piece that was not planned: journal validation and bounding on the server
   side (§2.4, §6). That is half a day more, not a week — but better to
   write it than to discover it.
