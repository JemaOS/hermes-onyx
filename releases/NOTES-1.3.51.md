# Onyx 1.3.51 — le navigateur parle enfin au téléphone

**Corrigé** : l'écran s'ouvrait, affichait « En attente du navigateur de l'agent… », puis
s'arrêtait sur `Expected HTTP 101 response but was '401 Unauthorized'`.

## La cause, exactement

Le relais de l'app (`onyx-one`) authentifie **toutes** les routes qu'il sert — y compris la
poignée de main WebSocket. Le client envoyait le ticket dans l'URL (`?ticket=…`) mais **pas**
l'en-tête `Authorization`, contrairement à ses appels HTTP. Le relais répondait donc 401, et
OkHttp refusait la connexion avant le moindre échange de trames.

`core/.../net/BrowserScreenClient.kt` : la requête du WebSocket porte maintenant
`Authorization: Bearer <clé du profil effectif>` — la même clé que les appels `observe` et
`lease`. Le ticket, lui, reste dans l'adresse : il est à usage unique et lié à cette connexion.

## Traductions

L'écran était en anglais dans la 1.3.49. Les 15 libellés sont maintenant en français :
« Le navigateur de l'agent », « En attente du navigateur de l'agent… », « Prendre la main »,
« Rendre la main », « C'est vous qui conduisez », « Tabulation », « Enregistré dans le coffre »…
Un test refuse qu'un de ces libellés reparte en anglais.

## Vérifications

- `BrowserScreenTest` : 13 tests, dont deux nouveaux — la clé sur la poignée de main (et le ticket
  qui reste dans l'URL), et la présence des 8 libellés d'écran en français.
- Suites complètes `:core` + `:app` avant publication.
