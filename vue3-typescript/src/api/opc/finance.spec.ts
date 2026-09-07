// W12.4 — api/opc/finance.spec.ts. Covers 14 endpoints across vouchers,
// bank flows, daily/tax reports.
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({ default: vi.fn() }))

import request from '@/utils/request'
import {
  listVouchers,
  voucherDetail,
  createVoucher,
  updateVoucher,
  reviewPass,
  reviewReject,
  postVoucher,
  uploadFlows,
  pendingFlows,
  extractFlows,
  generateDailyReport,
  generateTaxReport,
  listTaxReports,
  taxReportDetail,
} from '@/api/opc/finance'

const requestMock = vi.mocked(request)

beforeEach(() => {
  requestMock.mockReset()
  requestMock.mockResolvedValue({ code: 200 } as any)
})

describe('api/opc/finance', () => {
  // ---- vouchers ----
  it('1. listVouchers(1,"2026-09","REVIEW",50) -> GET /opc/finance/vouchers params', () => {
    listVouchers(1, '2026-09', 'REVIEW', 50)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/finance/vouchers',
      method: 'get',
      params: { companyId: 1, period: '2026-09', status: 'REVIEW', limit: 50 },
    })
  })

  it('2. listVouchers(1) -> defaults period=undefined, status=undefined, limit=20', () => {
    listVouchers(1)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/finance/vouchers',
      method: 'get',
      params: { companyId: 1, period: undefined, status: undefined, limit: 20 },
    })
  })

  it('3. voucherDetail(42) -> GET /opc/finance/voucher/42', () => {
    voucherDetail(42)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/finance/voucher/42',
      method: 'get',
    })
  })

  it('4. createVoucher({amount:100}) -> POST /opc/finance/voucher data', () => {
    createVoucher({ amount: 100 })
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/finance/voucher',
      method: 'post',
      data: { amount: 100 },
    })
  })

  it('5. updateVoucher({id:1, amount:200}) -> PUT /opc/finance/voucher data', () => {
    updateVoucher({ id: 1, amount: 200 })
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/finance/voucher',
      method: 'put',
      data: { id: 1, amount: 200 },
    })
  })

  it('6. reviewPass(7) -> POST /opc/finance/voucher/7/review-pass', () => {
    reviewPass(7)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/finance/voucher/7/review-pass',
      method: 'post',
    })
  })

  it('7. reviewReject(7,"missing receipt") -> POST + params.opinion', () => {
    reviewReject(7, 'missing receipt')
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/finance/voucher/7/review-reject',
      method: 'post',
      params: { opinion: 'missing receipt' },
    })
  })

  it('8. postVoucher(7) -> POST /opc/finance/voucher/7/post', () => {
    postVoucher(7)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/finance/voucher/7/post',
      method: 'post',
    })
  })

  // ---- flows ----
  it('9. uploadFlows([{row:1}]) -> POST /opc/finance/flows/upload data=array', () => {
    uploadFlows([{ row: 1 }])
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/finance/flows/upload',
      method: 'post',
      data: [{ row: 1 }],
    })
  })

  it('10. pendingFlows(1,50) -> GET /opc/finance/flows/pending params', () => {
    pendingFlows(1, 50)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/finance/flows/pending',
      method: 'get',
      params: { companyId: 1, limit: 50 },
    })
  })

  it('11. extractFlows(1) -> POST /opc/finance/flows/extract params={companyId}', () => {
    extractFlows(1)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/finance/flows/extract',
      method: 'post',
      params: { companyId: 1 },
    })
  })

  // ---- reports ----
  it('12. generateDailyReport(1) -> POST /opc/finance/daily-report params={companyId}', () => {
    generateDailyReport(1)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/finance/daily-report',
      method: 'post',
      params: { companyId: 1 },
    })
  })

  it('13. generateTaxReport(1,"2026-09") -> POST /opc/finance/tax-reports/generate', () => {
    generateTaxReport(1, '2026-09')
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/finance/tax-reports/generate',
      method: 'post',
      params: { companyId: 1, period: '2026-09' },
    })
  })

  it('14. listTaxReports(1,"2026-09","DONE",50) -> GET /opc/finance/tax-reports', () => {
    listTaxReports(1, '2026-09', 'DONE', 50)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/finance/tax-reports',
      method: 'get',
      params: { companyId: 1, period: '2026-09', status: 'DONE', limit: 50 },
    })
  })

  it('15. taxReportDetail(99) -> GET /opc/finance/tax-reports/99', () => {
    taxReportDetail(99)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/opc/finance/tax-reports/99',
      method: 'get',
    })
  })

  it('16. return value is the request() promise', () => {
    const sentinel = Symbol('p')
    requestMock.mockReturnValue(sentinel as any)
    expect(voucherDetail(1)).toBe(sentinel)
  })
})