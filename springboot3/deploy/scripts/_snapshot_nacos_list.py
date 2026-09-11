#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Helper: snapshot Nacos services (registry) + configs (storage).

Nacos 2.3.x has no public list-configs API, so we list:
  - registered services via /v1/ns/catalog/services
  - known config IDs by GETting each from /v1/cs/configs
"""
import io
import json
import sys
import urllib.error
import urllib.request

if len(sys.argv) != 4:
    print("Usage: _snapshot_nacos_list.py <addr> <ns> <ts>", file=sys.stderr)
    sys.exit(2)

addr = sys.argv[1]
ns = sys.argv[2]
ts = sys.argv[3]
base = f"http://{addr}"

# Known config IDs in this project (mirror import-dev.sh + aiopc-* service configs)
KNOWN_CONFIG_IDS = [
    "application-dev.yml",
    "application-prod.yml",
    "opc-notification-dev.yml",
    "opc-crm-dev.yml",
    "opc-ai-core-dev.yml",
    "opc-user-center-dev.yml",
    "opc-agent-hub-dev.yml",
    "opc-billing-dev.yml",
    "opc-finance-dev.yml",
    "opc-insight-dev.yml",
    "ruoyi-gateway-dev.yml",
    "ruoyi-auth-dev.yml",
    "ruoyi-system-dev.yml",
]


def http_get(path, timeout=8):
    return urllib.request.urlopen(base + path, timeout=timeout).read().decode("utf-8")


lines = [
    f"# Nacos 快照 - {ts}",
    f"# addr={addr} ns={ns}",
    "",
]

# --- Services (registry) ---
try:
    raw = http_get(f"/nacos/v1/ns/catalog/services?namespaceId={ns}&pageNo=1&pageSize=100")
    data = json.loads(raw)
    svcs = data.get("serviceList", [])
    lines += [
        f"## Registered services ({data.get('count', 0)})",
        "",
        f"{'name':<25} | {'group':<15} | {'healthy/total':<15}",
        "-" * 60,
    ]
    for s in sorted(svcs, key=lambda x: x.get("name", "")):
        healthy = s.get("healthyInstanceCount", 0)
        total = s.get("ipCount", 0)
        lines.append(f"{s.get('name',''):<25} | {s.get('groupName',''):<15} | {healthy}/{total}")
    lines.append("")
except (urllib.error.URLError, json.JSONDecodeError, KeyError) as e:
    lines.append(f"## Registered services: [ERROR] {e}")

# --- Configs (storage) ---
lines += [
    f"## Configs ({len(KNOWN_CONFIG_IDS)} known DataIDs)",
    "",
    f"{'dataId':<35} | {'namespace':<12} | size(B) | first 60 chars",
    "-" * 110,
]
for did in KNOWN_CONFIG_IDS:
    try:
        content = http_get(f"/nacos/v1/cs/configs?dataId={did}&group=DEFAULT_GROUP&namespaceId={ns}")
        first = content.splitlines()[0] if content else ""
        if first.startswith("#") or first.startswith("//"):
            first = first.lstrip("#/ ").strip()
        first = first[:60]
        lines.append(f"{did:<35} | {ns:<12} | {len(content):>7} | {first}")
    except urllib.error.HTTPError as e:
        lines.append(f"{did:<35} | {ns:<12} | {'HTTP '+str(e.code):>7} | {e.reason}")
    except urllib.error.URLError as e:
        lines.append(f"{did:<35} | {ns:<12} | {'ERR':>7} | {e.reason}")

print("\n".join(lines))
sys.stdout.flush()
# Force UTF-8 buffer for downstream redirection on Windows (avoid GBK mojibake)
if sys.platform.startswith("win"):
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")