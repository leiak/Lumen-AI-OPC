<template>
  <div class="page">
    <el-row :gutter="20" class="is-mobile-stack">
      <el-col :span="8">
        <el-card>
          <template #header><span>钱包余额</span></template>
          <div class="balance">¥ {{ wallet?.balance ?? '0.00' }}</div>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="冻结">¥ {{ wallet?.frozen }}</el-descriptions-item>
            <el-descriptions-item label="累计充值">¥ {{ wallet?.totalRecharge }}</el-descriptions-item>
            <el-descriptions-item label="累计消费">¥ {{ wallet?.totalConsume }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>

      <el-col :span="16">
        <el-card>
          <template #header><span>充值</span></template>
          <el-radio-group v-model="amount" size="large">
            <el-radio-button :label="50">¥ 50</el-radio-button>
            <el-radio-button :label="100">¥ 100</el-radio-button>
            <el-radio-button :label="500">¥ 500</el-radio-button>
            <el-radio-button :label="1000">¥ 1000</el-radio-button>
            <el-radio-button :label="5000">¥ 5000</el-radio-button>
          </el-radio-group>
          <div style="margin-top: 20px">
            <el-button type="primary" size="large" @click="recharge">立即充值（模拟）</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top: 20px">
      <template #header><span>订单历史</span></template>
      <ResponsiveTable :data="orders" :columns="orderColumns" empty-text="暂无订单" />
    </el-card>
  </div>
</template>

<script setup lang="ts" name="BillingWallet">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getWallet, recharge, listOrders } from '@/api/opc/billing'
import { listMyCompanies } from '@/api/opc/user'
import ResponsiveTable, { type Column } from '@/views/opc/components/ResponsiveTable.vue'

const wallet = ref<any>({})
const orders = ref<any[]>([])
const amount = ref(100)
const companyId = ref<number>()

const orderColumns: Column[] = [
  { key: 'orderNo', label: '订单号', primary: true, width: 180 },
  { key: 'title', label: '标题' },
  { key: 'amount', label: '金额', type: 'amount', width: 120, align: 'right' },
  {
    key: 'payMethod',
    label: '支付方式',
    width: 120,
    formatter: (v) =>
      ({ ALIPAY: '支付宝', WECHAT: '微信', BANK: '银行卡' } as any)[String(v)] || String(v || '-'),
  },
  {
    key: 'payStatus',
    label: '状态',
    width: 100,
    type: 'tag',
    tagMap: { PAID: 'success', PENDING: 'warning', REFUNDED: 'info', FAILED: 'danger' },
  },
  { key: 'createTime', label: '时间', type: 'date' },
]

async function load() {
  if (!companyId.value) return
  const w = await getWallet(companyId.value)
  wallet.value = w.data || {}
  const o = await listOrders(companyId.value)
  orders.value = o.data || []
}

async function init() {
  const r = await listMyCompanies()
  companyId.value = r.data?.[0]?.id
  load()
}

async function rechargeWallet() {
  if (!companyId.value) return
  const r = await recharge({ companyId: companyId.value, amount: amount.value, payMethod: 'ALIPAY' })
  if (r.code === 200) {
    ElMessage.success('充值成功')
    load()
  }
}

onMounted(init)
</script>

<style scoped>
.balance { font-size: 48px; font-weight: 600; color: #2b5fff; text-align: center; padding: 24px 0; }
</style>
