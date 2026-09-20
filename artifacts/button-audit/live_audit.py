#!/usr/bin/env python3
"""AIGICRM 活体逐按钮审计(2026-09-20)

对每个角色 × 每个路由, 逐个点击页面上的每个 el-button, 捕获其触发的后端请求与响应码。
判定: 严格一对一 —— 按钮点击后未产生任何 /api/ 请求 = 缺陷(前端-only 或无后端)。

用法:
  python3 live_audit.py --role admin --routes routes_admin.json --out out_admin.json
"""
import argparse
import json
import re
import time
from pathlib import Path

from playwright.sync_api import sync_playwright

SECRET = Path("/tmp/aigicrm-btn-audit")
PW = (SECRET / "pw.txt").read_text().strip()
BASE = "http://localhost:3000"


def login(page, username):
    page.goto(BASE + "/login", wait_until="networkidle")
    page.fill("input[placeholder='请输入账号']", username)
    page.fill("input[placeholder='请输入密码']", PW)
    page.click("button:has-text('登')")
    page.wait_for_timeout(3500)
    if "/login" in page.url:
        # 再等一次, 兼容慢响应
        page.wait_for_timeout(3000)
    if "/login" in page.url:
        raise RuntimeError("login failed for %s (still on /login)" % username)
    page.wait_for_load_state("networkidle")


def enumerate_buttons(page):
    """枚举页面上可见的 el-button, 按标签去重(同一功能的行内重复按钮只审计一次)。"""
    seen, out = {}, []
    btns = page.locator("button.el-button")
    for i in range(btns.count()):
        b = btns.nth(i)
        try:
            txt = (b.inner_text() or "").strip().replace("\n", " ")
            vis = b.is_visible()
        except Exception:  # noqa: BLE001
            txt, vis = "", False
        key = re.sub(r"\s+", "", txt) or ("icon-only#%d" % i)
        if key in seen:
            seen[key]["instances"] += 1
            continue
        rec = {"index": i, "text": txt[:40], "visible": vis, "instances": 1}
        seen[key] = rec
        out.append(rec)
    return out


PRIMARY_WORDS = ["确定", "确认", "保存", "提交", "是", "保 存", "确 定", "删 除", "删除"]


def _click_primary_in(page, scope_sel):
    """在给定容器内点主操作按钮(确定/保存/提交...)。返回点击到的按钮文字或 None。"""
    scopes = [scope_sel] if scope_sel else [".el-message-box", ".el-dialog", ".el-drawer"]
    for sel in scopes:
        box = page.locator(sel)
        for i in range(box.count()):
            b = box.nth(i)
            try:
                if not b.is_visible():
                    continue
            except Exception:  # noqa: BLE001
                continue
            for w in PRIMARY_WORDS:
                cand = b.locator("button:has-text('%s')" % w)
                if cand.count():
                    try:
                        cand.last.click(timeout=2500)
                        return w
                    except Exception:  # noqa: BLE001
                        continue
    return None


def interact_and_capture(page, index):
    """点击第 index 个按钮, 若弹出对话框/确认框则尝试提交, 捕获整条交互的后端请求。"""
    reqs, console_errors, pending = [], [], {}

    def on_request(r):
        if "/api/" in r.url:
            pending[r] = {"method": r.method, "path": r.url.split("/api/")[-1].split("?")[0],
                          "url": r.url.split("?")[0]}

    def on_response(r):
        req = r.request
        if req in pending:
            rec = pending.pop(req)
            rec["status"] = r.status
            reqs.append(rec)

    def on_console(m):
        if m.type == "error":
            console_errors.append(m.text[:200])

    page.on("request", on_request)
    page.on("response", on_response)
    page.on("console", on_console)

    dialog_opened, submitted = False, False
    try:
        btns = page.locator("button.el-button")
        if index >= btns.count():
            return {"dialog": False, "submitted": False, "api_requests": [],
                    "console_errors": ["index-out-of-range"]}
        btns.nth(index).click(timeout=5000, force=True)
        page.wait_for_timeout(1100)
    except Exception as exc:  # noqa: BLE001
        console_errors.append("click-error: %s" % str(exc)[:150])

    # 两轮: 处理"确认框"与"对话框提交"(提交后可能再弹确认框)
    for _ in range(2):
        box = page.locator(".el-message-box")
        dlg = page.locator(".el-dialog, .el-drawer")
        visible_box = any(box.nth(i).is_visible() for i in range(box.count()))
        visible_dlg = any(dlg.nth(i).is_visible() for i in range(dlg.count()))
        if not (visible_box or visible_dlg):
            break
        dialog_opened = True
        hit = _click_primary_in(page, ".el-message-box" if visible_box else ".el-dialog, .el-drawer")
        if hit:
            submitted = True
            page.wait_for_timeout(1300)
        else:
            break

    page.wait_for_timeout(400)
    for rec in pending.values():
        rec.setdefault("status", None)
        reqs.append(rec)
    pending.clear()
    page.keyboard.press("Escape")
    page.wait_for_timeout(250)
    page.remove_listener("request", on_request)
    page.remove_listener("response", on_response)
    page.remove_listener("console", on_console)
    return {"dialog": dialog_opened, "submitted": submitted,
            "api_requests": reqs, "console_errors": console_errors}


def dismiss_dialogs(page):
    """关闭残留弹层, 避免状态串扰。"""
    try:
        page.keyboard.press("Escape")
        page.wait_for_timeout(150)
    except Exception:  # noqa: BLE001
        pass


def _report(entry, label):
    n_ok = sum(1 for b in entry["buttons"] if b.get("has_backend"))
    n_all = len([b for b in entry["buttons"] if "skipped" not in b])
    print("  %-46s buttons=%2d withBackend=%2d" % (label[:46], n_all, n_ok), flush=True)


def _sweep(page, role, route, label, reset_fn):
    """枚举当前页面按钮, 每个按钮点击前调用 reset_fn() 恢复干净状态。"""
    entry = {"role": role, "route": route, "label": label,
             "page_url": page.url, "buttons": []}
    btns = enumerate_buttons(page)
    for b in btns:
        if not b["visible"]:
            entry["buttons"].append({**b, "skipped": "not-visible", "api_requests": []})
            continue
        reset_fn()
        res = interact_and_capture(page, b["index"])
        entry["buttons"].append({
            **b,
            "dialog": res["dialog"],
            "submitted": res["submitted"],
            "api_requests": res["api_requests"],
            "console_errors": res["console_errors"],
            "has_backend": len(res["api_requests"]) > 0,
        })
    return entry


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--role", required=True)
    ap.add_argument("--routes", required=True, help="JSON 文件: [{route,label}]")
    ap.add_argument("--out", required=True)
    ap.add_argument("--max-routes", type=int, default=0)
    args = ap.parse_args()

    routes = json.loads(Path(args.routes).read_text(encoding="utf-8"))
    if args.max_routes:
        routes = routes[:args.max_routes]

    results = []
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        ctx = browser.new_context()
        page = ctx.new_page()
        login(page, args.role)
        print("logged in as %s" % args.role, flush=True)

        for r in routes:
            route, label = r["route"], r.get("label", "")
            try:
                page.goto(BASE + route, wait_until="networkidle", timeout=25000)
                page.wait_for_timeout(1200)
            except Exception as exc:  # noqa: BLE001
                results.append({"role": args.role, "route": route, "label": label,
                                "error": "nav-failed: %s" % str(exc)[:150], "buttons": []})
                print("  NAV FAIL %s" % route, flush=True)
                continue

            # tab 展开: 逐个激活 el-tabs__item, 每个 tab 独立采集按钮
            tab_labels = r.get("tabs") or []
            if tab_labels:
                for tl in tab_labels:
                    def reset_tab(_tl=tl):
                        page.goto(BASE + route, wait_until="networkidle", timeout=25000)
                        page.wait_for_timeout(700)
                        try:
                            t = page.locator(".el-tabs__item", has_text=_tl).first
                            if t.count():
                                t.click()
                                page.wait_for_timeout(900)
                        except Exception:  # noqa: BLE001
                            pass
                    # 先激活一次以便枚举
                    try:
                        t0 = page.locator(".el-tabs__item", has_text=tl).first
                        if not t0.count():
                            continue
                        t0.click()
                        page.wait_for_timeout(1000)
                    except Exception:  # noqa: BLE001
                        continue
                    entry = _sweep(page, args.role, route,
                                   "%s / tab:%s" % (label, tl), reset_tab)
                    results.append(entry)
                    _report(entry, "%s [%s]" % (route, tl))
                page.goto(BASE + route, wait_until="networkidle", timeout=25000)
                page.wait_for_timeout(800)

            btns = enumerate_buttons(page)
            entry = {"role": args.role, "route": route, "label": label,
                     "page_url": page.url, "buttons": []}
            for b in btns:
                if not b["visible"]:
                    entry["buttons"].append({**b, "skipped": "not-visible",
                                             "api_requests": []})
                    continue
                # 重新导航获取干净状态
                try:
                    page.goto(BASE + route, wait_until="networkidle", timeout=25000)
                    page.wait_for_timeout(900)
                except Exception:  # noqa: BLE001
                    pass
                res = interact_and_capture(page, b["index"])
                entry["buttons"].append({
                    **b,
                    "dialog": res["dialog"],
                    "submitted": res["submitted"],
                    "api_requests": res["api_requests"],
                    "console_errors": res["console_errors"],
                    "has_backend": len(res["api_requests"]) > 0,
                })
            results.append(entry)
            _report(entry, route)

        browser.close()

    Path(args.out).write_text(json.dumps(results, ensure_ascii=False, indent=1),
                              encoding="utf-8")
    total = sum(len([b for b in e.get("buttons", []) if "skipped" not in b]) for e in results)
    ok = sum(1 for e in results for b in e.get("buttons", []) if b.get("has_backend"))
    print("\nDONE %s: buttons=%d withBackend=%d noBackend=%d" % (args.role, total, ok, total - ok))


if __name__ == "__main__":
    main()
