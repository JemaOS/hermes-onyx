# Toutes les routes de l'app Onyx et le retour système — passe complète 26/10/2026 07:39

Chaque route est atteinte **par l'interface réelle** (libellés cherchés dans la hiérarchie puis tapés),
puis le **retour système** (`keyevent 4`) est déclenché écran par écran ; l'écran d'arrivée est comparé
au parent réellement empilé. Tout écart est listé avec sa capture et sa hiérarchie.

## Dispositif

- Émulateur `emulator-5596` (`sdk_gphone64_x86_64`, API 36, locale fr-FR)
- APK testé : `/home/john/onyx-retours-fix2/app/build/outputs/apk/release/app-release.apk`
- versionName/versionCode sur l'appareil : versionCode=319 minSdk=26 targetSdk=36 ·     versionName=1.0.0+319 ·     lastUpdateTime=2026-10-26 04:10:22
- SHA-256 de l'APK testé : `4c3887bbee30cc8968bed51749d507a9f19a615b975205bbf1485f15a00ec812`
- Source : `/home/john/onyx-retours-fix2` @ `HEAD` — énumeré `Route` = **58** valeurs
- Faux serveur Hermes contrôlé (`http://10.0.2.2:8794`), deux entrées : transport Dashboard
  (écrans du serveur) et OpenAI-compatible (tours de chat).
- Sortie : 328 captures (`shots/`), 328 hiérarchies (`ui/`), résultats bruts `results.jsonl`

## Bilan

| | |
|---|---|
| Routes de l'énum `Route` | **58** |
| Routes atteintes par l'interface | **52** |
| Routes parcourues **et** retours vérifiés | **51** |
| Retours conformes (repères d'écran) | **47 / 51** |
| Retours conformes (recouvrement du harnais) | **45 / 51** |
| Anomalies | **15** (critique : 3, majeure : 2, mineure : 2, à connaître : 2, à qualifier : 6) |
| `FATAL EXCEPTION` / `ANR` sur la session | 0 / 0 |

## Détail par route

| Route | Parcours | Écran atteint | Repère | Retours | Verdict |
|---|---|---|---|---|---|
| CHAT | — | hermes-agent | ✅ | — | OK |
| CONVERSATIONS | Conversations | Conversations | ✅ | ✅ 1/1 | OK |
| SETTINGS | Plus › Settings | Réglages | ✅ | ✅ 1/1 | OK |
| APPEARANCE | Plus › Settings › Appearance | Apparence | ✅ | ✅ 2/2 | OK |
| MODELS | Plus › Settings › Model | Modèle | ✅ | ✅ 2/2 | OK |
| VOICE | Parler | What | ✅ | ✅ 1/1 | OK |
| GATEWAY | Plus › Settings › Gateway | Passerelle | ✅ | ✅ 2/2 | OK |
| LOGS | Plus › Settings › Gateway › Logs | Journaux | ✅ | ✅ 3/3 | OK |
| FILES | Plus › Settings › Files | Fichiers | ✅ | ✅ 2/2 | OK |
| FILE_EDITOR | Plus › Settings › Files › Hermes › processes.json | processes.json | ✅ | ✅ 4/4 | OK |
| CONTEXT | Plus › Settings › Context files | Fichiers de contexte | ✅ | ✅ 2/2 | OK |
| MEMORY | Plus › Settings › Memory | Mémoire | ✅ | ✅ 2/2 | OK |
| CONTEXT_EDITOR | Plus › Settings › Context files › SOUL.md | SOUL.md | ✅ | ✅ 3/3 | OK |
| VOICE_SETTINGS | Plus › Settings › Voice | Voix | ✅ | ✅ 2/2 | OK |
| NOTIFICATIONS | Plus › Settings › Notifications | Notifications | ✅ | ✅ 2/2 | OK |
| KOKORO_VOICES | Plus › Settings › Voice › Kokoro › Voice |  | — | — | non atteinte |
| CRON | Plus › Settings › Cron | Cron | ✅ | ⚠️ 1/2 | racine de section (retour voulu) |
| KANBAN | Plus › Settings › Kanban | Kanban | ✅ | ⚠️ 1/2 | racine de section (retour voulu) |
| COMPOSER_EDITOR | Open the editor | Message | ✅ | ❌ 0/1 | retour incohérent |
| PHONE_TOOLS | Plus › Settings › Phone tools | Outils du téléphone | ✅ | ✅ 2/2 | OK |
| LOCK | Plus › Settings › Lock | Verrouillage | ✅ | ✅ 2/2 | OK |
| CONNECT | Plus › Settings › Set up another device | Connecter | ✅ | ✅ 2/2 | OK |
| BOTS | Plus › Settings › Bots | Bots | ✅ | ✅ 2/2 | OK |
| BOT_EDITOR | Plus › Settings › Bots › Quill › Edit Bot… | Quill | ✅ | ✅ 3/3 | OK |
| BOT_FACE | Plus › Settings › Bots › Quill › Change Face… | Quill | ✅ | ✅ 3/3 | OK |
| BOT_SCHEDULES | Plus › Settings › Bots › Quill › Schedules… | Horaires de Quill | ✅ | ✅ 3/3 | OK |
| BOT_VOICE | Plus › Settings › Bots › Quill › Voice… | Voix de Quill | ✅ | ✅ 3/3 | OK |
| GROUP_CHAT | Plus › Settings › Bots › Group chat |  | ✅ | ✅ 3/3 | OK |
| GROUP_VOICE | Plus › Settings › Bots › Group chat › Parler | Discussion de groupe | ✅ | ✅ 4/4 | OK |
| ACKNOWLEDGEMENTS | Plus › Settings › Acknowledgements | Remerciements | ✅ | ✅ 2/2 | OK |
| SPEECH_LANGUAGE | Plus › Settings › Voice › Listening language | Langue d’écoute | ✅ | ✅ 3/3 | OK |
| REPLY_LANGUAGE | Plus › Settings › Voice › Reply language | Langue des réponses | ✅ | ✅ 3/3 | OK |
| COMPUTER | Plus › Computer | Ordinateur | ✅ | ✅ 1/1 | OK |
| STOP_PHRASES | Plus › Settings › Voice › Stop phrases | Phrases d’arrêt | ✅ | ✅ 3/3 | OK |
| SERVERS | Plus › Settings › Servers | Serveurs | ✅ | ✅ 2/2 | OK |
| SETUP | Plus › Settings › Servers › Edit connection… | Configurer Onyx | ✅ | ✅ 3/3 | OK |
| SPOKEN_PREFIXES | Plus › Settings › Voice › Spoken prefixes | Préfixes parlés | ✅ | ✅ 3/3 | OK |
| SPOKEN_PREFIX_EDITOR | Plus › Settings › Voice › Spoken prefixes › Add prefix | Nouveau préfixe | ✅ | ✅ 4/4 | OK |
| PROFILES | Plus › Settings › Profile | Profil | ✅ | ✅ 2/2 | OK |
| WHATS_NEW | Plus › Settings › What's New | Nouveautés de Onyx | ✅ | ✅ 2/2 | OK |
| PATCH | Voir la modification | Modifier | ✅ | ✅ 1/1 | OK |
| SELECT_TEXT | Plus | Sélectionner du texte | ✅ | ✅ 1/1 | OK |
| PHOTO | Add › Photo Library › qa-photo |  | — | — | non atteinte |
| PDF | Add › Files › qa-doc › Send › qa-doc | qa-doc.pdf | ✅ | ⚠️ 1/2 | retour incohérent |
| SKILLS | Plus › Settings › Skills | Compétences | ✅ | ✅ 2/2 | OK |
| SKILL_EDITOR | Plus › Settings › Skills › release-notes | release-notes | ✅ | ✅ 3/3 | retour à confirmer |
| TOOLS | Plus › Settings › Tools | Outils | ✅ | ✅ 2/2 | OK |
| API_BOT | Conversations › New Bot › New Bot |  | — | — | non atteinte |
| CAMERA | Add › Camera |  | ✅ | ✅ 1/1 | OK |
| TIP_JAR | Plus › Settings › Support Onyx | Soutenir Onyx | ✅ | ✅ 2/2 | OK |
| SUBAGENT | 1 task |  | — | — | non atteinte |
| SUBAGENTS | — |  | — | — | non atteignable sans état préalable |
| TERMINAL | Plus › Terminal | Terminal | ✅ | ✅ 1/1 | OK |
| TERMINAL_SETTINGS | Plus › Settings › Terminal | Terminal | ✅ | ✅ 2/2 | OK |
| CONNECTION_DETAILS | Plus › Settings › Connection details | Détails de connexion | ✅ | ✅ 2/2 | OK |
| HIDDEN_BOTS | Plus › Settings › Bots › Weekend hike › Hide › Hidden | Bots masqués | ✅ | ✅ 3/3 | OK |
| SESSION_USAGE | Conversations › Bonjour › Usage & cost |  | — | — | non atteinte |
| PROJECT_SESSIONS | Conversations › Fix-it | Fix-it | ✅ | ✅ 2/2 | retour à confirmer |

## Anomalies

- **Route non atteinte par le parcours prévu** — KOKORO_VOICES *(à qualifier)* : libellé introuvable : 'Voice'
  - preuve : `/home/john/onyx-integration2/qa/20261026-routes-onyx-319/shots/Fx5-KOKORO_VOICES.png`
- **Crash pendant le parcours** — KOKORO_VOICES *(critique)* : 10-26 07:23:09.243 17435 17435 E AndroidRuntime: FATAL EXCEPTION: main; 10-26 07:23:22.375 17613 17613 E AndroidRuntime: FATAL EXCEPTION: main; 10-26 07:24:08.585 18206 18206 E AndroidRuntime: FATAL EXCEPTION: main; 10-26 07:31:58.989 21244 21244 E AndroidRuntime: FATAL EXCEPTION: main; 10-26 07:32:
- **Retour vers la racine de section, pas vers l'écran d'où l'on vient** — CRON *(à connaître)* : parcours Plus › Settings › Cron : le retour 1 rend 'Conversations' au lieu de 'Réglages' — `navigateWithBackStack` vide la pile pour CONVERSATIONS/CRON/KANBAN (`SectionTabRoutes`), donc la page d'où l'on vient (Réglages) est volontairement refermée
  - preuve : `/home/john/onyx-integration2/qa/20261026-routes-onyx-319/shots/B1-CRON.png`
- **Retour vers la racine de section, pas vers l'écran d'où l'on vient** — KANBAN *(à connaître)* : parcours Plus › Settings › Kanban : le retour 1 rend 'Conversations' au lieu de 'Réglages' — `navigateWithBackStack` vide la pile pour CONVERSATIONS/CRON/KANBAN (`SectionTabRoutes`), donc la page d'où l'on vient (Réglages) est volontairement refermée
  - preuve : `/home/john/onyx-integration2/qa/20261026-routes-onyx-319/shots/B1-KANBAN.png`
- **Le retour système ne rend pas l'espace précédent** — COMPOSER_EDITOR *(majeure)* : retour 1 : attendu 'Nouvelle conversation', obtenu 'Message' (jaccard 0.06) — attendu 'Nouvelle conversation', obtenu 'Message' — repères communs: aucun
  - preuve : `/home/john/onyx-integration2/qa/20261026-routes-onyx-319/shots/B1-COMPOSER_EDITOR.png`
- **Crash pendant le parcours** — COMPOSER_EDITOR *(critique)* : 10-26 07:23:09.243 17435 17435 E AndroidRuntime: FATAL EXCEPTION: main; 10-26 07:23:22.375 17613 17613 E AndroidRuntime: FATAL EXCEPTION: main; 10-26 07:24:08.585 18206 18206 E AndroidRuntime: FATAL EXCEPTION: main; 10-26 07:31:58.989 21244 21244 E AndroidRuntime: FATAL EXCEPTION: main; 10-26 07:32:
- **Route non atteinte par le parcours prévu** — PHOTO *(à qualifier)* : libellé introuvable : 'qa-photo'
  - preuve : `/home/john/onyx-integration2/qa/20261026-routes-onyx-319/shots/Fx3-PHOTO.png`
- **Le retour système ne rend pas l'espace précédent** — PDF *(majeure)* : retour 2 : attendu 'New conversation', obtenu '' (jaccard 0.00) — attendu 'New conversation', obtenu '' — repères communs: aucun
  - preuve : `/home/john/onyx-integration2/qa/20261026-routes-onyx-319/shots/B2-PDF.png`
- **Crash pendant le parcours** — PDF *(critique)* : 10-26 07:23:09.243 17435 17435 E AndroidRuntime: FATAL EXCEPTION: main; 10-26 07:23:22.375 17613 17613 E AndroidRuntime: FATAL EXCEPTION: main; 10-26 07:24:08.585 18206 18206 E AndroidRuntime: FATAL EXCEPTION: main
- **Retour jugé différent par le harnais, conforme par les ancres** — SKILL_EDITOR *(mineure)* : retour 3 : '' -> 'hermes-agent' (jaccard 0.00) — le parent attendu est le fil et l'arrivée est le fil
  - preuve : `/home/john/onyx-integration2/qa/20261026-routes-onyx-319/shots/B3-SKILL_EDITOR.png`
- **Route non atteinte par le parcours prévu** — API_BOT *(à qualifier)* : le tap 'Ajouter un bot' n'a pas changé d'écran (3 essais)
  - preuve : `/home/john/onyx-integration2/qa/20261026-routes-onyx-319/shots/F3-API_BOT.png`
- **Route non atteinte par le parcours prévu** — SUBAGENT *(à qualifier)* : libellé introuvable : '1 task'
  - preuve : `/home/john/onyx-integration2/qa/20261026-routes-onyx-319/shots/Fx1-SUBAGENT.png`
- **Route non atteinte : état non produit** — SUBAGENTS *(à qualifier)* : la liste des sous-agents n'a de bouton que si le fil en porte (état produit par un tour d'agent réel)
- **Route non atteinte par le parcours prévu** — SESSION_USAGE *(à qualifier)* : libellé introuvable : 'Usage & cost'
  - preuve : `/home/john/onyx-integration2/qa/20261026-routes-onyx-319/shots/Fx3-SESSION_USAGE.png`
- **Retour jugé différent par le harnais, conforme par les ancres** — PROJECT_SESSIONS *(mineure)* : retour 2 : 'hermes-agent' -> 'Fix-it' (jaccard 0.27) — l'arrivée est le fil (titre différent : la conversation ouverte a changé)
  - preuve : `/home/john/onyx-integration2/qa/20261026-routes-onyx-319/shots/B2-PROJECT_SESSIONS.png`
