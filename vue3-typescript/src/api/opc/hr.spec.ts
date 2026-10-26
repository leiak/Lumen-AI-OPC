// api/opc/hr —— 职位/候选人/投递/面试/offer 的 api 薄包装。
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({ default: vi.fn() }))

import request from '@/utils/request'
import * as hr from '@/api/opc/hr'
import {
  listJobs,
  getJob,
  createJob,
  updateJob,
  deleteJob,
  publishJob,
  pauseJob,
  closeJob,
  generateLlmJob,
  listCandidates,
  getCandidate,
  createCandidate,
  updateCandidate,
  deleteCandidate,
  parseCandidate,
  searchCandidate,
  listApplications,
  getApplication,
  createApplication,
  transitionApplicationStatus,
  scoreApplication,
  listInterviews,
  createInterview,
  updateInterviewFeedback,
  createOffer,
  respondOffer,
  getHrDashboard,
} from '@/api/opc/hr'
import { expectApiContract, expectRequest } from '@/test/api-contract'

const requestMock = vi.mocked(request)

beforeEach(() => {
  requestMock.mockReset()
  requestMock.mockResolvedValue({ code: 200 } as any)
})

describe('api/opc/hr', () => {
  it('每个导出函数都遵守 request 契约（url 前缀 / 动词 / 不撞路由）', () => {
    expectApiContract(hr, '/opc/hr/')
  })

  // ---- job ----
  it('listJobs(params) -> GET /opc/hr/job/list', () => {
    const params = { companyId: 1, status: 'PUBLISHED' }
    expectRequest(() => listJobs(params), { url: '/opc/hr/job/list', method: 'get', params })
  })

  it('getJob(5, 1) -> GET /opc/hr/job/5?companyId=1', () => {
    expectRequest(() => getJob(5, 1), {
      url: '/opc/hr/job/5',
      method: 'get',
      params: { companyId: 1 },
    })
  })

  it('createJob(data) -> POST /opc/hr/job', () => {
    const data = { title: '会计' } as any
    expectRequest(() => createJob(data), { url: '/opc/hr/job', method: 'post', data })
  })

  it('updateJob(5, data) -> PUT /opc/hr/job/5', () => {
    const data = { title: '会计（改）' } as any
    expectRequest(() => updateJob(5, data), { url: '/opc/hr/job/5', method: 'put', data })
  })

  it('deleteJob(5, 1) -> DELETE /opc/hr/job/5', () => {
    expectRequest(() => deleteJob(5, 1), {
      url: '/opc/hr/job/5',
      method: 'delete',
      params: { companyId: 1 },
    })
  })

  it('publishJob(5, 1) -> POST /opc/hr/job/5/publish', () => {
    expectRequest(() => publishJob(5, 1), {
      url: '/opc/hr/job/5/publish',
      method: 'post',
      params: { companyId: 1 },
    })
  })

  it('pauseJob(5, 1) -> POST /opc/hr/job/5/pause', () => {
    expectRequest(() => pauseJob(5, 1), {
      url: '/opc/hr/job/5/pause',
      method: 'post',
      params: { companyId: 1 },
    })
  })

  it('closeJob(5, 1) -> POST /opc/hr/job/5/close', () => {
    expectRequest(() => closeJob(5, 1), {
      url: '/opc/hr/job/5/close',
      method: 'post',
      params: { companyId: 1 },
    })
  })

  it('generateLlmJob(req) -> POST /opc/hr/job/generate-llm，req 整体作为请求体', () => {
    const req = { title: '会计', category: '财务', description: '记账' }
    expectRequest(() => generateLlmJob(req), {
      url: '/opc/hr/job/generate-llm',
      method: 'post',
      data: req,
    })
  })

  // ---- candidate ----
  it('listCandidates(params) -> GET /opc/hr/candidate/list', () => {
    const params = { companyId: 1, offset: 0, limit: 20 }
    expectRequest(() => listCandidates(params), {
      url: '/opc/hr/candidate/list',
      method: 'get',
      params,
    })
  })

  it('getCandidate(5, 1) -> GET /opc/hr/candidate/5', () => {
    expectRequest(() => getCandidate(5, 1), {
      url: '/opc/hr/candidate/5',
      method: 'get',
      params: { companyId: 1 },
    })
  })

  it('createCandidate(data) -> POST /opc/hr/candidate', () => {
    const data = { name: '张三' } as any
    expectRequest(() => createCandidate(data), { url: '/opc/hr/candidate', method: 'post', data })
  })

  it('updateCandidate(5, data) -> PUT /opc/hr/candidate/5', () => {
    const data = { name: '张三改' } as any
    expectRequest(() => updateCandidate(5, data), {
      url: '/opc/hr/candidate/5',
      method: 'put',
      data,
    })
  })

  it('deleteCandidate(5, 1) -> DELETE /opc/hr/candidate/5', () => {
    expectRequest(() => deleteCandidate(5, 1), {
      url: '/opc/hr/candidate/5',
      method: 'delete',
      params: { companyId: 1 },
    })
  })

  it('parseCandidate(5, 1) -> POST /opc/hr/candidate/5/parse', () => {
    expectRequest(() => parseCandidate(5, 1), {
      url: '/opc/hr/candidate/5/parse',
      method: 'post',
      params: { companyId: 1 },
    })
  })

  it('searchCandidate(req, 1) -> POST /opc/hr/candidate/search，companyId 走查询参数', () => {
    const req = { query: '有经验的会计', topK: 5 }
    expectRequest(() => searchCandidate(req, 1), {
      url: '/opc/hr/candidate/search',
      method: 'post',
      params: { companyId: 1 },
      data: req,
    })
  })

  // ---- application ----
  it('listApplications(params) -> GET /opc/hr/application/list', () => {
    const params = { companyId: 1, jobId: 5 }
    expectRequest(() => listApplications(params), {
      url: '/opc/hr/application/list',
      method: 'get',
      params,
    })
  })

  it('getApplication(5, 1) -> GET /opc/hr/application/5', () => {
    expectRequest(() => getApplication(5, 1), {
      url: '/opc/hr/application/5',
      method: 'get',
      params: { companyId: 1 },
    })
  })

  it('createApplication(data) -> POST /opc/hr/application', () => {
    const data = { jobId: 5, candidateId: 9 } as any
    expectRequest(() => createApplication(data), {
      url: '/opc/hr/application',
      method: 'post',
      data,
    })
  })

  it('transitionApplicationStatus(5, 1, "INVITED") -> PUT /opc/hr/application/5/status', () => {
    expectRequest(() => transitionApplicationStatus(5, 1, 'INVITED'), {
      url: '/opc/hr/application/5/status',
      method: 'put',
      params: { companyId: 1, status: 'INVITED' },
    })
  })

  it('scoreApplication(5, 1) -> POST /opc/hr/application/5/score', () => {
    expectRequest(() => scoreApplication(5, 1), {
      url: '/opc/hr/application/5/score',
      method: 'post',
      params: { companyId: 1 },
    })
  })

  // ---- interview ----
  it('listInterviews(params) -> GET /opc/hr/interview/list', () => {
    const params = { companyId: 1, applicationId: 5 }
    expectRequest(() => listInterviews(params), {
      url: '/opc/hr/interview/list',
      method: 'get',
      params,
    })
  })

  it('createInterview(data) -> POST /opc/hr/interview', () => {
    const data = { applicationId: 5 } as any
    expectRequest(() => createInterview(data), { url: '/opc/hr/interview', method: 'post', data })
  })

  it('updateInterviewFeedback(5, 1, "很好", "PASS") -> PUT /opc/hr/interview/5', () => {
    expectRequest(() => updateInterviewFeedback(5, 1, '很好', 'PASS'), {
      url: '/opc/hr/interview/5',
      method: 'put',
      params: { companyId: 1, feedback: '很好', result: 'PASS' },
    })
  })

  // ---- offer ----
  it('createOffer(data) -> POST /opc/hr/offer', () => {
    const data = { applicationId: 5, salary: 20000 } as any
    expectRequest(() => createOffer(data), { url: '/opc/hr/offer', method: 'post', data })
  })

  it('respondOffer(5, 1, "ACCEPT") -> PUT /opc/hr/offer/5/respond', () => {
    expectRequest(() => respondOffer(5, 1, 'ACCEPT'), {
      url: '/opc/hr/offer/5/respond',
      method: 'put',
      params: { companyId: 1, response: 'ACCEPT' },
    })
  })

  // ---- dashboard ----
  it('getHrDashboard(params) -> GET /opc/hr/dashboard', () => {
    const params = { companyId: 1, sinceDays: 30 }
    expectRequest(() => getHrDashboard(params), { url: '/opc/hr/dashboard', method: 'get', params })
  })
})
