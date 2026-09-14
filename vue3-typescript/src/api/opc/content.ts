import request from '@/utils/request'
import type { AjaxResult } from '@/types'

// ================ OPC 内容生成 (W74) ================
// 11 + 5 + 4 = 20 endpoints (对齐 backend OpcContentScriptController /
// OpcContentPlatformController / OpcContentPublishController)

// ============== Enums (镜像后端) ==============

export type ContentScriptType = 'DRAMA' | 'VIDEO' | 'ARTICLE' | 'ADAPTER'

export type ContentScriptStatus = 'DRAFT' | 'READY' | 'PUBLISHED' | 'FAILED' | 'DELETED'

export type ContentPublishStatus = 'PENDING' | 'SUCCESS' | 'FAILED'

export type ContentPlatform = 'DOUYIN'

export type ContentAccountStatus = 'ACTIVE' | 'EXPIRED' | 'REVOKED'

// ============== 实体 ==============

/** 脚本 DTO — 字段名对齐 OpcContentScriptDto (snake_case) */
export interface OpcContentScript {
  id?: number
  company_id?: number
  user_id?: number
  type: ContentScriptType
  title: string
  prompt_input?: string
  content_json?: string
  content_md?: string
  word_count?: number
  status: ContentScriptStatus
  source_script_id?: number
  created_at?: string
  updated_at?: string
}

/** 平台账号 DTO — 对齐 OpcContentPlatformAccount (snake_case, token 不外露) */
export interface OpcContentPlatformAccount {
  id?: number
  company_id?: number
  platform: ContentPlatform
  nickname?: string
  open_id?: string
  union_id?: string
  access_token_expires_at?: string
  refresh_token_expires_at?: string
  scope?: string
  avatar_url?: string
  status?: ContentAccountStatus
  created_at?: string
  bound_at?: string
  updated_at?: string
}

/** 发布记录 DTO — 对齐 OpcContentPublish (snake_case) */
export interface OpcContentPublish {
  id?: number
  company_id?: number
  script_id: number
  platform_account_id: number
  platform?: ContentPlatform
  title?: string
  tags?: string
  external_video_id?: string
  external_post_id?: string
  external_url?: string
  status: ContentPublishStatus
  error_code?: string
  error_message?: string
  published_at?: string
  updated_at?: string
  created_at?: string
}

/** 通用分页列表响应 — 对齐 OpcContentListResponse */
export interface OpcContentListResponse<T> {
  rows: T[]
  total: number
}

/** Dashboard 聚合 DTO — 对齐 OpcContentDashboardDto */
export interface OpcContentDashboard {
  today_generate: number
  pending_publish: number
  published: number
  failed_rate: number
  seven_day_trend: number[]
  recent_scripts: OpcContentScript[]
}

// ============== Request DTOs ==============

/** 创建脚本 + 触发 LLM 生成 */
export interface OpcContentGenerateRequest {
  company_id: number
  type: ContentScriptType
  title?: string
  prompt_input: string
}

/** 部分精修 */
export interface OpcContentRefineRequest {
  line_no: number
  instruction: string
}

/** 平台适配 (原文 → 新脚本 type=ADAPTER) */
export interface OpcContentAdaptRequest {
  company_id: number
  source_script_id: number
  target_platform: ContentPlatform
  tone?: string
}

/** 发布请求 */
export interface OpcContentPublishRequest {
  company_id: number
  script_id: number
  platform_account_id: number
  title: string
  tags?: string[]
}

// ============== Script 11 ==============

/** 1. POST /opc/content/script — 创建脚本 + 触发 LLM 生成 */
export function createScript(data: OpcContentGenerateRequest): Promise<AjaxResult<number>> {
  return request({ url: '/opc/content/script', method: 'post', data })
}

/** 2. GET /opc/content/script/list — 脚本分页列表 (type/status 过滤) */
export function listScripts(params: {
  companyId: number
  type?: ContentScriptType
  status?: ContentScriptStatus
  page?: number
  size?: number
}): Promise<AjaxResult<OpcContentListResponse<OpcContentScript>>> {
  return request({ url: '/opc/content/script/list', method: 'get', params })
}

/** 3. GET /opc/content/script/{id} — 脚本详情 */
export function getScript(id: number, companyId: number): Promise<AjaxResult<OpcContentScript>> {
  return request({ url: `/opc/content/script/${id}`, method: 'get', params: { companyId } })
}

/** 4. PUT /opc/content/script/{id} — 更新脚本 (仅 DRAFT) */
export function updateScript(
  id: number,
  companyId: number,
  data: Partial<OpcContentScript>
): Promise<AjaxResult<number>> {
  return request({ url: `/opc/content/script/${id}`, method: 'put', params: { companyId }, data })
}

/** 5. DELETE /opc/content/script/{id} — 删除脚本 (仅 DRAFT, 软删) */
export function deleteScript(id: number, companyId: number): Promise<AjaxResult<number>> {
  return request({ url: `/opc/content/script/${id}`, method: 'delete', params: { companyId } })
}

/** 6. POST /opc/content/script/{id}/generate — 重新生成 (覆盖 content) */
export function regenerateScript(
  id: number,
  companyId: number,
  data: { prompt_input: string }
): Promise<AjaxResult<number>> {
  return request({
    url: `/opc/content/script/${id}/generate`,
    method: 'post',
    params: { companyId },
    data,
  })
}

/** 7. POST /opc/content/script/{id}/refine — 部分精修 (line_no + instruction) */
export function refineScript(
  id: number,
  companyId: number,
  data: OpcContentRefineRequest
): Promise<AjaxResult<void>> {
  return request({
    url: `/opc/content/script/${id}/refine`,
    method: 'post',
    params: { companyId },
    data,
  })
}

/** 8. POST /opc/content/script/{id}/ready — DRAFT → READY */
export function markScriptReady(id: number, companyId: number): Promise<AjaxResult<void>> {
  return request({
    url: `/opc/content/script/${id}/ready`,
    method: 'post',
    params: { companyId },
  })
}

/** 9. POST /opc/content/script/adapt — 平台适配 (原文 → 新脚本 type=ADAPTER) */
export function adaptScript(data: OpcContentAdaptRequest): Promise<AjaxResult<number>> {
  return request({ url: '/opc/content/script/adapt', method: 'post', data })
}

/** 10. GET /opc/content/script/{id}/publish-history — 脚本发布历史 */
export function getScriptPublishHistory(
  id: number,
  companyId: number
): Promise<AjaxResult<OpcContentPublish[]>> {
  return request({
    url: `/opc/content/script/${id}/publish-history`,
    method: 'get',
    params: { companyId },
  })
}

/** 11. GET /opc/content/script/dashboard — Dashboard 聚合统计 */
export function getContentDashboard(companyId: number): Promise<AjaxResult<OpcContentDashboard>> {
  return request({ url: '/opc/content/script/dashboard', method: 'get', params: { companyId } })
}

// ============== PlatformAccount 5 ==============

/** 1. GET /opc/content/platform-account/oauth/douyin/authorize — 抖音 OAuth 授权 URL (302) */
export function authorizeDouyin(companyId: number): Promise<AjaxResult<void>> {
  return request({
    url: '/opc/content/platform-account/oauth/douyin/authorize',
    method: 'get',
    params: { companyId },
  })
}

/** 2. GET /opc/content/platform-account/oauth/callback — 抖音 OAuth 回调 (302) */
export function oauthCallback(params: { code: string; state: string }): Promise<AjaxResult<void>> {
  return request({ url: '/opc/content/platform-account/oauth/callback', method: 'get', params })
}

/** 3. GET /opc/content/platform-account/list — 公司下平台账号列表 */
export function listPlatformAccounts(
  companyId: number
): Promise<AjaxResult<OpcContentPlatformAccount[]>> {
  return request({ url: '/opc/content/platform-account/list', method: 'get', params: { companyId } })
}

/** 4. DELETE /opc/content/platform-account/{id} — 解绑平台账号 (软删) */
export function deletePlatformAccount(
  id: number,
  companyId: number
): Promise<AjaxResult<number>> {
  return request({
    url: `/opc/content/platform-account/${id}`,
    method: 'delete',
    params: { companyId },
  })
}

/** 5. POST /opc/content/platform-account/{id}/refresh — 刷新 access_token */
export function refreshPlatformToken(id: number, companyId: number): Promise<AjaxResult<void>> {
  return request({
    url: `/opc/content/platform-account/${id}/refresh`,
    method: 'post',
    params: { companyId },
  })
}

// ============== Publish 4 ==============

/** 1. POST /opc/content/publish — 发布脚本到指定平台账号 (仅 READY) */
export function publishScript(data: OpcContentPublishRequest): Promise<AjaxResult<number>> {
  return request({ url: '/opc/content/publish', method: 'post', data })
}

/** 2. GET /opc/content/publish/list — 公司下发布记录分页列表 */
export function listPublishes(params: {
  companyId: number
  status?: ContentPublishStatus
  page?: number
  size?: number
}): Promise<AjaxResult<OpcContentListResponse<OpcContentPublish>>> {
  return request({ url: '/opc/content/publish/list', method: 'get', params })
}

/** 3. GET /opc/content/publish/{id} — 发布详情 */
export function getPublish(id: number, companyId: number): Promise<AjaxResult<OpcContentPublish>> {
  return request({ url: `/opc/content/publish/${id}`, method: 'get', params: { companyId } })
}

/** 4. POST /opc/content/publish/{id}/retry — 失败重试 (FAILED → PENDING) */
export function retryPublish(id: number, companyId: number): Promise<AjaxResult<void>> {
  return request({
    url: `/opc/content/publish/${id}/retry`,
    method: 'post',
    params: { companyId },
  })
}
