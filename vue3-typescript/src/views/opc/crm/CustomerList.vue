<template>
  <div class="page customer-list-page">
    <el-card>
      <template #header>
        <div class="header">
          <span class="title">
            <el-icon><User /></el-icon>
            客户管理
          </span>
          <div class="actions">
            <el-button type="primary" :icon="Plus" @click="openCreate">新建客户</el-button>
            <el-button :icon="Refresh" @click="load">刷新</el-button>
          </div>
        </div>
      </template>

      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="名称 / 电话" clearable @keyup.enter="load" />
        </el-form-item>
        <el-form-item label="等级">
          <el-select v-model="query.level" placeholder="全部" clearable @change="load" style="width: 100px">
            <el-option label="A" value="A" />
            <el-option label="B" value="B" />
            <el-option label="C" value="C" />
            <el-option label="D" value="D" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="query.source" placeholder="全部" clearable @change="load" style="width: 140px">
            <el-option label="推荐" value="REFERRAL" />
            <el-option label="广告" value="AD" />
            <el-option label="官网" value="WEBSITE" />
            <el-option label="陌拜" value="COLD_CALL" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="query.tag" placeholder="如 VIP" clearable @keyup.enter="load" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">搜索</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <ResponsiveTable
        :data="list"
        :loading="loading"
        :columns="columns"
        :action-width="160"
        empty-text="暂无客户"
      >
        <template #actions="{ row }">
          <el-button link type="primary" size="small" @click="goDetail(row)">查看</el-button>
          <el-button link type="warning" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
        </template>
      </ResponsiveTable>

      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="load"
          @size-change="load"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑客户' : '新建客户'" width="640px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="客户公司名称" />
        </el-form-item>
        <el-form-item label="等级">
          <el-select v-model="form.level" placeholder="选择等级" style="width: 100%">
            <el-option label="A (核心)" value="A" />
            <el-option label="B (重点)" value="B" />
            <el-option label="C (普通)" value="C" />
            <el-option label="D (观察)" value="D" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="form.source" placeholder="选择来源" style="width: 100%">
            <el-option label="推荐" value="REFERRAL" />
            <el-option label="广告" value="AD" />
            <el-option label="官网" value="WEBSITE" />
            <el-option label="陌拜" value="COLD_CALL" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="form.tags" placeholder="逗号分隔: VIP,大客户" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="form.phone" placeholder="联系电话" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="联系邮箱" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="form.address" placeholder="联系地址" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="OpcCustomerList">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { User, Plus, Refresh } from '@element-plus/icons-vue'
import {
  listCustomers,
  createCustomer,
  updateCustomer,
  deleteCustomer,
  type CrmCustomer,
} from '@/api/opc/crm'
import ResponsiveTable, { type Column } from '@/views/opc/components/ResponsiveTable.vue'

const router = useRouter()
const list = ref<CrmCustomer[]>([])
const loading = ref(false)
const saving = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

const query = reactive({
  keyword: '',
  level: '',
  source: '',
  tag: '',
})

const dialogVisible = ref(false)
const editing = ref(false)
const form = reactive<CrmCustomer>({
  name: '',
  level: '',
  source: '',
  tags: '',
  phone: '',
  email: '',
  address: '',
  remark: '',
})

const columns: Column[] = [
  { key: 'name', label: '名称', primary: true, minWidth: 180 },
  {
    key: 'level',
    label: '等级',
    width: 80,
    type: 'tag',
    tagMap: { A: 'danger', B: 'warning', C: 'info', D: '' },
  },
  {
    key: 'source',
    label: '来源',
    width: 100,
    type: 'tag',
    formatter: (v: any) =>
      ({ REFERRAL: '推荐', AD: '广告', WEBSITE: '官网', COLD_CALL: '陌拜', OTHER: '其他' } as any)[
        String(v)
      ] || String(v || '-'),
    tagMap: { REFERRAL: 'success', AD: 'warning', WEBSITE: 'primary', COLD_CALL: 'info', OTHER: '' },
  },
  { key: 'tags', label: '标签', hideOnMobile: true },
  { key: 'phone', label: '电话', width: 140, hideOnMobile: true },
  { key: 'ownerId', label: '负责人', width: 90 },
]

async function load() {
  loading.value = true
  try {
    const r = await listCustomers({
      keyword: query.keyword || undefined,
      level: query.level || undefined,
      source: query.source || undefined,
      tag: query.tag || undefined,
    })
    const all = r.data || []
    total.value = all.length
    const start = (page.value - 1) * pageSize.value
    list.value = all.slice(start, start + pageSize.value)
  } catch (e: any) {
    ElMessage.error(e?.msg || '加载客户失败')
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.keyword = ''
  query.level = ''
  query.source = ''
  query.tag = ''
  page.value = 1
  load()
}

function resetForm() {
  Object.assign(form, {
    id: undefined,
    name: '',
    level: '',
    source: '',
    tags: '',
    phone: '',
    email: '',
    address: '',
    remark: '',
  })
}

function openCreate() {
  resetForm()
  editing.value = false
  dialogVisible.value = true
}

function openEdit(row: CrmCustomer) {
  resetForm()
  Object.assign(form, row)
  editing.value = true
  dialogVisible.value = true
}

async function onSave() {
  if (!form.name?.trim()) {
    ElMessage.warning('请输入客户名称')
    return
  }
  saving.value = true
  try {
    if (editing.value && form.id) {
      await updateCustomer(form.id, form)
      ElMessage.success('已更新')
    } else {
      await createCustomer(form)
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '保存失败')
  } finally {
    saving.value = false
  }
}

async function onDelete(row: CrmCustomer) {
  if (!row.id) return
  await ElMessageBox.confirm(`确认删除客户「${row.name}」？`, '提示', { type: 'warning' }).catch(() => {})
  try {
    await deleteCustomer(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '删除失败')
  }
}

function goDetail(row: CrmCustomer) {
  if (!row.id) return
  router.push(`/opc/crm/customers/${row.id}`)
}

onMounted(load)
</script>

<style scoped lang="scss">
.customer-list-page {
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
  .search-form {
    margin-bottom: 12px;
  }
  .pager {
    display: flex;
    justify-content: flex-end;
    margin-top: 12px;
  }
}
</style>
