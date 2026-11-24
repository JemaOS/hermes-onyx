# Onyx's server companions

Onyx is a **client**. It has no account, no server, no subscription: it talks to the Hermes
running on *your* machine. Some functions, however, cannot live in a
phone — a key vault must be encrypted where the bot works, a demonstration journal
must be bounded where the turn executes.

Those functions are **Hermes plugins**, shipped here, in this repository, so that whoever
installs Onyx can place them alongside:

```
plugins/
├── onyx-vault/          # the secret vault per profile
│   ├── dashboard/       # ← the folder to copy into ~/.hermes/plugins/
│   ├── tests/           # 35 unittest tests, most runnable without Hermes
│   └── INSTALL.md       # two steps, no paid dependency
└── onyx-record/         # the demonstration→skill server side (lease, bounded journal, draft)
    ├── dashboard/
    ├── tests/           # 89 tests, including the golden identical to the Kotlin converter
    ├── ECARTS.md        # the 14 points where the spec was silent or contradicted itself
    └── INSTALL.md
```

## What this changes for the user

| Onyx function | Bare Hermes | Hermes + the companion |
|---|---|---|
| Chat, bots, rooms, voice, Computer, Kanban, Cron, Code, files | **works** | works |
| Skills via `/`, sharing a bot template, server capabilities | **works** | works |
| Room leader and handover (client v1) | **works** | works |
| Secret vault (write/list/revoke) | **the entry does not exist** | works |
| Demonstration → skill (the server bounds the journal) | **the entry does not exist** | works |

"The entry does not exist" is the rule: Onyx queries the server (`ServerProbe`) and **does not draw**
what it cannot do. No empty screen, no dead button, no empty promise.

## Installing a companion

```bash
cp -r plugins/onyx-vault ~/.hermes/plugins/onyx-vault
# then, in ~/.hermes/config.yaml, under plugins.enabled:
#     - onyx-vault
```

The details (encryption key, verification, uninstall) are in `plugins/onyx-vault/INSTALL.md`.
No account, no sign-up, no outgoing call: the routes mount on **your** dashboard.

## What these plugins will never do

- send a secret, a journal or an identifier to a third-party service;
- ask for an API key from a paid provider;
- activate without an explicit line in `config.yaml` — an unlisted plugin is not even imported.
