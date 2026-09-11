<template>
  <div class="page hr-dashboard" v-loading="loading">
    <el-row :gutter="16" class="hero">
      <el-col :xs="24" :md="6">
        <el-card shadow="never" class="hero-card hero-card--primary">
          <div class="hero-num">{{ stats.totalJobs }}</div>
          <div class="hero-label">招聘需求</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="6">
        <el-card shadow="never" class="hero-card hero-card--success">
          <div class="hero-num">{{ stats.openJobs }}</div>
          <div class="hero-label">招聘中</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="6">
        <el-card shadow="never" class="hero-card hero-card--warning">
          <div class="hero-num">{{ stats.totalApps }}</div>
          <div class="hero-label">总投递</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="6">
        <el-card shadow="never" class="hero-card hero-card--info">
          <div class="hero-num">{{ stats.hired }}</div>
          <div class="hero-label">已入职</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="block">
      <el-col :xs="24" :md="14">
        <el-card>
          <template #header><span class="card-title">招聘漏斗</span></template>
          <el-table :data="funnelRows" :show-header="false">
            <el-table-column prop="status" label="阶段" width="120">
              <template #default="{ row }">
                <el-tag :type="row.color" effect="plain">{{ row.label }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="数量">
              <template #default="{ row }">
                <div class="bar-row">
                  <div class="bar-track">
                    <div class="bar-fill" :style="{ width: row.percent + '%', background: row.colorHex }"></div>
                  </div>
                  <span class="bar-num">{{ row.count }}</span>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="10">
        <el-card>
          <template #header><span class="card-title">转化率</span></template>
          <div v-if="!conversionRows.length" class="empty-tip">暂无数据</div>
          <div v-else class="rate-list">
            <div v-for="row in conversionRows" :key="row.key" class="rate-row">
              <div class="rate-label">{{ row.label }}</div>
              <div class="rate-track">
                <div class="rate-fill" :style="{ width: row.percent + '%' }"></div>
              </div>
              <div class="rate-value">{{ row.percent.toFixed(1) }}%</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="block">
      <el-col :xs="24" :md="14">
        <el-card>
          <template #header><span class="card-title">JD 状态分布</span></template>
          <ResponsiveTable
            :data="jobDistributionRows"
            :columns="jobColumns"
            empty-text="暂无 JD"
          />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="10">
        <el-card>
          <template #header><span class="card-title">招聘效率</span></template>
          <div class="metric-list">
            <div class="metric-row">
              <span class="metric-label">平均招聘时长</span>
              <span class="metric-value">
                {{ data?.avgHireDays != null ? data.avgHireDays.toFixed(1) + ' 天' : '暂无数据' }}
              </span>
            </div>
            <div class="metric-row">
              <span class="metric-label">近 30 天新投递</span>
              <span class="metric-value">{{ funnelCount('NEW') }}</span>
            </div>
            <div class="metric-row">
              <span class="metric-label">进入面试</span>
              <span class="metric-value">{{ funnelCount('INTERVIEW') }}</span>
            </div>
            <div class="metric-row">
              <span class="metric-label">发出 Offer</span>
              <span class="metric-value">{{ funnelCount('OFFER') }}</span>
            </div>
            <div class="metric-row">
              <span class="metric-label">成功入职</span>
              <span class="metric-value">{{ funnelCount('HIRED') }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getHrDashboard, type OpcHrDashboard } from '@/api/opc/hr'
import ResponsiveTable, { type Column } from '@/views/opc/components/ResponsiveTable.vue'

const companyId = 1
const data = ref<OpcHrDashboard | null>(null)
const loading = ref(true)

const STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  OPEN: '招聘中',
  PAUSED: '已暂停',
  CLOSED: '已关闭',
}

const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'info',
  OPEN: 'success',
  PAUSED: 'warning',
  CLOSED: 'danger',
}

const STAGE_LABEL: Record<string, string> = {
  NEW: '新投递',
  SCREENING: '筛选',
  INTERVIEW: '面试',
  OFFER: 'Offer',
  HIRED: '入职',
  REJECTED: '拒绝',
}

const STAGE_COLOR: Record<string, string> = {
  NEW: 'primary',
  SCREENING: 'info',
  INTERVIEW: 'warning',
  OFFER: 'success',
  HIRED: 'success',
  REJECTED: 'danger',
}

const STAGE_HEX: Record<string, string> = {
  NEW: '#409eff',
  SCREENING: '#909399',
  INTERVIEW: '#e6a23c',
  OFFER: '#67c23a',
  HIRED: '#67c23a',
  REJECTED: '#f56c6c',
}

const funnelRows = computed(() => {
  if (!data.value) return []
  const max = Math.max(1, ...data.value.funnel.map((f: any) => Number(f.count || 0)))
  return data.value.funnel.map((f: any) => {
    const s = String(f.status || '')
    const count = Number(f.count || 0)
    return {
      status: s,
      label: STAGE_LABEL[s] || s,
      color: STAGE_COLOR[s] || 'info',
      colorHex: STAGE_HEX[s] || '#909399',
      count,
      percent: Math.max(2, Math.round((count / max) * 100)),
    }
  })
})

const conversionRows = computed(() => {
  if (!data.value) return []
  return Object.entries(data.value.conversionRates).map(([key, rate]) => {
    const num = Number(rate || 0)
    return {
      key,
      label: STAGE_LABEL[key] || key,
      percent: Math.min(100, Math.max(0, num * 100)),
    }
  })
})

const jobDistributionRows = computed(() => {
  if (!data.value) return []
  return Object.entries(data.value.jobStatusDistribution).map(([status, count]) => ({
    status,
    count,
  }))
})

const jobColumns: Column[] = [
  {
    key: 'status',
    label: '状态',
    primary: true,
    width: 120,
    type: 'tag',
    formatter: (v: any) => STATUS_LABEL[String(v || '')] || String(v || '-'),
    tagMap: { DRAFT: 'info', OPEN: 'success', PAUSED: 'warning', CLOSED: 'danger' },
  },
  { key: 'count', label: '数量', width: 100, type: 'number', align: 'right' },
]

const stats = computed(() => {
  if (!data.value) return { totalJobs: 0, openJobs: 0, totalApps: 0, hired: 0 }
  const dist = data.value.jobStatusDistribution || {}
  const totalJobs = Object.values(dist).reduce((sum: number, v: any) => sum + Number(v || 0), 0)
  const openJobs = Number(dist.OPEN || 0)
  const totalApps = data.value.funnel.reduce((sum: number, f: any) => sum + Number(f.count || 0), 0)
  const hired = funnelCountRef('HIRED')
  return { totalJobs, openJobs, totalApps, hired }
})

function funnelCountRef(status: string): number {
  if (!data.value) return 0
  const item = data.value.funnel.find((f: any) => f.status === status)
  return item ? Number((item as any).count || 0) : 0
}
function funnelCount(status: string): number {
  return funnelCountRef(status)
}

onMounted(async () => {
  try {
    const r = await getHrDashboard({ companyId })
    data.value = r.data || null
  } catch (e: any) {
    ElMessage.error(e?.msg || '加载失败')
  } finally {
    loading.value = false
  }
})
</script>

<style scoped lang="scss">
.hr-dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.hero {
  margin-bottom: 0;
}

.hero-card {
  text-align: center;
  color: #fff;
  border: none;
  &--primary {
    background: linear-gradient(135deg, #5b8def 0%, #6c5ce7 100%);
  }
  &--success {
    background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);
  }
  &--warning {
    background: linear-gradient(135deg, #fbc2eb 0%, #a6c1ee 100%);
    color: #5b3a29;
  }
  &--info {
    background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
  }
  :deep(.el-card__body) {
    padding: 20px 16px;
  }
}

.hero-num {
  font-size: 32px;
  font-weight: 700;
  line-height: 1.2;
}

.hero-label {
  font-size: 13px;
  opacity: 0.85;
  margin-top: 4px;
}

.card-title {
  font-weight: 600;
}

.block {
  margin-bottom: 0;
}

.bar-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.bar-track {
  flex: 1;
  background: var(--el-fill-color-light, #f5f7fa);
  height: 12px;
  border-radius: 6px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 6px;
  transition: width 0.3s ease;
}

.bar-num {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  min-width: 48px;
  text-align: right;
}

.rate-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.rate-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.rate-label {
  width: 80px;
  color: var(--el-text-color-secondary, #606266);
  font-size: 13px;
}

.rate-track {
  flex: 1;
  background: var(--el-fill-color-light, #f5f7fa);
  height: 10px;
  border-radius: 5px;
  overflow: hidden;
}

.rate-fill {
  background: linear-gradient(90deg, #5b8def, #6c5ce7);
  height: 100%;
  transition: width 0.3s ease;
}

.rate-value {
  width: 64px;
  text-align: right;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.metric-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.metric-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px dashed var(--el-border-color-lighter, #ebeef5);
  &:last-child {
    border-bottom: none;
  }
}

.metric-label {
  color: var(--el-text-color-secondary, #606266);
  font-size: 13px;
}

.metric-value {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.empty-tip {
  text-align: center;
  color: var(--el-text-color-secondary, #909399);
  font-size: 13px;
  padding: 16px;
}
</style>