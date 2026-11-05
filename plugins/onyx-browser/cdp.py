"""Le seul endroit qui parle à Chromium : capture d'une trame, et rejeu d'un geste.

On passe par le seam que Hermes réserve aux plugins (`SUPERVISOR_REGISTRY.capture`), avec le
`task_id` des outils `browser_*` du tour. Tout le reste du plugin ignore CDP.

Trois choses que ce module fait et que le reste ne doit pas refaire :

- **viser la bonne page** : le superviseur est épinglé au premier onglet, souvent une page vide ;
  on prend la dernière page web réelle et on s'y attache (`Target.attachToTarget`, sessions mises
  en cache) ;
- **poser un viewport de téléphone** avant la frappe (§2.1 du plan), et le **rendre** à la fin ;
- **détecter le formulaire de connexion** au moment de rendre la main — l'hôte et la présence d'un
  champ `type=password` — pour que l'app puisse proposer d'enregistrer, sans jamais deviner.
"""

from __future__ import annotations

import base64
import json
from typing import Any

#: Un Chromium Android crédible : c'est lui que le site sert quand on demande sa version mobile.
MOBILE_UA = (
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/131.0.0.0 Mobile Safari/537.36"
)
MOBILE_WIDTH, MOBILE_HEIGHT, MOBILE_DPR = 390, 844, 2

_KEYS = {
    "Enter": ("Enter", "Enter", 13, "\r"),
    "Backspace": ("Backspace", "Backspace", 8, ""),
    "Tab": ("Tab", "Tab", 9, ""),
    "Escape": ("Escape", "Escape", 27, ""),
}

_SKIP_PREFIXES = ("chrome://", "about:blank", "devtools://", "chrome-error://")


class BrowserUnavailable(RuntimeError):
    """Aucun navigateur d'agent vivant pour ce tour."""


class Controller:
    """Un client CDP pour le navigateur d'un tour, avec ses sessions de page en cache."""

    def __init__(self, task_id: str) -> None:
        self.task_id = task_id
        self._cdp: Any = None
        self._sessions: dict[str, str] = {}

    # -- attache ------------------------------------------------------------------------------

    def _client(self) -> Any:
        from tools.browser_supervisor import SUPERVISOR_REGISTRY

        cdp = self._cdp
        if cdp is None or not getattr(cdp, "is_valid", lambda: False)():
            cdp = self._cdp = SUPERVISOR_REGISTRY.capture(self.task_id, timeout=3)
            self._sessions = {}
        return cdp

    def page(self) -> str:
        """La session CDP de la page que l'agent regarde vraiment."""
        cdp = self._client()
        targets = cdp.call("Target.getTargets", timeout=4)["result"]["targetInfos"]
        pages = [
            t
            for t in targets
            if t.get("type") == "page" and not str(t.get("url", "")).startswith(_SKIP_PREFIXES)
        ]
        if not pages:
            return cdp.page_session_id
        target_id = pages[-1]["targetId"]
        if target_id not in self._sessions:
            reply = cdp.call(
                "Target.attachToTarget", {"targetId": target_id, "flatten": True}, timeout=4
            )
            self._sessions[target_id] = reply["result"]["sessionId"]
        return self._sessions[target_id]

    # -- viewport -----------------------------------------------------------------------------

    def use_phone_viewport(self) -> tuple[int, int]:
        """Pose un viewport de téléphone et rend la taille CSS obtenue."""
        cdp = self._client()
        session = self.page()
        cdp.call(
            "Emulation.setDeviceMetricsOverride",
            {
                "width": MOBILE_WIDTH,
                "height": MOBILE_HEIGHT,
                "deviceScaleFactor": MOBILE_DPR,
                "mobile": True,
            },
            session_id=session,
            timeout=4,
        )
        cdp.call(
            "Emulation.setUserAgentOverride",
            {"userAgent": MOBILE_UA},
            session_id=session,
            timeout=4,
        )
        return MOBILE_WIDTH, MOBILE_HEIGHT

    def clear_viewport(self) -> None:
        cdp = self._client()
        session = self.page()
        cdp.call("Emulation.clearDeviceMetricsOverride", session_id=session, timeout=4)
        cdp.call("Emulation.setUserAgentOverride", {"userAgent": ""}, session_id=session, timeout=4)

    # -- trame --------------------------------------------------------------------------------

    def frame(self, scale: float) -> dict[str, Any] | None:
        """Une capture JPEG de la page, prête pour le fil."""
        cdp = self._client()
        session = self.page()
        metrics = cdp.call("Page.getLayoutMetrics", session_id=session, timeout=4)["result"]
        view = metrics.get("cssVisualViewport") or metrics.get("layoutViewport") or {}
        width = int(view.get("clientWidth", MOBILE_WIDTH))
        height = int(view.get("clientHeight", MOBILE_HEIGHT))
        shot = cdp.call(
            "Page.captureScreenshot",
            {
                "format": "jpeg",
                "quality": 55,
                "optimizeForSpeed": True,
                "clip": {
                    "x": view.get("pageX", 0),
                    "y": view.get("pageY", 0),
                    "width": width,
                    "height": height,
                    "scale": scale,
                },
            },
            session_id=session,
            timeout=6,
        )["result"]
        history = cdp.call("Page.getNavigationHistory", session_id=session, timeout=4)["result"]
        entry = history["entries"][history["currentIndex"]]
        return {
            "data": shot["data"],
            "url": entry.get("url", ""),
            "title": entry.get("title", ""),
            "width": int(width * scale),
            "height": int(height * scale),
            "css": [width, height],
        }

    # -- saisie -------------------------------------------------------------------------------

    def input(self, action: str, frame: dict[str, Any], css: tuple[int, int]) -> bool:
        """Rejoue un geste. Les coordonnées arrivent en fractions de l'image."""
        cdp = self._client()
        session = self.page()
        width, height = css
        x = min(max(float(frame.get("x") or 0.0), 0.0), 1.0) * width
        y = min(max(float(frame.get("y") or 0.0), 0.0), 1.0) * height
        button = {"x": x, "y": y, "button": "left", "clickCount": 1}
        if action == "click":
            cdp.call("Input.dispatchMouseEvent", {"type": "mouseMoved", "x": x, "y": y},
                     session_id=session, timeout=4)
            cdp.call("Input.dispatchMouseEvent", {"type": "mousePressed", **button},
                     session_id=session, timeout=4)
            cdp.call("Input.dispatchMouseEvent", {"type": "mouseReleased", **button},
                     session_id=session, timeout=4)
            return True
        if action == "scroll":
            cdp.call(
                "Input.dispatchMouseEvent",
                {
                    "type": "mouseWheel",
                    "x": x,
                    "y": y,
                    "deltaX": 0,
                    "deltaY": float(frame.get("dy") or 0.0) * height,
                },
                session_id=session,
                timeout=4,
            )
            return True
        if action == "type":
            text = str(frame.get("text") or "")[:2000]
            if not text:
                return False
            cdp.call("Input.insertText", {"text": text}, session_id=session, timeout=4)
            return True
        if action == "key":
            key = _KEYS.get(str(frame.get("key") or ""))
            if key is None:
                return False
            name, code, vk, text = key
            down = {"type": "keyDown", "key": name, "code": code, "windowsVirtualKeyCode": vk}
            if text:
                down["text"] = text
            cdp.call("Input.dispatchKeyEvent", down, session_id=session, timeout=4)
            cdp.call(
                "Input.dispatchKeyEvent",
                {"type": "keyUp", "key": name, "code": code, "windowsVirtualKeyCode": vk},
                session_id=session,
                timeout=4,
            )
            return True
        return False

    # -- formulaire ---------------------------------------------------------------------------

    def login_form_facts(self) -> dict[str, Any]:
        """L'hôte de la page et la présence d'un champ mot de passe — rien de plus."""
        cdp = self._client()
        session = self.page()
        expression = (
            "(() => ({host: location.host,"
            " hasPassword: !!document.querySelector('form input[type=password]'),"
            " url: location.href}))()"
        )
        reply = cdp.call(
            "Runtime.evaluate",
            {"expression": expression, "returnByValue": True},
            session_id=session,
            timeout=4,
        )
        value = ((reply.get("result") or {}).get("result") or {}).get("value")
        if not isinstance(value, dict):
            return {}
        return {
            "host": str(value.get("host") or ""),
            "hasPassword": bool(value.get("hasPassword")),
            "url": str(value.get("url") or ""),
        }


def decode_frame(payload: str) -> bytes:
    """Une trame base64 → octets JPEG (utile aux tests et au débogage)."""
    return base64.b64decode(payload)


def dump_facts(facts: dict[str, Any]) -> str:
    """Les faits d'un formulaire, sans jamais la valeur d'un champ."""
    return json.dumps(facts, ensure_ascii=False, sort_keys=True)
