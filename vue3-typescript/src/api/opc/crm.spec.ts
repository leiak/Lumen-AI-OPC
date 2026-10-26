// api/opc/crm —— 客户/联系人/跟进/商机/合同/订单的 api 薄包装。
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({ default: vi.fn() }))

import request from '@/utils/request'
import * as crm from '@/api/opc/crm'
import {
  listCustomers,
  getCustomer,
  createCustomer,
  updateCustomer,
  deleteCustomer,
  listContacts,
  createContact,
  updateContact,
  deleteContact,
  setPrimaryContact,
  listFollowUps,
  createFollowUp,
  completeFollowUp,
  listOpportunities,
  getOpportunity,
  createOpportunity,
  updateOpportunity,
  changeOpportunityStage,
  scoreOpportunity,
  listContracts,
  createContract,
  activateContract,
  expireContract,
  terminateContract,
  listOrders,
  createOrder,
  payOrder,
  shipOrder,
  completeOrder,
  cancelOrder,
  getDashboard,
  getCustomersByLevel,
  getUpcomingFollowUps,
} from '@/api/opc/crm'
import { expectApiContract, expectRequest } from '@/test/api-contract'

const requestMock = vi.mocked(request)

beforeEach(() => {
  requestMock.mockReset()
  requestMock.mockResolvedValue({ code: 200 } as any)
})

describe('api/opc/crm', () => {
  it('每个导出函数都遵守 request 契约（url 前缀 / 动词 / 不撞路由）', () => {
    expectApiContract(crm, '/opc/crm/')
  })

  // ---- customer ----
  it('listCustomers(params) -> GET /opc/crm/customer', () => {
    const params = { level: 'A', keyword: '张' }
    expectRequest(() => listCustomers(params), {
      url: '/opc/crm/customer',
      method: 'get',
      params,
    })
  })

  it('getCustomer(3) -> GET /opc/crm/customer/3', () => {
    expectRequest(() => getCustomer(3), { url: '/opc/crm/customer/3', method: 'get' })
  })

  it('createCustomer(data) -> POST /opc/crm/customer', () => {
    const data = { name: '张三', level: 'A' } as any
    expectRequest(() => createCustomer(data), { url: '/opc/crm/customer', method: 'post', data })
  })

  it('updateCustomer(3, data) -> PUT /opc/crm/customer/3', () => {
    const data = { name: '张三改' } as any
    expectRequest(() => updateCustomer(3, data), { url: '/opc/crm/customer/3', method: 'put', data })
  })

  it('deleteCustomer(3) -> DELETE /opc/crm/customer/3', () => {
    expectRequest(() => deleteCustomer(3), { url: '/opc/crm/customer/3', method: 'delete' })
  })

  // ---- contact ----
  it('listContacts(3) -> GET /opc/crm/contact?customerId=3', () => {
    expectRequest(() => listContacts(3), {
      url: '/opc/crm/contact',
      method: 'get',
      params: { customerId: 3 },
    })
  })

  it('createContact(data) -> POST /opc/crm/contact', () => {
    const data = { customerId: 3, name: '李四' } as any
    expectRequest(() => createContact(data), { url: '/opc/crm/contact', method: 'post', data })
  })

  it('updateContact(8, data) -> PUT /opc/crm/contact/8', () => {
    const data = { customerId: 3, name: '李四改' } as any
    expectRequest(() => updateContact(8, data), { url: '/opc/crm/contact/8', method: 'put', data })
  })

  it('deleteContact(8) -> DELETE /opc/crm/contact/8', () => {
    expectRequest(() => deleteContact(8), { url: '/opc/crm/contact/8', method: 'delete' })
  })

  it('setPrimaryContact(8, 3) -> POST /opc/crm/contact/8/primary', () => {
    expectRequest(() => setPrimaryContact(8, 3), {
      url: '/opc/crm/contact/8/primary',
      method: 'post',
      params: { customerId: 3 },
    })
  })

  // ---- follow-up ----
  it('listFollowUps(params) -> GET /opc/crm/follow-up', () => {
    const params = { customerId: 3 }
    expectRequest(() => listFollowUps(params), {
      url: '/opc/crm/follow-up',
      method: 'get',
      params,
    })
  })

  it('createFollowUp(data) -> POST /opc/crm/follow-up', () => {
    const data = { customerId: 3, type: 'PHONE', content: '打过电话' } as any
    expectRequest(() => createFollowUp(data), { url: '/opc/crm/follow-up', method: 'post', data })
  })

  it('completeFollowUp(4, "已成交") -> POST /opc/crm/follow-up/4/complete', () => {
    expectRequest(() => completeFollowUp(4, '已成交'), {
      url: '/opc/crm/follow-up/4/complete',
      method: 'post',
      params: { result: '已成交' },
    })
  })

  // ---- opportunity ----
  it('listOpportunities(params) -> GET /opc/crm/opportunity', () => {
    const params = { stage: 'LEAD' }
    expectRequest(() => listOpportunities(params), {
      url: '/opc/crm/opportunity',
      method: 'get',
      params,
    })
  })

  it('getOpportunity(2) -> GET /opc/crm/opportunity/2', () => {
    expectRequest(() => getOpportunity(2), { url: '/opc/crm/opportunity/2', method: 'get' })
  })

  it('createOpportunity(data) -> POST /opc/crm/opportunity', () => {
    const data = { customerId: 3, title: '新单' } as any
    expectRequest(() => createOpportunity(data), {
      url: '/opc/crm/opportunity',
      method: 'post',
      data,
    })
  })

  it('updateOpportunity(2, data) -> PUT /opc/crm/opportunity/2', () => {
    const data = { customerId: 3, title: '改单' } as any
    expectRequest(() => updateOpportunity(2, data), {
      url: '/opc/crm/opportunity/2',
      method: 'put',
      data,
    })
  })

  it('changeOpportunityStage(2, "QUALIFIED") -> POST /opc/crm/opportunity/2/stage', () => {
    expectRequest(() => changeOpportunityStage(2, 'QUALIFIED'), {
      url: '/opc/crm/opportunity/2/stage',
      method: 'post',
      data: { stage: 'QUALIFIED', reason: undefined },
    })
  })

  it('scoreOpportunity(2) -> POST /opc/crm/opportunity/2/score', () => {
    expectRequest(() => scoreOpportunity(2), { url: '/opc/crm/opportunity/2/score', method: 'post' })
  })

  // ---- contract ----
  it('listContracts(3) -> GET /opc/crm/contract?customerId=3', () => {
    expectRequest(() => listContracts(3), {
      url: '/opc/crm/contract',
      method: 'get',
      params: { customerId: 3 },
    })
  })

  it('createContract(data) -> POST /opc/crm/contract', () => {
    const data = { customerId: 3, amount: 100 } as any
    expectRequest(() => createContract(data), { url: '/opc/crm/contract', method: 'post', data })
  })

  it('activateContract(6) -> POST /opc/crm/contract/6/activate', () => {
    expectRequest(() => activateContract(6), { url: '/opc/crm/contract/6/activate', method: 'post' })
  })

  it('expireContract(6) -> POST /opc/crm/contract/6/expire', () => {
    expectRequest(() => expireContract(6), { url: '/opc/crm/contract/6/expire', method: 'post' })
  })

  it('terminateContract(6) -> POST /opc/crm/contract/6/terminate', () => {
    expectRequest(() => terminateContract(6), { url: '/opc/crm/contract/6/terminate', method: 'post' })
  })

  // ---- order ----
  it('listOrders(params) -> GET /opc/crm/order', () => {
    const params = { customerId: 3 }
    expectRequest(() => listOrders(params), { url: '/opc/crm/order', method: 'get', params })
  })

  it('createOrder(data) -> POST /opc/crm/order', () => {
    const data = { customerId: 3, itemsJson: '[]' } as any
    expectRequest(() => createOrder(data), { url: '/opc/crm/order', method: 'post', data })
  })

  it('payOrder(7) -> POST /opc/crm/order/7/pay', () => {
    expectRequest(() => payOrder(7), { url: '/opc/crm/order/7/pay', method: 'post' })
  })

  it('shipOrder(7) -> POST /opc/crm/order/7/ship', () => {
    expectRequest(() => shipOrder(7), { url: '/opc/crm/order/7/ship', method: 'post' })
  })

  it('completeOrder(7) -> POST /opc/crm/order/7/complete', () => {
    expectRequest(() => completeOrder(7), { url: '/opc/crm/order/7/complete', method: 'post' })
  })

  it('cancelOrder(7) -> POST /opc/crm/order/7/cancel', () => {
    expectRequest(() => cancelOrder(7), { url: '/opc/crm/order/7/cancel', method: 'post' })
  })

  // ---- dashboard ----
  it('getDashboard() -> GET /opc/crm/dashboard', () => {
    expectRequest(() => getDashboard(), { url: '/opc/crm/dashboard', method: 'get' })
  })

  it('getCustomersByLevel() -> GET /opc/crm/dashboard/customers', () => {
    expectRequest(() => getCustomersByLevel(), { url: '/opc/crm/dashboard/customers', method: 'get' })
  })

  it('getUpcomingFollowUps() -> GET /opc/crm/dashboard/follow-ups/upcoming', () => {
    expectRequest(() => getUpcomingFollowUps(), {
      url: '/opc/crm/dashboard/follow-ups/upcoming',
      method: 'get',
    })
  })
})
