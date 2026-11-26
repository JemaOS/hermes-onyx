# "Bare Hermes" — what Onyx sees on a server without the companion

The client's goal is simple to state: **someone installs Onyx, connects the Hermes on
their PC, and everything that appears on screen works.** This document explains how that is
verified, and what has been measured.

## The chain to prove

```
server without the plugin  →  route absent  →  probe: NO  →  the entry is not drawn
```

Each link can be verified separately, and that is what makes the rule tenable.

## What has been measured

**1. A blank `HERMES_HOME` mounts no companion routes.**
On a fresh home with `plugins.enabled: []`, the Hermes application mounts only the bundled
plugins: `hermes-achievements` and `kanban`. No `/api/plugins/onyx-vault/*` route appears
in the routing table.

**2. A disabled plugin returns 404, not 500 and not an empty page.**
This is stated in the vault's own tests (`plugins/onyx-vault/tests/test_dashboard_mount.py`,
case `[6]`):

```
plugin disabled -> 404 {"detail":"Plugin not found"}
```

**3. An unauthenticated request returns 401 before any routing.**
Measured on the running dashboard: `/api/plugins/onyx-vault/health` without a session → `401`. This
matters for the probe, and it is why the probe classifies as follows:

| Status | Class | Consequence |
|---|---|---|
| `2xx` | `YES` | the entry is drawn |
| `404` / `405` / `501` | `NO` | the entry **does not exist** |
| `401` / `403` / `5xx` | `UNKNOWN` | "Not confirmed" — never a yes |

A `401` is not an absence: it means "I could not ask". The probe does not persist it to
cache, and the screen shows "Not confirmed" instead of promising or hiding.

**4. On the client side, `NO` closes the door.**
`ServerProbeTest` pins down: `404 → Support.NO`, `UNKNOWN` treated as `NO` by `allows()`, and
`CapabilityLabelTest` pins down what the screen shows in each case.

## What this means for the user

| Feature | Bare Hermes | With the companion |
|---|---|---|
| Chat, bots, rooms, voice, Computer, Kanban, Cron, Code, files | works | works |
| Skills via `/`, model sharing, server capabilities | works | works |
| Room leader, handover (client v1) | works | works |
| Secrets vault | **entry absent** | works |
| Demo → skill | **entry absent** | works |

## What remains to make this a recipe rather than an intention

- An **instrumented** on-device test: connect the app to a bare Hermes and check that no
  vault entry appears. Today the chain is proved from both ends (server and Kotlin unit
  tests), not by a screen walkthrough.
- The same walkthrough on a Hermes **with** the companion, to check that the entry appears *after*
  the probe, without restarting the app.
