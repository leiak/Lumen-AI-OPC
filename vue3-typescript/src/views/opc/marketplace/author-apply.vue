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

      <!-- 未关联企业 -->
      <el-result
        v-else-if="noTenant"
        icon="warning"
        title="当前账号未关联企业"
        sub-title="作者身份绑定企业，需要先加入或创建企业才能提交入驻申请。"
      >
        <template #extra>
          <el-button type="primary" @click="router.push('/opc/invite')">去加入企业</el-button>
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

        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="关于企业身份"
          description="申请所绑定的企业取自当前登录账号，不接受手动指定 —— 这是后端的硬性设计，避免冒名申请占用别家企业的唯一键。"
          class="tenant-hint"
        />

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
const noTenant = ref(false)
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

async function checkExisting() {
  checking.value = true
  try {
    // GET /author/me 在「已登录但未入驻」时返回 R.ok(null)（data 为 null），不是 404。
    // 未关联企业时后端返回 400「当前账号未关联企业」，据此引导去加入企业。
    const res = await getMyAuthor()
    author.value = res.data || null
    isAuthor.value = !!author.value
    noTenant.value = false
  } catch (e: any) {
    isAuthor.value = false
    noTenant.value = /未关联企业/.test(e?.message || '')
    if (!noTenant.value) {
      ElMessage.error('查询作者状态失败: ' + (e?.message || 'unknown'))
    }
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
  } catch (e: any) {
    ElMessage.error('提交失败: ' + (e?.message || 'unknown'))
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