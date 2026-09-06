import request from '@/utils/request'
import type { AjaxResult } from '@/types'

// ================ OPC 财务 API ================

export function listVouchers(companyId: number, period?: string, status?: string, limit = 20): Promise<AjaxResult> {
  return request({ url: '/opc/finance/vouchers', method: 'get', params: { companyId, period, status, limit } })
}

export function voucherDetail(id: number): Promise<AjaxResult> {
  return request({ url: `/opc/finance/voucher/${id}`, method: 'get' })
}

export function createVoucher(data: any): Promise<AjaxResult> {
  return request({ url: '/opc/finance/voucher', method: 'post', data })
}

export function updateVoucher(data: any): Promise<AjaxResult> {
  return request({ url: '/opc/finance/voucher', method: 'put', data })
}

export function reviewPass(id: number): Promise<AjaxResult> {
  return request({ url: `/opc/finance/voucher/${id}/review-pass`, method: 'post' })
}

export function reviewReject(id: number, opinion?: string): Promise<AjaxResult> {
  return request({ url: `/opc/finance/voucher/${id}/review-reject`, method: 'post', params: { opinion } })
}

export function postVoucher(id: number): Promise<AjaxResult> {
  return request({ url: `/opc/finance/voucher/${id}/post`, method: 'post' })
}

export function uploadFlows(data: any[]): Promise<AjaxResult> {
  return request({ url: '/opc/finance/flows/upload', method: 'post', data })
}

export function pendingFlows(companyId: number, limit = 20): Promise<AjaxResult> {
  return request({ url: '/opc/finance/flows/pending', method: 'get', params: { companyId, limit } })
}

export function extractFlows(companyId: number): Promise<AjaxResult> {
  return request({ url: '/opc/finance/flows/extract', method: 'post', params: { companyId } })
}

export function generateDailyReport(companyId: number): Promise<AjaxResult> {
  return request({ url: '/opc/finance/daily-report', method: 'post', params: { companyId } })
}

// ================ 月度税务报表 (W1 Sub-task 4.3) ================

export function generateTaxReport(companyId: number, period: string): Promise<AjaxResult> {
  return request({ url: '/opc/finance/tax-reports/generate', method: 'post', params: { companyId, period } })
}

export function listTaxReports(companyId: number, period?: string, status?: string, limit = 20): Promise<AjaxResult> {
  return request({ url: '/opc/finance/tax-reports', method: 'get', params: { companyId, period, status, limit } })
}

export function taxReportDetail(id: number): Promise<AjaxResult> {
  return request({ url: `/opc/finance/tax-reports/${id}`, method: 'get' })
}
