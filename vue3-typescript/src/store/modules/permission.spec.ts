// W13 — src/store/modules/permission.spec.ts.
// Pinned behaviors:
// - State init = 5 empty arrays
// - Setters copy or prepend constantRoutes (setRoutes/setDefaultRoutes only)
// - generateRoutes() fans out to 3 deep-clones + 3 filterAsyncRouter variants
//   (sidebarRoutes type=false, rewriteRoutes type=true, defaultRoutes type=false)
// - filterAsyncRouter(type=true) FLATTENS ParentView so grandchildren land
//   directly on the caller's children array with rebased path '<p>/<g>'
// - filterAsyncRouter(type=false) KEEPS ParentView as a wrapper (no flatten,
//   no rebase)
// - filterDynamicRoutes() routes by permissions vs roles (if/else if)
// - loadView() resolves views via the import.meta.glob (login exists)
// - loadView() returns undefined when no view matches
// - generateRoutes() promise HANGS if getRouters rejects (no .catch handler —
//   known bug, do NOT fix per W12 do-not-do rule)
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  getRouters: vi.fn(),
  authHasPermiOr: vi.fn(),
  authHasRoleOr: vi.fn(),
  routerAddRoute: vi.fn(),
}))

vi.mock('@/api/menu', () => ({ getRouters: mocks.getRouters }))
vi.mock('@/plugins/auth', () => ({
  default: {
    hasPermiOr: mocks.authHasPermiOr,
    hasRoleOr: mocks.authHasRoleOr,
  },
}))
vi.mock('@/router', () => ({
  default: { addRoute: mocks.routerAddRoute },
  constantRoutes: [{ path: '/login', name: 'Login' }],
  dynamicRoutes: [
    { path: '/dyn1', component: 'Layout', permissions: ['p1'] },
    { path: '/dyn2', component: 'Layout', roles: ['admin'] },
    { path: '/dyn3', component: 'Layout' },
  ],
}))
vi.mock('@/layout/index.vue', () => ({ default: 'Layout-stub' }))
vi.mock('@/components/ParentView/index.vue', () => ({ default: 'ParentView-stub' }))
vi.mock('@/layout/components/InnerLink/index.vue', () => ({ default: 'InnerLink-stub' }))

import { getRouters } from '@/api/menu'
import usePermissionStore, {
  filterDynamicRoutes,
  loadView,
} from '@/store/modules/permission'

const getRoutersMock = vi.mocked(getRouters)

beforeEach(() => {
  vi.clearAllMocks()
})

describe('permission store — state', () => {
  it('1. initial state is 5 empty arrays', () => {
    const store = usePermissionStore()
    expect(store.routes).toEqual([])
    expect(store.addRoutes).toEqual([])
    expect(store.defaultRoutes).toEqual([])
    expect(store.topbarRouters).toEqual([])
    expect(store.sidebarRouters).toEqual([])
  })
})

describe('permission store — setters', () => {
  it('2. setRoutes(routes) sets addRoutes AND routes = constantRoutes.concat(routes)', () => {
    const store = usePermissionStore()
    store.setRoutes([{ path: '/dyn' }])
    expect(store.addRoutes).toEqual([{ path: '/dyn' }])
    expect(store.routes).toEqual([
      { path: '/login', name: 'Login' },
      { path: '/dyn' },
    ])
  })

  it('3. setDefaultRoutes(routes) sets defaultRoutes = constantRoutes.concat(routes)', () => {
    const store = usePermissionStore()
    store.setDefaultRoutes([{ path: '/def' }])
    expect(store.defaultRoutes).toEqual([
      { path: '/login', name: 'Login' },
      { path: '/def' },
    ])
  })

  it('4. setTopbarRoutes(routes) is direct assignment (no constantRoutes concat)', () => {
    const store = usePermissionStore()
    store.setTopbarRoutes([{ path: '/top' }])
    expect(store.topbarRouters).toEqual([{ path: '/top' }])
  })

  it('5. setSidebarRouters(routes) is direct assignment (no constantRoutes concat)', () => {
    const store = usePermissionStore()
    store.setSidebarRouters([{ path: '/side' }])
    expect(store.sidebarRouters).toEqual([{ path: '/side' }])
  })
})

describe('permission store — generateRoutes()', () => {
  const backendRoute = {
    path: '/menu1',
    component: 'Layout',
    children: [
      { path: 'child', component: 'system/user/index' },
      { path: 'link', component: 'InnerLink' },
      {
        path: 'parent',
        component: 'ParentView',
        children: [{ path: 'grand', component: 'system/role/index' }],
      },
    ],
  }

  it('6. resolves with rewriteRoutes (length matches input)', async () => {
    getRoutersMock.mockResolvedValueOnce({ data: [backendRoute] })
    mocks.authHasPermiOr.mockReturnValue(false)
    mocks.authHasRoleOr.mockReturnValue(false)
    const store = usePermissionStore()
    const result = await store.generateRoutes()
    expect(result).toHaveLength(1)
    expect(result[0].path).toBe('/menu1')
  })

  it('7. populates all 5 state fields after a successful call', async () => {
    getRoutersMock.mockResolvedValueOnce({ data: [backendRoute] })
    mocks.authHasPermiOr.mockReturnValue(false)
    mocks.authHasRoleOr.mockReturnValue(false)
    const store = usePermissionStore()
    await store.generateRoutes()
    expect(store.addRoutes).toHaveLength(1)
    expect(store.routes.length).toBeGreaterThan(0)
    expect(store.routes[0]).toEqual({ path: '/login', name: 'Login' })
    expect(store.defaultRoutes.length).toBeGreaterThan(0)
    expect(store.topbarRouters).toHaveLength(1)
    expect(store.sidebarRouters.length).toBeGreaterThan(0)
  })

  it('8. calls getRouters exactly once', async () => {
    getRoutersMock.mockResolvedValueOnce({ data: [backendRoute] })
    mocks.authHasPermiOr.mockReturnValue(false)
    mocks.authHasRoleOr.mockReturnValue(false)
    const store = usePermissionStore()
    await store.generateRoutes()
    expect(getRoutersMock).toHaveBeenCalledTimes(1)
  })

  it('9. replaces top-level "Layout" component string with stub', async () => {
    getRoutersMock.mockResolvedValueOnce({ data: [backendRoute] })
    mocks.authHasPermiOr.mockReturnValue(false)
    mocks.authHasRoleOr.mockReturnValue(false)
    const store = usePermissionStore()
    const [rewrite] = await store.generateRoutes()
    expect(rewrite.component).toBe('Layout-stub')
  })

  it('10. replaces "InnerLink" component string with stub (not flattened)', async () => {
    getRoutersMock.mockResolvedValueOnce({ data: [backendRoute] })
    mocks.authHasPermiOr.mockReturnValue(false)
    mocks.authHasRoleOr.mockReturnValue(false)
    const store = usePermissionStore()
    const [rewrite] = await store.generateRoutes()
    const linkChild = rewrite.children.find((c: any) => c.path === 'link')
    expect(linkChild.component).toBe('InnerLink-stub')
  })

  it('11. rewriteRoutes (type=true) FLATTENS ParentView — grandchildren land directly on parent', async () => {
    // filterChildren() walks ParentView children and CONCATs them into the
    // caller's array — the ParentView wrapper itself is consumed (NOT added
    // to the children array). Grandchild's path is rebased to
    // '<parent.path>/<grand.path>' = 'parent/grand' (parent.path is 'parent',
    // since the outer filterChildren is called with lastRouter=false).
    getRoutersMock.mockResolvedValueOnce({ data: [backendRoute] })
    mocks.authHasPermiOr.mockReturnValue(false)
    mocks.authHasRoleOr.mockReturnValue(false)
    const store = usePermissionStore()
    const [rewrite] = await store.generateRoutes()
    // The ParentView wrapper is NOT in rewrite.children (flattened out).
    expect(
      rewrite.children.find((c: any) => c.path === 'parent'),
    ).toBeUndefined()
    // The grandchild IS in rewrite.children, with rebased 'parent/grand' path.
    const flattenedGrand = rewrite.children.find(
      (c: any) => c.path === 'parent/grand',
    )
    expect(flattenedGrand).toBeDefined()
    // No bare 'grand' path remains.
    expect(
      rewrite.children.find((c: any) => c.path === 'grand'),
    ).toBeUndefined()
    // rewrite.children has 3 entries: child + link + flattenedGrand.
    expect(rewrite.children).toHaveLength(3)
  })

  it('12. sidebarRoutes (type=false) KEEPS ParentView wrapper (no flatten, no rebase)', async () => {
    // filterChildren() is gated by `type && route.children` — type=false
    // skips it entirely. ParentView stays as a separate child route; its
    // grandchild path is un-rebased ('grand').
    getRoutersMock.mockResolvedValueOnce({ data: [backendRoute] })
    mocks.authHasPermiOr.mockReturnValue(false)
    mocks.authHasRoleOr.mockReturnValue(false)
    const store = usePermissionStore()
    await store.generateRoutes()
    // sidebarRouters = constantRoutes.concat(sidebarRoutes) — find /menu1.
    const sidebarMenu = store.sidebarRouters.find((r: any) => r.path === '/menu1')
    expect(sidebarMenu).toBeDefined()
    expect(sidebarMenu.component).toBe('Layout-stub')
    const parentChild = sidebarMenu.children.find((c: any) => c.path === 'parent')
    expect(parentChild).toBeDefined()
    // ParentView component is replaced via filterAsyncRouter recursion.
    expect(parentChild.component).toBe('ParentView-stub')
    // Grandchild is nested under parent with un-rebased path.
    const grandChild = parentChild.children.find((c: any) => c.path === 'grand')
    expect(grandChild).toBeDefined()
    expect(
      parentChild.children.find((c: any) => c.path === 'parent/grand'),
    ).toBeUndefined()
  })

  it('13. router.addRoute called for permission-matching dynamic routes ONLY', async () => {
    getRoutersMock.mockResolvedValueOnce({ data: [] })
    mocks.authHasPermiOr.mockImplementation((perms: string[]) => perms.includes('p1'))
    mocks.authHasRoleOr.mockReturnValue(false)
    const store = usePermissionStore()
    await store.generateRoutes()
    expect(mocks.routerAddRoute).toHaveBeenCalledTimes(1)
    expect(mocks.routerAddRoute).toHaveBeenCalledWith(
      expect.objectContaining({ path: '/dyn1' }),
    )
  })

  it('14. router.addRoute called for role-matching dynamic routes ONLY', async () => {
    getRoutersMock.mockResolvedValueOnce({ data: [] })
    mocks.authHasPermiOr.mockReturnValue(false)
    mocks.authHasRoleOr.mockImplementation((roles: string[]) => roles.includes('admin'))
    const store = usePermissionStore()
    await store.generateRoutes()
    expect(mocks.routerAddRoute).toHaveBeenCalledTimes(1)
    expect(mocks.routerAddRoute).toHaveBeenCalledWith(
      expect.objectContaining({ path: '/dyn2' }),
    )
  })

  it('15. dynamic route with neither permissions nor roles is excluded', async () => {
    getRoutersMock.mockResolvedValueOnce({ data: [] })
    mocks.authHasPermiOr.mockReturnValue(false)
    mocks.authHasRoleOr.mockReturnValue(false)
    const store = usePermissionStore()
    await store.generateRoutes()
    expect(mocks.routerAddRoute).not.toHaveBeenCalled()
  })

  it('16. generateRoutes promise HANGS if getRouters never resolves (no .catch)', async () => {
    // KNOWN BUG (do not fix per W12 rules): the source wraps getRouters() in
    // `new Promise(resolve => { getRouters().then(res => resolve(...)) })`
    // with NO .catch on the inner .then. If getRouters() rejects, the outer
    // promise stays PENDING forever (the rejection becomes an unhandled
    // rejection in node, but generateRoutes() never settles).
    //
    // We exercise the hang by returning a never-settling promise (instead of
    // a rejecting one) to avoid the unhandled-rejection noise — both failure
    // modes leave the outer promise pending, which is the bug being pinned.
    mocks.getRouters.mockImplementationOnce(
      () => new Promise<unknown>(() => {}),
    )
    mocks.authHasPermiOr.mockReturnValue(false)
    mocks.authHasRoleOr.mockReturnValue(false)
    const store = usePermissionStore()
    let resolved = false
    void store.generateRoutes().then(() => {
      resolved = true
    })
    await new Promise(r => setTimeout(r, 30))
    expect(resolved).toBe(false)
    expect(store.addRoutes).toEqual([])
    expect(store.routes).toEqual([])
  })
})

describe('filterDynamicRoutes()', () => {
  beforeEach(() => {
    mocks.authHasPermiOr.mockReset()
    mocks.authHasRoleOr.mockReset()
  })

  it('17. returns routes where auth.hasPermiOr(perms) is true', () => {
    mocks.authHasPermiOr.mockReturnValue(true)
    mocks.authHasRoleOr.mockReturnValue(false)
    const routes = [{ path: '/p', permissions: ['p1'] }]
    expect(filterDynamicRoutes(routes)).toEqual(routes)
    expect(mocks.authHasPermiOr).toHaveBeenCalledWith(['p1'])
    expect(mocks.authHasRoleOr).not.toHaveBeenCalled()
  })

  it('18. returns routes where auth.hasRoleOr(roles) is true (no permissions field)', () => {
    mocks.authHasPermiOr.mockReturnValue(false)
    mocks.authHasRoleOr.mockReturnValue(true)
    const routes = [{ path: '/r', roles: ['admin'] }]
    expect(filterDynamicRoutes(routes)).toEqual(routes)
    expect(mocks.authHasRoleOr).toHaveBeenCalledWith(['admin'])
  })

  it('19. excludes routes when neither hasPermiOr nor hasRoleOr is true', () => {
    mocks.authHasPermiOr.mockReturnValue(false)
    mocks.authHasRoleOr.mockReturnValue(false)
    const routes = [
      { path: '/p', permissions: ['p1'] },
      { path: '/r', roles: ['admin'] },
    ]
    expect(filterDynamicRoutes(routes)).toEqual([])
  })

  it('20. routes with neither permissions nor roles are excluded', () => {
    mocks.authHasPermiOr.mockReturnValue(false)
    mocks.authHasRoleOr.mockReturnValue(false)
    const routes = [{ path: '/x' }, { path: '/y' }]
    expect(filterDynamicRoutes(routes)).toEqual([])
    // Neither branch fires → neither mock invoked.
    expect(mocks.authHasPermiOr).not.toHaveBeenCalled()
    expect(mocks.authHasRoleOr).not.toHaveBeenCalled()
  })

  it('21. when both permissions and roles exist, hasPermiOr wins (else-if short-circuit)', () => {
    // Source uses `if (route.permissions) { ... } else if (route.roles) { ... }`.
    // If permissions is set, hasRoleOr is NEVER consulted.
    mocks.authHasPermiOr.mockReturnValue(false)
    mocks.authHasRoleOr.mockReturnValue(true)
    const routes = [{ path: '/x', permissions: ['p1'], roles: ['admin'] }]
    expect(filterDynamicRoutes(routes)).toEqual([])
    expect(mocks.authHasPermiOr).toHaveBeenCalledWith(['p1'])
    expect(mocks.authHasRoleOr).not.toHaveBeenCalled()
  })
})

describe('loadView()', () => {
  it('22. loadView("login") returns a function (src/views/login.vue matches)', () => {
    expect(typeof loadView('login')).toBe('function')
  })

  it('23. loadView("system/user/index") returns a function (nested view matches)', () => {
    expect(typeof loadView('system/user/index')).toBe('function')
  })

  it('24. loadView("nonexistent-view-xyz") returns undefined (no match)', () => {
    expect(loadView('nonexistent-view-xyz')).toBeUndefined()
  })
})