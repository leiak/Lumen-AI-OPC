<template>
  <div class="page erp-purchase-list">
    <el-card>
      <template #header>
        <div class="header">
          <span class="title">
            <el-icon><ShoppingCart /></el-icon>
            采购单
          </span>
          <div class="actions">
            <el-select v-model="statusFilter" placeholder="状态过滤" clearable style="width: 140px" @change="load">
              <el-option label="草稿" value="DRAFT" />
              <el-option label="已确认" value="CONFIRMED" />
              <el-option label="已完成" value="COMPLETED" />
              <el-option label="已取消" value="CANCELLED" />
            </el-select>
            <el-button :icon="Refresh" @click="load">刷新</el-button>
            <el-button type="primary" :icon="Plus" @click="goCreate">新建采购单</el-button>
          </div>
        </div>
      </template>

      <ResponsiveTable
        :data="purchases"
        :loading="loading"
        :columns="columns"
        :action-width="280"
        empty-text="暂无采购单"
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, ShoppingCart } from '@element-plus/icons-vue'
import {
  listPurchases,
  confirmPurchase,
  cancelPurchase,
  type OpcErpPurchase,
} from '@/api/opc/erp'
import ResponsiveTable, { type Column } from '@/views/opc/components/ResponsiveTable.vue'

const router = useRouter()
const companyId = 1

const purchases = ref<OpcErpPurchase[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

const statusFilter = ref('')

const STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  CONFIRMED: '已确认',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

const columns: Column[] = [
  { key: 'purchaseNo', label: '采购单号', primary: true, width: 170 },
  { key: 'supplierId', label: '供应商', width: 100, type: 'number' },
  {
    key: 'totalAmount',
    label: '总金额',
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
  { key: 'confirmedBy', label: '确认人', width: 90, hideOnMobile: true },
  { key: 'confirmedAt', label: '确认时间', type: 'date', width: 160, hideOnMobile: true },
  { key: 'createTime', label: '创建时间', type: 'date', width: 160, hideOnMobile: true },
]

async function load() {
  loading.value = true
  try {
    const r = await listPurchases({
      companyId,
      status: statusFilter.value || undefined,
    })
    const all = r.data || []
    total.value = all.length
    const start = (page.value - 1) * pageSize.value
    purchases.value = all.slice(start, start + pageSize.value)
  } catch (e: any) {
    ElMessage.error(e?.msg || '加载失败')
    purchases.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function onConfirm(row: OpcErpPurchase) {
  if (!row.id) return
  await ElMessageBox.confirm(
    `确认采购单「${row.purchaseNo || row.id}」?确认后将入库`,
    '提示',
    { type: 'warning' }
  ).catch(() => {})
  try {
    await confirmPurchase(row.id, companyId)
    ElMessage.success('已确认并入库')
    load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '确认失败')
  }
}

async function onCancel(row: OpcErpPurchase) {
  if (!row.id) return
  await ElMessageBox.confirm(`取消采购单「${row.purchaseNo || row.id}」?`, '提示', {
    type: 'warning',
  }).catch(() => {})
  try {
    await cancelPurchase(row.id, companyId)
    ElMessage.success('已取消')
    load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '取消失败')
  }
}

function goCreate() {
  router.push('/opc/erp/purchase/new')
}

function goDetail(row: OpcErpPurchase) {
  if (!row.id) return
  router.push(`/opc/erp/purchase?focus=${row.id}`)
}

onMounted(load)
</script>

<style scoped lang="scss">
.erp-purchase-list {
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
