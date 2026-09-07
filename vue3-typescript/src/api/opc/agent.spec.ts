// W12.4 — api/opc/agent.spec.ts. Asserts URL template + method + params/data
// for every exported function. request() is fully mocked.
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({ default: vi.fn() }))

import request from '@/utils/request'
import {
  listMarket,
  getAgentDetail,
  hireAgent,
  listMyInstances,
  getInstance,
  instanceAction,
  listInstanceTasks,
  listInstanceUsage,
  dailyUsage,
  usageSummary,
  runTask,
} from '@/api/opc/agent'

const requestMock = vi.mocked(request)

beforeEach(() => {
  requestMock.mockReset()
  requestMock.mockResolvedValue({ code: 200, data: 'mock' } as any)
})

describe('api/opc/agent', () => {
  it('1. listMarket("finance") -> GET /opc/agent/market params={category}', () => {
    listMarket('finance')
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/agent/market',
      method: 'get',
      params: { category: 'finance' },
    })
  })

  it('2. listMarket() (no arg) -> params={category:undefined}', () => {
    listMarket()
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/agent/market',
      method: 'get',
      params: { category: undefined },
    })
  })

  it('3. getAgentDetail(42) -> GET /opc/agent/detail/42', () => {
    getAgentDetail(42)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/agent/detail/42',
      method: 'get',
    })
  })

  it('4. hireAgent({agentId:1}) -> POST /opc/agent/hire data={agentId:1}', () => {
    hireAgent({ agentId: 1 })
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/agent/hire',
      method: 'post',
      data: { agentId: 1 },
    })
  })

  it('5. listMyInstances() -> GET /opc/agent/instances', () => {
    listMyInstances()
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/agent/instances',
      method: 'get',
    })
  })

  it('6. getInstance(99) -> GET /opc/agent/instance/99', () => {
    getInstance(99)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/agent/instance/99',
      method: 'get',
    })
  })

  it('7. instanceAction(7,"pause") -> POST /opc/agent/instance/7/action params={action}', () => {
    instanceAction(7, 'pause')
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/agent/instance/7/action',
      method: 'post',
      params: { action: 'pause' },
    })
  })

  it('8. listInstanceTasks(7,50) -> GET /opc/agent/instance/7/tasks params={limit:50}', () => {
    listInstanceTasks(7, 50)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/agent/instance/7/tasks',
      method: 'get',
      params: { limit: 50 },
    })
  })

  it('9. listInstanceUsage(7) -> GET /opc/agent/instance/7/usage params={limit:20 default}', () => {
    listInstanceUsage(7)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/agent/instance/7/usage',
      method: 'get',
      params: { limit: 20 },
    })
  })

  it('10. dailyUsage(1,"2026-09-01","2026-09-30") -> GET /opc/agent/usage/daily params', () => {
    dailyUsage(1, '2026-09-01', '2026-09-30')
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/agent/usage/daily',
      method: 'get',
      params: { companyId: 1, startDate: '2026-09-01', endDate: '2026-09-30' },
    })
  })

  it('11. usageSummary(1) -> GET /opc/agent/usage/summary params={companyId,bizDate:undefined}', () => {
    usageSummary(1)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/agent/usage/summary',
      method: 'get',
      params: { companyId: 1, bizDate: undefined },
    })
  })

  it('12. runTask({taskId:5}) -> POST /opc/agent/task/run data={taskId:5}', () => {
    runTask({ taskId: 5 })
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/agent/task/run',
      method: 'post',
      data: { taskId: 5 },
    })
  })

  it('13. return value is the request() promise (passthrough)', () => {
    const sentinel = Symbol('promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(listMarket()).toBe(sentinel)
    expect(hireAgent({})).toBe(sentinel)
  })
})