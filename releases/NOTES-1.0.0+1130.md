# Onyx 1.0.0+1130 — the channel holds, the voice obeys, the page belongs to itself

Four defects, all felt on the phone, and one system that was missing entirely.

## 1. Two sockets for one channel

`connect()` is `suspend` and makes network round trips; nothing stopped it from running twice.
Two coroutines called it at once, opened **two** WebSockets, and the second overwrote the first
(`socket = webSocket`): the discarded socket stayed open on the server, and the first coroutine
waited for an `opened` that nobody would ever complete. The relay's log showed it — **five
`ws: tunnel opened` in two seconds**.

The channel now takes a lock, and re-checks before opening.

## 2. A dropped connection was final

The Cloudflare tunnel cuts long connections when the network changes; the channel only came back
on the next manual action — in the middle of a voice conversation, that meant the turn was dead.
It now reopens **by itself** (three spaced attempts) and announces `connection.restored`.

A turn that dies is replayed — but **only if the bot has not answered yet**: replaying a question
that already started would send it twice.

## 3. The room read its answers in a queue

While the room was working, the follow-up loop issued **thirteen RPC calls in sequence** per tick
of 500 ms — the log, the state, the live sessions, one `session.events` per bot, one `memberSession`
per member. Each is a round trip, and the tunnel adds about 60 ms: one tick of the loop waited
longer than the interval between two ticks, and the fourth bot's answer arrived after the first
three.

Every read is now **parallel**: thirteen sequential trips become three simultaneous ones.

## 4. The stop order was never obeyed

Saying "arrête d'écouter" did nothing — in French or in English. There were **two rules**, and each
ignored what the other knew:

- while **listening**, the whole transcript had to be *equal* to a phrase: "arrête d'écouter arrête"
  never matched;
- during the **reply**, only the English "stop" was recognised — "arrête d'écouter" never was.

There is now **one rule** for both. Long phrases (two words or more) are recognised anywhere; short
ones only when they are the whole utterance ("l'auto s'arrête" must not stop the recording);
negation is honoured on both sides ("ne t'arrête pas", "arrête pas", "don't stop").

Two more faults made the order pointless even when it matched: the listening path called `stop()`,
which tells nobody, and the room reopened the microphone at the end of its turn — **the stop order
was cancelled**. The group scene now wires the order to the **room's turn**, and the hands-free
loop stops with it.

## 5. The voice had to be asked twice

On the **first** entry into the voice screen — bot alone or room — the engine stopped and the app
said so; "Try again" was needed. Two causes:

- **The app took the microphone the engine was holding.** The microphone is a shared resource. The
  app opened its own `AudioRecord` — to read a level when the engine gave none — *while the engine
  was listening*, and got evicted. The trigger was exactly wrong: the engine delivers levels only
  when someone speaks, so at start-up the app judged there was nothing and took the mic just as the
  engine was grabbing it. That is why it happened on the **first** time.
- **The recovery threw the engine away.** Every failure created a new one — and a new engine has to
  bind to the recognition service, so the recovery reproduced the very failure it was meant to
  repair, until the app gave up after three attempts.

Now a dead engine (the session had started) is replaced, and a service that is not yet bound (the
session never started) is **kept**, and the attempt is replayed on it. The first start also gets a
larger budget — the binding takes time, and it is the moment it happens.

## 6. The app now tells you when a newer build is published

A card in the drawer, under the header: "Version 1.0.0+1131 is out", the promise that it installs
**over** the current one (your sign-in and settings stay), and a **Download** button.

The check is **free and local**: GitHub's public API, no account and no key, asked by the phone
itself. It is **punctual** — once at launch, once when the drawer opens, never in a loop. When
nothing can be said (no network, GitHub unreachable), **nothing is said**: the drawer stays as it
is.

The three packages of a release carry similar names (`onyx-1131-release.apk`, `onyx-auto-…`,
`onyx-wear-…`); only the **phone** is offered. An order dismissed with the cross is remembered,
and does not come back.

## 7. A page of the drawer does not inherit another page's bars

On the settings screen, the two bands framed in red — the status bar and the navigation bar — did not
belong to the page: they belonged to the **drawer**, whose glass shows the thread behind. And the
title fell **two** status bars down.

The cause was a doubled margin: the drawer set its own, and the screen set its own too. The house
rule is that **a screen carries its margins** — the full-screen host sets none. The drawer was the
exception, because it holds a **list**, which is not a screen. Now that a destination is showing,
the drawer steps aside: the screen carries the margins, fills the surface, and **its own background
goes under both bars**.

Seven screens that relied on the drawer for the navigation margin — Archives, Capabilities,
Gateway, Insights, Model, Skills, Tools — now carry it themselves. In full screen their bottom sat
under the gesture bar, since the full-screen host sets no margin either.

---

**Tests: 3792, 0 failures.** New locks: `AppUpdateTest` (reading a release, choosing the phone
package, refusing a release without a phone APK or without a number, an unreadable answer,
comparing versions), `MoteurPremierDemarrageTest` (the engine is kept when the session never
started, replaced when it had, and the app's microphone never opens while the engine listens),
`StopPhraseUnifieTest` (one rule for the stop order), `GroupRoomFeedParalleleTest` (**measures**
that the reads overlap), `ServeRpcConnexionVerrouTest` (the lock, the recovery and its bound).

## Packages

| package | bytes |
|---|---|
| `onyx-1130-release.apk` | 12 563 761 |
| `onyx-auto-1130-release.apk` | 12 823 956 |
| `onyx-wear-1130-release.apk` | 35 817 951 |

`com.onyx.app` / `com.onyx.app.auto` / `com.onyx.app.wear`, signed with the same certificate, so
they install side by side. The sha256 are in `SHA256SUMS-1130.txt`, next to the packages.
