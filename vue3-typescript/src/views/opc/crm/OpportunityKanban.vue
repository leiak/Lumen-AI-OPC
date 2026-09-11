<template>
  <div class="page kanban-page" v-loading="loading">
    <el-card>
      <template #header>
        <div class="header">
          <span class="title">
            <el-icon><TrendCharts /></el-icon>
            商机看板
          </span>
          <div class="actions">
            <el-button type="primary" :icon="Plus" @click="openCreate">新建商机</el-button>
            <el-button :icon="Refresh" @click="load">刷新</el-button>
          </div>
        </div>
      </template>

      <div class="kanban-board">
        <div v-for="stage in stages" :key="stage.key" class="kanban-column">
          <div class="column-head" :class="stage.headClass">
            <span>{{ stage.label }}</span>
            <el-badge :value="byStage[stage.key]?.length || 0" type="primary" />
          </div>
          <div class="column-body">
            <el-empty v-if="!byStage[stage.key]?.length" :description="`暂无 ${stage.label}`" :image-size="60" />
            <el-card
              v-for="opp in byStage[stage.key]"
              :key="opp.id"
              class="opp-card"
              shadow="hover"
            >
              <div class="opp-title">{{ opp.name }}</div>
              <div class="opp-amount">¥ {{ formatAmount(opp.amount) }}</div>
              <div class="opp-meta">
                <el-tag size="small" effect="plain">客户 #{{ opp.customerId }}</el-tag>
                <el-tag v-if="opp.expectedClose" size="small" type="info" effect="plain">
                  预计 {{ opp.expectedClose }}
                </el-tag>
              </div>
              <div v-if="opp.score" class="opp-score">
                <el-progress
                  :percentage="opp.score"
                  :stroke-width="10"
                  :color="scoreColor(opp.score)"
                />
                <span class="score-text">{{ opp.score }} 分</span>
              </div>
              <div v-if="opp.scoreReason" class="opp-reason">{{ opp.scoreReason }}</div>
              <div class="opp-actions">
                <el-button link type="primary" size="small" @click="onScore(opp)">LLM 评分</el-button>
                <el-button link type="warning" size="small" @click="openStage(opp)">推进</el-button>
                <el-button link type="info" size="small" @click="goCustomer(opp)">客户</el-button>
              </div>
            </el-card>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 新建商机 -->
    <el-dialog v-model="createVisible" title="新建商机" width="520px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="客户ID" required>
          <el-input v-model.number="form.customerId" type="number" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="金额">
          <el-input v-model.number="form.amount" type="number" />
        </el-form-item>
        <el-form-item label="阶段">
          <el-select v-model="form.stage" style="width: 100%">
            <el-option v-for="s in stages" :key="s.key" :label="s.label" :value="s.key" />
          </el-select>
        </el-form-item>
        <el-form-item label="预计成交">
          <el-input v-model="form.expectedClose" placeholder="YYYY-MM-DD" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onCreate">保存</el-button>
      </template>
    </el-dialog>

    <!-- 推进阶段 -->
    <el-dialog v-model="stageVisible" title="推进商机阶段" width="420px">
      <el-form :model="stageForm" label-width="80px">
        <el-form-item label="当前">
          <el-tag>{{ stageForm.currentLabel }}</el-tag>
        </el-form-item>
        <el-form-item label="目标">
          <el-select v-model="stageForm.target" style="width: 100%">
            <el-option
              v-for="s in nextStages"
              :key="s.key"
              :label="s.label"
              :value="s.key"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="stageForm.target === 'LOST'" label="失单原因">
          <el-input v-model="stageForm.reason" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stageVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onChangeStage">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="OpcOpportunityKanban">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Refresh, TrendCharts } from '@element-plus/icons-vue'
import {
  listOpportunities,
  createOpportunity,
  changeOpportunityStage,
  scoreOpportunity,
  type CrmOpportunity,
  type OpportunityStage,
} from '@/api/opc/crm'

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const list = ref<CrmOpportunity[]>([])

interface StageMeta {
  key: OpportunityStage
  label: string
  headClass: string
  /** 状态机下一步允许的目标 */
  next?: OpportunityStage[]
}

const stages: StageMeta[] = [
  { key: 'LEAD', label: '线索', headClass: 'head-info', next: ['QUALIFIED', 'LOST'] },
  { key: 'QUALIFIED', label: '已验证', headClass: 'head-primary', next: ['PROPOSAL', 'LOST'] },
  { key: 'PROPOSAL', label: '方案中', headClass: 'head-warning', next: ['NEGOTIATION', 'LOST'] },
  { key: 'NEGOTIATION', label: '谈判中', headClass: 'head-warning', next: ['WON', 'LOST'] },
  { key: 'WON', label: '成交', headClass: 'head-success', next: [] },
  { key: 'LOST', label: '失单', headClass: 'head-danger', next: ['LEAD'] },
]

const byStage = computed(() => {
  const map: Record<string, CrmOpportunity[]> = {}
  for (const s of stages) map[s.key] = []
  for (const o of list.value) {
    const k = (o.stage || 'LEAD') as OpportunityStage
    if (!map[k]) map[k] = []
    map[k].push(o)
  }
  return map
})

const createVisible = ref(false)
const form = reactive<Partial<CrmOpportunity>>({
  customerId: undefined,
  name: '',
  amount: 0,
  stage: 'LEAD',
  expectedClose: '',
})

const stageVisible = ref(false)
const stageForm = reactive<{
  opp: CrmOpportunity | null
  currentLabel: string
  target: OpportunityStage | ''
  reason: string
}>({ opp: null, currentLabel: '', target: '', reason: '' })

const nextStages = computed<StageMeta[]>(() => {
  if (!stageForm.opp) return []
  const cur = stageForm.opp.stage as OpportunityStage
  const meta = stages.find((s) => s.key === cur)
  if (!meta) return []
  return stages.filter((s) => (meta.next || []).includes(s.key))
})

async function load() {
  loading.value = true
  try {
    const r = await listOpportunities({})
    list.value = r.data || []
  } catch (e: any) {
    ElMessage.error(e?.msg || '加载商机失败')
    list.value = []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    customerId: undefined,
    name: '',
    amount: 0,
    stage: 'LEAD',
    expectedClose: '',
  })
  createVisible.value = true
}

async function onCreate() {
  if (!form.customerId || !form.name?.trim()) {
    ElMessage.warning('请填写客户ID 和名称')
    return
  }
  saving.value = true
  try {
    await createOpportunity(form as CrmOpportunity)
    ElMessage.success('已创建')
    createVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '创建失败')
  } finally {
    saving.value = false
  }
}

function openStage(opp: CrmOpportunity) {
  stageForm.opp = opp
  stageForm.currentLabel = stages.find((s) => s.key === opp.stage)?.label || String(opp.stage)
  stageForm.target = ''
  stageForm.reason = ''
  stageVisible.value = true
}

async function onChangeStage() {
  if (!stageForm.opp?.id || !stageForm.target) {
    ElMessage.warning('请选择目标阶段')
    return
  }
  saving.value = true
  try {
    await changeOpportunityStage(stageForm.opp.id, stageForm.target, stageForm.reason || undefined)
    ElMessage.success('阶段已更新')
    stageVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '更新失败')
  } finally {
    saving.value = false
  }
}

async function onScore(opp: CrmOpportunity) {
  if (!opp.id) return
  try {
    const r = await scoreOpportunity(opp.id)
    ElMessage.success(`LLM 评分: ${r.data?.score ?? '-'} 分`)
    load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '评分失败')
  }
}

function goCustomer(opp: CrmOpportunity) {
  if (!opp.customerId) return
  router.push(`/opc/crm/customers/${opp.customerId}`)
}

function formatAmount(v?: number): string {
  if (v === null || v === undefined) return '0.00'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function scoreColor(score: number): string {
  if (score >= 80) return '#67c23a'
  if (score >= 60) return '#e6a23c'
  return '#f56c6c'
}

onMounted(load)
</script>

<style scoped lang="scss">
.kanban-page {
  .header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: 12px;
  }
  .title {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-weight: 600;
  }
  .actions {
    display: inline-flex;
    gap: 8px;
  }
  .kanban-board {
    display: grid;
    grid-template-columns: repeat(6, minmax(0, 1fr));
    gap: 12px;
    overflow-x: auto;
  }
  .kanban-column {
    background: #f5f7fa;
    border-radius: 6px;
    display: flex;
    flex-direction: column;
    min-height: 360px;
  }
  .column-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 8px 12px;
    font-weight: 600;
    color: #fff;
    border-radius: 6px 6px 0 0;
  }
  .head-info { background: #909399; }
  .head-primary { background: #2b5fff; }
  .head-warning { background: #e6a23c; }
  .head-success { background: #67c23a; }
  .head-danger { background: #f56c6c; }
  .column-body {
    padding: 8px;
    display: flex;
    flex-direction: column;
    gap: 8px;
    flex: 1;
  }
  .opp-card {
    margin-bottom: 0;
  }
  .opp-title {
    font-weight: 600;
    margin-bottom: 4px;
  }
  .opp-amount {
    color: #2b5fff;
    font-weight: 600;
    margin-bottom: 6px;
  }
  .opp-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
    margin-bottom: 6px;
  }
  .opp-score {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 4px;
  }
  .score-text {
    font-size: 12px;
    color: #909399;
    white-space: nowrap;
  }
  .opp-reason {
    font-size: 12px;
    color: #606266;
    margin-bottom: 6px;
    line-height: 1.4;
  }
  .opp-actions {
    display: flex;
    justify-content: flex-end;
    gap: 4px;
  }
  @media (max-width: 1024px) {
    .kanban-board {
      grid-template-columns: repeat(3, minmax(220px, 1fr));
    }
  }
  @media (max-width: 600px) {
    .kanban-board {
      grid-template-columns: 1fr;
    }
  }
}
</style>
