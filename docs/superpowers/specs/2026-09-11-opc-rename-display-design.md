# OPC 显示层「若依 → OPC」替换 — 设计稿

- 日期：2026-09-11
- 范围：轻量级（仅显示层）
- 关联：[[aiopc-deployment-persistence]] / [[aiopc-llm-providers]] / [[opc-community-w52]]

## 1. 目标

消除用户在 UI、文档、版权元数据里看到的「若依/RuoYi」字样，把品牌统一为 **OPC 管理后台**。**不**触动后端包名、Maven 模块、SQL 表结构等内部实现。

## 2. 不动范围（明确排除）

| 类别 | 不动理由 |
|---|---|
| `com.ruoyi.*` Java 包路径 | 改包名 = 重写全部 51 个 OPC 测试 + 重做 E2E + 全量 Maven 重编，无业务收益 |
| `ruoyi-api/ruoyi-auth/ruoyi-common/ruoyi-gateway/ruoyi-visual` 模块目录 | 同上 |
| `springboot3/sql/ry_*.sql`、`springboot3/deploy/mysql-initdb.d/01-ry-main-schema.sql` 数据种子 | 仅历史种子数据，表结构是 RuoYi 的 `sys_*` 但 OPC 业务依赖它们 |
| `@/utils/ruoyi.ts`、`vue3-typescript/src/assets/styles/ruoyi.scss`、`src/components/RuoYi/{Doc,Git}/` 目录名 | 路径重命名 = 改 10+ 处 import 与 vitest mock path，对用户无可见收益 |
| 后端 `pom.xml` 里 `<!-- RuoYi Common Core -->` 等注释 | 用户不可见 |
| Java 源文件包头 `Copyright (c) 2019 ruoyi` | 仅注释行，不影响构建 |
| `genInfoForm.vue` 提示文字里的 `com.ruoyi.system` | 是生成器表单字段含义（生成 Java 包路径），保留 |
| 测试里的 `// W19 RuoYi built-in auth endpoints.` 字样 | 仅注释，但保持与代码同源是惯例 |

## 3. 替换清单

### 3.1 前端用户可见文案

| 文件 | 改动 |
|---|---|
| `vue3-typescript/src/settings.ts:65` | `'Copyright © 2018-2026 RuoYi. All Rights Reserved.'` → `'Copyright © 2018-2026 OPC. All Rights Reserved.'` |
| `vue3-typescript/src/views/index.vue:5` | `<h2>若依后台管理框架</h2>` → `<h2>OPC 管理后台</h2>` |
| `vue3-typescript/src/views/index.vue:7` | 整段若依介绍文案 → OPC 项目简介（基于 OPC-MVP-DELIVERY） |
| `vue3-typescript/src/views/index.vue:20,26,78,80` | `http://ruoyi.vip`、`https://gitee.com/y_project/RuoYi-Cloud` → 占位为 OPC 主页 / OPC Gitee 仓库 |
| `vue3-typescript/src/views/index.vue:92,99` | `/ *若依` 等 → `/ *OPC` |
| `vue3-typescript/src/views/index.vue:994` | `若依微服务系统正式发布` → `OPC 微服务系统正式发布` |
| `vue3-typescript/src/components/RuoYi/Git/index.vue:8` | `https://gitee.com/y_project/RuoYi-Cloud` → OPC 仓库占位 |
| `vue3-typescript/src/components/RuoYi/Doc/index.vue:8` | `http://doc.ruoyi.vip/ruoyi-cloud` → OPC 文档占位 |
| `vue3-typescript/src/layout/components/Sidebar/Logo.spec.ts` | `'若依管理系统'` 字面量 → `'OPC 管理后台'` |
| `vue3-typescript/src/store/modules/settings.spec.ts:42,54,94` | 同上 |
| `vue3-typescript/src/utils/dynamicTitle.spec.ts` | 全部 `'若依管理系统'` 字面量 → `'OPC 管理后台'`（约 15 处） |

### 3.2 后端 pom / 配置注释（用户可见）

| 文件 | 改动 |
|---|---|
| `springboot3/pom.xml:13` | `<description>若依微服务系统</description>` → `<description>OPC 微服务系统</description>` |
| `springboot3/deploy/docker-compose.yml:194,224,254` | 三段 `# ============ RuoYi ... ============` 注释 → `# ============ OPC ... ============` |
| `springboot3/deploy/nacos/whitelist-opc-invite.yml:13,37` | `# RuoYi AuthFilter ...` 与 `# RuoYi 既有白名单` 注释 → `# OPC AuthFilter ...` |
| `springboot3/ruoyi-gateway/src/main/resources/application.yml:47,126` | `# (RuoYi 单体时代遗留 ...)` 与 `# RuoYi 默认白名单` 注释 → `# (OPC 沿用 RuoYi 时代的 /system 前缀 ...)` 与 `# OPC 默认白名单` |

### 3.3 文档与 LICENSE

| 文件 | 改动 |
|---|---|
| `springboot3/README.md` | 顶部 `RuoYi v3.6.8` 大标题、gitee badge、14/16/24-43 行表格全部改写为 OPC 表述（保留「基于 RuoYi v3.6.8 开源框架构建」一句以承认上游） |
| `vue3-typescript/README.md` | 同上 |
| `README.md`（根目录） | 把「若依」叙述统一为 OPC |
| `init.md` | 同上 |
| `OPC-MVP-DELIVERY.md` / `OPC-W1-TASK-BREAKDOWN.md` / `OPC-INVITATION-FLOW.md` / `OPC-SECURITY-REPORT-v0.1.md` | 「若依」叙述统一改为 OPC；保留「基于若依开源框架」事实陈述 |
| `LICENSE`（根目录） | 保持 MIT 但归属改为 OPC 项目方（年份 2026），保留「本项目包含 RuoYi 上游代码，遵循其 MIT 协议」声明 |
| `springboot3/LICENSE` | 同上 |
| `vue3-typescript/LICENSE` | 同上 |
| `.github/FUNDING.yml`（根 / springboot3 / vue3-typescript 三处） | 删掉指向 y_project 的链接，或改占位 |

## 4. 验证

- **静态**：grep `若依|RuoYi` 在「显示层」文件 = 0；「保留范围」文件保持现状
- **前端**：`cd vue3-typescript && npm run build` 退出 0
- **后端**：`cd springboot3 && mvn -q -pl opc-common,ruoyi-common,ruoyi-gateway -am -DskipTests package` 退出 0（仅编译验证，不跑测试）
- **DB**：`01-ry-main-schema.sql` 不动；`docker compose up aiopc-mysql` 启动后查询 `select dept_name from sys_dept limit 3` 仍返回「若依科技」等历史种子（合规边界）
- **冒烟**：`docker compose up -d aiopc-frontend aiopc-gateway aiopc-auth` → 浏览器登录后页面 title 显示 `OPC 管理后台`，底部版权 `Copyright © 2018-2026 OPC`

## 5. 不在本次范围

- OPC CRM / OPC HR / OPC Insight / OPC Community 等业务功能改动
- 后端任何 `com.ruoyi.*` → `com.opc.*` 重命名
- `ruoyi-modules/` 中 5 个 ruoyi-* 内置模块的拆解（ruoyi-system/ruoyi-job/ruoyi-file/ruoyi-gen/ruoyi-monitor/ruoyi-visual）
- 移除 `ruoyi-auth`（替换为 OPC 自有 auth）
- 任何 SQL 数据迁移

## 6. 风险与回滚

- 风险等级：极低。改动仅限 UI 文案 + 注释 + 版权元数据。
- 回滚：`git revert <commit>` 即可。
- 唯一对外可观测点：浏览器 title 与页面文案。需 W52 之后再补一次 health-check 截图。
