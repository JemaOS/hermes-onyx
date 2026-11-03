# Onyx 1.0.0+1132 — the notifications are on, and the work survives the app

## The notifications are on by default, and the work survives the app

Two things John asked for: every notification enabled like his Settings screen, and a turn that
keeps running when the app is closed.

### The setting that did not match the screen

On the Notifications screen, everything was on — the system permission, the five channels, the
background alerts, the reply watcher, the sound — **except one**: *"Notify me in the background"*
was **off** by default. A fresh install was therefore mute: the bots kept working on the server,
and nobody was ever told.

It is on by default now, and `POST_NOTIFICATIONS` is requested at the **first launch**. It used to
be requested only when the user toggled the switch — so an install that already had the switch on
could never publish anything, and a foreground service without a notification gets killed.

### The foreground service could never start

This is why the work died when the app was closed. The service that keeps the process awake was
started from the **background** — and since **Android 12 (API 31)** a foreground service cannot be
started from the background: `startForegroundService()` throws
`ForegroundServiceStartNotAllowedException`, the app swallows it, falls back to a plain
notification, and the process stays unprotected. Android then dozes the app, the app stops reading
its socket, the channel dies, and the turn dies with it. The window Android grants right after the
home button is shorter than one tick of the tracker, so "start it when the app goes to the
background" was never reliable.

The service is now **tied to the turn**, not to the background state: it starts when the turn
starts — the app is visible then, which is the only moment Android allows it — and it lives until
the turn ends. The tracker reacts in milliseconds (it waits on the turn store, not on a timer), so
the service is up before the prompt is even answered.

The cost is visible: the service's notification shows during the turn, **even in the app**. A
foreground service cannot hide it, and that notification is exactly what keeps the work alive.

### The policy decides what is shown, not whether the work survives

The service used to be tied to the **notification policy**, whose default (*"when the turn ends"*)
asks for no visible progress — so the service never started, and the process was free to be put to
sleep. A policy says *what is shown*, not *whether the work survives*: the process is now held
while a turn runs, discreetly (one word, no progress bar) for the default choice; only *"never"*
is respected as asked.

The background signal is also more honest: `ON_START`/`ON_STOP` instead of `ON_RESUME`/`ON_PAUSE` —
a pause comes with every dialog and notification shade, and none of that is "the app went to the
background".

---

**Tests: 3802, 0 failures.** The locks that moved with the rule: the notification defaults (the
background alerts are on from the start), the service holding the process while a turn runs, and
the wiring lock that names the policy call the tracker makes.
