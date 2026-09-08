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
        <el-descriptions-item label="所属期">{{ current?.period }}</el-descriptions-item>
        <el-descriptions-item label="凭证数">{{ current?.kpi?.voucherCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="收入合计">¥ {{ formatMoney(current?.kpi?.totalRevenue) }}</el-descriptions-item>
        <el-descriptions-item label="支出合计">¥ {{ formatMoney(current?.kpi?.totalExpense) }}</el-descriptions-item>
        <el-descriptions-item label="生成时间">{{ current?.createTime }}</el-descriptions-item>
        <el-descriptions-item label="生成人">{{ current?.createBy || '—' }}</el-descriptions-item>
      </el-descriptions>
      <h4 style="margin-top: 16px">摘要</h4>
      <div class="summary-box">
        <pre>{{ current?.summary || '（暂无）' }}</pre>
      </div>
      <h4>建议</h4>
      <div class="summary-box">
        <pre>{{ current?.advice || '（暂无）' }}</pre>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="InsightDaily">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listDaily, getDaily, generateDaily } from '@/api/opc/insight'
import ResponsiveTable, { type Column } from '@/views/opc/components/ResponsiveTable.vue'

const list = ref<any[]>([])
const loading = ref(false)
const generating = ref(false)
const range = ref<[string, string] | null>(null)
const show = ref(false)
const current = ref<any>(null)

const sortedList = computed(() =>
  [...list.value].sort((a, b) => String(b.period || '').localeCompare(String(a.period || '')))
)

const rangeHint = computed(() => {
  if (!range.value) return ''
  if (!range.value[0] || !range.value[1]) return '请选择完整的日期范围'
  return `查询范围：${range.value[0]} ~ ${range.value[1]}`
})

const columns: Column[] = [
  { key: 'period', label: '所属期', primary: true, width: 120 },
  { key: 'totalRevenue', label: '收入', type: 'amount', width: 140, align: 'right' },
  { key: 'totalExpense', label: '支出', type: 'amount', width: 140, align: 'right' },
  { key: 'voucherCount', label: '凭证数', type: 'number', width: 100, align: 'right' },
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
    list.value = r.data || []
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
    const r = await generateDaily(today)
    ElMessage.success(`日报生成成功（ID=${r.data?.reportId}）`)
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
    current.value = r.data
  } catch (e: any) {
    ElMessage.error(e?.msg || '加载日报详情失败')
    show.value = false
  }
}

function formatMoney(v: any): string {
  if (v === null || v === undefined || v === '') return '0.00'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
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
