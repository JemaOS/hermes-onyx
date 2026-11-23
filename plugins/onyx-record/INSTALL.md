# Installation — Hermes plugin `onyx-record`

Server for the **demonstration → skill** feature (batch 3): recording lease, canonical
action journal, Markdown draft. Served by the Hermes Dashboard under
`/api/plugins/onyx-record/`. No UI, no tab, no restart.

All paths below are those of John's machine (`/home/john`).

---

## 0. What is installed, and where

| Item | Location |
|---|---|
| Plugin code | `~/.hermes/plugins/onyx-record/dashboard/` |
| Activation | `plugins.enabled` in `~/.hermes/config.yaml` |
| Data | **none** — everything lives in memory, 120 s at most |

The plugin writes nothing: no database, no file, no journal. A journal sent to
`skills.draft` is not kept after the response, and a journal received by
`skills.record.stop` disappears after 120 s (the spec's collection window).
A Dashboard restart therefore loses, at worst, a journal that was about to expire.

---

## 1. Copy the plugin

```bash
mkdir -p ~/.hermes/plugins/onyx-record
cp -r /home/john/Bureau/onyx-record-plugin/dashboard \
      ~/.hermes/plugins/onyx-record/dashboard
chmod -R go-w ~/.hermes/plugins/onyx-record
```

Expected result:

```
~/.hermes/plugins/onyx-record/dashboard/
├── manifest.json      # declaration (name, hidden tab, api)
├── plugin_api.py      # router = APIRouter() — the five routes
├── onyx_record.py     # core: validation, bounds, Markdown draft
└── dist/index.js      # empty module (no UI)
```

`INSTALL.md`, `ECARTS.md`, `README.md` and `tests/` do not need to be
copied: Hermes only reads `dashboard/manifest.json`. The plugin can coexist
with `onyx-vault` — they are two folders, two lines in `plugins.enabled`,
and two distinct Python modules.

---

## 2. Enable the plugin

Open `~/.hermes/config.yaml` and add **exactly this line** (4 spaces
of indentation, under `plugins.enabled`):

```yaml
    - onyx-record
```

Full block if `plugins.enabled` does not exist yet:

```yaml
plugins:
  enabled:
    - onyx-record
```

If `plugins:` already exists, only add the `- onyx-record` line to the
existing `enabled:` list, at the same indentation.

> ⚠️ `hermes plugins enable onyx-record` **does not work**: the CLI only
> knows plugins that carry a `plugin.yaml` with `register(ctx)` code.
> A "dashboard-only" plugin is enabled only by this line. It is also the
> Dashboard's safety guard: without it, `plugin_api.py` is never imported
> (`_plugin_api_mount_skip_reason`, GHSA-mcfc-hp25-cjv7).

No restart: the Dashboard discovers the plugin on the first request
(`_PluginApiHotMount` → `_try_hot_mount_plugin_api`). **Measured precision**: when
hot, Hermes inserts the routes just before its fallback; a forced manual mount
after import would leave them *behind* that fallback and make it return
`404 {"detail":"Not Found"}`. This is why a plugin enabled at startup is the
nominal case, and why the mount test starts a real server.

---

## 3. Verify

```bash
PORT=9119   # Hermes Dashboard port
TOKEN=$(curl -s "http://127.0.0.1:$PORT/" \
        | sed -n 's/.*__HERMES_SESSION_TOKEN__="\([^"]*\)".*/\1/p' | head -1)
B="http://127.0.0.1:$PORT/api/plugins/onyx-record"
H="X-Hermes-Session-Token: $TOKEN"

# 1. does the server respond? (no journal, no lease identifier)
curl -s "$B/health"; echo

# 2. open a lease
curl -s -X POST "$B/skills/record/start" -H "$H" -H 'Content-Type: application/json' \
  -d '{"bridge":{"endpoint":"192.168.1.24:8765","session":"pc-7f21"}}'; echo
# -> {"session_id":"rec_…","started_at":…,"deadline_at":…,"max_seconds":600,"grace_seconds":120}

# 3. close the lease without a journal (abandon)
curl -s -X POST "$B/skills/record/stop" -H "$H" -H 'Content-Type: application/json' \
  -d '{"session_id":"rec_…","journal":null}'; echo
# -> {"…","end_reason":"discarded"}

# 4. convert a journal (stateless, no lease needed)
curl -s -X POST "$B/skills/draft" -H "$H" -H 'Content-Type: application/json' \
  -d @journal.json; echo
# -> {"name":…,"title":…,"steps":…,"markdown":…,"review":[…],"warnings":[…],"journal_bytes":…}
```

Expected: `health` → `"ok":true`; `start` → `200` with `max_seconds: 600`;
a second `start` without `stop` → `409 {"detail":{"code":"already_recording",…}}`;
`stop` → `200`, and a second `stop` returns **the same `bytes`**; `draft` → `200`
with a `markdown` identical to the app's golden.

Without a token, every request answers `401 {"detail":"Unauthorized"}`: that is
the Dashboard's authentication, which the plugin does not bypass.

If the Dashboard is behind the login page (`dashboard_auth`), the header
token is replaced by the session cookie: use the browser's Network tab, or test
from the app.

---

## 4. Run the tests

```bash
PY=$(ls -d ~/.hermes/tools/python-3.14.7*/bin/python3 | head -1)

# the whole suite, a single block, a single process (85 tests)
cd ~/.hermes/hermes-agent
PYTHONPATH=/home/john/.hermes/hermes-agent "$PY" -m unittest discover \
    -s /home/john/Bureau/onyx-record-plugin/tests \
    -t /home/john/Bureau/onyx-record-plugin/tests -v
```

or, from the plugin folder:

```bash
cd /home/john/Bureau/onyx-record-plugin
PYTHONPATH=/home/john/.hermes/hermes-agent "$PY" -m unittest discover -s tests -v
```

The mount test starts the real Dashboard in a disposable `HERMES_HOME`
(two child processes, no access to `~/.hermes`): so there is no need to
restart or stop the running gateway.

---

## 5. Uninstall

```bash
# 1. remove the plugin
rm -rf ~/.hermes/plugins/onyx-record

# 2. remove the line "    - onyx-record" from ~/.hermes/config.yaml
```

Without step 2, the Dashboard logs an enabled but missing plugin. Nothing
else to clean up: the plugin writes no file.

---

## 6. Settings

None. No environment variable, no key, no data folder:

| Constant | Value | Where |
|---|---|---|
| recording duration | 600 s | `onyx_record.MAX_SECONDS` |
| collection window after the end | 120 s | `onyx_record.GRACE_SECONDS` |
| events per journal | 2000 | `onyx_record.MAX_EVENTS` |
| journal size | 262144 bytes | `onyx_record.MAX_JOURNAL_BYTES` |

These bounds are those of the spec; changing them means changing the test.

---

## 7. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `401 {"detail":"Unauthorized"}` | no session token | `X-Hermes-Session-Token` header (§3) |
| `404 {"detail":"Plugin not found"}` | missing from `plugins.enabled`, or disabled at runtime | line §2; also check `plugins.disabled` |
| `404 {"detail":"No such API endpoint: …"}` | plugin enabled but `manifest.json`/`api` not found | check `~/.hermes/plugins/onyx-record/dashboard/manifest.json` |
| `404 {"detail":"Not Found"}` on a specific route | hot mount race, or plugin mounted manually behind the fallback | restart the Dashboard once (outside the plugin's scope), then re-check §3 |
| `409 already_recording` | a lease is open | the client adopts the returned `session_id`; otherwise `stop` with `journal: null` |
| `409 journal_pending` | a journal is waiting to be converted (120 s) | convert, then `start` with `"discard_pending": true` |
| `410 session_expired` | lease expired more than 120 s ago | the journal stays on the phone: `skills.draft` online still works |
| `422 secret_text_present` | a `secret: true` event carries a `text` | app-side default: the text must not be sent |
| `503 recorder_unavailable` | unexpected internal exception | check the Dashboard log (the plugin never logs content) |

---

## 8. Assumed security limits

1. **No secret in a response or a log.** A `text` accompanied by
   `secret: true` is rejected (`422`), never sanitized: the server does not decide
   to silently discard a text that was sent to it. Logs carry only
   `session_id`, counters and `end_reason`.
2. **Nothing on disk.** No journal, no draft, no state: an unreviewed draft lands
   nowhere. Writing a skill remains `POST api/skills`.
3. **The journal contains neither pixels nor audio.** The `image`, `png`,
   `screenshot`, `audio`, `transcript`… fields are rejected at any
   depth, and the seven `kind`s are closed.
4. **`bridge.endpoint` is a bare host:port**: no scheme, no identifier. A
   journal delivered under another bridge session is rejected (`409 bridge_mismatch`).
5. **The only access control is the Dashboard token** — the same level of
   trust as the Dashboard itself. The plugin reads no Hermes secret and
   opens no profile: it even rejects an explicit `profile`.
6. **One lease per server.** The profile is the Dashboard's; the route cannot
   designate another one (`400 profile_not_supported`), so "a
   recording already in progress for this profile" reads as "a lease is open".
7. **Memory only**: the journal being collected (120 s) and the lease live in
   the Dashboard process. A restart loses them — with no consequence, the
   reference journal is on the phone.
