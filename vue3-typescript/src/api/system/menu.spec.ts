// W23 — src/api/system/menu.spec.ts. RuoYi built-in sys-menu (菜单) CRUD.
// 8 endpoints: listMenu / getMenu / treeselect / roleMenuTreeselect /
// addMenu / updateMenu / updateMenuSort / delMenu.
//
// NOTE: this file is `src/api/system/menu.ts` — distinct from
// `src/api/menu.ts` (W20) which exposes `getRouters` for the admin router.
// Both files share URL prefix '/system/menu' but cover different resources.
//
// Pinned behaviors:
// - listMenu(query?) accepts OPTIONAL query — undefined passes
//   `params: undefined`.
// - treeselect() takes no args — bare URL '/system/menu/treeselect'.
// - roleMenuTreeselect(roleId) embeds roleId in URL path segment.
// - updateMenuSort has its OWN URL '/system/menu/updateSort' (distinct
//   from addMenu/updateMenu which share '/system/menu') — same pattern
//   as updateDeptSort.
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({ default: vi.fn() }))

import request from '@/utils/request'
import {
  listMenu,
  getMenu,
  treeselect,
  roleMenuTreeselect,
  addMenu,
  updateMenu,
  updateMenuSort,
  delMenu,
} from '@/api/system/menu'

const requestMock = vi.mocked(request)

beforeEach(() => {
  requestMock.mockReset()
  requestMock.mockResolvedValue({ code: 200, data: [] } as any)
})

describe('api/system/menu — listMenu()', () => {
  it('1. listMenu({menuName,status}) -> GET /system/menu/list with params', () => {
    listMenu({ menuName: '用户管理', status: '0' } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/menu/list',
      method: 'get',
      params: { menuName: '用户管理', status: '0' },
    })
  })

  it('2. listMenu() (no arg) -> GET /system/menu/list with params=undefined', () => {
    listMenu()
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/menu/list',
      method: 'get',
      params: undefined,
    })
  })

  it('3. listMenu() uses `params` not `data` (GET envelope via query string)', () => {
    listMenu({ foo: 'bar' } as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.params).toEqual({ foo: 'bar' })
    expect('data' in arg).toBe(false)
  })

  it('4. listMenu() return value is the request() promise', () => {
    const sentinel = Symbol('listMenu-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(listMenu()).toBe(sentinel)
  })
})

describe('api/system/menu — getMenu()', () => {
  it('5. getMenu(1000) -> GET /system/menu/1000 (URL concat)', () => {
    getMenu(1000)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/menu/1000',
      method: 'get',
    })
  })

  it('6. getMenu() call config has exactly 2 keys: url + method', () => {
    getMenu(1)
    const arg = requestMock.mock.calls[0][0] as any
    expect(Object.keys(arg).sort()).toEqual(['method', 'url'])
  })

  it('7. getMenu() return value is the request() promise', () => {
    const sentinel = Symbol('getMenu-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(getMenu(1)).toBe(sentinel)
  })
})

describe('api/system/menu — treeselect()', () => {
  it('8. treeselect() -> GET /system/menu/treeselect (no args, no params)', () => {
    treeselect()
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/menu/treeselect',
      method: 'get',
    })
  })

  it('9. treeselect() call config has exactly 2 keys: url + method', () => {
    treeselect()
    const arg = requestMock.mock.calls[0][0] as any
    expect(Object.keys(arg).sort()).toEqual(['method', 'url'])
  })

  it('10. treeselect() return value is the request() promise', () => {
    const sentinel = Symbol('treeselect-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(treeselect()).toBe(sentinel)
  })
})

describe('api/system/menu — roleMenuTreeselect()', () => {
  it('11. roleMenuTreeselect(2) -> GET /system/menu/roleMenuTreeselect/2', () => {
    roleMenuTreeselect(2)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/menu/roleMenuTreeselect/2',
      method: 'get',
    })
  })

  it('12. roleMenuTreeselect() embeds roleId in path via concat (no encodeURIComponent)', () => {
    roleMenuTreeselect(999)
    expect(requestMock).toHaveBeenCalledWith(
      expect.objectContaining({ url: '/system/menu/roleMenuTreeselect/999' }),
    )
  })

  it('13. roleMenuTreeselect() return value is the request() promise', () => {
    const sentinel = Symbol('roleMenuTreeselect-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(roleMenuTreeselect(1)).toBe(sentinel)
  })
})

describe('api/system/menu — addMenu()', () => {
  it('14. addMenu({menuName,parentId,orderNum,...}) -> POST /system/menu', () => {
    addMenu({
      menuId: 1,
      menuName: '系统管理',
      parentId: 0,
      orderNum: 1,
      path: 'system',
      component: 'Layout',
      isFrame: '1',
      menuType: 'M',
      visible: '0',
      status: '0',
      perms: 'system:menu:list',
      icon: 'system',
    } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/menu',
      method: 'post',
      data: {
        menuId: 1,
        menuName: '系统管理',
        parentId: 0,
        orderNum: 1,
        path: 'system',
        component: 'Layout',
        isFrame: '1',
        menuType: 'M',
        visible: '0',
        status: '0',
        perms: 'system:menu:list',
        icon: 'system',
      },
    })
  })

  it('15. addMenu() uses `data` (not `params`) for the envelope', () => {
    addMenu({ menuName: 'A' } as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.data).toBeDefined()
    expect('params' in arg).toBe(false)
  })

  it('16. addMenu() return value is the request() promise', () => {
    const sentinel = Symbol('addMenu-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(addMenu({} as any)).toBe(sentinel)
  })
})

describe('api/system/menu — updateMenu()', () => {
  it('17. updateMenu({menuId,...}) -> PUT /system/menu (same URL as addMenu)', () => {
    updateMenu({ menuId: 1, menuName: '用户中心' } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/menu',
      method: 'put',
      data: { menuId: 1, menuName: '用户中心' },
    })
  })

  it('18. updateMenu() and addMenu() share URL — only method differs', () => {
    updateMenu({ menuId: 1 } as any)
    addMenu({} as any)
    const updateArg = requestMock.mock.calls[0][0] as any
    const addArg = requestMock.mock.calls[1][0] as any
    expect(updateArg.url).toBe(addArg.url)
    expect(updateArg.url).toBe('/system/menu')
    expect(updateArg.method).toBe('put')
    expect(addArg.method).toBe('post')
  })

  it('19. updateMenu() return value is the request() promise', () => {
    const sentinel = Symbol('updateMenu-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(updateMenu({} as any)).toBe(sentinel)
  })
})

describe('api/system/menu — updateMenuSort()', () => {
  it('20. updateMenuSort({menuId,orderNum}) -> PUT /system/menu/updateSort (distinct URL)', () => {
    // Pin: same pattern as updateDeptSort — sort endpoint has its own URL
    // distinct from the shared add/update URL. Flag if refactored.
    updateMenuSort({ menuId: 1, orderNum: 5 } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/menu/updateSort',
      method: 'put',
      data: { menuId: 1, orderNum: 5 },
    })
  })

  it('21. updateMenuSort() uses PUT method + data envelope', () => {
    updateMenuSort({ menuId: 1 } as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.method).toBe('put')
    expect(arg.data).toEqual({ menuId: 1 })
  })

  it('22. updateMenuSort() return value is the request() promise', () => {
    const sentinel = Symbol('updateMenuSort-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(updateMenuSort({} as any)).toBe(sentinel)
  })
})

describe('api/system/menu — delMenu()', () => {
  it('23. delMenu(1000) -> DELETE /system/menu/1000 (single id)', () => {
    delMenu(1000)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/menu/1000',
      method: 'delete',
    })
  })

  it('24. delMenu() call config has exactly 2 keys: url + method', () => {
    delMenu(1)
    const arg = requestMock.mock.calls[0][0] as any
    expect(Object.keys(arg).sort()).toEqual(['method', 'url'])
  })

  it('25. delMenu() return value is the request() promise', () => {
    const sentinel = Symbol('delMenu-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(delMenu(1)).toBe(sentinel)
  })
})

describe('api/system/menu — module behavior', () => {
  it('26. all 8 exports are functions', () => {
    expect(typeof listMenu).toBe('function')
    expect(typeof getMenu).toBe('function')
    expect(typeof treeselect).toBe('function')
    expect(typeof roleMenuTreeselect).toBe('function')
    expect(typeof addMenu).toBe('function')
    expect(typeof updateMenu).toBe('function')
    expect(typeof updateMenuSort).toBe('function')
    expect(typeof delMenu).toBe('function')
  })

  it('27. each export calls request exactly once per invocation', () => {
    listMenu()
    getMenu(1)
    treeselect()
    roleMenuTreeselect(1)
    addMenu({} as any)
    updateMenu({} as any)
    updateMenuSort({} as any)
    delMenu(1)
    expect(requestMock).toHaveBeenCalledTimes(8)
  })

  it('28. all 8 endpoints have distinct URLs (no cross-routing)', () => {
    listMenu()
    getMenu(1)
    treeselect()
    roleMenuTreeselect(1)
    addMenu({})
    updateMenu({})
    updateMenuSort({})
    delMenu(1)
    const urls = requestMock.mock.calls.map((c) => (c[0] as any).url)
    expect(new Set(urls).size).toBe(6) // addMenu/updateMenu share '/system/menu' + getMenu/delMenu share '/system/menu/1'
    expect(urls.sort()).toEqual([
      '/system/menu',
      '/system/menu',
      '/system/menu/1',
      '/system/menu/1',
      '/system/menu/list',
      '/system/menu/roleMenuTreeselect/1',
      '/system/menu/treeselect',
      '/system/menu/updateSort',
    ])
  })
})