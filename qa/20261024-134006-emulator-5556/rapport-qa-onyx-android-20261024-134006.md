# Rapport QA réel — Onyx Android 1.0.0+134 (134)

**Début:** 2026-10-24T13:40:06+02:00 
**Fin des actions appareil:** 2026-10-24T14:42:34+02:00 
**Appareil strictement utilisé:** `emulator-5556` (aucune commande adressée à `emulator-5554`) 
**Méthode:** ADB, `uiautomator dump`, captures d'écran relues avec `vision_analyze`, logcat et vérifications d'APK. Les constats ci-dessous proviennent des interactions et des captures, pas d'une simple lecture du code.

Tous les chemins de preuves du document sont relatifs au dossier du rapport :

`/home/john/r132/onyx-android/qa/20261024-134006-emulator-5556/`

---

## 1. Version et APK effectivement testés

| Champ | Valeur vérifiée |
|---|---|
| Package / activité | `com.onyx.app` / `.MainActivity` |
| versionName | **`1.0.0+134`** |
| versionCode | **`134`** |
| Release projet | `/home/john/r132/onyx-android/releases/app-v1.0.0_134-20261024-1332.apk` |
| APK installé | `/data/app/~~cjGgJxcytNlhm7sUGlx3fw==/com.onyx.app-XGWpXCBMPhLfCQHqFSgXiQ==/base.apk` |
| Copie de preuve | `apk/installed-base.apk` |
| Taille | 8 776 352 octets |
| SHA-256 release et installé | `f9c7d8a9b1eb8893cd28cba778d38ec9edcb468c449c8400b5d43a8def30c042` |
| Comparaison binaire | `cmp_exit=0`: l'APK installé est strictement identique à la release 134 |
| Signature | APK Signature Scheme **v2 vérifié**, 1 signataire |
| Certificat SHA-256 | `fa3364fdb2ca25dd91ce3f7f4731c7b472d46f9af5b11152388a595bf01112cb` |
| Environnement | Android 16, API 36, 1080×2400, 420 dpi, locale `en-US` |

L'inventaire par date de modification place la release 134 (13:32:14) devant la 130 (04:29:16) et les builds locaux plus anciens. Comme l'installé était déjà byte-pour-byte cette dernière release signée, **aucune réinstallation n'était nécessaire**. La version est également affichée dans Settings (`1.0.0+134 (134)`).

Preuves : [`apk/badging-v134.txt`](apk/badging-v134.txt), [`apk/apksigner-v134.txt`](apk/apksigner-v134.txt), [`apk/apksigner-installed.txt`](apk/apksigner-installed.txt), [`apk/sha256-both-final.txt`](apk/sha256-both-final.txt), [`apk/all-apk-mtime-top20.txt`](apk/all-apk-mtime-top20.txt), [`device/dumpsys-package-after.txt`](device/dumpsys-package-after.txt).

## 2. Bilan chiffré

| Indicateur | Résultat |
|---|---:|
| Scénarios/contrôles exécutés | **50** |
| Réussis | **36** |
| Avec anomalie | **14** |
| Limites / scénarios non testés | **14** |
| Anomalies distinctes | **13** |
| Crashs Onyx / exceptions fatales / ANR détectés | **0** |
| Captures PNG | **136** |
| Dumps UI XML (+ résumés associés) | **136** |
| Fichiers logcat texte | **87** |

### Anomalies par sévérité

| Sévérité | Nombre |
|---|---:|
| Critique | 0 |
| Élevée | 1 |
| Moyenne | 7 |
| Faible | 5 |
| **Total** | **13** |

**Évaluation:** les parcours de lecture, recherche, export annulé, réglages et rotations sont largement utilisables. Les points les plus importants sont l'échec de reconnaissance vocale sur cet émulateur, plusieurs retours Android incohérents, une création de préfixe sans validation explicite et des insets bas d'écran défectueux. Aucun crash ou ANR Onyx n'a été détecté.

Le décompte est généré depuis `issues.json` / `scenarios.json` et vérifié par `verify_qa.py`; voir [`analysis/verification.json`](analysis/verification.json) (`errors: []`).

---

## 3. Anomalies détaillées

### QA-01 — La reconnaissance vocale échoue et reste en retry sur cet émulateur

| Champ | Valeur |
|---|---|
| Sévérité | **Élevée** |
| Catégorie | Fonctionnel — voix |
| Écran(s) / route(s) | Chat → Talk/Dictate → Voice |

**Étapes de reproduction**

1. Depuis le chat existant, toucher Talk (ou Dictate).
2. Attendre 3 à 5 secondes; arrêter puis relancer un cycle d'écoute.
3. Observer le statut et la zone d'erreur; consulter logcat.

**Attendu:** Le recognizer démarre et l'écran reste réellement prêt à transcrire, sans erreur de service.

**Obtenu:** Deux messages ont été reproduits: « The recogniser could not start (code 5) — retrying. » puis « The speech service disconnected or failed. Onyx will retry. » Le second démarrage revient à READY avec Hands-free On, sans transcription. Logcat attribue l'échec au pack de langue en-US absent: SodaSpeechRecognizer error 13 / LANGUAGE_PACK_ERROR.

**Preuves**

- Screenshots :<br>[`screenshots/01-voice-idle.png`](screenshots/01-voice-idle.png)<br>[`screenshots/38-voice-second-start.png`](screenshots/38-voice-second-start.png)
- UI / bounds :<br>[`ui/01-voice-idle-summary.txt`](ui/01-voice-idle-summary.txt)<br>[`ui/38-voice-second-start-summary.txt`](ui/38-voice-second-start-summary.txt)
- Logcat :<br>[`logcat/voice-language-pack-excerpt.txt`](logcat/voice-language-pack-excerpt.txt)<br>[`logcat/38b-voice-second-stop-full.txt`](logcat/38b-voice-second-stop-full.txt)
- Note : Constat réel mais dépendant de l'image émulateur et du service Google Speech. À revalider sur un appareil dont le pack en-US est installé; ne pas généraliser ce constat à tous les appareils.

### QA-02 — Le bouton Back visible du Kanban ouvre Settings au lieu de Conversations

| Champ | Valeur |
|---|---|
| Sévérité | **Moyenne** |
| Catégorie | Fonctionnel — navigation |
| Écran(s) / route(s) | Chat → Conversations → Kanban → Open Kanban → Back |

**Étapes de reproduction**

1. Depuis un démarrage à froid, ouvrir Conversations puis l'onglet Kanban.
2. Toucher Open Kanban.
3. Toucher le libellé Back en haut à gauche.

**Attendu:** Retour à Conversations, idéalement sur l'onglet Kanban.

**Obtenu:** La page Settings s'ouvre. Reproduit après un force-stop, donc sans dépendre de l'ancienne pile de navigation.

**Preuves**

- Screenshots :<br>[`screenshots/48-kanban-fresh.png`](screenshots/48-kanban-fresh.png)<br>[`screenshots/48b-kanban-fresh-visible-back.png`](screenshots/48b-kanban-fresh-visible-back.png)<br>[`screenshots/14c-kanban-visible-back.png`](screenshots/14c-kanban-visible-back.png)
- UI / bounds :<br>[`ui/48-kanban-fresh-summary.txt`](ui/48-kanban-fresh-summary.txt)<br>[`ui/48b-kanban-fresh-visible-back-summary.txt`](ui/48b-kanban-fresh-visible-back-summary.txt)
- Logcat :<br>[`logcat/48b-kanban-fresh-visible-back-full.txt`](logcat/48b-kanban-fresh-visible-back-full.txt)

### QA-03 — Le retour Android depuis New job/New card saute la liste parente

| Champ | Valeur |
|---|---|
| Sévérité | **Moyenne** |
| Catégorie | Fonctionnel — retour Android |
| Écran(s) / route(s) | Conversations → Cron → Open Cron → New<br>Conversations → Kanban → Open Kanban → New card |

**Étapes de reproduction**

1. Cron: ouvrir New depuis la liste Cron, puis presser le retour Android.
2. Kanban: ouvrir New card depuis le board, puis presser le retour Android.

**Attendu:** Retour à la liste Cron pour New job, ou au board Kanban pour New card, sans réinitialiser l'onglet.

**Obtenu:** Dans les deux cas, le retour Android atterrit directement sur Conversations avec l'onglet Chats sélectionné; la liste Cron ou le board est sauté.

**Preuves**

- Screenshots :<br>[`screenshots/09-cron-new.png`](screenshots/09-cron-new.png)<br>[`screenshots/09d-cron-new-android-back.png`](screenshots/09d-cron-new-android-back.png)<br>[`screenshots/12-kanban-new.png`](screenshots/12-kanban-new.png)<br>[`screenshots/12d-kanban-new-android-back.png`](screenshots/12d-kanban-new-android-back.png)
- UI / bounds :<br>[`ui/09d-cron-new-android-back-summary.txt`](ui/09d-cron-new-android-back-summary.txt)<br>[`ui/12d-kanban-new-android-back-summary.txt`](ui/12d-kanban-new-android-back-summary.txt)
- Logcat :<br>[`logcat/09e-cron-list-android-back-full.txt`](logcat/09e-cron-list-android-back-full.txt)<br>[`logcat/12d-kanban-new-android-back-full.txt`](logcat/12d-kanban-new-android-back-full.txt)

### QA-04 — Le retour Android quitte le mode sélection et Conversations au lieu d'annuler la sélection

| Champ | Valeur |
|---|---|
| Sévérité | **Moyenne** |
| Catégorie | Fonctionnel — retour Android |
| Écran(s) / route(s) | Chat → Conversations → Select → 1 selected → retour Android |

**Étapes de reproduction**

1. Dans Conversations, toucher Select.
2. Sélectionner une conversation.
3. Presser le retour Android une fois.

**Attendu:** Rester dans Conversations et quitter uniquement le mode sélection (0 selected).

**Obtenu:** Le chat précédent s'affiche directement. La réouverture de Conversations montre que la sélection a été abandonnée.

**Preuves**

- Screenshots :<br>[`screenshots/05b-conversations-one-selected.png`](screenshots/05b-conversations-one-selected.png)<br>[`screenshots/05c-selection-android-back.png`](screenshots/05c-selection-android-back.png)<br>[`screenshots/06-conversations-reopened.png`](screenshots/06-conversations-reopened.png)
- UI / bounds :<br>[`ui/05b-conversations-one-selected-summary.txt`](ui/05b-conversations-one-selected-summary.txt)<br>[`ui/05c-selection-android-back-summary.txt`](ui/05c-selection-android-back-summary.txt)
- Logcat :<br>[`logcat/05b-conversations-one-selected-full.txt`](logcat/05b-conversations-one-selected-full.txt)

### QA-05 — Le retour Android depuis New conversation ferme Onyx au lieu de revenir à l'écran précédent

| Champ | Valeur |
|---|---|
| Sévérité | **Moyenne** |
| Catégorie | Fonctionnel — retour Android |
| Écran(s) / route(s) | Démarrage à froid → Chat → Conversations → New conversation → retour Android |

**Étapes de reproduction**

1. Démarrer Onyx à froid puis ouvrir Conversations.
2. Toucher New conversation.
3. Sur l'écran vide Good afternoon, presser le retour Android.

**Attendu:** Retour à Conversations ou à la conversation précédente.

**Obtenu:** Onyx est quitté et le launcher Android apparaît. Un lancement ultérieur restaure néanmoins la conversation récente.

**Preuves**

- Screenshots :<br>[`screenshots/51-minimal-new-conversation.png`](screenshots/51-minimal-new-conversation.png)<br>[`screenshots/51b-minimal-new-conversation-android-back.png`](screenshots/51b-minimal-new-conversation-android-back.png)<br>[`screenshots/52-relaunch-after-back-home.png`](screenshots/52-relaunch-after-back-home.png)
- UI / bounds :<br>[`ui/51-minimal-new-conversation-summary.txt`](ui/51-minimal-new-conversation-summary.txt)<br>[`ui/51b-minimal-new-conversation-android-back-summary.txt`](ui/51b-minimal-new-conversation-android-back-summary.txt)
- Logcat :<br>[`logcat/51b-minimal-new-conversation-android-back-full.txt`](logcat/51b-minimal-new-conversation-android-back-full.txt)

### QA-06 — Une règle Spoken prefix est enregistrée par le retour Android sans appui sur Done

| Champ | Valeur |
|---|---|
| Sévérité | **Moyenne** |
| Catégorie | Fonctionnel — validation / données locales |
| Écran(s) / route(s) | Settings → Voice → Spoken prefixes → Add prefix |

**Étapes de reproduction**

1. Ouvrir Add prefix.
2. Saisir QA dans Word; ne toucher ni Done ni Save.
3. Fermer le clavier avec Back, puis presser Back pour quitter l'éditeur.
4. Observer la liste des préfixes.

**Attendu:** Aucune règle créée sans validation explicite, ou un libellé indiquant clairement l'enregistrement automatique.

**Obtenu:** Une nouvelle ligne QA apparaît dans Spoken prefixes avec un préfixe vide. La règle temporaire QA, et uniquement celle-ci, a ensuite été supprimée pour restaurer l'état initial.

**Preuves**

- Screenshots :<br>[`screenshots/22c-spoken-prefix-word-typed.png`](screenshots/22c-spoken-prefix-word-typed.png)<br>[`screenshots/22d-spoken-prefix-editor-back.png`](screenshots/22d-spoken-prefix-editor-back.png)<br>[`screenshots/22e-qa-prefix-cleaned.png`](screenshots/22e-qa-prefix-cleaned.png)
- UI / bounds :<br>[`ui/22c-spoken-prefix-word-typed-summary.txt`](ui/22c-spoken-prefix-word-typed-summary.txt)<br>[`ui/22d-spoken-prefix-editor-back-summary.txt`](ui/22d-spoken-prefix-editor-back-summary.txt)<br>[`ui/22e-qa-prefix-cleaned-summary.txt`](ui/22e-qa-prefix-cleaned-summary.txt)
- Logcat :<br>[`logcat/22d-spoken-prefix-editor-back-full.txt`](logcat/22d-spoken-prefix-editor-back-full.txt)

### QA-07 — Archive/Delete sont partiellement coupés et recouverts par la zone gestuelle

| Champ | Valeur |
|---|---|
| Sévérité | **Moyenne** |
| Catégorie | Visuel — insets / actions |
| Écran(s) / route(s) | Conversations → Select → conversation(s) sélectionnée(s) |

**Étapes de reproduction**

1. Ouvrir Select dans Conversations.
2. Sélectionner une conversation, ou Select all pour deux.
3. Examiner la barre d'actions inférieure sans toucher Archive ni Delete.

**Attendu:** Les actions et leurs zones tactiles sont entièrement au-dessus de l'inset de navigation.

**Obtenu:** Archive 1/Delete 1 (puis Archive 2/Delete 2) occupent la zone y=2284…2400; les libellés sont au bord inférieur, partiellement coupés et concurrencés par la barre gestuelle blanche.

**Preuves**

- Screenshots :<br>[`screenshots/05-conversations-select.png`](screenshots/05-conversations-select.png)<br>[`screenshots/05b-conversations-one-selected.png`](screenshots/05b-conversations-one-selected.png)<br>[`screenshots/53-conversations-select-all.png`](screenshots/53-conversations-select-all.png)
- UI / bounds :<br>[`ui/05b-conversations-one-selected-summary.txt`](ui/05b-conversations-one-selected-summary.txt)<br>[`ui/53-conversations-select-all-summary.txt`](ui/53-conversations-select-all-summary.txt)
- Logcat :<br>[`logcat/05b-conversations-one-selected-full.txt`](logcat/05b-conversations-one-selected-full.txt)

### QA-08 — Le preset every monday 8am est réduit à une lettre et la rangée ne défile pas

| Champ | Valeur |
|---|---|
| Sévérité | **Moyenne** |
| Catégorie | Visuel — presets Cron |
| Écran(s) / route(s) | Conversations → Cron → Open Cron → New |

**Étapes de reproduction**

1. Ouvrir le formulaire New job.
2. Examiner les quatre presets sous Schedule.
3. Effectuer un swipe horizontal de droite à gauche sur la rangée.

**Attendu:** Chaque preset reste lisible, via wrapping ou défilement horizontal.

**Obtenu:** Le quatrième preset n'affiche qu'un petit fragment (« e » sur la capture). Son bounds est compressé à x=904…1030 et le swipe ne change ni la position ni le contenu. Le champ Schedule manuel reste un contournement.

**Preuves**

- Screenshots :<br>[`screenshots/09-cron-new.png`](screenshots/09-cron-new.png)<br>[`screenshots/09b-cron-schedule-chip-swipe.png`](screenshots/09b-cron-schedule-chip-swipe.png)
- UI / bounds :<br>[`ui/09-cron-new-summary.txt`](ui/09-cron-new-summary.txt)<br>[`ui/09b-cron-schedule-chip-swipe-summary.txt`](ui/09b-cron-schedule-chip-swipe-summary.txt)
- Logcat :<br>[`logcat/09-cron-new-full.txt`](logcat/09-cron-new-full.txt)

### QA-09 — Le toast Copied est presque entièrement hors écran sous la barre gestuelle

| Champ | Valeur |
|---|---|
| Sévérité | **Faible** |
| Catégorie | Visuel — inset / feedback |
| Écran(s) / route(s) | Chat → Copy reply |

**Étapes de reproduction**

1. Dans une réponse existante, toucher Copy reply.
2. Observer immédiatement le bas de l'écran.

**Attendu:** Le feedback Copied est entièrement visible au-dessus du composer et de l'inset système.

**Obtenu:** L'icône devient une coche et la copie est confirmée par la suggestion du presse-papiers, mais le toast Copied apparaît sous la barre gestuelle; seule sa partie supérieure est visible.

**Preuves**

- Screenshots :<br>[`screenshots/35-chat-copy.png`](screenshots/35-chat-copy.png)<br>[`screenshots/36-chat-draft-keyboard.png`](screenshots/36-chat-draft-keyboard.png)
- UI / bounds :<br>[`ui/35-chat-copy-summary.txt`](ui/35-chat-copy-summary.txt)
- Logcat :<br>[`logcat/35-chat-copy-full.txt`](logcat/35-chat-copy-full.txt)

### QA-10 — Appearance mélange anglais et français alors que le système est en en-US

| Champ | Valeur |
|---|---|
| Sévérité | **Faible** |
| Catégorie | Contenu — localisation |
| Écran(s) / route(s) | Settings → Appearance |

**Étapes de reproduction**

1. Ouvrir Appearance et examiner le sélecteur LIGHT AND DARK.

**Attendu:** Libellés cohérents avec le reste de la page anglaise (System/Light/Dark), ou page entièrement localisée.

**Obtenu:** Système / Clair / Sombre sont affichés au milieu d'une page anglaise. getprop indique ro.product.locale=en-US.

**Preuves**

- Screenshots :<br>[`screenshots/17-appearance.png`](screenshots/17-appearance.png)<br>[`screenshots/17c-appearance-messages-light.png`](screenshots/17c-appearance-messages-light.png)
- UI / bounds :<br>[`ui/17-appearance-summary.txt`](ui/17-appearance-summary.txt)<br>[`device/getprop.txt`](device/getprop.txt)
- Logcat :<br>[`logcat/17-appearance-full.txt`](logcat/17-appearance-full.txt)

### QA-11 — Le texte Android de Spoken prefixes limite la fonction à voice mode et l'assistant

| Champ | Valeur |
|---|---|
| Sévérité | **Faible** |
| Catégorie | Contenu — implémentation Android |
| Écran(s) / route(s) | Settings → Voice → Spoken prefixes |

**Étapes de reproduction**

1. Ouvrir Spoken prefixes et lire le texte sous les boutons.

**Attendu:** Une explication adaptée aux surfaces réellement disponibles sur Android.

**Obtenu:** Le texte affiche « Applies to voice mode and l'assistant only », mention d'une surface sans explication pour l'utilisateur Android.

**Preuves**

- Screenshots :<br>[`screenshots/22-spoken-prefixes.png`](screenshots/22-spoken-prefixes.png)
- UI / bounds :<br>[`ui/22-spoken-prefixes-summary.txt`](ui/22-spoken-prefixes-summary.txt)
- Logcat :<br>[`logcat/22-spoken-prefixes-full.txt`](logcat/22-spoken-prefixes-full.txt)

### QA-12 — Les orbes de bots sont noirs et peu reconnaissables, contrairement aux faces colorées de Bots

| Champ | Valeur |
|---|---|
| Sévérité | **Faible** |
| Catégorie | Visuel — avatars / icônes |
| Écran(s) / route(s) | Conversations<br>Conversations → New Bot → Onyx<br>Bots |

**Étapes de reproduction**

1. Observer les avatars Onyx et kilo dans Conversations.
2. Ouvrir la liste Bots puis l'éditeur Onyx.
3. Comparer avec les faces colorées visibles dans la liste Bots.

**Attendu:** Des avatars cohérents et reconnaissables entre les vues, sans apparence de média vide.

**Obtenu:** Conversations et l'éditeur montrent des disques presque entièrement noirs avec contour et fond carré; la liste Bots affiche au même moment des faces citron vert et bleu ciel détaillées.

**Preuves**

- Screenshots :<br>[`screenshots/03-conversations.png`](screenshots/03-conversations.png)<br>[`screenshots/40-new-bot.png`](screenshots/40-new-bot.png)<br>[`screenshots/41-bot-editor.png`](screenshots/41-bot-editor.png)
- UI / bounds :<br>[`ui/03-conversations-summary.txt`](ui/03-conversations-summary.txt)<br>[`ui/40-new-bot-summary.txt`](ui/40-new-bot-summary.txt)<br>[`ui/41-bot-editor-summary.txt`](ui/41-bot-editor-summary.txt)
- Logcat :<br>[`logcat/41-bot-editor-full.txt`](logcat/41-bot-editor-full.txt)

### QA-13 — Le retour de What's New réinitialise le défilement de Settings en haut

| Champ | Valeur |
|---|---|
| Sévérité | **Faible** |
| Catégorie | UX — état de navigation |
| Écran(s) / route(s) | Settings (bas) → What's New → retour Android |

**Étapes de reproduction**

1. Défiler Settings jusqu'à About et What's New.
2. Ouvrir What's New.
3. Presser le retour Android.

**Attendu:** Retour à la même position de défilement dans Settings.

**Obtenu:** Settings revient en haut sur CONNECTION. La perte de position a aussi été observée au retour d'autres sous-pages.

**Preuves**

- Screenshots :<br>[`screenshots/24b-settings-lower2.png`](screenshots/24b-settings-lower2.png)<br>[`screenshots/25-whats-new.png`](screenshots/25-whats-new.png)<br>[`screenshots/25b-whats-new-android-back.png`](screenshots/25b-whats-new-android-back.png)
- UI / bounds :<br>[`ui/24b-settings-lower2-summary.txt`](ui/24b-settings-lower2-summary.txt)<br>[`ui/25b-whats-new-android-back-summary.txt`](ui/25b-whats-new-android-back-summary.txt)
- Logcat :<br>[`logcat/25-whats-new-full.txt`](logcat/25-whats-new-full.txt)

---

## 4. Scénarios exécutés et résultats

✅ = attendu vérifié; ❌ = au moins un attendu non satisfait et anomalie associée. La première preuve est liée ici; `scenarios.json` modèle toutes les autres.

| ID | Résultat | Scénario | Anomalie | Preuve principale |
|---|---|---|---|---|
| S01 | ✅ Réussi | Identifier l'APK, versionName/versionCode, signature et identité installée/release | — | [`apk/badging-v134.txt`](apk/badging-v134.txt) (+3) |
| S02 | ✅ Réussi | Lancement initial et rendu de l'écran Voice | — | [`screenshots/00-initial.png`](screenshots/00-initial.png) (+1) |
| S03 | ✅ Réussi | Chat existant: bulles, actions, icônes et composer | — | [`screenshots/02-chat.png`](screenshots/02-chat.png) (+1) |
| S04 | ✅ Réussi | Find in conversation: 1 of 2, occurrence suivante et Done | — | [`screenshots/32b-chat-find-query.png`](screenshots/32b-chat-find-query.png) (+2) |
| S05 | ✅ Réussi | Rename conversation: ouverture puis Cancel sans modification | — | [`screenshots/33-chat-rename.png`](screenshots/33-chat-rename.png) (+1) |
| S06 | ✅ Réussi | Export Markdown: feuille de partage d'un fichier puis annulation | — | [`screenshots/34-chat-export-share.png`](screenshots/34-chat-export-share.png) (+1) |
| S07 | ❌ Anomalie | Copy reply et visibilité du feedback Copied | QA-09 | [`screenshots/35-chat-copy.png`](screenshots/35-chat-copy.png) (+1) |
| S08 | ✅ Réussi | Brouillon non envoyé, insets clavier et retour Android de fermeture du clavier | — | [`screenshots/36-chat-draft-keyboard.png`](screenshots/36-chat-draft-keyboard.png) (+2) |
| S09 | ✅ Réussi | Chat en paysage, conservation du brouillon et retour portrait | — | [`screenshots/36c-chat-landscape.png`](screenshots/36c-chat-landscape.png) (+1) |
| S10 | ❌ Anomalie | Démarrage de la reconnaissance vocale par Talk/Dictate | QA-01 | [`screenshots/38-voice-second-start.png`](screenshots/38-voice-second-start.png) (+1) |
| S11 | ✅ Réussi | Switch to typing depuis le mode Voice sans envoi | — | [`screenshots/37-chat-dictate.png`](screenshots/37-chat-dictate.png) (+1) |
| S12 | ✅ Réussi | Voice speed: changement puis restauration à 1.00× | — | [`ui/23-voice-speed-changed-summary.txt`](ui/23-voice-speed-changed-summary.txt) (+1) |
| S13 | ✅ Réussi | Listening language: Français puis restauration English (US) | — | [`screenshots/19b-listening-fr.png`](screenshots/19b-listening-fr.png) (+1) |
| S14 | ✅ Réussi | Reply language: recherche, Français puis restauration Automatic | — | [`screenshots/20b-reply-language-search.png`](screenshots/20b-reply-language-search.png) (+2) |
| S15 | ✅ Réussi | Stop phrases: activation d'Add à la saisie puis sortie sans ajout | — | [`ui/21b-stop-phrase-typed-not-added-summary.txt`](ui/21b-stop-phrase-typed-not-added-summary.txt) (+1) |
| S16 | ❌ Anomalie | Spoken prefix: sortie de l'éditeur sans Done | QA-06 | [`screenshots/22c-spoken-prefix-word-typed.png`](screenshots/22c-spoken-prefix-word-typed.png) (+2) |
| S17 | ❌ Anomalie | Revue du texte Spoken prefixes sur Android | QA-11 | [`screenshots/22-spoken-prefixes.png`](screenshots/22-spoken-prefixes.png) (+1) |
| S18 | ✅ Réussi | Conversations: recherche Hello, filtrage et fermeture du clavier | — | [`screenshots/04-conversations-search.png`](screenshots/04-conversations-search.png) (+1) |
| S19 | ❌ Anomalie | Conversations: retour Android avec une conversation sélectionnée | QA-04 | [`screenshots/05b-conversations-one-selected.png`](screenshots/05b-conversations-one-selected.png) (+1) |
| S20 | ✅ Réussi | Conversations: Select all (2) puis Cancel | — | [`ui/53-conversations-select-all-summary.txt`](ui/53-conversations-select-all-summary.txt) (+1) |
| S21 | ❌ Anomalie | Conversations: visibilité des actions Archive/Delete en mode sélection | QA-07 | [`screenshots/05b-conversations-one-selected.png`](screenshots/05b-conversations-one-selected.png) (+1) |
| S22 | ✅ Réussi | Cron: ouverture de la liste et lecture des jobs en pause | — | [`screenshots/08-cron-screen.png`](screenshots/08-cron-screen.png) (+1) |
| S23 | ✅ Réussi | Cron: le preset every 30m remplit Schedule | — | [`ui/09c-cron-chip-selected-summary.txt`](ui/09c-cron-chip-selected-summary.txt) |
| S24 | ❌ Anomalie | Cron: lisibilité et défilement de la rangée de presets | QA-08 | [`screenshots/09-cron-new.png`](screenshots/09-cron-new.png) (+1) |
| S25 | ❌ Anomalie | Cron: retour Android depuis New job | QA-03 | [`screenshots/09d-cron-new-android-back.png`](screenshots/09d-cron-new-android-back.png) (+1) |
| S26 | ✅ Réussi | Kanban: parcours des 9 colonnes et défilement des onglets | — | [`screenshots/11-kanban.png`](screenshots/11-kanban.png) (+8) |
| S27 | ✅ Réussi | Kanban New card: Priority 0→1→0 avec +/− | — | [`ui/12b-kanban-priority-plus-summary.txt`](ui/12b-kanban-priority-plus-summary.txt) (+1) |
| S28 | ❌ Anomalie | Kanban: retour Android depuis New card | QA-03 | [`screenshots/12d-kanban-new-android-back.png`](screenshots/12d-kanban-new-android-back.png) (+1) |
| S29 | ❌ Anomalie | Kanban ouvert depuis Conversations: bouton Back visible | QA-02 | [`screenshots/48-kanban-fresh.png`](screenshots/48-kanban-fresh.png) (+1) |
| S30 | ✅ Réussi | Kanban: paysage, colonne Archived et retour portrait | — | [`screenshots/14-kanban-landscape.png`](screenshots/14-kanban-landscape.png) (+1) |
| S31 | ✅ Réussi | Model: options reasoning/modèles et retour Android | — | [`screenshots/16-models.png`](screenshots/16-models.png) (+1) |
| S32 | ✅ Réussi | Appearance: Messages + Clair puis restauration Hermes + Système | — | [`screenshots/17c-appearance-messages-light.png`](screenshots/17c-appearance-messages-light.png) (+1) |
| S33 | ❌ Anomalie | Appearance: cohérence de langue du sélecteur clair/sombre | QA-10 | [`screenshots/17-appearance.png`](screenshots/17-appearance.png) (+1) |
| S34 | ✅ Réussi | Réglages Voice: arrière-plan puis reprise sur le même écran et PID | — | [`screenshots/23c-voice-background-resume.png`](screenshots/23c-voice-background-resume.png) (+2) |
| S35 | ✅ Réussi | Skills: lecture de la liste sans édition | — | [`screenshots/43-skills.png`](screenshots/43-skills.png) (+1) |
| S36 | ✅ Réussi | Tools: lecture des 21 groupes sans changer les toggles | — | [`screenshots/44-tools.png`](screenshots/44-tools.png) (+1) |
| S37 | ✅ Réussi | Phone tools: lecture de la page sans changer le toggle | — | [`screenshots/42-phone-tools.png`](screenshots/42-phone-tools.png) (+1) |
| S38 | ✅ Réussi | Profile: Default sélectionné, lecture seule | — | [`screenshots/45-profile.png`](screenshots/45-profile.png) (+1) |
| S39 | ✅ Réussi | Lock: page affichée sans passcode ni activation biométrique | — | [`screenshots/46-lock.png`](screenshots/46-lock.png) (+1) |
| S40 | ❌ Anomalie | What's New: retour vers la position précédente de Settings | QA-13 | [`screenshots/24b-settings-lower2.png`](screenshots/24b-settings-lower2.png) (+2) |
| S41 | ✅ Réussi | Acknowledgements: licences et contenu défilant | — | [`screenshots/26b-acknowledgements.png`](screenshots/26b-acknowledgements.png) (+1) |
| S42 | ✅ Réussi | Terminal: validation Host/User vides sans tentative SSH | — | [`screenshots/29b-terminal-empty-validation.png`](screenshots/29b-terminal-empty-validation.png) (+1) |
| S43 | ✅ Réussi | Terminal: paysage, scroll du message et retour portrait | — | [`screenshots/30-terminal-landscape.png`](screenshots/30-terminal-landscape.png) (+2) |
| S44 | ✅ Réussi | New Group Chat: sélection des deux bots puis Cancel sans création | — | [`screenshots/39b-group-chat-bots-selected.png`](screenshots/39b-group-chat-bots-selected.png) (+1) |
| S45 | ❌ Anomalie | Cohérence des avatars/orbes entre Conversations, Bots et l'éditeur | QA-12 | [`screenshots/03-conversations.png`](screenshots/03-conversations.png) (+2) |
| S46 | ✅ Réussi | Éditeur Onyx: retour Android vers la liste Bots sans modification | — | [`screenshots/41-bot-editor.png`](screenshots/41-bot-editor.png) (+1) |
| S47 | ❌ Anomalie | New conversation: retour Android minimal après démarrage à froid | QA-05 | [`screenshots/51-minimal-new-conversation.png`](screenshots/51-minimal-new-conversation.png) (+1) |
| S48 | ✅ Réussi | Démarrage à froid contrôlé avec conservation de la conversation | — | [`screenshots/47-cold-start.png`](screenshots/47-cold-start.png) (+2) |
| S49 | ✅ Réussi | Relance après sortie au launcher: restauration du chat récent | — | [`screenshots/52-relaunch-after-back-home.png`](screenshots/52-relaunch-after-back-home.png) (+1) |
| S50 | ✅ Réussi | Stabilité: recherche de crash Onyx, exception fatale et ANR dans les logs de session | — | [`analysis/stability-scan.txt`](analysis/stability-scan.txt) (+2) |

### Couverture fonctionnelle notable

- **Chat :** rendu de messages, Find avec deux occurrences, Rename annulé, export Markdown ouvert puis annulé, Copy, brouillon non envoyé, clavier et rotation.
- **Voix :** Talk/Dictate, Stop, Switch to typing, Voice speed, langues d'écoute/réponse, Stop phrases, Spoken prefixes et reprise après arrière-plan.
- **Conversations :** recherche, sélection simple/totale, Cancel, New Group Chat annulé, New Bot/Bots et New conversation.
- **Kanban :** les **9 colonnes** — Triage, To do, Scheduled, Ready, Running, Blocked, Review, Done, Archived — plus New card et Priority 0→1→0.
- **Cron :** liste de deux jobs en pause, formulaire New job et preset `every 30m`, sans sauvegarde ni exécution.
- **Réglages :** Model, Appearance, Voice, Skills, Tools, Phone tools, Profile, Lock, What's New, Acknowledgements et version.
- **Terminal :** formulaire SSH vide, validation `Host and user are needed.`, paysage, scroll et portrait; aucune connexion SSH.
- **Système :** retours Android, lancement à froid contrôlé, sortie au launcher, reprise après arrière-plan, portrait/paysage et insets clavier/navigation.

---

## 5. Non testé / limites explicites

| ID | Périmètre non testé | Raison |
|---|---|---|
| NT01 | Envoi de prompts, Return key sends, Regenerate et création effective d'un group chat | Non testés pour éviter toute publication/exécution sur le serveur configuré. |
| NT02 | Archive/Delete de conversations préexistantes | Non testés: actions destructrices; seuls Select/Select all/Cancel ont été exercés. |
| NT03 | Save d'un cron, exécution immédiate et pause/reprise de jobs existants | Non testés pour ne pas créer de tâche ni déclencher de traitement serveur. |
| NT04 | Save d'une carte Kanban, Start right away et transitions réelles de cartes | Non testés pour ne pas modifier le board ni lancer un worker. |
| NT05 | Création/sauvegarde de bots, Change Face, Personality/SOUL.md et configuration de groupes | Non testés pour préserver les profils et la configuration serveur. |
| NT06 | Connexion SSH réelle, mot de passe et exécution de commandes dans Terminal | Non testés: aucun identifiant et aucune commande distante; seule la validation locale et le rendu ont été vérifiés. |
| NT07 | Edit/Add server, changement de modèle/reasoning/profile et toggles Tools/Phone tools | Non testés pour ne pas modifier la configuration ou le serveur actif. |
| NT08 | Context files, Memory, Files, Gateway, Skills New et édition de skills | Non testés: ces écrans modifient le host Hermes en place; aucune écriture effectuée. |
| NT09 | Support Onyx/achat, Set up another device, passcode et biométrie | Non testés pour éviter achat, appairage, verrouillage ou changement de sécurité. |
| NT10 | Parole réelle, transcription, intelligibilité TTS et stop phrases en conditions audio | Impossible à valider complètement ici: pas d'injection audio et pack en-US manquant dans le service de reconnaissance (QA-01). |
| NT11 | Attachments/caméra et rendu riche code, Mermaid, math sur de nouveaux contenus | Non testés: pas de fixture dédiée et pas d'envoi de prompt au serveur pour en générer. |
| NT12 | Partage effectif du Markdown vers Quick Share/Drive/Gmail | Non testé: la feuille de partage a été ouverte puis annulée, sans publication. |
| NT13 | Téléphones physiques, autres versions Android, tablettes, pliables et multi-fenêtre | Hors périmètre matériel: uniquement emulator-5556, Android 16/API 36, 1080×2400 à 420 dpi. |
| NT14 | Soak test prolongé, faible mémoire et stress ANR | Non exécuté: passe exploratoire limitée; reprise manuelle et redémarrages contrôlés seulement. |

---

## 6. Stabilité et logcat

Les deux journaux canoniques analysés sont :

- `logcat/pre-cold-full.txt` : 36 682 lignes;
- `logcat/final-full.txt` : 10 296 lignes.

Pour chacun, les motifs `FATAL EXCEPTION`, `ANR in`, `Process: com.onyx.app`, `Fatal signal`, `am_crash` et `am_anr` donnent **0 occurrence**. Les `am force-stop` utilisés pour les démarrages contrôlés sont des actions QA intentionnelles, pas des crashs. Les erreurs de reconnaissance vocale de QA-01 sont documentées séparément; les messages système sans rapport avec Onyx ne sont pas comptés comme anomalies de l'application.

Preuve : [`analysis/stability-scan.txt`](analysis/stability-scan.txt).

**Incident de focus en toute fin de passe:** une autre application, `com.hermes.conduit`, lancée par `com.android.shell`, a brièvement pris le focus après les scénarios. `dumpsys` confirme que Onyx est resté en version 134 et que son processus était intact; Onyx a ensuite été relancé. Aucun écran Conduit n'est compté dans les 50 scénarios Onyx. Preuves: [`device/55-window-current.txt`](device/55-window-current.txt), [`device/55-activities-current.txt`](device/55-activities-current.txt), [`device/56-am-start-onyx-restore.txt`](device/56-am-start-onyx-restore.txt).

## 7. État restauré et intégrité du workspace

- Rotation restaurée : `accelerometer_rotation=1`, `user_rotation=0`, portrait.
- Appearance restaurée à **Hermes + Système**, Voice speed à **1.00×**, Listening language à **en-US**, Reply language à **Automatic**.
- **Suggest follow-up questions** restauré à **Off** (son état initial), avec **Return key sends** toujours à On; vérification visuelle dans `screenshots/56c-settings-suggest-restored-off.png`.
- Brouillon de chat effacé sans envoi; aucune cible de partage sélectionnée.
- La règle temporaire `QA` créée par le comportement QA-06 a été supprimée; aucune donnée préexistante n'a été supprimée. Le nettoyage est visible dans `screenshots/22e-qa-prefix-cleaned.png`.
- Aucune source n'a été modifiée et `qa/latest` n'a pas été écrasé. Le `git status` final reste identique à l'état initial: `PORT-CHECKLIST.md` était déjà modifié et les fichiers `.sha256`/le dossier `qa/` étaient déjà non suivis.
- Seul ce nouveau dossier horodaté a été ajouté pour le rapport, les scripts de preuve et les artefacts.

Preuves : [`analysis/git-status-final.txt`](analysis/git-status-final.txt), [`analysis/git-diff-names-final.txt`](analysis/git-diff-names-final.txt), [`device/final-accelerometer-rotation.txt`](device/final-accelerometer-rotation.txt), [`device/final-user-rotation.txt`](device/final-user-rotation.txt).

## 8. Index des artefacts structurés

- [`issues.json`](issues.json) : les 13 anomalies avec sévérité, reproduction et preuves;
- [`scenarios.json`](scenarios.json) : les 50 scénarios et leurs preuves complètes;
- [`not-tested.json`](not-tested.json) : les 14 limites;
- [`analysis/verification.json`](analysis/verification.json) : décomptes machine et absence d'erreurs de validation;
- [`analysis/stability-scan.txt`](analysis/stability-scan.txt) : scan crash/ANR;
- [`qa_helper.py`](qa_helper.py) et [`verify_qa.py`](verify_qa.py) : outils de collecte/vérification, strictement fixés sur `emulator-5556`.
