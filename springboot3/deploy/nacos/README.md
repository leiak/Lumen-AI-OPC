# Nacos 配置管理

## 命名空间

| 命名空间 ID       | 用途                  | DataID 数量 | 状态                     |
|------------------|-----------------------|------------|--------------------------|
| `opc-dev`        | 开发 / 演示环境（compose 全栈共用） | 10         | **已建、已推、已生效**   |
| `opc-prod`       | 生产环境              | 7          | 仅本目录有 prod 模板，未部署 |
| `opc-staging`    | 预发环境（参考 prod）  | 7          | 未部署                   |

> 命名空间 ID 是**自定义字符串**（`opc-dev`），不是 UUID。
> Nacos 控制台 → 命名空间 → 新建时填「命名空间 ID」= `opc-dev` 即可，
> 服务端认的就是这个字符串。

⚠️ **命名空间在 Nacos 里有两处，只建一处等于没建**：

| 位置              | 作用                                        | 建了会怎样                       |
|------------------|---------------------------------------------|----------------------------------|
| `tenant_info`     | 命名空间的「户口本」，控制台的命名空间列表读它 | 控制台里能看见这一栏              |
| `config_info.tenant_id` | 配置实际存哪一栏                        | 服务端按 `tenant=opc-dev` 拉得到 |

老脚本只推配置、没建户口本，于是「配置其实躺在别处、控制台上一栏都看不见」。
`./create-namespace.sh` 补的就是 `tenant_info` 那一行。

## ⚠️ 配置 API 的参数陷阱（踩过很多次）

**Nacos 配置 API 的命名空间参数叫 `tenant`，不叫 `namespaceId`。**

| API                    | 参数              | 说明                                  |
|------------------------|-------------------|---------------------------------------|
| 配置读写 `/v1/cs/configs` | **`tenant=`**      | 用错成 `namespaceId=` 会被**静默忽略**，回落到默认 tenant(`''`) |
| 命名空间管理 `/v1/console/namespaces` | `namespaceId=` / `customNamespaceId=` | 建命名空间时用这个 |

后果：推送和回读**都**用 `namespaceId=` 时，两边一起回落到 public 栏，
md5 照样一致 —— 脚本一路 `[OK]`，但配置压根没进目标命名空间。
`./import-dev.sh` 现在推送和回读**都**用 `tenant=`，回读不到就直接报错。

另一个坑：`tenant=public` 会被服务端归一化成 `''`。
历史上由 `nacos_namespace_migrate` 写进去、`tenant_id='public'` 的那批行
（RuoYi 自带的 9 份），**任何 API 调用都读不到** —— 改不回来，只能当历史遗留。

## dev 环境怎么推

```bash
cd springboot3/deploy/nacos

# 1. 确保 opc-dev 命名空间存在（幂等；只补 tenant_info 的户口本）
./create-namespace.sh

# 2. 推 10 份配置（9 份 dev yml + 网关的 Sentinel 规则表），并按 tenant= 回读逐行校验
./import-dev.sh

# 灰度：只推其中几份
./import-dev.sh opc-common-dev.yml
```

`import-dev.sh` 推完会**回读比对内容**，对不上直接 `[FAIL]` 退出 1。
改完仓库里的 yml 一定要重推 —— `deploy/scripts/health-check.sh` 第 22 节会
盯着「Nacos 里的内容 == 仓库里的内容」这条不变量。

### 仓库文件名 ≠ DataID（唯一的一处例外）

| 仓库文件                        | Nacos DataID                | type  |
|---------------------------------|-----------------------------|-------|
| `application-dev.yml` 等 9 份   | 同名                        | yaml  |
| `sentinel-ruoyi-gateway.json`   | **`sentinel-ruoyi-gateway`** | json |

网关的 `bootstrap.yml` 里写死的是 `dataId: sentinel-ruoyi-gateway`（**不带扩展名**）——
仓库里的文件叫 `.json` 只是为了让编辑器把它当 JSON 打开。

这里要是图省事直接拿文件名当 DataID 推上去，Nacos 里就会多出一份
`sentinel-ruoyi-gateway.json`，而网关找的是不带扩展名的那个 —— **规则永远读不到，
而配置中心那几节还都显示「正常」**。`import-dev.sh` 里的 `dataid_of()` 就是
专门挡这一下的。

`type`（yaml / json）只是 Nacos 控制台决定用哪个编辑器打开的元数据，
**不影响 Sentinel 解析**——Sentinel 那边认的是 `bootstrap.yml` 里的
`data-type: json`。但把规则表标成 yaml 让人用 YAML 编辑器打开 JSON，很别扭。

## dev 的 DataID 清单（10 份）

| DataID                      | 谁会拉它                                    | 作用                        |
|-----------------------------|---------------------------------------------|-----------------------------|
| `application-dev.yml`       | 几乎所有服务                                | 全局共享                    |
| `opc-common-dev.yml`        | 除网关/社区外都拉                            | 跨服务通用（租户开关等）     |
| `opc-notification-dev.yml`  | opc-notification                            | 单服务私有                  |
| `opc-crm-dev.yml`           | opc-crm                                     | 单服务私有                  |
| `opc-community-dev.yml`     | opc-community                               | 单服务私有                  |
| `opc-marketplace-dev.yml`   | opc-marketplace                             | 单服务私有                  |
| `opc-hr-dev.yml`            | opc-hr                                      | 单服务私有                  |
| `opc-erp-dev.yml`           | opc-erp                                     | 单服务私有                  |
| `opc-content-dev.yml`       | opc-content                                 | 单服务私有                  |
| `sentinel-ruoyi-gateway`    | 网关的 Sentinel 数据源（`gw-flow` 流控规则） | **17 条规则**，一一对应网关 17 条路由 |

`opc-ai-core` / `opc-agent-hub` / `opc-user-center` / `opc-billing` / `opc-finance`
没有各自的 dev 文件：它们的私有配置（`ai.*` / `langchain4j.*` / `qdrant.*` 等）
由 `application-dev.yml` 覆盖。

> 每个服务实际会拉的 DataID 由它自己 `bootstrap.yml` 的 `spring.config.import` 决定。
> 注意 `opc-community` **不**拉 `opc-common-dev.yml`，网关**不**拉 `opc-common-dev.yml`。
> 网关的 Sentinel 数据源不走 `config.import`，它自己按 `dataId=sentinel-ruoyi-gateway`
> 去 Nacos 取流控规则（`groupId=DEFAULT_GROUP`、`namespace=opc-dev`）。

### 被清掉的同名残留（历史遗留）

老脚本用 `namespaceId=` 推的那 7 份配置，一直躺在**默认 tenant**（`''`）里，
和 `opc-dev` 里的同名两份并存。现已删除，内容逐字保存在 git 里：

| DataID                      | 内容在 git 的哪个版本                          |
|-----------------------------|-----------------------------------------------|
| `application-dev.yml`       | `48abbee^`                                    |
| `opc-notification-dev.yml`  | `48abbee^`                                    |
| `opc-community-dev.yml`     | `48abbee^`                                    |
| `opc-hr-dev.yml`            | `48abbee^`                                    |
| `opc-erp-dev.yml`           | `48abbee^`                                    |
| `opc-content-dev.yml`       | `48abbee^`                                    |
| `opc-crm-dev.yml`           | `18c7b90`（比别的更旧，还带着已废弃的 base-url 块） |

要取回：`git show 48abbee^:springboot3/deploy/nacos/application-dev.yml`。


### 网关的 Sentinel 限流规则表（唯一一份 JSON）

DataID `sentinel-ruoyi-gateway` 是这 10 份里**唯一不走 `config.import`** 的：
Sentinel 客户端自己去 Nacos 取（`groupId=DEFAULT_GROUP`、`namespace=opc-dev`、
`data-type=json`、`rule-type=gw-flow`），网关的 `bootstrap.yml` 里写死了这个 dataId。

#### `resource` 是**路由 id**，不是 URL 路径

这是最容易配错的一处。Sentinel 1.8.9 的 `SentinelGatewayFilter` 建 Entry 时，
资源名用的是 `route.getId()`，**不是** `route.getUri()`：

```java
new EntryConfig(route.getId(), EntryType.IN, 1, params,
                new ContextConfig(contextName(routeId), origin))
//          ^^^^^^^^^^^^^^ 资源名 = 路由 id
// contextName(routeId) 只是 ContextConfig 的名字，不是资源名
```

所以规则表里该写 `opc-ai-core`，**不是** `/opc/llm/**`。
`resourceMode` 只有两个取值（`SentinelGatewayConstants`）：

| 值 | 常量                        | 含义         |
|----|-----------------------------|--------------|
| 0  | `RESOURCE_MODE_ROUTE_ID`    | 按**路由 id**匹配（网关用这个） |
| 1  | `RESOURCE_MODE_CUSTOM_API_NAME` | 按自定义 API 名匹配（本栈不用） |

#### 规则表只有 17 条，一一对应网关的 17 条路由

真相源是**仓库里**的 `deploy/nacos/sentinel-ruoyi-gateway.json`；
Nacos 里那份是它的部署副本。改规则改仓库那份，然后 `./import-dev.sh` 重推。

| 路由 id | QPS | 为什么是这个数 |
|---------|-----|----------------|
| `ruoyi-auth-login` | 10 | 登录是打口令的地方，限得最狠 |
| `ruoyi-auth` | 30 | 令牌签发/校验 |
| `opc-ai-core` | 10 | LLM 调用最贵（每次都真掏钱），限得最狠 |
| `opc-insight` / `opc-agent-hub` / `opc-crm` / `opc-community` / `opc-hr` / `opc-erp` / `opc-content` | 20 | 重查询/重聚合 |
| `ruoyi-auth` / `ruoyi-system` / `opc-billing` / `opc-finance` / `opc-notification` / `opc-marketplace` | 30 | 普通业务读写 |
| `opc-notification-ws` | 50 | 长连接，握手频繁但单个请求很轻 |

`grade=1`（QPS 限流）、`intervalSec=1`（每秒）、`controlBehavior=0`（直接拒绝）。

**字段只能用 `GatewayFlowRule` 1.8.9 真有的那几个**：
`resource / resourceMode / grade / count / intervalSec / controlBehavior /
burst / maxQueueingTimeoutMs / paramItem`。
多写一个字段不会报错，只会被 Jackson 忽略 —— **规则看起来配了，其实没生效**。

#### 这张表曾经有 4 条规则，两条都死

存档里那 4 条（`ruoyi-auth` / `ruoyi-system` / `ruoyi-gen` / `ruoyi-job`）**从来没生效过**，
两个问题叠在一起：

1. 带了 `strategy` 和 `limitApp` 两个字段 —— `GatewayFlowRule` 里**压根没有这两个字段**；
2. `ruoyi-gen` / `ruoyi-job` **不是本网关的路由**（网关只有 17 条）。

即便当初它被正确推到了 `opc-dev`、网关也正确连上了 Nacos，这 4 条里
至少 2 条挂在不存在的路由上、另外 2 条带着模型里没有的字段。
存档在 `/root/opc-archive/nacos-public-tenant-2026-10-28/`（**不在仓库里** ——
那几份里有明文口令，而本仓库是公开的）。

#### 谁是这张表的唯一主人

| 位置 | 角色 |
|------|------|
| `deploy/nacos/sentinel-ruoyi-gateway.json` | **真相源**，改这里 |
| Nacos `opc-dev` / `sentinel-ruoyi-gateway` | 部署副本，由 `import-dev.sh` 推 |
| Sentinel 控制台 | **只读视图**；控制台上改的规则重启就没了（不落 Nacos） |

`health-check.sh` 第 23.4 节会拿 Nacos 里那份的每条 `resource`，
跟 `ruoyi-gateway/src/main/resources/application.yml` 里**真实存在的路由 id** 对表：
对不上报 FAIL，**路由新增了但忘了加规则**也报 FAIL。

#### 被限流时返回的是 429

Sentinel 命中时，RuoYi 的 `SentinelFallbackHandler` 以前返回的是 **HTTP 200** +
`{"code":500,"msg":"请求超过最大数，请稍候再试"}` —— 被限流的请求和成功的请求在 HTTP 层面
一模一样，客户端拿 200 当成功，监控里一个 429 都看不到。

现在返回 **429 Too Many Requests**，body 仍用 RuoYi 的 `{code,msg,data}` 信封、code 跟着是 429。
`SentinelFallbackHandlerTest` 把这条钉住了。


## 这份配置**不改**任何服务的行为

同一份配置摆两处时，本目录里的值必须和随 jar 走的本地 `application.yml` **逐条一致**。
这样「Nacos 生效了」不会顺带改掉任何服务的实际行为 —— 配置中心是本地 yml 的
**可运维副本**，不是偷偷换配置的另一处真相源。

各服务本地没有的键（`management.metrics.tags.application`、
`management.endpoint.health.show-components`、`opc.tenant.*`）才由这里新增，
且都是无害的（默认值与代码里的默认值相同）。

> `opc.tenant.*` 的值和 `TenantProperties` 的字段默认值相同
> （`enabled=true` / `publicCompanyId=1`），所以推上去等于「显式写出来」，不改行为。

## prod 环境

### DataID 清单（prod）

| DataID                    | 作用                              | 共享/私有 |
|--------------------------|----------------------------------|----------|
| `application-prod.yml`    | 全局共享：DB/redis/rabbit/qdrant/feign/sentinel/jasypt | 共享 |
| `opc-common-prod.yml`    | 跨服务通用：灰度/CORS/限流/审计/追踪 | 共享 |
| `opc-ai-core-prod.yml`    | AI 中台特有                       | 私有 |
| `opc-agent-hub-prod.yml`  | Agent Hub 特有                   | 私有 |
| `opc-user-center-prod.yml`| 用户中心特有                     | 私有 |
| `opc-billing-prod.yml`   | 计费特有                          | 私有 |
| `opc-finance-prod.yml`   | 财务特有                          | 私有 |

### 导入/导出/加密

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

### 改了 `deploy/nacos/*.yml`，服务却没变

配置在 Nacos 里**不是**自动同步回仓库的 —— 改了仓库文件必须重推：

```bash
cd springboot3/deploy/nacos && ./import-dev.sh
```

没重推的话，Nacos 里跑的还是旧内容（`health-check.sh` 第 22 节会报「漂移」）。

推完之后还要分两种情况：

- **热更新能覆盖的键**（`opc.tenant.*` 这类）：推完几秒内生效，日志里会看到
  `Refresh keys changed: [...]`。
- **只有启动时才定的键**（actuator 端点暴露面、`management.metrics.tags.*`）：
  **热刷新加不了**，必须重建那个服务 —— `bash deploy.sh rebuild <服务>`。
  判据是 `/actuator/metrics/jvm.memory.used` 上有没有 `application` 标签：
  这个标签只写在 Nacos 那份 `application-dev.yml` 里，本地 yml 都没有。

### 网关日志里每秒十几条 `Client not connected, current status:STARTING`

这是 Nacos 客户端连不上服务端在重试。栈里最容易踩的一处是**容器里的
`127.0.0.1` 指向容器自己** —— 网关的 Sentinel 数据源原来写着
`server-addr: 127.0.0.1:8848`，在容器里那是网关自己，客户端永远停在 STARTING。

表现极具迷惑性：**配置本身看上去一切正常**（Nacos 里有那几份配置、控制台能看见、
健康检查第 22 节全绿），但网关的流控规则一条都没加载过。

`health-check.sh` 第 **22.6** 项盯的就是它：网关日志里一条
`Client not connected` 都没有，才算连上了。修法是把地址换成容器网里的 DNS 名：

```yaml
server-addr: ${NACOS_SERVER:nacos1}:8848
```

（`nacos1` 是 compose 里的服务名；各服务统一用它，别写 `127.0.0.1`。）

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