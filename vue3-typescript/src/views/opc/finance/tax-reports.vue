<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="header">
          <span class="title">月度税务报表</span>
          <div class="header-actions">
            <el-select v-model="companyId" placeholder="选择公司" style="width: 200px" @change="load">
              <el-option v-for="c in companies" :key="c.id" :label="c.companyName" :value="c.id" />
            </el-select>
            <el-select v-model="status" placeholder="状态" clearable style="width: 140px; margin-left: 8px" @change="load">
              <el-option label="草稿" value="DRAFT" />
              <el-option label="已提交" value="SUBMITTED" />
              <el-option label="已缴税" value="PAID" />
            </el-select>
            <el-date-picker
              v-model="periodModel"
              type="month"
              placeholder="所属期"
              format="YYYY-MM"
              value-format="YYYY-MM"
              style="width: 160px; margin-left: 8px"
              @change="load"
            />
            <el-button type="primary" :loading="generating" style="margin-left: 8px" @click="onGenerate">
              <el-icon><Document /></el-icon>
              生成当月报表
            </el-button>
          </div>
        </div>
      </template>

      <ResponsiveTable
        :data="list"
        :loading="loading"
        :columns="reportColumns"
        :action-width="120"
        empty-text="本月还没有税务报表，点右上角'生成当月报表'试试"
      >
        <template #actions="{ row }">
          <el-button size="small" @click="view(row)">查看</el-button>
        </template>
      </ResponsiveTable>
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog v-model="show" :title="`税务报表 - ${current?.reportCode || ''}`" width="640px">
      <el-descriptions v-if="current" :column="2" border>
        <el-descriptions-item label="申报编号">{{ current?.reportCode }}</el-descriptions-item>
        <el-descriptions-item label="所属期">{{ current?.period }}</el-descriptions-item>
        <el-descriptions-item label="税种">{{ taxTypeLabel(current?.taxType) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(current?.status)">{{ statusLabel(current?.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="应税销售额">¥ {{ formatMoney(current?.taxableAmount) }}</el-descriptions-item>
        <el-descriptions-item label="应纳税额">¥ {{ formatMoney(current?.taxAmount) }}</el-descriptions-item>
        <el-descriptions-item label="应缴">¥ {{ formatMoney(current?.payAmount) }}</el-descriptions-item>
        <el-descriptions-item label="已缴">¥ {{ formatMoney(current?.paidAmount) }}</el-descriptions-item>
        <el-descriptions-item label="申报截止">{{ current?.dueDate }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ current?.submitTime || '—' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ current?.createTime }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ current?.createBy || '—' }}</el-descriptions-item>
      </el-descriptions>

      <h4 style="margin-top: 16px">报税建议（来自 LLM）</h4>
      <div class="advice-box">
        <pre>{{ current?.attachments || '（暂无）' }}</pre>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="FinanceTaxReports">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Document } from '@element-plus/icons-vue'
import { generateTaxReport, listTaxReports, taxReportDetail } from '@/api/opc/finance'
import { listMyCompanies } from '@/api/opc/user'
import ResponsiveTable, { type Column } from '@/views/opc/components/ResponsiveTable.vue'

const list = ref<any[]>([])
const companies = ref<any[]>([])
const companyId = ref<number>()
const status = ref<string>('')
const loading = ref(false)
const generating = ref(false)
const show = ref(false)
const current = ref<any>(null)

/** 月份选择器 v-model：默认当月 */
const periodModel = ref<string>(currentYYYYMM())

/** 期间传字符串 YYYY-MM 给后端 */
const period = computed(() => periodModel.value || '')

const reportColumns: Column[] = [
  { key: 'reportCode', label: '申报编号', primary: true, width: 180 },
  { key: 'period', label: '所属期', width: 100 },
  { key: 'taxType', label: '税种', width: 100, formatter: (v) => taxTypeLabel(String(v)) },
  { key: 'taxableAmount', label: '应税销售额', width: 140, align: 'right', type: 'amount' },
  { key: 'taxAmount', label: '应纳税额', width: 120, align: 'right', type: 'amount' },
  { key: 'payAmount', label: '应缴', width: 120, align: 'right', type: 'amount' },
  { key: 'paidAmount', label: '已缴', width: 120, align: 'right', type: 'amount' },
  { key: 'dueDate', label: '截止日', type: 'date', width: 120 },
  {
    key: 'status',
    label: '状态',
    width: 100,
    type: 'tag',
    tagMap: { DRAFT: 'info', SUBMITTED: 'warning', PAID: 'success' },
    formatter: (v) => statusLabel(String(v)),
  },
  { key: 'createTime', label: '生成时间', type: 'date', width: 160, hideOnMobile: true },
]

async function load() {
  if (!companyId.value) return
  loading.value = true
  try {
    const r = await listTaxReports(companyId.value, period.value || undefined, status.value || undefined)
    list.value = r.data || []
  } finally {
    loading.value = false
  }
}

async function loadCompanies() {
  const r = await listMyCompanies()
  companies.value = r.data || []
  if (companies.value.length) companyId.value = companies.value[0].id
  load()
}

async function onGenerate() {
  if (!companyId.value) {
    ElMessage.warning('请先选择公司')
    return
  }
  if (!period.value) {
    ElMessage.warning('请选择所属期')
    return
  }
  generating.value = true
  try {
    const r = await generateTaxReport(companyId.value, period.value)
    const reportId = r.data?.reportId
    ElMessage.success(`报表生成成功（ID=${reportId}）`)
    // AC 要求 5s 内看到新记录 — 直接拉一次详情兜底
    if (reportId) {
      const detail = await taxReportDetail(reportId)
      current.value = detail.data
    }
    load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '生成失败')
  } finally {
    generating.value = false
  }
}

async function view(row: any) {
  const r = await taxReportDetail(row.id)
  current.value = r.data
  show.value = true
}

function taxTypeLabel(s?: string) {
  return ({ VAT: '增值税', CIT: '企业所得税', SD: '印花税' } as any)[String(s)] || s || '-'
}

function statusTagType(s?: string) {
  return ({ DRAFT: 'info', SUBMITTED: 'warning', PAID: 'success' } as any)[String(s)] || ''
}

function statusLabel(s?: string) {
  return ({ DRAFT: '草稿', SUBMITTED: '已提交', PAID: '已缴税' } as any)[String(s)] || s || '-'
}

function formatMoney(v?: number | string) {
  if (v === null || v === undefined || v === '') return '0.00'
  const num = typeof v === 'number' ? v : Number(v)
  if (Number.isNaN(num)) return String(v)
  return num.toLocaleString('zh', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 返回当前 YYYY-MM (本地时区) */
function currentYYYYMM(): string {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  return `${y}-${m}`
}

onMounted(loadCompanies)
</script>

<style scoped lang="scss">
.header { display: flex; justify-content: space-between; align-items: center; }
.header-actions { display: flex; align-items: center; flex-wrap: wrap; }
.title { font-size: 18px; font-weight: 600; }

.advice-box {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 12px;
  max-height: 280px;
  overflow-y: auto;
  border: 1px solid #ebeef5;

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
</style>