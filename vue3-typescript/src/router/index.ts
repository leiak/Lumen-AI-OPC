import { createWebHistory, createRouter } from 'vue-router'
/* Layout */
import Layout from '@/layout/index.vue'

/**
 * Note: 路由配置项
 *
 * hidden: true                     // 当设置 true 的时候该路由不会再侧边栏出现 如401，login等页面，或者如一些编辑页面/edit/1
 * alwaysShow: true                 // 当你一个路由下面的 children 声明的路由大于1个时，自动会变成嵌套的模式--如组件页面
 *                                  // 只有一个时，会将那个子路由当做根路由显示在侧边栏--如引导页面
 *                                  // 若你想不管路由下面的 children 声明的个数都显示你的根路由
 *                                  // 你可以设置 alwaysShow: true，这样它就会忽略之前定义的规则，一直显示根路由
 * redirect: noRedirect             // 当设置 noRedirect 的时候该路由在面包屑导航中不可被点击
 * name:'router-name'               // 设定路由的名字，一定要填写不然使用<keep-alive>时会出现各种问题
 * query: '{"id": 1, "name": "ry"}' // 访问路由的默认传递参数
 * roles: ['admin', 'common']       // 访问路由的角色权限
 * permissions: ['a:a:a', 'b:b:b']  // 访问路由的菜单权限
 * meta : {
    noCache: true                   // 如果设置为true，则不会被 <keep-alive> 缓存(默认 false)
    title: 'title'                  // 设置该路由在侧边栏和面包屑中展示的名字
    icon: 'svg-name'                // 设置该路由的图标，对应路径src/assets/icons/svg
    breadcrumb: false               // 如果设置为false，则不会在breadcrumb面包屑中显示
    activeMenu: '/system/user'      // 当路由设置了该属性，则会高亮相对应的侧边栏。
  }
 */

/**
 * 造一条不进侧边栏的 OPC 隐藏路由(详情页 / 新建页)。
 *
 * parent 用**绝对 path** 独占一段,child 用 `path: ''` —— vue-router 4 的
 * normalizeRoutePath 把空 child 解析成「与父路径相同」,于是详情页 URL 仍是
 * /opc/crm/customers/123。这样每条隐藏路由自成一个 Layout 分支,不必挂在某个
 * '/opc' parent 下面,也就不会和 sys_menu 树里的 '/opc' 撞名(撞名的后果见下方
 * OPC 路由注释)。
 *
 * parent 必须 hidden:true —— SidebarItem.vue:2 的 `v-if="!item.hidden"` 只看
 * 最外层那一层,child 上的 hidden 管不到。
 */
function opcHiddenRoute(path: string, component: () => Promise<any>, name: string, meta: Record<string, any>) {
  return {
    path,
    component: Layout,
    hidden: true,
    children: [{ path: '', component, name, meta, hidden: true }]
  }
}
// 公共路由
export const constantRoutes = [
  {
    path: '/redirect',
    component: Layout,
    hidden: true,
    children: [
      {
        path: '/redirect/:path(.*)',
        component: () => import('@/views/redirect/index.vue')
      }
    ]
  },
  {
    path: '/login',
    component: () => import('@/views/login.vue'),
    hidden: true
  },
  {
    path: '/register',
    component: () => import('@/views/register.vue'),
    hidden: true
  },
  {
    path: "/:pathMatch(.*)*",
    component: () => import('@/views/error/404.vue'),
    hidden: true
  },
  {
    path: '/401',
    component: () => import('@/views/error/401.vue'),
    hidden: true
  },
  {
    path: '',
    component: Layout,
    redirect: '/index',
    children: [
      {
        path: '/index',
        component: () => import('@/views/index.vue'),
        name: 'Index',
        meta: { title: '首页', icon: 'dashboard', affix: true }
      }
    ]
  },
  {
    path: '/lock',
    component: () => import('@/views/lock.vue'),
    hidden: true,
    meta: { title: '锁定屏幕' }
  },
  {
    path: '/opc/invite',
    component: () => import('@/views/opc/invite.vue'),
    hidden: true,
    meta: { title: '邀请加入', noAuth: true }
  },
  {
    path: '/user',
    component: Layout,
    hidden: true,
    redirect: 'noredirect',
    children: [
      {
        path: 'profile/:activeTab?',
        component: () => import('@/views/system/user/profile/index.vue'),
        name: 'Profile',
        meta: { title: '个人中心', icon: 'user' }
      }
    ]
  },
  // ================ OPC 路由 ================
  // sys_menu 是 OPC 侧边栏的**唯一**事实源:V20261022 + V20261023 共注册了
  // OPC业务 → 13 个模块目录 → 33 个页面。所以这里**不再有** `path: '/opc'`
  // 这个 Layout parent。
  //
  // 为什么必须删:它会与 sys_menu menu_id=2000 撞成两条同路径记录 ——
  // getRouterPath(SysMenuServiceImpl.java:459) 给顶级目录补前导 '/' 得到 '/opc',
  // 而 permission.ts:51 把 constantRoutes 和后端路由树**都** addRoute。
  // vue-router 按注册顺序匹配,先注册进来的 constantRoutes 会遮蔽后端那棵树,
  // 症状是「菜单看得见、一点就 404」。同理 /opc/crm/customers 这类路径,
  // 两边各有一条记录且 name 还不同(sys_menu 叫 OpcCrmCustomers,原来叫
  // OpcCustomerList),匹配到哪条不确定。
  //
  // 下面只剩 sys_menu 不覆盖的 11 个详情页 / 新建页:它们靠页内 router.push
  // 到达,不该出现在侧边栏,故各挂自己的绝对路径 + hidden:true。
  //
  // route_name 保留原值是有硬约束的:marketplace/index.vue:249 与
  // marketplace/author-center.vue:595 用的是按名跳转
  // `router.push({ name: 'OpcMarketplaceDetail', params: { id } })`。
  opcHiddenRoute('/opc/marketplace/detail/:id(\\d+)', () => import('@/views/opc/marketplace/detail.vue'), 'OpcMarketplaceDetail', { title: '商品详情', activeMenu: '/opc/marketplace/index' }),
  opcHiddenRoute('/opc/agent/detail/:id(\\d+)', () => import('@/views/opc/agent/detail.vue'), 'AgentDetail', { title: 'Agent 详情', activeMenu: '/opc/agent/market' }),
  opcHiddenRoute('/opc/agent/instance/:id(\\d+)', () => import('@/views/opc/agent/instance.vue'), 'AgentInstance', { title: '实例详情', activeMenu: '/opc/agent/instances' }),
  opcHiddenRoute('/opc/crm/customers/:id(\\d+)', () => import('@/views/opc/crm/CustomerDetail.vue'), 'OpcCustomerDetail', { title: '客户详情', activeMenu: '/opc/crm/customers' }),
  opcHiddenRoute('/opc/hr/job/:id(\\d+)', () => import('@/views/opc/hr/job/detail.vue'), 'OpcHrJobDetail', { title: 'JD 详情', activeMenu: '/opc/hr/job' }),
  opcHiddenRoute('/opc/hr/candidate/:id(\\d+)', () => import('@/views/opc/hr/candidate/detail.vue'), 'OpcHrCandidateDetail', { title: '候选人详情', activeMenu: '/opc/hr/candidate' }),
  opcHiddenRoute('/opc/erp/product/:id(\\d+)', () => import('@/views/opc/erp/product/detail.vue'), 'OpcErpProductDetail', { title: '商品详情', activeMenu: '/opc/erp/product' }),
  opcHiddenRoute('/opc/erp/purchase/new', () => import('@/views/opc/erp/purchase/new.vue'), 'OpcErpPurchaseNew', { title: '新建采购单', activeMenu: '/opc/erp/purchase' }),
  opcHiddenRoute('/opc/erp/sale/new', () => import('@/views/opc/erp/sale/new.vue'), 'OpcErpSaleNew', { title: '新建销售单', activeMenu: '/opc/erp/sale' }),
  opcHiddenRoute('/opc/content/script/:id(\\d+)', () => import('@/views/opc/content/script/detail.vue'), 'OpcContentScriptDetail', { title: '脚本详情', activeMenu: '/opc/content/script' }),
  opcHiddenRoute('/opc/content/script/:id(\\d+)/adapt', () => import('@/views/opc/content/script/adapt.vue'), 'OpcContentScriptAdapt', { title: '平台适配', activeMenu: '/opc/content/script' })
]

// 动态路由，基于用户权限动态去加载
export const dynamicRoutes = [
  {
    path: '/system/user-auth',
    component: Layout,
    hidden: true,
    permissions: ['system:user:edit'],
    children: [
      {
        path: 'role/:userId(\\d+)',
        component: () => import('@/views/system/user/authRole.vue'),
        name: 'AuthRole',
        meta: { title: '分配角色', activeMenu: '/system/user' }
      }
    ]
  },
  {
    path: '/system/role-auth',
    component: Layout,
    hidden: true,
    permissions: ['system:role:edit'],
    children: [
      {
        path: 'user/:roleId(\\d+)',
        component: () => import('@/views/system/role/authUser.vue'),
        name: 'AuthUser',
        meta: { title: '分配用户', activeMenu: '/system/role' }
      }
    ]
  },
  {
    path: '/system/dict-data',
    component: Layout,
    hidden: true,
    permissions: ['system:dict:list'],
    children: [
      {
        path: 'index/:dictId(\\d+)',
        component: () => import('@/views/system/dict/data.vue'),
        name: 'Data',
        meta: { title: '字典数据', activeMenu: '/system/dict' }
      }
    ]
  },
  {
    path: '/monitor/job-log',
    component: Layout,
    hidden: true,
    permissions: ['monitor:job:list'],
    children: [
      {
        path: 'index/:jobId(\\d+)',
        component: () => import('@/views/monitor/job/log.vue'),
        name: 'JobLog',
        meta: { title: '调度日志', activeMenu: '/monitor/job' }
      }
    ]
  },
  {
    path: '/tool/gen-edit',
    component: Layout,
    hidden: true,
    permissions: ['tool:gen:edit'],
    children: [
      {
        path: 'index/:tableId(\\d+)',
        component: () => import('@/views/tool/gen/editTable.vue'),
        name: 'GenEdit',
        meta: { title: '修改生成配置', activeMenu: '/tool/gen' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes: constantRoutes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    }
    return { top: 0 }
  },
})

export default router
