import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, h } from 'vue'

vi.mock('@/api/login', () => ({
  login: vi.fn(),
  logout: vi.fn(),
  getInfo: vi.fn(),
}))
vi.mock('@/api/opc/user', () => ({
  listMyCompanyMembers: vi.fn(),
  switchCompany: vi.fn(),
}))

const switchTo = vi.fn()
vi.mock('@/composables/useCompanySwitch', () => ({
  useCompanySwitch: () => ({ switchTo }),
}))

import useUserStore from '@/store/modules/user'
import { listMyCompanyMembers } from '@/api/opc/user'
import CompanySwitcher from '@/components/CompanySwitcher/index.vue'

const listMock = listMyCompanyMembers as unknown as ReturnType<typeof vi.fn>

/** element-plus 的轻量替身：只保留本组件用到的几个组件与插槽结构。 */
const stubs: Record<string, any> = {
  ElDropdown: defineComponent({
    name: 'ElDropdown',
    props: ['trigger'],
    setup(_p, { slots }) {
      // 真实 el-dropdown 的下拉面板是「点击才弹出」的 Teleport 内容，
      // 替身里直接渲染出来，测试才能看到菜单项。
      return () =>
        h('div', { 'data-stub': 'ElDropdown' }, [slots.default?.(), slots.dropdown?.()])
    },
  }),
  ElDropdownMenu: defineComponent({
    name: 'ElDropdownMenu',
    setup(_p, { slots }) {
      return () => h('div', { 'data-stub': 'ElDropdownMenu' }, slots.default?.())
    },
  }),
  ElDropdownItem: defineComponent({
    name: 'ElDropdownItem',
    props: ['command', 'disabled'],
    setup(p: any, { slots }) {
      return () =>
        h('div', { 'data-stub': 'ElDropdownItem', 'data-command': p.command, 'data-disabled': String(p.disabled) }, slots.default?.())
    },
  }),
  ElIcon: defineComponent({
    name: 'ElIcon',
    setup(_p, { slots }) {
      return () => h('span', { 'data-stub': 'ElIcon' }, slots.default?.())
    },
  }),
}

const mountOpts = { global: { components: stubs } }

const companies = [
  { id: 1, companyName: 'A 公司', role: 'OWNER', current: true },
  { id: 2, companyName: 'B 公司', role: 'STAFF', current: false },
]

beforeEach(() => {
  vi.clearAllMocks()
  listMock.mockResolvedValue({ code: 200, data: companies })
  switchTo.mockResolvedValue(true)
})

describe('CompanySwitcher', () => {
  it('lists the companies I belong to on mount', async () => {
    mount(CompanySwitcher, mountOpts)
    await flushPromises()
    expect(listMock).toHaveBeenCalledTimes(1)
  })

  it('shows the current company name', async () => {
    const store = useUserStore()
    store.companyId = 1
    const wrapper = mount(CompanySwitcher, mountOpts)
    await flushPromises()
    expect(wrapper.text()).toContain('A 公司')
  })

  it('renders one entry per company and marks the current one', async () => {
    const store = useUserStore()
    store.companyId = 1
    const wrapper = mount(CompanySwitcher, mountOpts)
    await flushPromises()
    const items = wrapper.findAll('[data-stub="ElDropdownItem"]')
    expect(items).toHaveLength(2)
    expect(items[0].attributes('data-disabled')).toBe('true')
    expect(items[1].attributes('data-disabled')).toBe('false')
  })

  it('switches to the picked company', async () => {
    const store = useUserStore()
    store.companyId = 1
    const wrapper = mount(CompanySwitcher, mountOpts)
    await flushPromises()
    wrapper.findComponent({ name: 'ElDropdown' }).vm.$emit('command', 2)
    await flushPromises()
    expect(switchTo).toHaveBeenCalledWith(2)
  })

  it('renders nothing when I belong to no company', async () => {
    listMock.mockResolvedValue({ code: 200, data: [] })
    const wrapper = mount(CompanySwitcher, mountOpts)
    await flushPromises()
    expect(wrapper.find('[data-stub="ElDropdown"]').exists()).toBe(false)
  })

  it('stays hidden when the list cannot be loaded (不把页面拖挂)', async () => {
    listMock.mockRejectedValue(new Error('500'))
    const wrapper = mount(CompanySwitcher, mountOpts)
    await flushPromises()
    expect(wrapper.find('[data-stub="ElDropdown"]').exists()).toBe(false)
  })
})
