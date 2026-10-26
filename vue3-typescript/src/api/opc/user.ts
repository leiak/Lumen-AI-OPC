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

/**
 * 我加入的公司（= 可切换的租户）。
 *
 * <p>与 {@link listMyCompanies} 的区别：那个按「我创办的」过滤（owner_user_id），
 * 被邀请进别人公司的员工拿回来的是空数组；这个按成员关系过滤，
 * 员工也能看到自己所在的公司 —— 多公司用户要能选自己所在的那一家。</p>
 */
export function listMyCompanyMembers(): Promise<AjaxResult> {
  return request({ url: '/opc/user/company-members', method: 'get' })
}

/**
 * 切换当前公司（租户）。
 *
 * <p>请求体字段叫 {@code targetCompanyId} 而不是 {@code companyId}：
 * 服务端的租户守卫会把请求体里叫 companyId 的字段改写成「当前公司」，
 * 那样这个调用永远切不过去。</p>
 */
export function switchCompany(targetCompanyId: number): Promise<AjaxResult> {
  return request({ url: '/opc/user/switch-company', method: 'post', data: { targetCompanyId } })
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
