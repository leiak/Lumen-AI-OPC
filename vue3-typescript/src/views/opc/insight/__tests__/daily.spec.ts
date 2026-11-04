// M4 INSIGHT MVP Task 13 — daily.vue SFC tests.
//
// Component shape:
// - <h2>财务日报</h2> + daterange picker + 生成/刷新 buttons
// - ResponsiveTable lists reports (sorted desc by period)
// - Click row -> el-dialog with getDaily(id) detail
// - On generate -> POST /daily/generate?date=today -> ElMessage.success
// - Empty range -> "请选择日期范围" hint
// - ElMessage.error on failure
//
// Task 9（日报的数字列 + 状态标）：
// - 列表的「收入/支出/流水笔数/凭证数」四列读的是**后端真的返回的**字段 ——
//   kpiJson（JSON 字符串）里的 query_bank_flow / query_voucher。解析不出来就是空值
//   （渲染成 '-'），不是 0 —— 0.00 是在说「收入是 0 元」。
// - 「状态」「来源」两列走 ResponsiveTable 本来就有的 type:'tag' + formatter + tagMap。
//   （ResponsiveTable **没有**按列的 slot：一个单元格只渲染一个 tag，
//     一列塞两个 tag 就得改这个几十个页面在用的共享组件 —— 不改。）
// - 新增用例在**移动模式**下跑（mock @vueuse/core 把窗口宽度设成 500）：
//   桌面模式下 ElTableColumn 桩自己造了个空 row（{ row: {} }），
//   单元格恒为 '-' —— 在那儿断言数字等于断言一条永远为真的假测试。
//
// 修复轮 R=1：
// - 弹窗的状态/来源**兜底自称「完整」/「手工」** —— `report_status = NULL` 的旧日报
//   根本不知道完整不完整，界面不能替它说「完整」。未知取值 → **不出 tag**，显示「—」。
// - 弹窗的「取不到」渲染（formatMoney 的空值分支、凭证数的 `?? '—'`）**零测试保护** ——
//   补一条坏 kpiJson 的用例。
// - 用例 21 原来的 fixture 恰好是兜底分支的文案 → **没牙齿**（写死 tag 也全绿）。
//   改成 FALLBACK/MANUAL，并补 PARTIAL/AGENT_WORKFLOW 与 null/null（旧数据）两条。
// - `onGenerate` 的提示恒显示「（ID=undefined）」—— 后端 `generate` 返回 `R<Void>`，
//   压根没有 id。提示里别提 id；mock 也不能自己编一个 `reportId`。
//
// 修复轮 R=2：
// - `FULL → 「完整」` 这行**没有任何正向断言**（改成「完完整」也全绿）→ 补正向用例
//   （列表状态列 + 弹窗那一项都显示「完整」，且不含「未走 LLM」「部分完成」），
//   外加一条断 tag 颜色的用例（FULL→success / PARTIAL→warning / FALLBACK→danger）。
// - 列表用例 17 / 18 / 19 断的是**整张表的文本** → FALLBACK 与 PARTIAL 各有一行，
//   两个分支对调后两个字都还在表里，断言照样成立 → 改成**行级**断言：
//   先按「所属期」定位到那一张卡片，再断「状态」/「来源」那一列单元格。
//   （第 1 行的 summaryMd 本身就含「未走 LLM」，断整段文本会被摘要喂绿。）
import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { ElMessage } from 'element-plus'

const widthRef = ref(1200)

vi.mock('@vueuse/core', async () => {
  const actual = await vi.importActual<any>('@vueuse/core')
  return { ...actual, useWindowSize: () => ({ width: widthRef, height: ref(800) }) }
})

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

/**
 * 按后端**真实返回**的样子写的 fixture。
 *
 * 后端 DailyReportVo 里**没有** totalRevenue / totalExpense / voucherCount 这三个
 * 顶层字段（以前 fixture 里编了 `totalRevenue: 5000` —— 后端从不返回它，
 * 所以测试一直是绿的、页面一直是空的）。数字全在 kpiJson 这个 JSON 字符串里，
 * 而且有两种形状：
 * - 数字员工日报（AGENT_WORKFLOW）：
 *   {query_bank_flow:{totalIn,totalOut,count,...}, query_voucher:{count,...}}
 * - insight 自己生成的日报（KpiSnapshot）：{totalRevenue,totalExpense,voucherCount}
 */
function makeDaily(): any[] {
  return [
    {
      id: 1,
      period: '2026-09-08',
      kpiJson: JSON.stringify({
        query_bank_flow: {
          source: 'opc_finance_bank_flow',
          companyId: 2,
          fromDate: '2026-08-10',
          toDate: '2026-09-08',
          count: 6,
          totalIn: 51892.0,
          totalOut: 32430.0,
          net: 19462.0,
          yesterdayCount: 0,
          yesterdayIn: 0.0,
          yesterdayOut: 0.0,
          items: [],
        },
        query_voucher: {
          source: 'opc_finance_voucher',
          companyId: 2,
          fromDate: '2026-08-10',
          toDate: '2026-09-08',
          count: 5,
          totalIn: 35400.0,
          totalOut: 29750.0,
          items: [],
        },
      }),
      summaryMd: '昨日无资金流水',
      adviceMd: '保持现状',
      llmUsed: 'MiniMax-Text-01',
      reportStatus: 'FULL',
      reportSource: 'AGENT_WORKFLOW',
      createTime: '2026-09-08 09:00:00',
    },
    {
      id: 2,
      period: '2026-09-07',
      kpiJson: JSON.stringify({ totalRevenue: 4500.0, totalExpense: 3200.0, voucherCount: 10 }),
      summaryMd: '[自动聚合·未走 LLM] ...',
      adviceMd: '...',
      llmUsed: 'FALLBACK',
      reportStatus: 'FALLBACK',
      reportSource: 'MANUAL',
      createTime: '2026-09-07 09:00:00',
    },
    {
      id: 3,
      period: '2026-09-06',
      kpiJson: 'not json',
      summaryMd: 'kpi 解析失败',
      adviceMd: null,
      llmUsed: 'FALLBACK',
      reportStatus: 'PARTIAL',
      reportSource: 'MANUAL',
      createTime: '2026-09-06 09:00:00',
    },
  ]
}

/** 三条按后端真实形状写的日报：数字员工 / 手工 / PARTIAL。 */
function makeRealRows(): any[] {
  return [
    // 数字员工产出的日报：kpiJson 是字符串，内容是数字员工日报的嵌套形状
    {
      id: 11,
      period: '2026-11-01',
      kpiJson: JSON.stringify({
        query_bank_flow: {
          source: 'opc_finance_bank_flow',
          companyId: 2,
          fromDate: '2026-10-07',
          toDate: '2026-11-01',
          count: 6,
          totalIn: 51892.0,
          totalOut: 32430.0,
          net: 19462.0,
          yesterdayCount: 0,
          yesterdayIn: 0.0,
          yesterdayOut: 0.0,
          items: [],
        },
        query_voucher: {
          source: 'opc_finance_voucher',
          companyId: 2,
          fromDate: '2026-10-07',
          toDate: '2026-11-01',
          count: 5,
          totalIn: 35400.0,
          totalOut: 29750.0,
          items: [],
        },
      }),
      summaryMd: '昨日无资金流水',
      adviceMd: null,
      llmUsed: 'MiniMax-Text-01',
      reportStatus: 'FULL',
      reportSource: 'AGENT_WORKFLOW',
      createTime: '2026-11-01 09:00:00',
    },
    // insight 自己生成的日报：kpiJson 是 KpiSnapshot 形状
    {
      id: 12,
      period: '2026-10-31',
      kpiJson: JSON.stringify({ totalRevenue: 19600.0, totalExpense: 6200.0, voucherCount: 12 }),
      summaryMd: '[自动聚合·未走 LLM] ...',
      adviceMd: '...',
      llmUsed: 'FALLBACK',
      reportStatus: 'FALLBACK',
      reportSource: 'MANUAL',
      createTime: '2026-10-31 09:00:00',
    },
    // 数字是真的、但缺了 LLM 解读 —— PARTIAL
    {
      id: 13,
      period: '2026-10-30',
      kpiJson: JSON.stringify({
        query_bank_flow: {
          source: 'opc_finance_bank_flow',
          companyId: 2,
          fromDate: '2026-10-06',
          toDate: '2026-10-30',
          count: 2,
          totalIn: 1000.0,
          totalOut: 400.0,
          net: 600.0,
          items: [],
        },
        query_voucher: {
          source: 'opc_finance_voucher',
          companyId: 2,
          fromDate: '2026-10-06',
          toDate: '2026-10-30',
          count: 1,
          totalIn: 1000.0,
          totalOut: 400.0,
          items: [],
        },
      }),
      summaryMd: '数字是真的，但没写解读',
      adviceMd: null,
      llmUsed: 'MiniMax-Text-01',
      reportStatus: 'PARTIAL',
      reportSource: 'AGENT_WORKFLOW',
      createTime: '2026-10-30 09:00:00',
    },
  ]
}

/**
 * 详情弹窗用的 fixture。
 *
 * <p>summaryMd / adviceMd **刻意不含**「完整 / 部分完成 / 未走 LLM / 数字员工 / 手工」
 * 这几个字 —— 否则断言会断到摘要里的字，而不是断到状态 tag 上
 * （`makeRealRows()` 的第 2 行摘要就写着「[自动聚合·未走 LLM] ...」，不能拿它做弹窗断言）。</p>
 */
function makeDialogRow(over: any = {}): any {
  return {
    id: 41,
    period: '2026-11-01',
    kpiJson: JSON.stringify({
      query_bank_flow: {
        source: 'opc_finance_bank_flow',
        companyId: 2,
        fromDate: '2026-10-07',
        toDate: '2026-11-01',
        count: 6,
        totalIn: 51892.0,
        totalOut: 32430.0,
        net: 19462.0,
        items: [],
      },
      query_voucher: {
        source: 'opc_finance_voucher',
        companyId: 2,
        fromDate: '2026-10-07',
        toDate: '2026-11-01',
        count: 5,
        totalIn: 35400.0,
        totalOut: 29750.0,
        items: [],
      },
    }),
    summaryMd: '（无解读）',
    adviceMd: '（无建议）',
    llmUsed: 'MiniMax-Text-01',
    reportStatus: 'FULL',
    reportSource: 'AGENT_WORKFLOW',
    createTime: '2026-11-01 09:00:00',
    ...over,
  }
}

function mountDaily() {
  return mount(Daily, stubMountOpts)
}

/** 点开第一张卡片的「查看」按钮，把弹窗等出来 */
async function openFirstDialog(w: any) {
  const viewBtn = w.findAll('button').find((b: any) => b.text().includes('查看'))
  expect(viewBtn).toBeTruthy()
  await viewBtn!.trigger('click')
  await flushPromises()
  return w.find('[data-stub="ElDialog"]')
}

/**
 * 移动模式（ResponsiveTable 的卡片视图）下，把每张卡片的「标签 -> 值」抽出来。
 * 桌面模式抽不到真值 —— 见文件头的说明。
 */
function cardValues(w: any): Record<string, string[]> {
  const out: Record<string, string[]> = {}
  const rows = w.findAll('.opc-responsive-table__card-row')
  for (const row of rows) {
    const labels = row.findAll('.opc-responsive-table__card-label')
    const values = row.findAll('.opc-responsive-table__card-value')
    if (!labels.length || !values.length) continue
    const label = labels[0].text()
    const value = values[0].text()
    if (!out[label]) out[label] = []
    out[label].push(value)
  }
  return out
}

/**
 * 按「所属期」定位到移动模式下那一张卡片（卡片标题就是 primary 列 = 所属期）。
 */
function findCard(w: any, period: string): any {
  const card = w.findAll('[data-stub="ElCard"]').find((c: any) => {
    const titles = c.findAll('.opc-responsive-table__card-title')
    return titles.length > 0 && titles[0].text() === period
  })
  expect(card, `没找到所属期为 ${period} 的卡片`).toBeTruthy()
  return card
}

/**
 * **行级**取值：按「所属期」定位到那一张卡片，再取它某一列单元格。
 *
 * <p>为什么不能断整表 / 整行文本：`makeRealRows()` 里 FALLBACK 和 PARTIAL 各有一行，
 * 两个分支对调之后「未走 LLM」和「部分完成」这两个字**都还在表里**（只是挂错行），
 * `toContain` 照样成立 —— 断不出任何东西。而且第 1 行的 `summaryMd`
 * 本身就写着「[自动聚合·未走 LLM] ...」，断整段文本会被摘要喂绿。
 * → **必须断到「状态」/「来源」那一列单元格本身。**</p>
 */
function cellOf(w: any, period: string, label: string): { text: string; html: string } {
  const card = findCard(w, period)
  for (const row of card.findAll('.opc-responsive-table__card-row')) {
    const labels = row.findAll('.opc-responsive-table__card-label')
    const values = row.findAll('.opc-responsive-table__card-value')
    if (!labels.length || !values.length) continue
    if (labels[0].text() === label) return { text: values[0].text(), html: values[0].html() }
  }
  throw new Error(`卡片 ${period} 里没有「${label}」这一列`)
}

beforeEach(() => {
  // 既有 12 条用例保持桌面模式（它们断言的是标题/选择器/排序/调用次数，与渲染模式无关）
  widthRef.value = 1200

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
  mocks.getDaily.mockResolvedValue({
    code: 200,
    msg: 'ok',
    data: {
      id: 1,
      period: '2026-09-08',
      summaryMd: 'test',
      adviceMd: 'tip',
      kpiJson: JSON.stringify({
        query_bank_flow: {
          source: 'opc_finance_bank_flow',
          companyId: 2,
          fromDate: '2026-08-10',
          toDate: '2026-09-08',
          count: 6,
          totalIn: 51892.0,
          totalOut: 32430.0,
          items: [],
        },
        query_voucher: {
          source: 'opc_finance_voucher',
          companyId: 2,
          fromDate: '2026-08-10',
          toDate: '2026-09-08',
          count: 5,
          totalIn: 35400.0,
          totalOut: 29750.0,
          items: [],
        },
      }),
      llmUsed: 'MiniMax-Text-01',
      reportStatus: 'FULL',
      reportSource: 'AGENT_WORKFLOW',
      createTime: '2026-09-08 09:00:00',
    },
  })
  // 后端 `DailyReportController.generate` 返回的是 `R<Void>` —— **没有 data、没有 reportId**。
  // 以前这里 mock 了 `{ reportId: 99 }` —— mock 自己编了个后端不返回的字段，
  // 于是「提示里显示一个不存在的 id」这件事测试一直是绿的。
  mocks.generateDaily.mockResolvedValue({ code: 200, msg: 'ok' })
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

// Task 9：日报的数字列 + 状态标。
// 全部在移动模式下跑（宽度 500）—— 桌面模式的单元格恒为 '-'，断言不了数字。
describe('daily.vue — 数字列 + 状态标（Task 9）', () => {
  beforeEach(() => {
    widthRef.value = 500
    mocks.listDaily.mockResolvedValue({ code: 200, msg: 'ok', data: makeRealRows() })
    mocks.getDaily.mockResolvedValue({ code: 200, msg: 'ok', data: makeRealRows()[0] })
  })

  it('13. 列表「收入」列显示数字员工日报的 totalIn', async () => {
    const w = mountDaily()
    await flushPromises()
    const vals = cardValues(w)
    expect(vals['收入']).toBeTruthy()
    expect(vals['收入'][0]).toContain('51,892.00')
    expect(vals['收入'][0]).not.toContain('0.00')
  })

  it('14. 列表「收入」列显示手工日报的 totalRevenue（两种 kpi 形状都认）', async () => {
    const w = mountDaily()
    await flushPromises()
    const vals = cardValues(w)
    expect(vals['收入'][1]).toContain('19,600.00')
  })

  it('15. 列表「流水笔数」列显示 6；手工日报没有流水数据就显示空值（不是 0）', async () => {
    const w = mountDaily()
    await flushPromises()
    const vals = cardValues(w)
    expect(vals['流水笔数'][0]).toBe('6')
    // 手工日报（KpiSnapshot 形状）里根本没有流水笔数 —— 显示空值，不显示 0
    expect(vals['流水笔数'][1]).toBe('-')
    expect(vals['流水笔数'][1]).not.toBe('0')
  })

  it('16. 列表「凭证数」列显示 5（来自 query_voucher.count）', async () => {
    const w = mountDaily()
    await flushPromises()
    const vals = cardValues(w)
    expect(vals['凭证数'][0]).toBe('5')
    expect(vals['凭证数'][1]).toBe('12') // 手工日报的 voucherCount
  })

  it('17. FALLBACK 的日报显示「未走 LLM」标记（行级：按所属期定位到那一行）', async () => {
    const w = mountDaily()
    await flushPromises()
    // 行级断言：断「状态」列单元格本身，不是整表文本
    // （整表文本里「未走 LLM」和「部分完成」都在，两个分支对调也照样绿）
    const cell = cellOf(w, '2026-10-31', '状态')
    expect(cell.text).toBe('未走 LLM')
    expect(cell.text).not.toContain('部分完成')
    expect(cell.text).not.toContain('完整')
  })

  it('18. 数字员工的日报显示「数字员工」来源标记（行级）', async () => {
    const w = mountDaily()
    await flushPromises()
    // 两种来源都要断 —— 只断一种的话，对调两个分支照样绿
    expect(cellOf(w, '2026-11-01', '来源').text).toBe('数字员工') // AGENT_WORKFLOW
    expect(cellOf(w, '2026-10-30', '来源').text).toBe('数字员工') // AGENT_WORKFLOW
    expect(cellOf(w, '2026-10-31', '来源').text).toBe('手工') // MANUAL
  })

  it('19. PARTIAL 的日报显示「部分完成」标记（行级）', async () => {
    const w = mountDaily()
    await flushPromises()
    const cell = cellOf(w, '2026-10-30', '状态')
    expect(cell.text).toBe('部分完成')
    expect(cell.text).not.toContain('未走 LLM')
    expect(cell.text).not.toContain('完整')
  })

  it('20. kpiJson 解析失败时页面不崩，数字列显示空值（不是 0.00）', async () => {
    mocks.listDaily.mockResolvedValue({
      code: 200,
      msg: 'ok',
      data: [
        {
          id: 21,
          period: '2026-11-01',
          kpiJson: 'not json',
          summaryMd: 'x',
          adviceMd: 'y',
          llmUsed: 'FALLBACK',
          reportStatus: 'FALLBACK',
          reportSource: 'MANUAL',
          createTime: '2026-11-01 09:00:00',
        },
      ],
    })
    const w = mountDaily()
    await flushPromises()
    // 页面还活着（没被异常打空）
    expect(w.find('[data-stub="ElCard"]').exists()).toBe(true)
    const vals = cardValues(w)
    expect(vals['收入'][0]).toBe('¥ -')
    expect(vals['支出'][0]).toBe('¥ -')
    expect(vals['流水笔数'][0]).toBe('-')
    expect(vals['凭证数'][0]).toBe('-')
    expect(w.text()).not.toContain('0.00')
  })

  it('21. 详情弹窗显示日报状态与来源（FALLBACK / MANUAL）', async () => {
    // 刻意用**兜底分支之外**的取值 —— 原来这里用的是 FULL/AGENT_WORKFLOW，
    // 正好等于兜底分支的文案 → 把 tag 写死成「完整/数字员工」测试照样全绿（没牙齿）。
    mocks.getDaily.mockResolvedValue({
      code: 200,
      msg: 'ok',
      data: makeDialogRow({ reportStatus: 'FALLBACK', reportSource: 'MANUAL' }),
    })
    const w = mountDaily()
    await flushPromises()
    const dialog = await openFirstDialog(w)
    const t = dialog.text()
    expect(t).toContain('未走 LLM')
    expect(t).toContain('手工')
    // 不许出现别的状态的文案
    expect(t).not.toContain('完整')
    expect(t).not.toContain('数字员工')
  })

  it('21b. 详情弹窗显示日报状态与来源（PARTIAL / 数字员工）', async () => {
    mocks.getDaily.mockResolvedValue({
      code: 200,
      msg: 'ok',
      data: makeDialogRow({ reportStatus: 'PARTIAL', reportSource: 'AGENT_WORKFLOW' }),
    })
    const w = mountDaily()
    await flushPromises()
    const dialog = await openFirstDialog(w)
    const t = dialog.text()
    expect(t).toContain('部分完成')
    expect(t).toContain('数字员工')
    expect(t).not.toContain('未走 LLM')
    expect(t).not.toContain('手工')
  })

  it('21c. 旧日报（状态/来源为 null）弹窗不自称「完整」/「手工」', async () => {
    // report_status / report_source 为 NULL 的旧日报 —— 系统根本不知道，
    // 界面不能替它说「完整」。这条是「兜底自称完整」的看门狗。
    mocks.getDaily.mockResolvedValue({
      code: 200,
      msg: 'ok',
      data: makeDialogRow({ reportStatus: null, reportSource: null }),
    })
    const w = mountDaily()
    await flushPromises()
    const dialog = await openFirstDialog(w)
    const t = dialog.text()
    expect(t).not.toContain('完整')
    expect(t).not.toContain('部分完成')
    expect(t).not.toContain('未走 LLM')
    expect(t).not.toContain('数字员工')
    expect(t).not.toContain('手工')
    // 弹窗正常打开（没崩）
    expect(w.find('[data-stub="ElDescriptions"]').exists()).toBe(true)
  })

  it('22. 弹窗的「取不到」不是 0.00（坏 kpiJson：不崩、也不说 0 元）', async () => {
    mocks.getDaily.mockResolvedValue({
      code: 200,
      msg: 'ok',
      data: makeDialogRow({
        kpiJson: 'not json',
        reportStatus: 'FALLBACK',
        reportSource: 'MANUAL',
      }),
    })
    const w = mountDaily()
    await flushPromises()
    const dialog = await openFirstDialog(w)
    const t = dialog.text()
    // 「0.00」是在说「收入是 0 元」，而真相是「这条日报没有这个数据」
    expect(t).not.toContain('0.00')
    expect(t).not.toContain('¥ 0')
    // 凭证数那一项同理：不是 0
    const voucherItem = w
      .findAll('[data-stub="ElDescriptionsItem"]')
      .find((i: any) => i.attributes('data-label') === '凭证数')
    expect(voucherItem).toBeTruthy()
    expect(voucherItem!.text()).not.toBe('0')
    expect(voucherItem!.text()).not.toMatch(/\d/)
  })

  it('23. 生成日报的提示不含 undefined（后端 generate 返回 R<Void>，没有 id）', async () => {
    const successSpy = vi.spyOn(ElMessage, 'success').mockImplementation((() => undefined) as any)
    try {
      const w = mountDaily()
      await flushPromises()
      const genBtn = w.findAll('button').find((b) => b.text().includes('生成今日日报'))
      expect(genBtn).toBeTruthy()
      await genBtn!.trigger('click')
      await flushPromises()
      expect(successSpy).toHaveBeenCalled()
      const msg = String(successSpy.mock.calls[0][0] ?? '')
      expect(msg).toContain('日报生成成功')
      // 后端没给 id，提示里就不该出现 id（以前恒显示「（ID=undefined）」）
      expect(msg).not.toContain('undefined')
    } finally {
      successSpy.mockRestore()
    }
  })

  it('26. FULL 的日报：列表状态列和弹窗都显示「完整」（正向断言）', async () => {
    // 必改 A：以前 `if (v === 'FULL') return '完整'` 这行**没有任何正向断言** ——
    // 把它改成「完完整」，25 条用例全绿。规格写着、断言不存在 = 规格是装饰。
    const w = mountDaily()
    await flushPromises()
    // 列表那一行的状态单元格
    const cell = cellOf(w, '2026-11-01', '状态')
    expect(cell.text).toBe('完整')
    // 要能分辨「完整」和其它两个 —— 防的是把 FULL 的行画成 PARTIAL/FALLBACK 的样子
    expect(cell.text).not.toContain('未走 LLM')
    expect(cell.text).not.toContain('部分完成')
    // 弹窗那一项也是「完整」
    mocks.getDaily.mockResolvedValue({
      code: 200,
      msg: 'ok',
      data: makeDialogRow({ reportStatus: 'FULL', reportSource: 'AGENT_WORKFLOW' }),
    })
    await openFirstDialog(w)
    const statusItem = w
      .findAll('[data-stub="ElDescriptionsItem"]')
      .find((i: any) => i.attributes('data-label') === '状态')
    expect(statusItem).toBeTruthy()
    expect(statusItem!.text()).toBe('完整')
    expect(statusItem!.text()).not.toContain('未走 LLM')
    expect(statusItem!.text()).not.toContain('部分完成')
  })

  it('27. 状态 tag 的颜色：FULL→success / PARTIAL→warning / FALLBACK→danger', async () => {
    const w = mountDaily()
    await flushPromises()
    // 列表里的 tag 是**真** ElTag（ResponsiveTable 直接 import 的，不是 stub）
    // → class 拿得到；弹窗里那个 <el-tag> 在 spec 里是 stub → 断不了 class，
    //   所以颜色断言主战场在列表这一侧。
    const full = cellOf(w, '2026-11-01', '状态').html
    expect(full).toContain('el-tag--success')
    expect(full).not.toContain('el-tag--warning')
    expect(full).not.toContain('el-tag--danger')
    const partial = cellOf(w, '2026-10-30', '状态').html
    expect(partial).toContain('el-tag--warning')
    expect(partial).not.toContain('el-tag--success')
    expect(partial).not.toContain('el-tag--danger')
    const fallback = cellOf(w, '2026-10-31', '状态').html
    expect(fallback).toContain('el-tag--danger')
    expect(fallback).not.toContain('el-tag--success')
    expect(fallback).not.toContain('el-tag--warning')

    // 弹窗那一侧也断一遍 —— 弹窗的 <el-tag> 是 stub，断不了 class，
    // 但 stub 把 type 透出成 data-type（见 element-plus-stubs.ts）。
    // 列表和弹窗的颜色必须一致：改了一处忘了另一处，
    // 列表绿、弹窗黄，谁都看不出来。
    for (const [status, type] of [
      ['FULL', 'success'],
      ['PARTIAL', 'warning'],
      ['FALLBACK', 'danger'],
    ] as const) {
      mocks.getDaily.mockResolvedValue({
        code: 200,
        msg: 'ok',
        data: makeDialogRow({ reportStatus: status, reportSource: 'AGENT_WORKFLOW' }),
      })
      const w2 = mountDaily()
      await flushPromises()
      await openFirstDialog(w2)
      const item = w2
        .findAll('[data-stub="ElDescriptionsItem"]')
        .find((i: any) => i.attributes('data-label') === '状态')
      expect(item, `弹窗里没找到「状态」这一项`).toBeTruthy()
      const tag = item!.find('[data-stub="ElTag"]')
      expect(tag, `弹窗的「状态」项里没有 tag`).toBeTruthy()
      expect(tag!.attributes('data-type'), `${status} 的 tag 颜色`).toBe(type)
      w2.unmount()
    }
  })
})
