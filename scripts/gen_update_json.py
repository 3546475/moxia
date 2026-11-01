#!/usr/bin/env python3
"""Generate update.json — the manifest the app's in-app updater reads.

Contract (keep in sync with `UpdateInfo.kt`):

    versionCode  int    required, the ONLY value the client compares
    versionName  str    display only
    tag          str    display only, never compared
    channel      str    "stable"; a client on another channel ignores the entry
    releaseNotes str    shown as-is, may be empty
    apkUrl       map    abi -> absolute download URL
    apkSha256    map    abi -> lowercase hex digest (optional for clients, always written here)

ABI keys are `arm64-v8a`, `armeabi-v7a`, `universal` and match the asset names produced by the
release workflow (`moxia-<tag>-<abi>.apk`).

Usage (from the repository root, APKs already renamed into one directory):

    python3 scripts/gen_update_json.py \
        --tag v1.2.3 \
        --apk-dir release-apks \
        --base-url https://github.com/OWNER/REPO/releases/download/v1.2.3 \
        --notes-file release_notes.md \
        --out update.json

Exit codes: 0 ok, 2 bad arguments, 3 APK missing or unreadable.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys

ABIS = ("arm64-v8a", "armeabi-v7a", "universal")


def version_code(tag: str) -> int:
    """Same rule as `versionCodeOf` in app/build.gradle.kts: v1.2.3 -> 1002003."""
    raw = tag.strip().removeprefix("v")
    parts = raw.split("-")[0].split(".")
    if len(parts) < 3:
        raise ValueError(f"tag is not a version: {tag}")
    major, minor = int(parts[0]), int(parts[1])
    patch = int("".join(ch for ch in parts[2] if ch.isdigit()) or "-1")
    if not (0 <= major <= 99 and 0 <= minor <= 999 and 0 <= patch <= 999):
        raise ValueError(f"tag component out of range: {tag}")
    return major * 1_000_000 + minor * 1_000 + patch


def sha256_file(path: str) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def build(tag: str, apk_dir: str, base_url: str, notes: str, channel: str) -> dict:
    base = base_url.rstrip("/")
    urls, digests, sizes = {}, {}, {}
    for abi in ABIS:
        name = f"moxia-{tag}-{abi}.apk"
        path = os.path.join(apk_dir, name)
        if not os.path.isfile(path):
            raise FileNotFoundError(name)
        urls[abi] = f"{base}/{name}"
        digests[abi] = sha256_file(path)
        sizes[abi] = os.path.getsize(path)
    return {
        "schemaVersion": 1,
        "channel": channel,
        "versionCode": version_code(tag),
        "versionName": tag.removeprefix("v"),
        "tag": tag,
        "publishedAt": None,
        "releaseNotes": notes,
        "apkUrl": urls,
        "apkSha256": digests,
        "apkSize": sizes,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--tag", default=os.environ.get("GITHUB_REF_NAME"))
    parser.add_argument("--apk-dir", default=os.environ.get("APK_DIR", "release-apks"))
    parser.add_argument("--base-url", default=os.environ.get("APK_BASE_URL"))
    parser.add_argument("--notes-file", default=os.environ.get("NOTES_FILE", ""))
    parser.add_argument("--channel", default=os.environ.get("CHANNEL", "stable"))
    parser.add_argument("--out", default=os.environ.get("OUT_JSON", "update.json"))
    args = parser.parse_args()

    missing = [name for name in ("tag", "base_url") if not getattr(args, name)]
    if missing:
        print(f"missing required argument(s): {', '.join(missing)}", file=sys.stderr)
        return 2

    notes = ""
    if args.notes_file and os.path.isfile(args.notes_file):
        with open(args.notes_file, encoding="utf-8") as handle:
            notes = handle.read().strip()
    if not notes:
        # An empty changelog must not block a release; the client has its own fallback copy too.
        notes = f"Moxia {args.tag} is available."

    try:
        manifest = build(args.tag, args.apk_dir, args.base_url, notes, args.channel)
    except (FileNotFoundError, ValueError) as error:
        print(f"cannot build manifest: {error}", file=sys.stderr)
        return 3

    with open(args.out, "w", encoding="utf-8") as handle:
        json.dump(manifest, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    print(json.dumps(manifest, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
