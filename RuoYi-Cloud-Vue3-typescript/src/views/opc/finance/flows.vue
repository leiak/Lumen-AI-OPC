<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="header">
          <span>银行流水</span>
          <div>
            <el-select v-model="companyId" placeholder="选择公司" style="width: 200px" @change="load">
              <el-option v-for="c in companies" :key="c.id" :label="c.companyName" :value="c.id" />
            </el-select>
            <el-button type="primary" @click="showUpload = true" style="margin-left: 8px">导入流水</el-button>
            <el-button type="success" @click="extractAll">AI 提取所有</el-button>
          </div>
        </div>
      </template>

      <ResponsiveTable :data="list" :loading="loading" :columns="flowColumns" empty-text="暂无流水" />
    </el-card>

    <el-dialog v-model="showUpload" title="导入流水（粘贴文本）" width="640px">
      <el-input v-model="rawText" type="textarea" :rows="10" placeholder="每行一条流水，格式示例：&#10;2026-09-03 10:23 支付宝收款 客户A有限公司 转账 12680.00 元 备注 货款" />
      <template #footer>
        <el-button @click="showUpload = false">取消</el-button>
        <el-button type="primary" @click="parseAndUpload">解析并导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="FinanceFlows">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { pendingFlows, uploadFlows, extractFlows as doExtract } from '@/api/opc/finance'
import { listMyCompanies } from '@/api/opc/user'
import ResponsiveTable, { type Column } from '@/views/opc/components/ResponsiveTable.vue'

const list = ref<any[]>([])
const companies = ref<any[]>([])
const companyId = ref<number>()
const loading = ref(false)
const showUpload = ref(false)
const rawText = ref('')

const flowColumns: Column[] = [
  { key: 'flowCode', label: '编号', primary: true, width: 160 },
  { key: 'tradeTime', label: '交易时间', type: 'date', width: 180 },
  {
    key: 'direction',
    label: '方向',
    width: 80,
    type: 'tag',
    formatter: (v) => (String(v) === 'IN' ? '收入' : '支出'),
    tagMap: { IN: 'success', OUT: 'danger' },
  },
  { key: 'amount', label: '金额', type: 'amount', width: 120, align: 'right' },
  { key: 'counterParty', label: '对手方' },
  { key: 'memo', label: '备注' },
  {
    key: 'status',
    label: '状态',
    width: 100,
    type: 'tag',
    formatter: (v) =>
      ({ PENDING: '待核对', CONFIRMED: '已确认', REJECTED: '已拒绝' } as any)[String(v)] || String(v || '-'),
    tagMap: { PENDING: 'warning', CONFIRMED: 'success', REJECTED: 'danger' },
  },
]

async function load() {
  if (!companyId.value) return
  loading.value = true
  try {
    const r = await pendingFlows(companyId.value, 50)
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

function parseLine(line: string) {
  const m = line.match(/(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2})?\s*(.*?)\s+([\d.]+)/)
  if (!m) return null
  return {
    tradeTime: m[1] + ' ' + (m[2] ?? '00:00'),
    amount: m[4],
    counterParty: m[3],
    memo: line,
    rawText: line
  }
}

async function parseAndUpload() {
  if (!companyId.value) return
  const lines = rawText.value.split('\n').filter(l => l.trim())
  const flows = lines.map(l => parseLine(l)).filter(Boolean).map((f: any) => ({
    ...f,
    companyId: companyId.value,
    bankAccount: '-',
    bankName: '手动',
    direction: 'IN',
    currency: 'CNY',
    flowCode: 'F' + System.currentTime?.() ?? Date.now()
  }))
  await uploadFlows(flows)
  ElMessage.success(`导入 ${flows.length} 条`)
  showUpload.value = false
  load()
}

async function extractAll() {
  if (!companyId.value) return
  await doExtract(companyId.value)
  ElMessage.success('已提交 AI 提取任务')
}

onMounted(loadCompanies)
</script>

<style scoped>
.header { display: flex; justify-content: space-between; align-items: center; }
</style>
