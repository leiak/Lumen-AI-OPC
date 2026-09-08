// W24 — src/api/monitor/online.spec.ts. RuoYi online-user admin endpoints.
// 2 endpoints: list / forceLogout.
//
// Pinned behaviors:
// - `list` is exported as bare name (not listOnline) — same naming
//   asymmetry as logininfor.ts / operlog.ts.
// - forceLogout(tokenId) embeds tokenId in URL path without encoding.
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({ default: vi.fn() }))

import request from '@/utils/request'
import {
  list,
  forceLogout,
} from '@/api/monitor/online'

const requestMock = vi.mocked(request)

beforeEach(() => {
  requestMock.mockReset()
  requestMock.mockResolvedValue({ code: 200, data: {} } as any)
})

describe('api/monitor/online — list()', () => {
  it('1. list({ipaddr,userName}) -> GET /system/online/list with params', () => {
    list({ ipaddr: '127.0.0.1', userName: 'admin' } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/online/list',
      method: 'get',
      params: { ipaddr: '127.0.0.1', userName: 'admin' },
    })
  })

  it('2. list() uses `params` not `data` (GET envelope via query string)', () => {
    list({ foo: 'bar' } as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.params).toEqual({ foo: 'bar' })
    expect('data' in arg).toBe(false)
  })

  it('3. list() uses bare export name (not listOnline) — pin naming asymmetry', () => {
    // Source: `export function list(query: ...)` — bare name, same as
    // logininfor.ts / operlog.ts. Different from listConfig/listDept/listMenu.
    list({} as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.url).toBe('/system/online/list')
  })

  it('4. list() URL prefix is /system/online/ (NOT /schedule/)', () => {
    // Pin: unlike monitor/job + monitor/jobLog (under /schedule/),
    // monitor/online is rooted under /system/online/ (admin system).
    // Different backend service from the Quartz scheduler.
    list({} as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.url.startsWith('/system/online/')).toBe(true)
    expect(arg.url.startsWith('/schedule/')).toBe(false)
  })

  it('5. list() return value is the request() promise', () => {
    const sentinel = Symbol('list-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(list({} as any)).toBe(sentinel)
  })
})

describe('api/monitor/online — forceLogout()', () => {
  it('6. forceLogout("token-abc") -> DELETE /system/online/token-abc', () => {
    forceLogout('token-abc')
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/online/token-abc',
      method: 'delete',
    })
  })

  it('7. forceLogout() embeds tokenId in URL path via concat (no encodeURIComponent)', () => {
    // Pin: tokenIds are opaque server-generated strings; no encoding applied.
    forceLogout('xyz-123')
    expect(requestMock).toHaveBeenCalledWith(
      expect.objectContaining({ url: '/system/online/xyz-123' }),
    )
  })

  it('8. forceLogout() uses DELETE — destructive action, fits semantics', () => {
    forceLogout('t')
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.method).toBe('delete')
  })

  it('9. forceLogout() call config has exactly 2 keys: url + method', () => {
    forceLogout('t')
    const arg = requestMock.mock.calls[0][0] as any
    expect(Object.keys(arg).sort()).toEqual(['method', 'url'])
  })

  it('10. forceLogout() return value is the request() promise', () => {
    const sentinel = Symbol('forceLogout-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(forceLogout('t')).toBe(sentinel)
  })
})

describe('api/monitor/online — module behavior', () => {
  it('11. all 2 exports are functions', () => {
    expect(typeof list).toBe('function')
    expect(typeof forceLogout).toBe('function')
  })

  it('12. each export calls request exactly once per invocation', () => {
    list({} as any)
    forceLogout('t')
    expect(requestMock).toHaveBeenCalledTimes(2)
  })

  it('13. both endpoints have distinct URLs (no cross-routing)', () => {
    list({} as any)
    forceLogout('token-abc')
    const urls = requestMock.mock.calls.map((c) => (c[0] as any).url)
    expect(new Set(urls).size).toBe(2)
    expect(urls.sort()).toEqual([
      '/system/online/list',
      '/system/online/token-abc',
    ])
  })

  it('14. online endpoints span GET/DELETE only (list + force-logout)', () => {
    list({} as any)
    forceLogout('t')
    const methods = requestMock.mock.calls.map((c) => (c[0] as any).method)
    expect(methods.sort()).toEqual(['delete', 'get'])
  })
})