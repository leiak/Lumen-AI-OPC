// W12.4 — api/opc/billing.spec.ts.
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({ default: vi.fn() }))

import request from '@/utils/request'
import {
  getWallet,
  recharge,
  listOrders,
  orderDetail,
} from '@/api/opc/billing'

const requestMock = vi.mocked(request)

beforeEach(() => {
  requestMock.mockReset()
  requestMock.mockResolvedValue({ code: 200 } as any)
})

describe('api/opc/billing', () => {
  it('1. getWallet(1) -> GET /opc/billing/wallet params={companyId:1}', () => {
    getWallet(1)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/billing/wallet',
      method: 'get',
      params: { companyId: 1 },
    })
  })

  it('2. recharge({amount:50, channel:"alipay"}) -> POST /opc/billing/wallet/recharge data', () => {
    recharge({ amount: 50, channel: 'alipay' })
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/billing/wallet/recharge',
      method: 'post',
      data: { amount: 50, channel: 'alipay' },
    })
  })

  it('3. listOrders(1,"PAID",50) -> GET /opc/billing/orders params', () => {
    listOrders(1, 'PAID', 50)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/billing/orders',
      method: 'get',
      params: { companyId: 1, payStatus: 'PAID', limit: 50 },
    })
  })

  it('4. listOrders(1) -> defaults payStatus=undefined + limit=20', () => {
    listOrders(1)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/billing/orders',
      method: 'get',
      params: { companyId: 1, payStatus: undefined, limit: 20 },
    })
  })

  it('5. orderDetail("O20260901") -> GET /opc/billing/order/O20260901', () => {
    orderDetail('O20260901')
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/billing/order/O20260901',
      method: 'get',
    })
  })

  it('6. return value is the request() promise', () => {
    const sentinel = Symbol('p')
    requestMock.mockReturnValue(sentinel as any)
    expect(getWallet(1)).toBe(sentinel)
  })
})