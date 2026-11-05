#!/usr/bin/env python3
"""Faux gateway OpenAI-compatible : diffuse une longue réponse Markdown, jeton par jeton."""
import json, time, threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = 19200

PARA = ("Le gateway répond correctement, mais chaque appel d'outil repart d'une poignée de main "
        "complète, et la latence dépasse la seconde. **Ce n'est pas la taille de la réponse** qui "
        "coûte, c'est le nombre d'allers-retours avant le premier octet. ")
TABLE = ("\n\n| champ | valeur | remarque |\n|---|---|---|\n| `latence` | 412 ms | mesurée |\n"
         "| `contexte` | 118 000 | sur 200 000 |\n| `outils` | 14 | dont 3 en échec |\n\n")
CODE = ("\n\n```kotlin\nprivate fun publier(jeton: String) {\n    val texte = tampon.append(jeton).toString()\n"
        "    _etat.update { it.copy(texte = texte) }\n}\n```\n\n")

def answer():
    parts = ["## Analyse\n\n"]
    for i in range(12):
        parts.append(PARA)
        parts.append("\n".join(f"- Point {n} : le cache garde **{n * 7} entrées**, "
                               f"et la reprise ne relit que `{n % 5 + 1}` évènements."
                               for n in range(1, 9)))
        parts.append("\n\n")
        if i % 3 == 0:
            parts.append(TABLE)
        if i % 4 == 0:
            parts.append(CODE)
    parts.append("Tu veux que je continue ?")
    return "".join(parts)


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *args):
        pass

    def do_GET(self):
        if self.path.startswith("/v1/models"):
            body = json.dumps({"object": "list", "data": [{"id": "mock-1", "object": "model"}]}).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        self.send_error(404)

    def do_POST(self):
        if not self.path.startswith("/v1/chat/completions"):
            self.send_error(404)
            return
        length = int(self.headers.get("Content-Length") or 0)
        self.rfile.read(length)
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "close")
        self.end_headers()
        text = answer()
        step = 24
        for i in range(0, len(text), step):
            chunk = text[i:i + step]
            event = {"id": "chatcmpl-mock", "object": "chat.completion.chunk", "created": int(time.time()),
                     "model": "mock-1", "choices": [{"index": 0, "delta": {"content": chunk},
                                                     "finish_reason": None}]}
            try:
                self.wfile.write(("data: " + json.dumps(event) + "\n\n").encode())
                self.wfile.flush()
            except (BrokenPipeError, ConnectionResetError):
                return
            time.sleep(0.035)
        done = {"id": "chatcmpl-mock", "object": "chat.completion.chunk", "created": int(time.time()),
                "model": "mock-1", "choices": [{"index": 0, "delta": {}, "finish_reason": "stop"}]}
        try:
            self.wfile.write(("data: " + json.dumps(done) + "\n\ndata: [DONE]\n\n").encode())
            self.wfile.flush()
        except (BrokenPipeError, ConnectionResetError):
            pass
        self.close_connection = True


if __name__ == "__main__":
    srv = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    print(f"mock gateway on {PORT}", flush=True)
    srv.serve_forever()
