# Hermes Onyx 1.3.83

La réponse s'arrêtait au milieu d'une phrase, et l'app la montrait comme finie.

## Les trois fins d'un flux

Le fil d'un serveur OpenAI-compatible peut se terminer de trois façons :

| Comment le corps s'arrête | Ce que ça veut dire |
|---|---|
| `data: [DONE]` | fin normale — la sentinelle OpenAI |
| `choices[].finish_reason = stop / length / tool_calls` | fin normale — le serveur **nomme** sa fin (tous n'envoient pas `[DONE]`) |
| **plus rien, sans un mot** | **coupure** — NAT qui jette l'état, tunnel expiré, bascule Wi-Fi→4G |

Le troisième cas produisait une fin de tour en `Done`, **sans erreur** : la bulle gardait le texte
amputé, et rien ne proposait de relancer. C'est le « ça se coupe ».

## Le correctif

- **`ChatCompletionsClient` retient la fin que le serveur a *dite*.** `[DONE]` ou une
  `finish_reason` : le flux est complet. Ni l'un ni l'autre : il a été arraché, et le client lève
  `TransportError.Truncated` au lieu de faire passer une phrase amputée pour une réponse entière.
  La distinction est testée sur les trois cas.
- **La reprise va chercher la réponse chez l'hôte.** Le gateway d'Hermes garde le tour en cours
  quand le client se déconnecte et écrit la réponse dans la session
  (`_keeps_running_on_disconnect`). L'ancien garde-fou de la reprise sortait dès que le tour
  n'était plus en streaming — donc précisément dans le cas d'une coupure. Maintenant, une
  troncature déclenche la même relecture : la réponse complète remplace le texte amputé, et
  **l'erreur s'efface** au lieu de rester affichée au-dessus d'un texte entier.

## Vérifié

- `ChatCompletionsCutTest` : flux arraché → `Truncated` ; `finish_reason` sans `[DONE]` → complet ;
  `[DONE]` → complet.
- Le gateway lui-même écrit **toujours** `finish_chunk` puis `data: [DONE]`
  (`gateway/platforms/api_server_openai_routes.py:1225`) : la règle stricte ne casse aucun tour
  normal.
- Suites complètes `:core` + `:app` vertes (voir le journal de build), plus le canal du Dashboard
  en 1.3.82 : battement de 20 s et reprise à 5 essais.