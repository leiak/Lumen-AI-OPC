<template>
  <div class="page" v-loading="loading">
    <el-row :gutter="20">
      <el-col :span="14">
        <el-card>
          <div class="head">
            <el-avatar :size="64" :src="agent?.iconUrl">{{ agent?.name?.[0] }}</el-avatar>
            <div class="meta">
              <h2>{{ agent?.name }}</h2>
              <el-tag>{{ agent?.category }}</el-tag>
              <el-rate v-model="agent.rating" disabled show-score />
            </div>
          </div>
          <el-divider />
          <h4>简介</h4>
          <p>{{ agent?.description }}</p>

          <h4>能力清单</h4>
          <el-space wrap>
            <el-tag v-for="cap in parseList(agent?.capabilities)" :key="cap" type="success">{{ cap }}</el-tag>
          </el-space>

          <h4>工具清单</h4>
          <el-space wrap>
            <el-tag v-for="tool in parseList(agent?.toolsConfig)" :key="tool" type="info">{{ tool }}</el-tag>
          </el-space>
        </el-card>
      </el-col>

      <el-col :span="10">
        <el-card>
          <template #header><span class="price-title">订阅</span></template>
          <div class="price">
            <div class="month">¥ {{ agent?.priceMonthly }} <small>/ 月</small></div>
            <div class="year">年付 ¥ {{ agent?.priceYearly }}（省 ¥ {{ saving }}）</div>
          </div>
          <el-form>
            <el-form-item label="套餐">
              <el-select v-model="hireType" style="width: 100%">
                <el-option label="月付" value="MONTHLY" />
                <el-option label="季付" value="QUARTERLY" />
                <el-option label="年付" value="YEARLY" />
                <el-option label="试用 7 天" value="TRIAL" />
              </el-select>
            </el-form-item>
            <el-form-item label="公司">
              <el-select v-model="companyId" placeholder="选择公司" style="width: 100%">
                <el-option v-for="c in companies" :key="c.id" :label="c.companyName" :value="c.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="昵称（可选）">
              <el-input v-model="nickname" placeholder="给数字员工起个名字" />
            </el-form-item>
            <el-button type="primary" size="large" style="width: 100%" @click="hire">立即雇佣</el-button>
          </el-form>
        </el-card>

        <el-card style="margin-top: 16px">
          <template #header><span>Token 单价</span></template>
          <p>输入：¥ {{ agent?.tokenPriceInput }} / 1k tokens</p>
          <p>输出：¥ {{ agent?.tokenPriceOutput }} / 1k tokens</p>
          <p>免费额度：{{ agent?.freeQuota }} tokens</p>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts" name="AgentDetail">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getAgentDetail, hireAgent } from '@/api/opc/agent'
import { listMyCompanies } from '@/api/opc/user'

const route = useRoute()
const router = useRouter()
const agent = ref<any>({})
const companies = ref<any[]>([])
const companyId = ref<number>()
const hireType = ref('MONTHLY')
const nickname = ref('')
const loading = ref(false)

const saving = computed(() => {
  const m = Number(agent.value.priceMonthly) || 0
  const y = Number(agent.value.priceYearly) || 0
  return m * 12 - y
})

function parseList(json?: string) {
  if (!json) return []
  try { return JSON.parse(json) } catch { return [] }
}

async function loadDetail() {
  loading.value = true
  try {
    const r = await getAgentDetail(Number(route.params.id))
    agent.value = r.data || {}
  } finally {
    loading.value = false
  }
}

async function loadCompanies() {
  const r = await listMyCompanies()
  companies.value = r.data || []
  if (companies.value.length) companyId.value = companies.value[0].id
}

async function hire() {
  if (!companyId.value) return ElMessage.warning('请选择公司')
  const r = await hireAgent({
    companyId: companyId.value,
    definitionId: Number(route.params.id),
    hireType: hireType.value,
    duration: 1,
    nickname: nickname.value
  })
  if (r.code === 200) {
    ElMessage.success('雇佣成功')
    router.push(`/opc/agent/instances`)
  }
}

onMounted(() => {
  loadDetail()
  loadCompanies()
})
</script>

<style scoped>
.head { display: flex; gap: 16px; align-items: center; }
.meta h2 { margin: 0 0 8px; }
.price-title { font-weight: 600; }
.price .month { font-size: 36px; color: #f56c6c; font-weight: 600; }
.price .year { color: #67c23a; margin-top: 4px; }
</style>
