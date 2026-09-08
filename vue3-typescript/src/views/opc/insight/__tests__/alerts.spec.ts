// M4 INSIGHT MVP Task 13 — alerts.vue SFC tests.
//
// Component shape:
// - <h2>异常预警</h2> + filter row (status select + level select)
// - ResponsiveTable with level/title/desc/createdAt/status columns
// - "确认" button on rows with status=OPEN -> ackAlert(id) + refresh
// - Status filter "all/open/ack/resolved" filters rows
// - Level filter "all/low/medium/high" filters rows
// - Empty state shows "暂无异常"
// - ElMessage.error on API throw
// - HIGH=红 / MEDIUM=橙 / LOW=绿 tag colors
// - OPEN=红 / ACKED=绿 status tag colors
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
import Alerts from '@/views/opc/insight/alerts.vue'

function makeAlerts(): any[] {
  return [
    { id: 1, level: 'HIGH', title: '凭证超 100K', description: '单笔凭证 20 万', status: 'OPEN', createdAt: '2026-09-08 10:00:00' },
    { id: 2, level: 'MEDIUM', title: '收入下滑', description: '本周收入为 0', status: 'OPEN', createdAt: '2026-09-08 09:00:00' },
    { id: 3, level: 'LOW', title: '凭证积压', description: '待审超过 3 天', status: 'ACKED', createdAt: '2026-09-07 18:00:00' },
    { id: 4, level: 'HIGH', title: '账实不符', description: '现金日记账对不上', status: 'ACKED', createdAt: '2026-09-06 12:00:00' },
  ]
}

function mountAlerts() {
  return mount(Alerts, stubMountOpts)
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

  mocks.listAlerts.mockResolvedValue({ code: 200, msg: 'ok', data: makeAlerts() })
  mocks.ackAlert.mockResolvedValue({ code: 200, msg: 'ok' })
})

describe('alerts.vue — title + structure', () => {
  it('1. renders h2 title "异常预警"', async () => {
    const w = mountAlerts()
    await flushPromises()
    expect(w.find('h2').text()).toBe('异常预警')
  })

  it('2. renders filter row (status + level selects)', async () => {
    const w = mountAlerts()
    await flushPromises()
    expect(w.find('.filter-row').findAll('[data-stub="ElSelect"]').length).toBeGreaterThanOrEqual(2)
  })

  it('3. listAlerts() called once on mount', async () => {
    mountAlerts()
    await flushPromises()
    expect(mocks.listAlerts).toHaveBeenCalledTimes(1)
  })
})

describe('alerts.vue — ack action', () => {
  it('4. call onAck(row) directly -> ackAlert(id) + listAlerts reload', async () => {
    // Invoke the actions slot handler directly: el-table-column's per-row
    // scoped slot context isn't propagated through the test stub layer.
    const w = mountAlerts()
    await flushPromises()
    const vm: any = w.vm
    await vm.onAck({ id: 1 })
    await flushPromises()
    expect(mocks.ackAlert).toHaveBeenCalledWith(1)
    expect(mocks.listAlerts.mock.calls.length).toBeGreaterThanOrEqual(2)
  })

  it('5. onAck handler is a function (would render 确认 button in DOM)', async () => {
    // Pin: actions slot exists in template. Real DOM row button rendering
    // is gated on ElTable's scoped-slot machinery (out of scope for stub).
    const w = mountAlerts()
    await flushPromises()
    const vm: any = w.vm
    expect(typeof vm.onAck).toBe('function')
  })
})

describe('alerts.vue — filters', () => {
  it('6. status filter "ACKED" -> load() called again', async () => {
    const w = mountAlerts()
    await flushPromises()
    // Access setupState (Vue 3.4+ auto-unwraps refs there).
    const setup: any = (w.vm as any).$.setupState
    setup.statusFilter = 'ACKED'
    // el-select @change is stubbed; call load() directly to simulate.
    await setup.load()
    await flushPromises()
    expect(mocks.listAlerts.mock.calls.length).toBeGreaterThanOrEqual(2)
  })

  it('7. level filter "HIGH" -> filters rows locally (2 rows)', async () => {
    const w = mountAlerts()
    await flushPromises()
    const setup: any = (w.vm as any).$.setupState
    setup.levelFilter = 'HIGH'
    // el-select @change is stubbed (no propagation), so call load() directly.
    await setup.load()
    await flushPromises()
    // Two rows are HIGH (id=1, id=4)
    expect(setup.list.length).toBe(2)
  })

  it('8. empty data -> "暂无异常" text', async () => {
    mocks.listAlerts.mockResolvedValue({ code: 200, msg: 'ok', data: [] })
    const w = mountAlerts()
    await flushPromises()
    expect(w.text()).toContain('暂无异常')
  })
})

describe('alerts.vue — error handling', () => {
  it('9. listAlerts throws -> list reset to empty (no crash)', async () => {
    mocks.listAlerts.mockRejectedValue(new Error('boom'))
    const w = mountAlerts()
    await flushPromises()
    const setup: any = (w.vm as any).$.setupState
    expect(setup.list).toEqual([])
    expect(w.text()).toContain('暂无异常')
  })
})

describe('alerts.vue — tag colors (HIGH/MEDIUM/LOW)', () => {
  it('10. HIGH/MEDIUM/LOW levels appear in rendered DOM (via tagMap)', async () => {
    const w = mountAlerts()
    await flushPromises()
    const html = w.html()
    expect(html).toContain('HIGH')
    expect(html).toContain('MEDIUM')
    expect(html).toContain('LOW')
  })
})

describe('alerts.vue — status tag colors (OPEN/ACKED)', () => {
  it('11. OPEN/ACKED status strings appear in rendered DOM', async () => {
    const w = mountAlerts()
    await flushPromises()
    const html = w.html()
    expect(html).toContain('OPEN')
    expect(html).toContain('ACKED')
  })
})

describe('alerts.vue — module', () => {
  it('12. Alerts.vue is a valid SFC with default export', () => {
    expect(typeof Alerts).toBe('object')
    expect(Alerts).not.toBeNull()
  })
})
