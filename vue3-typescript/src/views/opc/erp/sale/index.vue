<template>
  <div class="page erp-sale-list">
    <el-card>
      <template #header>
        <div class="header">
          <span class="title">
            <el-icon><Sell /></el-icon>
            销售单
          </span>
          <div class="actions">
            <el-select v-model="statusFilter" placeholder="状态过滤" clearable style="width: 140px" @change="load">
              <el-option label="草稿" value="DRAFT" />
              <el-option label="已确认" value="CONFIRMED" />
              <el-option label="已完成" value="COMPLETED" />
              <el-option label="已取消" value="CANCELLED" />
            </el-select>
            <el-button :icon="Refresh" @click="load">刷新</el-button>
            <el-button type="primary" :icon="Plus" @click="goCreate">新建销售单</el-button>
          </div>
        </div>
      </template>

      <ResponsiveTable
        :data="sales"
        :loading="loading"
        :columns="columns"
        :action-width="280"
        empty-text="暂无销售单"
      >
        <template #actions="{ row }">
          <el-button link type="primary" size="small" @click="goDetail(row)">详情</el-button>
          <el-button
            v-if="row.status === 'DRAFT'"
            link
            type="success"
            size="small"
            @click="onConfirm(row)"
          >确认(出库)</el-button>
          <el-button
            v-if="row.status === 'DRAFT' || row.status === 'CONFIRMED'"
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
import { Plus, Refresh, Sell } from '@element-plus/icons-vue'
import {
  listSales,
  confirmSale,
  cancelSale,
  type OpcErpSale,
} from '@/api/opc/erp'
import ResponsiveTable, { type Column } from '@/views/opc/components/ResponsiveTable.vue'

const router = useRouter()
const companyId = 1

const sales = ref<OpcErpSale[]>([])
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
  { key: 'saleNo', label: '销售单号', primary: true, width: 170 },
  { key: 'customerName', label: '客户', width: 130 },
  { key: 'customerPhone', label: '电话', width: 130, hideOnMobile: true },
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
  { key: 'createTime', label: '创建时间', type: 'date', width: 160, hideOnMobile: true },
]

async function load() {
  loading.value = true
  try {
    const r = await listSales({
      companyId,
      status: statusFilter.value || undefined,
    })
    const all = r.data || []
    total.value = all.length
    const start = (page.value - 1) * pageSize.value
    sales.value = all.slice(start, start + pageSize.value)
  } catch (e: any) {
    ElMessage.error(e?.msg || '加载失败')
    sales.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function onConfirm(row: OpcErpSale) {
  if (!row.id) return
  await ElMessageBox.confirm(
    `确认销售单「${row.saleNo || row.id}」?确认后将按 FIFO 批次扣减库存`,
    '提示',
    { type: 'warning' }
  ).catch(() => {})
  try {
    await confirmSale(row.id, companyId)
    ElMessage.success('已确认并扣减库存(FIFO)')
    load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '确认失败')
  }
}

async function onCancel(row: OpcErpSale) {
  if (!row.id) return
  await ElMessageBox.confirm(`取消销售单「${row.saleNo || row.id}」?`, '提示', {
    type: 'warning',
  }).catch(() => {})
  try {
    await cancelSale(row.id, companyId)
    ElMessage.success('已取消')
    load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '取消失败')
  }
}

function goCreate() {
  router.push('/opc/erp/sale/new')
}

function goDetail(row: OpcErpSale) {
  if (!row.id) return
  router.push(`/opc/erp/sale?focus=${row.id}`)
}

onMounted(load)
</script>

<style scoped lang="scss">
.erp-sale-list {
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
