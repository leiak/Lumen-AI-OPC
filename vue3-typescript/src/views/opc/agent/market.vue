<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="header">
          <div class="heading">
            <span class="title">官方数字员工</span>
            <el-tag size="small" type="success">平台直营</el-tag>
          </div>
          <el-input v-model="keyword" placeholder="搜索数字员工" clearable style="width: 240px" />
        </div>
      </template>

      <!--
        这一页和 /opc/marketplace/index(开发者集市)是两个不同的目录:
          本页      = opc_agent_definition(published=1),平台自营,表里没有作者列
          开发者集市 = mp_publication,开发者自己上架的商品(智能体 + 功能模块)
        名字和徽标都在回答同一个问题:这两页有什么不一样。
      -->
      <p class="subtitle">
        平台自营的数字员工目录 —— 官方出品、统一定价,雇了就能用。
        开发者自己上架的商品在<router-link to="/opc/marketplace/index">开发者集市</router-link>。
      </p>
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
.heading { display: flex; align-items: center; gap: 8px; }
.title { font-size: 18px; font-weight: 600; }
.subtitle { margin: 0 0 12px; font-size: 13px; color: #6b7280; }
.subtitle a { color: #5b8def; text-decoration: none; }
.subtitle a:hover { text-decoration: underline; }
</style>
