<template>
  <div class="page hr-candidate-list">
    <el-card>
      <template #header>
        <div class="header">
          <span class="title">
            <el-icon><User /></el-icon>
            候选人库
          </span>
          <div class="actions">
            <el-input
              v-model="queryKeyword"
              placeholder="语义搜索:技术栈 / 经验 / 项目"
              clearable
              style="width: 280px"
              @keyup.enter="onSearch"
            />
            <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
            <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
            <el-button :icon="Plus" @click="openCreate">新建候选人</el-button>
          </div>
        </div>
      </template>

      <!-- 语义搜索结果 -->
      <div v-if="searchMode" class="search-result-banner">
        <el-icon><Aim /></el-icon>
        语义搜索关键词:<strong>{{ queryKeyword }}</strong>,共 {{ searchResults.length }} 条结果
        <el-button link type="primary" size="small" @click="resetQuery">清除</el-button>
      </div>

      <ResponsiveTable
        :data="displayList"
        :loading="loading"
        :columns="columns"
        :action-width="180"
        empty-text="暂无候选人"
      >
        <template #actions="{ row }">
          <el-button link type="primary" size="small" @click="goDetail(row)">详情</el-button>
          <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
        </template>
      </ResponsiveTable>
    </el-card>

    <!-- 新建候选人 -->
    <el-dialog v-model="createVisible" title="新建候选人" width="640px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="姓名" required>
          <el-input v-model="form.name" placeholder="候选人姓名" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="example@company.com" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="form.phone" placeholder="11 位手机号" />
        </el-form-item>
        <el-form-item label="简历 URL" required>
          <el-input v-model="form.resumeUrl" placeholder="https://..." />
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="form.source" style="width: 100%">
            <el-option label="官网" value="WEBSITE" />
            <el-option label="内推" value="REFERRAL" />
            <el-option label="猎头" value="HEADHUNTER" />
            <el-option label="招聘网站" value="JOB_SITE" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="tagsInput" placeholder="逗号分隔: Java, Redis, 10年经验" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onCreate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, User, Search, Aim } from '@element-plus/icons-vue'
import {
  listCandidates,
  createCandidate,
  deleteCandidate,
  searchCandidate,
  type OpcHrCandidate,
  type HrSearchResult,
} from '@/api/opc/hr'
import ResponsiveTable, { type Column } from '@/views/opc/components/ResponsiveTable.vue'

const router = useRouter()
const companyId = 1

const candidates = ref<OpcHrCandidate[]>([])
const loading = ref(false)
const saving = ref(false)
const queryKeyword = ref('')
const tagsInput = ref('')

const searchMode = ref(false)
const searchResults = ref<HrSearchResult[]>([])

const displayList = computed<Array<OpcHrCandidate & { _score?: number; _reason?: string }>>(() => {
  if (searchMode.value) {
    return searchResults.value.map((r: HrSearchResult) => ({
      ...r.candidate,
      _score: r.score,
      _reason: r.matchReason,
    }))
  }
  return candidates.value
})

const columns = computed<Column[]>(() => {
  const base: Column[] = [
    { key: 'name', label: '姓名', primary: true, minWidth: 140 },
    { key: 'email', label: '邮箱', minWidth: 200, hideOnMobile: true },
    { key: 'phone', label: '电话', width: 140 },
    {
      key: 'source',
      label: '来源',
      width: 100,
      type: 'tag',
      formatter: (v: any) =>
        ({ WEBSITE: '官网', REFERRAL: '内推', HEADHUNTER: '猎头', JOB_SITE: '招聘网站', OTHER: '其他' } as any)[
          String(v || '')
        ] || String(v || '-'),
      tagMap: { WEBSITE: 'primary', REFERRAL: 'success', HEADHUNTER: 'warning', JOB_SITE: 'info', OTHER: '' },
    },
    { key: 'createTime', label: '入库时间', type: 'date', width: 160, hideOnMobile: true },
  ]
  if (searchMode.value) {
    base.push({ key: '_score', label: '匹配度', width: 100, align: 'right' })
  }
  return base
})

const createVisible = ref(false)
const form = reactive<Partial<OpcHrCandidate>>({
  name: '',
  email: '',
  phone: '',
  resumeUrl: '',
  source: 'WEBSITE',
})

async function load() {
  loading.value = true
  searchMode.value = false
  try {
    const r = await listCandidates({ companyId })
    candidates.value = r.data || []
  } catch (e: any) {
    ElMessage.error(e?.msg || '加载失败')
  } finally {
    loading.value = false
  }
}

async function onSearch() {
  if (!queryKeyword.value.trim()) {
    resetQuery()
    return
  }
  loading.value = true
  try {
    const r = await searchCandidate({ query: queryKeyword.value.trim(), topK: 20 }, companyId)
    searchResults.value = r.data || []
    searchMode.value = true
  } catch (e: any) {
    ElMessage.error(e?.msg || '搜索失败')
    searchResults.value = []
    searchMode.value = false
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  queryKeyword.value = ''
  searchMode.value = false
  searchResults.value = []
  load()
}

function openCreate() {
  Object.assign(form, { name: '', email: '', phone: '', resumeUrl: '', source: 'WEBSITE' })
  tagsInput.value = ''
  createVisible.value = true
}

async function onCreate() {
  if (!form.name?.trim()) {
    ElMessage.warning('请输入姓名')
    return
  }
  if (!form.resumeUrl?.trim()) {
    ElMessage.warning('请填写简历 URL')
    return
  }
  saving.value = true
  try {
    const data: Partial<OpcHrCandidate> = { ...form, companyId }
    if (tagsInput.value.trim()) {
      data.tagsJson = JSON.stringify(
        tagsInput.value.split(',').map((s: string) => s.trim()).filter(Boolean),
      )
    }
    await createCandidate(data)
    ElMessage.success('已创建')
    createVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '创建失败')
  } finally {
    saving.value = false
  }
}

async function onDelete(row: OpcHrCandidate) {
  if (!row.id) return
  await ElMessageBox.confirm(`删除候选人「${row.name}」?`, '提示', { type: 'warning' }).catch(() => {})
  try {
    await deleteCandidate(row.id, companyId)
    ElMessage.success('已删除')
    if (searchMode.value) {
      onSearch()
    } else {
      load()
    }
  } catch (e: any) {
    ElMessage.error(e?.msg || '删除失败')
  }
}

function goDetail(row: OpcHrCandidate) {
  if (!row.id) return
  router.push(`/opc/hr/candidate/${row.id}`)
}

onMounted(load)
</script>

<style scoped lang="scss">
.hr-candidate-list {
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
  .search-result-banner {
    background: var(--el-color-primary-light-9, #ecf5ff);
    color: var(--el-color-primary, #409eff);
    padding: 8px 12px;
    border-radius: 6px;
    margin-bottom: 12px;
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
  }
}
</style>