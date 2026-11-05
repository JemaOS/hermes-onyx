#!/usr/bin/env python3
"""Génère une conversation Onyx **très dense** : réponses de ~15 Ko de Markdown varié."""
import json, time, random, sys, os

OUT = sys.argv[1] if len(sys.argv) > 1 else "/home/john/.hermes/cache/scratch/onyx-seed/conversations.json"
N_TURNS = int(sys.argv[2]) if len(sys.argv) > 2 else 40

random.seed(11)
now = int(time.time() * 1000)

PARA = ("Le serveur a répondu correctement, mais la **latence** du gateway reste élevée sur ce tour : "
        "chaque appel d'outil repart d'une poignée de main complète, et le total dépasse la seconde. "
        "Ce n'est pas la taille de la réponse qui coûte, c'est le nombre d'allers-retours avant le "
        "premier octet — et ce nombre ne dépend pas du modèle choisi.")

TABLE = """
| champ | valeur | remarque |
|---|---|---|
| `latence` | 412 ms | mesurée au gateway |
| `context` | 118 000 | sur 200 000 |
| `outils` | 14 | dont 3 en échec |
| `jetons` | 2 450 | réponse comprise |
| `cache` | 89 % | lectures servies |
"""

CODE = """
```kotlin
private fun publier(jeton: String) {
    val texte = tampon.append(jeton).toString()
    _etat.update { courant ->
        courant.copy(messages = courant.messages.toMutableList().also { liste ->
            liste[liste.lastIndex] = liste.last().copy(text = texte)
        })
    }
}
```

```bash
$ ./gradlew :app:assembleRelease --no-daemon
BUILD SUCCESSFUL in 1m 5s
88 actionable tasks: 14 executed, 74 up-to-date
```
"""

def block(i):
    return (f"### Étape {i}\n\n" + PARA + "\n\n" +
            "\n".join(f"- Point {n} : le cache garde **{random.randint(2, 60)} entrées**, "
                      f"et la reprise ne relit que `{random.randint(1, 9)}` évènements."
                      for n in range(1, 15)) + "\n\n" + TABLE + "\n" + CODE)

def markdown(i):
    parts = [f"## Analyse du tour {i + 1}", PARA]
    for k in range(3):
        parts.append(block(i * 3 + k))
    parts.append("### Ce qu'il reste\n\n" + "\n".join(
        f"{n}. Vérifier `{random.randint(10, 99)}` fichiers puis relancer la mesure." for n in range(1, 11)))
    parts.append("Tu veux que je regarde le tour suivant, ou qu'on s'arrête là pour aujourd'hui ?")
    return "\n\n".join(parts)

TOOLS = ["read_file", "terminal", "search_files", "web_search", "patch", "execute_code"]

messages = []
for turn in range(N_TURNS):
    messages.append({
        "id": f"seed-u{turn}",
        "role": "user",
        "text": f"Question {turn + 1} : détaille le comportement du gateway, avec des exemples.",
        "createdAt": now - (N_TURNS - turn) * 120000,
        "attachments": [], "approvals": [], "tools": [], "subagents": [],
        "reasoning": "", "isStreaming": False, "isSteer": False,
    })
    tools = [{
        "id": f"seed-t{turn}-{t}",
        "name": TOOLS[(turn + t) % len(TOOLS)],
        "preview": f"{TOOLS[(turn + t) % len(TOOLS)]} · {random.randint(1, 90)} ms",
        "status": "completed",
        "startedAt": now - (N_TURNS - turn) * 120000 + t * 500,
        "endedAt": now - (N_TURNS - turn) * 120000 + t * 500 + 420,
        "args": json.dumps({"path": "/home/john/Bureau/onyx/app/src/main/java/com/onyx/app/ui/chat/ChatScreen.kt"}),
        "output": (CODE + "\n" + PARA) * 6,
        "subagentId": None,
    } for t in range(6)]
    messages.append({
        "id": f"seed-a{turn}",
        "role": "assistant",
        "text": markdown(turn),
        "reasoning": ("Je regarde d'abord la façon dont le flux publie ses jetons, puis je compare "
                      "avec la référence. " * 20),
        "reasoningStartedAt": now - (N_TURNS - turn) * 120000 + 100,
        "reasoningEndedAt": now - (N_TURNS - turn) * 120000 + 900,
        "createdAt": now - (N_TURNS - turn) * 120000 + 1000,
        "tools": tools, "subagents": [], "approvals": [], "attachments": [],
        "isStreaming": False, "isSteer": False,
        "usage": {"prompt": 1200, "completion": 480, "total": 1680},
    })

conversation = {
    "id": "seed-perf",
    "title": "Audit de performance — grosse conversation",
    "createdAt": now - 86400000, "updatedAt": now, "serverId": "",
    "messages": messages, "outbox": [], "pinned": True, "archived": False,
    "remote": False, "botProfile": None,
}

os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w") as f:
    json.dump([conversation], f, ensure_ascii=False)
size = os.path.getsize(OUT)
print(OUT, size // 1024, "KiB", len(messages), "messages",
      sum(len(m["text"]) for m in messages) // 1024, "KiB de texte")
