// api/opc/marketplace —— 商品/安装/作者/评价/类目标签的 api 薄包装。
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({ default: vi.fn() }))

import request from '@/utils/request'
import * as marketplace from '@/api/opc/marketplace'
import {
  listPublications,
  searchPublications,
  getPublication,
  myPublication,
  myPublications,
  createPublication,
  editPublication,
  submitAudit,
  onlinePublication,
  offlinePublication,
  installPublication,
  uninstallPublication,
  myInstalls,
  installStatus,
  applyAuthor,
  getMyAuthor,
  getAuthor,
  getAuthorStats,
  createReview,
  listReviews,
  updateReview,
  deleteReview,
  listCategories,
  listTags,
} from '@/api/opc/marketplace'
import { expectApiContract, expectRequest } from '@/test/api-contract'

const requestMock = vi.mocked(request)

beforeEach(() => {
  requestMock.mockReset()
  requestMock.mockResolvedValue({ code: 200 } as any)
})

describe('api/opc/marketplace', () => {
  it('每个导出函数都遵守 request 契约（url 前缀 / 动词 / 不撞路由）', () => {
    expectApiContract(marketplace, '/opc/marketplace/')
  })

  // ---- 商品 ----
  it('listPublications(params) -> GET /opc/marketplace/publication/list', () => {
    const params = { page: 1, pageSize: 20 } as any
    expectRequest(() => listPublications(params), {
      url: '/opc/marketplace/publication/list',
      method: 'get',
      params,
    })
  })

  it('searchPublications(params) -> GET /opc/marketplace/publication/search', () => {
    const params = { keyword: '记账' } as any
    expectRequest(() => searchPublications(params), {
      url: '/opc/marketplace/publication/search',
      method: 'get',
      params,
    })
  })

  it('getPublication(5) -> GET /opc/marketplace/publication/5（公开详情）', () => {
    expectRequest(() => getPublication(5), { url: '/opc/marketplace/publication/5', method: 'get' })
  })

  it('myPublication(5) -> GET /opc/marketplace/publication/my/5（含草稿）', () => {
    expectRequest(() => myPublication(5), {
      url: '/opc/marketplace/publication/my/5',
      method: 'get',
    })
  })

  it('myPublications() -> GET /opc/marketplace/publication/my/list', () => {
    expectRequest(() => myPublications(), {
      url: '/opc/marketplace/publication/my/list',
      method: 'get',
    })
  })

  it('createPublication(data) -> POST /opc/marketplace/publication', () => {
    const data = { title: '记账代理' } as any
    expectRequest(() => createPublication(data), {
      url: '/opc/marketplace/publication',
      method: 'post',
      data,
    })
  })

  it('editPublication(5, data) -> PUT /opc/marketplace/publication/5', () => {
    const data = { title: '记账代理（改）' } as any
    expectRequest(() => editPublication(5, data), {
      url: '/opc/marketplace/publication/5',
      method: 'put',
      data,
    })
  })

  it('submitAudit(5) -> POST /opc/marketplace/publication/5/submit-audit', () => {
    expectRequest(() => submitAudit(5), {
      url: '/opc/marketplace/publication/5/submit-audit',
      method: 'post',
    })
  })

  it('onlinePublication(5) -> POST /opc/marketplace/publication/5/online', () => {
    expectRequest(() => onlinePublication(5), {
      url: '/opc/marketplace/publication/5/online',
      method: 'post',
    })
  })

  it('offlinePublication(5) -> POST /opc/marketplace/publication/5/offline', () => {
    expectRequest(() => offlinePublication(5), {
      url: '/opc/marketplace/publication/5/offline',
      method: 'post',
    })
  })

  // ---- 安装 ----
  it('installPublication(5) -> POST /opc/marketplace/install/5', () => {
    expectRequest(() => installPublication(5), {
      url: '/opc/marketplace/install/5',
      method: 'post',
    })
  })

  it('uninstallPublication(5) -> DELETE /opc/marketplace/install/5', () => {
    expectRequest(() => uninstallPublication(5), {
      url: '/opc/marketplace/install/5',
      method: 'delete',
    })
  })

  it('myInstalls() -> GET /opc/marketplace/install/my', () => {
    expectRequest(() => myInstalls(), { url: '/opc/marketplace/install/my', method: 'get' })
  })

  it('installStatus(5) -> GET /opc/marketplace/install/5/status', () => {
    expectRequest(() => installStatus(5), { url: '/opc/marketplace/install/5/status', method: 'get' })
  })

  // ---- 作者 ----
  it('applyAuthor(data) -> POST /opc/marketplace/author/apply', () => {
    const data = { realName: '张三' } as any
    expectRequest(() => applyAuthor(data), {
      url: '/opc/marketplace/author/apply',
      method: 'post',
      data,
    })
  })

  it('getMyAuthor() -> GET /opc/marketplace/author/me', () => {
    expectRequest(() => getMyAuthor(), { url: '/opc/marketplace/author/me', method: 'get' })
  })

  it('getAuthor(5) -> GET /opc/marketplace/author/5', () => {
    expectRequest(() => getAuthor(5), { url: '/opc/marketplace/author/5', method: 'get' })
  })

  it('getAuthorStats(5) -> GET /opc/marketplace/author/5/stats', () => {
    expectRequest(() => getAuthorStats(5), { url: '/opc/marketplace/author/5/stats', method: 'get' })
  })

  // ---- 评价 ----
  it('createReview(data) -> POST /opc/marketplace/review', () => {
    const data = { publicationId: 5, rating: 5, content: '好用' }
    expectRequest(() => createReview(data), { url: '/opc/marketplace/review', method: 'post', data })
  })

  it('listReviews(5, params) -> GET /opc/marketplace/review/list/5', () => {
    const params = { page: 1, pageSize: 10 }
    expectRequest(() => listReviews(5, params), {
      url: '/opc/marketplace/review/list/5',
      method: 'get',
      params,
    })
  })

  it('updateReview(5, data) -> PUT /opc/marketplace/review/5', () => {
    const data = { rating: 4 }
    expectRequest(() => updateReview(5, data), { url: '/opc/marketplace/review/5', method: 'put', data })
  })

  it('deleteReview(5) -> DELETE /opc/marketplace/review/5', () => {
    expectRequest(() => deleteReview(5), { url: '/opc/marketplace/review/5', method: 'delete' })
  })

  // ---- 类目 / 标签 ----
  it('listCategories() -> GET /opc/marketplace/category/tree', () => {
    expectRequest(() => listCategories(), { url: '/opc/marketplace/category/tree', method: 'get' })
  })

  it('listTags("财务") -> GET /opc/marketplace/tag/list?category=财务', () => {
    expectRequest(() => listTags('财务'), {
      url: '/opc/marketplace/tag/list',
      method: 'get',
      params: { category: '财务' },
    })
  })
})
