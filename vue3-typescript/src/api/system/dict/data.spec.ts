// W21 — src/api/system/dict/data.spec.ts. RuoYi built-in dict-data CRUD.
// 6 endpoints: listData / getData / getDicts / addData / updateData /
// delData. Thin request() wrappers; getDicts is consumed by the W16
// useDict() helper for cache-miss API calls.
//
// Pinned behaviors:
// - listData sends query as `params` (NOT `data`) — this is the only GET
//   that takes a body envelope (the DictDataQueryParams query string).
// - getData / getDicts / delData build URLs via string concatenation
//   (`'/system/dict/data/' + dictCode`), not path templates. Pin the
//   concat so a future migration to template strings is flagged.
// - getData / getDicts / delData do NOT encode the dictCode/dictType
//   (no encodeURIComponent) — special chars in the value would break the
//   URL. Pin the verbatim pass-through so consumers know to pre-encode.
// - delData accepts `number | number[]` but the source coerces to string
//   via JS concat: [1,2] -> '/system/dict/data/1,2' (comma-joined). Pin
//   the verbatim concat behavior.
// - addData / updateData both POST/PUT to the SAME URL '/system/dict/data'
//   but with different HTTP methods (POST vs PUT).
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({ default: vi.fn() }))

import request from '@/utils/request'
import {
  listData,
  getData,
  getDicts,
  addData,
  updateData,
  delData,
} from '@/api/system/dict/data'

const requestMock = vi.mocked(request)

beforeEach(() => {
  requestMock.mockReset()
  requestMock.mockResolvedValue({ code: 200, data: {} } as any)
})

describe('api/system/dict/data — listData()', () => {
  it('1. listData({dictType,dictLabel}) -> GET /system/dict/data/list with query as params', () => {
    listData({ dictType: 'sys_user_sex', dictLabel: '男', pageNum: 1, pageSize: 10 })
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/dict/data/list',
      method: 'get',
      params: { dictType: 'sys_user_sex', dictLabel: '男', pageNum: 1, pageSize: 10 },
    })
  })

  it('2. listData() uses `params` not `data` (GET body envelope via query string)', () => {
    // Pin: GET requests send the query envelope as `params`, not `data`,
    // so axios serializes them into the URL query string. A future refactor
    // that switches to `data` would break the contract.
    listData({ foo: 'bar' })
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.params).toEqual({ foo: 'bar' })
    expect('data' in arg).toBe(false)
  })

  it('3. listData({}) passes empty query envelope verbatim', () => {
    listData({})
    expect(requestMock).toHaveBeenCalledWith(
      expect.objectContaining({ params: {} }),
    )
  })

  it('4. listData() return value is the request() promise', () => {
    const sentinel = Symbol('listData-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(listData({})).toBe(sentinel)
  })
})

describe('api/system/dict/data — getData()', () => {
  it('5. getData(123) -> GET /system/dict/data/123 (URL concat)', () => {
    getData(123)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/dict/data/123',
      method: 'get',
    })
  })

  it('6. getData() call config has exactly 2 keys: url + method', () => {
    getData(1)
    const arg = requestMock.mock.calls[0][0] as any
    expect(Object.keys(arg).sort()).toEqual(['method', 'url'])
  })

  it('7. getData() coerces numeric dictCode via JS string concat (no encodeURIComponent)', () => {
    // Pin: 123 -> '/system/dict/data/123'. The source does literal concat
    // — no encoding. Special chars in dictCode (none in practice) would
    // break. Document the verbatim behavior so consumers pre-encode.
    getData(999999)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.url).toBe('/system/dict/data/999999')
  })

  it('8. getData() return value is the request() promise', () => {
    const sentinel = Symbol('getData-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(getData(1)).toBe(sentinel)
  })
})

describe('api/system/dict/data — getDicts()', () => {
  it('9. getDicts("sys_user_sex") -> GET /system/dict/data/type/sys_user_sex', () => {
    getDicts('sys_user_sex')
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/dict/data/type/sys_user_sex',
      method: 'get',
    })
  })

  it('10. getDicts() embeds dictType in URL path segment via concat', () => {
    getDicts('my_dict_type')
    expect(requestMock).toHaveBeenCalledWith(
      expect.objectContaining({ url: '/system/dict/data/type/my_dict_type' }),
    )
  })

  it('11. getDicts() does NOT URL-encode dictType (no encodeURIComponent)', () => {
    // Pin: dictType with special chars is appended verbatim.
    // 'a/b' -> '/system/dict/data/type/a/b' (slash breaks routing in
    // practice — consumers must use safe identifiers).
    getDicts('a b')
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.url).toBe('/system/dict/data/type/a b')
    // Confirm no %20 or any encoding happened.
    expect(arg.url).not.toContain('%20')
  })

  it('12. getDicts() call config has exactly 2 keys: url + method', () => {
    getDicts('x')
    const arg = requestMock.mock.calls[0][0] as any
    expect(Object.keys(arg).sort()).toEqual(['method', 'url'])
  })

  it('13. getDicts() return value is the request() promise', () => {
    const sentinel = Symbol('getDicts-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(getDicts('x')).toBe(sentinel)
  })
})

describe('api/system/dict/data — addData()', () => {
  it('14. addData({dictType,dictLabel,dictValue,...}) -> POST /system/dict/data with data envelope', () => {
    addData({
      dictType: 'sys_user_sex',
      dictLabel: '男',
      dictValue: '0',
      cssClass: '',
      listClass: 'primary',
      isDefault: 'Y',
      status: '0',
      remark: '性别男',
    })
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/dict/data',
      method: 'post',
      data: {
        dictType: 'sys_user_sex',
        dictLabel: '男',
        dictValue: '0',
        cssClass: '',
        listClass: 'primary',
        isDefault: 'Y',
        status: '0',
        remark: '性别男',
      },
    })
  })

  it('15. addData() uses `data` (not `params`) for the envelope', () => {
    addData({ dictType: 't', dictLabel: 'l', dictValue: 'v' })
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.data).toBeDefined()
    expect('params' in arg).toBe(false)
  })

  it('16. addData({}) passes empty object envelope verbatim', () => {
    addData({})
    expect(requestMock).toHaveBeenCalledWith(
      expect.objectContaining({ method: 'post', url: '/system/dict/data', data: {} }),
    )
  })

  it('17. addData() return value is the request() promise', () => {
    const sentinel = Symbol('addData-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(addData({})).toBe(sentinel)
  })
})

describe('api/system/dict/data — updateData()', () => {
  it('18. updateData({dictCode,dictLabel,...}) -> PUT /system/dict/data with data envelope', () => {
    updateData({
      dictCode: 100,
      dictType: 'sys_user_sex',
      dictLabel: '未知',
      dictValue: '2',
      cssClass: '',
      listClass: 'info',
      isDefault: 'N',
      status: '0',
      remark: '未知',
    })
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/dict/data',
      method: 'put',
      data: {
        dictCode: 100,
        dictType: 'sys_user_sex',
        dictLabel: '未知',
        dictValue: '2',
        cssClass: '',
        listClass: 'info',
        isDefault: 'N',
        status: '0',
        remark: '未知',
      },
    })
  })

  it('19. updateData() and addData() share the SAME URL — only method differs (PUT vs POST)', () => {
    // Pin the API contract: both endpoints are at /system/dict/data. Only
    // the HTTP method distinguishes create from update. A future refactor
    // that gives update its own URL would break this contract.
    updateData({ dictCode: 1 })
    const updateArg = requestMock.mock.calls[0][0] as any
    addData({})
    const addArg = requestMock.mock.calls[1][0] as any
    expect(updateArg.url).toBe(addArg.url)
    expect(updateArg.url).toBe('/system/dict/data')
    expect(updateArg.method).toBe('put')
    expect(addArg.method).toBe('post')
  })

  it('20. updateData() return value is the request() promise', () => {
    const sentinel = Symbol('updateData-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(updateData({})).toBe(sentinel)
  })
})

describe('api/system/dict/data — delData()', () => {
  it('21. delData(5) -> DELETE /system/dict/data/5 (single id URL concat)', () => {
    delData(5)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/dict/data/5',
      method: 'delete',
    })
  })

  it('22. delData([1,2,3]) -> DELETE /system/dict/data/1,2,3 (array coerced to comma-joined string)', () => {
    // Pin the actual JS behavior: `'/system/dict/data/' + [1,2,3]` evaluates
    // to '/system/dict/data/1,2,3' because Array.prototype.toString joins
    // with commas. This is NOT a typical batch-delete URL — most REST
    // APIs would use POST /batch-delete or query params. Pin the quirk.
    delData([1, 2, 3])
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/dict/data/1,2,3',
      method: 'delete',
    })
  })

  it('23. delData() call config has exactly 2 keys: url + method (no data/params/headers)', () => {
    delData(1)
    const arg = requestMock.mock.calls[0][0] as any
    expect(Object.keys(arg).sort()).toEqual(['method', 'url'])
  })

  it('24. delData() return value is the request() promise', () => {
    const sentinel = Symbol('delData-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(delData(1)).toBe(sentinel)
  })
})

describe('api/system/dict/data — module behavior', () => {
  it('25. all 6 exports are functions', () => {
    expect(typeof listData).toBe('function')
    expect(typeof getData).toBe('function')
    expect(typeof getDicts).toBe('function')
    expect(typeof addData).toBe('function')
    expect(typeof updateData).toBe('function')
    expect(typeof delData).toBe('function')
  })

  it('26. each export calls request exactly once per invocation', () => {
    listData({})
    getData(1)
    getDicts('t')
    addData({})
    updateData({})
    delData(1)
    expect(requestMock).toHaveBeenCalledTimes(6)
  })

  it('27. all 6 endpoints hit one of 4 distinct URL patterns (POST/PUT share URL)', () => {
    // Pin: addData (POST) and updateData (PUT) share '/system/dict/data';
    // getData (single id) and delData (single id) share '/system/dict/data/{id}'.
    // So 4 distinct URL patterns across 6 endpoints — confirmed by examining
    // the API contract (POST/PUT differ only by HTTP method).
    listData({})
    getData(1)
    getDicts('t')
    addData({})
    updateData({})
    delData(1)
    const urls = requestMock.mock.calls.map((c) => (c[0] as any).url)
    expect(new Set(urls).size).toBe(4)
    expect(urls.sort()).toEqual([
      '/system/dict/data',
      '/system/dict/data',
      '/system/dict/data/1',
      '/system/dict/data/1',
      '/system/dict/data/list',
      '/system/dict/data/type/t',
    ])
  })

  it('28. methods span GET/POST/PUT/DELETE — RuoYi dict-data covers full CRUD', () => {
    listData({})
    addData({})
    updateData({})
    getData(1)
    delData(1)
    const methods = requestMock.mock.calls.map((c) => (c[0] as any).method)
    expect(methods.sort()).toEqual(['delete', 'get', 'get', 'post', 'put'])
  })
})
