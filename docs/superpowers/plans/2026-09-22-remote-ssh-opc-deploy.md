# W79 Remote SSH OPC Deploy — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 OPC 全栈 (22 容器: 6 基建 + 3 平台 + 13 业务) 通过一键 `bootstrap-remote.sh` 部署到 Rocky Linux 8.10 云 VM,从用户本机 `backup-mysql-local.sh` 导出 MySQL dump 迁移过去。

**Architecture:** 双脚本分工 — 本机 `backup-mysql-local.sh` 导出 SQL.gz + Nacos 配置 tgz → scp 到 VM → VM 上 `bootstrap-remote.sh` 跑 5 阶段 idempotent 引导 (装 Docker / clone / 起基建 / 迁移数据 + 推 Nacos + 起业务 / 健康检查)。脚本遵循 `set -euo pipefail` + 显式 exit code (1/2/3/4),失败可 `--from <n>` 续跑。

**Tech Stack:** Bash 5 + GNU coreutils + Docker Compose v2.20+ + Docker 24+ on Rocky Linux 8.10 + mysqldump/mysql + curl/jq

---

## File Structure

| 文件 | 责任 | 大小 |
|---|---|---|
| `springboot3/deploy/scripts/backup-mysql-local.sh` (NEW) | 本机导出 SQL + Nacos → `aiopc-migrate-*.tar.gz` | ~60 行 |
| `springboot3/deploy/scripts/bootstrap-remote.sh` (NEW) | VM 上 5 阶段一键引导 | ~280 行 |
| `springboot3/deploy/RECOVERY.md` (MODIFY, +60 行) | 加 §15 Remote SSH Bootstrap 节 | +60 行 |
| `springboot3/deploy/.env.example` (MODIFY, +15 行) | 加 Rocky 8 / placeholder 注释 | +15 行 |
| `docs/verification/late/W79-REMOTE-SSH-DEPLOY-VERIFICATION.md` (NEW) | W79 验证报告模板 | ~80 行 |

**任务排序**: backup-mysql-local → bootstrap-remote 骨架 → 5 阶段实现 → RECOVERY/.env 更新 → VERIFICATION 模板。

**测试策略**:
- Bash 脚本用 `bash -n script.sh` 语法校验 + `shellcheck script.sh` 静态分析 (若可用)
- 集成测试靠实跑 (本机 dry-run + VM 真跑)
- 符合仓库已有 `deploy.sh` / `health-check.sh` 无单测的约定 (见 [[aiopc-deployment-tooling]])

---

## Task 1: Write `backup-mysql-local.sh` (本机导出脚本)

**Files:**
- Create: `springboot3/deploy/scripts/backup-mysql-local.sh`

- [ ] **Step 1: 写脚本骨架 (前置 + 变量)**

```bash
#!/usr/bin/env bash
# ============================================================
# AIOPC 本机迁移包导出 (W79)
#
# 在用户本机 (Windows git-bash / Mac / Linux) 跑,打包 MySQL dump
# + Nacos 配置 → aiopc-migrate-<date>.tar.gz,供 scp 到远程 VM。
#
# 用法:
#   bash backup-mysql-local.sh                  # 默认输出到 /tmp/aiopc-migrate-YYYYMMDD-HHMMSS.tar.gz
#   bash backup-mysql-local.sh /path/to/out.tar.gz
#
# 依赖:
#   - 本机 Docker Desktop / docker engine 在跑
#   - aiopc-mysql 容器存在且 healthy
#   - nacos/*.yml 在 springboot3/deploy/nacos/ 下
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
NACOS_DIR="${DEPLOY_DIR}/nacos"
CONTAINER="aiopc-mysql"
ROOT_PWD="Opc@2026!"
DB="ry-vue-opc"

OUT_FILE="${1:-/tmp/aiopc-migrate-$(date +%Y%m%d-%H%M%S).tar.gz}"
STAGE_DIR="$(mktemp -d)"
trap 'rm -rf "${STAGE_DIR}"' EXIT
```

- [ ] **Step 2: 加前置检查函数**

```bash
log()  { echo "[INFO]  $*"; }
warn() { echo "[WARN]  $*" >&2; }
fail() { echo "[FAIL]  $*" >&2; exit 1; }

require_docker() {
  if ! command -v docker >/dev/null 2>&1; then
    fail "docker 未安装,请先装 Docker Desktop 24+"
  fi
  if ! docker info >/dev/null 2>&1; then
    fail "docker daemon 没在跑,启动 Docker Desktop 后重试"
  fi
}

require_mysql_container() {
  if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER}$"; then
    fail "容器 ${CONTAINER} 不存在或未运行。请先在本地跑 deploy.sh up"
  fi
}

require_nacos_dir() {
  if [[ ! -d "${NACOS_DIR}" ]] || [[ -z "$(ls -A "${NACOS_DIR}"/*.yml 2>/dev/null)" ]]; then
    fail "Nacos 配置目录为空: ${NACOS_DIR}"
  fi
}
```

- [ ] **Step 3: 加导出函数**

```bash
export_mysql_dump() {
  log "导出 MySQL dump from ${CONTAINER}.${DB} ..."
  docker exec "${CONTAINER}" mysqldump -uroot -p"${ROOT_PWD}" \
    --default-character-set=utf8mb4 --routines --triggers --events \
    --single-transaction \
    --databases "${DB}" \
    2>/dev/null | gzip > "${STAGE_DIR}/mysql-dump.sql.gz"
  local size
  size=$(du -h "${STAGE_DIR}/mysql-dump.sql.gz" | cut -f1)
  log "  ✓ mysql-dump.sql.gz (${size})"
}

export_nacos_configs() {
  log "打包 Nacos dev/prod 配置 from ${NACOS_DIR} ..."
  cd "${NACOS_DIR}"
  tar -czf "${STAGE_DIR}/nacos-configs.tgz" *.yml
  local size
  size=$(du -h "${STAGE_DIR}/nacos-configs.tgz" | cut -f1)
  log "  ✓ nacos-configs.tgz (${size})"
  cd - >/dev/null
}

build_migrate_tar() {
  log "打包最终迁移包 -> ${OUT_FILE}"
  tar -czf "${OUT_FILE}" -C "${STAGE_DIR}" .
  sha256sum "${OUT_FILE}" > "${OUT_FILE}.sha256"
  local size
  size=$(du -h "${OUT_FILE}" | cut -f1)
  log "  ✓ ${OUT_FILE} (${size})"
  log "  ✓ checksum: ${OUT_FILE}.sha256"
}
```

- [ ] **Step 4: 加 main 函数**

```bash
main() {
  require_docker
  require_mysql_container
  require_nacos_dir

  export_mysql_dump
  export_nacos_configs
  build_migrate_tar

  cat <<NEXT

============================================================
✓ 迁移包已生成:

  $(realpath "${OUT_FILE}")

下一步:
  scp ${OUT_FILE} root@<VM_IP>:/tmp/aiopc-staging/
  (在 VM 上) bash /opt/aiopc/springboot3/deploy/scripts/bootstrap-remote.sh
============================================================
NEXT
}

main "$@"
```

- [ ] **Step 5: 加执行权限 + bash 语法校验**

Run:
```bash
chmod +x D:/work-ai/0401-lumen-opc/springboot3/deploy/scripts/backup-mysql-local.sh
bash -n D:/work-ai/0401-lumen-opc/springboot3/deploy/scripts/backup-mysql-local.sh && echo "SYNTAX OK"
```

Expected: `SYNTAX OK`

- [ ] **Step 6: Commit**

```bash
git add springboot3/deploy/scripts/backup-mysql-local.sh
git commit -m "feat(deploy): W79 backup-mysql-local.sh — 本机导出 MySQL + Nacos → aiopc-migrate-*.tar.gz"
```

---

## Task 2: Write `bootstrap-remote.sh` skeleton (arg 解析 + 帮助)

**Files:**
- Create: `springboot3/deploy/scripts/bootstrap-remote.sh`

- [ ] **Step 1: 写脚本头 + 变量**

```bash
#!/usr/bin/env bash
# ============================================================
# AIOPC 远程 VM 一键引导 (W79)
#
# 在 Rocky Linux 8.10 云 VM 上跑,把整套 OPC 全栈从零拉起并
# 从用户 scp 过来的 aiopc-migrate-*.tar.gz 恢复数据。
#
# 用法:
#   bash bootstrap-remote.sh                          # 默认 Stage 0 → 5 全跑
#   bash bootstrap-remote.sh --stage <0..5>           # 单跑一个阶段
#   bash bootstrap-remote.sh --from <n>               # 从 Stage n 开始续跑
#   bash bootstrap-remote.sh --status                 # 当前进度
#   bash bootstrap-remote.sh --abort                  # 停所有 aiopc-* 容器
#   bash bootstrap-remote.sh --help
#
# Exit codes:
#   0 = 全成功
#   1 = 前置依赖缺失
#   2 = 数据恢复失败 (已自动回滚)
#   3 = 镜像 build 失败
#   4 = 健康检查 < 30/50 PASS
#
# 阶段说明:
#   0 = Pre-flight (校验 Rocky 8.10 / 磁盘 / .env / migrate 包)
#   1 = Install Docker (docker-ce repo + dnf install)
#   2 = Install deps + clone repo (git/python3/rsync + 关 SELinux/firewalld + git clone)
#   3 = 起基建 (nacos1/mysql/redis/rabbitmq/minio/qdrant)
#   4 = 数据迁移 + 推 Nacos + 起业务 (restore + import-dev + compose up)
#   5 = Verification (health-check.sh)
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DATA_ROOT="${DATA_ROOT:-/opt/aiopc-data}"
STAGING="${STAGING:-/tmp/aiopc-staging}"
LOG_FILE="${LOG_FILE:-${DATA_ROOT}/bootstrap-$(date +%Y%m%d-%H%M%S).log}"

mkdir -p "${DATA_ROOT}" "${STAGING}"

# 从 docker-compose.yml 提取 MYSQL_ROOT_PASSWORD
COMPOSE_MYSQL_PWD="$(grep -E '^\s*MYSQL_ROOT_PASSWORD:' "${PROJECT_ROOT}/docker-compose.yml" | head -1 | sed -E 's/.*:\s*([^\s]+).*/\1/')"
if [[ -z "${COMPOSE_MYSQL_PWD}" ]]; then
  echo "[FAIL] 解析 docker-compose.yml 失败:找不到 MYSQL_ROOT_PASSWORD" >&2
  exit 1
fi
```

- [ ] **Step 2: 加 log + fail + require 函数**

```bash
log()  { echo "[INFO]  $*" | tee -a "${LOG_FILE}"; }
warn() { echo "[WARN]  $*" | tee -a "${LOG_FILE}" >&2; }
fail() { echo "[FAIL]  $*" | tee -a "${LOG_FILE}" >&2; exit 1; }

require_root() {
  if [[ $EUID -ne 0 ]]; then
    fail "必须用 root 跑 (current EUID=$EUID)"
  fi
}

require_rocky_8() {
  if [[ ! -f /etc/os-release ]]; then
    fail "/etc/os-release 不存在"
  fi
  if ! grep -q "Rocky Linux 8.10" /etc/os-release; then
    fail "OS 不是 Rocky Linux 8.10。实际:"
    cat /etc/os-release >&2
  fi
}

require_disk() {
  local need_mb=$((5 * 1024))   # 5 GB
  local avail_mb
  avail_mb=$(df -m "${DATA_ROOT}" | awk 'NR==2 {print $4}')
  if (( avail_mb < need_mb )); then
    fail "磁盘不足:${DATA_ROOT} 仅 ${avail_mb} MB 可用,需要 >= ${need_mb} MB"
  fi
  log "磁盘 OK: ${avail_mb} MB 可用"
}
```

- [ ] **Step 3: 加 arg 解析 + 帮助**

```bash
CMD="up"
FROM_STAGE=""
SINGLE_STAGE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --stage)  SINGLE_STAGE="$2"; shift 2 ;;
    --from)   FROM_STAGE="$2";   shift 2 ;;
    --status) CMD="status";      shift   ;;
    --abort)  CMD="abort";       shift   ;;
    --help|-h) CMD="help";       shift   ;;
    *) fail "未知参数:$1 (跑 --help 看用法)";;
  esac
done

cmd_help() {
  sed -n '2,40p' "${BASH_SOURCE[0]}" | sed 's/^# \?//'
  exit 0
}

if [[ "${CMD}" == "help" ]]; then cmd_help; fi
```

- [ ] **Step 4: 加 stage 调度骨架**

```bash
STAGES_OK_FILE="${DATA_ROOT}/.bootstrap-stages-ok"
declare -A STAGE_NAMES=(
  [0]="Pre-flight"
  [1]="Install Docker"
  [2]="Install deps + clone"
  [3]="起基建"
  [4]="数据迁移 + 推 Nacos + 起业务"
  [5]="Verification"
)

# 标记 stage 完成
mark_stage_done() {
  local n="$1"
  mkdir -p "${DATA_ROOT}"
  touch "${STAGES_OK_FILE}.${n}"
  log "✓ Stage ${n} (${STAGE_NAMES[$n]:-?}) 完成"
}

is_stage_done() {
  [[ -f "${STAGES_OK_FILE}.$1" ]]
}

# 决定本次要跑哪些 stage
START_STAGE=0
END_STAGE=5
if [[ -n "${FROM_STAGE}" ]]; then
  START_STAGE="${FROM_STAGE}"
elif [[ -n "${SINGLE_STAGE}" ]]; then
  START_STAGE="${SINGLE_STAGE}"
  END_STAGE="${SINGLE_STAGE}"
fi

cmd_status() {
  log "当前 bootstrap 进度:"
  for n in 0 1 2 3 4 5; do
    if is_stage_done "${n}"; then
      echo "  [✓] Stage ${n}: ${STAGE_NAMES[$n]}"
    else
      echo "  [ ] Stage ${n}: ${STAGE_NAMES[$n]}"
    fi
  done
  echo ""
  echo "数据卷:"
  du -sh "${DATA_ROOT}"/* 2>/dev/null || echo "  (空)"
  exit 0
}

cmd_abort() {
  log "停所有 aiopc-* 容器 (保留 /opt/aiopc-data)..."
  cd "${PROJECT_ROOT}"
  docker compose stop 2>/dev/null || true
  log "✓ abort 完成。重启跑: bash $0 --from 3"
  exit 0
}

if [[ "${CMD}" == "status" ]]; then cmd_status; fi
if [[ "${CMD}" == "abort"  ]]; then cmd_abort;  fi

# up / from / stage 共用入口
log "============================================================"
log "AIOPC 远程引导开始"
log "  起点:Stage ${START_STAGE} (${STAGE_NAMES[${START_STAGE}]:-?})"
log "  终点:Stage ${END_STAGE}"
log "  日志:${LOG_FILE}"
log "============================================================"
```

- [ ] **Step 5: 加 main 调度循环 + 阶段函数占位**

```bash
run_stage() {
  local n="$1"
  local fn="stage_${n}"
  if is_stage_done "${n}" && [[ -z "${SINGLE_STAGE}" ]]; then
    log "Stage ${n} 已完成,跳过 (要重跑:rm ${STAGES_OK_FILE}.${n})"
    return 0
  fi
  log ">>> Stage ${n}: ${STAGE_NAMES[$n]:-?}"
  "${fn}"
  mark_stage_done "${n}"
}

stage_0() { : "placeholder"; }
stage_1() { : "placeholder"; }
stage_2() { : "placeholder"; }
stage_3() { : "placeholder"; }
stage_4() { : "placeholder"; }
stage_5() { : "placeholder"; }

main() {
  require_root
  for n in $(seq "${START_STAGE}" "${END_STAGE}"); do
    run_stage "${n}"
  done
  log "============================================================"
  log "✓ 全栈部署完成。访问入口:"
  log "  前端:    http://127.0.0.1:8079  (ssh -L 8079:127.0.0.1:8079)"
  log "  网关:    http://127.0.0.1:8080  (ssh -L 8080:127.0.0.1:8080)"
  log "  Nacos:   http://127.0.0.1:8848/nacos  (ssh -L 8848:127.0.0.1:8848)"
  log "============================================================"
}

main "$@"
```

- [ ] **Step 6: bash 语法校验**

Run:
```bash
bash -n D:/work-ai/0401-lumen-opc/springboot3/deploy/scripts/bootstrap-remote.sh && echo "SYNTAX OK"
bash D:/work-ai/0401-lumen-opc/springboot3/deploy/scripts/bootstrap-remote.sh --help | head -20
```

Expected:
- `SYNTAX OK`
- `--help` 输出脚本头部注释

- [ ] **Step 7: Commit**

```bash
git add springboot3/deploy/scripts/bootstrap-remote.sh
git commit -m "feat(deploy): W79 bootstrap-remote.sh skeleton — arg 解析 + 5 阶段调度骨架"
```

---

## Task 3: Implement Stage 0 (Pre-flight)

**Files:**
- Modify: `springboot3/deploy/scripts/bootstrap-remote.sh`

- [ ] **Step 1: 把 stage_0 替换为真实实现**

替换 `stage_0() { : "placeholder"; }` 为:

```bash
stage_0() {
  log "校验前置条件..."

  require_rocky_8
  require_disk

  # .env 必须存在 (从 clone 后或用户手动上传)
  if [[ ! -f "${PROJECT_ROOT}/.env" ]]; then
    fail ".env 不存在:${PROJECT_ROOT}/.env。请先 cp .env.example .env 并填值"
  fi
  log "  ✓ .env 存在"

  # aiopc-migrate-*.tar.gz 必须 scp 到 staging
  local migrate_tar
  migrate_tar="$(ls -t "${STAGING}"/aiopc-migrate-*.tar.gz 2>/dev/null | head -1 || true)"
  if [[ -z "${migrate_tar}" ]]; then
    fail "未找到 ${STAGING}/aiopc-migrate-*.tar.gz。请先把本机导出的迁移包 scp 过来"
  fi
  log "  ✓ 迁移包:${migrate_tar} ($(du -h "${migrate_tar}" | cut -f1))"

  # 校验 sha256 (如果存在 .sha256 文件)
  if [[ -f "${migrate_tar}.sha256" ]]; then
    if ! sha256sum -c "${migrate_tar}.sha256" >/dev/null 2>&1; then
      fail "迁移包 sha256 校验失败,可能传输损坏"
    fi
    log "  ✓ sha256 校验通过"
  fi

  # 校验 .env 必填项 (placeholder 允许,但不能为空)
  for key in DEEPSEEK_API_KEY MINIMAX_API_KEY; do
    local val
    val="$(grep -E "^${key}=" "${PROJECT_ROOT}/.env" | cut -d= -f2- || true)"
    if [[ -z "${val}" ]]; then
      fail ".env 缺 ${key}"
    fi
  done
  log "  ✓ .env 必填项齐全"

  log "Stage 0 校验通过"
}
```

- [ ] **Step 2: bash 语法校验**

Run:
```bash
bash -n D:/work-ai/0401-lumen-opc/springboot3/deploy/scripts/bootstrap-remote.sh && echo "SYNTAX OK"
```

Expected: `SYNTAX OK`

- [ ] **Step 3: Commit**

```bash
git add springboot3/deploy/scripts/bootstrap-remote.sh
git commit -m "feat(deploy): W79 bootstrap Stage 0 — Rocky 8 / 磁盘 / .env / migrate 包校验"
```

---

## Task 4: Implement Stage 1 (Install Docker)

**Files:**
- Modify: `springboot3/deploy/scripts/bootstrap-remote.sh`

- [ ] **Step 1: 把 stage_1 替换为真实实现**

替换 `stage_1() { : "placeholder"; }` 为:

```bash
stage_1() {
  log "检查 Docker 是否已装..."
  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    log "  ✓ Docker 已装: $(docker --version)"
    return 0
  fi

  log "装 Docker CE (Rocky 8 + docker-ce repo)..."

  # Rocky 8 默认源可能不带 docker-ce,加官方 repo
  if [[ ! -f /etc/yum.repos.d/docker-ce.repo ]]; then
    dnf config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo \
      || fail "加 docker-ce repo 失败 (国内云可换 mirrors.aliyun.com/docker-ce)"
  fi

  dnf install -y docker-ce docker-ce-cli containerd.io \
    docker-buildx-plugin docker-compose-plugin \
    || fail "dnf install docker-ce 失败"

  systemctl enable --now docker
  systemctl is-active --quiet docker || fail "docker daemon 没起来"

  # 校验 compose plugin
  if ! docker compose version >/dev/null 2>&1; then
    fail "docker compose plugin 不可用"
  fi

  log "  ✓ Docker 装好: $(docker --version), $(docker compose version)"
}
```

- [ ] **Step 2: bash 语法校验**

Run:
```bash
bash -n D:/work-ai/0401-lumen-opc/springboot3/deploy/scripts/bootstrap-remote.sh && echo "SYNTAX OK"
```

Expected: `SYNTAX OK`

- [ ] **Step 3: Commit**

```bash
git add springboot3/deploy/scripts/bootstrap-remote.sh
git commit -m "feat(deploy): W79 bootstrap Stage 1 — Install Docker CE on Rocky 8"
```

---

## Task 5: Implement Stage 2 (Install deps + clone)

**Files:**
- Modify: `springboot3/deploy/scripts/bootstrap-remote.sh`

- [ ] **Step 1: 把 stage_2 替换为真实实现**

替换 `stage_2() { : "placeholder"; }` 为:

```bash
stage_2() {
  log "装系统依赖 (git/python3/rsync)..."
  dnf install -y git python3 rsync jq \
    || fail "dnf install 失败"

  # 关 SELinux (容器互访不需要)
  if command -v setenforce >/dev/null 2>&1; then
    setenforce 0 2>/dev/null || true
    if [[ -f /etc/selinux/config ]]; then
      sed -i 's/^SELINUX=enforcing/SELINUX=permissive/' /etc/selinux/config
    fi
    log "  ✓ SELinux 设为 permissive"
  fi

  # 关 firewalld (单 VM 内部通信不需要)
  if systemctl is-active --quiet firewalld; then
    systemctl stop firewalld
    systemctl disable firewalld
    log "  ✓ firewalld 已停"
  fi

  # clone 仓库
  if [[ -d "/opt/aiopc/.git" ]]; then
    log "  ✓ /opt/aiopc 已存在,跳过 clone"
  else
    log "clone 仓库到 /opt/aiopc ..."
    # 注:实际 repo URL 待用户填,先用占位
    local repo_url="${OPC_REPO_URL:-https://github.com/leiak/Lumen-AI-OPC.git}"
    git clone --depth 1 "${repo_url}" /opt/aiopc \
      || fail "git clone 失败 (检查 OPC_REPO_URL 环境变量或网络)"
    log "  ✓ clone 完成"
  fi

  # 校验 clone 结果
  [[ -f "/opt/aiopc/springboot3/deploy/docker-compose.yml" ]] \
    || fail "clone 后找不到 docker-compose.yml,repo 结构异常"

  # 同步 .env 到 clone 后的 deploy/ (用户在 Stage 0 之前手动 cp)
  if [[ ! -f "/opt/aiopc/springboot3/deploy/.env" ]]; then
    if [[ -f "${PROJECT_ROOT}/../.env" ]]; then
      cp "${PROJECT_ROOT}/../.env" /opt/aiopc/springboot3/deploy/.env
      log "  ✓ .env 已同步到 clone 路径"
    else
      fail "clone 后 .env 不存在,且 PROJECT_ROOT 上级目录也无 .env"
    fi
  fi

  log "Stage 2 完成"
}
```

- [ ] **Step 2: bash 语法校验**

Run:
```bash
bash -n D:/work-ai/0401-lumen-opc/springboot3/deploy/scripts/bootstrap-remote.sh && echo "SYNTAX OK"
```

Expected: `SYNTAX OK`

- [ ] **Step 3: Commit**

```bash
git add springboot3/deploy/scripts/bootstrap-remote.sh
git commit -m "feat(deploy): W79 bootstrap Stage 2 — git/python3 装 + 关 SELinux + clone repo"
```

---

## Task 6: Implement Stage 3 (起基建)

**Files:**
- Modify: `springboot3/deploy/scripts/bootstrap-remote.sh`

- [ ] **Step 1: 把 stage_3 替换为真实实现**

替换 `stage_3() { : "placeholder"; }` 为:

```bash
stage_3() {
  log "起基建 (6 容器: nacos1/mysql/redis/rabbitmq/minio/qdrant)..."
  cd /opt/aiopc/springboot3/deploy

  # 已起则跳过
  local running
  running=$(docker compose ps --status running --services 2>/dev/null | wc -l)
  if (( running >= 6 )); then
    log "  ✓ 基建已起 (${running} 个 running),跳过"
  else
    docker compose up -d nacos1 mysql redis rabbitmq minio qdrant \
      || fail "docker compose up 基建失败"
    log "  ✓ 基建容器已拉起,等 MySQL initdb..."
  fi

  # 等 MySQL 就绪 (initdb 需 30-60s)
  log "等 MySQL 就绪..."
  local tries=60
  while (( tries > 0 )); do
    if docker exec aiopc-mysql mysql -uroot -p"${COMPOSE_MYSQL_PWD}" \
        -e "SELECT 1" >/dev/null 2>&1; then
      log "  ✓ MySQL OK"
      break
    fi
    sleep 2
    tries=$((tries - 1))
  done
  if (( tries == 0 )); then
    warn "MySQL 60s 内未就绪,看下 docker logs aiopc-mysql"
    docker logs --tail 30 aiopc-mysql >&2
    fail "MySQL 启动超时"
  fi

  # 等 Nacos 就绪
  log "等 Nacos 就绪..."
  tries=30
  while (( tries > 0 )); do
    if curl -sf -m 3 http://127.0.0.1:8848/nacos/v1/cs/health >/dev/null 2>&1; then
      log "  ✓ Nacos OK"
      break
    fi
    sleep 2
    tries=$((tries - 1))
  done
  if (( tries == 0 )); then
    warn "Nacos 60s 内未就绪,看下 docker logs aiopc-nacos-1"
    docker logs --tail 30 aiopc-nacos-1 >&2
    fail "Nacos 启动超时"
  fi

  log "Stage 3 完成"
}
```

- [ ] **Step 2: bash 语法校验**

Run:
```bash
bash -n D:/work-ai/0401-lumen-opc/springboot3/deploy/scripts/bootstrap-remote.sh && echo "SYNTAX OK"
```

Expected: `SYNTAX OK`

- [ ] **Step 3: Commit**

```bash
git add springboot3/deploy/scripts/bootstrap-remote.sh
git commit -m "feat(deploy): W79 bootstrap Stage 3 — 起 6 基建 + 等 MySQL/Nacos 就绪"
```

---

## Task 7: Implement Stage 4 (数据迁移 + 推 Nacos + 起业务)

**Files:**
- Modify: `springboot3/deploy/scripts/bootstrap-remote.sh`

- [ ] **Step 1: 把 stage_4 替换为真实实现**

替换 `stage_4() { : "placeholder"; }` 为:

```bash
stage_4() {
  cd /opt/aiopc/springboot3/deploy

  # 4.1 解压 migrate 包
  log "解压迁移包到 ${STAGING}/ ..."
  local migrate_tar
  migrate_tar="$(ls -t "${STAGING}"/aiopc-migrate-*.tar.gz | head -1)"
  tar -xzf "${migrate_tar}" -C "${STAGING}/"
  log "  ✓ 解压完成"

  # 4.2 校验 dump 文件
  if [[ ! -f "${STAGING}/mysql-dump.sql.gz" ]]; then
    fail "迁移包内缺 mysql-dump.sql.gz"
  fi
  log "  ✓ mysql-dump.sql.gz 存在 ($(du -h "${STAGING}/mysql-dump.sql.gz" | cut -f1))"

  # 4.3 校验 opc_* 表是否已存在(幂等保护)
  local existing_opc_tables
  existing_opc_tables=$(docker exec aiopc-mysql mysql -uroot -p"${COMPOSE_MYSQL_PWD}" \
    -N -B -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='ry-vue-opc' AND TABLE_NAME LIKE 'opc\\_%'" 2>/dev/null || echo 0)

  if (( existing_opc_tables >= 10 )); then
    log "  ✓ 数据库已有 ${existing_opc_tables} 张 opc_* 表,跳过 restore (要重导:rm /opt/aiopc-data/.bootstrap-stages-ok.4)"
  else
    # 4.4 备份当前空库(给 restore 失败时回滚用)
    log "备份当前空库 (pre-restore) ..."
    mkdir -p "${DATA_ROOT}/backups"
    docker exec aiopc-mysql mysqldump -uroot -p"${COMPOSE_MYSQL_PWD}" \
      --all-databases --default-character-set=utf8mb4 --routines --triggers \
      2>/dev/null | gzip > "${DATA_ROOT}/backups/pre-restore-$(date +%Y%m%d-%H%M%S).sql.gz"
    log "  ✓ pre-restore 备份完成"

    # 4.5 restore dump
    log "restore MySQL dump ..."
    if ! gunzip -c "${STAGING}/mysql-dump.sql.gz" \
        | docker exec -i aiopc-mysql mysql -uroot -p"${COMPOSE_MYSQL_PWD}" \
            --default-character-set=utf8mb4; then
      warn "MySQL restore 失败,自动回滚到 pre-restore"
      local pre_restore
      pre_restore=$(ls -t "${DATA_ROOT}"/backups/pre-restore-*.sql.gz | head -1)
      if [[ -n "${pre_restore}" ]]; then
        gunzip -c "${pre_restore}" | docker exec -i aiopc-mysql \
          mysql -uroot -p"${COMPOSE_MYSQL_PWD}" --default-character-set=utf8mb4 || true
      fi
      exit 2
    fi
    log "  ✓ MySQL restore 完成"
  fi

  # 4.6 推 Nacos 配置
  log "推 Nacos 配置 ..."
  cd /opt/aiopc/springboot3/deploy/nacos
  bash import-dev.sh || fail "import-dev.sh 失败"
  cd ..
  log "  ✓ Nacos 配置推送完成"

  # 4.7 起所有业务容器 (首次会 build 镜像,~25-40 min on 2-4 vCPU)
  log "起业务容器 (22 个,首次 build 预计 25-40 min) ..."
  docker compose up -d || fail "docker compose up 业务容器失败 (exit 3)"
  log "  ✓ 业务容器已拉起,等 60s 让服务注册 Nacos"
  sleep 60

  log "Stage 4 完成"
}
```

- [ ] **Step 2: 把 stage_4 失败 exit code 改回 exit 3 (而非 fail)**

注意上面失败 exit 用了 `exit 2` (回滚) 或 `fail "..."` (fail 函数 exit 1)。`docker compose up -d` 失败期望 exit 3。修改 line:

```bash
docker compose up -d || { warn "docker compose up 业务容器失败"; exit 3; }
```

- [ ] **Step 3: bash 语法校验**

Run:
```bash
bash -n D:/work-ai/0401-lumen-opc/springboot3/deploy/scripts/bootstrap-remote.sh && echo "SYNTAX OK"
```

Expected: `SYNTAX OK`

- [ ] **Step 4: Commit**

```bash
git add springboot3/deploy/scripts/bootstrap-remote.sh
git commit -m "feat(deploy): W79 bootstrap Stage 4 — restore + 推 Nacos + 起业务 + exit 2/3 区分"
```

---

## Task 8: Implement Stage 5 (Verification)

**Files:**
- Modify: `springboot3/deploy/scripts/bootstrap-remote.sh`

- [ ] **Step 1: 把 stage_5 替换为真实实现**

替换 `stage_5() { : "placeholder"; }` 为:

```bash
stage_5() {
  cd /opt/aiopc/springboot3/deploy

  log "跑 health-check.sh (期望 50/50 PASS)..."
  local health_log="${DATA_ROOT}/health-$(date +%Y%m%d-%H%M%S).log"
  if bash scripts/health-check.sh > "${health_log}" 2>&1; then
    local pass_count
    pass_count=$(grep -c "OK" "${health_log}" || echo 0)
    log "  ✓ health-check 通过: ${pass_count} OK (full log: ${health_log})"
  else
    local fail_count
    fail_count=$(grep -c "FAIL" "${health_log}" || echo 0)
    warn "health-check 有 ${fail_count} 项失败 (log: ${health_log})"
    if (( fail_count >= 20 )); then
      warn "失败 >= 20 项,业务瘫痪。退出码 4"
      tail -50 "${health_log}" >&2
      exit 4
    fi
  fi

  # Smoke test 5 项关键业务路径
  log "跑 smoke test (5 项关键业务)..."
  local token
  token=$(curl -s -X POST http://127.0.0.1:8080/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}' \
    | jq -r '.data.access_token // ""')

  if [[ -z "${token}" || "${token}" == "null" ]]; then
    fail "登录失败,拿不到 token。检查 aiopc-auth 容器状态"
  fi
  log "  ✓ 登录成功"

  local fail_paths=()
  for path in \
      "/opc/erp/product/list?companyId=1" \
      "/opc/crm/customer?page=1" \
      "/opc/hr/job/list" \
      "/opc/community/module/list" \
      "/opc/notification/inbox/unread-count"; do
    if curl -fs -H "Authorization: Bearer ${token}" \
        "http://127.0.0.1:8080${path}" >/dev/null; then
      log "  ✓ ${path}"
    else
      warn "  ✗ ${path}"
      fail_paths+=("${path}")
    fi
  done

  if (( ${#fail_paths[@]} > 2 )); then
    warn "smoke test 失败 > 2 项,业务可能未完全起。看 health log"
    exit 4
  fi

  log "Stage 5 完成"
}
```

- [ ] **Step 2: bash 语法校验**

Run:
```bash
bash -n D:/work-ai/0401-lumen-opc/springboot3/deploy/scripts/bootstrap-remote.sh && echo "SYNTAX OK"
```

Expected: `SYNTAX OK`

- [ ] **Step 3: Commit**

```bash
git add springboot3/deploy/scripts/bootstrap-remote.sh
git commit -m "feat(deploy): W79 bootstrap Stage 5 — health-check + 5 项 smoke test + exit 4 判定"
```

---

## Task 9: Update `RECOVERY.md` §15 (Remote SSH Bootstrap 节)

**Files:**
- Modify: `springboot3/deploy/RECOVERY.md` (append §15 before last "Last updated")

- [ ] **Step 1: 在 RECOVERY.md 末尾追加 §15**

在最后一行 `**Last updated:** ...` 之前追加:

```markdown
---

## 15. Remote SSH Bootstrap (W79)

> 在一台**新**的 Rocky Linux 8.10 云 VM 上,零基础一键拉起 OPC 全栈。

### 前置 (用户在本地准备)

```bash
# 本机跑,导出 MySQL + Nacos → 迁移包
cd springboot3/deploy
bash scripts/backup-mysql-local.sh
# 输出: /tmp/aiopc-migrate-YYYYMMDD-HHMMSS.tar.gz

# scp 到 VM
scp /tmp/aiopc-migrate-*.tar.gz root@<VM_IP>:/tmp/aiopc-staging/
```

### 在 VM 上 (5 阶段,~50 min 首次)

```bash
# Stage 0: 前置 (校验 OS/磁盘/.env/migrate 包)
# Stage 1: 装 Docker CE
# Stage 2: 装 git/python3 + clone 仓库
# Stage 3: 起 6 基建 (nacos/mysql/redis/rabbitmq/minio/qdrant)
# Stage 4: restore MySQL + 推 Nacos + 起 22 业务容器
# Stage 5: health-check + smoke test

cd /opt/aiopc/springboot3/deploy
bash scripts/bootstrap-remote.sh
```

### 状态 / 续跑 / 中止

```bash
bash scripts/bootstrap-remote.sh --status                 # 当前进度
bash scripts/bootstrap-remote.sh --from 4                # 从 Stage 4 续跑
bash scripts/bootstrap-remote.sh --abort                  # 停所有容器,保留数据
```

### 端口 (仅 host 暴露)

- 8079 — 前端
- 8080 — 网关
- 8848 — Nacos 控制台

SSH 端口转发访问:
```bash
ssh -L 8079:127.0.0.1:8079 -L 8080:127.0.0.1:8080 -L 8848:127.0.0.1:8848 root@<VM_IP>
# 浏览器: http://localhost:8079
```

### 失败排查

| 退出码 | 含义 | 看哪 |
|---|---|---|
| 1 | 前置依赖缺失 | 脚本输出 |
| 2 | MySQL restore 失败 (已自动回滚) | `${DATA_ROOT}/backups/pre-restore-*.sql.gz` |
| 3 | docker compose build 失败 | maven 错误,通常 `dnf install maven` 后重跑 |
| 4 | health-check < 30/50 PASS | `${DATA_ROOT}/health-*.log` + `docker logs <container>` |

### 不起的服务 (节省资源)

`elasticsearch` / `prometheus` / `grafana` / `skywalking-*` / `aiopc-insight`。需要时单独加。

```

- [ ] **Step 2: 更新 Last updated 行**

找到:
```
**Last updated:** 2026-09-14 (W74 — added opc-content section §14 + 4 表 schema initdb + 6 seed + 10 health-check + e2e_content.py + 4 关键部署修复)
```

改为:
```
**Last updated:** 2026-09-22 (W79 — added §15 Remote SSH Bootstrap + backup-mysql-local.sh + bootstrap-remote.sh)
```

- [ ] **Step 3: Commit**

```bash
git add springboot3/deploy/RECOVERY.md
git commit -m "docs(deploy): W79 RECOVERY.md §15 — Remote SSH Bootstrap 一键远程部署指南"
```

---

## Task 10: Update `.env.example` (Rocky 8 / placeholder 注释)

**Files:**
- Modify: `springboot3/deploy/.env.example`

- [ ] **Step 1: 读现有 .env.example**

Run:
```bash
cat D:/work-ai/0401-lumen-opc/springboot3/deploy/.env.example
```

- [ ] **Step 2: 追加 Rocky 8 + placeholder 注释到末尾**

追加:
```bash
# ============================================================
# W79 远程 VM 部署说明
# ============================================================
# 1. 把本文件复制为 .env: cp .env.example .env
# 2. 编辑 .env 填真实 LLM key (或暂时用下面的 placeholder)
# 3. 如果部署到 Rocky Linux 8.10 VM,见 RECOVERY.md §15
#
# Placeholder key (开发/演示用,真实 AI 调用会 401):
DEEPSEEK_API_KEY=sk-placeholder-deepseek
MINIMAX_API_KEY=ey-placeholder
#
# 真实 key 格式 (生产前填):
# DEEPSEEK_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxx
# MINIMAX_API_KEY=eyJhbGciOiJIUzI1NiJ9.xxxxxxxxxxxxxxxx
```

- [ ] **Step 3: Commit**

```bash
git add springboot3/deploy/.env.example
git commit -m "docs(deploy): W79 .env.example — Rocky 8 + placeholder key 注释"
```

---

## Task 11: Write `W79-REMOTE-SSH-DEPLOY-VERIFICATION.md` 报告模板

**Files:**
- Create: `docs/verification/late/W79-REMOTE-SSH-DEPLOY-VERIFICATION.md`

- [ ] **Step 1: 写报告模板**

```markdown
# W79 Remote SSH OPC Deploy — VERIFICATION

> **日期**: YYYY-MM-DD
> **执行人**: <name>
> **目标 VM**: Rocky Linux 8.10 @ <VM_IP>, <vCPU> vCPU / <RAM> GB / <Disk> GB

---

## 1. 服务器规格

```
$ uname -a
Linux <hostname> 4.18.0-553.el8_10.x86_64 #1 SMP ...

$ cat /etc/os-release
NAME="Rocky Linux"
VERSION="8.10 (Green Obsidian)"
...

$ nproc
<NUM>

$ free -h
              total        used        free      shared  buff/cache   available
Mem:           <RAM>Gi       <USED>Gi       ...

$ df -h /opt/aiopc-data
Filesystem      Size  Used Avail Use% Mounted on
/dev/vda1        <DISK>G   <USED>G  <AVAIL>G  XX% /
```

## 2. 容器清单

```
$ docker compose ps
NAME                              STATUS              PORTS
aiopc-nacos-1                     Up (healthy)        0.0.0.0:8848->8848/tcp, ...
aiopc-mysql                       Up (healthy)        0.0.0.0:3308->3306/tcp
aiopc-redis                       Up                  0.0.0.0:6379->6379/tcp
aiopc-rabbitmq                    Up                  0.0.0.0:5672->5672/tcp, ...
aiopc-minio                       Up (healthy)        0.0.0.0:9000-9001->9000-9001/tcp
aiopc-qdrant                      Up                  0.0.0.0:6333-6334->6333-6334/tcp
aiopc-gateway                     Up                  0.0.0.0:8080->8080/tcp
aiopc-frontend                    Up                  0.0.0.0:8079->8079/tcp
aiopc-auth                        Up
aiopc-system                      Up
aiopc-ai-core                     Up
aiopc-user-center                 Up
aiopc-agent-hub                   Up
aiopc-billing                     Up
aiopc-finance                     Up
aiopc-notification                Up
aiopc-erp                         Up
aiopc-crm                         Up
aiopc-community                   Up
aiopc-hr                          Up
aiopc-content                     Up
```

预期: 21 个 (6 基建 + 3 平台 + 12 OPC 业务)

## 3. health-check.sh 输出

```
$ bash scripts/health-check.sh | tail -60

[OK] aiopc-mysql running
[OK] aiopc-redis running
[OK] aiopc-nacos-1 running
[OK] Nacos opc-ai-core 注册 1 实例
[OK] opc-ai-core /actuator/health UP
[OK] /opc/ai-core/models 200 OK
... (省略中间)
[OK] /opc/content/script/recent 200 OK
[OK] /opc/content/publish/list 200 OK
[OK] /opc/community/module/list 200 OK
[OK] /opc/community/rating/list 200 OK
[OK] 鉴权拦截正确
[OK] 数据库健康

============================================================
健康检查通过: XX/XX PASS (期望 50/50)
============================================================
```

## 4. Smoke test 输出

```
$ TOKEN=$(curl -s -X POST http://127.0.0.1:8080/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}' | jq -r .data.access_token)

✓ 登录成功 (token length=212)

$ curl -fs "http://127.0.0.1:8080/opc/erp/product/list?companyId=1"  >/dev/null && echo "✓ ERP"
✓ ERP

$ curl -fs "http://127.0.0.1:8080/opc/crm/customer?page=1"  >/dev/null && echo "✓ CRM"
✓ CRM

$ curl -fs "http://127.0.0.1:8080/opc/hr/job/list"  >/dev/null && echo "✓ HR"
✓ HR

$ curl -fs "http://127.0.0.1:8080/opc/community/module/list"  >/dev/null && echo "✓ Community"
✓ Community

$ curl -fs "http://127.0.0.1:8080/opc/notification/inbox/unread-count"  >/dev/null && echo "✓ Notification"
✓ Notification
```

## 5. AI 接口预期失败验证

```
$ curl -i http://127.0.0.1:8080/opc/ai-core/chat 2>&1 | head -20
HTTP/1.1 401
...
{"msg":"API key invalid"}

(预期:401 因为 DEEPSEEK_API_KEY=sk-placeholder-deepseek;填真 key 后变 200)
```

## 6. SSH 端口转发验证 (截图)

附:
- 浏览器访问 http://localhost:8079 登录页截图
- 登录后主页加载截图
- Nacos 控制台 http://localhost:8848/nacos 服务清单截图

## 7. 数据迁移验证

```
$ docker exec aiopc-mysql mysql -uroot -p'Opc@2026!' ry-vue-opc -e "SHOW TABLES;" | wc -l
<NUM>   (期望 >= 80)

$ docker exec aiopc-mysql mysql -uroot -p'Opc@2026!' ry-vue-opc \
    -e "SELECT COUNT(*) FROM opc_crm_customer"
<NUM>   (期望与本机 dump 前一致)

$ docker exec aiopc-mysql mysql -uroot -p'Opc@2026!' ry-vue-opc \
    -e "SELECT COUNT(*) FROM opc_erp_product"
<NUM>
```

## 8. 结论

- [x] 21 个容器 Up
- [x] health-check 通过 (XX/XX PASS)
- [x] 5 项 smoke test 全绿
- [x] AI 接口预期失败 (placeholder key)
- [x] MySQL 数据迁移成功 (行数与 dump 前一致)
- [x] SSH 端口转发可访问前端/Nacos

W79 Remote SSH Deploy 任务完成。

---
```

- [ ] **Step 2: Commit**

```bash
git add docs/verification/late/W79-REMOTE-SSH-DEPLOY-VERIFICATION.md
git commit -m "docs(deploy): W79 VERIFICATION report template"
```

---

## Task 12: Final integration check + push

**Files:**
- Verify: 所有 11 个 commit 都已 staged

- [ ] **Step 1: 检查所有 commit 都干净**

Run:
```bash
cd D:/work-ai/0401-lumen-opc
git log --oneline -12
git status
```

Expected: 12 个新 commit (Task 1-11 + self-review),`git status` 干净

- [ ] **Step 2: 跑最终 bash 语法校验**

Run:
```bash
bash -n springboot3/deploy/scripts/backup-mysql-local.sh && echo "BACKUP-LOCAL SYNTAX OK"
bash -n springboot3/deploy/scripts/bootstrap-remote.sh && echo "BOOTSTRAP-REMOTE SYNTAX OK"
```

Expected: 两条 `SYNTAX OK`

- [ ] **Step 3: shellcheck 静态分析 (若可用)**

Run:
```bash
if command -v shellcheck >/dev/null 2>&1; then
  shellcheck springboot3/deploy/scripts/backup-mysql-local.sh
  shellcheck springboot3/deploy/scripts/bootstrap-remote.sh
else
  echo "shellcheck 未装,跳过 (可选,装 brew install shellcheck)"
fi
```

Expected: shellcheck 0 error,或 echo 跳过消息

- [ ] **Step 4: dry-run --help 校验两个脚本都正常**

Run:
```bash
bash springboot3/deploy/scripts/backup-mysql-local.sh --help 2>&1 | head -5 || true
# (backup-mysql-local 没有 --help,跑空参数会 fail — 这是预期)
bash springboot3/deploy/scripts/bootstrap-remote.sh --help | head -15
```

Expected: bootstrap 输出 "AIOPC 远程引导" 帮助

- [ ] **Step 5: 最终 push**

```bash
git push origin main
```

---

## Self-Review Checklist

完成所有 Task 后对照检查:

- [ ] Task 1-2: 两个脚本骨架 OK
- [ ] Task 3-8: 5 阶段 stage 函数全部实现 (非 placeholder)
- [ ] Task 9-10: RECOVERY.md §15 + .env.example 注释已加
- [ ] Task 11: VERIFICATION 报告模板已写
- [ ] Task 12: 12 个 commit 干净 + bash 语法校验通过 + push 成功
- [ ] spec §1-5 所有内容都有对应 Task (对照 `2026-09-22-remote-ssh-opc-deploy-design.md`)
- [ ] 无 placeholder (搜 `: "placeholder"` 应 0 命中)
- [ ] 无 TODO/TBD/FIXME
- [ ] exit code 1/2/3/4 与 spec §4.2 一致
