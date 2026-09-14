"""
清理 Nacos yml 文件 v2:
- 删掉 spring.redis.* 旧 schema 块(已被 spring.data.redis.* 取代)
- 兼容不同缩进 (2 / 4 / 6 spaces)
"""
from pathlib import Path

nacos_dir = Path(r"D:\work-ai\0401-lumen-opc\springboot3\deploy\nacos")

files = [
    "application-dev.yml",
    "opc-ai-core-dev.yml", "opc-user-center-dev.yml",
    "opc-agent-hub-dev.yml", "opc-billing-dev.yml",
    "opc-crm-dev.yml", "opc-community-dev.yml",
    "opc-content-dev.yml", "opc-erp-dev.yml",
    "opc-finance-dev.yml", "opc-hr-dev.yml",
    "opc-insight-dev.yml", "opc-notification-dev.yml",
]

for fname in files:
    f = nacos_dir / fname
    if not f.exists():
        continue
    text = f.read_text(encoding="utf-8")
    if "  redis:" not in text and "    redis:" not in text:
        continue
    lines = text.split("\n")
    out = []
    skip = False
    skip_min_indent = 99
    for line in lines:
        if not skip:
            stripped = line.lstrip()
            # Check if this is `redis:` and indent is 2, 4, or 6 spaces
            if stripped == "redis:" and 2 <= (len(line) - len(stripped)) <= 8:
                # Check if previous non-blank line is "spring:" at indent-2
                indent = len(line) - len(stripped)
                skip_min_indent = indent
                skip = True
                continue
            out.append(line)
        else:
            stripped = line.lstrip()
            if not line.strip():
                out.append(line)
                continue
            indent = len(line) - len(stripped)
            if indent < skip_min_indent:
                skip = False
                out.append(line)
    new_text = "\n".join(out)
    new_text = new_text.rstrip() + "\n"
    f.write_text(new_text, encoding="utf-8")
    print(f"{fname}: removed spring.redis block (saved {len(text) - len(new_text)} bytes)")