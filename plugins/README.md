# Les compagnons serveur d'Onyx

Onyx est un **client**. Il ne possède ni compte, ni serveur, ni abonnement : il parle au Hermes
qui tourne sur *ta* machine. Certaines fonctions, pourtant, ne peuvent pas vivre dans un
téléphone — un coffre de clés doit être chiffré là où le bot travaille, un journal de
démonstration doit être borné là où le tour s'exécute.

Ces fonctions-là sont des **plugins Hermes**, livrés ici, dans ce dépôt, pour que celui qui
installe Onyx puisse les poser à côté :

```
plugins/
└── onyx-vault/          # le coffre de secrets par profil
    ├── dashboard/       # ← le dossier à copier dans ~/.hermes/plugins/
    ├── tests/           # 35 tests unittest, exécutables sans Hermes pour la plupart
    └── INSTALL.md       # deux étapes, aucune dépendance payante
```

## Ce que ça change pour l'utilisateur

| Fonction d'Onyx | Hermes nu | Hermes + le compagnon |
|---|---|---|
| Chat, bots, rooms, voix, Computer, Kanban, Cron, Code, fichiers | **marche** | marche |
| Skills par `/`, partage d'un modèle de bot, capacités du serveur | **marche** | marche |
| Meneur de room et passage de main (v1 client) | **marche** | marche |
| Coffre de secrets (écrire/lister/révoquer) | **l'entrée n'existe pas** | marche |
| Démonstration → skill (le serveur borne le journal) | **l'entrée n'existe pas** | marche |

« L'entrée n'existe pas » est la règle : Onyx interroge le serveur (`ServerProbe`) et **ne dessine
pas** ce qu'il ne sait pas faire. Aucun écran vide, aucun bouton mort, aucune promesse en l'air.

## Installer un compagnon

```bash
cp -r plugins/onyx-vault ~/.hermes/plugins/onyx-vault
# puis, dans ~/.hermes/config.yaml, sous plugins.enabled :
#     - onyx-vault
```

Le détail (clé de chiffrement, vérification, désinstallation) est dans `plugins/onyx-vault/INSTALL.md`.
Aucun compte, aucune inscription, aucun appel sortant : les routes se montent sur **ton** dashboard.

## Ce que ces plugins ne feront jamais

- envoyer un secret, un journal ou un identifiant à un service tiers ;
- demander une clé d'API d'un fournisseur payant ;
- s'activer sans une ligne explicite dans `config.yaml` — un plugin non listé n'est même pas importé.
