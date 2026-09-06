#!/usr/bin/env bash
# ============================================================
# OPC Jasypt 敏感字段加密脚本
# 用法：
#   JASYPT_PASSWORD='xxx' ./encrypt-secret.sh 'myPlaintext'
#   JASYPT_PASSWORD='xxx' ./encrypt-secret.sh --decrypt 'ENC(xxx)'
#
# 输出：ENC(...) 包裹的密文，可直接粘到 Nacos YAML。
# 依赖：opc-common 已编译完成（mvn install -pl opc-common -am）
# ============================================================
set -euo pipefail

: "${JASYPT_PASSWORD:?JASYPT_PASSWORD 必须设置（与 prod Nacos 部署时一致）}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MODULE="opc-common"
CLASS="com.ruoyi.opc.common.crypto.JasyptCipherTool"

# ---------- 参数解析 ----------
if [[ "${1:-}" == "--decrypt" ]]; then
  MODE="decrypt"
  VALUE="${2:-}"
else
  MODE="encrypt"
  VALUE="${1:-}"
fi

if [[ -z "${VALUE}" ]]; then
  echo "Usage: JASYPT_PASSWORD=xxx $0 [--decrypt] <value>" >&2
  exit 1
fi

# ---------- 运行 Java ----------
cd "${ROOT_DIR}"

MVN_CMD="mvn -q -pl ${MODULE} -am \
  -Dexec.mainClass=${CLASS} \
  -Dexec.args=\"${MODE} '${JASYPT_PASSWORD}' '${VALUE}'\" \
  -Dexec.cleanupDaemonThreads=false \
  exec:java"

# 优先使用本地编译的 classpath（避免每次启动 Spring 容器）
LOCAL_CP="${ROOT_DIR}/${MODULE}/target/classes"
if [[ -d "${LOCAL_CP}" ]] && [[ -f "${LOCAL_CP}/com/ruoyi/opc/common/crypto/JasyptCipherTool.class" ]]; then
  # 直接执行（快）
  mvn -q -pl "${MODULE}" dependency:build-classpath \
    -Dmdep.outputFile="${LOCAL_CP}/.cp.txt" > /dev/null 2>&1 || true
  if [[ -f "${LOCAL_CP}/.cp.txt" ]]; then
    CP="$(cat "${LOCAL_CP}/.cp.txt"):${LOCAL_CP}"
    java -cp "${CP}" "${CLASS}" "${MODE}" "${JASYPT_PASSWORD}" "${VALUE}"
    exit 0
  fi
fi

# 回退：mvn exec:java
eval "${MVN_CMD}"