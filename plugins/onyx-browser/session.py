"""The state of a takeover: the lease, the frames, the ticket, and the agent's wait.

Nothing here touches aiohttp or Compose: this module is **pure** (apart from CDP), hence testable without
a browser. It is where the rules we do not want to see drift live:

- a **lease** says who is driving (`agent` or `human`) and an `epoch` that advances on every transition;
- an input is refused if the lease is not human, or if the frame it was computed against
  is no longer the latest one;
- a **ticket** is single-use and expires after 30 seconds;
- the agent's wait is a `threading.Event`: the turn stays open, the browser stays alive.
"""

from __future__ import annotations

import secrets
import threading
import time
import uuid
from dataclasses import dataclass, field
from typing import Any

TICKET_TTL_S = 30.0
#: **Six minutes, not fifteen.** The tool blocks the turn while waiting for the human's hand, but the
#: tool-call harness cuts at 420 s: beyond that, the agent receives an opaque error instead of the
#: clean `{"status": "timeout"}` that tells it what to do. Staying under 420 s keeps control in the
#: plugin's code — and six minutes is more than enough to type a password.
TAKEOVER_TIMEOUT_S = 6 * 60
#: **The freshness margin for an input, in seconds.**
#:
#: Rule 3 of §7 refuses a gesture computed on an image that is no longer the latest. Taken
#: literally — "older than the last emitted frame" — it makes the screen unusable: the
#: server streams at 8 fps while the human drives, so the `seq` of the displayed frame is
#: already out of date by the time the finger lands. Every tap was refused as `stale`, and the app
#: could no longer do anything on the page.
#:
#: What the rule really seeks to prevent is the click **in the wrong place** because the
#: page moved between the image and the gesture. An image less than two seconds old is still
#: the one the human sees: we let it through. Beyond that, the page may have scrolled, and the refusal is the
#: right answer.
#: Freshness window, in seconds. Wide enough for a human gesture (look, aim,
#: place the finger), narrow enough that the click does not go to a page that has moved.
STALE_AFTER_S = 2.0


@dataclass
class Ticket:
    value: str
    expires_at: float
    viewer_id: str


@dataclass
class Waiting:
    """A pending takeover request: the agent blocks, the human can act."""

    takeover_id: str
    reason: str
    done: threading.Event = field(default_factory=threading.Event)
    note: str = ""
    facts: dict[str, Any] = field(default_factory=dict)
    #: **The conversation that asked for control** (`HERMES_SESSION_ID` of the turn). Without it, the app
    #: could not know which thread the request belonged to: it showed it in all
    #: open conversations, and the person saw it come back in threads where they had asked for
    #: nothing. It is the server that knows it, not the client.
    session: str = ""
    #: The page shown at the time of the request: with the conversation, it is the key of a refusal.
    url: str = ""
    #: **A refusal is a result.** A request that ends without `refused` or `facts` was
    #: read as a silent success: the agent believed control had been taken, whereas
    #: nobody had done anything. The flag tells the tool the truth.
    refused: bool = False


class Session:
    """The live state of a turn's browser, and the rules that go with it."""

    def __init__(self, clock=time.monotonic) -> None:
        self._clock = clock
        self.holder = "agent"
        self.epoch = 0
        self.seq = 0
        self.viewport = (1280, 720)
        #: The size that **the person's screen** announced, in physical pixels. It decides
        #: the capture sharpness; `None` = default template.
        self.phone_size: dict[str, Any] | None = None
        self.scale = 0.6
        self.frames = 0
        self.tickets: dict[str, Ticket] = {}
        self.waiting: Waiting | None = None
        self._lock = threading.RLock()
        #: The instant of the last emitted frame, and that of the last `seq` we let through.
        #: This is what makes it possible to say "this image is still less than two seconds old" without
        #: depending on the frame rate.
        self._last_frame_at: float = 0.0
        self._last_seen_seq: int = 0
        self._last_seen_at: float = 0.0

    # -- lease ---------------------------------------------------------------------------------

    def acquire(self) -> dict[str, Any]:
        """The human takes control. Returns the lease; raises if someone else is already driving."""
        with self._lock:
            if self.holder == "human":
                raise AlreadyHeld(self.epoch)
            self.holder = "human"
            self.epoch += 1
            return {"holder": "human", "epoch": self.epoch}

    def release(self, note: str = "") -> dict[str, Any]:
        """The human hands back control: the agent resumes, and its tool unblocks."""
        with self._lock:
            self.holder = "agent"
            self.epoch += 1
            waiting = self.waiting
            if waiting is not None:
                waiting.note = note.strip()
                waiting.done.set()
            return {"holder": "agent", "epoch": self.epoch}

    def abandon(self) -> dict[str, Any]:
        """The driver disappeared (screen closed, page dead): we switch back to the agent, without finishing.

        **This is not `release()`**: `release()` wakes the agent's tool as if the human
        had handed back control. Here, nobody handed anything back — the screen went dark. The agent must
        keep waiting, and the human must be able to take control again when they reopen the screen.
        """
        with self._lock:
            self.holder = "agent"
            self.epoch += 1
            return {"holder": "agent", "epoch": self.epoch}

    # -- tickets ------------------------------------------------------------------------------

    def mint(self) -> Ticket:
        with self._lock:
            now = self._clock()
            # A new ticket invalidates the old ones: a single handshake at a time, like `display`.
            self.tickets = {k: t for k, t in self.tickets.items() if t.expires_at > now}
            ticket = Ticket(
                value=secrets.token_urlsafe(24),
                expires_at=now + TICKET_TTL_S,
                viewer_id=uuid.uuid4().hex[:12],
            )
            self.tickets[ticket.value] = ticket
            return ticket

    def redeem(self, value: str) -> Ticket | None:
        """Return the ticket if it is valid, and **consume** it (single use)."""
        with self._lock:
            ticket = self.tickets.pop(value or "", None)
            if ticket is None or ticket.expires_at <= self._clock():
                return None
            return ticket

    # -- frames and inputs --------------------------------------------------------------------

    def next_seq(self) -> int:
        with self._lock:
            self.seq += 1
            self.frames += 1
            self._last_frame_at = self._clock()
            return self.seq

    def refusal(self, seq: int) -> str | None:
        """Why this input is refused, or `None` if it passes.

        **Freshness is measured in seconds, not in frames.** The §7 guard wants to prevent
        the click in the wrong place because the page moved between the image and the gesture. Comparing
        `seq` values does not do that: the server streams at 8 fps while the human drives, so
        the `seq` of the displayed image is *always* out of date by the time of the gesture — and
        every tap was refused, without exception. An image less than `STALE_AFTER_S` second old
        is still the one the human is looking at: it passes. Beyond that, the page may have scrolled, and the
        refusal is the right answer.

        The `seq` nonetheless remains monotonic and checked: an unknown `seq`, zero, or one later than
        the last emitted frame, is refused — it does not come from this page.
        """
        with self._lock:
            if self.holder != "human":
                return "no_lease"
            if seq <= 0 or seq > self.seq:
                # Zero, or a `seq` that the server never emitted.
                return "stale"
            if self._clock() - self._last_frame_at > STALE_AFTER_S:
                # No recent image: the human is acting on a screen that is no longer up to date.
                return "stale"
            self._last_seen_seq = seq
            self._last_seen_at = self._clock()
            return None


class AlreadyHeld(RuntimeError):
    def __init__(self, epoch: int) -> None:
        super().__init__("another viewer is driving")
        self.epoch = epoch
