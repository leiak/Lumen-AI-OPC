// M4 INSIGHT MVP Task 13 — advice.vue SFC tests.
//
// Component shape:
// - <h2>决策建议</h2> + topic selector (5 topics) + 生成/刷新 buttons
// - el-table lists past advice (sorted desc by createTime)
// - Click "重新生成" -> regenerateAdvice(id) -> refresh
// - Generate button POSTs /opc/insight/advice?topic= via raw request()
// - Disabled when no topic selected
// - Empty list -> "暂无建议"
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const mocks = vi.hoisted(() => ({
  dashboard: vi.fn(),
  listAlerts: vi.fn(),
  ackAlert: vi.fn(),
  listDaily: vi.fn(),
  getDaily: vi.fn(),
  generateDaily: vi.fn(),
  listAdvice: vi.fn(),
  getAdvice: vi.fn(),
  regenerateAdvice: vi.fn(),
}))

vi.mock('@/api/opc/insight', () => ({
  dashboard: mocks.dashboard,
  listAlerts: mocks.listAlerts,
  ackAlert: mocks.ackAlert,
  listDaily: mocks.listDaily,
  getDaily: mocks.getDaily,
  generateDaily: mocks.generateDaily,
  listAdvice: mocks.listAdvice,
  getAdvice: mocks.getAdvice,
  regenerateAdvice: mocks.regenerateAdvice,
}))

const requestMock = vi.hoisted(() => vi.fn())
vi.mock('@/utils/request', () => ({
  default: requestMock,
}))

import { stubMountOpts } from './element-plus-stubs'
import Advice from '@/views/opc/insight/advice.vue'

function makeAdvice(): any[] {
  return [
    { id: 1, topic: 'cost_optimization', summary: '砍掉云服务', createTime: '2026-09-08 10:00:00' },
    { id: 2, topic: 'revenue_growth', summary: '拓展渠道', createTime: '2026-09-07 09:00:00' },
    { id: 3, topic: 'risk_warning', summary: '关注现金流', createTime: '2026-09-06 08:00:00' },
  ]
}

function mountAdvice() {
  return mount(Advice, stubMountOpts)
}

beforeEach(() => {
  mocks.dashboard.mockReset()
  mocks.listAlerts.mockReset()
  mocks.ackAlert.mockReset()
  mocks.listDaily.mockReset()
  mocks.getDaily.mockReset()
  mocks.generateDaily.mockReset()
  mocks.listAdvice.mockReset()
  mocks.getAdvice.mockReset()
  mocks.regenerateAdvice.mockReset()
  requestMock.mockReset()

  mocks.listAdvice.mockResolvedValue({ code: 200, msg: 'ok', data: makeAdvice() })
  mocks.getAdvice.mockResolvedValue({ code: 200, msg: 'ok', data: { id: 1, summary: 'x', content: 'y' } })
  mocks.regenerateAdvice.mockResolvedValue({ code: 200, msg: 'ok', data: { adviceId: 1 } })
  requestMock.mockResolvedValue({ code: 200, msg: 'ok', data: { adviceId: 99 } })
})

describe('advice.vue — title + structure', () => {
  it('1. renders h2 title "决策建议"', async () => {
    const w = mountAdvice()
    await flushPromises()
    expect(w.find('h2').text()).toBe('决策建议')
  })

  it('2. listAdvice called once on mount', async () => {
    mountAdvice()
    await flushPromises()
    expect(mocks.listAdvice).toHaveBeenCalledTimes(1)
  })
})

describe('advice.vue — topic selector', () => {
  it('3. TOPICS has 5 entries (cost_optimization / revenue_growth / cashflow_health / tax_planning / risk_warning)', async () => {
    const w = mountAdvice()
    await flushPromises()
    const vm: any = w.vm
    const topics = vm.TOPICS
    expect(topics.length).toBe(5)
    expect(topics.map((t: any) => t.value)).toEqual([
      'cost_optimization',
      'revenue_growth',
      'cashflow_health',
      'tax_planning',
      'risk_warning',
    ])
  })

  it('4. topicLabel returns Chinese label for known topic, "-" for unknown', async () => {
    const w = mountAdvice()
    await flushPromises()
    const vm: any = w.vm
    expect(vm.topicLabel('cost_optimization')).toBe('成本优化')
    expect(vm.topicLabel('revenue_growth')).toBe('收入增长')
    expect(vm.topicLabel('cashflow_health')).toBe('现金流健康')
    expect(vm.topicLabel('tax_planning')).toBe('税务规划')
    expect(vm.topicLabel('risk_warning')).toBe('风险预警')
    expect(vm.topicLabel('xxx')).toBe('xxx')
    expect(vm.topicLabel(undefined)).toBe('-')
  })
})

describe('advice.vue — generate', () => {
  it('5. "生成建议" button disabled when no topic selected', async () => {
    const w = mountAdvice()
    await flushPromises()
    const vm: any = w.vm
    expect(vm.selectedTopic).toBe('')
    const btn = w.findAll('button').find((b) => b.text().includes('生成建议'))
    expect(btn).toBeTruthy()
    expect(btn!.attributes('disabled')).toBeDefined()
  })

  it('6. select topic + click generate -> request() called with topic param', async () => {
    const w = mountAdvice()
    await flushPromises()
    const vm: any = w.vm
    vm.selectedTopic = 'cost_optimization'
    await flushPromises()
    const btn = w.findAll('button').find((b) => b.text().includes('生成建议'))
    expect(btn!.attributes('disabled')).toBeUndefined()
    await btn!.trigger('click')
    await flushPromises()
    expect(requestMock).toHaveBeenCalledTimes(1)
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/opc/insight/advice')
    expect(call.method).toBe('post')
    expect(call.params).toMatchObject({ topic: 'cost_optimization' })
  })
})

describe('advice.vue — list + regenerate', () => {
  it('7. sortedList is sorted desc by createTime', async () => {
    const w = mountAdvice()
    await flushPromises()
    const vm: any = w.vm
    expect(vm.sortedList.map((r: any) => r.createTime)).toEqual([
      '2026-09-08 10:00:00',
      '2026-09-07 09:00:00',
      '2026-09-06 08:00:00',
    ])
  })

  it('8. call onRegenerate(row) directly -> regenerateAdvice(id) called', async () => {
    // We invoke the actions slot handler directly because el-table-column's
    // scoped slot row context can't propagate cleanly through the test
    // stubs (production ElTable provides `row` via runtime scope injection
    // that the stub layer doesn't replicate).
    const w = mountAdvice()
    await flushPromises()
    const vm: any = w.vm
    await vm.onRegenerate({ id: 1 })
    await flushPromises()
    expect(mocks.regenerateAdvice).toHaveBeenCalledWith(1)
  })
})

describe('advice.vue — error handling', () => {
  it('9. listAdvice throws -> list empty, "暂无建议" shown', async () => {
    mocks.listAdvice.mockRejectedValue(new Error('boom'))
    const w = mountAdvice()
    await flushPromises()
    const vm: any = w.vm
    expect(vm.list).toEqual([])
    expect(w.text()).toContain('暂无建议')
  })
})

describe('advice.vue — module', () => {
  it('10. Advice.vue is a valid SFC with default export', () => {
    expect(typeof Advice).toBe('object')
    expect(Advice).not.toBeNull()
  })
})
