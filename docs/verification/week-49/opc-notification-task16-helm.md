# Task 16: Helm chart — opc-notification entry

Date: 2026-09-11

## Changes

- Added `notification:` block to `values.yaml` (default):
  - name: `opc-notification`
  - port: 9310
  - image: `opc/opc-notification`
  - tier: business
  - resources: requests 300m/768Mi, limits 1500m/1.5Gi
  - jvmOpts: `-Xms512m -Xmx1g -XX:+UseG1GC`
  - probes: liveness/readiness `/actuator/health/{liveness,readiness}`
  - secrets: JASYPT_PASSWORD (from `opc-jasypt-secrets`)
  - hpa: disabled (default)
- Added `notification:` block to `values-dev.yaml` (replicaCount=1)
- Added `notification:` block to `values-staging.yaml` (replicaCount=2)
- Added `notification:` block to `values-prod.yaml` (replicaCount=2, hpa disabled)
- Created `templates/deployment-notification.yaml` (mirrors `deployment-insight.yaml`)
- Created `templates/service-notification.yaml` (ClusterIP:9310, mirrors `service-insight.yaml`)

Pattern matches the insight service (recently added). HPA and PDB templates already
auto-include new services via `range over .Values.services`, no edit needed.

## Verification

### helm lint (all envs)

```
helm lint ./springboot3/deploy/helm/opc -f values-{dev,staging,prod}.yaml
==> Linting ./springboot3/deploy/helm/opc
1 chart(s) linted, 0 chart(s) failed   (×3)
```

### helm template (per env kind counts)

| env      | Deploy | Service | PDB | HPA | Ingress | SM | CM | NS | SA | total |
|----------|--------|---------|-----|-----|---------|----|----|----|----|-------|
| default  | 9      | 9       | 9   | 1   | 1       | 1  | 1  | 1  | 1  | 33    |
| dev      | 9      | 9       | 0   | 0   | 0       | 0  | 1  | 1  | 1  | 21    |
| staging  | 9      | 9       | 9   | 1   | 1       | 1  | 1  | 1  | 1  | 33    |
| prod     | 9      | 9       | 9   | 3   | 1       | 1  | 1  | 1  | 1  | 35    |

All envs include `opc-notification` Deployment + Service (port 9310).

### diff-envs.py (Sub-task 6.3)

```
cd springboot3 && python deploy/helm/opc/ci/diff-envs.py

[1/3] helm install --dry-run per env
  values-dev: install dry-run = OK, dry-run marker = True
  values-staging: install dry-run = OK, dry-run marker = True
  values-prod: install dry-run = OK, dry-run marker = True

[2/3] resource Kind set comparison
  [PASS] resource differences only in ['HorizontalPodAutoscaler', 'Ingress',
                                        'Namespace', 'PodDisruptionBudget', 'ServiceMonitor']
  common across envs: 20 docs (1 CM + 9 Deploy + 9 Service + 1 SA)

[3/3] SHAPE invariants on common docs
  [PASS] all 20 common docs have required SHAPE fields

[OK] 6.3 AC satisfied: multi-env values share same templates + dry-run ready
```

## Rendered Resources (default env)

- Deployment/opc-notification (replicas=2, image `opc/opc-notification:1.0.0`)
- Service/opc-notification (ClusterIP, port 9310)
- PodDisruptionBudget/opc-notification (minAvailable=1)
- EnvFrom: shared `opc-common` ConfigMap + JASYPT_PASSWORD secret
- HPA: not rendered (hpa.enabled=false)

## Commit

- `feat(helm): Task 16 — add opc-notification service to chart`
- `docs(notification): Task 16 — helm verification log`