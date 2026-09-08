// Shared Element Plus stub components for INSIGHT view tests.
// Mirrors ResponsiveTable.spec.ts W5.2 pattern: each el-* component is a
// noop/stub that exposes slots via defineComponent + h() so Vue's template
// compiler can resolve <el-table-column>, <el-tag>, etc. without warnings.
//
// Each stub accepts its commonly-used props (data-attribute or rendered)
// so test assertions can find them. New stubs can be added when a new
// el-* component appears in an INSIGHT page.

import { defineComponent, h } from 'vue'

export const noopRender = (_p: any, { slots }: any) =>
  h('div', { 'data-stub': 'noop' }, slots.default?.())

export const stubComponents: Record<string, any> = {
  ElRow: defineComponent({
    name: 'ElRow',
    props: ['gutter'],
    setup(_p: any, { slots, attrs }: any) {
      return () => h('div', { ...attrs, 'data-stub': 'ElRow', class: attrs.class }, slots.default?.())
    },
  }),
  ElCol: defineComponent({
    name: 'ElCol',
    props: ['span', 'xs', 'sm', 'md', 'lg'],
    setup(_p: any, { slots, attrs }: any) {
      return () => h('div', { ...attrs, 'data-stub': 'ElCol' }, slots.default?.())
    },
  }),
  ElCard: defineComponent({
    name: 'ElCard',
    props: ['shadow'],
    setup(_p: any, { slots, attrs }: any) {
      return () => h('div', { ...attrs, 'data-stub': 'ElCard' }, slots.default?.())
    },
  }),
  ElButton: defineComponent({
    name: 'ElButton',
    props: ['type', 'size', 'loading'],
    setup(_p: any, { slots, attrs }: any) {
      return () => h('button', { ...attrs, 'data-stub': 'ElButton' }, slots.default?.())
    },
  }),
  ElIcon: defineComponent({ name: 'ElIcon', setup: noopRender }),
  ElTag: defineComponent({
    name: 'ElTag',
    props: ['type', 'size', 'effect'],
    setup(p: any, { slots }: any) {
      return () => h('span', { 'data-stub': 'ElTag', 'data-type': p.type ?? '', class: 'el-tag-stub' }, slots.default?.())
    },
  }),
  ElEmpty: defineComponent({
    name: 'ElEmpty',
    props: ['description'],
    setup(p: any, { slots }: any) {
      return () => h('div', { 'data-stub': 'ElEmpty' }, slots.default?.() ?? p.description)
    },
  }),
  ElSkeleton: defineComponent({
    name: 'ElSkeleton',
    props: ['rows'],
    setup(_p: any, { slots }: any) {
      return () => h('div', { 'data-stub': 'ElSkeleton' }, slots.default?.())
    },
  }),
  ElSkeletonItem: defineComponent({
    name: 'ElSkeletonItem',
    props: ['variant'],
    setup(_p: any) {
      return () => h('div', { 'data-stub': 'ElSkeletonItem' })
    },
  }),
  ElTable: defineComponent({
    name: 'ElTable',
    props: ['data', 'emptyText', 'stripe'],
    setup(p: any, { slots, attrs }: any) {
      return () => {
        const data = p.data || []
        const children: any[] = []
        if (data.length && slots.default) {
          data.forEach((row: any, idx: number) => {
            const scope = { row, $index: idx }
            children.push(h('div', { 'data-stub': 'ElTableRow', 'data-row-id': row.id ?? idx, 'data-idx': idx }, slots.default!(scope)))
          })
        } else if (slots.empty) {
          // Desktop empty path: invoke the named `empty` slot so the
          // parent (ResponsiveTable) renders <el-empty :description=...>.
          children.push(h('div', { 'data-stub': 'ElTableEmpty' }, slots.empty()))
        } else if (slots.default) {
          children.push(slots.default())
        }
        return h('div', { ...attrs, 'data-stub': 'ElTable', 'data-empty-text': p.emptyText ?? '' }, children)
      }
    },
  }),
  ElTableColumn: defineComponent({
    name: 'ElTableColumn',
    props: ['prop', 'label', 'width', 'minWidth', 'align', 'fixed'],
    setup(_p, { slots }) {
      // Provide an inject handle so ElTable stub can push per-row scope into us.
      // Tests can override via inject('__rt_insight_scope') on wrapper if needed.
      return () => {
        // Default empty scope — replaced by ElTable's row context when nested.
        const scope = { row: {}, column: {}, $index: 0 }
        const out: any[] = []
        if (slots.default) out.push(slots.default(scope))
        if (slots.header) out.push(slots.header(scope))
        return h('div', { 'data-stub': 'ElTableColumn', 'data-label': _p.label ?? '' }, out)
      }
    },
  }),
  ElSelect: defineComponent({
    name: 'ElSelect',
    props: ['modelValue', 'placeholder', 'clearable'],
    emits: ['change', 'update:modelValue'],
    setup(p: any, { slots, attrs, emit }: any) {
      return () => h('div', { ...attrs, 'data-stub': 'ElSelect', 'data-placeholder': p.placeholder ?? '' }, slots.default?.())
    },
  }),
  ElOption: defineComponent({
    name: 'ElOption',
    props: ['label', 'value'],
    setup(p: any, { slots }: any) {
      return () => h('div', { 'data-stub': 'ElOption', 'data-value': p.value ?? '', 'data-label': p.label ?? '' }, slots.default?.() ?? p.label ?? '')
    },
  }),
  ElDialog: defineComponent({
    name: 'ElDialog',
    props: ['modelValue', 'title', 'width'],
    emits: ['update:modelValue', 'close'],
    setup(p: any, { slots }: any) {
      return () => h('div', { 'data-stub': 'ElDialog', 'data-title': p.title ?? '' }, slots.default?.())
    },
  }),
  ElDescriptions: defineComponent({
    name: 'ElDescriptions',
    props: ['column', 'border'],
    setup(_p: any, { slots }: any) {
      return () => h('div', { 'data-stub': 'ElDescriptions' }, slots.default?.())
    },
  }),
  ElDescriptionsItem: defineComponent({
    name: 'ElDescriptionsItem',
    props: ['label'],
    setup(p: any, { slots }: any) {
      return () => h('div', { 'data-stub': 'ElDescriptionsItem', 'data-label': p.label ?? '' }, slots.default?.())
    },
  }),
  ElDatePicker: defineComponent({
    name: 'ElDatePicker',
    props: ['modelValue', 'type', 'rangeSeparator', 'startPlaceholder', 'endPlaceholder', 'valueFormat'],
    setup(p: any, { attrs }: any) {
      return () => h('div', { ...attrs, 'data-stub': 'ElDatePicker', 'data-type': p.type ?? '' })
    },
  }),
}

/** Convenience mount option to use in test files. */
export const stubMountOpts = { global: { components: stubComponents } }
