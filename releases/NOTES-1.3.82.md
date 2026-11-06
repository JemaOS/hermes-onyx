# Hermes Onyx 1.3.82

Le canal du Dashboard ne mourait pas : il cessait de respirer sans que personne ne le remarque.

## Ce que le journal du gateway disait

Vingt-trois fermetures `1006` en une soirée — une fermeture **sans trame de close**, la signature
d'un socket que plus personne n'écoutait. Et six tours tués en plein appel modèle
(`interrupted_during_api_call`), dont un à 00:49:52 alors que la réponse s'écrivait encore.

La cause était dans le client, pas dans le réseau : `ServeRpc` ouvrait son WebSocket avec

```kotlin
.readTimeout(0, TimeUnit.SECONDS)   // aucune échéance de lecture — juste pour un flux muet
// … et aucun pingInterval
```

`readTimeout(0)` est le bon réglage : un agent peut réfléchir longtemps, et une échéance de lecture
tuerait une réponse lente en pleine génération. Mais **sans `pingInterval`, aucune trame ne partait
jamais vérifier que la liaison était encore là**. Or un WebSocket de téléphone traverse un NAT, un
tunnel, une bascule Wi-Fi→4G : le NAT jette l'état de la connexion après quelques minutes
d'inactivité, et le socket meurt en silence. L'app ne le voyait pas — le tour restait suspendu, ou
l'écriture suivante échouait et le tour était déclaré perdu.

## Le correctif

- **Un battement de cœur de 20 s** (`PING_INTERVAL_MS`). Sans `pong` sous deux intervalles, OkHttp
  déclare la liaison morte, `onFailure` part, et le canal se rouvre tout seul — le tour se rattache
  par `session.resume` au lieu d'attendre indéfiniment.
- **Cinq tentatives de reprise** au lieu de trois, avec un délai qui **double** et plafonne :
  1 s, 2 s, 4 s, 8 s, 8 s (≈23 s de patience). L'ancienne formule (`RECONNECT_DELAY_MS * attempt`)
  donnait 1, 2, 3 s puis l'abandon : **six secondes**, moins qu'une bascule de réseau.
- Le client est extrait en `dashboardTransportClient()`, pour que les tests portent sur **le vrai
  client** et non sur une copie du `Builder` — recopier la configuration dans un test ne prouve rien
  sur ce que l'app utilise.

`ServeRpcHeartbeatTest` couvre le battement, la lecture sans échéance, la séparation du pool REST,
la progression du délai de reprise et son budget. Les trois suites du canal (`Heartbeat`,
`WebSocketClient`, `ConnectionLock`) passent : 14 tests. Le reste aussi : **4289 tests, aucun échec**.

## Effet attendu

Un lien mort est vu en moins de 40 s, et la coupure devient invisible au lieu de coûter le tour.
Le prix : une trame de 2 octets toutes les 20 s pendant qu'un canal est ouvert — négligeable devant
un seul tour perdu.