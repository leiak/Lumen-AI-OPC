<template>
  <div class="page content-script-adapt">
    <el-card>
      <template #header>
        <div class="header">
          <span class="title">
            <el-icon><Position /></el-icon>
            平台适配
          </span>
          <el-button :icon="Back" @click="goBack">返回详情</el-button>
        </div>
      </template>

      <el-form :model="form" label-width="100px" v-loading="loading">
        <el-form-item label="源脚本 ID">
          <el-input :model-value="String(sourceScriptId)" readonly />
        </el-form-item>
        <el-form-item label="源标题">
          <el-input :model-value="sourceTitle" readonly placeholder="(加载中)" />
        </el-form-item>
        <el-form-item label="目标平台" required>
          <el-select v-model="form.target_platform" style="width: 100%">
            <el-option label="抖音" value="DOUYIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="语气风格">
          <el-input v-model="form.tone" placeholder="例如:活泼俏皮 / 干货专业 / 情感治愈" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="remark" type="textarea" :rows="2" placeholder="适配时关注的方向(可选)" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="onSubmit">生成适配版本</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="result" class="result-card">
      <template #header>
        <div class="header">
          <span class="title">适配结果</span>
          <el-button type="primary" @click="goResult">查看新脚本</el-button>
        </div>
      </template>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="新脚本 ID">#{{ result }}</el-descriptions-item>
        <el-descriptions-item label="平台">{{ form.target_platform }}</el-descriptions-item>
        <el-descriptions-item label="语气">{{ form.tone || '(默认)' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Back, Position } from '@element-plus/icons-vue'
import {
  adaptScript,
  getScript,
  type OpcContentScript,
} from '@/api/opc/content'

const route = useRoute()
const router = useRouter()
const companyId = 1

const sourceScriptId = Number(route.params.id)
const sourceTitle = ref('')
const loading = ref(false)

const form = reactive<{ target_platform: 'DOUYIN'; tone: string }>({
  target_platform: 'DOUYIN',
  tone: '',
})
const remark = ref('')

const submitting = ref(false)
const result = ref<number | null>(null)

async function loadSource() {
  loading.value = true
  try {
    const r = await getScript(sourceScriptId, companyId)
    const s: OpcContentScript | undefined = r.data
    if (s) sourceTitle.value = s.title || '(未命名)'
  } catch (e: any) {
    ElMessage.error(e?.msg || '加载源脚本失败')
  } finally {
    loading.value = false
  }
}

async function onSubmit() {
  if (!form.target_platform) {
    ElMessage.warning('请选择目标平台')
    return
  }
  submitting.value = true
  try {
    const r = await adaptScript({
      company_id: companyId,
      source_script_id: sourceScriptId,
      target_platform: form.target_platform,
      tone: form.tone || undefined,
    })
    result.value = Number(r.data || 0)
    ElMessage.success('已生成适配版本')
  } catch (e: any) {
    ElMessage.error(e?.msg || '适配失败')
  } finally {
    submitting.value = false
  }
}

function goResult() {
  if (result.value) router.push(`/opc/content/script/${result.value}`)
}

function goBack() {
  router.push(`/opc/content/script/${sourceScriptId}`)
}

onMounted(loadSource)
</script>

<style scoped lang="scss">
.content-script-adapt {
  display: flex;
  flex-direction: column;
  gap: 16px;
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
  .result-card {
    margin-top: 0;
  }
}
</style>