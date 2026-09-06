<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="header">
          <span class="title">Agent 商店</span>
          <el-input v-model="keyword" placeholder="搜索 Agent" clearable style="width: 240px" />
        </div>
      </template>
      <el-tabs v-model="category" @tab-change="loadList">
        <el-tab-pane label="全部" name="" />
        <el-tab-pane label="财务" name="FINANCE" />
        <el-tab-pane label="ERP" name="ERP" />
        <el-tab-pane label="CRM" name="CRM" />
        <el-tab-pane label="人力" name="HR" />
        <el-tab-pane label="电商" name="ECOM" />
        <el-tab-pane label="内容" name="CONTENT" />
        <el-tab-pane label="数据" name="INSIGHT" />
      </el-tabs>

      <el-row :gutter="20">
        <el-col v-for="agent in filtered" :key="agent.id" :span="6" style="margin-bottom: 16px">
          <AgentCard :agent="agent" />
        </el-col>
      </el-row>

      <el-empty v-if="!loading && filtered.length === 0" description="暂无 Agent" />
    </el-card>
  </div>
</template>

<script setup lang="ts" name="AgentMarket">
import { ref, computed, onMounted } from 'vue'
import { listMarket } from '@/api/opc/agent'
import AgentCard from '@/views/opc/components/AgentCard.vue'

const list = ref<any[]>([])
const category = ref('')
const keyword = ref('')
const loading = ref(false)

const filtered = computed(() => {
  let arr = list.value
  if (keyword.value) {
    arr = arr.filter((a: any) =>
      a.name?.includes(keyword.value) || a.description?.includes(keyword.value)
    )
  }
  return arr
})

async function loadList() {
  loading.value = true
  try {
    const r = await listMarket(category.value || undefined)
    list.value = r.data || []
  } finally {
    loading.value = false
  }
}

onMounted(loadList)
</script>

<style scoped>
.page { padding: 0; }
.header { display: flex; justify-content: space-between; align-items: center; }
.title { font-size: 18px; font-weight: 600; }
</style>
