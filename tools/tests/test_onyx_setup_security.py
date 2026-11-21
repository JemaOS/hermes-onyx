"""Installer security contracts; never invoke the installer or real service managers."""
import contextlib
import importlib.util
import io
import os
from pathlib import Path
import plistlib
import stat
import subprocess
import tempfile
import unittest
from unittest import mock


SPEC = importlib.util.spec_from_file_location(
    "onyx_setup_security", Path(__file__).resolve().parents[1] / "onyx-setup.py")
assert SPEC is not None and SPEC.loader is not None
setup = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(setup)


class InstallerSecurityTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.home = Path(self.tmp.name)
        self.stack = contextlib.ExitStack()
        self.addCleanup(self.stack.close)
        self.stack.enter_context(mock.patch.object(Path, "home", return_value=self.home))
        root = self.home / ".onyx-one"
        self.stack.enter_context(mock.patch.object(setup, "RACINE", root))
        for name, filename in (("CLE", "cle"), ("JETON", "jeton"), ("MDP", "mdp"),
                               ("ENV_DASH", "dashboard.env"), ("VOIX_FICHIER", "voix"),
                               ("DESTINATAIRE", "recipient")):
            self.stack.enter_context(mock.patch.object(setup, name, root / filename))
        self.stack.enter_context(mock.patch.object(setup, "LIEN", self.home / "link.txt"))
        self.stack.enter_context(mock.patch.object(setup, "QR", self.home / "qr.png"))
        self.process_run = self.stack.enter_context(mock.patch.object(setup.subprocess, "run"))
        self.process_run.return_value = subprocess.CompletedProcess([], 0, stdout="", stderr="")
        self.stack.enter_context(mock.patch.object(setup.subprocess, "Popen"))

    def assert_private(self, path):
        self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o600)

    def test_secret_is_private_before_publication_and_replaces_symlink(self):
        victim = self.home / "unrelated"
        victim.write_text("untouched")
        setup.CLE.parent.mkdir()
        setup.CLE.symlink_to(victim)
        replace = os.replace

        def inspect(source, target):
            self.assert_private(Path(source))
            self.assertEqual(Path(source).read_text(), "credential\n")
            replace(source, target)

        previous = os.umask(0)
        try:
            with mock.patch.object(setup.os, "replace", side_effect=inspect):
                setup.ecrire_secret(setup.CLE, "credential")
        finally:
            os.umask(previous)
        self.assertEqual(victim.read_text(), "untouched")
        self.assertFalse(setup.CLE.is_symlink())
        self.assert_private(setup.CLE)

    def test_failed_atomic_write_keeps_old_secret_and_cleans_staging(self):
        setup.ecrire_secret(setup.CLE, "old")
        with self.assertRaises(RuntimeError):
            with setup.fichier_prive(setup.CLE) as stream:
                stream.write(b"new")
                raise RuntimeError("failed producer")
        self.assertEqual(setup.CLE.read_text(), "old\n")
        self.assertEqual(list(setup.CLE.parent.glob(".onyx-*")), [])

    def test_existing_secret_and_log_are_tightened_without_truncation(self):
        setup.CLE.parent.mkdir()
        setup.CLE.write_text("retained")
        setup.CLE.chmod(0o644)
        self.assertEqual(setup.lire_secret(setup.CLE), "retained")
        self.assert_private(setup.CLE)
        setup.proteger_fichier(setup.CLE, creer=True)
        self.assertEqual(setup.CLE.read_text(), "retained")
        victim = self.home / "victim"
        victim.write_text("preserved")
        setup.CLE.unlink()
        setup.CLE.symlink_to(victim)
        with self.assertRaises(OSError):
            setup.proteger_fichier(setup.CLE, creer=True)
        self.assertEqual(victim.read_text(), "preserved")

    def test_platform_service_artifacts_and_unchanged_repairs(self):
        for system in ("Linux", "Darwin", "Windows"):
            with self.subTest(system=system), mock.patch.object(setup, "SYSTEME", system):
                services = setup.Services()
                log = setup.RACINE / (system + ".log")
                services.installer("test", ["python", "worker.py"],
                                   {"SESSION_TOKEN": "credential"}, self.home, log)
                path = {"Linux": services._unite_linux,
                        "Darwin": services._plist,
                        "Windows": services._cmd_windows}[system]("test")
                self.assert_private(path)
                self.assert_private(log)
                text = path.read_text()
                if system == "Linux":
                    self.assertNotIn("credential", text)
                    self.assertIn("credential", path.with_suffix(".env").read_text())
                    self.assert_private(path.with_suffix(".env"))
                    path.with_suffix(".env").chmod(0o644)
                    self.assertIn("UMask=0077", text)
                    self.assertNotIn("StandardOutput=journal", text)
                elif system == "Darwin":
                    self.assertEqual(plistlib.loads(path.read_bytes())["Umask"], 63)
                path.chmod(0o644)
                log.chmod(0o644)
                services.installer("test", ["python", "worker.py"],
                                   {"SESSION_TOKEN": "credential"}, self.home, log)
                self.assert_private(path)
                self.assert_private(log)

    def test_cloudflared_output_is_not_duplicated(self):
        for system in ("Linux", "Darwin"):
            with self.subTest(system=system), mock.patch.object(setup, "SYSTEME", system):
                services = setup.Services()
                log = setup.RACINE / "tunnel.log"
                services.installer("tunnel", ["cloudflared", "--logfile", str(log)],
                                   {}, self.home, log)
                self.assert_private(log)
                if system == "Linux":
                    text = services._unite_linux("tunnel").read_text()
                    self.assertIn("StandardOutput=null", text)
                    self.assertIn("StandardError=null", text)
                else:
                    config = plistlib.loads(services._plist("tunnel").read_bytes())
                    self.assertEqual(config["StandardErrorPath"], "/dev/null")

    def fake_encoder(self, args, **kwargs):
        self.assertNotIn("credential", " ".join(args))
        if args[-1] == "png":
            self.assertEqual(kwargs["input"], b"credential")
            self.assertEqual(stat.S_IMODE(os.fstat(kwargs["stdout"].fileno()).st_mode), 0o600)
            kwargs["stdout"].write(b"PNG fixture")
            return subprocess.CompletedProcess(args, 0)
        return subprocess.CompletedProcess(args, 0, stdout="1\n")

    def test_qr_is_private_and_secret_is_piped_not_in_arguments(self):
        self.process_run.side_effect = self.fake_encoder
        with mock.patch.object(setup, "_python_avec_segno", return_value="python"), \
                contextlib.redirect_stdout(io.StringIO()) as output:
            self.assertEqual(setup.rendre_qr("credential", afficher=False), "image")
        self.assertEqual(output.getvalue(), "")
        self.assertEqual(setup.QR.read_bytes(), b"PNG fixture")
        self.assert_private(setup.QR)

    def test_failed_encoder_preserves_existing_qr(self):
        setup.ecrire_prive(setup.QR, "old image")
        self.process_run.side_effect = subprocess.CalledProcessError(1, ["encoder"])
        with mock.patch.object(setup, "_python_avec_segno", return_value="python"):
            self.assertEqual(setup.rendre_qr("credential"), "pas-encodeur")
        self.assertEqual(setup.QR.read_text(), "old image")

    def test_refresh_is_silent_and_repairs_existing_pairing_artifacts(self):
        setup.ecrire_secret(setup.CLE, "credential")
        setup.ecrire_secret(setup.LIEN, setup.lien("https://old", "credential", "", "", ""))
        setup.ecrire_prive(setup.QR, "previous")
        setup.QR.chmod(0o644)
        setup.CLE.chmod(0o644)
        with mock.patch.object(setup, "adresse_tunnel", return_value="https://new"), \
                mock.patch.object(setup, "_python_avec_segno", return_value=None), \
                mock.patch.object(setup, "ouvrir_image") as opener, \
                contextlib.redirect_stdout(io.StringIO()) as output:
            self.assertEqual(setup.rafraichir(setup.Services()), 0)
        self.assertEqual(output.getvalue(), "")
        opener.assert_not_called()
        self.assertIn("server=https%3A%2F%2Fnew", setup.LIEN.read_text())
        for path in (setup.CLE, setup.LIEN, setup.QR):
            self.assert_private(path)

    def test_foreground_pairing_still_displays_link(self):
        setup.ecrire_secret(setup.CLE, "credential")
        with mock.patch.object(setup, "adresse_tunnel", return_value="https://new"), \
                mock.patch.object(setup, "rendre_qr", return_value="pas-encodeur"), \
                contextlib.redirect_stdout(io.StringIO()) as output:
            setup.donner_lien(setup.Services())
        self.assertIn("credential", output.getvalue())
        self.assert_private(setup.LIEN)

    def test_watchdog_files_are_private_and_do_not_capture_pairing_output(self):
        for system in ("Linux", "Darwin", "Windows"):
            with self.subTest(system=system), mock.patch.object(setup, "SYSTEME", system):
                services = setup.Services()
                setup.installer_rafraichissement(services)
                self.assert_private(setup.RACINE / "watchdog.log")
                if system == "Linux":
                    path = services._unite_linux("onyx-link-watchdog")
                    self.assertIn("StandardOutput=null", path.read_text())
                    self.assert_private(path)
                elif system == "Darwin":
                    path = self.home / "Library/LaunchAgents/com.onyx.watchdog.plist"
                    self.assert_private(path)
                    self.assertEqual(plistlib.loads(path.read_bytes())["StandardOutPath"], "/dev/null")

    def test_notification_uses_stdin_and_redacts_failure_diagnostics(self):
        for outcome in ("success", "failure", "timeout"):
            with self.subTest(outcome=outcome):
                setup.ecrire_secret(setup.CLE, "credential")
                setup.ecrire_secret(setup.DESTINATAIRE, "recipient")
                setup.ecrire_secret(setup.LIEN, setup.lien("https://old", "credential", "", "", ""))
                self.process_run.reset_mock()

                def send(args, **kwargs):
                    self.assertEqual(args, ["hermes", "send", "--to", "recipient", "--file", "-"])
                    self.assertNotIn("credential", " ".join(args))
                    self.assertEqual(kwargs["input"],
                                     "Le tunnel Onyx a changé de nom — voici le lien courant :\n"
                                     + setup.LIEN.read_text().strip())
                    self.assertIn("credential", kwargs["input"])
                    self.assertTrue(kwargs["text"])
                    self.assertTrue(kwargs["capture_output"])
                    self.assertEqual(kwargs["timeout"], 120)
                    if outcome == "timeout":
                        raise subprocess.TimeoutExpired(
                            args, 120, output="credential stdout", stderr="credential stderr")
                    return subprocess.CompletedProcess(
                        args, int(outcome == "failure"),
                        stdout="credential stdout", stderr="credential stderr")

                self.process_run.side_effect = send
                with mock.patch.object(setup, "adresse_tunnel", return_value="https://new"), \
                        mock.patch.object(setup, "_python_avec_segno", return_value=None), \
                        mock.patch.object(setup, "commande", return_value="hermes"), \
                        contextlib.redirect_stdout(io.StringIO()) as output, \
                        contextlib.redirect_stderr(io.StringIO()) as errors:
                    self.assertEqual(setup.rafraichir(setup.Services()), 0)
                self.process_run.assert_called_once()
                diagnostics = output.getvalue() + errors.getvalue()
                self.assertNotIn("credential", diagnostics)
                if outcome == "success":
                    self.assertEqual(diagnostics, "")
                else:
                    self.assertIn("Notification", diagnostics)
                    self.assertIn("fichier local", diagnostics)
                self.assertIn("credential", setup.LIEN.read_text())

    def test_windows_acl_failure_prevents_secret_publication(self):
        import types
        windows_os = types.SimpleNamespace(**vars(os))
        windows_os.name = "nt"
        with mock.patch.object(setup, "os", windows_os), \
                mock.patch.object(setup, "_acl_privee", side_effect=OSError("ACL denied")):
            with self.assertRaisesRegex(OSError, "ACL denied"):
                setup.ecrire_secret(setup.CLE, "credential")
        self.assertFalse(setup.CLE.exists())
        self.assertEqual(list(setup.CLE.parent.glob(".onyx-*")), [])

    def test_windows_acl_replaces_inherited_and_explicit_grants(self):
        self.process_run.return_value.stdout = '"user","S-1-5-21-123"\n'
        setup._acl_privee(self.home)
        script = self.process_run.call_args.args[0][-1]
        self.assertIn("D:P(A;OICI;FA;;;S-1-5-21-123)", script)
        self.assertIn("Set-Acl -LiteralPath", script)
        self.assertTrue(self.process_run.call_args.kwargs["check"])


if __name__ == "__main__":
    unittest.main()
