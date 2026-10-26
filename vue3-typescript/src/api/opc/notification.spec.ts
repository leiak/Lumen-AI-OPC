// api/opc/notification —— 站内信的 api 薄包装。
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({ default: vi.fn() }))

import request from '@/utils/request'
import * as notification from '@/api/opc/notification'
import { listInbox, unreadCount, markRead, markAllRead } from '@/api/opc/notification'
import { expectApiContract, expectRequest } from '@/test/api-contract'

const requestMock = vi.mocked(request)

beforeEach(() => {
  requestMock.mockReset()
  requestMock.mockResolvedValue({ code: 200 } as any)
})

describe('api/opc/notification', () => {
  it('每个导出函数都遵守 request 契约（url 前缀 / 动词 / 不撞路由）', () => {
    expectApiContract(notification, '/opc/notification/')
  })

  it('listInbox({page,pageSize}) -> GET /opc/notification/inbox', () => {
    const params = { page: 1, pageSize: 50 }
    expectRequest(() => listInbox(params), {
      url: '/opc/notification/inbox',
      method: 'get',
      params,
    })
  })

  it('unreadCount() -> GET /opc/notification/inbox/unread-count', () => {
    expectRequest(() => unreadCount(), {
      url: '/opc/notification/inbox/unread-count',
      method: 'get',
    })
  })

  it('markRead(7) -> POST /opc/notification/inbox/read/7', () => {
    expectRequest(() => markRead(7), { url: '/opc/notification/inbox/read/7', method: 'post' })
  })

  it('markAllRead() -> POST /opc/notification/inbox/read-all', () => {
    expectRequest(() => markAllRead(), { url: '/opc/notification/inbox/read-all', method: 'post' })
  })
})
