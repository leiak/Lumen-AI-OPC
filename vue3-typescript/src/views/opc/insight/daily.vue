<template>
  <div class="page insight-daily">
    <h2>财务日报</h2>

    <!-- 工具行 -->
    <el-row :gutter="12" class="toolbar">
      <el-col :xs="24" :sm="10">
        <el-date-picker
          v-model="range"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          @change="load"
          style="width: 100%"
        />
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-button type="primary" :loading="generating" @click="onGenerate" style="width: 100%">
          生成今日日报
        </el-button>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-button :loading="loading" @click="load" style="width: 100%">刷新</el-button>
      </el-col>
    </el-row>

    <p v-if="rangeHint" class="range-hint">{{ rangeHint }}</p>

    <!-- 列表 -->
    <ResponsiveTable
      :data="sortedList"
      :loading="loading"
      :columns="columns"
      :action-width="120"
      empty-text="该日期范围内还没有日报，点右上角'生成今日日报'试试"
    >
      <template #actions="{ row }">
        <el-button size="small" @click="view(row)">查看</el-button>
      </template>
    </ResponsiveTable>

    <!-- 详情弹窗 -->
    <el-dialog v-model="show" :title="`财务日报 - ${current?.period || ''}`" width="640px">
      <el-descriptions v-if="current" :column="2" border>
        <el-descriptions-item label="状态">
          <!-- 未知取值（report_status 为 NULL 的旧数据）**不出 tag** ——
               兜底自称「完整」是界面替系统说它并不知道的事 -->
          <el-tag
            v-if="statusTextOf(current?.reportStatus)"
            :type="statusTypeOf(current?.reportStatus)"
            size="small"
          >{{ statusTextOf(current?.reportStatus) }}</el-tag>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="来源">
          <el-tag v-if="sourceTextOf(current?.reportSource)" type="info" size="small">
            {{ sourceTextOf(current?.reportSource) }}
          </el-tag>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="所属期">{{ current?.period }}</el-descriptions-item>
        <el-descriptions-item label="凭证数">{{ voucherCountOf(current) ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="收入合计">{{ formatMoney(revenueOf(current)) }}</el-descriptions-item>
        <el-descriptions-item label="支出合计">{{ formatMoney(expenseOf(current)) }}</el-descriptions-item>
        <el-descriptions-item v-if="intervalOf(current)" label="统计区间">
          {{ intervalOf(current) }}
        </el-descriptions-item>
        <el-descriptions-item label="生成时间">{{ current?.createTime }}</el-descriptions-item>
      </el-descriptions>
      <h4 style="margin-top: 16px">摘要</h4>
      <div class="summary-box">
        <pre>{{ current?.summaryMd || '（暂无）' }}</pre>
      </div>
      <h4>建议</h4>
      <div class="summary-box">
        <pre>{{ current?.adviceMd || '（暂无）' }}</pre>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="InsightDaily">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listDaily, getDaily, generateDaily } from '@/api/opc/insight'
import type { DailyReportVO } from '@/api/opc/insight'
import ResponsiveTable, { type Column } from '@/views/opc/components/ResponsiveTable.vue'

/** 列表行 = 后端返回的日报 + 前端从 kpiJson 里解析出来的四个数字 */
type DailyRow = DailyReportVO & {
  revenue: number | null
  expense: number | null
  flowCount: number | null
  voucherCount: number | null
}

const list = ref<DailyRow[]>([])
const loading = ref(false)
const generating = ref(false)
const range = ref<[string, string] | null>(null)
const show = ref(false)
const current = ref<DailyReportVO | null>(null)

const sortedList = computed(() =>
  [...list.value].sort((a, b) => String(b.period || '').localeCompare(String(a.period || '')))
)

const rangeHint = computed(() => {
  if (!range.value) return ''
  if (!range.value[0] || !range.value[1]) return '请选择完整的日期范围'
  return `查询范围：${range.value[0]} ~ ${range.value[1]}`
})

/**
 * 日报的数字**不在顶层字段上** —— 后端 DailyReportVo 只有 kpiJson 这个
 * JSON 字符串。以前这里写的是 row.totalRevenue / row.totalExpense /
 * row.voucherCount，那三个字段后端从来不返回 → 三列永远是空的。
 *
 * kpiJson 有两种形状，都得认：
 * - 数字员工日报（AGENT_WORKFLOW）：
 *   {query_bank_flow:{totalIn,totalOut,count,fromDate,toDate}, query_voucher:{count}}
 * - insight 自己生成的日报（KpiSnapshot）：{totalRevenue,totalExpense,voucherCount}
 */
function parseKpi(raw?: any): any | null {
  if (!raw) return null
  // 万一哪天后端改成直接给对象，也别把它当字符串解析失败丢掉
  if (typeof raw === 'object') return raw
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

function kpiOf(row: any): any {
  return parseKpi(row?.kpiJson) || {}
}

/** 取不到就返回 null —— 不返回 0。0 会被渲染成「0.00」，那是在说「收入是 0 元」 */
function numOrNull(v: any): number | null {
  if (v === undefined || v === null || v === '') return null
  const n = Number(v)
  return Number.isNaN(n) ? null : n
}

/** 收入：数字员工日报写 totalIn，insight 自己的日报写 totalRevenue */
function revenueOf(row: any): number | null {
  const k = kpiOf(row)
  return numOrNull(k?.query_bank_flow?.totalIn ?? k?.totalRevenue)
}

/** 支出：数字员工日报写 totalOut，insight 自己的日报写 totalExpense */
function expenseOf(row: any): number | null {
  const k = kpiOf(row)
  return numOrNull(k?.query_bank_flow?.totalOut ?? k?.totalExpense)
}

/** 流水笔数 —— 只有数字员工的日报有 */
function flowCountOf(row: any): number | null {
  return numOrNull(kpiOf(row)?.query_bank_flow?.count)
}

/** 凭证数 —— 数字员工日报取 query_voucher.count，insight 自己取 voucherCount */
function voucherCountOf(row: any): number | null {
  const k = kpiOf(row)
  return numOrNull(k?.query_voucher?.count ?? k?.voucherCount)
}

/** 数字员工的日报带真实区间；手工的不带 */
function intervalOf(row: any): string {
  const k = kpiOf(row)?.query_bank_flow
  return k?.fromDate && k?.toDate ? `${k.fromDate} ~ ${k.toDate}` : ''
}

/**
 * 状态取值 → 显示文案。
 *
 * <p>**未知取值（null / 空串 / 没见过的新取值）返回 null** ——
 * `report_status` 为 NULL 的旧日报（Task 4 之前写的行）根本不知道完整不完整，
 * 界面不能替它说「完整」。那跟「卡片上 823 次浏览」是同一类病。</p>
 */
function statusTextOf(v: any): string | null {
  if (v === 'FULL') return '完整'
  if (v === 'PARTIAL') return '部分完成'
  if (v === 'FALLBACK') return '未走 LLM'
  return null
}

/** 状态取值 → tag 颜色（只对已知的三个取值有意义；未知取值不会走到这里） */
function statusTypeOf(v: any): 'success' | 'warning' | 'danger' | 'info' {
  if (v === 'FULL') return 'success'
  if (v === 'PARTIAL') return 'warning'
  if (v === 'FALLBACK') return 'danger'
  return 'info'
}

/** 来源取值 → 显示文案；未知取值返回 null（理由同 statusTextOf） */
function sourceTextOf(v: any): string | null {
  if (v === 'AGENT_WORKFLOW') return '数字员工'
  if (v === 'MANUAL') return '手工'
  return null
}

const columns: Column[] = [
  { key: 'period', label: '所属期', primary: true, width: 120 },
  { key: 'revenue', label: '收入', type: 'amount', width: 140, align: 'right' },
  { key: 'expense', label: '支出', type: 'amount', width: 140, align: 'right' },
  { key: 'flowCount', label: '流水笔数', type: 'number', width: 100, align: 'right' },
  { key: 'voucherCount', label: '凭证数', type: 'number', width: 100, align: 'right' },
  {
    // 状态与来源拆成两列：ResponsiveTable 一个单元格只渲染一个 tag，
    // 一列放两个 tag 就得改这个几十个页面在用的共享组件 —— 不值得。
    // 文案跟弹窗共用 statusTextOf / sourceTextOf —— 同一个字段，
    // 列表和弹窗不许一个说「不知道」、另一个说「完整」。
    key: 'reportStatus',
    label: '状态',
    type: 'tag',
    width: 110,
    tagMap: { FULL: 'success', PARTIAL: 'warning', FALLBACK: 'danger' },
    formatter: (v: any) => statusTextOf(v) ?? '-',
  },
  {
    key: 'reportSource',
    label: '来源',
    type: 'tag',
    width: 110,
    tagMap: { AGENT_WORKFLOW: 'info', MANUAL: 'info' },
    formatter: (v: any) => sourceTextOf(v) ?? '-',
  },
  { key: 'createTime', label: '生成时间', type: 'date', width: 160, hideOnMobile: true },
]

async function load() {
  if (!range.value || !range.value[0] || !range.value[1]) {
    list.value = []
    return
  }
  loading.value = true
  try {
    const r = await listDaily(range.value[0], range.value[1], 50)
    // ResponsiveTable 的 amount/number 列取的是 row 上的同名 key，
    // 所以不能直接把后端返回的原始对象丢进去 —— 先把数字解析出来挂上去。
    list.value = (r.data || []).map((row: DailyReportVO) => ({
      ...row,
      revenue: revenueOf(row),
      expense: expenseOf(row),
      flowCount: flowCountOf(row),
      voucherCount: voucherCountOf(row),
    }))
  } catch (e: any) {
    ElMessage.error(e?.msg || '加载日报列表失败')
    list.value = []
  } finally {
    loading.value = false
  }
}

async function onGenerate() {
  const today = new Date().toISOString().slice(0, 10)
  generating.value = true
  try {
    // 后端 `DailyReportController.generate` 返回的是 `R<Void>` —— **没有日报 id**。
    // 以前这里写 `（ID=${r.data?.reportId}）` → 提示恒显示「（ID=undefined）」，
    // 界面在报一个后端根本没给的东西。后端没给 id，提示里就别提 id。
    await generateDaily(today)
    ElMessage.success('日报生成成功')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '生成日报失败')
  } finally {
    generating.value = false
  }
}

async function view(row: any) {
  show.value = true
  current.value = null
  try {
    const r = await getDaily(row.id)
    current.value = r.data ?? null
  } catch (e: any) {
    ElMessage.error(e?.msg || '加载日报详情失败')
    show.value = false
  }
}

/**
 * 金额格式化。**取不到就显示 '—'，不是 '0.00'** ——
 * '0.00' 是在说「收入是 0 元」，而真相是「这条日报没有这个数据」。
 */
function formatMoney(v: any): string {
  if (v === null || v === undefined || v === '') return '—'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  return '¥ ' + n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

onMounted(() => {
  // 默认加载最近 30 天
  const today = new Date()
  const start = new Date(today)
  start.setDate(start.getDate() - 30)
  range.value = [
    start.toISOString().slice(0, 10),
    today.toISOString().slice(0, 10),
  ]
  load()
})
</script>

<style scoped lang="scss">
.insight-daily { display: flex; flex-direction: column; gap: 12px; }
h2 { margin: 0 0 8px; font-size: 20px; font-weight: 600; }
.toolbar { margin-bottom: 12px; }
.range-hint {
  color: #909399;
  font-size: 13px;
  margin: -4px 0 8px;
}
.summary-box {
  background: #f5f7fa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px;
  max-height: 220px;
  overflow-y: auto;
  pre {
    margin: 0;
    white-space: pre-wrap;
    word-break: break-word;
    font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', sans-serif;
    font-size: 13px;
    line-height: 1.7;
    color: #303133;
  }
}
h4 { margin: 16px 0 8px; font-size: 14px; font-weight: 600; }
</style>
