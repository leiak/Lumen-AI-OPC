<template>
  <div class="page content-dashboard" v-loading="loading">
    <h2 class="page-title">AI 内容创作中心</h2>

    <el-row :gutter="16" class="hero">
      <el-col :xs="12" :md="6">
        <el-card shadow="never" class="hero-card hero-card--primary">
          <div class="hero-num">{{ stats.todayGenerate }}</div>
          <div class="hero-label">今日生成</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :md="6">
        <el-card shadow="never" class="hero-card hero-card--warning">
          <div class="hero-num">{{ stats.pendingPublish }}</div>
          <div class="hero-label">待发布</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :md="6">
        <el-card shadow="never" class="hero-card hero-card--success">
          <div class="hero-num">{{ stats.published }}</div>
          <div class="hero-label">已发布</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :md="6">
        <el-card shadow="never" class="hero-card hero-card--danger">
          <div class="hero-num">{{ formatPercent(stats.failedRate) }}</div>
          <div class="hero-label">失败率</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="block">
      <el-col :xs="24" :md="14">
        <el-card>
          <template #header><span class="card-title">最近脚本</span></template>
          <el-table :data="recentScripts" stripe empty-text="暂无脚本">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
            <el-table-column prop="type" label="类型" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="typeTagType(row.type)" effect="plain">{{ typeLabel(row.type) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="statusTagType(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="goDetail(row)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="10">
        <el-card>
          <template #header><span class="card-title">近 7 日生成趋势</span></template>
          <div v-if="!trend.length" class="empty-tip">暂无数据</div>
          <div v-else class="trend-list">
            <div v-for="(v, i) in trend" :key="i" class="trend-row">
              <span class="trend-label">D{{ i + 1 }}</span>
              <div class="trend-track">
                <div class="trend-fill" :style="{ width: trendPercent(v) + '%' }"></div>
              </div>
              <span class="trend-value">{{ v }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getContentDashboard, type OpcContentDashboard, type OpcContentScript } from '@/api/opc/content'

const router = useRouter()
const companyId = 1

const data = ref<OpcContentDashboard | null>(null)
const loading = ref(true)

const stats = computed(() => {
  if (!data.value) return { todayGenerate: 0, pendingPublish: 0, published: 0, failedRate: 0 }
  return {
    todayGenerate: data.value.today_generate || 0,
    pendingPublish: data.value.pending_publish || 0,
    published: data.value.published || 0,
    failedRate: data.value.failed_rate || 0,
  }
})

const recentScripts = computed<OpcContentScript[]>(() => data.value?.recent_scripts || [])
const trend = computed<number[]>(() => data.value?.seven_day_trend || [])

function formatPercent(v: number): string {
  const n = Number(v || 0)
  return (n * 100).toFixed(1) + '%'
}

function trendPercent(v: number): number {
  const max = Math.max(1, ...trend.value)
  return Math.max(2, Math.round((Number(v || 0) / max) * 100))
}

function typeLabel(t?: string) {
  return ({ DRAMA: '短剧', VIDEO: '视频', ARTICLE: '图文', ADAPTER: '适配' } as any)[String(t || '')] || String(t || '-')
}
function typeTagType(t?: string) {
  return ({ DRAMA: 'primary', VIDEO: 'success', ARTICLE: 'info', ADAPTER: 'warning' } as any)[String(t || '')] || ''
}
function statusLabel(s?: string) {
  return ({ DRAFT: '草稿', READY: '就绪', PUBLISHED: '已发布', FAILED: '失败', DELETED: '已删除' } as any)[String(s || '')] || String(s || '-')
}
function statusTagType(s?: string) {
  return ({ DRAFT: 'info', READY: 'success', PUBLISHED: '', FAILED: 'danger', DELETED: 'danger' } as any)[String(s || '')] || ''
}

function goDetail(row: OpcContentScript) {
  if (!row.id) return
  router.push(`/opc/content/script/${row.id}`)
}

onMounted(async () => {
  try {
    const r = await getContentDashboard(companyId)
    data.value = r.data || null
  } catch (e: any) {
    ElMessage.error(e?.msg || '加载仪表盘失败')
  } finally {
    loading.value = false
  }
})
</script>

<style scoped lang="scss">
.content-dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.page-title {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 600;
}
.hero {
  margin-bottom: 0;
}
.hero-card {
  text-align: center;
  color: #fff;
  border: none;
  &--primary { background: linear-gradient(135deg, #5b8def 0%, #6c5ce7 100%); }
  &--success { background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%); color: #1a4d3a; }
  &--warning { background: linear-gradient(135deg, #fbc2eb 0%, #a6c1ee 100%); color: #5b3a29; }
  &--danger { background: linear-gradient(135deg, #fa709a 0%, #fee140 100%); color: #5b1a3a; }
  :deep(.el-card__body) { padding: 20px 16px; }
}
.hero-num { font-size: 30px; font-weight: 700; line-height: 1.2; }
.hero-label { font-size: 13px; opacity: 0.9; margin-top: 4px; }
.card-title { font-weight: 600; }
.block { margin-bottom: 0; }
.trend-list { display: flex; flex-direction: column; gap: 8px; }
.trend-row { display: flex; align-items: center; gap: 10px; }
.trend-label { width: 36px; color: var(--el-text-color-secondary, #606266); font-size: 12px; }
.trend-track { flex: 1; background: var(--el-fill-color-light, #f5f7fa); height: 10px; border-radius: 5px; overflow: hidden; }
.trend-fill { background: linear-gradient(90deg, #5b8def, #6c5ce7); height: 100%; transition: width 0.3s ease; }
.trend-value { width: 48px; text-align: right; font-weight: 600; font-variant-numeric: tabular-nums; }
.empty-tip { text-align: center; color: var(--el-text-color-secondary, #909399); font-size: 13px; padding: 16px; }
</style>