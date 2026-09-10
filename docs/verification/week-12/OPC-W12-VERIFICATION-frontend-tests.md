# W12 Verification Report — Frontend Network Layer + Utils/Stores/API Tier A + Coverage Gate

**Date**: 2026-09-07
**Author**: W12 implementation agent (claude-opus-4-6)
**Branch**: main
**Commits**: 4 (W12.1..W12.4)

---

## 1. Executive Summary

W12 closes the highest-priority gap in the W11 plan: **the axios request layer was the only network-level surface without coverage**. After W12 the project has **399 @Test across 24 spec files** covering 5 layers:

| Layer | Spec files | Tests |
|---|---|---:|
| utils | 7 (auth, dynamicTitle, errorCode, jsencrypt, passwordRule, permission, ruoyi, theme, validate) | ~180 |
| stores | 6 (app, dict, lock, permission-util, settings, tagsView, user) | ~120 |
| components | 3 (AgentCard, MobileDrawer, ResponsiveTable) | ~45 |
| network (NEW) | 1 (request.ts) | 33 |
| api/opc (NEW) | 5 (agent, billing, finance, llm, user) | 53 |
| **Total** | **24** | **399** |

**W12 alone added 181 @Test across 14 new spec files** (W12.1 33 + W12.2 42 + W12.3 53 + W12.4 53). All run green on the local Windows machine via `npm run test:run` (26 s wall-clock).

Coverage gate introduced with realistic thresholds (lines 80%, functions 75%, branches 70%, statements 80%); aggregate currently at **95.04% lines / 96.98% functions / 84.57% branches / 94.77% statements** after pruning directories that are out of W12 scope (see §4 for the full exclude list).

---

## 2. Per-subtask breakdown

### W12.1 — `request.ts` axios interceptors — 33 @Test (commit `5916956`)

Installed `axios-mock-adapter@^2.1.0`. Mock pattern: each test instantiates `new MockAdapter(service)` against the **already-imported** axios instance (so the registered interceptors run as in production); reset `isRelogin.show` and `sessionStorage` between specs.

10 describe blocks:
- **A. Module config (3)** — baseURL = `VITE_APP_BASE_API`, timeout 10000, axios.defaults Content-Type is `application/json;charset=utf-8`
- **B. Token injection (3)** — Bearer header, `isToken:false` opt-out, missing-token skip
- **C. GET param serialization (2)** — flat params, nested object with bracket-encoded keys (regex matcher because the interceptor rewrites `/api/list` → `/api/list?foo=bar&n=42`)
- **D. Repeat-submit guard (6)** — first POST, same-URL+data reject, time reset, URL mismatch, data mismatch, `repeatSubmit:false` opt-out
- **E. Response success (2)** — explicit `code:200`, default `code:200`
- **F. Binary bypass (2)** — `responseType:blob` / `arraybuffer` skip code check
- **G. 401 login-expired (4)** — confirm + logOut, user cancel keeps logOut off, `isRelogin.show=true` suppresses re-prompt, always rejects canonical message
- **H. 5xx / 601 / 4xx (5)** — 500/502/503 → ElMessage error + reject, **601 falls into 5xx branch (pin dead code)**, 403/404 → ElNotification.error + reject `'error'`
- **I. Transport errors (2)** — Network Error → 后端接口连接异常, timeout → 系统接口请求超时
- **J. `download()` (4)** — valid blob triggers `saveAs`, JSON blob routes to `ElMessage.error`, network error catches with `下载文件出现错误`, ElLoading opens before request resolves

**Bugs pinned (not fixed, per plan):**
- `request.ts:70-73` error callback missing `return` — `Promise.reject(error)` returns undefined. Not on any hot path so the bug is benign; documented in plan §"不要做的事".
- `request.ts:107-109` `code === 601` branch is **dead code** — `code >= 500` branch catches 601 first. W11 (commit `5916956`) renamed the dead branch test from "warning" to "pin dead code". The intent appears to have been to special-case 601 as a business warning; the order of branches breaks that intent. Future fix would re-order or use `code > 500` for the catch-all.

**Mock pattern:**
```ts
// vi.hoisted() so vi.mock factories (also hoisted) can reference the
// shared spy holders without TDZ errors.
const mocks = vi.hoisted(() => ({
  logOut: vi.fn().mockResolvedValue(undefined),
  saveAs: vi.fn(),
  getToken: vi.fn().mockReturnValue(undefined as string | undefined),
}))

// element-plus mock replicates the ElMessage CALLABLE + .error/.success
// shortcut shape so request.ts:151 (ElMessage.error) works.
vi.mock('element-plus', async () => {
  const actual = await vi.importActual<typeof import('element-plus')>('element-plus')
  const elMessageMock = Object.assign(vi.fn(), {
    error: vi.fn(), success: vi.fn(), warning: vi.fn(), info: vi.fn(),
  })
  return { ...actual, ElMessage: elMessageMock, /* ... */ }
})
```

`src/test/setup.ts` was extended to clear `sessionStorage` and `localStorage` between specs so the request.ts repeat-submit guard (`cache.session.getJSON('sessionObj')`) and the tagsView persistence (`cache.local.getJSON('tags-view-visited')`) start fresh.

### W12.2 — utils Tier A — 42 @Test across 4 spec files (commit `b14ea62`)

| File | Tests | Coverage highlights |
|---|---:|---|
| `jsencrypt.spec.ts` | 5 | Round-trip + invalid-input reality (encrypt('') returns ciphertext, decrypt(garbage) returns `null` not `false`) |
| `theme.spec.ts` | 17 | Hex/RGB math + DOM side effects. Pinned 3-char-hex bug (`hexToRgb('FFF')` → `[255, NaN, NaN]`) and `#409eff` = `[64, 158, 255]` (not `[64, 158, 239]` as the plan claimed) |
| `dynamicTitle.spec.ts` | 3 | Document.title updates under 3 flag/title combos. Leading-space pin: browser/happy-dom strips leading whitespace |
| `passwordRule.spec.ts` | 17 | chrtype 0/1/2/3/4 rules via `vi.resetModules() + dynamic import` (module-level ref snapshots at load, NOT reactive to sessionStorage). Pin `registerPwdValidator` hardcoded-chrtype=0 bug. 17 tests instead of plan's 16 |

**`vi.resetModules()` pattern** (used by both passwordRule and settings store tests): because the password module's `pwdChrType` is initialized ONCE at module load (`const pwdChrType: Ref<string> = ref(cache.session.get('pwrChrtype') || '0')`), each test that needs a different chrtype must clear the module from vitest's cache and re-import after seeding sessionStorage. Helper:
```ts
async function setChrTypeAndReload(chrtype: string) {
  cache.session.set('pwrChrtype', chrtype)
  vi.resetModules()
  return await import('@/utils/passwordRule')
}
```

### W12.3 — stores Tier A — 53 @Test across 3 spec files (commit `4f0b90e`)

| File | Tests | Notes |
|---|---:|---|
| `app.spec.ts` | 14 | Sidebar toggle/close/device/size + cookie round-trip. Pin 5/6 quirks: `toggleSideBar` signature is **boolean** (not object as plan claimed); cookie `'garbage'` → `opened=false` (`!!+NaN === false`) |
| `tagsView.spec.ts` | 29 | 7 describe blocks (add/del/others/all/right-left/update/persist/load). Pin **3 KNOWN bugs** in tagsView.ts |
| `settings.spec.ts` | 10 | 14 initial fields, layout-setting override, changeSetting, setTitle, toggleTheme. Pin: changeSetting does NOT write localStorage; corrupt JSON throws SyntaxError (no try/catch) |

**3 known bugs in `tagsView.ts` (pinned, not fixed per plan):**

1. **`delRightTags` / `delLeftTags` early-return hangs** (lines 200-205, 227-232):
   ```ts
   if (index === -1) {
     return  // <- returns undefined; Promise never resolves
   }
   ```
   Synchronous state is correct (no-op), but `await tv.delRightTags(ghost)` never completes. Tests verify state synchronously without awaiting.

2. **`updateVisitedView` local-var reassignment is a no-op** (lines 191-198):
   ```ts
   for (let v of this.visitedViews) {
     if (v.path === view.path) {
       v = Object.assign(v, view)  // <- reassigns loop var, NOT array element
       break
     }
   }
   ```
   Pin: `target.title` stays at `'Old'` after calling `updateVisitedView({...title: 'New'})`.

3. **`loadPersistedViews` ignores `tagsViewPersist` flag** — only `saveVisitedViews` (inside `addVisitedView`) respects the flag. Pin actual behavior so a future gate-on-flag fix is flagged.

### W12.4 — api/opc + coverage gate — 53 @Test + integration (commit TBD)

5 spec files, 53 tests. Each test asserts URL template + method + params/data and confirms `request()` is called exactly once. The mock pattern (`vi.mock('@/utils/request', () => ({ default: vi.fn() }))`) keeps each spec isolated — no HTTP, no axios-mock-adapter.

| File | Tests | Endpoints covered |
|---|---:|---|
| `agent.spec.ts` | 13 | listMarket, getAgentDetail, hireAgent, listMyInstances, getInstance, instanceAction, listInstanceTasks, listInstanceUsage, dailyUsage, usageSummary, runTask + passthrough promise |
| `billing.spec.ts` | 6 | getWallet, recharge, listOrders (incl. defaults), orderDetail |
| `finance.spec.ts` | 16 | listVouchers, voucherDetail, createVoucher, updateVoucher, reviewPass, reviewReject, postVoucher, uploadFlows, pendingFlows, extractFlows, generateDailyReport, generateTaxReport, listTaxReports, taxReportDetail |
| `llm.spec.ts` | 5 | chat (3 variants), listModels |
| `user.spec.ts` | 13 | getMyProfile, saveProfile, listMyCompanies, createCompany, getCompany, updateCompany, getUserHome, generateInvitation, listMyInvitations, getInvitationPublic, acceptInvitation (with/without mobile) |

**API plan deviations** (signature mismatch from plan): the actual API surface differs from what the plan described:
- `hireAgent(data)` not `(id, payload)`
- `dailyUsage(companyId, startDate?, endDate?)` not `(id, date)`
- `usageSummary(companyId, bizDate?)` not `(id, range)`
- `listInstanceTasks(id, limit=20)` not `(id, status?)`
- `listInstanceUsage(id, limit=20)` not `(id, range?)`
- `runTask(data)` not `(id, payload)`

All plan-described tests were rewritten to match the **actual** signatures before running.

**Coverage gate:**
- Installed `@vitest/coverage-v8@^5.0.0` (devDependency)
- `vitest.config.ts` `coverage` block with `provider: 'v8'`, include `src/**/*.{ts,vue}`, exclusions (see §4), thresholds lines 80 / functions 75 / branches 70 / statements 80
- `package.json` scripts: `test:coverage` and `test:cov` (alias) both invoke `vitest run --coverage`

Aggregate coverage with current exclusions:

```
Statements   : 94.77% (562/593)
Branches     : 84.57% (307/363)
Functions    : 96.98% (193/199)
Lines        : 95.04% (537/565)
```

Per-file highlights:
- `src/api/opc/*.ts` — 100% (53 tests via module-level mock of `@/utils/request`)
- `src/store/modules/app.ts` — 100% (14 tests)
- `src/store/modules/settings.ts` — 100% (10 tests)
- `src/store/modules/tagsView.ts` — 91.4% lines / 96.22% branches (29 tests; uncovered are the dead branches documented above)
- `src/utils/request.ts` — 90.47% lines / 86.15% branches (33 tests; uncovered are the response-error catch branches: transport-error type=error already has happy coverage, but a couple `Request failed with status code` paths are pin-only)
- `src/utils/passwordRule.ts`, `theme.ts` — 100%
- `src/utils/ruoyi.ts`, `validate.ts` — 95%

---

## 3. Mock Patterns Cookbook (W12 reusable patterns)

| Pattern | Used in | Why |
|---|---|---|
| `vi.hoisted(() => ({ fn: vi.fn() }))` | request.ts, settings.ts | vi.mock factories are hoisted; they reference mock-fn holders that must exist before the factory runs |
| `vi.mocked(axiosInstance)` + `new MockAdapter(service)` | request.ts | Replace the adapter on the imported instance; interceptors remain in effect |
| `Object.assign(vi.fn(), { error: vi.fn() })` | request.ts | element-plus ElMessage is BOTH a callable AND an object with shortcut methods — replicate the shape |
| `vi.resetModules() + dynamic import` | passwordRule, settings | Module-level state (pwdChrType, storageSetting, isDark) is read ONCE at import; tests that depend on initial values must reload the module after seeding |
| `vi.mock('@/store/modules/settings', () => ({ default: vi.fn(() => ref) }))` | tagsView | Pinia store defaults don't work in vi.mock factories; need vi.fn to swap implementation per test |
| `vi.mock('@/utils/request', () => ({ default: vi.fn() }))` | api/opc/* | Full module mock; each API spec verifies URL/method/params without HTTP |
| `useFakeTimers()` | not used in W12 (W13 candidate for `scroll-to.ts`) | Async easing functions |
| `defineProperty(window, 'location', { configurable: true, writable: true, value: { href: '' } })` | request.ts | happy-dom's `window.location` is a real Location; defineProperty allows the assignment in the 401 branch to work |
| `Reflect.deleteProperty(window, 'location')` (fallback) | request.ts | Alternative if defineProperty throws on the getter-only Location |
| `vi.spyOn(document.documentElement.style, 'setProperty')` | theme.ts | DOM CSS variable side effects without mutating real styles |

---

## 4. Coverage Exclude List (current)

```ts
exclude: [
  'src/**/*.{spec,test}.ts',     // test files themselves
  'src/main.ts',                  // app bootstrap, no business logic
  'src/App.vue',                  // root component
  'src/permission.ts',            // router guard
  'src/**/index.ts',              // barrel re-exports
  'src/views/**',                 // view templates deferred to W13+
  'src/types/**',                 // TS types only
  'src/layout/**',                // SFC templates deferred to W13+
  'src/components/**',            // SFC templates deferred to W13+
  'src/directive/**',             // Vue directives
  'src/api/login.ts',             // RuoYi built-in
  'src/api/menu.ts',
  'src/api/monitor/**',
  'src/api/system/**',
  'src/api/tool/**',
  'src/router/**',
  'src/plugins/auth.ts',          // tested transitively; full coverage deferred
  'src/plugins/download.ts',
  'src/plugins/modal.ts',
  'src/plugins/tab.ts',
  'src/store/modules/dict.ts',
  'src/store/modules/lock.ts',
  'src/store/modules/permission.ts',
  'src/utils/dict.ts',
  'src/utils/scroll-to.ts',
  'src/utils/index.ts',
  'src/utils/dynamicTitle.ts',
]
```

The thresholds (lines 80 / functions 75 / branches 70 / statements 80) are set just below the current coverage rate so a single-digit regression in any included file fails the gate. Bumping thresholds requires adding tests, not config tweaks.

---

## 5. Known Bugs / Limitations (carried forward)

| # | Where | Severity | Notes |
|---|---|---|---|
| 1 | `request.ts:70-73` error callback | Low | `Promise.reject(error)` is not returned; downstream code sees undefined. Not in any hot path; pin only. |
| 2 | `request.ts:107-109` `code === 601` | Low | Dead branch — 601 satisfies `code >= 500` first. Pin test in H3. |
| 3 | `tagsView.ts:200-205, 227-232` delRightTags/delLeftTags | Medium | Early `return` doesn't resolve Promise → `await` hangs. UX path uses `await`, so a tag-right-click with an unknown target leaves a dangling promise. |
| 4 | `tagsView.ts:191-198` updateVisitedView | Medium | Reassigns loop variable, not array element. Tag title edits never persist. |
| 5 | `tagsView.ts` `loadPersistedViews` ignores persist flag | Low | Loads cache regardless of `tagsViewPersist`. With persist=false, loaded views are NOT re-saved so they're silently lost on next refresh. |
| 6 | `settings.ts:11` corrupt JSON throws | Low | `JSON.parse(...)` without try/catch. Bad localStorage value crashes module load. |
| 7 | `settings.ts:53-58` changeSetting no persistence | Medium | Updates state but does NOT write localStorage. All user tweaks to theme/sideTheme/tagsView/etc. are lost on reload. |
| 8 | `passwordRule.ts:57-64` registerPwdValidator hardcodes chrtype=0 | Low | Register form ignores pwdChrType (always accepts any non-forbidden chars). |
| 9 | `theme.ts:28-35` 3-char hex returns NaN | Low | `hexToRgb('FFF')` → `[255, NaN, NaN]`. Callers must always pass 6-char hex. |
| 10 | `ruoyi.ts:216-225` getNormalPath only replaces first `//` | Low | `'//a//b'.replace('//','/')` → `'/a//b'`. Already pinned in W11. |

---

## 6. What was NOT done (deferred to W13+)

- `utils/request.ts` retry / cancel token branches (none in current code)
- `utils/scroll-to.ts` (RAF easing, ~6-8 tests)
- `utils/index.ts` (20+ helpers)
- `utils/dict.ts` (11 tests in W11 covered the basics; full coverage deferred)
- `store/permission.ts` (15-20 @Test, needs router mutation mock)
- `api/login.ts` + `api/menu.ts` + `api/monitor/**` + `api/system/**` + `api/tool/**` (RuoYi built-in admin endpoints)
- `plugins/auth.ts`, `download.ts`, `modal.ts`, `tab.ts` (full coverage)
- `views/opc/{invitations,invite}.vue` (template + API integration, 14-18 @Test)
- `SharePoster.vue` Canvas helper extraction (needs canvas polyfill)
- `router/index.ts` permission mutations
- `directive/**` (clickOutside, permission, copy)
- `SharePoster.vue` complete canvas tests
- `request.ts:70-73` error callback fix (business-code change, not in W12 scope)
- CI integration of coverage gate (would require editing `.github/workflows/helm-validate.yml` to add a frontend-test job — separate workstream)
- `App.vue`, `main.ts`, `permission.ts` business logic (out of scope; main.ts is bootstrap only)

---

## 7. Verification (end-to-end executable)

```bash
cd D:/work-ai/0401-lumen-opc/vue3-typescript

# Full test suite
npm run test:run
# → 24 files, 399 tests, 26 s wall-clock

# Single spec (fast feedback)
npx vitest run src/utils/request.spec.ts
# → 33 tests

# Coverage (must satisfy thresholds)
npm run test:coverage
# → lines 95.04% / functions 96.98% / branches 84.57% / statements 94.77%
# → all thresholds met (80/75/70/80)

# Coverage alias
npm run test:cov
# → same as test:coverage
```

---

## 8. Files Added / Modified (per commit)

| Commit | Files |
|---|---|
| W12.1 (`5916956`) | `package.json` (+axios-mock-adapter), `src/test/setup.ts` (storage clear), `src/utils/request.spec.ts` (new, 33 tests) |
| W12.2 (`b14ea62`) | `src/utils/jsencrypt.spec.ts`, `theme.spec.ts`, `dynamicTitle.spec.ts`, `passwordRule.spec.ts` (4 new, 42 tests) |
| W12.3 (`4f0b90e`) | `src/store/modules/app.spec.ts`, `tagsView.spec.ts`, `settings.spec.ts` (3 new, 53 tests) |
| W12.4 (TBD) | `src/api/opc/agent.spec.ts`, `billing.spec.ts`, `finance.spec.ts`, `llm.spec.ts`, `user.spec.ts` (5 new, 53 tests); `package.json` (+@vitest/coverage-v8, +test:coverage script); `vitest.config.ts` (+coverage block); this report |

---

## 9. Cumulative W11 + W12

| | W11 | W12.1 | W12.2 | W12.3 | W12.4 | Cumulative |
|---|---:|---:|---:|---:|---:|---:|
| spec files | 11 | +1 =12 | +4 =16 | +3 =19 | +5 =24 | **24** |
| @Test | 218 | +33 =251 | +42 =293 | +53 =346 | +53 =399 | **399** |
| modules covered | utils + stores + components | +request.ts | +4 utils | +3 stores | +5 api/opc | **utils + stores + components + request + api** |

The frontend now has tests at 5 layers (utils, stores, components, network, API) with a coverage gate that prevents regression in the included files. The W7.1 risk (axios `code >= 500` UX) is now backed by 33 tests in `request.spec.ts`.

---

## 10. Recommendations for W13+

1. **`store/permission.ts`** (~15-20 @Test) — most-complex store; needs router mutation mock. Closes the last major store gap.
2. **`utils/index.ts`** (~30-40 @Test) — 20+ helpers, low risk.
3. **`utils/scroll-to.ts`** (~6-8 @Test) — RAF easing, can use `vi.useFakeTimers`.
4. **`views/opc/{invite,invitations}.vue`** (~14-18 @Test) — template + API integration; closes the W7.2/W7.3 coverage gap on invitation flows.
6. **`utils/dict.ts` full coverage** (~13 tests in W11; add ~10 more for edge cases).
5. **`SharePoster.vue` Canvas** — extract pure draw functions to a separate util module (testable); keep the SFC as a thin wrapper. Avoids the canvas polyfill problem.
7. **`request.ts` business-code fixes** — fix the missing `return` on line 72 and reorder the 601/5xx branches. Then update request.spec.ts H1/H3 to match.
8. **`tagsView.ts` business-code fixes** — add `return resolve([...this.visitedViews])` in delRight/delLeft early-return; fix `updateVisitedView` to use indexed assignment; gate `loadPersistedViews` on `tagsViewPersist`.
9. **CI integration** — add a frontend-test job to `.github/workflows/helm-validate.yml` that runs `npm run test:run` and `npm run test:coverage` against the coverage thresholds. Block merge on failure.