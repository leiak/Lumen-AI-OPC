import request from '@/utils/request'
import type { AjaxResult } from '@/types'

// ================ OPC ERP 进销存 API (W53) ================

// ---------- 类型定义 (对齐 backend domain/dto) ----------

export interface OpcErpProduct {
  id?: number
  companyId?: number
  skuRoot?: string
  name: string
  category?: string
  brand?: string
  unit?: string
  description?: string
  specAttrs?: string
  status?: string
  createdBy?: number
  createTime?: string
  updateTime?: string
}

export interface OpcErpProductSku {
  id?: number
  companyId?: number
  productId: number
  skuCode?: string
  specJson?: string
  price?: number
  cost?: number
  stock?: number
  threshold?: number
  version?: number
  status?: string
  createTime?: string
  updateTime?: string
}

export interface OpcErpPurchase {
  id?: number
  companyId?: number
  purchaseNo?: string
  supplierId?: number
  totalAmount?: number
  status?: string
  operatorId?: number
  confirmedBy?: number
  confirmedAt?: string
  completedAt?: string
  cancelledAt?: string
  remark?: string
  createdBy?: number
  createTime?: string
  updateTime?: string
}

export interface OpcErpPurchaseItem {
  id?: number
  companyId?: number
  purchaseId?: number
  skuId: number
  quantity: number
  unitPrice?: number
  subtotal?: number
  batchNo?: string
  productionDate?: string
  expiryDate?: string
}

export interface OpcErpSale {
  id?: number
  companyId?: number
  saleNo?: string
  customerName?: string
  customerPhone?: string
  totalAmount?: number
  status?: string
  operatorId?: number
  confirmedBy?: number
  confirmedAt?: string
  completedAt?: string
  cancelledAt?: string
  remark?: string
  createdBy?: number
  createTime?: string
  updateTime?: string
}

export interface OpcErpSaleItem {
  id?: number
  companyId?: number
  saleId?: number
  skuId: number
  quantity: number
  unitPrice?: number
  subtotal?: number
  batchId?: number
}

export interface OpcErpReturn {
  id?: number
  companyId?: number
  returnNo?: string
  returnType: string
  refId?: number
  refundAmount?: number
  status?: string
  operatorId?: number
  confirmedBy?: number
  confirmedAt?: string
  completedAt?: string
  reason?: string
  remark?: string
  createdBy?: number
  createTime?: string
  updateTime?: string
}

export interface OpcErpReturnItem {
  id?: number
  skuId: number
  quantity: number
  unitPrice?: number
  subtotal?: number
  batchId?: number
  reason?: string
}

export interface OpcErpSupplier {
  id?: number
  companyId?: number
  name: string
  contact?: string
  phone?: string
  email?: string
  address?: string
  level?: string
  status?: string
  createdBy?: number
  createTime?: string
  updateTime?: string
}

export interface OpcErpInventoryLog {
  id?: number
  companyId?: number
  skuId?: number
  batchId?: number
  change?: number
  type?: string
  refType?: string
  refId?: number
  remark?: string
  createdBy?: number
  createTime?: string
}

export interface OpcErpDailySnapshot {
  date?: string
  skuId?: number
  opening?: number
  inQty?: number
  outQty?: number
  closing?: number
}

export interface OpcErpMonthlyReport {
  year?: number
  month?: number
  daily?: OpcErpDailySnapshot[]
}

export interface OpcErpDailyReport {
  date?: string
  skuCount?: number
  totalClosingStock?: number
  details?: OpcErpDailySnapshot[]
}

// ---------- Product (7) ----------

export function createProduct(data: Partial<OpcErpProduct>): Promise<AjaxResult<number>> {
  return request({ url: '/opc/erp/product', method: 'post', data })
}

export function listProducts(params: {
  companyId: number
  category?: string
}): Promise<AjaxResult<OpcErpProduct[]>> {
  return request({ url: '/opc/erp/product/list', method: 'get', params })
}

export function getProduct(id: number, companyId: number): Promise<AjaxResult<OpcErpProduct>> {
  return request({ url: `/opc/erp/product/${id}`, method: 'get', params: { companyId } })
}

export function updateProduct(
  id: number,
  data: Partial<OpcErpProduct>
): Promise<AjaxResult<void>> {
  return request({ url: `/opc/erp/product/${id}`, method: 'put', data })
}

export function deleteProduct(id: number, companyId: number): Promise<AjaxResult<void>> {
  return request({ url: `/opc/erp/product/${id}`, method: 'delete', params: { companyId } })
}

export function autoCategoryProduct(data: {
  name: string
  description?: string
}): Promise<AjaxResult<string>> {
  return request({ url: '/opc/erp/product/auto-category', method: 'post', data })
}

export function listProductSkus(params: {
  companyId: number
  productId?: number
}): Promise<AjaxResult<OpcErpProductSku[]>> {
  return request({ url: '/opc/erp/product/product-sku/list', method: 'get', params })
}

// ---------- Purchase (6) ----------

export function createPurchase(data: {
  companyId: number
  supplierId: number
  remark?: string
  items: OpcErpPurchaseItem[]
}): Promise<AjaxResult<number>> {
  return request({ url: '/opc/erp/purchase', method: 'post', data })
}

export function confirmPurchase(id: number, companyId: number): Promise<AjaxResult<void>> {
  return request({
    url: `/opc/erp/purchase/${id}/confirm`,
    method: 'post',
    params: { companyId },
  })
}

export function cancelPurchase(id: number, companyId: number): Promise<AjaxResult<void>> {
  return request({
    url: `/opc/erp/purchase/${id}/cancel`,
    method: 'post',
    params: { companyId },
  })
}

export function listPurchases(params: {
  companyId: number
  status?: string
}): Promise<AjaxResult<OpcErpPurchase[]>> {
  return request({ url: '/opc/erp/purchase/list', method: 'get', params })
}

export function getPurchase(id: number, companyId: number): Promise<AjaxResult<OpcErpPurchase>> {
  return request({ url: `/opc/erp/purchase/${id}`, method: 'get', params: { companyId } })
}

export function getPurchaseByNo(
  purchaseNo: string,
  companyId: number
): Promise<AjaxResult<OpcErpPurchase>> {
  return request({
    url: `/opc/erp/purchase/no/${purchaseNo}`,
    method: 'get',
    params: { companyId },
  })
}

// ---------- Sale (6) ----------

export function createSale(data: {
  companyId: number
  customerName?: string
  customerPhone?: string
  remark?: string
  items: OpcErpSaleItem[]
}): Promise<AjaxResult<number>> {
  return request({ url: '/opc/erp/sale', method: 'post', data })
}

export function confirmSale(id: number, companyId: number): Promise<AjaxResult<void>> {
  return request({
    url: `/opc/erp/sale/${id}/confirm`,
    method: 'post',
    params: { companyId },
  })
}

export function cancelSale(id: number, companyId: number): Promise<AjaxResult<void>> {
  return request({
    url: `/opc/erp/sale/${id}/cancel`,
    method: 'post',
    params: { companyId },
  })
}

export function listSales(params: {
  companyId: number
  status?: string
}): Promise<AjaxResult<OpcErpSale[]>> {
  return request({ url: '/opc/erp/sale/list', method: 'get', params })
}

export function getSale(id: number, companyId: number): Promise<AjaxResult<OpcErpSale>> {
  return request({ url: `/opc/erp/sale/${id}`, method: 'get', params: { companyId } })
}

export function getSaleByNo(
  saleNo: string,
  companyId: number
): Promise<AjaxResult<OpcErpSale>> {
  return request({
    url: `/opc/erp/sale/no/${saleNo}`,
    method: 'get',
    params: { companyId },
  })
}

// ---------- Return (5) ----------

export function createReturn(data: {
  companyId: number
  returnType: string
  refId?: number
  reason?: string
  remark?: string
  items: OpcErpReturnItem[]
}): Promise<AjaxResult<number>> {
  return request({ url: '/opc/erp/return', method: 'post', data })
}

export function confirmReturn(id: number, companyId: number): Promise<AjaxResult<void>> {
  return request({
    url: `/opc/erp/return/${id}/confirm`,
    method: 'post',
    params: { companyId },
  })
}

export function cancelReturn(id: number, companyId: number): Promise<AjaxResult<void>> {
  return request({
    url: `/opc/erp/return/${id}/cancel`,
    method: 'post',
    params: { companyId },
  })
}

export function listReturns(params: {
  companyId: number
  returnType?: string
  status?: string
}): Promise<AjaxResult<OpcErpReturn[]>> {
  return request({ url: '/opc/erp/return/list', method: 'get', params })
}

export function getReturn(id: number, companyId: number): Promise<AjaxResult<OpcErpReturn>> {
  return request({ url: `/opc/erp/return/${id}`, method: 'get', params: { companyId } })
}

// ---------- Supplier (5) ----------

export function createSupplier(data: Partial<OpcErpSupplier>): Promise<AjaxResult<number>> {
  return request({ url: '/opc/erp/supplier', method: 'post', data })
}

export function listSuppliers(params: {
  companyId: number
  level?: string
}): Promise<AjaxResult<OpcErpSupplier[]>> {
  return request({ url: '/opc/erp/supplier/list', method: 'get', params })
}

export function getSupplier(id: number, companyId: number): Promise<AjaxResult<OpcErpSupplier>> {
  return request({ url: `/opc/erp/supplier/${id}`, method: 'get', params: { companyId } })
}

export function updateSupplier(
  id: number,
  data: Partial<OpcErpSupplier>
): Promise<AjaxResult<void>> {
  return request({ url: `/opc/erp/supplier/${id}`, method: 'put', data })
}

export function deleteSupplier(id: number, companyId: number): Promise<AjaxResult<void>> {
  return request({ url: `/opc/erp/supplier/${id}`, method: 'delete', params: { companyId } })
}

// ---------- Inventory (5) ----------

export function getInventorySku(
  skuId: number,
  companyId: number
): Promise<AjaxResult<OpcErpProductSku>> {
  return request({
    url: `/opc/erp/inventory/sku/${skuId}`,
    method: 'get',
    params: { companyId },
  })
}

export function listLowStock(companyId: number): Promise<AjaxResult<OpcErpProductSku[]>> {
  return request({
    url: '/opc/erp/inventory/low-stock',
    method: 'get',
    params: { companyId },
  })
}

export function getInventoryLog(
  skuId: number,
  companyId: number
): Promise<AjaxResult<OpcErpInventoryLog[]>> {
  return request({
    url: `/opc/erp/inventory/log/${skuId}`,
    method: 'get',
    params: { companyId },
  })
}

export function triggerDailySnapshot(): Promise<AjaxResult<void>> {
  return request({ url: '/opc/erp/inventory/internal/snapshot', method: 'post' })
}

export function triggerLowStockAlert(): Promise<AjaxResult<number>> {
  return request({ url: '/opc/erp/inventory/internal/low-stock-alert', method: 'post' })
}

// ---------- Report (2) ----------

export function getDailyReport(params: {
  companyId: number
  date: string
}): Promise<AjaxResult<OpcErpDailyReport>> {
  return request({ url: '/opc/erp/report/daily', method: 'get', params })
}

export function getMonthlyReport(params: {
  companyId: number
  year: number
  month: number
}): Promise<AjaxResult<OpcErpMonthlyReport>> {
  return request({ url: '/opc/erp/report/monthly', method: 'get', params })
}
