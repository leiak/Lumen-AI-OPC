import { defineComponent, h } from 'vue'
import { describe, expect, it } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import MobileDrawer from '@/views/opc/components/MobileDrawer.vue'

// Element-plus stubs. We use minimal pass-throughs so slot content renders.
const stubComponents: Record<string, any> = {
  ElDrawer: defineComponent({
    name: 'ElDrawer',
    props: ['modelValue', 'direction', 'size', 'modal', 'closeOnClickModal', 'appendToBody'],
    emits: ['update:modelValue', 'closed'],
    setup(props, { slots, attrs, emit }) {
      return () => {
        // Forward to body's actual slot content + a close button (real
        // element-plus drawer does NOT render its own close button).
        return h(
          'div',
          {
            ...attrs,
            'data-stub': 'ElDrawer',
            'data-direction': props.direction,
            'data-size': props.size,
            'data-modal': String(props.modal),
          },
          slots.default?.(),
        )
      }
    },
  }),
  ElIcon: defineComponent({
    name: 'ElIcon',
    setup(_p, { slots }) {
      return () => h('span', { 'data-stub': 'ElIcon' }, slots.default?.())
    },
  }),
}

const mountOpts = { global: { components: stubComponents } }

describe('MobileDrawer', () => {
  it('renders default body slot content', async () => {
    const wrapper = mount(MobileDrawer, {
      ...mountOpts,
      props: { visible: true, title: 'Test' },
      slots: { default: '<p class="body">Hello</p>' },
    })
    await flushPromises()
    expect(wrapper.find('.body').exists()).toBe(true)
    expect(wrapper.text()).toContain('Hello')
  })

  it('shows title prop when no header slot', async () => {
    const wrapper = mount(MobileDrawer, {
      ...mountOpts,
      props: { visible: true, title: '筛选' },
    })
    await flushPromises()
    expect(wrapper.find('.opc-mobile-drawer__title').text()).toBe('筛选')
  })

  it('header slot overrides title prop', async () => {
    const wrapper = mount(MobileDrawer, {
      ...mountOpts,
      props: { visible: true, title: 'Should Not Show' },
      slots: { header: '<h2 class="custom-header">Custom Header</h2>' },
    })
    await flushPromises()
    expect(wrapper.find('.custom-header').exists()).toBe(true)
    expect(wrapper.find('.opc-mobile-drawer__title').exists()).toBe(false)
  })

  it('emits update:visible [false] when close button is clicked', async () => {
    const wrapper = mount(MobileDrawer, {
      ...mountOpts,
      props: { visible: true, title: 'X' },
    })
    await flushPromises()
    await wrapper.find('.opc-mobile-drawer__close').trigger('click')
    expect(wrapper.emitted('update:visible')).toBeTruthy()
    expect(wrapper.emitted('update:visible')![0]).toEqual([false])
  })

  it('close button has aria-label="关闭" for a11y', async () => {
    const wrapper = mount(MobileDrawer, {
      ...mountOpts,
      props: { visible: true, title: 'X' },
    })
    await flushPromises()
    expect(wrapper.find('.opc-mobile-drawer__close').attributes('aria-label')).toBe('关闭')
  })

  it('does not render header when both title and #header are absent', async () => {
    const wrapper = mount(MobileDrawer, {
      ...mountOpts,
      props: { visible: true, title: '' },
    })
    await flushPromises()
    expect(wrapper.find('.opc-mobile-drawer__header').exists()).toBe(false)
  })

  it('does not render footer when #footer slot is absent', async () => {
    const wrapper = mount(MobileDrawer, {
      ...mountOpts,
      props: { visible: true, title: 'X' },
    })
    await flushPromises()
    expect(wrapper.find('.opc-mobile-drawer__footer').exists()).toBe(false)
  })

  it('renders footer when #footer slot is provided', async () => {
    const wrapper = mount(MobileDrawer, {
      ...mountOpts,
      props: { visible: true, title: 'X' },
      slots: { footer: '<button class="apply">Apply</button>' },
    })
    await flushPromises()
    expect(wrapper.find('.opc-mobile-drawer__footer').exists()).toBe(true)
    expect(wrapper.find('.apply').exists()).toBe(true)
  })

  it('passes direction prop through to el-drawer', async () => {
    const wrapper = mount(MobileDrawer, {
      ...mountOpts,
      props: { visible: true, direction: 'btt' },
    })
    await flushPromises()
    expect(wrapper.find('[data-stub="ElDrawer"]').attributes('data-direction')).toBe('btt')
  })

  it('passes size prop through to el-drawer (defaults to 80%)', async () => {
    const wrapper = mount(MobileDrawer, {
      ...mountOpts,
      props: { visible: true },
    })
    await flushPromises()
    expect(wrapper.find('[data-stub="ElDrawer"]').attributes('data-size')).toBe('80%')
  })

  it('custom size passes through', async () => {
    const wrapper = mount(MobileDrawer, {
      ...mountOpts,
      props: { visible: true, size: 400 },
    })
    await flushPromises()
    expect(wrapper.find('[data-stub="ElDrawer"]').attributes('data-size')).toBe('400')
  })
})
