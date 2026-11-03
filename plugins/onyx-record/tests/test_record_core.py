"""La machine à états du serveur et les bornes du journal, sans HTTP.

La spec (§7) décrit deux états côté serveur : ``recording`` (bail ouvert) et
``journal_pending`` (journal canonique gardé 120 s). Les transitions invalides
ont chacune **un code et une phrase** (§7.2) : c'est ce que ce fichier vérifie,
avec une horloge factice pour ne pas attendre dix minutes.

Lancer depuis la racine du plugin :
    python -m unittest discover -s tests -t tests -v
"""

from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import journal_fixture as fixture  # noqa: E402
import support  # noqa: E402

core = support.load_core()

SECRET = "SENTINEL-mot-de-passe-42-ZXCV"


class Clock:
    def __init__(self, now: float = 1730650000.0) -> None:
        self.now = now

    def __call__(self) -> float:
        return self.now

    def advance(self, seconds: float) -> None:
        self.now += seconds


class StoreCase(unittest.TestCase):
    def setUp(self) -> None:
        self.clock = Clock()
        self.store = core.RecordStore(clock=self.clock)
        self.session = self._start()

    def _start(self, **overrides) -> str:
        body = {"bridge": {"endpoint": "192.168.1.24:8765", "session": "pc-7f21"}}
        body.update(overrides)
        return self.store.start(body)["session_id"]

    def _journal(self, session_id: str, **overrides) -> dict:
        journal = fixture.golden_journal(session_id=session_id, bridge_session="pc-7f21")
        journal.update(overrides)
        return journal

    def assertRefused(self, code: str, status: int, call, *args, **kwargs):
        with self.assertRaises(core.RecordError) as caught:
            call(*args, **kwargs)
        self.assertEqual(caught.exception.code, code)
        self.assertEqual(caught.exception.status, status)
        self.assertTrue(caught.exception.message)
        return caught.exception


class TestStart(StoreCase):
    def test_start_opens_a_lease_with_the_spec_fields(self):
        body = self.store.start  # déjà ouvert par setUp : on vérifie la forme
        with self.assertRaises(core.RecordError):
            body({"bridge": {"endpoint": "h:1", "session": "s"}})
        lease = self.store.lease()
        self.assertIsNotNone(lease)
        self.assertRegex(lease.session_id, r"^rec_[0-9A-F]{12}$")
        self.assertEqual(lease.deadline_at - lease.started_at, 600)
        self.assertEqual(lease.bridge_session, "pc-7f21")

    def test_response_shape(self):
        store = core.RecordStore(clock=self.clock)
        opened = store.start({"bridge": {"endpoint": "h:1", "session": "s"}})
        self.assertEqual(
            sorted(opened),
            ["deadline_at", "grace_seconds", "max_seconds", "session_id", "started_at"],
        )
        self.assertEqual(opened["max_seconds"], 600)
        self.assertEqual(opened["grace_seconds"], 120)
        self.assertEqual(opened["deadline_at"], round(opened["started_at"] + 600, 3))

    def test_double_start_is_already_recording_and_names_the_lease(self):
        exc = self.assertRefused(
            "already_recording",
            409,
            self.store.start,
            {"bridge": {"endpoint": "h:1", "session": "s"}},
        )
        self.assertEqual(exc.extra["session_id"], self.session)
        self.assertIn("deadline_at", exc.extra)
        self.assertEqual(self.store.lease().session_id, self.session, "pas de second bail")

    def test_profile_is_refused_not_ignored(self):
        store = core.RecordStore(clock=self.clock)
        self.assertRefused(
            "profile_not_supported",
            400,
            store.start,
            {"profile": "onyx", "bridge": {"endpoint": "h:1", "session": "s"}},
        )
        self.assertIsNone(store.lease())

    def test_bad_bridge_variants(self):
        store = core.RecordStore(clock=self.clock)
        cases = [
            {},
            {"bridge": None},
            {"bridge": {"endpoint": "", "session": "s"}},
            {"bridge": {"endpoint": "http://192.168.1.24:8765", "session": "s"}},
            {"bridge": {"endpoint": "user:pwd@192.168.1.24:8765", "session": "s"}},
            {"bridge": {"endpoint": "192.168.1.24:8765"}},
            {"bridge": {"endpoint": "192.168.1.24:8765", "session": "  "}},
            {"bridge": {"endpoint": "192.168.1.24/8765", "session": "s"}},
        ]
        for body in cases:
            with self.subTest(body=body):
                self.assertRefused("bad_bridge", 400, store.start, body)

    def test_start_after_stop_needs_discard_pending(self):
        self.store.stop({"session_id": self.session, "journal": self._journal(self.session)})
        self.assertRefused(
            "journal_pending",
            409,
            self.store.start,
            {"bridge": {"endpoint": "h:1", "session": "s"}},
        )
        opened = self.store.start(
            {"bridge": {"endpoint": "h:1", "session": "s"}, "discard_pending": True}
        )
        self.assertNotEqual(opened["session_id"], self.session)
        self.assertIsNone(self.store.pending())

    def test_discard_pending_must_be_a_boolean(self):
        store = core.RecordStore(clock=self.clock)
        self.assertRefused(
            "bad_request",
            400,
            store.start,
            {"bridge": {"endpoint": "h:1", "session": "s"}, "discard_pending": "oui"},
        )


class TestStop(StoreCase):
    def test_full_stop_returns_the_canonical_journal(self):
        journal = self._journal(self.session)
        journal["events"][1]["target"]["label"] = "L" * 260  # tronqué à 200
        journal["events"][2]["ms"] = 40  # relevé à 100
        journal["unknown_field"] = "jeté"
        journal["events"][0]["unknown"] = "jeté aussi"

        closed = self.store.stop({"session_id": self.session, "journal": journal})
        self.assertEqual(closed["session_id"], self.session)
        self.assertEqual(closed["end_reason"], "stopped")
        self.assertFalse(closed["truncated"])
        self.assertEqual(closed["dropped"], 0)
        self.assertEqual(closed["actions"], len(closed["journal"]["events"]))
        self.assertEqual(closed["bytes"], core.serialized_size(closed["journal"]))
        self.assertNotIn("unknown_field", closed["journal"])
        self.assertNotIn("unknown", closed["journal"]["events"][0])
        self.assertEqual(len(closed["journal"]["events"][1]["target"]["label"]), 200)
        self.assertEqual(closed["journal"]["events"][2]["ms"], 100)
        self.assertEqual(sorted(closed["warnings"]), ["label_truncated", "wait_clamped"])
        self.assertEqual(closed["duration_ms"], 0)
        self.assertIsNotNone(self.store.pending())

    def test_stop_without_a_journal_abandons(self):
        closed = self.store.stop({"session_id": self.session, "journal": None})
        self.assertEqual(closed["end_reason"], "discarded")
        self.assertNotIn("journal", closed)
        self.assertEqual(closed["actions"], 0)
        self.assertIsNone(self.store.pending())

    def test_stop_is_idempotent_to_the_byte(self):
        journal = self._journal(self.session)
        first = self.store.stop({"session_id": self.session, "journal": journal})
        self.clock.advance(30)
        second = self.store.stop({"session_id": self.session, "journal": journal})
        self.assertEqual(second, first)
        self.assertEqual(second["bytes"], first["bytes"])

    def test_idle_stop_is_not_recording(self):
        store = core.RecordStore(clock=self.clock)
        self.assertRefused("not_recording", 409, store.stop, {"session_id": "rec_AAAAAAAAAAAA"})

    def test_unknown_session_when_another_one_is_open(self):
        self.assertRefused(
            "unknown_session", 409, self.store.stop, {"session_id": "rec_BBBBBBBBBBBB"}
        )

    def test_missing_session_id_is_bad_request(self):
        for body in ({}, {"session_id": ""}, {"session_id": 42}):
            with self.subTest(body=body):
                self.assertRefused("bad_request", 400, self.store.stop, body)

    def test_bridge_mismatch(self):
        journal = fixture.golden_journal(session_id=self.session, bridge_session="pc-autre")
        self.assertRefused(
            "bridge_mismatch", 409, self.store.stop, {"session_id": self.session, "journal": journal}
        )
        self.assertIsNotNone(self.store.lease(), "le bail reste ouvert après un refus")

    def test_journal_session_id_must_be_the_lease(self):
        journal = fixture.golden_journal(session_id="rec_ZZZZZZZZZZZZ", bridge_session="pc-7f21")
        self.assertRefused(
            "bad_journal", 422, self.store.stop, {"session_id": self.session, "journal": journal}
        )

    def test_refused_journal_does_not_close_the_lease(self):
        bad = self._journal(self.session)
        bad["events"][6]["text"] = SECRET
        exc = self.assertRefused(
            "secret_text_present", 422, self.store.stop, {"session_id": self.session, "journal": bad}
        )
        self.assertNotIn(SECRET, json.dumps(exc.payload()))
        self.assertIsNotNone(self.store.lease())
        # Le bon journal passe ensuite : le refus n'a rien coûté.
        closed = self.store.stop({"session_id": self.session, "journal": self._journal(self.session)})
        self.assertEqual(closed["end_reason"], "stopped")

    def test_version_is_the_only_unsupported_value(self):
        journal = self._journal(self.session)
        journal["version"] = 2
        self.assertRefused(
            "version_unsupported", 422, self.store.stop, {"session_id": self.session, "journal": journal}
        )
        journal.pop("version")
        self.assertRefused(
            "bad_journal", 422, self.store.stop, {"session_id": self.session, "journal": journal}
        )

    def test_journal_too_large_is_413_before_any_bounding(self):
        journal = self._journal(self.session)
        journal["events"] = [
            {
                "seq": index + 1,
                "at_ms": index * 10,
                "kind": "type",
                "target": {"window": "F"},
                "text": "x" * 2000,
                "chars": 2000,
            }
            for index in range(200)
        ]
        self.assertGreater(core.serialized_size(journal), core.MAX_JOURNAL_BYTES)
        self.assertRefused(
            "journal_too_large", 413, self.store.stop, {"session_id": self.session, "journal": journal}
        )


class TestExpiration(StoreCase):
    def test_within_grace_the_journal_is_accepted_as_expired(self):
        journal = self._journal(self.session)
        journal["events"].append(
            {"seq": 12, "at_ms": 650000, "kind": "tap", "target": {"label": "Trop tard", "window": "F",
                                                                   "x_pct": 0.5, "y_pct": 0.5}}
        )
        self.clock.advance(700)  # 100 s après l'échéance
        closed = self.store.stop({"session_id": self.session, "journal": journal})
        self.assertEqual(closed["end_reason"], "expired")
        self.assertEqual(closed["duration_ms"], 600000)
        self.assertTrue(closed["truncated"])
        self.assertEqual(closed["dropped"], 1)
        self.assertNotIn("Trop tard", json.dumps(closed["journal"], ensure_ascii=False))

    def test_beyond_grace_is_410_and_then_unknown(self):
        self.clock.advance(600 + 121)
        self.assertRefused(
            "session_expired", 410, self.store.stop, {"session_id": self.session}
        )
        self.assertRefused(
            "session_expired", 410, self.store.stop, {"session_id": self.session}
        )

    def test_expired_lease_still_blocks_a_second_start(self):
        self.clock.advance(700)
        self.assertRefused(
            "already_recording",
            409,
            self.store.start,
            {"bridge": {"endpoint": "h:1", "session": "s"}},
        )

    def test_pending_journal_vanishes_after_120_seconds(self):
        self.store.stop({"session_id": self.session, "journal": self._journal(self.session)})
        self.clock.advance(121)
        # Passé 120 s, le serveur ne garde plus rien : il répond 410, car il a
        # bien connu ce bail (voir ECARTS.md §5).
        self.assertRefused("session_expired", 410, self.store.stop, {"session_id": self.session})
        self.assertIsNone(self.store.pending())


class TestJournalRules(StoreCase):
    def _refuse(self, code: str, mutate) -> None:
        journal = self._journal(self.session)
        mutate(journal)
        self.assertRefused(code, 422, self.store.stop, {"session_id": self.session, "journal": journal})

    def test_secret_with_text_is_refused_not_cleaned(self):
        def mutate(journal):
            journal["events"][6]["text"] = SECRET

        self._refuse("secret_text_present", mutate)

    def test_secret_without_chars_is_refused(self):
        def mutate(journal):
            journal["events"][6].pop("chars")

        self._refuse("bad_journal", mutate)

    def test_unknown_kind_is_refused(self):
        def mutate(journal):
            journal["events"][2]["kind"] = "drag"

        self._refuse("bad_journal", mutate)

    def test_pixels_and_audio_are_refused_at_any_depth(self):
        for key in ("image", "png", "screenshot", "audio", "transcript"):
            with self.subTest(key=key):
                journal = self._journal(self.session)
                journal["events"][1]["target"][key] = "AAAA"
                self.assertRefused(
                    "bad_journal", 422, self.store.stop, {"session_id": self.session, "journal": journal}
                )
                self.assertIsNotNone(self.store.lease())

    def test_clipboard_with_text_is_refused(self):
        def mutate(journal):
            journal["events"][4]["from_clipboard"] = True

        self._refuse("bad_journal", mutate)

    def test_file_url_is_refused(self):
        def mutate(journal):
            journal["events"][0]["url"] = "file:///Users/john/secret.pdf"

        self._refuse("bad_journal", mutate)

    def test_seq_must_increase_and_at_ms_must_not_go_back(self):
        def duplicate_seq(journal):
            journal["events"][3]["seq"] = 2

        def backwards(journal):
            journal["events"][3]["at_ms"] = 100

        self._refuse("bad_journal", duplicate_seq)
        self._refuse("bad_journal", backwards)

    def test_screen_must_be_positive(self):
        def mutate(journal):
            journal["screen"]["width_px"] = 0

        self._refuse("bad_journal", mutate)

    def test_tap_without_position_is_accepted(self):
        """La position est facultative : un appui nommé n'en a pas besoin."""
        journal = self._journal(self.session)
        journal["events"].append(
            {"seq": 12, "at_ms": 7000, "kind": "tap", "target": {"label": "OK", "window": "F"}}
        )
        closed = self.store.stop({"session_id": self.session, "journal": journal})
        target = closed["journal"]["events"][-1]["target"]
        self.assertNotIn("x_pct", target)
        self.assertIn('Tap "OK"', core.draft(closed["journal"])["markdown"])

    def test_end_reason_is_a_closed_set(self):
        def mutate(journal):
            journal["end_reason"] = "crashed"

        self._refuse("bad_journal", mutate)

    def test_text_over_2000_characters_is_refused(self):
        def mutate(journal):
            journal["events"][4]["text"] = "x" * 2001

        self._refuse("bad_journal", mutate)

    def test_long_url_is_dropped_with_a_warning(self):
        journal = self._journal(self.session)
        journal["events"][0]["url"] = "https://exemple.fr/" + "a" * 2100
        closed = self.store.stop({"session_id": self.session, "journal": journal})
        self.assertEqual(closed["journal"]["events"][0]["url"], "")
        self.assertIn("url_dropped", closed["warnings"])


class TestBounds(StoreCase):
    def test_client_duration_is_capped_at_600_seconds(self):
        """`stop` garde la durée du serveur ; `draft` borne celle du client."""
        validated = core.validate(self._journal(self.session, duration_ms=10**9))
        self.assertEqual(validated.journal["duration_ms"], 600000)
        closed = self.store.stop({"session_id": self.session, "journal": self._journal(self.session)})
        self.assertEqual(closed["journal"]["duration_ms"], 0, "la durée est celle du serveur")

    def test_2001_events_keep_2000_and_count_the_drop(self):
        journal = self._journal(self.session)
        journal["events"] = [
            {"seq": index + 1, "at_ms": index * 10, "kind": "wait", "ms": 500}
            for index in range(2001)
        ]
        closed = self.store.stop({"session_id": self.session, "journal": journal})
        self.assertTrue(closed["truncated"])
        self.assertEqual(closed["dropped"], 1)
        self.assertEqual(closed["actions"], 2000)
        self.assertEqual(closed["journal"]["events"][0]["seq"], 2, "les plus anciens sont abandonnés")
        self.assertIn("events_dropped", closed["warnings"])

    def test_byte_bound_drops_from_the_front(self):
        # La route refuse d'abord un journal brut > 262144 octets (413) ; la
        # borne de taille reste donc une garde du **canonique** : on la teste là
        # où elle vit, dans ``validate`` (voir ECARTS.md §4).
        journal = self._journal(self.session)
        journal["events"] = [
            {
                "seq": index + 1,
                "at_ms": index * 10,
                "kind": "type",
                "target": {"window": "F"},
                "text": "y" * 1500,
                "chars": 1500,
            }
            for index in range(199)
        ]
        self.assertGreater(core.serialized_size(journal), core.MAX_JOURNAL_BYTES)
        bounded = core.validate(journal)
        self.assertLessEqual(bounded.size, core.MAX_JOURNAL_BYTES)
        self.assertTrue(bounded.truncated)
        self.assertGreater(bounded.dropped, 0)
        self.assertEqual(bounded.actions + bounded.dropped, 199)


class TestDraftAccess(StoreCase):
    def test_still_recording(self):
        self.assertRefused("still_recording", 409, self.store.journal_for_draft, self.session)

    def test_no_journal_after_abandon(self):
        self.store.stop({"session_id": self.session, "journal": None})
        self.assertRefused("no_journal", 409, self.store.journal_for_draft, self.session)

    def test_journal_pending_is_readable_then_expires(self):
        closed = self.store.stop({"session_id": self.session, "journal": self._journal(self.session)})
        self.assertEqual(self.store.journal_for_draft(self.session), closed["journal"])
        self.clock.advance(121)
        self.assertRefused("no_journal", 409, self.store.journal_for_draft, self.session)


class TestStatelessDraft(StoreCase):
    def test_draft_neither_writes_nor_keeps_state(self):
        closed = self.store.stop({"session_id": self.session, "journal": self._journal(self.session)})
        pending_before = self.store.pending()
        drafts = [core.draft(closed["journal"]) for _ in range(3)]
        self.assertEqual(drafts[0], drafts[1])
        self.assertEqual(drafts[1], drafts[2])
        self.assertIs(self.store.pending(), pending_before, "un brouillon ne consomme pas le journal")

    def test_health_never_names_a_session(self):
        state = self.store.health()
        self.assertEqual(sorted(state), [
            "grace_seconds",
            "journal_pending",
            "max_events",
            "max_journal_bytes",
            "max_seconds",
            "recording",
            "remaining_seconds",
        ])
        self.assertNotIn(self.session, json.dumps(state))


if __name__ == "__main__":
    unittest.main(verbosity=2)
