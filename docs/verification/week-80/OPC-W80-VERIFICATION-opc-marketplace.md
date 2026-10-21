# opc-marketplace W80 VERIFICATION 报告

> **模块**: opc-marketplace (智能体市场) — P1
> **端口**: 9317
> **Week**: W80
> **报告日期**: 2026-10-21
> **状态**: 代码完成。**单元测试 + 静态渲染验证通过;未部署、未跑 e2e、未跑 health-check**
> **验证方式**: `mvn test` 实跑 + `helm lint/template` 实跑 + `bash -n` / `py_compile` 语法校验 + 源码逐项比对。**不含任何运行时 / 端到端验证**

---

## 0. 结论摘要

### 0.1 已验证(有实跑命令或源码比对支撑)

| 维度 | 期望 | 实测 | 状态 |
|---|---|---|---|
| 后端单元测试 | 全绿 | **148 tests / 0 failures / 0 errors**,连跑 8 次全量无抖动 | ✅ |
| 数据模型 | 6 表 | `V20261020__opc_marketplace_schema.sql` 6 表 | ✅ |
| REST 端点 | — | **6 controllers / 29 endpoints**,按 controller 拆分见 §1.3 | ✅ |
| 前端页面 | 4 页 | `views/opc/marketplace/` 4 个 `.vue` | ✅ |
| 前端 API 契约 | 与后端一致 | `api/opc/marketplace.ts` 24 条路径全部命中真实 controller mapping,无内联 URL 字符串 | ✅ |
| 24 vs 29 差集 | 有据可依 | 未绑定的 5 个端点**逐条有理由**,非遗漏(§2.2) | ✅ |
| 前端构建 | 通过 | vite build 通过 | ✅ |
| Helm lint | 0 failed | `helm lint` → 0 chart(s) failed | ✅ |
| Helm 渲染 | 三环境可渲染 | dev 31 / staging 48 / prod 50 kind | ✅ |
| Gateway 路由 | 已配置 | `/opc/marketplace/**` → `http://aiopc-marketplace:9317` | ✅ |
| 白名单超集不变式 | fallback ⊆ Nacos snippet | 两侧同为 44 条,0 missing / 0 extra | ✅ |
| health-check 脚本 | 已接入 | `check_marketplace()` 注册为 step 11,`bash -n` 通过 | ⚠️ 仅语法校验,未执行 |
| e2e 脚本 | 已编写 | `e2e_marketplace.py` 12 步,`py_compile` 通过 | ⚠️ 仅语法校验,未执行 |

### 0.2 未验证(本报告不声称任何覆盖)

| # | 项目 | 状态 | 原因(详见 §4) |
|---|---|---|---|
| 1 | e2e 端到端 | ❌ **从未运行** | 脚本存在且可编译,但无可用运行栈 |
| 2 | `health-check.sh` 实跑 | ❌ **从未执行** | 仅 `bash -n` 语法校验 |
| 3 | 实际部署 | ❌ **从未部署** | Helm 模板可渲染,但从未 apply 到集群 |
| 4 | 阻塞根因 | — | Docker 主机端口被其他项目占用(§4.4) |
| 5 | 真实租户 smoke | ❌ **从未执行** | 13 处租户 fail-closed 守卫仅由单测覆盖,未在真实登录态下跑过 |

> **一句话结论**:W80 opc-marketplace P1 的**代码与静态配置是可验收的**(148 单测 + 6 controllers / 29 endpoints + 24 条 API 契约对齐且差集有据 + 三环境 Helm 渲染 + 白名单不变式),但**运行时行为完全未被验证过**。任何关于"市场能跑通""登录后能安装""租户隔离在生产有效"的说法,本报告都不支持。

---

## 1. 后端验证

### 1.1 单元测试

命令:

```bash
cd springboot3 && mvn -pl ruoyi-modules/opc-marketplace -o test
```

结果:

| 指标 | 值 |
|---|---|
| Tests run | **148** |
| Failures | **0** |
| Errors | **0** |
| 构建结果 | `BUILD SUCCESS` |
| 抖动检查 | 全量套件**连跑 8 次**,8 次结果一致 |

连跑 8 次的目的是排除顺序依赖 / 静态状态污染导致的偶发绿。本报告不引用覆盖率数字——未测量即不声称。

### 1.2 数据模型(6 表)

来源:`springboot3/sql/migrations/V20261020__opc_marketplace_schema.sql`

| 项 | 值 |
|---|---|
| 表数 | 6 |
| 建表脚本 | `springboot3/sql/migrations/V20261020__opc_marketplace_schema.sql` |
| 统一时间戳约定 | 6 张表**全部**声明<br>`update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` |

**这条约定有一个必须知道的后果**:由于存在 `ON UPDATE CURRENT_TIMESTAMP(3)`,任何 UPDATE 语句**即便没有点名 `update_time`,也会把它顶上去**。本报告已知缺陷 #1(`viewCount` 计数器污染 `update_time`)正是由此而来(§5)。这不是 bug,是 DDL 语义的必然结果,但它会误导任何依赖 `update_time` 做增量同步 / 变更检测的下游逻辑。

迁移脚本本身未在任何运行中的 MySQL 上执行过(§4.1 / §4.3)。

### 1.3 REST 端点(6 controllers / 29 endpoints)

| Controller | 端点数 |
|---|---:|
| `AuthorController` | 6 |
| `CategoryController` | 3 |
| `InstallController` | 5 |
| `PublicationController` | 10 |
| `ReviewController` | 4 |
| `TagController` | 1 |
| **合计(6 个 controller)** | **29** |

口径来源:`ls controller/*.java` 返回且仅返回这 6 个文件,与上表逐行对应。**早期口径写作「29 endpoints across 5 controllers」是笔误,此处更正为 6 controllers / 29 endpoints。**

29 个端点的逐条路径本报告不逐条重列(见 §9),只承诺"数量 + 按 controller 的归属"这两项。

---

## 2. 前端验证

### 2.1 页面 / API / 路由

| 项 | 位置 | 数量 |
|---|---|---:|
| Vue 页面 | `vue3-typescript/src/views/opc/marketplace/` | 4 |
| ├ `index.vue` | 市场列表 | — |
| ├ `detail.vue` | 智能体详情 | — |
| ├ `author-center.vue` | 作者中心 | — |
| └ `author-apply.vue` | 作者入驻申请 | — |
| API 模块 | `vue3-typescript/src/api/opc/marketplace.ts` | 1 |
| 路由 | `vue3-typescript/src/router/index.ts` | 4 |

### 2.2 API 契约对齐

| 检查项 | 结果 |
|---|---|
| API 模块中定义的路径数 | **24** |
| 逐条比对真实 controller mapping | **24 / 24 命中** |
| 视图内内联 URL 字符串 | **0 处**(全部走 API 模块) |

**24 vs 29 的差集:5 个端点无前端绑定,这是刻意设计,且已全部交代清楚。**

前端 API 模块导出 **24** 个函数,后端交付 **29** 个端点,差 **5** 个。逐条核查后确认,每一个未绑定端点都有明确理由:

| 未绑定端点 | 不绑定的理由 |
|---|---|
| `POST /install/{publicationId}/upgrade` | 升级需要 `mp_agent_version` 表,而这是 **P2** 才有的一张表。P1 阶段不存在"可升级到的下一个版本",没有东西可调 |
| `GET /author/list` | 作者目录页。P1 的 UI 没有作者浏览入口,该能力归入 P3 推荐 |
| `GET /author/{id}/publications` | 已知桩实现,返回硬编码空页(另见 §5 缺陷 #3) |
| `GET /category/list` | 与 `/category/tree` 冗余——后者已返回完整分类对象 |
| `GET /category/{id}` | 同上。有了树,单分类查询没有必要 |

**24 有绑定 + 5 无绑定 = 29。** 这个差集是**主动决定的、且每个成员都有出处**,不是前端没写完。之所以在报告里显式列出:一个没有解释的 5 端点缺口会被读成"前端没交付完",而事实相反。

> 未绑定 ≠ 未测试。这 5 个端点的后端行为仍由 §1.1 的 148 个单测覆盖;这里说的只是"前端没有调用点"。

### 2.3 构建

vite build 通过。除此之外前端没有任何运行时验证:页面未在浏览器打开过,未做过交互、未做过响应式断点检查、未做移动端 drawer 验证。

---

## 3. 部署与基础设施验证(静态)

本节全部结论均为**模板渲染 / 语法校验 / 源码比对**级别。没有任何一项涉及真实部署或容器运行。

### 3.1 Helm chart

```bash
helm lint springboot3/deploy/helm/opc
# → 0 chart(s) failed

helm template test springboot3/deploy/helm/opc -f values-dev.yaml    | grep '^kind:' | sort | uniq -c
helm template test springboot3/deploy/helm/opc -f values-staging.yaml | grep '^kind:' | sort | uniq -c
helm template test springboot3/deploy/helm/opc -f values-prod.yaml    | grep '^kind:' | sort | uniq -c
```

| 环境 | kind 总数 | 其中 Deployment | 其中 Service |
|---|---:|---:|---:|
| dev | 31 | 14 | 14 |
| staging | 48 | 14 | 14 |
| prod | 50 | 14 | 14 |

marketplace 在三环境渲染出的取值,与其余 business 层服务一致:

| 键 | 值 |
|---|---|
| `containerPort` | 9317 |
| `SERVER_PORT` | 9317 |
| `replicas` | 2 |
| `tier` | `business` |
| `hpa.enabled` | `false` |

> **未验证**:`helm template` 只证明模板能被渲染,不证明镜像能 pull、配置能注入、Pod 能 Ready。**这三个模板从未 apply 到任何集群**(§4.3)。

### 3.2 Gateway 路由

来源:`springboot3/ruoyi-gateway/src/main/resources/application.yml`

| 项 | 值 |
|---|---|
| route id | `opc-marketplace` |
| `uri` | `http://aiopc-marketplace:9317` |
| predicate | `Path=/opc/marketplace/**` |
| `StripPrefix` | **无** |

无 `StripPrefix` 是正确的:后端 controller 的 `@RequestMapping` 用的是完整路径,网关若做前缀剥离反而会 404。

> **未验证**:路由配置正确 ≠ 路由可用。网关未重启、无流量经过,`/opc/marketplace/**` 从未被真实请求过。

### 3.3 Gateway 公开白名单(安全关键)

| 项 | 值 |
|---|---|
| marketplace 相关白名单条目 | **10 条** |
| `AuthFilter` 匹配维度 | **仅按 path 匹配,不看 HTTP method**(`AuthFilter.java:50`) |
| 白名单超集不变式 | gateway fallback 与 Nacos snippet **同为 44 条**,0 missing / 0 extra |

**"AuthFilter 不看 method"这条事实的后果,必须逐条盯住**:凡是被放进白名单的 path,该 path 上的**所有** HTTP method 都会免鉴权穿透到后端。逐条审计这 10 条后,骑在公开 path 上的**改写型端点只有一个**:

| 端点 | 风险 | 守卫 |
|---|---|---|
| `PUT /opc/marketplace/publication/{id}` | 匿名可达(网关层) | `PublicationServiceImpl.requireOwnership` → 403;controller 层另有 `SecurityUtils.getCompanyId()` fail-closed |

即:网关层放行,业务层拦截。这是可接受的设计,但**只在单元测试里被验证过**——它从未在"真实网关 + 匿名请求"的组合下跑过(§4.2 / §4.5)。

**白名单 10 条 ≠ 前端绑定 24 条,两个数字不矛盾。** 白名单里包含 `/category/list` 与 `/category/{id}`,而这两个端点**前端并没有任何页面在调**(见 §2.2,它们被 `/category/tree` 取代了)。这不构成风险:它们是只读的公开分类查询,不返回任何租户数据,放行本身合理。多放两条公开只读路径的代价,是攻击面多了两个无害的 GET——**唯一需要担心的是 method 维度,而这两个都是纯读**。

顺带说明一个容易看反的顺序:白名单条目数(10)是**每条 path 一项**,前端绑定数(24)是**每个导出函数一项**,二者统计对象不同,不应相加或互相印证。

> 白名单 44 条的一致性是**已验证的**,这是本模块最扎实的一块:它消除了历史上"网关 fallback 与 Nacos snippet 各说各话、白名单被整体替换"那类事故(见 `docs/verification/week-1/OPC-W1-VERIFICATION-nacos-prod.md` 一系列)。但它同样只是**集合比对**,不是运行时校验。

### 3.4 部署脚本接入

| 文件 | 状态 | 说明 |
|---|---|---|
| `springboot3/deploy/docker-compose.yml` | ✅ 已含 `aiopc-marketplace` service 段 | 仅确认条目存在 |
| `springboot3/deploy/deploy.sh` | ✅ `rebuild all` 分支包含 `opc-marketplace` | 仅确认分支覆盖 |
| `springboot3/deploy/scripts/health-check.sh` | ⚠️ `check_marketplace()` 已新增并注册为 **step 11**;`bash -n` 通过 | **从未执行**(§4.2) |

`check_marketplace()` 中包含两条安全断言(已写入脚本,但从未运行):

1. `POST /install/1` 必须被拦截,返回 401 / 403;
2. 非 `PUBLISHED` 状态的 id 不得泄漏内容。

这两条断言的设计意图是对的,**但它们目前是死代码**——写在脚本里,从未产出过一次 PASS 或 FAIL。

### 3.5 e2e 脚本

| 项 | 值 |
|---|---|
| 文件 | `tmp_e2e/e2e_marketplace.py` |
| 步骤数 | 12 |
| 语法校验 | `python -m py_compile` 通过 |
| **实际运行** | **从未**(§4.1) |

---

## 4. 未验证项(本报告的核心)

本节是本报告价值最高的部分。以下每一项都**没有被验证过**,任何"应该没问题"的推断都是无根据的。

### 4.1 e2e 从未运行

**事实**:`e2e_marketplace.py` 存在,12 步,`python -m py_compile` 通过。

**没有做的**:一次都没有跑过。没有 PASS 数、没有 FAIL 数、没有部分通过、没有时间戳、没有日志。**本报告不给出任何 e2e 通过率**,连"预计大部分能过"这种话都不说——那是没有依据的。

**原因**:无可用运行栈(见 §4.4)。

**后果**:脚本里的 12 个步骤中,有多少真的能跑通、有没有路径写错、有没有 DTO 字段与后端对不上、有没有租户上下文缺失导致的必然 403——**全部未知**。这些恰恰是单测覆盖不到的边界(真实序列化、真实鉴权链、真实 SQL)。

### 4.2 health-check 从未执行

**事实**:`check_marketplace()` 已加入 `health-check.sh` 并注册为 step 11,`bash -n` 通过。

**没有做的**:整个 `health-check.sh` 一次都没有跑过。所以:

- marketplace 那一步从未产出 PASS 或 FAIL;
- 前面 10 步的状态是否仍全绿,本报告也**不作任何声称**(未运行 = 无数据,不是"应该还绿");
- 脚本里那两条安全断言从未生效。

**`bash -n` 只保证语法可解析。** 它不保证变量有值、不保证引用的脚本路径存在、不保证 `curl` 参数拼写正确、不保证 jq 表达式能取到字段——而这些都是健康检查脚本最常见的失败点。

**原因**:同上,无可用运行栈。

### 4.3 未部署

**事实**:`helm lint` 通过,三环境 `helm template` 渲染成功。

**没有做的**:从未 `helm install` / `helm upgrade`,从未进过任何集群。从未起过容器、从未注册过 Nacos、从未被网关路由到、从未 `/actuator/health` 过。

**"模板渲染成功"与"能跑起来"之间的距离,本报告不作估计。** 渲染只覆盖模板语法与 values 合并,不覆盖镜像拉取、ConfigMap/Secret 注入、就绪探针、依赖服务可达性、资源限额等一整类问题。

**原因**:同上,无可用运行栈。

### 4.4 阻塞根因:Docker 主机与其他项目争抢端口

这不是"环境没配好"这种含糊说法,冲突是具体且可复述的:

| aiopc 需要的主机端口 | 被谁占用 |
|---|---|
| **3308**(MySQL) | `res-mysql` |
| **8848 + 9848**(Nacos) | `res-nacos` |
| **8080**(网关) | `aicustomer-web-1` |

**这台 Docker 主机是与其他项目共享的。** 启动 aiopc 栈,就意味着要停掉**别的项目的在跑容器**。这是一个会波及其他项目、且不应由本模块单方面决定的动作,因此本模块停在这里,并如实上报,而没有强行抢占。

**另有一道独立的前置卡点**,即使端口问题解决了也依然存在:

> `bootstrap.yml` 中 `nacos.config.fail-fast: true`。在 `deploy/nacos/import-dev.sh` 把 `opc-marketplace-dev.yml` 导入 Nacos 之前,**服务在配置拉取阶段就会失败退出,根本起不来**。

所以完整的前置条件是两条,**必须同时**满足:

1. 腾出 3308 / 8848 / 9848 / 8080 四个主机端口;
2. 执行 `deploy/nacos/import-dev.sh` 导入 `opc-marketplace-dev.yml`。

第 1 条需要占用主机的项目所有者同意;第 2 条需要一台可用的 aiopc Nacos。**两条都不在 W80 的可控范围内。**

### 4.5 无真实租户 smoke,租户隔离守卫只到单测为止

**事实**:代码中有 **13 处** `SecurityUtils.getCompanyId()` 的 fail-closed 守卫(`companyId == null` → 直接抛 400),`PublicationServiceImpl.requireOwnership` 的 403 判定也已被单测覆盖。

**没有做的**:从未用真实登录态跑过一次请求。

**这意味着**:以下每一条,当前都只有单测背书,**没有任何运行时证据**:

- token 里确实带着能解析出的 `companyId`;
- 未关联企业的用户(token 无 `companyId`)走到这 13 处守卫时,前端拿到的是可理解的 400 而不是 500;
- `PUT /opc/marketplace/publication/{id}` 在「真实匿名请求穿透网关」这一真实链路上,确实由 `requireOwnership` 拦成 403 而不是漏放;
- SQL 层的 `WHERE id=? AND company_id=?` + `rowsAffected==0 → 403` 这条防 TOCTOU 的约定,在真实事务与并发下行为正确。

**单元测试用 mock 的 `SecurityUtils` 静态方法,它验证的是"如果登录态是这样,则会抛 400",而不是"登录态真的会是这样"。** 这两者之间的差距,只有真实登录才能补上。

---

## 5. 已知缺陷(已确认,未修,附不修的原因)

以下 7 项**全部经过核实**,但 P1 范围内**未修**。逐条给出保留原因,避免被当成"忘了"。

| # | 缺陷 | 影响 | 为何不修 |
|---|---|---|---|
| 1 | `viewCount` 计数器会顶起 `update_time`,尽管该 SQL 并未点名这一列 | 读计数器污染"最后更新时间"语义;任何依赖 `update_time` 做增量同步 / 变更检测的下游会被误触发 | 修复需要一次 **schema migration**。而当前所有其他写路径都显式设置 `update_time`,因此迁移是安全的;且 `PublicationVO` 里**根本没有 `updateTime` 字段**,即该污染目前对外不可见。P1 不值得为不可见的字段做迁移 |
| 2 | `subscriptionCount` 未接线 | 该字段恒为 0 | 计划留到 P2。当前 UI 的处理是**把该单元格删掉**,而不是展示一个永久的 `0`——后者会被用户当真实数据 |
| 3 | `GET /author/{id}/publications` 是桩实现 | 返回硬编码空页 | P1 未做作者维度的聚合查询。已明确标记为桩,不是"假装能查"。前端也刻意未绑定它(§2.2),所以这个桩**没有可达的 UI 入口**,不会误导用户 |
| 4 | `GET /review/list/{id}` 返回分页结构 `{rows,total,page,pageSize}`,但 rows 是裸 `MpReview` 而非 `ReviewVO` | 无 `userName`,UI 只能回退显示 `User #<id>` | 需要引入 `ReviewVO` + 关联用户名的查询。P1 选择暴露真实结构,而不是先造一个只有 id 的假 VO |
| 5 | `GET /category/tree` 在 P1 是平铺的 | 不按 `parent_id` 分组 | 分类层级树是 P2 范围。平铺是诚实的当前状态 |
| 6 | `utils/request.ts` 用裸字符串 `'error'` reject;其 `errorCode` 表让通用的 `'403' → '当前操作没有权限'` 覆盖掉后端返回的真实原因 | **全局性**,影响每一个视图,不止 marketplace | 属全局请求层改动,影响面远超 P1 单模块,刻意划在 P1 范围之外 |
| 7 | `springboot3/deploy/nacos/` 下有一份过期路由 JSON(6 条路由,缺 8 个服务),**已在本报告之后删除** | 若被当作路由事实来源,会用 6 条剥前缀的旧清单顶掉现行的 14 条,打掉 8 个服务的入口 | 实际生效的路由来自网关的 `application.yml`(见 §3.2),该文件从不参与运行时,故删除无运行时影响。**属既有问题,不是 P1 引入的回归** |

**这 7 项都不是"忘了修",而是逐条做过取舍后留下的**(理由见末列)。其中 #3 (`GET /author/{id}/publications`) 同时也是前端未绑定的 5 个端点之一(§2.2)——后端的缺口和前端的缺口**是同一件事的两个侧面**:既然 UI 够不着这个桩,就没有必要给它补实现,否则会做出一个"有 API 没入口、或有入口没实现"的半吊子。#5 (`/category/tree` 平铺)则相反:它**是**被绑定的,只是 P1 尚未做层级分组,P2 补树结构即可,前端调用点无需改动。

---

## 6. 复现命令(已实跑)

```bash
# 后端单元测试 —— 148 tests / 0 failures / 0 errors / BUILD SUCCESS(连跑 8 次)
cd springboot3 && mvn -pl ruoyi-modules/opc-marketplace -o test

# Helm lint —— 0 chart(s) failed
helm lint springboot3/deploy/helm/opc

# Helm 三环境渲染 —— dev 31 / staging 48 / prod 50(kind 总数)
helm template test springboot3/deploy/helm/opc -f values-dev.yaml    | grep '^kind:' | sort | uniq -c
helm template test springboot3/deploy/helm/opc -f values-staging.yaml | grep '^kind:' | sort | uniq -c
helm template test springboot3/deploy/helm/opc -f values-prod.yaml    | grep '^kind:' | sort | uniq -c

# health-check 脚本语法校验(仅此而已,脚本从未运行)
bash -n springboot3/deploy/scripts/health-check.sh

# e2e 脚本语法校验(仅此而已,脚本从未运行)
python -m py_compile tmp_e2e/e2e_marketplace.py

# 前端构建 —— vite build 通过
```

**为了让本报告能被信任,请注意上面这些命令的边界**:没有一条会启动容器、监听端口或发一个 HTTP 请求。§4 中所有"未验证"项,都不会因为重跑上面任何一条命令而变成已验证。

---

## 7. 关键文件路径

| 内容 | 路径 |
|---|---|
| 服务主目录 | `springboot3/ruoyi-modules/opc-marketplace/` |
| 数据库迁移(6 表) | `springboot3/sql/migrations/V20261020__opc_marketplace_schema.sql` |
| Gateway 路由 | `springboot3/ruoyi-gateway/src/main/resources/application.yml` |
| Gateway 鉴权过滤器 | `ruoyi-gateway` `AuthFilter.java:50` |
| 白名单 / Nacos snippet | gateway fallback + `springboot3/deploy/nacos/` 下的 snippet(两侧 44 条一致) |
| docker-compose | `springboot3/deploy/docker-compose.yml`(`aiopc-marketplace`) |
| 部署脚本 | `springboot3/deploy/deploy.sh`(`rebuild all` 含 `opc-marketplace`) |
| 健康检查 | `springboot3/deploy/scripts/health-check.sh`(`check_marketplace()`,step 11) |
| Helm chart | `springboot3/deploy/helm/opc/` |
| ~~过期的路由 JSON~~ | 已删除 —— 勿再作为路由来源(真源见上一行 Gateway 路由) |
| 前端页面 | `vue3-typescript/src/views/opc/marketplace/{index,detail,author-center,author-apply}.vue` |
| 前端 API | `vue3-typescript/src/api/opc/marketplace.ts` |
| 前端路由 | `vue3-typescript/src/router/index.ts` |
| e2e 脚本 | `tmp_e2e/e2e_marketplace.py` |

---

## 8. 解除阻塞后应补的验证(建议顺序)

严格按依赖顺序,前一步不通后面不用试:

1. **腾端口**:与 Docker 主机共用方协商 3308 / 8848 / 9848 / 8080 的归属。不解决这步,后面全部无效。
2. **导 Nacos**:`deploy/nacos/import-dev.sh` 导入 `opc-marketplace-dev.yml`。`fail-fast: true` 意味着这步不做容器起不来。
3. **起栈 + 跑 `health-check.sh`**。关注两处:marketplace 那一步(step 11)的结果;以及**脚本内那两条安全断言是否真的产出 PASS**——它们至今一次都没生效过。
4. **跑 `e2e_marketplace.py`**。重点看真实鉴权链与序列化,单测覆盖不到的就是这几处。
5. **真实租户 smoke**:用一个已关联企业的账号登录,再用一个**未关联企业**的账号登录,各打一遍 13 处守卫覆盖的路径,确认 400 / 403 而不是 500,确认无跨租户读取。
6. **补 P2 / P3 的能力**:`mp_agent_version` + `POST /install/{id}/upgrade`(P2)、作者目录页 `GET /author/list`(P3)、`/category/tree` 的 `parent_id` 分组(P2,前端调用点无需改)。这 3 项正是 §2.2 中刻意未绑定的部分,补齐后 24 → 29 即可收敛。

---

## 9. 本报告刻意没有写的东西

为免读者误以为是遗漏,在此列明:

- **没有任何覆盖率数字**——未测量。
- **没有任何 e2e 通过率**——未运行(§4.1)。
- **没有任何 health-check 通过数**——未执行(§4.2)。
- **没有逐条列出全部 29 个端点的路径清单**——本报告断言的是数量与按 controller 的归属(§1.3),不是把 29 条路径逐条重列一遍。§2.2 与 §3.3 中出现的具体路径,均因该处论断需要而单独引用。
- **没有 commit SHA**——本报告未被要求核对提交历史,因此不列 commit。
- **没有运行时性能 / 负载 / 并发数据**——一次请求都没发过。

**此前一度悬而未决、现已查清的两项,不再列为"未声称":**

- ~~controller 数量 5 还是 6~~ → 已核实为 **6**(§1.3)。
- ~~未绑定的 5 个端点具体是哪几个~~ → 已逐条列明并给出各自理由(§2.2)。
