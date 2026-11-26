# First run — from a fresh Hermes to a working phone

You have Hermes running on a machine. You have just installed Onyx on your phone. This page is the
shortest path between those two facts.

It is one command, and it is safe to run twice.

## What you need

- **Hermes**, installed and running on the machine that will answer the phone.
- **Python 3.9+** on that machine — the installer is a Python script so that one file covers
  Linux, macOS and Windows.
- **`cloudflared`** — the free binary from Cloudflare, **no account needed**. The installer
  downloads it for your platform if it is missing; to do it yourself:

  ```bash
  # Linux
  curl -L -o ~/.local/bin/cloudflared \
    https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64
  chmod +x ~/.local/bin/cloudflared
  ```

- **Onyx** on the phone: the latest APK from
  [releases/latest](https://github.com/JemaOS/hermes-onyx/releases/latest).
- **Optional — voices:** a Kokoro-compatible TTS server ([Kokoro-FastAPI](https://github.com/remsky/Kokoro-FastAPI)
  or anything that answers `/v1/audio/speech`). Local, on your network, or behind its own tunnel.
  Without it the app reads replies with the phone's own voice.

## One command

From a clone of this repository.

**Linux / macOS**

```bash
python3 tools/onyx-setup.py                            # install, then print the link and its QR
python3 tools/onyx-setup.py --voices https://tts.example.com   # …and point the voice at your server
python3 tools/onyx-setup.py --link                     # print the current link again
```

**Windows** (PowerShell or `cmd`, from the repository root)

```bat
py tools\onyx-setup.py
py tools\onyx-setup.py --voices https://tts.example.com
py tools\onyx-setup.py --link
```

`python3` usually does not exist on Windows — `py` is the launcher that ships with Python. The
`tools/onyx-setup.sh` shortcut exists for Linux and macOS only; `onyx-setup.py` is the installer on
all three.

It does five things, and says which one it is doing:

1. checks that Hermes answers on `127.0.0.1:8642` and that `cloudflared` exists — and **downloads
   `cloudflared`** for the current platform if it is missing;
2. **enables Hermes' API server** if a fresh Hermes has none (adding only what is missing, keeping
   an existing key, backing up `config.yaml` first) and restarts the gateway so it is read;
3. draws one key for the phone and two local secrets — file mode `0600`, never printed;
4. installs and starts the services without `sudo` — the headless dashboard, the one-key gateway,
   its tunnel, the link watchdog, and the **Computer** bridge on Linux;
5. waits until the public tunnel actually answers — a published name is not a working tunnel — then
   writes the `onyx://connect` link, prints it framed and wrapped to the terminal width, and
   produces a **QR code**.

### About the QR

A connect link is over 300 characters, so its QR is dense (77 modules). Drawing it in the terminal
would depend on the line spacing, the font and the number of colours your terminal supports — three
things that differ per machine, and a half-broken QR is worse than none. So the script writes a
**PNG** (`~/onyx-connect-qr.png`, 648×648, error correction M, verified bit-for-bit against the
encoded matrix) and opens it with your image viewer. Short links still render inline in the
terminal. The link itself is always printed, framed, so it can be copied by hand if you prefer.

Scan the QR at the phone's first screen, or paste the link. That is the whole setup: chat,
conversation history, voices, skills, toolsets and (on Linux) the Computer screen all arrive with
it.

## What the pieces are

| Piece | Port | Why it exists |
|---|---|---|
| `hermes serve` (dashboard) | 9119 | the routes the app expects beyond chat: `/api/cron/jobs`, `/api/tools/toolsets`, `/api/mcp/servers`, `/api/system/stats`, skills, analytics |
| Onyx One | 8890 | the single key. It authenticates the phone, and speaks to each backend with the identity *that* backend expects |
| Onyx One tunnel | — | the public address the phone uses |
| Computer bridge | 8795 | the screen of the machine and its input, for the app's Computer screen |
| Watchdog (timer) | — | every two minutes: if the public name changed, the link is rebuilt — and re-sent if a recipient is configured |

The phone only ever knows **one address and one key**. Everything behind it is the machine's
business.

## After a reboot

The services are enabled, so they come back on their own. A Cloudflare *quick* tunnel gets a **new
name** each time it starts, so the link you scanned can go stale after a reboot — that is exactly
what the watchdog is for. To fetch the current link yourself:

```bash
tools/onyx-setup.sh --link      # prints the link and re-renders the QR
```

To have it delivered to you automatically when the name changes, write the recipient once:

```bash
echo "telegram:<your-chat-id>" > ~/.onyx-one/telegram-destinataire
```

## What the script does that you would otherwise do by hand

- **It enables Hermes' API server.** A fresh Hermes has no `platforms.api_server` block at all — no
  `enabled`, no port, no key. The script adds only what is missing, keeps an existing key untouched,
  and backs the file up first (`config.yaml.avant-onyx-*`). If the block already lives under
  `gateway.api_server`, it is found there and nothing is duplicated.
- **It restarts the gateway**, because the configuration is only read at startup: a key written a
  second ago is refused with a `401` while the file shows it.
- **It installs the dashboard** (`hermes serve`), without which the app gets a `404` on Scheduled
  and Tools and reports the gateway as unreachable.
- **It installs the Computer bridge** — code, virtualenv, token, tunnel.
- **It waits for the tunnel to answer**, then prints the link and its QR.

## What is still yours to do

| Situation | What you do |
|---|---|
| After a reboot, if the public name changed | run `tools/onyx-setup.sh --link` and scan the new QR |
| You want the new link pushed to you automatically | `echo "telegram:<chat-id>" > ~/.onyx-one/telegram-destinataire` |
| A named tunnel instead of a random one | needs a Cloudflare account and a domain — the script uses a *quick* tunnel, which needs neither |

**Platforms:** Linux (systemd), macOS (launchd), Windows (a task at sign-in). The installer is the
same file everywhere. Two honest limits:

- **The Computer screen bridge is Linux-only.** It captures an X11 display and injects input;
  there is no equivalent in this bridge on macOS or Windows, so that one screen stays empty there
  while everything else works.
- **macOS and Windows are implemented but not yet exercised end-to-end.** The Linux path is tested
  from a clean machine; the launchd and `schtasks` paths follow the same contract and will tell you
  what failed rather than fail silently, but treat them as unproven until someone runs them.

## Proving the whole chain, like a new user

Installers rot silently. The way to know yours still works is to wipe everything it created and run
it again — which is what `tools/onyx-reset.py` is for:

```bash
python3 tools/onyx-reset.py            # asks before doing anything
python3 tools/onyx-setup.py            # the whole install, from nothing
```

On Windows: `py tools\onyx-reset.py` then `py tools\onyx-setup.py`.

`onyx-reset.py` removes the services (systemd units and their `*.service.d` overrides, launchd
agents, scheduled tasks), `~/.onyx-one` (key, secrets, dashboard credentials), the Computer bridge
(code, venv, token, logs), and the link and its QR. **It never touches Hermes** — its config,
sessions and database stay as they are.

A reset + install on a clean machine should print no `!` line at all. What it produced on the
reference machine, from nothing:

- 10 of 10 routes answering `200` through the public tunnel (`/v1/models`, `/api/sessions`,
  `/api/tools/toolsets`, `/api/cron/jobs`, `/api/skills`, `/api/profiles`, `/api/system/stats`,
  `/api/mcp/servers`, `/api/analytics/usage`, `/api/onyx/endpoints`)
- a real completion answered through the tunnel
- the WebSocket upgrading: `101 Switching Protocols`
- `/v1/models` without the key: `401`
- the Computer bridge: `/health` `200`
- running it a second time: same key, **same tunnel, same link**, zero warnings

## If something is wrong

```bash
journalctl --user -u onyx-one -u onyx-dashboard -u onyx-one-tunnel -f
```

- **`401` from the gateway** — the key in the link is not the one the gateway loaded; re-run the
  script, which reads the key from the live gateway rather than from a file.
- **One screen shows a 404** — that screen's route is missing from the dashboard: check
  `onyx-dashboard` is active.
- **Computer shows "bridge unreachable"** — check `onyx-computer-bridge` is active; it needs a
  graphical session (`DISPLAY`) to capture.
