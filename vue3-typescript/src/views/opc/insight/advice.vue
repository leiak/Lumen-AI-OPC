<template>
  <div class="page insight-advice">
    <h2>决策建议</h2>

    <!-- 主题选择 + 生成 -->
    <el-row :gutter="12" class="toolbar">
      <el-col :xs="24" :sm="14">
        <el-select v-model="selectedTopic" placeholder="选择建议主题" style="width: 100%">
          <el-option
            v-for="t in TOPICS"
            :key="t.value"
            :label="t.label"
            :value="t.value"
          />
        </el-select>
      </el-col>
      <el-col :xs="12" :sm="5">
        <el-button
          type="primary"
          :loading="generating"
          :disabled="!selectedTopic"
          style="width: 100%"
          @click="onGenerate"
        >
          生成建议
        </el-button>
      </el-col>
      <el-col :xs="12" :sm="5">
        <el-button :loading="loading" style="width: 100%" @click="load">刷新</el-button>
      </el-col>
    </el-row>

    <!-- 列表 -->
    <el-empty v-if="!loading && !list.length" description="暂无建议" />
    <el-table v-else :data="sortedList" v-loading="loading" stripe>
      <el-table-column prop="topic" label="主题" width="160">
        <template #default="{ row }">{{ topicLabel(row.topic) }}</template>
      </el-table-column>
      <el-table-column prop="summary" label="摘要" min-width="220" />
      <el-table-column prop="createTime" label="生成时间" width="180">
        <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160" align="center">
        <template #default="{ row }">
          <el-button size="small" @click="view(row)">查看</el-button>
          <el-button
            size="small"
            type="primary"
            :loading="regenId === row.id"
            @click="onRegenerate(row)"
          >
            重新生成
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 详情弹窗 -->
    <el-dialog v-model="show" :title="`建议 - ${topicLabel(current?.topic)}`" width="640px">
      <div v-if="current">
        <p><b>摘要：</b>{{ current.summary }}</p>
        <h4>建议详情</h4>
        <div class="advice-box">
          <pre>{{ current.content || current.advice || '（暂无）' }}</pre>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="InsightAdvice">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { listAdvice, getAdvice, regenerateAdvice } from '@/api/opc/insight'

const TOPICS = [
  { value: 'cost_optimization', label: '成本优化' },
  { value: 'revenue_growth', label: '收入增长' },
  { value: 'cashflow_health', label: '现金流健康' },
  { value: 'tax_planning', label: '税务规划' },
  { value: 'risk_warning', label: '风险预警' },
]

const list = ref<any[]>([])
const loading = ref(false)
const generating = ref(false)
const regenId = ref<number | null>(null)
const selectedTopic = ref<string>('')
const show = ref(false)
const current = ref<any>(null)

const sortedList = computed(() =>
  [...list.value].sort((a, b) => String(b.createTime || '').localeCompare(String(a.createTime || '')))
)

function topicLabel(v?: string): string {
  const t = TOPICS.find((x) => x.value === v)
  return t ? t.label : v || '-'
}

function formatDate(v: any): string {
  if (!v) return '-'
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return String(v)
  const pad = (n: number) => (n < 10 ? '0' + n : String(n))
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function load() {
  loading.value = true
  try {
    const r = await listAdvice(50)
    list.value = r.data || []
  } catch (e: any) {
    ElMessage.error(e?.msg || '加载建议列表失败')
    list.value = []
  } finally {
    loading.value = false
  }
}

async function onGenerate() {
  if (!selectedTopic.value) {
    ElMessage.warning('请先选择主题')
    return
  }
  generating.value = true
  try {
    // backend contract: POST /opc/insight/advice?topic=<value>
    await request({
      url: '/opc/insight/advice',
      method: 'post',
      params: { topic: selectedTopic.value },
    })
    ElMessage.success('已生成建议')
    selectedTopic.value = ''
    await load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '生成建议失败')
  } finally {
    generating.value = false
  }
}

async function onRegenerate(row: any) {
  regenId.value = row.id
  try {
    await regenerateAdvice(row.id)
    ElMessage.success('已重新生成')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '重新生成失败')
  } finally {
    regenId.value = null
  }
}

async function view(row: any) {
  show.value = true
  current.value = null
  try {
    const r = await getAdvice(row.id)
    current.value = r.data
  } catch (e: any) {
    ElMessage.error(e?.msg || '加载建议详情失败')
    show.value = false
  }
}

onMounted(load)
</script>

<style scoped lang="scss">
.insight-advice { display: flex; flex-direction: column; gap: 12px; }
h2 { margin: 0 0 8px; font-size: 20px; font-weight: 600; }
.toolbar { margin-bottom: 12px; }
.advice-box {
  background: #f5f7fa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px;
  max-height: 320px;
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
h4 { margin: 12px 0 8px; font-size: 14px; font-weight: 600; }
</style>
