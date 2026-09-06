<template>
  <div class="page">
    <el-row :gutter="20" class="is-mobile-stack">
      <el-col :span="12">
        <el-card>
          <template #header><span>OPC 用户画像</span></template>
          <el-form :model="profile" label-width="100px">
            <el-form-item label="用户类型">
              <el-select v-model="profile.userType" style="width: 100%">
                <el-option label="创业者" value="ENTREPRENEUR" />
                <el-option label="服务商" value="SERVICE" />
                <el-option label="企业主" value="OWNER" />
                <el-option label="投资者" value="INVESTOR" />
              </el-select>
            </el-form-item>
            <el-form-item label="真实姓名">
              <el-input v-model="profile.realName" />
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="profile.mobile" />
            </el-form-item>
            <el-form-item label="所在行业">
              <el-input v-model="profile.industry" />
            </el-form-item>
            <el-form-item label="所在城市">
              <el-input v-model="profile.city" />
            </el-form-item>
            <el-form-item label="个人简介">
              <el-input v-model="profile.bio" type="textarea" :rows="3" />
            </el-form-item>
            <el-form-item label="邀请码">
              <el-input v-model="profile.invitationCode" disabled />
            </el-form-item>
            <el-button type="primary" @click="save">保存</el-button>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="header">
              <span>我的公司</span>
              <el-button type="primary" size="small" @click="showCreate = true">+ 创建公司</el-button>
            </div>
          </template>
          <ResponsiveTable :data="companies" :columns="companyColumns" empty-text="还没有公司">
            <template #actions="{ row }">
              <!-- 暂无行内操作,保留扩展位 -->
            </template>
          </ResponsiveTable>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="showCreate" title="创建一人公司" width="540px">
      <el-form :model="newCompany" label-width="100px">
        <el-form-item label="公司名称"><el-input v-model="newCompany.companyName" /></el-form-item>
        <el-form-item label="公司类型">
          <el-select v-model="newCompany.companyType" style="width: 100%">
            <el-option label="个体工商户" value="INDIVIDUAL" />
            <el-option label="个人独资" value="PERSONAL_SOLE" />
            <el-option label="有限合伙" value="LLP" />
          </el-select>
        </el-form-item>
        <el-form-item label="行业"><el-input v-model="newCompany.industryName" /></el-form-item>
        <el-form-item label="省份"><el-input v-model="newCompany.province" /></el-form-item>
        <el-form-item label="城市"><el-input v-model="newCompany.city" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="newCompany.phone" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="createCompany">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="OpcUserProfile">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getMyProfile, saveProfile, listMyCompanies, createCompany as createCompanyApi } from '@/api/opc/user'
import ResponsiveTable, { type Column } from '@/views/opc/components/ResponsiveTable.vue'

const profile = ref<any>({})
const companies = ref<any[]>([])
const showCreate = ref(false)
const newCompany = ref<any>({ companyType: 'INDIVIDUAL' })

const companyColumns: Column[] = [
  { key: 'companyName', label: '公司名', primary: true },
  { key: 'companyType', label: '类型', width: 100 },
  { key: 'industryName', label: '行业' },
  {
    key: 'status',
    label: '状态',
    width: 100,
    type: 'tag',
    tagMap: { NORMAL: 'success', FROZEN: 'danger', REVOKED: 'info' },
  },
]

async function load() {
  const r = await getMyProfile()
  profile.value = r.data || {}
  const c = await listMyCompanies()
  companies.value = c.data || []
}

async function save() {
  await saveProfile(profile.value)
  ElMessage.success('保存成功')
  load()
}

async function createCompany() {
  await createCompanyApi(newCompany.value)
  ElMessage.success('创建成功')
  showCreate.value = false
  load()
}

onMounted(load)
</script>

<style scoped>
.header { display: flex; justify-content: space-between; align-items: center; }
</style>
