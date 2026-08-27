#!/usr/bin/env python3
"""Ensure third_party/yt-dlp is checked out to a pinned (or latest) release tag.

Build-time behavior:
  - Default: read ytdlp/YTDLP_PIN (committed) for reproducible builds.
  - Override pin: set S2A_YTDLP_TAG=YYYY.MM.DD.
  - Follow latest: set S2A_YTDLP_LATEST=1 to query GitHub for the newest release.
  - Skip network: if already on the desired tag, do nothing.

Requires network unless the desired tag is already present locally.
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path

REPO_URL = "https://github.com/yt-dlp/yt-dlp.git"
API_LATEST = "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest"
PIN_FILE = Path(__file__).resolve().parents[1] / "ytdlp" / "YTDLP_PIN"


def run(
    cmd: list[str],
    cwd: Path | None = None,
    check: bool = True,
) -> subprocess.CompletedProcess[str]:
    return subprocess.run(cmd, cwd=cwd, check=check, text=True, capture_output=True)


def git_ok(cwd: Path, *args: str) -> str | None:
    result = run(["git", *args], cwd=cwd, check=False)
    if result.returncode != 0:
        return None
    return (result.stdout or "").strip()


def current_exact_tag(repo: Path) -> str | None:
    if not ((repo / ".git").exists() or (repo / ".git").is_file()):
        return None
    return git_ok(repo, "describe", "--tags", "--exact-match")


def read_pin_file() -> str | None:
    if not PIN_FILE.is_file():
        return None
    for line in PIN_FILE.read_text(encoding="utf-8").splitlines():
        tag = line.split("#", 1)[0].strip().lstrip("v")
        if tag:
            return tag
    return None


def fetch_latest_tag() -> str:
    req = urllib.request.Request(
        API_LATEST,
        headers={
            "Accept": "application/vnd.github+json",
            "User-Agent": "share2archive-fetch-ytdlp",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            data = json.load(resp)
    except urllib.error.URLError as e:
        raise SystemExit(
            f"error: could not fetch latest yt-dlp release ({e}). "
            "Unset S2A_YTDLP_LATEST and use the committed pin, or set S2A_YTDLP_TAG."
        ) from e
    tag = data.get("tag_name")
    if not tag:
        raise SystemExit(f"error: unexpected GitHub API response: {data!r}")
    return str(tag).lstrip("v")


def resolve_desired_tag() -> str:
    pinned_env = os.environ.get("S2A_YTDLP_TAG", "").strip()
    if pinned_env:
        return pinned_env.lstrip("v")
    if os.environ.get("S2A_YTDLP_LATEST", "").strip() in ("1", "true", "yes"):
        return fetch_latest_tag()
    pin = read_pin_file()
    if pin:
        return pin
    # No pin file — fall back to latest so a fresh checkout still builds.
    return fetch_latest_tag()


def ensure_repo(repo: Path) -> None:
    repo.parent.mkdir(parents=True, exist_ok=True)
    if (repo / ".git").exists() or (repo / ".git").is_file():
        return
    if repo.exists() and any(repo.iterdir()):
        raise SystemExit(
            f"error: {repo} exists but is not a git repo. "
            "Remove it or clone yt-dlp there manually."
        )
    print(f"Cloning {REPO_URL} → {repo}")
    run(["git", "clone", "--filter=blob:none", REPO_URL, str(repo)])


def checkout_tag(repo: Path, tag: str) -> None:
    fetched = run(
        ["git", "fetch", "--tags", "--force", "origin", f"refs/tags/{tag}:refs/tags/{tag}"],
        cwd=repo,
        check=False,
    )
    if fetched.returncode != 0 and git_ok(repo, "rev-parse", f"refs/tags/{tag}") is None:
        run(["git", "fetch", "--tags", "--force", "origin"], cwd=repo)

    if git_ok(repo, "rev-parse", f"refs/tags/{tag}") is None:
        raise SystemExit(f"error: tag {tag} not found after fetch")

    run(["git", "checkout", "--force", f"tags/{tag}"], cwd=repo)
    print(f"yt-dlp checked out at release tag {tag}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--repo",
        type=Path,
        default=Path(__file__).resolve().parents[1] / "third_party" / "yt-dlp",
        help="Path to the yt-dlp git checkout",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Always fetch/checkout even if already on the desired tag",
    )
    args = parser.parse_args()
    repo: Path = args.repo

    tag = resolve_desired_tag()
    ensure_repo(repo)

    on_tag = current_exact_tag(repo)
    if on_tag == tag and not args.force:
        print(f"yt-dlp already at release tag {tag}")
        return 0

    if on_tag and on_tag != tag:
        print(f"yt-dlp moving {on_tag} → {tag}")

    try:
        checkout_tag(repo, tag)
    except subprocess.CalledProcessError as e:
        err = (e.stderr or e.stdout or str(e)).strip()
        raise SystemExit(f"error: git checkout of yt-dlp tag {tag} failed:\n{err}") from e

    head = git_ok(repo, "rev-parse", "HEAD")
    tag_rev = git_ok(repo, "rev-parse", f"refs/tags/{tag}^{{}}") or git_ok(
        repo, "rev-parse", f"refs/tags/{tag}"
    )
    if head != tag_rev:
        raise SystemExit(f"error: expected tag {tag} ({tag_rev}), got HEAD {head}")

    print(f"Ready: yt-dlp {tag}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
