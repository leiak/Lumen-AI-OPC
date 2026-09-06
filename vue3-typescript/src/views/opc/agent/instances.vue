<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="header">
          <span class="title">我的 Agent 实例</span>
          <el-button type="primary" @click="$router.push('/opc/agent/market')">雇佣新 Agent</el-button>
        </div>
      </template>

      <el-empty v-if="!loading && list.length === 0" description="还没有 Agent 实例，去商店雇佣一个吧！" />

      <ResponsiveTable :data="list" :loading="loading" :columns="instanceColumns" :action-width="260" empty-text="还没有 Agent 实例，去商店雇佣一个吧！">
        <template #actions="{ row }">
          <el-button size="small" @click="$router.push(`/opc/agent/instance/${row.id}`)">详情</el-button>
          <el-button size="small" type="warning" v-if="row.status === 'RUNNING'" @click="act(row, 'pause')">暂停</el-button>
          <el-button size="small" type="success" v-if="row.status === 'PAUSED'" @click="act(row, 'resume')">恢复</el-button>
          <el-button size="small" type="danger" v-if="row.status !== 'REVOKED'" @click="act(row, 'revoke')">退订</el-button>
        </template>
      </ResponsiveTable>
    </el-card>
  </div>
</template>

<script setup lang="ts" name="AgentInstances">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listMyInstances, instanceAction } from '@/api/opc/agent'
import ResponsiveTable, { type Column } from '@/views/opc/components/ResponsiveTable.vue'

const list = ref<any[]>([])
const loading = ref(false)

const instanceColumns: Column[] = [
  { key: 'instanceCode', label: '编码', width: 160 },
  { key: 'nickname', label: '昵称', primary: true },
  {
    key: 'hireType',
    label: '雇佣类型',
    width: 100,
    formatter: (v) => ({ FULL_TIME: '全职', PART_TIME: '兼职', TRIAL: '试用' } as any)[String(v)] || String(v || '-'),
  },
  { key: 'expireTime', label: '到期时间', type: 'date', width: 180 },
  { key: 'taskCount', label: '任务数', type: 'number', width: 100, align: 'right' },
  { key: 'tokenUsed', label: '已用 Token', type: 'number', width: 120, align: 'right', hideOnMobile: true },
  {
    key: 'status',
    label: '状态',
    width: 100,
    type: 'tag',
    tagMap: { RUNNING: 'success', PAUSED: 'warning', EXPIRED: 'info', REVOKED: 'danger' },
  },
]

async function load() {
  loading.value = true
  try {
    const r = await listMyInstances()
    list.value = r.data || []
  } finally {
    loading.value = false
  }
}

async function act(row: any, action: string) {
  const msg = { pause: '暂停', resume: '恢复', revoke: '退订' }[action]
  await ElMessageBox.confirm(`确认${msg}该 Agent？`, '提示')
  const r = await instanceAction(row.id, action as any)
  if (r.code === 200) {
    ElMessage.success(`${msg}成功`)
    load()
  }
}

onMounted(load)
</script>

<style scoped>
.header { display: flex; justify-content: space-between; align-items: center; }
.title { font-size: 18px; font-weight: 600; }
</style>
