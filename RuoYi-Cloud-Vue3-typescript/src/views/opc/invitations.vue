<template>
  <div class="page">
    <el-row :gutter="20" class="is-mobile-stack">
      <el-col :span="14">
        <el-card>
          <template #header>
            <div class="header">
              <span><el-icon><Promotion /></el-icon> 我的邀请码</span>
              <el-button type="primary" :loading="generating" @click="onGenerate">
                <el-icon><Plus /></el-icon> 生成新邀请码
              </el-button>
            </div>
          </template>

          <ResponsiveTable :data="invitations" :loading="loading" :columns="invitationColumns" empty-text="还没有邀请码">
            <template #actions="{ row }">
              <el-button link type="primary" :disabled="row.status !== 'ACTIVE'" @click="onShare(row)">
                生成分享卡
              </el-button>
            </template>
          </ResponsiveTable>

          <el-empty v-if="!loading && invitations.length === 0" description="还没有邀请码" />
        </el-card>
      </el-col>

      <el-col :span="10">
        <el-card>
          <template #header>
            <div class="header">
              <span><el-icon><Trophy /></el-icon> 邀请奖励</span>
            </div>
          </template>

          <div class="reward-stat">
            <div class="big">{{ stats.totalInvites }}</div>
            <div class="label">累计邀请人数</div>
          </div>
          <el-divider />
          <div class="reward-stat">
            <div class="big">¥ {{ stats.totalReward.toFixed(2) }}</div>
            <div class="label">累计获得奖励</div>
          </div>
          <el-divider />

          <el-alert type="info" :closable="false" show-icon>
            <p>每邀请一位好友加入，您将获得 <b>50 元</b> 钱包代金券，自动充值到您的默认公司钱包。</p>
          </el-alert>
        </el-card>
      </el-col>
    </el-row>

    <!-- 分享卡弹窗 -->
    <el-dialog v-model="shareDialog" title="分享邀请卡" width="540px" align-center>
      <SharePoster v-if="shareDialog" :invitation="sharingInvite" @close="shareDialog = false" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="OpcInvitations">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Promotion, Plus, CopyDocument, Trophy } from '@element-plus/icons-vue'
import { listMyInvitations, generateInvitation } from '@/api/opc/user'
import SharePoster from './components/SharePoster.vue'
import ResponsiveTable, { type Column } from '@/views/opc/components/ResponsiveTable.vue'

const loading = ref(false)
const generating = ref(false)
const invitations = ref<any[]>([])

const invitationColumns: Column[] = [
  {
    key: 'inviteCode',
    label: '邀请码',
    primary: true,
    width: 160,
    copyable: true,
    copyValue: (v) => `${window.location.origin}/opc/invite?code=${v}`,
  },
  {
    key: 'usedCount',
    label: '使用情况',
    width: 160,
    formatter: (v, r: any) => `${v || 0} / ${r.maxUses || 1}`,
  },
  {
    key: 'status',
    label: '状态',
    width: 100,
    type: 'tag',
    formatter: (v) => statusLabel(String(v)),
    tagMap: { ACTIVE: 'success', USED: 'info', EXPIRED: 'warning' },
  },
  { key: 'expireTime', label: '过期时间', type: 'date' },
  { key: 'createTime', label: '生成时间', type: 'date', hideOnMobile: true },
]

const shareDialog = ref(false)
const sharingInvite = ref<any>(null)

const stats = reactive({
  totalInvites: 0,
  totalReward: 0
})

async function load() {
  loading.value = true
  try {
    const r = await listMyInvitations()
    invitations.value = r.data || []
    stats.totalInvites = invitations.value.reduce((s, i) => s + (i.usedCount || 0), 0)
    stats.totalReward = stats.totalInvites * 50
  } finally {
    loading.value = false
  }
}

async function onGenerate() {
  if (invitations.value.filter(i => i.status === 'ACTIVE').length >= 50) {
    ElMessage.warning('最多持有 50 个有效邀请码')
    return
  }
  generating.value = true
  try {
    const r = await generateInvitation()
    ElMessage.success(`已生成：${r.data?.inviteCode}`)
    load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '生成失败')
  } finally {
    generating.value = false
  }
}

function onShare(row: any) {
  sharingInvite.value = row
  shareDialog.value = true
}

function statusLabel(s: string): string {
  if (s === 'ACTIVE') return '有效'
  if (s === 'USED') return '已使用'
  if (s === 'EXPIRED') return '已过期'
  return '失效'
}

onMounted(load)
</script>

<style scoped lang="scss">
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.code-text {
  font-family: 'Courier New', monospace;
  font-weight: 600;
  letter-spacing: 1px;
  margin-right: 4px;
}

.reward-stat {
  text-align: center;
  padding: 12px 0;

  .big {
    font-size: 32px;
    font-weight: 700;
    color: #409eff;
  }

  .label {
    font-size: 13px;
    color: #909399;
    margin-top: 4px;
  }
}
</style>
