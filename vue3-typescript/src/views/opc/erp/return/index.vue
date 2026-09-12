<template>
  <div class="page erp-return-list">
    <el-card>
      <template #header>
        <div class="header">
          <span class="title">
            <el-icon><Refresh /></el-icon>
            退货单
          </span>
          <div class="actions">
            <el-radio-group v-model="returnType" @change="load">
              <el-radio-button label="SALES_RETURN">销退</el-radio-button>
              <el-radio-button label="SUPPLIER_RETURN">采退</el-radio-button>
            </el-radio-group>
            <el-select v-model="statusFilter" placeholder="状态过滤" clearable style="width: 140px" @change="load">
              <el-option label="草稿" value="DRAFT" />
              <el-option label="已确认" value="CONFIRMED" />
              <el-option label="已完成" value="COMPLETED" />
              <el-option label="已取消" value="CANCELLED" />
            </el-select>
            <el-button :icon="Refresh" @click="load">刷新</el-button>
            <el-button type="primary" :icon="Plus" @click="openCreate">新建退货单</el-button>
          </div>
        </div>
      </template>

      <ResponsiveTable
        :data="returns"
        :loading="loading"
        :columns="columns"
        :action-width="280"
        empty-text="暂无退货单"
      >
        <template #actions="{ row }">
          <el-button link type="primary" size="small" @click="goDetail(row)">详情</el-button>
          <el-button
            v-if="row.status === 'DRAFT'"
            link
            type="success"
            size="small"
            @click="onConfirm(row)"
          >确认</el-button>
          <el-button
            v-if="row.status === 'DRAFT'"
            link
            type="danger"
            size="small"
            @click="onCancel(row)"
          >取消</el-button>
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

    <!-- 新建退货单对话框 -->
    <el-dialog v-model="dialogVisible" title="新建退货单" width="640px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="退货类型">
          <el-radio-group v-model="form.returnType">
            <el-radio value="SALES_RETURN">销退(客户退回)</el-radio>
            <el-radio value="SUPPLIER_RETURN">采退(退回供应商)</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="关联单号" v-if="form.returnType === 'SALES_RETURN'">
          <el-input-number v-model="form.refId" :min="0" placeholder="原销售单 ID(选填)" style="width: 100%" />
        </el-form-item>
        <el-form-item label="关联单号" v-if="form.returnType === 'SUPPLIER_RETURN'">
          <el-input-number v-model="form.refId" :min="0" placeholder="原采购单 ID(选填)" style="width: 100%" />
        </el-form-item>
        <el-form-item label="退货原因">
          <el-input v-model="form.reason" type="textarea" :rows="2" placeholder="如:质量问题、客户拒收" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="选填" />
        </el-form-item>
        <el-form-item label="明细">
          <el-button :icon="Plus" size="small" @click="addItem">新增明细</el-button>
        </el-form-item>
        <el-table :data="form.items" border size="small">
          <el-table-column label="SKU ID" min-width="120">
            <template #default="{ row }">
              <el-input-number v-model="row.skuId" :min="1" :step="1" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="数量" min-width="100">
            <template #default="{ row }">
              <el-input-number v-model="row.quantity" :min="1" :step="1" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="单价" min-width="120">
            <template #default="{ row }">
              <el-input-number v-model="row.unitPrice" :min="0" :precision="2" :step="0.1" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="批次 ID" min-width="120">
            <template #default="{ row }">
              <el-input-number v-model="row.batchId" :min="0" :step="1" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="原因" min-width="140">
            <template #default="{ row }">
              <el-input v-model="row.reason" placeholder="选填" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" size="small" @click="removeItem($index)">删</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存草稿</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  listReturns,
  createReturn,
  confirmReturn,
  cancelReturn,
  type OpcErpReturn,
} from '@/api/opc/erp'
import ResponsiveTable, { type Column } from '@/views/opc/components/ResponsiveTable.vue'

const router = useRouter()
const companyId = 1

const returns = ref<OpcErpReturn[]>([])
const loading = ref(false)
const saving = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

const returnType = ref<'SALES_RETURN' | 'SUPPLIER_RETURN'>('SALES_RETURN')
const statusFilter = ref('')

const dialogVisible = ref(false)
const form = reactive({
  returnType: 'SALES_RETURN' as 'SALES_RETURN' | 'SUPPLIER_RETURN',
  refId: undefined as number | undefined,
  reason: '',
  remark: '',
  items: [
    {
      skuId: undefined as number | undefined,
      quantity: 1,
      unitPrice: 0,
      batchId: undefined as number | undefined,
      reason: '',
    },
  ],
})

const STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  CONFIRMED: '已确认',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

const RETURN_LABEL: Record<string, string> = {
  SALES_RETURN: '销退',
  SUPPLIER_RETURN: '采退',
}

const columns: Column[] = [
  { key: 'returnNo', label: '退货单号', primary: true, width: 170 },
  {
    key: 'returnType',
    label: '类型',
    width: 90,
    type: 'tag',
    formatter: (v: any) => RETURN_LABEL[String(v || '')] || String(v || '-'),
    tagMap: { SALES_RETURN: 'warning', SUPPLIER_RETURN: 'info' },
  },
  { key: 'refId', label: '关联单号', width: 100, type: 'number' },
  {
    key: 'refundAmount',
    label: '退款金额',
    type: 'amount',
    width: 130,
    align: 'right',
    hideOnMobile: true,
  },
  {
    key: 'status',
    label: '状态',
    width: 100,
    type: 'tag',
    formatter: (v: any) => STATUS_LABEL[String(v || '')] || String(v || '-'),
    tagMap: { DRAFT: 'info', CONFIRMED: 'primary', COMPLETED: 'success', CANCELLED: 'danger' },
  },
  { key: 'reason', label: '原因', hideOnMobile: true },
  { key: 'createTime', label: '创建时间', type: 'date', width: 160, hideOnMobile: true },
]

async function load() {
  loading.value = true
  try {
    const r = await listReturns({
      companyId,
      returnType: returnType.value,
      status: statusFilter.value || undefined,
    })
    const all = r.data || []
    total.value = all.length
    const start = (page.value - 1) * pageSize.value
    returns.value = all.slice(start, start + pageSize.value)
  } catch (e: any) {
    ElMessage.error(e?.msg || '加载失败')
    returns.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    returnType: returnType.value,
    refId: undefined,
    reason: '',
    remark: '',
    items: [{ skuId: undefined, quantity: 1, unitPrice: 0, batchId: undefined, reason: '' }],
  })
  dialogVisible.value = true
}

function addItem() {
  form.items.push({ skuId: undefined, quantity: 1, unitPrice: 0, batchId: undefined, reason: '' })
}

function removeItem(idx: number) {
  if (form.items.length <= 1) {
    ElMessage.warning('至少保留一条明细')
    return
  }
  form.items.splice(idx, 1)
}

async function onSave() {
  if (!form.items.length || form.items.some((it: any) => !it.skuId || !it.quantity)) {
    ElMessage.warning('请完善所有明细(SKU + 数量)')
    return
  }
  saving.value = true
  try {
    await createReturn({
      companyId,
      returnType: form.returnType,
      refId: form.refId,
      reason: form.reason,
      remark: form.remark,
      items: form.items.map((it: any) => ({
        skuId: it.skuId!,
        quantity: it.quantity,
        unitPrice: it.unitPrice,
        batchId: it.batchId,
        reason: it.reason,
      })),
    })
    ElMessage.success('退货单已创建(草稿)')
    dialogVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '创建失败')
  } finally {
    saving.value = false
  }
}

async function onConfirm(row: OpcErpReturn) {
  if (!row.id) return
  await ElMessageBox.confirm(
    `确认退货单「${row.returnNo || row.id}」?确认后将自动回滚库存`,
    '提示',
    { type: 'warning' }
  ).catch(() => {})
  try {
    await confirmReturn(row.id, companyId)
    ElMessage.success('已确认并回滚库存')
    load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '确认失败')
  }
}

async function onCancel(row: OpcErpReturn) {
  if (!row.id) return
  await ElMessageBox.confirm(`取消退货单「${row.returnNo || row.id}」?`, '提示', {
    type: 'warning',
  }).catch(() => {})
  try {
    await cancelReturn(row.id, companyId)
    ElMessage.success('已取消')
    load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '取消失败')
  }
}

function goDetail(row: OpcErpReturn) {
  if (!row.id) return
  router.push(`/opc/erp/return?focus=${row.id}`)
}

watch(returnType, () => {
  page.value = 1
})

onMounted(load)
</script>

<style scoped lang="scss">
.erp-return-list {
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
}
</style>
