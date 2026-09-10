<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="header">
          <span>AI 对话测试</span>
          <div class="model-picker">
            <!-- W48.6: 改用 provider + model 二级选择 -->
            <el-select v-model="provider" placeholder="选择厂商" style="width: 160px" @change="onProviderChange">
              <el-option v-for="p in providers" :key="p" :label="providerLabel(p)" :value="p" />
            </el-select>
            <el-select v-model="model" style="width: 240px">
              <el-option
                v-for="m in filteredModels"
                :key="`${m.provider}-${m.id}`"
                :label="`${m.id} (priority=${m.priority})`"
                :value="m.id" />
            </el-select>
          </div>
        </div>
      </template>

      <div class="messages" ref="boxRef">
        <div v-for="(m, i) in messages" :key="i" :class="['msg', m.role]">
          <div class="role">{{ m.role }}</div>
          <div class="content" v-html="renderContent(m.content)" />
        </div>
      </div>

      <el-input v-model="input" type="textarea" :rows="3" placeholder="输入消息（Ctrl+Enter 发送）" @keydown.ctrl.enter="send" />
      <el-button type="primary" style="margin-top: 8px" :loading="loading" @click="send">发送</el-button>
      <el-button @click="clear">清空</el-button>
    </el-card>
  </div>
</template>

<script setup lang="ts" name="LlmChat">
import { ref, nextTick, onMounted, computed } from 'vue'
import { listModels, chat, type LlmModelOption } from '@/api/opc/llm'

const messages = ref<any[]>([])
const input = ref('')
// W48.6: provider 和 model 分开
const provider = ref<string>('minimax')
const model = ref<string>('MiniMax-Text-01')
const models = ref<LlmModelOption[]>([])
const loading = ref(false)
const boxRef = ref()

// 当前所有 provider 名（去重保序）
const providers = computed(() => {
  const seen = new Set<string>()
  const list: string[] = []
  for (const m of models.value) {
    if (!seen.has(m.provider)) {
      seen.add(m.provider)
      list.push(m.provider)
    }
  }
  return list
})

// 当前 provider 下的模型
const filteredModels = computed(() => models.value.filter(m => m.provider === provider.value))

function providerLabel(p: string) {
  return ({
    minimax: 'MiniMax (MiniMaxAI)',
    deepseek: 'DeepSeek',
    openai: 'OpenAI',
    wenxin: '文心一言',
    qwen: '通义千问'
  } as Record<string, string>)[p] || p
}

function onProviderChange() {
  // 切 provider 时,自动选该 provider 的第一个模型
  const first = filteredModels.value[0]
  if (first) model.value = first.id
}

async function loadModels() {
  const r = await listModels()
  models.value = (r.data as LlmModelOption[]) || []
  // 选第一个 enabled 的 provider/model
  if (models.value.length > 0) {
    provider.value = models.value[0].provider
    model.value = models.value[0].id
  }
}

function renderContent(s: string) {
  if (!s) return ''
  return s.replace(/\n/g, '<br/>')
}

async function send() {
  if (!input.value.trim()) return
  const userMsg = input.value
  messages.value.push({ role: 'user', content: userMsg })
  input.value = ''
  loading.value = true

  try {
    const r = await chat([
      { role: 'system', content: '你是一位有帮助的 AI 助手。' },
      ...messages.value.map(m => ({ role: m.role, content: m.content }))
    ], { temperature: 0.5, maxTokens: 1024, scene: provider.value })
    messages.value.push({ role: 'assistant', content: r.data?.content ?? '(无响应)' })
    await nextTick()
    if (boxRef.value) boxRef.value.scrollTop = boxRef.value.scrollHeight
  } finally {
    loading.value = false
  }
}

function clear() {
  messages.value = []
}

onMounted(loadModels)
</script>

<style scoped>
.header { display: flex; justify-content: space-between; align-items: center; }
.model-picker { display: flex; gap: 8px; }
.messages { height: 400px; overflow-y: auto; padding: 16px; background: #fafafa; border-radius: 6px; margin-bottom: 12px; }
.msg { padding: 10px 14px; border-radius: 6px; margin-bottom: 8px; }
.msg.user { background: #fff; border: 1px solid #e0e0e0; }
.msg.assistant { background: #ecf5ff; }
.role { font-size: 12px; color: #999; margin-bottom: 4px; }
.content { line-height: 1.6; }
</style>
