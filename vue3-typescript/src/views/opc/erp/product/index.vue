<template>
  <div class="page erp-product-list">
    <el-card>
      <template #header>
        <div class="header">
          <span class="title">
            <el-icon><Goods /></el-icon>
            商品管理
          </span>
          <div class="actions">
            <el-select v-model="categoryFilter" placeholder="分类过滤" clearable style="width: 160px" @change="load">
              <el-option label="食品饮料" value="FOOD" />
              <el-option label="日用百货" value="DAILY" />
              <el-option label="电子数码" value="ELECTRONICS" />
              <el-option label="服装鞋帽" value="CLOTHING" />
              <el-option label="美妆护肤" value="BEAUTY" />
              <el-option label="图书音像" value="BOOKS" />
              <el-option label="其他" value="OTHER" />
            </el-select>
            <el-button :icon="Refresh" @click="load">刷新</el-button>
            <el-button type="success" :icon="MagicStick" @click="openAiDialog">AI 自动分类</el-button>
            <el-button type="primary" :icon="Plus" @click="openCreate">新增商品</el-button>
          </div>
        </div>
      </template>

      <ResponsiveTable
        :data="products"
        :loading="loading"
        :columns="columns"
        :action-width="220"
        empty-text="暂无商品"
      >
        <template #actions="{ row }">
          <el-button link type="primary" size="small" @click="goDetail(row)">详情</el-button>
          <el-button link type="warning" size="small" @click="openEdit(row)">编辑</el-button>
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

    <!-- 新建/编辑商品对话框 -->
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑商品' : '新增商品'" width="640px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="商品名" required>
          <el-input v-model="form.name" placeholder="例如:可口可乐 330ml" />
        </el-form-item>
        <el-form-item label="SKU 根">
          <el-input v-model="form.skuRoot" placeholder="选填,自动生成" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" placeholder="选择分类" style="width: 100%">
            <el-option label="食品饮料" value="FOOD" />
            <el-option label="日用百货" value="DAILY" />
            <el-option label="电子数码" value="ELECTRONICS" />
            <el-option label="服装鞋帽" value="CLOTHING" />
            <el-option label="美妆护肤" value="BEAUTY" />
            <el-option label="图书音像" value="BOOKS" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="品牌">
          <el-input v-model="form.brand" placeholder="品牌名称" />
        </el-form-item>
        <el-form-item label="单位">
          <el-input v-model="form.unit" placeholder="如:瓶/件/箱" />
        </el-form-item>
        <el-form-item label="规格属性">
          <el-input
            v-model="form.specAttrs"
            type="textarea"
            :rows="2"
            placeholder="例如:颜色|尺码|容量"
          />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- AI 自动分类对话框 -->
    <el-dialog v-model="aiVisible" title="AI 自动分类" width="560px">
      <el-form :model="aiForm" label-width="80px">
        <el-form-item label="商品名" required>
          <el-input v-model="aiForm.name" placeholder="例如:可口可乐 330ml 听装" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="aiForm.description" type="textarea" :rows="3" placeholder="(可选)商品描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="aiVisible = false">取消</el-button>
        <el-button type="primary" :loading="aiLoading" @click="onAiCategory">生成分类</el-button>
      </template>
    </el-dialog>

    <!-- AI 结果对话框 -->
    <el-dialog v-model="aiResultVisible" title="AI 分类建议" width="420px">
      <div v-if="aiResult" class="ai-result">
        <el-tag size="large" type="success">{{ aiResultLabel }}</el-tag>
        <div class="ai-code">代码: {{ aiResult }}</div>
      </div>
      <template #footer>
        <el-button @click="aiResultVisible = false">关闭</el-button>
        <el-button type="primary" @click="applyAiCategory">应用到表单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Goods, MagicStick } from '@element-plus/icons-vue'
import {
  listProducts,
  createProduct,
  updateProduct,
  deleteProduct,
  autoCategoryProduct,
  type OpcErpProduct,
} from '@/api/opc/erp'
import ResponsiveTable, { type Column } from '@/views/opc/components/ResponsiveTable.vue'

const router = useRouter()
const companyId = 1

const products = ref<OpcErpProduct[]>([])
const loading = ref(false)
const saving = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

const categoryFilter = ref('')

const dialogVisible = ref(false)
const editing = ref(false)
const form = reactive<OpcErpProduct>({
  name: '',
  skuRoot: '',
  category: '',
  brand: '',
  unit: '',
  specAttrs: '',
  description: '',
})

const aiVisible = ref(false)
const aiLoading = ref(false)
const aiForm = reactive({ name: '', description: '' })
const aiResultVisible = ref(false)
const aiResult = ref('')

const CATEGORY_LABEL: Record<string, string> = {
  FOOD: '食品饮料',
  DAILY: '日用百货',
  ELECTRONICS: '电子数码',
  CLOTHING: '服装鞋帽',
  BEAUTY: '美妆护肤',
  BOOKS: '图书音像',
  OTHER: '其他',
}

const aiResultLabel = computed(() => CATEGORY_LABEL[aiResult.value] || aiResult.value || '-')

const columns: Column[] = [
  { key: 'name', label: '商品名', primary: true, minWidth: 180 },
  {
    key: 'category',
    label: '分类',
    width: 110,
    type: 'tag',
    formatter: (v: any) => CATEGORY_LABEL[String(v || '')] || String(v || '-'),
    tagMap: {
      FOOD: 'warning',
      DAILY: 'info',
      ELECTRONICS: 'primary',
      CLOTHING: 'success',
      BEAUTY: 'danger',
      BOOKS: 'info',
      OTHER: '',
    },
  },
  { key: 'skuRoot', label: 'SKU 根', width: 130, hideOnMobile: true },
  { key: 'brand', label: '品牌', width: 110, hideOnMobile: true },
  { key: 'unit', label: '单位', width: 80, hideOnMobile: true },
  { key: 'createTime', label: '创建时间', type: 'date', width: 160, hideOnMobile: true },
]

async function load() {
  loading.value = true
  try {
    const r = await listProducts({
      companyId,
      category: categoryFilter.value || undefined,
    })
    const all = r.data || []
    total.value = all.length
    const start = (page.value - 1) * pageSize.value
    products.value = all.slice(start, start + pageSize.value)
  } catch (e: any) {
    ElMessage.error(e?.msg || '加载商品失败')
    products.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, {
    id: undefined,
    name: '',
    skuRoot: '',
    category: '',
    brand: '',
    unit: '',
    specAttrs: '',
    description: '',
  })
}

function openCreate() {
  resetForm()
  editing.value = false
  dialogVisible.value = true
}

function openEdit(row: OpcErpProduct) {
  resetForm()
  Object.assign(form, row)
  editing.value = true
  dialogVisible.value = true
}

async function onSave() {
  if (!form.name?.trim()) {
    ElMessage.warning('请输入商品名')
    return
  }
  saving.value = true
  try {
    if (editing.value && form.id) {
      await updateProduct(form.id, { ...form, companyId })
      ElMessage.success('已更新')
    } else {
      await createProduct({ ...form, companyId })
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '保存失败')
  } finally {
    saving.value = false
  }
}

async function onDelete(row: OpcErpProduct) {
  if (!row.id) return
  await ElMessageBox.confirm(`确认删除商品「${row.name}」?`, '提示', { type: 'warning' }).catch(() => {})
  try {
    await deleteProduct(row.id, companyId)
    ElMessage.success('已删除')
    load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '删除失败')
  }
}

function goDetail(row: OpcErpProduct) {
  if (!row.id) return
  router.push(`/opc/erp/product/${row.id}`)
}

function openAiDialog() {
  Object.assign(aiForm, { name: '', description: '' })
  aiVisible.value = true
}

async function onAiCategory() {
  if (!aiForm.name?.trim()) {
    ElMessage.warning('请输入商品名')
    return
  }
  aiLoading.value = true
  try {
    const r = await autoCategoryProduct({ ...aiForm })
    aiResult.value = (r.data as string) || ''
    aiVisible.value = false
    aiResultVisible.value = true
  } catch (e: any) {
    ElMessage.error(e?.msg || '生成失败')
  } finally {
    aiLoading.value = false
  }
}

function applyAiCategory() {
  if (!aiResult.value) return
  form.category = aiResult.value
  aiResultVisible.value = false
  editing.value = false
  dialogVisible.value = true
  ElMessage.success('已应用,继续完善其他信息')
}

onMounted(load)
</script>

<style scoped lang="scss">
.erp-product-list {
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
  .pager {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
  }
  .ai-result {
    text-align: center;
    padding: 20px 0;
    .ai-code {
      margin-top: 12px;
      color: var(--el-text-color-secondary, #909399);
      font-size: 13px;
    }
  }
}
</style>
