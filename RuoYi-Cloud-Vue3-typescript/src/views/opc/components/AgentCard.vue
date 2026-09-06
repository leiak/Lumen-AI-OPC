<template>
  <el-card class="agent-card" shadow="hover" @click="goDetail">
    <div class="card-head">
      <el-avatar :size="48" :src="agent.iconUrl">{{ agent.name?.[0] }}</el-avatar>
      <div class="meta">
        <div class="name">{{ agent.name }}</div>
        <el-tag size="small" :type="categoryColor">{{ categoryLabel }}</el-tag>
      </div>
    </div>
    <div class="desc">{{ agent.description }}</div>
    <div class="footer">
      <span class="price">¥ {{ agent.priceMonthly }} / 月</span>
      <el-button type="primary" size="small" @click.stop="hire">雇佣</el-button>
    </div>
  </el-card>
</template>

<script setup lang="ts" name="AgentCard">
import { computed } from 'vue'
import { useRouter } from 'vue-router'

const props = defineProps<{ agent: any }>()
const router = useRouter()

const categoryMap: Record<string, { label: string; color: string }> = {
  FINANCE: { label: '财务', color: 'danger' },
  ERP: { label: 'ERP', color: 'success' },
  CRM: { label: 'CRM', color: 'warning' },
  HR: { label: '人力', color: 'info' },
  ECOM: { label: '电商', color: 'primary' },
  CONTENT: { label: '内容', color: '' },
  VOICE: { label: '语音', color: 'success' },
  INSIGHT: { label: '数据', color: 'warning' }
}

const categoryLabel = computed(() => categoryMap[props.agent.category]?.label ?? props.agent.category)
const categoryColor = computed(() => categoryMap[props.agent.category]?.color ?? '')

function goDetail() {
  router.push(`/opc/agent/detail/${props.agent.id}`)
}
function hire(e: Event) {
  e.stopPropagation()
  router.push(`/opc/agent/hire/${props.agent.id}`)
}
</script>

<style scoped>
.agent-card { cursor: pointer; }
.card-head { display: flex; gap: 12px; align-items: center; margin-bottom: 12px; }
.meta .name { font-weight: 600; margin-bottom: 4px; }
.desc { color: #666; font-size: 13px; min-height: 40px; }
.footer { display: flex; justify-content: space-between; align-items: center; margin-top: 12px; }
.price { color: #f56c6c; font-weight: 600; }
</style>
