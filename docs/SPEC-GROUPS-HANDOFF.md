# Server specification — main bot and handoff

**Batch 5 of `ARCHITECTURE-TOP5.md`** (§2 "Batch 5 — Lead bot and handoff", §3 "What remains
on the Hermes side"). This document fixes the contract of two routes and the **exact shape** of
the handoff event, so that the interface has nothing to guess. No line of implementation appears
here.

The references `ARCHITECTURE-TOP5.md:<line>` designate that plan as it is on `main` (`e8c195a`):
it is not in this document's branch, and its line numbers are those of that version.

What the plan decides and this document does not revisit: the routes are **in the Hermes core**,
not in a plugin, because a lead is a room concept (`ARCHITECTURE-TOP5.md:159`); the addition
is **additive**, no navigation destination moves (`:26-28`); the journal is still rendered by the
same list (`:142`).

---

## 0. The four decisions this document settles

| # | Decision | Why |
|---|---|---|
| D1 | The two routes are **RPC** methods (`groups.*`), not HTTP routes. | Rooms already go through the Dashboard RPC channel (`ServeGroupsRpc.kt:31`, `RpcChannel.kt:13`). An HTTP route for the same concept would mean two transports for a single room. |
| D2 | The lead is a **default recipient**, not a chief. | `@name` already addresses a member directly (`GroupRoomScreen.kt:956-957`). A lead that filtered the others would break it. |
| D3 | The handoff writes **one** event, `bot.handoff`, and nothing else. | A journal that writes two lines for a single gesture is read twice. The cancelled turn, for its part, already has its line (`turn.cancelled`). |
| D4 | Neither event carries **any `member_id` key** in its payload. | `GroupEvent.from` reads `payload.member_id` **before** the actor (`GroupThread.kt:83`). Such a key would silently steal the identity of the line's author. |

---

## 1. Where the routes live

Two methods of the Hermes core, on the rooms channel, next to `groups.send` and `groups.stop`
(`ServeGroupsRpc.kt:113-166`). They follow the same call shape: `rpc.call(method, params,
timeoutMillis)` (`RpcChannel.kt:13`), with the room call timeout (`CALL_TIMEOUT`, 60 s,
`ServeGroupsRpc.kt:286`).

| Name | Kind | Parameters |
|---|---|---|
| `groups.lead` | RPC | `room_id`, `member_id` (or `null`) |
| `groups.handoff` | RPC | `room_id`, `from_member_id`, `to_member_id`, `task_id` (optional), `summary` (optional) |

**Discovery.** Both methods announce themselves in `groups.capabilities.methods`, which the client
already reads (`ServeGroupsRpc.kt:297-303`). `GroupSupport` gains two boolean fields defaulting
to `false`: the addition breaks no caller, including the test that constructs the object
positionally (`ServeGroupsRpcTest.kt:52`). This is the absence mechanism provided by the plan
(`ARCHITECTURE-TOP5.md:143`), and it requires no probe route.

**Shape of a refusal.** An application error from the server arrives as `TransportError.Rpc(code, detail)`
(`TransportError.kt:49`). The codes below are therefore **negative or application RPC codes**,
not HTTP statuses.

---

## 2. `groups.lead`

Designates a room's lead, or removes it.

**Request**

```json
{"room_id": "onyx-9f2c1a4e", "member_id": "coder"}
```

| Field | Rule |
|---|---|
| `room_id` | Required. Unknown room: `404 room_not_found`. |
| `member_id` | Required **present**. A string designates the lead; `null` removes it. Absent ≠ `null`: absent returns `400 bad_request`, so that no client removes a lead by omission. |

**Response**

```json
{
  "room_id": "onyx-9f2c1a4e",
  "lead": {"member_id": "coder", "profile": "coder", "label": "Coder"},
  "previous_member_id": "researcher",
  "seq": 1237
}
```

| Field | Meaning |
|---|---|
| `lead` | The lead after the call, or `null` if there is none any more. `label` is the member's room name, the one the roster already provides (`GroupRoom.kt:34-35`). |
| `previous_member_id` | The previous lead, or `null`. Lets the screen undo without re-reading. |
| `seq` | The number of the `room.lead_changed` written. The screen does **not** draw an optimistic line: it re-reads (`look()`), and this value tells it what it will find. |

**What the call writes.** One `room.lead_changed` event, at the end of the journal, with its own
`seq`. Nothing else moves: neither the room name, nor its members, nor its previous `latest_seq`.

**Refusals**

| Code | `code` | When |
|---|---|---|
| 404 | `room_not_found` | Unknown room, or ended. |
| 404 | `profile_not_found` | The member's profile no longer exists on this server. |
| 409 | `member_not_in_room` | `member_id` is not a member of this room. |
| 409 | `already_lead` | This member is already the lead. No event is written. |
| 409 | `room_stopping` | Some bots are still stopping. Retry: this is the pattern already used for `disband` (`ServeGroupsRpc.kt:185-196`). |
| 400 | `bad_request` | Empty `room_id`, or absent `member_id`. |
| −32601 | — | The server does not know the method. See §7. |

---

## 3. `groups.handoff`

Hands over from one member to another, with the useful context.

**Request**

```json
{
  "room_id": "onyx-9f2c1a4e",
  "from_member_id": "writer",
  "to_member_id": "coder",
  "task_id": "task_7f3a",
  "summary": "Plan écrit ; reste le code."
}
```

| Field | Rule |
|---|---|
| `room_id` | Required. |
| `from_member_id` | Required. This is the member letting go of the hand — in practice the lead, or a bot finishing its turn. It must be in the room. |
| `to_member_id` | Required. Different from `from_member_id`: same member → `409 same_member`. |
| `task_id` | Optional. Present: this is `from_member_id`'s current turn, and it is **cancelled** before the other starts. Absent: nothing is cancelled, the hand passes for the next turn. |
| `summary` | Optional. Plain text, ≤ 280 characters, no control characters. An empty string counts as absent. |

`summary` is **the** useful context: what is done, what remains. It is not mandatory because
the room journal **is** already the context — a recipient can re-read it. A mandatory summary
would push people to write "continued" just to pass.

**Response**

```json
{
  "room_id": "onyx-9f2c1a4e",
  "seq": 1240,
  "from_member_id": "writer",
  "to_member_id": "coder",
  "cancelled_task_id": "task_7f3a"
}
```

`cancelled_task_id` is `null` when the request did not provide a `task_id`. The screen re-reads the journal
just after (`look()`), as it already does after `stop` and `retry` (`GroupRoomFeed.kt:524-531`):
the line appears on the next tick, which is 500 ms when the room is working
(`GroupRoomFeed.kt:581-582`).

**What the call writes, in this order**

1. `turn.cancelled` with `reason: "handed_over"` — **only** if `task_id` was present. The leaver's
   turn stops there.
2. `bot.handoff` — a single event, exact shape in §4.
3. The recipient's turn starts, if there is something to do.

**Refusals**

| Code | `code` | When |
|---|---|---|
| 404 | `room_not_found` | Unknown room. |
| 409 | `member_not_in_room` | One of the two members is not in the room. |
| 409 | `same_member` | `from_member_id` = `to_member_id`. |
| 409 | `task_not_yours` | `task_id` is not a current task of `from_member_id`. |
| 409 | `room_stopping` | Some bots are still stopping. |
| 422 | `summary_too_long` | `summary` > 280 characters. |
| 400 | `bad_request` | Empty required field. |
| −32601 | — | The server does not know the method. See §7. |

No refusal writes an event. A refused handoff leaves no half-made trace:
this is the rule that makes the state readable after an error.

---

## 4. The exact shape of the event

This is the part of the contract the interface reads. It must not change without changing this document.

### 4.1 `bot.handoff`

```json
{
  "seq": 1240,
  "kind": "bot.handoff",
  "actor": {"kind": "member", "id": "writer"},
  "payload": {
    "from_member_id": "writer",
    "to_member_id": "coder",
    "task_id": "task_7f3a",
    "summary": "Plan écrit ; reste le code."
  },
  "created_at": 1730650042.5
}
```

| Field | Rule | What the interface does with it |
|---|---|---|
| `seq` | Integer, at the end of the journal. | The line's order. Nothing is re-sorted: `rows` is a filter, not a sort (`GroupRoomFeed.kt:98`). |
| `kind` | Exactly `bot.handoff`. | The only discriminant. |
| `actor.kind` | `member`. | — |
| `actor.id` | The leaver, **equal** to `from_member_id`. | Becomes `actorID`: `GroupEvent.from` reads `actor.id` when `actor.kind` is `member` and the payload has no `member_id` (`GroupThread.kt:83-84`). |
| `payload.from_member_id` | String, member of the room. | The name displayed on the left. |
| `payload.to_member_id` | String, member of the room. | The name displayed on the right. |
| `payload.task_id` | String or `null`. | Not displayed. |
| `payload.summary` | String ≤ 280 or `null`. | Displayed **as is**, never reworded. |
| `created_at` | Decimal Unix seconds. | `GroupEvent.from` converts (`GroupThread.kt:86`). |

**Two traps, each to be held by a test.**

1. **No `member_id` key in `payload`.** `GroupEvent.from` reads `payload.firstString("member_id")`
   before the actor (`GroupThread.kt:83`). Such a key would make `actorID` the recipient instead of the
   leaver, and the sentence would name the same person twice. The server does not write one; the test
   must reject a journal that carries one.
2. **`bot.handoff` does not end the discussion.** `endsDiscussion` only recognises
   `room.activity` and `room.stop_requested` (`GroupThread.kt:46`). A `bot.handoff` slipped into that
   list would make `discussing` believe the turn is over (`GroupRoomFeed.kt:73-78`), and the room
   would stop waiting for the answer it has just requested.

### 4.2 `room.lead_changed`

```json
{
  "seq": 1237,
  "kind": "room.lead_changed",
  "actor": {"kind": "user"},
  "payload": {
    "lead_member_id": "coder",
    "previous_member_id": "researcher",
    "reason": "named"
  },
  "created_at": 1730650001.0
}
```

| Field | Rule |
|---|---|
| `payload.lead_member_id` | The new lead, or `null` if there is none any more. |
| `payload.previous_member_id` | The previous one, or `null`. |
| `payload.reason` | `named` (designated), `cleared` (removed), `member_left` (its profile has disappeared). Closed list. |
| `actor` | `user` for a human gesture, `system` for `member_left`. |

Once again: **no `member_id` key** (D4).

### 4.3 The lines the interface must know how to compose

The journal only renders a line if `GroupEvent.note(room)` returns a text (`GroupRoomFeed.kt:98`);
`RoomEventRow` then displays it centred, as a thread footnote, without new composition
(`GroupRoomScreen.kt:1009-1016`). The two new cases are therefore added to `note()`
(`GroupThread.kt:49-69`), following the model of `turn.settled` (`:52`) and `room.renamed` (`:66`): the id
comes from the payload, the name comes from the roster.

| Case | Exact text (FR) | EN key | Fallback when the member is unknown |
|---|---|---|---|
| `bot.handoff` | "Writer passe la main à Coder." | `%1$s passed the lead to %2$s.` | The unknown name becomes "a bot" (`OnyxLocalization["A bot"]`, already used in `GroupThread.kt:50`). |
| `bot.handoff` with `summary` | The same sentence, then a line break, then the summary **word for word**. | same | same |
| `room.lead_changed`, `reason` = `named` | "Coder mène la salle." | `%1$s leads the room.` | "A bot leads the room." |
| `room.lead_changed`, `reason` = `cleared` | "Plus de meneur dans la salle." | `This room has no lead.` | — |
| `room.lead_changed`, `reason` = `member_left` | "Coder a quitté la salle : plus de meneur." | `%1$s left the room: no lead.` | same |

**A third case, not to be forgotten.** `turn.cancelled` with `reason: "handed_over"` must show
**nothing**. The existing branch returns "%1$s was stopped" for any unknown reason
(`GroupThread.kt:56-58`); without an explicit exception for `handed_over`, the journal would show
"Writer was stopped" **and** "Writer passe la main à Coder" for a single gesture. The
existing branch already excludes one reason (`superseded_by_newer_user_event`): `handed_over` is added to the
same condition.

---

## 5. What the lead changes, and what it does not change

### 5.1 What it changes

| | Without a lead (today) | With a lead |
|---|---|---|
| Who receives a request without `@name` | The server driver chooses. Onyx only sees it through `driver_status` (`GroupThread.kt:114-127`) and through the empty-screen hint (`GroupRoomScreen.kt:956-957`). | The lead, first. |
| Who distributes | No one identifiable: the distribution is internal to the driver. | The lead, and its decision **is written**: each distribution is a `bot.handoff` in the journal. |
| What the screen can show | Nothing of the distribution. | The handoff line, and the current lead. |
| Turn order | Implicit. | Explicit in the journal, hence re-readable and testable. |

The gain is not "a chief": it is **a written order**. Today, a room that answers in an
unexpected order leaves no trace of the decision.

### 5.2 What it does not change

| Thing | Why it does not move |
|---|---|
| `@name` always addresses a member directly | The room hint already promises it (`GroupRoomScreen.kt:956-957`). The lead is a default, not a toll booth (D2). |
| `groups.stop` stops everyone, lead included | It only takes a `room_id` (`ServeGroupsRpc.kt:157-166`). Nothing to change. |
| `groups.approve` keeps its per-member identity | The request carries `member_id` (`ServeGroupsRpc.kt:211-224`). The lead cannot approve on someone else's behalf: that would be approving a command it did not request. |
| The roster of 2 to 6 members | Fixed at creation (`GroupRoom.kt:116-118`). `groups.lead` adds and removes nobody. |
| The journal's order and numbering | One more event, at the end. No existing line is moved. |
| The voice scene | It only reads messages and drafts (`GroupRoomFeed.kt:110-161`). |
| The Feed tab and the Files tab | `roomThreadMessages` keeps only `isMessage` (`GroupRoomJournal.kt:30`). A handoff is not a message: it does not invite itself there. This is **intentional** — the Feed is a conversation, not an operations log. |
| The room's name, identifier, date | `groups.lead` does not touch them. |

---

## 6. If the lead dies along the way

Five cases. Each has a precise outcome, and none is "we'll see".

| Case | What the server does | What the screen shows |
|---|---|---|
| The recipient's turn fails after the handoff | `turn.failed` for the recipient (`GroupThread.kt:53-55`). **The hand stays with the recipient.** No automatic return. | "Coder n'a pas pu répondre : …" ("Coder could not answer: …"), and the room keeps Coder as lead. |
| The leaver dies **before** `groups.handoff` is written | Nothing is written. The hand does not move. The turn is lost as today (`turn.failed`). | No handoff line. This is correct: there was no handoff. |
| The lead dies during its turn, without `groups.handoff` | `turn.failed` or `turn.cancelled`, depending on the cause. The lead **remains** designated. | The room stays with that lead; the person can designate another one. |
| The lead's profile disappears from the server | The server removes the lead and writes `room.lead_changed` with `lead_member_id: null`, `reason: "member_left"`. A **single** write, not two. | "Coder a quitté la salle : plus de meneur." The room behaves exactly as before this batch. |
| The server restarts mid-turn | The journal is on disk: the handoff, if it was written, remains. `groups.state` returns `blocked` if Hermes has lost track of the turn (`GroupThread.kt:96-100`). | The thread keeps the handoff line; nothing is invented on restart. |

Why no automatic return: a bounce-back on each failure would produce a ping-pong between two failing
bots. The return is **a gesture**, not a consequence — `groups.handoff` in the other direction.

---

## 7. The fallbacks

### 7.1 Server without the route

Two paths, one verdict.

1. **Announced**: `groups.capabilities.methods` does not list `groups.lead` / `groups.handoff`. The client
   already reads it for `supported` (`ServeGroupsRpc.kt:297-303`); the two fields added to
   `GroupSupport` remain `false`, and the menu entry is **not drawn**.
2. **Discovered in use**: a `-32601` (unknown method). `looksLikeMissingRoute` already recognises it
   (`PageStates.kt:113-123`); the entry disappears on the next return, and the §7.4 sentence is displayed
   once.

The conditional-entry pattern already exists in the room: "Bot Models" is drawn only if the
lambda is provided and the room has members (`GroupRoomScreen.kt:723-737`). One more entry,
with the same guard, requires no new mechanism. It does, however, require its label from the
catalog: `MenuAndSheetLocalizationTest` rejects a `MenuItem(…)` written in plain text without a French
translation (`MenuAndSheetLocalizationTest.kt:56-73`).

| Missing route | Exact phrase displayed (FR) | EN key |
|---|---|---|
| `groups.lead` | "Ce serveur ne sait pas désigner un meneur (route absente)." | `This server can't name a lead (route missing).` |
| `groups.handoff` | "Ce serveur ne sait pas passer la main (route absente)." | `This server can't hand over (route missing).` |

`humanFailure` is not suitable here: on a `-32601` it says "Le serveur a refusé : …"
(The server refused: …) (`TransportError.kt:160`), which is true but does not say that the function does not exist.

### 7.2 Room without a lead

This is today's state, identically: no line, no checked "Meneur" (Lead) entry, and
`groups.send` without `@name` behaves as before. `groups.handoff` remains callable — any
member can hand over — but the human menu's "Passer la main à…" (Hand over to…) entry is **not** drawn:
without a lead, there is no hand to pass, and this entry must not promise a gesture that has no
object.

`groups.lead` with `member_id: null` on a room that has no lead → `409 already_lead`, no
event. A removal that removes nothing is not information.

### 7.3 Lead removed from the room

Onyx **has no route to remove a member** from a room: `GroupRoomApi` carries none
(`ServeGroupsRpc.kt:326-339`), and the roster is fixed at creation. This case therefore arrives through the server,
when a profile disappears — and it is up to the server to handle it (§6, fourth row). On the phone side:

- The roster refreshes through `groups.state`: `look()` replaces the members when they have changed
  (`GroupRoomFeed.kt:264-266`).
- As long as the local roster lags, the room may display a lead that no longer exists. The
  next `groups.lead` or `groups.handoff` that names it returns `409 member_not_in_room`, and the screen
  re-reads the journal instead of inventing a correction.
- The line "Coder a quitté la salle : plus de meneur" comes from the server, never from a deduction by
  the app: that is the only way to distinguish a removal from a simple read failure.

### 7.4 What remains true in all cases

- No fallback breaks an existing room: without the two routes, the room is today's,
  to the pixel (`ARCHITECTURE-TOP5.md:143`).
- A `409` is never an outage: the screen says it in its own words (§5 of the refusals), not with
  "Réessayez" (Try again).
- A missing entry leaves no dead tap: this is rule 2 of the non-regression contract
  (`ARCHITECTURE-TOP5.md:17-19`).

---

## 8. The hook in the screen

No new destination: `Route` (`OnyxApp.kt:234`) and `RouteRetours.kt` do not move.

| Gesture | Where | Route |
|---|---|---|
| "Meneur de la salle" (Room lead) → members submenu | the room's "…" menu, one more conditional entry (`GroupRoomScreen.kt:713-752`) | `groups.lead` |
| "Passer la main à…" (Hand over to…) → members submenu, drawn only if there is a lead | same menu | `groups.handoff`, with `from_member_id` = the lead and `task_id: null` |
| The handoff line | the thread, via `note()` (`GroupThread.kt:49-69`) | read-only |

The per-member submenu already exists for "Bot Models" (`GroupRoomScreen.kt:723-737`): we reuse
its shape, not its code. The two new entries carry `localize = true` (default) and their
labels go to the catalog.

What is **not** in this batch: a lead screen, a permanent badge in the header, a
`/lead` command. The plan does not ask for them and the header is already loaded (three buttons,
`GroupRoomScreen.kt:892-911`).

---

## 9. Hooks in the existing code

| Claim in this document | Actual file |
|---|---|
| Rooms go through an RPC channel, not HTTP | `core/.../data/net/ServeGroupsRpc.kt:31` ; `core/.../data/net/RpcChannel.kt:13` |
| A room call has a 60 s timeout | `core/.../data/net/ServeGroupsRpc.kt:286` |
| The server's known methods are announced in `groups.capabilities` | `core/.../data/net/ServeGroupsRpc.kt:297-303` |
| `GroupSupport` has only two fields today, and a test constructs it positionally | `core/.../data/net/ServeGroupsRpc.kt:342` ; `core/.../test/.../ServeGroupsRpcTest.kt:52` |
| `groups.send` writes a `message.user` and lets the driver choose who answers | `core/.../data/net/ServeGroupsRpc.kt:113-129` |
| `groups.stop` stops a single room, without distinguishing a lead | `core/.../data/net/ServeGroupsRpc.kt:157-166` |
| `groups.approve` carries the `member_id` of the requester | `core/.../data/net/ServeGroupsRpc.kt:211-224` |
| A `disband` during shutdown is retried | `core/.../data/net/ServeGroupsRpc.kt:185-196` |
| `GroupEvent`: `seq`, `kind`, `actor`, `payload`, `created_at` | `core/.../data/model/serve/GroupThread.kt:27-39` |
| `actorID` reads `payload.member_id` **before** `actor.id` | `core/.../data/model/serve/GroupThread.kt:83-84` |
| `endsDiscussion` knows only two kinds | `core/.../data/model/serve/GroupThread.kt:46` |
| `note()` is the only path for a non-message journal line | `core/.../data/model/serve/GroupThread.kt:49-69` |
| `turn.settled` composes its label locally from the roster | `core/.../data/model/serve/GroupThread.kt:52` |
| `turn.cancelled` already excludes one reason so as to show nothing | `core/.../data/model/serve/GroupThread.kt:56-58` |
| `driver_status` is the only current view of the distribution | `core/.../data/model/serve/GroupThread.kt:114-127` |
| `rows` is a filter, the journal order is preserved | `app/.../ui/bots/GroupRoomFeed.kt:98` |
| `discussing` depends on `endsDiscussion` | `app/.../ui/bots/GroupRoomFeed.kt:73-78` |
| The roster refreshes through `groups.state` | `app/.../ui/bots/GroupRoomFeed.kt:264-266` |
| The screen re-reads after a gesture (`stop`, `retry`) | `app/.../ui/bots/GroupRoomFeed.kt:524-531` |
| The tick is 500 ms when the room is working | `app/.../ui/bots/GroupRoomFeed.kt:580-583` |
| A note is displayed centred, without new composition | `app/.../ui/bots/GroupRoomScreen.kt:1009-1016` |
| A per-member submenu is already drawn | `app/.../ui/bots/GroupRoomScreen.kt:723-737` |
| The room hint promises `@name` | `app/.../ui/bots/GroupRoomScreen.kt:956-957` |
| The roster runs from 2 to 6 members | `core/.../data/model/serve/GroupRoom.kt:116-118` |
| `label` is the room name, otherwise the handle | `core/.../data/model/serve/GroupRoom.kt:34-35` |
| The Feed only reads messages | `app/.../ui/bots/GroupRoomJournal.kt:30` |
| Voice only reads messages and drafts | `app/.../ui/bots/GroupRoomFeed.kt:110-161` |
| `-32601` and `404` are recognised as a missing route | `app/.../ui/common/PageStates.kt:113-123` |
| A `-32601` currently says "Le serveur a refusé : …" (The server refused: …) | `core/.../data/net/TransportError.kt:160` |
| A menu label without translation fails a test | `core/.../test/.../MenuAndSheetLocalizationTest.kt:56-73` |

---

## 10. Expected proofs

1. **The journal, not the screen**: 40 events + one `bot.handoff` → the same rows in the same
   order, plus one (`ARCHITECTURE-TOP5.md:144`). Pure test on `rows`.
2. **No leak into the Feed**: `roomThreadMessages` on this same journal returns the same messages
   as before, without the handoff line (`GroupRoomJournal.kt:30`).
3. **The discussion continues**: `discussing` stays true after a `bot.handoff` alone, and becomes false
   after a `room.activity`.
4. **The identity trap**: a `bot.handoff` whose payload carries `member_id` changes
   `actorID` — the test must catch it, and the server must never write it.
5. **No duplicate**: a `turn.cancelled` with reason `handed_over` produces no line.
6. **Absence**: without the two methods in `groups.capabilities`, neither entry is
   drawn, and no "Réessayez" (Try again) phrase appears.
7. **Localization**: the four menu labels and the five sentences of §4.3 are in the catalog;
   `MenuAndSheetLocalizationTest` passes.
8. **Compatibility**: `GroupSupport(supported, writable)` still compiles, and the room without a lead
   behaves exactly as before.

---

## 11. What this batch does not do

- It neither adds nor removes a member: no route allows it today.
- It gives the lead no right of approval, stop or exclusion.
- It does not display the lead permanently: the journal line is enough, and the header is full.
- It touches neither the thread, nor the voice scene, nor the five tabs (`ARCHITECTURE-TOP5.md:26-28`).
- It changes no existing route: `groups.lead` and `groups.handoff` are two more methods.

---

## 12. Open decisions

| # | Question | What I propose | What is needed to decide |
|---|---|---|---|
| O1 | Is the lead **also** the one who opens the turn after a message from the person, or only the first recipient? | First recipient: it can stay silent and let another answer. | A usage observation: is a silent lead a bug or a choice? |
| O2 | `summary` mandatory? | Optional: the journal is already the context. | — |
| O3 | Should a `groups.lead` write an event when the value does not change? | No: `409 already_lead`, no event. | — |
| O4 | Removing the lead on profile disappearance: `system` or `user` event? | `system`: nobody clicked. | The Hermes core convention for the events it writes alone. |
| O5 | The two menu entries, or one with two gestures? | Two distinct entries, each conditional on its route. | A mockup: two entries in a menu of six is a lot. |

## 13. Points in the plan that seemed inconsistent to me

1. **"a menu line, like `onOpenBotModels`" (`ARCHITECTURE-TOP5.md:142`).** That name does not exist
   in the repository. The actual pattern is `onPickBotModel` (`GroupRoomScreen.kt:723-737`), a
   conditional entry in the `buildList` of the room's `OnyxMenu`. The plan cites an invented symbol; the
   spec anchors on the real one.
2. **"in the thread, the handoff event ("Talk hands to Coder")" (`:139`).** The formulation
   in English does not exist anywhere, and "Talk hands" means nothing. The retained sentence is
   "Writer passe la main à Coder.", composed locally from the identifiers (§4.3), as
   `turn.settled` already does.
3. **"Fallback: server without handoff → the menu entry does not exist" (`:143`).** There are **two**
   routes and therefore **two** entries to switch off separately: a server may know `groups.lead`
   without knowing `groups.handoff` (an older core of one version). The plan treats both as
   a single flag; the spec separates them (§7.1), otherwise an entry would remain drawn for a missing
   route.
4. **"mostly server" for a week (`:135`).** The two methods are small; what takes
   time is deciding what the lead **does not** do (§5.2) and holding the two traps of
   §4.1. The plan underestimates the *interface* part of this batch, not the server part.
5. **The plan does not say what happens if the lead dies** (`:135-144`). This is the question that decides
   the handoff semantics: without it, one does not know whether `groups.handoff` moves a pointer or
   transfers a turn. The spec decides: both, depending on whether `task_id` is present (§3, §6).
