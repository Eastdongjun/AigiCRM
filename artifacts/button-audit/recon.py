#!/usr/bin/env python3
"""侦察: 登录流程选择器 + 页面按钮枚举方式验证。"""
from pathlib import Path
from playwright.sync_api import sync_playwright

SECRET = Path("/tmp/aigicrm-btn-audit")
PW = (SECRET / "pw.txt").read_text().strip()
BASE = "http://localhost:3000"


def main():
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page()
        api_calls = []
        page.on("request", lambda r: api_calls.append((r.method, r.url))
                if "/api/" in r.url else None)

        page.goto(BASE + "/login", wait_until="networkidle")
        print("== login page inputs ==")
        for el in page.locator("input").all():
            print("  input:", el.get_attribute("placeholder"), "|", el.get_attribute("type"))

        page.fill("input[placeholder*='用户名']", "admin")
        page.fill("input[type='password']", PW)
        page.click("button:has-text('登录')")
        page.wait_for_url(lambda u: "/login" not in u, timeout=15000)
        page.wait_for_load_state("networkidle")
        print("after login url:", page.url)
        print("api calls during login:", len(api_calls))

        # 侦察一个按钮密集页
        page.goto(BASE + "/customer", wait_until="networkidle")
        page.wait_for_timeout(1500)
        btns = page.locator("button.el-button")
        print("== /customer buttons: %d ==" % btns.count())
        for i in range(min(btns.count(), 12)):
            b = btns.nth(i)
            txt = (b.inner_text() or "").strip().replace("\n", " ")[:24]
            print("  [%d] visible=%s text=%r" % (i, b.is_visible(), txt))
        page.screenshot(path=str(SECRET / "recon_customer.png"), full_page=False)
        browser.close()


if __name__ == "__main__":
    main()
