// M4 INSIGHT MVP Task 13 — dashboard.vue SFC tests.
//
// Component shape:
// - <h2>经营驾驶舱</h2> header
// - 6 KPI cards (revenue / expense / voucher / pending / wallet / tokens)
// - 2 trend chart placeholders (revenue + expense)
// - Alert Top 5 list (from dashboard response)
// - Advice Top 3 list (from dashboard response)
// - partial badge when kpi.partial=true
// - is-mobile-stack on KPI row (mobile breakpoint)
// - Skeleton / empty state when data is null
// - ElMessage.error on API throw
// - Refresh button re-fetches
//
// We mock @/api/opc/insight entirely so the test runs offline. Element Plus
// components are registered via global.components stubs (matching the
// ResponsiveTable.spec.ts W5.2 pattern), sourced from ./element-plus-stubs.ts.
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

vi.mock('@element-plus/icons-vue', () => ({
  Refresh: { template: '<i class="mock-refresh-icon" />' },
}))

import { stubMountOpts } from './element-plus-stubs'
import Dashboard from '@/views/opc/insight/dashboard.vue'

function makeKpi(overrides: Record<string, any> = {}) {
  return {
    totalRevenue: 125000.5,
    totalExpense: 87234.0,
    voucherCount: 42,
    pendingVoucherCount: 7,
    walletBalance: 3000.0,
    tokenUsage: 123456,
    partial: false,
    ...overrides,
  }
}

function makeAlerts(n = 5) {
  return Array.from({ length: n }, (_, i) => ({
    id: i + 1,
    level: i === 0 ? 'HIGH' : i === 1 ? 'MEDIUM' : 'LOW',
    title: `异常 ${i + 1}`,
    description: `描述 ${i + 1}`,
    status: 'OPEN',
  }))
}

function makeAdvice(n = 3) {
  return Array.from({ length: n }, (_, i) => ({
    id: i + 1,
    topic: ['cost_optimization', 'revenue_growth', 'risk_warning'][i],
    summary: `建议 ${i + 1}`,
  }))
}

function mountDashboard() {
  return mount(Dashboard, stubMountOpts)
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

  mocks.dashboard.mockResolvedValue({
    code: 200,
    msg: '操作成功',
    data: { kpi: makeKpi(), alerts: makeAlerts(5), advice: makeAdvice(3) },
  })
})

describe('dashboard.vue — title + structure', () => {
  it('1. renders h2 title "经营驾驶舱"', async () => {
    const w = mountDashboard()
    await flushPromises()
    expect(w.find('h2').text()).toBe('经营驾驶舱')
  })

  it('2. calls dashboard() API exactly once on mount', async () => {
    const w = mountDashboard()
    await flushPromises()
    expect(mocks.dashboard).toHaveBeenCalledTimes(1)
  })

  it('3. KPI cards render 6 entries (loops over kpiCards)', async () => {
    const w = mountDashboard()
    await flushPromises()
    const cards = w.findAll('.kpi-card')
    expect(cards.length).toBe(6)
  })
})

describe('dashboard.vue — 6 KPI cards', () => {
  it('4. revenue card shows ¥ 125,000.50', async () => {
    const w = mountDashboard()
    await flushPromises()
    expect(w.find('[data-testid="kpi-revenue"]').text()).toContain('125,000.50')
  })

  it('5. expense card shows ¥ 87,234.00', async () => {
    const w = mountDashboard()
    await flushPromises()
    expect(w.find('[data-testid="kpi-expense"]').text()).toContain('87,234.00')
  })

  it('6. voucher card shows 42', async () => {
    const w = mountDashboard()
    await flushPromises()
    expect(w.find('[data-testid="kpi-voucher"]').text()).toContain('42')
  })

  it('7. pending card shows 7', async () => {
    const w = mountDashboard()
    await flushPromises()
    expect(w.find('[data-testid="kpi-pending"]').text()).toContain('7')
  })

  it('8. wallet card shows ¥ 3,000.00', async () => {
    const w = mountDashboard()
    await flushPromises()
    expect(w.find('[data-testid="kpi-wallet"]').text()).toContain('3,000.00')
  })

  it('9. tokens card shows 123,456', async () => {
    const w = mountDashboard()
    await flushPromises()
    expect(w.find('[data-testid="kpi-tokens"]').text()).toContain('123,456')
  })
})

describe('dashboard.vue — trend charts', () => {
  it('10. trend-revenue placeholder card renders', async () => {
    const w = mountDashboard()
    await flushPromises()
    expect(w.find('[data-testid="trend-revenue"]').exists()).toBe(true)
  })

  it('11. trend-expense placeholder card renders', async () => {
    const w = mountDashboard()
    await flushPromises()
    expect(w.find('[data-testid="trend-expense"]').exists()).toBe(true)
  })
})

describe('dashboard.vue — alert + advice lists', () => {
  it('12. alert list renders 5 entries', async () => {
    const w = mountDashboard()
    await flushPromises()
    expect(w.find('[data-testid="alert-list"]').findAll('.alert-item').length).toBe(5)
  })

  it('13. advice list renders 3 entries', async () => {
    const w = mountDashboard()
    await flushPromises()
    expect(w.find('[data-testid="advice-list"]').findAll('.advice-item').length).toBe(3)
  })
})

describe('dashboard.vue — partial flag', () => {
  it('14. partial=true renders 数据降级 badge', async () => {
    mocks.dashboard.mockResolvedValue({
      code: 200,
      msg: 'ok',
      data: { kpi: makeKpi({ partial: true }), alerts: [], advice: [] },
    })
    const w = mountDashboard()
    await flushPromises()
    const badge = w.find('.partial-badge')
    expect(badge.exists()).toBe(true)
    expect(badge.text()).toContain('数据降级')
  })

  it('15. partial=false does NOT render 数据降级 badge', async () => {
    mocks.dashboard.mockResolvedValue({
      code: 200,
      msg: 'ok',
      data: { kpi: makeKpi({ partial: false }), alerts: [], advice: [] },
    })
    const w = mountDashboard()
    await flushPromises()
    expect(w.find('.partial-badge').exists()).toBe(false)
  })
})

describe('dashboard.vue — loading + empty states', () => {
  it('16. API throws -> kpiCards reset to "-" values', async () => {
    mocks.dashboard.mockRejectedValue(new Error('网络异常'))
    const w = mountDashboard()
    await flushPromises()
    // kpi.value is null -> kpiCards computed all show '-' values
    const cards = w.findAll('.kpi-card')
    for (const c of cards) {
      expect(c.find('.kpi-value').text()).toBe('-')
    }
  })

  it('17. empty list -> "暂无异常" / "暂无建议" messages shown', async () => {
    mocks.dashboard.mockResolvedValue({
      code: 200,
      msg: 'ok',
      data: { kpi: makeKpi(), alerts: [], advice: [] },
    })
    const w = mountDashboard()
    await flushPromises()
    const empties = w.findAll('.empty')
    const texts = empties.map((e) => e.text())
    expect(texts.some((t) => t.includes('暂无异常'))).toBe(true)
    expect(texts.some((t) => t.includes('暂无建议'))).toBe(true)
  })
})

describe('dashboard.vue — refresh', () => {
  it('18. refresh button triggers re-fetch (dashboard called twice)', async () => {
    const w = mountDashboard()
    await flushPromises()
    expect(mocks.dashboard).toHaveBeenCalledTimes(1)
    // Find refresh button (text "刷新")
    const btn = w.findAll('button').find((b) => b.text().includes('刷新'))
    expect(btn).toBeTruthy()
    await btn!.trigger('click')
    await flushPromises()
    expect(mocks.dashboard).toHaveBeenCalledTimes(2)
  })
})
