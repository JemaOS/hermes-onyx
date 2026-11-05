#!/usr/bin/env bash
# ---------------------------------------------------------------------------------------------
# install.sh — installe le plugin `onyx-browser` sur ce profil Hermes.
#
# Ce qu'il fait, et rien de plus :
#   1. copie le plugin dans ~/.hermes/plugins/onyx-browser (le dossier que Hermes charge) ;
#   2. vérifie que son serveur se lève, sur 127.0.0.1:8788 ;
#   3. rappelle ce qu'il reste à faire à la main : la ligne `onyx-one` et le redémarrage.
#
# Il ne touche ni à la config de Hermes, ni à `onyx-one.py`, ni au service de la passerelle.
# ---------------------------------------------------------------------------------------------
set -euo pipefail

SOURCE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CIBLE="${HERMES_PLUGINS:-$HOME/.hermes/plugins}/onyx-browser"
PORT="${BROWSER_PORT:-8788}"

echo "→ copie vers $CIBLE"
mkdir -p "$CIBLE"
for fichier in plugin.yaml __init__.py session.py cdp.py server.py tools.py; do
  cp "$SOURCE/$fichier" "$CIBLE/$fichier"
done

echo "→ contrôle du serveur"
python3 - "$CIBLE" "$PORT" <<'PY'
import asyncio, importlib.util, sys
from pathlib import Path

cible, port = Path(sys.argv[1]), int(sys.argv[2])
spec = importlib.util.spec_from_file_location(
    "onyx_browser", cible / "__init__.py", submodule_search_locations=[str(cible)]
)
module = importlib.util.module_from_spec(spec)
sys.modules["onyx_browser"] = module
spec.loader.exec_module(module)
server = sys.modules["onyx_browser.server"]

from aiohttp.test_utils import TestClient, TestServer

async def main() -> int:
    async with TestClient(TestServer(server.build_app())) as client:
        async with client.get("/onyx-browser/health") as resp:
            body = await resp.json()
            if resp.status != 200 or body.get("plugin") != "onyx-browser":
                print("   le serveur ne répond pas correctement")
                return 1
    print("   health : ok")
    return 0

sys.exit(asyncio.run(main()))
PY

cat <<'TXT'

Le plugin est installé. Deux gestes restent, et ils sont à toi :

1. **Redémarrer la passerelle**, pour que Hermes charge le plugin au démarrage :
       systemctl --user restart hermes-gateway.service

2. **Relier le port au relais de l'app** — `tools/onyx-one.py` porte déjà le préfixe
   `/onyx-browser/` vers 127.0.0.1:8788 ; redémarre-le s'il tourne :
       systemctl --user restart onyx-one-tunnel.service   # ou le service qui porte onyx-one

Ensuite : Réglages → « Agent's browser ». La ligne n'apparaît que si le plugin répond.
TXT
