<template>
  <div class="page erp-purchase-new" v-loading="submitting">
    <el-card>
      <template #header>
        <div class="header">
          <span class="title">
            <el-icon><ShoppingCart /></el-icon>
            新建采购单
          </span>
          <el-button :icon="Back" @click="goBack">返回列表</el-button>
        </div>
      </template>

      <el-form :model="form" label-width="100px" :rules="rules" ref="formRef">
        <el-row :gutter="16">
          <el-col :xs="24" :md="12">
            <el-form-item label="供应商" prop="supplierId">
              <el-select
                v-model="form.supplierId"
                placeholder="选择供应商"
                style="width: 100%"
                filterable
              >
                <el-option
                  v-for="s in suppliers"
                  :key="s.id"
                  :label="`${s.name} (${s.level || '-'})`"
                  :value="s.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="备注">
              <el-input v-model="form.remark" placeholder="选填" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-divider content-position="left">
        <span class="section-title">采购明细</span>
      </el-divider>

      <div class="items-actions">
        <el-button :icon="Plus" @click="addItem">新增明细</el-button>
        <span class="total">合计: ¥ {{ totalAmount.toFixed(2) }}</span>
      </div>

      <el-table :data="form.items" border style="width: 100%">
        <el-table-column label="SKU" min-width="220">
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
                :label="`${s.skuCode || ''} ${s.specJson || ''}`"
                :value="s.id"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="130">
          <template #default="{ row }">
            <el-input-number v-model="row.quantity" :min="1" :step="1" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="单价" width="140">
          <template #default="{ row }">
            <el-input-number v-model="row.unitPrice" :min="0" :precision="2" :step="0.1" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="批次号" width="160">
          <template #default="{ row }">
            <el-input v-model="row.batchNo" placeholder="如 B202609-01" />
          </template>
        </el-table-column>
        <el-table-column label="生产日期" width="170">
          <template #default="{ row }">
            <el-date-picker v-model="row.productionDate" type="date" placeholder="选择" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="到期日期" width="170">
          <template #default="{ row }">
            <el-date-picker v-model="row.expiryDate" type="date" placeholder="选择" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center" fixed="right">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="removeItem($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="form-footer">
        <el-button @click="goBack">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">提交草稿</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Back, ShoppingCart } from '@element-plus/icons-vue'
import {
  listSuppliers,
  listProductSkus,
  createPurchase,
  type OpcErpSupplier,
  type OpcErpProductSku,
} from '@/api/opc/erp'

const router = useRouter()
const companyId = 1

const submitting = ref(false)
const formRef = ref()

const suppliers = ref<OpcErpSupplier[]>([])
const allSkus = ref<OpcErpProductSku[]>([])

const form = reactive({
  supplierId: undefined as number | undefined,
  remark: '',
  items: [
    {
      skuId: undefined as number | undefined,
      quantity: 1,
      unitPrice: 0,
      batchNo: '',
      productionDate: undefined as string | undefined,
      expiryDate: undefined as string | undefined,
    },
  ],
})

const rules = {
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
}

const totalAmount = computed(() =>
  form.items.reduce(
    (sum: number, it: any) => sum + (Number(it.unitPrice || 0) * Number(it.quantity || 0)),
    0
  )
)

async function loadSuppliers() {
  try {
    const r = await listSuppliers({ companyId })
    suppliers.value = r.data || []
  } catch {
    suppliers.value = []
  }
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
    batchNo: '',
    productionDate: undefined,
    expiryDate: undefined,
  })
}

function removeItem(idx: number) {
  if (form.items.length <= 1) {
    ElMessage.warning('至少保留一条明细')
    return
  }
  form.items.splice(idx, 1)
}

function onSkuChange(row: any) {
  const sku = allSkus.value.find((s: OpcErpProductSku) => s.id === row.skuId)
  if (sku && !row.unitPrice) {
    row.unitPrice = sku.cost || 0
  }
}

async function onSubmit() {
  if (!form.supplierId) {
    ElMessage.warning('请选择供应商')
    return
  }
  if (!form.items.length || form.items.some((it: any) => !it.skuId || !it.quantity)) {
    ElMessage.warning('请完善所有明细(SKU + 数量)')
    return
  }
  submitting.value = true
  try {
    await createPurchase({
      companyId,
      supplierId: form.supplierId,
      remark: form.remark,
      items: form.items.map((it: any) => ({
        skuId: it.skuId!,
        quantity: it.quantity,
        unitPrice: it.unitPrice,
        batchNo: it.batchNo,
        productionDate: typeof it.productionDate === 'string' ? it.productionDate : '',
        expiryDate: typeof it.expiryDate === 'string' ? it.expiryDate : '',
      })),
    })
    ElMessage.success('采购单已创建(草稿)')
    router.push('/opc/erp/purchase')
  } catch (e: any) {
    ElMessage.error(e?.msg || '创建失败')
  } finally {
    submitting.value = false
  }
}

function goBack() {
  router.push('/opc/erp/purchase')
}

onMounted(() => {
  loadSuppliers()
  loadSkus()
})
</script>

<style scoped lang="scss">
.erp-purchase-new {
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
  .form-footer {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 20px;
  }
}
</style>
