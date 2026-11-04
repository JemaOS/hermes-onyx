# Écarts à `docs/SPEC-SKILLS-RECORD.md` — et pourquoi

Chaque écart ci-dessous est **mesuré**, pas supposé : il est couvert par un test,
et le code porte un commentaire à l'endroit concerné. La règle de lecture est
simple : quand la spec se contredit, on garde la contrainte du lot (le golden
Kotlin est la référence d'octets) et on écrit la contradiction, au lieu de la
contourner en silence.

---

## 1. La forme du Markdown de §5 n'est pas celle du convertisseur livré

**Spec §5** montre un `markdown` avec front matter, `## Quand l'utiliser`,
`## Procédure` et des pas en français (« Toucher **Nouveau dossier**. »).

**Le convertisseur Kotlin livré** (`core/.../skill/SkillDraft.kt`) et son golden
(`core/src/test/resources/skill/invoice.md`) produisent autre chose : un `# titre`,
une phrase d'intention, des pas **en anglais** (`Navigate to`, `Tap`, `Type`,
`Scroll down`, `Wait`, `Press`) et une note `> À relire : l'étape N.` Aucun front
matter, aucune section.

**Ce que fait le plugin** : le Markdown est celui du Kotlin, à l'octet
(`tests/test_draft_golden.py` compare au fichier de l'app). La contrainte du lot
(« le convertisseur Kotlin est la référence d'octets ») et §12.1 (même entrée,
même sortie) l'emportent sur un exemple illustratif. L'enveloppe de réponse de §5
est conservée (`name`, `title`, `steps`, `markdown`, `review`, `warnings`,
`journal_bytes`) ; `title` est le texte du `#`, `steps` le nombre de pas numérotés.

> Conséquence pour l'écran : `SkillEditorScreen` affichera un brouillon dans la
> forme du convertisseur de l'app — pas celle de l'exemple de §5. La spec §5
> elle-même invoque « ce que l'éditeur annonce déjà » ; c'est l'app qui tranche,
> et l'app a déjà tranché par ce fichier.

## 2. `amount` d'un `scroll` : des pixels, pas des crans

**Spec §2.2** : « Crans, 1 à 100 ». **Spec §5** : « (3 crans) ».

**Le convertisseur de référence** écrit `Scroll down 760 px` et le golden du lot
somme `540 + 220 = 760` — deux valeurs impossibles sous une borne de 100. Le
serveur accepte donc `amount` comme une **longueur en pixels** (`0..100000`,
signe ignoré, zéro abandonné par la fusion), et le pas reste `Scroll down 760 px`.
Un journal conforme à l'exemple de §2.3 (`amount: 3`) produit `Scroll down 3 px`
et non « 3 crans ».

## 3. Un `kind` inconnu est refusé, pas converti en `unsupported_kind`

**Spec §2.2** ferme la liste des sept `kind` (« Rien d'autre ») et **§4** exige
`422 bad_journal` pour toute violation de §2. **Spec §5** demande qu'un `kind`
inconnu devienne un pas « à relire » de raison `unsupported_kind`.

Le refus l'emporte (`422 bad_journal`) : c'est la règle de validation, et elle est
la même sur `stop` et sur `draft`. `unsupported_kind` reste dans la liste fermée
des raisons mais n'est jamais émis — le convertisseur garde la branche par
symétrie avec le Kotlin, elle est inatteignable par les routes.

`long_wait` est de même **inatteignable** : §2.2 borne `ms` à 30000, donc une
attente « longue » n'existe pas dans un journal accepté, et le convertisseur ne
marque pas les attentes (le Kotlin ne le fait pas non plus).

## 4. La borne de 262144 octets et le refus `413` se recouvrent

**Spec §2.4** : au-delà de 262144 octets, les plus anciens événements sont
abandonnés (`truncated`, `dropped`). **Spec §4** : au-delà de 262144 octets
**avant** bornage, `413 journal_too_large`.

Les deux ne peuvent pas s'appliquer au même envoi : la forme canonique est un
sous-ensemble du journal reçu (champs inconnus retirés), donc si le reçu dépasse
la borne, la route a déjà répondu `413`. Le plugin fait donc les deux, dans cet
ordre : `413` sur le journal reçu, puis borne de taille sur le **canonique** —
une garde de second rang, réellement testée au niveau de `validate`
(`tests/test_record_core.py::TestBounds::test_byte_bound_drops_from_the_front`)
plutôt que par une route qui ne peut plus la déclencher.

## 5. Décisions là où la spec est muette

| Situation | Choix | Pourquoi |
|---|---|---|
| Un journal refusé (`422`, `413`, `409 bridge_mismatch`) | **le bail reste ouvert** | une faute d'envoi ne doit pas coûter l'enregistrement ; le test vérifie qu'un `stop` correct passe ensuite |
| Journal en collecte expiré (> 120 s) | `410 session_expired` | le serveur a connu ce bail et sa fenêtre est finie ; `409 unknown_session` est réservé aux identifiants jamais émis |
| Bail expiré puis `start` | `409 already_recording` (le client adopte) | le journal peut encore arriver dans la fenêtre de collecte ; un nouveau bail le jetterait |
| `journal.session_id` ≠ `session_id` de l'appel | `422 bad_journal` | c'est une incohérence du journal, pas un conflit d'état |
| `type` non secret avec `text` vide | pas converti en erreur, pas « à relire » `texte vide` → code `no_label` | la liste fermée de la spec n'a pas `empty_text` ; le texte de la ligne reste celui du Kotlin |
| `at_ms` égaux | acceptés | deux événements peuvent tomber dans la même milliseconde ; seul un recul est refusé |
| `review[]` d'un pas secret | présent (`secret_field`) | c'est ce que fait l'exemple de §5 ; le Markdown, lui, garde la note du Kotlin et ne le cite pas dans « À relire » |
| Ordre de `review[]` | ordre du brouillon | la table des champs le dit ; l'exemple de §5 liste 9 puis 4, ce qui contredit sa propre table |
| `warnings` | toujours présent, liste vide si rien n'a bougé | un client n'a pas à distinguer « absent » de « vide » |
| `stop` canonique, `started_at` | celui du téléphone | il ancre les `at_ms` ; `duration_ms`, lui, est celui du serveur (D3) |
| Champ `title` du journal | additif, optionnel | le convertisseur de référence en dépend (`ActionJournal.title`) ; sans lui, `facture.md` est irreproductible |
| `text_truncated` | jamais émis | `text` > 2000 est un `422` (§2.4), pas une troncature |

## 6. `drafted → start` n'existe pas côté serveur

**Spec §7** dessine `drafted ── start(200) ──► recording`, alors que §7.2 exige
`409 journal_pending` pour `stopped → start`. Le serveur ne connaît pas `drafted`
(D4 : `skills.draft` est sans état), donc il ne peut pas distinguer les deux.

Le client résout la contradiction avec ce que §3 prévoit déjà : après avoir
converti (ou jeté) le journal, il envoie `start` avec `"discard_pending": true`.
C'est le seul chemin qui rende la flèche de §7 vraie, et il ne demande aucune
route de plus.

## 7. `start` : deux champs d'erreur en plus

`409 already_recording` porte `session_id` **et** `deadline_at`, `409
journal_pending` porte `session_id` et `expires_at`. La spec ne demande que
`session_id` ; §7.2 dit que le client « adopte le `session_id` et le
`deadline_at` rendus », donc le second doit être là. Les deux corps restent sous
300 caractères.

## 8. Le montage à chaud a un ordre, et il est mesuré

Hors spec, mais utile : appeler `_mount_plugin_api_routes()` **après** l'import du
serveur ajoute les routes derrière le repli `/api/plugins/{name}/{path:path}`, qui
les masque — mesuré dans ce lot : `404 {"detail":"Not Found"}` sur chaque route
pourtant présente dans la table. Le seul montage autoritaire est celui du
démarrage (ou le montage à chaud de Hermes, qui insère les routes *avant* le
repli). C'est pourquoi `tests/test_dashboard_mount.py` fait tourner un vrai
Dashboard dans deux processus jetables au lieu d'un `TestClient` in-process.

Le plugin n'a par ailleurs **qu'un seul mode de montage** (l'`APIRouter` exporté,
sans variable d'environnement ni enregistrement direct sur l'application) : le
défaut du plugin du coffre — deux modes, dont un non autoritaire qui cassait des
tests partagés — n'est pas reproductible ici.
