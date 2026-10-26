// api/opc/community —— 社区模块的 api 薄包装。
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({ default: vi.fn() }))

import request from '@/utils/request'
import * as community from '@/api/opc/community'
import {
  listModules,
  getModule,
  createModule,
  installModule,
  listComments,
  addComment,
  listRatings,
  rateModule,
  refreshModuleRating,
} from '@/api/opc/community'
import { expectApiContract, expectRequest } from '@/test/api-contract'

const requestMock = vi.mocked(request)

beforeEach(() => {
  requestMock.mockReset()
  requestMock.mockResolvedValue({ code: 200 } as any)
})

describe('api/opc/community', () => {
  it('每个导出函数都遵守 request 契约（url 前缀 / 动词 / 不撞路由）', () => {
    expectApiContract(community, '/opc/community/')
  })

  it('listModules() -> GET /opc/community/module/list，查询条件透传', () => {
    expectRequest(() => listModules({ page: 2, pageSize: 10 }), {
      url: '/opc/community/module/list',
      method: 'get',
      params: { page: 2, pageSize: 10 },
    })
  })

  it('getModule(7) -> GET /opc/community/module/7', () => {
    expectRequest(() => getModule(7), {
      url: '/opc/community/module/7',
      method: 'get',
    })
  })

  it('createModule(data) -> POST /opc/community/module', () => {
    const data = { code: 'crm', name: '客户管理', category: 'biz' }
    expectRequest(() => createModule(data), {
      url: '/opc/community/module',
      method: 'post',
      data,
    })
  })

  it('installModule(7) -> POST /opc/community/module/7/install', () => {
    expectRequest(() => installModule(7), {
      url: '/opc/community/module/7/install',
      method: 'post',
    })
  })

  it('listComments(3) -> GET /opc/community/comment?moduleId=3', () => {
    expectRequest(() => listComments(3), {
      url: '/opc/community/comment',
      method: 'get',
      params: { moduleId: 3 },
    })
  })

  it('addComment(data) -> POST /opc/community/comment', () => {
    const data = { moduleId: 3, content: '好用' }
    expectRequest(() => addComment(data), {
      url: '/opc/community/comment',
      method: 'post',
      data,
    })
  })

  it('listRatings(3) -> GET /opc/community/rating?moduleId=3', () => {
    expectRequest(() => listRatings(3), {
      url: '/opc/community/rating',
      method: 'get',
      params: { moduleId: 3 },
    })
  })

  it('rateModule(data) -> POST /opc/community/rating', () => {
    const data = { moduleId: 3, score: 5 }
    expectRequest(() => rateModule(data), {
      url: '/opc/community/rating',
      method: 'post',
      data,
    })
  })

  it('refreshModuleRating(3) -> POST /opc/community/rating/3/refresh', () => {
    expectRequest(() => refreshModuleRating(3), {
      url: '/opc/community/rating/3/refresh',
      method: 'post',
    })
  })
})
