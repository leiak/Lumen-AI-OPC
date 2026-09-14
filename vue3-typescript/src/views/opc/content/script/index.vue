<template>
  <div class="page content-script-list">
    <el-card>
      <template #header>
        <div class="header">
          <span class="title">
            <el-icon><Document /></el-icon>
            AI 脚本管理
          </span>
          <div class="actions">
            <el-select v-model="typeFilter" placeholder="类型" clearable style="width: 130px" @change="load">
              <el-option label="短剧" value="DRAMA" />
              <el-option label="视频" value="VIDEO" />
              <el-option label="图文" value="ARTICLE" />
              <el-option label="适配" value="ADAPTER" />
            </el-select>
            <el-select v-model="statusFilter" placeholder="状态" clearable style="width: 130px" @change="load">
              <el-option label="草稿" value="DRAFT" />
              <el-option label="就绪" value="READY" />
              <el-option label="已发布" value="PUBLISHED" />
              <el-option label="失败" value="FAILED" />
            </el-select>
            <el-input v-model="titleFilter" placeholder="标题关键词" clearable style="width: 200px" @keyup.enter="load" />
            <el-button :icon="Refresh" @click="load">刷新</el-button>
            <el-button type="success" :icon="MagicStick" @click="openGenerateDialog">AI 生成</el-button>
          </div>
        </div>
      </template>

      <ResponsiveTable
        :data="list"
        :loading="loading"
        :columns="columns"
        :action-width="280"
        empty-text="暂无脚本"
      >
        <template #actions="{ row }">
          <el-button link type="primary" size="small" @click="goDetail(row)">查看</el-button>
          <el-button link type="success" size="small" @click="openRegenerateDialog(row)">重新生成</el-button>
          <el-button link type="warning" size="small" @click="goAdapt(row)">适配</el-button>
          <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
        </template>
      </ResponsiveTable>

      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="load"
          @size-change="load"
        />
      </div>
    </el-card>

    <!-- AI 生成对话框 -->
    <el-dialog v-model="generateVisible" title="AI 生成脚本" width="560px">
      <el-form :model="generateForm" label-width="80px">
        <el-form-item label="类型" required>
          <el-select v-model="generateForm.type" style="width: 100%">
            <el-option label="短剧" value="DRAMA" />
            <el-option label="视频" value="VIDEO" />
            <el-option label="图文" value="ARTICLE" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="generateForm.title" placeholder="可选,留空由 AI 生成" />
        </el-form-item>
        <el-form-item label="提示词" required>
          <el-input v-model="generateForm.prompt_input" type="textarea" :rows="4" placeholder="例如:生成一个 60 秒的护肤品种草视频脚本,面向都市女性,口吻活泼" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="generateVisible = false">取消</el-button>
        <el-button type="primary" :loading="generating" @click="onGenerate">生成</el-button>
      </template>
    </el-dialog>

    <!-- 重新生成对话框 -->
    <el-dialog v-model="regenVisible" title="重新生成内容" width="560px">
      <el-form :model="regenForm" label-width="80px">
        <el-form-item label="新提示词" required>
          <el-input v-model="regenForm.prompt_input" type="textarea" :rows="4" placeholder="基于原脚本调整的方向/风格" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="regenVisible = false">取消</el-button>
        <el-button type="primary" :loading="regenLoading" @click="onRegenerate">重新生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document, Plus, Refresh, MagicStick } from '@element-plus/icons-vue'
import {
  listScripts,
  createScript,
  regenerateScript,
  deleteScript,
  type OpcContentScript,
  type ContentScriptType,
} from '@/api/opc/content'
import ResponsiveTable, { type Column } from '@/views/opc/components/ResponsiveTable.vue'

const router = useRouter()
const companyId = 1

const list = ref<OpcContentScript[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

const typeFilter = ref<ContentScriptType | ''>('')
const statusFilter = ref('')
const titleFilter = ref('')

const generateVisible = ref(false)
const generating = ref(false)
const generateForm = reactive<{ type: ContentScriptType; title: string; prompt_input: string }>({
  type: 'VIDEO',
  title: '',
  prompt_input: '',
})

const regenVisible = ref(false)
const regenLoading = ref(false)
const regenTarget = ref<OpcContentScript | null>(null)
const regenForm = reactive({ prompt_input: '' })

const columns: Column[] = [
  { key: 'id', label: 'ID', width: 70 },
  { key: 'title', label: '标题', primary: true, minWidth: 180 },
  {
    key: 'type',
    label: '类型',
    width: 90,
    type: 'tag',
    formatter: (v: any) =>
      ({ DRAMA: '短剧', VIDEO: '视频', ARTICLE: '图文', ADAPTER: '适配' } as any)[String(v || '')] || String(v || '-'),
    tagMap: { DRAMA: 'primary', VIDEO: 'success', ARTICLE: 'info', ADAPTER: 'warning' },
  },
  {
    key: 'status',
    label: '状态',
    width: 90,
    type: 'tag',
    formatter: (v: any) =>
      ({ DRAFT: '草稿', READY: '就绪', PUBLISHED: '已发布', FAILED: '失败', DELETED: '已删除' } as any)[String(v || '')] || String(v || '-'),
    tagMap: { DRAFT: 'info', READY: 'success', PUBLISHED: '', FAILED: 'danger', DELETED: 'danger' },
  },
  { key: 'word_count', label: '字数', width: 80, type: 'number', align: 'right' },
  { key: 'updated_at', label: '更新时间', width: 170, hideOnMobile: true },
]

async function load() {
  loading.value = true
  try {
    const r = await listScripts({
      companyId,
      type: (typeFilter.value || undefined) as ContentScriptType | undefined,
      status: (statusFilter.value || undefined) as any,
      page: page.value,
      size: pageSize.value,
    })
    const payload = r.data || { rows: [], total: 0 }
    let rows = payload.rows || []
    if (titleFilter.value.trim()) {
      const kw = titleFilter.value.trim().toLowerCase()
      rows = rows.filter((s) => String(s.title || '').toLowerCase().includes(kw))
    }
    list.value = rows
    total.value = titleFilter.value.trim() ? rows.length : Number(payload.total || 0)
  } catch (e: any) {
    ElMessage.error(e?.msg || '加载脚本失败')
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function openGenerateDialog() {
  Object.assign(generateForm, { type: 'VIDEO', title: '', prompt_input: '' })
  generateVisible.value = true
}

async function onGenerate() {
  if (!generateForm.prompt_input.trim()) {
    ElMessage.warning('请输入提示词')
    return
  }
  generating.value = true
  try {
    const r = await createScript({
      company_id: companyId,
      type: generateForm.type,
      title: generateForm.title || undefined,
      prompt_input: generateForm.prompt_input.trim(),
    })
    ElMessage.success(`已生成脚本 #${r.data}`)
    generateVisible.value = false
    page.value = 1
    load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '生成失败')
  } finally {
    generating.value = false
  }
}

function openRegenerateDialog(row: OpcContentScript) {
  regenTarget.value = row
  regenForm.prompt_input = ''
  regenVisible.value = true
}

async function onRegenerate() {
  if (!regenTarget.value?.id || !regenForm.prompt_input.trim()) {
    ElMessage.warning('请输入新提示词')
    return
  }
  regenLoading.value = true
  try {
    await regenerateScript(regenTarget.value.id, companyId, { prompt_input: regenForm.prompt_input.trim() })
    ElMessage.success('已重新生成')
    regenVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '重新生成失败')
  } finally {
    regenLoading.value = false
  }
}

function goDetail(row: OpcContentScript) {
  if (!row.id) return
  router.push(`/opc/content/script/${row.id}`)
}

function goAdapt(row: OpcContentScript) {
  if (!row.id) return
  router.push(`/opc/content/script/${row.id}/adapt`)
}

async function onDelete(row: OpcContentScript) {
  if (!row.id) return
  await ElMessageBox.confirm(`确认删除脚本「${row.title}」？此操作不可恢复`, '提示', { type: 'warning' }).catch(() => {})
  try {
    await deleteScript(row.id, companyId)
    ElMessage.success('已删除')
    load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '删除失败')
  }
}

onMounted(load)
</script>

<style scoped lang="scss">
.content-script-list {
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
    flex-wrap: wrap;
    gap: 8px;
    align-items: center;
  }
  .pager {
    display: flex;
    justify-content: flex-end;
    margin-top: 12px;
  }
}
</style>