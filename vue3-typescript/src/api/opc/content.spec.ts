// api/opc/content —— 内容/脚本/发布/平台账号的 api 薄包装。
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({ default: vi.fn() }))

import request from '@/utils/request'
import * as content from '@/api/opc/content'
import {
  createScript,
  listScripts,
  getScript,
  updateScript,
  deleteScript,
  regenerateScript,
  refineScript,
  markScriptReady,
  adaptScript,
  getScriptPublishHistory,
  getContentDashboard,
  authorizeDouyin,
  oauthCallback,
  listPlatformAccounts,
  deletePlatformAccount,
  refreshPlatformToken,
  publishScript,
  listPublishes,
  getPublish,
  retryPublish,
} from '@/api/opc/content'
import { expectApiContract, expectRequest } from '@/test/api-contract'

const requestMock = vi.mocked(request)

beforeEach(() => {
  requestMock.mockReset()
  requestMock.mockResolvedValue({ code: 200 } as any)
})

describe('api/opc/content', () => {
  it('每个导出函数都遵守 request 契约（url 前缀 / 动词 / 不撞路由）', () => {
    expectApiContract(content, '/opc/content/')
  })

  // ---- script ----
  it('createScript(data) -> POST /opc/content/script', () => {
    const data = { companyId: 1, type: 'VIDEO', topic: 't' } as any
    expectRequest(() => createScript(data), {
      url: '/opc/content/script',
      method: 'post',
      data,
    })
  })

  it('listScripts(params) -> GET /opc/content/script/list', () => {
    const params = { companyId: 1, page: 1, size: 20 }
    expectRequest(() => listScripts(params), {
      url: '/opc/content/script/list',
      method: 'get',
      params,
    })
  })

  it('getScript(5, 1) -> GET /opc/content/script/5?companyId=1', () => {
    expectRequest(() => getScript(5, 1), {
      url: '/opc/content/script/5',
      method: 'get',
      params: { companyId: 1 },
    })
  })

  it('updateScript(5, 1, data) -> PUT /opc/content/script/5', () => {
    const data = { title: '新标题' } as any
    expectRequest(() => updateScript(5, 1, data), {
      url: '/opc/content/script/5',
      method: 'put',
      params: { companyId: 1 },
      data,
    })
  })

  it('deleteScript(5, 1) -> DELETE /opc/content/script/5', () => {
    expectRequest(() => deleteScript(5, 1), {
      url: '/opc/content/script/5',
      method: 'delete',
      params: { companyId: 1 },
    })
  })

  it('regenerateScript(5, 1, data) -> POST /opc/content/script/5/generate', () => {
    const data = { prompt_input: '换个开头' } as any
    expectRequest(() => regenerateScript(5, 1, data), {
      url: '/opc/content/script/5/generate',
      method: 'post',
      params: { companyId: 1 },
      data,
    })
  })

  it('refineScript(5, 1, data) -> POST /opc/content/script/5/refine', () => {
    const data = { line_no: 3, instruction: '更口语' } as any
    expectRequest(() => refineScript(5, 1, data), {
      url: '/opc/content/script/5/refine',
      method: 'post',
      params: { companyId: 1 },
      data,
    })
  })

  it('markScriptReady(5, 1) -> POST /opc/content/script/5/ready', () => {
    expectRequest(() => markScriptReady(5, 1), {
      url: '/opc/content/script/5/ready',
      method: 'post',
      params: { companyId: 1 },
    })
  })

  it('adaptScript(data) -> POST /opc/content/script/adapt', () => {
    const data = { companyId: 1, scriptId: 5 } as any
    expectRequest(() => adaptScript(data), {
      url: '/opc/content/script/adapt',
      method: 'post',
      data,
    })
  })

  it('getScriptPublishHistory(5, 1) -> GET /opc/content/script/5/publish-history', () => {
    expectRequest(() => getScriptPublishHistory(5, 1), {
      url: '/opc/content/script/5/publish-history',
      method: 'get',
      params: { companyId: 1 },
    })
  })

  it('getContentDashboard(1) -> GET /opc/content/script/dashboard', () => {
    expectRequest(() => getContentDashboard(1), {
      url: '/opc/content/script/dashboard',
      method: 'get',
      params: { companyId: 1 },
    })
  })

  // ---- platform account ----
  it('authorizeDouyin(1) -> GET .../oauth/douyin/authorize，companyId 走查询参数', () => {
    expectRequest(() => authorizeDouyin(1), {
      url: '/opc/content/platform-account/oauth/douyin/authorize',
      method: 'get',
      params: { companyId: 1 },
    })
  })

  it('oauthCallback({code,state}) -> GET .../oauth/callback，code/state 原样透传', () => {
    const params = { code: 'abc', state: 'xyz' }
    expectRequest(() => oauthCallback(params), {
      url: '/opc/content/platform-account/oauth/callback',
      method: 'get',
      params,
    })
  })

  it('listPlatformAccounts(1) -> GET /opc/content/platform-account/list', () => {
    expectRequest(() => listPlatformAccounts(1), {
      url: '/opc/content/platform-account/list',
      method: 'get',
      params: { companyId: 1 },
    })
  })

  it('deletePlatformAccount(9, 1) -> DELETE /opc/content/platform-account/9', () => {
    expectRequest(() => deletePlatformAccount(9, 1), {
      url: '/opc/content/platform-account/9',
      method: 'delete',
      params: { companyId: 1 },
    })
  })

  it('refreshPlatformToken(9, 1) -> POST /opc/content/platform-account/9/refresh', () => {
    expectRequest(() => refreshPlatformToken(9, 1), {
      url: '/opc/content/platform-account/9/refresh',
      method: 'post',
      params: { companyId: 1 },
    })
  })

  // ---- publish ----
  it('publishScript(data) -> POST /opc/content/publish', () => {
    const data = { scriptId: 5, platformAccountId: 9 } as any
    expectRequest(() => publishScript(data), {
      url: '/opc/content/publish',
      method: 'post',
      data,
    })
  })

  it('listPublishes(params) -> GET /opc/content/publish/list', () => {
    const params = { companyId: 1, page: 1, size: 20 }
    expectRequest(() => listPublishes(params), {
      url: '/opc/content/publish/list',
      method: 'get',
      params,
    })
  })

  it('getPublish(4, 1) -> GET /opc/content/publish/4', () => {
    expectRequest(() => getPublish(4, 1), {
      url: '/opc/content/publish/4',
      method: 'get',
      params: { companyId: 1 },
    })
  })

  it('retryPublish(4, 1) -> POST /opc/content/publish/4/retry', () => {
    expectRequest(() => retryPublish(4, 1), {
      url: '/opc/content/publish/4/retry',
      method: 'post',
      params: { companyId: 1 },
    })
  })
})
