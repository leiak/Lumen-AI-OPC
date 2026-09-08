// W23 — src/api/system/operlog.spec.ts. RuoYi built-in oper log (操作日志).
// 3 endpoints: list / delOperlog / cleanOperlog.
//
// Pinned behaviors:
// - `list` is exported as bare name (not listOperlog) — same naming
//   asymmetry as logininfor.ts. Document the inconsistency.
// - delOperlog accepts `number | number[]` (Array.toString → comma-join).
// - cleanOperlog() uses HTTP DELETE.
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({ default: vi.fn() }))

import request from '@/utils/request'
import {
  list,
  delOperlog,
  cleanOperlog,
} from '@/api/system/operlog'

const requestMock = vi.mocked(request)

beforeEach(() => {
  requestMock.mockReset()
  requestMock.mockResolvedValue({ code: 200, data: {} } as any)
})

describe('api/system/operlog — list()', () => {
  it('1. list({title,businessType,status}) -> GET /system/operlog/list with params', () => {
    list({ title: '登录', businessType: '0', status: '0' } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/operlog/list',
      method: 'get',
      params: { title: '登录', businessType: '0', status: '0' },
    })
  })

  it('2. list() uses `params` not `data` (GET envelope via query string)', () => {
    list({ foo: 'bar' } as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.params).toEqual({ foo: 'bar' })
    expect('data' in arg).toBe(false)
  })

  it('3. list() uses bare export name (not listOperlog) — pin naming asymmetry', () => {
    // Source: `export function list(query: ...)` — bare name, same as
    // logininfor.ts. Different from listConfig/listDept/listMenu which
    // use module-prefixed names. Document the inconsistency.
    list({} as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.url).toBe('/system/operlog/list')
  })

  it('4. list() return value is the request() promise', () => {
    const sentinel = Symbol('list-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(list({} as any)).toBe(sentinel)
  })
})

describe('api/system/operlog — delOperlog()', () => {
  it('5. delOperlog(100) -> DELETE /system/operlog/100 (single id)', () => {
    delOperlog(100)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/operlog/100',
      method: 'delete',
    })
  })

  it('6. delOperlog([1,2,3]) -> DELETE /system/operlog/1,2,3 (array comma-join)', () => {
    delOperlog([1, 2, 3])
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/operlog/1,2,3',
      method: 'delete',
    })
  })

  it('7. delOperlog() call config has exactly 2 keys: url + method', () => {
    delOperlog(1)
    const arg = requestMock.mock.calls[0][0] as any
    expect(Object.keys(arg).sort()).toEqual(['method', 'url'])
  })

  it('8. delOperlog() return value is the request() promise', () => {
    const sentinel = Symbol('delOperlog-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(delOperlog(1)).toBe(sentinel)
  })
})

describe('api/system/operlog — cleanOperlog()', () => {
  it('9. cleanOperlog() -> DELETE /system/operlog/clean (no body, no params)', () => {
    cleanOperlog()
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/operlog/clean',
      method: 'delete',
    })
  })

  it('10. cleanOperlog() call config has exactly 2 keys: url + method', () => {
    cleanOperlog()
    const arg = requestMock.mock.calls[0][0] as any
    expect(Object.keys(arg).sort()).toEqual(['method', 'url'])
  })

  it('11. cleanOperlog() return value is the request() promise', () => {
    const sentinel = Symbol('clean-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(cleanOperlog()).toBe(sentinel)
  })
})

describe('api/system/operlog — module behavior', () => {
  it('12. all 3 exports are functions', () => {
    expect(typeof list).toBe('function')
    expect(typeof delOperlog).toBe('function')
    expect(typeof cleanOperlog).toBe('function')
  })

  it('13. each export calls request exactly once per invocation', () => {
    list({} as any)
    delOperlog(1)
    cleanOperlog()
    expect(requestMock).toHaveBeenCalledTimes(3)
  })

  it('14. all 3 endpoints have distinct URLs (no cross-routing)', () => {
    list({} as any)
    delOperlog(1)
    cleanOperlog()
    const urls = requestMock.mock.calls.map((c) => (c[0] as any).url)
    expect(new Set(urls).size).toBe(3)
    expect(urls.sort()).toEqual([
      '/system/operlog/1',
      '/system/operlog/clean',
      '/system/operlog/list',
    ])
  })

  it('15. operlog endpoints span GET/DELETE only (read + clear, no update)', () => {
    list({} as any)
    delOperlog(1)
    cleanOperlog()
    const methods = requestMock.mock.calls.map((c) => (c[0] as any).method)
    expect(methods.sort()).toEqual(['delete', 'delete', 'get'])
  })
})