# Onyx 1.0.0+1131 — the turn outlives the app

## The turn outlives the app

Ask a question, leave the app, come back — and the turn was **dead**. The network was not the
culprit: the app was ordering the server to stop, and declaring the turn lost the moment the
channel dropped after the answer had started.

Three faults, one story.

### 1. A dropped channel killed the turn

The turn was replayed only while the bot had **not answered yet**. The moment a single word had
arrived, a dropped channel ended the turn with *"the connection to the Hermes Dashboard was lost"*
— which is what the screenshot showed.

Now a dropped channel **reattaches**: the app resumes the session the turn is running in, and the
host finishes the work. The question is **never replayed** — the bot must not answer it twice.

What comes back is the **whole** answer. `message.complete` carries the complete text, and the app
now replaces the fragment it had received before the cut instead of ignoring it. If the turn
finished *during* the outage, the app takes the finished reply straight from the host's history.

### 2. The app told the server to stop

The transport interrupted the host's turn whenever its coroutine was cancelled — and leaving the
app cancels it. The work was killed at the exact moment the person was waiting for it.

Only the **user** stops a turn now. An interruption — the app going away, the process dying, the
drawer closing — lets the host finish: its answer is there on return. The button that stops a turn
still interrupts it, and so does deleting the conversation.

### 3. Nothing held the process awake

Android dozes a background app, and a dozing app stops reading its socket: the channel died, and
the turn with it. The foreground service that prevents this was tied to the **notification
policy**, whose default (*"when the turn ends"*) asks for no visible progress — so the service
never started, and the process was free to be put to sleep.

A policy says *what is shown*, not *whether the work survives*. The service now holds the process
while a turn runs, whatever the policy — discreetly, with no progress bar for the default choice.
Only *"never"* is respected as asked: no notification, no service.

The background signal is also more honest: `ON_START`/`ON_STOP` instead of `ON_RESUME`/`ON_PAUSE`.
A pause comes with everything that passes in front of the app — a dialog, the notification shade, a
permission prompt — and none of that is the app going to the background.

---

**Tests: 3802, 0 failures.** New locks: `TourRattachementTest` (the question is not replayed, the
reattach asks the host for the history, the app leaving does not interrupt the host while the
user's stop does, and the reattach resumes the **turn's** session), `TourTenuProcessusTest` (the
process is held while a turn runs, whatever the policy, except *"never"*).

**How to try it:** send a question, press the home button, wait for the answer, come back. The
turn is still running, and the notification announces the end.
