#!/usr/bin/env python3
"""AigiCRM 前端驱动 — 登录 + 主流程截图.

用于 run-aigicrm skill: 前端 vite dev server(http://localhost:3000) 必须已运行,
后端 http://localhost:8080 必须已运行(经 vite /api 代理).

用法:
  python3 driver.py                        # 登录 → 截图控制台 → /tmp/aigicrm-dashboard.png
  python3 driver.py --flow customer        # 登录 → 打开客户列表 → 截图
  python3 driver.py --user x --password y  # 指定账号
  python3 driver.py --headed               # 有头模式(本机可看窗口)
  python3 driver.py --out /tmp/x.png       # 指定截图路径
"""
import argparse
import importlib
import json
import sys
from pathlib import Path
from urllib import error, request


def main() -> int:
    try:
        sync_playwright = importlib.import_module("playwright.sync_api").sync_playwright
    except ModuleNotFoundError:
        print(
            "缺少 Python 依赖 playwright。请先安装：python3 -m pip install playwright && python3 -m playwright install chrome",
            file=sys.stderr,
        )
        return 2

    ap = argparse.ArgumentParser(description="Drive the AigiCRM web app")
    ap.add_argument("--url", default="http://localhost:3000")
    ap.add_argument("--api-url", default=None,
                    help="API base URL, defaults to <url>/api so Vite proxy can be used")
    ap.add_argument("--user", default="admin")
    ap.add_argument("--password", default="admin123")
    ap.add_argument("--auth-mode", default="api", choices=["api", "form"],
                    help="api: login via /api/auth/login and seed localStorage; form: fill the login page")
    default_out = Path.home() / ".cache" / "aigicrm" / "aigicrm-dashboard.png"
    ap.add_argument("--out", default=str(default_out))
    ap.add_argument("--flow", default="dashboard", choices=["dashboard", "customer", "customer-add"])
    ap.add_argument("--headed", action="store_true")
    ap.add_argument("--screenshot-only", action="store_true",
                    help="已登录状态直接打开目标页截图(跳过登录, 用于在已登录会话中截图)")
    args = ap.parse_args()

    with sync_playwright() as p:
        browser = p.chromium.launch(channel="chrome", headless=not args.headed)
        page = browser.new_page(viewport={"width": 1440, "height": 900})
        errors = []
        page.on("pageerror", lambda e: errors.append(str(e)))
        page.on("console", lambda m: errors.append(f"console[{m.type}]: {m.text}")
                if m.type in ("error",) else None)

        if not args.screenshot_only:
            if args.auth_mode == "api":
                api_base = (args.api_url or args.url.rstrip("/") + "/api").rstrip("/")
                payload = json.dumps({
                    "username": args.user,
                    "password": args.password,
                    "rememberMe": True,
                }).encode("utf-8")
                req = request.Request(
                    api_base + "/auth/login",
                    data=payload,
                    headers={"Content-Type": "application/json"},
                    method="POST",
                )
                opener = request.build_opener(request.ProxyHandler({}))
                try:
                    with opener.open(req, timeout=30) as resp:
                        body = json.loads(resp.read().decode("utf-8"))
                except error.HTTPError as exc:
                    try:
                        body = json.loads(exc.read().decode("utf-8"))
                    except Exception:
                        body = {"code": exc.code, "message": exc.reason}
                token = body.get("data", {}).get("token")
                if body.get("code") != 200 or not token:
                    print(f"API 登录失败: code={body.get('code')} message={body.get('message')}", file=sys.stderr)
                    browser.close()
                    return 1
                page.goto(args.url + "/login", wait_until="domcontentloaded", timeout=30000)
                page.evaluate("(token) => localStorage.setItem('token', token)", token)
                page.goto(args.url + "/dashboard", wait_until="networkidle", timeout=30000)
                page.wait_for_selector("text=客户总数", timeout=30000)
                print(f"API 登录成功: {page.url}", file=sys.stderr)
            else:
                # ---- 登录 ----
                page.goto(args.url + "/login", wait_until="networkidle", timeout=30000)
                page.fill('input[placeholder="请输入账号"]', args.user)
                page.fill('input[placeholder="请输入密码"]', args.password)
                page.click("button:has-text('登 录')")
                # 登录成功 → SPA 跳到 /dashboard。wait_for_url 对 SPA 路由跳转不可靠,
                # 改为等待工作台标志元素(客户总数统计卡)。
                page.wait_for_selector("text=客户总数", timeout=30000)
                print(f"登录成功: {page.url}", file=sys.stderr)

        # ---- 目标页 ----
        if args.flow == "customer":
            page.goto(args.url + "/customer", wait_until="networkidle", timeout=30000)
            page.wait_for_timeout(1500)
        elif args.flow == "customer-add":
            page.goto(args.url + "/customer/add", wait_until="networkidle", timeout=30000)
            page.wait_for_timeout(1500)
        # else: dashboard(登录后已在控制台)

        output_path = Path(args.out).expanduser()
        output_path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        page.screenshot(path=str(output_path), full_page=False)
        print(f"✅ 截图: {args.out}", file=sys.stderr)
        print(f"   URL: {page.url}", file=sys.stderr)
        print(f"   标题: {page.title()}", file=sys.stderr)
        if errors:
            print(f"   ⚠️ 页面错误 {len(errors)} 条:", file=sys.stderr)
            for e in errors[:5]:
                print(f"     - {e[:150]}", file=sys.stderr)

        # 关停
        browser.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
