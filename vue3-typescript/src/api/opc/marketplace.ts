import request from '@/utils/request'
import type { AjaxResult } from '@/types'

// ================ OPC 上架市场 API (P1) ================
//
// 契约以 springboot3/ruoyi-modules/opc-marketplace 的 5 个 Controller 为准。
// 关键语义差异（写代码前务必读完，否则会踩 404）：
//
// 1. `GET /publication/{id}` 是**公开**端点（网关白名单允许匿名），SQL 层
//    `selectPublishedById` 硬过滤 `status='PUBLISHED'`。拿它读 DRAFT 一定 404。
//    作者读自己任意状态的商品必须走 `GET /publication/my/{id}`（校验 authorId 归属）。
// 2. `GET /publication/list|search` 恒定只返回 PUBLISHED，不接受 status 参数。
//    作者看全部状态走 `GET /publication/my/list`。
// 3. 列表类接口返回 `R<Map<String,Object>>`，Map 键为 `rows/total/page/pageSize`，
//    配合 AjaxResult 读作 `res.data.rows`。
// 4. `GET /author/me` 在未入驻时返回 `R.ok(null)`，data 为 null（不是 404）。

/** ProductType: AGENT / MODULE */
export type ProductType = 'AGENT' | 'MODULE'

/**
 * PublicationStatus —— 与后端 `enums/PublicationStatus.java` 逐一对应。
 * P1 下只有 3 个状态可达：创建 → DRAFT，submit-audit/online → PUBLISHED，offline → DEPRECATED。
 * PENDING / SUSPENDED 是 P2 审核流预留位，P1 没有任何代码路径会写出它们。
 */
export type PublicationStatus = 'DRAFT' | 'PENDING' | 'PUBLISHED' | 'SUSPENDED' | 'DEPRECATED'

/** AuthorLevel: NORMAL / PREMIUM（收益分成比例 P2 使用） */
export type AuthorLevel = 'NORMAL' | 'PREMIUM'

/** 排序方式，与 `PublicationListQuery.sortBy` 一致 */
export type PublicationSort = 'newest' | 'rating' | 'installs'

export interface PublicationListQuery {
  category?: string
  productType?: string
  keyword?: string
  sortBy?: string
  page?: number
  pageSize?: number
}

export interface PublicationPage {
  rows: PublicationVO[]
  total: number
  page: number
  pageSize: number
}

export interface PublicationVO {
  id: number
  productType: ProductType
  agentDefinitionId?: number
  moduleId?: number
  authorId?: number
  /** 冗余自 mp_author */
  authorName?: string
  authorAvatar?: string
  code: string
  name: string
  categoryCode: string
  /** 冗余自 mp_category */
  categoryName?: string
  subCategoryCode?: string
  summary?: string
  longDescription?: string
  iconUrl?: string
  /** JSON string */
  screenshots?: string
  tags?: string
  status: PublicationStatus | string
  publishedAt?: string
  viewCount?: number
  installCount?: number
  subscriptionCount?: number
  avgRating?: number
  ratingCount?: number
  createTime?: string
}

export interface PublicationCreatePayload {
  productType: ProductType
  /** productType=AGENT 时必填 */
  agentDefinitionId?: number
  /** productType=MODULE 时必填 */
  moduleId?: number
  code: string
  name: string
  categoryCode: string
  subCategoryCode?: string
  summary?: string
  longDescription?: string
  iconUrl?: string
  screenshots?: string[]
  /** 逗号分隔 */
  tags?: string
}

/** 编辑请求 —— 全部字段可选（后端 PATCH 语义） */
export interface PublicationEditPayload {
  name?: string
  categoryCode?: string
  subCategoryCode?: string
  summary?: string
  longDescription?: string
  iconUrl?: string
  screenshots?: string[]
  tags?: string
}

export interface AuthorVO {
  id: number
  authorCompanyId?: number
  authorName: string
  contactEmail?: string
  contactPhone?: string
  intro?: string
  avatarUrl?: string
  level?: AuthorLevel | string
  totalAgents?: number
  totalSubscribers?: number
  totalRevenue?: number
  /** ACTIVE / SUSPENDED */
  status?: string
  createTime?: string
}

/** `GET /author/{id}/stats` 的 data 形状 */
export interface AuthorStats {
  /** 脱敏后的作者档案（不含邮箱/营收） */
  author: AuthorVO
  /** 仅统计 PUBLISHED */
  publicationCount: number
}

/** `AuthorApplyRequest` —— 无 authorCompanyId 字段，公司身份一律取自登录态 */
export interface AuthorApplyPayload {
  authorName: string
  contactEmail: string
  contactPhone?: string
  intro?: string
  avatarUrl?: string
}

/**
 * 评价列表行。后端 `GET /review/list/{publicationId}` 目前返回的是裸 `MpReview`
 * （不是 `ReviewVO`），因此没有 userName / userAvatar，展示时请退回 `User #id`。
 */
export interface ReviewVO {
  id: number
  publicationId: number
  userId: number
  /** 冗余自 sys_user —— 仅 ReviewVO 有，当前 list 端点未装配 */
  userName?: string
  userAvatar?: string
  rating?: number
  content?: string
  /** VISIBLE / HIDDEN */
  status?: string
  createTime?: string
}

export interface ReviewPage {
  rows: ReviewVO[]
  total: number
  page: number
  pageSize: number
}

export interface CategoryVO {
  id: number
  parentId?: number
  productType?: ProductType | string
  code: string
  name: string
  sortOrder?: number
  icon?: string
}

export interface TagVO {
  id: number
  name: string
  category?: string
  sortOrder?: number
}

export interface InstallVO {
  id: number
  publicationId: number
  tenantCompanyId?: number
  userId: number
  agentInstanceId?: number
  installSource?: string
  /** INSTALLED / UNINSTALLED */
  status?: string
  installedAt?: string
  uninstalledAt?: string
}

// ================ 商品 ================

export function listPublications(params: PublicationListQuery): Promise<AjaxResult<PublicationPage>> {
  return request({ url: '/opc/marketplace/publication/list', method: 'get', params })
}

export function searchPublications(params: PublicationListQuery): Promise<AjaxResult<PublicationPage>> {
  return request({ url: '/opc/marketplace/publication/search', method: 'get', params })
}

/** 公开详情 —— 仅 PUBLISHED。DRAFT 读它必 404，作者请用 myPublication()。 */
export function getPublication(id: number): Promise<AjaxResult<PublicationVO>> {
  return request({ url: `/opc/marketplace/publication/${id}`, method: 'get' })
}

/**
 * 作者读取自己任一状态商品（含 DRAFT）用于编辑表单填充 —— 不走公开 GET (会 404)。
 * 走 getPublication 加载 DRAFT 会失败,因为 /{id} 在公开详情场景下被 selectPublishedById 过滤。
 */
export function myPublication(id: number): Promise<AjaxResult<PublicationVO>> {
  return request({ url: `/opc/marketplace/publication/my/${id}`, method: 'get' })
}

export function myPublications(): Promise<AjaxResult<PublicationVO[]>> {
  return request({ url: '/opc/marketplace/publication/my/list', method: 'get' })
}

export function createPublication(data: PublicationCreatePayload): Promise<AjaxResult<PublicationVO>> {
  return request({ url: '/opc/marketplace/publication', method: 'post', data })
}

export function editPublication(id: number, data: PublicationEditPayload): Promise<AjaxResult<PublicationVO>> {
  return request({ url: `/opc/marketplace/publication/${id}`, method: 'put', data })
}

export function submitAudit(id: number): Promise<AjaxResult<PublicationVO>> {
  return request({ url: `/opc/marketplace/publication/${id}/submit-audit`, method: 'post' })
}

export function onlinePublication(id: number): Promise<AjaxResult<PublicationVO>> {
  return request({ url: `/opc/marketplace/publication/${id}/online`, method: 'post' })
}

export function offlinePublication(id: number): Promise<AjaxResult<PublicationVO>> {
  return request({ url: `/opc/marketplace/publication/${id}/offline`, method: 'post' })
}

// ================ 安装 ================

export function installPublication(id: number): Promise<AjaxResult<InstallVO>> {
  return request({ url: `/opc/marketplace/install/${id}`, method: 'post' })
}

export function uninstallPublication(id: number): Promise<AjaxResult<void>> {
  return request({ url: `/opc/marketplace/install/${id}`, method: 'delete' })
}

export function myInstalls(): Promise<AjaxResult<InstallVO[]>> {
  return request({ url: '/opc/marketplace/install/my', method: 'get' })
}

/** 未安装时后端返回 `R.ok(null)`（data 为 null），不要当成错误 */
export function installStatus(id: number): Promise<AjaxResult<InstallVO | null>> {
  return request({ url: `/opc/marketplace/install/${id}/status`, method: 'get' })
}

// ================ 作者 ================

export function applyAuthor(data: AuthorApplyPayload): Promise<AjaxResult<AuthorVO>> {
  return request({ url: '/opc/marketplace/author/apply', method: 'post', data })
}

/** 未入驻时 data 为 null（不是 404） */
export function getMyAuthor(): Promise<AjaxResult<AuthorVO | null>> {
  return request({ url: '/opc/marketplace/author/me', method: 'get' })
}

export function getAuthor(id: number): Promise<AjaxResult<AuthorVO>> {
  return request({ url: `/opc/marketplace/author/${id}`, method: 'get' })
}

export function getAuthorStats(id: number): Promise<AjaxResult<AuthorStats>> {
  return request({ url: `/opc/marketplace/author/${id}/stats`, method: 'get' })
}

// ================ 评价 ================

export function createReview(data: {
  publicationId: number
  rating: number
  content?: string
}): Promise<AjaxResult<ReviewVO>> {
  return request({ url: '/opc/marketplace/review', method: 'post', data })
}

export function listReviews(publicationId: number, params: any): Promise<AjaxResult<ReviewPage>> {
  return request({ url: `/opc/marketplace/review/list/${publicationId}`, method: 'get', params })
}

export function updateReview(id: number, data: { rating?: number; content?: string }): Promise<AjaxResult<ReviewVO>> {
  return request({ url: `/opc/marketplace/review/${id}`, method: 'put', data })
}

export function deleteReview(id: number): Promise<AjaxResult<void>> {
  return request({ url: `/opc/marketplace/review/${id}`, method: 'delete' })
}

// ================ 类目 / 标签 ================

/**
 * 类目列表。后端 `CategoryServiceImpl#tree()` 在 P1 返回的是**扁平** List
 * （尚未按 parentId 组树），前端自行按 productType 过滤即可。
 */
export function listCategories(): Promise<AjaxResult<CategoryVO[]>> {
  return request({ url: '/opc/marketplace/category/tree', method: 'get' })
}

export function listTags(category?: string): Promise<AjaxResult<TagVO[]>> {
  return request({ url: '/opc/marketplace/tag/list', method: 'get', params: { category } })
}