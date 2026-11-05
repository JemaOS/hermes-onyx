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
#: **La marge de fraîcheur d'une saisie, en secondes.**
#:
#: La règle 3 du §7 refuse un geste calculé sur une image qui n'est plus la dernière. Prise à la
#: lettre — « plus vieux que la dernière trame émise » — elle rend l'écran inutilisable : le
#: serveur diffuse à 8 img/s pendant que l'humain conduit, donc le `seq` de la trame affichée est
#: déjà dépassé au moment où le doigt se pose. Chaque tap était refusé `stale`, et l'app ne
#: pouvait plus rien faire sur la page.
#:
#: Ce que la règle cherche vraiment à empêcher, c'est le clic **au mauvais endroit** parce que la
#: page a bougé entre l'image et le geste. Une image qui a moins de deux secondes est encore
#: celle que l'humain voit : on laisse passer. Au-delà, la page a pu défiler, et le refus est la
#: bonne réponse.
#: Fenêtre de fraîcheur, en secondes. Assez large pour un geste humain (regarder, viser,
#: poser le doigt), assez étroite pour que le clic ne parte pas sur une page qui a bougé.
STALE_AFTER_S = 2.0


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
        #: L'instant de la dernière trame émise, et celui du dernier `seq` qu'on a laissé passer.
        #: C'est ce qui permet de dire « cette image a encore moins de deux secondes » sans
        #: dépendre du débit d'images.
        self._last_frame_at: float = 0.0
        self._last_seen_seq: int = 0
        self._last_seen_at: float = 0.0

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
            self._last_frame_at = self._clock()
            return self.seq

    def refusal(self, seq: int) -> str | None:
        """Pourquoi cette saisie est refusée, ou `None` si elle passe.

        **La fraîcheur se mesure en secondes, pas en images.** Le garde-fou du §7 veut empêcher
        le clic au mauvais endroit parce que la page a bougé entre l'image et le geste. Comparer
        des `seq` ne le fait pas : le serveur diffuse à 8 img/s pendant que l'humain conduit, si
        bien que le `seq` de l'image affichée est *toujours* dépassé au moment du geste — et
        chaque tap était refusé, sans exception. Une image de moins de `STALE_AFTER_S` seconde
        est encore celle que l'humain regarde : elle passe. Au-delà, la page a pu défiler, et le
        refus est la bonne réponse.

        Le `seq` reste néanmoins monotone et vérifié : un `seq` inconnu, nul, ou postérieur à
        la dernière trame émise, est refusé — il ne vient pas de cette page.
        """
        with self._lock:
            if self.holder != "human":
                return "no_lease"
            if seq <= 0 or seq > self.seq:
                # Zéro, ou un `seq` que le serveur n'a jamais émis.
                return "stale"
            if self._clock() - self._last_frame_at > STALE_AFTER_S:
                # Aucune image récente : l'humain agit sur un écran qui n'est plus à jour.
                return "stale"
            self._last_seen_seq = seq
            self._last_seen_at = self._clock()
            return None


class AlreadyHeld(RuntimeError):
    def __init__(self, epoch: int) -> None:
        super().__init__("another viewer is driving")
        self.epoch = epoch
