<template>
  <div class="page content-publish-list">
    <el-card>
      <template #header>
        <div class="header">
          <span class="title">
            <el-icon><Upload /></el-icon>
            发布记录
          </span>
          <div class="actions">
            <el-select v-model="statusFilter" placeholder="状态" clearable style="width: 130px" @change="load">
              <el-option label="待发布" value="PENDING" />
              <el-option label="成功" value="SUCCESS" />
              <el-option label="失败" value="FAILED" />
            </el-select>
            <el-input v-model="scriptIdFilter" placeholder="脚本 ID" clearable style="width: 140px" @keyup.enter="load" />
            <el-button :icon="Refresh" @click="load">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table :data="list" v-loading="loading" stripe empty-text="暂无发布记录">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="script_id" label="脚本 ID" width="90">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="$router.push(`/opc/content/script/${row.script_id}`)">
              #{{ row.script_id }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="发布标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="platform" label="平台" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.platform || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="platform_account_id" label="账号 ID" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="external_url" label="外部链接" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <a v-if="row.external_url" :href="row.external_url" target="_blank">{{ row.external_url }}</a>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="error_message" label="错误信息" min-width="180" show-overflow-tooltip />
        <el-table-column prop="published_at" label="发布时间" width="170" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'FAILED'"
              link
              type="warning"
              size="small"
              @click="onRetry(row)"
            >重试</el-button>
          </template>
        </el-table-column>
      </el-table>

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
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload, Refresh } from '@element-plus/icons-vue'
import {
  listPublishes,
  retryPublish,
  type OpcContentPublish,
  type ContentPublishStatus,
} from '@/api/opc/content'

const companyId = 1
const list = ref<OpcContentPublish[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

const statusFilter = ref<ContentPublishStatus | ''>('')
const scriptIdFilter = ref('')

async function load() {
  loading.value = true
  try {
    const r = await listPublishes({
      companyId,
      status: (statusFilter.value || undefined) as ContentPublishStatus | undefined,
      page: page.value,
      size: pageSize.value,
    })
    const payload = r.data || { rows: [], total: 0 }
    let rows = payload.rows || []
    if (scriptIdFilter.value.trim()) {
      const sid = Number(scriptIdFilter.value.trim())
      if (!Number.isNaN(sid)) rows = rows.filter((p) => Number(p.script_id) === sid)
    }
    list.value = rows
    total.value = scriptIdFilter.value.trim() ? rows.length : Number(payload.total || 0)
  } catch (e: any) {
    ElMessage.error(e?.msg || '加载发布记录失败')
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function onRetry(row: OpcContentPublish) {
  if (!row.id) return
  try {
    await retryPublish(row.id, companyId)
    ElMessage.success('已重试')
    load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '重试失败')
  }
}

function statusLabel(s?: string) {
  return ({ PENDING: '待发布', SUCCESS: '成功', FAILED: '失败' } as any)[String(s || '')] || String(s || '-')
}
function statusTagType(s?: string) {
  return ({ PENDING: 'warning', SUCCESS: 'success', FAILED: 'danger' } as any)[String(s || '')] || ''
}

onMounted(load)
</script>

<style scoped lang="scss">
.content-publish-list {
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
    align-items: center;
    flex-wrap: wrap;
  }
  .pager {
    display: flex;
    justify-content: flex-end;
    margin-top: 12px;
  }
}
</style>