#!/usr/bin/env python3
"""Источник AltStore для релиза: altstore.json с последней версией FitFat.

Запускает release.yml: python3 iosApp/altstore_source.py <ipa> <версия> <сборка> <тег> <owner/repo> <notes.md> <out.json>
Файл прикладывается к релизу, поэтому постоянный адрес источника —
https://github.com/<owner/repo>/releases/latest/download/altstore.json (бета-релизы в «latest» не попадают).
"""
import datetime
import json
import os
import sys

ipa, version, build, tag, repo, notes_path, out = sys.argv[1:8]
notes = open(notes_path, encoding="utf-8").read().strip() if os.path.exists(notes_path) else "Исправления и улучшения."
icon = f"https://raw.githubusercontent.com/{repo}/main/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/icon-1024.png"
url = f"https://github.com/{repo}/releases/download/{tag}/{os.path.basename(ipa)}"
date = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
size = os.path.getsize(ipa)
about = "Трекер веса и КБЖУ: дневник еды, нормы по дням, график веса. Данные хранятся на телефоне."

app = {
    "name": "FitFat",
    "bundleIdentifier": "app.fitfat",
    "developerName": repo.split("/")[0],
    "subtitle": "Трекер веса и КБЖУ",
    "localizedDescription": about,
    "iconURL": icon,
    "tintColor": "#2A9A83",
    "category": "lifestyle",
    "screenshots": [],
    "versions": [{
        "version": version,
        "buildVersion": build,
        "date": date,
        "localizedDescription": notes,
        "downloadURL": url,
        "size": size,
        "minOSVersion": "15.0",
    }],
    "appPermissions": {"entitlements": [], "privacy": {}},
    # Поля старого формата — для прежних версий AltStore и SideStore
    "version": version,
    "versionDate": date,
    "versionDescription": notes,
    "downloadURL": url,
    "size": size,
}
source = {
    "name": "FitFat",
    "identifier": "app.fitfat.source",
    "subtitle": "Трекер веса и КБЖУ",
    "description": about,
    "iconURL": icon,
    "website": f"https://github.com/{repo}",
    "tintColor": "#2A9A83",
    "featuredApps": ["app.fitfat"],
    "apps": [app],
    "news": [],
}
with open(out, "w", encoding="utf-8") as f:
    json.dump(source, f, ensure_ascii=False, indent=2)
print(f"{out}: {version} ({build}), {size} байт, {url}")
