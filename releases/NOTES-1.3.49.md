# Onyx 1.3.49 — le navigateur de l'agent, sur le téléphone

**Ce que l'app sait faire maintenant** : voir en direct la page que l'agent ouvre, prendre la
main pour se connecter à sa place, puis lui rendre la page.

## Le principe

L'agent appelle `browser_request_takeover` et son tour **se met en pause** (15 min). L'app
reçoit une image de la page (3 img/s, 8 quand tu conduis), tu tapes sur l'écran, tu fais défiler,
tu écris dans un champ **masqué** — et ce que tu tapes part vers la page, jamais vers la
conversation ni vers le modèle. Quand tu rends la main, l'agent repart de là où il était.

- **Réglages → Navigateur** : la ligne n'existe que si le plugin `onyx-browser` répond sur le
  serveur (`onyx-browser/health`). Pas de ligne morte, pas de bouton qui ne fait rien.
- **Routes** : `observe` (ticket à usage unique, 30 s), `lease` (`acquire`/`release`, avec
  `epoch`), `health`. Aucun corps ne peut transporter un mot de passe.
- **Saisies refusées côté serveur** sans le bail (`no_lease`) et sur une image périmée (`stale`) —
  une frappe calculée sur une capture dépassée n'atteint pas la page.
- **Viewport téléphone** (390×844, UA mobile) posé avant la frappe : le site sert sa version
  mobile au lieu d'un bureau écrasé.
- **Coffre** : après la main rendue, l'app **propose** d'enregistrer ce qui a été tapé
  (`VaultApi.put`, origine liée au secret, valeur effacée aussitôt). Elle ne l'écrit jamais
  d'elle-même.

## Ce que cette version ne fait pas encore

- Le **remplissage depuis le coffre** (`fill`) : il demande une route serveur privilégiée qui
  n'existe pas encore. Le téléphone ne renvoie donc pas de secret au plugin.
- La **carte dans le fil** : la demande de l'agent apparaît aujourd'hui comme une étape d'outil.
- Le **plugin** n'est pas dans l'APK : il vit côté Hermes (`plugins/onyx-browser/`). Sans lui,
  la ligne de Réglages reste absente — c'est voulu.

## Vérifications

- `:core` 1 955 + `:app` 2 248 = **4 203 tests, 0 échec** (XML frais).
- `BrowserScreenTest` (11 tests) : `wss`/`ws` selon le schéma, corps des gestes (`seq`, `css`),
  fractions bornées, plafond de 2 000 caractères, décodage d'une trame (une trame incomplète ne
  devient jamais une image), hôte/empreinte, champ masqué, écriture au coffre liée à l'origine.
- Plugin, sur la machine : `health` → 200 en direct **et** à travers `onyx-one`, `observe` → ticket
  30 s, `selftest.py` → 20/20.

## Fichiers

`app/.../ui/browser/BrowserScreen.kt`, `core/.../data/net/BrowserScreenApi.kt`,
`core/.../data/net/BrowserScreenClient.kt`, `plugins/onyx-browser/*`, `tools/onyx-one.py`
(préfixe `/onyx-browser/`), `docs/PLAN-NAVIGATEUR-PARTAGE.md`.
