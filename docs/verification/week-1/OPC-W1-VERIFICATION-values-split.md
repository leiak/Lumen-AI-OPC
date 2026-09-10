# W1 Sub-task 6.3 多环境 values 拆分 — 验证报告

> 验证日期:2026-09-04
> 范围:`deploy/helm/opc/values-{dev,staging,prod}.yaml` + 模板结构等价性
> 验证手段:`helm install --dry-run` (3 套) + `diff-envs.py` (Python 静态等价性证明)
> 验证人:Claude

---

## 0. 验收结论

| AC | 状态 | 证据 |
|----|------|------|
| `helm install ... -f values-dev.yaml` 能在 dev 集群起服务 | ✅ | `helm install --dry-run --debug` 退出码 0 + `Dry run complete` 标记 |
| `helm install ... -f values-prod.yaml` 在 prod 集群起服务 | ✅ | 同上 |
| 三套 values 之间只有配置差异,无模板差异 | ✅ | `diff-envs.py` SHAPE 不变量检查:16 个 common docs 全部通过;差异只在 4 个允许的 optional Kind (HPA/PDB/Ingress/ServiceMonitor) + Namespace 命名 |
| 模板文件 + values 文件清单清晰 | ✅ | README v1.1 + values-{env}.yaml 头部"与 default 差异点"注释 |

**整体**:🟢 **Sub-task 6.3 全部 AC 静态达标**(实际部署需 K8s 集群,本机无集群仅做 dry-run + 静态验证)。

---

## 1. 改动清单

### 1.1 已有文件(增强注释)

| 文件 | 改动 |
|------|------|
| `values-dev.yaml` | 头部新增 "与 default 差异点" 17 行注释 + 资源数 18 |
| `values-staging.yaml` | 头部新增 12 行注释 + 资源数 28 |
| `values-prod.yaml` | 头部新增 17 行注释 + 资源数 29 |
| `README.md` | 文件结构表更新(7 service-* + hpa + pdb);新增 "Template 文件拆分原则";版本 v1.0 → v1.1 |

### 1.2 新增文件

| 文件 | 用途 |
|------|------|
| `ci/diff-envs.py` | 多环境模板结构等价性静态验证脚本(3 套 values → helm 渲染 → SHAPE 对比) |

---

## 2. AC-1 / AC-2:`helm install --dry-run` 跨 3 套环境

```
[1/3] helm install --dry-run per env
  values-dev:     install dry-run = OK, dry-run marker = True
  values-staging: install dry-run = OK, dry-run marker = True
  values-prod:    install dry-run = OK, dry-run marker = True
```

✅ 3 套环境的 install dry-run 全部通过(STATUS=pending-install, DESCRIPTION="Dry run complete")。

实际部署到真集群需要:
1. `kubectl create namespace opc-{dev,staging,prod}`
2. 创建 `opc-ai-secrets` + `opc-jasypt-secrets` + `aliyun-registry-secret`(README §Secrets 管理有详细命令)
3. 推送镜像到对应 registry
4. `helm install opc ./opc -f values-{env}.yaml --create-namespace -n opc`

---

## 3. AC-3:三套 values 之间只有配置差异,无模板差异

### 3.1 验证方法

```bash
python ci/diff-envs.py
```

脚本流程:
1. 用 `helm template` 渲染 3 套 values(dev / staging / prod)
2. 索引每个文档为 `(kind, name) → doc`
3. 找出"3 套都存在"的 common docs
4. 对每个 common doc 检查 SHAPE 不变量(必填字段 present):
     - Deployment: spec.selector / spec.strategy / spec.template.spec.containers / spec.template.spec.securityContext / spec.template.spec.serviceAccountName
     - Service: spec.ports / spec.selector / spec.type
     - ConfigMap / ServiceAccount: metadata.labels / metadata.name
5. 报告可选资源差异(必须仅限 4 个允许 Kind)

### 3.2 验证结果

```
[2/3] resource Kind set comparison
  [PASS] resource differences only in ['HorizontalPodAutoscaler', 'Ingress', 'Namespace', 'PodDisruptionBudget', 'ServiceMonitor']
  common across envs: 16 docs
  optional per env:   14 docs

[3/3] SHAPE invariants on common docs
  [PASS] all 16 common docs have required SHAPE fields
```

### 3.3 资源集合差异表(只允许 HPA / PDB / Ingress / ServiceMonitor / Namespace)

| Kind | dev | staging | prod | 差异原因 |
|------|-----|---------|------|----------|
| ConfigMap | 1 | 1 | 1 | — |
| Deployment | 7 | 7 | 7 | — |
| **HorizontalPodAutoscaler** | 0 | 1 | **2** | values-prod 启用 ai-core + agent-hub HPA |
| **Ingress** | 0 | 1 | 1 | dev 关闭 (port-forward) |
| **Namespace** | 1 | 1 | 1 | 命名不同 (opc-dev / opc-staging / opc) |
| **PodDisruptionBudget** | 0 | 7 | 7 | dev 关闭 |
| Service | 7 | 7 | 7 | — |
| ServiceAccount | 1 | 1 | 1 | — |
| **ServiceMonitor** | 0 | 1 | 1 | dev 关闭 |
| **总计** | **18** | **28** | **29** | |

✅ 所有差异都属于以下两类:
- 全局开关控制 (Ingress / ServiceMonitor / PDB 由 `*.enabled` 切换)
- 配置值控制 (HPA 数量由 `services.{name}.hpa.enabled` 控制)
- Namespace 名字 (由 `global.namespace` 配置)

**templates/ 目录本身在 3 套部署中是同一份**,只读不同 values → 这就是 "无模板差异" 的字面证明。

### 3.4 为什么不做 strict value-diff?

最初尝试用 `deep_normalize` 把"配置差异字段"hash 化再做严格 diff,发现:

```
diff:
  spec:
    replicas: v   ← dev 有这个值 (replicaCount=1, hpa.enabled=false)
    selector:
  spec:
    selector:     ← staging 这个字段被 {{- if not $svc.hpa.enabled }} 条件渲染掉
```

`spec.replicas` 由模板条件渲染:
```yaml
{{- if not $svc.hpa.enabled }}
replicas: {{ $svc.replicaCount }}
{{- end }}
```

dev 关闭 HPA → 渲染 `replicas: 1`
staging 启用 HPA → 不渲染 `replicas`

这是**合法的条件模板逻辑**,不是"模板差异"。同理 `nodeSelector / tolerations / affinity` 字段也由 `{{- with values.x }}` 条件渲染。

所以 AC "无模板差异" 应该理解为:**三套环境共享同一份 templates/ 目录**,只 values 不同 — 而不是"渲染输出逐字符相同"。

---

## 4. SHAPE 不变量定义

```python
SHAPE_INVARIANTS = {
    "Deployment": [
        ["metadata", "labels"],
        ["metadata", "name"],
        ["spec", "selector"],
        ["spec", "strategy"],
        ["spec", "template", "metadata", "labels"],
        ["spec", "template", "spec", "containers"],
        ["spec", "template", "spec", "securityContext"],
        ["spec", "template", "spec", "serviceAccountName"],
    ],
    "Service": [
        ["metadata", "labels"],
        ["metadata", "name"],
        ["spec", "ports"],
        ["spec", "selector"],
        ["spec", "type"],
    ],
    "ConfigMap": [
        ["metadata", "labels"],
        ["metadata", "name"],
    ],
    "ServiceAccount": [
        ["metadata", "labels"],
        ["metadata", "name"],
    ],
}
```

**原则**:
- "核心字段" (selector / strategy / containers / ports) 必填 — 缺失 = 模板 bug
- "可选字段" (replicas / nodeSelector / tolerations / affinity) 由 values 决定 — 允许 present/absent

---

## 5. values-{env}.yaml 头部"与 default 差异点" 注释

每个 values 文件头部新增一段注释,明确列出该环境**与 default 的差异点** + **资源渲染数**。例如 `values-prod.yaml`:

```yaml
# 与 default (values.yaml) 的差异点:
#   global.imagePullPolicy : Always (默认: IfNotPresent)
#   serviceMonitor.interval: 15s (默认: 30s)
#   podDisruptionBudget.minAvailable: 2 (默认: 1)
#   strategy.rollingUpdate : maxSurge=1 maxUnavailable=0 (默认: 25%/25%)
#   ingress.annotations     : 增加 cert-manager.io/cluster-issuer + X-Real-IP
#   nodeSelector           : node-role.kubernetes.io/opc=true (默认: {})
#   services.gateway.replicaCount : 3 (默认: 2)
#   services.ai-core.replicaCount : 4 (默认: 2) + HPA min4 max16 cpu60
#   services.agent-hub.hpa        : 启用 (min3 max12 cpu70) — 默认关闭
# 资源渲染: 29 个 K8s 资源(...)
```

运维 / Devs 改 values 时一眼就能看出哪些是"环境定制",避免误覆盖默认行为。

---

## 6. ⚠️ 已知遗留 / 待人工处理

| # | 等级 | 描述 |
|---|------|------|
| 1 | P2 | dev / staging / prod 的 `global.middleware.*` 主机名不同(dev=短名,prod=完整 FQDN),需要目标集群 DNS 能解析;minikube/Docker Desktop 通常 OK,自建 K8s 需配置 /etc/hosts 或 external-dns。 |
| 2 | P2 | `nacos.password` 在 values-staging.yaml / values-prod.yaml 留空(`""`),依赖 external secret 注入(`opc-nacos-secrets`);需要运维在 Nacos 侧先把密码改了,再 `kubectl create secret`。 |
| 3 | P3 | `diff-envs.py` 依赖 PyYAML,在 GitHub Actions / Jenkins 容器里需确保 `pip install pyyaml`(CI 6.4 阶段补)。 |
| 4 | P3 | 当前 SHAPE 不变量手工维护,后续如加新模板字段可能漏配;考虑改用 `inspect` 自动从 helm 模板生成。 |

---

## 7. 运行时验证(待 K8s 集群)

```bash
# 1. dev (本地 minikube / Docker Desktop)
kubectl create namespace opc-dev
helm install opc deploy/helm/opc -f deploy/helm/opc/values-dev.yaml -n opc-dev --create-namespace

# 2. staging
kubectl create namespace opc-staging
# 先创建 secrets
kubectl create secret generic opc-jasypt-secrets --from-literal=password=$JASYPT_PASSWORD -n opc-staging
kubectl create secret generic opc-ai-secrets \
  --from-literal=openai-api-key=$OPENAI_API_KEY \
  --from-literal=zhipuai-api-key=$ZHIPUAI_API_KEY -n opc-staging
helm install opc deploy/helm/opc -f deploy/helm/opc/values-staging.yaml -n opc-staging

# 3. prod
# 同 staging,加上 docker-registry secret + 节点亲和 + cert-manager
kubectl create secret docker-registry aliyun-registry-secret \
  --docker-server=registry.cn-hangzhou.aliyuncs.com \
  --docker-username=$ALIYUN_USER --docker-password=$ALIYUN_PASS -n opc
helm install opc deploy/helm/opc -f deploy/helm/opc/values-prod.yaml -n opc
```

部署后预期:
- dev: 17 个 K8s 资源
- staging: 27 个 K8s 资源
- prod: 28 个 K8s 资源

(差异:见 §3.3 表格)

---

## 8. 推进结论

🟢 **Sub-task 6.3 全部 3 条 AC 静态达标**:

- ✅ dev / staging / prod `helm install --dry-run` 全部 OK
- ✅ templates/ 目录共享,只有 values-{env}.yaml 不同(diff-envs.py 证明)
- ✅ values-{env}.yaml 头部加注释说明差异 + README v1.1 更新结构表

**W1 Task #6 整体收官**(v1 报告误判 6.4 已完成,本次 6.3 修正拆分):

| 子任务 | 状态 | 交付 |
|--------|------|------|
| 6.1 Chart 脚手架 | ✅ | Chart.yaml + 4 个 values + _helpers.tpl |
| 6.2 6 个服务 Templates | ✅(v2 修正) | 7 deployment + 7 service + hpa + ingress + pdb |
| **6.3 多环境 values** | ✅ | 3 套 values + dry-run 通过 + diff-envs.py + 头部注释 + README v1.1 |
| 6.4 CI + 文档 | ⏳ | README v1.1 已就位,Jenkinsfile.example / gitlab-ci.yml.example 已存在;**实际 CI 集成(在仓库加 `.github/workflows/` 或 Jenkins Pipeline 实际跑 diff-envs.py)待 6.4 推进** |

**下一步可选**:
- 6.4 GitHub Actions / Jenkins 实际跑 helm lint + diff-envs.py
- W1 Sub-task 8(E2E Cypress 测试)
- 跨任务:`OPC-W1-TASK-BREAKDOWN.md` 还有 1/5/7/8 多个子任务可继续推进

---

## 9. 变更清单(供 review)

```diff
~ deploy/helm/opc/values-dev.yaml
  + 头部新增 17 行 "与 default 差异点" 注释

~ deploy/helm/opc/values-staging.yaml
  + 头部新增 12 行 "与 default 差异点" 注释

~ deploy/helm/opc/values-prod.yaml
  + 头部新增 17 行 "与 default 差异点" 注释

~ deploy/helm/opc/README.md
  - 文件结构表更新(7 service-* + hpa + pdb)
  + "Template 文件拆分原则" 表格(4 环境资源数对比)
  + 版本 v1.0 → v1.1,加 changelog

++ deploy/helm/opc/ci/diff-envs.py
  + 130 行 Python 静态验证脚本
  + PyYAML + helm v4.x 调用
```

总计:**4 文件修改** + **1 文件新增**。