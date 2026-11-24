# Architecture — the five functions of the TOP5 workstream

This document describes **how** the five selected functions (see the conversation's `TOP5.md`) fit
into Onyx **without touching** what already exists. It contains no matters of taste: every
hook point cites a real file in the repository, read on `main` (`e8c195a`).

---

## 0. The non-regression contract

Five rules. A batch that violates one is rejected, even if it works.

1. **Additive only.** No existing signature changes without a default parameter. The pattern is
   already in the repository: `BotEditorScreen` takes `onOpenTools: (() -> Unit)? = null` and
   `onOpenSkills: (() -> Unit)? = null` (`BotEditorScreen.kt:326-327`) — capabilities added
   without breaking a single caller.
2. **No new screen without a gate.** A new screen is reachable only through an entry that
   **disappears** when the server cannot serve it — the same null-lambda pattern:
   passing `null` draws nothing rather than leaving a dead tap.
3. **No new network client.** Everything goes through `ServeClient.request(path, method, query, body,
   timeoutSeconds)` (`core/.../data/net/ServeClient.kt:128`). A new route family =
   an `*Api.kt` file mirroring `ServeSkillsApi.kt`, never a second transport.
4. **Nothing that breaks offline or against an old server.** A missing route returns
   `LoadState.Failed` (`chat/components/ChatActionSheet.kt:808`) or a "disabled" object —
   precedent: `RestorePointList(enabled = false)` (`ServeChatActions.kt:516-526`).
5. **Off-limits** in this workstream: the thread's five tabs, the voice scene, the rooms,
   `Computer`, the SSE chat stream, the existing navigation. We **add** routes to
   `Route`, we rename none (`OnyxApp.kt:234`).

## 1. The four foundational building blocks

Everything else depends on them; they ship **before** the functions, in this order.

### B1 — `ServerProbe`: what this server can do

**Real problem**: no capability route exists (`grep "capab\|supports"` on
`ServeClient.kt` / `ServeRpc.kt`: zero). Every screen discovers the absence **by failing**.

**Building block**: `core/.../data/net/ServerProbe.kt`
- one probe per `(server, configSignature)` pair, result cached in memory + on disk;
- each capability is an `enum class Support { YES, NO, UNKNOWN }`;
- a probe = a lightweight `GET` on an existing, idempotent route, with a short timeout;
- never on the critical path: `UNKNOWN` behaves like `NO`.

**Consequence**: the five functions become **drawing flags**, not exceptions.

### B2 — `VaultClient`: write a secret without ever reading it back

**Building block**: `core/.../data/net/VaultClient.kt`, mirroring `ServeSkillsApi.kt`.
- `put(profile, name, value)`: the value leaves, the app **does not keep it**;
- `list(profile)`: returns `name`, `fingerprint` (last 4), `usedBy`;
- `revoke(profile, name)`.

**Safeguard**: no log, no `toString()`, no Compose state save carries the value
— the pattern is `SecretStore` (`data/local/SecretStore.kt`) for local storage, but here **nothing
is stored on the phone**.

### B3 — `RecordingSession`: the demonstration

**Building block**: `core/.../data/net/RecordingSession.kt`, **on top of** `ComputerBridgeClient.kt`
(existing, 752 lines: it already knows the screen and touch).
- starts/stops a capture **of the user's actions** (tap, input, navigation),
  with timestamp and target;
- memory only, hard cap (10 minutes, as in the reference) then compressed `buf`;
- produces a **neutral log** (no pixels by default), converted into a skill draft.

**Decision (settled, see §7)**: the log of **actions** only — tap, input, scroll,
navigation, wait —, never pixels, never audio, never a field marked secret.

### B4 — `SlashRegistry`: extensible `/`

**Current state**: `SlashCommands.kt:48-53` — six fixed commands (`/model`, `/new`, `/find`, `/settings`,
`/voice`, `/help`) and a `when` on the input.

**Building block**: a **supplied list**, not a hard-coded list: `defaultCommands + skillCommands(profile)`
where `skillCommands` comes from `ServeSkillsApi.skills(profile)`. The picker and the execution read the
same list, so a `/nom-de-skill` slots in like a `/model` — and disappears if the profile does not have
that skill.

---

## 2. The five functions, one per batch

Each batch: a `lot/top5-<nom>` branch from `main`, a `RAPPORT-TOP5-<NOM>.md` report, screenshots
under `qa/<date>-top5-<nom>/`. No preference migration, no database change.

### Batch 1 — Skills via `/` (1 day)

| | |
|---|---|
| Surface | the composer's `/` picker, existing |
| Data | `ServeSkillsApi.skills(profile)` — route **already in use** |
| Server routes | none |
| Hook | `SlashCommands.kt` (list → provider), `ChatComposer` (no signature changed, the list is passed in) |
| Fallback | profile without skills → the list is today's, identically |
| Proof | pure test: `SkillSlashCommandsTest` — the six commands remain, the order does not move, a skill produces the same `SlashAction` as a command |

**Why first**: zero server dependency, zero new surface, immediate effect.

### Batch 2 — Vault (4 days, 2 of them server-side)

| | |
|---|---|
| Surface | **new screen** `VAULT` + entry in « Capacités » (Capabilities), under « Secrets » |
| Data | `VaultClient` (B2) |
| Server routes | `vault.list`, `vault.put`, `vault.revoke` — **to be created on the Hermes side** |
| Hook | `OnyxApp.kt:234` (+1 constant), a route `when`, `RouteRetours.kt` (back label), `CapabilitiesScreen` (entry) |
| Fallback | `ServerProbe` says `NO` → the entry is not drawn; the screen does not exist in history |
| Proof | drawing test: without the capability, no "Secrets" row; with it, one row per secret, never a value |

### Batch 3 — `Teach a task` (1.5 weeks, half of it server-side)

| | |
|---|---|
| Surface | a « Show me once » button in `ComputerScreen`, then a draft in `SkillEditorScreen`, then « en faire une routine » (turn it into a routine) toward `CronScreen` |
| Data | `RecordingSession` (B3) → skill text |
| Server routes | `skills.record.start/stop` (or 100 % local if you prefer), `skills.draft` |
| Hook | `ComputerScreen.kt` (button, no existing behavior modified), `SkillEditorScreen` (pre-fill), `CronScreen` (existing link) |
| Fallback | no Computer bridge → button absent; bridge without a capture route → button greyed out with the server's sentence |
| Proof | pure test of the log→skill **converter** (input: 40 events, output: a stable markdown) + test that the button is absent without the capability |
| Risk | the only batch that touches a sensitive surface (the screen). It sits behind `ServerProbe` + a `top5.recording = off` setting by default |

### Batch 4 — Shareable bot templates (3 days)

| | |
|---|---|
| Surface | in a bot's details: « Share as template » → link + QR; on arrival: preview then « Add » |
| Data | **`BotTemplateSpec` already exists** (`BotEditorScreen.kt:85`, templates `blank`/`Researcher`/`Writer`/`Coder`/`Home`/`Planner`) — we do not create a data model, we **serialize** it |
| Server routes | none: sharing is a local encoding (JSON → link, `onyx://bot/…`) |
| Hook | `BotEditorScreen` (menu action), `SetupCodeScanner`/`QrCodeDecoder` (already there for pairing), `OnyxApp` (arrival route) |
| Fallback | unreadable link → sentence, nothing is created; readable link → **mandatory preview** before writing |
| Forbidden | no secret, no conversation, no server identity in the link — the preview says so line by line |
| Proof | round-trip test: `spec → link → spec` identical; refusal test: a link containing `apiKey` is rejected, not silently cleaned |

### Batch 5 — Lead bot and handoff (1 week, mostly server-side)

| | |
|---|---|
| Surface | in a room: designate a « main bot »; in the thread, the handoff event (« Talk hands to Coder ») |
| Data | the existing room model (`GroupRoomFeed`, `GroupRoomJournal`) |
| Server routes | `groups.handoff`, `groups.lead` — **to be created on the Hermes side** |
| Hook | `GroupRoomScreen` — one menu row added next to the existing `onPickBotModel` entry (`GroupRoomScreen.kt:168`, drawn at `:723`), `GroupRoomFeed` (one more event type, rendered by the same list) |
| Fallback | server without handoff → the menu entry does not exist; current rooms behave exactly as today |
| Proof | pure test: a log with a handoff produces the same rows + one, without reordering the existing ones |

---

## 3. What remains on the Hermes side (to specify, not to code here)

**Decision (see §7)**: the vault routes are **not** a Hermes patch. They live in
a **plugin**, `~/.hermes/plugins/onyx-vault/` — this is a product mechanism, not a workaround:
plugins mount their routes with `APIRouter` under `/api/plugins/<nom>/`
(`plugins/kanban/dashboard/plugin_api.py:1-43` is the living example in the tree).

| Route | For | Where | Batch |
|---|---|---|---|
| `vault.list` / `vault.put` / `vault.revoke` | per-profile vault | **`onyx-vault` plugin** (`plugin.yaml` + `dashboard/plugin_api.py`) | 2 |
| `skills.record.start` / `stop` / `skills.draft` | demonstration → skill | `onyx-vault` plugin or a second plugin; nothing in the core | 3 |
| `groups.lead` / `groups.handoff` | lead bot | Hermes core (this is a room notion) | 5 |

**Two capabilities, not one**: `lead` and `handoff` each carry their own probe — a server can know
one without the other, and a single shared flag would draw a menu entry that fails. See §8.
None is a blocker for batches 1 and 4: **those two ship without touching the server.**

## 3 bis. The `onyx-vault` plugin, plainly

```
~/.hermes/plugins/onyx-vault/
├── plugin.yaml            # name, description, version
├── __init__.py            # register(ctx) — optionally a SecretSource
└── dashboard/plugin_api.py # APIRouter → vault.list / vault.put / vault.revoke
```

- **What it is not**: a Hermes fork, nor a patch of `~/.hermes/hermes-agent` (a
  `hermes update` would overwrite it). Remove the folder = the feature disappears cleanly.
- **What it is**: an ordinary plugin, mounted on `/api/plugins/onyx-vault/`, with the
  Dashboard's auth, so Onyx consumes it like any other route — through `ServeClient`.
- **Optional extra, not a requirement**: register as a `SecretSource`
  (`agent.secret_sources.base.SecretSource`, doc `secret-source-plugin.md`) so that the vault's keys
  also reach the bot's environment at startup. The framework owns the ordering and
  precedence; we only write `fetch()`.
- **Limitation to state in the screen**: a plugin runs with the agent's privileges. The vault is
  therefore a sensitive component, not a sandbox.

## 3 ter. Proof of mounting — verified, not assumed

Test run in a **disposable** `HERMES_HOME` (the real config and the running server are not
touched): a minimal `onyx-probe` plugin placed in `<home>/plugins/onyx-probe/dashboard/`, a
`plugins.enabled` in `config.yaml`, then the same path as the repository's tests —
discovery → `_get_dashboard_plugins(force_rescan=True)` → `_mount_plugin_api_routes()`.

**Case 1 — plugin enabled** (`plugins.enabled: [onyx-probe]`):

```
plugins discovered: ['onyx-probe', 'hermes-achievements', 'kanban']
routes mounted for onyx-probe: ['/api/plugins/onyx-probe/list', '/api/plugins/onyx-probe/put']
GET /api/plugins/onyx-probe/list -> 401 {"detail":"Unauthorized"}
```

→ the routes **mount** under `/api/plugins/<nom>/`, and the `401` proves that **the Dashboard
authentication is in front** (so Onyx passes the same key as for everything else).

**Case 2 — plugin present but not enabled** (`plugins.enabled: []`):

```
plugins discovered: ['onyx-probe', 'hermes-achievements', 'kanban']
routes mounted for onyx-probe: []
```

→ the plugin is **seen**, but its Python code is **never imported**: the
`plugins.enabled` gate is a security guard (GHSA-mcfc-hp25-cjv7), not a formality. **The plugin
must therefore be enabled**, otherwise the vault does not exist — a line of documentation not to
forget in batch 2.

**Verified bonus**: hot mounting exists (`_PluginApiHotMount`,
`hermes_cli/web_server_dashboard.py:1084-1105`) — the **first request** on
`/api/plugins/<nom>/…` mounts the routes in-process, **without restarting** the server. Vault
development therefore does not require stopping your Hermes.

**Conclusion**: ADR-2 holds. The vault is an enabled plugin, not a server patch.

## 4. Order, and why

```
Batch 1  (1 d,  0 dependency)     → proves the B4 foundation, ships immediately
Batch 2  (4 d,  3 routes)         → unlocks the rest: without a vault, a bot does nothing real
Batch 4  (3 d,  0 dependency)     → growth; reuses an existing model
Batch 3  (1.5 wks, 3 routes + B3) → the demonstration, the real differentiator
Batch 5  (1 wk, 2 routes)         → the team; worth nothing before bots can act
```

`B1 (ServerProbe)` is written **in batch 1** (one day), because batch 1 does not need it
but batch 2 does — and it is better to write it when it blocks nothing.

## 5. Checklist — what we verify at each batch

1. `:core:testDebugUnitTest` and `:app:testDebugUnitTest` with `--rerun --no-build-cache`, counts
   read **in the XML** (never in the Gradle output), zero failures.
2. The test count **goes up**; no existing test is modified to pass.
3. Against a server **without** the routes: the entry is absent, no empty screen, no dead tap.
4. Against a server **with** the routes: the feature works, and going back returns to the screen
   you came from (entry added to `RouteRetours.kt`).
5. Every visible string is in the catalog: an `OnyxFrenchTop5.kt` file added to the
   concatenation in `OnyxLocalization.kt:221` — otherwise `MenuAndSheetLocalizationTest` must fail.
6. `git status --short` shows only the batch's files; build artifacts are not
   tracked.

## 6. The three risks, and their countermeasures

| Risk | Countermeasure |
|---|---|
| The demonstration captures sensitive data | **action** log by default (no images), 10 min cap, `off` setting, and the preview before conversion shows the sequence line by line |
| The vault becomes a leak | value never read back, never logged, never saved in Compose state; the screen shows only a 4-character fingerprint; a test that rejects a `toString()` of the object |
| The 235 tests that read the source block the refactor | everything logical (converter, codec, command provider) is a **pure object** tested by behavior; source tests remain for the visual guard, we do not add any |

---

## 7. Settled decisions

### ADR-1 — The demonstration records **actions**, not images

**Decision**: the capture produces a **typed log** — `tap(position, label if accessible)`,
`type(text, masked if secret field)`, `scroll`, `navigate(url)`, `wait(ms)`, `key(back/home)` — and
nothing else. No pixels, no audio, no clipboard.

**What it gives**: a 10-minute demonstration weighs a few kilobytes, the conversion to
a skill is **deterministic** (hence testable without a screen), and no visual content leaves the
phone as long as an action does not name it.

**What we accept losing**: purely visual gestures (dragging on a canvas, drawing,
picking a color). The draft **says so** on the step concerned (« cette étape aura peut-être
besoin d'une capture » — "this step may need a capture") instead of pretending.

**Honest fallback**: a target without an accessibility label becomes `tap 42 % / 18 % of the window`,
marked « à relire » ("to review"). Never a silent step.

**Effect on the plan**: B3 drops to ~150 lines + a pure test of the converter; batch 3 goes from
2 weeks to **1.5 weeks**, and the "sensitive data leak" risk drops to the level of a
setting (`top5.recording`, `off` by default).

### ADR-2 — The vault is a **Hermes plugin**, not a server patch

**Decision**: `vault.*` is served by `~/.hermes/plugins/onyx-vault/` (an `APIRouter` mounted under
`/api/plugins/onyx-vault/`, exactly like `plugins/kanban/dashboard/plugin_api.py`), with the
optional registration of a `SecretSource` to expose the keys at agent startup.

**Why not otherwise**: patching `~/.hermes/hermes-agent` would be overwritten on the first
`hermes update`; forking Hermes costs a lifetime of maintenance; putting the secrets on the phone
contradicts the goal (the bot runs on the server, not on the phone).

**What it gives**: the feature installs and uninstalls with a folder, it survives
updates, it uses the Dashboard authentication (so Onyx consumes it through `ServeClient`, with no
new transport), and the Hermes repository stays intact.

**What we accept**: a plugin runs with the agent's privileges — it is not a
sandbox. The vault screen says so, and the test rejects any value going into a log or a
`toString()`.

**Effect on the plan**: batch 2 stays at 4 days, including **2 d of plugin** and 2 d of screen; and batch
3 no longer needs the core for `skills.record.*`.

---

## 8. Amendments after the first wave (verified, not assumed)

These five points come out of work actually executed: they correct the plan, they do not dress it up.

### 8.1 The demonstration log does not go into the vault

Batch 3 will live in a **second plugin**, `onyx-record`, not in `onyx-vault`. Reason: the vault's
central guard is "a value is **never** read back"; a demonstration log, by contrast, is
read back by construction (it is displayed before being turned into a skill). Putting them in the same
component would force writing an exception into the vault's most useful rule.

### 8.2 `lead` and `handoff` are two distinct capabilities

A server may know how to hand off without knowing how to designate a lead, or the reverse. Two probes,
two menu entries, two fallbacks — never a shared flag: it would draw an entry that fails.

### 8.3 Two traps in the existing code, written as mandatory tests for batch 5

- `GroupEvent.from` reads `payload.member_id` **before** the actor's identity (`GroupThread.kt:82-84`,
  verified). A `bot.handoff` event carrying that key would steal the line's author: the
  test must pin that the actor remains the right one.
- `turn.cancelled` with reason `handed_over` (the type exists, `GroupThread.kt:56`) must return `null`
  in the event note, otherwise the log shows « X was stopped » **and** the handoff for
  a single gesture.

### 8.4 One reference in the plan was wrong

`onOpenBotModels` **does not exist** on `main`: it belongs to uncommitted work on another
branch. The real conditional-entry pattern for a room is `onPickBotModel` (`GroupRoomScreen.kt:168`,
drawn at `:723`). Corrected in §2, batch 5. This is the second time this plan cites an unverified
symbol; the rule "cite or do not assert" also applies to this document.

### 8.5 What batch 1 learned about skill invocation

Hermes does not invoke a skill by sending a `/nom` line: `slash.exec` **deliberately refuses** a
skill command (`SkillSlashRefused`, `tui_gateway/slash_worker.py:89-112`). The real path is
`command.dispatch` (`tui_gateway/contracts/tools_commands.py:298`), which returns a `message` to submit
as a turn. Onyx already took this path for user commands (`ChatViewModel.kt:1154`,
`:1195-1204`): batch 1 reuses it, it invents no route.

### 8.6 Upstream shipped the lead differently (verified on `origin/main`, `9302ba9e`)

Batch 5 had been specified against `5b147ab0`. In the meantime, `main` received **its own** lead, and not
in the form described here:

- `groups.context` carries `routing: "everyone" | "lead"` and `lead_member_id`
  (`tui_gateway/methods_groups.py:531`, `gateway/hosted_room_context.py:set_room_context`);
- in `lead` mode, a message that names no one goes **to the lead, who distributes** the task
  (`gateway/hosted_room_lead.py`, event `room.assignment`) — it is not "the lead is queried
  first", it is a routing turn;
- `groups.state` returns `context` (`purpose`, `notes`, `routing`, `lead_member_id`), and
  `set_room_context` **requires** `purpose` and `notes`: a write that only concerns the lead must
  read them back first, otherwise it erases the room's purpose and its notes.

Consequence for the client: **three server ages**, tried from newest to oldest —
`groups.context` (routing), then `groups.lead` (this batch's route, added locally), then the
setting on the phone. `ServeGroupsRpc.leadRouting` does the first, `nameLead` the second, and
`RoomLeadStore` remains the fallback in both cases.

The upstream PR was closed: it duplicated `groups.context` with a poorer design,
and six of seven files were already in conflict. Reopenable, but pointless as long as lead
routing exists.

Still not done client-side: **read back** `context.lead_member_id` when opening a room, so that
the menu shows the lead chosen from another device. The write side, however, is in place.
