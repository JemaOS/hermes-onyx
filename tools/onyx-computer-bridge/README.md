# “Ordinateur” (Computer) bridge — the PC screen live, and its inputs

This folder contains the **PC companion** for Onyx's “Ordinateur” screen: the service that captures
the machine's screen, sends it to the app at a few frames per second, and takes back the
clicks, drags and keystrokes.

- `bridge.py` — the service (capture + input + takeover + approval).
- `check-bridge.py` — the end-to-end test, without the app, **locally**.
- `check-public.py` — the same test **from the public address** (tunnel), including a rejected token.
- `check-token.py` — verifies that the token is **accepted as it arrives** (whitespace, extra
  `Bearer `, `?token=`) and that each refusal carries **its reason** (`bad-token:missing`,
  `bad-token:len`, `bad-token:mismatch`).

## Why a bridge, and not a route on the Hermes server

The investigation was done before writing: **the Hermes server exposes nothing that captures the
machine's screen or injects input into it.**

| What exists | What it gives | Why it is not enough |
|---|---|---|
| `computer_use` (cua-driver) | Really drives the desktop: captures, mouse, keyboard, X11 | It is a **tool inside an agent turn**, not an endpoint: no client can subscribe to it or send a click |
| Dashboard's `GET /api/display/ws` | Relays **RFB** (VNC) over WebSocket, with a takeover (`lease`) and a clean eviction | Serves only a **bot desktop**: it requires `<HERMES_HOME>/bot-desktop/rfb.sock` (sandboxed Xvnc) and a one-time `bot-desktop` **ticket** issued for the Desktop. None of that is the PC screen |
| `/api/tools/computer-use/*` | The driver's permission state | No capture, no input |
| Dashboard `terminal` / PTY | A shell | It is not the screen |

The bridge therefore takes, on the app side, **the ideas** of what already exists on the Hermes
side — control (`agent` / `human`), refusal of input without control, a single source of state
truth — but on the machine's **real screen**, and on its own channel. It is the same choice as the
companion TTS server: a local service, which the app reaches at its address.

## Running

```bash
# Dependencies: once, in a separate environment
/usr/bin/python3 -m venv ~/.onyx-computer-bridge/venv
~/.onyx-computer-bridge/venv/bin/pip install python-xlib websockets

# The test: display, ffmpeg, XTest, and an image
python3 bridge.py --display :1 doctor

# The service (local only)
python3 bridge.py --display :1 --port 8795 --fps 6 --turbo-fps 12
```

In service now: two systemd **user units** (no `sudo`), `enable`d, which
restart on their own.

```bash
systemctl --user enable --now onyx-computer-bridge.service   # the bridge (127.0.0.1:8795)
systemctl --user enable --now onyx-computer-tunnel.service    # the public cloudflared tunnel
```

| Unit | What it does | Its log |
|---|---|---|
| `onyx-computer-bridge.service` | the bridge: capture + input, on `127.0.0.1:8795` | `~/.onyx-computer-bridge/bridge.log` (and `journalctl --user -u onyx-computer-bridge`) |
| `onyx-computer-tunnel.service` | the `cloudflared` tunnel to `http://127.0.0.1:8795` | `~/.onyx-computer-bridge/tunnel.log` |

<details>
<summary>Open the bridge unit</summary>

```ini
# ~/.config/systemd/user/onyx-computer-bridge.service
[Service]
WorkingDirectory=~/onyx-computer/tools/onyx-computer-bridge
ExecStart=~/.onyx-computer-bridge/venv/bin/python …/bridge.py --display :1 --port 8795 --fps 6
Restart=always
Environment=DISPLAY=:1
Environment=XAUTHORITY=/run/user/1000/gdm/Xauthority
```
</details>

On first launch, a **token** is created in `~/.onyx-computer-bridge/token` (permissions `0600`) and
never displayed. It is the one you paste into the app, the “Ordinateur” screen → *More* → *Adresse
du pont* (bridge address).

In the app, the address to enter depends on where you are connecting from:

| From | Address |
|---|---|
| An Android emulator on the PC (the bridge runs on the host) | `http://10.0.2.2:8795` |
| A phone on the same local network | `http://<ip-du-pc>:8795` (see “Local network”) |
| **From another network** (phone elsewhere, 4G, another city) | `https://<…>.trycloudflare.com` — see “From another network” |

## The protocol, in one page

A single WebSocket, `ws://<hôte>:<port>/ws`. The token goes in a header
(`Authorization: Bearer …` or `X-Onyx-Token`), never in the URL by default.

**The bridge → the app**

| Frame | Content |
|---|---|
| binary | a JPEG image of the screen (the whole screen, downscaled; the cursor is in it) |
| `{"t":"hello"}` | `screen` (actual PC size), `view` (image size), `fps`, `viewer`, `holder` |
| `{"t":"state"}` | `holder` (`agent`/`human`), `viewer`, `busy`, `last` (the last action) |
| `{"t":"ack"}` / `{"t":"denied"}` | the outcome of the last request, with its reason |
| `{"t":"run_result"}` | the output of an approved command |

**The app → the bridge**

| Request | Effect |
|---|---|
| `take` / `release` | take control, give it back |
| `tap` `x`,`y`,`button` | a click at coordinates on the **PC screen** |
| `drag` `from`,`to` | a drag |
| `scroll` `x`,`y`,`amount` | the scroll wheel |
| `text` `text` | typing, accents included, through the keyboard's actual layout |
| `key` `keys`,`approved` | a shortcut (`ctrl+c`, `Return`, `alt+F4`…) |
| `run` `command`,`approved` | a command — **refused without `approved: true`** |
| `stream` `width`,`quality` | the **desired** image size and quality — this is not input to the PC, so control is not required |
| `rate` `moving` | the app is moving: gesture rate, otherwise idle rate |

**Whatever the app asks for, the bridge bounds it.** `stream` is clamped to `MIN_VIEW_WIDTH`…`--max-width`
(never more than the screen, always even), the quantizer to 2…31, and `rate` to `--fps`…`--max-fps`:
a client does not get to burn the PC's CPU. After a size change, the bridge
**re-announces** `hello` — the app must place its clicks in a geometry that exists. Asking for the
same thing again does not restart capture; when no one is watching anymore, the rate drops back to
idle.

Throughput is therefore tuned on both sides: the app announces the **actual** width of its pane (on a
folded phone, ~1080 pixels — asking for 1280 encoded, transmitted and decoded 200 wasted pixels),
and the bridge never goes below what remains legible nor above its ceiling.

## Control, and approval

- **A single holder.** As long as control is with the agent, every input from the app is refused
  (`denied`, reason `not_holder`): you can watch without clicking. A second client that asks for
  control while another holds it is also refused (`taken_by_other`).
- **Closing the window gives control back.** A connection that ends gives control back by itself; a
  session resumed by the app (the user leaves the screen and comes back) does not give it back.
- **Approval is in the app, refusal is in the bridge.** The app asks before sending; the
  bridge asks nothing: it refuses anything not marked approved. A modified app therefore cannot
  bypass the question. This covers **commands** (`run`) and **session
  shortcuts** (lock, logout, console switch).
- **A few commands are refused even when approved** (formatting, direct write to a disk,
  `rm -rf /`, shutting the machine down): the reason is hard-coded on both sides, in the app and in the bridge.

## Security

- **Local listening by default** (`127.0.0.1`). The bridge itself opens nothing; for the outside,
  it is the cloudflared tunnel that publishes, and it alone.
- **Mandatory token** on every connection, compared **in constant time** (`secrets.compare_digest`),
  read from a `0600` file, never logged — not even in the path of a rejected URL — never
  sent to a client, and `GET /health` returns only `{"ok": true}` (no image, no token, no machine
  name).
- **No token in the URL** on the app side: it goes in an `Authorization: Bearer <jeton>` header. The
  bridge still **tolerates** `?token=…` (compatibility), but do not use it: a URL lingers in
  everyone's logs.
- **The real origin is logged.** Behind the tunnel, the TCP peer is always `127.0.0.1`:
  the bridge reads `CF-Connecting-IP` (then `X-Forwarded-For`) and records the caller's address on
  every connection, every refusal and every action —
  `{"event":"action","kind":"click","x":1920,"y":1950,"origin":"2a01:cb1d:…"}`.
- **Attempts are rate-limited.** 5 bad tokens in 60 s from the same origin → that origin
  is refused for 5 min (`{"event":"token_blocked", "retry_in":…}`), **even with the right token** for the
  duration of the block. The limiter lives in memory: a bridge `restart` resets it to zero.
- **The token stays in the phone's keychain** (Android Keystore) and is displayed fully
  masked, with a *copy* button: the app never shows it in clear text.
- **What the bridge does not do**: it does not take control from the agent.
  Hermes's `computer_use` is not aware of the app's takeover (its view is a bot desktop, not
  this screen): while you are driving by finger, it is better to leave the agent alone.
- Touching the screen moves **the real pointer** and presses **real keys** (XTest): that is what
  “taking control” means. For the duration of the takeover, the machine's mouse is taken.

### The link is judged on the stream, not on a control ping

The bridge serves **without** WebSocket keepalive (`ping_interval=None`): it already sends six images per
second — the stream is the heartbeat, and a dead socket shows on the next send. A control ping
only added a way to kill a healthy session: its pong queues behind tens of kilobytes of image,
and one mobile-network round trip is enough to exceed the 20 s
timeout. On the app side, a control ping every 20 s produced exactly this text on screen:
“sent ping but didn't receive pong within 20000ms (after 0 successful ping/pongs)” — the text from
the client library, in English and in milliseconds, on a session that had indeed been
established. The heartbeat is therefore **application-level**: the app sends `{"t":"ping"}`, the bridge replies
`{"t":"pong"}`, and the session is only declared dead after 45 s without **any** frame.

Measured on 26/10 through the public tunnel, three simultaneous connections held for 380 s (2,283
frames each):

- control ping **client → bridge**: pong in **0 ms**;
- control ping **bridge → client**: received at **+20 s** — so it does cross cloudflared;
- a client that does **not** answer pings is cut at **+40 s** by the bridge, code 1011, reason
  `keepalive ping timeout`.

This last point settles the diagnosis: the bridge does not say “sent ping but didn't receive pong
within 20000ms”. That text did not come from the bridge but from the client, and it said nothing useful to
whoever was reading the screen.

### What remains possible for anyone with the address **and** the token

To put it plainly: **everything the screen and keyboard allow.** The token does not limit
the damage, it limits entry.

- watch the screen continuously (6 fps) — including whatever appears there in clear text;
- click, drag, type text, and therefore **open a terminal and run commands in it**;
- the `run … approved: true` route executes a shell command: this is **remote code
  execution**, with the rights of user `john` (folders, browser, unlocked keychain,
  internal network access);
- the only bounds: session shortcuts and known destructive commands
  (`mkfs`, `dd of=/dev/…`, `rm -rf /`, `shutdown`…) are refused **even when approved**, and control
  can only be held by one client at a time.

In other words: **the address and the token, together, are worth control of the machine.** The address
alone is worth nothing (try it → `token_rejected` in the log).

### Changing the token

Do this **now**: this token has circulated in session logs.

```bash
# 1. a fresh token
python3 -c "import secrets; print(secrets.token_hex(24))" > ~/.onyx-computer-bridge/token
chmod 600 ~/.onyx-computer-bridge/token

# 2. the bridge re-reads it
systemctl --user restart onyx-computer-bridge.service

# 3. copy it back into the app: Ordinateur (Computer) → More → Adresse du pont (bridge address) → Token field
cat ~/.onyx-computer-bridge/token
```

The old token is worthless from step 2 on; the app reconnects on its own with the new one.
The tunnel does not need to be restarted — so **the public address does not change**.

### Local network (phone on the same Wi-Fi)

```bash
python3 bridge.py --display :1 --port 8795 --host 0.0.0.0
```

Only do this on a trusted network, and keep the token: this is access to the machine's keyboard and
mouse.

### From another network (public tunnel) — **in service**

It is set up. The bridge still listens only on `127.0.0.1`; it is **cloudflared** that publishes an
address `https://…trycloudflare.com` and forwards it to the bridge. Nothing is opened on the router.

#### The address to enter in the app

In the app: **Ordinateur** screen (Computer) → **More** (top right) → **Adresse du pont** (bridge address), then paste
the address into the **Address** field:

```
https://give-overnight-reveal-nano.trycloudflare.com
```

**Keep the `https://`.** The app derives `wss://…/ws` from it. Without a scheme, it would use `ws://…` (port 80,
in clear text) — it does end up working, but that is the unencrypted path. The **Token** field is just
below: see further down where to get it.

To display the current address again:

```bash
grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' ~/.onyx-computer-bridge/tunnel.log | tail -1
```

> **The address changes on every tunnel restart** (it is an “express” address, with no
> Cloudflare account). If the app can no longer reach the bridge after a PC restart, that is almost always it:
> read the address above again and paste it back into the app.

#### The token

It lives in `~/.onyx-computer-bridge/token` (permissions `0600`). The app keeps it in the Android
keychain and never displays it in full.

```bash
cat ~/.onyx-computer-bridge/token      # copy into the app, once
```

#### Check status, restart

```bash
systemctl --user status  onyx-computer-bridge.service onyx-computer-tunnel.service
systemctl --user restart onyx-computer-bridge.service     # re-reads the token, re-arms the rate limiter
systemctl --user restart onyx-computer-tunnel.service     # ⚠ changes the public address
journalctl --user -u onyx-computer-bridge -n 40 --no-pager
tail -f ~/.onyx-computer-bridge/bridge.log                # who connected, who clicked
```

#### Close everything (no more access from outside)

```bash
systemctl --user stop onyx-computer-tunnel.service    # cuts public access
systemctl --user stop onyx-computer-bridge.service    # also cuts the bridge
systemctl --user disable onyx-computer-tunnel.service # so it does not start again at boot
```

A simple `stop` of the tunnel is enough: the public address stops answering, the bridge stays usable
locally.

#### Test public access, without the phone

```bash
python3 check-public.py --url wss://give-overnight-reveal-nano.trycloudflare.com/ws --tap 1920,1950
```

(see “Testing” below for what the output must say)

#### What this changes, risk-wise

Publishing this port is publishing the PC's **screen, mouse and keyboard**. The token becomes the
**only** lock; what follows protects it (see “Security”), but read the “what remains
possible” line before leaving this open permanently.

## Testing

```bash
python3 check-bridge.py --url ws://127.0.0.1:8795/ws --tap 320,180 --type "bonjour" \
    --refuse-first --command "uname -a"
```

What the script should return, and what the log (`~/.onyx-computer-bridge/bridge.log`, one JSON
line per event) should show:

```
bad token refused (ConnectionClosedError)      → {"event":"token_rejected"}
hello: screen [3840, 2160] → image [1280, 720], 6 fps
initial state: control with agent
without control: denied / not_holder           → {"event":"denied","reason":"not_holder"}
takeover: ack / taken                          → {"event":"takeover"}
command without approval: denied / needs_approval   → {"event":"denied","reason":"needs_approval"}
approved command: run_result / output 0        → {"event":"run_started"} … {"event":"run_finished"}
control given back: ack / released             → {"event":"handback"}
after handback: denied / not_holder
```

### From the public address (what the owner sees 1000 km away)

```bash
python3 check-public.py --url wss://give-overnight-reveal-nano.trycloudflare.com/ws --tap 1920,1950
```

What the output returns (actual reading from the setup):

```
with no token at all → refused at the session (code 4401)
with a bad token → refused at the session (code 4401)
hello: screen [3840, 2160] → image [1280, 720], 6 fps, control with agent
image received: 95242 bytes, JPEG 1280x720
takeover: ack / taken
click at 1920,1950 (PC screen): ack / clicked 1920,1950
pointer read back on the PC: 1920 1950 (expected 1920 1950)      ← the click really reached the PC
control given back: ack / released

— attempt rate limiting —
  attempt 1..5: code 4401
  attempt 6: code 4429  ← blocked
good token during the block → refused at the session (code 4429)
```

and the bridge log, **with the origin address**:

```
{"event":"token_rejected","origin":"2a01:cb1d:…","path":"/ws","reason":"len","token_len":48,
 "expected_len":20,"attempts_left":4}
{"event":"client_connected","viewer":"a85ac1b1","origin":"2a01:cb1d:…","clients":1}
{"event":"action","kind":"click","x":1920,"y":1950,"origin":"2a01:cb1d:…"}
{"event":"token_blocked","origin":"2a01:cb1d:…","retry_in":299.9}
```

`reason` and the two lengths are the **proof** of a rejected token: `missing` (no token
presented), `len` (the received token is not the length of the bridge's — a truncated paste, or a
token from before the change), `mismatch` (same length, different value). The token itself is
never written, and **no fingerprint is written either**: eight hexadecimal characters of a
SHA-256 are brute-forced offline in seconds against a twenty-character token, which would be
publishing the secret. The length itself says nothing about the secret and is enough to decide.

> The test blocks **your own origin** for 5 min. To re-arm it right away:
> `systemctl --user restart onyx-computer-bridge.service`.
