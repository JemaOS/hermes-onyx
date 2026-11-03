"""Tests du cœur du coffre : chiffrement au repos, empreinte, non-fuite.

Lancer :
    python -m unittest discover -s tests -v
(aucune dépendance Hermes : seuls ``cryptography`` et la stdlib sont requis)
"""

from __future__ import annotations

import importlib.util
import json
import logging
import os
import sqlite3
import sys
import tempfile
import unittest
from contextlib import closing
from pathlib import Path
from unittest import mock

from cryptography.fernet import Fernet

ROOT = Path(__file__).resolve().parent.parent
CORE_PATH = ROOT / "dashboard" / "onyx_vault.py"

# Valeur sentinelle : longue (>= 8) pour que l'empreinte ne soit que la fin.
SENTINEL = "SENTINEL-do-not-leak-9f3a-ABCD"
SENTINEL_TAIL = "ABCD"


def load_core():
    spec = importlib.util.spec_from_file_location("onyx_vault", CORE_PATH)
    module = importlib.util.module_from_spec(spec)
    sys.modules["onyx_vault"] = module
    spec.loader.exec_module(module)
    return module


core = load_core()


class VaultTestCase(unittest.TestCase):
    """Isole ``ONYX_VAULT_DIR`` + clé pour chaque test, et remet le singleton à zéro."""

    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.vault_dir = Path(self._tmp.name) / "vault"
        self.env = {
            "ONYX_VAULT_DIR": str(self.vault_dir),
            "ONYX_VAULT_KEY": Fernet.generate_key().decode(),
        }
        self._patch = mock.patch.dict(os.environ, self.env, clear=False)
        self._patch.start()
        os.environ.pop("ONYX_VAULT_KEY_FILE", None)
        core.reset_vault()

    def tearDown(self):
        core.reset_vault()
        self._patch.stop()
        self._tmp.cleanup()

    def vault(self):
        return core.Vault()


class TestKeyHandling(VaultTestCase):
    def test_missing_key_refuses_to_serve_with_a_clear_message(self):
        with mock.patch.dict(os.environ, {"ONYX_VAULT_DIR": str(self.vault_dir)}):
            os.environ.pop("ONYX_VAULT_KEY", None)
            with self.assertRaises(core.VaultUnavailable) as ctx:
                core.load_key()
        message = str(ctx.exception)
        self.assertIn("absente", message)
        self.assertIn("Fernet.generate_key", message)  # remédiation actionnable
        self.assertNotIn(self.env["ONYX_VAULT_KEY"], message)

    def test_invalid_key_is_refused_without_echoing_it(self):
        os.environ["ONYX_VAULT_KEY"] = "pas-une-cle-fernet"
        with self.assertRaises(core.VaultUnavailable) as ctx:
            core.load_key()
        self.assertIn("invalide", str(ctx.exception))
        self.assertNotIn("pas-une-cle-fernet", str(ctx.exception))

    def test_key_file_must_be_private(self):
        key_file = self.vault_dir / "onyx-vault.key"
        key_file.parent.mkdir(parents=True, exist_ok=True)
        key_file.write_text(Fernet.generate_key().decode())
        os.environ.pop("ONYX_VAULT_KEY", None)
        os.chmod(key_file, 0o644)
        material = core.load_key()
        self.assertFalse(material.secure_permissions)
        os.chmod(key_file, 0o600)
        self.assertTrue(core.load_key().secure_permissions)

    def test_get_vault_retries_once_the_key_appears(self):
        os.environ.pop("ONYX_VAULT_KEY", None)
        with self.assertRaises(core.VaultUnavailable):
            core.get_vault()
        key_file = self.vault_dir / "onyx-vault.key"
        key_file.parent.mkdir(parents=True, exist_ok=True)
        key_file.write_text(Fernet.generate_key().decode())
        os.chmod(key_file, 0o600)
        self.assertEqual(core.get_vault().count(), 0)


class TestNoValueLeak(VaultTestCase):
    """Exigence : aucun ``repr``/``str``/``format``/log/disque ne contient la valeur."""

    def test_repr_and_str_of_secret_objects_never_contain_the_value(self):
        vault = self.vault()
        vault.put("onyx", "OPENAI_KEY", SENTINEL)
        secret = vault.read("onyx", "OPENAI_KEY")
        meta = vault.list("onyx")[0]

        renderings = {
            "repr(secret)": repr(secret),
            "str(secret)": str(secret),
            "format(secret)": f"{secret}",
            "format-spec(secret)": format(secret, ""),
            "repr(meta)": repr(meta),
            "str(meta)": str(meta),
            "json(meta.to_dict())": json.dumps(meta.to_dict()),
            "str(exc)": "",
        }
        with self.assertRaises(core.VaultNotFound) as ctx:
            vault.read("onyx", "INCONNU")
        renderings["str(exc)"] = str(ctx.exception)

        for label, rendered in renderings.items():
            self.assertNotIn(SENTINEL, rendered, f"fuite de la valeur via {label}")
            self.assertNotIn(SENTINEL[:-4], rendered, f"fuite partielle via {label}")

        # Le seul accès explicite reste reveal().
        self.assertEqual(secret.reveal(), SENTINEL)
        self.assertIn("<redacted>", repr(secret))

    def test_secret_cannot_be_pickled(self):
        secret = core.Secret("onyx", "K", SENTINEL)
        with self.assertRaises(TypeError):
            import pickle

            pickle.dumps(secret)

    def test_put_result_never_contains_the_value(self):
        result = self.vault().put("onyx", "K", SENTINEL)
        rendered = json.dumps(result)
        self.assertNotIn(SENTINEL, rendered)
        self.assertEqual(result["fingerprint"], SENTINEL_TAIL)

    def test_logs_never_contain_the_value(self):
        records: list[logging.LogRecord] = []

        class Collector(logging.Handler):
            def emit(self, record):
                records.append(record)

        handler = Collector(level=logging.DEBUG)
        logger = logging.getLogger(core.log.name)
        logger.addHandler(handler)
        old_level = logger.level
        logger.setLevel(logging.DEBUG)
        try:
            vault = self.vault()
            vault.put("onyx", "K", SENTINEL)
            vault.list("onyx")
            vault.read("onyx", "K")
            vault.revoke("onyx", "K")
            # Un échec de validation ne doit pas non plus journaliser la valeur.
            with self.assertRaises(core.VaultError):
                vault.put("onyx", "K", "x" * (core.MAX_VALUE_BYTES + 1))
        finally:
            logger.removeHandler(handler)
            logger.setLevel(old_level)

        self.assertTrue(records, "aucun log capturé : le test ne prouverait rien")
        corpus = "\n".join(record.getMessage() for record in records)
        corpus += "\n".join(repr(record.args) for record in records if record.args)
        self.assertNotIn(SENTINEL, corpus)

    def test_database_never_contains_the_plaintext(self):
        vault = self.vault()
        vault.put("onyx", "K", SENTINEL, used_by=["onyx-android"])
        artifacts = [vault.db_path, *vault.db_path.parent.glob(f"{vault.db_path.name}*")]
        self.assertTrue(artifacts)
        for artifact in artifacts:
            if not artifact.is_file():
                continue
            blob = artifact.read_bytes()
            self.assertNotIn(SENTINEL.encode(), blob, f"valeur en clair dans {artifact.name}")
            self.assertNotIn(SENTINEL[:-4].encode(), blob, f"motif de la valeur dans {artifact.name}")


class TestVaultBehaviour(VaultTestCase):
    def test_put_list_revoke_roundtrip(self):
        vault = self.vault()
        created = vault.put("onyx", "ANTHROPIC_KEY", SENTINEL, used_by=["onyx-android", "gateway"])
        self.assertTrue(created["created"])
        self.assertEqual(created["fingerprint"], SENTINEL_TAIL)

        metas = vault.list("onyx")
        self.assertEqual([m.name for m in metas], ["ANTHROPIC_KEY"])
        self.assertEqual(metas[0].used_by, ("onyx-android", "gateway"))

        updated = vault.put("onyx", "ANTHROPIC_KEY", "rotated-value-WXYZ")
        self.assertFalse(updated["created"])
        self.assertEqual(updated["fingerprint"], "WXYZ")
        # used_by omis : la métadonnée précédente est conservée.
        self.assertEqual(updated["used_by"], ["onyx-android", "gateway"])

        self.assertTrue(vault.revoke("onyx", "ANTHROPIC_KEY"))
        self.assertFalse(vault.revoke("onyx", "ANTHROPIC_KEY"))
        self.assertEqual(vault.list("onyx"), [])

    def test_short_values_are_fully_masked(self):
        vault = self.vault()
        for value in ("abc", "abcd", "abcde", "abcdefg"):
            meta = vault.put("onyx", f"K{len(value)}", value)
            self.assertEqual(meta["fingerprint"], core.MASKED_FINGERPRINT)

    def test_profiles_are_isolated(self):
        vault = self.vault()
        vault.put("onyx", "TOKEN", SENTINEL)
        vault.put("autre", "TOKEN", "different-value-1234")
        self.assertEqual(len(vault.list("onyx")), 1)
        self.assertEqual(vault.read("autre", "TOKEN").reveal(), "different-value-1234")
        self.assertTrue(vault.revoke("autre", "TOKEN"))
        self.assertEqual(len(vault.list("onyx")), 1)

    def test_ciphertext_moved_to_another_row_is_detected(self):
        vault = self.vault()
        vault.put("onyx", "A", SENTINEL)
        vault.put("onyx", "B", "other-value-0000")
        with closing(sqlite3.connect(vault.db_path)) as cx:
            (blob,) = cx.execute(
                "SELECT ciphertext FROM secrets WHERE profile='onyx' AND name='A'"
            ).fetchone()
            cx.execute(
                "UPDATE secrets SET ciphertext=? WHERE profile='onyx' AND name='B'", (blob,)
            )
            cx.commit()
        with self.assertRaises(core.VaultError) as ctx:
            vault.read("onyx", "B")
        self.assertIn("incohérent", str(ctx.exception))

    def test_wrong_key_cannot_decrypt(self):
        vault = self.vault()
        vault.put("onyx", "A", SENTINEL)
        stranger = core.Vault(key=core.KeyMaterial(Fernet.generate_key(), "env:test"))
        with self.assertRaises(core.VaultError) as ctx:
            stranger.read("onyx", "A")
        self.assertIn("déchiffrement impossible", str(ctx.exception))
        self.assertNotIn(SENTINEL, str(ctx.exception))

    def test_validation_rejects_bad_input_without_echoing_it(self):
        vault = self.vault()
        cases = [
            (("ONYX", "K", "v"), "profil invalide"),
            (("onyx", "-K", "v"), "nom invalide"),
            (("onyx", "K", ""), "valeur vide"),
            (("onyx", "K", "x" * (core.MAX_VALUE_BYTES + 1)), "trop longue"),
        ]
        for args, expected in cases:
            with self.assertRaises(core.VaultInvalid) as ctx:
                vault.put(*args)
            self.assertIn(expected, str(ctx.exception))
            self.assertNotIn("x" * 32, str(ctx.exception))

    def test_used_by_validation(self):
        vault = self.vault()
        with self.assertRaises(core.VaultInvalid):
            vault.put("onyx", "K", "v", used_by=["ok", "bad name with spaces"])
        with self.assertRaises(core.VaultInvalid):
            vault.put("onyx", "K", "v", used_by="onyx-android")
        self.assertEqual(vault.put("onyx", "K", "v", used_by=["a", "a"])["used_by"], ["a"])

    def test_files_are_private(self):
        vault = self.vault()
        vault.put("onyx", "K", SENTINEL)
        self.assertEqual(vault.db_path.stat().st_mode & 0o777, 0o600)
        self.assertEqual(vault.db_path.parent.stat().st_mode & 0o777, 0o700)


if __name__ == "__main__":
    unittest.main(verbosity=2)
