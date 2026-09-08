// W23 — src/api/system/config.spec.ts. RuoYi built-in config (参数配置) CRUD.
// 7 endpoints: listConfig / getConfig / getConfigKey / addConfig / updateConfig /
// delConfig / refreshCache.
//
// Pinned behaviors:
// - All 4 single-id endpoints (get/del) build URL via `'/system/config/' + id`
//   (string concat, NOT template literals).
// - refreshCache() uses HTTP DELETE — pin the unconventional method choice
//   so a future refactor to POST is flagged.
// - delConfig accepts `number | number[]` and concatenates the same way as
//   dict/data (Array.toString → comma-joined). See W21 delData quirk pin.
// - getConfigKey embeds configKey string in URL path segment without
//   encodeURIComponent (pin verbatim pass-through).
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({ default: vi.fn() }))

import request from '@/utils/request'
import {
  listConfig,
  getConfig,
  getConfigKey,
  addConfig,
  updateConfig,
  delConfig,
  refreshCache,
} from '@/api/system/config'

const requestMock = vi.mocked(request)

beforeEach(() => {
  requestMock.mockReset()
  requestMock.mockResolvedValue({ code: 200, data: {} } as any)
})

describe('api/system/config — listConfig()', () => {
  it('1. listConfig({configName,pageNum}) -> GET /system/config/list with params', () => {
    listConfig({ configName: 'sys.user.initPassword', pageNum: 1, pageSize: 10 } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/config/list',
      method: 'get',
      params: { configName: 'sys.user.initPassword', pageNum: 1, pageSize: 10 },
    })
  })

  it('2. listConfig() uses `params` (not `data`) — GET envelope via query string', () => {
    listConfig({ foo: 'bar' } as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.params).toEqual({ foo: 'bar' })
    expect('data' in arg).toBe(false)
  })

  it('3. listConfig() return value is the request() promise', () => {
    const sentinel = Symbol('listConfig-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(listConfig({} as any)).toBe(sentinel)
  })
})

describe('api/system/config — getConfig()', () => {
  it('4. getConfig(1) -> GET /system/config/1 (URL concat)', () => {
    getConfig(1)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/config/1',
      method: 'get',
    })
  })

  it('5. getConfig() call config has exactly 2 keys: url + method', () => {
    getConfig(1)
    const arg = requestMock.mock.calls[0][0] as any
    expect(Object.keys(arg).sort()).toEqual(['method', 'url'])
  })

  it('6. getConfig() return value is the request() promise', () => {
    const sentinel = Symbol('getConfig-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(getConfig(1)).toBe(sentinel)
  })
})

describe('api/system/config — getConfigKey()', () => {
  it('7. getConfigKey("sys.user.initPassword") -> GET /system/config/configKey/sys.user.initPassword', () => {
    getConfigKey('sys.user.initPassword')
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/config/configKey/sys.user.initPassword',
      method: 'get',
    })
  })

  it('8. getConfigKey() does NOT URL-encode configKey (no encodeURIComponent)', () => {
    // Pin: dots and special chars in configKey are appended verbatim.
    // 'a b' -> '/system/config/configKey/a b' (space preserved).
    getConfigKey('a b')
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.url).toBe('/system/config/configKey/a b')
    expect(arg.url).not.toContain('%20')
  })

  it('9. getConfigKey() return value is the request() promise', () => {
    const sentinel = Symbol('getConfigKey-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(getConfigKey('k')).toBe(sentinel)
  })
})

describe('api/system/config — addConfig()', () => {
  it('10. addConfig({configName,configKey,configValue,...}) -> POST /system/config', () => {
    addConfig({
      configId: 1,
      configName: '用户管理-账号初始密码',
      configKey: 'sys.user.initPassword',
      configValue: '123456',
      configType: 'Y',
      remark: '账号初始密码',
    } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/config',
      method: 'post',
      data: {
        configId: 1,
        configName: '用户管理-账号初始密码',
        configKey: 'sys.user.initPassword',
        configValue: '123456',
        configType: 'Y',
        remark: '账号初始密码',
      },
    })
  })

  it('11. addConfig() return value is the request() promise', () => {
    const sentinel = Symbol('addConfig-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(addConfig({} as any)).toBe(sentinel)
  })
})

describe('api/system/config — updateConfig()', () => {
  it('12. updateConfig({configId,...}) -> PUT /system/config (same URL as addConfig, only method differs)', () => {
    updateConfig({ configId: 1, configValue: '654321' } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/config',
      method: 'put',
      data: { configId: 1, configValue: '654321' },
    })
  })

  it('13. updateConfig() and addConfig() share URL — only method differs', () => {
    updateConfig({ configId: 1 } as any)
    addConfig({} as any)
    const updateArg = requestMock.mock.calls[0][0] as any
    const addArg = requestMock.mock.calls[1][0] as any
    expect(updateArg.url).toBe(addArg.url)
    expect(updateArg.url).toBe('/system/config')
    expect(updateArg.method).toBe('put')
    expect(addArg.method).toBe('post')
  })

  it('14. updateConfig() return value is the request() promise', () => {
    const sentinel = Symbol('updateConfig-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(updateConfig({} as any)).toBe(sentinel)
  })
})

describe('api/system/config — delConfig()', () => {
  it('15. delConfig(5) -> DELETE /system/config/5 (single id)', () => {
    delConfig(5)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/config/5',
      method: 'delete',
    })
  })

  it('16. delConfig([1,2]) -> DELETE /system/config/1,2 (array comma-join)', () => {
    // Pin the JS quirk: '/system/config/' + [1,2] == '/system/config/1,2'.
    delConfig([1, 2])
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/config/1,2',
      method: 'delete',
    })
  })

  it('17. delConfig() return value is the request() promise', () => {
    const sentinel = Symbol('delConfig-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(delConfig(1)).toBe(sentinel)
  })
})

describe('api/system/config — refreshCache()', () => {
  it('18. refreshCache() -> DELETE /system/config/refreshCache (unconventional method)', () => {
    // Pin: refreshCache uses HTTP DELETE (not POST). Most APIs use POST for
    // non-idempotent cache-refresh actions; this one uses DELETE because
    // it's considered to be removing the cached state. Flag if refactored.
    refreshCache()
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/config/refreshCache',
      method: 'delete',
    })
  })

  it('19. refreshCache() takes no arguments', () => {
    refreshCache()
    const arg = requestMock.mock.calls[0][0] as any
    expect(Object.keys(arg).sort()).toEqual(['method', 'url'])
  })

  it('20. refreshCache() return value is the request() promise', () => {
    const sentinel = Symbol('refreshCache-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(refreshCache()).toBe(sentinel)
  })
})

describe('api/system/config — module behavior', () => {
  it('21. all 7 exports are functions', () => {
    expect(typeof listConfig).toBe('function')
    expect(typeof getConfig).toBe('function')
    expect(typeof getConfigKey).toBe('function')
    expect(typeof addConfig).toBe('function')
    expect(typeof updateConfig).toBe('function')
    expect(typeof delConfig).toBe('function')
    expect(typeof refreshCache).toBe('function')
  })

  it('22. each export calls request exactly once per invocation', () => {
    listConfig({} as any)
    getConfig(1)
    getConfigKey('k')
    addConfig({} as any)
    updateConfig({} as any)
    delConfig(1)
    refreshCache()
    expect(requestMock).toHaveBeenCalledTimes(7)
  })

  it('23. all 7 endpoints have distinct URLs (no cross-routing)', () => {
    listConfig({} as any)
    getConfig(1)
    getConfigKey('k')
    addConfig({})
    updateConfig({})
    delConfig(1)
    refreshCache()
    const urls = requestMock.mock.calls.map((c) => (c[0] as any).url)
    expect(new Set(urls).size).toBeGreaterThanOrEqual(5) // POST/PUT share, GET/DEL-by-id share
    expect(urls.sort()).toEqual([
      '/system/config',
      '/system/config',
      '/system/config/1',
      '/system/config/1',
      '/system/config/configKey/k',
      '/system/config/list',
      '/system/config/refreshCache',
    ])
  })

  it('24. methods span GET/POST/PUT/DELETE — full CRUD + cache refresh', () => {
    listConfig({} as any)
    getConfig(1)
    getConfigKey('k')
    addConfig({} as any)
    updateConfig({} as any)
    delConfig(1)
    refreshCache()
    const methods = requestMock.mock.calls.map((c) => (c[0] as any).method)
    expect(methods.sort()).toEqual(['delete', 'delete', 'get', 'get', 'get', 'post', 'put'])
  })
})