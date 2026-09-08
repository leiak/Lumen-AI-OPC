// W24 — src/api/tool/gen.spec.ts. RuoYi code-generator admin endpoints.
// 9 endpoints: listTable / listDbTable / getGenTable / updateGenTable /
// importTable / previewTable / delTable / genCode / synchDb.
//
// IMPORTANT: this module uses URL prefix '/code/gen/' — DIFFERENT from
// the /system/ namespace. The code generator (ruoyi-gen microservice) is
// a separate backend service.
//
// Pinned behaviors:
// - importTable uses POST + `params:` (NOT `data:`) — body via query
//   string. Asymmetric with updateGenTable which uses POST + data.
// - listTable / listDbTable have SEPARATE URLs ('/code/gen/list' vs
//   '/code/gen/db/list') — different resources (imported vs available).
// - getGenTable / previewTable / delTable / genCode / synchDb ALL embed
//   their parameter in the URL path (no query envelope for single-id GET).
// - genCode uses GET to trigger code generation — unusual pattern (read
//   verb for action). Pin the semantic mismatch.
// - delTable accepts `number | number[]` (Array.toString → comma-join).
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({ default: vi.fn() }))

import request from '@/utils/request'
import {
  listTable,
  listDbTable,
  getGenTable,
  updateGenTable,
  importTable,
  previewTable,
  delTable,
  genCode,
  synchDb,
} from '@/api/tool/gen'

const requestMock = vi.mocked(request)

beforeEach(() => {
  requestMock.mockReset()
  requestMock.mockResolvedValue({ code: 200, data: {} } as any)
})

describe('api/tool/gen — listTable()', () => {
  it('1. listTable({tableName,tableComment}) -> GET /code/gen/list with params', () => {
    listTable({ tableName: 'sys_user', tableComment: '用户' } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/code/gen/list',
      method: 'get',
      params: { tableName: 'sys_user', tableComment: '用户' },
    })
  })

  it('2. listTable() uses `params` not `data` (GET envelope via query string)', () => {
    listTable({ foo: 'bar' } as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.params).toEqual({ foo: 'bar' })
    expect('data' in arg).toBe(false)
  })

  it('3. listTable() URL prefix is /code/gen/ — pin distinct from /system/', () => {
    listTable({} as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.url.startsWith('/code/gen/')).toBe(true)
    expect(arg.url.startsWith('/system/')).toBe(false)
  })

  it('4. listTable() return value is the request() promise', () => {
    const sentinel = Symbol('listTable-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(listTable({} as any)).toBe(sentinel)
  })
})

describe('api/tool/gen — listDbTable()', () => {
  it('5. listDbTable({tableName}) -> GET /code/gen/db/list with params', () => {
    listDbTable({ tableName: 'opc_wallet' } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/code/gen/db/list',
      method: 'get',
      params: { tableName: 'opc_wallet' },
    })
  })

  it('6. listDbTable() URL is distinct from listTable (different resource)', () => {
    // Pin: '/code/gen/db/list' (DB tables available to import) is
    // SEPARATE from '/code/gen/list' (tables that have been imported).
    listDbTable({} as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.url).toBe('/code/gen/db/list')
    expect(arg.url).not.toBe('/code/gen/list')
  })

  it('7. listDbTable() return value is the request() promise', () => {
    const sentinel = Symbol('listDbTable-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(listDbTable({} as any)).toBe(sentinel)
  })
})

describe('api/tool/gen — getGenTable()', () => {
  it('8. getGenTable(1) -> GET /code/gen/1 (URL concat)', () => {
    getGenTable(1)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/code/gen/1',
      method: 'get',
    })
  })

  it('9. getGenTable() call config has exactly 2 keys: url + method', () => {
    getGenTable(1)
    const arg = requestMock.mock.calls[0][0] as any
    expect(Object.keys(arg).sort()).toEqual(['method', 'url'])
  })

  it('10. getGenTable() return value is the request() promise', () => {
    const sentinel = Symbol('getGenTable-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(getGenTable(1)).toBe(sentinel)
  })
})

describe('api/tool/gen — updateGenTable()', () => {
  it('11. updateGenTable({tableId,tableComment,...}) -> PUT /code/gen', () => {
    updateGenTable({
      tableId: 1,
      tableName: 'sys_user',
      tableComment: '用户表',
      className: 'SysUser',
      tplCategory: 'crud',
      packageName: 'com.ruoyi.system',
      moduleName: 'system',
      businessName: 'user',
      functionName: '用户管理',
      functionAuthor: 'ruoyi',
      genType: '0',
      genPath: '/',
    } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/code/gen',
      method: 'put',
      data: {
        tableId: 1,
        tableName: 'sys_user',
        tableComment: '用户表',
        className: 'SysUser',
        tplCategory: 'crud',
        packageName: 'com.ruoyi.system',
        moduleName: 'system',
        businessName: 'user',
        functionName: '用户管理',
        functionAuthor: 'ruoyi',
        genType: '0',
        genPath: '/',
      },
    })
  })

  it('12. updateGenTable() uses PUT method + data envelope', () => {
    updateGenTable({ tableId: 1 } as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.method).toBe('put')
    expect(arg.data).toEqual({ tableId: 1 })
    expect('params' in arg).toBe(false)
  })

  it('13. updateGenTable() return value is the request() promise', () => {
    const sentinel = Symbol('updateGenTable-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(updateGenTable({} as any)).toBe(sentinel)
  })
})

describe('api/tool/gen — importTable()', () => {
  it('14. importTable({tables: "sys_user,opc_wallet"}) -> POST /code/gen/importTable with params', () => {
    // Pin: source uses POST + params (NOT data). Asymmetric with
    // updateGenTable which uses POST + data.
    importTable({ tables: 'sys_user,opc_wallet' })
    expect(requestMock).toHaveBeenCalledWith({
      url: '/code/gen/importTable',
      method: 'post',
      params: { tables: 'sys_user,opc_wallet' },
    })
  })

  it('15. importTable() uses POST + params (NOT data) — pin asymmetry', () => {
    importTable({ tables: 't1' })
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.method).toBe('post')
    expect(arg.params).toEqual({ tables: 't1' })
    expect('data' in arg).toBe(false)
  })

  it('16. importTable() takes `data: any` — accepts any shape', () => {
    // Pin: importTable(data: any) — untyped. The 'tables' field is
    // comma-separated string convention from RuoYi backend.
    importTable({ foo: 'bar', x: 1 } as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.params).toEqual({ foo: 'bar', x: 1 })
  })

  it('17. importTable() return value is the request() promise', () => {
    const sentinel = Symbol('importTable-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(importTable({})).toBe(sentinel)
  })
})

describe('api/tool/gen — previewTable()', () => {
  it('18. previewTable(1) -> GET /code/gen/preview/1 (URL concat)', () => {
    previewTable(1)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/code/gen/preview/1',
      method: 'get',
    })
  })

  it('19. previewTable() call config has exactly 2 keys: url + method', () => {
    previewTable(1)
    const arg = requestMock.mock.calls[0][0] as any
    expect(Object.keys(arg).sort()).toEqual(['method', 'url'])
  })

  it('20. previewTable() return value is the request() promise', () => {
    const sentinel = Symbol('previewTable-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(previewTable(1)).toBe(sentinel)
  })
})

describe('api/tool/gen — delTable()', () => {
  it('21. delTable(5) -> DELETE /code/gen/5 (single id)', () => {
    delTable(5)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/code/gen/5',
      method: 'delete',
    })
  })

  it('22. delTable([1,2]) -> DELETE /code/gen/1,2 (array comma-join)', () => {
    delTable([1, 2])
    expect(requestMock).toHaveBeenCalledWith({
      url: '/code/gen/1,2',
      method: 'delete',
    })
  })

  it('23. delTable() return value is the request() promise', () => {
    const sentinel = Symbol('delTable-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(delTable(1)).toBe(sentinel)
  })
})

describe('api/tool/gen — genCode()', () => {
  it('24. genCode("sys_user") -> GET /code/gen/genCode/sys_user (URL concat)', () => {
    genCode('sys_user')
    expect(requestMock).toHaveBeenCalledWith({
      url: '/code/gen/genCode/sys_user',
      method: 'get',
    })
  })

  it('25. genCode() uses GET for a side-effect action — pin semantic mismatch', () => {
    // Pin: genCode uses HTTP GET to TRIGGER code generation. This is a
    // semantic mismatch (GET should be safe/idempotent). RuoYi uses GET
    // here likely because the response is a downloaded zip file.
    // Document this anti-pattern. Flag if refactored to POST.
    genCode('sys_user')
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.method).toBe('get')
  })

  it('26. genCode() embeds tableName in path via concat (no encodeURIComponent)', () => {
    genCode('sys_user_ext')
    expect(requestMock).toHaveBeenCalledWith(
      expect.objectContaining({ url: '/code/gen/genCode/sys_user_ext' }),
    )
  })

  it('27. genCode() call config has exactly 2 keys: url + method', () => {
    genCode('t')
    const arg = requestMock.mock.calls[0][0] as any
    expect(Object.keys(arg).sort()).toEqual(['method', 'url'])
  })

  it('28. genCode() return value is the request() promise', () => {
    const sentinel = Symbol('genCode-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(genCode('t')).toBe(sentinel)
  })
})

describe('api/tool/gen — synchDb()', () => {
  it('29. synchDb("sys_user") -> GET /code/gen/synchDb/sys_user (URL concat)', () => {
    synchDb('sys_user')
    expect(requestMock).toHaveBeenCalledWith({
      url: '/code/gen/synchDb/sys_user',
      method: 'get',
    })
  })

  it('30. synchDb() uses GET for a side-effect action — same anti-pattern as genCode', () => {
    // Pin: synchDb uses HTTP GET to TRIGGER database synchronization.
    // Same semantic mismatch as genCode. Document both.
    synchDb('sys_user')
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.method).toBe('get')
  })

  it('31. synchDb() URL is distinct from genCode (different sub-path)', () => {
    synchDb('sys_user')
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.url).toBe('/code/gen/synchDb/sys_user')
    expect(arg.url).not.toBe('/code/gen/genCode/sys_user')
  })

  it('32. synchDb() call config has exactly 2 keys: url + method', () => {
    synchDb('t')
    const arg = requestMock.mock.calls[0][0] as any
    expect(Object.keys(arg).sort()).toEqual(['method', 'url'])
  })

  it('33. synchDb() return value is the request() promise', () => {
    const sentinel = Symbol('synchDb-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(synchDb('t')).toBe(sentinel)
  })
})

describe('api/tool/gen — module behavior', () => {
  it('34. all 9 exports are functions', () => {
    expect(typeof listTable).toBe('function')
    expect(typeof listDbTable).toBe('function')
    expect(typeof getGenTable).toBe('function')
    expect(typeof updateGenTable).toBe('function')
    expect(typeof importTable).toBe('function')
    expect(typeof previewTable).toBe('function')
    expect(typeof delTable).toBe('function')
    expect(typeof genCode).toBe('function')
    expect(typeof synchDb).toBe('function')
  })

  it('35. each export calls request exactly once per invocation', () => {
    listTable({} as any)
    listDbTable({} as any)
    getGenTable(1)
    updateGenTable({} as any)
    importTable({})
    previewTable(1)
    delTable(1)
    genCode('t')
    synchDb('t')
    expect(requestMock).toHaveBeenCalledTimes(9)
  })

  it('36. all 9 endpoints have distinct URLs (no cross-routing)', () => {
    listTable({} as any)
    listDbTable({} as any)
    getGenTable(1)
    updateGenTable({} as any)
    importTable({})
    previewTable(1)
    delTable(1)
    genCode('sys_user')
    synchDb('sys_user')
    const urls = requestMock.mock.calls.map((c) => (c[0] as any).url)
    expect(new Set(urls).size).toBe(8) // getGenTable/delTable share '/code/gen/1'
    expect(urls.sort()).toEqual([
      '/code/gen',
      '/code/gen/1',
      '/code/gen/1',
      '/code/gen/db/list',
      '/code/gen/genCode/sys_user',
      '/code/gen/importTable',
      '/code/gen/list',
      '/code/gen/preview/1',
      '/code/gen/synchDb/sys_user',
    ])
  })
})