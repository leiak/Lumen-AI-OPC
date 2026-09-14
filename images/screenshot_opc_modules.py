"""
OPC 全模块截图脚本 (W74)
- Playwright headless Chromium
- 截图所有 11 个 OPC 服务的主页面 (~30 张)
- 保存到 images/opc-community/<module>/ 下
- Bypass 验证码: urllib POST /login → Admin-Token cookie
"""

import os
import sys
import json
import time
import urllib.request
from pathlib import Path
from playwright.sync_api import sync_playwright

OUTPUT_ROOT = Path(r"D:\work-ai\0401-lumen-opc\images\opc-community")
OUTPUT_ROOT.mkdir(parents=True, exist_ok=True)

FRONTEND = "http://localhost:8079"
BACKEND_GATEWAY = "http://localhost:8080"

USERNAME = "admin"
PASSWORD = "admin123"


def shoot(page, full_path: Path, full_page: bool = True):
    full_path.parent.mkdir(parents=True, exist_ok=True)
    page.screenshot(path=str(full_path), full_page=full_page)
    size_kb = full_path.stat().st_size // 1024
    rel = full_path.relative_to(OUTPUT_ROOT)
    print(f"  [OK] {rel} ({size_kb} KB)")


def login_and_inject_token(ctx):
    """Bypass captcha: hit /login API directly, inject Admin-Token cookie."""
    print("[*] Bypass captcha via /login API")
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
    page = ctx.new_page()
    page.goto(f"{FRONTEND}/login")
    page.evaluate(
        """([token]) => {
            const expires = new Date(Date.now() + 8*3600*1000).toUTCString();
            document.cookie = `Admin-Token=${token}; expires=${expires}; path=/`;
            localStorage.setItem('Admin-Token', token);
            localStorage.setItem('token', token);
        }""",
        [token],
    )
    page.close()
    print(f"  -- token injected (Admin-Token len={len(token)})")
    return token


# 模块 → (子目录, [(路径, 文件名)])
MODULES = {
    "01-ai-core": ("ai-core", [
        ("/opc/llm/chat", "01-chat.png"),
    ]),
    "02-user-center": ("user-center", [
        ("/opc/user/profile", "01-profile.png"),
        ("/opc/user/invitations", "02-invitations.png"),
    ]),
    "03-agent-hub": ("agent-hub", [
        ("/opc/agent/market", "01-market.png"),
        ("/opc/agent/instances", "02-instances.png"),
    ]),
    "04-billing": ("billing", [
        ("/opc/billing/wallet", "01-wallet.png"),
    ]),
    "05-finance": ("finance", [
        ("/opc/finance/vouchers", "01-vouchers.png"),
        ("/opc/finance/flows", "02-flows.png"),
        ("/opc/finance/tax-reports", "03-tax-reports.png"),
    ]),
    "06-notification": ("notification", [
        ("/opc/inbox", "01-inbox.png"),
    ]),
    "07-crm": ("crm", [
        ("/opc/crm/customers", "01-customers.png"),
        ("/opc/crm/opportunities", "02-opportunities.png"),
        ("/opc/crm/follow-ups", "03-follow-ups.png"),
    ]),
    "08-community": ("community", [
        ("/opc/community/market", "01-market.png"),
    ]),
    "09-hr": ("hr", [
        ("/opc/hr/dashboard", "01-dashboard.png"),
        ("/opc/hr/job", "02-job-list.png"),
        ("/opc/hr/candidate", "03-candidate-list.png"),
    ]),
    "10-erp": ("erp", [
        ("/opc/erp/product", "01-product.png"),
        ("/opc/erp/purchase", "02-purchase.png"),
        ("/opc/erp/sale", "03-sale.png"),
        ("/opc/erp/return", "04-return.png"),
        ("/opc/erp/inventory", "05-inventory.png"),
    ]),
    "11-content": ("content", [
        ("/opc/content/dashboard", "01-dashboard.png"),
        ("/opc/content/script", "02-script-list.png"),
        ("/opc/content/platform-account", "03-platform-account.png"),
        ("/opc/content/publish", "04-publish.png"),
    ]),
}


def navigate_and_shoot(page, path: str, out_file: Path, full_page: bool = True):
    """Navigate to a Vue page and screenshot, with sanity check."""
    url = f"{FRONTEND}{path}"
    try:
        page.goto(url, wait_until="networkidle", timeout=30000)
    except Exception as e:
        print(f"  [warn] goto {path} slow: {e}")
    time.sleep(2.5)  # wait for charts/dialogs/animations
    actual = page.url
    if actual.rstrip("/") != url.rstrip("/") and not actual.endswith(path):
        print(f"  [warn] URL mismatch: expected {path} got {actual}")
    shoot(page, out_file, full_page=full_page)


def main():
    total = sum(len(pages) for _, pages in MODULES.values())
    print(f"[*] 共 {len(MODULES)} 模块 / {total} 页面 → {OUTPUT_ROOT}")
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
        page.set_default_timeout(20000)

        # ============ 0. 登录页 (顶部首图) ============
        print("\n[0] Login page")
        page.goto(f"{FRONTEND}/login")
        page.wait_for_load_state("networkidle", timeout=20000)
        time.sleep(1)
        shoot(page, OUTPUT_ROOT / "00-login.png", full_page=False)

        # ============ 1. 注入 token ============
        try:
            login_and_inject_token(ctx)
        except Exception as e:
            print(f"  [FATAL] token injection failed: {e}")
            sys.exit(1)

        # ============ 2. OPC 首页 ============
        print("\n[1] OPC home page")
        navigate_and_shoot(page, "/opc/index", OUTPUT_ROOT / "01-opc-home.png")

        # ============ 3. 各模块 ============
        for mod_label, (subdir, pages) in MODULES.items():
            print(f"\n[{mod_label}] {subdir} - {len(pages)} pages")
            for path, fname in pages:
                print(f"  → {path}")
                navigate_and_shoot(page, path, OUTPUT_ROOT / subdir / fname)

        browser.close()
    print(f"\n[OK] 全部截图完成 → {OUTPUT_ROOT}")


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"[ERR] {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)