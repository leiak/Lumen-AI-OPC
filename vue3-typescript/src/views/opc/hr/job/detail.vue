<template>
  <div class="page hr-job-detail" v-loading="loading">
    <el-card v-if="job">
      <template #header>
        <div class="header">
          <div class="title-block">
            <span class="title">{{ job.title }}</span>
            <el-tag :type="statusTagType(job.status)" effect="plain">{{ statusLabel(job.status) }}</el-tag>
            <el-tag v-if="job.category" type="info" effect="plain">{{ categoryLabel(job.category) }}</el-tag>
          </div>
          <el-button :icon="Back" @click="goBack">返回列表</el-button>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <!-- 基本信息 -->
        <el-tab-pane label="基本信息" name="basic">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="职位">{{ job.title }}</el-descriptions-item>
            <el-descriptions-item label="类别">{{ categoryLabel(job.category) }}</el-descriptions-item>
            <el-descriptions-item label="地点">{{ job.location || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ statusLabel(job.status) }}</el-descriptions-item>
            <el-descriptions-item label="薪资下限">{{ formatAmount(job.salaryMin) }}</el-descriptions-item>
            <el-descriptions-item label="薪资上限">{{ formatAmount(job.salaryMax) }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ job.createTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="发布时间">{{ job.publishAt || '-' }}</el-descriptions-item>
            <el-descriptions-item label="关闭时间">{{ job.closeAt || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建人">#{{ job.createdBy ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="业务描述" :span="2">
              <div class="multiline">{{ job.description || '-' }}</div>
            </el-descriptions-item>
            <el-descriptions-item v-if="job.fullJd" label="完整 JD" :span="2">
              <pre class="full-jd">{{ job.fullJd }}</pre>
            </el-descriptions-item>
            <el-descriptions-item v-if="job.skillsJson" label="技能标签" :span="2">
              <el-tag v-for="t in skillTags" :key="t" size="small" effect="plain" class="skill-tag">
                {{ t }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <!-- 投递列表 -->
        <el-tab-pane :label="`投递 (${applications.length})`" name="applications">
          <div class="tab-actions">
            <el-select v-model="appStatusFilter" placeholder="状态过滤" clearable style="width: 160px" @change="loadApplications">
              <el-option label="新建" value="NEW" />
              <el-option label="筛选中" value="SCREENING" />
              <el-option label="面试" value="INTERVIEW" />
              <el-option label="Offer" value="OFFER" />
              <el-option label="已入职" value="HIRED" />
              <el-option label="已拒绝" value="REJECTED" />
            </el-select>
            <el-button type="primary" :icon="Plus" @click="openCreateApp">录入投递</el-button>
          </div>
          <ResponsiveTable
            :data="applications"
            :loading="appLoading"
            :columns="appColumns"
            :action-width="220"
            empty-text="暂无投递"
          >
            <template #actions="{ row }">
              <el-button link type="primary" size="small" @click="goCandidate(row)">候选人</el-button>
              <el-button link type="warning" size="small" @click="openScore(row)">AI 评分</el-button>
              <el-button link type="success" size="small" @click="onAdvance(row)">推进</el-button>
            </template>
          </ResponsiveTable>
        </el-tab-pane>

        <!-- 面试 -->
        <el-tab-pane :label="`面试 (${interviews.length})`" name="interviews">
          <ResponsiveTable
            :data="interviews"
            :loading="interviewLoading"
            :columns="interviewColumns"
            :action-width="220"
            empty-text="暂无面试"
          >
            <template #actions="{ row }">
              <el-button link type="primary" size="small" @click="openInterview(row)">填写反馈</el-button>
            </template>
          </ResponsiveTable>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 录入投递对话框 -->
    <el-dialog v-model="appDialog" title="录入投递" width="560px">
      <el-form :model="appForm" label-width="80px">
        <el-form-item label="候选人" required>
          <el-input-number v-model="appForm.candidateId" :min="1" placeholder="候选人 ID" style="width: 100%" />
        </el-form-item>
        <el-form-item label="渠道">
          <el-select v-model="appForm.channel" style="width: 100%">
            <el-option label="官网" value="WEBSITE" />
            <el-option label="内推" value="REFERRAL" />
            <el-option label="猎头" value="HEADHUNTER" />
            <el-option label="招聘网站" value="JOB_SITE" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="appDialog = false">取消</el-button>
        <el-button type="primary" :loading="appSaving" @click="onCreateApp">保存</el-button>
      </template>
    </el-dialog>

    <!-- 推进状态对话框 -->
    <el-dialog v-model="advanceDialog" title="推进投递" width="420px">
      <el-form :model="advanceForm" label-width="80px">
        <el-form-item label="目标状态">
          <el-select v-model="advanceForm.status" style="width: 100%">
            <el-option label="筛选中" value="SCREENING" />
            <el-option label="面试" value="INTERVIEW" />
            <el-option label="Offer" value="OFFER" />
            <el-option label="已入职" value="HIRED" />
            <el-option label="已拒绝" value="REJECTED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="advanceDialog = false">取消</el-button>
        <el-button type="primary" :loading="advanceSaving" @click="onConfirmAdvance">确认</el-button>
      </template>
    </el-dialog>

    <!-- 填写面试反馈对话框 -->
    <el-dialog v-model="interviewDialog" title="填写面试反馈" width="560px">
      <el-form :model="interviewForm" label-width="80px">
        <el-form-item label="反馈">
          <el-input v-model="interviewForm.feedback" type="textarea" :rows="3" placeholder="面试评价" />
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="interviewForm.result" style="width: 100%">
            <el-option label="通过" value="PASS" />
            <el-option label="未通过" value="FAIL" />
            <el-option label="待定" value="PENDING" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="interviewDialog = false">取消</el-button>
        <el-button type="primary" :loading="interviewSaving" @click="onSaveInterview">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Back } from '@element-plus/icons-vue'
import {
  getJob,
  listApplications,
  createApplication,
  transitionApplicationStatus,
  scoreApplication,
  listInterviews,
  updateInterviewFeedback,
  type OpcHrJob,
  type OpcHrApplication,
  type OpcHrInterview,
} from '@/api/opc/hr'
import ResponsiveTable, { type Column } from '@/views/opc/components/ResponsiveTable.vue'

const route = useRoute()
const router = useRouter()
const companyId = 1
const jobId = computed(() => Number(route.params.id))

const job = ref<OpcHrJob | null>(null)
const loading = ref(false)
const activeTab = ref('basic')

const applications = ref<OpcHrApplication[]>([])
const appLoading = ref(false)
const appStatusFilter = ref('')

const interviews = ref<OpcHrInterview[]>([])
const interviewLoading = ref(false)

const appDialog = ref(false)
const appSaving = ref(false)
const appForm = reactive<Partial<OpcHrApplication>>({
  candidateId: undefined,
  channel: 'WEBSITE',
})

const advanceDialog = ref(false)
const advanceSaving = ref(false)
const advanceForm = reactive({ id: 0, status: 'SCREENING' })

const interviewDialog = ref(false)
const interviewSaving = ref(false)
const interviewForm = reactive({ id: 0, feedback: '', result: 'PASS' })

const appColumns: Column[] = [
  { key: 'candidateId', label: '候选人', primary: true, width: 100, type: 'number' },
  {
    key: 'channel',
    label: '渠道',
    width: 100,
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
  {
    key: 'currentStage',
    label: '当前阶段',
    width: 100,
    type: 'tag',
    formatter: (v: any) =>
      ({ NEW: '新投递', SCREENING: '筛选', INTERVIEW: '面试', OFFER: 'Offer', HIRED: '入职', REJECTED: '拒绝' } as any)[
        String(v || '')
      ] || String(v || '-'),
  },
  { key: 'appliedAt', label: '投递时间', type: 'date', width: 160, hideOnMobile: true },
]

const interviewColumns: Column[] = [
  { key: 'applicationId', label: '投递', primary: true, width: 90, type: 'number' },
  { key: 'round', label: '轮次', width: 70, type: 'number' },
  {
    key: 'type',
    label: '类型',
    width: 90,
    type: 'tag',
    formatter: (v: any) =>
      ({ PHONE: '电话', ONSITE: '现场', VIDEO: '视频', TECHNICAL: '技术', HR: 'HR' } as any)[
        String(v || '')
      ] || String(v || '-'),
    tagMap: { PHONE: 'info', ONSITE: 'warning', VIDEO: 'primary', TECHNICAL: 'success', HR: 'info' },
  },
  { key: 'interviewerId', label: '面试官', width: 90, type: 'number' },
  { key: 'scheduledAt', label: '面试时间', type: 'date', width: 160 },
  { key: 'durationMin', label: '时长(分)', width: 90, type: 'number', align: 'right' },
  {
    key: 'result',
    label: '结果',
    width: 90,
    type: 'tag',
    formatter: (v: any) =>
      ({ PASS: '通过', FAIL: '未过', PENDING: '待定' } as any)[String(v || '')] || String(v || '-'),
    tagMap: { PASS: 'success', FAIL: 'danger', PENDING: 'warning' },
  },
]

function statusLabel(s?: string) {
  return (
    ({ DRAFT: '草稿', OPEN: '招聘中', PAUSED: '已暂停', CLOSED: '已关闭' } as any)[String(s || '')] ||
    s ||
    '-'
  )
}
function statusTagType(s?: string): 'success' | 'warning' | 'info' | 'danger' {
  return (
    ({ DRAFT: 'info', OPEN: 'success', PAUSED: 'warning', CLOSED: 'danger' } as any)[String(s || '')] ||
    'info'
  )
}
function categoryLabel(c?: string) {
  return (
    ({ TECH: '技术', SALES: '销售', OPERATION: '运营', FINANCE: '财务', MARKETING: '市场', HR: '人事', OTHER: '其他' } as any)[
      String(c || '')
    ] || c || '-'
  )
}
function formatAmount(v?: number) {
  if (v === undefined || v === null) return '-'
  return `¥ ${v.toLocaleString('zh-CN')}`
}

const skillTags = computed<string[]>(() => {
  if (!job.value?.skillsJson) return []
  try {
    const arr = JSON.parse(job.value.skillsJson)
    return Array.isArray(arr) ? arr.map(String) : []
  } catch {
    return String(job.value.skillsJson).split(',').map((s) => s.trim()).filter(Boolean)
  }
})

async function loadJob() {
  loading.value = true
  try {
    const r = await getJob(jobId.value, companyId)
    job.value = r.data || null
  } catch (e: any) {
    ElMessage.error(e?.msg || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadApplications() {
  appLoading.value = true
  try {
    const r = await listApplications({
      companyId,
      jobId: jobId.value,
      status: appStatusFilter.value || undefined,
    })
    applications.value = r.data || []
  } catch (e: any) {
    ElMessage.error(e?.msg || '加载投递失败')
  } finally {
    appLoading.value = false
  }
}

async function loadInterviews() {
  interviewLoading.value = true
  try {
    const r = await listInterviews({ companyId })
    const all = r.data || []
    const appIds = new Set(applications.value.map((a: OpcHrApplication) => a.id).filter((x: number | undefined): x is number => !!x))
    interviews.value = all.filter((iv) => iv.applicationId && appIds.has(iv.applicationId))
  } catch {
    interviews.value = []
  } finally {
    interviewLoading.value = false
  }
}

function openCreateApp() {
  Object.assign(appForm, { candidateId: undefined, channel: 'WEBSITE' })
  appDialog.value = true
}

async function onCreateApp() {
  if (!appForm.candidateId) {
    ElMessage.warning('请填写候选人 ID')
    return
  }
  appSaving.value = true
  try {
    await createApplication({
      companyId,
      jobId: jobId.value,
      candidateId: appForm.candidateId,
      channel: appForm.channel,
    })
    ElMessage.success('已录入')
    appDialog.value = false
    loadApplications()
  } catch (e: any) {
    ElMessage.error(e?.msg || '录入失败')
  } finally {
    appSaving.value = false
  }
}

function openAdvance(row: OpcHrApplication) {
  if (!row.id) return
  advanceForm.id = row.id
  advanceForm.status = row.currentStage || 'SCREENING'
  advanceDialog.value = true
}

async function onConfirmAdvance() {
  advanceSaving.value = true
  try {
    await transitionApplicationStatus(advanceForm.id, companyId, advanceForm.status)
    ElMessage.success('状态已更新')
    advanceDialog.value = false
    loadApplications()
  } catch (e: any) {
    ElMessage.error(e?.msg || '更新失败')
  } finally {
    advanceSaving.value = false
  }
}

async function onScore(row: OpcHrApplication) {
  if (!row.id) return
  try {
    await scoreApplication(row.id, companyId)
    ElMessage.success('AI 评分完成')
    loadApplications()
  } catch (e: any) {
    ElMessage.error(e?.msg || '评分失败')
  }
}

function openInterview(row: OpcHrInterview) {
  if (!row.id) return
  interviewForm.id = row.id
  interviewForm.feedback = row.feedback || ''
  interviewForm.result = row.result || 'PENDING'
  interviewDialog.value = true
}

async function onSaveInterview() {
  interviewSaving.value = true
  try {
    await updateInterviewFeedback(
      interviewForm.id,
      companyId,
      interviewForm.feedback,
      interviewForm.result,
    )
    ElMessage.success('反馈已保存')
    interviewDialog.value = false
    loadInterviews()
  } catch (e: any) {
    ElMessage.error(e?.msg || '保存失败')
  } finally {
    interviewSaving.value = false
  }
}

function goCandidate(row: OpcHrApplication) {
  if (!row.candidateId) return
  router.push(`/opc/hr/candidate/${row.candidateId}`)
}

function goBack() {
  router.push('/opc/hr/job')
}

onMounted(() => {
  loadJob()
  loadApplications()
})
</script>

<style scoped lang="scss">
.hr-job-detail {
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
  .tab-actions {
    margin-bottom: 12px;
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  }
  .multiline {
    white-space: pre-wrap;
    word-break: break-word;
  }
  .full-jd {
    white-space: pre-wrap;
    font-family: inherit;
    margin: 0;
    word-break: break-word;
    background: var(--el-fill-color-light, #f5f7fa);
    padding: 8px;
    border-radius: 4px;
    max-height: 320px;
    overflow: auto;
  }
  .skill-tag {
    margin-right: 6px;
    margin-bottom: 4px;
  }
}
</style>