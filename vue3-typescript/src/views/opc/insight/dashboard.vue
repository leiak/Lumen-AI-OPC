<template>
  <div class="page insight-dashboard">
    <div class="page-header">
      <h2>经营驾驶舱</h2>
      <div class="header-actions">
        <el-tag v-if="partial" type="warning" effect="plain" class="partial-badge">
          数据降级
        </el-tag>
        <el-button :loading="loading" size="small" @click="refresh">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <!-- KPI 卡片 -->
    <el-row :gutter="16" class="is-mobile-stack" v-loading="loading && !kpiCards.length">
      <el-col v-for="kpi in kpiCards" :key="kpi.label" :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-label">{{ kpi.label }}</div>
          <div class="kpi-value" :data-testid="'kpi-' + kpi.testid">
            {{ kpi.value }}
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 趋势图表占位 -->
    <el-row :gutter="16" class="trend-row">
      <el-col :span="12">
        <el-card>
          <template #header>
            <span>收入趋势</span>
          </template>
          <div class="chart-placeholder" data-testid="trend-revenue">暂无趋势数据</div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>
            <span>支出趋势</span>
          </template>
          <div class="chart-placeholder" data-testid="trend-expense">暂无趋势数据</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 异常 Top 5 -->
    <el-card class="section-card">
      <template #header>
        <span>异常 Top 5</span>
      </template>
      <div v-if="!alertList.length" class="empty">暂无异常</div>
      <ul v-else class="alert-list" data-testid="alert-list">
        <li v-for="a in alertList" :key="a.id" class="alert-item">
          <el-tag size="small" :type="levelTagType(a.level)">{{ a.level }}</el-tag>
          <span class="alert-title">{{ a.title }}</span>
          <span class="alert-desc">{{ a.description }}</span>
        </li>
      </ul>
    </el-card>

    <!-- 建议 Top 3 -->
    <el-card class="section-card">
      <template #header>
        <span>建议 Top 3</span>
      </template>
      <div v-if="!adviceList.length" class="empty">暂无建议</div>
      <ul v-else class="advice-list" data-testid="advice-list">
        <li v-for="a in adviceList" :key="a.id" class="advice-item">
          <el-tag size="small" type="info">{{ a.topic }}</el-tag>
          <span class="advice-summary">{{ a.summary }}</span>
        </li>
      </ul>
    </el-card>
  </div>
</template>

<script setup lang="ts" name="InsightDashboard">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { dashboard } from '@/api/opc/insight'

interface KpiSnapshot {
  totalRevenue?: number | string
  totalExpense?: number | string
  voucherCount?: number
  pendingVoucherCount?: number
  walletBalance?: number | string
  tokenUsage?: number
  partial?: boolean
}

const loading = ref(false)
const kpi = ref<KpiSnapshot | null>(null)
const alertList = ref<any[]>([])
const adviceList = ref<any[]>([])

const partial = computed(() => !!kpi.value?.partial)

/** KPI 卡片视图模型 */
const kpiCards = computed(() => [
  { testid: 'revenue', label: '收入合计', value: formatMoney(kpi.value?.totalRevenue) },
  { testid: 'expense', label: '支出合计', value: formatMoney(kpi.value?.totalExpense) },
  { testid: 'voucher', label: '凭证数', value: formatNumber(kpi.value?.voucherCount) },
  { testid: 'pending', label: '待审凭证', value: formatNumber(kpi.value?.pendingVoucherCount) },
  { testid: 'wallet', label: '钱包余额', value: formatMoney(kpi.value?.walletBalance) },
  { testid: 'tokens', label: '本月 Token', value: formatNumber(kpi.value?.tokenUsage) },
])

async function load() {
  loading.value = true
  try {
    const r = await dashboard()
    const data = r.data || {}
    kpi.value = data.kpi || {}
    alertList.value = data.alerts || []
    adviceList.value = data.advice || []
  } catch (e: any) {
    ElMessage.error(e?.msg || '加载驾驶舱失败')
    kpi.value = null
    alertList.value = []
    adviceList.value = []
  } finally {
    loading.value = false
  }
}

function refresh() {
  load()
}

function levelTagType(level?: string): '' | 'success' | 'warning' | 'danger' {
  if (level === 'HIGH') return 'danger'
  if (level === 'MEDIUM') return 'warning'
  if (level === 'LOW') return 'success'
  return ''
}

function formatNumber(v: any): string {
  if (v === null || v === undefined || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  return n.toLocaleString('zh-CN')
}

function formatMoney(v: any): string {
  if (v === null || v === undefined || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  return '¥ ' + n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

onMounted(load)
</script>

<style scoped lang="scss">
.insight-dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  h2 { margin: 0; font-size: 20px; font-weight: 600; }
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.partial-badge { font-weight: 500; }
.kpi-card {
  .kpi-label { color: #909399; font-size: 13px; margin-bottom: 8px; }
  .kpi-value { font-size: 22px; font-weight: 600; color: #303133; }
}
.trend-row { margin-top: 0; }
.chart-placeholder {
  height: 220px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  background: #f5f7fa;
  border-radius: 4px;
}
.section-card { margin-bottom: 0; }
.alert-list, .advice-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.alert-item, .advice-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  font-size: 14px;
  flex-wrap: wrap;
}
.alert-title { font-weight: 500; color: #303133; }
.alert-desc { color: #909399; font-size: 13px; }
.advice-summary { color: #303133; }
.empty {
  padding: 24px;
  text-align: center;
  color: #909399;
  font-size: 14px;
}
</style>
