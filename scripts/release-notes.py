#!/usr/bin/env python3
"""Reecrit le corps de chaque release au format standard :

    ## What's Changed
    * sujet du commit (sha)

    **Full Changelog**: lien de comparaison

    ## New Contributors            (seulement si un nouvel auteur apparait)

Le contenu vient des **commits reels** entre le tag precedent et le tag de la release : aucune
phrase inventee, aucun adjectif. `--draft` ecrit les projets dans /tmp/releases-draft/ sans rien
publier.
"""
import json
import re
import subprocess
import sys
import urllib.request
from pathlib import Path

RACINE = Path("/home/john/Bureau/onyx")
DEPOT = "JemaOS/hermes-onyx"
def jeton():
    """Le jeton de publication : `GH_TOKEN`, sinon la premiere entree de ~/.git-credentials.

    `gh` est inutilisable sur cette machine (son hosts.yml porte un jeton mort et la migration de
    configuration echoue avant tout appel), d'ou l'appel direct a l'API.
    """
    import os
    if os.environ.get("GH_TOKEN"):
        return os.environ["GH_TOKEN"]
    ligne = Path.home().joinpath(".git-credentials").read_text().splitlines()[0]
    m = re.match(r"https://([^:]+):([^@]*)@github\.com", ligne)
    return (m.group(2) or m.group(1)).strip()


TOK = jeton()
BROUILLON = Path("/tmp/releases-draft")

# Les auteurs qui ne sont pas des personnes : l'assistant qui redige les commits.
PAS_UN_CONTRIBUTEUR = {"hermes@jemaos.local", "hermes@localhost", "onyx@local", "john@jemaos.local"}


def api(url, data=None, methode=None):
    entetes = {"Authorization": "Bearer " + TOK, "Accept": "application/vnd.github+json"}
    corps = None
    if data is not None:
        corps = json.dumps(data).encode()
        entetes["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=corps, headers=entetes, method=methode)
    return json.load(urllib.request.urlopen(req, timeout=60))


def git(*args):
    return subprocess.run(["git", *args], cwd=RACINE, capture_output=True, text=True).stdout


def tags_chronologiques():
    sortie = git("for-each-ref", "--sort=creatordate",
                 "--format=%(refname:short)", "refs/tags").split()
    return sortie


def commits(plage):
    """(sha court, sujet, auteur, email) des commits de la plage."""
    sortie = git("log", "--no-merges", "--format=%h\x1f%s\x1f%an\x1f%ae", plage)
    out = []
    for ligne in sortie.splitlines():
        if not ligne.strip():
            continue
        sha, sujet, auteur, email = ligne.split("\x1f")
        out.append((sha, sujet.strip(), auteur.strip(), email.strip().lower()))
    return out


def premier_commit_par_auteur():
    """email -> sha du tout premier commit de cet auteur dans l'histoire."""
    sortie = git("log", "--reverse", "--format=%H\x1f%ae")
    vus = {}
    for ligne in sortie.splitlines():
        if not ligne.strip():
            continue
        sha, email = ligne.split("\x1f")
        email = email.strip().lower()
        if email not in vus and email not in PAS_UN_CONTRIBUTEUR:
            vus[email] = sha[:7]
    return vus


def handle(email, auteur):
    if email.endswith("@users.noreply.github.com"):
        m = re.match(r"\d*\+?([^@]+)@", email)
        if m:
            return "@" + m.group(1)
    return None


# Ce qui n'apprend rien a celui qui installe : la note de version elle-meme.
BRUIT = re.compile(r"^(Release notes|Notes \d|Release \d|Changelog)", re.I)


def puce(sujet, sha):
    """`Menu: the panel is flat` devient `**Menu** — the panel is flat`."""
    m = re.match(r"^([A-Za-z0-9][A-Za-z0-9 /_+-]{1,24}):\s+(.+)$", sujet)
    # « Onyx: … » n'apprend rien : la release parle deja d'Onyx.
    if m and m.group(1).strip().lower() not in ("onyx", "app"):
        texte = f"**{m.group(1)}** — {m.group(2)}"
    else:
        texte = m.group(2) if m else sujet
    # Une puce commence par une majuscule, meme quand on a retire un prefixe.
    return f"* {texte[:1].upper() + texte[1:]} ({sha})"


def corps(version, precedent, liste):
    lignes = ["## What's Changed"]
    utiles = [c for c in liste if not BRUIT.match(c[1])] or liste
    for sha, sujet, _, _ in utiles:
        lignes.append(puce(sujet, sha))
    lignes.append("")
    lignes.append(f"**Full Changelog**: https://github.com/{DEPOT}/compare/"
                  f"{precedent}...{version}")
    return "\n".join(lignes)


def principal():
    brouillon = "--draft" in sys.argv
    appliquer = "--apply" in sys.argv
    publier = "--publish" in sys.argv

    releases = []
    page = 1
    while True:
        d = api(f"https://api.github.com/repos/{DEPOT}/releases?per_page=100&page={page}")
        if not d:
            break
        releases += d
        page += 1
    releases.sort(key=lambda r: r["published_at"])

    tags = tags_chronologiques()
    premiers = premier_commit_par_auteur()
    BROUILLON.mkdir(exist_ok=True)

    plan = []
    for i, rel in enumerate(releases):
        tag = rel["tag_name"]
        if tag not in tags:
            print(f"  !! {tag} : pas un tag du depot, ignore")
            continue
        idx = tags.index(tag)
        precedent = tags[idx - 1] if idx > 0 else None
        plage = f"{precedent}..{tag}" if precedent else tag
        liste = commits(plage)
        nouveau = []
        for sha, _, auteur, email in liste:
            if premiers.get(email, "").startswith(sha) or premiers.get(email, "") == sha:
                h = handle(email, auteur)
                if h:
                    nouveau.append((h, sha))
        texte = corps(tag, precedent, liste)
        if not liste:
            texte = (f"## What's Changed\n* No commit landed between `{precedent}` and `{tag}`.\n\n"
                     f"**Full Changelog**: https://github.com/{DEPOT}/compare/"
                     f"{precedent}...{tag}")
        if nouveau:
            texte += "\n\n## New Contributors\n"
            for h, sha in dict(nouveau).items():
                texte += f"* {h} made their first contribution in {sha}\n"
        plan.append((rel, texte, len(liste)))
        rel["_nom"] = "Onyx " + tag.lstrip("v")
        (BROUILLON / f"{tag}.md").write_text(texte, encoding="utf-8")
        print(f"  {tag:14} precedent={(precedent or '-'):14} commits={len(liste):3} "
              f"corps={len(texte):4} car.")

    print(f"\n{len(plan)} releases preparees dans {BROUILLON}")
    if appliquer or publier:
        for rel, texte, _ in plan:
            api(f"https://api.github.com/repos/{DEPOT}/releases/{rel['id']}",
                data={"body": texte, "name": rel["_nom"]}, methode="PATCH")
            print(f"  publie {rel['tag_name']}")
    return 0


if __name__ == "__main__":
    sys.exit(principal())
