"""Publish the language packs on Modrinth, one project per language.

Takes a folder of pack jars (as built by tools/build_language_packs.py, or downloaded from their GitHub
release) and checks each against the size and SHA-512 the mod's catalog pins. Without --apply it only
prints what it would do. With --apply it creates each missing project as a draft, uploads the pack as
a client-only version unless that version is there already, and prints each file's Modrinth URL for
the catalog. Drafts stay private until they're submitted for review on Modrinth.

Needs MODRINTH_TOKEN with the PROJECT_CREATE, PROJECT_READ, VERSION_CREATE and VERSION_WRITE scopes.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
API = "https://api.modrinth.com"
USER_AGENT = "w0x7y/Better-Typing (https://github.com/w0x7y/Better-Typing)"
REPOSITORY = "https://github.com/w0x7y/Better-Typing"
MOD_PROJECT = "better-typing"
CATALOG = ROOT / "common/src/main/resources/assets/bettertyping/languages.txt"
JAR_NAME = re.compile(r"bettertyping-([a-z]{2,3}_[a-z]{2,4})-(.+)\.jar$")


def catalog() -> dict[str, dict]:
    packs = {}
    for line in CATALOG.read_text(encoding="utf-8").splitlines():
        fields = line.split("\t")
        if line.startswith("#") or len(fields) != 6:
            continue
        packs[fields[0]] = {"name": fields[1], "size": int(fields[3]), "sha512": fields[4]}
    return packs


def game_versions() -> list[str]:
    versions = []
    for properties in sorted((ROOT / "versions").glob("*/gradle.properties")):
        for line in properties.read_text(encoding="utf-8").splitlines():
            if line.startswith("modrinth_game_versions="):
                versions += [v.strip() for v in line.split("=", 1)[1].split(",") if v.strip() not in versions]
    return versions


def slug(language: dict) -> str:
    return "better-typing-" + re.sub(r"[^a-z]+", "-", language["english"].lower()).strip("-")


def page(language: dict, config: dict) -> str:
    return f"""# Better Typing: {language['english']}

{language['english']} words for [Better Typing](https://modrinth.com/mod/{MOD_PROJECT})'s spell check and autocomplete.

You don't need to install this yourself: pick {language['name']} in Better Typing's settings and press
Save, and the mod downloads this file (checking its SHA-512) into `config/bettertyping/dictionaries/`.
Install it like a mod instead for a modpack or to play offline.

The list holds the words of [wordfreq](https://github.com/rspeer/wordfreq)'s {language['english']} list that
the LibreOffice {language['english']} dictionary accepts, most common first, up to {config['max_words']:,} words.

## Licence and credits

GNU GPL version 3. The jar's NOTICE.txt says exactly how it was made, and
[tools/build_language_packs.py]({REPOSITORY}/blob/main/tools/build_language_packs.py) rebuilds it.

- Words: {language['credit']} From the [LibreOffice dictionaries](https://github.com/LibreOffice/dictionaries).
- Word order: [wordfreq](https://github.com/rspeer/wordfreq) by Robyn Speer, data under
  [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). It's built from Google Books Ngrams,
  the Leeds Internet Corpus, Wikipedia, ParaCrawl, OPUS OpenSubtitles 2018 (from OpenSubtitles), SUBTLEX
  word lists by Marc Brysbaert et al. (freely available data), and Twitter.
"""


def request(method: str, path: str, token: str, body: bytes | None = None, content_type: str | None = None):
    headers = {"Authorization": token, "User-Agent": USER_AGENT}
    if content_type:
        headers["Content-Type"] = content_type
    with urllib.request.urlopen(urllib.request.Request(API + path, body, headers, method=method), timeout=120) as response:
        text = response.read().decode("utf-8")
        return json.loads(text) if text else None


def multipart(data: dict, files: dict[str, tuple[str, bytes, str]]) -> tuple[bytes, str]:
    """A multipart/form-data body: a "data" JSON part, then one part per file."""
    boundary = uuid.uuid4().hex
    parts = [f'--{boundary}\r\nContent-Disposition: form-data; name="data"\r\nContent-Type: application/json\r\n\r\n'.encode()
             + json.dumps(data).encode() + b"\r\n"]
    for field, (filename, content, mime) in files.items():
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{field}"; filename="{filename}"\r\n'
                     f"Content-Type: {mime}\r\n\r\n".encode() + content + b"\r\n")
    parts.append(f"--{boundary}--\r\n".encode())
    return b"".join(parts), f"multipart/form-data; boundary={boundary}"


def find(path: str, token: str):
    try:
        return request("GET", path, token)
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return None
        raise


def publish(jars: Path, token: str, apply: bool) -> None:
    config = json.loads((ROOT / "tools/language_packs.json").read_text(encoding="utf-8"))
    pinned = catalog()
    versions = game_versions()
    icon = (ROOT / "assets/icon.png").read_bytes()
    mod = request("GET", f"/v2/project/{MOD_PROJECT}", token) if apply else {"id": MOD_PROJECT}

    for jar in sorted(jars.glob("*.jar")):
        match = JAR_NAME.search(jar.name)
        if not match or match.group(1) not in config["languages"]:
            raise ValueError(f"{jar.name} isn't a language pack")
        code, version = match.groups()
        data = jar.read_bytes()
        expected = pinned.get(code)
        if not expected or len(data) != expected["size"] or hashlib.sha512(data).hexdigest() != expected["sha512"]:
            raise ValueError(f"{jar.name} doesn't match its size and SHA-512 in {CATALOG.name}")
        language = config["languages"][code]
        project_slug = slug(language)
        print(f"{code}: {jar.name} -> https://modrinth.com/mod/{project_slug}, version {version}")
        if not apply:
            continue

        project = find(f"/v2/project/{project_slug}", token)
        if project is None:
            body, content_type = multipart({
                "slug": project_slug,
                "title": f"Better Typing: {language['english']}",
                "description": f"{language['english']} words for Better Typing's spell check and autocomplete. "
                               f"The mod downloads it when you pick {language['name']}, or install it alongside.",
                "categories": ["utility"],
                "client_side": "required",
                "server_side": "unsupported",
                "body": page(language, config),
                "license_id": "GPL-3.0-only",
                "source_url": REPOSITORY,
                "issues_url": f"{REPOSITORY}/issues",
                "project_type": "mod",
                "initial_versions": [],
                "is_draft": True,
            }, {"icon": ("icon.png", icon, "image/png")})
            project = request("POST", "/v2/project", token, body, content_type)
            print(f"  created the draft project {project['slug']}")

        existing = {v["version_number"]: v for v in request("GET", f"/v2/project/{project['id']}/version", token)}
        uploaded = existing.get(version)
        if uploaded is None:
            body, content_type = multipart({
                "name": f"Better Typing: {language['english']} {version}",
                "version_number": version,
                "changelog": f"{language['english']} word list, built from LibreOffice dictionaries commit "
                             f"{config['libreoffice_commit'][:12]} and wordfreq {config['wordfreq_version']}.",
                "dependencies": [{"project_id": mod["id"], "dependency_type": "required"}],
                "game_versions": versions,
                "version_type": "release",
                "loaders": ["fabric"],
                "featured": True,
                "project_id": project["id"],
                "file_parts": ["file"],
                "primary_file": "file",
            }, {"file": (jar.name, data, "application/java-archive")})
            uploaded = request("POST", "/v2/version", token, body, content_type)
            print(f"  uploaded version {version}")
        request("PATCH", f"/v3/version/{uploaded['id']}", token, json.dumps({"environment": "client_only"}).encode(), "application/json")
        url = next(f["url"] for f in uploaded["files"] if f["hashes"].get("sha512") == expected["sha512"])
        print(f"  {code}\t{url}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("jars", type=Path, help="folder holding the pack jars")
    parser.add_argument("--apply", action="store_true", help="create the projects and upload; otherwise only print")
    args = parser.parse_args()
    token = os.environ.get("MODRINTH_TOKEN", "")
    try:
        if args.apply and not token.strip():
            raise ValueError("Set MODRINTH_TOKEN with the PROJECT_CREATE, PROJECT_READ, VERSION_CREATE and VERSION_WRITE scopes")
        publish(args.jars, token, args.apply)
    except urllib.error.HTTPError as error:
        parser.exit(1, f"Modrinth rejected the request: HTTP {error.code} {error.read().decode('utf-8', 'replace')}\n")
    except (ValueError, KeyError, OSError, StopIteration) as error:
        parser.exit(1, f"Publishing the language packs failed: {error!r}\n")


if __name__ == "__main__":
    main()
