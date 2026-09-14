<template>
  <div class="page content-platform-account">
    <el-card>
      <template #header>
        <div class="header">
          <span class="title">
            <el-icon><User /></el-icon>
            平台账号
          </span>
          <div class="actions">
            <el-button :icon="Refresh" @click="load">刷新</el-button>
            <el-button type="primary" :icon="Link" @click="onBindDouyin">绑定抖音账号</el-button>
          </div>
        </div>
      </template>

      <el-table :data="list" v-loading="loading" stripe empty-text="暂未绑定平台账号">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="platform" label="平台" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.platform || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="nickname" label="昵称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="open_id" label="Open ID" min-width="180" show-overflow-tooltip />
        <el-table-column prop="scope" label="授权范围" min-width="160" show-overflow-tooltip />
        <el-table-column prop="access_token_expires_at" label="Access Token 过期" width="170">
          <template #default="{ row }">
            <el-tag size="small" :type="expiresTagType(row.access_token_expires_at)" effect="plain">
              {{ formatExpiry(row.access_token_expires_at) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="bound_at" label="绑定时间" width="170" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="onRefresh(row)">刷新 Token</el-button>
            <el-button link type="danger" size="small" @click="onUnbind(row)">解绑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { User, Refresh, Link } from '@element-plus/icons-vue'
import {
  listPlatformAccounts,
  refreshPlatformToken,
  deletePlatformAccount,
  type OpcContentPlatformAccount,
} from '@/api/opc/content'

const companyId = 1
const list = ref<OpcContentPlatformAccount[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const r = await listPlatformAccounts(companyId)
    list.value = r.data || []
  } catch (e: any) {
    ElMessage.error(e?.msg || '加载账号失败')
    list.value = []
  } finally {
    loading.value = false
  }
}

async function onBindDouyin() {
  await ElMessageBox.confirm(
    '将跳转至抖音 OAuth 授权页,请在浏览器中完成授权。是否继续?',
    '绑定抖音账号',
    { type: 'info' }
  ).catch(() => {})
  try {
    const authUrl = `/opc/content/platform-account/oauth/douyin/authorize?companyId=${companyId}`
    window.open(authUrl, '_blank')
    ElMessage.info('请在弹出窗口完成授权,完成后回到此页刷新')
  } catch (e: any) {
    ElMessage.error(e?.msg || '跳转失败')
  }
}

async function onRefresh(row: OpcContentPlatformAccount) {
  if (!row.id) return
  try {
    await refreshPlatformToken(row.id, companyId)
    ElMessage.success('Token 刷新请求已提交')
    load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '刷新失败')
  }
}

async function onUnbind(row: OpcContentPlatformAccount) {
  if (!row.id) return
  await ElMessageBox.confirm(
    `确认解绑账号「${row.nickname || row.open_id}」？解绑后将无法继续发布`,
    '提示',
    { type: 'warning' }
  ).catch(() => {})
  try {
    await deletePlatformAccount(row.id, companyId)
    ElMessage.success('已解绑')
    load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '解绑失败')
  }
}

function formatExpiry(s?: string) {
  if (!s) return '-'
  const t = Date.parse(s)
  if (Number.isNaN(t)) return s
  const days = Math.round((t - Date.now()) / 86400000)
  if (days < 0) return `已过期 ${-days} 天`
  if (days <= 7) return `${days} 天后过期`
  return s
}

function expiresTagType(s?: string) {
  if (!s) return ''
  const t = Date.parse(s)
  if (Number.isNaN(t)) return ''
  const days = Math.round((t - Date.now()) / 86400000)
  if (days < 0) return 'danger'
  if (days <= 7) return 'warning'
  return 'success'
}

function statusLabel(s?: string) {
  return ({ ACTIVE: '正常', EXPIRED: '已过期', REVOKED: '已撤销' } as any)[String(s || '')] || String(s || '-')
}
function statusTagType(s?: string) {
  return ({ ACTIVE: 'success', EXPIRED: 'warning', REVOKED: 'danger' } as any)[String(s || '')] || ''
}

onMounted(load)
</script>

<style scoped lang="scss">
.content-platform-account {
  .header {
    display: flex;
    justify-content: space-between;
    align-items: center;
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
}
</style>