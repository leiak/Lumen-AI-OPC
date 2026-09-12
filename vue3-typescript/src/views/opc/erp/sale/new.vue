<template>
  <div class="page erp-sale-new" v-loading="submitting">
    <el-card>
      <template #header>
        <div class="header">
          <span class="title">
            <el-icon><Sell /></el-icon>
            新建销售单
          </span>
          <el-button :icon="Back" @click="goBack">返回列表</el-button>
        </div>
      </template>

      <el-form :model="form" label-width="100px">
        <el-row :gutter="16">
          <el-col :xs="24" :md="12">
            <el-form-item label="客户姓名">
              <el-input v-model="form.customerName" placeholder="客户姓名(选填)" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="客户电话">
              <el-input v-model="form.customerPhone" placeholder="客户电话(选填)" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
      </el-form>

      <el-divider content-position="left">
        <span class="section-title">销售明细 <el-tag size="small" type="info" effect="plain">FIFO 批次扣减</el-tag></span>
      </el-divider>

      <div class="items-actions">
        <el-button :icon="Plus" @click="addItem">新增明细</el-button>
        <span class="total">合计: ¥ {{ totalAmount.toFixed(2) }}</span>
      </div>

      <el-table :data="form.items" border style="width: 100%">
        <el-table-column label="SKU" min-width="240">
          <template #default="{ row }">
            <el-select
              v-model="row.skuId"
              placeholder="选择 SKU"
              filterable
              style="width: 100%"
              @change="onSkuChange(row)"
            >
              <el-option
                v-for="s in allSkus"
                :key="s.id"
                :label="skuLabel(s)"
                :value="s.id"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="可用库存" width="100" align="right">
          <template #default="{ row }">
            <el-tag
              :type="stockTagType(row)"
              effect="plain"
              size="small"
            >{{ row.available ?? '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="140">
          <template #default="{ row }">
            <el-input-number v-model="row.quantity" :min="1" :step="1" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="单价" width="160">
          <template #default="{ row }">
            <el-input-number v-model="row.unitPrice" :min="0" :precision="2" :step="0.1" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="小计" width="120" align="right">
          <template #default="{ row }">
            <span>¥ {{ ((Number(row.unitPrice || 0)) * (Number(row.quantity || 0))).toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center" fixed="right">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="removeItem($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-alert
        v-if="hasStockWarning"
        type="warning"
        :closable="false"
        title="库存不足"
        description="明细中存在数量超过可用库存的行,请调整后再提交。"
        show-icon
        class="stock-warn"
      />

      <div class="form-footer">
        <el-button @click="goBack">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="hasStockWarning" @click="onSubmit">提交草稿</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Back, Sell } from '@element-plus/icons-vue'
import {
  listProductSkus,
  getInventorySku,
  createSale,
  type OpcErpProductSku,
} from '@/api/opc/erp'

const router = useRouter()
const companyId = 1

const submitting = ref(false)
const allSkus = ref<OpcErpProductSku[]>([])

const form = reactive({
  customerName: '',
  customerPhone: '',
  remark: '',
  items: [
    {
      skuId: undefined as number | undefined,
      quantity: 1,
      unitPrice: 0,
      available: undefined as number | undefined,
    },
  ],
})

const totalAmount = computed(() =>
  form.items.reduce(
    (sum: number, it: any) => sum + Number(it.unitPrice || 0) * Number(it.quantity || 0),
    0
  )
)

const hasStockWarning = computed(() =>
  form.items.some(
    (it: any) =>
      it.skuId &&
      it.available !== undefined &&
      Number(it.quantity || 0) > Number(it.available || 0)
  )
)

function skuLabel(s: OpcErpProductSku) {
  const code = s.skuCode || `#${s.id}`
  const spec = s.specJson || ''
  return `${code} ${spec}`.trim()
}

function stockTagType(row: any): 'success' | 'warning' | 'danger' | 'info' {
  if (row.available === undefined) return 'info'
  if (Number(row.quantity || 0) > Number(row.available || 0)) return 'danger'
  if (Number(row.available || 0) <= Number(skuThreshold(row.skuId))) return 'warning'
  return 'success'
}

function skuThreshold(skuId?: number): number {
  if (!skuId) return 0
  const sku = allSkus.value.find((s: OpcErpProductSku) => s.id === skuId)
  return Number(sku?.threshold || 0)
}

async function loadSkus() {
  try {
    const r = await listProductSkus({ companyId })
    allSkus.value = r.data || []
  } catch {
    allSkus.value = []
  }
}

function addItem() {
  form.items.push({
    skuId: undefined,
    quantity: 1,
    unitPrice: 0,
    available: undefined,
  })
}

function removeItem(idx: number) {
  if (form.items.length <= 1) {
    ElMessage.warning('至少保留一条明细')
    return
  }
  form.items.splice(idx, 1)
}

async function onSkuChange(row: any) {
  const sku = allSkus.value.find((s: OpcErpProductSku) => s.id === row.skuId)
  if (sku) {
    row.unitPrice = sku.price || 0
    try {
      const r = await getInventorySku(sku.id!, companyId)
      row.available = Number(r.data?.stock ?? 0)
    } catch {
      row.available = sku.stock ?? 0
    }
  } else {
    row.available = undefined
  }
}

async function onSubmit() {
  if (!form.items.length || form.items.some((it: any) => !it.skuId || !it.quantity)) {
    ElMessage.warning('请完善所有明细(SKU + 数量)')
    return
  }
  submitting.value = true
  try {
    await createSale({
      companyId,
      customerName: form.customerName,
      customerPhone: form.customerPhone,
      remark: form.remark,
      items: form.items.map((it: any) => ({
        skuId: it.skuId!,
        quantity: it.quantity,
        unitPrice: it.unitPrice,
      })),
    })
    ElMessage.success('销售单已创建(草稿)')
    router.push('/opc/erp/sale')
  } catch (e: any) {
    ElMessage.error(e?.msg || '创建失败')
  } finally {
    submitting.value = false
  }
}

function goBack() {
  router.push('/opc/erp/sale')
}

onMounted(loadSkus)
</script>

<style scoped lang="scss">
.erp-sale-new {
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
  .section-title {
    font-weight: 600;
    color: var(--el-text-color-primary, #303133);
    display: inline-flex;
    align-items: center;
    gap: 8px;
  }
  .items-actions {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
    flex-wrap: wrap;
    gap: 8px;
  }
  .total {
    font-weight: 600;
    font-size: 16px;
    color: var(--el-color-primary, #409eff);
  }
  .stock-warn {
    margin-top: 16px;
  }
  .form-footer {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 20px;
  }
}
</style>
