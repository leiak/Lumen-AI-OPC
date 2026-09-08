// W24 — src/api/monitor/jobLog.spec.ts. RuoYi Quartz scheduler job-log endpoints.
// 3 endpoints: listJobLog / delJobLog / cleanJobLog.
//
// IMPORTANT: this module uses URL prefix '/schedule/job/log/' — same root
// as monitor/job (Quartz scheduler service), NOT /system/.
//
// Pinned behaviors:
// - delJobLog accepts `number | number[]` (Array.toString → comma-join).
// - cleanJobLog() uses HTTP DELETE (same pattern as cleanOperlog/cleanLogininfor).
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({ default: vi.fn() }))

import request from '@/utils/request'
import {
  listJobLog,
  delJobLog,
  cleanJobLog,
} from '@/api/monitor/jobLog'

const requestMock = vi.mocked(request)

beforeEach(() => {
  requestMock.mockReset()
  requestMock.mockResolvedValue({ code: 200, data: {} } as any)
})

describe('api/monitor/jobLog — listJobLog()', () => {
  it('1. listJobLog({jobName,status}) -> GET /schedule/job/log/list with params', () => {
    listJobLog({ jobName: 'workflowCron', status: '0' } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/schedule/job/log/list',
      method: 'get',
      params: { jobName: 'workflowCron', status: '0' },
    })
  })

  it('2. listJobLog() uses `params` not `data` (GET envelope via query string)', () => {
    listJobLog({ foo: 'bar' } as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.params).toEqual({ foo: 'bar' })
    expect('data' in arg).toBe(false)
  })

  it('3. listJobLog() URL prefix is /schedule/job/log/ — pin distinct from /system/', () => {
    listJobLog({} as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.url.startsWith('/schedule/job/log/')).toBe(true)
    expect(arg.url.startsWith('/system/')).toBe(false)
  })

  it('4. listJobLog() return value is the request() promise', () => {
    const sentinel = Symbol('listJobLog-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(listJobLog({} as any)).toBe(sentinel)
  })
})

describe('api/monitor/jobLog — delJobLog()', () => {
  it('5. delJobLog(100) -> DELETE /schedule/job/log/100 (single id)', () => {
    delJobLog(100)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/schedule/job/log/100',
      method: 'delete',
    })
  })

  it('6. delJobLog([1,2,3]) -> DELETE /schedule/job/log/1,2,3 (array comma-join)', () => {
    delJobLog([1, 2, 3])
    expect(requestMock).toHaveBeenCalledWith({
      url: '/schedule/job/log/1,2,3',
      method: 'delete',
    })
  })

  it('7. delJobLog() call config has exactly 2 keys: url + method', () => {
    delJobLog(1)
    const arg = requestMock.mock.calls[0][0] as any
    expect(Object.keys(arg).sort()).toEqual(['method', 'url'])
  })

  it('8. delJobLog() return value is the request() promise', () => {
    const sentinel = Symbol('delJobLog-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(delJobLog(1)).toBe(sentinel)
  })
})

describe('api/monitor/jobLog — cleanJobLog()', () => {
  it('9. cleanJobLog() -> DELETE /schedule/job/log/clean (no body, no params)', () => {
    cleanJobLog()
    expect(requestMock).toHaveBeenCalledWith({
      url: '/schedule/job/log/clean',
      method: 'delete',
    })
  })

  it('10. cleanJobLog() call config has exactly 2 keys: url + method', () => {
    cleanJobLog()
    const arg = requestMock.mock.calls[0][0] as any
    expect(Object.keys(arg).sort()).toEqual(['method', 'url'])
  })

  it('11. cleanJobLog() return value is the request() promise', () => {
    const sentinel = Symbol('cleanJobLog-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(cleanJobLog()).toBe(sentinel)
  })
})

describe('api/monitor/jobLog — module behavior', () => {
  it('12. all 3 exports are functions', () => {
    expect(typeof listJobLog).toBe('function')
    expect(typeof delJobLog).toBe('function')
    expect(typeof cleanJobLog).toBe('function')
  })

  it('13. each export calls request exactly once per invocation', () => {
    listJobLog({} as any)
    delJobLog(1)
    cleanJobLog()
    expect(requestMock).toHaveBeenCalledTimes(3)
  })

  it('14. all 3 endpoints have distinct URLs (no cross-routing)', () => {
    listJobLog({} as any)
    delJobLog(1)
    cleanJobLog()
    const urls = requestMock.mock.calls.map((c) => (c[0] as any).url)
    expect(new Set(urls).size).toBe(3)
    expect(urls.sort()).toEqual([
      '/schedule/job/log/1',
      '/schedule/job/log/clean',
      '/schedule/job/log/list',
    ])
  })

  it('15. jobLog endpoints span GET/DELETE only (read + clear, no update)', () => {
    listJobLog({} as any)
    delJobLog(1)
    cleanJobLog()
    const methods = requestMock.mock.calls.map((c) => (c[0] as any).method)
    expect(methods.sort()).toEqual(['delete', 'delete', 'get'])
  })
})