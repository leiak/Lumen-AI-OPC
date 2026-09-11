import request from '@/utils/request'
import type { AjaxResult } from '@/types'

// ================ OPC 客户关系管理 API (Task 18) ================

// ---------- Customer ----------

export interface CrmCustomer {
  id?: number
  ownerId?: number
  name: string
  level?: 'A' | 'B' | 'C' | 'D' | ''
  source?: 'REFERRAL' | 'AD' | 'WEBSITE' | 'COLD_CALL' | 'OTHER' | ''
  tags?: string
  phone?: string
  email?: string
  address?: string
  industry?: string
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

export function listCustomers(params: {
  level?: string
  source?: string
  tag?: string
  keyword?: string
}): Promise<AjaxResult<CrmCustomer[]>> {
  return request({ url: '/opc/crm/customer', method: 'get', params })
}

export function getCustomer(id: number): Promise<AjaxResult<CrmCustomer>> {
  return request({ url: `/opc/crm/customer/${id}`, method: 'get' })
}

export function createCustomer(data: CrmCustomer): Promise<AjaxResult<CrmCustomer>> {
  return request({ url: '/opc/crm/customer', method: 'post', data })
}

export function updateCustomer(id: number, data: CrmCustomer): Promise<AjaxResult<CrmCustomer>> {
  return request({ url: `/opc/crm/customer/${id}`, method: 'put', data })
}

export function deleteCustomer(id: number): Promise<AjaxResult<void>> {
  return request({ url: `/opc/crm/customer/${id}`, method: 'delete' })
}

// ---------- Contact ----------

export interface CrmContact {
  id?: number
  customerId: number
  name: string
  phone?: string
  email?: string
  position?: string
  isPrimary?: 0 | 1
  remark?: string
  createTime?: string
  updateTime?: string
}

export function listContacts(customerId: number): Promise<AjaxResult<CrmContact[]>> {
  return request({ url: '/opc/crm/contact', method: 'get', params: { customerId } })
}

export function createContact(data: CrmContact): Promise<AjaxResult<CrmContact>> {
  return request({ url: '/opc/crm/contact', method: 'post', data })
}

export function updateContact(id: number, data: CrmContact): Promise<AjaxResult<CrmContact>> {
  return request({ url: `/opc/crm/contact/${id}`, method: 'put', data })
}

export function deleteContact(id: number): Promise<AjaxResult<void>> {
  return request({ url: `/opc/crm/contact/${id}`, method: 'delete' })
}

export function setPrimaryContact(id: number, customerId: number): Promise<AjaxResult<void>> {
  return request({
    url: `/opc/crm/contact/${id}/primary`,
    method: 'post',
    params: { customerId },
  })
}

// ---------- FollowUp ----------

export interface CrmFollowUp {
  id?: number
  customerId: number
  contactId?: number
  ownerId?: number
  type?: 'PHONE' | 'EMAIL' | 'MEETING' | 'OTHER'
  content: string
  result?: string
  nextAt?: string
  completedAt?: string
  createTime?: string
}

export function listFollowUps(params: {
  customerId?: number
  contactId?: number
  ownerId?: number
}): Promise<AjaxResult<CrmFollowUp[]>> {
  return request({ url: '/opc/crm/follow-up', method: 'get', params })
}

export function createFollowUp(data: CrmFollowUp): Promise<AjaxResult<CrmFollowUp>> {
  return request({ url: '/opc/crm/follow-up', method: 'post', data })
}

export function completeFollowUp(id: number, result?: string): Promise<AjaxResult<CrmFollowUp>> {
  return request({
    url: `/opc/crm/follow-up/${id}/complete`,
    method: 'post',
    params: { result },
  })
}

// ---------- Opportunity ----------

export type OpportunityStage =
  | 'LEAD'
  | 'QUALIFIED'
  | 'PROPOSAL'
  | 'NEGOTIATION'
  | 'WON'
  | 'LOST'

export interface CrmOpportunity {
  id?: number
  customerId: number
  name: string
  amount?: number
  stage?: OpportunityStage
  score?: number
  scoreReason?: string
  expectedClose?: string
  ownerId?: number
  lostReason?: string
  createTime?: string
  updateTime?: string
}

export interface OpportunityScoreResponse {
  score: number
  reason: string
  risks?: string[]
}

export function listOpportunities(params: {
  customerId?: number
  stage?: OpportunityStage
}): Promise<AjaxResult<CrmOpportunity[]>> {
  return request({ url: '/opc/crm/opportunity', method: 'get', params })
}

export function getOpportunity(id: number): Promise<AjaxResult<CrmOpportunity>> {
  return request({ url: `/opc/crm/opportunity/${id}`, method: 'get' })
}

export function createOpportunity(data: CrmOpportunity): Promise<AjaxResult<CrmOpportunity>> {
  return request({ url: '/opc/crm/opportunity', method: 'post', data })
}

export function updateOpportunity(
  id: number,
  data: CrmOpportunity
): Promise<AjaxResult<CrmOpportunity>> {
  return request({ url: `/opc/crm/opportunity/${id}`, method: 'put', data })
}

export function changeOpportunityStage(
  id: number,
  stage: OpportunityStage,
  reason?: string
): Promise<AjaxResult<CrmOpportunity>> {
  return request({
    url: `/opc/crm/opportunity/${id}/stage`,
    method: 'post',
    data: { stage, reason },
  })
}

export function scoreOpportunity(id: number): Promise<AjaxResult<OpportunityScoreResponse>> {
  return request({ url: `/opc/crm/opportunity/${id}/score`, method: 'post' })
}

// ---------- Contract ----------

export type ContractStatus = 'DRAFT' | 'ACTIVE' | 'EXPIRED' | 'TERMINATED'

export interface CrmContract {
  id?: number
  customerId: number
  opportunityId?: number
  contractNo: string
  title: string
  amount?: number
  status?: ContractStatus
  signedAt?: string
  expireAt?: string
  fileUrl?: string
  remark?: string
  createTime?: string
}

export function listContracts(customerId: number): Promise<AjaxResult<CrmContract[]>> {
  return request({ url: '/opc/crm/contract', method: 'get', params: { customerId } })
}

export function createContract(data: CrmContract): Promise<AjaxResult<CrmContract>> {
  return request({ url: '/opc/crm/contract', method: 'post', data })
}

export function activateContract(id: number): Promise<AjaxResult<CrmContract>> {
  return request({ url: `/opc/crm/contract/${id}/activate`, method: 'post' })
}

export function expireContract(id: number): Promise<AjaxResult<CrmContract>> {
  return request({ url: `/opc/crm/contract/${id}/expire`, method: 'post' })
}

export function terminateContract(id: number): Promise<AjaxResult<CrmContract>> {
  return request({ url: `/opc/crm/contract/${id}/terminate`, method: 'post' })
}

// ---------- Order ----------

export type OrderStatus = 'PENDING' | 'PAID' | 'SHIPPED' | 'COMPLETED' | 'CANCELLED'

export interface CrmOrder {
  id?: number
  customerId: number
  contractId?: number
  orderNo: string
  itemsJson?: string
  total?: number
  status?: OrderStatus
  paidAt?: string
  shippedAt?: string
  remark?: string
  createTime?: string
}

export function listOrders(params: {
  customerId?: number
  contractId?: number
}): Promise<AjaxResult<CrmOrder[]>> {
  return request({ url: '/opc/crm/order', method: 'get', params })
}

export function createOrder(data: CrmOrder): Promise<AjaxResult<CrmOrder>> {
  return request({ url: '/opc/crm/order', method: 'post', data })
}

export function payOrder(id: number): Promise<AjaxResult<CrmOrder>> {
  return request({ url: `/opc/crm/order/${id}/pay`, method: 'post' })
}

export function shipOrder(id: number): Promise<AjaxResult<CrmOrder>> {
  return request({ url: `/opc/crm/order/${id}/ship`, method: 'post' })
}

export function completeOrder(id: number): Promise<AjaxResult<CrmOrder>> {
  return request({ url: `/opc/crm/order/${id}/complete`, method: 'post' })
}

export function cancelOrder(id: number): Promise<AjaxResult<CrmOrder>> {
  return request({ url: `/opc/crm/order/${id}/cancel`, method: 'post' })
}

// ---------- Dashboard ----------

export interface DashboardFunnelResponse {
  funnel?: Record<string, number>
  customersByLevel?: Record<string, number>
  topOpportunities?: CrmOpportunity[]
  activeAmount?: number
  wonAmount?: number
}

export function getDashboard(): Promise<AjaxResult<DashboardFunnelResponse>> {
  return request({ url: '/opc/crm/dashboard', method: 'get' })
}

export function getCustomersByLevel(): Promise<AjaxResult<Record<string, number>>> {
  return request({ url: '/opc/crm/dashboard/customers', method: 'get' })
}

export function getUpcomingFollowUps(): Promise<AjaxResult<CrmFollowUp[]>> {
  return request({ url: '/opc/crm/dashboard/follow-ups/upcoming', method: 'get' })
}
