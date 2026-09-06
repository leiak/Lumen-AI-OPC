<template>
  <div class="page" v-loading="loading">
    <el-row :gutter="20" class="is-mobile-stack">
      <el-col :span="14">
        <el-card>
          <template #header>
            <div class="header">
              <span><strong>{{ inst?.nickname }}</strong>（{{ inst?.instanceCode }}）</span>
              <el-tag :type="statusType(inst?.status)">{{ inst?.status }}</el-tag>
            </div>
          </template>

          <el-tabs v-model="tab">
            <el-tab-pane label="对话" name="chat">
              <div class="chat">
                <div class="messages">
                  <div v-for="(m, i) in messages" :key="i" :class="['msg', m.role]">
                    <div class="role">{{ m.role }}</div>
                    <div class="content">{{ m.content }}</div>
                  </div>
                </div>
                <el-input v-model="input" type="textarea" :rows="3" placeholder="输入你的任务（Enter+Shift 发送，Ctrl+Enter 提交）" @keydown.ctrl.enter="send" />
                <el-button type="primary" style="margin-top: 8px" @click="send" :loading="sending">发送</el-button>
              </div>
            </el-tab-pane>

            <el-tab-pane label="任务历史" name="tasks">
              <ResponsiveTable :data="tasks" :columns="taskColumns" empty-text="暂无任务" />
            </el-tab-pane>

            <el-tab-pane label="Token 消耗" name="usage">
              <div class="big-num">累计：{{ inst?.tokenUsed ?? 0 }} tokens</div>
              <ResponsiveTable :data="usage" :columns="usageColumns" empty-text="暂无用量" />
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>

      <el-col :span="10">
        <el-card>
          <template #header><span>实例信息</span></template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="编码">{{ inst?.instanceCode }}</el-descriptions-item>
            <el-descriptions-item label="公司">{{ inst?.companyId }}</el-descriptions-item>
            <el-descriptions-item label="雇佣类型">{{ inst?.hireType }}</el-descriptions-item>
            <el-descriptions-item label="到期时间">{{ inst?.expireTime }}</el-descriptions-item>
            <el-descriptions-item label="任务数">{{ inst?.taskCount }}</el-descriptions-item>
            <el-descriptions-item label="Token 已用">{{ inst?.tokenUsed }}</el-descriptions-item>
            <el-descriptions-item label="Token 配额">{{ inst?.tokenQuota }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts" name="AgentInstanceDetail">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getInstance, listInstanceTasks, listInstanceUsage, runTask } from '@/api/opc/agent'
import { chat as llmChat } from '@/api/opc/llm'
import ResponsiveTable, { type Column } from '@/views/opc/components/ResponsiveTable.vue'

const route = useRoute()
const inst = ref<any>({})
const tasks = ref<any[]>([])
const usage = ref<any[]>([])
const tab = ref('chat')
const loading = ref(false)
const messages = ref<any[]>([])
const input = ref('')
const sending = ref(false)

const taskColumns: Column[] = [
  { key: 'taskCode', label: '编号', primary: true, width: 180 },
  {
    key: 'taskType',
    label: '类型',
    width: 120,
    formatter: (v) => ({ CHAT: '对话', SUMMARY: '摘要', EXTRACT: '提取', PLAN: '规划' } as any)[String(v)] || String(v || '-'),
  },
  {
    key: 'status',
    label: '状态',
    width: 100,
    type: 'tag',
    formatter: (v) => ({ RUNNING: '运行中', SUCCESS: '成功', FAILED: '失败', PENDING: '等待中' } as any)[String(v)] || String(v || '-'),
    tagMap: { RUNNING: 'success', SUCCESS: 'success', PENDING: 'info', FAILED: 'danger' },
  },
  { key: 'tokenTotal', label: 'Token', type: 'number', width: 80, align: 'right' },
  { key: 'durationMs', label: '耗时(ms)', type: 'number', width: 100, align: 'right' },
  { key: 'createTime', label: '时间', type: 'date' },
]

const usageColumns: Column[] = [
  { key: 'model', label: '模型', primary: true, width: 100 },
  { key: 'tokenInput', label: '输入', type: 'number', width: 80, align: 'right' },
  { key: 'tokenOutput', label: '输出', type: 'number', width: 80, align: 'right' },
  { key: 'tokenTotal', label: '合计', type: 'number', width: 80, align: 'right' },
  { key: 'cost', label: '花费(元)', type: 'amount', width: 100, align: 'right' },
  { key: 'latencyMs', label: '耗时(ms)', type: 'number', width: 100, align: 'right', hideOnMobile: true },
  { key: 'bizDate', label: '日期', type: 'date' },
]

async function load() {
  loading.value = true
  try {
    const r = await getInstance(Number(route.params.id))
    inst.value = r.data || {}
    const t = await listInstanceTasks(Number(route.params.id), 50)
    tasks.value = t.data || []
    const u = await listInstanceUsage(Number(route.params.id), 50)
    usage.value = u.data || []
  } finally {
    loading.value = false
  }
}

async function send() {
  if (!input.value.trim()) return
  const userInput = input.value
  messages.value.push({ role: 'user', content: userInput })
  input.value = ''
  sending.value = true

  try {
    const r = await llmChat([
      { role: 'system', content: '你是 OPC 数字员工，请基于上下文回答。' },
      ...messages.value
    ], { instanceId: Number(route.params.id) })
    const content = r.data?.content ?? '(无响应)'
    messages.value.push({ role: 'assistant', content })
  } finally {
    sending.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.header { display: flex; justify-content: space-between; align-items: center; }
.chat { display: flex; flex-direction: column; gap: 12px; }
.messages { max-height: 400px; overflow-y: auto; border: 1px solid #eee; padding: 12px; border-radius: 6px; background: #fafafa; }
.msg { padding: 8px 12px; border-radius: 6px; margin-bottom: 8px; }
.msg .role { font-size: 12px; color: #999; margin-bottom: 4px; }
.msg.user { background: #fff; border: 1px solid #e0e0e0; }
.msg.assistant { background: #ecf5ff; }
.big-num { font-size: 24px; font-weight: 600; color: #2b5fff; }
</style>
