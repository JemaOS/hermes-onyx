"""The converter: same bytes as the Kotlin shipped in the app.

The golden ``core/src/test/resources/skill/invoice.md`` is the byte reference
(the batch statement): this file reads it as-is and compares. No expected
Markdown is copied here — if it were, the test would compare two copies of the
same error.

Run from the plugin root:
    python -m unittest discover -s tests -t tests -v
"""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import journal_fixture as fixture  # noqa: E402
import support  # noqa: E402

core = support.load_core()


class TestGoldenKotlin(unittest.TestCase):
    """The Python output must be the app's file, byte for byte."""

    def test_markdown_equals_the_app_golden(self):
        golden = Path(fixture.GOLDEN_PATH)
        self.assertTrue(golden.is_file(), f"golden absent : {golden}")
        expected = golden.read_bytes()

        produced = core.draft(fixture.golden_journal())["markdown"].encode("utf-8")
        self.assertEqual(produced, expected, "le brouillon Python diverge du golden Kotlin")
        print(f"\n[golden] {len(produced)} octets, identiques à {golden.name}")

    def test_route_shape_gives_the_same_bytes(self):
        """The real path (validation then conversion) does not change a byte."""
        validated = core.validate(fixture.golden_journal())
        produced = core.draft(validated.journal)["markdown"].encode("utf-8")
        self.assertEqual(produced, Path(fixture.GOLDEN_PATH).read_bytes())

    def test_the_app_secret_is_nowhere(self):
        draft = core.draft(fixture.golden_journal())
        self.assertNotIn(fixture.SECRET, draft["markdown"])
        self.assertNotIn(fixture.SECRET, str(draft))
        # Four bullets, always four: the length is not stated.
        self.assertIn(core.SECRET_TEXT, draft["markdown"])
        self.assertNotIn("••••••", draft["markdown"])

    def test_counts_and_review(self):
        draft = core.draft(fixture.golden_journal())
        self.assertEqual(draft["steps"], 9)
        self.assertEqual(draft["title"], fixture.GOLDEN_TITLE)
        # The secret step is "to review" in the spec's sense (secret_field): its
        # line already carries "masked field, not recorded", so the
        # Markdown's "> To review" note does not cite it — it is the Kotlin's.
        self.assertEqual(
            draft["review"],
            [
                {"step": 5, "reason": "no_label", "line": 9},
                {"step": 6, "reason": "secret_field", "line": 10},
            ],
        )
        self.assertEqual(draft["name"], "creer-une-facture-depuis-un-client")

    def test_two_calls_give_the_same_byte(self):
        first = core.draft(fixture.golden_journal())
        second = core.draft(fixture.golden_journal())
        self.assertEqual(first, second)
        self.assertIsNot(first["markdown"], second["markdown"])

    def test_merge_rules(self):
        draft = core.draft(fixture.golden_journal())
        markdown = draft["markdown"]
        # 120 + 180 ms -> a single 300 ms step.
        self.assertIn("3. Wait 300 ms", markdown)
        self.assertNotIn("120 ms", markdown)
        # 540 + 220 px -> a single 760 px scroll.
        self.assertIn("7. Scroll down 760 px", markdown)
        self.assertNotIn("540", markdown)

    def test_forty_events_are_stable(self):
        """The spec's §12.1 proof: 40 events, a stable markdown."""
        events = []
        for index in range(40):
            events.append(
                {
                    "seq": index + 1,
                    "at_ms": index * 250,
                    "kind": "tap",
                    "target": {
                        "label": f"Bouton {index % 7}",
                        "role": "button",
                        "window": "Fenêtre",
                        "x_pct": (index % 10) / 10,
                        "y_pct": (index % 5) / 5,
                    },
                }
            )
        journal = fixture.spec_journal(events=events)
        first = core.draft(journal)["markdown"]
        second = core.draft(journal)["markdown"]
        self.assertEqual(first, second)
        self.assertIn("Reproduire les 40 étapes", first)
        self.assertGreater(len(first), 500)

    def test_empty_journal_says_so(self):
        """An empty journal says so — it does not fabricate an empty skill (rule 5)."""
        journal = fixture.spec_journal(events=[])
        journal.pop("title", None)
        draft = core.draft(journal)
        self.assertEqual(draft["title"], core.EMPTY_TITLE)
        self.assertEqual(draft["steps"], 0)
        self.assertEqual(draft["review"], [])
        self.assertEqual(
            draft["markdown"],
            "# Démonstration vide\n\n"
            "Aucune action n'a été enregistrée : il n'y a rien à rejouer, donc rien à "
            "transformer en compétence.\n\n"
            "_Aucune étape._\n",
        )

    def test_single_step_uses_the_singular(self):
        journal = fixture.spec_journal(events=[{"seq": 1, "at_ms": 0, "kind": "wait", "ms": 2000}])
        draft = core.draft(journal)
        self.assertIn("Reproduire l'étape enregistrée", draft["markdown"])
        self.assertIn("1. Wait 2 s", draft["markdown"])

    def test_long_and_short_waits_switch_units(self):
        journal = fixture.spec_journal(
            events=[
                {"seq": 1, "at_ms": 0, "kind": "wait", "ms": 2500},
                {"seq": 2, "at_ms": 2600, "kind": "wait", "ms": 1000},
            ]
        )
        markdown = core.draft(journal)["markdown"]
        self.assertIn("1. Wait 2500 ms", markdown)
        self.assertIn("2. Wait 1 s", markdown)

    def test_tap_without_position_is_marked_for_review(self):
        """Neither label nor position: the step exists, and it says it must be reviewed."""
        journal = fixture.spec_journal(
            events=[{"seq": 1, "at_ms": 0, "kind": "tap", "target": {"window": "F"}}]
        )
        draft = core.draft(journal)
        self.assertIn(
            "1. Tap at an unknown position — à relire : fenêtre de taille inconnue",
            draft["markdown"],
        )
        self.assertEqual(draft["review"], [{"step": 1, "reason": "no_window", "line": 5}])

    def test_name_hint_wins_and_is_a_folder_name(self):
        draft = core.draft(fixture.golden_journal(), "Créer un dossier — Finder!")
        self.assertEqual(draft["name"], "creer-un-dossier-finder")
        long_hint = "a" * 90
        self.assertLessEqual(len(core.draft(fixture.golden_journal(), long_hint)["name"]), 64)

    def test_language_is_english_like_the_converter(self):
        """The Markdown stays the Kotlin's: the steps are in English, verb + target."""
        markdown = core.draft(fixture.spec_journal())["markdown"]
        self.assertIn('1. Focus "Finder"', markdown)
        self.assertIn('2. Tap "Nouveau dossier"', markdown)
        self.assertIn("5. Press Enter", markdown)
        # The prose (title, intent, review note) is French, like the Kotlin.
        self.assertIn("Reproduire les 9 étapes", markdown)
        self.assertIn("> À relire : l'étape 9.", markdown)

    def test_percent_rounds_half_up_and_is_clamped(self):
        """Kotlin's ``roundToInt`` rounds halves toward +∞, not to even."""
        journal = fixture.spec_journal(
            events=[
                {
                    "seq": 1,
                    "at_ms": 0,
                    "kind": "tap",
                    "target": {"window": "F", "x_pct": 0.625, "y_pct": 1.4},
                }
            ]
        )
        markdown = core.draft(journal)["markdown"]
        # 0.625 -> 62.5 %: Kotlin says 63 (half toward +∞), Python's round() would say 62.
        self.assertIn("Tap at 63% / 100% of the window", markdown)

    def test_escaping_keeps_one_line(self):
        journal = fixture.spec_journal(
            events=[
                {
                    "seq": 1,
                    "at_ms": 0,
                    "kind": "tap",
                    "target": {"label": 'Deux "lignes"\nici', "window": "F", "x_pct": 0.1, "y_pct": 0.1},
                },
                {
                    "seq": 2,
                    "at_ms": 100,
                    "kind": "type",
                    "target": {"window": "F"},
                    "text": "ligne 1\nligne 2\\fin",
                    "chars": 18,
                },
            ]
        )
        markdown = core.draft(journal)["markdown"]
        # The label is reduced to one line BEFORE escaping, like the Kotlin.
        self.assertIn('1. Tap "Deux \\"lignes\\" ici"', markdown)
        self.assertIn('2. Type "ligne 1\\nligne 2\\\\fin"', markdown)
        # Two steps -> at most four list lines: no blank line in the middle.
        self.assertEqual(markdown.count("\n\n"), 2)


if __name__ == "__main__":
    unittest.main(verbosity=2)
