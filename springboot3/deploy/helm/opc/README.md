# OPC Helm Chart

> OPC-Agent-Community (OAC) 微服务 Helm Chart
> 多环境（dev / staging / prod）一键部署

## 目录

- [快速开始](#快速开始)
- [环境矩阵](#环境矩阵)
- [常用命令](#常用命令)
- [升级与回滚](#升级与回滚)
- [Secrets 管理](#secrets-管理)
- [CI/CD 集成](#cicd-集成)
- [故障排查](#故障排查)

---

## 快速开始

### 1. 安装 chart

```bash
# 1.1 准备命名空间（首次）
kubectl create namespace opc-dev

# 1.2 创建 Secrets（外部管理，不在 chart 中）
kubectl create secret generic opc-ai-secrets \
  --from-literal=openai-api-key=$OPENAI_API_KEY \
  --from-literal=zhipuai-api-key=$ZHIPUAI_API_KEY \
  -n opc-dev

kubectl create secret generic opc-jasypt-secrets \
  --from-literal=password=$JASYPT_PASSWORD \
  -n opc-dev

# 1.3 本地开发部署
helm install opc ./opc -f values-dev.yaml

# 1.4 预发布部署
helm install opc ./opc -f values-staging.yaml

# 1.5 生产部署
helm install opc ./opc -f values-prod.yaml
```

### 2. 验证部署

```bash
kubectl get pods -n opc          # 查看 Pod
kubectl get svc -n opc           # 查看 Service
kubectl get ingress -n opc       # 查看 Ingress
kubectl get hpa -n opc           # 查看 HPA
helm list -n opc                 # 查看 Helm Release
```

### 3. 访问服务

| 环境 | URL |
|------|-----|
| 本地 | `kubectl port-forward -n opc-dev svc/ruoyi-gateway 8080:8080` 后访问 http://localhost:8080 |
| 预发布 | https://staging.opc.example.com |
| 生产 | https://opc.example.com |

---

## 环境矩阵

| 维度 | dev | staging | prod |
|------|-----|---------|------|
| Namespace | opc-dev | opc-staging | opc |
| 副本数 | 1 | 2 | 3-4 |
| HPA | ✗ | ✓ (2-4) | ✓ (4-16) |
| Ingress | ✗ (port-forward) | ✓ | ✓ |
| ServiceMonitor | ✗ | ✓ | ✓ |
| PodDisruptionBudget | ✗ | ✓ | ✓ |
| 镜像 tag | latest | 1.0.0-rc.1 | 1.0.0 |
| 镜像拉取策略 | Never | IfNotPresent | Always |
| 资源限制 | 宽松 | 中等 | 严格 |

---

## 常用命令

### 查看渲染结果（不实际部署）

```bash
# dev 环境模板
helm template opc ./opc -f values-dev.yaml

# prod 环境模板，只看 ai-core
helm template opc ./opc -f values-prod.yaml --show-only templates/deployment-ai-core.yaml

# 带 debug 看变量
helm template opc ./opc -f values-dev.yaml --debug
```

### 语法检查

```bash
helm lint ./opc
helm lint ./opc -f values-prod.yaml
```

### 模拟安装

```bash
helm install opc ./opc -f values-dev.yaml --dry-run --debug
```

### 升级

```bash
# 升级镜像版本
helm upgrade opc ./opc -f values-prod.yaml \
  --set global.imageTag=1.0.1 \
  --record

# 升级到指定 chart 版本
helm upgrade opc ./opc -f values-prod.yaml --version 1.1.0
```

### 回滚

```bash
# 查看历史
helm history opc -n opc

# 回滚到上一个版本
helm rollback opc -n opc

# 回滚到指定版本
helm rollback opc 3 -n opc
```

### 卸载

```bash
helm uninstall opc -n opc
```

---

## 升级与回滚

### 滚动升级策略

Chart 默认配置 `RollingUpdate`：
- `maxSurge: 25%` - 滚动时可超出副本数 25%
- `maxUnavailable: 25%` - 滚动时最大不可用 25%

生产环境可加严（在 values-prod.yaml）：
```yaml
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxSurge: 1
    maxUnavailable: 0  # 生产永不中断
```

### 灰度发布（蓝绿）

```bash
# 1. 部署新版本到独立 release
helm install opc-canary ./opc -f values-prod.yaml \
  --set global.imageTag=1.0.1 \
  --set global.namespace=opc-canary

# 2. 切流量（用 Istio / Nginx / 云 LB）
kubectl apply -f canary-virtualservice.yaml  # 切 5% 流量

# 3. 监控无异常，全量切
kubectl apply -f canary-virtualservice-100pct.yaml

# 4. 删除旧 release
helm uninstall opc-old -n opc
```

### 一键回滚

```bash
# 回滚整个 release
helm rollback opc -n opc

# 回滚单个服务（k8s 原生）
kubectl rollout undo deployment/opc-ai-core -n opc

# 回滚到指定版本
kubectl rollout undo deployment/opc-ai-core -n opc --to-revision=2
```

---

## Secrets 管理

**重要**：本 Chart **不在集群中创建任何 Secret**。所有敏感信息通过外部 `kubectl create secret` 管理，原因：
1. Secret 不进 Git
2. Secret 不进 Helm Release 历史
3. 团队成员权限隔离

### 必需的 Secrets

| Secret 名称 | 字段 | 来源 |
|------------|------|------|
| `opc-ai-secrets` | `openai-api-key`, `zhipuai-api-key` | LLM 厂商控制台 |
| `opc-jasypt-secrets` | `password` | Jasypt 加密密码 |
| `aliyun-registry-secret` | `.dockerconfigjson` | `kubectl create secret docker-registry` |

### 创建命令

```bash
# LLM API Keys
kubectl create secret generic opc-ai-secrets \
  --from-literal=openai-api-key=$OPENAI_API_KEY \
  --from-literal=zhipuai-api-key=$ZHIPUAI_API_KEY \
  -n opc

# Jasypt 密码
kubectl create secret generic opc-jasypt-secrets \
  --from-literal=password=$JASYPT_PASSWORD \
  -n opc

# Docker Registry 凭证
kubectl create secret docker-registry aliyun-registry-secret \
  --docker-server=registry.cn-hangzhou.aliyuncs.com \
  --docker-username=$ALIYUN_USER \
  --docker-password=$ALIYUN_PASS \
  -n opc
```

### 切换到 Sealed Secrets / External Secrets

生产环境建议用：
- **Sealed Secrets**（Bitnami）：把 Secret 加密后进 Git
- **External Secrets Operator**：从 AWS Secrets Manager / Vault 同步
- **阿里云 KMS**：直接对接 KMS

参考 `deploy/secrets/` 下后续文档。

---

## CI/CD 集成

本仓库提供 3 套 CI 集成(可直接使用):

| 平台 | 实际文件 | 说明 |
|------|----------|------|
| Jenkins | `/Jenkinsfile`(项目根) | Declarative pipeline,5 阶段:lint matrix → template → diff-envs → dry-run install → deploy |
| GitHub Actions | `/.github/workflows/helm-validate.yml` | 3 jobs:lint matrix → template + artifact → diff-envs |
| GitLab CI | `/deploy/helm/opc/ci/gitlab-ci.yml.example` | 参考模板(复制到根 `.gitlab-ci.yml` 使用) |

### GitHub Actions 验证流程

```bash
# 本地模拟 (等价于 .github/workflows/helm-validate.yml)
helm version                          # v3.14+
helm lint deploy/helm/opc -f deploy/helm/opc/values-dev.yaml
helm lint deploy/helm/opc -f deploy/helm/opc/values-staging.yaml
helm lint deploy/helm/opc -f deploy/helm/opc/values-prod.yaml

for env in dev staging prod; do
    helm template opc deploy/helm/opc \
        -f deploy/helm/opc/values-${env}.yaml \
        --set global.imageTag=$BUILD_NUMBER > rendered-${env}.yaml
done

pip install pyyaml
PYTHONIOENCODING=utf-8 python deploy/helm/opc/ci/diff-envs.py  # Sub-task 6.3 SHAPE 等价性
```

GitHub Actions 跑通后会自动:
- 上传 `rendered-{env}.yaml` 作为 artifact(7 天保留)
- 在 PR 上评论 diff(可选 + gh bot)

### Jenkins Pipeline 验证流程

`Jenkinsfile` 包含 5 个 stage:

1. **Helm Lint (matrix)** — parallel 跑 dev/staging/prod 的 `helm lint`
2. **Helm Template** — 渲染所有环境,归档 `rendered-*.yaml`
3. **Helm Diff-Envs** — 跑 `ci/diff-envs.py`,确保模板结构等价
4. **Helm Install Dry-Run** — `helm install --dry-run=client`,确认 manifest 接受
5. **Deploy** — develop→staging(自动),main→prod(手动确认 + `--wait --timeout 15m`)

### 回滚命令 (Sub-task 6.4 AC)

```bash
# 查看历史
helm history opc -n opc

# 回滚到上一版本
helm rollback opc -n opc

# 回滚到指定版本
helm rollback opc 1 -n opc

# 干跑(检查 manifest 而不应用)
helm rollback opc 1 -n opc --dry-run=client
```

**已验证**:本地 `helm rollback --help` 语法正确(v4.2.4)。完整 `helm rollback opc 1` 运行时验证需要真实 K8s 集群(`--record` 部署过的 release 才能回滚)。

### 完整 deploy / upgrade / rollback 流程

```bash
# 1) 首次部署 (安装)
helm install opc deploy/helm/opc -f deploy/helm/opc/values-prod.yaml \
  --set global.imageTag=1.0.0 \
  --namespace opc --create-namespace --wait --timeout 10m --record

# 2) 升级 (新版本)
helm upgrade opc deploy/helm/opc -f deploy/helm/opc/values-prod.yaml \
  --set global.imageTag=1.0.1 \
  --namespace opc --wait --timeout 10m --record

# 3) 回滚 (历史任一版本)
helm history opc -n opc                          # 查看所有 revision
helm rollback opc 1 -n opc --wait --timeout 5m   # 回滚到 revision 1
```

> ✅ `helm install / upgrade / rollback` 完整命令在本文档 §常用命令 和 §升级与回滚 中均有详细示例。

---

## 故障排查

### Pod 启动失败

```bash
kubectl describe pod <pod-name> -n opc
kubectl logs <pod-name> -n opc --previous
```

### Helm Release 状态异常

```bash
helm status opc -n opc
helm get manifest opc -n opc
```

### 回滚后仍有问题

```bash
# 查看历史
helm history opc -n opc

# 查看每个 revision 的 manifest 差异
helm get manifest opc -n opc --revision 2

# 强制回滚到指定版本
helm rollback opc 5 --force -n opc
```

### 数据库连接失败

```bash
# 检查 MySQL Service 是否存在
kubectl get svc -n opc | grep mysql

# 进入 Pod 测试连接
kubectl exec -it opc-ai-core-xxx -n opc -- sh
$ nc -zv mysql.opc.svc.cluster.local 3306
```

### 常见问题

| 问题 | 解决 |
|------|------|
| `ImagePullBackOff` | 检查 `imagePullSecrets` 是否正确 |
| `CrashLoopBackOff` | `kubectl logs` 看启动日志，通常是配置错 |
| HPA 不工作 | 检查 `metrics-server` 是否安装 |
| Ingress 404 | 检查 IngressClass 和 `ingressClassName` |
| PodDisruptionBudget 阻止驱逐 | 临时调大 `minAvailable` 或删除 PDB |

---

## 自定义 values

### 关闭某个服务

```bash
helm install opc ./opc -f values-prod.yaml \
  --set services.finance.enabled=false
```

### 调整副本数

```bash
helm upgrade opc ./opc -f values-prod.yaml \
  --set services.ai-core.replicaCount=6
```

### 启用 HPA

```bash
helm upgrade opc ./opc -f values-prod.yaml \
  --set services.agent-hub.hpa.enabled=true \
  --set services.agent-hub.hpa.minReplicas=3 \
  --set services.agent-hub.hpa.maxReplicas=10
```

---

## 文件结构

```
deploy/helm/opc/
├── Chart.yaml                    # Chart 元数据
├── values.yaml                   # 默认 values
├── values-dev.yaml               # 本地开发覆盖
├── values-staging.yaml           # 预发布覆盖
├── values-prod.yaml              # 生产覆盖
├── .helmignore                   # 打包忽略
├── README.md                     # 本文档
├── ci/
│   ├── diff-envs.py              # 多环境 values 模板结构等价性验证 (Sub-task 6.3)
│   ├── Jenkinsfile.example       # Jenkins 流水线示例
│   └── gitlab-ci.yml.example     # GitLab CI 流水线示例
└── templates/
    ├── _helpers.tpl              # 模板函数 (11 个 helper)
    ├── NOTES.txt                 # 安装成功提示
    ├── namespace.yaml            # Namespace
    ├── serviceaccount.yaml       # ServiceAccount
    ├── configmap.yaml            # 共享 ConfigMap
    ├── ingress.yaml              # Ingress
    ├── servicemonitor.yaml       # Prometheus Operator
    ├── hpa.yaml                  # HPA (range over services.hpa.enabled)
    ├── pdb.yaml                  # PodDisruptionBudget (range over enabled)
    ├── deployment-gateway.yaml   # 网关 (Deployment)
    ├── deployment-ai-core.yaml   # AI 中台
    ├── deployment-agent-hub.yaml   # Agent Hub
    ├── deployment-user-center.yaml # 用户中心
    ├── deployment-billing.yaml   # 计费
    ├── deployment-finance.yaml   # 财务 Agent
    ├── deployment-system.yaml    # ruoyi-system
    ├── service-gateway.yaml      # 网关 Service
    ├── service-ai-core.yaml      # AI 中台 Service
    ├── service-agent-hub.yaml    # Agent Hub Service
    ├── service-user-center.yaml  # 用户中心 Service
    ├── service-billing.yaml      # 计费 Service
    ├── service-finance.yaml      # 财务 Service
    └── service-system.yaml       # ruoyi-system Service
```

### Template 文件拆分原则 (Sub-task 6.2)

每个服务有 **2 个独立文件**:`deployment-<svc>.yaml` (Deployment) + `service-<svc>.yaml` (Service)。
共享资源 (HPA / PDB) 用单文件 + `range` 模式生成多实例。

**多环境渲染结果**:

| 环境 | Deploy | Service | HPA | PDB | Ingress | ServiceMonitor | 总数 |
|------|--------|---------|-----|-----|---------|----------------|------|
| default | 7 | 7 | 1 | 7 | 1 | 1 | **28** |
| dev | 7 | 7 | 0 | 0 | 0 | 0 | **18** |
| staging | 7 | 7 | 1 | 7 | 1 | 1 | **28** |
| prod | 7 | 7 | **2 | 7 | 1 | 1 | **29** |

差异: HPA 数量随 values 中 `services.{name}.hpa.enabled` 变化 (默认 ai-core;prod 启用 ai-core + agent-hub)。
Ingress / PDB / ServiceMonitor 由全局 enabled 控制,dev 全关。

---

## 验证 Checklist

部署前确认：

- [ ] `helm lint ./opc -f values-{env}.yaml` 0 errors
- [ ] `helm template opc ./opc -f values-{env}.yaml | kubectl apply --dry-run=client -f -` 全部资源可创建
- [ ] Secrets 已在目标命名空间创建
- [ ] 镜像已推送到目标 Registry
- [ ] Nacos / MySQL / Redis 等中间件可访问
- [ ] 域名 DNS 已解析到 Ingress Controller
- [ ] 监控（Prometheus + Grafana）已就绪

部署后验证：

- [ ] `kubectl get pods -n opc` 全部 Running
- [ ] `kubectl get svc -n opc` 有 ClusterIP
- [ ] Ingress 状态为 `HOSTS` 而非空
- [ ] 通过域名能访问前端
- [ ] Grafana 大盘能看到 4 大黄金指标
- [ ] 告警规则已加载（P0/P1/P2）

---

> 文档版本：v1.2 · 2026-09-04 · 与 `OPC-W1-TASK-BREAKDOWN.md` Task #6 配套
>
> v1.2 更新 (Sub-task 6.4):
> - CI/CD 集成章节:从"示例代码块"改为引用根目录实际文件(`/Jenkinsfile` + `/.github/workflows/helm-validate.yml` + `/ci/gitlab-ci.yml.example`)
> - 新增"回滚命令"小节,明确 `--dry-run=client` 限制(需真实 K8s cluster 做完整验证)
> - 完整 install/upgrade/rollback 流程汇总
>
> v1.1 更新 (Sub-task 6.2 + 6.3):
> - 文件结构更新:7 deployment-* + 7 service-* + hpa.yaml + pdb.yaml
> - 新增 "Template 文件拆分原则" 表格
> - 新增 `ci/diff-envs.py` 多环境模板结构等价性验证脚本
> - values-{env}.yaml 增加"与 default 差异点"头注释
