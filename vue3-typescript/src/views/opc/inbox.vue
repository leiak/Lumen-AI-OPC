<template>
  <div class="page inbox-page">
    <el-card>
      <template #header>
        <div class="header">
          <span class="title">
            <el-icon><Bell /></el-icon>
            通知中心
            <el-tag
              v-if="connected"
              type="success"
              size="small"
              effect="plain"
              class="conn-tag"
            >在线</el-tag>
            <el-tag v-else type="info" size="small" effect="plain" class="conn-tag">离线</el-tag>
          </span>
          <div class="actions">
            <el-badge :value="unread" :max="99" :hidden="unread === 0" class="unread-badge">
              <el-button :icon="ChatLineRound">未读 {{ unread }}</el-button>
            </el-badge>
            <el-button type="primary" :disabled="unread === 0 || loading" @click="markAllRead">
              全部已读
            </el-button>
            <el-button :icon="Refresh" @click="refresh">刷新</el-button>
          </div>
        </div>
      </template>

      <ResponsiveTable
        :data="inbox"
        :loading="loading"
        :columns="inboxColumns"
        empty-text="暂无通知"
        :action-width="120"
      >
        <template #actions="{ row }">
          <el-button
            v-if="row.readFlag === 0"
            link
            type="primary"
            size="small"
            @click="markRead(row.id)"
          >
            标为已读
          </el-button>
        </template>
      </ResponsiveTable>
    </el-card>
  </div>
</template>

<script setup lang="ts" name="OpcInbox">
import { Bell, ChatLineRound, Refresh } from '@element-plus/icons-vue'
import { useNotificationSocket } from '@/composables/useNotificationSocket'
import ResponsiveTable, { type Column } from '@/views/opc/components/ResponsiveTable.vue'

const { connected, inbox, unread, loading, refresh, markRead, markAllRead } =
  useNotificationSocket()

const inboxColumns: Column[] = [
  {
    key: 'title',
    label: '标题',
    primary: true,
    minWidth: 180,
  },
  {
    key: 'content',
    label: '内容',
    minWidth: 220,
    hideOnMobile: true,
  },
  {
    key: 'category',
    label: '分类',
    width: 120,
    type: 'tag',
    formatter: (v: any) => categoryLabel(String(v)),
    tagMap: {
      order_paid: 'success',
      inventory_low: 'danger',
      opportunity_assigned: 'warning',
      workflow_done: 'primary',
      system: 'info',
    },
  },
  {
    key: 'createTime',
    label: '时间',
    type: 'date',
    width: 160,
  },
  {
    key: 'readFlag',
    label: '状态',
    width: 100,
    type: 'tag',
    formatter: (v: any) => (Number(v) === 1 ? '已读' : '未读'),
    tagMap: { 0: 'warning', 1: 'info' },
  },
]

function categoryLabel(cat: string): string {
  switch (cat) {
    case 'order_paid': return '订单'
    case 'inventory_low': return '库存'
    case 'opportunity_assigned': return '商机'
    case 'workflow_done': return '工作流'
    case 'system': return '系统'
    default: return cat || '通知'
  }
}
</script>

<style scoped lang="scss">
.inbox-page {
  .header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
  }

  .title {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-weight: 600;
  }

  .conn-tag {
    margin-left: 4px;
  }

  .actions {
    display: inline-flex;
    gap: 8px;
    align-items: center;
    flex-wrap: wrap;
  }

  .unread-badge {
    :deep(.el-badge__content) {
      top: 8px;
      right: 4px;
    }
  }
}
</style>
