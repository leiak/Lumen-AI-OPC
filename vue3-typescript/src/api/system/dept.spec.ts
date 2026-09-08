// W23 — src/api/system/dept.spec.ts. RuoYi built-in dept (部门) CRUD.
// 7 endpoints: listDept / listDeptExcludeChild / getDept / addDept / updateDept /
// updateDeptSort / delDept.
//
// Pinned behaviors:
// - listDept(query?) accepts OPTIONAL query (note the `?`) — undefined
//   passes `params: undefined` which axios serializes to empty/no query.
// - listDeptExcludeChild embeds deptId in URL: '/system/dept/list/exclude/{id}'.
// - updateDeptSort uses PUT to a separate endpoint '/system/dept/updateSort'
//   (not the same as updateDept at '/system/dept' — distinct from typical
//   RuoYi pattern where add/update share URL).
// - delDept(deptId: number) accepts single number only (not array).
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({ default: vi.fn() }))

import request from '@/utils/request'
import {
  listDept,
  listDeptExcludeChild,
  getDept,
  addDept,
  updateDept,
  updateDeptSort,
  delDept,
} from '@/api/system/dept'

const requestMock = vi.mocked(request)

beforeEach(() => {
  requestMock.mockReset()
  requestMock.mockResolvedValue({ code: 200, data: [] } as any)
})

describe('api/system/dept — listDept()', () => {
  it('1. listDept({deptName,parentId}) -> GET /system/dept/list with params', () => {
    listDept({ deptName: '研发部', parentId: 100 } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/dept/list',
      method: 'get',
      params: { deptName: '研发部', parentId: 100 },
    })
  })

  it('2. listDept() (no arg) -> GET /system/dept/list with params=undefined', () => {
    // Pin: query is optional. The source passes `params: undefined`
    // which axios treats as "no query string".
    listDept()
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/dept/list',
      method: 'get',
      params: undefined,
    })
  })

  it('3. listDept() uses `params` not `data` (GET envelope via query string)', () => {
    listDept({ foo: 'bar' } as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.params).toEqual({ foo: 'bar' })
    expect('data' in arg).toBe(false)
  })

  it('4. listDept() return value is the request() promise', () => {
    const sentinel = Symbol('listDept-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(listDept()).toBe(sentinel)
  })
})

describe('api/system/dept — listDeptExcludeChild()', () => {
  it('5. listDeptExcludeChild(100) -> GET /system/dept/list/exclude/100', () => {
    listDeptExcludeChild(100)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/dept/list/exclude/100',
      method: 'get',
    })
  })

  it('6. listDeptExcludeChild() embeds deptId in path segment via concat', () => {
    listDeptExcludeChild(999)
    expect(requestMock).toHaveBeenCalledWith(
      expect.objectContaining({ url: '/system/dept/list/exclude/999' }),
    )
  })

  it('7. listDeptExcludeChild() return value is the request() promise', () => {
    const sentinel = Symbol('listDeptExcludeChild-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(listDeptExcludeChild(1)).toBe(sentinel)
  })
})

describe('api/system/dept — getDept()', () => {
  it('8. getDept(5) -> GET /system/dept/5 (URL concat)', () => {
    getDept(5)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/dept/5',
      method: 'get',
    })
  })

  it('9. getDept() call config has exactly 2 keys: url + method', () => {
    getDept(1)
    const arg = requestMock.mock.calls[0][0] as any
    expect(Object.keys(arg).sort()).toEqual(['method', 'url'])
  })

  it('10. getDept() return value is the request() promise', () => {
    const sentinel = Symbol('getDept-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(getDept(1)).toBe(sentinel)
  })
})

describe('api/system/dept — addDept()', () => {
  it('11. addDept({deptName,parentId,...}) -> POST /system/dept', () => {
    addDept({
      deptId: 1,
      parentId: 0,
      deptName: '研发部',
      orderNum: 1,
      leader: '张三',
      phone: '13800001111',
      email: 'rd@example.com',
      status: '0',
    } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/dept',
      method: 'post',
      data: {
        deptId: 1,
        parentId: 0,
        deptName: '研发部',
        orderNum: 1,
        leader: '张三',
        phone: '13800001111',
        email: 'rd@example.com',
        status: '0',
      },
    })
  })

  it('12. addDept() uses `data` (not `params`) for the envelope', () => {
    addDept({ deptName: 'A' } as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.data).toBeDefined()
    expect('params' in arg).toBe(false)
  })

  it('13. addDept() return value is the request() promise', () => {
    const sentinel = Symbol('addDept-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(addDept({} as any)).toBe(sentinel)
  })
})

describe('api/system/dept — updateDept()', () => {
  it('14. updateDept({deptId,...}) -> PUT /system/dept (same URL as addDept)', () => {
    updateDept({ deptId: 1, deptName: '产品部' } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/dept',
      method: 'put',
      data: { deptId: 1, deptName: '产品部' },
    })
  })

  it('15. updateDept() return value is the request() promise', () => {
    const sentinel = Symbol('updateDept-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(updateDept({} as any)).toBe(sentinel)
  })
})

describe('api/system/dept — updateDeptSort()', () => {
  it('16. updateDeptSort({deptId,sortOrder}) -> PUT /system/dept/updateSort (distinct URL)', () => {
    // Pin: unlike addDept/updateDept which share '/system/dept', the sort
    // endpoint has its own URL '/system/dept/updateSort'. Flag if a
    // refactor moves it under the shared '/system/dept' path.
    updateDeptSort({ deptId: 1, sortOrder: 5 } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/dept/updateSort',
      method: 'put',
      data: { deptId: 1, sortOrder: 5 },
    })
  })

  it('17. updateDeptSort() uses PUT method + data envelope', () => {
    updateDeptSort({ deptId: 1 } as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.method).toBe('put')
    expect(arg.data).toEqual({ deptId: 1 })
  })

  it('18. updateDeptSort() return value is the request() promise', () => {
    const sentinel = Symbol('updateDeptSort-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(updateDeptSort({} as any)).toBe(sentinel)
  })
})

describe('api/system/dept — delDept()', () => {
  it('19. delDept(5) -> DELETE /system/dept/5 (single id only — not array)', () => {
    delDept(5)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/dept/5',
      method: 'delete',
    })
  })

  it('20. delDept() signature accepts number (not number[]) — pin type narrowing', () => {
    // Pin: delDept(deptId: number) — different from delConfig/delPost
    // which accept `number | number[]`. Document the asymmetry.
    delDept(1)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.url).toBe('/system/dept/1')
  })

  it('21. delDept() return value is the request() promise', () => {
    const sentinel = Symbol('delDept-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(delDept(1)).toBe(sentinel)
  })
})

describe('api/system/dept — module behavior', () => {
  it('22. all 7 exports are functions', () => {
    expect(typeof listDept).toBe('function')
    expect(typeof listDeptExcludeChild).toBe('function')
    expect(typeof getDept).toBe('function')
    expect(typeof addDept).toBe('function')
    expect(typeof updateDept).toBe('function')
    expect(typeof updateDeptSort).toBe('function')
    expect(typeof delDept).toBe('function')
  })

  it('23. each export calls request exactly once per invocation', () => {
    listDept()
    listDeptExcludeChild(1)
    getDept(1)
    addDept({} as any)
    updateDept({} as any)
    updateDeptSort({} as any)
    delDept(1)
    expect(requestMock).toHaveBeenCalledTimes(7)
  })

  it('24. all 7 endpoints have distinct URLs (no cross-routing)', () => {
    listDept()
    listDeptExcludeChild(1)
    getDept(1)
    addDept({})
    updateDept({})
    updateDeptSort({})
    delDept(1)
    const urls = requestMock.mock.calls.map((c) => (c[0] as any).url)
    expect(new Set(urls).size).toBe(5) // addDept/updateDept share '/system/dept' + getDept/delDept share '/system/dept/1'
    expect(urls.sort()).toEqual([
      '/system/dept',
      '/system/dept',
      '/system/dept/1',
      '/system/dept/1',
      '/system/dept/list',
      '/system/dept/list/exclude/1',
      '/system/dept/updateSort',
    ])
  })
})