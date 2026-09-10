# OPC-W19-W22 — 前端 api 契约 + SFC Logo 单测批次验证报告

## 执行摘要

**批次**：W19 / W20 / W21 / W22（4 commits，2026-09-08）

| 子任务 | 内容 | @Test | commit |
|---|---|---:|---|
| **W19** | `src/api/login.ts` 7 endpoint 契约 | 28 | `5a2ef45` |
| **W20** | `src/api/menu.ts` getRouters wrapper | 6 | `c0f819f` |
| **W21** | `src/api/system/dict/data.ts` 6 endpoint CRUD | 28 | `1f83268` |
| **W22** | `src/layout/components/Sidebar/Logo.vue` SFC | 24 | `885c90d` |
| **合计** | 3 api 模块 + 1 SFC | **86 @Test** | 4 commits |

**累计 W11 + W12 + W13-W22**：628 @Test / 32 spec 文件 / 全绿。

---

## 1. 本机实测结果

```bash
cd vue3-typescript
npm run test:run
# Test Files  32 passed (32)
#      Tests  628 passed (628)
# Duration  17.51s

npm run test:coverage
# Statements   : 95.54% ( 793/830 )
# Branches     : 86.22% ( 438/508 )
# Functions    : 96.53% ( 251/260 )
# Lines        : 96.30% ( 756/785 )
```

**W19-W22 全部纳入覆盖率计算**（除 SFC + RuoYi built-in api/system/** 之外）。本批次新增覆盖：
- `src/api/login.ts` 28 / 28 stmts + 7 / 7 funcs = 100%
- `src/api/menu.ts` 3 / 3 stmts + 1 / 1 func = 100%
- `src/api/system/dict/data.ts` 6 个 export 函数全路径覆盖

`src/layout/components/Sidebar/Logo.vue` 在 `vitest.config.ts` 的 coverage exclude 列表（SFC 不纳入）。

---

## 2. W19 — `src/api/login.ts` 7 endpoint 契约

### 关键 pin（28 tests）

**A. login()**（4 tests）
- URL=`/auth/login`, method=POST
- `headers={isToken:false, repeatSubmit:false}`（双重旁路 token 注入 + 防重提交）
- data envelope 字段顺序 `username, password, code, uuid`（**顺序敏感**）
- 空字符串透传（API 层不做 validation）

**B. register()**（4 tests）
- URL=`/auth/register`, headers={isToken:false}（**无** `repeatSubmit` 旁路）

**C. refreshToken() / getInfo() / logout()**（3+3+3 tests）
- 全部 2 键 config `{url, method}` —— 无 headers / data / params
- logout 用 **DELETE** 方法（其余多为 POST）

**D. unlockScreen()**（4 tests）
- URL=`/auth/unlockscreen`, data=`{password}`（包裹信封）
- **不**带 isToken/repeatSubmit（屏幕锁已认证）

**E. getCodeImg()**（4 tests）
- URL=`/code`, `timeout:20000`（**覆盖默认 10000ms**——验证码慢）
- `headers={isToken:false}`（验证码无认证）

**F. Module-level**（3 tests）
- 7 exports 全部为 function
- 7 endpoints → 7 distinct URLs（`/auth/login, /auth/register, /auth/refresh, /auth/logout, /auth/unlockscreen, /code, /system/user/getInfo`）

### 设计意图

RuoYi 的 `headers.repeatSubmit=false` 是「防重提交守卫」的旁路开关——login 时不应被守卫视为重复提交。register 没设置说明**当前实现认为注册页提交也需守卫**（潜在风险点：用户连点注册可能被误拦，pin 行为待观察）。

---

## 3. W20 — `src/api/menu.ts` getRouters

### 关键 pin（6 tests）

- URL=`/system/menu/getRouters`, method=GET
- 2 键 config，**不**设置 `isToken:false`（这是认证后路由表，需 bearer token）
- Promise 1:1 passthrough
- 多次调用不缓存（缓存由 W13 permission store 的 `generateRoutes` 处理）
- Export shape：`const arrow function`（无 `.prototype`）

### 设计意图

`getRouters` 是 permission store 的「种子路由源」——W13 spec 已经覆盖 permission store 的 `generateRoutes` 如何消费这个 promise（含 deep clone + filterAsyncRouter fan-out），W20 仅钉死 API wrapper 契约。

---

## 4. W21 — `src/api/system/dict/data.ts` 6 endpoint CRUD

### 关键 pin（28 tests）

**A. listData()**（4 tests）
- URL=`/system/dict/data/list`, params=`{dictType, dictLabel, pageNum, ...}`（**params 而非 data**——GET body 经 query string）

**B. getData(dictCode)**（4 tests）
- URL=`/system/dict/data/{dictCode}` —— **字符串拼接**（非 template literal）
- **不调用 `encodeURIComponent`**（pin 行为——特殊字符会破 URL）
- 2 键 config

**C. getDicts(dictType)**（5 tests）
- URL=`/system/dict/data/type/{dictType}` —— 也是拼接
- 不编码（`'a b'` → URL 里有字面空格）
- W16 useDict 的 cache-miss 调用此函数

**D. addData(data) / updateData(data)**（7 tests）
- **相同 URL `/system/dict/data`**，仅 method 区分（POST vs PUT）—— REST 契约 pin
- data envelope 透传

**E. delData(dictCode)**（4 tests）
- 单 ID：`/system/dict/data/5`
- 数组 ID：`/system/dict/data/1,2,3`（**数组 toString 逗号拼接**——非典型 batch-delete，pin quirk）
- 该 quirk 是 JS `'/system/dict/data/' + [1,2,3]` 的自然行为

**F. Module-level**（3 tests）
- 4 distinct URL patterns（POST/PUT 共享 + GET-by-id/DEL-by-id 共享）

### 设计意图

这是 RuoYi 内置 dict 字典数据的完整 CRUD。`getDicts` 是 W16 useDict 的底层 API——pin 其语义保证 dict 缓存层 miss 时的行为可预测。

---

## 5. W22 — `src/layout/components/Sidebar/Logo.vue` SFC

### 关键 pin（24 tests）

**A. getLogoBackground 4-branch 决策树**（5 tests）
| 优先级 | 条件 | 返回 |
|---|---|---|
| 1 | isDark=true | `'var(--sidebar-bg)'`（短路） |
| 2 | isDark=false + navType=3 | `variables.menuLightBg`（`#ffffff`） |
| 3 | isDark=false + navType≠3 + sideTheme='theme-dark' | `variables.menuBg`（`#1a1f2e`） |
| 4 | else | `variables.menuLightBg` |

**isDark 是最高优先级**，会短路后续所有判断（test 2 pin 了 isDark=true 时 navType=3 vs navType=1 都返回相同结果）。

**B. getLogoTextColor 4-branch 决策树**（5 tests + 1 不对称 pin）
- 与 getLogoBackground 几乎相同，**但 theme-dark 分支返回硬编码 `'#fff'` 而非 `variables.menuLightText`**（test 10 pin 不对称）

**C. 计算响应性**（3 tests）
- 修改 `settingsStore.isDark / navType / sideTheme` → 计算立即重新计算

**D. 模板结构**（5 tests）
- `<img.sidebar-logo>` 渲染（src 来自 mock import）
- **`<h1.sidebar-title>` 总是渲染**（v-else 分支缺少 v-if 的 quirk，pin）
- `<router-link>` stub 用 `data-to` 属性捕获目标 URL

**E. collapse prop**（2 tests）
- `collapse=true` → root 加 class `'collapse'`
- `collapse=false` → 不加

**F. Title from env**（1 test）
- title 来自 `import.meta.env.VITE_APP_TITLE`（happy-dom 不加载 .env → undefined）

**G. Module behavior**（3 tests）
- SFC default export 是 object
- collapse prop required（缺 prop 时 console warning，不崩溃）
- computed 缓存（多次读取返回相同值）

### Mock 配置（5 个）

```ts
vi.mock('@vueuse/core')           // useDark / useToggle
vi.mock('@/utils/dynamicTitle')   // useDynamicTitle
vi.mock('@/utils/theme')          // handleThemeStyle
vi.mock('@/settings')             // defaultSettings（13 字段全部注入）
vi.mock('@/assets/logo/logo.png') // 二进制资源
vi.mock('@/assets/styles/variables.module.scss')  // SCSS variables
```

### 关键发现 & quirks

1. **Vue 3.4+ setupState 自动 unwrap ref**：访问计算值时 `wrapper.vm.$.setupState.getLogoBackground` 直接是 string，**不需要 `.value`**。
2. **SFC `<style>` 里 `v-bind()` 在 happy-dom 下不生成 inline style 属性**——必须通过 setupState 直接访问 computed 值。
3. **`v-else (expand) 分支缺少 v-if`**：源 SFC 的 `<h1>` 总是渲染，与 `<img>` 同时出现。pin 此 quirk 防止未来误以为是 bug 而错改。
4. **`getLogoTextColor` theme-dark 分支硬编码**：`'#fff'` 而非 SCSS var——SCSS 变量变更不会传播到此分支。

---

## 6. Mock 模式清单（本批次新增）

| Mock 目标 | 模式 | 用途 |
|---|---|---|
| `@/utils/request` | `vi.mock → vi.fn()` | 拦截所有 api/ 模块的 `request()` 调用 |
| `@vueuse/core` | `vi.hoisted` + `vi.mock` 部分覆盖 | useDark 返回 mutable ref |
| `@/utils/dynamicTitle` | 简单 stub function | 避免触发真实 watcher |
| `@/utils/theme` | `vi.importActual + spread` | 保留其他导出，仅替换 handleThemeStyle |
| `@/settings` | 完整 defaultSettings 副本 | settings store 模块加载依赖 |
| `@/assets/logo/logo.png` | `default: '/mock-logo.png'` | 跳过 Vite 二进制解析 |
| `@/assets/styles/variables.module.scss` | `{menuBg, menuLightBg, menuLightText, menuActiveText}` | SCSS variables 模拟 |

---

## 7. 已知 bug / 局限（本批次识别）

| 位置 | 行为 | pin 策略 |
|---|---|---|
| `Logo.vue` `<h1>` v-else 缺 v-if | h1 总是渲染 | 钉死 + 注释 |
| `Logo.vue` `getLogoTextColor` theme-dark 分支 | 硬编码 `'#fff'` | 钉死 + 不对称注释 |
| `api/login.ts:register()` 不设 `repeatSubmit:false` | 注册可能误触发防重 | 钉死 + 待观察 |
| `api/system/dict/data.ts:getData/getDicts` 不调用 `encodeURIComponent` | 特殊字符破 URL | 钉死 |
| `api/system/dict/data.ts:delData([1,2,3])` 数组拼接 | `'1,2,3'` 而非 batch endpoint | 钉死 quirk |
| `api/login.ts:addData()/updateData()` 共享 URL | 仅 method 区分 | 钉死 REST 契约 |

**全部为业务代码 quirks——本批次按 W11 起一贯原则「只 pin 不修」**。

---

## 8. 与其他 spec 的依赖关系

| 当前 spec | 依赖 spec | 共享契约 |
|---|---|---|
| `api/login.spec.ts` | (none) | `vi.mock('@/utils/request')` |
| `api/menu.spec.ts` | `store/permission.spec.ts` (W13) | getRouters 是 generateRoutes 的种子 |
| `api/system/dict/data.spec.ts` | `utils/dict.spec.ts` (W16) | getDicts 是 useDict cache-miss 入口 |
| `layout/Sidebar/Logo.spec.ts` | `store/settings.spec.ts` (W12.3) | 消费 isDark/navType/sideTheme 状态 |

---

## 9. W23+ 候选清单

### A. `src/api/system/**` 剩余 8 模块（~80 @Test，coverage 已 exclude 不增）
- `config.ts` (3 endpoints) / `dept.ts` (5) / `logininfor.ts` (2) / `menu.ts` (4) / `notice.ts` (5) / `operlog.ts` (2) / `post.ts` (4) / `role.ts` (7) / `user.ts` (8)
- 模式同 W19/W21（vi.mock request + 钉死 4 维度契约）
- 不增覆盖率，但能完整覆盖所有 RuoYi builtin 后端契约

### B. `src/api/monitor/**` + `src/api/tool/**`（~30 @Test）
- 同上模式

### C. SFC 剩余（happy-dom 限制下可达）
- `layout/components/Sidebar/SidebarItem.vue`（~8 @Test）
- `layout/components/Sidebar/Link.vue`（~4 @Test）
- `views/opc/invitations.vue` + `components/SharePoster.vue`（canvas 仍需 jsdom）

### D. Store Tier B
- `store/user.ts`（login/logout/refresh/getInfo integration）—— W11.2 已有部分覆盖
- `store/modules/dict.ts`（与 W16 useDict 对齐）

### E. Pinned-bug 修复候选
- `Logo.vue` v-else 缺 v-if（业务代码小改，1 行）
- `Logo.vue` getLogoTextColor 硬编码（一致性修复）

---

## 10. 本批次累计指标

| 指标 | W12 收官 | W22 收官 | 增量 |
|---|---:|---:|---:|
| @Test 总数 | 374 | **628** | +254 |
| Spec 文件数 | 15 | **32** | +17 |
| Commit 数（W11 起） | 14 | **18** | +4 |
| Coverage lines | 95.30% | **96.30%** | +1.00pp |
| Coverage funcs | 96.20% | **96.53%** | +0.33pp |
| Coverage branches | 85.50% | **86.22%** | +0.72pp |
| Coverage stmts | 95.20% | **95.54%** | +0.34pp |

**W12 起 4 批（W13/W14/W15/W16/W17/W18/W19/W20/W21/W22）共 10 commits / +254 @Test / +17 spec files**。

---

## 11. 验证方法

```bash
cd D:/work-ai/0401-lumen-opc/vue3-typescript

# 全量测试
npm run test:run

# 单文件（调试）
npx vitest run src/api/login.spec.ts
npx vitest run src/api/menu.spec.ts
npx vitest run src/api/system/dict/data.spec.ts
npx vitest run src/layout/components/Sidebar/Logo.spec.ts

# 覆盖率
npm run test:coverage
```

**期望**：32 spec files / 628 @Test 全绿，coverage ≥ 95.5% stmts / ≥ 86% branches / ≥ 96% funcs / ≥ 96% lines。

---

## 12. 不要做的事（保持约束）

- **不要改任何业务代码**（W19-W22 仅添加测试）
- **不要扩展 coverage exclude 列表**（已是 SFC + RuoYi builtin api/** 子集）
- **不要 commit .env / .pem / 真实 prod yml**
- **不要跑 `npm run build`**（本批次仅验证测试 + 覆盖率）

---

## Out-of-Scope（W23+ 候选）

- `src/api/system/**` 8 个剩余模块（**不增 coverage，仅补全契约 spec**）
- `src/api/monitor/**` + `src/api/tool/**`（同上）
- SFC 剩余（SidebarItem/Link + opc/invitations）
- 业务 bug 修复（Logo v-else v-if 缺失、textColor 硬编码）
- jsdom + canvas polyfill（SharePoster.vue Canvas 测试）
- CI 集成 coverage gate（需 .github/workflows 改造）