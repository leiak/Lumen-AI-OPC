# Nacos 配置管理（opc-prod 命名空间）

## 命名空间

| 命名空间 ID       | 用途               | DataID 数量 |
|------------------|-------------------|------------|
| `opc-prod`       | 生产环境           | 7          |
| `opc-staging`    | 预发环境（参考 prod） | 7        |
| `opc-dev`        | 开发环境           | 7（已部署） |

> 命名空间需先在 Nacos 控制台 → 命名空间 → 新建，拿到 namespaceId（UUID 格式）。

## DataID 清单（prod）

| DataID                    | 作用                              | 共享/私有 |
|--------------------------|----------------------------------|----------|
| `application-prod.yml`    | 全局共享：DB/redis/rabbit/qdrant/feign/sentinel/jasypt | 共享 |
| `opc-common-prod.yml`    | 跨服务通用：灰度/CORS/限流/审计/追踪 | 共享 |
| `opc-ai-core-prod.yml`    | AI 中台特有                       | 私有 |
| `opc-agent-hub-prod.yml`  | Agent Hub 特有                   | 私有 |
| `opc-user-center-prod.yml`| 用户中心特有                     | 私有 |
| `opc-billing-prod.yml`   | 计费特有                          | 私有 |
| `opc-finance-prod.yml`   | 财务特有                          | 私有 |

## 导入/导出/加密

```bash
# 1. 加密敏感字段（输出 ENC(...) 格式，直接粘到 YAML）
export JASYPT_PASSWORD='xxx'
./encrypt-secret.sh 'my-secret'

# 2. 导入到 Nacos
export NACOS_ADDR=nacos.example.com:8848
export NACOS_USER=nacos
export NACOS_PASS='xxx'
export NAMESPACE_ID=<opc-prod namespaceId>
./import-prod.sh

# 3. 备份 Nacos 配置到本地
./export-prod.sh ./backup-2026-09-03
```

## 启动期校验（fail-fast）

`opc-common` 模块注册的 `JasyptEnvironmentPosture` 在 prod profile 启动期执行：

1. **必须设置 JASYPT_PASSWORD** 环境变量（拒绝默认密码）
2. **禁止明文敏感字段**——以下占位符出现即抛 `IllegalStateException`：
   - `OpcEncrypt!2026`（默认密码）
   - `password: root`
   - `api-key: sk-placeholder`

任一校验失败 → Spring Boot 退出码非 0，CI 流水线红灯。

## 网关白名单补丁(可选)

邀请落地页 `/opc/invite?code=XXX` 公开访问 `/opc/user/invitations/{code}`,
需在 Nacos `application-{env}.yml` 的 `security.ignore.whites` 加白。
提供:

- `whitelist-opc-invite.yml` — 合并片段(含完整 12 条白名单)
- `apply-whitelist.sh` — 一键推 Nacos 脚本(支持 `DRY_RUN=1`)

```bash
DRY_RUN=1 ./apply-whitelist.sh dev   # 预览
./apply-whitelist.sh prod            # 应用到 prod
```

⚠️ 已知限制:`whites` 是 path-only 不支持 HTTP method 维度,
`/opc/user/invitations/*` 会**同时豁免** POST /generate 和 POST /accept;
但下游 controller 在缺少 userId 时抛 500,**不会**真正执行业务。长期方案
待 `IgnoreWhiteProperties` 增加 method+path 字段。

## 故障排查

### 启动报错 `Could not decrypt`
原因：`JASYPT_PASSWORD` 与加密时不一致，或加密后没重新导入 Nacos。
处理：用 `./encrypt-secret.sh --decrypt 'ENC(...)'` 验证能否解出明文。

### 应用启动后立即退出
检查日志中是否含 `[FATAL] prod profile ...`，按提示修复。

### 邀请落地页打开被 401 拦截
原因:`security.ignore.whites` 没加 `/opc/user/invitations/*`。
处理:跑 `./apply-whitelist.sh dev`(或 prod),然后等 30s 让 gateway 刷新。

### 路由 404 (`/opc/**` 全部不通)

**路由的唯一事实来源是网关自带的配置**:`springboot3/ruoyi-gateway/src/main/resources/application.yml`
的 `spring.cloud.gateway.server.webflux.routes`(第 29 行起)。Nacos 的
`application-{env}.yml` 不定义 routes,不会覆盖它 —— 所有环境共用这一份。
Nacos 在这里只管 DataID / Redis / DB / 白名单,不碰路由。

> ⚠️ 本目录曾放过一份 JSON 路由清单(6 条服务、每条带 `StripPrefix=1`、`lb://` 寻址),
> 它停留在"路由还挂在 Nacos 里"的那个年代,与上述配置早已分叉,**现已删除**。
> **不要**再把它转成 routes 节点发回 Nacos,也不要手写 `application.yml` 片段用
> `curl POST /v1/cs/configs` 覆盖网关配置 —— 那会用一份 6 条、剥前缀、`lb://` 的旧清单
> 顶掉现行 14 条 `http://aiopc-*:93xx` 路由,顺带打掉通知 / CRM / HR / ERP / 内容 /
> 社区 / 市场 8 个服务的入口,并让所有 controller 的全路径 `@RequestMapping("/opc/…")` 对不上。
> 这与 `apply-whitelist.sh` 那次「整体替换白名单,一次摧毁 35 条」是同一类事故:
> **整体替换 ≠ 合并**。

排查顺序(先确认网关活着,再怀疑路由):

1. **网关容器起来了吗**:`deploy/deploy.sh status` 或 `docker compose ps`;
   日志应有 `Started RuoYiGatewayApplication`。启动阶段直接退出就先修异常,此时外层
   看到的 404/502 与 routes 无关。
2. **Nacos 拉到了吗**:网关 `bootstrap.yml` 是 `fail-fast: true`,拉不到配置进程直接退出。
   日志搜 `OpcNacosStartupChecker` / `/nacos/v1/cs/health`。
3. **看 404 的形态**:网关日志里有
   `reactor.netty.channel.ChannelObserver ... 404`,说明**路由匹配上了,是下游容器没起**
   —— 去查对应服务的容器/IP;日志里**根本没有**这一行,说明请求压根没匹配上 `routes`
   里的 Path,回到 `application.yml` 第 29 行起的 routes 核对路径写法。
4. **确要改路由**:改 `springboot3/ruoyi-gateway/src/main/resources/application.yml`
   并重建网关镜像。改之前先数一遍现有 OPC 路由条数(现行 14 条),改完再数一遍,
   少一条就是回归 —— 注意 OPC 路由一律**不加** `StripPrefix`。