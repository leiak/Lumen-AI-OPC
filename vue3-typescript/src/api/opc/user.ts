import request from '@/utils/request'
import type { AjaxResult } from '@/types'

// ================ OPC 用户中心 API ================

export function getMyProfile(): Promise<AjaxResult> {
  return request({ url: '/opc/user/profile', method: 'get' })
}

export function saveProfile(data: any): Promise<AjaxResult> {
  return request({ url: '/opc/user/profile', method: 'post', data })
}

export function listMyCompanies(): Promise<AjaxResult> {
  return request({ url: '/opc/user/companies', method: 'get' })
}

export function createCompany(data: any): Promise<AjaxResult> {
  return request({ url: '/opc/user/company', method: 'post', data })
}

export function getCompany(id: number): Promise<AjaxResult> {
  return request({ url: `/opc/user/company/${id}`, method: 'get' })
}

export function updateCompany(data: any): Promise<AjaxResult> {
  return request({ url: '/opc/user/company', method: 'put', data })
}

export function getUserHome(): Promise<AjaxResult> {
  return request({ url: '/opc/user/home', method: 'get' })
}

// ================ 邀请码 API ================

export function generateInvitation(): Promise<AjaxResult> {
  return request({ url: '/opc/user/invitations/generate', method: 'post' })
}

export function listMyInvitations(): Promise<AjaxResult> {
  return request({ url: '/opc/user/invitations', method: 'get' })
}

export function getInvitationPublic(code: string): Promise<AjaxResult> {
  return request({ url: `/opc/user/invitations/${code}`, method: 'get' })
}

export function acceptInvitation(code: string, mobile?: string): Promise<AjaxResult> {
  return request({ url: '/opc/user/invitations/accept', method: 'post', data: { code, mobile } })
}
