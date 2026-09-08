// W25 — src/api/system/dict/type.spec.ts. RuoYi dict-type (字典类型) CRUD.
// 7 endpoints: listType / getType / addType / updateType / delType /
// refreshCache / optionselect.
//
// Sister module to dict/data.ts (W21) — same URL pattern but for dict
// TYPES rather than dict DATA. The 'type' here means the dictionary
// metadata (dictName, dictType, status) vs 'data' being the actual
// key-value pairs (dictLabel, dictValue).
//
// Pinned behaviors:
// - refreshCache() uses HTTP DELETE — same pattern as config.refreshCache
//   (unconventional for cache-refresh; pin method choice).
// - delType accepts `number | number[]` (Array.toString → comma-join).
// - optionselect() takes no args — bare URL '/system/dict/type/optionselect'.
// - addType + updateType share URL '/system/dict/type' (POST vs PUT).
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({ default: vi.fn() }))

import request from '@/utils/request'
import {
  listType,
  getType,
  addType,
  updateType,
  delType,
  refreshCache,
  optionselect,
} from '@/api/system/dict/type'

const requestMock = vi.mocked(request)

beforeEach(() => {
  requestMock.mockReset()
  requestMock.mockResolvedValue({ code: 200, data: {} } as any)
})

describe('api/system/dict/type — listType()', () => {
  it('1. listType({dictName,dictType,status}) -> GET /system/dict/type/list with params', () => {
    listType({ dictName: '用户性别', dictType: 'sys_user_sex', status: '0' } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/dict/type/list',
      method: 'get',
      params: { dictName: '用户性别', dictType: 'sys_user_sex', status: '0' },
    })
  })

  it('2. listType() uses `params` not `data` (GET envelope via query string)', () => {
    listType({ foo: 'bar' } as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.params).toEqual({ foo: 'bar' })
    expect('data' in arg).toBe(false)
  })

  it('3. listType() return value is the request() promise', () => {
    const sentinel = Symbol('listType-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(listType({} as any)).toBe(sentinel)
  })
})

describe('api/system/dict/type — getType()', () => {
  it('4. getType(1) -> GET /system/dict/type/1 (URL concat)', () => {
    getType(1)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/dict/type/1',
      method: 'get',
    })
  })

  it('5. getType() call config has exactly 2 keys: url + method', () => {
    getType(1)
    const arg = requestMock.mock.calls[0][0] as any
    expect(Object.keys(arg).sort()).toEqual(['method', 'url'])
  })

  it('6. getType() return value is the request() promise', () => {
    const sentinel = Symbol('getType-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(getType(1)).toBe(sentinel)
  })
})

describe('api/system/dict/type — addType()', () => {
  it('7. addType({dictName,dictType,status,...}) -> POST /system/dict/type', () => {
    addType({
      dictId: 1,
      dictName: '用户性别',
      dictType: 'sys_user_sex',
      status: '0',
      remark: '性别字典',
    } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/dict/type',
      method: 'post',
      data: {
        dictId: 1,
        dictName: '用户性别',
        dictType: 'sys_user_sex',
        status: '0',
        remark: '性别字典',
      },
    })
  })

  it('8. addType() uses `data` (not `params`) for the envelope', () => {
    addType({ dictName: 'A' } as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.data).toBeDefined()
    expect('params' in arg).toBe(false)
  })

  it('9. addType() return value is the request() promise', () => {
    const sentinel = Symbol('addType-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(addType({} as any)).toBe(sentinel)
  })
})

describe('api/system/dict/type — updateType()', () => {
  it('10. updateType({dictId,...}) -> PUT /system/dict/type (same URL as addType)', () => {
    updateType({ dictId: 1, dictName: '性别（更新）' } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/dict/type',
      method: 'put',
      data: { dictId: 1, dictName: '性别（更新）' },
    })
  })

  it('11. updateType() and addType() share URL — only method differs', () => {
    updateType({ dictId: 1 } as any)
    addType({} as any)
    const updateArg = requestMock.mock.calls[0][0] as any
    const addArg = requestMock.mock.calls[1][0] as any
    expect(updateArg.url).toBe(addArg.url)
    expect(updateArg.url).toBe('/system/dict/type')
    expect(updateArg.method).toBe('put')
    expect(addArg.method).toBe('post')
  })

  it('12. updateType() return value is the request() promise', () => {
    const sentinel = Symbol('updateType-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(updateType({} as any)).toBe(sentinel)
  })
})

describe('api/system/dict/type — delType()', () => {
  it('13. delType(5) -> DELETE /system/dict/type/5 (single id)', () => {
    delType(5)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/dict/type/5',
      method: 'delete',
    })
  })

  it('14. delType([1,2]) -> DELETE /system/dict/type/1,2 (array comma-join)', () => {
    delType([1, 2])
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/dict/type/1,2',
      method: 'delete',
    })
  })

  it('15. delType() return value is the request() promise', () => {
    const sentinel = Symbol('delType-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(delType(1)).toBe(sentinel)
  })
})

describe('api/system/dict/type — refreshCache()', () => {
  it('16. refreshCache() -> DELETE /system/dict/type/refreshCache (unconventional method)', () => {
    // Pin: refreshCache uses HTTP DELETE — same unconventional pattern
    // as config.refreshCache. DELETE here because clearing the cache is
    // semantically a state-removal operation. Flag if refactored to POST.
    refreshCache()
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/dict/type/refreshCache',
      method: 'delete',
    })
  })

  it('17. refreshCache() takes no arguments', () => {
    refreshCache()
    const arg = requestMock.mock.calls[0][0] as any
    expect(Object.keys(arg).sort()).toEqual(['method', 'url'])
  })

  it('18. refreshCache() return value is the request() promise', () => {
    const sentinel = Symbol('refreshCache-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(refreshCache()).toBe(sentinel)
  })
})

describe('api/system/dict/type — optionselect()', () => {
  it('19. optionselect() -> GET /system/dict/type/optionselect (no body, no params)', () => {
    optionselect()
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/dict/type/optionselect',
      method: 'get',
    })
  })

  it('20. optionselect() call config has exactly 2 keys: url + method', () => {
    optionselect()
    const arg = requestMock.mock.calls[0][0] as any
    expect(Object.keys(arg).sort()).toEqual(['method', 'url'])
  })

  it('21. optionselect() takes no arguments — bare function', () => {
    // Pin: unlike listType (which takes query), optionselect has NO
    // arguments. Returns ALL enabled dict types for use in select
    // dropdowns. Document the no-arg signature.
    optionselect()
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.params).toBeUndefined()
  })

  it('22. optionselect() return value is the request() promise', () => {
    const sentinel = Symbol('optionselect-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(optionselect()).toBe(sentinel)
  })
})

describe('api/system/dict/type — module behavior', () => {
  it('23. all 7 exports are functions', () => {
    expect(typeof listType).toBe('function')
    expect(typeof getType).toBe('function')
    expect(typeof addType).toBe('function')
    expect(typeof updateType).toBe('function')
    expect(typeof delType).toBe('function')
    expect(typeof refreshCache).toBe('function')
    expect(typeof optionselect).toBe('function')
  })

  it('24. each export calls request exactly once per invocation', () => {
    listType({} as any)
    getType(1)
    addType({} as any)
    updateType({} as any)
    delType(1)
    refreshCache()
    optionselect()
    expect(requestMock).toHaveBeenCalledTimes(7)
  })

  it('25. all 7 endpoints have distinct URLs (no cross-routing)', () => {
    listType({} as any)
    getType(1)
    addType({})
    updateType({})
    delType(1)
    refreshCache()
    optionselect()
    const urls = requestMock.mock.calls.map((c) => (c[0] as any).url)
    expect(new Set(urls).size).toBe(5) // 2 URL-sharing pairs (addType/updateType + getType/delType)
    expect(urls.sort()).toEqual([
      '/system/dict/type',
      '/system/dict/type',
      '/system/dict/type/1',
      '/system/dict/type/1',
      '/system/dict/type/list',
      '/system/dict/type/optionselect',
      '/system/dict/type/refreshCache',
    ])
  })
})