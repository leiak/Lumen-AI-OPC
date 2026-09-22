# W79 — Remote SSH OPC Full Stack Deploy

> **Status**: Draft (2026-09-22, brainstormed with user)
> **Goal**: 把 OPC 全栈(16 OPC 微服务 + Auth/Gateway/Frontend + 5 基建)部署到一台远程 Rocky Linux 8.10 云 VM,SSH 端口转发访问,不套 HTTPS/域名。从用户本地导出的 MySQL dump 迁移过去。
> **Scope**: 单 VM,2-4 vCPU / 4-8 GB RAM / 40-80 GB disk。不起 ES/Grafana/SkyWalking/Prometheus(省资源)。

---

## 0. Context & Background

### 0.1 现状

仓库 `D:\work-ai\0401-lumen-opc\` 已有完整的本地 Docker Compose 全栈:

- `springboot3/deploy/docker-compose.yml` — 20+ 容器(16 OPC + 3 RuoYi + Frontend + 7 基建)
- `springboot3/deploy/deploy.sh` — W50 固化的一键脚本(`up` / `status` / `stop` / `nuke` / `rebuild` / `health`)
- `springboot3/deploy/RECOVERY.md` — 9 节恢复手册 + 7 业务服务章节
- `springboot3/deploy/mysql-initdb.d/` — 16 个 schema + seed SQL
- `springboot3/deploy/nacos/import-dev.sh` — Nacos dev 配置推送
- `springboot3/deploy/scripts/health-check.sh` — 50+ 项端到端探针
- `springboot3/deploy/scripts/backup-mysql.sh` — mysqldump 备份/恢复

但**所有这些脚本都默认 Windows git-bash + 本机 Docker Desktop**,没有为远程 Linux VM 优化。本设计新增 **bootstrap-remote.sh** 把整套流程打包成"SSH 进去跑一行命令"。

### 0.2 已有相关设计

- `docs/superpowers/specs/2026-09-14-opc-content-design.md` — W74 opc-content 设计,提到 4 部署修复
- `docs/superpowers/specs/2026-09-12-opc-erp-design.md` — W72 opc-erp 设计,提到 11 表 schema
- `MEMORY.md` [[aiopc-deployment-persistence]] / [[aiopc-all-opc-stack]] / [[aiopc-llm-providers]] — 11 处部署踩坑教训

---

## 1. Architecture & Topology

### 1.1 目标主机

| 维度 | 值 |
|---|---|
| OS | Rocky Linux 8.10 (Green Obsidian),EOL 2029-05-31 |
| vCPU | 2-4 |
| RAM | 4-8 GB |
| Disk | 40-80 GB |
| 用户 | root(`uname -a` 显示 `Linux xiehui 4.18.0-553.el8_10.x86_64`) |
| 网络 | 内网/SSH 端口转发访问,不开放公网 Web |

### 1.2 容器清单(22 个)

**基建 (6)**: `aiopc-nacos-1` `aiopc-mysql` `aiopc-redis` `aiopc-rabbitmq` `aiopc-minio` `aiopc-qdrant`
- **不起**: `elasticsearch` (2GB 堆太重)、`prometheus`/`grafana`/`skywalking-oap`/`skywalking-ui`(监控/APM)

**平台 (3)**: `aiopc-gateway` (8080→host) `aiopc-auth` (19200→host) `aiopc-frontend` (8079→host)

**业务 (13)**: 1 RuoYi built-in + 12 OPC
- RuoYi: `aiopc-system` (9201)
- OPC: `aiopc-ai-core` (9301) `aiopc-user-center` (9302) `aiopc-agent-hub` (9303) `aiopc-billing` (9304) `aiopc-finance` (9305) `aiopc-notification` (9310) `aiopc-erp` (9311) `aiopc-crm` (9312) `aiopc-community` (9316) `aiopc-hr` (9322) `aiopc-content` (9325)
- 不起 `aiopc-insight` (9306,依赖 Qdrant + ES 较多,单 demo 用不到)

### 1.3 网络 & 端口

```
aiopc-net (bridge)
  ├─ aiopc-mysql:3306        (容器内)
  ├─ aiopc-redis:6379
  ├─ aiopc-nacos-1:8848/9848
  ├─ aiopc-rabbitmq:5672/15672
  ├─ aiopc-minio:9000/9001
  ├─ aiopc-qdrant:6333/6334
  ├─ aiopc-gateway:8080  →  host 8080
  ├─ aiopc-frontend:8079 →  host 8079
  └─ 业务容器 (9201/9301-9305/9310/9311/9312/9316/9322/9325)
```

Host 仅暴露 8079 / 8080 / 8848 (Nacos 控制台可选)。其余容器端口在 `aiopc-net` 内互访,网关统一对外。

### 1.4 目录布局

```
/opt/aiopc/                       ← git clone 目标
  ├─ springboot3/                 ← 仓库子目录
  │   └─ deploy/
  │       ├─ .env                 ← git ignored,手填 DEEPSEEK/MINIMAX 占位
  │       ├─ docker-compose.yml
  │       ├─ deploy.sh
  │       ├─ scripts/
  │       │   └─ bootstrap-remote.sh   ← 本设计新增
  │       ├─ nacos/
  │       └─ mysql-initdb.d/

/opt/aiopc-data/                  ← host bind mount 父目录(已 git ignored 类)
  ├─ mysql/data/         (/var/lib/mysql)
  ├─ redis/data/         (/data)
  ├─ nacos/conf/         (/home/nacos/conf)
  ├─ minio/data/         (/data)
  ├─ qdrant/data/        (/qdrant/storage)
  ├─ backups/            ← mysqldump 输出
  └─ snapshot-*.txt      ← snapshot-stack.sh 输出

/tmp/aiopc-staging/                ← bootstrap 临时区
  ├─ aiopc-migrate-YYYYMMDD.tar.gz  ← 用户 scp 上来
  ├─ mysql-dump.sql.gz
  └─ nacos-configs.tgz
```

### 1.5 访问流

```
用户笔记本
  └─ ssh -L 8079:127.0.0.1:8079 -L 8080:127.0.0.1:8080 root@<VM_IP>
      └─ 浏览器打开 http://localhost:8079
          └─ aiopc-frontend (nginx SPA + 反代 /prod-api/* → :8080)
              └─ aiopc-gateway (Spring Cloud Gateway)
                  └─ /opc/<svc>/** → aiopc-<svc>:<port>
```

---

## 2. Bootstrap Sequence (5 阶段)

每阶段独立,可 `bash bootstrap-remote.sh --from <n>` 续跑。

### 2.1 Stage 0 — Pre-flight

**用户手动**:
1. 在本机跑 `backup-mysql-local.sh`(本设计新增)→ 生成 `aiopc-migrate-YYYYMMDD.tar.gz`
2. `scp aiopc-migrate-*.tar.gz root@<VM_IP>:/tmp/aiopc-staging/`
3. 在 VM 上 `cp .env.example .env && vim .env` 填 LLM key(当前阶段用 placeholder)

**脚本校验**:
- `[ -f /tmp/aiopc-staging/aiopc-migrate-*.tar.gz ]`
- `[ -f /opt/aiopc/springboot3/deploy/.env ]` (clone 后才会有)
- `cat /etc/os-release | grep "Rocky Linux 8.10"`
- `df /opt/aiopc-data | awk 'NR==2 {print $4}' | numfmt --to=iec` ≥ 5 GB

### 2.2 Stage 1 — Install Docker

```bash
dnf config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
dnf install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker
docker compose version    # 期望 Docker Compose v2.20+
usermod -aG docker root   # 避免 sudo
```

**幂等**: 已装则 `dnf install` 报 "already installed" 不算错。

### 2.3 Stage 2 — Install deps + clone

```bash
dnf install -y git python3 rsync
# Rocky 8 默认开启 SELinux + firewalld,容器互访不需要,关掉省心
setenforce 0
sed -i 's/^SELINUX=enforcing/SELINUX=permissive/' /etc/selinux/config
systemctl stop firewalld && systemctl disable firewalld

# clone (--depth 1 省时间)
git clone --depth 1 https://github.com/<org>/0401-lumen-opc.git /opt/aiopc
# 或 SSH 协议看仓库配置
```

### 2.4 Stage 3 — 起基建(5 容器)

```bash
cd /opt/aiopc/springboot3/deploy
docker compose up -d nacos1 mysql redis rabbitmq minio qdrant
# 等 MySQL initdb
for i in {1..60}; do
  docker exec aiopc-mysql mysql -uroot -p"$MYSQL_PWD" -e "SELECT 1" && break
  sleep 2
done
# 等 Nacos
for i in {1..30}; do
  curl -sf -m 3 http://127.0.0.1:8848/nacos/v1/cs/health && break
  sleep 2
done
```

### 2.5 Stage 4 — 数据迁移 + 推 Nacos + 起业务

```bash
cd /opt/aiopc/springboot3/deploy

# 4.1 解压 migrate 包
tar -xzf /tmp/aiopc-staging/aiopc-migrate-*.tar.gz -C /tmp/aiopc-staging/

# 4.2 先备份当前空库(给 restore 失败时回滚用)
docker exec aiopc-mysql mysqldump -uroot -p"$MYSQL_PWD" --all-databases \
  | gzip > /opt/aiopc-data/backups/pre-restore-$(date +%Y%m%d-%H%M%S).sql.gz

# 4.3 restore dump(失败则脚本退出非 0,保留 pre-restore 备份)
gunzip -c /tmp/aiopc-staging/mysql-dump.sql.gz \
  | docker exec -i aiopc-mysql mysql -uroot -p"$MYSQL_PWD" --default-character-set=utf8mb4

# 4.4 推 Nacos 配置(覆盖现有 dev 配置)
cd nacos && bash import-dev.sh && cd ..

# 4.5 起所有业务容器(首次会 build 镜像,~25-40 min)
docker compose up -d
# 4.6 等 60s 让服务注册 Nacos
sleep 60
```

### 2.6 Stage 5 — Verification

```bash
cd /opt/aiopc/springboot3/deploy
bash scripts/health-check.sh | tee /opt/aiopc-data/health-$(date +%Y%m%d-%H%M%S).log
# 期望末尾: 50/50 PASS
```

若 < 30/50 PASS,bootstrap 退出码 = 4(业务瘫痪)。

---

## 3. Components (交付物)

### 3.1 新增文件 (3 个)

#### `springboot3/deploy/scripts/bootstrap-remote.sh` (~250 行)

```bash
#!/usr/bin/env bash
set -euo pipefail

# 用法:
#   bash bootstrap-remote.sh --stage {0..5} | --from <n> | --status | --abort
#   bash bootstrap-remote.sh --from 3     # 从 Stage 3 续跑
#
# Exit codes:
#   0 = 全成功
#   1 = 前置依赖缺失
#   2 = 数据恢复失败(已回滚)
#   3 = 镜像 build 失败
#   4 = 健康检查 < 30/50 PASS

STAGE="${STAGE:-0}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DATA_ROOT="${DATA_ROOT:-/opt/aiopc-data}"
STAGING="${STAGING:-/tmp/aiopc-staging}"
MYSQL_PWD="$(grep MYSQL_ROOT_PASSWORD ${PROJECT_ROOT}/docker-compose.yml | head -1 | sed -E 's/.*:\s*([^\s]+).*/\1/')"
```

#### `springboot3/deploy/scripts/backup-mysql-local.sh` (~60 行,本机跑)

```bash
#!/usr/bin/env bash
# 在用户本机(Windows git-bash / Mac / Linux 都行)跑
# 导出 MySQL dump + Nacos 配置 → aiopc-migrate-<date>.tar.gz
# 然后 scp 到远程 VM
set -euo pipefail

DATE=$(date +%Y%m%d-%H%M%S)
OUT="/tmp/aiopc-migrate-${DATE}.tar.gz"
STAGE_DIR="$(mktemp -d)"
trap "rm -rf ${STAGE_DIR}" EXIT

# 1. 导出 MySQL dump
docker exec aiopc-mysql mysqldump -uroot -p'Opc@2026!' --all-databases \
  --default-character-set=utf8mb4 --routines --triggers --events \
  | gzip > "${STAGE_DIR}/mysql-dump.sql.gz"

# 2. 打包 Nacos dev 配置
cd "${PROJECT_ROOT}/nacos"
tar -czf "${STAGE_DIR}/nacos-configs.tgz" *.yml

# 3. 打包最终 migrate 包
cd /tmp
tar -czf "${OUT}" -C "${STAGE_DIR}" .
sha256sum "${OUT}" > "${OUT}.sha256"

echo "✓ ${OUT} ($(du -h ${OUT} | cut -f1))"
echo "  scp ${OUT} root@<VM_IP>:/tmp/aiopc-staging/"
```

#### `docs/verification/late/W79-REMOTE-SSH-DEPLOY-VERIFICATION.md`

W79 验证报告模板,记录:服务器配置、容器清单、健康检查输出、smoke test 输出、SSH 转发截图。

### 3.2 更新文件 (2 个)

#### `springboot3/deploy/RECOVERY.md` — 加 §15 "Remote SSH Bootstrap"

引用 `bootstrap-remote.sh`,加一段"在新 VM 上 5 步跑通"的快速指引。

#### `springboot3/deploy/.env.example` — 加 Rocky 8 注释

注释当前 placeholder key 用法,加 `# Rocky 8.10: 配 docker-ce repo 后 dnf install 即可`。

### 3.3 不动的文件

- `docker-compose.yml` — 当前 22 容器 + opc-content 配置已覆盖
- `deploy.sh` — W50 固化,跨平台已验证
- `mysql-initdb.d/` — 16 schema 已覆盖 W74 全部 OPC 服务;若实跑发现缺表再追加 `99-w79-postdeploy.sql`(目前判断不需要)

---

## 4. Error Handling & Recovery

### 4.1 失败场景矩阵

| 失败场景 | 检测点 | 恢复动作 |
|---|---|---|
| Docker 装不上 | `docker compose version` exit≠0 | 提示检查 `/var/log/messages`,失败 3 次退出 1 |
| Rocky 8 docker-ce repo 拉不动(国内云常见) | `dnf install` 报 "Failed to download" | 备选 `mirrors.aliyun.com/docker-ce` 镜像;或换 `--enablerepo` 走 powertools |
| MySQL 60s 内未就绪 | `SELECT 1` 超时 | `docker logs aiopc-mysql` 输出末 30 行,提示检查 `/opt/aiopc-data/mysql/data/` 是否残留非空 |
| Nacos 健康检查失败 | `/nacos/v1/cs/health` 60s 内不返回 UP | `docker logs aiopc-nacos-1`,检查 MySQL 是否在同 stack |
| 镜像 build 失败 | `docker compose build` exit≠0 | 输出末 30 行 maven 错误,**不重试**(避免 mvn 缓存污染),退出 3 |
| MySQL restore 失败 | `mysql ... < dump.sql` exit≠0 | **不覆盖**:从 `pre-restore-*.sql.gz` 回滚,退出 2 |
| 健康检查 < 30/50 PASS | `health-check.sh` PASS 数 < 30 | 输出失败项 + `docker logs`,**不退出**(让运维看全貌,但脚本退出码=4) |
| 网络/磁盘满 | `df /opt/aiopc-data` < 5 GB | 提示 `docker system prune` + 扩容 |

### 4.2 致命错误退出码

| Code | 含义 |
|---|---|
| 0 | 全成功 |
| 1 | 前置依赖缺失(Rocky 8.10 不匹配 / 磁盘不足 / .env 缺失 / migrate 包缺失) |
| 2 | 数据恢复失败(已自动回滚到 pre-restore 备份) |
| 3 | 镜像 build 失败 |
| 4 | 健康检查 < 30/50 PASS |

### 4.3 续跑机制

任意阶段失败可 `bash bootstrap-remote.sh --from <next_stage>` 续跑,不必从头来。Stage 4 的 MySQL restore 是**幂等危险**操作:已 restore 过再跑会重复写,bootstrap 会先 `mysql -e "SHOW TABLES"` 校验 `opc_*` 表存在数 ≥ 10 张才跳过 restore。

### 4.4 Abort

`bash bootstrap-remote.sh --abort` 停所有 `aiopc-*` 容器 + 删 `/opt/aiopc-data/{mysql,redis,minio,qdrant}/` 之外的所有容器卷(不删 data/ ,留给用户决策)。

---

## 5. Verification

### 5.1 自动化验证(脚本必跑)

```bash
bash /opt/aiopc/springboot3/deploy/scripts/health-check.sh
# 期望末尾: 50/50 PASS (含 W49/W50/W51/W72/W74 累计探针)
```

输出落到 `/opt/aiopc-data/health-<date>.log` 留痕。

### 5.2 Smoke test(脚本尾部跑,5 项必过)

```bash
# 1. 登录拿 token
TOKEN=$(curl -s -X POST http://127.0.0.1:8080/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r .data.access_token)

# 2. 关键业务路径(每项断言 R200 + body.code=200)
curl -fs "http://127.0.0.1:8080/opc/erp/product/list?companyId=1"     > /dev/null && echo "✓ ERP"
curl -fs "http://127.0.0.1:8080/opc/crm/customer?page=1"               > /dev/null && echo "✓ CRM"
curl -fs "http://127.0.0.1:8080/opc/hr/job/list"                        > /dev/null && echo "✓ HR"
curl -fs "http://127.0.0.1:8080/opc/community/module/list"              > /dev/null && echo "✓ Community"
curl -fs "http://127.0.0.1:8080/opc/notification/inbox/unread-count"   > /dev/null && echo "✓ Notification"

# 3. AI 接口预期失败(因 key 是 placeholder),但 HTTP 不能 5xx 整个服务挂
curl -i "http://127.0.0.1:8080/opc/ai-core/chat" 2>&1 | grep -E "HTTP|401|500"
# 期望: HTTP/1.1 401 或 500,日志显式 "API key invalid",非 503(全栈挂)
```

### 5.3 手动验证(给运维写进 VERIFICATION 报告)

- 打开 `http://localhost:8079` (经 `ssh -L 8079:127.0.0.1:8079`) → 登录页可见
- 验证码弹窗正常 (math 类型)
- 登录 admin/admin123 → 主页加载
- 每个一级菜单点击 200(目测无白屏)
- Nacos 控制台 `http://localhost:8848/nacos` (经 `ssh -L 8848:127.0.0.1:8848`) → 看到 ≥ 19 个注册服务

### 5.4 VERIFICATION 报告产出

`docs/verification/late/W79-REMOTE-SSH-DEPLOY-VERIFICATION.md`,含:

- 服务器配置(OS/vCPU/RAM/Disk 截图)
- `docker compose ps` 容器清单
- `health-check.sh` 输出
- 5 项 smoke test 输出
- SSH 端口转发登录截图

---

## 6. Out of Scope (本次不做)

| 项 | 原因 |
|---|---|
| HTTPS / 域名 / Let's Encrypt | 用户明确选 SSH 转发 |
| 高可用 (Nacos 3 节点 / MySQL 主从) | 单 VM demo 阶段不需要 |
| k8s 部署 | 仓库 Helm chart 已有,但用户单 VM 不需要 |
| LLM 真 key 接入 | 用户先用 placeholder,后续单独任务 |
| ES / Prometheus / Grafana / SkyWalking | 资源紧张,不在本批 |
| opc-insight 服务 | 依赖 Qdrant + ES 较多,单 demo 用不到 |
| 监控告警 / 日志聚合 | 同上,后续单独任务 |
| 自动扩容 / 负载均衡 | 单 VM 无此需求 |

---

## 7. Timeline

| 阶段 | 耗时 |
|---|---|
| 写 bootstrap-remote.sh | 2-3 h |
| 写 backup-mysql-local.sh | 30 min |
| 更新 RECOVERY.md §15 + .env.example 注释 | 30 min |
| 在本机 dry-run bootstrap(若条件允许) | 1 h |
| 在 VM 实跑 bootstrap(Stage 0→5) | 50 min(首次 build 30 min + 数据迁移 + 验证) |
| 写 W79 VERIFICATION 报告 | 30 min |
| **合计** | **~5-6 h** |

---

## 8. Risks

| Risk | 概率 | Mitigation |
|---|---|---|
| Rocky 8 docker-ce repo 在国内云拉不动 | 中 | 备选阿里云镜像 `mirrors.aliyun.com/docker-ce` |
| 16 镜像 build 在 2 vCPU 超时 | 中 | build 用 `--no-cache` + 并行 `docker compose build --parallel` |
| MySQL dump 含本地 hostname/IP,容器内不通 | 低 | restore 后跑 `UPDATE sys_config SET config_value='mysql' WHERE config_key IN ('sys.account.captchaEnabled', ...)` 修配置 |
| Nacos namespace id 漂移 | 低 | import-dev.sh 用 `namespaceId=opc-dev` 强制写 |
| LLM placeholder 让 ai-core 启动失败 | 低 | ai-core 启动只校验 `@Value("${spring.ai.openai.api-key}")` 非空(已写 placeholder `sk-placeholder-deepseek`),真实 key 在运行时才校验 |
| 健康检查 < 30 因为某 OPC 缺 image build 错 | 中 | 阶段 4 后先 `docker compose ps --services \| xargs -I{} docker compose up -d {}` 单跑确认 |

---

## 9. Open Questions

无 — 5 节设计已通过用户确认(2026-09-22)。
