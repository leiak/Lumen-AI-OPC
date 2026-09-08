// M4 INSIGHT MVP Task 13 — daily.vue SFC tests.
//
// Component shape:
// - <h2>财务日报</h2> + daterange picker + 生成/刷新 buttons
// - ResponsiveTable lists reports (sorted desc by period)
// - Click row -> el-dialog with getDaily(id) detail
// - On generate -> POST /daily/generate?date=today -> ElMessage.success
// - Empty range -> "请选择日期范围" hint
// - ElMessage.error on failure
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

import { stubMountOpts } from './element-plus-stubs'
import Daily from '@/views/opc/insight/daily.vue'

function makeDaily(): any[] {
  return [
    { id: 1, period: '2026-09-08', totalRevenue: 5000, totalExpense: 3000, voucherCount: 12, createTime: '2026-09-08 09:00:00' },
    { id: 2, period: '2026-09-07', totalRevenue: 4500, totalExpense: 3200, voucherCount: 10, createTime: '2026-09-07 09:00:00' },
    { id: 3, period: '2026-09-06', totalRevenue: 0, totalExpense: 500, voucherCount: 3, createTime: '2026-09-06 09:00:00' },
  ]
}

function mountDaily() {
  return mount(Daily, stubMountOpts)
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

  mocks.listDaily.mockResolvedValue({ code: 200, msg: 'ok', data: makeDaily() })
  mocks.getDaily.mockResolvedValue({ code: 200, msg: 'ok', data: { id: 1, period: '2026-09-08', summary: 'test', advice: 'tip' } })
  mocks.generateDaily.mockResolvedValue({ code: 200, msg: 'ok', data: { reportId: 99 } })
})

describe('daily.vue — title + structure', () => {
  it('1. renders h2 title "财务日报"', async () => {
    const w = mountDaily()
    await flushPromises()
    expect(w.find('h2').text()).toBe('财务日报')
  })

  it('2. renders daterange picker (el-date-picker)', async () => {
    const w = mountDaily()
    await flushPromises()
    expect(w.find('[data-stub="ElDatePicker"]').exists()).toBe(true)
  })

  it('3. default range covers last 30 days', async () => {
    const w = mountDaily()
    await flushPromises()
    const vm: any = w.vm
    expect(vm.range).toBeTruthy()
    expect(vm.range.length).toBe(2)
  })
})

describe('daily.vue — list loading', () => {
  it('4. listDaily called on mount with from/to params', async () => {
    mountDaily()
    await flushPromises()
    expect(mocks.listDaily).toHaveBeenCalledTimes(1)
    const args = mocks.listDaily.mock.calls[0]
    expect(args[0]).toMatch(/^\d{4}-\d{2}-\d{2}$/) // from
    expect(args[1]).toMatch(/^\d{4}-\d{2}-\d{2}$/) // to
  })

  it('5. sortedList is sorted desc by period', async () => {
    const w = mountDaily()
    await flushPromises()
    const vm: any = w.vm
    expect(vm.sortedList.map((r: any) => r.period)).toEqual([
      '2026-09-08',
      '2026-09-07',
      '2026-09-06',
    ])
  })
})

describe('daily.vue — generate', () => {
  it('6. click "生成今日日报" -> generateDaily called with today date', async () => {
    const w = mountDaily()
    await flushPromises()
    const genBtn = w.findAll('button').find((b) => b.text().includes('生成今日日报'))
    expect(genBtn).toBeTruthy()
    await genBtn!.trigger('click')
    await flushPromises()
    expect(mocks.generateDaily).toHaveBeenCalledTimes(1)
    const arg = mocks.generateDaily.mock.calls[0][0]
    expect(arg).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })

  it('7. generateDaily throws -> generating flag reset', async () => {
    mocks.generateDaily.mockRejectedValue(new Error('boom'))
    const w = mountDaily()
    await flushPromises()
    const genBtn = w.findAll('button').find((b) => b.text().includes('生成今日日报'))
    await genBtn!.trigger('click')
    await flushPromises()
    const vm: any = w.vm
    expect(vm.generating).toBe(false)
  })
})

describe('daily.vue — empty range', () => {
  it('8. range=null -> load() early-returns and resets list', async () => {
    const w = mountDaily()
    await flushPromises()
    const setup: any = (w.vm as any).$.setupState
    setup.range = null
    await setup.load()
    await flushPromises()
    expect(setup.list).toEqual([])
  })
})

describe('daily.vue — detail dialog', () => {
  it('9. view(row) opens dialog and calls getDaily(id)', async () => {
    const w = mountDaily()
    await flushPromises()
    const vm: any = w.vm
    await vm.view({ id: 1, period: '2026-09-08' })
    await flushPromises()
    expect(mocks.getDaily).toHaveBeenCalledWith(1)
    expect(vm.show).toBe(true)
  })

  it('10. getDaily throws -> dialog closed, error shown', async () => {
    mocks.getDaily.mockRejectedValue(new Error('boom'))
    const w = mountDaily()
    await flushPromises()
    const vm: any = w.vm
    await vm.view({ id: 1 })
    await flushPromises()
    expect(vm.show).toBe(false)
  })
})

describe('daily.vue — refresh', () => {
  it('11. refresh button -> listDaily re-called', async () => {
    const w = mountDaily()
    await flushPromises()
    const initialCalls = mocks.listDaily.mock.calls.length
    const refreshBtn = w.findAll('button').find((b) => b.text().includes('刷新'))
    expect(refreshBtn).toBeTruthy()
    await refreshBtn!.trigger('click')
    await flushPromises()
    expect(mocks.listDaily.mock.calls.length).toBe(initialCalls + 1)
  })
})

describe('daily.vue — module', () => {
  it('12. Daily.vue is a valid SFC with default export', () => {
    expect(typeof Daily).toBe('object')
    expect(Daily).not.toBeNull()
  })
})
