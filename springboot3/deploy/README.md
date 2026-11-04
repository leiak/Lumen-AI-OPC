# OPC 部署文档 (Deploy README)

> **最后更新**: 2026-11-02
> **覆盖范围**: `docker-compose.yml` 里 **31 个服务**（13 个 OPC 业务 + 4 个 RuoYi 内置
> gateway/auth/system/job + 前端 + 12 个基础设施 + 1 个网络）

## 一键启动 (本地开发 · 推荐)

```bash
cd springboot3/deploy

# 1. 复制 .env (W48.6 模板已含 LLM keys,首次需要复制)
[ -f .env ] || cp .env.example .env

# 2. 启动基础依赖 (mysql / nacos / redis / ...)
docker compose up -d nacos1 mysql redis
sleep 60                                # 等 nacos 起来 (健康检查 60s)

# 3. 导入 Nacos 配置 (10 份: 9 份 dev yml + Sentinel 规则表)
cd nacos && bash import-dev.sh && cd ..

# 4. 启动后端 (gateway/auth/system/job + 13 个 OPC 业务服务)
docker compose up -d aiopc-gateway aiopc-auth aiopc-system aiopc-job \
                   aiopc-ai-core aiopc-user-center aiopc-agent-hub \
                   aiopc-billing aiopc-finance aiopc-notification \
                   aiopc-crm aiopc-community aiopc-hr aiopc-erp \
                   aiopc-content aiopc-insight aiopc-marketplace

# 5. 启动前端 (W51 教训: 上游 IP 漂移后必须显式 rebuild)
docker compose build aiopc-frontend && docker compose up -d aiopc-frontend

# 5b. (可选) Docker.io 不可达 fallback — ⚠️ 那次用的临时脚本不在 repo 里
# W74 当天用过 Windows 临时目录里的 frontend_proxy.py 顶替 nginx,
# 但它没有进仓库 —— 前端唯一可复现的起法还是上面的 docker compose build。

# 6. 健康检查 —— 26 节, 200+ 项断言
bash deploy.sh health          # ← 走这个入口, 见下方「健康检查的 26 节」

# 7. e2e 验证 (W74 新增 opc-content)
cd ../tmp_e2e && OPC_GATEWAY=http://127.0.0.1:8080 python e2e_content.py
```

## 全栈服务清单 (2026-11-02 对过 compose)

| 服务 | 端口 | 命名空间 | 路由前缀 | 说明 |
|------|------|---------|---------|------|
| **基础设施 (12)** |||||
| nacos1 | 8848 | - | - | 注册/配置中心（命名空间 `opc-dev`） |
| mysql | 3306 (容器内) | - | - | 数据库 (容器外 3308) |
| redis | 6379 | - | - | 缓存 |
| rabbitmq | 5672 | - | - | ⚠️ **没有一行代码用它**（见根 README） |
| qdrant | 6333 / 6334 | - | - | 向量库（Agent 长期记忆，http / grpc） |
| minio | 9000 | - | - | 对象存储 |
| elasticsearch | 9200 | - | - | 检索 |
| prometheus | 9090 | - | - | 指标采集（2026-10-29 起真的收到指标） |
| grafana | 3000 | - | - | 看板 |
| sentinel-dashboard | 8718 | - | - | Sentinel 控制台（网关限流规则可视化） |
| skywalking-oap | 11800 / 12800 | - | - | APM 后端（gRPC / HTTP）|
| skywalking-ui | 8081 | - | - | APM 界面 |
| **RuoYi 内置 (4)** |||||
| aiopc-gateway | 8080 | - | 全部路由 | Spring Cloud Gateway + Sentinel 流控（**18 条路由**） |
| aiopc-auth | 9200 → 19200 | - | /login /auth | RuoYi 认证 (19200 外部) |
| aiopc-system | 9201 | - | /system | 用户/角色/菜单/字典 |
| **aiopc-job** | **9203** | opc-dev | **/schedule** | **Quartz 调度器 —— 2026-10-30 才真的部署起来** |
| **OPC 业务 (13)** |||||
| aiopc-ai-core | 9301 | opc-dev | /opc/llm | LLM 网关 (DeepSeek + MiniMax) |
| aiopc-user-center | 9302 | opc-dev | /opc/user /opc/company /opc/invitation | 用户中心 |
| aiopc-agent-hub | 9303 | opc-dev | /opc/agent | Agent 工作流（官方数字员工目录在这里） |
| aiopc-billing | 9304 | opc-dev | /opc/billing | 计费 |
| aiopc-finance | 9305 | opc-dev | /opc/finance | 财务 (凭证/银行流水/税报) |
| aiopc-insight | 9306 | opc-dev | /opc/insight | 数据洞察（日报/驾驶舱/异常预警） |
| aiopc-notification | 9310 | opc-dev | /opc/notification | 通知中心 (REST + WS) |
| aiopc-erp | 9311 | opc-dev | /opc/erp | 进销存 (W72) |
| aiopc-crm | 9312 | opc-dev | /opc/crm | CRM (客户/商机/合同/订单/LLM 打分) |
| aiopc-community | 9316 | opc-dev | /opc/community | 模块社区（模块目录 + 评分 + 安装计数） |
| aiopc-marketplace | 9317 | opc-dev | /opc/marketplace | 开发者集市（上架/安装/评价/订阅） |
| aiopc-hr | 9322 | opc-dev | /opc/hr | 招聘管理 (W51/73) |
| aiopc-content | 9325 | opc-dev | /opc/content | AI 内容创作中心 (W74) |
| **前端** |||||
| aiopc-frontend | 8079 | - | - | Vue 3 + TS (nginx 反向代理) —— **唯一对外开放的端口** |

### Sentinel 控制台

`http://127.0.0.1:8718` —— 账号口令在 `deploy/sentinel-dashboard/application.yml` 的
`auth:` 段（演示环境的公开口令，只绑 127.0.0.1，公网够不着）。

控制台**不连 Nacos**（它的 jar 里没有 Nacos 客户端）。它显示的网关限流规则是
**回头问网关要的**：`GET http://<网关ip>:8719/gateway/getRules`。
所以数据流是

```
Nacos(sentinel-ruoyi-gateway, tenant=opc-dev)
  → 网关的 GatewayRuleManager（内存）
    → 控制台
```

第 24 节健康检查盯的就是这条链的最后一环 —— 控制台显示 0 条规则，
就是网关内存里真的一条都没加载，不是控制台自己的问题。

> 网关的心跳地址**只在启动那一刻解析一次**。控制台比网关晚起时，
> 网关解析不到就整组丢弃，之后永远不再重试 —— 症状是控制台永远「零节点」。
> 这时候要**重启网关**（`docker compose restart aiopc-gateway`），不是重启控制台。

## 关键文件

| 文件 | 作用 | 现状 |
|------|------|---------|
| `docker-compose.yml` | **31 个服务**编排 | ✅ 含 `aiopc-job` / `aiopc-marketplace` / `aiopc-insight` |
| `scripts/health-check.sh` | **26 节 / 200+ 项断言** | ✅ 每次部署后必跑 |
| `deploy.sh` | `rebuild` / `health` / `snapshot` 入口 | ✅ 线上验证走它，不手搓 curl |
| `nacos/import-dev.sh` | 推 10 份配置 + 按 `tenant=` 回读逐行比对 | ✅ 不对就 `[FAIL]` 退出 1 |
| `nacos/sentinel-ruoyi-gateway.json` | 限流规则**真相源**（18 条 ↔ 网关 18 条路由） | ✅ 加路由必须同步加 |
| `nacos/create-namespace.sh` | 补 `tenant_info` 那行「户口本」 | ✅ |
| `mysql-initdb.d/` | 新环境建库建表（**14 个 schema + 9 个 seed**） | ✅ |
| `../sql/migrations/V20261033__opc_scheduler_truth.sql` | 调度器的三处假话 + 隐藏 4 个死外链菜单 | ✅ 已上线 |
| `../docs/superpowers/plans/audit/` | 9 份实施计划的逐条审计 | ✅ |
| `RECOVERY.md` | 灾难恢复 + 各服务 runbook（17 节） | ✅ |

## 健康检查的 26 节

**这是本仓库的核心资产**：每个「这个功能是不是真的在管事」都变成一条会红的断言。
新增功能时按同样方式加一节 —— 写法见 `CLAUDE.md` §二。

| 节 | 断言的是什么 |
|---|---|
| 1 | 核心容器状态 |
| 2 | Nacos 健康 |
| 3 | `/opc/**` 接口验证（需先登录） |
| 4 | 前端 nginx |
| 5 | MySQL 抽查 |
| 6 | opc-notification 健康检查 |
| 7 | opc-crm 健康检查 |
| 8 | opc-hr 健康检查 |
| 9 | opc-erp 健康检查 |
| 10 | opc-content 健康检查 |
| 11 | opc-marketplace 健康检查 |
| 12 | 网关白名单 method 限定（3c 回归护栏） |
| 13 | 租户守卫 + LLM 计量（3b / 3a 回归护栏） |
| 14 | CRM 归属守卫（per-user IDOR 回归护栏） |
| 15 | 端口收敛护栏（业务端口不得外露） |
| 16 | 请求体租户归属 + OAuth state 签名（3b 回归护栏） |
| 17 | LLM 计量链（3c 回归护栏） |
| 18 | 多公司切租户（3d 回归护栏） |
| 19 | 市场页改名 + 数字真实性 + 监控 |
| 20 | 第 2 轮的动作是真的（订阅 + MODULE 装完真的通知社区） |
| 21 | CRM 的 AI 评分是真的（分数和理由同生共死） |
| 22 | Nacos 配置中心是真的在管事 |
| 23 | 记忆系统记事 + Sentinel 限流真的在限 |
| 24 | Sentinel 控制台是真的在管事 |
| 25 | 定时任务是真的在跑（真触发一次 + 落库对账） |
| 26 | 财务数字员工读的是真数据（日报逐笔对表 + 降级链真的开着） |

```bash
bash deploy.sh health            # 全量 (26 节)
HEALTH_ONLY=25 bash deploy.sh health   # 只跑第 25 节
HEALTH_ONLY=26 bash deploy.sh health   # 只跑第 26 节
```

> ⚠️ **必须走 `deploy.sh health`**，不要直接 `bash scripts/health-check.sh` ——
> 后者会被安全分类器判成「绕过既定入口的生产读」而拦掉（`CLAUDE.md` 坑 #10）。

## rebuild 入口

改完代码后重建服务，**统一走这里**（`deploy.sh` 会自己探测 JDK 17、区分 fat jar / 普通 jar）：

```bash
bash deploy.sh rebuild ruoyi-job        # 调度器（fat jar）
bash deploy.sh rebuild gateway          # 网关
bash deploy.sh rebuild auth             # 认证服务
bash deploy.sh rebuild opc-agent-hub    # OPC 业务模块（opc- 前缀必带）
bash deploy.sh rebuild frontend         # 前端
bash deploy.sh rebuild all              # 全部
```

> 共享库（`ruoyi-common-*` / `opc-common` / `opc-ai-core`）改完必须先 `mvn install`
> 把快照推进 `~/.m2`，否则镜像里还是旧包 —— 见 `CLAUDE.md` 坑 #1。

## 关键修复 (W74 部署阶段发现，存档)

> 这一节是 W74 那次上线时的记录，**原样保留**当经验用。
> 之后修过的坑不在这儿 —— 看 `RECOVERY.md`（17 节，按服务/场景分），
> 以及 `CLAUDE.md` §四「踩过的坑」（12 条，是最新的那一份）。

### 1. initdb 数据库名错位
- `16-opc-content-schema.sql` 原本 `CREATE DATABASE ry-cloud`,但容器实际只有 `ry-vue-opc`
- **修复**: 改为 `CREATE DATABASE ry-vue-opc`
- **教训**: 写 DDL 前必须 `docker exec aiopc-mysql mysql -uroot -p... -e "SHOW DATABASES"` 核对 (W50 教训)

### 2. Nacos 占位 ENC(...) 启动失败
- `douyin.client-secret: ENC(dev_client_secret_placeholder)` 让 Jasypt 试图解密假字符串 → 启动抛 `Failed to bind properties under 'douyin.client-secret'`
- **修复**: 占位改为明文 `dev_client_secret_placeholder`
- **教训**: 生产前必须真加密 (用 `deploy/nacos/encrypt-secret.sh` 替换)

### 3. Gateway 路由缺失
- `application.yml` 没有 `/opc/content/**` 路由 → 网关返回 "No static resource"
- **修复**: 加 `id: opc-content` + `uri: http://aiopc-content:9325`
- **教训**: 新加 OPC 服务必须在 gateway application.yml 加路由 + 白名单

### 4. Dockerfile 路径上下文错位
- Dockerfile 写 `target/dependency` 但 compose 用 `context: ..` + `dockerfile: ruoyi-modules/opc-content/Dockerfile` → Docker 找不到 `/target/dependency`
- **修复**: Dockerfile 改用 `./ruoyi-modules/opc-content/target/...` (与 opc-erp 一致)
- **教训**: build context 是 springboot3/ 时,Dockerfile 内路径必须以 `./ruoyi-modules/<svc>/` 开头

### 5. W51 教训再验证 — frontend rebuild
- 改完上游服务 (gateway/opc-content) 后,frontend 镜像缓存的 upstream IP 可能漂移
- **必须执行**: `docker compose build aiopc-frontend && docker compose up -d aiopc-frontend`
- **本次 docker.io 不可达,frontend build 失败**,需后续网络恢复后补做

## K8s 部署（存档 —— 不在本项目范围内）

> ⚠️ **2026-11-02 决定：本项目只有 Docker Compose 一条部署路径，K8s 不做。**
> 本节到「灰度发布」整段是**存档**，保留是因为里面的 manifest 还在仓库里，
> 但**不要照着跑** —— 那条路径没有接调度器、没有验收过、也没有健康检查。
> 演示环境的部署、健康检查、故障恢复全部在 compose 路径上，见本文件上面几节。

### Helm Chart（存档）

```bash
# helm lint
helm lint deploy/helm/opc

# helm template 看 kind 数
# (2026-10-29 实测: dev=31 / staging=48 / prod=50；chart 要求 kubeVersion >=1.24)
helm template test deploy/helm/opc --kube-version 1.29.0 | grep '^kind:' | sort | uniq -c

# 安装到 K8s
helm install opc deploy/helm/opc --namespace opc --create-namespace
```

## 公测部署（存档 —— K8s 旧版，已过时）

```bash
# 1. 创建命名空间
kubectl apply -f k8s/namespace.yaml

# 2. 创建 Secrets（API Key 等敏感信息）
kubectl create secret generic opc-ai-secrets \
  --from-literal=DEEPSEEK_API_KEY=$DEEPSEEK_API_KEY \
  --from-literal=MINIMAX_API_KEY=$MINIMAX_API_KEY \
  -n opc

kubectl create secret generic opc-jasypt-secrets \
  --from-literal=password=OpcEncrypt!2026 \
  -n opc

# 3. 部署 OPC 服务
kubectl apply -f k8s/ -n opc

# 4. 查看部署状态
kubectl get pods -n opc
kubectl get svc -n opc

# 5. 配置 Ingress（域名 + HTTPS）
kubectl apply -f k8s/ingress.yaml
```

## 灰度发布（存档 —— 不在本项目范围内）

```bash
# 1. 给新版本打 tag
docker build -t registry.cn-hangzhou.aliyuncs.com/opc/opc-ai-core:1.0.1 .
docker push registry.cn-hangzhou.aliyuncs.com/opc/opc-ai-core:1.0.1

# 2. 创建灰度 Deployment
kubectl apply -f k8s/opc-ai-core-canary.yaml

# 3. 用 Istio/Envoy 切 5% 流量
kubectl apply -f k8s/virtualservice-canary-5pct.yaml

# 4. 监控关键指标，无异常则全量
# 5. 删除旧版本
kubectl delete deployment opc-ai-core-old
```

## 回滚

```bash
./runbooks/rollback.sh opc-ai-core 1.0.0
```

详见：[runbooks/incident-response.md](runbooks/incident-response.md)

## 一键脚本 (W50 部署固化)

```bash
# 启动全栈
./deploy.sh up

# 健康检查
./deploy.sh health

# 状态快照
./deploy.sh snapshot

# 完全清理
./deploy.sh nuke
```

## 关键文档

| 文档 | 路径 | 说明 |
|------|------|------|
| **CLAUDE.md** | `../../CLAUDE.md` | 给 Claude 看的项目说明 + **12 条踩过的坑**（最新的一份） |
| **RECOVERY** | `RECOVERY.md` | 灾难恢复 + 各服务 runbook（**17 节**） |
| **健康检查** | `scripts/health-check.sh` | **26 节 / 200+ 项断言**（前 25 节 2026-10-30 实测全绿；第 26 节 2026-11-04 新增，待部署后首次实测） |
| **Nacos 配置** | `nacos/README.md` | 命名空间 / `tenant=` 参数陷阱 / 限流规则表真相源 |
| **计划审计** | `../docs/superpowers/plans/audit/README.md` | 9 份实施计划的 checkbox 逐条核实（565 勾 / 175 明确写没做） |
| **根 README** | `../../README.md` | 项目门面：模块布局、部署架构、端口转发 |
| **W74 VERIFICATION** | `../docs/verification/week-74/OPC-W74-VERIFICATION-opc-content.md` | opc-content 收官报告 |
| **W74 实施计划** | `../docs/superpowers/plans/2026-09-14-opc-content-impl.md` | 16-Task 实施 plan |
| **W74 设计 spec** | `../docs/superpowers/specs/2026-09-14-opc-content-design.md` | opc-content 设计 |
| **历史 VERIFICATION** | `../docs/verification/README.md` | 索引 (week-1..12 + milestones) |

### 为什么 Helm chart 里没有调度器 —— 因为 K8s 不在本项目范围内

`helm/opc/` 里有 **14 个 deployment**，**没有调度器**（`ruoyi-job`）。
这不是「装漏了」，是 **2026-11-02 定的范围**：本项目只走 Docker Compose 一条部署路径，
K8s 那条路径整个划出范围，**不补、不修、不验收**。

compose 路径上的调度器是通的 —— `aiopc-job` 容器（9203）+ 网关 `/schedule` 路由 +
健康检查第 25 节（真触发一次 + 落库对账）。见 `CLAUDE.md` 坑 #9 与 `RECOVERY.md` §17。
