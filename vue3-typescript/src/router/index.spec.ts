import { describe, it, expect } from 'vitest'
import { createRouter, createMemoryHistory } from 'vue-router'
import { constantRoutes } from './index'

/**
 * OPC 路由「唯一事实源」回归测试
 *
 * <p><b>缺陷形状</b>:修「侧边栏菜单全平铺、没有层级结构」时,根因不在后端 ——
 * {@code SysMenuServiceImpl#getChildPerms} 建树一直是对的(见 ruoyi-system 的
 * {@code SysMenuHierarchyTest})。真正的原因是 {@code store/modules/permission.ts}
 * 把两棵树**混**在一起:
 *
 * <pre>
 *   :48  setSidebarRouters(constantRoutes.concat(sidebarRoutes))
 *   :51  accessRoutes.forEach(route =&gt; router.addRoute(route))
 * </pre>
 *
 * 而 {@code constantRoutes} 里当时还硬编码着 {@code path: '/opc'} 这个 Layout parent,
 * 底下 41 个子路由全是平级(community/market、marketplace/index、crm/customers、
 * hr/job、erp/product、content/script … 同级)。于是侧边栏同时出现:
 * 一个标题叫「OPC 社区」的分组 + 41 个**没有二级分组**的菜单项,
 * 外加 sys_menu 那棵 {@code OPC业务 → 8 模块 → 24 页} 的三层树。重复且形态冲突。
 *
 * <p>更隐蔽的一半:两条 {@code /opc} 同路径记录并存,detail 这类路径两边各一条
 * 且 name 还不同({@code sys_menu} 叫 {@code OpcCrmCustomers},原来叫
 * {@code OpcCustomerList})—— 侧边栏看得见菜单,一点可能 404。
 *
 * <p><b>修法</b>:{@code sys_menu} 成为唯一事实源(V20261022 + V20261023 注册了
 * OPC业务 → 13 模块 → 33 页),{@code constantRoutes} 只保留 {@code sys_menu} 不覆盖的
 * 11 个详情页 / 新建页,各挂绝对路径 + hidden。
 *
 * <p>这里钉住三件事:没有 {@code '/opc'} 顶层 Layout parent、{@code constantRoutes}
 * 里所有 OPC 条目都 hidden(不污染侧边栏)、以及**用真实 vue-router 验证那 11 条
 * 隐藏路由仍然匹配得上**(URL 与改写前逐字一致)。
 *
 * @author OAC
 */
describe('constantRoutes 的 OPC 部分', () => {
  const opcTopLevel = constantRoutes.filter((r: any) => typeof r.path === 'string' && r.path.startsWith('/opc'))
  const router = createRouter({ history: createMemoryHistory(), routes: constantRoutes as any })

  it('不存在 path 为 /opc 的顶层 Layout parent', () => {
    // sys_menu menu_id=2000 经 getRouterPath(:459) 补前导 '/' 得到 '/opc'。
    // constantRoutes 若也声明一条,permission.ts:51 会把两条都 addRoute,
    // vue-router 按注册顺序匹配,先来的这条遮蔽后端那棵树。
    const exact = opcTopLevel.filter((r: any) => r.path === '/opc')
    expect(exact).toHaveLength(0)
  })

  it('不存在 path 为 /opc/insight 的常量路由(已由 sys_menu 2028-2032 接管)', () => {
    expect(opcTopLevel.filter((r: any) => r.path === '/opc/insight')).toHaveLength(0)
  })

  it('常量路由里只剩 12 条 OPC 条目:11 个详情页 / 新建页 + 公开邀请落地页,全部 hidden', () => {
    // 第 12 条 /opc/invite 是**改写前就存在**的公开邀请落地页(noAuth:true),
    // 它不挂 Layout、也没有 children —— 匿名访客不该看见后台外壳,刻意如此。
    expect(opcTopLevel.map((r: any) => r.path).sort()).toEqual([
      '/opc/agent/detail/:id(\\d+)',
      '/opc/agent/instance/:id(\\d+)',
      '/opc/content/script/:id(\\d+)',
      '/opc/content/script/:id(\\d+)/adapt',
      '/opc/crm/customers/:id(\\d+)',
      '/opc/erp/product/:id(\\d+)',
      '/opc/erp/purchase/new',
      '/opc/erp/sale/new',
      '/opc/hr/candidate/:id(\\d+)',
      '/opc/hr/job/:id(\\d+)',
      '/opc/invite',
      '/opc/marketplace/detail/:id(\\d+)'
    ])
    // SidebarItem.vue:2 的 `v-if="!item.hidden"` 只看最外层这一层,
    // child 上的 hidden 管不到 —— 所以 parent 必须 hidden:true。
    for (const r of opcTopLevel) {
      expect(r.hidden, `${r.path} 必须 hidden,否则会出现在侧边栏`).toBe(true)
      if (r.path === '/opc/invite') {
        continue
      }
      expect(r.component, `${r.path} 必须挂 Layout,否则详情页没有侧边栏/顶栏外壳`).toBeTruthy()
      expect(r.children).toHaveLength(1)
      // child 用 path:'' —— vue-router 4 的 normalizeRoutePath 把它解析成
      // 「与父路径相同」,URL 才不会多出一段
      expect(r.children[0].path).toBe('')
    }
  })

  it('详情页 URL 与改写前逐字一致,route_name 也保留 —— 11 条全部 resolve 到预期 name', () => {
    // route_name 保留原值是硬约束:marketplace/index.vue:249 与
    // marketplace/author-center.vue:595 用的是按名跳转
    // router.push({ name: 'OpcMarketplaceDetail', params: { id } })
    const expected: Array<[string, string]> = [
      ['/opc/marketplace/detail/7', 'OpcMarketplaceDetail'],
      ['/opc/agent/detail/7', 'AgentDetail'],
      ['/opc/agent/instance/7', 'AgentInstance'],
      ['/opc/crm/customers/7', 'OpcCustomerDetail'],
      ['/opc/hr/job/7', 'OpcHrJobDetail'],
      ['/opc/hr/candidate/7', 'OpcHrCandidateDetail'],
      ['/opc/erp/product/7', 'OpcErpProductDetail'],
      ['/opc/erp/purchase/new', 'OpcErpPurchaseNew'],
      ['/opc/erp/sale/new', 'OpcErpSaleNew'],
      ['/opc/content/script/7', 'OpcContentScriptDetail'],
      ['/opc/content/script/7/adapt', 'OpcContentScriptAdapt']
    ]
    for (const [url, name] of expected) {
      const resolved = router.resolve(url)
      expect(resolved.matched.length, `${url} 没匹配到任何路由`).toBeGreaterThan(0)
      expect(resolved.name, `${url} 匹配到了错误的 route name`).toBe(name)
      // 注意断言的是 resolved.path(解析后的完整 URL),不是 matched[i].path ——
      // 后者是路由**模式**('/opc/crm/customers/:id(\d+)'),对参数化段本就不相等
      expect(resolved.path, `${url} 的解析路径不对`).toBe(url)
    }
  })

  it('非数字 id 不匹配详情页 —— :id(\\d+) 正则仍然生效', () => {
    // constantRoutes 里有 '/:pathMatch(.*)*' 兜底路由(51 行),所以 resolve 不会
    // 返回空 matched,而是落到 404 页。这里断言的是「没匹配到详情页」。
    const resolved = router.resolve('/opc/crm/customers/abc')
    expect(resolved.name).not.toBe('OpcCustomerDetail')
    expect(resolved.matched[0].name).not.toBe('OpcCustomerDetail')
  })

  it('详情页路由不与 sys_menu 生成的列表页路径撞名', () => {
    // sys_menu 侧边栏会给出的 OPC 路径(顶级目录 + 叶子)。constantRoutes 里的
    // 隐藏路由只能以这些路径的**更深**段出现,不能与它们同路径 ——
    // 同路径双记录会让 vue-router 匹配到哪条不确定。
    const sysMenuPaths = [
      '/opc',
      '/opc/index',
      '/opc/marketplace', '/opc/marketplace/index', '/opc/marketplace/author-center', '/opc/marketplace/author-apply',
      '/opc/community/market',
      '/opc/notification/inbox',
      '/opc/crm/customers', '/opc/crm/opportunities', '/opc/crm/follow-ups',
      '/opc/hr/dashboard', '/opc/hr/job', '/opc/hr/candidate',
      '/opc/erp/product', '/opc/erp/purchase', '/opc/erp/sale', '/opc/erp/return', '/opc/erp/inventory',
      '/opc/content/dashboard', '/opc/content/script', '/opc/content/platform-account', '/opc/content/publish',
      '/opc/insight/dashboard', '/opc/insight/alerts', '/opc/insight/daily', '/opc/insight/advice',
      '/opc/agent/market', '/opc/agent/instances',
      '/opc/finance/vouchers', '/opc/finance/flows', '/opc/finance/tax-reports',
      '/opc/billing/wallet',
      '/opc/llm/chat',
      '/opc/user/profile', '/opc/invitations'
    ]
    for (const r of opcTopLevel) {
      expect(sysMenuPaths, `constantRoutes 的 ${r.path} 与 sys_menu 路径同路径 —— 会产生两条记录`).not.toContain(r.path)
    }
  })
})
