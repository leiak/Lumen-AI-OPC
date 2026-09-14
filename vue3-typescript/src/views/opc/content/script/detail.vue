<template>
  <div class="page content-script-detail" v-loading="loading">
    <el-card v-if="script">
      <template #header>
        <div class="header">
          <div class="title-block">
            <span class="title">{{ script.title || '(未命名)' }}</span>
            <el-tag :type="statusTagType(script.status)" effect="plain">{{ statusLabel(script.status) }}</el-tag>
            <el-tag v-if="script.type" type="info" effect="plain">{{ typeLabel(script.type) }}</el-tag>
          </div>
          <el-button :icon="Back" @click="goBack">返回列表</el-button>
        </div>
      </template>

      <el-alert
        v-if="script.status === 'FAILED'"
        :title="`生成失败: ${(script as any).error_message || '未知错误'}`"
        type="error"
        :closable="false"
        show-icon
        class="error-alert"
      />

      <el-tabs v-model="activeTab">
        <!-- 内容 -->
        <el-tab-pane label="内容" name="content">
          <div class="meta-row">
            <span class="meta-item">字数: {{ script.word_count || 0 }}</span>
            <span class="meta-item">更新: {{ script.updated_at || '-' }}</span>
            <span class="meta-item">创建: {{ script.created_at || '-' }}</span>
          </div>
          <el-input
            :model-value="bodyText"
            type="textarea"
            :rows="14"
            readonly
            placeholder="(无内容)"
            class="body-input"
          />
          <div class="action-row">
            <el-button type="primary" :icon="MagicStick" @click="openRefineDialog">AI 精修</el-button>
            <el-button
              v-if="script.status === 'DRAFT'"
              type="success"
              @click="onMarkReady"
              :loading="readyLoading"
            >标记就绪</el-button>
            <el-button
              v-if="script.status === 'DRAFT'"
              type="danger"
              @click="onDelete"
            >删除</el-button>
            <el-button
              v-if="script.status === 'READY' || script.status === 'PUBLISHED'"
              type="warning"
              @click="openPublishDialog"
            >发布</el-button>
          </div>
        </el-tab-pane>

        <!-- 适配历史 -->
        <el-tab-pane label="适配" name="adapt">
          <div class="tab-actions">
            <el-button type="primary" :icon="Plus" @click="goAdapt">新增适配</el-button>
          </div>
          <el-table :data="adaptList" stripe empty-text="暂无适配记录(原脚本不会出现在这里,适配创建新脚本)">
            <el-table-column prop="id" label="新脚本 ID" width="100" />
            <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="statusTagType(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="$router.push(`/opc/content/script/${row.id}`)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 发布历史 -->
        <el-tab-pane :label="`发布历史 (${publishes.length})`" name="publish">
          <div class="tab-actions">
            <el-button type="primary" :icon="Upload" @click="openPublishDialog">新增发布</el-button>
          </div>
          <el-table :data="publishes" stripe empty-text="暂无发布记录">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="platform" label="平台" width="100">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">{{ row.platform || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="publishTagType(row.status)" effect="plain">{{ publishLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="external_url" label="外部链接" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">
                <a v-if="row.external_url" :href="row.external_url" target="_blank">{{ row.external_url }}</a>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="error_message" label="错误" min-width="180" show-overflow-tooltip />
            <el-table-column prop="published_at" label="发布时间" width="170" />
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'FAILED'"
                  link
                  type="warning"
                  size="small"
                  @click="onRetryPublish(row)"
                >重试</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- AI 精修对话框 -->
    <el-dialog v-model="refineVisible" title="AI 精修" width="560px">
      <el-form :model="refineForm" label-width="100px">
        <el-form-item label="行号" required>
          <el-input-number v-model="refineForm.line_no" :min="1" :max="9999" style="width: 100%" />
        </el-form-item>
        <el-form-item label="修改指令" required>
          <el-input v-model="refineForm.instruction" type="textarea" :rows="3" placeholder="例如:把第 5 行改得更口语化,加入'姐妹们'的称呼" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="refineVisible = false">取消</el-button>
        <el-button type="primary" :loading="refineLoading" @click="onRefine">精修</el-button>
      </template>
    </el-dialog>

    <!-- 发布对话框 -->
    <el-dialog v-model="publishVisible" title="发布脚本" width="560px">
      <el-form :model="publishForm" label-width="100px">
        <el-form-item label="平台账号" required>
          <el-select v-model="publishForm.platform_account_id" placeholder="选择已绑定的平台账号" style="width: 100%">
            <el-option
              v-for="a in accounts"
              :key="a.id"
              :label="`${a.nickname || a.open_id || '#' + a.id} (${a.platform})`"
              :value="a.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="publishForm.title" placeholder="发布标题" />
        </el-form-item>
        <el-form-item label="话题标签">
          <el-input v-model="publishForm.tagsText" placeholder="逗号分隔:护肤,种草" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="publishVisible = false">取消</el-button>
        <el-button type="primary" :loading="publishLoading" @click="onPublish">发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Back, MagicStick, Plus, Upload } from '@element-plus/icons-vue'
import {
  getScript,
  refineScript,
  markScriptReady,
  deleteScript,
  getScriptPublishHistory,
  publishScript,
  retryPublish,
  listPlatformAccounts,
  type OpcContentScript,
  type OpcContentPublish,
  type OpcContentPlatformAccount,
} from '@/api/opc/content'

const route = useRoute()
const router = useRouter()
const companyId = 1

const script = ref<OpcContentScript | null>(null)
const loading = ref(false)
const activeTab = ref('content')

const publishes = ref<OpcContentPublish[]>([])
const accounts = ref<OpcContentPlatformAccount[]>([])
const adaptList = ref<OpcContentScript[]>([])

const readyLoading = ref(false)

const refineVisible = ref(false)
const refineLoading = ref(false)
const refineForm = reactive({ line_no: 1, instruction: '' })

const publishVisible = ref(false)
const publishLoading = ref(false)
const publishForm = reactive<{ platform_account_id?: number; title: string; tagsText: string }>({
  platform_account_id: undefined,
  title: '',
  tagsText: '',
})

const bodyText = computed(() => {
  const s = script.value
  if (!s) return ''
  if (s.content_md) return s.content_md
  if (s.content_json) {
    try {
      return JSON.stringify(JSON.parse(s.content_json), null, 2)
    } catch {
      return s.content_json
    }
  }
  return ''
})

async function loadScript() {
  loading.value = true
  try {
    const r = await getScript(Number(route.params.id), companyId)
    script.value = r.data || null
    if (script.value && !publishForm.title) {
      publishForm.title = script.value.title || ''
    }
  } catch (e: any) {
    ElMessage.error(e?.msg || '加载脚本失败')
  } finally {
    loading.value = false
  }
}

async function loadPublishes() {
  try {
    const r = await getScriptPublishHistory(Number(route.params.id), companyId)
    publishes.value = r.data || []
  } catch (e: any) {
    publishes.value = []
  }
}

async function loadAccounts() {
  try {
    const r = await listPlatformAccounts(companyId)
    accounts.value = r.data || []
  } catch {
    accounts.value = []
  }
}

function goBack() {
  router.push('/opc/content/script')
}

function goAdapt() {
  if (!script.value?.id) return
  router.push(`/opc/content/script/${script.value.id}/adapt`)
}

function openRefineDialog() {
  refineForm.line_no = 1
  refineForm.instruction = ''
  refineVisible.value = true
}

async function onRefine() {
  if (!script.value?.id) return
  if (!refineForm.instruction.trim()) {
    ElMessage.warning('请输入修改指令')
    return
  }
  refineLoading.value = true
  try {
    await refineScript(script.value.id, companyId, {
      line_no: refineForm.line_no,
      instruction: refineForm.instruction.trim(),
    })
    ElMessage.success('已应用精修')
    refineVisible.value = false
    loadScript()
  } catch (e: any) {
    ElMessage.error(e?.msg || '精修失败')
  } finally {
    refineLoading.value = false
  }
}

async function onMarkReady() {
  if (!script.value?.id) return
  readyLoading.value = true
  try {
    await markScriptReady(script.value.id, companyId)
    ElMessage.success('已标记为就绪')
    loadScript()
  } catch (e: any) {
    ElMessage.error(e?.msg || '标记失败')
  } finally {
    readyLoading.value = false
  }
}

async function onDelete() {
  if (!script.value?.id) return
  await ElMessageBox.confirm('确认删除此脚本?(仅 DRAFT 可删)', '提示', { type: 'warning' }).catch(() => {})
  try {
    await deleteScript(script.value.id, companyId)
    ElMessage.success('已删除')
    router.push('/opc/content/script')
  } catch (e: any) {
    ElMessage.error(e?.msg || '删除失败')
  }
}

function openPublishDialog() {
  if (!accounts.value.length) {
    ElMessage.warning('请先到「平台账号」页绑定账号')
    return
  }
  publishForm.platform_account_id = accounts.value[0]?.id
  publishForm.title = script.value?.title || ''
  publishForm.tagsText = ''
  publishVisible.value = true
}

async function onPublish() {
  if (!script.value?.id || !publishForm.platform_account_id) {
    ElMessage.warning('请选择平台账号')
    return
  }
  if (!publishForm.title.trim()) {
    ElMessage.warning('请输入发布标题')
    return
  }
  publishLoading.value = true
  try {
    const tags = publishForm.tagsText
      ? publishForm.tagsText.split(',').map((t) => t.trim()).filter(Boolean)
      : undefined
    await publishScript({
      company_id: companyId,
      script_id: script.value.id,
      platform_account_id: publishForm.platform_account_id,
      title: publishForm.title.trim(),
      tags,
    })
    ElMessage.success('已加入发布队列')
    publishVisible.value = false
    activeTab.value = 'publish'
    loadPublishes()
  } catch (e: any) {
    ElMessage.error(e?.msg || '发布失败')
  } finally {
    publishLoading.value = false
  }
}

async function onRetryPublish(row: OpcContentPublish) {
  if (!row.id) return
  try {
    await retryPublish(row.id, companyId)
    ElMessage.success('已重试')
    loadPublishes()
  } catch (e: any) {
    ElMessage.error(e?.msg || '重试失败')
  }
}

function statusLabel(s?: string) {
  return ({ DRAFT: '草稿', READY: '就绪', PUBLISHED: '已发布', FAILED: '失败', DELETED: '已删除' } as any)[String(s || '')] || String(s || '-')
}
function statusTagType(s?: string) {
  return ({ DRAFT: 'info', READY: 'success', PUBLISHED: '', FAILED: 'danger', DELETED: 'danger' } as any)[String(s || '')] || ''
}
function typeLabel(t?: string) {
  return ({ DRAMA: '短剧', VIDEO: '视频', ARTICLE: '图文', ADAPTER: '适配' } as any)[String(t || '')] || String(t || '-')
}
function publishLabel(s?: string) {
  return ({ PENDING: '待发布', SUCCESS: '成功', FAILED: '失败' } as any)[String(s || '')] || String(s || '-')
}
function publishTagType(s?: string) {
  return ({ PENDING: 'warning', SUCCESS: 'success', FAILED: 'danger' } as any)[String(s || '')] || ''
}

onMounted(() => {
  loadScript()
  loadPublishes()
  loadAccounts()
})
</script>

<style scoped lang="scss">
.content-script-detail {
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
    font-weight: 600;
    font-size: 16px;
  }
  .error-alert {
    margin-bottom: 16px;
  }
  .meta-row {
    display: flex;
    flex-wrap: wrap;
    gap: 16px;
    color: var(--el-text-color-secondary, #606266);
    font-size: 13px;
    margin-bottom: 12px;
  }
  .body-input {
    margin-bottom: 16px;
  }
  .action-row {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }
  .tab-actions {
    display: flex;
    justify-content: flex-end;
    margin-bottom: 12px;
  }
}
</style>