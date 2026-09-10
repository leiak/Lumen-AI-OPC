# Frontend Docker Integration Implementation Plan

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

- [ ] **Step 1: Check `node_modules` exists**

Run:
```bash
cd D:/work-ai/0401-lumen-opc/vue3-typescript && ls node_modules/.package-lock.json 2>&1 | head -1
```
Expected: `node_modules/.package-lock.json` printed (file exists)

- [ ] **Step 2: If missing, install dependencies**

Run only if Step 1 failed:
```bash
cd D:/work-ai/0401-lumen-opc/vue3-typescript && npm install --prefer-offline --no-audit --no-fund 2>&1 | tail -5
```
Expected: `added N packages` or similar — no error

- [ ] **Step 3: Run production build**

Run:
```bash
cd D:/work-ai/0401-lumen-opc/vue3-typescript && npm run build:prod 2>&1 | tail -10
```
Expected: Last line includes `built in` (e.g., `built in 35.42s`)

- [ ] **Step 4: Verify `dist/index.html` exists**

Run:
```bash
cd D:/work-ai/0401-lumen-opc/vue3-typescript && ls -la dist/index.html
```
Expected: file exists, size > 1KB

- [ ] **Step 5: Spot-check production env baked into bundle**

Run:
```bash
cd D:/work-ai/0401-lumen-opc/vue3-typescript && grep -r "prod-api" dist/assets/ 2>&1 | head -3
```
Expected: at least one match (proves `VITE_APP_BASE_API=/prod-api` was baked in)

- [ ] **Step 6: No commit (verification only) — proceed to Task 2**

---

## Task 2: Create `deploy/Dockerfile.frontend`

**Files:**
- Create: `D:\work-ai\0401-lumen-opc\springboot3\deploy\Dockerfile.frontend`

- [ ] **Step 1: Write the Dockerfile**

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

- [ ] **Step 2: Verify file written**

Run:
```bash
cat D:/work-ai/0401-lumen-opc/springboot3/deploy/Dockerfile.frontend | head -5
```
Expected: First line `# aiopc-frontend`

- [ ] **Step 3: Commit**

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

- [ ] **Step 1: Write `serve.json`**

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

- [ ] **Step 2: Validate JSON syntax**

Run:
```bash
node -e "console.log(JSON.stringify(require('D:/work-ai/0401-lumen-opc/vue3-typescript/serve.json'),null,2))" 2>&1 | head -25
```
Expected: pretty-printed JSON, no error. If you see "Cannot find module", use absolute path with proper escaping OR run from the dir:
```bash
cd D:/work-ai/0401-lumen-opc/vue3-typescript && node -e "console.log(JSON.stringify(require('./serve.json'),null,2))" | head -25
```

- [ ] **Step 3: Commit**

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

- [ ] **Step 1: Write `.dockerignore`**

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

- [ ] **Step 2: Verify**

Run:
```bash
ls -la D:/work-ai/0401-lumen-opc/vue3-typescript/.dockerignore
```
Expected: file exists

- [ ] **Step 3: Commit**

Run:
```bash
cd D:/work-ai/0401-lumen-opc && git add vue3-typescript/.dockerignore && git commit -m "chore(frontend): add .dockerignore to exclude node_modules and dist"
```
Expected: commit SHA returned

---

## Task 5: Add `aiopc-frontend` service to `deploy/docker-compose.yml`

**Files:**
- Modify: `D:\work-ai\0401-lumen-opc\springboot3\deploy\docker-compose.yml` (append new service)

- [ ] **Step 1: Read current compose file end**

Run:
```bash
wc -l D:/work-ai/0401-lumen-opc/springboot3/deploy/docker-compose.yml
tail -10 D:/work-ai/0401-lumen-opc/springboot3/deploy/docker-compose.yml
```
Expected: file ends with `networks:` section. Get the current line count for the Edit.

- [ ] **Step 2: Locate insertion point**

The `aiopc-insight` service ends around line 202 (`restart: unless-stopped`). We insert `aiopc-frontend` AFTER `aiopc-insight` and BEFORE `aiopc-gateway` (logical ordering: frontends first).

- [ ] **Step 3: Insert `aiopc-frontend` service**

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

- [ ] **Step 4: Validate YAML syntax**

Run:
```bash
docker compose -p aiopc -f D:/work-ai/0401-lumen-opc/springboot3/deploy/docker-compose.yml config --services 2>&1 | head -20
```
Expected: list includes `aiopc-frontend` AND all other services. If error, fix YAML and retry.

- [ ] **Step 5: Commit**

Run:
```bash
cd D:/work-ai/0401-lumen-opc && git add springboot3/deploy/docker-compose.yml && git commit -m "feat(deploy): add aiopc-frontend service to docker-compose"
```
Expected: commit SHA returned

---

## Task 6: Build the Docker image

**Files:** None modified (build only)

- [ ] **Step 1: Build `aiopc-frontend` image**

Run:
```bash
cd D:/work-ai/0401-lumen-opc/springboot3/deploy && docker compose -p aiopc build aiopc-frontend 2>&1 | tail -15
```
Expected: ends with `aiopc-frontend  Built`. First build ~2-3 minutes. If error, check Dockerfile syntax and that `vue3-typescript/dist/` and `vue3-typescript/serve.json` exist.

- [ ] **Step 2: Verify image exists**

Run:
```bash
docker images aiopc-frontend --format "{{.Repository}}:{{.Tag}}\t{{.Size}}\t{{.CreatedSince}}"
```
Expected: `aiopc-frontend:latest	~75MB	Less than a minute ago`

- [ ] **Step 3: Smoke-test the image in isolation**

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

- [ ] **Step 1: Bring up the service**

Run:
```bash
cd D:/work-ai/0401-lumen-opc/springboot3/deploy && docker compose -p aiopc up -d --force-recreate --no-deps aiopc-frontend 2>&1 | tail -3
```
Expected: `Container aiopc-frontend  Started`

- [ ] **Step 2: Verify container is healthy**

Run:
```bash
sleep 8 && docker inspect --format='{{.State.Status}}' aiopc-frontend
```
Expected: `running`

- [ ] **Step 3: Verify frontend serves SPA**

Run:
```bash
curl -s -m 5 -o /dev/null -w "frontend_index=%{http_code}\n" http://localhost:8079/
curl -s http://localhost:8079/ | grep -E "<title>|<div id" | head -3
```
Expected: `frontend_index=200`, output contains `<title>若依管理系统</title>` (or similar)

- [ ] **Step 4: Verify SPA route fallback works**

Run:
```bash
curl -s -m 5 -o /dev/null -w "spa_route=%{http_code}\n" http://localhost:8079/opc/invite
```
Expected: `spa_route=200` (404s fall back to index.html per serve.json)

- [ ] **Step 5: Verify reverse proxy to gateway works**

Run:
```bash
curl -s -m 5 -o /dev/null -w "captcha=%{http_code}\n" http://localhost:8079/prod-api/captchaImage
```
Expected: `captcha=200` (gateway→system works)

- [ ] **Step 6: Verify Nacos discovery unaffected**

Run:
```bash
curl -s "http://localhost:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=50&namespaceId=opc-dev" | head -c 600
```
Expected: still shows `ruoyi-gateway`, `ruoyi-system`, `opc-user-center`, `opc-insight` (4 services — frontend does NOT register)

---

## Task 8: Final verification + commit

- [ ] **Step 1: List all containers in stack**

Run:
```bash
docker ps --format '{{.Names}}\t{{.Status}}\t{{.Ports}}' | grep aiopc | sort
```
Expected: 5 containers — `aiopc-frontend`, `aiopc-gateway`, `aiopc-system`, `aiopc-user-center`, `aiopc-insight`

- [ ] **Step 2: Test all 5 services return 200 on their key endpoints**

Run:
```bash
for url in "http://localhost:8079/" "http://localhost:8080/captchaImage" "http://localhost:9201/actuator/health" "http://localhost:9302/actuator/health" "http://localhost:9306/actuator/health"; do
  code=$(curl -s -m 5 -o /dev/null -w "%{http_code}" "$url")
  printf "%-50s %s\n" "$url" "$code"
done
```
Expected: each line shows `200` or `404` (some `/actuator/health` may be disabled). At minimum `8079/` and `8080/captchaImage` must be 200.

- [ ] **Step 3: Confirm all spec requirements met**

| Requirement | Verified in step |
|---|---|
| Frontend on port 8079 | Task 7 step 3 |
| SPA fallback works | Task 7 step 4 |
| /prod-api reverse proxy to gateway | Task 7 step 5 |
| Nacos discovery unaffected (frontend NOT registered) | Task 7 step 6 |
| Backend services still healthy | Task 8 step 1-2 |
| Container name aiopc-frontend | Task 7 step 2 |

- [ ] **Step 4: Final commit (if any uncommitted changes)**

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
