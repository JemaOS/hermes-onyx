# `onyx-record` — serveur de la démonstration → skill (lot 3)

Second plugin Hermes d'Onyx, à côté de `onyx-vault`. Il sert trois routes et
n'écrit rien : un bail d'enregistrement, la forme canonique d'un journal
d'actions, et un brouillon de compétence **identique à l'octet** à celui que
l'app calcule déjà (`core/.../skill/SkillDraft.kt`, golden `facture.md`).

| Route | Méthode | Rôle |
|---|---|---|
| `/api/plugins/onyx-record/skills/record/start` | POST | ouvre un bail (600 s, 120 s de collecte) |
| `/api/plugins/onyx-record/skills/record/stop` | POST | ferme le bail, rend le journal canonique (ou `journal: null` = abandon) |
| `/api/plugins/onyx-record/skills/draft` | POST | brouillon Markdown + étapes à relire — sans état |
| `/api/plugins/onyx-record/health` | GET | état, jamais un journal ni un identifiant |
| `/api/plugins/onyx-record/info` | GET | version, préfixe, routes, bornes |

## Arborescence

```
onyx-record-plugin/
├── dashboard/                 # ce que Hermes lit (seul dossier à installer)
│   ├── manifest.json
│   ├── plugin_api.py          # router = APIRouter() — un seul mode de montage
│   ├── onyx_record.py         # validation, bornes, machine à états, brouillon
│   └── dist/index.js          # module vide (onglet masqué)
├── tests/
│   ├── support.py             # charge les modules comme le Dashboard
│   ├── journal_fixture.py     # le journal du golden Kotlin + celui de la spec
│   ├── test_draft_golden.py   # bytes == facture.md, déterminisme, secret
│   ├── test_record_core.py    # machine à états, refus, bornes, expiration
│   ├── test_plugin_api.py     # contrat HTTP sur une app FastAPI nue
│   ├── test_dashboard_mount.py# vrai Dashboard, HERMES_HOME jetable
│   └── _mount_child.py        # le serveur jetable (un processus par scénario)
├── INSTALL.md                 # copie, plugins.enabled, vérification, désinstallation
├── ECARTS.md                  # les contradictions de la spec, tranchées et justifiées
└── README.md
```

## Tests

85 tests, une seule commande, un seul processus :

```bash
PY=$(ls -d ~/.hermes/tools/python-3.14.7*/bin/python3 | head -1)
cd ~/.hermes/hermes-agent
PYTHONPATH=/home/john/.hermes/hermes-agent "$PY" -m unittest discover \
    -s /home/john/Bureau/onyx-record-plugin/tests \
    -t /home/john/Bureau/onyx-record-plugin/tests -v
```

Le montage réel tourne dans des processus enfants : le gateway en cours n'est ni
touché ni redémarré, et `~/.hermes` n'est jamais écrit.

## Ce que le plugin ne fait pas

Il n'écrit aucun skill (`POST api/skills` le fait), ne crée aucune routine,
n'observe ni écran ni pont (le journal est sur le téléphone), et ne garde aucun
journal après le brouillon. Voir `INSTALL.md` §8 pour les limites de sécurité et
`ECARTS.md` pour les points où la spec se contredit.
