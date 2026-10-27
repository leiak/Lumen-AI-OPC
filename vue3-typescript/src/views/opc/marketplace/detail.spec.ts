import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, h } from 'vue'

// ─── stub 依赖 ────────────────────────────────────────────────────────────────

const routerPush = vi.fn()
vi.mock('vue-router', async () => {
  const actual = await vi.importActual<typeof import('vue-router')>('vue-router')
  return {
    ...actual,
    useRouter: () => ({ push: routerPush }),
    useRoute: () => ({ params: { id: '1' } }),
  }
})

vi.mock('@/store/modules/user', () => ({ default: () => ({ id: 101 }) }))

// vi.mock 的 factory 会被提升到文件顶部,所以这些 mock 函数必须用 hoisted 一起提升,
// 否则 factory 执行时它们还在 TDZ 里(detail.vue 一 import element-plus 就炸)。
const { confirmMock, successMock, errorMock } = vi.hoisted(() => ({
  confirmMock: vi.fn(),
  successMock: vi.fn(),
  errorMock: vi.fn(),
}))

vi.mock('element-plus', async () => {
  const actual = await vi.importActual<typeof import('element-plus')>('element-plus')
  return {
    ...actual,
    ElMessage: Object.assign(vi.fn(), { success: successMock, error: errorMock, warning: vi.fn(), info: vi.fn() }),
    ElMessageBox: Object.assign(vi.fn(), { confirm: confirmMock, alert: vi.fn() }),
  }
})

import {
  getPublication,
  installPublication,
  uninstallPublication,
  installStatus,
  listReviews,
  subscribePublication,
  unsubscribePublication,
  subscriptionStatus,
} from '@/api/opc/marketplace'

vi.mock('@/api/opc/marketplace', () => ({
  getPublication: vi.fn(),
  installPublication: vi.fn(),
  uninstallPublication: vi.fn(),
  installStatus: vi.fn(),
  listReviews: vi.fn(),
  createReview: vi.fn(),
  updateReview: vi.fn(),
  deleteReview: vi.fn(),
  subscribePublication: vi.fn(),
  unsubscribePublication: vi.fn(),
  subscriptionStatus: vi.fn(),
}))

import Detail from '@/views/opc/marketplace/detail.vue'

// ─── 通用透传 stub ───────────────────────────────────────────────────────────
// detail.vue 用到 14 个 el-* 组件，逐个写 stub 太啰嗦；这里一个工厂全包了。
// el-button 必须渲染成真 <button>：测试是按按钮文案 find 再 trigger click 的，
// 渲染成 <div> 的话 findAll('button') 一个都找不到。
const stubAll = (name: string, tag = 'div') =>
  defineComponent({
    name,
    inheritAttrs: false,
    // 必须显式声明 emits:['click'] —— 否则模板里的 @click 既落进 attrs 又被
    // emit('click') 触发一次,一次真实点击会让 handler 跑两遍(装完弹两次框)。
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
          slots.default?.()
        )
    },
  })

const STUBS = [
  'el-card', 'el-icon', 'el-tag', 'el-avatar', 'el-image',
  'el-tabs', 'el-tab-pane', 'el-descriptions', 'el-descriptions-item',
  'el-dialog', 'el-empty', 'el-form', 'el-form-item', 'el-input',
  'el-pagination', 'el-rate', 'v-loading',
  'StarFilled', 'Star', 'Download', 'CircleCheck', 'EditPen',
].reduce<Record<string, any>>((acc, n) => ((acc[n] = stubAll(n)), acc), {})

STUBS['el-button'] = stubAll('el-button', 'button')

const mountOpts = { global: { stubs: STUBS } }

// ─── fixtures ───────────────────────────────────────────────────────────────

const AGENT_PUB = { id: 1, name: '财务数字员工-小财', productType: 'AGENT', code: 'AGENT-1', avgRating: 4.5 }
const MODULE_PUB = { id: 1, name: '电商运营助手', productType: 'MODULE', code: 'MOD-1', avgRating: 4.5 }

const installRow = (extra: Record<string, any> = {}) => ({
  id: 9, publicationId: 1, userId: 101, status: 'ACTIVE', ...extra,
})

/** 「查看实例」按钮 —— 按文案找,这是它唯一的可见契约 */
const instanceLink = (wrapper: any) =>
  wrapper.findAll('button').find((b: any) => b.text().includes('查看实例'))

/** 点「安装」按钮 —— 按 el-icon + 文案找 */
const installButton = (wrapper: any) =>
  wrapper.findAll('button').find((b: any) => b.text().includes('安装') && !b.text().includes('已安装'))

const installedButton = (wrapper: any) =>
  wrapper.findAll('button').find((b: any) => b.text().includes('已安装'))

/** 「查看模块」按钮 —— MODULE 商品指向模块社区的那条指路牌 */
const communityLink = (wrapper: any) =>
  wrapper.findAll('button').find((b: any) => b.text().includes('查看模块'))

/** 「订阅」/「已订阅」按钮 —— 按文案找,注意「已订阅」里也含「订阅」,所以要排除 */
const subscribeButton = (wrapper: any) =>
  wrapper.findAll('button').find((b: any) => b.text().includes('订阅') && !b.text().includes('已订阅'))

const unsubscribeButton = (wrapper: any) =>
  wrapper.findAll('button').find((b: any) => b.text().includes('已订阅'))

async function mountDetail(opts: {
  pub?: any
  status?: any
  installResult?: any
  subscription?: any
} = {}) {
  vi.mocked(getPublication).mockResolvedValue({ code: 200, data: opts.pub ?? AGENT_PUB } as any)
  vi.mocked(listReviews).mockResolvedValue({ code: 200, rows: [], total: 0 } as any)
  vi.mocked(installStatus).mockResolvedValue({ code: 200, data: opts.status } as any)
  vi.mocked(installPublication).mockResolvedValue({ code: 200, data: opts.installResult } as any)
  vi.mocked(uninstallPublication).mockResolvedValue({ code: 200 } as any)
  vi.mocked(subscriptionStatus).mockResolvedValue({ code: 200, data: opts.subscription } as any)
  vi.mocked(subscribePublication).mockResolvedValue({ code: 200, data: {} } as any)
  vi.mocked(unsubscribePublication).mockResolvedValue({ code: 200 } as any)
  const wrapper = mount(Detail, mountOpts)
  await flushPromises()
  return wrapper
}

// ─── 缺陷本体:装完不给指路牌 ─────────────────────────────────────────────────

describe('marketplace/detail — 安装后指向实例', () => {
  beforeEach(() => {
    routerPush.mockClear()
    confirmMock.mockReset()
    successMock.mockClear()
    errorMock.mockClear()
  })

  it('1. AGENT 装完 → 弹确认框 → 点「去对话」跳到 /opc/agent/instance/{id}', async () => {
    confirmMock.mockResolvedValue('confirm')
    const wrapper = await mountDetail({ installResult: installRow({ agentInstanceId: 8 }) })

    await installButton(wrapper).trigger('click')
    await flushPromises()

    expect(confirmMock).toHaveBeenCalledTimes(1)
    expect(confirmMock.mock.calls[0][0]).toContain('实例已创建')
    expect(confirmMock.mock.calls[0][1]).toBe('安装成功')
    expect(routerPush).toHaveBeenCalledWith('/opc/agent/instance/8')
  })

  it('2. 确认框点「稍后」→ 不跳转、不报错，「查看实例」按钮仍在', async () => {
    confirmMock.mockRejectedValue('cancel')
    const wrapper = await mountDetail({ installResult: installRow({ agentInstanceId: 8 }) })

    await installButton(wrapper).trigger('click')
    await flushPromises()

    // 取消不是失败 —— 绝不能因此弹「安装失败」
    expect(routerPush).not.toHaveBeenCalled()
    expect(errorMock).not.toHaveBeenCalled()
    expect(instanceLink(wrapper)).toBeTruthy()
  })

  it('3. MODULE 装完 → 弹框指向模块社区（MODULE 不建实例,但它在社区有一张卡片）', async () => {
    // W80 P2 之前:MODULE 装完只弹一句「安装成功」,用户不知道去哪儿 ——
    // 而 MODULE 根本没有「查看实例」那个按钮(不建 agent 实例),等于装完就断头路。
    // 现在给一条指路牌:去模块社区。
    confirmMock.mockResolvedValue('confirm')
    const wrapper = await mountDetail({ pub: MODULE_PUB, installResult: installRow({}) })

    await installButton(wrapper).trigger('click')
    await flushPromises()

    expect(confirmMock).toHaveBeenCalledTimes(1)
    expect(confirmMock.mock.calls[0][0]).toContain('模块已安装')
    expect(routerPush).toHaveBeenCalledWith('/opc/community/market')
    // MODULE 依然不该出现「查看实例」—— 它没有 agent 实例
    expect(instanceLink(wrapper)).toBeFalsy()
  })

  it('4. AGENT 但 agentInstanceId 缺失 → 不弹框也不给按钮（远端建实例失败过的老记录）', async () => {
    // 这个 case 值得单独钉：安装记录写成了，但 agentInstanceId 没有 —— 远端建实例
    // 失败过、或数据是迁移前留下的。若只判 installed 就去弹框，会把人送到
    // /opc/agent/instance/undefined。
    // 前提是「尚未安装」(status 不给),否则页面画的是「已安装」而不是「安装」。
    const wrapper = await mountDetail({ status: undefined, installResult: installRow({}) })

    expect(instanceLink(wrapper)).toBeFalsy()

    await installButton(wrapper).trigger('click')
    await flushPromises()

    expect(confirmMock).not.toHaveBeenCalled()
    expect(routerPush).not.toHaveBeenCalled()
  })

  it('5. 刷新后（已安装状态从 install/status 回来）→「查看实例」按钮直接可点', async () => {
    const wrapper = await mountDetail({ status: installRow({ agentInstanceId: 8 }) })

    expect(installedButton(wrapper)).toBeTruthy()
    const link = instanceLink(wrapper)
    expect(link).toBeTruthy()

    await link.trigger('click')
    expect(routerPush).toHaveBeenCalledWith('/opc/agent/instance/8')
  })

  it('6. 卸载后「查看实例」按钮消失', async () => {
    const wrapper = await mountDetail({ status: installRow({ agentInstanceId: 8 }) })
    expect(instanceLink(wrapper)).toBeTruthy()

    await installedButton(wrapper).trigger('click')
    await flushPromises()

    expect(instanceLink(wrapper)).toBeFalsy()
  })

  it('7. MODULE 商品有「查看模块」按钮 → 跳到模块社区', async () => {
    const wrapper = await mountDetail({ pub: MODULE_PUB })

    const link = communityLink(wrapper)
    expect(link).toBeTruthy()

    await link.trigger('click')
    expect(routerPush).toHaveBeenCalledWith('/opc/community/market')
  })

  it('8. AGENT 商品没有「查看模块」按钮（它去的是智能体中心,不是模块社区）', async () => {
    const wrapper = await mountDetail({ pub: AGENT_PUB })

    expect(communityLink(wrapper)).toBeFalsy()
  })
})

// ─── W80 P2:订阅 ──────────────────────────────────────────────────────────────

describe('marketplace/detail — 订阅', () => {
  beforeEach(() => {
    routerPush.mockClear()
    confirmMock.mockReset()
    successMock.mockClear()
    errorMock.mockClear()
  })

  it('1. 未订阅 → 显示「订阅」按钮,点它调 subscribePublication', async () => {
    const wrapper = await mountDetail({ subscription: undefined })

    const btn = subscribeButton(wrapper)
    expect(btn).toBeTruthy()
    expect(unsubscribeButton(wrapper)).toBeFalsy()

    await btn.trigger('click')
    await flushPromises()

    expect(subscribePublication).toHaveBeenCalledWith(1)
    expect(successMock).toHaveBeenCalledWith('已订阅')
    // 订阅成功后按钮翻面
    expect(unsubscribeButton(wrapper)).toBeTruthy()
  })

  it('2. 已订阅(ACTIVE)→ 显示「已订阅」按钮,点它调 unsubscribePublication', async () => {
    const wrapper = await mountDetail({
      subscription: { id: 5, publicationId: 1, userId: 101, status: 'ACTIVE' },
    })

    expect(subscribeButton(wrapper)).toBeFalsy()
    const btn = unsubscribeButton(wrapper)
    expect(btn).toBeTruthy()

    await btn.trigger('click')
    await flushPromises()

    expect(unsubscribePublication).toHaveBeenCalledWith(1)
    expect(successMock).toHaveBeenCalledWith('已退订')
  })

  it('3. 已退订(CANCELLED)的记录不算已订阅 → 仍然显示「订阅」', async () => {
    // 后端 mp_subscription 的 status 取值只有 ACTIVE / CANCELLED。
    // CANCELLED 行还占着唯一键(uk_sub 不含 deleted),但它不是「已订阅」。
    const wrapper = await mountDetail({
      subscription: { id: 5, publicationId: 1, userId: 101, status: 'CANCELLED' },
    })

    expect(subscribeButton(wrapper)).toBeTruthy()
    expect(unsubscribeButton(wrapper)).toBeFalsy()
  })

  it('4. 订阅失败 → 提示失败,不把按钮翻成「已订阅」', async () => {
    const wrapper = await mountDetail({ subscription: undefined })
    // 必须在 mount 之后设 —— mountDetail 内部会统一把 mock 重置成 resolved
    vi.mocked(subscribePublication).mockRejectedValue(new Error('未登录'))

    await subscribeButton(wrapper).trigger('click')
    await flushPromises()

    expect(errorMock).toHaveBeenCalledWith('订阅失败')
    expect(unsubscribeButton(wrapper)).toBeFalsy()
  })
})