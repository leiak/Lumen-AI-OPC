<template>
  <div class="invite-page">
    <div class="invite-bg" />

    <div class="invite-content">
      <header class="invite-header">
        <div class="logo">OPC</div>
        <h1>OPC-Agent-Community</h1>
        <p class="subtitle">一人公司 + N 个数字员工</p>
      </header>

      <el-card v-loading="loading" class="invite-card">
        <template v-if="inviteInfo">
          <div class="inviter-row">
            <el-avatar :size="64" :src="inviteInfo.inviter?.avatarUrl || defaultAvatar">
              {{ (inviteInfo.inviter?.realName || 'U').charAt(0) }}
            </el-avatar>
            <div class="inviter-info">
              <div class="name">{{ inviteInfo.inviter?.realName || 'OPC 用户' }} 邀请你加入</div>
              <div class="meta">
                <el-tag v-if="inviteInfo.inviter?.industry" size="small" type="info">
                  {{ inviteInfo.inviter.industry }}
                </el-tag>
                <el-tag v-if="inviteInfo.inviter?.city" size="small" type="info">
                  {{ inviteInfo.inviter.city }}
                </el-tag>
              </div>
            </div>
          </div>

          <el-divider />

          <div class="benefits">
            <h3>🎁 邀请福利</h3>
            <ul>
              <li>✅ 立即获得 <b>50 元</b> 钱包代金券（自动到账）</li>
              <li>✅ 加入邀请人的公司，成为团队成员</li>
              <li>✅ 7 天免费体验全部 Agent</li>
              <li>✅ 专属 1 对 1 新手引导</li>
            </ul>
          </div>

          <div v-if="isLoggedIn" class="action-row">
            <el-button type="primary" size="large" :loading="accepting" @click="onAccept">
              立即接受邀请
            </el-button>
            <div class="expire">邀请码 {{ code }} · 有效期至 {{ expireDate }}</div>
          </div>

          <div v-else class="action-row">
            <el-button type="primary" size="large" @click="goRegister">
              注册并接受邀请
            </el-button>
            <el-button size="large" @click="goLogin">
              已有账号 · 登录
            </el-button>
            <div class="expire">邀请码 {{ code }} · 有效期至 {{ expireDate }}</div>
          </div>
        </template>

        <el-result v-else-if="!loading" icon="error" title="邀请码无效或已过期">
          <template #sub-title>
            <div>请检查链接是否完整，或联系邀请人重新生成</div>
          </template>
          <template #extra>
            <el-button type="primary" @click="goHome">返回首页</el-button>
          </template>
        </el-result>
      </el-card>

      <footer class="invite-footer">
        <span>© 2026 OPC-Agent-Community</span>
      </footer>
    </div>
  </div>
</template>

<script setup lang="ts" name="OpcInvite">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getInfo } from '@/api/login'
import { getInvitationPublic, acceptInvitation } from '@/api/opc/user'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const accepting = ref(false)
const inviteInfo = ref<any>(null)
const code = computed(() => String(route.query.code || ''))
const expireDate = computed(() => {
  if (!inviteInfo.value?.expireTime) return '-'
  return new Date(inviteInfo.value.expireTime).toLocaleDateString('zh-CN')
})

const isLoggedIn = ref(false)
const defaultAvatar = 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAxMDAgMTAwIj48Y2lyY2xlIGN4PSI1MCIgY3k9IjUwIiByPSI1MCIgZmlsbD0iIzM0OThkYiIvPjwvdGV4dD48L3N2Zz4='

async function loadInvitation() {
  if (!code.value) {
    loading.value = false
    return
  }
  try {
    const r = await getInvitationPublic(code.value)
    inviteInfo.value = r.data
  } catch (e: any) {
    inviteInfo.value = null
  } finally {
    loading.value = false
  }
}

async function checkLogin() {
  try {
    const u: any = await getInfo()
    isLoggedIn.value = !!u?.user?.userId
  } catch {
    isLoggedIn.value = false
  }
}

async function onAccept() {
  accepting.value = true
  try {
    const r = await acceptInvitation(code.value)
    ElMessage.success(`接受成功！邀请人获得 ${r.data?.rewardAmount || 50} 元代金券`)
    setTimeout(() => router.push('/opc'), 1200)
  } catch (e: any) {
    ElMessage.error(e?.msg || '接受失败')
  } finally {
    accepting.value = false
  }
}

function goRegister() {
  router.push({ path: '/register', query: { inviteCode: code.value } })
}

function goLogin() {
  router.push({ path: '/login', query: { redirect: route.fullPath } })
}

function goHome() {
  router.push('/')
}

onMounted(async () => {
  await Promise.all([loadInvitation(), checkLogin()])
})
</script>

<style scoped lang="scss">
.invite-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: #f5f7fa;
}

.invite-bg {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  opacity: 0.95;
  z-index: 0;
}

.invite-content {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 520px;
}

.invite-header {
  text-align: center;
  color: #fff;
  margin-bottom: 24px;

  .logo {
    display: inline-block;
    width: 56px;
    height: 56px;
    line-height: 56px;
    background: rgba(255, 255, 255, 0.2);
    backdrop-filter: blur(10px);
    border-radius: 14px;
    font-size: 18px;
    font-weight: 700;
    margin-bottom: 12px;
  }

  h1 {
    margin: 0 0 4px;
    font-size: 24px;
    font-weight: 600;
  }

  .subtitle {
    margin: 0;
    opacity: 0.85;
    font-size: 13px;
  }
}

.invite-card {
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
  border: none;
}

.inviter-row {
  display: flex;
  gap: 16px;
  align-items: center;

  .inviter-info {
    flex: 1;

    .name {
      font-size: 16px;
      font-weight: 500;
      margin-bottom: 8px;
    }

    .meta {
      display: flex;
      gap: 6px;
      flex-wrap: wrap;
    }
  }
}

.benefits {
  h3 {
    margin: 0 0 12px;
    font-size: 15px;
  }

  ul {
    margin: 0;
    padding-left: 20px;
    color: #4c4d4f;
    font-size: 14px;
    line-height: 1.9;
  }
}

.action-row {
  text-align: center;
  margin-top: 8px;

  .el-button {
    width: 100%;
    margin-bottom: 8px;
  }

  .expire {
    margin-top: 12px;
    font-size: 12px;
    color: #909399;
  }
}

.invite-footer {
  text-align: center;
  color: rgba(255, 255, 255, 0.6);
  font-size: 12px;
  margin-top: 24px;
}

@media (max-width: 480px) {
  .invite-header h1 { font-size: 20px; }
  .invite-card { margin: 0 -8px; }
}
</style>
