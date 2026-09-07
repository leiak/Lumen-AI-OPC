import { describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

// Stub vue-router so we can drive router.push directly. We use createMemoryHistory
// (no document-level history) so happy-dom does not need a window navigation.
const routerPush = vi.fn()
vi.mock('vue-router', async () => {
  const actual = await vi.importActual<typeof import('vue-router')>('vue-router')
  return {
    ...actual,
    useRouter: () => ({ push: routerPush }),
  }
})

// Light element-plus stubs (el-card/avatar/tag/button).
import { defineComponent, h } from 'vue'
const stubComponents: Record<string, any> = {
  ElCard: defineComponent({
    name: 'ElCard',
    setup(_p, { slots, attrs }) {
      return () => h('div', { ...attrs, 'data-stub': 'ElCard' }, slots.default?.())
    },
  }),
  ElAvatar: defineComponent({
    name: 'ElAvatar',
    props: ['size', 'src'],
    setup(p: any, { slots, attrs }) {
      return () => h('div', { ...attrs, 'data-stub': 'ElAvatar' }, slots.default?.() ?? p.src ?? '')
    },
  }),
  ElTag: defineComponent({
    name: 'ElTag',
    props: ['type', 'size'],
    setup(p: any, { slots }) {
      return () => h('span', { 'data-stub': 'ElTag', 'data-type': p.type }, slots.default?.())
    },
  }),
  ElButton: defineComponent({
    name: 'ElButton',
    props: ['type', 'size'],
    setup(p: any, { slots, attrs, emit }) {
      return () => h('button', { ...attrs, 'data-stub': 'ElButton' }, slots.default?.())
    },
  }),
}

import AgentCard from '@/views/opc/components/AgentCard.vue'

const mountOpts = { global: { components: stubComponents } }

const baseAgent = {
  id: 1,
  name: 'Alpha',
  description: 'First agent',
  iconUrl: 'http://x/a.png',
  priceMonthly: 99,
  category: 'FINANCE',
}

describe('AgentCard — rendering', () => {
  it('renders name and description', async () => {
    const wrapper = mount(AgentCard, {
      ...mountOpts,
      props: { agent: baseAgent },
    })
    await flushPromises()
    expect(wrapper.text()).toContain('Alpha')
    expect(wrapper.text()).toContain('First agent')
  })

  it('shows price as ¥ X / 月', async () => {
    const wrapper = mount(AgentCard, {
      ...mountOpts,
      props: { agent: baseAgent },
    })
    await flushPromises()
    expect(wrapper.text()).toContain('¥ 99 / 月')
  })

  it.each([
    ['FINANCE', '财务', 'danger'],
    ['ERP', 'ERP', 'success'],
    ['CRM', 'CRM', 'warning'],
    ['HR', '人力', 'info'],
    ['ECOM', '电商', 'primary'],
    ['CONTENT', '内容', ''],
    ['VOICE', '语音', 'success'],
    ['INSIGHT', '数据', 'warning'],
  ])('maps category %s to label %s + color %s', async (cat, label, color) => {
    const wrapper = mount(AgentCard, {
      ...mountOpts,
      props: { agent: { ...baseAgent, category: cat } },
    })
    await flushPromises()
    expect(wrapper.text()).toContain(label)
    if (color) {
      expect(wrapper.html()).toContain(`data-type="${color}"`)
    }
  })

  it('falls back to raw category string for unknown values', async () => {
    const wrapper = mount(AgentCard, {
      ...mountOpts,
      props: { agent: { ...baseAgent, category: 'WEIRD' } },
    })
    await flushPromises()
    expect(wrapper.text()).toContain('WEIRD')
  })
})

describe('AgentCard — interactions', () => {
  it('clicking the card triggers router.push to detail', async () => {
    routerPush.mockClear()
    const wrapper = mount(AgentCard, {
      ...mountOpts,
      props: { agent: baseAgent },
    })
    await flushPromises()
    await wrapper.find('.agent-card').trigger('click')
    expect(routerPush).toHaveBeenCalledWith('/opc/agent/detail/1')
  })

  it('clicking 雇佣 triggers router.push to hire with stopPropagation', async () => {
    routerPush.mockClear()
    const wrapper = mount(AgentCard, {
      ...mountOpts,
      props: { agent: baseAgent },
    })
    await flushPromises()
    // Find the hire button by its text
    const buttons = wrapper.findAll('button')
    const hireBtn = buttons.find((b) => b.text() === '雇佣')
    expect(hireBtn).toBeTruthy()
    await hireBtn!.trigger('click')
    expect(routerPush).toHaveBeenCalledWith('/opc/agent/hire/1')
    // Verify it was called only ONCE — the .stop on the button should prevent
    // the card click handler from also firing.
    expect(routerPush).toHaveBeenCalledTimes(1)
  })
})
