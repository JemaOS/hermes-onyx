# First run — from a fresh Hermes to a working phone

You have Hermes running on a machine. You have just installed Onyx on your phone. This page is the
shortest path between those two facts.

It is one command, and it is safe to run twice.

## What you need

- **Hermes**, installed and running on the machine that will answer the phone.
- **`cloudflared`** on the same machine — the free binary from Cloudflare, no account needed:
  ```bash
  curl -L -o ~/.local/bin/cloudflared \
    https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64
  chmod +x ~/.local/bin/cloudflared
  ```
- **Onyx** on the phone: the latest APK from
  [releases/latest](https://github.com/JemaOS/hermes-onyx/releases/latest).

## One command

From a clone of this repository:

```bash
tools/onyx-setup.sh
```

It does five things, and says which one it is doing:

1. checks that Hermes answers on `127.0.0.1:8642` and that `cloudflared` exists;
2. draws one key for the phone and two local secrets — file mode `0600`, never printed;
3. installs and starts five user services (no `sudo`): the headless dashboard, the one-key
   gateway, its tunnel, the **Computer** bridge and the link watchdog;
4. waits until the public tunnel actually answers — a published name is not a working tunnel;
5. writes the `onyx://connect` link, prints it, and renders a **QR code**.

Scan the QR at the phone's first screen, or paste the link. That is the whole setup: chat,
conversation history, voices, skills, toolsets and the Computer screen all arrive with it.

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
