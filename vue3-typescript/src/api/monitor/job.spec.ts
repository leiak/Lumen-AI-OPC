// W24 — src/api/monitor/job.spec.ts. RuoYi Quartz scheduler admin endpoints.
// 7 endpoints: listJob / getJob / addJob / updateJob / delJob /
// changeJobStatus / runJob.
//
// IMPORTANT: this module uses URL prefix '/schedule/job/' — DIFFERENT from
// the '/system/' prefix used by other admin modules. The Quartz scheduler
// (ruoyi-job microservice) is a separate backend service, not under the
// admin system. Document the URL root so future refactors don't normalize
// it to /system/job by mistake.
//
// Pinned behaviors:
// - changeJobStatus(jobId, status) builds `{jobId, status}` envelope from
//   positional args (same pattern as user.changeUserStatus/role.changeRoleStatus).
// - runJob(jobId, jobGroup) builds `{jobId, jobGroup}` envelope.
// - delJob accepts `number | number[]` (Array.toString → comma-join).
// - runJob uses PUT (not POST) — distinct from typical "execute" pattern.
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({ default: vi.fn() }))

import request from '@/utils/request'
import {
  listJob,
  getJob,
  addJob,
  updateJob,
  delJob,
  changeJobStatus,
  runJob,
} from '@/api/monitor/job'

const requestMock = vi.mocked(request)

beforeEach(() => {
  requestMock.mockReset()
  requestMock.mockResolvedValue({ code: 200, data: {} } as any)
})

describe('api/monitor/job — listJob()', () => {
  it('1. listJob({jobName,status}) -> GET /schedule/job/list with params', () => {
    listJob({ jobName: 'workflowCron', jobGroup: 'DEFAULT', status: '0' } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/schedule/job/list',
      method: 'get',
      params: { jobName: 'workflowCron', jobGroup: 'DEFAULT', status: '0' },
    })
  })

  it('2. listJob() uses `params` not `data` (GET envelope via query string)', () => {
    listJob({ foo: 'bar' } as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.params).toEqual({ foo: 'bar' })
    expect('data' in arg).toBe(false)
  })

  it('3. listJob() URL prefix is /schedule/job/ — pin distinct from /system/', () => {
    // Pin: this module's URLs are rooted under /schedule/job/ (the Quartz
    // scheduler service), NOT /system/job/ (admin system). Different backend.
    listJob({} as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.url.startsWith('/schedule/job/')).toBe(true)
    expect(arg.url.startsWith('/system/')).toBe(false)
  })

  it('4. listJob() return value is the request() promise', () => {
    const sentinel = Symbol('listJob-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(listJob({} as any)).toBe(sentinel)
  })
})

describe('api/monitor/job — getJob()', () => {
  it('5. getJob(1) -> GET /schedule/job/1 (URL concat)', () => {
    getJob(1)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/schedule/job/1',
      method: 'get',
    })
  })

  it('6. getJob() call config has exactly 2 keys: url + method', () => {
    getJob(1)
    const arg = requestMock.mock.calls[0][0] as any
    expect(Object.keys(arg).sort()).toEqual(['method', 'url'])
  })

  it('7. getJob() return value is the request() promise', () => {
    const sentinel = Symbol('getJob-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(getJob(1)).toBe(sentinel)
  })
})

describe('api/monitor/job — addJob()', () => {
  it('8. addJob({jobName,cronExpression,invokeTarget,...}) -> POST /schedule/job', () => {
    addJob({
      jobId: 1,
      jobName: 'workflowCron',
      jobGroup: 'DEFAULT',
      invokeTarget: 'workflowCronJob.run()',
      cronExpression: '0 0 0/1 * * ?',
      misfirePolicy: '1',
      concurrent: '1',
      status: '0',
    } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/schedule/job',
      method: 'post',
      data: {
        jobId: 1,
        jobName: 'workflowCron',
        jobGroup: 'DEFAULT',
        invokeTarget: 'workflowCronJob.run()',
        cronExpression: '0 0 0/1 * * ?',
        misfirePolicy: '1',
        concurrent: '1',
        status: '0',
      },
    })
  })

  it('9. addJob() uses `data` (not `params`) for the envelope', () => {
    addJob({ jobName: 'A' } as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.data).toBeDefined()
    expect('params' in arg).toBe(false)
  })

  it('10. addJob() return value is the request() promise', () => {
    const sentinel = Symbol('addJob-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(addJob({} as any)).toBe(sentinel)
  })
})

describe('api/monitor/job — updateJob()', () => {
  it('11. updateJob({jobId,...}) -> PUT /schedule/job (same URL as addJob)', () => {
    updateJob({ jobId: 1, cronExpression: '0 0 0/2 * * ?' } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/schedule/job',
      method: 'put',
      data: { jobId: 1, cronExpression: '0 0 0/2 * * ?' },
    })
  })

  it('12. updateJob() and addJob() share URL — only method differs', () => {
    updateJob({ jobId: 1 } as any)
    addJob({} as any)
    const updateArg = requestMock.mock.calls[0][0] as any
    const addArg = requestMock.mock.calls[1][0] as any
    expect(updateArg.url).toBe(addArg.url)
    expect(updateArg.url).toBe('/schedule/job')
    expect(updateArg.method).toBe('put')
    expect(addArg.method).toBe('post')
  })

  it('13. updateJob() return value is the request() promise', () => {
    const sentinel = Symbol('updateJob-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(updateJob({} as any)).toBe(sentinel)
  })
})

describe('api/monitor/job — delJob()', () => {
  it('14. delJob(5) -> DELETE /schedule/job/5 (single id)', () => {
    delJob(5)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/schedule/job/5',
      method: 'delete',
    })
  })

  it('15. delJob([1,2,3]) -> DELETE /schedule/job/1,2,3 (array comma-join)', () => {
    delJob([1, 2, 3])
    expect(requestMock).toHaveBeenCalledWith({
      url: '/schedule/job/1,2,3',
      method: 'delete',
    })
  })

  it('16. delJob() return value is the request() promise', () => {
    const sentinel = Symbol('delJob-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(delJob(1)).toBe(sentinel)
  })
})

describe('api/monitor/job — changeJobStatus()', () => {
  it('17. changeJobStatus(1,"1") -> PUT /schedule/job/changeStatus with {jobId,status}', () => {
    changeJobStatus(1, '1')
    expect(requestMock).toHaveBeenCalledWith({
      url: '/schedule/job/changeStatus',
      method: 'put',
      data: { jobId: 1, status: '1' },
    })
  })

  it('18. changeJobStatus() builds {jobId,status} envelope from positional args', () => {
    changeJobStatus(99, '0')
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.data.jobId).toBe(99)
    expect(arg.data.status).toBe('0')
  })

  it('19. changeJobStatus() return value is the request() promise', () => {
    const sentinel = Symbol('changeJobStatus-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(changeJobStatus(1, '0')).toBe(sentinel)
  })
})

describe('api/monitor/job — runJob()', () => {
  it('20. runJob(1,"DEFAULT") -> PUT /schedule/job/run with {jobId,jobGroup}', () => {
    runJob(1, 'DEFAULT')
    expect(requestMock).toHaveBeenCalledWith({
      url: '/schedule/job/run',
      method: 'put',
      data: { jobId: 1, jobGroup: 'DEFAULT' },
    })
  })

  it('21. runJob() uses PUT (not POST) — pin unusual method choice for "execute"', () => {
    // Pin: runJob uses HTTP PUT — distinct from typical "execute action"
    // endpoints that use POST. PUT here means "set this job to run
    // immediately" (idempotent in the sense that re-running sets the
    // same state). Document the asymmetry.
    runJob(1, 'G')
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.method).toBe('put')
    expect(arg.method).not.toBe('post')
  })

  it('22. runJob() builds {jobId,jobGroup} envelope from positional args', () => {
    runJob(99, 'SYS')
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.data.jobId).toBe(99)
    expect(arg.data.jobGroup).toBe('SYS')
  })

  it('23. runJob() return value is the request() promise', () => {
    const sentinel = Symbol('runJob-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(runJob(1, 'G')).toBe(sentinel)
  })
})

describe('api/monitor/job — module behavior', () => {
  it('24. all 7 exports are functions', () => {
    expect(typeof listJob).toBe('function')
    expect(typeof getJob).toBe('function')
    expect(typeof addJob).toBe('function')
    expect(typeof updateJob).toBe('function')
    expect(typeof delJob).toBe('function')
    expect(typeof changeJobStatus).toBe('function')
    expect(typeof runJob).toBe('function')
  })

  it('25. each export calls request exactly once per invocation', () => {
    listJob({} as any)
    getJob(1)
    addJob({} as any)
    updateJob({} as any)
    delJob(1)
    changeJobStatus(1, '0')
    runJob(1, 'G')
    expect(requestMock).toHaveBeenCalledTimes(7)
  })

  it('26. all 7 endpoints have distinct URLs (no cross-routing)', () => {
    listJob({} as any)
    getJob(1)
    addJob({})
    updateJob({})
    delJob(1)
    changeJobStatus(1, '0')
    runJob(1, 'G')
    const urls = requestMock.mock.calls.map((c) => (c[0] as any).url)
    expect(new Set(urls).size).toBe(5) // addJob/updateJob + getJob/delJob share
    expect(urls.sort()).toEqual([
      '/schedule/job',
      '/schedule/job',
      '/schedule/job/1',
      '/schedule/job/1',
      '/schedule/job/changeStatus',
      '/schedule/job/list',
      '/schedule/job/run',
    ])
  })

  it('27. methods span GET/POST/PUT/DELETE — full Quartz scheduler CRUD + run', () => {
    listJob({} as any)
    getJob(1)
    addJob({} as any)
    updateJob({} as any)
    delJob(1)
    changeJobStatus(1, '0')
    runJob(1, 'G')
    const methods = requestMock.mock.calls.map((c) => (c[0] as any).method)
    expect(methods.sort()).toEqual(['delete', 'get', 'get', 'post', 'put', 'put', 'put'])
  })
})