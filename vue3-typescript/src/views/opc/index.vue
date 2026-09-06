<template>
  <div class="opc-home">
    <el-card class="hero" shadow="never">
      <div class="hero-content">
        <div class="hero-text">
          <h1>欢迎来到 OPC-Agent-Community</h1>
          <p>让 <strong>1 个人 + N 个数字员工</strong> 运转完整公司</p>
          <el-space wrap>
            <el-button type="primary" size="large" @click="$router.push('/opc/agent/market')">
              <el-icon><ShoppingCart /></el-icon> 浏览 Agent 商店
            </el-button>
            <el-button size="large" @click="$router.push('/opc/user/profile')">
              <el-icon><User /></el-icon> 完善我的画像
            </el-button>
          </el-space>
        </div>
        <div class="hero-stats">
          <el-statistic :value="stats.agentCount" title="已上架 Agent" />
          <el-statistic :value="stats.companyCount" title="已入驻公司" />
          <el-statistic :value="stats.tokenTotal" title="累计 Token 调用 (k)" />
        </div>
      </div>
    </el-card>

    <el-row :gutter="20" style="margin-top: 20px" class="is-mobile-stack">
      <el-col :span="8">
        <el-card>
          <template #header>
            <span><el-icon><Money /></el-icon> 我的钱包</span>
          </template>
          <div class="big-num">¥ {{ wallet?.balance ?? '0.00' }}</div>
          <el-button type="primary" plain @click="$router.push('/opc/billing/wallet')">充值</el-button>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <template #header>
            <span><el-icon><List /></el-icon> 我的 Agent 实例</span>
          </template>
          <div class="big-num">{{ instances.length }}</div>
          <el-button type="primary" plain @click="$router.push('/opc/agent/instances')">查看</el-button>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <template #header>
            <span><el-icon><Document /></el-icon> 今日凭证</span>
          </template>
          <div class="big-num">{{ voucherCount }}</div>
          <el-button type="primary" plain @click="$router.push('/opc/finance/vouchers')">凭证中心</el-button>
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top: 20px">
      <template #header>
        <span>推荐 Agent</span>
        <el-button text type="primary" @click="$router.push('/opc/agent/market')">查看全部</el-button>
      </template>
      <el-row :gutter="20" class="is-mobile-stack">
        <el-col v-for="agent in hotAgents" :key="agent.id" :span="6">
          <AgentCard :agent="agent" />
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup lang="ts" name="OpcHome">
import { ref, onMounted } from 'vue'
import { getWallet } from '@/api/opc/billing'
import { listMyInstances, listMarket } from '@/api/opc/agent'
import AgentCard from '@/views/opc/components/AgentCard.vue'

const wallet = ref<any>({})
const instances = ref<any[]>([])
const voucherCount = ref(0)
const hotAgents = ref<any[]>([])
const stats = ref({ agentCount: 8, companyCount: 0, tokenTotal: 0 })

onMounted(async () => {
  try {
    const r = await listMarket()
    hotAgents.value = (r.data || []).slice(0, 4)
    stats.value.agentCount = (r.data || []).length
  } catch (e) { /* ignore */ }

  try {
    const r = await listMyInstances()
    instances.value = r.data || []
  } catch (e) { /* ignore */ }
})
</script>

<style scoped>
.opc-home { padding: 0; }
.hero {
  background: linear-gradient(135deg, #2b5fff 0%, #6f42c1 100%);
  color: #fff;
}
.hero-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  gap: 16px;
  flex-wrap: wrap;
}
.hero-text h1 { font-size: 28px; margin: 0 0 8px; }
.hero-text p { margin: 0 0 16px; opacity: 0.9; }
.hero-stats { display: flex; gap: 40px; flex-wrap: wrap; }
.hero-stats :deep(.el-statistic__content) { color: #fff; font-size: 28px; }
.hero-stats :deep(.el-statistic__title) { color: rgba(255,255,255,0.85); }
.big-num { font-size: 28px; font-weight: 600; color: #2b5fff; margin: 12px 0; }

/* 响应式 (< 768px):hero 文字+统计 改为上下两栏 */
@media (max-width: 767px) {
  .hero-content {
    flex-direction: column;
    align-items: stretch;
    padding: 16px;
  }
  .hero-stats {
    justify-content: space-between;
    gap: 12px;
    margin-top: 12px;
  }
  .hero-stats :deep(.el-statistic__content) {
    font-size: 22px;
  }
}
</style>
