import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, h } from 'vue'

// ─── stub 依赖 ────────────────────────────────────────────────────────────────

vi.mock('vue-router', async () => {
  const actual = await vi.importActual<typeof import('vue-router')>('vue-router')
  return { ...actual, useRoute: () => ({ params: { id: '8' } }) }
})

vi.mock('@/api/opc/agent', () => ({
  getInstance: vi.fn(),
  getAgentDetail: vi.fn(),
  listInstanceTasks: vi.fn(),
  listInstanceUsage: vi.fn(),
}))

vi.mock('@/api/opc/llm', () => ({ chat: vi.fn() }))

// vi.mock 的 factory 会被提升到文件顶部,所以这些 mock 函数必须用 hoisted 一起提升,
// 否则 factory 执行时它们还在 TDZ 里。
const { successMock, errorMock } = vi.hoisted(() => ({
  successMock: vi.fn(),
  errorMock: vi.fn(),
}))

vi.mock('element-plus', async () => {
  const actual = await vi.importActual<typeof import('element-plus')>('element-plus')
  return {
    ...actual,
    ElMessage: Object.assign(vi.fn(), {
      success: successMock, error: errorMock, warning: vi.fn(), info: vi.fn(),
    }),
  }
})

import { getInstance, getAgentDetail, listInstanceTasks, listInstanceUsage } from '@/api/opc/agent'
import { chat as llmChat } from '@/api/opc/llm'
import Instance from '@/views/opc/agent/instance.vue'

// ─── 通用透传 stub ───────────────────────────────────────────────────────────

const stubAll = (name: string, tag = 'div') =>
  defineComponent({
    name,
    inheritAttrs: false,
    emits: ['click'],
    setup(_p, { slots, attrs, emit }) {
      return () =>
        h(
          tag,
          {
            ...attrs,
            'data-stub': name,
            onClick: (e: Event) => emit('click', e),
          },
          // 具名插槽也要渲染。只给 slots.default 的话 el-card 的 #header
          // （实例昵称、状态 tag）压根不进 DOM,断言会误判成「数据没加载上」。
          Object.values(slots).map((fn) => fn())
        )
    },
  })

const STUBS = [
  'el-row', 'el-col', 'el-card', 'el-tag', 'el-tabs', 'el-tab-pane',
  'el-descriptions', 'el-descriptions-item',
].reduce<Record<string, any>>((acc, n) => ((acc[n] = stubAll(n)), acc), {})

STUBS['el-button'] = stubAll('el-button', 'button')

// el-input 必须双向绑定,否则没法往输入框里打字 —— 测「回滚后原文还在不在」就无从谈起。
STUBS['el-input'] = defineComponent({
  name: 'el-input',
  inheritAttrs: false,
  props: { modelValue: { type: String, default: '' } },
  emits: ['update:modelValue'],
  setup(props, { attrs, emit }) {
    return () =>
      h('textarea', {
        ...attrs,
        'data-stub': 'el-input',
        value: props.modelValue,
        onInput: (e: Event) => emit('update:modelValue', (e.target as HTMLTextAreaElement).value),
      })
  },
})

const mountOpts = {
  global: {
    stubs: {
      ...STUBS,
      ResponsiveTable: defineComponent({ name: 'ResponsiveTable', setup: () => () => h('div') }),
    },
    // instance.vue 根节点挂了 v-loading,不 stub 会报未知指令
    directives: { loading: {} },
  },
}

// ─── fixtures ───────────────────────────────────────────────────────────────

/** 真实数据:实例 8 → 定义 1「财务数字员工-小财」,它的 system_prompt 有 191 字符 */
const PERSONA = '你是「小财」，OPC 平台的财务数字员工，负责财务报表与税务问答。'

const instRow = (extra: Record<string, any> = {}) => ({
  code: 200,
  data: {
    id: 8,
    definitionId: 1,
    nickname: '财务数字员工-小财(试用版)',
    instanceCode: 'AI20261023532795',
    status: 'RUNNING',
    companyId: 2,
    hireType: 'TRIAL',
    tokenUsed: 0,
    tokenQuota: 100000,
    taskCount: 0,
    ...extra,
  },
})

const defRow = (extra: Record<string, any> = {}) => ({
  code: 200,
  data: { id: 1, name: '财务数字员工-小财', systemPrompt: PERSONA, primaryModel: 'deepseek-v3', ...extra },
})

const chatReply = (content = '在的，请问需要我做什么？') => ({ code: 200, data: { content } })

async function mountInstance(opts: { inst?: any; def?: any; defRejects?: boolean } = {}) {
  vi.mocked(getInstance).mockResolvedValue(opts.inst ?? instRow())
  vi.mocked(listInstanceTasks).mockResolvedValue({ code: 200, data: [] })
  vi.mocked(listInstanceUsage).mockResolvedValue({ code: 200, data: [] })
  vi.mocked(getAgentDetail).mockResolvedValue(opts.def ?? defRow())
  if (opts.defRejects) vi.mocked(getAgentDetail).mockRejectedValue(new Error('definition 500'))
  vi.mocked(llmChat).mockResolvedValue(chatReply())
  const wrapper = mount(Instance, mountOpts)
  await flushPromises()
  return wrapper
}

/** 发出请求里那条 system 消息 —— 它才是人设的载体,比任何 DOM 断言都准 */
const sentSystem = () => {
  const call = vi.mocked(llmChat).mock.calls[0]
  expect(call, 'llmChat 没被调用').toBeTruthy()
  return (call![0] as any[])[0]
}

const GENERIC = '你是 OPC 数字员工，请基于上下文回答。'

const sendButton = (w: any) =>
  w.findAll('button').find((b: any) => b.text().includes('发送'))

const userBubbles = (w: any) => w.findAll('.msg.user')

// ─── 缺陷本体:雇来的数字员工没有它自己的人设 ─────────────────────────────────

describe('agent/instance — 对话用实例自己的人设', () => {
  beforeEach(() => {
    successMock.mockClear()
    errorMock.mockClear()
  })

  it('1. 首条 system 消息取自 agent 定义的 systemPrompt，而不是那句写死的通用提示', async () => {
    const wrapper = await mountInstance()

    // 人设链路：实例详情给出 definitionId → 据此查定义 → 取 system_prompt
    expect(vi.mocked(getAgentDetail)).toHaveBeenCalledWith(1)

    await wrapper.find('textarea').setValue('你是谁')
    await sendButton(wrapper).trigger('click')
    await flushPromises()

    expect(sentSystem()).toEqual({ role: 'system', content: PERSONA })
    expect(sentSystem().content).not.toBe(GENERIC)
  })

  it('2. 定义查不到 → 退回通用提示，且不算「实例加载失败」', async () => {
    // 人设是增强项，取不到不该把整个页面拖挂 —— 实例、任务、用量照常加载
    const wrapper = await mountInstance({ defRejects: true })

    expect(errorMock).not.toHaveBeenCalledWith('实例加载失败')
    expect(wrapper.text()).toContain('财务数字员工-小财(试用版)')

    await wrapper.find('textarea').setValue('在吗')
    await sendButton(wrapper).trigger('click')
    await flushPromises()

    expect(sentSystem()).toEqual({ role: 'system', content: GENERIC })
  })

  it('3. 实例没有 definitionId → 压根不查定义，直接用通用提示', async () => {
    await mountInstance({ inst: instRow({ definitionId: undefined }) })

    expect(vi.mocked(getAgentDetail)).not.toHaveBeenCalled()
  })

  it('4. 发送失败 → 回滚那条气泡，原文还回输入框，并给出提示', async () => {
    const wrapper = await mountInstance()
    vi.mocked(llmChat).mockRejectedValue(new Error('boom'))

    await wrapper.find('textarea').setValue('帮我算下季度毛利')
    await sendButton(wrapper).trigger('click')
    await flushPromises()

    // 原来只有 try/finally：气泡孤零零留在屏幕上，对面没回音
    expect(userBubbles(wrapper)).toHaveLength(0)
    // 别让用户白打一遍字
    expect((wrapper.find('textarea').element as HTMLTextAreaElement).value).toBe('帮我算下季度毛利')
    expect(errorMock).toHaveBeenCalledWith('发送失败，请重试')
  })

  it('5. 发送进行中再点一次 → 不会发出第二条请求', async () => {
    const wrapper = await mountInstance()
    // 挂住不 resolve，模拟「上一次还在飞」
    vi.mocked(llmChat).mockReturnValue(new Promise(() => {}) as any)

    await wrapper.find('textarea').setValue('第一句')
    await sendButton(wrapper).trigger('click')

    // 重新填上内容 —— 这样输入框的空白守卫失效，只剩 sending 守卫能挡
    await wrapper.find('textarea').setValue('第二句')
    await sendButton(wrapper).trigger('click')

    expect(vi.mocked(llmChat)).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })
})