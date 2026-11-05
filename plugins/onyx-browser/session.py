"""L'état d'une prise en main : le bail, les trames, le ticket, et l'attente de l'agent.

Rien ici ne touche à aiohttp ni à Compose : ce module est **pur** (hors CDP), donc testable sans
navigateur. C'est lui qui porte les règles qu'on ne veut pas voir dériver :

- un **bail** dit qui conduit (`agent` ou `human`) et un `epoch` qui avance à chaque transition ;
- une saisie est refusée si le bail n'est pas humain, ou si la trame sur laquelle elle a été
  calculée n'est plus la dernière ;
- un **ticket** est à usage unique et périme en 30 secondes ;
- l'attente de l'agent est un `threading.Event` : le tour reste ouvert, le navigateur reste vivant.
"""

from __future__ import annotations

import secrets
import threading
import time
import uuid
from dataclasses import dataclass, field
from typing import Any

TICKET_TTL_S = 30.0
TAKEOVER_TIMEOUT_S = 15 * 60
#: Au-delà de ce retard, une saisie est refusée : la page a pu bouger (§7, règle 3).
STALE_SEQ_SLACK = 0


@dataclass
class Ticket:
    value: str
    expires_at: float
    viewer_id: str


@dataclass
class Waiting:
    """Une demande de prise en main en attente : l'agent bloque, l'humain peut agir."""

    takeover_id: str
    reason: str
    done: threading.Event = field(default_factory=threading.Event)
    note: str = ""
    facts: dict[str, Any] = field(default_factory=dict)


class Session:
    """L'état vivant du navigateur d'un tour, et les règles qui vont avec."""

    def __init__(self, clock=time.monotonic) -> None:
        self._clock = clock
        self.holder = "agent"
        self.epoch = 0
        self.seq = 0
        self.viewport = (1280, 720)
        self.scale = 0.6
        self.frames = 0
        self.tickets: dict[str, Ticket] = {}
        self.waiting: Waiting | None = None
        self._lock = threading.RLock()

    # -- bail ---------------------------------------------------------------------------------

    def acquire(self) -> dict[str, Any]:
        """L'humain prend la main. Rend le bail ; lève si un autre conduit déjà."""
        with self._lock:
            if self.holder == "human":
                raise AlreadyHeld(self.epoch)
            self.holder = "human"
            self.epoch += 1
            return {"holder": "human", "epoch": self.epoch}

    def release(self, note: str = "") -> dict[str, Any]:
        """L'humain rend la main : l'agent reprend, et son outil se débloque."""
        with self._lock:
            self.holder = "agent"
            self.epoch += 1
            waiting = self.waiting
            if waiting is not None:
                waiting.note = note.strip()
                waiting.done.set()
            return {"holder": "agent", "epoch": self.epoch}

    def abandon(self) -> dict[str, Any]:
        """Le conducteur a disparu (écran fermé, page morte) : on repasse au agent, sans finir.

        **Ce n'est pas `release()`** : `release()` réveille l'outil de l'agent comme si l'humain
        avait rendu la main. Ici, personne n'a rien rendu — l'écran s'est éteint. L'agent doit
        continuer d'attendre, et l'humain pouvoir reprendre la main quand il rouvre l'écran.
        """
        with self._lock:
            self.holder = "agent"
            self.epoch += 1
            return {"holder": "agent", "epoch": self.epoch}

    # -- tickets ------------------------------------------------------------------------------

    def mint(self) -> Ticket:
        with self._lock:
            now = self._clock()
            # Un ticket neuf périme les anciens : une seule poignée à la fois, comme `display`.
            self.tickets = {k: t for k, t in self.tickets.items() if t.expires_at > now}
            ticket = Ticket(
                value=secrets.token_urlsafe(24),
                expires_at=now + TICKET_TTL_S,
                viewer_id=uuid.uuid4().hex[:12],
            )
            self.tickets[ticket.value] = ticket
            return ticket

    def redeem(self, value: str) -> Ticket | None:
        """Rend le ticket s'il est valide, et le **consomme** (usage unique)."""
        with self._lock:
            ticket = self.tickets.pop(value or "", None)
            if ticket is None or ticket.expires_at <= self._clock():
                return None
            return ticket

    # -- trames et saisies --------------------------------------------------------------------

    def next_seq(self) -> int:
        with self._lock:
            self.seq += 1
            self.frames += 1
            return self.seq

    def refusal(self, seq: int) -> str | None:
        """Pourquoi cette saisie est refusée, ou `None` si elle passe."""
        with self._lock:
            if self.holder != "human":
                return "no_lease"
            if seq <= 0 or seq + STALE_SEQ_SLACK < self.seq:
                return "stale"
            return None


class AlreadyHeld(RuntimeError):
    def __init__(self, epoch: int) -> None:
        super().__init__("another viewer is driving")
        self.epoch = epoch
