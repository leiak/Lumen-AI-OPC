<template>
  <el-dropdown
    v-if="visible"
    trigger="click"
    class="company-switcher right-menu-item"
    @command="onSelect"
  >
    <span class="company-trigger">
      <el-icon><OfficeBuilding /></el-icon>
      <span class="company-name">{{ currentName }}</span>
      <el-icon class="company-arrow"><CaretBottom /></el-icon>
    </span>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item
          v-for="c in userStore.companies"
          :key="c.id"
          :command="c.id"
          :disabled="c.id === userStore.companyId"
        >
          <span class="company-item">{{ c.companyName }}</span>
          <span v-if="c.id === userStore.companyId" class="company-tag">当前</span>
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { OfficeBuilding, CaretBottom } from '@element-plus/icons-vue'
import useUserStore from '@/store/modules/user'
import { useCompanySwitch } from '@/composables/useCompanySwitch'

/**
 * 顶栏的公司（租户）切换器
 *
 * <p>解决的问题：登录时只能解析出<b>一家</b>默认公司（老板优先、其次 id 最小），
 * 于是多公司用户在前端只能看见默认那一家的数据，而且看不出自己现在在哪家公司。
 * 这里列出「我加入的公司」，选中即切换（服务端改的是登录态，全局生效）。</p>
 *
 * <p>列表按<b>成员关系</b>取而不是「我创办的」—— 被邀请进别人公司的员工
 * 也是有租户的人。</p>
 *
 * <p>取不到列表就整个不显示：一个装饰性控件不该把页面拖挂。</p>
 */
const userStore = useUserStore()
const { switchTo } = useCompanySwitch()

/** 没有任何公司 → 不显示（用户可能压根没加入任何公司） */
const visible = computed(() => userStore.companies.length > 0)

const currentName = computed(() => {
  const cur = userStore.companies.find(c => c.id === userStore.companyId)
  return cur?.companyName || '未关联企业'
})

onMounted(async () => {
  try {
    await userStore.loadCompanies()
  } catch {
    // 取不到就当作「没有可切换的公司」—— visible 为 false，组件不显示
  }
})

async function onSelect(companyId: number) {
  await switchTo(companyId)
}
</script>

<style lang="scss" scoped>
.company-switcher {
  .company-trigger {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 14px;
    color: #5a5e66;
    cursor: pointer;
    white-space: nowrap;
  }

  .company-name {
    max-width: 160px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .company-arrow {
    font-size: 12px;
  }

  .company-item {
    margin-right: 8px;
  }

  .company-tag {
    font-size: 12px;
    color: var(--el-color-primary);
  }
}
</style>
