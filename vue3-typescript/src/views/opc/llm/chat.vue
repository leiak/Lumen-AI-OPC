<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="header">
          <span>AI 对话测试</span>
          <el-select v-model="model" style="width: 200px">
            <el-option v-for="m in models" :key="m.id" :label="`${m.name} (${m.priority})`" :value="m.id" />
          </el-select>
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
import { ref, nextTick, onMounted } from 'vue'
import { listModels, chat } from '@/api/opc/llm'

const messages = ref<any[]>([])
const input = ref('')
const model = ref('deepseek-v3')
const models = ref<any[]>([])
const loading = ref(false)
const boxRef = ref()

async function loadModels() {
  const r = await listModels()
  models.value = r.data || []
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
    ], { temperature: 0.5, maxTokens: 1024 })
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
.messages { height: 400px; overflow-y: auto; padding: 16px; background: #fafafa; border-radius: 6px; margin-bottom: 12px; }
.msg { padding: 10px 14px; border-radius: 6px; margin-bottom: 8px; }
.msg.user { background: #fff; border: 1px solid #e0e0e0; }
.msg.assistant { background: #ecf5ff; }
.role { font-size: 12px; color: #999; margin-bottom: 4px; }
.content { line-height: 1.6; }
</style>
