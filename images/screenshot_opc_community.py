"""
OPC Community 截图脚本 (W52)
- Playwright headless Chromium
- 截图 4 张:
    01-login.png        RuoYi 登录页
    02-market.png       模块市场首页
    03-market-filter.png   按 CRM 分类过滤
    04-market-detail.png   模块详情 drawer
"""

import os
import sys
import json
import time
import urllib.request
from pathlib import Path
from playwright.sync_api import sync_playwright

OUTPUT_DIR = Path(r"D:\work-ai\0401-lumen-opc\images\opc-community")
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

FRONTEND = "http://localhost:8079"
BACKEND_GATEWAY = "http://localhost:8080"
BACKEND_COMMUNITY = "http://localhost:9316"

# RuoYi 默认账号
USERNAME = "admin"
PASSWORD = "admin123"


def shoot(page, name: str, full_page: bool = True):
    out = OUTPUT_DIR / name
    page.screenshot(path=str(out), full_page=full_page)
    print(f"  [OK] {out.name} ({out.stat().st_size // 1024} KB)")


def login_and_inject_token(ctx):
    """Bypass captcha: hit /login API directly to get token, inject as cookie."""
    print("  -- bypass captcha via /login API")
    req = urllib.request.Request(
        f"{BACKEND_GATEWAY}/login",
        data=json.dumps({"username": USERNAME, "password": PASSWORD}).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=10) as resp:
        body = json.loads(resp.read().decode())
    if body.get("code") != 200:
        raise RuntimeError(f"login failed: {body}")
    token = body["data"]["access_token"]
    # RuoYi 存在 cookie 'Admin-Token' (js-cookie),需在 frontend 域名下设置
    page = ctx.new_page()
    page.goto(f"{FRONTEND}/login")
    page.evaluate(
        """([token]) => {
            // js-cookie 设置
            const expires = new Date(Date.now() + 8*3600*1000).toUTCString();
            document.cookie = `Admin-Token=${token}; expires=${expires}; path=/`;
            // 同时也写到 localStorage 备用
            localStorage.setItem('Admin-Token', token);
            localStorage.setItem('token', token);
        }""",
        [token],
    )
    page.close()
    return token


def main():
    with sync_playwright() as p:
        browser = p.chromium.launch(
            headless=True,
            args=["--no-sandbox", "--disable-dev-shm-usage"],
        )
        ctx = browser.new_context(
            viewport={"width": 1440, "height": 900},
            locale="zh-CN",
            timezone_id="Asia/Shanghai",
        )
        page = ctx.new_page()
        page.set_default_timeout(15000)

        # ============ 1. 登录页 ============
        print("[1/5] Login page")
        page.goto(f"{FRONTEND}/login")
        page.wait_for_load_state("networkidle", timeout=20000)
        time.sleep(1)
        shoot(page, "01-login.png", full_page=False)

        # ============ 2. 登录 (bypass captcha) ============
        print("[2/5] Login as admin (bypass captcha)")
        try:
            login_and_inject_token(ctx)
            print("  -- token injected")
        except Exception as e:
            print(f"  [warn] token injection failed: {e}")

        # ============ 3. 模块市场 ============
        print("[3/5] Market page")
        # vue-router 4 createWebHistory → URL 直接 /opc/community/market
        page.goto(f"{FRONTEND}/opc/community/market", wait_until="networkidle", timeout=30000)
        time.sleep(5)
        # 校验是否真的到了 market
        try:
            page.wait_for_selector('h1:has-text("OPC 模块市场"), .hero', timeout=10000)
            print(f"  [OK] market page rendered, URL: {page.url}")
        except Exception as e:
            print(f"  [warn] market page not loaded: {e}, URL: {page.url}")
        shoot(page, "02-market.png", full_page=True)

        # ============ 4. 按 CRM 分类过滤 ============
        print("[4/5] Filter by CRM")
        try:
            selects = page.locator('.el-form-item .el-select')
            if selects.count() > 0:
                selects.first.click()
                time.sleep(1)
                page.click('.el-select-dropdown__item:has-text("CRM")', timeout=5000)
                time.sleep(2)
            else:
                print("  [warn] no select found")
        except Exception as e:
            print(f"  [warn] filter failed: {e}")
        shoot(page, "03-market-filter-crm.png", full_page=True)

        # ============ 5. 模块详情 drawer ============
        print("[5/5] Module detail drawer")
        try:
            page.goto(f"{FRONTEND}/opc/community/market", wait_until="networkidle", timeout=30000)
            time.sleep(4)
            page.locator('.module-card').first.click(timeout=10000)
            time.sleep(2)
        except Exception as e:
            print(f"  [warn] drawer open failed: {e}")
        shoot(page, "04-market-detail-drawer.png", full_page=False)

        browser.close()
    print(f"\n[OK] 截图完成,保存于: {OUTPUT_DIR}")


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"[ERR] 截图失败: {e}")
        sys.exit(1)