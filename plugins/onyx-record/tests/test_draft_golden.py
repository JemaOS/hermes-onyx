"""Le convertisseur : même octet que le Kotlin livré dans l'app.

Le golden ``core/src/test/resources/skill/invoice.md`` est la référence d'octets
(l'énoncé du lot) : ce fichier le lit tel quel et compare. Aucun Markdown
attendu n'est recopié ici — s'il l'était, le test comparerait deux copies de la
même erreur.

Lancer depuis la racine du plugin :
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
    """La sortie Python doit être le fichier de l'app, à l'octet."""

    def test_markdown_equals_the_app_golden(self):
        golden = Path(fixture.GOLDEN_PATH)
        self.assertTrue(golden.is_file(), f"golden absent : {golden}")
        expected = golden.read_bytes()

        produced = core.draft(fixture.golden_journal())["markdown"].encode("utf-8")
        self.assertEqual(produced, expected, "le brouillon Python diverge du golden Kotlin")
        print(f"\n[golden] {len(produced)} octets, identiques à {golden.name}")

    def test_route_shape_gives_the_same_bytes(self):
        """Le chemin réel (validation puis conversion) ne change pas un octet."""
        validated = core.validate(fixture.golden_journal())
        produced = core.draft(validated.journal)["markdown"].encode("utf-8")
        self.assertEqual(produced, Path(fixture.GOLDEN_PATH).read_bytes())

    def test_the_app_secret_is_nowhere(self):
        draft = core.draft(fixture.golden_journal())
        self.assertNotIn(fixture.SECRET, draft["markdown"])
        self.assertNotIn(fixture.SECRET, str(draft))
        # Quatre puces, toujours quatre : la longueur n'est pas dite.
        self.assertIn(core.SECRET_TEXT, draft["markdown"])
        self.assertNotIn("••••••", draft["markdown"])

    def test_counts_and_review(self):
        draft = core.draft(fixture.golden_journal())
        self.assertEqual(draft["steps"], 9)
        self.assertEqual(draft["title"], fixture.GOLDEN_TITLE)
        # Le pas secret est « à relire » au sens de la spec (secret_field) : sa
        # ligne porte déjà « champ masqué, non enregistré », donc la note
        # « > À relire » du Markdown ne le cite pas — elle est celle du Kotlin.
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
        # 120 + 180 ms -> un seul pas de 300 ms.
        self.assertIn("3. Wait 300 ms", markdown)
        self.assertNotIn("120 ms", markdown)
        # 540 + 220 px -> un seul défilement de 760 px.
        self.assertIn("7. Scroll down 760 px", markdown)
        self.assertNotIn("540", markdown)

    def test_forty_events_are_stable(self):
        """La preuve §12.1 de la spec : 40 événements, un markdown stable."""
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
        """Un journal vide le dit — il ne fabrique pas un skill vide (règle 5)."""
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
        """Ni libellé ni position : le pas existe, et il dit qu'il faut le relire."""
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
        """Le Markdown reste celui du Kotlin : les pas sont en anglais verbe + cible."""
        markdown = core.draft(fixture.spec_journal())["markdown"]
        self.assertIn('1. Focus "Finder"', markdown)
        self.assertIn('2. Tap "Nouveau dossier"', markdown)
        self.assertIn("5. Press Enter", markdown)
        # La prose (titre, intention, note de relecture) est française, comme le Kotlin.
        self.assertIn("Reproduire les 9 étapes", markdown)
        self.assertIn("> À relire : l'étape 9.", markdown)

    def test_percent_rounds_half_up_and_is_clamped(self):
        """``roundToInt`` de Kotlin arrondit les demis vers +∞, pas vers le pair."""
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
        # 0.625 -> 62.5 % : Kotlin dit 63 (demi vers +∞), le round() de Python dirait 62.
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
        # Le libellé est ramené à une ligne AVANT l'échappement, comme le Kotlin.
        self.assertIn('1. Tap "Deux \\"lignes\\" ici"', markdown)
        self.assertIn('2. Type "ligne 1\\nligne 2\\\\fin"', markdown)
        # Deux étapes -> quatre lignes de liste au plus : aucune ligne vide au milieu.
        self.assertEqual(markdown.count("\n\n"), 2)


if __name__ == "__main__":
    unittest.main(verbosity=2)
