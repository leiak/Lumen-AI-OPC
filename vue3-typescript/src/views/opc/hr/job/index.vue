<template>
  <div class="page hr-job-list">
    <el-card>
      <template #header>
        <div class="header">
          <span class="title">
            <el-icon><Briefcase /></el-icon>
            招聘需求
          </span>
          <div class="actions">
            <el-select v-model="statusFilter" placeholder="状态过滤" clearable style="width: 140px" @change="load">
              <el-option label="草稿" value="DRAFT" />
              <el-option label="招聘中" value="OPEN" />
              <el-option label="暂停" value="PAUSED" />
              <el-option label="已关闭" value="CLOSED" />
            </el-select>
            <el-button :icon="Refresh" @click="load">刷新</el-button>
            <el-button type="success" :icon="MagicStick" @click="openLlmDialog">AI 生成 JD</el-button>
            <el-button type="primary" :icon="Plus" @click="openCreate">新建 JD</el-button>
          </div>
        </div>
      </template>

      <ResponsiveTable
        :data="jobs"
        :loading="loading"
        :columns="columns"
        :action-width="320"
        empty-text="暂无招聘需求"
      >
        <template #actions="{ row }">
          <el-button link type="primary" size="small" @click="goDetail(row)">详情</el-button>
          <el-button
            v-if="row.status === 'DRAFT'"
            link
            type="success"
            size="small"
            @click="onPublish(row)"
          >发布</el-button>
          <el-button
            v-if="row.status === 'OPEN'"
            link
            type="warning"
            size="small"
            @click="onPause(row)"
          >暂停</el-button>
          <el-button
            v-if="row.status === 'OPEN' || row.status === 'PAUSED'"
            link
            type="info"
            size="small"
            @click="onClose(row)"
          >关闭</el-button>
          <el-button
            v-if="row.status === 'DRAFT'"
            link
            type="danger"
            size="small"
            @click="onDelete(row)"
          >删除</el-button>
        </template>
      </ResponsiveTable>
    </el-card>

    <!-- 新建 JD 对话框 -->
    <el-dialog v-model="createVisible" title="新建招聘需求" width="640px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="职位" required>
          <el-input v-model="form.title" placeholder="例如:高级 Java 工程师" />
        </el-form-item>
        <el-form-item label="类别" required>
          <el-select v-model="form.category" placeholder="选择类别" style="width: 100%">
            <el-option label="技术" value="TECH" />
            <el-option label="销售" value="SALES" />
            <el-option label="运营" value="OPERATION" />
            <el-option label="财务" value="FINANCE" />
            <el-option label="市场" value="MARKETING" />
            <el-option label="人事" value="HR" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="地点">
          <el-input v-model="form.location" placeholder="工作地点" />
        </el-form-item>
        <el-form-item label="薪资范围">
          <div class="salary-range">
            <el-input-number v-model="form.salaryMin" :min="0" :step="1000" placeholder="最低" />
            <span class="separator">-</span>
            <el-input-number v-model="form.salaryMax" :min="0" :step="1000" placeholder="最高" />
            <span class="unit">元 / 月</span>
          </div>
        </el-form-item>
        <el-form-item label="业务描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="团队、产品线、关键技术栈等"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onCreate">创建草稿</el-button>
      </template>
    </el-dialog>

    <!-- AI 生成 JD 对话框 -->
    <el-dialog v-model="llmVisible" title="AI 生成 JD" width="640px">
      <el-form :model="llmForm" label-width="80px">
        <el-form-item label="职位">
          <el-input v-model="llmForm.title" placeholder="例如:高级前端工程师" />
        </el-form-item>
        <el-form-item label="类别">
          <el-select v-model="llmForm.category" style="width: 100%">
            <el-option label="技术" value="TECH" />
            <el-option label="销售" value="SALES" />
            <el-option label="运营" value="OPERATION" />
            <el-option label="财务" value="FINANCE" />
            <el-option label="市场" value="MARKETING" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务描述">
          <el-input
            v-model="llmForm.description"
            type="textarea"
            :rows="4"
            placeholder="团队、产品线、招聘背景等(可选)"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="llmVisible = false">取消</el-button>
        <el-button type="primary" :loading="llmLoading" @click="onGenerateLlm">生成</el-button>
      </template>
    </el-dialog>

    <!-- AI 生成结果 -->
    <el-dialog v-model="llmResultVisible" title="AI 生成结果" width="640px">
      <el-input
        v-model="llmResultText"
        type="textarea"
        :rows="14"
        readonly
        placeholder="生成结果"
      />
      <template #footer>
        <el-button @click="copyLlmResult">复制内容</el-button>
        <el-button @click="llmResultVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Briefcase, MagicStick } from '@element-plus/icons-vue'
import {
  listJobs,
  createJob,
  publishJob,
  pauseJob,
  closeJob,
  deleteJob,
  generateLlmJob,
  type OpcHrJob,
} from '@/api/opc/hr'
import ResponsiveTable, { type Column } from '@/views/opc/components/ResponsiveTable.vue'

const router = useRouter()
const companyId = 1

const jobs = ref<OpcHrJob[]>([])
const loading = ref(false)
const saving = ref(false)
const statusFilter = ref('')

const columns: Column[] = [
  { key: 'title', label: '职位', primary: true, minWidth: 180 },
  {
    key: 'category',
    label: '类别',
    width: 100,
    type: 'tag',
    formatter: (v: any) =>
      ({ TECH: '技术', SALES: '销售', OPERATION: '运营', FINANCE: '财务', MARKETING: '市场', HR: '人事', OTHER: '其他' } as any)[
        String(v || '')
      ] || String(v || '-'),
    tagMap: { TECH: 'primary', SALES: 'warning', OPERATION: 'success', FINANCE: 'info', MARKETING: 'success', HR: 'info', OTHER: '' },
  },
  { key: 'location', label: '地点', width: 110, hideOnMobile: true },
  { key: 'salaryMin', label: '薪资下限', type: 'amount', width: 120, hideOnMobile: true, align: 'right' },
  { key: 'salaryMax', label: '薪资上限', type: 'amount', width: 120, hideOnMobile: true, align: 'right' },
  {
    key: 'status',
    label: '状态',
    width: 100,
    type: 'tag',
    tagMap: { DRAFT: 'info', OPEN: 'success', PAUSED: 'warning', CLOSED: 'danger' },
  },
  { key: 'createTime', label: '创建时间', type: 'date', width: 160, hideOnMobile: true },
]

const createVisible = ref(false)
const form = reactive<Partial<OpcHrJob>>({
  title: '',
  category: 'TECH',
  location: '',
  salaryMin: undefined,
  salaryMax: undefined,
  description: '',
})

const llmVisible = ref(false)
const llmLoading = ref(false)
const llmForm = reactive({ title: '', category: 'TECH', description: '' })
const llmResultVisible = ref(false)
const llmResultText = ref('')

async function load() {
  loading.value = true
  try {
    const r = await listJobs({
      companyId,
      status: statusFilter.value || undefined,
    })
    jobs.value = r.data || []
  } catch (e: any) {
    ElMessage.error(e?.msg || '加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    title: '',
    category: 'TECH',
    location: '',
    salaryMin: undefined,
    salaryMax: undefined,
    description: '',
  })
  createVisible.value = true
}

async function onCreate() {
  if (!form.title?.trim()) {
    ElMessage.warning('请输入职位名称')
    return
  }
  if (!form.category) {
    ElMessage.warning('请选择类别')
    return
  }
  saving.value = true
  try {
    await createJob({ ...form, companyId })
    ElMessage.success('已创建草稿')
    createVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '创建失败')
  } finally {
    saving.value = false
  }
}

function openLlmDialog() {
  Object.assign(llmForm, { title: '', category: 'TECH', description: '' })
  llmVisible.value = true
}

async function onGenerateLlm() {
  if (!llmForm.title?.trim()) {
    ElMessage.warning('请输入职位名称')
    return
  }
  llmLoading.value = true
  try {
    const r = await generateLlmJob({ ...llmForm, companyId })
    llmResultText.value = r.data || ''
    llmVisible.value = false
    llmResultVisible.value = true
  } catch (e: any) {
    ElMessage.error(e?.msg || '生成失败')
  } finally {
    llmLoading.value = false
  }
}

async function copyLlmResult() {
  try {
    await navigator.clipboard.writeText(llmResultText.value)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.warning('复制失败')
  }
}

async function onPublish(row: OpcHrJob) {
  if (!row.id) return
  try {
    await publishJob(row.id, companyId)
    ElMessage.success('已发布')
    load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '发布失败')
  }
}

async function onPause(row: OpcHrJob) {
  if (!row.id) return
  try {
    await pauseJob(row.id, companyId)
    ElMessage.success('已暂停')
    load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '暂停失败')
  }
}

async function onClose(row: OpcHrJob) {
  if (!row.id) return
  await ElMessageBox.confirm(`关闭招聘需求「${row.title}」?`, '提示', { type: 'warning' }).catch(() => {})
  try {
    await closeJob(row.id, companyId)
    ElMessage.success('已关闭')
    load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '关闭失败')
  }
}

async function onDelete(row: OpcHrJob) {
  if (!row.id) return
  await ElMessageBox.confirm(`删除招聘需求「${row.title}」?`, '提示', { type: 'warning' }).catch(() => {})
  try {
    await deleteJob(row.id, companyId)
    ElMessage.success('已删除')
    load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '删除失败')
  }
}

function goDetail(row: OpcHrJob) {
  if (!row.id) return
  router.push(`/opc/hr/job/${row.id}`)
}

onMounted(load)
</script>

<style scoped lang="scss">
.hr-job-list {
  .header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: 12px;
  }
  .title {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-weight: 600;
  }
  .actions {
    display: inline-flex;
    gap: 8px;
    flex-wrap: wrap;
  }
  .salary-range {
    display: flex;
    align-items: center;
    gap: 8px;
    .separator {
      color: var(--el-text-color-secondary);
    }
    .unit {
      color: var(--el-text-color-secondary);
      font-size: 13px;
    }
  }
}
</style>