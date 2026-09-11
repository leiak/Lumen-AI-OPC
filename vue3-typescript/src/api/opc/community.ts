import request from '@/utils/request'
import type { AjaxResult } from '@/types'

// ================ OPC 社区 API (W52) ================

export interface CommunityModule {
  id?: number
  code: string
  name: string
  category: string
  description?: string
  ownerId?: number
  ownerName?: string
  icon?: string
  rating?: number
  ratingCount?: number
  installCount?: number
  commentCount?: number
  status?: string
  tags?: string
  createTime?: string
  updateTime?: string
}

export interface CommunityComment {
  id?: number
  moduleId: number
  userId: number
  userName?: string
  content: string
  parentId?: number
  status?: string
  createTime?: string
}

export interface CommunityRating {
  id?: number
  moduleId: number
  userId: number
  score: number
  review?: string
  createTime?: string
  updateTime?: string
}

// ----- 模块 -----

export function listModules(params: {
  category?: string
  keyword?: string
  page?: number
  pageSize?: number
}): Promise<AjaxResult<{ rows: CommunityModule[]; total: number; page: number; pageSize: number }>> {
  return request({ url: '/opc/community/module/list', method: 'get', params })
}

export function getModule(id: number): Promise<AjaxResult<CommunityModule>> {
  return request({ url: `/opc/community/module/${id}`, method: 'get' })
}

export function createModule(data: {
  code: string
  name: string
  category: string
  description?: string
  icon?: string
  tags?: string
}): Promise<AjaxResult<CommunityModule>> {
  return request({ url: '/opc/community/module', method: 'post', data })
}

export function installModule(id: number): Promise<AjaxResult<void>> {
  return request({ url: `/opc/community/module/${id}/install`, method: 'post' })
}

// ----- 评论 -----

export function listComments(moduleId: number): Promise<AjaxResult<CommunityComment[]>> {
  return request({ url: '/opc/community/comment', method: 'get', params: { moduleId } })
}

export function addComment(data: {
  moduleId: number
  content: string
  parentId?: number
}): Promise<AjaxResult<CommunityComment>> {
  return request({ url: '/opc/community/comment', method: 'post', data })
}

// ----- 评分 -----

export function listRatings(moduleId: number): Promise<AjaxResult<CommunityRating[]>> {
  return request({ url: '/opc/community/rating', method: 'get', params: { moduleId } })
}

export function rateModule(data: {
  moduleId: number
  score: number
  review?: string
}): Promise<AjaxResult<CommunityRating>> {
  return request({ url: '/opc/community/rating', method: 'post', data })
}

export function refreshModuleRating(moduleId: number): Promise<AjaxResult<CommunityModule>> {
  return request({ url: `/opc/community/rating/${moduleId}/refresh`, method: 'post' })
}