#!/usr/bin/env bash
# ============================================================
# 验证 prod profile 在没有 Nacos 时 fail-fast（启动失败、非 0 退出码）
# 用法：./verify-fail-fast.sh
# 前提：opc-ai-core 已经 mvn package 完成（target/*.jar 存在）
# ============================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MODULE="opc-ai-core"
JAR="${ROOT_DIR}/${MODULE}/target/${MODULE}.jar"

if [[ ! -f "${JAR}" ]]; then
  echo "[FAIL] 缺少 ${JAR}，请先 mvn package -pl ${MODULE} -am -DskipTests" >&2
  exit 1
fi

# 故意配错 Nacos 地址 + 启用 prod profile
export SPRING_PROFILES_ACTIVE=prod
export NACOS_NAMESPACE=opc-prod-bogus-namespace
export JASYPT_PASSWORD=test-only-strong-password

# 找一个本地空闲端口模拟"不存在的 Nacos"
unused_port=$(python -c "import socket;s=socket.socket();s.bind(('',0));print(s.getsockname()[1]);s.close()" 2>/dev/null \
  || (n=0; while :; do n=$((RANDOM % 50000 + 10000)); nc -z 127.0.0.1 $n 2>/dev/null || break; done; echo $n))

export SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR="127.0.0.1:${unused_port}"
export SPRING_CLOUD_NACOS_CONFIG_SERVER_ADDR="127.0.0.1:${unused_port}"

echo "[INFO] 用例：Nacos 不可达 + profile=prod + 无可用 Nacos"
echo "[INFO] SPRING_CLOUD_NACOS_CONFIG_SERVER_ADDR=${SPRING_CLOUD_NACOS_CONFIG_SERVER_ADDR}"
echo "[INFO] 预期：进程在 60 秒内退出，exit code ≠ 0"

# 启动并限时 90 秒（给 OpcNacosStartupChecker 三次重试的时间）
set +e
timeout 90 java -jar "${JAR}" 2>&1 | tail -100
exit_code=${PIPESTATUS[0]}
set -e

echo "--------------------------------------------------"
echo "[INFO] exit_code=${exit_code}"
if [[ "${exit_code}" -ne 0 ]]; then
  echo "[OK]  fail-fast 生效：进程退出码 = ${exit_code}"
  exit 0
else
  echo "[FAIL] fail-fast 未生效：进程意外退出码 0"
  exit 1
fi