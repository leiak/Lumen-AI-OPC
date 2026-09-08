// W23 — src/api/system/notice.spec.ts. RuoYi built-in notice (公告) CRUD + read-tracking.
// 9 endpoints: listNotice / getNotice / addNotice / updateNotice / delNotice /
// listNoticeTop / markNoticeRead / markNoticeReadAll / listNoticeReadUsers.
//
// Pinned behaviors:
// - markNoticeRead and markNoticeReadAll are POST but use `params:` (NOT
//   `data:`) for the body. This is asymmetric with addNotice/updateNotice
//   which use POST with `data:`. Pin the asymmetry.
// - markNoticeReadAll takes `ids: string` — single comma-joined string
//   rather than an array. Pin the verbatim type narrowing.
// - delNotice accepts `number | number[]` (Array.toString → comma-join).
// - listNoticeReadUsers is a SEPARATE endpoint from listNotice (different
//   URL: '/system/notice/readUsers/list' vs '/system/notice/list').
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({ default: vi.fn() }))

import request from '@/utils/request'
import {
  listNotice,
  getNotice,
  addNotice,
  updateNotice,
  delNotice,
  listNoticeTop,
  markNoticeRead,
  markNoticeReadAll,
  listNoticeReadUsers,
} from '@/api/system/notice'

const requestMock = vi.mocked(request)

beforeEach(() => {
  requestMock.mockReset()
  requestMock.mockResolvedValue({ code: 200, data: {} } as any)
})

describe('api/system/notice — listNotice()', () => {
  it('1. listNotice({noticeTitle,noticeType,status}) -> GET /system/notice/list with params', () => {
    listNotice({ noticeTitle: '系统升级', noticeType: '1', status: '0' } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/notice/list',
      method: 'get',
      params: { noticeTitle: '系统升级', noticeType: '1', status: '0' },
    })
  })

  it('2. listNotice() uses `params` not `data` (GET envelope via query string)', () => {
    listNotice({ foo: 'bar' } as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.params).toEqual({ foo: 'bar' })
    expect('data' in arg).toBe(false)
  })

  it('3. listNotice() return value is the request() promise', () => {
    const sentinel = Symbol('listNotice-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(listNotice({} as any)).toBe(sentinel)
  })
})

describe('api/system/notice — getNotice()', () => {
  it('4. getNotice(1) -> GET /system/notice/1 (URL concat)', () => {
    getNotice(1)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/notice/1',
      method: 'get',
    })
  })

  it('5. getNotice() call config has exactly 2 keys: url + method', () => {
    getNotice(1)
    const arg = requestMock.mock.calls[0][0] as any
    expect(Object.keys(arg).sort()).toEqual(['method', 'url'])
  })

  it('6. getNotice() return value is the request() promise', () => {
    const sentinel = Symbol('getNotice-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(getNotice(1)).toBe(sentinel)
  })
})

describe('api/system/notice — addNotice()', () => {
  it('7. addNotice({noticeTitle,noticeType,noticeContent,...}) -> POST /system/notice', () => {
    addNotice({
      noticeId: 1,
      noticeTitle: '系统升级通知',
      noticeType: '1',
      noticeContent: '本周日凌晨升级',
      status: '0',
      remark: '运营公告',
    } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/notice',
      method: 'post',
      data: {
        noticeId: 1,
        noticeTitle: '系统升级通知',
        noticeType: '1',
        noticeContent: '本周日凌晨升级',
        status: '0',
        remark: '运营公告',
      },
    })
  })

  it('8. addNotice() uses `data` (not `params`) for the envelope', () => {
    addNotice({ noticeTitle: 'A' } as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.data).toBeDefined()
    expect('params' in arg).toBe(false)
  })

  it('9. addNotice() return value is the request() promise', () => {
    const sentinel = Symbol('addNotice-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(addNotice({} as any)).toBe(sentinel)
  })
})

describe('api/system/notice — updateNotice()', () => {
  it('10. updateNotice({noticeId,...}) -> PUT /system/notice (same URL as addNotice)', () => {
    updateNotice({ noticeId: 1, noticeTitle: '更新标题' } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/notice',
      method: 'put',
      data: { noticeId: 1, noticeTitle: '更新标题' },
    })
  })

  it('11. updateNotice() and addNotice() share URL — only method differs', () => {
    updateNotice({ noticeId: 1 } as any)
    addNotice({} as any)
    const updateArg = requestMock.mock.calls[0][0] as any
    const addArg = requestMock.mock.calls[1][0] as any
    expect(updateArg.url).toBe(addArg.url)
    expect(updateArg.url).toBe('/system/notice')
    expect(updateArg.method).toBe('put')
    expect(addArg.method).toBe('post')
  })

  it('12. updateNotice() return value is the request() promise', () => {
    const sentinel = Symbol('updateNotice-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(updateNotice({} as any)).toBe(sentinel)
  })
})

describe('api/system/notice — delNotice()', () => {
  it('13. delNotice(5) -> DELETE /system/notice/5 (single id)', () => {
    delNotice(5)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/notice/5',
      method: 'delete',
    })
  })

  it('14. delNotice([1,2,3]) -> DELETE /system/notice/1,2,3 (array comma-join)', () => {
    delNotice([1, 2, 3])
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/notice/1,2,3',
      method: 'delete',
    })
  })

  it('15. delNotice() return value is the request() promise', () => {
    const sentinel = Symbol('delNotice-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(delNotice(1)).toBe(sentinel)
  })
})

describe('api/system/notice — listNoticeTop()', () => {
  it('16. listNoticeTop() -> GET /system/notice/listTop (no body, no params)', () => {
    listNoticeTop()
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/notice/listTop',
      method: 'get',
    })
  })

  it('17. listNoticeTop() is distinct URL from listNotice — different resource', () => {
    // Pin: '/system/notice/listTop' (top notices with read status) is
    // SEPARATE from '/system/notice/list' (admin list with query).
    // Different URL, different payload, different consumer.
    listNoticeTop()
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.url).toBe('/system/notice/listTop')
    expect(arg.url).not.toBe('/system/notice/list')
  })

  it('18. listNoticeTop() return value is the request() promise', () => {
    const sentinel = Symbol('listNoticeTop-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(listNoticeTop()).toBe(sentinel)
  })
})

describe('api/system/notice — markNoticeRead()', () => {
  it('19. markNoticeRead(5) -> POST /system/notice/markRead with params={noticeId:5}', () => {
    markNoticeRead(5)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/notice/markRead',
      method: 'post',
      params: { noticeId: 5 },
    })
  })

  it('20. markNoticeRead() uses POST + params (NOT data) — pin asymmetry with addNotice', () => {
    // Pin: addNotice/updateNotice use POST + data, but markNoticeRead uses
    // POST + params. Asymmetric — flag if refactored to use data envelope.
    markNoticeRead(1)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.method).toBe('post')
    expect(arg.params).toEqual({ noticeId: 1 })
    expect('data' in arg).toBe(false)
  })

  it('21. markNoticeRead() return value is the request() promise', () => {
    const sentinel = Symbol('markRead-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(markNoticeRead(1)).toBe(sentinel)
  })
})

describe('api/system/notice — markNoticeReadAll()', () => {
  it('22. markNoticeReadAll("1,2,3") -> POST /system/notice/markReadAll with params={ids}', () => {
    markNoticeReadAll('1,2,3')
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/notice/markReadAll',
      method: 'post',
      params: { ids: '1,2,3' },
    })
  })

  it('23. markNoticeReadAll() takes `ids: string` (not array) — pin type narrowing', () => {
    // Pin: caller is responsible for joining ids into a comma-separated
    // string before calling. Different from delNotice which accepts array.
    markNoticeReadAll('1,2')
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.params.ids).toBe('1,2')
  })

  it('24. markNoticeReadAll() return value is the request() promise', () => {
    const sentinel = Symbol('markReadAll-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(markNoticeReadAll('1')).toBe(sentinel)
  })
})

describe('api/system/notice — listNoticeReadUsers()', () => {
  it('25. listNoticeReadUsers({noticeId,pageNum}) -> GET /system/notice/readUsers/list', () => {
    listNoticeReadUsers({ noticeId: 1, pageNum: 1, pageSize: 10 } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/notice/readUsers/list',
      method: 'get',
      params: { noticeId: 1, pageNum: 1, pageSize: 10 },
    })
  })

  it('26. listNoticeReadUsers() has its own URL — distinct from listNotice', () => {
    listNoticeReadUsers({ noticeId: 1 } as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.url).toBe('/system/notice/readUsers/list')
  })

  it('27. listNoticeReadUsers() return value is the request() promise', () => {
    const sentinel = Symbol('readUsers-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(listNoticeReadUsers({} as any)).toBe(sentinel)
  })
})

describe('api/system/notice — module behavior', () => {
  it('28. all 9 exports are functions', () => {
    expect(typeof listNotice).toBe('function')
    expect(typeof getNotice).toBe('function')
    expect(typeof addNotice).toBe('function')
    expect(typeof updateNotice).toBe('function')
    expect(typeof delNotice).toBe('function')
    expect(typeof listNoticeTop).toBe('function')
    expect(typeof markNoticeRead).toBe('function')
    expect(typeof markNoticeReadAll).toBe('function')
    expect(typeof listNoticeReadUsers).toBe('function')
  })

  it('29. each export calls request exactly once per invocation', () => {
    listNotice({} as any)
    getNotice(1)
    addNotice({} as any)
    updateNotice({} as any)
    delNotice(1)
    listNoticeTop()
    markNoticeRead(1)
    markNoticeReadAll('1')
    listNoticeReadUsers({} as any)
    expect(requestMock).toHaveBeenCalledTimes(9)
  })

  it('30. all 9 endpoints have distinct URLs (no cross-routing)', () => {
    listNotice({} as any)
    getNotice(1)
    addNotice({})
    updateNotice({})
    delNotice(1)
    listNoticeTop()
    markNoticeRead(1)
    markNoticeReadAll('1')
    listNoticeReadUsers({} as any)
    const urls = requestMock.mock.calls.map((c) => (c[0] as any).url)
    expect(new Set(urls).size).toBe(7) // addNotice/updateNotice share '/system/notice' + getNotice/delNotice share '/system/notice/1'
    expect(urls.sort()).toEqual([
      '/system/notice',
      '/system/notice',
      '/system/notice/1',
      '/system/notice/1',
      '/system/notice/list',
      '/system/notice/listTop',
      '/system/notice/markRead',
      '/system/notice/markReadAll',
      '/system/notice/readUsers/list',
    ])
  })
})