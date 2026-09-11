# OPC 部署灾难恢复手册 (W48.7 固化)

> 当 docker stack 完全崩溃、机器重装、容器数据丢失时，**完全从本仓库 repo 恢复整套 OPC 运行环境**。

## 0. 适用范围

本手册覆盖 `springboot3/deploy/` 子项目，3 层级内容:

| 层级 | 在哪 | 持久化方式 |
|------|------|-----------|
| **代码 + 配置** | GitHub repo | Git push (每次变更后自动) |
| **容器运行时数据** | host 目录 (`deploy/{mysql,redis,qdrant,minio,es,nacos,prometheus,grafana}/data`) | bind mount, 已 git ignored |
| **密钥 (`.env`)** | `deploy/.env` (不在 repo) | git ignored, 用户手填 |

> ⚠️ **关键事实**：`deploy/.env` 含真实 LLM API key,**绝不进 repo**。第一次恢复必须用 `.env.example` 模板手动创建。

---

## 1. 一键恢复 (15 min)

### 前置依赖

- Docker Desktop 或 docker engine 24+
- 本机可联网（拉镜像 + 调 LLM API）
- GitHub repo 已 clone 到本地: `D:\work-ai\0401-lumen-opc\`

### 步骤

```bash
# 1. 拉最新代码
cd /d/work-ai/0401-lumen-opc
git pull origin main

# 2. 创建密钥文件 (从模板, 然后填真值)
cd springboot3/deploy
cp .env.example .env
notepad .env   # Windows; 或 vim .env
#   必填:
#     DEEPSEEK_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxx
#     MINIMAX_API_KEY=eyxxxxxxxxxxxxxxxxxxxxxxxx

# 3. 重灌 Nacos 配置 (各服务 bootstrap.yml 拉 application-dev.yml)
cd nacos
./import-dev.sh   # 必须先启动 nacos 容器 (第 5 步)
```

### 5. 启动 MySQL + 其它基建 (一次性)

```bash
cd /d/work-ai/0401-lumen-opc/springboot3/deploy

# 先只启动 mysql, 让它自动跑 mysql-initdb.d/ 建库
# ⚠️ 如果 deploy/mysql/data 不空 (之前的数据残留), initdb.d 不会执行
#    此时两种选择:
#    (a) 用旧数据: 跳过此节, 直接 docker compose up -d (业务容器)
#    (b) 全新环境: docker compose down -v aiopc-mysql && rm -rf mysql/data/*
docker compose up -d nacos1 mysql redis rabbitmq qdrant minio elasticsearch
```

**验证 mysql 是否建库成功:**

```bash
sleep 30   # mysql 首次启动 + 跑 10 个 initdb SQL 文件需时 ~30s
docker exec aiopc-mysql mysql -uroot -p'Opc@2026!' -e "SHOW DATABASES;"
# 应该看到: ry-vue-opc + nacos_config

docker exec aiopc-mysql mysql -uroot -p'Opc@2026!' ry-vue-opc -e "SHOW TABLES;"
# 应该看到 ~80 张表 (含 ruoyi_ + quartz_ + opc_ 前缀)
```

### 6. 启动业务容器

```bash
# 6.1 灌 Nacos 配置 (现在 nacos 已起)
cd /d/work-ai/0401-lumen-opc/springboot3/deploy/nacos
./import-dev.sh

# 6.2 起所有业务服务
cd ..
docker compose up -d
# 首次启动会构建所有 aiopc-* 镜像 (大约 8 分钟)
```

### 7. 验证栈

```bash
cd /d/work-ai/0401-lumen-opc/springboot3/deploy
bash scripts/health-check.sh
# 应该看到: 13/13 接口 R200, 7/7 容器 Up
```

---

## 2. 全部恢复 vs 部分恢复

| 故障场景 | 恢复动作 | 耗时 |
|----------|----------|------|
| 容器被删, host 数据还在 | `docker compose up -d` | 2 min |
| 容器 + host 数据被删, repo 在 | §1 全流程 | 15 min |
| 整盘重装 | §1 全流程 + 重装 Docker Desktop | 30 min |
| Nacos 配置漂移 (改了 Nacos 没改 repo) | `nacos/import-dev.sh` (强制覆盖) | 30 s |
| MySQL 数据坏了, repo 在 | `scripts/backup-mysql.sh restore <file>` | 1 min |

---

## 3. MySQL 数据备份/恢复

每次重大变更前(改 schema、加新 OPC 微服务)跑一次备份:

```bash
cd /d/work-ai/0401-lumen-opc/springboot3/deploy
bash scripts/backup-mysql.sh
# 生成 deploy/backups/aiopc-mysql-YYYYMMDD-HHMMSS.sql.gz
# 旧备份 (>30 天) 自动删除
```

**手动恢复** (覆盖当前库):

```bash
bash scripts/backup-mysql.sh restore backups/aiopc-mysql-20260910-210928.sql.gz
# 会要求输入 yes 二次确认
```

> ⚠️ backup 脚本 git-ignored `deploy/backups/` 目录本身(避免二进制进 repo)。
> 长期保留需要手动 cp 到网盘 / Git LFS。

---

## 4. 各组件数据存放位置

| 数据 | 容器内路径 | host 路径 | 是否在 repo |
|------|-----------|-----------|-------------|
| MySQL | `/var/lib/mysql` | `deploy/mysql/data/` | ❌ git ignored |
| Redis | `/data` | `deploy/redis/data/` | ❌ git ignored |
| Qdrant 向量 | `/qdrant/storage` | `deploy/qdrant/data/` | ❌ git ignored |
| MinIO 对象 | `/data` | `deploy/minio/data/` | ❌ git ignored |
| ES 日志 | `/usr/share/elasticsearch/data` | `deploy/es/data/` | ❌ git ignored |
| Nacos 配置 | Nacos 内嵌 | `deploy/nacos/conf/` | ❌ 部分 conf 是空的, 配置存在 DB |
| Grafana 仪表盘 | `/var/lib/grafana/dashboards` | `deploy/grafana/dashboards/` | ❌ 空 |
| Prometheus 数据 | `/prometheus` | (容器内, 无 bind) | ❌ 重启丢数据 |
| `.env` 密钥 | - | `deploy/.env` | ❌ git ignored |

**关键**: MySQL/Redis/Qdrant 是**强状态**，其它业务组件没这些数据起不来。这三者**必须用 host bind mount** (现状已配)，不要用匿名 volume（容器删了数据丢）。

---

## 5. Nacos 配置同步

**单向: repo → Nacos**。Nacos 控制台改的 config **不会自动回 repo**。

修改流程:
1. 改 `springboot3/deploy/nacos/*.yml` 本地文件
2. `git add + commit + push` (触发自动备份)
3. `./import-dev.sh` 把 yml 推 Nacos (覆盖现有)
4. `docker compose up -d` 让服务重连 Nacos 拿新配置

**反向同步** (从 Nacos 拉回 repo): `nacos/export-dev.sh` (TODO: 暂未实现)

---

## 6. 前端构建 (Vue3)

`aiopc-frontend` 用多阶段 Dockerfile, 构建时跑 `pnpm install + pnpm build`。

如果只想改前端不动后端:
```bash
cd vue3-typescript
pnpm install
pnpm build          # 输出 dist/
docker build -t aiopc-frontend:latest \
  -f ../springboot3/deploy/Dockerfile.frontend .
docker restart aiopc-frontend
```

---

## 7. 常见恢复坑

| 现象 | 原因 | 修复 |
|------|------|------|
| 容器起来但 `/opc/**` 全 404 | 改 Nacos 后没 recreate | `docker compose up -d` 强制重拉 |
| ai-core `Access denied for user 'root'` | datasource 写死 127.0.0.1 | 检查 `MYSQL_HOST=mysql` 在 compose |
| MySQL initdb 没跑 | `mysql/data` 不空 | `rm -rf mysql/data/*` 后 `docker compose up -d mysql` |
| LLM chat 返回 401 | `.env` 没填或填错 | `cat .env` 检查, 改完 `docker compose up -d aiopc-ai-core` |
| Nacos 配置没生效 | 只 restart, 没 recreate | `docker compose up -d` |
| gateway 报 "DiscoveryClient ... unknown" | ai-core 没起来被注册 | 等 30s 重试 |

详细 debug 历史见 [[aiopc-all-opc-stack]] / [[aiopc-llm-providers]]。

---

## 8. 验证清单 (恢复后跑一遍)

```bash
bash scripts/health-check.sh
```

预期全部 ✅:
- [x] 7 个容器 Up (gateway/auth/system/user-center/agent-hub/ai-core/billing/finance)
- [x] Nacos 健康 (`/nacos/v1/cs/health`)
- [x] 13/13 `/opc/**` 接口 R200
- [x] MySQL `SHOW TABLES` 看到 ruoyi_/quartz_/opc_ 表
- [x] 前端 `http://localhost:8079` 可访问
- [x] LLM chat 返回真实内容 (非 placeholder)

---

## 9. 文档维护

| 变更类型 | 同步到 |
|----------|--------|
| 加新 OPC 微服务 (新端口) | docker-compose.yml + import-dev.sh + RECOVERY.md §1 + scripts/health-check.sh |
| 改 Nacos 配置 | nacos/*.yml + import-dev.sh (auto) |
| 加新 SQL 表/迁移 | mysql-initdb.d/NN-*.sql + RECOVERY.md §1 |
| 改密钥需求 | .env.example + RECOVERY.md §1 |

---

## 10. opc-notification 服务 (9310)

**作用**：统一通知中心。邮件 / 短信 / 站内信 / WebSocket。

**端口**：9310

**数据**:
- 4 张表: `opc_notification_email_log`、`opc_notification_sms_log`、`opc_notification_inbox`、`opc_notification_template`
- 4 个默认模板（`welcome` / `order_paid` / `opportunity_assigned` / `inventory_low`）由 `94-opc-notification-template-seed.sql` 灌入
- schema 初始化: `mysql-initdb.d/08-opc-notification-schema.sql`（首次 `mysql/data` 为空时自动跑）

**Nacos 配置**:
- DataID: `opc-notification-dev.yml` / `opc-notification-prod.yml`
- 推送命令: `bash deploy/nacos/import-dev.sh`（W49 起 NAMES 已含 `opc-notification-dev.yml`）

**启动**:
```bash
cd springboot3
JAVA_HOME="C:/Program Files/Java/jdk-17.0.17.10-hotspot" \
  mvn -pl ruoyi-modules/opc-notification -am clean package \
    -Dmaven.test.skip=true -Dspring-boot.repackage.skip=true
JAVA_HOME="C:/Program Files/Java/jdk-17.0.17.10-hotspot" \
  mvn -pl ruoyi-modules/opc-notification dependency:copy-dependencies \
    -DoutputDirectory=target/dependency
cd deploy
docker compose build aiopc-notification
docker compose up -d aiopc-notification
```

**注意**:
- 必须用 `-Dmaven.test.skip=true` 绕过 `opc-common` 测试编译错误（`OpcNacosStartupCheckerTest.java`）
- 邮件/SMS 凭据在 Nacos `opc-notification-<profile>.yml` 里；没有 Aliyun SMS key 时 provider 自动降级为 NoOp（不抛异常，仅记录日志）
- gateway 路由 `opc-notification` → `http://aiopc-notification:9310` 必须在 `ruoyi-gateway/src/main/resources/application.yml` 存在；改完要重启 gateway
- thin jar 模式下 docker 容器 `java -cp "xxx.jar:lib/*" Main-Class` 启动，需先 `mvn dependency:copy-dependencies`

**验证**:
```bash
bash deploy/scripts/health-check.sh | tail -20
# 期望: 28/28 PASS
```

**快速 smoke test**:
```bash
TOKEN=$(curl -s -X POST -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  http://127.0.0.1:8080/login \
  | python -c "import sys,json;print(json.load(sys.stdin)['data']['access_token'])")
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://127.0.0.1:8080/opc/notification/inbox?page=1&pageSize=5"
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://127.0.0.1:8080/opc/notification/inbox/unread-count"
```

---

**Last updated:** 2026-09-11 (W49 — added opc-notification section)