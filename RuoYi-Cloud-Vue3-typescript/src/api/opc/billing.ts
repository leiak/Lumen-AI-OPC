import request from '@/utils/request'
import type { AjaxResult } from '@/types'

// ================ OPC 计费 API ================

export function getWallet(companyId: number): Promise<AjaxResult> {
  return request({ url: '/opc/billing/wallet', method: 'get', params: { companyId } })
}

export function recharge(data: any): Promise<AjaxResult> {
  return request({ url: '/opc/billing/wallet/recharge', method: 'post', data })
}

export function listOrders(companyId: number, payStatus?: string, limit = 20): Promise<AjaxResult> {
  return request({ url: '/opc/billing/orders', method: 'get', params: { companyId, payStatus, limit } })
}

export function orderDetail(orderNo: string): Promise<AjaxResult> {
  return request({ url: `/opc/billing/order/${orderNo}`, method: 'get' })
}
