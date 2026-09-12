<template>
  <div class="page erp-inventory" v-loading="loading">
    <el-tabs v-model="activeTab">
      <!-- 低库存预警 -->
      <el-tab-pane label="低库存预警" name="low">
        <div class="tab-actions">
          <el-button :icon="Refresh" @click="loadLowStock">刷新</el-button>
          <el-button type="warning" :icon="BellFilled" @click="onTriggerAlert">手动触发预警通知</el-button>
        </div>
        <ResponsiveTable
          :data="lowStock"
          :loading="lowLoading"
          :columns="lowColumns"
          :action-width="160"
          empty-text="暂无低库存 SKU"
        >
          <template #actions="{ row }">
            <el-button link type="primary" size="small" @click="onAdjust(row)">调整</el-button>
          </template>
        </ResponsiveTable>
      </el-tab-pane>

      <!-- 日报 -->
      <el-tab-pane label="库存日报" name="daily">
        <div class="tab-actions">
          <el-date-picker
            v-model="dailyDate"
            type="date"
            placeholder="选择日期"
            value-format="YYYY-MM-DD"
            style="width: 200px"
            @change="loadDaily"
          />
          <el-button type="primary" @click="loadDaily">查询</el-button>
          <el-button :icon="Refresh" @click="onTriggerSnapshot">手动触发日终快照</el-button>
        </div>
        <el-card v-if="daily" shadow="never" class="report-summary">
          <el-row :gutter="16">
            <el-col :xs="24" :md="8">
              <div class="metric">
                <div class="metric-label">日期</div>
                <div class="metric-value">{{ daily.date }}</div>
              </div>
            </el-col>
            <el-col :xs="24" :md="8">
              <div class="metric">
                <div class="metric-label">SKU 数</div>
                <div class="metric-value">{{ daily.skuCount }}</div>
              </div>
            </el-col>
            <el-col :xs="24" :md="8">
              <div class="metric">
                <div class="metric-label">总期末库存</div>
                <div class="metric-value">{{ daily.totalClosingStock }}</div>
              </div>
            </el-col>
          </el-row>
        </el-card>
        <ResponsiveTable
          :data="daily?.details || []"
          :loading="dailyLoading"
          :columns="snapshotColumns"
          :action-width="0"
          empty-text="暂无日报数据"
        />
      </el-tab-pane>

      <!-- 月报 -->
      <el-tab-pane label="库存月报" name="monthly">
        <div class="tab-actions">
          <el-date-picker
            v-model="monthlyValue"
            type="month"
            placeholder="选择月份"
            value-format="YYYY-MM"
            style="width: 200px"
            @change="loadMonthly"
          />
          <el-button type="primary" @click="loadMonthly">查询</el-button>
        </div>
        <ResponsiveTable
          :data="monthly?.daily || []"
          :loading="monthlyLoading"
          :columns="snapshotColumns"
          :action-width="0"
          empty-text="暂无月报数据"
        />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, BellFilled } from '@element-plus/icons-vue'
import {
  listLowStock,
  getDailyReport,
  getMonthlyReport,
  triggerDailySnapshot,
  triggerLowStockAlert,
  type OpcErpProductSku,
  type OpcErpDailyReport,
  type OpcErpMonthlyReport,
} from '@/api/opc/erp'
import ResponsiveTable, { type Column } from '@/views/opc/components/ResponsiveTable.vue'

const companyId = 1
const activeTab = ref('low')

const lowStock = ref<OpcErpProductSku[]>([])
const lowLoading = ref(false)
const loading = ref(false)

const dailyDate = ref<string>(new Date().toISOString().slice(0, 10))
const daily = ref<OpcErpDailyReport | null>(null)
const dailyLoading = ref(false)

const monthlyValue = ref<string>(new Date().toISOString().slice(0, 7))
const monthly = ref<OpcErpMonthlyReport | null>(null)
const monthlyLoading = ref(false)

const lowColumns: Column[] = [
  { key: 'skuCode', label: 'SKU 编码', primary: true, width: 160 },
  { key: 'productId', label: '商品 ID', width: 100, type: 'number', hideOnMobile: true },
  { key: 'stock', label: '当前库存', width: 110, align: 'right' },
  { key: 'threshold', label: '阈值', width: 100, align: 'right', hideOnMobile: true },
  {
    key: 'gap',
    label: '缺口',
    width: 100,
    align: 'right',
    formatter: (v: any, row: any) =>
      String(Math.max(Number(row?.threshold || 0) - Number(row?.stock || 0), 0)),
  },
  { key: 'updateTime', label: '更新时间', type: 'date', width: 160, hideOnMobile: true },
]

const snapshotColumns: Column[] = [
  { key: 'date', label: '日期', primary: true, width: 120 },
  { key: 'skuId', label: 'SKU', width: 100, type: 'number', hideOnMobile: true },
  { key: 'opening', label: '期初', width: 90, align: 'right' },
  { key: 'inQty', label: '入库', width: 90, align: 'right' },
  { key: 'outQty', label: '出库', width: 90, align: 'right' },
  { key: 'closing', label: '期末', width: 100, align: 'right' },
]

async function loadLowStock() {
  lowLoading.value = true
  try {
    const r = await listLowStock(companyId)
    lowStock.value = r.data || []
  } catch (e: any) {
    ElMessage.error(e?.msg || '加载低库存失败')
    lowStock.value = []
  } finally {
    lowLoading.value = false
  }
}

async function loadDaily() {
  if (!dailyDate.value) {
    ElMessage.warning('请选择日期')
    return
  }
  dailyLoading.value = true
  try {
    const r = await getDailyReport({ companyId, date: dailyDate.value })
    daily.value = r.data || null
  } catch (e: any) {
    ElMessage.error(e?.msg || '加载日报失败')
    daily.value = null
  } finally {
    dailyLoading.value = false
  }
}

async function loadMonthly() {
  if (!monthlyValue.value) {
    ElMessage.warning('请选择月份')
    return
  }
  monthlyLoading.value = true
  try {
    const [y, m] = monthlyValue.value.split('-').map(Number)
    const r = await getMonthlyReport({ companyId, year: y, month: m })
    monthly.value = r.data || null
  } catch (e: any) {
    ElMessage.error(e?.msg || '加载月报失败')
    monthly.value = null
  } finally {
    monthlyLoading.value = false
  }
}

async function onTriggerSnapshot() {
  try {
    await triggerDailySnapshot()
    ElMessage.success('日终快照已触发')
    loadDaily()
  } catch (e: any) {
    ElMessage.error(e?.msg || '触发失败')
  }
}

async function onTriggerAlert() {
  try {
    const r = await triggerLowStockAlert()
    ElMessage.success(`低库存预警通知已发送 (匹配 ${(r as any)?.data ?? 0} 条)`)
    loadLowStock()
  } catch (e: any) {
    ElMessage.error(e?.msg || '触发失败')
  }
}

function onAdjust(row: OpcErpProductSku) {
  ElMessage.info(`请到商品详情页调整「${row.skuCode || row.id}」库存`)
}

onMounted(() => {
  loading.value = true
  Promise.all([loadLowStock()])
    .catch(() => {})
    .finally(() => {
      loading.value = false
    })
})
</script>

<style scoped lang="scss">
.erp-inventory {
  .tab-actions {
    margin-bottom: 12px;
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  }
  .report-summary {
    margin-bottom: 16px;
  }
  .metric {
    text-align: center;
    padding: 8px 0;
  }
  .metric-label {
    color: var(--el-text-color-secondary, #909399);
    font-size: 13px;
    margin-bottom: 4px;
  }
  .metric-value {
    font-size: 22px;
    font-weight: 700;
    color: var(--el-color-primary, #409eff);
  }
}
</style>
