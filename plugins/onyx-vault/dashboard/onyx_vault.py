"""onyx-vault — cœur du coffre de secrets par profil (chiffrement au repos).

Invariants de sécurité — à ne jamais casser :

* la valeur en clair n'est écrite **nulle part** : ni sur disque, ni dans une
  requête SQL (seuls le chiffré Fernet, l'empreinte et les métadonnées le sont),
  ni dans un log, ni dans une exception, ni dans une réponse HTTP ;
* l'empreinte ne contient que les 4 derniers caractères de la valeur (et rien du
  tout en dessous de 8 caractères) ;
* la clé Fernet n'est jamais en dur : elle vient d'une variable d'environnement
  ou d'un fichier en 0600, sinon le coffre refuse de servir (``VaultUnavailable``)
  au lieu de planter ;
* chaque chiffré est lié à son ``(profil, nom)`` : le déplacer d'une ligne à
  l'autre est détecté au déchiffrement.

Ce module ne dépend d'aucun code Hermes : il est testable seul.
"""

from __future__ import annotations

import json
import logging
import os
import re
import sqlite3
import stat
import threading
from contextlib import closing
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable, Optional

from cryptography.fernet import Fernet, InvalidToken

log = logging.getLogger(__name__)

PLUGIN_NAME = "onyx-vault"
DB_FILENAME = "onyx-vault.db"
KEY_FILENAME = "onyx-vault.key"
MAX_VALUE_BYTES = 16 * 1024
# En dessous de cette longueur, une empreinte "4 derniers caractères" révélerait
# une part trop grande du secret : on masque entièrement.
MIN_FINGERPRINT_LENGTH = 8
MASKED_FINGERPRINT = "****"

_PROFILE_RE = re.compile(r"^[a-z0-9][a-z0-9._-]{0,63}$")
_NAME_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")
_USED_BY_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$")
_MAX_USED_BY = 32

KEY_HELP = (
    "Générer une clé (32 octets aléatoires en base64 urlsafe, équivalent de "
    "Fernet.generate_key(), sans dépendance) : "
    "mkdir -p ~/.hermes/vault && chmod 700 ~/.hermes/vault && "
    "python3 -c 'import base64, os; print(base64.urlsafe_b64encode(os.urandom(32)).decode())' "
    "> ~/.hermes/vault/onyx-vault.key && chmod 600 ~/.hermes/vault/onyx-vault.key"
)


# ---------------------------------------------------------------------------
# Erreurs — messages sûrs, jamais construits à partir d'une valeur
# ---------------------------------------------------------------------------


class VaultError(Exception):
    """Erreur du coffre. Le message est destiné à l'opérateur, pas au secret."""


class VaultUnavailable(VaultError):
    """Clé absente ou invalide : le coffre refuse de servir (503), il ne plante pas."""


class VaultInvalid(VaultError):
    """Entrée refusée (profil, nom, valeur ou métadonnée)."""


class VaultNotFound(VaultError):
    """Secret inconnu."""


# ---------------------------------------------------------------------------
# Empreinte / objets porteurs de secret
# ---------------------------------------------------------------------------


def fingerprint(value: str) -> str:
    """Les 4 derniers caractères, ou ``****`` pour une valeur trop courte."""
    if not isinstance(value, str) or len(value) < MIN_FINGERPRINT_LENGTH:
        return MASKED_FINGERPRINT
    return value[-4:]


class Secret:
    """Valeur déchiffrée en mémoire.

    ``repr``/``str``/``format``/``f-string`` ne rendent jamais la valeur : un
    ``log.info("%s", secret)`` ou un traceback ne peut donc pas la fuiter. Le
    seul chemin d'accès est ``reveal()``, explicite et grep-able.
    """

    __slots__ = ("profile", "name", "_value")

    def __init__(self, profile: str, name: str, value: str) -> None:
        self.profile = profile
        self.name = name
        self._value = value

    def reveal(self) -> str:
        return self._value

    def __repr__(self) -> str:
        return f"Secret(profile={self.profile!r}, name={self.name!r}, value=<redacted>)"

    __str__ = __repr__

    def __format__(self, _spec: str) -> str:
        return repr(self)

    def __reduce__(self):  # pickle/copy ne doivent pas sérialiser la valeur
        raise TypeError("Secret n'est pas sérialisable")


@dataclass(frozen=True)
class SecretMeta:
    """Métadonnées publiques d'un secret : jamais la valeur."""

    profile: str
    name: str
    fingerprint: str
    updated_at: str
    used_by: tuple = ()

    def to_dict(self) -> dict:
        return {
            "name": self.name,
            "fingerprint": self.fingerprint,
            "updated_at": self.updated_at,
            "used_by": list(self.used_by),
        }


@dataclass(frozen=True)
class KeyMaterial:
    key: bytes
    source: str
    secure_permissions: bool = True


# ---------------------------------------------------------------------------
# Validation
# ---------------------------------------------------------------------------


def validate_profile(profile: Any) -> str:
    if not isinstance(profile, str) or not profile.strip():
        raise VaultInvalid("profil requis (chaîne non vide)")
    value = profile.strip()
    if not _PROFILE_RE.match(value):
        raise VaultInvalid(
            "profil invalide : 1 à 64 caractères parmi [a-z0-9._-], "
            "commençant par une lettre minuscule ou un chiffre"
        )
    return value


def validate_name(name: Any) -> str:
    if not isinstance(name, str) or not name.strip():
        raise VaultInvalid("nom requis (chaîne non vide)")
    value = name.strip()
    if not _NAME_RE.match(value):
        raise VaultInvalid(
            "nom invalide : 1 à 128 caractères parmi [A-Za-z0-9._:-], "
            "commençant par une lettre ou un chiffre"
        )
    return value


def validate_value(value: Any) -> str:
    """Valide **sans jamais recopier** la valeur dans le message d'erreur."""
    if not isinstance(value, str):
        raise VaultInvalid("valeur invalide : chaîne attendue")
    if not value:
        raise VaultInvalid("valeur vide : refusée")
    if len(value.encode("utf-8")) > MAX_VALUE_BYTES:
        raise VaultInvalid(f"valeur trop longue : maximum {MAX_VALUE_BYTES} octets")
    return value


def validate_used_by(used_by: Any) -> tuple:
    if used_by is None:
        return ()
    if not isinstance(used_by, (list, tuple)):
        raise VaultInvalid("used_by invalide : liste de chaînes attendue")
    if len(used_by) > _MAX_USED_BY:
        raise VaultInvalid(f"used_by invalide : maximum {_MAX_USED_BY} entrées")
    out = []
    for item in used_by:
        if not isinstance(item, str) or not _USED_BY_RE.match(item.strip()):
            raise VaultInvalid(
                "used_by invalide : chaque entrée doit faire 1 à 64 caractères "
                "parmi [A-Za-z0-9._:-]"
            )
        out.append(item.strip())
    return tuple(dict.fromkeys(out))


# ---------------------------------------------------------------------------
# Emplacement + clé
# ---------------------------------------------------------------------------


def vault_dir() -> Path:
    """``$ONYX_VAULT_DIR`` sinon ``$HERMES_HOME/vault`` sinon ``~/.hermes/vault``.

    Hors du dépôt Hermes : un ``hermes update`` ne peut pas l'écraser.
    """
    override = os.environ.get("ONYX_VAULT_DIR", "").strip()
    if override:
        return Path(override).expanduser()
    home = os.environ.get("HERMES_HOME", "").strip()
    root = Path(home).expanduser() if home else Path.home() / ".hermes"
    return root / "vault"


def _check_permissions(path: Path) -> bool:
    try:
        mode = stat.S_IMODE(path.stat().st_mode)
    except OSError:
        return False
    return not (mode & 0o077)


def load_key() -> KeyMaterial:
    """Clé Fernet depuis ``$ONYX_VAULT_KEY``, ``$ONYX_VAULT_KEY_FILE`` ou le fichier par défaut.

    Lève ``VaultUnavailable`` (message actionnable, sans matériel de clé) quand
    elle manque ou est malformée : le plugin répond alors 503 au lieu de
    faire tomber le Dashboard.
    """
    env_key = os.environ.get("ONYX_VAULT_KEY", "").strip()
    if env_key:
        return KeyMaterial(_parse_key(env_key, "ONYX_VAULT_KEY"), "env:ONYX_VAULT_KEY", True)

    key_file = os.environ.get("ONYX_VAULT_KEY_FILE", "").strip()
    if key_file:
        path = Path(key_file).expanduser()
        source = f"file:{path}"
    else:
        path = vault_dir() / KEY_FILENAME
        source = f"file:{path}"

    try:
        raw = path.read_text(encoding="ascii").strip()
    except FileNotFoundError:
        raise VaultUnavailable(
            f"clé de chiffrement absente ({path}) — le coffre est désactivé, "
            f"aucun secret n'est lisible ni stocké. {KEY_HELP}"
        ) from None
    except OSError:
        raise VaultUnavailable(
            f"clé de chiffrement illisible ({path}) — vérifier les droits. {KEY_HELP}"
        ) from None
    if not raw:
        raise VaultUnavailable(f"clé de chiffrement vide ({path}). {KEY_HELP}")

    secure = _check_permissions(path)
    if not secure:
        log.warning(
            "onyx-vault: clé %s lisible par le groupe ou tous (chmod 600 recommandé)", path
        )
    return KeyMaterial(_parse_key(raw, str(path)), source, secure)


def _parse_key(raw: str, origin: str) -> bytes:
    try:
        Fernet(raw.encode("ascii"))
    except Exception:
        raise VaultUnavailable(
            f"clé de chiffrement invalide ({origin}) — attendu : 32 octets en base64 urlsafe "
            f"tel que produit par Fernet.generate_key(). {KEY_HELP}"
        ) from None
    return raw.encode("ascii")


# ---------------------------------------------------------------------------
# Stockage
# ---------------------------------------------------------------------------


def _now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


_SCHEMA = """
CREATE TABLE IF NOT EXISTS secrets (
    profile     TEXT NOT NULL,
    name        TEXT NOT NULL,
    ciphertext  BLOB NOT NULL,
    fingerprint TEXT NOT NULL,
    used_by     TEXT NOT NULL DEFAULT '[]',
    created_at  TEXT NOT NULL,
    updated_at  TEXT NOT NULL,
    PRIMARY KEY (profile, name)
);
CREATE TABLE IF NOT EXISTS meta (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
"""


class Vault:
    """Coffre SQLite + Fernet. Une connexion par opération, un verrou par instance."""

    def __init__(self, db_path: Optional[Path] = None, key: Optional[KeyMaterial] = None) -> None:
        self.key = key or load_key()
        self.db_path = Path(db_path).expanduser() if db_path else vault_dir() / DB_FILENAME
        self._fernet = Fernet(self.key.key)
        self._lock = threading.Lock()
        self._init_schema()

    # -- interne ---------------------------------------------------------
    def _connect(self):
        cx = sqlite3.connect(str(self.db_path), timeout=5.0)
        cx.execute("PRAGMA busy_timeout = 5000")
        cx.execute("PRAGMA synchronous = FULL")
        return cx

    def _init_schema(self) -> None:
        directory = self.db_path.parent
        directory.mkdir(parents=True, exist_ok=True)
        try:
            os.chmod(directory, 0o700)
        except OSError:
            pass
        with closing(self._connect()) as cx:
            cx.executescript(_SCHEMA)
            cx.execute(
                "INSERT INTO meta (key, value) VALUES ('schema_version', '1') "
                "ON CONFLICT(key) DO NOTHING"
            )
            cx.commit()
        try:
            os.chmod(self.db_path, 0o600)
        except OSError:
            pass

    def _seal(self, profile: str, name: str, value: str) -> bytes:
        """Chiffre la valeur *liée* à (profil, nom) pour détecter un déplacement de ligne."""
        envelope = json.dumps(
            {"v": 1, "p": profile, "n": name, "s": value}, ensure_ascii=False, separators=(",", ":")
        ).encode("utf-8")
        return self._fernet.encrypt(envelope)

    def _open(self, profile: str, name: str, blob: bytes) -> str:
        try:
            raw = self._fernet.decrypt(bytes(blob))
        except InvalidToken:
            raise VaultError(
                "déchiffrement impossible : clé différente de celle du coffre, ou donnée altérée"
            ) from None
        try:
            envelope = json.loads(raw)
            if envelope.get("p") != profile or envelope.get("n") != name:
                raise VaultError("chiffré incohérent : il ne correspond pas à ce secret")
            value = envelope["s"]
        except (ValueError, KeyError, TypeError):
            raise VaultError("contenu du coffre illisible") from None
        if not isinstance(value, str):
            raise VaultError("contenu du coffre illisible")
        return value

    # -- API -------------------------------------------------------------
    def put(self, profile: str, name: str, value: str, used_by: Iterable[str] | None = None) -> dict:
        """Écrit/remplace un secret. Retourne ses métadonnées (dont l'empreinte)."""
        profile = validate_profile(profile)
        name = validate_name(name)
        value = validate_value(value)
        declared = validate_used_by(used_by)
        fp = fingerprint(value)
        ciphertext = self._seal(profile, name, value)
        now = _now()

        with self._lock, closing(self._connect()) as cx:
            row = cx.execute(
                "SELECT used_by FROM secrets WHERE profile = ? AND name = ?", (profile, name)
            ).fetchone()
            created = row is None
            if used_by is None and row is not None:
                try:
                    declared = tuple(json.loads(row[0] or "[]"))
                except ValueError:
                    declared = ()
            payload = json.dumps(list(declared))
            cx.execute(
                """
                INSERT INTO secrets (profile, name, ciphertext, fingerprint, used_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(profile, name) DO UPDATE SET
                    ciphertext  = excluded.ciphertext,
                    fingerprint = excluded.fingerprint,
                    used_by     = excluded.used_by,
                    updated_at  = excluded.updated_at
                """,
                (profile, name, ciphertext, fp, payload, now, now),
            )
            cx.commit()

        log.info("onyx-vault: secret écrit (profil=%s, nom=%s)", profile, name)
        return {
            "profile": profile,
            "name": name,
            "fingerprint": fp,
            "updated_at": now,
            "used_by": list(declared),
            "created": created,
        }

    def list(self, profile: str) -> list:
        profile = validate_profile(profile)
        with self._lock, closing(self._connect()) as cx:
            rows = cx.execute(
                "SELECT name, fingerprint, used_by, updated_at FROM secrets "
                "WHERE profile = ? ORDER BY name COLLATE NOCASE",
                (profile,),
            ).fetchall()
        out = []
        for name, fp, used_by, updated_at in rows:
            try:
                declared = tuple(json.loads(used_by or "[]"))
            except ValueError:
                declared = ()
            out.append(
                SecretMeta(
                    profile=profile, name=name, fingerprint=fp, updated_at=updated_at, used_by=declared
                )
            )
        return out

    def revoke(self, profile: str, name: str) -> bool:
        profile = validate_profile(profile)
        name = validate_name(name)
        with self._lock, closing(self._connect()) as cx:
            cursor = cx.execute(
                "DELETE FROM secrets WHERE profile = ? AND name = ?", (profile, name)
            )
            cx.commit()
            removed = cursor.rowcount > 0
        if removed:
            log.info("onyx-vault: secret révoqué (profil=%s, nom=%s)", profile, name)
        return removed

    def read(self, profile: str, name: str) -> Secret:
        """Déchiffre un secret (usage interne : consommateur Onyx). Jamais exposé en HTTP."""
        profile = validate_profile(profile)
        name = validate_name(name)
        with self._lock, closing(self._connect()) as cx:
            row = cx.execute(
                "SELECT ciphertext FROM secrets WHERE profile = ? AND name = ?", (profile, name)
            ).fetchone()
        if row is None:
            raise VaultNotFound(f"secret inconnu : {name!r}")
        return Secret(profile, name, self._open(profile, name, row[0]))

    def count(self) -> int:
        with self._lock, closing(self._connect()) as cx:
            return int(cx.execute("SELECT COUNT(*) FROM secrets").fetchone()[0])


# ---------------------------------------------------------------------------
# Singleton paresseux : une clé ajoutée après coup est prise en compte au
# prochain appel, sans redémarrer le serveur.
# ---------------------------------------------------------------------------

_vault: Optional[Vault] = None
_vault_lock = threading.Lock()


def get_vault() -> Vault:
    global _vault
    with _vault_lock:
        if _vault is None:
            _vault = Vault()
        return _vault


def reset_vault() -> None:
    """Oublie l'instance (tests, ou rotation de clé)."""
    global _vault
    with _vault_lock:
        _vault = None
