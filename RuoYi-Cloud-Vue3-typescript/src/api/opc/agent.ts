import request from '@/utils/request'
import type { AjaxResult } from '@/types'

// ================ OPC Agent Hub API ================

export function listMarket(category?: string): Promise<AjaxResult> {
  return request({ url: '/opc/agent/market', method: 'get', params: { category } })
}

export function getAgentDetail(id: number): Promise<AjaxResult> {
  return request({ url: `/opc/agent/detail/${id}`, method: 'get' })
}

export function hireAgent(data: any): Promise<AjaxResult> {
  return request({ url: '/opc/agent/hire', method: 'post', data })
}

export function listMyInstances(): Promise<AjaxResult> {
  return request({ url: '/opc/agent/instances', method: 'get' })
}

export function getInstance(id: number): Promise<AjaxResult> {
  return request({ url: `/opc/agent/instance/${id}`, method: 'get' })
}

export function instanceAction(id: number, action: 'pause' | 'resume' | 'revoke'): Promise<AjaxResult> {
  return request({ url: `/opc/agent/instance/${id}/action`, method: 'post', params: { action } })
}

export function listInstanceTasks(id: number, limit = 20): Promise<AjaxResult> {
  return request({ url: `/opc/agent/instance/${id}/tasks`, method: 'get', params: { limit } })
}

export function listInstanceUsage(id: number, limit = 20): Promise<AjaxResult> {
  return request({ url: `/opc/agent/instance/${id}/usage`, method: 'get', params: { limit } })
}

export function dailyUsage(companyId: number, startDate?: string, endDate?: string): Promise<AjaxResult> {
  return request({ url: '/opc/agent/usage/daily', method: 'get', params: { companyId, startDate, endDate } })
}

export function usageSummary(companyId: number, bizDate?: string): Promise<AjaxResult> {
  return request({ url: '/opc/agent/usage/summary', method: 'get', params: { companyId, bizDate } })
}

export function runTask(data: any): Promise<AjaxResult> {
  return request({ url: '/opc/agent/task/run', method: 'post', data })
}
