#!/usr/bin/env python3
"""Verify static Mini Program navigation targets are registered in app.json."""

import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
APP_JSON = ROOT / "aigicrm-miniprogram" / "app.json"
PAGES_DIR = ROOT / "aigicrm-miniprogram" / "pages"
URL_RE = re.compile(r"[`'\"](/pages/[^`'\"?]+)(?:\?[^`'\"]*)?[`'\"]")
SWITCH_TAB_RE = re.compile(r"switchTab\(\{\s*url:\s*[`'\"](/pages/[^`'\"?]+)")


def main() -> int:
    app = json.loads(APP_JSON.read_text())
    registered = set(app['pages'])
    tab_pages = {item['pagePath'] for item in app.get('tabBar', {}).get('list', [])}
    missing = []
    invalid_tab_targets = []
    missing_onload_options = []
    targets = set()
    # A tab page is reached from the platform-owned tab bar, so it does not
    # need a self-navigation literal in its own source file.
    targets.update(tab_pages)
    for source in list(PAGES_DIR.rglob("*.js")) + list(PAGES_DIR.rglob("*.wxml")):
        text = source.read_text(errors="ignore")
        if re.search(r"\bonLoad\(\s*\)", text) and re.search(r"\boptions\.", text):
            missing_onload_options.append(str(source.relative_to(ROOT)))
        for match in URL_RE.finditer(text):
            target = match.group(1).lstrip("/")
            targets.add(target)
            if target not in registered:
                missing.append(f"{source.relative_to(ROOT)} -> /{target}")
        for match in SWITCH_TAB_RE.finditer(text):
            target = match.group(1).lstrip('/')
            if target not in tab_pages:
                invalid_tab_targets.append(f"{source.relative_to(ROOT)} -> /{target}")
    if missing:
        print("FAIL: unregistered Mini Program navigation targets:")
        print("\n".join(f"  {item}" for item in sorted(set(missing))))
        return 1
    if invalid_tab_targets:
        print("FAIL: switchTab targets are not registered in tabBar:")
        print("\n".join(f"  {item}" for item in sorted(set(invalid_tab_targets))))
        return 1
    if missing_onload_options:
        print("FAIL: pages read options but declare onLoad without options:")
        print("\n".join(f"  {item}" for item in sorted(set(missing_onload_options))))
        return 1
    source_targets = targets - tab_pages
    print(
        "PASS: checked "
        f"{len(source_targets)} source navigation targets + "
        f"{len(tab_pages)} tabBar entry points = {len(targets)} reachable pages "
        f"against {len(registered)} registered pages."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
