#!/usr/bin/env python3
"""Pont « Ordinateur » — l'écran du PC en direct et ses entrées, pour l'app Onyx.

Pourquoi ce pont existe
-----------------------
Le serveur Hermes n'expose **aucune** route pour capturer l'écran du poste ni pour y injecter
des entrées : `computer_use` est un outil *dans* un tour d'agent, pas un point d'accès, et
`/api/display/ws` ne relaie que le RFB d'un bureau de bot (Xvnc **dans** un bac à sable,
ticket à usage unique réservé au Desktop). Il fallait donc un compagnon local, comme le
serveur TTS : il tourne sur le PC, parle à l'app, et ne touche à rien d'autre.

Ce qu'il fait
-------------
- **Capture** : un seul ffmpeg `x11grab` sur l'affichage X souhaité, ré-encodé en JPEG
  (quelques images par seconde), diffusé à tous les clients abonnés ; l'image la plus récente
  seulement, jamais de file d'attente qui prendrait du retard.
- **Entrées** : XTest (du vrai pointeur, du vrai clavier — c'est ce que veut dire « prendre la
  main »), au clic, au glisser, à la molette et au clavier.
- **Prise de main** : un seul détenteur — l'agent, ou un humain (l'app). Comme dans Hermes, la
  main se prend et se rend explicitement ; un client qui n'a pas la main peut regarder mais
  chaque entrée est refusée, avec la raison.
- **Approbation** : les actions sensibles (une commande, les raccourcis qui verrouillent ou
  changent de session) exigent `approved: true`. Le pont refuse, il ne demande pas : la
  demande d'approbation vit dans l'app, devant l'utilisateur.

Sécurité
--------
- Écoute **en local seulement** (`127.0.0.1` par défaut). Aucun service n'est exposé sur
  l'Internet public par ce script ; un accès distant (réseau local, ou tunnel) se décide et se
  monte à la main — voir README.md.
- **Jeton obligatoire** sur chaque connexion (en-tête `Authorization: Bearer <jeton>` ou
  `X-Onyx-Token`), lu dans un fichier `0600`, comparé à **temps constant**. Le jeton n'est jamais
  journalisé — pas même dans le chemin d'une URL refusée — ni rendu par une route, ni renvoyé au
  client.
- **Origine réelle** : derrière un tunnel, le pair TCP est toujours `127.0.0.1`. Chaque
  connexion et chaque action est journalisée avec l'adresse d'origine (`CF-Connecting-IP`, sinon
  `X-Forwarded-For`, sinon le pair).
- **Tentatives limitées** : au-delà de `AUTH_MAX_FAILURES` jetons faux en `AUTH_WINDOW_SECONDS`
  depuis une même origine, cette origine est mise au coin `AUTH_BLOCK_SECONDS`
  (`token_blocked` au journal).
- `GET /health` ne dit que « le pont est là » : pas d'image, pas de jeton, pas de nom de
  machine.
"""

from __future__ import annotations

import argparse
import asyncio
import json
import os
import re
import secrets
import shutil
import subprocess
import sys
import threading
import time
from pathlib import Path
from urllib.parse import unquote

LOG_PATH = Path.home() / ".onyx-computer-bridge" / "bridge.log"
TOKEN_PATH = Path.home() / ".onyx-computer-bridge" / "token"

JPEG_SOI = b"\xff\xd8"
JPEG_EOI = b"\xff\xd9"

#: La largeur d'image minimale : en dessous, le texte du PC ne se lit plus.
MIN_VIEW_WIDTH = 480

#: Raccourcis qui verrouillent la session, l'éteignent ou en changent : ils demandent une
#: approbation explicite, comme une commande.
SENSITIVE_KEYS = {
    "ctrl+alt+l", "super+l", "logoff", "ctrl+alt+delete", "ctrl+alt+backspace",
    "ctrl+alt+f1", "ctrl+alt+f2", "ctrl+alt+f3", "ctrl+alt+f4", "ctrl+alt+f5", "ctrl+alt+f6",
}

#: Ce que le pont refuse même avec l'approbation : ça n'a rien à faire dans un aperçu piloté.
DENY_COMMANDS = (
    re.compile(r"\bmkfs(\.|\s)"),
    re.compile(r"\bdd\b[^\n]*\bof=/dev/"),
    re.compile(r"\brm\s+-rf\s+/(\s|$)"),
    re.compile(r"\bshutdown\b|\breboot\b|\bpoweroff\b"),
    re.compile(r":\(\)\s*\{"),
)

#: Combien de jetons faux une même origine peut présenter, sur quelle fenêtre, et pour quelle
#: mise au coin. Le jeton est la seule serrure d'un pont exposé : on compte les essais.
AUTH_MAX_FAILURES = 5
AUTH_WINDOW_SECONDS = 60.0
AUTH_BLOCK_SECONDS = 300.0


def log_line(event: str, **fields) -> None:
    """Une ligne JSON par événement, horodatée — la preuve se lit ici, pas à l'écran."""
    record = {"at": round(time.time(), 3), "event": event}
    record.update(fields)
    text = json.dumps(record, ensure_ascii=False)
    print(text, flush=True)
    try:
        LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
        with LOG_PATH.open("a", encoding="utf-8") as handle:
            handle.write(text + "\n")
    except OSError:
        pass


# ----------------------------------------------------------------------------- jeton


def read_token(path: Path) -> str:
    if path.exists():
        return path.read_text(encoding="utf-8").strip()
    token = secrets.token_hex(24)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(token + "\n", encoding="utf-8")
    os.chmod(path, 0o600)
    return token


# ----------------------------------------------------------------------------- origine


def client_origin(request, connection) -> str:
    """L'adresse d'où vient vraiment une connexion.

    Derrière un tunnel (cloudflared), le pair TCP est **toujours** `127.0.0.1` : sans lire les
    en-têtes posés par le tunnel, toutes les traces se ressemblent et une origine malveillante
    reste anonyme. On lit donc, dans l'ordre : `CF-Connecting-IP` (posé par Cloudflare), le
    premier saut de `X-Forwarded-For`, puis le pair TCP en dernier recours.
    """
    headers = getattr(request, "headers", None)
    if headers is not None:
        for header in ("CF-Connecting-IP", "X-Real-IP"):
            value = (headers.get(header) or "").strip()
            if value:
                return value
        forwarded = (headers.get("X-Forwarded-For") or "").strip()
        if forwarded:
            return forwarded.split(",")[0].strip()
    peer = getattr(connection, "remote_address", None)
    if isinstance(peer, tuple) and peer:
        return str(peer[0])
    return str(peer) if peer else "inconnue"


# ----------------------------------------------------------------------------- garde


class AuthGuard:
    """Limite les tentatives de jeton — la seule serrure d'un pont exposé.

    Le jeton fait 48 caractères hexadécimaux (192 bits) : on ne le devine pas. Mais un tunnel
    public laisse, sans ce filet, essayer des milliers de jetons par minute. On compte donc les
    échecs **par origine** sur une fenêtre glissante ; au-delà du seuil, l'origine est mise au
    coin un moment, et l'événement se lit dans le journal.
    """

    def __init__(self, max_failures: int = AUTH_MAX_FAILURES,
                 window: float = AUTH_WINDOW_SECONDS,
                 block: float = AUTH_BLOCK_SECONDS) -> None:
        self.max_failures = max_failures
        self.window = window
        self.block = block
        self._failures: dict[str, list[float]] = {}
        self._blocked_until: dict[str, float] = {}

    def blocked_for(self, origin: str) -> float:
        """Les secondes de blocage restantes — 0 quand l'origine peut encore essayer."""
        until = self._blocked_until.get(origin, 0.0)
        now = time.time()
        if until <= now:
            self._blocked_until.pop(origin, None)
            return 0.0
        return round(until - now, 1)

    def fail(self, origin: str) -> float:
        """Note un échec ; rend les secondes de blocage s'il vient d'atteindre le seuil."""
        now = time.time()
        recent = [at for at in self._failures.get(origin, []) if now - at < self.window]
        recent.append(now)
        if len(recent) >= self.max_failures:
            self._failures.pop(origin, None)
            self._blocked_until[origin] = now + self.block
            return self.block
        self._failures[origin] = recent
        return 0.0

    def succeed(self, origin: str) -> None:
        self._failures.pop(origin, None)

    def remaining(self, origin: str) -> int:
        """Combien d'essais il reste à cette origine avant la mise au coin."""
        return max(0, self.max_failures - len(self._failures.get(origin, [])))


# ----------------------------------------------------------------------------- écran


class Screen:
    """Taille de l'affichage X et géométrie de l'image envoyée à l'app."""

    def __init__(self, display: str, max_width: int) -> None:
        from Xlib import display as xdisplay

        self.display_name = display
        self.x = xdisplay.Display(display)
        self.width = self.x.screen().width_in_pixels
        self.height = self.x.screen().height_in_pixels
        self.max_width = max_width
        self.view_width = min(max_width, self.width)
        # Hauteur paire : les encodeurs vidéo n'aiment pas les tailles impaires.
        self.view_height = int(round(self.height * self.view_width / self.width)) // 2 * 2

    def set_view_width(self, width: int) -> int:
        """Choisit la largeur de l'image envoyée, **dans les bornes du pont**.

        Le client annonce la largeur de son volet : envoyer 1280 pixels de large à un aperçu de
        1080 en fait 200 qui seront encodés, transmis puis jetés. Le pont **borne** quand même —
        jamais moins de `MIN_VIEW_WIDTH` (illisible), jamais plus que son `--max-width`, jamais plus
        que l'écran lui-même, et toujours pair.

        Rend la largeur finalement retenue (l'appelant s'en sert pour répondre au client).
        """
        ceiling = max(MIN_VIEW_WIDTH, min(self.max_width, self.width))
        wanted = max(MIN_VIEW_WIDTH, min(int(width or 0), ceiling))
        wanted = wanted // 2 * 2
        if wanted == self.view_width:
            return self.view_width
        self.view_width = wanted
        self.view_height = int(round(self.height * self.view_width / self.width)) // 2 * 2
        return self.view_width

    def to_screen(self, x: float, y: float) -> tuple[int, int]:
        """Des pixels de l'image reçue par l'app vers les pixels de l'écran du PC."""
        sx = int(round(x * self.width / self.view_width))
        sy = int(round(y * self.height / self.view_height))
        return max(0, min(self.width - 1, sx)), max(0, min(self.height - 1, sy))


class Input:
    """Le pointeur et le clavier du PC, par XTest — de vrais événements, pas des simulacres."""

    def __init__(self, screen: Screen) -> None:
        from Xlib import X, XK
        from Xlib.ext import xtest

        self.x = screen.x
        self.kind = X
        self.keysym = XK
        self.xtest = xtest
        self.lock = threading.Lock()
        # L'extension XTest est ce qui permet de frapper du vrai pointeur et du vrai clavier.
        # `get_version` échoue (bad-request) quand elle manque : on ne peut alors rien piloter.
        try:
            xtest.get_version(self.x, 2, 2)
        except Exception as error:  # pragma: no cover - dépend de l'affichage
            raise RuntimeError(f"l'extension XTest manque sur cet affichage : {error}") from error

    def _sync(self) -> None:
        self.x.sync()

    def move(self, sx: int, sy: int) -> None:
        self.xtest.fake_input(self.x, self.kind.MotionNotify, x=sx, y=sy)
        self._sync()

    def _button(self, number: int, press: bool) -> None:
        # XTest veut 1 pour l'appui et 0 pour le relâchement.
        self.xtest.fake_input(self.x, self.kind.ButtonPress if press else self.kind.ButtonRelease, number)
        self._sync()

    def click(self, sx: int, sy: int, button: int = 1) -> None:
        with self.lock:
            self.move(sx, sy)
            time.sleep(0.02)
            self._button(button, True)
            time.sleep(0.02)
            self._button(button, False)

    def drag(self, from_xy: tuple[int, int], to_xy: tuple[int, int], steps: int = 14) -> None:
        with self.lock:
            self.move(*from_xy)
            time.sleep(0.03)
            self._button(1, True)
            x0, y0 = from_xy
            x1, y1 = to_xy
            for step in range(1, steps + 1):
                self.move(int(x0 + (x1 - x0) * step / steps), int(y0 + (y1 - y0) * step / steps))
                time.sleep(0.012)
            self._button(1, False)

    def scroll(self, sx: int, sy: int, dy: int) -> None:
        with self.lock:
            self.move(sx, sy)
            number = 4 if dy < 0 else 5
            for _ in range(max(1, abs(dy))):
                self._button(number, True)
                self._button(number, False)

    def _stroke(self, name: str, shift: bool) -> None:
        """Un nom de touche X11 (`Return`, `a`, `Escape`…) appuyé puis relâché."""
        code = self.x.keysym_to_keycode(self.keysym.string_to_keysym(name))
        if code == 0:
            raise ValueError(f"touche inconnue : {name}")
        if shift:
            self._press_keycode(self.x.keysym_to_keycode(self.keysym.string_to_keysym("Shift_L")))
        self._press_keycode(code)
        if shift:
            self._release_keycode(self.x.keysym_to_keycode(self.keysym.string_to_keysym("Shift_L")))

    def _press_keycode(self, code: int) -> None:
        self.xtest.fake_input(self.x, self.kind.KeyPress, code)
        self._sync()

    def _release_keycode(self, code: int) -> None:
        self.xtest.fake_input(self.x, self.kind.KeyRelease, code)
        self._sync()

    def _keystroke_for(self, char: str) -> tuple[int, bool]:
        """Le code de touche et l'éventuel Maj, en lisant la disposition réelle du clavier.

        On ne code pas la table AZERTY/QWERTY en dur : on demande au serveur X quel code et quel
        niveau portent ce caractère. C'est ce qui fait arriver « é à ç » tels quels.
        """
        codepoint = ord(char)
        target = codepoint if codepoint < 0x100 else 0x01000000 + codepoint
        for code in range(8, 256):
            for level in range(4):
                if self.x.keycode_to_keysym(code, level) == target:
                    return code, bool(level & 1)
        raise ValueError(f"caractère absent du clavier : {char!r}")

    def type_text(self, text: str) -> None:
        with self.lock:
            for char in text:
                if char == "\n":
                    self._stroke("Return", False)
                    continue
                if char == "\t":
                    self._stroke("Tab", False)
                    continue
                code, shift = self._keystroke_for(char)
                if shift:
                    shift_code = self.x.keysym_to_keycode(self.keysym.string_to_keysym("Shift_L"))
                    self._press_keycode(shift_code)
                self._press_keycode(code)
                self._release_keycode(code)
                if shift:
                    self._release_keycode(shift_code)
                time.sleep(0.008)

    def combo(self, keys: str) -> None:
        """`ctrl+c`, `alt+F4`, `Return` : les modifications se nomment comme dans l'app."""
        parts = [p.strip() for p in keys.split("+") if p.strip()]
        if not parts:
            raise ValueError("raccourci vide")
        named = {
            "ctrl": "Control_L", "control": "Control_L", "alt": "Alt_L", "shift": "Shift_L",
            "super": "Super_L", "meta": "Super_L", "cmd": "Super_L", "win": "Super_L",
        }
        aliases = {
            "return": "Return", "enter": "Return", "escape": "Escape", "esc": "Escape",
            "tab": "Tab", "space": "space", "backspace": "BackSpace", "delete": "Delete",
            "up": "Up", "down": "Down", "left": "Left", "right": "Right", "home": "Home",
            "end": "End", "pageup": "Prior", "pagedown": "Next", "f1": "F1", "f2": "F2",
            "f3": "F3", "f4": "F4", "f5": "F5", "f6": "F6", "f7": "F7", "f8": "F8",
            "f9": "F9", "f10": "F10", "f11": "F11", "f12": "F12",
        }
        with self.lock:
            modifiers: list[int] = []
            for part in parts[:-1]:
                name = named.get(part.lower())
                if name is None:
                    raise ValueError(f"modificateur inconnu : {part}")
                code = self.x.keysym_to_keycode(self.keysym.string_to_keysym(name))
                modifiers.append(code)
                self._press_keycode(code)
            last = parts[-1]
            name = aliases.get(last.lower(), last)
            code = self.x.keysym_to_keycode(self.keysym.string_to_keysym(name))
            if code == 0:
                raise ValueError(f"touche inconnue : {last}")
            self._press_keycode(code)
            self._release_keycode(code)
            for code in reversed(modifiers):
                self._release_keycode(code)


class Frames:
    """Un ffmpeg pour tous les clients : il tourne tant qu'il y a quelqu'un pour regarder."""

    def __init__(self, screen: Screen, fps: int, quality: int, ffmpeg: str,
                 max_fps: int = 24) -> None:
        self.screen = screen
        self.fps = max(1, min(max_fps, fps))
        #: Le plafond de cadence : un client ne décide pas de brûler le processeur du PC.
        self.max_fps = max(1, max_fps)
        self.quality = quality
        self.ffmpeg = ffmpeg
        self.latest: bytes | None = None
        self.seq = 0
        self._process: subprocess.Popen | None = None
        self._thread: threading.Thread | None = None
        self._loop: asyncio.AbstractEventLoop | None = None
        self._waiters: list[asyncio.Event] = []
        self._view_width = screen.view_width

    def configure(self, fps: int | None = None, view_width: int | None = None,
                  quality: int | None = None) -> bool:
        """Change la cadence, la taille ou la qualité de la capture — et dit si elle a repris.

        Un seul `ffmpeg` sert tous les clients : on ne peut pas lui donner deux cadences. La
        demande la plus récente gagne, ce qui est exactement ce qu'on veut ici (un seul client, le
        téléphone du propriétaire). On ne redémarre **que** si quelque chose a réellement changé :
        une session qui se reconnecte ne doit pas faire clignoter la capture.
        """
        changed = False
        if fps is not None and int(fps) != self.fps:
            self.fps = max(1, int(fps))
            changed = True
        if quality is not None and int(quality) != self.quality:
            # Le quantificateur JPEG de ffmpeg va de 2 (fin) à 31 (grossier).
            self.quality = max(2, min(31, int(quality)))
            changed = True
        if view_width is not None and self.screen.set_view_width(view_width) != self._view_width:
            changed = True
        if not changed:
            return True
        self._view_width = self.screen.view_width
        self.restart()
        return self._process is not None

    def restart(self) -> None:
        """Relance la capture avec les réglages courants.

        L'image déjà reçue reste en mémoire : un redémarrage ne doit pas vider l'écran de
        l'utilisateur ni lui montrer un trou noir le temps que le premier JPEG arrive.
        """
        loop = self._loop
        self.stop()
        if loop is not None:
            self.start(loop, keep_latest=True)

    def _command(self) -> list[str]:
        size = f"{self.screen.width}x{self.screen.height}"
        scale = f"scale={self.screen.view_width}:{self.screen.view_height}"
        return [
            self.ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin",
            "-f", "x11grab", "-draw_mouse", "1", "-framerate", str(self.fps),
            "-video_size", size, "-i", self.screen.display_name,
            "-vf", f"{scale},fps={self.fps}",
            "-f", "mjpeg", "-q:v", str(self.quality), "-",
        ]

    def _read(self) -> None:
        buffer = b""
        assert self._process is not None and self._process.stdout is not None
        stream = self._process.stdout
        while True:
            chunk = stream.read(64 * 1024)
            if not chunk:
                break
            buffer += chunk
            while True:
                start = buffer.find(JPEG_SOI)
                if start < 0:
                    buffer = b""
                    break
                end = buffer.find(JPEG_EOI, start + 2)
                if end < 0:
                    buffer = buffer[start:]
                    break
                frame = buffer[start:end + 2]
                buffer = buffer[end + 2:]
                self.latest = frame
                self.seq += 1
                if self._loop is not None:
                    for waiter in list(self._waiters):
                        self._loop.call_soon_threadsafe(waiter.set)
        log_line("capture_stopped")

    def start(self, loop: asyncio.AbstractEventLoop, keep_latest: bool = False) -> None:
        self._loop = loop
        if not keep_latest:
            self.latest = None
        log_line("capture_started", display=self.screen.display_name,
                 screen=[self.screen.width, self.screen.height],
                 view=[self.screen.view_width, self.screen.view_height], fps=self.fps,
                 quality=self.quality)
        self._process = subprocess.Popen(
            self._command(), stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, bufsize=0,
        )
        self._thread = threading.Thread(target=self._read, daemon=True)
        self._thread.start()

    def subscribe(self) -> asyncio.Event:
        event = asyncio.Event()
        self._waiters.append(event)
        return event

    def unsubscribe(self, event: asyncio.Event) -> None:
        if event in self._waiters:
            self._waiters.remove(event)

    def stop(self) -> None:
        if self._process is not None:
            self._process.kill()
            self._process = None


# ----------------------------------------------------------------------------- état


class Lease:
    """Qui a la main : l'agent (personne ne pilote) ou un humain (une connexion de l'app)."""

    def __init__(self) -> None:
        self.holder = "agent"
        self.viewer: str | None = None
        self.since = time.time()
        self.epoch = 0
        self.last: dict | None = None
        self.busy = False

    def take(self, viewer: str) -> bool:
        if self.holder == "human" and self.viewer != viewer:
            return False
        if self.holder != "human":
            self.holder = "human"
            self.viewer = viewer
            self.epoch += 1
            self.since = time.time()
            log_line("takeover", viewer=viewer, epoch=self.epoch)
        return True

    def release(self, viewer: str, reason: str) -> None:
        if self.holder == "human" and self.viewer == viewer:
            self.holder = "agent"
            self.viewer = None
            self.epoch += 1
            self.since = time.time()
            log_line("handback", viewer=viewer, reason=reason, epoch=self.epoch)

    def may_send_input(self, viewer: str) -> bool:
        return self.holder == "human" and self.viewer == viewer

    def note(self, kind: str, detail: str, x: int | None = None, y: int | None = None,
             origin: str | None = None) -> None:
        self.last = {"kind": kind, "detail": detail, "at": round(time.time() * 1000),
                     "x": x, "y": y}
        log_line("action", kind=kind, detail=detail, x=x, y=y, viewer=self.viewer,
                 origin=origin)

    def state(self) -> dict:
        return {
            "t": "state",
            "holder": self.holder,
            "viewer": self.viewer,
            "since": round(self.since, 3),
            "epoch": self.epoch,
            "busy": self.busy,
            "last": self.last,
        }


# ----------------------------------------------------------------------------- service


class Bridge:
    def __init__(self, screen: Screen, frames: Frames, inputs: Input, token: str,
                 shell_timeout: float, turbo_fps: int = 12) -> None:
        self.screen = screen
        self.frames = frames
        self.inputs = inputs
        self.token = token
        self.shell_timeout = shell_timeout
        #: La cadence au repos et celle d'un geste. Le client dit « je bouge », le pont décide
        #: combien d'images cela vaut — c'est lui qui connaît son processeur et son plafond.
        self.base_fps = frames.fps
        self.turbo_fps = max(self.base_fps, min(frames.max_fps, turbo_fps))
        self.lease = Lease()
        self.clients: set = set()

    def hello_payload(self, viewer: str) -> dict:
        """Ce que le pont annonce en s'ouvrant — et **de nouveau** quand la taille change.

        Passe par une seule fonction : deux endroits qui annoncent la taille finiraient par se
        contredire, et l'app croirait décoder une image qui n'existe plus.
        """
        return {
            "t": "hello",
            "screen": [self.screen.width, self.screen.height],
            "view": [self.screen.view_width, self.screen.view_height],
            "fps": self.frames.fps,
            "viewer": viewer,
            "holder": self.lease.holder,
        }

    def _point(self, message: dict) -> tuple[int, int]:
        """Un point reçu, ramené aux pixels de l'écran du PC.

        Deux espaces, et le client dit lequel il emploie : `view` (les pixels de l'image qu'il
        reçoit, que le pont agrandit) ou `screen` (déjà des pixels de l'écran du PC, comme le fait
        l'app, qui a mesuré l'image affichée chez elle). Sans cette précision, une app qui a déjà
        mis à l'échelle serait mise à l'échelle **deux fois** — et le clic partirait ailleurs.
        """
        x, y = float(message["x"]), float(message["y"])
        if str(message.get("space") or "view") == "screen":
            return (max(0, min(self.screen.width - 1, int(round(x)))),
                    max(0, min(self.screen.height - 1, int(round(y)))))
        return self.screen.to_screen(x, y)

    # -- diffusion d'état -------------------------------------------------------------

    async def broadcast(self, payload: dict) -> None:
        text = json.dumps(payload, ensure_ascii=False)
        for client in list(self.clients):
            try:
                await client.send(text)
            except Exception:  # un client parti ne doit pas interrompre la diffusion
                pass

    async def bump(self) -> None:
        await self.broadcast(self.lease.state())

    # -- actions ----------------------------------------------------------------------

    async def handle(self, client, message: dict) -> None:
        kind = str(message.get("t") or "")
        viewer = str(message.get("viewer") or "")
        echo = message.get("id")
        origin = getattr(client, "origin", "inconnue")

        if kind == "hello":
            await client.send(json.dumps(self.hello_payload(viewer)))
            await self.bump()
            return

        if kind == "ping":
            await client.send(json.dumps({"t": "pong", "echo": echo}))
            return

        # Ce que le client veut **recevoir** n'est pas une entrée sur le PC : la taille d'image et
        # la cadence se règlent sans la main (on regarde avant de piloter, et un invité qui regarde
        # ne doit pas être obligé de prendre la main pour lire son écran). Le pont borne tout : la
        # largeur par `--max-width` et par l'écran, la cadence par `--max-fps`.
        if kind == "stream":
            width = int(message.get("width") or 0)
            quality = int(message.get("quality") or 0)
            self.frames.configure(
                view_width=width or None,
                quality=quality or None,
            )
            log_line("stream_changed", view=[self.screen.view_width, self.screen.view_height],
                     fps=self.frames.fps, quality=self.frames.quality, origin=origin)
            # L'image reçue n'a plus la taille annoncée : on la réannonce, sinon l'app place ses
            # clics dans une géométrie qui n'existe plus.
            await client.send(json.dumps(self.hello_payload(viewer)))
            await self._ack(client, echo, True,
                            f"view {self.screen.view_width}x{self.screen.view_height}")
            return

        if kind == "rate":
            moving = bool(message.get("moving"))
            target = self.turbo_fps if moving else self.base_fps
            self.frames.configure(fps=target)
            log_line("rate_changed", moving=moving, fps=self.frames.fps, origin=origin)
            await self._ack(client, echo, True, f"{self.frames.fps} fps")
            return

        if kind == "take":
            if self.lease.take(viewer):
                await self.bump()
                await self._ack(client, echo, True, "taken")
            else:
                await self._deny(client, echo, "taken_by_other")
            return

        if kind == "release":
            self.lease.release(viewer, "app")
            await self.bump()
            await self._ack(client, echo, True, "released")
            return

        if kind == "state":
            await client.send(json.dumps(self.lease.state()))
            return

        # Tout le reste est une entrée : il faut la main.
        if not self.lease.may_send_input(viewer):
            log_line("denied", reason="not_holder", kind=kind, viewer=viewer,
                     holder=self.lease.holder, origin=origin)
            await self._deny(client, echo, "not_holder")
            return

        if kind == "tap":
            sx, sy = self._point(message)
            button = int(message.get("button") or 1)
            self.inputs.click(sx, sy, button)
            self.lease.note("click", f"bouton {button}", sx, sy, origin=origin)  # pixels de l'écran du PC
            await self._ack(client, echo, True, f"clicked {sx},{sy}")
            await self.bump()
            return

        if kind == "drag":
            fx, fy = self._point({"x": message["from"][0], "y": message["from"][1],
                                  "space": message.get("space")})
            tx, ty = self._point({"x": message["to"][0], "y": message["to"][1],
                                  "space": message.get("space")})
            self.inputs.drag((fx, fy), (tx, ty))
            self.lease.note("drag", f"{fx},{fy} → {tx},{ty}", tx, ty, origin=origin)
            await self._ack(client, echo, True, f"dragged to {tx},{ty}")
            await self.bump()
            return

        if kind == "scroll":
            sx, sy = self._point({"x": message.get("x") or 0, "y": message.get("y") or 0,
                                  "space": message.get("space")})
            amount = int(message.get("amount") or 3)
            self.inputs.scroll(sx, sy, amount)
            self.lease.note("scroll", f"{amount} crans", sx, sy, origin=origin)
            await self._ack(client, echo, True, "scrolled")
            await self.bump()
            return

        # Le pointeur **sans clic** : le doigt déplace le curseur, il ne clique pas. Message
        # **additif** — une app plus ancienne ne l'envoie pas ; une app plus récente que ce pont le
        # verra refusé (`unknown_action:motion`) sans que rien ne casse, car le curseur que l'app
        # dessine bouge de toute façon. On n'annonce pas d'état ici : le pointeur bouge trop souvent
        # pour un rafraîchissement par trame, et l'app borne déjà sa cadence. Juste un accusé.
        if kind == "motion":
            sx, sy = self._point(message)
            self.inputs.move(sx, sy)
            await self._ack(client, echo, True, f"moved {sx},{sy}")
            return

        if kind == "text":
            content = str(message.get("text") or "")
            if not content:
                await self._deny(client, echo, "empty_text")
                return
            self.inputs.type_text(content)
            shown = content if len(content) <= 40 else content[:40] + "…"
            self.lease.note("type", shown, origin=origin)
            await self._ack(client, echo, True, f"typed {len(content)} char(s)")
            await self.bump()
            return

        if kind == "key":
            keys = str(message.get("keys") or "")
            sensitive = keys.lower() in SENSITIVE_KEYS
            if sensitive and not bool(message.get("approved")):
                log_line("denied", reason="needs_approval", kind="key", keys=keys, viewer=viewer,
                         origin=origin)
                await self._deny(client, echo, "needs_approval")
                return
            self.inputs.combo(keys)
            self.lease.note("key", keys, origin=origin)
            await self._ack(client, echo, True, f"pressed {keys}")
            await self.bump()
            return

        if kind == "run":
            await self._run(client, message, viewer, echo, origin)
            return

        await self._deny(client, echo, f"unknown_action:{kind}")

    def _blocked(self, command: str) -> str | None:
        for pattern in DENY_COMMANDS:
            if pattern.search(command):
                return f"refusé par le pont ({pattern.pattern})"
        return None

    async def _run(self, client, message: dict, viewer: str, echo, origin: str = "inconnue") -> None:
        command = str(message.get("command") or "").strip()
        if not command:
            await self._deny(client, echo, "empty_command")
            return
        if not bool(message.get("approved")):
            # Le pont ne demande pas : il refuse, et l'app le dit à l'utilisateur.
            log_line("denied", reason="needs_approval", kind="run", command=command,
                     viewer=viewer, origin=origin)
            await self._deny(client, echo, "needs_approval")
            return
        blocked = self._blocked(command)
        if blocked is not None:
            log_line("denied", reason="blocked", command=command, viewer=viewer, origin=origin)
            await self._deny(client, echo, "blocked")
            return

        self.lease.busy = True
        await self.bump()
        log_line("run_started", command=command, viewer=viewer, origin=origin)
        try:
            process = await asyncio.create_subprocess_shell(
                command, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.STDOUT,
                env={**os.environ, "DISPLAY": self.screen.display_name},
            )
            try:
                out, _ = await asyncio.wait_for(process.communicate(), timeout=self.shell_timeout)
                code = process.returncode
            except asyncio.TimeoutError:
                process.kill()
                out, code = b"", -1
            text = out.decode("utf-8", "replace")[-4000:]
        finally:
            self.lease.busy = False
        log_line("run_finished", command=command, exit=code, origin=origin)
        self.lease.note("run", command, None, None, origin=origin)
        await client.send(json.dumps({"t": "run_result", "id": echo, "ok": code == 0,
                                      "exit": code, "output": text}, ensure_ascii=False))
        await self.bump()

    async def _ack(self, client, echo, ok: bool, detail: str) -> None:
        await client.send(json.dumps({"t": "ack", "id": echo, "ok": ok, "detail": detail}))

    async def _deny(self, client, echo, reason: str) -> None:
        await client.send(json.dumps({"t": "denied", "id": echo, "reason": reason}))


# ----------------------------------------------------------------------------- transport


async def _messages(connection):
    """Les trames texte d'une connexion. La fermeture n'est pas une erreur : on s'arrête, c'est tout."""
    from websockets.exceptions import ConnectionClosed

    try:
        async for raw in connection:
            if isinstance(raw, str):
                yield raw
    except ConnectionClosed:
        return


def _clean_token(candidate: str) -> str:
    """Le jeton tel qu'il arrive, ramené à sa valeur.

    Un jeton se colle : il garde des espaces, un retour à la ligne du terminal, des guillemets,
    parfois le préfixe qu'on vient de lire dans la documentation (`Bearer `, `token=`) ou la
    valeur entière recollée deux fois. Rien de tout cela n'est le jeton ; le refuser pour un
    espace serait une panne inventée par le pont.
    """
    value = (candidate or "").replace("\u200b", "").replace("\ufeff", "").strip().strip('"').strip("'").strip()
    # Un préfixe recollé devant le jeton — et parfois deux fois : l'app en ajoute un, l'utilisateur
    # en avait déjà tapé un. On les enlève tant qu'il en reste quelque chose.
    for _ in range(3):
        for prefix in ("bearer ", "token=", "token ", "jeton=", "jeton "):
            if value.lower().startswith(prefix) and len(value) > len(prefix):
                value = value[len(prefix):].strip().strip('"').strip("'").strip()
                break
        else:
            break
    return value


def arrived_token(request) -> str:
    """Le jeton présenté par le client, quel que soit l'endroit où il l'a mis.

    Trois chemins acceptés (l'app envoie un `Bearer`) : l'en-tête `Authorization`, l'en-tête
    `X-Onyx-Token`, et `?token=` en paramètre.
    """
    headers = getattr(request, "headers", None)
    candidate = ""
    if headers is not None:
        candidate = headers.get("Authorization", "") or ""
        if not candidate.strip():
            candidate = headers.get("X-Onyx-Token", "") or ""
    if not candidate.strip():
        path = getattr(request, "path", "") or ""
        query = path.split("?", 1)[1] if "?" in path else ""
        for part in query.split("&"):
            if part.startswith("token="):
                candidate = unquote(part[len("token="):])
                break
    return _clean_token(candidate)


def token_verdict(request, token: str) -> tuple[str, int]:
    """Le jeton présenté est-il le bon, et sinon **pourquoi** — sans jamais le journaliser.

    Rend `("ok", longueur)` ou un motif que le journal et l'app peuvent citer :
    `missing` (aucun jeton), `len` (bonne valeur mais longueur différente : un collage tronqué ou
    un jeton d'une autre époque), `mismatch` (même longueur, autre valeur).
    """
    arrived = arrived_token(request)
    if not arrived:
        return "missing", 0
    if not secrets.compare_digest(arrived.encode("utf-8", "replace"), token.encode("utf-8", "replace")):
        return ("mismatch" if len(arrived) == len(token) else "len"), len(arrived)
    return "ok", len(arrived)


def main(argv: list[str] | None = None) -> int:
    global LOG_PATH
    parser = argparse.ArgumentParser(description="Pont « Ordinateur » pour l'app Onyx")
    parser.add_argument("--host", default="127.0.0.1", help="adresse d'écoute (locale par défaut)")
    parser.add_argument("--port", type=int, default=8790)
    parser.add_argument("--display", default=os.environ.get("DISPLAY", ":1"))
    parser.add_argument("--fps", type=int, default=6,
                        help="images par seconde au repos")
    parser.add_argument("--turbo-fps", type=int, default=12,
                        help="images par seconde pendant un geste (demandé par l'app)")
    parser.add_argument("--max-fps", type=int, default=24,
                        help="plafond de cadence : un client ne décide pas de brûler le PC")
    parser.add_argument("--quality", type=int, default=7, help="quantificateur JPEG ffmpeg (2–31)")
    parser.add_argument("--max-width", type=int, default=1280, help="largeur maximale de l'image envoyée")
    parser.add_argument("--token-file", default=str(TOKEN_PATH))
    parser.add_argument("--shell-timeout", type=float, default=90.0)
    parser.add_argument("--log", default=str(LOG_PATH))
    parser.add_argument("doctor", nargs="?", help="vérifie l'affichage, ffmpeg et XTest, puis sort")
    args = parser.parse_args(argv)

    LOG_PATH = Path(args.log)

    ffmpeg = shutil.which("ffmpeg") or os.path.expanduser(
        "~/.hermes/tools/ffmpeg-9.0.1-linux-x64/bin/ffmpeg")
    if not Path(ffmpeg).exists():
        print("ffmpeg introuvable", file=sys.stderr)
        return 2

    from websockets.asyncio.server import serve

    screen = Screen(args.display, args.max_width)
    inputs = Input(screen)
    frames = Frames(screen, args.fps, args.quality, ffmpeg, max_fps=args.max_fps)
    token = read_token(Path(args.token_file))
    bridge = Bridge(screen, frames, inputs, token, args.shell_timeout, turbo_fps=args.turbo_fps)
    guard = AuthGuard()

    if args.doctor == "doctor":
        print(json.dumps({
            "display": screen.display_name, "screen": [screen.width, screen.height],
            "view": [screen.view_width, screen.view_height], "ffmpeg": ffmpeg,
            "xtest": True, "token_file": str(args.token_file),
        }, ensure_ascii=False))
        frames.start(asyncio.new_event_loop())
        for _ in range(60):
            if frames.latest:
                break
            time.sleep(0.05)
        frames.stop()
        print("capture ok" if frames.latest else "capture muette", file=sys.stderr)
        return 0 if frames.latest else 3

    async def handler(connection) -> None:
        request = connection.request
        path = request.path.split("?", 1)[0]
        origin = client_origin(request, connection)
        if path != "/ws":
            await connection.close(code=4404, reason="not found")
            return

        wait = guard.blocked_for(origin)
        if wait:
            log_line("token_blocked", origin=origin, path=path, retry_in=wait)
            await connection.close(code=4429, reason="too many attempts")
            return

        verdict, arrived_len = token_verdict(request, bridge.token)
        if verdict != "ok":
            blocked = guard.fail(origin)
            # Le **chemin seul** : une URL ne doit jamais traîner un jeton dans le journal.
            # On journalise la **longueur** reçue et celle attendue : c'est ce qui distingue un
            # jeton tronqué au collage d'un jeton d'une autre époque, sans écrire le secret.
            # (Une empreinte SHA-256, même tronquée, se force hors ligne — 20 caractères de
            # jeton contre 8 caractères hexadécimaux, ce n'est plus un secret.)
            log_line("token_rejected", origin=origin, path=path, reason=verdict,
                     token_len=arrived_len, expected_len=len(bridge.token),
                     attempts_left=(0 if blocked else guard.remaining(origin)))
            reason = "bad-token:" + verdict
            if verdict == "len":
                reason = f"bad-token:len:{arrived_len}:{len(bridge.token)}"
            await connection.close(code=4401, reason=reason)
            return

        guard.succeed(origin)
        connection.origin = origin
        viewer = secrets.token_hex(4)
        bridge.clients.add(connection)
        log_line("client_connected", viewer=viewer, origin=origin, clients=len(bridge.clients))
        event = frames.subscribe()
        try:
            await connection.send(json.dumps(bridge.hello_payload(viewer)))
            await connection.send(json.dumps(bridge.lease.state()))

            async def pump_frames() -> None:
                while True:
                    await event.wait()
                    event.clear()
                    frame = frames.latest
                    if frame is not None:
                        await connection.send(frame)

            async def pump_input() -> None:
                async for raw in _messages(connection):
                    if isinstance(raw, bytes):
                        continue
                    try:
                        message = json.loads(raw)
                    except ValueError:
                        continue
                    if not isinstance(message, dict):
                        continue
                    message.setdefault("viewer", viewer)
                    try:
                        await bridge.handle(connection, message)
                    except Exception as error:  # une action ratée ne coupe pas la session
                        log_line("action_failed", kind=str(message.get("t")), error=repr(error))
                        await connection.send(json.dumps(
                            {"t": "denied", "id": message.get("id"), "reason": "failed"}))

            frame_task = asyncio.create_task(pump_frames())
            input_task = asyncio.create_task(pump_input())
            done, pending = await asyncio.wait({frame_task, input_task},
                                               return_when=asyncio.FIRST_COMPLETED)
            for task in pending:
                task.cancel()
            await asyncio.gather(*done, *pending, return_exceptions=True)
        finally:
            frames.unsubscribe(event)
            bridge.clients.discard(connection)
            bridge.lease.release(viewer, "disconnected")
            # Plus personne ne regarde : la cadence retombe au repos, comme la main revient à
            # l'agent. Une capture montée en cadence pour un geste ne doit pas rester en l'air.
            if not bridge.clients:
                bridge.frames.configure(fps=bridge.base_fps)
            await bridge.bump()
            log_line("client_disconnected", viewer=viewer, origin=origin,
                     clients=len(bridge.clients))

    async def process_request(connection, request):
        path = request.path.split("?", 1)[0]
        if path == "/health":
            return connection.respond(200, json.dumps({"ok": True}) + "\n")
        return None

    async def run() -> None:
        loop = asyncio.get_running_loop()
        frames.start(loop)
        log_line("bridge_listening", host=args.host, port=args.port,
                 display=screen.display_name, screen=[screen.width, screen.height])
        # Ce que le pont attend, sans le dire : la **longueur** suffit à reconnaître un jeton
        # tronqué au collage quand l'app présente le sien (`token_len` d'un `token_rejected`).
        log_line("token_ready", path=str(args.token_file), length=len(bridge.token))
        # **Pas de keepalive WebSocket.** Le pont envoie déjà des images 6 fois par seconde : le
        # flux est le battement de cœur, et une socket morte se voit à l'envoi suivant. Un ping
        # de contrôle ajoute au contraire une façon de tuer une session saine : son pong fait la
        # queue derrière des dizaines de kilo-octets d'image, et un aller-retour de 20 s sur un
        # réseau mobile suffit à faire fermer la session (« keepalive ping timeout », code 1011)
        # alors que le client, lui, va très bien. La preuve est dans le journal : un client qui
        # ne répond pas aux pings est coupé en 40 s, ce qui n'a aucune valeur ici.
        async with serve(handler, args.host, args.port, process_request=process_request,
                         max_size=None, ping_interval=None, ping_timeout=None):
            await asyncio.Future()

    try:
        asyncio.run(run())
    except KeyboardInterrupt:
        pass
    finally:
        frames.stop()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
