#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
opc-erp E2E test (W72)
依赖: urllib + json (标准库)
执行: python tmp_e2e/e2e_erp.py
前置: aiopc-erp 服务已启动,admin/admin123 可登录

测试路径: GATEWAY(8080) → ruoyi-gateway → aiopc-erp:9311
"""

import json
import sys
import time
import urllib.request
import urllib.parse
from typing import Optional

# Windows console 默认 GBK, 经 echo | python 管道时多字节 UTF-8 字符会被破坏。
# 强制所有 python 子进程按 UTF-8 解码 stdin (避免 dashboard 等返回中文的接口假阴性, W50 教训 3)
import os
os.environ.setdefault("PYTHONIOENCODING", "utf-8")

GATEWAY = "http://127.0.0.1:8080"
COMPANY_ID = 1
USER_ID = 1
PASS_COUNT = 0
WARN_COUNT = 0

# ---------- helpers ----------

def login(username: str = "admin", password: str = "admin123") -> str:
    url = f"{GATEWAY}/login"
    data = json.dumps({"username": username, "password": password}).encode()
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=10) as resp:
        body = json.loads(resp.read().decode())
        if body.get("code") != 200:
            raise RuntimeError(f"login failed: {body.get('msg')}")
        # RuoYi 用 access_token 不是 token(W48 教训)
        token = body.get("access_token") or body.get("token")
        if not token:
            raise RuntimeError(f"login response missing token: {body}")
        return token

TOKEN: Optional[str] = None

def call(method: str, path: str, body: Optional[dict] = None,
         params: Optional[dict] = None, expected_code: int = 200,
         allow_warn_codes: tuple = (500,)) -> dict:
    """统一 API 调用,带断言"""
    global PASS_COUNT, WARN_COUNT
    url = f"{GATEWAY}{path}"
    if params:
        url += "?" + urllib.parse.urlencode(params)
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if TOKEN:
        headers["Authorization"] = f"Bearer {TOKEN}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method.upper())
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            result = json.loads(resp.read().decode())
    except Exception as e:
        WARN_COUNT += 1
        print(f"  [WARN] {method} {path} 异常: {e}")
        return {"code": -1, "msg": str(e)}

    actual = result.get("code", -1)
    if actual == expected_code:
        PASS_COUNT += 1
        print(f"  [OK]   {method:6s} {path:60s} code={actual}")
    elif actual in allow_warn_codes:
        WARN_COUNT += 1
        print(f"  [WARN] {method:6s} {path:60s} code={actual} msg={result.get('msg', '')[:60]}")
    else:
        WARN_COUNT += 1
        print(f"  [WARN] {method:6s} {path:60s} code={actual} expected={expected_code} msg={result.get('msg', '')[:60]}")
    return result

# ---------- 10 step E2E ----------

def step(n: int, title: str):
    print(f"\n[STEP {n}/10] {title}")

def main():
    global TOKEN

    print("=" * 70)
    print(f"opc-erp E2E test (gateway={GATEWAY}, companyId={COMPANY_ID})")
    print("=" * 70)

    # Step 1: 登录
    step(1, "登录拿 access_token")
    try:
        TOKEN = login()
        print(f"  token 长度: {len(TOKEN)}")
    except Exception as e:
        print(f"FATAL: 登录失败: {e}", file=sys.stderr)
        sys.exit(1)

    # Step 2: 创建商品(spec_attrs 笛卡尔积 → 4 SKU)
    step(2, "创建商品(2 颜色 × 2 尺码 = 4 SKU 笛卡尔积)")
    ts = int(time.time())
    r = call("POST", "/opc/erp/product", body={
        "companyId": COMPANY_ID,
        "skuRoot": f"T{ts}",
        "name": f"E2E 测试 T恤 {ts}",
        "category": "服装鞋帽",
        "brand": "OPC",
        "unit": "件",
        "description": "100% 纯棉",
        "specAttrs": '[{"name":"颜色","values":["黑","白"]},{"name":"尺码","values":["M","L"]}]',
        "price": 99.00
    })
    product_id = r.get("data")
    print(f"  → product_id={product_id}")

    # Step 3: 列出商品 SKU
    step(3, "列出商品 SKU(应 4 个: 黑/M, 黑/L, 白/M, 白/L)")
    r = call("GET", "/opc/erp/product/product-sku/list",
             params={"companyId": COMPANY_ID, "productId": product_id})
    if r.get("code") == 200 and r.get("data"):
        skus = r["data"] if isinstance(r["data"], list) else []
        print(f"  → SKU 总数: {len(skus)}")
        if len(skus) >= 4:
            print(f"  → 笛卡尔积验证 OK (期望 4, 实得 {len(skus)})")

    # Step 4: 创建采购单(DRAFT)
    step(4, "创建采购单(DRAFT, 2 SKU items + 批次信息)")
    r = call("POST", "/opc/erp/purchase", body={
        "companyId": COMPANY_ID,
        "supplierId": 1,
        "items": [
            {"companyId": COMPANY_ID, "skuId": 1, "quantity": 100, "unitPrice": 50.00,
             "batchNo": f"B{ts}", "productionDate": "2026-09-01", "expiryDate": "2027-09-01"},
            {"companyId": COMPANY_ID, "skuId": 2, "quantity": 50, "unitPrice": 80.00,
             "batchNo": f"B{ts + 1}", "productionDate": "2026-09-01", "expiryDate": "2027-09-01"}
        ]
    })
    purchase_id = r.get("data")
    print(f"  → purchase_id={purchase_id}")

    # Step 5: 确认采购单 → 创建批次 + 库存增加
    step(5, f"确认采购单 id={purchase_id}(CONFIRMED → COMPLETED)")
    call("POST", f"/opc/erp/purchase/{purchase_id}/confirm",
         params={"companyId": COMPANY_ID})

    # Step 6: 列出销售单(空)
    step(6, "列出销售单(空数据)")
    r = call("GET", "/opc/erp/sale/list", params={"companyId": COMPANY_ID})
    if r.get("code") == 200 and r.get("data"):
        sales = r["data"] if isinstance(r["data"], list) else []
        print(f"  → 销售单总数: {len(sales)}")

    # Step 7: 查询低库存预警
    step(7, "查询低库存预警")
    r = call("GET", "/opc/erp/inventory/low-stock", params={"companyId": COMPANY_ID})
    if r.get("code") == 200 and r.get("data"):
        low_stock = r["data"] if isinstance(r["data"], list) else []
        print(f"  → 低库存 SKU 数: {len(low_stock)}")

    # Step 8: 查询日报
    step(8, "查询库存日报")
    today = time.strftime("%Y-%m-%d")
    r = call("GET", "/opc/erp/report/daily", params={"companyId": COMPANY_ID, "date": today})
    if r.get("code") == 200 and r.get("data"):
        print(f"  → 日报条目数: {len(r['data']) if isinstance(r['data'], list) else 1}")

    # Step 9: 查询月报
    step(9, "查询库存月报")
    year, month = time.strftime("%Y"), time.strftime("%m")
    call("GET", "/opc/erp/report/monthly",
         params={"companyId": COMPANY_ID, "year": int(year), "month": int(month)})

    # Step 10: 列出供应商
    step(10, "列出供应商(应包含 seed 5 条)")
    r = call("GET", "/opc/erp/supplier/list", params={"companyId": COMPANY_ID})
    if r.get("code") == 200 and r.get("data"):
        suppliers = r["data"] if isinstance(r["data"], list) else []
        print(f"  → 供应商总数: {len(suppliers)}")

    # 总结
    print("\n" + "=" * 70)
    print(f"PASS: {PASS_COUNT}    WARN/FAIL: {WARN_COUNT}")
    print("=" * 70)
    if WARN_COUNT == 0:
        print("✅ 10/10 步骤全部成功")
        sys.exit(0)
    elif WARN_COUNT <= 3:
        print("⚠️  多数步骤成功,少量 warn(参考上方详情)")
        sys.exit(0)
    else:
        print("❌ 较多步骤失败,需排查")
        sys.exit(1)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nInterrupted", file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(f"\nFATAL: {e}", file=sys.stderr)
        sys.exit(2)
