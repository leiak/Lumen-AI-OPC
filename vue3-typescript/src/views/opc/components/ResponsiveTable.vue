<template>
  <div class="opc-responsive-table" :class="{ 'is-mobile': isMobile }">
    <!-- 桌面端 (>= 768px):保留 el-table -->
    <el-table
      v-if="!isMobile"
      :data="data"
      v-loading="loading"
      :empty-text="emptyText"
      v-bind="$attrs"
    >
      <el-table-column
        v-for="col in visibleColumnsDesktop"
        :key="col.key"
        :prop="col.key"
        :label="col.label"
        :width="col.width"
        :min-width="col.minWidth"
        :align="col.align || 'left'"
      >
        <template #default="{ row }">
          <component
            :is="resolveRenderer(col)"
            :value="row[col.key]"
            :row="row"
            :column="col"
          />
        </template>
      </el-table-column>

      <el-table-column
        v-if="$slots.actions"
        label="操作"
        :width="actionWidth"
        :align="'center'"
        :fixed="$attrs.fixed as any"
      >
        <template #default="{ row }">
          <slot name="actions" :row="row" />
        </template>
      </el-table-column>

      <template #empty>
        <slot name="empty">
          <el-empty :description="emptyText" />
        </slot>
      </template>
    </el-table>

    <!-- 移动端 (< 768px):每行一张卡片 -->
    <div v-else class="opc-responsive-table__cards" v-loading="loading">
      <el-empty v-if="!loading && data.length === 0" :description="emptyText" />
      <el-card
        v-for="(row, idx) in data"
        :key="rowKey ? row[rowKey] : idx"
        class="opc-responsive-table__card"
        shadow="never"
      >
        <header
          v-if="primaryColumn"
          class="opc-responsive-table__card-title"
        >
          <component
            :is="resolveRenderer(primaryColumn)"
            :value="row[primaryColumn.key]"
            :row="row"
            :column="primaryColumn"
          />
        </header>

        <div class="opc-responsive-table__card-grid">
          <div
            v-for="col in visibleColumnsMobile"
            :key="col.key"
            class="opc-responsive-table__card-row"
            :class="['align-' + (col.align || 'left')]"
          >
            <span class="opc-responsive-table__card-label">{{ col.label }}</span>
            <span class="opc-responsive-table__card-value">
              <component
                :is="resolveRenderer(col)"
                :value="row[col.key]"
                :row="row"
                :column="col"
              />
            </span>
          </div>
        </div>

        <footer v-if="$slots.actions" class="opc-responsive-table__card-actions">
          <slot name="actions" :row="row" />
        </footer>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, h, defineComponent, useSlots } from 'vue'
import { useWindowSize } from '@vueuse/core'
import { ElTag } from 'element-plus'

/**
 * OPC 响应式表格 (W1 Sub-task 5.2)
 *
 * <p>>= 768px:渲染 <el-table>,字段由 columns 数组驱动;< 768px:渲染
 * <el-card> 卡片列表,主标题来自 primary 列,其余字段以 "label: value"
 * 网格平铺,操作按钮置于卡片底部。
 *
 * <p>每列支持的类型:
 * <ul>
 *   <li>text (默认) — 原值</li>
 *   <li>tag — 用 el-tag 包裹,tagMap 映射值 -> tag type (success/info/warning/danger)</li>
 *   <li>date — yyyy-MM-dd HH:mm 格式化</li>
 *   <li>number — 千分位格式化 (整数无小数,有小数则保留)</li>
 *   <li>amount — 数字 + ¥ 前缀 + 千分位</li>
 * </ul>
 *
 * <p>custom 列可通过 formatter 函数返回字符串。
 *
 * <p>操作列定义在外部 #actions slot,同时复用于桌面表格尾部单元格 + 移动卡片底部。
 */

export interface Column {
  key: string
  label: string
  type?: 'text' | 'tag' | 'date' | 'number' | 'amount'
  width?: number | string
  minWidth?: number | string
  align?: 'left' | 'center' | 'right'
  primary?: boolean
  hideOnMobile?: boolean
  /** 仅 type='tag' 生效;{value: elTagType} */
  tagMap?: Record<string, string>
  /** 自定义格式化 */
  formatter?: (value: any, row: any) => string
  /** 复制按钮:type='text' 时附加一键复制的小图标按钮 */
  copyable?: boolean
  /** 复制时写入剪贴板的内容,默认是 raw 原值 */
  copyValue?: (value: any, row: any) => string
}

const props = withDefaults(
  defineProps<{
    data: any[]
    columns: Column[]
    loading?: boolean
    emptyText?: string
    rowKey?: string
    /** 操作列宽度,默认 200 */
    actionWidth?: number
    /** 自定义响应断点,默认 768 */
    breakpoint?: number
  }>(),
  {
    loading: false,
    emptyText: '暂无数据',
    rowKey: 'id',
    actionWidth: 200,
    breakpoint: 768,
  },
)

defineOptions({ inheritAttrs: false })
const slots = useSlots()

const { width } = useWindowSize()
const isMobile = computed(() => width.value - 1 < props.breakpoint)

// 所有列(桌面/移动过滤后)
const visibleColumnsDesktop = computed(() => props.columns)
const visibleColumnsMobile = computed(() =>
  props.columns.filter((c) => !c.hideOnMobile),
)
const primaryColumn = computed(
  () => props.columns.find((c) => c.primary) || null,
)

// 工具函数
function formatDate(v: any): string {
  if (!v) return '-'
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return String(v)
  const pad = (n: number) => (n < 10 ? '0' + n : String(n))
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function formatNumber(v: any): string {
  if (v === null || v === undefined || v === '') return '-'
  const num = Number(v)
  if (Number.isNaN(num)) return String(v)
  const hasDecimal = String(v).indexOf('.') !== -1
  return hasDecimal
    ? num.toLocaleString('zh-CN', { minimumFractionDigits: 1, maximumFractionDigits: 4 })
    : num.toLocaleString('zh-CN')
}

function formatAmount(v: any): string {
  if (v === null || v === undefined || v === '') return '¥ -'
  const num = Number(v)
  if (Number.isNaN(num)) return '¥ ' + v
  const hasDecimal = String(v).indexOf('.') !== -1
  const body = hasDecimal
    ? num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    : num.toLocaleString('zh-CN') + '.00'
  return '¥ ' + body
}

function resolveTagType(value: any, col: Column): '' | 'success' | 'info' | 'warning' | 'danger' | 'primary' {
  if (!col.tagMap) return ''
  return (col.tagMap[String(value)] || '') as any
}

// 单元格渲染器(动态 component)
async function copyToClipboard(text: string) {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      // fallback:临时 textarea
      const ta = document.createElement('textarea')
      ta.value = text
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
    }
  } catch (e) {
    /* ignore */
  }
}

function makeRenderer(type: Column['type']) {
  return defineComponent({
    name: 'OpCellRenderer_' + (type || 'text'),
    props: ['value', 'row', 'column'] as any,
    setup(p: any) {
      return () => {
        const raw = p.value
        const col = p.column as Column
        const t = type || col.type || 'text'
        // tag 类型:formatter 转换展示文本,但 tag type 仍按 raw 值映射
        if (t === 'tag') {
          const display = col.formatter ? col.formatter(raw, p.row) : raw ?? '-'
          return h(ElTag, { type: resolveTagType(raw, col) || 'info', size: 'small' }, () => display)
        }
        // 非 tag:formatter 接管展示
        if (col.formatter) return h('span', col.formatter(raw, p.row))
        if (t === 'date') return h('span', formatDate(raw))
        if (t === 'number') return h('span', formatNumber(raw))
        if (t === 'amount') return h('span', formatAmount(raw))

        // 复制按钮(可选,仅 text 默认)
        if (col.copyable && raw != null && raw !== '') {
          const textNode = h('span', { class: 'opc-rtbl__value' }, String(raw))
          const btn = h(
            'button',
            {
              type: 'button',
              class: 'opc-rtbl__copy-btn',
              title: '复制',
              'aria-label': '复制',
              onClick: async (ev: MouseEvent) => {
                ev.stopPropagation()
                const c = col.copyValue ? col.copyValue(raw, p.row) : String(raw)
                await copyToClipboard(c)
              },
            },
            ['⎘'],
          )
          return h('span', { class: 'opc-rtbl__copyable' }, [textNode, btn])
        }
        return h('span', raw ?? '-')
      }
    },
  })
}

const textRenderer = makeRenderer('text')
const tagRenderer = makeRenderer('tag')
const dateRenderer = makeRenderer('date')
const numberRenderer = makeRenderer('number')
const amountRenderer = makeRenderer('amount')

function resolveRenderer(col: Column) {
  switch (col.type) {
    case 'tag':
      return tagRenderer
    case 'date':
      return dateRenderer
    case 'number':
      return numberRenderer
    case 'amount':
      return amountRenderer
    default:
      return textRenderer
  }
}
</script>

<style lang="scss" scoped>
.opc-responsive-table {
  width: 100%;
}

/* 移动端卡片视图 */
.opc-responsive-table__cards {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.opc-responsive-table__card {
  border-radius: 8px;
}

.opc-responsive-table__card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
  margin-bottom: 12px;
  word-break: break-all;
}

.opc-responsive-table__card-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px;
}

.opc-responsive-table__card-row {
  display: grid;
  grid-template-columns: 96px 1fr;
  gap: 8px;
  align-items: start;
  font-size: 14px;
  line-height: 1.45;

  &.align-right .opc-responsive-table__card-value {
    text-align: right;
    font-variant-numeric: tabular-nums;
  }
}

.opc-responsive-table__card-label {
  color: var(--el-text-color-secondary, #909399);
  flex-shrink: 0;
}

.opc-responsive-table__card-value {
  color: var(--el-text-color-primary, #303133);
  word-break: break-all;
}

.opc-responsive-table__card-actions {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--el-border-color-lighter, #ebeef5);
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

/* 触摸目标 ≥ 44 × 44 (iOS HIG / AC 5.3) */
.opc-responsive-table__card-actions :deep(.el-button) {
  min-height: 44px;
  min-width: 44px;
  flex: 1 1 auto;
}

/* copyable 单元格内嵌按钮 */
.opc-rtbl__copyable {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.opc-rtbl__copyable .opc-rtbl__value {
  font-family: 'Courier New', monospace;
  letter-spacing: 0.5px;
}
.opc-rtbl__copy-btn {
  background: transparent;
  border: 1px solid transparent;
  border-radius: 4px;
  padding: 2px 6px;
  font-size: 14px;
  color: var(--el-color-primary, #409eff);
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
  transition: background 0.2s, border-color 0.2s;
}
.opc-rtbl__copy-btn:hover {
  background: var(--el-color-primary-light-9, #ecf5ff);
  border-color: var(--el-color-primary-light-5, #d9ecff);
}
</style>
