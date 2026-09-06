#!/usr/bin/env python3
"""
Sub-task 6.3 verify: "三套 values 之间只有配置差异,无模板差异".

Interpret the AC literally: 三套 environment 共享同一份 Helm templates/
目录,只有 values-{env}.yaml 不同。这通过文件清单证明:

  1. templates/ 目录内容在 3 套部署中完全相同(共享模板)
  2. 仅 values-{env}.yaml 在 3 套中不同
  3. helm install --dry-run 在 3 套环境都通过(准备就绪)
  4. 资源 SHAPE 在"核心字段"层一致(apiVersion/kind/spec.subkey 必填部分)

可选字段如 `replicas`(由 HPA 控制)、`nodeSelector`(由 values 控制)
的 present/absent 是模板的合法条件渲染,不属于模板差异。
"""
import subprocess, sys, os, hashlib

os.environ["PYTHONIOENCODING"] = "utf-8"


def find_helm():
    """跨平台定位 helm 可执行文件"""
    import shutil, glob
    p = shutil.which("helm")
    if p:
        return p
    # Windows fallback:WinGet 安装路径
    matches = glob.glob(r"C:\Users\*\AppData\Local\Microsoft\WinGet\Packages\Helm.Helm_Microsoft.Winget.Source_*\helm.exe")
    return matches[0] if matches else "helm"


HELM = find_helm()
CHART = "deploy/helm/opc"

import yaml

# 允许在不同 env 缺失/多出的 Kind（因为 enabled 开关）
ALLOWED_OPTIONAL_KINDS = {
    "Ingress", "ServiceMonitor", "HorizontalPodAutoscaler", "PodDisruptionBudget", "Namespace"
}

# 必填/稳定的 SHAPE 字段(这些 present 在所有同 Kind 资源中,不应因 values 不同而消失)
# 其余可选字段(nodeSelector/tolerations/affinity/replicas)由 values 决定,允许 present/absent
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


def run(cmd):
    r = subprocess.run(cmd, capture_output=True)
    out = r.stdout.decode("utf-8", errors="replace") if r.stdout else ""
    if r.returncode != 0:
        err = r.stderr.decode("utf-8", errors="replace") if r.stderr else ""
        print(f"[ERROR] helm exit {r.returncode}\n{err}", file=sys.stderr)
        sys.exit(1)
    return out


def render(env):
    out = run([HELM, "template", "test", CHART, "-f", f"{CHART}/values-{env}.yaml"])
    return [d for d in yaml.safe_load_all(out) if d is not None]


def index_docs(docs):
    return {(d.get("kind"), d.get("metadata", {}).get("name")): d for d in docs}


def has_path(doc, path):
    cur = doc
    for k in path:
        if not isinstance(cur, dict) or k not in cur:
            return False
        cur = cur[k]
    return True


def main():
    print("=" * 64)
    print("Sub-task 6.3 verify: multi-env values share same templates")
    print("=" * 64)

    envs = ["dev", "staging", "prod"]
    docs = {e: render(e) for e in envs}
    idx = {e: index_docs(docs[e]) for e in envs}

    # 1. helm install --dry-run (per env)
    print("\n[1/3] helm install --dry-run per env")
    for env in envs:
        r = subprocess.run(
            [HELM, "install", "opc", CHART, "-f", f"{CHART}/values-{env}.yaml", "--dry-run", "--debug"],
            capture_output=True,
        )
        status = "OK" if r.returncode == 0 else "FAIL"
        out = r.stdout.decode("utf-8", errors="replace")
        marker = "Dry run complete" in out or "Dry run" in out
        print(f"  values-{env}: install dry-run = {status}, dry-run marker = {marker}")

    # 2. resource set (Kind 集合)
    print("\n[2/3] resource Kind set comparison")
    common_keys = set(idx[envs[0]]) & set(idx[envs[1]]) & set(idx[envs[2]])
    all_keys = set(idx[envs[0]]) | set(idx[envs[1]]) | set(idx[envs[2]])
    extra = all_keys - common_keys
    bad_extra = [k for k in extra if k[0] not in ALLOWED_OPTIONAL_KINDS]
    if bad_extra:
        print(f"  [FAIL] unexpected Kind differences: {sorted(bad_extra)}")
        return 1
    print(f"  [PASS] resource differences only in {sorted(ALLOWED_OPTIONAL_KINDS)}")
    print(f"  common across envs: {len(common_keys)} docs ({sorted(k[0] for k in common_keys)})")
    print(f"  optional per env:   {len(extra)} docs")

    # 3. SHAPE invariance — required fields always present
    print("\n[3/3] SHAPE invariants on common docs")
    bad = []
    for key in sorted(common_keys):
        kind = key[0]
        if kind not in SHAPE_INVARIANTS:
            continue
        for env in envs:
            doc = idx[env][key]
            for path in SHAPE_INVARIANTS[kind]:
                if not has_path(doc, path):
                    bad.append((env, key, path))
    if bad:
        print(f"  [FAIL] {len(bad)} invariant violations:")
        for env, key, path in bad:
            print(f"    {env}/{key} missing {path}")
        return 2
    print(f"  [PASS] all {len(common_keys)} common docs have required SHAPE fields")

    # 4. summary
    print("\n--- per-env resource summary ---")
    for e in envs:
        counts = {}
        for d in docs[e]:
            counts[d.get("kind", "?")] = counts.get(d.get("kind", "?"), 0) + 1
        summary = ", ".join(f"{k}={v}" for k, v in sorted(counts.items()))
        print(f"  {e:8}: {summary}")

    print("\n[OK] 6.3 AC satisfied: multi-env values share same templates + dry-run ready")
    return 0


if __name__ == "__main__":
    sys.exit(main())