// useNotificationSocket —— 站内信 WS 订阅。
// 覆盖：挂载即拉一次收件箱、收到 NOTIFICATION 推送再拉一次、断线 3s 重连、
// 卸载时断开并取消重连、已读接口失败要往上抛。
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent } from 'vue'

vi.mock('@/utils/auth', () => ({
  getToken: vi.fn(() => 'tok-abc'),
  setToken: vi.fn(),
  removeToken: vi.fn(),
}))
vi.mock('@/api/opc/notification', () => ({
  listInbox: vi.fn(),
  markRead: vi.fn(),
  markAllRead: vi.fn(),
}))

import { listInbox, markRead as apiMarkRead, markAllRead as apiMarkAllRead } from '@/api/opc/notification'
import { useNotificationSocket } from '@/composables/useNotificationSocket'

const listInboxMock = listInbox as unknown as ReturnType<typeof vi.fn>
const markReadMock = apiMarkRead as unknown as ReturnType<typeof vi.fn>
const markAllReadMock = apiMarkAllRead as unknown as ReturnType<typeof vi.fn>

/** 假 WebSocket：替身不真连网，测试自己驱动 onopen / onmessage / onclose。 */
class FakeWebSocket {
  static instances: FakeWebSocket[] = []
  url: string
  onopen: ((e?: any) => void) | null = null
  onclose: ((e?: any) => void) | null = null
  onmessage: ((e?: any) => void) | null = null
  onerror: ((e?: any) => void) | null = null
  closed = false

  constructor(url: string) {
    this.url = url
    FakeWebSocket.instances.push(this)
  }

  close() {
    this.closed = true
  }

  emitOpen() {
    this.onopen?.()
  }

  emitMessage(data: any) {
    this.onmessage?.({ data })
  }

  emitClose() {
    this.onclose?.()
  }
}

const Host = defineComponent({
  setup() {
    return useNotificationSocket()
  },
  template: '<div />',
})

const inboxPayload = {
  code: 200,
  data: {
    items: [{ id: 1, title: '有一条新消息' }],
    total: 1,
    unreadCount: 3,
    page: 1,
    pageSize: 50,
  },
}

let errorSpy: ReturnType<typeof vi.spyOn>

beforeEach(() => {
  vi.clearAllMocks()
  FakeWebSocket.instances = []
  vi.stubGlobal('WebSocket', FakeWebSocket as any)
  listInboxMock.mockResolvedValue(inboxPayload)
  markReadMock.mockResolvedValue({ code: 200 })
  markAllReadMock.mockResolvedValue({ code: 200 })
  errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
  errorSpy.mockRestore()
})

describe('useNotificationSocket', () => {
  it('挂载时拉一次收件箱并填进 inbox/unread', async () => {
    const wrapper = mount(Host)
    await flushPromises()

    expect(listInboxMock).toHaveBeenCalledWith({ page: 1, pageSize: 50 })
    expect(wrapper.vm.inbox).toHaveLength(1)
    expect(wrapper.vm.unread).toBe(3)
    expect(wrapper.vm.loading).toBe(false)
    wrapper.unmount()
  })

  it('连接地址带上 token（不是裸连）', async () => {
    const wrapper = mount(Host)
    await flushPromises()

    expect(FakeWebSocket.instances).toHaveLength(1)
    expect(FakeWebSocket.instances[0].url).toMatch(/^wss?:\/\/[^/]+\/opc\/notification\/ws\?token=/)
    expect(FakeWebSocket.instances[0].url).toContain('token=tok-abc')
    wrapper.unmount()
  })

  it('onopen 之后 connected 变 true', async () => {
    const wrapper = mount(Host)
    await flushPromises()
    expect(wrapper.vm.connected).toBe(false)

    FakeWebSocket.instances[0].emitOpen()

    expect(wrapper.vm.connected).toBe(true)
    wrapper.unmount()
  })

  it('收到 NOTIFICATION 推送会再拉一次收件箱', async () => {
    const wrapper = mount(Host)
    await flushPromises()
    expect(listInboxMock).toHaveBeenCalledTimes(1)

    FakeWebSocket.instances[0].emitMessage(JSON.stringify({ type: 'NOTIFICATION' }))
    await flushPromises()

    expect(listInboxMock).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('别的消息类型不触发刷新', async () => {
    const wrapper = mount(Host)
    await flushPromises()

    FakeWebSocket.instances[0].emitMessage(JSON.stringify({ type: 'PING' }))
    await flushPromises()

    expect(listInboxMock).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('收到坏 JSON 不炸，也不刷新', async () => {
    const wrapper = mount(Host)
    await flushPromises()

    FakeWebSocket.instances[0].emitMessage('not-json')
    await flushPromises()

    expect(listInboxMock).toHaveBeenCalledTimes(1)
    expect(errorSpy).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('断线后 3 秒自动重连', async () => {
    vi.useFakeTimers()
    const wrapper = mount(Host)
    await flushPromises()

    FakeWebSocket.instances[0].emitClose()
    expect(FakeWebSocket.instances).toHaveLength(1)

    await vi.advanceTimersByTimeAsync(3000)

    expect(FakeWebSocket.instances).toHaveLength(2)
    wrapper.unmount()
  })

  it('卸载时断开连接并取消重连', async () => {
    vi.useFakeTimers()
    const wrapper = mount(Host)
    await flushPromises()

    const ws = FakeWebSocket.instances[0]
    wrapper.unmount()
    expect(ws.closed).toBe(true)

    // 断线后本来会排一个 3s 的重连 —— 卸载后不该再连
    ws.emitClose()
    await vi.advanceTimersByTimeAsync(3000)
    expect(FakeWebSocket.instances).toHaveLength(1)
  })

  it('markRead(id) 先调接口再刷新收件箱', async () => {
    const wrapper = mount(Host)
    await flushPromises()

    await wrapper.vm.markRead(7)

    expect(markReadMock).toHaveBeenCalledWith(7)
    expect(listInboxMock).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('markAllRead() 先调接口再刷新收件箱', async () => {
    const wrapper = mount(Host)
    await flushPromises()

    await wrapper.vm.markAllRead()

    expect(markAllReadMock).toHaveBeenCalled()
    expect(listInboxMock).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('已读接口失败要往上抛（不能静默吞掉）', async () => {
    const wrapper = mount(Host)
    await flushPromises()
    markReadMock.mockRejectedValueOnce(new Error('500'))

    await expect(wrapper.vm.markRead(7)).rejects.toThrow('500')
    expect(errorSpy).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('收件箱接口失败也不炸页面，loading 复位', async () => {
    listInboxMock.mockRejectedValueOnce(new Error('500'))
    const wrapper = mount(Host)
    await flushPromises()

    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.vm.inbox).toEqual([])
    expect(errorSpy).toHaveBeenCalled()
    wrapper.unmount()
  })
})
