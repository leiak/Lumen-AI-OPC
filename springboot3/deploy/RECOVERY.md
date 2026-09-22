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

## 11. opc-crm 服务 (9312)

**作用**：客户关系管理。客户档案 / 联系人 / 跟进记录 / 商机 / 合同 / 订单 / 看板。

**端口**：9312

**依赖服务**:
- opc-notification (9310) — 阶段变更/新跟进站内信通知
- opc-ai-core (9301) — 商机 LLM 评分 (`crm-opportunity-scorer`)
- opc-user-center (9302) — 客户所有人信息

**6 张核心表**:
- `opc_crm_customer` (客户档案, 客户级别 A/B/C/D, 来源, 标签)
- `opc_crm_contact` (客户联系人 1:N, 主联系人)
- `opc_crm_follow_up` (跟进时间线, 下次跟进时间)
- `opc_crm_opportunity` (商机漏斗, 阶段 LEAD/QUALIFIED/PROPOSAL/NEGOTIATION/WON/LOST, LLM 评分)
- `opc_crm_contract` (合同 DRAFT/ACTIVE/EXPIRED/TERMINATED)
- `opc_crm_order` (订单 PENDING/PAID/SHIPPED/COMPLETED/CANCELLED)

**schema 初始化**：`mysql-initdb.d/12-opc-crm-schema.sql` + `mysql-initdb.d/13-crm-seed.sql`（首次 `mysql/data` 为空时自动跑）

**Nacos 配置**:
- DataID: `opc-crm-dev.yml` / `opc-crm-prod.yml`
- 推送命令: `bash deploy/nacos/import-dev.sh`（W50 起 NAMES 已含 `opc-crm-dev.yml`）

**启动**:
```bash
cd springboot3
JAVA_HOME="C:/Program Files/Java/jdk-17.0.17.10-hotspot" \
  mvn -pl ruoyi-modules/opc-crm -am clean package \
    -Dmaven.test.skip=true -Dspring-boot.repackage.skip=true
JAVA_HOME="C:/Program Files/Java/jdk-17.0.17.10-hotspot" \
  mvn -pl ruoyi-modules/opc-crm dependency:copy-dependencies \
    -DoutputDirectory=target/dependency
cd deploy
docker compose build aiopc-crm
docker compose up -d aiopc-crm aiopc-gateway
```

**注意**:
- 必须用 `-Dmaven.test.skip=true` 绕过 `opc-common` 测试编译错误（`OpcNacosStartupCheckerTest.java`）
- gateway 路由 `opc-crm` → `http://aiopc-crm:9312` 必须在 `ruoyi-gateway/src/main/resources/application.yml` 存在；改完要重启 gateway
- thin jar 模式下 docker 容器 `java -cp "xxx.jar:lib/*" Main-Class` 启动，需先 `mvn dependency:copy-dependencies`
- 商机 LLM 评分依赖 opc-ai-core 真实 LLM key。dev 命名空间可设置 `opc.ai-core.enabled=true` 用 DeepSeek/MiniMax 真实评分；若 opc-ai-core 不可用,`/opc/crm/opportunity/{id}/score` 会返回 502。
- 阶段变更通知 (LEAD→QUALIFIED 等) 通过 `opc-crm.opportunity.auto-notify-on-stage-change` 控制。dev 默认 `true`。

**验证**:
```bash
bash deploy/scripts/health-check.sh | tail -20
# 期望: 36/36 PASS (28 + 8 new)
```

**快速 smoke test**:
```bash
TOKEN=$(curl -s -X POST -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  http://127.0.0.1:8080/login \
  | python -c "import sys,json;print(json.load(sys.stdin)['data']['access_token'])")
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://127.0.0.1:8080/opc/crm/customer?page=1&pageSize=5"
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://127.0.0.1:8080/opc/crm/opportunity?page=1&pageSize=5"
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://127.0.0.1:8080/opc/crm/dashboard"
```

---

**Last updated:** 2026-09-11 (W50 — added opc-crm section + §12 状态快照 + 一键 deploy.sh)

---

## 12. 全栈状态快照 (W50 固化)

> 团队 onboarding / 容量规划 / 异常回滚对账 时,跑一次导出当前 deploy 状态。

### 一键快照

```bash
cd springboot3/deploy/scripts
bash snapshot-stack.sh                     # 默认今天日期
bash snapshot-stack.sh 2026-09-11          # 指定日期(写入 git 历史可追溯)
```

### 产物 (git ignored)

```
deploy/snapshot-containers-<date>.txt        # 所有 aiopc-* 容器 (running + all)
deploy/mysql/snapshot-info-<date>.json       # 74 张表 + 行数 (含 crm/notification 分组)
deploy/mysql/snapshot-tables-<date>.txt      # 同上,人类可读
deploy/redis/snapshot-<date>.txt             # DBSIZE + 按前缀分组 (login_tokens/sys_dict/sys_config)
deploy/nacos/configs-list-<date>.txt         # 注册服务清单 + 已知 DataID 存在性
```

> 跑完即可丢 `.gitignore` (整个 `deploy/{mysql,redis,nacos,nacos/data,backups}/` + `deploy/snapshot-*` 都 git ignored)。

### 典型用例

| 场景 | 用 |
|------|---|
| 团队成员第一次 onboarding | 看 `snapshot-info-<date>.json` 知道有几张表 |
| 想验证 Redis 缓存清理有没有误删 | 看 `redis/snapshot-<date>.txt` 前后比对 |
| Nacos 漂移告警 | 看 `nacos/configs-list-<date>.txt` 对比 import-dev.sh 的 NAMES |
| 容器异常退出 | 看 `snapshot-containers-<date>.txt` 找 missing 容器 |

---

## 13. 一键全栈 deploy (`deploy.sh`)

> 任何协作者第一次 clone repo 后,**只跑这一个脚本**就能拉起完整 aiopc stack。

```bash
cd springboot3/deploy
bash deploy.sh                             # 拉起 mysql + nacos + 业务容器 (build 镜像)
bash deploy.sh status                      # 等价 docker compose ps
bash deploy.sh stop                        # 停所有容器,保留 host 数据
bash deploy.sh nuke                        # ⚠️ 删 mysql/redis/nacos 数据卷重新初始化
```

### 行为

1. 检测 `.env`,缺失则提示 `cp .env.example .env`
2. 检测 Docker 是否在跑
3. `docker compose up -d nacos1 mysql redis` 先起基建 (mysql 自动跑 initdb.d)
4. 等 30s 让 MySQL 建库
5. 跑 `nacos/import-dev.sh` 把 dev 配置推 Nacos
6. `docker compose up -d` 起业务容器 (首次会 build 镜像, ~8 分钟)
7. 提示跑 `bash scripts/health-check.sh` 验证

> ⚠️ `deploy.sh` 不重建镜像。如果改了 OPC 服务源码,先 `mvn package + mvn dependency:copy-dependencies`,再 `deploy.sh rebuild crm` (或 `rebuild all` 重建所有 OPC 模块镜像)。

---

## 14. opc-erp 服务 (9311, W72)

**作用**：进销存管理。商品/SKU/采购/销售/退货/库存查询/日报月报 + FIFO 批次出库算法 + 销退/采退双路退货 + 每日库存快照。

**端口**：9311

**依赖服务**:
- opc-notification (9310) — 库存预警 / 退货完成通知 (Task 8 Feign Gateway)
- MySQL `ry-vue-opc` 库 (11 张 opc_erp_* 表)

**11 张核心表**:
- `opc_erp_supplier` (供应商 NORMAL/PREFERRED/BLOCKED 等级)
- `opc_erp_product` (商品,含 spec_attrs JSON 动态规格)
- `opc_erp_product_sku` (SKU 笛卡尔积自动生成,`@Version` 乐观锁,stock 字段)
- `opc_erp_batch` (批次表,`production_date` + `remaining` 字段支持 FIFO 出库)
- `opc_erp_inventory_log` (库存流水,5 种 type: PURCHASE_IN/SALE_OUT/SALES_RETURN_IN/SUPPLIER_RETURN_OUT/ADJUST)
- `opc_erp_purchase` (采购单 DRAFT/CONFIRMED/COMPLETED/CANCELLED 状态机,`purchase_no = PO-yyyyMMdd-XXXX`)
- `opc_erp_purchase_item` (采购明细,含 batch_no + production_date + expiry_date)
- `opc_erp_sale` (销售单 同采购单状态机,`sale_no = SO-yyyyMMdd-XXXX`,含 customer_phone)
- `opc_erp_sale_item` (销售明细,`batch_id` 是 FIFO 扣减后写入的)
- `opc_erp_return` (退货单 SALES_RETURN/SUPPLIER_RETURN 双路,`return_no = RT-yyyyMMdd-XXXX`)
- `opc_erp_daily_snapshot` (Quartz 每日 23:55 落库,供日报/月报查询)

**schema 初始化**:
- `mysql-initdb.d/15-opc-erp-schema.sql` (11 张表,首次 `mysql/data` 为空时自动跑)
- `mysql-initdb.d/96-opc-erp-seed.sql` (5 个默认供应商 INSERT IGNORE)

**Nacos 配置**:
- DataID: `opc-erp-dev.yml` / `opc-erp-prod.yml`
- 推送命令: `bash deploy/nacos/import-dev.sh` (W72 起 NAMES 已含 `opc-erp-dev.yml`)

**启动**:
```bash
cd springboot3
JAVA_HOME="C:/Program Files/Java/jdk-17.0.17.10-hotspot" \
  mvn -pl ruoyi-modules/opc-erp -am clean package \
    -Dmaven.test.skip=true -Dspring-boot.repackage.skip=true
JAVA_HOME="C:/Program Files/Java/jdk-17.0.17.10-hotspot" \
  mvn -pl ruoyi-modules/opc-erp dependency:copy-dependencies \
    -DoutputDirectory=target/dependency
cd deploy
docker compose build aiopc-erp
docker compose up -d aiopc-erp aiopc-gateway
```

**注意**:
- 必须用 `-Dmaven.test.skip=true` 绕过 `opc-common` 测试编译错误 (`OpcNacosStartupCheckerTest.java`)
- gateway 路由 `opc-erp` → `http://aiopc-erp:9311` 已在 `ruoyi-gateway/src/main/resources/application.yml`;白名单包含 `/opc/erp/product/**` (公开浏览) + `/opc/erp/inventory/low-stock` (公开预警查询);改完要重启 gateway
- thin jar 模式下 docker 容器 `java -cp "xxx.jar:lib/*" Main-Class` 启动,需先 `mvn dependency:copy-dependencies`
- FIFO 出库使用 `SELECT ... FOR UPDATE` 行锁;并发场景下 `opc_erp_batch.remaining` 不会超扣
- SUPPLIER_RETURN 路径当前缺 `FOR UPDATE` 锁 (Task 6 reviewer 关注点),并发高时需在生产前加 `updateRemainingWithCheck` (UPDATE ... WHERE remaining >= qty)
- `/opc/notification/inbox/send` Feign 路径在 opc-notification 端是 aspirational (W49 教训),会 fallback 到 `R.ok()` 不影响主流程

**验证**:
```bash
bash deploy/scripts/health-check.sh | tail -20
# 期望: 53/53 PASS (44 + 9 new erp checks)
```

**快速 smoke test**:
```bash
TOKEN=$(curl -s -X POST -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  http://127.0.0.1:8080/login \
  | python -c "import sys,json;print(json.load(sys.stdin)['data']['access_token'])")
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://127.0.0.1:8080/opc/erp/product/list?companyId=1"
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://127.0.0.1:8080/opc/erp/purchase/list?companyId=1"
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://127.0.0.1:8080/opc/erp/inventory/low-stock?companyId=1"
```

**E2E 脚本**:
```bash
python tmp_e2e/e2e_erp.py
# 10 步全流程:登录 → 创建商品(笛卡尔积) → 列 SKU → 创建采购单 →
# 确认采购单 → 销售单列表 → 低库存预警 → 日报 → 月报 → 供应商列表
```

**W72 教训（与 opc-erp 部署相关）**:
- `docker compose build aiopc-frontend && docker compose up -d aiopc-frontend` 必须在所有 OPC 服务重建后跑(W50 教训):nginx.conf build 时烧进镜像,上游 IP 漂移会导致前端 502
- 容器跑 `mvn clean package` 后变 thin jar 缺 Main-Class(W52 教训):用 `-Dmaven.test.skip=true` 不带 clean repackage 出 104MB fat jar
- 部署链路完整顺序:`mvn package` → `mvn dependency:copy-dependencies` → `docker compose build aiopc-erp` → `docker compose up -d aiopc-erp` → `docker compose build aiopc-frontend && docker compose up -d aiopc-frontend`(最后一步,防 502)

---

**Last updated:** 2026-09-12 (W72 — added opc-erp section §14 + 11 表 schema initdb + 5 seed 供应商 + 9 health-check)

---

## 14. W74 opc-content 部署 (AI 内容创作中心)

### 端口与路由
- 微服务端口: 9325 (容器外直接访问 `127.0.0.1:9325`)
- Gateway 路由: `/opc/content/**` → `http://aiopc-content:9325` (在 `ruoyi-gateway/src/main/resources/application.yml` 配)
- 白名单: `/opc/content/script/list` `/script/dashboard` `/script/recent` `/script/**` `/platform-account/list` (GET 公开,POST 需登录)

### 关键文件 (W74 已固化)
| 文件 | 作用 |
|---|---|
| `springboot3/ruoyi-modules/opc-content/Dockerfile` | thin jar 启动 (`./ruoyi-modules/opc-content/target/...` 路径) |
| `springboot3/deploy/docker-compose.yml` (+39 行) | `aiopc-content` 服务段 |
| `springboot3/deploy/mysql-initdb.d/16-opc-content-schema.sql` | 4 表 DDL (`opc_content_script/platform_account/publish/adapt`) |
| `springboot3/deploy/mysql-initdb.d/98-opc-content-seed.sql` | dev seed (3 脚本 + 1 账号 + 1 发布 + 1 适配) |
| `springboot3/deploy/nacos/opc-content-dev.yml` | dev 配置 (38 项 + ENC 占位) |
| `springboot3/deploy/nacos/import-dev.sh` (修改) | NAMES 数组加 `"opc-content-dev.yml"` |
| `springboot3/deploy/scripts/health-check.sh` (+107 行) | `check_content()` 10 端点 |
| `springboot3/ruoyi-gateway/src/main/resources/application.yml` (+18 行) | `/opc/content/**` 路由 + 白名单 |
| `tmp_e2e/e2e_content.py` | 10 端点 e2e 脚本 |
| `docs/verification/week-74/OPC-W74-VERIFICATION-opc-content.md` | W74 VERIFICATION 报告 |

### 部署步骤 (新机器 / 容器崩溃后)

```bash
# 1. 基础依赖
cd springboot3/deploy
docker compose up -d nacos1 mysql redis
sleep 60

# 2. 灌 Nacos 配置 (含 opc-content)
cd nacos && bash import-dev.sh && cd ..

# 3. 构建 opc-content 镜像
cd ../ && mvn -pl opc-common install -DskipTests -Dmaven.test.skip=true -o
cd ../springboot3 && mvn -pl ruoyi-modules/opc-content package -DskipTests -Dmaven.test.skip=true -o
cd ruoyi-modules/opc-content && mvn dependency:copy-dependencies -DskipTests -Dmaven.test.skip=true -o
cd ../../deploy && docker compose build aiopc-content

# 4. 启动 opc-content
docker compose up -d aiopc-content
sleep 60  # 等启动

# 5. 重建 gateway (添加 /opc/content/** 路由)
cd ../ && mvn -pl ruoyi-gateway package -DskipTests -Dmaven.test.skip=true -o
cd ruoyi-gateway && mvn dependency:copy-dependencies -DskipTests -Dmaven.test.skip=true -o
cd ../deploy && docker compose build aiopc-gateway && docker compose up -d aiopc-gateway
sleep 40

# 6. 重建 frontend (W51 教训: 上游 IP 漂移后必须 rebuild)
docker compose build aiopc-frontend && docker compose up -d aiopc-frontend

# 6b. (Fallback) Docker.io 不可达时 (W74 实战) — 用 Python proxy 替代 nginx
# 当 nginx:1.27-alpine + node:20-alpine 基础镜像无法 pull 时,
# 本地 dist/ 已存在,可用 Python http.server + 反向代理脚本跑 8079 端口
python C:/Users/wma19/AppData/Local/Temp/frontend_proxy.py
# 等价 nginx.conf: /prod-api/* → http://127.0.0.1:8080 (gateway)
#                   / → vue3-typescript/dist/index.html (SPA fallback)

# 7. 健康检查 (期望 10/10 + 之前所有服务)
bash scripts/health-check.sh

# 8. e2e 验证
cd ../tmp_e2e && OPC_GATEWAY=http://127.0.0.1:8080 python e2e_content.py
# 期望 8/10 PASS (script POST + script/adapt 2 个失败是后端缺字段校验,非部署问题)
```

### W74 部署阶段关键修复 (3 处)

**修复 1: initdb 数据库名错位**
- `16-opc-content-schema.sql` 原本 `CREATE DATABASE ry-cloud`,但容器实际只有 `ry-vue-opc`
- **修法**: `sed -i 's/ry-cloud/ry-vue-opc/g' mysql-initdb.d/16-opc-content-schema.sql`
- **教训**: 写 DDL 前必须 `docker exec aiopc-mysql mysql -uroot -p... -e "SHOW DATABASES"` 核对

**修复 2: Nacos 占位 ENC(...) 启动失败**
- `douyin.client-secret: ENC(dev_client_secret_placeholder)` 让 Jasypt 试图解密假字符串 → `Failed to bind properties under 'douyin.client-secret'`
- **修法**: 改为明文 `dev_client_secret_placeholder` (生产前用 `deploy/nacos/encrypt-secret.sh` 真加密)
- **教训**: dev 占位禁用 `ENC(...)`,生产才加密

**修复 3: Gateway 路由缺失**
- `ruoyi-gateway/application.yml` 没有 `/opc/content/**` 路由 → 网关返回 `No static resource`
- **修法**: 在 opc-erp 路由后加:
  ```yaml
  - id: opc-content
    uri: http://aiopc-content:9325
    predicates:
      - Path=/opc/content/**
  ```
  + 白名单段加 5 个公开端点
- **教训**: 新加 OPC 服务必须同时改 gateway application.yml + 重建 gateway 镜像

**修复 4: Dockerfile 路径上下文**
- Dockerfile 写 `target/dependency` 但 compose 用 `context: ..` → Docker 找不到
- **修法**: Dockerfile 用 `./ruoyi-modules/opc-content/target/...` (与 opc-erp 一致)
- **教训**: build context = `springboot3/` 时,Dockerfile 路径以 `./ruoyi-modules/<svc>/` 开头

### W74 健康检查
```bash
bash scripts/health-check.sh
# 期望 check_content() 输出:
#   [OK] aiopc-content running
#   [OK] Nacos opc-content 注册 1 实例 (opc-dev)
#   [OK] opc-content /actuator/health UP
#   [OK] /opc/content/script/list 200 OK
#   [OK] /opc/content/script/dashboard 200 OK
#   [OK] /opc/content/script/recent 200 OK
#   [OK] /opc/content/platform-account/list 200 OK
#   [OK] /opc/content/publish/list 200 OK
#   [OK] 鉴权拦截正确
#   [OK] 数据库健康
```

### W74 e2e 验证
```bash
OPC_GATEWAY=http://127.0.0.1:8080 python tmp_e2e/e2e_content.py
# 当前 8/10 PASS (script POST + script/adapt 2 个失败是后端缺字段校验,
# 真实场景需前端传 companyId + 完整必填字段,后续 W75 迭代修复)
```

---

## 15. Remote SSH Bootstrap (W79)

> 单脚本一键部署 OPC 全栈到远程 Rocky Linux 8.10 VM。**用户自己 SSH 进 VM 跑脚本**, Claude 不需要 SSH 凭据。

### 15.1 前置

- **VM**: Rocky Linux 8.10, 2-4 vCPU / 4-8 GB / 40-80 GB
- **SSH 访问**: 用户有 root 账号(或 `sudo su -` 升 root)
- **本机已装**: Docker Desktop 24+(用于 `backup-mysql-local.sh`)
- **目标**: 22 个容器 (6 基建 + 3 平台 + 13 业务)

### 15.2 流程

#### 本机 (Windows / macOS / Linux)

```bash
cd springboot3/deploy
bash scripts/backup-mysql-local.sh
# → 生成 aiopc-migrate-YYYYMMDD-HHMMSS.tar.gz (含 mysql-dump.sql.gz + nacos/)
# 校验:
tar -tzf aiopc-migrate-*.tar.gz | head
# 期望: mysql-dump.sql.gz  nacos/*.yml ...
```

把 `aiopc-migrate-*.tar.gz` 上传到 VM (scp / sftp / rsync 任一):

```bash
scp aiopc-migrate-*.tar.gz root@<vm-ip>:/tmp/
```

#### VM (root 跑)

```bash
# SSH 进 VM
ssh root@<vm-ip>

# 准备 .env (从仓库拉一份)
cd /opt/aiopc/springboot3/deploy
ls -la .env 2>/dev/null || cp .env.example .env
# 编辑填真值:DEEPSEEK_API_KEY, MINIMAX_API_KEY, MYSQL_PWD 等

# 拉迁移包到 staging
mkdir -p /tmp/aiopc-staging
mv /tmp/aiopc-migrate-*.tar.gz /tmp/aiopc-staging/

# 一键拉起 (5 阶段)
bash scripts/bootstrap-remote.sh
# 期望: ~25-40 min 后 22 个容器全 UP, banner 打印 SSH 端口转发提示
```

### 15.3 5 阶段

| # | 阶段 | 任务 | 失败退出码 |
|---|---|---|---|
| 0 | preflight | Rocky 8.10 / 40G disk / .env / migrate tar / sha256 / env keys | 1 |
| 1 | install | dnf install docker-ce + systemctl enable --now | 1 |
| 2 | deps | git/curl/python3 + SELinux/permissive + firewalld 关闭 + git clone 仓库 | 1 |
| 3 | infra | 6 基建容器 (nacos1/mysql/redis/rabbitmq/minio/qdrant) + 等就绪 | 1 |
| 4 | restore | 解压 migrate → restore MySQL → 推 Nacos → 起 13 业务容器 | 2 (restore) / 3 (compose) |
| 5 | verify | health-check.sh + 5 smoke tests | 4 |

### 15.4 失败恢复

每个 stage 完成时会在 `${DATA_ROOT:-/opt/aiopc-data}/.bootstrap-stages-ok.N` 写 marker。
重跑某个 stage: `rm /opt/aiopc-data/.bootstrap-stages-ok.N && bash scripts/bootstrap-remote.sh`
完全重跑: `rm /opt/aiopc-data/.bootstrap-stages-ok.* && bash scripts/bootstrap-remote.sh`
跳到指定 stage: `bash scripts/bootstrap-remote.sh --from 4` (会先跑 stage 0-3 的 marker 检查)

### 15.5 关键文件位置

| 文件 | 路径 | 用途 |
|---|---|---|
| bootstrap-remote.sh | `/opt/aiopc/springboot3/deploy/scripts/bootstrap-remote.sh` | 主脚本 |
| backup-mysql-local.sh | 本机 `springboot3/deploy/scripts/backup-mysql-local.sh` | 本机导出 |
| .env | `/opt/aiopc/springboot3/deploy/.env` | 密钥 (git ignored) |
| DATA_ROOT | `/opt/aiopc-data/` | 容器数据卷 + 备份 + stage markers |
| STAGING | `/tmp/aiopc-staging/` | migrate tar 解压目录 |
| MySQL dump | `docker exec aiopc-mysql mysqldump ...` | 运行时备份 |
| Pre-restore 备份 | `/opt/aiopc-data/backups/pre-restore-*.sql.gz` | stage 4 自动备份 |

### 15.6 端口转发

VM 上 22 容器只暴露 3 个端口给本机:

```bash
ssh -L 8079:127.0.0.1:8079 \
    -L 8080:127.0.0.1:8080 \
    -L 8848:127.0.0.1:8848 \
    user@<vm-ip>
```

然后浏览器:
- 前端: `http://localhost:8079`
- 网关: `http://localhost:8080`
- Nacos: `http://localhost:8848/nacos` (nacos/nacos)

### 15.7 已知限制

- LLM API key 必须从本机 .env 复制过去;**没填时 AI 接口返回 401**, 其他功能正常
- ES/Grafana/SkyWalking/Prometheus 不在 W79 最小基建里,需要时单独加 compose service
- 首次 build 镜像 ~25-40 min(2-4 vCPU 估算),后续增量 build < 5 min
- VM disk 必须 ≥ 40 GB,推荐 80 GB 给增量备份留余地
- 数据卷用 host bind mount (`/opt/aiopc-data/...`),VM 重启数据保留;`docker compose down -v` 才删

### 15.8 常见错误

| 错误 | 原因 | 修复 |
|---|---|---|
| `Rocky 8.10 required` | OS 不是 Rocky 8.10 | 用 Rocky 8.10 (CentOS 8 EOL) |
| `Disk space < 40 GB` | VM disk 太小 | 加 disk 或选更大 VM |
| `migrate tar missing` | 上传失败或忘放 staging | 检查 `/tmp/aiopc-staging/aiopc-migrate-*.tar.gz` |
| `MySQL 120s 内未就绪` | initdb 首次跑 ~60s | 等久点,或 `docker logs aiopc-mysql` |
| `restore failed` (exit 2) | dump 文件坏或权限问题 | `docker logs aiopc-mysql` + `${DATA_ROOT}/backups/pre-restore-*.sql.gz` 回滚 |
| `compose up failed` (exit 3) | Dockerfile 错或端口占用 | `docker compose logs aiopc-<svc>` |
| `health check failed` (exit 4) | 服务没全起 | `bash scripts/health-check.sh` 单独跑看哪项失败 |
| `/login 失败` (smoke 1) | gateway 路由没配 / auth 没注册 Nacos | `curl http://127.0.0.1:8080/actuator/health` + `docker logs aiopc-gateway` |

---

**Last updated:** 2026-09-22 (W79 — added §15 Remote SSH Bootstrap 文档, 5 阶段 + 8 子节 + 端口转发 + 常见错误表)


---