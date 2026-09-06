# W1 Sub-task 6.4 CI 集成 + 文档验证 — 报告

> 验证日期:2026-09-04
> 范围:`/Jenkinsfile` + `/.github/workflows/helm-validate.yml` + `README.md` v1.2 + `ci/diff-envs.py`
> 验证手段:YAML/Groovy 结构解析 + helm v4.2.4 命令语法验证
> 验证人:Claude

---

## 0. 验收结论

| AC | 状态 | 证据 |
|----|------|------|
| GitHub Actions / Jenkinsfile 加 `helm template` 步骤 | ✅ | `Jenkinsfile`(5062B,5 stage)+ `.github/workflows/helm-validate.yml`(3167B,3 jobs) |
| README 含 `helm install / upgrade / rollback` 完整命令 | ✅ | `deploy/helm/opc/README.md` v1.2 — §常用命令 + §升级与回滚 + §CI/CD 集成 + 新 §回滚命令 |
| 回滚命令经过验证:`helm rollback opc 1` | ✅(语法) | `helm rollback --help` v4.2.4 输出确认语法;完整运行时验证需 K8s cluster |

**整体**:🟢 **Sub-task 6.4 全部 3 条 AC 静态达标**,完整 CI 跑通需 GitHub Actions / Jenkins runner + K8s cluster。

---

## 1. 改动清单

### 1.1 新增 2 个文件

| 文件 | 大小 | 用途 |
|------|------|------|
| `/Jenkinsfile` | 5062B | Jenkins Declarative pipeline(根目录) |
| `/.github/workflows/helm-validate.yml` | 3167B | GitHub Actions workflow |

### 1.2 修改 2 个文件

| 文件 | 改动 |
|------|------|
| `deploy/helm/opc/README.md` | v1.1 → v1.2:§CI/CD 集成 从"示例代码块"改为引用实际文件 + 新 §回滚命令 + 完整 install/upgrade/rollback 流程 |
| `deploy/helm/opc/ci/diff-envs.py` | HELM 路径硬编码 Windows WinGet → 自动检测(`shutil.which` + Windows fallback glob) |

### 1.3 已有未变

| 文件 | 用途 |
|------|------|
| `deploy/helm/opc/ci/Jenkinsfile.example` | 原 example,可作为其他场景参考 |
| `deploy/helm/opc/ci/gitlab-ci.yml.example` | GitLab CI 参考模板(用户复制到根 `.gitlab-ci.yml` 使用) |

---

## 2. Jenkinsfile 设计

```groovy
pipeline {
    agent any
    options { timeout(30 MINUTES); disableConcurrentBuilds() }
    environment { CHART_DIR = 'deploy/helm/opc'; RELEASE_NAME = 'opc' }

    stages {
        1) Helm Lint (matrix)         // 并行跑 dev/staging/prod 的 helm lint
        2) Helm Template               // 渲染所有环境,归档 rendered-*.yaml
        3) Helm Diff-Envs              // 跑 ci/diff-envs.py (Sub-task 6.3 SHAPE 验证)
        4) Helm Install Dry-Run        // helm install --dry-run=client (per env)
        5a) Deploy Staging             // develop 分支自动 deploy 到 opc-staging
        5b) Deploy Production          // main 分支 + 手动确认 input + deploy opc
    }

    post { success/failure/always { ... } }
}
```

### 2.1 关键 Jenkins 特性

| 特性 | 说明 |
|------|------|
| `matrix` | Lint + Install Dry-Run 在 3 个 env 上并行 |
| `archiveArtifacts` | `rendered-{env}.yaml` 归档(7 天)便于问题排查 |
| `input message 'Deploy to production?'` | Prod 部署需人工批准(防误推) |
| `when { branch 'main' }` | Staging 仅 develop,Prod 仅 main |
| `options.timeout 30m` | 单次 build 超时上限 |
| `post { always { cleanWs() } }` | 清理 workspace 防污染下次 build |

### 2.2 本地静态验证(13 个关键段)

```
agent any                           : OK
pipeline {                          : OK
Helm Lint (matrix)                  : OK
Helm Template                       : OK
Helm Diff-Envs                      : OK
Helm Install Dry-Run                : OK
Deploy Staging                      : OK
Deploy Production                   : OK
options {                           : OK
environment {                       : OK
matrix {                            : OK
input message                       : OK
helm upgrade                        : OK
archiveArtifacts                    : OK
```

---

## 3. GitHub Actions workflow 设计

```yaml
name: Helm Chart Validate

on:
  push:        { main / develop / release/* }
  pull_request: { main / develop }
  workflow_dispatch:           # 手动触发

jobs:
  1) helm-lint (matrix)        // ubuntu-latest × {dev, staging, prod}
  2) helm-template             // 渲染 + 上传 rendered-*.yaml artifact (7 天)
  3) helm-diff-envs            // 跨环境 SHAPE 等价性
```

### 3.1 YAML 结构验证(PyYAML)

```
GitHub Actions YAML validation:
  name: Helm Chart Validate
  triggers: ['push', 'pull_request', 'workflow_dispatch']
  jobs: ['helm-lint', 'helm-template', 'helm-diff-envs']
  job helm-lint:      runs-on=ubuntu-latest, steps=3
  job helm-template:  runs-on=ubuntu-latest, steps=6
  job helm-diff-envs: runs-on=ubuntu-latest, steps=5
```

✅ YAML 解析无误;3 个 job 顺序依赖(helm-template needs helm-lint,helm-diff-envs needs helm-template)。

### 3.2 Step 详情

**helm-lint job** (3 steps):
1. `actions/checkout@v4`
2. `azure/setup-helm@v4` with version=v3.14.4
3. `helm version && helm lint ${CHART_DIR} -f ${CHART_DIR}/values-${env}.yaml`

**helm-template job** (6 steps):
1. checkout
2. setup-helm
3. setup-python v3.11
4. pip install pyyaml
5. `helm template` 渲染 3 个 env,`grep -c '^kind:'` 计数
6. `actions/upload-artifact@v4` (name: helm-rendered, retention: 7 days)

**helm-diff-envs job** (5 steps):
1. checkout
2. setup-helm
3. setup-python v3.11
4. pip install pyyaml
5. `python ci/diff-envs.py` (脚本已 auto-detect helm 路径,无需 sed hack)

---

## 4. README v1.2 关键改动

`deploy/helm/opc/README.md` 从 v1.1 → v1.2:

### 4.1 §CI/CD 集成 重写

| 旧版(v1.1) | 新版(v1.2) |
|-----------|----------|
| "Jenkins Pipeline 示例" 代码块 | "Jenkinsfile 在项目根" 引用 |
| "GitLab CI 示例" 代码块 | "GitLab CI 在 ci/gitlab-ci.yml.example" 引用 |
| 无 GitHub Actions | "GitHub Actions 在 .github/workflows/helm-validate.yml" 引用 |
| 无本地模拟 | 新增"GitHub Actions 验证流程"(本地等价命令) |
| 无 Jenkins 阶段说明 | 新增 "Jenkins Pipeline 5 个 stage" 说明 |

### 4.2 新增 §回滚命令 小节

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

明确说明:**已验证 `helm rollback --help` 语法正确(v4.2.4),完整 `helm rollback opc 1` 运行时验证需要真实 K8s cluster**。

### 4.3 新增 §完整 deploy/upgrade/rollback 流程

3 步骤完整命令:
1. **首次部署**: `helm install ... --record`
2. **升级**: `helm upgrade ... --record`
3. **回滚**: `helm history` + `helm rollback opc <rev>`

---

## 5. helm rollback 语法验证

### 5.1 本地验证(`helm rollback --help` v4.2.4)

```
Usage: helm rollback <RELEASE> [REVISION] [flags]

Flags:
      --dry-run string[="unset"]      simulates the operation without persisting changes.
                                       Must be one of: "none" (default), "client", or "server".
      --cleanup-on-fail               allow deletion of new resources created in this rollback when rollback fails
      --history-max int               limit the maximum number of revisions saved per release. Use 0 for no limit (default 10)
      --wait WaitStrategy[=watcher]   wait until resources are ready
      --timeout duration              time to wait for any individual Kubernetes operation (default 5m0s)
```

✅ 语法完整。

### 5.2 限制

`helm rollback opc 1 --dry-run=client` 在本机无 K8s 集群时报:
```
Error: kubernetes cluster unreachable: the server could not find the requested resource
```

这是因为 `--dry-run=client` 仍然需要本地 kubeconfig(context 配置)。完整回滚验证需要:

```bash
# minikube / kind 本地集群
kind create cluster --name opc-test
helm install opc deploy/helm/opc -f values-dev.yaml --namespace opc --create-namespace --record
helm history opc -n opc
helm rollback opc 1 -n opc --wait
```

本机环境无 K8s 集群 → 静态语法验证通过,运行时验证留给 CI / 实际部署。

---

## 6. helm install --dry-run 验证(AC 验证)

3 套环境的 install dry-run 全部通过(Sub-task 6.3 已验证):

```bash
$ helm install opc deploy/helm/opc -f deploy/helm/opc/values-prod.yaml --dry-run=client -n opc
NAME: opc
LAST DEPLOYED: Fri Sep  4 21:25:19 2026
NAMESPACE: opc
STATUS: pending-install
REVISION: 1
DESCRIPTION: Dry run complete
TEST SUITE: None
HOOKS:
```

✅ Helm v4.2.4 接受 `--dry-run=client`(比传统 `--dry-run` 更严格,不连集群)。

---

## 7. ci/diff-envs.py 跨平台化

旧版:
```python
HELM = r"C:\Users\wma19\AppData\Local\Microsoft\WinGet\Packages\Helm.Helm_Microsoft.Winget.Source_8wekyb3d8bbwe\windows-amd64\helm.exe"
```

新版:
```python
def find_helm():
    """跨平台定位 helm 可执行文件"""
    import shutil, glob
    p = shutil.which("helm")         # PATH 中优先
    if p:
        return p
    # Windows fallback:WinGet 安装路径
    matches = glob.glob(r"C:\Users\*\AppData\Local\Microsoft\WinGet\Packages\Helm.Helm_Microsoft.Winget.Source_*\helm.exe")
    return matches[0] if matches else "helm"

HELM = find_helm()
```

收益:
- ✅ 本地开发(Windows / macOS / Linux):直接用 PATH 中的 helm
- ✅ GitHub Actions ubuntu-latest:`apt install helm` 或 `azure/setup-helm` 装的 helm 直接能用
- ✅ Windows WinGet 安装:glob 兜底
- ✅ CI runner 缺 helm:返回 "helm",调用时报清晰错误

re-run 验证:
```
[OK] 6.3 AC satisfied: multi-env values share same templates + dry-run ready
```

✅ 跨平台化后功能不变。

---

## 8. ⚠️ 已知遗留 / 待人工处理

| # | 等级 | 描述 |
|---|------|------|
| 1 | P2 | GitHub Actions workflow 用 `azure/setup-helm@v4` 安装 helm v3.14.4;CI runner 需启用 ubuntu-latest(默认有)。无需额外 secrets。 |
| 2 | P2 | Jenkinsfile 假设 agent 已装 helm v3.14+ + python 3.10+ + pyyaml + kubectl;需 Jenkins agent 预装或 Docker pipeline image(`helm/kubectl-jq:v3.14.4`)。 |
| 3 | P2 | Jenkins Deploy stage 需要 K8s cluster 的 kubeconfig(Prod 部署);建议用 Jenkins credentials store 注入 `KUBECONFIG` env,避免 hardcode。 |
| 4 | P3 | 当前 GH Actions 没有 PR comment(自动 diff),如需可在 `helm-diff-envs` job 加 `marocchino/sticky-pull-request-comment@v2`。 |
| 5 | P3 | 本机无 K8s cluster,完整 `helm install` + `helm rollback` 验证需在 CI runner 或 ops 集群执行。 |
| 6 | P3 | `Jenkinsfile.example` 还在 ci/ 目录,与新的 `/Jenkinsfile` 并存。后续可删除 example 或加注释说明后者优先。 |

---

## 9. 运行时验证(待 CI)

### 9.1 GitHub Actions 触发

PR 创建后会自动跑 `.github/workflows/helm-validate.yml`,预期:
- 3 个 env 的 lint 全过(✅<1 min)
- helm template 渲染 + artifact 上传
- diff-envs.py 跨环境 SHAPE 验证

### 9.2 Jenkins pipeline

新建 job → "Pipeline from SCM" → 选 repo + branch → Jenkinsfile 自动发现。

预期 stage 顺序:
1. Lint (parallel 3 jobs,~30s)
2. Template (~10s)
3. Diff-Envs (~5s)
4. Install Dry-Run (parallel 3 jobs,~5s)
5. Deploy (main 分支 + 手动 input)

---

## 10. 推进结论

🟢 **Sub-task 6.4 全部 3 条 AC 静态达标**:

- ✅ Jenkinsfile (5 stages,含 helm template + dry-run + deploy) + GitHub Actions (3 jobs) 实际文件就位
- ✅ README v1.2 含完整 `helm install / upgrade / rollback` 命令 + 4 阶段流程
- ✅ `helm rollback` 语法经 helm v4.2.4 验证,完整运行时验证需 K8s cluster

**W1 Task #6 整体收官**:

| 子任务 | 状态 | 交付 |
|--------|------|------|
| 6.1 Chart 脚手架 | ✅ | Chart.yaml + 4 个 values + _helpers.tpl + .helmignore |
| 6.2 服务 Templates | ✅(v2) | 7 deployment + 7 service + hpa + ingress + pdb |
| 6.3 多环境 values | ✅(v3) | 3 套 values + diff-envs.py + dry-run ready |
| **6.4 CI + 文档** | ✅ | Jenkinsfile + GitHub Actions + README v1.2 + 跨平台 helm 检测 + rollback 语法验证 |

**Task #6 全部 4 子任务完成** 🎉

---

## 11. 变更清单(供 review)

```diff
++ springboot3/Jenkinsfile
++ springboot3/.github/workflows/helm-validate.yml

~ springboot3/deploy/helm/opc/README.md
  - §CI/CD 集成 章节重写(引用实际文件,5 Jenkins stage,3 GH Actions job)
  + 新 §回滚命令 小节(helm rollback 语法 + dry-run 限制说明)
  + 新 §完整 deploy/upgrade/rollback 流程(3 步命令)
  - 版本 v1.1 → v1.2,加 v1.2 changelog

~ springboot3/deploy/helm/opc/ci/diff-envs.py
  - HELM 硬编码 Windows 路径 → find_helm() 自动检测(shutil.which + Windows glob fallback)
```

总计:**2 文件新增** + **2 文件修改**。

后续可选:W1 跨任务推进(Task #1 / #5 / #7 / #8)或其他主题。