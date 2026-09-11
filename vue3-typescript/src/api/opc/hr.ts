import request from '@/utils/request'
import type { AjaxResult } from '@/types'

// ================ OPC HR 招聘管理 API (W51) ================

// ---------- 类型定义 (对齐 backend domain/dto) ----------

export interface OpcHrJob {
  id?: number
  companyId?: number
  createdBy?: number
  title: string
  category: string
  description?: string
  fullJd?: string
  skillsJson?: string
  salaryMin?: number
  salaryMax?: number
  location?: string
  status?: string
  publishAt?: string
  closeAt?: string
  createTime?: string
  updateTime?: string
}

export interface OpcHrCandidate {
  id?: number
  companyId?: number
  name: string
  email?: string
  phone?: string
  resumeUrl: string
  resumeMd?: string
  parsedJson?: string
  source?: string
  tagsJson?: string
  createdBy?: number
  createTime?: string
  updateTime?: string
}

export interface OpcHrApplication {
  id?: number
  companyId?: number
  jobId: number
  candidateId: number
  channel?: string
  score?: number
  scoreReason?: string
  status?: string
  currentStage?: string
  appliedAt?: string
  updateTime?: string
}

export interface OpcHrInterview {
  id?: number
  companyId?: number
  applicationId: number
  round?: number
  type: string
  interviewerId: number
  scheduledAt: string
  durationMin?: number
  feedback?: string
  result?: string
  createTime?: string
}

export interface OpcHrOffer {
  id?: number
  companyId?: number
  applicationId: number
  salary: number
  startDate: string
  expireAt: string
  status?: string
  sentAt?: string
  respondedAt?: string
  createTime?: string
}

export interface HrSearchResult {
  candidate: OpcHrCandidate
  score: number
  matchReason?: string
}

export interface OpcHrDashboard {
  funnel: Array<Record<string, any>>
  conversionRates: Record<string, number>
  avgHireDays: number | null
  jobStatusDistribution: Record<string, number>
}

// ---------- Job (9) ----------

export function listJobs(params: {
  companyId: number
  status?: string
}): Promise<AjaxResult<OpcHrJob[]>> {
  return request({ url: '/opc/hr/job/list', method: 'get', params })
}

export function getJob(id: number, companyId: number): Promise<AjaxResult<OpcHrJob>> {
  return request({ url: `/opc/hr/job/${id}`, method: 'get', params: { companyId } })
}

export function createJob(data: Partial<OpcHrJob>): Promise<AjaxResult<number>> {
  return request({ url: '/opc/hr/job', method: 'post', data })
}

export function updateJob(id: number, data: Partial<OpcHrJob>): Promise<AjaxResult<void>> {
  return request({ url: `/opc/hr/job/${id}`, method: 'put', data })
}

export function deleteJob(id: number, companyId: number): Promise<AjaxResult<void>> {
  return request({ url: `/opc/hr/job/${id}`, method: 'delete', params: { companyId } })
}

export function publishJob(id: number, companyId: number): Promise<AjaxResult<void>> {
  return request({ url: `/opc/hr/job/${id}/publish`, method: 'post', params: { companyId } })
}

export function pauseJob(id: number, companyId: number): Promise<AjaxResult<void>> {
  return request({ url: `/opc/hr/job/${id}/pause`, method: 'post', params: { companyId } })
}

export function closeJob(id: number, companyId: number): Promise<AjaxResult<void>> {
  return request({ url: `/opc/hr/job/${id}/close`, method: 'post', params: { companyId } })
}

export function generateLlmJob(req: {
  title: string
  category: string
  description: string
  companyId?: number
}): Promise<AjaxResult<string>> {
  return request({ url: '/opc/hr/job/generate-llm', method: 'post', data: req })
}

// ---------- Candidate (7) ----------

export function listCandidates(params: {
  companyId: number
  offset?: number
  limit?: number
}): Promise<AjaxResult<OpcHrCandidate[]>> {
  return request({ url: '/opc/hr/candidate/list', method: 'get', params })
}

export function getCandidate(id: number, companyId: number): Promise<AjaxResult<OpcHrCandidate>> {
  return request({ url: `/opc/hr/candidate/${id}`, method: 'get', params: { companyId } })
}

export function createCandidate(data: Partial<OpcHrCandidate>): Promise<AjaxResult<number>> {
  return request({ url: '/opc/hr/candidate', method: 'post', data })
}

export function updateCandidate(id: number, data: Partial<OpcHrCandidate>): Promise<AjaxResult<void>> {
  return request({ url: `/opc/hr/candidate/${id}`, method: 'put', data })
}

export function deleteCandidate(id: number, companyId: number): Promise<AjaxResult<void>> {
  return request({ url: `/opc/hr/candidate/${id}`, method: 'delete', params: { companyId } })
}

export function parseCandidate(id: number, companyId: number): Promise<AjaxResult<void>> {
  return request({ url: `/opc/hr/candidate/${id}/parse`, method: 'post', params: { companyId } })
}

export function searchCandidate(req: {
  query: string
  topK?: number
}, companyId: number): Promise<AjaxResult<HrSearchResult[]>> {
  return request({ url: '/opc/hr/candidate/search', method: 'post', params: { companyId }, data: req })
}

// ---------- Application (5) ----------

export function listApplications(params: {
  companyId: number
  jobId?: number
  candidateId?: number
  status?: string
  offset?: number
  limit?: number
}): Promise<AjaxResult<OpcHrApplication[]>> {
  return request({ url: '/opc/hr/application/list', method: 'get', params })
}

export function getApplication(id: number, companyId: number): Promise<AjaxResult<OpcHrApplication>> {
  return request({ url: `/opc/hr/application/${id}`, method: 'get', params: { companyId } })
}

export function createApplication(data: Partial<OpcHrApplication>): Promise<AjaxResult<number>> {
  return request({ url: '/opc/hr/application', method: 'post', data })
}

export function transitionApplicationStatus(
  id: number,
  companyId: number,
  status: string
): Promise<AjaxResult<void>> {
  return request({
    url: `/opc/hr/application/${id}/status`,
    method: 'put',
    params: { companyId, status }
  })
}

export function scoreApplication(id: number, companyId: number): Promise<AjaxResult<void>> {
  return request({
    url: `/opc/hr/application/${id}/score`,
    method: 'post',
    params: { companyId }
  })
}

// ---------- Interview (3) ----------

export function listInterviews(params: {
  companyId: number
  applicationId?: number
}): Promise<AjaxResult<OpcHrInterview[]>> {
  return request({ url: '/opc/hr/interview/list', method: 'get', params })
}

export function createInterview(data: Partial<OpcHrInterview>): Promise<AjaxResult<number>> {
  return request({ url: '/opc/hr/interview', method: 'post', data })
}

export function updateInterviewFeedback(
  id: number,
  companyId: number,
  feedback: string,
  result: string
): Promise<AjaxResult<void>> {
  return request({
    url: `/opc/hr/interview/${id}`,
    method: 'put',
    params: { companyId, feedback, result }
  })
}

// ---------- Offer (2) ----------

export function createOffer(data: Partial<OpcHrOffer>): Promise<AjaxResult<number>> {
  return request({ url: '/opc/hr/offer', method: 'post', data })
}

export function respondOffer(
  id: number,
  companyId: number,
  response: string
): Promise<AjaxResult<void>> {
  return request({
    url: `/opc/hr/offer/${id}/respond`,
    method: 'put',
    params: { companyId, response }
  })
}

// ---------- Dashboard (1) ----------

export function getHrDashboard(params: {
  companyId: number
  sinceDays?: number
}): Promise<AjaxResult<OpcHrDashboard>> {
  return request({ url: '/opc/hr/dashboard', method: 'get', params })
}
