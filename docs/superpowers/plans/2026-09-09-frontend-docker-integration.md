# Frontend Docker Integration Implementation Plan

> **审计说明（2026-10-29）** —— 这份 plan 的 checkbox 原本 33 个全是空的，但仓库里其实绝大部分都真做了。
>
> **目标**：把 `aiopc-frontend` 容器加进本地 Docker 栈，让 Vue 3 + TS 前端完全跑在 Docker 里，访问 `http://localhost:8079`，并把 `/prod-api/*` 反代到 `aiopc-gateway:8080`。
>
> **审计结果**：勾了 **31** / 没勾 **2** / 共 **33**。
>
> **没勾的 2 个都卡在「查不到运行输出」**：Task 1 Step 3（`npm run build:prod` 的构建输出）和 Task 6 Step 3（隔离冒烟 `docker run --rm` 的输出）—— 仓库里既没有 CI 记录，也没有这两条命令的日志文件。
>
> **判定口径**：产物能在仓库里指认（文件/配置/提交）→ 勾；只有「跑一下看输出」而仓库里查不到输出的 → 不勾；但仓库里**留有实测记录**时（`tmp_e2e/e2e_report.md`、docs/verification 下的报告、提交信息里的测试结论）按有记录勾。
>
> **重要偏差**：plan 写的架构是 `serve@14` + `serve.json`，**实际落地换成了 nginx**（`vue3-typescript/nginx.conf` + 两阶段 Dockerfile）—— `serve.json` 写完后 20 分钟就被删了。照这份 plan 重做会重造一个已被放弃的设计。逐条证据见 `docs/superpowers/plans/audit/2026-09-09-frontend-docker-integration.md`。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `aiopc-frontend` container to the local Docker stack so the Vue 3 + TS frontend runs entirely in Docker, accessible at `http://localhost:8079`, with `/prod-api/*` reverse-proxied to `aiopc-gateway:8080`.

**Architecture:** Single-stage Dockerfile using `node:20-alpine` + `serve@14`. `serve.json` configures SPA fallback (`/**` → `/index.html`) and reverse proxy (`/prod-api` → `http://aiopc-gateway:8080`). Compose file adds the service to the `aiopc` stack with `context: ../../vue3-typescript`.

**Tech Stack:** Docker Compose v2, node:20-alpine, serve@14, vue3-typescript (existing build output)

---

## File Structure

| File                                      | Action  | Responsibility                                           |
|-------------------------------------------|---------|----------------------------------------------------------|
| `deploy/Dockerfile.frontend`              | CREATE  | Container image build instructions                        |
| `vue3-typescript/serve.json`              | CREATE  | SPA fallback + reverse proxy config for `serve`           |
| `vue3-typescript/.dockerignore`           | CREATE  | Exclude node_modules, dist-before-build, .git from build context |
| `deploy/docker-compose.yml`               | EDIT    | Add `aiopc-frontend` service                             |
| (verification scripts inline below)       | -       | curl-based smoke tests                                    |

---

## Task 1: Pre-build verification — confirm `npm run build:prod` works locally

**Files:** None modified (verification only)

This task verifies the frontend builds successfully with current code. If this fails, all subsequent Docker tasks will fail too. Caught early.

- [x] **Step 1: Check `node_modules` exists**

Run:
```bash
cd D:/work-ai/0401-lumen-opc/vue3-typescript && ls node_modules/.package-lock.json 2>&1 | head -1
```
Expected: `node_modules/.package-lock.json` printed (file exists)

- [x] **Step 2: If missing, install dependencies** （注：Step 1 通过，本步按 plan 属条件步骤；node_modules 已就位，443 个目录项 + .package-lock.json 291KB）

Run only if Step 1 failed:
```bash
cd D:/work-ai/0401-lumen-opc/vue3-typescript && npm install --prefer-offline --no-audit --no-fund 2>&1 | tail -5
```
Expected: `added N packages` or similar — no error

- [ ] **Step 3: Run production build** （未完成：仓库里查不到 `npm run build:prod` 的运行输出；dist/ 被 .gitignore 忽略，是本机残留产物，不能证明本 plan 窗口内跑过这次构建）

Run:
```bash
cd D:/work-ai/0401-lumen-opc/vue3-typescript && npm run build:prod 2>&1 | tail -10
```
Expected: Last line includes `built in` (e.g., `built in 35.42s`)

- [x] **Step 4: Verify `dist/index.html` exists**

Run:
```bash
cd D:/work-ai/0401-lumen-opc/vue3-typescript && ls -la dist/index.html
```
Expected: file exists, size > 1KB

- [x] **Step 5: Spot-check production env baked into bundle** （注：实际产物在 dist/static/js/，不在 plan 写的 dist/assets/；dist/static/js/index-*.js 两个文件命中 prod-api）

Run:
```bash
cd D:/work-ai/0401-lumen-opc/vue3-typescript && grep -r "prod-api" dist/assets/ 2>&1 | head -3
```
Expected: at least one match (proves `VITE_APP_BASE_API=/prod-api` was baked in)

- [x] **Step 6: No commit (verification only) — proceed to Task 2** （注：git 历史里 2026-09-09 的第一个前端提交就是 Task 2 的 6172a41，Task 1 没留下任何提交）

---

## Task 2: Create `deploy/Dockerfile.frontend`

**Files:**
- Create: `D:\work-ai\0401-lumen-opc\springboot3\deploy\Dockerfile.frontend`

- [x] **Step 1: Write the Dockerfile** （注：6172a41 按 plan 原文写入；同日 16:03 的 2fda26e 改成 nginx 多阶段构建）

Create file `D:\work-ai\0401-lumen-opc\springboot3\deploy\Dockerfile.frontend` with exact content:

```dockerfile
# aiopc-frontend
# Single-stage: build context provides pre-built dist/ (run npm run build:prod locally first)
FROM node:20-alpine

LABEL maintainer="aiopc"

# serve@14 supports SPA fallback + reverse proxy via serve.json
# Image size: ~50MB (node:20-alpine) + ~5MB (serve) + ~20MB (dist) ≈ 75MB
RUN npm install -g serve@14.2.4 --registry=https://registry.npmmirror.com

WORKDIR /app

# Copy built SPA bundle (assumes build context root contains dist/)
COPY ./dist/ ./

# Copy serve config (SPA fallback + reverse proxy)
COPY ./serve.json ./

EXPOSE 8079

# -s: single-page mode (history fallback)
# -l: listen port
# --config: load serve.json
CMD ["serve", "-s", ".", "-l", "8079", "--config", "serve.json"]
```

- [x] **Step 2: Verify file written** （注：现文件首行仍是 `# aiopc-frontend`）

Run:
```bash
cat D:/work-ai/0401-lumen-opc/springboot3/deploy/Dockerfile.frontend | head -5
```
Expected: First line `# aiopc-frontend`

- [x] **Step 3: Commit** （注：6172a41，提交信息与 plan 写的完全一致）

Run:
```bash
cd D:/work-ai/0401-lumen-opc && git add springboot3/deploy/Dockerfile.frontend && git commit -m "feat(frontend): add Dockerfile.frontend for aiopc-frontend"
```
Expected: commit SHA returned, no errors

---

## Task 3: Create `vue3-typescript/serve.json`

**Files:**
- Create: `D:\work-ai\0401-lumen-opc\vue3-typescript\serve.json`

`serve.json` is consumed by `serve@14` to configure SPA routing fallback (history mode) AND reverse proxy for `/prod-api/*`.

- [x] **Step 1: Write `serve.json`** （注：6a7d0c6 按 plan 原文写入；同日 2fda26e 把它删了，改用 vue3-typescript/nginx.conf —— 现在仓库里没有这个文件）

Create file `D:\work-ai\0401-lumen-opc\vue3-typescript\serve.json` with exact content:

```json
{
  "public": ".",
  "cleanUrls": true,
  "rewrites": [
    { "source": "/**", "destination": "/index.html" }
  ],
  "headers": [
    {
      "source": "**/*.@(js|css)",
      "headers": [
        { "key": "Cache-Control", "value": "public, max-age=31536000, immutable" }
      ]
    }
  ],
  "proxies": [
    {
      "source": "/prod-api",
      "target": "http://aiopc-gateway:8080",
      "changeOrigin": true,
      "secure": false
    }
  ]
}
```

- [x] **Step 2: Validate JSON syntax** （注：把 6a7d0c6 里那个 blob 拉出来解析过，JSON 合法，键是 public/cleanUrls/rewrites/headers/proxies）

Run:
```bash
node -e "console.log(JSON.stringify(require('D:/work-ai/0401-lumen-opc/vue3-typescript/serve.json'),null,2))" 2>&1 | head -25
```
Expected: pretty-printed JSON, no error. If you see "Cannot find module", use absolute path with proper escaping OR run from the dir:
```bash
cd D:/work-ai/0401-lumen-opc/vue3-typescript && node -e "console.log(JSON.stringify(require('./serve.json'),null,2))" | head -25
```

- [x] **Step 3: Commit** （注：6a7d0c6，提交信息与 plan 写的完全一致）

Run:
```bash
cd D:/work-ai/0401-lumen-opc && git add vue3-typescript/serve.json && git commit -m "feat(frontend): add serve.json with SPA fallback + /prod-api proxy"
```
Expected: commit SHA returned

---

## Task 4: Create `vue3-typescript/.dockerignore`

**Files:**
- Create: `D:\work-ai\0401-lumen-opc\vue3-typescript\.dockerignore`

Without `.dockerignore`, Docker will copy `node_modules/` (~500MB) and `.git/` (~hundreds of MB) into the build context, making builds slow and bloated.

- [x] **Step 1: Write `.dockerignore`** （注：b547183 比 plan 少一行 `dist` —— Dockerfile 要 COPY ./dist/，排掉就构建不出镜像）

Create file `D:\work-ai\0401-lumen-opc\vue3-typescript\.dockerignore` with exact content:

```
node_modules
dist
.git
.gitignore
.env.local
.env.*.local
*.log
.DS_Store
coverage
.vitest-cache
.idea
.vscode
*.md
```

- [x] **Step 2: Verify**

Run:
```bash
ls -la D:/work-ai/0401-lumen-opc/vue3-typescript/.dockerignore
```
Expected: file exists

- [x] **Step 3: Commit** （注：b547183，提交信息与 plan 写的完全一致）

Run:
```bash
cd D:/work-ai/0401-lumen-opc && git add vue3-typescript/.dockerignore && git commit -m "chore(frontend): add .dockerignore to exclude node_modules and dist"
```
Expected: commit SHA returned

---

## Task 5: Add `aiopc-frontend` service to `deploy/docker-compose.yml`

**Files:**
- Modify: `D:\work-ai\0401-lumen-opc\springboot3\deploy\docker-compose.yml` (append new service)

- [x] **Step 1: Read current compose file end** （注：最终 compose 的 service 顺序正好是 aiopc-insight → aiopc-frontend → aiopc-gateway，与 Step 2 描述的插入点一致）

Run:
```bash
wc -l D:/work-ai/0401-lumen-opc/springboot3/deploy/docker-compose.yml
tail -10 D:/work-ai/0401-lumen-opc/springboot3/deploy/docker-compose.yml
```
Expected: file ends with `networks:` section. Get the current line count for the Edit.

- [x] **Step 2: Locate insertion point** （注：实测 compose 里 aiopc-frontend 的上一个服务是 aiopc-insight、下一个是 aiopc-gateway）

The `aiopc-insight` service ends around line 202 (`restart: unless-stopped`). We insert `aiopc-frontend` AFTER `aiopc-insight` and BEFORE `aiopc-gateway` (logical ordering: frontends first).

- [x] **Step 3: Insert `aiopc-frontend` service**

Edit `D:\work-ai\0401-lumen-opc\springboot3\deploy\docker-compose.yml` — find the line that reads:
```
  # ============ RuoYi Gateway（Spring Cloud Gateway）============
  aiopc-gateway:
```

Replace it with:
```
  # ============ Vue3 Frontend (SPA + nginx reverse-proxy) ============
  # Build context = ../../vue3-typescript (resolves relative to deploy/)
  # Dockerfile path is also relative to deploy/
  aiopc-frontend:
    build:
      context: ../../vue3-typescript
      dockerfile: ../springboot3/deploy/Dockerfile.frontend
    image: aiopc-frontend:latest
    container_name: aiopc-frontend
    ports:
      - "8079:8079"
    depends_on:
      - aiopc-gateway
    networks:
      - aiopc-net
    restart: unless-stopped

  # ============ RuoYi Gateway（Spring Cloud Gateway）============
  aiopc-gateway:
```

- [x] **Step 4: Validate YAML syntax** （注：审计不许跑 docker，改用静态解析：YAML 合法，services 里确实有 aiopc-frontend）

Run:
```bash
docker compose -p aiopc -f D:/work-ai/0401-lumen-opc/springboot3/deploy/docker-compose.yml config --services 2>&1 | head -20
```
Expected: list includes `aiopc-frontend` AND all other services. If error, fix YAML and retry.

- [x] **Step 5: Commit** （注：77d3475，提交信息与 plan 写的完全一致）

Run:
```bash
cd D:/work-ai/0401-lumen-opc && git add springboot3/deploy/docker-compose.yml && git commit -m "feat(deploy): add aiopc-frontend service to docker-compose"
```
Expected: commit SHA returned

---

## Task 6: Build the Docker image

**Files:** None modified (build only)

- [x] **Step 1: Build `aiopc-frontend` image** （注：仓库里没有 docker build 的原始输出；但 0768036（2026-09-09 18:18）记录 localhost:8079/prod-api/login 端到端返回 JWT，tmp_e2e/e2e_report.md（2026-09-10）走 8079 跑通 49/50 端点，证明镜像确实构建出来并在跑）

Run:
```bash
cd D:/work-ai/0401-lumen-opc/springboot3/deploy && docker compose -p aiopc build aiopc-frontend 2>&1 | tail -15
```
Expected: ends with `aiopc-frontend  Built`. First build ~2-3 minutes. If error, check Dockerfile syntax and that `vue3-typescript/dist/` and `vue3-typescript/serve.json` exist.

- [x] **Step 2: Verify image exists** （注：镜像确实存在（compose 里 image: aiopc-frontend:latest；2026-09-14 的 docker ps 快照见 docs/verification/week-49/opc-notification-week49-verification.md）；但 plan 估的 ~75MB 已过时 —— 最终镜像是 node:20-alpine + nginx:1.27-alpine 两阶段）

Run:
```bash
docker images aiopc-frontend --format "{{.Repository}}:{{.Tag}}\t{{.Size}}\t{{.CreatedSince}}"
```
Expected: `aiopc-frontend:latest	~75MB	Less than a minute ago`

- [ ] **Step 3: Smoke-test the image in isolation** （未完成：仓库里查不到那次 `docker run --rm` 隔离冒烟的输出；plan 期望的日志行 "Accepting connections at http://localhost:8079" 属于 serve@14 方案，实施当天 16:03 已换成 nginx，镜像启动日志不再有这行）

Run:
```bash
docker run --rm -d --name aiopc-frontend-smoke --network aiopc_aiopc-net -p 8079:8079 aiopc-frontend:latest 2>&1 | tail -2
sleep 5
curl -s -m 5 -o /dev/null -w "smoke=%{http_code}\n" http://localhost:8079/
docker logs aiopc-frontend-smoke 2>&1 | head -10
docker rm -f aiopc-frontend-smoke 2>&1 | tail -1
```
Expected: `smoke=200`, logs show `Accepting connections at http://localhost:8079`

---

## Task 7: Run via docker-compose + full integration test

**Files:** None modified

- [x] **Step 1: Bring up the service** （注：2026-09-10 的 tmp_e2e/e2e_report.md 与 2026-09-14 的 docker ps 快照都证明容器在跑）

Run:
```bash
cd D:/work-ai/0401-lumen-opc/springboot3/deploy && docker compose -p aiopc up -d --force-recreate --no-deps aiopc-frontend 2>&1 | tail -3
```
Expected: `Container aiopc-frontend  Started`

- [x] **Step 2: Verify container is healthy** （注：docker ps 快照：aiopc-frontend Up 12 hours 0.0.0.0:8079->8079/tcp）

Run:
```bash
sleep 8 && docker inspect --format='{{.State.Status}}' aiopc-frontend
```
Expected: `running`

- [x] **Step 3: Verify frontend serves SPA** （注：RECOVERY.md §8 验证清单记着「[x] 前端 http://localhost:8079 可访问」；不过页标题实际是 %VITE_APP_TITLE%，不是 plan 写的「若依管理系统」—— .env.production 里根本没定义 VITE_APP_TITLE）

Run:
```bash
curl -s -m 5 -o /dev/null -w "frontend_index=%{http_code}\n" http://localhost:8079/
curl -s http://localhost:8079/ | grep -E "<title>|<div id" | head -3
```
Expected: `frontend_index=200`, output contains `<title>若依管理系统</title>` (or similar)

- [x] **Step 4: Verify SPA route fallback works** （注：56e1e56（2026-10-22）记录实测 11 条 SPA 路由全部返回 index.html；同一天先加的裸路径反代一度把 /opc/invite 打成 404 NOT_FOUND，当日已修）

Run:
```bash
curl -s -m 5 -o /dev/null -w "spa_route=%{http_code}\n" http://localhost:8079/opc/invite
```
Expected: `spa_route=200` (404s fall back to index.html per serve.json)

- [x] **Step 5: Verify reverse proxy to gateway works** （注：0768036 提交记录：localhost:8079/prod-api/login 端到端返回 JWT token、captcha 链路同步修复；tmp_e2e/e2e_report.md 走 8079/prod-api 49/50 通过）

Run:
```bash
curl -s -m 5 -o /dev/null -w "captcha=%{http_code}\n" http://localhost:8079/prod-api/captchaImage
```
Expected: `captcha=200` (gateway→system works)

- [x] **Step 6: Verify Nacos discovery unaffected** （注：那次 Nacos service/list 的返回没留在仓库里；但断言可 grep 验证 —— compose 里 aiopc-frontend 没有任何 Nacos/Spring 环境变量，镜像是 nginx，不可能注册；plan 预期的「4 个服务」清单也已过时）

Run:
```bash
curl -s "http://localhost:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=50&namespaceId=opc-dev" | head -c 600
```
Expected: still shows `ruoyi-gateway`, `ruoyi-system`, `opc-user-center`, `opc-insight` (4 services — frontend does NOT register)

---

## Task 8: Final verification + commit

- [x] **Step 1: List all containers in stack** （注：docs/verification/week-49/opc-notification-week49-verification.md 里存着 docker ps 快照：aiopc-frontend Up 12 hours 0.0.0.0:8079->8079/tcp；plan 预期的「5 个容器」清单已过时，现在 compose 有 29 个服务）

Run:
```bash
docker ps --format '{{.Names}}\t{{.Status}}\t{{.Ports}}' | grep aiopc | sort
```
Expected: 5 containers — `aiopc-frontend`, `aiopc-gateway`, `aiopc-system`, `aiopc-user-center`, `aiopc-insight`

- [x] **Step 2: Test all 5 services return 200 on their key endpoints** （注：没有那份 for 循环的原始输出；但每条断言都有对应的实测记录 —— tmp_e2e/e2e_report.md（2026-09-10，走 8079/prod-api，49/50）、0768036 提交记录（captcha + login JWT）、week-49 health-check 输出（actuator/health UP））

Run:
```bash
for url in "http://localhost:8079/" "http://localhost:8080/captchaImage" "http://localhost:9201/actuator/health" "http://localhost:9302/actuator/health" "http://localhost:9306/actuator/health"; do
  code=$(curl -s -m 5 -o /dev/null -w "%{http_code}" "$url")
  printf "%-50s %s\n" "$url" "$code"
done
```
Expected: each line shows `200` or `404` (some `/actuator/health` may be disabled). At minimum `8079/` and `8080/captchaImage` must be 200.

- [x] **Step 3: Confirm all spec requirements met** （注：六条要求逐条对应上面 Task 7/8 的实测记录）

| Requirement | Verified in step |
|---|---|
| Frontend on port 8079 | Task 7 step 3 |
| SPA fallback works | Task 7 step 4 |
| /prod-api reverse proxy to gateway | Task 7 step 5 |
| Nacos discovery unaffected (frontend NOT registered) | Task 7 step 6 |
| Backend services still healthy | Task 8 step 1-2 |
| Container name aiopc-frontend | Task 7 step 2 |

- [x] **Step 4: Final commit (if any uncommitted changes)** （注：git status 干净，6 个实现提交都在历史里）

Run:
```bash
cd D:/work-ai/0401-lumen-opc && git status --short
```
If anything uncommitted, add and commit with message: `chore: final cleanup for aiopc-frontend integration`

---

## Self-Review

**Spec coverage:**
- ✅ Architecture (Section 2) → Task 1-5
- ✅ Components (Section 3) → Tasks 2, 3, 4, 5
- ✅ Data flow (Section 4) → Task 7 steps 3-6
- ✅ Error handling (Section 5) → Task 6 step 3 (smoke test catches image build errors)
- ✅ Testing (Section 6) → Tasks 6, 7, 8

**Placeholder scan:** No TBDs, no "implement later", all step commands shown.

**Type consistency:** All paths use `D:\work-ai\0401-lumen-opc\` prefix. All commands target the same compose stack. No name mismatches.

**Total tasks:** 8. Total file edits: 4 created + 1 edited = 5.
