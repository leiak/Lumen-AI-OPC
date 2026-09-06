<template>
  <el-drawer
    v-model="visibleRef"
    :direction="direction"
    :size="size"
    :with-header="false"
    :modal="modal"
    :modal-class="'opc-mobile-drawer-modal'"
    :close-on-click-modal="closeOnClickModal"
    :show-close="false"
    :append-to-body="appendToBody"
    @closed="handleClosed"
  >
    <div class="opc-mobile-drawer">
      <header v-if="$slots.header || title" class="opc-mobile-drawer__header">
        <slot name="header">
          <span class="opc-mobile-drawer__title">{{ title }}</span>
        </slot>
        <button
          type="button"
          class="opc-mobile-drawer__close"
          aria-label="关闭"
          @click="handleClose"
        >
          <el-icon><Close /></el-icon>
        </button>
      </header>
      <div class="opc-mobile-drawer__body">
        <slot />
      </div>
      <footer v-if="$slots.footer" class="opc-mobile-drawer__footer">
        <slot name="footer" />
      </footer>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Close } from '@element-plus/icons-vue'

/**
 * OPC 移动端抽屉包装器 (W1 Sub-task 5.1)
 *
 * <p>基于 Element Plus 的 <el-drawer>,封装 OPC 视图在 < 768px 下用的
 * 左/右/底滑出抽屉。供 Sub-task 5.2/5.3 表格卡片化、滤镜面板、详情
 * 侧栏等场景复用。
 *
 * <p>动画:Element Plus 默认 300ms cubic-bezier,通过 :size + direction
 * 即可控滑出方向;点击遮罩关闭 + ESC 关闭(el-drawer 内建)。
 *
 * <p>使用示例:
 * <pre>
 *   <MobileDrawer v-model="filtersOpen" title="筛选" direction="rtl">
 *     <FilterForm @apply="filtersOpen=false" />
 *   </MobileDrawer>
 * </pre>
 */
const props = withDefaults(
  defineProps<{
    visible: boolean
    direction?: 'ltr' | 'rtl' | 'ttb' | 'btt'
    size?: string | number
    title?: string
    modal?: boolean
    closeOnClickModal?: boolean
    appendToBody?: boolean
  }>(),
  {
    direction: 'ltr',
    size: '80%',
    title: '',
    modal: true,
    closeOnClickModal: true,
    appendToBody: true,
  },
)

const emit = defineEmits<{
  'update:visible': [value: boolean]
  closed: []
}>()

const visibleRef = computed({
  get: () => props.visible,
  set: (v: boolean) => emit('update:visible', v),
})

function handleClose(): void {
  visibleRef.value = false
}

function handleClosed(): void {
  emit('closed')
}
</script>

<style scoped>
.opc-mobile-drawer {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--el-bg-color, #fff);
}

.opc-mobile-drawer__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
  flex-shrink: 0;
}

.opc-mobile-drawer__title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}

.opc-mobile-drawer__close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background: transparent;
  color: var(--el-text-color-secondary, #909399);
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
  transition: background 0.2s;
}

.opc-mobile-drawer__close:hover,
.opc-mobile-drawer__close:focus-visible {
  background: var(--el-fill-color-light, #f5f7fa);
  outline: none;
}

.opc-mobile-drawer__close :deep(svg) {
  width: 18px;
  height: 18px;
}

.opc-mobile-drawer__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.opc-mobile-drawer__footer {
  padding: 12px 16px;
  border-top: 1px solid var(--el-border-color-lighter, #ebeef5);
  flex-shrink: 0;
  display: flex;
  gap: 8px;
}

/* 触摸目标 ≥ 44×44 (iOS HIG / AC 5.3) */
.opc-mobile-drawer__body :deep(.el-button),
.opc-mobile-drawer__footer :deep(.el-button) {
  min-height: 44px;
}
</style>
