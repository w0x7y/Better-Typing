"""Manage the Modrinth project's versions.

--environment marks every version as client-only. Minotaur can't set it, so the release workflow
runs this after uploading. --delete MOD_VERSION removes every upload of that mod version, e.g.
0.1.0 removes 0.1.0+1.21.11, 0.1.0+26.1 and so on. Both need MODRINTH_TOKEN.
"""

import argparse
import json
import os
from pathlib import Path
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
API = "https://api.modrinth.com/v3"
USER_AGENT = "w0x7y/Better-Typing (https://github.com/w0x7y/Better-Typing)"
ENVIRONMENT = "client_only"


def request(method: str, path: str, token: str, payload: dict | None = None):
    headers = {"Authorization": token, "User-Agent": USER_AGENT}
    data = None
    if payload is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(payload).encode("utf-8")
    with urllib.request.urlopen(urllib.request.Request(API + path, data, headers, method=method), timeout=60) as response:
        text = response.read().decode("utf-8")
        return json.loads(text) if text else None


def versions(token: str) -> list[dict]:
    project = json.loads((ROOT / "Modrinth/project.json").read_text(encoding="utf-8"))["id"]
    return request("GET", f"/project/{urllib.parse.quote(project)}/version", token)


def set_environment(token: str) -> None:
    for version in versions(token):
        if version.get("environment") == ENVIRONMENT:
            continue
        request("PATCH", f"/version/{version['id']}", token, {"environment": ENVIRONMENT})
        print(f"Marked {version['version_number']} as {ENVIRONMENT}")
    wrong = [v["version_number"] for v in versions(token) if v.get("environment") != ENVIRONMENT]
    if wrong:
        raise ValueError(f"Modrinth didn't keep the environment for {wrong}")


def delete(mod_version: str, token: str) -> None:
    matching = [v for v in versions(token) if v["version_number"].startswith(mod_version + "+")]
    if not matching:
        raise ValueError(f"No versions of {mod_version} on Modrinth")
    for version in matching:
        request("DELETE", f"/version/{version['id']}", token)
        print(f"Deleted {version['version_number']}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--environment", action="store_true", help="mark every version as client-only")
    parser.add_argument("--delete", metavar="MOD_VERSION", help="delete every upload of this mod version")
    args = parser.parse_args()
    token = os.environ.get("MODRINTH_TOKEN", "")
    try:
        if not token.strip():
            raise ValueError("Set MODRINTH_TOKEN with the VERSION_WRITE and VERSION_DELETE scopes")
        if args.delete:
            delete(args.delete, token)
        if args.environment:
            set_environment(token)
    except urllib.error.HTTPError as error:
        parser.exit(1, f"Modrinth rejected the request: HTTP {error.code} {error.read().decode('utf-8', 'replace')}\n")
    except (ValueError, KeyError, OSError) as error:
        parser.exit(1, f"Modrinth version update failed: {error}\n")


if __name__ == "__main__":
    main()
