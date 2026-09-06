<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="header">
          <span class="title">财务凭证</span>
          <div>
            <el-select v-model="companyId" placeholder="选择公司" style="width: 200px" @change="load">
              <el-option v-for="c in companies" :key="c.id" :label="c.companyName" :value="c.id" />
            </el-select>
            <el-select v-model="status" placeholder="状态" clearable style="width: 140px; margin-left: 8px" @change="load">
              <el-option label="草稿" value="DRAFT" />
              <el-option label="待审核" value="REVIEW" />
              <el-option label="已入账" value="POSTED" />
              <el-option label="已拒绝" value="REJECTED" />
            </el-select>
            <el-button type="primary" :icon="Plus" style="margin-left: 8px" @click="extractFlows">AI 提取流水</el-button>
          </div>
        </div>
      </template>

      <ResponsiveTable
        :data="list"
        :loading="loading"
        :columns="voucherColumns"
        :action-width="260"
        empty-text="暂无凭证"
      >
        <template #actions="{ row }">
          <el-button size="small" @click="view(row)">查看</el-button>
          <el-button size="small" type="success" v-if="row.status === 'DRAFT'" @click="pass(row)">通过</el-button>
          <el-button size="small" type="warning" v-if="row.status === 'REVIEW'" @click="post(row)">入账</el-button>
          <el-button size="small" type="danger" v-if="row.status === 'DRAFT'" @click="reject(row)">拒绝</el-button>
        </template>
      </ResponsiveTable>
    </el-card>

    <el-dialog v-model="show" :title="`凭证详情 - ${current?.voucherCode}`" width="720px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="日期">{{ current?.voucherDate }}</el-descriptions-item>
        <el-descriptions-item label="期间">{{ current?.period }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ current?.sourceType }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="statusType(current?.status)">{{ statusLabel(current?.status) }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="摘要" :span="2">{{ current?.summary }}</el-descriptions-item>
      </el-descriptions>

      <h4 style="margin-top: 16px">分录</h4>
      <pre style="background: #f5f5f5; padding: 12px; border-radius: 4px;">{{ entriesPretty }}</pre>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="FinanceVouchers">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { listVouchers, reviewPass, reviewReject, postVoucher, extractFlows as doExtract, generateDailyReport } from '@/api/opc/finance'
import { listMyCompanies } from '@/api/opc/user'
import ResponsiveTable, { type Column } from '@/views/opc/components/ResponsiveTable.vue'

const list = ref<any[]>([])
const companies = ref<any[]>([])
const companyId = ref<number>()
const status = ref<string>('')
const loading = ref(false)
const show = ref(false)
const current = ref<any>({})

const voucherColumns: Column[] = [
  { key: 'voucherCode', label: '编号', primary: true, width: 160 },
  { key: 'voucherDate', label: '日期', type: 'date', width: 120 },
  { key: 'period', label: '期间', width: 100 },
  { key: 'summary', label: '摘要' },
  { key: 'totalDebit', label: '借方', type: 'amount', width: 120, align: 'right' },
  { key: 'totalCredit', label: '贷方', type: 'amount', width: 120, align: 'right' },
  {
    key: 'status',
    label: '状态',
    width: 100,
    type: 'tag',
    tagMap: { DRAFT: 'info', REVIEW: 'warning', POSTED: 'success', REJECTED: 'danger' },
  },
]

const entriesPretty = computed(() => {
  if (!current.value?.entriesJson) return ''
  try { return JSON.stringify(JSON.parse(current.value.entriesJson), null, 2) } catch { return current.value.entriesJson }
})

function statusType(s: string) {
  return { DRAFT: 'info', REVIEW: 'warning', POSTED: 'success', REJECTED: 'danger' }[s] || ''
}
function statusLabel(s: string) {
  return { DRAFT: '草稿', REVIEW: '待审核', POSTED: '已入账', REJECTED: '已拒绝' }[s] || s
}

async function load() {
  if (!companyId.value) return
  loading.value = true
  try {
    const r = await listVouchers(companyId.value, undefined, status.value || undefined)
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

function view(row: any) { current.value = row; show.value = true }
async function pass(row: any) {
  await reviewPass(row.id)
  ElMessage.success('已通过')
  load()
}
async function reject(row: any) {
  await ElMessageBox.confirm('确认拒绝该凭证？', '提示')
  await reviewReject(row.id, '人工拒绝')
  ElMessage.success('已拒绝')
  load()
}
async function post(row: any) {
  await ElMessageBox.confirm('确认入账？', '提示', { type: 'warning' })
  await postVoucher(row.id)
  ElMessage.success('已入账')
  load()
}
async function extractFlows() {
  if (!companyId.value) return
  const r = await doExtract(companyId.value)
  ElMessage.success('已提交 AI 提取任务：' + (r.data?.taskCode ?? ''))
}

onMounted(loadCompanies)
</script>

<style scoped>
.header { display: flex; justify-content: space-between; align-items: center; }
.title { font-size: 18px; font-weight: 600; }
</style>
