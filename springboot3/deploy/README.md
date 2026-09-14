# OPC 部署文档 (Deploy README) — W74 更新

> **最后更新**: 2026-09-14 (W74 opc-content 上线 + 全栈部署固化)
> **覆盖服务**: 8 OPC 微服务 + 5 RuoYi 内置 (gateway/auth/system) + 6 基础设施

## 一键启动 (本地开发 · 推荐)

```bash
cd springboot3/deploy

# 1. 复制 .env (W48.6 模板已含 LLM keys,首次需要复制)
[ -f .env ] || cp .env.example .env

# 2. 启动基础依赖 (mysql / nacos / redis / ...)
docker compose up -d nacos1 mysql redis
sleep 60                                # 等 nacos 起来 (健康检查 60s)

# 3. 导入 Nacos 配置 (W74: 含 opc-content-dev.yml)
cd nacos && bash import-dev.sh && cd ..

# 4. 启动后端 (gateway/auth/system/ai-core/agent-hub/billing/finance/user-center/notification/crm/community/hr/erp/content)
docker compose up -d aiopc-gateway aiopc-auth aiopc-system \
                   aiopc-ai-core aiopc-user-center aiopc-agent-hub \
                   aiopc-billing aiopc-finance aiopc-notification \
                   aiopc-crm aiopc-community aiopc-hr aiopc-erp aiopc-content

# 5. 启动前端 (W51 教训: 上游 IP 漂移后必须显式 rebuild)
docker compose build aiopc-frontend && docker compose up -d aiopc-frontend

# 6. 健康检查 (W48.7 风格 22+ 项 + W74 opc-content 10 项)
bash scripts/health-check.sh

# 7. e2e 验证 (W74 新增 opc-content)
cd ../tmp_e2e && OPC_GATEWAY=http://127.0.0.1:8080 python e2e_content.py
```

## 全栈服务清单 (W74)

| 服务 | 端口 | 命名空间 | 路由前缀 | 说明 |
|------|------|---------|---------|------|
| **基础设施** |||||
| nacos1 | 8848 | - | - | 注册/配置中心 |
| mysql | 3306 (容器内) | - | - | 数据库 (容器外 3308) |
| redis | 6379 | - | - | 缓存 |
| **RuoYi 内置** |||||
| aiopc-gateway | 8080 | - | - | Spring Cloud Gateway (8080) |
| aiopc-auth | 9200 → 19200 | - | /login /auth | RuoYi 认证 (19200 外部) |
| aiopc-system | 9201 | - | /system | 用户/角色/菜单/字典 |
| **OPC 8 大业务** |||||
| aiopc-ai-core | 9301 | opc-dev | /opc/llm | LLM 网关 (DeepSeek + MiniMax) |
| aiopc-user-center | 9302 | opc-dev | /opc/user /opc/company /opc/invitation | 用户中心 |
| aiopc-agent-hub | 9303 | opc-dev | /opc/agent | Agent 工作流 (W52 Quartz cron) |
| aiopc-billing | 9304 | opc-dev | /opc/billing | 计费 |
| aiopc-finance | 9305 | opc-dev | /opc/finance | 财务 (凭证/银行流水/税报) |
| aiopc-notification | 9310 | opc-dev | /opc/notification | 通知中心 (REST + WS) |
| aiopc-crm | 9312 | opc-dev | /opc/crm | CRM (客户/商机/合同/订单) |
| aiopc-community | 9316 | opc-dev | /opc/community | 模块市场 (W52) |
| aiopc-hr | 9322 | opc-dev | /opc/hr | 招聘管理 (W51/73) |
| aiopc-erp | 9311 | opc-dev | /opc/erp | 进销存 (W72) |
| **aiopc-content (W74 NEW)** | **9325** | **opc-dev** | **/opc/content** | **AI 内容创作中心** |
| aiopc-frontend | 8079 | - | - | Vue 3 + TS (nginx 反向代理) |

## 关键文件

| 文件 | 作用 | W74 状态 |
|------|------|---------|
| `docker-compose.yml` | 24 个服务编排 | ✅ 已加 aiopc-content |
| `mysql-initdb.d/16-opc-content-schema.sql` | 4 表 DDL | ✅ 已修 `ry-cloud`→`ry-vue-opc` |
| `mysql-initdb.d/98-opc-content-seed.sql` | dev seed (3+1+1+1) | ✅ |
| `nacos/opc-content-dev.yml` | dev 配置 (38 项) | ✅ 已修 `ENC(占位)`→明文占位 |
| `nacos/import-dev.sh` | 7 个 yml 批量推送 | ✅ 已加 opc-content |
| `scripts/health-check.sh` | 22+10 端点检查 | ✅ check_content() 10/10 |
| `tmp_e2e/e2e_content.py` | 10 端点 e2e | ✅ 8/10 PASS (2 失败是后端校验) |
| `../docs/verification/week-74/OPC-W74-VERIFICATION-opc-content.md` | VERIFICATION 报告 | ✅ |
| `../docs/superpowers/plans/2026-09-14-opc-content-impl.md` | 16-Task 实施计划 | ✅ |

## 关键修复 (W74 部署阶段发现)

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

## Helm 部署 (K8s)

```bash
# helm lint
helm lint deploy/helm/opc

# helm template 看 kind 数 (dev=29 / staging=29 / prod=29)
helm template test deploy/helm/opc | grep '^kind:' | sort | uniq -c

# 安装到 K8s
helm install opc deploy/helm/opc --namespace opc --create-namespace
```

## 公测部署 (K8s 旧版,待更新)

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

## 灰度发布

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
| **RECOVERY** | `RECOVERY.md` | 9 节一键恢复 (前置/步骤/范围/备份/位置/Nacos/前端/坑/清单) |
| **W74 VERIFICATION** | `../docs/verification/week-74/OPC-W74-VERIFICATION-opc-content.md` | opc-content 收官报告 |
| **W74 实施计划** | `../docs/superpowers/plans/2026-09-14-opc-content-impl.md` | 16-Task 实施 plan |
| **W74 设计 spec** | `../docs/superpowers/specs/2026-09-14-opc-content-design.md` | opc-content 设计 |
| **历史 VERIFICATION** | `../docs/verification/README.md` | 索引 (week-1..12 + milestones) |
