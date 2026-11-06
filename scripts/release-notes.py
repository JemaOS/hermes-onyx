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
# Les sujets de commit qui **citent du francais** (un libelle d'interface, par exemple) ne peuvent
# pas rester tels quels dans une release anglaise : `--apply` les reintroduisait a chaque passage.
# Ce fichier porte leur forme publiee, indexee par sha court, et il est relu a chaque execution.
OVERRIDES = RACINE / "scripts" / "release-notes-overrides.json"
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

# La table des sujets corriges : vide quand le fichier n'existe pas encore.
OVERRIDES_JETON = (
    json.loads(OVERRIDES.read_text(encoding="utf-8")) if OVERRIDES.exists() else {}
)
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
# **Ce qui ne dit rien à personne.** Un numéro de version qui monte, une note de release qui se
# décrit elle-même, une fusion : ce ne sont pas des changements, et ils n'ont rien à faire dans la
# liste que lit le propriétaire.
BRUIT = re.compile(
    r"^(Bump to|Bump |Release notes|Release:|Notes \d|Release \d|Changelog|Merge )", re.I)


def sujet_de(sha, sujet):
    """Le sujet publie d'un commit : la correction ecrite a la main, s'il y en a une."""
    return OVERRIDES_JETON.get(sha, sujet)


def puce(sujet, sha):
    """`Menu: the panel is flat` devient `**Menu** — the panel is flat`."""
    # **Le sujet du commit, tel quel.** Pas de préfixe réécrit, pas de « **Onyx** — » : la
    # structure demandée est « * sujet (sha) », comme les notes générées par GitHub.
    texte = sujet.strip()
    # **Une ligne, sans puce.** La structure demandée est « sujet (sha) », telle quelle ; deux
    # espaces en fin de ligne gardent le saut à l'affichage Markdown, invisibles dans le texte.
    return f"{texte[:1].upper() + texte[1:]} ({sha})  "


def corps(version, precedent, liste):
    lignes = ["What's Changed"]
    utiles = [c for c in liste if not BRUIT.match(c[1])] or liste
    for sha, sujet, _, _ in utiles:
        lignes.append(puce(sujet_de(sha, sujet), sha))
    lignes.append("")
    lignes.append(f"Full Changelog: {precedent}...{version}")
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
            meme = git("rev-parse", "--short", tag).strip() or tag
            texte = (f"What's Changed\n"
                     f"No new commit between {precedent} and {tag} — both tags point to {meme}.  \n"
                     f"\nFull Changelog: {precedent}...{tag}")
        if nouveau:
            texte += "\n\nNew Contributors\n"
            for h, sha in dict(nouveau).items():
                texte += f"{h} made their first contribution in {sha}  \n"
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
