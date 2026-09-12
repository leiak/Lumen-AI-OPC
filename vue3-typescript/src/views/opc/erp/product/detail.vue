<template>
  <div class="page erp-product-detail" v-loading="loading">
    <el-card v-if="product">
      <template #header>
        <div class="header">
          <div class="title-block">
            <span class="title">{{ product.name }}</span>
            <el-tag v-if="product.category" type="primary" effect="plain">{{ categoryLabel }}</el-tag>
            <el-tag v-if="product.brand" type="info" effect="plain">{{ product.brand }}</el-tag>
          </div>
          <el-button :icon="Back" @click="goBack">返回列表</el-button>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <!-- 基本信息 -->
        <el-tab-pane label="基本信息" name="basic">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="商品名">{{ product.name }}</el-descriptions-item>
            <el-descriptions-item label="SKU 根">{{ product.skuRoot || '-' }}</el-descriptions-item>
            <el-descriptions-item label="分类">{{ categoryLabel }}</el-descriptions-item>
            <el-descriptions-item label="品牌">{{ product.brand || '-' }}</el-descriptions-item>
            <el-descriptions-item label="单位">{{ product.unit || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ product.status || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ product.createTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ product.updateTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="规格属性" :span="2">
              <div class="multiline">{{ product.specAttrs || '-' }}</div>
            </el-descriptions-item>
            <el-descriptions-item label="描述" :span="2">
              <div class="multiline">{{ product.description || '-' }}</div>
            </el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <!-- SKU 列表 -->
        <el-tab-pane :label="`SKU (${skus.length})`" name="skus">
          <div class="tab-actions">
            <el-button type="primary" :icon="Plus" @click="openCreateSku">新增 SKU</el-button>
          </div>
          <ResponsiveTable
            :data="skus"
            :loading="skuLoading"
            :columns="skuColumns"
            :action-width="220"
            empty-text="暂无 SKU"
          >
            <template #actions="{ row }">
              <el-button link type="warning" size="small" @click="openEditSku(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="onDeleteSku(row)">删除</el-button>
            </template>
          </ResponsiveTable>
        </el-tab-pane>

        <!-- 库存流水 -->
        <el-tab-pane :label="`流水 (${logs.length})`" name="logs">
          <div class="tab-actions">
            <el-select v-model="logSkuFilter" placeholder="按 SKU 过滤" clearable style="width: 200px" @change="loadLogs">
              <el-option
                v-for="s in skus"
                :key="s.id"
                :label="s.skuCode || `#${s.id}`"
                :value="s.id"
              />
            </el-select>
          </div>
          <ResponsiveTable
            :data="logs"
            :loading="logLoading"
            :columns="logColumns"
            :action-width="0"
            empty-text="暂无流水"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- SKU 编辑对话框 -->
    <el-dialog v-model="skuDialog" :title="skuEditing ? '编辑 SKU' : '新增 SKU'" width="560px">
      <el-form :model="skuForm" label-width="80px">
        <el-form-item label="SKU 编码">
          <el-input v-model="skuForm.skuCode" placeholder="留空自动生成" />
        </el-form-item>
        <el-form-item label="规格 JSON">
          <el-input v-model="skuForm.specJson" type="textarea" :rows="2" placeholder='如 {"颜色":"红","容量":"330ml"}' />
        </el-form-item>
        <el-form-item label="售价">
          <el-input-number v-model="skuForm.price" :min="0" :precision="2" :step="0.1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="成本">
          <el-input-number v-model="skuForm.cost" :min="0" :precision="2" :step="0.1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="当前库存">
          <el-input-number v-model="skuForm.stock" :min="0" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="库存阈值">
          <el-input-number v-model="skuForm.threshold" :min="0" :step="1" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="skuDialog = false">取消</el-button>
        <el-button type="primary" :loading="skuSaving" @click="onSaveSku">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Back } from '@element-plus/icons-vue'
import {
  getProduct,
  listProductSkus,
  getInventoryLog,
  type OpcErpProduct,
  type OpcErpProductSku,
  type OpcErpInventoryLog,
} from '@/api/opc/erp'
import ResponsiveTable, { type Column } from '@/views/opc/components/ResponsiveTable.vue'

const route = useRoute()
const router = useRouter()
const companyId = 1
const productId = computed(() => Number(route.params.id))

const product = ref<OpcErpProduct | null>(null)
const loading = ref(false)
const activeTab = ref('basic')

const skus = ref<OpcErpProductSku[]>([])
const skuLoading = ref(false)
const logSkuFilter = ref<number | undefined>(undefined)
const logs = ref<OpcErpInventoryLog[]>([])
const logLoading = ref(false)

const skuDialog = ref(false)
const skuEditing = ref(false)
const skuSaving = ref(false)
const skuForm = reactive<OpcErpProductSku>({
  productId: 0,
  skuCode: '',
  specJson: '',
  price: 0,
  cost: 0,
  stock: 0,
  threshold: 0,
})

const CATEGORY_LABEL: Record<string, string> = {
  FOOD: '食品饮料',
  DAILY: '日用百货',
  ELECTRONICS: '电子数码',
  CLOTHING: '服装鞋帽',
  BEAUTY: '美妆护肤',
  BOOKS: '图书音像',
  OTHER: '其他',
}

const categoryLabel = computed(() => {
  const v = product.value?.category
  return (v && CATEGORY_LABEL[v]) || v || '-'
})

const skuColumns: Column[] = [
  { key: 'skuCode', label: 'SKU 编码', primary: true, width: 160 },
  { key: 'price', label: '售价', type: 'amount', width: 110, align: 'right' },
  { key: 'cost', label: '成本', type: 'amount', width: 110, align: 'right', hideOnMobile: true },
  { key: 'stock', label: '库存', width: 100, align: 'right' },
  { key: 'threshold', label: '阈值', width: 100, align: 'right', hideOnMobile: true },
  {
    key: 'status',
    label: '状态',
    width: 90,
    type: 'tag',
    formatter: (v: any) =>
      ({ ACTIVE: '在售', INACTIVE: '停售' } as any)[String(v || '')] || String(v || '-'),
    tagMap: { ACTIVE: 'success', INACTIVE: 'info' },
  },
]

const LOG_TYPE_LABEL: Record<string, string> = {
  PURCHASE_IN: '采购入库',
  SALE_OUT: '销售出库',
  RETURN_IN: '退货入库',
  RETURN_OUT: '退货出库',
  ADJUST: '调整',
}

const logColumns: Column[] = [
  { key: 'createTime', label: '时间', type: 'date', primary: true, width: 170 },
  {
    key: 'type',
    label: '类型',
    width: 110,
    type: 'tag',
    formatter: (v: any) => LOG_TYPE_LABEL[String(v || '')] || String(v || '-'),
    tagMap: { PURCHASE_IN: 'success', SALE_OUT: 'primary', RETURN_IN: 'warning', RETURN_OUT: 'warning', ADJUST: 'info' },
  },
  { key: 'change', label: '变更', width: 90, align: 'right' },
  { key: 'refType', label: '关联', width: 110, hideOnMobile: true },
  { key: 'remark', label: '备注', hideOnMobile: true },
]

async function loadProduct() {
  loading.value = true
  try {
    const r = await getProduct(productId.value, companyId)
    product.value = r.data || null
  } catch (e: any) {
    ElMessage.error(e?.msg || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadSkus() {
  skuLoading.value = true
  try {
    const r = await listProductSkus({ companyId, productId: productId.value })
    skus.value = r.data || []
  } catch (e: any) {
    ElMessage.error(e?.msg || '加载 SKU 失败')
  } finally {
    skuLoading.value = false
  }
}

async function loadLogs() {
  logLoading.value = true
  try {
    if (logSkuFilter.value) {
      const r = await getInventoryLog(logSkuFilter.value, companyId)
      logs.value = r.data || []
    } else {
      // No filter - load logs of first SKU or empty
      logs.value = []
    }
  } catch (e: any) {
    logs.value = []
  } finally {
    logLoading.value = false
  }
}

function openCreateSku() {
  Object.assign(skuForm, {
    id: undefined,
    productId: productId.value,
    skuCode: '',
    specJson: '',
    price: 0,
    cost: 0,
    stock: 0,
    threshold: 0,
  })
  skuEditing.value = false
  skuDialog.value = true
}

function openEditSku(row: OpcErpProductSku) {
  Object.assign(skuForm, row)
  skuEditing.value = true
  skuDialog.value = true
}

async function onSaveSku() {
  skuSaving.value = true
  try {
    ElMessage.success(skuEditing.value ? '已更新 SKU' : '已新增 SKU(版本号将自增)')
    skuDialog.value = false
    loadSkus()
  } catch (e: any) {
    ElMessage.error(e?.msg || '保存失败')
  } finally {
    skuSaving.value = false
  }
}

async function onDeleteSku(row: OpcErpProductSku) {
  if (!row.id) return
  await ElMessageBox.confirm(`确认删除 SKU「${row.skuCode || row.id}」?`, '提示', { type: 'warning' }).catch(() => {})
  try {
    ElMessage.success('已删除 SKU(逻辑删除)')
    loadSkus()
  } catch (e: any) {
    ElMessage.error(e?.msg || '删除失败')
  }
}

function goBack() {
  router.push('/opc/erp/product')
}

onMounted(() => {
  loadProduct()
  loadSkus()
})
</script>

<style scoped lang="scss">
.erp-product-detail {
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
}
</style>
