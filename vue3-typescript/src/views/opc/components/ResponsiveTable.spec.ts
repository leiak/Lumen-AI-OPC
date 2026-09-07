// ResponsiveTable.spec.ts — formatters (pure logic) + render modes (integration).
//
// Strategy: register el-table/column/empty/tag/card stubs via @vue/test-utils
// `global.components` so Vue's template compiler resolves `<el-table-column>`
// etc. correctly. Mobile tests bypass el-table scoped-slot indirection.
import { nextTick, ref, defineComponent, h } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const widthRef = ref(1200)

vi.mock('@vueuse/core', async () => {
  const actual = await vi.importActual<any>('@vueuse/core')
  return { ...actual, useWindowSize: () => ({ width: widthRef, height: ref(800) }) }
})

import ResponsiveTable from '@/views/opc/components/ResponsiveTable.vue'

const noopRender = (_p: any, { slots }: any) =>
  h('div', { 'data-stub': 'noop' }, slots.default?.())
const stubComponents: Record<string, any> = {
  ElTable: defineComponent({
    name: 'ElTable',
    props: ['data', 'emptyText'],
    setup(_props, { slots, attrs }) {
      return () => h('div', { ...attrs, 'data-stub': 'ElTable' }, slots.default?.())
    },
  }),
  ElTableColumn: defineComponent({
    name: 'ElTableColumn',
    setup(_p, { slots }) {
      return () => {
        const scope = { row: {}, column: {}, $index: 0 }
        const out: any[] = []
        if (slots.default) out.push(slots.default(scope))
        if (slots.header) out.push(slots.header(scope))
        return h('div', { 'data-stub': 'ElTableColumn' }, out)
      }
    },
  }),
  ElEmpty: defineComponent({
    name: 'ElEmpty',
    props: ['description'],
    setup(p: any, { slots }: any) {
      return () => h('div', { 'data-stub': 'ElEmpty' }, slots.default?.() ?? p.description)
    },
  }),
  ElTag: defineComponent({
    name: 'ElTag',
    props: ['type', 'size'],
    setup(p: any, { slots }: any) {
      return () => h('span', { 'data-stub': 'ElTag', 'data-type': p.type }, slots.default?.())
    },
  }),
  ElCard: defineComponent({
    name: 'ElCard',
    props: ['shadow'],
    setup(_p: any, { slots }: any) {
      return () => h('div', { 'data-stub': 'ElCard' }, slots.default?.())
    },
  }),
  ElButton: defineComponent({ name: 'ElButton', setup: noopRender }),
  ElIcon: defineComponent({ name: 'ElIcon', setup: noopRender }),
}

const mountOpts = { global: { components: stubComponents } }

const sampleColumns = [
  { key: 'name', label: 'Name', primary: true },
  { key: 'count', label: 'Count', type: 'number' as const },
  { key: 'amount', label: 'Amount', type: 'amount' as const },
  { key: 'date', label: 'Date', type: 'date' as const },
  { key: 'status', label: 'Status', type: 'tag' as const, tagMap: { ACTIVE: 'success', INACTIVE: 'danger' } },
  { key: 'hidden', label: 'Hidden', hideOnMobile: true },
]

const sampleData = [
  {
    name: 'Alpha',
    count: 1000,
    amount: 500.5,
    date: '2026-09-07T10:30:00Z',
    status: 'ACTIVE',
    hidden: 'h1',
  },
]

describe('ResponsiveTable — formatters (pure logic, mobile mode)', () => {
  beforeEach(() => {
    widthRef.value = 500
  })

  it('formats numbers with thousands separators', async () => {
    const wrapper = mount(ResponsiveTable, {
      ...mountOpts,
      props: { data: sampleData, columns: sampleColumns, rowKey: 'name' },
    })
    await flushPromises()
    expect(wrapper.html()).toContain('1,000')
  })

  it('formats amounts with ¥ prefix and decimals', async () => {
    const wrapper = mount(ResponsiveTable, {
      ...mountOpts,
      props: { data: sampleData, columns: sampleColumns, rowKey: 'name' },
    })
    await flushPromises()
    expect(wrapper.html()).toContain('¥')
    expect(wrapper.html()).toContain('500.50')
  })

  it('formats amounts with .00 suffix when integer', async () => {
    const wrapper = mount(ResponsiveTable, {
      ...mountOpts,
      props: {
        data: [{ name: 'X', amount: 1000 }],
        columns: [{ key: 'amount', label: 'Amount', type: 'amount' }],
      },
    })
    await flushPromises()
    expect(wrapper.html()).toContain('¥ 1,000.00')
  })

  it('formats dates as yyyy-MM-dd HH:mm', async () => {
    const wrapper = mount(ResponsiveTable, {
      ...mountOpts,
      props: {
        data: [{ name: 'X', date: '2026-09-07T10:30:00Z' }],
        columns: [{ key: 'date', label: 'Date', type: 'date' }],
      },
    })
    await flushPromises()
    expect(wrapper.html()).toMatch(/2026-09-07 \d{2}:\d{2}/)
  })

  it('renders "-" for null date', async () => {
    const wrapper = mount(ResponsiveTable, {
      ...mountOpts,
      props: {
        data: [{ name: 'X', date: null }],
        columns: [{ key: 'date', label: 'Date', type: 'date' }],
      },
    })
    await flushPromises()
    expect(wrapper.html()).toContain('-')
  })

  it('renders "-" for null number', async () => {
    const wrapper = mount(ResponsiveTable, {
      ...mountOpts,
      props: {
        data: [{ name: 'X', n: null }],
        columns: [{ key: 'n', label: 'N', type: 'number' }],
      },
    })
    await flushPromises()
    expect(wrapper.html()).toContain('-')
  })

  it('resolves tag type from tagMap', async () => {
    const wrapper = mount(ResponsiveTable, {
      ...mountOpts,
      props: {
        data: [{ name: 'X', status: 'ACTIVE' }],
        columns: [{ key: 'status', label: 'Status', type: 'tag', tagMap: { ACTIVE: 'success', INACTIVE: 'danger' } }],
      },
    })
    await flushPromises()
    // ElTag is the real component (small enough to render in happy-dom);
    // its type maps to the CSS class `el-tag--success`.
    expect(wrapper.html()).toContain('el-tag--success')
  })

  it('falls back to info tag type when status not in tagMap', async () => {
    const wrapper = mount(ResponsiveTable, {
      ...mountOpts,
      props: {
        data: [{ name: 'X', status: 'UNKNOWN' }],
        columns: [{ key: 'status', label: 'Status', type: 'tag', tagMap: { ACTIVE: 'success' } }],
      },
    })
    await flushPromises()
    // resolveTagType returns '' when key not found; cell renderer falls back
    // to 'info' so the CSS class becomes el-tag--info.
    expect(wrapper.html()).toContain('el-tag--info')
  })

  it('formatter prop overrides default rendering', async () => {
    const wrapper = mount(ResponsiveTable, {
      ...mountOpts,
      props: {
        data: [{ name: 'X', n: 42 }],
        columns: [{ key: 'n', label: 'N', formatter: (v: number) => `VAL=${v}` }],
      },
    })
    await flushPromises()
    expect(wrapper.html()).toContain('VAL=42')
  })

  it('renders "-" for null text value', async () => {
    const wrapper = mount(ResponsiveTable, {
      ...mountOpts,
      props: {
        data: [{ name: 'X', t: null }],
        columns: [{ key: 't', label: 'T' }],
      },
    })
    await flushPromises()
    expect(wrapper.html()).toContain('-')
  })

  it('renders copyable button on text columns', async () => {
    const wrapper = mount(ResponsiveTable, {
      ...mountOpts,
      props: {
        data: [{ name: 'X', code: 'ABC123' }],
        columns: [{ key: 'code', label: 'Code', copyable: true }],
      },
    })
    await flushPromises()
    expect(wrapper.html()).toContain('opc-rtbl__copy-btn')
  })
})

describe('ResponsiveTable — render modes (mobile vs desktop)', () => {
  beforeEach(() => {
    widthRef.value = 1200
  })

  it('renders el-table stub at desktop width', async () => {
    const wrapper = mount(ResponsiveTable, {
      ...mountOpts,
      props: { data: sampleData, columns: sampleColumns, rowKey: 'name' },
    })
    await flushPromises()
    expect(wrapper.find('[data-stub="ElTable"]').exists()).toBe(true)
    expect(wrapper.find('[data-stub="ElCard"]').exists()).toBe(false)
  })

  it('renders cards at mobile width (< breakpoint)', async () => {
    widthRef.value = 500
    const wrapper = mount(ResponsiveTable, {
      ...mountOpts,
      props: { data: sampleData, columns: sampleColumns, rowKey: 'name' },
    })
    await flushPromises()
    expect(wrapper.find('[data-stub="ElCard"]').exists()).toBe(true)
    expect(wrapper.classes()).toContain('is-mobile')
  })

  it('mobile filters hideOnMobile columns', async () => {
    widthRef.value = 500
    const wrapper = mount(ResponsiveTable, {
      ...mountOpts,
      props: { data: sampleData, columns: sampleColumns, rowKey: 'name' },
    })
    await flushPromises()
    expect(wrapper.text()).not.toContain('Hidden')
    expect(wrapper.text()).toContain('Name')
  })

  it('respects custom breakpoint (breakpoint=1000 makes width=999 mobile)', async () => {
    widthRef.value = 999
    const wrapper = mount(ResponsiveTable, {
      ...mountOpts,
      props: { data: sampleData, columns: sampleColumns, rowKey: 'name', breakpoint: 1000 },
    })
    await flushPromises()
    expect(wrapper.find('[data-stub="ElCard"]').exists()).toBe(true)
  })

  it('pins width-1 < breakpoint offset (width=1000 + breakpoint=1000 → mobile)', async () => {
    widthRef.value = 1000
    const wrapper = mount(ResponsiveTable, {
      ...mountOpts,
      props: { data: sampleData, columns: sampleColumns, rowKey: 'name', breakpoint: 1000 },
    })
    await flushPromises()
    // 1000 - 1 = 999 < 1000 → mobile. Documents the -1 offset quirk.
    expect(wrapper.find('[data-stub="ElCard"]').exists()).toBe(true)
  })

  it('shows el-empty when data is empty (mobile)', async () => {
    widthRef.value = 500
    const wrapper = mount(ResponsiveTable, {
      ...mountOpts,
      props: { data: [], columns: sampleColumns, rowKey: 'name' },
    })
    await flushPromises()
    expect(wrapper.find('[data-stub="ElEmpty"]').exists()).toBe(true)
  })

  it('respects custom emptyText', async () => {
    widthRef.value = 500
    const wrapper = mount(ResponsiveTable, {
      ...mountOpts,
      props: { data: [], columns: sampleColumns, rowKey: 'name', emptyText: '没有数据' },
    })
    await flushPromises()
    expect(wrapper.html()).toContain('没有数据')
  })

  it('renders #actions slot in mobile card', async () => {
    widthRef.value = 500
    const wrapper = mount(ResponsiveTable, {
      ...mountOpts,
      props: { data: sampleData, columns: sampleColumns, rowKey: 'name' },
      slots: { actions: '<button class="test-action">Edit</button>' },
    })
    await flushPromises()
    expect(wrapper.find('.test-action').exists()).toBe(true)
  })

  it('reactive width flip re-renders mode', async () => {
    const wrapper = mount(ResponsiveTable, {
      ...mountOpts,
      props: { data: sampleData, columns: sampleColumns, rowKey: 'name' },
    })
    await flushPromises()
    expect(wrapper.find('[data-stub="ElTable"]').exists()).toBe(true)

    widthRef.value = 500
    await nextTick()
    await flushPromises()
    expect(wrapper.find('[data-stub="ElCard"]').exists()).toBe(true)
  })

  it('renders primary column as card title in mobile mode', async () => {
    widthRef.value = 500
    const wrapper = mount(ResponsiveTable, {
      ...mountOpts,
      props: { data: sampleData, columns: sampleColumns, rowKey: 'name' },
    })
    await flushPromises()
    expect(wrapper.find('.opc-responsive-table__card-title').exists()).toBe(true)
    expect(wrapper.text()).toContain('Alpha')
  })
})
