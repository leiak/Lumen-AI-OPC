<template>
  <div class="page follow-up-page" v-loading="loading">
    <el-card>
      <template #header>
        <div class="header">
          <span class="title">
            <el-icon><Clock /></el-icon>
            跟进记录
          </span>
          <div class="actions">
            <el-input
              v-model="customerIdFilter"
              placeholder="按客户ID筛选"
              clearable
              style="width: 160px"
              @keyup.enter="load"
            />
            <el-button type="primary" :icon="Plus" @click="openCreate">添加跟进</el-button>
            <el-button :icon="Refresh" @click="load">刷新</el-button>
          </div>
        </div>
      </template>

      <el-timeline v-if="timeline.length">
        <el-timeline-item
          v-for="f in timeline"
          :key="f.id"
          :timestamp="f.createTime"
          placement="top"
          :type="typeColor(f.type)"
          :hollow="!f.result"
        >
          <el-card shadow="never" class="follow-card">
            <div class="follow-head">
              <el-tag size="small">{{ typeLabel(f.type) }}</el-tag>
              <span class="owner">负责人 #{{ f.ownerId ?? '-' }}</span>
              <span class="customer-link">
                <el-link type="primary" @click="goCustomer(f.customerId)">
                  客户 #{{ f.customerId }}
                </el-link>
              </span>
              <span v-if="f.contactId" class="contact">联系人 #{{ f.contactId }}</span>
              <span class="spacer" />
              <el-button
                v-if="!f.result"
                link
                type="success"
                size="small"
                @click="openComplete(f)"
              >
                标记完成
              </el-button>
            </div>
            <div class="follow-content">{{ f.content }}</div>
            <div v-if="f.result" class="follow-result">完成结果: {{ f.result }}</div>
            <div v-if="f.completedAt" class="follow-completed">
              完成时间: {{ f.completedAt }}
            </div>
            <div v-if="f.nextAt" class="follow-next">下次跟进: {{ f.nextAt }}</div>
          </el-card>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无跟进记录" />
    </el-card>

    <!-- 新建跟进 -->
    <el-dialog v-model="createVisible" title="添加跟进" width="520px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="客户ID" required>
          <el-input v-model.number="form.customerId" type="number" />
        </el-form-item>
        <el-form-item label="联系人ID">
          <el-input v-model.number="form.contactId" type="number" placeholder="可选" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type" style="width: 100%">
            <el-option label="电话" value="PHONE" />
            <el-option label="邮件" value="EMAIL" />
            <el-option label="会面" value="MEETING" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input v-model="form.content" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="下次跟进">
          <el-input v-model="form.nextAt" placeholder="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onCreate">保存</el-button>
      </template>
    </el-dialog>

    <!-- 完成跟进 -->
    <el-dialog v-model="completeVisible" title="标记跟进完成" width="420px">
      <el-form :model="completeForm" label-width="80px">
        <el-form-item label="结果">
          <el-input v-model="completeForm.result" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="completeVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onComplete">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="OpcFollowUpTimeline">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Clock, Plus, Refresh } from '@element-plus/icons-vue'
import {
  listFollowUps,
  createFollowUp,
  completeFollowUp,
  type CrmFollowUp,
} from '@/api/opc/crm'

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const list = ref<CrmFollowUp[]>([])
const customerIdFilter = ref<string>('')

const timeline = computed(() =>
  list.value.slice().sort((a, b) =>
    String(b.createTime || '').localeCompare(String(a.createTime || ''))
  )
)

const createVisible = ref(false)
const form = reactive<Partial<CrmFollowUp>>({
  customerId: undefined,
  contactId: undefined,
  type: 'PHONE',
  content: '',
  nextAt: '',
})

const completeVisible = ref(false)
const completeForm = reactive<{ id: number | null; result: string }>({ id: null, result: '' })

function typeLabel(t?: string): string {
  return ({ PHONE: '电话', EMAIL: '邮件', MEETING: '会面', OTHER: '其他' } as any)[String(t || '')] || t || '跟进'
}
function typeColor(t?: string): 'primary' | 'success' | 'warning' | 'info' {
  return (
    ({ PHONE: 'primary', EMAIL: 'success', MEETING: 'warning', OTHER: 'info' } as any)[
      String(t || '')
    ] || 'info'
  )
}

async function load() {
  loading.value = true
  try {
    const params: { customerId?: number } = {}
    if (customerIdFilter.value) {
      const id = Number(customerIdFilter.value)
      if (!Number.isNaN(id)) params.customerId = id
    }
    const r = await listFollowUps(params)
    list.value = r.data || []
  } catch (e: any) {
    ElMessage.error(e?.msg || '加载跟进记录失败')
    list.value = []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    customerId: customerIdFilter.value ? Number(customerIdFilter.value) : undefined,
    contactId: undefined,
    type: 'PHONE',
    content: '',
    nextAt: '',
  })
  createVisible.value = true
}

async function onCreate() {
  if (!form.customerId || !form.content?.trim()) {
    ElMessage.warning('请填写客户ID 和内容')
    return
  }
  saving.value = true
  try {
    await createFollowUp(form as CrmFollowUp)
    ElMessage.success('已添加')
    createVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '保存失败')
  } finally {
    saving.value = false
  }
}

function openComplete(f: CrmFollowUp) {
  if (!f.id) return
  completeForm.id = f.id
  completeForm.result = ''
  completeVisible.value = true
}

async function onComplete() {
  if (!completeForm.id) return
  saving.value = true
  try {
    await completeFollowUp(completeForm.id, completeForm.result || undefined)
    ElMessage.success('已标记完成')
    completeVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '操作失败')
  } finally {
    saving.value = false
  }
}

function goCustomer(id?: number) {
  if (!id) return
  router.push(`/opc/crm/customers/${id}`)
}

onMounted(load)
</script>

<style scoped lang="scss">
.follow-up-page {
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
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }
  .follow-card {
    margin-bottom: 0;
  }
  .follow-head {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 6px;
    flex-wrap: wrap;
  }
  .owner,
  .contact {
    font-size: 12px;
    color: #909399;
  }
  .spacer {
    flex: 1;
  }
  .follow-content {
    color: #303133;
    line-height: 1.6;
    white-space: pre-wrap;
  }
  .follow-result {
    margin-top: 6px;
    font-size: 13px;
    color: #67c23a;
  }
  .follow-completed,
  .follow-next {
    margin-top: 4px;
    font-size: 12px;
    color: #909399;
  }
}
</style>
