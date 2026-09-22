# W79 Remote SSH OPC Full Stack Deploy — VERIFICATION

> **范围**: 单脚本一键部署 22 容器到 Rocky Linux 8.10 VM
> **状态**: ✅ 设计 + 计划 + 实施全部完成; VM 实跑需用户 SSH 验证
> **日期**: 2026-09-22

---

## 1. 交付清单

| # | 文件 | 行数 | 提交 |
|---|---|---|---|
| 1 | `springboot3/deploy/scripts/backup-mysql-local.sh` | 110 | `cd694b9` + 2 fixes |
| 2 | `springboot3/deploy/scripts/bootstrap-remote.sh` | ~530 | `1467690` ... `932341e` (10 commits) |
| 3 | `springboot3/deploy/RECOVERY.md` §15 | +121 | `49f0780` |
| 4 | `springboot3/deploy/.env.example` | +58 | `fda7b5e` |
| 5 | `docs/superpowers/specs/2026-09-22-remote-ssh-opc-deploy-design.md` | ~600 | `8f5c939` + 2 fixes |
| 6 | `docs/superpowers/plans/2026-09-22-remote-ssh-opc-deploy.md` | 1290 | `e72b840` |

---

## 2. 单测 / 静态校验

| 检查项 | 结果 |
|---|---|
| `bash -n backup-mysql-local.sh` | SYNTAX_OK |
| `bash -n bootstrap-remote.sh` | SYNTAX_OK (最终 932341e) |
| `bash -n import-dev.sh` | SYNTAX_OK (沿用 W47) |
| Git: 工作区干净 | ✅ (post-push) |
| Git: commit 历史线性 | ✅ (10 commits on main) |

---

## 3. 5 阶段退出码语义

| 阶段 | 任务 | 退出码 |
|---|---|---|
| 0 | preflight (Rocky / disk / .env / migrate tar / sha256 / env keys) | 1 |
| 1 | install Docker CE | 1 |
| 2 | deps (git/curl/python3) + SELinux/permissive + firewalld + git clone | 1 |
| 3 | 起 6 基建容器 (nacos1/mysql/redis/rabbitmq/minio/qdrant) | 1 |
| 4 | restore MySQL + 推 Nacos + 起 13 业务容器 | **2** (restore fail, auto-rollback) / **3** (compose up fail) |
| 5 | health-check + 5 smoke tests | **4** |

✅ 用户可按 `exit_code` 区分"preflight 错了"vs"restore 错了"vs"compose up 错了"vs"健康检查没过"

---

## 4. 端口映射 (宿主机 VM 视角)

| 容器 | 容器端口 | 宿主机端口 | 备注 |
|---|---|---|---|
| aiopc-elasticsearch | 9200 | **9200** | (not auth!) |
| aiopc-auth | 9200 | **19200** | host 9200 被 ES 占 |
| aiopc-gateway | 8080 | **8080** | 暴露给用户 |
| aiopc-frontend | 8079 | **8079** | 暴露给用户 |
| aiopc-nacos1 | 8848/9848 | **8848/9848** | 暴露给用户 |
| aiopc-mysql | 3306 | 3308 | host:3308 → container:3306 |
| aiopc-redis | 6379 | 6379 | |
| aiopc-rabbitmq | 5672/15672 | 5672/15672 | |
| aiopc-minio | 9000/9001 | 9000/9001 | |
| aiopc-qdrant | 6333/6334 | 6333/6334 | |
| 业务 13 svc | 9301-9325 | 同容器端口 | 不暴露 host,网关代理 |

✅ 用户 SSH 端口转发只需开 3 个: **8079 / 8080 / 8848**

---

## 5. 数据迁移闭环

```
本机 (Windows/Mac/Linux)
  ↓ backup-mysql-local.sh
  aiopc-migrate-YYYYMMDD-HHMMSS.tar.gz (含 mysql-dump.sql.gz + nacos/*.yml)
  ↓ scp
VM /tmp/aiopc-staging/
  ↓ bootstrap-remote.sh stage 4
  docker exec aiopc-mysql mysql < dump.sql
  ↓
  ${DATA_ROOT}/backups/pre-restore-*.sql.gz (自动备份,restore 失败时回滚)
```

✅ 双侧 idempotent: 本机 tar 已存在会拒绝覆写;VM `opc_* >= 10` 表自动 skip restore

---

## 6. 已知限制

| 项 | 说明 | 影响 |
|---|---|---|
| LLM API key 留空 | ai-core /v1/chat/completions 返 401 | 其他 12 个服务正常 |
| ES / Grafana / SkyWalking / Prometheus | 不在 W79 最小基建里 | 需要时单独加 compose service |
| 首次镜像 build | 25-40 min (2-4 vCPU) | 一次性 |
| VM disk | 必须 ≥ 40 GB,推荐 80 GB | 增量备份留余地 |
| LLM providers | DeepSeek + MiniMax (W48.6 已固化) | 其他 provider 需改 `OpcLlmController.models()` |

---

## 7. 安全模型

- **无外部 SSH 凭据泄漏**: Claude 不持有 VM 账号/密码/密钥,用户自跑
- **`.env` git ignored**: API key + MySQL pwd 不会进仓库
- **MySQL 密码一致性**: `MYSQL_ROOT_PASSWORD` 与 `COMPOSE_MYSQL_PWD` 必须在 `.env` 保持一致
- **Jasypt 加密**: 沿用 W47,生产 `JASYPT_PASSWORD` 走环境变量
- **数据卷 host bind mount**: `/opt/aiopc-data/` 持久化,容器删除不丢数据

---

## 8. 与既有部署的关系

| 工具 | 角色 | 关系 |
|---|---|---|
| `deploy.sh` (W50 固化) | 本机一键拉起 | W79 是它的"远程 VM 替代品" |
| `deploy/scripts/health-check.sh` (W47) | 36+ 项健康检查 | W79 stage 5 直接复用 |
| `deploy/scripts/backup-mysql-local.sh` (W79 新) | 本机 MySQL + Nacos 导出 | 新文件,与 W48.7 backup-mysql.sh 是姐妹脚本 |
| `deploy/scripts/bootstrap-remote.sh` (W79 新) | VM 端 5 阶段 orchestrator | 新文件 |
| `RECOVERY.md` | 故障恢复手册 | §15 新增,W79 用户入门 |
| `springboot3/deploy/nacos/import-dev.sh` (W47) | 推 Nacos dev 配置 | W79 stage 4 直接复用 |

---

## 9. 下一步 (用户操作)

1. **复制 `.env.example` 到 `.env`**, 填真值
2. **本机** 跑 `bash scripts/backup-mysql-local.sh` 生成 `aiopc-migrate-*.tar.gz`
3. **scp** 到 VM `/tmp/`
4. **VM** 跑 `bash scripts/bootstrap-remote.sh`
5. **等 25-40 min** (首次 build) 后看 banner
6. **SSH 端口转发** `ssh -L 8079:127.0.0.1:8079 -L 8080:127.0.0.1:8080 -L 8848:127.0.0.1:8848 user@<vm-ip>`
7. **浏览器** `http://localhost:8079` 验证

---

## 10. 故障排查速查

| 现象 | 原因 | 修复 |
|---|---|---|
| `Rocky 8.10 required` | OS 不匹配 | 用 Rocky 8.10 (CentOS 8 EOL) |
| `Disk space < 40 GB` | VM disk 太小 | 加 disk 或选更大 VM |
| `migrate tar missing` | scp 失败 | 检查 `/tmp/aiopc-staging/` |
| `MySQL 120s 内未就绪` | initdb 首次跑 ~60s | 等待或 `docker logs aiopc-mysql` |
| `restore failed` (exit 2) | dump 损坏 | `${DATA_ROOT}/backups/pre-restore-*.sql.gz` 回滚 |
| `compose up failed` (exit 3) | Dockerfile 错或端口占用 | `docker compose logs aiopc-<svc>` |
| `health check failed` (exit 4) | 服务没全起 | `bash scripts/health-check.sh` 单独跑 |
| `/login 失败` (smoke 1) | gateway→auth 路由不通 | `docker logs aiopc-gateway` |

---

**End of W79 VERIFICATION**
