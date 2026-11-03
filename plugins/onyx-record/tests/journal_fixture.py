"""Journal d'exemple partagé par les tests — la fixture du golden Kotlin.

Il ne s'agit pas d'une invention : c'est le journal de
``core/src/test/java/com/onyx/app/data/skill/SkillDraftGoldenTest.kt`` transposé
dans le format JSON de la spec (``seq``/``at_ms`` ajoutés, ``x_pct``/``y_pct``
calculés depuis les pixels de la fenêtre 1080 x 2400 du test Kotlin).

Le test ``test_draft_golden`` compare la sortie Python au fichier
``facture.md`` livré par l'app ; les autres suites réutilisent cette fixture pour
parler d'un journal réaliste plutôt que d'un objet à trois champs.
"""

from __future__ import annotations

import os
from pathlib import Path
from typing import Optional

# Le dépôt de l'app (référence d'octets). Surchargeable si le dépôt bouge.
APP_ROOT = Path(os.environ.get("ONYX_APP_DIR", "/home/john/Bureau/onyx"))
GOLDEN_PATH = str(APP_ROOT / "core" / "src" / "test" / "resources" / "skill" / "facture.md")
GOLDEN_TITLE = "Créer une facture depuis un client"
SECRET = "mot-de-passe-de-demo"


def golden_journal(
    *,
    title: Optional[str] = GOLDEN_TITLE,
    session_id: str = "rec_01J9Z3K4Q7V8",
    bridge_session: str = "pc-7f21",
) -> dict:
    journal = {
        "version": 1,
        "session_id": session_id,
        "started_at": 1730650000.123,
        "duration_ms": 12000,
        "end_reason": "stopped",
        "screen": {"width_px": 1080, "height_px": 2400, "scale": 1.0},
        "bridge": {"endpoint": "192.168.1.24:8765", "session": bridge_session},
        "events": [
            {
                "seq": 1,
                "at_ms": 0,
                "kind": "navigate",
                "target": {"window": "Factures"},
                "url": "https://factures.example.fr/nouvelle",
            },
            {
                "seq": 2,
                "at_ms": 400,
                "kind": "tap",
                "target": {
                    "label": "Nouvelle facture",
                    "role": "button",
                    "window": "Factures",
                    "x_pct": 540 / 1080,
                    "y_pct": 240 / 2400,
                },
            },
            {"seq": 3, "at_ms": 900, "kind": "wait", "ms": 120},
            {"seq": 4, "at_ms": 1020, "kind": "wait", "ms": 180},
            {
                "seq": 5,
                "at_ms": 1400,
                "kind": "type",
                "target": {"label": "Montant", "role": "text field", "window": "Factures"},
                "text": "1 240,00 €",
                "chars": 10,
            },
            {
                "seq": 6,
                "at_ms": 2000,
                "kind": "tap",
                "target": {"window": "Factures", "x_pct": 300 / 1080, "y_pct": 900 / 2400},
            },
            {
                "seq": 7,
                "at_ms": 2600,
                "kind": "type",
                "target": {"label": "Mot de passe", "role": "secure text field", "window": "Factures"},
                "secret": True,
                "chars": 20,
            },
            {
                "seq": 8,
                "at_ms": 3000,
                "kind": "scroll",
                "target": {"window": "Factures"},
                "direction": "down",
                "amount": 540,
            },
            {
                "seq": 9,
                "at_ms": 3300,
                "kind": "scroll",
                "target": {"window": "Factures"},
                "direction": "down",
                "amount": 220,
            },
            {"seq": 10, "at_ms": 5000, "kind": "wait", "ms": 2500},
            {
                "seq": 11,
                "at_ms": 6000,
                "kind": "key",
                "target": {"window": "Factures"},
                "key": "back",
            },
        ],
    }
    if title is not None:
        journal["title"] = title
    return journal


def spec_journal(session_id: str = "rec_01J9Z3K4Q7V8", events: Optional[list] = None) -> dict:
    """Le journal complet de la spec (§2.3), neuf événements dont un focus."""
    return {
        "version": 1,
        "session_id": session_id,
        "started_at": 1730650000.123,
        "duration_ms": 12000,
        "end_reason": "stopped",
        "screen": {"width_px": 1440, "height_px": 900, "scale": 1.0},
        "bridge": {"endpoint": "192.168.1.24:8765", "session": "pc-7f21"},
        "events": events
        if events is not None
        else [
            {
                "seq": 1,
                "at_ms": 0,
                "kind": "focus",
                "target": {"label": "Finder", "role": "window", "window": "Finder"},
            },
            {
                "seq": 2,
                "at_ms": 1240,
                "kind": "tap",
                "target": {
                    "label": "Nouveau dossier",
                    "role": "button",
                    "window": "Finder",
                    "x_pct": 0.421,
                    "y_pct": 0.183,
                },
            },
            {
                "seq": 3,
                "at_ms": 2600,
                "kind": "type",
                "target": {"label": "Nom", "role": "text field", "window": "Finder"},
                "text": "Rapport 2026",
                "chars": 12,
            },
            {
                "seq": 4,
                "at_ms": 3900,
                "kind": "type",
                "target": {"label": "Mot de passe", "role": "secure text field", "window": "Réglages"},
                "secret": True,
                "chars": 9,
            },
            {
                "seq": 5,
                "at_ms": 5200,
                "kind": "key",
                "target": {"label": "Mot de passe", "role": "secure text field", "window": "Réglages"},
                "key": "enter",
            },
            {
                "seq": 6,
                "at_ms": 6100,
                "kind": "scroll",
                "target": {"label": "Liste des comptes", "role": "list", "window": "Réglages"},
                "direction": "down",
                "amount": 3,
            },
            {
                "seq": 7,
                "at_ms": 7400,
                "kind": "navigate",
                "target": {"label": "Rapport 2026", "role": "link", "window": "Navigateur"},
                "url": "https://exemple.fr/rapport",
            },
            {"seq": 8, "at_ms": 9600, "kind": "wait", "ms": 1800},
            {
                "seq": 9,
                "at_ms": 12000,
                "kind": "tap",
                "target": {"window": "Aperçu", "x_pct": 0.512, "y_pct": 0.077},
            },
        ],
    }
