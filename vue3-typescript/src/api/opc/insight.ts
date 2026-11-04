import request from '@/utils/request'
import type { AjaxResult } from '@/types'

// ================ OPC 经营洞察 API (M4 INSIGHT MVP) ================
//
// Spec deviation: regenerateAdvice takes `id: number` and POSTs to
// /opc/insight/advice/{id}/regenerate (path-scoped). The plan spec line 1326
// said `?topic=cost_optimization` (query), but the actual backend (Task 9
// AdviceController) uses `{id}` path variable to identify the advice row
// being re-generated. Both the wrapper and the spec file implement the
// backend-correct contract.

// Dashboard
export function dashboard() {
  return request({ url: '/opc/insight/dashboard', method: 'get' })
}

// Alerts
export function listAlerts(limit?: number) {
  return request({ url: '/opc/insight/alerts', method: 'get', params: { limit } })
}
export function ackAlert(id: number) {
  return request({ url: `/opc/insight/alerts/${id}/ack`, method: 'post' })
}

// Daily Reports
//
// 契约以 springboot3/ruoyi-modules/opc-insight 的 DailyReportVo 为准。
// 注意：日报的数字**不在顶层字段上**，全都在 kpiJson 这个 JSON 字符串里 ——
// 顶层只有 id/companyId/period/summaryMd/kpiJson/adviceMd/llmUsed/
// reportStatus/reportSource/sourceWorkflowRunId/createTime。
// 曾经前端读 row.totalRevenue / row.totalExpense / row.voucherCount，
// 那三个字段后端从来不返回 → 三列永远是空的（详见 daily.vue 的注释）。
export interface DailyReportVO {
  id: number
  companyId?: number
  /** yyyy-MM-dd */
  period: string
  summaryMd?: string
  adviceMd?: string
  /** 后端存的是 JSON 字符串，不是对象 —— 页面要自己 parse */
  kpiJson?: string
  /** 用的 LLM 模型 id；"FALLBACK" = LLM 根本没参与 */
  llmUsed?: string
  /** 写到哪一步：FULL / PARTIAL / FALLBACK */
  reportStatus?: 'FULL' | 'PARTIAL' | 'FALLBACK'
  /** 谁写的这一行：AGENT_WORKFLOW / MANUAL */
  reportSource?: 'AGENT_WORKFLOW' | 'MANUAL'
  /** 产出该日报的工作流运行记录 id（手工生成时为空） */
  sourceWorkflowRunId?: number
  createTime?: string
}

export function listDaily(from: string, to: string, limit?: number): Promise<AjaxResult<DailyReportVO[]>> {
  return request({ url: '/opc/insight/daily', method: 'get', params: { from, to, limit } })
}
export function getDaily(id: number): Promise<AjaxResult<DailyReportVO>> {
  return request({ url: `/opc/insight/daily/${id}`, method: 'get' })
}
/**
 * 生成日报。
 *
 * <p>后端 `DailyReportController.generate` 返回的是 **`R<Void>`**（`R.ok()`，没有 body）——
 * **没有日报 id 可言**。以前前端写「日报生成成功（ID=${r.data?.reportId}）」，
 * 那个 id 恒为 `undefined`：界面在报一个后端根本没给的东西。</p>
 */
export function generateDaily(date: string): Promise<AjaxResult<void>> {
  return request({ url: '/opc/insight/daily/generate', method: 'post', params: { date } })
}

// Advice
export function listAdvice(limit?: number) {
  return request({ url: '/opc/insight/advice', method: 'get', params: { limit } })
}
export function getAdvice(id: number) {
  return request({ url: `/opc/insight/advice/${id}`, method: 'get' })
}
export function regenerateAdvice(id: number) {
  return request({ url: `/opc/insight/advice/${id}/regenerate`, method: 'post' })
}