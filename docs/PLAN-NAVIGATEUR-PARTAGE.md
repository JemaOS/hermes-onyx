# Live agent browser, and takeover from the phone

**The reference architecture.** This document is normative, not a sketch: it fixes the decisions,
the complete wire contract, the files touched on both sides, the security rules and the
criteria that say "this is done". What is not written here is not in scope.

Status: **M1 → M3 written** on the `lot/browser-screen` branch (plugin, `onyx-one` relay, Onyx
screen, locks). Remaining for v1: `fill` (the vault on the server side), the card in the thread, and the real
test on github.com. See §9 for details of what is done.

---

## 0. Decisions to validate

| # | Question | Recommended default | Why |
|---|---|---|---|
| D1 | **Where does the server live?** | A **platform plugin** `onyx-browser`, with its **own aiohttp server** (not a Dashboard route) | A Dashboard plugin receives the FastAPI app for REST; it is `register(ctx)` + `register_tool` from **platform** plugins that provides the blocking tool — and `dots` proves, on this same version, that a platform plugin can hold its own WebSocket behind a TLS proxy |
| D2 | **Latency budget?** | 3 img/s at rest, **8 img/s** while driving | 3 is enough to read a form; 8 makes tap and typing usable |
| D3 | **Scope?** | **Phone only** in v1 | Dots' web app only has the card, not the screen; we'll add it if the need arises |

These three defaults are the **basis of this document**: everything that follows is written for them. A veto on
one of the rows carries over to the relevant sections (§2 for D1, §3 for D2, §5 for D3).

---

## 1. Objective, and non-objectives

**Objective.** When the agent hits a page that requires a human — login, password, 2FA,
captcha, consent wall — it **asks**, the phone displays its browser live, you
act on it (tap, drag, type), you hand back, and it **continues in the same turn**, on the
same page, already logged in.

**v1 non-objectives** (to be written in the release notes, so that nobody expects them):

- no file picker, no download, no tabs, no pinch-to-zoom;
- no passkeys / WebAuthn (impossible by typing);
- no stream in the web app;
- no automatic driving by the agent: it *asks*, it does not take.

---

## 2. Architecture

```
Onyx (phone)                              Hermes serve + onyx-browser plugin (the profile)
─────────────────                         ───────────────────────────────────────────────
                                          browser_request_takeover(reason)   ← blocking tool
browser.observe        ── JSON-RPC ───▶   single-use ticket (30 s) + viewer_id
                       ◀── ticket ────
WS /api/browser/ws     ── frames ─────▶   JPEG 0.6 / q55, 3 img/s (8 while driving)
  input {seq, action, x, y, dy, text} ──▶ CDP Input.*        (refused if no lease)
  done  {id, note}      ────────────────▶ releases the tool, the agent resumes
browser.lease.acquire  ── JSON-RPC ───▶   holder: human        ← « You're driving »
browser.lease.release  ── JSON-RPC ───▶   holder: agent        ← « Hand back »
                       ◀── display.lease ── fan-out to all clients
```

**Why not Dots' scheme.** Dots hand-rolls what Hermes already has:
`display.observe` mints a single-use ticket (`tui_gateway/methods_display.py:152`), a
WebSocket serves the view (`hermes_cli/web_routers/display.py:75`), and a **lease** says who is
driving (`tui_gateway/contracts/display.py`, `DisplayLease(holder: agent|human, epoch)`). We reuse these
shapes and invent only the **source**: instead of a bot's Xvnc, the agent's Chromium.

The `epoch` is the piece we absolutely keep: it moves on every real transition, so an
input computed under an expired lease is detected instead of clicking somewhere else. Dots has nothing like it —
for Dots, `takeover` is only a stream accelerator, and the server applies inputs without
checking who is driving.

### 2.1 Rendering: a phone viewport, not a shrunken desktop

Dots renders the page at ~1280 px then **shrinks** it to the phone width: the form text
becomes tiny, checkboxes are three pixels, and tapping requires aiming. We don't do that.

At the start of the takeover — **before** any typing — the server sets a phone viewport on
the agent's page:

```
Emulation.setDeviceMetricsOverride {
  width: 390, height: 844, deviceScaleFactor: 2, mobile: true
}
Emulation.setUserAgentOverride { userAgent: <UA mobile> }
```

Three consequences, all intended:

- the site serves **its mobile version**: readable text, wide targets, one tap = one thumb;
- the frame sent is **1:1** with what you see: no more offset between the image and the finger;
- the frame is smaller for the same legibility → fewer bytes per second.

Precautions, in order:

1. the change **reflows the page**: it is applied at the moment the takeover begins, never
   during an input;
2. some sites force their desktop version (`?desktop=true` URL, cookie): in that case the viewport
   is not enough and we fall back to the shrunken rendering, without breaking the stream;
3. at the end of the takeover, the original viewport is **restored** (`Emulation.clearDeviceMetricsOverride`)
   so the agent finds the page as it knew it.

---

## 3. The wire contract

### HTTP routes (on the plugin, relayed by `onyx-one`)

**Corrected at implementation**: the plugin is not the Dashboard, so its routes do not go through
`ServeRpc` (the Dashboard's JSON-RPC). They are REST, on its own origin, and `onyx-one` relays them
under `/onyx-browser/` with the app's key.

| Route | Body | Result |
|---|---|---|
| `GET  /onyx-browser/health` | — | `{ok, browser, lease, watching, frames, pending}` |
| `POST /onyx-browser/observe` | — | `{ok, ticket, path: "/onyx-browser/ws", viewer_id, expires_in: 30}` |
| `POST /onyx-browser/lease` | `{action: "acquire"\|"release", note?}` | `{ok, holder, epoch}` — `409 lease_held` if someone else is driving |

Errors: `401 token` (loopback or token), `409 lease_held` (someone else is already driving), and on the
socket `refused {reason}` ∈ `stale`, `no_lease`, `no_page`, `action`.

### WebSocket frames

**Phone → server**

| `type` | Fields | Effect |
|---|---|---|
| `watch` | `on`, `takeover` | opens/closes the stream; `takeover` raises the rate to 8 img/s |
| `input` | `seq`, `action` ∈ `click\|scroll\|type\|key\|fill`, `x`,`y` (fractions 0..1), `dy`, `text` (≤ 2000), `key`, `secret` | replayed on the page |
| `done` | `id`, `note` | hands back, releases the tool |

**Server → phone**

| `type` | Fields |
|---|---|
| `frame` | `seq`, `data` (base64 JPEG), `url`, `title`, `width`, `height`, `css` (CSS viewport `[w,h]`), `live`, `ts` |
| `idle` | — (no browser open, the stream keeps polling at 1.5 s) |
| `lease` | `holder`, `viewer_hash`, `epoch` |
| `refused` | `seq`, `reason` ∈ `stale\|no_lease\|no_page\|origin` |

The `seq` is **monotonic per connection**: it is used to refuse an input computed on a stale
image (§7).

> **`css` is mandatory in the frame.** It is the viewport size in CSS pixels, and it is what
> tells the app where to put the finger: the gesture starts in fractions of the image (0..1), and the
> server multiplies it by that size. Without it, the app computes its gestures on a viewport of
> zero pixels — and the click lands in the corner of the screen. The server therefore broadcasts it **with** the
> frame; it also keeps a copy for itself, and falls back to it if the app sends a
> zero size (older app, or frame received before the fix).

---

## 4. Server — the `onyx-browser` plugin

**Platform plugin**, not a Dashboard plugin. The difference is not cosmetic: it is
`register(ctx)` that provides `register_tool` (the blocking tool) and the `pre/post_tool_call`
hooks from which the browser's `task_id` comes. A Dashboard plugin, by contrast, only receives the FastAPI app, for
REST.

This is the `dots` setup (`server/hermes-plugin-dots/adapter.py`) — `register(ctx)` there declares
`browser_request_takeover` (`adapter.py:1874`) and the plugin holds **its own** `aiohttp` there
(`web.AppRunner` + `TCPSite`, behind a TLS proxy). We therefore do not touch `hermes serve`.

```
plugins/onyx-browser/
  plugin.yaml   # kind: platform ; requires: DOTS-like token (BROWSER_TOKEN) ; bind 127.0.0.1:8788
  __init__.py   # register(ctx): the tool + the task_id hooks
  adapter.py    # capture, input, lease, credential capture
  server.py     # aiohttp: /onyx-browser/ws, /onyx-browser/health (single-use ticket)
```

**Operational consequence**: the plugin listens on its own port (8788), like `dots` on 8787.
A proxy rule to that port is needed in the tunnel that already exposes the gateway — one line of
configuration, not code.

### What we reuse as is

- `SUPERVISOR_REGISTRY.capture(task_id, timeout=…)` → `tools/browser_supervisor.py:518` (verified
  present on this machine);
- `Target.getTargets` + `Target.attachToTarget(flatten)` to target the **real** page (the
  supervisor is pinned to the first tab, often empty) — Dots' heuristic
  (`adapter.py:1367`) is correct, we reuse it;
- `Page.getLayoutMetrics` → `cssVisualViewport` (the size we will map);
- `Page.captureScreenshot(jpeg, quality=55, optimizeForSpeed, clip=viewport, scale=0.6)`;
- `Page.getNavigationHistory` → `url` + `title` for the address bar;
- `Input.dispatchMouseEvent` / `insertText` / `dispatchKeyEvent` — **exactly** Dots'
  mapping (`adapter.py:1400-1425`), which is correct.

### What we write

1. **The tool** `browser_request_takeover(reason)`: blocking tool on the agent's thread
   (`threading.Event`), cap **15 min**, returns `{status: done|timeout, note}`. Registered in an
   `onyx` toolset (offered in Onyx conversations only).
   This is the non-negotiable point: **Hermes closes its browser at the end of the turn**; without a blocking
   tool, the takeover has no point.
2. **The WS route** `/api/browser/ws`: single-use ticket (30 s), same shape as
   `display.observe`; `watch`/`input`/`done` as input, `frame`/`idle`/`lease`/`refused` as output.
3. **The lease**: `holder`, `viewer_hash`, `epoch`, and the `browser.lease` event broadcast to all
   clients (the `tui_gateway/contracts/display.py` model).
4. **The input guard** (§7): the only rule Dots does not have.
5. **The mobile viewport** (§2.1): set at the start of the takeover, restored at the end.
6. **Login form detection** (§6.2): at the moment of `done`, the server records
   the page's host and the presence of a `type=password` field in the last submitted form, and
   returns these two facts in the tool's response — the save proposal relies on them.

---

## 5. Onyx — the files

### New

| File | Role |
|---|---|
| `core/…/data/net/BrowserScreenApi.kt` | `browser.observe` / `lease.*` / `status`, the models (`BrowserFrame`, `BrowserLease`, `ScreenInput`) |
| `core/…/data/net/BrowserScreenClient.kt` | the WS (OkHttp): watch, input, done; reconnection; nothing else |
| `app/…/ui/browser/BrowserScreenViewer.kt` | the screen: image, address bar, takeover controls, « Rendre la main » ("Hand back") button |
| `app/…/ui/browser/BrowserScreenViewModel.kt` | the state: last frame, `seq`, lease, errors — **outside** the thread's composition scope |
| `app/src/test/…/browser/BrowserScreenTest.kt` | the locks (see §8) |

### Reused, without copying them

- `ServeRpc` (`core/…/ServeRpc.kt:44`) for the methods — the same channel as the context sheet;
- `ComputerStream.viewWidthFor(paneWidthPx, screenWidth)`: the requested image width is **negotiated**,
  we do not hardcode 60 % (Dots hardcodes it);
- `ServerProbe` / `ServerCapability`: `BROWSER("api/plugins/onyx-browser/health")`, modeled on
  `VAULT` — the row, the tile and the shortcut exist only if the probe responds;
- `BoundedImages` for bounded decoding, if frames arrive at full size;
- the thread's tool cards for the `takeover` card (pending / expired / handed back).

### What we do **not** reuse

`ComputerTouch`: it is a **relative pointer**, excellent for a 3840×2160 desktop, bad for a
web page. Here the tap is **absolute** — you click where you see the field.

### Performance rules (the same as in 1.3.35)

- decoding **off** the main thread, one `Bitmap` reused per frame, never one per image;
- the frame state lives in **its own scope**: an image at 3 img/s must not recompose the
  thread — this is the defect it took three versions to fix (`GlassScrollFreeze`,
  `WorkingPreviewLine`);
- `watch { on: false }` on `onDispose` **and** on `ON_STOP`: nothing scrolls in the background;
- input is **throttled** (~30/s max) and cancelled if `seq` has changed.

---

## 6. The vault — filling, and saving at login

The vault is not an accessory: it is what moves the feature from "I type my password
on my phone" to "the agent logs in without ever reading it". Two gestures, two directions.

### 6.1 Filling (`fill`)

If `ServerCapability.VAULT` is proven, the takeover offers the profile's secrets
(`vault.list`: name + `••••de` fingerprint):

```
input { action: "fill", secret: "reddit", seq: 412 }     ← no secret travels
server: vault.reveal("reddit") in memory → Input.insertText(...)
```

The password touches neither the phone, nor the IME, nor the clipboard, nor the WebSocket, nor the
model. This is **strictly better** than the Dots version, where the input field is a
`BasicTextField` **in clear text** (`TakeoverControls`): no masking, no password keyboard.

Conditions, otherwise we don't do it:

1. the secret carries its **origin** (`used_by`) and the injection is **refused** if
   `Page.getNavigationHistory` does not return that origin — refusal **on the server side**;
2. injection requires `holder == human`;
3. the vault **never** returns a value to the app: the read route does not exist, and we do not
   add one.

### 6.2 Saving at login (`capture`)

When the submitted page was a login form, the takeover offers to **save**
the credentials in the vault — **never on its own**.

**No automatic write.** The server only sees `type {text}` on a focused field: it
does not know *what it is writing*. An automatic write would amount to letting **the page** decide
what enters the vault, under the name it chooses — a vault that accepts writes
dictated by the page is no longer a vault.

The path is therefore explicit, and it requires **no new server route**:

```
app → POST vault.put { profile, name: "reddit", value, used_by: ["reddit.com"] }
     ← { fingerprint: "••••de", created: true }        (the value never comes back)
```

`VaultApi.put(name, value, profile, usedBy)` already exists on the Onyx side, with `used_by`, with no value in
the return and no log. The server has nothing to learn.

**Where the value comes from**:

| Case | Behavior |
|---|---|
| credentials typed on the phone | the app keeps them in the ViewModel until `done`, then erases them after the write |
| credentials injected by `fill` | nothing to save: they come from the vault |

**The five rules** (one test lock per rule, §8):

1. **never without a gesture** — no write triggered by the page, by the agent or by a timer;
2. **bound to the origin** — `used_by` carries the host of the submitted page, and `fill` will refuse this secret outside
   that origin;
3. **the value is erased** from the ViewModel as soon as it is written, and the field is masked while typing;
4. **only on a real login form** — a `type=password` field in the submitted form;
   otherwise we do not guess, we propose nothing;
5. **no log, no file** — `VaultApi.put` does not log the value, a contract already written.

### 6.3 Two entries per account

The vault holds **one value per name**, and a login asks for two. A convention, not a new
format:

```
reddit        → the password    used_by: ["reddit.com"]
reddit-email  → the address     used_by: ["reddit.com"]
```

`fill` fills them **in order** (address, then password), and the fingerprint displayed next to the
field says which one was just sent. The name is pre-filled from the host (`reddit.com` → `reddit`),
and remains editable.

### 6.4 What it looks like, in practice

Reddit: the agent asks, you fill from the vault or you type once, you save with one gesture,
you hand back. The next time, the same page fills **without you typing anything**, and the agent
posts. The password never left the server, and it never went through the model.

---

## 7. Security — the six rules

1. **The token is the boundary.** The WS requires the single-use ticket from `browser.observe` (30 s),
   never the pairing token in clear text in the URL.
2. **No input without a lease.** `input` refused if `holder != human` → `refused {reason: "no_lease"}`.
   (Dots does not do this: for Dots the flag only changes the rate.)
3. **No stale input.** A `seq` that is zero, unknown, or later than the last emitted frame →
   `refused {reason: "stale"}`; likewise, a gesture computed on an image more than two
   seconds old → `refused {reason: "stale"}`. This is the guard that prevents clicking on a field that
   has moved.
   > *Why two seconds and not "the last frame".* The server broadcasts at 8 img/s while
   > the human is driving: the `seq` of the **displayed** frame is necessarily already past by the
   > time the finger lands. Comparing `seq` values therefore refused *every* gesture, and the screen
   > became useless. The rule compares a **duration**, not a counter: two seconds
   > cover human reaction time (look, aim, place the finger) and no more. The `seq`
   > is still checked — it refuses a number the server never emitted.
4. **A single driver.** `browser.lease.acquire` when someone else is driving → `5302 LEASE_HELD`; the
   other devices receive `lease {holder: "human", viewer_hash}` and remain read-only.
5. **Nothing in the model.** `input` only touches CDP: no path from `screen_input` to
   history or to the prompt. To be verified by a test, not by an intention.
6. **The origin is the only key to secrets.** A `fill` or a `capture` whose host does not match
   `used_by` is refused on the server side (`refused {reason: "origin"}`) — never on the app side, never
   "with a warning".

---

## 8. Tests and locks (the repository's style)

| Lock | What it holds |
|---|---|
| `BrowserScreenTest` (pure) | the fractions → CSS viewport mapping, the `stale` refusal, the `no_lease` refusal, the rate (3 / 8 img/s) |
| `BrowserCapabilityTest` | the tile, the row and the shortcut appear **only** if `ServerCapability.BROWSER` responds; no dead entry |
| `BrowserNoModelLeakTest` | no field of `ScreenInput` reaches `ChatViewModel` / history (guard by reading the source, like `VaultScreenTest`) |
| `BrowserThreadPerfTest` | `BrowserScreenViewer` does not read the thread state; a frame recomposes only the screen |
| `VaultCaptureTest` | **no write** without a gesture; `used_by` carries the host; the value is erased from the ViewModel after the write; nothing is proposed without `type=password` |
| `VaultFillOriginTest` | a `fill` whose origin does not match `used_by` is **refused on the server side** (`refused {reason: "origin"}`) |
| plugin tests (server side) | `browser.observe` returns a single-use ticket; `input` without a lease is refused; the tool returns `timeout` at 15 min |

No test measures real fps: no device on the build machine. The measurement protocol in
`docs/PERF-MESURE-2026-11-05.md` applies if you want numbers.

---

## 9. The lots

| Lot | Content | Done when |
|---|---|---|
| **M1 — the pipe** | plugin: blocking tool + `browser.observe` + WS broadcasting JPEG frames + mobile viewport (§2.1), without input | an end-to-end test shows 3 img/s, a 390×844 frame and a `done` that releases the tool |
| **M2 — the hand** | `input` (click, scroll, type, key) + lease + `no_lease` / `stale` refusals | we log in to a test site from the screen, and an input without a lease is refused |
| **M3 — Onyx** | the Compose screen, the ViewModel, the `BROWSER` probe, the `takeover` card in the thread | everything in M2 can be done from the phone, without recomposing the thread |
| **M4 — the vault** | `fill` + `capture` (save at login) + origin binding + fingerprint | the password never leaves the server; a `fill` on another origin is refused; no write without a gesture |

**Locked in at writing (2026-11-05)**: `capture` is done on the app side (`VaultApi.put`, origin bound,
explicit gesture, masked field); `fill` **remains to be done** — it requires the plugin to read the
vault, hence a privileged server route that does not exist yet.

Each batch ends with a commit + release notes + the three APKs, as usual.

---

## 10. Risks, stated frankly

- **The CDP seam is an internal API.** `SUPERVISOR_REGISTRY.capture` may change between two
  Hermes versions. Dots' README says so itself. Consequence: the plugin may break on an
  update, and it must say so clearly when it can no longer find the seam (message, not silence).
- **3 img/s is not screen sharing.** An image-grid captcha is painful. If it
  becomes a blocker, the remedy is `Page.startScreencast` (a real stream), not a higher rate.
- **Server CPU cost**: one full-frame JPEG every 300 ms (120 ms while driving).
- **Ghost profiles.** If two tabs are open, "the last real page" may be the wrong one.
- **Browser profile session and lifetime**: to be measured before promising "you stay
  logged in" (see the 5/11 conversation).

---

## 11. What I do not do in v1

- audio/video streaming of the page (JPEG is enough for a form);
- simultaneous driving by two;
- web app;
- automatic filling without a user gesture;
- remembering logins in the vault (the vault keeps the **password**, not the session: a
  cookie is a bearer token with no origin binding).
