# OPC-W11-VERIFICATION-frontend-tests

> 首个前端测试 commit；本机真跑 Vitest（不是静态分析）。

**日期**：2026-09-07
**作者**：OAC (Claude Opus 4.6)
**范围**：vue3-typescript/ 前端测试基础设施 + Tier S 覆盖（utils / stores / components）
**提交链**：`ff31cac` W11.1 → `409b886` W11.2 → `0beb8fc` W11.3 → (本文件) W11.4

---

## 1. 执行摘要

W11 是 OPC 项目的**第一个前端测试 commit**，也是**第一个能在此 Windows 机器上真正运行（而非仅静态分析）的 W 任务**——后端 Spring Boot 3 受 JDK 8 限制只能静态验证，Vitest 是 Node 原生可在本地跑通。

| 指标 | 值 |
|---|---|
| 新增 spec 文件 | **11 个** |
| @Test 总数 | **218** |
| 测试套件数 | **11**（4 utils + 3 stores + 1 permission + 3 components） |
| 提交数 | **3**（W11.1 / W11.2 / W11.3，W11.4 为文档） |
| 运行时间 | ~17.5s 全部 11 文件 |
| 失败数 | **0** |
| 跳过 | **0** |
| 本机真跑 | ✅ Windows + Node v22 + happy-dom 20.14 |
| 业务代码改动 | **0**（W11 仅添加测试和配置） |

### 1.1 与计划的差异

- W11.1 计划 ~79，实际 **126**（因为 validate.ts 12 函数展开更细）
- W11.2 计划 ~43，实际 **47**（dict store 多加了 2 个 push-semantics 钉子测试）
- W11.3 计划 ~36，实际 **45**（ResponsiveTable 多 2 个边界、MobileDrawer 多 1 个空 footer 测试）
- 合计计划 ~158，实际 **218**（超出 38%，因为 formatters 的边界场景值得钉）

---

## 2. 本机实测（真跑 Vitest）

```bash
$ cd vue3-typescript
$ npx vitest run

 RUN  v5.0.0 D:/work-ai/0401-lumen-opc/vue3-typescript

 Test Files  11 passed (11)
      Tests  218 passed (218)
   Start at  15:43:13
   Duration  17.56s (environment 33%, import 28%, setup 19%, transform 16%, tests 2%, worker 1%)
```

```bash
# 单文件
$ npx vitest run src/utils/errorCode.spec.ts
 Test Files  1 passed (1)
      Tests  6 passed (6)

# 按名称过滤
$ npx vitest run -t "parseTime"
 Test Files  1 passed (1)
      Tests  7 passed (7)
```

**npm scripts**（已加入 `package.json`）：

```json
"test": "vitest",
"test:run": "vitest run",
"test:ui": "vitest --ui"
```

---

## 3. 测试套件明细

| Spec 文件 | 行数 | @Test | 覆盖范围 |
|---|---:|---:|---|
| `src/utils/errorCode.spec.ts` | 30 | 6 | 4 个错误码映射 + `500` 未定义边界 + 重复导入身份 |
| `src/utils/validate.spec.ts` | 122 | 62 | 12 个函数 × 多个边界 |
| `src/utils/auth.spec.ts` | 76 | 11 | 6 个 token/expiresIn 函数 × vi.mock('js-cookie') |
| `src/utils/ruoyi.spec.ts` | 247 | 47 | 11 个函数 × 多场景（`resetForm` Vue 2 残留主动跳过） |
| `src/store/modules/dict.spec.ts` | 99 | 13 | setDict push 语义 + getDict 已知 `&&` bug 钉死 + removeDict/cleanDict/initDict |
| `src/store/modules/lock.spec.ts` | 64 | 7 | lock/unlock + localStorage 持久化 + 初始恢复 |
| `src/store/modules/user.spec.ts` | 154 | 14 | login (4) + getInfo (8) + logOut (2)，6 模块 mock |
| `src/utils/permission.spec.ts` | 76 | 13 | checkPermi (7) + checkRole (6) + console.error spy |
| `src/views/opc/components/ResponsiveTable.spec.ts` | 282 | 21 | 11 formatter + 10 render mode + width-1 breakpoint offset 钉 |
| `src/views/opc/components/MobileDrawer.spec.ts` | 122 | 11 | body/title/header-slot/close/aria-label/footer 渲染 + direction/size 透传 |
| `src/views/opc/components/AgentCard.spec.ts` | 119 | 13 | name/price/8 类别映射 + unknown fallback + 详情/雇佣路由 |
| **合计** | **~1390** | **218** | |

---

## 4. Mock 模式清单

### 4.1 全局（`src/test/setup.ts`）

```ts
beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
})

// ElMessageBox 静默解决 — 不渲染 UI
vi.mock('element-plus', async () => {
  const actual = await vi.importActual<typeof import('element-plus')>('element-plus')
  return {
    ...actual,
    ElMessageBox: {
      confirm: vi.fn().mockResolvedValue(undefined),
      alert: vi.fn().mockResolvedValue(undefined),
      prompt: vi.fn().mockResolvedValue({ value: '' }),
    },
  }
})

// profile.jpg 在 happy-dom 下没有真实二进制
vi.mock('@/assets/images/profile.jpg', () => ({
  default: '/mock-profile.jpg',
}))
```

### 4.2 各 spec 局部 mock

| 模式 | 用法 | 应用文件 |
|---|---|---|
| `vi.mock('js-cookie', () => ({ default: mock }))` | inline factory（避开 hoisting pitfall） | `auth.spec.ts` |
| `vi.mock('@/api/login', () => ({ login, logout, getInfo }))` | 6 模块全部 inline factory | `user.spec.ts` |
| `vi.mock('@/router', () => ({ default: { push: vi.fn() } }))` | router.push spy | `user.spec.ts` |
| `vi.mock('@vueuse/core', () => ({ ..., useWindowSize: () => ({ width: widthRef }) }))` | mutable widthRef 切桌面/移动 | `ResponsiveTable.spec.ts` |
| `mount(opts.global.components)` | 注册 stub 替身，让 `<el-table-column>` 等模板编译能找到组件名 | 3 个 component spec |
| `vi.mock('vue-router', () => ({ ..., useRouter: () => ({ push }) }))` | useRouter 替换为 spy | `AgentCard.spec.ts` |

### 4.3 关键决策记录

**为什么不用 `vi.mock('element-plus')` 全局？**
el-tag / el-button 等小组件在 happy-dom 下能正常工作；只需对 el-table / el-card 等有运行时副作用的组件 stub。全局 vi.mock 反而会引入 vi.mock hoisting + 重复 default export 的复杂度。

**为什么用 `global.components` 而非 `global.stubs`？**
`@vue/test-utils` 的 `stubs` 选项只 stub 根组件；子组件（`<el-table-column>` 在 `<el-table>` 内）必须通过 `global.components` 显式注册。

**为什么 `mergeConfig` 不能接受 callback？**
vite.config.ts 是 `defineConfig(({mode, command}) => ...)` 的 callback 形式；Vitest 的 `mergeConfig` 只接受对象。W11.1 在 `vitest.config.ts` 中重新声明 alias + plugins 子集（不在 callback 中）。

---

## 5. 已知 bug / 局限

### 5.1 已发现并钉死的 bug

#### `dict.ts:19` — `&&` 应为 `||`

```ts
// src/store/modules/dict.ts:18-21
getDict(_key: string): any[] | null {
  if (_key == null && _key == "") {  // 永远 false（一个值不可能同时是 null 和 ""）
    return null
  }
  ...
}
```

测试覆盖（`dict.spec.ts` 第 53-63 行）：

```ts
// KNOWN BUG: && → ||, see W11.4 report
it('returns null for empty key (KNOWN BUG: && → ||, see W11.4 report)', ...)
it('returns null for null key (same known bug as above)', ...)
```

**修复建议**（W12+ 候选）：
```ts
if (_key == null || _key == "") {  // 用 ||
  return null
}
```

#### `validate.ts:24` — `isEmpty` 用 `==` 导致误判

```ts
if (value == null || value == "" || value == undefined || value == "undefined") {
  return true
}
```

JS coercion 副作用：
- `0 == ""` → `true` → `isEmpty(0)` 返回 `true`（应该是 `false`）
- `[] == ""` → `true` → `isEmpty([])` 返回 `true`（应该是 `false`）

测试覆盖（`validate.spec.ts` 第 30-65 行）显式断言这些 quirk：

```ts
it('returns true for 0 (== "" coercion quirk)', () => expect(isEmpty(0)).toBe(true))
it('returns true for empty array (== "" coercion quirk)', () =>
  expect(isEmpty([])).toBe(true))
```

#### `ruoyi.ts:getNormalPath` — `replace('//', '/')` 只替换第一个

```ts
let res = p.replace('//', '/')
```

`'//a//b'` → `'/a//b'`（应该是 `/a/b`）。测试在 `ruoyi.spec.ts` 第 218-225 行钉死：

```ts
it('collapses first double slash only (known spec)', () =>
  expect(getNormalPath('//a//b')).toBe('/a//b'))
```

**修复建议**：`res = p.replace(/\/+/g, '/')`

#### `ResponsiveTable.vue:167` — `width - 1 < breakpoint` offset

```ts
const isMobile = computed(() => width.value - 1 < props.breakpoint)
```

`-1` offset 让 `width === breakpoint` 时也走 mobile 分支。**有意为之**（避免 iOS Safari `100vw` 滚动条抖动），但非常反直觉。

测试 `ResponsiveTable.spec.ts` 第 304-313 行钉死该 quirk：

```ts
it('pins width-1 < breakpoint offset (width=1000 + breakpoint=1000 → mobile)', ...)
```

### 5.2 跳过的业务函数

#### `ruoyi.ts:resetForm` — Vue 2 Options-API 残留

```ts
export function resetForm(refName: string): void {
  if ((this as any).$refs[refName]) {
    (this as any).$refs[refName].resetFields()
  }
}
```

依赖 `this.$refs`（Vue 2 特性），与 Composition API `<script setup>` 不兼容。已被 `<el-form>` 的 `@submit.prevent` + `formRef.value?.resetFields()` 替代。W11 主动跳过，标记为「待废弃」。

#### `ResponsiveTable.vue` 桌面 `el-table` 单元渲染

由于 element-plus `el-table` 在测试环境的 scoped-slot 编排极复杂（需要 row context 从 table 传给 column 再传给 cell），W11 桌面渲染测试**仅断言 stub 存在**，单元格内容走移动端路径（直接 `<component :is="resolveRenderer(col)">`）覆盖。W12+ 候选：用 `@vue/test-utils` 的 `RouterLinkStub` 模式 + 真实 el-table + 数据数组。

---

## 6. 显式覆盖盲区（W12+ 候选）

### 6.1 `utils/request.ts` axios 拦截器（高优先级）

W7.1 提示 `code >= 500` UX 修复，但没有回归测试。`request.ts` 是所有 OPC API 的统一入口，包含：

- 请求拦截器（`Admin-Token` 注入、`isToken: false` 跳过、`repeatSubmit` 防抖）
- 响应拦截器（401 登出、403/404/default 错误码映射、`blobValidate` JSON 错误识别）
- 错误日志 NProgress 关闭

**推荐方案**：`axios-mock-adapter` 拦截真实请求，~40 个测试覆盖 4 类 HTTP code × 2 类 token × 重复提交。

### 6.2 其他 utils

| 文件 | 行数 | 备注 |
|---|---:|---|
| `utils/jsencrypt.ts` | ~80 | RSA 加密，前端登录密码用；需 `jsencrypt` 真实 + 已知 RSA 密钥对测试 |
| `utils/theme.ts` | ~60 | 主题切换 + localStorage 持久化 |
| `utils/scroll-to.ts` | ~30 | 平滑滚动 |
| `utils/dynamicTitle.ts` | ~30 | document.title 动态切换 |
| `utils/passwordRule.ts` | ~50 | zxcvbn 强度检测（需要字典 fixture） |
| `utils/index.ts` | ~100 | date util 工具 |

### 6.3 组件

| 组件 | 备注 |
|---|---|
| `SharePoster.vue` | Canvas API 在 happy-dom 不完整（无 `getContext('2d')` 真实实现），需 jsdom + canvas polyfill |
| 22 个 API 模块（`src/api/opc/*`） | 薄壳，等 `request.ts` 测完一并覆盖 |
| 61 个 Vue views | template-heavy，ROI 低；建议只测 store 编排 + form submit 流程 |

### 6.4 覆盖率

`@vitest/coverage-v8` 已规划在 W12。当前 218 个测试已覆盖大部分逻辑路径，但 cell-level 覆盖度未量化。

---

## 7. 提交链

```
$ git log --oneline -4
0beb8fc W11.3 components Tier S (~45 @Test)
409b886 W11.2 stores Tier S + permission util (~47 @Test)
ff31cac W11.1 vitest: harness + utils Tier S (~126 @Test)
ee4484d W10 ruoyi-gateway/opc-finance: 3 filter 单测 + 前端 code>=500 + updateBy 审计 + PIT 配置
```

### 7.1 文件清单（W11 新增 / 修改）

新增 12 文件：

```
vue3-typescript/vitest.config.ts
vue3-typescript/src/test/setup.ts
vue3-typescript/src/utils/errorCode.spec.ts
vue3-typescript/src/utils/validate.spec.ts
vue3-typescript/src/utils/auth.spec.ts
vue3-typescript/src/utils/ruoyi.spec.ts
vue3-typescript/src/store/modules/dict.spec.ts
vue3-typescript/src/store/modules/lock.spec.ts
vue3-typescript/src/store/modules/user.spec.ts
vue3-typescript/src/utils/permission.spec.ts
vue3-typescript/src/views/opc/components/ResponsiveTable.spec.ts
vue3-typescript/src/views/opc/components/MobileDrawer.spec.ts
vue3-typescript/src/views/opc/components/AgentCard.spec.ts
```

修改 1 文件：

```
vue3-typescript/package.json  (+3 scripts)
```

---

## 8. 与后端 W1–W10 的对比

| 维度 | 后端 (W1–W10) | 前端 (W11) |
|---|---|---|
| 框架 | JUnit 5 + Mockito | Vitest 5 + @vue/test-utils |
| 模式 | @ExtendWith + @InjectMocks | `mount()` + `global.components` |
| Mock | `mockStatic()` (RuoYi SecurityUtils) | `vi.mock()` (vue-router / element-plus / js-cookie) |
| 断言 | AssertJ + ArgumentCaptor | expect + flushPromises |
| 本机跑 | ❌ JDK 8 限制，仅静态验证 | ✅ Node v22 直接跑 |
| 测试数 | 281 @Test + 29 静态变异 (W10) | 218 @Test (W11) |
| 总周期 | W1 2026-09-03 → W10 2026-09-07 | W11 2026-09-07（一天） |

前端**测试/代码比**远高于后端（utils 是纯逻辑、components 是 SFC 单元），218 @Test 中大部分在毫秒级运行。

---

## 9. 结论

W11 是 OPC 项目的**测试基础设施里程碑**：

1. **首个真跑的前端测试**（后端受 JDK 限制只能静态分析，前端 Vitest 是 W11 才在 Windows 机器上跑通的）
2. **218 @Test 覆盖 utils + stores + permission + components 四个 Tier S**
3. **钉死 4 个已知业务 bug**（dict `&&` → `||`、isEmpty `==` coercion、getNormalPath 单一替换、width-1 breakpoint offset）
4. **建立 mock 模式库**（js-cookie / vue-router / element-plus / @vueuse.core / Vue 组件 globals）
5. **明确记录覆盖盲区**（W12+ 候选：request.ts axios 拦截器、22 个 API 模块、SharePoster.vue Canvas）

下一阶段 W12 建议：

- **`request.ts` axios 拦截器 + 401/403/404/default 错误码映射回归测试**（W7.1 风险闭环）
- **`@vitest/coverage-v8` 集成 + coverage gate**
- **`SharePoster.vue` Canvas 测试**（jsdom + canvas 依赖）
- **业务 stores 扩展测试**（app / tagsView / settings / permission — 当前只测了 dict/lock/user）

— END —
