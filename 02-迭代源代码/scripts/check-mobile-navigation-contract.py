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


def onload_bodies_missing_options(text):
    """Find onLoad() declarations with empty params whose own body reads bare `options.`.

    Body-scoped: an `options.` in another function (e.g. saveFile options,
    picker options) must not flag the page. A locally declared
    const/let/var options inside the body is also fine.
    """
    hits = []
    for match in re.finditer(r"\bonLoad\(\s*\)\s*\{", text):
        depth = 1
        i = match.end()
        while i < len(text) and depth > 0:
            if text[i] == "{":
                depth += 1
            elif text[i] == "}":
                depth -= 1
            i += 1
        body = text[match.end():i - 1]
        if re.search(r"\boptions\s*\.", body) and not re.search(
                r"\b(?:const|let|var)\s+options\b", body):
            hits.append(True)
    return hits


# 【PR-12 滚动位置保持】结构性保证的静态固化:
# 1) 列表页均为页面级滚动 + 详情走 navigateTo, 返回时微信原生保留滚动位置;
#    任何 wx.pageScrollTo(跳顶)都会破坏该保证 —— 如确需"回顶部"按钮, 显式修订本契约。
# 2) redirectTo/reLaunch 会销毁页面栈: 仅允许 ①跳登录的会话流 ②file/cases 遗留别名壳。
#    业务流(如详情删除后回列表)必须 navigateBack, 否则返回手势回旧数据且滚动丢失。
PAGE_SCROLL_TO_RE = re.compile(r"wx\.pageScrollTo\s*\(")
REDIRECT_RE = re.compile(r"wx\.(redirectTo|reLaunch)\s*\(\s*\{\s*url:\s*[`'\"](/pages/[^`'\"?]+)")
LEGACY_REDIRECT_SHELLS = {
    "pages/file/index.js",
    "pages/cases/index.js",
    "pages/cases/detail/index.js",
}


def scroll_preservation_violations():
    page_scroll_hits = []
    stack_breakers = []
    for source in PAGES_DIR.rglob("*.js"):
        text = source.read_text(errors="ignore")
        rel = str(source.relative_to(ROOT / "aigicrm-miniprogram"))
        if PAGE_SCROLL_TO_RE.search(text):
            page_scroll_hits.append(rel)
        for match in REDIRECT_RE.finditer(text):
            target = match.group(2)
            if rel in LEGACY_REDIRECT_SHELLS:
                continue
            if target.lstrip("/").startswith("pages/login"):
                continue
            stack_breakers.append(f"{rel} -> {target}")
    return page_scroll_hits, stack_breakers


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
        if source.suffix == ".js":
            if onload_bodies_missing_options(text):
                missing_onload_options.append(str(source.relative_to(ROOT)))
        elif re.search(r"\bonLoad\(\s*\)", text) and re.search(r"\boptions\.", text):
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
    page_scroll_hits, stack_breakers = scroll_preservation_violations()
    if page_scroll_hits:
        print("FAIL: wx.pageScrollTo breaks native scroll preservation on back navigation:")
        print("\n".join(f"  {item}" for item in page_scroll_hits))
        return 1
    if stack_breakers:
        print("FAIL: redirectTo/reLaunch destroys the page stack (back gesture loses data+scroll);")
        print("      only login-session flows and legacy alias shells may redirect:")
        print("\n".join(f"  {item}" for item in stack_breakers))
        return 1
    source_targets = targets - tab_pages
    print(
        "PASS: checked "
        f"{len(source_targets)} source navigation targets + "
        f"{len(tab_pages)} tabBar entry points = {len(targets)} reachable pages "
        f"against {len(registered)} registered pages; "
        "scroll preservation holds (no pageScrollTo, no stack-breaking redirect)."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
