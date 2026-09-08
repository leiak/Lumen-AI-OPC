// W20 — src/api/menu.spec.ts. RuoYi built-in admin endpoint.
// 1 export: getRouters — fetches the dynamic router table for the
// authenticated user. This is the endpoint that the W13 permission
// store's generateRoutes() consumes (after wrapping in deep clone +
// filterAsyncRouter). Pin the wrapper contract here, not the consumer.
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({ default: vi.fn() }))

import request from '@/utils/request'
import { getRouters } from '@/api/menu'

const requestMock = vi.mocked(request)

beforeEach(() => {
  requestMock.mockReset()
  requestMock.mockResolvedValue({ code: 200, data: [] } as any)
})

describe('api/menu — getRouters()', () => {
  it('1. getRouters() -> GET /system/menu/getRouters (no body, no headers)', () => {
    getRouters()
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/menu/getRouters',
      method: 'get',
    })
  })

  it('2. getRouters() call config has exactly 2 keys: url + method (no data/params/headers)', () => {
    getRouters()
    const arg = requestMock.mock.calls[0][0] as any
    expect(Object.keys(arg).sort()).toEqual(['method', 'url'])
  })

  it('3. getRouters() does NOT set isToken:false (authed endpoint — bearer token required)', () => {
    // login/getCodeImg set isToken:false because they are unauthenticated.
    // getRouters requires the caller's bearer token, so the request
    // interceptor should add Authorization. Confirm the API layer does
    // NOT override that by setting isToken:false.
    getRouters()
    const arg = requestMock.mock.calls[0][0] as any
    expect('headers' in arg).toBe(false)
  })

  it('4. getRouters() return value is the request() promise (1:1 passthrough)', () => {
    const sentinel = Symbol('routers-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(getRouters()).toBe(sentinel)
  })

  it('5. getRouters is exported as a const arrow function', () => {
    // Source: `export const getRouters = (): Promise<...> => { ... }`.
    // Pin the export shape so a future refactor that changes the binding
    // (e.g. function declaration, default export) is flagged.
    expect(typeof getRouters).toBe('function')
    // Arrow functions have no prototype property by default.
    expect(getRouters.prototype).toBeUndefined()
  })

  it('6. calling getRouters() multiple times invokes request() each time (no caching at API layer)', () => {
    getRouters()
    getRouters()
    getRouters()
    expect(requestMock).toHaveBeenCalledTimes(3)
  })
})
