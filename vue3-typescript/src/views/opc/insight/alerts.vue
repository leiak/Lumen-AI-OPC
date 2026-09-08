<template>
  <div class="page insight-alerts">
    <h2>异常预警</h2>

    <!-- 过滤行 -->
    <el-row :gutter="12" class="filter-row">
      <el-col :span="8">
        <el-select v-model="statusFilter" placeholder="状态" clearable @change="load">
          <el-option label="全部状态" value="" />
          <el-option label="OPEN" value="OPEN" />
          <el-option label="ACKED" value="ACKED" />
          <el-option label="RESOLVED" value="RESOLVED" />
        </el-select>
      </el-col>
      <el-col :span="8">
        <el-select v-model="levelFilter" placeholder="级别" clearable @change="load">
          <el-option label="全部级别" value="" />
          <el-option label="HIGH" value="HIGH" />
          <el-option label="MEDIUM" value="MEDIUM" />
          <el-option label="LOW" value="LOW" />
        </el-select>
      </el-col>
      <el-col :span="8">
        <el-button :loading="loading" @click="load">刷新</el-button>
      </el-col>
    </el-row>

    <!-- 列表 -->
    <ResponsiveTable
      :data="list"
      :loading="loading"
      :columns="columns"
      :action-width="120"
      empty-text="暂无异常"
    >
      <template #actions="{ row }">
        <el-button
          v-if="row.status === 'OPEN'"
          size="small"
          type="primary"
          :loading="ackingId === row.id"
          @click="onAck(row)"
        >
          确认
        </el-button>
        <el-tag v-else type="success" size="small">已确认</el-tag>
      </template>
    </ResponsiveTable>
  </div>
</template>

<script setup lang="ts" name="InsightAlerts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listAlerts, ackAlert } from '@/api/opc/insight'
import ResponsiveTable, { type Column } from '@/views/opc/components/ResponsiveTable.vue'

const list = ref<any[]>([])
const loading = ref(false)
const ackingId = ref<number | null>(null)
const statusFilter = ref<string>('')
const levelFilter = ref<string>('')

const columns: Column[] = [
  { key: 'level', label: '级别', width: 90, type: 'tag',
    tagMap: { HIGH: 'danger', MEDIUM: 'warning', LOW: 'success' } },
  { key: 'title', label: '标题', primary: true, minWidth: 160 },
  { key: 'description', label: '描述', minWidth: 200, hideOnMobile: true },
  { key: 'createdAt', label: '时间', type: 'date', width: 160 },
  { key: 'status', label: '状态', width: 90, type: 'tag',
    tagMap: { OPEN: 'danger', ACKED: 'success', RESOLVED: 'info' } },
]

async function load() {
  loading.value = true
  try {
    const r = await listAlerts(50)
    let rows = r.data || []
    if (statusFilter.value) rows = rows.filter((x: any) => x.status === statusFilter.value)
    if (levelFilter.value) rows = rows.filter((x: any) => x.level === levelFilter.value)
    list.value = rows
  } catch (e: any) {
    ElMessage.error(e?.msg || '加载异常列表失败')
    list.value = []
  } finally {
    loading.value = false
  }
}

async function onAck(row: any) {
  ackingId.value = row.id
  try {
    await ackAlert(row.id)
    ElMessage.success('已确认')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '确认失败')
  } finally {
    ackingId.value = null
  }
}

onMounted(load)
</script>

<style scoped lang="scss">
.insight-alerts { display: flex; flex-direction: column; gap: 12px; }
h2 { margin: 0 0 8px; font-size: 20px; font-weight: 600; }
.filter-row {
  margin-bottom: 12px;
  :deep(.el-select) { width: 100%; }
}
</style>
