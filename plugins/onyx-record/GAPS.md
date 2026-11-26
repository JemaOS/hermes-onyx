# Gaps from `docs/SPEC-SKILLS-RECORD.md` — and why

Each gap below is **measured**, not assumed: it is covered by a test,
and the code carries a comment at the relevant place. The reading rule is
simple: when the spec contradicts itself, we keep the batch's constraint (the golden
Kotlin is the byte reference) and write down the contradiction, instead of
working around it silently.

---

## 1. The Markdown shape of §5 is not that of the shipped converter

**Spec §5** shows a `markdown` with front matter, `## Quand l'utiliser` ("When to use it"),
`## Procédure` ("Procedure") and steps in French (« Toucher **Nouveau dossier**. », "Tap
**Nouveau dossier**" — "New folder").

**The shipped Kotlin converter** (`core/.../skill/SkillDraft.kt`) and its golden
(`core/src/test/resources/skill/invoice.md`) produce something else: a `# titre` ("# title"),
an intent sentence, steps **in English** (`Navigate to`, `Tap`, `Type`,
`Scroll down`, `Wait`, `Press`) and a note `> À relire : l'étape N.` ("To review: step N").
No front matter, no section.

**What the plugin does**: the Markdown is the Kotlin one, byte for byte
(`tests/test_draft_golden.py` compares against the app's file). The batch's
constraint ("the Kotlin converter is the byte reference") and §12.1 (same input,
same output) prevail over an illustrative example. The §5 response envelope
is kept (`name`, `title`, `steps`, `markdown`, `review`, `warnings`,
`journal_bytes`); `title` is the text of the `#`, `steps` the number of numbered steps.

> Consequence for the screen: `SkillEditorScreen` will display a draft in the
> app converter's shape — not that of the §5 example. Spec §5
> itself invokes "what the editor already announces"; the app decides,
> and the app has already decided through this file.

## 2. `amount` of a `scroll`: pixels, not notches

**Spec §2.2**: "Crans, 1 à 100" ("Notches, 1 to 100"). **Spec §5**: "(3 crans)" ("(3 notches)").

**The reference converter** writes `Scroll down 760 px` and the batch golden
sums `540 + 220 = 760` — two values impossible under a bound of 100. The
server therefore accepts `amount` as a **length in pixels** (`0..100000`,
sign ignored, zero dropped by merging), and the step remains `Scroll down 760 px`.
A journal conforming to the §2.3 example (`amount: 3`) produces `Scroll down 3 px`
and not "3 crans" ("3 notches").

## 3. An unknown `kind` is rejected, not converted to `unsupported_kind`

**Spec §2.2** closes the list of the seven `kind`s ("Nothing else") and **§4** requires
`422 bad_journal` for any violation of §2. **Spec §5** asks that an unknown
`kind` become a "to review" step with reason `unsupported_kind`.

The rejection prevails (`422 bad_journal`): it is the validation rule, and it is
the same on `stop` and on `draft`. `unsupported_kind` remains in the closed list
of reasons but is never emitted — the converter keeps the branch by
symmetry with Kotlin; it is unreachable through the routes.

`long_wait` is likewise **unreachable**: §2.2 bounds `ms` at 30000, so a
"long" wait does not exist in an accepted journal, and the converter does not
flag waits (Kotlin does not do so either).

## 4. The 262144-byte bound and the `413` rejection overlap

**Spec §2.4**: beyond 262144 bytes, the oldest events are
dropped (`truncated`, `dropped`). **Spec §4**: beyond 262144 bytes
**before** bounding, `413 journal_too_large`.

The two cannot apply to the same send: the canonical form is a
subset of the received journal (unknown fields removed), so if the received one exceeds
the bound, the route has already answered `413`. The plugin therefore does both, in this
order: `413` on the received journal, then a size bound on the **canonical** one —
a second-rank guard, actually tested at the `validate` level
(`tests/test_record_core.py::TestBounds::test_byte_bound_drops_from_the_front`)
rather than through a route that can no longer trigger it.

## 5. Decisions where the spec is silent

| Situation | Choice | Why |
|---|---|---|
| A rejected journal (`422`, `413`, `409 bridge_mismatch`) | **the lease stays open** | a send error must not cost the recording; the test verifies that a correct `stop` then passes |
| Expired collecting journal (> 120 s) | `410 session_expired` | the server knew this lease and its window is over; `409 unknown_session` is reserved for identifiers never issued |
| Expired lease then `start` | `409 already_recording` (the client adopts it) | the journal can still arrive within the collection window; a new lease would discard it |
| `journal.session_id` ≠ the call's `session_id` | `422 bad_journal` | it is a journal inconsistency, not a state conflict |
| non-secret `type` with empty `text` | not turned into an error, not "to review" `texte vide` ("empty text") → code `no_label` | the spec's closed list has no `empty_text`; the line's text remains the Kotlin one |
| equal `at_ms` | accepted | two events can fall in the same millisecond; only going backwards is rejected |
| `review[]` of a secret step | present (`secret_field`) | this is what the §5 example does; the Markdown, for its part, keeps the Kotlin note and does not cite it in "À relire" ("To review") |
| Order of `review[]` | draft order | the field table says so; the §5 example lists 9 then 4, which contradicts its own table |
| `warnings` | always present, empty list if nothing moved | a client should not have to distinguish "absent" from "empty" |
| canonical `stop`, `started_at` | the phone's | it anchors the `at_ms`; `duration_ms`, for its part, is the server's (D3) |
| Journal `title` field | additive, optional | the reference converter depends on it (`ActionJournal.title`); without it, `facture.md` is not reproducible |
| `text_truncated` | never emitted | `text` > 2000 is a `422` (§2.4), not a truncation |

## 6. `drafted → start` does not exist server-side

**Spec §7** draws `drafted ── start(200) ──► recording`, whereas §7.2 requires
`409 journal_pending` for `stopped → start`. The server does not know `drafted`
(D4: `skills.draft` is stateless), so it cannot distinguish the two.

The client resolves the contradiction with what §3 already provides: after
converting (or discarding) the journal, it sends `start` with `"discard_pending": true`.
This is the only path that makes the §7 arrow true, and it requires no extra
route.

## 7. `start`: two extra error fields

`409 already_recording` carries `session_id` **and** `deadline_at`, `409
journal_pending` carries `session_id` and `expires_at`. The spec asks only for
`session_id`; §7.2 says the client "adopts the returned `session_id` and
`deadline_at`", so the second must be there. Both bodies remain under
300 characters.

## 8. The hot mount has an order, and it is measured

Outside the spec, but useful: calling `_mount_plugin_api_routes()` **after** the
server import adds the routes behind the `/api/plugins/{name}/{path:path}` fallback,
which masks them — measured in this batch: `404 {"detail":"Not Found"}` on every
route even though it is present in the table. The only authoritative mount is the
startup one (or Hermes's hot mount, which inserts the routes *before* the
fallback). This is why `tests/test_dashboard_mount.py` runs a real
Dashboard in two disposable processes instead of an in-process `TestClient`.

The plugin also has **only one mount mode** (the exported `APIRouter`,
with no environment variable and no direct registration on the application): the
vault plugin's default — two modes, one of which was non-authoritative and broke shared
tests — is not reproducible here.
