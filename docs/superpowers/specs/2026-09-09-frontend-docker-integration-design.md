# Frontend Docker Integration Design

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `aiopc-frontend` container to the local Docker stack so the Vue 3 + TS frontend runs entirely in Docker alongside the 4 backend services.

**Architecture:** Single-stage Dockerfile using `node:20-alpine` + `serve` to host the built SPA. Nginx reverse-proxy `/prod-api/*` to `aiopc-gateway:8080` inside the same `aiopc-net` Docker network. No changes to frontend source or build config — `VITE_APP_BASE_API = '/prod-api'` is already correct.

**Tech Stack:** Docker Compose v2, node:20-alpine, `serve` (npm), vue3-typescript (existing), nginx 1.27 in container (alpine variant)

---

## 1. Context

### Current state (W46)
Local Docker stack `aiopc` (compose file `springboot3/deploy/docker-compose.yml`) runs 4 backend services:

| container         | port | Nacos registered |
|-------------------|------|------------------|
| aiopc-gateway     | 8080 | yes              |
| aiopc-system      | 9201 | yes              |
| aiopc-user-center | 9302 | yes              |
| aiopc-insight     | 9306 | yes              |

Frontend lives at `vue3-typescript/` (sibling of `springboot3/` at repo root). Today the user runs it manually via `npm run dev` on port 8081, which proxies `/dev-api/*` to `localhost:8080` (gateway) or `localhost:9306` (insight fallback).

### Goal
- Frontend runs as a container `aiopc-frontend` in the `aiopc` stack
- Accessible via `http://localhost:8079` (single host port, container runs on same)
- Production build (uses `/prod-api`) so frontend's calls flow through gateway just like in any deployment
- No source changes to the frontend project

### Non-goals
- No CI/CD pipeline changes
- No staging/prod env config
- No frontend feature work

---

## 2. Architecture

### Network topology

```
                ┌─────────────────────────────────────────┐
                │           aiopc-net (bridge)            │
                │                                         │
   host:8079 ──►│  aiopc-frontend ──► aiopc-gateway:8080 │
                │       (8079)        │                   │
                │                     ▼                   │
                │               aiopc-system:9201        │
                │               aiopc-user-center:9302   │
                │               aiopc-insight:9306       │
                └─────────────────────────────────────────┘
```

### Service responsibilities

| container         | port | responsibility                                |
|-------------------|------|-----------------------------------------------|
| aiopc-frontend    | 8079 | Static SPA + nginx reverse-proxy `/prod-api/*` |
| aiopc-gateway     | 8080 | Spring Cloud Gateway — routes to backend svcs |
| aiopc-system      | 9201 | RuoYi built-in (users/roles/menus)            |
| aiopc-user-center | 9302 | OPC user center (invitation, company)         |
| aiopc-insight     | 9306 | OPC AI insight                                |

### Reverse-proxy rule (nginx inside aiopc-frontend)

```
location /prod-api/ {
    proxy_pass http://aiopc-gateway:8080/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
location / {
    root /app/dist;
    try_files $uri $uri/ /index.html;
}
```

### Why `/prod-api` and not `/dev-api`
- `vue3-typescript/.env.production` sets `VITE_APP_BASE_API = '/prod-api'`
- `vue3-typescript/.env.development` sets `VITE_APP_BASE_API = '/dev-api'`
- `npm run build:prod` reads `.env.production` → built bundle hard-codes `/prod-api`
- The 4 backend services already register in Nacos under their application names (no `/prod-api` prefix at service discovery) — gateway handles that prefix transformation via its route rules (already in `ruoyi-gateway-dev.yml`).

---

## 3. Components

### 3.1 `deploy/Dockerfile.frontend` (new file)

```dockerfile
FROM node:20-alpine

LABEL maintainer="aiopc"

# serve 包支持 SPA 路由（history mode fallback → index.html）+ 反代能力
# 用 npm 全局安装，镜像 ~150MB（node:20-alpine base ~50MB + serve ~5MB + dist ~20MB）
RUN npm install -g serve@14

WORKDIR /app

# 把构建产物拷贝进来
COPY ./dist/ ./

# 反代配置 / 默认 SPA 路由
COPY ./serve.json ./

EXPOSE 8079

# serve.json 控制路由 fallback + proxy
CMD ["serve", "-s", ".", "-l", "8079", "--config", "serve.json"]
```

### 3.2 `vue3-typescript/serve.json` (new file)

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

### 3.3 `vue3-typescript/.dockerignore` (new file)

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
```

### 3.4 `deploy/docker-compose.yml` — additive change

Append a new service `aiopc-frontend`. Place it AFTER `aiopc-insight` and BEFORE `aiopc-gateway` (logical ordering: frontends first, then backends).

```yaml
  # ============ Vue3 Frontend (SPA) ============
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
```

**Gotcha:** `context: ../../vue3-typescript` resolves relative to the compose file location (`springboot3/deploy/`). The `dockerfile` path is also relative to the compose file. This is the standard pattern — DO NOT use absolute paths (breaks on different host OSes).

### 3.5 What we DO NOT touch

- `vue3-typescript/vite.config.ts` — unchanged
- `vue3-typescript/package.json` — unchanged
- `vue3-typescript/.env.production` — unchanged
- `ruoyi-gateway-dev.yml` (in Nacos) — unchanged; gateway already strips `/prod-api` prefix
- All other backend services — unchanged

---

## 4. Data flow

### Login flow (end-to-end)

```
Browser (localhost:8079)
    │
    ├── GET /index.html ──► nginx:8079 ──► SPA bundle
    │
    ├── GET /prod-api/captchaImage ──► proxy_pass ──► aiopc-gateway:8080
    │                                       │
    │                                       └──► aiopc-system:9201 (SysLoginController)
    │
    └── POST /prod-api/login ──► proxy_pass ──► aiopc-gateway:8080
                                          │
                                          └──► ruoyi-auth (NOT in stack — login returns 404 today)
```

Note: `/login` endpoint is in `ruoyi-auth` module which is NOT deployed. The frontend cannot fully login until `ruoyi-auth` is added. This design does NOT fix that — it's out of scope. The captcha endpoint DOES work via gateway→system.

### Static asset flow

```
Browser GET /assets/index-abc123.js
    │
    └──► nginx reads /app/dist/assets/index-abc123.js (immutable cache)
```

---

## 5. Error handling

| Failure mode                          | Symptom                        | Mitigation                                |
|---------------------------------------|--------------------------------|-------------------------------------------|
| `npm run build:prod` fails            | `dist/` empty or missing       | Build verified locally before docker build |
| `aiopc-gateway` not running           | `/prod-api/*` returns 502      | `depends_on: aiopc-gateway` orders startup |
| `serve.json` JSON syntax error        | Container exits immediately    | JSON validated before commit              |
| Port 8079 already in use on host      | Container restart loop         | Document in compose comments             |
| Frontend image rebuild slow           | First build ~2-3 min           | Acceptable for local dev                 |

---

## 6. Testing & verification

### Local pre-build (one-time)
```bash
cd D:/work-ai/0401-lumen-opc/vue3-typescript
npm install
npm run build:prod
ls dist/index.html  # must exist
```

### Docker build
```bash
cd D:/work-ai/0401-lumen-opc/springboot3/deploy
docker compose -p aiopc build aiopc-frontend
```

### Docker run + verify
```bash
docker compose -p aiopc up -d --force-recreate --no-deps aiopc-frontend
sleep 10
docker inspect --format='{{.State.Status}}' aiopc-frontend   # expect: running
curl -s -m 5 -o /dev/null -w "frontend=%{http_code}\n" http://localhost:8079/   # expect: 200
curl -s -m 5 -o /dev/null -w "captcha=%{http_code}\n" http://localhost:8079/prod-api/captchaImage   # expect: 200
curl -s http://localhost:8079/ | head -20   # expect: <html>...<title>若依</title>...
```

### Failure diagnostic
```bash
docker logs aiopc-frontend --tail 50
docker exec aiopc-frontend ls /app/dist/index.html   # verify build copied
docker exec aiopc-frontend cat /app/serve.json | python -m json.tool
```

---

## 7. File change summary

| File                                 | Action | Lines |
|--------------------------------------|--------|-------|
| `deploy/Dockerfile.frontend`         | CREATE | ~15   |
| `vue3-typescript/serve.json`         | CREATE | ~20   |
| `vue3-typescript/.dockerignore`       | CREATE | ~10   |
| `deploy/docker-compose.yml`          | EDIT   | +20   |
| (no other changes)                   |        |       |

**Total: ~65 lines across 4 files.**

---

## 8. Out of scope (explicitly deferred)

1. **`ruoyi-auth` deployment** — login endpoint cannot work until this is in the stack. Separate spec needed.
2. **HTTPS / TLS termination** — local dev HTTP only. Prod would use Caddy or Nginx ingress.
3. **CDN / static asset hosting** — single-container static serve is fine for local.
4. **Multi-stage build** — user explicitly chose single-stage node + serve.
5. **Frontend code-splitting optimization** — vite defaults are fine.
6. **HMR / hot-reload in container** — local dev still uses `npm run dev` for that.
