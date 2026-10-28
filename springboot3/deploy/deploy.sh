#!/usr/bin/env bash
# ============================================================
# AIOPC 一键全栈 deploy (W50 固化)
#
# 用法:
#   cd springboot3/deploy
#   bash deploy.sh                          # 拉起完整 aiopc stack (mysql + nacos + 业务容器)
#   bash deploy.sh status                   # 容器清单
#   bash deploy.sh stop                     # 停所有容器,保留 host 数据
#   bash deploy.sh nuke                     # ⚠️ 删 mysql/redis 数据卷重新初始化
#   bash deploy.sh rebuild crm              # 重建镜像(改完 OPC 源码后)
#   bash deploy.sh rebuild gateway          # 只重建网关 (改完 ruoyi-gateway 后)
#   bash deploy.sh rebuild auth             # 只重建认证服务 (改完 ruoyi-auth 后)
#   bash deploy.sh rebuild frontend         # 只重建前端 (改完 vue3-typescript 后)
#   bash deploy.sh rebuild all              # 重建所有 aiopc-* 镜像
#   bash deploy.sh snapshot                 # 跑状态快照
#   bash deploy.sh health                   # 跑健康检查
#
# 设计:
#   - Windows git-bash 兼容 (forward slash 路径 + Python 替 curl)
#   - 失败立即退出 (set -euo pipefail)
#   - .env 缺失时友好提示,不强行复制
#   - 镜像 build 用 mvn 在 springboot3/ 跑,不在 deploy/ 里
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${PROJECT_ROOT}/.." && pwd)"
JAVA_HOME="${JAVA_HOME:-}"
if [[ -z "${JAVA_HOME}" ]]; then
  # Linux 上找系统里的 JDK 17 —— 原来写死 Windows 路径, 在 Linux 跑会直接
  # 「找不到 java」, 而 JAVA_HOME 没设时 mvn 会退回系统默认的 1.8 编译失败。
  for cand in /usr/lib/jvm/java-17-openjdk-* /usr/lib/jvm/jdk-17*; do
    if [[ -x "${cand}/bin/javac" ]]; then JAVA_HOME="${cand}"; break; fi
  done
fi
JAVA_HOME="${JAVA_HOME:-C:/Program Files/Java/jdk-17.0.17.10-hotspot}"

CMD="${1:-help}"
if [[ $# -gt 0 ]]; then
  shift
fi

# ---------- helpers ----------
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

check_env_file() {
  if [[ ! -f "${SCRIPT_DIR}/.env" ]]; then
    if [[ -f "${SCRIPT_DIR}/.env.example" ]]; then
      warn ".env 不存在,请先: cp .env.example .env && 编辑填真值"
      warn "  必填: DEEPSEEK_API_KEY + MINIMAX_API_KEY"
      if [[ -t 0 ]]; then
        read -p "  按 Enter 继续(非交互环境会跳过): "
      fi
    else
      fail ".env 和 .env.example 都不存在"
    fi
  fi
}

wait_mysql_ready() {
  log "等 MySQL 起来 (initdb 约 30-60s)..."
  local tries=60
  while (( tries > 0 )); do
    if docker exec aiopc-mysql mysql -uroot -p'Opc@2026!' \
        -e "SELECT 1" >/dev/null 2>&1; then
      log "  MySQL OK"
      return 0
    fi
    sleep 2
    tries=$((tries - 1))
  done
  fail "MySQL 60s 内未就绪,查看 docker logs aiopc-mysql"
}

wait_nacos_ready() {
  log "等 Nacos 起来..."
  local tries=30
  while (( tries > 0 )); do
    if curl -sf -m 3 http://127.0.0.1:8848/nacos/v1/cs/health >/dev/null 2>&1; then
      log "  Nacos OK"
      return 0
    fi
    sleep 2
    tries=$((tries - 1))
  done
  fail "Nacos 60s 内未就绪,查看 docker logs aiopc-nacos-1"
}

# ---------- subcommands ----------

cmd_up() {
  require_docker
  check_env_file

  log "启动基建 (nacos + mysql + redis + 其他)..."
  cd "${SCRIPT_DIR}"
  docker compose up -d nacos1 mysql redis rabbitmq qdrant minio elasticsearch 2>&1 | tail -5
  wait_mysql_ready
  wait_nacos_ready

  log "推送 Nacos dev 配置..."
  cd "${SCRIPT_DIR}/nacos"
  ./import-dev.sh

  log "启动业务容器..."
  cd "${SCRIPT_DIR}"
  docker compose up -d 2>&1 | tail -5

  log ""
  log "=================================================="
  log " aiopc stack 已拉起 (或已运行)"
  log "=================================================="
  log "  - 前端:    http://localhost:8079"
  log "  - 网关:    http://localhost:8080"
  log "  - Nacos:   http://localhost:8848/nacos"
  log ""
  log " 跑 bash deploy.sh health 验证"
}

cmd_status() {
  require_docker
  cd "${SCRIPT_DIR}"
  docker compose ps
}

cmd_stop() {
  require_docker
  cd "${SCRIPT_DIR}"
  docker compose stop
  log "所有 aiopc-* 容器已停 (host 数据保留)"
}

cmd_nuke() {
  require_docker
  warn "⚠️  将删除以下 host 数据: deploy/mysql/data deploy/redis/data deploy/nacos/conf"
  warn "    (其它容器数据如 qdrant/minio/es 不动)"
  if [[ -t 0 ]]; then
    read -p "  确认? 输入 yes 继续: " ans
    [[ "${ans}" == "yes" ]] || { log "已取消"; exit 0; }
  fi
  cd "${SCRIPT_DIR}"
  docker compose down -v aiopc-mysql aiopc-redis aiopc-nacos-1
  rm -rf mysql/data/* redis/data/* nacos/conf/* 2>/dev/null || true
  log "数据卷已清空,跑 'bash deploy.sh up' 重建"
}

cmd_rebuild() {
  require_docker
  local target="${1:-}"
  if [[ -z "${target}" ]]; then
    fail "用法: deploy.sh rebuild <svc-name|all|gateway|auth|frontend>     (e.g. crm / notification / ai-core / gateway / auth / frontend / all)"
  fi

  # 共享模块必须**先 install 进本地仓库** ——
  #
  # 下面 build_one 里的 `dependency:copy-dependencies` 是**另一次** mvn 调用,
  # 不带 -am, 解析不到 reactor, 依赖只能从 ~/.m2 拿。而 ~/.m2 里的
  # ruoyi-common-security 是上一次 install 时的快照: 共享模块改了代码
  # (比如新增租户守卫) 而没 install 的话, 镜像里就是**旧包** ——
  # 部署看起来成功了, 实际上守卫根本没生效, 而且不报任何错。
  #
  # 一句话: package 只写 target/, install 才写 ~/.m2。两者不是一回事。
  #
  # 这条坑不只踩在 ruoyi-common-security 上 —— OPC 的两个共享库
  # (opc-common / opc-ai-core) 同样是别人的依赖, 同样会从 ~/.m2 拿快照。
  # 之前只 install 了 ruoyi-common-security, 于是 opc-ai-core 一直是
  # 9 月 23 日那个快照: 10-23 的 PromptGuard 接入 /opc/llm/chat、
  # 10-26 的两笔 LLM 计量修复, 仓库里都有、生产上**一条都没生效** ——
  # 而 deploy.sh 照样报成功。
  #
  # 所以这里必须把**所有**共享库都 install 一遍。
  #
  # 前端不走这条 (它压根不碰 Maven), 跳过 —— 省一次 install, 也少一个失败点。
  if [[ "${target}" != "frontend" ]]; then
    log "[shared] mvn install 共享模块 (ruoyi-common-security / opc-common / opc-ai-core)"
    cd "${PROJECT_ROOT}"
    JAVA_HOME="${JAVA_HOME}" \
      mvn -pl ruoyi-common/ruoyi-common-security,opc-common,ruoyi-modules/opc-ai-core \
        -am clean install \
        -Dmaven.test.skip=true -Dspring-boot.repackage.skip=true -q \
      || fail "共享模块 install 失败"
  fi

  # compose 服务名: 大多数是 aiopc-<去掉 opc- 前缀>, ruoyi-system 是个例外
  svc_of() {
    case "$1" in
      ruoyi-system) echo "aiopc-system" ;;
      *)            echo "aiopc-${1#opc-}" ;;
    esac
  }
  # 打包方式:
  #
  # thin: OPC 各模块 —— pom 里用 <phase>none</phase> 挡掉 repackage,
  #       打成 thin jar, 依赖靠 dependency:copy-dependencies 拷进 lib/,
  #       Dockerfile 用 `-cp x.jar:lib/*` 启动。
  # fat:  ruoyi-system —— pom 里 repackage 正常跑, 打成带 Main-Class 的
  #       fat jar, Dockerfile 用 `java -jar` 启动, 没有 lib/ 目录。
  #
  # 之前对所有模块一律加 -Dspring-boot.repackage.skip=true, 把 ruoyi-system
  # 也打成了 thin jar → 容器起来就 "no main manifest attribute" 无限重启,
  # 而部署脚本照样报成功。
  mode_of() {
    case "$1" in
      ruoyi-system) echo "fat" ;;
      *)            echo "thin" ;;
    esac
  }

  build_one() {
    local module="$1"
    # compose 服务名与模块名**不**一一对应:
    #   opc-crm      → aiopc-crm       (去掉 opc- 前缀)
    #   ruoyi-system → aiopc-system    (例外, 见 svc_of)
    # 之前写死 aiopc-${module} → 拼成 aiopc-opc-crm, compose 回一句
    # "no such service" 就把整条部署断了 —— 而且是断在第一个模块,
    # 看起来像成功。所以下面还有一道存在性检查。
    local svc="${2:-$(svc_of "${module}")}"
    local mode="${3:-$(mode_of "${module}")}"
    local svc_dir="${PROJECT_ROOT}/ruoyi-modules/${module}"
    if [[ ! -d "${svc_dir}" ]]; then
      warn "  模块 ${module} 不存在,跳过"
      return
    fi
    # 注意: cmd_rebuild 里已经 cd 到 ${PROJECT_ROOT} 了, 这里的 cwd 不是
    # deploy/ —— 所以必须用 -f 把 compose 文件指出来, 否则 docker compose
    # 找不到文件, 这道检查会误判成「服务名拼错了」。
    #
    # 还有: 不能写成 `docker compose ... | grep -qx`。grep -q 一匹配就退出、
    # 关掉管道, docker compose 还在写就吃 SIGPIPE(141), 遇上 pipefail
    # 整个管道被判成失败 —— 服务名明明是对的也报「拼错了」。所以先收进
    # 变量再匹配。
    local services
    services="$(docker compose -f "${SCRIPT_DIR}/docker-compose.yml" config --services 2>/dev/null || true)"
    if ! grep -qx "${svc}" <<<"${services}"; then
      fail "compose 里没有服务 '${svc}' (模块 ${module}) —— 服务名拼错了"
    fi
    cd "${PROJECT_ROOT}"
    if [[ "${mode}" == "fat" ]]; then
      log "[${module}] mvn clean package (fat jar) — 启动方式是 java -jar"
      JAVA_HOME="${JAVA_HOME}" \
        mvn -pl "ruoyi-modules/${module}" -am clean package \
          -Dmaven.test.skip=true -q
    else
      log "[${module}] mvn clean package + copy-dependencies"
      JAVA_HOME="${JAVA_HOME}" \
        mvn -pl "ruoyi-modules/${module}" -am clean package \
          -Dmaven.test.skip=true -Dspring-boot.repackage.skip=true -q
      JAVA_HOME="${JAVA_HOME}" \
        mvn -pl "ruoyi-modules/${module}" dependency:copy-dependencies \
          -DoutputDirectory=target/dependency -q
    fi

    log "[${module}] docker build ${svc}"
    cd "${SCRIPT_DIR}"
    docker compose build "${svc}"
    log "[${module}] restart"
    docker compose up -d "${svc}"
    log "[${module}] OK"
  }

  if [[ "${target}" == "all" ]]; then
    # 全部业务服务 —— 共享模块(ruoyi-common-security)被**每一个** OPC 服务
    # 依赖(直接或经 opc-common 传递), 所以改了共享模块就得全量重建,
    # 漏掉哪一个, 那个服务上就没有租户守卫。
    for m in opc-crm opc-notification opc-ai-core opc-user-center \
             opc-agent-hub opc-billing opc-finance opc-insight \
             opc-marketplace opc-community opc-content opc-erp opc-hr; do
      build_one "${m}"
    done
    # ruoyi-system 的 compose 服务名是 aiopc-system(不是 aiopc-ruoyi-system),
    # 而且它是 fat jar 启动(java -jar), 不能走 thin jar 那套
    build_one ruoyi-system
    build_gateway
    build_auth
    build_frontend
  elif [[ "${target}" == "gateway" ]]; then
    # 网关不在 ruoyi-modules/ 下(build_one 那条路走不通:它在 ruoyi-gateway/),
    # 而且它是 fat jar(java -jar 启动),不能走 thin jar 那套。
    #
    # 以前没有这个入口: `rebuild gateway` 掉进 build_one → 「模块 gateway 不存在,
    # 跳过」→ 然后照样打印「rebuild 完成」。什么都没做,却看着像成功了。
    # 这跟 build_one 里「服务名拼错了就 fail」的严格程度完全两样。
    build_gateway
  elif [[ "${target}" == "auth" ]]; then
    # 认证服务同理: 它在 ruoyi-auth/ 下(不在 ruoyi-modules/ 里),
    # 也是 fat jar。跟网关一样, 以前没有任何入口能重建它 ——
    # 改完 ruoyi-auth 的代码, 只能自己去敲 mvn + docker build,
    # 或者干脆忘了, 让线上跑着上一个 jar。
    build_auth
  elif [[ "${target}" == "frontend" ]]; then
    # 前端不是 ruoyi-modules/ 下的 Maven 模块, build_one 那条路走不通
    # (没有 pom、没有镜像构建脚本, 它是 nginx + 预构建产物)。
    #
    # 以前前端只能靠 rebuild all 顺带重建 —— 改一版前端就得把
    # 13 个后端服务 + 网关 + ruoyi-system 全重建一遍, 又慢又容易
    # 在半路出岔子。现在前端有了自己的入口。
    build_frontend
  else
    build_one "${target}"
  fi
  log "rebuild 完成,跑 bash scripts/health-check.sh 验证"
}

# 网关: fat jar(-jar 启动), 跟业务服务的 thin jar 流程不一样 ——
# 不能加 -Dspring-boot.repackage.skip=true, 否则打出来的 jar 跑不起来。
build_gateway() {
  log "[gateway] mvn clean package (fat jar) + docker build"
  cd "${PROJECT_ROOT}"
  JAVA_HOME="${JAVA_HOME}" \
    mvn -pl ruoyi-gateway -am clean package -Dmaven.test.skip=true -q \
    || fail "网关 package 失败"
  cd "${SCRIPT_DIR}"
  docker compose build aiopc-gateway || fail "网关 docker build 失败"
  docker compose up -d aiopc-gateway || fail "网关重启失败"
  log "[gateway] OK"
}

# 认证服务: 跟网关一样的路数 —— fat jar, 但它不在 ruoyi-modules/ 下,
# 所以 build_one 那条路("ruoyi-modules/<module>")也走不通。
#
# 以前**没有任何入口**能重建它: `rebuild auth` 会掉进 build_one →
# 「模块 auth 不存在, 跳过」→ 照样打印「rebuild 完成」。
# 结果是: 改了 ruoyi-auth 的代码, 线上跑的还是上一个 jar, 而且没人报错。
build_auth() {
  log "[auth] mvn clean package (fat jar) + docker build"
  cd "${PROJECT_ROOT}"
  JAVA_HOME="${JAVA_HOME}" \
    mvn -pl ruoyi-auth -am clean package -Dmaven.test.skip=true -q \
    || fail "认证服务 package 失败"
  cd "${SCRIPT_DIR}"
  docker compose build aiopc-auth || fail "认证服务 docker build 失败"
  docker compose up -d aiopc-auth || fail "认证服务重启失败"
  log "[auth] OK"
}

# 前端: 宿主机上已有 node_modules 就在宿主机 build, 产物交给
# Dockerfile.frontend.prebuilt 打包(省掉容器里那次 npm install);
# 没有 node_modules 就退回 Dockerfile.frontend —— 在容器里 npm install + 构建。
build_frontend() {
  local web_dir="${REPO_ROOT}/vue3-typescript"
  if [[ -d "${web_dir}/node_modules" ]]; then
    log "[frontend] 宿主机 npm run build:prod"
    (cd "${web_dir}" && npm run build:prod) || fail "前端构建失败"
    log "[frontend] docker build (prebuilt dist)"
    (cd "${web_dir}" && docker build \
      -f "${SCRIPT_DIR}/Dockerfile.frontend.prebuilt" \
      -t aiopc-frontend:latest .) || fail "前端 docker build 失败"
  else
    log "[frontend] 没有 node_modules,退回容器内构建"
    cd "${SCRIPT_DIR}"
    docker compose build aiopc-frontend || fail "前端 docker build 失败"
  fi
  cd "${SCRIPT_DIR}"
  docker compose up -d aiopc-frontend || fail "前端重启失败"
  log "[frontend] OK"
}

cmd_snapshot() {
  cd "${SCRIPT_DIR}/scripts"
  bash snapshot-stack.sh
}

cmd_health() {
  cd "${SCRIPT_DIR}/scripts"
  bash health-check.sh
}

cmd_help() {
  cat <<USAGE
AIOPC 一键全栈 deploy (W50 固化)

用法: bash deploy.sh <command> [args]

Commands:
  up                       拉起完整 aiopc stack (mysql + nacos + 业务容器, idempotent)
  status                   docker compose ps
  stop                     停所有容器,保留 host 数据
  nuke                     ⚠️  清 mysql/redis/nacos 数据卷
  rebuild <svc|all|gateway|auth|frontend>  重建指定服务镜像 (e.g. crm / notification / gateway / auth / frontend / all)
  snapshot                 跑全栈状态快照
  health                   跑健康检查 (36+ 项)
  help                     显示本帮助

示例:
  bash deploy.sh                       # -> 等价 deploy.sh help
  bash deploy.sh up
  bash deploy.sh rebuild crm
  bash deploy.sh rebuild gateway
  bash deploy.sh rebuild auth
  bash deploy.sh rebuild frontend
  bash deploy.sh rebuild all
  bash deploy.sh snapshot
  bash deploy.sh health
USAGE
}

# ---------- dispatch ----------
case "${CMD}" in
  help|-h|--help) cmd_help ;;
  up)        cmd_up ;;
  status)    cmd_status ;;
  stop)      cmd_stop ;;
  nuke)      cmd_nuke ;;
  rebuild)   cmd_rebuild "$@" ;;
  snapshot)  cmd_snapshot ;;
  health)    cmd_health ;;
  *)
    cmd_help
    exit 1
    ;;
esac