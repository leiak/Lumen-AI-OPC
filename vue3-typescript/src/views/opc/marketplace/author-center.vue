<template>
  <div class="page author-center">
    <!-- 作者信息头 -->
    <el-card v-loading="authorLoading" class="author-hero">
      <div class="author-head">
        <el-avatar :size="64" :src="author?.avatarUrl" />
        <div class="author-meta">
          <h2>{{ author?.authorName || '未入驻作者' }}</h2>
          <div class="badges">
            <el-tag size="small" :type="levelTagType">{{ levelLabel }}</el-tag>
            <el-tag size="small" type="info">{{ statusLabel }}</el-tag>
          </div>
          <p class="intro">{{ author?.intro || '还没有填写作者简介' }}</p>
        </div>
        <div class="author-actions">
          <el-button type="primary" @click="activeTab = 'create'">
            <el-icon><Plus /></el-icon>&nbsp;新建商品
          </el-button>
        </div>
      </div>

      <el-alert
        v-if="!authorLoading && !author"
        type="warning"
        :closable="false"
        show-icon
        title="你还不是市场作者"
        description="作者才能上架商品、查看数据看板。提交入驻申请后即可开始发布。"
      >
        <el-button type="warning" size="small" @click="router.push('/opc/marketplace/author-apply')">
          去申请
        </el-button>
      </el-alert>
    </el-card>

    <el-card class="tabs-card">
      <el-tabs v-model="activeTab">
        <!-- 我的作品 -->
        <el-tab-pane label="我的作品" name="works">
          <ResponsiveTable
            :data="myPubs"
            :columns="columns"
            :loading="worksLoading"
            row-key="id"
            empty-text="还没有上架任何商品"
            :action-width="260"
          >
            <template #actions="{ row }">
              <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
              <el-button
                v-if="row.status === 'DRAFT'"
                link
                type="success"
                size="small"
                @click="onSubmitAudit(row)"
              >
                提交审核
              </el-button>
              <el-button
                v-if="row.status === 'DRAFT' || row.status === 'DEPRECATED'"
                link
                type="warning"
                size="small"
                @click="onOnline(row)"
              >
                上架
              </el-button>
              <el-button
                v-if="row.status === 'PUBLISHED'"
                link
                type="danger"
                size="small"
                @click="onOffline(row)"
              >
                下架
              </el-button>
              <el-button link type="info" size="small" @click="preview(row)">预览</el-button>
            </template>
          </ResponsiveTable>
        </el-tab-pane>

        <!-- 新建商品 -->
        <el-tab-pane label="新建商品" name="create">
          <el-form ref="createRef" :model="createForm" :rules="createRules" label-width="110px">
            <el-row :gutter="16">
              <el-col :xs="24" :md="12">
                <el-form-item label="商品类型" prop="productType">
                  <el-radio-group v-model="createForm.productType" @change="onTypeChange">
                    <el-radio-button label="AGENT">智能体</el-radio-button>
                    <el-radio-button label="MODULE">功能模块</el-radio-button>
                  </el-radio-group>
                </el-form-item>
              </el-col>
              <el-col :xs="24" :md="12">
                <el-form-item :label="createForm.productType === 'AGENT' ? '智能体定义 ID' : '模块 ID'" :prop="refIdProp">
                  <el-input-number
                    v-model="createRefId"
                    :min="1"
                    :step="1"
                    controls-position="right"
                    style="width: 100%"
                    :placeholder="createForm.productType === 'AGENT' ? 'opc_agent_definition.id' : 'opc_community_module.id'"
                  />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="16">
              <el-col :xs="24" :md="12">
                <el-form-item label="商品编码" prop="code">
                  <el-input v-model="createForm.code" maxlength="64" placeholder="唯一英文标识，如 crm-lead-agent" />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :md="12">
                <el-form-item label="商品名称" prop="name">
                  <el-input v-model="createForm.name" maxlength="128" placeholder="展示名称" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="16">
              <el-col :xs="24" :md="12">
                <el-form-item label="类目" prop="categoryCode">
                  <el-select v-model="createForm.categoryCode" placeholder="选择类目" style="width: 100%">
                    <el-option v-for="c in typeCategories" :key="c.code" :label="c.name" :value="c.code" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :xs="24" :md="12">
                <el-form-item label="子类目">
                  <el-input v-model="createForm.subCategoryCode" maxlength="32" placeholder="选填" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-form-item label="图标 URL">
              <el-input v-model="createForm.iconUrl" maxlength="512" placeholder="选填，建议 1:1 正方形" />
            </el-form-item>

            <el-form-item label="简介">
              <el-input
                v-model="createForm.summary"
                type="textarea"
                :rows="2"
                maxlength="512"
                show-word-limit
                placeholder="一句话说明这个商品能做什么"
              />
            </el-form-item>

            <el-form-item label="详细介绍">
              <el-input v-model="createForm.longDescription" type="textarea" :rows="6" placeholder="功能说明、使用场景、限制条件…" />
            </el-form-item>

            <el-form-item label="标签">
              <el-input v-model="createForm.tags" maxlength="512" placeholder="逗号分隔，如 销售,线索,自动化" />
            </el-form-item>

            <div class="form-footer">
              <el-button @click="resetCreate">重置</el-button>
              <el-button type="primary" :loading="creating" @click="onCreate">保存草稿</el-button>
            </div>
          </el-form>
        </el-tab-pane>

        <!-- 数据看板 -->
        <el-tab-pane label="数据看板" name="stats">
          <div v-loading="statsLoading" class="stats">
            <el-empty v-if="!statsLoading && !stats" description="暂无数据" />
            <template v-if="stats">
              <div class="stat-strip">
                <div class="stat-cell">
                  <div class="num">{{ stats.publicationCount || 0 }}</div>
                  <div class="lbl">在售商品数</div>
                </div>
                <div class="stat-cell">
                  <div class="num">{{ myPubs.length }}</div>
                  <div class="lbl">全部商品数</div>
                </div>
                <div class="stat-cell">
                  <div class="num">{{ totalInstalls }}</div>
                  <div class="lbl">累计安装</div>
                </div>
                <div class="stat-cell">
                  <div class="num">{{ totalRating }}</div>
                  <div class="lbl">累计评价</div>
                </div>
              </div>

              <el-descriptions :column="1" border class="stats-desc">
                <el-descriptions-item label="作者名称">{{ stats.author?.authorName || '—' }}</el-descriptions-item>
                <el-descriptions-item label="作者等级">{{ levelLabel }}</el-descriptions-item>
                <el-descriptions-item label="账号状态">{{ statusLabel }}</el-descriptions-item>
                <el-descriptions-item label="入驻时间">{{ stats.author?.createTime || '—' }}</el-descriptions-item>
              </el-descriptions>
            </template>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="editDialog" title="编辑商品" width="640px" :close-on-click-modal="false">
      <el-form ref="editRef" :model="editForm" :rules="editRules" label-width="110px">
        <el-form-item label="商品名称" prop="name">
          <el-input v-model="editForm.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="类目" prop="categoryCode">
          <el-select v-model="editForm.categoryCode" style="width: 100%">
            <el-option v-for="c in categories" :key="c.code" :label="c.name" :value="c.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="子类目">
          <el-input v-model="editForm.subCategoryCode" maxlength="32" />
        </el-form-item>
        <el-form-item label="图标 URL">
          <el-input v-model="editForm.iconUrl" maxlength="512" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="editForm.summary" type="textarea" :rows="2" maxlength="512" show-word-limit />
        </el-form-item>
        <el-form-item label="详细介绍">
          <el-input v-model="editForm.longDescription" type="textarea" :rows="5" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="editForm.tags" maxlength="512" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSaveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import ResponsiveTable, { type Column } from '@/views/opc/components/ResponsiveTable.vue'
import {
  getMyAuthor,
  myPublications,
  myPublication,
  createPublication,
  editPublication,
  submitAudit,
  onlinePublication,
  offlinePublication,
  getAuthorStats,
  listCategories,
  type PublicationVO,
  type AuthorVO,
  type AuthorStats,
  type CategoryVO,
  type PublicationCreatePayload
} from '@/api/opc/marketplace'

const router = useRouter()

const activeTab = ref('works')

const authorLoading = ref(false)
const author = ref<AuthorVO | null>(null)

const worksLoading = ref(false)
const myPubs = ref<PublicationVO[]>([])

const categories = ref<CategoryVO[]>([])

const statsLoading = ref(false)
const stats = ref<AuthorStats | null>(null)

// ---------- 计算属性 ----------
const levelLabel = computed(() => (author.value?.level === 'PREMIUM' ? '优质作者' : '普通作者'))
const levelTagType = computed(() => (author.value?.level === 'PREMIUM' ? 'success' : 'info'))
/**
 * 作者状态标签（P1 下只有 ACTIVE 可写，见 AuthorServiceImpl#apply 的 setStatus("ACTIVE")）。
 * 之前是 `status === 'ACTIVE' ? '正常' : status || '—'` —— 任何非 ACTIVE 的值都会把裸枚举
 * （比如 SUSPENDED）直接显示在徽章上，等于把内部状态码漏到界面上。
 *
 * 与 detail.vue 的 statusLabel 是同一个套路，唯一的区别是这里的 default 不回显原值：
 * mp_author.status 是无约束的 VARCHAR(16)，出现没预料到的值时显示「未知」，
 * 比把内部码直接甩到用户脸上好。真出了新状态，在这里补一条即可。
 */
function authorStatusLabel(s?: string) {
  switch (s) {
    case 'ACTIVE': return '正常'
    case 'SUSPENDED': return '已封禁'
    default: return s ? '未知' : '—'
  }
}
const statusLabel = computed(() => authorStatusLabel(author.value?.status))
const typeCategories = computed(() => categories.value.filter((c) => c.productType === createForm.productType))
const refIdProp = computed(() => (createForm.productType === 'AGENT' ? 'agentDefinitionId' : 'moduleId') as any)
const totalInstalls = computed(() => myPubs.value.reduce((s, p) => s + (p.installCount || 0), 0))
const totalRating = computed(() => myPubs.value.reduce((s, p) => s + (p.ratingCount || 0), 0))

const columns: Column[] = [
  { key: 'name', label: '商品名称', primary: true, minWidth: 180 },
  { key: 'code', label: '编码', minWidth: 160 },
  { key: 'categoryCode', label: '类目', width: 120 },
  {
    key: 'status',
    label: '状态',
    width: 100,
    type: 'tag',
    // 与后端 PublicationStatus 枚举一一对应（DRAFT / PUBLISHED / DEPRECATED 为 P1 可达）
    tagMap: {
      DRAFT: 'info',
      PUBLISHED: 'success',
      DEPRECATED: 'warning',
      PENDING: 'warning',
      SUSPENDED: 'danger'
    }
  },
  { key: 'installCount', label: '安装数', width: 90, type: 'number', align: 'right' },
  { key: 'ratingCount', label: '评价数', width: 90, type: 'number', align: 'right' },
  { key: 'createTime', label: '创建时间', width: 170, type: 'date', hideOnMobile: true }
]

// ---------- 新建表单 ----------
const createRef = ref<FormInstance>()
const creating = ref(false)

const createRefId = ref<number | undefined>(undefined)
const createForm = reactive<PublicationCreatePayload>({
  productType: 'AGENT',
  agentDefinitionId: undefined,
  moduleId: undefined,
  code: '',
  name: '',
  categoryCode: '',
  subCategoryCode: '',
  summary: '',
  longDescription: '',
  iconUrl: '',
  tags: ''
})

const createRules: FormRules = {
  productType: [{ required: true, message: '请选择商品类型', trigger: 'change' }],
  code: [{ required: true, message: '商品编码必填', trigger: 'blur' }],
  name: [{ required: true, message: '商品名称必填', trigger: 'blur' }],
  categoryCode: [{ required: true, message: '请选择类目', trigger: 'change' }]
}

// ---------- 编辑表单 ----------
const editRef = ref<FormInstance>()
const editDialog = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const editForm = reactive({
  name: '',
  categoryCode: '',
  subCategoryCode: '',
  summary: '',
  longDescription: '',
  iconUrl: '',
  tags: ''
})
const editRules: FormRules = {
  name: [{ required: true, message: '商品名称必填', trigger: 'blur' }],
  categoryCode: [{ required: true, message: '请选择类目', trigger: 'change' }]
}

// ---------- 数据加载 ----------
async function loadAuthor() {
  authorLoading.value = true
  try {
    // 未入驻时后端 R.ok(null)，但本模块开了 default-property-inclusion: non_null，
    // 响应里**没有 data 键**，res.data 是 undefined —— 不是 404，别当错误弹
    const res = await getMyAuthor()
    author.value = res.data || null
  } catch {
    author.value = null
    ElMessage.error('加载作者信息失败')
  } finally {
    authorLoading.value = false
  }
}

async function loadWorks() {
  worksLoading.value = true
  try {
    // /my/list 返回作者全部状态的商品（DRAFT / PUBLISHED / DEPRECATED 都在内）
    const res = await myPublications()
    myPubs.value = res.data || []
  } catch {
    myPubs.value = []
    ElMessage.error('加载我的商品失败')
  } finally {
    worksLoading.value = false
  }
}

async function loadCategories() {
  try {
    const res = await listCategories()
    categories.value = res.data || []
  } catch {
    categories.value = []
  }
}

async function loadStats() {
  if (!author.value?.id) {
    stats.value = null
    return
  }
  statsLoading.value = true
  try {
    const res = await getAuthorStats(author.value.id)
    stats.value = res.data || null
  } catch {
    stats.value = null
    ElMessage.error('加载数据看板失败')
  } finally {
    statsLoading.value = false
  }
}

// ---------- 新建 ----------
function onTypeChange() {
  createRefId.value = undefined
  createForm.agentDefinitionId = undefined
  createForm.moduleId = undefined
  // 类目随商品类型收窄，旧的选中值可能已失效
  if (createForm.categoryCode && !typeCategories.value.some((c) => c.code === createForm.categoryCode)) {
    createForm.categoryCode = ''
  }
}

async function onCreate() {
  if (!createRef.value) return
  const ok = await createRef.value.validate().catch(() => false)
  if (!ok) return
  if (!createRefId.value) {
    ElMessage.warning(createForm.productType === 'AGENT' ? '请填写智能体定义 ID' : '请填写模块 ID')
    return
  }
  creating.value = true
  try {
    const payload: PublicationCreatePayload = {
      ...createForm,
      agentDefinitionId: createForm.productType === 'AGENT' ? createRefId.value : undefined,
      moduleId: createForm.productType === 'MODULE' ? createRefId.value : undefined
    }
    const res = await createPublication(payload)
    ElMessage.success('草稿已创建（提交审核或上架后才会出现在市场里）')
    resetCreate()
    // 切 tab 会触发 watch(activeTab) 里的 loadWorks()，这里不要再显式调一次 ——
    // 两次并发请求同一份数据，既浪费又会让表格闪两次 loading。
    // ⚠️ 此处依赖一个隐式耦合:**当前**「新建商品」表单只在 `create` tab 上渲染,
    // 所以 activeTab.value 一定真切到 works,watch 才会拉一次新的作品列表。
    // 若日后把新建表单搬到 works/stats tab 内(例如弹窗/drawer、就地创建并保留当前 tab),
    // 这里会把用户悄悄切走 —— 必须改成显式调用 loadWorks() 并去掉 activeTab 赋值。
    activeTab.value = 'works'
    await loadStats()
    const created = res.data
    if (created?.id) openEditById(created.id)
  } catch {
    ElMessage.error('创建失败')
  } finally {
    creating.value = false
  }
}

function resetCreate() {
  createForm.code = ''
  createForm.name = ''
  createForm.categoryCode = ''
  createForm.subCategoryCode = ''
  createForm.summary = ''
  createForm.longDescription = ''
  createForm.iconUrl = ''
  createForm.tags = ''
  createRefId.value = undefined
  createForm.agentDefinitionId = undefined
  createForm.moduleId = undefined
  createRef.value?.clearValidate()
}

// ---------- 编辑 ----------
/**
 * ⚠️ 载入编辑表单时**必须**调用 `myPublication(id)`（GET /publication/my/{id}），
 * 绝不能用 `getPublication(id)`（GET /publication/{id}）。
 *
 * 原因：`GET /publication/{id}` 是公开端点（网关白名单允许匿名），
 * service 层走 `getPublishedById` → `selectPublishedById`，SQL 硬编码
 * `status='PUBLISHED'`。DRAFT / PENDING / SUSPENDED 的商品调它一律 404，
 * 编辑弹窗会永远打不开。DRAFT 只能通过 `/my/{id}` 读取（该端点不做
 * PUBLISHED 过滤，并校验 authorId 归属，非本人 403）。
 *
 * 列表数据已经在 `myPublications()` 里，本可直接复用；但如果后台被别人改过
 * （或列表加载于改动之前），重新拉一次能拿到最新值，所以这里始终走 myPublication()。
 */
async function openEditById(id: number) {
  try {
    const res = await myPublication(id)
    const p = res.data
    if (!p) return
    editingId.value = p.id
    editForm.name = p.name || ''
    editForm.categoryCode = p.categoryCode || ''
    editForm.subCategoryCode = p.subCategoryCode || ''
    editForm.summary = p.summary || ''
    editForm.longDescription = p.longDescription || ''
    editForm.iconUrl = p.iconUrl || ''
    editForm.tags = p.tags || ''
    editDialog.value = true
  } catch {
    ElMessage.error('加载商品草稿失败')
  }
}

function openEdit(row: PublicationVO) {
  if (!row.id) return
  openEditById(row.id)
}

async function onSaveEdit() {
  if (!editRef.value) return
  const ok = await editRef.value.validate().catch(() => false)
  if (!ok) return
  if (!editingId.value) return
  saving.value = true
  try {
    await editPublication(editingId.value, { ...editForm })
    ElMessage.success('已保存')
    editDialog.value = false
    await loadWorks()
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

// ---------- 状态机操作 ----------
// DRAFT ──submit-audit|online──▶ PUBLISHED ──offline──▶ DEPRECATED ──online──▶ PUBLISHED
async function onSubmitAudit(row: PublicationVO) {
  if (!row.id) return
  try {
    await submitAudit(row.id)
    ElMessage.success('已提交审核（P1 提交即自动过审）')
    await loadWorks()
    await loadStats()
  } catch {
    ElMessage.error('提交审核失败')
  }
}

async function onOnline(row: PublicationVO) {
  if (!row.id) return
  try {
    await onlinePublication(row.id)
    ElMessage.success('已上架')
    await loadWorks()
    await loadStats()
  } catch {
    ElMessage.error('上架失败')
  }
}

async function onOffline(row: PublicationVO) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm(`确定下架「${row.name}」？下架后市场访客将无法看到。`, '下架确认', {
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await offlinePublication(row.id)
    ElMessage.success('已下架')
    await loadWorks()
    await loadStats()
  } catch {
    ElMessage.error('下架失败')
  }
}

function preview(row: PublicationVO) {
  if (!row.id) return
  // 预览走公开详情页 —— DRAFT 在那里必然 404（见 openEditById 的注释），
  // 所以对非 PUBLISHED 商品直接提示，不做无意义的跳转。
  if (row.status !== 'PUBLISHED') {
    ElMessage.warning('只有已发布的商品才能在市场预览，先上架再查看')
    return
  }
  router.push({ name: 'OpcMarketplaceDetail', params: { id: String(row.id) } })
}

watch(activeTab, (t) => {
  if (t === 'stats') loadStats()
  if (t === 'works') loadWorks()
})

onMounted(() => {
  loadCategories()
  loadAuthor().then(() => {
    loadWorks()
    loadStats()
  })
})
</script>

<style scoped lang="scss">
$bp-sm: 768px;

.author-center {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.author-head {
  display: flex;
  align-items: center;
  gap: 16px;
}

.author-meta {
  flex: 1;
  min-width: 0;

  h2 {
    margin: 0 0 6px;
    font-size: 20px;
  }
}

.badges {
  display: flex;
  gap: 6px;
  margin-bottom: 6px;
}

.intro {
  margin: 0;
  font-size: 13px;
  color: #6b7280;
}

.tabs-card {
  :deep(.el-card__body) {
    padding: 8px 16px 16px;
  }
}

.stats {
  min-height: 80px;
}

.stat-strip {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;

  .stat-cell {
    background: #f9fafb;
    border-radius: 8px;
    padding: 16px 8px;
    text-align: center;

    .num {
      font-size: 24px;
      font-weight: 700;
      color: #1f2937;
    }

    .lbl {
      font-size: 12px;
      color: #6b7280;
      margin-top: 2px;
    }
  }
}

.stats-desc {
  margin-top: 8px;
}

.form-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 8px;
}

// ---------- 移动端 (< 768px) ----------
@media (max-width: #{$bp-sm - 0.02}) {
  .author-center {
    padding: 12px;
    gap: 12px;
  }

  .author-head {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .author-actions {
    width: 100%;

    :deep(.el-button) {
      width: 100%;
      min-height: 44px;
    }
  }

  .stat-strip {
    grid-template-columns: repeat(2, 1fr);
  }

  .form-footer {
    :deep(.el-button) {
      flex: 1;
      min-height: 44px;
    }
  }
}
</style>