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


def tab_target_violations(tab_pages):
    """navigateTo/redirectTo 到 tabBar 页会被微信直接拒绝(fail 回调缺失即静默死按钮)。

    【2026-09-20 实证】home 四项激励卡片/profile 激励入口/upload 取消兜底三处均为此缺陷。
    到 tab 页唯一合法路径是 switchTab。
    """
    hits = []
    nav_re = re.compile(r"wx\.(navigateTo|redirectTo)\s*\(\s*\{\s*url:\s*[`'\"]([^`'\"?]+)")
    for source in PAGES_DIR.rglob("*.js"):
        for match in nav_re.finditer(source.read_text(errors="ignore")):
            target = match.group(2).lstrip("/")
            if target in tab_pages:
                hits.append(f"{source.relative_to(ROOT / 'aigicrm-miniprogram')} -> {match.group(1)} {match.group(2)}")
    return hits


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
            # 【2026-10-12 钉随体检P1】登录后 pwdResetRequired=1 强制改密引导:
            # 后端对未改密用户非白名单路径一律 403, redirectTo 到改密页(白名单端点)是
            # 唯一出路; 非栈保持敏感的会话流, 与跳登录同类
            if rel == "pages/login/index.js" and target.lstrip("/").startswith("pages/profile/password"):
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
    tab_hits = tab_target_violations(tab_pages)
    if tab_hits:
        print("FAIL: navigateTo/redirectTo to a tabBar page is rejected by WeChat (dead button); use switchTab:")
        print("\n".join(f"  {item}" for item in tab_hits))
        return 1
    # 【F22 2026-10-13】openApp 的 switchTab 分流清单(pages/app/index.js tabPages 数组)必须与
    # app.json tabBar.list 保持一致: 增删 tab 漏改数组会退回"tabBar 页 navigateTo 静默失败"死按钮,
    # 且本检查的字面量 URL 正则抓不到映射表简写形态, 单独钉一致性。
    app_center = PAGES_DIR / "app" / "index.js"
    tab_pages_decl = re.search(r"const tabPages = \[([^\]]*)\]", app_center.read_text(encoding="utf-8"))
    if not tab_pages_decl:
        print("FAIL: pages/app/index.js no longer declares the tabPages switchTab list (#11 fix guard)")
        return 1
    declared = set(re.findall(r"['\"](/pages/[^'\"]+)['\"]", tab_pages_decl.group(1)))
    json_tabs = {p.lstrip("/") for p in tab_pages}
    if {p.lstrip("/") for p in declared} != json_tabs:
        print("FAIL: pages/app/index.js tabPages list has drifted from app.json tabBar.list:")
        print(f"  only in app/index.js: {sorted(declared - tab_pages)}")
        print(f"  only in app.json:     {sorted(tab_pages - declared)}")
        print("  add/remove a tab requires updating both, or the entry degrades to a silent dead button")
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
