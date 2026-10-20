<template>
  <div class="page author-apply">
    <el-card class="apply-hero">
      <h1>✍️ 申请成为市场作者</h1>
      <p>提交后即可上架智能体与功能模块。审核通过后你会获得作者后台入口，可以发布、管理并查看自己的商品数据。</p>
    </el-card>

    <el-card v-loading="checking" class="form-card">
      <!-- 已是作者 -->
      <el-result
        v-if="!checking && isAuthor"
        icon="success"
        title="你已经是市场作者"
        :sub-title="`作者名称：${author?.authorName || '-'}（入驻于 ${author?.createTime || '-'}）`"
      >
        <template #extra>
          <el-button type="primary" @click="router.push('/opc/marketplace/author-center')">进入作者后台</el-button>
          <el-button @click="router.push('/opc/marketplace/index')">逛逛市场</el-button>
        </template>
      </el-result>

      <!-- 申请表单 -->
      <el-form v-else ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="企业 / 团队名称" prop="authorName">
          <el-input v-model="form.authorName" maxlength="128" placeholder="将作为作者名展示在市场上" />
        </el-form-item>

        <el-form-item label="联系邮箱" prop="contactEmail">
          <el-input v-model="form.contactEmail" maxlength="128" placeholder="用于平台联系与结算" />
        </el-form-item>

        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="form.contactPhone" maxlength="32" placeholder="选填" />
        </el-form-item>

        <el-form-item label="头像 URL" prop="avatarUrl">
          <el-input v-model="form.avatarUrl" maxlength="512" placeholder="选填，正方形图片效果最好" />
        </el-form-item>

        <el-form-item label="作者简介" prop="intro">
          <el-input
            v-model="form.intro"
            type="textarea"
            :rows="6"
            maxlength="1024"
            show-word-limit
            placeholder="介绍一下你的团队、擅长领域与代表作品"
          />
        </el-form-item>

        <!-- 企业提示是常驻的，不是条件分支 —— 见下方 checkExisting 的注释。
             未关联企业的用户本来就无法通过 /author/me 探测出来（它只区分「是不是作者」），
             所以这里把「去完成这一步」的入口直接摆在表单里，而不是提交失败后才显示。
             链接指向 /opc/user/profile 而不是 /opc/invite：本页针对的正是「没有企业」的用户，
             而 /opc/invite 没有 ?code= 时 inviteInfo 拿不到东西，页面直接渲染「邀请码无效或已过期」
             （invite.vue 102-105 直接 return）；就算带码也没用 —— 邀请是别的公司发给他人的，
             用户自己想创建公司得走 profile 的「+ 创建公司」。 -->
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="关于企业身份"
          description="作者身份绑定企业：企业取自当前登录账号，不接受手动指定（这是后端的硬性设计，避免冒名申请占用别家企业的唯一键）。如果你的账号还没加入或创建企业，请先完成这一步，否则提交会被后端拒绝。"
          class="tenant-hint"
        >
          <el-button size="small" @click="router.push('/opc/user/profile')">去加入企业</el-button>
        </el-alert>

        <div class="form-footer">
          <el-button @click="router.back()">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="onSubmit">提交申请</el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  applyAuthor,
  getMyAuthor,
  type AuthorApplyPayload,
  type AuthorVO
} from '@/api/opc/marketplace'

const router = useRouter()

const formRef = ref<FormInstance>()
const submitting = ref(false)
const checking = ref(true)
const isAuthor = ref(false)
const author = ref<AuthorVO | null>(null)

const form = reactive<AuthorApplyPayload>({
  authorName: '',
  contactEmail: '',
  contactPhone: '',
  intro: '',
  avatarUrl: ''
})

const rules: FormRules = {
  authorName: [{ required: true, message: '企业 / 团队名称必填', trigger: 'blur' }],
  contactEmail: [
    { required: true, message: '联系邮箱必填', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ]
}

/**
 * 只判断「是不是作者」，不判断「有没有企业」。
 *
 * 为什么不分支处理「未关联企业」：`GET /author/me` 对「已登录但未入驻」返回 `R.ok(null)`，
 * 对「未关联企业」返回 `AjaxResult.code=400`，但 `utils/request.ts` 在 code!==200 的分支里
 * 只弹一个 ElNotification 然后 `Promise.reject('error')` —— reject 出去的是**裸字符串**，
 * 没有 `.message`，业务码和文案都已经丢了。所以在 catch 里靠 `e?.message` 嗅探「未关联企业」
 * 永远匹配不上，那样的分支是死代码（曾经就踩过这个坑）。
 *
 * 也不打算改 request.ts 来救它：那是全局拦截器，会影响全站所有视图的 reject 值，
 * 为了一个页面的一个分支去动它不划算。真正需要的提示文案后端已经通过通知弹出来了
 * （「当前账号未关联企业，请先加入或创建企业」），本页再用常驻 alert 提供「去加入企业」入口。
 *
 * 顺带说明另一个看似可行、实则会误伤的做法：不能用 `GET /opc/user/companies`
 * （listMyCompanies）来判断有没有企业 —— 它按 `owner_user_id` 过滤，
 * 而 `SecurityUtils.getCompanyId()` 是从 `opc_company_member`（M:N）里解析的，
 * 只是「优先取 OWNER 的那家」，普通成员同样能拿到 companyId。
 * 拿 owner 口径当租户口径，会把「别人公司的普通成员」误判成「没有企业」而挡掉申请。
 */
async function checkExisting() {
  checking.value = true
  try {
    const res = await getMyAuthor()
    author.value = res.data || null
    isAuthor.value = !!author.value
  } catch {
    // 失败一律降级为「按未入驻处理」，展示表单。
    // 失败原因（未关联企业 / 会话过期 / 后端异常）已由 request.ts 的通知或消息弹窗给出，这里不再重复报错。
    isAuthor.value = false
    author.value = null
  } finally {
    checking.value = false
  }
}

async function onSubmit() {
  if (!formRef.value) return
  const ok = await formRef.value.validate().catch(() => false)
  if (!ok) return
  submitting.value = true
  try {
    const res = await applyAuthor({
      authorName: form.authorName.trim(),
      contactEmail: form.contactEmail.trim(),
      contactPhone: form.contactPhone?.trim() || undefined,
      intro: form.intro?.trim() || undefined,
      avatarUrl: form.avatarUrl?.trim() || undefined
    })
    author.value = res.data || null
    isAuthor.value = true
    ElMessage.success('入驻申请已提交')
    router.push('/opc/marketplace/author-center')
  } catch {
    // 同上：request.ts 对 code!==200 只会 reject 一个裸字符串 'error'，
    // 拿不到 msg（未关联企业 / 重复申请 409 等文案都已由它的通知弹窗展示），这里不重复报错。
    ElMessage.error('提交失败，请留意页面上的错误提示')
  } finally {
    submitting.value = false
  }
}

onMounted(checkExisting)
</script>

<style scoped lang="scss">
$bp-sm: 768px;

.author-apply {
  padding: 16px;
  max-width: 860px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.apply-hero {
  background: linear-gradient(135deg, #6c5ce7 0%, #5b8def 100%);
  color: #fff;
  border: none;

  :deep(.el-card__body) {
    padding: 24px;
  }

  h1 {
    margin: 0 0 8px;
    font-size: 22px;
  }

  p {
    margin: 0;
    opacity: 0.9;
    line-height: 1.6;
  }
}

.form-card {
  :deep(.el-card__body) {
    padding: 24px;
  }
}

.tenant-hint {
  margin-bottom: 16px;
}

.form-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 8px;
}

// ---------- 移动端 (< 768px) ----------
@media (max-width: #{$bp-sm - 0.02}) {
  .author-apply {
    padding: 12px;
    gap: 12px;
  }

  .apply-hero :deep(.el-card__body) {
    padding: 16px;
  }

  .apply-hero h1 {
    font-size: 19px;
  }

  .form-card :deep(.el-card__body) {
    padding: 16px;
  }

  .form-card {
    // 窄屏下标签占一整行会把输入框挤到很窄
    :deep(.el-form-item) {
      display: flex;
      flex-direction: column;
    }

    :deep(.el-form-item__label) {
      width: auto !important;
      text-align: left;
    }

    :deep(.el-form-item__content) {
      margin-left: 0 !important;
    }
  }

  .form-footer {
    :deep(.el-button) {
      flex: 1;
      min-height: 44px;
    }
  }
}
</style>