// api/opc/erp —— 商品/采购/销售/退货/供应商/库存/报表的 api 薄包装。
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({ default: vi.fn() }))

import request from '@/utils/request'
import * as erp from '@/api/opc/erp'
import {
  createProduct,
  listProducts,
  getProduct,
  updateProduct,
  deleteProduct,
  autoCategoryProduct,
  listProductSkus,
  createPurchase,
  confirmPurchase,
  cancelPurchase,
  listPurchases,
  getPurchase,
  getPurchaseByNo,
  createSale,
  confirmSale,
  cancelSale,
  listSales,
  getSale,
  getSaleByNo,
  createReturn,
  confirmReturn,
  cancelReturn,
  listReturns,
  getReturn,
  createSupplier,
  listSuppliers,
  getSupplier,
  updateSupplier,
  deleteSupplier,
  getInventorySku,
  listLowStock,
  getInventoryLog,
  triggerDailySnapshot,
  triggerLowStockAlert,
  getDailyReport,
  getMonthlyReport,
} from '@/api/opc/erp'
import { expectApiContract, expectRequest } from '@/test/api-contract'

const requestMock = vi.mocked(request)

beforeEach(() => {
  requestMock.mockReset()
  requestMock.mockResolvedValue({ code: 200 } as any)
})

describe('api/opc/erp', () => {
  it('每个导出函数都遵守 request 契约（url 前缀 / 动词 / 不撞路由）', () => {
    expectApiContract(erp, '/opc/erp/')
  })

  // ---- product ----
  it('createProduct(data) -> POST /opc/erp/product', () => {
    const data = { name: 'A4 纸' } as any
    expectRequest(() => createProduct(data), { url: '/opc/erp/product', method: 'post', data })
  })

  it('listProducts(params) -> GET /opc/erp/product/list', () => {
    const params = { companyId: 1, category: '办公' }
    expectRequest(() => listProducts(params), { url: '/opc/erp/product/list', method: 'get', params })
  })

  it('getProduct(5, 1) -> GET /opc/erp/product/5?companyId=1', () => {
    expectRequest(() => getProduct(5, 1), {
      url: '/opc/erp/product/5',
      method: 'get',
      params: { companyId: 1 },
    })
  })

  it('updateProduct(5, data) -> PUT /opc/erp/product/5', () => {
    const data = { name: 'A4 纸（改）' } as any
    expectRequest(() => updateProduct(5, data), { url: '/opc/erp/product/5', method: 'put', data })
  })

  it('deleteProduct(5, 1) -> DELETE /opc/erp/product/5', () => {
    expectRequest(() => deleteProduct(5, 1), {
      url: '/opc/erp/product/5',
      method: 'delete',
      params: { companyId: 1 },
    })
  })

  it('autoCategoryProduct(data) -> POST /opc/erp/product/auto-category', () => {
    const data = { name: 'A4 纸' }
    expectRequest(() => autoCategoryProduct(data), {
      url: '/opc/erp/product/auto-category',
      method: 'post',
      data,
    })
  })

  it('listProductSkus(params) -> GET /opc/erp/product/product-sku/list', () => {
    const params = { companyId: 1, productId: 5 }
    expectRequest(() => listProductSkus(params), {
      url: '/opc/erp/product/product-sku/list',
      method: 'get',
      params,
    })
  })

  // ---- purchase ----
  it('createPurchase(data) -> POST /opc/erp/purchase', () => {
    const data = { companyId: 1, supplierId: 2, items: [] } as any
    expectRequest(() => createPurchase(data), { url: '/opc/erp/purchase', method: 'post', data })
  })

  it('confirmPurchase(5, 1) -> POST /opc/erp/purchase/5/confirm', () => {
    expectRequest(() => confirmPurchase(5, 1), {
      url: '/opc/erp/purchase/5/confirm',
      method: 'post',
      params: { companyId: 1 },
    })
  })

  it('cancelPurchase(5, 1) -> POST /opc/erp/purchase/5/cancel', () => {
    expectRequest(() => cancelPurchase(5, 1), {
      url: '/opc/erp/purchase/5/cancel',
      method: 'post',
      params: { companyId: 1 },
    })
  })

  it('listPurchases(params) -> GET /opc/erp/purchase/list', () => {
    const params = { companyId: 1, status: 'PENDING' }
    expectRequest(() => listPurchases(params), {
      url: '/opc/erp/purchase/list',
      method: 'get',
      params,
    })
  })

  it('getPurchase(5, 1) -> GET /opc/erp/purchase/5', () => {
    expectRequest(() => getPurchase(5, 1), {
      url: '/opc/erp/purchase/5',
      method: 'get',
      params: { companyId: 1 },
    })
  })

  it('getPurchaseByNo("PO-1", 1) -> GET /opc/erp/purchase/no/PO-1', () => {
    expectRequest(() => getPurchaseByNo('PO-1', 1), {
      url: '/opc/erp/purchase/no/PO-1',
      method: 'get',
      params: { companyId: 1 },
    })
  })

  // ---- sale ----
  it('createSale(data) -> POST /opc/erp/sale', () => {
    const data = { companyId: 1, items: [] } as any
    expectRequest(() => createSale(data), { url: '/opc/erp/sale', method: 'post', data })
  })

  it('confirmSale(5, 1) -> POST /opc/erp/sale/5/confirm', () => {
    expectRequest(() => confirmSale(5, 1), {
      url: '/opc/erp/sale/5/confirm',
      method: 'post',
      params: { companyId: 1 },
    })
  })

  it('cancelSale(5, 1) -> POST /opc/erp/sale/5/cancel', () => {
    expectRequest(() => cancelSale(5, 1), {
      url: '/opc/erp/sale/5/cancel',
      method: 'post',
      params: { companyId: 1 },
    })
  })

  it('listSales(params) -> GET /opc/erp/sale/list', () => {
    const params = { companyId: 1 }
    expectRequest(() => listSales(params), { url: '/opc/erp/sale/list', method: 'get', params })
  })

  it('getSale(5, 1) -> GET /opc/erp/sale/5', () => {
    expectRequest(() => getSale(5, 1), {
      url: '/opc/erp/sale/5',
      method: 'get',
      params: { companyId: 1 },
    })
  })

  it('getSaleByNo("SO-1", 1) -> GET /opc/erp/sale/no/SO-1', () => {
    expectRequest(() => getSaleByNo('SO-1', 1), {
      url: '/opc/erp/sale/no/SO-1',
      method: 'get',
      params: { companyId: 1 },
    })
  })

  // ---- return ----
  it('createReturn(data) -> POST /opc/erp/return', () => {
    const data = { companyId: 1, returnType: 'PURCHASE', items: [] } as any
    expectRequest(() => createReturn(data), { url: '/opc/erp/return', method: 'post', data })
  })

  it('confirmReturn(5, 1) -> POST /opc/erp/return/5/confirm', () => {
    expectRequest(() => confirmReturn(5, 1), {
      url: '/opc/erp/return/5/confirm',
      method: 'post',
      params: { companyId: 1 },
    })
  })

  it('cancelReturn(5, 1) -> POST /opc/erp/return/5/cancel', () => {
    expectRequest(() => cancelReturn(5, 1), {
      url: '/opc/erp/return/5/cancel',
      method: 'post',
      params: { companyId: 1 },
    })
  })

  it('listReturns(params) -> GET /opc/erp/return/list', () => {
    const params = { companyId: 1 }
    expectRequest(() => listReturns(params), { url: '/opc/erp/return/list', method: 'get', params })
  })

  it('getReturn(5, 1) -> GET /opc/erp/return/5', () => {
    expectRequest(() => getReturn(5, 1), {
      url: '/opc/erp/return/5',
      method: 'get',
      params: { companyId: 1 },
    })
  })

  // ---- supplier ----
  it('createSupplier(data) -> POST /opc/erp/supplier', () => {
    const data = { name: '供货商' } as any
    expectRequest(() => createSupplier(data), { url: '/opc/erp/supplier', method: 'post', data })
  })

  it('listSuppliers(params) -> GET /opc/erp/supplier/list', () => {
    const params = { companyId: 1, level: 'A' }
    expectRequest(() => listSuppliers(params), {
      url: '/opc/erp/supplier/list',
      method: 'get',
      params,
    })
  })

  it('getSupplier(3, 1) -> GET /opc/erp/supplier/3', () => {
    expectRequest(() => getSupplier(3, 1), {
      url: '/opc/erp/supplier/3',
      method: 'get',
      params: { companyId: 1 },
    })
  })

  it('updateSupplier(3, data) -> PUT /opc/erp/supplier/3', () => {
    const data = { name: '供货商改' } as any
    expectRequest(() => updateSupplier(3, data), { url: '/opc/erp/supplier/3', method: 'put', data })
  })

  it('deleteSupplier(3, 1) -> DELETE /opc/erp/supplier/3', () => {
    expectRequest(() => deleteSupplier(3, 1), {
      url: '/opc/erp/supplier/3',
      method: 'delete',
      params: { companyId: 1 },
    })
  })

  // ---- inventory ----
  it('getInventorySku(11, 1) -> GET /opc/erp/inventory/sku/11', () => {
    expectRequest(() => getInventorySku(11, 1), {
      url: '/opc/erp/inventory/sku/11',
      method: 'get',
      params: { companyId: 1 },
    })
  })

  it('listLowStock(1) -> GET /opc/erp/inventory/low-stock', () => {
    expectRequest(() => listLowStock(1), {
      url: '/opc/erp/inventory/low-stock',
      method: 'get',
      params: { companyId: 1 },
    })
  })

  it('getInventoryLog(11, 1) -> GET /opc/erp/inventory/log/11', () => {
    expectRequest(() => getInventoryLog(11, 1), {
      url: '/opc/erp/inventory/log/11',
      method: 'get',
      params: { companyId: 1 },
    })
  })

  it('triggerDailySnapshot() -> POST /opc/erp/inventory/internal/snapshot', () => {
    expectRequest(() => triggerDailySnapshot(), {
      url: '/opc/erp/inventory/internal/snapshot',
      method: 'post',
    })
  })

  it('triggerLowStockAlert() -> POST /opc/erp/inventory/internal/low-stock-alert', () => {
    expectRequest(() => triggerLowStockAlert(), {
      url: '/opc/erp/inventory/internal/low-stock-alert',
      method: 'post',
    })
  })

  // ---- report ----
  it('getDailyReport(params) -> GET /opc/erp/report/daily', () => {
    const params = { companyId: 1, date: '2026-01-01' }
    expectRequest(() => getDailyReport(params), { url: '/opc/erp/report/daily', method: 'get', params })
  })

  it('getMonthlyReport(params) -> GET /opc/erp/report/monthly', () => {
    const params = { companyId: 1, year: 2026, month: 1 }
    expectRequest(() => getMonthlyReport(params), {
      url: '/opc/erp/report/monthly',
      method: 'get',
      params,
    })
  })
})
