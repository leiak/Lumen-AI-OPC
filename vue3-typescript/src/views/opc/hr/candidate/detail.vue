<template>
  <div class="page hr-candidate-detail" v-loading="loading">
    <el-card v-if="candidate">
      <template #header>
        <div class="header">
          <div class="title-block">
            <span class="title">{{ candidate.name }}</span>
            <el-tag v-if="candidate.source" type="info" effect="plain">{{ sourceLabel(candidate.source) }}</el-tag>
            <el-tag v-for="t in tagList" :key="t" size="small" effect="plain">{{ t }}</el-tag>
          </div>
          <div class="actions">
            <el-button type="primary" :icon="Refresh" @click="onParse" :loading="parsing">
              AI 解析简历
            </el-button>
            <el-button :icon="Back" @click="goBack">返回列表</el-button>
          </div>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="基本信息" name="basic">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="姓名">{{ candidate.name }}</el-descriptions-item>
            <el-descriptions-item label="来源">{{ sourceLabel(candidate.source) }}</el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ candidate.email || '-' }}</el-descriptions-item>
            <el-descriptions-item label="电话">{{ candidate.phone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="简历" :span="2">
              <a v-if="candidate.resumeUrl" :href="candidate.resumeUrl" target="_blank">
                {{ candidate.resumeUrl }}
              </a>
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="入库时间">{{ candidate.createTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ candidate.updateTime || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <el-tab-pane label="简历解析" name="resume">
          <el-empty v-if="!candidate.resumeMd && !parsedPreview" description="尚未解析,点击右上「AI 解析简历」" />
          <template v-else>
            <h3>解析预览(LLM 输出)</h3>
            <pre class="parsed-md">{{ parsedPreview || '暂无' }}</pre>
          </template>
        </el-tab-pane>

        <el-tab-pane :label="`投递历史 (${applications.length})`" name="applications">
          <ResponsiveTable
            :data="applications"
            :loading="appLoading"
            :columns="appColumns"
            :action-width="160"
            empty-text="暂无投递"
          >
            <template #actions="{ row }">
              <el-button link type="primary" size="small" @click="goJob(row)">查看 JD</el-button>
            </template>
          </ResponsiveTable>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Back } from '@element-plus/icons-vue'
import {
  getCandidate,
  parseCandidate,
  listApplications,
  type OpcHrCandidate,
  type OpcHrApplication,
} from '@/api/opc/hr'
import ResponsiveTable, { type Column } from '@/views/opc/components/ResponsiveTable.vue'

const route = useRoute()
const router = useRouter()
const companyId = 1
const candidateId = computed(() => Number(route.params.id))

const candidate = ref<OpcHrCandidate | null>(null)
const loading = ref(false)
const parsing = ref(false)
const activeTab = ref('basic')

const applications = ref<OpcHrApplication[]>([])
const appLoading = ref(false)

const appColumns: Column[] = [
  { key: 'jobId', label: 'JD', primary: true, width: 90, type: 'number' },
  {
    key: 'channel',
    label: '渠道',
    width: 90,
    type: 'tag',
    formatter: (v: any) =>
      ({ WEBSITE: '官网', REFERRAL: '内推', HEADHUNTER: '猎头', JOB_SITE: '招聘网站', OTHER: '其他' } as any)[
        String(v || '')
      ] || String(v || '-'),
    tagMap: { WEBSITE: 'primary', REFERRAL: 'success', HEADHUNTER: 'warning', JOB_SITE: 'info', OTHER: '' },
  },
  { key: 'score', label: 'LLM 评分', width: 100, align: 'right' },
  {
    key: 'status',
    label: '状态',
    width: 110,
    type: 'tag',
    tagMap: {
      NEW: 'info',
      SCREENING: 'primary',
      INTERVIEW: 'warning',
      OFFER: 'success',
      HIRED: 'success',
      REJECTED: 'danger',
    },
  },
  { key: 'appliedAt', label: '投递时间', type: 'date', width: 160 },
]

const tagList = computed<string[]>(() => {
  if (!candidate.value?.tagsJson) return []
  try {
    const arr = JSON.parse(candidate.value.tagsJson)
    return Array.isArray(arr) ? arr.map(String) : []
  } catch {
    return String(candidate.value.tagsJson).split(',').map((s) => s.trim()).filter(Boolean)
  }
})

const parsedPreview = computed(() => {
  if (!candidate.value?.parsedJson) return ''
  try {
    const obj = JSON.parse(candidate.value.parsedJson)
    return JSON.stringify(obj, null, 2)
  } catch {
    return candidate.value.parsedJson
  }
})

function sourceLabel(s?: string) {
  return (
    ({ WEBSITE: '官网', REFERRAL: '内推', HEADHUNTER: '猎头', JOB_SITE: '招聘网站', OTHER: '其他' } as any)[
      String(s || '')
    ] || s || '-'
  )
}

async function loadCandidate() {
  loading.value = true
  try {
    const r = await getCandidate(candidateId.value, companyId)
    candidate.value = r.data || null
  } catch (e: any) {
    ElMessage.error(e?.msg || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadApplications() {
  appLoading.value = true
  try {
    const r = await listApplications({ companyId, candidateId: candidateId.value })
    applications.value = r.data || []
  } catch {
    applications.value = []
  } finally {
    appLoading.value = false
  }
}

async function onParse() {
  if (!candidate.value?.id) return
  parsing.value = true
  try {
    await parseCandidate(candidate.value.id, companyId)
    ElMessage.success('解析完成')
    await loadCandidate()
  } catch (e: any) {
    ElMessage.error(e?.msg || '解析失败')
  } finally {
    parsing.value = false
  }
}

function goJob(row: OpcHrApplication) {
  if (!row.jobId) return
  router.push(`/opc/hr/job/${row.jobId}`)
}

function goBack() {
  router.push('/opc/hr/candidate')
}

onMounted(() => {
  loadCandidate()
  loadApplications()
})
</script>

<style scoped lang="scss">
.hr-candidate-detail {
  .header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: 12px;
  }
  .title-block {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }
  .title {
    font-size: 18px;
    font-weight: 600;
  }
  .actions {
    display: inline-flex;
    gap: 8px;
  }
  .parsed-md {
    background: var(--el-fill-color-light, #f5f7fa);
    padding: 12px;
    border-radius: 4px;
    white-space: pre-wrap;
    word-break: break-word;
    max-height: 480px;
    overflow: auto;
    font-family: 'Courier New', monospace;
    font-size: 13px;
  }
  h3 {
    margin: 0 0 8px;
  }
}
</style>