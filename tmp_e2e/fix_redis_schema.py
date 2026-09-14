"""
为所有 OPC 模块的 jar application.yml 添加 spring.data.redis.*
Spring Boot 3.5 Lettuce 只读新 schema,旧 spring.redis.* 被忽略
"""
from pathlib import Path

modules = [
    "opc-erp", "opc-community", "opc-content",
    "opc-ai-core", "opc-user-center", "opc-agent-hub",
    "opc-billing", "opc-crm", "opc-finance",
    "opc-hr", "opc-insight", "opc-notification",
]

NEW_BLOCK = """
  # Spring Boot 3.5: Lettuce 只读 spring.data.redis.* (旧 spring.redis.* 已废弃)
  data:
    redis:
      host: ${REDIS_HOST:aiopc-redis}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:Opc@2026!}
      timeout: 10s
"""

base = Path(r"D:\work-ai\0401-lumen-opc\springboot3\ruoyi-modules")

for mod in modules:
    yml = base / mod / "src" / "main" / "resources" / "application.yml"
    if not yml.exists():
        print(f"{mod}: file not found")
        continue
    text = yml.read_text(encoding="utf-8")
    if "spring.data.redis" in text:
        print(f"{mod}: already has spring.data.redis")
        continue
    # 在 spring: 块的最后 (下一个顶级 key 之前) 插入
    lines = text.split("\n")
    out = []
    inserted = False
    in_spring_block = False
    spring_indent = 0
    for i, line in enumerate(lines):
        if line.startswith("spring:"):
            in_spring_block = True
            spring_indent = 0
            out.append(line)
            continue
        if in_spring_block and not inserted:
            # Find first line that ends spring: block (column 0 non-comment)
            stripped = line.lstrip()
            if stripped and not line.startswith(" ") and not line.startswith("#"):
                # End of spring block - insert before
                if not NEW_BLOCK.rstrip().endswith(line):
                    out.append(NEW_BLOCK.rstrip())
                    inserted = True
                    in_spring_block = False
            elif not stripped and i > 0:
                # blank line - might be end of block, peek ahead
                if i + 1 < len(lines):
                    nxt = lines[i + 1]
                    if nxt and not nxt.startswith(" ") and not nxt.startswith("#") and not nxt.startswith("spring:"):
                        out.append(NEW_BLOCK.rstrip())
                        inserted = True
                        in_spring_block = False
        out.append(line)
    if not inserted:
        # Append at end of file
        out.append(NEW_BLOCK)
    new_text = "\n".join(out)
    yml.write_text(new_text, encoding="utf-8")
    print(f"{mod}: updated ({len(new_text) - len(text)} bytes added)")