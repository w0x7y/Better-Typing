"""Preview the Modrinth project page; use --apply to upload the icon and gallery and update the summary, body and license.

Gallery images are matched by title: ones already on the page only get their description updated, so
running it twice doesn't upload duplicates. gallery.json is the whole gallery, so an image it no longer lists
is removed; to replace an image, give it a new title. In Modrinth/description.md, `gallery:<title>` stands
for that image's URL.
"""

import argparse
import json
import os
from pathlib import Path
import re
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
API = "https://api.modrinth.com/v2"
USER_AGENT = "w0x7y/Better-Typing (https://github.com/w0x7y/Better-Typing)"
GALLERY_LINK = re.compile(r"\(gallery:([^)]+)\)")


def load(root: Path = ROOT) -> tuple[dict, list[dict], str]:
    project = json.loads((root / "Modrinth/project.json").read_text(encoding="utf-8"))
    if not 3 <= len(project["summary"]) <= 255:
        raise ValueError("The summary must be 3 to 255 characters")
    icon = root / project["icon"]
    if icon.suffix != ".png" or icon.stat().st_size > 256 * 1024:
        raise ValueError(f"{project['icon']} must be a png under 256 KiB")
    gallery = json.loads((root / "Modrinth/gallery.json").read_text(encoding="utf-8"))
    titles = [item["title"] for item in gallery]
    if len(set(titles)) != len(titles):
        raise ValueError("Gallery titles must be distinct, they're how images are matched")
    for item in gallery:
        image = root / item["file"]
        if image.suffix != ".png" or image.stat().st_size > 5 * 1024 * 1024:
            raise ValueError(f"{item['file']} must be a png under 5 MiB")
    body = (root / "Modrinth/description.md").read_text(encoding="utf-8")
    missing = set(GALLERY_LINK.findall(body)) - set(titles)
    if missing:
        raise ValueError(f"description.md links to images not in gallery.json: {sorted(missing)}")
    return project, gallery, body


def request(method: str, path: str, token: str, data: bytes | None = None, content_type: str | None = None):
    headers = {"Authorization": token, "User-Agent": USER_AGENT}
    if content_type:
        headers["Content-Type"] = content_type
    with urllib.request.urlopen(urllib.request.Request(API + path, data, headers, method=method), timeout=60) as response:
        text = response.read().decode("utf-8")
        return json.loads(text) if text else None


def apply(project: dict, gallery: list[dict], body: str, token: str, root: Path = ROOT) -> None:
    if not token.strip():
        raise ValueError("Set MODRINTH_TOKEN with the PROJECT_WRITE scope")
    slug = urllib.parse.quote(project["id"])
    request("PATCH", f"/project/{slug}/icon?ext=png", token, (root / project["icon"]).read_bytes(), "image/png")
    print(f"Uploaded {project['icon']} as the project icon")
    live = request("GET", f"/project/{slug}", token)
    existing = {image["title"]: image for image in live["gallery"]}
    for ordering, item in enumerate(gallery):
        image = existing.get(item["title"])
        if image is not None:
            if image.get("description") != item["description"]:
                query = urllib.parse.urlencode({"url": image["url"], "description": item["description"]})
                request("PATCH", f"/project/{slug}/gallery?{query}", token)
                print(f"Updated the description of \"{item['title']}\"")
            continue
        query = urllib.parse.urlencode({
            "ext": "png",
            "featured": str(item["featured"]).lower(),
            "title": item["title"],
            "description": item["description"],
            "ordering": ordering,
        })
        request("POST", f"/project/{slug}/gallery?{query}", token, (root / item["file"]).read_bytes(), "image/png")
        print(f"Uploaded {item['file']} as \"{item['title']}\"")
    titles = {item["title"] for item in gallery}
    for image in live["gallery"]:
        if image["title"] not in titles:
            request("DELETE", f"/project/{slug}/gallery?{urllib.parse.urlencode({'url': image['url']})}", token)
            print(f"Removed \"{image['title']}\", which gallery.json no longer lists")
    urls = {image["title"]: image["url"] for image in request("GET", f"/project/{slug}", token)["gallery"]}
    body = GALLERY_LINK.sub(lambda match: f"({urls[match.group(1)]})", body)
    payload = json.dumps({"description": project["summary"], "body": body, "license_id": project["license"]}).encode("utf-8")
    request("PATCH", f"/project/{slug}", token, payload, "application/json")
    print(f"Updated the summary, description and license of {project['id']}")
    # Only an approved project shows up for everyone; a draft must first be submitted for review on the website.
    print(f"Project status: {live['status']}" + (f" (requested: {live['requested_status']})" if live.get("requested_status") else ""))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="update the live Modrinth page")
    args = parser.parse_args()
    try:
        project, gallery, body = load()
        if args.apply:
            apply(project, gallery, body, os.environ.get("MODRINTH_TOKEN", ""))
        else:
            print(json.dumps({"project": project, "gallery": gallery}, indent=2))
            print(body)
    except urllib.error.HTTPError as error:
        parser.exit(1, f"Modrinth rejected the request: HTTP {error.code} {error.read().decode('utf-8', 'replace')}\n")
    except (ValueError, KeyError, OSError) as error:
        parser.exit(1, f"Modrinth page update failed: {error}\n")


if __name__ == "__main__":
    main()
