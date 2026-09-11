<template>
  <div class="page customer-detail-page" v-loading="loading">
    <el-card v-if="customer">
      <template #header>
        <div class="header">
          <div class="title-block">
            <span class="title">{{ customer.name }}</span>
            <el-tag v-if="customer.level" :type="levelTagType(customer.level)" effect="plain">
              等级 {{ customer.level }}
            </el-tag>
            <el-tag v-if="customer.source" type="info" effect="plain">
              {{ sourceLabel(customer.source) }}
            </el-tag>
            <el-tag v-for="t in tagList" :key="t" size="small" effect="plain">{{ t }}</el-tag>
          </div>
          <el-button :icon="Back" @click="goBack">返回</el-button>
        </div>
      </template>

      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <!-- 基本信息 -->
        <el-tab-pane label="基本信息" name="basic">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="名称">{{ customer.name }}</el-descriptions-item>
            <el-descriptions-item label="等级">{{ customer.level || '-' }}</el-descriptions-item>
            <el-descriptions-item label="来源">{{ sourceLabel(customer.source) }}</el-descriptions-item>
            <el-descriptions-item label="负责人">{{ customer.ownerId ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="电话">{{ customer.phone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ customer.email || '-' }}</el-descriptions-item>
            <el-descriptions-item label="地址" :span="2">{{ customer.address || '-' }}</el-descriptions-item>
            <el-descriptions-item label="备注" :span="2">{{ customer.remark || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ customer.createTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ customer.updateTime || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <!-- 联系人 -->
        <el-tab-pane label="联系人" name="contact">
          <div class="tab-actions">
            <el-button type="primary" :icon="Plus" @click="openContact">添加联系人</el-button>
          </div>
          <ResponsiveTable
            :data="contacts"
            :loading="contactLoading"
            :columns="contactColumns"
            :action-width="200"
            empty-text="暂无联系人"
          >
            <template #actions="{ row }">
              <el-button v-if="!row.isPrimary" link type="primary" size="small" @click="onSetPrimary(row)">
                设为主联系人
              </el-button>
              <el-tag v-else type="success" size="small">主联系人</el-tag>
              <el-button link type="danger" size="small" @click="onDeleteContact(row)">删除</el-button>
            </template>
          </ResponsiveTable>
        </el-tab-pane>

        <!-- 跟进 -->
        <el-tab-pane label="跟进记录" name="follow">
          <div class="tab-actions">
            <el-button type="primary" :icon="Plus" @click="goAddFollowUp">添加跟进</el-button>
          </div>
          <el-timeline v-if="followUps.length">
            <el-timeline-item
              v-for="f in followUps"
              :key="f.id"
              :timestamp="f.createTime"
              placement="top"
              :type="followTypeColor(f.type)"
            >
              <el-card shadow="never">
                <div class="follow-head">
                  <el-tag size="small">{{ followTypeLabel(f.type) }}</el-tag>
                  <span class="follow-owner">负责人 #{{ f.ownerId ?? '-' }}</span>
                </div>
                <div class="follow-content">{{ f.content }}</div>
                <div v-if="f.result" class="follow-result">结果: {{ f.result }}</div>
              </el-card>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无跟进记录" />
        </el-tab-pane>

        <!-- 商机 -->
        <el-tab-pane label="商机" name="opportunity">
          <ResponsiveTable
            :data="opportunities"
            :loading="oppLoading"
            :columns="oppColumns"
            empty-text="暂无商机"
          >
            <template #actions="{ row }">
              <el-button link type="primary" size="small" @click="goOppKanban">查看看板</el-button>
            </template>
          </ResponsiveTable>
        </el-tab-pane>

        <!-- 合同 -->
        <el-tab-pane label="合同" name="contract">
          <ResponsiveTable
            :data="contracts"
            :loading="contractLoading"
            :columns="contractColumns"
            empty-text="暂无合同"
          />
        </el-tab-pane>

        <!-- 订单 -->
        <el-tab-pane label="订单" name="order">
          <ResponsiveTable
            :data="orders"
            :loading="orderLoading"
            :columns="orderColumns"
            empty-text="暂无订单"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="contactDialog" title="添加联系人" width="520px">
      <el-form :model="contactForm" label-width="80px">
        <el-form-item label="姓名" required>
          <el-input v-model="contactForm.name" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="contactForm.phone" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="contactForm.email" />
        </el-form-item>
        <el-form-item label="职位">
          <el-input v-model="contactForm.position" />
        </el-form-item>
        <el-form-item label="主联系人">
          <el-switch v-model="contactForm.isPrimary" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="contactForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="contactDialog = false">取消</el-button>
        <el-button type="primary" :loading="contactSaving" @click="onSaveContact">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="OpcCustomerDetail">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Back } from '@element-plus/icons-vue'
import {
  getCustomer,
  listContacts,
  createContact,
  deleteContact,
  setPrimaryContact,
  listFollowUps,
  listOpportunities,
  listContracts,
  listOrders,
  type CrmCustomer,
  type CrmContact,
  type CrmFollowUp,
  type CrmOpportunity,
  type CrmContract,
  type CrmOrder,
} from '@/api/opc/crm'
import ResponsiveTable, { type Column } from '@/views/opc/components/ResponsiveTable.vue'

const route = useRoute()
const router = useRouter()

const customerId = computed(() => Number(route.params.id))
const customer = ref<CrmCustomer | null>(null)
const loading = ref(false)
const activeTab = ref('basic')

const contacts = ref<CrmContact[]>([])
const contactLoading = ref(false)
const contactSaving = ref(false)
const contactDialog = ref(false)
const contactForm = reactive<CrmContact>({
  customerId: 0,
  name: '',
  phone: '',
  email: '',
  position: '',
  isPrimary: 0,
  remark: '',
})

const followUps = ref<CrmFollowUp[]>([])
const opportunities = ref<CrmOpportunity[]>([])
const oppLoading = ref(false)
const contracts = ref<CrmContract[]>([])
const contractLoading = ref(false)
const orders = ref<CrmOrder[]>([])
const orderLoading = ref(false)

const tagList = computed(() =>
  (customer.value?.tags || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
)

const contactColumns: Column[] = [
  { key: 'name', label: '姓名', primary: true, width: 120 },
  { key: 'position', label: '职位', width: 140, hideOnMobile: true },
  { key: 'phone', label: '电话', width: 140 },
  { key: 'email', label: '邮箱', hideOnMobile: true },
  {
    key: 'isPrimary',
    label: '主联系人',
    width: 100,
    type: 'tag',
    formatter: (v: any) => (Number(v) === 1 ? '是' : '否'),
    tagMap: { 1: 'success', 0: 'info' },
  },
]

const oppColumns: Column[] = [
  { key: 'name', label: '商机', primary: true, minWidth: 180 },
  { key: 'amount', label: '金额', type: 'amount', width: 140, align: 'right' },
  {
    key: 'stage',
    label: '阶段',
    width: 120,
    type: 'tag',
    tagMap: {
      LEAD: 'info',
      QUALIFIED: 'primary',
      PROPOSAL: 'warning',
      NEGOTIATION: 'warning',
      WON: 'success',
      LOST: 'danger',
    },
  },
  { key: 'score', label: 'LLM 评分', width: 100, align: 'right' },
  { key: 'expectedClose', label: '预计成交', type: 'date', width: 120 },
]

const contractColumns: Column[] = [
  { key: 'contractNo', label: '合同号', primary: true, width: 160 },
  { key: 'title', label: '标题', minWidth: 180 },
  { key: 'amount', label: '金额', type: 'amount', width: 140, align: 'right' },
  {
    key: 'status',
    label: '状态',
    width: 100,
    type: 'tag',
    tagMap: { DRAFT: 'info', ACTIVE: 'success', EXPIRED: 'warning', TERMINATED: 'danger' },
  },
  { key: 'signedAt', label: '签订', type: 'date', width: 120 },
  { key: 'expireAt', label: '到期', type: 'date', width: 120 },
]

const orderColumns: Column[] = [
  { key: 'orderNo', label: '订单号', primary: true, width: 160 },
  { key: 'total', label: '金额', type: 'amount', width: 140, align: 'right' },
  {
    key: 'status',
    label: '状态',
    width: 110,
    type: 'tag',
    tagMap: {
      PENDING: 'info',
      PAID: 'primary',
      SHIPPED: 'warning',
      COMPLETED: 'success',
      CANCELLED: 'danger',
    },
  },
  { key: 'paidAt', label: '支付时间', type: 'date', width: 160 },
  { key: 'createTime', label: '创建时间', type: 'date', width: 160 },
]

function levelTagType(l: string): 'danger' | 'warning' | 'info' | '' {
  return ({ A: 'danger', B: 'warning', C: 'info', D: '' } as any)[l] || ''
}
function sourceLabel(s?: string): string {
  return (
    ({ REFERRAL: '推荐', AD: '广告', WEBSITE: '官网', COLD_CALL: '陌拜', OTHER: '其他' } as any)[
      String(s || '')
    ] || s || '-'
  )
}
function followTypeLabel(t?: string): string {
  return ({ PHONE: '电话', EMAIL: '邮件', MEETING: '会面', OTHER: '其他' } as any)[String(t || '')] || t || '跟进'
}
function followTypeColor(t?: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  return (
    ({ PHONE: 'primary', EMAIL: 'success', MEETING: 'warning', OTHER: 'info' } as any)[
      String(t || '')
    ] || 'info'
  )
}

async function loadCustomer() {
  loading.value = true
  try {
    const r = await getCustomer(customerId.value)
    customer.value = r.data || null
  } catch (e: any) {
    ElMessage.error(e?.msg || '加载客户失败')
    customer.value = null
  } finally {
    loading.value = false
  }
}

async function loadContacts() {
  contactLoading.value = true
  try {
    const r = await listContacts(customerId.value)
    contacts.value = r.data || []
  } finally {
    contactLoading.value = false
  }
}

async function loadFollowUps() {
  try {
    const r = await listFollowUps({ customerId: customerId.value })
    followUps.value = (r.data || []).slice().sort((a, b) =>
      String(b.createTime || '').localeCompare(String(a.createTime || ''))
    )
  } catch {
    followUps.value = []
  }
}

async function loadOpportunities() {
  oppLoading.value = true
  try {
    const r = await listOpportunities({ customerId: customerId.value })
    opportunities.value = r.data || []
  } finally {
    oppLoading.value = false
  }
}

async function loadContracts() {
  contractLoading.value = true
  try {
    const r = await listContracts(customerId.value)
    contracts.value = r.data || []
  } finally {
    contractLoading.value = false
  }
}

async function loadOrders() {
  orderLoading.value = true
  try {
    const r = await listOrders({ customerId: customerId.value })
    orders.value = r.data || []
  } finally {
    orderLoading.value = false
  }
}

function onTabChange(name: string | number) {
  const tab = String(name)
  if (tab === 'contact' && !contacts.value.length) loadContacts()
  if (tab === 'follow' && !followUps.value.length) loadFollowUps()
  if (tab === 'opportunity' && !opportunities.value.length) loadOpportunities()
  if (tab === 'contract' && !contracts.value.length) loadContracts()
  if (tab === 'order' && !orders.value.length) loadOrders()
}

function openContact() {
  Object.assign(contactForm, {
    customerId: customerId.value,
    name: '',
    phone: '',
    email: '',
    position: '',
    isPrimary: contacts.value.length === 0 ? 1 : 0,
    remark: '',
  })
  contactDialog.value = true
}

async function onSaveContact() {
  if (!contactForm.name?.trim()) {
    ElMessage.warning('请输入姓名')
    return
  }
  contactSaving.value = true
  try {
    await createContact({ ...contactForm, customerId: customerId.value })
    ElMessage.success('已添加')
    contactDialog.value = false
    loadContacts()
  } catch (e: any) {
    ElMessage.error(e?.msg || '保存失败')
  } finally {
    contactSaving.value = false
  }
}

async function onDeleteContact(row: CrmContact) {
  if (!row.id) return
  await ElMessageBox.confirm(`删除联系人「${row.name}」？`, '提示', { type: 'warning' }).catch(() => {})
  try {
    await deleteContact(row.id)
    ElMessage.success('已删除')
    loadContacts()
  } catch (e: any) {
    ElMessage.error(e?.msg || '删除失败')
  }
}

async function onSetPrimary(row: CrmContact) {
  if (!row.id) return
  try {
    await setPrimaryContact(row.id, customerId.value)
    ElMessage.success('已设为主联系人')
    loadContacts()
  } catch (e: any) {
    ElMessage.error(e?.msg || '设置失败')
  }
}

function goAddFollowUp() {
  ElMessage.info('请到「跟进记录」页面统一添加')
}
function goOppKanban() {
  router.push('/opc/crm/opportunities')
}
function goBack() {
  router.push('/opc/crm/customers')
}

onMounted(() => {
  loadCustomer()
  loadContacts()
})
</script>

<style scoped lang="scss">
.customer-detail-page {
  .header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: 12px;
  }
  .title-block {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }
  .title {
    font-size: 18px;
    font-weight: 600;
  }
  .tab-actions {
    margin-bottom: 12px;
  }
  .follow-head {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 6px;
  }
  .follow-owner {
    font-size: 12px;
    color: #909399;
  }
  .follow-content {
    color: #303133;
    line-height: 1.6;
  }
  .follow-result {
    margin-top: 6px;
    font-size: 13px;
    color: #67c23a;
  }
}
</style>
