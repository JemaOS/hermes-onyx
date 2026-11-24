# `onyx-record` — server for the demonstration → skill feature (batch 3)

Onyx's second Hermes plugin, alongside `onyx-vault`. It serves three routes and
writes nothing: a recording lease, the canonical form of an action journal,
and a skill draft **byte-identical** to the one the app already computes
(`core/.../skill/SkillDraft.kt`, golden `facture.md`).

| Route | Method | Role |
|---|---|---|
| `/api/plugins/onyx-record/skills/record/start` | POST | opens a lease (600 s, 120 s collection) |
| `/api/plugins/onyx-record/skills/record/stop` | POST | closes the lease, returns the canonical journal (or `journal: null` = abandon) |
| `/api/plugins/onyx-record/skills/draft` | POST | Markdown draft + steps to review — stateless |
| `/api/plugins/onyx-record/health` | GET | state, never a journal or an identifier |
| `/api/plugins/onyx-record/info` | GET | version, prefix, routes, bounds |

## Directory layout

```
onyx-record-plugin/
├── dashboard/                 # what Hermes reads (the only folder to install)
│   ├── manifest.json
│   ├── plugin_api.py          # router = APIRouter() — a single mount mode
│   ├── onyx_record.py         # validation, bounds, state machine, draft
│   └── dist/index.js          # empty module (hidden tab)
├── tests/
│   ├── support.py             # loads the modules like the Dashboard
│   ├── journal_fixture.py     # the Kotlin golden journal + the spec one
│   ├── test_draft_golden.py   # bytes == facture.md, determinism, secret
│   ├── test_record_core.py    # state machine, rejections, bounds, expiration
│   ├── test_plugin_api.py     # HTTP contract on a bare FastAPI app
│   ├── test_dashboard_mount.py# real Dashboard, disposable HERMES_HOME
│   └── _mount_child.py        # the disposable server (one process per scenario)
├── INSTALL.md                 # copy, plugins.enabled, verification, uninstall
├── ECARTS.md                  # the spec's contradictions, resolved and justified
└── README.md
```

## Tests

85 tests, a single command, a single process:

```bash
PY=$(ls -d ~/.hermes/tools/python-3.14.7*/bin/python3 | head -1)
cd ~/.hermes/hermes-agent
PYTHONPATH=/home/john/.hermes/hermes-agent "$PY" -m unittest discover \
    -s /home/john/Bureau/onyx-record-plugin/tests \
    -t /home/john/Bureau/onyx-record-plugin/tests -v
```

The real mount runs in child processes: the running gateway is neither
touched nor restarted, and `~/.hermes` is never written.

## What the plugin does not do

It writes no skill (`POST api/skills` does that), creates no routine,
observes neither screen nor bridge (the journal is on the phone), and keeps no
journal after the draft. See `INSTALL.md` §8 for the security limits and
`ECARTS.md` for the points where the spec contradicts itself.
