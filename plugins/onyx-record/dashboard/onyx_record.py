"""onyx-record — core of the "demonstration → skill" server.

This module depends on no Hermes code: it is tested on its own
(``tests/test_record_core.py``, ``tests/test_draft_golden.py``).

Three responsibilities, and nothing else:

1. **validate a journal** of actions according to ``docs/SPEC-SKILLS-RECORD.md`` §2 — a
   journal that does not look like one is refused (``422``), never repaired;
2. **bound it** (§2.4) and return its **canonical form** (§4, D3): unknown
   fields removed, bounds applied, secret text absent;
3. **convert it** into a Markdown draft, with the **same bytes** as the
   Kotlin converter already shipped in the app
   (``core/src/main/java/com/onyx/app/data/skill/SkillDraft.kt``, golden
   ``core/src/test/resources/skill/invoice.md``).

Invariants — never to be broken:

* **no secret goes out**: a ``text`` accompanied by ``secret: true`` is
  refused (``422 secret_text_present``), not cleaned; a secret text is never
  logged, never copied into a response, never kept in memory;
* **no write**: the module opens no file, writes nothing to
  disk, keeps no journal after the draft. Leases and journals in
  collection live in memory, 120 s at most;
* **same input, same byte**: the conversion is pure (no clock, no
  locale, no unfixed dictionary order). This is what makes it possible to
  compare it with the golden.

Deviations from the spec, measured and assumed, are listed in ``ECARTS.md`` at the root
of the plugin — each is also recalled in a comment at the place in the code.
"""

from __future__ import annotations

import copy
import json
import math
import re
import secrets
import threading
import time
import unicodedata
from collections import deque
from dataclasses import dataclass, field
from typing import Any, Callable, Optional

PLUGIN_NAME = "onyx-record"
VERSION = "1.0.0"

# --- bounds (spec §2.4) ---------------------------------------------------
MAX_SECONDS = 600
GRACE_SECONDS = 120
MAX_EVENTS = 2000
MAX_JOURNAL_BYTES = 262144
MAX_TEXT = 2000
MAX_LABEL = 200
MAX_ROLE = 40
MAX_WINDOW = 120
MAX_URL = 2048
MIN_WAIT_MS = 100
MAX_WAIT_MS = 30000
# The spec says "steps, 1 to 100"; the reference converter counts
# pixels (golden: 540 + 220 = 760 px). See ECARTS.md §2.
MAX_SCROLL_AMOUNT = 100000

# --- conversion (mirror of SkillDraft.kt) -----------------------------------
SHORT_WAIT_MS = 500
DEFAULT_TITLE = "Démonstration enregistrée"
EMPTY_TITLE = "Démonstration vide"
SECRET_TEXT = "••••"
MAX_NAME = 64
MAX_TITLE = 120

KINDS = ("focus", "tap", "type", "scroll", "navigate", "wait", "key")
SCROLL_DIRECTIONS = ("up", "down")
KEY_WORDS = {"back": "Back", "home": "Home", "enter": "Enter", "tab": "Tab", "escape": "Escape"}
SCROLL_WORDS = {"up": "up", "down": "down", "left": "left", "right": "right"}

# Fields forbidden at any depth (spec §6): pixels and audio.
FORBIDDEN_KEYS = frozenset(
    {
        "image",
        "image_data",
        "png",
        "jpeg",
        "jpg",
        "gif",
        "screenshot",
        "thumbnail",
        "frame",
        "audio",
        "wav",
        "mp3",
        "microphone",
        "transcript",
    }
)

_STATUS = {
    "bad_request": 400,
    "bad_bridge": 400,
    "profile_not_supported": 400,
    "two_journals": 400,
    "already_recording": 409,
    "journal_pending": 409,
    "not_recording": 409,
    "bridge_mismatch": 409,
    "unknown_session": 409,
    "still_recording": 409,
    "no_journal": 409,
    "session_expired": 410,
    "journal_too_large": 413,
    "bad_journal": 422,
    "version_unsupported": 422,
    "secret_text_present": 422,
    "recorder_unavailable": 503,
}

# Messages in English: they go to the server log, never to the screen
# (the screen displays its own sentence, spec §1 and §8).
_MESSAGES = {
    "bad_request": "Malformed request body.",
    "bad_bridge": "The bridge is missing or its endpoint is not host:port.",
    "profile_not_supported": "Recording does not take a profile.",
    "two_journals": "Send either journal or journal_id, not both.",
    "already_recording": "A recording is already open.",
    "journal_pending": "A demonstration is waiting to be converted.",
    "not_recording": "No recording is open.",
    "bridge_mismatch": "The journal belongs to another bridge session.",
    "unknown_session": "Unknown recording session.",
    "still_recording": "The recording is still open.",
    "no_journal": "Nothing to convert.",
    "session_expired": "The recording session expired more than 120 seconds ago.",
    "journal_too_large": "The journal exceeds 262144 bytes.",
    "bad_journal": "The journal does not match the v1 format.",
    "version_unsupported": "Unsupported journal version.",
    "secret_text_present": "A secret event must not carry text.",
    "recorder_unavailable": "No memory room for a recording on this server.",
}


class RecordError(Exception):
    """Server refusal. ``code`` is the contract key (spec §1)."""

    def __init__(self, code: str, message: Optional[str] = None, **extra: Any) -> None:
        self.code = code
        self.message = message or _MESSAGES.get(code, "Refused.")
        self.extra = extra
        super().__init__(self.message)

    @property
    def status(self) -> int:
        return _STATUS.get(self.code, 400)

    def payload(self) -> dict:
        body: dict[str, Any] = {"code": self.code, "message": self.message}
        body.update(self.extra)
        return body


# ---------------------------------------------------------------------------
# Small conversions, exact replicas of SkillDraft.kt
# ---------------------------------------------------------------------------


def single_line(text: Any) -> str:
    """Any run of whitespace becomes a single space, edges trimmed (Kotlin ``singleLine``)."""
    if not isinstance(text, str):
        return ""
    return " ".join(text.split())


def escape(text: str) -> str:
    """Escape whatever would break a list line (Kotlin ``escape``)."""
    out = []
    for ch in text:
        if ch == "\\":
            out.append("\\\\")
        elif ch == '"':
            out.append('\\"')
        elif ch == "\n":
            out.append("\\n")
        elif ch == "\r":
            out.append("\\r")
        elif ch == "\t":
            out.append("\\t")
        else:
            out.append(ch)
    return "".join(out)


def _percent(fraction: Optional[float]) -> Optional[int]:
    """``x_pct`` (0..1) → integer percentage, halves rounded up, bounded 0..100.

    Kotlin's ``roundToInt`` rounds halves toward +∞ (``Math.round``); Python's
    ``round`` rounds halves to even. Hence ``floor(x + .5)``.
    """
    if fraction is None:
        return None
    try:
        ratio = float(fraction) * 100.0
    except (TypeError, ValueError):
        return None
    if not math.isfinite(ratio):
        return None
    return max(0, min(100, int(math.floor(ratio + 0.5))))


def _duration(ms: int) -> str:
    """"2 s" when the second is whole, "2500 ms" otherwise (Kotlin ``duration``)."""
    if ms >= 1000 and ms % 1000 == 0:
        return f"{ms // 1000} s"
    return f"{ms} ms"


def _intention(count: int) -> str:
    if count == 1:
        return "Reproduire l'étape enregistrée, dans l'ordre où elle a été faite."
    return f"Reproduire les {count} étapes enregistrées, dans l'ordre où elles ont été faites."


def _join_french(numbers: list[int]) -> str:
    if len(numbers) <= 1:
        return "".join(str(n) for n in numbers)
    return ", ".join(str(n) for n in numbers[:-1]) + " et " + str(numbers[-1])


def _draft_title(raw: Any) -> Optional[str]:
    title = single_line(raw)
    title = title.lstrip("# ")
    return title or None


def slugify(text: str) -> str:
    """Folder name: ASCII lowercase, hyphens, ≤ 64 characters."""
    folded = unicodedata.normalize("NFKD", str(text))
    folded = folded.encode("ascii", "ignore").decode("ascii").lower()
    folded = re.sub(r"[^a-z0-9]+", "-", folded).strip("-")
    if len(folded) > MAX_NAME:
        folded = folded[:MAX_NAME].rstrip("-")
    return folded


def _humanize(hint: str) -> str:
    words = [w for w in re.split(r"[^0-9A-Za-zÀ-ÿ]+", str(hint)) if w]
    if not words:
        return ""
    joined = " ".join(words)
    return joined[:1].upper() + joined[1:]


# ---------------------------------------------------------------------------
# One step of the draft
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class DraftStep:
    """A step's line, the reason to review it (spec code), and its text.

    ``review_text`` is the French text that the Kotlin converter attaches to
    the line (" — to review: missing label"); ``reason`` is the closed code of
    the spec (``no_label``…). Both exist because the Markdown must stay
    identical to Kotlin whereas the JSON response speaks in codes.
    """

    line: str
    reason: Optional[str] = None
    review_text: Optional[str] = None

    def markdown(self) -> str:
        if self.review_text is None:
            return self.line
        return f"{self.line} — à relire : {self.review_text}"


# The intermediate actions, after reading the journal: (kind, payload).
def _actions(journal: dict) -> list[tuple[str, dict]]:
    actions: list[tuple[str, dict]] = []
    for event in journal.get("events") or []:
        kind = event["kind"]
        target = event.get("target") or {}
        if kind == "tap":
            actions.append(
                (
                    "tap",
                    {
                        "label": target.get("label"),
                        "x_pct": target.get("x_pct"),
                        "y_pct": target.get("y_pct"),
                    },
                )
            )
        elif kind == "type":
            actions.append(
                (
                    "type",
                    {"text": event.get("text", ""), "secret": bool(event.get("secret"))},
                )
            )
        elif kind == "scroll":
            actions.append(("scroll", {"direction": event["direction"], "amount": int(event["amount"])}))
        elif kind == "navigate":
            actions.append(("navigate", {"url": event.get("url", "")}))
        elif kind == "wait":
            actions.append(("wait", {"ms": int(event["ms"])}))
        elif kind == "key":
            actions.append(("key", {"key": event["key"]}))
        elif kind == "focus":
            actions.append(("focus", {"label": target.get("label"), "window": target.get("window")}))
        else:  # safeguard: validation has already closed the list of kinds
            actions.append(("unsupported", {"kind": kind}))
    return actions


def _merge_waits(actions: list[tuple[str, dict]]) -> list[tuple[str, dict]]:
    """Merge consecutive short waits, discard zero waits (Kotlin)."""
    out: list[tuple[str, dict]] = []
    short_ms = 0
    short_count = 0

    def flush() -> None:
        nonlocal short_ms, short_count
        if short_count > 0:
            out.append(("wait", {"ms": short_ms}))
        short_ms = 0
        short_count = 0

    for kind, payload in actions:
        if kind != "wait":
            flush()
            out.append((kind, payload))
            continue
        ms = max(0, int(payload["ms"]))
        if ms == 0:
            continue
        if ms <= SHORT_WAIT_MS:
            short_ms += ms
            short_count += 1
        else:
            flush()
            out.append(("wait", {"ms": ms}))
    flush()
    return out


def _merge_scrolls(actions: list[tuple[str, dict]]) -> list[tuple[str, dict]]:
    """Accumulate adjacent scrolls in the same direction, discard zero amounts."""
    out: list[tuple[str, dict]] = []
    for kind, payload in actions:
        if kind == "scroll" and abs(int(payload["amount"])) == 0:
            continue
        if kind == "scroll" and out and out[-1][0] == "scroll" and out[-1][1]["direction"] == payload["direction"]:
            total = min(abs(int(out[-1][1]["amount"])) + abs(int(payload["amount"])), 2**31 - 1)
            out[-1] = ("scroll", {"direction": payload["direction"], "amount": total})
        else:
            out.append((kind, payload))
    return out


def _render(kind: str, payload: dict) -> DraftStep:
    if kind == "tap":
        label = single_line(payload.get("label"))
        if label:
            return DraftStep(f'Tap "{escape(label)}"')
        x = _percent(payload.get("x_pct"))
        y = _percent(payload.get("y_pct"))
        if x is None or y is None:
            # Same fallback as Kotlin: no percentage would be honest.
            return DraftStep(
                "Tap at an unknown position",
                reason="no_window",
                review_text="fenêtre de taille inconnue",
            )
        return DraftStep(
            f"Tap at {x}% / {y}% of the window",
            reason="no_label",
            review_text="libellé absent",
        )
    if kind == "type":
        if payload["secret"]:
            # Four bullets, always four: the length is already information.
            return DraftStep(f"Type {SECRET_TEXT} — secret, non enregistré", reason="secret_field")
        text = payload.get("text") or ""
        if not text:
            # ``no_label`` code: the spec's list has no ``empty_text``.
            return DraftStep('Type ""', reason="no_label", review_text="texte vide")
        return DraftStep(f'Type "{escape(text)}"')
    if kind == "scroll":
        direction = SCROLL_WORDS.get(payload["direction"], str(payload["direction"]))
        return DraftStep(f"Scroll {direction} {abs(int(payload['amount']))} px")
    if kind == "navigate":
        url = single_line(payload.get("url"))
        if not url:
            return DraftStep("Navigate to an unknown address", reason="no_label", review_text="adresse absente")
        return DraftStep(f"Navigate to {url}")
    if kind == "wait":
        return DraftStep(f"Wait {_duration(int(payload['ms']))}")
    if kind == "key":
        return DraftStep(f"Press {KEY_WORDS.get(payload['key'], payload['key'])}")
    if kind == "focus":
        label = single_line(payload.get("label")) or single_line(payload.get("window"))
        if not label:
            return DraftStep(
                "Focus at an unknown window", reason="no_window", review_text="fenêtre inconnue"
            )
        return DraftStep(f'Focus "{escape(label)}"')
    # ``unsupported_kind`` from the spec: the only way not to lose a step.
    return DraftStep(
        f"{payload.get('kind')} (unsupported)",
        reason="unsupported_kind",
        review_text="action non prise en charge",
    )


def draft_steps(journal: dict) -> list[DraftStep]:
    merged = _merge_scrolls(_merge_waits(_actions(journal)))
    return [_render(kind, payload) for kind, payload in merged]


def derive_name(journal: dict, name_hint: Optional[str] = None) -> str:
    """Folder name of the skill (``ServeSkillsApi.create`` will write it).

    ``name_hint`` first, then the journal title, then "first focus +
    first named target" (spec §5). An empty name is not a name: we
    fall back on a stable default.
    """
    if isinstance(name_hint, str) and name_hint.strip():
        slug = slugify(name_hint)
        if slug:
            return slug
    title = _draft_title(journal.get("title"))
    if title:
        slug = slugify(title)
        if slug:
            return slug
    focus_label = ""
    first_named = ""
    for event in journal.get("events") or []:
        target = event.get("target") or {}
        label = single_line(target.get("label"))
        if not focus_label and event.get("kind") == "focus":
            focus_label = label or single_line(target.get("window"))
        if not first_named and event.get("kind") != "focus" and label:
            first_named = label
        if focus_label and first_named:
            break
    slug = slugify(" ".join(part for part in (focus_label, first_named) if part))
    return slug or "demonstration-enregistree"


def draft(journal: dict, name_hint: Optional[str] = None) -> dict:
    """The draft: stable Markdown, steps to review, warnings.

    **Pure** function: no state, no clock, no write. Two calls
    with the same journal return the same byte (spec §5, D4).
    """
    steps = draft_steps(journal)
    title = _draft_title(journal.get("title"))
    if title is None:
        title = _humanize(name_hint) if isinstance(name_hint, str) and name_hint.strip() else ""
        if not title:
            title = EMPTY_TITLE if not steps else DEFAULT_TITLE
    title = title[:MAX_TITLE]

    lines: list[str] = [f"# {title}", ""]
    step_line: dict[int, int] = {}
    if not steps:
        lines.extend(
            [
                "Aucune action n'a été enregistrée : il n'y a rien à rejouer, donc rien à "
                "transformer en compétence.",
                "",
                "_Aucune étape._",
            ]
        )
    else:
        lines.append(_intention(len(steps)))
        lines.append("")
        for number, step in enumerate(steps, 1):
            step_line[number] = len(lines) + 1
            lines.append(f"{number}. {step.markdown()}")
        numbers = [n for n, s in enumerate(steps, 1) if s.review_text is not None]
        if numbers:
            listed = f"l'étape {numbers[0]}" if len(numbers) == 1 else f"les étapes {_join_french(numbers)}"
            lines.extend(["", f"> À relire : {listed}."])
    markdown = "".join(line + "\n" for line in lines)

    review = [
        {"step": number, "reason": step.reason, "line": step_line[number]}
        for number, step in enumerate(steps, 1)
        if step.reason is not None
    ]
    return {
        "name": derive_name(journal, name_hint),
        "title": title,
        "steps": len(steps),
        "markdown": markdown,
        "review": review,
    }


# ---------------------------------------------------------------------------
# Journal validation — spec §2, refusals §4
# ---------------------------------------------------------------------------


def serialized_size(value: Any) -> int:
    """UTF-8 size of the serialized form, as the response writes it.

    Starlette's ``JSONResponse`` serializes with ``ensure_ascii=False`` and compact
    separators: that is exactly what ``bytes`` measures.
    """
    return len(
        json.dumps(value, ensure_ascii=False, allow_nan=False, separators=(",", ":")).encode("utf-8")
    )


def _is_int(value: Any) -> bool:
    return isinstance(value, int) and not isinstance(value, bool)


def _is_number(value: Any) -> bool:
    return (isinstance(value, int) and not isinstance(value, bool)) or isinstance(value, float)


def _bad(message: Optional[str] = None) -> "RecordError":
    return RecordError("bad_journal", message)


def _scan_forbidden(value: Any, depth: int = 0) -> None:
    """Refuse pixels and audio at any depth (spec §6)."""
    if depth > 8:
        raise _bad("Journal nested too deeply.")
    if isinstance(value, dict):
        for key, item in value.items():
            if isinstance(key, str) and key.lower() in FORBIDDEN_KEYS:
                raise _bad(f"Field {key!r} is not allowed in a journal.")
            _scan_forbidden(item, depth + 1)
    elif isinstance(value, list):
        for item in value:
            _scan_forbidden(item, depth + 1)


def _bridge(value: Any) -> tuple[str, str]:
    """``bridge.endpoint`` is a bare host:port — no scheme, no identifier."""
    if not isinstance(value, dict):
        raise RecordError("bad_bridge")
    endpoint = value.get("endpoint")
    session = value.get("session")
    if not isinstance(endpoint, str) or not endpoint.strip():
        raise RecordError("bad_bridge")
    endpoint = endpoint.strip()
    if "://" in endpoint or any(c in endpoint for c in "/?#") or "@" in endpoint:
        raise RecordError("bad_bridge")
    if any(c.isspace() for c in endpoint):
        raise RecordError("bad_bridge")
    if not isinstance(session, str) or not session.strip():
        raise RecordError("bad_bridge")
    if len(endpoint) > 255 or len(session) > 200:
        raise RecordError("bad_bridge")
    return endpoint, session.strip()


def _target(value: Any, kind: str, warnings: dict) -> dict:
    if not isinstance(value, dict):
        raise _bad(f"Event {kind} has no target.")
    out: dict[str, Any] = {}
    label = value.get("label")
    if label is not None:
        if not isinstance(label, str):
            raise _bad("target.label must be a string.")
        if len(label) > MAX_LABEL:
            label = label[:MAX_LABEL]
            warnings["label_truncated"] = True
        out["label"] = label
    role = value.get("role")
    if role is not None:
        if not isinstance(role, str):
            raise _bad("target.role must be a string.")
        if len(role) > MAX_ROLE:
            role = role[:MAX_ROLE]
            warnings["label_truncated"] = True
        out["role"] = role
    window = value.get("window")
    if window is not None:
        if not isinstance(window, str):
            raise _bad("target.window must be a string.")
        if len(window) > MAX_WINDOW:
            window = window[:MAX_WINDOW]
            warnings["label_truncated"] = True
        out["window"] = window
    if kind == "tap":
        # "x_pct / y_pct … only for tap" (spec §2.2): optional, but
        # then a step with no label would have no honest position — the
        # converter says so ("Tap at an unknown position", to review) instead
        # of refusing the whole recording.
        for axis in ("x_pct", "y_pct"):
            raw = value.get(axis)
            if raw is None:
                continue
            if not _is_number(raw) or not math.isfinite(float(raw)):
                raise _bad(f"tap.{axis} must be a number.")
            out[axis] = min(1.0, max(0.0, float(raw)))
    return out


def _event(raw: Any, previous: Optional[dict], warnings: dict) -> dict:
    if not isinstance(raw, dict):
        raise _bad("Each event must be an object.")
    seq = raw.get("seq")
    if not _is_int(seq) or seq < 1:
        raise _bad("Event seq must be an integer >= 1.")
    at_ms = raw.get("at_ms")
    if not _is_int(at_ms) or at_ms < 0:
        raise _bad("Event at_ms must be an integer >= 0.")
    if previous is not None:
        if seq <= previous["seq"]:
            raise _bad("Event seq must be strictly increasing.")
        if at_ms < previous["at_ms"]:
            raise _bad("Event at_ms must not go backwards.")
    kind = raw.get("kind")
    if not isinstance(kind, str) or kind not in KINDS:
        # Spec §5 provides unsupported_kind; §2.2 closes the list of kinds and
        # §4 requires 422 bad_journal. The refusal wins: see ECARTS.md §3.
        raise _bad(f"Unknown kind {kind!r}.")

    out: dict[str, Any] = {"seq": seq, "at_ms": at_ms, "kind": kind}
    if kind != "wait":
        out["target"] = _target(raw.get("target"), kind, warnings)

    if kind == "type":
        secret = raw.get("secret", False)
        if not isinstance(secret, bool):
            raise _bad("type.secret must be a boolean.")
        text_present = raw.get("text") is not None
        if secret and text_present:
            # Refused, never cleaned: the server has no business deciding to discard a
            # text that was sent to it. The body of the 422 does not copy it.
            raise RecordError("secret_text_present")
        if not secret:
            text = raw.get("text")
            if not isinstance(text, str):
                raise _bad("A non-secret type needs a text.")
            if len(text) > MAX_TEXT:
                raise _bad("type.text exceeds 2000 characters.")
            out["text"] = text
            chars = raw.get("chars")
            out["chars"] = chars if _is_int(chars) and chars >= 0 else len(text)
        else:
            chars = raw.get("chars")
            if not _is_int(chars) or chars < 0:
                raise _bad("A secret type needs chars.")
            out["secret"] = True
            out["chars"] = chars
        if raw.get("from_clipboard") is True and text_present:
            raise _bad("Clipboard content must be typed as secret.")
    elif kind == "scroll":
        direction = raw.get("direction")
        if direction not in SCROLL_DIRECTIONS:
            raise _bad("scroll.direction must be up or down.")
        amount = raw.get("amount")
        if not _is_int(amount) or abs(amount) > MAX_SCROLL_AMOUNT:
            raise _bad("scroll.amount must be an integer.")
        out["direction"] = direction
        out["amount"] = abs(amount)
    elif kind == "navigate":
        url = raw.get("url")
        if not isinstance(url, str) or not url:
            raise _bad("navigate needs a url.")
        scheme = url.split(":", 1)[0].lower() if ":" in url else ""
        if scheme not in ("http", "https") or "//" not in url[:12]:
            # file:, onyx:, content: …: refused, not truncated (spec §6).
            raise _bad("navigate.url must be http or https.")
        if len(url) > MAX_URL:
            warnings["url_dropped"] = True
            url = ""
        out["url"] = url
    elif kind == "wait":
        ms = raw.get("ms")
        if not _is_int(ms):
            raise _bad("wait.ms must be an integer.")
        if ms <= 0:
            out["ms"] = 0
        elif ms < MIN_WAIT_MS:
            out["ms"] = MIN_WAIT_MS
            warnings["wait_clamped"] = True
        elif ms > MAX_WAIT_MS:
            out["ms"] = MAX_WAIT_MS
            warnings["wait_clamped"] = True
        else:
            out["ms"] = ms
    elif kind == "key":
        key = raw.get("key")
        if key not in KEY_WORDS:
            raise _bad("key must be back, home, enter, tab or escape.")
        out["key"] = key
    return out


@dataclass
class Validation:
    journal: dict
    warnings: dict = field(default_factory=dict)
    truncated: bool = False
    dropped: int = 0

    @property
    def actions(self) -> int:
        return len(self.journal["events"])

    @property
    def size(self) -> int:
        return serialized_size(self.journal)


def validate(
    journal: Any,
    *,
    session_id: Optional[str] = None,
    duration_ms: Optional[int] = None,
    end_reason: Optional[str] = None,
) -> Validation:
    """Validate, bound and return the journal's canonical form (spec §2 and §4).

    Raises ``RecordError`` with the contract code. ``duration_ms`` and
    ``end_reason`` are the **server**'s when it knows them (D3); otherwise
    the client's are used.
    """
    if not isinstance(journal, dict):
        raise _bad("A journal must be an object.")
    _scan_forbidden(journal)

    version = journal.get("version")
    if version != 1 or isinstance(version, bool):
        if "version" in journal:
            raise RecordError("version_unsupported")
        raise _bad("version is required.")

    journal_session = journal.get("session_id")
    if not isinstance(journal_session, str) or not journal_session:
        raise _bad("session_id is required.")
    if session_id is not None and journal_session != session_id:
        raise _bad("journal.session_id is not the recording session.")

    started_at = journal.get("started_at")
    if not _is_number(started_at) or not math.isfinite(float(started_at)):
        raise _bad("started_at is required.")

    client_duration = journal.get("duration_ms")
    if not _is_int(client_duration) or client_duration < 0:
        raise _bad("duration_ms is required.")

    client_reason = journal.get("end_reason")
    if client_reason not in ("stopped", "expired"):
        raise _bad("end_reason must be stopped or expired.")

    screen = journal.get("screen")
    if not isinstance(screen, dict):
        raise _bad("screen is required.")
    width = screen.get("width_px")
    height = screen.get("height_px")
    scale = screen.get("scale")
    if not _is_int(width) or width <= 0 or not _is_int(height) or height <= 0:
        raise _bad("screen dimensions must be positive integers.")
    if not _is_number(scale) or not math.isfinite(float(scale)) or float(scale) <= 0:
        raise _bad("screen.scale must be positive.")

    endpoint, bridge_session = _bridge(journal.get("bridge"))

    raw_events = journal.get("events")
    if not isinstance(raw_events, list):
        raise _bad("events must be a list.")

    warnings: dict = {}
    dropped = 0
    if len(raw_events) > MAX_EVENTS:
        # The oldest are abandoned (spec §2.4): we keep the end.
        dropped += len(raw_events) - MAX_EVENTS
        raw_events = raw_events[-MAX_EVENTS:]

    events: list[dict] = []
    previous: Optional[dict] = None
    for raw in raw_events:
        event = _event(raw, previous, warnings)
        previous = event
        events.append(event)

    canonical: dict[str, Any] = {
        "version": 1,
        "session_id": journal_session,
        # ``started_at`` remains the phone's: it anchors the at_ms.
        "started_at": round(float(started_at), 3),
        "duration_ms": min(
            MAX_SECONDS * 1000,
            int(duration_ms if duration_ms is not None else client_duration),
        ),
        "end_reason": end_reason if end_reason in ("stopped", "expired") else client_reason,
        "screen": {
            "width_px": width,
            "height_px": height,
            "scale": float(scale),
        },
        "bridge": {"endpoint": endpoint, "session": bridge_session},
        "events": events,
    }
    title = journal.get("title")
    if isinstance(title, str) and title.strip():
        # Additive field: the reference converter depends on it (ActionJournal.title).
        canonical["title"] = single_line(title)[:MAX_TITLE]

    result = Validation(journal=canonical, warnings=warnings, dropped=dropped)

    # Serialized-size bound: same rules as the number of events.
    while result.size > MAX_JOURNAL_BYTES and canonical["events"]:
        del canonical["events"][0]
        result.dropped += 1
        result.truncated = True
    if result.dropped:
        result.truncated = True
        result.warnings["events_dropped"] = True
    return result


# ---------------------------------------------------------------------------
# Leases and journals in collection — memory only, never a file
# ---------------------------------------------------------------------------


@dataclass
class Lease:
    session_id: str
    endpoint: str
    bridge_session: str
    started_at: float
    deadline_at: float

    def expired(self, now: float) -> bool:
        return now > self.deadline_at

    def collectable(self, now: float) -> bool:
        return now <= self.deadline_at + GRACE_SECONDS


@dataclass
class Pending:
    session_id: str
    payload: dict
    expires_at: float


def new_session_id() -> str:
    return "rec_" + secrets.token_hex(6).upper()


class RecordStore:
    """At most one open lease, at most one journal in collection — per server.

    The profile is implicit: the Dashboard serves one profile, and the route refuses an
    explicit ``profile`` (spec §3). "A recording already in progress for this
    profile" therefore reads "a lease is open".
    """

    def __init__(self, clock: Callable[[], float] = time.time) -> None:
        self.clock = clock
        self._lock = threading.RLock()
        self._lease: Optional[Lease] = None
        self._pending: Optional[Pending] = None
        # The identifiers of the swept leases: ``stop`` must still answer 410,
        # not "unknown" (spec §4, case 2).
        self._expired_ids: deque[str] = deque(maxlen=8)

    # -- read ---------------------------------------------------------
    def lease(self) -> Optional[Lease]:
        with self._lock:
            return self._lease

    def pending(self) -> Optional[Pending]:
        with self._lock:
            return self._pending

    def health(self) -> dict:
        with self._lock:
            now = self.clock()
            self._sweep(now)
            lease = self._lease
            return {
                "recording": lease is not None,
                "remaining_seconds": (
                    max(0, int(round(lease.deadline_at - now))) if lease is not None else 0
                ),
                "journal_pending": self._pending is not None,
                "max_seconds": MAX_SECONDS,
                "grace_seconds": GRACE_SECONDS,
                "max_events": MAX_EVENTS,
                "max_journal_bytes": MAX_JOURNAL_BYTES,
            }

    def reset(self) -> None:
        """Clear the in-memory state. Reserved for tests (no route exposes it)."""
        with self._lock:
            self._lease = None
            self._pending = None
            self._expired_ids.clear()

    def _sweep(self, now: float) -> None:
        if self._lease is not None and not self._lease.collectable(now):
            self._expired_ids.append(self._lease.session_id)
            self._lease = None
        if self._pending is not None and now > self._pending.expires_at:
            # The collection window is over: ``stop`` must answer 410, not
            # "unknown" — the server did know this lease.
            self._expired_ids.append(self._pending.session_id)
            self._pending = None

    # -- start -----------------------------------------------------------
    def start(self, body: dict) -> dict:
        if "profile" in body:
            # Refused, not ignored: creating a skill does not take a profile
            # (ServeSkillsApi.create), so promising a layout would be false.
            raise RecordError("profile_not_supported")
        endpoint, bridge_session = _bridge(body.get("bridge"))
        discard = body.get("discard_pending", False)
        if not isinstance(discard, bool):
            raise RecordError("bad_request", "discard_pending must be a boolean.")

        with self._lock:
            now = self.clock()
            self._sweep(now)
            if self._lease is not None:
                lease = self._lease
                raise RecordError(
                    "already_recording",
                    session_id=lease.session_id,
                    deadline_at=round(lease.deadline_at, 3),
                )
            if self._pending is not None and not discard:
                raise RecordError(
                    "journal_pending",
                    session_id=self._pending.session_id,
                    expires_at=round(self._pending.expires_at, 3),
                )
            if discard:
                self._pending = None
            session_id = new_session_id()
            started_at = round(now, 3)
            self._lease = Lease(
                session_id=session_id,
                endpoint=endpoint,
                bridge_session=bridge_session,
                started_at=started_at,
                deadline_at=started_at + MAX_SECONDS,
            )
            return {
                "session_id": session_id,
                "started_at": started_at,
                "deadline_at": round(started_at + MAX_SECONDS, 3),
                "max_seconds": MAX_SECONDS,
                "grace_seconds": GRACE_SECONDS,
            }

    # -- stop ------------------------------------------------------------
    def stop(self, body: dict) -> dict:
        session_id = body.get("session_id")
        if not isinstance(session_id, str) or not session_id.strip():
            raise RecordError("bad_request", "session_id is required.")
        session_id = session_id.strip()
        journal = body.get("journal")

        with self._lock:
            now = self.clock()
            pending = self._pending
            if pending is not None and pending.session_id == session_id:
                if now > pending.expires_at:
                    self._expired_ids.append(session_id)
                    self._pending = None
                    raise RecordError("session_expired")
                # Idempotent within the window: same journal, same bytes.
                return copy.deepcopy(pending.payload)

            lease = self._lease
            if lease is None or lease.session_id != session_id:
                if session_id in self._expired_ids:
                    raise RecordError("session_expired")
                raise RecordError("unknown_session" if (lease or self._pending) else "not_recording")

            if not lease.collectable(now):
                self._lease = None
                self._expired_ids.append(session_id)
                raise RecordError("session_expired")

            if journal is None:
                # The only cancellation path (spec §4): nothing kept, nothing returned.
                self._lease = None
                return {
                    "session_id": session_id,
                    "stopped_at": round(now, 3),
                    "duration_ms": min(MAX_SECONDS * 1000, max(0, int(round((now - lease.started_at) * 1000)))),
                    "actions": 0,
                    "bytes": 0,
                    "truncated": False,
                    "dropped": 0,
                    "end_reason": "discarded",
                }

            if not isinstance(journal, dict):
                raise _bad("journal must be an object.")
            if serialized_size(journal) > MAX_JOURNAL_BYTES:
                # Before bounding: an oversized journal is not a journal to
                # truncate, it is a send that should never have gone out (413).
                raise RecordError("journal_too_large")
            bridge = journal.get("bridge")
            if not isinstance(bridge, dict):
                raise _bad("bridge is required.")
            if bridge.get("session") != lease.bridge_session:
                raise RecordError("bridge_mismatch")

            expired = lease.expired(now)
            end_reason = "expired" if expired else "stopped"
            duration = min(
                MAX_SECONDS * 1000, max(0, int(round((now - lease.started_at) * 1000)))
            )
            # A refused journal leaves the lease open: a send error must
            # not cost the recording (see ECARTS.md §5).
            result = validate(
                journal,
                session_id=session_id,
                duration_ms=duration,
                end_reason=end_reason,
            )
            if expired:
                # The ceiling does not discard the recording, it discards what
                # arrived after it (spec §4, case 1) — and the count.
                kept = [e for e in result.journal["events"] if e["at_ms"] <= MAX_SECONDS * 1000]
                dropped = len(result.journal["events"]) - len(kept)
                if dropped:
                    result.journal["events"] = kept
                    result.dropped += dropped
                    result.truncated = True
                    result.warnings["events_dropped"] = True

            payload = {
                "session_id": session_id,
                "stopped_at": round(now, 3),
                "duration_ms": duration,
                "actions": result.actions,
                "bytes": result.size,
                "truncated": result.truncated,
                "dropped": result.dropped,
                "end_reason": end_reason,
                "journal": result.journal,
                "warnings": list(result.warnings),
            }
            self._lease = None
            self._pending = Pending(
                session_id=session_id,
                payload=copy.deepcopy(payload),
                expires_at=round(now + GRACE_SECONDS, 3),
            )
            return payload

    # -- resolution for skills.draft -----------------------------------
    def journal_for_draft(self, session_id: str) -> dict:
        with self._lock:
            now = self.clock()
            lease = self._lease
            if lease is not None and lease.session_id == session_id:
                raise RecordError("still_recording", session_id=session_id)
            pending = self._pending
            if pending is not None and pending.session_id == session_id:
                if now <= pending.expires_at:
                    return copy.deepcopy(pending.payload["journal"])
                self._expired_ids.append(session_id)
                self._pending = None
            raise RecordError("no_journal", session_id=session_id)


_STORE: Optional[RecordStore] = None
_STORE_LOCK = threading.Lock()


def get_store() -> RecordStore:
    global _STORE
    with _STORE_LOCK:
        if _STORE is None:
            _STORE = RecordStore()
        return _STORE


def reset_store() -> None:
    """Clear the singleton (tests, and nothing else)."""
    get_store().reset()
